package org.metajava.core;

import java.util.*;

/**
 * Tests for funnel plot asymmetry as implemented in the metafor package: regtest() (all the regression versions that have a counterpart in the
 * meta suite) and ranktest() (Begg and Mazumdar, with the exact Kendall p-value that cor.test(..., method = "kendall", exact = TRUE) gives).
 * Called from {@link Bias#run} for the "(metafor)" entries of the "Test method" list.
 */
final class Metafor {
    private Metafor() {}

    static Bias.Result run(MetaResult r, String method, boolean correct, Bias.Result res, List<Integer> ok, double[] te, double[] se) {
        MetaSpec s = r.spec; MetaData d = r.data; int k = te.length;
        double[] v = new double[k]; for (int i = 0; i < k; i++) v[i] = se[i] * se[i];
        if (method.equals("Begg")) return ranktest(res, te, v);

        res.label = "Regression test for funnel plot asymmetry (metafor::regtest)";
        String model = "lm", predictor = "sei"; double[] ni = null;
        switch (method) {
            case "Egger": break;
            case "Thompson": model = "rma"; break;
            case "Macaskill": predictor = "ni"; ni = totalN(d, ok); if (ni == null) return noN(res); break;
            case "Peters": predictor = "ninv"; ni = totalN(d, ok); if (ni == null) return noN(res); break;
            case "Deeks": {
                if (!s.type.equals("bin")) { res.error = "Deeks test only available for binary data."; return res; }
                double[] ne = Bias.pick(d.get("n.e"), ok), nc = Bias.pick(d.get("n.c"), ok); predictor = "sqrtninv"; ni = new double[k];
                for (int i = 0; i < k; i++) ni[i] = 4 * ne[i] * nc[i] / (ne[i] + nc[i]);   // effective sample size (Deeks et al., 2005)
                break;
            }
            case "Pustejovsky": {
                double[] a = Bias.pick(d.get("n.e"), ok), b = Bias.pick(d.get("n.c"), ok);
                if (a == null || b == null) { res.error = "Sample sizes n.e and n.c required for Pustejovsky test."; return res; }
                predictor = "sqrtninv"; ni = new double[k];
                for (int i = 0; i < k; i++) ni[i] = a[i] * b[i] / (a[i] + b[i]);   // 1/sqrt(ni) = sqrt(1/n.e + 1/n.c)
                break;
            }
            default: res.error = "Unknown method: " + method; return res;
        }
        double[] x = new double[k];
        for (int i = 0; i < k; i++) x[i] = predictor.equals("sei") ? se[i] : predictor.equals("ni") ? ni[i] : predictor.equals("ninv") ? 1 / ni[i] : 1 / Math.sqrt(ni[i]);
        double mx = 0, mn = Double.MAX_VALUE; for (double q : x) { mx = Math.max(mx, Math.abs(q)); mn = Math.min(mn, Math.abs(q)); }
        double xmin = Arrays.stream(x).min().getAsDouble(), xmax = Arrays.stream(x).max().getAsDouble();
        if (!(xmax - xmin > 1e-12 * Math.max(1, mx))) { res.error = "Model matrix no longer of full rank after addition of predictor. Cannot fit model."; return res; }

        double[][] X = new double[k][2]; for (int i = 0; i < k; i++) { X[i][0] = 1; X[i][1] = x[i]; }
        double b0, b1, se0, se1, sigma; int dfr = k - 2; String stat; Double df = null; double pval, zt; double lvl = s.level; double crit; double[] vbq; double psig2 = Double.NaN, ptau2 = 0;
        res.model = model; res.predictor = predictor;
        if (model.equals("lm")) {
            double[] w = new double[k]; for (int i = 0; i < k; i++) w[i] = 1 / v[i];
            double[][] xtwx = new double[2][2]; double[] xtwy = new double[2];
            for (int i = 0; i < k; i++) for (int a = 0; a < 2; a++) { xtwy[a] += X[i][a] * w[i] * te[i]; for (int b = 0; b < 2; b++) xtwx[a][b] += X[i][a] * w[i] * X[i][b]; }
            double[][] inv = Lin.inv(xtwx); if (inv == null) { res.error = "Model matrix no longer of full rank after addition of predictor. Cannot fit model."; return res; }
            double[] beta = Lin.mulv(inv, xtwy); double rss = 0;
            for (int i = 0; i < k; i++) { double f = beta[0] + beta[1] * x[i]; rss += w[i] * (te[i] - f) * (te[i] - f); }
            double s2 = rss / dfr; sigma = Math.sqrt(s2);
            b0 = beta[0]; b1 = beta[1]; se0 = Math.sqrt(s2 * inv[0][0]); se1 = Math.sqrt(s2 * inv[1][1]);
            stat = "t"; df = (double) dfr; zt = b1 / se1; pval = Dist.ptTwoSided(zt, dfr); crit = Dist.qt(1 - (1 - lvl) / 2, dfr);
            res.varModel = "multiplicative"; res.testKind = "t"; vbq = new double[]{s2 * inv[0][0], s2 * inv[0][1], s2 * inv[1][1]}; psig2 = s2;
        } else {
            int[] okIdx = new int[k]; for (int i = 0; i < k; i++) okIdx[i] = ok.get(i);
            // metafor: the model of regtest() is fitted with the method of the analysed model: a common-effect analysis (method = "FE") gives a fixed-effects meta-regression, tau^2 = 0 (Wald z-test, or Knapp-Hartung t with metafor's warning when HK is selected)
            final boolean fe = !s.random; final String meth = fe ? "FE" : s.methodTau;
            Rma m = Rma.fit(te, v, X, meth, fe ? null : Pool.genqWeightsFor(s, okIdx));
            sigma = Math.sqrt(m.tau2); b0 = m.beta[0]; b1 = m.beta[1]; se0 = m.se[0]; se1 = m.se[1];
            res.varModel = fe ? "fixed" : "additive"; res.methodTau = meth; vbq = new double[]{m.vb[0][0], m.vb[0][1], m.vb[1][1]}; ptau2 = m.tau2;
            if (s.methodRandomCi.equals("HK")) {   // metafor: test = "knha", or "adhoc" (scale factor at least 1) for the ad hoc option "se"; meta's ad hoc "ci" has no metafor counterpart
                double[] bw = m.betaW(); double rss = 0;
                for (int i = 0; i < k; i++) { double f = bw[0] + bw[1] * x[i]; rss += m.wTilde[i] * (te[i] - f) * (te[i] - f); }
                final boolean adhoc = s.adhocHakn.equals("se"); double s2 = rss <= 2.220446049250313e-16 ? 0 : rss / dfr; if (adhoc) s2 = Math.max(1, s2);   // metafor: s2w = 0 when RSS.knha is at machine epsilon (all yi equal), then the ad hoc floor
                se0 = Math.sqrt(s2 * m.vb[0][0]); se1 = Math.sqrt(s2 * m.vb[1][1]); vbq = new double[]{s2 * m.vb[0][0], s2 * m.vb[0][1], s2 * m.vb[1][1]};
                stat = "t"; df = (double) dfr; zt = b1 / se1; pval = Dist.ptTwoSided(zt, dfr); crit = Dist.qt(1 - (1 - lvl) / 2, dfr);
                res.testKind = adhoc ? "adhoc" : "knha";
                if (fe) res.warning = "Knapp and Hartung method is not meant to be used in the context of FE models.";   // metafor: warning of rma.uni
                if (!adhoc && !s.adhocHakn.isEmpty()) res.hkNote = "The ad hoc option \"" + s.adhocHakn + "\" has no metafor counterpart: metafor's test = \"adhoc\" is the ad hoc option \"se\"; it is not applied to this regression, so the plain Knapp-Hartung test (test = \"knha\") was used.";
            } else { stat = "z"; zt = b1 / se1; pval = Dist.pnormTwoSided(zt); crit = Dist.qnorm(1 - (1 - lvl) / 2); res.testKind = "z"; }
        }
        res.statName = stat; res.statistic = zt; res.df = df; res.pval = pval;
        res.bias = b1; res.seBias = se1; res.tau = sigma; res.intercept = b0; res.seIntercept = se0;
        if (!predictor.equals("ni")) { res.limitEst = b0; res.limitLo = b0 - crit * se0; res.limitHi = b0 + crit * se0; }   // 'limit estimate' (metafor: est, ci.lb, ci.ub)
        if (predictor.equals("sei")) {   // radial form of the same regression: x = 1/se, y = TE/se
            res.plotX = new double[k]; res.plotY = new double[k];
            for (int i = 0; i < k; i++) { res.plotX[i] = 1 / se[i]; res.plotY[i] = te[i] / se[i]; }
            res.plotXlab = "Inverse of standard error"; res.plotYlab = "Standardised treatment effect (z-score)";
            res.fitVb = vbq; res.fitCrit = df != null ? Dist.qt(0.975, dfr) : Dist.qnorm(0.975); res.piSigma2 = psig2; res.piTau2 = ptau2;
        }
        return res;
    }

    private static Bias.Result noN(Bias.Result res) { res.error = "No sample size information available to use this predictor."; return res; }

    /** Total sample size per study (n.e + n.c, or n), or null when the data have none. */
    private static double[] totalN(MetaData d, List<Integer> ok) {
        double[] a = d.get("n.e"), b = d.get("n.c"), n = d.get("n"); int k = ok.size(); double[] out = new double[k];
        for (int i = 0; i < k; i++) { int ix = ok.get(i); double v = a != null && b != null ? a[ix] + b[ix] : n != null ? n[ix] : Double.NaN; if (Double.isNaN(v) || v <= 0) return null; out[i] = v; }
        return out;
    }

    /** metafor::ranktest: Kendall's tau between the standardised effects (around the fixed-effect estimate) and the sampling variances. */
    private static Bias.Result ranktest(Bias.Result res, double[] te, double[] v) {
        int k = te.length; res.label = "Rank correlation test of funnel plot asymmetry (metafor::ranktest)";
        double sw = 0, swy = 0; for (int i = 0; i < k; i++) { sw += 1 / v[i]; swy += te[i] / v[i]; }
        double theta = swy / sw, vb = 1 / sw; double[] ys = new double[k];
        for (int i = 0; i < k; i++) ys[i] = (te[i] - theta) / Math.sqrt(v[i] - vb);
        double[] kt = Bias.kentau(ys, v, false); double S = kt[0], sdS = kt[1];
        double[] t = Bias.tieCounts(ys), u = Bias.tieCounts(v); double N = 0.5 * k * (k - 1), N1 = N, N2 = N;
        for (double a : t) N1 -= a * (a - 1) / 2; for (double a : u) N2 -= a * (a - 1) / 2;
        res.ties = t.length < k || u.length < k;
        double tau = S / Math.sqrt(N1 * N2); res.kendallS = S;
        double p;
        if (!res.ties) {   // cor.test(exact = TRUE) without ties: exact null distribution of the number of concordant pairs
            res.exactP = true; res.testKind = "exact";
            double[] pr = kendallDist(k); double cut = Math.rint((tau + 1) * k * (k - 1) / 4.0); double nn = k * (k - 1) / 4.0;
            double cum = 0; int qi = (int) cut; double lower;
            if (cut > nn) { for (int j = 0; j <= qi - 1 && j < pr.length; j++) cum += pr[j]; lower = 1 - cum; } else { for (int j = 0; j <= qi && j < pr.length; j++) cum += pr[j]; lower = cum; }
            p = Math.min(2 * lower, 1);
        } else { res.testKind = "normal"; p = Dist.pnormTwoSided(S / sdS); }   // ties: normal approximation with tie correction (R warns that the exact p-value cannot be computed)
        res.statName = "tau"; res.statistic = tau; res.pval = p; res.bias = S; res.seBias = sdS;
        res.plotX = ys; res.plotY = v; res.plotXlab = "Standardised treatment effect"; res.plotYlab = "Estimated variance of treatment estimate";
        return res;
    }

    /** P(Q = q), q = 0..n(n-1)/2, for the number of discordant pairs of a random permutation of n items (null distribution of Kendall's statistic). */
    static double[] kendallDist(int n) {
        double[] p = {1};
        for (int m = 2; m <= n; m++) {
            int up = p.length - 1, u = m * (m - 1) / 2; double[] cum = new double[p.length]; double c = 0;
            for (int j = 0; j < p.length; j++) { c += p[j]; cum[j] = c; }
            double[] q = new double[u + 1];
            for (int j = 0; j <= u; j++) { double hi = cum[Math.min(j, up)], lo = j - m >= 0 ? cum[j - m] : 0; q[j] = (hi - lo) / m; }
            p = q;
        }
        return p;
    }
}
