package org.metajava.plot;

import java.awt.Color;
import java.util.*;

/**
 * Visual theme of the forest plots (main, subgroup, leave-one-out and cumulative).
 * <ul>
 * <li>Default: the look of meta::forest (R package meta);</li>
 * <li>RevMan 5 style: Cochrane Review Manager 5 (blue squares, black diamond, rule under the header, "0.36 [0.30, 0.44]", weight before the estimate);</li>
 * <li>BMJ style: purple diamonds, "e / n" columns, grey rules, "0.36 (0.30 to 0.44)", weight in %;</li>
 * <li>STATA style: Stata metan look (pale-blue frame, navy title, grey squares with a dot, hollow diamond, dashed red pooled line, "Favours ..." labels, note under the table).</li>
 * </ul>
 * The individual options (square fill, diamond fill / border, null-effect line and pooled-effect line colour and style) override the theme.
 */
public final class ForestTheme {
    public static final String DEFAULT = "Default", REVMAN = "RevMan 5 style", BMJ = "BMJ style", STATA = "STATA style";
    public static final String[] THEMES = {DEFAULT, REVMAN, BMJ, STATA};
    /** First entry of every override list: keep what the theme prescribes. */
    public static final String AUTO = "Theme default", NONE = "None";

    private static final LinkedHashMap<String, Color> PALETTE = new LinkedHashMap<>();
    static {
        PALETTE.put("Black", new Color(0, 0, 0)); PALETTE.put("Dark grey", new Color(64, 64, 64)); PALETTE.put("Grey", new Color(128, 128, 128)); PALETTE.put("Light grey", new Color(192, 192, 192)); PALETTE.put("White", Color.WHITE);
        PALETTE.put("Blue", new Color(0, 0, 255)); PALETTE.put("Navy", new Color(0, 0, 128)); PALETTE.put("Light blue", new Color(120, 170, 230)); PALETTE.put("Teal", new Color(0, 128, 128));
        PALETTE.put("Red", new Color(205, 0, 0)); PALETTE.put("Dark red", new Color(128, 0, 0)); PALETTE.put("Orange", new Color(240, 130, 0)); PALETTE.put("Gold", new Color(212, 160, 20));
        PALETTE.put("Green", new Color(0, 150, 0)); PALETTE.put("Dark green", new Color(0, 100, 0)); PALETTE.put("Purple", new Color(112, 96, 168)); PALETTE.put("Pink", new Color(225, 105, 165)); PALETTE.put("Brown", new Color(139, 69, 19));
    }
    /** Items of the colour drop-down lists. */
    public static final String[] COLOURS = list(false), FILL_COLOURS = list(true);
    public static final String[] LINE_STYLES = {AUTO, "Solid", "Dotted", "Dashed"};
    private static String[] list(boolean none) { List<String> l = new ArrayList<>(); l.add(AUTO); l.addAll(PALETTE.keySet()); if (none) l.add(NONE); return l.toArray(new String[0]); }

    static final float[] DOTTED = {1.5f, 2.5f}, DASHED = {5f, 3f};

    // ------------------------------------------------------------------ resolved theme
    public final int kind;               // 0 Default, 1 RevMan 5, 2 BMJ, 3 STATA
    final int rowBase, plotW;            // row height at spacing 1, width of the plot area
    final boolean noDataCols, boldHead, boldPool, twoLineHead, markerDiamond, markerDot, ciFollowsMarker;
    final float tickSize, markerMax;
    Color headRule, axisCol, bgOuter, boxBorder, titleCol, ciCol;   // ciCol: null = black
    Color squareFill, diamondFill, diamondBorder, nullCol, pooledCol;   // diamondFill / diamondBorder: null = none
    float nullW, pooledW; float[] nullDash, pooledDash;

    private ForestTheme(int kind) {
        this.kind = kind;
        rowBase = kind == 3 ? 24 : 20; plotW = kind == 3 ? 300 : 230; noDataCols = kind == 3; boldHead = kind != 3; boldPool = kind <= 1; twoLineHead = kind != 0;
        markerDiamond = kind == 2; markerDot = kind == 3; ciFollowsMarker = kind == 2; tickSize = kind == 0 ? 13f : 12f; markerMax = kind == 2 ? 13f : kind == 3 ? 18f : 15f;
        axisCol = kind == 2 ? new Color(90, 90, 90) : kind == 3 ? new Color(80, 90, 95) : Color.BLACK;
        nullW = kind == 3 ? 1.3f : 1.1f; pooledW = kind == 3 ? 1.2f : 0.9f;
        switch (kind) {
            case 1: headRule = Color.BLACK; squareFill = new Color(0, 0, 255); diamondFill = Color.BLACK; diamondBorder = Color.BLACK; nullCol = Color.BLACK; pooledCol = Color.BLACK; nullDash = null; pooledDash = DOTTED; break;
            case 2: headRule = new Color(165, 165, 165); squareFill = PALETTE.get("Purple"); diamondFill = PALETTE.get("Purple"); diamondBorder = PALETTE.get("Purple"); nullCol = new Color(140, 140, 140); pooledCol = new Color(40, 40, 40); nullDash = null; pooledDash = DOTTED; nullW = 1.0f; break;
            case 3: headRule = null; bgOuter = new Color(234, 242, 243); boxBorder = new Color(150, 165, 170); titleCol = new Color(26, 71, 111); squareFill = new Color(192, 192, 192); diamondFill = Color.WHITE; diamondBorder = Color.BLACK; nullCol = Color.BLACK; pooledCol = new Color(128, 0, 0); nullDash = null; pooledDash = DASHED; break;
            default: squareFill = new Color(192, 192, 192); diamondFill = new Color(192, 192, 192); diamondBorder = Color.BLACK; nullCol = Color.BLACK; pooledCol = Color.BLACK; nullDash = null; pooledDash = DOTTED;
        }
        if (kind == 2) ciCol = squareFill;
    }

    static ForestTheme of(Plots.Opts o) {
        String name = o.theme == null ? DEFAULT : o.theme;
        ForestTheme t = new ForestTheme(name.equals(REVMAN) ? 1 : name.equals(BMJ) ? 2 : name.equals(STATA) ? 3 : 0);
        Color c = pick(o.squareColour); if (c != null) { t.squareFill = c; if (t.ciFollowsMarker) t.ciCol = c; }
        if (!isAuto(o.diamondFill)) t.diamondFill = o.diamondFill.equals(NONE) ? null : orElse(pick(o.diamondFill), t.diamondFill);
        if (!isAuto(o.diamondBorder)) t.diamondBorder = o.diamondBorder.equals(NONE) ? null : orElse(pick(o.diamondBorder), t.diamondBorder);
        if ((c = pick(o.nullColour)) != null) t.nullCol = c;
        if ((c = pick(o.pooledColour)) != null) t.pooledCol = c;
        if (!isAuto(o.nullStyle)) t.nullDash = dash(o.nullStyle, t.nullDash);
        if (!isAuto(o.pooledStyle)) t.pooledDash = dash(o.pooledStyle, t.pooledDash);
        return t;
    }

    private static boolean isAuto(String s) { return s == null || s.isEmpty() || s.equals(AUTO); }
    private static Color pick(String name) { return isAuto(name) ? null : PALETTE.get(name); }
    private static Color orElse(Color c, Color d) { return c != null ? c : d; }
    private static float[] dash(String style, float[] keep) { switch (style) { case "Solid": return null; case "Dotted": return DOTTED; case "Dashed": return DASHED; default: return keep; } }

    /** Stroke of the null-effect line (solid, dotted or dashed). */
    java.awt.BasicStroke nullStroke() { return stroke(nullW, nullDash); }
    java.awt.BasicStroke pooledStroke() { return stroke(pooledW, pooledDash); }
    private static java.awt.BasicStroke stroke(float w, float[] d) { return d == null ? new java.awt.BasicStroke(w) : new java.awt.BasicStroke(w, java.awt.BasicStroke.CAP_BUTT, java.awt.BasicStroke.JOIN_MITER, 10, d, 0); }
}
