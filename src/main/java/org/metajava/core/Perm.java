package org.metajava.core;

import java.util.*;

/**
 * Permutation test for a meta-regression model (metafor::permutest.rma.uni).
 * The rows of the model matrix are permuted (effects and sampling variances stay in place), the model is refitted
 * (tau^2 is re-estimated every time) and the Wald statistics of the coefficients and the omnibus statistic QM are
 * compared with the observed ones: p = share of permutations with |z*| >= |z| (QM* >= QM). With Knapp-Hartung
 * adjustment the statistics are t and F, as in metafor.
 * Exact or random, as metafor decides: the number of <em>unique</em> permutations of the rows of the model matrix (identical rows count once:
 * k!/(n1! n2! ...) for groups of identical rows, {@link #uniquePermutations}) is compared with {@code iter}; the exact test enumerates them all
 * when {@code exact = TRUE} or when that number does not exceed {@code iter}. In the exact test a permuted fit that stops with an error leaves its slot undefined.
 * Random: as metafor, {@code iter} slots are filled one after the other with the statistics of a permuted fit; a permutation whose fit
 * stops with an error is redrawn without limit (so exactly {@code iter} valid fits are made; the analysis can be cancelled, see {@link Par#checkCancel}), and the first slot is then replaced by the observed
 * statistics (Phipson and Smyth, 2010: the observed arrangement is one of the {@code iter} permutations, p = hits / iter is never 0).
 * The permutations are drawn with R's generator and {@code sample(k)} from the rows of the model matrix sorted as metafor sorts them,
 * so a seed gives the permutations metafor draws after {@code set.seed(seed)}.
 */
public final class Perm {
    public String[] coefNames; public int k, p, iter, failed; public boolean exact, knha; public long nPerm, seed, attempts;
    public double[] z, pval; public double QM = Double.NaN, pQM = Double.NaN; public int dfQM; public String note = "";
    static final double TOL = Math.sqrt(Math.ulp(1.0));
    /** Exact tests are enumerated in chunks of this many permutations (memory stays small however many there are). */
    static final int CHUNK = 20000;

    /** @param wantExact exact test (always when the number of unique permutations does not exceed iter) */
    public static Perm run(Reg g, int iter, boolean wantExact, long seed) { return run(g, iter, wantExact, seed, 0); }

    /**
     * @param maxDraws optional upper limit of permuted fits tried by the random test (valid and failed together); 0 = none, as metafor (it retries without limit)
     * @throws IllegalArgumentException when an exact test needs too many permutations, or when a limit was given and {@code iter} valid permuted fits cannot be obtained within it
     */
    public static Perm run(Reg g, int iter, boolean wantExact, long seed, long maxDraws) {
        final Rma f = g.fit; final int k = f.k, p = f.p; final double[] y = f.yi, v = f.vi; final double[][] X = f.X; final String method = g.method;
        if (g.dfQM <= 0) throw new IllegalArgumentException("The permutation test needs at least one moderator.");
        Perm o = new Perm(); o.k = k; o.p = p; o.coefNames = g.coefNames; o.knha = g.knha; o.seed = seed; o.dfQM = g.dfQM;
        o.z = g.zval.clone(); o.QM = g.QM;
        iter = Math.max(2, iter);   // metafor: 'iter' must be >= 2
        final double[][] Xs = sortedRows(X);
        final int[] lab = groupLabels(Xs); final double uniq = uniquePermutations(lab, k);
        boolean exact = wantExact || uniq <= iter;   // metafor: X.exact || (X.exact.iter <= X.iter)
        if (exact && uniq == Double.POSITIVE_INFINITY) throw new IllegalArgumentException("Too many iterations required for an exact permutation test.");
        if (exact && uniq > Integer.MAX_VALUE - 8) throw new IllegalArgumentException("Number of iterations requested too large.");
        o.exact = exact; o.iter = exact ? (int) uniq : iter; o.nPerm = o.iter;
        final int n = o.iter;
        final boolean knha = g.knha, adhoc = g.adhocSe, icpt = g.intercept;
        final long[] cnt = new long[p], nv = new long[p]; long cq = 0, nq = 0;
        if (exact) {
            // all unique permutations of the group labels in lexicographic order (what metafor's .genuperms() returns); the rows of a group are identical, so any of them can stand for it
            int g0 = 0; for (int l : lab) g0 = Math.max(g0, l + 1);
            final double[][] rep = new double[g0][]; for (int i = 0; i < k; i++) rep[lab[i]] = Xs[i];
            int[] cur = lab.clone(); long done = 0;
            while (done < n) {
                Par.checkCancel();
                int m = (int) Math.min(CHUNK, n - done); final int[][] batch = new int[m][]; final double[][] res = new double[m][];
                for (int b = 0; b < m; b++) { batch[b] = cur.clone(); nextPermutation(cur); }
                Par.forRange(m, b -> res[b] = fitPermuted(f, y, v, rep, batch[b], method, knha, adhoc, icpt, null));
                for (int b = 0; b < m; b++) {
                    double[] st = res[b]; if (st == null) { o.failed++; continue; }
                    for (int a = 0; a < p; a++) if (!Double.isNaN(st[a])) { nv[a]++; if (Math.abs(st[a]) >= Math.abs(o.z[a]) - TOL) cnt[a]++; }
                    if (!Double.isNaN(st[p])) { nq++; if (st[p] >= o.QM - TOL) cq++; }
                }
                done += m;
            }
            o.attempts = n;
        } else {
            // metafor: X <- as.data.frame(x$X)[do.call(order, as.data.frame(x$X)),]; rows X[sample(k),] are then used with yi, vi and the weights in their original order
            final long cap = maxDraws > 0 ? maxDraws : Long.MAX_VALUE;
            RRandom rng = new RRandom(seed);
            final double[][] zp = new double[n][]; final double[] qp = new double[n];
            final java.util.concurrent.atomic.AtomicReference<String> firstError = new java.util.concurrent.atomic.AtomicReference<>();
            int valid = 0; long drawn = 0;   // valid = fits without error so far; the first one only takes the place that the observed statistics then occupy
            while (valid < n && drawn < cap) {
                Par.checkCancel(); if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("interrupted");
                int m = (int) Math.min(n - valid, cap - drawn);
                final int[][] batch = new int[m][]; for (int b = 0; b < m; b++) batch[b] = rng.sample(k);   // the stream does not depend on which fits fail
                final double[][] res = new double[m][];
                Par.forRange(m, b -> res[b] = fitPermuted(f, y, v, Xs, batch[b], method, knha, adhoc, icpt, firstError));
                for (int b = 0; b < m; b++) {
                    drawn++;
                    if (res[b] == null) { o.failed++; continue; }
                    if (valid > 0) { zp[valid] = java.util.Arrays.copyOf(res[b], p); qp[valid] = res[b][p]; }
                    valid++;
                }
            }
            o.attempts = drawn;
            if (valid < n)
                throw new IllegalArgumentException("Random permutation test: only " + valid + " of the " + n + " requested valid permuted fits could be obtained in " + drawn + " draws ("
                    + o.failed + " permuted fits failed" + (firstError.get() == null ? "" : "; first error: " + firstError.get()) + "). metafor keeps redrawing without limit; this run was limited to "
                    + cap + " draws. Change the tau\u00B2 estimator or the weights, or use fewer moderators.");
            zp[0] = o.z.clone(); qp[0] = o.QM;   // metafor: the first random permutation is always the observed data (avoids p = 0)
            for (int b = 0; b < n; b++) {
                for (int a = 0; a < p; a++) if (!Double.isNaN(zp[b][a])) { nv[a]++; if (Math.abs(zp[b][a]) >= Math.abs(o.z[a]) - TOL) cnt[a]++; }
                if (!Double.isNaN(qp[b])) { nq++; if (qp[b] >= o.QM - TOL) cq++; }
            }
        }
        // metafor: mean(abs(zval.perm) >= abs(zval) - comptol, na.rm = TRUE); an undefined permuted statistic is left out, and an undefined observed statistic gives NA
        o.pval = new double[p];
        for (int a = 0; a < p; a++) o.pval[a] = Double.isNaN(o.z[a]) || nv[a] == 0 ? Double.NaN : cnt[a] / (double) nv[a];
        o.pQM = Double.isNaN(o.QM) || nq == 0 ? Double.NaN : cq / (double) nq;
        if (!exact && uniq <= 1e6) o.note = "The exact test (all " + (long) uniq + " unique permutations) is available for this model.";
        return o;
    }

    /** Group number (0, 1, ... in the order of the sorted rows) of every row of the sorted model matrix; identical rows share a group. */
    static int[] groupLabels(double[][] Xs) {
        int[] lab = new int[Xs.length]; int g = -1;
        for (int i = 0; i < Xs.length; i++) { if (i == 0 || !java.util.Arrays.equals(Xs[i], Xs[i - 1])) g++; lab[i] = g; }
        return lab;
    }

    /**
     * metafor's X.exact.iter: the number of unique permutations of the rows of the model matrix, round(prod((max(n)+1):k) / prod(factorial(n[-which.max(n)]))) with n the sizes of the groups of identical rows
     * (Infinity when that is not a number or overflows, as in R). The R expression (max+1):k counts downwards when max = k, giving k(k+1); reproduced.
     */
    static double uniquePermutations(int[] lab, int k) {
        int g = 0; for (int l : lab) g = Math.max(g, l + 1);
        int[] size = new int[g]; for (int l : lab) size[l]++;
        int mx = 0, imx = 0; for (int j = 0; j < g; j++) if (size[j] > mx) { mx = size[j]; imx = j; }
        double num = 1; if (mx + 1 <= k) for (int i = mx + 1; i <= k; i++) num *= i; else num = (double) (k + 1) * k;
        double den = 1; for (int j = 0; j < g; j++) if (j != imx) den *= factorial(size[j]);
        double r = Math.rint(num / den);
        return Double.isNaN(r) ? Double.POSITIVE_INFINITY : r;
    }

    private static double factorial(int n) { if (n > 170) return Double.POSITIVE_INFINITY; double f = 1; for (int i = 2; i <= n; i++) f *= i; return f; }

    /** Next permutation of a (with repeated values) in lexicographic order; false (and a unchanged order reversed to the first) after the last one. */
    static boolean nextPermutation(int[] a) {
        int i = a.length - 2; while (i >= 0 && a[i] >= a[i + 1]) i--;
        if (i < 0) { for (int l = 0, r = a.length - 1; l < r; l++, r--) { int t = a[l]; a[l] = a[r]; a[r] = t; } return false; }
        int j = a.length - 1; while (a[j] <= a[i]) j--;
        int t = a[i]; a[i] = a[j]; a[j] = t;
        for (int l = i + 1, r = a.length - 1; l < r; l++, r--) { int u = a[l]; a[l] = a[r]; a[r] = u; }
        return true;
    }

    /** Refits the model with the rows of X in the order pi (effects, variances and GENQ weights stay with the studies). Returns {z[0..p-1], QM}, or null when the fit stops with an error (metafor: try-error). NaN statistics are kept. */
    private static double[] fitPermuted(Rma f, double[] y, double[] v, double[][] X, int[] pi, String method, boolean knha, boolean adhoc, boolean icpt, java.util.concurrent.atomic.AtomicReference<String> firstError) {
        int k = pi.length, p = X[0].length;
        double[][] Xp = new double[k][]; for (int i = 0; i < k; i++) Xp[i] = X[pi[i]];
        try {
            Rma m = Rma.fit(y, v, Xp, method, f.aw);   // GENQ / GENQM: the weights stay with the studies (metafor: permutest keeps the weights, permutes the moderators)
            Reg.Wald W = Reg.wald(m, knha, adhoc, icpt);
            double[] st = new double[p + 1]; System.arraycopy(W.z, 0, st, 0, p); st[p] = W.QM;
            return st;
        } catch (RuntimeException e) {   // singular or non-convergent permuted fit
            if (firstError != null) firstError.compareAndSet(null, e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
            return null;
        }
    }

    /** The rows of X in lexicographic order of their columns (stable), as {@code X[do.call(order, as.data.frame(X)), ]} in R. */
    static double[][] sortedRows(double[][] X) {
        Integer[] idx = new Integer[X.length]; for (int i = 0; i < idx.length; i++) idx[i] = i;
        java.util.Arrays.sort(idx, (a, b) -> { for (int c = 0; c < X[a].length; c++) { if (X[a][c] < X[b][c]) return -1; if (X[a][c] > X[b][c]) return 1; } return 0; });   // Arrays.sort on objects is stable
        double[][] out = new double[X.length][]; for (int i = 0; i < idx.length; i++) out[i] = X[idx[i]];
        return out;
    }

    /** The idx-th permutation of 0..k-1 in lexicographic order (factorial number system). */
    public static int[] unrank(int k, long idx) {
        List<Integer> pool = new ArrayList<>(); for (int i = 0; i < k; i++) pool.add(i);
        long[] f = new long[k + 1]; f[0] = 1; for (int i = 1; i <= k; i++) f[i] = f[i - 1] * i;
        int[] out = new int[k];
        for (int i = 0; i < k; i++) { long d = idx / f[k - 1 - i]; idx %= f[k - 1 - i]; out[i] = pool.remove((int) d); }
        return out;
    }
}
