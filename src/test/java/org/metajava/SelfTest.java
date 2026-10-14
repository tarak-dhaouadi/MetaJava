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

    static boolean same(BufferedImage a, BufferedImage b) { if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return false; for (int y = 0; y < a.getHeight(); y++) for (int x = 0; x < a.getWidth(); x++) if (a.getRGB(x, y) != b.getRGB(x, y)) return false; return true; }
    static int darkCount(BufferedImage im, int x0, int y0, int x1, int y1) { int n = 0; for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) { int rgb = im.getRGB(x, y); if ((rgb >> 16 & 255) < 60 && (rgb >> 8 & 255) < 60 && (rgb & 255) < 60) n++; } return n; }
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

    /** Pixels close to the colour rgb in the top (top = true) or right marginal panel of an ordinary GOSH plot drawn at scale 1 (frame x 72..600, y 112..548). */
    static int marg(BufferedImage im, int[] rgb, boolean top) { int n = 0, x0 = top ? 72 : 604, x1 = top ? 600 : im.getWidth(), y0 = top ? 0 : 112, y1 = top ? 108 : 548;
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) { int c = im.getRGB(x, y); if (Math.abs(((c >> 16) & 255) - rgb[0]) + Math.abs(((c >> 8) & 255) - rgb[1]) + Math.abs((c & 255) - rgb[2]) < 40) n++; } return n; }
    /** Pixels within a small distance of the colour rgb (anti-aliased lines never hit the exact colour). */
    static int near(BufferedImage im, int rgb) { int n = 0, r0 = (rgb >> 16) & 255, g0 = (rgb >> 8) & 255, b0 = rgb & 255; for (int y = 0; y < im.getHeight(); y++) for (int x = 0; x < im.getWidth(); x++) { int c = im.getRGB(x, y); if (Math.abs(((c >> 16) & 255) - r0) + Math.abs(((c >> 8) & 255) - g0) + Math.abs((c & 255) - b0) < 30) n++; } return n; }
    static boolean sameImage(BufferedImage a, BufferedImage b) { if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return false; for (int y = 0; y < a.getHeight(); y++) for (int x = 0; x < a.getWidth(); x++) if (a.getRGB(x, y) != b.getRGB(x, y)) return false; return true; }
    static int countColour(BufferedImage im, int rgb) { int n = 0; for (int y = 0; y < im.getHeight(); y++) for (int x = 0; x < im.getWidth(); x++) if ((im.getRGB(x, y) & 0xFFFFFF) == rgb) n++; return n; }

    // ------------------------------------------------------------------ cluster detection: separation criterion, tests on data with and without structure
    /** Simulated meta-analysis: k studies, standard errors 0.1-0.25, true effect 0.3 (+ shift for the studies listed), one unrelated covariate x. kind: 0 homogeneous (tau^2 = 0), 1 two outliers (+1.5), 2 a subgroup of 3 studies (+1.2). */
    static double[][] simData(int kind, long seed, int k) {
        java.util.Random rnd = new java.util.Random(seed); double[] se = new double[k], th = new double[k], y = new double[k], x = new double[k]; for (int i = 0; i < k; i++) se[i] = 0.1 + 0.15 * rnd.nextDouble();
        if (kind == 1) { th[0] = 1.5; th[1] = 1.5; } if (kind == 2) for (int i = k - 3; i < k; i++) th[i] = 1.2;
        for (int i = 0; i < k; i++) { y[i] = 0.3 + th[i] + se[i] * rnd.nextGaussian(); x[i] = rnd.nextGaussian(); } return new double[][]{y, se, x};
    }
    static MetaResult simResult(double[] y, double[] se) { MetaSpec sp = new MetaSpec(); sp.type = "gen"; sp.sm = "MD"; sp.methodTau = "REML"; MetaData md = new MetaData(); md.put("TE", y); md.put("seTE", se); md.n = y.length; return Meta.run(sp, md); }
    static boolean separatesAny(GoshCluster c, int lo, int hi) { for (int g = 0; g < c.G; g++) for (int[] jj : c.separating(g)) if (jj[0] >= lo && jj[0] <= hi) return true; return false; }

    /** Pixels that are clearly orange (the colour of the subsets that exclude the highlighted study). */
    static int orangePixels(BufferedImage im) { int n = 0; for (int y = 0; y < im.getHeight(); y++) for (int x = 0; x < im.getWidth(); x++) { int c = im.getRGB(x, y); int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255; if (r > 180 && r - g > 40 && g - b > 20) n++; } return n; }

    /** Ordinary GOSH plot: option to limit the x axis to the central 99% of the subsets (heavy-tailed effects, so that a few subsets are far out). */
    static void ordinaryAxisLimitTests() {
        java.util.Random rnd = new java.util.Random(1); int k = 12; double[] y = new double[k], se = new double[k]; for (int i = 0; i < k; i++) { se[i] = 0.1 + 0.15 * rnd.nextDouble(); y[i] = 0.3 + 0.3 * Math.tan(Math.PI * (rnd.nextDouble() - 0.5)) + se[i] * rnd.nextGaussian(); }
        MetaResult res = simResult(y, se); Gosh go = Gosh.run(res, 8000, 1); int nSub = go.nSub, nValid = 0; for (int b = 0; b < go.nSub; b++) if (!Double.isNaN(go.est[b]) && !Double.isNaN(go.i2[b])) nValid++;
        java.util.List<String> on = org.metajava.plot.ExtraPlots.goshAxisNotes(go, res, true), off = org.metajava.plot.ExtraPlots.goshAxisNotes(go, res, false);
        String txt = String.join(" ", on); java.util.regex.Matcher mt = java.util.regex.Pattern.compile("\\(([0-9.]+)%\\) outside").matcher(txt); boolean has = mt.find(); double pct = has ? Double.parseDouble(mt.group(1)) : -1;
        check(!on.isEmpty() && txt.contains("central 99%") && txt.contains("x axis") && has && pct > 0 && pct <= 5, "ordinary GOSH plot: extreme subsets limit the x axis to the central 99%, with the displayed range and the share of subsets outside stated (" + pct + "%)");
        int nValid2 = 0; for (int b = 0; b < go.nSub; b++) if (!Double.isNaN(go.est[b]) && !Double.isNaN(go.i2[b])) nValid2++;
        check(off.isEmpty() && go.nSub == nSub && nValid2 == nValid, "ordinary GOSH plot: nothing is stated when the axis is not limited; all " + nValid + " valid subsets stay in the analysis");
        Plots.Opts po = new Plots.Opts(); po.scale = 1.0; BufferedImage t1 = org.metajava.plot.ExtraPlots.gosh(go, res, po, false, null, true), t0 = org.metajava.plot.ExtraPlots.gosh(go, res, po, false, null, false), t00 = org.metajava.plot.ExtraPlots.gosh(go, res, po, false);
        check(t1.getHeight() > t0.getHeight() && t0.getHeight() == t00.getHeight() && t0.getWidth() == t1.getWidth(), "ordinary GOSH plot with the limited axis carries the note (height " + t1.getHeight() + " against " + t0.getHeight() + "); without the option the plot is as before");
        double[][] dd = simData(0, 1, 5); MetaResult rh = simResult(dd[0], dd[1]); Gosh gh = Gosh.run(rh, 8000, 1);   // 26 subsets: the central 99% is almost the full range
        check(org.metajava.plot.ExtraPlots.goshAxisNotes(gh, rh, true).isEmpty(), "ordinary GOSH plot: the axis is not limited when the central 99% is not more than 25% narrower than the full range");
    }

    static void clusterGateTests() {
        ordinaryAxisLimitTests();
        java.util.List<String> noF = java.util.List.of(); java.util.Map<String, String[]> noFd = new java.util.HashMap<>();
        // (a) homogeneous data (tau^2 = 0): the mixture alone proposes several components, because many subsets have I^2 = 0 exactly (a spike), but they are not clusters
        for (long seed = 1; seed <= 3; seed++) {
            double[][] dd = simData(0, seed, 12); MetaResult res = simResult(dd[0], dd[1]); java.util.Map<String, double[]> nd = new java.util.HashMap<>(); nd.put("x", dd[2]);
            Gosh go = Gosh.run(res, 8000, 1); GoshCluster co = go.detectClusters();
            check(co.iclG >= 2 && co.spike >= 0.5, "homogeneous data " + seed + " (ordinary): ICL alone proposes " + co.iclG + " components and " + Math.round(100 * co.spike) + "% of the subsets have I^2 = 0");
            check(co.G == 1 && !co.found(), "homogeneous data " + seed + " (ordinary): reported as one cluster, no meaningful clusters (G = " + co.G + ")");
            GoshReg gg = GoshReg.run(res, Reg.design(res, java.util.List.of("x"), nd, noF, noFd, true, null), true, 8000, 1); GoshCluster cg = gg.detectClusters();
            check(cg.iclG >= 2 && cg.G == 1 && !cg.found(), "homogeneous data " + seed + " (meta-regression): ICL proposes " + cg.iclG + " components, reported as one cluster (G = " + cg.G + ")");
            check(RegReport.goshText(go, res).contains("No meaningful clusters") && RegReport.goshRegText(gg, res).contains("No meaningful clusters"), "homogeneous data " + seed + ": the report says that there are no meaningful clusters");
        }
        // (b) the spike alone is not structure: estimates independent of I^2, 40% of the subsets at I^2 = 0
        { java.util.SplittableRandom rg = new java.util.SplittableRandom(11); int nn = 3000, kk = 10; double[][] Xs = new double[nn][2]; long[][] mk = new long[nn][1]; int[] szs = new int[nn]; double[] hf = new double[nn];
          for (int b = 0; b < nn; b++) { double u1 = rg.nextDouble(), u2 = rg.nextDouble(); Xs[b][0] = 0.3 + 0.05 * Math.sqrt(-2 * Math.log(1 - u1)) * Math.cos(2 * Math.PI * u2); Xs[b][1] = rg.nextDouble() < 0.4 ? 0 : 5 + 40 * rg.nextDouble();
            long m = 0; for (int j = 0; j < kk; j++) if (rg.nextBoolean()) m |= 1L << j; mk[b][0] = m; szs[b] = Long.bitCount(m); double w1 = rg.nextDouble(), w2 = rg.nextDouble(); hf[b] = Math.sqrt(-2 * Math.log(1 - w1)) * Math.cos(2 * Math.PI * w2); }
          GoshCluster c0 = GoshCluster.detect(Xs, new String[]{"estimate", "I^2"}, mk, kk, szs, 1); GoshCluster c1 = GoshCluster.detect(Xs, new String[]{"estimate", "I^2"}, mk, kk, szs, 1, 1, GoshCluster.MIN_SILHOUETTE, GoshCluster.MIN_SHARE, hf);
          check(c0.iclG >= 2 && c0.G == 1, "spike at I^2 = 0 only: ICL proposes " + c0.iclG + " components, no cluster is reported (G = " + c0.G + ")");
          check(c1.iclG >= 2 && c1.G == 1, "spike at I^2 = 0 only, with a continuous heterogeneity measure: no cluster is reported (G = " + c1.G + ")"); }
        // (c) genuine outliers: two studies far from the others
        for (long seed = 1; seed <= 2; seed++) {
            double[][] dd = simData(1, seed, 12); MetaResult res = simResult(dd[0], dd[1]); java.util.Map<String, double[]> nd = new java.util.HashMap<>(); nd.put("x", dd[2]);
            Gosh go = Gosh.run(res, 8000, 1); GoshCluster co = go.detectClusters(); GoshReg gg = GoshReg.run(res, Reg.design(res, java.util.List.of("x"), nd, noF, noFd, true, null), true, 8000, 1); GoshCluster cg = gg.detectClusters();
            check(co.found() && separatesAny(co, 0, 1), "two outlier studies " + seed + " (ordinary): " + co.G + " clusters, the outliers are among the studies that separate them");
            check(cg.found() && separatesAny(cg, 0, 1), "two outlier studies " + seed + " (meta-regression): " + cg.G + " clusters, the outliers are among the studies that separate them");
            check(RegReport.goshText(go, res).contains("Clusters found") && RegReport.goshRegText(gg, res).contains("Clusters found"), "two outlier studies " + seed + ": the report lists the clusters");
            check(co.share != null && Math.abs(java.util.Arrays.stream(co.share).sum() - 100) < 1e-6, "two outlier studies " + seed + ": cluster shares add up to 100%");
        }
        // (d) a real subgroup: 3 of 12 studies with a much larger effect
        for (long seed = 1; seed <= 2; seed++) {
            double[][] dd = simData(2, seed, 12); MetaResult res = simResult(dd[0], dd[1]); java.util.Map<String, double[]> nd = new java.util.HashMap<>(); nd.put("x", dd[2]);
            GoshCluster co = Gosh.run(res, 8000, 1).detectClusters(); GoshCluster cg = GoshReg.run(res, Reg.design(res, java.util.List.of("x"), nd, noF, noFd, true, null), true, 8000, 1).detectClusters();
            check(co.found() && separatesAny(co, 9, 11), "subgroup of 3 studies " + seed + " (ordinary): " + co.G + " clusters, subgroup studies separate them");
            check(cg.found() && separatesAny(cg, 9, 11), "subgroup of 3 studies " + seed + " (meta-regression): " + cg.G + " clusters, subgroup studies separate them");
        }
        // (e) the threshold can be changed: nothing is separated well enough for 0.97
        { double[][] dd = simData(1, 1, 12); MetaResult res = simResult(dd[0], dd[1]); Gosh go = Gosh.run(res, 8000, 1); GoshCluster strict = go.detectClusters(0.97, 0.97, GoshCluster.HET_MIN_SHARE);
          check(GoshCluster.MIN_SILHOUETTE == 0.60 && GoshCluster.MIN_SHARE == 0.05 && strict.G == 1 && strict.iclG >= 2, "separation threshold is configurable (0.97: one cluster although ICL proposed " + strict.iclG + ")");
          check(go.detectClusters().minSil == GoshCluster.MIN_SILHOUETTE, "default separation threshold is used by detectClusters()"); }
        // (f) in / out means per study (computed in parallel) equal a plain serial computation
        { double[][] dd = simData(1, 2, 12); MetaResult res = simResult(dd[0], dd[1]); java.util.Map<String, double[]> nd = new java.util.HashMap<>(); nd.put("x", dd[2]); Gosh go = Gosh.run(res, 5000, 3); GoshReg gg = GoshReg.run(res, Reg.design(res, java.util.List.of("x"), nd, noF, noFd, true, null), true, 5000, 3);
          double worst = 0; for (int j = 0; j < go.k; j++) { double si = 0, so = 0; int ni = 0, no = 0; for (int b = 0; b < go.nSub; b++) { if (Double.isNaN(go.est[b]) || Double.isNaN(go.i2[b])) continue; if (go.includes(b, j)) { si += go.i2[b]; ni++; } else { so += go.i2[b]; no++; } }
              worst = Math.max(worst, Math.max(Math.abs(si / ni - go.i2In[j]), Math.abs(so / no - go.i2Out[j]))); }
          double worstR = 0; for (int j = 0; j < gg.k; j++) for (int a = 0; a < gg.p; a++) { double si = 0, so = 0; int ni = 0, no = 0; for (int b = 0; b < gg.nSub; b++) { if (!gg.valid(b)) continue; boolean in = (gg.mask[b][j >> 6] >>> (j & 63) & 1L) != 0; if (in) { si += gg.coef[a][b]; ni++; } else { so += gg.coef[a][b]; no++; } }
              worstR = Math.max(worstR, Math.max(Math.abs(si / ni - gg.coefIn[a][j]), Math.abs(so / no - gg.coefOut[a][j]))); }
          check(worst == 0 && worstR == 0, "in / out means per study (parallel) equal the serial computation (ordinary " + worst + ", meta-regression " + worstR + ")");
          double[][] d1 = simData(1, 1, 12); MetaResult r1 = simResult(d1[0], d1[1]); java.util.Map<String, double[]> n1b = new java.util.HashMap<>(); n1b.put("x", d1[2]); gg = GoshReg.run(r1, Reg.design(r1, java.util.List.of("x"), n1b, noF, noFd, true, null), true, 5000, 3);   // two outliers, seed 1: clusters are found
          GoshCluster c = gg.detectClusters(); double tot = 0; if (c.found()) for (int g = 0; g < c.G; g++) for (int j = 0; j < c.rateIn[g].length; j++) { int cnt = 0, sz = 0; for (int b = 0; b < gg.nSub; b++) if (c.label[b] == g) { sz++; if ((gg.mask[b][j >> 6] >>> (j & 63) & 1L) != 0) cnt++; } tot = Math.max(tot, Math.abs((double) cnt / sz - c.rateIn[g][j])); }
          check(c.found() && tot < 1e-12, "cluster inclusion rates (parallel) equal the serial computation"); }
        // (g) GOSH plots: central 99% axis limits are disclosed, no subset is removed
        { double[] ta = {4, 6, 3, 62, 33, 180, 8, 505, 29, 17, 186, 5, 27}, n1 = {123, 306, 231, 13598, 5069, 1541, 2545, 88391, 7499, 1716, 50634, 2498, 16913}, tc = {11, 29, 11, 248, 47, 372, 10, 499, 45, 65, 141, 3, 29}, n2 = {139, 303, 220, 12867, 5808, 1451, 629, 88391, 7277, 1665, 27338, 2341, 17854};
          double[] ablat = {44, 55, 42, 52, 13, 44, 19, 13, 27, 42, 18, 33, 33}, year = {48, 49, 60, 77, 73, 53, 73, 80, 68, 61, 74, 69, 76}; String[] alloc = {"random", "random", "random", "random", "alt", "alt", "sys", "random", "random", "sys", "sys", "alt", "random"};
          int K = 13; double[] by = new double[K], bse = new double[K]; for (int i = 0; i < K; i++) { by[i] = Math.log((ta[i] / n1[i]) / (tc[i] / n2[i])); bse[i] = Math.sqrt(1 / ta[i] - 1 / n1[i] + 1 / tc[i] - 1 / n2[i]); }
          MetaResult rb = simResult(by, bse); java.util.Map<String, double[]> nd = new java.util.HashMap<>(); nd.put("ablat", ablat); nd.put("year", year); java.util.Map<String, String[]> fd = new java.util.HashMap<>(); fd.put("alloc", alloc);
          GoshReg gb = GoshReg.run(rb, Reg.design(rb, java.util.List.of("ablat", "year"), nd, java.util.List.of("alloc"), fd, true, null), true, 20000, 1); int nv = gb.nValid;
          java.util.List<String> notes = org.metajava.plot.ExtraPlots.goshRegAxisNotes(gb, true), none = org.metajava.plot.ExtraPlots.goshRegAxisNotes(gb, false);
          String all = String.join(" ", notes); java.util.regex.Matcher mt = java.util.regex.Pattern.compile("\\(([0-9.]+)%\\) outside").matcher(all); double maxPct = 0; int found = 0; while (mt.find()) { found++; maxPct = Math.max(maxPct, Double.parseDouble(mt.group(1))); }
          check(!notes.isEmpty() && all.contains("central 99%") && found >= 1 && maxPct > 0 && maxPct <= 5, "regression GOSH plots: extreme coefficients limit the axes, with the displayed range and the share of subsets outside stated (max " + maxPct + "%)");
          check(none.isEmpty() && gb.nValid == nv, "regression GOSH plots: nothing is stated or removed when the axes are not limited; all " + nv + " valid subsets stay in the analysis");
          Plots.Opts po = new Plots.Opts(); po.scale = 1.0; BufferedImage a1 = org.metajava.plot.ExtraPlots.goshRegCoef(gb, po, true), a0 = org.metajava.plot.ExtraPlots.goshRegCoef(gb, po, false), m1 = org.metajava.plot.ExtraPlots.goshRegMatrix(gb, po, true);
          { int hl = gb.mostInfluential(); double bd = -1; int arg = 0; for (int j = 0; j < gb.k; j++) { double d = Math.abs(gb.i2In[j] - gb.i2Out[j]); if (d > bd) { bd = d; arg = j; } }
            BufferedImage plain = org.metajava.plot.ExtraPlots.goshRegCoef(gb, po, true, false), hlc = org.metajava.plot.ExtraPlots.goshRegCoef(gb, po, true, true), hlm = org.metajava.plot.ExtraPlots.goshRegMatrix(gb, po, true, true), plm = org.metajava.plot.ExtraPlots.goshRegMatrix(gb, po, true, false);
            check(hl == arg && hl >= 0 && hl < gb.k, "meta-regression GOSH: most influential study = largest difference in mean residual I^2 between the subsets with and without it (" + gb.labels[hl] + ")");
            check(orangePixels(plain) == 0 && orangePixels(plm) == 0 && orangePixels(hlc) > 50 && orangePixels(hlm) > 50, "meta-regression GOSH plots are coloured by inclusion of the most influential study when asked (as the ordinary GOSH plot): " + orangePixels(hlc) + " and " + orangePixels(hlm) + " orange pixels, none without the option"); }
          check(a1.getHeight() > a0.getHeight() && m1.getWidth() >= 560, "regression GOSH plots with limited axes carry the note (" + a1.getWidth() + "x" + a1.getHeight() + " against " + a0.getWidth() + "x" + a0.getHeight() + ")"); }
    }

    /** Simulated meta-analysis (standard errors 0.08-0.33): tau between studies, the first nOut studies shifted; x is an unrelated covariate with slope 0.3 (meta-regression). */
    static MetaResult robustMeta(long seed, int k, double tau, int nOut, double shift, double slope, double[] x) {
        java.util.Random R = new java.util.Random(seed); double[] y = new double[k], se = new double[k];
        for (int i = 0; i < k; i++) { se[i] = 0.08 + 0.25 * R.nextDouble(); x[i] = R.nextGaussian(); y[i] = 0.2 + slope * x[i] + tau * R.nextGaussian() + se[i] * R.nextGaussian() + (i < nOut ? shift : 0); }
        return simResult(y, se);
    }
    static GoshCluster[] robustPair(long seed, int k, double tau, int nOut, double shift, double hetMin) {
        double[] x0 = new double[k], x = new double[k]; MetaResult r0 = robustMeta(seed, k, tau, nOut, shift, 0, x0), r = robustMeta(seed, k, tau, nOut, shift, 0.3, x); java.util.Map<String, double[]> nd = new java.util.HashMap<>(); nd.put("x", x);   // the covariate has a real effect only in the meta-regression data
        GoshCluster a = Gosh.run(r0, 4000, seed).detectClusters(GoshCluster.MIN_SILHOUETTE, hetMin, GoshCluster.HET_MIN_SHARE);
        GoshCluster b = GoshReg.run(r, Reg.design(r, java.util.List.of("x"), nd, java.util.List.of(), new java.util.HashMap<>(), true, null), true, 4000, seed).detectClusters(GoshCluster.MIN_SILHOUETTE, hetMin, GoshCluster.HET_MIN_SHARE);
        return new GoshCluster[]{a, b};
    }

    /**
     * Several data sets per scenario (not a single lucky seed), including heterogeneity without outliers (where the first version reported clusters in 15-20% of the
     * data sets) and weaker signals than the other tests. The limits are set from the simulation in the CHANGELOG (24 data sets per scenario) with a margin for the small number here.
     */
    static void clusterRobustnessTests() {
        int n = 6, fpO = 0, fpR = 0, fpLoose = 0, hetO = 0, hetR = 0, out2O = 0, out2R = 0, sepOk = 0, sepN = 0, out1O = 0, out1R = 0;
        for (long sd = 1; sd <= n; sd++) {
            GoshCluster[] h = robustPair(sd, 15, 0.3, 0, 0, GoshCluster.HET_MIN_SILHOUETTE); if (h[0].found()) fpO++; if (h[1].found()) fpR++;
            GoshCluster[] hl = robustPair(sd, 15, 0.3, 0, 0, GoshCluster.MIN_SILHOUETTE); if (hl[0].found()) fpLoose++; if (hl[1].found()) fpLoose++;   // heterogeneity route as lenient as the effects route (the first version)
            GoshCluster[] b = robustPair(sd, 25, 0.5, 0, 0, GoshCluster.HET_MIN_SILHOUETTE); if (b[0].found()) hetO++; if (b[1].found()) hetR++;
            GoshCluster[] t = robustPair(sd, 15, 0, 2, 2.0, GoshCluster.HET_MIN_SILHOUETTE); if (t[0].found()) { out2O++; sepN++; if (separatesAny(t[0], 0, 1)) sepOk++; } if (t[1].found()) { out2R++; sepN++; if (separatesAny(t[1], 0, 1)) sepOk++; }
            GoshCluster[] o = robustPair(sd, 15, 0, 1, 2.5, GoshCluster.HET_MIN_SILHOUETTE); if (o[0].found()) out1O++; if (o[1].found()) out1R++;
        }
        check(fpO <= 1 && fpR <= 1, "heterogeneity without outliers (tau = 0.3, k = 15): at most 1 of " + n + " data sets gives clusters (ordinary " + fpO + ", meta-regression " + fpR + ")");
        check(hetO <= 1 && hetR <= 1, "heterogeneity without outliers (tau = 0.5, k = 25): at most 1 of " + n + " data sets gives clusters (ordinary " + hetO + ", meta-regression " + hetR + ")");
        check(fpLoose >= fpO + fpR, "the stricter heterogeneity route never gives more clusters than the lenient one on data without outliers (" + (fpO + fpR) + " against " + fpLoose + ")");
        check(out2O >= 4 && out2R >= 4, "two outlier studies (+2, standard errors 0.08-0.33): found in at least 4 of " + n + " data sets (ordinary " + out2O + ", meta-regression " + out2R + ")");
        check(sepN > 0 && sepOk == sepN, "when clusters are found for the two outlier studies, one of the outliers is among the separating studies (" + sepOk + " of " + sepN + ")");
        check(out1O >= 5 && out1R >= 5, "one clear outlier study (+2.5): found in at least 5 of " + n + " data sets (ordinary " + out1O + ", meta-regression " + out1R + ")");
        // the note and the report give the real silhouette widths and the adjusted score, with labels
        { GoshCluster f = null; for (long sd = 1; sd <= n && f == null; sd++) { GoshCluster[] q = robustPair(sd, 15, 0, 2, 2.0, GoshCluster.HET_MIN_SILHOUETTE); if (q[1].found()) f = q[1]; else if (q[0].found()) f = q[0]; }
          check(f != null && f.silEffCluster != null && f.silEffCluster.length == f.G && f.silHetCluster.length == f.G && f.silCluster.length == f.G, "silhouette: real values in both spaces are kept per cluster next to the adjusted score");
          if (f != null) { double delta = f.hetMinSil - f.minSil; boolean consistent = true, viaHet = false, lower = true; for (int g = 0; g < f.G; g++) { double e = f.silEffCluster[g], h = f.silHetCluster[g], adj = Math.max(Double.isNaN(e) ? Double.NEGATIVE_INFINITY : e, Double.isNaN(h) ? Double.NEGATIVE_INFINITY : h - delta);
                if (Math.abs(adj - f.silCluster[g]) > 1e-9) consistent = false; if (!(e >= f.minSil) && h >= f.hetMinSil) { viaHet = true; if (!(f.silCluster[g] < h)) lower = false; } }
            check(consistent, "silhouette: the adjusted score of every cluster is the better of the effects value and the heterogeneity value minus " + Fmt.num(delta, 2));
            check(!viaHet || lower, "silhouette: for a cluster accepted through the heterogeneity the real value is higher than the adjusted score by " + Fmt.num(delta, 2));
            String[] lb = new String[15]; for (int i = 0; i < 15; i++) lb[i] = "S" + (i + 1); String ct = RegReport.clusterText(f, lb, null, f.varNames);
            check(f.note.contains("real values: effects") && f.note.contains("adjusted score used for the threshold") && f.note.contains("minus " + Fmt.num(delta, 2)), "silhouette: the note prints the real averages and the adjusted score, labelled");
            check(ct.contains("Effects (real)") && ct.contains("Heterogeneity (real)") && ct.contains("Adjusted score") && ct.contains("Accepted through") && ct.contains(Fmt.num(f.silEffCluster[0], 2)), "silhouette: the report lists the real and the adjusted values per cluster and the route"); } }
        GoshCluster[] c = robustPair(1, 15, 0, 1, 2.5, GoshCluster.HET_MIN_SILHOUETTE); check(c[0].hetMinSil == GoshCluster.HET_MIN_SILHOUETTE && GoshCluster.HET_MIN_SILHOUETTE == 0.70 && c[0].minShare == GoshCluster.MIN_SHARE, "the thresholds of the two routes are recorded in the result");
    }

    /** The example data sets of the "Example data" button: each is bundled, readable, its columns are assigned automatically and the analysis runs. */
    static void exampleDataTests() {
        check(org.metajava.gui.Examples.ALL.size() == 5, "example data: five example data sets are offered");
        for (org.metajava.gui.Examples.Item it : org.metajava.gui.Examples.ALL) {
            try (java.io.InputStream in = SelfTest.class.getResourceAsStream("/examples/" + it.file)) {
                check(in != null, "example data: " + it.file + " is bundled"); if (in == null) continue;
                java.io.File tmp = java.io.File.createTempFile("mjex", ".xlsx"); tmp.deleteOnExit(); java.nio.file.Files.copy(in, tmp.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                DataTable tb = org.metajava.io.TableReader.read(tmp, 0); check(tb.nrow >= 10 && tb.names.size() >= 4, "example data: " + it.file + " is read (" + tb.nrow + " rows, " + tb.names.size() + " columns)");
                MetaSpec sp = new MetaSpec(); sp.type = it.type; sp.sm = it.sm; sp.prediction = false; MetaData md = new MetaData(); boolean all = true; StringBuilder miss = new StringBuilder();
                for (String key : org.metajava.gui.Examples.parameterKeys(it.type)) { String col = org.metajava.gui.Examples.guessColumn(key, tb.names); if (col.equals("(none)")) { all = false; miss.append(key).append(' '); } else md.put(key, tb.num(col)); }
                check(all, "example data: " + it.file + " - every column of the " + it.type + " analysis is assigned automatically" + (all ? "" : " (missing: " + miss + ")")); if (!all) continue;
                String[] lab = tb.strings("Study"); md.n = tb.nrow; md.studlab = lab; MetaResult r = Meta.run(sp, md);
                check(r.overall != null && !Double.isNaN(r.overall.teRandom) && !Double.isNaN(r.overall.teCommon) && r.kAll >= 10, "example data: " + it.file + " - the analysis runs (k = " + r.kAll + ", random-effects estimate " + Fmt.num(r.overall.teRandom, 4) + ")");
            } catch (Exception e) { e.printStackTrace(); check(false, "example data: " + it.file + " threw " + e); }
        }
    }

    /** The likelihood of the ML / REML estimators can have two maxima with few, very unequal studies: the estimate is unchanged (as in metafor), a higher mode is reported. */
    static void remlModeTests() {
        double[] y = {0.20657085589375465, -5.116347049481632, 0.16546564893809362, 0.37492810016703193, -1.9057887912530813, 4.199789824364626, -3.7110214579973917};
        double[] v = {0.002620136899687197, 2.0794018448444427, 0.0034911660476908283, 0.007768072574424503, 4.017428242692546, 16.03248909438053, 6.287382328017086};
        Rma f = Rma.fit(y, v, null, "REML"); double alt = f.higherMode();
        near(f.tau2, 3.28426506, 1e-6, "REML: the estimate is the one of the usual root search (unchanged), tau^2 of a two-mode likelihood");
        near(alt, 0.0037990, 2e-5, "REML: the higher mode of the likelihood is found by the diagnostic (tau^2 = 0.0038)");
        check(Rma.logLik(f, alt, true) > Rma.logLik(f, f.tau2, true) + 0.1, "REML: the likelihood is clearly higher at the second mode");
        check(Double.isNaN(Rma.fit(y, v, null, "DL").higherMode()) && Double.isNaN(Rma.fit(y, v, null, "PM").higherMode()), "the diagnostic is not run for estimators that are not likelihood based");
        MetaSpec sp = genSpec("MD"); sp.methodTau = "REML"; sp.prediction = false; MetaData md = new MetaData(); md.put("TE", y); md.put("seTE", java.util.Arrays.stream(v).map(Math::sqrt).toArray()); md.n = y.length; md.studlab = new String[]{"A", "B", "C", "D", "E", "F", "G"};
        String txt = Report.summary(Meta.run(sp, md)); check(txt.contains("restricted log-likelihood has a second, higher maximum at tau^2 = 0.0038"), "the summary warns that the restricted likelihood has a second, higher maximum");
        check(txt.contains("use the selected mode, tau^2 = 3.2843") && txt.contains("the higher mode is not used in any calculation") && txt.contains("as in metafor"), "the warning states that the results use the selected mode (as in metafor) and that the higher mode is not used");
        near(f.higherModeGain, Rma.logLik(f, alt, true) - Rma.logLik(f, f.tau2, true), 1e-12, "the reported gain is the difference of the restricted log-likelihoods at the two modes"); check(f.higherModeGain > 0.3 && f.higherModeGain > Rma.MODE_MIN_GAIN, "the gain is far above the reporting threshold (" + f.higherModeGain + ")");
        { double h = 1e-6; double dEst = (Rma.logLik(f, f.tau2 + h, true) - Rma.logLik(f, f.tau2 - h, true)) / (2 * h), dAlt = (Rma.logLik(f, alt + 1e-8, true) - Rma.logLik(f, alt - 1e-8, true)) / 2e-8;
          check(Math.abs(dEst) < 1e-6 && Math.abs(dAlt) < 1e-4 && Rma.logLik(f, alt + 0.01, true) < Rma.logLik(f, alt, true) && Rma.logLik(f, alt * 0.5, true) < Rma.logLik(f, alt, true), "both modes are stationary points (the same restricted likelihood that the score equation differentiates), and the higher one is a maximum"); }
        { Rma ml = Rma.fit(y, v, null, "ML"); double am = ml.higherMode(); check(Double.isNaN(am) || Rma.logLik(ml, am, false) > Rma.logLik(ml, ml.tau2, false) + Rma.MODE_MIN_GAIN, "ML: a reported higher mode always has a clearly higher (unrestricted) log-likelihood"); }
        { double[] yh = {0.20, 0.21, 0.19, 0.2, 0.22, 0.18}, vh = {0.05, 0.06, 0.04, 0.05, 0.07, 0.05}; Rma hh = Rma.fit(yh, vh, null, "REML"); check(hh.tau2 == 0 && Double.isNaN(hh.higherMode()) && Double.isNaN(hh.higherModeGain), "boundary estimate tau^2 = 0 with a single mode: nothing is reported"); }
        double[] y2 = {0.1, 0.35, 0.22, 0.4, 0.05, 0.3, 0.18, 0.27}, v2 = {0.02, 0.03, 0.025, 0.04, 0.02, 0.05, 0.03, 0.02};
        check(Double.isNaN(Rma.fit(y2, v2, null, "REML").higherMode()) && Double.isNaN(Rma.fit(y2, v2, null, "ML").higherMode()), "an ordinary data set has a single mode: nothing is reported");
        check(!Report.summary(Meta.run(sp, gen(LNHR, SEHR, true))).contains("second, higher maximum"), "no warning in the reference data sets");
    }

    /** Funnel plot themes (Default, MetaWin style, STATA style) for the main and the trim-and-fill funnel plot. */
    static void funnelThemes() {
        try {
            double[] te = {0.07, -0.02, -0.06, 0.03, 0.12, 0.30, -0.18, -0.14, -0.07, 0.27, -0.32, 0.18, 0.80, -0.02, 0.23, 0.54, 0.26, 1.18, -0.06, 0.06}, se = {0.094, 0.103, 0.102, 0.125, 0.147, 0.140, 0.160, 0.167, 0.175, 0.165, 0.222, 0.224, 0.252, 0.290, 0.291, 0.302, 0.370, 0.374, 0.168, 0.095};
            MetaSpec fs = new MetaSpec(); fs.type = "gen"; fs.sm = "SMD"; fs.methodTau = "REML"; MetaData fd = new MetaData(); fd.put("TE", te); fd.put("seTE", se); fd.studlab = new String[te.length]; for (int i = 0; i < te.length; i++) fd.studlab[i] = "S" + (i + 1);
            MetaResult fr = Meta.run(fs, fd); TrimFill tf = TrimFill.run(fr, null, true, "L0", true, true);
            for (String th : org.metajava.plot.FunnelThemes.NAMES) for (int v = 0; v < 4; v++) {
                Plots.Opts q = new Plots.Opts(); q.funnelTheme = th; q.scale = 1; q.contour = (v & 1) == 1; q.studyLabels = false; BufferedImage im = Plots.funnel(fr, q, (v & 2) != 0 ? tf : null);
                check(im.getWidth() > 300 && im.getHeight() > 300, "funnel theme '" + th + "' (contour " + q.contour + ", trim-and-fill " + ((v & 2) != 0) + ") is drawn");
            }
            Plots.Opts q = new Plots.Opts(); q.scale = 1; q.funnelTheme = "MetaWin style"; BufferedImage mw = Plots.funnel(fr, q, null), mwTf = Plots.funnel(fr, q, tf);
            check(near(mw, 0x006837) > 20 && near(mw, 0xA50026) > 20, "MetaWin funnel has the power colour bar (dark green to dark red)");
            check(near(mwTf, 0xFF7F0E) > 20 && near(mw, 0xFF7F0E) == 0, "MetaWin trim-and-fill funnel draws the imputed studies in orange, the plain funnel has none");
            q = new Plots.Opts(); q.scale = 1; q.funnelTheme = "STATA style"; BufferedImage st = Plots.funnel(fr, q, null), stTf = Plots.funnel(fr, q, tf);
            check(near(st, 0xEAF2F3) > 1000 && near(st, 0x1A476F) > 50, "STATA funnel has the pale-blue frame and the navy points / title");
            check(near(stTf, 0xDE7D00) > 20 && near(st, 0xDE7D00) == 0, "STATA trim-and-fill funnel draws the imputed studies in orange");
            q.contour = true; BufferedImage stc = Plots.funnel(fr, q, null); check(near(stc, 0x696969) > 5000 && near(stc, 0x696969) > 5 * near(st, 0x696969), "STATA contour-enhanced funnel draws the grey bands");
            for (String th : new String[]{"Default", "MetaWin style", "STATA style"}) { Plots.Opts a = new Plots.Opts(), b = new Plots.Opts(); a.funnelTheme = th; b.funnelTheme = th; a.scale = 1; b.scale = 1; a.studyLabels = false; b.studyLabels = true;
                check(!sameImage(Plots.funnel(fr, a, null), Plots.funnel(fr, b, null)) && !sameImage(Plots.funnel(fr, a, tf), Plots.funnel(fr, b, tf)), "study labels work in the '" + th + "' funnel plot and trim-and-fill funnel plot"); }
            org.metajava.plot.VectorGraphics vg = org.metajava.plot.Canvas.record(sc -> { Plots.Opts z = new Plots.Opts(); z.funnelTheme = "MetaWin style"; z.scale = sc; return Plots.funnel(fr, z, tf); }, 1.0); check(vg != null, "themed funnel plot can be recorded as vector graphics (SVG / PDF)");
        } catch (Exception e) { e.printStackTrace(); check(false, "funnel theme tests threw " + e); }
    }

    /** Forest-plot themes (Default, RevMan 5, BMJ, STATA) and the overrides of colour and line style, for the main, subgroup, leave-one-out and cumulative plots. */
    static void forestThemes() {
        try {
            double[] e1 = {220, 180, 200, 230, 200, 240}, n1 = {1000, 800, 780, 750, 900, 850}, e2 = {440, 348, 420, 380, 460, 370}, n2 = n1;
            MetaSpec bs = new MetaSpec(); bs.type = "bin"; bs.sm = "OR"; bs.methodTau = "DL"; bs.prediction = true; bs.subgroupName = "Group";
            MetaData bd = new MetaData(); bd.put("event.e", e1); bd.put("n.e", n1); bd.put("event.c", e2); bd.put("n.c", n2); bd.subgroup = new String[]{"A", "B", "A", "B", "A", "B"};
            MetaResult br = Meta.run(bs, bd); Meta.leaveOneOut(br); Meta.cumulative(br, null);
            java.util.function.Function<String, Plots.Opts> opt = th -> { Plots.Opts q = new Plots.Opts(); q.theme = th; q.scale = 1; return q; };
            java.util.Map<String, int[]> size = new java.util.HashMap<>();
            for (String th : org.metajava.plot.ForestTheme.THEMES) {
                BufferedImage main = Plots.forest(br, opt.apply(th)), loo = Plots.forestSeries(br, br.loo, br.looLabels, opt.apply(th), "loo"), cum = Plots.forestSeries(br, br.cum, br.cumLabels, opt.apply(th), "cum");
                check(main.getWidth() > 300 && loo.getWidth() > 300 && cum.getWidth() > 300, "forest theme '" + th + "': main, leave-one-out and cumulative forest plots are drawn");
                size.put(th, new int[]{main.getWidth(), main.getHeight()});
            }
            { BufferedImage a1 = Plots.forest(br, opt.apply("Default")), a2 = Plots.forest(br, opt.apply("RevMan 5 style")), a3 = Plots.forest(br, opt.apply("BMJ style")), a4 = Plots.forest(br, opt.apply("STATA style"));
              check(!sameImage(a1, a2) && !sameImage(a1, a3) && !sameImage(a1, a4) && !sameImage(a2, a3) && !sameImage(a3, a4), "the four forest themes give four different plots"); }
            // theme colours: RevMan blue squares, BMJ purple, STATA pale-blue frame
            int blue = countColour(Plots.forest(br, opt.apply("RevMan 5 style")), 0x0000FF), purple = countColour(Plots.forest(br, opt.apply("BMJ style")), 0x7060A8), frame = countColour(Plots.forest(br, opt.apply("STATA style")), 0xEAF2F3);
            check(blue > 100, "RevMan 5 theme draws blue study squares"); check(purple > 50, "BMJ theme draws purple study diamonds"); check(frame > 500, "STATA theme draws the pale-blue frame");
            check(countColour(Plots.forest(br, opt.apply("Default")), 0x0000FF) == 0, "Default theme has no blue square");
            // overrides (drawn at scale 3 and counted with a tolerance: thin lines are anti-aliased)
            java.util.function.BiFunction<Plots.Opts, Integer, Integer> cnt = (z, rgb) -> { z.scale = 3; return near(Plots.forest(br, z), rgb); };
            Plots.Opts q = opt.apply("Default"); q.squareColour = "Red"; check(cnt.apply(q, 0xCD0000) > 500, "square fill colour override (red) applies to the default theme");
            q = opt.apply("RevMan 5 style"); q.squareColour = "Green"; check(cnt.apply(q, 0x009600) > 500 && cnt.apply(q, 0x0000FF) == 0, "square fill colour override replaces the RevMan 5 blue");
            q = opt.apply("Default"); q.diamondFill = "Orange"; q.diamondBorder = "Dark green"; check(cnt.apply(q, 0xF08200) > 100 && cnt.apply(q, 0x006400) > 40, "diamond fill and border overrides apply");
            Plots.Opts d0 = opt.apply("Default"); q = opt.apply("Default"); q.diamondFill = "None"; q.diamondBorder = "None"; check(cnt.apply(q, 0xC0C0C0) < cnt.apply(d0, 0xC0C0C0), "diamond fill 'None' leaves the diamond hollow");
            q = opt.apply("Default"); q.nullColour = "Blue"; q.pooledColour = "Green"; check(cnt.apply(q, 0x0000FF) > 200 && cnt.apply(q, 0x009600) > 100, "null-effect and pooled-effect line colour overrides apply");
            Plots.Opts qs = opt.apply("Default"), qd = opt.apply("Default"); qs.nullColour = "Blue"; qs.nullStyle = "Solid"; qd.nullColour = "Blue"; qd.nullStyle = "Dashed";
            check(cnt.apply(qs, 0x0000FF) > cnt.apply(qd, 0x0000FF) + 100, "null-effect line style: dashed has fewer pixels than solid");
            qs = opt.apply("Default"); qd = opt.apply("Default"); qs.pooledColour = "Red"; qs.pooledStyle = "Solid"; qd.pooledColour = "Red"; qd.pooledStyle = "Dotted";
            check(cnt.apply(qs, 0xCD0000) > cnt.apply(qd, 0xCD0000) + 50, "pooled-effect line style: dotted has fewer pixels than solid");
            q = opt.apply("STATA style"); q.nullStyle = "Dotted"; q.pooledStyle = "Solid"; check(Plots.forest(br, q).getWidth() > 300, "overrides also apply on top of the STATA theme");
            // vector export of a themed plot
            q = opt.apply("BMJ style"); org.metajava.plot.VectorGraphics vg = org.metajava.plot.Canvas.record(sc -> { Plots.Opts z = opt.apply("BMJ style"); z.scale = sc; return Plots.forest(br, z); }, 1.0); check(vg != null, "themed forest plot can be recorded as vector graphics (SVG / PDF)");
        } catch (Exception e) { e.printStackTrace(); check(false, "forest theme tests threw " + e); }
    }

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
        { String infTxt = Report.influenceText(in);
          check(infTxt.contains("Note on rstudent (not used for flagging)") && infTxt.contains("unadjusted per-study limits") && infTxt.contains("+/-1.96") && infTxt.contains("family-wise criterion") && infTxt.contains("Bonferroni limit qnorm(1 - 0.025/k)") && infTxt.contains("not drawn in the plot and is not applied") && infTxt.contains("screening aid") && infTxt.contains("empirical"),
              "the influence report states that rstudent is not a flagging rule, that the +/-1.96 plot limits are unadjusted per-study limits, that the Bonferroni family-wise criterion is a simulation criterion that is not drawn or applied, and that the calibration is empirical"); }

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
            // I^2 / tau^2 confidence intervals appear on every forest-plot theme (RevMan 5, BMJ, STATA as well as Default) and never shrink the figure
            for (String th : org.metajava.plot.ForestTheme.THEMES) { Plots.Opts f0 = new Plots.Opts(), f1 = new Plots.Opts(), f2 = new Plots.Opts(); f0.theme = f1.theme = f2.theme = th; f1.ciI2 = true; f2.ciTau = true;
              BufferedImage a0 = Plots.forest(br, f0), a1 = Plots.forest(br, f1), a2 = Plots.forest(br, f2);
              check(!same(a0, a1) && a1.getWidth() >= a0.getWidth(), "Forest plot (" + th + "): the I\u00B2 confidence interval is shown and the figure does not shrink");
              check(!same(a0, a2) && a2.getWidth() >= a0.getWidth(), "Forest plot (" + th + "): the tau\u00B2 confidence interval is shown and the figure does not shrink"); }
            // L'Abbe themes and colours: Default is unchanged by the new options, ggplot2 style draws, colours reach the pixels, legend moves to the lower right when the effect line is above the no-effect line
            { Plots.Opts d0 = new Plots.Opts(); d0.studyLabels = false; BufferedImage dflt = Plots.labbe(br, d0); Plots.Opts d1 = new Plots.Opts(); d1.studyLabels = false; d1.labbeTheme = org.metajava.plot.LabbePlot.DEFAULT;
              check(same(dflt, Plots.labbe(br, d1)), "L'Abbe Default theme with every colour on 'Theme default' draws the same plot");
              { for (String th : org.metajava.plot.FunnelThemes.NAMES) { Plots.Opts q = new Plots.Opts(); q.funnelTheme = th; BufferedImage f1 = Plots.funnel(br, q, null); q.precision = true; BufferedImage f2 = Plots.funnel(br, q, null); BufferedImage f3 = Plots.funnel(br, q, null); q.contour = true; BufferedImage f4 = Plots.funnel(br, q, null);
                  check(!same(f1, f2) && same(f2, f3), "Funnel plot of precision (" + th + ") differs from the standard funnel and is reproducible"); check(!same(f2, f4) || th.startsWith("MetaWin"), "Funnel plot of precision (" + th + ") honours Contour-enhanced");
                  q.studyLabels = false; check(!same(f4, Plots.funnel(br, q, null)), "Funnel plot of precision (" + th + ") honours Study labels"); } }
              { for (String th : org.metajava.plot.RadialPlot.THEMES) { Plots.Opts q = new Plots.Opts(); q.radialTheme = th; BufferedImage a1 = Plots.radial(br, q); q.radialLabels = false; BufferedImage a2 = Plots.radial(br, q);
                  check(a1.getWidth() > 100 && !same(a1, a2), "Radial plot (" + th + ") draws and the Study labels option changes it"); }
                Plots.Opts q0 = new Plots.Opts(); BufferedImage r0 = Plots.radial(br, q0); q0.radialCI = "Red"; check(!same(r0, Plots.radial(br, q0)), "Radial plot confidence area colour reaches the plot");
                Plots.Opts l0 = new Plots.Opts(); l0.studyLabels = true; BufferedImage lo = Plots.labbe(br, l0); l0.studyLabels = false; check(!same(lo, Plots.labbe(br, l0)), "L'Abbe study labels can be switched off"); }
              Plots.Opts g1 = new Plots.Opts(); g1.studyLabels = false; g1.labbeTheme = org.metajava.plot.LabbePlot.GG; BufferedImage gi = Plots.labbe(br, g1);
              check(gi.getWidth() > 0 && !same(gi, dflt), "L'Abbe ggplot2 style renders and differs from the Default theme");
              Plots.Opts c1 = new Plots.Opts(); c1.studyLabels = false; c1.labbeCI = "Red"; BufferedImage ci1 = Plots.labbe(br, c1); int reds = 0, greens = 0;
              Plots.Opts c2 = new Plots.Opts(); c2.studyLabels = false; c2.labbePI = "Green"; BufferedImage pi1 = Plots.labbe(br, c2);   // alone, so that the (narrow) confidence area does not hide it
              for (int yy = 0; yy < ci1.getHeight(); yy++) for (int xx = 0; xx < ci1.getWidth(); xx++) { int rgb = ci1.getRGB(xx, yy), rr = rgb >> 16 & 255, gg2 = rgb >> 8 & 255, bb = rgb & 255; if (rr > 200 && gg2 < 60 && bb < 60) reds++;
                  rgb = pi1.getRGB(xx, yy); rr = rgb >> 16 & 255; gg2 = rgb >> 8 & 255; bb = rgb & 255; if (gg2 > 100 && rr < 60 && bb < 60) greens++; }
              check(reds > 200 && greens > 50, "L'Abbe confidence / prediction area colours are drawn in the Default theme (" + reds + " / " + greens + " pixels)");
              Plots.Opts b1 = new Plots.Opts(); b1.studyLabels = false; b1.labbeBack = "White"; check(same(Plots.labbe(br, b1), dflt), "L'Abbe background colour White equals the Default background");
              Plots.Opts b2 = new Plots.Opts(); b2.studyLabels = false; b2.labbeBack = "Light grey"; check(!same(Plots.labbe(br, b2), dflt), "L'Abbe background colour changes the plot");
              // legend: effect line below the no-effect line (RR < 1) -> top left, above (arms swapped) -> lower right; legend box is the only black frame region in the corner
              MetaData sw = new MetaData(); sw.put("event.e", bd.get("event.c")); sw.put("n.e", bd.get("n.c")); sw.put("event.c", bd.get("event.e")); sw.put("n.c", bd.get("n.e")); sw.n = 6; MetaResult brHigh = Meta.run(bs, sw);
              BufferedImage lo = Plots.labbe(br, g1), up = Plots.labbe(brHigh, g1); int W = lo.getWidth(), H = lo.getHeight();
              check(darkCount(lo, 0, 0, W / 2, H / 3) > darkCount(lo, W / 2, 2 * H / 3, W, H) && darkCount(up, W / 2, 2 * H / 3, W, H) > darkCount(up, 0, 0, W / 2, H / 3), "L'Abbe ggplot2 legend: top left when the estimated line is below the diagonal, lower right when above");
              Plots.Opts g2 = new Plots.Opts(); g2.labbeTheme = org.metajava.plot.LabbePlot.GG; g2.studyLabels = false; MetaSpec bc = bs.clone(); bc.random = false; bc.common = true; check(Plots.labbe(Meta.run(bc, bd), g2).getWidth() > 0, "L'Abbe ggplot2 style with a common-effect model only (no prediction interval) renders");
            }
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

        // ---- publication-bias extras: reference values from an independent implementation (numpy/scipy) on a simulated, selectively published data set (k = 40)
        try {
            final double[] BT = {0.15619, 0.29910, 0.01761, -0.20216, 0.05837, 0.35535, 0.67845, 0.26147, -0.12771, 0.50847, 0.07148, 0.30391, 0.34216, 0.20934, 0.17643, -0.25949, -0.10307, 0.37826, 0.04549, 0.31993, 0.71792, 0.21288, -0.02555, 0.18620, 0.32047, 0.29902, 0.34393, 0.87863, 0.08648, 0.24543, -0.05728, 0.19867, 0.11262, 0.21947, 0.28166, 0.37987, 0.75569, 0.39152, 0.08332, 0.20562};
            final double[] BS = {0.26878, 0.32897, 0.12536, 0.27023, 0.05413, 0.17934, 0.34650, 0.10257, 0.28676, 0.19087, 0.36637, 0.24167, 0.23168, 0.09787, 0.05499, 0.22955, 0.35737, 0.20050, 0.39005, 0.06775, 0.34406, 0.21304, 0.35719, 0.06060, 0.13700, 0.05738, 0.11694, 0.33058, 0.06775, 0.08369, 0.35355, 0.08538, 0.07854, 0.07714, 0.09662, 0.18138, 0.36381, 0.06167, 0.34072, 0.09086};
            MetaSpec sb = new MetaSpec(); sb.type = "gen"; sb.sm = "MD"; MetaResult rb = Meta.run(sb, gen(BT, BS, false));
            PubBias.PetPeese pp = PubBias.petPeese(rb);
            near(pp.pet.b0, 0.198451, 2e-6, "PET intercept"); near(pp.pet.se0, 0.037680, 2e-6, "PET intercept SE"); near(pp.peese.b0, 0.212147, 2e-6, "PEESE intercept"); near(pp.peese.se0, 0.023573, 2e-6, "PEESE intercept SE");
            check(pp.usePeese && Math.abs(pp.estimate - pp.peese.b0) < 1e-12, "PET-PEESE: significant PET intercept selects PEESE");
            PubBias.Selection sl = PubBias.selection(rb, "Auto");
            near(sl.mu, 0.175014, 5e-5, "selection model: adjusted effect"); near(sl.delta, 0.316320, 5e-4, "selection model: delta"); near(sl.tau2, 0.006014, 5e-5, "selection model: tau^2");
            near(sl.ll, 13.127976, 1e-4, "selection model: log-likelihood"); near(sl.lrt, 4.96899, 2e-3, "selection model: likelihood ratio test"); near(sl.muLo, 0.088362, 1e-3, "selection model: profile CI lower"); near(sl.muUp, 0.239641, 1e-3, "selection model: profile CI upper");
            PubBias.FailSafe fs = PubBias.failSafe(rb);
            near(fs.nRosenthal, 1776, 0.5, "fail-safe N (Rosenthal)"); near(fs.nRosenberg, 2387, 0.5, "fail-safe N (Rosenberg)");
            // exact linear relations are recovered
            double[] sx = {0.05, 0.1, 0.15, 0.2, 0.25, 0.3}, ya = new double[6], yb = new double[6]; for (int i = 0; i < 6; i++) { ya[i] = 0.2 + 1.5 * sx[i] + (i % 2 == 0 ? 1e-4 : -1e-4); yb[i] = 0.3 + 2 * sx[i] * sx[i] + (i % 2 == 0 ? 1e-4 : -1e-4); }
            near(PubBias.petPeese(Meta.run(sb, gen(ya, sx, false))).pet.b0, 0.2, 1e-3, "PET recovers the intercept of an exact linear relation"); near(PubBias.petPeese(Meta.run(sb, gen(yb, sx, false))).peese.b0, 0.3, 1e-3, "PEESE recovers the intercept of an exact quadratic relation");
            // a negative favoured direction is the mirror image of the positive one
            double[] neg = new double[BT.length]; for (int i = 0; i < neg.length; i++) neg[i] = -BT[i];
            PubBias.Selection sn = PubBias.selection(Meta.run(sb, gen(neg, BS, false)), "Auto");
            check(sn.flipped && Math.abs(sn.mu + sl.mu) < 1e-4 && Math.abs(sn.delta - sl.delta) < 1e-3 && Math.abs(sn.muLo + sl.muUp) < 1e-3, "selection model: mirrored data give the mirrored result (direction Auto)");
            check(PubBias.petPeese(Meta.run(sb, gen(new double[]{0.1, 0.2}, new double[]{0.1, 0.2}, false))).error != null && PubBias.selection(Meta.run(sb, gen(new double[]{0.1, 0.2, 0.3}, new double[]{0.1, 0.2, 0.3}, false)), "Auto").error != null, "too few studies give an explanatory message, not an exception");
            String btxt = BiasReport.petPeeseText(pp, rb) + BiasReport.selectionText(sl, rb) + BiasReport.failSafeText(fs, rb);
            check(btxt.contains("PEESE is used") && btxt.contains("Likelihood ratio test of selection") && btxt.contains("Rosenthal"), "publication-bias reports are produced");
            BufferedImage pim = Plots.petPeese(pp, rb, new Plots.Opts()); check(pim.getWidth() > 100 && countColour(pim, 0x1F4E8C) > 50 && countColour(pim, 0xD9822B) > 50, "PET-PEESE plot draws the PET line and the PEESE curve");
        } catch (Exception e) { e.printStackTrace(); check(false, "publication-bias tests threw " + e); }

        // ---- data validation
        try {
            DataTable tb = new DataTable(); tb.nrow = 6;
            tb.add("Study", new String[]{"Smith 2010", "Lee 2011", "Smith 2010", "Kim 2012", "Wu 2013", "Ng 2014"});
            tb.add("Ev1", new String[]{"5", "30", "abc", "4", "6", "7"}); tb.add("N1", new String[]{"20", "25", "40", "50", "60", "70"});
            tb.add("Ev2", new String[]{"8", "9", "10", "", "12", "-1"}); tb.add("N2", new String[]{"20", "25", "40", "50", "60", "70"});
            java.util.Map<String, String> cols = new java.util.LinkedHashMap<>(), nm = new java.util.HashMap<>();
            cols.put("event.e", "Ev1"); cols.put("n.e", "N1"); cols.put("event.c", "Ev2"); cols.put("n.c", "N2"); nm.put("event.e", "Events (experimental)"); nm.put("n.e", "Total (experimental)"); nm.put("event.c", "Events (control)"); nm.put("n.c", "Total (control)");
            try { Validate.check(tb, "bin", cols, nm, "Study", null, false, "RR"); check(false, "validation: bad data are rejected"); }
            catch (Validate.Failed f) { String all = f.getMessage();
                check(all.contains("row 3 (Smith 2010) has \"abc\"") && all.contains("Column 'Ev1' (Events (experimental)) contains text where a number is expected"), "validation: non-numeric cell named with row, study and column");
                check(all.contains("Events (experimental) is greater than Total (experimental): row 2 (Lee 2011): 30 > 25"), "validation: events greater than the total");
                check(all.contains("Events (control) cannot be negative: row 6 (Ng 2014): -1"), "validation: negative events");
                check(all.contains("Duplicate study labels: \"Smith 2010\" in rows 1 and 3"), "validation: duplicate study labels");
                check(all.contains("Column 'Ev2' (Events (control)) has no value: row 4 (Kim 2012)"), "validation: missing value reported"); }
            tb.cols.set(1, new String[]{"5", "7", "8", "4", "6", "7"}); tb.cols.set(3, new String[]{"8", "9", "10", "11", "12", "13"}); tb.cols.set(0, new String[]{"A", "B", "C", "D", "E", "E"});
            java.util.List<String> w = Validate.check(tb, "bin", cols, nm, "Study", null, false, "RR");
            check(w.size() == 1 && w.get(0).contains("Duplicate study labels"), "validation: valid numbers pass and only the duplicate label is a warning (" + w.size() + ")");
            DataTable tg = new DataTable(); tg.nrow = 3; tg.add("te", new String[]{"0.1", "0.2", "0.3"}); tg.add("se", new String[]{"0.1", "0", "-0.2"});
            java.util.Map<String, String> gc = new java.util.LinkedHashMap<>(); gc.put("TE", "te"); gc.put("seTE", "se");
            try { Validate.check(tg, "gen", gc, new java.util.HashMap<>(), null, null, false, "MD"); check(false, "validation: non-positive SE rejected"); } catch (Validate.Failed f) { check(f.getMessage().contains("Standard errors must be positive"), "validation: non-positive standard errors"); }
        } catch (Exception e) { e.printStackTrace(); check(false, "validation tests threw " + e); }


        // ---- permutest, model comparison and GOSH (BCG vaccine trials, log risk ratios; reference values from an independent numpy / scipy implementation, ablat = absolute latitude)
        try {
            double[] ta = {4, 6, 3, 62, 33, 180, 8, 505, 29, 17, 186, 5, 27}, n1 = {123, 306, 231, 13598, 5069, 1541, 2545, 88391, 7499, 1716, 50634, 2498, 16913}, tc = {11, 29, 11, 248, 47, 372, 10, 499, 45, 65, 141, 3, 29}, n2 = {139, 303, 220, 12867, 5808, 1451, 629, 88391, 7277, 1665, 27338, 2341, 17854};
            double[] ablat = {44, 55, 42, 52, 13, 44, 19, 13, 27, 42, 18, 33, 33}, year = {48, 49, 60, 77, 73, 53, 73, 80, 68, 61, 74, 69, 76}; String[] alloc = {"random", "random", "random", "random", "alt", "alt", "sys", "random", "random", "sys", "sys", "alt", "random"};
            int K = 13; double[] by = new double[K], bse = new double[K];
            for (int i = 0; i < K; i++) { by[i] = Math.log((ta[i] / n1[i]) / (tc[i] / n2[i])); bse[i] = Math.sqrt(1 / ta[i] - 1 / n1[i] + 1 / tc[i] - 1 / n2[i]); }
            java.util.Map<String, double[]> nd = new java.util.HashMap<>(); nd.put("ablat", ablat); nd.put("year", year); java.util.Map<String, String[]> fd = new java.util.HashMap<>(); fd.put("alloc", alloc);
            MetaSpec bs = new MetaSpec(); bs.type = "gen"; bs.sm = "RR"; bs.methodTau = "REML"; MetaData bd = new MetaData(); bd.put("TE", by); bd.put("seTE", bse); bd.n = K; MetaResult br = Meta.run(bs, bd);
            Reg g = Reg.run(br, java.util.List.of("ablat"), nd, java.util.List.of(), fd, true);
            { Reg gf = Reg.run(br, java.util.List.of("ablat"), nd, java.util.List.of("alloc"), fd, true);
              String t1 = Report.regText(gf, br, 1, ""); Perm pf = Perm.run(gf, 200, false, 1); String t2 = RegReport.permText(gf, pf, br);
              for (String t : new String[]{t1, t2}) { check(t.contains("alloc: alt (reference)") && t.contains("alloc: random") && t.contains("alloc: sys") && !t.contains("allocrandom"), "factor rows read \"Name: Level\" with a reference-category row"); check(t.indexOf("alloc: alt (reference)") < t.indexOf("alloc: random"), "reference row precedes the other levels of its factor"); }
              { java.util.Map<String, String> rf = new java.util.HashMap<>(); rf.put("alloc", "sys"); Reg gs = Reg.run(br, java.util.List.of("ablat"), nd, java.util.List.of("alloc"), fd, true, rf);
                String t3 = Report.regText(gs, br, 1, ""); check(t3.contains("alloc: sys (reference)") && t3.contains("alloc: alt") && t3.contains("alloc: random") && t3.indexOf("alloc: sys (reference)") < t3.indexOf("alloc: alt"), "chosen reference category is the reference row");
                near(gs.QM, gf.QM, 1e-8, "omnibus test and fit do not depend on the reference category"); near(gs.tau2, gf.tau2, 1e-8, "tau^2 does not depend on the reference category");
                int ia = java.util.Arrays.asList(gf.coefNames).indexOf("alloc: random"), is = java.util.Arrays.asList(gs.coefNames).indexOf("alloc: random");
                near(gs.est[is], gf.est[ia] - gf.est[java.util.Arrays.asList(gf.coefNames).indexOf("alloc: sys")], 1e-6, "random-vs-sys contrast equals the difference of the two coefficients against alt");
                rf.put("alloc", "nonexistent"); check(Reg.run(br, java.util.List.of("ablat"), nd, java.util.List.of("alloc"), fd, true, rf).refLabel[0].equals("alloc: alt"), "unknown reference falls back to the first level"); }
              Reg gn = Reg.run(br, java.util.List.of("ablat"), nd, java.util.List.of("alloc"), fd, false); check(!Report.regText(gn, br, 1, "").contains("(reference)"), "no reference row without intercept"); }
            near(g.tau2, 0.0763479640, 1e-8, "meta-regression (REML, ablat): tau^2"); near(g.QE, 30.73309001, 1e-6, "meta-regression: QE"); near(g.I2, 68.391225, 1e-4, "meta-regression: I^2"); near(g.R2, 75.626622, 1e-4, "meta-regression: R^2");
            near(g.logLik, -8.08732006, 1e-6, "REML fit statistics: restricted logLik (as metafor)"); near(g.aic, 22.17464012, 1e-6, "REML fit statistics: AIC"); near(g.bic, 23.36832593, 1e-6, "REML fit statistics: BIC (uses k - p)"); near(g.aicc, 25.60321155, 1e-6, "REML fit statistics: AICc");
            MetaSpec ms = bs.clone(); ms.methodTau = "ML"; Reg gm = Reg.run(Meta.run(ms, bd), java.util.List.of("ablat"), nd, java.util.List.of(), fd, true);
            near(gm.logLik, -7.68566553, 1e-6, "ML fit statistics: logLik"); near(gm.aic, 21.37133106, 1e-6, "ML fit statistics: AIC"); near(gm.bic, 23.06617913, 1e-6, "ML fit statistics: BIC"); near(gm.aicc, 24.03799772, 1e-6, "ML fit statistics: AICc");
            // model comparison: all subsets of ablat, year, alloc
            ModelCompare mc = ModelCompare.run(br, java.util.List.of("ablat", "year"), nd, java.util.List.of("alloc"), fd, true);
            check(mc.rows.size() == 8, "model comparison: 2^3 models"); ModelCompare.Row top = mc.rows.get(0), nullRow = null; for (ModelCompare.Row w : mc.rows) if (w.mask == 0) nullRow = w;
            check(top.label.equals("~ ablat") && top.best, "model comparison: best model by AICc is ~ ablat"); near(top.aicc, 24.03799772, 1e-6, "model comparison: AICc of ~ ablat"); near(top.weight, 0.67176891, 1e-6, "model comparison: Akaike weight of ~ ablat");
            near(top.R2, 75.626622, 1e-4, "model comparison: R^2 of ~ ablat (REML tau^2)"); near(top.lrt, 9.95882164, 1e-6, "model comparison: LRT of ~ ablat against the null model"); near(top.lrtP, 0.00160080, 1e-7, "model comparison: LRT p-value");
            near(nullRow.aic, 29.33015270, 1e-6, "model comparison: AIC of the intercept-only model"); double sw = 0; for (ModelCompare.Row w : mc.rows) sw += w.weight; near(sw, 1.0, 1e-12, "model comparison: Akaike weights sum to 1");
            ModelCompare.Row ab = null; for (ModelCompare.Row w : mc.rows) if (w.label.equals("~ ablat + alloc")) ab = w; near(ab.bic, 20.76888636, 1e-6, "model comparison: BIC of ~ ablat + alloc"); near(ab.R2, 72.803033, 1e-4, "model comparison: R^2 of ~ ablat + alloc");
            check(mc.drops.size() == 3 && mc.drops.get(2).df == 2, "model comparison: drop-one tests (df of the factor)"); near(mc.drops.get(0).lrt, 11.84776639, 1e-6, "model comparison: drop-one LRT for ablat"); near(mc.drops.get(2).p, 0.01935750, 1e-7, "model comparison: drop-one p-value for alloc");
            // exact permutation test (first 7 trials, ablat + year; all 5040 permutations)
            for (int hk = 0; hk < 2; hk++) {
                MetaSpec ps = bs.clone(); if (hk == 1) ps.methodRandomCi = "HK"; MetaData pd = new MetaData(); pd.put("TE", java.util.Arrays.copyOf(by, 7)); pd.put("seTE", java.util.Arrays.copyOf(bse, 7)); pd.n = 7;
                java.util.Map<String, double[]> pn = new java.util.HashMap<>(); pn.put("ablat", java.util.Arrays.copyOf(ablat, 7)); pn.put("year", java.util.Arrays.copyOf(year, 7));
                Reg pg = Reg.run(Meta.run(ps, pd), java.util.List.of("ablat", "year"), pn, java.util.List.of(), new java.util.HashMap<>(), true); Perm pt = Perm.run(pg, 1000, true, 1);
                double[] want = hk == 0 ? new double[]{0.5795634920634921, 0.1880952380952381, 0.48928571428571427} : new double[]{0.582936507936508, 0.20456349206349206, 0.49563492063492065}; double wq = hk == 0 ? 0.37043650793650795 : 0.37956349206349205;
                check(pt.exact && pt.nPerm == 5040, "permutest" + (hk == 1 ? " (Knapp-Hartung)" : "") + ": exact test enumerates 7! permutations");
                for (int a = 0; a < 3; a++) near(pt.pval[a], want[a], 1e-12, "permutest" + (hk == 1 ? " (KH)" : "") + ": exact p-value of coefficient " + a); near(pt.pQM, wq, 1e-12, "permutest" + (hk == 1 ? " (KH)" : "") + ": exact p-value of the omnibus test");
            }
            Perm r1 = Perm.run(g, 5000, false, 1), r2 = Perm.run(g, 5000, false, 1), r3 = Perm.run(g, 5000, false, 2);
            check(!r1.exact && r1.pval[1] == r2.pval[1] && r1.pQM == r2.pQM, "permutest: random test is reproducible for a given seed"); near(r1.pval[1], 0.00575, 0.0035, "permutest: random p-value (k = 13) close to the reference 0.0057 from 20000 permutations"); check(r3.pval[1] > 0 && r1.pval[1] >= 1.0 / 5001, "permutest: random p-value is never 0");
            check(Perm.unrank(4, 0)[0] == 0 && Perm.unrank(4, 23)[0] == 3 && Perm.unrank(4, 23)[3] == 0, "permutest: lexicographic unranking");
            // GOSH: all 247 subsets (>= 2 studies) of the first 8 trials
            MetaData gd = new MetaData(); gd.put("TE", java.util.Arrays.copyOf(by, 8)); gd.put("seTE", java.util.Arrays.copyOf(bse, 8)); gd.n = 8; Gosh gs = Gosh.run(Meta.run(bs, gd), 20000, 1);
            check(gs.exhaustive && gs.nSub == 247, "GOSH: 2^8 - 1 - 8 subsets"); double me = 0, mi = 0; for (int b = 0; b < gs.nSub; b++) { me += gs.est[b]; mi += gs.i2[b]; }
            near(me / gs.nSub, -0.9131121626, 1e-9, "GOSH: mean estimate over the subsets"); near(mi / gs.nSub, 74.64465273, 1e-6, "GOSH: mean I^2 over the subsets"); near(gs.fullEst, -0.8990140584, 1e-9, "GOSH: estimate with all studies"); near(gs.estIn[0], -0.9060221181, 1e-9, "GOSH: mean estimate of the subsets with study 1"); near(gs.i2Out[0], 78.53012553, 1e-6, "GOSH: mean I^2 of the subsets without study 1");
            MetaData gd2 = new MetaData(); gd2.put("TE", by); gd2.put("seTE", bse); gd2.n = K; Gosh a1 = Gosh.run(Meta.run(bs, gd2), 3000, 7), a2 = Gosh.run(Meta.run(bs, gd2), 3000, 7);
            check(a1.exhaustive == false && a1.nSub == 3000 && java.util.Arrays.equals(a1.est, a2.est), "GOSH: random subsets are reproducible for a given seed");
            // plots
            Plots.Opts po = new Plots.Opts(); po.scale = 1; MetaSpec ss = bs.clone(); ss.prediction = true; ss.predictionSubgroup = true; MetaData sd = new MetaData(); sd.put("TE", by); sd.put("seTE", bse); sd.n = K; sd.subgroup = alloc; MetaResult sr = Meta.run(ss, sd);
            java.awt.image.BufferedImage[] imgs = {org.metajava.plot.ExtraPlots.gosh(gs, br, po, false), org.metajava.plot.ExtraPlots.gosh(a1, br, po, true), org.metajava.plot.ExtraPlots.orchard(sr, po), org.metajava.plot.ExtraPlots.caterpillar(sr, po)}; String[] nm2 = {"GOSH plot", "GOSH plot (highlight)", "orchard plot", "caterpillar plot"};
            for (int i = 0; i < imgs.length; i++) { int dark = 0; for (int yy = 0; yy < imgs[i].getHeight(); yy += 2) for (int xx = 0; xx < imgs[i].getWidth(); xx += 2) if ((imgs[i].getRGB(xx, yy) & 0xFFFFFF) != 0xFFFFFF) dark++; check(dark > 400, nm2[i] + " is drawn (" + dark + " non-white samples)"); }
            { // marginal densities: highlight mode draws one colour-matched curve per group (includes = blue, excludes = orange) in BOTH marginal panels; the plain plot draws a single blue curve
                java.awt.image.BufferedImage hi = imgs[1], pl = imgs[0]; int[] blue = {0x1F, 0x4E, 0x8C}, orange = {0xD9, 0x82, 0x2B};
                int[] hb = {marg(hi, blue, true), marg(hi, orange, true), marg(hi, blue, false), marg(hi, orange, false)}, pb = {marg(pl, blue, true), marg(pl, orange, true), marg(pl, blue, false), marg(pl, orange, false)};
                check(hb[0] > 20 && hb[1] > 20, "GOSH highlight: the top marginal panel has a blue (includes) and an orange (excludes) density curve (" + hb[0] + " / " + hb[1] + " px)");
                check(hb[2] > 20 && hb[3] > 20, "GOSH highlight: the right marginal panel has a blue (includes) and an orange (excludes) density curve (" + hb[2] + " / " + hb[3] + " px)");
                check(pb[0] > 20 && pb[2] > 20 && pb[1] == 0 && pb[3] == 0, "GOSH without highlight: a single blue density curve in each marginal panel, no orange"); }
            org.metajava.plot.VectorGraphics vg = org.metajava.plot.Canvas.record(sc -> { Plots.Opts q = new Plots.Opts(); q.scale = sc; return org.metajava.plot.ExtraPlots.orchard(sr, q); }, 1.0); check(vg != null, "orchard plot can be recorded as vector graphics (SVG / PDF)");
            check(RegReport.goshText(gs, br).contains("247 subsets") && RegReport.compareText(mc, br).contains("* ~ ablat") && RegReport.permText(g, r1, br).contains("p-val*"), "report texts for GOSH, model comparison and permutest");
            // ---- GOSH for meta-regression (reference values from an independent numpy implementation: REML, rank check by SVD, itertools subsets)
            MetaSpec bsT = bs.clone(); bsT.methodI2 = "tau2"; MetaResult brT = Meta.run(bsT, bd);   // the reference I^2 is the tau^2-based one
            Reg.Design D1 = Reg.design(brT, java.util.List.of("ablat"), nd, java.util.List.of(), fd, true, null); GoshReg gr = GoshReg.run(brT, D1, true, 20000, 1);
            { Reg.Design Dq = Reg.design(br, java.util.List.of("ablat"), nd, java.util.List.of(), fd, true, null); GoshReg gq = GoshReg.run(br, Dq, true, 20000, 1); near(gq.fullI2, 100 * (30.73309001 - 11) / 30.73309001, 1e-6, "GOSH meta-regression: Q-based I^2 of the full model, (Q_E - (k - p)) / Q_E"); check(gq.nValid == gr.nValid, "GOSH meta-regression: the I^2 definition does not change which subsets are valid"); }
            check(gr.exhaustive && gr.nSub == 8178 && gr.nTotal == 8178, "GOSH meta-regression (~ ablat): all 2^13 - 1 - 13 subsets with at least p = 2 studies");
            check(gr.nValid == 8100 && gr.nRankDef == 4 && gr.nSaturated == 74 && gr.nFailed == 0, "GOSH meta-regression: 8100 valid, 4 not estimable (equal ablat), 74 saturated subsets");
            double[] mc2 = new double[4]; for (int b = 0; b < gr.nSub; b++) if (gr.valid(b)) { mc2[0] += gr.coef[0][b]; mc2[1] += gr.coef[1][b]; mc2[2] += gr.tau2[b]; mc2[3] += gr.i2[b]; } for (int q = 0; q < 4; q++) mc2[q] /= gr.nValid;
            near(mc2[0], 0.18556861000, 1e-6, "GOSH meta-regression: mean intercept"); near(mc2[1], -0.027107780301, 1e-8, "GOSH meta-regression: mean slope of ablat"); near(mc2[2], 0.15135039218, 1e-7, "GOSH meta-regression: mean tau^2"); near(mc2[3], 56.122903173, 1e-5, "GOSH meta-regression: mean residual I^2");
            near(Gosh.quantile(gr.coef[1], 0), -0.269395242, 1e-7, "GOSH meta-regression: smallest slope"); near(Gosh.quantile(gr.coef[1], 1), 0.2915429531, 1e-7, "GOSH meta-regression: largest slope"); near(Gosh.quantile(gr.coef[0], 0), -13.6161488327, 1e-5, "GOSH meta-regression: smallest intercept"); near(Gosh.quantile(gr.i2, 1), 96.5526474131, 1e-5, "GOSH meta-regression: largest I^2");
            near(gr.fullCoef[1], g.est[1], 1e-12, "GOSH meta-regression: full model equals the meta-regression"); near(gr.fullI2, g.I2, 1e-6, "GOSH meta-regression: full-model I^2");
            // factor with 3 levels, first 8 trials: subsets that miss a level are not estimable
            MetaData fd8 = new MetaData(); fd8.put("TE", java.util.Arrays.copyOf(by, 8)); fd8.put("seTE", java.util.Arrays.copyOf(bse, 8)); fd8.n = 8; MetaResult r8 = Meta.run(bsT, fd8);
            GoshReg gf = GoshReg.run(r8, Reg.design(r8, java.util.List.of(), nd, java.util.List.of("alloc"), fd, true, null), true, 20000, 1);
            check(gf.p == 3 && gf.nSub == 219 && gf.nValid == 83 && gf.nRankDef == 126 && gf.nSaturated == 10, "GOSH meta-regression (~ alloc, 8 trials): 219 subsets, 83 valid, 126 with a missing factor level, 10 saturated");
            double[] mf = new double[5]; for (int b = 0; b < gf.nSub; b++) if (gf.valid(b)) { for (int a = 0; a < 3; a++) mf[a] += gf.coef[a][b]; mf[3] += gf.tau2[b]; mf[4] += gf.i2[b]; } for (int q = 0; q < 5; q++) mf[q] /= gf.nValid;
            near(mf[0], -0.5143802971, 1e-7, "GOSH meta-regression (factor): mean intercept"); near(mf[1], -0.5107918589, 1e-7, "GOSH meta-regression (factor): mean coefficient of allocrandom"); near(mf[2], -1.1065179265, 1e-7, "GOSH meta-regression (factor): mean coefficient of allocsys"); near(mf[3], 0.3283762317, 1e-7, "GOSH meta-regression (factor): mean tau^2"); near(mf[4], 60.5232785104, 1e-5, "GOSH meta-regression (factor): mean I^2");
            // random sampling (metafor): sizes between p and k, reproducible, reported as not exhaustive
            GoshReg s1 = GoshReg.run(brT, D1, true, 3000, 7), s2 = GoshReg.run(brT, D1, true, 3000, 7), s3 = GoshReg.run(brT, D1, true, 3000, 8);
            check(!s1.exhaustive && s1.nSub == 3000 && s1.nTotal == 8178 && java.util.Arrays.equals(s1.coef[1], s2.coef[1]) && !java.util.Arrays.equals(s1.coef[1], s3.coef[1]), "GOSH meta-regression: random subsets are reproducible for a given seed and differ between seeds");
            int minSz = 99, maxSz = 0; for (int b = 0; b < s1.nSub; b++) { minSz = Math.min(minSz, s1.size[b]); maxSz = Math.max(maxSz, s1.size[b]); } check(minSz >= 2 && maxSz <= 13, "GOSH meta-regression: random subsets have between p and k studies");
            double msz = 0; for (int b = 0; b < s1.nSub; b++) msz += s1.size[b]; near(msz / s1.nSub, 6.5, 0.1, "GOSH meta-regression: mean subset size follows Binomial(k, 1/2)");
            boolean tooFew = false; try { GoshReg.run(r8, Reg.design(r8, java.util.List.of(), nd, java.util.List.of("alloc"), fd, true, new int[]{0, 1, 4, 6}), true, 100, 1); } catch (IllegalArgumentException e) { tooFew = true; } check(tooFew, "GOSH meta-regression: refuses models with too few studies");
            // cluster detection on synthetic subsets: two well separated groups that differ in study 0, and one single cloud
            { java.util.SplittableRandom rg = new java.util.SplittableRandom(3); int nn = 1200, kk = 10; double[][] Xc = new double[nn][2]; long[][] mk = new long[nn][1]; int[] szs = new int[nn];
              for (int b = 0; b < nn; b++) { boolean first = b < nn / 2; double u1 = rg.nextDouble(), u2 = rg.nextDouble(); double z1 = Math.sqrt(-2 * Math.log(1 - u1)) * Math.cos(2 * Math.PI * u2), z2 = Math.sqrt(-2 * Math.log(1 - u1)) * Math.sin(2 * Math.PI * u2);
                Xc[b][0] = (first ? -1 : 1) + 0.2 * z1; Xc[b][1] = (first ? 30 : 70) + 5 * z2; long m = 0; for (int j = 1; j < kk; j++) if (rg.nextBoolean()) m |= 1L << j; if (!first) m |= 1L; mk[b][0] = m; szs[b] = Long.bitCount(m); }
              GoshCluster cc = GoshCluster.detect(Xc, new String[]{"estimate", "I^2"}, mk, kk, szs, 1);
              check(cc.G == 2 && Math.abs(cc.size[0] - 600) <= 5, "cluster detection: two separated groups are found (G = " + cc.G + ")");
              boolean sep0 = false; for (int gq = 0; gq < cc.G; gq++) for (int[] jj : cc.separating(gq)) if (jj[0] == 0) sep0 = true; check(sep0, "cluster detection: study 0 is reported as the separating study");
              double[][] X1 = new double[nn][2]; for (int b = 0; b < nn; b++) { double u1 = rg.nextDouble(), u2 = rg.nextDouble(); X1[b][0] = Math.sqrt(-2 * Math.log(1 - u1)) * Math.cos(2 * Math.PI * u2); X1[b][1] = Math.sqrt(-2 * Math.log(1 - u1)) * Math.sin(2 * Math.PI * u2); }
              GoshCluster c1 = GoshCluster.detect(X1, new String[]{"a", "b"}, mk, kk, szs, 1); check(c1.G == 1 && !c1.found(), "cluster detection: one Gaussian cloud gives a single cluster (G = " + c1.G + ")"); }
            clusterGateTests();
            remlModeTests();
            exampleDataTests();
            clusterRobustnessTests();
            gs.detectClusters(); gr.detectClusters(); GoshReg gq2 = s1; gq2.detectClusters();
            check(gr.clusters != null && gr.clusters.nUsed == gr.nValid, "cluster detection uses the valid subsets of the meta-regression GOSH");
            java.awt.image.BufferedImage[] im2 = {org.metajava.plot.ExtraPlots.goshRegCoef(gr, po), org.metajava.plot.ExtraPlots.goshRegMatrix(gr, po), org.metajava.plot.ExtraPlots.goshRegCoef(gf, po), org.metajava.plot.ExtraPlots.goshRegMatrix(gf, po), org.metajava.plot.ExtraPlots.goshRegCoef(s1, po), org.metajava.plot.ExtraPlots.goshRegMatrix(s1, po), org.metajava.plot.ExtraPlots.gosh(a1, br, po, false, a1.detectClusters())};
            String[] nm3 = {"coefficients vs heterogeneity plot", "scatterplot matrix", "coefficients plot (factor)", "scatterplot matrix (factor)", "coefficients plot (random subsets)", "scatterplot matrix (random subsets)", "GOSH plot coloured by cluster"};
            for (int i = 0; i < im2.length; i++) { int dark = 0; for (int yy = 0; yy < im2[i].getHeight(); yy += 2) for (int xx = 0; xx < im2[i].getWidth(); xx += 2) if ((im2[i].getRGB(xx, yy) & 0xFFFFFF) != 0xFFFFFF) dark++; check(dark > 400, nm3[i] + " is drawn (" + dark + " non-white samples)"); }
            String gtx = RegReport.goshRegText(gr, br), gtf = RegReport.goshRegText(s1, br); check(gtx.contains("8,100") && gtx.contains("exhaustive") && gtf.contains("random sample of 3,000 subsets"), "report text of the meta-regression GOSH states the number of valid subsets and whether all subsets or a sample were analysed");
            check(RegReport.goshText(gs, br).contains("Cluster detection"), "report text of the ordinary GOSH includes the cluster detection when requested");
        } catch (Exception e) { e.printStackTrace(); check(false, "permutest / model comparison / GOSH tests threw " + e); }

        forestThemes(); funnelThemes();

        // ---- prediction interval and study symbols of the regression plots
        { double[][] dd = simData(1, 7, 14); MetaResult res = simResult(dd[0], dd[1]); java.util.Map<String, double[]> nd = new java.util.HashMap<>(); nd.put("x", dd[2]);
            Reg rg = Reg.run(res, java.util.List.of("x"), nd, java.util.List.of(), new java.util.HashMap<>(), true, new java.util.HashMap<>());
            double[] row = {1, dd[2][0]}; double[] ci = rg.predict(row, 0.95), pi = rg.predictInterval(row, 0.95);
            check(pi[0] <= ci[1] + 1e-12 && pi[1] >= ci[2] - 1e-12, "prediction interval of the regression line contains the confidence interval");
            check(Math.abs((pi[1] - ci[0]) - (pi[1] - pi[0]) / 2) < 1e-9, "prediction interval is symmetric around the fitted value");
            Plots.Opts o0 = new Plots.Opts(); o0.studyLabels = false; o0.confInt = true; Plots.Opts o2 = new Plots.Opts(); o2.studyLabels = false; o2.confInt = true; o2.predInt = true;
            for (String stl : new String[]{org.metajava.plot.RegPlots.DEFAULT_STYLE, org.metajava.plot.RegPlots.GG}) { o0.bubbleStyle = stl; o2.bubbleStyle = stl;
                BufferedImage a0 = Plots.bubble(rg, o0, res), a2 = Plots.bubble(rg, o2, res); boolean diff = a0.getWidth() != a2.getWidth(); for (int yy = 0; !diff && yy < Math.min(a0.getHeight(), a2.getHeight()); yy++) for (int xx = 0; xx < a0.getWidth() && !diff; xx++) if (a0.getRGB(xx, yy) != a2.getRGB(xx, yy)) diff = true;
                check(diff, "'" + stl + "' bubble plot: the prediction interval changes the picture"); }
            for (String sy : org.metajava.plot.RegPlots.SYMBOLS) { Plots.Opts q = new Plots.Opts(); q.studyLabels = false; q.symbol = sy; q.symSize = 1.4; q.predInt = true; q.confInt = true;
                try { check(Plots.bubble(rg, q, res) != null, "bubble plot with study symbol '" + sy + "'"); q.bubbleStyle = org.metajava.plot.RegPlots.GG; check(Plots.bubble(rg, q, res) != null, "ggplot2-style bubble plot with study symbol '" + sy + "'"); } catch (Exception e) { check(false, "study symbol " + sy + " threw " + e); } } }
        System.out.println(checks + " checks, " + failures + " failed");
        if (failures > 0) System.exit(1);
    }
}
