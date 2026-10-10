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

    /** Default value of the null hypothesis on the working scale. */
    public double nullValue() {
        if (!Double.isNaN(nullEffect)) {
            // user supplies on back-transformed scale for ratio measures
            if (isRatioSm() && backtransf) return Math.log(nullEffect);
            if (backtransf) switch (sm) {
                case "PLOGIT": return Math.log(nullEffect / (1 - nullEffect));
                case "PAS": case "PFT": return Math.asin(Math.sqrt(nullEffect));
                case "ZCOR": return 0.5 * Math.log((1 + nullEffect) / (1 - nullEffect));
                default:
            }
            return nullEffect;
        }
        switch (sm) {
            case "OR": case "RR": case "HR": case "IRR": case "DOR": case "ROM": case "RD": case "ASD": case "MD": case "SMD":
            case "IRD": case "IRSD": case "ZCOR": case "COR": case "": case "VE": return 0;
            default: return Double.NaN; // single-group measures: no test by default
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
