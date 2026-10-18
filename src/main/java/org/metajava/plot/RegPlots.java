package org.metajava.plot;

import org.metajava.core.*;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.function.DoubleFunction;

/** Regression plots of a meta-regression: curves with confidence bounds, the ggplot2-style variant and the plot adjusted for all moderators of the model. */
public final class RegPlots {
    private RegPlots() {}

    public static final String DEFAULT_STYLE = "Default (meta style)", GG = "ggplot2 style";
    public static final String[] STYLES = {DEFAULT_STYLE, GG};
    /** Named colours offered for the study circles, the regression line and the confidence band / lines. */
    public static final String[] COLOURS = {"Blue", "Navy", "Steel blue", "Light blue", "Red", "Dark red", "Light red", "Green", "Dark green", "Light green", "Orange", "Light orange", "Purple", "Light purple", "Black", "Dark grey", "Grey", "Light grey"};
    private static final Color[] RGB = {new Color(0, 0, 255), new Color(31, 78, 140), new Color(70, 130, 180), new Color(173, 200, 235), new Color(255, 0, 0), new Color(139, 0, 0), new Color(245, 170, 170), new Color(34, 160, 80), new Color(0, 100, 0), new Color(190, 235, 200), new Color(255, 140, 0), new Color(255, 215, 170), new Color(128, 0, 128), new Color(215, 190, 235), Color.BLACK, new Color(70, 70, 70), new Color(150, 150, 150), new Color(211, 211, 211)};
    public static final String POINTS_DEFAULT = "Blue", LINE_DEFAULT = "Red", BAND_DEFAULT = "Light grey", BANDLINE_DEFAULT = "Dark grey", PRED_DEFAULT = "Light grey", PREDLINE_DEFAULT = "Grey";
    /** Dotted stroke of the prediction bounds. */
    static final BasicStroke DOTTED = new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10, new float[]{0.1f, 3.4f}, 0);

    /** Study symbols. */
    public static final String SYM_DEFAULT = "Circle";
    public static final String[] SYMBOLS = {"Circle", "Triangle", "Square", "Star", "Diamond", "Open circle", "Open triangle", "Open square", "Open diamond"};
    static boolean isOpen(String sym) { return sym != null && sym.startsWith("Open "); }
    /** Outline of a study symbol centred at (x, y); rad = radius of the circle of the same visual weight. */
    static Shape symbolShape(String sym, double x, double y, double rad) {
        String k = sym == null ? "circle" : sym.toLowerCase(Locale.ROOT).replace("open ", "");
        switch (k) {
            case "triangle": { double R = 1.3 * rad; Path2D t = new Path2D.Double(); t.moveTo(x, y - R); t.lineTo(x + R * 0.866, y + R * 0.5); t.lineTo(x - R * 0.866, y + R * 0.5); t.closePath(); return t; }
            case "square": { double h = 0.89 * rad; return new Rectangle2D.Double(x - h, y - h, 2 * h, 2 * h); }
            case "diamond": { double R = 1.25 * rad; Path2D t = new Path2D.Double(); t.moveTo(x, y - R); t.lineTo(x + R, y); t.lineTo(x, y + R); t.lineTo(x - R, y); t.closePath(); return t; }
            case "star": { double R = 1.35 * rad, ri = R * 0.4; Path2D t = new Path2D.Double(); for (int i = 0; i < 10; i++) { double ang = -Math.PI / 2 + i * Math.PI / 5, rr = i % 2 == 0 ? R : ri, px = x + rr * Math.cos(ang), py = y + rr * Math.sin(ang); if (i == 0) t.moveTo(px, py); else t.lineTo(px, py); } t.closePath(); return t; }
            default: return new Ellipse2D.Double(x - rad, y - rad, 2 * rad, 2 * rad);
        }
    }
    /** Draws a study symbol: filled symbols with the given colour / alpha and an outline; open symbols as an outline in the fill colour (or in the outline colour when the fill is not a chosen colour). */
    static void drawSymbol(Graphics2D g, String sym, double x, double y, double rad, Color fill, int fillAlpha, Color edge, float edgeW) {
        Shape sh = symbolShape(sym, x, y, rad); Stroke old = g.getStroke();
        if (isOpen(sym)) { g.setColor(edge); g.setStroke(new BasicStroke(Math.max(1.3f, edgeW * 1.6f))); g.draw(sh); }
        else { g.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), fillAlpha)); g.fill(sh); g.setColor(edge); g.setStroke(new BasicStroke(edgeW)); g.draw(sh); }
        g.setStroke(old);
    }
    /** Rows of the curve array that determine the vertical extent of the plot: the fit and, when shown, the confidence and prediction bounds. */
    static int[] rowsShown(Plots.Opts o) { List<Integer> l = new ArrayList<>(); l.add(1); if (o.confInt) { l.add(2); l.add(3); } if (o.predInt) { l.add(4); l.add(5); } return l.stream().mapToInt(Integer::intValue).toArray(); }

    public static Color rgb(String name) { return colour(name, POINTS_DEFAULT); }
    static Color colour(String name, String dflt) {
        for (int i = 0; i < COLOURS.length; i++) if (COLOURS[i].equals(name)) return RGB[i];
        for (int i = 0; i < COLOURS.length; i++) if (COLOURS[i].equals(dflt)) return RGB[i];
        return Color.GRAY;
    }
    static Color alpha(Color c, double a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) Math.round(255 * a)); }

    /** Fitted values and confidence bounds along x: {x, fit, lower, upper, prediction lower, prediction upper}; rowAt gives the model-matrix row for a value of x. */
    static double[][] curve(Reg g, MetaSpec s, double lo, double hi, DoubleFunction<double[]> rowAt) {
        int n = 80; double[][] o = new double[6][n];
        for (int i = 0; i < n; i++) { double x = lo + (hi - lo) * i / (n - 1); double[] p = g.predict(rowAt.apply(x), s.levelMa); o[0][i] = x; o[1][i] = p[0]; o[2][i] = p[1]; o[3][i] = p[2]; double[] pi = g.predictInterval(rowAt.apply(x), s.levelPredict); o[4][i] = pi[0]; o[5][i] = pi[1]; }
        return o;
    }
    static double tr(double v, boolean log) { return log ? Math.exp(v) : v; }

    /** ggplot2 hue palette (hcl, c = 100, l = 65, hues equally spaced from 15 degrees). */
    public static Color[] huePalette(int n) {
        Color[] out = new Color[n];
        for (int i = 0; i < n; i++) out[i] = hcl(15 + 360.0 * i / n, 100, 65);
        return out;
    }
    static Color hcl(double h, double c, double l) {
        double hr = Math.toRadians(h), u = c * Math.cos(hr), v = c * Math.sin(hr);
        double un = 0.19783, vn = 0.46832, up = u / (13 * l) + un, vp = v / (13 * l) + vn;
        double Y = l > 8 ? Math.pow((l + 16) / 116, 3) : l / 903.3, X = Y * 9 * up / (4 * vp), Z = Y * (12 - 3 * up - 20 * vp) / (4 * vp);
        double[] rgb = {3.2404542 * X - 1.5371385 * Y - 0.4985314 * Z, -0.9692660 * X + 1.8760108 * Y + 0.0415560 * Z, 0.0556434 * X - 0.2040259 * Y + 1.0572252 * Z};
        int[] k = new int[3]; for (int i = 0; i < 3; i++) { double a = Math.max(0, Math.min(1, rgb[i])); a = a <= 0.0031308 ? 12.92 * a : 1.055 * Math.pow(a, 1 / 2.4) - 0.055; k[i] = (int) Math.round(255 * Math.max(0, Math.min(1, a))); }
        return new Color(k[0], k[1], k[2]);
    }

    // ------------------------------------------------------------------ legend (regression line / confidence interval / prediction interval)
    /** One legend entry: a line sample (regression line) or a filled swatch with the dashed / dotted bound style (interval band). */
    static final class LegItem {
        final boolean line; final Color fill, edge; final Stroke stroke; final String text;
        LegItem(boolean line, Color fill, Color edge, Stroke stroke, String text) { this.line = line; this.fill = fill; this.edge = edge; this.stroke = stroke; this.text = text; }
    }
    static final BasicStroke CI_DASH = new BasicStroke(1.3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{5f, 4f}, 0);
    private static String pct(double level) { return Fmt.num(level * 100, 0) + "%"; }
    /** Legend entries of a regression plot; empty unless the confidence and / or the prediction interval is shown. menuColours: the plot is drawn with the colours chosen in the Meta-regression menu
     *  (ggplot2 style, adjusted plot); otherwise with the fixed colours of the default style. The order is the one of the plot: regression line, confidence interval, prediction interval. */
    static List<LegItem> legendItems(Plots.Opts o, MetaSpec s, boolean menuColours) {
        List<LegItem> l = new ArrayList<>(); if (!o.confInt && !o.predInt) return l;
        if (o.regLine) { Color c = menuColours ? colour(o.colLine, LINE_DEFAULT) : Color.BLACK; l.add(new LegItem(true, c, c, new BasicStroke(menuColours ? 2.3f : 1.4f), "Regression line")); }
        if (o.confInt) l.add(new LegItem(false, menuColours ? alpha(colour(o.colBand, BAND_DEFAULT), 0.75) : new Color(215, 215, 215, 170), menuColours ? colour(o.colBandLine, BANDLINE_DEFAULT) : new Color(70, 70, 70), CI_DASH, pct(s.levelMa) + " Confidence Interval"));
        if (o.predInt) l.add(new LegItem(false, menuColours ? alpha(colour(o.colPred, PRED_DEFAULT), 0.5) : new Color(235, 235, 235, 150), menuColours ? colour(o.colPredLine, PREDLINE_DEFAULT) : new Color(70, 70, 70), DOTTED, pct(s.levelPredict) + " Prediction Interval"));
        return l;
    }
    /** Height of the legend box. */
    static int legendBoxH(List<LegItem> items) { return items.isEmpty() ? 0 : 12 + 20 * items.size(); }
    /** Extra height a plot needs below its x-axis label for the legend (box + gap above and below). */
    static int legendExtra(List<LegItem> items) { return items.isEmpty() ? 0 : legendBoxH(items) + 10; }
    /** Draws the framed legend centred at cx with its top edge at y. */
    static void drawLegend(Canvas c, double cx, double y, List<LegItem> items) {
        if (items.isEmpty()) return; Graphics2D g = c.g; FontMetrics fm = g.getFontMetrics(Canvas.font(12f));
        int tw = 0; for (LegItem it : items) tw = Math.max(tw, fm.stringWidth(it.text));
        double sw = 30, bw = 12 + sw + 12 + tw + 14, bh = legendBoxH(items), x0 = cx - bw / 2;
        Stroke old = g.getStroke();
        g.setColor(Color.WHITE); g.fill(new Rectangle2D.Double(x0, y, bw, bh)); g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1f)); g.draw(new Rectangle2D.Double(x0, y, bw, bh));
        for (int i = 0; i < items.size(); i++) {
            LegItem it = items.get(i); double my = y + 6 + 20 * i + 10, sx = x0 + 12;
            if (it.line) { g.setColor(it.edge); g.setStroke(it.stroke); c.line(sx, my, sx + sw, my); }
            else { Rectangle2D.Double r = new Rectangle2D.Double(sx, my - 6, sw, 12); g.setColor(Color.WHITE); g.fill(r); g.setColor(it.fill); g.fill(r);
                g.setColor(it.edge); g.setStroke(it.stroke); c.line(sx, my - 6, sx + sw, my - 6); c.line(sx, my + 6, sx + sw, my + 6); }
            g.setStroke(new BasicStroke(1f)); g.setColor(Color.BLACK); c.text(it.text, sx + sw + 12, my + 4, 12f, 0, false);
        }
        g.setStroke(old);
    }

    static String effectLabel(MetaSpec s) {
        String m = Plots.xlabFor(s).toLowerCase(Locale.ROOT);
        return "Effect size (" + m + ")";
    }

    // ------------------------------------------------------------------ ggplot2-like frame
    /** Panel with ggplot2 theme_minimal look: no frame, light grid, grey tick labels. */
    static final class Gg {
        Canvas c; double x0, x1, y0, y1, xl, xh, yl, yh;
        double X(double v) { return x0 + (v - xl) / (xh - xl) * (x1 - x0); }
        double Y(double v) { return y1 - (v - yl) / (yh - yl) * (y1 - y0); }
    }
    static final Color GRID = new Color(222, 222, 222), GRID_MINOR = new Color(240, 240, 240), TICK_TXT = new Color(77, 77, 77);

    static Gg ggFrame(Canvas c, double left, double right, double top, double bottom, double xl, double xh, double yl, double yh, String title, String subtitle, String xlab, String ylab) { return ggFrame(c, left, right, top, bottom, xl, xh, yl, yh, title, subtitle, xlab, ylab, false); }
    /** logY: yl and yh are natural logarithms; grid lines and labels of the y-axis are at ratio values (0.25, 0.5, 1, 2, ...). */
    static Gg ggFrame(Canvas c, double left, double right, double top, double bottom, double xl, double xh, double yl, double yh, String title, String subtitle, String xlab, String ylab, boolean logY) {
        Gg a = new Gg(); a.c = c; a.x0 = left; a.x1 = c.w / c.scaleF() - right; a.y0 = top; a.y1 = c.h / c.scaleF() - bottom; a.xl = xl; a.xh = xh; a.yl = yl; a.yh = yh;
        Graphics2D g = c.g; double[] xt = Canvas.pretty(xl, xh, 6), yt = logY ? Plots.logTicks(Math.exp(yl), Math.exp(yh)) : Canvas.pretty(yl, yh, 6);
        if (logY) for (int i = 0; i < yt.length; i++) yt[i] = Math.log(yt[i]);   // positions on the log scale; labels below are exponentiated again
        g.setStroke(new BasicStroke(0.8f)); g.setColor(GRID_MINOR);
        for (int i = 0; i + 1 < xt.length; i++) { double m = (xt[i] + xt[i + 1]) / 2; if (m > xl && m < xh) c.line(a.X(m), a.y0, a.X(m), a.y1); }
        for (int i = 0; i + 1 < yt.length; i++) { double m = (yt[i] + yt[i + 1]) / 2; if (m > yl && m < yh) c.line(a.x0, a.Y(m), a.x1, a.Y(m)); }
        g.setStroke(new BasicStroke(1.1f)); g.setColor(GRID);
        for (double t : xt) if (t >= xl - 1e-9 && t <= xh + 1e-9) c.line(a.X(t), a.y0, a.X(t), a.y1);
        for (double t : yt) if (t >= yl - 1e-9 && t <= yh + 1e-9) c.line(a.x0, a.Y(t), a.x1, a.Y(t));
        g.setColor(TICK_TXT);
        for (double t : xt) if (t >= xl - 1e-9 && t <= xh + 1e-9) c.text(Canvas.tick(t), a.X(t), a.y1 + 17, 11f, 1, false);
        for (double t : yt) if (t >= yl - 1e-9 && t <= yh + 1e-9) c.text(Canvas.tick(logY ? Math.exp(t) : t), a.x0 - 7, a.Y(t) + 4, 11f, 2, false);
        g.setColor(Color.BLACK); c.text(xlab, (a.x0 + a.x1) / 2, a.y1 + 40, 13f, 1, false); c.textRot(ylab, 16, (a.y0 + a.y1) / 2, 13f);
        c.text(title, a.x0, 28, 17f, 0, false);
        if (subtitle != null && !subtitle.isEmpty()) { g.setColor(new Color(60, 60, 60)); c.text(subtitle, a.x0, 48, 12.5f, 0, false); }
        return a;
    }

    static void ggBand(Gg a, double[][] cv, boolean log, Color band, Color edge, boolean ci, Color pband, Color pedge, boolean pi) {
        if (!ci && !pi) return; Graphics2D g = a.c.g; Shape clip = g.getClip(); g.setClip(new Rectangle2D.Double(a.x0, a.y0, a.x1 - a.x0, a.y1 - a.y0)); int n = cv[0].length;
        if (pi) {   // prediction band underneath the confidence band: lighter area, dotted bounds
            Path2D p = new Path2D.Double();
            for (int i = 0; i < n; i++) { double x = a.X(cv[0][i]), y = a.Y(tr(cv[5][i], log)); if (i == 0) p.moveTo(x, y); else p.lineTo(x, y); }
            for (int i = n - 1; i >= 0; i--) p.lineTo(a.X(cv[0][i]), a.Y(tr(cv[4][i], log))); p.closePath();
            g.setColor(alpha(pband, 0.5)); g.fill(p);
            g.setColor(pedge); g.setStroke(DOTTED);
            for (int row = 4; row <= 5; row++) { Path2D q = new Path2D.Double(); for (int i = 0; i < n; i++) { double x = a.X(cv[0][i]), y = a.Y(tr(cv[row][i], log)); if (i == 0) q.moveTo(x, y); else q.lineTo(x, y); } g.draw(q); }
        }
        if (ci) {
            Path2D p = new Path2D.Double();
            for (int i = 0; i < n; i++) { double x = a.X(cv[0][i]), y = a.Y(tr(cv[3][i], log)); if (i == 0) p.moveTo(x, y); else p.lineTo(x, y); }
            for (int i = n - 1; i >= 0; i--) p.lineTo(a.X(cv[0][i]), a.Y(tr(cv[2][i], log))); p.closePath();
            g.setColor(alpha(band, 0.75)); g.fill(p);
            g.setColor(edge); g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{5f, 4f}, 0));
            for (int row = 2; row <= 3; row++) { Path2D q = new Path2D.Double(); for (int i = 0; i < n; i++) { double x = a.X(cv[0][i]), y = a.Y(tr(cv[row][i], log)); if (i == 0) q.moveTo(x, y); else q.lineTo(x, y); } g.draw(q); }
        }
        g.setStroke(new BasicStroke(1f)); g.setClip(clip);
    }
    static void ggLine(Gg a, double[][] cv, boolean log, Color col, boolean show) {
        if (!show) return; Graphics2D g = a.c.g; Shape clip = g.getClip(); g.setClip(new Rectangle2D.Double(a.x0, a.y0, a.x1 - a.x0, a.y1 - a.y0));
        Path2D q = new Path2D.Double(); for (int i = 0; i < cv[0].length; i++) { double x = a.X(cv[0][i]), y = a.Y(tr(cv[1][i], log)); if (i == 0) q.moveTo(x, y); else q.lineTo(x, y); }
        g.setColor(col); g.setStroke(new BasicStroke(2.3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND)); g.draw(q); g.setStroke(new BasicStroke(1f)); g.setClip(clip);
    }
    static void ggPoint(Gg a, String sym, double xv, double yv, double rad, Color fill) {
        drawSymbol(a.c.g, sym, a.X(xv), a.Y(yv), rad, fill, 217, isOpen(sym) ? fill : new Color(0, 0, 0, 170), 0.9f);
    }

    /** ggplot2-style regression plot of a model with exactly one numeric covariate. */
    static BufferedImage ggBubble(Reg g0, Plots.Opts o, MetaResult r) {
        MetaSpec s = r.spec; final boolean ratio = s.backtransf && s.isRatioSm(), ly = ratio && o.logAxis, log = ratio && !ly; List<LegItem> leg = legendItems(o, s, true); int ext = legendExtra(leg); Canvas c = new Canvas(720, 450 + ext, o.scale);
        double dxl = Arrays.stream(g0.x1).min().getAsDouble(), dxh = Arrays.stream(g0.x1).max().getAsDouble();
        final boolean icpt = g0.intercept; double[][] cv = curve(g0, s, dxl, dxh, x -> icpt ? new double[]{1, x} : new double[]{x});
        double yl = Double.POSITIVE_INFINITY, yh = Double.NEGATIVE_INFINITY;
        for (double v : g0.y1) { double t = tr(v, log); yl = Math.min(yl, t); yh = Math.max(yh, t); }
        for (int i = 0; i < cv[0].length; i++) for (int q : rowsShown(o)) { double t = tr(cv[q][i], log); yl = Math.min(yl, t); yh = Math.max(yh, t); }
        double px = (dxh - dxl) * 0.05 + 1e-9, py = (yh - yl) * 0.06 + 1e-9;
        Gg a = ggFrame(c, 66, 24, 58, 56 + ext, dxl - px, dxh + px, yl - py, yh + py, "Meta-Regression", "", g0.xName, effectLabel(s) + (ly ? " - log scale" : ""), ly);
        drawAll(a, g0, o, r, cv, log, colour(o.colPoints, POINTS_DEFAULT), null);
        drawLegend(c, (a.x0 + a.x1) / 2, a.y1 + 52, leg);
        return c.img;
    }

    private static void drawAll(Gg a, Reg g0, Plots.Opts o, MetaResult r, double[][] cv, boolean log, Color pts, Color[] perStudy) {
        ggBand(a, cv, log, colour(o.colBand, BAND_DEFAULT), colour(o.colBandLine, BANDLINE_DEFAULT), o.confInt, colour(o.colPred, PRED_DEFAULT), colour(o.colPredLine, PREDLINE_DEFAULT), o.predInt);
        double wmax = Arrays.stream(g0.size != null ? g0.size : weights(g0)).max().getAsDouble(); double[] w = g0.size != null ? g0.size : weights(g0);
        Shape clip = a.c.g.getClip(); a.c.g.setClip(new Rectangle2D.Double(a.x0 - 14, a.y0 - 14, a.x1 - a.x0 + 28, a.y1 - a.y0 + 28));
        for (int i = 0; i < w.length; i++) { double rad = o.symSize * (4 + 9 * Math.sqrt(w[i] / wmax)); double xv = xOf(g0, i), yv = tr(g0.yv[i], log); ggPoint(a, o.symbol, xv, yv, rad, perStudy != null ? perStudy[i] : pts);
            if (o.studyLabels) { a.c.g.setColor(new Color(40, 40, 40)); a.c.text(r.studlab[g0.used[i]], a.X(xv) + rad + 2, a.Y(yv) - 2, 8f, 0, false); } }
        a.c.g.setClip(clip);
        ggLine(a, cv, log, colour(o.colLine, LINE_DEFAULT), o.regLine);
    }
    private static double[] weights(Reg g) { double[] w = new double[g.k]; for (int j = 0; j < g.k; j++) w[j] = g.fit.wTilde[j]; return w; }
    private static double xOf(Reg g, int i) { return g.x1 != null ? g.x1[i] : g.plotX[i]; }

    // ------------------------------------------------------------------ plot adjusted for all moderators of the model
    /** Regression plot that takes all selected moderators of the model into account: x = the first numeric covariate, study circles coloured by the chosen factor (the first one by default), the line is the model
     *  prediction for the reference category of every factor with the other numeric covariates at their mean. */
    public static BufferedImage adjusted(Reg g0, List<String> nums, List<String> facs, Plots.Opts o, MetaResult r) { return adjusted(g0, nums, facs, null, o, r); }

    /** As above; {@code colourBy} is the selected factor whose categories colour the studies (null, or a name that is not among {@code facs}: the first factor). */
    public static BufferedImage adjusted(Reg g0, List<String> nums, List<String> facs, String colourBy, Plots.Opts o, MetaResult r) {
        final int ci = colourBy == null || !facs.contains(colourBy) ? 0 : facs.indexOf(colourBy);   // index of the factor that colours the studies
        MetaSpec s = r.spec; final boolean ratio = s.backtransf && s.isRatioSm(), lgY = ratio && o.logAxis, log = ratio && !lgY;
        if (nums.isEmpty()) { Canvas c = new Canvas(620, 200, o.scale); c.g.setColor(Color.BLACK); c.text("This plot needs at least one numeric covariate (x-axis)", 310, 100, 12, 1, false); return c.img; }
        String[] cn = g0.coefNames; int p = g0.p, k = g0.k; double[][] X = g0.X;
        int xcol = Arrays.asList(cn).indexOf(nums.get(0));
        double[] mean = new double[p]; for (int a = 0; a < p; a++) { double t = 0; for (int j = 0; j < k; j++) t += X[j][a]; mean[a] = t / k; }
        // factor structure: columns of each factor, reference level, level of each study
        List<List<Integer>> fcols = new ArrayList<>(); List<String> refLevel = new ArrayList<>();
        for (String f : facs) { List<Integer> cols = new ArrayList<>(); String pre = f + ": "; for (int a = 0; a < p; a++) if (cn[a].startsWith(pre)) cols.add(a); fcols.add(cols);
            String ref = null; if (g0.intercept) for (String rl : g0.refLabel) if (rl.startsWith(pre)) ref = rl.substring(pre.length());
            if (ref == null && !cols.isEmpty()) ref = cn[cols.get(0)].substring(pre.length()); refLevel.add(ref); }
        final double[] base = new double[p]; for (int a = 0; a < p; a++) { boolean isFac = false; for (List<Integer> cols : fcols) if (cols.contains(a)) isFac = true; base[a] = isFac ? 0 : mean[a]; }
        if (g0.intercept) { int ic = Arrays.asList(cn).indexOf("intercept"); if (ic >= 0) base[ic] = 1; }
        if (!g0.intercept) for (int q = 0; q < fcols.size(); q++) if (!fcols.get(q).isEmpty()) base[fcols.get(q).get(0)] = 1;   // no intercept: the first category of each factor
        double[][] cv = curve(g0, s, Arrays.stream(X).mapToDouble(row -> row[xcol]).min().getAsDouble(), Arrays.stream(X).mapToDouble(row -> row[xcol]).max().getAsDouble(), x -> { double[] row = base.clone(); row[xcol] = x; return row; });
        // colours by the first factor
        Color[] per = null; List<String> levels = new ArrayList<>(); Color[] pal = null;
        if (!facs.isEmpty()) {
            String pre = facs.get(ci) + ": "; List<Integer> cols = fcols.get(ci); levels.add(refLevel.get(ci)); for (int a : cols) { String lv = cn[a].substring(pre.length()); if (!levels.contains(lv)) levels.add(lv); }
            pal = huePalette(levels.size()); per = new Color[k];
            for (int j = 0; j < k; j++) { String lv = refLevel.get(ci); for (int a : cols) if (X[j][a] > 0.5) lv = cn[a].substring(pre.length()); per[j] = pal[levels.indexOf(lv)]; }
        }
        // subtitle: reference categories and the other covariates at their mean
        List<String> sub = new ArrayList<>(); for (int q = 0; q < facs.size(); q++) sub.add(refLevel.get(q)); for (int q = 1; q < nums.size(); q++) { int a = Arrays.asList(cn).indexOf(nums.get(q)); sub.add(nums.get(q) + " = " + Canvas.tick3(mean[a])); }
        String subtitle = sub.isEmpty() ? "" : "Prediction shown for " + String.join(", ", sub);
        boolean leg = !levels.isEmpty(); int legW = 0;
        if (leg) { Canvas pr = Canvas.probe(); FontMetrics fm = pr.g.getFontMetrics(Canvas.font(12f)); int w = fm.stringWidth(facs.get(ci)); for (String l : levels) w = Math.max(w, fm.stringWidth(l) + 22); legW = w + 36; }
        List<LegItem> ileg = legendItems(o, s, true); int ext = legendExtra(ileg); Canvas c = new Canvas(720 + legW, 470 + ext, o.scale);
        double[] y = new double[k]; double dxl = Double.POSITIVE_INFINITY, dxh = Double.NEGATIVE_INFINITY, yl = Double.POSITIVE_INFINITY, yh = Double.NEGATIVE_INFINITY;
        for (int j = 0; j < k; j++) { dxl = Math.min(dxl, X[j][xcol]); dxh = Math.max(dxh, X[j][xcol]); double t = tr(g0.yv[j], log); yl = Math.min(yl, t); yh = Math.max(yh, t); }
        for (int i = 0; i < cv[0].length; i++) for (int q : rowsShown(o)) { double t = tr(cv[q][i], log); yl = Math.min(yl, t); yh = Math.max(yh, t); }
        double nul = log ? 1 : 0; yl = Math.min(yl, nul); yh = Math.max(yh, nul);
        double px = (dxh - dxl) * 0.05 + 1e-9, py = (yh - yl) * 0.06 + 1e-9;
        g0.plotX = new double[k]; for (int j = 0; j < k; j++) g0.plotX[j] = X[j][xcol];
        Gg a = ggFrame(c, 66, 24 + legW, 70, 56 + ext, dxl - px, dxh + px, yl - py, yh + py, "Meta-regression with moderators", subtitle, nums.get(0), effectLabel(s) + (lgY ? " - log scale" : ""), lgY);
        Graphics2D g = c.g; g.setColor(new Color(110, 110, 110)); g.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{5f, 4f}, 0)); c.line(a.x0, a.Y(nul), a.x1, a.Y(nul)); g.setStroke(new BasicStroke(1f));
        drawAll(a, g0, o, r, cv, log, colour(o.colPoints, POINTS_DEFAULT), per);
        drawLegend(c, (a.x0 + a.x1) / 2, a.y1 + 52, ileg);
        if (leg) { double lx = a.x1 + 26, ly = (a.y0 + a.y1) / 2 - (levels.size() * 20 + 24) / 2.0; g.setColor(Color.BLACK); c.text(facs.get(ci), lx, ly, 13f, 0, false);
            for (int q = 0; q < levels.size(); q++) { double yy = ly + 20 + q * 20; drawSymbol(g, o.symbol, lx + 5.5, yy, 4.8, pal[q], 217, isOpen(o.symbol) ? pal[q] : new Color(0, 0, 0, 170), 0.9f); g.setColor(Color.BLACK); c.text(levels.get(q), lx + 20, yy + 4, 12f, 0, false); } }
        return c.img;
    }
}
