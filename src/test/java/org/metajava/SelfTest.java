package org.metajava;

import org.metajava.core.*;
import org.metajava.plot.Plots;

import java.awt.image.BufferedImage;

/**
 * Self-test run by build.sh --test / build.bat --test (and by the GitHub "check" workflow).
 * No test framework is needed: the program exits with status 1 if any check fails.
 *
 * Reference values: forest plots printed by R meta (metagen with lnHR/SE and with ZCOR/SE; DerSimonian-Laird tau^2,
 * classic CI, normal-distribution prediction interval method.predict = "S"), compared at the printed precision.
 */
public class SelfTest {
    static int failures = 0, checks = 0;

    static void check(boolean ok, String what) { checks++; if (!ok) { failures++; System.out.println("FAIL: " + what); } else System.out.println("ok:   " + what); }
    static void near(double got, double want, double tol, String what) { check(Math.abs(got - want) <= tol, what + " (got " + got + ", expected " + want + " +/- " + tol + ")"); }

    static final double[] LNHR = {-0.6931, -0.6539, -0.7985, -0.5447, -0.8675, -0.4943, -0.7340, -0.6162, -0.9416, -0.5276};
    static final double[] SEHR = {0.0719, 0.0691, 0.0800, 0.0619, 0.0858, 0.0588, 0.0749, 0.0665, 0.0926, 0.0608};
    static final double[] Z = {0.5957, 0.4071, 0.6580, 0.6854, 0.2790, 0.3700, 0.5709, 0.5088, 0.5506, 0.4332, 0.6761, 0.6625, 0.5614, 0.7105, 0.6184, 0.4320, 0.6042, 0.4177, 0.6761, 0.5453};
    static final double[] SEZ = {0.0963, 0.0779, 0.0648, 0.0579, 0.0806, 0.0485, 0.0705, 0.0635, 0.0520, 0.0844, 0.0492, 0.0709, 0.0691, 0.0539, 0.0892, 0.0596, 0.0713, 0.0499, 0.0764, 0.0579};

    static MetaData gen(double[] te, double[] se, boolean twice) {
        int k = twice ? 2 * te.length : te.length; double[] t = new double[k], s = new double[k]; String[] lab = new String[k];
        for (int i = 0; i < k; i++) { t[i] = te[i % te.length]; s[i] = se[i % se.length]; lab[i] = String.format("Study_%02d", i + 1); }
        MetaData d = new MetaData(); d.put("TE", t); d.put("seTE", s); d.n = k; d.studlab = lab; return d;
    }

    static MetaSpec genSpec(String sm) { MetaSpec s = new MetaSpec(); s.type = "gen"; s.sm = sm; s.prediction = true; return s; }
    static MetaSpec refSpec(String sm) { MetaSpec s = genSpec(sm); s.methodTau = "DL"; s.methodPredict = "S"; return s; }

    static int countColour(BufferedImage im, int rgb) { int n = 0; for (int y = 0; y < im.getHeight(); y++) for (int x = 0; x < im.getWidth(); x++) if ((im.getRGB(x, y) & 0xFFFFFF) == rgb) n++; return n; }

    public static void main(String[] args) {
        // ---- 1. hazard ratios (meta forest plot 1; DL, method.predict = "S"): HR 0.51 [0.48; 0.54], I2 73.3%, tau2 0.0136, z = -22.07, PI [0.40; 0.64]
        MetaSpec s = refSpec("HR"); MetaData d = gen(LNHR, SEHR, true); MetaResult r = Meta.run(s, d); Pool p = r.overall;
        near(Math.exp(p.teRandom), 0.51, 0.005, "HR random-effects estimate");
        near(Math.exp(p.loRandom), 0.48, 0.005, "HR lower CI limit"); near(Math.exp(p.upRandom), 0.54, 0.005, "HR upper CI limit");
        near(100 * p.I2, 73.3, 0.05, "HR I^2 (%)"); near(p.tau2, 0.0136, 0.00005, "HR tau^2"); near(p.statRandom, -22.07, 0.005, "HR z statistic");
        near(Math.exp(p.loPred), 0.40, 0.005, "HR prediction interval, lower"); near(Math.exp(p.upPred), 0.64, 0.005, "HR prediction interval, upper");

        // ---- 2. correlations on Fisher's z scale (meta forest plot 3; DL, method.predict = "S"): COR 0.50 [0.46; 0.54], I2 73.7%, tau2 0.0113, z = 19.53, PI [0.32; 0.64]
        s = refSpec("ZCOR"); r = Meta.run(s, gen(Z, SEZ, false)); p = r.overall;
        near(Meta.back(s, p.teRandom, Double.NaN), 0.50, 0.005, "COR random-effects estimate");
        near(Meta.back(s, p.loRandom, Double.NaN), 0.46, 0.005, "COR lower CI limit"); near(Meta.back(s, p.upRandom, Double.NaN), 0.54, 0.005, "COR upper CI limit");
        near(100 * p.I2, 73.7, 0.05, "COR I^2 (%)"); near(p.tau2, 0.0113, 0.00005, "COR tau^2"); near(p.statRandom, 19.53, 0.005, "COR z statistic");
        near(Meta.back(s, p.loPred, Double.NaN), 0.32, 0.005, "COR prediction interval, lower"); near(Meta.back(s, p.upPred, Double.NaN), 0.64, 0.005, "COR prediction interval, upper");

        // ---- 3. prediction-interval level and report text
        check(Report.pct(0.95).equals("95") && Report.pct(0.99).equals("99") && Report.pct(0.975).equals("97.5"), "Report.pct formats confidence levels");
        s = genSpec("HR"); d = gen(LNHR, SEHR, true); r = Meta.run(s, d); String txt = Report.summary(r);
        check(txt.contains("Prediction interval (95%):") && txt.contains("95% PI"), "main results show the 95% prediction interval below the heterogeneity test");
        double lo95 = r.overall.loPred; s = genSpec("HR"); s.levelPredict = 0.99; r = Meta.run(s, d); txt = Report.summary(r);
        check(txt.contains("Prediction interval (99%):") && txt.contains("99% PI"), "main results show the 99% prediction interval");
        check(r.overall.loPred < lo95, "a 99% prediction interval is wider than a 95% one");
        s = genSpec("HR"); s.prediction = false; txt = Report.summary(Meta.run(s, d)); check(!txt.contains("Prediction interval ("), "no prediction interval block when the option is off");

        // ---- 4. subgroup analysis: PI column of "Results for subgroups", forest plot rows controlled by the subgroup option
        String[] sg = new String[20]; for (int i = 0; i < 20; i++) sg[i] = i % 2 == 0 ? "A" : "B"; d.subgroup = sg;
        s = genSpec("HR"); s.prediction = false; s.subgroupName = "Group"; r = Meta.run(s, d); String st = Report.subgroupText(r);
        check(st.contains("95% PI"), "subgroup table has a 95% PI column");
        s = genSpec("HR"); s.prediction = false; s.levelPredict = 0.99; s.subgroupName = "Group"; check(Report.subgroupText(Meta.run(s, d)).contains("99% PI"), "subgroup table has a 99% PI column");
        final int PI_RED = (205 << 16);   // fill colour of the prediction-interval bar in the forest plot
        s = genSpec("HR"); s.prediction = false; s.predictionSubgroup = false; s.subgroupName = "Group"; int off = countColour(Plots.forest(Meta.run(s, d), new Plots.Opts()), PI_RED);
        s = genSpec("HR"); s.prediction = false; s.predictionSubgroup = true; s.subgroupName = "Group"; int on = countColour(Plots.forest(Meta.run(s, d), new Plots.Opts()), PI_RED);
        s = genSpec("HR"); s.prediction = true; s.predictionSubgroup = false; s.subgroupName = "Group"; int mainOnly = countColour(Plots.forest(Meta.run(s, d), new Plots.Opts()), PI_RED);
        check(off == 0, "subgroup forest: no prediction interval when both options are off");
        check(on > 0, "subgroup forest: subgroup prediction intervals appear with the subgroup option alone");
        s = genSpec("HR"); s.prediction = true; s.predictionSubgroup = true; s.subgroupName = "Group"; int both = countColour(Plots.forest(Meta.run(s, d), new Plots.Opts()), PI_RED);
        check(mainOnly > 0 && both > on && both > mainOnly, "subgroup forest: the main option adds the overall interval, the subgroup option the subgroup ones");

        // ---- 5. influence diagnostics: flagged studies are drawn as red points
        double[] y = {0.2, 0.35, 0.3, 0.28, 0.6, -0.1, 1.5}, v = {0.04, 0.05, 0.03, 0.045, 0.06, 0.05, 0.5}; String[] lab = {"1", "2", "3", "4", "5", "6", "7"};
        Influence in = Influence.run(y, v, null, "REML", lab, new String[]{"intrcpt"}); boolean any = false; for (boolean f : in.flagged) any |= f;
        check(any, "influence diagnostics flag at least one study in simulated data");
        check(countColour(Plots.influence(in, new Plots.Opts()), 0xFF0000) > 0, "flagged studies are drawn in red in the diagnostics plot");

        // ---- 5b. metafor compatibility of the classification rule: rstudent / cov.r never flag a study on their own
        for (int i = 0; i < in.k; i++) {
            boolean rule = Math.abs(in.dffits[i]) > 3 * Math.sqrt((double) in.p / (in.k - in.p)) || Dist.pchisq(in.cookD[i], in.p) > 0.5 || in.hat[i] > 3.0 * in.p / in.k;
            for (double b : in.dfbs[i]) rule |= Math.abs(b) > 1;
            check(rule == in.flagged[i], "is.infl matches metafor rule for study " + (i + 1) + " (rstudent not used)");
            check(!in.flagReason[i].contains("rstudent"), "rstudent is not a flag reason (study " + (i + 1) + ")");
        }
        // dfbs uses the full-design covariance with tau2.del plugged in (metafor vb.del), not the leave-one-out vb
        {
            int i = 6; double t2 = in.tau2Del[i], sw = 0, swx = 0; for (int j = 0; j < y.length; j++) { double w = 1 / (v[j] + t2); sw += w; swx += w * y[j]; }
            Rma d6 = Rma.fit(java.util.Arrays.copyOf(y, 6), java.util.Arrays.copyOf(v, 6), null, "REML"); Rma f7 = Rma.fit(y, v, null, "REML");
            double expect = (f7.beta[0] - d6.beta[0]) / Math.sqrt(1 / sw);
            check(Math.abs(in.dfbs[i][0] - expect) < 1e-9, "dfbetas = dfb / sqrt(diag(vb.del)) with tau2.del plugged into all k studies");
        }
        // an rstudent-only outlier must not be drawn red
        {
            double[] y2 = {0.2, 0.22, 0.18, 0.21, 0.19, 0.2, 0.23, 0.17, 0.2, 0.21}, v2 = new double[10]; java.util.Arrays.fill(v2, 0.04);
            Influence i2 = Influence.run(y2, v2, null, "REML", new String[10], new String[]{"intrcpt"});
            for (int i = 0; i < 10; i++) check(i2.flagged[i] == !i2.flagReason[i].isEmpty(), "flag/reason consistent");
        }

        // ---- 5c. parallel workflows: eigenvalues and thread-count independence
        {
            double[][] m = {{2, -1, 0}, {-1, 2, -1}, {0, -1, 2}}; double[] ev = Lin.eigSym(m); java.util.Arrays.sort(ev);
            near(ev[0], 2 - Math.sqrt(2), 1e-12, "eigSym: smallest eigenvalue of the 1-2-1 matrix"); near(ev[1], 2, 1e-12, "eigSym: middle eigenvalue"); near(ev[2], 2 + Math.sqrt(2), 1e-12, "eigSym: largest eigenvalue");
            int keep = Par.threads(); MetaSpec sp = genSpec("HR"); sp.methodPredict = "NNF";
            Par.setThreads(1); MetaResult a1 = Meta.run(sp, gen(LNHR, SEHR, true)); Meta.leaveOneOut(a1); Meta.cumulative(a1, null); String t1 = Report.looText(a1) + Report.cumText(a1) + Report.summary(a1);
            Par.setThreads(4); MetaResult a4 = Meta.run(sp, gen(LNHR, SEHR, true)); Meta.leaveOneOut(a4); Meta.cumulative(a4, null); String t4 = Report.looText(a4) + Report.cumText(a4) + Report.summary(a4);
            check(t1.equals(t4), "leave-one-out, cumulative and NNF results do not depend on the number of threads"); Par.setThreads(keep);
            double[] sum = new double[1000]; Par.forRange(1000, i -> sum[i] = i * 2.0); boolean okp = true; for (int i = 0; i < 1000; i++) okp &= sum[i] == i * 2.0; check(okp, "Par.forRange fills every slot exactly once");
        }

        // ---- 6. forest plots are produced for single proportions (no null value, no centring) and correlations
        s = new MetaSpec(); s.type = "prop"; s.sm = "PLOGIT"; MetaData dp = new MetaData(); dp.put("event", new double[]{5, 10, 20, 8}); dp.put("n", new double[]{50, 60, 90, 40}); dp.n = 4; dp.studlab = new String[]{"A", "B", "C", "D"};
        BufferedImage im = Plots.forest(Meta.run(s, dp), new Plots.Opts()); check(im.getWidth() > 100 && im.getHeight() > 100, "single-proportion forest plot is drawn");

        // ---- 7. tau^2 confidence interval of DerSimonian-Laird (Jackson / generalised-Q method, the meta default for method.tau = "DL")
        // reference values: the previous (slow) implementation, which integrated Imhof's formula; agreement to ~1e-7 (the tau^2 root tolerance)
        {
            double[] zi = java.util.Arrays.copyOf(Z, 20), sz = java.util.Arrays.copyOf(SEZ, 20);
            int[] ks = {3, 5, 10, 20}; double[][] want = {{0, 0.71455704}, {0.00553420, 0.25332152}, {0.00341606, 0.05301707}, {0.00435033, 0.02794274}};
            long t0 = System.nanoTime();
            for (int q = 0; q < ks.length; q++) {
                int k = ks[q]; double[] yk = java.util.Arrays.copyOf(zi, k), vk = new double[k]; for (int i = 0; i < k; i++) vk[i] = sz[i] * sz[i];
                Rma.TauCI ci = Rma.fit(yk, vk, null, "DL").confint("J", 0.95);
                near(ci.lbTau2, want[q][0], 1e-7, "Jackson tau2 CI lower bound, k=" + k); near(ci.ubTau2, want[q][1], 1e-7, "Jackson tau2 CI upper bound, k=" + k);
            }
            double[] v10 = new double[10]; for (int i = 0; i < 10; i++) v10[i] = SEHR[i] * SEHR[i];
            Rma.TauCI chr = Rma.fit(LNHR, v10, null, "DL").confint("J", 0.95);
            near(chr.lbTau2, 0.00484930, 1e-7, "Jackson tau2 CI lower bound, hazard ratios"); near(chr.ubTau2, 0.06553877, 1e-7, "Jackson tau2 CI upper bound, hazard ratios");
            check((System.nanoTime() - t0) / 1e6 < 5000, "Jackson tau2 CIs for k = 3, 5, 10, 20 take well under 5 s (the old integral needed several seconds for k = 3)");
            double[] lam = {0.31, 0.12, 0.05, 0.4, 0.22}; double sum = 1.1; double mx = 0;
            for (double f : new double[]{0.3, 0.8, 1, 1.5, 3}) mx = Math.max(mx, Math.abs(Rma.rubenCdf(f * sum, lam, 1e-12, 12000) - Rma.imhofCdf(f * sum, lam)));
            check(mx < 1e-9, "Ruben series and Imhof integral agree for a weighted sum of chi-squares (max difference " + mx + ")");
            Rma.TauCI again = Rma.fit(LNHR, v10, null, "DL").confint("J", 0.95);
            check(again.lbTau2 == chr.lbTau2 && again.ubTau2 == chr.ubTau2 && again != chr, "repeated tau2 CI is served from the cache with identical values");
        }

        {   // parallel pool: nested loops, cancellation
            int keep = Par.threads(); Par.setThreads(4);
            long[][] grid = new long[40][40]; Par.forRange(40, i -> Par.forRange(40, j -> grid[i][j] = (long) i * 100 + j));   // a loop inside a loop of the pool must neither deadlock nor lose work
            boolean okn = true; for (int i = 0; i < 40; i++) for (int j = 0; j < 40; j++) okn &= grid[i][j] == (long) i * 100 + j; check(okn, "nested Par.forRange loops complete with the right values");
            java.util.concurrent.atomic.AtomicBoolean stop = new java.util.concurrent.atomic.AtomicBoolean(); java.util.concurrent.atomic.AtomicInteger ran = new java.util.concurrent.atomic.AtomicInteger(); boolean cancelled = false;
            try { Par.withCancel(stop::get, () -> Par.forRange(100000, i -> { if (ran.incrementAndGet() == 50) stop.set(true); })); } catch (java.util.concurrent.CancellationException e) { cancelled = true; }
            check(cancelled && ran.get() < 100000, "a parallel loop stops early once the cancellation condition is set (" + ran.get() + " of 100000 iterations ran)");
            boolean failed = false; try { Par.forRange(100, i -> { if (i == 3) throw new IllegalStateException("boom"); }); } catch (IllegalStateException e) { failed = true; }
            check(failed, "an exception in a parallel loop is rethrown to the caller"); Par.setThreads(keep);
        }

        {   // GLMM with fixed study effects (UM.FS): the intercept of each study is found by Newton steps on the analytic score; reference values from the earlier Brent-search implementation
            double[] ox1 = {4, 25, 9, 30, 6, 22, 3, 28}, on1 = {100, 110, 95, 120, 90, 105, 100, 115}, ox2 = {10, 12, 9, 11, 10, 8, 13, 10}, on2 = {100, 108, 97, 118, 92, 104, 102, 112};
            double[] ix1 = {5, 40, 12, 3, 50, 9}, it1 = {100, 150, 120, 110, 140, 130}, ix2 = {20, 22, 18, 15, 25, 19}, it2 = {105, 148, 125, 108, 138, 128};
            Object[][] cs = {
                {"OR", 7, ox1, on1, ox2, on2, 0.484519084, 0.151733277, -49.965965672, 0.255509086, 0.342473508, 0.708198011, -43.280501137},
                {"OR", 1, ox1, on1, ox2, on2, 0.484519084, 0.151733277, -49.965965672, 0.255446127, 0.342712515, 0.709601528, -43.270292444},
                {"IRR", 7, ix1, it1, ix2, it2, -0.000973809, 0.129650875, -45.467709330, -0.364557244, 0.349927137, 0.570250048, -36.058986220}};
            for (Object[] c : cs) {
                double[] x1 = (double[]) c[2]; int[] sel = new int[x1.length]; for (int i = 0; i < sel.length; i++) sel[i] = i;
                Glmm.Data g = new Glmm.Data((String) c[0], "UM.FS", (Integer) c[1], x1, (double[]) c[3], (double[]) c[4], (double[]) c[5]);
                Glmm.Result fe = Glmm.fit(g, sel, false, 0), re = Glmm.fit(g, sel, true, fe.est); String w = "GLMM UM.FS " + c[0] + " nAGQ = " + c[1] + ": ";
                near(fe.est, (Double) c[6], 1e-6, w + "common-effect estimate"); near(fe.se, (Double) c[7], 1e-6, w + "common-effect SE"); near(fe.logLik, (Double) c[8], 1e-7, w + "common-effect log-likelihood");
                near(re.est, (Double) c[9], 1e-6, w + "random-effects estimate"); near(re.se, (Double) c[10], 1e-6, w + "random-effects SE");
                near(re.tau2, (Double) c[11], 1e-5, w + "tau^2"); near(re.logLik, (Double) c[12], 1e-7, w + "random-effects log-likelihood");
            }
        }

        {   // GLMM with random study intercepts (UM.RS): reference values from the earlier implementation (Brent search for every saturated study effect); the saturated model is now Newton-based and parallel. The Wald-type Q is the Wald test of the saturated model with random study intercepts, with the covariance matrix lme4 reports (as metafor::rma.glmm); the two Q values were checked against an independent Python implementation
            double[] ox1 = {4, 25, 9, 30, 6, 22, 3, 28}, on1 = {100, 110, 95, 120, 90, 105, 100, 115}, ox2 = {10, 12, 9, 11, 10, 8, 13, 10}, on2 = {100, 108, 97, 118, 92, 104, 102, 112};
            double[] ix1 = {5, 40, 12, 3, 50, 9}, it1 = {100, 150, 120, 110, 140, 130}, ix2 = {20, 22, 18, 15, 25, 19}, it2 = {105, 148, 125, 108, 138, 128};
            Object[][] cs = {
                {"OR", ox1, on1, ox2, on2, 0.484111143, 0.151402125, -58.909227117, 50.161717042, 42.567849317, 0.328927608, 0.338468286, 0.698217630, 0.093905747, -51.590637624},
                {"IRR", ix1, it1, ix2, it2, -0.000570251, 0.129649530, -53.784975613, 50.812021552, 56.738225305, -0.319978885, 0.343087727, 0.549816023, 0.165562623, -44.544602442}};
            for (Object[] c : cs) {
                double[] x1 = (double[]) c[1]; int[] sel = new int[x1.length]; for (int i = 0; i < sel.length; i++) sel[i] = i;
                Glmm.Data g = new Glmm.Data((String) c[0], "UM.RS", 1, x1, (double[]) c[2], (double[]) c[3], (double[]) c[4]);
                Glmm.Result fe = Glmm.fit(g, sel, false, 0), re = Glmm.fit(g, sel, true, fe.est); String w = "GLMM UM.RS " + c[0] + ": ";
                near(fe.est, (Double) c[5], 1e-6, w + "common-effect estimate"); near(fe.se, (Double) c[6], 1e-6, w + "common-effect SE"); near(fe.logLik, (Double) c[7], 1e-7, w + "common-effect log-likelihood");
                near(fe.qLRT, (Double) c[8], 1e-6, w + "likelihood-ratio Q (saturated model)"); near(fe.qWald, (Double) c[9], 1e-6, w + "Wald-type Q");
                near(re.est, (Double) c[10], 1e-6, w + "random-effects estimate"); near(re.se, (Double) c[11], 1e-6, w + "random-effects SE");
                near(re.tau2, (Double) c[12], 1e-5, w + "tau^2"); near(re.sigma2, (Double) c[13], 1e-5, w + "sigma^2"); near(re.logLik, (Double) c[14], 1e-7, w + "random-effects log-likelihood");
                // lrt = false (leave-one-out, cumulative, Baujat): same fit, no saturated model; a known saturated likelihood is reused
                Glmm.Result fl = Glmm.fit(g, sel, true, fe.est, false, Double.NaN), fk = Glmm.fit(g, sel, true, fe.est, true, fe.llSat);
                check(Double.isNaN(fl.qLRT) && Double.isNaN(fl.llSat), w + "without the likelihood-ratio test qLRT is not computed");
                check(fl.est == re.est && fl.se == re.se && fl.tau2 == re.tau2 && fl.qWald == re.qWald, w + "the fit is identical without the likelihood-ratio test");
                check(fk.qLRT == re.qLRT, w + "a known saturated log-likelihood gives the same likelihood-ratio Q");
            }
        }

        {   // CM.EL on a 20-study data set (the first 10 studies of HR_meta.xlsx twice): metafor::rma.glmm(model = "CM.EL", method = "ML") gives tau^2 = 0.0334778, LRT Q = 81.6036, Wald Q = 81.5578, I^2 = 75.249 % (tau^2 / (tau^2 + typical v)), H^2 = 4.04, estimate -1.01601285
            double[] e1 = new double[20], s1 = new double[20], e2 = new double[20], s2 = new double[20]; double[] ee = {220, 180, 200, 230, 200, 240, 190, 210, 200, 240}, ne = {1000, 800, 780, 750, 900, 850, 830, 880, 950, 870}, ec = {440, 348, 420, 380, 460, 370, 400, 390, 470, 390};
            for (int i = 0; i < 20; i++) { e1[i] = ee[i % 10]; s1[i] = ne[i % 10]; e2[i] = ec[i % 10]; s2[i] = ne[i % 10]; }
            MetaSpec cs = new MetaSpec(); cs.type = "bin"; cs.sm = "OR"; cs.method = "GLMM"; cs.modelGlmm = "CM.EL"; cs.methodTau = "ML"; cs.random = true; cs.common = false;
            MetaData cd = new MetaData(); cd.put("event.e", e1); cd.put("n.e", s1); cd.put("event.c", e2); cd.put("n.c", s2); Pool cp = Meta.run(cs, cd).overall;
            near(cp.teRandom, -1.01601285, 1e-6, "GLMM CM.EL 20 studies: estimate agrees with metafor"); near(cp.tau2, 0.0334778, 1e-6, "GLMM CM.EL 20 studies: tau^2 agrees with metafor");
            near(cp.qLRT, 81.6036, 2e-4, "GLMM CM.EL 20 studies: likelihood-ratio Q agrees with metafor");
            near(cp.Q, 81.5578, 0.13, "GLMM CM.EL 20 studies: Wald-type Q agrees with metafor (metafor's numerically optimised saturated model stops a little short of the exact maximum)");
            near(cp.Q, 81.4358, 2e-3, "GLMM CM.EL 20 studies: Wald-type Q at the exact saturated-model maximum (independent Python check)");
            near(100 * cp.I2tau, 75.249, 2e-3, "GLMM CM.EL 20 studies: tau^2-based I^2 agrees with metafor"); near(cp.Htau * cp.Htau, 4.04, 5e-3, "GLMM CM.EL 20 studies: tau^2-based H^2 agrees with metafor"); near(100 * cp.I2, 76.7, 0.05, "GLMM CM.EL 20 studies: Q-based I^2 agrees with meta");
        }

        {   // UM.RS on an 80-study data set (HR_meta_5.xlsx, events/size of treatment and control): metafor::rma.glmm(model = "UM.RS", method = "ML") gives Wald Q = 296.2068, LRT Q = 293.0248, tau^2 = 0.0334328, estimate -1.0160170
            double[] x1 = {220, 180, 200, 230, 200, 240, 190, 210, 200, 240, 220, 180, 200, 230, 200, 240, 190, 210, 200, 240, 220, 180, 200, 230, 200, 240, 190, 210, 200, 240, 220, 180, 200, 230, 200, 240, 190, 210, 200, 240, 220, 180, 200, 230, 200, 240, 190, 210, 200, 240, 220, 180, 200, 230, 200, 240, 190, 210, 200, 240, 220, 180, 200, 230, 200, 240, 190, 210, 200, 240, 220, 180, 200, 230, 200, 240, 190, 210, 200, 240}, n1 = {1000, 800, 780, 750, 900, 850, 830, 880, 950, 870, 1000, 800, 780, 750, 900, 850, 830, 880, 950, 870, 1000, 800, 780, 750, 900, 850, 830, 880, 950, 870, 1000, 800, 780, 750, 900, 850, 830, 880, 950, 870, 1000, 800, 780, 750, 900, 850, 830, 880, 950, 870, 1000, 800, 780, 750, 900, 850, 830, 880, 950, 870, 1000, 800, 780, 750, 900, 850, 830, 880, 950, 870, 1000, 800, 780, 750, 900, 850, 830, 880, 950, 870}, x2 = {440, 348, 420, 380, 460, 370, 400, 390, 470, 390, 440, 348, 420, 380, 460, 370, 400, 390, 470, 390, 440, 348, 420, 380, 460, 370, 400, 390, 470, 390, 440, 348, 420, 380, 460, 370, 400, 390, 470, 390, 440, 348, 420, 380, 460, 370, 400, 390, 470, 390, 440, 348, 420, 380, 460, 370, 400, 390, 470, 390, 440, 348, 420, 380, 460, 370, 400, 390, 470, 390, 440, 348, 420, 380, 460, 370, 400, 390, 470, 390}, n2 = {1000, 800, 780, 750, 900, 850, 830, 880, 950, 870, 1000, 800, 780, 750, 900, 850, 830, 880, 950, 870, 1000, 800, 780, 750, 900, 850, 830, 880, 950, 870, 1000, 800, 780, 750, 900, 850, 830, 880, 950, 870, 1000, 800, 780, 750, 900, 850, 830, 880, 950, 870, 1000, 800, 780, 750, 900, 850, 830, 880, 950, 870, 1000, 800, 780, 750, 900, 850, 830, 880, 950, 870, 1000, 800, 780, 750, 900, 850, 830, 880, 950, 870};
            int[] sel = new int[x1.length]; for (int i = 0; i < sel.length; i++) sel[i] = i;
            Glmm.Data g = new Glmm.Data("OR", "UM.RS", 1, x1, n1, x2, n2); Glmm.Result fe = Glmm.fit(g, sel, false, 0), re = Glmm.fit(g, sel, true, fe.est);
            near(re.qWald, 296.2068, 0.03, "GLMM UM.RS 80 studies: Wald-type Q agrees with metafor (lme4 stops a little short of the exact saturated-model sigma)");
            near(re.qWald, 296.2276, 2e-3, "GLMM UM.RS 80 studies: Wald-type Q at the exact saturated-model maximum");
            near(re.qLRT, 293.0248, 2e-3, "GLMM UM.RS 80 studies: likelihood-ratio Q agrees with metafor"); near(re.est, -1.0160170, 1e-5, "GLMM UM.RS 80 studies: estimate agrees with metafor"); near(re.tau2, 0.0334328, 1e-5, "GLMM UM.RS 80 studies: tau^2 agrees with metafor");
            check(fe.qWald == re.qWald, "GLMM UM.RS: the Wald-type Q does not depend on the model fitted (saturated model)");
            Glmm.Result nk = Glmm.fit(g, sel, true, fe.est, false, Double.NaN, true, Double.NaN), kn = Glmm.fit(g, sel, true, fe.est, false, Double.NaN, true, fe.qWald), nw = Glmm.fit(g, sel, true, fe.est, false, Double.NaN, false, Double.NaN);
            check(nk.qWald == re.qWald && kn.qWald == re.qWald, "GLMM UM.RS: the Wald-type Q is available without the likelihood-ratio test, and a known value is reused");
            check(Double.isNaN(nw.qWald) && nw.est == re.est, "GLMM UM.RS: wald = false skips the Wald-type Q only");
        }

        System.out.println(checks + " checks, " + failures + " failed");
        if (failures > 0) System.exit(1);
    }
}
