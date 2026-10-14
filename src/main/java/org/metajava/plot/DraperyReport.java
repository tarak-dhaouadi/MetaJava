package org.metajava.plot;

import org.metajava.core.*;

import java.util.*;

/**
 * "Drapery Plot Analysis and Interpretation": a statistical report and an automatically generated narrative for the drapery plot.
 * Everything is read from the fitted model and from the curves that the plot really draws (the same {@link Drapery#shown} decision, the same normal-theory p-value
 * functions, the same null effect), so the text never contradicts the figure: the random-effects and the common-effect results are reported separately, the prediction
 * interval is kept apart from the confidence interval, statements about the prediction region are only made when it is displayed, and nothing is said about publication bias
 * beyond the reminder that a drapery plot does not test for it. Plain text (tau^2, alpha) like the rest of the output.
 */
public final class DraperyReport {
    private DraperyReport() {}

    public static final String TITLE = "Drapery Plot Analysis and Interpretation";
    public static final double ALPHA = 0.05;

    public static final class Result {
        /** Statistical report, in the order of the template: A = title, 1 and 2; the individual-study table (3, expandable in the window); B = 4 and 5. */
        public String partA, studyTitle, studyNote, partB;
        public String studyRendered;
        /** Manuscript-ready narrative ("Drapery Plot Analysis"), one paragraph per element. */
        public final List<String> narrative = new ArrayList<>();
        public boolean available = true;
        public String statisticalReport() { return partA + "\n" + studyTitle + "\n" + studyNote + "\n\n" + studyRendered + "\n" + partB; }
        public String narrativeText() { return String.join("\n\n", narrative); }
        /** Whole section as one text (the table at its place), for the Word / Excel export and the text file. */
        public String combined() { return statisticalReport() + "\n\nDrapery Plot Analysis\n\n" + narrativeText() + "\n"; }
    }

    private static boolean ok(double v) { return !Double.isNaN(v) && !Double.isInfinite(v); }
    private static String n4(double v) { return Fmt.num(v, 4); }
    /** Null value without superfluous decimals (1 rather than 1.0000). */
    private static String nv(double v) { return Math.abs(v - Math.rint(v)) < 1e-12 ? String.valueOf((long) Math.rint(v)) : n4(v); }
    private static String pEq(double p) { return "p " + Fmt.pvalEq(p, 4); }
    private static String ci(double lo, double up) { return "[" + n4(lo) + ", " + n4(up) + "]"; }
    private static String pct(double level) { return Report.pct(level); }

    static String effectName(MetaSpec s) {
        switch (s.sm) {
            case "OR": return "Odds ratio (OR)"; case "RR": return "Risk ratio (RR)"; case "HR": return "Hazard ratio (HR)"; case "IRR": return "Incidence rate ratio (IRR)";
            case "DOR": return "Diagnostic odds ratio (DOR)"; case "ROM": return "Ratio of means (ROM)"; case "RD": return "Risk difference (RD)"; case "ASD": return "Arcsine difference (ASD)";
            case "MD": return "Mean difference (MD)"; case "SMD": return "Standardised mean difference (SMD)"; case "COR": return "Correlation (COR)"; case "ZCOR": return "Fisher's z transformed correlation (ZCOR)";
            case "PLOGIT": return "Logit-transformed proportion (PLOGIT)"; case "PLN": return "Log-transformed proportion (PLN)"; case "PAS": return "Arcsine-transformed proportion (PAS)"; case "PFT": return "Freeman-Tukey double-arcsine proportion (PFT)";
            case "PRAW": return "Raw proportion (PRAW)"; case "MRAW": return "Raw mean (MRAW)"; case "MLN": return "Log-transformed mean (MLN)"; case "IR": return "Incidence rate (IR)"; case "IRLN": return "Log incidence rate (IRLN)";
            case "IRS": return "Square-root incidence rate (IRS)"; case "IRFT": return "Freeman-Tukey incidence rate (IRFT)"; case "IRD": return "Incidence rate difference (IRD)"; case "VE": return "Vaccine efficacy (VE)";
            default: return s.sm.isEmpty() ? "Effect size (TE)" : s.sm;
        }
    }
    /** Measures that are not back-transformed on the drapery plot although the analysis transforms them: shown on the analysis scale. */
    private static boolean analysisScale(MetaSpec s) { return !Drapery.relative(s.sm) && Arrays.asList("ZCOR", "PLOGIT", "PLN", "PAS", "PFT", "MLN", "IRLN", "IRS", "IRFT", "VE").contains(s.sm); }

    public static Result build(MetaResult r, Plots.Opts o) {
        MetaSpec s = r.spec; Pool P = r.overall; Result R = new Result();
        final boolean rel = Drapery.relative(s.sm), zType = !Drapery.PVALUE.equals(o.drapType);
        int k = 0; for (int i = 0; i < r.TE.length; i++) if (ok(r.TE[i]) && ok(r.seTE[i]) && r.seTE[i] > 0) k++;
        if (k == 0) { R.available = false; return R; }
        Drapery.Shown sh = Drapery.shown(r, o); boolean rnd = sh.random, com = sh.common, both = rnd && com, pred = sh.pred;
        final double nullW = Drapery.nullOf(s); final boolean hasNull = ok(nullW);
        final java.util.function.DoubleUnaryOperator v = x -> rel ? Math.exp(x) : x;   // plotted scale
        final String eff = rel ? Report.smLabel(s) : s.sm.isEmpty() ? "TE" : s.sm, cl = pct(s.levelMa) + "% CI";
        final double zc = Dist.qnorm(1 - ALPHA / 2);
        String modelName = both ? "Common-effect and random-effects (reported separately)" : rnd ? "Random-effects" : "Common-effect";

        // ------------------------------------------------------------------ part A: title, 1. Analysis summary, 2. Pooled effect
        StringBuilder a = new StringBuilder();
        a.append("Drapery Plot - Statistical Report\n");
        a.append("Analysis model: ").append(modelName).append("\n\n");
        a.append("1. Analysis summary\n\n");
        a.append("Studies included: k = ").append(k).append("\n");
        a.append("Effect measure: ").append(effectName(s)).append("\n");
        if (rnd) { if (ok(P.tau2) && !P.lrp) a.append("Between-study variance: tau^2 = ").append(n4(P.tau2)).append(" (").append(Report.tauLabel(s.methodTau)).append(" estimator)\n");
            else a.append("Between-study variance: not reported for this pooling method\n"); }
        else a.append("Between-study variance: not part of the common-effect model\n");
        if (P.k >= 2 && ok(P.I2)) a.append("Heterogeneity (Q-based): I^2 = ").append(Fmt.num(100 * P.I2, 1)).append("%, Q = ").append(Fmt.num(P.Q, 2)).append(" (d.f. = ").append(P.dfQ).append("), ").append(pEq(P.pQ)).append("\n");
        a.append("Display: ").append(zType ? "Test-statistic (z-value) curves, i.e. scaled p-value functions; the right-hand axis gives the confidence level" : "P-value functions").append("\n");
        a.append("Null effect: ").append(hasNull ? eff + " = " + nv(v.applyAsDouble(nullW)) : "not defined for this measure (no threshold statements are made)").append("\n");
        a.append("Scale of the estimates: ").append(rel ? "back-transformed (the x-axis of the plot is logarithmic)" : analysisScale(s) ? "analysis scale, as plotted (not back-transformed): " + Plots.xlabFor(s) : "as analysed").append("\n");
        a.append("\n2. Pooled effect\n");
        if (rnd) a.append(pooled(r, P, s, true, both, eff, cl, nullW, hasNull, zc, v));
        if (com) a.append(pooled(r, P, s, false, both, eff, cl, nullW, hasNull, zc, v));
        R.partA = a.toString();

        // ------------------------------------------------------------------ 3. Individual studies (table; expandable in the window)
        R.studyTitle = "3. Individual studies";
        R.studyNote = "Study-level estimates and " + pct(s.level) + "% confidence intervals. P-value (null): the value of the study's own p-value curve at the null effect (normal approximation, as plotted)" + (hasNull ? "." : "; not available because the measure has no null effect.")
            + " Observed power: power of the two-sided z-test at alpha = 0.05 if the true effect equalled the study's observed estimate (post hoc; it is a direct function of the study's p-value and adds no independent information).";
        List<String> head = new ArrayList<>(List.of("#", "Study", eff, pct(s.level) + "% CI", "p (null)")); boolean wr = !s.noWeights() && rnd, wc = !s.noWeights() && com;
        if (wr) head.add("%W(random)"); if (wc) head.add("%W(common)"); head.add("Observed power");
        double sumC = 0, sumR = 0; for (int i = 0; i < r.TE.length; i++) { if (r.wCommonFull != null && ok(r.wCommonFull[i])) sumC += r.wCommonFull[i]; if (r.wRandomFull != null && ok(r.wRandomFull[i])) sumR += r.wRandomFull[i]; }
        List<String[]> rows = new ArrayList<>(); int sigStudies = 0;
        for (int i = 0; i < r.TE.length; i++) {
            List<String> row = new ArrayList<>(); row.add(String.valueOf(i + 1)); row.add(r.studlab[i]);
            boolean good = ok(r.TE[i]) && ok(r.seTE[i]) && r.seTE[i] > 0;
            if (!good) { row.add("NA"); row.add(""); row.add("NA"); }
            else { row.add(n4(v.applyAsDouble(r.TE[i]))); row.add(ok(r.lower[i]) ? Fmt.ci(v.applyAsDouble(r.lower[i]), v.applyAsDouble(r.upper[i]), 4) : "");
                double p = hasNull ? Drapery.curve(false, nullW, r.TE[i], r.seTE[i]) : Double.NaN; if (hasNull && p < ALPHA) sigStudies++; row.add(hasNull ? Fmt.pval(p, 4) : "NA"); }
            if (wr) row.add(r.wRandomFull != null && ok(r.wRandomFull[i]) && sumR > 0 ? Fmt.num(100 * r.wRandomFull[i] / sumR, 1) : "NA");
            if (wc) row.add(r.wCommonFull != null && ok(r.wCommonFull[i]) && sumC > 0 ? Fmt.num(100 * r.wCommonFull[i] / sumC, 1) : "NA");
            if (good && hasNull) { double zs = Math.abs(r.TE[i] - nullW) / r.seTE[i]; row.add(Fmt.num(Dist.pnorm(zs - zc) + Dist.pnorm(-zs - zc), 3)); } else row.add("NA");
            rows.add(row.toArray(new String[0])); }
        boolean[] right = new boolean[head.size()]; Arrays.fill(right, true); right[1] = false;
        R.studyRendered = Report.table(head, rows, right);   // Report.table also records the table for the Excel / Word export

        // ------------------------------------------------------------------ part B: 4. Prediction region, 5. Interpretation
        StringBuilder b = new StringBuilder();
        b.append("4. Prediction region\n\n");
        b.append("Prediction region shown: ").append(pred ? "yes" : "no").append("\n");
        double w95 = Double.NaN;
        if (pred) {
            b.append("Prediction interval (").append(pct(s.levelPredict)).append("%, random-effects model): ").append(ci(v.applyAsDouble(P.loPred), v.applyAsDouble(P.upPred))).append("\n");
            if (hasNull) b.append("Null effect within interval: ").append(nullW >= P.loPred && nullW <= P.upPred ? "yes" : "no").append("\n");
            b.append("This is a prediction interval for the effect in a future study; it is not a confidence interval for the pooled effect (").append(cl).append(" ").append(ci(v.applyAsDouble(P.loRandom), v.applyAsDouble(P.upRandom))).append(").\n");
            w95 = (P.upPred - P.loPred) / (P.upRandom - P.loRandom);
        } else b.append("Not displayed because ").append(sh.predReason).append("; no prediction-interval statements are made.\n");
        b.append("\n5. Interpretation\n\n");
        b.append("The drapery plot displays how statistical evidence changes across hypothetical effect values. The pooled curve summarizes the selected meta-analysis model, while the individual curves show study-level evidence. The prediction region, when displayed, describes uncertainty about the effect in a future study under the fitted model.\n\n");
        b.append("Notes:\n- A drapery plot is not a test for publication bias. It describes uncertainty and statistical evidence across possible effect values; it does not establish whether publication bias exists.\n");
        b.append("- The report follows the model(s) displayed in the plot: random-effects and common-effect results are never mixed, and a prediction interval is not a confidence interval for the pooled effect.\n");
        R.partB = b.toString();

        // ------------------------------------------------------------------ narrative ("Drapery Plot Analysis")
        String effPhrase = effectName(s).replaceAll(" \\(.*\\)$", "").toLowerCase(Locale.ROOT); if (effPhrase.startsWith("fisher")) effPhrase = "Fisher's z transformed correlation";
        String modelsTxt = (both ? "common-effect and random-effects meta-analyses of " + k + " studies (interpreted separately below)" : (rnd ? "random-effects" : "common-effect") + " meta-analysis of " + k + " studies");
        R.narrative.add("Purpose and model. A drapery plot was used to examine how statistical evidence for the pooled effect and for the individual studies varies across a range of hypothetical effect sizes. The analysis was based on " + (both ? "" : "a ") + modelsTxt
            + ", using the " + effPhrase + " as the effect measure. " + (zType ? "The curves show test statistics (z-values) against hypothetical effect values, with the confidence level on the right-hand axis." : "The curves show p-values against hypothetical effect values.")
            + (rel ? " The horizontal axis is logarithmic and the estimates are back-transformed." : analysisScale(s) ? " The estimates are shown on the analysis scale." : ""));
        StringBuilder p2 = new StringBuilder("Pooled effect and statistical significance. ");
        if (rnd) p2.append(poolNarr(r, P, s, true, both, eff, cl, nullW, hasNull, zc, v));
        if (com && rnd) p2.append(" "); if (com) p2.append(poolNarr(r, P, s, false, both, eff, cl, nullW, hasNull, zc, v));
        R.narrative.add(p2.toString().replaceAll(" +$", "").replace("  ", " "));
        StringBuilder p3 = new StringBuilder(); boolean hetOk = P.k >= 2 && ok(P.I2);
        if (rnd && ok(P.tau2) && !P.lrp) { p3.append("Between-study variability and prediction. Between-study heterogeneity was estimated as tau^2 = ").append(n4(P.tau2)).append(" (").append(Report.tauLabel(s.methodTau)).append(")");
            if (hetOk) p3.append(", with I^2 = ").append(Fmt.num(100 * P.I2, 1)).append("%").append(ok(P.loI2) && ok(P.upI2) ? " (" + pct(s.levelHetstat) + "% CI " + Fmt.num(100 * P.loI2, 1) + "% to " + Fmt.num(100 * P.upI2, 1) + "%)" : ""); p3.append(". "); }
        else if (hetOk) { p3.append("Between-study variability. ").append(rnd ? "Between-study variance was not reported for this pooling method; " : "The common-effect model assumes one true effect and has no between-study variance; ").append("the Q-based heterogeneity statistic was I^2 = ").append(Fmt.num(100 * P.I2, 1)).append("% (").append(pEq(P.pQ)).append(" for Q). "); }
        if (pred) { boolean in = hasNull && nullW >= P.loPred && nullW <= P.upPred;
            if (p3.length() == 0) p3.append("Prediction. ");
            p3.append("The prediction region corresponds to a ").append(pct(s.levelPredict)).append("% prediction interval of ").append(n4(v.applyAsDouble(P.loPred))).append(" to ").append(n4(v.applyAsDouble(P.upPred))).append(" for the effect in a future study");
            if (ok(w95) && w95 > 0) p3.append(", which is about ").append(Fmt.num(w95, 1)).append(" times as wide as the ").append(cl).append(" of the pooled effect (on the analysis scale)");
            p3.append(". ");
            if (hasNull) p3.append(in ? "The prediction interval includes the null effect (" + eff + " = " + nv(v.applyAsDouble(nullW)) + "), so under the fitted model the effect in a future study may be compatible with no effect. "
                                       : "The prediction interval does not include the null effect (" + eff + " = " + nv(v.applyAsDouble(nullW)) + "): under the fitted model the effect in a future study is expected to lie on the same side of the null as the pooled estimate. ");
            p3.append("This describes uncertainty about the effect in a future study, subject to the assumptions of the fitted model, and is distinct from the confidence interval of the pooled effect."); }
        if (p3.length() > 0) R.narrative.add(p3.toString().trim());
        StringBuilder p4 = new StringBuilder("Overall interpretation. The drapery plot provides a complementary representation of statistical uncertainty, showing how evidence changes across possible effect sizes. The pooled curve should be interpreted alongside the conventional confidence interval and p-value, while the individual-study curves illustrate study-level uncertainty");
        if (hasNull) p4.append(": at the null effect, ").append(sigStudies).append(" of ").append(k).append(sigStudies == 1 ? " study curve lies" : " study curves lie").append(" below the 5% threshold (p < 0.05)");
        p4.append(". ");
        if (both) p4.append("The common-effect and random-effects curves answer different questions (a single true effect versus a distribution of true effects) and are interpreted separately; differences between them should not be read as evidence of publication bias. ");
        p4.append("The plot does not, by itself, establish the presence or absence of publication bias.");
        R.narrative.add(p4.toString());
        return R;
    }

    /** Pooled-effect block of the statistical report for one model. */
    private static String pooled(MetaResult r, Pool P, MetaSpec s, boolean random, boolean both, String eff, String cl, double nullW, boolean hasNull, double zc, java.util.function.DoubleUnaryOperator v) {
        StringBuilder b = new StringBuilder("\n"); double te = random ? P.teRandom : P.teCommon, se = random ? P.seRandom : P.seCommon, lo = random ? P.loRandom : P.loCommon, up = random ? P.upRandom : P.upCommon, p = random ? P.pRandom : P.pCommon;
        if (both) b.append(random ? "Random-effects model" : "Common-effect model").append("\n");
        b.append("Pooled ").append(eff).append(": ").append(n4(v.applyAsDouble(te))).append("\n");
        b.append(cl).append(": ").append(ci(v.applyAsDouble(lo), v.applyAsDouble(up))).append("\n");
        boolean tt = random && "t".equals(P.testType) && ok(P.dfRandom);
        if (hasNull) {
            b.append("P-value: ").append(Fmt.pval(p, 4)).append(tt ? " (t-test, d.f. = " + Fmt.num(P.dfRandom, 1) + ")" : " (z-test)").append("\n");
            b.append("Statistical significance at alpha = 0.05: ").append(ok(p) ? (p < ALPHA ? "yes" : "no") : "not available").append("\n");
            double pc = Drapery.curve(false, nullW, te, se);
            if (!Fmt.pval(pc, 4).equals(Fmt.pval(p, 4))) b.append("P-value of the plotted curve at the null effect (normal approximation): ").append(Fmt.pval(pc, 4)).append("\n");
        } else b.append("P-value: not available (no null effect defined for this measure)\n");
        double cLo = v.applyAsDouble(te - zc * se), cUp = v.applyAsDouble(te + zc * se);
        if (!ci(cLo, cUp).equals(ci(v.applyAsDouble(lo), v.applyAsDouble(up))) || Math.abs(s.levelMa - 0.95) > 1e-9)
            b.append("Hypothetical effects with p >= 0.05 on the plotted curve: ").append(ci(cLo, cUp)).append("\n");
        return b.toString();
    }

    private static String rng0(double te, double se, double zc, java.util.function.DoubleUnaryOperator v) { return ci(v.applyAsDouble(te - zc * se), v.applyAsDouble(te + zc * se)); }

    /** Narrative sentences for the pooled effect of one model, conditional on the plotted curve and on the reported test. */
    private static String poolNarr(MetaResult r, Pool P, MetaSpec s, boolean random, boolean both, String eff, String cl, double nullW, boolean hasNull, double zc, java.util.function.DoubleUnaryOperator v) {
        double te = random ? P.teRandom : P.teCommon, se = random ? P.seRandom : P.seCommon, lo = random ? P.loRandom : P.loCommon, up = random ? P.upRandom : P.upCommon, p = random ? P.pRandom : P.pCommon;
        boolean differs = !rng0(te, se, zc, v).equals(ci(v.applyAsDouble(lo), v.applyAsDouble(up)));
        String who = both ? (random ? "In the random-effects model" : "In the common-effect model") + ", the" : "The", rng = ci(v.applyAsDouble(te - zc * se), v.applyAsDouble(te + zc * se)).replace(", ", " to ");
        StringBuilder b = new StringBuilder(who).append(" pooled effect estimate was ").append(n4(v.applyAsDouble(te))).append(" (").append(cl).append(" ").append(n4(v.applyAsDouble(lo))).append(" to ").append(n4(v.applyAsDouble(up)));
        if (hasNull) b.append("; ").append(pEq(p)); b.append("). ");
        if (!hasNull) return b.append("No null effect is defined for this measure, so no statement about statistical significance is made; at the 5% level, hypothetical effects from ").append(rng.replace("[", "").replace("]", "")).append(" are compatible with the data (pooled curve on or above the 5% line). ").toString();
        double pc = Drapery.curve(false, nullW, te, se); boolean sigCurve = pc < ALPHA, sigModel = ok(p) ? p < ALPHA : sigCurve; String nul = eff + " = " + nv(v.applyAsDouble(nullW));
        b.append("The drapery plot illustrates how the statistical significance of the pooled effect changes across hypothetical values of the underlying effect. At the conventional 5% significance level, ");
        if (sigCurve) b.append("the pooled curve lies below the 5% line at the null effect (").append(nul).append("); the null value is outside the range of hypothetical effects from ").append(rng.replace("[", "").replace("]", "")).append(differs ? " (normal approximation of the plotted curve)" : "").append(", which is where the curve stays on or above the 5% line, so the pooled effect was statistically significant at that level. ");
        else b.append("the pooled curve lies on or above the 5% line at the null effect (").append(nul).append("); the null value is within the range of hypothetical effects from ").append(rng.replace("[", "").replace("]", "")).append(differs ? " (normal approximation of the plotted curve)" : "").append(" that remain compatible with the data, so the pooled effect was not statistically significant at that level. ");
        if (sigCurve != sigModel) b.append("Note that the reported p-value uses the t distribution").append(random && ok(P.dfRandom) ? " (d.f. = " + Fmt.num(P.dfRandom, 1) + ")" : "").append(", whereas the plotted curve uses the normal approximation (p = ").append(Fmt.pval(pc, 4)).append(" at the null); the two lead to different conclusions at the 5% level. ");
        return b.toString();
    }
}
