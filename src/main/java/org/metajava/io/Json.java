package org.metajava.io;

import java.util.*;

/** Minimal JSON reader / writer (objects = LinkedHashMap, arrays = ArrayList, numbers = Double, plus String, Boolean, null). */
public final class Json {
    private Json() {}

    // ---------------------------------------------------------------- writer
    public static String write(Object v) { StringBuilder b = new StringBuilder(); put(b, v, 0); b.append('\n'); return b.toString(); }

    @SuppressWarnings("unchecked")
    private static void put(StringBuilder b, Object v, int ind) {
        if (v == null) b.append("null");
        else if (v instanceof String) str(b, (String) v);
        else if (v instanceof Boolean) b.append(v);
        else if (v instanceof Number) {
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) b.append("null");
            else if (v instanceof Integer || v instanceof Long || d == Math.rint(d) && Math.abs(d) < 1e15) b.append((long) d);
            else b.append(v.toString());
        }
        else if (v instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) v; if (m.isEmpty()) { b.append("{}"); return; }
            b.append("{\n"); int i = 0;
            for (Map.Entry<String, Object> e : m.entrySet()) { indent(b, ind + 1); str(b, e.getKey()); b.append(": "); put(b, e.getValue(), ind + 1); if (++i < m.size()) b.append(','); b.append('\n'); }
            indent(b, ind); b.append('}');
        }
        else if (v instanceof List) {
            List<Object> l = (List<Object>) v; if (l.isEmpty()) { b.append("[]"); return; }
            boolean flat = true; for (Object o : l) if (o instanceof Map || o instanceof List) { flat = false; break; }
            if (flat) { b.append('['); for (int i = 0; i < l.size(); i++) { if (i > 0) b.append(", "); put(b, l.get(i), ind); } b.append(']'); return; }
            b.append("[\n");
            for (int i = 0; i < l.size(); i++) { indent(b, ind + 1); put(b, l.get(i), ind + 1); if (i < l.size() - 1) b.append(','); b.append('\n'); }
            indent(b, ind); b.append(']');
        }
        else str(b, v.toString());
    }
    private static void indent(StringBuilder b, int n) { for (int i = 0; i < n; i++) b.append("  "); }
    private static void str(StringBuilder b, String s) {
        b.append('"');
        for (int i = 0; i < s.length(); i++) { char c = s.charAt(i);
            switch (c) { case '"': b.append("\\\""); break; case '\\': b.append("\\\\"); break; case '\n': b.append("\\n"); break; case '\r': b.append("\\r"); break; case '\t': b.append("\\t"); break;
                default: if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c); } }
        b.append('"');
    }

    // ---------------------------------------------------------------- parser
    public static Object parse(String s) {
        P p = new P(s); p.ws(); Object v = p.value(); p.ws();
        if (p.i < s.length()) throw new IllegalArgumentException("Unexpected text after the JSON value at position " + p.i);
        return v;
    }
    private static final class P {
        final String s; int i;
        P(String s) { this.s = s.length() > 0 && s.charAt(0) == '﻿' ? s.substring(1) : s; }
        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        IllegalArgumentException err(String m) { return new IllegalArgumentException("Invalid JSON: " + m + " at position " + i); }
        Object value() {
            if (i >= s.length()) throw err("unexpected end");
            char c = s.charAt(i);
            if (c == '{') return object(); if (c == '[') return array(); if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; } if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; } if (s.startsWith("null", i)) { i += 4; return null; }
            int st = i; while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (st == i) throw err("unexpected character '" + c + "'");
            try { return Double.valueOf(s.substring(st, i)); } catch (NumberFormatException e) { throw err("bad number"); }
        }
        Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>(); i++; ws();
            if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
            while (true) { ws(); if (i >= s.length() || s.charAt(i) != '"') throw err("expected a key"); String k = string(); ws();
                if (i >= s.length() || s.charAt(i) != ':') throw err("expected ':'"); i++; ws(); m.put(k, value()); ws();
                if (i < s.length() && s.charAt(i) == ',') { i++; continue; } if (i < s.length() && s.charAt(i) == '}') { i++; return m; } throw err("expected ',' or '}'"); }
        }
        List<Object> array() {
            List<Object> l = new ArrayList<>(); i++; ws();
            if (i < s.length() && s.charAt(i) == ']') { i++; return l; }
            while (true) { ws(); l.add(value()); ws();
                if (i < s.length() && s.charAt(i) == ',') { i++; continue; } if (i < s.length() && s.charAt(i) == ']') { i++; return l; } throw err("expected ',' or ']'"); }
        }
        String string() {
            StringBuilder b = new StringBuilder(); i++;
            while (i < s.length()) { char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c == '\\') { if (i >= s.length()) break; char e = s.charAt(i++);
                    switch (e) { case 'n': b.append('\n'); break; case 'r': b.append('\r'); break; case 't': b.append('\t'); break; case 'b': b.append('\b'); break; case 'f': b.append('\f'); break;
                        case 'u': if (i + 4 > s.length()) throw err("bad escape"); b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break; default: b.append(e); } }
                else b.append(c); }
            throw err("unterminated string");
        }
    }
}
