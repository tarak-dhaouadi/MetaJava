package org.metajava.core;

import java.util.*;

/**
 * Graphical display of study heterogeneity (GOSH; Olkin, Dahabreh and Trikalinos, 2012; metafor::gosh).
 * The random-effects model (inverse variance, selected tau^2 estimator, with the weights of GENQ / GENQM) is fitted to subsets of the studies,
 * as metafor::gosh: every non-empty subset (size >= p = 1, single studies included) when there are few enough, otherwise random subsets whose
 * size is drawn from Binomial(k, 1/2) restricted to 1..k and whose members are a uniform random sample of that size. For every subset
 * the pooled estimate and the heterogeneity (I^2 = tau^2 / (tau^2 + typical sampling variance) as metafor's rma.uni, tau^2, Q) are kept;
 * a subset of one study has tau^2 = 0, Q = 0 and I^2 = 0 (metafor: k = 1). Subsets are generated sequentially from one
 * seeded generator and fitted in parallel, so the result does not depend on the number of threads.
 */
public final class Gosh {
    public int k, nSub; public boolean exhaustive; public long seed; public String method, i2Def;
    public double[] est, i2, tau2, q, nHarm; public int[] size; public long[][] mask; public String[] labels;
    public double fullEst, fullI2, fullTau2; public int failed;
    /** Per study: mean estimate / I^2 over the subsets that include / exclude it. */
    public double[] estIn, estOut, i2In, i2Out; public int[] nIn;
    /** Cluster detection over the subsets (null until {@link #detectClusters} is called). */
    public GoshCluster clusters;
    public static final int MIN_SUBSETS = 1000, MAX_SUBSETS = 500000;

    public static Gosh run(MetaResult r, int maxSubsets, long seed) {
        MetaSpec s = r.spec; List<Integer> use = new ArrayList<>();
        for (int i = 0; i < r.TE.length; i++) if (!Double.isNaN(r.TE[i]) && !Double.isInfinite(r.TE[i]) && !Double.isNaN(r.seTE[i]) && r.seTE[i] > 0) use.add(i);
        final int k = use.size(); if (k < 3) throw new IllegalArgumentException("The GOSH plot needs at least 3 studies.");
        if (k > 400) throw new IllegalArgumentException("The GOSH plot is limited to 400 studies.");
        final double[] y = new double[k], v = new double[k]; final double[] gwAll; { int[] ui = new int[k]; for (int j = 0; j < k; j++) ui[j] = use.get(j); gwAll = Pool.genqWeightsFor(s, ui); }   // GENQ / GENQM weights chosen by the user (null: inverse variances / other estimators)
        final double[] nn = s.nPft != null && s.nPft.length == r.TE.length ? s.nPft : null; final double[] nStudy = nn == null ? null : new double[k];
        Gosh g = new Gosh(); g.k = k; g.seed = seed; g.method = s.methodTau; g.i2Def = "tau2"; g.labels = new String[k];
        for (int j = 0; j < k; j++) { int i = use.get(j); y[j] = r.TE[i]; v[j] = r.seTE[i] * r.seTE[i]; g.labels[j] = r.studlab[i]; if (nn != null) nStudy[j] = nn[i]; }
        final int W = (k + 63) / 64;
        // number of non-empty subsets: 2^k - 1 (metafor: sizes p..k with p = 1)
        double total = Math.pow(2, k) - 1; int want = Math.max(MIN_SUBSETS, Math.min(MAX_SUBSETS, maxSubsets));
        List<long[]> masks = new ArrayList<>();
        if (total <= want) {
            g.exhaustive = true; // k <= 18 or so
            for (long m = 1; m < (1L << k); m++) masks.add(new long[]{m});
        } else {
            // as metafor::gosh: size m ~ prob dbinom(m, k, 1/2) for m = 1..k, then a uniform random subset of that size (partial Fisher-Yates)
            SplittableRandom rng = new SplittableRandom(seed); double[] lf = GoshReg.logFact(k); double[] cum = new double[k]; double tot = 0;
            for (int m = 1; m <= k; m++) { tot += Math.exp(lf[k] - lf[m] - lf[k - m]); cum[m - 1] = tot; }
            int[] perm = new int[k]; for (int i = 0; i < k; i++) perm[i] = i;
            while (masks.size() < want) {
                double u = rng.nextDouble() * tot; int m = 1; while (m < k && cum[m - 1] < u) m++;
                for (int i = 0; i < m; i++) { int j = i + rng.nextInt(k - i); int t = perm[i]; perm[i] = perm[j]; perm[j] = t; }
                long[] mk = new long[W]; for (int i = 0; i < m; i++) mk[perm[i] >> 6] |= 1L << (perm[i] & 63); masks.add(mk);
            }
        }
        final int n = masks.size(); g.nSub = n; g.mask = masks.toArray(new long[0][]);
        g.est = new double[n]; g.i2 = new double[n]; g.tau2 = new double[n]; g.q = new double[n]; g.size = new int[n]; if (nStudy != null) g.nHarm = new double[n];
        final boolean qDef = false;   // metafor: I^2 of a random-effects fit is tau^2 / (tau^2 + vt), whatever the estimator (the Q-based I^2 is the fixed-effect definition)
        final String method = s.methodTau; final long[][] M = g.mask;
        Par.forRange(n, b -> {
            long[] m = M[b]; int c = 0; for (long x : m) c += Long.bitCount(x);
            double[] yy = new double[c], vv = new double[c], ww = gwAll == null ? null : new double[c]; int t = 0; double hs = 0; int hc = 0;
            for (int j = 0; j < k; j++) if ((m[j >> 6] >>> (j & 63) & 1L) != 0) { yy[t] = y[j]; vv[t] = v[j]; if (ww != null) ww[t] = gwAll[j]; t++; if (nStudy != null && nStudy[j] > 0) { hs += 1 / nStudy[j]; hc++; } }
            try {
                if (c == 1) { g.est[b] = yy[0]; g.i2[b] = 0; g.tau2[b] = 0; g.q[b] = 0; g.size[b] = 1; if (g.nHarm != null) g.nHarm[b] = hc == 0 ? Double.NaN : hc / hs; return; }   // metafor, k = 1: tau^2 = 0, Q = 0, I^2 = 0
                Rma f = Rma.fit(yy, vv, null, method, ww);
                double ii = qDef ? (f.QE > 0 ? Math.max(0, (f.QE - (c - 1)) / f.QE) : 0) : f.tau2 / (f.tau2 + Rma.typicalV(c, 1, vv, f.X));
                g.est[b] = f.beta[0]; g.i2[b] = 100 * ii; g.tau2[b] = f.tau2; g.q[b] = f.QE; g.size[b] = c; if (g.nHarm != null) g.nHarm[b] = hc == 0 ? Double.NaN : hc / hs;
            } catch (RuntimeException e) { g.est[b] = Double.NaN; g.i2[b] = Double.NaN; g.tau2[b] = Double.NaN; g.q[b] = Double.NaN; g.size[b] = c; if (g.nHarm != null) g.nHarm[b] = Double.NaN; }
        });
        for (int b = 0; b < n; b++) if (Double.isNaN(g.est[b]) || Double.isNaN(g.i2[b])) g.failed++;
        // reference values: all studies
        Rma f = Rma.fit(y, v, null, method, gwAll); g.fullEst = f.beta[0]; g.fullTau2 = f.tau2;
        g.fullI2 = 100 * (qDef ? (f.QE > 0 ? Math.max(0, (f.QE - (k - 1)) / f.QE) : 0) : f.tau2 / (f.tau2 + Rma.typicalV(k, 1, v, f.X)));
        // in / out means
        g.estIn = new double[k]; g.estOut = new double[k]; g.i2In = new double[k]; g.i2Out = new double[k]; g.nIn = new int[k];
        final Gosh G = g;
        Par.forRange(k, j -> {   // one study per task; the subsets are visited in the same order as before, so the sums (and the results) are identical
            double sEi = 0, sEo = 0, sIi = 0, sIo = 0; int ni = 0, no = 0;
            for (int b = 0; b < n; b++) { if (Double.isNaN(G.est[b]) || Double.isNaN(G.i2[b])) continue;
                if ((M[b][j >> 6] >>> (j & 63) & 1L) != 0) { sEi += G.est[b]; sIi += G.i2[b]; ni++; } else { sEo += G.est[b]; sIo += G.i2[b]; no++; } }
            G.nIn[j] = ni; G.estIn[j] = ni > 0 ? sEi / ni : Double.NaN; G.i2In[j] = ni > 0 ? sIi / ni : Double.NaN; G.estOut[j] = no > 0 ? sEo / no : Double.NaN; G.i2Out[j] = no > 0 ? sIo / no : Double.NaN;
        });
        return g;
    }

    /** Detects clusters among the (estimate, I^2) points of the valid subsets; the result is kept in {@link #clusters}. */
    public GoshCluster detectClusters() { return detectClusters(GoshCluster.MIN_SILHOUETTE); }
    /** @param minSil smallest silhouette width a cluster needs in the effects (the estimate / the coefficients) to be reported; heterogeneity route with its default thresholds, see {@link GoshCluster} */
    public GoshCluster detectClusters(double minSil) { return detectClusters(minSil, GoshCluster.HET_MIN_SILHOUETTE, GoshCluster.HET_MIN_SHARE); }
    /**
     * @param minSil smallest silhouette width a cluster needs in the effects (the estimate / the coefficients) to be reported
     * @param hetMinSil smallest silhouette width in ln(Q_E/df) for a cluster that is accepted through the heterogeneity instead
     * @param hetMinShare smallest share of the valid subsets such a cluster must contain; see {@link GoshCluster}
     */
    public GoshCluster detectClusters(double minSil, double hetMinSil, double hetMinShare) {
        double[][] X = new double[nSub][]; for (int b = 0; b < nSub; b++) X[b] = new double[]{est[b], i2[b]};
        double[] hf = new double[nSub]; for (int b = 0; b < nSub; b++) hf[b] = GoshCluster.logQ(q[b], size[b] - 1);
        clusters = GoshCluster.detect(X, new String[]{"estimate", "I^2"}, mask, k, size, seed, 1, minSil, GoshCluster.MIN_SHARE, hf, hetMinSil, hetMinShare); return clusters;
    }

    public boolean includes(int b, int j) { return (mask[b][j >> 6] >>> (j & 63) & 1L) != 0; }

    /** Study whose inclusion changes the mean I^2 over the subsets the most (largest |mean I^2 in - mean I^2 out|). */
    public int mostInfluential() { int best = 0; double bd = -1; for (int j = 0; j < k; j++) { double d = Math.abs(i2In[j] - i2Out[j]); if (!Double.isNaN(d) && d > bd) { bd = d; best = j; } } return best; }

    /** Quantile (type 7) of the finite values. */
    public static double quantile(double[] x, double p) {
        int c = 0; for (double d : x) if (!Double.isNaN(d)) c++; double[] a = new double[c]; c = 0; for (double d : x) if (!Double.isNaN(d)) a[c++] = d; Arrays.sort(a);
        if (a.length == 0) return Double.NaN; double h = (a.length - 1) * p; int lo = (int) Math.floor(h); int hi = Math.min(a.length - 1, lo + 1); return a[lo] + (h - lo) * (a[hi] - a[lo]);
    }
}
