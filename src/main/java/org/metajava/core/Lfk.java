package org.metajava.core;

import java.util.*;

/**
 * Doi plot quantities and LFK index (Furuya-Kanamori, Barendregt & Doi, 2018), computed exactly as in metasens::lfkindex.
 * Studies are ordered by effect; each gets a mid-rank percentile weighted by its precision (N = 100 * max(var) / var), the
 * percentile is turned into a Z-score, and the index is the (scaled) difference between the two limbs of the Doi plot around
 * the study with the smallest absolute Z-score.
 */
public final class Lfk {
    public double lfk = Double.NaN; public String interpretation = "";
    public int k, apex;                                 // apex = position (in the ordered studies) of the smallest |Z|
    public double[] te, se, N, midRank, percentile, zscore, absZ; public String[] label;   // all ordered by effect (ascending)
    public String note = "";

    public static Lfk compute(double[] TE, double[] seTE, String[] lab) {
        List<Integer> ok = new ArrayList<>(); for (int i = 0; i < TE.length; i++) if (!Double.isNaN(TE[i]) && !Double.isNaN(seTE[i]) && seTE[i] > 0) ok.add(i);
        Lfk r = new Lfk(); int k = ok.size(); r.k = k; if (k < 2) { r.note = "LFK index needs at least two studies."; return r; }
        Integer[] idx = ok.toArray(new Integer[0]); Arrays.sort(idx, Comparator.comparingDouble(i -> TE[i]));   // stable, like R's order()
        r.te = new double[k]; r.se = new double[k]; r.label = new String[k]; double[] var = new double[k]; double vmax = 0;
        for (int j = 0; j < k; j++) { int i = idx[j]; r.te[j] = TE[i]; r.se[j] = seTE[i]; r.label[j] = lab == null ? "Study " + (i + 1) : lab[i]; var[j] = seTE[i] * seTE[i]; vmax = Math.max(vmax, var[j]); }
        r.N = new double[k]; r.midRank = new double[k]; r.percentile = new double[k]; r.zscore = new double[k]; r.absZ = new double[k]; double sumN = 0;
        for (int j = 0; j < k; j++) { r.N[j] = 100 * vmax / var[j]; sumN += r.N[j]; }
        r.midRank[0] = r.N[0] / 2; for (int j = 1; j < k; j++) r.midRank[j] = r.midRank[j - 1] + (r.N[j - 1] + r.N[j]) / 2;
        int apex = 0; double zmax = Double.NEGATIVE_INFINITY, zmin = Double.POSITIVE_INFINITY;
        for (int j = 0; j < k; j++) { r.percentile[j] = (r.midRank[j] - 0.5) / sumN; r.zscore[j] = Dist.qnorm(r.percentile[j]); r.absZ[j] = Math.abs(r.zscore[j]); zmax = Math.max(zmax, r.zscore[j]); zmin = Math.min(zmin, r.zscore[j]); if (r.absZ[j] < r.absZ[apex]) apex = j; }
        r.apex = apex; double tej = r.te[apex], dmax = Double.NEGATIVE_INFINITY, dmin = Double.POSITIVE_INFINITY;
        for (int j = 0; j < k; j++) { dmax = Math.max(dmax, r.te[j] - tej); dmin = Math.min(dmin, r.te[j] - tej); }
        double sum = 0; for (int j = 0; j < k; j++) sum += r.zscore[j] + (zmax - zmin) / (dmax - dmin) * (r.te[j] - tej);
        r.lfk = 5.0 / (2.0 * k) * sum;
        double a = Math.abs(r.lfk); r.interpretation = Double.isNaN(a) ? "unclear" : a <= 1 ? "no asymmetry" : a <= 2 ? "minor asymmetry" : "major asymmetry";
        if (k < 5) r.note = "Fewer than 5 studies: the Doi plot and LFK index should be interpreted with great caution.";
        return r;
    }
}
