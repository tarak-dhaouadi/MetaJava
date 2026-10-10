package org.metajava.core;

import java.util.*;

/** Text output for the permutation test, the model comparison and the GOSH analysis. */
public final class RegReport {
    private RegReport() {}
    static final String NL = "\n";

    static String stars(double p) { return p < 0.001 ? "***" : p < 0.01 ? "**" : p < 0.05 ? "*" : p < 0.1 ? "." : ""; }
    static String pv(double p) { return Double.isNaN(p) ? "NA" : p < 0.0001 ? "<.0001" : Fmt.num(p, 4); }

    public static String permText(Reg g, Perm t, MetaResult r) {
        StringBuilder b = new StringBuilder();
        b.append("Permutation Test for Meta-Regression (as metafor::permutest)").append(NL).append(NL);
        if (t.exact) b.append("Exact test: all ").append(t.nPerm).append(" permutations of the k = ").append(t.k).append(" rows of the model matrix.").append(NL);
        else b.append("Random test: ").append(t.iter - t.failed).append(" random permutations of the rows of the model matrix (seed = ").append(t.seed).append(").").append(NL)
            .append("p-value = (number of permutations with |statistic| >= observed + 1) / (permutations + 1) (Phipson and Smyth, 2010); smallest possible p = ").append(Fmt.num(1.0 / (t.iter - t.failed + 1), 4)).append(".").append(NL);
        b.append("tau^2 estimator: ").append(g.method).append(" (re-estimated in every permutation); test statistics: ").append(t.knha ? "t / F (Knapp-Hartung)" : "z / QM").append(NL);
        if (t.failed > 0) b.append("Note: ").append(t.failed).append(" permutations could not be fitted and were left out.").append(NL);
        if (!t.note.isEmpty()) b.append("Note: ").append(t.note).append(NL);
        b.append(NL).append("Test of Moderators (coefficient").append(t.dfQM > 1 || !g.intercept ? "s" : "").append(" ").append(g.intercept ? (t.dfQM == 1 ? "2" : "2:" + t.p) : (t.dfQM == 1 ? "1" : "1:" + t.p)).append("):").append(NL);
        if (t.knha) b.append("F(df1 = ").append(t.dfQM).append(", df2 = ").append(t.k - t.p).append(") = ").append(Fmt.num(t.QM, 4)).append(", p-val* = ").append(pv(t.pQM)).append("  (asymptotic p-val = ").append(pv(g.pQM)).append(")").append(NL);
        else b.append("QM(df = ").append(t.dfQM).append(") = ").append(Fmt.num(t.QM, 4)).append(", p-val* = ").append(pv(t.pQM)).append("  (asymptotic p-val = ").append(pv(g.pQM)).append(")").append(NL);
        b.append(NL).append("Model Results:").append(NL).append(NL);
        List<String> head = List.of("", "estimate", "se", t.knha ? "tval" : "zval", "pval*", "ci.lb", "ci.ub", "", "pval (asympt.)");
        List<String[]> rows = new ArrayList<>();
        for (int a = 0; a < t.p; a++) rows.add(new String[]{t.coefNames[a], Fmt.num(g.est[a], 4), Fmt.num(g.se[a], 4), Fmt.num(g.zval[a], 4), pv(t.pval[a]), Fmt.num(g.lb[a], 4), Fmt.num(g.ub[a], 4), stars(t.pval[a]), pv(g.pval[a])});
        b.append(Report.table(head, rows, new boolean[]{false, true, true, true, true, true, true, false, true})).append(NL);
        b.append("---").append(NL).append("Signif. codes:  0 '***' 0.001 '**' 0.01 '*' 0.05 '.' 0.1 ' ' 1").append(NL).append(NL);
        b.append("* p-values based on permutation; standard errors and confidence limits are the ordinary (model-based) ones.").append(NL);
        b.append("Note: Estimates and confidence intervals are on the ").append(r.spec.isRatioSm() ? "log " + r.spec.sm : r.spec.sm).append(" scale.").append(NL);
        return b.toString();
    }

    public static String compareText(ModelCompare c, MetaResult r) {
        StringBuilder b = new StringBuilder(); MetaSpec s = r.spec;
        b.append("Model Comparison for Meta-Regression (k = ").append(c.k).append(" studies with complete data)").append(NL).append(NL);
        b.append(c.allSubsets ? "All subsets of the " + c.terms.size() + " selected term" + (c.terms.size() == 1 ? "" : "s") + " are fitted to the same studies, sorted by AICc." : c.note).append(NL);
        b.append("logLik, AIC, AICc and BIC come from maximum-likelihood fits (the restricted likelihood of REML cannot be compared between models with different fixed effects).").append(NL);
        b.append("tau^2, I^2 and R^2 use the selected estimator (").append(c.estimator).append("); R^2 = 100 (tau^2 of the intercept-only model - tau^2) / tau^2 of the intercept-only model, truncated at 0.").append(NL);
        b.append("p = number of coefficients; npar = p + 1 (tau^2); delta and w: differences to the best model and Akaike weights based on AICc; LRT: likelihood-ratio test against the intercept-only model.").append(NL).append(NL);
        List<String> head = new ArrayList<>(List.of("Model", "p", "tau^2", "I^2", "R^2", "logLik", "AIC", "AICc", "BIC", "dAICc", "dBIC", "w(AICc)"));
        if (c.hasNull) { head.add("LRT"); head.add("LRT p"); }
        List<String[]> rows = new ArrayList<>();
        for (ModelCompare.Row w : c.rows) {
            List<String> row = new ArrayList<>(List.of((w.best ? "* " : "  ") + w.label, String.valueOf(w.p), Fmt.num(w.tau2, 4), Fmt.num(w.I2, 1) + "%", Double.isNaN(w.R2) ? "" : Fmt.num(w.R2, 1) + "%", Fmt.num(w.ll, 3), Fmt.num(w.aic, 2), Fmt.num(w.aicc, 2), Fmt.num(w.bic, 2), Fmt.num(w.dAICc, 2), Fmt.num(w.dBIC, 2), Fmt.num(w.weight, 3)));
            if (c.hasNull) { row.add(Double.isNaN(w.lrt) ? "" : Fmt.num(w.lrt, 3) + " (df " + w.lrtDf + ")"); row.add(Double.isNaN(w.lrtP) ? "" : pv(w.lrtP)); }
            rows.add(row.toArray(new String[0]));
        }
        boolean[] right = new boolean[head.size()]; Arrays.fill(right, true); right[0] = false;
        b.append(Report.table(head, rows, right)).append(NL);
        b.append("* best model by AICc").append(NL);
        if (!c.drops.isEmpty()) {
            b.append(NL).append("Likelihood-ratio tests for each term (full model against the full model without the term; maximum likelihood):").append(NL).append(NL);
            List<String[]> dr = new ArrayList<>(); for (ModelCompare.Drop d : c.drops) dr.add(new String[]{d.term, String.valueOf(d.df), Fmt.num(d.lrt, 3), pv(d.p), stars(d.p)});
            b.append(Report.table(List.of("Term dropped", "df", "LRT", "p-value", ""), dr, new boolean[]{false, true, true, true, false})).append(NL);
        }
        if (!c.note.isEmpty() && c.allSubsets) b.append(NL).append(c.note).append(NL);
        return b.toString();
    }

    /** Back-transformed value of a working-scale estimate (n = harmonic mean for Freeman-Tukey). */
    static double bt(MetaSpec s, double x, double n) { return Meta.back(s, x, n); }

    public static String goshText(Gosh g, MetaResult r) {
        MetaSpec s = r.spec; StringBuilder b = new StringBuilder(); double nh = r.overall.nHarm;
        b.append("GOSH Analysis (Graphical Display of Study Heterogeneity; Olkin et al., 2012; as metafor::gosh)").append(NL).append(NL);
        b.append("Random-effects model (inverse variance, tau^2 estimator ").append(g.method).append(") fitted to ").append(g.nSub).append(g.exhaustive ? " subsets (all subsets of at least 2 of the " : " random subsets (seed = " + g.seed + ") of the ").append(g.exhaustive ? g.k + " studies)." : g.k + " studies.").append(NL);
        b.append("Heterogeneity: I^2 ").append("tau2".equals(g.i2Def) ? "= tau^2 / (tau^2 + typical sampling variance)" : "= (Q - df) / Q").append(" (the setting of the main analysis).").append(NL);
        if (!"Inverse".equals(s.method)) b.append("Note: the study estimates and standard errors are pooled by inverse variance, whatever the pooling method of the main analysis (").append(s.method).append(").").append(NL);
        if (g.failed > 0) b.append("Note: ").append(g.failed).append(" subsets could not be fitted and were left out.").append(NL);
        b.append(NL).append("All ").append(g.k).append(" studies: estimate = ").append(Fmt.num(bt(s, g.fullEst, nh), 4)).append(", I^2 = ").append(Fmt.num(g.fullI2, 1)).append("%, tau^2 = ").append(Fmt.num(g.fullTau2, 4)).append(NL).append(NL);
        String[] nm = {"min", "2.5%", "25%", "50%", "75%", "97.5%", "max"}; double[] ps = {0, 0.025, 0.25, 0.5, 0.75, 0.975, 1};
        List<String> head = new ArrayList<>(); head.add("Over the subsets"); head.addAll(Arrays.asList(nm));
        List<String[]> rows = new ArrayList<>();
        String[] e = new String[8], i = new String[8], t = new String[8], z = new String[8]; e[0] = Report.smLabel(s) + (s.backtransf && !s.isRatioSm() ? "" : ""); i[0] = "I^2 (%)"; t[0] = "tau^2"; z[0] = "Number of studies";
        double[] sz = new double[g.nSub]; for (int q = 0; q < g.nSub; q++) sz[q] = g.size[q];
        for (int q = 0; q < 7; q++) { e[q + 1] = Fmt.num(bt(s, Gosh.quantile(g.est, ps[q]), nh), 4); i[q + 1] = Fmt.num(Gosh.quantile(g.i2, ps[q]), 1); t[q + 1] = Fmt.num(Gosh.quantile(g.tau2, ps[q]), 4); z[q + 1] = Fmt.num(Gosh.quantile(sz, ps[q]), 0); }
        rows.add(e); rows.add(i); rows.add(t); rows.add(z);
        boolean[] right = new boolean[8]; Arrays.fill(right, true); right[0] = false;
        b.append(Report.table(head, rows, right)).append(NL);
        int hi = 0, fin = 0; for (double x : g.i2) if (!Double.isNaN(x)) { fin++; if (x >= 50) hi++; }
        b.append("Subsets with I^2 >= 50%: ").append(Fmt.num(100.0 * hi / Math.max(1, fin), 1)).append("%").append(NL).append(NL);
        // per study
        Integer[] ord = new Integer[g.k]; for (int j = 0; j < g.k; j++) ord[j] = j;
        Arrays.sort(ord, (a, c) -> Double.compare(Math.abs(g.i2In[c] - g.i2Out[c]) , Math.abs(g.i2In[a] - g.i2Out[a])));   // NaN sorts as equal: fine
        int show = Math.min(g.k, 15);
        b.append("Mean over the subsets that include / exclude each study (").append(show == g.k ? "all studies" : "the " + show + " studies with the largest difference in I^2").append(", ordered by |difference|):").append(NL).append(NL);
        List<String[]> sr = new ArrayList<>();
        for (int q = 0; q < show; q++) { int j = ord[q];
            sr.add(new String[]{g.labels[j], Fmt.num(bt(s, g.estIn[j], nh), 4), Fmt.num(bt(s, g.estOut[j], nh), 4), Fmt.num(g.i2In[j], 1), Fmt.num(g.i2Out[j], 1), Fmt.num(g.i2In[j] - g.i2Out[j], 1)}); }
        b.append(Report.table(List.of("Study", "Estimate (in)", "Estimate (out)", "I^2 (in)", "I^2 (out)", "I^2 diff."), sr, new boolean[]{false, true, true, true, true, true})).append(NL);
        b.append("A study whose inclusion raises the mean I^2 by a large amount (and shifts the estimate) is a candidate source of heterogeneity; in the plot, separate clusters of subsets point to the same thing. These differences are descriptive and should be read together with the plot and with the outlier and influence diagnostics.").append(NL);
        if (g.clusters != null) { double nhh = nh; b.append(NL).append(clusterText(g.clusters, g.labels, new java.util.function.DoubleUnaryOperator[]{x -> bt(s, x, nhh), null}, new String[]{Report.smLabel(s), "I^2 (%)"})); }
        return b.toString();
    }

    /** Cluster detection section (ordinary and meta-regression GOSH). tf: optional transformation of the mean of each variable (null entry: none). */
    public static String clusterText(GoshCluster c, String[] labels, java.util.function.DoubleUnaryOperator[] tf, String[] varLabels) {
        StringBuilder b = new StringBuilder(); int d = c.d;
        b.append("Cluster detection").append(NL).append(NL);
        b.append("Gaussian mixture model (full covariance matrices, EM) fitted to the standardised ").append(String.join(", ", varLabels)).append(" of the ").append(c.nUsed).append(" valid subsets").append(c.nUsed > GoshCluster.FIT_MAX ? " (a random sample of " + GoshCluster.FIT_MAX + " subsets was used for fitting; every subset was then assigned to its most probable cluster)" : "").append("; the number of clusters (1 to ").append(GoshCluster.MAX_G).append(") is chosen by the integrated completed likelihood (ICL).").append(NL);
        List<String[]> ir = new ArrayList<>(); String[] ic = new String[GoshCluster.MAX_G + 1], bc = new String[GoshCluster.MAX_G + 1]; ic[0] = "ICL"; bc[0] = "BIC"; List<String> ih = new ArrayList<>(); ih.add("Clusters");
        for (int q = 0; q < GoshCluster.MAX_G; q++) { ih.add(String.valueOf(q + 1)); ic[q + 1] = Fmt.num(c.icl[q], 1); bc[q + 1] = Fmt.num(c.bic[q], 1); } ir.add(ic); ir.add(bc);
        boolean[] rr = new boolean[GoshCluster.MAX_G + 1]; Arrays.fill(rr, true); rr[0] = false; b.append(NL).append(Report.table(ih, ir, rr)).append(NL);
        if (c.note != null) b.append(c.note).append(NL);
        if (!c.found()) { if (c.G == 1) b.append("A single cluster means that no study, or small group of studies, splits the subsets into distinct groups; the heterogeneity is not driven by a subset of studies that the GOSH analysis can isolate.").append(NL); return b.toString(); }
        b.append("Clusters found: ").append(c.G).append(". A cluster is a group of subsets with a similar result, not a group of studies: one study appears in many subsets and in several clusters. Clusters and percentages describe the subsets actually analysed (all subsets or the random sample, see above).").append(NL).append(NL);
        List<String> head = new ArrayList<>(Arrays.asList("Cluster", "Subsets", "%", "Mean no. of studies")); for (int a = 0; a < d; a++) head.add("Mean " + varLabels[a]);
        List<String[]> rows = new ArrayList<>();
        for (int g = 0; g < c.G; g++) { List<String> r = new ArrayList<>(Arrays.asList(String.valueOf(g + 1), String.valueOf(c.size[g]), Fmt.num(c.share[g], 1), Fmt.num(c.meanSize[g], 1)));
            for (int a = 0; a < d; a++) { double v = c.mean[g][a]; if (tf != null && a < tf.length && tf[a] != null) v = tf[a].applyAsDouble(v); r.add(Fmt.num(v, a == d - 1 && varLabels[a].startsWith("I") ? 1 : 4)); } rows.add(r.toArray(new String[0])); }
        boolean[] right = new boolean[head.size()]; Arrays.fill(right, true); right[0] = false; b.append(Report.table(head, rows, right)).append(NL);
        List<String[]> sr = new ArrayList<>(); int shown = 0;
        for (int g = 0; g < c.G; g++) { int per = 0; for (int[] jj : c.separating(g)) { if (per >= 8) break; int j = jj[0]; double in = 100 * c.rateIn[g][j], out = 100 * c.rateOut[g][j];
            sr.add(new String[]{String.valueOf(g + 1), labels[j], Fmt.num(in, 0), Fmt.num(out, 0), in < out ? "mostly excluded" : "mostly included"}); per++; shown++; } }
        if (sr.isEmpty()) b.append("No single study is clearly associated with a cluster (inclusion rates differ by less than ").append(Fmt.num(100 * GoshCluster.SEP, 0)).append(" percentage points); the clusters reflect combinations of studies.").append(NL);
        else { b.append("Studies associated with a cluster (share of the subsets that include the study, inside the cluster and in all other clusters; heuristic threshold, not a significance test: difference >= ").append(Fmt.num(100 * GoshCluster.SEP, 0)).append(" percentage points; at most 8 per cluster):").append(NL).append(NL);
            b.append(Report.table(List.of("Cluster", "Study", "In cluster (%)", "In other clusters (%)", "Pattern"), sr, new boolean[]{false, false, true, true, false})).append(NL);
            b.append("A study that is almost always excluded from (or included in) a cluster is a potential candidate for an outlier or a source of heterogeneity; this is an association in the subsets, not proof of cause. Confirm with the outlier and influence diagnostics and by repeating the analysis without the study. Clusters are exploratory, not a formal test.").append(NL); }
        return b.toString();
    }

    /** Text output of the GOSH analysis of a meta-regression model. */
    public static String goshRegText(GoshReg g, MetaResult r) {
        MetaSpec s = r.spec; StringBuilder b = new StringBuilder(); int p = g.p; long nTot = (long) g.nTotal;
        b.append("GOSH Analysis for Meta-Regression (Olkin et al., 2012; as metafor::gosh for a model with moderators)").append(NL).append(NL);
        b.append("Model: mixed-effects meta-regression (tau^2 estimator ").append(g.method).append(") with ").append(p).append(" coefficients (").append(String.join(", ", g.coefNames)).append(") fitted to subsets of the ").append(g.k).append(" studies with complete data.").append(NL);
        b.append("Coefficients are on the analysis scale of the effect measure").append(s.isRatioSm() ? " (log scale for ratio measures)" : "").append("; residual heterogeneity: I^2 ").append("tau2".equals(g.i2Def) ? "= tau^2 / (tau^2 + typical sampling variance)" : "= (Q_E - df) / Q_E").append(" (the setting of the main analysis), df = k - p.").append(NL);
        if (!"Inverse".equals(s.method)) b.append("Note: the study estimates and standard errors are pooled by inverse variance, whatever the pooling method of the main analysis (").append(s.method).append(").").append(NL);
        b.append(NL).append("Subsets").append(NL);
        b.append("Possible subsets with at least ").append(p).append(" studies (sum of choose(").append(g.k).append(", m) for m = ").append(p).append("..").append(g.k).append("): ").append(g.nTotal < 1e15 ? String.format(Locale.ROOT, "%,d", nTot) : String.format(Locale.ROOT, "%.3e", g.nTotal)).append(NL);
        if (g.exhaustive) b.append("All possible subsets were analysed (exhaustive enumeration).").append(NL);
        else b.append("A random sample of ").append(String.format(Locale.ROOT, "%,d", g.nSub)).append(" subsets was analysed (seed = ").append(g.seed).append("), because the number of possible subsets exceeds the number requested; as in metafor, the size m of a subset is drawn with probability proportional to the Binomial(").append(g.k).append(", 1/2) mass on ").append(p).append("..").append(g.k).append(" and the studies are then drawn at random without replacement.").append(NL);
        b.append(NL);
        List<String[]> cr = new ArrayList<>();
        cr.add(new String[]{"Subsets analysed", String.format(Locale.ROOT, "%,d", g.nSub), "100.0"});
        cr.add(new String[]{"Valid (coefficients and residual heterogeneity estimable)", String.format(Locale.ROOT, "%,d", g.nValid), Fmt.num(100.0 * g.nValid / g.nSub, 1)});
        cr.add(new String[]{"Not estimable (rank-deficient model matrix)", String.format(Locale.ROOT, "%,d", g.nRankDef), Fmt.num(100.0 * g.nRankDef / g.nSub, 1)});
        cr.add(new String[]{"Saturated (k = p, no residual degrees of freedom)", String.format(Locale.ROOT, "%,d", g.nSaturated), Fmt.num(100.0 * g.nSaturated / g.nSub, 1)});
        cr.add(new String[]{"Model fit failed", String.format(Locale.ROOT, "%,d", g.nFailed), Fmt.num(100.0 * g.nFailed / g.nSub, 1)});
        b.append(Report.table(List.of("Subsets", "Number", "%"), cr, new boolean[]{false, true, true})).append(NL);
        b.append("Not estimable: too little variation in a moderator within the subset (for instance a covariate that is constant, or a factor level that is absent or confounded with another term), so that some coefficients cannot be estimated. Only valid subsets enter the summaries, the plots and the cluster detection; all summaries refer to the subsets actually analysed" + (g.exhaustive ? " (all possible subsets)." : " (a random sample, so rare patterns may be missing and results can change with the seed).") + "").append(NL);
        if (g.nValid < 0.5 * g.nSub) b.append("Warning (a prompt to look closer, not a validity threshold): fewer than half of the subsets are valid, so the summaries describe a selected part of the subsets.").append(NL);
        b.append(NL).append("Full model (all ").append(g.k).append(" studies): ");
        for (int a = 0; a < p; a++) b.append(a > 0 ? ", " : "").append(g.coefNames[a]).append(" = ").append(Fmt.num(g.fullCoef[a], 4)); b.append("; I^2 = ").append(Fmt.num(g.fullI2, 1)).append("%, tau^2 = ").append(Fmt.num(g.fullTau2, 4)).append(NL).append(NL);
        String[] nm = {"min", "2.5%", "25%", "50%", "75%", "97.5%", "max"}; double[] ps = {0, 0.025, 0.25, 0.5, 0.75, 0.975, 1};
        List<String> head = new ArrayList<>(); head.add("Over the valid subsets"); head.add("All studies"); head.addAll(Arrays.asList(nm)); head.add("Sign flips (%)");
        List<String[]> rows = new ArrayList<>();
        for (int a = 0; a <= p + 1; a++) { double[] x = a < p ? g.coef[a] : a == p ? g.i2 : g.tau2; double full = a < p ? g.fullCoef[a] : a == p ? g.fullI2 : g.fullTau2; int dg = a == p ? 1 : 4;
            String[] row = new String[10]; row[0] = a < p ? g.coefNames[a] : a == p ? "I^2 (%)" : "tau^2"; row[1] = Fmt.num(full, dg); for (int q = 0; q < 7; q++) row[q + 2] = Fmt.num(Gosh.quantile(x, ps[q]), dg);
            String flip = "-"; if (a < p && !(g.intercept && a == 0) && full != 0) { int opp = 0, tot = 0; for (int bb = 0; bb < g.nSub; bb++) if (g.status[bb] == GoshReg.OK) { tot++; if (Math.signum(x[bb]) == -Math.signum(full)) opp++; } flip = Fmt.num(100.0 * opp / Math.max(1, tot), 1); }
            row[9] = flip; rows.add(row); }
        boolean[] right = new boolean[10]; Arrays.fill(right, true); right[0] = false; b.append(Report.table(head, rows, right)).append(NL);
        b.append("Sign flips: exploratory sign-stability measure, the share of the valid subsets in which a moderator coefficient has the opposite sign to the one in the full model. It is not a test of the moderator and not the probability that the effect is positive or negative; it is sensitive to coefficients close to zero.").append(NL).append(NL);
        // per study
        int ns = Math.min(3, Math.max(0, p - (g.intercept ? 1 : 0))); int[] sc = new int[ns]; for (int q = 0; q < ns; q++) sc[q] = (g.intercept ? 1 : 0) + q;
        Integer[] ord = new Integer[g.k]; for (int j = 0; j < g.k; j++) ord[j] = j;
        Arrays.sort(ord, (x, y) -> Double.compare(Math.abs(g.i2In[y] - g.i2Out[y]), Math.abs(g.i2In[x] - g.i2Out[x])));
        int show = Math.min(g.k, 15);
        b.append("Mean over the valid subsets that include / exclude each study (").append(show == g.k ? "all studies" : "the " + show + " studies with the largest difference in I^2").append(", ordered by |difference|):").append(NL).append(NL);
        List<String> sh = new ArrayList<>(Arrays.asList("Study", "I^2 (in)", "I^2 (out)", "I^2 diff.")); for (int q : sc) sh.add("d " + g.coefNames[q]);
        List<String[]> sr = new ArrayList<>();
        for (int q = 0; q < show; q++) { int j = ord[q]; List<String> row = new ArrayList<>(Arrays.asList(g.labels[j], Fmt.num(g.i2In[j], 1), Fmt.num(g.i2Out[j], 1), Fmt.num(g.i2In[j] - g.i2Out[j], 1))); for (int a : sc) row.add(Fmt.num(g.coefIn[a][j] - g.coefOut[a][j], 4)); sr.add(row.toArray(new String[0])); }
        boolean[] sright = new boolean[sh.size()]; Arrays.fill(sright, true); sright[0] = false; b.append(Report.table(sh, sr, sright)).append(NL);
        if (ns > 0) b.append("d = mean coefficient over the subsets that include the study minus the mean over those that exclude it.").append(NL);
        b.append("Read the plots of the coefficients against the residual heterogeneity and the scatterplot matrix together with this table: a study whose exclusion removes the residual heterogeneity or reverses a coefficient is a candidate source of the heterogeneity or a high-leverage observation.").append(NL);
        if (g.clusters != null) { String[] vl = new String[p + 1]; for (int a = 0; a < p; a++) vl[a] = g.coefNames[a]; vl[p] = "I^2 (%)"; b.append(NL).append(clusterText(g.clusters, g.labels, null, vl)); }
        return b.toString();
    }
}
