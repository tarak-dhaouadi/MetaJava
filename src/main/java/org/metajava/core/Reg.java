package org.metajava.core;

import java.util.*;

/** Meta-regression (meta::metareg / metafor::rma.uni with moderators). */
public final class Reg {
    public String formula; public Rma fit; public String[] coefNames; public int k, p; public String method;
    public double logLik, deviance, aic, bic, aicc, tau2, seTau2 = Double.NaN, tau, I2, H2, R2 = Double.NaN;
    public double QE, pQE, QM, pQM; public int dfQE, dfQM; public String qmTest = "QM";
    public double[] est, se, zval, pval, lb, ub; public boolean knha; public double dfT = Double.NaN;
    public double[] x1, y1, size; public String xName; // for bubble plot (single numeric covariate)
    public double[] fitted; public int[] used; public boolean intercept;
    public double tau2RE;

    public static Reg run(MetaResult r, List<String> numCovs, Map<String, double[]> numData, List<String> factors, Map<String, String[]> facData, boolean intercept) {
        MetaSpec s = r.spec;
        List<Integer> ok = new ArrayList<>();
        for (int i = 0; i < r.TE.length; i++) {
            boolean good = !Double.isNaN(r.TE[i]) && !Double.isNaN(r.seTE[i]) && r.seTE[i] > 0;
            for (String c : numCovs) good &= !Double.isNaN(numData.get(c)[i]);
            for (String f : factors) good &= facData.get(f)[i] != null && !facData.get(f)[i].isBlank();
            if (good) ok.add(i);
        }
        int k = ok.size();
        List<String> names = new ArrayList<>(); List<double[]> cols = new ArrayList<>();
        if (intercept) { names.add("intercept"); double[] o = new double[k]; Arrays.fill(o, 1); cols.add(o); }
        for (String c : numCovs) { names.add(c); double[] x = new double[k]; for (int j = 0; j < k; j++) x[j] = numData.get(c)[ok.get(j)]; cols.add(x); }
        for (String f : factors) {
            TreeSet<String> lv = new TreeSet<>(); for (int i : ok) lv.add(facData.get(f)[i].trim());
            List<String> L = new ArrayList<>(lv);
            for (int a = intercept ? 1 : 0; a < L.size(); a++) { names.add(f + L.get(a)); double[] x = new double[k]; for (int j = 0; j < k; j++) x[j] = facData.get(f)[ok.get(j)].trim().equals(L.get(a)) ? 1 : 0; cols.add(x); }
        }
        int p = cols.size(); double[][] X = new double[k][p]; for (int j = 0; j < k; j++) for (int a = 0; a < p; a++) X[j][a] = cols.get(a)[j];
        double[] y = new double[k], v = new double[k]; for (int j = 0; j < k; j++) { y[j] = r.TE[ok.get(j)]; v[j] = r.seTE[ok.get(j)] * r.seTE[ok.get(j)]; }
        Reg g = new Reg(); g.k = k; g.p = p; g.intercept = intercept; g.method = s.methodTau;
        if (k <= p) throw new IllegalArgumentException("Number of studies (" + k + ") must exceed number of coefficients (" + p + ").");
        g.fit = Rma.fit(y, v, X, s.methodTau); g.coefNames = names.toArray(new String[0]);
        g.tau2 = g.fit.tau2; g.tau = Math.sqrt(g.tau2);
        // intercept-only model for R2
        double[][] X0 = new double[k][1]; for (double[] q : X0) q[0] = 1;
        g.tau2RE = Rma.fit(y, v, X0, s.methodTau).tau2;
        if (intercept && p > 1 && g.tau2RE > 0) g.R2 = Math.max(0, 100 * (g.tau2RE - g.tau2) / g.tau2RE);
        else if (intercept && p > 1) g.R2 = 0;
        // fit stats (ML type)
        double ll = 0; double[] w = g.fit.wTilde;
        for (int j = 0; j < k; j++) { double f = 0; for (int a = 0; a < p; a++) f += X[j][a] * g.fit.beta[a]; ll += -0.5 * Math.log(2 * Math.PI * (v[j] + g.tau2)) - 0.5 * w[j] * (y[j] - f) * (y[j] - f); }
        double llSat = 0; for (int j = 0; j < k; j++) llSat += -0.5 * Math.log(2 * Math.PI * v[j]);
        int np = p + (s.methodTau.equals("FE") ? 0 : 1);
        g.logLik = ll; g.deviance = -2 * (ll - llSat); g.aic = -2 * ll + 2 * np; g.bic = -2 * ll + np * Math.log(k); g.aicc = -2 * ll + 2.0 * np * k / Math.max(k - np - 1, 1e-9);
        // QE, QM
        g.QE = g.fit.QE; g.dfQE = k - p; g.pQE = Dist.pchisqUpper(g.QE, g.dfQE);
        g.knha = s.methodRandomCi.equals("HK");
        double s2 = 1;
        if (g.knha) { double rssw = 0; for (int j = 0; j < k; j++) { double f = 0; for (int a = 0; a < p; a++) f += X[j][a] * g.fit.beta[a]; rssw += w[j] * (y[j] - f) * (y[j] - f); } s2 = rssw / (k - p); if (s.adhocHakn.equals("se")) s2 = Math.max(1, s2); }
        g.est = g.fit.beta.clone(); g.se = new double[p]; for (int a = 0; a < p; a++) g.se[a] = Math.sqrt(g.fit.vb[a][a] * s2);
        g.zval = new double[p]; g.pval = new double[p]; g.lb = new double[p]; g.ub = new double[p];
        double alpha = 1 - s.levelMa;
        for (int a = 0; a < p; a++) {
            g.zval[a] = g.est[a] / g.se[a];
            if (g.knha) { g.dfT = k - p; double q = Dist.qt(1 - alpha / 2, g.dfT); g.pval[a] = Dist.ptTwoSided(g.zval[a], g.dfT); g.lb[a] = g.est[a] - q * g.se[a]; g.ub[a] = g.est[a] + q * g.se[a]; }
            else { double q = Dist.qnorm(1 - alpha / 2); g.pval[a] = Dist.pnormTwoSided(g.zval[a]); g.lb[a] = g.est[a] - q * g.se[a]; g.ub[a] = g.est[a] + q * g.se[a]; }
        }
        int m0 = intercept ? 1 : 0, dm = p - m0;
        if (dm > 0) {
            double[][] sub = new double[dm][dm]; double[] bb = new double[dm];
            for (int a = 0; a < dm; a++) { bb[a] = g.est[a + m0]; for (int b = 0; b < dm; b++) sub[a][b] = g.fit.vb[a + m0][b + m0] * s2; }
            double[][] inv = Lin.inv(sub); double qm = 0; for (int a = 0; a < dm; a++) for (int b = 0; b < dm; b++) qm += bb[a] * inv[a][b] * bb[b];
            g.dfQM = dm;
            if (g.knha) { g.qmTest = "QM"; g.QM = qm / dm; g.pQM = Dist.pfUpper(g.QM, dm, k - p); }
            else { g.QM = qm; g.pQM = Dist.pchisqUpper(qm, dm); }
        }
        double sv = Rma.typicalV(k, p, v, X); g.I2 = 100 * g.tau2 / (g.tau2 + sv); g.H2 = (g.tau2 + sv) / sv;
        g.seTau2 = seTau2(g.fit, s.methodTau, X, v);
        g.fitted = new double[k]; for (int j = 0; j < k; j++) { double f = 0; for (int a = 0; a < p; a++) f += X[j][a] * g.fit.beta[a]; g.fitted[j] = f; }
        g.used = ok.stream().mapToInt(Integer::intValue).toArray();
        if (numCovs.size() == 1 && factors.isEmpty()) { g.xName = numCovs.get(0); g.x1 = new double[k]; g.y1 = y; g.size = new double[k]; for (int j = 0; j < k; j++) { g.x1[j] = numData.get(numCovs.get(0))[ok.get(j)]; g.size[j] = w[j]; } }
        g.formula = (intercept ? "" : "0 + ") ;
        return g;
    }

    static double seTau2(Rma m, String method, double[][] X, double[] v) {
        int k = m.k, p = m.p; double t2 = m.tau2;
        double[] w = new double[k];
        switch (method) {
            case "DL": {
                for (int i = 0; i < k; i++) w[i] = 1 / v[i];
                double[][] P = pmat(X, w); double trP = 0; for (int i = 0; i < k; i++) trP += P[i][i];
                double[][] PS = new double[k][k]; for (int i = 0; i < k; i++) for (int j = 0; j < k; j++) PS[i][j] = P[i][j] * (v[j] + t2);
                double tr = 0; for (int i = 0; i < k; i++) for (int j = 0; j < k; j++) tr += PS[i][j] * PS[j][i];
                return Math.sqrt(2 * tr) / trP;
            }
            case "REML": { for (int i = 0; i < k; i++) w[i] = 1 / (v[i] + t2); double[][] P = pmat(X, w); double tr = 0; for (int i = 0; i < k; i++) for (int j = 0; j < k; j++) tr += P[i][j] * P[j][i]; return Math.sqrt(2 / tr); }
            case "ML": { double s = 0; for (int i = 0; i < k; i++) { double wi = 1 / (v[i] + t2); s += wi * wi; } return Math.sqrt(2 / s); }
            default: return Double.NaN;
        }
    }

    static double[][] pmat(double[][] X, double[] w) {
        int k = X.length, p = X[0].length; double[][] xtwx = new double[p][p];
        for (int i = 0; i < k; i++) for (int a = 0; a < p; a++) for (int b = 0; b < p; b++) xtwx[a][b] += X[i][a] * w[i] * X[i][b];
        double[][] inv = Lin.inv(xtwx); double[][] P = new double[k][k];
        for (int i = 0; i < k; i++) for (int j = 0; j < k; j++) { double h = 0; for (int a = 0; a < p; a++) for (int b = 0; b < p; b++) h += X[i][a] * inv[a][b] * X[j][b]; P[i][j] = (i == j ? w[i] : 0) - w[i] * w[j] * h; }
        return P;
    }
}
