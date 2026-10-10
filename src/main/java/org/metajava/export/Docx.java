package org.metajava.export;

import org.metajava.core.Report;

import java.io.*;
import java.util.*;

/** Writes the whole analysis output (headings, paragraphs, tables, figures, in the order of the results panel) to a Word document (.docx, Office Open XML) without external libraries. */
public final class Docx {
    private Docx() {}

    private static final long EMU_PER_PX = 9525;                 // 96 dpi
    private static final long MAX_W = 17L * 360000, MAX_H = 22L * 360000;   // A4 with 2 cm margins: 17 cm x 22 cm of picture

    private static String run(String text, boolean bold, int halfPt, String font, boolean italic, String color) {
        StringBuilder b = new StringBuilder("<w:r><w:rPr>");
        if (font != null) b.append("<w:rFonts w:ascii=\"").append(font).append("\" w:hAnsi=\"").append(font).append("\" w:cs=\"").append(font).append("\"/>");
        if (bold) b.append("<w:b/>"); if (italic) b.append("<w:i/>"); if (color != null) b.append("<w:color w:val=\"").append(color).append("\"/>");
        if (halfPt > 0) b.append("<w:sz w:val=\"").append(halfPt).append("\"/><w:szCs w:val=\"").append(halfPt).append("\"/>");
        b.append("</w:rPr><w:t xml:space=\"preserve\">").append(ZipOut.esc(text)).append("</w:t></w:r>");
        return b.toString();
    }

    private static String para(String[] lines, boolean keepNext) {
        boolean mono = false; for (String l : lines) if (l.contains("   ") && !l.startsWith("   ")) mono = true;
        StringBuilder b = new StringBuilder("<w:p><w:pPr><w:pStyle w:val=\"Body\"/><w:keepLines/>" + (keepNext ? "<w:keepNext/>" : "") + "</w:pPr>");
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i].stripTrailing(); if (!mono) l = l.stripLeading(); else l = l.replace("\t", "    ");
            if (i > 0) b.append("<w:r><w:br/></w:r>");
            b.append(mono ? run(l, false, 18, "Consolas", false, null) : run(l, false, 0, null, false, null));
        }
        return b.append("</w:p>").toString();
    }

    private static String cell(String text, int widthDxa, boolean right, boolean header, int halfPt, boolean keepNext) {
        StringBuilder b = new StringBuilder("<w:tc><w:tcPr><w:tcW w:w=\"").append(widthDxa).append("\" w:type=\"dxa\"/>");
        if (header) b.append("<w:tcBorders><w:top w:val=\"single\" w:sz=\"8\" w:space=\"0\" w:color=\"000000\"/><w:bottom w:val=\"single\" w:sz=\"6\" w:space=\"0\" w:color=\"000000\"/></w:tcBorders><w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"DCE6F1\"/>");
        b.append("<w:vAlign w:val=\"center\"/></w:tcPr><w:p><w:pPr><w:pStyle w:val=\"Cell\"/>").append(keepNext ? "<w:keepNext/>" : "").append("<w:jc w:val=\"").append(header ? "center" : right ? "right" : "left").append("\"/></w:pPr>");
        b.append(run(text.strip(), header, halfPt, null, false, null)).append("</w:p></w:tc>");
        return b.toString();
    }

    private static final int PAGE_DXA = 9864;   // A4 width minus 2 x 1021 twips of margin

    private static String table(Report.Tbl t) {
        int n = t.head.size(); int hp = n > 12 ? 14 : n > 9 ? 16 : 18; double dxaPerChar = hp * 5.2;   // approximate width of one character
        int[] w = new int[n]; int sum = 0;
        for (int j = 0; j < n; j++) { int len = Math.max(3, t.head.get(j).strip().length()); for (String[] r : t.rows) if (j < r.length) len = Math.max(len, r[j].strip().length()); w[j] = (int) Math.round((len + 2.5) * dxaPerChar); sum += w[j]; }
        if (sum > PAGE_DXA) { double k = (double) PAGE_DXA / sum; sum = 0; for (int j = 0; j < n; j++) { w[j] = (int) Math.round(w[j] * k); sum += w[j]; } }
        boolean together = t.rows.size() <= 30;   // small tables are kept on one page
        StringBuilder b = new StringBuilder("<w:tbl><w:tblPr><w:tblW w:w=\"" + sum + "\" w:type=\"dxa\"/><w:jc w:val=\"left\"/><w:tblBorders><w:bottom w:val=\"single\" w:sz=\"8\" w:space=\"0\" w:color=\"000000\"/></w:tblBorders><w:tblLayout w:type=\"fixed\"/><w:tblCellMar><w:left w:w=\"70\" w:type=\"dxa\"/><w:right w:w=\"70\" w:type=\"dxa\"/></w:tblCellMar></w:tblPr><w:tblGrid>");
        for (int j = 0; j < n; j++) b.append("<w:gridCol w:w=\"").append(w[j]).append("\"/>");
        b.append("</w:tblGrid><w:tr><w:trPr><w:cantSplit/><w:tblHeader/></w:trPr>");
        for (int j = 0; j < n; j++) b.append(cell(t.head.get(j), w[j], false, true, hp, true));
        b.append("</w:tr>");
        for (int i = 0; i < t.rows.size(); i++) { String[] r = t.rows.get(i); b.append("<w:tr><w:trPr><w:cantSplit/></w:trPr>"); for (int j = 0; j < n; j++) b.append(cell(j < r.length ? r[j] : "", w[j], t.right[j], false, hp, together && i < t.rows.size() - 1)); b.append("</w:tr>"); }
        return b.append("</w:tbl><w:p><w:pPr><w:pStyle w:val=\"Body\"/><w:spacing w:after=\"40\"/></w:pPr></w:p>").toString();
    }

    private static String picture(int id, String rid, long cx, long cy, String name) {
        return "<w:p><w:pPr><w:pStyle w:val=\"Body\"/><w:keepNext w:val=\"0\"/></w:pPr><w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\"><wp:extent cx=\"" + cx + "\" cy=\"" + cy + "\"/><wp:docPr id=\"" + id + "\" name=\"" + ZipOut.esc(name) + "\" descr=\"" + ZipOut.esc(name) + "\"/>"
            + "<wp:cNvGraphicFramePr><a:graphicFrameLocks xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" noChangeAspect=\"1\"/></wp:cNvGraphicFramePr>"
            + "<a:graphic xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><pic:pic xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">"
            + "<pic:nvPicPr><pic:cNvPr id=\"" + id + "\" name=\"" + ZipOut.esc(name) + "\"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed=\"" + rid + "\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>"
            + "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"" + cx + "\" cy=\"" + cy + "\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>";
    }

    private static final String STYLES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
        + "<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\" w:cs=\"Calibri\" w:eastAsia=\"Calibri\"/><w:sz w:val=\"21\"/><w:szCs w:val=\"21\"/><w:lang w:val=\"en-GB\"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after=\"80\" w:line=\"259\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault></w:docDefaults>"
        + "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/><w:qFormat/></w:style>"
        + "<w:style w:type=\"paragraph\" w:styleId=\"Body\"><w:name w:val=\"Body Text\"/><w:basedOn w:val=\"Normal\"/><w:qFormat/></w:style>"
        + "<w:style w:type=\"paragraph\" w:styleId=\"Cell\"><w:name w:val=\"Table Cell\"/><w:basedOn w:val=\"Normal\"/><w:pPr><w:spacing w:before=\"20\" w:after=\"20\" w:line=\"240\" w:lineRule=\"auto\"/></w:pPr></w:style>"
        + "<w:style w:type=\"paragraph\" w:styleId=\"Title\"><w:name w:val=\"Title\"/><w:basedOn w:val=\"Normal\"/><w:next w:val=\"Body\"/><w:qFormat/><w:pPr><w:spacing w:after=\"60\"/></w:pPr><w:rPr><w:b/><w:color w:val=\"1F4E8C\"/><w:sz w:val=\"44\"/><w:szCs w:val=\"44\"/></w:rPr></w:style>"
        + "<w:style w:type=\"paragraph\" w:styleId=\"Subtitle\"><w:name w:val=\"Subtitle\"/><w:basedOn w:val=\"Normal\"/><w:next w:val=\"Body\"/><w:qFormat/><w:pPr><w:spacing w:after=\"240\"/></w:pPr><w:rPr><w:color w:val=\"667085\"/><w:sz w:val=\"20\"/></w:rPr></w:style>"
        + "<w:style w:type=\"paragraph\" w:styleId=\"Heading1\"><w:name w:val=\"heading 1\"/><w:basedOn w:val=\"Normal\"/><w:next w:val=\"Body\"/><w:qFormat/><w:pPr><w:keepNext/><w:keepLines/><w:spacing w:before=\"320\" w:after=\"100\"/><w:outlineLvl w:val=\"0\"/></w:pPr><w:rPr><w:b/><w:color w:val=\"1F4E8C\"/><w:sz w:val=\"32\"/><w:szCs w:val=\"32\"/></w:rPr></w:style>"
        + "</w:styles>";

    public static void write(File f, ResultsDoc doc) throws IOException {
        StringBuilder body = new StringBuilder(); List<byte[]> media = new ArrayList<>(); int pic = 0;
        body.append("<w:p><w:pPr><w:pStyle w:val=\"Title\"/></w:pPr>").append(run("MetaJava – meta-analysis results", false, 0, null, false, null)).append("</w:p>");
        if (!doc.title.isEmpty()) body.append("<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr>").append(run(doc.title, false, 0, null, false, null)).append("</w:p>");
        if (!doc.subtitle.isEmpty()) body.append("<w:p><w:pPr><w:pStyle w:val=\"Subtitle\"/></w:pPr>").append(run(doc.subtitle, false, 0, null, false, null)).append("</w:p>");
        for (ResultsDoc.Section s : doc.sections) {
            body.append("<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr>").append(run(s.title, false, 0, null, false, null)).append("</w:p>");
            if (s.png != null) {
                media.add(s.png); pic++;
                double k = 96.0 / (s.dpi > 0 ? s.dpi : 96.0);   // pixels at the render resolution -> pixels at 96 dpi
                long cx = Math.round(s.pxW * k * EMU_PER_PX), cy = Math.round(s.pxH * k * EMU_PER_PX);
                double sc = Math.min(1.0, Math.min((double) MAX_W / cx, (double) MAX_H / cy)); cx = Math.round(cx * sc); cy = Math.round(cy * sc);
                body.append(picture(pic, "rIdImg" + pic, cx, cy, s.title));
            } else for (int bi = 0; bi < s.blocks.size(); bi++) { Object o = s.blocks.get(bi); boolean beforeTable = bi + 1 < s.blocks.size() && s.blocks.get(bi + 1) instanceof Report.Tbl; body.append(o instanceof Report.Tbl ? table((Report.Tbl) o) : para((String[]) o, beforeTable)); }
        }
        body.append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1021\" w:right=\"1021\" w:bottom=\"1021\" w:left=\"1021\" w:header=\"567\" w:footer=\"567\" w:gutter=\"0\"/></w:sectPr>");
        String document = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\"><w:body>" + body + "</w:body></w:document>";
        StringBuilder rel = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rIdSt\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        for (int i = 1; i <= media.size(); i++) rel.append("<Relationship Id=\"rIdImg").append(i).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/image").append(i).append(".png\"/>");
        rel.append("</Relationships>");
        try (ZipOut z = new ZipOut(new BufferedOutputStream(new FileOutputStream(f)))) {
            z.text("[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Default Extension=\"png\" ContentType=\"image/png\"/>"
                + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/><Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/></Types>");
            z.text("_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>");
            z.text("word/document.xml", document); z.text("word/styles.xml", STYLES); z.text("word/_rels/document.xml.rels", rel.toString());
            for (int i = 0; i < media.size(); i++) z.bytes("word/media/image" + (i + 1) + ".png", media.get(i));
        }
    }
}
