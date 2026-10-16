package org.metajava.core;

/** One pooled analysis (common + random + heterogeneity + prediction) of a set of studies. */
public class Pool {
    public int k;                  // number of studies used
    public double[] idxTE, idxSE;  // studies used
    public double[] wCommon, wRandom;
    // common effect
    public double teCommon, seCommon, loCommon, upCommon, statCommon, pCommon;
    // random effects
    public double teRandom, seRandom, loRandom, upRandom, statRandom, pRandom, dfRandom = Double.NaN;
    public String testType = "z";
    /** Explicit record of the random-effects confidence-interval method that produced the reported result (do not infer it from labels). */
    public boolean hkPrimary = false;        // Hartung-Knapp (meta: method.random.ci = "HK"), with or without ad hoc correction, even when the ad hoc "ci" rule ended in the z interval
    public boolean hkZ = false;              // ... and the ad hoc "ci" rule replaced the t interval by the classic z interval
    public boolean krPrimary = false;        // Kenward-Roger (meta: method.random.ci = "KR") actually used (not the fallback to the classic interval)
    /** The random-effects result is degenerate: its SE is exactly 0 (plain Hartung-Knapp with q = 0, all weighted study effects identical). The interval then has zero width and the
     *  test statistic is infinite (p = 0 only as a numerical limit) or 0/0 (p undefined, estimate equal to the null effect), the numbers meta::ci() returns. */
    public boolean randomSe0() { return seRandom == 0 && !Double.isNaN(teRandom); }
    public String randomLabelSuffix = "";
    public double altTau2 = Double.NaN, altGain = Double.NaN;   // tau^2 of a higher mode of the likelihood (NaN: none), see Rma.higherMode()
    public double tau2, tau, loTau2 = Double.NaN, upTau2 = Double.NaN, loTau = Double.NaN, upTau = Double.NaN;
    public String tauCiMethod = "";
    public double Q, pQ, H, loH, upH, I2, loI2, upI2;
    public int dfQ;
    public double loPred = Double.NaN, upPred = Double.NaN, dfPred = Double.NaN, sePred = Double.NaN;
    public double seClassicRandom;
    public String predLabel = "";
    public double seKR = Double.NaN, dfKR = Double.NaN;
    public double nHarm = Double.NaN;        // harmonic mean of n (proportions) or person-time (rates) of the studies pooled; used for Freeman-Tukey back-transformation
    public boolean lrp = false; public double phi = Double.NaN;   // penalised logistic regression (LRP) and its heterogeneity parameter
    public boolean glmm = false;             // pooled by a generalised linear mixed model (no study weights)
    public double seTau2 = Double.NaN, qLRT = Double.NaN, pQLRT = Double.NaN;   // GLMM: likelihood-ratio heterogeneity test
    public double I2tau = Double.NaN, Htau = Double.NaN;   // GLMM: tau^2-based I^2 = tau^2 / (tau^2 + typical sampling variance) and H, as metafor reports them (I2 / H2 of rma.glmm)

    public interface CommonFn { double[] apply(int[] subset); } // returns {TE, se} for MH/Peto/etc.

    /** Weights for GENQ / GENQM (null for every other estimator, and for the inverse sampling variance option, which Rma forms from the variances). */
    public static double[] genqWeightsFor(MetaSpec s, int[] sel) {
        if (!MetaSpec.isGenq(s.methodTau) || s.genqInverse) return null;
        double[] w = s.genqFor(sel);
        if (w == null) throw new IllegalArgumentException(s.genqError != null ? s.genqError : "The weights of the GENQ / GENQM estimators are not available.");
        return w;
    }

    /** subset = indices into te/se arrays of studies to include; NaN studies are dropped. */
    public static Pool compute(MetaSpec s, double[] te, double[] se, int[] subset, CommonFn commonFn) {
        int cnt = 0;
        for (int i : subset) if (!Double.isNaN(te[i]) && !Double.isNaN(se[i]) && !Double.isInfinite(te[i]) && se[i] > 0) cnt++;
        int[] sel = new int[cnt]; int c = 0;
        for (int i : subset) if (!Double.isNaN(te[i]) && !Double.isNaN(se[i]) && !Double.isInfinite(te[i]) && se[i] > 0) sel[c++] = i;
        Pool p = new Pool(); p.k = cnt;
        double[] y = new double[cnt], v = new double[cnt];
        for (int i = 0; i < cnt; i++) { y[i] = te[sel[i]]; v[i] = se[sel[i]] * se[sel[i]]; }
        p.nHarm = Meta.harmonic(s.nPft != null && s.nPft.length == te.length ? s.nPft : null, sel);
        if (s.isGlmm() && s.glmm != null && s.glmm.n == te.length) return glmmPool(s, sel, y, v, p);
        if (s.isLrp() && s.glmm != null && s.glmm.n == te.length) return lrpPool(s, te, se, subset, sel);
        double[] ssw = null;   // sample size weights (method = "SSW")
        if (s.method.equals("SSW") && s.sswW != null && s.sswW.length == te.length) { ssw = new double[cnt]; for (int i = 0; i < cnt; i++) ssw[i] = s.sswW[sel[i]]; }
        p.idxTE = y; p.idxSE = new double[cnt]; for (int i = 0; i < cnt; i++) p.idxSE[i] = Math.sqrt(v[i]);
        double nullv = Meta.nullWorking(s, p.nHarm);
        double zc = Dist.qnorm(1 - (1 - s.levelMa) / 2);
        // ---- common effect
        double sw = 0, swy = 0; p.wCommon = new double[cnt];
        for (int i = 0; i < cnt; i++) { p.wCommon[i] = 1 / v[i]; sw += p.wCommon[i]; swy += p.wCommon[i] * y[i]; }
        double teIV = cnt > 0 ? swy / sw : Double.NaN, seIV = cnt > 0 ? 1 / Math.sqrt(sw) : Double.NaN;
        p.teCommon = teIV; p.seCommon = seIV;
        if (commonFn != null && cnt > 0 && !s.method.equals("Inverse")) {
            double[] r = commonFn.apply(sel);
            if (r != null) { p.teCommon = r[0]; p.seCommon = r[1];
                if (r.length > 2) { // user weights as relative contributions
                }
            }
        }
        if (s.methodCommonCi.equals("IVhet") && cnt > 1) { /* computed below once tau2 known */ }
        // ---- heterogeneity
        double Q = 0; for (int i = 0; i < cnt; i++) Q += p.wCommon[i] * (y[i] - teIV) * (y[i] - teIV);
        p.Q = cnt > 0 ? Q : Double.NaN; p.dfQ = Math.max(cnt - 1, 0); p.pQ = cnt > 1 ? Dist.pchisqUpper(Q, p.dfQ) : Double.NaN;
        Rma m = null;
        if (cnt >= 2) {
            if (!Double.isNaN(s.tauPreset)) { p.tau2 = s.tauPreset * s.tauPreset * 0 + s.tauPreset; }
            else { m = Rma.fit(y, v, null, s.methodTau, genqWeightsFor(s, sel)); p.tau2 = m.tau2; p.altTau2 = m.higherMode(); p.altGain = m.higherModeGain; }
        } else p.tau2 = Double.NaN;
        p.tau = Math.sqrt(p.tau2);
        double t2 = Double.isNaN(p.tau2) ? 0 : p.tau2;
        // H and I2 with CIs
        hi(p, s);
        String tc = s.effectiveTauCi();
        p.tauCiMethod = "";
        if (cnt >= 3 && m != null && !tc.isEmpty() && !tc.equals("PL") && (!s.skipTauCi || s.methodI2.equals("tau2"))) {
            Rma.TauCI ci = m.confint(tc, s.levelHetstat);
            p.loTau2 = ci.lbTau2; p.upTau2 = ci.ubTau2; p.loTau = ci.lbTau; p.upTau = ci.ubTau; p.tauCiMethod = tc;
            if (s.methodI2.equals("tau2")) {
                double sv = Rma.typicalV(cnt, 1, v, ones2(cnt));
                p.H = Math.sqrt((p.tau2 + sv) / sv); p.I2 = p.tau2 / (p.tau2 + sv);
                p.loH = Math.sqrt(ci.lbH2); p.upH = Math.sqrt(ci.ubH2); p.loI2 = ci.lbI2; p.upI2 = ci.ubI2;
            }
        } else if (cnt == 2 || (m != null && cnt < 3)) { p.loTau2 = p.upTau2 = p.loTau = p.upTau = Double.NaN; }
        // ---- common effect inference
        if (ssw != null && cnt > 0) {   // sample size method: weighted mean with weights n.e * n.c / (n.e + n.c)
            double a = 0, b = 0, c2 = 0; for (int i = 0; i < cnt; i++) { a += ssw[i]; b += ssw[i] * y[i]; c2 += ssw[i] * ssw[i] * v[i]; }
            p.teCommon = b / a; p.seCommon = Math.sqrt(c2) / a; p.wCommon = ssw.clone();
        }
        if (ssw == null && s.methodCommonCi.equals("IVhet") && cnt > 1) {
            double a = 0; for (int i = 0; i < cnt; i++) a += p.wCommon[i] * p.wCommon[i] * (v[i] + t2);
            p.seCommon = Math.sqrt(a) / sw;
        }
        double nv = Double.isNaN(nullv) ? 0 : nullv;
        p.loCommon = p.teCommon - zc * p.seCommon; p.upCommon = p.teCommon + zc * p.seCommon;
        p.statCommon = (p.teCommon - nv) / p.seCommon; p.pCommon = Dist.pnormTwoSided(p.statCommon);
        // ---- random effects
        p.wRandom = new double[cnt]; double swr = 0, swyr = 0;
        for (int i = 0; i < cnt; i++) { p.wRandom[i] = 1 / (v[i] + t2); swr += p.wRandom[i]; swyr += p.wRandom[i] * y[i]; }
        p.teRandom = cnt > 0 ? swyr / swr : Double.NaN;
        double seClassic = cnt > 0 ? 1 / Math.sqrt(swr) : Double.NaN; p.seClassicRandom = seClassic;
        p.seRandom = seClassic; p.dfRandom = Double.NaN;
        double qq = 0; for (int i = 0; i < cnt; i++) qq += p.wRandom[i] * (y[i] - p.teRandom) * (y[i] - p.teRandom);
        double hkQ = cnt > 1 ? qq / (cnt - 1) : Double.NaN, seHK = Math.sqrt(hkQ / swr);
        // Kenward-Roger (Partlett & Riley 2017), as in meta::kenwardroger
        if (cnt > 1) { double w1 = 0, w2 = 0, w3 = 0; for (double x : p.wRandom) { w1 += x; w2 += x * x; w3 += x * x * x; }
            double IE = w2 * 0.5 - w3 / w1 + 0.5 * Math.pow(w2 / w1, 2); double var = (1 + 2 * (w3 / w1 - Math.pow(w2 / w1, 2)) / IE) / w1;
            p.dfKR = 2 * IE / Math.pow(var * w2, 2); p.seKR = var >= 0 ? Math.sqrt(var) : Double.NaN; }
        // GENQ / GENQM: as metafor (rma.uni with weights = A), the pooled estimate is the A-weighted mean and its variance the sandwich (X'AX)^-1 X'A M A X (X'AX)^-1, M = diag(vi + tau2);
        // the Knapp-Hartung scale factor stays based on the weights 1/(vi + tau2) (computed above), the prediction interval uses the sandwich variance plus tau2
        boolean genqFit = MetaSpec.isGenq(s.methodTau) && ssw == null && cnt >= 2 && !Double.isNaN(p.tau2);
        if (genqFit) {
            if (s.methodRandomCi.equals("KR")) throw new IllegalArgumentException("The Kenward-Roger method is not available with the weights of the GENQ / GENQM estimators; use \"classic\" or \"HK\".");
            String mpp = s.methodPredict == null ? "" : s.methodPredict;
            if (s.prediction && (mpp.equals("KR") || mpp.equals("KR-PR"))) throw new IllegalArgumentException("Prediction interval method \"" + mpp + "\" is not available with the weights of the GENQ / GENQM estimators.");
            Rma mg = m != null ? m : Rma.withTau2(y, v, null, s.methodTau, genqWeightsFor(s, sel), t2);
            double vg = mg.vb[0][0]; p.teRandom = mg.beta[0]; seClassic = Math.sqrt(vg); p.seClassicRandom = seClassic; p.seRandom = seClassic; seHK = Math.sqrt(hkQ * vg);
            p.wRandom = mg.aw.clone(); p.dfKR = Double.NaN; p.seKR = Double.NaN;
        }
        if (ssw != null && cnt > 0) {   // random effects sample size method: tau^2 enters the variance only
            double a = 0, b = 0, c2 = 0; for (int i = 0; i < cnt; i++) { a += ssw[i]; b += ssw[i] * y[i]; c2 += ssw[i] * ssw[i] * (v[i] + t2); }
            p.teRandom = b / a; seClassic = Math.sqrt(c2) / a; p.seClassicRandom = seClassic; p.seRandom = seClassic; p.wRandom = ssw.clone();
        }
        String mr = s.methodRandomCi;
        if (mr.equals("KR") && (Double.isNaN(p.seKR) || Double.isNaN(p.dfKR) || p.dfKR <= 0)) mr = "classic";
        double alpha = 1 - s.levelMa;
        if (ssw != null) {   // meta::metabin: z-based CI, or t-based CI (df = k - 1) for method.random.ci = "HK", with the sample size variance
            boolean useT = mr.equals("HK") && cnt > 1; double q = useT ? Dist.qt(1 - alpha / 2, cnt - 1) : zc;
            p.loRandom = p.teRandom - q * seClassic; p.upRandom = p.teRandom + q * seClassic; p.statRandom = (p.teRandom - nv) / seClassic;
            if (useT) { p.dfRandom = cnt - 1; p.testType = "t"; p.pRandom = Dist.ptTwoSided(p.statRandom, cnt - 1); } else p.pRandom = Dist.pnormTwoSided(p.statRandom);
        } else if (mr.equals("HK") && cnt > 1) {
            double dfh = cnt - 1, seU = seHK; boolean useT = true;
            switch (s.adhocHakn) {
                case "se": if (hkQ < 1) seU = seClassic; break;
                case "ci": { double wh = 2 * Dist.qt(1 - alpha / 2, dfh) * seHK, wc = 2 * zc * seClassic; if (wh < wc) { seU = seClassic; useT = false; } break; }
                case "IQWiG6": {
                    Rma dl = Rma.fit(y, v, null, "DL"); double[] wd = new double[cnt]; double sd = 0, sdy = 0;
                    for (int i = 0; i < cnt; i++) { wd[i] = 1 / (v[i] + dl.tau2); sd += wd[i]; }
                    double wdl = 2 * zc / Math.sqrt(sd), wh = 2 * Dist.qt(1 - alpha / 2, dfh) * seHK;
                    if (wh < wdl) seU = seClassic; break;
                }
                default:
            }
            p.seRandom = seU; p.hkPrimary = true; p.hkZ = !useT;
            if (useT) { p.dfRandom = dfh; p.testType = "t"; double q = Dist.qt(1 - alpha / 2, dfh); p.loRandom = p.teRandom - q * seU; p.upRandom = p.teRandom + q * seU;
                p.statRandom = (p.teRandom - nv) / seU; p.pRandom = Dist.ptTwoSided(p.statRandom, dfh); }
            else { p.loRandom = p.teRandom - zc * seU; p.upRandom = p.teRandom + zc * seU; p.statRandom = (p.teRandom - nv) / seU; p.pRandom = Dist.pnormTwoSided(p.statRandom); }
            p.randomLabelSuffix = " (HK" + (s.adhocHakn.isEmpty() ? "" : "-" + s.adhocHakn.substring(0, 2).toUpperCase()) + ")";
        } else if (mr.equals("KR") && cnt > 1) {
            p.seRandom = p.seKR; p.dfRandom = p.dfKR; p.testType = "t"; p.krPrimary = true; double q = Dist.qt(1 - alpha / 2, p.dfKR);
            p.loRandom = p.teRandom - q * p.seKR; p.upRandom = p.teRandom + q * p.seKR; p.statRandom = (p.teRandom - nv) / p.seKR; p.pRandom = Dist.ptTwoSided(p.statRandom, p.dfKR); p.randomLabelSuffix = " (KR)";
        } else {
            p.loRandom = p.teRandom - zc * seClassic; p.upRandom = p.teRandom + zc * seClassic;
            p.statRandom = (p.teRandom - nv) / seClassic; p.pRandom = Dist.pnormTwoSided(p.statRandom);
            if (mr.equals("KR")) p.randomLabelSuffix = " (KR)";
        }
        // ---- prediction interval (all methods of meta::metagen: V, HTS, HK, HK-PR, KR, KR-PR, NNF, S)
        double qp = 1 - (1 - s.levelPredict) / 2; String mp = s.methodPredict == null ? "" : s.methodPredict;
        boolean krOk = !Double.isNaN(p.seKR) && !Double.isNaN(p.dfKR) && p.dfKR > 0;
        if ((mp.equals("KR") || mp.equals("KR-PR")) && !krOk) mp = "V";
        if (cnt >= 2 && !mp.isEmpty()) {
            double sePI = Double.NaN, df = Double.NaN; double tt = t2;
            switch (mp) {
                case "V": sePI = Math.sqrt(seClassic * seClassic + tt); df = cnt - 1; p.predLabel = "t-distribution (df = " + (int) df + ")"; break;
                case "HTS": if (cnt > 2) { sePI = Math.sqrt(seClassic * seClassic + tt); df = cnt - 2; p.predLabel = "t-distribution (df = " + (int) df + ")"; } break;
                case "HK": { double sU = seHK; if (s.adhocHaknPi.equals("se") && hkQ < 1) sU = seClassic; sePI = Math.sqrt(sU * sU + tt); df = cnt - 1; p.predLabel = "Hartung-Knapp variance and t-distribution (df = " + (int) df + ")"; break; }
                case "HK-PR": if (cnt - 1 >= 2) { double sU = seHK; if (s.adhocHaknPi.equals("se") && hkQ < 1) sU = seClassic; sePI = Math.sqrt(sU * sU + tt); df = cnt - 2; p.predLabel = "Hartung-Knapp variance and t-distribution (df = " + (int) df + ")"; } break;
                case "KR": sePI = Math.sqrt(p.seKR * p.seKR + tt); df = p.dfKR; p.predLabel = "Kenward-Roger variance and t-distribution (df = " + Fmt.num(df, 2) + ")"; break;
                case "KR-PR": if (p.dfKR > 1) { sePI = Math.sqrt(p.seKR * p.seKR + tt); df = p.dfKR - 1; p.predLabel = "Kenward-Roger variance and t-distribution (df = " + Fmt.num(df, 2) + ")"; } break;
                case "S": sePI = Math.sqrt(seClassic * seClassic + tt); df = Double.POSITIVE_INFINITY; p.predLabel = "normal distribution"; break;
                case "NNF": {
                    double[] ci = nnf(y, v, p.teRandom, t2, seClassic, s.levelPredict);
                    p.loPred = ci[0]; p.upPred = ci[1]; p.sePred = Double.NaN; p.dfPred = Double.NaN; p.predLabel = "parametric bootstrap (Nagashima et al., 2019)"; mp = ""; break; }
                default: sePI = Math.sqrt(seClassic * seClassic + tt); df = cnt - 1; p.predLabel = "t-distribution (df = " + (int) df + ")";
            }
            if (!Double.isNaN(sePI)) { double q = Double.isInfinite(df) ? Dist.qnorm(qp) : Dist.qt(qp, df); p.sePred = sePI; p.dfPred = df; p.loPred = p.teRandom - q * sePI; p.upPred = p.teRandom + q * sePI; }
        }
        return p;
    }

    /** Penalised logistic regression (meta method = "LRP"): common effect and random effects estimates coincide, the random-effects SE is scaled by phi; no tau^2. */
    static Pool lrpPool(MetaSpec s, double[] te, double[] se, int[] subset, int[] sel) {
        MetaSpec s0 = s.forAux(); s0.methodTau = "DL"; s0.methodTauCi = ""; s0.methodPredict = ""; s0.methodRandomCi = "classic";
        Pool p = compute(s0, te, se, subset, null);   // heterogeneity statistics (Q, I^2, H) from the inverse variance analysis
        int cnt = sel.length; p.lrp = true; p.glmm = true; p.nHarm = Double.NaN;
        java.util.Arrays.fill(p.wCommon, Double.NaN); p.wRandom = new double[cnt]; java.util.Arrays.fill(p.wRandom, Double.NaN);
        double nullv = s.nullValue(), nv = Double.isNaN(nullv) ? 0 : nullv, alpha = 1 - s.levelMa, zc = Dist.qnorm(1 - alpha / 2); double nan = Double.NaN;
        p.tau2 = p.tau = p.loTau2 = p.upTau2 = p.loTau = p.upTau = nan; p.tauCiMethod = "";
        // meta fits all studies with complete data (including single and double zero studies); only user-excluded studies are dropped
        java.util.List<Integer> all = new java.util.ArrayList<>(); Glmm.Data g = s.glmm;
        for (int i : subset) if (!Double.isNaN(g.x1[i]) && !Double.isNaN(g.x2[i]) && !Double.isNaN(g.s1[i]) && !Double.isNaN(g.s2[i]) && g.s1[i] > 0 && g.s2[i] > 0) all.add(i);
        int[] selAll = new int[all.size()]; for (int i = 0; i < selAll.length; i++) selAll[i] = all.get(i);
        Lrp.Result lr = Lrp.fit(g.x1, g.s1, g.x2, g.s2, selAll);
        if (!lr.ok) { p.teCommon = p.seCommon = p.teRandom = p.seRandom = nan; return p; }
        p.phi = lr.phi;
        p.teCommon = lr.est; p.seCommon = lr.se; p.loCommon = lr.est - zc * lr.se; p.upCommon = lr.est + zc * lr.se; p.statCommon = (lr.est - nv) / lr.se; p.pCommon = Dist.pnormTwoSided(p.statCommon);
        p.teRandom = lr.est; p.seClassicRandom = lr.seRandom; p.seRandom = lr.seRandom;
        if (s.methodRandomCi.equals("HK") && cnt > 1) {
            double q = Dist.qt(1 - alpha / 2, cnt - 1); p.dfRandom = cnt - 1; p.testType = "t"; p.randomLabelSuffix = " (T)";
            p.loRandom = lr.est - q * lr.seRandom; p.upRandom = lr.est + q * lr.seRandom; p.statRandom = (lr.est - nv) / lr.seRandom; p.pRandom = Dist.ptTwoSided(p.statRandom, cnt - 1);
        } else { p.loRandom = lr.est - zc * lr.seRandom; p.upRandom = lr.est + zc * lr.seRandom; p.statRandom = (lr.est - nv) / lr.seRandom; p.pRandom = Dist.pnormTwoSided(p.statRandom); }
        String mp = s.methodPredict == null ? "" : s.methodPredict;   // meta::calcPI: tau^2 = 0, SE of the random effects estimate
        if (cnt >= 2 && !mp.isEmpty()) {
            double qp = 1 - (1 - s.levelPredict) / 2, df;
            if (mp.equals("S")) { df = Double.POSITIVE_INFINITY; p.predLabel = "normal distribution"; }
            else if (mp.equals("HTS") && cnt > 2) { df = cnt - 2; p.predLabel = "t-distribution (df = " + (int) df + ")"; }
            else { df = cnt - 1; p.predLabel = "t-distribution (df = " + (int) df + ")"; }
            double q = Double.isInfinite(df) ? Dist.qnorm(qp) : Dist.qt(qp, df); p.sePred = lr.seRandom; p.dfPred = df; p.loPred = lr.est - q * lr.seRandom; p.upPred = lr.est + q * lr.seRandom;
        }
        return p;
    }

    /** Pooling by generalised linear mixed models (meta method = "GLMM", fitted as metafor::rma.glmm with model = "UM.FS"). */
    static Pool glmmPool(MetaSpec s, int[] sel, double[] y, double[] v, Pool p) {
        int cnt = sel.length; p.glmm = true; p.idxTE = y; p.idxSE = new double[cnt]; for (int i = 0; i < cnt; i++) p.idxSE[i] = Math.sqrt(v[i]);
        p.wCommon = new double[cnt]; p.wRandom = new double[cnt]; java.util.Arrays.fill(p.wCommon, Double.NaN); java.util.Arrays.fill(p.wRandom, Double.NaN);
        double nullv = s.nullValue(), nv = Double.isNaN(nullv) ? 0 : nullv, alpha = 1 - s.levelMa, zc = Dist.qnorm(1 - alpha / 2);
        double sw = 0, swy = 0; for (int i = 0; i < cnt; i++) { sw += 1 / v[i]; swy += y[i] / v[i]; } double init = cnt > 0 ? swy / sw : 0;
        double nan = Double.NaN; p.teCommon = p.seCommon = p.loCommon = p.upCommon = p.statCommon = p.pCommon = nan; p.teRandom = p.seRandom = p.loRandom = p.upRandom = p.statRandom = p.pRandom = nan;
        p.tau2 = p.tau = nan; p.Q = nan; p.pQ = nan; p.dfQ = Math.max(cnt - 1, 0); p.tauCiMethod = "";
        if (cnt == 0) { hi(p, s); return p; }
        Glmm.Result rc = Glmm.fit(s.glmm, sel, false, init, !s.skipLrt, Double.NaN, !s.skipHet, Double.NaN);
        if (!rc.ok) { hi(p, s); return p; }
        Glmm.Result rr = (s.random && cnt > 1) ? Glmm.fit(s.glmm, sel, true, rc.est, !s.skipLrt, rc.llSat, !s.skipHet, rc.qWald) : rc; if (!rr.ok) rr = rc;
        p.teCommon = rc.est; p.seCommon = rc.se; p.loCommon = rc.est - zc * rc.se; p.upCommon = rc.est + zc * rc.se; p.statCommon = (rc.est - nv) / rc.se; p.pCommon = Dist.pnormTwoSided(p.statCommon);
        p.teRandom = rr.est; p.seClassicRandom = rr.se; p.seRandom = rr.se;
        if (s.methodRandomCi.equals("HK") && cnt > 1) {   // Wald t-test with k - 1 degrees of freedom (test = "t" in rma.glmm)
            double q = Dist.qt(1 - alpha / 2, cnt - 1); p.dfRandom = cnt - 1; p.testType = "t";
            p.loRandom = rr.est - q * rr.se; p.upRandom = rr.est + q * rr.se; p.statRandom = (rr.est - nv) / rr.se; p.pRandom = Dist.ptTwoSided(p.statRandom, cnt - 1); p.randomLabelSuffix = " (T)";
        } else { p.loRandom = rr.est - zc * rr.se; p.upRandom = rr.est + zc * rr.se; p.statRandom = (rr.est - nv) / rr.se; p.pRandom = Dist.pnormTwoSided(p.statRandom); }
        if (cnt > 1) { p.tau2 = rr.tau2; p.tau = Math.sqrt(rr.tau2); p.seTau2 = rr.seTau2; }
        // heterogeneity: Wald-type Q of the saturated model (as meta::addGLMM), I^2 and H from Q
        if (cnt > 1) { p.Q = rr.qWald; p.dfQ = cnt - 1; p.pQ = Dist.pchisqUpper(p.Q, p.dfQ); p.qLRT = rr.qLRT; p.pQLRT = Double.isNaN(rr.qLRT) ? Double.NaN : Dist.pchisqUpper(rr.qLRT, p.dfQ); }
        hi(p, s);
        if (cnt > 1 && !Double.isNaN(p.tau2) && p.tau2 >= 0) {   // metafor's I^2 and H^2 (rma.glmm): typical sampling variance from the study log ratios / log odds with their continuity-corrected variances
            double sv = Rma.typicalV(cnt, 1, v, ones2(cnt)); p.I2tau = p.tau2 / (p.tau2 + sv); p.Htau = Math.sqrt((p.tau2 + sv) / sv);
        }
        // prediction interval (V, HTS or S)
        String mp = s.methodPredict == null ? "" : s.methodPredict;
        if (cnt >= 2 && !mp.isEmpty()) {
            double qp = 1 - (1 - s.levelPredict) / 2, t2 = Double.isNaN(p.tau2) ? 0 : p.tau2, sePI = Math.sqrt(rr.se * rr.se + t2), df;
            if (mp.equals("S")) { df = Double.POSITIVE_INFINITY; p.predLabel = "normal distribution"; }
            else if (mp.equals("HTS") && cnt > 2) { df = cnt - 2; p.predLabel = "t-distribution (df = " + (int) df + ")"; }
            else { df = cnt - 1; p.predLabel = "t-distribution (df = " + (int) df + ")"; }
            double q = Double.isInfinite(df) ? Dist.qnorm(qp) : Dist.qt(qp, df); p.sePred = sePI; p.dfPred = df; p.loPred = rr.est - q * sePI; p.upPred = rr.est + q * sePI;
        }
        return p;
    }

    /** Bootstrap prediction interval after Nagashima, Noma & Furukawa (2019): bootstrap the pivot of a new study effect. */
    /** The seeded Gaussian stream of the bootstrap depends only on k, so it is cached (leave-one-out rows all share k-1; re-runs share everything). */
    private static final java.util.concurrent.ConcurrentHashMap<Integer, double[]> NNF_STREAMS = new java.util.concurrent.ConcurrentHashMap<>();
    private static double[] nnfStream(int k, int B) {
        double[] z = NNF_STREAMS.get(k); if (z != null) return z;
        z = new double[B * (k + 1)]; java.util.Random rnd = new java.util.Random(20190101L); for (int q = 0; q < z.length; q++) z[q] = rnd.nextGaussian();
        if (k <= 100) { if (NNF_STREAMS.size() >= 8) NNF_STREAMS.clear(); NNF_STREAMS.put(k, z); }
        return z;
    }

    static double[] nnf(double[] y, double[] v, double mu, double tau2, double se, double level) {
        final int k = y.length, B = 10000; final double[] piv = new double[B];
        // the random stream is drawn sequentially (identical to a single-threaded run); the replicates are then evaluated in parallel
        final double[] z = nnfStream(k, B);
        final double sdTau = Math.sqrt(tau2); final double[] sd = new double[k], w = new double[k]; double sw = 0, sw2 = 0;
        for (int i = 0; i < k; i++) { sd[i] = Math.sqrt(v[i] + tau2); w[i] = 1 / v[i]; sw += w[i]; sw2 += w[i] * w[i]; }
        final double swF = sw, sw2F = sw2;
        Par.forRange(B, b -> {
            int o = b * (k + 1); double[] yb = new double[k]; double swy = 0;
            for (int i = 0; i < k; i++) { yb[i] = mu + sd[i] * z[o + i]; swy += w[i] * yb[i]; }
            double m0 = swy / swF, q = 0; for (int i = 0; i < k; i++) q += w[i] * (yb[i] - m0) * (yb[i] - m0);
            double tb = Math.max(0, (q - (k - 1)) / (swF - sw2F / swF)); double sr = 0, sry = 0; for (int i = 0; i < k; i++) { double wi = 1 / (v[i] + tb); sr += wi; sry += wi * yb[i]; }
            double mb = sry / sr, seb2 = 1 / sr; double ynew = mu + sdTau * z[o + k];
            piv[b] = (ynew - mb) / Math.sqrt(tb + seb2);
        });
        int nb = B; java.util.Arrays.sort(piv, 0, nb); double a = (1 - level) / 2; double sc = Math.sqrt(tau2 + se * se);
        return new double[]{mu + piv[(int) Math.floor(a * nb)] * sc, mu + piv[Math.min(nb - 1, (int) Math.ceil((1 - a) * nb) - 1)] * sc};
    }

    static double[][] ones2(int k) { double[][] o = new double[k][1]; for (double[] r : o) r[0] = 1; return o; }

    static double[] ones(int k) { double[] o = new double[k]; java.util.Arrays.fill(o, 1); return o; }

    static void hi(Pool p, MetaSpec s) {
        int k = p.k; double Q = p.Q; int df = p.dfQ;
        if (k < 2) { p.H = p.loH = p.upH = p.I2 = p.loI2 = p.upI2 = Double.NaN; return; }
        double H = Math.sqrt(Q / (k - 1)), se;
        if (Q > k) se = k >= 2 ? 0.5 * (Math.log(Q) - Math.log(k - 1)) / (Math.sqrt(2 * Q) - Math.sqrt(2 * k - 3)) : Double.NaN;
        else se = k > 2 ? Math.sqrt(1.0 / (2 * (k - 2)) * (1 - 1.0 / (3 * (k - 2.0) * (k - 2.0)))) : Double.NaN;
        double lh = Math.log(Math.max(H, 1)), z = Dist.qnorm(1 - (1 - s.levelHetstat) / 2);
        p.H = Math.exp(lh); p.loH = Math.max(Math.exp(lh - z * se), 1); p.upH = Math.max(Math.exp(lh + z * se), 1);
        java.util.function.DoubleUnaryOperator f = x -> (x * x - 1) / (x * x);
        p.I2 = f.applyAsDouble(p.H); p.loI2 = f.applyAsDouble(p.loH); p.upI2 = f.applyAsDouble(p.upH);
    }
}
