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
    public static BufferedImage gosh(Gosh gs, MetaResult r, Plots.Opts o, boolean highlight) {
        MetaSpec s = r.spec; boolean log = isLog(s); Canvas c = new Canvas(720, 640, o.scale); Graphics2D g = c.g;
        final double fx0 = 72, fx1 = 600, fy0 = 112, fy1 = 548;
        int n = gs.nSub; double[] xs = new double[n]; double xmin = Double.POSITIVE_INFINITY, xmax = Double.NEGATIVE_INFINITY;
        for (int b = 0; b < n; b++) { xs[b] = Double.isNaN(gs.est[b]) ? Double.NaN : av(s, gs.est[b], gs.nHarm == null ? r.overall.nHarm : gs.nHarm[b]); if (!Double.isNaN(xs[b]) && !Double.isNaN(gs.i2[b])) { xmin = Math.min(xmin, xs[b]); xmax = Math.max(xmax, xs[b]); } }
        double full = av(s, gs.fullEst, r.overall.nHarm); xmin = Math.min(xmin, full); xmax = Math.max(xmax, full);
        if (!(xmax > xmin)) { double m = Double.isFinite(xmin) ? xmin : 0; xmin = m - 0.5; xmax = m + 0.5; }
        double pad = (xmax - xmin) * 0.05; Ax a = new Ax(); a.x0 = fx0; a.x1 = fx1; a.xl = xmin - pad; a.xh = xmax + pad; a.log = log;
        double yl = 0, yh = 100;
        java.util.function.DoubleUnaryOperator Y = v -> fy1 - (v - yl) / (yh - yl) * (fy1 - fy0);
        // points or density
        int hl = highlight ? gs.mostInfluential() : -1;
        int[] counts = null; int nx = (int) Math.round((fx1 - fx0) / 4), ny = (int) Math.round((fy1 - fy0) / 4);
        boolean dens = !highlight && n > 6000;
        Shape clip = g.getClip(); g.setClip(new Rectangle2D.Double(fx0, fy0, fx1 - fx0, fy1 - fy0));
        if (dens) {
            counts = new int[nx * ny]; int mx = 0;
            for (int b = 0; b < n; b++) { if (Double.isNaN(xs[b]) || Double.isNaN(gs.i2[b])) continue; int ix = (int) Math.min(nx - 1, Math.max(0, (xs[b] - a.xl) / (a.xh - a.xl) * nx)), iy = (int) Math.min(ny - 1, Math.max(0, (gs.i2[b] - yl) / (yh - yl) * ny)); int v = ++counts[iy * nx + ix]; if (v > mx) mx = v; }
            double cw = (fx1 - fx0) / nx, ch = (fy1 - fy0) / ny;
            for (int iy = 0; iy < ny; iy++) for (int ix = 0; ix < nx; ix++) { int v = counts[iy * nx + ix]; if (v == 0) continue; double t = Math.log1p(v) / Math.log1p(mx);
                g.setColor(ramp(t)); g.fill(new Rectangle2D.Double(fx0 + ix * cw - 0.3, fy1 - (iy + 1) * ch - 0.3, cw + 0.6, ch + 0.6)); }
        } else {
            int step = Math.max(1, (int) Math.ceil(n / 8000.0));
            for (int pass = 0; pass < (highlight ? 2 : 1); pass++) for (int b = 0; b < n; b += step) { if (Double.isNaN(xs[b]) || Double.isNaN(gs.i2[b])) continue;
                boolean in = highlight && gs.includes(b, hl); if (highlight && (pass == 0) == in) continue;
                g.setColor(!highlight ? new Color(BLUE.getRed(), BLUE.getGreen(), BLUE.getBlue(), 70) : in ? new Color(BLUE.getRed(), BLUE.getGreen(), BLUE.getBlue(), 80) : new Color(ORANGE.getRed(), ORANGE.getGreen(), ORANGE.getBlue(), 90));
                double xx = a.X(xs[b]), yy = Y.applyAsDouble(gs.i2[b]); g.fill(new Ellipse2D.Double(xx - 2.2, yy - 2.2, 4.4, 4.4)); }
        }
        g.setStroke(new BasicStroke(1f)); g.setColor(GRID); c.dashed(a.X(full), fy0, a.X(full), fy1); c.dashed(fx0, Y.applyAsDouble(gs.fullI2), fx1, Y.applyAsDouble(gs.fullI2));
        g.setClip(clip);
        // frame and axes
        g.setColor(Color.BLACK); g.draw(new Rectangle2D.Double(fx0, fy0, fx1 - fx0, fy1 - fy0));
        drawXAxis(c, a, fy1, Plots.xlabFor(s), false, fy0);
        for (double t : Canvas.pretty(yl, yh, 5)) { if (t < yl - 1e-9 || t > yh + 1e-9) continue; double yy = Y.applyAsDouble(t); c.line(fx0 - 5, yy, fx0, yy); c.textRot(Canvas.tick(t), fx0 - 10, yy, 10); }
        c.textRot("I² (%)", 16, (fy0 + fy1) / 2, 11);
        // marginal densities
        int nb = 50; double[] hx = new double[nb], hy = new double[nb];
        for (int b = 0; b < n; b++) { if (Double.isNaN(xs[b]) || Double.isNaN(gs.i2[b])) continue; hx[(int) Math.min(nb - 1, Math.max(0, (xs[b] - a.xl) / (a.xh - a.xl) * nb))]++; hy[(int) Math.min(nb - 1, Math.max(0, (gs.i2[b] - yl) / (yh - yl) * nb))]++; }
        hx = smooth(hx); hy = smooth(hy); double mx = 0, my = 0; for (double v : hx) mx = Math.max(mx, v); for (double v : hy) my = Math.max(my, v);
        Path2D top = new Path2D.Double(); top.moveTo(fx0, fy0 - 4);
        for (int i = 0; i < nb; i++) { double xx = fx0 + (i + 0.5) / nb * (fx1 - fx0); top.lineTo(xx, fy0 - 4 - (mx > 0 ? hx[i] / mx : 0) * 58); } top.lineTo(fx1, fy0 - 4); top.closePath();
        g.setColor(new Color(0xD9, 0xE2, 0xF0)); g.fill(top); g.setColor(BLUE); g.setStroke(new BasicStroke(1f)); g.draw(top);
        Path2D rt = new Path2D.Double(); rt.moveTo(fx1 + 4, fy1);
        for (int i = 0; i < nb; i++) { double yy = fy1 - (i + 0.5) / nb * (fy1 - fy0); rt.lineTo(fx1 + 4 + (my > 0 ? hy[i] / my : 0) * 62, yy); } rt.lineTo(fx1 + 4, fy0); rt.closePath();
        g.setColor(new Color(0xD9, 0xE2, 0xF0)); g.fill(rt); g.setColor(BLUE); g.draw(rt);
        g.setColor(Color.BLACK);
        // legend / notes
        String note = String.format(Locale.ROOT, "%,d %ssubsets", n, gs.exhaustive ? "(all) " : "random ");
        c.text(note, fx0 + 8, fy0 + 16, 10, 0, false);
        if (highlight) { double ly = fy0 + 32; g.setColor(new Color(BLUE.getRed(), BLUE.getGreen(), BLUE.getBlue(), 160)); g.fill(new Ellipse2D.Double(fx0 + 8, ly - 7, 8, 8)); g.setColor(Color.BLACK); c.text("includes " + gs.labels[hl], fx0 + 22, ly, 10, 0, false);
            ly += 15; g.setColor(new Color(ORANGE.getRed(), ORANGE.getGreen(), ORANGE.getBlue(), 190)); g.fill(new Ellipse2D.Double(fx0 + 8, ly - 7, 8, 8)); g.setColor(Color.BLACK); c.text("excludes " + gs.labels[hl], fx0 + 22, ly, 10, 0, false); }
        c.text("dashed lines: all " + gs.k + " studies", fx1 - 6, fy1 - 8, 9, 2, false);
        return c.img;
    }

    /** Sequential single-hue ramp (light to dark blue) for the density of the GOSH points. */
    static Color ramp(double t) {
        t = Math.max(0, Math.min(1, t)); int[] lo = {0xE3, 0xEC, 0xF7}, hi = {0x0B, 0x2D, 0x63};
        return new Color((int) Math.round(lo[0] + (hi[0] - lo[0]) * t), (int) Math.round(lo[1] + (hi[1] - lo[1]) * t), (int) Math.round(lo[2] + (hi[2] - lo[2]) * t));
    }
    static double[] smooth(double[] h) { double[] o = new double[h.length]; for (int i = 0; i < h.length; i++) { double s = 0; int c = 0; for (int d = -2; d <= 2; d++) { int j = i + d; if (j >= 0 && j < h.length) { s += h[j]; c++; } } o[i] = s / c; } return o; }

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
