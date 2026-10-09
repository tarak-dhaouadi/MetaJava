package org.metajava.core;

/**
 * Penalised logistic regression for meta-analysis of binary data (meta::metabin, method = "LRP").
 * Binomial logistic model  cbind(events, nonevents) ~ treatment + study  fitted by maximum penalised likelihood with the Jeffreys prior
 * (brglm2::brglmFit, type = "MPL_Jeffreys", i.e. Firth's bias reduction). The random-effects model rescales the standard error by the
 * heterogeneity parameter phi (modified Pearson statistic of Fletcher, truncated at 1), exactly as meta::runLRP does.
 */
public final class Lrp {
    private Lrp() {}

    public static final class Result { public double est = Double.NaN, se = Double.NaN, seRandom = Double.NaN, phi = 1; public boolean ok = true; public String note = ""; }

    public static Result fit(double[] e1, double[] n1, double[] e2, double[] n2, int[] sel) {
        Result res = new Result(); int k = sel.length, rows = 2 * k, p = k + 1;
        if (k == 0) { res.ok = false; return res; }
        double[][] X = new double[rows][p]; double[] y = new double[rows], m = new double[rows];
        for (int i = 0; i < k; i++) { int j = sel[i];
            X[2 * i][1 + i] = 1; y[2 * i] = e2[j]; m[2 * i] = n2[j];                       // control row
            X[2 * i + 1][0] = 1; X[2 * i + 1][1 + i] = 1; y[2 * i + 1] = e1[j]; m[2 * i + 1] = n1[j]; }   // experimental row (treatment indicator)
        double[] beta = new double[p]; double[][] Vinv = null; double[] pi = new double[rows];
        for (int it = 0; it < 500; it++) {
            double[] w = new double[rows];
            for (int r = 0; r < rows; r++) { double eta = 0; for (int c = 0; c < p; c++) eta += X[r][c] * beta[c]; pi[r] = 1 / (1 + Math.exp(-eta)); w[r] = m[r] * pi[r] * (1 - pi[r]); }
            double[][] I = new double[p][p];
            for (int r = 0; r < rows; r++) for (int a = 0; a < p; a++) if (X[r][a] != 0) for (int b = 0; b < p; b++) if (X[r][b] != 0) I[a][b] += w[r] * X[r][a] * X[r][b];
            double[][] V = Lin.inv(I); if (V == null) { res.ok = false; res.note = "Singular information matrix in penalised logistic regression."; return res; }
            double[] U = new double[p];
            for (int r = 0; r < rows; r++) {
                double h = 0; for (int a = 0; a < p; a++) if (X[r][a] != 0) for (int b = 0; b < p; b++) if (X[r][b] != 0) h += X[r][a] * V[a][b] * X[r][b]; h *= w[r];
                double adj = y[r] - m[r] * pi[r] + h * (0.5 - pi[r]); for (int a = 0; a < p; a++) U[a] += X[r][a] * adj;
            }
            double[] step = new double[p]; double mx = 0; for (int a = 0; a < p; a++) { for (int b = 0; b < p; b++) step[a] += V[a][b] * U[b]; mx = Math.max(mx, Math.abs(step[a])); }
            double f = mx > 5 ? 5 / mx : 1; for (int a = 0; a < p; a++) beta[a] += f * step[a];
            Vinv = V; if (mx * f < 1e-10) break;
        }
        // information matrix and covariance at the final estimate
        double[] w = new double[rows]; double chisq = 0, sbar = 0;
        for (int r = 0; r < rows; r++) { double eta = 0; for (int c = 0; c < p; c++) eta += X[r][c] * beta[c]; pi[r] = 1 / (1 + Math.exp(-eta)); w[r] = m[r] * pi[r] * (1 - pi[r]); }
        double[][] I = new double[p][p];
        for (int r = 0; r < rows; r++) for (int a = 0; a < p; a++) if (X[r][a] != 0) for (int b = 0; b < p; b++) if (X[r][b] != 0) I[a][b] += w[r] * X[r][a] * X[r][b];
        Vinv = Lin.inv(I); if (Vinv == null) { res.ok = false; return res; }
        res.est = beta[0]; res.se = Math.sqrt(Vinv[0][0]);
        for (int r = 0; r < rows; r++) { double pr = (y[r] / m[r] - pi[r]) * Math.sqrt(m[r]) / Math.sqrt(pi[r] * (1 - pi[r])); chisq += pr * pr; sbar += (1 - 2 * pi[r]) / (m[r] * pi[r] * (1 - pi[r])); }
        sbar /= rows; double phi = 1;
        if (k > 1) { phi = (chisq / (rows - p)) / (1 + sbar); phi = Double.isNaN(phi) ? 1 : Math.max(phi, 1); }
        res.phi = phi; res.seRandom = res.se * phi;   // meta::runLRP: seTE.random = seTE.common * phi
        return res;
    }
}
