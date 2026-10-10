package org.metajava.core;

import java.util.*;

/**
 * Cluster detection in the output of a GOSH analysis (ordinary or meta-regression).
 * Every valid subset is a point (pooled estimate or regression coefficients, heterogeneity); the points are standardised and a
 * Gaussian mixture with full covariance matrices is fitted by EM (1 to {@link #MAX_G} components, k-means++ starts, seeded, so the
 * result is reproducible). The number of clusters is chosen by the integrated completed likelihood (ICL = BIC + entropy penalty, as in
 * mclust), which, unlike BIC, does not split one skewed, unimodal cloud into several overlapping components: one cluster means that no
 * clear subgroups of subsets were found. For large clouds the mixture is fitted to a random sample of at most {@link #FIT_MAX} subsets
 * and every subset is then assigned to the component with the highest posterior probability.
 * Per cluster and per study the share of the cluster's subsets that include the study is kept, so that studies that separate a cluster
 * from the rest (almost always in, or almost always out) are easy to identify (Olkin et al., 2012).
 */
public final class GoshCluster {
    public static final int MAX_G = 6, FIT_MAX = 5000, MIN_POINTS = 50;
    /** A study "separates" a cluster when its inclusion rate inside and outside the cluster differs by at least this much. */
    public static final double SEP = 0.40;

    public int G, nUsed, d; public String note; public String[] varNames;
    /** Cluster of every subset (0..G-1, ordered by decreasing size); -1: subset not valid / not clustered. */
    public int[] label; public int[] size; public double[] share;
    /** Mean of every variable (original units) per cluster: mean[g][v]. */
    public double[][] mean;
    public double[] icl, bic;   // for G = 1..MAX_G (NaN when not fitted)
    /** Inclusion rate of study j among the subsets of cluster g, and among all other clusters: rateIn[g][j], rateOut[g][j]. */
    public double[][] rateIn, rateOut;
    public double[] meanSize;   // mean number of studies of the subsets in each cluster

    public boolean found() { return G >= 2; }

    /**
     * @param X      n x d matrix of the points (original units); rows with NaN are ignored
     * @param masks  study inclusion mask of every subset (as in {@link Gosh#mask}); sizes: number of studies per subset
     */
    public static GoshCluster detect(double[][] X, String[] varNames, long[][] masks, int k, int[] sizes, long seed) {
        final int n = X.length, d = varNames.length; GoshCluster r = new GoshCluster(); r.d = d; r.varNames = varNames; r.label = new int[n]; Arrays.fill(r.label, -1);
        List<Integer> ok = new ArrayList<>();
        for (int b = 0; b < n; b++) { boolean good = true; for (int a = 0; a < d; a++) if (!Double.isFinite(X[b][a])) good = false; if (good) ok.add(b); }
        r.nUsed = ok.size(); r.icl = new double[MAX_G]; r.bic = new double[MAX_G]; Arrays.fill(r.icl, Double.NaN); Arrays.fill(r.bic, Double.NaN);
        if (r.nUsed < MIN_POINTS) { r.G = 0; r.note = "Too few valid subsets (" + r.nUsed + ", at least " + MIN_POINTS + " are needed) for cluster detection."; return r; }
        // standardise (variables with no spread are dropped from the clustering, but kept in the summaries)
        double[] mu = new double[d], sd = new double[d];
        for (int a = 0; a < d; a++) { double s = 0; for (int b : ok) s += X[b][a]; mu[a] = s / r.nUsed; double q = 0; for (int b : ok) q += (X[b][a] - mu[a]) * (X[b][a] - mu[a]); sd[a] = Math.sqrt(q / Math.max(1, r.nUsed - 1)); }
        List<Integer> act = new ArrayList<>(); for (int a = 0; a < d; a++) if (sd[a] > 1e-10 * Math.max(1, Math.abs(mu[a]))) act.add(a);
        final int m = act.size();
        if (m == 0) { r.G = 1; r.fillSummary(X, masks, k, sizes, ok); r.note = "All subsets give the same result; there is nothing to cluster."; return r; }
        double[][] Z = new double[r.nUsed][m]; for (int i = 0; i < r.nUsed; i++) for (int a = 0; a < m; a++) { int v = act.get(a); Z[i][a] = (X[ok.get(i)][v] - mu[v]) / sd[v]; }
        // sample for fitting
        SplittableRandom rng = new SplittableRandom(seed * 0x9E3779B97F4A7C15L + 12345);
        double[][] F; if (r.nUsed <= FIT_MAX) F = Z; else { F = new double[FIT_MAX][]; int[] perm = new int[r.nUsed]; for (int i = 0; i < perm.length; i++) perm[i] = i;
            for (int i = 0; i < FIT_MAX; i++) { int j = i + rng.nextInt(perm.length - i); int t = perm[i]; perm[i] = perm[j]; perm[j] = t; F[i] = Z[perm[i]]; } }
        Mix best = null; double bestIcl = Double.POSITIVE_INFINITY;
        for (int g = 1; g <= MAX_G; g++) {
            if (F.length < 10 * g) break;
            Mix mx = null; for (int start = 0; start < 3; start++) { Mix t = Mix.fit(F, g, rng); if (t != null && (mx == null || t.ll > mx.ll)) mx = t; }
            if (mx == null) continue;
            int npar = g * m + g * m * (m + 1) / 2 + (g - 1); double nF = F.length;
            double bic = -2 * mx.ll + npar * Math.log(nF);
            double ent = 0; for (double[] row : mx.resp) for (double p : row) if (p > 1e-300) ent -= p * Math.log(p);
            double icl = bic + 2 * ent; r.bic[g - 1] = bic; r.icl[g - 1] = icl;
            if (icl < bestIcl) { bestIcl = icl; best = mx; }
        }
        if (best == null) { r.G = 1; r.fillSummary(X, masks, k, sizes, ok); r.note = "The mixture model could not be fitted; no clusters reported."; return r; }
        // assign every valid subset
        int[] lab = new int[r.nUsed]; int[] cnt = new int[best.G];
        for (int i = 0; i < r.nUsed; i++) { lab[i] = best.assign(Z[i]); cnt[lab[i]]++; }
        // clusters smaller than 1% of the subsets (or 10 subsets) are merged into the nearest larger cluster
        int minSz = (int) Math.max(10, Math.ceil(0.01 * r.nUsed)); boolean changed = true;
        while (changed && best.G > 1) { changed = false; int small = -1; for (int g = 0; g < best.G; g++) if (cnt[g] < minSz && (small < 0 || cnt[g] < cnt[small])) small = g;
            if (small >= 0) { int to = best.nearest(small, cnt, minSz); if (to == small) break; for (int i = 0; i < r.nUsed; i++) if (lab[i] == small) lab[i] = to; cnt[to] += cnt[small]; cnt[small] = 0; changed = true;
                // renumber
                int[] map = new int[best.G]; int c = 0; for (int g = 0; g < best.G; g++) map[g] = cnt[g] > 0 ? c++ : -1; for (int i = 0; i < r.nUsed; i++) lab[i] = map[lab[i]];
                int[] nc = new int[c]; for (int g = 0; g < best.G; g++) if (map[g] >= 0) nc[map[g]] = cnt[g]; cnt = nc; best = best.drop(map, c); } }
        r.G = best.G;
        // order by decreasing size
        Integer[] ord = new Integer[r.G]; for (int g = 0; g < r.G; g++) ord[g] = g; final int[] cc = cnt; Arrays.sort(ord, (x, y) -> cc[y] - cc[x]); int[] inv = new int[r.G]; for (int g = 0; g < r.G; g++) inv[ord[g]] = g;
        for (int i = 0; i < r.nUsed; i++) r.label[ok.get(i)] = inv[lab[i]];
        r.fillSummary(X, masks, k, sizes, ok);
        r.note = r.G == 1 ? "No clear clusters: the subsets form one cloud (ICL selects a single component)." : r.G >= MAX_G ? "Note: the maximum number of clusters (" + MAX_G + ") was reached; the cloud is skewed or heavy-tailed, so the clusters may be finer than the real structure. Read them together with the studies that separate them." : null;
        return r;
    }

    private void fillSummary(double[][] X, long[][] masks, int k, int[] sizes, List<Integer> ok) {
        if (G < 1) return; if (G == 1) for (int b : ok) label[b] = 0;   // single cluster
        size = new int[G]; mean = new double[G][d]; rateIn = new double[G][k]; rateOut = new double[G][k]; meanSize = new double[G]; double[][] cntIn = new double[G][k];
        for (int b : ok) { int g = label[b]; size[g]++; for (int a = 0; a < d; a++) mean[g][a] += X[b][a]; if (sizes != null) meanSize[g] += sizes[b];
            long[] mk = masks[b]; for (int j = 0; j < k; j++) if ((mk[j >> 6] >>> (j & 63) & 1L) != 0) cntIn[g][j]++; }
        share = new double[G]; for (int g = 0; g < G; g++) { share[g] = 100.0 * size[g] / Math.max(1, nUsed); for (int a = 0; a < d; a++) mean[g][a] /= Math.max(1, size[g]); meanSize[g] /= Math.max(1, size[g]); }
        for (int g = 0; g < G; g++) for (int j = 0; j < k; j++) { double tin = 0, tout = 0; int nout = 0; for (int h = 0; h < G; h++) { if (h == g) tin = cntIn[h][j]; else { tout += cntIn[h][j]; nout += size[h]; } }
            rateIn[g][j] = size[g] > 0 ? tin / size[g] : Double.NaN; rateOut[g][j] = nout > 0 ? tout / nout : Double.NaN; }
    }

    /** Studies that separate cluster g from the other subsets: |inclusion rate in - rate out| >= {@link #SEP}, in decreasing order of the difference. */
    public List<int[]> separating(int g) {
        List<int[]> out = new ArrayList<>(); if (G < 2 || rateIn == null) return out; int k = rateIn[g].length; List<Integer> js = new ArrayList<>();
        for (int j = 0; j < k; j++) if (Math.abs(rateIn[g][j] - rateOut[g][j]) >= SEP) js.add(j);
        js.sort((a, b) -> Double.compare(Math.abs(rateIn[g][b] - rateOut[g][b]), Math.abs(rateIn[g][a] - rateOut[g][a])));
        for (int j : js) out.add(new int[]{j}); return out;
    }

    // ------------------------------------------------------------------ Gaussian mixture (EM, full covariances)
    static final class Mix {
        int G, m; double[] pi; double[][] mu; double[][][] cov, prec; double[] logDet; double ll; double[][] resp;

        static Mix fit(double[][] F, int G, SplittableRandom rng) {
            int n = F.length, m = F[0].length; Mix x = new Mix(); x.G = G; x.m = m;
            // k-means++ start
            double[][] c = new double[G][]; c[0] = F[rng.nextInt(n)].clone(); double[] dist = new double[n];
            for (int g = 1; g < G; g++) { double tot = 0; for (int i = 0; i < n; i++) { double best = Double.MAX_VALUE; for (int h = 0; h < g; h++) best = Math.min(best, d2(F[i], c[h])); dist[i] = best; tot += best; }
                double u = rng.nextDouble() * tot, acc = 0; int pick = n - 1; for (int i = 0; i < n; i++) { acc += dist[i]; if (acc >= u) { pick = i; break; } } c[g] = F[pick].clone(); }
            int[] a = new int[n];
            for (int it = 0; it < 15; it++) { for (int i = 0; i < n; i++) { int bg = 0; double bd = Double.MAX_VALUE; for (int g = 0; g < G; g++) { double q = d2(F[i], c[g]); if (q < bd) { bd = q; bg = g; } } a[i] = bg; }
                double[][] s = new double[G][m]; int[] cn = new int[G]; for (int i = 0; i < n; i++) { cn[a[i]]++; for (int v = 0; v < m; v++) s[a[i]][v] += F[i][v]; }
                for (int g = 0; g < G; g++) if (cn[g] > 0) for (int v = 0; v < m; v++) c[g][v] = s[g][v] / cn[g]; }
            x.resp = new double[n][G]; for (int i = 0; i < n; i++) x.resp[i][a[i]] = 1;
            x.pi = new double[G]; x.mu = new double[G][m]; x.cov = new double[G][m][m]; x.prec = new double[G][][]; x.logDet = new double[G];
            double prev = Double.NEGATIVE_INFINITY;
            for (int it = 0; it < 200; it++) {
                if (!x.mstep(F)) return null;
                double ll = x.estep(F); if (Double.isNaN(ll)) return null;
                if (Math.abs(ll - prev) < 1e-7 * (1 + Math.abs(ll))) { prev = ll; break; } prev = ll;
            }
            x.ll = prev; return x;
        }

        boolean mstep(double[][] F) {
            int n = F.length; double ridge = 1e-4;
            for (int g = 0; g < G; g++) {
                double nk = 0; for (int i = 0; i < n; i++) nk += resp[i][g]; if (nk < 2) return false; pi[g] = nk / n;
                for (int v = 0; v < m; v++) { double s = 0; for (int i = 0; i < n; i++) s += resp[i][g] * F[i][v]; mu[g][v] = s / nk; }
                for (int v = 0; v < m; v++) for (int w = v; w < m; w++) { double s = 0; for (int i = 0; i < n; i++) s += resp[i][g] * (F[i][v] - mu[g][v]) * (F[i][w] - mu[g][w]); cov[g][v][w] = cov[g][w][v] = s / nk + (v == w ? ridge : 0); }
                prec[g] = Lin.inv(cov[g]); double det = Lin.det(cov[g]); if (prec[g] == null || !(det > 0)) return false; logDet[g] = Math.log(det);
            }
            return true;
        }

        /** E step: updates the responsibilities and returns the log-likelihood. */
        double estep(double[][] F) {
            int n = F.length; double ll = 0; double[] lp = new double[G];
            for (int i = 0; i < n; i++) { double mx = Double.NEGATIVE_INFINITY; for (int g = 0; g < G; g++) { lp[g] = logDens(g, F[i]); if (lp[g] > mx) mx = lp[g]; }
                double s = 0; for (int g = 0; g < G; g++) s += Math.exp(lp[g] - mx); double lse = mx + Math.log(s); ll += lse; for (int g = 0; g < G; g++) resp[i][g] = Math.exp(lp[g] - lse); }
            return ll;
        }

        double logDens(int g, double[] z) {
            double q = 0; for (int v = 0; v < m; v++) { double s = 0; for (int w = 0; w < m; w++) s += prec[g][v][w] * (z[w] - mu[g][w]); q += (z[v] - mu[g][v]) * s; }
            return Math.log(pi[g]) - 0.5 * (m * Math.log(2 * Math.PI) + logDet[g] + q);
        }

        int assign(double[] z) { int bg = 0; double b = Double.NEGATIVE_INFINITY; for (int g = 0; g < G; g++) { double q = logDens(g, z); if (q > b) { b = q; bg = g; } } return bg; }

        /** Component nearest to component `from` (Euclidean distance of the means) among those with at least minSz subsets. */
        int nearest(int from, int[] cnt, int minSz) { int best = -1; double bd = Double.MAX_VALUE; for (int g = 0; g < G; g++) { if (g == from || cnt[g] == 0) continue; double q = d2(mu[g], mu[from]); if (q < bd) { bd = q; best = g; } } return best < 0 ? from : best; }

        Mix drop(int[] map, int c) { Mix y = new Mix(); y.G = c; y.m = m; y.pi = new double[c]; y.mu = new double[c][]; y.cov = new double[c][][]; y.prec = new double[c][][]; y.logDet = new double[c];
            for (int g = 0; g < G; g++) if (map[g] >= 0) { int h = map[g]; y.pi[h] = pi[g]; y.mu[h] = mu[g]; y.cov[h] = cov[g]; y.prec[h] = prec[g]; y.logDet[h] = logDet[g]; } return y; }

        static double d2(double[] a, double[] b) { double s = 0; for (int i = 0; i < a.length; i++) s += (a[i] - b[i]) * (a[i] - b[i]); return s; }
    }
}
