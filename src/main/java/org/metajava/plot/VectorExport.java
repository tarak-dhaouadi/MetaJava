package org.metajava.plot;

import java.awt.*;
import java.awt.geom.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.zip.DeflaterOutputStream;

/** Writes a {@link VectorGraphics} recording as SVG or PDF. Page size = pixel size of the PNG produced at the same resolution (1 px = 0.75 pt in the PDF). */
public final class VectorExport {
    private VectorExport() {}

    static String n(double v) { if (Math.abs(v) < 5e-4) return "0"; String s = String.format(Locale.ROOT, "%.3f", v); int e = s.length(); while (s.charAt(e - 1) == '0') e--; if (s.charAt(e - 1) == '.') e--; return s.substring(0, e); }
    static String hex(Color c) { return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue()); }

    /** Path data in SVG syntax. */
    static String svgPath(Shape s) {
        StringBuilder b = new StringBuilder(); double[] c = new double[6];
        for (PathIterator it = s.getPathIterator(null); !it.isDone(); it.next()) switch (it.currentSegment(c)) {
            case PathIterator.SEG_MOVETO: b.append('M').append(n(c[0])).append(' ').append(n(c[1])); break;
            case PathIterator.SEG_LINETO: b.append('L').append(n(c[0])).append(' ').append(n(c[1])); break;
            case PathIterator.SEG_QUADTO: b.append('Q').append(n(c[0])).append(' ').append(n(c[1])).append(' ').append(n(c[2])).append(' ').append(n(c[3])); break;
            case PathIterator.SEG_CUBICTO: b.append('C').append(n(c[0])).append(' ').append(n(c[1])).append(' ').append(n(c[2])).append(' ').append(n(c[3])).append(' ').append(n(c[4])).append(' ').append(n(c[5])); break;
            default: b.append('Z');
        }
        return b.toString();
    }
    static String esc(String s) { StringBuilder b = new StringBuilder(); for (char ch : s.toCharArray()) { switch (ch) { case '&': b.append("&amp;"); break; case '<': b.append("&lt;"); break; case '>': b.append("&gt;"); break; case '"': b.append("&quot;"); break; default: if (ch >= 32 || ch == '\t') b.append(ch); } } return b.toString(); }

    // ================= SVG =================
    public static void svg(VectorGraphics g, OutputStream out) throws IOException { svg(g, out, null); }
    public static void svg(VectorGraphics g, OutputStream out, String title) throws IOException {
        StringBuilder b = new StringBuilder();
        b.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(g.pxW).append("\" height=\"").append(g.pxH).append("\" viewBox=\"0 0 ").append(g.pxW).append(' ').append(g.pxH).append("\">\n");
        if (title != null && !title.isEmpty()) b.append("<title>").append(esc(title)).append("</title>\n");
        Map<String, String> clips = new HashMap<>(); String open = null; int id = 0;
        for (VectorGraphics.Op o : g.ops) {
            String cp = o.clip == null ? null : svgPath(o.clip);
            if (!Objects.equals(cp, open)) {
                if (open != null) b.append("</g>\n");
                if (cp != null) { String cid = clips.get(cp); if (cid == null) { cid = "clip" + (++id); clips.put(cp, cid); b.append("<clipPath id=\"").append(cid).append("\"><path d=\"").append(cp).append("\"/></clipPath>\n"); } b.append("<g clip-path=\"url(#").append(cid).append(")\">\n"); }
                open = cp;
            }
            String op = o.alpha < 0.999 ? " opacity=\"" + n(o.alpha) + "\"" : "";
            if (o.kind == VectorGraphics.FILL) {
                PathIterator pi = o.shape.getPathIterator(null);
                b.append("<path d=\"").append(svgPath(o.shape)).append("\" fill=\"").append(hex(o.color)).append('"').append(pi.getWindingRule() == PathIterator.WIND_EVEN_ODD ? " fill-rule=\"evenodd\"" : "").append(op.isEmpty() ? "" : " fill-opacity=\"" + n(o.alpha) + "\"").append(" stroke=\"none\"/>\n");
            } else if (o.kind == VectorGraphics.STROKE) {
                b.append("<path d=\"").append(svgPath(o.shape)).append("\" fill=\"none\" stroke=\"").append(hex(o.color)).append("\" stroke-width=\"").append(n(o.lw)).append('"')
                 .append(o.cap == BasicStroke.CAP_ROUND ? " stroke-linecap=\"round\"" : o.cap == BasicStroke.CAP_SQUARE ? " stroke-linecap=\"square\"" : "")
                 .append(o.join == BasicStroke.JOIN_ROUND ? " stroke-linejoin=\"round\"" : o.join == BasicStroke.JOIN_BEVEL ? " stroke-linejoin=\"bevel\"" : " stroke-linejoin=\"miter\" stroke-miterlimit=\"" + n(o.miter) + "\"");
                if (o.dash != null && o.dash.length > 0) { b.append(" stroke-dasharray=\""); for (int i = 0; i < o.dash.length; i++) b.append(i > 0 ? " " : "").append(n(o.dash[i])); b.append('"'); if (o.phase != 0) b.append(" stroke-dashoffset=\"").append(n(o.phase)).append('"'); }
                if (!op.isEmpty()) b.append(" stroke-opacity=\"").append(n(o.alpha)).append('"');
                b.append("/>\n");
            } else {
                double[] m = new double[6]; o.tx.getMatrix(m); boolean bold = (o.font.getStyle() & Font.BOLD) != 0, it = (o.font.getStyle() & Font.ITALIC) != 0;
                b.append("<text transform=\"matrix(").append(n(m[0])).append(' ').append(n(m[1])).append(' ').append(n(m[2])).append(' ').append(n(m[3])).append(' ').append(n(m[4])).append(' ').append(n(m[5])).append(")\" x=\"").append(n(o.x)).append("\" y=\"").append(n(o.y))
                 .append("\" font-family=\"Helvetica, Arial, 'Liberation Sans', sans-serif\" font-size=\"").append(n(o.font.getSize2D())).append('"').append(bold ? " font-weight=\"bold\"" : "").append(it ? " font-style=\"italic\"" : "")
                 .append(" fill=\"").append(hex(o.color)).append('"').append(op.isEmpty() ? "" : " fill-opacity=\"" + n(o.alpha) + "\"").append(o.adv > 0 ? " textLength=\"" + n(o.adv) + "\" lengthAdjust=\"spacingAndGlyphs\"" : "").append(" xml:space=\"preserve\">").append(esc(o.text)).append("</text>\n");
            }
        }
        if (open != null) b.append("</g>\n");
        b.append("</svg>\n"); out.write(b.toString().getBytes(StandardCharsets.UTF_8));
    }

    // ================= PDF =================
    static void pdfPath(StringBuilder b, Shape s) {
        double[] c = new double[6]; double cx = 0, cy = 0;
        for (PathIterator it = s.getPathIterator(null); !it.isDone(); it.next()) switch (it.currentSegment(c)) {
            case PathIterator.SEG_MOVETO: b.append(n(c[0])).append(' ').append(n(c[1])).append(" m\n"); cx = c[0]; cy = c[1]; break;
            case PathIterator.SEG_LINETO: b.append(n(c[0])).append(' ').append(n(c[1])).append(" l\n"); cx = c[0]; cy = c[1]; break;
            case PathIterator.SEG_QUADTO: { double x1 = cx + 2 / 3.0 * (c[0] - cx), y1 = cy + 2 / 3.0 * (c[1] - cy), x2 = c[2] + 2 / 3.0 * (c[0] - c[2]), y2 = c[3] + 2 / 3.0 * (c[1] - c[3]);
                b.append(n(x1)).append(' ').append(n(y1)).append(' ').append(n(x2)).append(' ').append(n(y2)).append(' ').append(n(c[2])).append(' ').append(n(c[3])).append(" c\n"); cx = c[2]; cy = c[3]; break; }
            case PathIterator.SEG_CUBICTO: b.append(n(c[0])).append(' ').append(n(c[1])).append(' ').append(n(c[2])).append(' ').append(n(c[3])).append(' ').append(n(c[4])).append(' ').append(n(c[5])).append(" c\n"); cx = c[4]; cy = c[5]; break;
            default: b.append("h\n");
        }
    }
    static String rgb(Color c) { return n(c.getRed() / 255.0) + " " + n(c.getGreen() / 255.0) + " " + n(c.getBlue() / 255.0); }

    /** Text is written as glyph outlines (no font embedding needed, identical layout on every viewer). */
    public static void pdf(VectorGraphics g, OutputStream out) throws IOException { pdf(g, out, null); }
    /** Text string for the document information dictionary: UTF-16BE with byte-order mark, as a hex string. */
    static String pdfText(String s) { StringBuilder b = new StringBuilder("<FEFF"); for (char ch : s.toCharArray()) b.append(String.format("%04X", (int) ch)); return b.append('>').toString(); }
    public static void pdf(VectorGraphics g, OutputStream out, String title) throws IOException {
        StringBuilder cs = new StringBuilder(); double k = 0.75;   // px -> pt
        cs.append("q ").append(n(k)).append(" 0 0 ").append(n(-k)).append(" 0 ").append(n(g.pxH * k)).append(" cm\n");
        List<Double> alphas = new ArrayList<>(); alphas.add(1.0); double curA = 1; Shape curClip = null; boolean clipOpen = false;
        for (VectorGraphics.Op o : g.ops) {
            if (o.clip != curClip) {
                if (clipOpen) { cs.append("Q\n"); clipOpen = false; curA = 1; cs.append("/GS0 gs\n"); }
                if (o.clip != null) { cs.append("q\n"); pdfPath(cs, o.clip); cs.append(o.clip.getPathIterator(null).getWindingRule() == PathIterator.WIND_EVEN_ODD ? "W* n\n" : "W n\n"); clipOpen = true; }
                curClip = o.clip;
            }
            double a = Math.round(o.alpha * 1000) / 1000.0;
            if (a != curA) { int idx = alphas.indexOf(a); if (idx < 0) { alphas.add(a); idx = alphas.size() - 1; } cs.append("/GS").append(idx).append(" gs\n"); curA = a; }
            if (o.kind == VectorGraphics.STROKE) {
                cs.append(rgb(o.color)).append(" RG ").append(n(o.lw)).append(" w ").append(o.cap == BasicStroke.CAP_ROUND ? 1 : o.cap == BasicStroke.CAP_SQUARE ? 2 : 0).append(" J ").append(o.join == BasicStroke.JOIN_ROUND ? 1 : o.join == BasicStroke.JOIN_BEVEL ? 2 : 0).append(" j ").append(n(Math.max(1, o.miter))).append(" M ");
                if (o.dash != null && o.dash.length > 0) { cs.append('['); for (float d : o.dash) cs.append(n(d)).append(' '); cs.append("] ").append(n(o.phase)).append(" d\n"); } else cs.append("[] 0 d\n");
                pdfPath(cs, o.shape); cs.append("S\n");
            } else {
                Shape sh = o.shape;
                if (o.kind == VectorGraphics.TEXT) { Shape outline = o.font.createGlyphVector(VectorGraphics.FRC, o.text).getOutline(o.x, o.y); sh = o.tx.createTransformedShape(outline); }
                cs.append(rgb(o.color)).append(" rg\n"); pdfPath(cs, sh); cs.append(sh.getPathIterator(null).getWindingRule() == PathIterator.WIND_EVEN_ODD ? "f*\n" : "f\n");
            }
        }
        if (clipOpen) cs.append("Q\n"); cs.append("Q\n");
        ByteArrayOutputStream z = new ByteArrayOutputStream(); try (DeflaterOutputStream d = new DeflaterOutputStream(z)) { d.write(cs.toString().getBytes(StandardCharsets.US_ASCII)); }
        byte[] stream = z.toByteArray(); StringBuilder gs = new StringBuilder(); for (int i = 0; i < alphas.size(); i++) gs.append("/GS").append(i).append(" << /ca ").append(n(alphas.get(i))).append(" /CA ").append(n(alphas.get(i))).append(" >> ");
        gs.append(""); if (!alphas.isEmpty() && !gs.toString().contains("/GS0")) gs.insert(0, "/GS0 << /ca 1 /CA 1 >> ");
        String[] objs = new String[4];
        objs[0] = "<< /Type /Catalog /Pages 2 0 R >>"; objs[1] = "<< /Type /Pages /Kids [3 0 R] /Count 1 >>";
        objs[2] = "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + n(g.pxW * k) + " " + n(g.pxH * k) + "] /Contents 4 0 R /Resources << /ExtGState << " + gs + ">> >> >>";
        ByteArrayOutputStream o = new ByteArrayOutputStream(); int[] off = new int[6]; o.write("%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n".getBytes(StandardCharsets.ISO_8859_1));
        for (int i = 0; i < 3; i++) { off[i + 1] = o.size(); o.write(((i + 1) + " 0 obj\n" + objs[i] + "\nendobj\n").getBytes(StandardCharsets.ISO_8859_1)); }
        off[4] = o.size(); o.write(("4 0 obj\n<< /Length " + stream.length + " /Filter /FlateDecode >>\nstream\n").getBytes(StandardCharsets.ISO_8859_1)); o.write(stream); o.write("\nendstream\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));
        off[5] = o.size(); o.write(("5 0 obj\n<< " + (title != null && !title.isEmpty() ? "/Title " + pdfText(title) + " " : "") + "/Creator " + pdfText("MetaJava") + " /Producer " + pdfText("MetaJava") + " >>\nendobj\n").getBytes(StandardCharsets.ISO_8859_1));
        int xref = o.size(); StringBuilder x = new StringBuilder("xref\n0 6\n0000000000 65535 f \n"); for (int i = 1; i <= 5; i++) x.append(String.format("%010d 00000 n \n", off[i]));
        x.append("trailer\n<< /Size 6 /Root 1 0 R /Info 5 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n"); o.write(x.toString().getBytes(StandardCharsets.ISO_8859_1)); o.writeTo(out);
    }
}
