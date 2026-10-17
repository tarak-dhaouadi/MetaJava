package org.metajava.gui;

import org.metajava.core.DataTable;
import org.metajava.io.Json;

import javax.swing.*;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Saves and restores a whole session as a JSON project file: the data table, the analysis type, the column assignments and every setting of the window.
 * Settings are found by reflection over the window's Swing fields (combo boxes, check boxes, text fields, spinners, lists), so new settings are saved automatically.
 * The results are not stored: they are recomputed when the project is opened.
 */
final class ProjectIO {
    private ProjectIO() {}
    static final String FORMAT = "MetaJava project"; static final int VERSION = 1;
    /** Components that are saved separately (data and structure), not as plain settings. */
    private static final Set<String> SKIP = new HashSet<>(Arrays.asList("typeBox", "sheetBox"));
    private static final Set<String> LISTS = new HashSet<>(Arrays.asList("covList", "facList"));

    private static List<Field> widgetFields() {
        List<Field> out = new ArrayList<>();
        for (Field f : MainWindow.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || SKIP.contains(f.getName())) continue;
            Class<?> t = f.getType();
            if (JComboBox.class.isAssignableFrom(t) || JCheckBox.class.isAssignableFrom(t) || JTextField.class.isAssignableFrom(t) || JSpinner.class.isAssignableFrom(t) || JList.class.isAssignableFrom(t)) { f.setAccessible(true); out.add(f); }
        }
        return out;
    }

    // ------------------------------------------------------------------ save
    static Map<String, Object> capture(MainWindow w) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("format", FORMAT); m.put("version", VERSION); m.put("application", "MetaJava 0.1.0"); m.put("saved", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        m.put("analysisType", MainWindow.TYPES[w.typeBox.getSelectedIndex()][0]);
        if (w.table != null) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("source", w.fileLabel.getText()); d.put("file", w.file == null ? null : w.file.getAbsolutePath()); d.put("sheets", new ArrayList<Object>(w.sheets)); d.put("sheetIndex", w.sheetBox.getSelectedIndex()); d.put("rows", w.table.nrow);
            List<Object> cols = new ArrayList<>();
            for (int j = 0; j < w.table.names.size(); j++) { Map<String, Object> c = new LinkedHashMap<>(); c.put("name", w.table.names.get(j)); c.put("values", new ArrayList<Object>(Arrays.asList((Object[]) w.table.cols.get(j)))); cols.add(c); }
            d.put("columns", cols); m.put("data", d);
        }
        Map<String, Object> map = new LinkedHashMap<>(); for (Map.Entry<String, JComboBox<String>> e : w.maps.entrySet()) map.put(e.getKey(), e.getValue().getSelectedItem()); m.put("columnAssignment", map);
        Map<String, Object> set = new LinkedHashMap<>(), lists = new LinkedHashMap<>();
        for (Field f : widgetFields()) {
            Object o = f.get(w); if (o == null) continue; String n = f.getName();
            if (o instanceof JComboBox) { Object s = ((JComboBox<?>) o).getSelectedItem(); if (s != null) set.put(n, s.toString()); }
            else if (o instanceof JCheckBox) set.put(n, ((JCheckBox) o).isSelected());
            else if (o instanceof JTextField) set.put(n, ((JTextField) o).getText());
            else if (o instanceof JSpinner) set.put(n, (Number) ((JSpinner) o).getValue());
            else if (o instanceof JList) { List<Object> sel = new ArrayList<>(((JList<?>) o).getSelectedValuesList()); lists.put(n, sel); }
        }
        m.put("settings", set); m.put("selections", lists);
        return m;
    }

    static void save(MainWindow w, File f) throws Exception {
        String json = Json.write(capture(w));
        try (java.io.Writer out = new java.io.OutputStreamWriter(new java.io.FileOutputStream(f), "UTF-8")) { out.write(json); }
    }

    // ------------------------------------------------------------------ load
    @SuppressWarnings("unchecked")
    static void load(MainWindow w, File f) throws Exception {
        String text = new String(java.nio.file.Files.readAllBytes(f.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        Object root = Json.parse(text);
        if (!(root instanceof Map)) throw new IllegalArgumentException("This file is not a MetaJava project.");
        Map<String, Object> m = (Map<String, Object>) root;
        if (!FORMAT.equals(m.get("format"))) throw new IllegalArgumentException("This file is not a MetaJava project (missing format tag).");
        Object ver = m.get("version"); if (ver instanceof Number && ((Number) ver).intValue() > VERSION) throw new IllegalArgumentException("This project was saved by a newer version of MetaJava (format " + ((Number) ver).intValue() + ").");

        // data first: parse everything before touching the window, so a damaged file leaves the current session intact
        DataTable table = null; List<String> sheets = new ArrayList<>(); int sheetIndex = 0; String source = null; File orig = null;
        Object dd = m.get("data");
        if (dd instanceof Map) {
            Map<String, Object> d = (Map<String, Object>) dd; table = new DataTable(); int rows = d.get("rows") instanceof Number ? ((Number) d.get("rows")).intValue() : 0;
            for (Object co : (List<Object>) d.get("columns")) { Map<String, Object> c = (Map<String, Object>) co; List<Object> v = (List<Object>) c.get("values"); String[] a = new String[Math.max(rows, v.size())];
                for (int i = 0; i < a.length; i++) a[i] = i < v.size() && v.get(i) != null ? v.get(i).toString() : null; table.add(String.valueOf(c.get("name")), a); table.nrow = Math.max(table.nrow, a.length); }
            if (d.get("sheets") instanceof List) for (Object s : (List<Object>) d.get("sheets")) sheets.add(String.valueOf(s));
            if (d.get("sheetIndex") instanceof Number) sheetIndex = ((Number) d.get("sheetIndex")).intValue();
            source = d.get("source") == null ? null : d.get("source").toString();
            if (d.get("file") != null) { File o = new File(d.get("file").toString()); if (o.isFile()) orig = o; }
        }
        String type = String.valueOf(m.get("analysisType")); int ti = -1; for (int i = 0; i < MainWindow.TYPES.length; i++) if (MainWindow.TYPES[i][0].equals(type)) ti = i;
        if (ti < 0) throw new IllegalArgumentException("Unknown analysis type in the project: " + type);

        // apply
        w.suppress = true; w.autoTimer.stop();
        try {
            w.table = table; w.file = orig; w.sheets = sheets;
            w.loading = true; w.sheetBox.removeAllItems(); for (String s : sheets) w.sheetBox.addItem(s); if (sheetIndex >= 0 && sheetIndex < sheets.size()) w.sheetBox.setSelectedIndex(sheetIndex); w.loading = false;
            if (table != null) { w.showTable(table); w.fileLabel.setText(source != null ? source : "Project data"); } else { w.preview.setModel(new javax.swing.table.DefaultTableModel()); w.fileLabel.setText("No file loaded"); }
            if (w.typeBox.getSelectedIndex() != ti) w.typeBox.setSelectedIndex(ti); else w.typeChanged();   // rebuilds the column assignment rows, effect measures and methods of the type
            w.refreshColumns();

            Object ca = m.get("columnAssignment");
            if (ca instanceof Map) for (Map.Entry<String, Object> e : ((Map<String, Object>) ca).entrySet()) { JComboBox<String> c = w.maps.get(e.getKey()); if (c != null && e.getValue() != null) select(c, e.getValue().toString()); }

            Object so = m.get("settings"); Map<String, Object> set = so instanceof Map ? (Map<String, Object>) so : new HashMap<>();
            List<Field> fields = widgetFields();
            for (Field fd : fields) if (fd.getName().equals("methodBox")) apply(fd.get(w), set.get("methodBox"));   // first: choosing GLMM / LRP adjusts other settings, which the saved values then overwrite
            for (Field fd : fields) { String n = fd.getName(); if (n.equals("methodBox") || LISTS.contains(n)) continue; if (set.containsKey(n)) apply(fd.get(w), set.get(n)); }
            Object se = m.get("selections");
            if (se instanceof Map) for (Field fd : fields) if (LISTS.contains(fd.getName())) { Object v = ((Map<String, Object>) se).get(fd.getName()); if (v instanceof List) selectList((JList<String>) fd.get(w), (List<Object>) v); }
        } finally { w.suppress = false; w.loading = false; }
        w.schedule();
        if (table == null) w.showWelcome();
    }


    // ------------------------------------------------------------------ reset to defaults
    /** Settings that a reset leaves alone: data structure, column assignments and boxes rebuilt from the data or from the analysis type. */
    private static final Set<String> KEEP = new HashSet<>(Arrays.asList("studlabBox", "subgroupBox", "orderBox", "cfgFactorBox", "refBox", "smBox", "methodBox", "glmmModelBox"));

    /** Current value of every plain setting (check boxes, options, text fields, spinners), taken once at start-up as the default state. */
    static Map<String, Object> defaults(MainWindow w) throws Exception {
        Map<String, Object> d = new LinkedHashMap<>();
        for (Field f : widgetFields()) {
            Object o = f.get(w); String n = f.getName(); if (o == null || LISTS.contains(n) || KEEP.contains(n)) continue;
            if (o instanceof JComboBox) { Object s = ((JComboBox<?>) o).getSelectedItem(); if (s != null) d.put(n, s.toString()); }
            else if (o instanceof JCheckBox) d.put(n, ((JCheckBox) o).isSelected());
            else if (o instanceof JTextField) d.put(n, ((JTextField) o).getText());
            else if (o instanceof JSpinner) d.put(n, (Number) ((JSpinner) o).getValue());
        }
        return d;
    }

    /** Puts every plain setting back to the stored default. */
    static void restore(MainWindow w, Map<String, Object> d) throws Exception {
        for (Field f : widgetFields()) { String n = f.getName(); if (d.containsKey(n)) apply(f.get(w), d.get(n)); }
    }

    private static void select(JComboBox<String> c, String v) { if (v.equals("zvalue")) v = "z-value"; else if (v.equals("pvalue")) v = "p-value";
        else if (c.getItemCount() > 1 && c.getItemAt(1).equals(org.metajava.core.Bias.LIST[0])) v = org.metajava.core.Bias.withPkg(v);   // projects saved before the (meta) / (metafor) versions of the asymmetry tests: bare name = meta   // projects saved before the Drapery types were renamed
        for (int i = 0; i < c.getItemCount(); i++) if (c.getItemAt(i).equals(v)) { c.setSelectedIndex(i); return; } }
    private static void selectList(JList<String> l, List<Object> want) {
        List<Integer> idx = new ArrayList<>(); ListModel<String> md = l.getModel();
        for (Object o : want) for (int i = 0; i < md.getSize(); i++) if (md.getElementAt(i).equals(String.valueOf(o))) { idx.add(i); break; }
        if (idx.isEmpty() && md.getSize() > 0) idx.add(0);
        int[] a = new int[idx.size()]; for (int i = 0; i < a.length; i++) a[i] = idx.get(i); l.setSelectedIndices(a);
    }
    @SuppressWarnings("unchecked")
    private static void apply(Object comp, Object v) {
        if (v == null) return;
        if (comp instanceof JComboBox) select((JComboBox<String>) comp, v.toString());
        else if (comp instanceof JCheckBox) ((JCheckBox) comp).setSelected(Boolean.TRUE.equals(v));
        else if (comp instanceof JTextField) ((JTextField) comp).setText(v.toString());
        else if (comp instanceof JSpinner && v instanceof Number) { JSpinner sp = (JSpinner) comp; Object cur = sp.getValue(); Number n = (Number) v;
            try { sp.setValue(cur instanceof Integer ? (Object) n.intValue() : cur instanceof Double ? (Object) n.doubleValue() : (Object) n); } catch (Exception ignored) { } }
    }
}
