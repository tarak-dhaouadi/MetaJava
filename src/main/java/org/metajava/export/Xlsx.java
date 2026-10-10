package org.metajava.export;

import org.metajava.core.Report;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/** Writes the results tables to an Excel workbook (.xlsx, Office Open XML) without external libraries: one sheet per analysis section. */
public final class Xlsx {
    private Xlsx() {}

    private static final Pattern NUM = Pattern.compile("-?\\d+(\\.\\d+)?"), PCT = Pattern.compile("-?\\d+(\\.\\d+)?%");
    private static final Pattern CI = Pattern.compile("\\[\\s*(NA|-?Inf|-?\\d+(?:\\.\\d+)?)\\s*;\\s*(NA|-?Inf|-?\\d+(?:\\.\\d+)?)\\s*\\]");

    // ------------------------------------------------------------ styles
    private static final class Styles {
        final List<String> numFmts = new ArrayList<>(); final List<String> xfs = new ArrayList<>(); final Map<String, Integer> idx = new HashMap<>();
        Styles() { xf(0, 0, 0, 0, null); }
        int fmt(String code) { int i = numFmts.indexOf(code); if (i < 0) { numFmts.add(code); i = numFmts.size() - 1; } return 164 + i; }
        /** font: 0 normal, 1 bold, 2 title, 3 italic; fill: 0 none, 2 header; border: 0 none, 1 header (top and bottom), 2 bottom line. */
        int xf(int font, int fill, int border, int numFmt, String align) {
            String key = font + "|" + fill + "|" + border + "|" + numFmt + "|" + align; Integer i = idx.get(key); if (i != null) return i;
            String x = "<xf numFmtId=\"" + numFmt + "\" fontId=\"" + font + "\" fillId=\"" + fill + "\" borderId=\"" + border + "\" xfId=\"0\"" + (numFmt != 0 ? " applyNumberFormat=\"1\"" : "") + (font != 0 ? " applyFont=\"1\"" : "") + (fill != 0 ? " applyFill=\"1\"" : "") + (border != 0 ? " applyBorder=\"1\"" : "")
                + (align != null ? " applyAlignment=\"1\"><alignment horizontal=\"" + align + "\" vertical=\"center\"/></xf>" : "/>");
            xfs.add(x); idx.put(key, xfs.size() - 1); return xfs.size() - 1;
        }
        String xml() {
            StringBuilder b = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
            if (!numFmts.isEmpty()) { b.append("<numFmts count=\"").append(numFmts.size()).append("\">"); for (int i = 0; i < numFmts.size(); i++) b.append("<numFmt numFmtId=\"").append(164 + i).append("\" formatCode=\"").append(ZipOut.esc(numFmts.get(i))).append("\"/>"); b.append("</numFmts>"); }
            b.append("<fonts count=\"4\">")
             .append("<font><sz val=\"11\"/><name val=\"Calibri\"/><family val=\"2\"/></font>")
             .append("<font><b/><sz val=\"11\"/><name val=\"Calibri\"/><family val=\"2\"/></font>")
             .append("<font><b/><sz val=\"14\"/><color rgb=\"FF1F4E8C\"/><name val=\"Calibri\"/><family val=\"2\"/></font>")
             .append("<font><i/><sz val=\"11\"/><color rgb=\"FF555555\"/><name val=\"Calibri\"/><family val=\"2\"/></font></fonts>")
             .append("<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFDCE6F1\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>")
             .append("<borders count=\"3\"><border><left/><right/><top/><bottom/><diagonal/></border>")
             .append("<border><left/><right/><top style=\"thin\"><color auto=\"1\"/></top><bottom style=\"thin\"><color auto=\"1\"/></bottom><diagonal/></border>")
             .append("<border><left/><right/><top/><bottom style=\"thin\"><color auto=\"1\"/></bottom><diagonal/></border></borders>")
             .append("<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>")
             .append("<cellXfs count=\"").append(xfs.size()).append("\">"); for (String x : xfs) b.append(x);
            b.append("</cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>");
            return b.toString();
        }
    }

    // ------------------------------------------------------------ sheet builder
    private static final class Sheet {
        final String name; final StringBuilder rows = new StringBuilder(); int r = 0; final List<Integer> width = new ArrayList<>();
        Sheet(String name) { this.name = name; }
        void need(int col, int chars) { while (width.size() <= col) width.add(8); width.set(col, Math.max(width.get(col), Math.min(chars + 2, 60))); }
        void row(List<String> cells) { r++; rows.append("<row r=\"").append(r).append("\">"); for (int j = 0; j < cells.size(); j++) rows.append(cells.get(j)); rows.append("</row>"); }
        void blank() { r++; }
    }
    private static String ref(int col, int row) { StringBuilder c = new StringBuilder(); int n = col; do { c.insert(0, (char) ('A' + n % 26)); n = n / 26 - 1; } while (n >= 0); return c.toString() + row; }
    private static String str(Sheet sh, int col, String v, int style) { return "<c r=\"" + ref(col, sh.r + 1) + "\" s=\"" + style + "\" t=\"inlineStr\"><is><t xml:space=\"preserve\">" + ZipOut.esc(v) + "</t></is></c>"; }
    private static String num(Sheet sh, int col, String v, int style) { return "<c r=\"" + ref(col, sh.r + 1) + "\" s=\"" + style + "\"><v>" + v + "</v></c>"; }
    private static String empty(Sheet sh, int col, int style) { return "<c r=\"" + ref(col, sh.r + 1) + "\" s=\"" + style + "\"/>"; }

    private static boolean isInf(String s) { return s.endsWith("Inf"); }

    /** Splits columns whose cells are all confidence intervals "[lo; up]" into two numeric columns. */
    private static Report.Tbl splitCi(Report.Tbl t) {
        int n = t.head.size(); boolean[] ci = new boolean[n]; boolean any = false;
        for (int j = 0; j < n; j++) { int hit = 0, tot = 0;
            for (String[] row : t.rows) { String c = j < row.length ? row[j].trim() : ""; if (c.isEmpty() || c.equals("NA")) continue; tot++; if (CI.matcher(c).matches()) hit++; }
            if (tot > 0 && hit == tot) { ci[j] = true; any = true; } }
        if (!any) return t;
        List<String> head = new ArrayList<>(); List<Boolean> right = new ArrayList<>();
        for (int j = 0; j < n; j++) { String h = t.head.get(j); if (ci[j]) { String base = h.isEmpty() ? "CI" : h; head.add(base + " lower"); head.add(base + " upper"); right.add(true); right.add(true); } else { head.add(h); right.add(t.right[j]); } }
        List<String[]> rows = new ArrayList<>();
        for (String[] row : t.rows) { List<String> o = new ArrayList<>();
            for (int j = 0; j < n; j++) { String c = j < row.length ? row[j].trim() : "";
                if (ci[j]) { Matcher m = CI.matcher(c); if (m.matches()) { o.add(m.group(1)); o.add(m.group(2)); } else { o.add(c); o.add(""); } } else o.add(j < row.length ? row[j] : ""); }
            rows.add(o.toArray(new String[0])); }
        boolean[] rr = new boolean[right.size()]; for (int i = 0; i < rr.length; i++) rr[i] = right.get(i);
        return new Report.Tbl("", head, rows, rr);   // the rendered text is only used to locate a table in the printed output
    }

    private static void writeTable(Sheet sh, Styles st, Report.Tbl t0) {
        Report.Tbl t = splitCi(t0); int n = t.head.size();
        List<String> cells = new ArrayList<>(); int hs = st.xf(1, 2, 1, 0, "center");
        for (int j = 0; j < n; j++) { String h = t.head.get(j); cells.add(str(sh, j, h, hs)); sh.need(j, h.length()); }
        sh.row(cells);
        for (String[] row : t.rows) {
            cells = new ArrayList<>();
            for (int j = 0; j < n; j++) {
                String c = j < row.length ? row[j] : ""; String v = c.trim(); sh.need(j, v.length());
                String al = t.right[j] ? "right" : "left";
                if (v.isEmpty()) cells.add(empty(sh, j, 0));
                else if (NUM.matcher(v).matches()) { int dec = v.indexOf('.') < 0 ? 0 : v.length() - v.indexOf('.') - 1; String code = dec == 0 ? "0" : "0." + "0".repeat(dec); cells.add(num(sh, j, v, st.xf(0, 0, 0, st.fmt(code), "right"))); }
                else if (PCT.matcher(v).matches()) { String d = v.substring(0, v.length() - 1); int dec = d.indexOf('.') < 0 ? 0 : d.length() - d.indexOf('.') - 1; String code = (dec == 0 ? "0" : "0." + "0".repeat(dec)) + "\"%\""; cells.add(num(sh, j, d, st.xf(0, 0, 0, st.fmt(code), "right"))); }
                else if (isInf(v)) cells.add(str(sh, j, v, st.xf(0, 0, 0, 0, "right")));
                else cells.add(str(sh, j, c, st.xf(0, 0, 0, 0, al)));
            }
            sh.row(cells);
        }
    }

    private static String sheetName(String title, Set<String> used) {
        String b = title.replace(": ", " - ").replaceAll("[\\[\\]:*?/\\\\]", "-").replace('’', '\'').trim(); if (b.isEmpty()) b = "Sheet"; if (b.startsWith("'")) b = b.substring(1);
        if (b.length() > 31 && b.contains(" - ")) { String head = b.substring(0, b.indexOf(" - ")).trim(); if (head.length() >= 8) b = head; }
        if (b.length() > 31) b = b.substring(0, 31); String nm = b; int k = 2;
        while (used.contains(nm.toLowerCase())) { String suf = " (" + k++ + ")"; nm = b.substring(0, Math.min(b.length(), 31 - suf.length())) + suf; }
        used.add(nm.toLowerCase()); return nm;
    }

    /** One sheet per text section that contains tables (title, then every table and the lines printed around it); text sections without tables go on one "Notes" sheet. Figures are not included. */
    public static void write(File f, ResultsDoc doc) throws IOException {
        Styles st = new Styles(); List<Sheet> sheets = new ArrayList<>(); Set<String> used = new HashSet<>(); Sheet notes = null;
        int title = st.xf(2, 0, 0, 0, null), plain = st.xf(0, 0, 0, 0, null), ital = st.xf(3, 0, 0, 0, null), bold = st.xf(1, 0, 0, 0, null);
        for (ResultsDoc.Section s : doc.sections) {
            if (s.png != null) continue;
            if (s.hasTables()) {
                Sheet sh = new Sheet(sheetName(s.title, used)); sheets.add(sh);
                sh.row(List.of(str(sh, 0, s.title, title))); sh.blank();
                for (Object o : s.blocks) {
                    if (o instanceof Report.Tbl) { writeTable(sh, st, (Report.Tbl) o); sh.blank(); }
                    else for (String line : (String[]) o) sh.row(List.of(str(sh, 0, line.stripTrailing(), plain)));
                    if (!(o instanceof Report.Tbl)) sh.blank();
                }
            } else {
                if (notes == null) { notes = new Sheet(sheetName("Notes", used)); sheets.add(notes); }
                notes.row(List.of(str(notes, 0, s.title, bold)));
                for (Object o : s.blocks) { for (String line : (String[]) o) notes.row(List.of(str(notes, 0, line.stripTrailing(), plain))); }
                notes.blank();
            }
        }
        if (sheets.isEmpty()) { Sheet sh = new Sheet("Results"); sh.row(List.of(str(sh, 0, "No tables to export.", plain))); sheets.add(sh); }
        try (ZipOut z = new ZipOut(new BufferedOutputStream(new FileOutputStream(f)))) {
            StringBuilder ct = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
            StringBuilder wb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
            StringBuilder rel = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
            for (int i = 0; i < sheets.size(); i++) {
                Sheet sh = sheets.get(i); int id = i + 1;
                ct.append("<Override PartName=\"/xl/worksheets/sheet").append(id).append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
                wb.append("<sheet name=\"").append(ZipOut.esc(sh.name)).append("\" sheetId=\"").append(id).append("\" r:id=\"rId").append(id).append("\"/>");
                rel.append("<Relationship Id=\"rId").append(id).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet").append(id).append(".xml\"/>");
                StringBuilder x = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView workbookViewId=\"0\"").append(i == 0 ? " tabSelected=\"1\"" : "").append("/></sheetViews><sheetFormatPr defaultRowHeight=\"15\"/>");
                if (!sh.width.isEmpty()) { x.append("<cols>"); for (int j = 0; j < sh.width.size(); j++) x.append("<col min=\"").append(j + 1).append("\" max=\"").append(j + 1).append("\" width=\"").append(sh.width.get(j)).append("\" customWidth=\"1\"/>"); x.append("</cols>"); }
                x.append("<sheetData>").append(sh.rows).append("</sheetData></worksheet>");
                z.text("xl/worksheets/sheet" + id + ".xml", x.toString());
            }
            ct.append("</Types>"); wb.append("</sheets></workbook>");
            rel.append("<Relationship Id=\"rId").append(sheets.size() + 1).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>");
            z.text("[Content_Types].xml", ct.toString());
            z.text("_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            z.text("xl/workbook.xml", wb.toString()); z.text("xl/_rels/workbook.xml.rels", rel.toString()); z.text("xl/styles.xml", st.xml());
        }
    }
}
