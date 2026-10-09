package org.metajava.core;

import java.util.*;

/** Outlier and influence diagnostics (Viechtbauer & Cheung, 2010; metafor::influence.rma.uni). */
public final class Influence {
    public String[] labels; public int k, p;
    public double[] rstudent, dffits, cookD, covR, tau2Del, QEdel, hat, weight, rstandard;
    public double[][] dfbs; public String[] coefNames; public boolean[] flagged; public String[] flagReason;
    public double tau2, QE;

    /** Diagnostics for a random-effects (X=null) or mixed-effects model. */
    public static Influence run(double[] y, double[] v, double[][] X, String method, String[] labels, String[] coefNames) {
        return run(y, v, X, method, labels, coefNames, false, false);
    }

    /** s2w scaling as in metafor's rma.uni: 1 unless test="knha"/"adhoc"; adhoc floors it at 1. */
    static double s2w(Rma f, boolean knha, boolean adhoc) {
        if (!knha) return 1;
        double rss = 0;
        for (int i = 0; i < f.k; i++) { double fit = 0; for (int a = 0; a < f.p; a++) fit += f.X[i][a] * f.beta[a]; rss += f.wTilde[i] * (f.yi[i] - fit) * (f.yi[i] - fit); }
        double s = rss <= 2.220446049250313e-16 ? 0 : rss / (f.k - f.p);
        return adhoc && s < 1 ? 1 : s;
    }

    /** knha=true mimics test="knha" (adhoc=true: test="adhoc", scale factor floored at 1). */
    public static Influence run(double[] y, double[] v, double[][] X0, String method, String[] labels, String[] coefNames, boolean knha, boolean adhoc) {
        final int k = y.length; final double[][] X = X0 != null ? X0 : ones(k);
        int p = X[0].length;
        Influence o = new Influence(); o.k = k; o.p = p; o.labels = labels;
        o.coefNames = coefNames != null ? coefNames : defaultNames(p);
        Rma full = Rma.fit(y, v, X, method); o.tau2 = full.tau2; o.QE = full.QE;
        double[] w = full.wTilde; double sw = 0; for (double x : w) sw += x;
        o.rstudent = new double[k]; o.dffits = new double[k]; o.cookD = new double[k]; o.covR = new double[k];
        o.tau2Del = new double[k]; o.QEdel = new double[k]; o.hat = new double[k]; o.weight = new double[k]; o.rstandard = new double[k]; o.dfbs = new double[k][p];
        for (double[] arr : new double[][]{o.rstudent, o.dffits, o.cookD, o.covR, o.tau2Del, o.QEdel}) Arrays.fill(arr, Double.NaN);
        for (double[] r : o.dfbs) Arrays.fill(r, Double.NaN);
        final double[] hdiag = new double[k]; // diagonal of the hat matrix (the only part used)
        for (int i = 0; i < k; i++) { double h = 0; for (int a = 0; a < p; a++) for (int b = 0; b < p; b++) h += X[i][a] * full.vb[a][b] * X[i][b]; hdiag[i] = h * w[i]; }
        double[][] xtwx = Lin.inv(full.vb);   // inverse of the unscaled full-model vb
        double s2wFull = s2w(full, knha, adhoc);
        double detFullS = Lin.det(scale(full.vb, s2wFull));
        final double swf = sw; final double s2wFullF = s2wFull; final double detFullSF = detFullS; final double[][] xtwxF = xtwx; final Rma fullF = full;
        Par.forRange(k, i -> {
            o.hat[i] = hdiag[i]; o.weight[i] = 100 * w[i] / swf;
            double yhat = 0; for (int a = 0; a < p; a++) yhat += X[i][a] * full.beta[a];
            double var = (1 - o.hat[i]) * (v[i] + full.tau2);
            o.rstandard[i] = (y[i] - yhat) / Math.sqrt(var);
            int m = k - 1; double[] y2 = new double[m], v2 = new double[m]; double[][] X2 = new double[m][];
            int c = 0; for (int j = 0; j < k; j++) if (j != i) { y2[c] = y[j]; v2[c] = v[j]; X2[c] = X[j]; c++; }
            Rma d;
            try { d = Rma.fit(y2, v2, X2, method); } catch (Exception e) { return; }
            if (d.vb == null || Double.isNaN(d.tau2)) return;
            double s2wDel = s2w(d, knha, adhoc);
            double yhatDel = 0; for (int a = 0; a < p; a++) yhatDel += X[i][a] * d.beta[a];
            double varYhatDel = 0; for (int a = 0; a < p; a++) for (int b = 0; b < p; b++) varYhatDel += X[i][a] * (s2wDel * d.vb[a][b]) * X[i][b];
            o.tau2Del[i] = d.tau2; o.QEdel[i] = d.QE;
            // metafor: seresid = sqrt(vi*s2w.del + vdelpred + tau2.del*s2w.del), with vdelpred from the (s2w-scaled) deleted-model vb
            double resid = y[i] - yhatDel; if (Math.abs(resid) < 100 * 2.220446049250313e-16) resid = 0;
            o.rstudent[i] = resid / Math.sqrt(v[i] * s2wDel + varYhatDel + d.tau2 * s2wDel);
            o.dffits[i] = (yhat - yhatDel) / Math.sqrt(s2wDel * o.hat[i] * (d.tau2 + v[i]));
            // vb.del: full design/all k studies, with tau2.del plugged in (not the leave-one-out covariance matrix)
            double[][] xtwxDel = new double[p][p];
            for (int j = 0; j < k; j++) { double wj = 1 / (v[j] + d.tau2); for (int a = 0; a < p; a++) for (int b = 0; b < p; b++) xtwxDel[a][b] += X[j][a] * wj * X[j][b]; }
            double[][] vbDel = Lin.inv(xtwxDel);
            double[] db = new double[p]; for (int a = 0; a < p; a++) db[a] = full.beta[a] - d.beta[a];
            if (vbDel != null) for (int a = 0; a < p; a++) o.dfbs[i][a] = db[a] / Math.sqrt(s2wDel * vbDel[a][a]);
            // Cook's distance: dfb' * inv(vb_full) * dfb, where vb_full carries the full model's s2w
            double cd = 0; for (int a = 0; a < p; a++) for (int b = 0; b < p; b++) cd += db[a] * xtwx[a][b] * db[b];
            o.cookD[i] = cd / s2wFull;
            o.covR[i] = Lin.det(scale(d.vb, s2wDel)) / detFullS;
        });
        o.flagged = new boolean[k]; o.flagReason = new String[k];
        // metafor::influence.rma.uni is.infl: rstudent and cov.r are displayed but NOT used for classification
        double dfThr = 3 * Math.sqrt((double) p / ((double) k - p)), hatThr = 3.0 * p / k;
        for (int i = 0; i < k; i++) {
            List<String> rs = new ArrayList<>();
            if (Math.abs(o.dffits[i]) > dfThr) rs.add("dffits");
            if (Dist.pchisq(o.cookD[i], p) > 0.50) rs.add("cook.d");
            if (o.hat[i] > hatThr) rs.add("hat");
            for (int a = 0; a < p; a++) if (Math.abs(o.dfbs[i][a]) > 1) { rs.add("dfbs"); break; }
            o.flagged[i] = !rs.isEmpty(); o.flagReason[i] = String.join(", ", rs);
        }
        return o;
    }

    static double[][] scale(double[][] m, double f) { double[][] r = new double[m.length][]; for (int i = 0; i < m.length; i++) { r[i] = m[i].clone(); for (int j = 0; j < r[i].length; j++) r[i][j] *= f; } return r; }

    static double[][] ones(int k) { double[][] x = new double[k][1]; for (double[] r : x) r[0] = 1; return x; }

    static String[] defaultNames(int p) { String[] n = new String[p]; for (int i = 0; i < p; i++) n[i] = i == 0 ? "intrcpt" : "x" + i; return n; }
}
