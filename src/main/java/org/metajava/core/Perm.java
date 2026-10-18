package org.metajava.core;

import java.util.*;

/**
 * Permutation test for a meta-regression model (metafor::permutest.rma.uni).
 * The rows of the model matrix are permuted (effects and sampling variances stay in place), the model is refitted
 * (tau^2 is re-estimated every time) and the Wald statistics of the coefficients and the omnibus statistic QM are
 * compared with the observed ones: p = share of permutations with |z*| >= |z| (QM* >= QM). With Knapp-Hartung
 * adjustment the statistics are t and F, as in metafor.
 * Exact or random, as metafor decides: the number of <em>unique</em> permutations of the rows of the model matrix (identical rows count once:
 * k!/(n1! n2! ...) for groups of identical rows, {@link #uniqueCount}) is compared with {@code iter}; the exact test enumerates them all
 * when {@code exact = TRUE} or when that number does not exceed {@code iter}. In the exact test a permuted fit that stops with an error leaves its slot undefined.
 * Random: as metafor, {@code iter} slots are filled one after the other with the statistics of a permuted fit; a permutation whose fit
 * stops with an error is redrawn without limit (so exactly {@code iter} valid fits are made; the analysis can be cancelled, see {@link Par#checkCancel}), and the first slot is then replaced by the observed
 * statistics (Phipson and Smyth, 2010: the observed arrangement is one of the {@code iter} permutations, p = hits / iter is never 0).
 * The permutations are drawn with R's generator and {@code sample(k)} from the rows of the model matrix sorted as metafor sorts them,
 * so a seed gives the permutations metafor draws after {@code set.seed(seed)}.
 */
public final class Perm {
    public String[] coefNames; public int k, p, iter; public long failed; public boolean exact, knha; public long nPerm, seed, attempts; public Plan plan;
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
    public static Perm run(Reg g, int iter, boolean wantExact, long seed, long maxDraws) { return run(g, iter, wantExact, seed, maxDraws, defaultBudgetSeconds()); }

    /**
     * @param exactBudgetSeconds longest wall-clock time an <em>exact</em> test may be estimated to need (measured from timed fits of this model, not a limit on k); longer exact tests are refused with an {@link InfeasibleExactTest}
     */
    public static Perm run(Reg g, int iter, boolean wantExact, long seed, long maxDraws, double exactBudgetSeconds) {
        final Rma f = g.fit; final int k = f.k, p = f.p; final double[] y = f.yi, v = f.vi; final String method = g.method;
        if (g.dfQM <= 0) throw new IllegalArgumentException("The permutation test needs at least one moderator.");
        Perm o = new Perm(); o.k = k; o.p = p; o.coefNames = g.coefNames; o.knha = g.knha; o.seed = seed; o.dfQM = g.dfQM;
        o.z = g.zval.clone(); o.QM = g.QM;
        iter = Math.max(2, iter);   // metafor: 'iter' must be >= 2
        final Plan pl = plan(g, iter, wantExact, exactBudgetSeconds); o.plan = pl;   // the number of unique permutations is known before anything is generated or fitted
        if (pl.exact && !pl.feasible) throw new InfeasibleExactTest(pl);
        final double[][] Xs = sortedRows(f.X); final int[] lab = groupLabels(Xs);
        boolean exact = pl.exact;
        final long total = exact ? pl.unique.longValueExact() : iter;
        o.exact = exact; o.iter = (int) Math.min(total, Integer.MAX_VALUE); o.nPerm = total;
        final int n = (int) Math.min(total, Integer.MAX_VALUE);   // random test only: iter is an int and its arrays are n long
        final boolean knha = g.knha, adhoc = g.adhocSe, icpt = g.intercept;
        final long[] cnt = new long[p], nv = new long[p]; long cq = 0, nq = 0;
        if (exact) {
            // all unique permutations of the group labels in lexicographic order (what metafor's .genuperms() returns), generated chunk by chunk (nothing proportional to the number of permutations is stored);
            // the rows of a group are identical, so any of them can stand for it
            int g0 = 0; for (int l : lab) g0 = Math.max(g0, l + 1);
            final double[][] rep = new double[g0][]; for (int i = 0; i < k; i++) rep[lab[i]] = Xs[i];
            int[] cur = lab.clone(); long done = 0;
            while (done < total) {
                Par.checkCancel();
                int m = (int) Math.min(CHUNK, total - done); final int[][] batch = new int[m][]; final double[][] res = new double[m][];
                for (int b = 0; b < m; b++) { batch[b] = cur.clone(); nextPermutation(cur); }
                Par.forRange(m, b -> res[b] = fitPermuted(f, y, v, rep, batch[b], method, knha, adhoc, icpt, null));
                for (int b = 0; b < m; b++) {
                    double[] st = res[b]; if (st == null) { o.failed++; continue; }
                    for (int a = 0; a < p; a++) if (!Double.isNaN(st[a])) { nv[a]++; if (Math.abs(st[a]) >= Math.abs(o.z[a]) - TOL) cnt[a]++; }
                    if (!Double.isNaN(st[p])) { nq++; if (st[p] >= o.QM - TOL) cq++; }
                }
                done += m;
            }
            o.attempts = total;
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
        if (!exact && pl.unique.compareTo(java.math.BigInteger.valueOf(1_000_000)) <= 0) o.note = "The exact test (all " + String.format(java.util.Locale.ROOT, "%,d", pl.unique) + " unique permutations) is available for this model.";
        return o;
    }

    /** Group number (0, 1, ... in the order of the sorted rows) of every row of the sorted model matrix; identical rows share a group. */
    static int[] groupLabels(double[][] Xs) {
        int[] lab = new int[Xs.length]; int g = -1;
        for (int i = 0; i < Xs.length; i++) { if (i == 0 || !java.util.Arrays.equals(Xs[i], Xs[i - 1])) g++; lab[i] = g; }
        return lab;
    }

    static int[] groupSizes(int[] lab) { int g = 0; for (int l : lab) g = Math.max(g, l + 1); int[] size = new int[g]; for (int l : lab) size[l]++; return size; }

    /** Number of unique permutations of the rows, exactly: k! / (n1! n2! ...) for groups of identical rows of sizes n1, n2, ... */
    public static java.math.BigInteger uniqueCount(int[] sizes) {
        int k = 0; for (int s : sizes) k += s;
        java.math.BigInteger r = java.math.BigInteger.ONE; int placed = 0;
        for (int s : sizes) { for (int i = 1; i <= s; i++) { placed++; r = r.multiply(java.math.BigInteger.valueOf(placed)).divide(java.math.BigInteger.valueOf(i)); } }   // product of binomials, every step exact
        return r;
    }

    /**
     * metafor's X.exact.iter, in double arithmetic as R computes it: round(prod((max(n)+1):k) / prod(factorial(n[-which.max(n)]))) (Infinity when that is not a number or overflows).
     * The R expression (max+1):k counts downwards when max = k, giving k(k+1); reproduced. Kept to compare with {@link #uniqueCount}.
     */
    public static double metaforCount(int[] size, int k) {
        int mx = 0, imx = 0; for (int j = 0; j < size.length; j++) if (size[j] > mx) { mx = size[j]; imx = j; }
        double num = 1; if (mx + 1 <= k) for (int i = mx + 1; i <= k; i++) num *= i; else num = (double) (k + 1) * k;
        double den = 1; for (int j = 0; j < size.length; j++) if (j != imx) den *= factorial(size[j]);
        double r = Math.rint(num / den);
        return Double.isNaN(r) ? Double.POSITIVE_INFINITY : r;
    }

    static java.math.BigInteger factorialBig(int n) { java.math.BigInteger r = java.math.BigInteger.ONE; for (int i = 2; i <= n; i++) r = r.multiply(java.math.BigInteger.valueOf(i)); return r; }

    private static double factorial(int n) { if (n > 170) return Double.POSITIVE_INFINITY; double f = 1; for (int i = 2; i <= n; i++) f *= i; return f; }

    /** Default time budget of an exact test in seconds: system property metajava.exactBudget, else 600. */
    public static double defaultBudgetSeconds() { try { return Double.parseDouble(System.getProperty("metajava.exactBudget", "600")); } catch (NumberFormatException e) { return 600; } }

    /** What the permutation test is going to do, known before any permutation is generated or fitted. */
    public static final class Plan {
        public int k, p, iter; public int[] groupSizes; public java.math.BigInteger unique, kFactorial; public double metaforCount;
        public boolean exactRequested, exact, feasible = true; public double secondsPerFit = Double.NaN, estimatedSeconds = Double.NaN, budgetSeconds; public int threads; public String reason = "";
        static String n(java.math.BigInteger b) { return String.format(java.util.Locale.ROOT, "%,d", b); }
        static String big(java.math.BigInteger b) { return b.bitLength() < 63 ? n(b) : new java.math.BigDecimal(b).round(new java.math.MathContext(3)).toString() + " (" + b.toString().length() + " digits)"; }
        public static String dur(double s) { return s < 1 ? String.format(java.util.Locale.ROOT, "%.2f s", s) : s < 120 ? String.format(java.util.Locale.ROOT, "%.0f s", s) : s < 7200 ? String.format(java.util.Locale.ROOT, "%.0f min", s / 60) : s < 172800 ? String.format(java.util.Locale.ROOT, "%.1f h", s / 3600) : s < 3.15e7 ? String.format(java.util.Locale.ROOT, "%.0f days", s / 86400) : String.format(java.util.Locale.ROOT, "%.3g years", s / 3.156e7); }
        /** One-paragraph description of the model matrix, the permutation count, the decision and, for an exact test, the time it needs. */
        public String describe() {
            StringBuilder b = new StringBuilder();
            b.append("Model matrix: k = ").append(k).append(" rows, ").append(groupSizes.length).append(" distinct row").append(groupSizes.length == 1 ? "" : "s").append(" (group sizes ").append(java.util.Arrays.toString(groupSizes).replace("[", "").replace("]", "").replace(",", " +")).append("). ");
            b.append("Unique permutations: ").append(big(unique)).append(" (k! = ").append(big(kFactorial)).append("; identical rows count once, as in metafor").append(groupSizes.length == k ? ", here all rows differ so k! applies" : "").append(").");
            if (!Double.isNaN(metaforCount) && (metaforCount == Double.POSITIVE_INFINITY || new java.math.BigDecimal(unique).subtract(new java.math.BigDecimal(metaforCount)).abs().compareTo(new java.math.BigDecimal(unique).multiply(new java.math.BigDecimal("1e-12"))) > 0)) b.append(" metafor's double-precision count: ").append(metaforCount).append(".");
            b.append(exact ? (exactRequested ? " Exact test requested." : " The number does not exceed iter = " + iter + ", so the test is exact (as metafor).") : " Random test with iter = " + iter + " (the number exceeds iter and no exact test was requested).");
            if (exact && !Double.isNaN(estimatedSeconds)) b.append(" Estimated time: ").append(dur(estimatedSeconds)).append(" (about ").append(String.format(java.util.Locale.ROOT, "%.3g", secondsPerFit * 1e6)).append(" \u00B5s per permuted fit on ").append(threads).append(" thread").append(threads == 1 ? "" : "s").append(").");
            if (!feasible) b.append(' ').append(reason);
            return b.toString();
        }
    }

    /** Thrown instead of running an exact test that cannot be finished within the time budget; never replaced by an approximate test. */
    public static final class InfeasibleExactTest extends IllegalArgumentException {
        public final transient Plan plan;
        InfeasibleExactTest(Plan pl) {
            super("The exact permutation test was not run. " + pl.describe() + "\nThis is a limit of computing time, not of memory: the permutations are generated one chunk at a time and none are stored. "
                + "To use a random permutation test instead, switch off the exact test and choose the number of permutations (for example 5000); the result is then labelled a random test and is not exact. "
                + "To wait for the exact test anyway, raise the time budget (the box \"Exact test time budget (s)\" next to the exact-test checkbox, or the Java option -Dmetajava.exactBudget=SECONDS).");
            plan = pl;
        }
    }

    /**
     * Counts the unique permutations and decides, before any permutation is generated: exact when requested or when the count does not exceed {@code iter} (metafor), else random.
     * For an exact test the time is estimated from timed permuted fits of this very model; the test is infeasible when it cannot be enumerated at all (more than 2^63 - 1 permutations) or is estimated to take longer than {@code budgetSeconds}.
     */
    public static Plan plan(Reg g, int iter, boolean wantExact, double budgetSeconds) {
        final Rma f = g.fit; Plan pl = new Plan(); pl.k = f.k; pl.p = f.p; pl.iter = Math.max(2, iter); pl.exactRequested = wantExact; pl.budgetSeconds = budgetSeconds;
        final double[][] Xs = sortedRows(f.X); final int[] lab = groupLabels(Xs); pl.groupSizes = groupSizes(lab); java.util.Arrays.sort(pl.groupSizes);
        for (int i = 0, j = pl.groupSizes.length - 1; i < j; i++, j--) { int t = pl.groupSizes[i]; pl.groupSizes[i] = pl.groupSizes[j]; pl.groupSizes[j] = t; }   // largest group first
        pl.unique = uniqueCount(pl.groupSizes); pl.kFactorial = factorialBig(f.k); pl.metaforCount = metaforCount(pl.groupSizes, f.k);
        pl.exact = wantExact || pl.unique.compareTo(java.math.BigInteger.valueOf(pl.iter)) <= 0;
        if (!pl.exact) return pl;
        pl.threads = Par.threads();
        if (pl.unique.bitLength() > 63) { pl.feasible = false; pl.reason = "That is more permutations than can be counted in a 64-bit integer (2^63 - 1), so they cannot be enumerated."; return pl; }
        if (pl.unique.compareTo(java.math.BigInteger.valueOf(pl.iter)) <= 0) { pl.estimatedSeconds = 0; return pl; }   // at most iter fits: never more work than the random test the user asked for
        // time permuted fits of this model (a few random arrangements, one thread) to estimate the exact test
        final int[] size = pl.groupSizes; int[] cur = lab.clone(); int g0 = size.length; double[][] rep = new double[g0][]; for (int i = 0; i < f.k; i++) rep[lab[i]] = Xs[i];
        RRandom rng = new RRandom(1); long tw = System.nanoTime(); for (int w = 0; w < 300 && System.nanoTime() - tw < 4e8; w++) { int[] pi = new int[f.k]; int[] sm = rng.sample(f.k); for (int i = 0; i < f.k; i++) pi[i] = lab[sm[i]]; fitPermuted(f, f.yi, f.vi, rep, pi, g.method, g.knha, g.adhocSe, g.intercept, null); }   // untimed warm-up (JIT)
        long t0 = System.nanoTime(); int done = 0;
        while (done < 25 && (done < 5 || System.nanoTime() - t0 < 2e8)) { int[] pi = new int[f.k]; int[] sm = rng.sample(f.k); for (int i = 0; i < f.k; i++) pi[i] = lab[sm[i]]; fitPermuted(f, f.yi, f.vi, rep, pi, g.method, g.knha, g.adhocSe, g.intercept, null); done++; }
        pl.secondsPerFit = (System.nanoTime() - t0) / 1e9 / done;
        pl.estimatedSeconds = pl.unique.doubleValue() * pl.secondsPerFit / Math.max(1, Math.min(pl.threads, Runtime.getRuntime().availableProcessors()));
        if (pl.estimatedSeconds > budgetSeconds) { pl.feasible = false; pl.reason = "That is estimated to take longer than the time budget of " + Plan.dur(budgetSeconds) + "."; }
        return pl;
    }

    /** Next permutation of a (with repeated values) in lexicographic order; false (and a unchanged order reversed to the first) after the last one. */
    public static boolean nextPermutation(int[] a) {
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
