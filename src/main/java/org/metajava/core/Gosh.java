package org.metajava.core;

import java.util.*;

/**
 * Graphical display of study heterogeneity (GOSH; Olkin, Dahabreh and Trikalinos, 2012; metafor::gosh).
 * The random-effects model (inverse variance, selected tau^2 estimator) is fitted to subsets of the studies (at least two):
 * all of them when there are few enough, otherwise random subsets (each study included with probability 1/2). For every subset
 * the pooled estimate and the heterogeneity (I^2 as selected, tau^2, Q) are kept. Subsets are generated sequentially from one
 * seeded generator and fitted in parallel, so the result does not depend on the number of threads.
 */
public final class Gosh {
    public int k, nSub; public boolean exhaustive; public long seed; public String method, i2Def;
    public double[] est, i2, tau2, q, nHarm; public int[] size; public long[][] mask; public String[] labels;
    public double fullEst, fullI2, fullTau2; public int failed;
    /** Per study: mean estimate / I^2 over the subsets that include / exclude it. */
    public double[] estIn, estOut, i2In, i2Out; public int[] nIn;
    public static final int MIN_SUBSETS = 1000, MAX_SUBSETS = 500000;

    public static Gosh run(MetaResult r, int maxSubsets, long seed) {
        MetaSpec s = r.spec; List<Integer> use = new ArrayList<>();
        for (int i = 0; i < r.TE.length; i++) if (!Double.isNaN(r.TE[i]) && !Double.isInfinite(r.TE[i]) && !Double.isNaN(r.seTE[i]) && r.seTE[i] > 0) use.add(i);
        final int k = use.size(); if (k < 3) throw new IllegalArgumentException("The GOSH plot needs at least 3 studies.");
        if (k > 400) throw new IllegalArgumentException("The GOSH plot is limited to 400 studies.");
        final double[] y = new double[k], v = new double[k]; final double[] nn = s.nPft != null && s.nPft.length == r.TE.length ? s.nPft : null; final double[] nStudy = nn == null ? null : new double[k];
        Gosh g = new Gosh(); g.k = k; g.seed = seed; g.method = s.methodTau; g.i2Def = s.methodI2; g.labels = new String[k];
        for (int j = 0; j < k; j++) { int i = use.get(j); y[j] = r.TE[i]; v[j] = r.seTE[i] * r.seTE[i]; g.labels[j] = r.studlab[i]; if (nn != null) nStudy[j] = nn[i]; }
        final int W = (k + 63) / 64;
        // number of subsets of size >= 2: 2^k - 1 - k
        double total = Math.pow(2, k) - 1 - k; int want = Math.max(MIN_SUBSETS, Math.min(MAX_SUBSETS, maxSubsets));
        List<long[]> masks = new ArrayList<>();
        if (total <= want) {
            g.exhaustive = true; // k <= 18 or so
            for (long m = 1; m < (1L << k); m++) if (Long.bitCount(m) >= 2) masks.add(new long[]{m});
        } else {
            SplittableRandom rng = new SplittableRandom(seed);
            while (masks.size() < want) {
                long[] m = new long[W]; int c = 0;
                for (int w = 0; w < W; w++) { int bits = Math.min(64, k - 64 * w); long x = rng.nextLong(); if (bits < 64) x &= (1L << bits) - 1; m[w] = x; c += Long.bitCount(x); }
                if (c >= 2) masks.add(m);
            }
        }
        final int n = masks.size(); g.nSub = n; g.mask = masks.toArray(new long[0][]);
        g.est = new double[n]; g.i2 = new double[n]; g.tau2 = new double[n]; g.q = new double[n]; g.size = new int[n]; if (nStudy != null) g.nHarm = new double[n];
        final boolean qDef = !"tau2".equals(s.methodI2); final String method = s.methodTau; final long[][] M = g.mask;
        Par.forRange(n, b -> {
            long[] m = M[b]; int c = 0; for (long x : m) c += Long.bitCount(x);
            double[] yy = new double[c], vv = new double[c]; int t = 0; double hs = 0; int hc = 0;
            for (int j = 0; j < k; j++) if ((m[j >> 6] >>> (j & 63) & 1L) != 0) { yy[t] = y[j]; vv[t] = v[j]; t++; if (nStudy != null && nStudy[j] > 0) { hs += 1 / nStudy[j]; hc++; } }
            try {
                Rma f = Rma.fit(yy, vv, null, method);
                double ii = qDef ? (f.QE > 0 ? Math.max(0, (f.QE - (c - 1)) / f.QE) : 0) : f.tau2 / (f.tau2 + Rma.typicalV(c, 1, vv, f.X));
                g.est[b] = f.beta[0]; g.i2[b] = 100 * ii; g.tau2[b] = f.tau2; g.q[b] = f.QE; g.size[b] = c; if (g.nHarm != null) g.nHarm[b] = hc == 0 ? Double.NaN : hc / hs;
            } catch (RuntimeException e) { g.est[b] = Double.NaN; g.i2[b] = Double.NaN; g.tau2[b] = Double.NaN; g.q[b] = Double.NaN; g.size[b] = c; if (g.nHarm != null) g.nHarm[b] = Double.NaN; }
        });
        for (int b = 0; b < n; b++) if (Double.isNaN(g.est[b]) || Double.isNaN(g.i2[b])) g.failed++;
        // reference values: all studies
        Rma f = Rma.fit(y, v, null, method); g.fullEst = f.beta[0]; g.fullTau2 = f.tau2;
        g.fullI2 = 100 * (qDef ? (f.QE > 0 ? Math.max(0, (f.QE - (k - 1)) / f.QE) : 0) : f.tau2 / (f.tau2 + Rma.typicalV(k, 1, v, f.X)));
        // in / out means
        g.estIn = new double[k]; g.estOut = new double[k]; g.i2In = new double[k]; g.i2Out = new double[k]; g.nIn = new int[k];
        double[] sEi = new double[k], sEo = new double[k], sIi = new double[k], sIo = new double[k]; int[] nOut = new int[k];
        for (int b = 0; b < n; b++) { if (Double.isNaN(g.est[b]) || Double.isNaN(g.i2[b])) continue; long[] m = M[b];
            for (int j = 0; j < k; j++) { if ((m[j >> 6] >>> (j & 63) & 1L) != 0) { sEi[j] += g.est[b]; sIi[j] += g.i2[b]; g.nIn[j]++; } else { sEo[j] += g.est[b]; sIo[j] += g.i2[b]; nOut[j]++; } } }
        for (int j = 0; j < k; j++) { g.estIn[j] = g.nIn[j] > 0 ? sEi[j] / g.nIn[j] : Double.NaN; g.i2In[j] = g.nIn[j] > 0 ? sIi[j] / g.nIn[j] : Double.NaN; g.estOut[j] = nOut[j] > 0 ? sEo[j] / nOut[j] : Double.NaN; g.i2Out[j] = nOut[j] > 0 ? sIo[j] / nOut[j] : Double.NaN; }
        return g;
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
