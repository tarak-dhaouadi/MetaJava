package org.metajava.plot;

import org.metajava.core.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/**
 * L'Abbe plot themes and colours.
 * <ul>
 * <li>Default: the look of meta::labbe / metafor::labbe (black frame, light-grey no-effect line, dashed estimated-effect line, grey circles);</li>
 * <li>ggplot2 style: open axes, dotted grid, black no-effect line, dashed estimated-effect line, 95% confidence area (dark grey) and 95% prediction area (light grey)
 *     around that line, boxed legend. The legend sits at the top left while the estimated-effect line lies below the no-effect line and moves to the lower right
 *     when it lies above it, so that it never covers the studies.</li>
 * </ul>
 * The seven colour options apply to both themes; "Theme default" keeps what the theme prescribes. In the Default theme the confidence and prediction areas are
 * only drawn when a colour has been chosen for them (the current meta / metafor plot has none).
 */
public final class LabbePlot {
    private LabbePlot() {}
    public static final String DEFAULT = "Default", GG = "ggplot2 style", AUTO = "Theme default", WHITE = "White";
    public static final String[] THEMES = {DEFAULT, GG};
    public static final String[] COLOURS = prepend(RegPlots.COLOURS, AUTO), BACK_COLOURS = prepend(RegPlots.COLOURS, AUTO, WHITE);
    private static String[] prepend(String[] a, String... first) { String[] o = new String[first.length + a.length]; System.arraycopy(first, 0, o, 0, first.length); System.arraycopy(a, 0, o, first.length, a.length); return o; }

    static boolean isGg(Plots.Opts o) { return GG.equals(o.labbeTheme); }
    /** Colour chosen in a drop-down, or null for "Theme default". */
    static Color chosen(String name) { return BaujatPlot.colourOf(name); }
    static Color pick(String name, Color dflt) { Color c = chosen(name); return c != null ? c : dflt; }
    private static boolean light(Color c) { return 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue() > 238; }

    static final Color GG_CI = new Color(215, 215, 215), GG_PI = new Color(241, 241, 241), GG_CIRCLE = new Color(160, 160, 160), DEF_CIRCLE = new Color(211, 211, 211);

    /** Event rate in the experimental group implied by the control rate x and the pooled effect te (NaN when the measure has no line). */
    static double lineY(String sm, double te, double x) {
        double y;
        switch (sm) {
            case "RR": y = Math.exp(te) * x; break;
            case "OR": { double e = Math.exp(te); y = e * x / (1 - x + e * x); break; }
            case "RD": y = x + te; break;
            case "ASD": y = Math.pow(Math.sin(Math.max(0, Math.min(Math.PI / 2, Math.asin(Math.sqrt(x)) + te))), 2); break;
            default: return Double.NaN;
        }
        return Math.max(0, Math.min(1, y));
    }
    static boolean hasLine(String sm) { return sm.equals("RR") || sm.equals("OR") || sm.equals("RD") || sm.equals("ASD"); }

    /** The pooled result drawn with confidence / prediction areas: random effects when selected (it has the prediction interval), else the common-effect estimate. */
    static final class Model { double te, ciLo, ciUp, piLo = Double.NaN, piUp = Double.NaN; boolean random; boolean hasCi() { return !Double.isNaN(ciLo) && !Double.isNaN(ciUp); } boolean hasPi() { return !Double.isNaN(piLo) && !Double.isNaN(piUp); } }
    static Model model(MetaResult r) {
        MetaSpec s = r.spec; Pool p = r.overall; Model m = new Model();
        if (s.random) { m.random = true; m.te = p.teRandom; m.ciLo = p.loRandom; m.ciUp = p.upRandom; m.piLo = p.loPred; m.piUp = p.upPred; }
        else { m.te = p.teCommon; m.ciLo = p.loCommon; m.ciUp = p.upCommon; }
        return m;
    }

    /** Area between the curves implied by two effect limits (control rate on the x axis, experimental rate on the y axis). */
    static void area(Plots.Axes a, MetaSpec s, double lo, double up, double lim, Color col) {
        if (Double.isNaN(lo) || Double.isNaN(up) || !hasLine(s.sm)) return;
        int N = 200; Path2D p = new Path2D.Double(); Graphics2D g = a.c.g;
        for (int i = 0; i <= N; i++) { double x = lim * i / N, y = lineY(s.sm, up, x); if (i == 0) p.moveTo(a.X(x), a.Y(y)); else p.lineTo(a.X(x), a.Y(y)); }
        for (int i = N; i >= 0; i--) { double x = lim * i / N; p.lineTo(a.X(x), a.Y(lineY(s.sm, lo, x))); }
        p.closePath(); g.setColor(col); g.fill(p);
    }

    // ------------------------------------------------------------------ ggplot2 style
    private static String pct(double level) { double v = level * 100; return Math.abs(v - Math.rint(v)) < 1e-9 ? String.valueOf((long) Math.rint(v)) : String.format(Locale.ROOT, "%.1f", v); }

    /** Entries of the legend of the ggplot2-style L'Abbe plot: texts and kinds (0 solid line, 1 dashed line, 2 confidence area, 3 prediction area). */
    static Object[] ggLegendItems(MetaSpec s, boolean line, boolean ci, boolean pi) {
        List<String> labs = new ArrayList<>(); List<Integer> kinds = new ArrayList<>();
        labs.add("Reference Line of No Effect"); kinds.add(0);
        if (line) { labs.add("Line for the Estimated Effect"); kinds.add(1); }
        if (ci) { labs.add(pct(s.levelMa) + "% Confidence Interval"); kinds.add(2); }
        if (pi) { labs.add(pct(s.levelPredict) + "% Prediction Interval"); kinds.add(3); }
        return new Object[]{labs, kinds};
    }
    /** Legend box {x, y, width, height}: top left, or lower right when the estimated-effect line lies above the no-effect line (where the studies are). */
    static double[] ggLegendBox(Graphics2D g, List<String> labs, boolean upper, double x0, double y0, double x1, double y1) {
        FontMetrics fm = g.getFontMetrics(Canvas.font(12.5f)); double tw = 0; for (String t : labs) tw = Math.max(tw, fm.stringWidth(t));
        double sample = 40, rowH = 21, bw = 12 + sample + 12 + tw + 14, bh = 10 + rowH * labs.size() + 2;
        return new double[]{upper ? x1 - 10 - bw : x0 + 10, upper ? y1 - 10 - bh : y0 + 10, bw, bh};
    }
    static BufferedImage gg(MetaResult r, Plots.Opts o, double[] w, double lim, double[] ee, double[] ne, double[] ec, double[] nc) {
        MetaSpec s = r.spec; int n = r.TE.length; Canvas c = new Canvas(640, 580, o.scale); Graphics2D g = c.g;
        double L = 80, R = 22, T = 22, B = 66, x0 = L, x1 = c.w / c.scaleF() - R, y0 = T, y1 = c.h / c.scaleF() - B;
        double lo = -0.04 * lim, hi = 1.04 * lim;
        final double fx0 = x0, fx1 = x1, fy0 = y0, fy1 = y1, flo = lo, fhi = hi;
        Plots.Axes a = new Plots.Axes(); a.c = c; a.x0 = x0; a.x1 = x1; a.y0 = y0; a.y1 = y1; a.xl = lo; a.xh = hi; a.yl = lo; a.yh = hi; a.yRev = false;
        Color bg = pick(o.labbeBack, Color.WHITE), refC = pick(o.labbeRef, Color.BLACK), estC = pick(o.labbeEst, Color.BLACK), ciC = pick(o.labbeCI, GG_CI), piC = pick(o.labbePI, GG_PI),
              circ = pick(o.labbeCircle, GG_CIRCLE), edge = pick(o.labbeSymbol, Color.BLACK);
        Model m = model(r); boolean line = hasLine(s.sm) && !Double.isNaN(m.te), ci = line && m.hasCi(), pi = line && m.hasPi();
        Shape clip0 = g.getClip(); Rectangle2D.Double panel = new Rectangle2D.Double(x0, y0, x1 - x0, y1 - y0);
        g.setColor(bg); g.fill(panel);
        double[] tk = Canvas.pretty(0, lim, 5); List<Double> ticks = new ArrayList<>(); for (double t : tk) if (t >= -1e-9 && t <= lim + 1e-9) ticks.add(t);
        g.setClip(panel);
        g.setColor(light(bg) ? new Color(190, 190, 190) : Color.WHITE); g.setStroke(new BasicStroke(0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{1.5f, 3f}, 0));
        for (double t : ticks) { c.line(a.X(t), y0, a.X(t), y1); c.line(x0, a.Y(t), x1, a.Y(t)); }
        g.setStroke(new BasicStroke(1f));
        if (pi) area(a, s, m.piLo, m.piUp, lim, piC);
        if (ci) area(a, s, m.ciLo, m.ciUp, lim, ciC);
        g.setColor(refC); g.setStroke(new BasicStroke(1.1f)); c.line(a.X(0), a.Y(0), a.X(lim), a.Y(lim));
        if (line) { g.setColor(estC); g.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{4.5f, 3.5f}, 0));
            Path2D p = new Path2D.Double(); for (int i = 0; i <= 200; i++) { double x = lim * i / 200.0, y = lineY(s.sm, m.te, x); if (i == 0) p.moveTo(a.X(x), a.Y(y)); else p.lineTo(a.X(x), a.Y(y)); } g.draw(p); }
        g.setStroke(new BasicStroke(1f)); g.setClip(clip0);
        // studies: larger circles first so that small ones stay visible
        List<Integer> ord = new ArrayList<>(); for (int i = 0; i < n; i++) if (!Double.isNaN(r.TE[i]) && !Double.isNaN(w[i]) && ne[i] > 0 && nc[i] > 0) ord.add(i);
        ord.sort((p, q) -> Double.compare(w[q], w[p]));
        LabelPlacer lp = LabelPlacer.on(o.repelLabbe && o.studyLabels, a.x0, a.y0, a.x1, a.y1);
        if (lp != null) { double[] lg = ggLegendBox(g, (List<String>) ggLegendItems(s, line, ci, pi)[0], line && lineY(s.sm, m.te, lim / 2) > lim / 2, x0, y0, x1, y1); lp.block(lg[0] - 2, lg[1] - 2, lg[0] + lg[2] + 2, lg[1] + lg[3] + 2); }   // the legend is drawn after the labels
        for (int i : ord) { double xx = a.X(ec[i] / nc[i]), yy = a.Y(ee[i] / ne[i]), dia = 4 * w[i], rad = dia / 2;
            g.setColor(circ); g.fill(new Ellipse2D.Double(xx - rad, yy - rad, dia, dia)); g.setColor(edge); g.setStroke(new BasicStroke(1f)); g.draw(new Ellipse2D.Double(xx - rad, yy - rad, dia, dia));
            if (o.studyLabels) { if (lp != null) lp.add(r.studlab[i], xx, yy, rad); else { g.setColor(Color.BLACK); c.text(r.studlab[i], xx - rad - 3, yy + 3, 8, 2, false); } } }
        if (lp != null) lp.draw(c);
        // open axes (left and bottom), ticks, tick labels and titles
        g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1f)); c.line(x0, y0, x0, y1); c.line(x0, y1, x1, y1);
        int dec = 1; for (double t : ticks) { String q = Canvas.tick(t); int d = q.indexOf('.'); if (d >= 0) dec = Math.max(dec, q.length() - d - 1); }
        for (double t : ticks) { String lab = String.format(Locale.ROOT, "%." + dec + "f", t); double xx = a.X(t), yy = a.Y(t);
            c.line(xx, y1, xx, y1 + 5); c.text(lab, xx, y1 + 20, 12, 1, false); c.line(x0 - 5, yy, x0, yy); c.textRot(lab, x0 - 10, yy, 12); }
        c.text("Event rate (" + s.labelC + ")", (x0 + x1) / 2, y1 + 47, 13, 1, false); c.textRot("Event rate (" + s.labelE + ")", 18, (y0 + y1) / 2, 13);
        // legend: top left, or lower right when the estimated-effect line lies above the no-effect line (where the studies are)
        Object[] items = ggLegendItems(s, line, ci, pi); List<String> labs = (List<String>) items[0]; List<Integer> kinds = (List<Integer>) items[1];
        double sample = 40, rowH = 21; double[] box = ggLegendBox(g, labs, line && lineY(s.sm, m.te, lim / 2) > lim / 2, x0, y0, x1, y1); double bx = box[0], by = box[1], bw = box[2], bh = box[3];
        g.setColor(Color.WHITE); g.fill(new Rectangle2D.Double(bx, by, bw, bh)); g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1f)); g.draw(new Rectangle2D.Double(bx, by, bw, bh));
        for (int i = 0; i < labs.size(); i++) { double cy = by + 6 + rowH * (i + 0.5), sx = bx + 12;
            switch (kinds.get(i)) {
                case 0: g.setColor(refC); g.setStroke(new BasicStroke(1.1f)); c.line(sx, cy, sx + sample, cy); break;
                case 1: g.setColor(estC); g.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{4.5f, 3.5f}, 0)); c.line(sx, cy, sx + sample, cy); break;
                case 2: g.setColor(ciC); g.fill(new Rectangle2D.Double(sx + sample / 2 - 10, cy - 8, 20, 16)); break;
                default: g.setColor(piC); g.fill(new Rectangle2D.Double(sx + sample / 2 - 10, cy - 8, 20, 16)); }
            g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1f)); c.text(labs.get(i), sx + sample + 12, cy + 4.5, 12.5f, 0, false); }
        g.setColor(Color.BLACK); return c.img;
    }
}
