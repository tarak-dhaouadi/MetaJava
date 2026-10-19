package org.metajava;

import org.metajava.core.*;
import java.util.*;

/** Sections made of several parallel loops (GOSH + cluster detection, meta-regression with permutation test + model comparison, plot rendering) report one steadily rising fraction:
 *  no falling back to 0 between the steps, and 100 % at the end. Run: java -cp build/classes:build/test-classes org.metajava.PhaseProgressTest */
public class PhaseProgressTest {
    static int fails = 0;
    static void check(boolean ok, String what) { if (!ok) { fails++; System.out.println("FAIL: " + what); } else System.out.println("ok:   " + what); }

    /** largest drop of the fraction from the highest value seen so far (threads may report a few iterations out of order, so a small tolerance is allowed). */
    static double maxDrop(List<double[]> ev) {
        List<double[]> e = new ArrayList<>(ev); e.sort(Comparator.comparingDouble(a -> a[0])); double hi = 0, drop = 0; for (double[] x : e) { hi = Math.max(hi, x[1]); drop = Math.max(drop, hi - x[1]); } return drop;
    }

    public static void main(String[] args) {
        int k = 16; double[] yi = new double[k], se = new double[k], x = new double[k]; String[] f = new String[k];
        for (int i = 0; i < k; i++) { yi[i] = 0.3 + 0.4 * Math.sin(1.7 * i + 0.3) + 0.01 * i; se[i] = 0.15 + 0.1 * ((i * 7) % 5) / 5.0; x[i] = (i * 37) % 11; f[i] = i % 3 == 0 ? "A" : i % 3 == 1 ? "B" : "C"; }
        MetaSpec sp = new MetaSpec(); sp.type = "gen"; sp.sm = "MD"; sp.methodTau = "REML"; MetaData md = new MetaData(); md.put("TE", yi); md.put("seTE", se); md.n = k;
        final MetaResult r = Meta.run(sp, md);

        // GOSH section as the GUI runs it: 90 % computation (run 0..w, clusters w..1), then 10 % rendering
        final List<double[]> ev = Collections.synchronizedList(new ArrayList<>()); final long t0 = System.nanoTime(); final double w = 0.25;
        Par.withProgress(fr -> ev.add(new double[]{System.nanoTime() - t0, fr}), () -> {
            Par.phase(0, 0.9, () -> { final Gosh[] g = new Gosh[1]; Par.phase(0, w, () -> g[0] = Gosh.run(r, 100000, 1)); Par.phase(w, 1, () -> g[0].detectClusters()); });
            Par.nextPhase(0.9, 1); Par.forRange(2, i -> { });   // stand-in for rendering two plots
        });
        double mx = 0; for (double[] a : ev) mx = Math.max(mx, a[1]);
        check(maxDrop(ev) < 0.03, "GOSH + clusters: the fraction never falls back (largest drop " + maxDrop(ev) + ")"); check(mx > 0.999, "GOSH + clusters: ends at 100 % (" + mx + ")");
        double atBoundary = 0; for (double[] a : ev) if (a[1] < 0.9 * w + 0.01 && a[1] > atBoundary) atBoundary = a[1];
        check(atBoundary > 0.2, "GOSH: the subsets part fills its share before the clusters start (" + atBoundary + ")");

        // meta-regression section: exact permutation test (95 %) then model comparison (5 %), all in the 90 % computation phase
        final int k2 = 11;   // 11! / (4! 4! 3!) = 11,550 unique permutations of the factor
        MetaData md2 = new MetaData(); md2.put("TE", Arrays.copyOf(yi, k2)); md2.put("seTE", Arrays.copyOf(se, k2)); md2.n = k2; final MetaResult r2 = Meta.run(sp, md2);
        Map<String, double[]> nd = new HashMap<>(); nd.put("x", Arrays.copyOf(x, k2)); Map<String, String[]> fd = new HashMap<>(); fd.put("f", Arrays.copyOf(f, k2));
        Reg g = Reg.run(r2, new ArrayList<>(), nd, List.of("f"), fd, true);
        final List<double[]> ev2 = Collections.synchronizedList(new ArrayList<>()); final List<String> msgs = Collections.synchronizedList(new ArrayList<>()); final long t1 = System.nanoTime();
        Par.withStatus(msgs::add, () -> Par.withProgress(fr -> ev2.add(new double[]{System.nanoTime() - t1, fr}), () -> Par.phase(0, 0.9, () -> {
            final Perm[] p = new Perm[1]; Par.phase(0, 0.95, () -> p[0] = Perm.run(g, 100, true, 1, 0, 3600));
            Par.phase(0.95, 1, () -> ModelCompare.run(r2, new ArrayList<>(), nd, List.of("f"), fd, true)); })));
        double mx2 = 0; for (double[] a : ev2) mx2 = Math.max(mx2, a[1]);
        check(maxDrop(ev2) < 0.03, "regression: the fraction never falls back (largest drop " + maxDrop(ev2) + ")"); check(mx2 > 0.89, "regression: computation phase completes (" + mx2 + ")");
        // quiet(): no progress at all from the loops inside
        final int[] cnt = new int[1]; Par.withProgress(fr -> cnt[0]++, () -> Par.quiet(() -> { Par.forRange(50, i -> { }); return null; })); check(cnt[0] == 0, "quiet(): loops inside report nothing");
        // nextPhase is used up by the next loop only
        final List<Double> v = Collections.synchronizedList(new ArrayList<>()); Par.withProgress(v::add, () -> { Par.nextPhase(0.5, 0.6); Par.forRange(10, i -> { }); Par.forRange(10, i -> { }); });
        double lo = 1, hi = 0; for (int i = 0; i < v.size(); i++) { lo = Math.min(lo, v.get(i)); hi = Math.max(hi, v.get(i)); }
        check(v.size() == 21 && lo >= 0.0999 && hi <= 1.0 && v.contains(0.6), "nextPhase applies to one loop only (first loop in 0.5-0.6, second loop 0..1; " + v.size() + " events)");
        System.out.println(fails == 0 ? "ALL OK" : fails + " FAILURES"); if (fails > 0) System.exit(1);
    }
}
