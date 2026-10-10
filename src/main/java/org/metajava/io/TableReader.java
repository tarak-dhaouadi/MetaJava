package org.metajava.io;

import org.metajava.core.DataTable;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

/** Reads .xlsx (pure JDK), .csv, .tsv, .txt. */
public final class TableReader {
    private TableReader() {}

    public static List<String> sheetNames(File f) throws Exception {
        List<String> r = new ArrayList<>();
        if (!f.getName().toLowerCase().endsWith(".xlsx")) { r.add("data"); return r; }
        try (ZipFile z = new ZipFile(f)) {
            Document wb = parse(z, "xl/workbook.xml");
            NodeList sh = wb.getElementsByTagName("sheet");
            for (int i = 0; i < sh.getLength(); i++) r.add(((Element) sh.item(i)).getAttribute("name"));
        }
        return r;
    }

    public static DataTable read(File f, int sheetIndex) throws Exception {
        String n = f.getName().toLowerCase();
        if (n.endsWith(".xlsx")) return readXlsx(f, sheetIndex);
        return readDelimited(f);
    }

    private static Document parse(ZipFile z, String name) throws Exception {
        ZipEntry e = z.getEntry(name);
        if (e == null) return null;
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        try (InputStream in = z.getInputStream(e)) { return dbf.newDocumentBuilder().parse(in); }
    }

    private static int colIndex(String ref) {
        int c = 0;
        for (char ch : ref.toCharArray()) { if (!Character.isLetter(ch)) break; c = c * 26 + (Character.toUpperCase(ch) - 'A' + 1); }
        return c - 1;
    }

    static DataTable readXlsx(File f, int sheetIndex) throws Exception {
        try (ZipFile z = new ZipFile(f)) {
            List<String> ss = new ArrayList<>();
            Document sd = parse(z, "xl/sharedStrings.xml");
            if (sd != null) {
                NodeList si = sd.getElementsByTagName("si");
                for (int i = 0; i < si.getLength(); i++) {
                    StringBuilder sb = new StringBuilder();
                    NodeList ts = ((Element) si.item(i)).getElementsByTagName("t");
                    for (int k = 0; k < ts.getLength(); k++) sb.append(ts.item(k).getTextContent());
                    ss.add(sb.toString());
                }
            }
            // resolve sheet path via workbook rels
            String path = "xl/worksheets/sheet" + (sheetIndex + 1) + ".xml";
            Document wb = parse(z, "xl/workbook.xml"), rel = parse(z, "xl/_rels/workbook.xml.rels");
            if (wb != null && rel != null) {
                NodeList sh = wb.getElementsByTagName("sheet");
                if (sheetIndex < sh.getLength()) {
                    String rid = ((Element) sh.item(sheetIndex)).getAttribute("r:id");
                    NodeList rs = rel.getElementsByTagName("Relationship");
                    for (int i = 0; i < rs.getLength(); i++) {
                        Element r = (Element) rs.item(i);
                        if (r.getAttribute("Id").equals(rid)) {
                            String t = r.getAttribute("Target");
                            path = t.startsWith("/") ? t.substring(1) : "xl/" + t;
                        }
                    }
                }
            }
            Document ws = parse(z, path);
            if (ws == null) throw new IOException("Worksheet not found: " + path);
            NodeList rows = ws.getElementsByTagName("row");
            List<Map<Integer, String>> data = new ArrayList<>();
            int maxc = 0;
            for (int i = 0; i < rows.getLength(); i++) {
                Element row = (Element) rows.item(i);
                int rn = row.hasAttribute("r") ? Integer.parseInt(row.getAttribute("r")) - 1 : data.size();
                while (data.size() < rn) data.add(new HashMap<>());
                Map<Integer, String> m = new HashMap<>();
                NodeList cs = row.getElementsByTagName("c");
                for (int k = 0; k < cs.getLength(); k++) {
                    Element c = (Element) cs.item(k);
                    int ci = colIndex(c.getAttribute("r"));
                    String t = c.getAttribute("t"), val = null;
                    NodeList v = c.getElementsByTagName("v");
                    if (t.equals("inlineStr")) val = c.getTextContent();
                    else if (v.getLength() > 0) {
                        val = v.item(0).getTextContent();
                        if (t.equals("s")) val = ss.get(Integer.parseInt(val));
                    }
                    if (val != null) { m.put(ci, val); maxc = Math.max(maxc, ci + 1); }
                }
                data.add(m);
            }
            return build(data, maxc);
        }
    }

    private static DataTable build(List<Map<Integer, String>> data, int maxc) {
        DataTable t = new DataTable();
        if (data.isEmpty()) return t;
        int hr = 0;
        while (hr < data.size() && data.get(hr).isEmpty()) hr++;
        if (hr >= data.size()) return t;
        List<Map<Integer, String>> body = new ArrayList<>();
        for (int i = hr + 1; i < data.size(); i++) if (!data.get(i).isEmpty()) body.add(data.get(i));
        t.nrow = body.size();
        for (int j = 0; j < maxc; j++) {
            String h = data.get(hr).get(j);
            if (h == null || h.trim().isEmpty()) h = "V" + (j + 1);
            String[] c = new String[t.nrow];
            for (int i = 0; i < t.nrow; i++) c[i] = body.get(i).getOrDefault(j, "");
            t.add(h.trim(), c);
        }
        return t;
    }

    static DataTable readDelimited(File f) throws IOException {
        List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
        lines.removeIf(String::isBlank);
        if (lines.isEmpty()) return new DataTable();
        String first = lines.get(0);
        char sep = count(first, '\t') > 0 ? '\t' : count(first, ';') > count(first, ',') ? ';' : ',';
        List<Map<Integer, String>> data = new ArrayList<>();
        int maxc = 0;
        for (String l : lines) {
            List<String> p = split(l, sep);
            Map<Integer, String> m = new HashMap<>();
            for (int j = 0; j < p.size(); j++) m.put(j, p.get(j));
            maxc = Math.max(maxc, p.size());
            data.add(m);
        }
        return build(data, maxc);
    }

    private static int count(String s, char c) { int n = 0; for (char x : s.toCharArray()) if (x == c) n++; return n; }

    private static List<String> split(String l, char sep) {
        List<String> r = new ArrayList<>(); StringBuilder sb = new StringBuilder(); boolean q = false;
        for (int i = 0; i < l.length(); i++) {
            char c = l.charAt(i);
            if (c == '"') { if (q && i + 1 < l.length() && l.charAt(i + 1) == '"') { sb.append('"'); i++; } else q = !q; }
            else if (c == sep && !q) { r.add(sb.toString()); sb.setLength(0); }
            else sb.append(c);
        }
        r.add(sb.toString());
        return r;
    }
}
