package org.metajava;

import org.metajava.core.*;
import org.metajava.plot.Plots;
import java.awt.image.BufferedImage;

/** Forest-plot x-axis checks: extreme target effect-sizes for all single-group measures (axis must keep the studies visible, scales must match the table),
 *  user-defined limits and arrows on truncated intervals. Run: java -cp build/classes:build/test-classes org.metajava.ForestAxisTest */
public class ForestAxisTest {
    static int fails = 0;
    static void check(boolean ok, String what) { if (!ok) { fails++; System.out.println("FAIL: " + what); } }

    static MetaResult single(String type, String sm, double target, double scale) {
        MetaSpec s = new MetaSpec(); s.type = type; s.sm = sm; s.methodTau = "REML"; s.random = true; s.common = false; s.backtransf = true; s.nullEffect = target; s.prediction = true;
        MetaData d = new MetaData(); d.n = 8;
        if (type.equals("prop")) { d.put("event", new double[]{12, 30, 22, 45, 8, 19, 33, 27}); d.put("n", new double[]{50, 90, 70, 120, 40, 60, 100, 85}); s.pscale = scale; }
        else if (type.equals("rate")) { d.put("event", new double[]{12, 30, 22, 45, 8, 19, 33, 27}); d.put("time", new double[]{500, 900, 700, 1200, 400, 600, 1000, 850}); s.irscale = scale; }
        else { d.put("n", new double[]{30, 40, 25, 50, 35, 28, 44, 31}); d.put("mean", new double[]{5.1, 4.8, 5.5, 4.9, 5.2, 6.0, 4.4, 5.3}); d.put("sd", new double[]{1.2, 1.1, 1.4, 1.0, 1.3, 1.2, 1.1, 1.5}); }
        return Meta.run(s, d);
    }

    /** Dark pixels in the marker columns of the study rows: ~0 when the studies fall outside the axis. */
    static int ink(BufferedImage im) {
        int dark = 0;
        for (int y = 40; y < im.getHeight() - 60; y++) for (int x = (int) (im.getWidth() * 0.34); x < (int) (im.getWidth() * 0.66); x++) { int p = im.getRGB(x, y); if (((p >> 16 & 0xff) + (p >> 8 & 0xff) + (p & 0xff)) / 3 < 200) dark++; }
        return dark;
    }

    static boolean same(BufferedImage a, BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) return false;
        for (int y = 0; y < a.getHeight(); y++) for (int x = 0; x < a.getWidth(); x++) if (a.getRGB(x, y) != b.getRGB(x, y)) return false;
        return true;
    }

    public static void main(String[] args) {
        Object[][] cfg = {{"prop", "PRAW", 1.0}, {"prop", "PLN", 1.0}, {"prop", "PLOGIT", 1.0}, {"prop", "PAS", 1.0}, {"prop", "PFT", 1.0}, {"prop", "PFT", 100.0},
            {"rate", "IR", 1000.0}, {"rate", "IRLN", 1000.0}, {"rate", "IRS", 1000.0}, {"rate", "IRFT", 1000.0}, {"rate", "IR", 1.0}, {"mean", "MRAW", 1.0}, {"mean", "MLN", 1.0}};
        for (Object[] c : cfg) for (double fr : new double[]{0.9, 0.1}) for (String th : new String[]{"Default", "RevMan 5 style", "BMJ style", "STATA style"}) {
            String type = (String) c[0], sm = (String) c[1]; double sc = (Double) c[2];
            double tg = type.equals("prop") ? fr * sc : type.equals("rate") ? (fr > 0.5 ? 60 : 4) * sc / 1000 : fr > 0.5 ? 7 : 3;
            String tag = sm + " scale " + sc + " target " + tg + " " + th;
            try { Plots.Opts o = new Plots.Opts(); o.theme = th; o.testOverall = true; check(ink(Plots.forest(single(type, sm, tg, sc), o)) > 150, "studies not visible: " + tag); }
            catch (Throwable t) { check(false, "exception " + t + ": " + tag); }
        }
        // log single-group axes (PLN, IRLN, MLN): limits are 1-2-5 values that contain every study CI (lower limit <= lowest lower bound, upper limit >= highest upper bound)
        {
            double[][] props = {{12, 30, 22, 45, 8, 19, 33, 27}, {2, 5, 3, 7, 1, 4, 6, 3}, {1, 1, 0, 2, 1}};
            double[][] ns = {{50, 90, 70, 120, 40, 60, 100, 85}, {900, 1500, 1100, 2200, 800, 1400, 1900, 1000}, {900, 1500, 800, 2200, 1000}};
            for (String sm : new String[]{"PLN", "IRLN", "MLN"}) for (int v = 0; v < 3; v++) for (String th : new String[]{"Default", "STATA style"}) {
                MetaSpec sp = new MetaSpec(); sp.methodTau = "REML"; sp.random = true; sp.common = false; sp.backtransf = true; sp.prediction = true; sp.sm = sm; MetaData dd = new MetaData();
                if (sm.equals("PLN")) { sp.type = "prop"; dd.put("event", props[v]); dd.put("n", ns[v]); }
                else if (sm.equals("IRLN")) { sp.type = "rate"; double[] ev = props[v].clone(); for (int i = 0; i < ev.length; i++) ev[i] *= Math.pow(10, v == 2 ? 3 : v); dd.put("event", ev); double[] tm = new double[ev.length]; java.util.Arrays.fill(tm, 1); dd.put("time", tm); }
                else { sp.type = "mean"; double sc = Math.pow(10, v - 1); double[] mn = new double[]{5.1 * sc, 4.8 * sc, 5.5 * sc, 4.9 * sc, 5.2 * sc}, nn = {30, 40, 25, 50, 35}, sdv = {1.2 * sc, 1.1 * sc, 1.4 * sc, 1.0 * sc, 1.3 * sc}; dd.put("mean", mn); dd.put("n", nn); dd.put("sd", sdv); }
                dd.n = dd.get(sm.equals("PLN") ? "event" : sm.equals("IRLN") ? "event" : "mean").length;
                String tag = sm + " variant " + v + " " + th;
                try {
                    MetaResult rr = Meta.run(sp, dd); Plots.Opts o = new Plots.Opts(); o.theme = th; Plots.forest(rr, o); double[] ax = Plots.lastForestAxis(); double mn = Double.POSITIVE_INFINITY, mx = 0;
                    for (int i = 0; i < rr.TE.length; i++) { double bl = Meta.back(rr.spec, rr.lower[i], Double.NaN); if (bl > 0) mn = Math.min(mn, bl);   // a bound of 0 (zero events) cannot lie on a log axis: its line is cut with an arrow
                         mx = Math.max(mx, Meta.back(rr.spec, rr.upper[i], Double.NaN)); }
                    check(ax[0] <= mn * (1 + 1e-9), "lower limit " + ax[0] + " above the lowest bound " + mn + ": " + tag);
                    check(ax[1] >= mx * (1 - 1e-9), "upper limit " + ax[1] + " below the highest bound " + mx + ": " + tag);
                } catch (Throwable t) { check(false, "exception " + t + ": " + tag); }
            }
            for (double x : new double[]{0.003, 0.02, 0.09, 0.1, 0.12, 0.5, 0.7, 12, 80, 663, 1000, 4e-5}) {
                double dn = Plots.round125(x, false), up = Plots.round125(x, true);
                check(dn <= x * (1 + 1e-9) && up >= x * (1 - 1e-9) && up / dn <= 2.5 + 1e-9, "round125 " + x + " -> " + dn + " / " + up);
            }
            check(Plots.round125(0.09, false) == 0.05 && Plots.round125(12, true) == 20 && Plots.round125(663, true) == 1000 && Plots.round125(0.003, false) == 0.002, "round125 values");
        }
        // user-defined limits: partial truncation changes the drawing (arrows), entirely outside limits do not throw, tick interval accepted
        double[] te = {-0.6931, -0.6539, -0.7985, -0.5447, -0.8675, -0.4943, -0.7340, -0.6162, -0.9416, -1.9, 0.9}, se = {0.0719, 0.0691, 0.08, 0.0619, 0.0858, 0.0588, 0.0749, 0.0665, 0.0926, 0.3, 0.4};
        MetaSpec s = new MetaSpec(); s.type = "gen"; s.sm = "HR"; s.methodTau = "REML"; s.random = true; s.common = true; s.backtransf = true; s.prediction = true;
        MetaData d = new MetaData(); d.put("TE", te); d.put("seTE", se); d.n = te.length; MetaResult m = Meta.run(s, d);
        for (String th : new String[]{"Default", "RevMan 5 style", "BMJ style", "STATA style"}) {
            Plots.Opts o = new Plots.Opts(); o.theme = th; BufferedImage auto = Plots.forest(m, o);
            for (String[] l : new String[][]{{"0.6", ""}, {"", "0.8"}, {"0.5", "0.9"}, {"3", "5"}}) {
                o.xMin = l[0]; o.xMax = l[1]; BufferedImage im = null;
                try { im = Plots.forest(m, o); } catch (Throwable t) { check(false, "exception with limits " + l[0] + ".." + l[1] + " " + th + ": " + t); }
                check(im != null && !same(im, auto), "limits " + l[0] + ".." + l[1] + " do not change the plot: " + th);
            }
            o.xMin = ""; o.xMax = ""; o.xTick = "0.5"; check(Plots.forest(m, o) != null, "tick interval " + th);
        }
        System.out.println(fails == 0 ? "ForestAxisTest: all checks passed" : "ForestAxisTest: " + fails + " failure(s)");
        if (fails > 0) System.exit(1);
    }
}
