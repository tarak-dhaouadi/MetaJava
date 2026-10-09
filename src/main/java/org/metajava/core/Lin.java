package org.metajava.core;

/** Small dense linear algebra helpers. */
public final class Lin {
    private Lin() {}

    public static double[][] t(double[][] a) {
        double[][] r = new double[a[0].length][a.length];
        for (int i = 0; i < a.length; i++) for (int j = 0; j < a[0].length; j++) r[j][i] = a[i][j];
        return r;
    }

    public static double[][] mul(double[][] a, double[][] b) {
        int n = a.length, m = b[0].length, k = b.length;
        double[][] r = new double[n][m];
        for (int i = 0; i < n; i++) for (int l = 0; l < k; l++) { double v = a[i][l]; if (v == 0) continue;
            for (int j = 0; j < m; j++) r[i][j] += v * b[l][j]; }
        return r;
    }

    public static double[] mulv(double[][] a, double[] x) {
        double[] r = new double[a.length];
        for (int i = 0; i < a.length; i++) { double s = 0; for (int j = 0; j < x.length; j++) s += a[i][j] * x[j]; r[i] = s; }
        return r;
    }

    /** Inverse via Gauss-Jordan with partial pivoting; returns null if singular. */
    public static double[][] inv(double[][] a) {
        int n = a.length;
        double[][] m = new double[n][2 * n];
        for (int i = 0; i < n; i++) { System.arraycopy(a[i], 0, m[i], 0, n); m[i][n + i] = 1; }
        for (int c = 0; c < n; c++) {
            int p = c;
            for (int r = c + 1; r < n; r++) if (Math.abs(m[r][c]) > Math.abs(m[p][c])) p = r;
            if (Math.abs(m[p][c]) < 1e-14) return null;
            double[] tmp = m[c]; m[c] = m[p]; m[p] = tmp;
            double d = m[c][c];
            for (int j = 0; j < 2 * n; j++) m[c][j] /= d;
            for (int r = 0; r < n; r++) if (r != c) { double f = m[r][c]; if (f != 0) for (int j = 0; j < 2 * n; j++) m[r][j] -= f * m[c][j]; }
        }
        double[][] r = new double[n][n];
        for (int i = 0; i < n; i++) System.arraycopy(m[i], n, r[i], 0, n);
        return r;
    }

    public static double det(double[][] a) {
        int n = a.length; double[][] m = new double[n][]; for (int i = 0; i < n; i++) m[i] = a[i].clone();
        double det = 1;
        for (int c = 0; c < n; c++) {
            int p = c;
            for (int r = c + 1; r < n; r++) if (Math.abs(m[r][c]) > Math.abs(m[p][c])) p = r;
            if (m[p][c] == 0) return 0;
            if (p != c) { double[] t = m[c]; m[c] = m[p]; m[p] = t; det = -det; }
            det *= m[c][c];
            for (int r = c + 1; r < n; r++) { double f = m[r][c] / m[c][c]; for (int j = c; j < n; j++) m[r][j] -= f * m[c][j]; }
        }
        return det;
    }

    /** Eigenvalues of a symmetric matrix: Householder tridiagonalisation + implicit QL (eigenvalues only), O(n^3) with a small constant. */
    public static double[] eigSym(double[][] a0) {
        int n = a0.length; if (n == 0) return new double[0];
        double[][] a = new double[n][]; for (int i = 0; i < n; i++) a[i] = a0[i].clone();
        return eigSymInPlace(a);
    }

    /** Same as {@link #eigSym} but overwrites its argument (no copy); for matrices built just to be decomposed. */
    public static double[] eigSymInPlace(double[][] a) {
        int n = a.length; if (n == 0) return new double[0];
        double[] d = new double[n], e = new double[n];
        // tred2, eigenvalues only (Numerical Recipes / EISPACK)
        for (int i = n - 1; i > 0; i--) {
            int l = i - 1; double h = 0, scale = 0;
            if (l > 0) {
                for (int k = 0; k <= l; k++) scale += Math.abs(a[i][k]);
                if (scale == 0.0) e[i] = a[i][l];
                else {
                    for (int k = 0; k <= l; k++) { a[i][k] /= scale; h += a[i][k] * a[i][k]; }
                    double f = a[i][l], g = f > 0 ? -Math.sqrt(h) : Math.sqrt(h);
                    e[i] = scale * g; h -= f * g; a[i][l] = f - g; f = 0;
                    for (int j = 0; j <= l; j++) {
                        g = 0; for (int k = 0; k <= j; k++) g += a[j][k] * a[i][k];
                        for (int k = j + 1; k <= l; k++) g += a[k][j] * a[i][k];
                        e[j] = g / h; f += e[j] * a[i][j];
                    }
                    double hh = f / (h + h);
                    for (int j = 0; j <= l; j++) { f = a[i][j]; e[j] = g = e[j] - hh * f; for (int k = 0; k <= j; k++) a[j][k] -= (f * e[k] + g * a[i][k]); }
                }
            } else e[i] = a[i][l];
            d[i] = h;
        }
        for (int i = 0; i < n; i++) d[i] = a[i][i];
        // tqli, eigenvalues only
        for (int i = 1; i < n; i++) e[i - 1] = e[i]; e[n - 1] = 0;
        for (int l = 0; l < n; l++) {
            int iter = 0, m;
            do {
                for (m = l; m < n - 1; m++) { double dd = Math.abs(d[m]) + Math.abs(d[m + 1]); if (Math.abs(e[m]) <= 2.220446049250313e-16 * dd) break; }
                if (m != l) {
                    if (iter++ == 200) break;
                    double g = (d[l + 1] - d[l]) / (2.0 * e[l]), r = Math.hypot(g, 1.0);
                    g = d[m] - d[l] + e[l] / (g + (g >= 0 ? Math.abs(r) : -Math.abs(r)));
                    double s = 1, c = 1, p = 0; int i;
                    for (i = m - 1; i >= l; i--) {
                        double f = s * e[i], b = c * e[i]; e[i + 1] = r = Math.hypot(f, g);
                        if (r == 0.0) { d[i + 1] -= p; e[m] = 0; break; }
                        s = f / r; c = g / r; g = d[i + 1] - p; r = (d[i] - g) * s + 2.0 * c * b; d[i + 1] = g + (p = s * r); g = c * r - b;
                    }
                    if (r == 0.0 && i >= l) continue;
                    d[l] -= p; e[l] = g; e[m] = 0;
                }
            } while (m != l);
        }
        return d;
    }
}
