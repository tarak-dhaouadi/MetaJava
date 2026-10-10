package org.metajava.export;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;

/** Thin wrapper over ZipOutputStream for writing OOXML packages (.xlsx / .docx). */
final class ZipOut implements Closeable {
    private final ZipOutputStream z;
    ZipOut(OutputStream o) { z = new ZipOutputStream(o); z.setLevel(Deflater.DEFAULT_COMPRESSION); }
    void text(String name, String content) throws IOException { bytes(name, content.getBytes(StandardCharsets.UTF_8)); }
    void bytes(String name, byte[] data) throws IOException { z.putNextEntry(new ZipEntry(name)); z.write(data); z.closeEntry(); }
    @Override public void close() throws IOException { z.close(); }
    /** XML text escaping, also dropping characters that are illegal in XML 1.0. */
    static String esc(String s) {
        if (s == null) return ""; StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) { char c = s.charAt(i);
            if (c == '&') b.append("&amp;"); else if (c == '<') b.append("&lt;"); else if (c == '>') b.append("&gt;"); else if (c == '"') b.append("&quot;");
            else if (c == 0x9 || c == 0xA || c == 0xD || c >= 0x20 && c <= 0xD7FF || c >= 0xE000 && c <= 0xFFFD || Character.isSurrogate(c)) b.append(c); }
        return b.toString();
    }
}
