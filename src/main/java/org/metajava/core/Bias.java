package org.metajava.core;

import java.util.*;

/** Tests for funnel plot asymmetry (meta::metabias). */
public final class Bias {
    private Bias() {}

    public static final class Result {
        public String method, label, varModel = "", statName;
        public double statistic, pval, bias, seBias, tau = Double.NaN, intercept, seIntercept; public Double df = null;
        public double[] plotX, plotY;          // regression plot data (radial form)
        public String plotXlab = "", plotYlab = "";
        public double kendallKs, kendallSe; public String methodTau = "DL";
        public boolean correct;
        public String error;
        public String methodBias;
        /** "meta" (meta::metabias) or "metafor" (metafor::regtest / metafor::ranktest). */
        public String pkg = "meta";
        // metafor only
        public String model = "", predictor = "", testKind = "";
        public double limitEst = Double.NaN, limitLo = Double.NaN, limitHi = Double.NaN;
        public boolean exactP;   // ranktest: exact Kendall p-value (no ties) instead of the normal approximation
        public boolean ties;
        public double kendallS = Double.NaN;
    }

    /** Entries of the "Test method" list: every test of the suite as implemented by meta and, where metafor has an equivalent, by metafor. */
    public static final String[] LIST = {
        "Egger (meta)", "Egger (metafor)", "Begg (meta)", "Begg (metafor)", "Thompson (meta)", "Thompson (metafor)",
        "Harbord (meta)", "Peters (meta)", "Peters (metafor)", "Macaskill (meta)", "Macaskill (metafor)",
        "Deeks (meta)", "Deeks (metafor)", "Pustejovsky (meta)", "Pustejovsky (metafor)", "Schwarzer (meta)"};

    /** "Egger (metafor)" -> "Egger"; a bare name (old projects) -> itself. */
    public static String baseName(String m) { if (m == null) return null; int i = m.indexOf(" ("); return i < 0 ? m : m.substring(0, i); }
    public static boolean isMetafor(String m) { return m != null && m.endsWith("(metafor)"); }
    /** Old project files stored the bare name (meta only). */
    public static String withPkg(String m) { return m == null || m.contains("(") || m.equals("Default") ? m : m + " (meta)"; }

    public static List<String> methodsFor(String type, String sm) {
        List<String> m = new ArrayList<>(List.of("Egger", "Begg", "Thompson", "Macaskill", "Pustejovsky"));
        if (type.equals("bin")) { m.addAll(List.of("Harbord", "Peters", "Schwarzer")); if (sm.equals("DOR")) m.add("Deeks"); }
        if (type.equals("prop")) m.add("Peters");
        List<String> out = new ArrayList<>();
        for (String e : LIST) if (m.contains(baseName(e))) out.add(e);
        return out;
    }

    public static Result run(MetaResult r, String method, boolean correct) {
        MetaSpec s = r.spec; MetaData d = r.data; Result res = new Result(); res.methodBias = method; res.correct = correct;
        boolean mf = isMetafor(method); method = baseName(method);
        if (method == null) method = s.type.equals("bin") ? (s.sm.equals("OR") ? "Harbord" : s.sm.equals("DOR") ? "Deeks" : "Egger") : "Egger";
        if (mf && (method.equals("Harbord") || method.equals("Schwarzer"))) { res.methodBias = method; res.pkg = "metafor"; res.error = method + " test is not available in metafor (use \"" + method + " (meta)\")."; return res; }
        res.methodBias = method; res.pkg = mf ? "metafor" : "meta";
        List<Integer> ok = new ArrayList<>();
        for (int i = 0; i < r.TE.length; i++) if (!Double.isNaN(r.TE[i]) && !Double.isNaN(r.seTE[i]) && r.seTE[i] > 0) ok.add(i);
        int k = ok.size();
        double[] te = new double[k], se = new double[k];
        for (int j = 0; j < k; j++) { te[j] = r.TE[ok.get(j)]; se[j] = r.seTE[ok.get(j)]; }
        if (k < 3) { res.error = "Number of studies (k=" + k + ") too small to test for small study effects (k >= 3 required)."; return res; }
        if (mf) return Metafor.run(r, method, correct, res, ok, te, se);
        boolean same = true; for (double x : se) if (Math.abs(x - se[0]) > 1e-12) same = false;
        if (same && !method.equals("Harbord") && !method.equals("Peters") && !method.equals("Schwarzer") && !method.equals("Deeks")) { res.error = "Test for small-study effects not feasible as all studies have the same precision."; return res; }
        res.label = method.equals("Begg") || method.equals("Schwarzer") ? "Rank correlation test of funnel plot asymmetry" + (method.equals("Schwarzer") ? " of counts" : "")
                : method.equals("Deeks") ? "Funnel plot test for diagnostic odds ratios" : "Linear regression test of funnel plot asymmetry";
        if (method.equals("Begg")) {
            MetaSpec s2 = s.forAux(); s2.methodTau = "DL"; s2.methodTauCi = "";
            int[] all = new int[k]; for (int i = 0; i < k; i++) all[i] = i;
            Pool p = Pool.compute(s2, te, se, all, null);
            double[] ts = new double[k], v = new double[k];
            for (int i = 0; i < k; i++) { v[i] = se[i] * se[i]; ts[i] = (te[i] - p.teCommon) / Math.sqrt(v[i] - p.seCommon * p.seCommon); }
            double[] kt = kentau(ts, v, correct);
            res.statName = "z"; res.statistic = kt[0] / kt[1]; res.pval = Dist.pnormTwoSided(res.statistic); res.bias = kt[0]; res.seBias = kt[1];
            res.plotX = ts; res.plotY = v; res.plotXlab = "Standardised treatment effect"; res.plotYlab = "Estimated variance of treatment estimate";
            return res;
        }
        double[] cov = null; String predictor = "sei"; double[] y = te, sei = se; String model = "lm";
        switch (method) {
            case "Egger": break;
            case "Thompson": model = "rma"; break;
            case "Harbord": {
                if (!s.type.equals("bin")) { res.error = "Harbord test only available for binary data."; return res; }
                double[] ee = pick(d.get("event.e"), ok), ne = pick(d.get("n.e"), ok), ec = pick(d.get("event.c"), ok), nc = pick(d.get("n.c"), ok);
                y = new double[k]; sei = new double[k];
                for (int i = 0; i < k; i++) {
                    double Z, V;
                    if (s.sm.equals("RR")) { Z = (ee[i] * (ne[i] + nc[i]) - (ee[i] + ec[i]) * ne[i]) / (ne[i] - ee[i] + nc[i] - ec[i]); V = (ne[i] + nc[i]) / 4 * (ee[i] + ec[i]) / (ne[i] - ee[i] + nc[i] - ec[i]); }
                    else { Z = ee[i] - (ee[i] + ec[i]) * ne[i] / (ne[i] + nc[i]); V = ne[i] * nc[i] * (ee[i] + ec[i]) * (ne[i] - ee[i] + nc[i] - ec[i]) / (Math.pow(ne[i] + nc[i], 2) * (ne[i] + nc[i] - 1)); }
                    y[i] = Z / V; sei[i] = 1 / Math.sqrt(V);
                }
                break;
            }
            case "Macaskill": {
                cov = new double[k]; predictor = "ni";
                double[] a = d.get("n.e"), b = d.get("n.c"), n = d.get("n");
                for (int i = 0; i < k; i++) { int ix = ok.get(i); cov[i] = a != null && b != null ? a[ix] + b[ix] : n != null ? n[ix] : a != null ? a[ix] : b[ix]; }
                break;
            }
            case "Peters": {
                predictor = "ni"; cov = new double[k]; sei = new double[k];
                if (s.type.equals("bin")) { double[] ee = pick(d.get("event.e"), ok), ne = pick(d.get("n.e"), ok), ec = pick(d.get("event.c"), ok), nc = pick(d.get("n.c"), ok);
                    for (int i = 0; i < k; i++) { sei[i] = Math.sqrt(1 / (ee[i] + ec[i]) + 1 / (ne[i] - ee[i] + nc[i] - ec[i])); cov[i] = 1 / (ne[i] + nc[i]); } }
                else if (s.type.equals("prop")) { double[] ev = pick(d.get("event"), ok), n = pick(d.get("n"), ok); for (int i = 0; i < k; i++) { sei[i] = Math.sqrt(1 / ev[i] + 1 / (n[i] - ev[i])); cov[i] = 1 / n[i]; } }
                else { res.error = "Peters test only available for binary or proportion data."; return res; }
                break;
            }
            case "Deeks": {
                if (!s.type.equals("bin")) { res.error = "Deeks test only available for binary data."; return res; }
                double[] ne = pick(d.get("n.e"), ok), nc = pick(d.get("n.c"), ok); predictor = "ni"; cov = new double[k]; sei = new double[k];
                for (int i = 0; i < k; i++) { double ess = 4 * ne[i] * nc[i] / (ne[i] + nc[i]); sei[i] = Math.sqrt(1 / ess); cov[i] = 1 / Math.sqrt(ess); }
                break;
            }
            case "Pustejovsky": {
                double[] a = pick(d.get("n.e"), ok), b = pick(d.get("n.c"), ok);
                if (a == null || b == null) { res.error = "Sample sizes n.e and n.c required for Pustejovsky test."; return res; }
                cov = new double[k]; predictor = "ni"; for (int i = 0; i < k; i++) cov[i] = Math.sqrt(1 / a[i] + 1 / b[i]); break;
            }
            case "Schwarzer": {
                if (!s.type.equals("bin")) { res.error = "Schwarzer test only available for binary data."; return res; }
                return schwarzer(r, ok, correct, res);
            }
            default: res.error = "Unknown method: " + method; return res;
        }
        double[] x = predictor.equals("sei") ? sei : cov;
        double[][] X = new double[k][2]; double[] v = new double[k];
        for (int i = 0; i < k; i++) { X[i][0] = 1; X[i][1] = x[i]; v[i] = sei[i] * sei[i]; }
        double sigma, b1, seb1, b0, seb0; int dfr = k - 2;
        if (model.equals("lm")) {
            double[] w = new double[k]; for (int i = 0; i < k; i++) w[i] = 1 / v[i];
            double[][] xtwx = new double[2][2]; double[] xtwy = new double[2];
            for (int i = 0; i < k; i++) for (int a = 0; a < 2; a++) { xtwy[a] += X[i][a] * w[i] * y[i]; for (int b = 0; b < 2; b++) xtwx[a][b] += X[i][a] * w[i] * X[i][b]; }
            double[][] inv = Lin.inv(xtwx); double[] beta = Lin.mulv(inv, xtwy); double rss = 0;
            for (int i = 0; i < k; i++) { double f = beta[0] + beta[1] * x[i]; rss += w[i] * (y[i] - f) * (y[i] - f); }
            double s2 = rss / dfr; sigma = Math.sqrt(s2);
            b0 = beta[0]; b1 = beta[1]; seb0 = Math.sqrt(s2 * inv[0][0]); seb1 = Math.sqrt(s2 * inv[1][1]);
            res.varModel = "multiplicative";
        } else {
            int[] okIdx = new int[k]; for (int i = 0; i < k; i++) okIdx[i] = ok.get(i);
            Rma m = Rma.fit(y, v, X, s.methodTau.equals("PM") ? "PM" : s.methodTau, Pool.genqWeightsFor(s, okIdx));
            sigma = Math.sqrt(m.tau2); b0 = m.beta[0]; b1 = m.beta[1]; seb0 = m.se[0]; seb1 = m.se[1]; res.varModel = "additive"; res.methodTau = s.methodTau;
        }
        res.statName = "t"; res.statistic = b1 / seb1; res.df = (double) dfr; res.pval = Dist.ptTwoSided(res.statistic, dfr);
        res.bias = b1; res.seBias = seb1; res.tau = sigma; res.intercept = b0; res.seIntercept = seb0;
        if (method.equals("Egger") || method.equals("Thompson") || method.equals("Harbord")) {
            res.plotX = new double[k]; res.plotY = new double[k]; // radial: x = 1/se, y = TE/se
            for (int i = 0; i < k; i++) { res.plotX[i] = 1 / sei[i]; res.plotY[i] = y[i] / sei[i]; }
            res.plotXlab = "Inverse of standard error"; res.plotYlab = "Standardised treatment effect (z-score)";
        }
        return res;
    }

    /** For the self-test. */
    public static double[] kendallDistForTest(int n) { return Metafor.kendallDist(n); }

    static double[] pick(double[] a, List<Integer> ok) { if (a == null) return null; double[] r = new double[ok.size()]; for (int i = 0; i < r.length; i++) r[i] = a[ok.get(i)]; return r; }

    static Result schwarzer(MetaResult r, List<Integer> ok, boolean correct, Result res) {
        MetaSpec s = r.spec; MetaData d = r.data;
        MetaSpec s2 = s.clone(); s2.sm = "OR"; s2.method = "MH"; s2.methodTau = "DL"; s2.methodTauCi = "";
        Effects.Out eff = Effects.compute(s2, d);
        Pool.CommonFn fn = Meta.makeCommonFn(s2, d, eff);
        int[] all = new int[d.n]; for (int i = 0; i < d.n; i++) all[i] = i;
        Pool p = Pool.compute(s2, eff.TE, eff.seTE, all, fn);
        double psi = Math.exp(p.teCommon); int k = ok.size();
        double[] ee = pick(d.get("event.e"), ok), ne = pick(d.get("n.e"), ok), ec = pick(d.get("event.c"), ok), nc = pick(d.get("n.c"), ok);
        double[] x = new double[k], y = new double[k];
        for (int i = 0; i < k; i++) {
            double n1 = ne[i], n = ne[i] + nc[i], m1 = ee[i] + ec[i], n2 = n - n1;
            int lo = (int) Math.max(0, m1 - n2), hi = (int) Math.min(n1, m1);
            double[] lp = new double[hi - lo + 1]; double mx = -1e300;
            for (int a = lo; a <= hi; a++) { lp[a - lo] = Dist.lgamma(n1 + 1) - Dist.lgamma(a + 1) - Dist.lgamma(n1 - a + 1) + Dist.lgamma(n2 + 1) - Dist.lgamma(m1 - a + 1) - Dist.lgamma(n2 - m1 + a + 1) + a * Math.log(psi); mx = Math.max(mx, lp[a - lo]); }
            double sum = 0, m = 0, m2 = 0; for (double l : lp) sum += Math.exp(l - mx);
            for (int a = lo; a <= hi; a++) { double pr = Math.exp(lp[a - lo] - mx) / sum; m += a * pr; m2 += a * a * pr; }
            double var = m2 - m * m; x[i] = (ee[i] - m) / Math.sqrt(var); y[i] = 1 / var;
        }
        double[] kt = kentau(x, y, correct);
        res.statName = "z"; res.statistic = kt[0] / kt[1]; res.pval = Dist.pnormTwoSided(res.statistic); res.bias = kt[0]; res.seBias = kt[1];
        return res;
    }

    /** Kendall's tau as used in meta::kentau; returns {ks, se.ks}. */
    public static double[] kentau(double[] x, double[] y, boolean correct) {
        int n = x.length;
        double conc = 0; double ties = 0;
        for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) conc += Math.signum(x[i] - x[j]) * Math.signum(y[i] - y[j]);
        double[] t = tieCounts(x), u = tieCounts(y);
        double N = 0.5 * n * (n - 1), N1 = N, N2 = N; for (double a : t) N1 -= a * (a - 1) / 2; for (double a : u) N2 -= a * (a - 1) / 2;
        double tauB = conc / (Math.sqrt(N1) * Math.sqrt(N2));
        double ks = conc;
        double st = 0, su = 0, st3 = 0, su3 = 0, st1 = 0, su1 = 0;
        for (double a : t) { st += a * (a - 1) * (2 * a + 5); st3 += a * (a - 1) * (a - 2); st1 += a * (a - 1); }
        for (double a : u) { su += a * (a - 1) * (2 * a + 5); su3 += a * (a - 1) * (a - 2); su1 += a * (a - 1); }
        double se = Math.sqrt(1.0 / 18 * (n * (n - 1.0) * (2 * n + 5) - st - su) + 1.0 / (9.0 * n * (n - 1) * (n - 2)) * st3 * su3 + 1.0 / (2.0 * n * (n - 1)) * st1 * su1);
        if (correct) ks -= Math.signum(ks);
        return new double[]{ks, se};
    }

    static double[] tieCounts(double[] x) {
        double[] s = x.clone(); Arrays.sort(s); List<Double> c = new ArrayList<>(); int run = 1;
        for (int i = 1; i <= s.length; i++) { if (i < s.length && s[i] == s[i - 1]) run++; else { c.add((double) run); run = 1; } }
        return c.stream().mapToDouble(Double::doubleValue).toArray();
    }
}
