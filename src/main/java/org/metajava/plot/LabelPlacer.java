package org.metajava.plot;

import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

/**
 * Label placement shared by every plot that offers the option "Avoid label overlap" (boxed labels with leader lines, like ggrepel::geom_label_repel).
 * <p>
 * Hard rules (never traded away): a label box keeps {@link #CLEAR} pixels away from <b>every</b> plotted symbol (its own and all the others, labelled or not)
 * and from every blocked rectangle (legends, text), two label boxes never overlap, and a box stays inside the panel (it leaves the panel only when the panel has no
 * free place at all). Distance to the point and leader lines are what the algorithm trades for these rules: among the admissible places it takes the one that
 * keeps the label close to its point, whose leader line crosses no other symbol, no other label and no other leader line.
 * <p>
 * Method: for each label, candidate places on rings around its symbol (32 directions, growing distances); candidates that touch a symbol are dropped for good. The
 * labels are placed one after the other (most crowded first) on the cheapest admissible candidate, then each label is re-placed, in turn, given all the others,
 * until nothing improves. A label with no admissible ring place falls back to a grid scan of the panel (and, as the last resort, of a band around it).
 * Everything is deterministic. Positions are in the logical (unscaled) coordinates of the {@link Canvas}.
 */
final class LabelPlacer {
    static final double CLEAR = 2.5;        // minimum gap between a label box and any symbol
    static final double BOX_GAP = 2.0;      // minimum gap between two label boxes
    static final double LABEL_H = 15;       // box height at 8.5 pt
    private static final double MIN_LEADER = 3.5;   // a leader line is drawn when the box is farther than this from the edge of its symbol
    private static final long BUDGET_MS = 1500;   // refinement (extra starts, passes, exchanges) stops after this long: only crowded plots with 100+ labels get near it
    private static final double HARD = 1e6, K_SYMBOL = 1500, K_BOX = 1500, K_LEADER = 300, K_AMBIGUOUS = 40;

    private final double x0, y0, x1, y1; private final float font;
    private final List<String> texts = new ArrayList<>(); private final List<double[]> lab = new ArrayList<>(); private final List<Integer> labObs = new ArrayList<>();   // label points {x, y, radius}; index of the symbol among the obstacles
    private final List<double[]> obs = new ArrayList<>();      // symbols {x, y, radius}: every plotted point
    private final List<double[]> blocks = new ArrayList<>();   // forbidden rectangles {left, top, right, bottom}

    LabelPlacer(double x0, double y0, double x1, double y1, float font) { this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1; this.font = font; }

    /** Returns a placer for the panel of {@code a}, or null when the option is off (callers then use the plain label). */
    static LabelPlacer on(boolean enabled, Plots.Axes a) { return enabled ? new LabelPlacer(a.x0, a.y0, a.x1, a.y1, 8.5f) : null; }
    static LabelPlacer on(boolean enabled, double x0, double y0, double x1, double y1) { return enabled ? new LabelPlacer(x0, y0, x1, y1, 8.5f) : null; }

    /** A plotted point that carries a label (its symbol is an obstacle for all the labels); rad = radius of the circle that encloses the drawn symbol. */
    void add(String text, double x, double y, double rad) {
        if (text == null || !Double.isFinite(x) || !Double.isFinite(y)) return;
        texts.add(text); lab.add(new double[]{x, y, rad}); labObs.add(obs.size()); obs.add(new double[]{x, y, rad});
    }
    /** A plotted symbol without a label (still an obstacle). */
    void point(double x, double y, double rad) { if (Double.isFinite(x) && Double.isFinite(y)) obs.add(new double[]{x, y, rad}); }
    /** A rectangle (legend, text, ...) that no label may cover. */
    void block(double left, double top, double right, double bottom) { if (right > left && bottom > top) blocks.add(new double[]{left, top, right, bottom}); }

    // ------------------------------------------------------------------ result of a placement (also used to verify it)
    static final class Layout {
        final int n; final double[] cx, cy, w, h, px, py, rad; final boolean[] leader; final double[][] seg;   // seg[i] = {x1, y1, x2, y2} of the leader of label i
        final double[][] symbols, blocks; final int[] symbolOf; int forced;   // forced: labels that could not be kept off every symbol (only when no free place exists at all)
        Layout(int n, double[][] symbols, double[][] blocks, int[] symbolOf) { this.n = n; cx = new double[n]; cy = new double[n]; w = new double[n]; h = new double[n]; px = new double[n]; py = new double[n]; rad = new double[n]; leader = new boolean[n]; seg = new double[n][]; this.symbols = symbols; this.blocks = blocks; this.symbolOf = symbolOf; }
    }
    /** Test hook: when set (per thread), every {@link #draw} appends the layout it drew. */
    static final ThreadLocal<List<Layout>> AUDIT = new ThreadLocal<>();

    private static final class Cand {
        double cx, cy, cost; double bx0, by0, bx1, by1; boolean lead; double sx, sy, ex, ey;
    }

    // ------------------------------------------------------------------ geometry
    private static double rectDist(double bx0, double by0, double bx1, double by1, double qx, double qy) { return Math.hypot(Math.max(Math.max(bx0 - qx, qx - bx1), 0), Math.max(Math.max(by0 - qy, qy - by1), 0)); }
    private static double segPointDist(double ax, double ay, double bx, double by, double qx, double qy) {
        double dx = bx - ax, dy = by - ay, l2 = dx * dx + dy * dy, t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((qx - ax) * dx + (qy - ay) * dy) / l2)); return Math.hypot(ax + t * dx - qx, ay + t * dy - qy);
    }
    /** Length of the part of segment (a, b) inside the circle (c, r). */
    private static double chord(double ax, double ay, double bx, double by, double cx, double cy, double r) {
        double dx = bx - ax, dy = by - ay, l = Math.hypot(dx, dy); if (l == 0) return 0; double fx = ax - cx, fy = ay - cy, A = l * l, B = 2 * (fx * dx + fy * dy), C = fx * fx + fy * fy - r * r, D = B * B - 4 * A * C;
        if (D <= 0) return 0; double q = Math.sqrt(D), t1 = Math.max(0, (-B - q) / (2 * A)), t2 = Math.min(1, (-B + q) / (2 * A)); return t2 > t1 ? (t2 - t1) * l : 0;
    }
    private static boolean segRect(double ax, double ay, double bx, double by, double r0, double s0, double r1, double s1) {   // Liang-Barsky: does the segment meet the rectangle?
        double t0 = 0, t1 = 1, dx = bx - ax, dy = by - ay; double[] p = {-dx, dx, -dy, dy}, q = {ax - r0, r1 - ax, ay - s0, s1 - ay};
        for (int k = 0; k < 4; k++) { if (p[k] == 0) { if (q[k] < 0) return false; } else { double t = q[k] / p[k]; if (p[k] < 0) { if (t > t1) return false; if (t > t0) t0 = t; } else { if (t < t0) return false; if (t < t1) t1 = t; } } }
        return true;
    }
    private static boolean segSeg(double ax, double ay, double bx, double by, double cx, double cy, double dx, double dy) {
        double d1 = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax), d2 = (bx - ax) * (dy - ay) - (by - ay) * (dx - ax), d3 = (dx - cx) * (ay - cy) - (dy - cy) * (ax - cx), d4 = (dx - cx) * (by - cy) - (dy - cy) * (bx - cx);
        return ((d1 > 0) != (d2 > 0)) && ((d3 > 0) != (d4 > 0));
    }

    // ------------------------------------------------------------------ the algorithm
    private final class Solver {
        final int n, m; final double[] w, h, px, py, rad, ox, oy, orad; final int[] own; final Cand[][] ring, grid; final Cand[] cur; final boolean[] placed, forcedAt; final long deadline = System.nanoTime() + BUDGET_MS * 1_000_000L; boolean late() { return System.nanoTime() > deadline; }
        Solver(FontMetrics fm) {
            n = texts.size(); m = obs.size(); w = new double[n]; h = new double[n]; px = new double[n]; py = new double[n]; rad = new double[n]; own = new int[n];
            for (int i = 0; i < n; i++) { w[i] = fm.stringWidth(texts.get(i)) + 10; h[i] = LABEL_H; px[i] = lab.get(i)[0]; py[i] = lab.get(i)[1]; rad[i] = lab.get(i)[2]; own[i] = labObs.get(i); }
            ox = new double[m]; oy = new double[m]; orad = new double[m]; for (int j = 0; j < m; j++) { ox[j] = obs.get(j)[0]; oy[j] = obs.get(j)[1]; orad[j] = obs.get(j)[2]; }
            ring = new Cand[n][]; grid = new Cand[n][]; cur = new Cand[n]; placed = new boolean[n]; forcedAt = new boolean[n];
        }

        /** The place with centre (cx, cy) for label i, or null when it touches a symbol or a blocked rectangle or leaves the panel by more than {@code out} px. Static cost: distance, leader through a symbol, ambiguity. */
        Cand make(int i, double cx, double cy, double out) {
            double bx0 = cx - w[i] / 2, bx1 = cx + w[i] / 2, by0 = cy - h[i] / 2, by1 = cy + h[i] / 2;
            if (bx0 < x0 + 1 - out || bx1 > x1 - 1 + out || by0 < y0 + 1 - out || by1 > y1 - 1 + out) return null;
            for (double[] b : blocks) if (bx0 < b[2] + BOX_GAP && b[0] < bx1 + BOX_GAP && by0 < b[3] + BOX_GAP && b[1] < by1 + BOX_GAP) return null;
            for (int j = 0; j < m; j++) { if (Math.abs(ox[j] - cx) > w[i] / 2 + orad[j] + CLEAR || Math.abs(oy[j] - cy) > h[i] / 2 + orad[j] + CLEAR) continue; if (rectDist(bx0, by0, bx1, by1, ox[j], oy[j]) < orad[j] + CLEAR) return null; }
            Cand k = new Cand(); k.cx = cx; k.cy = cy; k.bx0 = bx0; k.by0 = by0; k.bx1 = bx1; k.by1 = by1;
            double d = rectDist(bx0, by0, bx1, by1, px[i], py[i]), gap = d - rad[i]; k.cost = gap + 0.08 * Math.hypot(cx - px[i], cy - py[i]);
            k.lead = gap > MIN_LEADER;
            if (k.lead) {
                double qx = Math.max(bx0, Math.min(px[i], bx1)), qy = Math.max(by0, Math.min(py[i], by1)), ux = (qx - px[i]) / d, uy = (qy - py[i]) / d;
                k.sx = px[i] + ux * rad[i]; k.sy = py[i] + uy * rad[i]; k.ex = qx; k.ey = qy;
                for (int j = 0; j < m; j++) if (j != own[i] && segPointDist(k.sx, k.sy, k.ex, k.ey, ox[j], oy[j]) < orad[j] + 1.0)   // leader through another symbol: the longer inside it, the dearer
                    k.cost += K_SYMBOL * (0.3 + 0.7 * Math.min(1, chord(k.sx, k.sy, k.ex, k.ey, ox[j], oy[j], orad[j]) / (2 * Math.max(orad[j], 1))));
            } else {
                for (int j = 0; j < m; j++) if (j != own[i] && rectDist(bx0, by0, bx1, by1, ox[j], oy[j]) - orad[j] < gap + 1.5) { k.cost += K_AMBIGUOUS; break; }   // no leader, yet another symbol is as near as its own
            }
            return k;
        }

        Cand[] rings(int i) {
            if (ring[i] != null) return ring[i];
            final int ND = 32; final double[] extra = {0, 3, 7, 12, 18, 26, 36, 48, 64, 84, 110, 140, 180, 230, 300, 400, 520, 700};
            List<Cand> out = new ArrayList<>();
            for (double e : extra) {
                double dd = rad[i] + CLEAR + 0.5 + e; if (dd > Math.hypot(x1 - x0, y1 - y0)) break;
                for (int k = 0; k < ND; k++) {
                    double th = 2 * Math.PI * k / ND, ux = Math.cos(th), uy = -Math.sin(th), lo = 0, hi = 2 * (dd + w[i] + h[i]);
                    for (int it = 0; it < 32; it++) { double t = (lo + hi) / 2, dx = Math.max(Math.abs(ux * t) - w[i] / 2, 0), dy = Math.max(Math.abs(uy * t) - h[i] / 2, 0); if (Math.hypot(dx, dy) >= dd) hi = t; else lo = t; }
                    Cand c = make(i, px[i] + ux * hi, py[i] + uy * hi, 0); if (c != null) out.add(c);
                }
            }
            return ring[i] = out.toArray(new Cand[0]);
        }
        /** Fallback when the ring places are all taken: every position on a grid over the panel (then over a band around the panel). */
        Cand[] grid(int i) {
            if (grid[i] != null) return grid[i];
            List<Cand> out = new ArrayList<>();
            for (double out0 : new double[]{0, 60, 160}) {
                double step = 4; for (double cy = y0 - out0 + h[i] / 2 + 1; cy <= y1 + out0 - h[i] / 2 - 1; cy += step) for (double cx = x0 - out0 + w[i] / 2 + 1; cx <= x1 + out0 - w[i] / 2 - 1; cx += step) { Cand c = make(i, cx, cy, out0); if (c != null) out.add(c); }
                if (!out.isEmpty()) break;
            }
            return grid[i] = out.toArray(new Cand[0]);
        }

        /** Interaction of two placed labels: >= HARD when their boxes overlap, else the penalties for a leader through the other box or across the other leader. */
        double pair(Cand k, Cand q) {
            if (k.bx0 < q.bx1 + BOX_GAP && q.bx0 < k.bx1 + BOX_GAP && k.by0 < q.by1 + BOX_GAP && q.by0 < k.by1 + BOX_GAP) return HARD + Math.min(k.bx1, q.bx1) - Math.max(k.bx0, q.bx0);
            double c = 0;
            if (k.lead && segRect(k.sx, k.sy, k.ex, k.ey, q.bx0 - 1, q.by0 - 1, q.bx1 + 1, q.by1 + 1)) c += K_BOX;
            if (q.lead && segRect(q.sx, q.sy, q.ex, q.ey, k.bx0 - 1, k.by0 - 1, k.bx1 + 1, k.by1 + 1)) c += K_BOX;
            if (k.lead && q.lead && segSeg(k.sx, k.sy, k.ex, k.ey, q.sx, q.sy, q.ex, q.ey)) c += K_LEADER;
            return c;
        }
        /** Cost of putting label i on k given the labels placed so far (>= HARD when it overlaps a placed label). */
        double total(int i, Cand k, double bound) {
            double cost = k.cost;
            for (int j = 0; j < n; j++) { if (j == i || !placed[j]) continue; cost += pair(k, cur[j]); if (cost >= bound) return cost; }
            return cost;
        }
        /** Energy of the whole layout: every label's own cost plus every pair's interaction. */
        double energy() {
            double e = 0; for (int i = 0; i < n; i++) { e += cur[i].cost; for (int j = i + 1; j < n; j++) e += pair(cur[i], cur[j]); } return e;
        }

        /** Cheapest admissible place for label i; {@code keep} (its current place, if any) wins ties. */
        Cand best(int i, Cand keep) {
            Cand best = keep; double bc = keep == null ? Double.MAX_VALUE : total(i, keep, Double.MAX_VALUE);
            for (Cand k : rings(i)) { double c = total(i, k, bc); if (c < bc - 1e-9) { bc = c; best = k; } }
            if (best == null || (bc >= HARD && !late())) for (Cand k : grid(i)) { double c = total(i, k, bc); if (c < bc - 1e-9) { bc = c; best = k; } }
            if (best == null) {   // not a single place is free of symbols: put the label on the least-covered ring place (reported by Layout.forced)
                Cand f = make(i, Math.max(x0 + w[i] / 2, Math.min(x1 - w[i] / 2, px[i] + rad[i] + w[i] / 2 + CLEAR)), Math.max(y0 + h[i] / 2, Math.min(y1 - h[i] / 2, py[i])), 1e9);
                if (f == null) { f = new Cand(); f.cx = px[i] + rad[i] + w[i] / 2 + CLEAR; f.cy = py[i]; f.bx0 = f.cx - w[i] / 2; f.bx1 = f.cx + w[i] / 2; f.by0 = f.cy - h[i] / 2; f.by1 = f.cy + h[i] / 2; }
                forcedAt[i] = true; best = f;
            }
            return best;
        }

        /** One greedy placement in the given order, refined by re-placing each label until nothing improves, then by exchanging the places of two labels. */
        void once(Integer[] order) {
            Arrays.fill(placed, false);
            for (int i : order) { cur[i] = best(i, null); placed[i] = true; }
            for (int pass = 0; pass < 25 && !late(); pass++) {
                boolean changed = false;
                for (int i : order) { Cand old = cur[i]; placed[i] = false; Cand nw = best(i, old); placed[i] = true; if (nw != old) { cur[i] = nw; changed = true; } }
                if (n <= 160 && swaps()) changed = true;
                if (!changed) break;
            }
        }
        /** Exchanges the places of two labels (their boxes' centres) when that lowers the energy; true if any exchange was made. */
        boolean swaps() {
            boolean any = false;
            for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) {
                if (Math.abs(cur[i].cx - cur[j].cx) > 220 || Math.abs(cur[i].cy - cur[j].cy) > 220) continue; if (late()) return any;
                Cand a = make(i, cur[j].cx, cur[j].cy, 0), b = make(j, cur[i].cx, cur[i].cy, 0); if (a == null || b == null) continue;
                placed[i] = placed[j] = false; double eo = total(i, cur[i], Double.MAX_VALUE) + total(j, cur[j], Double.MAX_VALUE) + pair(cur[i], cur[j]), en = total(i, a, Double.MAX_VALUE) + total(j, b, Double.MAX_VALUE) + pair(a, b);
                placed[i] = placed[j] = true; if (en < eo - 1e-6) { cur[i] = a; cur[j] = b; any = true; }
            }
            return any;
        }

        Layout run() {
            Integer[] order = new Integer[n]; int[] crowd = new int[n];
            for (int i = 0; i < n; i++) { order[i] = i; for (int j = 0; j < m; j++) if (j != own[i] && Math.hypot(ox[j] - px[i], oy[j] - py[i]) < 60 + orad[j]) crowd[i]++; }
            Arrays.sort(order, (a, b) -> crowd[b] != crowd[a] ? Integer.compare(crowd[b], crowd[a]) : Integer.compare(a, b));   // most crowded first
            Cand[] bestCur = null; double bestE = Double.MAX_VALUE; int starts = n <= 60 ? 8 : n <= 160 ? 3 : 1; Random rnd = new Random(12345);
            for (int s = 0; s < starts && (s == 0 || !late()); s++) {
                Integer[] ord = order.clone(); if (s > 0) for (int i = ord.length - 1; i > 0; i--) { int j = rnd.nextInt(i + 1); Integer t = ord[i]; ord[i] = ord[j]; ord[j] = t; }   // later starts: random orders
                once(ord); double e = energy(); if (e < bestE - 1e-9) { bestE = e; bestCur = cur.clone(); }
            }
            System.arraycopy(bestCur, 0, cur, 0, n);
            double[][] sym = new double[m][]; for (int j = 0; j < m; j++) sym[j] = new double[]{ox[j], oy[j], orad[j]};
            Layout L = new Layout(n, sym, blocks.toArray(new double[0][]), own.clone()); for (boolean f : forcedAt) if (f) L.forced++;
            for (int i = 0; i < n; i++) { Cand k = cur[i]; L.cx[i] = k.cx; L.cy[i] = k.cy; L.w[i] = w[i]; L.h[i] = h[i]; L.px[i] = px[i]; L.py[i] = py[i]; L.rad[i] = rad[i]; L.leader[i] = k.lead; if (k.lead) L.seg[i] = new double[]{k.sx, k.sy, k.ex, k.ey}; }
            return L;
        }
    }

    /** The registered symbols {x, y, radius} (tests). */
    List<double[]> symbolsForTest() { return obs; }

    /** Computes the places of the labels (no drawing). */
    Layout solve(FontMetrics fm) { return new Solver(fm).run(); }

    /** Places and draws the labels (call after all the points are drawn). */
    void draw(Canvas c) {
        int n = texts.size(); if (n == 0) return; Graphics2D g = c.g; Layout L = solve(g.getFontMetrics(Canvas.font(font)));
        List<Layout> audit = AUDIT.get(); if (audit != null) audit.add(L);
        Shape clip = g.getClip(); g.setClip(null);
        g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.setColor(new Color(40, 40, 40, 200));
        for (int i = 0; i < n; i++) if (L.leader[i]) c.line(L.seg[i][0], L.seg[i][1], L.seg[i][2], L.seg[i][3]);   // leader line: from the edge of the symbol to the nearest point of the box
        for (int i = 0; i < n; i++) {
            RoundRectangle2D.Double b = new RoundRectangle2D.Double(L.cx[i] - L.w[i] / 2, L.cy[i] - L.h[i] / 2, L.w[i], L.h[i], 5, 5);
            g.setColor(Color.WHITE); g.fill(b); g.setColor(new Color(50, 50, 50)); g.setStroke(new BasicStroke(0.8f)); g.draw(b);
            g.setColor(new Color(60, 60, 60)); c.text(texts.get(i), L.cx[i], L.cy[i] + 3.2, font, 1, false);
        }
        g.setStroke(new BasicStroke(1f)); g.setClip(clip); g.setColor(Color.BLACK);
    }
}
