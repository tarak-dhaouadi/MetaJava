package org.metajava.core;

import java.util.*;

/** Simple column-oriented table of strings with numeric helpers. */
public class DataTable {
    public final List<String> names = new ArrayList<>();
    public final List<String[]> cols = new ArrayList<>();
    public int nrow;

    public int index(String name) { return names.indexOf(name); }
    public String[] col(String name) { int i = index(name); return i < 0 ? null : cols.get(i); }

    public double[] num(String name) {
        String[] c = col(name);
        if (c == null) return null;
        double[] r = new double[nrow];
        for (int i = 0; i < nrow; i++) r[i] = parse(c[i]);
        return r;
    }

    public static double parse(String s) {
        if (s == null) return Double.NaN;
        s = s.trim().replace(',', '.');
        if (s.isEmpty() || s.equalsIgnoreCase("NA") || s.equals("-") || s.equalsIgnoreCase("NaN")) return Double.NaN;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return Double.NaN; }
    }

    /** True if at least 80% of non-empty cells parse as numbers. */
    public boolean isNumeric(int j) {
        int ok = 0, tot = 0;
        for (String s : cols.get(j)) { if (s == null || s.trim().isEmpty()) continue; tot++; if (!Double.isNaN(parse(s))) ok++; }
        return tot > 0 && ok >= 0.8 * tot;
    }

    public String[] strings(String name) {
        String[] c = col(name);
        if (c == null) return null;
        String[] r = new String[nrow];
        for (int i = 0; i < nrow; i++) r[i] = c[i] == null ? "" : c[i].trim();
        return r;
    }

    public void add(String name, String[] values) { names.add(name); cols.add(values); }
}
