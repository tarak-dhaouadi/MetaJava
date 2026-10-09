package org.metajava.gui;

import org.metajava.core.*;
import org.metajava.io.TableReader;
import org.metajava.plot.Plots;

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
        tfChk = Ui.check("Trim and fill", false), infChk = Ui.check("Outlier & influence diagnostics (metafor)", false), baujatChk = Ui.check("Baujat plot", false), radialChk = Ui.check("Radial (Galbraith) plot", false);
    JComboBox<String> biasMethod = Ui.combo("Default", "Egger", "Begg", "Thompson", "Harbord", "Peters", "Macaskill", "Deeks", "Pustejovsky", "Schwarzer");
    JComboBox<String> tfSide = Ui.combo("Auto", "Left", "Right"), tfEst = Ui.combo("L0", "R0");
    JSpinner dpiSp = new JSpinner(new SpinnerNumberModel(300, 72, 1200, 100));
    final List<Object[]> plots = new ArrayList<>(); // {title, DoubleFunction<BufferedImage>}
    final JLabel status = Ui.muted("Ready");
    String lastText = "";
    boolean suppress = false; final javax.swing.Timer autoTimer = new javax.swing.Timer(350, e -> runImpl(true));

    public MainWindow() {
        super("MetaJava \u2013 Meta-analysis (meta / metafor)");
        setDefaultCloseOperation(EXIT_ON_CLOSE); setSize(1500, 900); setLocationRelativeTo(null);
        getContentPane().setBackground(Ui.BG); setLayout(new BorderLayout());
        add(header(), BorderLayout.NORTH);
        JPanel left = leftPanel(); JScrollPane ls = new JScrollPane(left); ls.setBorder(null); ls.getVerticalScrollBar().setUnitIncrement(16); ls.setPreferredSize(new Dimension(470, 800));
        results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS)); results.setBackground(Color.WHITE); results.setBorder(new EmptyBorder(14, 18, 14, 18));
        resScroll = new JScrollPane(results); resScroll.getVerticalScrollBar().setUnitIncrement(24); resScroll.setBorder(new LineBorder(Ui.BORDER));
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
        JPanel l = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); l.setOpaque(false); l.add(t); l.add(s); h.add(l, BorderLayout.WEST);
        JButton run = Ui.button("\u25B6  Run analysis", true); run.setBackground(new Color(0x2E9E5B)); run.addActionListener(e -> run());
        JLabel cl = new JLabel("CPU threads"); cl.setForeground(new Color(0xC9D8EE)); cl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        JSpinner th = new JSpinner(new SpinnerNumberModel(Par.threads(), 1, Par.maxThreads(), 1)); th.setToolTipText("Worker threads for parallel analyses (this machine: " + Par.maxThreads() + " cores)");
        th.addChangeListener(e -> { int v = (Integer) th.getValue(); Par.setThreads(v); try { PREFS.putInt("threads", v); } catch (Exception ex) { } });
        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)); east.setOpaque(false); east.add(cl); east.add(th); east.add(run); h.add(east, BorderLayout.EAST);
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
        Ui.Section sg = new Ui.Section("5. Subgroup analysis", false); sg.row("Subgroup variable", subgroupBox); sg.row(null, sgChk); sg.row(null, tauCommon); sg.row(null, sgPred); sg.row(null, sgTestEffect); sg.row(null, sgDetails); p.add(sg); p.add(Box.createVerticalStrut(8));
        Ui.Section rg = new Ui.Section("6. Meta-regression", false); rg.row(null, regChk); rg.row("Covariates (numeric)", new JScrollPane(covList)); rg.row("Factors (categorical)", new JScrollPane(facList)); rg.row(null, regIntercept); rg.row(null, bubbleChk); rg.row(null, regLine); p.add(rg); p.add(Box.createVerticalStrut(8));
        Ui.Section lo = new Ui.Section("7. Leave-one-out analysis", false); lo.row(null, looChk); lo.row(null, looForest); p.add(lo); p.add(Box.createVerticalStrut(8));
        Ui.Section cu = new Ui.Section("8. Cumulative meta-analysis", false); cu.row(null, cumChk); cu.row(null, cumForest); p.add(cu); p.add(Box.createVerticalStrut(8));
        Ui.Section pb = new Ui.Section("9. Publication bias", false); pb.row(null, funnelChk); pb.row(null, contourChk); pb.row(null, labChk); pb.row(null, biasChk); pb.row("Test method", biasMethod); pb.row(null, biasPlotChk);
        pb.row(null, tfChk); pb.row("Trim-fill side", tfSide); pb.row("Trim-fill estimator", tfEst); p.add(pb); p.add(Box.createVerticalStrut(8));
        Ui.Section dg = new Ui.Section("10. Diagnostics", false); dg.row(null, infChk); dg.row(null, baujatChk); dg.row(null, radialChk); p.add(dg); p.add(Box.createVerticalStrut(8));
        Ui.Section ou = new Ui.Section("11. Export", false); JButton sa = Ui.button("Save all plots\u2026", false), st = Ui.button("Save results (.txt)\u2026", false); sa.addActionListener(e -> savePlots()); st.addActionListener(e -> saveText());
        ou.row("Plot resolution (dpi)", dpiSp); ou.row(null, sa); ou.row(null, st); p.add(ou);
        p.add(Box.createVerticalGlue());
        modelBox.setSelectedIndex(0); return p;
    }

    boolean loading = false;
    void schedule() { if (!suppress && table != null) autoTimer.restart(); }
    void hook(Component c) {
        if (c == dpiSp || (c instanceof JComponent && c.getParent() == dpiSp)) return;
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
            DefaultTableModel m = new DefaultTableModel(table.names.toArray(), 0); for (int i = 0; i < Math.min(table.nrow, 200); i++) { Object[] row = new Object[table.names.size()]; for (int j = 0; j < row.length; j++) row[j] = table.cols.get(j)[i]; m.addRow(row); }
            preview.setModel(m); preview.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); refreshColumns(); status.setText("Loaded " + f.getName());
        } catch (Exception ex) { error(ex); }
    }
    void error(Exception ex) { ex.printStackTrace(); JOptionPane.showMessageDialog(this, String.valueOf(ex.getMessage() == null ? ex : ex.getMessage()), "Error", JOptionPane.ERROR_MESSAGE); status.setText("Error: " + ex.getMessage()); }

    String sel(JComboBox<String> c) { Object o = c.getSelectedItem(); return o == null || o.equals("(none)") ? null : o.toString(); }
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

    // ===== analysis pipeline: computed off the UI thread; independent sections run concurrently =====
    static final class Item { final String title, text; final DoubleFunction<BufferedImage> render; BufferedImage img; Item(String t, String x, DoubleFunction<BufferedImage> r) { title = t; text = x; render = r; } }
    static final class Out { final List<Item> items = new ArrayList<>(); Exception error; }
    /** Everything the background code needs, captured on the UI thread (Swing components are never read from worker threads). */
    static final class Snap {
        MetaSpec s, sSub; MetaData d, dSub; DataTable tbl; Plots.Opts o; List<String> nums, facs; String biasMethod, tfSide, tfEst;
        boolean sg, forest, sgTestEffect, sgDetails, reg, regIntercept, bubble, regLine, loo, looForest, cum, cumForest, funnel, contour, labels, bias, biasPlot, tf, inf, baujat, radial;
    }
    private final ThreadLocal<List<Item>> sink = new ThreadLocal<>();
    private volatile int generation = 0; private java.util.concurrent.Future<?> current;
    private final java.util.concurrent.ExecutorService coordinator = java.util.concurrent.Executors.newCachedThreadPool(r -> { Thread t = new Thread(r, "metajava-analysis"); t.setDaemon(true); return t; });
    final JProgressBar busy = new JProgressBar(0, 100);

    void runImpl(boolean auto) {
        if (table == null) { if (auto) return; JOptionPane.showMessageDialog(this, "Please load a data file first.", "No data", JOptionPane.WARNING_MESSAGE); return; }
        final Snap n = new Snap();
        try {
            n.tbl = table; n.s = buildSpec(); n.d = buildData(n.s, false); n.o = forestOpts();
            n.forest = forestChk.isSelected(); n.sg = sel(subgroupBox) != null && sgChk.isSelected(); n.sgTestEffect = sgTestEffect.isSelected(); n.sgDetails = sgDetails.isSelected();
            if (n.sg) { n.sSub = n.s.clone(); n.dSub = buildData(n.sSub, true); }
            n.reg = regChk.isSelected(); n.nums = chosen(covList); n.facs = chosen(facList); n.regIntercept = regIntercept.isSelected(); n.bubble = bubbleChk.isSelected(); n.regLine = regLine.isSelected();
            n.loo = looChk.isSelected(); n.looForest = looForest.isSelected(); n.cum = cumChk.isSelected(); n.cumForest = cumForest.isSelected();
            n.funnel = funnelChk.isSelected(); n.contour = contourChk.isSelected(); n.labels = labChk.isSelected(); n.bias = biasChk.isSelected(); n.biasPlot = biasPlotChk.isSelected(); n.biasMethod = (String) biasMethod.getSelectedItem();
            n.tf = tfChk.isSelected(); n.tfSide = (String) tfSide.getSelectedItem(); n.tfEst = (String) tfEst.getSelectedItem(); n.inf = infChk.isSelected(); n.baujat = baujatChk.isSelected(); n.radial = radialChk.isSelected();
            n.o.contour = n.funnel && n.contour; n.o.studyLabels = n.labels;
        } catch (Exception ex) { if (auto) softError(ex); else error(ex); return; }
        final int gen = ++generation; if (current != null) current.cancel(true);
        status.setText("Running\u2026"); busy.setValue(0); busy.setIndeterminate(true); busy.setVisible(true);   // indeterminate until the first section completes: a long first section (e.g. a GLMM leave-one-out) must not look frozen
        final int scrollPos = resScroll.getVerticalScrollBar().getValue();
        current = coordinator.submit(() -> Par.withCancel(() -> gen != generation, () -> analyse(n, gen, auto, scrollPos)));   // loops inside the analysis stop as soon as it is superseded
    }

    private void checkCurrent(int gen) { if (gen != generation) throw new java.util.concurrent.CancellationException("superseded"); }

    /** Runs on a background thread. Light sections go to the shared pool at once; heavy ones run in turn here, using the pool for their inner loops. */
    void analyse(Snap n, int gen, boolean auto, int scrollPos) {
        try {
            final MetaSpec s = n.s; final MetaResult r = Meta.run(s, n.d); checkCurrent(gen);
            final List<Out> outs = new ArrayList<>(); final List<Runnable> light = new ArrayList<>(), heavy = new ArrayList<>(); final java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger();
            final List<Object[]> secs = new ArrayList<>();   // {body, heavy?}
            final Plots.Opts o = n.o;
            secs.add(new Object[]{(Runnable) () -> { addText("Meta-Analysis", Report.summary(r));
                if (n.forest) addPlot("Meta-Analysis Forest Plot", sc -> { Plots.Opts q = copy(o); q.scale = sc; return Plots.forest(r, q); }); }, false});
            if (n.sg) secs.add(new Object[]{(Runnable) () -> { MetaSpec s2 = n.sSub; MetaResult rs = Meta.run(s2, n.dSub);
                addText("Subgroup Analysis: " + s2.subgroupName, "Subgroup Analysis Summary\n\n" + Report.subgroupText(rs) + Report.details(rs));
                if (n.forest) addPlot("Subgroup Analysis Forest Plot: " + s2.subgroupName, sc -> { Plots.Opts q = copy(o); q.scale = sc; q.testEffectSubgroup = n.sgTestEffect; q.analysisDetails = n.sgDetails; return Plots.forest(rs, q); }); }, true});
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
            if (n.inf) secs.add(new Object[]{(Runnable) () -> influence(r, s, n), true});
            if (n.baujat) secs.add(new Object[]{(Runnable) () -> addPlot("Baujat Plot", sc -> { Plots.Opts q = copy(o); q.scale = sc; return Plots.baujat(r, q); }), false});
            if (n.radial) secs.add(new Object[]{(Runnable) () -> addPlot("Radial Plot", sc -> { Plots.Opts q = copy(o); q.scale = sc; return Plots.radial(r, q); }), false});
            final int total = secs.size();
            for (Object[] sc : secs) {
                final Out out = new Out(); outs.add(out); final Runnable body = (Runnable) sc[0];
                final Runnable task = () -> {
                    if (gen != generation) return;
                    sink.set(out.items);
                    try { body.run(); for (Item it : out.items) if (it.render != null) it.img = it.render.apply(1.0); }   // plots are rendered here, not on the UI thread
                    catch (Exception ex) { out.error = ex; }
                    finally { sink.remove(); final int dn = done.incrementAndGet(); SwingUtilities.invokeLater(() -> { if (gen == generation) { busy.setIndeterminate(false); busy.setValue(100 * dn / total); } }); }
                };
                (((boolean) sc[1]) ? heavy : light).add(task);
            }
            final List<java.util.concurrent.Future<?>> fs = new ArrayList<>();
            for (Runnable t : light) fs.add(Par.submit(() -> { t.run(); return null; }));
            for (Runnable t : heavy) { checkCurrent(gen); t.run(); }
            for (java.util.concurrent.Future<?> f : fs) f.get();
            checkCurrent(gen);
            SwingUtilities.invokeLater(() -> display(n, outs, r, gen, auto, scrollPos));
        } catch (java.util.concurrent.CancellationException | InterruptedException ex) { /* superseded by a newer run */
        } catch (Throwable ex) {
            final Exception e = ex instanceof Exception ? (Exception) ex : new RuntimeException(ex);
            SwingUtilities.invokeLater(() -> { if (gen != generation) return; busy.setIndeterminate(false); busy.setVisible(false); if (auto) softError(e); else error(e); });
        }
    }

    /** UI thread: builds the Swing components from the finished sections (images are already rendered). */
    void display(Snap n, List<Out> outs, MetaResult r, int gen, boolean auto, int scrollPos) {
        if (gen != generation) return;
        results.removeAll(); plots.clear(); lastText = ""; Exception first = null;
        for (Out o : outs) { for (Item it : o.items) { if (it.render == null) showText(it.title, it.text); else showPlot(it.title, it.render, it.img); } if (o.error != null && first == null) first = o.error; }
        for (String w : r.warnings) showText("Warning", w);
        busy.setIndeterminate(false); busy.setVisible(false);
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
    void addText(String title, String text) { sink.get().add(new Item(title, text, null)); }
    void addPlot(String title, DoubleFunction<BufferedImage> render) { sink.get().add(new Item(title, null, render)); }

    /** UI thread only. */
    void showText(String title, String text) {
        lastText += "== " + title + " ==\n" + text + "\n\n";
        JLabel t = new JLabel(title); t.setFont(new Font("SansSerif", Font.BOLD, 18)); t.setForeground(Ui.PRIMARY); t.setAlignmentX(LEFT_ALIGNMENT); t.setBorder(new EmptyBorder(14, 0, 6, 0)); results.add(t);
        JTextArea a = new JTextArea(text); a.setEditable(false); a.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12)); a.setBackground(Color.WHITE); a.setAlignmentX(LEFT_ALIGNMENT); a.setBorder(new EmptyBorder(0, 0, 0, 0)); results.add(a);
    }
    void showPlot(String title, DoubleFunction<BufferedImage> render, BufferedImage img) {
        plots.add(new Object[]{title, render});
        JLabel t = new JLabel(title); t.setFont(new Font("SansSerif", Font.BOLD, 18)); t.setForeground(Ui.PRIMARY); t.setAlignmentX(LEFT_ALIGNMENT); t.setBorder(new EmptyBorder(16, 0, 6, 0)); results.add(t);
        JLabel il = new JLabel(new ImageIcon(img)); il.setAlignmentX(LEFT_ALIGNMENT); results.add(il);
        JButton sv = Ui.button("Save this plot\u2026", false); sv.setAlignmentX(LEFT_ALIGNMENT); sv.addActionListener(e -> savePlot(title, render)); results.add(Box.createVerticalStrut(4)); results.add(sv);
    }

    double scaleDpi() { return ((Integer) dpiSp.getValue()) / 96.0; }
    void savePlot(String title, DoubleFunction<BufferedImage> render) {
        JFileChooser fc = new JFileChooser(); fc.setSelectedFile(new File(title.replaceAll("[^A-Za-z0-9]+", "_") + ".png")); if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        final File f = fc.getSelectedFile(); final double sc = scaleDpi(); status.setText("Saving\u2026"); busy.setIndeterminate(true); busy.setVisible(true);
        new Thread(() -> {   // high-dpi rendering happens off the UI thread
            try { ImageIO.write(render.apply(sc), "png", f); SwingUtilities.invokeLater(() -> { busy.setIndeterminate(false); busy.setVisible(false); status.setText("Saved " + f.getName()); }); }
            catch (Exception ex) { SwingUtilities.invokeLater(() -> { busy.setIndeterminate(false); busy.setVisible(false); error(ex); }); }
        }, "metajava-save").start();
    }
    @SuppressWarnings("unchecked")
    void savePlots() {
        if (plots.isEmpty()) { JOptionPane.showMessageDialog(this, "Run an analysis first."); return; }
        JFileChooser fc = new JFileChooser(); fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        final File dir = fc.getSelectedFile(); final List<Object[]> ps = new ArrayList<>(plots); final double sc = scaleDpi(); status.setText("Saving plots\u2026"); busy.setIndeterminate(true); busy.setVisible(true);
        new Thread(() -> {
            try { Par.forRange(ps.size(), i -> { try { ImageIO.write(((DoubleFunction<BufferedImage>) ps.get(i)[1]).apply(sc), "png", new File(dir, String.format("%02d_%s.png", i + 1, ((String) ps.get(i)[0]).replaceAll("[^A-Za-z0-9]+", "_")))); } catch (IOException e) { throw new UncheckedIOException(e); } });
                SwingUtilities.invokeLater(() -> { busy.setIndeterminate(false); busy.setVisible(false); status.setText("Saved " + ps.size() + " plots"); }); }
            catch (Exception ex) { SwingUtilities.invokeLater(() -> { busy.setIndeterminate(false); busy.setVisible(false); error(ex); }); }
        }, "metajava-save").start();
    }
    void saveText() {
        JFileChooser fc = new JFileChooser(); fc.setSelectedFile(new File("meta_results.txt")); if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try (Writer w = new OutputStreamWriter(new FileOutputStream(fc.getSelectedFile()), "UTF-8")) { w.write(lastText); status.setText("Saved results"); } catch (Exception ex) { error(ex); }
    }

    static final java.util.prefs.Preferences PREFS = java.util.prefs.Preferences.userNodeForPackage(MainWindow.class);
    public static void main(String[] args) {
        if (System.getProperty("metajava.threads") == null) { try { Par.setThreads(PREFS.getInt("threads", 4)); } catch (Exception e) { } }   // -Dmetajava.threads=N wins over the remembered setting
        Ui.install(); SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true)); }
}
