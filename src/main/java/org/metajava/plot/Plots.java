package org.metajava.plot;

import org.metajava.core.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/** Plots in the style of meta (R base graphics + grid forest). */
public final class Plots {
    private Plots() {}
    static final Color GRAY = new Color(190, 190, 190), RED = new Color(205, 0, 0);

    public static class Opts { public double scale = 1; public int width = 900; public String theme = "Default"; public String sortBy = "Original order"; public boolean ascending = true;
        public boolean overall = true, analysisDetails = false, testOverall = true, hetStats = true, studyLabels = true, regLine = true, contour = false, ciI2 = false, ciTau = false;
        public String xLeft = "", xRight = "", xlab = ""; public boolean showSubgroupName = true, testSubgroup = true, testEffectSubgroup = false; public boolean overallResults = true;
        public boolean weightsCol = true, effectCol = true, seCol = true, teCol = true; public double spacing = 1.0; public int digits = 2; public boolean predictionLine = true; public boolean apexLine = false; }

    static class Row { String label; double te, lo, up, w, se; int type; // 0 study,1 diamond,2 header,3 PI,4 blank,5 text
        String[] lcols = new String[0]; String rcol1 = "", rcol2 = "", wtxt = ""; String text = ""; double predLo, predUp; boolean hasPred;
        double n = Double.NaN;       // n (proportions) / person-time (rates) used to back-transform Freeman-Tukey values
        double ptBack = Double.NaN;  // study estimate on the original scale (event / n), if shown instead of back(TE)
    }

    static String[] left(MetaResult r) { return new String[0]; }

    public static BufferedImage forest(MetaResult r, Opts o) {
        MetaSpec s = r.spec; Pool P = r.overall; boolean log = s.backtransf && s.isRatioSm();
        List<Row> rows = new ArrayList<>(); int n = r.TE.length;
        Integer[] ord = new Integer[n]; for (int i = 0; i < n; i++) ord[i] = i;
        sortStudies(ord, r, o);
        String smL = Report.smLabel(s); int dg = o.digits;
        boolean hasSg = r.hasSubgroups();
        double sc = 0, sr = 0; for (int i = 0; i < n; i++) { if (!Double.isNaN(r.wCommonFull[i])) sc += r.wCommonFull[i]; if (!Double.isNaN(r.wRandomFull[i])) sr += r.wRandomFull[i]; }
        boolean useRandom = s.random, useCommon = s.common;
        List<String> hl = new ArrayList<>();
        if (hasSg) {
            for (int g = 0; g < r.sgLevels.size(); g++) {
                Row h = new Row(); h.type = 2; h.label = (o.showSubgroupName ? (s.subgroupName.isEmpty() ? "Subgroup" : s.subgroupName) + s.sepSubgroup : "") + r.sgLevels.get(g); rows.add(h);
                int[] idx = r.sgIdx.get(g); List<Integer> ids = new ArrayList<>(); for (int i : ord) for (int j : idx) if (i == j) ids.add(i);
                for (int i : ids) rows.add(studyRow(r, i, sc, sr, dg, log));
                Pool p = r.sgPools.get(g); double wsum = 0; for (int i : idx) { double w = useRandom ? r.wRandomFull[i] : r.wCommonFull[i]; if (!Double.isNaN(w)) wsum += w; }
                if (useCommon) rows.add(poolRow(r, s, p, s.textCommon, true, dg, 100 * wsumFor(r, idx, true) / sc, idx));
                if (useRandom) rows.add(poolRow(r, s, p, s.textRandom + p.randomLabelSuffix, false, dg, 100 * wsumFor(r, idx, false) / sr, idx));
                if (s.predictionSubgroup && !Double.isNaN(p.loPred)) rows.add(piRow(s, p, dg));
                if (o.hetStats) { Row t = new Row(); t.type = 5; t.text = hetLine(p, s, true, o); rows.add(t); }
                if (o.testEffectSubgroup) { Row t = new Row(); t.type = 5; t.text = "Test for effect in subgroup: " + IT + (p.testType.equals("t") && s.random ? "t" : "z") + IT + " = " + Fmt.num(s.random ? p.statRandom : p.statCommon, 2) + " (" + IT + "p" + IT + " " + Fmt.pvalEq(s.random ? p.pRandom : p.pCommon, 4) + ")"; rows.add(t); }
                rows.add(blank());
            }
        } else for (int i : ord) rows.add(studyRow(r, i, sc, sr, dg, log));
        if (!hasSg || o.overallResults) {
            if (useCommon) rows.add(poolRow(r, s, P, s.textCommon, true, dg, 100, allIdx(n)));
            if (useRandom) rows.add(poolRow(r, s, P, s.textRandom + P.randomLabelSuffix, false, dg, 100, allIdx(n)));
            if (s.prediction && !Double.isNaN(P.loPred)) rows.add(piRow(s, P, dg));
            if (o.hetStats) { Row t = new Row(); t.type = 5; t.text = hetLine(P, s, false, o); rows.add(t); }
            if (o.testOverall) { Row t = new Row(); t.type = 5; t.text = "Test for overall effect: " + IT + (P.testType.equals("t") && s.random ? "t" : "z") + IT + " = " + Fmt.num(s.random ? P.statRandom : P.statCommon, 2) + " (" + IT + "p" + IT + " " + Fmt.pvalEq(s.random ? P.pRandom : P.pCommon, 4) + ")"; rows.add(t); }
            if (hasSg && o.testSubgroup) { Row t = new Row(); t.type = 5; double q = s.random && !s.common ? r.qbRandom : r.qbCommon, pv = s.random && !s.common ? r.pQbRandom : r.pQbCommon;
                t.text = "Test for subgroup differences: \u03C7\u00B2" + sub(r.dfBetween) + " = " + Fmt.num(q, 2) + ", df = " + r.dfBetween + " (" + IT + "p" + IT + " " + Fmt.pvalEq(pv, 4) + ")"; rows.add(t); }
        }
        return drawForest(rows, r, o, log, smL, !s.noWeights(), false);
    }

    static String sub(int d) { String x = String.valueOf(d); StringBuilder b = new StringBuilder(); for (char c : x.toCharArray()) b.append((char) ('\u2080' + (c - '0'))); return b.toString(); }

    static double wsumFor(MetaResult r, int[] idx, boolean common) { double w = 0; for (int i : idx) { double x = common ? r.wCommonFull[i] : r.wRandomFull[i]; if (!Double.isNaN(x)) w += x; } return w; }

    static final String IT = "\u0001";
    static String t4(double x) { return Math.round(x * 10000) == 0 ? "0" : Fmt.num(x, 4); }
    static String hetLine(Pool p, MetaSpec s, boolean sg, Opts o) {
        StringBuilder b = new StringBuilder("Heterogeneity: " + IT + "I" + IT + "\u00B2 = " + Fmt.num(100 * p.I2, 1) + "%");
        if (o.ciI2 && !Double.isNaN(p.loI2)) b.append(" [").append(Fmt.num(100 * p.loI2, 1)).append("%; ").append(Fmt.num(100 * p.upI2, 1)).append("%]");
        if (s.random) { b.append(", " + IT + "\u03C4" + IT + "\u00B2 = ").append(t4(p.tau2)); if (o.ciTau && !Double.isNaN(p.loTau2)) b.append(" [").append(t4(p.loTau2)).append("; ").append(t4(p.upTau2)).append("]"); }
        String pq = Fmt.pval(p.pQ, 4); b.append(", " + IT + "p" + IT + " ").append(pq.startsWith("<") ? pq : "= " + pq); return b.toString();
    }

    static Row studyRow(MetaResult r, int i, double sc, double sr, int dg, boolean log) {
        MetaSpec s = r.spec; Row w = new Row(); w.type = 0; w.label = r.studlab[i]; w.te = r.TE[i]; w.se = r.seTE[i]; w.lo = r.lower[i]; w.up = r.upper[i];
        double wt = s.random ? r.wRandomFull[i] : r.wCommonFull[i]; double tot = s.random ? sr : sc; w.w = Double.isNaN(wt) ? 0 : wt / tot;
        if (s.noWeights()) w.w = 1;   // GLMM: no study weights, equally sized squares
        w.n = Report.nOf(s, i); w.lcols = leftCols(r, i, dg); double t = Report.studyEst(r, i); if (s.type.equals("prop") || s.type.equals("rate")) w.ptBack = t;
        w.rcol1 = Double.isNaN(r.TE[i]) ? "NA" : Fmt.num(t, dg); w.rcol2 = Double.isNaN(r.TE[i]) ? "" : Fmt.ci(Report.studyLo(r, i), Report.studyUp(r, i), dg);
        w.wtxt = s.noWeights() ? "" : Fmt.num(100 * w.w, 1) + "%"; return w;
    }

    static String[] leftCols(MetaResult r, int i, int dg) {
        MetaSpec s = r.spec; MetaData d = r.data;
        switch (s.type) {
            case "bin": return new String[]{ num(d.get("event.e")[i]), num(d.get("n.e")[i]), num(d.get("event.c")[i]), num(d.get("n.c")[i]) };
            case "cont": return new String[]{ num(d.get("n.e")[i]), Fmt.num(d.get("mean.e")[i], 2), Fmt.num(d.get("sd.e")[i], 2), num(d.get("n.c")[i]), Fmt.num(d.get("mean.c")[i], 2), Fmt.num(d.get("sd.c")[i], 2) };
            case "inc": return new String[]{ num(d.get("event.e")[i]), num(d.get("time.e")[i]), num(d.get("event.c")[i]), num(d.get("time.c")[i]) };
            case "prop": return new String[]{ num(d.get("event")[i]), num(d.get("n")[i]) };
            case "rate": return new String[]{ num(d.get("event")[i]), num(d.get("time")[i]) };
            case "mean": return new String[]{ num(d.get("n")[i]), Fmt.num(d.get("mean")[i], 2), Fmt.num(d.get("sd")[i], 2) };
            case "cor": return new String[]{ num(d.get("n")[i]) };
            default: return new String[]{ Fmt.num(r.TE[i], 2), Fmt.num(r.seTE[i], 2) };
        }
    }
    static String num(double x) { return x == Math.rint(x) ? String.valueOf((long) x) : Fmt.num(x, 2); }

    static String[] leftHead(MetaSpec s) {
        switch (s.type) {
            case "bin": return new String[]{"Events", "Total", "Events", "Total"};
            case "cont": return new String[]{"Total", "Mean", "SD", "Total", "Mean", "SD"};
            case "inc": return new String[]{"Events", "Time", "Events", "Time"};
            case "prop": return new String[]{"Events", "Total"}; case "rate": return new String[]{"Events", "Time"};
            case "mean": return new String[]{"Total", "Mean", "SD"}; case "cor": return new String[]{"Total"};
            default: return new String[]{ s.sm.isEmpty() ? "TE" : s.isRatioSm() ? "log(" + Report.smLabel(s) + ")" : s.sm, "SE" };
        }
    }

    static int[] allIdx(int n) { int[] a = new int[n]; for (int i = 0; i < n; i++) a[i] = i; return a; }

    static String forestLabel(String l) { return l.replace("Random effects model", "Random-effects model").replace("Common effect model", "Common-effect model"); }

    static String[] sums(MetaResult r, int[] idx) {
        MetaSpec s = r.spec; MetaData d = r.data; if (idx == null) return new String[0];
        String[] keys;
        switch (s.type) {
            case "bin": keys = new String[]{"event.e", "n.e", "event.c", "n.c"}; break; case "cont": keys = new String[]{"n.e", null, null, "n.c", null, null}; break;
            case "inc": keys = new String[]{"event.e", "time.e", "event.c", "time.c"}; break; case "prop": keys = new String[]{"event", "n"}; break; case "rate": keys = new String[]{"event", "time"}; break;
            case "mean": keys = new String[]{"n", null, null}; break; case "cor": keys = new String[]{"n"}; break; default: return new String[0];
        }
        String[] out = new String[keys.length];
        for (int j = 0; j < keys.length; j++) { if (keys[j] == null || d.get(keys[j]) == null) { out[j] = ""; continue; } double t = 0; for (int i : idx) if (!Double.isNaN(r.TE[i]) && !Double.isNaN(d.get(keys[j])[i])) t += d.get(keys[j])[i]; out[j] = num(t); }
        return out;
    }

    static Row poolRow(MetaResult r, MetaSpec s, Pool p, String label, boolean common, int dg, double wpct, int[] idx) {
        Row w = new Row(); w.type = 1; w.label = forestLabel(label); w.te = common ? p.teCommon : p.teRandom; w.lo = common ? p.loCommon : p.loRandom; w.up = common ? p.upCommon : p.upRandom; w.w = wpct / 100;
        w.n = p.nHarm; w.rcol1 = Fmt.num(Meta.back(s, w.te, w.n), dg); w.rcol2 = Fmt.ci(Meta.back(s, w.lo, w.n), Meta.back(s, w.up, w.n), dg); w.wtxt = s.noWeights() ? "" : Fmt.num(wpct, 1) + "%";
        w.lcols = sums(r, idx); return w;
    }
    static Row piRow(MetaSpec s, Pool p, int dg) { Row w = new Row(); w.type = 3; w.label = s.textPredict + (Math.abs(s.levelPredict - 0.95) > 1e-9 ? " (" + Report.pct(s.levelPredict) + "%)" : ""); w.te = p.teRandom; w.lo = p.loPred; w.up = p.upPred; w.n = p.nHarm; w.rcol2 = Fmt.ci(Meta.back(s, w.lo, w.n), Meta.back(s, w.up, w.n), dg); return w; }
    static Row blank() { Row r = new Row(); r.type = 4; return r; }

    static void sortStudies(Integer[] ord, MetaResult r, Opts o) {
        Comparator<Integer> c = null; MetaSpec s = r.spec;
        switch (o.sortBy) {
            case "Effect size": c = Comparator.comparingDouble(i -> r.TE[i]); break;
            case "Standard error": c = Comparator.comparingDouble(i -> r.seTE[i]); break;
            case "Weight": c = Comparator.comparingDouble(i -> s.random ? nz(r.wRandomFull[i]) : nz(r.wCommonFull[i])); break;
            case "Study label": c = Comparator.comparing(i -> r.studlab[i]); break;
            default: return;
        }
        if (!o.ascending) c = c.reversed(); Arrays.sort(ord, c);
    }
    static double nz(double x) { return Double.isNaN(x) ? 0 : x; }

    static void rich(Canvas c, String text, double x, double y, float size, boolean bold) {
        Graphics2D g = c.g; boolean it = false; double cx = x;
        for (String part : text.split("\u0001", -1)) {
            Font f = Canvas.font(size).deriveFont((bold ? Font.BOLD : Font.PLAIN) | (it ? Font.ITALIC : Font.PLAIN)); g.setFont(f);
            g.drawString(part, (float) cx, (float) y); cx += g.getFontMetrics().stringWidth(part); it = !it;
        }
    }

    /** Forest plot renderer reproducing the look of meta::forest (layout "meta"). */
    static BufferedImage drawForest(List<Row> rows, MetaResult r, Opts o, boolean log, String smL, boolean showWeight, boolean loo) {
        MetaSpec s = r.spec; final float FS = 12f; int rowH = (int) Math.round(20 * o.spacing), M = 14, nrow = rows.size();
        String[] lh = loo ? new String[]{"P-value", "Tau\u00B2", "Tau", "I\u00B2"} : leftHead(s); int nl = lh.length;
        Canvas probe = Canvas.probe(); FontMetrics fm = probe.g.getFontMetrics(Canvas.font(FS)), fb = probe.g.getFontMetrics(Canvas.bold(FS));
        int labW = fb.stringWidth("Study");
        for (Row w : rows) if (w.type == 0 || w.type == 1 || w.type == 2 || w.type == 3) labW = Math.max(labW, (w.type == 0 ? fm : fb).stringWidth(w.label == null ? "" : w.label));
        int[] cw = new int[nl]; for (int j = 0; j < nl; j++) { cw[j] = fb.stringWidth(lh[j]); for (Row w : rows) if (j < w.lcols.length) cw[j] = Math.max(cw[j], (w.type == 1 ? fb : fm).stringWidth(w.lcols[j])); }
        // two-group outcomes (metabin, metacont, metainc): group labels ("Experimental" / "Control") above the column headings, as meta::forest
        final boolean grp = !loo && (s.type.equals("bin") || s.type.equals("cont") || s.type.equals("inc")) && nl >= 4 && nl % 2 == 0; final int gh = grp ? 18 : 0; final int gn = nl / 2;
        if (grp) { String[] gl = {s.labelE == null ? "" : s.labelE, s.labelC == null ? "" : s.labelC};
            for (int g2 = 0; g2 < 2; g2++) { int a0 = g2 * gn, b0 = a0 + gn - 1, span = 0; for (int j = a0; j <= b0; j++) span += cw[j]; span += 14 * (gn - 1); int need = fb.stringWidth(gl[g2]); if (need > span) cw[b0] += need - span; } }
        int[] xr = new int[nl]; int cur = M + labW + 16; for (int j = 0; j < nl; j++) { cur += cw[j]; xr[j] = cur; cur += 14; }
        int plotW = 230, px0 = (nl == 0 ? M + labW + 16 : xr[nl - 1] + 14), px1 = px0 + plotW;
        String ciHead = Fmt.num(s.levelMa * 100, 0) + "% CI";
        int effW = fb.stringWidth(smL), ciW = fb.stringWidth(ciHead), wW = fb.stringWidth("Weight");
        for (Row w : rows) { effW = Math.max(effW, (w.type == 1 ? fb : fm).stringWidth(w.rcol1)); ciW = Math.max(ciW, (w.type == 1 || w.type == 3 ? fb : fm).stringWidth(w.rcol2)); wW = Math.max(wW, fb.stringWidth(w.wtxt)); }
        int rx1 = px1 + 22 + effW, rx2 = rx1 + 12 + ciW, rx3 = rx2 + 10 + wW; int W = (showWeight ? rx3 : rx2) + M;
        // ---- vertical layout
        int tail = nrow; while (tail > 0 && rows.get(tail - 1).type == 5) tail--; int lastPool = -1; for (int i = 0; i < tail; i++) if (rows.get(i).type == 1) lastPool = i;
        double y = 12 + gh + 14 + rowH * 0.9; double[] cy = new double[nrow]; int firstPool = -1;
        for (int i = 0; i < tail; i++) {
            Row w = rows.get(i);
            if (w.type == 1 && firstPool < 0) { firstPool = i; if (i > 0 && rows.get(i - 1).type == 0) y += rowH * 0.6; }
            double h = w.type == 4 ? rowH * 0.6 : w.type == 5 ? rowH * 0.75 : rowH; cy[i] = y + h / 2; y += h;
        }
        double yFirstTop = 12 + gh + 14 + rowH * 0.9 - 4, ay = y + rowH * 0.5;
        String[] dl = o.analysisDetails ? Report.details(r).split("\n") : new String[0];
        // ---- axis range
        // For measures with a back-transformation (proportions, correlations, ...) the axis is drawn on the back-transformed (original) scale, as meta::forest does.
        final boolean lin = s.backtransf && linBack(s.sm); final double[] bnd = linBounds(s);
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY, wmax = 0;
        for (Row w : rows) if (w.type == 0 || w.type == 1 || w.type == 3) { double pe = ptPos(s, lin, w), pl = lin ? P(s, true, w, w.lo) : w.lo, pu = lin ? P(s, true, w, w.up) : w.up; double a = Math.min(pl, pe), b = Math.max(pu, pe); if (!Double.isNaN(a) && !Double.isInfinite(a)) lo = Math.min(lo, a); if (!Double.isNaN(b) && !Double.isInfinite(b)) hi = Math.max(hi, b); if (w.type == 0) wmax = Math.max(wmax, w.w); }
        double refT = s.nullValue(); final boolean hasRef = !lin || !Double.isNaN(refT);
        double ref = Double.isNaN(refT) ? 0 : refT; if (lin && hasRef) ref = Meta.back(s, refT, Double.NaN);
        if (hasRef) { lo = Math.min(lo, ref); hi = Math.max(hi, ref); double half = Math.max(ref - lo, hi - ref); if (lin) half = Math.min(half, Math.min(ref - bnd[0], bnd[1] - ref)); if (half > 0) { lo = ref - half; hi = ref + half; } }   // axis centred on the null value (as meta::forest, xlim = "symmetric")
        if (Double.isInfinite(lo) || Double.isInfinite(hi)) { lo = lin ? bnd[0] : -1; hi = lin ? bnd[1] : 1; }
        double pad = (hi - lo) * 0.04 + 1e-9; lo -= pad; hi += pad; if (lin) { if (hasRef) { double h2 = Math.min(hi - ref, ref - lo); h2 = Math.min(h2, Math.min(ref - bnd[0], bnd[1] - ref)); lo = ref - h2; hi = ref + h2; } else { lo = Math.max(lo, bnd[0]); hi = Math.min(hi, bnd[1]); } }
        final double lo2 = lo, hi2 = hi; final int fx0 = px0, fw = plotW; final double[] sh = {0}; java.util.function.DoubleUnaryOperator X = v -> fx0 + sh[0] + (v - lo2) / (hi2 - lo2) * fw;
        if (wmax <= 0) wmax = 1;
        // ---- axis ticks (computed up-front: the footer layout depends on the axis extent)
        double[] ticks = log ? logTicks(Math.exp(lo), Math.exp(hi)) : Canvas.pretty(lo, hi, 5); double tmin = Double.POSITIVE_INFINITY, tmax = Double.NEGATIVE_INFINITY;
        double axL = Double.POSITIVE_INFINITY, axR = Double.NEGATIVE_INFINITY; FontMetrics ft = probe.g.getFontMetrics(Canvas.font(13f));
        for (double t : ticks) { double v = log ? Math.log(t) : t; if (v < lo - 1e-9 || v > hi + 1e-9) continue; tmin = Math.min(tmin, v); tmax = Math.max(tmax, v);
            double xx = X.applyAsDouble(v), hw = ft.stringWidth(Canvas.tick(t)) / 2.0; axL = Math.min(axL, xx - hw); axR = Math.max(axR, xx + hw); }
        boolean xlab2 = !o.xLeft.isEmpty() || !o.xRight.isEmpty();
        double axTop = ay - 3, axBot = ay + (xlab2 ? 38 : 20) + 5;   // vertical band occupied by the axis, tick labels and optional left/right labels
        if (xlab2) { FontMetrics f11 = probe.g.getFontMetrics(Canvas.font(11f)); if (!o.xLeft.isEmpty()) axL = Math.min(axL, X.applyAsDouble(tmin) - f11.stringWidth(o.xLeft) / 2.0); if (!o.xRight.isEmpty()) axR = Math.max(axR, X.applyAsDouble(tmax) + f11.stringWidth(o.xRight) / 2.0); }
        // ---- extra gap between the left columns and the plot: if the heterogeneity / test lines are slightly too long to fit left of the axis,
        // shift the plot (and the right-hand columns) to the right instead of moving the whole footer below the axis (capped at 140 px).
        { FontMetrics fq = probe.g.getFontMetrics(Canvas.font(11f)); double need = 0;
          for (int i = tail; i < nrow; i++) need = Math.max(need, M + fq.stringWidth(rows.get(i).text.replace("\u0001", "")) + 6 + 8);
          int extra = (int) Math.ceil(need - axL); if (extra > 0 && extra <= 140) { sh[0] = extra; px0 += extra; px1 += extra; rx1 += extra; rx2 += extra; rx3 += extra; W += extra; axL += extra; axR += extra; } }
        // ---- footer text (heterogeneity, tests, methods): starts directly under the last row, as in meta::forest, and is only pushed
        // below the axis if a line would actually overlap the axis / tick labels horizontally.
        FontMetrics f11 = probe.g.getFontMetrics(Canvas.font(11f)), f11b = probe.g.getFontMetrics(Canvas.bold(11f));
        java.util.List<String> fTxt = new ArrayList<>(); java.util.List<Boolean> fBold = new ArrayList<>(), fRich = new ArrayList<>(); java.util.List<Double> fGap = new ArrayList<>();
        for (int i = tail; i < nrow; i++) { fTxt.add(rows.get(i).text); fBold.add(false); fRich.add(true); fGap.add(fTxt.size() == 1 ? 0.0 : 15.0); }
        if (dl.length > 0) { fTxt.add("Details of meta-analysis methods:"); fBold.add(true); fRich.add(false); fGap.add(fTxt.size() == 1 ? 0.0 : 25.0);
            for (int i = 1; i < dl.length; i++) { fTxt.add(dl[i]); fBold.add(false); fRich.add(false); fGap.add(14.0); } }
        double[] fy = new double[fTxt.size()]; double ty0 = y + 15, tcur = ty0, tyEnd = y; boolean first = true;
        for (int i = 0; i < fy.length; i++) {
            tcur = first ? ty0 : tcur + fGap.get(i); first = false;
            String plain = fTxt.get(i).replace("\u0001", ""); double right = M + (fBold.get(i) ? f11b : f11).stringWidth(plain) + 6;   // +6: safety margin for italic glyphs
            if (right > axL - 8 && tcur + 4 > axTop && tcur - 11 < axBot) tcur = axBot + 12;   // would collide with the axis -> move below it
            fy[i] = tcur; tyEnd = tcur;
        }
        double yEnd = Math.max(fy.length > 0 ? tyEnd + 4 : y, Math.max(axBot, y));
        int H = (int) Math.ceil(yEnd + 14);
        Canvas c = new Canvas(W, H, o.scale); Graphics2D g = c.g;
        // ---- header
        double hy = 12 + gh + 12; g.setColor(Color.BLACK);
        if (grp) { String[] gl = {s.labelE == null ? "" : s.labelE, s.labelC == null ? "" : s.labelC};
            for (int g2 = 0; g2 < 2; g2++) { int a0 = g2 * gn, b0 = a0 + gn - 1; double xl = xr[a0] - cw[a0], xrr = xr[b0]; c.text(gl[g2], (xl + xrr) / 2.0, hy - gh, FS, 1, true); } }
        c.text("Study", M, hy, FS, 0, true); for (int j = 0; j < nl; j++) c.text(lh[j], xr[j], hy, FS, 2, true);
        c.text(loo ? smL : xlabFor(s), (px0 + px1) / 2.0, hy, FS, 1, true); c.text(smL, rx1, hy, FS, 2, true); c.text(ciHead, rx2, hy, FS, 2, true); if (showWeight) c.text("Weight", rx3, hy, FS, 2, true);
        // ---- reference lines
        Pool P = r.overall; double pooled = s.random ? P.teRandom : P.teCommon; double yRefEnd = ay;
        g.setStroke(new BasicStroke(1.1f)); if (hasRef) c.line(X.applyAsDouble(ref), yFirstTop, X.applyAsDouble(ref), ay);
        if (!Double.isNaN(pooled) && firstPool >= 0 && !loo) { double pp = lin ? Meta.back(s, pooled, P.nHarm) : pooled; double yd = cy[lastPool] - 6; g.setStroke(new BasicStroke(0.9f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{1.5f, 2.5f}, 0)); c.line(X.applyAsDouble(pp), yFirstTop, X.applyAsDouble(pp), yd); }
        g.setStroke(new BasicStroke(1f));
        // ---- rows
        for (int i = 0; i < tail; i++) {
            Row w = rows.get(i); double mid = cy[i], base = mid + 4; g.setColor(Color.BLACK);
            if (w.type == 2) { c.text(w.label, M, base, FS, 0, true); continue; }
            if (w.type == 4) continue;
            if (w.type == 5) { rich(c, w.text, M, base, 11f, false); continue; }
            boolean bold = w.type != 0;
            c.text(w.label == null ? "" : w.label, M, base, FS, 0, bold);
            for (int j = 0; j < w.lcols.length && j < nl; j++) c.text(w.lcols[j], xr[j], base, FS, 2, bold);
            if (w.type == 0) {
                if (!Double.isNaN(w.te)) {
                    double wl = lin ? P(s, true, w, w.lo) : w.lo, wu = lin ? P(s, true, w, w.up) : w.up;
                    double a = X.applyAsDouble(Math.max(wl, lo)), b = X.applyAsDouble(Math.min(wu, hi)), xm = X.applyAsDouble(ptPos(s, lin, w));
                    double size = Math.max(3, 15 * Math.sqrt(w.w / wmax)), bl = xm - size / 2, br = xm + size / 2;
                    g.setColor(new Color(192, 192, 192)); g.fill(new Rectangle2D.Double(bl, mid - size / 2, size, size));
                    // as meta::forest: the CI line and the vertical tick stay visible on top of the square (solid black);
                    // if the whole CI fits inside the square, the horizontal line and the vertical tick are white.
                    final float LW = 1.1f, TW = 1.3f;
                    if (a >= bl && b <= br) {
                        g.setColor(Color.WHITE); g.setStroke(new BasicStroke(LW)); c.line(a, mid, b, mid);
                        g.setStroke(new BasicStroke(TW)); c.line(xm, mid - 2.5, xm, mid + 2.5);
                    } else {
                        g.setColor(Color.BLACK); g.setStroke(new BasicStroke(LW)); c.line(a, mid, b, mid);
                        g.setStroke(new BasicStroke(TW)); c.line(xm, mid - 2.5, xm, mid + 2.5);
                    }
                }
            } else if (w.type == 1) {
                double a = X.applyAsDouble(lin ? P(s, true, w, w.lo) : w.lo), b = X.applyAsDouble(lin ? P(s, true, w, w.up) : w.up), xm = X.applyAsDouble(lin ? P(s, true, w, w.te) : w.te);
                Path2D d = new Path2D.Double(); d.moveTo(a, mid); d.lineTo(xm, mid - 5); d.lineTo(b, mid); d.lineTo(xm, mid + 5); d.closePath();
                g.setColor(new Color(192, 192, 192)); g.fill(d); g.setColor(Color.BLACK); g.setStroke(new BasicStroke(0.9f)); g.draw(d);
            } else if (w.type == 3) {
                double a = X.applyAsDouble(lin ? P(s, true, w, w.lo) : w.lo), b = X.applyAsDouble(lin ? P(s, true, w, w.up) : w.up);
                g.setColor(new Color(205, 0, 0)); g.fill(new Rectangle2D.Double(a, mid - 2.5, b - a, 5)); g.setColor(new Color(120, 0, 0)); g.setStroke(new BasicStroke(0.7f)); g.draw(new Rectangle2D.Double(a, mid - 2.5, b - a, 5));
            }
            g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1f));
            if (w.type != 3) c.text(w.rcol1, rx1, base, FS, 2, bold);
            c.text(w.rcol2, rx2, base, FS, 2, bold); if (showWeight && !loo) c.text(w.wtxt, rx3, base, FS, 2, bold);
        }
        // ---- axis
        g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1.1f));
        if (tmin <= tmax) c.line(X.applyAsDouble(tmin), ay, X.applyAsDouble(tmax), ay);
        for (double t : ticks) { double v = log ? Math.log(t) : t; if (v < lo - 1e-9 || v > hi + 1e-9) continue; double xx = X.applyAsDouble(v); c.line(xx, ay, xx, ay + 5); c.text(Canvas.tick(t), xx, ay + 20, 13f, 1, false); }
        g.setStroke(new BasicStroke(1f));
        if (!o.xLeft.isEmpty()) c.text(o.xLeft, X.applyAsDouble(tmin), ay + 38, 11f, 1, false); if (!o.xRight.isEmpty()) c.text(o.xRight, X.applyAsDouble(tmax), ay + 38, 11f, 1, false);
        // ---- footer text
        for (int i = 0; i < fy.length; i++) { if (fRich.get(i)) rich(c, fTxt.get(i), M, fy[i], 11f, false); else c.text(fTxt.get(i), M, fy[i], 11f, 0, fBold.get(i)); }
        return c.img;
    }

    /** Measures whose forest plot axis is drawn on the back-transformed scale (as in meta::forest). */
    static boolean linBack(String sm) { switch (sm) { case "PLOGIT": case "PAS": case "PFT": case "ZCOR": case "IRS": case "IRFT": return true; default: return false; } }
    /** Natural limits of the back-transformed axis. */
    static double[] linBounds(MetaSpec s) {
        switch (s.sm) { case "ZCOR": return new double[]{-1, 1}; case "IRS": case "IRFT": return new double[]{0, Double.MAX_VALUE}; default: return new double[]{0, s.pscale}; }
    }
    /** Position of a value on the back-transformed axis. */
    static double P(MetaSpec s, boolean lin, Row w, double v) { return lin ? Meta.back(s, v, w.n) : v; }
    /** Position of the point estimate of a row: event / n for single proportions and rates, else back(TE). */
    static double ptPos(MetaSpec s, boolean lin, Row w) { return !lin ? w.te : !Double.isNaN(w.ptBack) ? w.ptBack : Meta.back(s, w.te, w.n); }

    static double[] logTicks(double lo, double hi) {
        double[] cand = {0.01, 0.02, 0.05, 0.1, 0.2, 0.25, 0.5, 1, 2, 4, 5, 10, 20, 50, 100};
        List<Double> t = new ArrayList<>(); for (double c : cand) if (c >= lo * 0.999 && c <= hi * 1.001) t.add(c);
        if (t.size() > 5) { List<Double> u = new ArrayList<>(); for (double c : new double[]{0.1, 0.2, 0.5, 1, 2, 5, 10}) if (c >= lo && c <= hi) u.add(c); t = u; }
        if (t.size() < 2) { t.clear(); t.add(1.0); }
        return t.stream().mapToDouble(Double::doubleValue).toArray();
    }

    // ----- leave-one-out / cumulative forest
    public static BufferedImage forestSeries(MetaResult r, List<Pool> pools, List<String> labels, Opts o, String kind) {
        MetaSpec s = r.spec; boolean log = s.backtransf && s.isRatioSm(); int dg = o.digits; List<Row> rows = new ArrayList<>();
        for (int i = 0; i < pools.size(); i++) {
            Pool p = pools.get(i); Row w = new Row(); w.type = 0; w.label = labels.get(i);
            w.te = s.random ? p.teRandom : p.teCommon; w.lo = s.random ? p.loRandom : p.loCommon; w.up = s.random ? p.upRandom : p.upCommon; w.w = 0.3;
            w.n = p.nHarm; w.rcol1 = Fmt.num(Meta.back(s, w.te, w.n), dg); w.rcol2 = Fmt.ci(Meta.back(s, w.lo, w.n), Meta.back(s, w.up, w.n), dg);
            w.lcols = new String[]{ Fmt.num(s.random ? p.pRandom : p.pCommon, 4).replace("0.0000", "< 0.0001"), Fmt.num(p.tau2, 4), Fmt.num(p.tau, 4), Fmt.num(100 * p.I2, 1) + "%" }; rows.add(w);
            if (s.prediction && s.random && !Double.isNaN(p.loPred)) rows.add(piRow(s, p, dg)); }
        Pool p = r.overall; Row w = poolRow(r, s, p, s.random ? s.textRandom : s.textCommon, !s.random, dg, 100, null); w.lcols = new String[]{ "", "", "", "" }; rows.add(w);
        if (s.prediction && s.random) rows.add(piRow(s, p, dg));
        MetaSpec s2 = s; BufferedImage im = drawForest(rows, r, o, log, Report.smLabel(s), false, true); return im;
    }

    // ---------------- funnel & friends ----------------
    static class Axes { Canvas c; double x0, x1, y0, y1, xl, xh, yl, yh; boolean yRev;
        double X(double v) { return x0 + (v - xl) / (xh - xl) * (x1 - x0); }
        double Y(double v) { return yRev ? y0 + (v - yl) / (yh - yl) * (y1 - y0) : y1 - (v - yl) / (yh - yl) * (y1 - y0); } }

    static Axes axes(Canvas c, double xl, double xh, double yl, double yh, boolean yRev, String xlab, String ylab, boolean logX, String title) {
        Axes a = new Axes(); a.c = c; a.x0 = 70; a.x1 = c.w / c.scaleF() - 30; a.y0 = 40; a.y1 = c.h / c.scaleF() - 60; a.xl = xl; a.xh = xh; a.yl = yl; a.yh = yh; a.yRev = yRev;
        Graphics2D g = c.g; g.setColor(Color.BLACK); g.draw(new Rectangle2D.Double(a.x0, a.y0, a.x1 - a.x0, a.y1 - a.y0));
        double[] xt = logX ? logTicks(Math.exp(xl), Math.exp(xh)) : Canvas.pretty(xl, xh, 6), yt = Canvas.pretty(yl, yh, 6);
        if (logX) { int inside = 0; for (double t : xt) { double v = Math.log(t); if (v >= xl && v <= xh) inside++; } if (inside < 2) xt = Canvas.pretty(Math.exp(xl), Math.exp(xh), 5); }   // narrow range: the 1-2-5 log ticks leave fewer than two labels
        for (double t : xt) { double v = logX ? Math.log(t) : t; if (v < xl || v > xh) continue; double xx = a.X(v); c.line(xx, a.y1, xx, a.y1 + 5); c.text(Canvas.tick(t), xx, a.y1 + 18, 10, 1, false); }
        for (double t : yt) { if (t < yl - 1e-9 || t > yh + 1e-9) continue; double yy = a.Y(t); c.line(a.x0 - 5, yy, a.x0, yy); c.textRot(Canvas.tick(t), a.x0 - 10, yy, 10); }
        c.text(xlab, (a.x0 + a.x1) / 2, a.y1 + 40, 11, 1, false); c.textRot(ylab, 16, (a.y0 + a.y1) / 2, 11);
        if (title != null && !title.isEmpty()) c.text(title, (a.x0 + a.x1) / 2, 24, 13, 1, true);
        return a; }

    static String xlabFor(MetaSpec s) { switch (s.sm) { case "HR": return "Hazard Ratio"; case "OR": return "Odds Ratio"; case "RR": return "Risk Ratio"; case "RD": return "Risk Difference"; case "MD": return "Mean Difference"; case "SMD": return "Standardised Mean Difference"; case "ROM": return "Ratio of Means";
        case "ZCOR": case "COR": return "Correlation"; case "IRR": return "Incidence Rate Ratio"; case "IRD": return "Incidence Rate Difference"; case "PLOGIT": case "PLN": case "PRAW": case "PAS": case "PFT": return "Proportion"; case "MRAW": case "MLN": return "Mean"; case "IR": case "IRLN": case "IRS": case "IRFT": return "Incidence Rate"; case "DOR": return "Diagnostic Odds Ratio"; case "ASD": return "Arcsine Difference"; case "VE": return "Vaccine Efficacy"; default: return Report.smLabel(s); } }

    public static BufferedImage funnel(MetaResult r, Opts o, TrimFill tf) {
        MetaSpec s = r.spec; boolean log = s.backtransf && s.isRatioSm(); Canvas c = new Canvas(620, 520, o.scale);
        double[] te = tf != null ? tf.TE : r.TE, se = tf != null ? tf.seTE : r.seTE; String[] lab = tf != null ? tf.studlab : r.studlab; boolean[] fl = tf != null ? tf.filled : null;
        double center = tf != null ? (s.random ? tf.pool.teRandom : tf.pool.teCommon) : (s.random ? r.overall.teRandom : r.overall.teCommon);
        double maxse = 0, mn = 1e300, mx = -1e300; for (int i = 0; i < te.length; i++) if (!Double.isNaN(te[i])) { maxse = Math.max(maxse, se[i]); mn = Math.min(mn, te[i]); mx = Math.max(mx, te[i]); }
        double z = Dist.qnorm(0.975); double half = Math.max(z * maxse, Math.max(Math.abs(mx - center), Math.abs(mn - center))) * 1.05;
        double xl = center - half, xh = center + half;
        Axes a = axes(c, xl, xh, 0, maxse * 1.05, true, xlabFor(s), "Standard Error", log, "");
        Graphics2D g = c.g; double ymax = maxse * 1.05;
        if (o.contour) { // contour-enhanced
            double[][] bands = {{1.96, 2.576, 0.0}, {1.645, 1.96, 0.0}};
            Color[] cols = {new Color(150, 150, 150), new Color(190, 190, 190), new Color(225, 225, 225)}; double[] zs = {Dist.qnorm(0.995), Dist.qnorm(0.975), Dist.qnorm(0.95)};
            for (int i = 0; i < 3; i++) { Path2D p = new Path2D.Double(); p.moveTo(a.X(-zs[i] * ymax), a.Y(ymax)); p.lineTo(a.X(0), a.Y(0)); p.lineTo(a.X(zs[i] * ymax), a.Y(ymax)); p.closePath(); }
            double[] zz = {Dist.qnorm(0.995), Dist.qnorm(0.975), Dist.qnorm(0.95)};
            Color[] fills = {new Color(235, 235, 235), new Color(215, 215, 215), new Color(195, 195, 195)};
            double ref = s.nullValue(); if (Double.isNaN(ref)) ref = 0;
            for (int i = 0; i < 3; i++) { Path2D p = new Path2D.Double(); p.moveTo(a.X(ref - zz[i] * ymax) , a.Y(ymax)); p.lineTo(a.X(ref), a.Y(0)); p.lineTo(a.X(ref + zz[i] * ymax), a.Y(ymax)); p.closePath(); Shape clip = g.getClip(); g.setClip(new Rectangle2D.Double(a.x0, a.y0, a.x1 - a.x0, a.y1 - a.y0)); g.setColor(fills[2 - i]); g.fill(p); g.setClip(clip); }
            g.setColor(Color.BLACK); g.draw(new Rectangle2D.Double(a.x0, a.y0, a.x1 - a.x0, a.y1 - a.y0));
        }
        g.setColor(Color.BLACK); c.dashed(a.X(center), a.y0, a.X(center), a.y1);
        Shape clip = g.getClip(); g.setClip(new Rectangle2D.Double(a.x0, a.y0, a.x1 - a.x0, a.y1 - a.y0));
        c.dashed(a.X(center), a.Y(0), a.X(center - z * ymax), a.Y(ymax)); c.dashed(a.X(center), a.Y(0), a.X(center + z * ymax), a.Y(ymax)); g.setClip(clip);
        for (int i = 0; i < te.length; i++) { if (Double.isNaN(te[i])) continue; double xx = a.X(te[i]), yy = a.Y(se[i]);
            boolean f = fl != null && fl[i]; if (f) { g.setColor(Color.WHITE); g.fill(new Ellipse2D.Double(xx - 3, yy - 3, 6, 6)); g.setColor(Color.BLACK); g.draw(new Ellipse2D.Double(xx - 3, yy - 3, 6, 6)); }
            else { g.setColor(Color.BLACK); g.fill(new Ellipse2D.Double(xx - 2.5, yy - 2.5, 5, 5)); }
            if (o.studyLabels) { g.setColor(Color.BLACK); c.text(lab[i], xx + 4, yy - 3, 8, 0, false); } }
        return c.img;
    }

    /** Regression plot belonging to the asymmetry test (Egger radial form). */
    public static BufferedImage biasPlot(Bias.Result b, MetaResult r, Opts o) {
        Canvas c = new Canvas(600, 480, o.scale); if (b.plotX == null) { c.text("No plot available for this method", 300, 240, 12, 1, false); return c.img; }
        double xl = 0, xh = Arrays.stream(b.plotX).max().getAsDouble() * 1.05, yl = Math.min(0, Arrays.stream(b.plotY).min().getAsDouble()) - 0.5, yh = Math.max(0, Arrays.stream(b.plotY).max().getAsDouble()) + 0.5;
        if (!b.methodBias.equals("Begg")) yl = Math.min(yl, b.bias - 0.5);
        if (b.methodBias.equals("Begg")) { xl = Arrays.stream(b.plotX).min().getAsDouble() - 0.5; xh = Arrays.stream(b.plotX).max().getAsDouble() + 0.5; yl = 0; yh = Arrays.stream(b.plotY).max().getAsDouble() * 1.05; }
        Axes a = axes(c, xl, xh, yl, yh, false, b.plotXlab, b.plotYlab, false, "");
        Graphics2D g = c.g;
        for (int i = 0; i < b.plotX.length; i++) { double xx = a.X(b.plotX[i]), yy = a.Y(b.plotY[i]); g.setColor(Color.BLACK); g.fill(new Ellipse2D.Double(xx - 2.5, yy - 2.5, 5, 5)); if (o.studyLabels && !b.methodBias.equals("Begg")) c.text(r.studlab[i < r.studlab.length ? i : 0], xx + 4, yy - 3, 8, 0, false); }
        if (!b.methodBias.equals("Begg")) { Shape clip = g.getClip(); g.setClip(new Rectangle2D.Double(a.x0, a.y0, a.x1 - a.x0, a.y1 - a.y0)); g.setColor(Color.BLACK);
            c.line(a.X(xl), a.Y(b.intercept + b.bias * 0 + (b.bias == 0 ? 0 : 0)), a.X(xl), a.Y(b.intercept)); // placeholder
            double slopeEff = b.intercept, interceptBias = b.bias; // regression: y = bias + effect * x
            c.line(a.X(xl), a.Y(interceptBias + slopeEff * xl), a.X(xh), a.Y(interceptBias + slopeEff * xh)); g.setClip(clip); }
        return c.img;
    }

    /** Funnel plot with the PET line (effect = b0 + b1 * SE) and the PEESE curve (effect = b0 + b1 * SE^2); both start at their intercept = the corrected effect. */
    public static BufferedImage petPeese(PubBias.PetPeese pp, MetaResult r, Opts o) {
        MetaSpec s = r.spec; boolean log = s.backtransf && s.isRatioSm(); Canvas c = new Canvas(620, 520, o.scale);
        if (pp.error != null) { c.text(pp.error, 310, 260, 12, 1, false); return c.img; }
        double center = s.random ? r.overall.teRandom : r.overall.teCommon, maxse = pp.maxSe * 1.05, mn = center, mx = center;
        for (double t : pp.te) { mn = Math.min(mn, t); mx = Math.max(mx, t); }
        double[] ends = {pp.pet.b0, pp.peese.b0, pp.pet.b0 + pp.pet.b1 * maxse, pp.peese.b0 + pp.peese.b1 * maxse * maxse};
        for (double e : ends) { mn = Math.min(mn, e); mx = Math.max(mx, e); }
        double pad = (mx - mn) * 0.06 + 1e-9; double xl = mn - pad, xh = mx + pad;
        Axes a = axes(c, xl, xh, 0, maxse, true, xlabFor(s), "Standard Error", log, ""); Graphics2D g = c.g;
        Shape clip = g.getClip(); g.setClip(new Rectangle2D.Double(a.x0, a.y0, a.x1 - a.x0, a.y1 - a.y0));
        g.setColor(Color.GRAY); c.dashed(a.X(center), a.Y(0), a.X(center), a.Y(maxse));
        Color petCol = new Color(0x1F4E8C), peeseCol = new Color(0xD9822B); g.setStroke(new BasicStroke(1.8f));
        g.setColor(petCol); c.line(a.X(pp.pet.b0), a.Y(0), a.X(pp.pet.b0 + pp.pet.b1 * maxse), a.Y(maxse));
        g.setColor(peeseCol); double px = a.X(pp.peese.b0), py = a.Y(0);
        for (int i = 1; i <= 60; i++) { double se = maxse * i / 60.0, ex = pp.peese.b0 + pp.peese.b1 * se * se; double nx = a.X(ex), ny = a.Y(se); c.line(px, py, nx, ny); px = nx; py = ny; }
        g.setStroke(new BasicStroke(1f)); g.setClip(clip);
        for (int i = 0; i < pp.te.length; i++) { double xx = a.X(pp.te[i]), yy = a.Y(pp.se[i]); g.setColor(Color.BLACK); g.fill(new Ellipse2D.Double(xx - 2.5, yy - 2.5, 5, 5)); if (o.studyLabels) c.text(pp.label[i], xx + 4, yy - 3, 8, 0, false); }
        for (int q = 0; q < 2; q++) { double ex = q == 0 ? pp.pet.b0 : pp.peese.b0; g.setColor(q == 0 ? petCol : peeseCol); g.fill(new Rectangle2D.Double(a.X(ex) - 4, a.Y(0) - 4, 8, 8)); }
        double lx = a.x0 + 4; g.setStroke(new BasicStroke(1.8f));
        g.setColor(petCol); c.line(lx, 22, lx + 22, 22); g.setColor(Color.BLACK); c.text("PET", lx + 26, 26, 10, 0, false);
        g.setColor(peeseCol); c.line(lx + 70, 22, lx + 92, 22); g.setColor(Color.BLACK); c.text("PEESE", lx + 96, 26, 10, 0, false);
        g.setStroke(new BasicStroke(1f)); g.setColor(Color.GRAY); c.dashed(lx + 160, 22, lx + 182, 22); g.setColor(Color.BLACK); c.text("pooled effect", lx + 186, 26, 10, 0, false);
        return c.img;
    }

    public static BufferedImage bubble(Reg g0, Opts o, MetaResult r) {
        MetaSpec s = r.spec; boolean log = s.backtransf && s.isRatioSm(); Canvas c = new Canvas(620, 520, o.scale);
        if (g0.x1 == null) { c.text("Bubble plot requires exactly one numeric covariate", 310, 260, 12, 1, false); return c.img; }
        double xl = Arrays.stream(g0.x1).min().getAsDouble(), xh = Arrays.stream(g0.x1).max().getAsDouble(), pad = (xh - xl) * 0.08 + 1e-9; xl -= pad; xh += pad;
        double yl = Double.POSITIVE_INFINITY, yh = Double.NEGATIVE_INFINITY; for (double v : g0.y1) { yl = Math.min(yl, v); yh = Math.max(yh, v); } double yp = (yh - yl) * 0.1 + 1e-9; yl -= yp; yh += yp;
        Canvas probe = c; Axes a = axes(c, xl, xh, log ? Math.exp(yl) : yl, log ? Math.exp(yh) : yh, false, g0.xName, xlabFor(s), false, "");
        double wmax = Arrays.stream(g0.size).max().getAsDouble(); Graphics2D g = c.g;
        for (int i = 0; i < g0.x1.length; i++) { double yv = log ? Math.exp(g0.y1[i]) : g0.y1[i]; double rad = 4 + 12 * Math.sqrt(g0.size[i] / wmax); double xx = a.X(g0.x1[i]), yy = a.Y(yv);
            g.setColor(new Color(150, 150, 150, 200)); g.fill(new Ellipse2D.Double(xx - rad, yy - rad, 2 * rad, 2 * rad)); g.setColor(Color.BLACK); g.draw(new Ellipse2D.Double(xx - rad, yy - rad, 2 * rad, 2 * rad));
            if (o.studyLabels) c.text(r.studlab[g0.used[i]], xx - rad - 2, yy - 2, 8, 2, false); }
        if (o.regLine) { double b0 = g0.est[0], b1 = g0.est[1]; if (!g0.intercept) { b0 = 0; b1 = g0.est[0]; } double yA = b0 + b1 * xl, yB = b0 + b1 * xh; if (log) { yA = Math.exp(yA); yB = Math.exp(yB); } g.setColor(Color.BLACK); c.line(a.X(xl), a.Y(yA), a.X(xh), a.Y(yB)); }
        return c.img;
    }

    /** Baujat points (contribution to overall heterogeneity, influence on the overall result): one leave-one-out refit per study, computed once per analysis (rendering the
     *  plot again, e.g. to save it at a higher resolution, reuses them) and in parallel. Only the common-effect refit is read, so GLMM refits skip the random-effects fit
     *  and the likelihood-ratio heterogeneity test. */
    private static synchronized void baujatPoints(MetaResult r) {
        if (r.baujatPts != null) return;
        final MetaSpec s2 = r.spec.clone(); s2.skipTauCi = true; s2.skipLrt = true; s2.skipHet = true; if (s2.isGlmm()) s2.random = false;
        final Pool p = r.overall; final int n = r.TE.length; final List<Integer> use = new ArrayList<>();
        for (int i = 0; i < n; i++) if (!Double.isNaN(r.TE[i]) && r.seTE[i] > 0) use.add(i);
        final double[][] xy = new double[use.size()][];
        org.metajava.core.Par.forRange(xy.length, q -> {
            int i = use.get(q); double w = 1 / (r.seTE[i] * r.seTE[i]); double x = w * Math.pow(r.TE[i] - p.teCommon, 2);
            int[] sub = new int[n - 1]; int cc = 0; for (int j = 0; j < n; j++) if (j != i) sub[cc++] = j; Pool qq = Pool.compute(s2, r.TE, r.seTE, sub, r.commonFn);
            xy[q] = new double[]{x, Math.pow(p.teCommon - qq.teCommon, 2) / (qq.seCommon * qq.seCommon)};
        });
        List<double[]> pts = new ArrayList<>(); List<String> labs = new ArrayList<>(); for (int q = 0; q < xy.length; q++) { pts.add(xy[q]); labs.add(r.studlab[use.get(q)]); }
        r.baujatLabs = labs; r.baujatPts = pts;
    }

    public static BufferedImage baujat(MetaResult r, Opts o) {
        baujatPoints(r); List<double[]> pts = r.baujatPts; List<String> labs = r.baujatLabs;
        Canvas c = new Canvas(560, 520, o.scale); double xm = 0, ym = 0; for (double[] q : pts) { xm = Math.max(xm, q[0]); ym = Math.max(ym, q[1]); }
        Axes a = axes(c, 0, xm * 1.08, 0, ym * 1.08, false, "Contribution to overall heterogeneity", "Influence on overall result", false, "");
        for (int i = 0; i < pts.size(); i++) { double xx = a.X(pts.get(i)[0]), yy = a.Y(pts.get(i)[1]); c.g.fill(new Ellipse2D.Double(xx - 2.5, yy - 2.5, 5, 5)); if (o.studyLabels) c.text(labs.get(i), xx + 4, yy - 3, 8, 0, false); }
        return c.img;
    }

    public static BufferedImage radial(MetaResult r, Opts o) {
        MetaSpec s = r.spec; Pool p = r.overall; int n = r.TE.length; List<double[]> pts = new ArrayList<>(); List<String> labs = new ArrayList<>();
        for (int i = 0; i < n; i++) if (!Double.isNaN(r.TE[i]) && r.seTE[i] > 0) { pts.add(new double[]{1 / r.seTE[i], r.TE[i] / r.seTE[i]}); labs.add(r.studlab[i]); }
        Canvas c = new Canvas(560, 520, o.scale); double xm = 0, ym = 0, yn = 0; for (double[] q : pts) { xm = Math.max(xm, q[0]); ym = Math.max(ym, q[1]); yn = Math.min(yn, q[1]); }
        double slope = p.teCommon; double yl = Math.min(yn, 0) - 1, yh = Math.max(ym, slope * xm) + 1;
        Axes a = axes(c, 0, xm * 1.05, yl, yh, false, "Inverse of standard error", "Standardised treatment estimate", false, "");
        Shape clip = c.g.getClip(); c.g.setClip(new Rectangle2D.Double(a.x0, a.y0, a.x1 - a.x0, a.y1 - a.y0)); c.line(a.X(0), a.Y(0), a.X(xm * 1.05), a.Y(slope * xm * 1.05)); c.g.setClip(clip);
        for (int i = 0; i < pts.size(); i++) { double xx = a.X(pts.get(i)[0]), yy = a.Y(pts.get(i)[1]); c.g.fill(new Ellipse2D.Double(xx - 2.5, yy - 2.5, 5, 5)); if (o.studyLabels) c.text(labs.get(i), xx + 4, yy - 3, 8, 0, false); }
        return c.img;
    }

    /** Doi plot (Furuya-Kanamori et al. 2018): |Z-score| (reversed axis) against the effect size, studies joined in order of effect; optional dashed line through the apex (Opts.apexLine, off as in metasens::doiplot). */
    public static BufferedImage doi(Lfk l, MetaResult r, Opts o) {
        MetaSpec s = r.spec; boolean log = s.backtransf && s.isRatioSm(); Canvas c = new Canvas(620, 520, o.scale); int k = l.k;
        if (l.lfk != l.lfk) { c.text("Doi plot not available (needs at least two studies with different effects)", 310, 260, 12, 1, false); return c.img; }
        double mn = l.te[0], mx = l.te[k - 1], pad = (mx - mn) > 0 ? (mx - mn) * 0.06 : 0.5, yh = 0; for (double z : l.absZ) yh = Math.max(yh, z); yh = Math.max(yh * 1.08, 0.5);
        Axes a = axes(c, mn - pad, mx + pad, 0, yh, true, xlabFor(s), "|Z-score|", log, ""); Graphics2D g = c.g;
        g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1f));
        if (o.apexLine) c.dashed(a.X(l.te[l.apex]), a.Y(0), a.X(l.te[l.apex]), a.y1);   // not part of metasens::doiplot: optional
        for (int j = 1; j < k; j++) c.line(a.X(l.te[j - 1]), a.Y(l.absZ[j - 1]), a.X(l.te[j]), a.Y(l.absZ[j]));
        for (int j = 0; j < k; j++) { double xx = a.X(l.te[j]), yy = a.Y(l.absZ[j]); g.fill(new Ellipse2D.Double(xx - 3, yy - 3, 6, 6)); if (o.studyLabels) c.text(l.label[j], xx + 5, yy - 4, 8, 0, false); }
        c.text("LFK index: " + Fmt.num(l.lfk, 2), a.x0 + 8, a.y0 + 17, 11, 0, false);
        return c.img;
    }

    /** L'Abbe plot (meta::labbe.metabin): event rate in the experimental group against the control group; symbol sizes follow metafor::labbe (psize) for every pooling method. */
    public static BufferedImage labbe(MetaResult r, Opts o) {
        MetaSpec s = r.spec; MetaData d = r.data; Canvas c = new Canvas(520, 520, o.scale); int n = r.TE.length;
        double[] ee = d.get("event.e"), ne = d.get("n.e"), ec = d.get("event.c"), nc = d.get("n.c");
        if (ee == null || ne == null || ec == null || nc == null) { c.text("L'Abb\u00E9 plot requires binary outcome data", 260, 260, 12, 1, false); return c.img; }
        // symbol sizes follow metafor::labbe for every pooling method: psize = sqrt(1/vi), vi = arm-level variances summed, rescaled linearly to plim = c(0.5, 3.5) (all 1 if every value is equal)
        double[] w = labbeMetaforPsize(s, ee, ne, ec, nc, r.TE);
        double hi = 0; for (int i = 0; i < n; i++) if (!Double.isNaN(r.TE[i]) && !Double.isNaN(w[i]) && ne[i] > 0 && nc[i] > 0) hi = Math.max(hi, Math.max(ee[i] / ne[i], ec[i] / nc[i]));
        double[] tk = Canvas.pretty(0, Math.max(hi * 1.05, 0.05), 5); double lim = Math.min(1, tk[tk.length - 1]);   // same limits on both axes, as metafor::labbe
        Axes a = axes(c, 0, lim, 0, lim, false, "Event rate (" + s.labelC + ")", "Event rate (" + s.labelE + ")", false, ""); Graphics2D g = c.g;
        Shape clip = g.getClip(); g.setClip(new Rectangle2D.Double(a.x0, a.y0, a.x1 - a.x0, a.y1 - a.y0));
        g.setColor(new Color(211, 211, 211)); g.setStroke(new BasicStroke(1f)); c.line(a.X(0), a.Y(0), a.X(lim), a.Y(lim));   // no effect (lightgray)
        g.setColor(Color.BLACK);
        if (s.common) labbeLine(c, a, s, r.overall.teCommon, false, lim); if (s.random) labbeLine(c, a, s, r.overall.teRandom, true, lim);
        g.setClip(clip);
        for (int i = 0; i < n; i++) { if (Double.isNaN(r.TE[i]) || Double.isNaN(w[i]) || ne[i] <= 0 || nc[i] <= 0) continue;
            double xx = a.X(ec[i] / nc[i]), yy = a.Y(ee[i] / ne[i]), dia = 4 * w[i], rad = dia / 2;   // metafor: cex = psize in [0.5, 3.5] -> diameter 2 to 14 px
            g.setColor(new Color(211, 211, 211)); g.fill(new Ellipse2D.Double(xx - rad, yy - rad, dia, dia)); g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1f)); g.draw(new Ellipse2D.Double(xx - rad, yy - rad, dia, dia));
            if (o.studyLabels) c.text(r.studlab[i], xx - rad - 3, yy + 3, 8, 2, false); }
        g.setColor(Color.BLACK); return c.img;
    }
    /** Symbol sizes of metafor::labbe.rma (plim = c(0.5, 3.5)): wi = sqrt(1/vi) with vi the sum of the arm-level sampling variances (escalc measures PLN for RR, PLO for OR, PR for RD, PAS for ASD, continuity correction as add/to of the model), rescaled linearly to [0.5, 3.5]; all 1 when every wi is equal. */
    public static double[] labbeMetaforPsize(MetaSpec s, double[] ee, double[] ne, double[] ec, double[] nc, double[] te) {
        int n = te.length; double[] vi = new double[n], ps = new double[n]; double[] vx = armVar(s, ec, nc), vy = armVar(s, ee, ne);
        double mn = Double.POSITIVE_INFINITY, mx = Double.NEGATIVE_INFINITY; double[] wi = new double[n];
        for (int i = 0; i < n; i++) { vi[i] = vx[i] + vy[i]; wi[i] = vi[i] > 0 && !Double.isInfinite(vi[i]) && !Double.isNaN(vi[i]) && !Double.isNaN(te[i]) ? Math.sqrt(1 / vi[i]) : Double.NaN;
            if (!Double.isNaN(wi[i])) { mn = Math.min(mn, wi[i]); mx = Math.max(mx, wi[i]); } }
        double rng = mx - mn;
        for (int i = 0; i < n; i++) ps[i] = Double.isNaN(wi[i]) ? Double.NaN : rng <= Math.sqrt(Math.ulp(1.0)) ? 1 : (wi[i] - mn) / rng * (3.5 - 0.5) + 0.5;
        return ps;
    }
    /** Arm-level sampling variances as escalc(measure = PLN / PLO / PR / PAS, add, to, addvi = TRUE) for the events xi out of ni. */
    private static double[] armVar(MetaSpec s, double[] x, double[] nn) {
        int n = x.length; double[] v = new double[n]; boolean any = false; for (int i = 0; i < n; i++) if (nn[i] > 0 && (x[i] == 0 || x[i] == nn[i])) any = true;
        for (int i = 0; i < n; i++) { if (!(nn[i] > 0)) { v[i] = Double.NaN; continue; }
            boolean zero = x[i] == 0 || x[i] == nn[i]; double add = s.methodIncr.equals("all") ? s.incr : s.methodIncr.equals("if0all") ? (any ? s.incr : 0) : s.methodIncr.equals("only0") ? (zero ? s.incr : 0) : 0;
            double xi = x[i] + add, mi = nn[i] - x[i] + add, ni = xi + mi;
            switch (s.sm) { case "RR": v[i] = 1 / xi - 1 / ni; break; case "RD": { double p = xi / ni; v[i] = p * (1 - p) / ni; break; } case "ASD": v[i] = 1 / (4 * ni); break; default: v[i] = 1 / xi + 1 / mi; } }
        return v;
    }
    /** Line of the pooled effect in the L'Abbe plot: event rate in the experimental group implied by a control rate x. */
    private static void labbeLine(Canvas c, Axes a, MetaSpec s, double te, boolean random, double lim) {
        if (Double.isNaN(te)) return; double[] xs = new double[101], ys = new double[101]; boolean ok = true;
        for (int i = 0; i <= 100; i++) { double x = lim * i / 100.0, y;
            switch (s.sm) { case "RR": y = Math.exp(te) * x; break; case "OR": { double e = Math.exp(te); y = e * x / (1 - x + e * x); break; } case "RD": y = x + te; break;
                case "ASD": y = Math.pow(Math.sin(Math.max(0, Math.min(Math.PI / 2, Math.asin(Math.sqrt(x)) + te))), 2); break; default: ok = false; y = x; }
            xs[i] = x; ys[i] = Math.max(0, Math.min(1, y)); }
        if (!ok) return; Graphics2D g = c.g; Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, random ? new float[]{9, 3} : new float[]{5, 4}, 0));
        Path2D p = new Path2D.Double(); for (int i = 0; i <= 100; i++) { if (i == 0) p.moveTo(a.X(xs[i]), a.Y(ys[i])); else p.lineTo(a.X(xs[i]), a.Y(ys[i])); } g.draw(p); g.setStroke(old);
    }

    /** 1-2-5 tick values inside [exp(lo), exp(hi)] for a log axis (bounds given on the log scale). */
    static double[] covrTicks(double lo, double hi) {
        java.util.List<Double> t = new java.util.ArrayList<>();
        for (int e = (int) Math.floor(lo / Math.log(10)) - 1; e <= (int) Math.ceil(hi / Math.log(10)); e++) for (double m : new double[]{1, 2, 5}) { double x = m * Math.pow(10, e); if (Math.log(x) >= lo && Math.log(x) <= hi) t.add(x); }
        if (t.size() < 2) { t.clear(); for (int i = 0; i < 4; i++) t.add(Math.exp(lo + (hi - lo) * i / 3)); }
        double[] r = new double[t.size()]; for (int i = 0; i < r.length; i++) r[i] = t.get(i); return r;
    }

    /** Eight-panel influence plot as drawn by metafor::plot.infl.rma.uni. */
    public static BufferedImage influence(Influence in, Opts o) {
        int k = in.k, p = in.p; Canvas c = new Canvas(980, 880, o.scale); Graphics2D g = c.g;
        String[] names = {"rstudent", "dffits", "cook.d", "cov.r", "tau2.del", "QE.del", "hat", "weight"};
        double[][] vals = {in.rstudent, in.dffits, in.cookD, in.covR, in.tau2Del, in.QEdel, in.hat, in.weight};
        double zq = Dist.qnorm(0.975), dfT = 3 * Math.sqrt((double) p / ((double) k - p));
        double[][] dashed = {{0}, {0}, {}, {1}, {in.tau2}, {in.QE}, {(double) p / k}, {100.0 / k}};
        double[][] dotted = {{-zq, zq}, {-dfT, dfT}, {Dist.qchisq(0.5, p)}, {}, {}, {k - p}, {3.0 * p / k}, {}};
        int cw = 490, ch = 220;
        for (int q = 0; q < 8; q++) {
            int col = q % 2, row = q / 2; double ox = col * cw + 50, oy = row * ch + 28, w = cw - 70, h = ch - 70;
            double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
            final boolean lg = q == 3;   // metafor: logcov=TRUE draws cov.r on a log y-axis
            for (double v : vals[q]) if (!Double.isNaN(v)) { double t = lg ? Math.log(v) : v; lo = Math.min(lo, t); hi = Math.max(hi, t); }
            if (lg) { for (double v : dashed[q]) { lo = Math.min(lo, Math.log(v)); hi = Math.max(hi, Math.log(v)); } } else
            for (double v : dashed[q]) { lo = Math.min(lo, v); hi = Math.max(hi, v); } for (double v : dotted[q]) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
            if (lo > hi) { lo = 0; hi = 1; }
            if (q == 2) lo = Math.min(lo, 0); if (q == 7) lo = 0;
            double pd = (hi - lo) * 0.04 + 1e-9; lo -= pd; hi += pd;
            final double fl = lo, fh = hi; final double fox = ox, foy = oy, fw = w, fh2 = h;
            java.util.function.DoubleUnaryOperator Y = v0 -> { double v = lg ? Math.log(v0) : v0; return foy + fh2 - (v - fl) / (fh - fl) * fh2; };
            java.util.function.DoubleUnaryOperator X = i -> fox + (k == 1 ? fw / 2 : (i - 1) / (double) (k - 1) * (fw - 20) + 10);
            c.text(names[q], ox + w / 2, oy - 10, 13, 1, true);
            g.setColor(Color.BLACK); g.draw(new Rectangle2D.Double(ox, oy, w, h));
            Shape clip = g.getClip(); g.setClip(new Rectangle2D.Double(ox, oy, w, h));
            for (double v : dashed[q]) c.dashed(ox, Y.applyAsDouble(v), ox + w, Y.applyAsDouble(v));
            for (double v : dotted[q]) { Stroke os = g.getStroke(); g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{1, 2}, 0)); c.line(ox, Y.applyAsDouble(v), ox + w, Y.applyAsDouble(v)); g.setStroke(os); }
            for (int i = 1; i < k; i++) if (!Double.isNaN(vals[q][i - 1]) && !Double.isNaN(vals[q][i])) c.line(X.applyAsDouble(i), Y.applyAsDouble(vals[q][i - 1]), X.applyAsDouble(i + 1), Y.applyAsDouble(vals[q][i]));
            for (int i = 1; i <= k; i++) { if (Double.isNaN(vals[q][i - 1])) continue; double xx = X.applyAsDouble(i), yy = Y.applyAsDouble(vals[q][i - 1]); g.setColor(in.flagged[i - 1] ? Color.RED : new Color(190, 190, 190)); g.fill(new Ellipse2D.Double(xx - 3, yy - 3, 6, 6)); g.setColor(Color.BLACK); g.draw(new Ellipse2D.Double(xx - 3, yy - 3, 6, 6)); }
            g.setClip(clip);
            for (double t : (lg ? covrTicks(lo, hi) : Canvas.pretty(lo, hi, 4))) { if (!lg && (t < lo || t > hi)) continue; double yy = Y.applyAsDouble(t); c.line(ox - 4, yy, ox, yy); c.textRot(Canvas.tick3(t), ox - 8, yy, 9); }
            int step = Math.max(1, (int) Math.ceil(k / 15.0));
            for (int i = 1; i <= k; i += step) { double xx = X.applyAsDouble(i); c.line(xx, oy + h, xx, oy + h + 4); g.setColor(in.flagged[i - 1] ? new Color(150, 0, 100) : new Color(0x66, 0, 0)); c.text(String.valueOf(i), xx, oy + h + 17, 9, 1, false); g.setColor(Color.BLACK); }
        }
        return c.img;
    }
}
