package org.metajava.plot;

import org.metajava.core.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/** GOSH plot, orchard plot and caterpillar plot (same drawing style as {@link Plots}; all drawing goes through Canvas, so SVG / PDF export works). */
public final class ExtraPlots {
    private ExtraPlots() {}
    static final Color BLUE = new Color(0x1F, 0x4E, 0x8C), ORANGE = new Color(0xD9, 0x82, 0x2B), INK = new Color(60, 60, 60), GRID = new Color(150, 150, 150);

    /** Horizontal value axis in "axis space": the working scale for ratio measures drawn on a log axis, the back-transformed scale otherwise. */
    static final class Ax {
        double x0, x1, xl, xh; boolean log;
        double X(double v) { return x0 + (v - xl) / (xh - xl) * (x1 - x0); }
    }

    static boolean isLog(MetaSpec s) { return s.backtransf && s.isRatioSm(); }
    /** Axis-space value of a working-scale value (n: sample size / person-time for the Freeman-Tukey back-transformations). */
    static double av(MetaSpec s, double working, double n) { return isLog(s) ? working : Meta.back(s, working, n); }

    static void drawXAxis(Canvas c, Ax a, double y, String label, boolean frameTop, double yTop) {
        Graphics2D g = c.g; g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1f)); c.line(a.x0, y, a.x1, y);
        double[] t = a.log ? Plots.logTicks(Math.exp(a.xl), Math.exp(a.xh)) : Canvas.pretty(a.xl, a.xh, 6);
        if (a.log) { int in = 0; for (double q : t) { double v = Math.log(q); if (v >= a.xl && v <= a.xh) in++; } if (in < 2) t = Canvas.pretty(Math.exp(a.xl), Math.exp(a.xh), 5); }
        for (double q : t) { double v = a.log ? Math.log(q) : q; if (v < a.xl - 1e-12 || v > a.xh + 1e-12) continue; double xx = a.X(v); c.line(xx, y, xx, y + 5); c.text(Canvas.tick(q), xx, y + 18, 10, 1, false); }
        c.text(label, (a.x0 + a.x1) / 2, y + 40, 11, 1, false);
    }

    /** Value shown in the text columns: back-transformed (ratios: exp of the working-scale value). */
    static double shown(MetaSpec s, double axisValue) { return isLog(s) ? Math.exp(axisValue) : axisValue; }

    static double jitter(int i) { long h = i * 0x9E3779B97F4A7C15L; h ^= h >>> 29; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 32; return ((h & 0xFFFFL) / 65535.0) - 0.5; }

    // ------------------------------------------------------------------ GOSH
    public static BufferedImage gosh(Gosh gs, MetaResult r, Plots.Opts o, boolean highlight) { return gosh(gs, r, o, highlight, null); }

    /** cl: when it holds at least two clusters the subsets are coloured by cluster (instead of the density map or the highlight of one study). */
    public static BufferedImage gosh(Gosh gs, MetaResult r, Plots.Opts o, boolean highlight0, GoshCluster cl) { return gosh(gs, r, o, highlight0, cl, false); }

    /**
     * @param trim limit the x axis to the central 99% of the subsets when that is more than 25% narrower than the full range (a few extreme subsets otherwise flatten the plot);
     *             the displayed range and the number of subsets outside it are stated below the plot, and no subset is removed from the analysis
     */
    public static BufferedImage gosh(Gosh gs, MetaResult r, Plots.Opts o, boolean highlight0, GoshCluster cl, boolean trim) {
        final boolean clus = cl != null && cl.found(); final boolean highlight = highlight0 && !clus;
        MetaSpec s = r.spec; boolean log = isLog(s);
        final double fx0 = 72, fx1 = 600, fy0 = 112, fy1 = 548;
        int n = gs.nSub; double[] xs = new double[n]; double xmin = Double.POSITIVE_INFINITY, xmax = Double.NEGATIVE_INFINITY;
        for (int b = 0; b < n; b++) { xs[b] = Double.isNaN(gs.est[b]) ? Double.NaN : av(s, gs.est[b], gs.nHarm == null ? r.overall.nHarm : gs.nHarm[b]); if (!Double.isNaN(xs[b]) && !Double.isNaN(gs.i2[b])) { xmin = Math.min(xmin, xs[b]); xmax = Math.max(xmax, xs[b]); } }
        double full = av(s, gs.fullEst, r.overall.nHarm); xmin = Math.min(xmin, full); xmax = Math.max(xmax, full);
        boolean trimmed = false;
        if (trim) { double[] v = new double[n]; int nv = 0; for (int b = 0; b < n; b++) if (!Double.isNaN(xs[b]) && !Double.isNaN(gs.i2[b])) v[nv++] = xs[b];
            if (nv >= 20) { v = Arrays.copyOf(v, nv); Arrays.sort(v); double ql = Math.min(quant(v, 0.005), full), qh = Math.max(quant(v, 0.995), full); if (qh > ql && xmax - xmin > 1.25 * (qh - ql)) { xmin = ql; xmax = qh; trimmed = true; } } }
        if (!(xmax > xmin)) { double m = Double.isFinite(xmin) ? xmin : 0; xmin = m - 0.5; xmax = m + 0.5; }
        double pad = (xmax - xmin) * 0.05; Ax a = new Ax(); a.x0 = fx0; a.x1 = fx1; a.xl = xmin - pad; a.xh = xmax + pad; a.log = log;
        List<String> trimLines = new ArrayList<>(); int outside = 0;
        if (trimmed) { int nv = 0; for (int b = 0; b < n; b++) if (!Double.isNaN(xs[b]) && !Double.isNaN(gs.i2[b])) { nv++; if (xs[b] < a.xl || xs[b] > a.xh) outside++; }
            trimLines = trimNotes(new String[]{"x axis (" + Plots.xlabFor(s) + ")"}, new double[][]{{a.xl, a.xh}}, new int[]{outside}, nv, 118); }
        Canvas c = new Canvas(720, trimLines.isEmpty() ? 640 : 632 + 12 * trimLines.size(), o.scale); Graphics2D g = c.g;
        for (int l = 0; l < trimLines.size(); l++) c.text(trimLines.get(l), 20, 622 + 12 * l, 9, 0, false);
        double yl = 0, yh = 100;
        java.util.function.DoubleUnaryOperator Y = v -> fy1 - (v - yl) / (yh - yl) * (fy1 - fy0);
        // points or density
        int hl = highlight ? gs.mostInfluential() : -1;
        int[] counts = null; int nx = (int) Math.round((fx1 - fx0) / 4), ny = (int) Math.round((fy1 - fy0) / 4);
        boolean dens = !highlight && !clus && n > 6000;
        Shape clip = g.getClip(); g.setClip(new Rectangle2D.Double(fx0, fy0, fx1 - fx0, fy1 - fy0));
        if (dens) {
            counts = new int[nx * ny]; int mx = 0;
            for (int b = 0; b < n; b++) { if (Double.isNaN(xs[b]) || Double.isNaN(gs.i2[b]) || xs[b] < a.xl || xs[b] > a.xh) continue; int ix = (int) Math.min(nx - 1, Math.max(0, (xs[b] - a.xl) / (a.xh - a.xl) * nx)), iy = (int) Math.min(ny - 1, Math.max(0, (gs.i2[b] - yl) / (yh - yl) * ny)); int v = ++counts[iy * nx + ix]; if (v > mx) mx = v; }
            double cw = (fx1 - fx0) / nx, ch = (fy1 - fy0) / ny;
            for (int iy = 0; iy < ny; iy++) for (int ix = 0; ix < nx; ix++) { int v = counts[iy * nx + ix]; if (v == 0) continue; double t = Math.log1p(v) / Math.log1p(mx);
                g.setColor(ramp(t)); g.fill(new Rectangle2D.Double(fx0 + ix * cw - 0.3, fy1 - (iy + 1) * ch - 0.3, cw + 0.6, ch + 0.6)); }
        } else {
            int step = Math.max(1, (int) Math.ceil(n / 8000.0));
            for (int pass = 0; pass < (highlight ? 2 : 1); pass++) for (int b = 0; b < n; b += step) { if (Double.isNaN(xs[b]) || Double.isNaN(gs.i2[b])) continue;
                boolean in = highlight && gs.includes(b, hl); if (highlight && (pass == 0) == in) continue;
                if (clus && cl.label[b] < 0) continue;
                g.setColor(clus ? clusterColor(cl.label[b], 95) : !highlight ? new Color(BLUE.getRed(), BLUE.getGreen(), BLUE.getBlue(), 70) : in ? new Color(BLUE.getRed(), BLUE.getGreen(), BLUE.getBlue(), 80) : new Color(ORANGE.getRed(), ORANGE.getGreen(), ORANGE.getBlue(), 90));
                double xx = a.X(xs[b]), yy = Y.applyAsDouble(gs.i2[b]); g.fill(new Ellipse2D.Double(xx - 2.2, yy - 2.2, 4.4, 4.4)); }
        }
        g.setStroke(new BasicStroke(1f)); g.setColor(GRID); c.dashed(a.X(full), fy0, a.X(full), fy1); c.dashed(fx0, Y.applyAsDouble(gs.fullI2), fx1, Y.applyAsDouble(gs.fullI2));
        g.setClip(clip);
        // frame and axes
        g.setColor(Color.BLACK); g.draw(new Rectangle2D.Double(fx0, fy0, fx1 - fx0, fy1 - fy0));
        drawXAxis(c, a, fy1, Plots.xlabFor(s), false, fy0);
        for (double t : Canvas.pretty(yl, yh, 5)) { if (t < yl - 1e-9 || t > yh + 1e-9) continue; double yy = Y.applyAsDouble(t); c.line(fx0 - 5, yy, fx0, yy); c.textRot(Canvas.tick(t), fx0 - 10, yy, 10); }
        c.textRot("I² (%)", 16, (fy0 + fy1) / 2, 11);
        // marginal densities: one curve per group (includes / excludes the highlighted study, or one per cluster), colour-matched to the points and each scaled to unit area,
        // so the shapes can be compared even when the groups differ in size; a single curve for all subsets when the points are not grouped
        final int nb = 50; final int ng = highlight ? 2 : clus ? cl.G : 1;
        double[][] hx = new double[ng][nb], hy = new double[ng][nb]; int[] cnt = new int[ng];
        for (int b = 0; b < n; b++) { if (Double.isNaN(xs[b]) || Double.isNaN(gs.i2[b]) || xs[b] < a.xl || xs[b] > a.xh) continue;
            int q = highlight ? (gs.includes(b, hl) ? 0 : 1) : clus ? cl.label[b] : 0; if (q < 0 || q >= ng) continue; cnt[q]++;
            hx[q][(int) Math.min(nb - 1, Math.max(0, (xs[b] - a.xl) / (a.xh - a.xl) * nb))]++; hy[q][(int) Math.min(nb - 1, Math.max(0, (gs.i2[b] - yl) / (yh - yl) * nb))]++; }
        double mx = 0, my = 0;
        for (int q = 0; q < ng; q++) { hx[q] = smooth(hx[q]); hy[q] = smooth(hy[q]); if (cnt[q] > 0) { for (int i2 = 0; i2 < nb; i2++) { hx[q][i2] /= cnt[q]; hy[q][i2] /= cnt[q]; mx = Math.max(mx, hx[q][i2]); my = Math.max(my, hy[q][i2]); } } }
        for (int q = 0; q < ng; q++) { if (cnt[q] == 0) continue;
            Color line = ng == 1 ? BLUE : highlight ? (q == 0 ? BLUE : ORANGE) : clusterColor(q, 255), fill = ng == 1 ? new Color(0xD9, 0xE2, 0xF0) : new Color(line.getRed(), line.getGreen(), line.getBlue(), 70);
            Path2D top = new Path2D.Double(); top.moveTo(fx0, fy0 - 4);
            for (int i2 = 0; i2 < nb; i2++) { double xx = fx0 + (i2 + 0.5) / nb * (fx1 - fx0); top.lineTo(xx, fy0 - 4 - (mx > 0 ? hx[q][i2] / mx : 0) * 58); } top.lineTo(fx1, fy0 - 4); top.closePath();
            g.setColor(fill); g.fill(top); g.setColor(line); g.setStroke(new BasicStroke(ng == 1 ? 1f : 1.6f)); g.draw(top);
            Path2D rt = new Path2D.Double(); rt.moveTo(fx1 + 4, fy1);
            for (int i2 = 0; i2 < nb; i2++) { double yy = fy1 - (i2 + 0.5) / nb * (fy1 - fy0); rt.lineTo(fx1 + 4 + (my > 0 ? hy[q][i2] / my : 0) * 62, yy); } rt.lineTo(fx1 + 4, fy0); rt.closePath();
            g.setColor(fill); g.fill(rt); g.setColor(line); g.draw(rt); }
        g.setStroke(new BasicStroke(1f));
        if (ng > 1) { g.setColor(Color.DARK_GRAY); c.text(highlight ? "Marginal curves: density of the subsets that include / exclude " + gs.labels[hl] + " (each curve has equal area)" : "Marginal curves: density within each cluster (each curve has equal area)", fx0, 22, 9, 0, false); }
        g.setColor(Color.BLACK);
        // legend / notes
        String note = String.format(Locale.ROOT, "%,d %ssubsets", n, gs.exhaustive ? "(all) " : "random ");
        c.text(note, fx0 + 8, fy0 + 16, 10, 0, false);
        if (highlight) { double ly = fy0 + 32; g.setColor(new Color(BLUE.getRed(), BLUE.getGreen(), BLUE.getBlue(), 160)); g.fill(new Ellipse2D.Double(fx0 + 8, ly - 7, 8, 8)); g.setColor(Color.BLACK); c.text("includes " + gs.labels[hl], fx0 + 22, ly, 10, 0, false);
            ly += 15; g.setColor(new Color(ORANGE.getRed(), ORANGE.getGreen(), ORANGE.getBlue(), 190)); g.fill(new Ellipse2D.Double(fx0 + 8, ly - 7, 8, 8)); g.setColor(Color.BLACK); c.text("excludes " + gs.labels[hl], fx0 + 22, ly, 10, 0, false); }
        if (clus) clusterLegend(c, cl, fx0 + 8, fy0 + 32);
        c.text("dashed lines: all " + gs.k + " studies", fx1 - 6, fy1 - 8, 9, 2, false);
        return c.img;
    }

    /** Sequential single-hue ramp (light to dark blue) for the density of the GOSH points. */
    static Color ramp(double t) {
        t = Math.max(0, Math.min(1, t)); int[] lo = {0xE3, 0xEC, 0xF7}, hi = {0x0B, 0x2D, 0x63};
        return new Color((int) Math.round(lo[0] + (hi[0] - lo[0]) * t), (int) Math.round(lo[1] + (hi[1] - lo[1]) * t), (int) Math.round(lo[2] + (hi[2] - lo[2]) * t));
    }
    static double[] smooth(double[] h) { double[] o = new double[h.length]; for (int i = 0; i < h.length; i++) { double s = 0; int c = 0; for (int d = -2; d <= 2; d++) { int j = i + d; if (j >= 0 && j < h.length) { s += h[j]; c++; } } o[i] = s / c; } return o; }


    // ------------------------------------------------------------------ cluster colours
    /** Colour-blind-safe qualitative palette (Okabe and Ito). */
    static final int[][] CL = {{0, 114, 178}, {230, 159, 0}, {0, 158, 115}, {213, 94, 0}, {204, 121, 167}, {110, 110, 110}};
    static Color clusterColor(int g, int alpha) { int[] q = CL[Math.floorMod(g, CL.length)]; return new Color(q[0], q[1], q[2], alpha); }
    static void clusterLegend(Canvas c, GoshCluster cl, double x, double y) {
        Graphics2D g = c.g;
        for (int q = 0; q < cl.G; q++) { g.setColor(clusterColor(q, 220)); g.fill(new Ellipse2D.Double(x, y - 7, 8, 8)); g.setColor(Color.BLACK);
            c.text(String.format(Locale.ROOT, "cluster %d (%.0f%%)", q + 1, cl.share[q]), x + 14, y, 10, 0, false); y += 15; }
    }

    // ------------------------------------------------------------------ GOSH for meta-regression
    /** Indices of the valid subsets, thinned to at most `max` (deterministic stride). */
    static int[] validIdx(GoshReg gs, int max) {
        int c = 0; for (int b = 0; b < gs.nSub; b++) if (gs.valid(b)) c++; int step = Math.max(1, (int) Math.ceil(c / (double) max)); int[] out = new int[(c + step - 1) / step]; int t = 0, i = 0;
        for (int b = 0; b < gs.nSub; b++) if (gs.valid(b)) { if (i++ % step == 0 && t < out.length) out[t++] = b; } return Arrays.copyOf(out, t);
    }

    static double[] range(double[] x, int[] idx, double ref) {
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY; for (int b : idx) { if (Double.isFinite(x[b])) { lo = Math.min(lo, x[b]); hi = Math.max(hi, x[b]); } }
        if (Double.isFinite(ref)) { lo = Math.min(lo, ref); hi = Math.max(hi, ref); } if (!(hi > lo)) { double m = Double.isFinite(lo) ? lo : 0; lo = m - 0.5; hi = m + 0.5; } double pad = (hi - lo) * 0.05; return new double[]{lo - pad, hi + pad};
    }

    /**
     * Axis range of the valid subsets. With trim, and when the central 99% of the subsets (0.5% to 99.5% quantiles, widened to include the reference value) is clearly
     * narrower than the full range (the full range is more than 25% wider), the axis is limited to the central 99% so that a few extreme subsets (nearly collinear ones,
     * for example) do not flatten the plot. The subsets outside are not removed from the analysis, only left out of the drawing; out[0] receives their number
     * (0 when the full range is shown).
     */
    static double[] range(double[] x, int[] idx, double ref, boolean trim, int[] out) {
        double[] full = range(x, idx, ref); if (out != null) out[0] = 0; if (!trim) return full;
        double[] v = new double[idx.length]; int n = 0; for (int b : idx) if (Double.isFinite(x[b])) v[n++] = x[b]; if (n < 20) return full; v = Arrays.copyOf(v, n); Arrays.sort(v);
        double lo0 = v[0], hi0 = v[n - 1], ql = quant(v, 0.005), qh = quant(v, 0.995); if (Double.isFinite(ref)) { lo0 = Math.min(lo0, ref); hi0 = Math.max(hi0, ref); ql = Math.min(ql, ref); qh = Math.max(qh, ref); }
        if (!(qh > ql) || !(hi0 - lo0 > 1.25 * (qh - ql))) return full;
        double pad = (qh - ql) * 0.05, lo = ql - pad, hi = qh + pad; int c = 0; for (double q : v) if (q < lo || q > hi) c++; if (out != null) out[0] = c; return new double[]{lo, hi};
    }
    static double quant(double[] sorted, double q) { double pos = q * (sorted.length - 1); int i = (int) Math.floor(pos); double f = pos - i; return i + 1 < sorted.length ? sorted[i] * (1 - f) + sorted[i + 1] * f : sorted[i]; }

    /** Lines stating whether the x axis of the ordinary GOSH plot is limited to the central 99% of the subsets, the displayed range and how many valid subsets lie outside it (empty when it is not limited). */
    public static List<String> goshAxisNotes(Gosh gs, MetaResult r, boolean trim) {
        MetaSpec s = r.spec; int n = gs.nSub; double[] v = new double[n]; int nv = 0; double mn = Double.POSITIVE_INFINITY, mx = Double.NEGATIVE_INFINITY;
        for (int b = 0; b < n; b++) { if (Double.isNaN(gs.est[b]) || Double.isNaN(gs.i2[b])) continue; double x = av(s, gs.est[b], gs.nHarm == null ? r.overall.nHarm : gs.nHarm[b]); v[nv++] = x; mn = Math.min(mn, x); mx = Math.max(mx, x); }
        double full = av(s, gs.fullEst, r.overall.nHarm); mn = Math.min(mn, full); mx = Math.max(mx, full);
        if (!trim || nv < 20) return new ArrayList<>(); v = Arrays.copyOf(v, nv); Arrays.sort(v); double ql = Math.min(quant(v, 0.005), full), qh = Math.max(quant(v, 0.995), full);
        if (!(qh > ql && mx - mn > 1.25 * (qh - ql))) return new ArrayList<>(); double pad = (qh - ql) * 0.05, lo = ql - pad, hi = qh + pad; int out = 0; for (double x : v) if (x < lo || x > hi) out++;
        return trimNotes(new String[]{"x axis (" + Plots.xlabFor(s) + ")"}, new double[][]{{lo, hi}}, new int[]{out}, nv, 118);
    }

    /** Lines (wrapped to about maxChars) that state which axes were limited to the central 99%, the displayed range and how many valid subsets lie outside; empty when no axis was limited. */
    static List<String> trimNotes(String[] names, double[][] rg, int[] outside, int nValid, int maxChars) {
        List<String> parts = new ArrayList<>(); for (int a = 0; a < names.length; a++) if (outside[a] > 0 && rg[a] != null)
            parts.add(String.format(Locale.ROOT, "%s [%s, %s]: %,d subsets (%.1f%%) outside", names[a], Canvas.tick3(rg[a][0]), Canvas.tick3(rg[a][1]), outside[a], 100.0 * outside[a] / Math.max(1, nValid)));
        List<String> lines = new ArrayList<>(); if (parts.isEmpty()) return lines;
        String head = (parts.size() == 1 ? "Axis" : "Axes") + " limited to the central 99% of the valid subsets (displayed range; subsets outside it are kept in the analysis and the tables, only not drawn): ", cur = head;
        for (int i = 0; i < parts.size(); i++) { String t = parts.get(i) + (i < parts.size() - 1 ? "; " : "."); if (cur.length() + t.length() > maxChars && cur.length() > 0 && !cur.equals(head) ) { lines.add(cur.trim()); cur = ""; } cur += t; }
        lines.add(cur.trim()); List<String> w = new ArrayList<>(); for (String l : lines) { while (l.length() > maxChars) { int cut = l.lastIndexOf(' ', maxChars); if (cut < 20) cut = maxChars; w.add(l.substring(0, cut)); l = l.substring(cut).trim(); } w.add(l); } return w;
    }

    /** One scatter panel (x against y) in the box [x0,x1] x [y0,y1]: density map for large clouds, points (coloured by cluster) otherwise. */
    static void panel(Canvas c, GoshReg gs, double[] xv, double[] yv, double[] xr, double[] yr, double x0, double x1, double y0, double y1, GoshCluster cl, int hl, double refX, double refY, float ptSize) {
        Graphics2D g = c.g; boolean clus = cl != null && cl.found(); final boolean hlOn = !clus && hl >= 0; int nv = gs.nValid; boolean dens = !clus && !hlOn && nv > 4000;
        Shape clip = g.getClip(); g.setClip(new Rectangle2D.Double(x0, y0, x1 - x0, y1 - y0));
        if (dens) {
            int nx = Math.max(8, (int) Math.round((x1 - x0) / 3)), ny = Math.max(8, (int) Math.round((y1 - y0) / 3)); int[] cnt = new int[nx * ny]; int mx = 0;
            for (int b = 0; b < gs.nSub; b++) { if (!gs.valid(b) || xv[b] < xr[0] || xv[b] > xr[1] || yv[b] < yr[0] || yv[b] > yr[1]) continue; int ix = (int) Math.min(nx - 1, Math.max(0, (xv[b] - xr[0]) / (xr[1] - xr[0]) * nx)), iy = (int) Math.min(ny - 1, Math.max(0, (yv[b] - yr[0]) / (yr[1] - yr[0]) * ny)); int v = ++cnt[iy * nx + ix]; if (v > mx) mx = v; }
            double cw = (x1 - x0) / nx, ch = (y1 - y0) / ny;
            for (int iy = 0; iy < ny; iy++) for (int ix = 0; ix < nx; ix++) { int v = cnt[iy * nx + ix]; if (v == 0) continue; g.setColor(ramp(Math.log1p(v) / Math.log1p(mx))); g.fill(new Rectangle2D.Double(x0 + ix * cw - 0.3, y1 - (iy + 1) * ch - 0.3, cw + 0.6, ch + 0.6)); }
        } else {
            int[] idx = validIdx(gs, clus ? 2500 : 4000); double r = ptSize / 2;
            for (int pass = 0; pass < (hlOn ? 2 : 1); pass++) for (int b : idx) { if (clus && cl.label[b] < 0) continue;
                boolean in = hlOn && gs.includes(b, hl); if (hlOn && (pass == 0) == in) continue;   // subsets without the study first, then those with it (as in the ordinary GOSH plot)
                g.setColor(clus ? clusterColor(cl.label[b], 100) : !hlOn ? new Color(BLUE.getRed(), BLUE.getGreen(), BLUE.getBlue(), nv > 1500 ? 60 : 90) : in ? new Color(BLUE.getRed(), BLUE.getGreen(), BLUE.getBlue(), 80) : new Color(ORANGE.getRed(), ORANGE.getGreen(), ORANGE.getBlue(), 90));
                g.fill(new Ellipse2D.Double(x0 + (xv[b] - xr[0]) / (xr[1] - xr[0]) * (x1 - x0) - r, y1 - (yv[b] - yr[0]) / (yr[1] - yr[0]) * (y1 - y0) - r, ptSize, ptSize)); }
        }
        g.setStroke(new BasicStroke(1f)); g.setColor(GRID);
        if (Double.isFinite(refX)) { double xx = x0 + (refX - xr[0]) / (xr[1] - xr[0]) * (x1 - x0); c.dashed(xx, y0, xx, y1); }
        if (Double.isFinite(refY)) { double yy = y1 - (refY - yr[0]) / (yr[1] - yr[0]) * (y1 - y0); c.dashed(x0, yy, x1, yy); }
        g.setClip(clip); g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1f)); g.draw(new Rectangle2D.Double(x0, y0, x1 - x0, y1 - y0));
    }

    static void xTicks(Canvas c, double[] r, double x0, double x1, double y, int n, boolean labels) {
        for (double t : Canvas.pretty(r[0], r[1], n)) { if (t < r[0] - 1e-9 || t > r[1] + 1e-9) continue; double xx = x0 + (t - r[0]) / (r[1] - r[0]) * (x1 - x0); c.line(xx, y, xx, y + 4); if (labels) c.text(Canvas.tick3(t), xx, y + 15, 9, 1, false); }
    }
    static void yTicks(Canvas c, double[] r, double y0, double y1, double x, int n, boolean labels) {
        for (double t : Canvas.pretty(r[0], r[1], n)) { if (t < r[0] - 1e-9 || t > r[1] + 1e-9) continue; double yy = y1 - (t - r[0]) / (r[1] - r[0]) * (y1 - y0); c.line(x - 4, yy, x, yy); if (labels) c.text(Canvas.tick3(t), x - 7, yy + 3, 9, 2, false); }
    }

    /** "Coefficients versus residual heterogeneity": one panel per regression coefficient, coefficient (x) against residual I^2 (y) over the valid subsets; dashed lines: the model with all studies. */
    /** Lines stating which coefficient axes of the regression GOSH plots are limited to the central 99% of the subsets, the displayed range and how many valid subsets lie outside it (empty when no axis is limited or trim is false). */
    public static List<String> goshRegAxisNotes(GoshReg gs, boolean trim) {
        int p = gs.p; int[] all = validIdx(gs, Integer.MAX_VALUE); double[][] xrs = new double[p][]; int[] outN = new int[p]; int[] tmp = new int[1];
        for (int a = 0; a < p; a++) { xrs[a] = range(gs.coef[a], all, gs.fullCoef[a], trim, tmp); outN[a] = tmp[0]; }
        return trimNotes(gs.coefNames, xrs, outN, gs.nValid, 100);
    }
    /** Colour legend of the regression GOSH plots: clusters, or inclusion of the most influential study, or a note that no meaningful clusters were found (the cluster detection was asked for). */
    static void goshRegLegend(Canvas c, GoshReg gs, GoshCluster cl, int hl, double x, double yDot) {
        Graphics2D g = c.g; boolean clus = cl != null && cl.found();
        if (clus) { double lx = x; for (int q = 0; q < cl.G; q++) { g.setColor(clusterColor(q, 220)); g.fill(new Ellipse2D.Double(lx, yDot, 8, 8)); g.setColor(Color.BLACK); c.text(String.format(Locale.ROOT, "cluster %d", q + 1), lx + 12, yDot + 8, 10, 0, false); lx += 80; } }
        else if (hl >= 0) { String nm = gs.labels[hl]; g.setColor(new Color(BLUE.getRed(), BLUE.getGreen(), BLUE.getBlue(), 200)); g.fill(new Ellipse2D.Double(x, yDot, 8, 8)); g.setColor(Color.BLACK); c.text("subsets that include " + nm, x + 12, yDot + 8, 10, 0, false);
            double lx = x + 40 + 6.2 * ("subsets that include " + nm).length(); g.setColor(new Color(ORANGE.getRed(), ORANGE.getGreen(), ORANGE.getBlue(), 220)); g.fill(new Ellipse2D.Double(lx, yDot, 8, 8)); g.setColor(Color.BLACK); c.text("subsets that exclude " + nm, lx + 12, yDot + 8, 10, 0, false); }
        else if (cl != null) { g.setColor(INK); c.text("no meaningful clusters: the subsets are not coloured by cluster", x, yDot + 8, 10, 0, false); g.setColor(Color.BLACK); }
    }
    /** Study used to colour the subsets by inclusion (the most influential one), or -1 when the highlight is off or clusters are shown instead. */
    static int highlightStudy(GoshReg gs, boolean highlight) { GoshCluster cl = gs.clusters; return highlight && !(cl != null && cl.found()) && gs.nValid > 0 ? gs.mostInfluential() : -1; }

    public static BufferedImage goshRegCoef(GoshReg gs, Plots.Opts o) { return goshRegCoef(gs, o, true); }
    /** @param trim limit an axis to the central 99% of the subsets when a few extreme subsets would flatten it (the displayed range and the number of subsets outside are stated in the plot) */
    public static BufferedImage goshRegCoef(GoshReg gs, Plots.Opts o, boolean trim) { return goshRegCoef(gs, o, trim, false); }
    /** @param highlight colour the subsets by inclusion of the most influential study (as in the ordinary GOSH plot; cluster colours take precedence when clusters were found) */
    public static BufferedImage goshRegCoef(GoshReg gs, Plots.Opts o, boolean trim, boolean highlight) {
        int p = gs.p; int ncol = Math.min(3, p), nrow = (p + ncol - 1) / ncol; final int cw = 270, chh = 250; int W = ncol * cw + 30, H = nrow * chh + 70;
        int[] all = validIdx(gs, Integer.MAX_VALUE); double[] yr = {0, 100}; double[][] xrs = new double[p][]; int[] outN = new int[p]; int[] tmp = new int[1];
        for (int a = 0; a < p; a++) { xrs[a] = range(gs.coef[a], all, gs.fullCoef[a], trim, tmp); outN[a] = tmp[0]; }
        List<String> notes = trimNotes(gs.coefNames, xrs, outN, gs.nValid, Math.max(60, (Math.max(W, 560) - 24) * 10 / 52)); if (!notes.isEmpty()) { W = Math.max(W, 560); H += 12 * notes.size() + 4; }
        Canvas c = new Canvas(W, H, o.scale); Graphics2D g = c.g; GoshCluster cl = gs.clusters; boolean clus = cl != null && cl.found(); final int hl = highlightStudy(gs, highlight);
        for (int l = 0; l < notes.size(); l++) c.text(notes.get(l), 20, 50 + nrow * chh + 8 + 12 * l, 9, 0, false);
        c.text(String.format(Locale.ROOT, "%,d valid of %,d %ssubsets", gs.nValid, gs.nSub, gs.exhaustive ? "(all) " : "random "), 20, 18, 11, 0, true);
        goshRegLegend(c, gs, cl, hl, 20, 29);
        for (int a = 0; a < p; a++) {
            int rI = a / ncol, cI = a % ncol; double x0 = 20 + cI * cw + 52, x1 = 20 + cI * cw + cw - 18, y0 = 50 + rI * chh + 22, y1 = 50 + rI * chh + chh - 52;
            double[] xr = xrs[a];
            c.text(gs.coefNames[a], (x0 + x1) / 2, y0 - 8, 11, 1, true);
            panel(c, gs, gs.coef[a], gs.i2, xr, yr, x0, x1, y0, y1, cl, hl, gs.fullCoef[a], gs.fullI2, 4.0f);
            g.setColor(Color.BLACK); xTicks(c, xr, x0, x1, y1, 5, true); yTicks(c, yr, y0, y1, x0, 5, true);
            c.text("coefficient", (x0 + x1) / 2, y1 + 32, 10, 1, false); if (cI == 0) c.textRot("residual I\u00B2 (%)", 14, (y0 + y1) / 2, 10);
        }
        c.text("dashed lines: model with all " + gs.k + " studies", W - 12, H - 8, 9, 2, false);
        return c.img;
    }

    /** Scatterplot matrix of the residual I^2 and the regression coefficients over the valid subsets (histograms on the diagonal, scatterplots below, correlations above). */
    public static BufferedImage goshRegMatrix(GoshReg gs, Plots.Opts o) { return goshRegMatrix(gs, o, true); }
    /** @param trim limit an axis to the central 99% of the subsets when a few extreme subsets would flatten it (stated in the plot; the correlations always use all valid subsets) */
    public static BufferedImage goshRegMatrix(GoshReg gs, Plots.Opts o, boolean trim) { return goshRegMatrix(gs, o, trim, false); }
    public static BufferedImage goshRegMatrix(GoshReg gs, Plots.Opts o, boolean trim, boolean highlight) {
        int p = gs.p; int maxCoef = 7; int nc = Math.min(p, maxCoef); int m = nc + 1; final int cell = m <= 4 ? 150 : m <= 6 ? 125 : 105; final int left = 54, top = 44, bottom = 36;
        int W = left + m * cell + 20, H = top + m * cell + bottom;
        String[] nm = new String[m]; double[][] v = new double[m][]; double[] ref = new double[m]; nm[0] = "I\u00B2 (%)"; v[0] = gs.i2; ref[0] = gs.fullI2;
        for (int a = 0; a < nc; a++) { nm[a + 1] = gs.coefNames[a]; v[a + 1] = gs.coef[a]; ref[a + 1] = gs.fullCoef[a]; }
        int[] all = validIdx(gs, Integer.MAX_VALUE); double[][] rg = new double[m][]; int[] outN = new int[m]; int[] tmp = new int[1];
        for (int a = 0; a < m; a++) { if (a == 0) rg[a] = new double[]{0, 100}; else { rg[a] = range(v[a], all, ref[a], trim, tmp); outN[a] = tmp[0]; } }
        List<String> notes = trimNotes(nm, rg, outN, gs.nValid, Math.max(60, (Math.max(W, 560) - 24) * 10 / 52)); if (!notes.isEmpty()) { W = Math.max(W, 560); H += 12 * notes.size() + 4; }
        Canvas c = new Canvas(W, H, o.scale); Graphics2D g = c.g; GoshCluster cl = gs.clusters; boolean clus = cl != null && cl.found(); final int hl = highlightStudy(gs, highlight);
        for (int l = 0; l < notes.size(); l++) c.text(notes.get(l), 20, top + m * cell + bottom + 8 + 12 * l, 9, 0, false);
        c.text(String.format(Locale.ROOT, "%,d valid of %,d %ssubsets", gs.nValid, gs.nSub, gs.exhaustive ? "(all) " : "random ") + (p > nc ? "; first " + nc + " of " + p + " coefficients shown" : ""), left, 18, 11, 0, true);
        goshRegLegend(c, gs, cl, hl, left, 27);
        for (int i = 0; i < m; i++) for (int j = 0; j < m; j++) {
            double x0 = left + j * cell + 4, x1 = left + (j + 1) * cell - 4, y0 = top + i * cell + 4, y1 = top + (i + 1) * cell - 4;
            if (i == j) {   // histogram of the variable
                int nb = 20; double[] h = new double[nb]; for (int b : all) { double q = v[i][b]; if (Double.isFinite(q) && q >= rg[i][0] && q <= rg[i][1]) h[(int) Math.min(nb - 1, Math.max(0, (q - rg[i][0]) / (rg[i][1] - rg[i][0]) * nb))]++; } double mx = 0; for (double q : h) mx = Math.max(mx, q);
                g.setColor(new Color(0xD9, 0xE2, 0xF0)); for (int q = 0; q < nb; q++) { double hh = mx > 0 ? h[q] / mx * (y1 - y0 - 18) : 0; g.fill(new Rectangle2D.Double(x0 + q * (x1 - x0) / nb, y1 - hh, (x1 - x0) / nb, hh)); }
                g.setColor(BLUE); for (int q = 0; q < nb; q++) { double hh = mx > 0 ? h[q] / mx * (y1 - y0 - 18) : 0; g.draw(new Rectangle2D.Double(x0 + q * (x1 - x0) / nb, y1 - hh, (x1 - x0) / nb, hh)); }
                g.setColor(Color.BLACK); g.draw(new Rectangle2D.Double(x0, y0, x1 - x0, y1 - y0)); c.text(nm[i], (x0 + x1) / 2, y0 + 12, 11, 1, true);
                g.setColor(GRID); double xx = x0 + (ref[i] - rg[i][0]) / (rg[i][1] - rg[i][0]) * (x1 - x0); c.dashed(xx, y0 + 16, xx, y1); g.setColor(Color.BLACK);
            } else if (i > j) panel(c, gs, v[j], v[i], rg[j], rg[i], x0, x1, y0, y1, cl, hl, ref[j], ref[i], 3.0f);
            else {   // correlation
                double r = corr(v[i], v[j], all); g.setColor(Color.BLACK); g.draw(new Rectangle2D.Double(x0, y0, x1 - x0, y1 - y0));
                c.text(Double.isNaN(r) ? "NA" : Fmt.num(r, 2), (x0 + x1) / 2, (y0 + y1) / 2 + 5, (float) (10 + 8 * (Double.isNaN(r) ? 0 : Math.abs(r))), 1, false); c.text("r", x0 + 8, y0 + 12, 9, 0, false);
            }
            g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1f));
            if (i == m - 1 && i != j || (i == m - 1 && j == m - 1)) { xTicks(c, rg[j], x0, x1, y1, 3, true); }
            if (j == 0 && i > 0) yTicks(c, rg[i], y0, y1, x0, 3, true);
            if (i == m - 1) c.text(nm[j], (x0 + x1) / 2, y1 + 29, 10, 1, false);
            if (j == 0) c.textRot(nm[i], 12, (y0 + y1) / 2, 10);
        }
        return c.img;
    }

    static double corr(double[] a, double[] b, int[] idx) {
        double ma = 0, mb = 0; int n = 0; for (int i : idx) if (Double.isFinite(a[i]) && Double.isFinite(b[i])) { ma += a[i]; mb += b[i]; n++; } if (n < 3) return Double.NaN; ma /= n; mb /= n;
        double sab = 0, saa = 0, sbb = 0; for (int i : idx) if (Double.isFinite(a[i]) && Double.isFinite(b[i])) { sab += (a[i] - ma) * (b[i] - mb); saa += (a[i] - ma) * (a[i] - ma); sbb += (b[i] - mb) * (b[i] - mb); }
        return saa > 0 && sbb > 0 ? sab / Math.sqrt(saa * sbb) : Double.NaN;
    }

    // ------------------------------------------------------------------ subgroup plots
    static final class Grp { String label; int[] idx; Pool pool; double est, lo, up, plo = Double.NaN, pup = Double.NaN; }

    static List<Grp> groups(MetaResult rs) {
        MetaSpec s = rs.spec; List<Grp> out = new ArrayList<>();
        for (int g = 0; g < rs.sgLevels.size(); g++) { Pool p = rs.sgPools.get(g); if (p.k == 0) continue;
            Grp q = new Grp(); q.label = rs.sgLevels.get(g); q.pool = p; List<Integer> ok = new ArrayList<>();
            for (int i : rs.sgIdx.get(g)) if (!Double.isNaN(rs.TE[i]) && !Double.isNaN(rs.seTE[i]) && rs.seTE[i] > 0) ok.add(i);
            q.idx = ok.stream().mapToInt(Integer::intValue).toArray();
            boolean rnd = s.random; q.est = av(s, rnd ? p.teRandom : p.teCommon, p.nHarm); q.lo = av(s, rnd ? p.loRandom : p.loCommon, p.nHarm); q.up = av(s, rnd ? p.upRandom : p.upCommon, p.nHarm);
            if (rnd && !Double.isNaN(p.loPred) && !Double.isNaN(p.upPred)) { q.plo = av(s, p.loPred, p.nHarm); q.pup = av(s, p.upPred, p.nHarm); }
            out.add(q); }
        return out;
    }
    static double studyN(MetaSpec s, int i) { return s.nPft != null && i < s.nPft.length ? s.nPft[i] : Double.NaN; }

    /** Axis from the study values and the pooled results of the groups (and the null value). */
    static Ax axisFor(MetaResult rs, List<Grp> gr, double x0, double x1) {
        MetaSpec s = rs.spec; double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (Grp q : gr) { for (int i : q.idx) { double v = av(s, rs.TE[i], studyN(s, i)); if (Double.isFinite(v)) { lo = Math.min(lo, v); hi = Math.max(hi, v); } }
            for (double v : new double[]{q.lo, q.up, q.plo, q.pup}) if (Double.isFinite(v)) { lo = Math.min(lo, v); hi = Math.max(hi, v); } }
        double nv = s.nullValue(); if (!Double.isNaN(nv)) { double v = av(s, nv, Double.NaN); if (Double.isFinite(v)) { lo = Math.min(lo, v); hi = Math.max(hi, v); } }
        if (!(hi > lo)) { lo -= 0.5; hi += 0.5; } double pad = (hi - lo) * 0.05;
        Ax a = new Ax(); a.x0 = x0; a.x1 = x1; a.xl = lo - pad; a.xh = hi + pad; a.log = isLog(s); return a;
    }

    /** Orchard plot (Nakagawa et al., 2021): study estimates as dots (area proportional to precision), pooled estimate of each subgroup with its confidence interval (thick) and prediction interval (thin). */
    public static BufferedImage orchard(MetaResult rs, Plots.Opts o) {
        MetaSpec s = rs.spec; List<Grp> gr = groups(rs); int G = gr.size();
        Canvas c = new Canvas(780, Math.max(300, 100 + G * 96 + 30), o.scale); Graphics2D g = c.g; double h = c.h / c.scaleF();
        if (G == 0) { c.text("Orchard plot: no subgroup results", 390, 150, 12, 1, false); return c.img; }
        final double x0 = 200, x1 = 750, y0 = 30, rowH = 96, y1 = y0 + G * rowH; Ax a = axisFor(rs, gr, x0, x1);
        double pmax = 0; for (Grp q : gr) for (int i : q.idx) pmax = Math.max(pmax, 1 / rs.seTE[i]);
        g.setStroke(new BasicStroke(1f)); g.setColor(new Color(235, 235, 235)); for (int i = 1; i < G; i++) c.line(x0, y0 + i * rowH, x1, y0 + i * rowH);
        double nv = s.nullValue(); if (!Double.isNaN(nv)) { double v = isLog(s) ? nv : av(s, nv, Double.NaN); if (v >= a.xl && v <= a.xh) { g.setColor(GRID); c.dashed(a.X(v), y0, a.X(v), y1); } }
        for (int gi = 0; gi < G; gi++) { Grp q = gr.get(gi); double cy = y0 + (gi + 0.5) * rowH;
            for (int i : q.idx) { double v = av(s, rs.TE[i], studyN(s, i)); if (!Double.isFinite(v)) continue; double rad = 2.5 + 6.5 * Math.sqrt((1 / rs.seTE[i]) / pmax), xx = a.X(v), yy = cy + jitter(i) * rowH * 0.62;
                g.setColor(new Color(110, 110, 110, 70)); g.fill(new Ellipse2D.Double(xx - rad, yy - rad, 2 * rad, 2 * rad)); g.setColor(new Color(90, 90, 90, 150)); g.setStroke(new BasicStroke(0.8f)); g.draw(new Ellipse2D.Double(xx - rad, yy - rad, 2 * rad, 2 * rad)); }
            g.setColor(BLUE);
            if (!Double.isNaN(q.plo)) { g.setStroke(new BasicStroke(1.4f)); c.line(a.X(q.plo), cy, a.X(q.pup), cy); c.line(a.X(q.plo), cy - 5, a.X(q.plo), cy + 5); c.line(a.X(q.pup), cy - 5, a.X(q.pup), cy + 5); }
            g.setStroke(new BasicStroke(5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER)); c.line(a.X(q.lo), cy, a.X(q.up), cy); g.setStroke(new BasicStroke(1f));
            g.setColor(Color.WHITE); g.fill(new Ellipse2D.Double(a.X(q.est) - 5.5, cy - 5.5, 11, 11)); g.setColor(BLUE); g.setStroke(new BasicStroke(2f)); g.draw(new Ellipse2D.Double(a.X(q.est) - 5.5, cy - 5.5, 11, 11)); g.setStroke(new BasicStroke(1f));
            g.setColor(Color.BLACK); c.text(q.label, x0 - 12, cy - 2, 12, 2, true); c.text("k = " + q.pool.k, x0 - 12, cy + 14, 10, 2, false); }
        g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1f)); c.line(x0, y0, x0, y1);
        drawXAxis(c, a, y1, Plots.xlabFor(s), false, y0);
        String ci = Fmt.num(s.levelMa * 100, 0) + "% CI"; boolean anyPi = false; for (Grp q : gr) if (!Double.isNaN(q.plo)) anyPi = true;
        c.text("Dots: studies (area proportional to precision); circle and thick bar: pooled estimate and " + ci + (anyPi ? "; thin line: " + Fmt.num(s.levelPredict * 100, 0) + "% prediction interval" : ""), x0, y1 + 62, 9, 0, false);
        return c.img;
    }

    /** Caterpillar plot: studies of each subgroup ordered by effect with their confidence intervals, followed by the pooled estimate (diamond) and prediction interval of the subgroup. */
    public static BufferedImage caterpillar(MetaResult rs, Plots.Opts o) {
        MetaSpec s = rs.spec; List<Grp> gr = groups(rs); int rows = 0; for (Grp q : gr) rows += q.idx.length + 4;
        final double rh = 16, top = 24; Canvas c = new Canvas(860, (int) (top + Math.max(rows, 3) * rh + 80), o.scale); Graphics2D g = c.g;
        if (gr.isEmpty()) { c.text("Caterpillar plot: no subgroup results", 430, 120, 12, 1, false); return c.img; }
        final double x0 = 210, x1 = 690; Ax a = axisFor(rs, gr, x0, x1); double y = top; double yTop = top;
        double nv = s.nullValue(); double nvA = Double.isNaN(nv) ? Double.NaN : isLog(s) ? nv : av(s, nv, Double.NaN);
        List<Runnable> later = new ArrayList<>();
        double alpha = 1 - s.levelMa; double zq = Dist.qnorm(1 - alpha / 2);
        for (Grp q : gr) {
            c.text(q.label + "  (k = " + q.pool.k + ")", 8, y + 11, 11, 0, true); y += rh;
            Integer[] ord = new Integer[q.idx.length]; for (int i = 0; i < ord.length; i++) ord[i] = q.idx[i];
            Arrays.sort(ord, (p1, p2) -> Double.compare(rs.TE[p2], rs.TE[p1]));   // largest effect at the top
            double wmax = 0; for (int i : q.idx) wmax = Math.max(wmax, 1 / (rs.seTE[i] * rs.seTE[i]));
            for (int i : ord) { final double yy = y + rh / 2; final int ii = i; final double wm = wmax;
                double lo = rs.TE[i] - zq * rs.seTE[i], up = rs.TE[i] + zq * rs.seTE[i]; double n = studyN(s, i);
                double ev = av(s, rs.TE[i], n), lv = av(s, lo, n), uv = av(s, up, n);
                g.setColor(Color.BLACK); c.text(rs.studlab[i], x0 - 10, yy + 4, 9, 2, false);
                g.setColor(INK); g.setStroke(new BasicStroke(1.2f)); c.line(Math.max(x0, a.X(lv)), yy, Math.min(x1, a.X(uv)), yy);
                double half = 2.5 + 3.5 * Math.sqrt((1 / (rs.seTE[i] * rs.seTE[i])) / wmax); g.setColor(Color.BLACK); g.fill(new Rectangle2D.Double(a.X(ev) - half, yy - half, 2 * half, 2 * half));
                g.setColor(Color.BLACK); c.text(Fmt.num(shown(s, ev), 2) + " " + Fmt.ci(shown(s, lv), shown(s, uv), 2), x1 + 14, yy + 4, 9, 0, false); y += rh; }
            // pooled
            final double yy = y + rh / 2; g.setColor(BLUE); Path2D d = new Path2D.Double(); d.moveTo(a.X(q.lo), yy); d.lineTo(a.X(q.est), yy - 6); d.lineTo(a.X(q.up), yy); d.lineTo(a.X(q.est), yy + 6); d.closePath(); g.fill(d);
            g.setColor(Color.BLACK); c.text(s.random ? s.textRandom : s.textCommon, x0 - 10, yy + 4, 9, 2, true); c.text(Fmt.num(shown(s, q.est), 2) + " " + Fmt.ci(shown(s, q.lo), shown(s, q.up), 2), x1 + 14, yy + 4, 9, 0, true); y += rh;
            if (!Double.isNaN(q.plo)) { double py = y + rh / 2; g.setColor(BLUE); g.setStroke(new BasicStroke(1.4f)); c.line(a.X(q.plo), py, a.X(q.pup), py); c.line(a.X(q.plo), py - 4, a.X(q.plo), py + 4); c.line(a.X(q.pup), py - 4, a.X(q.pup), py + 4); g.setStroke(new BasicStroke(1f));
                g.setColor(Color.BLACK); c.text(s.textPredict, x0 - 10, py + 4, 9, 2, false); c.text(Fmt.ci(shown(s, q.plo), shown(s, q.pup), 2), x1 + 14, py + 4, 9, 0, false); }
            y += rh;          // row of the prediction interval (empty when there is none)
            y += rh * 0.5;    // spacer
        }
        double yEnd = y - rh * 0.5;
        if (!Double.isNaN(nvA) && nvA >= a.xl && nvA <= a.xh) { g.setColor(GRID); g.setStroke(new BasicStroke(1f)); c.dashed(a.X(nvA), yTop + rh, a.X(nvA), yEnd); }
        drawXAxis(c, a, yEnd, Plots.xlabFor(s), false, yTop);
        return c.img;
    }
}
