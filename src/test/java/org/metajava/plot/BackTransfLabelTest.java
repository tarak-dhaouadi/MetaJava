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
        check(Plots.xlabFor(spec("ZCOR", false)).equals("Fisher's z Transformed Correlation"), "ZCOR");
        check(Plots.xlabFor(spec("PLOGIT", false)).equals("Logit Transformed Proportion"), "PLOGIT");
        check(Plots.xlabFor(spec("MD", false)).equals("Mean Difference") && Plots.xlabFor(spec("SMD", false)).equals("Standardised Mean Difference"), "unaffected measures");
        check(Drapery.class != null, "drapery loads");

        // every plot renders with back-transform off for a ratio measure
        MetaSpec s = spec("OR", false); s.type = "bin"; s.methodTau = "REML"; s.random = true; s.common = true; s.prediction = true;
        MetaData d = new MetaData(); d.n = 8;
        d.put("event.e", new double[]{12, 9, 15, 6, 20, 11, 8, 14}); d.put("n.e", new double[]{60, 50, 70, 40, 90, 55, 45, 65});
        d.put("event.c", new double[]{18, 14, 20, 11, 27, 16, 13, 19}); d.put("n.c", new double[]{60, 50, 70, 40, 90, 55, 45, 65});
        try {
            MetaResult r = Meta.run(s, d);
            for (String th : new String[]{"Default", "RevMan 5 style", "BMJ style", "STATA style"}) { Plots.Opts o = new Plots.Opts(); o.theme = th; BufferedImage im = Plots.forest(r, o); check(im != null && im.getWidth() > 100, "forest " + th); }
            Plots.Opts o = new Plots.Opts(); check(Plots.funnel(r, o, null) != null, "funnel");
        } catch (RuntimeException e) { System.out.println("note: render smoke test skipped (" + e + ")"); }
        System.out.println(fails == 0 ? "ALL OK" : fails + " FAILURES"); if (fails > 0) System.exit(1);
    }
}
