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
 * Method (no limit on the number of labels, no clock: the same data always gives the same layout, on any computer and at any export resolution):
 * <ol>
 * <li>Candidate places on rings of growing distance around each symbol (36 directions); a place that touches a symbol or a blocked rectangle is dropped for good.
 *     Symbols and placed labels live in spatial grids, so testing a place costs the same however many labels the plot has.</li>
 * <li>Greedy placement, most crowded symbol first, each label on the cheapest admissible place (distance, with a price that grows faster than linearly so one very long leader
 *     is dearer than several short ones; leader through a symbol; leader through another label; leader across another leader), then re-placement of each label and exchange of
 *     pairs of labels until nothing improves.</li>
 * <li>While a leader still crosses another leader, runs through a label or through a symbol: greedy restarts in shaken orders, then simulated annealing (labels jump to random
 *     free spots; worse moves are accepted with a falling probability, so crowded clusters get out of the local minima of the greedy pass), then the same re-placement.
 *     The annealing may let labels overlap while it moves, but only a state without overlap is ever returned.</li>
 * <li>A label with no admissible ring place falls back to a scan of the panel (coarse, then fine, then a band around it); as the last resort (no spot free of symbols at all)
 *     it is put beside its symbol and reported in {@link Layout#forced}.</li>
 * </ol>
 * Positions are in the logical (unscaled) coordinates of the {@link Canvas}.
 */
final class LabelPlacer {
    static final double CLEAR = 2.5;        // minimum gap between a label box and any symbol
    static final double BOX_GAP = 2.0;      // minimum gap between two label boxes
    static final double LABEL_H = 15;       // box height at 8.5 pt
    private static final double MIN_LEADER = 3.5;   // a leader line is drawn when the box is farther than this from the edge of its symbol
    private static final double HARD = 1e6, SA_OVERLAP = 4000, K_SYMBOL = 1500, K_BOX = 1500, K_LEADER = 600, K_AMBIGUOUS = 40;

    /** Price of a leader line whose box is {@code gap} px from its symbol: grows faster than linearly, so one very long leader costs more than several short ones. */
    static double lenCost(double gap) { return gap + gap * gap / 200.0; }

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

    /** One admissible place of a label: its box, its leader line and the part of its cost that does not depend on the other labels. */
    private static final class Cand {
        double cx, cy, cost, thru; double bx0, by0, bx1, by1; boolean lead; double sx, sy, ex, ey;   // thru: price of the leader running through other symbols (part of cost)
        double gx0, gy0, gx1, gy1;   // bounding box of the box and the leader (what the label grid stores)
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

    /** Uniform grid over rectangles (ids stored in every cell they touch): neighbours of a place are found without looking at all the labels or symbols. */
    private static final class Grid {
        final double ox, oy, cell; final int gx, gy; final int[][] ids; final int[] cnt; final int[] stamp; int mark = 0; final int[] out;
        Grid(double x0, double y0, double x1, double y1, double cell, int maxId) {
            this.cell = cell; ox = x0 - 1500; oy = y0 - 1500; gx = Math.max(1, (int) Math.ceil((x1 + 1500 - ox) / cell)); gy = Math.max(1, (int) Math.ceil((y1 + 1500 - oy) / cell));
            ids = new int[gx * gy][]; cnt = new int[gx * gy]; stamp = new int[Math.max(1, maxId)]; out = new int[Math.max(1, maxId)];
        }
        int ix(double x) { return Math.max(0, Math.min(gx - 1, (int) Math.floor((x - ox) / cell))); }
        int iy(double y) { return Math.max(0, Math.min(gy - 1, (int) Math.floor((y - oy) / cell))); }
        void add(int id, double r0, double s0, double r1, double s1) {
            int a = ix(r0), b = ix(r1), c = iy(s0), d = iy(s1);
            for (int v = c; v <= d; v++) for (int u = a; u <= b; u++) { int k = v * gx + u; int[] l = ids[k]; if (l == null) ids[k] = l = new int[4]; else if (cnt[k] == l.length) ids[k] = l = Arrays.copyOf(l, l.length * 2); l[cnt[k]++] = id; }
        }
        void remove(int id, double r0, double s0, double r1, double s1) {
            int a = ix(r0), b = ix(r1), c = iy(s0), d = iy(s1);
            for (int v = c; v <= d; v++) for (int u = a; u <= b; u++) { int k = v * gx + u; int[] l = ids[k]; for (int q = 0; q < cnt[k]; q++) if (l[q] == id) { l[q] = l[--cnt[k]]; break; } }
        }
        /** Ids stored in a cell the rectangle touches (each once) go to {@link #out}; returns how many. */
        int query(double r0, double s0, double r1, double s1) {
            int a = ix(r0), b = ix(r1), c = iy(s0), d = iy(s1), m = 0; if (++mark == Integer.MAX_VALUE) { Arrays.fill(stamp, 0); mark = 1; }
            for (int v = c; v <= d; v++) for (int u = a; u <= b; u++) { int k = v * gx + u; int[] l = ids[k]; for (int q = 0; q < cnt[k]; q++) { int id = l[q]; if (stamp[id] != mark) { stamp[id] = mark; out[m++] = id; } } }
            return m;
        }
    }

    // ------------------------------------------------------------------ the algorithm
    private final class Solver {
        final int n, m; final double[] w, h, px, py, rad, ox, oy, orad; final int[] own; final Cand[][] ring; final Cand[] cur; final boolean[] forcedAt;
        final Grid symG, labG; final double maxRad, ext; boolean saMode = false; int lastOv = 0; final double[] dirX, dirY;
        Solver(FontMetrics fm) {
            n = texts.size(); m = obs.size(); w = new double[n]; h = new double[n]; px = new double[n]; py = new double[n]; rad = new double[n]; own = new int[n];
            for (int i = 0; i < n; i++) { w[i] = fm.stringWidth(texts.get(i)) + 10; h[i] = LABEL_H; px[i] = lab.get(i)[0]; py[i] = lab.get(i)[1]; rad[i] = lab.get(i)[2]; own[i] = labObs.get(i); }
            ox = new double[m]; oy = new double[m]; orad = new double[m]; double mr = 0; for (int j = 0; j < m; j++) { ox[j] = obs.get(j)[0]; oy[j] = obs.get(j)[1]; orad[j] = obs.get(j)[2]; mr = Math.max(mr, orad[j]); } maxRad = mr;
            ring = new Cand[n][]; cur = new Cand[n]; forcedAt = new boolean[n]; ext = Math.hypot(x1 - x0, y1 - y0);
            symG = new Grid(x0, y0, x1, y1, 48, m); for (int j = 0; j < m; j++) symG.add(j, ox[j], oy[j], ox[j], oy[j]);
            labG = new Grid(x0, y0, x1, y1, 64, n);
            int nd = 36; dirX = new double[nd]; dirY = new double[nd]; for (int k = 0; k < nd; k++) { double th = 2 * Math.PI * k / nd; dirX[k] = Math.cos(th); dirY[k] = -Math.sin(th); }
        }

        // ---- label grid bookkeeping (a label is "placed" while it is in the grid)
        void put(int i, Cand k) { cur[i] = k; labG.add(i, k.gx0, k.gy0, k.gx1, k.gy1); }
        void take(int i) { Cand k = cur[i]; if (k != null) labG.remove(i, k.gx0, k.gy0, k.gx1, k.gy1); }

        /** Centre of the box of label i whose nearest point to the symbol is {@code dd} px from the symbol centre, in direction (ux, uy). */
        void centreAt(int i, double ux, double uy, double dd, double[] outC) {
            double ax = Math.abs(ux), ay = Math.abs(uy), t, tv = ay > 1e-9 ? (dd + h[i] / 2) / ay : Double.POSITIVE_INFINITY, th = ax > 1e-9 ? (dd + w[i] / 2) / ax : Double.POSITIVE_INFINITY;
            if (ax * tv <= w[i] / 2) t = tv;                       // beside the symbol vertically only: the nearest point lies on the top or bottom edge
            else if (ay * th <= h[i] / 2) t = th;                  // horizontally only: on the left or right edge
            else { double B = ax * w[i] + ay * h[i], disc = B * B - 4 * ((w[i] * w[i] + h[i] * h[i]) / 4 - dd * dd); t = (B + Math.sqrt(Math.max(disc, 0))) / 2; }   // a corner
            outC[0] = px[i] + ux * t; outC[1] = py[i] + uy * t;
        }

        /** The place with centre (cx, cy) for label i, or null when it touches a symbol or a blocked rectangle or leaves the panel by more than {@code out} px. Own cost: distance, leader through a symbol, ambiguity. */
        Cand make(int i, double cx, double cy, double out) {
            double bx0 = cx - w[i] / 2, bx1 = cx + w[i] / 2, by0 = cy - h[i] / 2, by1 = cy + h[i] / 2;
            if (bx0 < x0 + 1 - out || bx1 > x1 - 1 + out || by0 < y0 + 1 - out || by1 > y1 - 1 + out) return null;
            for (double[] b : blocks) if (bx0 < b[2] + BOX_GAP && b[0] < bx1 + BOX_GAP && by0 < b[3] + BOX_GAP && b[1] < by1 + BOX_GAP) return null;
            double reach = maxRad + CLEAR; int c = symG.query(bx0 - reach, by0 - reach, bx1 + reach, by1 + reach);
            for (int q = 0; q < c; q++) { int j = symG.out[q]; if (rectDist(bx0, by0, bx1, by1, ox[j], oy[j]) < orad[j] + CLEAR) return null; }
            Cand k = new Cand(); k.cx = cx; k.cy = cy; k.bx0 = bx0; k.by0 = by0; k.bx1 = bx1; k.by1 = by1;
            double d = rectDist(bx0, by0, bx1, by1, px[i], py[i]), gap = d - rad[i]; k.cost = lenCost(Math.max(0, gap)) + 0.08 * Math.hypot(cx - px[i], cy - py[i]);
            k.lead = gap > MIN_LEADER; double lx0 = bx0, ly0 = by0, lx1 = bx1, ly1 = by1;
            if (k.lead) {
                double qx = Math.max(bx0, Math.min(px[i], bx1)), qy = Math.max(by0, Math.min(py[i], by1)), ux = (qx - px[i]) / d, uy = (qy - py[i]) / d;
                k.sx = px[i] + ux * rad[i]; k.sy = py[i] + uy * rad[i]; k.ex = qx; k.ey = qy;
                lx0 = Math.min(lx0, Math.min(k.sx, k.ex)); lx1 = Math.max(lx1, Math.max(k.sx, k.ex)); ly0 = Math.min(ly0, Math.min(k.sy, k.ey)); ly1 = Math.max(ly1, Math.max(k.sy, k.ey));
                int e = symG.query(Math.min(k.sx, k.ex) - reach, Math.min(k.sy, k.ey) - reach, Math.max(k.sx, k.ex) + reach, Math.max(k.sy, k.ey) + reach);
                for (int q = 0; q < e; q++) { int j = symG.out[q]; if (j == own[i] || Math.hypot(ox[j] - k.sx, oy[j] - k.sy) < orad[j]) continue;   // (a symbol the leader already starts in, an overlapping neighbour, cannot be avoided)
                    if (segPointDist(k.sx, k.sy, k.ex, k.ey, ox[j], oy[j]) < orad[j] + 1.0) k.thru += K_SYMBOL * (0.3 + 0.7 * Math.min(1, chord(k.sx, k.sy, k.ex, k.ey, ox[j], oy[j], orad[j]) / (2 * Math.max(orad[j], 1)))); }   // the longer inside it, the dearer
                k.cost += k.thru;
            } else {
                int e = symG.query(bx0 - gap - 1.5 - maxRad, by0 - gap - 1.5 - maxRad, bx1 + gap + 1.5 + maxRad, by1 + gap + 1.5 + maxRad);
                for (int q = 0; q < e; q++) { int j = symG.out[q]; if (j != own[i] && rectDist(bx0, by0, bx1, by1, ox[j], oy[j]) - orad[j] < gap + 1.5) { k.cost += K_AMBIGUOUS; break; } }   // no leader, yet another symbol is as near as its own
            }
            k.gx0 = lx0 - BOX_GAP; k.gy0 = ly0 - BOX_GAP; k.gx1 = lx1 + BOX_GAP; k.gy1 = ly1 + BOX_GAP;
            return k;
        }

        Cand[] rings(int i) {
            if (ring[i] != null) return ring[i];
            final double[] extra = {0, 3, 7, 12, 18, 26, 36, 48, 64, 84, 110, 140, 180, 230, 300, 400, 520, 700}; double[] c = new double[2];
            List<Cand> out = new ArrayList<>();
            for (double e : extra) {
                double dd = rad[i] + CLEAR + 0.5 + e; if (dd > ext) break;
                for (int k = 0; k < dirX.length; k++) { centreAt(i, dirX[k], dirY[k], dd, c); Cand q = make(i, c[0], c[1], 0); if (q != null) out.add(q); }
            }
            return ring[i] = out.toArray(new Cand[0]);
        }

        /** Interaction of two placed labels: >= SA_OVERLAP when their boxes overlap (HARD in the normal mode), else the penalties for a leader through the other box or across the other leader. */
        double pair(Cand k, Cand q) {
            if (k.bx0 < q.bx1 + BOX_GAP && q.bx0 < k.bx1 + BOX_GAP && k.by0 < q.by1 + BOX_GAP && q.by0 < k.by1 + BOX_GAP) return (saMode ? SA_OVERLAP : HARD) + Math.min(k.bx1, q.bx1) - Math.max(k.bx0, q.bx0);
            double c = 0;
            if (k.lead && segRect(k.sx, k.sy, k.ex, k.ey, q.bx0 - 1, q.by0 - 1, q.bx1 + 1, q.by1 + 1)) c += K_BOX;
            if (q.lead && segRect(q.sx, q.sy, q.ex, q.ey, k.bx0 - 1, k.by0 - 1, k.bx1 + 1, k.by1 + 1)) c += K_BOX;
            if (k.lead && q.lead && segSeg(k.sx, k.sy, k.ex, k.ey, q.sx, q.sy, q.ex, q.ey)) c += K_LEADER;
            return c;
        }
        /** Cost of putting label i on k given the labels placed so far (>= HARD when it overlaps a placed label); {@link #lastOv} = number of placed labels it overlaps. */
        double total(int i, Cand k, double bound) {
            double cost = k.cost; lastOv = 0; int c = labG.query(k.gx0, k.gy0, k.gx1, k.gy1);
            for (int q = 0; q < c; q++) { int j = labG.out[q]; if (j == i) continue; double v = pair(k, cur[j]); if (v >= SA_OVERLAP) lastOv++; cost += v; if (cost >= bound) return cost; }
            return cost;
        }
        /** Energy of the whole layout: every label's own cost plus every pair's interaction. */
        double energy() {
            double e = 0; for (int i = 0; i < n; i++) { Cand k = cur[i]; e += k.cost; int c = labG.query(k.gx0, k.gy0, k.gx1, k.gy1); for (int q = 0; q < c; q++) { int j = labG.out[q]; if (j > i) e += pair(k, cur[j]); } } return e;
        }
        /** Label pairs whose boxes overlap, and labels with a leader conflict (through a symbol, a label, or across another leader). */
        int[] conflicts(boolean[] bad) {
            int ov = 0, cf = 0; if (bad != null) Arrays.fill(bad, false);
            for (int i = 0; i < n; i++) { Cand k = cur[i]; if (k.thru > 0) { cf++; if (bad != null) bad[i] = true; }
                int c = labG.query(k.gx0, k.gy0, k.gx1, k.gy1);
                for (int q = 0; q < c; q++) { int j = labG.out[q]; if (j <= i) continue; double v = pair(k, cur[j]); if (v >= SA_OVERLAP) ov++; else if (v > 0) { cf++; if (bad != null) bad[i] = bad[j] = true; } } }
            return new int[]{ov, cf};
        }

        /** Cheapest place for label i among the rings (and, when those are all taken, the panel scan); {@code keep} (its current place, if any) wins ties. Label i must not be in the grid. */
        Cand best(int i, Cand keep) { return best(i, keep, true); }
        Cand best(int i, Cand keep, boolean allowScan) {
            Cand best = keep; double bc = keep == null ? Double.MAX_VALUE : total(i, keep, Double.MAX_VALUE);
            for (Cand k : rings(i)) { double c = total(i, k, bc); if (c < bc - 1e-9) { bc = c; best = k; } }
            if (allowScan && (best == null || bc >= HARD)) { Cand g = scan(i, bc); if (g != null) best = g; }
            if (best == null) {   // not a single place is free of symbols: put the label beside its symbol (reported by Layout.forced)
                Cand f = make(i, Math.max(x0 + w[i] / 2, Math.min(x1 - w[i] / 2, px[i] + rad[i] + w[i] / 2 + CLEAR)), Math.max(y0 + h[i] / 2, Math.min(y1 - h[i] / 2, py[i])), 1e9);
                if (f == null) { f = new Cand(); f.cx = px[i] + rad[i] + w[i] / 2 + CLEAR; f.cy = py[i]; f.bx0 = f.cx - w[i] / 2; f.bx1 = f.cx + w[i] / 2; f.by0 = f.cy - h[i] / 2; f.by1 = f.cy + h[i] / 2; f.gx0 = f.bx0; f.gx1 = f.bx1; f.gy0 = f.by0; f.gy1 = f.by1; }
                forcedAt[i] = true; best = f;
            }
            return best;
        }
        /** Fallback: every position on a grid over the panel (coarse, then fine; then over a band around the panel); the cheapest one that beats {@code bound}, or null. */
        Cand scan(int i, double bound) {
            Cand best = null; double bc = bound;
            for (double band : new double[]{0, 60, 160}) for (double step : new double[]{12, 4}) {
                for (double cy = y0 - band + h[i] / 2 + 1; cy <= y1 + band - h[i] / 2 - 1; cy += step) for (double cx = x0 - band + w[i] / 2 + 1; cx <= x1 + band - w[i] / 2 - 1; cx += step) {
                    Cand k = make(i, cx, cy, band); if (k == null || k.cost >= bc) continue; double c = total(i, k, bc); if (c < bc - 1e-9) { bc = c; best = k; } }
                if (best != null && bc < HARD) return best;
            }
            return best;
        }

        /** Re-places each label (given all the others) and exchanges pairs of labels, until nothing improves. */
        void refine(Integer[] order) {
            int passes = n <= 40 ? 25 : n <= 100 ? 10 : 5;   // (the annealing that follows does the heavy lifting for big plots)
            for (int pass = 0; pass < passes; pass++) {
                boolean changed = false;
                for (int i : order) { Cand old = cur[i]; take(i); Cand nw = best(i, old, pass < 2 || old == null); if (nw == null) nw = best(i, null, true); put(i, nw); if (nw != old) changed = true; }   // the (slow) panel scan only in the first passes
                if (swaps()) changed = true;
                if (!changed) break;
            }
        }
        /** Exchanges the places of two nearby labels (their box centres) when that lowers the energy; true if any exchange was made. */
        boolean swaps() {
            boolean any = false;
            for (int i = 0; i < n; i++) {
                Cand ci = cur[i]; int c = labG.query(ci.cx - 220, ci.cy - 220, ci.cx + 220, ci.cy + 220); int[] near = Arrays.copyOf(labG.out, c);
                for (int j : near) {
                    if (j <= i) continue; Cand a0 = cur[i], b0 = cur[j]; if (Math.abs(a0.cx - b0.cx) > 220 || Math.abs(a0.cy - b0.cy) > 220) continue;
                    Cand a = make(i, b0.cx, b0.cy, 0), b = make(j, a0.cx, a0.cy, 0); if (a == null || b == null) continue;
                    take(i); take(j); double eo = total(i, a0, Double.MAX_VALUE) + total(j, b0, Double.MAX_VALUE) + pair(a0, b0), en = total(i, a, Double.MAX_VALUE) + total(j, b, Double.MAX_VALUE) + pair(a, b);
                    if (en < eo - 1e-6) { put(i, a); put(j, b); any = true; } else { put(i, a0); put(j, b0); } }
            }
            return any;
        }

        /** Greedy placement in the given order (every label is taken out of the grid first), then refine. */
        void once(Integer[] order) {
            for (int i = 0; i < n; i++) { take(i); cur[i] = null; }
            for (int i : order) put(i, best(i, null));
            refine(order);
        }
        void setState(Cand[] s) { for (int i = 0; i < n; i++) take(i); for (int i = 0; i < n; i++) put(i, s[i]); }

        /** Simulated annealing from the current layout: a label jumps to a random place clear of every symbol; the move is kept when it lowers the energy, or by chance while the
         *  temperature is high. Overlapping labels are only a (heavy) cost during the run; the state with the fewest overlaps and then the lowest energy is restored at the end. */
        void anneal(long seed, int iters, double T0) {
            Random rnd = new Random(seed); saMode = true; double E = energy(); int ov = conflicts(null)[0]; Cand[] bestS = cur.clone(); int bestOv = ov; double bestE = E;
            double T1 = 0.5, scaleMax = Math.max(30, Math.min(260, ext / 3)); boolean[] bad = new boolean[n]; int[] badList = new int[n]; int nb = 0; double[] c = new double[2];
            for (int k = 0; k < iters; k++) {
                if (k % 2000 == 0) { conflicts(bad); nb = 0; for (int i = 0; i < n; i++) if (bad[i]) badList[nb++] = i; }
                double T = T0 * Math.pow(T1 / T0, (double) k / iters); int i = nb > 0 && rnd.nextInt(10) < 7 ? badList[rnd.nextInt(nb)] : rnd.nextInt(n);   // most moves go to labels in conflict
                double prog = (double) k / iters, sc = Math.max(24, scaleMax * Math.pow(1 - prog, 1.5));   // long jumps early (to leave a crowded cluster), short ones late (to settle)
                double th = rnd.nextDouble() * 2 * Math.PI, gap = 2.5 + -Math.log(1 - rnd.nextDouble()) * Math.exp(Math.log(3) + rnd.nextDouble() * Math.log(sc / 3));
                centreAt(i, Math.cos(th), Math.sin(th), rad[i] + gap, c); Cand nw = make(i, c[0], c[1], 0); if (nw == null) continue;
                Cand old = cur[i]; take(i); double eo = total(i, old, Double.MAX_VALUE); int oo = lastOv, en = 0; double enew = total(i, nw, Double.MAX_VALUE); int on = lastOv; double d = enew - eo;
                if (d <= 0 || rnd.nextDouble() < Math.exp(-d / T)) { put(i, nw); E += d; ov += on - oo; if (ov < bestOv || (ov == bestOv && E < bestE - 1e-9)) { bestOv = ov; bestE = E; bestS = cur.clone(); } } else put(i, old);
            }
            saMode = false; setState(bestS);
        }

        Layout run() {
            Integer[] order = new Integer[n]; int[] crowd = new int[n];
            for (int i = 0; i < n; i++) { order[i] = i; int c = symG.query(px[i] - 60 - maxRad, py[i] - 60 - maxRad, px[i] + 60 + maxRad, py[i] + 60 + maxRad); for (int q = 0; q < c; q++) { int j = symG.out[q]; if (j != own[i] && Math.hypot(ox[j] - px[i], oy[j] - py[i]) < 60 + orad[j]) crowd[i]++; } }
            Arrays.sort(order, (a, b) -> crowd[b] != crowd[a] ? Integer.compare(crowd[b], crowd[a]) : Integer.compare(a, b));   // most crowded first
            once(order); Cand[] bestS = cur.clone(); double bestE = energy(); int[] cf = conflicts(null);
            if (cf[1] > 0 || cf[0] > 0) {   // a leader still crosses another leader / runs through a label or a symbol (or labels overlap): shaken restarts, then annealing
                Random rnd = new Random(12345); int starts = n <= 30 ? 6 : n <= 60 ? 2 : 0;   // (a restart costs a full greedy pass, annealing iterations are far cheaper: big plots rely on the latter)
                for (int s = 0; s < starts; s++) {
                    Integer[] ord = order.clone(); for (int i = ord.length - 1; i > 0; i--) { int j = rnd.nextInt(i + 1); Integer t = ord[i]; ord[i] = ord[j]; ord[j] = t; }
                    once(ord); double e = energy(); if (e < bestE - 1e-9) { bestE = e; bestS = cur.clone(); } }
                setState(bestS); cf = conflicts(null);
                int iters = Math.max(250000, Math.min(500000, n * 15000));
                for (int run = 0; run < 3 && (cf[1] > 0 || cf[0] > 0); run++) {
                    anneal(1234L + 77 * run, iters, run == 0 ? 300 : run == 1 ? 100 : 40); refine(order); double e = energy(); int[] c2 = conflicts(null);
                    boolean better = c2[0] < cf[0] || (c2[0] == cf[0] && e < bestE - 1e-9); if (better) { bestE = e; bestS = cur.clone(); } else setState(bestS);
                    boolean fewer = c2[0] + c2[1] < cf[0] + cf[1]; cf = better ? c2 : cf; if (!fewer) break; }   // a further run only while the previous one still removed conflicts
                setState(bestS);
            }
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
