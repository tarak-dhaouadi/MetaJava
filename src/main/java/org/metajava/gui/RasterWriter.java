package org.metajava.gui;

import javax.imageio.*;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

/** Writes a plot as PNG, TIFF (LZW, lossless) or JPEG (quality 0.95), recording the resolution (dpi) in the file. */
public final class RasterWriter {
    private RasterWriter() {}
    public static final float JPEG_QUALITY = 0.95f;

    /** File extension of a format name ("PNG", "TIFF", "JPG", "SVG", "PDF"), without the dot. */
    public static String ext(String fmt) { switch (fmt) { case "TIFF": return "tif"; case "JPG": return "jpg"; default: return fmt.toLowerCase(); } }
    public static boolean isRaster(String fmt) { return fmt.equals("PNG") || fmt.equals("TIFF") || fmt.equals("JPG"); }

    public static void write(BufferedImage img, File f, String fmt, double dpi) throws IOException {
        String name = fmt.equals("TIFF") ? "tiff" : fmt.equals("JPG") ? "jpeg" : "png";
        Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName(name);
        if (!it.hasNext()) throw new IOException("This Java runtime has no " + fmt + " writer (Java 9 or later is required for TIFF).");
        ImageWriter w = it.next();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(f)) {
            if (out == null) throw new IOException("Cannot write " + f);
            w.setOutput(out); ImageWriteParam p = w.getDefaultWriteParam();
            if (fmt.equals("TIFF")) { p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT); p.setCompressionType("LZW"); }
            else if (fmt.equals("JPG")) { p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT); p.setCompressionQuality(JPEG_QUALITY); }
            IIOMetadata md = w.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(img), p);
            try { if (md != null && !md.isReadOnly()) tagResolution(md, fmt, dpi); } catch (Exception e) { /* the picture is still written, without the resolution tag */ }
            w.write(null, new IIOImage(img, null, md), p);
        } finally { w.dispose(); }
    }

    /** Records the resolution. TIFF understands the standard (pixel size in mm) tree; for PNG (pHYs) and JPEG (JFIF) the writers misread it, so their native trees are used. */
    private static void tagResolution(IIOMetadata md, String fmt, double dpi) throws IOException {
        if (fmt.equals("TIFF")) {
            double mm = 25.4 / dpi; IIOMetadataNode root = new IIOMetadataNode("javax_imageio_1.0"), dim = new IIOMetadataNode("Dimension"), h = new IIOMetadataNode("HorizontalPixelSize"), v = new IIOMetadataNode("VerticalPixelSize");
            h.setAttribute("value", Double.toString(mm)); v.setAttribute("value", Double.toString(mm)); dim.appendChild(h); dim.appendChild(v); root.appendChild(dim); md.mergeTree("javax_imageio_1.0", root);
        } else if (fmt.equals("PNG")) {
            String ppm = Long.toString(Math.round(dpi / 0.0254)); IIOMetadataNode root = new IIOMetadataNode("javax_imageio_png_1.0"), ph = new IIOMetadataNode("pHYs");
            ph.setAttribute("pixelsPerUnitXAxis", ppm); ph.setAttribute("pixelsPerUnitYAxis", ppm); ph.setAttribute("unitSpecifier", "meter"); root.appendChild(ph); md.mergeTree("javax_imageio_png_1.0", root);
        } else {
            String name = "javax_imageio_jpeg_image_1.0"; IIOMetadataNode root = (IIOMetadataNode) md.getAsTree(name); IIOMetadataNode j = find(root, "app0JFIF");
            if (j == null) return; String d = Integer.toString((int) Math.min(65535, Math.round(dpi))); j.setAttribute("resUnits", "1"); j.setAttribute("Xdensity", d); j.setAttribute("Ydensity", d); md.setFromTree(name, root);
        }
    }
    private static IIOMetadataNode find(IIOMetadataNode n, String name) {
        if (n.getNodeName().equals(name)) return n;
        for (org.w3c.dom.Node c = n.getFirstChild(); c != null; c = c.getNextSibling()) { IIOMetadataNode r = find((IIOMetadataNode) c, name); if (r != null) return r; }
        return null;
    }
}
