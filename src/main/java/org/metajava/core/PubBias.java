package org.metajava.core;

import java.util.*;
import java.util.function.ToDoubleFunction;

/**
 * Publication-bias methods that complement the funnel plot tests: PET-PEESE (Stanley &amp; Doucouliagos, 2014), a three-parameter
 * selection model (Vevea &amp; Hedges, 1995; as metafor::selmodel(type = "stepfun") with one step at one-sided p = .025) and
 * fail-safe N (Rosenthal, 1979; Rosenberg, 2005). All work on the working scale of the effect (log scale for ratio measures).
 */
public final class PubBias {
    private PubBias() {}

    /** Studies with a finite effect and a positive finite standard error. */
    private static int[] usable(MetaResult r) {
        List<Integer> ok = new ArrayList<>();
        for (int i = 0; i < r.TE.length; i++) if (Double.isFinite(r.TE[i]) && Double.isFinite(r.seTE[i]) && r.seTE[i] > 0) ok.add(i);
        return ok.stream().mapToInt(Integer::intValue).toArray();
    }
    private static double nullValue(MetaResult r) { double z = r.spec.nullValue(); return Double.isNaN(z) ? 0 : z; }

    // ================================================================ PET-PEESE
    public static final class Reg {
        public double b0, se0, b1, se1, t0, p0, t1, p1, lo0, up0; public int df;
        public double p0Null;   // p-value of H0: corrected effect = null value
        public boolean exact;   // the regression fits the data exactly (residual variance 0, e.g. all effects identical): SE 0, t and p are not defined (NaN)
    }
    public static final class PetPeese {
        public String error; public int k; public Reg pet, peese;
        public boolean usePeese; public boolean decisionApplied; public boolean exactFit; public double alpha = 0.05;
        public double estimate, lower, upper;          // the recommended (conditional) estimate, working scale
        public double[] te, se; public String[] label; public double maxSe, nullValue;
        public String note = "";
    }

    /** Weighted least squares of y on x with weights w; residual variance estimated (multiplicative error, as lm() with weights). */
    static Reg wls(double[] y, double[] x, double[] w, double level, double nullIntercept) {
        int k = y.length; double sw = 0, swx = 0, swxx = 0, swy = 0, swxy = 0;
        for (int i = 0; i < k; i++) { sw += w[i]; swx += w[i] * x[i]; swxx += w[i] * x[i] * x[i]; swy += w[i] * y[i]; swxy += w[i] * x[i] * y[i]; }
        double det = sw * swxx - swx * swx; Reg g = new Reg(); g.df = k - 2;
        g.b0 = (swxx * swy - swx * swxy) / det; g.b1 = (sw * swxy - swx * swy) / det;
        double rss = 0; for (int i = 0; i < k; i++) { double e = y[i] - g.b0 - g.b1 * x[i]; rss += w[i] * e * e; }
        double syy = 0; for (int i = 0; i < k; i++) syy += w[i] * y[i] * y[i];
        g.exact = rss <= 1e-18 * syy;   // zero residual variance: the t statistics are 0/0 or x/0, not numbers
        double s2 = rss / g.df; g.se0 = Math.sqrt(s2 * swxx / det); g.se1 = Math.sqrt(s2 * sw / det);
        g.t0 = g.b0 / g.se0; g.t1 = g.b1 / g.se1; g.p0 = Dist.ptTwoSided(g.t0, g.df); g.p1 = Dist.ptTwoSided(g.t1, g.df);
        g.p0Null = Dist.ptTwoSided((g.b0 - nullIntercept) / g.se0, g.df);
        if (g.exact) { g.se0 = 0; g.se1 = 0; g.t0 = g.t1 = g.p0 = g.p1 = g.p0Null = Double.NaN; }
        double q = Dist.qt(1 - (1 - level) / 2, g.df); g.lo0 = g.b0 - q * g.se0; g.up0 = g.b0 + q * g.se0;
        return g;
    }

    public static PetPeese petPeese(MetaResult r) {
        PetPeese res = new PetPeese(); int[] u = usable(r); int k = u.length; res.k = k;
        if (k < 3) { res.error = "PET-PEESE needs at least 3 studies with a standard error (k = " + k + ")."; return res; }
        double[] y = new double[k], se = new double[k], w = new double[k], se2 = new double[k]; String[] lab = new String[k];
        for (int j = 0; j < k; j++) { int i = u[j]; y[j] = r.TE[i]; se[j] = r.seTE[i]; w[j] = 1 / (se[j] * se[j]); se2[j] = se[j] * se[j]; lab[j] = r.studlab[i]; res.maxSe = Math.max(res.maxSe, se[j]); }
        boolean same = true; for (double s : se) if (Math.abs(s - se[0]) > 1e-12 * Math.max(1, se[0])) same = false;
        if (same) { res.error = "PET-PEESE is not feasible as all studies have the same standard error."; return res; }
        double lv = r.spec.levelMa, nv = nullValue(r); res.nullValue = nv; res.te = y; res.se = se; res.label = lab;
        res.pet = wls(y, se, w, lv, nv); res.peese = wls(y, se2, w, lv, nv);
        boolean hasNull = !Double.isNaN(r.spec.nullValue());
        res.exactFit = res.pet.exact || res.peese.exact;
        res.decisionApplied = hasNull && !res.exactFit;
        res.usePeese = res.decisionApplied && res.pet.p0Null < res.alpha;   // PET intercept differs from the null: a genuine effect is likely, PEESE is less biased then
        Reg g = res.usePeese ? res.peese : res.pet; res.estimate = g.b0; res.lower = g.lo0; res.upper = g.up0;
        if (res.exactFit) res.note = "The regression fits the data exactly (residual variance 0, e.g. all study effects are identical), so the standard errors, test statistics and p-values of PET and PEESE are not defined (NA) and the PET-PEESE decision rule is not applied; the estimate shown is the PET intercept.";
        else if (k < 10) res.note = "Fewer than 10 studies: PET and PEESE have very low power and unstable estimates; interpret with great caution.";
        return res;
    }

    // ================================================================ selection model
    public static final class Selection {
        public String error, note = ""; public int k, kSig, kNon; public boolean flipped; public double cutZ = Dist.qnorm(0.975);
        public double mu, tau2, delta, ll;                  // adjusted (selection) model, on the original working scale
        public double muLo, muUp;                           // profile-likelihood confidence limits of the adjusted effect
        public double mu0, tau20, ll0;                      // unadjusted random-effects model (ML), delta fixed at 1
        public double lrt, pLrt; public boolean converged = true;
        public String direction = "";
    }

    /** direction: "Auto" (the sign of the pooled effect decides), "Positive" or "Negative" (the direction in which significant results are favoured for publication). */
    public static Selection selection(MetaResult r, String direction) {
        Selection res = new Selection(); int[] u = usable(r); int k = u.length; res.k = k;
        if (k < 5) { res.error = "The selection model needs at least 5 studies with a standard error (k = " + k + ")."; return res; }
        if (!r.spec.hasNull()) { res.error = "The selection model needs a null effect (significance and the favoured direction are defined against it): this measure has none by default. Enter a 'Target effect-size' (section 2) to use it."; return res; }
        double nv = nullValue(r);
        double sw = 0, swy = 0; for (int i : u) { double w = 1 / (r.seTE[i] * r.seTE[i]); sw += w; swy += w * (r.TE[i] - nv); }
        boolean neg = "Negative".equals(direction) || (!"Positive".equals(direction) && swy < 0); res.flipped = neg;
        res.direction = neg ? "negative (effects below the null value)" : "positive (effects above the null value)";
        // work with y = +/-(TE - null) so that the favoured direction is positive and the null value is 0
        final double[] y = new double[k], se = new double[k], v = new double[k], c = new double[k];
        for (int j = 0; j < k; j++) { int i = u[j]; y[j] = (neg ? -1 : 1) * (r.TE[i] - nv); se[j] = r.seTE[i]; v[j] = se[j] * se[j]; c[j] = res.cutZ * se[j]; if (y[j] > c[j]) res.kSig++; else res.kNon++; }
        double mean = (neg ? -1 : 1) * swy / sw, sd = 0; for (double a : y) sd += (a - mean) * (a - mean); sd = Math.sqrt(sd / (k - 1));
        final double scale = Math.max(sd, 1e-8);
        final double[] stepMu = {0.5 * scale / Math.sqrt(k), 0.3 * scale, 0.7};
        // full model: x = {mu, t (tau = |t|), eta (delta = exp(eta))}
        ToDoubleFunction<double[]> full = x -> loglik(y, v, c, x[0], x[1], x[2]);
        double[] best = null; double bf = Double.NEGATIVE_INFINITY;
        for (double eta0 : new double[]{0, -1.5, -3, 1}) {
            double[] b = maximise(full, new double[]{mean, 0.5 * scale, eta0}, stepMu); double fb = full.applyAsDouble(b);
            if (fb > bf) { bf = fb; best = b; } }
        // restricted model: delta = 1
        ToDoubleFunction<double[]> restr = x -> loglik(y, v, c, x[0], x[1], 0);
        double[] b0 = null; double f0 = Double.NEGATIVE_INFINITY;
        for (double t0 : new double[]{0.5 * scale, 0.05 * scale, 1.5 * scale}) { double[] b = maximise(x2 -> restr.applyAsDouble(new double[]{x2[0], x2[1]}), new double[]{mean, t0}, new double[]{stepMu[0], stepMu[1]}); double fb = restr.applyAsDouble(b); if (fb > f0) { f0 = fb; b0 = b; } }
        if (bf < f0) { bf = f0; best = new double[]{b0[0], b0[1], 0}; }   // the full model contains the restricted one: never worse
        res.ll = bf; res.ll0 = f0; res.lrt = Math.max(0, 2 * (bf - f0)); res.pLrt = Dist.pchisqUpper(res.lrt, 1);
        double sgn = neg ? -1 : 1;
        res.mu = nv + sgn * best[0]; res.tau2 = best[1] * best[1]; res.delta = Math.exp(best[2]); res.mu0 = nv + sgn * b0[0]; res.tau20 = b0[1] * b0[1];
        // profile-likelihood CI of mu
        double crit = Math.pow(Dist.qnorm(1 - (1 - r.spec.levelMa) / 2), 2), muHat = best[0], etaHat = best[2], tHat = best[1], step = Math.max(stepMu[0] * 3, 1e-6);
        final double llMax = bf;
        java.util.function.DoubleUnaryOperator dev = m -> {   // 2 * (llMax - profile loglik at mu = m)
            ToDoubleFunction<double[]> f = x -> loglik(y, v, c, m, x[0], x[1]); double pf = Double.NEGATIVE_INFINITY;
            for (double e0 : new double[]{etaHat, 0, -2}) { double[] b = maximise(f, new double[]{tHat, e0}, new double[]{stepMu[1], stepMu[2]}); pf = Math.max(pf, f.applyAsDouble(b)); }
            return Math.max(0, 2 * (llMax - pf)); };
        double lo = limit(dev, muHat, -1, step, crit), up = limit(dev, muHat, 1, step, crit);
        res.muLo = neg ? nv - (muHat + up) : nv + muHat - lo; res.muUp = neg ? nv - (muHat - lo) : nv + muHat + up;
        if (Double.isNaN(lo) || Double.isNaN(up)) res.converged = false;
        if (res.kSig < 3 || res.kNon < 3) res.note = "Fewer than 3 studies on one side of the selection cut-off (p = .025): the selection parameter is hardly identifiable and the adjusted estimate is very unstable.";
        else if (k < 20) res.note = "Fewer than 20 studies: selection models need many studies (often 30 or more); treat the adjusted estimate as a sensitivity analysis only.";
        return res;
    }

    /** Log-likelihood of the three-parameter selection model (one cut at one-sided p = .025, relative weight delta = exp(eta) for non-significant results). Parameters: mu, t (tau^2 = t^2), eta. */
    static double loglik(double[] y, double[] v, double[] c, double mu, double t, double eta) {
        if (eta < -9 || eta > 4) return -1e12 - Math.abs(eta) * 1e3;
        double tau2 = t * t, delta = Math.exp(eta), ll = 0;
        for (int i = 0; i < y.length; i++) {
            double sig = Math.sqrt(v[i] + tau2), z = (y[i] - mu) / sig, a = (c[i] - mu) / sig;
            double A = 1 - (1 - delta) * Dist.pnorm(a);   // P(selected) = P(y > c) + delta * P(y <= c)
            ll += (y[i] > c[i] ? 0 : eta) - 0.5 * Math.log(2 * Math.PI) - Math.log(sig) - 0.5 * z * z - Math.log(A);
        }
        return ll;
    }

    /** Distance from the estimate at which the profile deviance reaches crit (bracketing, then bisection); NaN if not found. dir = +1 / -1. Returns a positive distance. */
    private static double limit(java.util.function.DoubleUnaryOperator dev, double centre, int dir, double step, double crit) {
        double a = 0, b = step; int n = 0;
        while (dev.applyAsDouble(centre + dir * b) < crit) { a = b; b *= 2; if (++n > 30) return Double.NaN; }
        for (int i = 0; i < 40; i++) { double m = 0.5 * (a + b); if (dev.applyAsDouble(centre + dir * m) < crit) a = m; else b = m; if (b - a < 1e-7 * Math.max(1, b)) break; }
        return 0.5 * (a + b);
    }

    private static double[] maximise(ToDoubleFunction<double[]> f, double[] x0, double[] step) {
        double[] b = Glmm.nelderMead(f, x0, step, 1e-13, 2500); double fb = f.applyAsDouble(b);
        for (int i = 0; i < 6; i++) {
            double[] st = new double[step.length]; for (int j = 0; j < st.length; j++) st[j] = step[j] * Math.pow(0.4, i + 1);
            double[] b2 = Glmm.nelderMead(f, b, st, 1e-14, 2500); double f2 = f.applyAsDouble(b2);
            if (f2 > fb + 1e-11) { b = b2; fb = f2; } else { if (f2 >= fb) b = b2; break; }
        }
        return b;
    }

    // ================================================================ fail-safe N
    public static final class FailSafe {
        public String error; public int k; public double alpha = 0.05, zAlpha;
        public double sumZ, stouffer, pStouffer, nRosenthal, tolerance, nRosenberg, zFixed, pFixed, estFixed;
    }

    public static FailSafe failSafe(MetaResult r) {
        FailSafe f = new FailSafe(); int[] u = usable(r); int k = u.length; f.k = k;
        if (k < 2) { f.error = "Fail-safe N needs at least 2 studies (k = " + k + ")."; return f; }
        if (!r.spec.hasNull()) { f.error = "Fail-safe N needs a null effect (the combined test is against it): this measure has none by default. Enter a 'Target effect-size' (section 2) to use it."; return f; }
        double nv = nullValue(r); f.zAlpha = Dist.qnorm(1 - f.alpha);   // one-tailed 5 %: 1.645
        double sw = 0, swy = 0; for (int i : u) { double z = (r.TE[i] - nv) / r.seTE[i]; f.sumZ += z; double w = 1 / (r.seTE[i] * r.seTE[i]); sw += w; swy += w * (r.TE[i] - nv); }
        f.stouffer = f.sumZ / Math.sqrt(k); f.pStouffer = Dist.pnormTwoSided(f.stouffer);
        f.nRosenthal = Math.max(0, Math.ceil(f.sumZ * f.sumZ / (f.zAlpha * f.zAlpha) - k)); f.tolerance = 5.0 * k + 10;
        f.estFixed = nv + swy / sw; f.zFixed = swy / Math.sqrt(sw); f.pFixed = Dist.pnormTwoSided(f.zFixed);
        f.nRosenberg = Math.max(0, Math.ceil(k * (f.zFixed * f.zFixed / (f.zAlpha * f.zAlpha) - 1)));
        return f;
    }
}
