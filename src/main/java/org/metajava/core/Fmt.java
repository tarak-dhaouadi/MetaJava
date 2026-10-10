package org.metajava.core;

import java.util.Locale;

/** Number formatting in the style of the R meta package. */
public final class Fmt {
    private Fmt() {}
    public static String num(double x, int d) { if (Double.isNaN(x)) return "NA"; if (Double.isInfinite(x)) return x > 0 ? "Inf" : "-Inf"; return String.format(Locale.ROOT, "%." + d + "f", x); }
    public static String pval(double p, int d) {
        if (Double.isNaN(p)) return "NA"; double lim = Math.pow(10, -d);
        if (p < lim) return "< " + String.format(Locale.ROOT, "%." + d + "f", lim);
        return String.format(Locale.ROOT, "%." + d + "f", p);
    }
    public static String pvalEq(double p, int d) { String s = pval(p, d); return s.startsWith("<") ? s : "= " + s; }
    public static String ci(double lo, double up, int d) { return "[" + num(lo, d) + "; " + num(up, d) + "]"; }
    public static String pad(String s, int w) { StringBuilder b = new StringBuilder(); for (int i = s.length(); i < w; i++) b.append(' '); return b + s; }
    public static String padR(String s, int w) { StringBuilder b = new StringBuilder(s); while (b.length() < w) b.append(' '); return b.toString(); }
    public static String rep(char c, int n) { return String.valueOf(c).repeat(Math.max(0, n)); }
}
