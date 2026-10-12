package org.metajava;

import org.metajava.core.*;
import java.util.*;

/**
 * Calibration of the leave-one-out studentized residual (Influence.rstudent) when there is NO outlier.
 * Question: is the normal reference (|z| > 1.96 per study, or the normal Bonferroni limit qnorm(1 - 0.025/k)
 * for a family-wise error rate of 5% over the k studies) appropriate under a fitted random-effects
 * (meta-)regression?  Usage: java org.metajava.Sim5 <model 1|2> <datasets per scenario> [seed]
 *   model 1 = random-effects model (intercept only), model 2 = meta-regression (intercept + one covariate).
 * Columns: per-study rate |z| > 1.96; pooled 99th percentile of |z| (normal: 2.576); SD of z;
 * family-wise rate (any study flagged) at 1.96, at the normal Bonferroni limit and at the t Bonferroni limit
 * (t with k-p-1 df); the same two rates for the ORACLE residual that uses the true tau^2 instead of the
 * leave-one-out REML estimate; share of leave-one-out fits with tau^2 = 0; median of tau^2.del / true tau^2.
 * "skew" = random effects drawn from a centred exponential distribution with the same SD (non-normal).
 */
public class Sim5 {
    static double[] oracle(double[] y, double[] v, double[][] X, double tau2) {
        int k = y.length, p = X[0].length; double[] z = new double[k];
        for (int i = 0; i < k; i++) {
            double[][] A = new double[p][p]; double[] b = new double[p];
            for (int j = 0; j < k; j++) if (j != i) { double w = 1 / (v[j] + tau2);
                for (int a = 0; a < p; a++) { b[a] += X[j][a] * w * y[j]; for (int c = 0; c < p; c++) A[a][c] += X[j][a] * w * X[j][c]; } }
            double[][] inv = Lin.inv(A); double[] be = Lin.mulv(inv, b); double fit = 0, var = 0;
            for (int a = 0; a < p; a++) { fit += X[i][a] * be[a]; for (int c = 0; c < p; c++) var += X[i][a] * inv[a][c] * X[i][c]; }
            z[i] = (y[i] - fit) / Math.sqrt(v[i] + tau2 + var);
        }
        return z;
    }
    static double q(double[] s, double p) { double[] t = s.clone(); Arrays.sort(t); return t[(int) Math.min(t.length - 1, Math.floor(p * t.length))]; }

    public static void main(String[] a) {
        int model = Integer.parseInt(a[0]), N = Integer.parseInt(a[1]); long seed0 = a.length > 2 ? Long.parseLong(a[2]) : 555;
        List<Object[]> sc = new ArrayList<>();   // name, k, tau, skew, seLo, seHi
        for (int k : new int[]{12, 20, 30}) sc.add(new Object[]{"homogeneous k=" + k, k, 0.0, 0, .08, .33});
        for (int k : new int[]{12, 20, 30}) for (double t : new double[]{0.2, 0.3, 0.5}) sc.add(new Object[]{"tau=" + t + " k=" + k, k, t, 0, .08, .33});
        sc.add(new Object[]{"tau=0.3 k=20 equal se=0.2", 20, .3, 0, .2, .2});
        sc.add(new Object[]{"skewed tau=0.3 k=20", 20, .3, 1, .08, .33});
        sc.add(new Object[]{"skewed tau=0.5 k=20", 20, .5, 1, .08, .33});
        System.out.println("model | scenario | per-study |z|>1.96 | 99th pct |z| | SD z | FWER>1.96 | FWER normal Bonf | FWER t Bonf | oracle per-study>1.96 | oracle FWER normal Bonf | share tau2.del=0 | median tau2.del/tau2");
        for (Object[] s : sc) {
            Random R = new Random(seed0 + s[0].hashCode() + model); int k = (Integer) s[1]; double tau = (Double) s[2]; int skew = (Integer) s[3]; double lo = (Double) s[4], hi = (Double) s[5];
            int p = model == 2 ? 2 : 1; double zb = Dist.qnorm(1 - 0.025 / k), tb = Dist.qt(1 - 0.025 / k, k - p - 1);
            List<Double> all = new ArrayList<>(); long n196 = 0, nO196 = 0, tot = 0; int fw196 = 0, fwN = 0, fwT = 0, ofwN = 0, ok = 0; long zero = 0, ntau = 0; double sumsq = 0; List<Double> ratio = new ArrayList<>();
            for (int d = 0; d < N; d++) {
                double[] y = new double[k], v = new double[k]; double[][] X = new double[k][p];
                for (int i = 0; i < k; i++) {
                    double se = lo + (hi - lo) * R.nextDouble(); v[i] = se * se;
                    double x = R.nextGaussian(); X[i][0] = 1; if (p == 2) X[i][1] = x;
                    double u = tau == 0 ? 0 : skew == 1 ? tau * (-Math.log(1 - R.nextDouble()) - 1) : tau * R.nextGaussian();
                    y[i] = 0.2 + (p == 2 ? 0.3 * x : 0) + u + se * R.nextGaussian();
                }
                Influence in; try { in = Influence.run(y, v, p == 2 ? X : null, "REML", null, null); } catch (Exception e) { continue; }
                boolean bad = false; for (double z : in.rstudent) if (Double.isNaN(z)) bad = true; if (bad) continue; ok++;
                double[] oz = oracle(y, v, X, tau * tau); boolean f1 = false, fn = false, ft = false, of = false;
                for (int i = 0; i < k; i++) { double z = Math.abs(in.rstudent[i]); all.add(z); sumsq += in.rstudent[i] * in.rstudent[i]; tot++;
                    if (z > 1.96) { n196++; f1 = true; } if (z > zb) fn = true; if (z > tb) ft = true;
                    if (Math.abs(oz[i]) > 1.96) nO196++; if (Math.abs(oz[i]) > zb) of = true;
                    ntau++; if (in.tau2Del[i] <= 1e-12) zero++; if (tau > 0) ratio.add(in.tau2Del[i] / (tau * tau)); }
                if (f1) fw196++; if (fn) fwN++; if (ft) fwT++; if (of) ofwN++;
            }
            double[] arr = new double[all.size()]; for (int i = 0; i < arr.length; i++) arr[i] = all.get(i);
            double med = Double.NaN; if (!ratio.isEmpty()) { Collections.sort(ratio); med = ratio.get(ratio.size() / 2); }
            System.out.printf("%s | %s | %.1f%% | %.2f | %.2f | %.0f%% | %.0f%% | %.0f%% | %.1f%% | %.0f%% | %.0f%% | %s (n=%d)%n", model == 2 ? "REG" : "ORD", s[0],
                100.0 * n196 / tot, q(arr, 0.99), Math.sqrt(sumsq / tot), 100.0 * fw196 / ok, 100.0 * fwN / ok, 100.0 * fwT / ok, 100.0 * nO196 / tot, 100.0 * ofwN / ok, 100.0 * zero / ntau, Double.isNaN(med) ? "-" : String.format("%.2f", med), ok);
            System.out.flush();
        }
    }
}
