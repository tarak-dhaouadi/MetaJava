package org.metajava.core;

import java.util.*;

/** Orchestrates a meta-analysis: effects, pooling, subgroups, leave-one-out, cumulative. */
public final class Meta {
    private Meta() {}

    public static MetaResult run(MetaSpec s, MetaData d) {
        MetaResult r = new MetaResult(); r.spec = s; r.data = d; r.kAll = d.n;
        if (s.sm == null || s.sm.isEmpty()) { if (!s.type.equals("gen")) s.sm = MetaSpec.defaultSm(s.type); }
        setupAux(s, d);
        r.eff = Effects.compute(s, d);
        r.studlab = d.studlab != null ? d.studlab : defaultLabels(d.n);
        r.TE = r.eff.TE; r.seTE = r.eff.seTE; r.lower = r.eff.lower; r.upper = r.eff.upper;
        // exclude studies with all-NaN
        int[] all = new int[d.n]; for (int i = 0; i < d.n; i++) all[i] = i;
        r.commonFn = makeCommonFn(s, d, r.eff);
        r.overall = Pool.compute(s, r.TE, r.seTE, all, r.commonFn);
        r.wCommonFull = expand(r.overall.wCommon, r.TE, r.seTE, all);
        r.wRandomFull = expand(r.overall.wRandom, r.TE, r.seTE, all);
        if ((s.method.equals("MH") || (s.method.equals("Cochran") && s.type.equals("inc"))) && s.common) { // weights for MH / Cochran: use their own weights
            r.wCommonFull = mhWeights(s, d, r.eff, r.TE, r.seTE);
        }
        if (s.method.equals("Peto")) { /* inverse variance weights equal Peto V */ }
        if (d.subgroup != null) subgroups(r);
        return r;
    }

    /** Attaches the auxiliary data needed by special pooling methods (GLMM, SSW) and by the Freeman-Tukey back-transformation. */
    static void setupAux(MetaSpec s, MetaData d) {
        s.glmm = null; s.sswW = null; s.nPft = null;
        if (s.type.equals("prop")) s.nPft = d.get("n");
        else if (s.type.equals("rate")) s.nPft = d.get("time");
        if (s.method.equals("SSW")) {
            if (!s.type.equals("bin")) throw new IllegalArgumentException("Sample size weighting (SSW) is only available for binary outcomes.");
            double[] ne = d.get("n.e"), nc = d.get("n.c"); double[] w = new double[d.n];
            for (int i = 0; i < d.n; i++) w[i] = ne[i] * nc[i] / (ne[i] + nc[i]);
            s.sswW = w;
        }
        if (s.isGlmm()) {
            String req = MetaSpec.glmmSm(s.type);
            if (req == null) throw new IllegalArgumentException("Method \"GLMM\" is only available for binary, incidence rate, single proportion and single rate data.");
            if (!req.equals(s.sm)) throw new IllegalArgumentException("Generalised linear mixed models only possible with argument 'sm = \"" + req + "\"'.");
            if (!"ML".equals(s.methodTau)) throw new IllegalArgumentException("Generalised linear mixed models only possible with argument 'method.tau = \"ML\"'.");
            if (s.methodRandomCi.equals("KR")) throw new IllegalArgumentException("Kenward-Roger method for random effects meta-analysis not available for GLMMs.");
            if (s.methodRandomCi.equals("HK") && !s.adhocHakn.isEmpty()) throw new IllegalArgumentException("Hartung-Knapp method with ad hoc correction not available for GLMMs.");
            String mp = s.methodPredict == null ? "" : s.methodPredict;
            if (s.prediction && (mp.equals("KR") || mp.equals("KR-PR") || mp.equals("NNF") || mp.equals("HK") || mp.equals("HK-PR")))
                throw new IllegalArgumentException("Prediction interval method \"" + mp + "\" not available for GLMMs; use \"V\", \"HTS\" or \"S\".");
            String model = MetaSpec.glmmModels(s.type).contains(s.modelGlmm) ? s.modelGlmm : "UM.FS";
            if (!model.equals(s.modelGlmm) && MetaSpec.glmmModels(s.type).size() > 1) throw new IllegalArgumentException("GLMM model \"" + s.modelGlmm + "\" is not available for this type of data.");
            switch (s.type) {
                case "prop": s.glmm = new Glmm.Data("PLO", model, s.nAGQ, d.get("event"), d.get("n"), null, null); break;
                case "rate": s.glmm = new Glmm.Data("IRLN", model, s.nAGQ, d.get("event"), d.get("time"), null, null); break;
                case "bin": s.glmm = new Glmm.Data("OR", model, s.nAGQ, d.get("event.e"), d.get("n.e"), d.get("event.c"), d.get("n.c")); break;
                default: s.glmm = new Glmm.Data("IRR", model, s.nAGQ, d.get("event.e"), d.get("time.e"), d.get("event.c"), d.get("time.c"));
            }
        }
        if (s.isLrp()) {   // penalised logistic regression (meta::metabin, method = "LRP")
            if (!s.type.equals("bin") || !"OR".equals(s.sm)) throw new IllegalArgumentException("Penalised logistic regression only possible with argument 'sm = \"OR\"'.");
            if (s.tauCommon) throw new IllegalArgumentException("Subgroup analysis not defined for penalised logistic regression assuming a common tau-squared.");
            s.glmm = new Glmm.Data("OR", "UM.FS", 1, d.get("event.e"), d.get("n.e"), d.get("event.c"), d.get("n.c"));
        }
    }

    static String[] defaultLabels(int n) { String[] l = new String[n]; for (int i = 0; i < n; i++) l[i] = "Study " + (i + 1); return l; }

    static double[] expand(double[] w, double[] te, double[] se, int[] idx) {
        double[] r = new double[te.length]; Arrays.fill(r, Double.NaN); int c = 0;
        for (int i : idx) if (!Double.isNaN(te[i]) && !Double.isNaN(se[i]) && !Double.isInfinite(te[i]) && se[i] > 0) r[i] = w[c++];
        return r;
    }

    static void subgroups(MetaResult r) {
        MetaSpec s = r.spec; String[] sg = r.data.subgroup;
        LinkedHashSet<String> lv = new LinkedHashSet<>();
        List<String> sorted = new ArrayList<>(); for (String x : sg) if (x != null && !x.isBlank()) sorted.add(x.trim());
        TreeSet<String> ts = new TreeSet<>(sorted);
        boolean numeric = true; for (String x : ts) if (Double.isNaN(DataTable.parse(x))) numeric = false;
        List<String> order = new ArrayList<>(ts);
        if (numeric) order.sort(Comparator.comparingDouble(DataTable::parse));
        for (String g : order) {
            List<Integer> ix = new ArrayList<>(); for (int i = 0; i < sg.length; i++) if (sg[i] != null && sg[i].trim().equals(g)) ix.add(i);
            int[] a = ix.stream().mapToInt(Integer::intValue).toArray();
            r.sgLevels.add(g); r.sgIdx.add(a);
        }
        {   // the subgroups are independent analyses: pool them concurrently (results are stored by index, so the order is unchanged)
            final Pool[] sp = new Pool[r.sgIdx.size()];
            Par.forRange(sp.length, g -> sp[g] = Pool.compute(s, r.TE, r.seTE, r.sgIdx.get(g), r.commonFn));
            r.sgPools.addAll(Arrays.asList(sp));
        }
        if (s.tauCommon && !s.isGlmm() && !s.isLrp()) {
            // common tau2: estimate residual heterogeneity with subgroup as moderator
            List<Integer> use = new ArrayList<>();
            for (int i = 0; i < r.TE.length; i++) if (!Double.isNaN(r.TE[i]) && r.seTE[i] > 0 && sg[i] != null && !sg[i].isBlank()) use.add(i);
            int kk = use.size(), G = r.sgLevels.size();
            double[] y = new double[kk], v = new double[kk]; double[][] X = new double[kk][G];
            for (int j = 0; j < kk; j++) { int i = use.get(j); y[j] = r.TE[i]; v[j] = r.seTE[i] * r.seTE[i]; X[j][r.sgLevels.indexOf(sg[i].trim())] = 1; }
            if (kk > G) {
                Rma m = Rma.fit(y, v, X, s.methodTau);
                MetaSpec s2 = s.clone(); s2.tauPreset = m.tau2;
                final Pool[] sp2 = new Pool[G];
                Par.forRange(G, g -> sp2[g] = Pool.compute(s2, r.TE, r.seTE, r.sgIdx.get(g), r.commonFn));
                for (int g = 0; g < G; g++) r.sgPools.set(g, sp2[g]);
            }
        }
        // between-group tests
        int G = r.sgLevels.size(); List<Pool> gp = new ArrayList<>(); for (Pool p : r.sgPools) if (p.k > 0) gp.add(p);
        r.dfBetween = gp.size() - 1;
        double sw = 0, swy = 0, swr = 0, swyr = 0;
        for (Pool p : gp) { double w = 1 / (p.seCommon * p.seCommon); sw += w; swy += w * p.teCommon; double wr = 1 / (p.seRandom * p.seRandom); swr += wr; swyr += wr * p.teRandom; }
        double qc = 0, qr = 0;
        for (Pool p : gp) { double w = 1 / (p.seCommon * p.seCommon); qc += w * Math.pow(p.teCommon - swy / sw, 2); double wr = 1 / (p.seRandom * p.seRandom); qr += wr * Math.pow(p.teRandom - swyr / swr, 2); }
        // A subgroup whose random-effects SE is exactly 0 (plain Hartung-Knapp with q = 0: all its weighted effects are identical) has an infinite weight: the between-group statistic is then
        // undefined (Inf, or 0 * Inf = NaN depending on the data). It is reported as not available, with the reason, instead of an accidental number or NaN.
        StringBuilder zero = new StringBuilder(); int nz = 0; for (int g = 0; g < r.sgPools.size(); g++) { Pool p = r.sgPools.get(g); if (p.k > 0 && p.seRandom == 0) { zero.append(zero.length() > 0 ? ", " : "").append(r.sgLevels.get(g)); nz++; } }
        if (s.random && !s.isGlmm() && !s.isLrp() && zero.length() > 0) { qr = Double.NaN; r.qbRandomNote = "not available: the random-effects standard error of subgroup" + (nz > 1 ? "s " : " ") + zero + (nz > 1 ? " are" : " is") + " 0 (Hartung-Knapp with q = 0, all weighted study effects identical), so " + (nz > 1 ? "their weights are" : "its weight is") + " infinite and the between-group statistic is undefined"; }
        r.qbCommon = qc; r.qbRandom = qr;
        if (r.dfBetween > 0) { r.pQbCommon = Dist.pchisqUpper(qc, r.dfBetween); r.pQbRandom = Double.isNaN(qr) ? Double.NaN : Dist.pchisqUpper(qr, r.dfBetween); }
        double qwc = 0; int dfw = 0; for (Pool p : gp) { qwc += p.Q; dfw += p.dfQ; }
        r.qwCommon = qwc; r.dfWithin = dfw;
    }

    /** Common-effect estimator for MH / Peto / Cochran (needs raw counts). */
    static Pool.CommonFn makeCommonFn(MetaSpec s, MetaData d, Effects.Out eff) {
        if (s.method.equals("Inverse") || s.method.equals("SSW") || s.method.equals("GLMM")) return null;
        if (s.type.equals("bin")) {
            double[] ee = d.get("event.e"), ne = d.get("n.e"), ec = d.get("event.c"), nc = d.get("n.c");
            return sel -> {
                String sm = s.sm;
                double sa = 0, sb = 0, sP1 = 0, sP2 = 0, sP3 = 0, sRD = 0, sRDw = 0, sRDv = 0, rrNum = 0, rrDen = 0, rrVar = 0, vPeto = 0, oMinusE = 0;
                double R = 0, S = 0, PR = 0, PSQR = 0, QS = 0;
                for (int i : sel) {
                    double inc = s.mhExact ? 0 : eff.incrE[i];
                    double a = ee[i] + inc, b = ne[i] - ee[i] + inc, c = ec[i] + inc, dd = nc[i] - ec[i] + inc;
                    double n1 = a + b, n2 = c + dd, n = n1 + n2;
                    if (s.method.equals("Peto")) {
                        double a0 = ee[i], n10 = ne[i], n20 = nc[i], n0 = n10 + n20, E = n10 * (a0 + ec[i]) / n0;
                        double V = n10 * n20 * (a0 + ec[i]) * (n0 - a0 - ec[i]) / (n0 * n0 * (n0 - 1));
                        oMinusE += a0 - E; vPeto += V; continue;
                    }
                    switch (sm) {
                        case "OR": case "DOR": { double Ri = a * dd / n, Si = b * c / n, P = (a + dd) / n, Q = (b + c) / n;
                            R += Ri; S += Si; PR += P * Ri; PSQR += P * Si + Q * Ri; QS += Q * Si; break; }
                        case "RR": case "VE": { rrNum += a * n2 / n; rrDen += c * n1 / n; rrVar += (n1 * n2 * (a + c) - a * c * n) / (n * n); break; }
                        case "RD": { double w = n1 * n2 / n; sRDw += w; sRD += (a * n2 - c * n1) / n; sRDv += (a * b * n2 * n2 * n2 + c * dd * n1 * n1 * n1) / (n1 * n2 * n * n); break; }
                        default: return null;
                    }
                }
                if (s.method.equals("Peto")) return new double[]{oMinusE / vPeto, 1 / Math.sqrt(vPeto)};
                switch (sm) {
                    case "OR": case "DOR": return new double[]{Math.log(R / S), Math.sqrt(PR / (2 * R * R) + PSQR / (2 * R * S) + QS / (2 * S * S))};
                    case "RR": case "VE": return new double[]{Math.log(rrNum / rrDen), Math.sqrt(rrVar / (rrNum * rrDen))};
                    case "RD": return new double[]{sRD / sRDw, Math.sqrt(sRDv) / sRDw};
                    default: return null;
                }
            };
        }
        if (s.type.equals("inc") && s.method.equals("Cochran")) {   // Cochran method (Smoking and Health, 1964), as in meta::metainc
            if (!(s.sm.equals("IRR") || s.sm.equals("VE"))) return null;
            double[] ee = d.get("event.e"), te = d.get("time.e"), ec = d.get("event.c"), tc = d.get("time.c");
            return sel -> {
                double sw = 0, swy = 0, sE = 0, sC = 0;
                for (int i : sel) { double w = ec[i] * te[i] / tc[i]; sw += w; swy += w * eff.TE[i]; sE += ee[i]; sC += ec[i]; }
                return new double[]{swy / sw, Math.sqrt(1 / sE + 1 / sC)};
            };
        }
        if (s.type.equals("inc") && s.method.equals("MH")) {
            double[] ee = d.get("event.e"), te = d.get("time.e"), ec = d.get("event.c"), tc = d.get("time.c");
            return sel -> {
                double num = 0, den = 0, var = 0, rd = 0, rdw = 0, rdv = 0;
                for (int i : sel) {
                    double inc = eff.incrE[i]; double a = ee[i] + inc, c = ec[i] + inc, T = te[i] + tc[i];
                    if (s.sm.equals("IRD")) { rdw += te[i] * tc[i] / T; rd += (a * tc[i] - c * te[i]) / T; rdv += (a * tc[i] * tc[i] + c * te[i] * te[i]) / (T * T); }
                    else { num += a * tc[i] / T; den += c * te[i] / T; var += (a + c) * te[i] * tc[i] / (T * T); }
                }
                if (s.sm.equals("IRD")) return new double[]{rd / rdw, Math.sqrt(rdv) / rdw};
                return new double[]{Math.log(num / den), Math.sqrt(var / (num * den))};
            };
        }
        return null;
    }

    /** MH weights for display (relative contribution in %). */
    static double[] mhWeights(MetaSpec s, MetaData d, Effects.Out eff, double[] te, double[] se) {
        int k = d.n; double[] w = new double[k]; Arrays.fill(w, Double.NaN);
        if (s.type.equals("bin")) {
            double[] ee = d.get("event.e"), ne = d.get("n.e"), ec = d.get("event.c"), nc = d.get("n.c");
            for (int i = 0; i < k; i++) {
                if (Double.isNaN(te[i]) && !s.sm.equals("RD")) { w[i] = 0; continue; }
                double inc = s.mhExact ? 0 : eff.incrE[i]; double a = ee[i] + inc, b = ne[i] - ee[i] + inc, c = ec[i] + inc, dd = nc[i] - ec[i] + inc, n1 = a + b, n2 = c + dd, n = n1 + n2;
                switch (s.sm) { case "OR": case "DOR": w[i] = b * c / n; break; case "RR": case "VE": w[i] = c * n1 / n; break; case "RD": w[i] = n1 * n2 / n; break; default: w[i] = Double.NaN; }
            }
            return w;
        }
        if (s.type.equals("inc")) {
            double[] ee = d.get("event.e"), te_ = d.get("time.e"), ec = d.get("event.c"), tc = d.get("time.c");
            for (int i = 0; i < k; i++) {
                if (s.method.equals("Cochran")) { w[i] = ec[i] * te_[i] / tc[i]; continue; }
                double T = te_[i] + tc[i], c = ec[i] + eff.incrE[i]; w[i] = s.sm.equals("IRD") ? te_[i] * tc[i] / T : c * te_[i] / T; }
            return w;
        }
        return null;
    }

    public static void leaveOneOut(MetaResult r) {
        r.loo = new ArrayList<>(); r.looLabels = new ArrayList<>();
        final int n = r.TE.length; final MetaSpec s2 = r.spec.clone(); s2.skipTauCi = true; s2.skipLrt = true;
        final List<Integer> use = new ArrayList<>(); for (int i = 0; i < n; i++) if (!Double.isNaN(r.TE[i]) && !Double.isNaN(r.seTE[i])) use.add(i);
        final Pool[] out = new Pool[use.size()];
        Par.forRange(out.length, q -> {
            int i = use.get(q); int[] sub = new int[n - 1]; int c = 0; for (int j = 0; j < n; j++) if (j != i) sub[c++] = j;
            out[q] = Pool.compute(s2, r.TE, r.seTE, sub, r.commonFn);
        });
        for (int q = 0; q < out.length; q++) { r.loo.add(out[q]); r.looLabels.add("Omitting " + r.studlab[use.get(q)]); }
    }

    /** Cumulative meta-analysis; order = permutation of study indices (null => data order, or ordering variable). */
    public static void cumulative(MetaResult r, int[] order) {
        final int n = r.TE.length;
        if (order == null) {
            Integer[] o = new Integer[n]; for (int i = 0; i < n; i++) o[i] = i;
            if (r.data.ordering != null) Arrays.sort(o, Comparator.comparingDouble(i -> r.data.ordering[i]));
            order = new int[n]; for (int i = 0; i < n; i++) order[i] = o[i];
        }
        r.cum = new ArrayList<>(); r.cumLabels = new ArrayList<>();
        final int[] ord = order; final MetaSpec s2 = r.spec.clone(); s2.skipTauCi = true; s2.skipLrt = true;
        final List<Integer> use = new ArrayList<>(); for (int j = 0; j < n; j++) if (!Double.isNaN(r.TE[ord[j]])) use.add(j);
        final Pool[] out = new Pool[use.size()];
        Par.forRange(out.length, q -> { int j = use.get(q); out[q] = Pool.compute(s2, r.TE, r.seTE, Arrays.copyOfRange(ord, 0, j + 1), r.commonFn); });
        for (int q = 0; q < out.length; q++) { r.cum.add(out[q]); r.cumLabels.add("Adding " + r.studlab[ord[use.get(q)]]); }
    }

    // ---- back-transformation ----------------------------------------------------------
    /** Inverse of the arcsine (n = NaN) or Freeman-Tukey double arcsine (Miller 1978, n = harmonic mean sample size) transformation; as meta::asin2p. */
    public static double asin2p(double x, double n) {
        if (Double.isNaN(x)) return Double.NaN;
        if (Double.isNaN(n) || Double.isInfinite(n)) { if (x < 0) return 0; if (x > Math.PI / 2) return 1; double v = Math.sin(x); return v * v; }
        double min = 0.5 * (Math.asin(0) + Math.asin(Math.sqrt(1 / (n + 1)))), max = 0.5 * (Math.asin(Math.sqrt(n / (n + 1))) + Math.asin(1));
        if (x < min) return 0; if (x > max) return 1;
        double s2 = Math.sin(2 * x), arg = 1 - Math.pow(s2 + (s2 - 1 / s2) / n, 2);
        return 0.5 * (1 - Math.signum(Math.cos(2 * x)) * Math.sqrt(Math.max(0, arg)));
    }

    /** Inverse of the Freeman-Tukey transformation of incidence rates (meta::asin2ir). */
    public static double asin2ir(double x, double time) {
        if (Double.isNaN(x)) return Double.NaN;
        double min = 0.5 * (0 + Math.sqrt(1 / time)); if (x < min) return 0;
        double r = (1 / time - 8 * x * x + 16 * time * Math.pow(x, 4)) / (16 * x * x * time); return r < 0 ? 0 : r;
    }

    /** Back-transformation as meta::backtransf. n = harmonic mean sample size (PFT) or person-time (IRFT); NaN if unknown. */
    public static double back(MetaSpec s, double x, double nHarm) {
        if (!s.backtransf) return x;
        double r;
        switch (s.sm) {
            case "OR": case "RR": case "HR": case "IRR": case "DOR": case "ROM": case "MLN": case "IRLN": return Math.exp(x);
            case "PLN": r = Math.exp(x); return (r < 0 ? 0 : r > 1 ? 1 : r) * s.pscale;
            case "VE": return 1 - Math.exp(x);
            case "PLOGIT": return 1 / (1 + Math.exp(-x)) * s.pscale;
            case "PRAW": return (x < 0 ? 0 : x > 1 ? 1 : x) * s.pscale;
            case "PAS": return asin2p(x, Double.NaN) * s.pscale;
            case "PFT": return asin2p(x, nHarm) * s.pscale;
            case "ZCOR": return Math.tanh(x);
            case "IR": r = x < 0 ? 0 : x; return r * s.irscale;
            case "IRS": return x * x * s.irscale;
            case "IRFT": return asin2ir(x, nHarm) * s.irscale;
            default: return x;
        }
    }

    /** Null value on the working scale for a pool whose harmonic mean sample size (person-time) is nHarm. Same as {@link MetaSpec#nullValue()}, except for the Freeman-Tukey measures
     *  (PFT, IRFT), whose back-transformation depends on nHarm: the working-scale value is then the one that back-transforms to the target (bisection; the back-transformation is increasing). */
    public static double nullWorking(MetaSpec s, double nHarm) {
        double nv = s.nullValue();
        if (Double.isNaN(nv) || !s.backtransf || Double.isNaN(s.nullEffect) || !(nHarm > 0) || !(s.sm.equals("PFT") || s.sm.equals("IRFT"))) return nv;
        boolean pft = s.sm.equals("PFT"); double target = s.nullEffect, lo = 0, hi = pft ? Math.PI / 2 : 4 * Math.sqrt(Math.max(1e-9, target / s.irscale) + 1) + 1;
        double flo = back(s, lo, nHarm) - target, fhi = back(s, hi, nHarm) - target;
        if (!(flo <= 0 && fhi >= 0)) return nv;   // not bracketed (target outside the range of the transformation): keep the large-sample value
        for (int it = 0; it < 80; it++) { double mid = 0.5 * (lo + hi); if (back(s, mid, nHarm) - target < 0) lo = mid; else hi = mid; }
        return 0.5 * (lo + hi);
    }

    /** Harmonic mean of the sample sizes (or person-times) of the studies in idx; NaN if not applicable. */
    public static double harmonic(double[] n, int[] idx) {
        if (n == null) return Double.NaN; double s = 0; int c = 0;
        for (int i : idx) if (!Double.isNaN(n[i]) && n[i] > 0) { s += 1 / n[i]; c++; }
        return c == 0 ? Double.NaN : c / s;
    }
}
