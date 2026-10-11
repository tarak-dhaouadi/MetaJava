package org.metajava.core;

import java.util.*;

/**
 * GOSH analysis for a meta-regression model (Olkin, Dahabreh and Trikalinos, 2012; metafor::gosh.rma for models with moderators).
 * The mixed-effects model with the moderators chosen for the meta-regression is fitted to subsets of the studies and, for every
 * subset, all regression coefficients and the residual heterogeneity (I^2 as selected, tau^2, Q_E) are kept.
 * <p>Subsets follow metafor: a subset needs at least p studies (p = number of coefficients, intercept included). When the number of
 * possible subsets, sum over m = p..k of choose(k, m), does not exceed the number requested, all of them are fitted; otherwise
 * random subsets are drawn: the size m is drawn with probability proportional to the Binomial(k, 1/2) mass on p..k and the studies are
 * then drawn without replacement, from one seeded generator (fits run in parallel, so results do not depend on the number of threads).</p>
 * <p>A subset is <i>valid</i> when all coefficients are estimable and the residual heterogeneity is defined. Not valid are subsets whose
 * model matrix is rank deficient (a moderator without variation, a factor level that is absent or perfectly confounded with another
 * term; metafor's {@code coef.na}), saturated subsets (k = p: the coefficients are determined exactly but there are no residual degrees
 * of freedom, so Q_E, I^2 and tau^2 are not defined) and subsets in which the fit failed. Their numbers are reported.</p>
 */
public final class GoshReg {
    public static final byte OK = 0, SATURATED = 1, RANK_DEFICIENT = 2, FAILED = 3;

    public int k, p, nSub, nValid; public double nTotal; public boolean exhaustive; public long seed; public String method, i2Def; public String[] coefNames; public boolean intercept;
    public int nSaturated, nRankDef, nFailed;
    /** Per subset: status, number of studies, inclusion mask. */
    public byte[] status; public int[] size; public long[][] mask; public String[] labels;
    /** coef[a][b]: coefficient a of subset b (NaN when the subset is not valid). */
    public double[][] coef; public double[] i2, tau2, qe;
    /** The model fitted to all studies. */
    public double[] fullCoef; public double fullI2, fullTau2, fullQE;
    /** Per study: mean residual I^2 and mean of each coefficient over the valid subsets that include / exclude the study. */
    public double[] i2In, i2Out, tau2In, tau2Out; public double[][] coefIn, coefOut; public int[] nIn;
    public GoshCluster clusters;

    public boolean includes(int b, int j) { return (mask[b][j >> 6] >>> (j & 63) & 1L) != 0; }
    /** The study whose inclusion changes the mean residual I^2 most (same definition as for the ordinary GOSH analysis); used to colour the subsets by inclusion of that study. */
    public int mostInfluential() { int best = 0; double bd = -1; for (int j = 0; j < k; j++) { double d = Math.abs(i2In[j] - i2Out[j]); if (!Double.isNaN(d) && d > bd) { bd = d; best = j; } } return best; }

    public boolean valid(int b) { return status[b] == OK; }

    /** Number of possible subsets with at least p of k studies: sum over m = p..k of choose(k, m). */
    public static double totalSubsets(int k, int p) {
        double[] lf = logFact(k); double s = 0; for (int m = p; m <= k; m++) s += Math.exp(lf[k] - lf[m] - lf[k - m]); return Math.rint(s);
    }
    static double[] logFact(int n) { double[] f = new double[n + 1]; for (int i = 2; i <= n; i++) f[i] = f[i - 1] + Math.log(i); return f; }

    /** Number of columns of the model matrix that are linearly independent (columns taken in order, as lm / metafor do; tolerance 1e-7 on the unit-scaled columns). */
    public static int rank(double[][] X) {
        int n = X.length, p = X[0].length; List<double[]> basis = new ArrayList<>(); int r = 0;
        for (int a = 0; a < p; a++) {
            double[] v = new double[n]; double nrm = 0; for (int i = 0; i < n; i++) { v[i] = X[i][a]; nrm += v[i] * v[i]; } nrm = Math.sqrt(nrm); if (nrm == 0) continue;
            for (int i = 0; i < n; i++) v[i] /= nrm;
            for (int pass = 0; pass < 2; pass++) for (double[] q : basis) { double dot = 0; for (int i = 0; i < n; i++) dot += v[i] * q[i]; for (int i = 0; i < n; i++) v[i] -= dot * q[i]; }
            double res = 0; for (int i = 0; i < n; i++) res += v[i] * v[i]; res = Math.sqrt(res);
            if (res > 1e-7) { for (int i = 0; i < n; i++) v[i] /= res; basis.add(v); r++; }
        }
        return r;
    }

    /**
     * @param D design of the meta-regression (rows with complete data; see {@link Reg#design})
     * @param maxSubsets number of subsets requested (as for the ordinary GOSH analysis)
     */
    public static GoshReg run(MetaResult r, Reg.Design D, boolean intercept, int maxSubsets, long seed) {
        MetaSpec s = r.spec; final int k = D.k, p = D.p;
        if (k < p + 2) throw new IllegalArgumentException("GOSH for the meta-regression needs at least " + (p + 2) + " studies (the model has " + p + " coefficients; " + k + " studies with complete data).");
        if (k > 400) throw new IllegalArgumentException("The GOSH analysis is limited to 400 studies.");
        if (rank(D.X) < p) throw new IllegalArgumentException("The meta-regression model cannot be estimated with all studies (the moderators are collinear or a factor level is empty).");
        GoshReg g = new GoshReg(); g.k = k; g.p = p; g.seed = seed; g.method = s.methodTau; g.i2Def = s.methodI2; g.coefNames = D.names; g.intercept = intercept;
        final double[][] X = D.X; final double[] y = D.y, v = D.v; g.labels = new String[k]; for (int j = 0; j < k; j++) g.labels[j] = r.studlab[D.rows[j]];
        final int W = (k + 63) / 64; g.nTotal = totalSubsets(k, p); int want = Math.max(Gosh.MIN_SUBSETS, Math.min(Gosh.MAX_SUBSETS, maxSubsets));
        List<long[]> masks = new ArrayList<>();
        if (g.nTotal <= want) {
            g.exhaustive = true;
            for (int m = p; m <= k; m++) { int[] idx = new int[m]; for (int i = 0; i < m; i++) idx[i] = i;
                while (true) { long[] mk = new long[W]; for (int i : idx) mk[i >> 6] |= 1L << (i & 63); masks.add(mk);
                    int i = m - 1; while (i >= 0 && idx[i] == k - m + i) i--; if (i < 0) break; idx[i]++; for (int j = i + 1; j < m; j++) idx[j] = idx[j - 1] + 1; } }
        } else {
            SplittableRandom rng = new SplittableRandom(seed); double[] lf = logFact(k); double[] cum = new double[k - p + 1]; double tot = 0;
            for (int m = p; m <= k; m++) { tot += Math.exp(lf[k] - lf[m] - lf[k - m]); cum[m - p] = tot; }
            int[] perm = new int[k]; for (int i = 0; i < k; i++) perm[i] = i;
            while (masks.size() < want) {
                double u = rng.nextDouble() * tot; int m = p; while (m < k && cum[m - p] < u) m++;
                for (int i = 0; i < m; i++) { int j = i + rng.nextInt(k - i); int t = perm[i]; perm[i] = perm[j]; perm[j] = t; }   // partial Fisher-Yates: perm[0..m) is a uniform m-subset
                long[] mk = new long[W]; for (int i = 0; i < m; i++) mk[perm[i] >> 6] |= 1L << (perm[i] & 63); masks.add(mk);
            }
        }
        final int n = masks.size(); g.nSub = n; g.mask = masks.toArray(new long[0][]);
        g.status = new byte[n]; g.size = new int[n]; g.coef = new double[p][n]; g.i2 = new double[n]; g.tau2 = new double[n]; g.qe = new double[n];
        final boolean qDef = !"tau2".equals(s.methodI2); final String method = s.methodTau; final long[][] M = g.mask; final GoshReg G = g;
        Par.forRange(n, b -> {
            long[] m = M[b]; int c = 0; for (long x : m) c += Long.bitCount(x); G.size[b] = c;
            for (int a = 0; a < p; a++) G.coef[a][b] = Double.NaN; G.i2[b] = G.tau2[b] = G.qe[b] = Double.NaN;
            double[][] Xs = new double[c][]; double[] yy = new double[c], vv = new double[c]; int t = 0;
            for (int j = 0; j < k; j++) if ((m[j >> 6] >>> (j & 63) & 1L) != 0) { Xs[t] = X[j]; yy[t] = y[j]; vv[t] = v[j]; t++; }
            if (rank(Xs) < p) { G.status[b] = RANK_DEFICIENT; return; }
            if (c <= p) { G.status[b] = SATURATED; return; }
            try {
                Rma f = Rma.fit(yy, vv, Xs, method);
                double ii = qDef ? (f.QE > 0 ? Math.max(0, (f.QE - (c - p)) / f.QE) : 0) : f.tau2 / (f.tau2 + Rma.typicalV(c, p, vv, Xs));
                boolean good = !Double.isNaN(ii) && !Double.isNaN(f.tau2); for (int a = 0; a < p; a++) good &= Double.isFinite(f.beta[a]);
                if (!good) { G.status[b] = FAILED; return; }
                for (int a = 0; a < p; a++) G.coef[a][b] = f.beta[a]; G.i2[b] = 100 * ii; G.tau2[b] = f.tau2; G.qe[b] = f.QE; G.status[b] = OK;
            } catch (RuntimeException e) { G.status[b] = FAILED; }
        });
        for (int b = 0; b < n; b++) switch (g.status[b]) { case OK: g.nValid++; break; case SATURATED: g.nSaturated++; break; case RANK_DEFICIENT: g.nRankDef++; break; default: g.nFailed++; }
        if (g.nValid == 0) throw new IllegalArgumentException("No subset gave an estimable model (" + g.nRankDef + " not estimable, " + g.nSaturated + " saturated, " + g.nFailed + " failed fits).");
        // the model with all studies
        Rma f = Rma.fit(y, v, X, method); g.fullCoef = f.beta.clone(); g.fullTau2 = f.tau2; g.fullQE = f.QE;
        g.fullI2 = 100 * (qDef ? (f.QE > 0 ? Math.max(0, (f.QE - (k - p)) / f.QE) : 0) : f.tau2 / (f.tau2 + Rma.typicalV(k, p, v, X)));
        // in / out means
        g.i2In = new double[k]; g.i2Out = new double[k]; g.coefIn = new double[p][k]; g.coefOut = new double[p][k]; g.nIn = new int[k];
        g.tau2In = new double[k]; g.tau2Out = new double[k];
        Par.forRange(k, j -> {   // one study per task: the subsets are visited in the same order as before, so the sums are identical
            double sIi = 0, sIo = 0, sTi = 0, sTo = 0; int ni = 0, no = 0; double[] sCi = new double[p], sCo = new double[p];
            for (int b = 0; b < n; b++) { if (G.status[b] != OK) continue;
                if ((M[b][j >> 6] >>> (j & 63) & 1L) != 0) { sIi += G.i2[b]; sTi += G.tau2[b]; for (int a = 0; a < p; a++) sCi[a] += G.coef[a][b]; ni++; }
                else { sIo += G.i2[b]; sTo += G.tau2[b]; for (int a = 0; a < p; a++) sCo[a] += G.coef[a][b]; no++; } }
            G.nIn[j] = ni; G.i2In[j] = ni > 0 ? sIi / ni : Double.NaN; G.i2Out[j] = no > 0 ? sIo / no : Double.NaN; G.tau2In[j] = ni > 0 ? sTi / ni : Double.NaN; G.tau2Out[j] = no > 0 ? sTo / no : Double.NaN;
            for (int a = 0; a < p; a++) { G.coefIn[a][j] = ni > 0 ? sCi[a] / ni : Double.NaN; G.coefOut[a][j] = no > 0 ? sCo[a] / no : Double.NaN; }
        });
        return g;
    }

    /** Detects clusters among the valid subsets in the space of all coefficients and the residual I^2; the result is kept in {@link #clusters}. */
    public GoshCluster detectClusters() { return detectClusters(GoshCluster.MIN_SILHOUETTE); }
    /** @param minSil smallest silhouette width a cluster needs in the effects (the estimate / the coefficients) to be reported; heterogeneity route with its default thresholds, see {@link GoshCluster} */
    public GoshCluster detectClusters(double minSil) { return detectClusters(minSil, GoshCluster.HET_MIN_SILHOUETTE, GoshCluster.HET_MIN_SHARE); }
    /**
     * @param minSil smallest silhouette width a cluster needs in the effects (the estimate / the coefficients) to be reported
     * @param hetMinSil smallest silhouette width in ln(Q_E/df) for a cluster that is accepted through the heterogeneity instead
     * @param hetMinShare smallest share of the valid subsets such a cluster must contain; see {@link GoshCluster}
     */
    public GoshCluster detectClusters(double minSil, double hetMinSil, double hetMinShare) {
        double[][] X = new double[nSub][p + 1]; String[] nm = new String[p + 1];
        for (int a = 0; a < p; a++) nm[a] = coefNames[a]; nm[p] = "I^2";
        for (int b = 0; b < nSub; b++) { for (int a = 0; a < p; a++) X[b][a] = coef[a][b]; X[b][p] = i2[b]; }
        double[] hf = new double[nSub]; for (int b = 0; b < nSub; b++) hf[b] = valid(b) ? GoshCluster.logQ(qe[b], size[b] - p) : Double.NaN;
        clusters = GoshCluster.detect(X, nm, mask, k, size, seed, p, minSil, GoshCluster.MIN_SHARE, hf, hetMinSil, hetMinShare); return clusters;
    }
}
