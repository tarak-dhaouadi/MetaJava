package org.metajava.core;

/** Confidence intervals for a single proportion (meta::binconf methods). Returns null for "z". */
public final class PropCI {
    private PropCI() {}

    public static double[] interval(String m, double x, double n, double level) {
        double a = 1 - level, z = Dist.qnorm(1 - a / 2), p = x / n;
        switch (m) {
            case "z": case "NAsm": return null;
            case "CP": {
                double lo = x == 0 ? 0 : Dist.brent(q -> 1 - Dist.pbetaReg(q, x, n - x + 1) - 0 - (1 - a / 2) * 0 - (a / 2) + 0 * q, 1e-12, 1 - 1e-12, 1e-14, 300);
                // exact via beta quantiles
                lo = x == 0 ? 0 : qbeta(a / 2, x, n - x + 1);
                double up = x == n ? 1 : qbeta(1 - a / 2, x + 1, n - x);
                return new double[]{lo, up};
            }
            case "WS": {
                double c = z * z / n, mid = (p + c / 2) / (1 + c), hw = z / (1 + c) * Math.sqrt(p * (1 - p) / n + z * z / (4 * n * n));
                return new double[]{Math.max(0, mid - hw), Math.min(1, mid + hw)};
            }
            case "WSCC": {
                double den = 2 * (n + z * z);
                double lo = x == 0 ? 0 : (2 * x + z * z - 1 - z * Math.sqrt(z * z - 2 - 1 / n + 4 * p * (n * (1 - p) + 1))) / den;
                double up = x == n ? 1 : (2 * x + z * z + 1 + z * Math.sqrt(z * z + 2 - 1 / n + 4 * p * (n * (1 - p) - 1))) / den;
                return new double[]{Math.max(0, lo), Math.min(1, up)};
            }
            case "AC": {
                double nt = n + z * z, pt = (x + z * z / 2) / nt, hw = z * Math.sqrt(pt * (1 - pt) / nt);
                return new double[]{Math.max(0, pt - hw), Math.min(1, pt + hw)};
            }
            case "SA": { double hw = z * Math.sqrt(0.25 / n); return new double[]{Math.max(0, p - hw), Math.min(1, p + hw)}; }
            case "SM": { double hw = z * Math.sqrt(p * (1 - p) / n); return new double[]{Math.max(0, p - hw), Math.min(1, p + hw)}; }
            default: return null;
        }
    }

    public static double qbeta(double q, double a, double b) {
        return Dist.brent(x -> Dist.pbetaReg(x, a, b) - q, 0, 1, 1e-14, 300);
    }
}
