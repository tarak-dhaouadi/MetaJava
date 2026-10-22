package org.metajava.plot;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/**
 * Baujat plot themes and colours.
 * <ul>
 * <li>Default: the look of meta::baujat / metafor::baujat (black frame, plain circles, labels next to the points);</li>
 * <li>ggplot2 style: grey panel with white grid lines, semi-transparent circles sized by the study weight and boxed labels with leader lines (geom_label_repel).</li>
 * </ul>
 * The symbol and background colours apply to both themes; "Theme default" keeps what the theme prescribes.
 */
public final class BaujatPlot {
    private BaujatPlot() {}
    public static final String DEFAULT = "Default", GG = "ggplot2 style", AUTO = "Theme default", WHITE = "White";
    public static final String[] THEMES = {DEFAULT, GG};
    /** "Baujat model" drop-down: the model of the analysis as metafor::baujat.rma draws it (random effects when selected: squared Pearson residuals against the influence on the fitted value, tau^2 re-estimated
     *  after every deletion) or the common-effect model as meta::baujat draws it; with a common-effect analysis (and for GLMMs, which metafor's baujat does not cover) both give the common-effect plot. */
    public static final String MODEL_ANALYSIS = "As the analysis (metafor)", MODEL_COMMON = "Common effect (meta)";
    public static final String[] MODELS = {MODEL_ANALYSIS, MODEL_COMMON};
    /** True when the plot is drawn with the random-effects model of the analysis. */
    static boolean useRandom(org.metajava.core.MetaSpec s, Plots.Opts o) { return s.random && !s.isGlmm() && !MODEL_COMMON.equals(o.baujatModel); }
    public static final String[] SYMBOL_COLOURS = prepend(RegPlots.COLOURS, AUTO), BACK_COLOURS = prepend(RegPlots.COLOURS, AUTO, WHITE);
    private static String[] prepend(String[] a, String... first) { String[] o = new String[first.length + a.length]; System.arraycopy(first, 0, o, 0, first.length); System.arraycopy(a, 0, o, first.length, a.length); return o; }

    /** Colour of a drop-down item (null for "Theme default" / unknown). */
    public static Color colourOf(String name) {
        if (name == null || name.equals(AUTO)) return null; if (name.equals(WHITE)) return Color.WHITE;
        for (String c : RegPlots.COLOURS) if (c.equals(name)) return RegPlots.rgb(name); return null;
    }
    static Color symbolColour(Plots.Opts o, boolean gg) { Color c = colourOf(o.baujatSymbol); return c != null ? c : gg ? new Color(0, 0, 255) : Color.BLACK; }
    static Color backColour(Plots.Opts o, boolean gg) { Color c = colourOf(o.baujatBack); return c != null ? c : gg ? new Color(235, 235, 235) : Color.WHITE; }
    /** Colour "Theme default" stands for in the given theme (swatch of the drop-down lists); roles: 0 symbol colour, 1 background colour. */
    public static Color defaultColour(String theme, int role) { Plots.Opts o = new Plots.Opts(); o.baujatTheme = theme; return role == 0 ? symbolColour(o, isGg(o)) : backColour(o, isGg(o)); }
    static boolean isGg(Plots.Opts o) { return GG.equals(o.baujatTheme); }
    private static boolean light(Color c) { return 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue() > 238; }

    // ------------------------------------------------------------------ quadrants
    /** Positions of the two quadrant lines (\"Baujat quadrant lines\" drop-down). Baujat et al. (2002) and the R packages meta / metafor define no cut-off (the plot is read visually, relative to the other studies);
     *  the rules below are the data-driven and the conventional influence-diagnostic cut-offs used in the literature. x-line: contribution to heterogeneity, y-line: influence on the overall result. */
    public static final String Q_MEDIAN = "Median split", Q_MEAN = "Mean split", Q_P75 = "75th percentile (upper quartile)", Q_P85 = "85th percentile", Q_P90 = "90th percentile", Q_P95 = "95th percentile", Q_P99 = "99th percentile",
        Q_TUKEY = "Tukey fence (Q3 + 1.5 IQR)", Q_SD = "Mean + 2 SD", Q_SD3 = "Mean + 3 SD", Q_MAD = "Median + 2.5 MAD (robust)", Q_THEORY = "Theoretical (chi-square 3.84; 4/k)";
    public static final String[] QUAD_RULES = {Q_MEDIAN, Q_MEAN, Q_P75, Q_P85, Q_P90, Q_P95, Q_P99, Q_TUKEY, Q_SD, Q_SD3, Q_MAD, Q_THEORY};
    /** 0.95 quantile of the chi-square distribution with 1 df (= 1.96^2). */
    static final double CHI2_1_95 = 3.841458820694124;
    /** "Baujat quadrant colour" drop-down: the default red of the original quadrants or one of the named plot colours. The colour applies to the two quadrant lines (opaque), the shaded quadrant (same colour at
     *  {@link #FILL_ALPHA} / 255 opacity, always far more transparent than the lines) and the symbol and label of every study inside the shaded quadrant. */
    public static final String QUAD_AUTO = "Default (red)";
    public static final String[] QUAD_COLOURS = prepend(RegPlots.COLOURS, QUAD_AUTO);
    static final Color QUAD_DEFAULT = new Color(200, 30, 30); static final int FILL_ALPHA = 62;
    /** Colour of a quadrant-colour item (the default red for {@link #QUAD_AUTO}); for the drop-down swatches. */
    public static Color quadColourOf(String name) { Color c = colourOf(name); return c != null ? c : QUAD_DEFAULT; }
    static Color quadLine(Plots.Opts o) { return quadColourOf(o.baujatQuadColour); }
    static Color quadFill(Plots.Opts o) { Color c = quadLine(o); return new Color(c.getRed(), c.getGreen(), c.getBlue(), FILL_ALPHA); }
    /** For tests. */
    public static boolean[] flaggedForTest(List<double[]> pts, Plots.Opts o) { return flagged(pts, o); }
    /** Studies inside the shaded quadrant (strictly beyond both quadrant lines) when the quadrants are on; null otherwise. Their symbols and labels are drawn in the colour of the lines. */
    static boolean[] flagged(List<double[]> pts, Plots.Opts o) {
        if (!o.baujatQuad || pts.isEmpty()) return null;
        double[] cu = cutoffs(pts, o.baujatQuadRule); if (Double.isNaN(cu[0]) || Double.isNaN(cu[1])) return null;
        boolean[] f = new boolean[pts.size()]; for (int i = 0; i < f.length; i++) f[i] = pts.get(i)[0] > cu[0] && pts.get(i)[1] > cu[1]; return f;
    }

    /** Quantile with R's default definition (type 7: linear interpolation between order statistics). */
    static double quantile(double[] sorted, double p) {
        int n = sorted.length; if (n == 0) return Double.NaN; if (n == 1) return sorted[0];
        double h = (n - 1) * p; int lo = (int) Math.floor(h); int hi = Math.min(n - 1, lo + 1); return sorted[lo] + (h - lo) * (sorted[hi] - sorted[lo]);
    }
    private static double[] column(List<double[]> pts, int j) { double[] a = new double[pts.size()]; for (int i = 0; i < a.length; i++) a[i] = pts.get(i)[j]; Arrays.sort(a); return a; }
    private static double mean(double[] a) { double s = 0; for (double v : a) s += v; return s / a.length; }
    private static double sd(double[] a) { if (a.length < 2) return 0; double m = mean(a), s = 0; for (double v : a) s += (v - m) * (v - m); return Math.sqrt(s / (a.length - 1)); }
    /** Cut-off on one axis (a = sorted values) for a rule; k = number of studies (used by the 4/k rule on the influence axis). */
    private static double cut(double[] a, String rule, boolean yAxis) {
        int k = a.length; if (k == 0) return Double.NaN;
        if (Q_MEAN.equals(rule)) return mean(a);
        if (Q_P75.equals(rule)) return quantile(a, 0.75);
        if (Q_P85.equals(rule)) return quantile(a, 0.85);
        if (Q_P90.equals(rule)) return quantile(a, 0.90);
        if (Q_P95.equals(rule)) return quantile(a, 0.95);
        if (Q_P99.equals(rule)) return quantile(a, 0.99);
        if (Q_TUKEY.equals(rule)) { double q1 = quantile(a, 0.25), q3 = quantile(a, 0.75); return q3 + 1.5 * (q3 - q1); }
        if (Q_SD.equals(rule)) return mean(a) + 2 * sd(a);
        if (Q_SD3.equals(rule)) return mean(a) + 3 * sd(a);
        if (Q_MAD.equals(rule)) { double med = quantile(a, 0.5); double[] d = new double[k]; for (int i = 0; i < k; i++) d[i] = Math.abs(a[i] - med); Arrays.sort(d); return med + 2.5 * 1.4826 * quantile(d, 0.5); }
        if (Q_THEORY.equals(rule)) return yAxis ? 4.0 / k : CHI2_1_95;   // y: (2 / sqrt(k))^2, the DFBETAS cut-off of Belsley, Kuh & Welsch squared (Cook's D: 4/k); x: squared 1.96 residual
        return quantile(a, 0.5);   // median split (default)
    }
    /** {x-line, y-line} for the points (x = contribution to heterogeneity, y = influence) under a rule; NaN when there are no points. */
    public static double[] cutoffs(List<double[]> pts, String rule) {
        return new double[]{cut(column(pts, 0), rule, false), cut(column(pts, 1), rule, true)};
    }
    /** Upper limit that keeps both quadrant lines inside the plot (the axes are extended when a rule puts a line beyond the data). */
    static double[] extend(double xm, double ym, Plots.Opts o, List<double[]> pts) {
        if (!o.baujatQuad || pts.isEmpty()) return new double[]{xm, ym};
        double[] c = cutoffs(pts, o.baujatQuadRule); return new double[]{Double.isNaN(c[0]) ? xm : Math.max(xm, c[0]), Double.isNaN(c[1]) ? ym : Math.max(ym, c[1])};
    }
    /** Quadrants: the high-influence / high-contribution quadrant (upper right) shaded with the colour of the lines at partial transparency, the two lines and their captions
     *  (the vertical line separates the contribution to heterogeneity, the horizontal one the influence). Drawn inside the panel (x0, y0)-(x1, y1) before the points. */
    static void quadrants(Canvas c, java.util.function.DoubleUnaryOperator X, java.util.function.DoubleUnaryOperator Y, double x0, double y0, double x1, double y1, List<double[]> pts, Plots.Opts o) {
        if (!o.baujatQuad || pts.isEmpty()) return;
        double[] cu = cutoffs(pts, o.baujatQuadRule); if (Double.isNaN(cu[0]) || Double.isNaN(cu[1])) return;
        Graphics2D g = c.g; Shape clip = g.getClip(); Stroke st = g.getStroke(); g.setClip(new Rectangle2D.Double(x0, y0, x1 - x0, y1 - y0));
        double xv = Math.max(x0, Math.min(x1, X.applyAsDouble(cu[0]))), yv = Math.max(y0, Math.min(y1, Y.applyAsDouble(cu[1])));
        g.setColor(quadFill(o)); g.fill(new Rectangle2D.Double(xv, y0, x1 - xv, yv - y0));
        g.setColor(quadLine(o)); g.setStroke(new BasicStroke(1.4f)); c.line(xv, y0, xv, y1); c.line(x0, yv, x1, yv);
        final float fs = 11f; g.setFont(Canvas.bold(fs)); FontMetrics fm = g.getFontMetrics();
        // caption of the horizontal line (influence), right-aligned just below it (above it when the line is at the bottom of the panel)
        String hl = "High influence"; double hy = yv + 15 < y1 - 3 ? yv + 15 : yv - 5; c.text(hl, x1 - 6, hy, fs, 2, true);
        // caption of the vertical line (contribution to heterogeneity), rotated, left of the line near the top (right of it when there is no room on the left)
        String vl = "High contribution to heterogeneity"; double wd = fm.stringWidth(vl), vx = xv - 6 - 0 >= x0 + 14 ? xv - 6 : xv + 14, vy = y0 + 8 + wd / 2;
        if (vy + wd / 2 > y1) vy = (y0 + y1) / 2;
        AffineTransform tr = g.getTransform(); g.rotate(-Math.PI / 2, vx, vy); g.setFont(Canvas.bold(fs)); g.drawString(vl, (float) (vx - wd / 2), (float) vy); g.setTransform(tr);
        g.setStroke(st); g.setClip(clip); g.setColor(Color.BLACK);
    }

    // ------------------------------------------------------------------ ggplot2 style
    private static String[] fmtTicks(double[] t, double lo, double hi) {
        int dec = 0; List<String> raw = new ArrayList<>();
        for (double v : t) { String s = Canvas.tick(v); raw.add(s); int p = s.indexOf('.'); if (p >= 0) dec = Math.max(dec, s.length() - p - 1); }
        String[] out = new String[t.length]; for (int i = 0; i < t.length; i++) out[i] = String.format(Locale.ROOT, "%." + dec + "f", t[i]).replaceFirst("^-(0\\.?0*)$", "$1"); return out;
    }

    static BufferedImage gg(List<double[]> pts, List<String> labs, Plots.Opts o, boolean rnd) {
        Canvas c = new Canvas(760, 620, o.scale); Graphics2D g = c.g;
        double L = 82, R = 20, T = 16, B = 66, x0 = L, x1 = c.w / c.scaleF() - R, y0 = T, y1 = c.h / c.scaleF() - B;
        double xm = 0, ym = 0, wmax = 0; for (double[] q : pts) { xm = Math.max(xm, q[0]); ym = Math.max(ym, q[1]); wmax = Math.max(wmax, q[2]); }
        { double[] e = extend(xm, ym, o, pts); xm = e[0]; ym = e[1]; }   // quadrant lines always inside the plot
        if (xm <= 0) xm = 1; if (ym <= 0) ym = 1; if (wmax <= 0) wmax = 1;
        double xl = -0.05 * xm, xh = xm * 1.05, yl = -0.05 * ym, yh = ym * 1.05;
        final double fxl = xl, fxh = xh, fyl = yl, fyh = yh, fx0 = x0, fx1 = x1, fy0 = y0, fy1 = y1;
        java.util.function.DoubleUnaryOperator X = v -> fx0 + (v - fxl) / (fxh - fxl) * (fx1 - fx0), Y = v -> fy1 - (v - fyl) / (fyh - fyl) * (fy1 - fy0);
        Color bg = backColour(o, true), pc = symbolColour(o, true);
        g.setColor(bg); g.fill(new Rectangle2D.Double(x0, y0, x1 - x0, y1 - y0));
        double[] xt = Canvas.pretty(xl, xh, 5), yt = Canvas.pretty(yl, yh, 5);
        Color grid = light(bg) ? new Color(228, 228, 228) : Color.WHITE; Shape clip = g.getClip(); g.setClip(new Rectangle2D.Double(x0, y0, x1 - x0, y1 - y0));
        g.setColor(grid); g.setStroke(new BasicStroke(0.6f));
        for (int i = 0; i + 1 < xt.length; i++) { double m = (xt[i] + xt[i + 1]) / 2; c.line(X.applyAsDouble(m), y0, X.applyAsDouble(m), y1); }
        for (int i = 0; i + 1 < yt.length; i++) { double m = (yt[i] + yt[i + 1]) / 2; c.line(x0, Y.applyAsDouble(m), x1, Y.applyAsDouble(m)); }
        g.setStroke(new BasicStroke(1.2f)); for (double t : xt) c.line(X.applyAsDouble(t), y0, X.applyAsDouble(t), y1); for (double t : yt) c.line(x0, Y.applyAsDouble(t), x1, Y.applyAsDouble(t));
        g.setClip(clip);
        quadrants(c, X, Y, x0, y0, x1, y1, pts, o);   // optional quadrants (shading, lines, captions) above the grid and below the points
        // axis ticks and labels
        List<Double> xs = new ArrayList<>(), ys = new ArrayList<>(); for (double t : xt) if (t >= xl - 1e-9 && t <= xh + 1e-9) xs.add(t); for (double t : yt) if (t >= yl - 1e-9 && t <= yh + 1e-9) ys.add(t);
        String[] xlab = fmtTicks(xs.stream().mapToDouble(Double::doubleValue).toArray(), xl, xh), ylab = fmtTicks(ys.stream().mapToDouble(Double::doubleValue).toArray(), yl, yh);
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i < xs.size(); i++) { double xx = X.applyAsDouble(xs.get(i)); g.setColor(new Color(51, 51, 51)); c.line(xx, y1, xx, y1 + 4); g.setColor(new Color(77, 77, 77)); c.text(xlab[i], xx, y1 + 19, 13f, 1, false); }
        for (int i = 0; i < ys.size(); i++) { double yy = Y.applyAsDouble(ys.get(i)); g.setColor(new Color(51, 51, 51)); c.line(x0 - 4, yy, x0, yy); g.setColor(new Color(77, 77, 77)); c.text(ylab[i], x0 - 8, yy + 4.5, 13f, 2, false); }
        g.setColor(Color.BLACK); c.text(rnd ? "Squared Pearson residual" : "Overall heterogeneity contribution", (x0 + x1) / 2, y1 + 50, 17f, 1, false); c.textRot("Influence on overall result", 20, (y0 + y1) / 2, 17f);
        // points
        int n = pts.size(); double[] px = new double[n], py = new double[n], rad = new double[n];
        g.setClip(new Rectangle2D.Double(x0 - 14, y0 - 14, x1 - x0 + 28, y1 - y0 + 28));
        Integer[] ord = new Integer[n]; for (int i = 0; i < n; i++) { ord[i] = i; px[i] = X.applyAsDouble(pts.get(i)[0]); py[i] = Y.applyAsDouble(pts.get(i)[1]); rad[i] = o.baujatSymSize * (3.5 + 8.5 * Math.sqrt(pts.get(i)[2] / wmax)); }
        Arrays.sort(ord, (a, b) -> Double.compare(rad[b], rad[a]));   // small circles on top of large ones
        final boolean[] fl = flagged(pts, o);
        for (int i : ord) { Color cc = fl != null && fl[i] ? quadLine(o) : pc; g.setColor(new Color(cc.getRed(), cc.getGreen(), cc.getBlue(), 190)); g.fill(new Ellipse2D.Double(px[i] - rad[i], py[i] - rad[i], 2 * rad[i], 2 * rad[i])); }
        g.setClip(clip);
        if (o.studyLabels) labels(c, labs, px, py, rad, x0, y0, x1, y1, fl, quadLine(o));
        return c.img;
    }

    /** Boxed labels with leader lines (geom_label_repel-like): see {@link LabelPlacer}, which every plot offering "Avoid label overlap" shares. */
    private static void labels(Canvas c, List<String> labs, double[] px, double[] py, double[] rad, double x0, double y0, double x1, double y1, boolean[] fl, Color hot) {
        LabelPlacer lp = new LabelPlacer(x0, y0, x1, y1, 8.5f); for (int i = 0; i < labs.size(); i++) lp.add(labs.get(i), px[i], py[i], rad[i], fl != null && fl[i] ? hot : null); lp.draw(c);
    }
}
