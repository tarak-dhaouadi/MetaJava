package org.metajava.core;

import java.util.function.DoubleUnaryOperator;

/** Probability distributions (pure JDK), accuracy ~1e-12 for typical meta-analysis ranges. */
public final class Dist {
    private Dist() {}

    private static final double[] LG = {76.18009172947146, -86.50532032941677, 24.01409824083091,
            -1.231739572450155, 0.1208650973866179e-2, -0.5395239384953e-5};

    public static double lgamma(double x) {
        if (x < 0.5) return Math.log(Math.PI / Math.abs(Math.sin(Math.PI * x))) - lgamma(1 - x);
        x -= 1;
        double g = 7, a = 0.99999999999980993;
        double[] c = {676.5203681218851, -1259.1392167224028, 771.32342877765313, -176.61502916214059,
                12.507343278686905, -0.13857109526572012, 9.9843695780195716e-6, 1.5056327351493116e-7};
        for (int i = 0; i < 8; i++) a += c[i] / (x + i + 1);
        double t = x + g + 0.5;
        return 0.5 * Math.log(2 * Math.PI) + (x + 0.5) * Math.log(t) - t + Math.log(a);
    }

    /** Regularised lower incomplete gamma P(a,x). */
    public static double pgammaReg(double a, double x) {
        if (x <= 0) return 0;
        if (Double.isInfinite(x)) return 1;
        if (x < a + 1) {
            double ap = a, sum = 1 / a, del = sum;
            for (int n = 0; n < 2000; n++) {
                ap += 1; del *= x / ap; sum += del;
                if (Math.abs(del) < Math.abs(sum) * 1e-16) break;
            }
            return sum * Math.exp(-x + a * Math.log(x) - lgamma(a));
        }
        return 1 - qgammaCF(a, x);
    }

    /** Regularised upper incomplete gamma Q(a,x) via continued fraction (x >= a+1). */
    private static double qgammaCF(double a, double x) {
        double tiny = 1e-300, b = x + 1 - a, c = 1 / tiny, d = 1 / b, h = d;
        for (int i = 1; i < 2000; i++) {
            double an = -i * (i - a);
            b += 2;
            d = an * d + b; if (Math.abs(d) < tiny) d = tiny;
            c = b + an / c; if (Math.abs(c) < tiny) c = tiny;
            d = 1 / d;
            double del = d * c; h *= del;
            if (Math.abs(del - 1) < 1e-16) break;
        }
        return Math.exp(-x + a * Math.log(x) - lgamma(a)) * h;
    }

    public static double qgammaUpper(double a, double x) {
        if (x <= 0) return 1;
        if (x < a + 1) return 1 - pgammaReg(a, x);
        return qgammaCF(a, x);
    }

    /** Regularised incomplete beta I_x(a,b). */
    public static double pbetaReg(double x, double a, double b) {
        if (x <= 0) return 0;
        if (x >= 1) return 1;
        double bt = Math.exp(lgamma(a + b) - lgamma(a) - lgamma(b) + a * Math.log(x) + b * Math.log1p(-x));
        if (x < (a + 1) / (a + b + 2)) return bt * betaCF(x, a, b) / a;
        return 1 - bt * betaCF(1 - x, b, a) / b;
    }

    private static double betaCF(double x, double a, double b) {
        double tiny = 1e-300, qab = a + b, qap = a + 1, qam = a - 1, c = 1, d = 1 - qab * x / qap;
        if (Math.abs(d) < tiny) d = tiny;
        d = 1 / d; double h = d;
        for (int m = 1; m <= 5000; m++) {
            int m2 = 2 * m;
            double aa = m * (b - m) * x / ((qam + m2) * (a + m2));
            d = 1 + aa * d; if (Math.abs(d) < tiny) d = tiny;
            c = 1 + aa / c; if (Math.abs(c) < tiny) c = tiny;
            d = 1 / d; h *= d * c;
            aa = -(a + m) * (qab + m) * x / ((a + m2) * (qap + m2));
            d = 1 + aa * d; if (Math.abs(d) < tiny) d = tiny;
            c = 1 + aa / c; if (Math.abs(c) < tiny) c = tiny;
            d = 1 / d;
            double del = d * c; h *= del;
            if (Math.abs(del - 1) < 1e-16) break;
        }
        return h;
    }

    public static double dnorm(double x) { return Math.exp(-0.5 * x * x) / Math.sqrt(2 * Math.PI); }

    public static double pnorm(double x) {
        if (Double.isNaN(x)) return Double.NaN;
        if (Double.isInfinite(x)) return x < 0 ? 0 : 1;
        double q = 0.5 * qgammaUpper(0.5, x * x / 2);
        return x < 0 ? q : 1 - q;
    }

    public static double pnormUpper(double x) { return pnorm(-x); }

    public static double qnorm(double p) {
        if (p <= 0) return Double.NEGATIVE_INFINITY;
        if (p >= 1) return Double.POSITIVE_INFINITY;
        if (p == 0.5) return 0;
        double pp = p < 0.5 ? p : 1 - p;
        double t = Math.sqrt(-2 * Math.log(pp));
        double x = t - (2.515517 + 0.802853 * t + 0.010328 * t * t) / (1 + 1.432788 * t + 0.189269 * t * t + 0.001308 * t * t * t);
        x = -x; // lower-tail quantile for pp
        for (int i = 0; i < 6; i++) {
            double f = pnorm(x) - pp, d = dnorm(x);
            if (d == 0) break;
            double step = f / d;
            x -= step / (1 + 0.5 * x * step);
        }
        return p < 0.5 ? x : -x;
    }

    public static double pt(double t, double df) {
        if (Double.isInfinite(df)) return pnorm(t);
        if (Double.isNaN(t) || df <= 0) return Double.NaN;
        double x = df / (df + t * t);
        double p = 0.5 * pbetaReg(x, df / 2, 0.5);
        return t > 0 ? 1 - p : p;
    }

    public static double qt(double p, double df) {
        if (Double.isInfinite(df)) return qnorm(p);
        if (p <= 0) return Double.NEGATIVE_INFINITY;
        if (p >= 1) return Double.POSITIVE_INFINITY;
        if (p == 0.5) return 0;
        boolean low = p < 0.5;
        double pp = low ? p : 1 - p; // upper tail prob for |t|
        // solve tail(t)=pp for t>0 where tail = 0.5*I_x(df/2,.5)
        double lo = 0, hi = 1;
        while (0.5 * pbetaReg(df / (df + hi * hi), df / 2, 0.5) > pp) { lo = hi; hi *= 2; if (hi > 1e300) break; }
        double t = brent(z -> 0.5 * pbetaReg(df / (df + z * z), df / 2, 0.5) - pp, lo, hi, 1e-14, 300);
        return low ? -t : t;
    }

    public static double pchisq(double x, double df) { return x <= 0 ? 0 : pgammaReg(df / 2, x / 2); }
    public static double pchisqUpper(double x, double df) { return x <= 0 ? 1 : qgammaUpper(df / 2, x / 2); }

    public static double qchisq(double p, double df) {
        if (p <= 0) return 0;
        if (p >= 1) return Double.POSITIVE_INFINITY;
        double lo = 0, hi = Math.max(1, df);
        while (pchisq(hi, df) < p) { lo = hi; hi *= 2; }
        return brent(z -> pchisq(z, df) - p, lo, hi, 1e-13, 300);
    }

    public static double pf(double f, double d1, double d2) {
        if (f <= 0) return 0;
        return pbetaReg(d1 * f / (d1 * f + d2), d1 / 2, d2 / 2);
    }
    public static double pfUpper(double f, double d1, double d2) {
        if (f <= 0) return 1;
        return pbetaReg(d2 / (d1 * f + d2), d2 / 2, d1 / 2);
    }

    public static double ptTwoSided(double t, double df) { return 2 * pt(-Math.abs(t), df); }
    public static double pnormTwoSided(double z) { return 2 * pnorm(-Math.abs(z)); }

    /** Brent's root finder on [a,b]; f(a), f(b) must have opposite signs. */
    public static double brent(DoubleUnaryOperator f, double a, double b, double tol, int maxit) {
        return brent(f, a, f.applyAsDouble(a), b, f.applyAsDouble(b), tol, maxit);
    }

    /** Brent's root finder when f(a) and f(b) are already known (saves two evaluations of an expensive f). */
    public static double brent(DoubleUnaryOperator f, double a, double fa, double b, double fb, double tol, int maxit) {
        if (fa == 0) return a;
        if (fb == 0) return b;
        double c = a, fc = fa, d = b - a, e = d;
        for (int i = 0; i < maxit; i++) {
            if ((fb > 0 && fc > 0) || (fb < 0 && fc < 0)) { c = a; fc = fa; d = b - a; e = d; }
            if (Math.abs(fc) < Math.abs(fb)) { a = b; b = c; c = a; fa = fb; fb = fc; fc = fa; }
            double tol1 = 2 * 2.2e-16 * Math.abs(b) + 0.5 * tol, xm = 0.5 * (c - b);
            if (Math.abs(xm) <= tol1 || fb == 0) return b;
            if (Math.abs(e) >= tol1 && Math.abs(fa) > Math.abs(fb)) {
                double s = fb / fa, p, q;
                if (a == c) { p = 2 * xm * s; q = 1 - s; }
                else {
                    double qq = fa / fc, r = fb / fc;
                    p = s * (2 * xm * qq * (qq - r) - (b - a) * (r - 1));
                    q = (qq - 1) * (r - 1) * (s - 1);
                }
                if (p > 0) q = -q;
                p = Math.abs(p);
                if (2 * p < Math.min(3 * xm * q - Math.abs(tol1 * q), Math.abs(e * q))) { e = d; d = p / q; }
                else { d = xm; e = d; }
            } else { d = xm; e = d; }
            a = b; fa = fb;
            b += Math.abs(d) > tol1 ? d : (xm > 0 ? tol1 : -tol1);
            fb = f.applyAsDouble(b);
        }
        return b;
    }

    /** Brent minimiser on [a,b]. */
    public static double fmin(DoubleUnaryOperator f, double a, double b, double tol) {
        double c = 0.5 * (3 - Math.sqrt(5)), eps = Math.sqrt(2.2e-16);
        double v = a + c * (b - a), w = v, x = v, d = 0, e = 0;
        double fx = f.applyAsDouble(x), fv = fx, fw = fx, tol3 = tol / 3;
        for (int it = 0; it < 500; it++) {
            double xm = 0.5 * (a + b), tol1 = eps * Math.abs(x) + tol3, t2 = 2 * tol1;
            if (Math.abs(x - xm) <= t2 - 0.5 * (b - a)) break;
            double p = 0, q = 0, r = 0;
            if (Math.abs(e) > tol1) {
                r = (x - w) * (fx - fv); q = (x - v) * (fx - fw); p = (x - v) * q - (x - w) * r;
                q = 2 * (q - r); if (q > 0) p = -p; else q = -q; r = e; e = d;
            }
            double u;
            if (Math.abs(p) >= Math.abs(0.5 * q * r) || p <= q * (a - x) || p >= q * (b - x)) {
                e = x < xm ? b - x : a - x; d = c * e;
            } else {
                d = p / q; u = x + d;
                if (u - a < t2 || b - u < t2) { d = tol1; if (x >= xm) d = -d; }
            }
            u = Math.abs(d) >= tol1 ? x + d : (d > 0 ? x + tol1 : x - tol1);
            double fu = f.applyAsDouble(u);
            if (fu <= fx) { if (u < x) b = x; else a = x; v = w; w = x; x = u; fv = fw; fw = fx; fx = fu; }
            else {
                if (u < x) a = u; else b = u;
                if (fu <= fw || w == x) { v = w; fv = fw; w = u; fw = fu; }
                else if (fu <= fv || v == x || v == w) { v = u; fv = fu; }
            }
        }
        return x;
    }
}
