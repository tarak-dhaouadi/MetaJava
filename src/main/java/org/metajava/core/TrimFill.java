package org.metajava.core;

import java.util.*;

/** Duval & Tweedie trim-and-fill, reproducing metafor::trimfill.rma.uni (L0, R0, Q0). */
public final class TrimFill {
    public double[] TE, seTE; public boolean[] filled; public boolean left; public int k0;
    public String[] studlab; public String type = "L"; public Pool pool; public MetaSpec spec; public double teSum;

    /** Number of imputed studies is {@link #k0}; {@link #seK0} and {@link #pK0} (R0 only, else NaN) follow metafor's trimfill.rma.uni. */
    public double seK0 = Double.NaN, pK0 = Double.NaN; public String model = "";

    /**
     * Trim-and-fill (Duval &amp; Tweedie 2000), a line-by-line port of metafor::trimfill.rma.uni (4.6-0).
     * <p>maCommon = true: the centre is re-estimated with the common-effect (FE) model after every trimming step;
     * maCommon = false: with the random-effects model of the analysis (tau^2 estimator = spec.methodTau, re-estimated on the
     * truncated data, exactly as metafor does with the method of the fitted rma.uni object).
     * <p>Side (leftArg == null): as metafor, the sign of the slope of a meta-regression of the effects on sqrt(v_i)
     * (same model and tau^2 estimator): negative slope = right side, otherwise left side.
     * Ranks use ties.method = "first"; k0 is rounded like R's round() (half to even).</p>
     */
    public static TrimFill run(MetaResult r, Boolean leftArg, boolean maCommon, String type, boolean commonOut, boolean randomOut) {
        MetaSpec s = r.spec;
        type = normType(type);
        List<Integer> sel = new ArrayList<>();
        for (int i = 0; i < r.TE.length; i++) if (!Double.isNaN(r.TE[i]) && !Double.isNaN(r.seTE[i]) && !Double.isInfinite(r.TE[i]) && r.seTE[i] > 0) sel.add(i);
        int k = sel.size(); if (k <= 2) return null;
        double[] te = new double[k], se = new double[k]; String[] lab = new String[k];
        for (int j = 0; j < k; j++) { te[j] = r.TE[sel.get(j)]; se[j] = r.seTE[sel.get(j)]; lab[j] = r.studlab[sel.get(j)]; }
        final String method = maCommon ? "FE" : s.methodTau;
        int[] selIdx = new int[k]; for (int j = 0; j < k; j++) selIdx[j] = sel.get(j);
        final double[] gwSel = maCommon ? null : Pool.genqWeightsFor(s, selIdx);   // GENQ / GENQM weights of the studies (metafor: trimfill passes x$weights to every refit)
        // ---- side: slope of yi on sqrt(vi) (metafor: rma.uni(yi, vi, mods = sqrt(vi))); beta[2] < 0 -> right, otherwise left
        boolean left;
        if (leftArg != null) left = leftArg;
        else {
            double[][] X = new double[k][2]; double[] v = new double[k];
            for (int i = 0; i < k; i++) { X[i][0] = 1; X[i][1] = se[i]; v[i] = se[i] * se[i]; }
            double slope; try { slope = Rma.fit(te, v, X, method, gwSel).beta[1]; } catch (RuntimeException e) { slope = Double.NaN; }
            left = !(slope < 0);
        }
        if (!left) for (int j = 0; j < k; j++) te[j] = -te[j];
        // ---- sort by increasing yi (stable, like R's sort(index.return = TRUE))
        Integer[] ord = new Integer[k]; for (int i = 0; i < k; i++) ord[i] = i;
        final double[] teF = te; Arrays.sort(ord, Comparator.comparingDouble(i -> teF[i]));
        double[] t1 = new double[k], s1 = new double[k]; String[] l1 = new String[k];
        double[] w1 = gwSel == null ? null : new double[k];
        for (int j = 0; j < k; j++) { t1[j] = te[ord[j]]; s1[j] = se[ord[j]]; l1[j] = lab[ord[j]]; if (w1 != null) w1[j] = gwSel[ord[j]]; }
        // ---- iterate (metafor: while (abs(k0 - k0.sav) > 0), maxiter = 100)
        int k0 = 0, k0sav = -1, iter = 0; double beta = 0, seK0 = Double.NaN;
        while (k0 != k0sav) {
            k0sav = k0; iter++;
            if (iter > 100) throw new IllegalStateException("Trim and fill algorithm did not converge.");
            int m = k - k0;
            if (m < 2) beta = t1[0];
            else { double[] y = Arrays.copyOf(t1, m), v = new double[m]; for (int i = 0; i < m; i++) v[i] = s1[i] * s1[i]; beta = Rma.fit(y, v, null, method, w1 == null ? null : Arrays.copyOf(w1, m)).beta[0]; }
            double[] yc = new double[k], ab = new double[k];
            for (int i = 0; i < k; i++) { yc[i] = t1[i] - beta; ab[i] = Math.abs(yc[i]); }
            double[] rk = rankFirst(ab); double[] rs = new double[k];
            for (int i = 0; i < k; i++) rs[i] = Math.signum(yc[i]) * rk[i];
            double raw; double kk = k;
            if (type.equals("R")) {
                double mx = Double.NEGATIVE_INFINITY; for (double x : rs) if (x < 0) mx = Math.max(mx, -x);
                raw = (kk - mx) - 1; seK0 = Math.sqrt(2 * Math.max(0, raw) + 2);
            } else {
                double Sr = 0; for (double x : rs) if (x > 0) Sr += x;
                if (type.equals("L")) raw = (4 * Sr - kk * (kk + 1)) / (2 * kk - 1);
                else raw = kk - 0.5 - Math.sqrt(2 * kk * kk - 4 * Sr + 0.25);
                double varSr = 1.0 / 24 * (kk * (kk + 1) * (2 * kk + 1) + 10 * Math.pow(raw, 3) + 27 * raw * raw + 17 * raw - 18 * kk * raw * raw - 18 * kk * raw + 6 * kk * kk * raw);
                seK0 = type.equals("L") ? 4 * Math.sqrt(varSr) / (2 * kk - 1) : 2 * Math.sqrt(varSr) / Math.sqrt((kk - 0.5) * (kk - 0.5) - raw * (2 * kk - raw - 1));
            }
            if (Double.isNaN(raw) || raw >= k - 1) k0 = k - 1;           // metafor stops with an error here; at most k - 1 studies can be trimmed
            else k0 = (int) Math.max(0, Math.rint(raw));                 // R's round(): half to even
            seK0 = Math.max(0, seK0);
        }
        int tot = k + k0; double[] T = new double[tot], S = new double[tot]; String[] L = new String[tot]; boolean[] f = new boolean[tot];
        for (int j = 0; j < k; j++) { T[ord[j]] = t1[j]; S[ord[j]] = s1[j]; L[ord[j]] = l1[j]; }
        // filled-in studies: mirror image of the k0 most extreme studies around the centre of the last (truncated) fit
        for (int j = 0; j < k0; j++) { int src = k - k0 + j; T[k + j] = 2 * beta - t1[src]; S[k + j] = s1[src]; L[k + j] = "Filled: " + l1[src]; f[k + j] = true; }
        if (!left) for (int j = 0; j < tot; j++) T[j] = -T[j];
        TrimFill tf = new TrimFill(); tf.TE = T; tf.seTE = S; tf.filled = f; tf.left = left; tf.type = type; tf.k0 = k0; tf.studlab = L; tf.teSum = left ? beta : -beta;
        tf.seK0 = seK0; tf.pK0 = type.equals("R") ? Math.pow(0.5, k0 + 1) : Double.NaN; tf.model = maCommon ? "common effect (FE)" : "random effects (" + method + ")";
        MetaSpec s2 = s.forAux(); s2.common = commonOut; s2.random = randomOut; tf.spec = s2;
        if (w1 != null) { double[] wt = new double[tot]; for (int j = 0; j < k; j++) wt[ord[j]] = w1[j]; for (int j = 0; j < k0; j++) wt[k + j] = w1[k - k0 + j]; s2.genqW = wt; s2.genqInverse = false; }   // filled studies carry the weight of the study they mirror
        int[] all = new int[tot]; for (int i = 0; i < tot; i++) all[i] = i;
        tf.pool = Pool.compute(s2, T, S, all, null);
        return tf;
    }

    /** R's rank(x, ties.method = "first"): ties are ranked in order of appearance. */
    public static double[] rankFirst(double[] x) {
        int n = x.length; Integer[] o = new Integer[n]; for (int i = 0; i < n; i++) o[i] = i;
        Arrays.sort(o, Comparator.comparingDouble(i -> x[i]));
        double[] r = new double[n]; for (int i = 0; i < n; i++) r[o[i]] = i + 1; return r;
    }

    /** Accepts "L", "R", "Q" as well as "L0", "R0", "Q0" (case-insensitive); anything else is an error. */
    static String normType(String t) {
        String u = t == null ? "L" : t.trim().toUpperCase(Locale.ROOT);
        if (u.endsWith("0")) u = u.substring(0, u.length() - 1);
        if (!u.equals("L") && !u.equals("R") && !u.equals("Q")) throw new IllegalArgumentException("Unknown trim-and-fill estimator: " + t);
        return u;
    }

    public static int estimateMissing(double[] te, double teSum, String type) {
        int n = te.length; double[] tc = new double[n], abs = new double[n];
        for (int i = 0; i < n; i++) { tc[i] = te[i] - teSum; abs[i] = Math.abs(tc[i]); }
        double[] rk = rankFirst(abs); double[] rs = new double[n];
        for (int i = 0; i < n; i++) rs[i] = rk[i] * Math.signum(tc[i]);
        double res0;
        if (type.equals("L")) { double S = 0; for (double x : rs) if (x > 0) S += x; res0 = (4 * S - n * (n + 1.0)) / (2 * n - 1); }
        else if (type.equals("Q")) { // quadratic estimator of Duval & Tweedie (2000): Q0 = n - 1/2 - sqrt(2n^2 - 4 T_n + 1/4)
            double S = 0; for (double x : rs) if (x > 0) S += x;
            double rad = 2.0 * n * n - 4 * S + 0.25;
            res0 = rad < 0 ? Double.POSITIVE_INFINITY : n - 0.5 - Math.sqrt(rad); }
        else { double mn = 0; for (double x : rs) mn = Math.min(mn, x); res0 = n - Math.abs(mn) - 1.5; }
        if (Double.isInfinite(res0)) return n;
        return (int) Math.max(0, Math.rint(res0));
    }

    static double[] rank(double[] x) {
        int n = x.length; Integer[] o = new Integer[n]; for (int i = 0; i < n; i++) o[i] = i;
        Arrays.sort(o, Comparator.comparingDouble(i -> x[i]));
        double[] r = new double[n]; int i = 0;
        while (i < n) { int j = i; while (j + 1 < n && x[o[j + 1]] == x[o[i]]) j++; double avg = (i + j) / 2.0 + 1; for (int q = i; q <= j; q++) r[o[q]] = avg; i = j + 1; }
        return r;
    }
}
