package org.metajava.plot;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.*;
import java.awt.image.*;
import java.awt.image.renderable.RenderableImage;
import java.text.AttributedCharacterIterator;
import java.util.*;
import java.util.List;

/**
 * A Graphics2D that records what is drawn (fills, strokes, text) instead of rasterising it, so that the very same plotting code
 * that produces the PNG can also be written as SVG or PDF (see {@link VectorExport}). All recorded geometry is in device pixels,
 * i.e. already multiplied by the resolution scale, exactly as the PNG would be.
 */
public final class VectorGraphics extends Graphics2D {
    static final int FILL = 0, STROKE = 1, TEXT = 2;
    static final class Op {
        int kind; Shape shape; Color color; double alpha = 1; float lw = 1; int cap, join; float miter = 10; float[] dash; float phase; Shape clip;
        String text; Font font; AffineTransform tx; float x, y; double adv;
    }
    static final FontRenderContext FRC = new FontRenderContext(null, true, true);

    final List<Op> ops; final int pxW, pxH;
    private AffineTransform tx = new AffineTransform(); private Color color = Color.BLACK; private Paint paint = Color.BLACK; private Stroke stroke = new BasicStroke(1f);
    private Font font = new Font("SansSerif", Font.PLAIN, 12); private Shape clip; private Composite comp = AlphaComposite.SrcOver; private Color bg = Color.WHITE;
    private final RenderingHints hints = new RenderingHints(null); private final Graphics2D ref;

    /** @param scale resolution scale; only used so that font metrics are those the raster canvas would see. */
    public VectorGraphics(int pxW, int pxH, double scale) {
        this.ops = new ArrayList<>(); this.pxW = pxW; this.pxH = pxH;
        BufferedImage im = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB); ref = im.createGraphics();
        ref.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); ref.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        ref.scale(scale, scale);
    }
    private VectorGraphics(VectorGraphics o) {
        ops = o.ops; pxW = o.pxW; pxH = o.pxH; tx = new AffineTransform(o.tx); color = o.color; paint = o.paint; stroke = o.stroke; font = o.font; clip = o.clip; comp = o.comp; bg = o.bg; ref = o.ref; hints.putAll(o.hints);
    }

    private double alpha() { double a = color.getAlpha() / 255.0; if (comp instanceof AlphaComposite) a *= ((AlphaComposite) comp).getAlpha(); return a; }
    private Op base(int kind) { Op o = new Op(); o.kind = kind; o.color = color; o.alpha = alpha(); o.clip = clip; return o; }
    private static double sc(AffineTransform t) { return Math.sqrt(Math.abs(t.getDeterminant())); }

    // ---- drawing ----
    @Override public void fill(Shape s) { Op o = base(FILL); o.shape = new Path2D.Double(s, tx); ops.add(o); }
    @Override public void draw(Shape s) {
        if (stroke instanceof BasicStroke) {
            BasicStroke b = (BasicStroke) stroke; Op o = base(STROKE); o.shape = new Path2D.Double(s, tx); double k = sc(tx);
            o.lw = (float) (b.getLineWidth() * k); o.cap = b.getEndCap(); o.join = b.getLineJoin(); o.miter = b.getMiterLimit();
            if (b.getDashArray() != null) { o.dash = new float[b.getDashArray().length]; for (int i = 0; i < o.dash.length; i++) o.dash[i] = (float) (b.getDashArray()[i] * k); o.phase = (float) (b.getDashPhase() * k); }
            ops.add(o);
        } else fill(stroke.createStrokedShape(s));
    }
    @Override public void drawString(String str, float x, float y) {
        if (str == null || str.isEmpty()) return; Op o = base(TEXT); o.text = str; o.font = font; o.tx = new AffineTransform(tx); o.x = x; o.y = y; o.adv = font.getStringBounds(str, FRC).getWidth(); ops.add(o);
    }
    @Override public void drawString(String str, int x, int y) { drawString(str, (float) x, (float) y); }
    @Override public void drawString(AttributedCharacterIterator it, int x, int y) { drawString(it, (float) x, (float) y); }
    @Override public void drawString(AttributedCharacterIterator it, float x, float y) { StringBuilder sb = new StringBuilder(); for (char c = it.first(); c != AttributedCharacterIterator.DONE; c = it.next()) sb.append(c); drawString(sb.toString(), x, y); }
    @Override public void drawGlyphVector(GlyphVector g, float x, float y) { fill(g.getOutline(x, y)); }
    @Override public void fillRect(int x, int y, int w, int h) { fill(new Rectangle(x, y, w, h)); }
    @Override public void clearRect(int x, int y, int w, int h) { Color c = color; color = bg; fillRect(x, y, w, h); color = c; }
    @Override public void drawLine(int x1, int y1, int x2, int y2) { draw(new Line2D.Float(x1, y1, x2, y2)); }
    @Override public void drawRoundRect(int x, int y, int w, int h, int aw, int ah) { draw(new RoundRectangle2D.Float(x, y, w, h, aw, ah)); }
    @Override public void fillRoundRect(int x, int y, int w, int h, int aw, int ah) { fill(new RoundRectangle2D.Float(x, y, w, h, aw, ah)); }
    @Override public void drawOval(int x, int y, int w, int h) { draw(new Ellipse2D.Float(x, y, w, h)); }
    @Override public void fillOval(int x, int y, int w, int h) { fill(new Ellipse2D.Float(x, y, w, h)); }
    @Override public void drawArc(int x, int y, int w, int h, int s, int a) { draw(new Arc2D.Float(x, y, w, h, s, a, Arc2D.OPEN)); }
    @Override public void fillArc(int x, int y, int w, int h, int s, int a) { fill(new Arc2D.Float(x, y, w, h, s, a, Arc2D.PIE)); }
    @Override public void drawPolyline(int[] xs, int[] ys, int n) { Path2D.Float p = new Path2D.Float(); for (int i = 0; i < n; i++) { if (i == 0) p.moveTo(xs[i], ys[i]); else p.lineTo(xs[i], ys[i]); } draw(p); }
    @Override public void drawPolygon(int[] xs, int[] ys, int n) { draw(new Polygon(xs, ys, n)); }
    @Override public void fillPolygon(int[] xs, int[] ys, int n) { fill(new Polygon(xs, ys, n)); }

    // ---- images are not used by the plots ----
    @Override public boolean drawImage(Image i, AffineTransform x, java.awt.image.ImageObserver o) { return false; }
    @Override public void drawImage(BufferedImage i, BufferedImageOp op, int x, int y) { }
    @Override public void drawRenderedImage(RenderedImage i, AffineTransform x) { }
    @Override public void drawRenderableImage(RenderableImage i, AffineTransform x) { }
    @Override public boolean drawImage(Image i, int x, int y, ImageObserver o) { return false; }
    @Override public boolean drawImage(Image i, int x, int y, int w, int h, ImageObserver o) { return false; }
    @Override public boolean drawImage(Image i, int x, int y, Color b, ImageObserver o) { return false; }
    @Override public boolean drawImage(Image i, int x, int y, int w, int h, Color b, ImageObserver o) { return false; }
    @Override public boolean drawImage(Image i, int a, int b, int c, int d, int e, int f, int g, int h, ImageObserver o) { return false; }
    @Override public boolean drawImage(Image i, int a, int b, int c, int d, int e, int f, int g, int h, Color bk, ImageObserver o) { return false; }
    @Override public void copyArea(int x, int y, int w, int h, int dx, int dy) { }

    // ---- state ----
    @Override public Graphics create() { return new VectorGraphics(this); }
    @Override public void dispose() { }
    @Override public Color getColor() { return color; }
    @Override public void setColor(Color c) { if (c != null) { color = c; paint = c; } }
    @Override public void setPaint(Paint p) { if (p instanceof Color) setColor((Color) p); else if (p != null) paint = p; }   // gradients are not used by the plots; the current colour is kept
    @Override public Paint getPaint() { return paint; }
    @Override public void setPaintMode() { }
    @Override public void setXORMode(Color c) { }
    @Override public Font getFont() { return font; }
    @Override public void setFont(Font f) { if (f != null) font = f; }
    @Override public FontMetrics getFontMetrics(Font f) { return ref.getFontMetrics(f); }
    @Override public FontRenderContext getFontRenderContext() { return ref.getFontRenderContext(); }
    @Override public Stroke getStroke() { return stroke; }
    @Override public void setStroke(Stroke s) { if (s != null) stroke = s; }
    @Override public Composite getComposite() { return comp; }
    @Override public void setComposite(Composite c) { if (c != null) comp = c; }
    @Override public void setBackground(Color c) { bg = c; }
    @Override public Color getBackground() { return bg; }
    @Override public GraphicsConfiguration getDeviceConfiguration() { return ref.getDeviceConfiguration(); }
    @Override public boolean hit(Rectangle r, Shape s, boolean onStroke) { return (onStroke ? stroke.createStrokedShape(s) : s).intersects(r); }
    @Override public void setRenderingHint(RenderingHints.Key k, Object v) { hints.put(k, v); }
    @Override public Object getRenderingHint(RenderingHints.Key k) { return hints.get(k); }
    @Override public void setRenderingHints(Map<?, ?> m) { hints.clear(); hints.putAll(m); }
    @Override public void addRenderingHints(Map<?, ?> m) { hints.putAll(m); }
    @Override public RenderingHints getRenderingHints() { return (RenderingHints) hints.clone(); }

    // ---- transform ----
    @Override public void translate(int x, int y) { tx.translate(x, y); }
    @Override public void translate(double x, double y) { tx.translate(x, y); }
    @Override public void rotate(double t) { tx.rotate(t); }
    @Override public void rotate(double t, double x, double y) { tx.rotate(t, x, y); }
    @Override public void scale(double a, double b) { tx.scale(a, b); }
    @Override public void shear(double a, double b) { tx.shear(a, b); }
    @Override public void transform(AffineTransform t) { tx.concatenate(t); }
    @Override public void setTransform(AffineTransform t) { tx = new AffineTransform(t); }
    @Override public AffineTransform getTransform() { return new AffineTransform(tx); }

    // ---- clip (kept in device space, reported in user space) ----
    @Override public Shape getClip() { if (clip == null) return null; try { return tx.createInverse().createTransformedShape(clip); } catch (NoninvertibleTransformException e) { return null; } }
    @Override public void setClip(Shape s) { clip = s == null ? null : new Path2D.Double(s, tx); }
    @Override public void setClip(int x, int y, int w, int h) { setClip(new Rectangle(x, y, w, h)); }
    @Override public void clip(Shape s) { if (s == null) { clip = null; return; } Area a = new Area(new Path2D.Double(s, tx)); if (clip != null) a.intersect(new Area(clip)); clip = a; }
    @Override public void clipRect(int x, int y, int w, int h) { clip(new Rectangle(x, y, w, h)); }
    @Override public Rectangle getClipBounds() { Shape s = getClip(); return s == null ? null : s.getBounds(); }
}
