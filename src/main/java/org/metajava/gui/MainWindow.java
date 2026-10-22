package org.metajava.gui;

import org.metajava.core.*;
import org.metajava.io.TableReader;
import org.metajava.plot.Plots;
import org.metajava.plot.RegPlots;
import org.metajava.plot.ForestTheme;
import org.metajava.plot.ExtraPlots;
import org.metajava.plot.Canvas;
import org.metajava.plot.VectorExport;
import org.metajava.export.ResultsDoc;
import org.metajava.export.Xlsx;
import org.metajava.export.Docx;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.function.DoubleFunction;

public class MainWindow extends JFrame {
    static final String[][] TYPES = {
        {"bin", "Binary outcomes (metabin)"}, {"cont", "Continuous outcomes (metacont)"}, {"cor", "Correlations (metacor)"},
        {"gen", "Generic / precomputed effect sizes (metagen)"}, {"inc", "Incidence rate outcomes (metainc)"},
        {"mean", "Single means (metamean)"}, {"prop", "Single proportions (metaprop)"}, {"rate", "Single rates (metarate)"}};
    static final Map<String, String[][]> PARAMS = new LinkedHashMap<>();
    static {
        PARAMS.put("bin", new String[][]{{"event.e", "Events (experimental)"}, {"n.e", "Total (experimental)"}, {"event.c", "Events (control)"}, {"n.c", "Total (control)"}});
        PARAMS.put("cont", new String[][]{{"n.e", "N (experimental)"}, {"mean.e", "Mean (experimental)"}, {"sd.e", "SD (experimental)"}, {"n.c", "N (control)"}, {"mean.c", "Mean (control)"}, {"sd.c", "SD (control)"}});
        PARAMS.put("cor", new String[][]{{"cor", "Correlation"}, {"n", "Sample size"}});
        PARAMS.put("gen", new String[][]{{"TE", "Effect size (TE, log scale for ratios)"}, {"seTE", "Standard error (seTE)"}, {"lower", "Lower CI limit (optional)"}, {"upper", "Upper CI limit (optional)"}, {"n.e", "N experimental (optional)"}, {"n.c", "N control (optional)"}});
        PARAMS.put("inc", new String[][]{{"event.e", "Events (experimental)"}, {"time.e", "Person-time (experimental)"}, {"event.c", "Events (control)"}, {"time.c", "Person-time (control)"}});
        PARAMS.put("mean", new String[][]{{"n", "Sample size"}, {"mean", "Mean"}, {"sd", "SD"}});
        PARAMS.put("prop", new String[][]{{"event", "Events"}, {"n", "Total"}});
        PARAMS.put("rate", new String[][]{{"event", "Events"}, {"time", "Person-time"}});
    }
    static final String[] TAU = {"REML", "DL", "DLIT", "PM", "PMM", "MP", "ML", "HS", "HSk", "SJ", "SJIT", "HE", "EB", "GENQ", "GENQM"};

    DataTable table; File file; List<String> sheets = new ArrayList<>();
    final JComboBox<String> typeBox = Ui.combo(Arrays.stream(TYPES).map(t -> t[1]).toArray(String[]::new));
    final JPanel mapPanel = new JPanel(new GridBagLayout()); final Map<String, JComboBox<String>> maps = new LinkedHashMap<>();
    JComboBox<String> studlabBox = Ui.combo(), subgroupBox = Ui.combo(), orderBox = Ui.combo(), sheetBox = Ui.combo();
    JList<String> covList = new JList<>(), facList = new JList<>();
    // reference category of each categorical factor (factor -> level); the combo boxes only show the factor being configured
    // adjFactorBox: factor that colours the studies of the adjusted regression plot (filled with the selected factors only)
    final JComboBox<String> cfgFactorBox = Ui.combo(), refBox = Ui.combo(), adjFactorBox = Ui.combo(); final Map<String, String> refChoice = new HashMap<>(); JPanel refPanel; JLabel refNote;
    final JLabel fileLabel = Ui.muted("No file loaded");
    final JTable preview = new JTable(); final JPanel results = new JPanel(); final JScrollPane resScroll;
    // settings
    JComboBox<String> modelBox = Ui.combo("Random effects", "Common effect", "Both"), smBox = Ui.combo(), methodBox = Ui.combo("Inverse"), tauBox = Ui.combo(TAU), genqWBox = Ui.combo(MetaSpec.GENQ_WEIGHTS),
        tauCiBox = Ui.combo("Default", "QP", "BJ", "J", "GENQ", "PL", "HT", "Wald", "Wald.log", "Wald.sqrt", "None"), randCiBox = Ui.combo("classic", "HK", "KR"), adhocBox = Ui.combo("none", "se", "ci", "IQWiG6"),
        predBox = Ui.combo("V", "HTS", "HK", "HK-PR", "KR", "KR-PR", "NNF", "S"), adhocPiBox = Ui.combo("none", "se"), i2Box = Ui.combo("Q", "tau2"), commonCiBox = Ui.combo("classic", "IVhet"), incrBox = Ui.combo("only 0", "if 0 all", "all"),
        smdBox = Ui.combo("Hedges", "Cohen", "Glass"), glmmModelBox = Ui.combo("UM.FS", "UM.RS", "CM.EL", "CM.AL"), nagqBox = Ui.combo("7", "1", "2", "3", "5", "9", "11", "15", "21"), ciBox = Ui.combo("Default", "z", "t", "CP", "WS", "WSCC", "AC", "SA", "SM", "exact"), genMode = Ui.combo("Effect size & SE", "Estimate & CI (natural scale)");
    JCheckBox predChk = Ui.check("Prediction interval", true), backChk = Ui.check("Back-transform results", true), mhExact = Ui.check("MH.exact (no continuity correction in MH)", false),
        pooledvar = Ui.check("Pooled variance (MD)", false), allStudies = Ui.check("Include studies with zero events in both arms", false), tauCommon = Ui.check("Assume common tau\u00B2 across subgroups", false);
    JTextField genqCustomF = new JTextField("", 14), predLevelF = new JTextField("95", 5), levelF = new JTextField("0.95", 6), incrF = new JTextField("0.5", 6), nullF = new JTextField("", 6), targetF = new JTextField("", 6), pscaleF = new JTextField("1", 6), titleF = new JTextField(14);
    // forest options
    JCheckBox forestChk = Ui.check("Forest plot", true), fDetails = Ui.check("Analysis details", false), fOverallTest = Ui.check("Overall effect test", true), fHet = Ui.check("Heterogeneity statistics", true), fCiI2 = Ui.check("I\u00B2 confidence interval", false), fCiTau = Ui.check("Tau\u00B2 confidence interval", false);
    JComboBox<String> sortBox = Ui.combo("Original order", "Effect size", "Standard error", "Weight", "Study label"), sortDir = Ui.combo("Ascending", "Descending");
    // forest theme and the options that override it
    JComboBox<String> themeBox = Ui.combo(ForestTheme.THEMES), squareColBox = forestCombo(ForestTheme.COLOURS, 0), diamondFillBox = forestCombo(ForestTheme.FILL_COLOURS, 1), diamondBorderBox = forestCombo(ForestTheme.FILL_COLOURS, 2),
        nullColBox = forestCombo(ForestTheme.COLOURS, 3), nullStyleBox = Ui.combo(ForestTheme.LINE_STYLES), pooledColBox = forestCombo(ForestTheme.COLOURS, 4), pooledStyleBox = Ui.combo(ForestTheme.LINE_STYLES);
    JComboBox<String> regStyleBox = Ui.combo(RegPlots.STYLES), regColPoints = themedColourCombo(RegPlots.COLOURS_AUTO, RegPlots.AUTO, RegPlots::rgb, regStyleBox, RegPlots::defaultColour, 0), regColLine = themedColourCombo(RegPlots.COLOURS_AUTO, RegPlots.AUTO, RegPlots::rgb, regStyleBox, RegPlots::defaultColour, 1), regColBand = themedColourCombo(RegPlots.COLOURS_AUTO, RegPlots.AUTO, RegPlots::rgb, regStyleBox, RegPlots::defaultColour, 2), regColBandLine = themedColourCombo(RegPlots.COLOURS_AUTO, RegPlots.AUTO, RegPlots::rgb, regStyleBox, RegPlots::defaultColour, 3), regColPred = themedColourCombo(RegPlots.COLOURS_AUTO, RegPlots.AUTO, RegPlots::rgb, regStyleBox, RegPlots::defaultColour, 4), regColPredLine = themedColourCombo(RegPlots.COLOURS_AUTO, RegPlots.AUTO, RegPlots::rgb, regStyleBox, RegPlots::defaultColour, 5), regSymbolBox = Ui.combo(RegPlots.SYMBOLS);
    JSpinner regSymSize = new JSpinner(new SpinnerNumberModel(1.0, 0.3, 4.0, 0.1));
    JSpinner baujatSymSize = new JSpinner(new SpinnerNumberModel(1.0, 0.3, 4.0, 0.1));   // Baujat symbol size (multiplier, like "Size of study symbol" of the bubble plot)
    JCheckBox baujatQuadChk = Ui.check("Baujat quadrants", false);   // quadrants: shaded high-influence / high-heterogeneity quadrant and the two quadrant lines
    JComboBox<String> baujatQuadBox = Ui.combo(org.metajava.plot.BaujatPlot.QUAD_RULES), baujatQuadColBox = colourCombo(org.metajava.plot.BaujatPlot.QUAD_COLOURS, org.metajava.plot.BaujatPlot.QUAD_AUTO, org.metajava.plot.BaujatPlot::quadColourOf);   // position of the quadrant lines
    /** Forest-plot colour drop-down: swatch in front of every colour name (none for "Theme default" and "None"), like the Baujat, radial and L'Abbe lists. */
    /** Colour drop-down whose "Theme default" entry shows the colour the currently selected theme prescribes (dflt: theme name and role to colour; null = element not drawn, no swatch); repaints when the theme changes. */
    static JComboBox<String> themedColourCombo(String[] items, String auto, java.util.function.Function<String, Color> named, JComboBox<String> theme, java.util.function.BiFunction<String, Integer, Color> dflt, int role) {
        JComboBox<String> c = colourCombo(items, auto, v -> auto.equals(v) ? dflt.apply((String) theme.getSelectedItem(), role) : named.apply(v)); theme.addActionListener(e -> c.repaint()); return c; }
    JComboBox<String> forestCombo(String[] items, int role) { return themedColourCombo(items, ForestTheme.AUTO, ForestTheme::colourOf, themeBox, ForestTheme::defaultColour, role); }
    static JComboBox<String> forestColourCombo(String[] items) { return colourCombo(items, ForestTheme.AUTO, ForestTheme::colourOf); }
    /** Combo box of the named plot colours, with a colour swatch in front of every name. */
    static JComboBox<String> colourCombo(String dflt) { return colourCombo(RegPlots.COLOURS, dflt); }
    /** Drop-down of named colours with a colour swatch in front of every item (items "Theme default" show no swatch, "White" a white one). */
    static JComboBox<String> colourCombo(String[] items, String dflt) { return colourCombo(items, dflt, v -> { Color col = org.metajava.plot.BaujatPlot.colourOf(v); if (col == null && !org.metajava.plot.BaujatPlot.AUTO.equals(v)) col = RegPlots.rgb(v); return col; }); }
    /** Same, with the colour of every name given by {@code res} (null: no swatch). */
    static JComboBox<String> colourCombo(String[] items, String dflt, java.util.function.Function<String, Color> res) { JComboBox<String> c = Ui.combo(items); c.setSelectedItem(dflt);
        c.setRenderer(new DefaultListCellRenderer() { @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean foc) { JLabel x = (JLabel) super.getListCellRendererComponent(l, v, i, sel, foc);
            final Color fc = res.apply(String.valueOf(v)); x.setIcon(fc == null ? null : new Icon() { public int getIconWidth() { return 14; } public int getIconHeight() { return 12; } public void paintIcon(Component cc, Graphics gg, int px, int py) { gg.setColor(fc); gg.fillRect(px, py, 14, 12); gg.setColor(Color.GRAY); gg.drawRect(px, py, 13, 11); } }); return x; } }); return c; }
    JComboBox<String> baujatModelBox = Ui.combo(org.metajava.plot.BaujatPlot.MODELS), baujatThemeBox = Ui.combo(org.metajava.plot.BaujatPlot.THEMES), baujatSymbolBox = themedColourCombo(org.metajava.plot.BaujatPlot.SYMBOL_COLOURS, org.metajava.plot.BaujatPlot.AUTO, org.metajava.plot.BaujatPlot::colourOf, baujatThemeBox, org.metajava.plot.BaujatPlot::defaultColour, 0), baujatBackBox = themedColourCombo(org.metajava.plot.BaujatPlot.BACK_COLOURS, org.metajava.plot.BaujatPlot.AUTO, org.metajava.plot.BaujatPlot::colourOf, baujatThemeBox, org.metajava.plot.BaujatPlot::defaultColour, 1);   // Baujat plot theme / symbol colour / background colour
    JComboBox<String> radialModelBox = Ui.combo(org.metajava.plot.RadialPlot.MODELS), radialThemeBox = Ui.combo(org.metajava.plot.RadialPlot.THEMES), radialSymbolBox = themedColourCombo(org.metajava.plot.RadialPlot.COLOURS, org.metajava.plot.RadialPlot.AUTO, org.metajava.plot.BaujatPlot::colourOf, radialThemeBox, org.metajava.plot.RadialPlot::defaultColour, 0), radialBackBox = themedColourCombo(org.metajava.plot.RadialPlot.BACK_COLOURS, org.metajava.plot.RadialPlot.AUTO, org.metajava.plot.BaujatPlot::colourOf, radialThemeBox, org.metajava.plot.RadialPlot::defaultColour, 1), radialCircleBox = themedColourCombo(org.metajava.plot.RadialPlot.COLOURS, org.metajava.plot.RadialPlot.AUTO, org.metajava.plot.BaujatPlot::colourOf, radialThemeBox, org.metajava.plot.RadialPlot::defaultColour, 2),
        radialRegBox = themedColourCombo(org.metajava.plot.RadialPlot.COLOURS, org.metajava.plot.RadialPlot.AUTO, org.metajava.plot.BaujatPlot::colourOf, radialThemeBox, org.metajava.plot.RadialPlot::defaultColour, 3), radialRefBox = themedColourCombo(org.metajava.plot.RadialPlot.COLOURS, org.metajava.plot.RadialPlot.AUTO, org.metajava.plot.BaujatPlot::colourOf, radialThemeBox, org.metajava.plot.RadialPlot::defaultColour, 4), radialCiBox = themedColourCombo(org.metajava.plot.RadialPlot.COLOURS, org.metajava.plot.RadialPlot.AUTO, org.metajava.plot.BaujatPlot::colourOf, radialThemeBox, org.metajava.plot.RadialPlot::defaultColour, 5), radialCiLineBox = themedColourCombo(org.metajava.plot.RadialPlot.COLOURS, org.metajava.plot.RadialPlot.AUTO, org.metajava.plot.BaujatPlot::colourOf, radialThemeBox, org.metajava.plot.RadialPlot::defaultColour, 6);   // Radial plot theme and colours
    /** \"Avoid label overlap\": boxed study labels with leader lines that never cover a study symbol (LabelPlacer), for each plot. */
    JCheckBox regRepelChk = Ui.check("Avoid label overlap (bubble and adjusted regression plots)", false), funnelRepelChk = Ui.check("Avoid label overlap", false), doiRepelChk = Ui.check("Avoid label overlap (Doi plot)", false), petRepelChk = Ui.check("Avoid label overlap (PET-PEESE plot)", false),
        radialRepelChk = Ui.check("Avoid label overlap", false), labbeRepelChk = Ui.check("Avoid label overlap", false),
        biasRepelChk = Ui.check("Avoid label overlap (asymmetry plot)", false), biasCiChk = Ui.check("95% confidence interval", false), biasPiChk = Ui.check("95% prediction interval", false);
    JCheckBox radialLabChk = Ui.check("Study labels", true), labbeLabChk = Ui.check("Study labels", true);   // study labels of the radial and L'Abbe plots
    JComboBox<String> labbeThemeBox = Ui.combo(org.metajava.plot.LabbePlot.THEMES), labbeSymbolBox = themedColourCombo(org.metajava.plot.LabbePlot.COLOURS, org.metajava.plot.LabbePlot.AUTO, org.metajava.plot.BaujatPlot::colourOf, labbeThemeBox, org.metajava.plot.LabbePlot::defaultColour, 0), labbeBackBox = themedColourCombo(org.metajava.plot.LabbePlot.BACK_COLOURS, org.metajava.plot.LabbePlot.AUTO, org.metajava.plot.BaujatPlot::colourOf, labbeThemeBox, org.metajava.plot.LabbePlot::defaultColour, 1),
        labbeCircleBox = themedColourCombo(org.metajava.plot.LabbePlot.COLOURS, org.metajava.plot.LabbePlot.AUTO, org.metajava.plot.BaujatPlot::colourOf, labbeThemeBox, org.metajava.plot.LabbePlot::defaultColour, 2), labbeRefBox = themedColourCombo(org.metajava.plot.LabbePlot.COLOURS, org.metajava.plot.LabbePlot.AUTO, org.metajava.plot.BaujatPlot::colourOf, labbeThemeBox, org.metajava.plot.LabbePlot::defaultColour, 3), labbeEstBox = themedColourCombo(org.metajava.plot.LabbePlot.COLOURS, org.metajava.plot.LabbePlot.AUTO, org.metajava.plot.BaujatPlot::colourOf, labbeThemeBox, org.metajava.plot.LabbePlot::defaultColour, 4),
        labbeCiBox = themedColourCombo(org.metajava.plot.LabbePlot.COLOURS, org.metajava.plot.LabbePlot.AUTO, org.metajava.plot.BaujatPlot::colourOf, labbeThemeBox, org.metajava.plot.LabbePlot::defaultColour, 5), labbePiBox = themedColourCombo(org.metajava.plot.LabbePlot.COLOURS, org.metajava.plot.LabbePlot.AUTO, org.metajava.plot.BaujatPlot::colourOf, labbeThemeBox, org.metajava.plot.LabbePlot::defaultColour, 6);   // L'Abbe plot theme and colours
    // drapery plot options (defaults = meta::drapery)
    JComboBox<String> drapTypeBox = Ui.combo(org.metajava.plot.Drapery.TYPES), drapLayoutBox = Ui.combo(org.metajava.plot.Drapery.LAYOUTS), drapLabelsBox = Ui.combo(org.metajava.plot.Drapery.LABELS), drapModelBox = Ui.combo(org.metajava.plot.Drapery.MODELS),
        drapStudyColBox = colourCombo(org.metajava.plot.Drapery.STUDY_COLOURS, org.metajava.plot.Drapery.STUDY_DEFAULT, org.metajava.plot.Drapery::colour), drapPredColBox = colourCombo(org.metajava.plot.Drapery.COLOURS, org.metajava.plot.Drapery.PRED_DEFAULT, org.metajava.plot.Drapery::colour),
        drapRandomColBox = colourCombo(org.metajava.plot.Drapery.COLOURS, org.metajava.plot.Drapery.RANDOM_DEFAULT, org.metajava.plot.Drapery::colour), drapCommonColBox = colourCombo(org.metajava.plot.Drapery.COLOURS, org.metajava.plot.Drapery.COMMON_DEFAULT, org.metajava.plot.Drapery::colour);
    JSpinner drapAngleSp = new JSpinner(new SpinnerNumberModel(0, 0, 90, 5));
    JComboBox<String> funnelThemeBox = Ui.combo(org.metajava.plot.FunnelThemes.NAMES);   // Default / MetaWin style / STATA style / metafor style
    JTextField xLeft = new JTextField(10), xRight = new JTextField(10); JTextField drapTargetF = new JTextField(10), xMinF = new JTextField(10), xMaxF = new JTextField(10), xTickF = new JTextField(10); JSpinner digitsSp = new JSpinner(new SpinnerNumberModel(2, 0, 6, 1)), spacingSp = new JSpinner(new SpinnerNumberModel(1.0, 0.6, 2.5, 0.1));
    // other analyses
    JCheckBox sgChk = Ui.check("Subgroup analysis (uses the subgroup column)", false), sgPred = Ui.check("Subgroup prediction interval", false), sgTestEffect = Ui.check("Test for effect in subgroup", false), sgDetails = Ui.check("Details of meta-analysis methods", false), regChk = Ui.check("Meta-regression", false), bubbleChk = Ui.check("Bubble plot", true), regLine = Ui.check("Regression line", true), regCI = Ui.check("Confidence interval", false), regPI = Ui.check("Prediction interval", false), regLog = Ui.check("Log scale for ratio measures (straight regression line)", false), adjChk = Ui.check("Regression plot with categorical moderator adjustment", false), regIntercept = Ui.check("Intercept", true),
        looChk = Ui.check("Leave-one-out analysis", false), looForest = Ui.check("Forest plot", true), cumChk = Ui.check("Cumulative meta-analysis", false), cumForest = Ui.check("Forest plot", true),
        funnelChk = Ui.check("Funnel plot", false), funnelPrecChk = Ui.check("Funnel plot of precision", false), funnelBackChk = Ui.check("Back-transform effect-size for funnel plot", false), contourChk = Ui.check("Contour-enhanced", false), labChk = Ui.check("Study labels", true), biasChk = Ui.check("Test for funnel plot asymmetry", false), biasPlotChk = Ui.check("Plot for funnel plot asymmetry", true),
        tfChk = Ui.check("Trim and fill", false), tfPrecChk = Ui.check("Trim and fill funnel plot of precision", false), infChk = Ui.check("Outlier & influence diagnostics (metafor)", false), baujatChk = Ui.check("Baujat plot", false), radialChk = Ui.check("Radial (Galbraith) plot", false),
        drapChk = Ui.check("Drapery plot", false), drapPredChk = Ui.check("Prediction region", true), drapReportChk = Ui.check("Drapery plot analysis and interpretation (statistical report + narrative)", true), doiChk = Ui.check("Doi plot and LFK index", false), doiApexChk = Ui.check("Mark the apex (dashed line)", false), labbeChk = Ui.check("L\u2019Abb\u00E9 plot (binary outcomes)", false);
    JCheckBox petChk = Ui.check("PET-PEESE (small-study effects)", false), petPlotChk = Ui.check("PET-PEESE plot", false), selChk = Ui.check("Selection model (3-parameter)", false), failChk = Ui.check("Fail-safe N", false);
    JComboBox<String> selDir = Ui.combo("Auto", "Positive", "Negative");
    // meta-regression extras, subgroup plots, GOSH
    JCheckBox permChk = Ui.check("Permutation test (permutest)", false), permExactChk = Ui.check("Exact test (all unique permutations)", false), cmpChk = Ui.check("Model comparison table (AIC / BIC / R\u00B2)", false),
        orchardChk = Ui.check("Orchard plot (subgroups)", false), caterChk = Ui.check("Caterpillar plot (subgroups)", false), goshChk = Ui.check("GOSH plot (heterogeneity diagnostics)", false), goshTrimOrdChk = Ui.check("Limit the ordinary GOSH plot to the central 99% of subsets", false), goshHlChk = Ui.check("Colour subsets by the most influential study", false), goshClChk = Ui.check("Detect clusters of subsets (colour by cluster)", false), goshRegChk = Ui.check("GOSH for meta-regression (coefficients and residual heterogeneity)", false), goshTrimChk = Ui.check("Limit the GOSH meta-regression plot axes to the central 99% of the subsets", true);
    JSpinner permIterSp = new JSpinner(new SpinnerNumberModel(5000, 100, 100000, 1000)), goshSubSp = new JSpinner(new SpinnerNumberModel(20000, 1000, 500000, 5000));
    JTextField permSeedF = new JTextField("1", 6), goshSeedF = new JTextField("1", 6);
    JSpinner permBudgetSp = new JSpinner(new SpinnerNumberModel((int) Math.max(1, Math.min(604800, Math.round(Perm.defaultBudgetSeconds()))), 1, 604800, 60));   // seconds an exact permutation test may be estimated to need
    static String[] biasItems() { String[] a = new String[Bias.LIST.length + 1]; a[0] = "Default"; System.arraycopy(Bias.LIST, 0, a, 1, Bias.LIST.length); return a; }
    JComboBox<String> biasMethod = Ui.combo(biasItems());
    JComboBox<String> tfSide = Ui.combo("Auto", "Left", "Right"), tfEst = Ui.combo("L0", "R0", "Q0"), tfModel = Ui.combo("As the analysis (metafor)", "Common effect (meta)");
    JSpinner dpiSp = new JSpinner(new SpinnerNumberModel(300, 72, 1200, 100)); JComboBox<String> fmtBox = Ui.combo("PNG", "TIFF", "JPG", "SVG", "PDF"); JComboBox<String> goshPalBox = Ui.combo(ExtraPlots.PALETTE_NAMES);
    final List<Object[]> plots = new ArrayList<>(); // {title, DoubleFunction<BufferedImage>}
    final JLabel status = Ui.muted("Ready");
    final ProgressStrip strip = new ProgressStrip();
    final List<Item> shown = new ArrayList<>(); String shownTitle = "", shownSource = "";   // the output as displayed (for the Excel / Word export)
    String lastText = "";
    final List<JTextArea> textAreas = new ArrayList<>(); int wrapCols = -1;   // text blocks of the results panel (their unwrapped text is kept in the "raw" client property)
    boolean suppress = false; final javax.swing.Timer autoTimer = new javax.swing.Timer(350, e -> runImpl(true));

    public MainWindow() {
        super("MetaJava \u2013 Meta-analysis (meta / metafor)");
        setDefaultCloseOperation(EXIT_ON_CLOSE); setSize(1500, 900); setLocationRelativeTo(null);
        getContentPane().setBackground(Ui.BG); setLayout(new BorderLayout());
        JPanel north = new JPanel(new BorderLayout()); north.setOpaque(false); north.add(header(), BorderLayout.NORTH); north.add(strip, BorderLayout.CENTER); add(north, BorderLayout.NORTH);
        JPanel left = leftPanel(); JScrollPane ls = new JScrollPane(left); ls.setBorder(null); ls.getVerticalScrollBar().setUnitIncrement(16); ls.setPreferredSize(new Dimension(520, 800));
        results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS)); results.setBackground(Color.WHITE); results.setBorder(new EmptyBorder(14, 18, 14, 18));
        resScroll = new JScrollPane(results); resScroll.getVerticalScrollBar().setUnitIncrement(24); resScroll.setBorder(new LineBorder(Ui.BORDER));
        resScroll.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() { public void componentResized(java.awt.event.ComponentEvent e) { rewrap(); } });   // long lines are re-wrapped to the window width, so no sideways scrolling is needed
        JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, ls, resScroll); sp.setDividerLocation(520); sp.setBorder(new EmptyBorder(8, 8, 0, 8)); sp.setOpaque(false);
        add(sp, BorderLayout.CENTER);
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 5)); bar.setBackground(Ui.BG); bar.add(status); busy.setPreferredSize(new Dimension(160, 12)); busy.setVisible(false); bar.add(busy); add(bar, BorderLayout.SOUTH);
        autoTimer.setRepeats(false); typeBox.addActionListener(e -> typeChanged()); typeChanged(); hook(left);
        showWelcome();
        try { defaultSettings = ProjectIO.defaults(this); } catch (Exception ex) { defaultSettings = null; }
    }

    JComponent header() {
        JPanel h = new JPanel(new BorderLayout()) { protected void paintComponent(Graphics g) { Graphics2D g2 = (Graphics2D) g; g2.setPaint(new GradientPaint(0, 0, Ui.PRIMARY_DARK, getWidth(), 0, Ui.PRIMARY)); g2.fillRect(0, 0, getWidth(), getHeight()); } };
        h.setBorder(new EmptyBorder(12, 20, 12, 20));
        JLabel t = new JLabel("MetaJava"); t.setForeground(Color.WHITE); t.setFont(new Font("SansSerif", Font.BOLD, 22));
        JLabel s = new JLabel("  Meta-analysis based on the R packages meta and metafor"); s.setForeground(new Color(0xC9D8EE)); s.setFont(new Font("SansSerif", Font.PLAIN, 13));
        JPanel l = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); l.setOpaque(false); l.add(t); l.add(s);
        PillButton run = new PillButton("Run analysis", Icons.play(15, Color.WHITE), "Run the analysis now").colors(new Color(0x2E9E5B), Color.WHITE); run.addActionListener(e -> run());
        JLabel cl = new JLabel("CPU threads"); cl.setForeground(new Color(0xC9D8EE)); cl.setFont(new Font("SansSerif", Font.PLAIN, 15));
        JSpinner th = new JSpinner(new SpinnerNumberModel(Par.threads(), 1, Par.maxThreads(), 1)); th.setToolTipText("Worker threads for parallel analyses (this machine: " + Par.maxThreads() + " cores)");
        th.setFont(Ui.BOLD.deriveFont(20f)); th.setPreferredSize(new Dimension(76, 38));
        { JFormattedTextField tf = ((JSpinner.DefaultEditor) th.getEditor()).getTextField(); tf.setFont(Ui.BOLD.deriveFont(20f)); tf.setHorizontalAlignment(JTextField.CENTER); }   // number centred in the white box
        th.addChangeListener(e -> { int v = (Integer) th.getValue(); Par.setThreads(v); try { PREFS.putInt("threads", v); } catch (Exception ex) { } });
        PillButton reset = new PillButton("Reset", Icons.reset(17, Ui.PRIMARY_DARK), "Reset all options and check boxes to their default values (the data, analysis type and column assignments are kept)"); reset.addActionListener(e -> resetSettings());
        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0)); east.setOpaque(false); east.add(cl); east.add(th); east.add(reset); east.add(run);
        PillButton load = new PillButton("Load project", Icons.folderOpen(17, Ui.PRIMARY_DARK), "Open a saved MetaJava project (.json): data, assignments and all settings"), save = new PillButton("Save project", Icons.floppy(17, Ui.PRIMARY_DARK), "Save the session (data, assignments and all settings) to a .json file");
        load.addActionListener(e -> loadProject()); save.addActionListener(e -> saveProject());
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0)); btns.setOpaque(false); btns.add(load); btns.add(save);
        JPanel row = new JPanel(new BorderLayout()); row.setOpaque(false); row.setBorder(new EmptyBorder(12, 0, 0, 0)); row.add(btns, BorderLayout.WEST); row.add(east, BorderLayout.EAST);
        JPanel top = new JPanel(new BorderLayout()); top.setOpaque(false); top.add(l, BorderLayout.WEST);
        h.add(top, BorderLayout.NORTH); h.add(row, BorderLayout.SOUTH);
        return h;
    }

    JPanel leftPanel() {
        JPanel p = new JPanel(); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS)); p.setBackground(Ui.BG); p.setBorder(new EmptyBorder(0, 0, 8, 6));
        // data
        Ui.Section d = new Ui.Section("1. Data", true);
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); btns.setOpaque(false);
        JButton open = Ui.button("Open file\u2026", true), ex = Ui.button("Example data", false); open.addActionListener(e -> openFile()); ex.addActionListener(e -> showExampleMenu(ex));
        btns.add(open); btns.add(ex); d.row(null, btns); d.row(null, fileLabel); d.row("Sheet", sheetBox); sheetBox.addActionListener(e -> { if (file != null && sheetBox.getSelectedIndex() >= 0 && !loading) load(file, sheetBox.getSelectedIndex()); });
        preview.setFillsViewportHeight(true); preview.setFont(Ui.BASE.deriveFont(11f)); preview.setRowHeight(20); preview.getTableHeader().setFont(Ui.BOLD.deriveFont(11f));
        JScrollPane ps = new JScrollPane(preview); ps.setPreferredSize(new Dimension(380, 140)); d.row(null, ps);
        p.add(d); p.add(Box.createVerticalStrut(8));
        // analysis type + variables
        Ui.Section v = new Ui.Section("2. Analysis type and variables", true); v.row("Type", typeBox); targetF.setToolTipText("Single proportions, means and rates have no natural null hypothesis. Enter the target value (on the natural scale: a proportion, a mean or a rate, in the units of the data) to test the pooled effect against it; the tests and p-values of the report and of the forest plots then refer to it. Empty: no test and no p-value is reported."); v.row("Target effect-size", targetF);
        mapPanel.setOpaque(false); v.row(null, mapPanel); v.row("Study label", studlabBox); v.row("Order (cumulative)", orderBox); v.row("Input mode (generic)", genMode);
        covList.setVisibleRowCount(4); facList.setVisibleRowCount(4); noneBehaviour(covList); noneBehaviour(facList);
        p.add(v); p.add(Box.createVerticalStrut(8));
        // meta-analysis
        Ui.Section m = new Ui.Section("3. Meta-analysis settings", false);
        m.row("Model", modelBox); m.row("Effect measure (sm)", smBox); m.row("Pooling method", methodBox); m.row("GLMM model (model.glmm)", glmmModelBox); m.row("GLMM quadrature points (nAGQ)", nagqBox); m.row("Heterogeneity estimator (tau\u00B2)", tauBox); genqWBox.setToolTipText("Weights of the generalised Q-statistic estimators GENQ and GENQM (also used by their rows of the sensitivity table). All other tau\u00B2 estimators ignore this option."); m.row("Weights (GENQ, GENQM only)", genqWBox); genqCustomF.setToolTipText("One weight per study, in the order of the data, separated by commas or spaces (for instance 1, 2, 0.5, 3). Used when Weights = Custom."); m.row("Custom weights", genqCustomF); genqWBox.addActionListener(e -> genqCustomF.setEnabled("Custom".equals(genqWBox.getSelectedItem()))); genqCustomF.setEnabled(false); m.row("CI method for tau\u00B2", tauCiBox);
        m.row("Random effects CI method", randCiBox); m.row("Ad hoc HK variance correction", adhocBox); m.row("Common effect CI method", commonCiBox); m.row("Prediction interval method", predBox); m.row("Ad hoc HK correction (PI)", adhocPiBox); m.row(null, predChk); { JPanel pp = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0)); pp.setOpaque(false); pp.add(predLevelF); pp.add(Ui.label("%")); m.row("PI confidence level", pp); }
        m.row("I\u00B2 calculation", i2Box); m.row("Confidence level", levelF); m.row("Null effect (natural scale)", nullF); m.row(null, backChk); m.row("Individual study CI method", ciBox);
        m.row("Continuity correction (incr)", incrF); m.row("Apply correction", incrBox); m.row(null, mhExact); m.row(null, allStudies); m.row("SMD method", smdBox); m.row(null, pooledvar); m.row("Scale factor (proportions/rates)", pscaleF); m.row("Title", titleF);
        p.add(m); p.add(Box.createVerticalStrut(8));
        Ui.Section f = new Ui.Section("4. Forest plot", false);
        f.row(null, forestChk);
        themeBox.setToolTipText("Look of every forest plot (main, subgroup, leave-one-out and cumulative). Default = meta (R) style; RevMan 5 = Cochrane Review Manager 5; BMJ = BMJ journal style; STATA = Stata metan style; metafor style = R package metafor (black squares and diamond, red prediction bar, rules under the header and above the summary, axis title under the axis). The colour options below apply to every theme");
        squareColBox.setToolTipText("Fill colour of the study squares (diamonds in the BMJ style). Theme default keeps the colour of the selected theme");
        diamondFillBox.setToolTipText("Fill colour of the pooled-effect diamonds. None leaves the diamond hollow"); diamondBorderBox.setToolTipText("Border colour of the pooled-effect diamonds. None draws no border");
        nullColBox.setToolTipText("Colour of the vertical line at the null effect"); nullStyleBox.setToolTipText("Line style of the vertical line at the null effect");
        pooledColBox.setToolTipText("Colour of the vertical line at the pooled effect"); pooledStyleBox.setToolTipText("Line style of the vertical line at the pooled effect");
        f.row("Theme", themeBox); f.row("Study-square fill colour", squareColBox);
        f.row(null, pairRow("Diamond fill", diamondFillBox, "Diamond border", diamondBorderBox));
        f.row(null, pairRow("Null-effect line colour", nullColBox, "Null-effect line style", nullStyleBox));
        f.row(null, pairRow("Pooled-effect line colour", pooledColBox, "Pooled-effect line style", pooledStyleBox));
        f.row("Sort by", sortBox); f.row("Order", sortDir); f.row(null, fOverallTest); f.row(null, fHet); f.row(null, fCiI2); f.row(null, fCiTau); f.row(null, fDetails); xMinF.setToolTipText("Lower limit of the x-axis, on the scale shown on the axis (for ratio measures: the ratio, e.g. 0.2). Blank = automatic."); xMaxF.setToolTipText("Upper limit of the x-axis, on the scale shown on the axis (for ratio measures: the ratio, e.g. 5). Blank = automatic."); xTickF.setToolTipText("Distance between two ticks of the x-axis (e.g. 0.1). Blank = automatic. Ignored on logarithmic axes (ratio measures).");
        f.row("X-axis minimum", xMinF); f.row("X-axis maximum", xMaxF); f.row("X-axis tick interval", xTickF); f.row("X-axis left label", xLeft); f.row("X-axis right label", xRight); f.row("Digits", digitsSp); f.row("Row spacing", spacingSp);
        p.add(f); p.add(Box.createVerticalStrut(8));
        Ui.Section sg = new Ui.Section("5. Subgroup analysis", false); sg.row("Subgroup variable", subgroupBox); sg.row(null, sgChk); sg.row(null, tauCommon); sg.row(null, sgPred); sg.row(null, sgTestEffect); sg.row(null, sgDetails); sg.row(null, orchardChk); sg.row(null, caterChk); p.add(sg); p.add(Box.createVerticalStrut(8));
        Ui.Section rg = new Ui.Section("6. Meta-regression", false); rg.row(null, regChk); rg.row("Covariates (numeric)", new JScrollPane(covList)); rg.row("Factors (categorical)", new JScrollPane(facList)); rg.row(null, buildRefPanel()); rg.row(null, regIntercept); regIntercept.addActionListener(e -> updateRefPanel()); rg.row(null, bubbleChk); rg.row(null, regLine); rg.row(null, regCI); rg.row(null, regPI); rg.row(null, regLog); rg.row(null, regRepelChk);
        regCI.setToolTipText("Confidence bounds of the regression line (dashed lines) with the area between them filled in a light colour");
        regLog.setToolTipText("Ratio measures (OR, RR, HR, ...) are modelled on the log scale, so the regression line is straight there. By default the plots show the back-transformed values and the line is a curve (exp of a straight line). With this box the y-axis is logarithmic (labelled with the ratio values) and the line, its bands and the study points are drawn on the log scale: the line is straight. Same model, same numbers; only the axis changes. Applies to the bubble plot and the adjusted regression plot; no effect when results are shown on the log scale or for non-ratio measures.");
        // the log axis exists only for ratio measures that are shown back-transformed (the plots draw the log values otherwise); the box is greyed out for every other measure
        { final Runnable regLogEn = () -> { String sm = (String) smBox.getSelectedItem(); MetaSpec t = new MetaSpec(); t.sm = sm == null || "(none)".equals(sm) ? "" : sm;
            boolean ok = t.isRatioSm() && backChk.isSelected(); regLog.setEnabled(ok);
            regLog.setToolTipText(regLog.getToolTipText() == null ? null : regLog.getToolTipText().replaceAll(" \\(Not available:[^)]*\\)$", "") + (ok ? "" : " (Not available: needs a ratio measure such as OR, RR or HR with back-transformed results.)")); };
          smBox.addActionListener(e -> regLogEn.run()); backChk.addActionListener(e -> regLogEn.run()); regLogEn.run(); }
        { String tip = "Places the study labels with the label-placement algorithm of the ggplot2-style Baujat plot: boxed labels that repel each other and the study symbols, with a leader line when a label has to move away from its point. Applies when study labels are shown";
          for (JCheckBox x : new JCheckBox[]{regRepelChk, funnelRepelChk, doiRepelChk, petRepelChk, radialRepelChk, labbeRepelChk, biasRepelChk}) x.setToolTipText(tip);
          funnelRepelChk.setToolTipText(tip + ". Applies to all funnel plots (funnel, funnel of precision, trim-and-fill) and all funnel plot themes");
          regRepelChk.setToolTipText(tip + ". Applies to the bubble plot (both styles) and to the regression plot with categorical moderator adjustment; the labels are those of the \"Study labels\" box of the publication-bias section");
          radialRepelChk.setToolTipText(tip + ". Applies to every radial plot theme"); labbeRepelChk.setToolTipText(tip + ". Applies to both L\u2019Abb\u00E9 plot themes");
          biasCiChk.setToolTipText("95% confidence interval of the regression line: the uncertainty in the expected standardised effect at each value of the inverse SE (light grey area, dashed lines)");
          biasPiChk.setToolTipText("95% prediction interval: the range in which a new study\u2019s standardised effect might fall at a given inverse SE, under the fitted regression model (lighter grey area, dotted lines)");
          java.awt.event.ItemListener repEn = e -> { boolean l = labChk.isSelected(); for (JCheckBox x : new JCheckBox[]{regRepelChk, funnelRepelChk, doiRepelChk, petRepelChk, biasRepelChk}) x.setEnabled(l); radialRepelChk.setEnabled(radialLabChk.isSelected()); labbeRepelChk.setEnabled(labbeLabChk.isSelected()); };
          labChk.addItemListener(repEn); radialLabChk.addItemListener(repEn); labbeLabChk.addItemListener(repEn); repEn.itemStateChanged(null); }
        regPI.setToolTipText("Prediction bounds of the regression line (dotted lines) with the area between them filled in an even lighter colour than the confidence area: where the true effect of a new study with these moderator values is expected to lie");
        regSymbolBox.setToolTipText("Symbol drawn for each study in the bubble plot and the regression plot with categorical moderator adjustment");
        regSymSize.setToolTipText("Multiplier of the study-symbol size (1.0 = default); symbols stay proportional to the study weights");
        baujatSymSize.setToolTipText("Multiplier of the Baujat study-symbol size (1.0 = default). Applies to both Baujat plot themes; in the ggplot2 style the symbols stay proportional to the study weights");
        baujatQuadChk.setToolTipText("Divides the Baujat plot into four quadrants with two red lines (x: contribution to heterogeneity, y: influence on the overall result) and shades the upper-right quadrant (high influence and high contribution to heterogeneity) in the colour of the lines at partial transparency. Applies to both Baujat plot themes. Baujat et al. (2002) define no cut-off: the plot is read visually, so the lines are positioned with the rule chosen below");
        baujatQuadBox.setToolTipText("Position of the quadrant lines. Median / mean split: data-driven central dividers. 75th, 85th, 90th, 95th, 99th percentile: studies above both lines are flagged as outliers. Tukey fence: Q3 + 1.5 IQR (boxplot outlier rule). Mean + 2 SD, Mean + 3 SD. Median + 2.5 MAD: robust rule of Leys et al. (2013). Theoretical: x = 3.84 (chi-square, 1 df, 95%; squared residual of 1.96), y = 4/k (squared DFBETAS cut-off 2/sqrt(k) of Belsley, Kuh and Welsch; k = number of studies)");
        baujatQuadChk.addItemListener(e -> { baujatQuadBox.setEnabled(baujatQuadChk.isSelected()); baujatQuadColBox.setEnabled(baujatQuadChk.isSelected()); }); baujatQuadBox.setEnabled(baujatQuadChk.isSelected()); baujatQuadColBox.setEnabled(baujatQuadChk.isSelected());
        baujatQuadColBox.setToolTipText("Colour of the Baujat quadrants: the two quadrant lines, the shaded quadrant (the same colour at a much lower opacity than the lines) and the symbol and label of every study that falls inside the shaded quadrant. Applies to both Baujat plot themes");
        regStyleBox.setToolTipText("Default = meta style; ggplot2 style = look of a ggplot2 plot (minimal theme, coloured circles, line and confidence band). The colours below apply to both styles and to the adjusted regression plot; Theme default keeps the colours of the selected style");
        adjChk.setToolTipText("Regression plot of the model of this section with all selected covariates and factors: x = the first numeric covariate, study symbols coloured by the factor chosen in the list below (one of the selected factors; the first one by default); the line (and confidence band) is the prediction for the reference category of each factor, the other numeric covariates held at their mean. Without a numeric covariate no plot can be drawn");
        rg.row("Bubble plot style", regStyleBox); rg.row("Study symbol", regSymbolBox); rg.row("Size of study symbol", regSymSize); rg.row("Study symbol colour", regColPoints); rg.row("Regression line colour", regColLine); rg.row("Confidence area colour", regColBand); rg.row("Confidence lines colour", regColBandLine); rg.row("Prediction area colour", regColPred); rg.row("Prediction line colour", regColPredLine);
        adjFactorBox.setToolTipText("Factor whose categories colour the studies of the regression plot with categorical moderator adjustment; the list only contains the factors selected in the model"); adjChk.addActionListener(e -> adjFactorBox.setEnabled(adjChk.isSelected() && adjFactorBox.getItemCount() > 0)); adjFactorBox.setEnabled(false);
        permChk.setToolTipText("Permutation test for the moderators (metafor::permutest): the rows of the model matrix are permuted and the model is refitted each time. Applies to the model of this section.");
        permIterSp.setToolTipText("Number of permutations of the random test, the observed arrangement being the first of them, as in metafor (ignored by the exact test). Precision of the p-value is about 1/iterations."); permBudgetSp.setToolTipText("Time budget of the exact permutation test, in seconds (default 600). Before an exact test is run, its running time is estimated from the measured speed of your computer on your model and multiplied by a safety factor (default 1.25, Java option -Dmetajava.exactSafety); if this conservative estimate exceeds this budget the test is not run (and is never replaced by an approximate one): the output gives the number of unique permutations and the estimate (a conservative estimate, not a guarantee: the real run can still take longer, and there is no hard timeout), and you can raise the budget here or switch to a random test with a chosen number of permutations. Not a limit on the number of studies."); permSeedF.setToolTipText("Seed of the R random number generator (set.seed): the same seed gives the same p-values, and the permutations metafor draws with this seed");
        cmpChk.setToolTipText("Fits every subset of the selected covariates / factors on the same studies and compares AIC, AICc, BIC (maximum likelihood), tau\u00B2, I\u00B2 and R\u00B2");
        rg.row(null, adjChk); rg.row("Colour studies by factor", adjFactorBox); rg.row(null, permChk); rg.row("Permutations", permIterSp); rg.row("Random seed (permutations)", permSeedF); rg.row(null, permExactChk); rg.row("Exact test time budget (s)", permBudgetSp); rg.row(null, cmpChk); p.add(rg); p.add(Box.createVerticalStrut(8));
        Ui.Section lo = new Ui.Section("7. Leave-one-out analysis", false); lo.row(null, looChk); lo.row(null, looForest); p.add(lo); p.add(Box.createVerticalStrut(8));
        Ui.Section cu = new Ui.Section("8. Cumulative meta-analysis", false); cu.row(null, cumChk); cu.row(null, cumForest); p.add(cu); p.add(Box.createVerticalStrut(8));
funnelThemeBox.setToolTipText("Look of the funnel plot and of the trim-and-fill funnel plot. Default = meta style; MetaWin style = power-enhanced (sunset) funnel with contour bands, always contour-enhanced; STATA style = Stata look (pale-blue frame, grey pseudo 95% limits or contour bands, framed legend); metafor style = as metafor::funnel (light-grey panel, white pseudo 95% region with dotted limits, black points; contour-enhanced: 90 / 95 / 99% regions around the pooled estimate). Imputed studies of the trim-and-fill plot are orange in the MetaWin and STATA styles and open circles in the metafor style");
        funnelThemeBox.addActionListener(e -> contourChk.setEnabled(!org.metajava.plot.FunnelThemes.METAWIN.equals(funnelThemeBox.getSelectedItem())));   // MetaWin style is always contour-enhanced
        Ui.Section pb = new Ui.Section("9. Publication bias", false); pb.row(null, funnelChk); pb.row(null, funnelPrecChk); pb.row(null, funnelBackChk); pb.row(null, contourChk); pb.row(null, pair(labChk, funnelRepelChk)); pb.row("Funnel plot themes", funnelThemeBox); pb.row(null, biasChk); pb.row("Test method", biasMethod); pb.row(null, biasPlotChk); pb.row(null, biasRepelChk); pb.row(null, pair(biasCiChk, biasPiChk));
        pb.row(null, tfChk); pb.row(null, tfPrecChk); pb.row("Trim-fill side", tfSide); pb.row("Trim-fill estimator", tfEst); pb.row("Trim-fill model", tfModel); pb.row(null, drapChk); pb.row("Drapery target effect-size (power computation only)", drapTargetF); pb.row("Drapery type", drapTypeBox); pb.row("Drapery layout", drapLayoutBox); pb.row("Drapery study colour", drapStudyColBox); pb.row("Drapery study labels", drapLabelsBox); pb.row("Drapery study label angle", drapAngleSp); pb.row(null, drapPredChk); pb.row("Drapery prediction colour", drapPredColBox);
        pb.row("Drapery model", drapModelBox); pb.row("Drapery random-effects colour", drapRandomColBox); pb.row("Drapery common-effect colour", drapCommonColBox); pb.row(null, drapReportChk);
        drapChk.setToolTipText("Drapery plot (meta::drapery): p-value functions (confidence curves) of the individual studies and of the pooled estimate, with the prediction region. Drawn after trim-and-fill. Relative effects (OR, RR, HR, ...) are always back-transformed (log-scaled x-axis)");
        drapLabelsBox.setSelectedItem(org.metajava.plot.Drapery.LAB_ID);   // default as in the meta examples: study numbers
        funnelBackChk.setToolTipText("Show the x-axis of all funnel plots (funnel, funnel of precision, trim-and-fill and trim-and-fill of precision) on the back-transformed scale: odds / risk / hazard ratios instead of their logarithms, proportions instead of logit / log / arcsine / Freeman-Tukey values, correlations instead of Fisher\u2019s z, rates instead of their transformed values. Points, funnel limits and contour bands are unchanged; only the axis labels are back-transformed.");
        tfPrecChk.setToolTipText("Trim-and-fill funnel plot with precision (1/SE) on the vertical axis; the imputed studies are shown as open circles. It uses the trim-and-fill settings below and the options of the funnel plot (study labels, funnel theme, contour-enhanced).");
        drapReportChk.setToolTipText("Adds the section \"Drapery Plot Analysis and Interpretation\" below the plot: a statistical report (model, pooled effect, individual studies, prediction region) and a narrative generated from the fitted model and the plotted curves. It follows the model(s) displayed in the plot and never infers publication bias");
        drapTypeBox.setToolTipText("z-value: scaled curves (test statistic, default); p-value: p-value functions");
        drapTargetF.setToolTipText("Effect size used only for the column \"Power (target effect)\" of the Drapery report (power of each study to detect it against the null effect). Ratios: the ratio (e.g. 0.8); proportions, rates, means: natural scale. Blank = column not computed. Does not change any other result or plot.");
        drapLayoutBox.setToolTipText("Line layout of the individual studies: grayscale = darker with increasing precision (default); equal = identical lines; linewidth = thicker with increasing weight");
        drapStudyColBox.setToolTipText("Colour of the study lines. Default: grey scale for the layout grayscale, dark gray otherwise; a colour chosen here is used for all studies in every layout");
        drapLabelsBox.setToolTipText("Labels at the top of the plot: None, the study number in the data (id) or the study name");
        drapAngleSp.setToolTipText("Rotation of the study labels in degrees (0 to 90). Study names are rotated up on the left of the pooled estimate and down on the right, as in meta");
        drapModelBox.setToolTipText("As main analysis: the model(s) of the analysis (random effects, common effect or both); or choose Random, Common or Both. The prediction region belongs to the random-effects model");
        java.awt.event.ActionListener drapEn = e -> { boolean on = drapChk.isSelected(); for (JComponent x : new JComponent[]{drapTargetF, drapTypeBox, drapLayoutBox, drapStudyColBox, drapLabelsBox, drapPredChk, drapReportChk, drapModelBox, drapRandomColBox, drapCommonColBox}) x.setEnabled(on);
            drapAngleSp.setEnabled(on && !org.metajava.plot.Drapery.LAB_NONE.equals(drapLabelsBox.getSelectedItem())); drapPredColBox.setEnabled(on && drapPredChk.isSelected());
            String dm = (String) drapModelBox.getSelectedItem(); drapRandomColBox.setEnabled(on && !org.metajava.plot.Drapery.MODEL_COMMON.equals(dm)); drapCommonColBox.setEnabled(on && !org.metajava.plot.Drapery.MODEL_RANDOM.equals(dm)); };
        drapChk.addItemListener(e -> drapEn.actionPerformed(null)); drapPredChk.addItemListener(e -> drapEn.actionPerformed(null)); drapLabelsBox.addActionListener(drapEn); drapModelBox.addActionListener(drapEn); drapEn.actionPerformed(null);
        pb.row(null, doiChk); pb.row(null, pair(doiApexChk, doiRepelChk));
        petChk.setToolTipText("Precision-effect test (PET) and precision-effect estimate with standard error (PEESE): regression-based effect corrected for small-study effects (Stanley & Doucouliagos, 2014)");
        selChk.setToolTipText("Vevea-Hedges three-parameter selection model: adjusted effect when non-significant results are less likely to be published (works best with 20-30 or more studies)");
        selDir.setToolTipText("Direction in which significant results are favoured for publication. Auto: the direction of the pooled effect");
        failChk.setToolTipText("Number of unpublished null studies needed to make the combined result non-significant (Rosenthal, Rosenberg)");
        pb.row(null, petChk); pb.row(null, pair(petPlotChk, petRepelChk)); pb.row(null, selChk); pb.row("Selection direction", selDir); pb.row(null, failChk); p.add(pb); p.add(Box.createVerticalStrut(8));
        Ui.Section dg = new Ui.Section("10. Diagnostics", false); dg.row(null, infChk); dg.row(null, baujatChk); dg.row("Baujat model", baujatModelBox); dg.row("Baujat plot theme", baujatThemeBox); dg.row(null, baujatQuadChk); dg.row("Baujat quadrant lines", baujatQuadBox); dg.row("Baujat quadrant colour", baujatQuadColBox); dg.row("Baujat symbol colour", baujatSymbolBox); dg.row("Baujat symbol size", baujatSymSize); dg.row("Baujat background colour", baujatBackBox); dg.row(null, radialChk); dg.row("Radial model", radialModelBox); dg.row("Radial plot theme", radialThemeBox); dg.row(null, pair(radialLabChk, radialRepelChk)); dg.row("Radial symbol colour", radialSymbolBox); dg.row("Radial background colour", radialBackBox); dg.row("Radial study circle colour", radialCircleBox);
        dg.row("Radial regression line colour", radialRegBox); dg.row("Radial no-effect line colour", radialRefBox); dg.row("Radial confidence area colour", radialCiBox); dg.row("Radial confidence line colour", radialCiLineBox);
        dg.row(null, labbeChk); dg.row(null, pair(labbeLabChk, labbeRepelChk)); dg.row("L\u2019Abb\u00E9 plot theme", labbeThemeBox); dg.row("L\u2019Abb\u00E9 symbol colour", labbeSymbolBox); dg.row("L\u2019Abb\u00E9 background colour", labbeBackBox); dg.row("L\u2019Abb\u00E9 plot study circle colour", labbeCircleBox);
        dg.row("L\u2019Abb\u00E9 plot reference line colour", labbeRefBox); dg.row("L\u2019Abb\u00E9 plot estimated-effect line colour", labbeEstBox); dg.row("L\u2019Abb\u00E9 plot confidence area colour", labbeCiBox); dg.row("L\u2019Abb\u00E9 plot prediction area colour", labbePiBox);
        goshChk.setToolTipText("Graphical display of study heterogeneity (Olkin et al., 2012; metafor::gosh): the random-effects model is fitted to all (or many random) subsets of the studies; pooled estimate against I\u00B2");
        goshSubSp.setToolTipText("All subsets are used when there are fewer than this many (at most 18 studies); otherwise this many random subsets"); goshSeedF.setToolTipText("Seed for the random subsets");
        goshClChk.setToolTipText("Gaussian mixture model (ICL) fitted to the GOSH points; reports the clusters, the studies that separate them and colours the plots by cluster. Replaces the highlight of the most influential study");
        goshRegChk.setToolTipText("Fits the meta-regression model (covariates / factors chosen in the Meta-regression section) to subsets of the studies and shows how the regression coefficients and the residual heterogeneity vary; output below the ordinary GOSH analysis. Subsets in which the coefficients cannot be estimated are counted and left out");
        goshSubSp.setToolTipText("All subsets are used when there are at most this many possible subsets; otherwise this many random subsets (as metafor::gosh). Also used for the meta-regression GOSH");
        goshTrimChk.setToolTipText("A few extreme subsets (for example nearly collinear ones) can give coefficients far outside the bulk and flatten the plots. When this is on, an axis is limited to the central 99% of the subsets if that is clearly narrower than the full range; the displayed range and the number of subsets outside it are stated below the plot. No subset is removed from the analysis, the tables or the cluster detection.");
        goshTrimOrdChk.setToolTipText("A few extreme subsets can give estimates far outside the bulk and flatten the plot. When this is on, the estimate axis is limited to the central 99% of the subsets if that is more than 25% narrower than the full range; the displayed range and the number of subsets outside it are stated below the plot. No subset is removed from the analysis or from the tables.");
        java.awt.event.ItemListener goshEn = e -> { boolean on = goshChk.isSelected(); goshClChk.setEnabled(on); goshRegChk.setEnabled(on); goshTrimChk.setEnabled(on); goshTrimOrdChk.setEnabled(on); goshPalBox.setEnabled(on); }; goshChk.addItemListener(goshEn); goshEn.itemStateChanged(null);
        dg.row(null, goshChk); dg.row("GOSH subsets", goshSubSp); dg.row("Random seed (GOSH)", goshSeedF); dg.row(null, goshTrimOrdChk); goshPalBox.setToolTipText("Colours of all GOSH plots (ordinary GOSH and GOSH for meta-regression): points, marginal curves, the two groups of the most-influential-study highlight, the clusters and the density map. Default = current palette; Classic = pure blue / red as in the usual GOSH figures; Okabe-Ito and Viridis are colour-blind safe; NPG follows the Nature Publishing Group style; Greyscale is for black-and-white print");
        goshPalBox.setPreferredSize(new Dimension(104, goshPalBox.getPreferredSize().height)); JLabel palLbl = Ui.muted("Colour palette"); palLbl.setBorder(new EmptyBorder(0, 0, 0, 4));
        JPanel palRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); palRow.setOpaque(false); palRow.add(palLbl); palRow.add(goshPalBox);
        JPanel hlRow = new JPanel(new BorderLayout(10, 0)); hlRow.setOpaque(false); hlRow.add(goshHlChk, BorderLayout.WEST); hlRow.add(palRow, BorderLayout.EAST);
        dg.row(null, hlRow); dg.row(null, goshClChk); dg.row(null, goshRegChk); dg.row(null, goshTrimChk); p.add(dg); p.add(Box.createVerticalStrut(8));
        Ui.Section ou = new Ui.Section("11. Export", false); JButton sa = Ui.button("Save all plots\u2026", false), st = Ui.button("Save results (.txt)\u2026", false), sx = Ui.button("Save tables (.xlsx)\u2026", false), sw = Ui.button("Save full report (.docx)\u2026", false); sa.addActionListener(e -> savePlots()); st.addActionListener(e -> saveText()); sx.addActionListener(e -> saveExcel()); sw.addActionListener(e -> saveWord());
        sx.setToolTipText("Excel workbook with the results tables: one sheet per analysis section"); sw.setToolTipText("Word document with the whole output (paragraphs, tables and figures) in the order of the results panel");
        dpiSp.setToolTipText("Resolution of PNG, TIFF and JPG files. SVG and PDF are resolution independent and ignore it."); fmtBox.addActionListener(e -> dpiSp.setEnabled(RasterWriter.isRaster((String) fmtBox.getSelectedItem()))); dpiSp.setEnabled(RasterWriter.isRaster((String) fmtBox.getSelectedItem()));
        ou.row("Plot format", fmtBox); ou.row("Plot resolution (dpi)", dpiSp); ou.row(null, sa); ou.row(null, st); ou.row(null, sx); ou.row(null, sw); p.add(ou);
        p.add(Box.createVerticalGlue());
        modelBox.setSelectedIndex(0); return p;
    }

    /** Two drop-down lists side by side, each with its label above it (compact layout of the forest-plot overrides). */
    static JPanel pairRow(String l1, JComboBox<String> c1, String l2, JComboBox<String> c2) {
        JPanel p = new JPanel(new GridLayout(1, 2, 12, 0)); p.setOpaque(false);
        p.add(cell(l1, c1)); p.add(cell(l2, c2));
        return p;
    }

    private static JPanel cell(String label, JComboBox<String> c) { JPanel cell = new JPanel(new BorderLayout(0, 3)); cell.setOpaque(false); cell.add(Ui.label(label), BorderLayout.NORTH); cell.add(c, BorderLayout.CENTER); return cell; }

    boolean loading = false;
    Map<String, Object> defaultSettings;   // start-up state of every plain setting, restored by the Reset button

    /** Reset button: every check box, option, text field and number goes back to its start-up default; data, analysis type and column assignments are kept. */
    void resetSettings() {
        if (defaultSettings == null) return;
        suppress = true; autoTimer.stop();
        try {
            refChoice.clear();
            // the boxes that depend on the analysis type (effect measure, pooling method, GLMM model) and the covariate / factor lists are rebuilt by the type; the column assignments are put back afterwards
            Map<String, Object> keep = new LinkedHashMap<>(); for (Map.Entry<String, JComboBox<String>> e : maps.entrySet()) keep.put(e.getKey(), e.getValue().getSelectedItem());
            Object sl = studlabBox.getSelectedItem(), sg = subgroupBox.getSelectedItem(), od = orderBox.getSelectedItem();
            typeChanged(); suppress = true;
            for (Map.Entry<String, Object> e : keep.entrySet()) { JComboBox<String> c = maps.get(e.getKey()); if (c != null && e.getValue() != null) c.setSelectedItem(e.getValue()); }
            studlabBox.setSelectedItem(sl); subgroupBox.setSelectedItem(sg); orderBox.setSelectedItem(od);
            ProjectIO.restore(this, defaultSettings);
            updateRefPanel();
            covList.setSelectedIndex(0); facList.setSelectedIndex(0);
        } catch (Exception ex) { error(ex); }
        finally { suppress = false; loading = false; }
        status.setText("Settings reset to defaults"); schedule();
    }
    void schedule() { if (!suppress && table != null) autoTimer.restart(); }
    void hook(Component c) {
        if (c == dpiSp || c == fmtBox || (c instanceof JComponent && c.getParent() == dpiSp)) return;
        if (c instanceof JCheckBox) ((JCheckBox) c).addActionListener(e -> schedule());
        else if (c instanceof JComboBox) ((JComboBox<?>) c).addActionListener(e -> schedule());
        else if (c instanceof JTextField && !(c.getParent() instanceof JSpinner.DefaultEditor)) ((JTextField) c).getDocument().addDocumentListener(new javax.swing.event.DocumentListener() { public void insertUpdate(javax.swing.event.DocumentEvent e) { schedule(); } public void removeUpdate(javax.swing.event.DocumentEvent e) { schedule(); } public void changedUpdate(javax.swing.event.DocumentEvent e) { schedule(); } });
        else if (c instanceof JSpinner) ((JSpinner) c).addChangeListener(e -> schedule());
        else if (c instanceof JList) ((JList<?>) c).addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) schedule(); });
        if (c instanceof Container) for (Component ch : ((Container) c).getComponents()) hook(ch);
    }
    void typeChanged() {
        suppress = true;
        String type = TYPES[typeBox.getSelectedIndex()][0];
        mapPanel.removeAll(); maps.clear(); GridBagConstraints g = new GridBagConstraints(); g.insets = new Insets(2, 0, 2, 8); g.anchor = GridBagConstraints.WEST; int r = 0;
        for (String[] pr : PARAMS.get(type)) { JComboBox<String> c = Ui.combo(); maps.put(pr[0], c); g.gridy = r++; g.gridx = 0; g.weightx = 0; mapPanel.add(Ui.label(pr[1]), g); g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL; mapPanel.add(c, g); }
        smBox.removeAllItems(); for (String s : MetaSpec.smOptions(type)) smBox.addItem(s.isEmpty() ? "(none)" : s); smBox.setSelectedItem(type.equals("gen") ? "HR" : MetaSpec.defaultSm(type));
        methodBox.removeAllItems(); for (String s : MetaSpec.methodOptions(type)) methodBox.addItem(s);
        glmmModelBox.removeAllItems(); for (String s : MetaSpec.glmmModels(type)) glmmModelBox.addItem(s); glmmModelBox.setEnabled(false); nagqBox.setEnabled(false);
        genMode.setEnabled(type.equals("gen"));
        { boolean single = type.equals("mean") || type.equals("prop") || type.equals("rate"); targetF.setEnabled(single); nullF.setEnabled(!single); nullF.setToolTipText(single ? "Single proportions, means and rates: use 'Target effect-size' (section 2)." : null); }
        for (java.awt.event.ActionListener l : methodBox.getActionListeners()) methodBox.removeActionListener(l);
        methodBox.addActionListener(e -> methodChanged(type));
        refreshColumns(); hook(mapPanel); mapPanel.revalidate(); mapPanel.repaint(); suppress = false; schedule();
    }

    /** GLMM needs a specific effect measure, ML for tau^2 and a z/t Wald test (as meta requires); select these automatically. */
    void methodChanged(String type) {
        boolean glmm = "GLMM".equals(methodBox.getSelectedItem()), lrp = "LRP".equals(methodBox.getSelectedItem());
        glmmModelBox.setEnabled(glmm && glmmModelBox.getItemCount() > 1); nagqBox.setEnabled(glmm);
        if (lrp) { boolean o = suppress; suppress = true; smBox.setSelectedItem("OR"); String pm = (String) predBox.getSelectedItem(); if (!"V".equals(pm) && !"HTS".equals(pm) && !"S".equals(pm)) predBox.setSelectedItem("V"); if ("KR".equals(randCiBox.getSelectedItem())) randCiBox.setSelectedItem("classic"); suppress = o; schedule(); return; }
        if (!glmm) { schedule(); return; }   // Inverse / MH / Peto / SSW: re-run automatically like every other option
        boolean old = suppress; suppress = true;
        String sm = MetaSpec.glmmSm(type); if (sm != null) smBox.setSelectedItem(sm);
        tauBox.setSelectedItem("ML"); if ("KR".equals(randCiBox.getSelectedItem())) randCiBox.setSelectedItem("classic");
        adhocBox.setSelectedItem("none"); String pm = (String) predBox.getSelectedItem(); if (!"V".equals(pm) && !"HTS".equals(pm) && !"S".equals(pm)) predBox.setSelectedItem("V");
        suppress = old; schedule();
    }

    void refreshColumns() {
        boolean old = suppress; suppress = true;
        List<String> cols = table == null ? new ArrayList<>() : new ArrayList<>(table.names);
        List<String> opt = new ArrayList<>(); opt.add("(none)"); opt.addAll(cols);
        for (Map.Entry<String, JComboBox<String>> e : maps.entrySet()) { fill(e.getValue(), opt); e.getValue().setSelectedItem(guess(e.getKey(), cols)); }
        fill(studlabBox, opt); fill(subgroupBox, opt); fill(orderBox, opt);
        studlabBox.setSelectedItem(guessAny(cols, "study", "studlab", "label", "author", "id")); subgroupBox.setSelectedItem(guessAny(cols, "subgroup", "ethnic", "group", "region"));
        DefaultListModel<String> m1 = new DefaultListModel<>(), m2 = new DefaultListModel<>(); m1.addElement(NONE); m2.addElement(NONE);
        if (table != null) for (int j = 0; j < table.names.size(); j++) { (table.isNumeric(j) ? m1 : m2).addElement(table.names.get(j)); if (table.isNumeric(j)) m2.addElement(table.names.get(j)); }
        covList.setModel(m1); facList.setModel(m2); covList.setSelectedIndex(0); facList.setSelectedIndex(0); suppress = old; updateRefPanel(); schedule();
    }

    static final String NONE = "none";
    /** Selected entries of a covariate/factor list, ignoring the "none" entry. */

    /** Small settings panel below the factor list: choose the reference category of each categorical factor. */
    JPanel buildRefPanel() {
        refPanel = new JPanel(new GridBagLayout()); refPanel.setBackground(new Color(0xF8F9FC)); refPanel.setBorder(BorderFactory.createCompoundBorder(new javax.swing.border.LineBorder(Ui.BORDER, 1, true), new javax.swing.border.EmptyBorder(8, 10, 8, 10)));
        JLabel t = new JLabel("Categorical factor settings"); t.setFont(Ui.BOLD.deriveFont(12f)); t.setForeground(Ui.TEXT);
        refNote = Ui.muted("The selected category is used as the reference for this factor.");
        GridBagConstraints g = new GridBagConstraints(); g.gridx = 0; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL; g.anchor = GridBagConstraints.WEST; g.insets = new Insets(2, 0, 2, 0);
        g.gridy = 0; refPanel.add(t, g); g.gridy = 1; refPanel.add(Ui.muted("Configure factor"), g); g.gridy = 2; refPanel.add(cfgFactorBox, g);
        g.gridy = 3; refPanel.add(Ui.muted("Reference category"), g); g.gridy = 4; refPanel.add(refBox, g); g.gridy = 5; refPanel.add(refNote, g);
        cfgFactorBox.addActionListener(e -> { if (!suppress) fillRefBox(); });
        refBox.addActionListener(e -> { if (suppress || refFilling) return; String f = (String) cfgFactorBox.getSelectedItem(), l = (String) refBox.getSelectedItem(); if (f != null && l != null) refChoice.put(f, l); });
        facList.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) updateRefPanel(); });
        updateRefPanel(); return refPanel;
    }
    boolean refFilling = false;

    /** Levels (sorted) of a factor column in the loaded data. */
    List<String> levelsOf(String f) {
        TreeSet<String> lv = new TreeSet<>();
        if (table != null && f != null) try { for (String x : table.strings(f)) if (x != null && !x.isBlank()) lv.add(x.trim()); } catch (RuntimeException ex) { /* column vanished */ }
        return new ArrayList<>(lv);
    }

    /** Enables / disables the panel and keeps the factor dropdown in line with the selected factors. */
    void updateRefPanel() {
        if (refPanel == null) return;
        List<String> facs = chosen(facList); boolean on = !facs.isEmpty();
        boolean old = suppress; suppress = true;
        try {
            Object cur = cfgFactorBox.getSelectedItem(); cfgFactorBox.removeAllItems(); for (String f : facs) cfgFactorBox.addItem(f);
            if (cur != null && facs.contains(cur)) cfgFactorBox.setSelectedItem(cur);
            Object curAdj = adjFactorBox.getSelectedItem(); adjFactorBox.removeAllItems(); for (String f : facs) adjFactorBox.addItem(f);   // only the factors selected in the model
            if (curAdj != null && facs.contains(curAdj)) adjFactorBox.setSelectedItem(curAdj);
            fillRefBox();
        } finally { suppress = old; }
        for (Component c : refPanel.getComponents()) c.setEnabled(on); cfgFactorBox.setEnabled(on); refBox.setEnabled(on); adjFactorBox.setEnabled(on && adjChk.isSelected());
        refNote.setText(on ? (regIntercept.isSelected() ? "The selected category is used as the reference for this factor." : "Without an intercept every category gets its own coefficient (no reference).") : "Select a categorical factor above to choose its reference category.");
    }

    void fillRefBox() {
        refFilling = true;
        try {
            refBox.removeAllItems(); String f = (String) cfgFactorBox.getSelectedItem(); if (f == null) return;
            List<String> lv = levelsOf(f); for (String l : lv) refBox.addItem(l);
            String keep = refChoice.get(f); if (keep == null || !lv.contains(keep)) { keep = lv.isEmpty() ? null : lv.get(0); if (keep != null) refChoice.put(f, keep); else refChoice.remove(f); }
            if (keep != null) refBox.setSelectedItem(keep);
        } finally { refFilling = false; }
    }

    /** Reference categories of the selected factors (a level that no longer exists in the data is dropped). */
    Map<String, String> chosenRefs(List<String> facs) {
        Map<String, String> m = new HashMap<>(); for (String f : facs) { String l = refChoice.get(f); if (l != null && levelsOf(f).contains(l)) m.put(f, l); } return m;
    }

    static List<String> chosen(JList<String> l) { List<String> o = new ArrayList<>(l.getSelectedValuesList()); o.remove(NONE); return o; }
    /** "none" is exclusive: choosing it clears the other entries, choosing another entry clears "none". */
    void noneBehaviour(JList<String> l) {
        l.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || fixingList) return; fixingList = true;
            try { int[] s = l.getSelectedIndices(); boolean hasNone = s.length > 0 && s[0] == 0;
                if (hasNone && s.length > 1) { if (l.getLeadSelectionIndex() == 0) l.setSelectedIndex(0); else { l.removeSelectionInterval(0, 0); } }
                else if (s.length == 0 && l.getModel().getSize() > 0) l.setSelectedIndex(0); }
            finally { fixingList = false; }
        });
    }
    boolean fixingList = false;

    static void fill(JComboBox<String> c, List<String> items) { c.removeAllItems(); for (String s : items) c.addItem(s); }
    static String norm(String s) { return s.toLowerCase().replaceAll("[^a-z0-9]", ""); }
    String guessAny(List<String> cols, String... keys) { for (String k : keys) for (String c : cols) if (norm(c).contains(k)) return c; return "(none)"; }
    static String guess(String key, List<String> cols) {
        String[][] alias = {
            {"event.e", "eventse", "eventsexp", "eventstreat", "eventstreatment", "eventsintervention", "eventsexperimental", "events1", "evente", "ae"}, {"n.e", "ne", "ntreat", "ntreatment", "nexp", "nexperimental", "ntotal1", "totalexp", "totaltreatment", "n1", "totalexperimental"},
            {"event.c", "eventsc", "eventscontrol", "eventscontrols", "eventsctrl", "events2", "eventc", "ce"}, {"n.c", "nc", "ncontrol", "ncontrols", "nctrl", "ntotal2", "totalcontrol", "n2", "totalcontrols"},
            {"mean.e", "meane", "meanexp", "meantreatment", "mean1", "meanexperimental"}, {"sd.e", "sde", "sdexp", "sdtreatment", "sd1", "sdexperimental"}, {"mean.c", "meanc", "meancontrol", "meancontrols", "mean2"}, {"sd.c", "sdc", "sdcontrol", "sdcontrols", "sd2"},
            {"TE", "loghr", "logor", "logrr", "te", "effect", "logeffect", "estimate"}, {"seTE", "stderror", "se", "sete", "standarderror"}, {"lower", "lower", "lci", "cilower", "lowerci"}, {"upper", "upper", "uci", "ciupper", "upperci"},
            {"cor", "cor", "r", "correlation"}, {"n", "n", "total", "samplesize", "ntotal"}, {"event", "event", "events", "cases"}, {"time", "time", "persontime", "personyears", "py", "t"}, {"mean", "mean", "average"}, {"sd", "sd", "stdev"},
            {"time.e", "timee", "timeexp", "persontimeexp", "pyexp", "time1"}, {"time.c", "timec", "timecontrol", "persontimecontrol", "pycontrol", "time2"}};
        for (String[] a : alias) if (a[0].equals(key)) for (int i = 1; i < a.length; i++) for (String c : cols) if (norm(c).equals(a[i])) return c;
        for (String c : cols) if (norm(c).equals(norm(key))) return c;
        return "(none)";
    }

    void showWelcome() {
        results.removeAll(); JLabel l = new JLabel("<html><div style='width:560px'><h2 style='color:#1F4E8C'>Welcome to MetaJava</h2><p>1. Open an Excel (.xlsx) or CSV file, or click <b>Example data</b>.<br>2. Choose the analysis type and assign the columns of your file to each parameter.<br>3. Adjust the settings on the left, then click <b>Run analysis</b>.</p><p style='color:#667085'>Methods follow the R package <i>meta</i>; outlier and influence diagnostics follow <i>metafor</i> (Viechtbauer &amp; Cheung, 2010).</p></div></html>");
        results.add(l); results.revalidate(); results.repaint();
    }

    void openFile() { JFileChooser fc = new JFileChooser(); fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Excel / CSV", "xlsx", "csv", "tsv", "txt")); if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) { file = fc.getSelectedFile(); try { loading = true; sheets = TableReader.sheetNames(file); sheetBox.removeAllItems(); for (String s : sheets) sheetBox.addItem(s); loading = false; load(file, 0); } catch (Exception ex) { loading = false; error(ex); } } }
    /** "Example data": a menu under the button lists the bundled example data sets; the chosen one is loaded and the matching analysis type selected. */
    void showExampleMenu(Component anchor) {
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        for (Examples.Item it : Examples.ALL) { javax.swing.JMenuItem mi = new javax.swing.JMenuItem(it.title + "  \u2013  " + it.file); mi.addActionListener(e -> loadExample(it)); menu.add(mi); }
        menu.show(anchor, 0, anchor.getHeight());
    }
    void loadExample(Examples.Item it) {
        try (InputStream in = getClass().getResourceAsStream("/examples/" + it.file)) {
            if (in == null) throw new IOException("Example file not found: " + it.file);
            String base = it.file.substring(0, it.file.lastIndexOf('.')); File t = File.createTempFile(base.length() < 3 ? base + "___" : base, ".xlsx"); t.deleteOnExit(); java.nio.file.Files.copy(in, t.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); file = t;
            loading = true; sheets = TableReader.sheetNames(t); sheetBox.removeAllItems(); for (String s : sheets) sheetBox.addItem(s); loading = false; load(t, 0);
            int ti = 0; for (int i = 0; i < TYPES.length; i++) if (TYPES[i][0].equals(it.type)) ti = i; typeBox.setSelectedIndex(ti);
            fileLabel.setText("Example: " + it.file + " \u2013 " + table.nrow + " rows, " + table.names.size() + " columns"); status.setText("Loaded example " + it.file);
        } catch (Exception ex) { loading = false; error(ex); }
    }
    void load(File f, int sheet) {
        try { table = TableReader.read(f, sheet); fileLabel.setText(f.getName() + " \u2013 " + table.nrow + " rows, " + table.names.size() + " columns");
            showTable(table); refreshColumns(); status.setText("Loaded " + f.getName());
        } catch (Exception ex) { error(ex); }
    }
    /** Fills the data preview (first 200 rows). */
    void showTable(DataTable t) {
        DefaultTableModel m = new DefaultTableModel(t.names.toArray(), 0); for (int i = 0; i < Math.min(t.nrow, 200); i++) { Object[] row = new Object[t.names.size()]; for (int j = 0; j < row.length; j++) row[j] = i < t.cols.get(j).length ? t.cols.get(j)[i] : null; m.addRow(row); }
        preview.setModel(m); preview.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    }
    void error(Exception ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, String.valueOf(ex.getMessage() == null ? ex : ex.getMessage()), "Error", JOptionPane.ERROR_MESSAGE); status.setText("Error: " + ex.getMessage()); }

    String sel(JComboBox<String> c) { Object o = c.getSelectedItem(); return o == null || o.equals("(none)") ? null : o.toString(); }
    long lng(JTextField f, long def) { try { return Long.parseLong(f.getText().trim()); } catch (Exception e) { return def; } }
    double dbl(JTextField f, double def) { try { String s = f.getText().trim(); return s.isEmpty() ? def : Double.parseDouble(s.replace(',', '.')); } catch (Exception e) { return def; } }

    static String noneToEmpty(String x) { return x == null || x.equals("none") ? "" : x; }

    MetaSpec buildSpec() {
        MetaSpec s = new MetaSpec(); s.type = TYPES[typeBox.getSelectedIndex()][0]; String sm = (String) smBox.getSelectedItem(); s.sm = "(none)".equals(sm) ? "" : sm;
        s.method = (String) methodBox.getSelectedItem(); if (glmmModelBox.getSelectedItem() != null) s.modelGlmm = (String) glmmModelBox.getSelectedItem(); s.nAGQ = Integer.parseInt((String) nagqBox.getSelectedItem()); int mi = modelBox.getSelectedIndex(); s.random = mi != 1; s.common = mi != 0; s.methodTau = (String) tauBox.getSelectedItem(); s.genqWeights = (String) genqWBox.getSelectedItem(); s.genqCustom = genqCustomF.getText();
        String tc = (String) tauCiBox.getSelectedItem(); s.methodTauCi = tc.equals("Default") ? null : tc.equals("None") ? "" : tc; s.methodRandomCi = (String) randCiBox.getSelectedItem(); s.adhocHakn = noneToEmpty((String) adhocBox.getSelectedItem());
        s.methodPredict = (String) predBox.getSelectedItem(); s.adhocHaknPi = noneToEmpty((String) adhocPiBox.getSelectedItem()); s.prediction = predChk.isSelected(); s.methodI2 = (String) i2Box.getSelectedItem(); s.methodCommonCi = (String) commonCiBox.getSelectedItem();
        double lv = dbl(levelF, 0.95); s.level = s.levelMa = s.levelHetstat = lv; double pl = dbl(predLevelF, 95) / 100; s.levelPredict = pl > 0.5 && pl < 1 ? pl : 0.95; s.backtransf = backChk.isSelected(); s.incr = dbl(incrF, 0.5); s.methodIncr = ((String) incrBox.getSelectedItem()).replace(" ", "");
        s.mhExact = mhExact.isSelected(); s.allstudies = allStudies.isSelected(); s.methodSmd = (String) smdBox.getSelectedItem(); s.pooledvar = pooledvar.isSelected(); String ci = (String) ciBox.getSelectedItem(); s.methodCi = ci.equals("Default") ? null : ci;
        { boolean single = s.type.equals("mean") || s.type.equals("prop") || s.type.equals("rate"); JTextField nf = single ? targetF : nullF;
            s.nullEffect = nf.getText().isBlank() ? Double.NaN : dbl(nf, Double.NaN);
            if (single && !nf.getText().isBlank()) { s.pscale = dbl(pscaleF, 1); if (!Double.isFinite(s.nullEffect)) throw new IllegalArgumentException("Target effect-size: '" + nf.getText().trim() + "' is not a number.");
                if (s.backtransf && Double.isNaN(s.nullValue())) throw new IllegalArgumentException("Target effect-size " + nf.getText().trim() + " cannot be used with the effect measure " + (s.sm.isEmpty() ? "(none)" : s.sm) + " (proportions must lie between 0 and the scaling factor, rates and means analysed on the log scale must be positive)."); } } s.pscale = dbl(pscaleF, 1); s.tauCommon = tauCommon.isSelected(); s.title = titleF.getText(); s.predictionSubgroup = sgPred.isSelected();
        String sg = sel(subgroupBox); s.subgroupName = sg == null ? "" : sg;
        return s;
    }

    MetaData buildData(MetaSpec s, boolean withSubgroup) {
        MetaData d = new MetaData();
        for (Map.Entry<String, JComboBox<String>> e : maps.entrySet()) { String c = sel(e.getValue()); if (c != null) d.put(e.getKey(), table.num(c)); }
        String sl = sel(studlabBox); d.n = table.nrow; d.studlab = sl != null ? table.strings(sl) : null;
        if (d.studlab != null) for (int i = 0; i < d.studlab.length; i++) if (d.studlab[i].isEmpty()) d.studlab[i] = "Study " + (i + 1);
        String sg = sel(subgroupBox); if (sg != null && withSubgroup) d.subgroup = table.strings(sg);
        String ob = sel(orderBox); if (ob != null) d.ordering = table.num(ob);
        if (s.type.equals("gen")) {
            if (genMode.getSelectedIndex() == 1) { if (!d.has("TE") || !d.has("lower") || !d.has("upper")) throw new IllegalArgumentException("Assign effect size (TE), lower and upper CI limits."); Effects.fromRatioCi(d, "TE", "lower", "upper", s.level); }
            else if (!d.has("seTE") && !(d.has("lower") && d.has("upper"))) throw new IllegalArgumentException("Assign the standard error (seTE) or lower/upper CI limits.");
            if (d.has("TE")) d.n = d.get("TE").length;
        } else { for (String[] pr : PARAMS.get(s.type)) if (!d.has(pr[0])) throw new IllegalArgumentException("Please assign a column to: " + pr[1]); }
        return d;
    }

    void run() { runImpl(false); }

    /** Checks the table for the current column assignment: throws {@link Validate.Failed} when the data cannot be analysed, otherwise returns the warnings. */
    List<String> validateData(MetaSpec s) {
        Map<String, String> cols = new LinkedHashMap<>(), names = new HashMap<>();
        for (String[] pr : PARAMS.get(s.type)) { names.put(pr[0], pr[1]); JComboBox<String> cb = maps.get(pr[0]); String c = cb == null ? null : sel(cb); if (c != null) cols.put(pr[0], c); }
        return Validate.check(table, s.type, cols, names, sel(studlabBox), regChk.isSelected() ? chosen(covList) : new ArrayList<>(), s.type.equals("gen") && genMode.getSelectedIndex() == 1, s.sm);
    }

    /** Shows every data problem in the results panel (instead of a pop-up) so the list can be read while the columns are corrected. */
    void showValidation(Validate.Failed ex) {
        ++generation; if (current != null) current.cancel(true); runFinished();
        results.removeAll(); int ne = 0, nw = 0; StringBuilder h = new StringBuilder("<html><div style='width:560px'>");
        for (Validate.Issue i : ex.issues) if (i.error) ne++; else nw++;
        h.append("<h2 style='color:#B42318'>The data need correcting before the analysis can run</h2><p style='color:#667085'>Row numbers count the data rows below the header line.</p>");
        h.append("<p><b>").append(ne).append(ne == 1 ? " error" : " errors").append("</b> (must be fixed):</p><ul>");
        for (Validate.Issue i : ex.issues) if (i.error) h.append("<li style='margin-bottom:6px'>").append(esc(i.text)).append("</li>");
        h.append("</ul>");
        if (nw > 0) { h.append("<p><b>").append(nw).append(nw == 1 ? " warning" : " warnings").append("</b> (worth checking):</p><ul>"); for (Validate.Issue i : ex.issues) if (!i.error) h.append("<li style='margin-bottom:6px;color:#8A5A00'>").append(esc(i.text)).append("</li>"); h.append("</ul>"); }
        h.append("</div></html>");
        JLabel l = new JLabel(h.toString()); l.setBorder(new EmptyBorder(14, 10, 10, 10)); l.setAlignmentX(LEFT_ALIGNMENT); results.add(l);
        status.setText("Data check: " + ne + (ne == 1 ? " error" : " errors") + " found"); results.add(Box.createVerticalGlue()); results.revalidate(); results.repaint();
    }
    static String esc(String x) { return x.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    // ===== analysis pipeline: computed off the UI thread; independent sections run concurrently =====
    static final class Item { final String title, text; final DoubleFunction<BufferedImage> render; BufferedImage img; org.metajava.plot.DraperyReport.Result drap; List<Report.Tbl> tables = new ArrayList<>(); Item(String t, String x, DoubleFunction<BufferedImage> r) { title = t; text = x; render = r; } }
    static final class Out { final List<Item> items = new ArrayList<>(); Exception error; }
    /** Everything the background code needs, captured on the UI thread (Swing components are never read from worker threads). */
    static final class Snap {
        MetaSpec s, sSub; MetaData d, dSub; DataTable tbl; Plots.Opts o; List<String> nums, facs; Map<String, String> refs = new HashMap<>(); String biasMethod, tfSide, tfEst, tfModel, selDir, adjFactor; List<String> dataWarn = new ArrayList<>(); boolean pet, petPlot, sel, fail;
        String goshPal = "Default"; boolean perm, permExact, cmp, orchard, cater, gosh, goshHl, goshCl, goshReg, goshTrim, goshTrimOrd; int permIter, permBudget, goshSub; long permSeed, goshSeed;
        boolean tfPrec, drap, drapReport, doi, doiApex, labbe, sg, forest, sgTestEffect, sgDetails, reg, regIntercept, bubble, regLine, adj, loo, looForest, cum, cumForest, funnel, funnelPrec, contour, labels, bias, biasPlot, tf, inf, baujat, radial;
    }
    private final ThreadLocal<List<Item>> sink = new ThreadLocal<>();
    private volatile int generation = 0; private java.util.concurrent.Future<?> current;
    private final java.util.concurrent.ExecutorService coordinator = java.util.concurrent.Executors.newCachedThreadPool(r -> { Thread t = new Thread(r, "metajava-analysis"); t.setDaemon(true); return t; });
    final JProgressBar busy = new JProgressBar(0, 100);
    private final java.util.concurrent.atomic.AtomicInteger lastPct = new java.util.concurrent.atomic.AtomicInteger();
    /** Any thread: reports the overall progress (0..1) of run `gen` to the top strip and the small bar; never goes backwards within a run, and posts to the UI thread only when the percentage changes. */
    void progressTo(int gen, double frac) {
        final int p = (int) Math.max(0, Math.min(99, Math.floor(frac * 100)));
        if (lastPct.getAndUpdate(o -> Math.max(o, p)) >= p) return;
        SwingUtilities.invokeLater(() -> { if (gen == generation) { busy.setIndeterminate(false); busy.setValue(p); strip.setProgress(p); } });
    }
    /** Any thread: status text after "Running analysis..." (e.g. permutations done); null clears it. */
    void statusTo(int gen, String msg) { SwingUtilities.invokeLater(() -> { if (gen == generation) strip.setDetail(msg); }); }
    /** UI thread: the run is over. */
    void runFinished() { busy.setIndeterminate(false); busy.setVisible(false); strip.stop(); }

    void runImpl(boolean auto) {
        if (table == null) { if (auto) return; JOptionPane.showMessageDialog(this, "Please load a data file first.", "No data", JOptionPane.WARNING_MESSAGE); return; }
        final Snap n = new Snap();
        try {
            n.tbl = table; n.s = buildSpec(); n.d = buildData(n.s, false); n.dataWarn = validateData(n.s); n.o = forestOpts();
            n.forest = forestChk.isSelected(); n.sg = sel(subgroupBox) != null && sgChk.isSelected(); n.sgTestEffect = sgTestEffect.isSelected(); n.sgDetails = sgDetails.isSelected();
            if (n.sg) { n.sSub = n.s.clone(); n.dSub = buildData(n.sSub, true); }
            n.reg = regChk.isSelected(); n.nums = chosen(covList); n.facs = chosen(facList); n.refs = chosenRefs(n.facs); n.regIntercept = regIntercept.isSelected(); n.bubble = bubbleChk.isSelected(); n.regLine = regLine.isSelected(); n.adj = adjChk.isSelected(); n.adjFactor = (String) adjFactorBox.getSelectedItem();
            n.loo = looChk.isSelected(); n.looForest = looForest.isSelected(); n.cum = cumChk.isSelected(); n.cumForest = cumForest.isSelected();
            n.funnel = funnelChk.isSelected(); n.funnelPrec = funnelPrecChk.isSelected(); n.contour = contourChk.isSelected(); n.labels = labChk.isSelected(); n.bias = biasChk.isSelected(); n.biasPlot = biasPlotChk.isSelected(); n.biasMethod = (String) biasMethod.getSelectedItem();
            n.tf = tfChk.isSelected(); n.tfPrec = tfPrecChk.isSelected(); n.tfSide = (String) tfSide.getSelectedItem(); n.tfEst = (String) tfEst.getSelectedItem(); n.tfModel = (String) tfModel.getSelectedItem(); n.inf = infChk.isSelected(); n.baujat = baujatChk.isSelected(); n.radial = radialChk.isSelected(); n.drap = drapChk.isSelected(); n.drapReport = drapReportChk.isSelected(); n.doi = doiChk.isSelected(); n.doiApex = doiApexChk.isSelected(); n.labbe = labbeChk.isSelected();
            n.o.contour = n.funnel && n.contour; n.o.studyLabels = n.labels;
            n.perm = permChk.isSelected(); n.permExact = permExactChk.isSelected(); n.cmp = cmpChk.isSelected(); n.permIter = (Integer) permIterSp.getValue(); n.permBudget = ((Number) permBudgetSp.getValue()).intValue(); n.permSeed = lng(permSeedF, 1);
            n.orchard = orchardChk.isSelected(); n.cater = caterChk.isSelected(); n.gosh = goshChk.isSelected(); n.goshHl = goshHlChk.isSelected(); n.goshPal = (String) goshPalBox.getSelectedItem(); n.goshCl = goshClChk.isSelected(); n.goshReg = goshRegChk.isSelected(); n.goshTrim = goshTrimChk.isSelected(); n.goshTrimOrd = goshTrimOrdChk.isSelected(); n.goshSub = (Integer) goshSubSp.getValue(); n.goshSeed = lng(goshSeedF, 1);
            n.pet = petChk.isSelected(); n.petPlot = petPlotChk.isSelected(); n.sel = selChk.isSelected(); n.selDir = (String) selDir.getSelectedItem(); n.fail = failChk.isSelected();
        } catch (Validate.Failed ex) { showValidation(ex); return;
        } catch (Exception ex) { if (auto) softError(ex); else error(ex); return; }
        final int gen = ++generation; lastPct.set(0); if (current != null) current.cancel(true);
        status.setText("Running\u2026"); busy.setValue(0); busy.setIndeterminate(true); busy.setVisible(true); strip.start();   // the small bar is indeterminate until the first step reports: a long first section (e.g. a GLMM leave-one-out) must not look frozen
        final int scrollPos = resScroll.getVerticalScrollBar().getValue();
        current = coordinator.submit(() -> Par.withCancel(() -> gen != generation, () -> analyse(n, gen, auto, scrollPos)));   // loops inside the analysis stop as soon as it is superseded
    }

    /** Overall progress (0..1 of the part after the main fit) from the finished sections and the partial progress of the running ones. */
    private static double progressOf(int done, java.util.concurrent.atomic.AtomicIntegerArray fracs, int[] wt, int total) {   // done / total: weights of the finished sections / of all sections
        double part = 0; for (int i = 0; i < fracs.length(); i++) part += wt[i] * fracs.get(i) / 1000.0;
        return 0.05 + 0.95 * (done + part) / total;
    }

    private static void addSec(List<Object[]> secs, List<String> names, String name, Object[] sec) { secs.add(sec); names.add(name); }
    /** Weight of a section in the overall bar (relative expected time): an exact permutation test and GOSH dominate, the other sections are short. */
    /** Status line of the running section (null-safe: nothing happens when the section is not connected to the status line). */
    private static void say(String msg) { java.util.function.Consumer<String> st = Par.statusSink(); if (st != null) st.accept(msg); }
    private static int weightOf(String name, Object[] sec, Snap n) {
        if (sec.length > 4) return (Integer) sec[4];
        if (name.startsWith("GOSH")) return n.goshCl ? 6 : 4;
        return (boolean) sec[1] ? 2 : 1;
    }
    /** Share of a GOSH section spent on the subsets themselves (the rest is cluster detection, whose cost hardly depends on the number of subsets: about 3.5 s against 4 microseconds per subset). */
    static double goshRunShare(int subsets) { double a = 4e-6 * Math.max(1, subsets); return Math.min(0.9, Math.max(0.05, a / (a + 3.5))); }

    private void checkCurrent(int gen) { if (gen != generation) throw new java.util.concurrent.CancellationException("superseded"); }

    /** Runs on a background thread. Light sections go to the shared pool at once; heavy ones run in turn here, using the pool for their inner loops. */
    void analyse(Snap n, int gen, boolean auto, int scrollPos) {
        try {
            final MetaSpec s = n.s; statusTo(gen, "Fitting the main model ..."); final MetaResult r = Meta.run(s, n.d); checkCurrent(gen); progressTo(gen, 0.05); statusTo(gen, null);
            final List<Out> outs = new ArrayList<>(); final java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger();
            final List<Object[]> secs = new ArrayList<>(); final List<String> names = new ArrayList<>();   // {body, heavy?, rank, reports progress, weight} and the name of every section
            final Plots.Opts o = n.o;
            addSec(secs, names, "Meta-analysis", new Object[]{(Runnable) () -> { addText("Meta-Analysis", Report.summary(r));
                if (n.forest) addPlot("Meta-Analysis Forest Plot", sc -> { Plots.Opts q = copy(o); q.scale = sc; return Plots.forest(r, q); }); }, false});
            if (n.sg) addSec(secs, names, "Subgroup analysis", new Object[]{(Runnable) () -> { MetaSpec s2 = n.sSub; MetaResult rs = Meta.run(s2, n.dSub);
                addText("Subgroup Analysis: " + s2.subgroupName, "Subgroup Analysis Summary\n\n" + Report.subgroupText(rs) + Report.details(rs));
                if (n.forest) addPlot("Subgroup Analysis Forest Plot: " + s2.subgroupName, sc -> { Plots.Opts q = copy(o); q.scale = sc; q.testEffectSubgroup = n.sgTestEffect; q.analysisDetails = n.sgDetails; return Plots.forest(rs, q); });
                if (n.orchard) addPlot("Orchard Plot: " + s2.subgroupName, sc -> { Plots.Opts q = copy(o); q.scale = sc; return ExtraPlots.orchard(rs, q); });
                if (n.cater) addPlot("Caterpillar Plot: " + s2.subgroupName, sc -> { Plots.Opts q = copy(o); q.scale = sc; return ExtraPlots.caterpillar(rs, q); }); }, true, 4});
            if (n.reg) addSec(secs, names, "Meta-regression", new Object[]{(Runnable) () -> regression(r, n), false, n.perm && n.permExact ? 0 : 9, true, n.perm && n.permExact ? 40 : 1});   // {body, heavy, rank, reports progress, weight of the section in the bar}: an exact permutation test dominates the run
            if (n.loo) addSec(secs, names, "Leave-one-out analysis", new Object[]{(Runnable) () -> { Meta.leaveOneOut(r); addText("Leave-One-Out Analysis", Report.looText(r));
                if (n.looForest) addPlot("Leave-One-Out Analysis Forest Plot", sc -> { Plots.Opts q = copy(o); q.scale = sc; return Plots.forestSeries(r, r.loo, r.looLabels, q, "loo"); }); }, true, 1});
            if (n.cum) addSec(secs, names, "Cumulative meta-analysis", new Object[]{(Runnable) () -> { Meta.cumulative(r, null); addText("Cumulative Meta-Analysis", Report.cumText(r));
                if (n.cumForest) addPlot("Cumulative Meta-Analysis Forest Plot", sc -> { Plots.Opts q = copy(o); q.scale = sc; return Plots.forestSeries(r, r.cum, r.cumLabels, q, "cum"); }); }, true, 2});
            if (n.funnel) addSec(secs, names, "Funnel plot", new Object[]{(Runnable) () -> addPlot("Funnel Plot", sc -> { Plots.Opts q = copy(o); q.scale = sc; q.contour = n.contour; q.studyLabels = n.labels; return Plots.funnel(r, q, null); }), false});
            if (n.funnelPrec) addSec(secs, names, "Funnel plot of precision", new Object[]{(Runnable) () -> addPlot("Funnel Plot of Precision", sc -> { Plots.Opts q = copy(o); q.scale = sc; q.contour = n.contour; q.studyLabels = n.labels; q.precision = true; return Plots.funnel(r, q, null); }), false});
            if (n.bias) addSec(secs, names, "Funnel plot asymmetry test", new Object[]{(Runnable) () -> { Bias.Result b = Bias.run(r, n.biasMethod.equals("Default") ? null : n.biasMethod, false); addText("Test for Funnel Plot Asymmetry", Report.biasText(b, r));
                if (n.biasPlot && b.error == null && b.plotX != null) addPlot("Plot (Test for Funnel Plot Asymmetry)", sc -> { Plots.Opts q = copy(o); q.scale = sc; q.studyLabels = n.labels; return Plots.biasPlot(b, r, q); }); }, false});
            if (n.tf || n.tfPrec) addSec(secs, names, "Trim and fill", new Object[]{(Runnable) () -> { TrimFill tf = TrimFill.run(r, n.tfSide.equals("Auto") ? null : n.tfSide.equals("Left"), n.tfModel.startsWith("Common") || !s.random, n.tfEst, s.common, s.random);
                if (tf == null) addText("Trim and Fill", "Too few studies for trim-and-fill."); else { addText("Trim and Fill", Report.trimfillText(tf, r));
                    if (n.tf) addPlot("Trim-and-Fill Funnel Plot", sc -> { Plots.Opts q = copy(o); q.scale = sc; return Plots.funnel(r, q, tf); });
                    if (n.tfPrec) addPlot("Trim-and-Fill Funnel Plot of Precision", sc -> { Plots.Opts q = copy(o); q.scale = sc; q.precision = true; return Plots.funnel(r, q, tf); }); } }, false});
            if (n.drap) addSec(secs, names, "Drapery plot", new Object[]{(Runnable) () -> { addPlot("Drapery Plot", sc -> { Plots.Opts q = copy(o); q.scale = sc; return org.metajava.plot.Drapery.plot(r, q); });
                if (n.drapReport) { org.metajava.plot.DraperyReport.Result dr = org.metajava.plot.DraperyReport.build(r, copy(o)); if (dr.available) addDrapery(dr); } }, false, 6});
            if (n.doi) addSec(secs, names, "Doi plot", new Object[]{(Runnable) () -> { Lfk l = Lfk.compute(r.TE, r.seTE, r.studlab); addText("Doi Plot and LFK Index", Report.lfkText(l, r));
                if (l.lfk == l.lfk) addPlot("Doi Plot", sc -> { Plots.Opts q = copy(o); q.scale = sc; q.studyLabels = n.labels; q.apexLine = n.doiApex; return Plots.doi(l, r, q); }); }, false});
            if (n.pet) addSec(secs, names, "PET-PEESE", new Object[]{(Runnable) () -> { PubBias.PetPeese pp = PubBias.petPeese(r); addText("PET-PEESE", BiasReport.petPeeseText(pp, r));
                if (n.petPlot && pp.error == null) addPlot("PET-PEESE Plot", sc -> { Plots.Opts q = copy(o); q.scale = sc; q.studyLabels = n.labels; return Plots.petPeese(pp, r, q); }); }, false});
            if (n.sel) addSec(secs, names, "Selection model", new Object[]{(Runnable) () -> addText("Selection Model", BiasReport.selectionText(PubBias.selection(r, n.selDir), r)), false, 5});
            if (n.fail) addSec(secs, names, "Fail-safe N", new Object[]{(Runnable) () -> addText("Fail-Safe N", BiasReport.failSafeText(PubBias.failSafe(r), r)), false});
            if (n.inf) addSec(secs, names, "Influence diagnostics", new Object[]{(Runnable) () -> influence(r, s, n), true, 3});
            if (n.baujat) addSec(secs, names, "Baujat plot", new Object[]{(Runnable) () -> addPlot("Baujat Plot", sc -> { Plots.Opts q = copy(o); q.scale = sc; return Plots.baujat(r, q); }), false});
            if (n.radial) addSec(secs, names, "Radial plot", new Object[]{(Runnable) () -> addPlot("Radial Plot", sc -> { Plots.Opts q = copy(o); q.scale = sc; q.studyLabels = q.radialLabels; return Plots.radial(r, q); }), false});
            if (n.labbe) addSec(secs, names, "L\u2019Abb\u00E9 plot", new Object[]{(Runnable) () -> { if (!s.type.equals("bin")) { addText("L\u2019Abb\u00E9 Plot", "The L\u2019Abb\u00E9 plot requires binary outcome data (analysis type: metabin)."); return; }
                if (!java.util.Arrays.asList("RR", "OR", "RD", "ASD").contains(s.sm)) addText("L\u2019Abb\u00E9 Plot", "Note: the line of the pooled effect is only drawn for RR, OR, RD and ASD;\nthe study event rates are shown without it.");
                addPlot("L\u2019Abb\u00E9 Plot", sc -> { Plots.Opts q = copy(o); q.scale = sc; q.studyLabels = q.labbeLabels; return Plots.labbe(r, q); }); }, false});
            if (n.gosh) addSec(secs, names, "GOSH analysis", new Object[]{(Runnable) () -> { final double wRun = n.goshCl ? goshRunShare(n.goshSub) : 1.0; final Gosh[] gb = new Gosh[1]; say("GOSH analysis: fitting the subsets of studies ..."); Par.phase(0, wRun, () -> gb[0] = Gosh.run(r, n.goshSub, n.goshSeed)); final Gosh gs = gb[0]; final GoshCluster[] gcb = new GoshCluster[1]; if (n.goshCl) { say("GOSH analysis: detecting clusters of subsets ..."); Par.phase(wRun, 1, () -> gcb[0] = gs.detectClusters()); } final GoshCluster gc = gcb[0]; addText("GOSH Analysis", RegReport.goshText(gs, r));
                addPlot("GOSH Plot", sc -> { Plots.Opts q = copy(o); q.scale = sc; q.goshPalette = n.goshPal; return ExtraPlots.gosh(gs, r, q, n.goshHl, gc, n.goshTrimOrd); }); }, true, 0});
            if (n.gosh && n.goshReg) addSec(secs, names, "GOSH meta-regression", new Object[]{(Runnable) () -> goshRegression(r, n), true, 0});   // independent of the ordinary GOSH: runs at the same time, its output stays right below the ordinary GOSH
            final java.util.concurrent.ConcurrentSkipListSet<Integer> running = new java.util.concurrent.ConcurrentSkipListSet<>();   // sections that report progress and are running now
            final java.util.concurrent.atomic.AtomicReferenceArray<String> detail = new java.util.concurrent.atomic.AtomicReferenceArray<>(secs.size());   // what a running section says it is doing
            final Runnable refreshStatus = () -> {   // status line under the bar: the running sections (two at most), with their own detail when they give one
                StringBuilder sb = new StringBuilder(); int c = 0, more = 0;
                for (int sl : running) { String d = detail.get(sl); if (c < 2) { if (c++ > 0) sb.append("  |  "); sb.append(d != null ? d : names.get(sl)); } else more++; }
                if (more > 0) sb.append("  |  +").append(more).append(" more"); statusTo(gen, c == 0 ? null : sb.toString()); };
            final int total = secs.size(); final int[] wt = new int[total]; int wsum = 0;
            for (int si = 0; si < total; si++) { wt[si] = weightOf(names.get(si), secs.get(si), n); wsum += wt[si]; }
            final int totalW = wsum;
            final List<Object[]> order = new ArrayList<>(); final List<Runnable> tasks = new ArrayList<>();
            final java.util.concurrent.atomic.AtomicIntegerArray fracs = new java.util.concurrent.atomic.AtomicIntegerArray(total);   // progress (0..1000) of each running section
            for (int si = 0; si < total; si++) {
                Object[] sc = secs.get(si); final int slot = si;
                final Out out = new Out(); outs.add(out); final Runnable body = (Runnable) sc[0]; final boolean isHeavy = (boolean) sc[1];
                final boolean tracks = isHeavy || (sc.length > 3 && (boolean) sc[3]);
                final Runnable task = () -> {
                    if (gen != generation) return;
                    sink.set(out.items); Report.startCapture(); if (tracks) { running.add(slot); refreshStatus.run(); }
                    try { Par.phase(0, 0.9, body);   // the computation is 90 % of the section, the rendering of its plots the rest
                        // the plots of this section are rendered here (not on the UI thread), in parallel when the section has several
                        final List<Item> its = out.items; if (tracks && !its.isEmpty()) { detail.set(slot, names.get(slot) + ": drawing the plots ..."); refreshStatus.run(); } Par.nextPhase(0.9, 1); Par.forRange(its.size(), j -> { Item it = its.get(j); if (it.render != null) it.img = it.render.apply(1.0); }); }
                    catch (Exception ex) { out.error = ex; }
                    finally { Report.stopCapture(); sink.remove(); fracs.set(slot, 0); running.remove(slot); detail.set(slot, null); done.addAndGet(wt[slot]); progressTo(gen, progressOf(done.get(), fracs, wt, totalW)); }
                };
                final Runnable timed = !tracks ? task : () -> Par.withStatus(msg -> { detail.set(slot, msg); refreshStatus.run(); }, () -> Par.withProgress(fr -> { fracs.accumulateAndGet(slot, (int) (1000 * Math.min(1.0, Math.max(0.0, fr))), Math::max); progressTo(gen, progressOf(done.get(), fracs, wt, totalW)); }, task));
                order.add(new Object[]{timed, sc.length > 2 ? (Integer) sc[2] : (isHeavy ? 4 : 9), isHeavy});
            }
            // All sections run at the same time in the shared pool (the heaviest are started first); a section that is waiting for its own parallel loop leaves the idle threads to the others.
            // The results stay in the order of the sections, so the output is identical to a serial run.
            order.sort((a, b) -> Integer.compare((Integer) a[1], (Integer) b[1]));   // stable: equal ranks keep the order of the sections
            final List<java.util.concurrent.Future<?>> fs = new ArrayList<>();
            // the memory-hungry sections start in this order but at most Par.heavyLimit() at a time (one per 400 MB of maximum heap, -Dmetajava.heavy=N); the small ones are not limited
            final List<java.util.concurrent.Callable<Object>> heavyTasks = new ArrayList<>(), lightTasks = new ArrayList<>();
            for (Object[] ob : order) { final Runnable t = (Runnable) ob[0]; (((Boolean) ob[2]) ? heavyTasks : lightTasks).add(() -> { t.run(); return null; }); }
            fs.addAll(Par.submitLimited(heavyTasks, Par.heavyLimit()));
            for (java.util.concurrent.Callable<Object> t : lightTasks) fs.add(Par.submit(t));
            for (java.util.concurrent.Future<?> f : fs) f.get();
            checkCurrent(gen);
            SwingUtilities.invokeLater(() -> display(n, outs, r, gen, auto, scrollPos));
        } catch (java.util.concurrent.CancellationException | InterruptedException ex) { /* superseded by a newer run */
        } catch (Throwable ex) {
            final Exception e = ex instanceof Exception ? (Exception) ex : new RuntimeException(ex);
            SwingUtilities.invokeLater(() -> { if (gen != generation) return; runFinished(); if (auto) softError(e); else error(e); });
        }
    }

    /** UI thread: builds the Swing components from the finished sections (images are already rendered). */
    void display(Snap n, List<Out> outs, MetaResult r, int gen, boolean auto, int scrollPos) {
        if (gen != generation) return;
        results.removeAll(); textAreas.clear(); plots.clear(); shown.clear(); shownTitle = n.s.title; shownSource = fileLabel.getText(); lastText = ""; Exception first = null;
        if (!n.dataWarn.isEmpty()) { StringBuilder dw = new StringBuilder("The analysis was run, but please check these points in the data (row numbers count the data rows below the header line):\n"); for (String w : n.dataWarn) dw.append("\n- ").append(w); Item it = new Item("Data Check", BiasReport.wrapLines(dw.toString(), 100), null); shown.add(it); showText(it.title, it.text); }
        for (Out o : outs) { for (Item it : o.items) { shown.add(it); if (it.drap != null) showDrapery(it); else if (it.render == null) showText(it.title, it.text); else showPlot(it.title, it.render, it.img); } if (o.error != null && first == null) first = o.error; }
        for (String w : r.warnings) { shown.add(new Item("Warning", w, null)); showText("Warning", w); }
        strip.setProgress(100); runFinished();
        if (first != null) { if (auto) { softError(first); return; } error(first); }
        else status.setText("Done \u2013 k = " + r.overall.k + " studies");
        results.add(Box.createVerticalGlue()); results.revalidate(); results.repaint(); final boolean fa = auto; SwingUtilities.invokeLater(() -> resScroll.getVerticalScrollBar().setValue(fa ? scrollPos : 0));
    }

    void softError(Exception ex) {
        ex.printStackTrace(); results.removeAll(); JLabel l = new JLabel("<html><div style='width:520px;color:#667085'><b>Waiting for a complete setup:</b> " + String.valueOf(ex.getMessage()).replace("<", "&lt;") + "</div></html>"); l.setBorder(new EmptyBorder(20, 10, 10, 10)); results.add(l); status.setText("Incomplete setup"); results.revalidate(); results.repaint();
    }

    /** Two check boxes side by side in one row. */
    static JPanel pair(JCheckBox a, JCheckBox b) { JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 18, 0)); p.setOpaque(false); p.add(a); p.add(b); return p; }

    /** Optional numeric x-axis setting: blank = automatic; anything else must be a finite number (decimal comma accepted). */
    static String axisText(JTextField fld, String name) {
        String s = fld.getText().trim(); if (s.isEmpty()) return "";
        try { double v = Double.parseDouble(s.replace(',', '.')); if (!Double.isFinite(v)) throw new NumberFormatException(); if (name.endsWith("interval") && !(v > 0)) throw new IllegalArgumentException(name + " must be greater than 0."); return s; }
        catch (NumberFormatException e) { throw new IllegalArgumentException(name + ": '" + s + "' is not a number."); }
    }
    Plots.Opts forestOpts() { Plots.Opts o = new Plots.Opts(); o.sortBy = (String) sortBox.getSelectedItem(); o.ascending = sortDir.getSelectedIndex() == 0; o.analysisDetails = fDetails.isSelected(); o.testOverall = fOverallTest.isSelected(); o.hetStats = fHet.isSelected(); o.ciI2 = fCiI2.isSelected(); o.ciTau = fCiTau.isSelected();
        o.drapTarget = axisText(drapTargetF, "Drapery target effect-size (power computation only)"); o.drapType = (String) drapTypeBox.getSelectedItem(); o.drapLayout = (String) drapLayoutBox.getSelectedItem(); o.drapStudyColour = (String) drapStudyColBox.getSelectedItem(); o.drapLabels = (String) drapLabelsBox.getSelectedItem(); o.drapAngle = (Integer) drapAngleSp.getValue(); o.drapPrediction = drapPredChk.isSelected();
        o.drapPredColour = (String) drapPredColBox.getSelectedItem(); o.drapModel = (String) drapModelBox.getSelectedItem(); o.drapRandomColour = (String) drapRandomColBox.getSelectedItem(); o.drapCommonColour = (String) drapCommonColBox.getSelectedItem();
        o.theme = (String) themeBox.getSelectedItem(); o.squareColour = (String) squareColBox.getSelectedItem(); o.diamondFill = (String) diamondFillBox.getSelectedItem(); o.diamondBorder = (String) diamondBorderBox.getSelectedItem();
        o.nullColour = (String) nullColBox.getSelectedItem(); o.nullStyle = (String) nullStyleBox.getSelectedItem(); o.pooledColour = (String) pooledColBox.getSelectedItem(); o.pooledStyle = (String) pooledStyleBox.getSelectedItem();
        o.radialModel = (String) radialModelBox.getSelectedItem(); o.radialTheme = (String) radialThemeBox.getSelectedItem(); o.radialLabels = radialLabChk.isSelected(); o.radialSymbol = (String) radialSymbolBox.getSelectedItem(); o.radialBack = (String) radialBackBox.getSelectedItem(); o.radialCircle = (String) radialCircleBox.getSelectedItem();
        o.radialReg = (String) radialRegBox.getSelectedItem(); o.radialRef = (String) radialRefBox.getSelectedItem(); o.radialCI = (String) radialCiBox.getSelectedItem(); o.radialCILine = (String) radialCiLineBox.getSelectedItem(); o.labbeLabels = labbeLabChk.isSelected();
        o.repelBubble = regRepelChk.isSelected(); o.repelFunnel = funnelRepelChk.isSelected(); o.repelDoi = doiRepelChk.isSelected(); o.repelPet = petRepelChk.isSelected(); o.repelRadial = radialRepelChk.isSelected(); o.repelLabbe = labbeRepelChk.isSelected(); o.repelBias = biasRepelChk.isSelected(); o.biasCI = biasCiChk.isSelected(); o.biasPI = biasPiChk.isSelected();
        o.baujatModel = (String) baujatModelBox.getSelectedItem(); o.baujatTheme = (String) baujatThemeBox.getSelectedItem(); o.baujatSymbol = (String) baujatSymbolBox.getSelectedItem(); o.baujatBack = (String) baujatBackBox.getSelectedItem(); o.baujatSymSize = ((Number) baujatSymSize.getValue()).doubleValue(); o.baujatQuad = baujatQuadChk.isSelected(); o.baujatQuadRule = (String) baujatQuadBox.getSelectedItem(); o.baujatQuadColour = (String) baujatQuadColBox.getSelectedItem(); o.labbeTheme = (String) labbeThemeBox.getSelectedItem(); o.labbeSymbol = (String) labbeSymbolBox.getSelectedItem(); o.labbeBack = (String) labbeBackBox.getSelectedItem(); o.labbeCircle = (String) labbeCircleBox.getSelectedItem(); o.labbeRef = (String) labbeRefBox.getSelectedItem(); o.labbeEst = (String) labbeEstBox.getSelectedItem(); o.labbeCI = (String) labbeCiBox.getSelectedItem(); o.labbePI = (String) labbePiBox.getSelectedItem(); o.funnelTheme = (String) funnelThemeBox.getSelectedItem(); o.funnelBack = funnelBackChk.isSelected(); o.xLeft = xLeft.getText(); o.xRight = xRight.getText(); o.xMin = axisText(xMinF, "X-axis minimum"); o.xMax = axisText(xMaxF, "X-axis maximum"); o.xTick = axisText(xTickF, "X-axis tick interval"); if (!o.xMin.isEmpty() && !o.xMax.isEmpty() && Double.parseDouble(o.xMin.replace(',', '.')) >= Double.parseDouble(o.xMax.replace(',', '.'))) throw new IllegalArgumentException("X-axis minimum must be lower than X-axis maximum."); o.digits = (Integer) digitsSp.getValue(); o.spacing = (Double) spacingSp.getValue(); o.studyLabels = labChk.isSelected(); o.regLine = regLine.isSelected();
        o.confInt = regCI.isSelected(); o.bubbleStyle = (String) regStyleBox.getSelectedItem(); o.colPoints = (String) regColPoints.getSelectedItem(); o.colLine = (String) regColLine.getSelectedItem(); o.colBand = (String) regColBand.getSelectedItem(); o.colBandLine = (String) regColBandLine.getSelectedItem(); o.predInt = regPI.isSelected(); o.logAxis = regLog.isSelected() && regLog.isEnabled(); o.colPred = (String) regColPred.getSelectedItem(); o.colPredLine = (String) regColPredLine.getSelectedItem(); o.symbol = (String) regSymbolBox.getSelectedItem(); o.symSize = ((Number) regSymSize.getValue()).doubleValue(); return o; }
    static Plots.Opts copy(Plots.Opts a) { Plots.Opts o = new Plots.Opts(); for (java.lang.reflect.Field f : Plots.Opts.class.getFields()) try { f.set(o, f.get(a)); } catch (Exception e) { } return o; }

    void regression(MetaResult r, Snap n) {
        List<String> nums = n.nums, facs = n.facs; if (nums.isEmpty() && facs.isEmpty()) { addText("Meta-Regression", "Select at least one covariate or factor."); return; }
        Map<String, double[]> nd = new HashMap<>(); for (String c : nums) nd.put(c, n.tbl.num(c)); Map<String, String[]> fd = new HashMap<>(); for (String f : facs) fd.put(f, n.tbl.strings(f));
        Reg g = Reg.run(r, nums, nd, facs, fd, n.regIntercept, n.refs); List<String> terms = new ArrayList<>(nums); terms.addAll(facs);
        addText("Meta-Regression - Model 1: ~ " + String.join(" + ", terms), Report.regText(g, r, 1, ""));
        final double wPerm = n.perm && n.cmp ? (n.permExact ? 0.95 : 0.6) : 1.0;   // share of the section taken by the permutation test, the rest is the model comparison
        if (n.perm) { try { final Perm[] tb = new Perm[1]; Par.phase(0, wPerm, () -> tb[0] = Perm.run(g, n.permIter, n.permExact, n.permSeed, 0, n.permBudget)); Perm t = tb[0]; addText("Permutation Test (permutest) - Model 1", RegReport.permText(g, t, r)); }
            catch (IllegalArgumentException ex) { addText("Permutation Test (permutest) - Model 1", ex.getMessage()); } }
        if (n.cmp) { try { say("Meta-regression: comparing the models ..."); final ModelCompare[] mcb = new ModelCompare[1]; Par.phase(n.perm ? wPerm : 0, 1, () -> mcb[0] = ModelCompare.run(r, nums, nd, facs, fd, n.regIntercept)); ModelCompare mc = mcb[0]; addText("Model Comparison", RegReport.compareText(mc, r)); }
            catch (IllegalArgumentException ex) { addText("Model Comparison", ex.getMessage()); } }
        if (n.bubble && g.x1 != null) addPlot("Bubble Plot", sc -> { Plots.Opts q = copy(n.o); q.scale = sc; q.studyLabels = n.labels; q.regLine = n.regLine; return Plots.bubble(g, q, r); });
        if (n.adj) addPlot("Regression Plot (adjusted for all moderators)", sc -> { Plots.Opts q = copy(n.o); q.scale = sc; q.studyLabels = n.labels; q.regLine = n.regLine; return RegPlots.adjusted(g, nums, facs, n.adjFactor, q, r); });
    }

    /** GOSH with the meta-regression model of section 6: output right below the ordinary GOSH analysis and plot. */
    void goshRegression(MetaResult r, Snap n) {
        final String title = "GOSH Analysis - Meta-Regression";
        List<String> nums = n.nums, facs = n.facs;   // same switch as influence(): the model is the one of section 6, so the meta-regression has to be turned on there
        if (!n.reg || (nums.isEmpty() && facs.isEmpty())) { addText(title, "GOSH for a meta-regression model needs the model of the Meta-regression section: turn on the meta-regression there and select at least one covariate or factor."); return; }
        Map<String, double[]> nd = new HashMap<>(); for (String c : nums) nd.put(c, n.tbl.num(c)); Map<String, String[]> fd = new HashMap<>(); for (String f : facs) fd.put(f, n.tbl.strings(f));
        List<String> terms = new ArrayList<>(nums); terms.addAll(facs);
        final GoshReg[] grb = new GoshReg[1]; final double wRun = n.goshCl ? goshRunShare(n.goshSub) : 1.0;
        try { Reg.Design D = Reg.design(r, nums, nd, facs, fd, n.regIntercept, null, n.refs); say("GOSH meta-regression: fitting the subsets of studies ..."); Par.phase(0, wRun, () -> grb[0] = GoshReg.run(r, D, n.regIntercept, n.goshSub, n.goshSeed)); }
        catch (IllegalArgumentException ex) { addText(title + ": ~ " + String.join(" + ", terms), "GOSH for meta-regression: " + ex.getMessage()); return; }
        final GoshReg gr = grb[0];
        if (n.goshCl) { say("GOSH meta-regression: detecting clusters of subsets ..."); Par.phase(wRun, 1, () -> gr.detectClusters()); }
        addText(title + ": ~ " + String.join(" + ", terms), RegReport.goshRegText(gr, r));
        addPlot("GOSH Meta-Regression: Coefficients versus Residual Heterogeneity", sc -> { Plots.Opts q = copy(n.o); q.scale = sc; q.goshPalette = n.goshPal; return ExtraPlots.goshRegCoef(gr, q, n.goshTrim, n.goshHl); });
        addPlot("GOSH Meta-Regression: Scatterplot Matrix", sc -> { Plots.Opts q = copy(n.o); q.scale = sc; q.goshPalette = n.goshPal; return ExtraPlots.goshRegMatrix(gr, q, n.goshTrim, n.goshHl); });
    }

    void influence(MetaResult r, MetaSpec s, Snap n) {
        List<String> nums = n.nums, facs = n.facs;
        List<Integer> ok = new ArrayList<>(); for (int i = 0; i < r.TE.length; i++) if (!Double.isNaN(r.TE[i]) && r.seTE[i] > 0) ok.add(i);
        int k = ok.size(); double[] y = new double[k], v = new double[k]; String[] lab = new String[k]; for (int j = 0; j < k; j++) { int i = ok.get(j); y[j] = r.TE[i]; v[j] = r.seTE[i] * r.seTE[i]; lab[j] = r.studlab[i]; }
        double[][] X = null; String[] names = null;
        if (n.reg && !(nums.isEmpty() && facs.isEmpty())) { Map<String, double[]> nd = new HashMap<>(); for (String c : nums) nd.put(c, n.tbl.num(c)); Map<String, String[]> fd = new HashMap<>(); for (String f : facs) fd.put(f, n.tbl.strings(f));
            Reg g = Reg.run(r, nums, nd, facs, fd, n.regIntercept, n.refs); if (g.k == k) { X = g.fit.X; names = g.coefNames; } }
        boolean hk = X != null && s.methodRandomCi.equals("HK");   // regression model with test="knha" (adhoc when the se-floor is on)
        int[] okIdx = new int[k]; for (int j = 0; j < k; j++) okIdx[j] = ok.get(j);
        Influence in = Influence.run(y, v, X, s.methodTau, lab, names == null ? new String[]{"intrcpt"} : names, hk, hk && s.adhocHakn.equals("se"), org.metajava.core.Pool.genqWeightsFor(s, okIdx));
        addText("Outlier and Influence Diagnostics", Report.influenceText(in));
        addPlot("Influence Diagnostics Plots", sc -> { Plots.Opts q = new Plots.Opts(); q.scale = sc; return Plots.influence(in, q); });
    }

    /** Called from analysis threads: collects the item in the current section. */
    void addText(String title, String text) { Item it = new Item(title, text, null); it.tables = Report.drain(); sink.get().add(it); }
    /** Called from analysis threads: the merged section "Drapery Plot Analysis and Interpretation" (statistical report with its study table, and the narrative). */
    void addDrapery(org.metajava.plot.DraperyReport.Result dr) { Item it = new Item(org.metajava.plot.DraperyReport.TITLE, dr.combined(), null); it.tables = Report.drain(); it.drap = dr; sink.get().add(it); }
    void addPlot(String title, DoubleFunction<BufferedImage> render) { sink.get().add(new Item(title, null, render)); }

    /** UI thread only. */
    void showText(String title, String text) {
        lastText += "== " + title + " ==\n" + text + "\n\n";
        JLabel t = new JLabel(title); t.setFont(new Font("SansSerif", Font.BOLD, 18)); t.setForeground(Ui.PRIMARY); t.setAlignmentX(LEFT_ALIGNMENT); t.setBorder(new EmptyBorder(14, 0, 6, 0)); results.add(t);
        JTextArea a = new JTextArea(text); a.setEditable(false); a.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12)); a.setBackground(Color.WHITE); a.setAlignmentX(LEFT_ALIGNMENT); a.setBorder(new EmptyBorder(0, 0, 0, 0)); a.putClientProperty("raw", text); textAreas.add(a); a.setText(wrapped(a, text)); results.add(a);
    }
    /** UI thread: the drapery report. Statistical report (the individual-study table is an expandable block, collapsed at first) and the narrative, each with its own copy button. */
    void showDrapery(Item it) {
        final org.metajava.plot.DraperyReport.Result dr = it.drap; lastText += "== " + it.title + " ==\n" + it.text + "\n\n";
        JLabel t = new JLabel(it.title); t.setFont(new Font("SansSerif", Font.BOLD, 18)); t.setForeground(Ui.PRIMARY); t.setAlignmentX(LEFT_ALIGNMENT); t.setBorder(new EmptyBorder(16, 0, 6, 0)); results.add(t);
        // statistical report: all sections, the individual-study table included, in the same black text
        JLabel h1 = new JLabel("Statistical report"); h1.setFont(new Font("SansSerif", Font.BOLD, 14)); h1.setAlignmentX(LEFT_ALIGNMENT); h1.setBorder(new EmptyBorder(2, 0, 4, 0)); results.add(h1);
        results.add(proseArea(dr.statisticalReport()));
        // narrative
        JLabel h2 = new JLabel("Drapery plot analysis"); h2.setFont(new Font("SansSerif", Font.BOLD, 14)); h2.setAlignmentX(LEFT_ALIGNMENT); h2.setBorder(new EmptyBorder(14, 0, 4, 0)); results.add(h2);
        results.add(proseArea(dr.narrativeText()));
    }
    /** Read-only text block that is re-wrapped with the panel width like the other text sections. */
    JTextArea proseArea(String text) {
        JTextArea a = new JTextArea(text); a.setEditable(false); a.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12)); a.setBackground(Color.WHITE); a.setAlignmentX(LEFT_ALIGNMENT); a.setBorder(new EmptyBorder(0, 0, 0, 0));
        a.putClientProperty("raw", text); textAreas.add(a); a.setText(wrapped(a, text)); return a;
    }
    void copyText(String text, String msg) { try { java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(text), null); status.setText(msg); } catch (Exception ex) { error(ex); } }
    /** Number of monospaced characters that fit in the visible width of the results panel. */
    int fitCols(JTextArea a) {
        int vw = resScroll.getViewport().getWidth(); if (vw <= 0) return 100;
        Insets in = results.getBorder() == null ? new Insets(0, 0, 0, 0) : results.getBorder().getBorderInsets(results);
        int cw = Math.max(1, a.getFontMetrics(a.getFont()).charWidth('m'));
        return Math.max(40, (vw - in.left - in.right - 24) / cw);
    }
    String wrapped(JTextArea a, String text) { return org.metajava.core.BiasReport.wrapLines(text, fitCols(a)); }
    /** Re-wraps the prose of every text block when the panel width changes (tables with aligned columns are left alone). */
    void rewrap() {
        if (textAreas.isEmpty()) return; int c = fitCols(textAreas.get(0)); if (c == wrapCols) return; wrapCols = c;
        for (JTextArea a : textAreas) { String raw = (String) a.getClientProperty("raw"); if (raw != null) a.setText(org.metajava.core.BiasReport.wrapLines(raw, c)); }
        results.revalidate(); results.repaint();
    }
    void showPlot(String title, DoubleFunction<BufferedImage> render, BufferedImage img) {
        plots.add(new Object[]{title, render});
        JLabel t = new JLabel(title); t.setFont(new Font("SansSerif", Font.BOLD, 18)); t.setForeground(Ui.PRIMARY); t.setAlignmentX(LEFT_ALIGNMENT); t.setBorder(new EmptyBorder(16, 0, 6, 0)); results.add(t);
        JLabel il = new JLabel(new ImageIcon(img)); il.setAlignmentX(LEFT_ALIGNMENT); results.add(il);
        JButton sv = Ui.button("Save this plot\u2026", false); sv.setAlignmentX(LEFT_ALIGNMENT); sv.addActionListener(e -> savePlot(title, render)); results.add(Box.createVerticalStrut(4)); results.add(sv);
    }

    double scaleDpi() { return ((Integer) dpiSp.getValue()) / 96.0; }
    /** Writes one plot as PNG, TIFF, JPG, SVG or PDF. The raster formats are rendered at the chosen resolution (scale = dpi / 96) and carry it as metadata. SVG and PDF are resolution independent: they run the same drawing code at scale 1 (page = the plot's natural size, 1 px = 0.75 pt in the PDF), so the dpi setting does not enlarge the page. */
    public static void writePlot(DoubleFunction<BufferedImage> render, File f, String fmt, double sc, String title) throws IOException {
        if (RasterWriter.isRaster(fmt)) { RasterWriter.write(render.apply(sc), f, fmt, sc * 96.0); return; }
        org.metajava.plot.VectorGraphics vg = Canvas.record(render, 1.0);
        try (OutputStream o = new BufferedOutputStream(new FileOutputStream(f))) { if (fmt.equals("SVG")) VectorExport.svg(vg, o, title); else VectorExport.pdf(vg, o, title); }
    }
    void savePlot(String title, DoubleFunction<BufferedImage> render) {
        PlotChooser fc = new PlotChooser(title, (String) fmtBox.getSelectedItem());
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION || fc.target() == null) return;
        final File f = fc.target(); final String fmt = fc.targetFormat();   // extension and type both follow the type chosen in the dialog
        final double sc = scaleDpi(); status.setText("Saving\u2026"); busy.setIndeterminate(true); busy.setVisible(true);
        new Thread(() -> {   // high-dpi rendering happens off the UI thread
            try { writePlot(render, f, fmt, sc, title); SwingUtilities.invokeLater(() -> { busy.setIndeterminate(false); busy.setVisible(false); status.setText("Saved " + f.getName()); }); }
            catch (Exception ex) { SwingUtilities.invokeLater(() -> { busy.setIndeterminate(false); busy.setVisible(false); error(ex); }); }
        }, "metajava-save").start();
    }
    @SuppressWarnings("unchecked")
    void savePlots() {
        if (plots.isEmpty()) { JOptionPane.showMessageDialog(this, "Run an analysis first."); return; }
        JFileChooser fc = new JFileChooser(); fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        final File dir = fc.getSelectedFile(); final List<Object[]> ps = new ArrayList<>(plots); final double sc = scaleDpi(); final String fmt = (String) fmtBox.getSelectedItem(); status.setText("Saving plots\u2026"); busy.setIndeterminate(true); busy.setVisible(true);
        new Thread(() -> {
            try { Par.forRange(ps.size(), i -> { try { writePlot((DoubleFunction<BufferedImage>) ps.get(i)[1], new File(dir, String.format("%02d_%s.%s", i + 1, ((String) ps.get(i)[0]).replaceAll("[^A-Za-z0-9]+", "_"), RasterWriter.ext(fmt))), fmt, sc, (String) ps.get(i)[0]); } catch (IOException e) { throw new UncheckedIOException(e); } });
                SwingUtilities.invokeLater(() -> { busy.setIndeterminate(false); busy.setVisible(false); status.setText("Saved " + ps.size() + " plots (" + fmt + ")"); }); }
            catch (Exception ex) { SwingUtilities.invokeLater(() -> { busy.setIndeterminate(false); busy.setVisible(false); error(ex); }); }
        }, "metajava-save").start();
    }
    void saveText() {
        JFileChooser fc = new JFileChooser(); fc.setSelectedFile(new File("meta_results.txt")); if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try (Writer w = new OutputStreamWriter(new FileOutputStream(fc.getSelectedFile()), "UTF-8")) { w.write(lastText); status.setText("Saved results"); } catch (Exception ex) { error(ex); }
    }

    // ===== project files (JSON) =====
    private File lastDir;
    private File choose(boolean save, String desc, String ext, String defName) {
        JFileChooser fc = new JFileChooser(lastDir); fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(desc, ext)); if (save) fc.setSelectedFile(new File(lastDir, defName));
        if ((save ? fc.showSaveDialog(this) : fc.showOpenDialog(this)) != JFileChooser.APPROVE_OPTION) return null;
        File f = fc.getSelectedFile(); lastDir = f.getParentFile();
        if (save) { if (!f.getName().toLowerCase().endsWith("." + ext)) f = new File(f.getParentFile(), f.getName() + "." + ext);
            if (f.exists() && JOptionPane.showConfirmDialog(this, f.getName() + " already exists. Replace it?", "Confirm", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return null; }
        return f;
    }
    void saveProject() {
        String base = file == null ? "metajava_project" : file.getName().replaceFirst("\\.[^.]*$", "") + "_project";
        File f = choose(true, "MetaJava project (*.json)", "json", base + ".json"); if (f == null) return;
        try { ProjectIO.save(this, f); status.setText("Project saved: " + f.getName()); } catch (Exception ex) { error(ex); }
    }
    void loadProject() {
        File f = choose(false, "MetaJava project (*.json)", "json", null); if (f == null) return;
        try { ProjectIO.load(this, f); status.setText("Project loaded: " + f.getName()); } catch (Exception ex) { error(new Exception("Cannot open the project: " + ex.getMessage(), ex)); }
    }

    // ===== Excel and Word export =====
    private ResultsDoc buildDoc(double scale) throws IOException {
        ResultsDoc doc = new ResultsDoc(); doc.title = shownTitle == null ? "" : shownTitle;
        doc.subtitle = "Generated by MetaJava on " + java.time.LocalDate.now() + (shownSource == null || shownSource.isEmpty() || shownSource.startsWith("No file") ? "" : " \u00B7 Data: " + shownSource);
        final List<Item> items = new ArrayList<>(shown); final byte[][] png = new byte[items.size()][]; final int[][] dim = new int[items.size()][];
        if (scale > 0) Par.forRange(items.size(), i -> { Item it = items.get(i); if (it.render == null) return;
            try { BufferedImage im = it.render.apply(scale); ByteArrayOutputStream bo = new ByteArrayOutputStream(); ImageIO.write(im, "png", bo); png[i] = bo.toByteArray(); dim[i] = new int[]{im.getWidth(), im.getHeight()}; } catch (IOException e) { throw new UncheckedIOException(e); } });
        for (int i = 0; i < items.size(); i++) { Item it = items.get(i); if (it.render == null) doc.text(it.title, it.text, it.tables); else if (png[i] != null) doc.figure(it.title, png[i], dim[i][0], dim[i][1], scale * 96.0); }
        return doc;
    }
    void saveExcel() {
        if (shown.isEmpty()) { JOptionPane.showMessageDialog(this, "Run an analysis first."); return; }
        File f = choose(true, "Excel workbook (*.xlsx)", "xlsx", "meta_results.xlsx"); if (f == null) return;
        try { Xlsx.write(f, buildDoc(0)); status.setText("Saved tables: " + f.getName()); } catch (Exception ex) { error(ex); }
    }
    void saveWord() {
        if (shown.isEmpty()) { JOptionPane.showMessageDialog(this, "Run an analysis first."); return; }
        File f = choose(true, "Word document (*.docx)", "docx", "meta_report.docx"); if (f == null) return;
        final double sc = Math.min(scaleDpi(), 300 / 96.0);   // figures at the chosen resolution, at most 300 dpi (keeps the file a reasonable size)
        status.setText("Writing report\u2026"); busy.setIndeterminate(true); busy.setVisible(true);
        new Thread(() -> {
            try { Docx.write(f, buildDoc(sc)); SwingUtilities.invokeLater(() -> { busy.setIndeterminate(false); busy.setVisible(false); status.setText("Saved report: " + f.getName()); }); }
            catch (Throwable ex) { SwingUtilities.invokeLater(() -> { busy.setIndeterminate(false); busy.setVisible(false); error(ex instanceof Exception ? (Exception) ex : new RuntimeException(ex)); }); }
        }, "metajava-docx").start();
    }

    static final java.util.prefs.Preferences PREFS = java.util.prefs.Preferences.userNodeForPackage(MainWindow.class);
    public static void main(String[] args) {
        if (System.getProperty("metajava.threads") == null) { try { Par.setThreads(PREFS.getInt("threads", 4)); } catch (Exception e) { } }   // -Dmetajava.threads=N wins over the remembered setting
        Ui.install(); SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true)); }
}
