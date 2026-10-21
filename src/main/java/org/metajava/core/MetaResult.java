package org.metajava.core;

import java.util.*;

/** Full result of a meta-analysis (analogue of an object of class "meta"). */
public class MetaResult {
    public MetaSpec spec; public MetaData data; public Effects.Out eff;
    public String[] studlab; public double[] TE, seTE, lower, upper;
    public int kAll;
    public Pool overall;
    public volatile java.util.List<double[]> baujatPts; public volatile java.util.List<String> baujatLabs; public final Object baujatLock = new Object(); public volatile java.util.List<double[]> baujatPtsRnd; public volatile java.util.List<String> baujatLabsRnd;   // Baujat plot points, computed on first use
    public double[] wCommonFull, wRandomFull;          // per-study weights (NaN for excluded)
    // subgroup analysis
    public List<String> sgLevels = new ArrayList<>(); public List<Pool> sgPools = new ArrayList<>();
    public List<int[]> sgIdx = new ArrayList<>();
    /** Non-empty when the random-effects test for subgroup differences cannot be calculated (a subgroup has SE = 0, i.e. an infinite weight): the reason, for reports and plots. */ public String qbRandomNote = "";
    public double qbCommon = Double.NaN, qbRandom = Double.NaN; public int dfBetween; public double pQbCommon = Double.NaN, pQbRandom = Double.NaN;
    public double qwCommon = Double.NaN, qwRandom = Double.NaN; public int dfWithin;
    public Pool residualTau;                            // when tau.common = TRUE
    // leave-one-out and cumulative
    public List<Pool> loo; public List<String> looLabels;
    public List<Pool> cum; public List<String> cumLabels;
    public List<String> warnings = new ArrayList<>();
    public Pool.CommonFn commonFn;

    public boolean hasSubgroups() { return !sgLevels.isEmpty(); }
}
