package org.metajava.gui;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;

/**
 * Progress display shown directly beneath the blue header: a thin bar spanning the window width (lighter blue than the header),
 * and below it a line "Running analysis..." with a spinner and the percentage. The line only appears when a run lasts longer than a
 * short delay, so quick automatic updates do not make the layout jump; the bar itself always takes the same 4 px.
 */
final class ProgressStrip extends JPanel {
    static final Color BAR = new Color(0x4DA3F0), TRACK = new Color(0xDCE7F5), PCT = new Color(0x9A5B2E), SPIN = new Color(0x1F6F54);
    static final String TEXT = "Running analysis...";
    static final int BAR_H = 4, SHOW_DELAY_MS = 250;

    private volatile int pct = 0; private boolean running = false;
    private final JComponent bar = new JComponent() {
        @Override protected void paintComponent(Graphics g) {
            g.setColor(running ? TRACK : Ui.BG); g.fillRect(0, 0, getWidth(), getHeight());
            if (running && pct > 0) { g.setColor(BAR); g.fillRect(0, 0, (int) Math.round(getWidth() * pct / 100.0), getHeight()); }
        }
    };
    private final JPanel line = new JPanel(new BorderLayout());
    private final JLabel text = new JLabel(TEXT), percent = new JLabel("0%");
    private final Icons.Spinner spinner = new Icons.Spinner(16, SPIN);
    private final javax.swing.Timer spin = new javax.swing.Timer(70, e -> { spinner.angle = (spinner.angle + 30) % 360; text.repaint(); });
    private final javax.swing.Timer showTimer = new javax.swing.Timer(SHOW_DELAY_MS, e -> { if (running) { line.setVisible(true); spin.start(); revalidate(); repaint(); } });

    ProgressStrip() {
        setLayout(new BorderLayout()); setOpaque(false);
        bar.setPreferredSize(new Dimension(1, BAR_H)); bar.setMinimumSize(new Dimension(1, BAR_H));
        text.setIcon(spinner); text.setFont(Ui.BOLD.deriveFont(13f)); text.setForeground(Ui.TEXT); text.setIconTextGap(10);
        percent.setFont(Ui.BASE.deriveFont(13f)); percent.setForeground(PCT);
        line.setBackground(Color.WHITE); line.setBorder(new CompoundBorder(new MatteBorder(0, 0, 1, 0, Ui.BORDER), new EmptyBorder(7, 20, 7, 20)));
        line.add(text, BorderLayout.WEST); line.add(percent, BorderLayout.EAST); line.setVisible(false);
        showTimer.setRepeats(false);
        add(bar, BorderLayout.NORTH); add(line, BorderLayout.CENTER);
    }

    /** UI thread: a run begins. */
    void start() { running = true; pct = 0; percent.setText("0%"); if (!line.isVisible() && !showTimer.isRunning()) showTimer.restart(); bar.repaint(); }
    /** UI thread: progress in percent (0-100). */
    void setProgress(int p) { if (!running) return; pct = Math.max(0, Math.min(100, p)); percent.setText(pct + "%"); bar.repaint(); }
    /** UI thread: the run is over (finished, failed or superseded by nothing). */
    void stop() { running = false; showTimer.stop(); spin.stop(); boolean was = line.isVisible(); line.setVisible(false); bar.repaint(); if (was) { revalidate(); repaint(); } }
}
