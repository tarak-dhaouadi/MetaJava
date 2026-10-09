package org.metajava.gui;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/** Modern look helpers. */
final class Ui {
    private Ui() {}
    static final Color PRIMARY = new Color(0x1F4E8C), PRIMARY_DARK = new Color(0x163A69), BG = new Color(0xF3F5F9), CARD = Color.WHITE, BORDER = new Color(0xD5DBE5), TEXT = new Color(0x1E2633), MUTED = new Color(0x667085), ACCENT = new Color(0x2E7D32);
    static final Font BASE = new Font("SansSerif", Font.PLAIN, 12), BOLD = new Font("SansSerif", Font.BOLD, 12);

    static void install() {
        try { for (UIManager.LookAndFeelInfo i : UIManager.getInstalledLookAndFeels()) if (i.getName().equals("Nimbus")) { UIManager.setLookAndFeel(i.getClassName()); break; } } catch (Exception e) { }
        UIManager.put("nimbusBase", PRIMARY); UIManager.put("nimbusBlueGrey", new Color(0xE3E8F0)); UIManager.put("control", BG); UIManager.put("nimbusFocus", PRIMARY);
        UIManager.put("defaultFont", BASE);
    }

    static JButton button(String t, boolean primary) {
        JButton b = new JButton(t); b.setFont(BOLD); b.setFocusPainted(false); b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (primary) { b.setBackground(PRIMARY); b.setForeground(Color.WHITE); b.setOpaque(true); b.setBorder(new EmptyBorder(8, 18, 8, 18)); }
        else { b.setBackground(Color.WHITE); b.setForeground(PRIMARY); b.setOpaque(true); b.setBorder(new CompoundBorder(new LineBorder(PRIMARY, 1, true), new EmptyBorder(6, 14, 6, 14))); }
        b.setContentAreaFilled(false); b.setOpaque(true);
        return b;
    }

    static JLabel label(String t) { JLabel l = new JLabel(t); l.setFont(BASE); l.setForeground(TEXT); return l; }
    static JLabel muted(String t) { JLabel l = new JLabel(t); l.setFont(BASE.deriveFont(11f)); l.setForeground(MUTED); return l; }

    /** Collapsible card section. */
    static final class Section extends JPanel {
        final JPanel body = new JPanel(new GridBagLayout()); int row = 0; private boolean open;
        private final JLabel head;
        Section(String title, boolean open) {
            setLayout(new BorderLayout()); setBackground(CARD); setBorder(new LineBorder(BORDER, 1, true)); this.open = open;
            head = new JLabel((open ? "\u25BE  " : "\u25B8  ") + title); head.setFont(BOLD.deriveFont(13f)); head.setForeground(PRIMARY_DARK); head.setBorder(new EmptyBorder(9, 12, 9, 12)); head.setOpaque(true); head.setBackground(new Color(0xEAF0F8));
            head.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            head.addMouseListener(new MouseAdapter() { public void mouseClicked(MouseEvent e) { toggle(title); } });
            body.setBackground(CARD); body.setBorder(new EmptyBorder(8, 12, 10, 12)); body.setVisible(open);
            add(head, BorderLayout.NORTH); add(body, BorderLayout.CENTER);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE)); setAlignmentX(LEFT_ALIGNMENT);
        }
        void toggle(String title) { open = !open; body.setVisible(open); head.setText((open ? "\u25BE  " : "\u25B8  ") + title); revalidate(); repaint(); }
        void row(String label, JComponent c) {
            GridBagConstraints g = new GridBagConstraints(); g.gridy = row++; g.insets = new Insets(3, 0, 3, 8); g.anchor = GridBagConstraints.WEST;
            if (label != null) { g.gridx = 0; g.weightx = 0; body.add(label(label), g); g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL; body.add(c, g); }
            else { g.gridx = 0; g.gridwidth = 2; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL; body.add(c, g); }
        }
    }

    static JCheckBox check(String t, boolean sel) { JCheckBox c = new JCheckBox(t, sel); c.setOpaque(false); c.setFont(BASE); return c; }
    static JComboBox<String> combo(String... items) { JComboBox<String> c = new JComboBox<>(items); c.setFont(BASE); return c; }
}
