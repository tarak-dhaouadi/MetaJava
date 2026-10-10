package org.metajava.core;

import java.util.*;

/** Text output (and exportable tables) for {@link PubBias}: PET-PEESE, selection model, fail-safe N. */
public final class BiasReport {
    private BiasReport() {}
    private static final String NL = "\n";
    private static final int D = 4, WIDTH = 100;

    /** Wraps long prose lines (not table lines) at about {@code width} characters, with a hanging indent for list items; used so that long sentences stay readable in the results panel. */
    public static String wrapLines(String text, int width) {
        StringBuilder out = new StringBuilder(); String[] lines = text.split("\n", -1);
        for (int li = 0; li < lines.length; li++) {
            String line = lines[li], body = line.trim(); if (li > 0) out.append('\n');
            if (line.length() <= width || (body.contains("   ") && !body.startsWith("- "))) { out.append(line); continue; }   // table rows (aligned columns) stay as they are; list items always wrap
            int lead = 0; while (lead < line.length() && line.charAt(lead) == ' ') lead++;
            String indent = " ".repeat(lead), hang = indent + (body.startsWith("- ") ? "  " : "");
            StringBuilder cur = new StringBuilder(indent); boolean empty = true;
            for (String w : body.split(" ")) {
                if (!empty && cur.length() + 1 + w.length() > width) { out.append(cur).append('\n'); cur = new StringBuilder(hang); empty = true; }
                if (!empty) cur.append(' ');
                cur.append(w); empty = false;
            }
            out.append(cur);
        }
        return out.toString();
    }

    private static double bt(MetaResult r, double x) { return Report.bt(r.spec, x, r.overall); }
    private static String effLabel(MetaResult r) { MetaSpec s = r.spec; return (s.isRatioSm() && !s.backtransf ? "log " : "") + Report.smLabel(s); }
    private static String scaleNote(MetaResult r) {
        MetaSpec s = r.spec;
        return s.isRatioSm() && s.backtransf ? "Regressions and the model are fitted on the log scale; effects are shown back-transformed (" + Report.smLabel(s) + ")." + NL
                : s.backtransf && !s.sm.isEmpty() && !s.isRatioSm() && !List.of("MD", "SMD", "RD", "ASD", "IRD", "IRSD", "ROM", "COR", "MRAW").contains(s.sm) ? "Effects are shown back-transformed to the original scale." + NL : "";
    }
    private static String ci(MetaResult r, double lo, double up) { double a = bt(r, lo), b = bt(r, up); return Fmt.ci(Math.min(a, b), Math.max(a, b), D); }

    public static String petPeeseText(PubBias.PetPeese p, MetaResult r) {
        if (p.error != null) return p.error + NL;
        StringBuilder b = new StringBuilder(); MetaSpec s = r.spec; String eff = effLabel(r); int lvl = (int) Math.round(s.levelMa * 100);
        b.append("PET-PEESE: precision-effect test and precision-effect estimate with standard error (Stanley & Doucouliagos, 2014)").append(NL).append(NL);
        b.append("Number of studies: k = ").append(p.k).append(NL);
        b.append("Weighted regression of the study effect on its standard error (PET) and on its variance (PEESE), weights = 1/variance.").append(NL);
        b.append("The intercept estimates the effect of a hypothetical study with infinite precision (standard error = 0), i.e. free of small-study effects.").append(NL).append(scaleNote(r)).append(NL);
        List<String> head = List.of("Model", "Corrected " + eff, lvl + "% CI", "p (vs null)", "Slope", "p (slope)");
        List<String[]> rows = new ArrayList<>(); PubBias.Reg[] rg = {p.pet, p.peese}; String[] nm = {"PET (effect ~ SE)", "PEESE (effect ~ SE^2)"};
        for (int i = 0; i < 2; i++) { PubBias.Reg g = rg[i]; rows.add(new String[]{nm[i], Fmt.num(bt(r, g.b0), D), ci(r, g.lo0, g.up0), Fmt.pval(g.p0Null, 4), Fmt.num(g.b1, D), Fmt.pval(g.p1, 4)}); }
        Pool o = r.overall; boolean rnd = s.random; double te = rnd ? o.teRandom : o.teCommon, lo = rnd ? o.loRandom : o.loCommon, up = rnd ? o.upRandom : o.upCommon, pv = rnd ? o.pRandom : o.pCommon;
        rows.add(new String[]{rnd ? "Random effects model (unadjusted)" : "Common effect model (unadjusted)", Fmt.num(bt(r, te), D), ci(r, lo, up), Fmt.pval(pv, 4), "", ""});
        b.append(Report.table(head, rows, new boolean[]{false, true, true, true, true, true})).append(NL);
        b.append("Slope of PET: ").append(Fmt.num(p.pet.b1, D)).append(" (SE = ").append(Fmt.num(p.pet.se1, D)).append("), t = ").append(Fmt.num(p.pet.t1, 2)).append(", df = ").append(p.pet.df).append(", p ").append(Fmt.pvalEq(p.pet.p1, 4))
         .append(" - a significant slope indicates small-study effects / funnel plot asymmetry (this is the Egger regression test).").append(NL).append(NL);
        if (p.decisionApplied) {
            b.append("Conditional estimate (PET-PEESE decision rule, alpha = ").append(Fmt.num(p.alpha, 2)).append("): ").append(NL);
            b.append("  PET intercept ").append(p.usePeese ? "differs significantly" : "does not differ significantly").append(" from the null value (p ").append(Fmt.pvalEq(p.pet.p0Null, 4)).append("), so ")
             .append(p.usePeese ? "PEESE" : "PET").append(" is used.").append(NL);
            b.append("  Bias-corrected effect: ").append(Fmt.num(bt(r, p.estimate), D)).append(" ").append(ci(r, p.lower, p.upper)).append(" (").append(p.usePeese ? "PEESE" : "PET").append(")").append(NL);
        } else b.append("This effect measure has no natural null value, so the PET-PEESE decision rule is not applied; interpret PET and PEESE side by side.").append(NL);
        b.append(NL).append("Notes:").append(NL).append("- The residual variance is estimated (multiplicative error model); confidence limits use the t distribution with k - 2 degrees of freedom.").append(NL);
        b.append("- PET underestimates a true non-zero effect and PEESE overestimates a true zero effect; both can be misleading when heterogeneity is large or when the bias is not related to precision.").append(NL);
        if (!p.note.isEmpty()) b.append("- ").append(p.note).append(NL);
        return wrapLines(b.toString(), WIDTH);
    }

    private static String delta(double d) { return d < 0.001 ? "< 0.001" : Fmt.num(d, 3); }

    public static String selectionText(PubBias.Selection sm, MetaResult r) {
        if (sm.error != null) return sm.error + NL;
        StringBuilder b = new StringBuilder(); MetaSpec s = r.spec; String eff = effLabel(r); int lvl = (int) Math.round(s.levelMa * 100);
        b.append("Selection model for publication bias: three-parameter selection model (Vevea & Hedges, 1995)").append(NL).append(NL);
        b.append("Number of studies: k = ").append(sm.k).append(NL);
        b.append("Random-effects model in which studies with a statistically significant result (one-sided p < .025, i.e. z > ").append(Fmt.num(sm.cutZ, 3)).append(") are published with relative probability 1, and the others with relative probability delta.").append(NL);
        b.append("Favoured direction: ").append(sm.direction).append(".  Studies significant in this direction: ").append(sm.kSig).append(";  not significant (or in the other direction): ").append(sm.kNon).append(".").append(NL).append(scaleNote(r)).append(NL);
        List<String> head = List.of("Model", eff, lvl + "% CI", "tau^2", "delta"); List<String[]> rows = new ArrayList<>(); Pool o = r.overall; boolean rnd = s.random;
        rows.add(new String[]{rnd ? "Random effects model (main analysis)" : "Common effect model (main analysis)", Fmt.num(bt(r, rnd ? o.teRandom : o.teCommon), D), ci(r, rnd ? o.loRandom : o.loCommon, rnd ? o.upRandom : o.upCommon), Fmt.num(o.tau2, D), "1 (fixed)"});
        rows.add(new String[]{"Selection model (adjusted)", Fmt.num(bt(r, sm.mu), D), sm.converged ? ci(r, sm.muLo, sm.muUp) : "not found", Fmt.num(sm.tau2, D), delta(sm.delta)});
        b.append(Report.table(head, rows, new boolean[]{false, true, true, true, true})).append(NL);
        b.append("Confidence interval of the adjusted effect: profile likelihood.").append(NL);
        b.append("Likelihood ratio test of selection (delta = 1 vs. free delta, ML estimation): chi^2 = ").append(Fmt.num(sm.lrt, 3)).append(", df = 1, p ").append(Fmt.pvalEq(sm.pLrt, 4)).append(NL);
        b.append("Log-likelihood: ").append(Fmt.num(sm.ll, 3)).append(" (selection model), ").append(Fmt.num(sm.ll0, 3)).append(" (no selection; ML estimate ").append(Fmt.num(bt(r, sm.mu0), D)).append(", tau^2 = ").append(Fmt.num(sm.tau20, D)).append(")").append(NL).append(NL);
        b.append("Interpretation: delta < 1 means that non-significant results are less likely to be published than significant ones; the adjusted effect is the estimate corrected for that selection.").append(NL);
        if (!sm.note.isEmpty()) b.append("Caution: ").append(sm.note).append(NL);
        return wrapLines(b.toString(), WIDTH);
    }

    public static String failSafeText(PubBias.FailSafe f, MetaResult r) {
        if (f.error != null) return f.error + NL;
        StringBuilder b = new StringBuilder();
        b.append("Fail-safe N (file drawer analysis)").append(NL).append(NL);
        b.append("Number of studies: k = ").append(f.k).append(NL);
        b.append("Number of unpublished studies with an average effect of zero (the null value) that would be needed to bring the combined result to non-significance (one-tailed alpha = ").append(Fmt.num(f.alpha, 2)).append(", z = ").append(Fmt.num(f.zAlpha, 3)).append(").").append(NL).append(NL);
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Rosenthal (1979)", "sum z = " + Fmt.num(f.sumZ, 3) + ", Stouffer z = " + Fmt.num(f.stouffer, 3) + ", p " + Fmt.pvalEq(f.pStouffer, 4), String.format(Locale.ROOT, "%.0f", f.nRosenthal)});
        rows.add(new String[]{"Rosenberg (2005)", "common-effect z = " + Fmt.num(f.zFixed, 3) + ", p " + Fmt.pvalEq(f.pFixed, 4), String.format(Locale.ROOT, "%.0f", f.nRosenberg)});
        b.append(Report.table(List.of("Method", "Combined test", "Fail-safe N"), rows, new boolean[]{false, false, true})).append(NL);
        b.append("Rosenthal's tolerance level 5k + 10 = ").append(String.format(Locale.ROOT, "%.0f", f.tolerance)).append(": ");
        b.append(f.nRosenthal > f.tolerance ? "the fail-safe N exceeds it, so the result is considered robust to the file drawer problem (by this rule)." : "the fail-safe N does not exceed it, so the result may not be robust to the file drawer problem (by this rule).").append(NL).append(NL);
        b.append("Caution: the fail-safe N does not estimate bias and does not adjust the effect; it assumes that all missing studies have exactly zero effect and ignores study precision and heterogeneity. Use it as a rough descriptive measure only, next to the methods above.").append(NL);
        return wrapLines(b.toString(), WIDTH);
    }
}
