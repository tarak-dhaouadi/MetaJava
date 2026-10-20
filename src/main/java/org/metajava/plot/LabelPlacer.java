package org.metajava.plot;

import org.metajava.core.Par;
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
 * Order of the objectives (a lower one is never bought with a higher one): (1) no overlapping labels, (2) no leader line through a symbol that it could avoid,
 * (3) no leader through a label or across another leader, (4) short leader lines. A leader that starts inside a symbol that overlaps its own (overlapping bubbles)
 * has to leave that symbol: the shortest way out is unavoidable and free, only the part of the leader inside it beyond that shortest way counts as avoidable.
 * <p>
 * Method (no limit on the number of labels or iterations, no clock: the same data always gives the same layout, whatever the number of CPU threads):
 * <ol>
 * <li>Candidate places on rings of growing distance around each symbol (36 directions); a place that touches a symbol or a blocked rectangle is dropped for good.
 *     Symbols and placed labels live in spatial grids, so testing a place costs the same however many labels the plot has.</li>
 * <li>Greedy placement, most crowded symbol first, each label on the cheapest admissible place (distance, with a price that grows faster than linearly so one very long leader
 *     is dearer than several short ones; leader through a symbol; leader through another label; leader across another leader), then re-placement of each label and exchange of
 *     pairs of labels until nothing improves.</li>
 * <li>While a leader still runs through a symbol, through a label or across another leader (or labels overlap): cycles of {@link #CHAINS} independent simulated-annealing chains
 *     (labels jump to random free spots; worse moves are accepted with a falling probability), half of them from the best layout so far and half from a greedy restart in a
 *     shaken order, each followed by the re-placement of step 2. The chains of a cycle run in parallel (the number of chains does not depend on the number of CPU threads,
 *     so the result does not either). The best layout of a cycle replaces the best so far when it is better; cycles go on until {@link #STALLS} cycles in a row have not reduced
 *     the number of conflicts. A chain stops as soon as its layout is free of conflicts. The annealing may let labels overlap while it moves, but only a state without overlap
 *     is ever returned. Nothing is capped: neither iterations nor time; a superseded analysis is cancelled through {@link Par#checkCancel}.</li>
 * <li>A label with no admissible ring place falls back to a scan of the panel (coarse, then fine, then a band around it); as the last resort (no spot free of symbols at all)
 *     it is put beside its symbol and reported in {@link Layout#forced}.</li>
 * </ol>
 * All the arithmetic is exact IEEE arithmetic or {@link StrictMath}, so the layout does not depend on the Java version or the CPU.
 * Positions are in the logical (unscaled) coordinates of the {@link Canvas}.
 */
final class LabelPlacer {
    static final double CLEAR = 2.5;        // minimum gap between a label box and any symbol
    static final double BOX_GAP = 2.0;      // minimum gap between two label boxes
    static final double LABEL_H = 15;       // box height at 8.5 pt
    static final double THRU_TOL = 1.0;     // px of a leader inside an overlapping symbol, beyond the shortest way out, that is still not counted as avoidable
    static final int CHAINS = 4;   // annealing chains per cycle (fixed: the result must not depend on the number of CPU threads)
    private static final double MIN_LEADER = 3.5;   // a leader line is drawn when the box is farther than this from the edge of its symbol
    private static final double HARD = 1e6, SA_OVERLAP = 4000, K_THRU = 8000, K_BOX = 1500, K_LEADER = 600, K_AMBIGUOUS = 40;
    private static final int ITER_PER_LABEL = 3000, ITER_MIN = 40000;   // length of one annealing run (not a limit: runs continue while they make progress)   // length of one annealing chain: proportional to the number of labels (no upper limit)

    /** Price of a leader line whose box is {@code gap} px from its symbol: grows faster than linearly, so one very long leader costs more than several short ones. */
    static double lenCost(double gap) { return gap + gap * gap / 200.0; }
    private static double hyp(double dx, double dy) { return Math.sqrt(dx * dx + dy * dy); }   // exact (correctly rounded) on every platform, unlike Math.hypot

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

    /** One admissible place of a label: its box, its leader line and the part of its cost that does not depend on the other labels. Immutable once made (shared by the chains). */
    private static final class Cand {
        double cx, cy, cost, thru; double bx0, by0, bx1, by1; boolean lead; double sx, sy, ex, ey;   // thru: price of the leader running through other symbols (part of cost)
        double gx0, gy0, gx1, gy1;   // bounding box of the box and the leader (what the label grid stores)
        int thruN; boolean forced;   // thruN: symbols the leader runs through although it could avoid them; forced: last-resort place (no spot free of symbols)
    }
    /** A complete layout and its quality: overlapping pairs, avoidable leader-through-symbol, other conflicts (leader through a label / across a leader / ...), energy. */
    private static final class Res {
        Cand[] s; int ov, tn, cf; double e;
        /** Lower is better: overlaps, then avoidable symbol crossings, then the energy (which also prices the other conflicts and the distances). */
        boolean beats(Res o) { return ov != o.ov ? ov < o.ov : tn != o.tn ? tn < o.tn : e < o.e - 1e-9; }
        /** The conflict counts (what the search tries to reduce), lexicographically. */
        boolean fewerConflictsThan(int[] k) { return ov != k[0] ? ov < k[0] : tn != k[1] ? tn < k[1] : cf < k[2]; }
        int[] key() { return new int[]{ov, tn, cf}; }
        boolean clean() { return ov == 0 && cf == 0 && tn == 0; }
    }

    // ------------------------------------------------------------------ geometry
    private static double rectDist(double bx0, double by0, double bx1, double by1, double qx, double qy) { return hyp(Math.max(Math.max(bx0 - qx, qx - bx1), 0), Math.max(Math.max(by0 - qy, qy - by1), 0)); }
    private static double segPointDist(double ax, double ay, double bx, double by, double qx, double qy) {
        double dx = bx - ax, dy = by - ay, l2 = dx * dx + dy * dy, t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((qx - ax) * dx + (qy - ay) * dy) / l2)); return hyp(ax + t * dx - qx, ay + t * dy - qy);
    }
    /** Length of the part of segment (a, b) inside the circle (c, r). */
    private static double chord(double ax, double ay, double bx, double by, double cx, double cy, double r) {
        double dx = bx - ax, dy = by - ay, l = hyp(dx, dy); if (l == 0) return 0; double fx = ax - cx, fy = ay - cy, A = l * l, B = 2 * (fx * dx + fy * dy), C = fx * fx + fy * fy - r * r, D = B * B - 4 * A * C;
        if (D <= 0) return 0; double q = Math.sqrt(D), t1 = Math.max(0, (-B - q) / (2 * A)), t2 = Math.min(1, (-B + q) / (2 * A)); return t2 > t1 ? (t2 - t1) * l : 0;
    }
    /**
     * Length of the leader (a, b) inside the symbol (c, r) that it could have avoided. A leader that starts outside the symbol could avoid all of it. A leader that starts inside
     * (the symbol overlaps the one the leader belongs to) has to leave it: the shortest way out, r - |a - c|, is unavoidable; only the excess length is avoidable.
     * Returns 0 when the leader does not meet the symbol or the excess is within {@link #THRU_TOL}.
     */
    static double avoidableInside(double ax, double ay, double bx, double by, double cx, double cy, double r) {
        double ch = chord(ax, ay, bx, by, cx, cy, r); if (ch <= 0) return 0;
        double d0 = hyp(ax - cx, ay - cy); if (d0 >= r) return ch;          // starts outside: all of it
        double excess = ch - (r - d0); return excess > THRU_TOL ? excess : 0;   // starts inside: only what exceeds the shortest way out
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
        /** A reader of a grid that is no longer modified (the symbols): shares its contents, has its own scratch space, so several threads can query "the same" grid. */
        Grid(Grid g) { ox = g.ox; oy = g.oy; cell = g.cell; gx = g.gx; gy = g.gy; ids = g.ids; cnt = g.cnt; stamp = new int[g.stamp.length]; out = new int[g.out.length]; }
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
        final int n, m; final double[] w, h, px, py, rad, ox, oy, orad; final int[] own; final Cand[][] ring; final Cand[] cur;
        final Grid symG, labG; final double maxRad, ext; boolean saMode = false; int lastOv = 0; final double[] dirX, dirY;
        Solver(FontMetrics fm) {
            n = texts.size(); m = obs.size(); w = new double[n]; h = new double[n]; px = new double[n]; py = new double[n]; rad = new double[n]; own = new int[n];
            for (int i = 0; i < n; i++) { w[i] = fm.stringWidth(texts.get(i)) + 10; h[i] = LABEL_H; px[i] = lab.get(i)[0]; py[i] = lab.get(i)[1]; rad[i] = lab.get(i)[2]; own[i] = labObs.get(i); }
            ox = new double[m]; oy = new double[m]; orad = new double[m]; double mr = 0; for (int j = 0; j < m; j++) { ox[j] = obs.get(j)[0]; oy[j] = obs.get(j)[1]; orad[j] = obs.get(j)[2]; mr = Math.max(mr, orad[j]); } maxRad = mr;
            ring = new Cand[n][]; cur = new Cand[n]; ext = hyp(x1 - x0, y1 - y0);
            symG = new Grid(x0, y0, x1, y1, 48, m); for (int j = 0; j < m; j++) symG.add(j, ox[j], oy[j], ox[j], oy[j]);
            labG = new Grid(x0, y0, x1, y1, 64, n);
            int nd = 36; dirX = new double[nd]; dirY = new double[nd]; for (int k = 0; k < nd; k++) { double th = 2 * Math.PI * k / nd; dirX[k] = StrictMath.cos(th); dirY[k] = -StrictMath.sin(th); }
        }
        /** A worker copy: the same input (shared, read-only), its own state. Chains running in different threads each work on one. */
        private Solver(Solver b) {
            n = b.n; m = b.m; w = b.w; h = b.h; px = b.px; py = b.py; rad = b.rad; ox = b.ox; oy = b.oy; orad = b.orad; own = b.own; ring = b.ring; maxRad = b.maxRad; ext = b.ext; dirX = b.dirX; dirY = b.dirY;
            cur = new Cand[n]; symG = new Grid(b.symG); labG = new Grid(x0, y0, x1, y1, 64, n);
        }
        Solver fork() { return new Solver(this); }

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
            double d = rectDist(bx0, by0, bx1, by1, px[i], py[i]), gap = d - rad[i]; k.cost = lenCost(Math.max(0, gap)) + 0.08 * hyp(cx - px[i], cy - py[i]);
            k.lead = gap > MIN_LEADER; double lx0 = bx0, ly0 = by0, lx1 = bx1, ly1 = by1;
            if (k.lead) {
                double qx = Math.max(bx0, Math.min(px[i], bx1)), qy = Math.max(by0, Math.min(py[i], by1)), ux = (qx - px[i]) / d, uy = (qy - py[i]) / d;
                k.sx = px[i] + ux * rad[i]; k.sy = py[i] + uy * rad[i]; k.ex = qx; k.ey = qy;
                lx0 = Math.min(lx0, Math.min(k.sx, k.ex)); lx1 = Math.max(lx1, Math.max(k.sx, k.ex)); ly0 = Math.min(ly0, Math.min(k.sy, k.ey)); ly1 = Math.max(ly1, Math.max(k.sy, k.ey));
                int e = symG.query(Math.min(k.sx, k.ex) - reach, Math.min(k.sy, k.ey) - reach, Math.max(k.sx, k.ex) + reach, Math.max(k.sy, k.ey) + reach);
                for (int q = 0; q < e; q++) { int j = symG.out[q]; if (j == own[i]) continue;
                    if (segPointDist(k.sx, k.sy, k.ex, k.ey, ox[j], oy[j]) >= orad[j] + 1.0) continue;   // clear of it by more than a pixel
                    boolean graze = segPointDist(k.sx, k.sy, k.ex, k.ey, ox[j], oy[j]) >= orad[j];       // within a pixel of the edge without entering
                    double av = graze ? 0 : avoidableInside(k.sx, k.sy, k.ex, k.ey, ox[j], oy[j], orad[j]);
                    if (!graze && av <= 0) continue;   // enters a symbol that overlaps its own, by no more than the shortest way out: unavoidable, free
                    k.thru += K_THRU * (0.5 + 0.5 * Math.min(1, av / (2 * Math.max(orad[j], 1)))); k.thruN++; }   // the longer inside it, the dearer (a graze costs half)
                k.cost += k.thru;
            } else {
                int e = symG.query(bx0 - gap - 1.5 - maxRad, by0 - gap - 1.5 - maxRad, bx1 + gap + 1.5 + maxRad, by1 + gap + 1.5 + maxRad);
                for (int q = 0; q < e; q++) { int j = symG.out[q]; if (j != own[i] && rectDist(bx0, by0, bx1, by1, ox[j], oy[j]) - orad[j] < gap + 1.5) { k.cost += K_AMBIGUOUS; break; } }   // no leader, yet another symbol is as near as its own
            }
            k.gx0 = lx0 - BOX_GAP; k.gy0 = ly0 - BOX_GAP; k.gx1 = lx1 + BOX_GAP; k.gy1 = ly1 + BOX_GAP;
            return k;
        }

        Cand[] computeRings(int i) {
            final double[] extra = {0, 3, 7, 12, 18, 26, 36, 48, 64, 84, 110, 140, 180, 230, 300, 400, 520, 700}; double[] c = new double[2];
            List<Cand> out = new ArrayList<>();
            for (double e : extra) {
                double dd = rad[i] + CLEAR + 0.5 + e; if (dd > ext) break;
                for (int k = 0; k < dirX.length; k++) { centreAt(i, dirX[k], dirY[k], dd, c); Cand q = make(i, c[0], c[1], 0); if (q != null) out.add(q); }
            }
            return out.toArray(new Cand[0]);
        }
        /** The ring places of every label, computed once, in parallel (each label is independent of the others, so the result does not depend on how the work is split). */
        void prepareRings() {
            final int parts = Math.min(n, 8); if (parts <= 0) return;
            Par.forRange(parts, p -> { Solver f = fork(); for (int i = p; i < n; i += parts) ring[i] = f.computeRings(i); });
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
        /** {overlapping pairs, conflicts (labels whose leader runs through an avoidable symbol, pairs with a leader through a label or across a leader), avoidable symbol crossings}; {@code bad} marks the labels in conflict. */
        int[] conflicts(boolean[] bad) {
            int ov = 0, cf = 0, tn = 0; if (bad != null) Arrays.fill(bad, false);
            for (int i = 0; i < n; i++) { Cand k = cur[i]; tn += k.thruN; if (k.thru > 0) { cf++; if (bad != null) bad[i] = true; }
                int c = labG.query(k.gx0, k.gy0, k.gx1, k.gy1);
                for (int q = 0; q < c; q++) { int j = labG.out[q]; if (j <= i) continue; double v = pair(k, cur[j]); if (v >= SA_OVERLAP) ov++; else if (v > 0) { cf++; if (bad != null) bad[i] = bad[j] = true; } } }
            return new int[]{ov, cf, tn};
        }
        Res snapshot() { int[] c = conflicts(null); Res r = new Res(); r.s = cur.clone(); r.ov = c[0]; r.cf = c[1]; r.tn = c[2]; r.e = energy(); return r; }

        /** Cheapest place for label i among the rings (and, when those are all taken, the panel scan); {@code keep} (its current place, if any) wins ties. Label i must not be in the grid. */
        Cand best(int i, Cand keep) {
            Cand best = keep; double bc = keep == null ? Double.MAX_VALUE : total(i, keep, Double.MAX_VALUE);
            for (Cand k : ring[i]) { double c = total(i, k, bc); if (c < bc - 1e-9) { bc = c; best = k; } }
            if (best == null || bc >= HARD) { Cand g = scan(i, bc); if (g != null) best = g; }
            if (best == null) {   // not a single place is free of symbols: put the label beside its symbol (reported by Layout.forced)
                Cand f = make(i, Math.max(x0 + w[i] / 2, Math.min(x1 - w[i] / 2, px[i] + rad[i] + w[i] / 2 + CLEAR)), Math.max(y0 + h[i] / 2, Math.min(y1 - h[i] / 2, py[i])), 1e9);
                if (f == null) { f = new Cand(); f.cx = px[i] + rad[i] + w[i] / 2 + CLEAR; f.cy = py[i]; f.bx0 = f.cx - w[i] / 2; f.bx1 = f.cx + w[i] / 2; f.by0 = f.cy - h[i] / 2; f.by1 = f.cy + h[i] / 2; f.gx0 = f.bx0; f.gx1 = f.bx1; f.gy0 = f.by0; f.gy1 = f.by1; }
                f.forced = true; best = f;
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

        /** Re-places each label (given all the others) and exchanges pairs of labels, until nothing improves (every change lowers the energy by a fixed minimum, so this ends). */
        void refine(Integer[] order) {
            for (boolean changed = true; changed; ) {
                changed = false; Par.checkCancel();
                for (int i : order) { Cand old = cur[i]; take(i); Cand nw = best(i, old); put(i, nw); if (nw != old) changed = true; }
                if (swaps()) changed = true;
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
         *  temperature is high. Overlapping labels are only a (heavy) cost during the run. Ends with the layout free of conflicts as soon as there is one, else with the best layout seen
         *  (fewest overlaps, then fewest avoidable symbol crossings, then lowest energy). */
        void anneal(long seed, int iters, double T0) {
            Random rnd = new Random(seed); saMode = true; double E = energy(); int[] c0 = conflicts(null); int ov = c0[0], tn = c0[2];
            Cand[] bestS = cur.clone(); int bestOv = ov, bestTn = tn; double bestE = E; boolean done = false;
            double T1 = 0.5, scaleMax = Math.max(30, Math.min(260, ext / 3)); boolean[] bad = new boolean[n]; int[] badList = new int[n]; int nb = 0; double[] c = new double[2];
            for (int k = 0; k < iters && !done; k++) {
                if (k % 2000 == 0) { Par.checkCancel(); int[] cc = conflicts(bad); if (cc[0] == 0 && cc[1] == 0) { bestS = cur.clone(); done = true; break; }
                    nb = 0; for (int i = 0; i < n; i++) if (bad[i]) badList[nb++] = i; }
                double T = T0 * StrictMath.pow(T1 / T0, (double) k / iters); int i = nb > 0 && rnd.nextInt(10) < 7 ? badList[rnd.nextInt(nb)] : rnd.nextInt(n);   // most moves go to labels in conflict
                double prog = (double) k / iters, sc = Math.max(24, scaleMax * StrictMath.pow(1 - prog, 1.5));   // long jumps early (to leave a crowded cluster), short ones late (to settle)
                double th = rnd.nextDouble() * 2 * Math.PI, gap = 2.5 + -StrictMath.log(1 - rnd.nextDouble()) * StrictMath.exp(StrictMath.log(3) + rnd.nextDouble() * StrictMath.log(sc / 3));
                centreAt(i, StrictMath.cos(th), StrictMath.sin(th), rad[i] + gap, c); Cand nw = make(i, c[0], c[1], 0); if (nw == null) continue;
                Cand old = cur[i]; take(i); double eo = total(i, old, Double.MAX_VALUE); int oo = lastOv; double enew = total(i, nw, Double.MAX_VALUE); int on = lastOv; double d = enew - eo;
                if (d <= 0 || rnd.nextDouble() < StrictMath.exp(-d / T)) { put(i, nw); E += d; ov += on - oo; tn += nw.thruN - old.thruN;
                    if (ov < bestOv || (ov == bestOv && (tn < bestTn || (tn == bestTn && E < bestE - 1e-9)))) { bestOv = ov; bestTn = tn; bestE = E; bestS = cur.clone(); } } else put(i, old);
            }
            saMode = false; setState(bestS);
        }

        /** One annealing chain: from the given layout, or (fresh) from a greedy restart in a shaken order. It then anneals in runs (each from the best layout so far, with new random numbers,
         *  followed by the re-placement of the labels) for as long as a run still reduces the number of conflicts below the lowest seen; it ends with a conflict-free layout or after the first run
         *  that brought no progress. The length of a run is fixed by the number of labels; the number of runs is not limited. */
        Res chain(Cand[] start, Integer[] order, long seed, boolean fresh, double T0, int iters) {
            if (fresh) { Random rnd = new Random(seed ^ 0x5DEECE66DL); Integer[] ord = order.clone(); for (int i = ord.length - 1; i > 0; i--) { int j = rnd.nextInt(i + 1); Integer t = ord[i]; ord[i] = ord[j]; ord[j] = t; } once(ord); }
            else setState(start);
            Res best = snapshot(); int[] seen = best.key();
            for (int run = 0; !best.clean(); run++) {
                anneal(seed + 104729L * run, iters, T0); refine(order); Res s = snapshot();
                boolean progress = s.fewerConflictsThan(seen); if (progress) seen = s.key();
                if (s.beats(best)) best = s;
                if (!progress) break;
                setState(best.s);
            }
            return best;
        }

        Layout run() {
            prepareRings();
            Integer[] order = new Integer[n]; int[] crowd = new int[n];
            for (int i = 0; i < n; i++) { order[i] = i; int c = symG.query(px[i] - 60 - maxRad, py[i] - 60 - maxRad, px[i] + 60 + maxRad, py[i] + 60 + maxRad); for (int q = 0; q < c; q++) { int j = symG.out[q]; if (j != own[i] && hyp(ox[j] - px[i], oy[j] - py[i]) < 60 + orad[j]) crowd[i]++; } }
            Arrays.sort(order, (a, b) -> crowd[b] != crowd[a] ? Integer.compare(crowd[b], crowd[a]) : Integer.compare(a, b));   // most crowded first
            once(order); Res best = snapshot();
            if (!best.clean()) {   // a leader still runs through a symbol / a label or across another leader (or labels overlap): cycles of annealing chains
                final int iters = Math.max(ITER_MIN, ITER_PER_LABEL * n); int[] key = best.key();
                for (int cycle = 0; !best.clean(); cycle++) {
                    final Cand[] startS = best.s; final long base = 1234L + 7919L * cycle; final Res[] res = new Res[CHAINS];
                    Par.forRange(CHAINS, ch -> res[ch] = fork().chain(startS, order, base + 77L * ch, ch >= CHAINS / 2, ch % 2 == 0 ? 300 : 100, iters));
                    Res pick = res[0]; for (int ch = 1; ch < CHAINS; ch++) if (res[ch].beats(pick)) pick = res[ch];   // (ties: the lowest chain number, whatever the thread timing)
                    if (pick.beats(best)) best = pick;
                    boolean progress = false; for (Res r : res) if (r.fewerConflictsThan(key)) { key = r.key(); progress = true; }
                    if (!progress) break;
                }
                setState(best.s);
            }
            double[][] sym = new double[m][]; for (int j = 0; j < m; j++) sym[j] = new double[]{ox[j], oy[j], orad[j]};
            Layout L = new Layout(n, sym, blocks.toArray(new double[0][]), own.clone());
            for (int i = 0; i < n; i++) { Cand k = cur[i]; if (k.forced) L.forced++; L.cx[i] = k.cx; L.cy[i] = k.cy; L.w[i] = w[i]; L.h[i] = h[i]; L.px[i] = px[i]; L.py[i] = py[i]; L.rad[i] = rad[i]; L.leader[i] = k.lead; if (k.lead) L.seg[i] = new double[]{k.sx, k.sy, k.ex, k.ey}; }
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
