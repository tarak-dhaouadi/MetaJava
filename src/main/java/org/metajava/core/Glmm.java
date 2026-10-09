package org.metajava.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleUnaryOperator;
import java.util.function.ToDoubleFunction;

/**
 * Generalised linear mixed models for meta-analysis as fitted by metafor::rma.glmm(method = "ML") and used by meta (method = "GLMM").
 * Single proportions / rates (PLO, IRLN): random-intercept logistic / Poisson model.
 * Two-group data (OR, IRR), with random effect u_i ~ N(0, tau^2) on the group difference (coded +1/2, -1/2):
 *   UM.FS  fixed study effects          logit p_i1 = a_i + theta + u_i/2,      logit p_i2 = a_i - u_i/2
 *   UM.RS  random study effects         logit p_i1 = a + theta + b_i + u_i/2,  logit p_i2 = a + b_i - u_i/2,  b_i ~ N(0, sigma^2), uncorrelated (cor = FALSE)
 *   CM.AL  conditional, approximate     a_i1 | a_i1 + a_i2 ~ Bin(a_i1 + a_i2, expit(theta + u_i + log(n_i1 / n_i2)))
 *   CM.EL  conditional, exact           OR: Fisher's non-central hypergeometric with log odds ratio theta + u_i; IRR: as CM.AL on person-time
 * Marginal likelihoods: adaptive Gauss-Hermite quadrature with nAGQ nodes (as lme4::glmer; 1 node = Laplace approximation).
 * UM.RS has two random effects per study and is evaluated by the Laplace approximation (lme4 allows nAGQ = 1 only for this model).
 */
public final class Glmm {
    private Glmm() {}

    public static final class Data {
        public final String kind;                 // PLO, IRLN, OR, IRR
        public final String model;                // UM.FS, UM.RS, CM.EL, CM.AL (IRR + CM.AL is fitted as CM.EL, as in metafor)
        public final int nAGQ;
        public final double[] x1, s1, x2, s2;     // events and size (n or person-time) of group 1 / group 2
        public final int n;
        public Data(String kind, String model, int nAGQ, double[] x1, double[] s1, double[] x2, double[] s2) {
            this.kind = kind; this.nAGQ = Math.max(1, nAGQ); this.x1 = x1; this.s1 = s1; this.x2 = x2; this.s2 = s2; this.n = x1.length;
            this.model = kind.equals("IRR") && model.equals("CM.AL") ? "CM.EL" : model;
        }
        public Data(String kind, double[] x1, double[] s1, double[] x2, double[] s2) { this(kind, "UM.FS", 7, x1, s1, x2, s2); }
        boolean twoGroup() { return kind.equals("OR") || kind.equals("IRR"); }
        boolean pois() { return kind.equals("IRLN") || kind.equals("IRR"); }
        boolean fs() { return twoGroup() && model.equals("UM.FS"); }
        boolean rs() { return twoGroup() && model.equals("UM.RS"); }
        boolean cm() { return twoGroup() && model.startsWith("CM"); }
    }

    public static final class Result {
        public double est = Double.NaN, se = Double.NaN, tau2 = Double.NaN, seTau2 = Double.NaN, sigma2 = Double.NaN, logLik = Double.NaN;
        public double qWald = Double.NaN, qLRT = Double.NaN, llSat = Double.NaN; public int dfQ; public int k; public boolean ok = true; public String note = "";
    }

    // ================================================================ observations
    /** One observation: binomial (0), Poisson (1) or Fisher's non-central hypergeometric (2); linear predictor = base + coef * u (+ coefA * a for UM.RS). */
    private static final class Arm {
        final int kind; final double x, size, coef, coefA; double[] c, ed; int lo; double xIdx; double lc = Double.NaN;   // lc: cached log binomial coefficient (binomial arms)   // ed[j] = pmf weight ratio c[j+1]/c[j] on the exp scale
        Arm(int kind, double x, double size, double coef, double coefA) { this.kind = kind; this.x = x; this.size = size; this.coef = coef; this.coefA = coefA; }
        /** non-central hypergeometric: x events in group 1 (n1 = size) among m = x + y events, n2 = size2. */
        static Arm nch(double x, double n1, double n2, double m) {
            Arm a = new Arm(2, x, n1, 1, 0); a.lo = (int) Math.max(0, m - n2); int hi = (int) Math.min(n1, m); a.c = new double[hi - a.lo + 1];
            for (int j = a.lo; j <= hi; j++) a.c[j - a.lo] = lchoose(n1, j) + lchoose(n2, m - j); a.xIdx = x - a.lo;
            a.ed = new double[Math.max(0, a.c.length - 1)]; for (int i = 0; i < a.ed.length; i++) { double j = a.lo + i; a.ed[i] = (n1 - j) * (m - j) / ((j + 1) * (n2 - m + j + 1)); }   // C(n1,j+1)C(n2,m-j-1) / (C(n1,j)C(n2,m-j)), exact
            return a;
        }
    }

    /** log(1 + z) for 0 <= z <= 1 to a few ulp, through the (intrinsic, fast) Math.log; Math.log1p is a slow software routine and dominated the UM.RS fits. */
    private static double log1pFast(double z) { double u = 1.0 + z; return u == 1.0 ? z : Math.log(u) * (z / (u - 1.0)); }
    private static double log1pexp(double a) { return a > 0 ? a + log1pFast(Math.exp(-a)) : log1pFast(Math.exp(a)); }
    private static double expit(double a) { return a >= 0 ? 1 / (1 + Math.exp(-a)) : Math.exp(a) / (1 + Math.exp(a)); }
    private static double lchoose(double n, double x) { return Dist.lgamma(n + 1) - Dist.lgamma(x + 1) - Dist.lgamma(n - x + 1); }

    /** log pmf of an observation at linear predictor eta: out = {ll, score, information}; with out.length > 3 (binomial, Poisson) also out[3] = d(information)/d(eta). */
    private static void armEval(Arm a, double eta, double[] out) {
        if (a.kind == 1) { double lam = a.size * Math.exp(eta); out[0] = a.x * (eta + Math.log(a.size)) - lam - Dist.lgamma(a.x + 1); out[1] = a.x - lam; out[2] = lam; if (out.length > 3) out[3] = lam; }
        else if (a.kind == 0) {   // binomial: one exp serves both the probability and log(1 + exp(eta)); the log binomial coefficient is constant and cached
            double lc = a.lc; if (lc != lc) a.lc = lc = lchoose(a.size, a.x);
            double e = Math.exp(-Math.abs(eta)), l1 = log1pFast(e), p, lse;
            if (eta >= 0) { p = 1 / (1 + e); lse = eta + l1; } else { p = e / (1 + e); lse = l1; }
            out[0] = a.x * eta - a.size * lse + lc; out[1] = a.x - a.size * p; out[2] = a.size * p * (1 - p); if (out.length > 3) out[3] = out[2] * (1 - 2 * p);
        }
        else {
            // non-central hypergeometric: f(j) = c[j] + (lo + j) * eta is concave in j, so its maximum is found by bisection and only the terms
            // within exp(-45) of it (a window of a few standard deviations, instead of all min(n1, n2) + 1 terms) are summed
            final int m = a.c.length; final double[] c = a.c; int l = 0, h = m - 1;
            while (l < h) { int mid = (l + h) >>> 1; if (c[mid + 1] - c[mid] + eta > 0) l = mid + 1; else h = mid; }
            final double mx = c[l] + (a.lo + l) * eta; int j0 = l, j1 = l;
            if (!(Math.abs(eta) <= 300)) { while (j0 > 0 && c[j0 - 1] + (a.lo + j0 - 1) * eta > mx - 45) j0--; while (j1 < m - 1 && c[j1 + 1] + (a.lo + j1 + 1) * eta > mx - 45) j1++; }
            double z = 0, e1 = 0, e2 = 0;
            if (Math.abs(eta) <= 300) {   // weights relative to the mode by the recurrence w[j+1] = w[j] * ed[j] * exp(eta): no exp per term
                final double q = Math.exp(eta), vl = a.lo + l; z = 1; e1 = vl; e2 = vl * vl; double w = 1;
                for (int j = l; j < m - 1; j++) { w *= a.ed[j] * q; if (w < 3e-20) break; double v = a.lo + j + 1; z += w; e1 += w * v; e2 += w * v * v; }
                w = 1; for (int j = l; j > 0; j--) { w /= a.ed[j - 1] * q; if (w < 3e-20) break; double v = a.lo + j - 1; z += w; e1 += w * v; e2 += w * v * v; }
            } else { for (int j = j0; j <= j1; j++) { double w = Math.exp(c[j] + (a.lo + j) * eta - mx), v = a.lo + j; z += w; e1 += w * v; e2 += w * v * v; } }
            e1 /= z; e2 /= z; out[0] = a.c[(int) a.xIdx] + a.x * eta - (mx + Math.log(z)); out[1] = a.x - e1; out[2] = Math.max(e2 - e1 * e1, 1e-300);
        }
    }

    /** Conditional log-likelihood in the random effect u (loading coef): {ll, d1, d2}. */
    private static void condEval(Arm[] arms, double[] base, double u, double[] out) {
        double ll = 0, d1 = 0, d2 = 0; double[] t = new double[3];
        for (int j = 0; j < arms.length; j++) { armEval(arms[j], base[j] + arms[j].coef * u, t); ll += t[0]; d1 += arms[j].coef * t[1]; d2 -= arms[j].coef * arms[j].coef * t[2]; }
        out[0] = ll; out[1] = d1; out[2] = d2;
    }

    // ================================================================ Gauss-Hermite
    private static final Map<Integer, double[][]> GH = new ConcurrentHashMap<>();
    private static double[][] gh(int n) {   // lock-free: called for every study at every likelihood evaluation, from several threads
        double[][] r = GH.get(n); if (r != null) return r;
        double[] x = new double[n], w = new double[n];
        if (n == 1) { x[0] = 0; w[0] = Math.sqrt(Math.PI); }
        else {   // physicists' Gauss-Hermite (weight exp(-x^2)), Numerical Recipes gauher
            int m = (n + 1) / 2; double z = 0, z1, p1, p2, p3, pp = 0;
            for (int i = 0; i < m; i++) {
                if (i == 0) z = Math.sqrt(2.0 * n + 1) - 1.85575 * Math.pow(2.0 * n + 1, -0.16667);
                else if (i == 1) z -= 1.14 * Math.pow(n, 0.426) / z;
                else if (i == 2) z = 1.86 * z - 0.86 * x[0];
                else if (i == 3) z = 1.91 * z - 0.91 * x[1];
                else z = 2.0 * z - x[i - 2];
                for (int its = 0; its < 100; its++) {
                    p1 = Math.pow(Math.PI, -0.25); p2 = 0;
                    for (int j = 0; j < n; j++) { p3 = p2; p2 = p1; p1 = z * Math.sqrt(2.0 / (j + 1)) * p2 - Math.sqrt((double) j / (j + 1)) * p3; }
                    pp = Math.sqrt(2.0 * n) * p2; z1 = z; z = z1 - p1 / pp; if (Math.abs(z - z1) < 1e-14) break;
                }
                x[i] = z; x[n - 1 - i] = -z; w[i] = 2.0 / (pp * pp); w[n - 1 - i] = w[i];
            }
            if (n % 2 == 1) x[n / 2] = 0;
        }
        r = new double[][]{x, w}; double[][] prev = GH.putIfAbsent(n, r); return prev != null ? prev : r;   // a concurrent duplicate computation gives identical values
    }

    /** log of  integral exp(ll(u)) N(u; 0, tau^2) du  by adaptive Gauss-Hermite quadrature with nodes points (nodes = 1: Laplace). tau <= 0 gives ll(0). */
    private static double logMarginal(Arm[] arms, double[] base, double tau, int nodes) {
        double[] t = new double[3];
        if (tau <= 0) { condEval(arms, base, 0, t); return t[0]; }
        double tau2 = tau * tau, u = 0;
        for (int it = 0; it < 100; it++) {   // damped Newton for the mode of h(u) = ll(u) - u^2/(2 tau^2)
            condEval(arms, base, u, t); double g = t[1] - u / tau2, H = t[2] - 1 / tau2; double step = -g / H;
            if (step > 3) step = 3; else if (step < -3) step = -3; u += step; if (Math.abs(step) < 1e-11) break;
        }
        condEval(arms, base, u, t); double H = t[2] - 1 / tau2, sig = 1 / Math.sqrt(-H);
        double[][] g = gh(nodes); double[] terms = new double[nodes]; double mx = Double.NEGATIVE_INFINITY;
        for (int j = 0; j < nodes; j++) { double uj = u + 1.4142135623730951 * sig * g[0][j]; condEval(arms, base, uj, t); terms[j] = Math.log(g[1][j]) + g[0][j] * g[0][j] + t[0] - uj * uj / (2 * tau2); mx = Math.max(mx, terms[j]); }
        double s = 0; for (double x : terms) s += Math.exp(x - mx);
        return mx + Math.log(s) + 0.5 * Math.log(2) + Math.log(sig) - Math.log(tau) - 0.5 * Math.log(2 * Math.PI);
    }

    /** Laplace approximation for two independent random effects (a: loading coefA, u: loading coef) with SDs sigma and tau (> 0). */
    private static double logMarginal2(Arm[] arms, double[] base, double sigma, double tau) {
        double s2 = sigma * sigma, t2 = tau * tau, a = 0, u = 0; double[] t = new double[3];
        java.util.function.Function<double[], double[]> hfun = r -> {   // returns {h, ga, gu, haa, hau, huu}
            double ll = 0, ga = 0, gu = 0, haa = 0, hau = 0, huu = 0;
            for (int q = 0; q < arms.length; q++) { Arm m = arms[q]; armEval(m, base[q] + m.coefA * r[0] + m.coef * r[1], t); ll += t[0]; ga += m.coefA * t[1]; gu += m.coef * t[1]; haa -= m.coefA * m.coefA * t[2]; hau -= m.coefA * m.coef * t[2]; huu -= m.coef * m.coef * t[2]; }
            return new double[]{ll - r[0] * r[0] / (2 * s2) - r[1] * r[1] / (2 * t2), ga - r[0] / s2, gu - r[1] / t2, haa - 1 / s2, hau, huu - 1 / t2};
        };
        double[] cur = hfun.apply(new double[]{a, u});
        for (int it = 0; it < 200; it++) {
            double det = cur[3] * cur[5] - cur[4] * cur[4]; double da = -(cur[5] * cur[1] - cur[4] * cur[2]) / det, du = -(-cur[4] * cur[1] + cur[3] * cur[2]) / det;
            double mxs = Math.max(Math.abs(da), Math.abs(du)); if (mxs > 3) { da *= 3 / mxs; du *= 3 / mxs; }
            double f = 1; double[] nx = null; for (int h = 0; h < 30; h++) { nx = hfun.apply(new double[]{a + f * da, u + f * du}); if (nx[0] >= cur[0] - 1e-12) break; f *= 0.5; }
            a += f * da; u += f * du; cur = nx; if (Math.abs(f * da) < 1e-10 && Math.abs(f * du) < 1e-10) break;
        }
        double det = cur[3] * cur[5] - cur[4] * cur[4];
        return cur[0] - Math.log(sigma) - Math.log(tau) - 0.5 * Math.log(det);
    }

    // ================================================================ one-parameter profiles by Newton / secant steps (UM.FS intercept, UM.RS saturated study effect)
    /** Per-arm derivative of the linear predictor with respect to the profiled parameter: UM.FS intercept (both arms) and UM.RS saturated study effect (group 1 only). */
    private static final double[] DL_ALPHA = {1, 1}, DL_THETA = {1, 0};

    /** Conditional log-likelihood of one study and its derivatives at the profiled parameter p and random effect u (linear predictors dl[q] p + b[q] + coef[q] u):
     *  g = {L, Lu, Luu, Luuu, Lp, Lup, Luup, Lpp}; t is scratch of length 4. */
    private static void fsAgg(Arm[] arms, double[] b, double[] dl, double p, double u, double[] g, double[] t) {
        double L = 0, Lu = 0, Luu = 0, Luuu = 0, La = 0, Lua = 0, Luua = 0, Laa = 0;
        for (int q = 0; q < arms.length; q++) {
            Arm m = arms[q]; final double c = m.coef, d = dl[q]; armEval(m, d * p + b[q] + c * u, t);
            L += t[0]; La += d * t[1]; Laa -= d * d * t[2]; Lu += c * t[1]; Luu -= c * c * t[2]; Lua -= c * d * t[2]; Luua -= c * c * d * t[3]; Luuu -= c * c * c * t[3];
        }
        g[0] = L; g[1] = Lu; g[2] = Luu; g[3] = Luuu; g[4] = La; g[5] = Lua; g[6] = Luua; g[7] = Laa;
    }

    /** Log marginal likelihood of one study at profiled parameter p, exactly as logMarginal computes it (linear predictors dl[q] p + b[q] + coef[q] u), together with its
     *  analytic derivative in p (differentiating the mode, the Laplace scale and every quadrature node) and an estimate of the curvature.
     *  out = {logMarginal, d/dp, approximate d2/dp2, d(mode)/dp}; st[0] is the mode of u (warm start on entry, updated on exit). */
    private static void fsMarginal(Arm[] arms, double[] b, double[] dl, double alpha, double tau, int nodes, double[] st, double[] out) {
        double[] t = new double[4], g = new double[8];
        if (tau <= 0) { fsAgg(arms, b, dl, alpha, 0, g, t); out[0] = g[0]; out[1] = g[4]; out[2] = g[7]; out[3] = 0; return; }
        final double tau2 = tau * tau; double u = st[0];
        for (int it = 0; it < 100; it++) {   // damped Newton for the mode of h(u) = L(u) - u^2/(2 tau^2), started from the previous mode
            fsAgg(arms, b, dl, alpha, u, g, t); double gr = g[1] - u / tau2, H = g[2] - 1 / tau2, step = -gr / H;
            if (step > 3) step = 3; else if (step < -3) step = -3; u += step; if (Math.abs(step) < 1e-11) break;
        }
        st[0] = u; fsAgg(arms, b, dl, alpha, u, g, t);
        final double H = g[2] - 1 / tau2, sig = 1 / Math.sqrt(-H);
        final double m = -g[5] / H;                         // du*/dp
        final double dH = g[6] + g[3] * m, dsig = 0.5 * sig * sig * sig * dH;   // dH/dp and dsigma/dp, sigma = (-H)^(-1/2)
        final double[][] gh = gh(nodes); final double[] terms = new double[nodes], dterm = new double[nodes], lac = new double[nodes]; double mx = Double.NEGATIVE_INFINITY;
        for (int j = 0; j < nodes; j++) {
            final double x = gh[0][j], uj = u + 1.4142135623730951 * sig * x; fsAgg(arms, b, dl, alpha, uj, g, t);
            terms[j] = Math.log(gh[1][j]) + x * x + g[0] - uj * uj / (2 * tau2); mx = Math.max(mx, terms[j]);
            final double duj = m + 1.4142135623730951 * x * dsig;
            dterm[j] = g[4] + (g[1] - uj / tau2) * duj; lac[j] = g[7] + g[5] * m;
        }
        double s = 0, d1 = 0, e2 = 0; for (int j = 0; j < nodes; j++) { double w = Math.exp(terms[j] - mx); s += w; d1 += w * dterm[j]; e2 += w * lac[j]; }
        d1 /= s; e2 /= s; double var = 0; for (int j = 0; j < nodes; j++) { double w = Math.exp(terms[j] - mx) / s, dv = dterm[j] - d1; var += w * dv * dv; }
        out[0] = mx + Math.log(s) + 0.5 * Math.log(2) + Math.log(sig) - Math.log(tau) - 0.5 * Math.log(2 * Math.PI);
        out[1] = d1 + dsig / sig; out[2] = e2 + var; out[3] = m;
    }

    /** Maximises the log marginal likelihood of one study over a single parameter p confined to centre +/- 15 (the UM.FS intercept, or the UM.RS saturated study effect):
     *  safeguarded Newton steps on the analytic score (curvature from the secant of the last two scores once available), bracketed by the sign of the score, started from the
     *  previous optimum of this study (hot = {p, u}; hot[0] may be pre-seeded). The marginal log-likelihood is concave in p, so this typically converges in 1-5 evaluations
     *  instead of about 40 for a Brent search. An optimum on the boundary (a study without a finite maximum, e.g. zero events in a group) is returned at the boundary.
     *  Falls back to the Brent search over centre +/- 15 if anything is not finite or the iteration does not converge. Returns the maximised log marginal likelihood. */
    private static double fsOptimise(Arm[] arms, double[] b, double[] dl, double tau, int nodes, double centre, double[] hot) {
        final double lb = centre - 15, ub = centre + 15; final double[] out = new double[4], st = {Double.isNaN(hot[1]) ? 0 : hot[1]};
        double a = Double.isNaN(hot[0]) ? centre : Math.max(lb, Math.min(ub, hot[0]));
        double lo = Double.NEGATIVE_INFINITY, hi = Double.POSITIVE_INFINITY, aPrev = Double.NaN, gPrev = Double.NaN;
        for (int it = 0; it < 60; it++) {
            fsMarginal(arms, b, dl, a, tau, nodes, st, out);
            final double f = out[0], g = out[1];
            if (Double.isNaN(f) || Double.isInfinite(f) || Double.isNaN(g) || Double.isInfinite(g)) break;
            if ((a <= lb && g < 0) || (a >= ub && g > 0)) { hot[0] = a; hot[1] = st[0]; return f; }   // maximum on the boundary of the search interval
            if (g > 0) lo = Math.max(lo, a); else if (g < 0) hi = Math.min(hi, a);
            double slope = out[2];
            if (!Double.isNaN(aPrev) && Math.abs(a - aPrev) > 1e-7) { double sl = (g - gPrev) / (a - aPrev); if (sl < 0 && !Double.isInfinite(sl)) slope = sl; }
            double step = slope < 0 ? -g / slope : (g > 0 ? 1 : -1);
            if (step > 5) step = 5; else if (step < -5) step = -5;
            if (Math.abs(step) < 1e-10 || 0.5 * Math.abs(g * step) < 1e-18) { hot[0] = a; hot[1] = st[0]; return f; }
            double an = a + step;
            if (lo > Double.NEGATIVE_INFINITY && hi < Double.POSITIVE_INFINITY) { if (!(lo < hi)) break; if (an <= lo || an >= hi) an = 0.5 * (lo + hi); }
            if (Double.isNaN(an)) break;
            an = Math.max(lb, Math.min(ub, an));
            st[0] += out[3] * (an - a); aPrev = a; gPrev = g; a = an;
        }
        double best = brentMin(al -> -logMarginal(arms, new double[]{dl[0] * al + b[0], dl[1] * al + b[1]}, tau, nodes), lb, ub, 1e-10);
        hot[0] = best; hot[1] = Double.NaN; return logMarginal(arms, new double[]{dl[0] * best + b[0], dl[1] * best + b[1]}, tau, nodes);
    }

    // ================================================================ optimisation helpers
    static double brentMin(DoubleUnaryOperator f, double a, double b, double tol) {
        final double CG = 0.3819660112501051; double x = a + CG * (b - a), w = x, v = x, fx = f.applyAsDouble(x), fw = fx, fv = fx, d = 0, e = 0;
        for (int it = 0; it < 200; it++) {
            double xm = 0.5 * (a + b), tol1 = 1e-12 * Math.abs(x) + tol / 3, tol2 = 2 * tol1;
            if (Math.abs(x - xm) <= tol2 - 0.5 * (b - a)) return x;
            if (Math.abs(e) > tol1) {
                double r = (x - w) * (fx - fv), q = (x - v) * (fx - fw), p = (x - v) * q - (x - w) * r; q = 2 * (q - r); if (q > 0) p = -p; q = Math.abs(q); double etemp = e; e = d;
                if (Math.abs(p) >= Math.abs(0.5 * q * etemp) || p <= q * (a - x) || p >= q * (b - x)) { e = x >= xm ? a - x : b - x; d = CG * e; }
                else { d = p / q; double u = x + d; if (u - a < tol2 || b - u < tol2) d = xm - x >= 0 ? tol1 : -tol1; }
            } else { e = x >= xm ? a - x : b - x; d = CG * e; }
            double u = Math.abs(d) >= tol1 ? x + d : x + (d >= 0 ? tol1 : -tol1), fu = f.applyAsDouble(u);
            if (fu <= fx) { if (u >= x) a = x; else b = x; v = w; w = x; x = u; fv = fw; fw = fx; fx = fu; }
            else { if (u < x) a = u; else b = u; if (fu <= fw || w == x) { v = w; w = u; fv = fw; fw = fu; } else if (fu <= fv || v == x || v == w) { v = u; fv = fu; } }
        }
        return x;
    }

    static double[] nelderMead(ToDoubleFunction<double[]> f, double[] x0, double[] step, double tol, int maxIter) {
        int n = x0.length; double[][] s = new double[n + 1][]; double[] fv = new double[n + 1];
        s[0] = x0.clone(); for (int i = 0; i < n; i++) { s[i + 1] = x0.clone(); s[i + 1][i] += step[i]; }
        for (int i = 0; i <= n; i++) fv[i] = -f.applyAsDouble(s[i]);
        for (int it = 0; it < maxIter; it++) {
            Integer[] o = new Integer[n + 1]; for (int i = 0; i <= n; i++) o[i] = i; java.util.Arrays.sort(o, (a, b) -> Double.compare(fv[a], fv[b]));
            double[][] s2 = new double[n + 1][]; double[] f2 = new double[n + 1]; for (int i = 0; i <= n; i++) { s2[i] = s[o[i]]; f2[i] = fv[o[i]]; } s = s2; System.arraycopy(f2, 0, fv, 0, n + 1);
            if (Math.abs(fv[n] - fv[0]) < tol) { double mx = 0; for (int i = 1; i <= n; i++) for (int j = 0; j < n; j++) mx = Math.max(mx, Math.abs(s[i][j] - s[0][j])); if (mx < 1e-7) break; }
            double[] c = new double[n]; for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) c[j] += s[i][j] / n;
            double[] xr = new double[n]; for (int j = 0; j < n; j++) xr[j] = c[j] + (c[j] - s[n][j]); double fr = -f.applyAsDouble(xr);
            if (fr < fv[0]) { double[] xe = new double[n]; for (int j = 0; j < n; j++) xe[j] = c[j] + 2 * (c[j] - s[n][j]); double fe = -f.applyAsDouble(xe); if (fe < fr) { s[n] = xe; fv[n] = fe; } else { s[n] = xr; fv[n] = fr; } }
            else if (fr < fv[n - 1]) { s[n] = xr; fv[n] = fr; }
            else {
                double[] xc = new double[n]; boolean out = fr < fv[n]; for (int j = 0; j < n; j++) xc[j] = out ? c[j] + 0.5 * (xr[j] - c[j]) : c[j] + 0.5 * (s[n][j] - c[j]); double fc = -f.applyAsDouble(xc);
                if (fc < Math.min(fr, fv[n])) { s[n] = xc; fv[n] = fc; }
                else for (int i = 1; i <= n; i++) { for (int j = 0; j < n; j++) s[i][j] = s[0][j] + 0.5 * (s[i][j] - s[0][j]); fv[i] = -f.applyAsDouble(s[i]); }
            }
        }
        int b = 0; for (int i = 1; i <= n; i++) if (fv[i] < fv[b]) b = i; return s[b];
    }

    /** Multi-start Nelder-Mead (restarts until the objective no longer improves). */
    private static double[] maximise(ToDoubleFunction<double[]> f, double[] x0, double[] step) {
        double[] b = nelderMead(f, x0, step, 1e-12, 1500); double fb = f.applyAsDouble(b);
        for (int r = 0; r < 4; r++) { double[] st = new double[step.length]; for (int i = 0; i < st.length; i++) st[i] = step[i] * 0.2 / (r + 1); double[] b2 = nelderMead(f, b, st, 1e-13, 1500); double f2 = f.applyAsDouble(b2); if (f2 > fb + 1e-10) { b = b2; fb = f2; } else { if (f2 >= fb) b = b2; break; } }
        return polish(f, b);
    }

    /** A few Newton steps (numerical derivatives, step halving) to finish what Nelder-Mead leaves on flat likelihood ridges. */
    private static double[] polish(ToDoubleFunction<double[]> f, double[] x) {
        int n = x.length; double fx = f.applyAsDouble(x);
        for (int it = 0; it < 12; it++) {
            double[] g = new double[n]; double hg = 1e-4;
            for (int i = 0; i < n; i++) { double[] a = x.clone(), b = x.clone(); a[i] += hg; b[i] -= hg; g[i] = (f.applyAsDouble(a) - f.applyAsDouble(b)) / (2 * hg); }
            double[] hs = new double[n]; java.util.Arrays.fill(hs, 2e-3); double[][] Hm = Lin.inv(neg(hessian(f, x, hs))); if (Hm == null) break;
            double[] st = new double[n]; for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) st[i] += Hm[i][j] * g[j];
            double fac = 1; boolean ok = false; double[] nx = null; double fn = fx;
            for (int h = 0; h < 12; h++) { nx = x.clone(); for (int i = 0; i < n; i++) nx[i] += fac * st[i]; fn = f.applyAsDouble(nx); if (fn > fx) { ok = true; break; } fac *= 0.5; }
            if (!ok) break; boolean small = fn - fx < 1e-11; x = nx; fx = fn; if (small) break;
        }
        return x;
    }

    /** Numerical Hessian (central differences). */
    private static double[][] hessian(ToDoubleFunction<double[]> f, double[] x, double[] h) {
        int n = x.length; double[][] H = new double[n][n]; double f0 = f.applyAsDouble(x);
        for (int i = 0; i < n; i++) {
            double[] xp = x.clone(), xm = x.clone(); xp[i] += h[i]; xm[i] -= h[i]; H[i][i] = (f.applyAsDouble(xp) - 2 * f0 + f.applyAsDouble(xm)) / (h[i] * h[i]);
            for (int j = i + 1; j < n; j++) {
                double[] a = x.clone(), b = x.clone(), c = x.clone(), d = x.clone(); a[i] += h[i]; a[j] += h[j]; b[i] += h[i]; b[j] -= h[j]; c[i] -= h[i]; c[j] += h[j]; d[i] -= h[i]; d[j] -= h[j];
                H[i][j] = H[j][i] = (f.applyAsDouble(a) - f.applyAsDouble(b) - f.applyAsDouble(c) + f.applyAsDouble(d)) / (4 * h[i] * h[j]);
            }
        }
        return H;
    }

    // ================================================================ model setup
    private static final class Ctx {
        Data d; int[] sel; Arm[][] arms; boolean[] skip; double[] centre, off; int nodes; double[][] hot;   // hot[i] = {intercept, mode of u} of study i at the previous UM.FS evaluation (warm start)
    }

    private static boolean degenerate(Data d, int j) {
        if (!d.fs()) return false; double e = d.x1[j] + d.x2[j];
        return d.pois() ? e == 0 : (e == 0 || e == d.s1[j] + d.s2[j]);
    }

    private static Ctx context(Data d, int[] sel) {
        Ctx c = new Ctx(); c.d = d; c.sel = sel; int k = sel.length; c.arms = new Arm[k][]; c.skip = new boolean[k]; c.centre = new double[k]; c.off = new double[k]; c.nodes = d.nAGQ; c.hot = new double[k][]; for (int i = 0; i < k; i++) c.hot[i] = new double[]{Double.NaN, Double.NaN};
        for (int i = 0; i < k; i++) {
            int j = sel[i]; c.skip[i] = degenerate(d, j); int kd = d.pois() ? 1 : 0;
            if (!d.twoGroup()) c.arms[i] = new Arm[]{ new Arm(kd, d.x1[j], d.s1[j], 1, 0) };
            else if (d.fs()) {
                c.arms[i] = new Arm[]{ new Arm(kd, d.x1[j], d.s1[j], 0.5, 0), new Arm(kd, d.x2[j], d.s2[j], -0.5, 0) };
                c.centre[i] = d.pois() ? Math.log((d.x1[j] + d.x2[j] + 0.5) / (d.s1[j] + d.s2[j])) : Math.log((d.x1[j] + d.x2[j] + 0.5) / (d.s1[j] + d.s2[j] - d.x1[j] - d.x2[j] + 0.5));
            } else if (d.rs()) c.arms[i] = new Arm[]{ new Arm(kd, d.x1[j], d.s1[j], 0.5, 1), new Arm(kd, d.x2[j], d.s2[j], -0.5, 1) };
            else if (d.pois()) { c.arms[i] = new Arm[]{ new Arm(0, d.x1[j], d.x1[j] + d.x2[j], 1, 0) }; c.off[i] = Math.log(d.s1[j] / d.s2[j]); }   // CM.EL (IRR)
            else if (d.model.equals("CM.AL")) { c.arms[i] = new Arm[]{ new Arm(0, d.x1[j], d.x1[j] + d.x2[j], 1, 0) }; c.off[i] = Math.log(d.s1[j] / d.s2[j]); }
            else c.arms[i] = new Arm[]{ Arm.nch(d.x1[j], d.s1[j], d.s2[j], d.x1[j] + d.x2[j]) };   // CM.EL (OR): the offset is part of the pmf
        }
        return c;
    }

    /** Profile log-likelihood at (theta, tau) for single-group, conditional and UM.FS models (study intercepts maximised out). */
    private static double profileLL(Ctx c, double theta, double tau) {
        final Data d = c.d; final double[] part = new double[c.arms.length];
        // per-study terms are independent: evaluated in parallel, then summed in study order (identical rounding to a serial loop)
        Par.forRange(c.arms.length, i -> {
            if (c.skip[i]) return; final Arm[] a = c.arms[i];
            if (!d.fs()) { part[i] = logMarginal(a, new double[]{theta + c.off[i]}, tau, c.nodes); return; }
            part[i] = fsOptimise(a, new double[]{theta, 0}, DL_ALPHA, tau, c.nodes, c.centre[i], c.hot[i]);   // study intercept maximised out by Newton, warm-started from this study's previous optimum
        });
        double tot = 0; for (int i = 0; i < c.arms.length; i++) if (!c.skip[i]) tot += part[i];
        return tot;
    }

    /** UM.RS: log-likelihood at (alpha, theta, sigma, tau); tau = 0 gives the common-effect model with random study intercepts (1-D Laplace). */
    private static double llRS(Ctx c, double alpha, double theta, double sigma, double tau) {
        final double[] part = new double[c.arms.length];
        Par.forRange(c.arms.length, i -> {
            Arm[] a = c.arms[i]; double[] base = {alpha + theta, alpha};
            if (tau > 0) part[i] = logMarginal2(a, base, sigma, tau);
            else { Arm[] a1 = {new Arm(a[0].kind, a[0].x, a[0].size, 1, 0), new Arm(a[1].kind, a[1].x, a[1].size, 1, 0)}; part[i] = logMarginal(a1, base, sigma, c.nodes); }   // one random effect: nAGQ points, as lme4 allows
        });
        double tot = 0; for (double x : part) tot += x; return tot;
    }

    // ================================================================ saturated model statistics
    private static double satArm(boolean pois, double x, double s) {
        if (pois) return (x > 0 ? x * Math.log(x) : 0) - x - Dist.lgamma(x + 1);
        double p = x / s; return (x > 0 ? x * Math.log(p) : 0) + (s - x > 0 ? (s - x) * Math.log(1 - p) : 0) + lchoose(s, x);
    }

    /** Saturated model: study-specific MLE of the linear predictor; returns {log-likelihood, estimate of the log ratio or NaN, information or NaN} for the study at index i. */
    private static double[] satStudy(Ctx c, int i) {
        Data d = c.d; int j = c.sel[i]; Arm[] a = c.arms[i];
        if (!d.twoGroup()) { double x = d.x1[j], s = d.s1[j]; double ll = satArm(d.pois(), x, s); if (d.pois()) return new double[]{ll, x > 0 ? Math.log(x / s) : Double.NaN, x}; return new double[]{ll, x > 0 && x < s ? Math.log(x / (s - x)) : Double.NaN, x > 0 && x < s ? x * (s - x) / s : Double.NaN}; }
        if (d.fs() || d.rs()) {
            double ll = satArm(d.pois(), d.x1[j], d.s1[j]) + satArm(d.pois(), d.x2[j], d.s2[j]);
            if (d.pois()) { double p = d.x1[j], q = d.x2[j]; return new double[]{ll, p > 0 && q > 0 ? Math.log((p / d.s1[j]) / (q / d.s2[j])) : Double.NaN, p > 0 && q > 0 ? 1 / (1 / p + 1 / q) : Double.NaN}; }
            double p = d.x1[j], b = d.s1[j] - p, q = d.x2[j], r = d.s2[j] - q; boolean ok = p > 0 && b > 0 && q > 0 && r > 0;
            return new double[]{ll, ok ? Math.log(p * r / (b * q)) : Double.NaN, ok ? 1 / (1 / p + 1 / b + 1 / q + 1 / r) : Double.NaN};
        }
        // conditional models: single observation with free log ratio eta_i
        double x = d.x1[j], m = d.x1[j] + d.x2[j];
        if (a[0].kind == 0) { double ll = satArm(false, x, m); boolean ok = x > 0 && x < m; return new double[]{ll, ok ? Math.log(x / (m - x)) - c.off[i] : Double.NaN, ok ? x * (m - x) / m : Double.NaN}; }
        int hi = a[0].lo + a[0].c.length - 1; if (x <= a[0].lo || x >= hi) return new double[]{0, Double.NaN, Double.NaN};
        double lo = -40, up = 40, e = 0, t[] = new double[3];
        for (int it = 0; it < 200; it++) { e = 0.5 * (lo + up); armEval(a[0], e, t); if (t[1] > 0) lo = e; else up = e; if (up - lo < 1e-12) break; }
        armEval(a[0], e, t); return new double[]{t[0], e, t[2]};
    }

    private static double qWaldOf(Ctx c) {
        java.util.List<double[]> yv = new java.util.ArrayList<>();
        for (int i = 0; i < c.sel.length; i++) { double[] s = satStudy(c, i); if (!Double.isNaN(s[1]) && !Double.isNaN(s[2]) && s[2] > 0) yv.add(new double[]{s[1], 1 / s[2]}); }
        if (yv.size() < 2) return 0; double sw = 0, swy = 0; for (double[] t : yv) { sw += 1 / t[1]; swy += t[0] / t[1]; } double m = swy / sw, q = 0;
        for (double[] t : yv) q += (t[0] - m) * (t[0] - m) / t[1]; return q;
    }

    private static double llSat(Ctx c) {
        if (c.d.rs()) return llSatRS(c);
        final double[] part = new double[c.sel.length]; Par.forRange(part.length, i -> part[i] = satStudy(c, i)[0]); double tot = 0; for (double x : part) tot += x; return tot;
    }

    /** Fit of the UM.RS saturated model: log-likelihood, common intercept, SD of the random study intercepts, and per study the maximised study effect and the mode of its random intercept (hot[i] = {theta_i, b_i}). */
    private static final class SatRS { double ll, alpha, sigma; double[][] hot; Arm[][] a1; }

    /** UM.RS saturated model: study-specific group effects, common intercept and random study intercepts (sigma estimated). The study effect of each study is maximised
     *  out by Newton steps on the analytic score (fsOptimise), in parallel over studies and warm-started from the previous Nelder-Mead evaluation. */
    private static SatRS satRS(Ctx c) {
        final int k = c.sel.length; final Arm[][] a1 = new Arm[k][]; final double[][] hot = new double[k][];
        for (int i = 0; i < k; i++) {
            Arm[] a = c.arms[i]; a1[i] = new Arm[]{new Arm(a[0].kind, a[0].x, a[0].size, 1, 0), new Arm(a[1].kind, a[1].x, a[1].size, 1, 0)};
            double l1 = a[0].kind == 0 ? Math.log((a[0].x + 0.5) / (a[0].size - a[0].x + 0.5)) : Math.log((a[0].x + 0.5) / a[0].size);
            double l2 = a[1].kind == 0 ? Math.log((a[1].x + 0.5) / (a[1].size - a[1].x + 0.5)) : Math.log((a[1].x + 0.5) / a[1].size);
            hot[i] = new double[]{Math.max(-14, Math.min(14, l1 - l2)), Double.NaN};   // empirical log odds (rate) ratio as the first starting value
        }
        ToDoubleFunction<double[]> f = p -> {
            final double alpha = p[0], sigma = Math.exp(Math.max(-12, Math.min(5, p[1]))); final double[] part = new double[k];
            Par.forRange(k, i -> part[i] = fsOptimise(a1[i], new double[]{alpha, alpha}, DL_THETA, sigma, c.nodes, 0, hot[i]));
            double tot = 0; for (double x : part) tot += x; return tot;
        };
        double a0 = rsInit(c)[0]; double[] b = maximise(f, new double[]{a0, Math.log(0.5)}, new double[]{0.3, 0.5});
        SatRS r = new SatRS(); r.ll = f.applyAsDouble(b);   // the last evaluation leaves hot at the optimum
        r.alpha = b[0]; r.sigma = Math.exp(Math.max(-12, Math.min(5, b[1]))); r.hot = hot; r.a1 = a1; return r;
    }

    private static double llSatRS(Ctx c) { return satRS(c).ll; }

    /** UM.RS Wald-type heterogeneity statistic, as metafor::rma.glmm (lme4 backend) computes it: the Wald test that the k study-specific effects of the saturated model
     *  (study-specific group effects, common intercept, random study intercepts with SD sigma) are equal, using the covariance matrix lme4 reports for its fixed effects.
     *  That covariance is the inverse of the information of the fixed effects with the random intercepts integrated out around their conditional modes:
     *  with I1, I2 the information of the two arms at the mode and c = 1 / sigma^2, S = I1 + I2 + c, study i contributes
     *      theta_i, theta_i : I1 (I2 + c) / S      theta_i, alpha : I1 c / S      alpha, alpha : (I1 + I2) c / S.
     *  Unlike Cochran's Q on the study log ratios (the UM.FS statistic), this accounts for the random intercepts and for the correlation of the study effects through the
     *  shared intercept. Studies with no finite study effect (a zero cell, or all events) carry no information about theta_i and are left out of the test, as before;
     *  they still contribute to the information about alpha. */
    private static double qWaldRS(Ctx c, SatRS s) {
        final int k = c.sel.length; final double cc = 1 / (s.sigma * s.sigma); double[] t = new double[3]; double jaa = 0;
        double[] th = new double[k], u = new double[k], d = new double[k]; int m = 0;
        for (int i = 0; i < k; i++) {
            Arm[] a = s.a1[i]; final double ti = s.hot[i][0], bi = s.hot[i][1];
            armEval(a[0], s.alpha + ti + bi, t); final double i1 = t[2];
            armEval(a[1], s.alpha + bi, t); final double i2 = t[2], S = i1 + i2 + cc;
            jaa += (i1 + i2) * cc / S;
            boolean finite = true;
            for (Arm q : a) finite &= q.x > 0 && (q.kind == 1 || q.x < q.size);
            if (finite && !Double.isNaN(ti) && !Double.isNaN(bi)) { th[m] = ti; u[m] = i1 * cc / S; d[m] = i1 * (i2 + cc) / S; m++; }
        }
        if (m < 2 || !(jaa > 0)) return 0;
        double mean = 0; for (int i = 0; i < m; i++) mean += th[i] / m;   // Q is invariant to a common shift of the study effects; centring limits cancellation
        double sd = 0, sdth = 0, su = 0, sth = 0, sd1 = 0, sut = 0;
        for (int i = 0; i < m; i++) { double x = th[i] - mean; sd += d[i] * x * x; sdth += d[i] * x; su += u[i]; sut += u[i] * x; sd1 += d[i]; }
        double tPt = sd - sut * sut / jaa, onePt = sdth - su * sut / jaa, oneP1 = sd1 - su * su / jaa;   // theta' P theta, 1' P theta, 1' P 1 with P = diag(d) - u u' / jaa
        return Math.max(0, tPt - onePt * onePt / oneP1);
    }

    private static double[] rsInit(Ctx c) {
        Data d = c.d; double sx = 0, ss = 0; for (int j : c.sel) { sx += d.x2[j]; ss += d.s2[j]; }
        double a = d.pois() ? Math.log(Math.max(sx, 0.5) / ss) : Math.log((sx + 0.5) / (ss - sx + 0.5)); return new double[]{a};
    }

    // ================================================================ fitting
    /** Fit the model on the studies in sel. random = false: common-effect model. initTheta: starting value for the effect. */
    public static Result fit(Data d, int[] sel, boolean random, double initTheta) { return fit(d, sel, random, initTheta, true, Double.NaN); }

    /** As above. lrt = false skips the saturated-model likelihood (the slowest part of a UM.RS fit), leaving qLRT = NaN: for callers that only need the estimates and the Wald Q
     *  (leave-one-out, cumulative, Baujat). llSatKnown: the saturated log-likelihood of the same studies if already available (it depends on the data only, not on the model fitted), else NaN. */
    public static Result fit(Data d, int[] sel, boolean random, double initTheta, boolean lrt, double llSatKnown) { return fit(d, sel, random, initTheta, lrt, llSatKnown, true, Double.NaN); }

    /** As above. wald = false skips the Wald-type heterogeneity statistic of UM.RS (it needs the saturated model too), leaving qWald = NaN: for callers that only read the
     *  estimates (Baujat). qWaldKnown: the Wald statistic of the same studies if already available (like llSatKnown, it depends on the data only), else NaN. */
    public static Result fit(Data d, int[] sel, boolean random, double initTheta, boolean lrt, double llSatKnown, boolean wald, double qWaldKnown) {
        Result r = new Result(); int k = sel.length; r.k = k; r.dfQ = k - 1;
        if (k == 0) { r.ok = false; return r; }
        Ctx c = context(d, sel);
        int used = 0; for (int i = 0; i < k; i++) if (!c.skip[i]) used++;
        if (used == 0) { r.ok = false; r.note = "No information in the data for the GLMM."; return r; }
        if (!d.twoGroup()) { double sx = 0, ss = 0; for (int j : sel) { sx += d.x1[j]; ss += d.s1[j]; } if (sx == 0 || (!d.pois() && sx == ss)) { r.ok = false; r.note = "All studies have zero (or all) events; GLMM not estimable."; return r; } }
        double th0 = Double.isNaN(initTheta) || Double.isInfinite(initTheta) ? 0 : initTheta;
        if (d.rs()) return fitRS(c, r, random, th0, lrt, llSatKnown, wald, qWaldKnown);
        // ---- common-effect fit (tau = 0)
        double thFE;
        if (!d.twoGroup()) { double sx = 0, ss = 0; for (int j : sel) { sx += d.x1[j]; ss += d.s1[j]; } thFE = d.pois() ? Math.log(sx / ss) : Math.log(sx / (ss - sx)); }
        else thFE = brentMin(t -> -profileLL(c, t, 0), th0 - 12, th0 + 12, 1e-9);
        double llFE = profileLL(c, thFE, 0), seFE = seFromCurv(t -> profileLL(c, t, 0), thFE);
        if (lrt) { r.llSat = Double.isNaN(llSatKnown) ? llSat(c) : llSatKnown; r.qLRT = Math.max(0, 2 * (r.llSat - llFE)); } r.qWald = qWaldOf(c);
        r.est = thFE; r.se = seFE; r.tau2 = 0; r.logLik = llFE;
        if (!random || k < 2 || used < 2) return r;
        // ---- random-effects ML fit over (theta, log tau)
        ToDoubleFunction<double[]> obj = p -> profileLL(c, p[0], Math.exp(Math.max(-14, Math.min(6, p[1]))));
        double[] best = maximise(obj, new double[]{thFE, Math.log(0.5)}, new double[]{0.3, 0.7});
        double llR = obj.applyAsDouble(best), tau = Math.exp(Math.max(-14, Math.min(6, best[1])));
        if (tau < 1e-3 || llR <= llFE + 1e-9) { r.tau2 = 0; return r; }   // boundary solution: tau^2 = 0
        double[][] H = hessian(obj, best, new double[]{1e-3, 1e-3}); double det = H[0][0] * H[1][1] - H[0][1] * H[0][1];
        r.est = best[0]; r.tau2 = tau * tau; r.logLik = llR;
        if (det > 0 && H[0][0] < 0) { r.se = Math.sqrt(-H[1][1] / det); r.seTau2 = 2 * r.tau2 * Math.sqrt(-H[0][0] / det); } else r.se = Double.NaN;
        return r;
    }

    private static Result fitRS(Ctx c, Result r, boolean random, double th0, boolean lrt, double llSatKnown, boolean wald, double qWaldKnown) {
        int k = c.sel.length; double a0 = rsInit(c)[0];
        ToDoubleFunction<double[]> fe = p -> llRS(c, p[0], p[1], Math.exp(Math.max(-12, Math.min(4, p[2]))), 0);
        double[] b = maximise(fe, new double[]{a0, th0, Math.log(0.5)}, new double[]{0.3, 0.3, 0.5});
        double llFE = fe.applyAsDouble(b);
        // the saturated model serves both heterogeneity tests: fitted once, and only if one of them still needs it
        SatRS sat = ((lrt && Double.isNaN(llSatKnown)) || (wald && Double.isNaN(qWaldKnown))) && k > 0 ? satRS(c) : null;
        if (lrt) { r.llSat = Double.isNaN(llSatKnown) ? sat.ll : llSatKnown; r.qLRT = Math.max(0, 2 * (r.llSat - llFE)); }
        r.qWald = !wald ? Double.NaN : !Double.isNaN(qWaldKnown) ? qWaldKnown : qWaldRS(c, sat);
        r.est = b[1]; r.tau2 = 0; r.logLik = llFE; r.sigma2 = Math.exp(2 * b[2]);
        r.se = seOf(fe, b, new boolean[]{true, true, Math.exp(b[2]) >= 1e-3}, 1);
        if (!random || k < 2) return r;
        ToDoubleFunction<double[]> re = p -> llRS(c, p[0], p[1], Math.exp(Math.max(-12, Math.min(4, p[2]))), Math.exp(Math.max(-12, Math.min(4, p[3]))));
        double[] x0 = {b[0], b[1], b[2], Math.log(0.5)}; double[] best = maximise(re, x0, new double[]{0.3, 0.3, 0.5, 0.7});
        double llR = re.applyAsDouble(best), tau = Math.exp(Math.max(-12, Math.min(4, best[3])));
        if (tau < 1e-3 || llR <= llFE + 1e-9) return r;
        r.est = best[1]; r.tau2 = tau * tau; r.logLik = llR; r.sigma2 = Math.exp(2 * best[2]);
        r.se = seOf(re, best, new boolean[]{true, true, Math.exp(best[2]) >= 1e-3, true}, 1); r.seTau2 = 2 * r.tau2 * seOf(re, best, new boolean[]{true, true, Math.exp(best[2]) >= 1e-3, true}, 3);
        return r;
    }

    /** Standard error of parameter idx from the inverse Hessian over the free parameters (parameters on a boundary are held fixed). */
    private static double seOf(ToDoubleFunction<double[]> f, double[] x, boolean[] free, int idx) {
        int m = 0; int[] map = new int[x.length]; for (int i = 0; i < x.length; i++) map[i] = free[i] ? m++ : -1;
        ToDoubleFunction<double[]> g = q -> { double[] full = x.clone(); for (int i = 0; i < x.length; i++) if (map[i] >= 0) full[i] = q[map[i]]; return f.applyAsDouble(full); };
        double[] q0 = new double[m]; double[] h = new double[m]; for (int i = 0; i < x.length; i++) if (map[i] >= 0) { q0[map[i]] = x[i]; h[map[i]] = 1e-3; }
        double[][] V = Lin.inv(neg(hessian(g, q0, h))); if (V == null) return Double.NaN; double v = V[map[idx]][map[idx]]; return v > 0 ? Math.sqrt(v) : Double.NaN;
    }

    private static double[][] neg(double[][] h) { int n = h.length; double[][] o = new double[n][n]; for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) o[i][j] = -h[i][j]; return o; }

    private static double seFromCurv(DoubleUnaryOperator f, double x) {
        double h = 1e-3, f0 = f.applyAsDouble(x), fp = f.applyAsDouble(x + h), fm = f.applyAsDouble(x - h); double a = (fp - 2 * f0 + fm) / (h * h);
        return a < 0 ? Math.sqrt(-1 / a) : Double.NaN;
    }
}
