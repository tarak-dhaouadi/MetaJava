package org.metajava.core;

import java.util.*;

/** Text output equivalent to print(summary(meta)), print(metabias), print(metareg), etc. */
public final class Report {
    private Report() {}
    static final String NL = "\n";

    /** A table printed in the text output, kept in structured form for the Excel and Word exports. */
    public static final class Tbl {
        public final String rendered; public final List<String> head; public final List<String[]> rows; public final boolean[] right;
        public Tbl(String rendered, List<String> head, List<String[]> rows, boolean[] right) { this.rendered = rendered; this.head = new ArrayList<>(head); this.rows = new ArrayList<>(rows); this.right = right.clone(); }
    }
    private static final ThreadLocal<List<Tbl>> REC = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> MUTE = new ThreadLocal<>();
    /** Starts recording the tables printed by this thread (see {@link #drain}). */
    public static void startCapture() { REC.set(new ArrayList<>()); }
    public static void stopCapture() { REC.remove(); MUTE.remove(); }
    /** Returns the tables recorded since the last call and clears the record. */
    public static List<Tbl> drain() { List<Tbl> l = REC.get(); if (l == null) return new ArrayList<>(); List<Tbl> c = new ArrayList<>(l); l.clear(); return c; }
    private static void record(String rendered, List<String> head, List<String[]> rows, boolean[] right) { List<Tbl> l = REC.get(); if (l != null && MUTE.get() == null) l.add(new Tbl(rendered, head, rows, right)); }
    /** Registers a table that was assembled by hand (rendered text and its cells). */
    private static void recordManual(String rendered, List<String> head, List<String[]> rows, boolean[] right) { List<Tbl> l = REC.get(); if (l != null) l.add(new Tbl(rendered, head, rows, right)); }

    /** Splits a text output into paragraphs (a String[] of lines) and tables (Tbl), in the order printed. */
    public static List<Object> blocks(String text, List<Tbl> tables) {
        List<Object> out = new ArrayList<>(); int pos = 0;
        for (Tbl t : tables) {
            int i = text.indexOf(t.rendered, pos); if (i < 0) continue;
            paragraphs(text.substring(pos, i), out); out.add(t); pos = i + t.rendered.length();
        }
        paragraphs(text.substring(pos), out); return out;
    }
    private static void paragraphs(String seg, List<Object> out) {
        List<String> cur = new ArrayList<>();
        for (String line : seg.split("\n", -1)) { if (line.trim().isEmpty()) { if (!cur.isEmpty()) { out.add(cur.toArray(new String[0])); cur.clear(); } } else cur.add(line); }
        if (!cur.isEmpty()) out.add(cur.toArray(new String[0]));
    }

    static final Map<String, String> TAU_NAME = new LinkedHashMap<>();
    static { TAU_NAME.put("DL", "DerSimonian-Laird"); TAU_NAME.put("PM", "Paule-Mandel"); TAU_NAME.put("REML", "Restricted maximum-likelihood");
        TAU_NAME.put("ML", "Maximum-likelihood"); TAU_NAME.put("HS", "Hunter-Schmidt"); TAU_NAME.put("HSk", "Hunter-Schmidt with small-sample correction"); TAU_NAME.put("GENQ", "Generalised Q-statistic"); TAU_NAME.put("GENQM", "Median-unbiased generalised Q-statistic"); TAU_NAME.put("SJ", "Sidik-Jonkman"); TAU_NAME.put("HE", "Hedges"); TAU_NAME.put("EB", "Empirical Bayes");
        TAU_NAME.put("PMM", "Median-unbiased Paule-Mandel"); TAU_NAME.put("MP", "Mandel-Paule"); TAU_NAME.put("DLIT", "DerSimonian-Laird (iterated)"); TAU_NAME.put("SJIT", "Sidik-Jonkman (iterated)"); }

    public static String smLabel(MetaSpec s) {
        switch (s.sm) {
            case "HR": return "HR"; case "OR": return "OR"; case "RR": return "RR"; case "RD": return "RD"; case "ASD": return "ASD"; case "DOR": return "DOR"; case "VE": return "VE";
            case "MD": return "MD"; case "SMD": return "SMD"; case "ROM": return "ROM"; case "ZCOR": return s.backtransf ? "COR" : "ZCOR"; case "COR": return "COR";
            case "IRR": return "IRR"; case "IRD": return "IRD"; case "IRSD": return "IRSD"; case "MRAW": return "mean"; case "MLN": return s.backtransf ? "mean" : "MLN";
            case "PLOGIT": case "PLN": case "PRAW": case "PAS": case "PFT": return s.backtransf ? "proportion" : s.sm; case "IR": case "IRLN": case "IRS": case "IRFT": return s.backtransf ? "rate" : s.sm;
            default: return s.sm.isEmpty() ? "TE" : s.sm;
        }
    }

    static double bt(MetaSpec s, double x, Pool p) { return Meta.back(s, x, p.nHarm); }

    /** n (proportions) or person-time (rates) of study i, needed to back-transform Freeman-Tukey values. */
    public static double nOf(MetaSpec s, int i) { return s.nPft != null && i < s.nPft.length ? s.nPft[i] : Double.NaN; }
    /** Study estimate on the original scale; meta shows event / n (event / time) for single proportions (rates). */
    public static double studyEst(MetaResult r, int i) {
        MetaSpec s = r.spec;
        if (s.backtransf && s.type.equals("prop")) return r.data.get("event")[i] / r.data.get("n")[i] * s.pscale;
        if (s.backtransf && s.type.equals("rate")) return r.data.get("event")[i] / r.data.get("time")[i] * s.irscale;
        return Meta.back(s, r.TE[i], nOf(s, i));
    }
    public static double studyLo(MetaResult r, int i) { return Meta.back(r.spec, r.lower[i], nOf(r.spec, i)); }
    public static double studyUp(MetaResult r, int i) { return Meta.back(r.spec, r.upper[i], nOf(r.spec, i)); }

    /** Confidence level in percent without superfluous decimals (95, 99, 97.5). */
    public static String pct(double level) { double x = Math.round(level * 1e6) / 1e4; return x == Math.rint(x) ? String.valueOf((long) x) : String.valueOf(x); }

    public static String tauLabel(String m) { return TAU_NAME.getOrDefault(m, m); }

    public static String summary(MetaResult r) {
        MetaSpec s = r.spec; Pool p = r.overall; StringBuilder b = new StringBuilder();
        int k = p.k; String lab = smLabel(s); int d = 4;
        if (!s.title.isEmpty()) b.append("Title: ").append(s.title).append(NL).append(NL);
        b.append("Meta-Analysis Summary").append(NL).append(NL);
        // study table
        List<String[]> rows = new ArrayList<>(); List<String> head = new ArrayList<>();
        head.add(""); head.add(lab); head.add(Fmt.num(s.level * 100, 0).replaceAll("\\.0+$", "") + "% CI");
        boolean showW = !s.noWeights();
        if (showW && s.common) head.add("%W(" + s.textWCommon + ")"); if (showW && s.random) head.add("%W(" + s.textWRandom + ")");
        List<String> extraNames = new ArrayList<>();
        if (r.data.subgroup != null) { head.add(s.subgroupName.isEmpty() ? "Subgroup" : s.subgroupName); }
        double sc = 0, sr = 0; for (int i = 0; i < r.TE.length; i++) { if (!Double.isNaN(r.wCommonFull[i])) sc += r.wCommonFull[i]; if (!Double.isNaN(r.wRandomFull[i])) sr += r.wRandomFull[i]; }
        for (int i = 0; i < r.TE.length; i++) {
            List<String> row = new ArrayList<>();
            row.add(r.studlab[i]);
            if (Double.isNaN(r.TE[i])) { row.add("NA"); row.add(""); } else {
                row.add(Fmt.num(studyEst(r, i), d)); row.add(Fmt.ci(studyLo(r, i), studyUp(r, i), d)); }
            if (showW && s.common) row.add(Fmt.num(100 * (Double.isNaN(r.wCommonFull[i]) ? 0 : r.wCommonFull[i]) / sc, 1));
            if (showW && s.random) row.add(Fmt.num(100 * (Double.isNaN(r.wRandomFull[i]) ? 0 : r.wRandomFull[i]) / sr, 1));
            if (r.data.subgroup != null) row.add(r.data.subgroup[i] == null ? "" : r.data.subgroup[i]);
            rows.add(row.toArray(new String[0]));
        }
        b.append(table(head, rows, new boolean[]{false, true, true, true, true, false}));
        b.append(NL).append("Number of studies: k = ").append(k).append(NL);
        if (s.type.equals("bin") || s.type.equals("cont") || s.type.equals("cor") || s.type.equals("prop") || s.type.equals("mean")) {
            double o = 0; boolean any = false;
            for (String a : new String[]{"n.e", "n.c", "n"}) { double[] x = r.data.get(a); if (x != null) { for (int i = 0; i < x.length; i++) if (!Double.isNaN(r.TE[i])) o += x[i]; any = true; } }
            if (any) b.append("Number of observations: o = ").append((long) o).append(NL);
        }
        if (s.type.equals("bin") || s.type.equals("prop") || s.type.equals("inc") || s.type.equals("rate")) {
            double ev = 0; for (String a : new String[]{"event.e", "event.c", "event"}) { double[] x = r.data.get(a); if (x != null) for (int i = 0; i < x.length; i++) if (!Double.isNaN(r.TE[i]) || !s.type.equals("bin")) ev += x[i]; }
            b.append("Number of events: e = ").append((long) ev).append(NL);
        }
        b.append(NL);
        b.append(pooledTable(r, p, "", false));
        b.append(se0Note(s, List.of(s.textRandom), List.of(p)));
        b.append(hetText(r, p, s));
        b.append(predText(r, p, s));
        if (r.hasSubgroups()) b.append(subgroupText(r));
        b.append(details(r));
        b.append(tauSensitivity(r));
        return b.toString();
    }

    /** Estimators of tau^2 compared in {@link #tauSensitivity}. */
    static final String[] TAU_SENS = {"DL", "DLIT", "REML", "PM", "PMM", "ML", "HS", "HSk", "SJ", "SJIT", "HE", "EB", "GENQ", "GENQM"};   // MP is PM under another name and is not repeated

    /**
     * Sensitivity table by tau^2 estimator (random-effects model): the pooled effect, its confidence interval and p-value, I^2, the p-value of the test of heterogeneity,
     * tau^2 and the prediction interval, re-estimated with each estimator. Everything else (pooling method, CI method of the pooled effect, levels) is kept as in the main analysis.
     * Empty for common-effect-only analyses, GLMM and penalised-likelihood pooling (no tau^2 estimator to choose) and fewer than two studies.
     */
    static String tauSensitivity(MetaResult r) {
        MetaSpec s = r.spec; Pool p0 = r.overall;
        if (!s.random || s.isGlmm() || s.isLrp() || p0.k < 2 || r.commonFn == null && s.method.equals("MH")) return "";
        int d = 4; boolean test = s.hasNull(); int[] all = new int[r.TE.length]; for (int i = 0; i < all.length; i++) all[i] = i;
        List<String> head = new ArrayList<>(List.of("Tau^2 estimator", smLabel(s), pct(s.levelMa) + "% CI")); if (test) head.add("p-value");
        head.add("I^2"); head.add("Het. p-value"); head.add("tau^2"); head.add(pct(s.levelPredict) + "% PI");
        List<String[]> rows = new ArrayList<>(); boolean any = false; Map<String, String> notes = new LinkedHashMap<>();
        // the fourteen refits are independent: run in parallel, each into its own slot; the rows and notes are then assembled in the order of TAU_SENS (identical output to a serial loop)
        final Pool[] fits = new Pool[TAU_SENS.length]; final String[] fitErr = new String[TAU_SENS.length]; final boolean[] threw = new boolean[TAU_SENS.length]; final int[] allIdx = all;
        Par.forRange(TAU_SENS.length, i -> {
            try { MetaSpec c = s.clone(); c.methodTau = TAU_SENS[i]; c.methodTauCi = ""; c.prediction = true; fits[i] = Pool.compute(c, r.TE, r.seTE, allIdx, r.commonFn); }
            catch (RuntimeException e) { fits[i] = null; threw[i] = true; fitErr[i] = e.getMessage(); }
        });
        for (int mi = 0; mi < TAU_SENS.length; mi++) {
            String m = TAU_SENS[mi];
            List<String> row = new ArrayList<>(); row.add(m + (m.equals(s.methodTau) ? " *" : ""));
            Pool p = fits[mi]; if (threw[mi] && MetaSpec.isGenq(m)) notes.put(m, fitErr[mi]);
            if (p == null || Double.isNaN(p.teRandom)) { for (int j = 1; j < head.size(); j++) row.add("NA"); rows.add(row.toArray(new String[0])); continue; }
            any = true; boolean se0 = p.randomSe0();
            row.add(Fmt.num(bt(s, p.teRandom, p), d)); row.add(se0 ? "NA" : Fmt.ci(bt(s, p.loRandom, p), bt(s, p.upRandom, p), d));
            if (test) row.add(se0 ? "NA" : Fmt.pval(p.pRandom, 4));
            row.add(Double.isNaN(p.I2) ? "NA" : Fmt.num(100 * p.I2, 1) + "%"); row.add(Fmt.pval(p.pQ, 4)); row.add(Fmt.num(p.tau2, 4));
            row.add(Double.isNaN(p.loPred) ? "NA" : Fmt.ci(bt(s, p.loPred, p), bt(s, p.upPred, p), d));
            rows.add(row.toArray(new String[0]));
        }
        if (!any) return "";
        boolean[] right = new boolean[head.size()]; Arrays.fill(right, true); right[0] = false;
        return NL + "Sensitivity analysis by tau^2 estimator (random effects model):" + NL + NL + table(head, rows, right) + NL + "* estimator used in the main analysis. Other settings (pooling method, CI method, levels) are those of the main analysis; PI: prediction interval." + NL
            + genqNote(s, notes) + NL;
    }

    /** Footnote on the weights of GENQ / GENQM (the Weights option applies to these two estimators only) and on why either of them could not be computed. */
    static String genqNote(MetaSpec s, Map<String, String> failed) {
        StringBuilder b = new StringBuilder("GENQ / GENQM weights: " + s.genqWeights + (s.genqWeights != null && s.genqWeights.equals("Custom") ? " (" + (s.genqCustom == null ? "" : s.genqCustom.trim()) + ")" : "") + "; they are used by these two estimators only.");
        for (Map.Entry<String, String> e : failed.entrySet()) b.append(NL).append(e.getKey()).append(": NA (").append(e.getValue()).append(")");
        return b.toString();
    }

    static String pooledTable(MetaResult r, Pool p, String suffix, boolean pi) {
        MetaSpec s = r.spec; int d = 4; List<String> head = new ArrayList<>(); List<String[]> rows = new ArrayList<>();
        head.add(""); head.add(smLabel(s)); head.add(Fmt.num(s.levelMa * 100, 0) + "% CI");
        boolean test = !Double.isNaN(s.nullValue());
        if (test) { head.add(p.testType.equals("t") && s.random && !s.common ? "t" : "z|t"); head.add("p-value"); }
        if (s.common) { List<String> row = new ArrayList<>(); row.add(s.textCommon + suffix); row.add(Fmt.num(bt(s, p.teCommon, p), d)); row.add(Fmt.ci(bt(s, p.loCommon, p), bt(s, p.upCommon, p), d));
            if (test) { row.add(Fmt.num(p.statCommon, 2)); row.add(Fmt.pval(p.pCommon, 4)); } rows.add(row.toArray(new String[0])); }
        if (s.random) { List<String> row = new ArrayList<>(); row.add(s.textRandom + p.randomLabelSuffix + suffix); row.add(Fmt.num(bt(s, p.teRandom, p), d)); row.add(Fmt.ci(bt(s, p.loRandom, p), bt(s, p.upRandom, p), d));
            if (test) { boolean se0 = p.randomSe0(); row.add(se0 ? "NA" : Fmt.num(p.statRandom, 2)); row.add(se0 ? "NA" : Fmt.pval(p.pRandom, 4)); } rows.add(row.toArray(new String[0])); }
        if (s.prediction && pi && !Double.isNaN(p.loPred)) { List<String> row = new ArrayList<>(); row.add(s.textPredict); row.add(""); row.add(Fmt.ci(bt(s, p.loPred, p), bt(s, p.upPred, p), d)); if (test) { row.add(""); row.add(""); } rows.add(row.toArray(new String[0])); }
        // fix z/t header
        if (test) head.set(3, s.random && (p.testType.equals("t")) ? (s.common ? "z|t" : "t") : "z");
        boolean[] right = new boolean[head.size()]; Arrays.fill(right, true); right[0] = false;
        return table(head, rows, right) + NL;
    }

    /** Description of the GLMM as printed by meta (catmeth). */
    static String glmmText(MetaSpec s) {
        switch (s.type) {
            case "bin": return s.modelGlmm.equals("UM.RS") ? "- Mixed-effects logistic regression model (random study effects)" : s.modelGlmm.equals("CM.EL") ? "- Generalised linear mixed model (conditional Hypergeometric-Normal)" : s.modelGlmm.equals("CM.AL") ? "- Generalised linear mixed model (conditional Binomial-Normal)" : "- Logistic regression model (fixed study effects)";
            case "inc": return s.modelGlmm.equals("UM.RS") ? "- Mixed-effects Poisson regression model (random study effects)" : s.modelGlmm.startsWith("CM") ? "- Generalised linear mixed model (conditional Poisson-Normal)" : "- Poisson regression model (fixed study effects)";
            case "prop": return "- Random intercept logistic regression model";
            default: return "- Random intercept Poisson regression model";
        }
    }

    /** Prediction interval of the main analysis (printed below the test of heterogeneity). */
    static String predText(MetaResult r, Pool p, MetaSpec s) {
        if (!s.prediction || Double.isNaN(p.loPred)) return "";
        List<String[]> rows = new ArrayList<>(); rows.add(new String[]{s.textPredict, Fmt.ci(bt(s, p.loPred, p), bt(s, p.upPred, p), 4)});
        return "Prediction interval (" + pct(s.levelPredict) + "%):" + NL + table(List.of("", pct(s.levelPredict) + "% PI"), rows, new boolean[]{false, true}) + NL;
    }

    /**
     * Note when the likelihood of the ML / REML estimator has a second, higher maximum than the estimate reported (few studies, very unequal variances).
     * The estimate is NOT moved to it: it is the one found by the usual search from zero (as in metafor), and every result of the analysis uses it.
     */
    static String modeNote(double alt, double gain, double tau2, String method) {
        if (Double.isNaN(alt)) return "";
        String what = method.equals("REML") ? "restricted log-likelihood" : "log-likelihood";
        return "Note: the " + what + " has a second, higher maximum at tau^2 = " + Fmt.num(alt, 4) + " (higher by " + (gain < 1e-3 ? String.format(java.util.Locale.ROOT, "%.1e", gain) : Fmt.num(gain, 3)) + "). The results above and below use the selected mode, tau^2 = " + Fmt.num(tau2, 4)
            + " (the one found by the usual search from zero, as in metafor); the higher mode is not used in any calculation. With so few studies or such unequal variances tau^2 is poorly determined: compare with another estimator (for example DL, PM or SJ) and read the heterogeneity results with caution." + NL;
    }

    static String hetText(MetaResult r, Pool p, MetaSpec s) {
        StringBuilder b = new StringBuilder(); int lv = (int) Math.round(s.levelHetstat * 100);
        if (p.k < 2) return b.toString();
        if (s.random && !p.lrp) {
            b.append("Quantifying heterogeneity (with ").append(lv).append("% CIs):").append(NL);
            b.append(" tau^2 = ").append(Fmt.num(p.tau2, 4)); if (!s.isGlmm() && !Double.isNaN(p.seTau2)) b.append(" (SE = ").append(Fmt.num(p.seTau2, 4)).append(")"); if (!Double.isNaN(p.loTau2)) b.append(" [").append(p.loTau2Sign).append(Fmt.num(p.loTau2, 4)).append("; ").append(p.upTau2Sign).append(Fmt.num(p.upTau2, 4)).append("]");
            b.append("; tau = ").append(Fmt.num(p.tau, 4)); if (!Double.isNaN(p.loTau)) b.append(" [").append(p.loTau2Sign).append(Fmt.num(p.loTau, 4)).append("; ").append(p.upTau2Sign).append(Fmt.num(p.upTau, 4)).append("]"); b.append(NL);
        } else b.append("Quantifying heterogeneity (with ").append(lv).append("% CIs):").append(NL);
        b.append(" I^2 = ").append(Fmt.num(100 * p.I2, 1)).append("% [").append(Fmt.num(100 * p.loI2, 1)).append("%; ").append(Fmt.num(100 * p.upI2, 1)).append("%]; H = ")
         .append(Fmt.num(p.H, 2)).append(" ").append(Fmt.ci(p.loH, p.upH, 2)).append(NL);
        if (s.random) b.append(modeNote(p.altTau2, p.altGain, p.tau2, s.methodTau));
        if (p.glmm && !p.lrp && s.random && !Double.isNaN(p.I2tau))   // metafor's definition next to the Q-based one of meta
            b.append(" I^2 = ").append(Fmt.num(100 * p.I2tau, 1)).append("% (tau^2-based, as in metafor); H = ").append(Fmt.num(p.Htau, 2)).append(NL);
        b.append(NL);
        b.append("Test of heterogeneity:").append(NL);
        if (p.glmm && !p.lrp) b.append(table(List.of("Q", "d.f.", "p-value", "Test"), List.<String[]>of(new String[]{Fmt.num(p.Q, 2), String.valueOf(p.dfQ), Fmt.pval(p.pQ, 4), "Wald-type"}, new String[]{Fmt.num(p.qLRT, 2), String.valueOf(p.dfQ), Fmt.pval(p.pQLRT, 4), "Likelihood-Ratio"}), new boolean[]{true, true, true, false})).append(NL);
        else b.append(table(List.of("Q", "d.f.", "p-value"), List.<String[]>of(new String[]{Fmt.num(p.Q, 2), String.valueOf(p.dfQ), Fmt.pval(p.pQ, 4)}), new boolean[]{true, true, true})).append(NL);
        return b.toString();
    }

    public static String subgroupText(MetaResult r) {
        MetaSpec s = r.spec; StringBuilder b = new StringBuilder(); int d = 4;
        String nm = s.subgroupName.isEmpty() ? "subgroup" : s.subgroupName;
        b.append("Results for subgroups (").append(s.common && s.random ? "common effect model | random effects model" : s.random ? "random effects model" : "common effect model").append("):").append(NL);
        List<String> head = new ArrayList<>(List.of("", "k", smLabel(s), Fmt.num(s.levelMa * 100, 0) + "% CI"));
        if (s.random) { head.add("tau^2"); head.add("tau"); } head.add("Q"); head.add("I^2");
        boolean piCol = s.random; /* PI of each subgroup (random effects only) */ if (piCol) head.add(pct(s.levelPredict) + "% PI");
        List<String[]> rows = new ArrayList<>();
        for (int g = 0; g < r.sgLevels.size(); g++) {
            Pool p = r.sgPools.get(g); List<String> row = new ArrayList<>();
            row.add(nm + s.sepSubgroup + r.sgLevels.get(g)); row.add(String.valueOf(p.k));
            if (s.random && s.common) {   // both models selected: one row per model (as meta prints them), Q and I^2 once, on the common-effect row
                List<String> cr = new ArrayList<>(row); cr.set(0, row.get(0) + " (common)"); cr.add(Fmt.num(bt(s, p.teCommon, p), d)); cr.add(Fmt.ci(bt(s, p.loCommon, p), bt(s, p.upCommon, p), d)); cr.add(""); cr.add(""); cr.add(Fmt.num(p.Q, 2)); cr.add(Fmt.num(100 * p.I2, 1) + "%"); cr.add(""); rows.add(cr.toArray(new String[0]));
                row.set(0, row.get(0) + " (random)");
            }
            if (s.random) { row.add(Fmt.num(bt(s, p.teRandom, p), d)); row.add(Fmt.ci(bt(s, p.loRandom, p), bt(s, p.upRandom, p), d)); row.add(Fmt.num(p.tau2, 4)); row.add(Fmt.num(p.tau, 4)); }
            else { row.add(Fmt.num(bt(s, p.teCommon, p), d)); row.add(Fmt.ci(bt(s, p.loCommon, p), bt(s, p.upCommon, p), d)); }
            if (s.random && s.common) { row.add(""); row.add(""); } else { row.add(Fmt.num(p.Q, 2)); row.add(Fmt.num(100 * p.I2, 1) + "%"); }
            if (piCol) row.add(Double.isNaN(p.loPred) ? "NA" : Fmt.ci(bt(s, p.loPred, p), bt(s, p.upPred, p), d)); rows.add(row.toArray(new String[0]));
        }
        boolean[] right = new boolean[head.size()]; Arrays.fill(right, true); right[0] = false;
        b.append(table(head, rows, right)).append(NL);
        boolean both = s.random && s.common;
        b.append("Test for subgroup differences (").append(both ? "common effect model | random effects model" : s.random ? "random effects model" : "common effect model").append("):").append(NL);
        List<String[]> tr = new ArrayList<>();
        if (!s.random || both) tr.add(new String[]{both ? "Between groups (common)" : "Between groups", Fmt.num(r.qbCommon, 2), String.valueOf(r.dfBetween), Fmt.pval(r.pQbCommon, 4)});
        if (s.random) tr.add(new String[]{both ? "Between groups (random)" : "Between groups", Fmt.num(r.qbRandom, 2), String.valueOf(r.dfBetween), Fmt.pval(r.pQbRandom, 4)});
        b.append(table(List.of("", "Q", "d.f.", "p-value"), tr, new boolean[]{false, true, true, true})).append(NL);
        { List<String> nm0 = new ArrayList<>(); for (String l : r.sgLevels) nm0.add(nm + s.sepSubgroup + l); b.append(se0Note(s, nm0, r.sgPools)); }
        if (s.random && !r.qbRandomNote.isEmpty()) b.append("Note: the test for subgroup differences is ").append(r.qbRandomNote).append(".").append(NL);
        return b.toString();
    }

    public static String details(MetaResult r) {
        MetaSpec s = r.spec; Pool p = r.overall; StringBuilder b = new StringBuilder();
        b.append("Details of meta-analysis methods:").append(NL);
        switch (s.method) {
            case "MH": b.append("- Mantel-Haenszel method").append(NL); break; case "Peto": b.append("- Peto method").append(NL); break; case "Cochran": b.append("- Cochran method").append(NL); break;
            case "SSW": b.append("- Sample size method").append(NL); break;
            case "LRP": b.append("- Penalised logistic regression model"); if (s.random && !Double.isNaN(p.phi)) b.append(" (sqrt(phi) = ").append(Fmt.num(Math.sqrt(p.phi), 4)).append(")"); b.append(NL); break;
            case "GLMM": b.append(glmmText(s)).append(NL); break;
            default: b.append("- Inverse variance method").append(NL); }
        if (s.isLrp()) { /* no tau^2 */ }
        else if (s.isGlmm()) {
            if (s.random) b.append(s.modelGlmm.equals("UM.RS") && s.type.equals("bin") || s.modelGlmm.equals("UM.RS") && s.type.equals("inc") ? "- Maximum-likelihood estimator for tau^2 (Laplace approximation)" : "- Maximum-likelihood estimator for tau^2 (adaptive Gauss-Hermite quadrature, nAGQ = " + s.nAGQ + ")").append(NL);
            if (s.methodRandomCi.equals("HK")) b.append("- Wald-type t-test (df = ").append(p.k - 1).append(") for random effects model").append(NL);
        } else if (s.random) {
            b.append("- ").append(tauLabel(s.methodTau)).append(" estimator for tau^2").append(MetaSpec.isGenq(s.methodTau) ? " (weights: " + s.genqWeights + ")" : "").append(NL);
            if (!p.tauCiMethod.isEmpty()) b.append("- ").append(p.tauCiMethod.equals("QP") ? "Q-profile" : p.tauCiMethod.equals("BJ") ? "Biggerstaff-Jackson" : p.tauCiMethod.equals("GENQ") ? "Generalised Q-statistic (weights of the " + s.methodTau + " estimator)" : p.tauCiMethod.equals("PL") ? "Profile-likelihood (" + (s.methodTau.equals("REML") ? "REML" : "ML") + " likelihood of the model fitted with the " + s.methodTau + " estimator)" : p.tauCiMethod.equals("HT") ? "Higgins-Thompson" : p.tauCiMethod.equals("Wald") ? "Wald-type (SE of tau^2 of the " + s.methodTau + " estimator)" : p.tauCiMethod.equals("Wald.log") ? "Wald-type, log scale (SE of tau^2 of the " + s.methodTau + " estimator)" : p.tauCiMethod.equals("Wald.sqrt") ? "Wald-type, square-root scale (SE of tau^2 of the " + s.methodTau + " estimator)" : "Jackson").append(" method for confidence interval of tau^2 and tau").append(NL);
            if (s.methodRandomCi.equals("KR")) b.append("- Kenward-Roger adjustment for random effects model (df = ").append(Fmt.num(p.dfKR, 2)).append(")").append(NL);
            if (s.methodRandomCi.equals("HK")) b.append("- Hartung-Knapp adjustment for random effects model (df = ").append(p.k - 1).append(")").append(NL);
        }
        b.append("- Calculation of I^2 based on ").append(s.isGlmm() ? "Wald-type Q" : s.isLrp() ? "Q" : s.methodI2.equals("Q") ? "Q" : "tau^2").append(NL);
        if (s.isGlmm() && s.random && !Double.isNaN(p.I2tau)) b.append("- Calculation of I^2 (metafor) based on tau^2 / (tau^2 + typical sampling variance)").append(NL);
        if ((s.prediction || s.predictionSubgroup) && !Double.isNaN(p.loPred) && !p.predLabel.isEmpty()) b.append("- Prediction interval based on ").append(p.predLabel).append(NL);
        if (!s.type.equals("gen") && s.incr > 0 && (s.type.equals("bin") || s.type.equals("inc") || s.type.equals("prop") || s.type.equals("rate")))
            b.append("- Continuity correction of ").append(s.incr).append(" in studies ").append(s.methodIncr.equals("all") ? "(all studies)" : s.methodIncr.equals("if0all") ? "(all studies if any study has zero cell)" : "with zero cell frequencies").append(NL);
        if (s.type.equals("bin") && s.sm.equals("RR") && !s.rrCochrane) { }
        return b.toString();
    }

    public static String biasText(Bias.Result b, MetaResult r) {
        StringBuilder sb = new StringBuilder();
        if (b.error != null) return b.error + NL;
        if ("metafor".equals(b.pkg)) return biasTextMetafor(b, r);
        sb.append(b.label).append(NL).append(NL);
        sb.append("Test result: ").append(b.statName).append(" = ").append(Fmt.num(b.statistic, 2)).append(", ");
        if (b.df != null) sb.append("df = ").append(b.df.intValue()).append(", ");
        sb.append("p-value ").append(Fmt.pvalEq(b.pval, 4)).append(NL);
        sb.append("Bias estimate: ").append(Fmt.num(b.bias, 4)).append(" (SE = ").append(Fmt.num(b.seBias, 4)).append(")").append(NL);
        if (!b.varModel.isEmpty()) {
            sb.append(NL).append("Details:").append(NL);
            sb.append("- ").append(b.varModel).append(" residual heterogeneity variance (tau^2 = ").append(Fmt.num(b.tau * b.tau, 4)).append(")").append(NL);
            if (b.varModel.equals("additive")) sb.append("- ").append(tauLabel(b.methodTau)).append(" estimator").append(NL);
            String resp = r.spec.sm.isEmpty() ? "effect" : (r.spec.isRatioSm() ? "log " + longSm(r.spec.sm) : longSm(r.spec.sm));
            sb.append("- response:    ").append(b.methodBias.equals("Harbord") ? "score-based effect estimate" : resp).append(NL);
            String pred = b.methodBias.equals("Egger") || b.methodBias.equals("Thompson") || b.methodBias.equals("Harbord") ? "standard error" : b.methodBias.equals("Peters") ? "inverse of total sample size" : b.methodBias.equals("Macaskill") ? "total sample size" : b.methodBias.equals("Deeks") ? "inverse of square root of effective sample size" : "square root of the sum of inverse sample sizes";
            sb.append("- predictor:   ").append(pred).append(NL).append("- weight:      inverse variance").append(NL);
            sb.append("- reference:   ").append(b.methodBias.equals("Egger") ? "Egger et al. (1997), BMJ" : b.methodBias.equals("Thompson") ? "Thompson and Sharp (1999), Stat Med" : b.methodBias.equals("Harbord") ? "Harbord et al. (2006), Stat Med" : b.methodBias.equals("Peters") ? "Peters et al. (2006), JAMA" : b.methodBias.equals("Macaskill") ? "Macaskill et al. (2001), Stat Med" : b.methodBias.equals("Deeks") ? "Deeks et al. (2005), J Clin Epidemiol" : "Pustejovsky and Rodgers (2019), Res Synth Methods").append(NL);
        }
        if (!b.note.isEmpty()) sb.append(NL).append("Note: ").append(b.note).append(NL);
        return sb.toString();
    }

    /** Report of the metafor versions of the funnel plot asymmetry tests (regtest / ranktest). */
    static String biasTextMetafor(Bias.Result b, MetaResult r) {
        StringBuilder sb = new StringBuilder(); boolean ratio = r.spec.isRatioSm();
        sb.append(b.label).append(NL).append(NL);
        if (b.methodBias.equals("Begg")) {
            sb.append("Kendall's tau = ").append(Fmt.num(b.statistic, 4)).append(", p-value ").append(Fmt.pvalEq(b.pval, 4)).append(NL);
            sb.append("Kendall's S (concordant - discordant pairs) = ").append(Fmt.num(b.kendallS, 0)).append(NL);
            sb.append(NL).append("Details:").append(NL);
            sb.append("- p-value: ").append(b.exactP ? "exact (no ties among the standardised effects or the sampling variances)" : "normal approximation with tie correction (ties present, so the exact p-value cannot be computed)").append(NL);
            sb.append("- rank correlation between the standardised effects (around the fixed-effect estimate) and the sampling variances").append(NL);
            sb.append("- reference:   Begg and Mazumdar (1994), Biometrics").append(NL);
            return sb.toString();
        }
        sb.append("Test result: ").append(b.statName).append(" = ").append(Fmt.num(b.statistic, 2)).append(", ");
        if (b.df != null) sb.append("df = ").append(b.df.intValue()).append(", ");
        sb.append("p-value ").append(Fmt.pvalEq(b.pval, 4)).append(NL);
        String pn = b.predictor.equals("sei") ? "standard error" : b.predictor.equals("ni") ? "total sample size" : b.predictor.equals("ninv") ? "inverse of total sample size" : b.predictor.equals("sqrtninv") && b.methodBias.equals("Deeks") ? "inverse of square root of effective sample size" : "inverse of square root of sample size";
        sb.append("Bias estimate (coefficient of the predictor): ").append(Fmt.num(b.bias, 4)).append(" (SE = ").append(Fmt.num(b.seBias, 4)).append(")").append(NL);
        if (!Double.isNaN(b.limitEst)) {
            sb.append("Limit estimate (intercept, effect at infinite precision): ").append(Fmt.num(b.limitEst, 4)).append(" [").append(Fmt.num(b.limitLo, 4)).append("; ").append(Fmt.num(b.limitHi, 4)).append("]");
            if (ratio) sb.append(" (log scale; ").append(Fmt.num(Math.exp(b.limitEst), 4)).append(" [").append(Fmt.num(Math.exp(b.limitLo), 4)).append("; ").append(Fmt.num(Math.exp(b.limitHi), 4)).append("])");
            sb.append(NL);
        }
        sb.append(NL).append("Details:").append(NL);
        if (b.model.equals("lm")) sb.append("- model:       weighted regression with multiplicative dispersion term (model = \"lm\"), residual SD = ").append(Fmt.num(b.tau, 4)).append(NL);
        else {
            if (b.methodTau.equals("FE")) sb.append("- model:       fixed-effects meta-regression (model = \"rma\", method = \"FE\": common-effect analysis)").append(NL);
            else sb.append("- model:       mixed-effects meta-regression (model = \"rma\"), ").append(tauLabel(b.methodTau)).append(" estimator, tau^2 = ").append(Fmt.num(b.tau * b.tau, 4)).append(NL);
            sb.append("- test:        ").append(b.testKind.equals("z") ? "Wald-type z-test" : b.testKind.equals("knha") ? "Knapp-Hartung t-test (test = \"knha\")" : b.testKind.equals("adhoc") ? "Knapp-Hartung t-test with the scale factor set to at least 1 (test = \"adhoc\")" : b.testKind.equals("adhoc-ci") ? "Knapp-Hartung t-test with ad hoc correction \"ci\" (classic SE and z-test for the coefficient where the Hartung-Knapp interval is narrower than the classic interval)" : "Knapp-Hartung t-test with ad hoc correction \"IQWiG6\" (classic SE for the coefficient where the Hartung-Knapp interval is narrower than the DerSimonian-Laird-based interval)").append(NL);
        }
        String resp = r.spec.sm.isEmpty() ? "effect" : (ratio ? "log " + longSm(r.spec.sm) : longSm(r.spec.sm));
        sb.append("- response:    ").append(resp).append(NL);
        sb.append("- predictor:   ").append(pn).append(" (predictor = \"").append(b.predictor).append("\")").append(NL).append("- weight:      inverse variance").append(NL);
        String ref = b.methodBias.equals("Egger") ? "Egger et al. (1997), BMJ" : b.methodBias.equals("Thompson") ? "Thompson and Sharp (1999), Stat Med; Sterne and Egger (2005)" : b.methodBias.equals("Macaskill") ? "Macaskill et al. (2001), Stat Med" : b.methodBias.equals("Peters") ? "Peters et al. (2006), JAMA" : b.methodBias.equals("Deeks") ? "Deeks et al. (2005), J Clin Epidemiol" : "Pustejovsky and Rodgers (2019), Res Synth Methods";
        sb.append("- reference:   ").append(ref).append(NL);
        if (b.methodBias.equals("Peters") || b.methodBias.equals("Deeks")) sb.append("- note:        metafor weights by the sampling variance of the chosen effect measure; the (meta) version uses the variance defined by the original authors.").append(NL);
        return sb.toString();
    }

    /** Text output for the Doi plot / LFK index. */
    public static String lfkText(Lfk l, MetaResult r) {
        StringBuilder sb = new StringBuilder();
        if (l.lfk != l.lfk) return (l.note.isEmpty() ? "LFK index could not be computed (all study effects identical?)." : l.note) + NL;
        boolean ratio = r.spec.isRatioSm(); int d = 4;
        sb.append("LFK index test (Furuya-Kanamori et al., 2018; as implemented in metasens::lfkindex)").append(NL).append(NL);
        sb.append("LFK index: ").append(Fmt.num(l.lfk, 2)).append(" (").append(l.interpretation).append(")").append(NL);
        sb.append("Number of studies: ").append(l.k).append(NL);
        sb.append("Apex of the Doi plot (smallest |Z-score|): ").append(l.label[l.apex]).append(NL);
        sb.append("Interpretation: |LFK| <= 1 no asymmetry; 1 < |LFK| <= 2 minor asymmetry; |LFK| > 2 major asymmetry.").append(NL);
        sb.append("Negative values indicate asymmetry towards smaller effects (left limb), positive values towards larger effects.").append(NL);
        if (!l.note.isEmpty()) sb.append("Note: ").append(l.note).append(NL);
        sb.append(NL); int w = 5; for (String x : l.label) w = Math.max(w, x.length());
        StringBuilder tb = new StringBuilder(); List<String[]> lrows = new ArrayList<>();
        tb.append(Fmt.padR("Study", w)).append("  ").append(Fmt.pad(ratio ? "log " + smLabel(r.spec) : smLabel(r.spec), 12)).append(Fmt.pad("SE", 10)).append(Fmt.pad("Z-score", 10)).append(Fmt.pad("|Z-score|", 11)).append(NL);
        for (int j = 0; j < l.k; j++) { tb.append(Fmt.padR(l.label[j], w)).append("  ").append(Fmt.pad(Fmt.num(l.te[j], d), 12)).append(Fmt.pad(Fmt.num(l.se[j], d), 10)).append(Fmt.pad(Fmt.num(l.zscore[j], 3), 10)).append(Fmt.pad(Fmt.num(l.absZ[j], 3), 11)).append(j == l.apex ? "  <- apex" : "").append(NL);
            lrows.add(new String[]{l.label[j], Fmt.num(l.te[j], d), Fmt.num(l.se[j], d), Fmt.num(l.zscore[j], 3), Fmt.num(l.absZ[j], 3), j == l.apex ? "apex" : ""}); }
        sb.append(tb);
        recordManual(tb.toString(), List.of("Study", ratio ? "log " + smLabel(r.spec) : smLabel(r.spec), "SE", "Z-score", "|Z-score|", ""), lrows, new boolean[]{false, true, true, true, true, false});
        return sb.toString();
    }

    static String longSm(String sm) {
        switch (sm) { case "HR": return "hazard ratio"; case "OR": return "odds ratio"; case "RR": return "risk ratio"; case "MD": return "mean difference"; case "SMD": return "standardised mean difference"; case "IRR": return "incidence rate ratio"; default: return sm; }
    }

    public static String regText(Reg g, MetaResult r, int modelNo, String formula) {
        StringBuilder b = new StringBuilder(); int k = g.k;
        b.append("Mixed-Effects Model (k = ").append(k).append("; tau^2 estimator: ").append(g.method).append(")").append(NL).append(NL);
        b.append(table(List.of("logLik", "deviance", "AIC", "BIC", "AICc"), List.<String[]>of(new String[]{Fmt.num(g.logLik, 4), Fmt.num(g.deviance, 4), Fmt.num(g.aic, 4), Fmt.num(g.bic, 4), Fmt.num(g.aicc, 4)}), new boolean[]{true, true, true, true, true})).append(NL);
        b.append("tau^2 (estimated amount of residual heterogeneity):     ").append(Fmt.num(g.tau2, 4)); if (!Double.isNaN(g.seTau2)) b.append(" (SE = ").append(Fmt.num(g.seTau2, 4)).append(")"); b.append(NL);
        b.append("tau (square root of estimated tau^2 value):             ").append(Fmt.num(g.tau, 4)).append(NL);
        b.append(modeNote(g.altTau2, g.altGain, g.tau2, g.method));
        b.append("I^2 (residual heterogeneity / unaccounted variability): ").append(Fmt.num(g.I2, 2)).append("%").append(NL);
        b.append("H^2 (unaccounted variability / sampling variability):   ").append(Fmt.num(g.H2, 2)).append(NL);
        if (!Double.isNaN(g.R2)) b.append("R^2 (amount of heterogeneity accounted for):            ").append(Fmt.num(g.R2, 2)).append("%").append(NL);
        b.append(NL).append("Test for Residual Heterogeneity:").append(NL).append("QE(df = ").append(g.dfQE).append(") = ").append(Fmt.num(g.QE, 4)).append(", p-val ").append(Fmt.pvalEq(g.pQE, 4).replace("= ", "= ").replace("< 0.0001", "< .0001")).append(NL).append(NL);
        if (g.dfQM > 0) { b.append("Test of Moderators (coefficient").append(g.dfQM > 1 || !g.intercept ? "s" : "").append(" ").append(coefIdx(g)).append("):").append(NL);
            if (!g.qmChi) b.append("F(df1 = ").append(g.dfQM).append(", df2 = ").append((int) g.dfT).append(") = ").append(Fmt.num(g.QM, 4)).append(", p-val ").append(Fmt.pval(g.pQM, 4)).append(NL);
            else b.append("QM(df = ").append(g.dfQM).append(") = ").append(Fmt.num(g.QM, 4)).append(", p-val = ").append(Fmt.num(g.pQM, 4)).append(NL); b.append(NL); }
        b.append("Model Results:").append(NL).append(NL);
        List<String> head = new ArrayList<>(List.of("", "estimate", "se", g.knha ? "tval" : "zval", "pval", "ci.lb", "ci.ub", ""));
        List<String[]> rows = new ArrayList<>();
        for (int a = 0; a < g.p; a++) rows.add(new String[]{g.coefNames[a], Fmt.num(g.est[a], 4), Fmt.num(g.se[a], 4), Fmt.num(g.zval[a], 4), Fmt.num(g.pval[a], 4), Fmt.num(g.lb[a], 4), Fmt.num(g.ub[a], 4), stars(g.pval[a])});
        b.append(table(head, g.withReferenceRows(head, rows), new boolean[]{false, true, true, true, true, true, true, false})).append(NL);
        b.append("---").append(NL).append("Signif. codes:  0 '***' 0.001 '**' 0.01 '*' 0.05 '.' 0.1 ' ' 1").append(NL).append(NL);
        b.append(adhocNote(g));
        b.append("Note: Estimates and confidence intervals are on the ").append(r.spec.isRatioSm() ? "Log " + capital(longSm(r.spec.sm)) : r.spec.sm).append(" scale.").append(NL);
        return b.toString();
    }

    /** Note on the ad hoc correction of the Hartung-Knapp adjustment in a meta-regression (empty without Hartung-Knapp or without correction). */
    public static String adhocNote(Reg g) {
        if (!g.knha || g.adhoc == null || g.adhoc.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        if (g.adhoc.equals("se")) return b.append("Ad hoc correction \"se\": Knapp-Hartung scale factor floored at 1 (s^2 = ").append(Fmt.num(g.s2, 4)).append(").").append(NL).toString();
        boolean ci = g.adhoc.equals("ci"); List<String> hit = new ArrayList<>();
        for (int a = 0; a < g.p; a++) if (g.adjCoef != null && g.adjCoef[a]) hit.add(g.coefNames[a]);
        b.append("Ad hoc correction \"").append(g.adhoc).append("\": ").append(ci ? "where the Hartung-Knapp interval is narrower than the classic (z) interval, the classic SE and the z distribution are used"
            : "where the Hartung-Knapp interval is narrower than the DerSimonian-Laird-based classic interval, the classic SE (t distribution) replaces the Hartung-Knapp SE").append(". ");
        b.append(hit.isEmpty() ? "Rule not triggered for any coefficient: results identical to the conventional Hartung-Knapp adjustment." : "Triggered for: " + String.join(", ", hit) + ".");
        if (g.dfQM > 0 && g.qmClassic) b.append(" Test of moderators: classic covariance matrix (").append(g.qmChi ? "chi-square" : "F").append(").");
        return b.append(NL).toString();
    }

    static String capital(String s) { StringBuilder b = new StringBuilder(); for (String w : s.split(" ")) b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" "); return b.toString().trim(); }
    static String coefIdx(Reg g) { int from = g.intercept ? 2 : 1; return g.dfQM == 1 ? String.valueOf(from) : from + ":" + g.p; }
    static String stars(double p) { return p < 0.001 ? "***" : p < 0.01 ? "**" : p < 0.05 ? "*" : p < 0.1 ? "." : ""; }

    /**
     * p-value of a pooled result for the leave-one-out / cumulative tables and forest plots. A degenerate random-effects result (SE = 0) is flagged instead of printing
     * "< 0.0001" (which reads as strong evidence) or a bare NA: "0 (degenerate)" when the statistic is infinite, "undefined (0/0)" when the estimate equals the null effect.
     * Every other result keeps the usual format (plot = true: the forest-plot style "< 0.0001").
     */
    public static String pooledP(Pool p, boolean random, boolean plot) {
        if (random && p.randomSe0()) return Double.isNaN(p.pRandom) ? "undefined (0/0)" : "0 (degenerate)";
        double pv = random ? p.pRandom : p.pCommon;
        return plot ? Fmt.num(pv, 4).replace("0.0000", "< 0.0001") : Fmt.pval(pv, 4);
    }

    /** Note under a results table: random-effects results whose standard error is exactly 0 (plain Hartung-Knapp with q = 0). Empty when there is none. */
    static String se0Note(MetaSpec s, List<String> names, List<Pool> pools) {
        if (!s.random) return "";
        List<String> z = new ArrayList<>(); for (int i = 0; i < pools.size() && i < names.size(); i++) if (pools.get(i).k > 0 && pools.get(i).randomSe0()) z.add(names.get(i));
        if (z.isEmpty()) return "";
        return "Note: the random-effects standard error is 0 for " + String.join(", ", z) + " (Hartung-Knapp with q = 0: all weighted study effects are identical). The estimate is valid, but its confidence interval has zero width" + (s.hasNull() ? " and its test statistic and p-value are not available (NA)." : ".") + NL + NL;
    }

    /** Explanation of the degenerate rows of a leave-one-out / cumulative analysis (empty when no row is degenerate). */
    public static String degenerateNote(MetaSpec s, List<String> labels, List<Pool> pools, Pool overall) {
        if (!s.random) return "";
        List<String> inf = new ArrayList<>(), nul = new ArrayList<>();
        for (int i = 0; i < pools.size(); i++) if (pools.get(i).randomSe0()) (Double.isNaN(pools.get(i).pRandom) ? nul : inf).add(labels.get(i));
        if (overall != null && overall.randomSe0()) (Double.isNaN(overall.pRandom) ? nul : inf).add(s.textRandom);
        if (inf.isEmpty() && nul.isEmpty()) return "";
        List<String> all = new ArrayList<>(inf); all.addAll(nul);
        if (!s.hasNull()) return "Note: the random-effects standard error is 0 for " + String.join(", ", all) + " (Hartung-Knapp with q = 0: all weighted study effects are identical). The estimate is valid, but its confidence interval has zero width.";
        StringBuilder b = new StringBuilder("Note: the random-effects standard error is 0 for " + String.join(", ", all) + " (Hartung-Knapp with q = 0: all weighted study effects are identical). The estimate is valid, but its confidence interval has zero width. ");
        if (!inf.isEmpty()) b.append("For ").append(String.join(", ", inf)).append(" the test statistic is infinite and the p-value is 0 only as a numerical limit. ");
        if (!nul.isEmpty()) b.append("For ").append(String.join(", ", nul)).append(" the estimate equals the null effect, so the test statistic is 0/0 and the p-value is undefined. ");
        return b.append("These p-values are marked degenerate and must not be read as evidence of precision.").toString();
    }

    public static String looText(MetaResult r) {
        MetaSpec s = r.spec; StringBuilder b = new StringBuilder(); int d = 4;
        b.append("Leave-One-Out Meta-Analysis Summary").append(NL).append(NL);
        List<String> head = new ArrayList<>(List.of("", s.random ? "" : "", smLabel(s), s.levelMa == 0.95 ? "95% CI" : Fmt.num(s.levelMa * 100, 0) + "% CI", "p-value"));
        head.remove(1); if (!s.hasNull()) head.remove("p-value");   // no null hypothesis (single proportions / means / rates without a target): no test, no p-value
        if (s.random) { head.add("tau^2"); head.add("tau"); } head.add("I^2");
        if (s.prediction && s.random) head.add(pct(s.levelPredict) + "% PI");
        List<String[]> rows = new ArrayList<>();
        for (int i = 0; i < r.loo.size(); i++) {
            Pool p = r.loo.get(i); List<String> row = new ArrayList<>(); row.add(r.looLabels.get(i));
            double te = s.random ? p.teRandom : p.teCommon, lo = s.random ? p.loRandom : p.loCommon, up = s.random ? p.upRandom : p.upCommon, pv = s.random ? p.pRandom : p.pCommon;
            row.add(Fmt.num(bt(s, te, p), d)); row.add(Fmt.ci(bt(s, lo, p), bt(s, up, p), d)); if (s.hasNull()) row.add(pooledP(p, s.random, false));
            if (s.random) { row.add(Fmt.num(p.tau2, 4)); row.add(Fmt.num(p.tau, 4)); } row.add(Fmt.num(100 * p.I2, 1) + "%");
            if (s.prediction && s.random) row.add(Fmt.ci(bt(s, p.loPred, p), bt(s, p.upPred, p), d));
            rows.add(row.toArray(new String[0]));
        }
        List<String> row = new ArrayList<>(); Pool p = r.overall; row.add(s.random ? s.textRandom : s.textCommon);
        double te = s.random ? p.teRandom : p.teCommon, lo = s.random ? p.loRandom : p.loCommon, up = s.random ? p.upRandom : p.upCommon, pv = s.random ? p.pRandom : p.pCommon;
        row.add(Fmt.num(bt(s, te, p), d)); row.add(Fmt.ci(bt(s, lo, p), bt(s, up, p), d)); if (s.hasNull()) row.add(pooledP(p, s.random, false));
        if (s.random) { row.add(Fmt.num(p.tau2, 4)); row.add(Fmt.num(p.tau, 4)); } row.add(Fmt.num(100 * p.I2, 1) + "%"); if (s.prediction && s.random) row.add(Fmt.ci(bt(s, p.loPred, p), bt(s, p.upPred, p), d));
        boolean[] right = new boolean[head.size()]; Arrays.fill(right, true); right[0] = false;
        MUTE.set(Boolean.TRUE); String t1, t2;
        try { t1 = table(head, rows, right); t2 = table(head, List.<String[]>of(row.toArray(new String[0])), right).split("\n", 2)[1]; } finally { MUTE.remove(); }
        List<String[]> all = new ArrayList<>(rows); all.add(row.toArray(new String[0]));
        b.append(t1).append(NL); // overall row separately
        b.append(t2).append(NL).append(NL);
        recordManual(t1 + NL + t2, head, all, right);
        String dn = degenerateNote(s, r.looLabels, r.loo, r.overall); if (!dn.isEmpty()) b.append(dn).append(NL).append(NL);
        b.append(details(r));
        return b.toString();
    }

    public static String cumText(MetaResult r) {
        MetaSpec s = r.spec; StringBuilder b = new StringBuilder(); int d = 4;
        b.append("Cumulative Meta-Analysis Summary").append(NL).append(NL);
        List<String> head = new ArrayList<>(List.of("", smLabel(s), Fmt.num(s.levelMa * 100, 0) + "% CI", "p-value"));
        if (!s.hasNull()) head.remove("p-value");
        if (s.random) { head.add("tau^2"); head.add("tau"); } head.add("I^2");
        if (s.prediction && s.random) head.add(pct(s.levelPredict) + "% PI");   // as in the leave-one-out table (and the cumulative forest plot, which draws the prediction rows)
        List<String[]> rows = new ArrayList<>();
        for (int i = 0; i < r.cum.size(); i++) {
            Pool p = r.cum.get(i); List<String> row = new ArrayList<>(); row.add(r.cumLabels.get(i));
            double te = s.random ? p.teRandom : p.teCommon, lo = s.random ? p.loRandom : p.loCommon, up = s.random ? p.upRandom : p.upCommon, pv = s.random ? p.pRandom : p.pCommon;
            row.add(Fmt.num(bt(s, te, p), d)); row.add(Fmt.ci(bt(s, lo, p), bt(s, up, p), d)); if (s.hasNull()) row.add(pooledP(p, s.random, false));
            if (s.random) { row.add(Fmt.num(p.tau2, 4)); row.add(Fmt.num(p.tau, 4)); } row.add(Fmt.num(100 * p.I2, 1) + "%");
            if (s.prediction && s.random) row.add(Double.isNaN(p.loPred) ? "NA" : Fmt.ci(bt(s, p.loPred, p), bt(s, p.upPred, p), d));
            rows.add(row.toArray(new String[0]));
        }
        boolean[] right = new boolean[head.size()]; Arrays.fill(right, true); right[0] = false;
        b.append(table(head, rows, right)).append(NL);
        String dn = degenerateNote(s, r.cumLabels, r.cum, null); if (!dn.isEmpty()) b.append(dn).append(NL).append(NL);
        b.append(details(r));
        return b.toString();
    }

    public static String influenceText(Influence in) {
        StringBuilder b = new StringBuilder(); int d = 4;
        b.append("Outlier and Influence Diagnostics (Viechtbauer & Cheung, 2010)").append(NL).append(NL);
        List<String> head = new ArrayList<>(List.of("", "rstudent", "dffits", "cook.d", "cov.r", "tau2.del", "QE.del", "hat", "weight"));
        for (String c : in.coefNames) head.add("dfb." + c); head.add("inf");
        List<String[]> rows = new ArrayList<>();
        for (int i = 0; i < in.k; i++) {
            List<String> row = new ArrayList<>(); row.add(in.labels[i]);
            row.add(Fmt.num(in.rstudent[i], d)); row.add(Fmt.num(in.dffits[i], d)); row.add(Fmt.num(in.cookD[i], d)); row.add(Fmt.num(in.covR[i], d)); row.add(Fmt.num(in.tau2Del[i], d)); row.add(Fmt.num(in.QEdel[i], d)); row.add(Fmt.num(in.hat[i], d)); row.add(Fmt.num(in.weight[i], d));
            for (int a = 0; a < in.p; a++) row.add(Fmt.num(in.dfbs[i][a], d)); row.add(in.flagged[i] ? "*" : ""); rows.add(row.toArray(new String[0]));
        }
        boolean[] right = new boolean[head.size()]; Arrays.fill(right, true); right[0] = false; right[head.size() - 1] = false;
        b.append(table(head, rows, right)).append(NL);
        b.append("Flagged (*) as in metafor::influence: |dffits| > 3*sqrt(p/(k-p)), pchisq(cook.d, p) > 0.5, hat > 3p/k, any |dfbs| > 1 (rstudent and cov.r are shown but not used)").append(NL);
        b.append("Note on rstudent (not used for flagging):").append(NL);
        b.append("  (a) The dotted lines at +/-").append(Fmt.num(Dist.qnorm(0.975), 2)).append(" in the plot are unadjusted per-study limits: each study is compared with them separately, with no correction for looking at all k studies.").append(NL);
        b.append("  (b) The simulations in the CHANGELOG used a different, family-wise criterion: at least one of the k studies beyond the normal Bonferroni limit qnorm(1 - 0.025/k), intended to be 5% over all k studies. That limit is not drawn in the plot and is not applied here.").append(NL);
        b.append("  In simulated data sets without any outlier (empirical results of the tested scenarios), random-effects residuals with tau^2 > 0 exceeded +/-").append(Fmt.num(Dist.qnorm(0.975), 2)).append(" in about 6-8% of the studies (5% expected), and at least one of 12-30 studies passed the Bonferroni limit in about 12-23% of the data sets (5% intended; more with skewed random effects), because tau^2 is estimated. Use rstudent as a screening aid, not as a formal outlier test.").append(NL);
        for (int i = 0; i < in.k; i++) if (in.flagged[i]) b.append("  ").append(in.labels[i]).append(": ").append(in.flagReason[i]).append(NL);
        return b.toString();
    }

    public static String trimfillText(TrimFill tf, MetaResult r) {
        StringBuilder b = new StringBuilder(); MetaSpec s = r.spec; int d = 4;
        if (!s.title.isEmpty()) b.append("Review:     ").append(s.title).append(NL).append(NL);
        List<String> head = new ArrayList<>(List.of("", smLabel(s), Fmt.num(s.levelMa * 100, 0) + "% CI")); List<String[]> rows = new ArrayList<>();
        Pool p = tf.pool;
        rows.add(new String[]{"Number of studies combined: k = " + (tf.TE.length) + " (with " + tf.k0 + " added studies)", "", ""});
        b.append("Number of studies combined: k = ").append(tf.TE.length).append(" (with ").append(tf.k0).append(" added ").append(tf.k0 == 1 ? "study" : "studies").append(")").append(NL).append(NL);
        List<String[]> rr = new ArrayList<>();
        if (s.common) rr.add(new String[]{"Common effect model", Fmt.num(bt(s, p.teCommon, p), d), Fmt.ci(bt(s, p.loCommon, p), bt(s, p.upCommon, p), d)});
        rr.add(new String[]{"Random effects model", Fmt.num(bt(s, p.teRandom, p), d), Fmt.ci(bt(s, p.loRandom, p), bt(s, p.upRandom, p), d)});
        b.append(table(head, rr, new boolean[]{false, true, true})).append(NL);
        b.append("Quantifying heterogeneity:").append(NL).append(" tau^2 = ").append(Fmt.num(p.tau2, 4)).append("; tau = ").append(Fmt.num(p.tau, 4)).append("; I^2 = ").append(Fmt.num(100 * p.I2, 1)).append("%; H = ").append(Fmt.num(p.H, 2)).append(NL).append(NL);
        b.append("Test of heterogeneity:").append(NL).append(table(List.of("Q", "d.f.", "p-value"), List.<String[]>of(new String[]{Fmt.num(p.Q, 2), String.valueOf(p.dfQ), Fmt.pval(p.pQ, 4)}), new boolean[]{true, true, true})).append(NL);
        b.append("Details on adjustment for publication bias:").append(NL).append("- Trim-and-fill method to adjust for funnel plot asymmetry (" + tf.type + "0 estimator)").append(NL);
        b.append("- Model used to estimate the centre: ").append(tf.model).append(NL);
        b.append("- Estimated number of missing studies on the ").append(tf.left ? "left" : "right").append(" side: ").append(tf.k0).append(" (SE = ").append(Fmt.num(tf.seK0, 4)).append(")").append(NL);
        if (!Double.isNaN(tf.pK0)) b.append("- Test of H0: no missing studies on the ").append(tf.left ? "left" : "right").append(" side: p-value = ").append(Fmt.pval(tf.pK0, 4)).append(NL);
        return b.toString();
    }

    /** Right/left aligned text table with a rule under the header, like R's print.data.frame/prmatrix. */
    /** Cell text that is drawn as a dash centred in its column (reference-category rows). */
    public static final String DASH = "\u0001";

    public static String table(List<String> head, List<String[]> rows, boolean[] right) {
        int n = head.size(); int[] w = new int[n];
        for (int j = 0; j < n; j++) { w[j] = head.get(j).length(); for (String[] r : rows) w[j] = Math.max(w[j], j < r.length ? (DASH.equals(r[j]) ? 1 : r[j].length()) : 0); }
        StringBuilder b = new StringBuilder(); StringBuilder line = new StringBuilder();
        for (int j = 0; j < n; j++) { line.append(right[j] ? Fmt.pad(head.get(j), w[j]) : Fmt.padR(head.get(j), w[j])); if (j < n - 1) line.append(' '); }
        b.append(line).append(NL);
        List<String[]> shown = new ArrayList<>();
        for (String[] r : rows) { line.setLength(0); String[] sr = r.clone();
            for (int j = 0; j < n; j++) { String c = j < r.length ? r[j] : "";
                if (DASH.equals(c)) { int left = (w[j] - 1) / 2; c = " ".repeat(left) + "-" + " ".repeat(w[j] - 1 - left); line.append(c); if (j < n - 1) line.append(' '); if (j < sr.length) sr[j] = "\u2014"; continue; }
                line.append(right[j] ? Fmt.pad(c, w[j]) : Fmt.padR(c, w[j])); if (j < n - 1) line.append(' '); }
            b.append(line).append(NL); shown.add(sr); }
        record(b.toString(), head, shown, right);
        return b.toString();
    }
}
