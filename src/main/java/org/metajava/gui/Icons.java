package org.metajava.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

/** Small vector icons drawn with Java2D (no image files needed). */
final class Icons {
    private Icons() {}

    private abstract static class Base implements Icon {
        final int size; final Color color;
        Base(int size, Color color) { this.size = size; this.color = color; }
        public int getIconWidth() { return size; } public int getIconHeight() { return size; }
        public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.translate(x, y); g.scale(size / 16.0, size / 16.0); g.setColor(color); g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            draw(g); g.dispose();
        }
        abstract void draw(Graphics2D g);
    }

    /** Open folder (outline). */
    static Icon folderOpen(int size, Color color) {
        return new Base(size, color) { void draw(Graphics2D g) {
            Path2D back = new Path2D.Double(); back.moveTo(2, 13); back.lineTo(2, 3.5); back.quadTo(2, 2.5, 3, 2.5); back.lineTo(6, 2.5); back.lineTo(7.6, 4.2); back.lineTo(12, 4.2); back.quadTo(13, 4.2, 13, 5.2); back.lineTo(13, 6.5); g.draw(back);
            Path2D front = new Path2D.Double(); front.moveTo(2, 13); front.lineTo(3.7, 7.6); front.quadTo(4, 6.7, 5, 6.7); front.lineTo(14, 6.7); front.quadTo(15, 6.7, 14.7, 7.7); front.lineTo(13.2, 12.2); front.quadTo(12.9, 13, 12, 13); front.closePath(); g.draw(front);
        } };
    }

    /** Floppy disk (outline). */
    static Icon floppy(int size, Color color) {
        return new Base(size, color) { void draw(Graphics2D g) {
            Path2D body = new Path2D.Double(); body.moveTo(3, 2); body.lineTo(11.3, 2); body.lineTo(14, 4.7); body.lineTo(14, 13); body.quadTo(14, 14, 13, 14); body.lineTo(3, 14); body.quadTo(2, 14, 2, 13); body.lineTo(2, 3); body.quadTo(2, 2, 3, 2); body.closePath(); g.draw(body);
            g.draw(new Rectangle2D.Double(5, 2, 5, 3.8)); g.draw(new RoundRectangle2D.Double(4.5, 8.5, 7, 5.5, 1, 1));
        } };
    }

    /** Counter-clockwise circular arrow (reset). */
    static Icon reset(int size, Color color) {
        return new Base(size, color) { void draw(Graphics2D g) {
            g.draw(new Arc2D.Double(2.5, 2.5, 11, 11, 120, -285, Arc2D.OPEN));
            Path2D head = new Path2D.Double(); head.moveTo(2.3, 1.8); head.lineTo(2.9, 5.3); head.lineTo(6.3, 4.4); g.draw(head);
        } };
    }

    /** Filled play triangle. */
    static Icon play(int size, Color color) {
        return new Base(size, color) { void draw(Graphics2D g) { Path2D t = new Path2D.Double(); t.moveTo(4, 2.5); t.lineTo(13.5, 8); t.lineTo(4, 13.5); t.closePath(); g.fill(t); } };
    }

    /** Spinner arc; angle in degrees. */
    static final class Spinner extends Base {
        volatile double angle;
        Spinner(int size, Color color) { super(size, color); }
        void draw(Graphics2D g) {
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 55)); g.draw(new Ellipse2D.Double(2.5, 2.5, 11, 11));
            g.setColor(color); g.draw(new Arc2D.Double(2.5, 2.5, 11, 11, -angle, -250, Arc2D.OPEN));
        }
    }
}
