package org.metajava.plot;

import org.metajava.core.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/**
 * Radial (Galbraith) plot: themes, colours, study labels and legend.
 * <ul>
 * <li>Default: the current meta look (black frame, black points, regression line through the origin; common-effect estimate);</li>
 * <li>metafor style: detached axes, grey confidence area with dotted limits, grey points with black border, the arc axis for the effect size with the
 *     confidence interval of the pooled estimate; z = y / sqrt(v + tau^2) against x = 1 / sqrt(v + tau^2) with the pooled (random-effects when selected) estimate as slope;</li>
 * <li>ggplot2 style: open axes, dotted grid, grey confidence area, dark-red regression line, teal no-effect line, navy points, title;</li>
 * <li>STATA style: pale-blue frame, navy title, white panel, dashed confidence limits, black regression line, navy points, navy labels.</li>
 * </ul>
 * The "Radial model" option ({@link #MODELS}) applies to every theme: the model of the analysis (random effects when selected) or the common-effect model.
 * All four themes carry a framed legend below the plot (only the elements that are drawn are listed). The seven colour options apply to every theme; "Theme default" keeps
 * what the theme prescribes. Elements a theme does not draw by itself (e.g. the no-effect line or the confidence area in the Default theme) appear when a colour is chosen.
 */
public final class RadialPlot {
    private RadialPlot() {}
    public static final String DEFAULT = "Default", METAFOR = "metafor style", GG = "ggplot2 style", STATA = "STATA style", AUTO = "Theme default", WHITE = "White";
    public static final String[] THEMES = {DEFAULT, METAFOR, GG, STATA};
    /** "Radial model" drop-down: the model of the analysis (random effects when selected, as in metafor) or the common-effect model (as in meta); applies to every theme. */
    public static final String MODEL_ANALYSIS = "As the analysis (metafor)", MODEL_COMMON = "Common effect (meta)";
    public static final String[] MODELS = {MODEL_ANALYSIS, MODEL_COMMON};
    /** True when the radial plot is drawn with the random-effects model (tau^2 in the weights, random-effects slope and interval). */
    static boolean useRandom(MetaSpec s, Plots.Opts o) { return s.random && !MODEL_COMMON.equals(o.radialModel); }
    public static final String[] COLOURS = prepend(RegPlots.COLOURS, AUTO), BACK_COLOURS = prepend(RegPlots.COLOURS, AUTO, WHITE);
    private static String[] prepend(String[] a, String... first) { String[] o = new String[first.length + a.length]; System.arraycopy(first, 0, o, 0, first.length); System.arraycopy(a, 0, o, first.length, a.length); return o; }

    private static final Color NAVY = new Color(26, 71, 111), PALE = new Color(234, 242, 243), TEAL = new Color(47, 132, 132), DARKRED = new Color(165, 40, 50), TEXT = new Color(60, 60, 60);
    private static final float[] DASH = {5f, 4f}, DOT = {1.5f, 2.5f};

    private static Color chosen(String name) { return BaujatPlot.colourOf(name); }
    private static Color pick(String name, Color d) { Color c = chosen(name); return c != null ? c : d; }
    private static boolean light(Color c) { return 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue() > 238; }
    private static String pct(double level) { double v = level * 100; return Math.abs(v - Math.rint(v)) < 1e-9 ? String.valueOf((long) Math.rint(v)) : String.format(Locale.ROOT, "%.1f", v); }
    private static Stroke stroke(float w, float[] dash) { return dash == null ? new BasicStroke(w) : new BasicStroke(w, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, dash, 0); }

    /** Resolved look of one plot. */
    private static final class Look {
        int kind;                       // 0 Default, 1 metafor, 2 ggplot2, 3 STATA
        Color bg, fill, edge, reg, ref, area, ciLine; float[] ciDash;   // ref / area / ciLine == null: not drawn
        boolean pointsFilledOnly;
    }
    private static Look look(Plots.Opts o) {
        Look l = new Look(); String t = o.radialTheme == null ? DEFAULT : o.radialTheme;
        l.kind = METAFOR.equals(t) ? 1 : GG.equals(t) ? 2 : STATA.equals(t) ? 3 : 0;
        Color[] fill = {Color.BLACK, new Color(176, 176, 176), NAVY, NAVY}, edge = {Color.BLACK, Color.BLACK, NAVY, NAVY}, reg = {Color.BLACK, Color.BLACK, DARKRED, Color.BLACK};
        Color[] ref = {null, null, TEAL, null}, area = {null, new Color(229, 229, 229), new Color(190, 190, 190), null}, line = {Color.BLACK, Color.BLACK, null, Color.BLACK};
        float[][] dash = {DASH, DOT, DASH, DASH};
        int k = l.kind; l.bg = pick(o.radialBack, Color.WHITE);
        l.fill = pick(o.radialCircle, fill[k]); l.edge = pick(o.radialSymbol, edge[k]); l.reg = pick(o.radialReg, reg[k]);
        l.ref = pick(o.radialRef, ref[k]); l.area = pick(o.radialCI, area[k]); l.ciLine = pick(o.radialCILine, line[k]); l.ciDash = dash[k];
        return l;
    }

    /** Colour "Theme default" stands for in the given theme (swatch of the drop-down lists; null when the theme does not draw that element); roles: 0 symbol (border), 1 background, 2 study circle,
     *  3 regression line, 4 no-effect line, 5 confidence area, 6 confidence-limit lines. */
    public static Color defaultColour(String theme, int role) {
        Plots.Opts o = new Plots.Opts(); o.radialTheme = theme; Look l = look(o);
        switch (role) { case 0: return l.edge; case 1: return l.bg; case 2: return l.fill; case 3: return l.reg; case 4: return l.ref; case 5: return l.area; default: return l.ciLine; }
    }

    // ------------------------------------------------------------------ small helpers
    private static FontMetrics fm(float size) { return Canvas.probe().g.getFontMetrics(Canvas.font(size)); }

    /** Text with subscripts (_{...}) and superscripts (^{...}); returns the width. */
    private static double rich(Canvas c, String s, double x, double y, float size, int align, boolean draw) {
        List<Object[]> parts = new ArrayList<>(); StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) { char ch = s.charAt(i);
            if ((ch == '_' || ch == '^') && i + 1 < s.length() && s.charAt(i + 1) == '{') { int e = s.indexOf('}', i); if (cur.length() > 0) { parts.add(new Object[]{cur.toString(), 0}); cur.setLength(0); }
                parts.add(new Object[]{s.substring(i + 2, e), ch == '_' ? 1 : 2}); i = e; } else cur.append(ch); }
        if (cur.length() > 0) parts.add(new Object[]{cur.toString(), 0});
        double w = 0; for (Object[] p : parts) w += fm((int) p[1] == 0 ? size : size * 0.7f).stringWidth((String) p[0]);
        if (!draw) return w; double xx = align == 0 ? x : align == 1 ? x - w / 2 : x - w;
        for (Object[] p : parts) { int m = (int) p[1]; float sz = m == 0 ? size : size * 0.7f; c.text((String) p[0], xx, y + (m == 1 ? size * 0.28 : m == 2 ? -size * 0.4 : 0), sz, 0, false); xx += fm(sz).stringWidth((String) p[0]); }
        return w;
    }

    private static final class Leg { int kind; Color fill, edge; float[] dash; String text; Leg(int k, Color f, Color e, float[] d, String t) { kind = k; fill = f; edge = e; dash = d; text = t; } }   // kind: 0 line, 1 area, 2 point
    private static int[] legendSize(List<Leg> items) {
        FontMetrics f = fm(12f); int colW = 0; for (Leg it : items) colW = Math.max(colW, 44 + f.stringWidth(it.text));
        int cols = items.size() > 1 ? 2 : 1, rows = (items.size() + cols - 1) / cols; return new int[]{cols * colW + 30, rows * 22 + 14, colW};
    }
    /** Framed legend, items filled row by row in two columns, horizontally centred on cx. */
    private static void legend(Canvas c, List<Leg> items, double cx, double top) {
        int[] sz = legendSize(items); Graphics2D g = c.g; double lx = cx - sz[0] / 2.0; int cols = items.size() > 1 ? 2 : 1;
        g.setColor(Color.WHITE); g.fill(new Rectangle2D.Double(lx, top, sz[0], sz[1])); g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1f)); g.draw(new Rectangle2D.Double(lx, top, sz[0], sz[1]));
        for (int k = 0; k < items.size(); k++) { Leg it = items.get(k); double x = lx + 14 + (k % cols) * (sz[2] + 2), y = top + 14 + (k / cols) * 22 + 3;
            switch (it.kind) {
                case 0: g.setColor(it.fill); g.setStroke(stroke(1.6f, it.dash)); c.line(x, y, x + 30, y); break;
                case 1: g.setColor(it.fill); g.fill(new Rectangle2D.Double(x + 3, y - 7, 24, 14)); break;
                default: g.setColor(it.fill); g.fill(new Ellipse2D.Double(x + 10.5, y - 4.5, 9, 9)); g.setColor(it.edge); g.setStroke(new BasicStroke(1f)); g.draw(new Ellipse2D.Double(x + 10.5, y - 4.5, 9, 9)); }
            g.setStroke(new BasicStroke(1f)); g.setColor(Color.BLACK); c.text(it.text, x + 38, y + 4, 12f, 0, false); }
    }

    /** Study labels: right of the point, else left, above or below; the first position that stays inside the bounds and clear of other labels and points. */
    private static void labels(Canvas c, List<String> labs, double[] px, double[] py, double r, Rectangle2D bounds, Color col, float size, boolean repel) {
        if (repel) { LabelPlacer lp = new LabelPlacer(bounds.getMinX(), bounds.getMinY(), bounds.getMaxX(), bounds.getMaxY(), 8.5f); for (int i = 0; i < labs.size(); i++) lp.add(labs.get(i), px[i], py[i], r); lp.draw(c); return; }
        FontMetrics f = fm(size); List<Rectangle2D> used = new ArrayList<>(); Graphics2D g = c.g; g.setColor(col); g.setFont(Canvas.font(size));
        for (int i = 0; i < labs.size(); i++) { double w = f.stringWidth(labs.get(i)), h = size + 1, best[] = null;
            double[][] cand = {{px[i] + r + 3, py[i] - 3}, {px[i] - r - 3 - w, py[i] - 3}, {px[i] - w / 2, py[i] - r - 5}, {px[i] - w / 2, py[i] + r + h}, {px[i] + r + 3, py[i] + h}, {px[i] - r - 3 - w, py[i] + h}};
            for (double[] cd : cand) { Rectangle2D b = new Rectangle2D.Double(cd[0], cd[1] - h + 2, w, h); boolean ok = bounds.contains(b);
                for (Rectangle2D u : used) if (ok && u.intersects(b)) ok = false;
                for (int j = 0; j < px.length && ok; j++) if (b.intersects(px[j] - r, py[j] - r, 2 * r, 2 * r)) ok = false;
                if (ok) { best = cd; break; } }
            if (best == null) best = cand[0]; used.add(new Rectangle2D.Double(best[0], best[1] - h + 2, w, h)); g.drawString(labs.get(i), (float) best[0], (float) best[1]); }
    }

    // ------------------------------------------------------------------ plot
    public static BufferedImage draw(MetaResult r, Plots.Opts o) {
        MetaSpec s = r.spec; Pool p = r.overall; Look L = look(o); int n = r.TE.length; boolean met = L.kind == 1, rnd = useRandom(s, o) && !Double.isNaN(p.tau2) && !Double.isNaN(p.teRandom);
        double tau2 = rnd ? p.tau2 : 0;
        double b = rnd ? p.teRandom : p.teCommon; if (Double.isNaN(b)) b = Double.isNaN(p.teRandom) ? 0 : p.teRandom;
        List<double[]> pts = new ArrayList<>(); List<String> labs = new ArrayList<>(); double elo = Double.POSITIVE_INFINITY, ehi = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) if (!Double.isNaN(r.TE[i]) && r.seTE[i] > 0) { double sd = Math.sqrt(r.seTE[i] * r.seTE[i] + tau2); pts.add(new double[]{1 / sd, r.TE[i] / sd}); labs.add(r.studlab[i]); elo = Math.min(elo, r.TE[i]); ehi = Math.max(ehi, r.TE[i]); }
        if (pts.isEmpty()) { Canvas c = new Canvas(520, 200, o.scale); c.text("Radial plot not available (no study with a valid estimate and standard error)", 260, 100, 12, 1, false); return c.img; }
        double xm = 0, ym = Double.NEGATIVE_INFINITY, yn = Double.POSITIVE_INFINITY; for (double[] q : pts) { xm = Math.max(xm, q[0]); ym = Math.max(ym, q[1]); yn = Math.min(yn, q[1]); }
        double z = Dist.qnorm(1 - (1 - s.levelMa) / 2);
        boolean showArea = L.area != null, showLine = L.ciLine != null, showRef = L.ref != null;
        // ---- legend items
        List<Leg> leg = new ArrayList<>();
        if (showArea) leg.add(new Leg(1, L.area, null, null, pct(s.levelMa) + "% CI"));
        if (showLine) leg.add(new Leg(0, L.ciLine, null, L.ciDash, pct(s.levelMa) + "% CI limits"));
        leg.add(new Leg(2, L.fill, L.edge, null, "Studies")); leg.add(new Leg(0, L.reg, null, null, "Regression line")); if (showRef) leg.add(new Leg(0, L.ref, null, null, "No effect"));
        int[] ls = legendSize(leg);
        switch (L.kind) { case 1: return metafor(r, o, L, pts, labs, b, tau2, rnd, xm, ym, yn, z, elo, ehi, leg, ls, showArea, showLine, showRef);
            default: return standard(r, o, L, pts, labs, b, tau2, xm, ym, yn, z, leg, ls, showArea, showLine, showRef); }
    }

    /** Default, ggplot2 and STATA themes. */
    private static BufferedImage standard(MetaResult r, Plots.Opts o, Look L, List<double[]> pts, List<String> labs, double b, double tau2, double xm, double ym, double yn, double z,
                                          List<Leg> leg, int[] ls, boolean showArea, boolean showLine, boolean showRef) {
        MetaSpec s = r.spec; int k = L.kind; boolean def = k == 0, rnd = tau2 > 0; String eff = (s.isRatioSm() ? "ln" : "") + s.sm;
        double xh = def ? xm * 1.05 : Canvas.pretty(0, xm, 5)[Canvas.pretty(0, xm, 5).length - 1], xl = k == 3 ? -0.04 * xh : 0;
        if (def) { xh = xm * 1.05; }
        double lo = Math.min(yn, 0), hi = Math.max(Math.max(ym, b * xh), 0);
        if (showArea || showLine || !def) { lo = Math.min(lo, Math.min(-z, b * xh - z)); hi = Math.max(hi, Math.max(z, b * xh + z)); }
        double yl, yh; if (def) { yl = lo - 1; yh = hi + 1; } else { double[] t = Canvas.pretty(lo, hi, 5); yl = t[0]; yh = t[t.length - 1]; }
        int W = def ? 560 : 680, legGap = def ? 52 : 62, panelH = def ? 410 : 340;
        double top = def ? 40 : 62, left = def ? 70 : 84, right = def ? 30 : 24;
        double y0 = top, y1 = top + (def ? 520 - 40 - 60 : panelH), legTop = y1 + legGap; int H = (int) Math.ceil(legTop + ls[1] + 20);
        if (def) W = Math.max(W, ls[0] + 60);
        Canvas c = new Canvas(W, H, o.scale); Graphics2D g = c.g; double x0 = left, x1 = W - right;
        Plots.Axes a = new Plots.Axes(); a.c = c; a.x0 = x0; a.x1 = x1; a.y0 = y0; a.y1 = y1; a.xl = xl; a.xh = xh; a.yl = yl; a.yh = yh; a.yRev = false;
        Rectangle2D.Double panel = new Rectangle2D.Double(x0, y0, x1 - x0, y1 - y0);
        if (k == 3) { g.setColor(PALE); g.fill(new Rectangle2D.Double(0, 0, W, H)); g.setColor(NAVY); c.text("Galbraith (radial) Plot", (x0 + x1) / 2, 36, 18f, 1, false); }
        if (k == 2) { g.setColor(NAVY); c.text("Galbraith plot", (x0 + x1) / 2, 34, 18f, 1, false); }
        g.setColor(L.bg); g.fill(panel);
        double[] xt = Canvas.pretty(xl, xh, 5), yt = Canvas.pretty(yl, yh, k == 0 ? 6 : 5);
        Shape clip0 = g.getClip();
        if (!def) { g.setClip(panel);
            if (k == 3) { g.setColor(light(L.bg) ? new Color(224, 235, 238) : Color.WHITE); g.setStroke(new BasicStroke(1.5f)); for (double t : yt) if (t >= yl - 1e-9 && t <= yh + 1e-9) c.line(x0, a.Y(t), x1, a.Y(t)); }
            else { g.setColor(light(L.bg) ? new Color(205, 205, 205) : Color.WHITE); g.setStroke(stroke(0.8f, new float[]{1.5f, 3f})); for (double t : xt) if (t >= xl - 1e-9 && t <= xh + 1e-9) c.line(a.X(t), y0, a.X(t), y1); for (double t : yt) if (t >= yl - 1e-9 && t <= yh + 1e-9) c.line(x0, a.Y(t), x1, a.Y(t)); }
            g.setClip(clip0); }
        // ---- content, clipped to the panel
        g.setClip(panel);
        if (showArea) { Path2D q = new Path2D.Double(); q.moveTo(a.X(0), a.Y(z)); q.lineTo(a.X(xh), a.Y(b * xh + z)); q.lineTo(a.X(xh), a.Y(b * xh - z)); q.lineTo(a.X(0), a.Y(-z)); q.closePath(); g.setColor(L.area); g.fill(q); }
        if (showRef) { g.setColor(L.ref); g.setStroke(new BasicStroke(1.2f)); c.line(a.X(0), a.Y(0), a.X(xh), a.Y(0)); }
        if (showLine) { g.setColor(L.ciLine); g.setStroke(stroke(1.2f, L.ciDash)); c.line(a.X(0), a.Y(z), a.X(xh), a.Y(b * xh + z)); c.line(a.X(0), a.Y(-z), a.X(xh), a.Y(b * xh - z)); }
        g.setColor(L.reg); g.setStroke(new BasicStroke(k == 0 ? 1f : 1.4f)); c.line(a.X(0), a.Y(0), a.X(xh), a.Y(b * xh));
        g.setStroke(new BasicStroke(1f)); g.setClip(clip0);
        double rad = k == 0 ? 3 : 3.8; double[] px = new double[pts.size()], py = new double[pts.size()];
        for (int i = 0; i < pts.size(); i++) { px[i] = a.X(pts.get(i)[0]); py[i] = a.Y(pts.get(i)[1]);
            g.setColor(L.fill); g.fill(new Ellipse2D.Double(px[i] - rad, py[i] - rad, 2 * rad, 2 * rad)); g.setColor(L.edge); g.setStroke(new BasicStroke(1f)); g.draw(new Ellipse2D.Double(px[i] - rad, py[i] - rad, 2 * rad, 2 * rad)); }
        if (o.radialLabels) labels(c, labs, px, py, rad, panel, k == 3 ? NAVY : k == 0 ? Color.BLACK : TEXT, k == 0 ? 8f : 10f, o.repelRadial);
        // ---- axes
        if (def) { g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1f)); g.draw(panel);
            for (double t : xt) { if (t < xl - 1e-9 || t > xh + 1e-9) continue; double xx = a.X(t); c.line(xx, y1, xx, y1 + 5); c.text(Canvas.tick(t), xx, y1 + 18, 10, 1, false); }
            for (double t : yt) { if (t < yl - 1e-9 || t > yh + 1e-9) continue; double yy = a.Y(t); c.line(x0 - 5, yy, x0, yy); c.textRot(Canvas.tick(t), x0 - 10, yy, 10); }
            c.text(rnd ? "Inverse of \u221A(SE\u00B2 + \u03C4\u00B2)" : "Inverse of standard error", (x0 + x1) / 2, y1 + 40, 11, 1, false); c.textRot(rnd ? "Standardised treatment estimate (random effects)" : "Standardised treatment estimate", 16, (y0 + y1) / 2, 11); }
        else { g.setColor(Color.BLACK); g.setStroke(new BasicStroke(k == 3 ? 1.2f : 1f)); c.line(x0, y0, x0, y1); c.line(x0, y1, x1, y1);
            float tf = k == 3 ? 13f : 12f;
            for (double t : xt) { if (t < xl - 1e-9 || t > xh + 1e-9) continue; double xx = a.X(t); g.setColor(Color.BLACK); c.line(xx, y1, xx, y1 + 5); g.setColor(k == 3 ? Color.BLACK : TEXT); c.text(Canvas.tick(t), xx, y1 + 21, tf, 1, false); }
            for (double t : yt) { if (t < yl - 1e-9 || t > yh + 1e-9) continue; double yy = a.Y(t); g.setColor(Color.BLACK); c.line(x0 - 5, yy, x0, yy); g.setColor(k == 3 ? Color.BLACK : TEXT);
                if (k == 3) c.textRot(Canvas.tick(t), x0 - 12, yy, tf); else c.text(Canvas.tick(t), x0 - 9, yy + 4.5, tf, 2, false); }
            g.setColor(k == 3 ? Color.BLACK : TEXT);
            String den = rnd ? "\u221A(SE\u00B2 + \u03C4\u00B2)" : "SE";
            c.text(k == 3 ? "1 / " + den : "Precision (1 / " + den + ")", (x0 + x1) / 2, y1 + 46, 13f, 1, false); c.textRot(k == 3 ? eff + " / " + den : "Standardised effect (" + eff + " / " + den + ")", 20, (y0 + y1) / 2, 13f); }
        g.setColor(Color.BLACK); legend(c, leg, (x0 + x1) / 2, legTop); return c.img;
    }

    /** metafor style: detached axes, shaded band with dotted limits, arc axis for the effect size. */
    private static BufferedImage metafor(MetaResult r, Plots.Opts o, Look L, List<double[]> pts, List<String> labs, double b, double tau2, boolean rnd, double xm, double ym, double yn, double z,
                                         double elo, double ehi, List<Leg> leg, int[] ls, boolean showArea, boolean showLine, boolean showRef) {
        MetaSpec s = r.spec; Pool p = r.overall; boolean logAx = s.backtransf && s.isRatioSm();
        double ciLo = rnd ? p.loRandom : p.loCommon, ciUp = rnd ? p.upRandom : p.upCommon;
        double xend = xm * 1.25; if (!(xend > 0)) xend = 1;
        double lo = Math.min(yn, Math.min(0, b * xend)) - z, hi = Math.max(ym, Math.max(0, b * xend)) + z; if (hi < z + 0.5) hi = z + 0.5; if (lo > -z - 0.5) lo = -z - 0.5;
        double[] yt = Canvas.pretty(lo, hi, 6); double yl = yt[0], yh = yt[yt.length - 1];
        // arc axis: circle of radius R around the origin (data units); effect e sits at the point (R / sqrt(1 + e^2)) * (1, e)
        double ae0 = Math.min(elo, b), ae1 = Math.max(ehi, b); if (!Double.isNaN(ciLo)) ae0 = Math.min(ae0, ciLo); if (!Double.isNaN(ciUp)) ae1 = Math.max(ae1, ciUp);
        double span = Math.max(ae1 - ae0, 1e-6), padE = span * 0.06; ae0 -= padE; ae1 += padE;
        // the arc is a circle in pixel space around the origin (as in metafor); its radius is chosen so that even its most inclined end stays to the right of the band
        double pw = 420, ph = 300, ppu = ph / (yh - yl), ppx = pw / xend, kk = ppu / ppx, emax = Math.max(Math.abs(ae0), Math.abs(ae1));
        double Rpx = pw * Math.sqrt(1 + emax * emax * kk * kk) * 1.04 + 8, phiMax = Math.atan(ae1 * kk), phiMin = Math.atan(ae0 * kk);
        double x0 = 150, padTop = Math.max(0, Rpx * Math.sin(Math.max(phiMax, phiMin)) - yh * ppu + 16), padBot = Math.max(0, Rpx * Math.sin(-Math.min(phiMax, phiMin)) + yl * ppu + 16);
        double y0 = 28 + padTop, y1 = y0 + ph, x1 = x0 + pw;
        double legTop = Math.max(y1 + 112, y1 + padBot + 30); int W = (int) Math.ceil(Math.max(x0 + Rpx + 70, x0 + pw + 40)); W = Math.max(W, ls[0] + 40);
        int H = (int) Math.ceil(legTop + ls[1] + 20);
        Canvas c = new Canvas(W, H, o.scale); Graphics2D g = c.g;
        final double fyl = yl, fy1 = y1, fx0 = x0;
        java.util.function.DoubleUnaryOperator X = v -> fx0 + v * ppx, Y = v -> fy1 - (v - fyl) * ppu;
        // panel background (the area the band lives in) and band
        Rectangle2D.Double panel = new Rectangle2D.Double(x0, y0, pw, ph);
        if (!L.bg.equals(Color.WHITE)) { g.setColor(L.bg); g.fill(new Rectangle2D.Double(x0, y0, W - x0 - 4, ph)); }
        if (showArea) { Path2D q = new Path2D.Double(); q.moveTo(X.applyAsDouble(0), Y.applyAsDouble(z)); q.lineTo(X.applyAsDouble(xend), Y.applyAsDouble(b * xend + z)); q.lineTo(X.applyAsDouble(xend), Y.applyAsDouble(b * xend - z)); q.lineTo(X.applyAsDouble(0), Y.applyAsDouble(-z)); q.closePath(); g.setColor(L.area); g.fill(q); }
        if (showRef) { g.setColor(L.ref); g.setStroke(new BasicStroke(1.1f)); c.line(X.applyAsDouble(0), Y.applyAsDouble(0), X.applyAsDouble(xend), Y.applyAsDouble(0)); }
        if (showLine) { g.setColor(L.ciLine); g.setStroke(stroke(1.1f, L.ciDash)); c.line(X.applyAsDouble(0), Y.applyAsDouble(z), X.applyAsDouble(xend), Y.applyAsDouble(b * xend + z)); c.line(X.applyAsDouble(0), Y.applyAsDouble(-z), X.applyAsDouble(xend), Y.applyAsDouble(b * xend - z)); }
        g.setColor(L.reg); g.setStroke(new BasicStroke(1.2f)); c.line(X.applyAsDouble(0), Y.applyAsDouble(0), X.applyAsDouble(xend), Y.applyAsDouble(b * xend));
        double rad = 4.2; double[] px = new double[pts.size()], py = new double[pts.size()];
        for (int i = 0; i < pts.size(); i++) { px[i] = X.applyAsDouble(pts.get(i)[0]); py[i] = Y.applyAsDouble(pts.get(i)[1]);
            g.setColor(L.fill); g.fill(new Ellipse2D.Double(px[i] - rad, py[i] - rad, 2 * rad, 2 * rad)); g.setColor(L.edge); g.setStroke(new BasicStroke(1f)); g.draw(new Ellipse2D.Double(px[i] - rad, py[i] - rad, 2 * rad, 2 * rad)); }
        if (o.radialLabels) labels(c, labs, px, py, rad, new Rectangle2D.Double(x0, y0, x1 - x0, y1 - y0), TEXT, 10f, o.repelRadial);
        // left axis (detached tick line with minor ticks), bottom axis (detached)
        g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1f)); c.line(x0, Y.applyAsDouble(yl), x0, Y.applyAsDouble(yh));
        for (int i = 0; i < yt.length; i++) { double yy = Y.applyAsDouble(yt[i]); c.line(x0 - 6, yy, x0, yy); c.text(Canvas.tick(yt[i]), x0 - 10, yy + 4.5, 12f, 2, false);
            if (i + 1 < yt.length) c.line(x0 - 3, Y.applyAsDouble((yt[i] + yt[i + 1]) / 2), x0, Y.applyAsDouble((yt[i] + yt[i + 1]) / 2)); }
        double ax = y1 + 30; double[] xt = Canvas.pretty(0, xend, 5); double last = 0; for (double t : xt) if (t <= xend + 1e-9 && t >= -1e-9) last = t;
        c.line(X.applyAsDouble(0), ax, X.applyAsDouble(last), ax);
        for (double t : xt) { if (t > xend + 1e-9 || t < -1e-9) continue; double xx = X.applyAsDouble(t); c.line(xx, ax, xx, ax + 6); c.text(Canvas.tick(t), xx, ax + 21, 12f, 1, false); }
        // axis titles with the formulas
        rich(c, "x_{i} = 1/√(v_{i} + τ^{2})", (x0 + x1) / 2, ax + 46, 13f, 1, true);
        double my = (y0 + y1) / 2; rich(c, "z_{i} =", 18, my + 4, 13f, 0, true); double eqW = rich(c, "z_{i} =", 0, 0, 13f, 0, false), fx = 18 + eqW + 8;
        double nw = rich(c, "y_{i}", 0, 0, 13f, 0, false), dw = rich(c, "√(v_{i} + τ^{2})", 0, 0, 13f, 0, false), fw = Math.max(nw, dw);
        rich(c, "y_{i}", fx + (fw - nw) / 2, my - 4, 13f, 0, true); g.setStroke(new BasicStroke(1f)); c.line(fx, my, fx + fw, my); rich(c, "√(v_{i} + τ^{2})", fx + (fw - dw) / 2, my + 15, 13f, 0, true);
        // arc axis: circle of radius Rpx around the origin; the effect e sits at the angle atan(e * ppu / ppx)
        g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1f)); double ox = X.applyAsDouble(0), oy = Y.applyAsDouble(0); Path2D arc = new Path2D.Double();
        for (int i = 0; i <= 120; i++) { double ph2 = Math.atan((ae0 + (ae1 - ae0) * i / 120) * kk), xx = ox + Rpx * Math.cos(ph2), yy = oy - Rpx * Math.sin(ph2); if (i == 0) arc.moveTo(xx, yy); else arc.lineTo(xx, yy); } g.draw(arc);
        for (double tk : arcTicks(ae0, ae1, logAx)) { double e = logAx ? Math.log(tk) : tk, ph2 = Math.atan(e * kk), cs = Math.cos(ph2), sn = -Math.sin(ph2), xx = ox + Rpx * cs, yy = oy + Rpx * sn;
            c.line(xx, yy, xx + 6 * cs, yy + 6 * sn); c.text(Canvas.tick(tk), xx + 9 * cs + 1, yy + 9 * sn + 4, 11f, 0, false); }
        if (!Double.isNaN(ciLo) && !Double.isNaN(ciUp) && !Double.isNaN(b)) {   // confidence interval of the pooled estimate: bracket just inside the arc, with a tick at the estimate
            double rr = Rpx - 7; double[][] pp = new double[3][]; double[] es = {ciLo, ciUp, b};
            for (int q = 0; q < 3; q++) { double ph2 = Math.atan(es[q] * kk); pp[q] = new double[]{ox + rr * Math.cos(ph2), oy - rr * Math.sin(ph2), Math.cos(ph2), -Math.sin(ph2)}; }
            c.line(pp[0][0], pp[0][1], pp[1][0], pp[1][1]);
            for (int q = 0; q < 3; q++) { double nx = pp[q][2] * (q == 2 ? 3 : 4), ny = pp[q][3] * (q == 2 ? 3 : 4); c.line(pp[q][0] - nx, pp[q][1] - ny, pp[q][0] + nx, pp[q][1] + ny); } }
        g.setColor(Color.BLACK); legend(c, leg, (x0 + x1) / 2, legTop); return c.img;
    }

    /** Ticks of the arc axis: values on the displayed scale (back-transformed for ratio measures), at least two inside the range. */
    private static double[] arcTicks(double e0, double e1, boolean logAx) {
        List<Double> out = new ArrayList<>();
        if (logAx) { for (double t : Plots.logTicks(Math.exp(e0), Math.exp(e1))) { double v = Math.log(t); if (v >= e0 && v <= e1) out.add(t); } if (out.size() < 3) { out.clear(); for (double t : Canvas.pretty(Math.exp(e0), Math.exp(e1), 6)) if (Math.log(Math.max(t, 1e-300)) >= e0 && Math.log(Math.max(t, 1e-300)) <= e1) out.add(t); } }
        else for (double t : Canvas.pretty(e0, e1, 6)) if (t >= e0 - 1e-9 && t <= e1 + 1e-9) out.add(t);
        double[] r = new double[out.size()]; for (int i = 0; i < r.length; i++) r[i] = out.get(i); return r;
    }
}
