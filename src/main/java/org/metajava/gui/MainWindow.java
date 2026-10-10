package org.metajava.gui;

import org.metajava.core.*;
import org.metajava.io.TableReader;
import org.metajava.plot.Plots;
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
    static final String[] TAU = {"REML", "DL", "PM", "ML", "HS", "SJ", "HE", "EB"};

    DataTable table; File file; List<String> sheets = new ArrayList<>();
    final JComboBox<String> typeBox = Ui.combo(Arrays.stream(TYPES).map(t -> t[1]).toArray(String[]::new));
    final JPanel mapPanel = new JPanel(new GridBagLayout()); final Map<String, JComboBox<String>> maps = new LinkedHashMap<>();
    JComboBox<String> studlabBox = Ui.combo(), subgroupBox = Ui.combo(), orderBox = Ui.combo(), sheetBox = Ui.combo();
    JList<String> covList = new JList<>(), facList = new JList<>();
    final JLabel fileLabel = Ui.muted("No file loaded");
    final JTable preview = new JTable(); final JPanel results = new JPanel(); final JScrollPane resScroll;
    // settings
    JComboBox<String> modelBox = Ui.combo("Random effects", "Common effect", "Both"), smBox = Ui.combo(), methodBox = Ui.combo("Inverse"), tauBox = Ui.combo(TAU),
        tauCiBox = Ui.combo("Default", "QP", "BJ", "J", "None"), randCiBox = Ui.combo("classic", "HK", "KR"), adhocBox = Ui.combo("none", "se", "ci", "IQWiG6"),
        predBox = Ui.combo("V", "HTS", "HK", "HK-PR", "KR", "KR-PR", "NNF", "S"), adhocPiBox = Ui.combo("none", "se"), i2Box = Ui.combo("Q", "tau2"), commonCiBox = Ui.combo("classic", "IVhet"), incrBox = Ui.combo("only 0", "if 0 all", "all"),
        smdBox = Ui.combo("Hedges", "Cohen", "Glass"), glmmModelBox = Ui.combo("UM.FS", "UM.RS", "CM.EL", "CM.AL"), nagqBox = Ui.combo("7", "1", "2", "3", "5", "9", "11", "15", "21"), ciBox = Ui.combo("Default", "z", "t", "CP", "WS", "WSCC", "AC", "SA", "SM", "exact"), genMode = Ui.combo("Effect size & SE", "Estimate & CI (natural scale)");
    JCheckBox predChk = Ui.check("Prediction interval", true), backChk = Ui.check("Back-transform results", true), mhExact = Ui.check("MH.exact (no continuity correction in MH)", false),
        pooledvar = Ui.check("Pooled variance (MD)", false), allStudies = Ui.check("Include studies with zero events in both arms", false), tauCommon = Ui.check("Assume common tau\u00B2 across subgroups", false);
    JTextField predLevelF = new JTextField("95", 5), levelF = new JTextField("0.95", 6), incrF = new JTextField("0.5", 6), nullF = new JTextField("", 6), pscaleF = new JTextField("1", 6), titleF = new JTextField(14);
    // forest options
    JCheckBox forestChk = Ui.check("Forest plot", true), fDetails = Ui.check("Analysis details", false), fOverallTest = Ui.check("Overall effect test", true), fHet = Ui.check("Heterogeneity statistics", true), fCiI2 = Ui.check("I\u00B2 confidence interval", false), fCiTau = Ui.check("Tau\u00B2 confidence interval", false);
    JComboBox<String> sortBox = Ui.combo("Original order", "Effect size", "Standard error", "Weight", "Study label"), sortDir = Ui.combo("Ascending", "Descending");
    JTextField xLeft = new JTextField(10), xRight = new JTextField(10); JSpinner digitsSp = new JSpinner(new SpinnerNumberModel(2, 0, 6, 1)), spacingSp = new JSpinner(new SpinnerNumberModel(1.0, 0.6, 2.5, 0.1));
    // other analyses
    JCheckBox sgChk = Ui.check("Subgroup analysis (uses the subgroup column)", true), sgPred = Ui.check("Subgroup prediction interval", false), sgTestEffect = Ui.check("Test for effect in subgroup", false), sgDetails = Ui.check("Details of meta-analysis methods", false), regChk = Ui.check("Meta-regression", false), bubbleChk = Ui.check("Bubble plot", true), regLine = Ui.check("Regression line", true), regIntercept = Ui.check("Intercept", true),
        looChk = Ui.check("Leave-one-out analysis", false), looForest = Ui.check("Forest plot", true), cumChk = Ui.check("Cumulative meta-analysis", false), cumForest = Ui.check("Forest plot", true),
        funnelChk = Ui.check("Funnel plot", true), contourChk = Ui.check("Contour-enhanced", false), labChk = Ui.check("Study labels", true), biasChk = Ui.check("Test for funnel plot asymmetry", true), biasPlotChk = Ui.check("Plot", true),
        tfChk = Ui.check("Trim and fill", false), infChk = Ui.check("Outlier & influence diagnostics (metafor)", false), baujatChk = Ui.check("Baujat plot", false), radialChk = Ui.check("Radial (Galbraith) plot", false),
        doiChk = Ui.check("Doi plot and LFK index", false), doiApexChk = Ui.check("Mark the apex (dashed line)", false), labbeChk = Ui.check("L\u2019Abb\u00E9 plot (binary outcomes)", false);
    JCheckBox petChk = Ui.check("PET-PEESE (small-study effects)", false), petPlotChk = Ui.check("PET-PEESE plot", true), selChk = Ui.check("Selection model (3-parameter)", false), failChk = Ui.check("Fail-safe N", false);
    JComboBox<String> selDir = Ui.combo("Auto", "Positive", "Negative");
    // meta-regression extras, subgroup plots, GOSH
    JCheckBox permChk = Ui.check("Permutation test (permutest)", false), permExactChk = Ui.check("Exact test when k \u2264 9 (all k! permutations)", false), cmpChk = Ui.check("Model comparison table (AIC / BIC / R\u00B2)", false),
        orchardChk = Ui.check("Orchard plot (subgroups)", false), caterChk = Ui.check("Caterpillar plot (subgroups)", false), goshChk = Ui.check("GOSH plot (heterogeneity diagnostics)", false), goshHlChk = Ui.check("Colour subsets by the most influential study", false);
    JSpinner permIterSp = new JSpinner(new SpinnerNumberModel(5000, 100, 100000, 1000)), goshSubSp = new JSpinner(new SpinnerNumberModel(20000, 1000, 500000, 5000));
    JTextField permSeedF = new JTextField("1", 6), goshSeedF = new JTextField("1", 6);
    JComboBox<String> biasMethod = Ui.combo("Default", "Egger", "Begg", "Thompson", "Harbord", "Peters", "Macaskill", "Deeks", "Pustejovsky", "Schwarzer");
    JComboBox<String> tfSide = Ui.combo("Auto", "Left", "Right"), tfEst = Ui.combo("L0", "R0");
    JSpinner dpiSp = new JSpinner(new SpinnerNumberModel(300, 72, 1200, 100)); JComboBox<String> fmtBox = Ui.combo("PNG", "TIFF", "JPG", "SVG", "PDF");
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
        JPanel left = leftPanel(); JScrollPane ls = new JScrollPane(left); ls.setBorder(null); ls.getVerticalScrollBar().setUnitIncrement(16); ls.setPreferredSize(new Dimension(470, 800));
        results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS)); results.setBackground(Color.WHITE); results.setBorder(new EmptyBorder(14, 18, 14, 18));
        resScroll = new JScrollPane(results); resScroll.getVerticalScrollBar().setUnitIncrement(24); resScroll.setBorder(new LineBorder(Ui.BORDER));
        resScroll.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() { public void componentResized(java.awt.event.ComponentEvent e) { rewrap(); } });   // long lines are re-wrapped to the window width, so no sideways scrolling is needed
        JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, ls, resScroll); sp.setDividerLocation(470); sp.setBorder(new EmptyBorder(8, 8, 0, 8)); sp.setOpaque(false);
        add(sp, BorderLayout.CENTER);
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 5)); bar.setBackground(Ui.BG); bar.add(status); busy.setPreferredSize(new Dimension(160, 12)); busy.setVisible(false); bar.add(busy); add(bar, BorderLayout.SOUTH);
        autoTimer.setRepeats(false); typeBox.addActionListener(e -> typeChanged()); typeChanged(); hook(left);
        showWelcome();
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
        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0)); east.setOpaque(false); east.add(cl); east.add(th); east.add(run);
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
        JButton open = Ui.button("Open file\u2026", true), ex = Ui.button("Example data", false); open.addActionListener(e -> openFile()); ex.addActionListener(e -> loadExample());
        btns.add(open); btns.add(ex); d.row(null, btns); d.row(null, fileLabel); d.row("Sheet", sheetBox); sheetBox.addActionListener(e -> { if (file != null && sheetBox.getSelectedIndex() >= 0 && !loading) load(file, sheetBox.getSelectedIndex()); });
        preview.setFillsViewportHeight(true); preview.setFont(Ui.BASE.deriveFont(11f)); preview.setRowHeight(20); preview.getTableHeader().setFont(Ui.BOLD.deriveFont(11f));
        JScrollPane ps = new JScrollPane(preview); ps.setPreferredSize(new Dimension(380, 140)); d.row(null, ps);
        p.add(d); p.add(Box.createVerticalStrut(8));
        // analysis type + variables
        Ui.Section v = new Ui.Section("2. Analysis type and variables", true); v.row("Type", typeBox);
        mapPanel.setOpaque(false); v.row(null, mapPanel); v.row("Study label", studlabBox); v.row("Order (cumulative)", orderBox); v.row("Input mode (generic)", genMode);
        covList.setVisibleRowCount(4); facList.setVisibleRowCount(4); noneBehaviour(covList); noneBehaviour(facList);
        p.add(v); p.add(Box.createVerticalStrut(8));
        // meta-analysis
        Ui.Section m = new Ui.Section("3. Meta-analysis settings", false);
        m.row("Model", modelBox); m.row("Effect measure (sm)", smBox); m.row("Pooling method", methodBox); m.row("GLMM model (model.glmm)", glmmModelBox); m.row("GLMM quadrature points (nAGQ)", nagqBox); m.row("Heterogeneity estimator (tau\u00B2)", tauBox); m.row("CI method for tau\u00B2", tauCiBox);
        m.row("Random effects CI method", randCiBox); m.row("Ad hoc HK variance correction", adhocBox); m.row("Common effect CI method", commonCiBox); m.row("Prediction interval method", predBox); m.row("Ad hoc HK correction (PI)", adhocPiBox); m.row(null, predChk); { JPanel pp = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0)); pp.setOpaque(false); pp.add(predLevelF); pp.add(Ui.label("%")); m.row("PI confidence level", pp); }
        m.row("I\u00B2 calculation", i2Box); m.row("Confidence level", levelF); m.row("Null effect (natural scale)", nullF); m.row(null, backChk); m.row("Individual study CI method", ciBox);
        m.row("Continuity correction (incr)", incrF); m.row("Apply correction", incrBox); m.row(null, mhExact); m.row(null, allStudies); m.row("SMD method", smdBox); m.row(null, pooledvar); m.row("Scale factor (proportions/rates)", pscaleF); m.row("Title", titleF);
        p.add(m); p.add(Box.createVerticalStrut(8));
        Ui.Section f = new Ui.Section("4. Forest plot", false);
        f.row(null, forestChk); f.row("Sort by", sortBox); f.row("Order", sortDir); f.row(null, fOverallTest); f.row(null, fHet); f.row(null, fCiI2); f.row(null, fCiTau); f.row(null, fDetails); f.row("X-axis left label", xLeft); f.row("X-axis right label", xRight); f.row("Digits", digitsSp); f.row("Row spacing", spacingSp);
        p.add(f); p.add(Box.createVerticalStrut(8));
        Ui.Section sg = new Ui.Section("5. Subgroup analysis", false); sg.row("Subgroup variable", subgroupBox); sg.row(null, sgChk); sg.row(null, tauCommon); sg.row(null, sgPred); sg.row(null, sgTestEffect); sg.row(null, sgDetails); sg.row(null, orchardChk); sg.row(null, caterChk); p.add(sg); p.add(Box.createVerticalStrut(8));
        Ui.Section rg = new Ui.Section("6. Meta-regression", false); rg.row(null, regChk); rg.row("Covariates (numeric)", new JScrollPane(covList)); rg.row("Factors (categorical)", new JScrollPane(facList)); rg.row(null, regIntercept); rg.row(null, bubbleChk); rg.row(null, regLine);
        permChk.setToolTipText("Permutation test for the moderators (metafor::permutest): the rows of the model matrix are permuted and the model is refitted each time. Applies to the model of this section.");
        permIterSp.setToolTipText("Number of random permutations (ignored by the exact test). Precision of the p-value is about 1/iterations."); permSeedF.setToolTipText("Seed of the random number generator: the same seed gives the same p-values");
        cmpChk.setToolTipText("Fits every subset of the selected covariates / factors on the same studies and compares AIC, AICc, BIC (maximum likelihood), tau\u00B2, I\u00B2 and R\u00B2");
        rg.row(null, permChk); rg.row("Permutations", permIterSp); rg.row("Random seed (permutations)", permSeedF); rg.row(null, permExactChk); rg.row(null, cmpChk); p.add(rg); p.add(Box.createVerticalStrut(8));
        Ui.Section lo = new Ui.Section("7. Leave-one-out analysis", false); lo.row(null, looChk); lo.row(null, looForest); p.add(lo); p.add(Box.createVerticalStrut(8));
        Ui.Section cu = new Ui.Section("8. Cumulative meta-analysis", false); cu.row(null, cumChk); cu.row(null, cumForest); p.add(cu); p.add(Box.createVerticalStrut(8));
        Ui.Section pb = new Ui.Section("9. Publication bias", false); pb.row(null, funnelChk); pb.row(null, contourChk); pb.row(null, labChk); pb.row(null, biasChk); pb.row("Test method", biasMethod); pb.row(null, biasPlotChk);
        pb.row(null, tfChk); pb.row("Trim-fill side", tfSide); pb.row("Trim-fill estimator", tfEst); pb.row(null, doiChk); pb.row(null, doiApexChk);
        petChk.setToolTipText("Precision-effect test (PET) and precision-effect estimate with standard error (PEESE): regression-based effect corrected for small-study effects (Stanley & Doucouliagos, 2014)");
        selChk.setToolTipText("Vevea-Hedges three-parameter selection model: adjusted effect when non-significant results are less likely to be published (works best with 20-30 or more studies)");
        selDir.setToolTipText("Direction in which significant results are favoured for publication. Auto: the direction of the pooled effect");
        failChk.setToolTipText("Number of unpublished null studies needed to make the combined result non-significant (Rosenthal, Rosenberg)");
        pb.row(null, petChk); pb.row(null, petPlotChk); pb.row(null, selChk); pb.row("Selection direction", selDir); pb.row(null, failChk); p.add(pb); p.add(Box.createVerticalStrut(8));
        Ui.Section dg = new Ui.Section("10. Diagnostics", false); dg.row(null, infChk); dg.row(null, baujatChk); dg.row(null, radialChk); dg.row(null, labbeChk);
        goshChk.setToolTipText("Graphical display of study heterogeneity (Olkin et al., 2012; metafor::gosh): the random-effects model is fitted to all (or many random) subsets of the studies; pooled estimate against I\u00B2");
        goshSubSp.setToolTipText("All subsets are used when there are fewer than this many (at most 18 studies); otherwise this many random subsets"); goshSeedF.setToolTipText("Seed for the random subsets");
        dg.row(null, goshChk); dg.row("GOSH subsets", goshSubSp); dg.row("Random seed (GOSH)", goshSeedF); dg.row(null, goshHlChk); p.add(dg); p.add(Box.createVerticalStrut(8));
        Ui.Section ou = new Ui.Section("11. Export", false); JButton sa = Ui.button("Save all plots\u2026", false), st = Ui.button("Save results (.txt)\u2026", false), sx = Ui.button("Save tables (.xlsx)\u2026", false), sw = Ui.button("Save full report (.docx)\u2026", false); sa.addActionListener(e -> savePlots()); st.addActionListener(e -> saveText()); sx.addActionListener(e -> saveExcel()); sw.addActionListener(e -> saveWord());
        sx.setToolTipText("Excel workbook with the results tables: one sheet per analysis section"); sw.setToolTipText("Word document with the whole output (paragraphs, tables and figures) in the order of the results panel");
        dpiSp.setToolTipText("Resolution of PNG, TIFF and JPG files. SVG and PDF are resolution independent and ignore it."); fmtBox.addActionListener(e -> dpiSp.setEnabled(RasterWriter.isRaster((String) fmtBox.getSelectedItem()))); dpiSp.setEnabled(RasterWriter.isRaster((String) fmtBox.getSelectedItem()));
        ou.row("Plot format", fmtBox); ou.row("Plot resolution (dpi)", dpiSp); ou.row(null, sa); ou.row(null, st); ou.row(null, sx); ou.row(null, sw); p.add(ou);
        p.add(Box.createVerticalGlue());
        modelBox.setSelectedIndex(0); return p;
    }

    boolean loading = false;
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
        for (java.awt.event.ActionListener l : methodBox.getActionListeners()) methodBox.removeActionListener(l);
        methodBox.addActionListener(e -> methodChanged(type));
        refreshColumns(); hook(mapPanel); mapPanel.revalidate(); mapPanel.repaint(); suppress = false; schedule();
    }

    /** GLMM needs a specific effect measure, ML for tau^2 and a z/t Wald test (as meta requires); select these automatically. */
    void methodChanged(String type) {
        boolean glmm = "GLMM".equals(methodBox.getSelectedItem()), lrp = "LRP".equals(methodBox.getSelectedItem());
        glmmModelBox.setEnabled(glmm && glmmModelBox.getItemCount() > 1); nagqBox.setEnabled(glmm);
        if (lrp) { boolean o = suppress; suppress = true; smBox.setSelectedItem("OR"); String pm = (String) predBox.getSelectedItem(); if (!"V".equals(pm) && !"HTS".equals(pm) && !"S".equals(pm)) predBox.setSelectedItem("V"); if ("KR".equals(randCiBox.getSelectedItem())) randCiBox.setSelectedItem("classic"); suppress = o; schedule(); return; }
        if (!glmm) return;
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
        covList.setModel(m1); facList.setModel(m2); covList.setSelectedIndex(0); facList.setSelectedIndex(0); suppress = old; schedule();
    }

    static final String NONE = "none";
    /** Selected entries of a covariate/factor list, ignoring the "none" entry. */
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
    String guess(String key, List<String> cols) {
        String[][] alias = {
            {"event.e", "eventse", "eventsexp", "eventstreat", "eventstreatment", "eventsintervention", "eventsexperimental", "events1", "evente", "ae"}, {"n.e", "ne", "ntreat", "ntreatment", "nexp", "nexperimental", "ntotal1", "totalexp", "totaltreatment", "n1", "totalexperimental"},
            {"event.c", "eventsc", "eventscontrol", "eventscontrols", "eventsctrl", "events2", "eventc", "ce"}, {"n.c", "nc", "ncontrol", "ncontrols", "nctrl", "ntotal2", "totalcontrol", "n2", "totalcontrols"},
            {"mean.e", "meane", "meanexp", "meantreatment", "mean1", "meanexperimental"}, {"sd.e", "sde", "sdexp", "sdtreatment", "sd1", "sdexperimental"}, {"mean.c", "meanc", "meancontrol", "mean2"}, {"sd.c", "sdc", "sdcontrol", "sd2"},
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
    void loadExample() {
        try (InputStream in = getClass().getResourceAsStream("/examples/HR_meta.xlsx")) { File t = File.createTempFile("HR_meta", ".xlsx"); t.deleteOnExit(); java.nio.file.Files.copy(in, t.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); file = t;
            loading = true; sheets = TableReader.sheetNames(t); sheetBox.removeAllItems(); for (String s : sheets) sheetBox.addItem(s); loading = false; load(t, 0); typeBox.setSelectedIndex(3); fileLabel.setText("Example: HR_meta.xlsx"); } catch (Exception ex) { loading = false; error(ex); }
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
        s.method = (String) methodBox.getSelectedItem(); if (glmmModelBox.getSelectedItem() != null) s.modelGlmm = (String) glmmModelBox.getSelectedItem(); s.nAGQ = Integer.parseInt((String) nagqBox.getSelectedItem()); int mi = modelBox.getSelectedIndex(); s.random = mi != 1; s.common = mi != 0; s.methodTau = (String) tauBox.getSelectedItem();
        String tc = (String) tauCiBox.getSelectedItem(); s.methodTauCi = tc.equals("Default") ? null : tc.equals("None") ? "" : tc; s.methodRandomCi = (String) randCiBox.getSelectedItem(); s.adhocHakn = noneToEmpty((String) adhocBox.getSelectedItem());
        s.methodPredict = (String) predBox.getSelectedItem(); s.adhocHaknPi = noneToEmpty((String) adhocPiBox.getSelectedItem()); s.prediction = predChk.isSelected(); s.methodI2 = (String) i2Box.getSelectedItem(); s.methodCommonCi = (String) commonCiBox.getSelectedItem();
        double lv = dbl(levelF, 0.95); s.level = s.levelMa = s.levelHetstat = lv; double pl = dbl(predLevelF, 95) / 100; s.levelPredict = pl > 0.5 && pl < 1 ? pl : 0.95; s.backtransf = backChk.isSelected(); s.incr = dbl(incrF, 0.5); s.methodIncr = ((String) incrBox.getSelectedItem()).replace(" ", "");
        s.mhExact = mhExact.isSelected(); s.allstudies = allStudies.isSelected(); s.methodSmd = (String) smdBox.getSelectedItem(); s.pooledvar = pooledvar.isSelected(); String ci = (String) ciBox.getSelectedItem(); s.methodCi = ci.equals("Default") ? null : ci;
        s.nullEffect = nullF.getText().isBlank() ? Double.NaN : dbl(nullF, Double.NaN); s.pscale = dbl(pscaleF, 1); s.tauCommon = tauCommon.isSelected(); s.title = titleF.getText(); s.predictionSubgroup = sgPred.isSelected();
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
    static final class Item { final String title, text; final DoubleFunction<BufferedImage> render; BufferedImage img; List<Report.Tbl> tables = new ArrayList<>(); Item(String t, String x, DoubleFunction<BufferedImage> r) { title = t; text = x; render = r; } }
    static final class Out { final List<Item> items = new ArrayList<>(); Exception error; }
    /** Everything the background code needs, captured on the UI thread (Swing components are never read from worker threads). */
    static final class Snap {
        MetaSpec s, sSub; MetaData d, dSub; DataTable tbl; Plots.Opts o; List<String> nums, facs; String biasMethod, tfSide, tfEst, selDir; List<String> dataWarn = new ArrayList<>(); boolean pet, petPlot, sel, fail;
        boolean perm, permExact, cmp, orchard, cater, gosh, goshHl; int permIter, goshSub; long permSeed, goshSeed;
        boolean doi, doiApex, labbe, sg, forest, sgTestEffect, sgDetails, reg, regIntercept, bubble, regLine, loo, looForest, cum, cumForest, funnel, contour, labels, bias, biasPlot, tf, inf, baujat, radial;
    }
    private final ThreadLocal<List<Item>> sink = new ThreadLocal<>();
    private volatile int generation = 0; private java.util.concurrent.Future<?> current;
    private final java.util.concurrent.ExecutorService coordinator = java.util.concurrent.Executors.newCachedThreadPool(r -> { Thread t = new Thread(r, "metajava-analysis"); t.setDaemon(true); return t; });
    final JProgressBar busy = new JProgressBar(0, 100);
    private volatile double heavyFrac = 0; private final java.util.concurrent.atomic.AtomicInteger lastPct = new java.util.concurrent.atomic.AtomicInteger();
    /** Any thread: reports the overall progress (0..1) of run `gen` to the top strip and the small bar; never goes backwards within a run, and posts to the UI thread only when the percentage changes. */
    void progressTo(int gen, double frac) {
        final int p = (int) Math.max(0, Math.min(99, Math.floor(frac * 100)));
        if (lastPct.getAndUpdate(o -> Math.max(o, p)) >= p) return;
        SwingUtilities.invokeLater(() -> { if (gen == generation) { busy.setIndeterminate(false); busy.setValue(p); strip.setProgress(p); } });
    }
    /** UI thread: the run is over. */
    void runFinished() { busy.setIndeterminate(false); busy.setVisible(false); strip.stop(); }

    void runImpl(boolean auto) {
        if (table == null) { if (auto) return; JOptionPane.showMessageDialog(this, "Please load a data file first.", "No data", JOptionPane.WARNING_MESSAGE); return; }
        final Snap n = new Snap();
        try {
            n.tbl = table; n.s = buildSpec(); n.d = buildData(n.s, false); n.dataWarn = validateData(n.s); n.o = forestOpts();
            n.forest = forestChk.isSelected(); n.sg = sel(subgroupBox) != null && sgChk.isSelected(); n.sgTestEffect = sgTestEffect.isSelected(); n.sgDetails = sgDetails.isSelected();
            if (n.sg) { n.sSub = n.s.clone(); n.dSub = buildData(n.sSub, true); }
            n.reg = regChk.isSelected(); n.nums = chosen(covList); n.facs = chosen(facList); n.regIntercept = regIntercept.isSelected(); n.bubble = bubbleChk.isSelected(); n.regLine = regLine.isSelected();
            n.loo = looChk.isSelected(); n.looForest = looForest.isSelected(); n.cum = cumChk.isSelected(); n.cumForest = cumForest.isSelected();
            n.funnel = funnelChk.isSelected(); n.contour = contourChk.isSelected(); n.labels = labChk.isSelected(); n.bias = biasChk.isSelected(); n.biasPlot = biasPlotChk.isSelected(); n.biasMethod = (String) biasMethod.getSelectedItem();
            n.tf = tfChk.isSelected(); n.tfSide = (String) tfSide.getSelectedItem(); n.tfEst = (String) tfEst.getSelectedItem(); n.inf = infChk.isSelected(); n.baujat = baujatChk.isSelected(); n.radial = radialChk.isSelected(); n.doi = doiChk.isSelected(); n.doiApex = doiApexChk.isSelected(); n.labbe = labbeChk.isSelected();
            n.o.contour = n.funnel && n.contour; n.o.studyLabels = n.labels;
            n.perm = permChk.isSelected(); n.permExact = permExactChk.isSelected(); n.cmp = cmpChk.isSelected(); n.permIter = (Integer) permIterSp.getValue(); n.permSeed = lng(permSeedF, 1);
            n.orchard = orchardChk.isSelected(); n.cater = caterChk.isSelected(); n.gosh = goshChk.isSelected(); n.goshHl = goshHlChk.isSelected(); n.goshSub = (Integer) goshSubSp.getValue(); n.goshSeed = lng(goshSeedF, 1);
            n.pet = petChk.isSelected(); n.petPlot = petPlotChk.isSelected(); n.sel = selChk.isSelected(); n.selDir = (String) selDir.getSelectedItem(); n.fail = failChk.isSelected();
        } catch (Validate.Failed ex) { showValidation(ex); return;
        } catch (Exception ex) { if (auto) softError(ex); else error(ex); return; }
        final int gen = ++generation; lastPct.set(0); heavyFrac = 0; if (current != null) current.cancel(true);
        status.setText("Running\u2026"); busy.setValue(0); busy.setIndeterminate(true); busy.setVisible(true); strip.start();   // the small bar is indeterminate until the first step reports: a long first section (e.g. a GLMM leave-one-out) must not look frozen
        final int scrollPos = resScroll.getVerticalScrollBar().getValue();
        current = coordinator.submit(() -> Par.withCancel(() -> gen != generation, () -> analyse(n, gen, auto, scrollPos)));   // loops inside the analysis stop as soon as it is superseded
    }

    private void checkCurrent(int gen) { if (gen != generation) throw new java.util.concurrent.CancellationException("superseded"); }

    /** Runs on a background thread. Light sections go to the shared pool at once; heavy ones run in turn here, using the pool for their inner loops. */
    void analyse(Snap n, int gen, boolean auto, int scrollPos) {
        try {
            final MetaSpec s = n.s; final MetaResult r = Meta.run(s, n.d); checkCurrent(gen); progressTo(gen, 0.05);
            final List<Out> outs = new ArrayList<>(); final List<Runnable> light = new ArrayList<>(), heavy = new ArrayList<>(); final java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger();
            final List<Object[]> secs = new ArrayList<>();   // {body, heavy?}
            final Plots.Opts o = n.o;
            secs.add(new Object[]{(Runnable) () -> { addText("Meta-Analysis", Report.summary(r));
                if (n.forest) addPlot("Meta-Analysis Forest Plot", sc -> { Plots.Opts q = copy(o); q.scale = sc; return Plots.forest(r, q); }); }, false});
            if (n.sg) secs.add(new Object[]{(Runnable) () -> { MetaSpec s2 = n.sSub; MetaResult rs = Meta.run(s2, n.dSub);
                addText("Subgroup Analysis: " + s2.subgroupName, "Subgroup Analysis Summary\n\n" + Report.subgroupText(rs) + Report.details(rs));
                if (n.forest) addPlot("Subgroup Analysis Forest Plot: " + s2.subgroupName, sc -> { Plots.Opts q = copy(o); q.scale = sc; q.testEffectSubgroup = n.sgTestEffect; q.analysisDetails = n.sgDetails; return Plots.forest(rs, q); });
                if (n.orchard) addPlot("Orchard Plot: " + s2.subgroupName, sc -> { Plots.Opts q = copy(o); q.scale = sc; return ExtraPlots.orchard(rs, q); });
                if (n.cater) addPlot("Caterpillar Plot: " + s2.subgroupName, sc -> { Plots.Opts q = copy(o); q.scale = sc; return ExtraPlots.caterpillar(rs, q); }); }, true});
            if (n.reg) secs.add(new Object[]{(Runnable) () -> regression(r, n), false});
            if (n.loo) secs.add(new Object[]{(Runnable) () -> { Meta.leaveOneOut(r); addText("Leave-One-Out Analysis", Report.looText(r));
                if (n.looForest) addPlot("Leave-One-Out Analysis Forest Plot", sc -> { Plots.Opts q = copy(o); q.scale = sc; return Plots.forestSeries(r, r.loo, r.looLabels, q, "loo"); }); }, true});
            if (n.cum) secs.add(new Object[]{(Runnable) () -> { Meta.cumulative(r, null); addText("Cumulative Meta-Analysis", Report.cumText(r));
                if (n.cumForest) addPlot("Cumulative Meta-Analysis Forest Plot", sc -> { Plots.Opts q = copy(o); q.scale = sc; return Plots.forestSeries(r, r.cum, r.cumLabels, q, "cum"); }); }, true});
            if (n.funnel) secs.add(new Object[]{(Runnable) () -> addPlot("Funnel Plot", sc -> { Plots.Opts q = copy(o); q.scale = sc; q.contour = n.contour; q.studyLabels = n.labels; return Plots.funnel(r, q, null); }), false});
            if (n.bias) secs.add(new Object[]{(Runnable) () -> { Bias.Result b = Bias.run(r, n.biasMethod.equals("Default") ? null : n.biasMethod, false); addText("Test for Funnel Plot Asymmetry", Report.biasText(b, r));
                if (n.biasPlot && b.error == null && b.plotX != null) addPlot("Plot (Test for Funnel Plot Asymmetry)", sc -> { Plots.Opts q = copy(o); q.scale = sc; q.studyLabels = n.labels; return Plots.biasPlot(b, r, q); }); }, false});
            if (n.tf) secs.add(new Object[]{(Runnable) () -> { TrimFill tf = TrimFill.run(r, n.tfSide.equals("Auto") ? null : n.tfSide.equals("Left"), true, n.tfEst, s.common, s.random);
                if (tf == null) addText("Trim and Fill", "Too few studies for trim-and-fill."); else { addText("Trim and Fill", Report.trimfillText(tf, r)); addPlot("Trim-and-Fill Funnel Plot", sc -> { Plots.Opts q = copy(o); q.scale = sc; return Plots.funnel(r, q, tf); }); } }, false});
            if (n.doi) secs.add(new Object[]{(Runnable) () -> { Lfk l = Lfk.compute(r.TE, r.seTE, r.studlab); addText("Doi Plot and LFK Index", Report.lfkText(l, r));
                if (l.lfk == l.lfk) addPlot("Doi Plot", sc -> { Plots.Opts q = copy(o); q.scale = sc; q.studyLabels = n.labels; q.apexLine = n.doiApex; return Plots.doi(l, r, q); }); }, false});
            if (n.pet) secs.add(new Object[]{(Runnable) () -> { PubBias.PetPeese pp = PubBias.petPeese(r); addText("PET-PEESE", BiasReport.petPeeseText(pp, r));
                if (n.petPlot && pp.error == null) addPlot("PET-PEESE Plot", sc -> { Plots.Opts q = copy(o); q.scale = sc; q.studyLabels = n.labels; return Plots.petPeese(pp, r, q); }); }, false});
            if (n.sel) secs.add(new Object[]{(Runnable) () -> addText("Selection Model", BiasReport.selectionText(PubBias.selection(r, n.selDir), r)), false});
            if (n.fail) secs.add(new Object[]{(Runnable) () -> addText("Fail-Safe N", BiasReport.failSafeText(PubBias.failSafe(r), r)), false});
            if (n.inf) secs.add(new Object[]{(Runnable) () -> influence(r, s, n), true});
            if (n.baujat) secs.add(new Object[]{(Runnable) () -> addPlot("Baujat Plot", sc -> { Plots.Opts q = copy(o); q.scale = sc; return Plots.baujat(r, q); }), false});
            if (n.radial) secs.add(new Object[]{(Runnable) () -> addPlot("Radial Plot", sc -> { Plots.Opts q = copy(o); q.scale = sc; return Plots.radial(r, q); }), false});
            if (n.labbe) secs.add(new Object[]{(Runnable) () -> { if (!s.type.equals("bin")) { addText("L\u2019Abb\u00E9 Plot", "The L\u2019Abb\u00E9 plot requires binary outcome data (analysis type: metabin)."); return; }
                if (!java.util.Arrays.asList("RR", "OR", "RD", "ASD").contains(s.sm)) addText("L\u2019Abb\u00E9 Plot", "Note: the line of the pooled effect is only drawn for RR, OR, RD and ASD;\nthe study event rates are shown without it.");
                addPlot("L\u2019Abb\u00E9 Plot", sc -> { Plots.Opts q = copy(o); q.scale = sc; q.studyLabels = n.labels; return Plots.labbe(r, q); }); }, false});
            if (n.gosh) secs.add(new Object[]{(Runnable) () -> { Gosh gs = Gosh.run(r, n.goshSub, n.goshSeed); addText("GOSH Analysis", RegReport.goshText(gs, r));
                addPlot("GOSH Plot", sc -> { Plots.Opts q = copy(o); q.scale = sc; return ExtraPlots.gosh(gs, r, q, n.goshHl); }); }, true});
            final int total = secs.size();
            for (Object[] sc : secs) {
                final Out out = new Out(); outs.add(out); final Runnable body = (Runnable) sc[0];
                final Runnable task = () -> {
                    if (gen != generation) return;
                    sink.set(out.items); Report.startCapture();
                    try { body.run(); for (Item it : out.items) if (it.render != null) it.img = it.render.apply(1.0); }   // plots are rendered here, not on the UI thread
                    catch (Exception ex) { out.error = ex; }
                    finally { Report.stopCapture(); sink.remove(); heavyFrac = 0; final int dn = done.incrementAndGet(); progressTo(gen, 0.05 + 0.95 * dn / total); }
                };
                (((boolean) sc[1]) ? heavy : light).add(task);
            }
            final List<java.util.concurrent.Future<?>> fs = new ArrayList<>();
            for (Runnable t : light) fs.add(Par.submit(() -> { t.run(); return null; }));
            for (Runnable t : heavy) { checkCurrent(gen); heavyFrac = 0; Par.withProgress(fr -> { if (fr > heavyFrac) { heavyFrac = fr; progressTo(gen, 0.05 + 0.95 * (done.get() + Math.min(1.0, fr)) / total); } }, t); }
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
        for (Out o : outs) { for (Item it : o.items) { shown.add(it); if (it.render == null) showText(it.title, it.text); else showPlot(it.title, it.render, it.img); } if (o.error != null && first == null) first = o.error; }
        for (String w : r.warnings) { shown.add(new Item("Warning", w, null)); showText("Warning", w); }
        strip.setProgress(100); runFinished();
        if (first != null) { if (auto) { softError(first); return; } error(first); }
        else status.setText("Done \u2013 k = " + r.overall.k + " studies");
        results.add(Box.createVerticalGlue()); results.revalidate(); results.repaint(); final boolean fa = auto; SwingUtilities.invokeLater(() -> resScroll.getVerticalScrollBar().setValue(fa ? scrollPos : 0));
    }

    void softError(Exception ex) {
        ex.printStackTrace(); results.removeAll(); JLabel l = new JLabel("<html><div style='width:520px;color:#667085'><b>Waiting for a complete setup:</b> " + String.valueOf(ex.getMessage()).replace("<", "&lt;") + "</div></html>"); l.setBorder(new EmptyBorder(20, 10, 10, 10)); results.add(l); status.setText("Incomplete setup"); results.revalidate(); results.repaint();
    }

    Plots.Opts forestOpts() { Plots.Opts o = new Plots.Opts(); o.sortBy = (String) sortBox.getSelectedItem(); o.ascending = sortDir.getSelectedIndex() == 0; o.analysisDetails = fDetails.isSelected(); o.testOverall = fOverallTest.isSelected(); o.hetStats = fHet.isSelected(); o.ciI2 = fCiI2.isSelected(); o.ciTau = fCiTau.isSelected();
        o.xLeft = xLeft.getText(); o.xRight = xRight.getText(); o.digits = (Integer) digitsSp.getValue(); o.spacing = (Double) spacingSp.getValue(); o.studyLabels = labChk.isSelected(); o.regLine = regLine.isSelected(); return o; }
    static Plots.Opts copy(Plots.Opts a) { Plots.Opts o = new Plots.Opts(); for (java.lang.reflect.Field f : Plots.Opts.class.getFields()) try { f.set(o, f.get(a)); } catch (Exception e) { } return o; }

    void regression(MetaResult r, Snap n) {
        List<String> nums = n.nums, facs = n.facs; if (nums.isEmpty() && facs.isEmpty()) { addText("Meta-Regression", "Select at least one covariate or factor."); return; }
        Map<String, double[]> nd = new HashMap<>(); for (String c : nums) nd.put(c, n.tbl.num(c)); Map<String, String[]> fd = new HashMap<>(); for (String f : facs) fd.put(f, n.tbl.strings(f));
        Reg g = Reg.run(r, nums, nd, facs, fd, n.regIntercept); List<String> terms = new ArrayList<>(nums); terms.addAll(facs);
        addText("Meta-Regression - Model 1: ~ " + String.join(" + ", terms), Report.regText(g, r, 1, ""));
        if (n.perm) { try { Perm t = Perm.run(g, n.permIter, n.permExact, n.permSeed); addText("Permutation Test (permutest) - Model 1", RegReport.permText(g, t, r)); }
            catch (IllegalArgumentException ex) { addText("Permutation Test (permutest) - Model 1", ex.getMessage()); } }
        if (n.cmp) { try { ModelCompare mc = ModelCompare.run(r, nums, nd, facs, fd, n.regIntercept); addText("Model Comparison", RegReport.compareText(mc, r)); }
            catch (IllegalArgumentException ex) { addText("Model Comparison", ex.getMessage()); } }
        if (n.bubble && g.x1 != null) addPlot("Bubble Plot", sc -> { Plots.Opts q = copy(n.o); q.scale = sc; q.studyLabels = n.labels; q.regLine = n.regLine; return Plots.bubble(g, q, r); });
    }

    void influence(MetaResult r, MetaSpec s, Snap n) {
        List<String> nums = n.nums, facs = n.facs;
        List<Integer> ok = new ArrayList<>(); for (int i = 0; i < r.TE.length; i++) if (!Double.isNaN(r.TE[i]) && r.seTE[i] > 0) ok.add(i);
        int k = ok.size(); double[] y = new double[k], v = new double[k]; String[] lab = new String[k]; for (int j = 0; j < k; j++) { int i = ok.get(j); y[j] = r.TE[i]; v[j] = r.seTE[i] * r.seTE[i]; lab[j] = r.studlab[i]; }
        double[][] X = null; String[] names = null;
        if (n.reg && !(nums.isEmpty() && facs.isEmpty())) { Map<String, double[]> nd = new HashMap<>(); for (String c : nums) nd.put(c, n.tbl.num(c)); Map<String, String[]> fd = new HashMap<>(); for (String f : facs) fd.put(f, n.tbl.strings(f));
            Reg g = Reg.run(r, nums, nd, facs, fd, n.regIntercept); if (g.k == k) { X = g.fit.X; names = g.coefNames; } }
        boolean hk = X != null && s.methodRandomCi.equals("HK");   // regression model with test="knha" (adhoc when the se-floor is on)
        Influence in = Influence.run(y, v, X, s.methodTau, lab, names == null ? new String[]{"intrcpt"} : names, hk, hk && s.adhocHakn.equals("se"));
        addText("Outlier and Influence Diagnostics", Report.influenceText(in));
        addPlot("Influence Diagnostics Plots", sc -> { Plots.Opts q = new Plots.Opts(); q.scale = sc; return Plots.influence(in, q); });
    }

    /** Called from analysis threads: collects the item in the current section. */
    void addText(String title, String text) { Item it = new Item(title, text, null); it.tables = Report.drain(); sink.get().add(it); }
    void addPlot(String title, DoubleFunction<BufferedImage> render) { sink.get().add(new Item(title, null, render)); }

    /** UI thread only. */
    void showText(String title, String text) {
        lastText += "== " + title + " ==\n" + text + "\n\n";
        JLabel t = new JLabel(title); t.setFont(new Font("SansSerif", Font.BOLD, 18)); t.setForeground(Ui.PRIMARY); t.setAlignmentX(LEFT_ALIGNMENT); t.setBorder(new EmptyBorder(14, 0, 6, 0)); results.add(t);
        JTextArea a = new JTextArea(text); a.setEditable(false); a.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12)); a.setBackground(Color.WHITE); a.setAlignmentX(LEFT_ALIGNMENT); a.setBorder(new EmptyBorder(0, 0, 0, 0)); a.putClientProperty("raw", text); textAreas.add(a); a.setText(wrapped(a, text)); results.add(a);
    }
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
