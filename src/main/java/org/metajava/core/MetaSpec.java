package org.metajava.core;

import java.util.*;

/** All analysis arguments (mirrors the arguments of meta::metagen/metabin/... ). */
public class MetaSpec implements Cloneable {
    public String type = "gen";               // gen, bin, cont, cor, inc, mean, prop, rate
    public String sm = "";                    // effect measure
    public String method = "Inverse";         // Inverse, MH, Peto, SSW, Cochran
    public boolean common = true, random = true, prediction = false;
    public boolean skipLrt = false;   // internal: leave-one-out / cumulative / Baujat never display the GLMM likelihood-ratio heterogeneity test, so skip its (slow) saturated-model fit
    public boolean skipHet = false;   // internal: Baujat reads only the common-effect estimate, so skip the GLMM Wald-type heterogeneity statistic (UM.RS needs its saturated-model fit)
    public boolean skipTauCi = false;   // internal: leave-one-out / cumulative never display tau2/H/I2 CIs, so skip the (slow) tau2 CI unless I2 is derived from tau2
    public String methodTau = "REML";
    /** Weights of the generalised Q-statistic estimators GENQ / GENQM only (ignored by every other tau2 estimator): see {@link #GENQ_WEIGHTS}. */
    public String genqWeights = "Inverse sampling variance: wi = 1/vi";
    /** Custom GENQ / GENQM weights (genqWeights = "Custom"): one number per study, in the order of the data, separated by commas, semicolons or white space; a single number is used for every study. */
    public String genqCustom = "";
    public static final String[] GENQ_WEIGHTS = {"Inverse sampling variance: wi = 1/vi", "Equal weights: wi = 1", "Total sample size: wi = ni", "Custom"};
    public String methodTauCi = null;         // null => default (J for DL, QP otherwise); "" = none
    public String methodI2 = "Q";
    public String methodCommonCi = "classic"; // classic, IVhet
    public String methodRandomCi = "classic"; // classic, HK, KR
    public String adhocHakn = "";             // "", se, ci, IQWiG6
    public String methodPredict = "V";        // V, HTS, HK, S, NNF, ""
    public String adhocHaknPi = "";           // "", se
    public double level = 0.95, levelMa = 0.95, levelPredict = 0.95, levelHetstat = 0.95;
    public double incr = 0.5; public String methodIncr = "only0";
    public boolean mhExact = false, rrCochrane = false, allstudies = false;
    public String methodSmd = "Hedges"; public boolean pooledvar = false; public boolean sdGlass = false; // sdGlass: false=control
    public String methodCi = null;            // individual study CI method for prop/rate/mean/cont (CP, WS, WSCC, AC, SA, SM, NAsm, z, t)
    public double nullEffect = Double.NaN;    // NaN => measure-specific default
    public double tauPreset = Double.NaN;
    public boolean tauCommon = false;
    public boolean backtransf = true;
    public double pscale = 1, irscale = 1; public String irunit = "person-years";
    public boolean testSubgroup = true, predictionSubgroup = false, printSubgroupName = true;
    public String sepSubgroup = " = "; public String subgroupName = "";
    public boolean overallHetstat = true;
    public String methodBias = null;          // null => Egger (OR: Harbord, DOR: Deeks)
    public String textCommon = "Common effect model", textRandom = "Random effects model", textPredict = "Prediction interval";
    public String textWCommon = "common", textWRandom = "random";
    public String title = "", complab = "", outclab = "";
    public String labelE = "Experimental", labelC = "Control", labelLeft = "", labelRight = "";
    /** Auxiliary data attached by Meta.run (not user settings): GLMM data, sample-size weights, n / time for Freeman-Tukey back-transformation. */
    public transient Glmm.Data glmm; public transient double[] sswW, nPft;
    /** GENQ / GENQM weights, one per study of the data (null when they cannot be formed: see {@link #genqError}); set by Meta.run. */
    public transient double[] genqW; public transient String genqError; public transient boolean genqInverse = true;
    public static boolean isGenq(String m) { return "GENQ".equals(m) || "GENQM".equals(m); }
    /** Weights of the studies at the given indices (null when the GENQ weights are unavailable). */
    public double[] genqFor(int[] idx) { if (genqW == null) return null; double[] w = new double[idx.length]; for (int i = 0; i < idx.length; i++) { if (idx[i] >= genqW.length) return null; w[i] = genqW[idx[i]]; } return w; }
    /** GLMM model as in metafor::rma.glmm (UM.FS, UM.RS, CM.EL, CM.AL; meta argument model.glmm) and number of quadrature points (nAGQ). */
    public String modelGlmm = "UM.FS"; public int nAGQ = 7;

    @Override public MetaSpec clone() { try { return (MetaSpec) super.clone(); } catch (CloneNotSupportedException e) { throw new RuntimeException(e); } }

    public String effectiveTauCi() {
        if (methodTauCi != null) return methodTauCi;
        return methodTau.equals("DL") ? "J" : "QP";
    }

    public boolean isGlmm() { return "GLMM".equals(method); }
    public boolean isLrp() { return "LRP".equals(method); }
    /** Methods without study weights (GLMM, penalised logistic regression). */
    public boolean noWeights() { return isGlmm() || isLrp(); }

    /** GLMM models available for an analysis type (meta::metabin and metainc; single proportions and rates only UM.FS). */
    public static List<String> glmmModels(String type) {
        switch (type) { case "bin": return List.of("UM.FS", "UM.RS", "CM.EL", "CM.AL"); case "inc": return List.of("UM.FS", "UM.RS", "CM.EL"); default: return List.of("UM.FS"); }
    }

    /** Copy for internal helper analyses (trim-and-fill, tests): plain inverse-variance pooling. */
    public MetaSpec forAux() { MetaSpec c = clone(); c.method = "Inverse"; c.glmm = null; c.sswW = null; return c; }

    /** Effect measure required by method = "GLMM" for each analysis type (as in meta::chkglmm). */
    public static String glmmSm(String type) {
        switch (type) { case "prop": return "PLOGIT"; case "rate": return "IRLN"; case "bin": return "OR"; case "inc": return "IRR"; default: return null; }
    }

    public boolean isRatioSm() { return Arrays.asList("OR", "RR", "HR", "IRR", "DOR", "ROM", "MLN", "PLN", "IRLN").contains(sm); }

    /** True when a null hypothesis is defined: a null value is given ("Target effect-size" / null effect) or the measure has a natural one (ratios, differences, correlations).
     *  Single proportions, means and rates have none by default, as in meta (null.effect = NA): no test and no p-value is reported for them. */
    public boolean hasNull() { return !Double.isNaN(nullValue()); }

    /** Null value on the working scale. The value entered (nullEffect) is on the natural scale (ratios, proportions, rates, means, correlations) when the results are back-transformed,
     *  and on the working scale otherwise. NaN: no null hypothesis (or a target that cannot be transformed, e.g. a proportion outside (0, 1)). */
    public double nullValue() {
        if (!Double.isNaN(nullEffect)) {
            double e = nullEffect;
            if (backtransf) switch (sm) {   // single-group measures: the natural scale includes the scaling factor (pscale / irscale)
                case "PRAW": { double p = e / pscale; return p >= 0 && p <= 1 ? p : Double.NaN; }
                case "PLOGIT": { double p = e / pscale; return p > 0 && p < 1 ? Math.log(p / (1 - p)) : Double.NaN; }
                case "PLN": { double p = e / pscale; return p > 0 && p <= 1 ? Math.log(p) : Double.NaN; }
                case "PAS": case "PFT": { double p = e / pscale; return p >= 0 && p <= 1 ? Math.asin(Math.sqrt(p)) : Double.NaN; }   // PFT: refined with the harmonic mean n by Meta.nullWorking
                case "IR": { double r = e / irscale; return r >= 0 ? r : Double.NaN; }
                case "IRLN": { double r = e / irscale; return r > 0 ? Math.log(r) : Double.NaN; }
                case "IRS": case "IRFT": { double r = e / irscale; return r >= 0 ? Math.sqrt(r) : Double.NaN; }   // IRFT: refined with the person-time by Meta.nullWorking
                case "MLN": return e > 0 ? Math.log(e) : Double.NaN;
                default:
            }
            if (isRatioSm() && backtransf) return e > 0 ? Math.log(e) : Double.NaN;
            if (backtransf && sm.equals("ZCOR")) return Math.abs(e) < 1 ? 0.5 * Math.log((1 + e) / (1 - e)) : Double.NaN;
            return e;
        }
        switch (sm) {
            case "OR": case "RR": case "HR": case "IRR": case "DOR": case "ROM": case "RD": case "ASD": case "MD": case "SMD":
            case "IRD": case "IRSD": case "ZCOR": case "COR": case "": case "VE": return 0;
            default: return Double.NaN; // single-group measures: no null hypothesis and no test by default
        }
    }

    public static String defaultSm(String type) {
        switch (type) {
            case "bin": return "RR"; case "cont": return "MD"; case "cor": return "ZCOR"; case "inc": return "IRR";
            case "mean": return "MRAW"; case "prop": return "PLOGIT"; case "rate": return "IR"; default: return "";
        }
    }

    public static List<String> smOptions(String type) {
        switch (type) {
            case "bin": return List.of("OR", "RD", "RR", "ASD", "DOR", "VE");
            case "cont": return List.of("MD", "SMD", "ROM");
            case "cor": return List.of("ZCOR", "COR");
            case "inc": return List.of("IRR", "IRD", "IRSD", "VE");
            case "mean": return List.of("MRAW", "MLN");
            case "prop": return List.of("PLOGIT", "PLN", "PRAW", "PAS", "PFT");
            case "rate": return List.of("IR", "IRLN", "IRS", "IRFT");
            default: return List.of("HR", "OR", "RR", "IRR", "MD", "SMD", "RD", "ZCOR", "COR", "MRAW", "PLOGIT", "PLN", "PRAW", "PAS", "PFT", "IR", "IRLN", "ROM", "");
        }
    }

    public static List<String> methodOptions(String type) {
        switch (type) {
            case "bin": return List.of("Inverse", "MH", "Peto", "SSW", "GLMM", "LRP");
            case "inc": return List.of("Inverse", "MH", "Cochran", "GLMM");
            case "prop": case "rate": return List.of("Inverse", "GLMM");
            default: return List.of("Inverse");
        }
    }
}
