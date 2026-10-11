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
 * <p><b>Separation criterion.</b> A mixture always fits a skewed or bounded cloud better with several components, and the GOSH clouds are bounded:
 * subsets in which Q_E does not exceed its degrees of freedom have I^2 = 0 exactly, a spike that is present even when all studies are
 * homogeneous. The components proposed by ICL are therefore accepted only when each of them is separated from the others <i>either</i> in the
 * effects (pooled estimate, or all regression coefficients) <i>or</i> in the heterogeneity measured by ln(QE / df), which is continuous, unlike I^2
 * (the I^2 column itself plays no part in this check). Two routes are open to a cluster: its silhouette width in the effects must reach
 * {@link #MIN_SILHOUETTE}, or its silhouette width in ln(QE / df) must reach the stricter {@link #HET_MIN_SILHOUETTE}; every cluster must also hold at least
 * {@link #MIN_SHARE} of the valid subsets (an average over all subsets is not enough: a few
 * far-away tail subsets make the bulk look well separated). The heterogeneity route is stricter because taking the better of two spaces gives every cluster two
 * chances to pass: with the same threshold (0.60) it let through 4-21% of homogeneous or heterogeneous-without-outliers data sets; at 0.70 that fell to 0-8%
 * while two outlier studies (+2) were still found in 88-96% (see the CHANGELOG for the simulation, including the weaker signals that are missed). Otherwise the worst cluster is merged with the one whose centre is closest and the check is
 * repeated, until it is met or one cluster is left ("no meaningful clusters"). A group of subsets at I^2 = 0 is thus never a cluster on its own.
 * The thresholds are heuristics (Kaufman and Rousseeuw: 0.51-0.70 reasonable, above 0.70 strong). They were chosen on simulated data sets with and
 * without structure (see the self-test and the CHANGELOG): homogeneous data, heterogeneity without outliers and skewed effects should give one cluster;
 * one or two clear outlier studies and a small subgroup of studies should give several. Limits: a pair of balanced subgroups of studies gives a continuous
 * ridge of subsets, not separate clouds, and is not resolved; a cluster defined by m studies holds about 2^-m of the subsets, so groups of more than about
 * four studies cannot be isolated. The thresholds can be changed by the caller.</p>
 */
public final class GoshCluster {
    public static final int MAX_G = 6, FIT_MAX = 5000, MIN_POINTS = 50, SIL_MAX = 2000;
    /** Smallest silhouette width (of each cluster, in the effects or in the heterogeneity) for which the clusters are reported; see the class comment. */
    public static final double MIN_SILHOUETTE = 0.60;
    /** Smallest share of the valid subsets a cluster must contain: a cluster defined by the inclusion or exclusion of m studies holds about 2^-m of the subsets, so 5% allows up to 4 studies; smaller groups are tails of the cloud. */
    public static final double MIN_SHARE = 0.05;
    /** Heterogeneity route of the separation check (ln(Q_E/df)): silhouette width and share of the subsets a cluster needs to be accepted through the heterogeneity alone. Stricter than the effects route, see the class comment. */
    public static final double HET_MIN_SILHOUETTE = 0.70, HET_MIN_SHARE = 0.05;
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
    /** Separation check: components proposed by ICL, average silhouette width of that partition and of the reported one (per cluster in silCluster), threshold used, share of the subsets at I^2 = 0. */
    public int iclG; public double silIcl = Double.NaN, sil = Double.NaN, minSil = MIN_SILHOUETTE, minShare = MIN_SHARE, hetMinSil = HET_MIN_SILHOUETTE, hetMinShare = HET_MIN_SHARE, spike = Double.NaN; public double[] silCluster; public int nMerged;

    public boolean found() { return G >= 2; }

    /**
     * @param X      n x d matrix of the points (original units); rows with NaN are ignored
     * @param masks  study inclusion mask of every subset (as in {@link Gosh#mask}); sizes: number of studies per subset
     */
    public static GoshCluster detect(double[][] X, String[] varNames, long[][] masks, int k, int[] sizes, long seed) {
        int het = -1; for (int a = 0; a < varNames.length; a++) if (varNames[a].startsWith("I^2")) het = a;   // the heterogeneity column is named I^2 (%)
        return detect(X, varNames, masks, k, sizes, seed, het, MIN_SILHOUETTE, MIN_SHARE, null);
    }

    /**
     * @param hetCol index of the heterogeneity column (the I^2 spike at 0 must not count as structure; left out of the separation check), or -1
     * @param minSil smallest silhouette width (of each cluster) for which clusters are reported
     * @param minShare smallest share of the valid subsets a cluster must contain
     * @param hetFree optional per-subset heterogeneity measure without a spike at a boundary (ln(QE / df), see {@link #logQ}); used, with the effects, in the separation check
     */
    public static GoshCluster detect(double[][] X, String[] varNames, long[][] masks, int k, int[] sizes, long seed, int hetCol, double minSil, double minShare, double[] hetFree) {
        return detect(X, varNames, masks, k, sizes, seed, hetCol, minSil, minShare, hetFree, HET_MIN_SILHOUETTE, HET_MIN_SHARE);
    }

    /**
     * As above with the thresholds of the heterogeneity route of the separation check.
     * @param hetMinSil smallest silhouette width in ln(Q_E/df) for a cluster that is accepted through the heterogeneity (it needs the effects route otherwise)
     * @param hetMinShare smallest share of the valid subsets such a cluster must contain
     */
    public static GoshCluster detect(double[][] X, String[] varNames, long[][] masks, int k, int[] sizes, long seed, int hetCol, double minSil, double minShare, double[] hetFree, double hetMinSil, double hetMinShare) {
        final int n = X.length, d = varNames.length; GoshCluster r = new GoshCluster(); r.d = d; r.varNames = varNames; r.label = new int[n]; Arrays.fill(r.label, -1); r.minSil = minSil; r.minShare = minShare; r.hetMinSil = hetMinSil; r.hetMinShare = hetMinShare;
        List<Integer> ok = new ArrayList<>();
        for (int b = 0; b < n; b++) { boolean good = true; for (int a = 0; a < d; a++) if (!Double.isFinite(X[b][a])) good = false; if (good) ok.add(b); }
        r.nUsed = ok.size(); r.icl = new double[MAX_G]; r.bic = new double[MAX_G]; Arrays.fill(r.icl, Double.NaN); Arrays.fill(r.bic, Double.NaN);
        if (hetCol >= 0 && r.nUsed > 0) { int z = 0; for (int b : ok) if (X[b][hetCol] <= 1e-9) z++; r.spike = (double) z / r.nUsed; }
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
        return finish(r, X, masks, k, sizes, seed, hetCol, minSil, minShare, hetFree, hetMinSil, hetMinShare, ok, act, Z, lab, cnt, best.G);
    }

    /** Gating phase (separation check, ordering, summaries) on the partition proposed by ICL; r is modified and returned. */
    private static GoshCluster finish(GoshCluster r, double[][] X, long[][] masks, int k, int[] sizes, long seed, int hetCol, double minSil, double minShare, double[] hetFree, double hetMinSil, double hetMinShare,
                                      List<Integer> ok, List<Integer> act, double[][] Z, int[] lab, int[] cnt, int bestG) {
        final int m = act.size(); r.minSil = minSil; r.minShare = minShare; r.hetMinSil = hetMinSil; r.hetMinShare = hetMinShare; r.iclG = bestG; int finalG = bestG;
      // separation criterion: the clusters must be separated in the space of the effects (the heterogeneity column is left out because of its spike at 0)
        if (bestG >= 2) {
            List<Integer> eff = new ArrayList<>(); for (int t = 0; t < m; t++) if (act.get(t) != hetCol) eff.add(t);
            double hmu = 0, hsd = 0; boolean useH = false;
            if (hetFree != null) { int c0 = 0; for (int b : ok) if (Double.isFinite(hetFree[b])) { hmu += hetFree[b]; c0++; } if (c0 > 1) { hmu /= c0; double q2 = 0; for (int b : ok) if (Double.isFinite(hetFree[b])) q2 += (hetFree[b] - hmu) * (hetFree[b] - hmu); hsd = Math.sqrt(q2 / (c0 - 1)); useH = hsd > 1e-9; } }
            SplittableRandom r2 = new SplittableRandom(seed * 0x2545F4914F6CDD1DL + 99); int S = Math.min(r.nUsed, SIL_MAX); int[] sp = new int[r.nUsed]; for (int i = 0; i < sp.length; i++) sp[i] = i;
            for (int i = 0; i < S; i++) { int j = i + r2.nextInt(sp.length - i); int t = sp[i]; sp[i] = sp[j]; sp[j] = t; }
            int[] smp = Arrays.copyOf(sp, S); int cur = bestG, G0 = bestG; r.nMerged = 0; double sv; double[] per; int D2 = eff.size() + (useH ? 1 : 0);
            double[][] P = new double[S][D2]; final int nE = eff.size(); double[][] PE = new double[S][nE], PH = new double[S][useH ? 1 : 0]; for (int i = 0; i < S; i++) { for (int e = 0; e < eff.size(); e++) P[i][e] = Z[smp[i]][eff.get(e)]; if (useH) { double hv = hetFree[ok.get(smp[i])]; P[i][D2 - 1] = Double.isFinite(hv) ? (hv - hmu) / hsd : 0; } }
            for (int i = 0; i < S; i++) { for (int e = 0; e < nE; e++) PE[i][e] = P[i][e]; if (useH) PH[i][0] = P[i][D2 - 1]; }
            while (true) {
                int[] sl = new int[S]; for (int i = 0; i < S; i++) sl[i] = lab[smp[i]];
                double[][] sres = silhouette(P, sl, G0); sv = sres[0][0]; per = sres[1];
                {   // a cluster counts when it is separated from the others in the effects or in the heterogeneity: its best silhouette width of the two
                    double[][] sE = nE > 0 ? silhouette(PE, sl, G0) : null, sH = useH ? silhouette(PH, sl, G0) : null; double[] pc = new double[G0]; int[] ns = new int[G0]; for (int v : sl) ns[v]++; double tot = 0;
                    int[] fl = new int[G0]; for (int v : lab) fl[v]++;   // share of the cluster among all valid subsets
                    for (int g = 0; g < G0; g++) { double bst = Double.NaN; if (sE != null && !(sE[1][g] <= bst)) bst = sE[1][g];
                        // heterogeneity route: stricter threshold (put on the scale of the effects threshold) and its own minimum share
                        double hv = sH != null && fl[g] >= hetMinShare * r.nUsed ? sH[1][g] - (hetMinSil - minSil) : Double.NaN; if (!Double.isNaN(hv) && !(hv <= bst)) bst = hv; pc[g] = bst; tot += (Double.isNaN(bst) ? 0 : bst) * ns[g]; }
                    per = pc; sv = tot / S; }
                if (r.nMerged == 0) r.silIcl = sv;
                int[] full = new int[G0]; for (int v : lab) full[v]++;
                // every cluster must be separated from the others (own average silhouette width) and hold a minimum share of the subsets;
                // an average over all subsets alone is not enough: a few far-away tail subsets raise the width of the bulk
                boolean ok2 = true; for (int g = 0; g < G0; g++) if (full[g] < minShare * r.nUsed || !(per[g] >= minSil)) ok2 = false;
                if (ok2) break;
                // merge the worst cluster (too small, otherwise the lowest silhouette width) with the one whose centre (effects, standardised) is closest
                int worst = -1; double wk = Double.POSITIVE_INFINITY; for (int g = 0; g < G0; g++) { double key = full[g] < minShare * r.nUsed ? -2 + (double) full[g] / r.nUsed : Double.isNaN(per[g]) ? -1 : per[g]; if (key < wk) { wk = key; worst = g; } }
                double[][] cen = new double[G0][D2]; int[] cc = new int[G0]; for (int i = 0; i < S; i++) { int g = sl[i]; cc[g]++; for (int e = 0; e < D2; e++) cen[g][e] += P[i][e]; }
                int to = -1; double bd = Double.POSITIVE_INFINITY;
                for (int h = 0; h < G0; h++) { if (h == worst || cc[h] == 0 || cc[worst] == 0) continue; double q = 0; for (int e = 0; e < D2; e++) { double dd = cen[worst][e] / cc[worst] - cen[h][e] / cc[h]; q += dd * dd; } if (q < bd) { bd = q; to = h; } }
                if (to < 0) { cur = 1; break; }
                for (int i = 0; i < lab.length; i++) if (lab[i] == worst) lab[i] = to;
                int[] map = new int[G0]; Arrays.fill(map, -1); int c2 = 0; for (int i = 0; i < lab.length; i++) if (map[lab[i]] < 0) map[lab[i]] = c2++;
                for (int i = 0; i < lab.length; i++) lab[i] = map[lab[i]];
                r.nMerged++; cur--; G0 = c2; if (cur < 2) break;
            }
            if (cur < 2) { Arrays.fill(lab, 0); finalG = 1; per = null; sv = Double.NaN; } else finalG = cur;
            cnt = new int[finalG]; for (int i = 0; i < lab.length; i++) cnt[lab[i]]++;
            r.sil = sv; r.silCluster = per;
        }
        r.G = finalG;
        // order by decreasing size
        Integer[] ord = new Integer[r.G]; for (int g = 0; g < r.G; g++) ord[g] = g; final int[] cc = cnt; Arrays.sort(ord, (x, y) -> cc[y] - cc[x]); int[] inv = new int[r.G]; for (int g = 0; g < r.G; g++) inv[ord[g]] = g;
        for (int i = 0; i < r.nUsed; i++) r.label[ok.get(i)] = inv[lab[i]];
        if (r.silCluster != null) { double[] ps = new double[r.G]; for (int g = 0; g < r.G; g++) ps[inv[g]] = r.silCluster[g]; r.silCluster = ps; }
        r.fillSummary(X, masks, k, sizes, ok);
        String spk = !Double.isNaN(r.spike) && r.spike >= 0.05 ? " " + Math.round(100 * r.spike) + "% of the subsets have I^2 = 0 exactly (a boundary value that occurs even when the studies are homogeneous); a group of subsets at I^2 = 0 is not counted as a cluster on its own." : "";
        if (r.iclG >= 2 && r.G == 1) r.note = "ICL proposed " + r.iclG + " components, but they are not separated in the effects or in the heterogeneity (average silhouette width " + Fmt.num(r.silIcl, 2) + " for the proposed partition; every cluster needs a silhouette width of at least " + Fmt.num(minSil, 2) + " in the effects (or " + Fmt.num(hetMinSil, 2) + " in the heterogeneity) and at least " + Math.round(100 * minShare) + "% of the subsets, and merging the worst clusters did not help). No meaningful clusters: the subsets form one cloud." + spk;
        else if (r.iclG >= 2 && r.nMerged > 0) r.note = "ICL proposed " + r.iclG + " components; " + r.nMerged + " cluster" + (r.nMerged > 1 ? "s were" : " was") + " merged with the closest one because it was too small or not separated enough in the effects or in the heterogeneity (average silhouette width " + Fmt.num(r.silIcl, 2) + " before merging, " + Fmt.num(r.sil, 2) + " after; every cluster needs at least " + Fmt.num(minSil, 2) + " in the effects (or " + Fmt.num(hetMinSil, 2) + " in the heterogeneity) and " + Math.round(100 * minShare) + "% of the subsets)." + spk;
        else if (r.G == 1) r.note = "No clear clusters: the subsets form one cloud (ICL selects a single component)." + spk;
        else r.note = (r.G >= MAX_G ? "Note: the maximum number of clusters (" + MAX_G + ") was reached; the cloud is skewed or heavy-tailed, so the clusters may be finer than the real structure. Read them together with the studies that separate them. " : "") + "Separation of the clusters in the space of the effects: average silhouette width " + Fmt.num(r.sil, 2) + " (every cluster needs at least " + Fmt.num(minSil, 2) + " in the effects (or " + Fmt.num(hetMinSil, 2) + " in the heterogeneity) and " + Math.round(100 * minShare) + "% of the subsets)." + spk;
        return r;
    }

    /** Spike-free heterogeneity coordinate for the separation check: ln(QE / df) (continuous, unlike I^2, which is exactly 0 for many subsets). NaN when it cannot be computed. */
    public static double logQ(double qe, int df) { return qe >= 0 && df > 0 ? Math.log(Math.max(qe, 1e-12) / df) : Double.NaN; }

    /**
     * Silhouette widths (Rousseeuw, 1987) of the points P (rows; Euclidean distance) with cluster labels lab: {{average}, {average per cluster}}.
     * Clusters with a single point get 0. NaN when there is no coordinate to measure the separation with.
     */
    static double[][] silhouette(double[][] P, int[] lab, int G) {
        int S = P.length, D = S > 0 ? P[0].length : 0; double[] sumPer = new double[G]; int[] cnt = new int[G]; for (int i = 0; i < S; i++) cnt[lab[i]]++;
        if (D == 0) { double[] nan = new double[G]; Arrays.fill(nan, Double.NaN); return new double[][]{{Double.NaN}, nan}; }
        double tot = 0;
        for (int i = 0; i < S; i++) {
            double[] sum = new double[G]; for (int j = 0; j < S; j++) { if (j == i) continue; double q = 0; for (int e = 0; e < D; e++) { double dd = P[i][e] - P[j][e]; q += dd * dd; } sum[lab[j]] += Math.sqrt(q); }
            int c = lab[i]; double si = 0;
            if (cnt[c] > 1) { double a = sum[c] / (cnt[c] - 1), b = Double.POSITIVE_INFINITY; for (int g = 0; g < G; g++) if (g != c && cnt[g] > 0) b = Math.min(b, sum[g] / cnt[g]); double mx = Math.max(a, b); si = Double.isInfinite(b) || mx <= 0 ? 0 : (b - a) / mx; }
            tot += si; sumPer[c] += si;
        }
        double[] per = new double[G]; for (int g = 0; g < G; g++) per[g] = cnt[g] > 0 ? sumPer[g] / cnt[g] : Double.NaN;
        return new double[][]{{tot / S}, per};
    }

    private void fillSummary(double[][] X, long[][] masks, int k, int[] sizes, List<Integer> ok) {
        if (G < 1) return; if (G == 1) for (int b : ok) label[b] = 0;   // single cluster
        size = new int[G]; mean = new double[G][d]; rateIn = new double[G][k]; rateOut = new double[G][k]; meanSize = new double[G];
        for (int b : ok) { int g = label[b]; size[g]++; for (int a = 0; a < d; a++) mean[g][a] += X[b][a]; if (sizes != null) meanSize[g] += sizes[b]; }
        share = new double[G]; for (int g = 0; g < G; g++) { share[g] = 100.0 * size[g] / Math.max(1, nUsed); for (int a = 0; a < d; a++) mean[g][a] /= Math.max(1, size[g]); meanSize[g] /= Math.max(1, size[g]); }
        // inclusion rates: one study per task (counts are integers, so the result does not depend on the order)
        final int[] okIdx = new int[ok.size()]; for (int i = 0; i < okIdx.length; i++) okIdx[i] = ok.get(i); final int GG = G;
        Par.forRange(k, j -> {
            long[] cntIn = new long[GG]; for (int b : okIdx) if ((masks[b][j >> 6] >>> (j & 63) & 1L) != 0) cntIn[label[b]]++;
            for (int g = 0; g < GG; g++) { double tin = cntIn[g], tout = 0; int nout = 0; for (int h = 0; h < GG; h++) if (h != g) { tout += cntIn[h]; nout += size[h]; }
                rateIn[g][j] = size[g] > 0 ? tin / size[g] : Double.NaN; rateOut[g][j] = nout > 0 ? tout / nout : Double.NaN; }
        });
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
