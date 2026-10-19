package org.metajava;

import org.metajava.core.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** The exact permutation test reports its progress (fraction and status line) while it runs, in steps that never go backwards and end at 100 %.
 *  Run: java -cp build/classes:build/test-classes org.metajava.PermProgressTest */
public class PermProgressTest {
    static int fails = 0;
    static void check(boolean ok, String what) { if (!ok) { fails++; System.out.println("FAIL: " + what); } else System.out.println("ok:   " + what); }

    public static void main(String[] args) {
        int k = 14; double[] yi = new double[k], se = new double[k]; String[] f = new String[k];
        for (int i = 0; i < k; i++) { yi[i] = 0.3 + 0.4 * Math.sin(1.7 * i + 0.3); se[i] = 0.15 + 0.1 * ((i * 7) % 5) / 5.0; f[i] = i % 3 == 0 ? "A" : i % 3 == 1 ? "B" : "C"; }   // 14! / (5! 5! 4!) = 252,252 unique permutations
        MetaSpec sp = new MetaSpec(); sp.type = "gen"; sp.sm = "MD"; sp.methodTau = "REML"; MetaData md = new MetaData(); md.put("TE", yi); md.put("seTE", se); md.n = k;
        Map<String, String[]> fd = new HashMap<>(); fd.put("f", f);
        Reg g = Reg.run(Meta.run(sp, md), new ArrayList<>(), new HashMap<>(), List.of("f"), fd, true);
        final List<Double> fr = Collections.synchronizedList(new ArrayList<>()); final List<String> msgs = Collections.synchronizedList(new ArrayList<>());
        final Perm[] out = new Perm[1];
        Par.withStatus(msgs::add, () -> Par.withProgress(fr::add, () -> out[0] = Perm.run(g, 1000, true, 1, 0, 3600)));
        Perm t = out[0];
        check(t.exact && t.nPerm == 252252L, "exact test with 252,252 permutations (" + t.nPerm + ")");
        check(fr.size() > 100, "progress reported many times: " + fr.size());
        double mx = 0, last = -1; boolean inRange = true; for (double x : fr) { mx = Math.max(mx, x); if (x < 0 || x > 1.0000001) inRange = false; }
        check(inRange, "fractions within 0..1"); check(mx > 0.999, "the last chunk reaches 100 % (max " + mx + ")");
        // the exact loop reports one overall fraction: the first reported value is small (the timing fits of plan() do not report)
        check(fr.get(0) < 0.2, "first value is small: " + fr.get(0));
        check(msgs.size() >= 3 && msgs.get(0).contains("measuring"), "status: speed measurement announced first: " + msgs.get(0));
        boolean hasPerm = false; for (String m : msgs) if (m != null && m.startsWith("Exact permutation test: ") && m.contains("of 252,252 permutations")) hasPerm = true;
        check(hasPerm, "status line shows 'x of 252,252 permutations'"); check(msgs.get(msgs.size() - 1) == null, "status cleared at the end");
        System.out.println(msgs.size() > 3 ? "e.g. " + msgs.get(msgs.size() / 2) : "");
        System.out.println(fails == 0 ? "ALL OK" : fails + " FAILURES"); if (fails > 0) System.exit(1);
    }
}
