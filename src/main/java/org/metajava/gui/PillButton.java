package org.metajava.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;

/** White rounded button with an icon, used on the blue header. */
final class PillButton extends JButton {
    private boolean hover; private Color base = Color.WHITE, over = new Color(0xEAF1FB), down = new Color(0xD3DEEE);
    /** Same button in another colour (e.g. the green run button). */
    PillButton colors(Color bg, Color fg) { base = bg; over = bg.brighter(); down = bg.darker(); setForeground(fg); return this; }
    PillButton(String text, Icon icon, String tip) {
        super(text, icon); setFont(Ui.BOLD.deriveFont(13f)); setForeground(Ui.PRIMARY_DARK); setFocusPainted(false); setContentAreaFilled(false); setBorderPainted(false); setOpaque(false);
        setIconTextGap(8); setBorder(new EmptyBorder(9, 16, 9, 18)); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); setToolTipText(tip);
        addMouseListener(new MouseAdapter() { public void mouseEntered(MouseEvent e) { hover = true; repaint(); } public void mouseExited(MouseEvent e) { hover = false; repaint(); } });
    }
    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(getModel().isPressed() ? down : hover ? over : base); g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12); g.dispose();
        super.paintComponent(g0);
    }
}
