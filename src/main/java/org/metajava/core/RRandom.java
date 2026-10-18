package org.metajava.core;

/**
 * R's default random number generator (Mersenne Twister, R >= 3.6 "Rejection" sampling), so that a seed gives the same permutations
 * as {@code set.seed(seed); sample(k)} in R. Used by the random permutation test, where metafor draws the permutations with
 * {@code sample(x$k)} after {@code set.seed(seed)}. Port of R's RNG.c (RNG_Init, MT_genrand, fixup), random.c (R_unif_index, rbits)
 * and do_sample (sampling without replacement). Not thread-safe: one instance per stream.
 */
public final class RRandom {
    private static final int N = 624, M = 397;
    private static final int UPPER_MASK = 0x80000000, LOWER_MASK = 0x7fffffff, MATRIX_A = 0x9908b0df;
    private final int[] mt = new int[N];
    private int mti;

    /** As {@code set.seed(seed)} (the seed is an R integer; a larger value is reduced modulo 2^32 as the C unsigned cast would). */
    public RRandom(long seed) {
        int s = (int) seed;
        for (int j = 0; j < 50; j++) s = 69069 * s + 1;          // initial scrambling
        s = 69069 * s + 1; /* i_seed[0] is the position, overwritten by FixupSeeds */
        for (int j = 0; j < N; j++) { s = 69069 * s + 1; mt[j] = s; }
        mti = N;                                                  // FixupSeeds: dummy[0] = 624
    }

    private double genrand() {
        int y;
        if (mti >= N) {
            int kk;
            for (kk = 0; kk < N - M; kk++) { y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK); mt[kk] = mt[kk + M] ^ (y >>> 1) ^ ((y & 1) != 0 ? MATRIX_A : 0); }
            for (; kk < N - 1; kk++) { y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK); mt[kk] = mt[kk + (M - N)] ^ (y >>> 1) ^ ((y & 1) != 0 ? MATRIX_A : 0); }
            y = (mt[N - 1] & UPPER_MASK) | (mt[0] & LOWER_MASK); mt[N - 1] = mt[M - 1] ^ (y >>> 1) ^ ((y & 1) != 0 ? MATRIX_A : 0);
            mti = 0;
        }
        y = mt[mti++];
        y ^= (y >>> 11); y ^= (y << 7) & 0x9d2c5680; y ^= (y << 15) & 0xefc60000; y ^= (y >>> 18);
        return (y & 0xffffffffL) * 2.3283064365386963e-10;
    }

    /** R's unif_rand(): uniform on (0, 1). */
    public double unifRand() {
        final double i2_32m1 = 2.328306437080797e-10;
        double v = genrand();
        if (v <= 0.0) return 0.5 * i2_32m1;
        if (1.0 - v <= 0.0) return 1.0 - 0.5 * i2_32m1;
        return v;
    }

    private double rbits(int bits) {
        long v = 0;
        for (int n = 0; n <= bits; n += 16) { int v1 = (int) Math.floor(unifRand() * 65536); v = 65536 * v + v1; }
        if (bits < 64) v &= (1L << bits) - 1;
        return (double) v;
    }

    /** R_unif_index(dn): uniform integer in 0 .. dn-1 by rejection sampling. */
    public int unifIndex(int dn) {
        if (dn <= 0) return 0;
        int bits = dn <= 1 ? 0 : 32 - Integer.numberOfLeadingZeros(dn - 1);   // ceil(log2(dn)), exact for powers of two
        double dv;
        do { dv = rbits(bits); } while (dn <= dv);
        return (int) dv;
    }

    /** {@code sample(k)} without the +1: a random permutation of 0 .. k-1 (R's do_sample, no replacement). */
    public int[] sample(int k) {
        int[] x = new int[k], y = new int[k];
        for (int i = 0; i < k; i++) x[i] = i;
        int n = k;
        for (int i = 0; i < k; i++) { int j = unifIndex(n); y[i] = x[j]; x[j] = x[--n]; }
        return y;
    }
}
