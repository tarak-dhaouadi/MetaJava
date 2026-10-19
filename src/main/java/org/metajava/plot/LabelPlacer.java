package org.metajava.plot;

import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

/**
 * Label-placement algorithm of the ggplot2-style Baujat plot (boxed labels with leader lines, like ggrepel::geom_label_repel), shared by every plot that offers
 * the option "Avoid label overlap": the labels repel each other and every plotted point, a weak spring keeps each label near its point, and a leader line joins a label
 * to its point when the label had to move away. Positions are in the logical (unscaled) coordinates of the {@link Canvas}.
 */
final class LabelPlacer {
    private final double x0, y0, x1, y1; private final float font;
    private final List<String> texts = new ArrayList<>(); private final List<double[]> lab = new ArrayList<>();   // label points {x, y, radius}
    private final List<double[]> obs = new ArrayList<>();                                                        // obstacles {x, y, radius}: every plotted point
    private final List<Integer> obsOf = new ArrayList<>();

    LabelPlacer(double x0, double y0, double x1, double y1, float font) { this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1; this.font = font; }

    /** Returns a placer for the panel of {@code a}, or null when the option is off (callers then use the plain label). */
    static LabelPlacer on(boolean enabled, Plots.Axes a) { return enabled ? new LabelPlacer(a.x0, a.y0, a.x1, a.y1, 8.5f) : null; }
    static LabelPlacer on(boolean enabled, double x0, double y0, double x1, double y1) { return enabled ? new LabelPlacer(x0, y0, x1, y1, 8.5f) : null; }

    /** A plotted point that carries a label (its marker is an obstacle for all the labels). */
    void add(String text, double x, double y, double rad) {
        if (text == null || !Double.isFinite(x) || !Double.isFinite(y)) return;
        texts.add(text); lab.add(new double[]{x, y, rad}); obs.add(new double[]{x, y, rad});
    }
    /** A plotted point without a label (still an obstacle). */
    void point(double x, double y, double rad) { if (Double.isFinite(x) && Double.isFinite(y)) obs.add(new double[]{x, y, rad}); }

    /** Places and draws the labels (call after all the points are drawn). */
    void draw(Canvas c) {
        int n = texts.size(); if (n == 0) return; Graphics2D g = c.g; FontMetrics fm = g.getFontMetrics(Canvas.font(font));
        double[] px = new double[n], py = new double[n], rad = new double[n], w = new double[n], h = new double[n], cx = new double[n], cy = new double[n];
        for (int i = 0; i < n; i++) {
            px[i] = lab.get(i)[0]; py[i] = lab.get(i)[1]; rad[i] = lab.get(i)[2]; w[i] = fm.stringWidth(texts.get(i)) + 10; h[i] = 15;
            double ang = -((i * 0.618034) % 1.0) * Math.PI / 2, dist = 16 + 12 * (i % 7);
            double sx = px[i] + rad[i] + w[i] + dist > x1 ? -1 : 1, sy = py[i] - rad[i] - dist - h[i] < y0 ? -1 : 1;   // upper-right quadrant, mirrored where it would leave the panel
            cx[i] = px[i] + sx * Math.cos(ang) * (rad[i] + w[i] / 2 + dist); cy[i] = py[i] + sy * Math.sin(ang) * (rad[i] + dist);
        }
        int m = obs.size(); double[] ox = new double[m], oy = new double[m], orad = new double[m];
        for (int j = 0; j < m; j++) { ox[j] = obs.get(j)[0]; oy[j] = obs.get(j)[1]; orad[j] = obs.get(j)[2]; }
        int iters = n > 250 ? 300 : n > 120 ? 700 : 1500;
        for (int it = 0; it < iters; it++) {
            double step = 1.0 - it / (iters + 200.0); double[] fx = new double[n], fy = new double[n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) if (j != i) {   // labels repel each other
                    double dx = Math.abs(cx[i] - cx[j]), dy = Math.abs(cy[i] - cy[j]); if (dx > (w[i] + w[j]) / 2 + 3 || dy > (h[i] + h[j]) / 2 + 3) continue;
                    double ex = (w[i] + w[j]) / 2 + 3 - dx, ey = (h[i] + h[j]) / 2 + 3 - dy;
                    if (ex > 0 && ey > 0) { if (ex < ey) fx[i] += Math.signum(cx[i] - cx[j] + 1e-6 * (i - j)) * ex * 0.7; else fy[i] += Math.signum(cy[i] - cy[j] + 1e-6 * (i - j)) * ey * 0.7; }
                }
                for (int j = 0; j < m; j++) {   // points: keep the box off every marker
                    double nx = Math.max(cx[i] - w[i] / 2, Math.min(ox[j], cx[i] + w[i] / 2)), ny = Math.max(cy[i] - h[i] / 2, Math.min(oy[j], cy[i] + h[i] / 2)), dx = nx - ox[j], dy = ny - oy[j], d = Math.hypot(dx, dy), need = orad[j] + 3;
                    if (d < need) { if (d < 1e-6) { dx = cx[i] - ox[j]; dy = cy[i] - oy[j]; d = Math.hypot(dx, dy) + 1e-6; } fx[i] += dx / d * (need - d) * 0.6; fy[i] += dy / d * (need - d) * 0.6; }
                }
                fx[i] += (px[i] - cx[i]) * 0.004; fy[i] += (py[i] - cy[i]) * 0.004;
            }
            for (int i = 0; i < n; i++) { cx[i] += fx[i] * step; cy[i] += fy[i] * step; cx[i] = Math.max(x0 + w[i] / 2 + 1, Math.min(x1 - w[i] / 2 - 1, cx[i])); cy[i] = Math.max(y0 + h[i] / 2 + 1, Math.min(y1 - h[i] / 2 - 1, cy[i])); }
        }
        Shape clip = g.getClip(); g.setClip(null);
        g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < n; i++) {   // leader line: from the marker to the border of the box
            double dx = cx[i] - px[i], dy = cy[i] - py[i]; if (Math.abs(dx) < w[i] / 2 && Math.abs(dy) < h[i] / 2) continue;
            double tx = dx == 0 ? 1e9 : (w[i] / 2) / Math.abs(dx), ty = dy == 0 ? 1e9 : (h[i] / 2) / Math.abs(dy), t = Math.min(tx, ty), ex = cx[i] - dx * t, ey = cy[i] - dy * t, d = Math.hypot(dx, dy);
            g.setColor(new Color(40, 40, 40, 200)); c.line(px[i] + dx / d * Math.min(rad[i], d), py[i] + dy / d * Math.min(rad[i], d), ex, ey);
        }
        for (int i = 0; i < n; i++) {
            RoundRectangle2D.Double b = new RoundRectangle2D.Double(cx[i] - w[i] / 2, cy[i] - h[i] / 2, w[i], h[i], 5, 5);
            g.setColor(Color.WHITE); g.fill(b); g.setColor(new Color(50, 50, 50)); g.setStroke(new BasicStroke(0.8f)); g.draw(b);
            g.setColor(new Color(60, 60, 60)); c.text(texts.get(i), cx[i], cy[i] + 3.2, font, 1, false);
        }
        g.setStroke(new BasicStroke(1f)); g.setClip(clip); g.setColor(Color.BLACK);
    }
}
