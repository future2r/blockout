package name.ulbricht.blockout;

import java.util.Arrays;

/// Exact rotation by quarter turns, as a row major 3x3 matrix applied as `M * v`.
///
/// The record compares the matrix contents, so equal rotations are equal objects.
record Rot(int[] m) {

    static final Rot IDENTITY = new Rot(new int[] {1, 0, 0, 0, 1, 0, 0, 0, 1});

    // Quarter turns around the pit axes, as defined by the original game
    static final Rot OX = new Rot(new int[] {1, 0, 0, 0, 0, -1, 0, 1, 0});
    static final Rot OY = new Rot(new int[] {0, 0, -1, 0, 1, 0, 1, 0, 0});
    static final Rot OZ = new Rot(new int[] {0, -1, 0, 1, 0, 0, 0, 0, 1});
    static final Rot NOX = new Rot(new int[] {1, 0, 0, 0, 0, 1, 0, -1, 0});
    static final Rot NOY = new Rot(new int[] {0, 0, 1, 0, 1, 0, -1, 0, 0});
    static final Rot NOZ = new Rot(new int[] {0, 1, 0, -1, 0, 0, 0, 0, 1});

    Rot {
        m = m.clone();
    }

    @Override
    public int[] m() {
        return m.clone();
    }

    /// Returns `this * other`, i.e. `other` is applied first.
    Rot times(Rot other) {
        double[] r = multiply(toDouble(), other.toDouble());
        int[] exact = new int[9];
        for (int i = 0; i < 9; i++) exact[i] = (int) Math.round(r[i]);
        return new Rot(exact);
    }

    int get(int row, int col) {
        return m[row * 3 + col];
    }

    /// Applies the rotation to a vector.
    Point apply(Point v) {
        return new Point(
                m[0] * v.x() + m[1] * v.y() + m[2] * v.z(),
                m[3] * v.x() + m[4] * v.y() + m[5] * v.z(),
                m[6] * v.x() + m[7] * v.y() + m[8] * v.z());
    }

    double[] toDouble() {
        double[] d = new double[9];
        for (int i = 0; i < 9; i++) d[i] = m[i];
        return d;
    }

    /// Part of this quarter turn: the rotation by `angle` radians around the same axis
    /// (Rodrigues' formula with the axis taken from the antisymmetric part of the matrix).
    /// An angle of π/2 gives this rotation again.
    double[] partial(double angle) {
        double[] k = new double[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) k[i * 3 + j] = (get(i, j) - get(j, i)) / 2.0;
        }
        double[] k2 = multiply(k, k);
        double s = Math.sin(angle), c = 1 - Math.cos(angle);
        double[] r = new double[9];
        for (int i = 0; i < 9; i++) r[i] = (i % 4 == 0 ? 1 : 0) + s * k[i] + c * k2[i];
        return r;
    }

    /// Product `a * b` of two row major 3x3 matrices.
    static double[] multiply(double[] a, double[] b) {
        double[] r = new double[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                double s = 0;
                for (int k = 0; k < 3; k++) s += a[i * 3 + k] * b[k * 3 + j];
                r[i * 3 + j] = s;
            }
        }
        return r;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Rot(int[] other) && Arrays.equals(m, other);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(m);
    }

    @Override
    public String toString() {
        return "Rot" + Arrays.toString(m);
    }
}
