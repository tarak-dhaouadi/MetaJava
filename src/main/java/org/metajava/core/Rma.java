package org.metajava.core;

import java.util.Arrays;

/** Random/mixed-effects model fitting (rma.uni equivalent): tau2 estimators, confint for tau2, H/I2. */
public final class Rma {
    public double[] yi, vi; public double[][] X; public int k, p;
    public String method; public double tau2;
    public double[] beta, se; public double[][] vb;
    public double QE; public int dfQ;
    public double[] wTilde;
    /** GENQ / GENQM only: the weights A of the fit (metafor: rma(..., weights = A, method = "GENQ"/"GENQM")); null for every other estimator. */
    public double[] aw;
    /** (X'AX)^-1 of the weighted fit (aw != null); for the usual fit it is vb itself, (X'WX)^-1 with W = 1/(vi + tau2). */
    public double[][] bread;

    /**
     * y centred at its mean (metafor: Ymc = scale(yi, center = TRUE, scale = FALSE)). metafor's tau^2 estimators and the Q_E statistic use the quadratic form
     * Ymc' P Ymc, also for a model without an intercept, where it is not the residual sum of squares of y (P Ymc differs from P y when 1 is not in the column space of X).
     * The likelihood, the coefficients, the Knapp-Hartung scale factor and the tau^2 intervals (confint) use y itself, as in metafor.
     */
    private double[] ymc;

    private static double[] centred(double[] y) { double m = 0; for (double d : y) m += d; m /= y.length; double[] c = new double[y.length]; for (int i = 0; i < y.length; i++) c[i] = y[i] - m; return c; }

    private Rma() {}

    public static Rma fit(double[] y, double[] v, double[][] X, String method) { return fit(y, v, X, method, null); }

    public static Rma fit(double[] y, double[] v, double[][] X, String method, double[] genqWeights) {
        Rma m = new Rma();
        m.yi = y; m.vi = v; m.k = y.length; m.method = method;
        if (X == null) { X = new double[m.k][1]; for (double[] r : X) r[0] = 1; }
        m.X = X; m.p = X[0].length;
        double[] w0 = new double[m.k]; for (int i = 0; i < m.k; i++) w0[i] = 1 / v[i];
        m.ymc = centred(y); m.QE = m.k > m.p ? Math.max(0, rss(m.ymc, X, w0)) : 0; m.dfQ = m.k - m.p;   // metafor: QE <- max(0, Ymc' P Ymc)
        if (MetaSpec.isGenq(method)) { m.aw = genqW(genqWeights, w0).clone(); requireInvertible(X, m.aw); }
        m.tau2 = estimateTau2(m, m.aw);
        m.refit(m.tau2);
        return m;
    }

    /** metafor: .invcalc() stops (qr.solve: "singular matrix 'a' in solve") when sqrt(A) X is rank deficient, e.g. zero GENQ weights on all but one level of a moderator. */
    private static void requireInvertible(double[][] X, double[] a) {
        int k = X.length, p = X[0].length; double[][] xtax = new double[p][p];
        for (int i = 0; i < k; i++) for (int s = 0; s < p; s++) for (int t = 0; t < p; t++) xtax[s][t] += X[i][s] * a[i] * X[i][t];
        if (Lin.inv(xtax) == null) throw new IllegalArgumentException("Singular design matrix: X'AX is not invertible with the GENQ / GENQM weights (zero weights leave a moderator without variation).");
    }

    /** Fit with a given tau2 (metafor: rma(..., tau2 = value)); the weights of GENQ / GENQM are those of the fit. */
    public static Rma withTau2(double[] y, double[] v, double[][] X, String method, double[] genqWeights, double t2) {
        Rma m = new Rma();
        m.yi = y; m.vi = v; m.k = y.length; m.method = method;
        if (X == null) { X = new double[m.k][1]; for (double[] r : X) r[0] = 1; }
        m.X = X; m.p = X[0].length;
        double[] w0 = new double[m.k]; for (int i = 0; i < m.k; i++) w0[i] = 1 / v[i];
        m.ymc = centred(y); m.QE = m.k > m.p ? Math.max(0, rss(m.ymc, X, w0)) : 0; m.dfQ = m.k - m.p;   // metafor: QE <- max(0, Ymc' P Ymc)
        if (MetaSpec.isGenq(method)) m.aw = genqW(genqWeights, w0).clone();
        m.tau2 = t2; m.refit(t2);
        return m;
    }

    /** Coefficients of the fit with the weights 1/(vi + tau2) (what the Knapp-Hartung scale factor is based on, also when the model is fitted with the weights of GENQ / GENQM). */
    public double[] betaW() {
        if (aw == null) return beta;
        double[][] xtwx = new double[p][p]; double[] xtwy = new double[p];
        for (int i = 0; i < k; i++) for (int a = 0; a < p; a++) { xtwy[a] += X[i][a] * wTilde[i] * yi[i]; for (int b = 0; b < p; b++) xtwx[a][b] += X[i][a] * wTilde[i] * X[i][b]; }
        return Lin.mulv(Lin.inv(xtwx), xtwy);
    }

    /** Weights the model was fitted with: A for GENQ / GENQM, 1/(vi + tau2) otherwise. */
    public double[] fitWeights() { return aw != null ? aw : wTilde; }

    /** Hat values: diagonal of H = X (X'AX)^-1 X'A (A = 1/(vi + tau2) for the usual fit). */
    public double[] hatDiag() {
        double[] a = fitWeights(); double[][] br = bread != null ? bread : vb; double[] h = new double[k];
        for (int i = 0; i < k; i++) { double s = 0; for (int c = 0; c < p; c++) for (int d = 0; d < p; d++) s += X[i][c] * br[c][d] * X[i][d]; h[i] = s * a[i]; }
        return h;
    }

    /** y'Py with P = W - WX(X'WX)^-1 X'W. */
    static double rss(double[] y, double[][] X, double[] w) {
        int k = y.length, p = X[0].length;
        double[][] xtwx = new double[p][p]; double[] xtwy = new double[p];
        for (int i = 0; i < k; i++) for (int a = 0; a < p; a++) { xtwy[a] += X[i][a] * w[i] * y[i]; for (int b = 0; b < p; b++) xtwx[a][b] += X[i][a] * w[i] * X[i][b]; }
        double[][] inv = Lin.inv(xtwx); if (inv == null) return Double.NaN;
        double[] b = Lin.mulv(inv, xtwy);
        double s = 0; for (int i = 0; i < k; i++) { double f = 0; for (int a = 0; a < p; a++) f += X[i][a] * b[a]; s += w[i] * (y[i] - f) * (y[i] - f); }
        return s;
    }

    static double trP(double[][] X, double[] w) {
        int k = X.length, p = X[0].length; double sw = 0; for (double x : w) sw += x;
        double[][] xtwx = new double[p][p];
        for (int i = 0; i < k; i++) for (int a = 0; a < p; a++) for (int b = 0; b < p; b++) xtwx[a][b] += X[i][a] * w[i] * X[i][b];
        double[][] inv = Lin.inv(xtwx);
        double t = 0;
        for (int i = 0; i < k; i++) for (int a = 0; a < p; a++) for (int b = 0; b < p; b++) t += w[i] * w[i] * X[i][a] * inv[a][b] * X[i][b];
        double sw1 = 0; for (double x : w) sw1 += x;
        return sw1 - t;
    }

    void refit(double t2) {
        wTilde = new double[k]; for (int i = 0; i < k; i++) wTilde[i] = 1 / (vi[i] + t2);
        double[] a = aw != null ? aw : wTilde;
        double[][] xtwx = new double[p][p]; double[] xtwy = new double[p];
        for (int i = 0; i < k; i++) for (int c = 0; c < p; c++) { xtwy[c] += X[i][c] * a[i] * yi[i]; for (int b = 0; b < p; b++) xtwx[c][b] += X[i][c] * a[i] * X[i][b]; }
        double[][] inv = Lin.inv(xtwx); beta = Lin.mulv(inv, xtwy); bread = inv;
        if (aw == null) vb = inv;
        else {   // metafor: vb = (X'AX)^-1 X'A M A X (X'AX)^-1, M = diag(vi + tau2)
            double[][] mid = new double[p][p];
            for (int i = 0; i < k; i++) { double f = a[i] * a[i] * (vi[i] + t2); for (int c = 0; c < p; c++) for (int b = 0; b < p; b++) mid[c][b] += f * X[i][c] * X[i][b]; }
            double[][] tmp = new double[p][p]; vb = new double[p][p];
            for (int c = 0; c < p; c++) for (int b = 0; b < p; b++) { double t = 0; for (int d = 0; d < p; d++) t += inv[c][d] * mid[d][b]; tmp[c][b] = t; }
            for (int c = 0; c < p; c++) for (int b = 0; b < p; b++) { double t = 0; for (int d = 0; d < p; d++) t += tmp[c][d] * inv[d][b]; vb[c][b] = t; }
        }
        se = new double[p]; for (int c = 0; c < p; c++) se[c] = Math.sqrt(vb[c][c]);
    }

    private static double estimateTau2(Rma m, double[] gw) {
        int k = m.k, p = m.p; double[] y = m.ymc, v = m.vi; double[][] X = m.X;   // y = Ymc, the centred y (see ymc)
        double[] w = new double[k]; double sw = 0; for (int i = 0; i < k; i++) { w[i] = 1 / v[i]; sw += w[i]; }
        switch (m.method) {
            case "FE": return 0;
            case "DL": return Math.max(0, (m.QE - (k - p)) / trP(X, w));
            case "HS": return Math.max(0, (m.QE - k) / sw);
            case "HSk": return Math.max(0, ((double) k / (k - p) * m.QE - k) / sw);   // metafor: (k/(k-p) * RSS - k) / sum(1/vi), Brannick et al. (2019)
            case "HE": {
                double[] one = new double[k]; Arrays.fill(one, 1);
                double rssu = rss(y, X, one);
                // tr[(I-H)V]
                double[][] xtx = new double[p][p];
                for (int i = 0; i < k; i++) for (int a = 0; a < p; a++) for (int b = 0; b < p; b++) xtx[a][b] += X[i][a] * X[i][b];
                double[][] inv = Lin.inv(xtx); double tr = 0;
                for (int i = 0; i < k; i++) { double h = 0; for (int a = 0; a < p; a++) for (int b = 0; b < p; b++) h += X[i][a] * inv[a][b] * X[i][b]; tr += (1 - h) * v[i]; }
                return Math.max(0, (rssu - tr) / (k - p));
            }
            case "SJ": {
                double t0 = sjStart(y);
                double[] wt = new double[k]; for (int i = 0; i < k; i++) wt[i] = 1 / (v[i] + t0);
                return Math.max(0, t0 * rss(y, X, wt) / (k - p));
            }
            case "DLIT": {   // metafor: DL estimate iterated from tau^2 = 0 (weights 1/(vi + tau^2)) until |change| <= 1e-5 (control: threshold, maxiter = 100); tau^2 >= tau2.min = 0 in every step
                double t = 0, change = THRESHOLD + 1; int iter = 0;
                while (change > THRESHOLD) {
                    iter++; double old = t; double[] wt = new double[k]; for (int i = 0; i < k; i++) wt[i] = 1 / (v[i] + t);
                    t = Math.max(0, genqTau2(y, v, X, wt)); change = Math.abs(old - t);
                    if (iter > MAXITER) throw new IllegalArgumentException("Iterative DL estimator did not converge.");
                }
                return t;
            }
            case "SJIT": {   // metafor: SJ iterated from the SJ starting value var(y) (k-1)/k: tau^2 <- tau^2 * RSS / (k - p) with weights 1/(vi + tau^2)
                double t = sjStart(y), change = THRESHOLD + 1; int iter = 0;
                while (change > THRESHOLD) {
                    iter++; double old = t; double[] wt = new double[k]; for (int i = 0; i < k; i++) wt[i] = 1 / (v[i] + t);
                    t = t * rss(y, X, wt) / (k - p); change = Math.abs(old - t);
                    if (iter > MAXITER) throw new IllegalArgumentException("Iterative SJ estimator did not converge.");
                }
                return Math.max(0, t);
            }
            case "PMM": {    // metafor: median-unbiased Paule-Mandel, Q(tau^2) = qchisq(0.5, k - p) (uniroot, tol = eps^0.25)
                final double target = Dist.qchisq(0.5, k - p);
                java.util.function.DoubleUnaryOperator f = t -> { double[] wt = new double[k]; for (int i = 0; i < k; i++) wt[i] = 1 / (v[i] + t); return rss(y, X, wt) - target; };
                double flo = f.applyAsDouble(0); if (flo < 0) return 0;
                double hi = tau2MaxMetafor(y), fhi = f.applyAsDouble(hi);
                if (fhi > 0) throw new IllegalArgumentException("Value of 'tau2.max' too low (PMM): the median-unbiased Paule-Mandel estimate exceeds " + hi + ". Switch to another tau\u00B2 estimator.");
                return Dist.brent(f, 0, flo, hi, fhi, Math.pow(2.220446049250313e-16, 0.25), 100);
            }
            case "PM": case "MP": case "EB": {   // MP (Mandel-Paule) is the same estimator as PM
                java.util.function.DoubleUnaryOperator f = t -> { double[] wt = new double[k]; for (int i = 0; i < k; i++) wt[i] = 1 / (v[i] + t); return rss(y, X, wt) - (k - p); };
                if (f.applyAsDouble(0) <= 0) return 0;
                double hi = 1; while (f.applyAsDouble(hi) > 0 && hi < 1e12) hi *= 2;
                return Dist.brent(f, 0, hi, 1e-12, 500);
            }
            case "ML": case "REML": {
                boolean reml = m.method.equals("REML");
                java.util.function.DoubleUnaryOperator sc = t -> score(m, m.ymc, t, reml);   // metafor: Fisher scoring with Ymc
                if (sc.applyAsDouble(0) <= 0) return 0;
                double hi = 1; while (sc.applyAsDouble(hi) > 0 && hi < 1e12) hi *= 2;
                return Dist.brent(sc, 0, hi, 1e-12, 500);
            }
            case "GENQ": return Math.max(0, genqTau2(y, v, X, genqW(gw, w)));
            case "GENQM": return genqmTau2(y, v, X, genqW(gw, w));
            default: throw new IllegalArgumentException("Unknown tau2 estimator: " + m.method);
        }
    }

    /** Convergence threshold and maximum number of iterations of the iterative estimators (metafor control defaults: threshold = 1e-5, maxiter = 100). */
    static final double THRESHOLD = 1e-5; static final int MAXITER = 100;

    /** metafor: var(y) (k-1)/k, the starting value of SJ / SJIT. */
    static double sjStart(double[] y) { int k = y.length; double mean = 0; for (double d : y) mean += d; mean /= k; double ss = 0; for (double d : y) ss += (d - mean) * (d - mean); return ss / (k - 1) * (k - 1) / k; }

    /**
     * Standard error of the tau^2 estimate, as the se.tau2 of metafor's rma.uni (notes.pdf): the large-sample variance of the estimator,
     * evaluated at max(tau^2, 0). NaN for the common-effect model and when k = p.
     */
    public double seTau2() {
        final int df = k - p; if (df <= 0 || Double.isNaN(tau2)) return Double.NaN;
        final double t = Math.max(tau2, 0); double[] w = new double[k]; double[][] P;
        switch (method) {
            case "HS": case "HSk": {   // P and the weights are those of 1/vi
                double sw = 0; for (int i = 0; i < k; i++) { w[i] = 1 / vi[i]; sw += w[i]; }
                P = Reg.pmat(X, w); double r = Math.sqrt(1 / (sw * sw) * (2 * df + 4 * t * trace(P) + 2 * t * t * sumSq(P, null, null)));
                return method.equals("HSk") ? (double) k / df * r : r;
            }
            case "DL": {
                for (int i = 0; i < k; i++) w[i] = 1 / vi[i];
                P = Reg.pmat(X, w); double trP = trace(P); return Math.sqrt(1 / (trP * trP) * (2 * df + 4 * t * trP + 2 * t * t * sumSq(P, null, null)));
            }
            case "HE": {   // P = I - X (X'X)^-1 X'; PV = P V
                Arrays.fill(w, 1); P = Reg.pmat(X, w); double trPV = 0; for (int i = 0; i < k; i++) trPV += P[i][i] * vi[i];
                return Math.sqrt(1.0 / ((double) df * df) * (2 * sumSq(P, vi, vi) + 4 * t * trPV + 2 * t * t * df));
            }
            case "GENQ": case "GENQM": {   // P with the weights A of the fit: sum(PV * t(PV)) = sum_ij P_ij^2 v_i v_j, sum(PV * P) = sum_ij P_ij^2 v_j
                P = Reg.pmat(X, aw); double trP = trace(P);
                return Math.sqrt(1 / (trP * trP) * (2 * sumSq(P, vi, vi) + 4 * t * sumSq(P, null, vi) + 2 * t * t * sumSq(P, null, null)));
            }
            case "SJ": {   // P with the weights 1/(vi + tau2.0), tau2.0 = var(y) (k-1)/k
                double t0 = sjStart(yi); for (int i = 0; i < k; i++) w[i] = 1 / (vi[i] + t0);
                P = Reg.pmat(X, w); return Math.sqrt(t0 * t0 / ((double) df * df) * (2 * sumSq(P, vi, vi) + 4 * t * sumSq(P, null, vi) + 2 * t * t * sumSq(P, null, null)));
            }
            case "ML": { double s = 0; for (int i = 0; i < k; i++) { double wi = 1 / (vi[i] + t); s += wi * wi; } return Math.sqrt(2 / s); }
            case "REML": { for (int i = 0; i < k; i++) w[i] = 1 / (vi[i] + t); P = Reg.pmat(X, w); return Math.sqrt(2 / sumSq(P, null, null)); }
            case "EB": case "PM": case "MP": case "PMM": case "DLIT": case "SJIT": {
                double sw = 0; for (int i = 0; i < k; i++) sw += 1 / (vi[i] + t);
                return Math.sqrt(2.0 * k * k / df / (sw * sw));
            }
            default: return Double.NaN;
        }
    }

    private static double trace(double[][] P) { double s = 0; for (int i = 0; i < P.length; i++) s += P[i][i]; return s; }
    /** sum_ij P_ij^2 a_i b_j (a, b = null: 1). */
    private static double sumSq(double[][] P, double[] a, double[] b) {
        double s = 0; for (int i = 0; i < P.length; i++) for (int j = 0; j < P.length; j++) s += P[i][j] * P[i][j] * (a == null ? 1 : a[i]) * (b == null ? 1 : b[j]); return s;
    }

    /** Set by {@link #higherMode()}: log-likelihood of the higher mode minus that of the reported estimate (NaN when there is no higher mode). */
    public double higherModeGain = Double.NaN;
    /** Smallest log-likelihood difference reported as a higher mode; far above the rounding error of the likelihood (about 1e-12 for these sizes), far below anything that matters. */
    public static final double MODE_MIN_GAIN = 1e-6;

    /**
     * Diagnostic for ML / REML (it never changes the estimate). The estimate is the root of the score equation found by the usual search from tau2 = 0, the one
     * metafor-compatible results rest on; the score equation can have several roots, so that root can be a local maximum of the (restricted) likelihood.
     * This method looks at <i>the same</i> likelihood that the score is the derivative of ({@link #logLik}: REML or ML, as selected) over the whole admissible range
     * tau2 >= 0: tau2 = 0 and 240 geometrically spaced points from 1e-9 to 1e9 times the scale of the data (the larger of the mean sampling variance, the variance of the
     * effects and the estimate), beyond which the likelihood only falls. Every local maximum of that grid is refined on the score equation (or taken as the boundary
     * tau2 = 0 when the score is not positive there), so that only a verified stationary point or the boundary is compared. A higher mode is reported only if its
     * log-likelihood exceeds that of the estimate by more than {@link #MODE_MIN_GAIN} and its tau2 differs from the estimate. Returns the tau2 of the highest such mode,
     * and sets {@link #higherModeGain}; NaN (and a NaN gain) when there is none or the estimator is not ML / REML. About 250 likelihood evaluations, so it is not run
     * for each GOSH subset.
     */
    public double higherMode() {
        higherModeGain = Double.NaN;
        if (!method.equals("ML") && !method.equals("REML")) return Double.NaN;
        final boolean reml = method.equals("REML"); final Rma m = this; java.util.function.DoubleUnaryOperator sc = t -> score(m, m.yi, t, reml);   // the likelihood itself: y, not Ymc
        final double t0 = tau2, l0 = logLik(m, t0, reml); if (Double.isNaN(l0) || Double.isNaN(t0)) return Double.NaN;
        double vs = 0; for (double x : vi) vs += x; vs /= k; double ym = 0; for (double x : yi) ym += x; ym /= k; double yv = 0; for (double x : yi) yv += (x - ym) * (x - ym); yv /= Math.max(1, k - 1);
        final double sc0 = Math.max(Math.max(vs, yv), Math.max(t0, 1e-300)); final int G = 240; final double lo = 1e-9 * sc0, hi = 1e9 * sc0, step = Math.pow(hi / lo, 1.0 / (G - 1));
        double[] t = new double[G + 1], l = new double[G + 1]; t[0] = 0; l[0] = logLik(m, 0, reml); for (int j = 1; j <= G; j++) { t[j] = lo * Math.pow(step, j - 1); l[j] = logLik(m, t[j], reml); }
        double bestT = Double.NaN, bestL = l0;
        for (int j = 0; j <= G; j++) {
            if (Double.isNaN(l[j])) continue;
            boolean left = j == 0 || Double.isNaN(l[j - 1]) || l[j] >= l[j - 1], right = j == G || Double.isNaN(l[j + 1]) || l[j] > l[j + 1]; if (!(left && right)) continue;   // local maximum of the grid
            double cand;
            if (j == 0) { if (sc.applyAsDouble(0) > 0) continue; cand = 0; }   // boundary: the likelihood does not rise to the right of 0
            else if (j == G) continue;                                          // maximum at the end of the range: not a verified stationary point
            else { double a = t[j - 1], b = t[j + 1]; if (!(sc.applyAsDouble(a) > 0 && sc.applyAsDouble(b) < 0)) { if (j == 1 && sc.applyAsDouble(0) <= 0) cand = 0; else continue; } else cand = Dist.brent(sc, a, b, 1e-12, 500); }
            double lc = logLik(m, cand, reml); if (Double.isNaN(lc) || Math.abs(cand - t0) <= 1e-8 * sc0) continue;
            if (lc > bestL + MODE_MIN_GAIN) { bestL = lc; bestT = cand; }
        }
        if (!Double.isNaN(bestT)) higherModeGain = bestL - l0;
        return bestT;
    }

    /** (Restricted) log-likelihood of the random-effects model at tau2 = t, up to a constant. */
    public static double logLik(Rma m, double t, boolean reml) { return logLik(m, t, reml, false); }

    /**
     * As {@link #logLik(Rma, double, boolean)}; with {@code weighted} and a model fitted with weights (GENQ / GENQM with user weights) the coefficients are those of the fit,
     * i.e. the weighted ones, whatever tau^2, as metafor's logLik() of an rma.uni fit with weights (used by the profile-likelihood interval, which refits the model with the same weights).
     */
    public static double logLik(Rma m, double t, boolean reml, boolean weighted) {
        int k = m.k, p = m.p; double[] w = new double[k]; double sl = 0; for (int i = 0; i < k; i++) { w[i] = 1 / (m.vi[i] + t); sl += Math.log(m.vi[i] + t); }
        double[][] A = new double[p][p]; double[] b = new double[p];
        for (int i = 0; i < k; i++) for (int a = 0; a < p; a++) { b[a] += m.X[i][a] * w[i] * m.yi[i]; for (int c = 0; c < p; c++) A[a][c] += m.X[i][a] * w[i] * m.X[i][c]; }
        double[][] inv = Lin.inv(A); if (inv == null) return Double.NaN; double[] be = weighted && m.aw != null ? m.beta : Lin.mulv(inv, b); double rss = 0;
        for (int i = 0; i < k; i++) { double f = 0; for (int a = 0; a < p; a++) f += m.X[i][a] * be[a]; rss += w[i] * (m.yi[i] - f) * (m.yi[i] - f); }
        double ld = 0;
        if (reml) { double[][] M = new double[p][]; for (int a = 0; a < p; a++) M[a] = A[a].clone(); for (int a = 0; a < p; a++) { int pv = a; for (int r = a + 1; r < p; r++) if (Math.abs(M[r][a]) > Math.abs(M[pv][a])) pv = r; double[] tmp = M[a]; M[a] = M[pv]; M[pv] = tmp;
            ld += Math.log(Math.abs(M[a][a])); for (int r = a + 1; r < p; r++) { double f = M[r][a] / M[a][a]; for (int c = a; c < p; c++) M[r][c] -= f * M[a][c]; } } }
        return -0.5 * (sl + ld + rss);
    }

    /** Weights of the generalised Q-statistic estimators; metafor requires them to be specified, here the inverse sampling variances (GENQ = DL) when none are given. */
    private static double[] genqW(double[] gw, double[] inv) {
        if (gw == null) return inv;
        for (double x : gw) if (Double.isNaN(x) || Double.isInfinite(x)) throw new IllegalArgumentException("Weights must be finite numbers.");
        for (double x : gw) if (x < 0) throw new IllegalArgumentException("Negative weights not allowed.");
        return gw;
    }

    /** tau2.max of metafor's control list: max(100, 10 * mad(yi)^2), mad = 1.4826 * median(|yi - median(yi)|). */
    static double tau2MaxMetafor(double[] y) {
        double[] t = y.clone(); Arrays.sort(t); double med = median(t); double[] d = new double[t.length];
        for (int i = 0; i < t.length; i++) d[i] = Math.abs(y[i] - med);
        Arrays.sort(d); double mad = 1.4826 * median(d);
        return Math.max(100, 10 * mad * mad);
    }
    private static double median(double[] sorted) { int n = sorted.length; return n % 2 == 1 ? sorted[n / 2] : 0.5 * (sorted[n / 2 - 1] + sorted[n / 2]); }

    /**
     * Median-unbiased generalised Q-statistic estimator (GENQM, Viechtbauer 2021) exactly as metafor: the tau2 for which the observed generalised Q statistic y'P_a y is the
     * median of its distribution, i.e. P(sum lambda_j chi2_1 <= Q) = 0.5 with lambda the k-p eigenvalues of S^(1/2) P_a S^(1/2), S = diag(vi + tau2).
     * The root is searched in [tau2.min = 0, tau2.max] with R's uniroot defaults (tol = .Machine$double.eps^0.25, 100 iterations); Q at or below the median
     * for tau2 = 0 gives 0, and a median not reached at tau2.max is an error (as in metafor: "Value of 'tau2.max' too low").
     */
    static double genqmTau2(double[] y, double[] v, double[][] X, double[] a) {
        final double Q = rss(y, X, a); final GenqCtx ctx = new GenqCtx(a, v, X);
        java.util.function.DoubleUnaryOperator cdfMinusHalf = t -> quadCdf(Q, ctx.lambdas(t)) - 0.5;   // metafor: (1 - Qq) - level with level = 0.5, Qq the upper tail
        final double lo = 0, hi = tau2MaxMetafor(y);
        double flo = cdfMinusHalf.applyAsDouble(lo);
        if (1 - (flo + 0.5) > 0.5) return lo;                      // upper tail at tau2.min > 0.5: estimate below tau2.min
        double fhi = cdfMinusHalf.applyAsDouble(hi);
        if (1 - (fhi + 0.5) < 0.5) throw new IllegalArgumentException("Value of 'tau2.max' too low (GENQM): the median-unbiased estimate exceeds " + hi + ". Switch to another tau\u00B2 estimator.");
        return Dist.brent(cdfMinusHalf, lo, flo, hi, fhi, Math.pow(2.220446049250313e-16, 0.25), 100);
    }

    static double genqTau2(double[] y, double[] v, double[][] X, double[] a) {
        int k = y.length;
        double q = rss(y, X, a), tr = trP(X, a);
        // tr(P_a V)
        int p = X[0].length; double[][] xtax = new double[p][p];
        for (int i = 0; i < k; i++) for (int s = 0; s < p; s++) for (int t = 0; t < p; t++) xtax[s][t] += X[i][s] * a[i] * X[i][t];
        double[][] inv = Lin.inv(xtax); double trPV = 0;
        for (int i = 0; i < k; i++) { double h = 0; for (int s = 0; s < p; s++) for (int t = 0; t < p; t++) h += X[i][s] * inv[s][t] * X[i][t]; trPV += (a[i] - a[i] * a[i] * h) * v[i]; }
        return (q - trPV) / tr;
    }

    private static double score(Rma m, double[] y, double t, boolean reml) {
        int k = m.k, p = m.p; double[] w = new double[k];
        for (int i = 0; i < k; i++) w[i] = 1 / (m.vi[i] + t);
        double[][] xtwx = new double[p][p]; double[] xtwy = new double[p];
        for (int i = 0; i < k; i++) for (int a = 0; a < p; a++) { xtwy[a] += m.X[i][a] * w[i] * y[i]; for (int b = 0; b < p; b++) xtwx[a][b] += m.X[i][a] * w[i] * m.X[i][b]; }
        double[][] inv = Lin.inv(xtwx); double[] b = Lin.mulv(inv, xtwy);
        double s = 0;
        for (int i = 0; i < k; i++) { double f = 0; for (int a = 0; a < p; a++) f += m.X[i][a] * b[a]; double r = y[i] - f; s += w[i] * w[i] * r * r; }
        double sw = 0; for (double x : w) sw += x;
        double tr = sw;
        if (reml) { double t2 = 0; for (int i = 0; i < k; i++) for (int a = 0; a < p; a++) for (int c = 0; c < p; c++) t2 += w[i] * w[i] * m.X[i][a] * inv[a][c] * m.X[i][c]; tr = sw - t2; }
        return 0.5 * (s - tr);
    }

    /** Generalised Q statistic for given weights a and tau2, as y'P_a y. */
    static double genQ(double[] y, double[][] X, double[] a) { return rss(y, X, a); }

    public static final class TauCI {
        public double lbTau2, ubTau2, lbTau, ubTau; public String lbSign = "", ubSign = ""; public String method;
        public double lbH2, ubH2, lbI2, ubI2;
        TauCI copy() { TauCI c = new TauCI(); c.lbTau2 = lbTau2; c.ubTau2 = ubTau2; c.lbTau = lbTau; c.ubTau = ubTau; c.lbSign = lbSign; c.ubSign = ubSign; c.method = method; c.lbH2 = lbH2; c.ubH2 = ubH2; c.lbI2 = lbI2; c.ubI2 = ubI2; return c; }
    }

    /** CI for tau2: "QP" (Q-profile), "BJ" (GENQ w=1/v), "J" (GENQ w=1/sqrt(v)), "GENQ" (generalised Q-statistic interval with the weights of this fit, GENQ / GENQM models only: the default of metafor's confint() for them). Results are memoised (see {@link CiKey}). */
    public TauCI confint(String ciMethod, double level) {
        if (ciMethod.equals("GENQ") && aw == null) throw new IllegalArgumentException("The generalised Q-statistic interval with the weights of the model needs tau^2 to be estimated by GENQ or GENQM (metafor: model must be fitted with method = \"GENQ\" or \"GENQM\"); use QP, BJ or J instead.");
        CiKey key = new CiKey(ciMethod + (isFitBased(ciMethod) ? "|" + method + "|" + Double.doubleToLongBits(tau2) : ""), level, yi, vi, X, ciMethod.equals("GENQ") ? aw : null);
        synchronized (CI_CACHE) { TauCI c = CI_CACHE.get(key); if (c != null) return c.copy(); }
        TauCI r = computeConfint(ciMethod, level);
        synchronized (CI_CACHE) { CI_CACHE.put(key, r.copy()); }
        return r;
    }

    private TauCI computeConfint(String ciMethod, double level) {
        TauCI r = new TauCI(); r.method = ciMethod;
        double alpha = 1 - level;
        int df = k - p;
        final boolean qp = ciMethod.equals("QP");
        if (isFitBased(ciMethod)) {
            metaforTypeCi(r, ciMethod, alpha);
        } else if (qp) {
            double cl = Dist.qchisq(1 - alpha / 2, df), cu = Dist.qchisq(alpha / 2, df);
            java.util.function.DoubleUnaryOperator qf = t -> { double[] wt = new double[k]; for (int i = 0; i < k; i++) wt[i] = 1 / (vi[i] + t); return rss(yi, X, wt); };
            r.lbTau2 = qf.applyAsDouble(0) < cl ? 0 : solve(t -> qf.applyAsDouble(t) - cl);
            r.ubTau2 = qf.applyAsDouble(0) < cu ? 0 : solve(t -> qf.applyAsDouble(t) - cu);
        } else {
            double[] cw = new double[k];   // weights of the generalised Q statistic: BJ 1/v, J 1/sqrt(v), GENQ the weights of the fit
            for (int i = 0; i < k; i++) cw[i] = ciMethod.equals("GENQ") ? aw[i] : ciMethod.equals("J") ? 1 / Math.sqrt(vi[i]) : 1 / vi[i];
            final double qObs = rss(yi, X, cw);
            // The eigenvalues of S^(1/2) P_a S^(1/2) (S = diag(v + tau2)) are, apart from p zeros, those of the (k-p)x(k-p) matrix
            // B0 + tau2*B1 that is set up once here; the Imhof integrand is evaluated with a complex running product.
            final GenqCtx ctx = new GenqCtx(cw, vi, X);
            java.util.function.DoubleUnaryOperator cdf = t -> quadCdf(qObs, ctx.lambdas(t));
            double pl = 1 - alpha / 2, pu = alpha / 2;
            final double c0 = cdf.applyAsDouble(0);   // shared by both bounds
            // starting scale for the bracketing of the roots: the DerSimonian-Laird estimate (data only, so the result never depends on the fit)
            double[] w0 = new double[k]; for (int i = 0; i < k; i++) w0[i] = 1 / vi[i];
            double scale = Math.max(Math.max(0, (rss(yi, X, w0) - (k - p)) / trP(X, w0)), 0.05 * typicalV(k, p, vi, X));
            final double sc = Math.max(scale, 1e-12); final double[] out = new double[2];
            // the two bounds are independent root searches: run them concurrently (sequential when already inside a pool worker)
            Par.all(java.util.List.<Runnable>of(
                () -> out[0] = c0 < pl ? 0 : solveDecreasing(cdf, pl, c0, sc),
                () -> out[1] = c0 < pu ? 0 : solveDecreasing(cdf, pu, c0, 3 * sc)));
            r.lbTau2 = out[0]; r.ubTau2 = out[1];
        }
        r.lbTau = Math.sqrt(r.lbTau2); r.ubTau = Math.sqrt(r.ubTau2);
        double sv = typicalV(k, p, vi, X);
        r.lbH2 = (r.lbTau2 + sv) / sv; r.ubH2 = (r.ubTau2 + sv) / sv;
        r.lbI2 = r.lbTau2 / (r.lbTau2 + sv); r.ubI2 = r.ubTau2 / (r.ubTau2 + sv);
        return r;
    }

    /** Interval types of metafor's confint.rma.uni that depend on the fitted estimator (its tau^2, its likelihood, its se.tau2), not only on the data. */
    public static boolean isFitBased(String ciMethod) { return ciMethod.equals("PL") || ciMethod.equals("HT") || ciMethod.equals("Wald") || ciMethod.equals("Wald.log") || ciMethod.equals("Wald.sqrt"); }

    /**
     * confint.rma.uni(type = "pl" / "ht" / "wald" / "wald.log" / "wald.sqrt") exactly as in metafor 4.6-0, for the model fitted with <i>this</i> estimator:
     * PL profiles the (REML for method REML, otherwise ML) log-likelihood with tau^2 fixed, objective qchisq(1 - alpha, 1), uniroot (tol = eps^0.25, maxiter = 1000) on
     * [tau2.min, tau2] and [tau2, tau2.max], tau2.min = min(0, tau2), tau2.max = max(100, 10 tau2, 10 tau2.min); a limit that is not crossed is set to tau2.min / tau2.max (sign "<" / ">").
     * The Wald types use se.tau2 of the estimator ({@link #seTau2()}); HT is the Higgins-Thompson interval for H^2 transformed to tau^2 (intercept-only models, k > 2).
     */
    private void metaforTypeCi(TauCI r, String type, double alpha) {
        final double crit = Dist.qnorm(1 - alpha / 2), t = tau2; double lb = Double.NaN, ub = Double.NaN;
        switch (type) {
            case "PL": {
                final boolean reml = method.equals("REML"); final double objective = Dist.qchisq(1 - alpha, 1), ll0 = logLik(this, t, reml, true), tol = Math.pow(2.220446049250313e-16, 0.25);
                java.util.function.DoubleUnaryOperator f = x -> -2 * (logLik(this, x, reml, true) - ll0) - objective;   // metafor .profile.rma.uni(confint = TRUE)
                final double tmin = Math.min(0, t), tmax = Math.max(100, Math.max(t * 10, tmin * 10));
                double f0 = f.applyAsDouble(tmin);
                if (!Double.isNaN(f0)) { if (f0 < 0) { lb = tmin; if (tmin > 0) r.lbSign = "<"; } else lb = Dist.brent(f, tmin, f0, t, f.applyAsDouble(t), tol, 1000); }
                double f1 = f.applyAsDouble(tmax);
                if (!Double.isNaN(f1)) { if (f1 < 0) { ub = tmax; r.ubSign = ">"; } else ub = Dist.brent(f, t, f.applyAsDouble(t), tmax, f1, tol, 1000); }
                break;
            }
            case "HT": {
                boolean intOnly = p == 1; for (int i = 0; i < k && intOnly; i++) intOnly = X[i][0] == 1;
                if (!intOnly) throw new IllegalArgumentException("The Higgins-Thompson interval (HT) is only applicable to models without moderators.");
                if (k <= 2) throw new IllegalArgumentException("The Higgins-Thompson interval (HT) is only applicable when k > 2.");
                double vt = typicalV(k, p, vi, X), H2 = t / vt + 1;
                double seLnH = QE > k ? 0.5 * (Math.log(QE) - Math.log(k - 1)) / (Math.sqrt(2 * QE) - Math.sqrt(2.0 * k - 3)) : Math.sqrt(1.0 / (2.0 * (k - 2)) * (1 - 1 / (3.0 * (k - 2) * (k - 2))));
                double h2lb = Math.exp(Math.log(H2) - crit * 2 * seLnH), h2ub = Math.exp(Math.log(H2) + crit * 2 * seLnH);   // SE[log(H^2)] = 2 SE[log(H)]
                double i2lb = (h2lb - 1) / h2lb, i2ub = (h2ub - 1) / h2ub;
                lb = Math.max(0, i2lb * vt / (1 - i2lb)); ub = i2ub * vt / (1 - i2ub);
                break;
            }
            case "Wald": { double se = seTau2(); lb = Math.max(0, t - crit * se); ub = t + crit * se; break; }
            case "Wald.log": {
                if (t >= 0) { double se = seTau2(); lb = Math.exp(Math.log(t) - crit * se / t); ub = Math.exp(Math.log(t) + crit * se / t); ub = Math.max(t, ub); }   // as R: NaN for tau^2 = 0 (-Inf + Inf)
                break;
            }
            case "Wald.sqrt": {
                if (t >= 0) { double se = seTau2(), st = Math.sqrt(t); lb = Math.pow(Math.max(0, st - crit * se / (2 * st)), 2); ub = Math.pow(st + crit * se / (2 * st), 2); }
                break;
            }
            default: throw new IllegalArgumentException("Unknown tau^2 interval type: " + type);
        }
        r.lbTau2 = lb; r.ubTau2 = ub;
    }

    /** Root of cdf(t) = pr for a cdf that decreases in t, given cdf(0) = c0 > pr; the bracket [0, hi0*4^j] reuses every evaluation. */
    private static double solveDecreasing(java.util.function.DoubleUnaryOperator cdf, double pr, double c0, double hi0) {
        double lo = 0, flo = c0 - pr, hi = hi0, fhi = cdf.applyAsDouble(hi) - pr;
        while (fhi > 0 && hi < 1e8) { lo = hi; flo = fhi; hi *= 4; fhi = cdf.applyAsDouble(hi) - pr; }
        if (fhi > 0) return Double.POSITIVE_INFINITY;
        return Dist.brent(t -> cdf.applyAsDouble(t) - pr, lo, flo, hi, fhi, 1e-10, 300);
    }

    // ---- memoisation of confint (identical data, level and method are re-analysed often: GUI refreshes, repeated runs) ----
    private static final int CI_CACHE_MAX = 64;
    private static final java.util.Map<CiKey, TauCI> CI_CACHE = new java.util.LinkedHashMap<CiKey, TauCI>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<CiKey, TauCI> e) { return size() > CI_CACHE_MAX; }
    };

    private static final class CiKey {
        final String method; final double level; final double[] y, v, a; final double[][] x; final int hash;
        CiKey(String method, double level, double[] y, double[] v, double[][] x, double[] a) {
            this.method = method; this.level = level; this.y = y.clone(); this.v = v.clone(); this.a = a == null ? new double[0] : a.clone();
            this.x = new double[x.length][]; for (int i = 0; i < x.length; i++) this.x[i] = x[i].clone();
            hash = 31 * (31 * (31 * (31 * (31 * method.hashCode() + Double.hashCode(level)) + Arrays.hashCode(this.y)) + Arrays.hashCode(this.v)) + Arrays.deepHashCode(this.x)) + Arrays.hashCode(this.a);
        }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof CiKey)) return false; CiKey c = (CiKey) o;
            return hash == c.hash && method.equals(c.method) && Double.compare(level, c.level) == 0 && Arrays.equals(y, c.y) && Arrays.equals(v, c.v) && Arrays.equals(a, c.a) && Arrays.deepEquals(x, c.x);
        }
    }

    /**
     * Reduced form of the GENQ eigenproblem. With Z = A^(1/2) X and Q an orthonormal basis (k x (k-p)) of the orthogonal complement of
     * col(Z), P_a = A^(1/2) Q Q' A^(1/2), so the non-zero eigenvalues of S^(1/2) P_a S^(1/2) equal those of Q' A^(1/2) S A^(1/2) Q
     * = B0 + tau2 * B1 with B0 = Q' diag(a v) Q and B1 = Q' diag(a) Q.
     */
    private static final class GenqCtx {
        final int n; final double[][] B0, B1;
        GenqCtx(double[] a, double[] v, double[][] X) {
            int k = a.length, p = X[0].length; n = k - p;
            double[][] xtax = new double[p][p];
            for (int i = 0; i < k; i++) for (int s = 0; s < p; s++) for (int u = 0; u < p; u++) xtax[s][u] += X[i][s] * a[i] * X[i][u];
            if (Lin.inv(xtax) == null) throw new IllegalArgumentException("Singular design matrix");
            double[][] Z = new double[k][p];
            for (int i = 0; i < k; i++) { double sq = Math.sqrt(a[i]); for (int s = 0; s < p; s++) Z[i][s] = sq * X[i][s]; }
            double[][] W = new double[p][]; double[] ww = new double[p];   // Householder vectors and their squared norms
            for (int j = 0; j < p; j++) {
                double norm = 0; for (int i = j; i < k; i++) norm += Z[i][j] * Z[i][j]; norm = Math.sqrt(norm);
                double[] w = new double[k]; for (int i = j; i < k; i++) w[i] = Z[i][j];
                w[j] -= Z[j][j] > 0 ? -norm : norm;
                double nn = 0; for (int i = j; i < k; i++) nn += w[i] * w[i];
                W[j] = w; ww[j] = nn;
                if (nn == 0) continue;
                for (int c = j; c < p; c++) { double dot = 0; for (int i = j; i < k; i++) dot += w[i] * Z[i][c]; double f = 2 * dot / nn; for (int i = j; i < k; i++) Z[i][c] -= f * w[i]; }
            }
            double[][] Qt = new double[n][];   // rows = basis vectors: columns p..k-1 of H_0 H_1 ... H_(p-1)
            for (int r = 0; r < n; r++) {
                double[] vec = new double[k]; vec[p + r] = 1;
                for (int j = p - 1; j >= 0; j--) {
                    if (ww[j] == 0) continue; double[] w = W[j]; double dot = 0; for (int i = j; i < k; i++) dot += w[i] * vec[i];
                    double f = 2 * dot / ww[j]; for (int i = j; i < k; i++) vec[i] -= f * w[i];
                }
                Qt[r] = vec;
            }
            double[] av = new double[k]; for (int i = 0; i < k; i++) av[i] = a[i] * v[i];
            B0 = new double[n][n]; B1 = new double[n][n];
            for (int r = 0; r < n; r++) for (int c = r; c < n; c++) {
                double s0 = 0, s1 = 0; double[] qr = Qt[r], qc = Qt[c];
                for (int i = 0; i < k; i++) { double q = qr[i] * qc[i]; s0 += q * av[i]; s1 += q * a[i]; }
                B0[r][c] = B0[c][r] = s0; B1[r][c] = B1[c][r] = s1;
            }
        }
        double[] lambdas(double t) {
            double[][] M = new double[n][n];
            for (int r = 0; r < n; r++) for (int c = r; c < n; c++) M[r][c] = M[c][r] = B0[r][c] + t * B1[r][c];
            return Lin.eigSymInPlace(M);
        }
    }

    /** Typical within-study variance as in metafor: (k-p)/tr(P). */
    public static double typicalV(int k, int p, double[] v, double[][] X) {
        double[] w = new double[k]; for (int i = 0; i < k; i++) w[i] = 1 / v[i];
        return (k - p) / trP(X, w);
    }

    private static double solve(java.util.function.DoubleUnaryOperator f) {
        double lo = 0, hi = 1;
        while (f.applyAsDouble(hi) > 0 && hi < 1e8) { lo = hi; hi *= 4; }
        if (f.applyAsDouble(hi) > 0) return Double.POSITIVE_INFINITY;
        return Dist.brent(f, lo, hi, 1e-10, 300);
    }

    /** Eigenvalues of S^{1/2} P_a S^{1/2}, S=diag(v+tau2). */
    static double[] genqLambdas(double[] a, double[] v, double[][] X, double t) {
        int k = a.length, p = X[0].length;
        double[][] xtax = new double[p][p];
        for (int i = 0; i < k; i++) for (int s = 0; s < p; s++) for (int u = 0; u < p; u++) xtax[s][u] += X[i][s] * a[i] * X[i][u];
        double[][] inv = Lin.inv(xtax);
        final double[][] P = new double[k][k], M = new double[k][k]; final double[][] invF = inv; final double[] sq = new double[k];
        for (int i = 0; i < k; i++) sq[i] = Math.sqrt(v[i] + t);
        Par.forRange(k, i -> { for (int j = 0; j < k; j++) {
            double h = 0; for (int s = 0; s < p; s++) for (int u = 0; u < p; u++) h += X[i][s] * invF[s][u] * X[j][u];
            P[i][j] = (i == j ? a[i] : 0) - a[i] * a[j] * h;
            M[i][j] = sq[i] * P[i][j] * sq[j];
        } });
        return Lin.eigSym(M);
    }

    /**
     * P(sum lambda_j chi2_1 <= x). Positive weights (the GENQ case) use the Ruben/Farebrother series, the algorithm behind
     * CompQuadForm::farebrother as called by metafor. Imhof's integral is the fallback (mixed signs, ill-conditioned weights); it decays
     * too slowly to ever stop when there are only a few weights.
     */
    public static double quadCdf(double x, double[] lam) {
        int m = lam.length;
        // few weights: the series always beats Imhof (whose integral then decays too slowly to stop); many weights: use it only when
        // it needs few terms (its recursion costs O(terms^2 / 2 + terms * m)), which is the usual case since the GENQ weights are close together
        double r = rubenCdf(x, lam, 1e-12, m <= 12 ? 12000 : (int) (60 * Math.sqrt(m)));
        return Double.isNaN(r) ? imhofCdf(x, lam) : r;
    }

    /** Ruben's series (Farebrother 1984, AS 204) for positive weights; NaN when it cannot reach eps within maxTerms terms. */
    public static double rubenCdf(double x, double[] lam0, double eps, int maxTerms) {
        int m = 0; for (double l : lam0) if (Math.abs(l) > 1e-12) m++;
        double[] lam = new double[m]; int c = 0; double lmin = Double.POSITIVE_INFINITY, lmax = 0;
        for (double l : lam0) if (Math.abs(l) > 1e-12) { if (l < 0) return Double.NaN; lam[c++] = l; lmin = Math.min(lmin, l); lmax = Math.max(lmax, l); }
        if (m == 0) return x >= 0 ? 1 : 0;
        if (x <= 0) return 0;
        double beta = lmin, y = x / beta, z = y / 2, h = m / 2.0;
        double[] gam = new double[m], pw = new double[m]; double loga0 = 0;
        for (int j = 0; j < m; j++) { gam[j] = 1 - beta / lam[j]; pw[j] = gam[j]; loga0 += 0.5 * Math.log(beta / lam[j]); }
        if (loga0 < -690) return Double.NaN;
        // terms needed ~ ln(1/eps) * lmax/lmin (a_k decays like (1 - lmin/lmax)^k); the a_k recursion is O(k) per term, so cap the cost
        double need = Math.log(1 / eps) * (lmax / lmin) + 60;
        if (need > maxTerms) return Double.NaN;   // too ill-conditioned for the series: let Imhof handle it
        int cap = (int) Math.min(maxTerms, 1.5 * need);
        double[] a = new double[cap + 1], g = new double[cap + 1];
        a[0] = Math.exp(loga0);
        double F = Dist.pchisq(y, m), logt = h * Math.log(z) - z - Dist.lgamma(h + 1);   // F = P(chi2_m <= y); t_k = P(chi2_{m+2k} <= y) - P(chi2_{m+2k+2} <= y)
        double cdf = a[0] * F, sumA = a[0];
        for (int k = 1; k <= cap; k++) {
            F -= Math.exp(logt); logt += Math.log(z) - Math.log(h + k);
            double gk = 0; for (int j = 0; j < m; j++) { gk += pw[j]; pw[j] *= gam[j]; }
            g[k] = gk; double sa = 0; for (int r = 1; r <= k; r++) sa += g[r] * a[k - r];
            a[k] = sa / (2.0 * k);
            cdf += a[k] * Math.max(F, 0); sumA += a[k];
            if (1 - sumA < eps) return Math.min(1, Math.max(0, cdf));
        }
        return Double.NaN;
    }

    /** P(sum lambda_j chi2_1 <= x) by Imhof's method. */
    public static double imhofCdf(double x, double[] lam) {
        double sumAbs = 0; int m = 0; for (double l : lam) if (Math.abs(l) > 1e-12) { sumAbs += Math.abs(l); m++; }
        double[] L = new double[m]; int c = 0; for (double l : lam) if (Math.abs(l) > 1e-12) L[c++] = l;
        double omega = 0.5 * (sumAbs + Math.abs(x)); if (omega <= 0) omega = 1;
        double width = 2 * Math.PI / omega / 2, u0 = 0, total = 0;
        double[] gx = GL_X, gw = GL_W;
        for (int panel = 0; panel < 200000; panel++) {
            double a = u0, b = u0 + width, half = (b - a) / 2, mid = (a + b) / 2, s = 0;
            for (int i = 0; i < gx.length; i++) { double u = mid + half * gx[i]; s += gw[i] * imhofIntegrand(u, x, L); }
            s *= half; total += s; u0 = b;
            double rho = 0.25 * sumLog1p(L, u0);
            if (Math.abs(s) < 1e-14 && rho - Math.log(u0) > 12) break;
            if (rho - Math.log(u0) > 34) break;
        }
        double pr = 0.5 - total / Math.PI;
        return Math.min(1, Math.max(0, pr));
    }

    /**
     * Imhof integrand sin(theta(u)) / (u * rho(u)) with theta = -x u / 2 + (1/2) sum atan(l u) and rho = prod (1 + l^2 u^2)^(1/4).
     * prod (1 + i l u) is accumulated as one complex number: its argument (unwrapped by counting crossings of the negative real
     * axis) equals sum atan(l u) and log|prod| equals (1/2) sum log(1 + l^2 u^2), which replaces 2m transcendental calls by m multiply-adds.
     */
    private static double imhofIntegrand(double u, double x, double[] L) {
        double zr = 1, zi = 0, logScale = 0; int wind = 0;
        for (double l : L) {
            double f = l * u, nr = zr - zi * f, ni = (zi + zr * f) + 0.0;   // + 0.0 turns -0.0 into +0.0 (keeps atan2 consistent with the crossing test)
            if (nr < 0) { if (zi >= 0 && ni < 0) wind++; else if (zi < 0 && ni >= 0) wind--; }
            zr = nr; zi = ni;
            if (Math.abs(zr) + Math.abs(zi) > 1e100) { zr *= 1e-100; zi *= 1e-100; logScale += 230.25850929940458; }
        }
        double th = -0.5 * x * u + 0.5 * (Math.atan2(zi, zr) + 2 * Math.PI * wind);
        double lr = 0.5 * (Math.log(Math.hypot(zr, zi)) + logScale);   // = (1/4) sum log1p(l^2 u^2)
        return Math.sin(th) / (u * Math.exp(lr));
    }

    /** sum log(1 + l^2 u^2), accumulated as a product with rescaling. */
    private static double sumLog1p(double[] L, double u) {
        double prod = 1, logp = 0;
        for (double l : L) { double f = l * u; prod *= 1 + f * f; if (prod > 1e100) { logp += 230.25850929940458; prod *= 1e-100; } }
        return Math.log(prod) + logp;
    }

    /** Straightforward per-eigenvalue version of {@link #imhofIntegrand} (reference for the self-test). */
    static double imhofIntegrandRef(double u, double x, double[] L) {
        double th = -0.5 * x * u, lr = 0;
        for (double l : L) { th += 0.5 * Math.atan(l * u); lr += 0.25 * Math.log1p(l * l * u * u); }
        return Math.sin(th) / (u * Math.exp(lr));
    }

    private static final double[] GL_X = new double[16], GL_W = new double[16];
    static {
        int n = 16;
        for (int i = 0; i < n; i++) {
            double z = Math.cos(Math.PI * (i + 0.75) / (n + 0.5)), pp = 0;
            for (int it = 0; it < 100; it++) {
                double p1 = 1, p2 = 0;
                for (int j = 0; j < n; j++) { double p3 = p2; p2 = p1; p1 = ((2 * j + 1) * z * p2 - j * p3) / (j + 1); }
                pp = n * (z * p1 - p2) / (z * z - 1);
                double z1 = z; z = z1 - p1 / pp; if (Math.abs(z - z1) < 1e-15) break;
            }
            GL_X[i] = z; GL_W[i] = 2 / ((1 - z * z) * pp * pp);
        }
    }
}
