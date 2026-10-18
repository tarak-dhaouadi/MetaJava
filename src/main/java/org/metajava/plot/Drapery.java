package org.metajava.plot;

import org.metajava.core.*;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/**
 * Drapery plot: (scaled) p-value curves of the individual studies and of the meta-analysis estimates, with the prediction region.
 * A port of meta::drapery (meta 8.5-0; Rucker and Schwarzer): same grid, same curves, same polygons for the prediction region,
 * same alpha lines, same right-hand "Confidence level" axis, same legend. Relative effects (OR, RR, HR, IRR, DOR, ROM) are always back-transformed
 * (log scale on the x-axis); every other measure is shown on the scale on which it was analysed, as in meta.
 */
public final class Drapery {
    private Drapery() {}

    public static final String ZVALUE = "z-value", PVALUE = "p-value";
    public static final String GRAYSCALE = "grayscale", EQUAL = "equal", LINEWIDTH = "linewidth";
    public static final String LAB_NONE = "None", LAB_ID = "id", LAB_NAME = "study name";
    public static final String MODEL_AUTO = "As main analysis", MODEL_RANDOM = "Random", MODEL_COMMON = "Common", MODEL_BOTH = "Both";
    public static final String COL_DEFAULT = "Default";
    public static final String[] TYPES = {ZVALUE, PVALUE}, LAYOUTS = {GRAYSCALE, EQUAL, LINEWIDTH}, LABELS = {LAB_NONE, LAB_ID, LAB_NAME}, MODELS = {MODEL_AUTO, MODEL_RANDOM, MODEL_COMMON, MODEL_BOTH};
    /** R colour names (rgb values of grDevices::colors()). */
    private static final String[] NAMES = {"Dark gray", "Gray", "Light gray", "Black", "Red", "Dark red", "Blue", "Navy", "Light blue", "Steel blue", "Green", "Dark green", "Orange", "Purple", "Brown", "Pink"};
    private static final int[] RGB = {0xA9A9A9, 0xBEBEBE, 0xD3D3D3, 0x000000, 0xFF0000, 0x8B0000, 0x0000FF, 0x000080, 0xADD8E6, 0x4682B4, 0x00CD00, 0x006400, 0xFFA500, 0xA020F0, 0xA52A2A, 0xFFC0CB};
    public static final String[] STUDY_COLOURS = prepend(NAMES, COL_DEFAULT), COLOURS = NAMES;
    public static final String STUDY_DEFAULT = COL_DEFAULT, PRED_DEFAULT = "Light blue", RANDOM_DEFAULT = "Red", COMMON_DEFAULT = "Blue";

    private static String[] prepend(String[] a, String first) { String[] o = new String[a.length + 1]; o[0] = first; System.arraycopy(a, 0, o, 1, a.length); return o; }
    /** Colour of a name from the lists above (null for "Default" or an unknown name). */
    public static Color colour(String name) { for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equals(name)) return new Color(RGB[i]); return null; }
    private static Color colour(String name, Color dflt) { Color c = colour(name); return c != null ? c : dflt; }

    /** Which curves the plot draws (the report of {@link DraperyReport} reads the same decision, so the two never disagree). randomSe0: the random-effects result is requested and finite, but its SE is exactly 0, so its pooled curve cannot be drawn. */
    public static final class Shown { public boolean random, common, okRandom, okCommon, pred, randomSe0; public String predReason = ""; }

    public static Shown shown(MetaResult r, Plots.Opts o) {
        MetaSpec s = r.spec; Pool P = r.overall; Shown h = new Shown(); boolean showRandom, showCommon; String mo = o.drapModel == null ? MODEL_AUTO : o.drapModel;
        switch (mo) { case MODEL_RANDOM: showRandom = true; showCommon = false; break; case MODEL_COMMON: showRandom = false; showCommon = true; break; case MODEL_BOTH: showRandom = showCommon = true; break; default: showRandom = s.random; showCommon = s.common; }
        if (!showRandom && !showCommon) showRandom = true;   // nothing selected in the analysis: random effects, as the default of the analysis
        boolean okRandom = !Double.isNaN(P.teRandom) && !Double.isNaN(P.seRandom) && P.seRandom > 0, okCommon = !Double.isNaN(P.teCommon) && !Double.isNaN(P.seCommon) && P.seCommon > 0;
        boolean se0 = showRandom && !okRandom && !Double.isNaN(P.teRandom) && !Double.isInfinite(P.teRandom) && P.seRandom == 0;
        showRandom &= okRandom; showCommon &= okCommon;
        double sePred = P.sePred, tq = (P.upPred - P.loPred) / sePred / 2;
        boolean predOk = !Double.isNaN(sePred) && sePred > 0 && !Double.isNaN(tq) && !Double.isInfinite(tq);
        h.pred = o.drapPrediction && showRandom && okRandom && predOk;
        if (!h.pred) h.predReason = !o.drapPrediction ? "the option \"Prediction region\" is turned off" : !showRandom ? (se0 ? "the random-effects pooled curve cannot be drawn (its standard error is 0), and the prediction region is drawn around that curve" : "the random-effects curve is not displayed (the prediction region belongs to the random-effects model)") : "no standard-error-based prediction interval is available (it needs enough studies for the chosen method, and the bootstrap method of Nagashima et al. gives no SE)";
        h.randomSe0 = se0; h.random = showRandom; h.common = showCommon; h.okRandom = okRandom; h.okCommon = okCommon; return h;
    }

    /** Null effect on the working scale of the plot (log scale for relative effects), NaN when the measure has none. */
    public static double nullOf(MetaSpec s) {
        boolean rel = relative(s.sm); double nullEffect = Double.NaN; { double nv = s.nullValue(); if (!Double.isNaN(nv)) nullEffect = nv; else if (rel || "RD".equals(s.sm) || "MD".equals(s.sm) || "SMD".equals(s.sm)) nullEffect = 0; }
        if (rel && !Double.isNaN(s.nullEffect) && !s.backtransf) nullEffect = s.nullEffect;   // ratio measures analysed on the log scale: the null value is given on that scale
        return nullEffect;
    }

    /** meta's relative effects: shown back-transformed, on a log-scaled x-axis. */
    public static boolean relative(String sm) { return Arrays.asList("HR", "OR", "RR", "IRR", "ROM", "DOR").contains(sm); }

    /** R's gray.colors(n): start = 0.3, end = 0.9, gamma = 2.2. */
    static Color[] grayColors(int n) {
        Color[] c = new Color[n]; double a = Math.pow(0.3, 2.2), b = Math.pow(0.9, 2.2);
        for (int i = 0; i < n; i++) { double v = n == 1 ? a : a + (b - a) * i / (n - 1); int g = (int) Math.round(255 * Math.pow(v, 1 / 2.2)); c[i] = new Color(g, g, g); }
        return c;
    }

    /** Test statistic (z) or p-value of the curve with estimate te and standard error se at x. */
    static double curve(boolean z, double x, double te, double se) { double d = Math.abs(te - x) / se; return z ? -d : 2 * Dist.pnorm(-d); }

    public static BufferedImage plot(MetaResult r, Plots.Opts o) {
        MetaSpec s = r.spec; Pool P = r.overall; final boolean z = !PVALUE.equals(o.drapType); final boolean rel = relative(s.sm);
        Canvas c = new Canvas(780, 520, o.scale); Graphics2D g = c.g;
        int n = r.TE.length; List<Integer> ok = new ArrayList<>();
        for (int i = 0; i < n; i++) if (!Double.isNaN(r.TE[i]) && !Double.isInfinite(r.TE[i]) && !Double.isNaN(r.seTE[i]) && r.seTE[i] > 0) ok.add(i);
        // ---- model(s) shown: as the analysis (meta: common = x$common, random = x$random), or chosen
        Shown sh = shown(r, o); boolean showRandom = sh.random, showCommon = sh.common, okRandom = sh.okRandom, okCommon = sh.okCommon, showPred = sh.pred;
        double sePred = P.sePred, tq = (P.upPred - P.loPred) / sePred / 2;   // meta: t.quantile = (upper.predict - lower.predict) / seTE.predict / 2
        if (ok.isEmpty()) { c.text("Drapery plot not available (no study with an estimate and a standard error)", 390, 260, 12, 1, false); return c.img; }

        // ---- variables for plotting (meta: section 2)
        double[] ylim = z ? new double[]{Dist.qnorm(0.0005), 0} : new double[]{0, 1};
        double mn = 1e300, mx = -1e300; for (int i : ok) { if (!Double.isNaN(r.lower[i])) mn = Math.min(mn, r.lower[i]); if (!Double.isNaN(r.upper[i])) mx = Math.max(mx, r.upper[i]); }
        if (mn > mx) { mn = 1e300; mx = -1e300; for (int i : ok) { mn = Math.min(mn, r.TE[i] - 1.96 * r.seTE[i]); mx = Math.max(mx, r.TE[i] + 1.96 * r.seTE[i]); } }
        int nGrid = z ? 400 : 1000;   // the curves of test statistics are straight lines with kinks at the estimates and the cut-offs, which are part of the grid: a coarse grid draws them exactly
        List<Double> gl = new ArrayList<>(); for (int i = 0; i < nGrid; i++) gl.add(mn + (mx - mn) * i / (nGrid - 1));
        for (int i : ok) { gl.add(r.TE[i]); if (z) { gl.add(r.TE[i] + ylim[0] * r.seTE[i]); gl.add(r.TE[i] - ylim[0] * r.seTE[i]); } }
        if (okCommon) { gl.add(P.teCommon); if (z) { gl.add(P.teCommon + ylim[0] * P.seCommon); gl.add(P.teCommon - ylim[0] * P.seCommon); } }
        if (showPred && z) { double sp = tq / Dist.qnorm(0.975) * sePred; gl.add(P.teRandom + ylim[0] * sp); gl.add(P.teRandom - ylim[0] * sp); }
        if (okRandom) { gl.add(P.teRandom); if (z) { gl.add(P.teRandom + ylim[0] * P.seRandom); gl.add(P.teRandom - ylim[0] * P.seRandom); } }
        double[] grid = gl.stream().filter(v -> !Double.isNaN(v) && !Double.isInfinite(v)).mapToDouble(Double::doubleValue).sorted().toArray(); int G = grid.length;
        double nullEffect = nullOf(s);

        // study order: by standard error (meta: o <- order(x$seTE)); the most precise study is drawn last, i.e. on top
        Integer[] ord = ok.toArray(new Integer[0]); Arrays.sort(ord, (a, b) -> Double.compare(r.seTE[a], r.seTE[b])); int K = ord.length;
        Color[] col = new Color[K]; double[] lwd = new double[K]; Color userCol = colour(o.drapStudyColour);
        String layout = o.drapLayout == null ? GRAYSCALE : o.drapLayout;
        Color[] gray = grayColors(K);
        for (int j = 0; j < K; j++) { col[j] = userCol != null ? userCol : GRAYSCALE.equals(layout) ? gray[j] : colour("Dark gray"); lwd[j] = 1; }
        if (LINEWIDTH.equals(layout)) {   // meta: lwd.max * w / max(w), weights of the random-effects model if it is shown, else of the common-effect model
            double[] w = new double[K]; double wmax = 0; for (int j = 0; j < K; j++) { double[] src = showRandom ? r.wRandomFull : r.wCommonFull; w[j] = src != null && ord[j] < src.length ? src[ord[j]] : Double.NaN; if (!Double.isNaN(w[j])) wmax = Math.max(wmax, w[j]); }
            for (int j = 0; j < K; j++) lwd[j] = wmax > 0 && !Double.isNaN(w[j]) ? 2.5 * w[j] / wmax : 1;
        }
        double lwdModel = 3;   // meta: lwd.common = max(3, lwd.study), lwd.random = lwd.common

        // ---- the plotting region (R: 4% expansion of the data range on both axes; log scale for relative effects)
        double xlo = mn, xhi = mx;   // for relative effects the grid is on the log scale: the plot is simply drawn against it (a log-scaled axis)
        double dx = xhi - xlo, ux0 = xlo - 0.04 * dx, ux1 = xhi + 0.04 * dx, dy = ylim[1] - ylim[0], uy0 = ylim[0] - 0.04 * dy, uy1 = ylim[1] + 0.04 * dy;
        double L = 66, R = c.w / c.scaleF() - 84, T = 30, B = c.h / c.scaleF() - 66;
        final Map m = new Map(L, R, T, B, ux0, ux1, uy0, uy1);
        g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1f));
        Shape clipAll = g.getClip(); Rectangle2D box = new Rectangle2D.Double(L, T, R - L, B - T);

        // ---- y values of the model curves on the grid
        double[] yCommon = new double[G], yRandom = new double[G], yPred = new double[G];
        for (int i = 0; i < G; i++) {
            yCommon[i] = okCommon ? curve(z, grid[i], P.teCommon, P.seCommon) : Double.NaN; yRandom[i] = okRandom ? curve(z, grid[i], P.teRandom, P.seRandom) : Double.NaN;
            yPred[i] = showPred ? curve(z, grid[i], P.teRandom, tq / Dist.qnorm(0.975) * sePred) : Double.NaN; }
        double ymin = ylim[0] - 1e-9;   // tolerance: the cut-off points are on the grid and sit exactly on the lower limit
        double[] alpha = z ? new double[]{0.001, 0.01, 0.05, 0.1} : new double[]{0.01, 0.05, 0.1}; double[] yAlpha = new double[alpha.length];
        for (int i = 0; i < alpha.length; i++) yAlpha[i] = z ? Dist.qnorm(alpha[i] / 2) : alpha[i];

        // ---- prediction region (meta: polygon of the visible part of the curve + the same two helper polygons)
        g.setClip(box);
        if (showPred) {   // area under the prediction curve, from the lower limit of the plot up to the curve (cut at the limit), over the whole grid: one polygon, so no gaps whatever the data
            List<double[]> pts = new ArrayList<>(); pts.add(new double[]{grid[0], ylim[0]});
            for (int i = 0; i < G; i++) pts.add(new double[]{grid[i], Math.max(yPred[i], ylim[0])});
            pts.add(new double[]{grid[G - 1], ylim[0]});
            g.setColor(colour(o.drapPredColour, colour("Light blue"))); g.fill(poly(m, pts));
        }
        // ---- individual studies (largest standard error first, so that the most precise curve is on top)
        String labMode = o.drapLabels == null ? LAB_ID : o.drapLabels; int angle = Math.max(0, Math.min(90, o.drapAngle));
        double yval = ylim[1] + 0.005 * Math.abs(ylim[0] - ylim[1]); List<Object[]> labels = new ArrayList<>();
        for (int j = K - 1; j >= 0; j--) {
            int i = ord[j]; Path2D.Double path = null; boolean pen = false;
            for (int q = 0; q < G; q++) { double y = curve(z, grid[q], r.TE[i], r.seTE[i]); if (y < ymin) { pen = false; continue; } double px = m.X(grid[q]), py = m.Y(y); if (!pen || path == null) { if (path == null) path = new Path2D.Double(); path.moveTo(px, py); pen = true; } else path.lineTo(px, py); }
            g.setColor(col[j]); g.setStroke(new BasicStroke((float) lwd[j])); if (path != null) g.draw(path);
            if (!LAB_NONE.equals(labMode)) labels.add(new Object[]{r.TE[i], LAB_ID.equals(labMode) ? String.valueOf(i + 1) : r.studlab[i]});
        }
        // ---- model curves
        if (showCommon) polyline(g, m, grid, yCommon, ymin, colour(o.drapCommonColour, colour("Blue")), lwdModel);
        if (showRandom) polyline(g, m, grid, yRandom, ymin, colour(o.drapRandomColour, colour("Red")), lwdModel);
        // ---- null effect line, alpha lines
        g.setStroke(new BasicStroke(1f)); g.setColor(Color.BLACK);
        if (!Double.isNaN(nullEffect)) { double xv = nullEffect; c.line(m.X(xv), T, m.X(xv), B); }
        Stroke dash = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{3.5f, 3.5f}, 0);
        g.setStroke(dash); for (double ya : yAlpha) c.line(L, m.Y(ya), R, m.Y(ya)); g.setStroke(new BasicStroke(1f));
        // texts of the alpha lines: "p = 0.1" at the lower end of the data range, "90% CI" at the upper end
        for (int i = 0; i < alpha.length; i++) { double yy = m.Y(yAlpha[i] + (ylim[1] - ylim[0]) / 200);
            c.text("p = " + num(alpha[i]), m.X(xlo), yy, 8, 0, false); c.text(num(100 * (1 - alpha[i])) + "% CI", m.X(xhi), yy, 8, 2, false); }
        // ---- study labels (top of the plot)
        if (!labels.isEmpty()) { double com = okCommon ? P.teCommon : P.teRandom;
            for (Object[] lb : labels) { double te = (Double) lb[0]; String t = (String) lb[1]; double srt = 0;
                if (angle != 0) srt = LAB_NAME.equals(labMode) ? (te < com ? angle : -angle) : angle;   // meta: +45 below the common-effect estimate, -45 otherwise
                g.setFont(Canvas.font(8)); FontMetrics fm = g.getFontMetrics(); double w = fm.stringWidth(t), ax = m.X(te), ay = m.Y(yval);
                AffineTransform old = g.getTransform(); g.translate(ax, ay); g.rotate(-Math.toRadians(srt)); double off = srt == 0 ? -w / 2 : srt > 0 ? -w : 0;
                g.setColor(Color.BLACK); g.drawString(t, (float) off, 0f); g.setTransform(old); } }
        g.setClip(clipAll);

        // ---- legend (topleft, white background, framed)
        if (showCommon || showRandom || showPred) {
            List<String> tx = new ArrayList<>(); List<Color> lc = new ArrayList<>();
            if (showCommon) { tx.add("Common-effect model"); lc.add(colour(o.drapCommonColour, colour("Blue"))); }
            if (showRandom) { tx.add("Random-effects model"); lc.add(colour(o.drapRandomColour, colour("Red"))); }
            if (showPred) { tx.add("Range of prediction"); lc.add(colour(o.drapPredColour, colour("Light blue"))); }
            g.setFont(Canvas.font(11)); FontMetrics fm = g.getFontMetrics(); double tw = 0; for (String t : tx) tw = Math.max(tw, fm.stringWidth(t));
            double pad = 9, seg = 26, gap = 9, rowH = 21, bw = pad + seg + gap + tw + pad, bh = pad * 0.6 + rowH * tx.size() - 3 + pad * 0.6;
            g.setColor(Color.WHITE); g.fill(new Rectangle2D.Double(L, T, bw, bh)); g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1f)); g.draw(new Rectangle2D.Double(L, T, bw, bh));
            for (int i = 0; i < tx.size(); i++) { double yy = T + pad * 0.6 + rowH * i + rowH / 2 - 1;
                g.setColor(lc.get(i)); g.setStroke(new BasicStroke((float) lwdModel, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER)); g.draw(new Line2D.Double(L + pad, yy, L + pad + seg, yy));
                g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1f)); g.drawString(tx.get(i), (float) (L + pad + seg + gap), (float) (yy + 4)); }
        }

        // ---- frame, axes
        g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1f)); g.draw(box);
        // x-axis: ticks as R: log scale (GLPretty) for relative effects, otherwise pretty over the data range
        double[] xt; if (rel) xt = logTicks(Math.exp(xlo), Math.exp(xhi), Math.exp(ux0), Math.exp(ux1)); else xt = inside(Canvas.pretty(xlo, xhi, 5), ux0, ux1);
        String[] xl = labelsOf(xt, rel);
        for (int i = 0; i < xt.length; i++) { double xx = m.X(rel ? Math.log(xt[i]) : xt[i]); c.line(xx, B, xx, B + 6); c.text(xl[i], xx, B + 21, 11, 1, false); }
        double[] yt = inside(Canvas.pretty(ylim[0], ylim[1], 5), uy0, uy1); String[] yl = labelsOf(yt, false);
        for (int i = 0; i < yt.length; i++) { double yy = m.Y(yt[i]); c.line(L - 6, yy, L, yy); c.textRot(yl[i], L - 11, yy, 11); }
        // right-hand axis: confidence level
        if (z) { double[] a1 = {0.001, 0.01, 0.05}; String[] l1 = {"0.999", "0.99", "0.95"};
            for (int i = 0; i < a1.length; i++) { double yy = m.Y(Dist.qnorm(a1[i] / 2)); c.line(R, yy, R + 6, yy); c.text(l1[i], R + 10, yy + 4, 11, 0, false); }
            String[] l2 = {"0.8", "0.6", "0.4", "0.2", "0.0"}; for (int i = 0; i < 5; i++) { double yy = m.Y(Dist.qnorm((0.2 * (i + 1)) / 2)); c.line(R, yy, R + 6, yy); c.text(l2[i], R + 10, yy + 4, 11, 0, false); }
        } else { double[] rt = Canvas.pretty(0, 1, 5); String[] rl = labelsOf(rt, false);
            for (int i = 0; i < rt.length; i++) { if (rt[i] < -1e-9 || rt[i] > 1 + 1e-9) continue; double yy = m.Y(1 - rt[i]); c.line(R, yy, R + 6, yy); c.text(rl[i], R + 10, yy + 4, 11, 0, false); } }
        c.text(xlab(s, rel), (L + R) / 2, B + 46, 12, 1, false);
        rotText(g, z ? "Test statistic" : "P-value", 17, (T + B) / 2, 12, false);
        rotText(g, "Confidence level", R + 64, (T + B) / 2, 12, true);
        return c.img;
    }

    private static String num(double v) { String t = String.format(Locale.ROOT, "%.6f", v).replaceAll("0+$", "").replaceAll("\\.$", ""); return t; }

    private static String xlab(MetaSpec s, boolean rel) {
        if (rel) return Plots.xlabBack(s);   // relative effects are always drawn back-transformed
        switch (s.sm) { case "ZCOR": return "Fisher's z Transformed Correlation"; case "PLOGIT": case "PLN": case "PAS": case "PFT": return Plots.funnelXlab(s); default: return Plots.xlabFor(s); }
    }

    /** Data coordinates to canvas pixels. */
    private static final class Map { final double L, R, T, B, x0, x1, y0, y1; Map(double L, double R, double T, double B, double x0, double x1, double y0, double y1) { this.L = L; this.R = R; this.T = T; this.B = B; this.x0 = x0; this.x1 = x1; this.y0 = y0; this.y1 = y1; }
        double X(double v) { return L + (v - x0) / (x1 - x0) * (R - L); } double Y(double v) { return B - (v - y0) / (y1 - y0) * (B - T); } }

    private static Area poly(Map m, List<double[]> pts) {
        Path2D.Double p = new Path2D.Double(); boolean first = true;
        for (double[] q : pts) { double x = m.X(q[0]), y = m.Y(q[1]); if (first) { p.moveTo(x, y); first = false; } else p.lineTo(x, y); }
        p.closePath(); return new Area(p);
    }

    /** Text rotated by 90 degrees: counter-clockwise (reads upwards, left margin) or clockwise (reads downwards, right margin, as mtext(side = 4)). */
    private static void rotText(Graphics2D g, String t, double x, double y, float size, boolean clockwise) {
        g.setFont(Canvas.font(size)); g.setColor(Color.BLACK); AffineTransform old = g.getTransform(); double w = g.getFontMetrics().stringWidth(t);
        g.translate(x, y); g.rotate(clockwise ? Math.PI / 2 : -Math.PI / 2); g.drawString(t, (float) (-w / 2), 0f); g.setTransform(old);
    }

    private static void polyline(Graphics2D g, Map m, double[] gx, double[] gy, double ymin, Color col, double lwd) {
        Path2D.Double p = new Path2D.Double(); boolean first = true;
        for (int i = 0; i < gx.length; i++) if (!Double.isNaN(gy[i]) && gy[i] >= ymin) { double x = m.X(gx[i]), y = m.Y(gy[i]); if (first) { p.moveTo(x, y); first = false; } else p.lineTo(x, y); }
        if (first) return; g.setColor(col); g.setStroke(new BasicStroke((float) lwd, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND)); g.draw(p);
    }

    private static double[] inside(double[] t, double lo, double hi) { return Arrays.stream(t).filter(v -> v >= lo - 1e-9 * Math.abs(hi - lo) && v <= hi + 1e-9 * Math.abs(hi - lo)).toArray(); }

    /** Tick positions of a log-scaled R axis (GLPretty): inside one decade the ordinary pretty values, else 1-2-5, 1-5 or 10^k, kept inside the plotting region [ul, uh]. dl, dh: data range. */
    public static double[] logTicks(double dl, double dh, double ul, double uh) {
        double p1 = Math.ceil(Math.log10(dl)), p2 = Math.floor(Math.log10(dh));
        if (p2 <= p1 && dh / dl > 10) { p1 = Math.ceil(Math.log10(dl) - 0.5); p2 = Math.floor(Math.log10(dh) + 0.5); }
        List<Double> t = new ArrayList<>();
        if (p2 <= p1) { for (double v : Canvas.pretty(dl, dh, 5)) t.add(v); }
        else { double[] mult = p2 - p1 <= 2 ? new double[]{1, 2, 5} : p2 - p1 <= 3 ? new double[]{1, 5} : new double[]{1};
            for (int k = (int) p1 - 1; k <= (int) p2; k++) for (double m : mult) t.add(m * Math.pow(10, k)); }
        return t.stream().filter(v -> v >= ul * (1 - 1e-9) && v <= uh * (1 + 1e-9)).mapToDouble(Double::doubleValue).toArray();
    }

    /** Axis labels formatted like R: one common number of decimals (the fewest that shows every tick exactly). */
    public static String[] labelsOf(double[] t, boolean log) {
        int dec = 0; for (double v : t) { for (int d = 0; d <= 8; d++) { double sc = Math.pow(10, d); if (Math.abs(Math.round(v * sc) / sc - v) < 1e-9 * Math.max(1, Math.abs(v))) { dec = Math.max(dec, d); break; } if (d == 8) dec = Math.max(dec, 4); } }
        String[] o = new String[t.length]; for (int i = 0; i < t.length; i++) { o[i] = String.format(Locale.ROOT, "%." + dec + "f", t[i] == 0 ? 0.0 : t[i]); if (o[i].startsWith("-") && Double.parseDouble(o[i]) == 0) o[i] = o[i].substring(1); } return o;
    }
}
