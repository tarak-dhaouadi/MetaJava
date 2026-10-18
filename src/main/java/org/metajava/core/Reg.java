package org.metajava.core;

import java.util.*;

/** Meta-regression (meta::metareg / metafor::rma.uni with moderators). */
public final class Reg {
    public double altTau2 = Double.NaN, altGain = Double.NaN;   // tau^2 of a higher mode of the likelihood (NaN: none), see Rma.higherMode()
    public String formula; public Rma fit; public String[] coefNames; public int k, p; public String method;
    public double logLik, deviance, aic, bic, aicc, tau2, seTau2 = Double.NaN, tau, I2, H2, R2 = Double.NaN;
    public double QE, pQE, QM, pQM; public int dfQE, dfQM; public String qmTest = "QM";
    public double[] est, se, zval, pval, lb, ub; public boolean knha; public double dfT = Double.NaN;
    public double[] x1, y1, size; public String xName; // for bubble plot (single numeric covariate)
    public double[] fitted; public int[] used; public boolean intercept;
    public double s2 = 1;   // Knapp-Hartung scale factor of the coefficient covariance matrix (1 without KNHA)
    public double[] plotX;   // x values used by the adjusted regression plot (set by the plot)
    public double tau2RE;
    public int[] refPos = new int[0]; public String[] refLabel = new String[0];   // reference-category rows of the coefficient tables

    /** Design matrix of a meta-regression: rows of the studies used, response, sampling variances and coefficient names. */
    public static final class Design { public double[][] X; public double[] y, v; public String[] names; public int[] rows; public int k, p;
        /** Reference categories of the factors: refPos[i] = index of the first coefficient of that factor (the row is shown just before it), refLabel[i] = "Factor: Level". */
        public int[] refPos = new int[0]; public String[] refLabel = new String[0]; }

    /** Complete cases of the given terms (studies with a usable effect and non-missing covariates / factors). */
    public static int[] completeCases(MetaResult r, List<String> numCovs, Map<String, double[]> numData, List<String> factors, Map<String, String[]> facData) {
        List<Integer> ok = new ArrayList<>();
        for (int i = 0; i < r.TE.length; i++) {
            boolean good = !Double.isNaN(r.TE[i]) && !Double.isNaN(r.seTE[i]) && r.seTE[i] > 0;
            for (String c : numCovs) good &= !Double.isNaN(numData.get(c)[i]);
            for (String f : factors) good &= facData.get(f)[i] != null && !facData.get(f)[i].isBlank();
            if (good) ok.add(i);
        }
        return ok.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Builds the design matrix on the given rows (null: the complete cases of the terms). Factors are dummy coded against their first level (alphabetical unless a reference level is chosen) when an intercept is present. */
    public static Design design(MetaResult r, List<String> numCovs, Map<String, double[]> numData, List<String> factors, Map<String, String[]> facData, boolean intercept, int[] rows) {
        return design(r, numCovs, numData, factors, facData, intercept, rows, null);
    }

    /** As above; refs maps a factor to the level used as its reference category (default, or when the level is absent from the studies used: the first level alphabetically). */
    public static Design design(MetaResult r, List<String> numCovs, Map<String, double[]> numData, List<String> factors, Map<String, String[]> facData, boolean intercept, int[] rows, Map<String, String> refs) {
        if (rows == null) rows = completeCases(r, numCovs, numData, factors, facData);
        final int[] ok = rows; int k = ok.length;
        List<String> names = new ArrayList<>(); List<double[]> cols = new ArrayList<>();
        if (intercept) { names.add("intercept"); double[] o = new double[k]; Arrays.fill(o, 1); cols.add(o); }
        for (String c : numCovs) { names.add(c); double[] x = new double[k]; for (int j = 0; j < k; j++) x[j] = numData.get(c)[ok[j]]; cols.add(x); }
        List<Integer> rp = new ArrayList<>(); List<String> rl = new ArrayList<>();
        for (String f : factors) {
            TreeSet<String> lv = new TreeSet<>(); for (int i : ok) lv.add(facData.get(f)[i].trim());
            List<String> L = new ArrayList<>(lv);
            String want = refs == null ? null : refs.get(f); if (want != null && L.remove(want.trim())) L.add(0, want.trim());
            if (intercept && !L.isEmpty()) { rp.add(names.size()); rl.add(f + ": " + L.get(0)); }
            for (int a = intercept ? 1 : 0; a < L.size(); a++) { names.add(f + ": " + L.get(a)); double[] x = new double[k]; for (int j = 0; j < k; j++) x[j] = facData.get(f)[ok[j]].trim().equals(L.get(a)) ? 1 : 0; cols.add(x); }
        }
        Design d = new Design(); d.k = k; d.p = cols.size(); d.rows = ok; d.names = names.toArray(new String[0]);
        d.refPos = rp.stream().mapToInt(Integer::intValue).toArray(); d.refLabel = rl.toArray(new String[0]);
        d.X = new double[k][d.p]; for (int j = 0; j < k; j++) for (int a = 0; a < d.p; a++) d.X[j][a] = cols.get(a)[j];
        d.y = new double[k]; d.v = new double[k]; for (int j = 0; j < k; j++) { d.y[j] = r.TE[ok[j]]; d.v[j] = r.seTE[ok[j]] * r.seTE[ok[j]]; }
        return d;
    }

    /** Wald-type statistics of a fitted model: the Knapp-Hartung scale factor s2, the coefficient test statistics (z, or t with knha) and the omnibus test of the moderators (chi-square, or F with knha). */
    public static final class Wald { public double s2 = 1, QM = Double.NaN; public int dfQM; public double[] se, z; }

    public static Wald wald(Rma fit, boolean knha, boolean adhocSe, boolean intercept) {
        int k = fit.k, p = fit.p; double[] w = fit.wTilde; double[] y = fit.yi; double[][] X = fit.X; Wald o = new Wald(); double[] bw = fit.betaW();   // metafor: RSS.knha always uses the weights 1/(vi + tau2)
        if (knha) { double rssw = 0; for (int j = 0; j < k; j++) { double f = 0; for (int a = 0; a < p; a++) f += X[j][a] * bw[a]; rssw += w[j] * (y[j] - f) * (y[j] - f); } o.s2 = rssw / (k - p); if (adhocSe) o.s2 = Math.max(1, o.s2); }
        o.se = new double[p]; o.z = new double[p];
        for (int a = 0; a < p; a++) { o.se[a] = Math.sqrt(fit.vb[a][a] * o.s2); o.z[a] = fit.beta[a] / o.se[a]; }
        int m0 = intercept ? 1 : 0, dm = p - m0; o.dfQM = dm;
        if (dm > 0) {
            double[][] sub = new double[dm][dm]; double[] bb = new double[dm];
            for (int a = 0; a < dm; a++) { bb[a] = fit.beta[a + m0]; for (int b = 0; b < dm; b++) sub[a][b] = fit.vb[a + m0][b + m0] * o.s2; }
            double[][] inv = Lin.inv(sub); double qm = 0; for (int a = 0; a < dm; a++) for (int b = 0; b < dm; b++) qm += bb[a] * inv[a][b] * bb[b];
            o.QM = knha ? qm / dm : qm;
        }
        return o;
    }

    /** Fit statistics defined as in metafor::rma.uni: the restricted likelihood for method REML, the ordinary (ML) likelihood for every other estimator. */
    public static final class FitStats { public double ll, deviance, aic, bic, aicc; public int parms; public boolean reml; }

    public static FitStats fitStats(Rma m, boolean reml) {
        int k = m.k, p = m.p; double t2 = m.tau2; FitStats f = new FitStats(); f.reml = reml; f.parms = p + (m.method.equals("FE") ? 0 : 1);
        double rss = 0, slog = 0, llSat = 0;
        for (int j = 0; j < k; j++) { double fit = 0; for (int a = 0; a < p; a++) fit += m.X[j][a] * m.beta[a]; rss += (m.yi[j] - fit) * (m.yi[j] - fit) / (m.vi[j] + t2); slog += Math.log(m.vi[j] + t2); llSat += -0.5 * Math.log(2 * Math.PI * m.vi[j]); }
        double ll; double keff = k;
        if (reml) {
            double[][] xtx = new double[p][p], xtwx = new double[p][p];
            for (int j = 0; j < k; j++) for (int a = 0; a < p; a++) for (int b = 0; b < p; b++) { xtx[a][b] += m.X[j][a] * m.X[j][b]; xtwx[a][b] += m.X[j][a] * m.X[j][b] / (m.vi[j] + t2); }
            ll = -0.5 * (k - p) * Math.log(2 * Math.PI) + 0.5 * Math.log(Lin.det(xtx)) - 0.5 * slog - 0.5 * Math.log(Lin.det(xtwx)) - 0.5 * rss;
            f.deviance = -2 * ll; keff = k - p;
        } else { ll = -0.5 * k * Math.log(2 * Math.PI) - 0.5 * slog - 0.5 * rss; f.deviance = -2 * (ll - llSat); }
        f.ll = ll; f.aic = -2 * ll + 2 * f.parms; f.bic = -2 * ll + f.parms * Math.log(keff);
        double kk = Math.max(keff, f.parms + 2); f.aicc = -2 * ll + 2.0 * f.parms * kk / (kk - f.parms - 1);
        return f;
    }

    public boolean adhocSe;   // Knapp-Hartung scale factor floored at 1 (ad hoc "se")
    public String adhocOption = "";   // ad hoc option of the analysis ("", se, ci, IQWiG6); only "se" has a metafor counterpart (test = "adhoc")

    /** One line saying which Knapp-Hartung test was used (as metafor: "knha", or "adhoc" for the option "se"), and why an ad hoc option without a metafor counterpart was not applied; empty without Knapp-Hartung. */
    public String hkNote() {
        if (!knha) return "";
        String sf = " Scale factor = " + Fmt.num(s2, 4) + ".";
        if (adhocSe) return "Knapp-Hartung t-test with the scale factor set to at least 1 (metafor: test = \"adhoc\", the ad hoc option \"se\")." + sf;
        if (!adhocOption.isEmpty()) return "Knapp-Hartung t-test (metafor: test = \"knha\"). The ad hoc option \"" + adhocOption + "\" has no metafor counterpart: metafor's test = \"adhoc\" is the ad hoc option \"se\"; it is not applied to a meta-regression." + sf;
        return "Knapp-Hartung t-test (metafor: test = \"knha\")." + sf;
    }
    public double[][] X; public double[] yv, vv;   // design matrix, effects and variances as fitted (for permutation tests)

    public static Reg run(MetaResult r, List<String> numCovs, Map<String, double[]> numData, List<String> factors, Map<String, String[]> facData, boolean intercept) {
        return run(r, numCovs, numData, factors, facData, intercept, null);
    }

    public static Reg run(MetaResult r, List<String> numCovs, Map<String, double[]> numData, List<String> factors, Map<String, String[]> facData, boolean intercept, Map<String, String> refs) {
        MetaSpec s = r.spec;
        Design D = design(r, numCovs, numData, factors, facData, intercept, null, refs);
        List<Integer> ok = new ArrayList<>(); for (int i : D.rows) ok.add(i);
        int k = D.k, p = D.p; double[][] X = D.X; double[] y = D.y, v = D.v;
        Reg g = new Reg(); g.k = k; g.p = p; g.intercept = intercept; g.method = s.methodTau; g.X = X; g.yv = y; g.vv = v;
        if (k <= p) throw new IllegalArgumentException("Number of studies (" + k + ") must exceed number of coefficients (" + p + ").");
        double[] gqw = Pool.genqWeightsFor(s, D.rows); g.fit = Rma.fit(y, v, X, s.methodTau, gqw); g.coefNames = D.names; g.refPos = D.refPos; g.refLabel = D.refLabel;
        g.tau2 = g.fit.tau2; g.tau = Math.sqrt(g.tau2); g.altTau2 = g.fit.higherMode(); g.altGain = g.fit.higherModeGain;
        // intercept-only model for R2
        double[][] X0 = new double[k][1]; for (double[] q : X0) q[0] = 1;
        g.tau2RE = Rma.fit(y, v, X0, s.methodTau, gqw).tau2;
        if (intercept && p > 1 && g.tau2RE > 0) g.R2 = Math.max(0, 100 * (g.tau2RE - g.tau2) / g.tau2RE);
        else if (intercept && p > 1) g.R2 = 0;
        double[] w = g.fit.wTilde;
        FitStats fs = fitStats(g.fit, s.methodTau.equals("REML"));
        g.logLik = fs.ll; g.deviance = fs.deviance; g.aic = fs.aic; g.bic = fs.bic; g.aicc = fs.aicc;
        // QE, QM
        g.QE = g.fit.QE; g.dfQE = k - p; g.pQE = Dist.pchisqUpper(g.QE, g.dfQE);
        g.knha = s.methodRandomCi.equals("HK"); g.adhocSe = s.adhocHakn.equals("se"); g.adhocOption = s.adhocHakn;
        Wald W = wald(g.fit, g.knha, g.adhocSe, intercept);
        g.est = g.fit.beta.clone(); g.se = W.se; g.s2 = W.s2;
        g.zval = W.z; g.pval = new double[p]; g.lb = new double[p]; g.ub = new double[p];
        double alpha = 1 - s.levelMa;
        for (int a = 0; a < p; a++) {
            if (g.knha) { g.dfT = k - p; double q = Dist.qt(1 - alpha / 2, g.dfT); g.pval[a] = Dist.ptTwoSided(g.zval[a], g.dfT); g.lb[a] = g.est[a] - q * g.se[a]; g.ub[a] = g.est[a] + q * g.se[a]; }
            else { double q = Dist.qnorm(1 - alpha / 2); g.pval[a] = Dist.pnormTwoSided(g.zval[a]); g.lb[a] = g.est[a] - q * g.se[a]; g.ub[a] = g.est[a] + q * g.se[a]; }
        }
        int dm = W.dfQM;
        if (dm > 0) {
            g.dfQM = dm; g.QM = W.QM;
            if (g.knha) { g.qmTest = "QM"; g.pQM = Dist.pfUpper(g.QM, dm, k - p); }
            else g.pQM = Dist.pchisqUpper(g.QM, dm);
        }
        double sv = Rma.typicalV(k, p, v, X); g.I2 = 100 * g.tau2 / (g.tau2 + sv); g.H2 = (g.tau2 + sv) / sv;
        g.seTau2 = seTau2(g.fit, s.methodTau, X, v);
        g.fitted = new double[k]; for (int j = 0; j < k; j++) { double f = 0; for (int a = 0; a < p; a++) f += X[j][a] * g.fit.beta[a]; g.fitted[j] = f; }
        g.used = D.rows;
        if (numCovs.size() == 1 && factors.isEmpty()) { g.xName = numCovs.get(0); g.x1 = new double[k]; g.y1 = y; g.size = new double[k]; for (int j = 0; j < k; j++) { g.x1[j] = numData.get(numCovs.get(0))[ok.get(j)]; g.size[j] = w[j]; } }
        g.formula = (intercept ? "" : "0 + ") ;
        return g;
    }


    /** Fitted value and confidence bounds {fit, lower, upper} of the model at a model-matrix row (z or, with KNHA, t quantile). */
    public double[] predict(double[] x, double level) {
        double f = 0, v = 0; for (int a = 0; a < p; a++) { f += x[a] * fit.beta[a]; for (int b = 0; b < p; b++) v += x[a] * fit.vb[a][b] * x[b]; }
        double se = Math.sqrt(Math.max(0, v * s2)), al = 1 - level, q = knha ? Dist.qt(1 - al / 2, k - p) : Dist.qnorm(1 - al / 2);
        return new double[]{f, f - q * se, f + q * se};
    }

    /** Prediction-interval bounds {lower, upper} of the model at a model-matrix row (metafor::predict: fit +/- crit * sqrt(se^2 + tau^2); z, or t with KNHA, quantile). */
    public double[] predictInterval(double[] x, double level) {
        double f = 0, v = 0; for (int a = 0; a < p; a++) { f += x[a] * fit.beta[a]; for (int b = 0; b < p; b++) v += x[a] * fit.vb[a][b] * x[b]; }
        double se = Math.sqrt(Math.max(0, v * s2) + Math.max(0, tau2)), al = 1 - level, q = knha ? Dist.qt(1 - al / 2, k - p) : Dist.qnorm(1 - al / 2);
        return new double[]{f - q * se, f + q * se};
    }

    /** Inserts the reference-category rows into the rows of a coefficient table (row a = coefficient a). Columns whose header is empty (significance stars) stay blank, all other columns get a dash. */
    public List<String[]> withReferenceRows(List<String> head, List<String[]> rows) {
        List<String[]> out = new ArrayList<>();
        for (int a = 0; a < rows.size(); a++) {
            for (int q = 0; q < refPos.length; q++) if (refPos[q] == a) {
                String[] r = new String[head.size()]; r[0] = refLabel[q] + " (reference)";
                for (int c = 1; c < r.length; c++) r[c] = head.get(c).isEmpty() ? "" : Report.DASH;
                out.add(r);
            }
            out.add(rows.get(a));
        }
        return out;
    }

    /** Standard error of tau^2 (metafor se.tau2) for every supported estimator, see {@link Rma#seTau2()}. */
    static double seTau2(Rma m, String method, double[][] X, double[] v) { return m.seTau2(); }

    static double[][] pmat(double[][] X, double[] w) {
        int k = X.length, p = X[0].length; double[][] xtwx = new double[p][p];
        for (int i = 0; i < k; i++) for (int a = 0; a < p; a++) for (int b = 0; b < p; b++) xtwx[a][b] += X[i][a] * w[i] * X[i][b];
        double[][] inv = Lin.inv(xtwx); double[][] P = new double[k][k];
        for (int i = 0; i < k; i++) for (int j = 0; j < k; j++) { double h = 0; for (int a = 0; a < p; a++) for (int b = 0; b < p; b++) h += X[i][a] * inv[a][b] * X[j][b]; P[i][j] = (i == j ? w[i] : 0) - w[i] * w[j] * h; }
        return P;
    }
}
