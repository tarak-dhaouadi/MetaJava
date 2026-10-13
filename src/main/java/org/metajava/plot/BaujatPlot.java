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
    public static final String[] SYMBOL_COLOURS = prepend(RegPlots.COLOURS, AUTO), BACK_COLOURS = prepend(RegPlots.COLOURS, AUTO, WHITE);
    private static String[] prepend(String[] a, String... first) { String[] o = new String[first.length + a.length]; System.arraycopy(first, 0, o, 0, first.length); System.arraycopy(a, 0, o, first.length, a.length); return o; }

    /** Colour of a drop-down item (null for "Theme default" / unknown). */
    public static Color colourOf(String name) {
        if (name == null || name.equals(AUTO)) return null; if (name.equals(WHITE)) return Color.WHITE;
        for (String c : RegPlots.COLOURS) if (c.equals(name)) return RegPlots.rgb(name); return null;
    }
    static Color symbolColour(Plots.Opts o, boolean gg) { Color c = colourOf(o.baujatSymbol); return c != null ? c : gg ? new Color(0, 0, 255) : Color.BLACK; }
    static Color backColour(Plots.Opts o, boolean gg) { Color c = colourOf(o.baujatBack); return c != null ? c : gg ? new Color(235, 235, 235) : Color.WHITE; }
    static boolean isGg(Plots.Opts o) { return GG.equals(o.baujatTheme); }
    private static boolean light(Color c) { return 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue() > 238; }

    // ------------------------------------------------------------------ ggplot2 style
    private static String[] fmtTicks(double[] t, double lo, double hi) {
        int dec = 0; List<String> raw = new ArrayList<>();
        for (double v : t) { String s = Canvas.tick(v); raw.add(s); int p = s.indexOf('.'); if (p >= 0) dec = Math.max(dec, s.length() - p - 1); }
        String[] out = new String[t.length]; for (int i = 0; i < t.length; i++) out[i] = String.format(Locale.ROOT, "%." + dec + "f", t[i]).replaceFirst("^-(0\\.?0*)$", "$1"); return out;
    }

    static BufferedImage gg(List<double[]> pts, List<String> labs, Plots.Opts o) {
        Canvas c = new Canvas(760, 620, o.scale); Graphics2D g = c.g;
        double L = 82, R = 20, T = 16, B = 66, x0 = L, x1 = c.w / c.scaleF() - R, y0 = T, y1 = c.h / c.scaleF() - B;
        double xm = 0, ym = 0, wmax = 0; for (double[] q : pts) { xm = Math.max(xm, q[0]); ym = Math.max(ym, q[1]); wmax = Math.max(wmax, q[2]); }
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
        // axis ticks and labels
        List<Double> xs = new ArrayList<>(), ys = new ArrayList<>(); for (double t : xt) if (t >= xl - 1e-9 && t <= xh + 1e-9) xs.add(t); for (double t : yt) if (t >= yl - 1e-9 && t <= yh + 1e-9) ys.add(t);
        String[] xlab = fmtTicks(xs.stream().mapToDouble(Double::doubleValue).toArray(), xl, xh), ylab = fmtTicks(ys.stream().mapToDouble(Double::doubleValue).toArray(), yl, yh);
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i < xs.size(); i++) { double xx = X.applyAsDouble(xs.get(i)); g.setColor(new Color(51, 51, 51)); c.line(xx, y1, xx, y1 + 4); g.setColor(new Color(77, 77, 77)); c.text(xlab[i], xx, y1 + 19, 13f, 1, false); }
        for (int i = 0; i < ys.size(); i++) { double yy = Y.applyAsDouble(ys.get(i)); g.setColor(new Color(51, 51, 51)); c.line(x0 - 4, yy, x0, yy); g.setColor(new Color(77, 77, 77)); c.text(ylab[i], x0 - 8, yy + 4.5, 13f, 2, false); }
        g.setColor(Color.BLACK); c.text("Overall heterogeneity contribution", (x0 + x1) / 2, y1 + 50, 17f, 1, false); c.textRot("Influence on pooled result", 20, (y0 + y1) / 2, 17f);
        // points
        int n = pts.size(); double[] px = new double[n], py = new double[n], rad = new double[n];
        g.setClip(new Rectangle2D.Double(x0 - 14, y0 - 14, x1 - x0 + 28, y1 - y0 + 28));
        Integer[] ord = new Integer[n]; for (int i = 0; i < n; i++) { ord[i] = i; px[i] = X.applyAsDouble(pts.get(i)[0]); py[i] = Y.applyAsDouble(pts.get(i)[1]); rad[i] = 3.5 + 8.5 * Math.sqrt(pts.get(i)[2] / wmax); }
        Arrays.sort(ord, (a, b) -> Double.compare(rad[b], rad[a]));   // small circles on top of large ones
        for (int i : ord) { g.setColor(new Color(pc.getRed(), pc.getGreen(), pc.getBlue(), 190)); g.fill(new Ellipse2D.Double(px[i] - rad[i], py[i] - rad[i], 2 * rad[i], 2 * rad[i])); }
        g.setClip(clip);
        if (o.studyLabels) labels(c, labs, px, py, rad, x0, y0, x1, y1);
        return c.img;
    }

    /** Boxed labels with leader lines; positions found by a simple repulsion procedure (labels repel each other and the points, a weak spring keeps each near its point). */
    private static void labels(Canvas c, List<String> labs, double[] px, double[] py, double[] rad, double x0, double y0, double x1, double y1) {
        Graphics2D g = c.g; int n = labs.size(); FontMetrics fm = g.getFontMetrics(Canvas.font(8.5f));
        double[] w = new double[n], h = new double[n], cx = new double[n], cy = new double[n];
        for (int i = 0; i < n; i++) { w[i] = fm.stringWidth(labs.get(i)) + 10; h[i] = 15; double ang = -((i * 0.618034) % 1.0) * Math.PI / 2, dist = 16 + 12 * (i % 7); cx[i] = px[i] + Math.cos(ang) * (rad[i] + w[i] / 2 + dist); cy[i] = py[i] + Math.sin(ang) * (rad[i] + dist);   // start in the free upper-right quadrant
        }
        for (int it = 0; it < 1500; it++) {
            double step = 1.0 - it / 1700.0; double[] fx = new double[n], fy = new double[n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) if (j != i) {
                    double ox = (w[i] + w[j]) / 2 + 3 - Math.abs(cx[i] - cx[j]), oy = (h[i] + h[j]) / 2 + 3 - Math.abs(cy[i] - cy[j]);
                    if (ox > 0 && oy > 0) { if (ox < oy) fx[i] += Math.signum(cx[i] - cx[j] + 1e-6 * (i - j)) * ox * 0.7; else fy[i] += Math.signum(cy[i] - cy[j] + 1e-6 * (i - j)) * oy * 0.7; }
                }
                for (int j = 0; j < n; j++) {   // points: keep the box off every circle
                    double nx = Math.max(cx[i] - w[i] / 2, Math.min(px[j], cx[i] + w[i] / 2)), ny = Math.max(cy[i] - h[i] / 2, Math.min(py[j], cy[i] + h[i] / 2)), dx = nx - px[j], dy = ny - py[j], d = Math.hypot(dx, dy), need = rad[j] + 3;
                    if (d < need) { if (d < 1e-6) { dx = cx[i] - px[j]; dy = cy[i] - py[j]; d = Math.hypot(dx, dy) + 1e-6; } fx[i] += dx / d * (need - d) * 0.6; fy[i] += dy / d * (need - d) * 0.6; }
                }
                fx[i] += (px[i] - cx[i]) * 0.004; fy[i] += (py[i] - cy[i]) * 0.004;
            }
            for (int i = 0; i < n; i++) { cx[i] += fx[i] * step; cy[i] += fy[i] * step; cx[i] = Math.max(x0 + w[i] / 2 + 1, Math.min(x1 - w[i] / 2 - 1, cx[i])); cy[i] = Math.max(y0 + h[i] / 2 + 1, Math.min(y1 - h[i] / 2 - 1, cy[i])); }
        }
        g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < n; i++) {   // leader line: from the circle to the border of the box
            double dx = cx[i] - px[i], dy = cy[i] - py[i]; if (Math.abs(dx) < w[i] / 2 && Math.abs(dy) < h[i] / 2) continue;
            double tx = dx == 0 ? 1e9 : (w[i] / 2) / Math.abs(dx), ty = dy == 0 ? 1e9 : (h[i] / 2) / Math.abs(dy), t = Math.min(tx, ty), ex = cx[i] - dx * t, ey = cy[i] - dy * t, d = Math.hypot(dx, dy);
            g.setColor(new Color(40, 40, 40, 200)); c.line(px[i] + dx / d * Math.min(rad[i], d), py[i] + dy / d * Math.min(rad[i], d), ex, ey);
        }
        for (int i = 0; i < n; i++) {
            RoundRectangle2D.Double b = new RoundRectangle2D.Double(cx[i] - w[i] / 2, cy[i] - h[i] / 2, w[i], h[i], 5, 5);
            g.setColor(Color.WHITE); g.fill(b); g.setColor(new Color(50, 50, 50)); g.setStroke(new BasicStroke(0.8f)); g.draw(b);
            g.setColor(new Color(60, 60, 60)); c.text(labs.get(i), cx[i], cy[i] + 3.2, 8.5f, 1, false);
        }
        g.setStroke(new BasicStroke(1f));
    }
}
