package org.metajava.plot;

import org.metajava.core.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/**
 * Visual themes of the funnel plot and of the trim-and-fill funnel plot:
 * <ul>
 * <li>MetaWin style: sunset (power-enhanced) funnel with contour bands on a red-yellow-green power map, colour bar, dashed pseudo-confidence limits;</li>
 * <li>STATA style: pale-blue frame, navy title, light grid, grey pseudo 95% limits (or grey contour bands), crimson estimate line, framed legend.</li>
 * </ul>
 * The Default theme (meta style) is drawn by {@link Plots#funnel}.
 */
public final class FunnelThemes {
    private FunnelThemes() {}
    public static final String DEFAULT = "Default", METAWIN = "MetaWin style", STATA = "STATA style";
    public static final String[] NAMES = {DEFAULT, METAWIN, STATA};

    static final Color NAVY = new Color(26, 71, 111), CRIMSON = new Color(165, 24, 60), ORANGE = new Color(222, 125, 0), PALE = new Color(234, 242, 243), TABBLUE = new Color(31, 119, 180), TABORANGE = new Color(255, 127, 14);
    static final double Z90 = Dist.qnorm(0.95), Z95 = Dist.qnorm(0.975), Z99 = Dist.qnorm(0.995);

    /** What every funnel theme needs: studies (with the filled ones of a trim-and-fill), centre, extent. */
    static final class D {
        MetaSpec s; boolean log; double[] te, se; String[] lab; boolean[] fl; double center, ref, maxse; boolean tf;
        D(MetaResult r, TrimFill tf) {
            s = r.spec; log = s.backtransf && s.isRatioSm(); this.tf = tf != null;
            te = tf != null ? tf.TE : r.TE; se = tf != null ? tf.seTE : r.seTE; lab = tf != null ? tf.studlab : r.studlab; fl = tf != null ? tf.filled : null;
            center = tf != null ? (s.random ? tf.pool.teRandom : tf.pool.teCommon) : (s.random ? r.overall.teRandom : r.overall.teCommon);
            ref = s.nullValue(); if (Double.isNaN(ref)) ref = 0;
            for (int i = 0; i < te.length; i++) if (!Double.isNaN(te[i]) && !Double.isNaN(se[i])) maxse = Math.max(maxse, se[i]);
            if (maxse <= 0) maxse = 1;
        }
        boolean filled(int i) { return fl != null && fl[i]; }
        boolean ok(int i) { return !Double.isNaN(te[i]) && !Double.isNaN(se[i]); }
        String thetaSub() { return s.random ? s.methodTau : "IV"; }
    }

    /** Ticks on the effect scale: for ratio measures the labels are exp(value) at 1-2-5 positions, otherwise pretty numbers. Returns {value, label-number} pairs. */
    static double[][] ticks(double lo, double hi, boolean log, int n) {
        double[] t = log ? Plots.logTicks(Math.exp(lo), Math.exp(hi)) : Canvas.pretty(lo, hi, n);
        if (log) { int inside = 0; for (double x : t) { double v = Math.log(x); if (v >= lo && v <= hi) inside++; } if (inside < 2) { t = Canvas.pretty(Math.exp(lo), Math.exp(hi), 5); } }
        List<double[]> out = new ArrayList<>(); for (double x : t) { double v = log ? Math.log(x) : x; if (v >= lo - 1e-9 && v <= hi + 1e-9) out.add(new double[]{v, x}); }
        return out.toArray(new double[0][]);
    }
    static String stataNum(double v) { return Canvas.tick(v).replaceFirst("^(-?)0\\.", "$1."); }

    static Path2D tri(double xa, double ya, double xb, double yb, double xc, double yc) { Path2D p = new Path2D.Double(); p.moveTo(xa, ya); p.lineTo(xb, yb); p.lineTo(xc, yc); p.closePath(); return p; }
    static Color alpha(Color c, double a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) Math.round(255 * a)); }

    /** Study labels next to the points (to the left of a point that is close to the right edge of the plot area). */
    static void labels(Canvas c, D d, java.util.function.DoubleUnaryOperator X, java.util.function.DoubleUnaryOperator Y, double xmax, Color col) {
        FontMetrics fm = c.g.getFontMetrics(Canvas.font(9f)); c.g.setColor(col);
        for (int i = 0; i < d.te.length; i++) { if (!d.ok(i) || d.lab[i] == null) continue; double xx = X.applyAsDouble(d.te[i]), yy = Y.applyAsDouble(d.se[i]), w = fm.stringWidth(d.lab[i]);
            if (xx + 6 + w > xmax) c.text(d.lab[i], xx - 6, yy - 4, 9f, 2, false); else c.text(d.lab[i], xx + 6, yy - 4, 9f, 0, false); }
    }

    // =================================================================== STATA
    static BufferedImage stata(MetaResult r, Plots.Opts o, TrimFill tf) {
        D d = new D(r, tf); final boolean con = o.contour; MetaSpec s = d.s; final double z = Z95;
        // ---- legend items {kind, text}, filled row by row in two columns
        List<String[]> leg = new ArrayList<>();
        if (con) { leg.add(new String[]{"b1", "1% < p < 5%"}); leg.add(new String[]{"b2", "5% < p < 10%"}); leg.add(new String[]{"b3", "p > 10%"}); } else leg.add(new String[]{"ci", "Pseudo 95% CI"});
        leg.add(new String[]{"dot", d.tf ? "Observed studies" : "Studies"}); leg.add(new String[]{"est", "Estimated θ"}); if (d.tf) leg.add(new String[]{"imp", "Imputed studies"});
        Canvas probe = Canvas.probe(); FontMetrics f12 = probe.g.getFontMetrics(Canvas.font(12f)), f9 = probe.g.getFontMetrics(Canvas.font(9f));
        int colW = 0; for (String[] it : leg) { int w = f12.stringWidth(it[1]) + (it[0].equals("est") ? f9.stringWidth(d.thetaSub()) + 2 : 0); colW = Math.max(colW, 44 + w); }
        int rows = (leg.size() + 1) / 2, legW = 2 * colW + 30, legH = rows * 22 + 14;
        int W = Math.max(640, legW + 120), x0 = 84, x1 = W - 28, y0 = 62, y1 = y0 + 320, legTop = y1 + 62, H = legTop + legH + 20;
        // ---- ranges
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < d.te.length; i++) if (d.ok(i)) { lo = Math.min(lo, d.te[i]); hi = Math.max(hi, d.te[i]); }
        double mid = con ? d.ref : d.center; lo = Math.min(lo, mid - z * d.maxse); hi = Math.max(hi, mid + z * d.maxse); if (con) { lo = Math.min(lo, d.center); hi = Math.max(hi, d.center); }
        double padx = (hi - lo) * 0.05; lo -= padx; hi += padx; { double[] cl = Plots.clampRaw(s, lo, hi); lo = cl[0]; hi = cl[1]; } final double xl = lo, xh = hi, yh = d.maxse * (con ? 1.14 : 1.07), yEnd = d.maxse * 1.04;   // contour-enhanced: the bands stop just below the lowest study, leaving white space down to the axis (as Stata does)   // contour-enhanced: the bands stop just below the lowest study, leaving white space down to the axis (as Stata does)
        java.util.function.DoubleUnaryOperator X = v -> x0 + (v - xl) / (xh - xl) * (x1 - x0), Y = v -> y0 + v / yh * (y1 - y0);
        Canvas c = new Canvas(W, H, o.scale); Graphics2D g = c.g;
        g.setColor(PALE); g.fill(new Rectangle2D.Double(0, 0, W, H));
        g.setColor(NAVY); c.text(con ? "Contour-enhanced funnel plot" : "Funnel plot", (x0 + x1) / 2.0, 36, 18f, 1, false);
        g.setColor(Color.WHITE); g.fill(new Rectangle2D.Double(x0, y0, x1 - x0, y1 - y0));
        Shape clip0 = g.getClip(); g.setClip(new Rectangle2D.Double(x0, y0, x1 - x0, y1 - y0));
        double[] yt = Canvas.pretty(0, yh, 5);
        g.setColor(new Color(224, 235, 238)); g.setStroke(new BasicStroke(1.5f)); for (double t : yt) if (t >= 0 && t <= yh + 1e-9) c.line(x0, Y.applyAsDouble(t), x1, Y.applyAsDouble(t));
        if (con) { double[] zs = {Z99, Z95, Z90}; Color[] cols = {new Color(208, 208, 208), new Color(160, 160, 160), new Color(105, 105, 105)};
            for (int i = 0; i < 3; i++) g.setColor(cols[i]) ; for (int i = 0; i < 3; i++) { g.setColor(cols[i]); g.fill(tri(X.applyAsDouble(d.ref - zs[i] * yEnd), Y.applyAsDouble(yEnd), X.applyAsDouble(d.ref), y0, X.applyAsDouble(d.ref + zs[i] * yEnd), Y.applyAsDouble(yEnd))); } }
        else { g.setColor(new Color(190, 190, 190)); g.setStroke(new BasicStroke(1.8f)); double yb = Y.applyAsDouble(d.maxse); c.line(X.applyAsDouble(d.center), y0, X.applyAsDouble(d.center - z * d.maxse), yb); c.line(X.applyAsDouble(d.center), y0, X.applyAsDouble(d.center + z * d.maxse), yb); }
        g.setColor(CRIMSON); g.setStroke(new BasicStroke(1.8f)); c.line(X.applyAsDouble(d.center), y0, X.applyAsDouble(d.center), Y.applyAsDouble(d.maxse));
        // studies
        for (int i = 0; i < d.te.length; i++) { if (!d.ok(i)) continue; double xx = X.applyAsDouble(d.te[i]), yy = Y.applyAsDouble(d.se[i]);
            g.setColor(d.filled(i) ? ORANGE : NAVY); g.fill(new Ellipse2D.Double(xx - 3.8, yy - 3.8, 7.6, 7.6));
        }
        g.setClip(clip0); if (o.studyLabels) labels(c, d, X, Y, x1, new Color(40, 40, 40));
        // axes (left and bottom line, outward ticks)
        g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1.2f)); c.line(x0, y0, x0, y1); c.line(x0, y1, x1, y1);
        for (double t : yt) { if (t < 0 || t > yh + 1e-9) continue; double yy = Y.applyAsDouble(t); c.line(x0 - 5, yy, x0, yy); c.textRot(stataNum(t), x0 - 12, yy, 13f); }
        for (double[] t : ticks(xl, xh, d.log, 6)) { double xx = X.applyAsDouble(t[0]); c.line(xx, y1, xx, y1 + 5); c.text(stataNum(t[1]), xx, y1 + 22, 13f, 1, false); }
        c.text(Plots.funnelXlab(s), (x0 + x1) / 2.0, y1 + 44, 13f, 1, false); c.textRot("Standard error", 22, (y0 + y1) / 2.0, 13f);
        // legend
        double lx = (W - legW) / 2.0; g.setColor(Color.WHITE); g.fill(new Rectangle2D.Double(lx, legTop, legW, legH)); g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1f)); g.draw(new Rectangle2D.Double(lx, legTop, legW, legH));
        for (int k = 0; k < leg.size(); k++) {
            double cx = lx + 14 + (k % 2) * (colW + 2), cy = legTop + 14 + (k / 2) * 22 + 3; String kind = leg.get(k)[0];
            switch (kind) {
                case "b1": g.setColor(new Color(208, 208, 208)); g.fill(new Rectangle2D.Double(cx, cy - 7, 30, 14)); break;
                case "b2": g.setColor(new Color(160, 160, 160)); g.fill(new Rectangle2D.Double(cx, cy - 7, 30, 14)); break;
                case "b3": g.setColor(new Color(105, 105, 105)); g.fill(new Rectangle2D.Double(cx, cy - 7, 30, 14)); break;
                case "ci": g.setColor(new Color(190, 190, 190)); g.setStroke(new BasicStroke(1.8f)); c.line(cx, cy, cx + 30, cy); break;
                case "est": g.setColor(CRIMSON); g.setStroke(new BasicStroke(1.8f)); c.line(cx, cy, cx + 30, cy); break;
                case "dot": g.setColor(NAVY); g.fill(new Ellipse2D.Double(cx + 11, cy - 4, 8, 8)); break;
                default: g.setColor(ORANGE); g.fill(new Ellipse2D.Double(cx + 11, cy - 4, 8, 8));
            }
            g.setColor(Color.BLACK); c.text(leg.get(k)[1], cx + 38, cy + 4, 12f, 0, false);
            if (kind.equals("est")) c.text(d.thetaSub(), cx + 38 + f12.stringWidth(leg.get(k)[1]) + 1, cy + 7, 9f, 0, false);
        }
        return c.img;
    }

    // =================================================================== MetaWin
    /** RdYlGn colour map (ColorBrewer, 11 classes), t in [0, 1]: 0 dark red, 1 dark green. */
    static Color power(double t) {
        int[][] k = {{165, 0, 38}, {215, 48, 39}, {244, 109, 67}, {253, 174, 97}, {254, 224, 139}, {255, 255, 191}, {217, 239, 139}, {166, 217, 106}, {102, 189, 99}, {26, 152, 80}, {0, 104, 55}};
        t = Math.max(0, Math.min(1, t)); double p = t * 10; int i = Math.min(9, (int) Math.floor(p)); double f = p - i;
        return new Color((int) Math.round(k[i][0] + f * (k[i + 1][0] - k[i][0])), (int) Math.round(k[i][1] + f * (k[i + 1][1] - k[i][1])), (int) Math.round(k[i][2] + f * (k[i + 1][2] - k[i][2])));
    }
    /** Power of a two-sided 5% z-test to detect an effect of size |theta| with a study of standard error se. */
    static double powerAt(double theta, double se) { double m = Math.abs(theta) / se; return Dist.pnorm(-Z95 + m) + Dist.pnorm(-Z95 - m); }

    static BufferedImage metawin(MetaResult r, Plots.Opts o, TrimFill tf) {
        D d = new D(r, tf); MetaSpec s = d.s;
        final int W = 800, H = 440, x0 = 66, x1 = W - 118, y0 = 14, y1 = H - 58, cb0 = W - 84, cb1 = cb0 + 22;
        double yh = d.maxse * 1.15;
        double lo = d.ref - Z99 * yh, hi = d.ref + Z99 * yh; for (int i = 0; i < d.te.length; i++) if (d.ok(i)) { lo = Math.min(lo, d.te[i]); hi = Math.max(hi, d.te[i]); }
        double pad = (hi - lo) * 0.012; lo -= pad; hi += pad; { double[] cl = Plots.clampRaw(s, lo, hi); lo = cl[0]; hi = cl[1]; } final double xl = lo, xh = hi;
        java.util.function.DoubleUnaryOperator X = v -> x0 + (v - xl) / (xh - xl) * (x1 - x0), Y = v -> y0 + v / yh * (y1 - y0);
        Canvas c = new Canvas(W, H, o.scale); Graphics2D g = c.g; Shape clip0 = g.getClip();
        g.setClip(new Rectangle2D.Double(x0, y0, x1 - x0, y1 - y0));
        // power map: colour of a row = power of a study with that standard error to detect the pooled effect
        for (int py = 0; py < (y1 - y0); py++) { double se = (py + 0.5) / (y1 - y0) * yh; g.setColor(power(powerAt(d.center, se))); g.fill(new Rectangle2D.Double(x0, y0 + py, x1 - x0, 1.6)); }
        // contour bands around the null value: p > 10% (clear), 5-10%, 1-5%, < 1%
        Area panel = new Area(new Rectangle2D.Double(x0, y0, x1 - x0, y1 - y0)), f90 = new Area(funnel(X, d.ref, Z90, yh, y0, y1)), f95 = new Area(funnel(X, d.ref, Z95, yh, y0, y1)), f99 = new Area(funnel(X, d.ref, Z99, yh, y0, y1));
        Area bB = new Area(f95); bB.subtract(f90); Area bC = new Area(f99); bC.subtract(f95); Area bD = new Area(panel); bD.subtract(f99);
        g.setColor(new Color(150, 150, 150, 110)); g.fill(bB); g.setColor(new Color(200, 200, 200, 95)); g.fill(bC); g.setColor(new Color(255, 255, 255, 115)); g.fill(bD);
        // pseudo confidence limits (dashed) and pooled effect (dotted)
        Color lg = new Color(196, 196, 196); double xc = X.applyAsDouble(d.center);
        g.setColor(lg); g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{6f, 4f}, 0));
        c.line(xc, y0, X.applyAsDouble(d.center - Z95 * yh), y1); c.line(xc, y0, X.applyAsDouble(d.center + Z95 * yh), y1);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{1.5f, 3f}, 0)); c.line(xc, y0, xc, y1);
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i < d.te.length; i++) { if (!d.ok(i)) continue; double xx = X.applyAsDouble(d.te[i]), yy = Y.applyAsDouble(d.se[i]);
            g.setColor(d.filled(i) ? TABORANGE : TABBLUE); g.fill(new Ellipse2D.Double(xx - 3.8, yy - 3.8, 7.6, 7.6));
        }
        g.setClip(clip0); if (o.studyLabels) labels(c, d, X, Y, x1, new Color(30, 30, 30));
        // legend of a trim-and-fill plot
        if (d.tf) { double bx = x1 - 132, by = y0 + 8; g.setColor(new Color(255, 255, 255, 215)); g.fill(new Rectangle2D.Double(bx, by, 124, 40)); g.setColor(new Color(150, 150, 150)); g.draw(new Rectangle2D.Double(bx, by, 124, 40));
            g.setColor(TABBLUE); g.fill(new Ellipse2D.Double(bx + 8, by + 8, 7.6, 7.6)); g.setColor(TABORANGE); g.fill(new Ellipse2D.Double(bx + 8, by + 25, 7.6, 7.6));
            g.setColor(Color.BLACK); c.text("Observed studies", bx + 22, by + 16, 10f, 0, false); c.text("Imputed studies", bx + 22, by + 33, 10f, 0, false); }
        // axes: left and bottom spines, outward ticks
        g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1f)); c.line(x0, y0, x0, y1); c.line(x0, y1, x1, y1);
        for (double t : Canvas.pretty(0, yh, 6)) { if (t < 0 || t > yh + 1e-9) continue; double yy = Y.applyAsDouble(t); c.line(x0 - 4, yy, x0, yy); c.text(Canvas.tick(t), x0 - 8, yy + 4, 11f, 2, false); }
        for (double[] t : ticks(xl, xh, d.log, 8)) { double xx = X.applyAsDouble(t[0]); c.line(xx, y1, xx, y1 + 4); c.text(Canvas.tick(t[1]), xx, y1 + 18, 11f, 1, false); }   // ratio measures: axis labelled on the OR / HR scale (positions stay logarithmic)
        c.text(Plots.funnelXlab(s), (x0 + x1) / 2.0, y1 + 40, 12f, 1, false); c.textRot("standard error", 16, (y0 + y1) / 2.0, 12f);
        // colour bar
        for (int py = 0; py < (y1 - y0); py++) { g.setColor(power(1 - (py + 0.5) / (y1 - y0))); g.fill(new Rectangle2D.Double(cb0, y0 + py, cb1 - cb0, 1.6)); }
        g.setColor(Color.BLACK); for (int t = 0; t <= 100; t += 20) { double yy = y1 - t / 100.0 * (y1 - y0); c.line(cb1, yy, cb1 + 4, yy); c.text(String.valueOf(t), cb1 + 7, yy + 4, 11f, 0, false); }
        c.textRot("Power", cb1 + 50, (y0 + y1) / 2.0, 12f);
        return c.img;
    }
    private static Path2D funnel(java.util.function.DoubleUnaryOperator X, double ref, double zc, double yh, double y0, double y1) { return tri(X.applyAsDouble(ref - zc * yh), y1, X.applyAsDouble(ref), y0, X.applyAsDouble(ref + zc * yh), y1); }
}
