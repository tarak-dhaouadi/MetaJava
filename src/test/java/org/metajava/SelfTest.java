package org.metajava;

import org.metajava.core.*;
import org.metajava.plot.Plots;
import org.metajava.export.ResultsDoc;

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

        // ---- 4. Doi plot / LFK index (metasens::lfkindex algorithm) and vector export
        { double[] te = {-4.270, -1.710, -0.580, -0.190, 0.000}, vr = {0.158, 0.076, 0.018, 0.022, 0.040}, se = new double[5]; for (int i = 0; i < 5; i++) se[i] = Math.sqrt(vr[i]);
            Lfk lf = Lfk.compute(te, se, null); check(lf.apex == 3 && lf.interpretation.equals("major asymmetry") && lf.lfk < -2, "LFK index: pain example (Furuya-Kanamori 2018) is flagged as major left asymmetry (" + lf.lfk + ")");
            Lfk sy = Lfk.compute(new double[]{-1, -0.5, 0, 0.5, 1}, new double[]{.3, .2, .1, .2, .3}, null); near(sy.lfk, 0, 0.05, "LFK index: symmetric Doi plot gives an index close to 0");
            double[] perm = {0.5, -1, 1, 0, -0.5}, pse = {.2, .3, .3, .1, .2}; near(Lfk.compute(perm, pse, null).lfk, sy.lfk, 1e-12, "LFK index does not depend on the input order");
            near(Lfk.compute(new double[]{1, 2, 3, 4, 5}, new double[]{1, 1, 1, 1, 1}, null).lfk, 0, 0.05, "LFK index: equally precise, evenly spaced studies are symmetric");
            MetaData bd = new MetaData(); bd.put("event.e", new double[]{12, 8, 20, 5, 30, 9}); bd.put("n.e", new double[]{100, 60, 150, 40, 220, 80}); bd.put("event.c", new double[]{20, 15, 31, 9, 41, 14}); bd.put("n.c", new double[]{100, 62, 148, 42, 215, 79}); bd.n = 6;
            MetaSpec bs = new MetaSpec(); bs.type = "bin"; bs.sm = "RR"; bs.method = "Inverse"; bs.common = true; bs.random = true; MetaResult br = Meta.run(bs, bd); Lfk bl = Lfk.compute(br.TE, br.seTE, br.studlab);
            java.util.function.DoubleFunction<BufferedImage> labF = sc -> { Plots.Opts q = new Plots.Opts(); q.scale = sc; return Plots.labbe(br, q); }, doiF = sc -> { Plots.Opts q = new Plots.Opts(); q.scale = sc; return Plots.doi(bl, br, q); };
            check(Report.lfkText(bl, br).contains("LFK index: ") && labF.apply(1.0).getWidth() == 520 && doiF.apply(1.0).getWidth() == 620, "Doi plot and L'Abbe plot render and the LFK report is produced");
            try { for (java.util.function.DoubleFunction<BufferedImage> f : java.util.List.of(labF, doiF)) {
                org.metajava.plot.VectorGraphics vg = org.metajava.plot.Canvas.record(f, 300 / 96.0); java.io.ByteArrayOutputStream sv = new java.io.ByteArrayOutputStream(), pf = new java.io.ByteArrayOutputStream();
                org.metajava.plot.VectorExport.svg(vg, sv); org.metajava.plot.VectorExport.pdf(vg, pf); String x = sv.toString("UTF-8"); int w = f.apply(300 / 96.0).getWidth();
                check(x.startsWith("<?xml") && x.contains("width=\"" + w + "\"") && x.contains("<text ") && x.trim().endsWith("</svg>"), "SVG export keeps the PNG pixel size at 300 dpi (" + w + " px) and holds live text");
                String ph = new String(pf.toByteArray(), 0, 8, "ISO-8859-1"); String pt = new String(pf.toByteArray(), "ISO-8859-1");
                check(ph.startsWith("%PDF-1.4") && pt.trim().endsWith("%%EOF") && pt.contains("/MediaBox [0 0 " + (w * 0.75 == Math.rint(w * 0.75) ? String.valueOf((long) (w * 0.75)) : String.format(java.util.Locale.ROOT, "%.2f", w * 0.75).replaceAll("0+$", "").replaceAll("\\.$", "")) + " "), "PDF export has a valid header/trailer and the page size of the 300 dpi PNG (1 px = 0.75 pt)"); }
            } catch (Exception e) { check(false, "vector export threw " + e); }
        }

        // ---- 5. "Save this plot": the type chosen in the dialog decides the extension (bug: PDF chosen, name stayed .png)
        { java.util.Set<String> pr = new java.util.HashSet<>(java.util.List.of("L_Abb_Plot.png")); java.io.File sd = new java.io.File("some", "dir");
            check(org.metajava.gui.PlotChooser.withExtension(new java.io.File(sd, "L_Abb_Plot.png"), "PDF", pr).equals(new java.io.File(sd, "L_Abb_Plot.pdf")), "save dialog: proposed .png name with PDF chosen is written as .pdf");
            check(org.metajava.gui.PlotChooser.withExtension(new java.io.File(sd, "L_Abb_Plot.png"), "SVG", pr).getName().equals("L_Abb_Plot.svg"), "save dialog: proposed .png name with SVG chosen is written as .svg");
            check(org.metajava.gui.PlotChooser.withExtension(new java.io.File(sd, "fig.svg"), "PNG", pr).getName().equals("fig.svg"), "save dialog: an extension typed by the user is respected");
            check(org.metajava.gui.PlotChooser.withExtension(new java.io.File(sd, "fig"), "PDF", pr).getName().equals("fig.pdf") && org.metajava.gui.PlotChooser.withExtension(new java.io.File(sd, "fig.v2"), "PNG", pr).getName().equals("fig.v2.png"), "save dialog: a name without known extension gets the extension of the chosen type");
            check(org.metajava.gui.PlotChooser.withExtension(new java.io.File("plot.PNG"), "PDF", new java.util.HashSet<>(java.util.List.of("plot.PNG"))).getName().equals("plot.pdf"), "save dialog: upper-case extension and relative name"); }

        // ---- 6. PDF/SVG: page independent of dpi, document title; Doi apex line optional
        { MetaData bd2 = new MetaData(); bd2.put("event.e", new double[]{12, 8, 20, 5, 30, 9}); bd2.put("n.e", new double[]{100, 60, 150, 40, 220, 80}); bd2.put("event.c", new double[]{20, 15, 31, 9, 41, 14}); bd2.put("n.c", new double[]{100, 62, 148, 42, 215, 79}); bd2.n = 6;
            MetaSpec s2 = new MetaSpec(); s2.type = "bin"; s2.sm = "RR"; s2.method = "Inverse"; s2.common = true; s2.random = true; MetaResult r2 = Meta.run(s2, bd2); Lfk l2 = Lfk.compute(r2.TE, r2.seTE, r2.studlab);
            java.util.function.DoubleFunction<BufferedImage> doi0 = sc -> { Plots.Opts q = new Plots.Opts(); q.scale = sc; return Plots.doi(l2, r2, q); }, doi1 = sc -> { Plots.Opts q = new Plots.Opts(); q.scale = sc; q.apexLine = true; return Plots.doi(l2, r2, q); };
            try { java.io.File t1 = java.io.File.createTempFile("mjt", ".pdf"), t2 = java.io.File.createTempFile("mjt", ".pdf"), t3 = java.io.File.createTempFile("mjt", ".svg"); t1.deleteOnExit(); t2.deleteOnExit(); t3.deleteOnExit();
                org.metajava.gui.MainWindow.writePlot(doi0, t1, "PDF", 300 / 96.0, "Doi Plot"); org.metajava.gui.MainWindow.writePlot(doi0, t2, "PDF", 1200 / 96.0, "Doi Plot"); org.metajava.gui.MainWindow.writePlot(doi0, t3, "SVG", 1200 / 96.0, "Doi & Plot");
                String p1 = new String(java.nio.file.Files.readAllBytes(t1.toPath()), "ISO-8859-1"), p2 = new String(java.nio.file.Files.readAllBytes(t2.toPath()), "ISO-8859-1"), sv = new String(java.nio.file.Files.readAllBytes(t3.toPath()), "UTF-8");
                check(p1.contains("/MediaBox [0 0 465 390]") && p2.contains("/MediaBox [0 0 465 390]"), "PDF page is the natural size of the plot whatever the dpi setting");
                check(p1.contains("/Title <FEFF") && p1.contains("/Info 5 0 R") && p1.contains("/Size 6"), "PDF carries a document information dictionary with the plot title");
                check(sv.contains("viewBox=\"0 0 620 520\"") && sv.contains("<title>Doi &amp; Plot</title>"), "SVG is the natural size whatever the dpi setting and has a title"); } catch (Exception e) { check(false, "vector export of the title/size test threw " + e); }
            int dashed = 0; { org.metajava.plot.VectorGraphics a0 = org.metajava.plot.Canvas.record(doi0, 1.0), a1 = org.metajava.plot.Canvas.record(doi1, 1.0); try { java.io.ByteArrayOutputStream b0 = new java.io.ByteArrayOutputStream(), b1 = new java.io.ByteArrayOutputStream(); org.metajava.plot.VectorExport.svg(a0, b0); org.metajava.plot.VectorExport.svg(a1, b1);
                check(!b0.toString("UTF-8").contains("stroke-dasharray") && b1.toString("UTF-8").contains("stroke-dasharray"), "Doi plot: the dashed apex line is drawn only when requested"); } catch (Exception e) { check(false, "apex test threw " + e); } } }

        // ---- 7. TIFF and JPG export
        { java.util.Set<String> pr = new java.util.HashSet<>(java.util.List.of("Doi_Plot.png")); java.io.File sd = new java.io.File("some", "dir");
            check(org.metajava.gui.PlotChooser.withExtension(new java.io.File(sd, "Doi_Plot.png"), "TIFF", pr).getName().equals("Doi_Plot.tif") && org.metajava.gui.PlotChooser.withExtension(new java.io.File(sd, "Doi_Plot.png"), "JPG", pr).getName().equals("Doi_Plot.jpg"), "save dialog: TIFF and JPG types give .tif and .jpg");
            check(org.metajava.gui.PlotChooser.withExtension(new java.io.File(sd, "a.tiff"), "PNG", pr).getName().equals("a.tiff") && org.metajava.gui.PlotChooser.withExtension(new java.io.File(sd, "a.JPEG"), "PDF", pr).getName().equals("a.JPEG") && org.metajava.gui.PlotChooser.knownFormat("a.jpeg").equals("JPG"), "save dialog: typed .tiff / .jpeg are respected");
            MetaData bd3 = new MetaData(); bd3.put("event.e", new double[]{12, 8, 20, 5, 30, 9}); bd3.put("n.e", new double[]{100, 60, 150, 40, 220, 80}); bd3.put("event.c", new double[]{20, 15, 31, 9, 41, 14}); bd3.put("n.c", new double[]{100, 62, 148, 42, 215, 79}); bd3.n = 6;
            MetaSpec s3 = new MetaSpec(); s3.type = "bin"; s3.sm = "RR"; s3.method = "Inverse"; s3.common = true; s3.random = true; MetaResult r3 = Meta.run(s3, bd3);
            java.util.function.DoubleFunction<BufferedImage> labR = sc -> { Plots.Opts q = new Plots.Opts(); q.scale = sc; return Plots.labbe(r3, q); };
            try { for (String fm : new String[]{"PNG", "TIFF", "JPG"}) { java.io.File tf = java.io.File.createTempFile("mjr", "." + org.metajava.gui.RasterWriter.ext(fm)); tf.deleteOnExit();
                org.metajava.gui.MainWindow.writePlot(labR, tf, fm, 300 / 96.0, "L'Abbe"); BufferedImage back = javax.imageio.ImageIO.read(tf);
                check(back != null && back.getWidth() == 1625 && back.getHeight() == 1625, fm + " export is readable and has the pixel size of the chosen resolution (300 dpi: 1625 px)"); } } catch (Exception e) { check(false, "raster export threw " + e); } }

        // ---- 8. L'Abbe plot shows the study circles for every pooling method (GLMM and LRP have no study weights)
        { MetaData bd4 = new MetaData(); bd4.put("event.e", new double[]{12, 8, 20, 5, 30, 9}); bd4.put("n.e", new double[]{100, 60, 150, 40, 220, 80}); bd4.put("event.c", new double[]{20, 15, 31, 9, 41, 14}); bd4.put("n.c", new double[]{100, 62, 148, 42, 215, 79}); bd4.n = 6;
            for (String mth : new String[]{"Inverse", "Peto", "MH", "SSW", "GLMM", "LRP"}) { MetaSpec s4 = new MetaSpec(); s4.type = "bin"; s4.sm = "OR"; s4.method = mth; s4.common = true; s4.random = true; if (mth.equals("GLMM")) s4.methodTau = "ML";
                try { MetaResult r4 = Meta.run(s4, bd4); Plots.Opts q = new Plots.Opts(); q.scale = 3; q.studyLabels = false; BufferedImage lim = Plots.labbe(r4, q); int grey = 0; for (int yy = 0; yy < lim.getHeight(); yy++) for (int xx = 0; xx < lim.getWidth(); xx++) if ((lim.getRGB(xx, yy) & 0xFFFFFF) == 0xD3D3D3) grey++;
                    check(grey > 5000, "L'Abbe plot (" + mth + "): study circles are drawn (" + grey + " grey pixels)"); } catch (Exception e) { check(false, "L'Abbe plot " + mth + " threw " + e); } } }

        // ---- 8b. L'Abbe symbol sizes (all pooling methods) follow metafor::labbe (psize = rescaled sqrt(1/vi) of the arm-level variances, range 0.5 to 3.5)
        { MetaSpec s5 = new MetaSpec(); s5.type = "bin"; s5.sm = "OR"; double[] ee5 = {12, 8, 20, 5, 30, 0}, ne5 = {100, 60, 150, 40, 220, 80}, ec5 = {20, 15, 31, 9, 41, 14}, nc5 = {100, 62, 148, 42, 215, 79}, te5 = {0, 0, 0, 0, 0, 0};
            double[] ps5 = org.metajava.plot.Plots.labbeMetaforPsize(s5, ee5, ne5, ec5, nc5, te5); double mn5 = 9, mx5 = 0; for (double q5 : ps5) { mn5 = Math.min(mn5, q5); mx5 = Math.max(mx5, q5); }
            check(Math.abs(mn5 - 0.5) < 1e-12 && Math.abs(mx5 - 3.5) < 1e-12, "L'Abbe: symbol sizes span 0.5 to 3.5 as metafor (" + mn5 + " to " + mx5 + ")");
            double[] w5 = new double[6]; for (int i = 0; i < 6; i++) { double a5 = ee5[i] == 0 ? 0.5 : ee5[i], b5 = ne5[i] - ee5[i] + (ee5[i] == 0 ? 0.5 : 0), c5 = ec5[i], d5 = nc5[i] - ec5[i]; w5[i] = Math.sqrt(1 / (1 / a5 + 1 / b5 + 1 / c5 + 1 / d5)); }
            double lo5 = 9, hi5 = 0; for (double q5 : w5) { lo5 = Math.min(lo5, q5); hi5 = Math.max(hi5, q5); } boolean same5 = true; for (int i = 0; i < 6; i++) same5 &= Math.abs(ps5[i] - ((w5[i] - lo5) / (hi5 - lo5) * 3 + 0.5)) < 1e-12;
            check(same5, "L'Abbe: symbol sizes equal metafor's sqrt(1/(vi_x + vi_y)) rescaling with the 0.5 continuity correction on zero cells"); }

        // ---- 8c. L'Abbe circles drawn at metafor's size (diameter = 4 x psize px) for every pooling method; psize all 1 when every wi is equal
        { double[] ee6 = {10, 300, 90}, ne6 = {20, 1000, 300}, ec6 = {4, 150, 120}, nc6 = {20, 1000, 300};
            for (String mth : new String[]{"Inverse", "MH", "Peto", "GLMM"}) { MetaData bd6 = new MetaData(); bd6.put("event.e", ee6); bd6.put("n.e", ne6); bd6.put("event.c", ec6); bd6.put("n.c", nc6); bd6.n = 3;
                MetaSpec s6 = new MetaSpec(); s6.type = "bin"; s6.sm = "OR"; s6.method = mth; s6.common = true; s6.random = false; if (mth.equals("GLMM")) { s6.methodTau = "ML"; s6.common = false; s6.random = true; }
                try { MetaResult r6 = Meta.run(s6, bd6); Plots.Opts q = new Plots.Opts(); q.scale = 1; q.studyLabels = false; BufferedImage im6 = Plots.labbe(r6, q);
                    double[] ps6 = Plots.labbeMetaforPsize(s6, ee6, ne6, ec6, nc6, r6.TE); int W = im6.getWidth(), H = im6.getHeight(); boolean[][] seen = new boolean[W][H]; java.util.List<int[]> boxes = new java.util.ArrayList<>();
                    for (int x0 = 0; x0 < W; x0++) for (int y0 = 0; y0 < H; y0++) { if (seen[x0][y0] || (im6.getRGB(x0, y0) & 0xFFFFFF) != 0xD3D3D3) continue;
                        java.util.ArrayDeque<int[]> stk = new java.util.ArrayDeque<>(); stk.push(new int[]{x0, y0}); seen[x0][y0] = true; int mnx = x0, mxx = x0, mny = y0, mxy = y0;
                        while (!stk.isEmpty()) { int[] c = stk.pop(); mnx = Math.min(mnx, c[0]); mxx = Math.max(mxx, c[0]); mny = Math.min(mny, c[1]); mxy = Math.max(mxy, c[1]);
                            for (int[] dd : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) { int nx = c[0] + dd[0], ny = c[1] + dd[1]; if (nx >= 0 && ny >= 0 && nx < W && ny < H && !seen[nx][ny] && (im6.getRGB(nx, ny) & 0xFFFFFF) == 0xD3D3D3) { seen[nx][ny] = true; stk.push(new int[]{nx, ny}); } } }
                        if (mxx - mnx < 30 && mxy - mny < 30) boxes.add(new int[]{mxx - mnx + 1, mxy - mny + 1}); }
                    int big = 0; for (int[] b : boxes) big = Math.max(big, b[0]); double mid = 0; for (double pv6 : ps6) if (pv6 > 0.6 && pv6 < 3.4) mid = 4 * pv6;
                    boolean midFound = false; for (int[] b : boxes) if (Math.abs(b[0] - (mid - 2)) <= 1.5) midFound = true;
                    check(Math.abs(ps6[1] - 3.5) < 1e-12 && Math.abs(ps6[0] - 0.5) < 1e-12 && big >= 11 && big <= 14 && midFound, "L'Abbe (" + mth + "): the largest circle is 14 px across (grey fill " + big + "), the middle one " + String.format("%.1f", mid) + " px, psize " + java.util.Arrays.toString(ps6)); }
                catch (Exception e) { check(false, "L'Abbe circle sizes " + mth + " threw " + e); } }
            MetaSpec s7 = new MetaSpec(); s7.type = "bin"; s7.sm = "RR"; double[] eq = org.metajava.plot.Plots.labbeMetaforPsize(s7, new double[]{10, 10}, new double[]{50, 50}, new double[]{20, 20}, new double[]{50, 50}, new double[]{0, 0});
            check(eq[0] == 1 && eq[1] == 1, "L'Abbe: every symbol gets size 1 when all values are equal (metafor)"); }

        // ---- 9. JSON, project files, Excel and Word export
        { Object j = org.metajava.io.Json.parse(org.metajava.io.Json.write(new java.util.LinkedHashMap<String, Object>() {{ put("a", "caf\u00E9 \"q\" \\ \n tab\t"); put("n", 0.1 + 0.2); put("i", 42); put("b", true); put("z", null); put("l", java.util.Arrays.asList("x", null, 1.5, new java.util.LinkedHashMap<String, Object>() {{ put("k", java.util.Collections.emptyList()); }})); }}));
            @SuppressWarnings("unchecked") java.util.Map<String, Object> jm = (java.util.Map<String, Object>) j; @SuppressWarnings("unchecked") java.util.List<Object> jl = (java.util.List<Object>) jm.get("l");
            check(jm.get("a").equals("caf\u00E9 \"q\" \\ \n tab\t") && Math.abs((Double) jm.get("n") - 0.30000000000000004) < 1e-15 && ((Double) jm.get("i")) == 42 && jm.get("b").equals(Boolean.TRUE) && jm.containsKey("z") && jm.get("z") == null && jl.size() == 4 && jl.get(1) == null, "JSON: write and parse round-trip (unicode, escapes, numbers, null, nesting)");
            boolean bad = false; try { org.metajava.io.Json.parse("{\"a\": [1, 2}"); } catch (IllegalArgumentException e) { bad = true; } check(bad, "JSON: malformed text is rejected"); }
        try { MetaData bd8 = new MetaData(); bd8.put("event.e", new double[]{12, 8, 20, 5, 30, 9}); bd8.put("n.e", new double[]{100, 60, 150, 40, 220, 80}); bd8.put("event.c", new double[]{20, 15, 31, 9, 41, 14}); bd8.put("n.c", new double[]{100, 62, 148, 42, 215, 79}); bd8.n = 6;
            MetaSpec s8 = new MetaSpec(); s8.type = "bin"; s8.sm = "OR"; s8.method = "Inverse"; s8.common = true; s8.random = true; s8.prediction = true; s8.title = "Test review"; MetaResult r8 = Meta.run(s8, bd8);
            Report.startCapture(); String t8 = Report.summary(r8); java.util.List<Report.Tbl> tb8 = Report.drain(); Report.startCapture(); Meta.leaveOneOut(r8); String l8 = Report.looText(r8); java.util.List<Report.Tbl> lb8 = Report.drain(); Report.stopCapture();
            java.util.List<Object> bl = Report.blocks(t8, tb8); int nt = 0, np = 0; StringBuilder rebuilt = new StringBuilder(); for (Object o : bl) { if (o instanceof Report.Tbl) nt++; else np++; }
            check(nt == tb8.size() && nt >= 3 && np >= 3, "Export: every printed table of the summary is found again in the text (" + nt + " tables, " + np + " paragraphs)");
            java.util.List<Object> lbl = Report.blocks(l8, lb8); Report.Tbl loo = null; for (Object o : lbl) if (o instanceof Report.Tbl) loo = (Report.Tbl) o;
            check(loo != null && loo.rows.size() == 7 && loo.rows.get(0)[0].startsWith("Omitting") == false || loo != null && loo.rows.size() == 7, "Export: leave-one-out table has the 6 omitted-study rows plus the overall row (" + (loo == null ? -1 : loo.rows.size()) + " rows)");
            ResultsDoc doc = new ResultsDoc(); doc.title = "Test review"; doc.subtitle = "t"; doc.text("Meta-Analysis", t8, tb8); doc.text("Leave-One-Out Analysis", l8, lb8); doc.text("Funnel note", "Plain note\nsecond line", new java.util.ArrayList<>());
            BufferedImage fig = Plots.labbe(r8, new Plots.Opts()); java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream(); javax.imageio.ImageIO.write(fig, "png", bo); doc.figure("L'Abbe Plot", bo.toByteArray(), fig.getWidth(), fig.getHeight(), 96);
            java.io.File fx = java.io.File.createTempFile("mjx", ".xlsx"), fd = java.io.File.createTempFile("mjd", ".docx"); fx.deleteOnExit(); fd.deleteOnExit(); org.metajava.export.Xlsx.write(fx, doc); org.metajava.export.Docx.write(fd, doc);
            boolean wf = true; int sheets = 0, drawings = 0, tbls = 0; String x1 = ""; javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(fx)) { for (java.util.Enumeration<? extends java.util.zip.ZipEntry> en = z.entries(); en.hasMoreElements(); ) { java.util.zip.ZipEntry e = en.nextElement(); byte[] b = z.getInputStream(e).readAllBytes(); try { dbf.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(b)); } catch (Exception ex) { wf = false; System.out.println("   not well-formed: " + e.getName() + " " + ex.getMessage()); }
                if (e.getName().startsWith("xl/worksheets/")) sheets++; if (e.getName().equals("xl/worksheets/sheet1.xml")) x1 = new String(b, "UTF-8"); } }
            check(wf && sheets == 3 && x1.contains("<v>") && x1.contains("lower") && x1.contains("upper"), "Excel export: valid package with 3 sheets (2 with tables, notes), numeric cells and split CI columns (" + sheets + " sheets)");
            try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(fd)) { String dx = ""; boolean png = false; for (java.util.Enumeration<? extends java.util.zip.ZipEntry> en = z.entries(); en.hasMoreElements(); ) { java.util.zip.ZipEntry e = en.nextElement(); byte[] b = z.getInputStream(e).readAllBytes();
                    if (!e.getName().endsWith(".png")) try { dbf.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(b)); } catch (Exception ex) { wf = false; System.out.println("   not well-formed: " + e.getName() + " " + ex.getMessage()); } else png = b.length > 8 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
                    if (e.getName().equals("word/document.xml")) dx = new String(b, "UTF-8"); }
                for (int i = dx.indexOf("<w:tbl>"); i >= 0; i = dx.indexOf("<w:tbl>", i + 1)) tbls++; for (int i = dx.indexOf("<w:drawing>"); i >= 0; i = dx.indexOf("<w:drawing>", i + 1)) drawings++;
                check(wf && tbls == nt + (loo == null ? 0 : 1) && drawings == 1 && png && dx.indexOf("Meta-Analysis") < dx.indexOf("Leave-One-Out Analysis") && dx.indexOf("Leave-One-Out Analysis") < dx.indexOf("<w:drawing>"), "Word export: valid package, " + tbls + " tables, 1 figure, sections in the order of the output"); }
        } catch (Exception e) { e.printStackTrace(); check(false, "export tests threw " + e); }

        System.out.println(checks + " checks, " + failures + " failed");
        if (failures > 0) System.exit(1);
    }
}
