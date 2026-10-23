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
        public String xLeft = "", xRight = "", xlab = ""; public String xMin = "", xMax = "", xTick = ""; public boolean showSubgroupName = true, testSubgroup = true, testEffectSubgroup = false; public boolean overallResults = true;
        public boolean weightsCol = true, effectCol = true, seCol = true, teCol = true; public double spacing = 1.0; public int digits = 2; public boolean predictionLine = true; public boolean apexLine = false; public String goshPalette = "Default";
        /** Overrides of the forest-plot theme ("Theme default" keeps what the theme prescribes). */
        public boolean logAxis = false;   // regression plots of ratio measures: log-scaled OR / RR / HR axis (the fitted line is straight) instead of the back-transformed, curved line
        public boolean confInt = false; public String bubbleStyle = RegPlots.DEFAULT_STYLE, colPoints = RegPlots.AUTO, colLine = RegPlots.AUTO, colBand = RegPlots.AUTO, colBandLine = RegPlots.AUTO;   // regression plots
        public boolean predInt = false; public String colPred = RegPlots.AUTO, colPredLine = RegPlots.AUTO, symbol = RegPlots.SYM_DEFAULT; public double symSize = 1.0;   // prediction interval, study symbol
        public String baujatModel = BaujatPlot.MODEL_ANALYSIS, baujatTheme = BaujatPlot.DEFAULT, baujatSymbol = BaujatPlot.AUTO, baujatBack = BaujatPlot.AUTO, baujatQuadRule = BaujatPlot.Q_MEDIAN, baujatQuadColour = BaujatPlot.QUAD_AUTO; public boolean baujatQuad = false; public double baujatSymSize = 1.0;   // Baujat plot: theme, symbol colour, background colour, quadrant-line rule, quadrants on/off, symbol size (multiplier)
        public String labbeTheme = LabbePlot.DEFAULT, labbeSymbol = LabbePlot.AUTO, labbeBack = LabbePlot.AUTO, labbeCircle = LabbePlot.AUTO, labbeRef = LabbePlot.AUTO, labbeEst = LabbePlot.AUTO, labbeCI = LabbePlot.AUTO, labbePI = LabbePlot.AUTO;   // L'Abbe plot: theme, symbol (circle border) colour, background, circle fill, reference line, estimated-effect line, confidence area, prediction area
        public String radialModel = RadialPlot.MODEL_ANALYSIS, radialTheme = RadialPlot.DEFAULT, radialSymbol = RadialPlot.AUTO, radialBack = RadialPlot.AUTO, radialCircle = RadialPlot.AUTO, radialReg = RadialPlot.AUTO, radialRef = RadialPlot.AUTO, radialCI = RadialPlot.AUTO, radialCILine = RadialPlot.AUTO; public boolean radialLabels = true, labbeLabels = true; public boolean precision = false;   // funnel plot of precision (y = 1 / SE)   // Radial plot: theme, colours, study labels; L'Abbe plot: study labels
        /** Drapery plot (meta::drapery): test statistic or p-value, layout and colour of the study lines, study labels and their angle, prediction region, model(s) shown and their colours. */
        public String drapTarget = "", drapType = Drapery.ZVALUE, drapLayout = Drapery.GRAYSCALE, drapStudyColour = Drapery.STUDY_DEFAULT, drapLabels = Drapery.LAB_ID, drapPredColour = Drapery.PRED_DEFAULT, drapModel = Drapery.MODEL_AUTO, drapRandomColour = Drapery.RANDOM_DEFAULT, drapCommonColour = Drapery.COMMON_DEFAULT; public int drapAngle = 0; public boolean drapPrediction = true;
        public boolean repelBubble = false, repelFunnel = false, repelDoi = false, repelPet = false, repelRadial = false, repelLabbe = false, repelBias = false, biasCI = false, biasPI = false;   // "Avoid label overlap" (LabelPlacer, the Baujat ggplot2-style placement) per plot; asymmetry-test plot: confidence / prediction band of the regression
        public boolean funnelBack = false;   // funnel plots: x-axis on the back-transformed scale (all transformed effect sizes)
        public String funnelTheme = FunnelThemes.DEFAULT;   // funnel plot and trim-and-fill funnel plot: Default, MetaWin style, STATA style
        public String squareColour = ForestTheme.AUTO, diamondFill = ForestTheme.AUTO, diamondBorder = ForestTheme.AUTO, nullColour = ForestTheme.AUTO, nullStyle = ForestTheme.AUTO, pooledColour = ForestTheme.AUTO, pooledStyle = ForestTheme.AUTO; }

    static class Row { String label; double te, lo, up, w, se; int type; // 0 study,1 diamond,2 header,3 PI,4 blank,5 text
        String[] lcols = new String[0]; String rcol1 = "", rcol2 = "", wtxt = ""; String sEst = "", sLo = "", sUp = "";   // sEst / sLo / sUp: estimate and CI limits as separate strings (RevMan 5 / BMJ / STATA print them in one column)
        String text = ""; double predLo, predUp; boolean hasPred;
        String pTxt = "", rawTxt = "";   // IBM SPSS style table: p-value of the row and the raw (inverse-variance) weight
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
        boolean hasSg = r.hasSubgroups(); final int K = ForestTheme.of(o).kind;   // theme: 0 Default, 1 RevMan 5, 2 BMJ, 3 STATA
        double sc = 0, sr = 0; for (int i = 0; i < n; i++) { if (!Double.isNaN(r.wCommonFull[i])) sc += r.wCommonFull[i]; if (!Double.isNaN(r.wRandomFull[i])) sr += r.wRandomFull[i]; }
        boolean useRandom = s.random, useCommon = s.common;
        List<String> hl = new ArrayList<>();
        if (hasSg) {
            for (int g = 0; g < r.sgLevels.size(); g++) {
                Row h = new Row(); h.type = 2; h.label = (o.showSubgroupName ? (s.subgroupName.isEmpty() ? "Subgroup" : s.subgroupName) + s.sepSubgroup : "") + r.sgLevels.get(g); rows.add(h);
                int[] idx = r.sgIdx.get(g); List<Integer> ids = new ArrayList<>(); for (int i : ord) for (int j : idx) if (i == j) ids.add(i);
                for (int i : ids) rows.add(studyRow(r, i, sc, sr, dg, log));
                Pool p = r.sgPools.get(g); double wsum = 0; for (int i : idx) { double w = useRandom ? r.wRandomFull[i] : r.wCommonFull[i]; if (!Double.isNaN(w)) wsum += w; }
                if (useCommon) rows.add(themed(K, poolRow(r, s, p, s.textCommon, true, dg, 100 * wsumFor(r, idx, true) / sc, idx), p, s, o, true, useRandom, true));
                if (useRandom) rows.add(themed(K, poolRow(r, s, p, s.textRandom + p.randomLabelSuffix, false, dg, 100 * wsumFor(r, idx, false) / sr, idx), p, s, o, true, useCommon, false));
                if (s.predictionSubgroup && !Double.isNaN(p.loPred)) rows.add(piRow(s, p, dg));
                if (o.hetStats && K == 5) for (String tx : hetSpss(p, s, o)) { Row t = new Row(); t.type = 5; t.text = tx; rows.add(t); }
                else if (o.hetStats && K != 3) { Row t = new Row(); t.type = 5; t.text = K == 0 ? hetLine(p, s, true, o) : K == 4 ? hetMetafor(p, s, o) : hetThemed(p, s, K, o); rows.add(t); }
                if (o.testEffectSubgroup) for (String tx : testLines(p, s, true, K)) { Row t = new Row(); t.type = 5; t.text = tx; rows.add(t); }
                rows.add(blank());
            }
        } else for (int i : ord) rows.add(studyRow(r, i, sc, sr, dg, log));
        if (!hasSg || o.overallResults) {
            if (useCommon) rows.add(themed(K, poolRow(r, s, P, s.textCommon, true, dg, 100, allIdx(n)), P, s, o, false, useRandom, true));
            if (useRandom) rows.add(themed(K, poolRow(r, s, P, s.textRandom + P.randomLabelSuffix, false, dg, 100, allIdx(n)), P, s, o, false, useCommon, false));
            if (s.prediction && !Double.isNaN(P.loPred)) rows.add(piRow(s, P, dg));
            if (K == 5) { Row t = new Row(); t.type = 5; t.text = "Model: " + (s.common && s.random ? "Common-effect and random-effects models" : s.random ? "Random-effects model" : "Common-effect model"); rows.add(t); }   // IBM SPSS: the model first
            if (o.hetStats && K == 5) for (String tx : hetSpss(P, s, o)) { Row t = new Row(); t.type = 5; t.text = tx; rows.add(t); }
            else if (o.hetStats && K != 3) { Row t = new Row(); t.type = 5; t.text = K == 0 ? hetLine(P, s, false, o) : K == 4 ? hetMetafor(P, s, o) : hetThemed(P, s, K, o); rows.add(t); }
            if (o.testOverall) for (String tx : testLines(P, s, false, K)) { Row t = new Row(); t.type = 5; t.text = tx; rows.add(t); }
            if (hasSg && o.testSubgroup) { Row t = new Row(); t.type = 5; double q = s.random && !s.common ? r.qbRandom : r.qbCommon, pv = s.random && !s.common ? r.pQbRandom : r.pQbCommon;
                if (s.random && !s.common && !r.qbRandomNote.isEmpty()) t.text = K == 5 ? "Test of subgroup differences: not available (a subgroup has standard error 0)" : "Test for subgroup differences: not available (a subgroup has standard error 0)";
                else t.text = K == 5 ? "Test of subgroup differences: Q = " + Fmt.num(q, 2) + ", df = " + r.dfBetween + ", p-value = " + Fmt.num(pv, 2) : K == 1 || K == 2 ? subgroupThemed(q, r.dfBetween, pv, K) : "Test for subgroup differences: \u03C7\u00B2" + sub(r.dfBetween) + " = " + Fmt.num(q, 2) + ", df = " + r.dfBetween + " (" + IT + "p" + IT + " " + Fmt.pvalEq(pv, 4) + ")"; rows.add(t); }
        }
        if (K == 3 && !s.noWeights()) { Row t = new Row(); t.type = 5; t.text = "NOTE: Weights are from " + (s.random ? "random" : "common") + " effects analysis"; int at = rows.size(); while (at > 0 && rows.get(at - 1).type == 5) at--; rows.add(at, t); }   // STATA: note under the table, before the test lines
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

    /** IBM SPSS style: "Heterogeneity: Tau-squared = 0.03, H-squared = 1.24, I-squared = 0.19" and "Homogeneity: Q = 10.34, df = 9, p-value = 0.32" (I-squared as a fraction, as SPSS prints it). */
    static List<String> hetSpss(Pool p, MetaSpec s, Opts o) {
        int dg = o.digits; String h2 = Double.isNaN(p.I2) || p.I2 >= 1 ? "NA" : Fmt.num(1 / (1 - p.I2), dg);
        StringBuilder b = new StringBuilder("Heterogeneity: ");
        if (s.random) { String t = Fmt.num(p.tau2, dg); if (p.tau2 > 0 && Double.parseDouble(t.replace(',', '.')) == 0) t = t4(p.tau2); b.append("Tau-squared = ").append(t); if (o.ciTau && !Double.isNaN(p.loTau2) && !Double.isNaN(p.upTau2)) b.append(" [").append(t4(p.loTau2)).append("; ").append(t4(p.upTau2)).append("]"); b.append(", "); }
        b.append("H-squared = ").append(h2).append(", I-squared = ").append(Fmt.num(p.I2, dg)); if (o.ciI2 && !Double.isNaN(p.loI2) && !Double.isNaN(p.upI2)) b.append(" [").append(Fmt.num(p.loI2, dg)).append("; ").append(Fmt.num(p.upI2, dg)).append("]");
        return Arrays.asList(b.toString(), "Homogeneity: Q = " + Fmt.num(p.Q, 2) + ", df = " + p.dfQ + ", p-value = " + Fmt.num(p.pQ, dg));
    }
    /** metafor style: "I² = 76.68%, τ² = 0.0362, p = 0.0000" under the table (as the text metafor users add with mtext / the annotation of forest.rma). */
    static String hetMetafor(Pool p, MetaSpec s, Opts o) {
        StringBuilder b = new StringBuilder(IT + "I" + IT + "\u00B2 = " + Fmt.num(100 * p.I2, 2) + "%" + i2ci(p, o, 3));
        if (s.random) b.append(", \u03C4\u00B2 = ").append(Fmt.num(p.tau2, 4)).append(tauCi(p, o, 3));
        return b.append(", " + IT + "p" + IT + " = ").append(Fmt.num(p.pQ, 4)).toString();
    }
    /** RevMan 5 / BMJ: pooled row "Total (95% CI)" ("Subtotal (95% CI)" in subgroups). STATA: "Overall (I-squared = 12.3%, p = 0.571)" (or "Subtotal ...") instead of a separate heterogeneity line. */
    static Row themed(int K, Row w, Pool p, MetaSpec s, Opts o, boolean sub, boolean both, boolean commonRow) {
        if (K == 1 || K == 2) { if (!both) w.label = (sub ? "Subtotal" : "Total") + " (" + Fmt.num(s.levelMa * 100, 0) + "% CI)"; return w; }   // RevMan 5 / BMJ: "Total (95% CI)"
        if (K == 5) { if (!both) w.label = sub ? "Subtotal" : "Overall"; return w; }   // IBM SPSS: "Overall"
        if (K != 3) return w;
        String base = both ? w.label : sub ? "Subtotal" : "Overall";
        w.label = !o.hetStats || (both && commonRow) ? base : base + " (I-squared = " + Fmt.num(100 * p.I2, 1) + "%" + i2ci(p, o, 3) + ", p = " + Fmt.num(p.pQ, 3) + (s.random && o.ciTau && !tauCi(p, o, 3).isEmpty() ? "; tau-squared = " + t4(p.tau2) + tauCi(p, o, 3) : "") + ")"; return w;
    }
    static String tau3(double x) { String t = Fmt.num(x, 3); return t.equals("0.000") && x > 0 ? Fmt.num(x, 4) : t; }
    static String i2txt(Pool p) { return Double.isNaN(p.I2) ? "NA" : Math.round(100 * p.I2) + "%"; }
    /** Heterogeneity line of the RevMan 5 (K = 1) and BMJ (K = 2) themes. */
    static String hetThemed(Pool p, MetaSpec s, int K, Opts o) {
        String i2 = i2ci(p, o, K), tc = tauCi(p, o, K);
        if (K == 1) return "Heterogeneity: " + (s.random ? "Tau\u00B2 = " + tau3(p.tau2) + tc + "; " : "") + "Chi\u00B2 = " + Fmt.num(p.Q, 2) + ", df = " + p.dfQ + " (P " + Fmt.pvalEq(p.pQ, 4) + "); I\u00B2 = " + i2txt(p) + i2;
        return "Test for heterogeneity: " + (s.random ? "\u03C4\u00B2=" + Fmt.num(p.tau2, 2) + tc + "; " : "") + "\u03C7\u00B2=" + Fmt.num(p.Q, 2) + ", df=" + p.dfQ + ", P " + Fmt.pvalEq(p.pQ, 3) + "; I\u00B2=" + i2txt(p) + i2;
    }
    /** Confidence interval of I-squared for the RevMan 5 (K = 1, whole percent), BMJ (K = 2, whole percent) and STATA (K = 3, one decimal) themes; empty unless selected and available. */
    static String i2ci(Pool p, Opts o, int K) {
        if (!o.ciI2 || Double.isNaN(p.loI2) || Double.isNaN(p.upI2)) return "";
        return K == 3 ? " [" + Fmt.num(100 * p.loI2, 1) + "%; " + Fmt.num(100 * p.upI2, 1) + "%]" : " [" + Math.round(100 * p.loI2) + "%; " + Math.round(100 * p.upI2) + "%]";
    }
    /** Confidence interval of tau-squared in the number format of each theme; empty unless selected and available. */
    static String tauCi(Pool p, Opts o, int K) {
        if (!o.ciTau || Double.isNaN(p.loTau2) || Double.isNaN(p.upTau2)) return "";
        return K == 1 ? " [" + tau3(p.loTau2) + "; " + tau3(p.upTau2) + "]" : K == 2 ? " [" + Fmt.num(p.loTau2, 2) + "; " + Fmt.num(p.upTau2, 2) + "]" : " [" + t4(p.loTau2) + "; " + t4(p.upTau2) + "]";
    }
    /** Test line(s) of the forest plot (all themes). None when there is no null hypothesis (single proportions / means / rates without a "Target effect-size"). With both models selected one
     *  labelled line per model, as in meta ("Test for overall effect (common effect): ..." / "(random effects)"); otherwise one line. */
    static List<String> testLines(Pool p, MetaSpec s, boolean sub, int K) {
        List<String> out = new ArrayList<>(); if (!s.hasNull()) return out;
        if (s.common && s.random) { out.add(testLine(p, s, sub, K, false, "common effect")); out.add(testLine(p, s, sub, K, true, "random effects")); }
        else out.add(testLine(p, s, sub, K, s.random, null));
        return out;
    }
    /** One test line. A degenerate pooled result (SE = 0: plain Hartung-Knapp with q = 0, all weighted effects identical) has an infinite or undefined statistic ("t = Inf (p < 0.0001)" is a
     *  numerical artefact), so it is reported as not available instead of as a number. */
    static String testLine(Pool p, MetaSpec s, boolean sub, int K, boolean rnd, String model) {
        String head = (sub ? "Test for effect in subgroup" : "Test for overall effect") + (model == null ? "" : " (" + model + ")") + ": ";
        double st = rnd ? p.statRandom : p.statCommon, pv = rnd ? p.pRandom : p.pCommon;
        if (Double.isInfinite(st) || Double.isNaN(st)) {
            double se = rnd ? p.seRandom : p.seCommon;
            return head + "not available (standard error = 0" + (rnd && se == 0 ? ", Hartung-Knapp with q = 0" : "") + ")";
        }
        boolean tt = p.testType.equals("t") && rnd;
        if (K == 5) return (sub ? "Test of effect size in subgroup" : "Test of overall effect size") + (model == null ? "" : " (" + model + ")") + ": " + (tt ? "t" : "z") + " = " + Fmt.num(st, 2) + ", p-value = " + Fmt.num(pv, 2);   // IBM SPSS style
        if (K == 4) return head + IT + (tt ? "t" : "z") + IT + " = " + Fmt.num(st, 2) + " (" + IT + "p" + IT + " = " + Fmt.num(pv, 4) + ")";   // metafor style: p = 0.0000
        if (K == 1) return head + (tt ? "t" : "Z") + " = " + Fmt.num(st, 2) + " (P " + Fmt.pvalEq(pv, 4) + ")";
        if (K == 2) return head + (tt ? "t" : "Z") + "=" + Fmt.num(st, 2) + ", P " + Fmt.pvalEq(pv, 3);
        return head + IT + (tt ? "t" : "z") + IT + " = " + Fmt.num(st, 2) + " (" + IT + "p" + IT + " " + Fmt.pvalEq(pv, 4) + ")";
    }
    static String subgroupThemed(double q, int df, double pv, int K) {
        if (K == 1) return "Test for subgroup differences: Chi\u00B2 = " + Fmt.num(q, 2) + ", df = " + df + " (P " + Fmt.pvalEq(pv, 4) + "), I\u00B2 = " + Fmt.num(q > df ? 100 * (q - df) / q : 0, 1) + "%";
        return "Test for subgroup differences: \u03C7\u00B2=" + Fmt.num(q, 2) + ", df=" + df + ", P " + Fmt.pvalEq(pv, 3);
    }

    static Row studyRow(MetaResult r, int i, double sc, double sr, int dg, boolean log) {
        MetaSpec s = r.spec; Row w = new Row(); w.type = 0; w.label = r.studlab[i]; w.te = r.TE[i]; w.se = r.seTE[i]; w.lo = r.lower[i]; w.up = r.upper[i];
        double wt = s.random ? r.wRandomFull[i] : r.wCommonFull[i]; double tot = s.random ? sr : sc; w.w = Double.isNaN(wt) ? 0 : wt / tot;
        if (s.noWeights()) w.w = 1;   // GLMM: no study weights, equally sized squares
        w.n = Report.nOf(s, i); w.lcols = leftCols(r, i, dg); double t = Report.studyEst(r, i); if (s.type.equals("prop") || s.type.equals("rate")) w.ptBack = t;
        w.rcol1 = Double.isNaN(r.TE[i]) ? "NA" : Fmt.num(t, dg); w.rcol2 = Double.isNaN(r.TE[i]) ? "" : Fmt.ci(Report.studyLo(r, i), Report.studyUp(r, i), dg);
        w.sEst = w.rcol1; if (!Double.isNaN(r.TE[i])) { w.sLo = Fmt.num(Report.studyLo(r, i), dg); w.sUp = Fmt.num(Report.studyUp(r, i), dg); }
        w.wtxt = s.noWeights() ? "" : Fmt.num(100 * w.w, 1) + "%";
        if (s.hasNull() && !Double.isNaN(r.TE[i]) && r.seTE[i] > 0) w.pTxt = Fmt.num(Dist.pnormTwoSided((r.TE[i] - s.nullValue()) / r.seTE[i]), dg);   // IBM SPSS style: p-value of the study (z-test against the null value)
        if (!s.noWeights() && !Double.isNaN(wt)) w.rawTxt = Fmt.num(wt, dg);
        return w;
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
        w.sEst = w.rcol1; w.sLo = Fmt.num(Meta.back(s, w.lo, w.n), dg); w.sUp = Fmt.num(Meta.back(s, w.up, w.n), dg);
        if (s.hasNull()) w.pTxt = Fmt.num(common ? p.pCommon : p.pRandom, dg);
        w.lcols = sums(r, idx); return w;
    }
    static Row piRow(MetaSpec s, Pool p, int dg) { Row w = new Row(); w.type = 3; w.label = s.textPredict + (Math.abs(s.levelPredict - 0.95) > 1e-9 ? " (" + Report.pct(s.levelPredict) + "%)" : ""); w.te = p.teRandom; w.lo = p.loPred; w.up = p.upPred; w.n = p.nHarm; w.rcol2 = Fmt.ci(Meta.back(s, w.lo, w.n), Meta.back(s, w.up, w.n), dg); w.sLo = Fmt.num(Meta.back(s, w.lo, w.n), dg); w.sUp = Fmt.num(Meta.back(s, w.up, w.n), dg); return w; }
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

    /** One column of a forest plot that is placed left or right of the plot area (weight, estimate, 95% CI ...). align / hAlign: 0 left, 1 centre, 2 right. */
    static final class Col {
        final String h1, h2; final java.util.function.Function<Row, String> val; final int align, hAlign; int w, xl;
        Col(String h1, String h2, java.util.function.Function<Row, String> val, int align, int hAlign) { this.h1 = h1; this.h2 = h2; this.val = val; this.align = align; this.hAlign = hAlign; }
        double ax(int al) { return al == 0 ? xl : al == 1 ? xl + w / 2.0 : xl + w; }
    }

    /** "IV", "M-H", "Peto" ... as RevMan / BMJ print the pooling method. */
    static String methodTag(MetaSpec s) { switch (s.method) { case "Inverse": return "IV"; case "MH": return "M-H"; default: return s.method; } }

    /** Estimate and CI in one string, as RevMan 5 "0.36 [0.30, 0.44]", BMJ "0.36 (0.30 to 0.44)" and Stata "0.36 (0.30, 0.44)". */
    static String comb(int K, Row w) {
        String o = K == 1 || K == 4 ? "[" : "(", sep = K == 2 ? " to " : ", ", c = K == 1 || K == 4 ? "]" : ")";
        if (w.type == 3) return o + w.sLo + sep + w.sUp + c;
        if (w.sLo.isEmpty() || w.sEst.equals("NA")) return w.sEst;
        return w.sEst + " " + o + w.sLo + sep + w.sUp + c;
    }

    static boolean boldRow(ForestTheme T, Row w) { if (T.kind == 4) return w.type == 1 || w.type == 2;   /* metafor: pooled rows and subgroup headings bold, prediction interval row plain */ return w.type == 0 ? false : w.type == 2 ? T.kind != 3 : T.boldPool; }

    /** Forest plot renderer. The look follows the theme in Opts (Default = meta::forest, RevMan 5, BMJ, STATA), then the individual colour / line-style overrides. */
    static BufferedImage drawForest(List<Row> rows, MetaResult r, Opts o, boolean log, String smL0, boolean showWeight, boolean loo) { return drawForest(rows, r, o, log, smL0, showWeight, loo, null); }
    /** serTitle: heading of the plot column of a leave-one-out / cumulative forest plot (\"Leave-One-Out Meta-Analysis\", \"Cumulative Meta-Analysis\"), as meta::forest; null for ordinary forest plots. */
    static BufferedImage drawForest(List<Row> rows, MetaResult r, Opts o, boolean log, String smL0, boolean showWeight, boolean loo, String serTitle) {
        MetaSpec s = r.spec; final String smL = logRatio(s) ? "log" + smL0 : smL0; /* results not back-transformed: effect column headed logOR, logHR, ... */ final ForestTheme T = ForestTheme.of(o); final int K = T.kind; final float FS = 12f; int rowH = (int) Math.round(T.rowBase * o.spacing), M = 14, nrow = rows.size();
        final String measure = forestMeasure(s), lvl = Fmt.num(s.levelMa * 100, 0) + "% CI", mt = methodTag(s), mdl = s.random ? "Random" : "Fixed";
        // ---- left-hand data columns (events, totals, means ...). BMJ merges events and total into "e / n"; STATA shows no data columns.
        String[] lh0 = loo ? (s.hasNull() ? new String[]{"P-value", "Tau²", "Tau", "I²"} : new String[]{"Tau²", "Tau", "I²"}) : leftHead(s);
        final boolean moveR = loo && (K == 0 || K == 2 || K == 4) && !T.noDataCols;   // Default (meta) and BMJ: P-value, tau^2, tau and I^2 right of the plot, after the effect and its CI (as meta::forest); RevMan 5 keeps them on the left
        final boolean merge = K == 2 && !loo && (s.type.equals("bin") || s.type.equals("inc") || s.type.equals("prop") || s.type.equals("rate")), noData = T.noDataCols;
        final String[] lh = noData || moveR ? new String[0] : merge ? (s.type.equals("bin") || s.type.equals("inc") ? new String[]{ s.labelE == null ? "" : s.labelE, s.labelC == null ? "" : s.labelC } : new String[]{ "" }) : lh0;
        final int nl = lh.length;
        final java.util.function.Function<Row, String[]> LC = w -> { if (noData) return new String[0]; if (!merge) return w.lcols; String[] a = w.lcols, m = new String[a.length / 2];
            for (int k = 0; k < m.length; k++) m[k] = a[2 * k].isEmpty() && a[2 * k + 1].isEmpty() ? "" : a[2 * k] + " / " + a[2 * k + 1]; return m; };
        Canvas probe = Canvas.probe(); FontMetrics fm = probe.g.getFontMetrics(K == 5 ? Canvas.mono(FS, false) : Canvas.font(FS)), fb = probe.g.getFontMetrics(K == 5 ? Canvas.mono(FS, true) : Canvas.bold(FS)), fh = T.boldHead ? fb : fm;   // IBM SPSS style: the table is monospaced
        java.util.function.Function<Row, FontMetrics> FR = w -> boldRow(T, w) ? fb : fm;
        final String[] sh = K == 2 ? new String[]{"Study or", "subgroup"} : K == 3 ? new String[]{"Study", "ID"} : K == 5 ? new String[]{"", "ID"} : new String[]{"", "Study"};
        int labW = Math.max(fh.stringWidth(sh[0]), fh.stringWidth(sh[1]));
        for (Row w : rows) if (w.type == 0 || w.type == 1 || w.type == 2 || w.type == 3) labW = Math.max(labW, FR.apply(w).stringWidth(w.label == null ? "" : w.label));
        int[] cw = new int[nl]; for (int j = 0; j < nl; j++) { cw[j] = fh.stringWidth(lh[j]); for (Row w : rows) { String[] lc = LC.apply(w); if (j < lc.length) cw[j] = Math.max(cw[j], FR.apply(w).stringWidth(lc[j])); } }
        // column groups above the headings: "Experimental" / "Control" (meta, RevMan 5), "No of events / total" (BMJ)
        final List<Object[]> groups = new ArrayList<>();   // {text, first col, last col}
        if (merge) groups.add(new Object[]{"No of events / total", 0, nl - 1});
        else if (!loo && (s.type.equals("bin") || s.type.equals("cont") || s.type.equals("inc")) && nl >= 4 && nl % 2 == 0 && K != 3) { int gn = nl / 2; groups.add(new Object[]{ s.labelE == null ? "" : s.labelE, 0, gn - 1 }); groups.add(new Object[]{ s.labelC == null ? "" : s.labelC, gn, nl - 1 }); }
        final boolean grp = !groups.isEmpty(); final int gh = grp || T.twoLineHead ? 18 : 0;
        for (Object[] g2 : groups) { int a0 = (Integer) g2[1], b0 = (Integer) g2[2], span = 0; for (int j = a0; j <= b0; j++) span += cw[j]; span += 14 * (b0 - a0); int need = fb.stringWidth((String) g2[0]); if (need > span) cw[b0] += need - span; }
        int[] xr = new int[nl]; int cur = M + labW + (K == 3 ? 26 : 16); for (int j = 0; j < nl; j++) { cur += cw[j]; xr[j] = cur; cur += 14; }
        // ---- columns between the data columns and the plot (RevMan 5) and to the right of the plot (all other themes)
        final List<Col> pre = new ArrayList<>(), post = new ArrayList<>(); int[] gap;
        final String mPre = measure.startsWith("Log ") ? "Log " : "", mRest = measure.substring(mPre.length());   // "Log Odds Ratio" is split as "Log Odds" / "ratio"
        final String measure1 = mPre + (mRest.contains(" ") ? mRest.substring(0, mRest.indexOf(' ')) : mRest), measure2 = mRest.contains(" ") ? mRest.substring(mRest.indexOf(' ') + 1).toLowerCase(Locale.ROOT) + " (" + lvl + ")" : "(" + lvl + ")";
        final java.util.function.Function<Row, String> CB = w -> comb(K, w);
        switch (K) {
            case 1: if (showWeight) pre.add(new Col("", "Weight", w -> w.wtxt, 2, 2)); pre.add(new Col(measure, mt + ", " + mdl + ", " + lvl, CB, 2, 2)); gap = new int[0]; break;
            case 2: if (showWeight) post.add(new Col("Weight", "(%)", w -> w.wtxt.replace("%", ""), 2, 2)); post.add(new Col(measure + ", " + mt + ",", mdl.toLowerCase(Locale.ROOT) + " (" + lvl + ")", CB, 0, 1)); gap = new int[]{34, 18}; break;
            case 3: post.add(new Col(measure1, measure2, CB, 0, 0)); if (showWeight) post.add(new Col("%", "Weight", w -> w.wtxt.isEmpty() || (w.type != 0 && w.type != 1) ? "" : Fmt.num(100 * w.w, 2), 0, 0)); gap = new int[]{30, 34}; break;
            case 4: if (showWeight) post.add(new Col("", "Weight", w -> w.type == 0 ? w.wtxt : "", 2, 2)); post.add(new Col("", measure + " [" + lvl + "]", CB, 2, 2)); gap = showWeight ? new int[]{34, 22} : new int[]{30}; break;   // metafor style: Weight, then "estimate [CI]" in one column
            case 5: {   // IBM SPSS style: ID | effect | Lower | Upper | p-value | Weight | Weight (%) left of the plot (leave-one-out / cumulative: P-value, tau^2, tau, I^2 instead of the weights)
                pre.add(new Col("", smL, w -> w.type == 3 ? "" : w.sEst, 2, 2)); pre.add(new Col("", "Lower", w -> w.sLo, 2, 2)); pre.add(new Col("", "Upper", w -> w.sUp, 2, 2));
                if (loo) { for (int j = 0; j < lh0.length; j++) { final int jj = j; pre.add(new Col("", lh0[j], w -> jj < w.lcols.length ? w.lcols[jj] : "", 2, 2)); } }
                else { if (s.hasNull()) pre.add(new Col("", "p-value", w -> w.pTxt, 2, 2)); if (showWeight) { pre.add(new Col("", "Weight", w -> w.rawTxt, 2, 2)); pre.add(new Col("", "Weight (%)", w -> w.type == 0 ? Fmt.num(100 * w.w, 2) : "", 2, 2)); } }
                gap = new int[0]; break; }
            default: post.add(new Col("", smL, w -> w.type == 3 ? "" : w.rcol1, 2, 2)); post.add(new Col("", lvl, w -> w.rcol2, 2, 2)); if (showWeight) post.add(new Col("", "Weight", w -> w.wtxt, 2, 2)); gap = new int[]{22, 12, 10};
        }
        if (moveR) { int n0 = post.size(); for (int j = 0; j < lh0.length; j++) { final int jj = j; post.add(new Col("", lh0[j], w -> jj < w.lcols.length ? w.lcols[jj] : "", 2, 2)); }
            int[] g3 = Arrays.copyOf(gap, post.size()); for (int j = n0; j < g3.length; j++) g3[j] = 14; gap = g3; }
        final boolean RICH = K == 0 || K == 4;   // italic markers (z, p, I) in the footer lines
        final String serT = !loo ? null : serTitle != null ? serTitle : "Leave-One-Out Meta-Analysis";
        for (Col c : pre) { c.w = Math.max(fh.stringWidth(c.h1), fh.stringWidth(c.h2)); for (Row w : rows) if (w.type == 0 || w.type == 1 || w.type == 3) c.w = Math.max(c.w, FR.apply(w).stringWidth(c.val.apply(w))); c.xl = cur; cur += c.w + 14; }
        for (Col c : post) { c.w = Math.max(fh.stringWidth(c.h1), fh.stringWidth(c.h2)); for (Row w : rows) if (w.type == 0 || w.type == 1 || w.type == 3) c.w = Math.max(c.w, FR.apply(w).stringWidth(c.val.apply(w))); }
        int plotW = serT == null ? T.plotW : Math.max(T.plotW, (K == 0 || K == 3 || K == 4 || K == 5 ? fb.stringWidth(serT) : fb.stringWidth(serT.substring(serT.indexOf(' ') + 1))) + 10), px0 = cur, px1 = px0 + plotW; { int pc = px1; for (int i = 0; i < post.size(); i++) { post.get(i).xl = pc + gap[i]; pc = post.get(i).xl + post.get(i).w; } }
        int W = (post.isEmpty() ? px1 : post.get(post.size() - 1).xl + post.get(post.size() - 1).w) + M;
        // ---- axis range
        // For measures with a back-transformation (proportions, correlations, ...) the axis is drawn on the back-transformed (original) scale, as meta::forest does.
        final boolean sg = singleGroup(s.sm); final boolean lin = s.backtransf && (linBack(s.sm) || (log && sg)); final double[] bnd = linBounds(s);
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY, wmax = 0;
        for (Row w : rows) if (w.type == 0 || w.type == 1 || w.type == 3) { double pe = ptPos(s, lin, w), pl = lin ? P(s, true, w, w.lo) : w.lo, pu = lin ? P(s, true, w, w.up) : w.up; double a = Math.min(pl, pe), b = Math.max(pu, pe); if (!Double.isNaN(a) && !Double.isInfinite(a)) lo = Math.min(lo, a); if (!Double.isNaN(b) && !Double.isInfinite(b)) hi = Math.max(hi, b); if (w.type == 0) wmax = Math.max(wmax, w.w); }
        double refT = s.nullValue(); final boolean hasRef = sg ? !Double.isNaN(refT) : (!lin || !Double.isNaN(refT));
        double ref = Double.isNaN(refT) ? 0 : refT; if (lin && hasRef) ref = lg(s, sg && s.backtransf && !Double.isNaN(s.nullEffect) ? s.nullEffect : Meta.back(s, refT, Double.NaN));   // single-group: the target is already on the displayed scale (incl. pscale / irscale); PFT / IRFT cannot be back-transformed without n
        // The axis is centred on the null value (as meta::forest, xlim = "symmetric") for measures with a natural null value. For single proportions, rates and means the
        // "target effect size" is only a reference line: it must not squeeze the axis (an extreme target such as 0.9 would otherwise clip every study), so the axis covers the data and the target.
        final boolean centre = hasRef && !sg;
        if (hasRef) { lo = Math.min(lo, ref); hi = Math.max(hi, ref);
            if (centre) { double half = Math.max(ref - lo, hi - ref); if (lin) half = Math.min(half, Math.min(ref - bnd[0], bnd[1] - ref)); if (half > 0) { lo = ref - half; hi = ref + half; } } }
        if (!Double.isFinite(lo) || !Double.isFinite(hi)) { lo = lin ? bnd[0] : -1; hi = lin ? bnd[1] : 1; }
        final double lo0 = lo, hi0 = hi; double pad = (hi - lo) * 0.04 + 1e-9; lo -= pad; hi += pad;
        if (lin) { if (centre) { double h2 = Math.min(hi - ref, ref - lo); h2 = Math.min(h2, Math.min(ref - bnd[0], bnd[1] - ref)); lo = ref - h2; hi = ref + h2; } else { lo = Math.max(lo, bnd[0]); hi = Math.min(hi, bnd[1]); } }
        // Log-transformed single-group measures (PLN, IRLN, MLN): the limits are "round" values of the 1-2-5 sequence (..., 0.01, 0.02, 0.05, 0.1, 0.2, 0.5, 1, 2, 5, 10, ...) that always
        // contain every interval drawn (studies, pooled CI, prediction interval, target): the lower limit is the largest such value <= the lowest bound (0.09 -> 0.05, 0.02 -> 0.02, 0.003 -> 0.002),
        // the upper limit the smallest value >= the highest bound (12 -> 20, 80 -> 100, 663 -> 1000). PLN stays within the scaling factor.
        if (log && sg && Double.isFinite(lo0) && Double.isFinite(hi0)) {
            double vLo = Math.exp(lo0), vHi = Math.exp(hi0); lo = Math.log(round125(vLo, false)); hi = Math.log(round125(vHi, true));
            if (hi <= lo) hi = lo + Math.log(2); if (bnd[1] < hi) hi = Math.max(bnd[1], Math.max(hi0, lo + 1e-6));
        }
        // ---- user-defined x-axis limits (given on the displayed scale; for ratio measures on the log axis they are ratios)
        { double uMin = parseAxis(o.xMin), uMax = parseAxis(o.xMax); double span = hi - lo;
          if (log) { if (!(uMin > 0)) uMin = Double.NaN; if (!(uMax > 0)) uMax = Double.NaN; if (!Double.isNaN(uMin)) uMin = Math.log(uMin); if (!Double.isNaN(uMax)) uMax = Math.log(uMax); }
          double nlo = Double.isNaN(uMin) ? lo : uMin, nhi = Double.isNaN(uMax) ? hi : uMax;
          if (!(nhi > nlo)) { if (!Double.isNaN(uMin) && Double.isNaN(uMax)) nhi = nlo + span; else if (Double.isNaN(uMin) && !Double.isNaN(uMax)) nlo = nhi - span; else { nlo = lo; nhi = hi; } }
          lo = nlo; hi = nhi; }
        LAST_AXIS.set(new double[]{log ? Math.exp(lo) : lo, log ? Math.exp(hi) : hi});
        final double lo2 = lo, hi2 = hi; final int fx0 = px0, fw = plotW; final double[] sh0 = {0}; java.util.function.DoubleUnaryOperator X = v -> fx0 + sh0[0] + (v - lo2) / (hi2 - lo2) * fw;
        if (wmax <= 0) wmax = 1;
        // ---- IBM SPSS style: key above the table (symbols follow the colour options of the theme), plot title above the plot
        final List<String[]> leg5 = new ArrayList<>(); int legRows5 = 0, legW5 = 0; final int colGap5 = 44; int[] legCol5 = new int[2];
        final double pooledNow = s.random ? r.overall.teRandom : r.overall.teCommon;
        if (K == 5) {
            boolean anyPool = false, anyPI = false; for (Row w : rows) { if (w.type == 1) anyPool = true; if (w.type == 3) anyPI = true; }
            leg5.add(new String[]{"sq", "Effect size of each study"}); if (anyPool) { leg5.add(new String[]{"dia", "Estimated overall effect size"}); leg5.add(new String[]{"ibeam", "Estimated overall confidence interval"}); }
            leg5.add(new String[]{"vbar", "Confidence interval of effect size"});
            final double ppl = Double.isNaN(pooledNow) ? Double.NaN : lin ? lg(s, Meta.back(s, pooledNow, r.overall.nHarm)) : pooledNow;
            if (anyPool && !loo && !Double.isNaN(ppl) && ppl >= lo - 1e-9 && ppl <= hi + 1e-9) leg5.add(new String[]{"pooled", "Overall effect size value"});
            if (hasRef && ref >= lo - 1e-9 && ref <= hi + 1e-9) leg5.add(new String[]{"null", "No-effect value"});
            if (anyPI) leg5.add(new String[]{"pi", "Prediction interval"});
            int n1 = Math.min(3, leg5.size()); legRows5 = Math.max(n1, leg5.size() - n1); FontMetrics f12 = probe.g.getFontMetrics(Canvas.font(12f));
            for (int k = 0; k < leg5.size(); k++) { int cI = k < n1 ? 0 : 1; legCol5[cI] = Math.max(legCol5[cI], f12.stringWidth(leg5.get(k)[1])); }
            legW5 = M + 32 + legCol5[0] + (leg5.size() > n1 ? colGap5 + 32 + legCol5[1] : 0) + M;
        }
        final double top5 = K == 5 ? 14 + legRows5 * 22 + 8 : 0;
        // ---- vertical layout of the table
        int tail = nrow; while (tail > 0 && rows.get(tail - 1).type == 5) tail--; int lastPool = -1; for (int i = 0; i < tail; i++) if (rows.get(i).type == 1) lastPool = i;
        final double hy = 12 + gh + 12 + top5, ruleY = hy + 8;
        double y = 12 + gh + 14 + rowH * 0.9 + top5; double[] cy = new double[nrow]; int firstPool = -1;
        for (int i = 0; i < tail; i++) {
            Row w = rows.get(i);
            if (w.type == 1 && firstPool < 0) { firstPool = i; if (i > 0 && rows.get(i - 1).type == 0) y += rowH * 0.6; }
            else if (K == 4 && w.type == 1 && i > 0 && rows.get(i - 1).type == 0) y += rowH * 0.6;   // metafor style: room for the rule above each summary
            double h = w.type == 4 ? rowH * 0.6 : w.type == 5 ? rowH * 0.75 : rowH; cy[i] = y + h / 2; y += h;
        }
        final double yFirstTop = (K == 5 ? hy - 8 : T.headRule != null ? ruleY : 12 + gh + 14 + rowH * 0.9 - 4), yTableEnd = y;
        // ---- axis ticks (computed up-front: the footer layout depends on the axis extent)
        double[] ticks = log ? logTicks(Math.exp(lo), Math.exp(hi)) : userTicks(lo, hi, parseAxis(o.xTick)); if (ticks == null) ticks = Canvas.pretty(lo, hi, 5); double tmin = Double.POSITIVE_INFINITY, tmax = Double.NEGATIVE_INFINITY;
        double axL = Double.POSITIVE_INFINITY, axR = Double.NEGATIVE_INFINITY; FontMetrics ft = probe.g.getFontMetrics(Canvas.font(T.tickSize));
        final int[] tdec = {0};   // metafor style: all tick labels with the same number of decimals (0.1, 0.5, 1.0, 2.0, 10.0)
        java.util.function.Function<Double, String> TK = t -> K == 3 ? Canvas.tick(t).replaceFirst("^(-?)0\\.", "$1.") : K == 4 && tdec[0] > 0 ? String.format(Locale.ROOT, "%." + tdec[0] + "f", t) : Canvas.tick(t);
        if (log && sg) {   // powers of ten first, then 5 x and 2 x of them if the labels do not overlap
            List<double[]> cand = new ArrayList<>(); int kLo = (int) Math.floor(Math.log10(Math.exp(lo))) - 1, kHi = (int) Math.ceil(Math.log10(Math.exp(hi))) + 1;
            for (int k = kLo; k <= kHi; k++) for (double mlt : new double[]{1, 5, 2}) { double val = mlt * Math.pow(10, k), v = Math.log(val); if (v >= lo - 1e-9 && v <= hi + 1e-9 && k >= -4) cand.add(new double[]{val, Math.abs(v - lo) < 1e-9 || Math.abs(v - hi) < 1e-9 ? -1 : mlt == 1 ? 0 : mlt == 5 ? 1 : 2}); }
            cand.sort((p1, p2) -> Double.compare(p1[1], p2[1])); List<double[]> acc = new ArrayList<>();
            for (double[] cd : cand) { double xx = X.applyAsDouble(Math.log(cd[0])), hw = ft.stringWidth(TK.apply(cd[0])) / 2.0; boolean ok = true;
                for (double[] ac : acc) { double xa = X.applyAsDouble(Math.log(ac[0])), ha = ft.stringWidth(TK.apply(ac[0])) / 2.0; if (Math.abs(xx - xa) < hw + ha + 6) { ok = false; break; } }
                if (ok) acc.add(cd); }
            if (acc.size() >= 2) { acc.sort((p1, p2) -> Double.compare(p1[0], p2[0])); ticks = new double[acc.size()]; for (int i = 0; i < ticks.length; i++) ticks[i] = acc.get(i)[0]; }
        }
        if (K == 4) { int dmax = 0; for (double tk0 : ticks) { String ts = Canvas.tick(tk0); int dot = ts.indexOf('.'); if (dot >= 0) dmax = Math.max(dmax, Math.min(3, ts.length() - dot - 1)); } tdec[0] = dmax; }
        { List<Double> keepT = new ArrayList<>(); double lastR = Double.NEGATIVE_INFINITY;   // drop tick labels that would overlap their left neighbour
          for (double tk0 : ticks) { double v0 = log ? Math.log(tk0) : tk0; if (v0 < lo - 1e-9 || v0 > hi + 1e-9) { keepT.add(tk0); continue; }
            double x0 = X.applyAsDouble(v0), h0 = ft.stringWidth(TK.apply(tk0)) / 2.0; if (x0 - h0 >= lastR + 5) { keepT.add(tk0); lastR = x0 + h0; } }
          ticks = keepT.stream().mapToDouble(Double::doubleValue).toArray(); }
        for (double t : ticks) { double v = log ? Math.log(t) : t; if (v < lo - 1e-9 || v > hi + 1e-9) continue; tmin = Math.min(tmin, v); tmax = Math.max(tmax, v);
            double xx = X.applyAsDouble(v), hw = ft.stringWidth(TK.apply(t)) / 2.0; axL = Math.min(axL, xx - hw); axR = Math.max(axR, xx + hw); }
        boolean xlab2 = !o.xLeft.isEmpty() || !o.xRight.isEmpty();
        // positions of the "Favours ..." labels: under the ends of the axis, or (STATA) centred on each half of the axis
        double nullPos = hasRef ? ref : (tmin + tmax) / 2; if (!(tmin <= tmax)) { tmin = lo; tmax = hi; }
        double xlC = K == 3 ? (tmin + nullPos) / 2 : tmin, xrC = K == 3 ? (nullPos + tmax) / 2 : tmax;
        // ---- footer text (heterogeneity, tests, notes, methods)
        String[] dl = o.analysisDetails ? Report.details(r).split("\n") : new String[0];
        FontMetrics f11 = probe.g.getFontMetrics(Canvas.font(11f)), f11b = probe.g.getFontMetrics(Canvas.bold(11f));
        java.util.List<String> fTxt = new ArrayList<>(); java.util.List<Boolean> fBold = new ArrayList<>(), fRich = new ArrayList<>(); java.util.List<Double> fGap = new ArrayList<>();
        for (int i = tail; i < nrow; i++) { fTxt.add(RICH ? rows.get(i).text : rows.get(i).text.replace(IT, "")); fBold.add(false); fRich.add(RICH); fGap.add(fTxt.size() == 1 ? 0.0 : 15.0); }
        final int nNote = fTxt.size();   // lines before the methods details: weights note, heterogeneity / test lines
        if (dl.length > 0) { fTxt.add("Details of meta-analysis methods:"); fBold.add(true); fRich.add(false); fGap.add(fTxt.size() == 1 ? 0.0 : 25.0);
            for (int i = 1; i < dl.length; i++) { fTxt.add(dl[i]); fBold.add(false); fRich.add(false); fGap.add(14.0); } }
        double ay, ruleNote = -1; double[] fy = new double[fTxt.size()];
        if (K == 3) {   // STATA: the note / test lines sit directly under the last table row (prediction interval), the axis under them, and the methods details under a rule below the axis
            double tcur = yTableEnd + 14; for (int i = 0; i < nNote; i++) { tcur += i == 0 ? 0 : fGap.get(i); fy[i] = tcur; }
            ay = nNote > 0 ? fy[0] + 8 : yTableEnd + rowH * 0.5;   // axis line level with the note lines (the plot is shifted right of them, see below)
        } else ay = y + rowH * 0.5;
        double axTop = ay - 3, axBot = ay + (xlab2 ? 38 : 20) + 5 + (K == 4 ? 22 : 0);   // vertical band occupied by the axis, tick labels and optional left/right labels
        if (K == 3 && fy.length > nNote) { ruleNote = Math.max(axBot, nNote > 0 ? fy[nNote - 1] + 6 : axBot) + 16; double tcur = ruleNote + 20; for (int i = nNote; i < fy.length; i++) { tcur += i == nNote ? 0 : fGap.get(i); fy[i] = tcur; } }
        if (K == 4 && tmin <= tmax) { double mid = (X.applyAsDouble(tmin) + X.applyAsDouble(tmax)) / 2.0, half = fm.stringWidth(measure) / 2.0; axL = Math.min(axL, mid - half); axR = Math.max(axR, mid + half); }   // axis title
        if (xlab2) { FontMetrics f11x = f11; if (!o.xLeft.isEmpty()) axL = Math.min(axL, X.applyAsDouble(xlC) - f11x.stringWidth(o.xLeft) / 2.0); if (!o.xRight.isEmpty()) axR = Math.max(axR, X.applyAsDouble(xrC) + f11x.stringWidth(o.xRight) / 2.0); }
        // ---- extra gap between the left columns and the plot: if the heterogeneity / test lines are slightly too long to fit left of the axis,
        // shift the plot (and the columns right of it) to the right instead of moving the whole footer below the axis (capped at 140 px).
        { double need = 0;   // STATA: the note lines stand at the level of the axis, so the plot is moved right until the longest line ends clearly left of the axis (no cap)
          if (K == 5) { /* the footer sits below the axis */ } else if (K != 3) for (int i = tail; i < nrow; i++) need = Math.max(need, M + f11.stringWidth(rows.get(i).text.replace(IT, "")) + 6 + 8);
          else for (int i = 0; i < nNote; i++) need = Math.max(need, M + f11.stringWidth(fTxt.get(i)) + 6 + 18);
          int extra = (int) Math.ceil(need - axL); if (extra > 0 && (K == 3 || extra <= 140 || o.ciI2 || o.ciTau)) { sh0[0] = extra; px0 += extra; px1 += extra; for (Col c : post) c.xl += extra; W += extra; axL += extra; axR += extra; } }
        if (K == 5) { W = Math.max(W, legW5); for (int i = 0; i < fTxt.size(); i++) W = Math.max(W, (int) Math.ceil(M + (fBold.get(i) ? f11b : f11).stringWidth(fTxt.get(i)) + 14)); }   // key and footer lines must fit
        W = Math.max(W, (int) Math.ceil(axR + M));
        // ---- footer text: starts directly under the last row, as in meta::forest, and is only pushed below the axis if a line would actually overlap the axis / tick labels horizontally.
        double tyEnd = y;
        if (K != 3) { double ty0 = K == 5 ? Math.max(y + 15, axBot + 12) : y + 15, tcur = ty0; boolean first = true;
            for (int i = 0; i < fy.length; i++) {
                tcur = first ? ty0 : tcur + fGap.get(i); first = false;
                String plain = fTxt.get(i).replace("\u0001", ""); double right = M + (fBold.get(i) ? f11b : f11).stringWidth(plain) + 6;   // +6: safety margin for italic glyphs
                if (right > axL - 8 && tcur + 4 > axTop && tcur - 11 < axBot) tcur = axBot + 12;   // would collide with the axis -> move below it
                fy[i] = tcur; tyEnd = tcur;
            } }
        double yEnd = K == 3 ? (fy.length > nNote ? fy[fy.length - 1] + 6 : Math.max(axBot, nNote > 0 ? fy[nNote - 1] + 6 : axBot)) + 4 : Math.max(fy.length > 0 ? tyEnd + 4 : y, Math.max(axBot, y));
        int H = (int) Math.ceil(yEnd + 14);
        // ---- STATA: pale frame, navy title, white panel
        final String title = K != 3 ? null : !s.title.isEmpty() ? s.title : (s.random ? "Random effects" : "Fixed effects") + " Meta-Analysis of " + plural(measure);
        int ox = 0, oy = 0, oyb = 0, Wt = W, Ht = H;
        if (K == 3) { ox = 12; oy = 44; oyb = 12; Wt = Math.max(W, probe.g.getFontMetrics(Canvas.font(17f)).stringWidth(title) + 40) + 2 * ox; Ht = H + oy + oyb; if (Wt - 2 * ox > W) { int add = (Wt - 2 * ox - W); W += add; } }
        Canvas c = new Canvas(Wt, Ht, o.scale); Graphics2D g = c.g;
        if (K == 3) {
            g.setColor(T.bgOuter); g.fill(new Rectangle2D.Double(0, 0, Wt, Ht)); g.setColor(T.titleCol); c.text(title, Wt / 2.0, 28, 17f, 1, false);
            g.setColor(Color.WHITE); g.fill(new Rectangle2D.Double(ox, oy, Wt - 2 * ox, Ht - oy - oyb)); g.setColor(T.boxBorder); g.setStroke(new BasicStroke(1f)); g.draw(new Rectangle2D.Double(ox, oy, Wt - 2 * ox, Ht - oy - oyb));
            g.translate(ox, oy);
        }
        final int Wc = W;
        // ---- header
        g.setColor(Color.BLACK); final boolean hb = T.boldHead; c.mono = K == 5;
        c.text(sh[0], M, hy - gh, FS, 0, hb); c.text(sh[1], M, hy, FS, 0, hb);
        for (Object[] g2 : groups) { int a0 = (Integer) g2[1], b0 = (Integer) g2[2]; double xl = xr[a0] - cw[a0], xrr = xr[b0]; c.text((String) g2[0], (xl + xrr) / 2.0, hy - gh, FS, 1, true);
            if (merge) { g.setStroke(new BasicStroke(1f)); c.line(xl, hy - gh + 4, xrr, hy - gh + 4); } }
        for (int j = 0; j < nl; j++) { if (merge) c.text(lh[j], xr[j] - cw[j] / 2.0, hy, FS, 1, true); else c.text(lh[j], xr[j], hy, FS, 2, hb); }
        for (Col col : pre) { c.text(col.h1, col.ax(col.hAlign), hy - gh, FS, col.hAlign, hb); c.text(col.h2, col.ax(col.hAlign), hy, FS, col.hAlign, hb); }
        for (Col col : post) { c.text(col.h1, col.ax(col.hAlign), hy - gh, FS, col.hAlign, hb); c.text(col.h2, col.ax(col.hAlign), hy, FS, col.hAlign, hb); }
        double pcx = (px0 + px1) / 2.0;
        if (K == 5) {   // IBM SPSS style: the measure heads its own table column; key at the top left, title above the plot
            c.mono = false; g.setColor(Color.BLACK);
            String ttl = serT != null ? serT : !s.title.isEmpty() ? s.title : "Forest Plot"; c.text(ttl, pcx, 26, 14f, 1, true);
            int n1 = Math.min(3, leg5.size()); final double x2 = M + 32 + legCol5[0] + colGap5;
            for (int k = 0; k < leg5.size(); k++) { int cI = k < n1 ? 0 : 1; int rI = k < n1 ? k : k - n1; double lx = cI == 0 ? M : x2, ly = 22 + rI * 22 + 2, sx = lx + 14, cyy = ly - 4;
                Color sqF = T.squareFill == null ? new Color(0, 145, 235) : T.squareFill;
                switch (leg5.get(k)[0]) {
                    case "sq": g.setColor(sqF); g.fill(new Rectangle2D.Double(sx - 6, cyy - 6, 12, 12)); if (T.squareEdge != null) { g.setColor(T.squareEdge); g.setStroke(new BasicStroke(1f)); g.draw(new Rectangle2D.Double(sx - 6, cyy - 6, 12, 12)); } break;
                    case "dia": { Path2D d = new Path2D.Double(); d.moveTo(sx - 13, cyy); d.lineTo(sx, cyy - 6.5); d.lineTo(sx + 13, cyy); d.lineTo(sx, cyy + 6.5); d.closePath(); if (T.diamondFill != null) { g.setColor(T.diamondFill); g.fill(d); } if (T.diamondBorder != null) { g.setColor(T.diamondBorder); g.setStroke(new BasicStroke(1f)); g.draw(d); } break; }
                    case "ibeam": g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1.3f)); c.line(sx, cyy - 8, sx, cyy + 8); c.line(sx - 4, cyy - 8, sx + 4, cyy - 8); c.line(sx - 4, cyy + 8, sx + 4, cyy + 8); break;
                    case "vbar": g.setColor(Color.BLACK); g.setStroke(new BasicStroke(2f)); c.line(sx, cyy - 9, sx, cyy + 9); break;
                    case "pooled": g.setColor(T.pooledCol); g.setStroke(T.pooledStroke()); c.line(sx - 13, cyy, sx + 13, cyy); break;
                    case "null": g.setColor(T.nullCol); g.setStroke(T.nullStroke()); c.line(sx - 13, cyy, sx + 13, cyy); break;
                    default: g.setColor(new Color(205, 0, 0)); g.fill(new Rectangle2D.Double(sx - 13, cyy - 2.5, 26, 5)); }
                g.setStroke(new BasicStroke(1f)); g.setColor(Color.BLACK); c.text(leg5.get(k)[1], lx + 32, ly, 12f, 0, false); }
            c.mono = true; }
        else if (serT != null) {   // leave-one-out / cumulative: the heading of meta::forest instead of the effect measure (which heads its own column)
            if (K == 1 || K == 2) { int sp = serT.indexOf(' '); c.text(serT.substring(0, sp), pcx, hy - gh, FS, 1, true); c.text(serT.substring(sp + 1), pcx, hy, FS, 1, true); }
            else c.text(serT, pcx, hy, FS, 1, true); }
        else if (K == 0 || K == 4) c.text(measure, pcx, hy, FS, 1, true);   // Default and metafor style: the measure heads the plot column (metafor style also repeats it as the axis title)
        else if (K == 1) { c.text(measure, pcx, hy - gh, FS, 1, true); c.text(mt + ", " + mdl + ", " + lvl, pcx, hy, FS, 1, true); }
        else if (K == 2) { c.text(measure + ", " + mt + ",", pcx, hy - gh, FS, 1, true); c.text(mdl.toLowerCase(Locale.ROOT) + " (" + lvl + ")", pcx, hy, FS, 1, true); }
        if (T.headRule != null) { g.setColor(T.headRule); g.setStroke(new BasicStroke(K == 1 ? 1f : 1.2f)); c.line(M, ruleY, Wc - M, ruleY); }
        if (K == 3 && ruleNote >= 0 && fy.length > 0) { g.setColor(T.boxBorder); g.setStroke(new BasicStroke(1f)); c.line(0, ruleNote, Wc, ruleNote); }
        // ---- reference lines
        Pool P = r.overall; double pooled = s.random ? P.teRandom : P.teCommon;
        if (hasRef && ref >= lo - 1e-9 && ref <= hi + 1e-9) { g.setColor(T.nullCol); g.setStroke(T.nullStroke()); c.line(X.applyAsDouble(ref), yFirstTop, X.applyAsDouble(ref), ay); }   // starts at the rule under the header, so it never runs through the plot-column heading
        if (!Double.isNaN(pooled) && firstPool >= 0 && !loo) { double pp = lin ? lg(s, Meta.back(s, pooled, P.nHarm)) : pooled; if (pp >= lo - 1e-9 && pp <= hi + 1e-9) { double yd = K == 3 || K == 5 ? ay : cy[lastPool] - 6; g.setColor(T.pooledCol); g.setStroke(T.pooledStroke()); c.line(X.applyAsDouble(pp), yFirstTop, X.applyAsDouble(pp), yd);
            if (K == 5) { c.mono = false; g.setColor(Color.BLACK); c.text(Fmt.num(Meta.back(s, pooled, P.nHarm), Math.max(2, o.digits)), X.applyAsDouble(pp), hy - 14, 12f, 1, false); c.mono = true; } } }
        g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1f));
        // ---- rows
        for (int i = 0; i < tail; i++) {
            Row w = rows.get(i); double mid = cy[i], base = mid + 4; g.setColor(Color.BLACK);
            if (w.type == 2) { c.text(w.label, M, base, FS, 0, boldRow(T, w)); continue; }
            if (w.type == 4) continue;
            if (w.type == 5) { String tx = RICH ? w.text : w.text.replace(IT, ""); boolean m0 = c.mono; c.mono = false; if (RICH) rich(c, tx, M, base, 11f, false); else c.text(tx, M, base, 11f, 0, false); c.mono = m0; continue; }
            boolean bold = boldRow(T, w);
            if (K == 4 && w.type == 1 && (i == 0 || rows.get(i - 1).type != 1)) { double ry = cy[i] - rowH / 2.0 - (i > 0 && rows.get(i - 1).type == 0 ? rowH * 0.3 : 2); g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1f)); c.line(M, ry, Wc - M, ry); g.setColor(Color.BLACK); }   // metafor style: rule above each summary block
            c.text(w.label == null ? "" : w.label, M, base, FS, 0, bold);
            String[] lc = LC.apply(w);
            for (int j = 0; j < lc.length && j < nl; j++) { if (merge) c.text(lc[j], xr[j] - cw[j] / 2.0, base, FS, 1, bold); else c.text(lc[j], xr[j], base, FS, 2, bold); }
            if (w.type == 0) {
                if (!Double.isNaN(w.te)) {
                    double wl = lin ? P(s, true, w, w.lo) : w.lo, wu = lin ? P(s, true, w, w.up) : w.up;
                    double a = X.applyAsDouble(Math.max(wl, lo)), b = X.applyAsDouble(Math.min(wu, hi)), xm = X.applyAsDouble(ptPos(s, lin, w)); final double ptv = ptPos(s, lin, w); final boolean inR = ptv >= lo - 1e-9 && ptv <= hi + 1e-9;   // point estimate inside the axis limits?
                    double size = Math.max(K == 3 ? 2 : 3, T.markerMax * Math.sqrt(w.w / wmax)), bl = xm - size / 2, br = xm + size / 2;
                    final float LW = K == 3 ? 1.3f : K == 2 ? 1f : 1.1f, TW = 1.3f;
                    if (!(wu >= lo && wl <= hi)) { /* confidence interval entirely outside the axis limits: nothing is drawn */ }
                    else if (T.markerDiamond) {   // BMJ: diamonds in the colour of the CI line
                        g.setColor(T.ciCol); g.setStroke(new BasicStroke(LW)); c.line(a, mid, b, mid); if (wl < lo - 1e-12) arrowHead(g, a, mid, true, 5); if (wu > hi + 1e-12) arrowHead(g, b, mid, false, 5);
                        if (inR) { Path2D d = new Path2D.Double(); double h = size / 2; d.moveTo(xm - h, mid); d.lineTo(xm, mid - h); d.lineTo(xm + h, mid); d.lineTo(xm, mid + h); d.closePath(); g.setColor(T.squareFill); g.fill(d); }
                    } else if (K == 5) {   // IBM SPSS: black interval behind a blue square with a dark outline
                        g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1.5f)); c.line(a, mid, b, mid); if (wl < lo - 1e-12) arrowHead(g, a, mid, true, 5); if (wu > hi + 1e-12) arrowHead(g, b, mid, false, 5);
                        if (inR) { Rectangle2D.Double sq = new Rectangle2D.Double(bl, mid - size / 2, size, size); g.setColor(T.squareFill); g.fill(sq); if (T.squareEdge != null) { g.setColor(T.squareEdge); g.setStroke(new BasicStroke(1f)); g.draw(sq); } }
                    } else {
                        if (inR) { g.setColor(T.squareFill); g.fill(new Rectangle2D.Double(bl, mid - size / 2, size, size)); }
                        // as meta::forest: the CI line and the vertical tick stay visible on top of the square (solid black);
                        // if the whole CI fits inside the square, the horizontal line and the vertical tick are white (Default theme only).
                        boolean inside = K == 0 && a >= bl && b <= br;
                        g.setColor(inside ? Color.WHITE : Color.BLACK); g.setStroke(new BasicStroke(LW)); c.line(a, mid, b, mid);
                        g.setColor(Color.BLACK); if (wl < lo - 1e-12) arrowHead(g, a, mid, true, 5); if (wu > hi + 1e-12) arrowHead(g, b, mid, false, 5); g.setColor(inside ? Color.WHITE : Color.BLACK);
                        if (K == 4) { g.setColor(Color.BLACK); g.setStroke(new BasicStroke(LW)); if (wl >= lo - 1e-12) c.line(a, mid - 2.5, a, mid + 2.5); if (wu <= hi + 1e-12) c.line(b, mid - 2.5, b, mid + 2.5); }   // metafor style: end caps of the interval
                        if (!inR || K == 4) { } else if (K == 3) { g.setColor(Color.BLACK); g.fill(new Ellipse2D.Double(xm - 2, mid - 2, 4, 4)); }
                        else { g.setStroke(new BasicStroke(TW)); c.line(xm, mid - 2.5, xm, mid + 2.5); }
                    }
                }
            } else if (w.type == 1 || w.type == 3) {
                double rl = lin ? P(s, true, w, w.lo) : w.lo, ru = lin ? P(s, true, w, w.up) : w.up, rt = lin ? P(s, true, w, w.te) : w.te;
                if (!(ru >= lo && rl <= hi)) { /* entirely outside the axis limits: nothing is drawn */ }
                else {
                    // clipped to the axis (geometrically: the part of the diamond / bar inside the limits), with an arrow at each truncated end, as meta::forest
                    final double span = hi - lo, cl = lo - 5 * span, ch = hi + 5 * span; final boolean tL = rl < lo - 1e-12, tR = ru > hi + 1e-12;
                    double a = X.applyAsDouble(Math.max(cl, Math.min(ch, rl))), b = X.applyAsDouble(Math.max(cl, Math.min(ch, ru))), xm = X.applyAsDouble(Math.max(cl, Math.min(ch, rt)));
                    final double xL = X.applyAsDouble(lo), xR = X.applyAsDouble(hi); java.awt.Shape oldClip = g.getClip(); g.clip(new Rectangle2D.Double(xL, 0, xR - xL, 1e6));
                    if (w.type == 1 && K == 5) {   // IBM SPSS: small diamond at the estimate, the confidence interval as an I-beam through it
                        g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1.3f)); c.line(a, mid, b, mid); if (!tL) c.line(a, mid - 5, a, mid + 5); if (!tR) c.line(b, mid - 5, b, mid + 5);
                        Path2D d = new Path2D.Double(); d.moveTo(xm - 10, mid); d.lineTo(xm, mid - 6.5); d.lineTo(xm + 10, mid); d.lineTo(xm, mid + 6.5); d.closePath();
                        if (T.diamondFill != null) { g.setColor(T.diamondFill); g.fill(d); } if (T.diamondBorder != null) { g.setColor(T.diamondBorder); g.setStroke(new BasicStroke(0.9f)); g.draw(d); }
                    } else if (w.type == 1) {
                        double hh = K == 3 ? 7 : 5; Path2D d = new Path2D.Double(); d.moveTo(a, mid); d.lineTo(xm, mid - hh); d.lineTo(b, mid); d.lineTo(xm, mid + hh); d.closePath();
                        if (T.diamondFill != null) { g.setColor(T.diamondFill); g.fill(d); }
                        if (T.diamondBorder != null) { g.setColor(T.diamondBorder); g.setStroke(new BasicStroke(K == 3 ? 1.1f : 0.9f)); g.draw(d); }
                    } else if (K == 4) { g.setColor(new Color(255, 0, 0)); g.fill(new Rectangle2D.Double(a, mid - 3.5, b - a, 7));   // metafor style: thick red bar
                    } else { g.setColor(new Color(205, 0, 0)); g.fill(new Rectangle2D.Double(a, mid - 2.5, b - a, 5)); g.setColor(new Color(120, 0, 0)); g.setStroke(new BasicStroke(0.7f)); g.draw(new Rectangle2D.Double(a, mid - 2.5, b - a, 5)); }
                    g.setClip(oldClip);
                    g.setColor(w.type == 3 ? new Color(120, 0, 0) : T.diamondBorder != null ? T.diamondBorder : Color.BLACK); if (tL) arrowHead(g, xL, mid, true, 5); if (tR) arrowHead(g, xR, mid, false, 5);
                }
            }
            g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1f));
            for (Col col : pre) c.text(col.val.apply(w), col.ax(col.align), base, FS, col.align, bold);
            for (Col col : post) c.text(col.val.apply(w), col.ax(col.align), base, FS, col.align, bold && K != 4);   // metafor style: only the label and the data columns of a summary row are bold
        }
        // ---- axis
        c.mono = false; g.setColor(T.axisCol); g.setStroke(new BasicStroke(K == 5 ? 1.6f : 1.1f));
        if (K == 5) c.line(px0, ay, px1, ay);   // IBM SPSS: grey line over the whole width of the plot, tick labels without tick marks
        else if (tmin <= tmax) c.line(X.applyAsDouble(tmin), ay, X.applyAsDouble(tmax), ay);
        for (double t : ticks) { double v = log ? Math.log(t) : t; if (v < lo - 1e-9 || v > hi + 1e-9) continue; double xx = X.applyAsDouble(v); g.setColor(T.axisCol); if (K != 5) c.line(xx, ay, xx, ay + 5); g.setColor(Color.BLACK); c.text(TK.apply(t), xx, ay + 20, T.tickSize, 1, false); }
        g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1f));
        if (K == 4 && tmin <= tmax) c.text(measure, (X.applyAsDouble(tmin) + X.applyAsDouble(tmax)) / 2.0, ay + (xlab2 ? 58 : 42), FS, 1, false);   // metafor style: axis title
        if (!o.xLeft.isEmpty()) c.text(o.xLeft, X.applyAsDouble(xlC), ay + 38, 11f, 1, false); if (!o.xRight.isEmpty()) c.text(o.xRight, X.applyAsDouble(xrC), ay + 38, 11f, 1, false);
        // ---- footer text
        if (K == 3) for (int i = 0; i < nNote; i++) { double tw = f11.stringWidth(fTxt.get(i)) + 4; if (M + tw > px0 - 4) { g.setColor(Color.WHITE); g.fill(new Rectangle2D.Double(M - 2, fy[i] - 11, tw + 2, 15)); g.setColor(Color.BLACK); } }   // keep long note lines readable where they cross the reference lines
        for (int i = 0; i < fy.length; i++) { if (fRich.get(i)) rich(c, fTxt.get(i), M, fy[i], 11f, false); else c.text(fTxt.get(i), M, fy[i], 11f, 0, fBold.get(i)); }
        return c.img;
    }

    /** Parses an x-axis setting typed by the user (blank, or not a finite number: NaN = automatic). Accepts a decimal comma. */
    static double parseAxis(String s) { if (s == null || s.isBlank()) return Double.NaN; try { double v = Double.parseDouble(s.trim().replace(',', '.')); return Double.isFinite(v) ? v : Double.NaN; } catch (NumberFormatException e) { return Double.NaN; } }
    /** Ticks every {@code step} units from the first multiple of step inside [lo, hi]; null (= automatic ticks) when step is not set, not positive, or would give more than 60 ticks. */
    static double[] userTicks(double lo, double hi, double step) {
        if (!(step > 0) || !((hi - lo) / step <= 60)) return null;
        double first = Math.ceil(lo / step - 1e-9) * step; List<Double> t = new ArrayList<>();
        for (int i = 0; first + i * step <= hi + step * 1e-9 && i <= 61; i++) t.add(Math.round((first + i * step) / step * 1e6) / 1e6 * step);
        return t.isEmpty() ? null : t.stream().mapToDouble(Double::doubleValue).toArray();
    }

    /** Limits (displayed scale) of the x-axis of the last forest plot drawn; for checks in the self tests. */
    /** Axis limits of the last forest plot drawn by the calling thread (back-transformed; for tests). Thread-local: forest plots are rendered in parallel. */
    private static final ThreadLocal<double[]> LAST_AXIS = new ThreadLocal<>();
    public static double[] lastForestAxis() { return LAST_AXIS.get(); }

    /** Nearest value of the 1-2-5 sequence below (up = false) or above (up = true) v, v itself when it belongs to the sequence. */
    public static double round125(double v, boolean up) {
        if (!(v > 0) || Double.isInfinite(v)) return v;
        int k = (int) Math.floor(Math.log10(v)) - 1; double best = Double.NaN;
        for (int i = k; i <= k + 3; i++) for (double m : new double[]{1, 2, 5}) { double c = m * Math.pow(10, i);
            if (up) { if (c >= v * (1 - 1e-9) && (Double.isNaN(best) || c < best)) best = c; } else { if (c <= v * (1 + 1e-9) && (Double.isNaN(best) || c > best)) best = c; } }
        return Double.isNaN(best) ? v : best;
    }

    /** Small filled arrow head whose tip is at xTip, pointing left or right: marks a confidence interval truncated by the axis limits. */
    static void arrowHead(Graphics2D g, double xTip, double y, boolean left, double sz) {
        Path2D p = new Path2D.Double(); double d = left ? sz : -sz; p.moveTo(xTip, y); p.lineTo(xTip + d, y - sz * 0.55); p.lineTo(xTip + d, y + sz * 0.55); p.closePath(); g.fill(p); }

    static String plural(String m) { return m.endsWith("s") ? m : m.matches(".*[^aeiou]y") ? m.substring(0, m.length() - 1) + "ies" : m + "s"; }

    /** Measures whose forest plot axis is drawn on the back-transformed scale (as in meta::forest). */
    static boolean linBack(String sm) { switch (sm) { case "PRAW": case "PLOGIT": case "PAS": case "PFT": case "ZCOR": case "IR": case "IRS": case "IRFT": return true; default: return false; } }
    /** Single-group measures (proportions, rates, means): no null effect unless a target is set, and the axis is never centred on it. */
    static boolean singleGroup(String sm) { switch (sm) { case "PRAW": case "PLOGIT": case "PLN": case "PAS": case "PFT": case "IR": case "IRLN": case "IRS": case "IRFT": case "MRAW": case "MLN": return true; default: return false; } }
    /** Axis coordinate of a displayed-scale value: logarithmic for the log-transformed single-group measures (PLN, IRLN, MLN), identity otherwise. */
    static double lg(MetaSpec s, double v) { return s.backtransf && s.isRatioSm() ? Math.log(v) : v; }
    /** Natural limits of the back-transformed axis. */
    static double[] linBounds(MetaSpec s) {
        switch (s.sm) { case "ZCOR": return new double[]{-1, 1}; case "IR": case "IRS": case "IRFT": return new double[]{0, Double.MAX_VALUE};
            case "PLN": return new double[]{Double.NEGATIVE_INFINITY, Math.log(s.pscale)}; case "IRLN": case "MLN": return new double[]{Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}; default: return new double[]{0, s.pscale}; }
    }
    /** Position of a value on the back-transformed axis. */
    static double P(MetaSpec s, boolean lin, Row w, double v) { return lin ? lg(s, Meta.back(s, v, w.n)) : v; }
    /** Position of the point estimate of a row: event / n for single proportions and rates, else back(TE). */
    static double ptPos(MetaSpec s, boolean lin, Row w) { return !lin ? w.te : lg(s, !Double.isNaN(w.ptBack) ? w.ptBack : Meta.back(s, w.te, w.n)); }

    /** Denser ticks for a log axis (funnel plots): 0.5, 0.6, 0.7, 0.8, 1, 1.2, 1.5, 2 ... thinned to at most 8 when the range is wide; empty when fewer than 3 fit (the caller then uses {@link #logTicks}). */
    static double[] logTicksDense(double lo, double hi) {
        double[][] lists = {{0.05, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1, 1.1, 1.2, 1.3, 1.5, 1.75, 2, 2.5, 3, 4, 5, 7, 10, 15, 20, 30, 50, 100}, {0.05, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 1, 1.2, 1.5, 2, 3, 4, 5, 7, 10, 15, 20, 30, 50, 100}, {0.1, 0.2, 0.3, 0.5, 0.7, 1, 1.5, 2, 3, 5, 7, 10, 20, 50}};
        for (double[] cand : lists) { List<Double> u = new ArrayList<>(); for (double c : cand) if (c >= lo * 0.999 && c <= hi * 1.001) u.add(c);
            if (u.size() >= 3 && u.size() <= 8) return u.stream().mapToDouble(Double::doubleValue).toArray(); }
        return new double[0];
    }
    static double[] logTicks(double lo, double hi) {
        double[] cand = {0.01, 0.02, 0.05, 0.1, 0.2, 0.25, 0.5, 1, 2, 4, 5, 10, 20, 50, 100};
        List<Double> t = new ArrayList<>(); for (double c : cand) if (c >= lo * 0.999 && c <= hi * 1.001) t.add(c);
        if (t.size() > 5) { List<Double> u = new ArrayList<>(); for (double c : new double[]{0.1, 0.2, 0.5, 1, 2, 5, 10}) if (c >= lo && c <= hi) u.add(c); t = u; }
        if (t.size() < 2) { t.clear(); if (hi > lo * 1.0001) for (double c : Canvas.pretty(lo, hi, 5)) if (c > 0 && c >= lo * 0.999 && c <= hi * 1.001) t.add(c); if (t.size() < 2) { t.clear(); t.add(1.0); } }
        return t.stream().mapToDouble(Double::doubleValue).toArray();
    }

    // ----- leave-one-out / cumulative forest
    public static BufferedImage forestSeries(MetaResult r, List<Pool> pools, List<String> labels, Opts o, String kind) {
        MetaSpec s = r.spec; boolean log = s.backtransf && s.isRatioSm(); int dg = o.digits; List<Row> rows = new ArrayList<>();
        for (int i = 0; i < pools.size(); i++) {
            Pool p = pools.get(i); Row w = new Row(); w.type = 0; w.label = labels.get(i);
            w.te = s.random ? p.teRandom : p.teCommon; w.lo = s.random ? p.loRandom : p.loCommon; w.up = s.random ? p.upRandom : p.upCommon; w.w = 0.3;
            w.n = p.nHarm; w.rcol1 = Fmt.num(Meta.back(s, w.te, w.n), dg); w.rcol2 = Fmt.ci(Meta.back(s, w.lo, w.n), Meta.back(s, w.up, w.n), dg);
            w.sEst = w.rcol1; w.sLo = Fmt.num(Meta.back(s, w.lo, w.n), dg); w.sUp = Fmt.num(Meta.back(s, w.up, w.n), dg);
            w.lcols = s.hasNull() ? new String[]{ Report.pooledP(p, s.random, true), Fmt.num(p.tau2, 4), Fmt.num(p.tau, 4), Fmt.num(100 * p.I2, 1) + "%" } : new String[]{ Fmt.num(p.tau2, 4), Fmt.num(p.tau, 4), Fmt.num(100 * p.I2, 1) + "%" }; rows.add(w);
            if (s.prediction && s.random && !Double.isNaN(p.loPred)) rows.add(piRow(s, p, dg)); }
        Pool p = r.overall; Row w = themed(ForestTheme.of(o).kind, poolRow(r, s, p, s.random ? s.textRandom : s.textCommon, !s.random, dg, 100, null), p, s, o, false, false, false); w.lcols = s.hasNull() ? new String[]{ Report.pooledP(p, s.random, true), Fmt.num(p.tau2, 4), Fmt.num(p.tau, 4), Fmt.num(100 * p.I2, 1) + "%" } : new String[]{ Fmt.num(p.tau2, 4), Fmt.num(p.tau, 4), Fmt.num(100 * p.I2, 1) + "%" }; rows.add(w);   // pooled result of all studies: its P-value, tau^2, tau and I^2 as in meta::forest
        if (s.prediction && s.random) rows.add(piRow(s, p, dg));
        if (!Report.degenerateNote(s, labels, pools, r.overall).isEmpty()) { Row t = new Row(); t.type = 5; t.text = s.hasNull() ? "Degenerate (SE = 0: Hartung-Knapp with q = 0): zero-width interval, p-value not interpretable" : "Degenerate (SE = 0: Hartung-Knapp with q = 0): zero-width interval"; rows.add(t); }
        MetaSpec s2 = s; BufferedImage im = drawForest(rows, r, o, log, Report.smLabel(s), false, true, "cum".equals(kind) ? "Cumulative Meta-Analysis" : "Leave-One-Out Meta-Analysis"); return im;
    }

    // ---------------- funnel & friends ----------------
    static class Axes { Canvas c; double x0, x1, y0, y1, xl, xh, yl, yh; boolean yRev; java.util.function.DoubleUnaryOperator bk;
        double X(double v) { return x0 + (v - xl) / (xh - xl) * (x1 - x0); }
        double Y(double v) { return yRev ? y0 + (v - yl) / (yh - yl) * (y1 - y0) : y1 - (v - yl) / (yh - yl) * (y1 - y0); } }

    static Axes axes(Canvas c, double xl, double xh, double yl, double yh, boolean yRev, String xlab, String ylab, boolean logX, String title) { return axes(c, xl, xh, yl, yh, yRev, xlab, ylab, logX, title, 60); }
    /** bottom: space (px) between the panel and the lower edge of the canvas. */
    static Axes axes(Canvas c, double xl, double xh, double yl, double yh, boolean yRev, String xlab, String ylab, boolean logX, String title, double bottom) { return axes(c, xl, xh, yl, yh, yRev, xlab, ylab, logX, title, bottom, false); }
    /** dense: denser tick labels on a log x-axis (funnel plots). */
    static Axes axes(Canvas c, double xl, double xh, double yl, double yh, boolean yRev, String xlab, String ylab, boolean logX, String title, double bottom, boolean dense) { return axes(c, xl, xh, yl, yh, yRev, xlab, ylab, logX, title, bottom, dense, false); }
    /** logY: yl and yh are natural logarithms and the y-axis is labelled with their exponentials (ratio measures on a log scale). */
    static Axes axes(Canvas c, double xl, double xh, double yl, double yh, boolean yRev, String xlab, String ylab, boolean logX, String title, double bottom, boolean dense, boolean logY) { return axes(c, xl, xh, yl, yh, yRev, xlab, ylab, logX, title, bottom, dense, logY, null); }
    /** bk: monotone back-transformation of the (transformed) x values (logit, arcsine, Freeman-Tukey, Fisher z ...): ticks are placed at round values of the back-transformed scale. */
    static Axes axes(Canvas c, double xl, double xh, double yl, double yh, boolean yRev, String xlab, String ylab, boolean logX, String title, double bottom, boolean dense, boolean logY, java.util.function.DoubleUnaryOperator bk) { return axes(c, xl, xh, yl, yh, yRev, xlab, ylab, logX, title, bottom, dense, logY, bk, false); }
    /** cover: x-axis widened so that its end ticks lie at or beyond the study values (Doi, PET-PEESE and funnel plots), see {@link #coverAxis}. */
    static Axes axes(Canvas c, double xl, double xh, double yl, double yh, boolean yRev, String xlab, String ylab, boolean logX, String title, double bottom, boolean dense, boolean logY, java.util.function.DoubleUnaryOperator bk, boolean cover) { return axes(c, xl, xh, yl, yh, yRev, xlab, ylab, logX, title, bottom, dense, logY, bk, cover, Double.NaN, Double.NaN); }
    /** dl, dh: smallest and largest plotted study value (NaN = the axis ends): a saturated end tick (0 or 1) is never placed to the right of the smallest / left of the largest study. */
    static Axes axes(Canvas c, double xl, double xh, double yl, double yh, boolean yRev, String xlab, String ylab, boolean logX, String title, double bottom, boolean dense, boolean logY, java.util.function.DoubleUnaryOperator bk, boolean cover, double dl, double dh) {
        Axes a = new Axes(); a.bk = bk; XT cv = null; if (cover) { cv = coverAxis(xl, xh, dl, dh, logX, bk, c.w / c.scaleF() - 100); if (cv.t.size() >= 2) { xl = cv.xl; xh = cv.xh; } else cv = null; } a.c = c; a.x0 = 70; a.x1 = c.w / c.scaleF() - 30; a.y0 = 40; a.y1 = c.h / c.scaleF() - bottom; a.xl = xl; a.xh = xh; a.yl = yl; a.yh = yh; a.yRev = yRev;
        Graphics2D g = c.g; g.setColor(Color.BLACK); g.draw(new Rectangle2D.Double(a.x0, a.y0, a.x1 - a.x0, a.y1 - a.y0));
        if (cv != null) { for (double[] q : cv.t) { double xx = a.X(q[0]); c.line(xx, a.y1, xx, a.y1 + 5); c.text(Canvas.tick(q[1]), xx, a.y1 + 18, 10, 1, false); } }
        double[] xt = cv != null ? new double[0] : logX ? logTicks(Math.exp(xl), Math.exp(xh)) : Canvas.pretty(xl, xh, 6), yt = logY ? logTicks(Math.exp(yl), Math.exp(yh)) : Canvas.pretty(yl, yh, 6);
        if (cv == null && logX && dense) { double[] dt = logTicksDense(Math.exp(xl), Math.exp(xh)); if (dt.length > 0) xt = dt; }
        if (cv == null && logX) { int inside = 0; for (double t : xt) { double v = Math.log(t); if (v >= xl && v <= xh) inside++; } if (inside < 2) xt = Canvas.pretty(Math.exp(xl), Math.exp(xh), 5); }   // narrow range: the 1-2-5 log ticks leave fewer than two labels
        if (cv == null && logX) { int inside = 0; for (double t : xt) { double v = Math.log(t); if (v >= xl && v <= xh) inside++; } if (inside < 4) { double[] pt = Canvas.pretty(Math.exp(xl), Math.exp(xh), 6); List<Double> ok = new ArrayList<>(); for (double q : pt) if (q > 0 && Math.log(q) >= xl - 1e-9 && Math.log(q) <= xh + 1e-9) ok.add(q); if (ok.size() >= 3) xt = ok.stream().mapToDouble(Double::doubleValue).toArray(); } }   // few round log ticks: evenly spaced round values of the ratio scale instead
        if (cv == null && bk != null && !logX) {   // ticks at round values of the back-transformed scale, positioned by inverting bk
            List<double[]> bt = bkTicks(xl, xh, bk, a.x1 - a.x0);
            if (bt.size() >= 2) { for (double[] q : bt) { double xx = a.X(q[0]); c.line(xx, a.y1, xx, a.y1 + 5); c.text(Canvas.tick(q[1]), xx, a.y1 + 18, 10, 1, false); } xt = new double[0]; }
        }
        for (double t : xt) { double v = logX ? Math.log(t) : t; if (v < xl || v > xh) continue; double xx = a.X(v); c.line(xx, a.y1, xx, a.y1 + 5); c.text(Canvas.tick(t), xx, a.y1 + 18, 10, 1, false); }
        for (double t : yt) { double v = logY ? Math.log(t) : t; if (v < yl - 1e-9 || v > yh + 1e-9) continue; double yy = a.Y(v); c.line(a.x0 - 5, yy, a.x0, yy); c.textRot(Canvas.tick(t), a.x0 - 10, yy, 10); }
        c.text(xlab, (a.x0 + a.x1) / 2, a.y1 + 40, 11, 1, false); c.textRot(ylab, 16, (a.y0 + a.y1) / 2, 11);
        if (title != null && !title.isEmpty()) c.text(title, (a.x0 + a.x1) / 2, 24, 13, 1, true);
        return a; }

    /** Back-transformation of the x-axis for measures whose back-transformed scale is not a plain exponential (proportions, rates, Fisher's z): null when results are not back-transformed or the measure needs none. */
    static java.util.function.DoubleUnaryOperator backAxis(MetaSpec s, MetaResult r) {
        if (!s.backtransf || !java.util.Arrays.asList("PLOGIT", "PLN", "PAS", "PFT", "PRAW", "ZCOR", "IRLN", "IRS", "IRFT", "IR").contains(s.sm)) return null;
        final double n = r.overall != null ? r.overall.nHarm : Double.NaN; return x -> Meta.back(s, x, n);
    }

    /** Result of {@link #coverAxis}: axis range widened so that the first and last tick lie at or beyond the study values, and the ticks {position on the plotted scale, label number}. */
    static final class XT { double xl, xh; List<double[]> t = new ArrayList<>(); }
    private static double floorSig(double x, int d) { if (x == 0) return 0; double sc = Math.pow(10, Math.floor(Math.log10(Math.abs(x))) - d + 1); return Math.floor(x / sc + 1e-9) * sc; }
    private static double ceilSig(double x, int d) { if (x == 0) return 0; double sc = Math.pow(10, Math.floor(Math.log10(Math.abs(x))) - d + 1); return Math.ceil(x / sc - 1e-9) * sc; }
    private static double invert(java.util.function.DoubleUnaryOperator f, double v, double lo, double hi) {
        double fl = f.applyAsDouble(lo), fh = f.applyAsDouble(hi); if (Double.isNaN(fl) || Double.isNaN(fh) || v <= fl || v >= fh) return Double.NaN;
        for (int it = 0; it < 80; it++) { double m = (lo + hi) / 2; if (f.applyAsDouble(m) < v) lo = m; else hi = m; } return (lo + hi) / 2;
    }
    /** x-axis ticks of the funnel-type plots (Doi, PET-PEESE, funnels) that cover the whole range of the studies: the first tick is at or below the smallest and the last at or above the largest
     *  value (the axis is widened to the end ticks, which are round numbers of the displayed scale, e.g. 0.6 and 4 on an odds-ratio axis, 0.02 and 0.98 on a proportion axis). Between them round
     *  values are added by decreasing roundness (1, 5, 2, 2.5, 3 ...) while labels stay at least 40 px apart. bk: back-transformation of the plotted values (null = displayed as plotted);
     *  logX: the plotted values are logarithms of the displayed ratio. Without a usable bk the ticks are the uniform round values of {@link Canvas#pretty}. */
    static XT coverAxis(double xl, double xh, boolean logX, java.util.function.DoubleUnaryOperator bk, double widthPx) { return coverAxis(xl, xh, Double.NaN, Double.NaN, logX, bk, widthPx); }
    /** dl, dh: smallest / largest plotted study value (NaN = axis ends, the tick then sits at the axis end). When the back-transformation saturates (0 or 1) at an axis end, that end tick (exactly 0 or 1) is placed at the edge of the saturated
     *  part or at the extreme study, whichever lies further out, so it never falls inside the studies. */
    static XT coverAxis(double xl, double xh, double dl, double dh, boolean logX, java.util.function.DoubleUnaryOperator bk, double widthPx) {
        XT r = new XT(); r.xl = xl; r.xh = xh; if (!(xh > xl)) return r;
        if (logX) bk = Math::exp;
        if (bk == null) { double[] p = Canvas.pretty(xl, xh, 6); if (p.length >= 2) { r.xl = Math.min(xl, p[0]); r.xh = Math.max(xh, p[p.length - 1]); for (double q : p) r.t.add(new double[]{q, q}); } return r; }
        final java.util.function.DoubleUnaryOperator f = bk; double w = xh - xl, elo = xl - 2 * w, ehi = xh + 2 * w;
        double b1 = f.applyAsDouble(xl), b2 = f.applyAsDouble(xh); if (Double.isNaN(b1) || Double.isNaN(b2) || !(b2 > b1) || !(f.applyAsDouble(ehi) > f.applyAsDouble(elo))) return r;
        double vlo = Double.NaN, vhi = Double.NaN, plo = xl, phi = xh; boolean satLo = false, satHi = false;
        // saturating back-transformations (PRAW, PAS, PFT, PLN, IR ... clamp at 0 and/or 1): when the axis end lies in the flat part, floor/ceil of the end value (0 or 1) cannot be inverted,
        // so the end tick is placed at the edge of the flat part instead, i.e. at exactly 0 (or 1), the first (last) position where the back-transformed value is 0 (1)
        { double fe0 = f.applyAsDouble(elo), fe1 = f.applyAsDouble(ehi), tol = 1e-12 * Math.max(1, Math.abs(b2 - b1));
          if (!Double.isNaN(fe0) && b1 - fe0 <= tol) { double u = xl, v = xh; for (int it = 0; it < 100; it++) { double m = (u + v) / 2; if (f.applyAsDouble(m) <= b1 + tol) u = m; else v = m; } vlo = b1; plo = Math.max(xl, Math.min(u, Double.isNaN(dl) ? xl : dl)); satLo = true; }
          if (!Double.isNaN(fe1) && fe1 - b2 <= tol) { double u = xl, v = xh; for (int it = 0; it < 100; it++) { double m = (u + v) / 2; if (f.applyAsDouble(m) >= b2 - tol) v = m; else u = m; } vhi = b2; phi = Math.min(xh, Math.max(v, Double.isNaN(dh) ? xh : dh)); satHi = true; } }
        for (int d = 1; d <= 3 && Double.isNaN(vlo); d++) { double v = floorSig(b1, d), pos = invert(f, v, elo, ehi); if (!Double.isNaN(pos) && (d == 3 ? xl - pos <= 0.3 * w : xl - pos <= 0.10 * w)) { vlo = v; plo = Math.min(pos, xl); } }
        for (int d = 1; d <= 3 && Double.isNaN(vhi); d++) { double v = ceilSig(b2, d), pos = invert(f, v, elo, ehi); if (!Double.isNaN(pos) && (d == 3 ? pos - xh <= 0.3 * w : pos - xh <= 0.10 * w)) { vhi = v; phi = Math.max(pos, xh); } }
        r.xl = satLo ? xl : Math.min(xl, plo); r.xh = satHi ? xh : Math.max(xh, phi); double lo = Double.isNaN(vlo) ? b1 : Math.min(vlo, b1), hi = Double.isNaN(vhi) ? b2 : Math.max(vhi, b2), span = hi - lo, wid = r.xh - r.xl;
        List<double[]> cand = new ArrayList<>(); double[] ms = {1, 5, 2, 2.5, 3, 4, 6, 7, 8, 9, 1.5}; int[] rk = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        int kmax = (int) Math.ceil(Math.log10(Math.max(Math.abs(lo), Math.abs(hi)) + 1e-300)) + 1, kmin = (int) Math.floor(Math.log10(span)) - 1;
        if (lo <= 0 && hi >= 0) cand.add(new double[]{0, 0});
        for (int k = kmin; k <= kmax; k++) for (int j = 0; j < ms.length; j++) for (int sg = -1; sg <= 1; sg += 2) { double v = sg * ms[j] * Math.pow(10, k); if (v >= lo - 1e-9 * span && v <= hi + 1e-9 * span) cand.add(new double[]{v, rk[j]}); }
        cand.sort((u, v) -> Double.compare(u[1], v[1]));
        List<double[]> chosen = new ArrayList<>(); List<Double> px = new ArrayList<>();
        for (double fv : new double[]{vlo, vhi}) if (!Double.isNaN(fv)) { double pos = satLo && fv == vlo ? plo : satHi && fv == vhi ? phi : fv == b1 ? xl : fv == b2 ? xh : invert(f, fv, elo, ehi); if (!Double.isNaN(pos)) { chosen.add(new double[]{pos, fv}); px.add((pos - r.xl) / wid * widthPx); } }
        for (double[] cd : cand) { double pos = invert(f, cd[0], r.xl - 1e-9 * wid - 1e-12, r.xh + 1e-9 * wid + 1e-12); if (Double.isNaN(pos)) continue; double x = (pos - r.xl) / wid * widthPx; boolean ok = true; for (double o : px) if (Math.abs(o - x) < 40) { ok = false; break; }
            if (ok) { chosen.add(new double[]{pos, cd[0]}); px.add(x); } }
        chosen.sort((u, v) -> Double.compare(u[0], v[0])); r.t = chosen; return r;
    }

    /** Ticks {position on the transformed scale, label on the back-transformed scale} at round back-transformed values: finer until the first and last tick lie close to the axis ends
     *  (the study limits are covered), labels closer than 34 px dropped. Empty when bk is not usable on [xl, xh]. */
    static List<double[]> bkTicks(double xl, double xh, java.util.function.DoubleUnaryOperator bk, double widthPx) {
        List<double[]> out = new ArrayList<>(); double b1 = bk.applyAsDouble(xl), b2 = bk.applyAsDouble(xh), lo = Math.min(b1, b2), hi = Math.max(b1, b2);
        if (Double.isNaN(lo) || Double.isNaN(hi) || hi - lo <= 1e-12) return out;
        for (int nt : new int[]{6, 9, 13, 18}) { out.clear(); double last = -1e9;
            for (double q : Canvas.pretty(lo, hi, nt)) { if (q < lo - 1e-9 * (hi - lo) || q > hi + 1e-9 * (hi - lo)) continue;
                double u = xl, w = xh; for (int it = 0; it < 80; it++) { double m = (u + w) / 2; if ((bk.applyAsDouble(m) < q) == (b1 < b2)) u = m; else w = m; }
                double pos = (u + w) / 2, px = (pos - xl) / (xh - xl) * widthPx; if (px - last < 34) continue; last = px; out.add(new double[]{pos, q}); }
            if (out.size() >= 2 && (out.get(0)[0] - xl) < 0.1 * (xh - xl) && (xh - out.get(out.size() - 1)[0]) < 0.1 * (xh - xl)) break; }
        return out;
    }

    /** x-axis of the funnel plots. log: ratio measures on a log axis labelled with the ratio (existing behaviour when results are back-transformed, or when the funnel option is on);
     *  bk: back-transformation of the other transformed measures (logit, log, arcsine, Freeman-Tukey, Fisher z, rate transformations), only when "Back-transform effect-size for funnel plot" is on. */
    static final class FunnelAxis { boolean log; java.util.function.DoubleUnaryOperator bk; String xlab; }
    static FunnelAxis funnelAxis(MetaSpec s, MetaResult r, Opts o) {
        FunnelAxis f = new FunnelAxis(); boolean ratioExp = java.util.Arrays.asList("OR", "RR", "HR", "IRR", "DOR", "ROM", "MLN").contains(s.sm);
        f.log = ratioExp && (o.funnelBack || s.backtransf);
        if (!f.log && o.funnelBack && java.util.Arrays.asList("PLOGIT", "PLN", "PAS", "PFT", "ZCOR", "IRLN", "IRS", "IRFT").contains(s.sm)) {
            final MetaSpec sb = s.clone(); sb.backtransf = true; final double n = r.overall != null ? r.overall.nHarm : Double.NaN; f.bk = x -> Meta.back(sb, x, n); }
        if (f.log || f.bk != null) f.xlab = xlabBack(s); else { MetaSpec off = s.clone(); off.backtransf = false; f.xlab = xlabFor(off); }   // transformed scale drawn: name it (Fisher\u2019s z, Log Odds Ratio, ...) whatever the main option says
        return f;
    }

    /** Study label next to a point, kept inside the plot panel: drawn to the right of the point (or to the left when preferLeft) and flipped to the other side when it would cross the panel edge; never above the top edge. */
    /** Study label next to a point, or (placer != null) handed to the label-placement algorithm that draws all the labels at the end of the plot. */
    static void lab(LabelPlacer lp, Canvas c, Axes a, String text, double xx, double yy, double off, double dy, boolean preferLeft, double rad) { if (lp != null) lp.add(text, xx, yy, rad); else ptLabel(c, a, text, xx, yy, off, dy, preferLeft); }
    static void ptLabel(Canvas c, Axes a, String text, double xx, double yy, double off, double dy, boolean preferLeft) { ptLabel(c, a.x0, a.x1, a.y0, text, xx, yy, off, dy, preferLeft); }
    static void ptLabel(Canvas c, double x0, double x1, double y0, String text, double xx, double yy, double off, double dy, boolean preferLeft) {
        if (text == null) return; double w = c.g.getFontMetrics(Canvas.font(8f)).stringWidth(text); boolean left = preferLeft;
        if (!left && xx + off + w > x1 - 2) left = true; else if (left && xx - off - w < x0 + 2 && xx + off + w <= x1 - 2) left = false;
        double y = Math.max(yy + dy, y0 + 10); c.text(text, left ? xx - off : xx + off, y, 8, left ? 2 : 0, false);
    }

    /** Funnel-type plots: single-proportion measures are labelled with the transformation used; other measures as in xlabFor. */
    static String funnelXlab(MetaSpec s) { switch (s.sm) { case "PLOGIT": return "Logit Transformed Proportion"; case "PLN": return "Log Transformed Proportion"; case "PAS": return "Arcsine Transformed Proportion"; case "PFT": return "Freeman-Tukey Double Arcsine Transformed Proportion"; case "PRAW": return "Proportion"; default: return xlabFor(s); } }
    /** Untransformed proportions (PRAW) live in [0, 1]: the x-axis of a funnel plot is truncated there (the funnel is cut at 0 and 1). */
    static double[] clampRaw(MetaSpec s, double xl, double xh) { return "PRAW".equals(s.sm) ? new double[]{Math.max(xl, 0), Math.min(xh, 1)} : new double[]{xl, xh}; }

    /** Measures whose working scale is the log of the plotted ratio (OR, RR, HR, IRR, DOR, ROM). */
    static boolean logRatio(MetaSpec s) { return !s.backtransf && java.util.Arrays.asList("HR", "OR", "RR", "IRR", "DOR", "ROM").contains(s.sm); }
    /** Axis label of the effect measure: the back-transformed name (Odds Ratio) when results are back-transformed, the name of the scale actually plotted (Log Odds Ratio,
     *  Fisher's z Transformed Correlation, Logit Transformed Proportion ...) when "Back-transform results" is unchecked (as meta::xlab). */
    static String xlabFor(MetaSpec s) {
        if (!s.backtransf) switch (s.sm) {
            case "HR": case "OR": case "RR": case "IRR": case "DOR": case "ROM": return "Log " + xlabBack(s);
            case "ZCOR": return "Fisher's z Transformed Correlation";
            case "PLOGIT": case "PLN": case "PAS": case "PFT": return funnelXlab(s);
            case "MLN": return "Log Transformed Mean";
            case "IRLN": return "Log Transformed Incidence Rate"; case "IRS": return "Square Root Transformed Incidence Rate"; case "IRFT": return "Freeman-Tukey Double Arcsine Transformed Incidence Rate";
            default: break;
        }
        return xlabBack(s);
    }
    /** Compact header of the effect column of a forest plot (same scale as xlabFor, shorter names for the transformed proportions / rates). */
    static String forestMeasure(MetaSpec s) {
        if (s.backtransf) return xlabBack(s);
        switch (s.sm) {
            case "PLOGIT": return "Logit Proportion"; case "PLN": return "Log Proportion"; case "PAS": return "Arcsine Proportion"; case "PFT": return "FT Proportion";
            case "IRLN": return "Log Incidence Rate"; case "IRS": return "Sqrt Incidence Rate"; case "IRFT": return "FT Incidence Rate"; case "ZCOR": return "Fisher's z";
            case "MLN": return "Log Mean";
            default: return xlabFor(s);
        }
    }
    static String xlabBack(MetaSpec s) { switch (s.sm) { case "HR": return "Hazard Ratio"; case "OR": return "Odds Ratio"; case "RR": return "Risk Ratio"; case "RD": return "Risk Difference"; case "MD": return "Mean Difference"; case "SMD": return "Standardised Mean Difference"; case "ROM": return "Ratio of Means";
        case "ZCOR": case "COR": return "Correlation"; case "IRR": return "Incidence Rate Ratio"; case "IRD": return "Incidence Rate Difference"; case "PLOGIT": case "PLN": case "PRAW": case "PAS": case "PFT": return "Proportion"; case "MRAW": case "MLN": return "Mean"; case "IR": case "IRLN": case "IRS": case "IRFT": return "Incidence Rate"; case "DOR": return "Diagnostic Odds Ratio"; case "ASD": return "Arcsine Difference"; case "VE": return "Vaccine Efficacy"; default: return Report.smLabel(s); } }

    public static BufferedImage funnel(MetaResult r, Opts o, TrimFill tf) {
        if (FunnelThemes.METAWIN.equals(o.funnelTheme)) return FunnelThemes.metawin(r, o, tf);
        if (FunnelThemes.STATA.equals(o.funnelTheme)) return FunnelThemes.stata(r, o, tf);
        if (FunnelThemes.METAFOR.equals(o.funnelTheme)) return FunnelThemes.metafor(r, o, tf);
        if (FunnelThemes.SPSS.equals(o.funnelTheme)) return FunnelThemes.spss(r, o, tf);
        MetaSpec s = r.spec; final FunnelAxis fa = funnelAxis(s, r, o); boolean log = fa.log; final int extraH = contourOn(o, s) ? 56 : 0; Canvas c = new Canvas(620, 520 + extraH, o.scale);   // contour-enhanced: room below the x-axis title for the legend
        double[] te = tf != null ? tf.TE : r.TE, se = tf != null ? tf.seTE : r.seTE; String[] lab = tf != null ? tf.studlab : r.studlab; boolean[] fl = tf != null ? tf.filled : null;
        double center = tf != null ? (s.random ? tf.pool.teRandom : tf.pool.teCommon) : (s.random ? r.overall.teRandom : r.overall.teCommon);
        double maxse = 0, mn = 1e300, mx = -1e300; for (int i = 0; i < te.length; i++) if (!Double.isNaN(te[i])) { maxse = Math.max(maxse, se[i]); mn = Math.min(mn, te[i]); mx = Math.max(mx, te[i]); }
        double z = Dist.qnorm(1 - (1 - s.levelMa) / 2); double half = Math.max(z * maxse, Math.max(Math.abs(mx - center), Math.abs(mn - center))) * 1.05;
        double xl = center - half, xh = center + half;
        if (contourOn(o, s) && !Double.isNaN(s.nullValue())) { double ref = s.nullValue(); double pad = 0.05 * z * maxse; xl = Math.min(xl, ref - z * maxse - pad); xh = Math.max(xh, ref + z * maxse + pad); }   // the contour bands are centred on the null value: it must be inside the axis range (as in meta / STATA), e.g. hazard ratios far from 1
        { double[] cl = clampRaw(s, xl, xh); xl = cl[0]; xh = cl[1]; }
        if (o.precision) return funnelPrecision(c, s, fa, te, se, lab, fl, center, xl, xh, z, o);
        Axes a = axes(c, xl, xh, 0, maxse * 1.05, true, fa.xlab, "Standard Error", log, "", 60 + extraH, true, false, fa.bk, true);
        Graphics2D g = c.g; double ymax = maxse * 1.05;
        if (contourOn(o, s)) { // contour-enhanced
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
        LabelPlacer lp = LabelPlacer.on(o.repelFunnel && o.studyLabels, a);
        for (int i = 0; i < te.length; i++) { if (Double.isNaN(te[i])) continue; double xx = a.X(te[i]), yy = a.Y(se[i]);
            boolean f = fl != null && fl[i]; if (f) { g.setColor(Color.WHITE); g.fill(new Ellipse2D.Double(xx - 3, yy - 3, 6, 6)); g.setColor(Color.BLACK); g.draw(new Ellipse2D.Double(xx - 3, yy - 3, 6, 6)); }
            else { g.setColor(Color.BLACK); g.fill(new Ellipse2D.Double(xx - 2.5, yy - 2.5, 5, 5)); }
            if (o.studyLabels) { g.setColor(Color.BLACK); lab(lp, c, a, lab[i], xx, yy, 4, -3, false, 3); } }
        if (lp != null) lp.draw(c);
        if (contourOn(o, s)) contourLegend(c, a);
        return c.img;
    }

    /** Contour bands are centred on the null value: without one (single proportions / means / rates and no target) the option is ignored. */
    public static boolean contourOn(Opts o, MetaSpec s) { return o.contour && s.hasNull(); }

    /** Legend of the contour bands of a contour-enhanced funnel plot (two-sided p-value of a study against the null value): framed, one row, centred below the x-axis title (as in the STATA style). */
    static void contourLegend(Canvas c, Axes a) {
        Graphics2D g = c.g; String[] tx = {"p > 0.10", "0.10 > p > 0.05", "0.05 > p > 0.01", "p < 0.01"}; Color[] fl = {new Color(235, 235, 235), new Color(215, 215, 215), new Color(195, 195, 195), Color.WHITE};
        FontMetrics fm = g.getFontMetrics(Canvas.font(10f)); double[] w = new double[tx.length]; double tot = 0; for (int i = 0; i < tx.length; i++) { w[i] = 28 + fm.stringWidth(tx[i]) + 16; tot += w[i]; }
        double bw = tot + 4, bh = 26, x = (a.x0 + a.x1) / 2 - bw / 2, y = a.y1 + 56;
        g.setColor(Color.WHITE); g.fill(new Rectangle2D.Double(x, y, bw, bh)); g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1f)); g.draw(new Rectangle2D.Double(x, y, bw, bh));
        double cx = x + 10; for (int i = 0; i < tx.length; i++) { g.setColor(fl[i]); g.fill(new Rectangle2D.Double(cx, y + 7, 20, 12)); g.setColor(new Color(120, 120, 120)); g.draw(new Rectangle2D.Double(cx, y + 7, 20, 12)); g.setColor(Color.BLACK); c.text(tx[i], cx + 26, y + 17, 10, 0, false); cx += w[i]; }
    }

    /** Default (meta style) funnel plot of precision: precision 1 / SE on the vertical axis (largest at the top), pseudo confidence limits and contour bands are the curves x = centre +/- z / precision. */
    private static BufferedImage funnelPrecision(Canvas c, MetaSpec s, FunnelAxis fa, double[] te, double[] se, String[] lab, boolean[] fl, double center, double xl, double xh, double z, Opts o) {
        double pmax = 0; for (int i = 0; i < te.length; i++) if (!Double.isNaN(te[i]) && se[i] > 0) pmax = Math.max(pmax, 1 / se[i]); if (pmax <= 0) pmax = 1;
        Axes a = axes(c, xl, xh, 0, pmax * 1.05, false, fa.xlab, "Precision (1/SE)", fa.log, "", 60 + (contourOn(o, s) ? 56 : 0), true, false, fa.bk, true); Graphics2D g = c.g; double ymax = pmax * 1.05, half = xh - xl;
        Shape clip = g.getClip(); g.setClip(new Rectangle2D.Double(a.x0, a.y0, a.x1 - a.x0, a.y1 - a.y0));
        if (contourOn(o, s)) { double ref = s.nullValue(); if (Double.isNaN(ref)) ref = 0;
            double[] zz = {Dist.qnorm(0.995), Dist.qnorm(0.975), Dist.qnorm(0.95)}; Color[] fills = {new Color(235, 235, 235), new Color(215, 215, 215), new Color(195, 195, 195)};
            for (int i = 0; i < 3; i++) { g.setColor(fills[2 - i]); g.fill(FunnelThemes.curvePoly(a::X, a::Y, ref, zz[i], 0, ymax, half)); } }
        g.setColor(Color.BLACK); c.dashed(a.X(center), a.y0, a.X(center), a.y1);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{5, 4}, 0));
        g.draw(FunnelThemes.curve(a::X, a::Y, center, -z, 0, ymax, half, true)); g.draw(FunnelThemes.curve(a::X, a::Y, center, z, 0, ymax, half, true)); g.setStroke(new BasicStroke(1f)); g.setClip(clip);
        if (contourOn(o, s)) { g.setColor(Color.BLACK); g.draw(new Rectangle2D.Double(a.x0, a.y0, a.x1 - a.x0, a.y1 - a.y0)); }
        LabelPlacer lp = LabelPlacer.on(o.repelFunnel && o.studyLabels, a);
        for (int i = 0; i < te.length; i++) { if (Double.isNaN(te[i]) || !(se[i] > 0)) continue; double xx = a.X(te[i]), yy = a.Y(1 / se[i]);
            boolean f = fl != null && fl[i]; if (f) { g.setColor(Color.WHITE); g.fill(new Ellipse2D.Double(xx - 3, yy - 3, 6, 6)); g.setColor(Color.BLACK); g.draw(new Ellipse2D.Double(xx - 3, yy - 3, 6, 6)); }
            else { g.setColor(Color.BLACK); g.fill(new Ellipse2D.Double(xx - 2.5, yy - 2.5, 5, 5)); }
            if (o.studyLabels) { g.setColor(Color.BLACK); lab(lp, c, a, lab[i], xx, yy, 4, -3, false, 3); } }
        if (lp != null) lp.draw(c);
        if (contourOn(o, s)) contourLegend(c, a);
        return c.img;
    }

    /** Regression plot belonging to the asymmetry test (Egger radial form). */
    public static BufferedImage biasPlot(Bias.Result b, MetaResult r, Opts o) {
        final boolean fitOk = b.plotX != null && b.fitVb != null && !b.methodBias.equals("Begg") && b.fitCrit == b.fitCrit;
        final boolean ci = o.biasCI && fitOk, pi = o.biasPI && fitOk; final int extra = ci || pi ? 30 : 0;
        Canvas c = new Canvas(600, 480 + extra, o.scale); if (b.plotX == null) { c.text("No plot available for this method", 300, 240, 12, 1, false); return c.img; }
        double xl = 0, xh = Arrays.stream(b.plotX).max().getAsDouble() * 1.05, yl = Math.min(0, Arrays.stream(b.plotY).min().getAsDouble()) - 0.5, yh = Math.max(0, Arrays.stream(b.plotY).max().getAsDouble()) + 0.5;
        if (!b.methodBias.equals("Begg")) yl = Math.min(yl, b.bias - 0.5);
        if (b.methodBias.equals("Begg")) { xl = Arrays.stream(b.plotX).min().getAsDouble() - 0.5; xh = Arrays.stream(b.plotX).max().getAsDouble() + 0.5; yl = 0; yh = Arrays.stream(b.plotY).max().getAsDouble() * 1.05; }
        // confidence / prediction band of the regression y = bias + effect * x: {x, fit, ciLo, ciUp, piLo, piUp}
        final int NG = 120; double[][] bd = null;
        if (ci || pi) {
            bd = new double[6][NG + 1];
            for (int q = 0; q <= NG; q++) { double x = xl + (xh - xl) * q / NG, fit = b.bias + b.intercept * x, v = Math.max(0, x * x * b.fitVb[0] + 2 * x * b.fitVb[1] + b.fitVb[2]);
                double res = Double.isNaN(b.piSigma2) ? 1 + b.piTau2 * x * x : b.piSigma2, hc = b.fitCrit * Math.sqrt(v), hp = b.fitCrit * Math.sqrt(v + res);
                bd[0][q] = x; bd[1][q] = fit; bd[2][q] = fit - hc; bd[3][q] = fit + hc; bd[4][q] = fit - hp; bd[5][q] = fit + hp; }
            for (int q = 0; q <= NG; q++) { int lo = pi ? 4 : 2, up = pi ? 5 : 3; yl = Math.min(yl, bd[lo][q] - 0.2); yh = Math.max(yh, bd[up][q] + 0.2); }
        }
        Axes a = axes(c, xl, xh, yl, yh, false, b.plotXlab, b.plotYlab, false, "", 60 + extra);
        Graphics2D g = c.g;
        if (bd != null) {
            Shape clip = g.getClip(); g.setClip(new Rectangle2D.Double(a.x0, a.y0, a.x1 - a.x0, a.y1 - a.y0));
            if (pi) biasBand(a, bd, 4, 5, new Color(240, 240, 240), new Color(70, 70, 70), RegPlots.DOTTED);   // prediction: lighter area, dotted lines (drawn underneath)
            if (ci) biasBand(a, bd, 2, 3, new Color(215, 215, 215), new Color(70, 70, 70), new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{5f, 4f}, 0));   // confidence: light grey area, dashed lines
            g.setStroke(new BasicStroke(1f)); g.setClip(clip);
        }
        LabelPlacer lp = LabelPlacer.on(o.repelBias && o.studyLabels && !b.methodBias.equals("Begg"), a);
        for (int i = 0; i < b.plotX.length; i++) { double xx = a.X(b.plotX[i]), yy = a.Y(b.plotY[i]); g.setColor(Color.BLACK); g.fill(new Ellipse2D.Double(xx - 2.5, yy - 2.5, 5, 5)); if (o.studyLabels && !b.methodBias.equals("Begg")) lab(lp, c, a, r.studlab[i < r.studlab.length ? i : 0], xx, yy, 4, -3, false, 3); }
        if (!b.methodBias.equals("Begg")) { Shape clip = g.getClip(); g.setClip(new Rectangle2D.Double(a.x0, a.y0, a.x1 - a.x0, a.y1 - a.y0)); g.setColor(Color.BLACK);
            double slopeEff = b.intercept, interceptBias = b.bias; // regression: y = bias + effect * x
            c.line(a.X(xl), a.Y(interceptBias + slopeEff * xl), a.X(xh), a.Y(interceptBias + slopeEff * xh)); g.setClip(clip); }
        if (lp != null) lp.draw(c);
        if (bd != null) {   // legend below the x-axis title
            List<Object[]> it = new ArrayList<>(); if (ci) it.add(new Object[]{false, "95% confidence interval"}); if (pi) it.add(new Object[]{true, "95% prediction interval"});
            FontMetrics fm = g.getFontMetrics(Canvas.font(10f)); double tot = 0; for (Object[] e : it) tot += 44 + fm.stringWidth((String) e[1]) + 18; double lx = (a.x0 + a.x1) / 2 - tot / 2 + 4, ly = a.y1 + 56;
            for (Object[] e : it) { boolean p = (Boolean) e[0]; Rectangle2D.Double sw = new Rectangle2D.Double(lx, ly - 5, 26, 10);
                g.setColor(p ? new Color(240, 240, 240) : new Color(215, 215, 215)); g.fill(sw); g.setColor(new Color(70, 70, 70)); g.setStroke(p ? RegPlots.DOTTED : new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{4f, 3f}, 0));
                c.line(lx, ly - 5, lx + 26, ly - 5); c.line(lx, ly + 5, lx + 26, ly + 5); g.setStroke(new BasicStroke(1f)); g.setColor(Color.BLACK); c.text((String) e[1], lx + 32, ly + 4, 10f, 0, false); lx += 44 + fm.stringWidth((String) e[1]) + 18; }
        }
        return c.img;
    }

    /** Band between two rows of {@code bd} (lower, upper) of the asymmetry-test plot: filled area and the two bounding lines. */
    private static void biasBand(Axes a, double[][] bd, int lo, int up, Color fill, Color edge, Stroke st) {
        Graphics2D g = a.c.g; int n = bd[0].length; Path2D p = new Path2D.Double();
        for (int i = 0; i < n; i++) { double x = a.X(bd[0][i]), y = a.Y(bd[up][i]); if (i == 0) p.moveTo(x, y); else p.lineTo(x, y); }
        for (int i = n - 1; i >= 0; i--) p.lineTo(a.X(bd[0][i]), a.Y(bd[lo][i])); p.closePath();
        g.setColor(fill); g.fill(p); g.setColor(edge); g.setStroke(st);
        for (int row : new int[]{lo, up}) { Path2D q = new Path2D.Double(); for (int i = 0; i < n; i++) { double x = a.X(bd[0][i]), y = a.Y(bd[row][i]); if (i == 0) q.moveTo(x, y); else q.lineTo(x, y); } g.draw(q); }
    }

    /** Funnel plot with the PET line (effect = b0 + b1 * SE) and the PEESE curve (effect = b0 + b1 * SE^2); both start at their intercept = the corrected effect. */
    public static BufferedImage petPeese(PubBias.PetPeese pp, MetaResult r, Opts o) {
        MetaSpec s = r.spec; final java.util.function.DoubleUnaryOperator bk = backAxis(s, r); boolean log = s.backtransf && s.isRatioSm() && bk == null; Canvas c = new Canvas(620, 520, o.scale);
        if (pp.error != null) { c.text(pp.error, 310, 260, 12, 1, false); return c.img; }
        double center = s.random ? r.overall.teRandom : r.overall.teCommon, maxse = pp.maxSe * 1.05, mn = center, mx = center;
        for (double t : pp.te) { mn = Math.min(mn, t); mx = Math.max(mx, t); }
        double[] ends = {pp.pet.b0, pp.peese.b0, pp.pet.b0 + pp.pet.b1 * maxse, pp.peese.b0 + pp.peese.b1 * maxse * maxse};
        for (double e : ends) { mn = Math.min(mn, e); mx = Math.max(mx, e); }
        double pad = (mx - mn) * 0.06 + 1e-9; double xl = mn - pad, xh = mx + pad; { double[] cl = clampRaw(s, xl, xh); xl = cl[0]; xh = cl[1]; }
        double dmn = Double.POSITIVE_INFINITY, dmx = Double.NEGATIVE_INFINITY; for (double t : pp.te) { dmn = Math.min(dmn, t); dmx = Math.max(dmx, t); }
        Axes a = axes(c, xl, xh, 0, maxse, true, xlabFor(s), "Standard Error", log, "", 60, false, false, bk, true, dmn, dmx); Graphics2D g = c.g;
        Shape clip = g.getClip(); g.setClip(new Rectangle2D.Double(a.x0, a.y0, a.x1 - a.x0, a.y1 - a.y0));
        g.setColor(Color.GRAY); c.dashed(a.X(center), a.Y(0), a.X(center), a.Y(maxse));
        Color petCol = new Color(0x1F4E8C), peeseCol = new Color(0xD9822B); g.setStroke(new BasicStroke(1.8f));
        g.setColor(petCol); c.line(a.X(pp.pet.b0), a.Y(0), a.X(pp.pet.b0 + pp.pet.b1 * maxse), a.Y(maxse));
        g.setColor(peeseCol); double px = a.X(pp.peese.b0), py = a.Y(0);
        for (int i = 1; i <= 60; i++) { double se = maxse * i / 60.0, ex = pp.peese.b0 + pp.peese.b1 * se * se; double nx = a.X(ex), ny = a.Y(se); c.line(px, py, nx, ny); px = nx; py = ny; }
        g.setStroke(new BasicStroke(1f)); g.setClip(clip);
        LabelPlacer lp = LabelPlacer.on(o.repelPet && o.studyLabels, a);
        if (lp != null) for (int q = 0; q < 2; q++) lp.point(a.X(q == 0 ? pp.pet.b0 : pp.peese.b0), a.Y(0), 4 * Math.sqrt(2));   // the PET and PEESE estimates (squares at SE = 0) are drawn after the labels: keep the labels off them
        for (int i = 0; i < pp.te.length; i++) { double xx = a.X(pp.te[i]), yy = a.Y(pp.se[i]); g.setColor(Color.BLACK); g.fill(new Ellipse2D.Double(xx - 2.5, yy - 2.5, 5, 5)); if (o.studyLabels) lab(lp, c, a, pp.label[i], xx, yy, 4, -3, false, 3); }
        if (lp != null) lp.draw(c);
        for (int q = 0; q < 2; q++) { double ex = q == 0 ? pp.pet.b0 : pp.peese.b0; g.setColor(q == 0 ? petCol : peeseCol); g.fill(new Rectangle2D.Double(a.X(ex) - 4, a.Y(0) - 4, 8, 8)); }
        double lx = a.x0 + 4; g.setStroke(new BasicStroke(1.8f));
        g.setColor(petCol); c.line(lx, 22, lx + 22, 22); g.setColor(Color.BLACK); c.text("PET", lx + 26, 26, 10, 0, false);
        g.setColor(peeseCol); c.line(lx + 70, 22, lx + 92, 22); g.setColor(Color.BLACK); c.text("PEESE", lx + 96, 26, 10, 0, false);
        g.setStroke(new BasicStroke(1f)); g.setColor(Color.GRAY); c.dashed(lx + 160, 22, lx + 182, 22); g.setColor(Color.BLACK); c.text("pooled effect", lx + 186, 26, 10, 0, false);
        return c.img;
    }

    /** Default-style prediction band: lighter than the confidence band (drawn underneath it), with dotted bounds. */
    private static void metaPred(Axes a, double[][] cv, boolean log, Graphics2D g, Opts o) {
        Path2D p = new Path2D.Double(); int n = cv[0].length;
        for (int i = 0; i < n; i++) { double yy = a.Y(log ? Math.exp(cv[5][i]) : cv[5][i]); if (i == 0) p.moveTo(a.X(cv[0][i]), yy); else p.lineTo(a.X(cv[0][i]), yy); }
        for (int i = n - 1; i >= 0; i--) p.lineTo(a.X(cv[0][i]), a.Y(log ? Math.exp(cv[4][i]) : cv[4][i])); p.closePath();
        g.setColor(RegPlots.metaPred(o)); g.fill(p);
        g.setColor(RegPlots.metaPredLine(o)); g.setStroke(RegPlots.DOTTED);
        for (int row = 4; row <= 5; row++) { Path2D q = new Path2D.Double(); for (int i = 0; i < n; i++) { double yy = a.Y(log ? Math.exp(cv[row][i]) : cv[row][i]); if (i == 0) q.moveTo(a.X(cv[0][i]), yy); else q.lineTo(a.X(cv[0][i]), yy); } g.draw(q); }
        g.setStroke(new BasicStroke(1f));
    }

    public static BufferedImage bubble(Reg g0, Opts o, MetaResult r) {
        if (g0.x1 != null && RegPlots.GG.equals(o.bubbleStyle)) return RegPlots.ggBubble(g0, o, r);
        MetaSpec s = r.spec; final boolean ratio = s.backtransf && s.isRatioSm(), ly = ratio && o.logAxis, log = ratio && !ly; List<RegPlots.LegItem> leg = RegPlots.legendItems(o, s, false); int ext = RegPlots.legendExtra(leg); Canvas c = new Canvas(620, 520 + ext, o.scale);
        if (g0.x1 == null) { c.text("Bubble plot requires exactly one numeric covariate", 310, 260, 12, 1, false); return c.img; }
        double dxl = Arrays.stream(g0.x1).min().getAsDouble(), dxh = Arrays.stream(g0.x1).max().getAsDouble(), xl = dxl, xh = dxh, pad = (xh - xl) * 0.08 + 1e-9; xl -= pad; xh += pad;
        final boolean icpt = g0.intercept; double[][] cv = RegPlots.curve(g0, s, dxl, dxh, x -> icpt ? new double[]{1, x} : new double[]{x});
        double yl = Double.POSITIVE_INFINITY, yh = Double.NEGATIVE_INFINITY; for (double v : g0.y1) { double t = log ? Math.exp(v) : v; yl = Math.min(yl, t); yh = Math.max(yh, t); }
        for (int i = 0; i < cv[0].length; i++) for (int q : RegPlots.rowsShown(o)) { double t = log ? Math.exp(cv[q][i]) : cv[q][i]; yl = Math.min(yl, t); yh = Math.max(yh, t); }
        double yp = (yh - yl) * 0.1 + 1e-9; yl -= yp; yh += yp;
        Axes a = axes(c, xl, xh, yl, yh, false, g0.xName, xlabFor(s) + (ly ? " (log scale)" : ""), false, "", 60 + ext, false, ly);   // results not back-transformed: the axis shows the log ratio
        double wmax = Arrays.stream(g0.size).max().getAsDouble(); Graphics2D g = c.g; Shape clip0 = g.getClip();
        g.setClip(new Rectangle2D.Double(a.x0, a.y0, a.x1 - a.x0, a.y1 - a.y0));
        if (o.predInt) metaPred(a, cv, log, g, o);   // prediction band: lighter than the confidence band, dotted bounds
        if (o.confInt) {   // lighter band between the confidence bounds, dashed bounds
            Path2D p = new Path2D.Double(); int n = cv[0].length;
            for (int i = 0; i < n; i++) { double yy = a.Y(log ? Math.exp(cv[3][i]) : cv[3][i]); if (i == 0) p.moveTo(a.X(cv[0][i]), yy); else p.lineTo(a.X(cv[0][i]), yy); }
            for (int i = n - 1; i >= 0; i--) p.lineTo(a.X(cv[0][i]), a.Y(log ? Math.exp(cv[2][i]) : cv[2][i])); p.closePath();
            g.setColor(RegPlots.metaBand(o)); g.fill(p);
            g.setColor(RegPlots.metaBandLine(o)); g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{5f, 4f}, 0));
            for (int row = 2; row <= 3; row++) { Path2D q = new Path2D.Double(); for (int i = 0; i < n; i++) { double yy = a.Y(log ? Math.exp(cv[row][i]) : cv[row][i]); if (i == 0) q.moveTo(a.X(cv[0][i]), yy); else q.lineTo(a.X(cv[0][i]), yy); } g.draw(q); }
            g.setStroke(new BasicStroke(1f)); }
        g.setClip(clip0);
        LabelPlacer lp = LabelPlacer.on(o.repelBubble && o.studyLabels, a);
        for (int i = 0; i < g0.x1.length; i++) { double yv = log ? Math.exp(g0.y1[i]) : g0.y1[i]; double rad = o.symSize * (4 + 12 * Math.sqrt(g0.size[i] / wmax)); double xx = a.X(g0.x1[i]), yy = a.Y(yv);
            { boolean own = !RegPlots.isAuto(o.colPoints); Color pf = RegPlots.metaPoints(o); RegPlots.drawSymbol(g, o.symbol, xx, yy, rad, pf, 200, own && RegPlots.isOpen(o.symbol) ? pf : Color.BLACK, 1f); }
            if (o.studyLabels) lab(lp, c, a, r.studlab[g0.used[i]], xx, yy, rad + 2, -2, true, rad * RegPlots.symbolExtent(o.symbol)); }
        if (o.regLine) { Path2D q = new Path2D.Double(); for (int i = 0; i < cv[0].length; i++) { double yy = a.Y(log ? Math.exp(cv[1][i]) : cv[1][i]); if (i == 0) q.moveTo(a.X(cv[0][i]), yy); else q.lineTo(a.X(cv[0][i]), yy); } g.setColor(RegPlots.metaLine(o)); g.draw(q); }
        if (lp != null) lp.draw(c);
        RegPlots.drawLegend(c, (a.x0 + a.x1) / 2, a.y1 + 52, leg);
        return c.img;
    }

    /** Baujat points, computed once per analysis and model (rendering the plot again, e.g. to save it at a higher resolution, reuses them) and in parallel.
     *  Common-effect model (meta::baujat): contribution to overall heterogeneity, influence on the overall result, one leave-one-out refit per study; only the common-effect refit is read, so GLMM refits
     *  skip the random-effects fit and the likelihood-ratio heterogeneity test. Random-effects model (metafor::baujat.rma): see {@link org.metajava.core.Influence#baujatRandom}. */
    private static void baujatPoints(MetaResult r, boolean rnd) {
        if ((rnd ? r.baujatPtsRnd : r.baujatPts) != null) return;
        synchronized (r.baujatLock) {   // per analysis: other analyses (subgroups) are not held up, and the labels are stored before the points (both volatile)
        if ((rnd ? r.baujatPtsRnd : r.baujatPts) != null) return;
        final int n = r.TE.length; final List<Integer> use = new ArrayList<>();
        for (int i = 0; i < n; i++) if (!Double.isNaN(r.TE[i]) && r.seTE[i] > 0) use.add(i);
        if (rnd) {
            final MetaSpec s = r.spec; final int k = use.size(); double[] y = new double[k], v = new double[k]; int[] sel = new int[k];
            for (int j = 0; j < k; j++) { int i = use.get(j); y[j] = r.TE[i]; v[j] = r.seTE[i] * r.seTE[i]; sel[j] = i; }
            boolean hk = s.methodRandomCi.equals("HK");   // test = "knha" is passed on to every refit, as metafor does
            double[][] xy = org.metajava.core.Influence.baujatRandom(y, v, s.methodTau, Pool.genqWeightsFor(s, sel), hk, hk && s.adhocHakn.equals("se"));
            List<double[]> pts = new ArrayList<>(); List<String> labs = new ArrayList<>(); for (int q = 0; q < k; q++) if (xy[q] != null) { pts.add(xy[q]); labs.add(r.studlab[use.get(q)]); }
            r.baujatLabsRnd = labs; r.baujatPtsRnd = pts; return;
        }
        final MetaSpec s2 = r.spec.clone(); s2.skipTauCi = true; s2.skipLrt = true; s2.skipHet = true; if (s2.isGlmm()) s2.random = false;
        final Pool p = r.overall;
        final double[][] xy = new double[use.size()][];
        org.metajava.core.Par.forRange(xy.length, q -> {
            int i = use.get(q); double w = 1 / (r.seTE[i] * r.seTE[i]); double x = w * Math.pow(r.TE[i] - p.teCommon, 2);
            int[] sub = new int[n - 1]; int cc = 0; for (int j = 0; j < n; j++) if (j != i) sub[cc++] = j; Pool qq = Pool.compute(s2, r.TE, r.seTE, sub, r.commonFn);
            xy[q] = new double[]{x, Math.pow(p.teCommon - qq.teCommon, 2) / (qq.seCommon * qq.seCommon), w};
        });
        List<double[]> pts = new ArrayList<>(); List<String> labs = new ArrayList<>(); for (int q = 0; q < xy.length; q++) { pts.add(xy[q]); labs.add(r.studlab[use.get(q)]); }
        r.baujatLabs = labs; r.baujatPts = pts;
        }
    }

    /** Baujat plot: "Baujat model" = common effect (meta::baujat) or the model of the analysis (metafor::baujat.rma when it is a random-effects model). */
    public static BufferedImage baujat(MetaResult r, Opts o) {
        final boolean rnd = BaujatPlot.useRandom(r.spec, o);
        baujatPoints(r, rnd); List<double[]> pts = rnd ? r.baujatPtsRnd : r.baujatPts; List<String> labs = rnd ? r.baujatLabsRnd : r.baujatLabs;
        if (BaujatPlot.isGg(o)) return BaujatPlot.gg(pts, labs, o, rnd);
        Canvas c = new Canvas(560, 520, o.scale); double xm = 0, ym = 0; for (double[] q : pts) { xm = Math.max(xm, q[0]); ym = Math.max(ym, q[1]); }
        { double[] e = BaujatPlot.extend(xm, ym, o, pts); xm = e[0]; ym = e[1]; }   // quadrant lines always inside the plot
        c.g.setColor(BaujatPlot.backColour(o, false)); c.g.fill(new Rectangle2D.Double(70, 40, c.w / c.scaleF() - 100, c.h / c.scaleF() - 100));   // panel background (the frame is drawn on top)
        Axes a = axes(c, 0, xm * 1.08, 0, ym * 1.08, false, rnd ? "Squared Pearson Residual" : "Contribution to overall heterogeneity", "Influence on overall result", false, "");
        BaujatPlot.quadrants(c, a::X, a::Y, a.x0, a.y0, a.x1, a.y1, pts, o);   // optional quadrants (shading, lines, captions) below the points
        Color sym = BaujatPlot.symbolColour(o, false); double d = 5 * o.baujatSymSize;   // symbol diameter: 5 x "Baujat symbol size"
        final boolean[] fl = BaujatPlot.flagged(pts, o);   // studies in the shaded quadrant: symbol and label in the colour of the quadrant lines
        for (int i = 0; i < pts.size(); i++) { double xx = a.X(pts.get(i)[0]), yy = a.Y(pts.get(i)[1]); boolean hot = fl != null && fl[i]; c.g.setColor(hot ? BaujatPlot.quadLine(o) : sym); c.g.fill(new Ellipse2D.Double(xx - d / 2, yy - d / 2, d, d)); if (o.studyLabels) { c.g.setColor(hot ? BaujatPlot.quadLine(o) : Color.BLACK); ptLabel(c, a, labs.get(i), xx, yy, 4 + (d - 5) / 2, -3 - (d - 5) / 2, false); } }
        c.g.setColor(Color.BLACK); return c.img;
    }

    /** Radial (Galbraith) plot: see {@link RadialPlot} (Default, metafor, ggplot2 and STATA themes, colours, study labels, legend). */
    public static BufferedImage radial(MetaResult r, Opts o) { return RadialPlot.draw(r, o); }

    /** Doi plot (Furuya-Kanamori et al. 2018): |Z-score| (reversed axis) against the effect size, studies joined in order of effect; optional dashed line through the apex (Opts.apexLine, off as in metasens::doiplot). */
    public static BufferedImage doi(Lfk l, MetaResult r, Opts o) {
        MetaSpec s = r.spec; final java.util.function.DoubleUnaryOperator bk = backAxis(s, r); boolean log = s.backtransf && s.isRatioSm() && bk == null; Canvas c = new Canvas(620, 520, o.scale); int k = l.k;
        if (l.lfk != l.lfk) { c.text("Doi plot not available (needs at least two studies with different effects)", 310, 260, 12, 1, false); return c.img; }
        double mn = l.te[0], mx = l.te[k - 1], pad = (mx - mn) > 0 ? (mx - mn) * 0.06 : 0.5, yh = 0; for (double z : l.absZ) yh = Math.max(yh, z); yh = Math.max(yh * 1.08, 0.5);
        Axes a = axes(c, mn - pad, mx + pad, 0, yh, true, xlabFor(s), "|Z-score|", log, "", 60, false, false, bk, true, mn, mx); Graphics2D g = c.g;
        g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1f));
        if (o.apexLine) c.dashed(a.X(l.te[l.apex]), a.Y(0), a.X(l.te[l.apex]), a.y1);   // not part of metasens::doiplot: optional
        for (int j = 1; j < k; j++) c.line(a.X(l.te[j - 1]), a.Y(l.absZ[j - 1]), a.X(l.te[j]), a.Y(l.absZ[j]));
        LabelPlacer lp = LabelPlacer.on(o.repelDoi && o.studyLabels, a);
        if (lp != null) lp.block(a.x0 + 2, a.y0 + 2, a.x0 + 14 + c.g.getFontMetrics(Canvas.font(11f)).stringWidth("LFK index: " + Fmt.num(l.lfk, 2)), a.y0 + 24);   // the LFK index text is drawn after the labels
        for (int j = 0; j < k; j++) { double xx = a.X(l.te[j]), yy = a.Y(l.absZ[j]); g.fill(new Ellipse2D.Double(xx - 3, yy - 3, 6, 6)); if (o.studyLabels) lab(lp, c, a, l.label[j], xx, yy, 5, -4, false, 3); }
        if (lp != null) lp.draw(c);
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
        if (LabbePlot.isGg(o)) return LabbePlot.gg(r, o, w, lim, ee, ne, ec, nc);
        Graphics2D g0 = c.g; g0.setColor(LabbePlot.pick(o.labbeBack, Color.WHITE)); g0.fill(new Rectangle2D.Double(70, 40, c.w / c.scaleF() - 100, c.h / c.scaleF() - 100));   // panel background (the frame is drawn on top)
        Axes a = axes(c, 0, lim, 0, lim, false, "Event rate (" + s.labelC + ")", "Event rate (" + s.labelE + ")", false, ""); Graphics2D g = c.g;
        Shape clip = g.getClip(); g.setClip(new Rectangle2D.Double(a.x0, a.y0, a.x1 - a.x0, a.y1 - a.y0));
        { LabbePlot.Model mo = LabbePlot.model(r); Color pc = LabbePlot.chosen(o.labbePI), cc = LabbePlot.chosen(o.labbeCI);   // confidence / prediction areas: only when a colour was chosen (the current meta / metafor plot has none)
          if (pc != null && mo.hasPi()) LabbePlot.area(a, s, mo.piLo, mo.piUp, lim, pc); if (cc != null && mo.hasCi()) LabbePlot.area(a, s, mo.ciLo, mo.ciUp, lim, cc); }
        g.setColor(LabbePlot.pick(o.labbeRef, new Color(211, 211, 211))); g.setStroke(new BasicStroke(1f)); c.line(a.X(0), a.Y(0), a.X(lim), a.Y(lim));   // no effect (lightgray)
        g.setColor(LabbePlot.pick(o.labbeEst, Color.BLACK));
        if (s.common) labbeLine(c, a, s, r.overall.teCommon, false, lim); if (s.random) labbeLine(c, a, s, r.overall.teRandom, true, lim);
        g.setClip(clip);
        LabelPlacer lp = LabelPlacer.on(o.repelLabbe && o.studyLabels, a);
        if (lp != null) { double[] lg = labbeLegendBox(c, a, r, o, lim); lp.block(lg[0] - 2, lg[1] - 2, lg[0] + lg[2] + 2, lg[1] + lg[3] + 2); }   // the legend is drawn after the labels
        for (int i = 0; i < n; i++) { if (Double.isNaN(r.TE[i]) || Double.isNaN(w[i]) || ne[i] <= 0 || nc[i] <= 0) continue;
            double xx = a.X(ec[i] / nc[i]), yy = a.Y(ee[i] / ne[i]), dia = 4 * w[i], rad = dia / 2;   // metafor: cex = psize in [0.5, 3.5] -> diameter 2 to 14 px
            g.setColor(LabbePlot.pick(o.labbeCircle, LabbePlot.DEF_CIRCLE)); g.fill(new Ellipse2D.Double(xx - rad, yy - rad, dia, dia)); g.setColor(LabbePlot.pick(o.labbeSymbol, Color.BLACK)); g.setStroke(new BasicStroke(1f)); g.draw(new Ellipse2D.Double(xx - rad, yy - rad, dia, dia));
            if (o.studyLabels) { g.setColor(Color.BLACK); lab(lp, c, a, r.studlab[i], xx, yy, rad + 3, 3, true, rad); } }
        if (lp != null) lp.draw(c);
        labbeLegend(c, a, r, o, lim);
        g.setColor(Color.BLACK); return c.img;
    }
    /** Legend box of the Default L'Abbe plot: reference line, line(s) of the pooled effect (common / random: dash patterns as drawn) and the confidence / prediction areas when drawn. As in the ggplot2
     *  style it sits at the top left while the estimated-effect line lies below the no-effect line and at the lower right when it lies above it, where it never covers the studies. */
    /** Rectangle {x, y, width, height} of the legend inside the panel of the L'Abbe plot (labels must stay off it). */
    private static double[] labbeLegendBox(Canvas c, Axes a, MetaResult r, Opts o, double lim) {
        MetaSpec s = r.spec; Graphics2D g = c.g; LabbePlot.Model mo = LabbePlot.model(r);
        boolean line = LabbePlot.hasLine(s.sm) && !Double.isNaN(mo.te); Color pc = LabbePlot.chosen(o.labbePI), cc = LabbePlot.chosen(o.labbeCI);
        List<String> labs = new ArrayList<>(); labs.add("Reference line (no effect)");
        if (line) { if (s.common && !Double.isNaN(r.overall.teCommon)) labs.add(s.textCommon); if (s.random && !Double.isNaN(r.overall.teRandom)) labs.add(s.textRandom); if (cc != null && mo.hasCi()) labs.add(Report.pct(s.levelMa) + "% confidence interval"); if (pc != null && mo.hasPi()) labs.add(Report.pct(s.levelPredict) + "% prediction interval"); }
        FontMetrics fm = g.getFontMetrics(Canvas.font(11f)); double tw = 0; for (String x : labs) tw = Math.max(tw, fm.stringWidth(x));
        double sample = 36, rowH = 18, bw = 10 + sample + 10 + tw + 12, bh = 8 + rowH * labs.size();
        boolean upper = line && LabbePlot.lineY(s.sm, mo.te, lim / 2) > lim / 2;
        return new double[]{upper ? a.x1 - 10 - bw : a.x0 + 10, upper ? a.y1 - 10 - bh : a.y0 + 10, bw, bh};
    }
    private static void labbeLegend(Canvas c, Axes a, MetaResult r, Opts o, double lim) {
        MetaSpec s = r.spec; Graphics2D g = c.g; LabbePlot.Model mo = LabbePlot.model(r);
        boolean line = LabbePlot.hasLine(s.sm) && !Double.isNaN(mo.te); Color pc = LabbePlot.chosen(o.labbePI), cc = LabbePlot.chosen(o.labbeCI);
        List<String> labs = new ArrayList<>(); List<Integer> kinds = new ArrayList<>();   // 0 reference, 1 common-effect line, 2 random-effects line, 3 confidence area, 4 prediction area
        labs.add("Reference line (no effect)"); kinds.add(0);
        if (line) { if (s.common && !Double.isNaN(r.overall.teCommon)) { labs.add(s.textCommon); kinds.add(1); } if (s.random && !Double.isNaN(r.overall.teRandom)) { labs.add(s.textRandom); kinds.add(2); } }
        if (line && cc != null && mo.hasCi()) { labs.add(Report.pct(s.levelMa) + "% confidence interval"); kinds.add(3); }
        if (line && pc != null && mo.hasPi()) { labs.add(Report.pct(s.levelPredict) + "% prediction interval"); kinds.add(4); }
        FontMetrics fm = g.getFontMetrics(Canvas.font(11f)); double tw = 0; for (String x : labs) tw = Math.max(tw, fm.stringWidth(x));
        double sample = 36, rowH = 18, bw = 10 + sample + 10 + tw + 12, bh = 8 + rowH * labs.size();
        boolean upper = line && LabbePlot.lineY(s.sm, mo.te, lim / 2) > lim / 2;
        double bx = upper ? a.x1 - 10 - bw : a.x0 + 10, by = upper ? a.y1 - 10 - bh : a.y0 + 10;
        g.setColor(Color.WHITE); g.fill(new Rectangle2D.Double(bx, by, bw, bh)); g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1f)); g.draw(new Rectangle2D.Double(bx, by, bw, bh));
        for (int i = 0; i < labs.size(); i++) { double cy = by + 4 + rowH * (i + 0.5), sx = bx + 10;
            switch (kinds.get(i)) {
                case 0: g.setColor(LabbePlot.pick(o.labbeRef, new Color(211, 211, 211))); g.setStroke(new BasicStroke(1f)); c.line(sx, cy, sx + sample, cy); break;
                case 1: case 2: g.setColor(LabbePlot.pick(o.labbeEst, Color.BLACK)); g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, kinds.get(i) == 2 ? new float[]{9, 3} : new float[]{5, 4}, 0)); c.line(sx, cy, sx + sample, cy); break;
                case 3: g.setColor(cc); g.fill(new Rectangle2D.Double(sx + sample / 2 - 9, cy - 6, 18, 12)); break;
                default: g.setColor(pc); g.fill(new Rectangle2D.Double(sx + sample / 2 - 9, cy - 6, 18, 12)); }
            g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1f)); c.text(labs.get(i), sx + sample + 10, cy + 4, 11f, 0, false); }
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
        for (int i = 0; i <= 100; i++) { double x = lim * i / 100.0, y = LabbePlot.lineY(s.sm, te, x); if (Double.isNaN(y)) { ok = false; y = x; }
            xs[i] = x; ys[i] = y; }
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
