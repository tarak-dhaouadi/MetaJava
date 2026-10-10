package org.metajava.gui;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Save dialog of "Save this plot": PNG / TIFF / JPG / SVG / PDF types, file name proposed from the plot title.
 * <p>The type chosen in the dialog decides the extension of the file that is written. The file name field is only a proposal:
 * how and when Swing refreshes it after a type change differs between look-and-feels and platforms (and {@link JFileChooser#setFileFilter}
 * even clears the selected file), so the extension is not taken from what the field happens to show but fixed when the dialog is
 * confirmed ({@link #approveSelection()}). A name typed by the user with another known extension (e.g. "fig.svg") is respected.
 */
public final class PlotChooser extends JFileChooser {
    private static final String[] FORMATS = {"PNG", "TIFF", "JPG", "SVG", "PDF"};
    private final java.util.Map<String, FileNameExtensionFilter> filters = new java.util.LinkedHashMap<>();
    private final Set<String> proposed = new HashSet<>();   // names offered by the program (as opposed to typed by the user)
    private final String base; private File result;

    PlotChooser(String title, String defaultFormat) {
        base = title.replaceAll("[^A-Za-z0-9]+", "_"); setAcceptAllFileFilterUsed(false);
        filters.put("PNG", new FileNameExtensionFilter("PNG image (*.png)", "png")); filters.put("TIFF", new FileNameExtensionFilter("TIFF image (*.tif, *.tiff)", "tif", "tiff"));
        filters.put("JPG", new FileNameExtensionFilter("JPEG image (*.jpg, *.jpeg)", "jpg", "jpeg")); filters.put("SVG", new FileNameExtensionFilter("SVG vector graphics (*.svg)", "svg")); filters.put("PDF", new FileNameExtensionFilter("PDF document (*.pdf)", "pdf"));
        for (FileNameExtensionFilter f : filters.values()) addChoosableFileFilter(f);
        setFileFilter(filters.getOrDefault(defaultFormat, filters.get("PNG"))); propose(base + "." + RasterWriter.ext(format()));
        // Swing clears the selected file when the new type does not accept it, and some look-and-feels refresh the name field only after this
        // event has been delivered: propose the new name once the dialog has finished its own update.
        addPropertyChangeListener(JFileChooser.FILE_FILTER_CHANGED_PROPERTY, ev -> SwingUtilities.invokeLater(() -> {
            File cur = getSelectedFile(); String nm = cur == null ? base : cur.getName(); nm = nm.replaceAll("(?i)\\.(png|tiff?|jpe?g|svg|pdf)$", "") + "." + RasterWriter.ext(format()); propose(nm); }));
    }
    private void propose(String name) { proposed.add(name); setSelectedFile(new File(getCurrentDirectory(), name)); }

    /** "PNG", "TIFF", "JPG", "SVG" or "PDF" according to the type selected in the dialog. */
    String format() { FileFilter f = getFileFilter(); for (String k : FORMATS) if (filters.get(k) == f) return k; return "PNG"; }

    /** Format named by the extension of a file name ("PNG", "TIFF", "JPG", "SVG", "PDF"), otherwise "". */
    public static String knownFormat(String name) { String n = name.toLowerCase();
        return n.endsWith(".svg") ? "SVG" : n.endsWith(".pdf") ? "PDF" : n.endsWith(".png") ? "PNG" : n.endsWith(".tif") || n.endsWith(".tiff") ? "TIFF" : n.endsWith(".jpg") || n.endsWith(".jpeg") ? "JPG" : ""; }

    /** The file to write: the chosen type decides, unless the user typed a name carrying a different known extension. */
    public static File withExtension(File f, String format, Set<String> proposedNames) {
        String name = f.getName(), typed = knownFormat(name);
        boolean keep = !typed.isEmpty() && !proposedNames.contains(name);   // typed by the user with a known extension: keep it as typed (".tiff", ".jpeg", or another type than the one selected)
        if (!keep) { if (!typed.isEmpty()) name = name.substring(0, name.lastIndexOf('.')); name += "." + RasterWriter.ext(format); }
        File p = f.getParentFile(); return p == null ? new File(name) : new File(p, name);
    }

    /** The file chosen by the user, with the extension of the chosen type; null until the dialog has been confirmed. */
    File target() { return result; }
    String targetFormat() { return result == null ? null : knownFormat(result.getName()); }

    @Override public void approveSelection() {
        File sel = getSelectedFile(); if (sel == null) return;
        File t = withExtension(sel, format(), proposed);
        if (t.exists() && JOptionPane.showConfirmDialog(this, t.getName() + " already exists.\nDo you want to replace it?", "Confirm Save", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        result = t; super.approveSelection();
    }
}
