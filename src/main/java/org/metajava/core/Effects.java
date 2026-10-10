package org.metajava.core;

/** Study-level effect estimates for every meta* function. */
public final class Effects {
    private Effects() {}

    public static final class Out {
        public double[] TE, seTE, lower, upper;
        public double[] incrE, incrC;      // continuity corrections used (binary / inc)
        public String note = "";
    }

    public static Out compute(MetaSpec s, MetaData d) {
        switch (s.type) {
            case "gen": return gen(s, d);
            case "bin": return bin(s, d);
            case "cont": return cont(s, d);
            case "cor": return cor(s, d);
            case "inc": return inc(s, d);
            case "mean": return mean(s, d);
            case "prop": return prop(s, d);
            case "rate": return rate(s, d);
            default: throw new IllegalArgumentException("Unknown analysis type " + s.type);
        }
    }

    static double zq(double level) { return Dist.qnorm(1 - (1 - level) / 2); }

    static Out alloc(int k) {
        Out o = new Out(); o.TE = new double[k]; o.seTE = new double[k]; o.lower = new double[k]; o.upper = new double[k];
        return o;
    }

    static void ciZ(Out o, double level) {
        double z = zq(level);
        for (int i = 0; i < o.TE.length; i++) { o.lower[i] = o.TE[i] - z * o.seTE[i]; o.upper[i] = o.TE[i] + z * o.seTE[i]; }
    }

    static Out gen(MetaSpec s, MetaData d) {
        int k = d.n; Out o = alloc(k);
        double[] te = d.get("TE"), se = d.get("seTE"), lo = d.get("lower"), up = d.get("upper");
        double z = zq(0.95);
        boolean logit = s.isRatioSm();
        for (int i = 0; i < k; i++) {
            if (te == null) { // derive from CI + point estimate on natural scale handled by caller
                o.TE[i] = Double.NaN;
            } else o.TE[i] = te[i];
            if (se != null) o.seTE[i] = se[i];
            else if (lo != null && up != null) o.seTE[i] = (up[i] - lo[i]) / (2 * z);
            else o.seTE[i] = Double.NaN;
        }
        ciZ(o, s.level);
        return o;
    }

    /** Transforms effect and CI limits given on natural (ratio) scale into the log scale (used for HR/OR/RR with lower/upper). */
    public static MetaData fromRatioCi(MetaData d, String effCol, String loCol, String upCol, double ciLevel) {
        double[] e = d.get(effCol), lo = d.get(loCol), up = d.get(upCol);
        double z = zq(ciLevel); int k = d.n;
        double[] te = new double[k], se = new double[k];
        for (int i = 0; i < k; i++) { te[i] = Math.log(e[i]); se[i] = (Math.log(up[i]) - Math.log(lo[i])) / (2 * z); }
        d.put("TE", te); d.put("seTE", se);
        return d;
    }

    static Out bin(MetaSpec s, MetaData d) {
        int k = d.n; Out o = alloc(k);
        double[] ee = d.get("event.e"), ne = d.get("n.e"), ec = d.get("event.c"), nc = d.get("n.c");
        o.incrE = new double[k]; o.incrC = new double[k];
        boolean anySparse = false;
        boolean[] sparse = new boolean[k];
        for (int i = 0; i < k; i++) {
            sparse[i] = ee[i] == 0 || ec[i] == 0 || ee[i] == ne[i] || ec[i] == nc[i];
            anySparse |= sparse[i];
        }
        String sm = s.sm;
        boolean needsIncr = !(sm.equals("RD") || sm.equals("ASD"));
        for (int i = 0; i < k; i++) {
            double inc = 0;
            switch (s.methodIncr) {
                case "all": inc = s.incr; break;
                case "if0all": inc = anySparse ? s.incr : 0; break;
                default: inc = sparse[i] ? s.incr : 0;
            }
            if (s.method.equals("Peto") || sm.equals("ASD")) inc = 0;
            if (sm.equals("RD") && !(ee[i] + ec[i] == 0 || ee[i] == ne[i] && ec[i] == nc[i])) inc = (s.methodIncr.equals("all")) ? s.incr : 0;
            if (sm.equals("RD") && (ee[i] + ec[i] == 0 || (ee[i] == ne[i] && ec[i] == nc[i]))) inc = s.incr;
            o.incrE[i] = inc; o.incrC[i] = inc;
        }
        for (int i = 0; i < k; i++) {
            double a = ee[i], n1 = ne[i], c = ec[i], n2 = nc[i];
            boolean bothZero = (a == 0 && c == 0) || (a == n1 && c == n2);
            double ia = o.incrE[i], ic = o.incrC[i];
            if (!s.allstudies && bothZero && !sm.equals("RD") && !sm.equals("ASD") && !sm.equals("VE") && !s.method.equals("Peto")) {
                o.TE[i] = Double.NaN; o.seTE[i] = Double.NaN; o.lower[i] = o.upper[i] = Double.NaN; continue;
            }
            double a1 = a + ia, c1 = c + ic, b1 = n1 - a + ia, d1 = n2 - c + ic;
            double n1c = n1 + 2 * ia, n2c = n2 + 2 * ic;
            switch (sm) {
                case "OR":
                    if (s.method.equals("Peto")) {
                        double n = n1 + n2, E = n1 * (a + c) / n, V = n1 * n2 * (a + c) * (n - a - c) / (n * n * (n - 1));
                        o.TE[i] = (a - E) / V; o.seTE[i] = 1 / Math.sqrt(V);
                    } else { o.TE[i] = Math.log(a1 * d1 / (b1 * c1)); o.seTE[i] = Math.sqrt(1 / a1 + 1 / b1 + 1 / c1 + 1 / d1); }
                    break;
                case "DOR": {
                    double sens = a1 / (a1 + c1), specE = d1 / (b1 + d1); // not supported fully: treat as OR on 2x2 table
                    o.TE[i] = Math.log(a1 * d1 / (b1 * c1)); o.seTE[i] = Math.sqrt(1 / a1 + 1 / b1 + 1 / c1 + 1 / d1); break;
                }
                case "RR": {
                    double nn1 = s.rrCochrane ? n1 + 2 * ia : n1 + ia, nn2 = s.rrCochrane ? n2 + 2 * ic : n2 + ic;
                    o.TE[i] = Math.log(a1 / nn1) - Math.log(c1 / nn2);
                    o.seTE[i] = Math.sqrt(1 / a1 - 1 / nn1 + 1 / c1 - 1 / nn2); break;
                }
                case "VE": {
                    double nn1 = n1 + ia, nn2 = n2 + ic;
                    double lrr = Math.log(a1 / nn1) - Math.log(c1 / nn2);
                    o.TE[i] = lrr; o.seTE[i] = Math.sqrt(1 / a1 - 1 / nn1 + 1 / c1 - 1 / nn2); break;
                }
                case "RD": {
                    double p1 = a1 / (n1 + 2 * ia), p2 = c1 / (n2 + 2 * ic);
                    o.TE[i] = p1 - p2; o.seTE[i] = Math.sqrt(p1 * (1 - p1) / (n1 + 2 * ia) + p2 * (1 - p2) / (n2 + 2 * ic));
                    if (o.seTE[i] == 0 && ia == 0) { }
                    break;
                }
                case "ASD": {
                    double p1 = a / n1, p2 = c / n2;
                    o.TE[i] = Math.asin(Math.sqrt(p1)) - Math.asin(Math.sqrt(p2));
                    o.seTE[i] = Math.sqrt(0.25 * (1 / n1 + 1 / n2)); break;
                }
                default: throw new IllegalArgumentException("Unsupported sm for binary data: " + sm);
            }
        }
        ciZ(o, s.level);
        if (sm.equals("VE")) { /* VE shown as 1 - RR */ }
        return o;
    }

    static Out cont(MetaSpec s, MetaData d) {
        int k = d.n; Out o = alloc(k);
        double[] ne = d.get("n.e"), me = d.get("mean.e"), se_ = d.get("sd.e"), nc = d.get("n.c"), mc = d.get("mean.c"), sc = d.get("sd.c");
        for (int i = 0; i < k; i++) {
            double n1 = ne[i], n2 = nc[i], N = n1 + n2;
            switch (s.sm) {
                case "MD":
                    o.TE[i] = me[i] - mc[i];
                    if (s.pooledvar) { double sp2 = ((n1 - 1) * se_[i] * se_[i] + (n2 - 1) * sc[i] * sc[i]) / (N - 2); o.seTE[i] = Math.sqrt(sp2 * (1 / n1 + 1 / n2)); }
                    else o.seTE[i] = Math.sqrt(se_[i] * se_[i] / n1 + sc[i] * sc[i] / n2);
                    break;
                case "SMD": {
                    double sp = Math.sqrt(((n1 - 1) * se_[i] * se_[i] + (n2 - 1) * sc[i] * sc[i]) / (N - 2));
                    double dd;
                    if (s.methodSmd.equals("Glass")) { double sdr = s.sdGlass ? se_[i] : sc[i]; dd = (me[i] - mc[i]) / sdr; o.TE[i] = dd;
                        o.seTE[i] = Math.sqrt(N / (n1 * n2) + dd * dd / (2 * ((s.sdGlass ? n1 : n2) - 1))); }
                    else {
                        dd = (me[i] - mc[i]) / sp;
                        if (s.methodSmd.equals("Cohen")) { o.TE[i] = dd; o.seTE[i] = Math.sqrt(N / (n1 * n2) + dd * dd / (2 * N)); }
                        else { double J = hedgesJ(N - 2); o.TE[i] = J * dd; o.seTE[i] = Math.sqrt(N / (n1 * n2) + o.TE[i] * o.TE[i] / (2 * (N - 3.94))); }
                    }
                    break;
                }
                case "ROM":
                    o.TE[i] = Math.log(me[i] / mc[i]);
                    o.seTE[i] = Math.sqrt(se_[i] * se_[i] / (n1 * me[i] * me[i]) + sc[i] * sc[i] / (n2 * mc[i] * mc[i])); break;
                default: throw new IllegalArgumentException("Unsupported sm for continuous data: " + s.sm);
            }
        }
        ciZ(o, s.level);
        return o;
    }

    static double hedgesJ(double df) {
        return Math.exp(Dist.lgamma(df / 2) - 0.5 * Math.log(df / 2) - Dist.lgamma((df - 1) / 2));
    }

    static Out cor(MetaSpec s, MetaData d) {
        int k = d.n; Out o = alloc(k);
        double[] r = d.get("cor"), n = d.get("n");
        for (int i = 0; i < k; i++) {
            if (s.sm.equals("ZCOR")) { o.TE[i] = 0.5 * Math.log((1 + r[i]) / (1 - r[i])); o.seTE[i] = 1 / Math.sqrt(n[i] - 3); }
            else { o.TE[i] = r[i]; o.seTE[i] = (1 - r[i] * r[i]) / Math.sqrt(n[i] - 1); }
        }
        ciZ(o, s.level);
        return o;
    }

    static Out inc(MetaSpec s, MetaData d) {
        int k = d.n; Out o = alloc(k);
        double[] ee = d.get("event.e"), te = d.get("time.e"), ec = d.get("event.c"), tc = d.get("time.c");
        o.incrE = new double[k]; o.incrC = new double[k];
        boolean any = false; for (int i = 0; i < k; i++) any |= ee[i] == 0 || ec[i] == 0;
        for (int i = 0; i < k; i++) {
            boolean sp = ee[i] == 0 || ec[i] == 0;
            double inc = s.methodIncr.equals("all") ? s.incr : s.methodIncr.equals("if0all") ? (any ? s.incr : 0) : (sp ? s.incr : 0);
            if (s.sm.equals("IRD") || s.sm.equals("IRSD")) inc = (ee[i] + ec[i] == 0) ? s.incr : 0;
            o.incrE[i] = inc; o.incrC[i] = inc;
            double a = ee[i] + inc, c = ec[i] + inc;
            if (!s.allstudies && ee[i] == 0 && ec[i] == 0 && (s.sm.equals("IRR") || s.sm.equals("VE"))) { o.TE[i] = o.seTE[i] = Double.NaN; continue; }
            switch (s.sm) {
                case "IRR": case "VE": o.TE[i] = Math.log((a / te[i]) / (c / tc[i])); o.seTE[i] = Math.sqrt(1 / a + 1 / c); break;
                case "IRD": o.TE[i] = a / te[i] - c / tc[i]; o.seTE[i] = Math.sqrt(a / (te[i] * te[i]) + c / (tc[i] * tc[i])); break;
                case "IRSD": o.TE[i] = Math.sqrt(a / te[i]) - Math.sqrt(c / tc[i]); o.seTE[i] = Math.sqrt(0.25 * (1 / te[i] + 1 / tc[i])); break;
                default: throw new IllegalArgumentException("Unsupported sm for incidence data: " + s.sm);
            }
        }
        ciZ(o, s.level);
        return o;
    }

    static Out mean(MetaSpec s, MetaData d) {
        int k = d.n; Out o = alloc(k);
        double[] n = d.get("n"), m = d.get("mean"), sd = d.get("sd");
        double[] t = null;
        for (int i = 0; i < k; i++) {
            if (s.sm.equals("MRAW")) { o.TE[i] = m[i]; o.seTE[i] = sd[i] / Math.sqrt(n[i]); }
            else { o.TE[i] = Math.log(m[i]); o.seTE[i] = sd[i] / (Math.sqrt(n[i]) * m[i]); }
        }
        ciZ(o, s.level);
        if (!"z".equals(s.methodCi) && s.methodCi != null && s.methodCi.equals("t")) {
            for (int i = 0; i < k; i++) { double q = Dist.qt(1 - (1 - s.level) / 2, n[i] - 1); o.lower[i] = o.TE[i] - q * o.seTE[i]; o.upper[i] = o.TE[i] + q * o.seTE[i]; }
        }
        return o;
    }

    static double transfProp(String sm, double e, double n) {
        switch (sm) {
            case "PLOGIT": return Math.log(e / (n - e));
            case "PLN": return Math.log(e / n);
            case "PRAW": return e / n;
            case "PAS": return Math.asin(Math.sqrt(e / n));
            case "PFT": return 0.5 * (Math.asin(Math.sqrt(e / (n + 1))) + Math.asin(Math.sqrt((e + 1) / (n + 1))));
            default: throw new IllegalArgumentException(sm);
        }
    }

    static double transfP(String sm, double p, double n) {
        switch (sm) {
            case "PLOGIT": return Math.log(p / (1 - p));
            case "PLN": return Math.log(p);
            case "PRAW": return p;
            case "PAS": return Math.asin(Math.sqrt(p));
            case "PFT": { double ev = n * p; return 0.5 * (Math.asin(Math.sqrt(ev / (n + 1))) + Math.asin(Math.sqrt((ev + 1) / (n + 1)))); }   // meta::metaprop
            default: throw new IllegalArgumentException(sm);
        }
    }

    static Out prop(MetaSpec s, MetaData d) {
        int k = d.n; Out o = alloc(k);
        double[] ev = d.get("event"), n = d.get("n");
        o.incrE = new double[k];
        boolean any = false; for (int i = 0; i < k; i++) any |= ev[i] == 0 || ev[i] == n[i];
        for (int i = 0; i < k; i++) {
            boolean sp = ev[i] == 0 || ev[i] == n[i];
            double inc = s.methodIncr.equals("all") ? s.incr : s.methodIncr.equals("if0all") ? (any ? s.incr : 0) : (sp ? s.incr : 0);
            if (s.sm.equals("PFT") || s.sm.equals("PAS") && false) inc = 0;
            if (s.sm.equals("PRAW") && !s.methodIncr.equals("all")) inc = sp ? inc : 0;
            o.incrE[i] = inc;
            double e = ev[i] + inc, nn = n[i] + 2 * inc;
            switch (s.sm) {
                case "PLOGIT": o.TE[i] = Math.log(e / (nn - e)); o.seTE[i] = Math.sqrt(1 / e + 1 / (nn - e)); break;
                case "PLN":   // meta::metaprop: TE = log((event + incr) / (n + incr)); Hartung & Knapp (2001), formula (18) for the variance
                    o.TE[i] = Math.log((ev[i] + inc) / (n[i] + inc));
                    o.seTE[i] = ev[i] == n[i] ? Math.sqrt(1 / ev[i] - 1 / (n[i] + inc)) : Math.sqrt(1 / (ev[i] + inc) - 1 / (n[i] + inc)); break;
                case "PRAW":  // meta::metaprop: TE = event / n (no correction), variance uses the corrected counts
                    o.TE[i] = ev[i] / n[i]; o.seTE[i] = Math.sqrt((ev[i] + inc) * (n[i] - ev[i] + inc) / Math.pow(n[i] + 2 * inc, 3)); break;
                case "PAS": o.TE[i] = Math.asin(Math.sqrt(ev[i] / n[i])); o.seTE[i] = Math.sqrt(1 / (4 * n[i])); break;
                case "PFT": o.TE[i] = transfProp("PFT", ev[i], n[i]); o.seTE[i] = Math.sqrt(1 / (4 * n[i] + 2)); break;
                default: throw new IllegalArgumentException("Unsupported sm for proportions: " + s.sm);
            }
            double[] ci = PropCI.interval(s.methodCi == null ? "CP" : s.methodCi, ev[i], n[i], s.level);
            if (ci == null) { double z = zq(s.level); o.lower[i] = o.TE[i] - z * o.seTE[i]; o.upper[i] = o.TE[i] + z * o.seTE[i]; }
            else {
                o.lower[i] = transfP(s.sm, clamp(ci[0]), n[i]); o.upper[i] = transfP(s.sm, clamp(ci[1]), n[i]);
                if (s.sm.equals("PLOGIT") || s.sm.equals("PLN")) { if (ci[0] <= 0) o.lower[i] = Double.NEGATIVE_INFINITY; if (ci[1] >= 1 && s.sm.equals("PLOGIT")) o.upper[i] = Double.POSITIVE_INFINITY; }
            }
        }
        return o;
    }

    static double clamp(double p) { return Math.min(1 - 1e-300, Math.max(1e-300, p)); }

    static Out rate(MetaSpec s, MetaData d) {
        int k = d.n; Out o = alloc(k);
        double[] ev = d.get("event"), t = d.get("time");
        o.incrE = new double[k];
        boolean any = false; for (int i = 0; i < k; i++) any |= ev[i] == 0;
        for (int i = 0; i < k; i++) {
            boolean sp = ev[i] == 0;
            double inc = s.methodIncr.equals("all") ? s.incr : s.methodIncr.equals("if0all") ? (any ? s.incr : 0) : (sp ? s.incr : 0);
            if (s.sm.equals("IRS") || s.sm.equals("IRFT")) inc = 0;
            o.incrE[i] = inc; double e = ev[i] + inc;
            switch (s.sm) {
                case "IR": o.TE[i] = e / t[i]; o.seTE[i] = Math.sqrt(e) / t[i]; break;
                case "IRLN": o.TE[i] = Math.log(e / t[i]); o.seTE[i] = 1 / Math.sqrt(e); break;
                case "IRS": o.TE[i] = Math.sqrt(ev[i] / t[i]); o.seTE[i] = 0.5 / Math.sqrt(t[i]); break;
                case "IRFT": o.TE[i] = 0.5 * (Math.sqrt(ev[i] / t[i]) + Math.sqrt((ev[i] + 1) / t[i])); o.seTE[i] = 0.5 / Math.sqrt(t[i]); break;
                default: throw new IllegalArgumentException("Unsupported sm for rates: " + s.sm);
            }
        }
        ciZ(o, s.level);
        if ("exact".equalsIgnoreCase(s.methodCi) && s.sm.equals("IR")) {
            double a = 1 - s.level;
            for (int i = 0; i < k; i++) {
                double lo = ev[i] == 0 ? 0 : Dist.qchisq(a / 2, 2 * ev[i]) / 2, up = Dist.qchisq(1 - a / 2, 2 * ev[i] + 2) / 2;
                o.lower[i] = lo / t[i]; o.upper[i] = up / t[i];
            }
        }
        return o;
    }
}
