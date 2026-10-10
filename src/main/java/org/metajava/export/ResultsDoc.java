package org.metajava.export;

import org.metajava.core.Report;

import java.util.*;

/** The analysis output as shown in the results panel: an ordered list of sections (heading, then text / tables, or a figure). */
public final class ResultsDoc {
    public static final class Section {
        public final String title;
        /** String[] (one paragraph, one entry per line) or Report.Tbl, in the order printed; empty for figures. */
        public final List<Object> blocks = new ArrayList<>();
        /** PNG bytes of a figure (null for text sections), its pixel size and the resolution it was rendered at. */
        public byte[] png; public int pxW, pxH; public double dpi = 96;
        public Section(String title) { this.title = title; }
        public boolean hasTables() { for (Object o : blocks) if (o instanceof Report.Tbl) return true; return false; }
    }
    public String title = "", subtitle = "";
    public final List<Section> sections = new ArrayList<>();

    public Section text(String title, String text, List<Report.Tbl> tables) {
        Section s = new Section(title); s.blocks.addAll(Report.blocks(text, tables == null ? new ArrayList<>() : tables)); sections.add(s); return s;
    }
    public Section figure(String title, byte[] png, int w, int h, double dpi) {
        Section s = new Section(title); s.png = png; s.pxW = w; s.pxH = h; s.dpi = dpi; sections.add(s); return s;
    }
}
