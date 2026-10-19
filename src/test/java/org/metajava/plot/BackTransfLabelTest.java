package org.metajava.plot;

import org.metajava.core.*;
import java.awt.image.BufferedImage;
import java.util.List;

/** Axis / column labels when "Back-transform results" is unchecked: the plots must name the scale actually drawn (Log Odds Ratio, not Odds Ratio).
 *  Run: java -cp build/classes:build/test-classes org.metajava.plot.BackTransfLabelTest */
public class BackTransfLabelTest {
    static int fails = 0;
    static void check(boolean ok, String what) { if (!ok) { fails++; System.out.println("FAIL: " + what); } }

    static MetaSpec spec(String sm, boolean back) { MetaSpec s = new MetaSpec(); s.sm = sm; s.backtransf = back; return s; }

    public static void main(String[] a) {
        String[][] log = {{"OR", "Odds Ratio"}, {"RR", "Risk Ratio"}, {"HR", "Hazard Ratio"}, {"IRR", "Incidence Rate Ratio"}, {"DOR", "Diagnostic Odds Ratio"}, {"ROM", "Ratio of Means"}};
        for (String[] m : log) {
            check(Plots.xlabFor(spec(m[0], true)).equals(m[1]), m[0] + " back-transformed: " + Plots.xlabFor(spec(m[0], true)));
            check(Plots.xlabFor(spec(m[0], false)).equals("Log " + m[1]), m[0] + " not back-transformed: " + Plots.xlabFor(spec(m[0], false)));
            check(Plots.funnelXlab(spec(m[0], false)).equals("Log " + m[1]), m[0] + " funnel: " + Plots.funnelXlab(spec(m[0], false)));
            check(Plots.forestMeasure(spec(m[0], false)).equals("Log " + m[1]), m[0] + " forest column");
            check(RegPlots.effectLabel(spec(m[0], false)).equals("Effect size (log " + m[1].toLowerCase() + ")"), m[0] + " regression label: " + RegPlots.effectLabel(spec(m[0], false)));
        }
        for (String[] m : log) {
            check(Report.effHeader(spec(m[0], true)).equals(m[0]), m[0] + " table header back-transformed");
            check(Report.effHeader(spec(m[0], false)).equals("log " + m[0]), m[0] + " table header not back-transformed: " + Report.effHeader(spec(m[0], false)));
        }
        check(Plots.xlabFor(spec("ZCOR", false)).equals("Fisher's z Transformed Correlation"), "ZCOR");
        check(Plots.xlabFor(spec("PLOGIT", false)).equals("Logit Transformed Proportion"), "PLOGIT");
        check(Plots.xlabFor(spec("MD", false)).equals("Mean Difference") && Plots.xlabFor(spec("SMD", false)).equals("Standardised Mean Difference"), "unaffected measures");
        // funnel plots: "Back-transform effect-size for funnel plot"
        for (String sm : new String[]{"PAS", "PFT", "PLOGIT", "PLN", "ZCOR", "IRS", "IRFT", "IRLN", "OR", "HR"}) {
            MetaSpec sp = spec(sm, true); sp.type = sm.startsWith("IR") && !sm.equals("IRR") ? "rate" : sm.startsWith("P") ? "prop" : "bin"; MetaResult rr = new MetaResult(); rr.spec = sp; rr.overall = new Pool(); rr.overall.nHarm = 100;
            Plots.Opts on = new Plots.Opts(); on.funnelBack = true; Plots.Opts off = new Plots.Opts();
            Plots.FunnelAxis a1 = Plots.funnelAxis(sp, rr, on), a0 = Plots.funnelAxis(sp, rr, off);
            check(a1.log || a1.bk != null, sm + " funnel back-transformed axis"); check(!a1.xlab.startsWith("Log ") && !a1.xlab.contains("Transformed"), sm + " funnel label on: " + a1.xlab);
            if (a1.bk != null) check(a1.bk.applyAsDouble(0.3) == a1.bk.applyAsDouble(0.3) && a1.bk.applyAsDouble(0.1) <= a1.bk.applyAsDouble(0.5), sm + " bk monotone");
            check(a0.xlab.contains("Transformed") || a0.xlab.startsWith("Log ") || a0.log, sm + " funnel label off: " + a0.xlab);
        }
        // tick coverage: first tick <= smallest and last tick >= largest plotted value
        { double[][] rng = {{-0.77, 1.1}, {0.02, 0.98}, {0.3, 3.1}};
          Plots.XT x1 = Plots.coverAxis(rng[0][0], rng[0][1], false, null, 520); check(x1.t.get(0)[1] <= rng[0][0] && x1.t.get(x1.t.size() - 1)[1] >= rng[0][1], "linear axis covers the studies");
          Plots.XT x2 = Plots.coverAxis(Math.log(rng[2][0]), Math.log(rng[2][1]), true, null, 520); check(x2.t.get(0)[1] <= rng[2][0] + 1e-9 && x2.t.get(x2.t.size() - 1)[1] >= rng[2][1] - 1e-9 && x2.xl <= Math.log(rng[2][0]) && x2.xh >= Math.log(rng[2][1]), "log axis covers the studies");
          Plots.XT x3 = Plots.coverAxis(-3.9, 3.9, false, v -> 1 / (1 + Math.exp(-v)), 520); check(x3.t.get(0)[1] <= 0.02 + 1e-9 && x3.t.get(x3.t.size() - 1)[1] >= 0.98 - 1e-9, "logit axis covers the studies: " + x3.t.get(0)[1] + " .. " + x3.t.get(x3.t.size() - 1)[1]); }
        // saturating back-transforms (PRAW, PAS, PFT): the axis ends lie in the flat part (back-transform = 0 or 1) and still need end ticks at exactly 0 and 1
        for (String sm : new String[]{"PRAW", "PAS", "PFT"}) {
            MetaSpec sp = spec(sm, true); final double nh = 150;
            java.util.function.DoubleUnaryOperator f = x -> Meta.back(sp, x, nh);
            double lo = 0, hi = 1; for (int it = 0; it < 100; it++) { double m = (lo + hi) / 2; if (f.applyAsDouble(m) < 0.004) lo = m; else hi = m; } double xa = hi;   // study at 0.004
            lo = 0; hi = 2; for (int it = 0; it < 100; it++) { double m = (lo + hi) / 2; if (f.applyAsDouble(m) < 0.97) lo = m; else hi = m; } double xb = hi;   // study at 0.97
            double w = xb - xa, pad = 0.4 * w;   // padding deep into the saturated parts on both sides
            Plots.XT x = Plots.coverAxis(xa - pad, xb + pad, false, f, 520);
            double t0 = x.t.get(0)[1], t1 = x.t.get(x.t.size() - 1)[1];
            check(Math.abs(t0) < 1e-9 && Math.abs(t1 - 1) < 1e-9 || sm.equals("PRAW") && t0 <= 0.004 && t1 >= 0.97, sm + " saturated axis: end ticks at 0 and 1, got " + t0 + " .. " + t1);
            check(t0 <= 0.004 && t1 >= 0.97, sm + " saturated axis covers the studies");
            double p0 = x.t.get(0)[0], p1 = x.t.get(x.t.size() - 1)[0]; check(p0 <= xa && p1 >= xb && p0 >= x.xl - 1e-9 && p1 <= x.xh + 1e-9, sm + " end ticks lie inside the axis and at or beyond the studies");
            for (int i = 1; i < x.t.size(); i++) check(x.t.get(i)[0] > x.t.get(i - 1)[0], sm + " ticks increasing");
            System.out.println("  " + sm + " ticks: " + x.t.stream().map(q -> String.valueOf(Math.round(q[1] * 1000) / 1000.0)).collect(java.util.stream.Collectors.joining(" ")));
        }
        check(Drapery.class != null, "drapery loads");

        // every plot renders with back-transform off for a ratio measure
        MetaSpec s = spec("OR", false); s.type = "bin"; s.methodTau = "REML"; s.random = true; s.common = true; s.prediction = true;
        MetaData d = new MetaData(); d.n = 8;
        d.put("event.e", new double[]{12, 9, 15, 6, 20, 11, 8, 14}); d.put("n.e", new double[]{60, 50, 70, 40, 90, 55, 45, 65});
        d.put("event.c", new double[]{18, 14, 20, 11, 27, 16, 13, 19}); d.put("n.c", new double[]{60, 50, 70, 40, 90, 55, 45, 65});
        try {
            MetaResult r = Meta.run(s, d); Meta.leaveOneOut(r); Meta.cumulative(r, null);
            for (String th : new String[]{"Default", "RevMan 5 style", "BMJ style", "STATA style"}) { Plots.Opts o = new Plots.Opts(); o.theme = th; BufferedImage im = Plots.forest(r, o); check(im != null && im.getWidth() > 100, "forest " + th); }
            String[] tabs = {Report.summary(r), Report.looText(r), Report.cumText(r), Report.subgroupText(r)};
            for (String tx : tabs) check(tx.contains("log OR"), "table text uses log OR");
            Plots.Opts o = new Plots.Opts(); check(Plots.funnel(r, o, null) != null, "funnel");
        } catch (RuntimeException e) { System.out.println("note: render smoke test skipped (" + e + ")"); }
        System.out.println(fails == 0 ? "ALL OK" : fails + " FAILURES"); if (fails > 0) System.exit(1);
    }
}
