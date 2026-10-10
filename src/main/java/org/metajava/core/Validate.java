package org.metajava.core;

import java.util.*;

/**
 * Checks the table before it is analysed and explains every problem in plain words, with the row (and study label) concerned:
 * non-numeric cells, events greater than the total, negative or zero sample sizes / times, negative SDs, correlations outside [-1, 1],
 * non-positive standard errors, inconsistent confidence limits, duplicate study labels, duplicated rows, ...
 * Problems that make the analysis impossible or wrong are errors (the analysis is not run); the others are warnings
 * (the analysis runs, and the warnings are shown with the results). Row numbers count the data rows below the header line.
 */
public final class Validate {
    private Validate() {}

    public static final class Issue {
        public final boolean error; public final String text;
        Issue(boolean error, String text) { this.error = error; this.text = text; }
        @Override public String toString() { return (error ? "Error: " : "Warning: ") + text; }
    }

    /** Thrown when the data contain at least one error; carries every issue found (errors first). */
    public static final class Failed extends IllegalArgumentException {
        public final List<Issue> issues;
        Failed(List<Issue> issues) { super(summary(issues)); this.issues = issues; }
    }

    public static String summary(List<Issue> l) {
        StringBuilder b = new StringBuilder();
        for (Issue i : l) b.append("- ").append(i).append('\n');
        return b.toString().trim();
    }

    private static final int MAX_LISTED = 6;

    /**
     * @param cols       parameter key (event.e, n.e, TE, ...) -> name of the assigned column
     * @param names      parameter key -> readable name ("Events (experimental)")
     * @param studlab    name of the study label column, or null
     * @param covariates numeric covariate columns chosen for the meta-regression
     * @param genRatioCi generic data given as ratio + confidence limits (TE, lower, upper on the ratio scale)
     * @return the warnings (analysis can go on); throws {@link Failed} when there is at least one error
     */
    public static List<String> check(DataTable t, String type, Map<String, String> cols, Map<String, String> names, String studlab,
                                     List<String> covariates, boolean genRatioCi, String sm) {
        List<Issue> errs = new ArrayList<>(), warns = new ArrayList<>();
        final int n = t.nrow;
        final String[] lab = studlab == null ? null : t.strings(studlab);
        if (type.equals("gen") && !genRatioCi && cols.containsKey("seTE")) { cols = new LinkedHashMap<>(cols); cols.remove("lower"); cols.remove("upper"); }   // the confidence limits are not used when the standard error is given
        Set<String> required = required(type, cols.keySet(), genRatioCi);
        Map<String, double[]> v = new LinkedHashMap<>();

        // ---- 1. every assigned column: non-numeric and missing cells
        for (Map.Entry<String, String> e : cols.entrySet()) {
            String key = e.getKey(), colName = e.getValue(); String[] raw = t.col(colName); if (raw == null) continue;
            String disp = disp(key, colName, names); double[] x = new double[n]; List<String> bad = new ArrayList<>(), thousands = new ArrayList<>(); List<Integer> miss = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                String s = raw[i] == null ? "" : raw[i].trim();
                if (isMissing(s)) { x[i] = Double.NaN; miss.add(i); continue; }
                x[i] = DataTable.parse(s);
                if (Double.isNaN(x[i])) bad.add(row(lab, i) + " has \"" + s + "\"");
                else if (s.matches("[-+]?\\d{1,3}(,\\d{3})+")) thousands.add(row(lab, i) + " \"" + s + "\"");
            }
            v.put(key, x);
            if (!bad.isEmpty()) errs.add(new Issue(true, list("Column " + disp + " contains text where a number is expected", bad, "Use plain numbers (a decimal point or comma is accepted) and leave the cell empty, or write NA, when a value is unknown.")));
            if (!thousands.isEmpty()) warns.add(new Issue(false, list("Column " + disp + ": a comma is read as a decimal separator, so these values are read as small decimals, not thousands", thousands, "Remove thousands separators if that is not what you meant.")));
            if (!miss.isEmpty() && required.contains(key)) warns.add(new Issue(false, list("Column " + disp + " has no value", rows(lab, miss), "These studies cannot be used and are left out of the analysis.")));
        }

        // ---- 2. value ranges, by analysis type
        switch (type) {
            case "bin": counts(v, names, lab, errs, warns, "event.e", "n.e", null); counts(v, names, lab, errs, warns, "event.c", "n.c", null); break;
            case "prop": counts(v, names, lab, errs, warns, "event", "n", null); break;
            case "inc": counts(v, names, lab, errs, warns, "event.e", null, "time.e"); counts(v, names, lab, errs, warns, "event.c", null, "time.c"); break;
            case "rate": counts(v, names, lab, errs, warns, "event", null, "time"); break;
            case "cont":
                size(v, names, lab, errs, warns, "n.e", 1); size(v, names, lab, errs, warns, "n.c", 1);
                sd(v, names, lab, errs, warns, "sd.e"); sd(v, names, lab, errs, warns, "sd.c"); break;
            case "mean": size(v, names, lab, errs, warns, "n", 1); sd(v, names, lab, errs, warns, "sd"); break;
            case "cor": {
                double[] r = v.get("cor"), nn = v.get("n"); String dr = disp("cor", cols.get("cor"), names);
                if (r != null) { List<String> out = new ArrayList<>(), one = new ArrayList<>();
                    for (int i = 0; i < n; i++) { if (Double.isNaN(r[i])) continue; if (Math.abs(r[i]) > 1) out.add(row(lab, i) + ": " + fmt(r[i])); else if (Math.abs(r[i]) == 1) one.add(row(lab, i) + ": " + fmt(r[i])); }
                    if (!out.isEmpty()) errs.add(new Issue(true, list("Correlations must lie between -1 and 1, but column " + dr + " has", out, "")));
                    if (!one.isEmpty()) errs.add(new Issue(true, list("A correlation of exactly -1 or 1 has no finite standard error (column " + dr + ")", one, ""))); }
                if (nn != null) { boolean z = "ZCOR".equals(sm); int min = z ? 4 : 2; String dn = disp("n", cols.get("n"), names); List<String> low = new ArrayList<>();
                    for (int i = 0; i < n; i++) if (!Double.isNaN(nn[i]) && nn[i] < min) low.add(row(lab, i) + ": " + fmt(nn[i]));
                    if (!low.isEmpty()) errs.add(new Issue(true, list("Sample size must be at least " + min + (z ? " for the Fisher z transformation (variance 1/(n-3))" : "") + ", but column " + dn + " has", low, ""))); }
                break; }
            default: { // generic
                double[] se = v.get("seTE"), te = v.get("TE"), lo = v.get("lower"), up = v.get("upper");
                if (se != null) { List<String> bad = new ArrayList<>(); for (int i = 0; i < n; i++) if (!Double.isNaN(se[i]) && !(se[i] > 0)) bad.add(row(lab, i) + ": " + fmt(se[i]));
                    if (!bad.isEmpty()) errs.add(new Issue(true, list("Standard errors must be positive, but column " + disp("seTE", cols.get("seTE"), names) + " has", bad, "Check that this column holds standard errors, not variances or sample sizes, and no zeros."))); }
                if (lo != null && up != null) {
                    List<String> inv = new ArrayList<>(), outside = new ArrayList<>(), nonpos = new ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        if (Double.isNaN(lo[i]) || Double.isNaN(up[i])) continue;
                        if (lo[i] > up[i]) inv.add(row(lab, i) + ": lower " + fmt(lo[i]) + " > upper " + fmt(up[i]));
                        else if (te != null && !Double.isNaN(te[i]) && (te[i] < lo[i] || te[i] > up[i])) outside.add(row(lab, i) + ": " + fmt(te[i]) + " is not within [" + fmt(lo[i]) + "; " + fmt(up[i]) + "]");
                        if (genRatioCi && (lo[i] <= 0 || up[i] <= 0 || (te != null && !Double.isNaN(te[i]) && te[i] <= 0))) nonpos.add(row(lab, i));
                    }
                    if (!inv.isEmpty()) errs.add(new Issue(true, list("Lower confidence limit is larger than the upper limit", inv, "Check that the two columns are not swapped.")));
                    if (!nonpos.isEmpty()) errs.add(new Issue(true, list("The effect and its confidence limits must be positive when they are entered as ratios (they are log-transformed)", nonpos, "")));
                    if (!outside.isEmpty()) warns.add(new Issue(false, list("The effect estimate lies outside its own confidence interval", outside, genRatioCi ? "" : "Check the columns; for ratio measures enter the effect on the log scale, or choose the 'ratio + confidence limits' input mode.")));
                }
                size(v, names, lab, errs, warns, "n.e", 1); size(v, names, lab, errs, warns, "n.c", 1);
            }
        }

        // ---- 3. enough complete studies?
        int complete = 0; for (int i = 0; i < n; i++) { boolean ok = true; for (String k : required) { double[] x = v.get(k); if (x == null || Double.isNaN(x[i])) { ok = false; break; } } if (ok) complete++; }
        if (n == 0) errs.add(new Issue(true, "The table has no data rows."));
        else if (complete == 0) errs.add(new Issue(true, "No row has a value in every required column, so there is no study to analyse. Check the column assignment."));
        else if (complete == 1) warns.add(new Issue(false, "Only one study has all required values: a meta-analysis needs at least two studies to estimate heterogeneity."));

        // ---- 4. duplicate study labels and duplicated rows
        if (lab != null) {
            Map<String, List<Integer>> by = new LinkedHashMap<>();
            for (int i = 0; i < n; i++) if (!lab[i].isEmpty()) by.computeIfAbsent(lab[i], z -> new ArrayList<>()).add(i);
            List<String> dup = new ArrayList<>();
            for (Map.Entry<String, List<Integer>> e : by.entrySet()) if (e.getValue().size() > 1) dup.add("\"" + e.getKey() + "\" in " + rowNumbers(e.getValue()));
            if (!dup.isEmpty()) warns.add(new Issue(false, list("Duplicate study labels", dup, "Plots and tables will show identical names. If one study was entered twice it is counted twice in the pooled result; otherwise give the entries distinct labels (for example 'Smith 2010a' and 'Smith 2010b').")));
        }
        List<String> dataCols = new ArrayList<>(new LinkedHashSet<>(cols.values()));
        if (cols.size() > dataCols.size()) {
            Map<String, List<String>> use = new LinkedHashMap<>(); for (Map.Entry<String, String> e : cols.entrySet()) use.computeIfAbsent(e.getValue(), z -> new ArrayList<>()).add(names.getOrDefault(e.getKey(), e.getKey()));
            for (Map.Entry<String, List<String>> e : use.entrySet()) if (e.getValue().size() > 1) warns.add(new Issue(false, "Column '" + e.getKey() + "' is assigned to several parameters (" + String.join(", ", e.getValue()) + "). Check the column assignment."));
        }
        if (dataCols.size() >= 2) {
            Map<String, List<Integer>> same = new LinkedHashMap<>();
            for (int i = 0; i < n; i++) { StringBuilder k = new StringBuilder(); boolean any = false; for (String c : dataCols) { String s = t.col(c) == null || t.col(c)[i] == null ? "" : t.col(c)[i].trim(); if (!s.isEmpty()) any = true; k.append(s).append('\u0001'); } if (any) same.computeIfAbsent(k.toString(), z -> new ArrayList<>()).add(i); }
            List<String> dups = new ArrayList<>(); for (List<Integer> l : same.values()) if (l.size() > 1) dups.add(rowNumbers(l));
            if (!dups.isEmpty()) warns.add(new Issue(false, list("Identical data in all assigned columns", dups, "This may be a study entered twice, which would be counted twice.")));
        }

        // ---- 5. meta-regression covariates
        for (String c : covariates == null ? List.<String>of() : covariates) {
            String[] raw = t.col(c); if (raw == null) continue; List<String> bad = new ArrayList<>();
            for (int i = 0; i < n; i++) { String s = raw[i] == null ? "" : raw[i].trim(); if (!isMissing(s) && Double.isNaN(DataTable.parse(s))) bad.add(row(lab, i) + " has \"" + s + "\""); }
            if (!bad.isEmpty()) warns.add(new Issue(false, list("Covariate '" + c + "' contains text where a number is expected", bad, "These values are treated as missing, so those studies are left out of the meta-regression.")));
        }

        if (!errs.isEmpty()) { List<Issue> all = new ArrayList<>(errs); all.addAll(warns); throw new Failed(all); }
        List<String> out = new ArrayList<>(); for (Issue i : warns) out.add(i.text); return out;
    }

    // ---------------------------------------------------------------- rules
    /** Events (and total n, or person-time) of one arm / group. */
    private static void counts(Map<String, double[]> v, Map<String, String> names, String[] lab, List<Issue> errs, List<Issue> warns, String ev, String nk, String tk) {
        double[] e = v.get(ev); if (e == null) return; int n = e.length; String de = names.getOrDefault(ev, ev);
        List<String> neg = new ArrayList<>(), frac = new ArrayList<>();
        for (int i = 0; i < n; i++) { if (Double.isNaN(e[i])) continue; if (e[i] < 0) neg.add(row(lab, i) + ": " + fmt(e[i])); else if (e[i] != Math.rint(e[i])) frac.add(row(lab, i) + ": " + fmt(e[i])); }
        if (!neg.isEmpty()) errs.add(new Issue(true, list(de + " cannot be negative", neg, "")));
        if (!frac.isEmpty()) warns.add(new Issue(false, list(de + " should be whole numbers (counts of events)", frac, "")));
        if (nk != null) { size(v, names, lab, errs, warns, nk, 1); double[] tot = v.get(nk); String dn = names.getOrDefault(nk, nk);
            if (tot != null) { List<String> over = new ArrayList<>(); for (int i = 0; i < n; i++) if (!Double.isNaN(e[i]) && !Double.isNaN(tot[i]) && e[i] > tot[i]) over.add(row(lab, i) + ": " + fmt(e[i]) + " > " + fmt(tot[i]));
                if (!over.isEmpty()) errs.add(new Issue(true, list(de + " is greater than " + dn, over, "The number of events cannot exceed the number of participants; check that the columns are not swapped."))); } }
        if (tk != null) { double[] tm = v.get(tk); String dt = names.getOrDefault(tk, tk);
            if (tm != null) { List<String> bad = new ArrayList<>(); for (int i = 0; i < n; i++) if (!Double.isNaN(tm[i]) && !(tm[i] > 0)) bad.add(row(lab, i) + ": " + fmt(tm[i]));
                if (!bad.isEmpty()) errs.add(new Issue(true, list(dt + " must be greater than zero", bad, ""))); } }
    }

    private static void size(Map<String, double[]> v, Map<String, String> names, String[] lab, List<Issue> errs, List<Issue> warns, String k, double min) {
        double[] x = v.get(k); if (x == null) return; List<String> bad = new ArrayList<>();
        for (int i = 0; i < x.length; i++) if (!Double.isNaN(x[i]) && x[i] < min) bad.add(row(lab, i) + ": " + fmt(x[i]));
        if (!bad.isEmpty()) errs.add(new Issue(true, list(names.getOrDefault(k, k) + " must be at least " + fmt(min), bad, "")));
    }

    private static void sd(Map<String, double[]> v, Map<String, String> names, String[] lab, List<Issue> errs, List<Issue> warns, String k) {
        double[] x = v.get(k); if (x == null) return; List<String> neg = new ArrayList<>(), zero = new ArrayList<>();
        for (int i = 0; i < x.length; i++) { if (Double.isNaN(x[i])) continue; if (x[i] < 0) neg.add(row(lab, i) + ": " + fmt(x[i])); else if (x[i] == 0) zero.add(row(lab, i)); }
        String d = names.getOrDefault(k, k);
        if (!neg.isEmpty()) errs.add(new Issue(true, list(d + " cannot be negative", neg, "")));
        if (!zero.isEmpty()) warns.add(new Issue(false, list(d + " is zero (the study then has no variability and an undefined or infinite weight)", zero, "")));
    }

    /** Keys that must have a value for a study to be usable. */
    private static Set<String> required(String type, Set<String> mapped, boolean genRatioCi) {
        Set<String> r = new LinkedHashSet<>();
        switch (type) {
            case "bin": case "inc": for (String k : type.equals("bin") ? new String[]{"event.e", "n.e", "event.c", "n.c"} : new String[]{"event.e", "time.e", "event.c", "time.c"}) r.add(k); break;
            case "cont": r.addAll(List.of("n.e", "mean.e", "sd.e", "n.c", "mean.c", "sd.c")); break;
            case "cor": r.addAll(List.of("cor", "n")); break;
            case "mean": r.addAll(List.of("n", "mean", "sd")); break;
            case "prop": r.addAll(List.of("event", "n")); break;
            case "rate": r.addAll(List.of("event", "time")); break;
            default:
                r.add("TE");
                if (genRatioCi || !mapped.contains("seTE")) { r.add("lower"); r.add("upper"); } else r.add("seTE");
        }
        r.retainAll(mapped); return r;
    }

    // ---------------------------------------------------------------- text helpers
    static boolean isMissing(String s) { return s.isEmpty() || s.equalsIgnoreCase("NA") || s.equals("-") || s.equalsIgnoreCase("NaN"); }
    static String disp(String key, String col, Map<String, String> names) { return "'" + col + "' (" + names.getOrDefault(key, key) + ")"; }
    static String row(String[] lab, int i) { return "row " + (i + 1) + (lab != null && !lab[i].isEmpty() ? " (" + lab[i] + ")" : ""); }
    static List<String> rows(String[] lab, List<Integer> idx) { List<String> r = new ArrayList<>(); for (int i : idx) r.add(row(lab, i)); return r; }
    static String rowNumbers(List<Integer> idx) { StringBuilder b = new StringBuilder("rows "); for (int j = 0; j < idx.size(); j++) { if (j > 0) b.append(j == idx.size() - 1 ? " and " : ", "); b.append(idx.get(j) + 1); } return b.toString(); }
    static String fmt(double x) { return x == Math.rint(x) && Math.abs(x) < 1e9 ? String.valueOf((long) x) : Fmt.num(x, 4).replaceAll("0+$", "").replaceAll("\\.$", ""); }

    /** "Head: item; item; item (and N more). Tail" */
    static String list(String head, List<String> items, String tail) {
        StringBuilder b = new StringBuilder(head).append(": ");
        for (int i = 0; i < Math.min(MAX_LISTED, items.size()); i++) { if (i > 0) b.append("; "); b.append(items.get(i)); }
        if (items.size() > MAX_LISTED) b.append(" (and ").append(items.size() - MAX_LISTED).append(" more)");
        b.append('.'); if (!tail.isEmpty()) b.append(' ').append(tail);
        return b.toString();
    }
}
