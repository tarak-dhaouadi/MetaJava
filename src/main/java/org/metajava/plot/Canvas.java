package org.metajava.plot;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/** Helpers for base-graphics-like drawing (white background, black axes, Helvetica-like font). */
public final class Canvas {
    public final BufferedImage img; public final Graphics2D g; public final int w, h; private final double sc;
    public double scaleF() { return sc; }
    public Canvas(int w, int h, double scale) {
        this.sc = scale; this.w = (int) (w * scale); this.h = (int) (h * scale);
        img = new BufferedImage(this.w, this.h, BufferedImage.TYPE_INT_RGB);
        g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(scale, scale);
        g.setColor(Color.WHITE); g.fillRect(0, 0, w, h); g.setColor(Color.BLACK);
    }
    public static Font font(float size) { return new Font("SansSerif", Font.PLAIN, Math.round(size)).deriveFont(size); }
    public static Font bold(float size) { return new Font("SansSerif", Font.BOLD, Math.round(size)).deriveFont(size); }
    public void text(String s, double x, double y, float size, int align, boolean bold) {
        g.setFont(bold ? bold(size) : font(size)); FontMetrics fm = g.getFontMetrics(); double wd = fm.stringWidth(s);
        double xx = align == 0 ? x : align == 1 ? x - wd / 2 : x - wd; g.drawString(s, (float) xx, (float) y);
    }
    public void textRot(String s, double x, double y, float size) {
        g.setFont(font(size)); AffineTransform o = g.getTransform(); g.rotate(-Math.PI / 2, x, y);
        double wd = g.getFontMetrics().stringWidth(s); g.drawString(s, (float) (x - wd / 2), (float) y); g.setTransform(o);
    }
    public void line(double x1, double y1, double x2, double y2) { g.draw(new Line2D.Double(x1, y1, x2, y2)); }
    public void dashed(double x1, double y1, double x2, double y2) {
        Stroke o = g.getStroke(); g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{5, 4}, 0)); line(x1, y1, x2, y2); g.setStroke(o);
    }
    /** Pretty axis ticks (R's pretty()). */
    public static double[] pretty(double lo, double hi, int n) {
        if (hi - lo < 1e-300) { return new double[]{lo}; }
        double raw = (hi - lo) / n, mag = Math.pow(10, Math.floor(Math.log10(raw))), f = raw / mag;
        double unit = f < 1.5 ? 1 : f < 3 ? 2 : f < 7 ? 5 : 10; unit *= mag;
        double s = Math.floor(lo / unit + 1e-9) * unit, e = Math.ceil(hi / unit - 1e-9) * unit;
        int m = (int) Math.round((e - s) / unit) + 1; double[] t = new double[m];
        for (int i = 0; i < m; i++) t[i] = Math.round((s + i * unit) / unit * 1e9) / 1e9 * unit; return t;
    }
    public static String tick3(double v) { if (v == 0) return "0"; return new java.math.BigDecimal(v).round(new java.math.MathContext(3)).stripTrailingZeros().toPlainString(); }
    public static String tick(double v) { String s = String.format(java.util.Locale.ROOT, "%.4f", v).replaceAll("0+$", "").replaceAll("\\.$", ""); return s.equals("-0") ? "0" : s; }
}
