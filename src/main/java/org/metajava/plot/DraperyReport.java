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
        Drapery.Shown sh = Drapery.shown(r, o); boolean rnd = sh.random || sh.randomSe0, com = sh.common, both = rnd && com, pred = sh.pred;   // sh.randomSe0: the random-effects result is reported although its pooled curve cannot be drawn (SE = 0)
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
        if (Math.abs(s.levelMa - s.level) < 1e-12) a.append("Confidence level for pooled and study-level intervals: ").append(pct(s.levelMa)).append("%\n");
        else a.append("Confidence level for pooled intervals: ").append(pct(s.levelMa)).append("%\nConfidence level for study-level intervals: ").append(pct(s.level)).append("%\n");
        if (pred) a.append("Prediction interval level: ").append(pct(s.levelPredict)).append("%\n");
        a.append("Confidence intervals for heterogeneity parameters: ").append(pct(s.levelHetstat)).append("%\n");
        a.append("Significance threshold: alpha = 0.05\n");
        a.append("Studies in the data: ").append(r.kAll > 0 ? r.kAll : r.TE.length).append(" (used in the pooled analysis: ").append(k).append(")\n");
        a.append("Display: ").append(zType ? "Test-statistic curves, plotted as the negative absolute z-value -|z| (scaled p-value functions that peak at 0 at the estimate); the right-hand axis gives the confidence level" : "P-value functions").append("\n");
        a.append("Null effect: ").append(hasNull ? eff + " = " + nv(v.applyAsDouble(nullW)) : "not defined for this measure (no threshold statements are made)").append("\n");
        final double thetaPooled = rnd ? P.teRandom : P.teCommon;
        final String tgtTxt = o.drapTarget == null ? "" : o.drapTarget.trim(); double tgtW = Double.NaN;
        if (!tgtTxt.isEmpty()) { double e = Plots.parseAxis(tgtTxt);
            if (rel) tgtW = e > 0 ? Math.log(e) : Double.NaN; else if (!Double.isNaN(e)) { MetaSpec c = s.clone(); c.nullEffect = e; tgtW = Meta.nullWorking(c, P.nHarm); } }
        final double targetW = tgtW; final boolean hasTarget = ok(targetW);
        if (!tgtTxt.isEmpty()) a.append("Target effect (used only for the study-level power): ").append(hasTarget ? eff + " = " + tgtTxt : tgtTxt + " (cannot be used with this effect measure: outside the valid range)").append("\n");
        a.append("Scale of the estimates: ").append(rel ? "back-transformed (the x-axis of the plot is logarithmic)" : analysisScale(s) ? "analysis scale, as plotted (not back-transformed): " + Plots.xlabFor(s) : "as analysed").append("\n");
        a.append("\n2. Pooled effect\n");
        if (rnd) a.append(pooled(r, P, s, true, both, eff, cl, nullW, hasNull, zc, v));
        if (com) a.append(pooled(r, P, s, false, both, eff, cl, nullW, hasNull, zc, v));
        R.partA = a.toString();

        // ------------------------------------------------------------------ 3. Individual studies (table; expandable in the window)
        R.studyTitle = "3. Individual studies";
        R.studyNote = "Study-level estimates and " + pct(s.level) + "% confidence intervals. P-value (null): the value of the study's own p-value curve at the null effect (normal approximation, as plotted)" + (hasNull ? "." : "; not available because the measure has no null effect.")
            + " Observed power: power of the two-sided z-test at alpha = 0.05 if the true effect equalled the study's observed estimate (post hoc; it is a direct function of the study's p-value and adds no independent information). Power (pooled effect) and Power (target effect): power of the same two-sided z-test (alpha = 0.05, the study's own standard error) against the null effect if the true effect equalled the " + (rnd ? "random-effects" : "common-effect") + " pooled estimate, or the target effect set under \"Drapery plot\"; NA without a null effect" + (hasTarget ? "." : ", and Power (target effect) is NA because no valid target effect is set.");
        List<String> head = new ArrayList<>(List.of("#", "Study", eff, pct(s.level) + "% CI", "p (null)")); boolean wr = !s.noWeights() && rnd, wc = !s.noWeights() && com;
        if (wr) head.add("%W(random)"); if (wc) head.add("%W(common)"); head.add("Observed power"); head.add("Power (pooled effect)"); head.add("Power (target effect)");
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
            for (double th : new double[]{thetaPooled, targetW}) { if (good && hasNull && ok(th)) { double zs = Math.abs(th - nullW) / r.seTE[i]; row.add(Fmt.num(Dist.pnorm(zs - zc) + Dist.pnorm(-zs - zc), 3)); } else row.add("NA"); }
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
            b.append("Method: ").append(predMethod(s, P)).append("\n");
            b.append("Critical value: ").append(predCritical(P)).append("\n");
            b.append("This is a prediction interval for the effect in a future study; it is not a confidence interval for the pooled effect (").append(cl).append(" ").append(ci(v.applyAsDouble(P.loRandom), v.applyAsDouble(P.upRandom))).append(").\n");
            w95 = (P.upPred - P.loPred) / (P.upRandom - P.loRandom);
        } else b.append("Not displayed because ").append(sh.predReason).append("; no prediction-interval statements are made.\n");
        b.append("\n5. Plotted statistic and inferential methods\n\n");
        b.append(plottedStatistic(s, P, rel, zType, pred, rnd, com, both)).append("\n");
        b.append(inference(r, s, P, rnd, com, both, eff, v));
        b.append(heterogeneity(s, P, rnd, k));
        if (pred) b.append(predictionDetail(s, P, v, hasNull ? nullW : Double.NaN));
        b.append("\n6. Data and group coding\n\n");
        b.append(dataCoding(r, s, eff));
        b.append("\n7. Interpretation and caveats\n\n");
        b.append("The drapery plot displays how statistical evidence changes across hypothetical effect values. The pooled curve summarizes the selected meta-analysis model, while the individual curves show study-level evidence. The prediction region, when displayed, describes uncertainty about the effect in a future study under the fitted model; the prediction curve is a graphical calibration to that interval, not a predictive distribution.\n\n");
        b.append("Notes:\n- A drapery plot is not a test for publication bias. It describes uncertainty and statistical evidence across possible effect values; it does not establish whether publication bias exists.\n");
        b.append("- The report follows the model(s) displayed in the plot: random-effects and common-effect results are never mixed, and a prediction interval is not a confidence interval for the pooled effect.\n");
        b.append("- The plotted curves use normal-theory (Wald-type) p-value functions based on the estimated study effects and their standard errors. The calculations treat the estimated study-level variances and the estimated between-study variance as fixed. The random-effects model additionally assumes that the true study effects follow a normal distribution around the pooled mean. p(theta) is the p-value of the test of the hypothetical effect theta, not the probability that theta is the true effect.\n");
        b.append("- Where the reported test uses a t distribution (Hartung-Knapp, Kenward-Roger), the reported CI and p-value differ from the points where the plotted normal-theory curve crosses the 5% line; the reported values are the inferential result.\n");
        if (k < 10) b.append("- Only k = ").append(k).append(" studies contribute: tau^2, I^2 and the prediction interval are imprecisely estimated, and small-sample adjustments (Hartung-Knapp, Kenward-Roger) are advisable.\n");
        Prec pg0 = hasNull ? precision(r, k) : null;
        if (pg0 != null) { StringBuilder hi = new StringBuilder(), lo = new StringBuilder(); for (int i : pg0.hi) hi.append(hi.length() > 0 ? ", " : "").append(r.studlab[i]); for (int i : pg0.lo) lo.append(lo.length() > 0 ? ", " : "").append(r.studlab[i]);
            b.append("- Precision gradient (descriptive, not a test): precision = 1/SE_i on the analysis scale; studies ranked by it (ties in input order); m = floor(k / 3) = ").append(pg0.m).append(" studies at each extreme. Most precise: ").append(hi).append(". Least precise: ").append(lo).append(". Medians of the observed estimates: ")
                .append(n4(v.applyAsDouble(pg0.medHi))).append(" (most precise) and ").append(n4(v.applyAsDouble(pg0.medLo))).append(" (least precise). Ranking by 1/SE is equivalent to ranking by confidence-curve width at any confidence level under the normal approximation.\n"); }
        if (hasNull) b.append("- Observed power of a study is a one-to-one function of its p-value; it should not be used to interpret a non-significant result.\n");
        if (!r.warnings.isEmpty()) { b.append("\nWarnings issued by the analysis:\n"); for (String w : r.warnings) b.append("- ").append(w).append("\n"); }
        R.partB = b.toString();

        // ------------------------------------------------------------------ narrative ("Drapery Plot Analysis")
        String effPhrase = effectName(s).replaceAll(" \\(.*\\)$", "").toLowerCase(Locale.ROOT); if (effPhrase.startsWith("fisher")) effPhrase = "Fisher's z transformed correlation";
        String modelsTxt = (both ? "common-effect and random-effects meta-analyses of " + k + " studies (interpreted separately below)" : (rnd ? "random-effects" : "common-effect") + " meta-analysis of " + k + " studies");
        R.narrative.add("Purpose and model. A drapery plot was used to examine how statistical evidence for the pooled effect and for the individual studies varies across a range of hypothetical effect sizes. The analysis was based on " + (both ? "" : "a ") + modelsTxt
            + ", using the " + effPhrase + " as the effect measure. " + (zType ? "The curves show the negative absolute test statistic (-|z|, a scaled p-value function that peaks at zero at the estimate) against hypothetical effect values, with the confidence level on the right-hand axis." : "The curves show p-values against hypothetical effect values.")
            + (rel ? " The horizontal axis is logarithmic and the estimates are back-transformed." : analysisScale(s) ? " The estimates are shown on the analysis scale." : ""));
        R.narrative.add(methodsNarrative(r, s, P, rnd, com, both, pred, rel, zType, k));
        StringBuilder p2 = new StringBuilder("Pooled effect and statistical significance. ");
        if (rnd) p2.append(poolNarr(r, P, s, true, both, eff, cl, nullW, hasNull, zc, v));
        if (com && rnd) p2.append(" "); if (com) p2.append(poolNarr(r, P, s, false, both, eff, cl, nullW, hasNull, zc, v));
        R.narrative.add(p2.toString().replaceAll(" +$", "").replace("  ", " "));
        StringBuilder p3 = new StringBuilder(); boolean hetOk = P.k >= 2 && ok(P.I2);
        if (rnd && ok(P.tau2) && !P.lrp) { p3.append("Between-study variability and prediction. Between-study heterogeneity was estimated as tau^2 = ").append(n4(P.tau2)).append(" (").append(Report.tauLabel(s.methodTau)).append(")");
            if (hetOk) p3.append(", with I^2 = ").append(Fmt.num(100 * P.I2, 1)).append("%").append(ok(P.loI2) && ok(P.upI2) ? " (" + pct(s.levelHetstat) + "% CI " + Fmt.num(100 * P.loI2, 1) + "% to " + Fmt.num(100 * P.upI2, 1) + "%)" : ""); p3.append(". ");
            if (hetOk && ok(P.Q) && P.dfQ > 0) p3.append(P.pQ < ALPHA ? "Cochran's Q test rejected the common-effect homogeneity hypothesis" : "Cochran's Q test did not reject the common-effect homogeneity hypothesis").append(" (Q = ").append(Fmt.num(P.Q, 2)).append(", d.f. = ").append(P.dfQ).append(", ").append(pEq(P.pQ)).append(P.pQ < ALPHA
                ? "), indicating evidence of between-study variability beyond that expected from sampling error alone under the test assumptions; the test does not establish the cause of the heterogeneity. " : "), which does not establish that the studies share one true effect, as the test has limited power when studies are few or small. ");
            if (hetOk && ok(P.loI2) && ok(P.upI2) && P.upI2 - P.loI2 >= 0.30) p3.append("The confidence interval for I^2").append(ok(P.loTau2) && ok(P.upTau2) ? " and that for tau^2 (" + n4(P.loTau2) + " to " + n4(P.upTau2) + ")" : "").append(ok(P.loTau2) && ok(P.upTau2) ? " are" : " is").append(" wide, so the amount of heterogeneity is estimated imprecisely. "); }
        else if (hetOk) { p3.append("Between-study variability. ").append(rnd ? "Between-study variance was not reported for this pooling method; " : "The common-effect model assumes one true effect and has no between-study variance; ").append("the Q-based heterogeneity statistic was I^2 = ").append(Fmt.num(100 * P.I2, 1)).append("% (").append(pEq(P.pQ)).append(" for Q). "); }
        if (pred) { boolean in = hasNull && nullW >= P.loPred && nullW <= P.upPred;
            if (p3.length() == 0) p3.append("Prediction. ");
            p3.append("The prediction region corresponds to a ").append(pct(s.levelPredict)).append("% prediction interval of ").append(n4(v.applyAsDouble(P.loPred))).append(" to ").append(n4(v.applyAsDouble(P.upPred))).append(" for the effect in a future study");
            if (ok(w95) && w95 > 0) p3.append(", which is about ").append(Fmt.num(w95, 1)).append(" times as wide as the ").append(cl).append(" of the pooled effect (on the analysis scale)");
            p3.append(". ");
            String[] sd = sides(s.sm);
            if (hasNull) p3.append(in ? "The prediction interval includes the null effect (" + eff + " = " + nv(v.applyAsDouble(nullW)) + "), so under the fitted model the effect in a future study may be compatible with no effect"
                                          + (sd != null && P.loPred < nullW && P.upPred > nullW ? ": it could range from " + sd[0] + " (" + eff + " = " + n4(v.applyAsDouble(P.loPred)) + ") to " + sd[1] + " (" + eff + " = " + n4(v.applyAsDouble(P.upPred)) + ")" + GROUPS : "") + ". "
                                       : "The prediction interval does not include the null effect (" + eff + " = " + nv(v.applyAsDouble(nullW)) + "): under the fitted model the effect in a future study is expected to lie on the same side of the null as the pooled estimate. ");
            p3.append("The prediction interval depends on the estimated between-study variance, and uncertainty in that estimate affects the reliability of the interval. The reported interval uses the ").append(P.glmm ? "GLMM" : Report.tauLabel(s.methodTau)).append(" point estimate of tau^2 and does not explicitly propagate its estimation uncertainty. Consequently, the reported interval should be interpreted in light of both the fitted model assumptions and the limitations of this plug-in prediction-interval method. ");
            p3.append("This describes uncertainty about the effect in a future study, subject to the assumptions of the fitted model, and is distinct from the confidence interval of the pooled effect. The prediction curve drawn on the plot is only a graphical calibration to this interval, not a predictive distribution."); }
        if (p3.length() > 0) R.narrative.add(p3.toString().trim());
        StringBuilder p4 = new StringBuilder("Overall interpretation. The drapery plot shows how statistical evidence changes across possible effect sizes; the pooled curve should be read alongside the conventional confidence interval and p-value, and the individual-study curves illustrate study-level uncertainty");
        if (hasNull) p4.append(": at the null effect, ").append(sigStudies).append(" of ").append(k).append(sigStudies == 1 ? " study curve lies" : " study curves lie").append(" below the 5% threshold (p < 0.05)");
        p4.append(". ");
        if (hasNull) {
            int above = 0, below = 0; List<Integer> ord = new ArrayList<>();
            for (int i = 0; i < r.TE.length; i++) if (ok(r.TE[i]) && ok(r.seTE[i]) && r.seTE[i] > 0) { ord.add(i); if (r.TE[i] > nullW) above++; else if (r.TE[i] < nullW) below++; }
            int rest = k - sigStudies;
            if (rest > 0) p4.append(sigStudies == 0 ? "None of the study curves rejects the null at the 5% level" : rest == 1 ? "The remaining study curve does not reject the null at the 5% level" : "The remaining " + rest + " study curves do not reject the null at the 5% level")
                .append("; this reflects the limited precision of individual studies and should not be read as evidence of no effect. ");
            if (above > 0 && below > 0) p4.append("Study point estimates lie on both sides of the null (").append(above).append(" above, ").append(below).append(" below), so the direction of effect is not uniform across studies. ");
            Prec pg = precision(r, k);
            if (pg != null) {
                double pooledTe = rnd ? P.teRandom : P.teCommon;
                p4.append("Precision was defined as 1/SE_i, the inverse of the study-level standard error on the analysis scale").append(rel ? " (log scale)" : "").append("; studies were ranked by this value (ties kept in input order), and the ").append(pg.m).append(" (= floor(k / 3)) studies at each extreme were selected before the medians of their observed estimates were calculated. The ").append(pg.m).append(" most precise studies (the steepest and narrowest curves) have a median estimate of ")
                    .append(n4(v.applyAsDouble(pg.medHi))).append(", compared with ").append(n4(v.applyAsDouble(pg.medLo))).append(" for the ").append(pg.m).append(" least precise studies. ");
                if (Math.signum(pg.medLo - nullW) == Math.signum(pooledTe - nullW) && Math.abs(pg.medLo - nullW) > Math.abs(pg.medHi - nullW))
                    p4.append("Larger estimates among the less precise studies would be compatible with small-study effects, but this is a descriptive observation, not a test; it requires a separate assessment (for example a funnel plot with an asymmetry test). ");
            }
        }
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
        boolean tt = random && "t".equals(P.testType) && ok(P.dfRandom), noCurve = random && ok(se) && se == 0;
        if (noCurve) b.append("Pooled curve: not drawn. The standard error of the random-effects result is 0").append(P.hkPrimary ? " (the Hartung-Knapp variance factor q is 0: the weighted study estimates are identical)" : "").append(", so the normal-theory curve z(theta) = -|y - theta| / SE is undefined and would collapse to a vertical line at the estimate. The random-effects pooled curve and the prediction region are omitted from the plot; the study curves are unaffected.\n");
        if (noCurve && hasNull) {
            b.append("P-value: ").append(ok(p) ? "0" : "undefined (0/0)").append(tt ? " (t-test, d.f. = " + Fmt.num(P.dfRandom, 1) + ")" : " (z-test)").append("; degenerate: SE = 0 makes the test statistic ").append(ok(p) ? "infinite, so the p-value is 0 only as a numerical limit (as ci() of meta computes it)" : "0/0 because the estimate equals the null effect, so the p-value is undefined (NA in meta)").append(" and cannot be interpreted as evidence\n");
            b.append("Statistical significance at alpha = 0.05: not assessable (degenerate result, SE = 0)\n");
        } else if (hasNull) {
            b.append("P-value: ").append(Fmt.pval(p, 4)).append(tt ? " (t-test, d.f. = " + Fmt.num(P.dfRandom, 1) + ")" : " (z-test)").append("\n");
            b.append("Statistical significance at alpha = 0.05: ").append(ok(p) ? (p < ALPHA ? "yes" : "no") : "not available").append("\n");
            double pc = Drapery.curve(false, nullW, te, se);
            if (!Fmt.pval(pc, 4).equals(Fmt.pval(p, 4))) b.append("P-value of the plotted curve at the null effect (normal approximation): ").append(Fmt.pval(pc, 4)).append("\n");
        } else b.append("P-value: not available (no null effect defined for this measure)\n");
        double cLo = v.applyAsDouble(te - zc * se), cUp = v.applyAsDouble(te + zc * se);
        if (!noCurve && (!ci(cLo, cUp).equals(ci(v.applyAsDouble(lo), v.applyAsDouble(up))) || Math.abs(s.levelMa - 0.95) > 1e-9))
            b.append("Hypothetical effects with p >= 0.05 on the plotted curve: ").append(ci(cLo, cUp)).append("\n");
        Hk hk = random && hasNull ? hkSens(P, s, nullW) : null;
        if (hk != null) {
            boolean sigMain = noCurve ? false : ok(p) ? p < ALPHA : Drapery.curve(false, nullW, te, se) < ALPHA;
            if (hk.primary) b.append(hk.primaryIdx == 0
                ? "\nComparison of Hartung-Knapp variants. The reported result of the pooled curve already is the conventional Hartung-Knapp adjustment (no ad hoc correction, section 5.2), so this is not a sensitivity analysis against the classic Wald z test; "
                + (hk.reportedDegenerate() ? "the ad hoc variants below are listed as alternatives (see the note on unavailable comparisons).\n" : "the ad hoc variants below only show how much the conclusion depends on the choice of ad hoc rule.\n")
                : "\nComparison of Hartung-Knapp variants. The reported result of the pooled curve already is the Hartung-Knapp method with the ad hoc correction '" + hk.vars.get(hk.primaryIdx).name + "' (section 5.2), so this is not a sensitivity analysis against the classic Wald z test; the variants below are alternative Hartung-Knapp specifications that only show how much the conclusion depends on the choice of ad hoc rule.\n");
            else if (hk.kr) b.append("\nSensitivity analysis: Hartung-Knapp variants. The reported result of the pooled curve uses the Kenward-Roger adjustment (section 5.2); the conventional Hartung-Knapp adjustment and its ad hoc variants below only check the robustness of the conclusion.\n");
            else b.append("\nSensitivity analysis: Hartung-Knapp adjustment. It is not the inferential method of the pooled curve (classic Wald z test, section 5.2); it only checks the robustness of the conclusion.\n");
            b.append("  Variance factor q = sum w*_i (y_i - mu)^2 / (k - 1) = ").append(n4(hk.q)).append("; classic SE (analysis scale) = ").append(n4(hk.seClassic)).append("; conventional SE_HK = sqrt(q / sum w*_i) = ").append(n4(hk.conv().se)).append("\n");
            if (hk.reportedDegenerate()) b.append("  Comparisons with the reported result are UNAVAILABLE: it is degenerate (SE = 0, zero-width interval, no usable p-value), so it has no conclusion to compare with. The variants below are alternative calculations, each reported on its own; they were not compared with the reported result, and nothing here implies that they agree or differ.\n");
            for (int j = 0; j < hk.vars.size(); j++) { HkVar x = hk.vars.get(j);
                b.append("  Ad hoc correction: ").append(x.name).append(hk.primary && j == hk.primaryIdx ? " [reported result]" : hk.reportedDegenerate() ? " [alternative, not the reported result]" : "").append(" - ").append(x.rule).append("\n    Rule check: ").append(x.check).append(hk.reportedDegenerate() && j != hk.primaryIdx && x.applied ? " (this rule applies to this alternative only; it does not change the reported result)" : "").append("\n    SE = ").append(n4(x.se)).append("; ").append(x.t ? "t distribution, " + Fmt.num(x.df, 0) + " d.f." : "standard normal distribution").append("; ").append(cl).append(" ")
                    .append(ci(v.applyAsDouble(x.lo), v.applyAsDouble(x.up)));
                if (x.degenerate) b.append("; degenerate: SE_HK = 0 gives a zero-width interval; P-value: ").append(ok(x.p) ? "0 only as a numerical limit (infinite test statistic, as ci() of meta computes it)" : "undefined (0/0: the estimate equals the null effect)").append(", not interpretable; conclusion at alpha = 0.05 not assessable\n");
                else b.append("; P-value: ").append(Fmt.pval(x.p, 4)).append("; ").append(hk.reportedDegenerate() ? NO_COMPARISON : "conclusion at alpha = 0.05 " + ((x.p < ALPHA) == sigMain ? "unchanged" : "changed")).append("\n"); }
            b.append("\n").append(hk.primary ? "Summary of the comparison of Hartung-Knapp variants" : "Summary of the sensitivity analysis").append(" (analysis of the reported random-effects result):\n").append(hkTable(hk, P, p, te, se, lo, up, sigMain, cl, v)).append("\n")
                .append(hk.reportedDegenerate() ? "Conclusion vs reported: Unavailable = the reported result is degenerate (SE = 0), so no comparison was made; the rows other than the reported one are alternatives shown with their own results, and 'Ad hoc rule triggered' refers to each alternative's own rule.\n" : "");
        }
        return b.toString();
    }

    /** Phrases for the two sides of the null ("a lower risk", "a higher risk" ...), or null when the measure has no stated direction. */
    private static String[] sides(String sm) {
        if (Drapery.relative(sm)) { String n = sm.equals("HR") ? "hazard" : sm.equals("OR") || sm.equals("DOR") ? "odds" : sm.equals("RR") ? "risk" : sm.equals("IRR") ? "incidence rate" : "mean";
            return new String[]{"a lower " + n, "a higher " + n}; }
        if (Arrays.asList("RD", "MD", "SMD", "ASD", "IRD").contains(sm)) return new String[]{"a smaller value", "a larger value"};
        return null;
    }
    private static final String GROUPS = " in the experimental group than in the control group";

    /** One Hartung-Knapp variant: SE on the analysis scale, reference distribution (d.f. NaN = standard normal), CI and two-sided p-value for the null. */
    private static final class HkVar { boolean degenerate;
        String name, rule, check; double se, df, lo, up, p; boolean t, applied;
        boolean same(HkVar o) { return Fmt.num(lo, 4).equals(Fmt.num(o.lo, 4)) && Fmt.num(up, 4).equals(Fmt.num(o.up, 4)) && Fmt.pval(p, 4).equals(Fmt.pval(o.p, 4)); }
    }
    private static final class Hk { double q, seClassic; boolean primary, kr; int primaryIdx = -1; List<HkVar> vars = new ArrayList<>(); HkVar conv() { return vars.get(0); }
        /** The reported result is itself degenerate (plain Hartung-Knapp with q = 0): it cannot serve as the reference for "unchanged / changed". */
        boolean reportedDegenerate() { return primary && primaryIdx >= 0 && vars.get(primaryIdx).degenerate; } }

    /** Statement used wherever a comparison with a degenerate reported result is not possible: unavailable, never "equivalent". */
    private static final String NO_COMPARISON = "comparison with the reported result unavailable (the reported result is degenerate, SE = 0)";

    /** The random-effects result has a standard error of exactly 0 (Hartung-Knapp with q = 0): the pooled curve cannot be drawn. */
    private static boolean zeroSe(Pool P) { return ok(P.teRandom) && ok(P.seRandom) && P.seRandom == 0; }

    /**
     * Summary table of the Hartung-Knapp block (also recorded for the Excel / Word export): the reported result (classic Wald z or Kenward-Roger; when the reported result is
     * itself a Hartung-Knapp variant it is marked in the list of variants) and the four Hartung-Knapp variants, each with SE, reference distribution, CI, p-value, significance at
     * 0.05, whether its ad hoc rule was triggered and whether the 5% conclusion differs from the reported one.
     */
    private static String hkTable(Hk hk, Pool P, double pRep, double teRep, double seRep, double loRep, double upRep, boolean sigMain, String cl, java.util.function.DoubleUnaryOperator v) {
        List<String> head = new ArrayList<>(List.of("Method", "Distribution", "SE", cl, "p-value", "Significant at 0.05", "Ad hoc rule triggered", "Conclusion vs reported")); List<String[]> rows = new ArrayList<>();
        if (!hk.primary) {
            boolean t = "t".equals(P.testType) && ok(P.dfRandom);
            rows.add(new String[]{(hk.kr ? "Kenward-Roger, t" : "Classic Wald, z") + " [reported]", t ? "t (" + Fmt.num(P.dfRandom, Math.abs(P.dfRandom - Math.rint(P.dfRandom)) < 1e-9 ? 0 : 2) + " d.f.)" : "normal", n4(seRep), ci(v.applyAsDouble(loRep), v.applyAsDouble(upRep)),
                Fmt.pval(pRep, 4), ok(pRep) ? (pRep < ALPHA ? "Yes" : "No") : "NA", "-", "-"});
        }
        String[] lab = {"Conventional Hartung-Knapp", "Hartung-Knapp, ad hoc se", "Hartung-Knapp, ad hoc ci", "Hartung-Knapp, IQWiG 6"};
        for (int j = 0; j < hk.vars.size(); j++) { HkVar x = hk.vars.get(j); boolean rep = hk.primary && j == hk.primaryIdx;
            String dist = x.t ? "t (" + Fmt.num(x.df, 0) + " d.f.)" : "normal";
            if (x.degenerate) rows.add(new String[]{lab[j] + (rep ? " [reported]" : ""), dist, n4(x.se), ci(v.applyAsDouble(x.lo), v.applyAsDouble(x.up)), ok(x.p) ? "0 (degenerate)" : "undefined (0/0)", "NA", j == 0 ? "-" : x.applied ? "Yes" : "No", rep ? "-" : hk.reportedDegenerate() ? "Unavailable" : "NA"});
            else rows.add(new String[]{lab[j] + (rep ? " [reported]" : ""), dist, n4(x.se), ci(v.applyAsDouble(x.lo), v.applyAsDouble(x.up)), Fmt.pval(x.p, 4), x.p < ALPHA ? "Yes" : "No", j == 0 ? "-" : x.applied ? "Yes" : "No", rep ? "-" : hk.reportedDegenerate() ? "Unavailable" : (x.p < ALPHA) == sigMain ? "unchanged" : "changed"}); }
        boolean[] right = new boolean[head.size()]; Arrays.fill(right, true); right[0] = false; right[1] = false; right[6] = false; right[7] = false;
        return Report.table(head, rows, right);
    }

    /**
     * Hartung-Knapp block of the report: the conventional adjustment and each ad hoc variant (se, ci, IQWiG6), calculated exactly as meta::metagen does
     * (adhoc.hakn.ci = "", "se", "ci", "IQWiG6") and as {@link Pool} does when Hartung-Knapp is chosen as the reported method.
     * <ul>
     * <li>Reported result = classic Wald z test: the block is a sensitivity analysis of that result.</li>
     * <li>Reported result = Hartung-Knapp with an ad hoc correction (also when the "ci" rule has fallen back to the z distribution): the block is a comparison
     *     of alternative Hartung-Knapp variants, the reported one is marked, and the classic Wald z test is not mentioned as the reported method.</li>
     * <li>Reported result = plain Hartung-Knapp (no ad hoc correction): the block compares the reported conventional adjustment with the three ad hoc variants.</li>
     * <li>Reported result = Kenward-Roger: the block is a sensitivity analysis of that result with the four Hartung-Knapp variants.</li>
     * <li>Reported result = GLMM, LRP or sample-size weighting: no block. GLMM / LRP / SSW have no inverse-variance
     *     q statistic, and meta does not offer the ad hoc corrections for GLMMs.</li>
     * </ul>
     * Boundary cases follow meta, each rule being evaluated on its own: Hartung-Knapp needs k >= 2 studies (t distribution with k - 1 d.f., so k = 2 gives 1 d.f.);
     * with q = 0 (SE_HK = 0) the conventional interval has zero width and its p-value is 0 (undefined when the estimate equals the null), and the three ad hoc rules
     * are all triggered (q &lt; 1, and a zero-width interval is narrower than any classic interval), which gives the classic SE. The IQWiG6 comparison interval is the
     * DerSimonian-Laird interval of the studies that are pooled (studies with missing effect or SE are not pooled; a leave-one-out / subset analysis uses its own
     * studies). meta::metagen also keeps studies removed with its argument 'exclude' in that comparison interval; MetaJava has no 'exclude' option, so this case cannot occur.
     */
    private static Hk hkSens(Pool P, MetaSpec s, double nullW) {
        if (P.glmm || P.lrp || !ok(P.tau2) || P.k < 2 || P.idxTE == null || P.idxSE == null || "SSW".equals(s.method) || !ok(nullW)) return null;
        String ah = s.adhocHakn == null ? "" : s.adhocHakn; boolean prim = P.hkPrimary;   // explicit method flag set by Pool (never inferred from labels)
        boolean kr = !prim && P.krPrimary;   // reported result = Kenward-Roger (t): the Hartung-Knapp variants are a sensitivity analysis of it
        int k = P.k; double sw = 0, swy = 0; double[] w = new double[k], vv = new double[k];
        for (int i = 0; i < k; i++) { vv[i] = P.idxSE[i] * P.idxSE[i]; w[i] = 1 / (vv[i] + P.tau2); sw += w[i]; swy += w[i] * P.idxTE[i]; }
        double mu = swy / sw, q = 0; for (int i = 0; i < k; i++) q += w[i] * (P.idxTE[i] - mu) * (P.idxTE[i] - mu);
        q /= (k - 1); double seHK = Math.sqrt(q / sw), seCl = 1 / Math.sqrt(sw), df = k - 1, alpha = 1 - s.levelMa, tc = Dist.qt(1 - alpha / 2, df), zc = Dist.qnorm(1 - alpha / 2);
        if (!ok(seHK) || !ok(seCl)) return null;   // SE_HK = 0 (q = 0) is a valid boundary case of the conventional adjustment, see above
        Hk h = new Hk(); h.q = q; h.seClassic = seCl; h.primary = prim; h.kr = kr; h.primaryIdx = prim ? Arrays.asList("", "se", "ci", "IQWiG6").indexOf(ah) : -1;
        double dl = Rma.fit(P.idxTE, vv, null, "DL").tau2, sd = 0; for (int i = 0; i < k; i++) sd += 1 / (vv[i] + dl);
        double wHK = 2 * tc * seHK, wCl = 2 * zc * seCl, wDL = 2 * zc / Math.sqrt(sd);
        String[] names = {"none (conventional Hartung-Knapp)", "se", "ci", "IQWiG6"};
        String[] rules = {"SE_HK = sqrt(q / sum w*_i); t distribution with k - 1 d.f.",
            "SE_HK is replaced by the classic SE when q < 1 (SE never below the classic random-effects SE); t distribution with k - 1 d.f.",
            "the classic z interval replaces the Hartung-Knapp interval when the latter is narrower",
            "the classic SE (t distribution) replaces SE_HK when the Hartung-Knapp interval is narrower than the DerSimonian-Laird-based classic interval (IQWiG method 6)"};
        String[] checks = {"no correction", "q = " + Fmt.num(q, 4) + (q < 1 ? " < 1, so SE_HK (" + Fmt.num(seHK, 4) + ") is smaller than the classic SE (" + Fmt.num(seCl, 4) + ")" : " >= 1, so SE_HK (" + Fmt.num(seHK, 4) + ") is not smaller than the classic SE (" + Fmt.num(seCl, 4) + ")"),
            "width of the Hartung-Knapp interval " + Fmt.num(wHK, 4) + " versus classic z interval " + Fmt.num(wCl, 4) + " (analysis scale)",
            "width of the Hartung-Knapp interval " + Fmt.num(wHK, 4) + " versus DerSimonian-Laird-based classic z interval " + Fmt.num(wDL, 4) + " (analysis scale)"};
        boolean[] trig = {false, q < 1, wHK < wCl, wHK < wDL};
        for (int j = 0; j < 4; j++) {
            double se = seHK; boolean t = true;
            if (j == 1 && trig[1]) se = seCl;
            if (j == 2 && trig[2]) { se = seCl; t = false; }
            if (j == 3 && trig[3]) se = seCl;
            HkVar x = new HkVar(); x.name = names[j]; x.rule = rules[j]; x.se = se; x.t = t; x.applied = trig[j]; x.check = checks[j] + (j == 0 ? "" : trig[j] ? ": correction applied" : ": correction not triggered, result identical to the conventional adjustment"); x.df = t ? df : Double.NaN; double c = t ? tc : zc;
            x.lo = mu - c * se; x.up = mu + c * se;
            if (se == 0) { x.degenerate = true; x.p = mu == nullW ? Double.NaN : 0.0; }   // as ci() of meta: statistic +-Inf gives p = 0, 0/0 gives NA
            else x.p = t ? Dist.ptTwoSided((mu - nullW) / se, df) : Dist.pnormTwoSided((mu - nullW) / se);
            h.vars.add(x);
        }
        return h;
    }

    /** Reproducible precision gradient: studies ranked by 1/SE_i (analysis scale), the m = floor(k/3) extremes selected before the medians are taken. */
    private static final class Prec { int m; List<Integer> hi = new ArrayList<>(), lo = new ArrayList<>(); double medHi, medLo; }
    private static Prec precision(MetaResult r, int k) {
        if (k < 9) return null;
        List<Integer> ord = new ArrayList<>(); for (int i = 0; i < r.TE.length; i++) if (ok(r.TE[i]) && ok(r.seTE[i]) && r.seTE[i] > 0) ord.add(i);
        ord.sort(Comparator.comparingDouble(i -> r.seTE[i]));   // ascending SE = descending 1/SE; stable, so ties keep the input order
        Prec pg = new Prec(); pg.m = k / 3; List<Double> hv = new ArrayList<>(), lv = new ArrayList<>();
        for (int j = 0; j < pg.m; j++) { pg.hi.add(ord.get(j)); pg.lo.add(ord.get(k - 1 - j)); hv.add(r.TE[ord.get(j)]); lv.add(r.TE[ord.get(k - 1 - j)]); }
        pg.medHi = median(hv); pg.medLo = median(lv); return pg;
    }

    /** Median of a list of doubles. */
    private static double median(List<Double> x) { List<Double> c = new ArrayList<>(x); Collections.sort(c); int n = c.size(); return n % 2 == 1 ? c.get(n / 2) : 0.5 * (c.get(n / 2 - 1) + c.get(n / 2)); }

    private static String rng0(double te, double se, double zc, java.util.function.DoubleUnaryOperator v) { return ci(v.applyAsDouble(te - zc * se), v.applyAsDouble(te + zc * se)); }

    /** Narrative sentences of the Hartung-Knapp block (comparison of variants or sensitivity analysis), appended to b. */
    private static void hkNarr(StringBuilder b, Hk hk, boolean sigModel, String cl, java.util.function.DoubleUnaryOperator v) {
        if (hk != null) { HkVar c0 = hk.conv(); StringBuilder dv = new StringBuilder();
            String lead = hk.primary ? (hk.primaryIdx == 0 ? "As a comparison of Hartung-Knapp variants (the reported pooled result already is the conventional Hartung-Knapp adjustment without ad hoc correction, so this is not a sensitivity analysis against the classic Wald z test), "
                    : "As a comparison of Hartung-Knapp variants (the reported pooled result already uses the Hartung-Knapp method with the '" + hk.vars.get(hk.primaryIdx).name + "' ad hoc correction, so this is not a sensitivity analysis against the classic Wald z test), ")
                : hk.kr ? "As a sensitivity analysis, and not as the inferential method behind the pooled result (the Kenward-Roger adjustment), " : "As a sensitivity analysis, and not as the inferential method behind the pooled curve (the classic Wald z test), ";
            if (c0.degenerate) b.append(lead).append("the conventional Hartung-Knapp adjustment was degenerate (q = ").append(n4(hk.q)).append(" gives SE_HK = 0, an interval of zero width and a degenerate p-value (0 only as a numerical limit, or undefined when the estimate equals the null); t distribution with ").append(Fmt.num(c0.df, 0)).append(" d.f.), so its conclusion at the 5% level cannot be assessed. ");
            else b.append(lead).append("the conventional Hartung-Knapp adjustment (SE = sqrt(q / sum w*_i) with q = ").append(n4(hk.q)).append("; t distribution with ").append(Fmt.num(c0.df, 0)).append(" d.f.) gave a ").append(cl).append(" of ")
                .append(n4(v.applyAsDouble(c0.lo))).append(" to ").append(n4(v.applyAsDouble(c0.up))).append(" (").append(pEq(c0.p)).append(hk.primary && hk.primaryIdx == 0 ? "), which is the reported result. " : "), so the conclusion at the 5% level was " + ((c0.p < ALPHA) == sigModel ? "unchanged. " : "different. "));
            String[] nm = {"", "'se'", "'ci'", "IQWiG 6"}; StringBuilder nt = new StringBuilder();
            for (int j = 1; j < 4; j++) { HkVar x = hk.vars.get(j);
                if (x.applied) dv.append(dv.length() > 0 ? "; " : "").append(nm[j]).append(" ad hoc correction: ").append(n4(v.applyAsDouble(x.lo))).append(" to ").append(n4(v.applyAsDouble(x.up))).append(" (").append(pEq(x.p)).append(")");
                else nt.append(nt.length() > 0 ? ", " : "").append(nm[j]); }
            if (dv.length() > 0) b.append(hk.reportedDegenerate() ? "As alternatives (not the reported result), the ad hoc corrections whose rules were triggered gave: " : "Ad hoc corrections that were triggered gave: ").append(dv).append(". ");
            if (hk.reportedDegenerate()) b.append("These alternatives were not compared with the reported result, because that result is degenerate; comparisons are unavailable, and no statement is made about whether they agree or differ. ");
            if (nt.length() > 0) b.append("The ad hoc correction").append(nt.indexOf(",") >= 0 ? "s " : " ").append(nt).append(nt.indexOf(",") >= 0 ? " were" : " was").append(" not triggered (the Hartung-Knapp result did not meet the condition of the rule; see the rule checks in section 2), so ").append(nt.indexOf(",") >= 0 ? "they coincide" : "it coincides").append(" with the conventional adjustment. "); }
    }

    /** Narrative sentences for the pooled effect of one model, conditional on the plotted curve and on the reported test. */
    private static String poolNarr(MetaResult r, Pool P, MetaSpec s, boolean random, boolean both, String eff, String cl, double nullW, boolean hasNull, double zc, java.util.function.DoubleUnaryOperator v) {
        double te = random ? P.teRandom : P.teCommon, se = random ? P.seRandom : P.seCommon, lo = random ? P.loRandom : P.loCommon, up = random ? P.upRandom : P.upCommon, p = random ? P.pRandom : P.pCommon;
        boolean differs = !rng0(te, se, zc, v).equals(ci(v.applyAsDouble(lo), v.applyAsDouble(up)));
        String who = both ? (random ? "In the random-effects model" : "In the common-effect model") + ", the" : "The", rng = ci(v.applyAsDouble(te - zc * se), v.applyAsDouble(te + zc * se)).replace(", ", " to ");
        if (random && ok(se) && se == 0) {   // zero standard error (plain Hartung-Knapp with q = 0): no curve, no significance statement from the degenerate test
            StringBuilder nb = new StringBuilder(who).append(" pooled effect estimate was ").append(n4(v.applyAsDouble(te))).append(" (").append(cl).append(" ").append(n4(v.applyAsDouble(lo))).append(" to ").append(n4(v.applyAsDouble(up))).append("). ");
            nb.append("The standard error of this estimate was 0").append(P.hkPrimary ? " (the Hartung-Knapp variance factor q was 0 because the weighted study estimates are identical)" : "").append(", so ").append(ok(p) ? "the test statistic is infinite and the p-value is 0 only as a numerical limit" : "the test statistic is 0/0 and the p-value is undefined").append("; no statement about statistical significance is made from this degenerate result. The random-effects pooled curve cannot be drawn and is omitted from the drapery plot, whereas the study curves are shown. ");
            Hk hk0 = hasNull ? hkSens(P, s, nullW) : null; if (hk0 != null) hkNarr(nb, hk0, false, cl, v);
            return nb.toString(); }
        StringBuilder b = new StringBuilder(who).append(" pooled effect estimate was ").append(n4(v.applyAsDouble(te))).append(" (").append(cl).append(" ").append(n4(v.applyAsDouble(lo))).append(" to ").append(n4(v.applyAsDouble(up)));
        if (hasNull) b.append("; ").append(pEq(p)); b.append("). ");
        if (!hasNull) return b.append("No null effect is defined for this measure, so no statement about statistical significance is made; at the 5% level, hypothetical effects from ").append(rng.replace("[", "").replace("]", "")).append(" are compatible with the data (pooled curve on or above the 5% line). ").toString();
        double pc = Drapery.curve(false, nullW, te, se); boolean sigCurve = pc < ALPHA, sigModel = ok(p) ? p < ALPHA : sigCurve; String nul = eff + " = " + nv(v.applyAsDouble(nullW));
        String lo95 = n4(v.applyAsDouble(te - zc * se)), up95 = n4(v.applyAsDouble(te + zc * se)); double stat = random ? P.statRandom : P.statCommon; boolean tt = random && "t".equals(P.testType) && ok(P.dfRandom);
        String[] sd = sides(s.sm); double tev = te - nullW; String testTxt = ok(stat) ? " (" + (tt ? "t" : "z") + " = " + Fmt.num(stat, 2) + ", " + pEq(p) + ")" : "";
        b.append("The drapery plot illustrates how the statistical significance of the pooled effect changes across hypothetical values of the underlying effect. The pooled normal-theory p-value function reaches the 5% significance threshold at ").append(eff).append(" = ").append(lo95).append(" and ").append(eff).append(" = ").append(up95)
            .append(", corresponding to the limits of the ").append(differs ? "normal-theory (Wald-type) 95% interval (the reported interval is t-based and differs; see section 2)" : "conventional 95% Wald confidence interval").append(". Hypothetical effect values within this interval are not rejected by the corresponding two-sided test at the 5% significance level; values outside it are rejected under that procedure. This does not imply that all values within the interval are equally plausible or that values outside it are impossible. ");
        if (sigCurve) b.append("The null value (").append(nul).append(") lies outside this interval, so the null hypothesis is rejected and the pooled effect was statistically significant at the 5% level").append(testTxt).append(". ");
        else b.append("The null value (").append(nul).append(") lies within this interval, so the null hypothesis is not rejected and the pooled effect was not statistically significant at the 5% level").append(testTxt).append(". ");
        if (sd != null && ok(tev) && tev != 0) b.append("The point estimate corresponds to ").append(tev > 0 ? sd[1] : sd[0]).append(GROUPS).append(sigCurve ? ". " : ", but the data are also compatible with no effect. ");
        Hk hk = random && hasNull ? hkSens(P, s, nullW) : null;
        if (hk != null) hkNarr(b, hk, sigModel, cl, v);
        if (sigCurve != sigModel) b.append("Note that the reported p-value uses the t distribution").append(random && ok(P.dfRandom) ? " (d.f. = " + Fmt.num(P.dfRandom, 1) + ")" : "").append(", whereas the plotted curve uses the normal approximation (p = ").append(Fmt.pval(pc, 4)).append(" at the null); the two lead to different conclusions at the 5% level. ");
        return b.toString();
    }

    // =================================================================== verbose methods text
    private static String nI(double x) { return Math.abs(x - Math.rint(x)) < 1e-9 ? String.valueOf((long) Math.rint(x)) : Fmt.num(x, 2); }

    /** 5.1 Mathematical definition of the plotted statistic. */
    private static String plottedStatistic(MetaSpec s, Pool P, boolean rel, boolean zType, boolean pred, boolean rnd, boolean com, boolean both) {
        StringBuilder b = new StringBuilder("5.1 Plotted statistic\n");
        b.append("theta is a hypothetical true effect on the analysis scale").append(rel ? " (log scale for this ratio measure; the axis shows exp(theta))" : "").append(". For a curve with estimate y and standard error se:\n");
        b.append("  z(theta) = -|y - theta| / se\n");
        b.append("  p(theta) = 2 * [1 - Phi(|y - theta| / se)] = 2 * Phi(z(theta)),  Phi = standard normal distribution function\n");
        b.append("The plot shows ").append(zType ? "z(theta); the right-hand axis displays 100 * [1 - p(theta)]%" : "p(theta)").append(". A hypothetical effect belongs to the normal-theory 100(1 - alpha)% confidence interval when p(theta) >= alpha (|z(theta)| <= 1.96 for alpha = 0.05).\n");
        b.append("Study curves use the study estimate and its standard error. ");
        if (rnd && zeroSe(P)) b.append("The random-effects pooled curve cannot be drawn: the standard error used by the model is 0, so z(theta) = -|y - theta| / se is undefined away from the estimate and the curve would collapse to a vertical line at the estimate. It is omitted from the plot (so is the prediction region, which is drawn around it); the study curves are unaffected. ");
        else if (rnd) b.append("The random-effects pooled curve uses the pooled random-effects estimate and the standard error used by the model. ");
        if (com) b.append("The common-effect pooled curve uses the pooled common-effect estimate and its standard error. ");
        b.append("The curves always use the normal distribution, whatever distribution the reported test uses.\n");
        if (pred) b.append("The prediction curve has the same form with se* = (c / 1.96) * SE_pred, where c is the critical value of the prediction interval (section 5.4). It is a graphical construction calibrated so that its 5% threshold coincides with the limits of the reported ").append(pct(s.levelPredict)).append("% prediction interval. It is not itself the predictive distribution of the true effect in a future study and should not be interpreted as an additional inferential procedure.\n");
        return b.toString();
    }

    private static String crit(Pool P, MetaSpec s, boolean random) {
        double alpha = 1 - s.levelMa;
        if (random && "t".equals(P.testType) && ok(P.dfRandom)) return "t(" + Fmt.num(P.dfRandom, 2) + " d.f.), critical value " + Fmt.num(Dist.qt(1 - alpha / 2, P.dfRandom), 4);
        return "standard normal, critical value " + Fmt.num(Dist.qnorm(1 - alpha / 2), 4);
    }

    /** 5.2 Inferential method of the pooled CI and p-value. */
    private static String inference(MetaResult r, MetaSpec s, Pool P, boolean rnd, boolean com, boolean both, String eff, java.util.function.DoubleUnaryOperator v) {
        StringBuilder b = new StringBuilder("5.2 Inference for the pooled effect (confidence interval and p-value)\n");
        b.append("Null hypothesis: theta = null effect; two-sided p-value; confidence level ").append(pct(s.levelMa)).append("%; CI = estimate -/+ critical value * SE" + (Drapery.relative(s.sm) ? ", back-transformed for ratio measures" : "") + ".\n");
        if (rnd) { b.append(both ? "Random-effects model: " : "").append(randomMethod(s, P)).append("\n");
            b.append("  SE (analysis scale) = ").append(n4(P.seRandom)).append("; test statistic = ").append(n4(P.statRandom)).append("; reference distribution: ").append(crit(P, s, true)).append(".\n"); }
        if (com) { b.append(both ? "Common-effect model: " : "").append(commonMethod(s)).append("\n");
            b.append("  SE (analysis scale) = ").append(n4(P.seCommon)).append("; test statistic = ").append(n4(P.statCommon)).append("; reference distribution: ").append(crit(P, s, false)).append(".\n"); }
        return b.toString() + "\n";
    }

    static String randomMethod(MetaSpec s, Pool P) {
        if (P.lrp) return "penalised (Firth) logistic regression; the random-effects SE is the common-effect SE scaled by the heterogeneity parameter phi" + ("t".equals(P.testType) ? "; t test with k - 1 d.f." : "; Wald z test") + ".";
        if (P.glmm) return "generalised linear mixed model (" + s.modelGlmm + ", " + s.nAGQ + " quadrature points) with a random study effect; " + ("t".equals(P.testType) ? "Wald-type t test with k - 1 d.f." : "Wald-type z test") + "; no study weights.";
        String w = "pooled estimate = weighted mean with weights w*_i = 1/(s_i^2 + tau^2) (tau^2 = " + Report.tauLabel(s.methodTau) + " estimate, treated as known). ";
        String mr = s.methodRandomCi == null ? "classic" : s.methodRandomCi;
        if (P.krPrimary) return w + "Kenward-Roger adjustment (Partlett & Riley 2017): approximate small-sample variance of the pooled estimate and Satterthwaite-type degrees of freedom; t distribution.";
        if (mr.equals("HK") && ("t".equals(P.testType) || P.hkPrimary)) { String ah = s.adhocHakn == null ? "" : s.adhocHakn;
            return w + "Hartung-Knapp (Knapp-Hartung) adjustment: SE = sqrt( [sum w*_i (y_i - mu)^2 / (k - 1)] / sum w*_i ), t distribution with k - 1 d.f."
                + (ah.equals("se") ? " Ad hoc correction: SE is not allowed to be smaller than the classic SE." : ah.equals("ci") ? " Ad hoc correction: the classic z interval is used when it is wider than the Hartung-Knapp interval." + (!P.hkZ ? "" : " Here the correction was triggered: the classic z interval is reported (standard normal distribution).") : ah.equals("IQWiG6") ? " Ad hoc correction (IQWiG 6): classic SE used when the Hartung-Knapp interval is narrower than the DerSimonian-Laird interval." : ""); }
        if (mr.equals("HK") && s.method.equals("SSW")) return w + "sample-size weights; t distribution with k - 1 d.f.";
        if (s.method.equals("SSW")) return "sample-size weights (n_E * n_C / (n_E + n_C)); SE includes tau^2; Wald z test.";
        return w + "Classic (Wald-type) inference: SE = 1 / sqrt(sum w*_i), standard normal distribution; the uncertainty of tau^2 is not propagated to the interval.";
    }

    static String commonMethod(MetaSpec s) {
        String m = s.method == null ? "Inverse" : s.method;
        String base = m.equals("MH") ? "Mantel-Haenszel pooled estimate" : m.equals("Peto") ? "Peto one-step pooled odds ratio" : m.equals("SSW") ? "sample-size weighted mean (weights n_E * n_C / (n_E + n_C))"
            : m.equals("GLMM") ? "generalised linear mixed model without random study effect" : m.equals("LRP") ? "penalised (Firth) logistic regression" : "inverse-variance weighted mean, weights w_i = 1/s_i^2";
        String ci = "IVhet".equals(s.methodCommonCi) && !m.equals("SSW") ? "; heterogeneity-robust (IVhet) variance sqrt( sum w_i^2 (s_i^2 + tau^2) ) / sum w_i" : "; classic variance (heterogeneity ignored)";
        return base + ci + "; Wald-type z test.";
    }

    private static String tauCiName(String m) {
        switch (m) { case "J": return "Jackson (2013) method"; case "QP": return "Q-profile method (Viechtbauer 2007)"; case "BJ": return "Biggerstaff-Jackson method"; case "GENQ": return "generalised Q-statistic method with the weights of the tau^2 estimator"; case "PL": return "profile likelihood"; default: return m; }
    }

    /** 5.3 Between-study variance and heterogeneity statistics, with the method of the I^2 confidence interval. */
    private static String heterogeneity(MetaSpec s, Pool P, boolean rnd, int k) {
        StringBuilder b = new StringBuilder("5.3 Between-study variance and heterogeneity\n");
        if (k < 2 || !ok(P.Q)) return b.append("Not estimable (fewer than 2 studies).\n\n").toString();
        b.append("Cochran Q = ").append(Fmt.num(P.Q, 3)).append(", d.f. = ").append(P.dfQ).append(", ").append(pEq(P.pQ)).append(P.glmm ? " (Wald-type Q of the saturated model)" : "").append("\n");
        if (rnd && ok(P.tau2) && !P.lrp) {
            b.append("tau^2 = ").append(n4(P.tau2)).append(", tau = ").append(n4(P.tau)).append(" (").append(P.glmm ? "estimated by the GLMM" : Report.tauLabel(s.methodTau) + " estimator").append(")");
            if (ok(P.loTau2) && ok(P.upTau2)) b.append("; ").append(pct(s.levelHetstat)).append("% CI of tau^2 ").append(ci(P.loTau2, P.upTau2)).append(" (").append(tauCiName(P.tauCiMethod)).append(")"); else b.append("; no CI for tau^2 (").append(k < 3 ? "needs at least 3 studies" : "not requested or not available for this setting").append(")");
            b.append("\n");
        } else if (!rnd) b.append("tau^2: not part of the common-effect model.\n");
        if (ok(P.I2)) {
            b.append("I^2 = ").append(Fmt.num(100 * P.I2, 1)).append("%");
            if (ok(P.loI2) && ok(P.upI2)) b.append(" (").append(pct(s.levelHetstat)).append("% CI ").append(Fmt.num(100 * P.loI2, 1)).append("% to ").append(Fmt.num(100 * P.upI2, 1)).append("%)");
            if (ok(P.H)) b.append("; H = ").append(Fmt.num(P.H, 3)).append(ok(P.loH) && ok(P.upH) ? " (" + Fmt.num(P.loH, 3) + " to " + Fmt.num(P.upH, 3) + ")" : "");
            b.append("\n");
            boolean tauBased = "tau2".equals(s.methodI2) && ok(P.loTau2);
            if (tauBased) b.append("I^2 confidence interval method: tau^2-based. I^2 = tau^2 / (tau^2 + s^2), s^2 = typical within-study variance (Higgins & Thompson 2002); limits obtained by transforming the CI of tau^2 (").append(tauCiName(P.tauCiMethod)).append(").\n");
            else b.append("I^2 confidence interval method: Q-based (Higgins & Thompson 2002). H = sqrt(Q / (k - 1)); the limits of ln H are ln H -/+ ").append(Fmt.num(Dist.qnorm(1 - (1 - s.levelHetstat) / 2), 3)).append(" * SE(ln H), with SE(ln H) = 0.5 * [ln Q - ln(k - 1)] / [sqrt(2Q) - sqrt(2k - 3)] if Q > k, else sqrt( 1/(2(k - 2)) * (1 - 1/(3(k - 2)^2)) ) (requires k > 2); limits of H are truncated at 1; I^2 = (H^2 - 1) / H^2, with the same transformation applied to the limits.\n");
            if (P.glmm && ok(P.I2tau)) b.append("GLMM: tau^2-based I^2 = ").append(Fmt.num(100 * P.I2tau, 1)).append("% (tau^2 / (tau^2 + typical sampling variance)), as reported by metafor.\n");
        } else b.append("I^2: not estimable.\n");
        return b.append("\n").toString();
    }

    private static String effPredMethod(MetaSpec s, Pool P) {
        String mp = s.methodPredict == null ? "" : s.methodPredict, lab = P.predLabel == null ? "" : P.predLabel;
        if ((mp.equals("KR") || mp.equals("KR-PR")) && !lab.startsWith("Kenward")) mp = "V";
        return mp;
    }

    static String predMethod(MetaSpec s, Pool P) {
        String mp = effPredMethod(s, P);
        switch (mp) {
            case "V": return "SE_pred = sqrt(SE_mu^2 + tau^2), t distribution with k - 1 d.f. (method \"V\" of the meta package)";
            case "HTS": return "SE_pred = sqrt(SE_mu^2 + tau^2), t distribution with k - 2 d.f. (Higgins, Thompson & Spiegelhalter 2009)";
            case "HK": return "SE_pred = sqrt(SE_HK^2 + tau^2) with the Hartung-Knapp variance of the pooled mean, t distribution with k - 1 d.f.";
            case "HK-PR": return "SE_pred = sqrt(SE_HK^2 + tau^2) with the Hartung-Knapp variance of the pooled mean, t distribution with k - 2 d.f. (Partlett & Riley 2017)";
            case "KR": return "SE_pred = sqrt(SE_KR^2 + tau^2) with the Kenward-Roger variance of the pooled mean, t distribution with the Kenward-Roger d.f.";
            case "KR-PR": return "SE_pred = sqrt(SE_KR^2 + tau^2) with the Kenward-Roger variance, t distribution with Kenward-Roger d.f. minus 1 (Partlett & Riley 2017)";
            case "S": return "SE_pred = sqrt(SE_mu^2 + tau^2), standard normal distribution (method \"S\" of the meta package)";
            default: return P.predLabel;
        }
    }

    static String predCritical(Pool P) {
        double c = (P.upPred - P.loPred) / P.sePred / 2;
        boolean norm = Double.isInfinite(P.dfPred);
        return Fmt.num(c, 4) + (norm ? " (standard normal quantile)" : " (t quantile, " + Fmt.num(P.dfPred, 2).replaceAll("\\.00$", "") + " d.f.)") + "; SE_pred = " + n4(P.sePred);
    }

    /** Treatment of the uncertainty of tau^2 and of the pooled mean in the prediction interval. */
    private static String tauTreatment(MetaSpec s, Pool P) {
        String mp = effPredMethod(s, P);
        String base = "tau^2 enters as the point estimate " + n4(P.tau2) + " (plug-in, treated as known). ";
        switch (mp) {
            case "V": return base + "Uncertainty of tau^2 is not propagated explicitly; the t distribution with k - 1 d.f. is a small-sample approximation, and the uncertainty of the pooled mean enters through SE_mu (" + ("t".equals(P.testType) ? "random-effects SE as used by the model" : "classic random-effects SE") + ").";
            case "HTS": return base + "Uncertainty of tau^2 and of the pooled mean is allowed for only approximately, through the heavier-tailed t distribution with k - 2 d.f.";
            case "HK": case "HK-PR": return base + "The uncertainty of the pooled mean is estimated empirically (Hartung-Knapp variance); the uncertainty of tau^2 itself is allowed for only through the t d.f.";
            case "KR": case "KR-PR": return base + "The variance of the pooled mean includes the Kenward-Roger small-sample correction, which accounts for the estimation of tau^2 in that variance; tau^2 in the predictive term is a plug-in.";
            case "S": return base + "Neither the uncertainty of tau^2 nor the finite number of studies is allowed for (normal quantile); the interval can be too narrow for small k.";
            default: return base;
        }
    }

    /** Shorter wording for the manuscript narrative: the pooled-effect method sentence has already said that tau^2 is treated as known. */
    private static String tauTreatmentNarr(MetaSpec s, Pool P) {
        if ("V".equals(effPredMethod(s, P))) return "tau^2 enters as a plug-in point estimate; the t distribution with k - 1 d.f. is only a small-sample approximation for the uncertainty of tau^2, and the uncertainty of the pooled mean enters through SE_mu.";
        return tauTreatment(s, P);
    }

    private static String predictionDetail(MetaSpec s, Pool P, java.util.function.DoubleUnaryOperator v, double nullW) {
        StringBuilder b = new StringBuilder("5.4 Prediction interval\n");
        b.append("Level: ").append(pct(s.levelPredict)).append("%. Interval = pooled random-effects estimate -/+ c * SE_pred (analysis scale)" + (Drapery.relative(s.sm) ? ", back-transformed for ratio measures" : "") + ".\n");
        b.append("Method: ").append(predMethod(s, P)).append("\n");
        b.append("Critical value c = ").append(predCritical(P)).append("\n");
        b.append("Treatment of tau^2 uncertainty: ").append(tauTreatment(s, P)).append("\n");
        b.append("Assumption: the true effects of the studies (and of a future study) are exchangeable draws from a normal distribution with mean mu and variance tau^2.\n\n");
        return b.toString();
    }

    // =================================================================== data and group coding (6)
    private static String direction(String sm) {
        if (Drapery.relative(sm)) return "values above 1 indicate a higher " + (sm.equals("HR") ? "hazard" : sm.equals("OR") || sm.equals("DOR") ? "odds" : sm.equals("RR") ? "risk" : sm.equals("IRR") ? "incidence rate" : "mean") + " in the experimental group than in the control group, values below 1 a lower one";
        if (Arrays.asList("RD", "MD", "SMD", "ASD", "IRD").contains(sm)) return "positive values indicate a larger value in the experimental group than in the control group, negative values a smaller one";
        return "";
    }
    private static String formula(String sm) {
        switch (sm) {
            case "OR": return "ln OR = ln[(a/b) / (c/d)], Var = 1/a + 1/b + 1/c + 1/d (a, b = events and non-events in the experimental group, c, d = in the control group)";
            case "RR": return "ln RR = ln[(a/n_E) / (c/n_C)], Var = 1/a - 1/n_E + 1/c - 1/n_C";
            case "RD": return "RD = a/n_E - c/n_C, Var = a*b/n_E^3 + c*d/n_C^3";
            case "ASD": return "ASD = asin(sqrt(a/n_E)) - asin(sqrt(c/n_C)), Var = 1/(4 n_E) + 1/(4 n_C)";
            case "IRR": return "ln IRR = ln[(a/T_E) / (c/T_C)], Var = 1/a + 1/c (T = person-time)";
            case "IRD": return "IRD = a/T_E - c/T_C, Var = a/T_E^2 + c/T_C^2";
            case "MD": return "MD = mean_E - mean_C, Var = sd_E^2/n_E + sd_C^2/n_C";
            case "ROM": return "ln ROM = ln(mean_E / mean_C), Var = sd_E^2/(n_E mean_E^2) + sd_C^2/(n_C mean_C^2)";
            case "SMD": return "standardised mean difference (pooled SD), small-sample corrected for Hedges' g";
            case "PLOGIT": return "logit(a/n), Var = 1/a + 1/(n - a)";
            case "PLN": return "ln(a/n), Var = 1/a - 1/n";
            case "PAS": return "asin(sqrt(a/n)), Var = 1/(4n)";
            case "PFT": return "Freeman-Tukey double arcsine transformation of a/n";
            case "COR": return "Pearson correlation r, Var = (1 - r^2)^2 / (n - 1)";
            case "ZCOR": return "Fisher z = atanh(r), Var = 1/(n - 3)";
            default: return "";
        }
    }
    private static String sumFmt(double x) { return String.valueOf((long) Math.rint(x)); }

    private static String dataCoding(MetaResult r, MetaSpec s, String eff) {
        StringBuilder b = new StringBuilder(); MetaData d = r.data; String ty = s.type == null ? "gen" : s.type; int n = r.TE.length;
        String E = s.labelE == null || s.labelE.isEmpty() ? "Experimental" : s.labelE, C = s.labelC == null || s.labelC.isEmpty() ? "Control" : s.labelC;
        if (d == null || ty.equals("gen")) {
            return b.append("Generic inverse-variance input: the study effect estimates (TE) and standard errors (seTE) were supplied directly; no event or group data are used, so no event / group coding and no continuity correction apply.\n").toString();
        }
        boolean twoArm = ty.equals("bin") || ty.equals("inc") || ty.equals("cont");
        String[] cols; String[] hd;
        switch (ty) {
            case "bin": cols = new String[]{"event.e", "n.e", "event.c", "n.c"}; hd = new String[]{"Events (E)", "N (E)", "Events (C)", "N (C)"}; break;
            case "inc": cols = new String[]{"event.e", "time.e", "event.c", "time.c"}; hd = new String[]{"Events (E)", "Time (E)", "Events (C)", "Time (C)"}; break;
            case "cont": cols = new String[]{"n.e", "mean.e", "sd.e", "n.c", "mean.c", "sd.c"}; hd = new String[]{"N (E)", "Mean (E)", "SD (E)", "N (C)", "Mean (C)", "SD (C)"}; break;
            case "prop": cols = new String[]{"event", "n"}; hd = new String[]{"Events", "N"}; break;
            case "rate": cols = new String[]{"event", "time"}; hd = new String[]{"Events", "Time"}; break;
            case "mean": cols = new String[]{"n", "mean", "sd"}; hd = new String[]{"N", "Mean", "SD"}; break;
            case "cor": cols = new String[]{"cor", "n"}; hd = new String[]{"r", "N"}; break;
            default: return "Study-level group data are not available for this analysis type.\n";
        }
        double[][] x = new double[cols.length][]; for (int q = 0; q < cols.length; q++) { x[q] = d.get(cols[q]); if (x[q] == null || x[q].length < n) return "Study-level group data are not available (columns missing).\n"; }
        double[] ie = r.eff != null ? r.eff.incrE : null, ic = r.eff != null ? r.eff.incrC : null;
        if (twoArm) b.append("Group coding: E = the group labelled \"").append(E).append("\", C = the group labelled \"").append(C).append("\". ");
        String dir = direction(s.sm); if (!dir.isEmpty()) b.append("Direction of the effect measure: ").append(dir).append(". ");
        b.append("\n");
        String f = formula(s.sm); if (!f.isEmpty()) b.append("Effect size: ").append(f).append(".\n");
        if (ty.equals("cont") && s.sm.equals("SMD")) b.append("SMD method: ").append(s.methodSmd).append(s.pooledvar ? ", pooled variance" : "").append(", standardiser ").append(s.sdGlass ? "SD of the experimental group (Glass)" : "pooled SD").append(".\n");
        boolean cnt = ty.equals("bin") || ty.equals("inc") || ty.equals("prop") || ty.equals("rate");
        List<String> head = new ArrayList<>(List.of("#", "Study")); head.addAll(Arrays.asList(hd)); if (cnt) head.add("Cont. corr.");
        List<String[]> rows = new ArrayList<>(); double[] tot = new double[cols.length]; int used = 0, corr = 0, single0 = 0, double0 = 0;
        for (int i = 0; i < n; i++) { boolean good = ok(r.TE[i]) && ok(r.seTE[i]) && r.seTE[i] > 0;
            List<String> row = new ArrayList<>(); row.add(String.valueOf(i + 1)); row.add(r.studlab[i]);
            for (int q = 0; q < cols.length; q++) row.add(ok(x[q][i]) ? nI(x[q][i]) : "NA");
            if (cnt) { double inc = ie != null && i < ie.length && ok(ie[i]) ? ie[i] : 0; row.add(inc > 0 ? nI(inc) : "-"); if (good && inc > 0) corr++; }
            rows.add(row.toArray(new String[0]));
            if (good) { used++; for (int q = 0; q < cols.length; q++) if (ok(x[q][i])) tot[q] += x[q][i];
                if (ty.equals("bin")) { double a = x[0][i], nE = x[1][i], c = x[2][i], nC = x[3][i]; boolean z1 = a == 0 || a == nE || c == 0 || c == nC, z2 = (a == 0 && c == 0) || (a == nE && c == nC); if (z2) double0++; else if (z1) single0++; }
                if (ty.equals("inc")) { double a = x[0][i], c = x[2][i]; if (a == 0 && c == 0) double0++; else if (a == 0 || c == 0) single0++; } } }
        boolean[] right = new boolean[head.size()]; Arrays.fill(right, true); right[1] = false;
        b.append("Study-level data of the studies used (NA = missing):\n");
        b.append(Report.table(head, rows, right));
        StringBuilder t = new StringBuilder("Totals over the ").append(used).append(" studies used: ");
        switch (ty) {
            case "bin": t.append("events ").append(sumFmt(tot[0])).append(" / ").append(sumFmt(tot[1])).append(" (E), ").append(sumFmt(tot[2])).append(" / ").append(sumFmt(tot[3])).append(" (C)"); break;
            case "inc": t.append("events ").append(sumFmt(tot[0])).append(" over ").append(nI(tot[1])).append(" ").append(s.irunit).append(" (E), ").append(sumFmt(tot[2])).append(" over ").append(nI(tot[3])).append(" ").append(s.irunit).append(" (C)"); break;
            case "cont": t.append("N = ").append(sumFmt(tot[0])).append(" (E), ").append(sumFmt(tot[3])).append(" (C)"); break;
            case "prop": t.append("events ").append(sumFmt(tot[0])).append(" / N ").append(sumFmt(tot[1])); break;
            case "rate": t.append("events ").append(sumFmt(tot[0])).append(" over ").append(nI(tot[1])).append(" ").append(s.irunit); break;
            case "mean": t.append("N = ").append(sumFmt(tot[0])); break;
            default: t.append("N = ").append(sumFmt(tot[1]));
        }
        b.append(t).append(".\n");
        if (ty.equals("bin") || ty.equals("inc")) b.append("Studies with a zero cell in one group: ").append(single0).append("; in both groups (double-zero): ").append(double0).append(".\n");
        if (cnt) {
            String mi = s.methodIncr == null ? "only0" : s.methodIncr;
            String how = mi.equals("all") ? "added to every study" : mi.equals("if0all") ? "added to all studies if any study has a zero cell" : "added only to studies with a zero cell";
            b.append("Continuity correction: increment ").append(nI(s.incr)).append(" (").append(how).append(", method.incr = ").append(mi).append("); applied to ").append(corr).append(" of ").append(used).append(" studies used").append(s.mhExact && s.method.equals("MH") ? "; Mantel-Haenszel pooling without correction (exact)" : "").append(".\n");
            if (s.method.equals("Peto") || s.isGlmm() || s.isLrp()) b.append("Pooling method ").append(s.method).append(" works from the raw counts; the correction affects only the study-level estimates shown.\n");
        }
        return b.toString();
    }

    /** Methods paragraph of the narrative (manuscript-ready), conditional on what was displayed. */
    private static String methodsNarrative(MetaResult r, MetaSpec s, Pool P, boolean rnd, boolean com, boolean both, boolean pred, boolean rel, boolean zType, int k) {
        StringBuilder b = new StringBuilder("Statistical methods. ");
        b.append(zType ? "The plotted curves are the test-statistic functions z(theta) = -|y - theta| / se" : "The plotted curves are the two-sided p-value functions p(theta) = 2[1 - Phi(|y - theta| / se)]")
            .append(", where theta is the hypothetical true effect on the analysis scale").append(rel ? " (log scale, back-transformed for display)" : "").append(", y the study or pooled estimate and se its standard error; the normal distribution is used for the curves. ");
        if (rnd) { String rm = randomMethod(s, P); b.append(both ? "For the random-effects model, " : "").append(both ? Character.toLowerCase(rm.charAt(0)) + rm.substring(1) : Character.toUpperCase(rm.charAt(0)) + rm.substring(1)); b.append(" "); }
        if (com) { String cm = commonMethod(s); b.append(both ? "For the common-effect model, " + Character.toLowerCase(cm.charAt(0)) + cm.substring(1) : Character.toUpperCase(cm.charAt(0)) + cm.substring(1)).append(" "); }
        if (rnd && ok(P.tau2) && !P.lrp && P.k >= 2) { b.append("Between-study variance tau^2 was estimated by ").append(P.glmm ? "the GLMM" : Report.tauLabel(s.methodTau)).append(ok(P.loTau2) && ok(P.upTau2) ? " with a " + pct(s.levelHetstat) + "% confidence interval from the " + tauCiName(P.tauCiMethod) : "").append(". "); }
        if (ok(P.I2) && P.k >= 2) { b.append("The confidence interval of I^2 was based on ").append("tau2".equals(s.methodI2) && ok(P.loTau2) ? "the transformed confidence interval of tau^2 (tau^2-based I^2)" : "the Q-based H statistic (Higgins and Thompson, 2002; limits of ln H with the standard error of that paper)").append(". "); }
        if (pred) b.append("The prediction interval used ").append(predMethod(s, P).replaceFirst("^SE_pred", "SE_pred")).append(", critical value ").append(predCritical(P).replaceAll(";.*$", "")).append("; ").append(tauTreatmentNarr(s, P)).append(" ");
        String ty = s.type == null ? "gen" : s.type;
        if (ty.equals("gen") || r.data == null) b.append("Study effect estimates and standard errors were used as supplied (generic inverse-variance data).");
        else if (ty.equals("bin") || ty.equals("inc")) { String E = s.labelE == null || s.labelE.isEmpty() ? "Experimental" : s.labelE, C = s.labelC == null || s.labelC.isEmpty() ? "Control" : s.labelC;
            b.append("Study-level counts were coded as events and ").append(ty.equals("bin") ? "group sizes" : "person-time").append(" in the ").append(E).append(" (E) and ").append(C).append(" (C) groups (see section 6 of the statistical report); ")
                .append(s.methodIncr != null && s.methodIncr.equals("all") ? "a continuity correction of " + nI(s.incr) + " was added to all studies." : "a continuity correction of " + nI(s.incr) + " was applied to studies with a zero cell (" + (s.methodIncr == null ? "only0" : s.methodIncr) + ").").append(' '); }
        else b.append("The study-level data used for the effect sizes are listed in section 6 of the statistical report.");
        return b.toString().trim();
    }
}
