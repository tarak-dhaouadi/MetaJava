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
        return b.toString();
    }
}
