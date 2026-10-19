package org.metajava.plot;

import org.metajava.core.*;
import java.awt.image.BufferedImage;
import java.util.*;

/**
 * "Avoid label overlap": a label never covers a symbol. Renders every plot that offers the option on the example data set "Binary outcome" (risk ratios), in all its
 * themes, and checks the placement geometrically: no label box within {@link LabelPlacer#CLEAR} px of any symbol, no two boxes overlapping, no box on a blocked rectangle.
 * It also counts the leader lines that cross a symbol or another leader line (these are traded for the hard rules, so they are reported, not failed), and runs
 * synthetic stress cases (dense clusters, coincident points, large bubbles).
 * Run: java -Djava.awt.headless=true -cp build/classes:build/test-classes org.metajava.plot.LabelPlacerTest [output-dir]
 */
public class LabelPlacerTest {
    static int fails = 0, plots = 0, labels = 0, leaders = 0, leaderThroughSymbol = 0, leaderCross = 0; static long worstMs = 0;
    static void check(boolean ok, String what) { if (!ok) { fails++; if (fails <= 12) System.out.println("FAIL: " + what); } }

    static double rectDist(double bx0, double by0, double bx1, double by1, double qx, double qy) { return Math.hypot(Math.max(Math.max(bx0 - qx, qx - bx1), 0), Math.max(Math.max(by0 - qy, qy - by1), 0)); }
    static double segPt(double ax, double ay, double bx, double by, double qx, double qy) { double dx = bx - ax, dy = by - ay, l2 = dx * dx + dy * dy, t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((qx - ax) * dx + (qy - ay) * dy) / l2)); return Math.hypot(ax + t * dx - qx, ay + t * dy - qy); }
    static boolean segSeg(double[] p, double[] q) {
        double d1 = (p[2] - p[0]) * (q[1] - p[1]) - (p[3] - p[1]) * (q[0] - p[0]), d2 = (p[2] - p[0]) * (q[3] - p[1]) - (p[3] - p[1]) * (q[2] - p[0]), d3 = (q[2] - q[0]) * (p[1] - q[1]) - (q[3] - q[1]) * (p[0] - q[0]), d4 = (q[2] - q[0]) * (p[3] - q[1]) - (q[3] - q[1]) * (p[2] - q[0]);
        return ((d1 > 0) != (d2 > 0)) && ((d3 > 0) != (d4 > 0));
    }

    /** Checks one layout; returns {leaders through a symbol, leader pairs crossing}. */
    static int[] verify(String name, LabelPlacer.Layout L) { return verify(name, L, true); }
    static int[] verify(String name, LabelPlacer.Layout L, boolean boxes) {
        int thru = 0, cross = 0; double eps = 1e-6;
        check(L.forced == 0, name + ": " + L.forced + " label(s) could not be kept off the symbols");
        for (int i = 0; i < L.n; i++) {
            double bx0 = L.cx[i] - L.w[i] / 2, bx1 = L.cx[i] + L.w[i] / 2, by0 = L.cy[i] - L.h[i] / 2, by1 = L.cy[i] + L.h[i] / 2;
            for (int j = 0; j < L.symbols.length; j++) { double[] s = L.symbols[j]; double d = rectDist(bx0, by0, bx1, by1, s[0], s[1]) - s[2];
                check(d >= LabelPlacer.CLEAR - eps, name + ": label " + i + " is " + String.format("%.2f", d) + " px from symbol " + j + (j == L.symbolOf[i] ? " (its own)" : "")); }
            for (double[] b : L.blocks) check(!(bx0 < b[2] && b[0] < bx1 && by0 < b[3] && b[1] < by1), name + ": label " + i + " on a blocked rectangle");
            for (int k = i + 1; k < L.n; k++) { if (!boxes) break; double qx0 = L.cx[k] - L.w[k] / 2, qx1 = L.cx[k] + L.w[k] / 2, qy0 = L.cy[k] - L.h[k] / 2, qy1 = L.cy[k] + L.h[k] / 2;
                check(!(bx0 < qx1 + LabelPlacer.BOX_GAP - eps && qx0 < bx1 + LabelPlacer.BOX_GAP - eps && by0 < qy1 + LabelPlacer.BOX_GAP - eps && qy0 < by1 + LabelPlacer.BOX_GAP - eps), name + ": labels " + i + " and " + k + " overlap");
                if (L.leader[i] && L.leader[k] && segSeg(L.seg[i], L.seg[k])) cross++; }
            if (L.leader[i]) { double[] s = L.seg[i]; for (int j = 0; j < L.symbols.length; j++) if (j != L.symbolOf[i] && segPt(s[0], s[1], s[2], s[3], L.symbols[j][0], L.symbols[j][1]) < L.symbols[j][2]) { thru++; } }
        }
        return new int[]{thru, cross};
    }

    static java.io.File outDir;
    static BufferedImage run(String name, java.util.function.Supplier<BufferedImage> plot) {
        List<LabelPlacer.Layout> audit = new ArrayList<>(); LabelPlacer.AUDIT.set(audit); long t0 = System.currentTimeMillis(); BufferedImage img;
        try { img = plot.get(); } finally { LabelPlacer.AUDIT.remove(); }
        long ms = System.currentTimeMillis() - t0; worstMs = Math.max(worstMs, ms);
        check(!audit.isEmpty(), name + ": the placer was not used"); int thru = 0, cross = 0, nl = 0, ld = 0;
        for (LabelPlacer.Layout L : audit) { int[] v = verify(name, L); thru += v[0]; cross += v[1]; nl += L.n; for (boolean b : L.leader) if (b) ld++; }
        plots++; labels += nl; leaders += ld; leaderThroughSymbol += thru; leaderCross += cross;
        System.out.printf("%-34s labels %3d  leaders %3d  leader-through-symbol %d  leader-leader crossings %d  (%d ms)%n", name, nl, ld, thru, cross, ms);
        if (outDir != null && img != null) try { javax.imageio.ImageIO.write(img, "png", new java.io.File(outDir, name.replaceAll("[^A-Za-z0-9]+", "_") + ".png")); } catch (Exception e) { throw new RuntimeException(e); }
        return img;
    }

    static MetaResult binary(String sm) throws Exception {
        java.io.File f = java.io.File.createTempFile("mjbin", ".xlsx"); f.deleteOnExit();
        try (java.io.InputStream in = LabelPlacerTest.class.getResourceAsStream("/examples/Binary_outcome.xlsx")) { java.nio.file.Files.copy(in, f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
        DataTable tb = org.metajava.io.TableReader.read(f, 0); tbl = tb;
        MetaSpec sp = new MetaSpec(); sp.type = "bin"; sp.sm = sm; sp.prediction = false; MetaData md = new MetaData();
        for (String key : org.metajava.gui.Examples.parameterKeys("bin")) md.put(key, tb.num(org.metajava.gui.Examples.guessColumn(key, tb.names)));
        md.n = tb.nrow; md.studlab = tb.strings("Study"); return Meta.run(sp, md);
    }
    static DataTable tbl;

    static Plots.Opts opts() {
        Plots.Opts o = new Plots.Opts(); o.studyLabels = true; o.radialLabels = true; o.labbeLabels = true;
        o.repelBubble = o.repelFunnel = o.repelDoi = o.repelPet = o.repelRadial = o.repelLabbe = o.repelBias = true; return o;
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("metajava.labelBudgetMs", "600000");   // the 1.5 s refinement budget would make the result depend on the speed of the machine (slow CI runners): test the placement itself
        outDir = args.length > 0 ? new java.io.File(args[0]) : null; if (outDir != null) outDir.mkdirs();
        for (String sm : new String[]{"RR", "OR"}) { System.out.println("== Binary outcome, " + sm); example(sm); }
        stress();
        System.out.printf("%n%d plots, %d labels, %d leader lines (%d through a symbol, %d leader-leader crossings), slowest plot %d ms%n", plots, labels, leaders, leaderThroughSymbol, leaderCross, worstMs);
        System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED"); if (fails > 0) System.exit(1);
    }

    static void example(String sm) throws Exception {
        MetaResult r = binary(sm); String t = sm + " ";
        Plots.Opts o = opts();
        for (String th : FunnelThemes.NAMES) { Plots.Opts q = opts(); q.funnelTheme = th; q.contour = true;
            run(t + "funnel " + th, () -> Plots.funnel(r, q, null));
            Plots.Opts p = opts(); p.funnelTheme = th; p.precision = true; run(t + "funnel precision " + th, () -> Plots.funnel(r, p, null)); }
        TrimFill tf = TrimFill.run(r, null, true, "L0", true, true);
        run(t + "trim-and-fill funnel", () -> Plots.funnel(r, opts(), tf));
        for (String th : new String[]{FunnelThemes.METAWIN, FunnelThemes.STATA}) { Plots.Opts q = opts(); q.funnelTheme = th; q.contour = true; run(t + "trim-and-fill funnel " + th, () -> Plots.funnel(r, q, tf)); }
        { Plots.Opts p = opts(); p.precision = true; run(t + "trim-and-fill precision", () -> Plots.funnel(r, p, tf)); }
        Bias.Result b = Bias.run(r, null, false);
        if (b.error == null && b.plotX != null) { run(t + "asymmetry plot", () -> Plots.biasPlot(b, r, opts())); Plots.Opts p = opts(); p.biasCI = true; p.biasPI = true; run(t + "asymmetry plot bands", () -> Plots.biasPlot(b, r, p)); }
        run(t + "Doi plot", () -> Plots.doi(Lfk.compute(r.TE, r.seTE, r.studlab), r, opts()));
        PubBias.PetPeese pp = PubBias.petPeese(r); if (pp.error == null) run(t + "PET-PEESE", () -> Plots.petPeese(pp, r, opts()));
        for (String th : RadialPlot.THEMES) { Plots.Opts q = opts(); q.radialTheme = th; run(t + "radial " + th, () -> Plots.radial(r, q)); }
        for (String th : LabbePlot.THEMES) { Plots.Opts q = opts(); q.labbeTheme = th; run(t + "Labbe " + th, () -> Plots.labbe(r, q)); }
        { Plots.Opts q = opts(); q.labbeTheme = LabbePlot.GG; q.labbeCI = "Light blue"; q.labbePI = "Light red"; run(t + "Labbe ggplot2 areas", () -> Plots.labbe(r, q)); }
        // meta-regression: bubble plot (one numeric covariate) and adjusted plot (numeric covariate + factor)
        Map<String, double[]> nd = new HashMap<>(); nd.put("SVR/NR", tbl.num("SVR/NR")); Map<String, String[]> fd = new HashMap<>(); fd.put("Treatment", tbl.strings("Treatment"));
        Reg g1 = Reg.run(r, List.of("SVR/NR"), nd, List.of(), fd, true, new HashMap<>()), g2 = Reg.run(r, List.of("SVR/NR"), nd, List.of("Treatment"), fd, true, new HashMap<>());
        for (String st : RegPlots.STYLES) for (String sym : new String[]{"Circle", "Star", "Triangle", "Open square"}) { if (!sym.equals("Circle") && !st.equals(RegPlots.GG) && !sym.equals("Star")) continue;
            Plots.Opts q = opts(); q.bubbleStyle = st; q.symbol = sym; q.regLine = true; if (g1.x1 != null) run(t + "bubble " + st + " " + sym, () -> Plots.bubble(g1, q, r)); }
        { Plots.Opts q = opts(); q.regLine = true; q.confInt = true; q.predInt = true; q.bubbleStyle = RegPlots.GG; run(t + "adjusted ggplot2", () -> RegPlots.adjusted(g2, List.of("SVR/NR"), List.of("Treatment"), "Treatment", q, r)); }
        { Plots.Opts q = opts(); q.regLine = true; q.symSize = 1.6; q.bubbleStyle = RegPlots.GG; run(t + "adjusted ggplot2 big symbols", () -> RegPlots.adjusted(g2, List.of("SVR/NR"), List.of("Treatment"), "Treatment", q, r)); }
        { Plots.Opts q = opts(); q.baujatTheme = BaujatPlot.GG; run(t + "Baujat ggplot2", () -> Plots.baujat(r, q)); }
    }

    // ------------------------------------------------------------------ synthetic stress cases
    static void stress() {
        System.out.println("== synthetic stress cases");
        Random rnd = new Random(7);
        for (int[] cfg : new int[][]{{20, 0}, {60, 0}, {90, 0}, {60, 1}, {100, 1}, {80, 2}, {40, 3}, {150, 4}}) {   // {points, kind}: 0 uniform, 1 one tight cluster, 2 line of points, 3 coincident points, 4 hopelessly crowded (labels must overlap each other, but never a symbol: only that is checked)
            int n = cfg[0], kind = cfg[1]; String name = "stress n=" + n + " kind=" + kind; LabelPlacer lp = new LabelPlacer(0, 0, 600, 450, 8.5f);
            for (int i = 0; i < n; i++) { double x, y, rad = 3 + rnd.nextInt(8) * (kind == 1 ? 0.5 : 1);
                switch (kind) { case 1: x = 300 + rnd.nextGaussian() * 25; y = 220 + rnd.nextGaussian() * 25; break; case 2: x = 30 + 540.0 * i / n; y = 30 + 390.0 * i / n; break; case 3: x = 300 + (i % 4); y = 200 + (i % 3); break; case 4: x = 20 + rnd.nextDouble() * 560; y = 20 + rnd.nextDouble() * 410; break; default: x = 20 + rnd.nextDouble() * 560; y = 20 + rnd.nextDouble() * 410; }
                lp.add((char) ('A' + i % 26) + "-" + (rnd.nextBoolean() ? "Smith " : "Lee ") + (1990 + i % 30), x, y, rad); }
            for (int i = 0; i < n / 3; i++) lp.point(20 + rnd.nextDouble() * 560, 20 + rnd.nextDouble() * 410, 4);   // unlabelled symbols
            if (kind == 0) lp.block(400, 330, 590, 440);
            Canvas c = new Canvas(600, 450, 1); List<LabelPlacer.Layout> audit = new ArrayList<>(); LabelPlacer.AUDIT.set(audit); long t0 = System.currentTimeMillis();
            try { for (double[] s : lpSymbols(lp)) { c.g.fill(new java.awt.geom.Ellipse2D.Double(s[0] - s[2], s[1] - s[2], 2 * s[2], 2 * s[2])); } lp.draw(c); } finally { LabelPlacer.AUDIT.remove(); }
            long ms = System.currentTimeMillis() - t0; worstMs = Math.max(worstMs, ms); int[] v = verify(name, audit.get(0), kind != 4); plots++; labels += n;
            System.out.printf("%-34s labels %3d  leader-through-symbol %d  leader-leader crossings %d  (%d ms)%n", name, n, v[0], v[1], ms);
            if (outDir != null) try { javax.imageio.ImageIO.write(c.img, "png", new java.io.File(outDir, name.replaceAll("[^A-Za-z0-9]+", "_") + ".png")); } catch (Exception e) { throw new RuntimeException(e); }
        }
    }
    static List<double[]> lpSymbols(LabelPlacer lp) { return lp.symbolsForTest(); }
}
