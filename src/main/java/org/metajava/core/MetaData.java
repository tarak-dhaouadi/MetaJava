package org.metajava.core;

import java.util.*;

/** Input data for one meta-analysis. Columns by meta argument name (event.e, n.e, TE, seTE, ...). */
public class MetaData {
    public String[] studlab;
    public String[] subgroup;       // may be null
    public double[] ordering;       // optional numeric ordering variable for cumulative analysis
    public final Map<String, double[]> v = new LinkedHashMap<>();
    public int n;

    public double[] get(String k) { return v.get(k); }
    public boolean has(String k) { return v.containsKey(k); }
    public MetaData put(String k, double[] x) { v.put(k, x); n = x.length; return this; }
}
