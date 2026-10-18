package org.metajava.core;

import java.util.*;

/**
 * Permutation test for a meta-regression model (metafor::permutest.rma.uni).
 * The rows of the model matrix are permuted (effects and sampling variances stay in place), the model is refitted
 * (tau^2 is re-estimated every time) and the Wald statistics of the coefficients and the omnibus statistic QM are
 * compared with the observed ones: p = share of permutations with |z*| >= |z| (QM* >= QM). With Knapp-Hartung
 * adjustment the statistics are t and F, as in metafor.
 * Exact: all k! permutations are enumerated (feasible up to k = 9). Random: iter random permutations; as recommended by
 * Phipson and Smyth (2010) the observed arrangement counts as one of them, p = (r + 1) / (iter + 1), so p is never 0.
 */
public final class Perm {
    public String[] coefNames; public int k, p, iter, failed; public boolean exact, knha; public long nPerm, seed;
    public double[] z, pval; public double QM = Double.NaN, pQM = Double.NaN; public int dfQM; public String note = "";
    public static final int MAX_EXACT_K = 9;
    static final double TOL = Math.sqrt(Math.ulp(1.0));

    /** @param wantExact use the exact test when k <= 9 (always when k! does not exceed iter) */
    public static Perm run(Reg g, int iter, boolean wantExact, long seed) {
        final Rma f = g.fit; final int k = f.k, p = f.p; final double[] y = f.yi, v = f.vi; final double[][] X = f.X; final String method = g.method;
        if (g.dfQM <= 0) throw new IllegalArgumentException("The permutation test needs at least one moderator.");
        Perm o = new Perm(); o.k = k; o.p = p; o.coefNames = g.coefNames; o.knha = g.knha; o.seed = seed; o.dfQM = g.dfQM;
        o.z = g.zval.clone(); o.QM = g.QM;
        double fact = 1; for (int i = 2; i <= k && fact < 1e12; i++) fact *= i;
        boolean exact = k <= MAX_EXACT_K && (wantExact || fact <= iter);
        o.exact = exact; o.iter = exact ? (int) fact : Math.max(1, iter); o.nPerm = o.iter;
        final int n = o.iter; final int[][] perms = exact ? null : randomPerms(k, n, seed);
        final double[][] zp = new double[n][]; final double[] qp = new double[n];
        final boolean knha = g.knha, adhoc = g.adhocSe, icpt = g.intercept;
        Par.forRange(n, b -> {
            int[] pi = exact ? unrank(k, b) : perms[b];
            double[][] Xp = new double[k][]; for (int i = 0; i < k; i++) Xp[i] = X[pi[i]];
            try {
                Rma m = Rma.fit(y, v, Xp, method, f.aw);   // GENQ / GENQM: the weights stay with the studies (metafor: permutest keeps the weights, permutes the moderators)
                Reg.Wald W = Reg.wald(m, knha, adhoc, icpt);
                zp[b] = W.z; qp[b] = W.QM;   // NaN statistics are kept: as in metafor, each statistic drops its own NA permutations (na.rm = TRUE), a fit that stops with an error is dropped as a whole
            } catch (RuntimeException e) { /* singular permuted fit: dropped, as metafor does */ }
        });
        int ok = 0; for (int b = 0; b < n; b++) if (zp[b] != null) ok++;
        o.failed = n - ok; o.pval = new double[p];
        // metafor: mean(abs(zval.perm) >= abs(zval) - comptol, na.rm = TRUE); an undefined permuted statistic is left out, and an undefined observed statistic gives NA
        for (int a = 0; a < p; a++) {
            int cnt = 0, nv = 0;
            for (int b = 0; b < n; b++) { if (zp[b] == null || Double.isNaN(zp[b][a])) continue; nv++; if (Math.abs(zp[b][a]) >= Math.abs(o.z[a]) - TOL) cnt++; }
            o.pval[a] = Double.isNaN(o.z[a]) ? Double.NaN : exact ? cnt / (double) nv : Math.min(1, (cnt + 1.0) / (nv + 1.0));
        }
        int cq = 0, nq = 0;
        for (int b = 0; b < n; b++) { if (zp[b] == null || Double.isNaN(qp[b])) continue; nq++; if (qp[b] >= o.QM - TOL) cq++; }
        o.pQM = Double.isNaN(o.QM) ? Double.NaN : exact ? cq / (double) nq : Math.min(1, (cq + 1.0) / (nq + 1.0));
        if (!exact && k <= MAX_EXACT_K) o.note = "The exact test (all " + (long) fact + " permutations) is available for this k.";
        if (!exact && wantExact && k > MAX_EXACT_K) o.note = "Exact test requested but k = " + k + " > " + MAX_EXACT_K + " (k! permutations): random permutations were used.";
        return o;
    }

    /** iter random permutations (Fisher-Yates), drawn sequentially from one seeded generator so the result does not depend on the number of threads. */
    static int[][] randomPerms(int k, int iter, long seed) {
        SplittableRandom rng = new SplittableRandom(seed); int[][] out = new int[iter][k];
        for (int b = 0; b < iter; b++) { int[] a = out[b]; for (int i = 0; i < k; i++) a[i] = i; for (int i = k - 1; i > 0; i--) { int j = rng.nextInt(i + 1); int t = a[i]; a[i] = a[j]; a[j] = t; } }
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
