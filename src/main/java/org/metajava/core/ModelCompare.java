package org.metajava.core;

import java.util.*;

/**
 * Model comparison for a meta-regression: every subset of the selected terms (covariates, factors) is fitted on the same studies
 * (complete cases of all terms). Information criteria and likelihood-ratio tests come from maximum-likelihood fits (the
 * restricted likelihood of REML is not comparable between models with different fixed effects); tau^2, I^2 and R^2 are those of
 * the selected tau^2 estimator, R^2 = 100 (tau^2_null - tau^2) / tau^2_null as in metafor (truncated at 0).
 */
public final class ModelCompare {
    public static final int MAX_TERMS_ALL_SUBSETS = 6;

    public static final class Row {
        public String label; public int mask, p, parms; public double tau2, I2, R2, ll, aic, aicc, bic, dAIC, dAICc, dBIC, weight = Double.NaN, lrt = Double.NaN, lrtP = Double.NaN; public int lrtDf;
        public double tau2ML; public boolean best;
    }
    public static final class Drop { public String term; public int df; public double lrt, p; }

    public List<Row> rows = new ArrayList<>(); public List<Drop> drops = new ArrayList<>(); public List<String> terms; public int k;
    public String estimator, note = ""; public boolean hasNull, allSubsets;

    public static ModelCompare run(MetaResult r, List<String> nums, Map<String, double[]> nd, List<String> facs, Map<String, String[]> fd, boolean intercept) {
        MetaSpec s = r.spec; ModelCompare out = new ModelCompare(); out.estimator = s.methodTau; out.hasNull = intercept;
        List<String> terms = new ArrayList<>(nums); terms.addAll(facs); out.terms = terms; int T = terms.size();
        final int[] rowsUsed = Reg.completeCases(r, nums, nd, facs, fd); final int k = rowsUsed.length; out.k = k;
        if (k < 3) throw new IllegalArgumentException("Model comparison needs at least 3 studies with complete data.");
        // masks to fit
        List<Integer> masks = new ArrayList<>(); out.allSubsets = T <= MAX_TERMS_ALL_SUBSETS;
        if (out.allSubsets) { for (int m = intercept ? 0 : 1; m < (1 << T); m++) masks.add(m); }
        else { if (intercept) masks.add(0); for (int t = 0; t < T; t++) masks.add(1 << t); masks.add((1 << T) - 1); for (int t = 0; t < T; t++) masks.add(((1 << T) - 1) & ~(1 << t));
            out.note = "More than " + MAX_TERMS_ALL_SUBSETS + " terms: only the null model, each term alone, the full model and the full model without one term are compared (not all subsets)."; }
        final Row[] res = new Row[masks.size()]; final boolean[] skip = new boolean[masks.size()];
        final double[] null0 = new double[1];   // tau^2 of the intercept-only model (selected estimator)
        if (intercept) { double[][] X0 = new double[k][1]; for (double[] q : X0) q[0] = 1; double[] y = new double[k], v = new double[k]; for (int j = 0; j < k; j++) { y[j] = r.TE[rowsUsed[j]]; v[j] = r.seTE[rowsUsed[j]] * r.seTE[rowsUsed[j]]; } null0[0] = Rma.fit(y, v, X0, s.methodTau, Pool.genqWeightsFor(s, rowsUsed)).tau2; }
        final List<Integer> mk = masks;
        Par.forRange(masks.size(), q -> {
            int m = mk.get(q); List<String> nn = new ArrayList<>(), ff = new ArrayList<>(), lab = new ArrayList<>();
            for (int t = 0; t < T; t++) if ((m >> t & 1) == 1) { if (t < nums.size()) nn.add(terms.get(t)); else ff.add(terms.get(t)); lab.add(terms.get(t)); }
            Reg.Design D = Reg.design(r, nn, nd, ff, fd, intercept, rowsUsed);
            if (D.p >= k) { skip[q] = true; return; }
            Row w = new Row(); w.mask = m; w.p = D.p; w.parms = D.p + 1;
            w.label = (lab.isEmpty() ? (intercept ? "(intercept only)" : "") : (intercept ? "~ " : "~ 0 + ") + String.join(" + ", lab));
            Rma ml = Rma.fit(D.y, D.v, D.X, "ML"); Reg.FitStats fs = Reg.fitStats(ml, false);
            w.ll = fs.ll; w.aic = fs.aic; w.aicc = fs.aicc; w.bic = fs.bic; w.tau2ML = ml.tau2;
            Rma fit = s.methodTau.equals("ML") ? ml : Rma.fit(D.y, D.v, D.X, s.methodTau, Pool.genqWeightsFor(s, D.rows));
            w.tau2 = fit.tau2; w.I2 = 100 * fit.tau2 / (fit.tau2 + Rma.typicalV(k, D.p, D.v, D.X));
            w.R2 = Double.NaN; if (intercept && D.p > 1) w.R2 = null0[0] > 0 ? Math.max(0, 100 * (null0[0] - fit.tau2) / null0[0]) : 0;
            res[q] = w;
        });
        Row nullRow = null; for (Row w : res) if (w != null && w.mask == 0 && intercept) nullRow = w;
        for (Row w : res) if (w != null) {
            if (nullRow != null && w != nullRow) { w.lrt = Math.max(0, 2 * (w.ll - nullRow.ll)); w.lrtDf = w.p - 1; w.lrtP = w.lrtDf > 0 ? Dist.pchisqUpper(w.lrt, w.lrtDf) : Double.NaN; }
            out.rows.add(w);
        }
        if (out.rows.isEmpty()) throw new IllegalArgumentException("No model could be fitted.");
        double minA = Double.POSITIVE_INFINITY, minC = Double.POSITIVE_INFINITY, minB = Double.POSITIVE_INFINITY;
        for (Row w : out.rows) { minA = Math.min(minA, w.aic); minC = Math.min(minC, w.aicc); minB = Math.min(minB, w.bic); }
        double sw = 0; for (Row w : out.rows) { w.dAIC = w.aic - minA; w.dAICc = w.aicc - minC; w.dBIC = w.bic - minB; sw += Math.exp(-0.5 * w.dAICc); }
        for (Row w : out.rows) { w.weight = Math.exp(-0.5 * w.dAICc) / sw; w.best = w.dAICc == 0; }
        out.rows.sort(Comparator.comparingDouble((Row w) -> w.aicc).thenComparingInt(w -> w.p));
        // drop-one likelihood ratio tests (full model against full model without one term), ML
        if (T >= 2) {
            Row full = null; for (Row w : out.rows) if (w.mask == (1 << T) - 1) full = w;
            if (full != null) for (int t = 0; t < T; t++) { int m = ((1 << T) - 1) & ~(1 << t); Row red = null; for (Row w : out.rows) if (w.mask == m) red = w;
                if (red == null) continue; Drop d = new Drop(); d.term = terms.get(t); d.df = full.p - red.p; d.lrt = Math.max(0, 2 * (full.ll - red.ll)); d.p = d.df > 0 ? Dist.pchisqUpper(d.lrt, d.df) : Double.NaN; out.drops.add(d); }
        }
        return out;
    }
}
