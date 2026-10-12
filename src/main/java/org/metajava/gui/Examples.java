package org.metajava.gui;

import java.util.List;

/** The example data sets offered by the "Example data" button (resources under /examples). */
public final class Examples {
    private Examples() {}

    /** title: text of the menu item; file: resource name; type: analysis type code (as in MainWindow.TYPES); sm: effect measure to analyse the example with. */
    public static final class Item {
        public final String title, file, type, sm;
        Item(String title, String file, String type, String sm) { this.title = title; this.file = file; this.type = type; this.sm = sm; }
        @Override public String toString() { return title; }
    }

    public static final List<Item> ALL = List.of(
        new Item("Hazard ratios (generic effect sizes, metagen)", "HR_meta.xlsx", "gen", "HR"),
        new Item("Binary outcome (events and totals, metabin)", "Binary_outcome.xlsx", "bin", "OR"),
        new Item("Mean difference (continuous outcome, metacont)", "Mean_difference.xlsx", "cont", "MD"),
        new Item("Pearson correlation (metacor)", "Pearson_Correlation.xlsx", "cor", "ZCOR"),
        new Item("Single proportion (metaprop)", "Single_proportion.xlsx", "prop", "PLOGIT"));

    /** Keys of the columns to assign for an analysis type (the parameters of the mapping panel). */
    public static List<String> parameterKeys(String type) {
        String[][] p = MainWindow.PARAMS.get(type); java.util.ArrayList<String> k = new java.util.ArrayList<>(); if (p != null) for (String[] q : p) k.add(q[0]); return k;
    }

    /** The column the application assigns automatically to parameter key, or "(none)". */
    public static String guessColumn(String key, List<String> columns) { return MainWindow.guess(key, columns); }
}
