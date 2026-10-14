package org.metajava.core;

import java.util.*;

/** Duval & Tweedie trim-and-fill (meta::trimfill). */
public final class TrimFill {
    public double[] TE, seTE; public boolean[] filled; public boolean left; public int k0;
    public String[] studlab; public String type = "L"; public Pool pool; public MetaSpec spec; public double teSum;

    public static TrimFill run(MetaResult r, Boolean leftArg, boolean maCommon, String type, boolean commonOut, boolean randomOut) {
        MetaSpec s = r.spec;
        type = normType(type);
        List<Integer> sel = new ArrayList<>();
        for (int i = 0; i < r.TE.length; i++) if (!Double.isNaN(r.TE[i]) && !Double.isNaN(r.seTE[i]) && r.seTE[i] > 0) sel.add(i);
        int k = sel.size(); if (k <= 2) return null;
        double[] te = new double[k], se = new double[k]; String[] lab = new String[k];
        for (int j = 0; j < k; j++) { te[j] = r.TE[sel.get(j)]; se[j] = r.seTE[sel.get(j)]; lab[j] = r.studlab[sel.get(j)]; }
        boolean left;
        if (leftArg == null) { Bias.Result b = Bias.run(r, "Egger", false); left = b.error == null && Math.signum(b.bias) == 1; } else left = leftArg;
        if (!left) for (int j = 0; j < k; j++) te[j] = -te[j];
        Integer[] ord = new Integer[k]; for (int i = 0; i < k; i++) ord[i] = i;
        final double[] teF = te; Arrays.sort(ord, Comparator.comparingDouble(i -> teF[i]));
        double[] t1 = new double[k], s1 = new double[k]; String[] l1 = new String[k];
        for (int j = 0; j < k; j++) { t1[j] = te[ord[j]]; s1[j] = se[ord[j]]; l1[j] = lab[ord[j]]; }
        MetaSpec sd = s.forAux(); sd.methodTauCi = ""; sd.prediction = false;
        if (maCommon) sd.methodTau = "DL";
        int k0 = 0, last = -1, iter = 0; double teSum = 0;
        while (last != k0 && k0 <= k - 1 && iter < 50) {
            iter++; last = k0; int m = k - k0; int[] idx = new int[m]; for (int i = 0; i < m; i++) idx[i] = i;
            Pool p = Pool.compute(sd, t1, s1, idx, null);
            teSum = maCommon ? p.teCommon : p.teRandom;
            k0 = estimateMissing(t1, teSum, type);
        }
        if (k0 > k - 1) k0 = k - 1;
        int tot = k + k0; double[] T = new double[tot], S = new double[tot]; String[] L = new String[tot]; boolean[] f = new boolean[tot];
        // restore original order
        for (int j = 0; j < k; j++) { T[ord[j]] = t1[j]; S[ord[j]] = s1[j]; L[ord[j]] = l1[j]; }
        for (int j = 0; j < k0; j++) { int src = k - k0 + j; T[k + j] = 2 * teSum - t1[src]; S[k + j] = s1[src]; L[k + j] = "Filled: " + l1[src]; f[k + j] = true; }
        if (!left) for (int j = 0; j < tot; j++) T[j] = -T[j];
        TrimFill tf = new TrimFill(); tf.TE = T; tf.seTE = S; tf.filled = f; tf.left = left; tf.type = type; tf.k0 = k0; tf.studlab = L; tf.teSum = left ? teSum : -teSum;
        MetaSpec s2 = s.forAux(); s2.common = commonOut; s2.random = randomOut; tf.spec = s2;
        int[] all = new int[tot]; for (int i = 0; i < tot; i++) all[i] = i;
        tf.pool = Pool.compute(s2, T, S, all, null);
        return tf;
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
        double[] rk = rank(abs); double[] rs = new double[n];
        for (int i = 0; i < n; i++) rs[i] = rk[i] * Math.signum(tc[i]);
        double res0;
        if (type.equals("L")) { double S = 0; for (double x : rs) if (x > 0) S += x; res0 = (4 * S - n * (n + 1.0)) / (2 * n - 1); }
        else if (type.equals("Q")) { // quadratic estimator of Duval & Tweedie (2000): Q0 = n - 1/2 - sqrt(2n^2 - 4 T_n + 1/4)
            double S = 0; for (double x : rs) if (x > 0) S += x;
            double rad = 2.0 * n * n - 4 * S + 0.25;
            res0 = rad < 0 ? Double.POSITIVE_INFINITY : n - 0.5 - Math.sqrt(rad); }
        else { double mn = 0; for (double x : rs) mn = Math.min(mn, x); res0 = n - Math.abs(mn) - 1.5; }
        if (Double.isInfinite(res0)) return n;
        return (int) Math.floor(Math.max(0, res0 + 0.5));
    }

    static double[] rank(double[] x) {
        int n = x.length; Integer[] o = new Integer[n]; for (int i = 0; i < n; i++) o[i] = i;
        Arrays.sort(o, Comparator.comparingDouble(i -> x[i]));
        double[] r = new double[n]; int i = 0;
        while (i < n) { int j = i; while (j + 1 < n && x[o[j + 1]] == x[o[i]]) j++; double avg = (i + j) / 2.0 + 1; for (int q = i; q <= j; q++) r[o[q]] = avg; i = j + 1; }
        return r;
    }
}
