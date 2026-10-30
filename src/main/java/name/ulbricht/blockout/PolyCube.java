package name.ulbricht.blockout;

import java.util.ArrayList;
import java.util.List;

/// A block made of unit cubes, in local coordinates (x grows to the left on screen, y grows
/// upwards, z grows into the pit).
///
/// The block turns around its rotation center, a lattice point chosen like in the original
/// game. [#rotateCell] and [#rotateVertex] apply the same turn to cells (for collisions) and
/// to lattice points (for drawing).
///
/// @param cubes     the cube cells
/// @param highScore score when dropped from the top
/// @param lowScore  score when not dropped
/// @param flat      part of the FLAT block set
/// @param basic     part of the BASIC block set
record PolyCube(List<Point> cubes, int highScore, int lowScore, boolean flat, boolean basic) {

    PolyCube {
        cubes = List.copyOf(cubes);
    }

    int size() {
        return cubes.size();
    }

    int width() {
        return extent(0);
    }

    int height() {
        return extent(1);
    }

    int depth() {
        return extent(2);
    }

    int maxDim() {
        return Math.max(width(), Math.max(height(), depth()));
    }

    /// Rotation center: a lattice point, emulating the original game.
    Point center() {
        return new Point(width() - 1, 1, depth() - 1);
    }

    /// The cell that `cell` moves to when the block is turned by `rot` around its center.
    Point rotateCell(Rot rot, Point cell) {
        // Cell middles relative to the center, doubled to stay integer
        Point c = center();
        Point r = rot.apply(new Point(2 * (cell.x() - c.x()) + 1, 2 * (cell.y() - c.y()) + 1,
                2 * (cell.z() - c.z()) + 1));
        return c.plus((r.x() - 1) / 2, (r.y() - 1) / 2, (r.z() - 1) / 2);
    }

    /// The position of the lattice point `p` when the block is turned by the row major matrix
    /// `r` around its center.
    double[] rotateVertex(double[] r, Point p) {
        Point c = center();
        double dx = p.x() - c.x(), dy = p.y() - c.y(), dz = p.z() - c.z();
        return new double[] {
            r[0] * dx + r[1] * dy + r[2] * dz + c.x(),
            r[3] * dx + r[4] * dy + r[5] * dz + c.y(),
            r[6] * dx + r[7] * dy + r[8] * dz + c.z()
        };
    }

    private int extent(int axis) {
        int max = 0;
        for (Point c : cubes) max = Math.max(max, c.get(axis));
        return max + 1;
    }

    /// A wireframe edge between two lattice points.
    record Edge(Point from, Point to) {
    }

    /// Visible wireframe edges. An edge is drawn when the faces meeting there are not
    /// coplanar, so the seams between cubes on a flat side stay invisible.
    List<Edge> edges() {
        List<Edge> edges = new ArrayList<>();
        int[] dim = {width(), height(), depth()};
        for (int axis = 0; axis < 3; axis++) {
            int u = (axis + 1) % 3, v = (axis + 2) % 3;
            for (int a = 0; a < dim[axis]; a++) {
                for (int i = 0; i <= dim[u]; i++) {
                    for (int j = 0; j <= dim[v]; j++) {
                        // The four cells around the edge
                        boolean c00 = has(axis, a, u, i - 1, v, j - 1);
                        boolean c10 = has(axis, a, u, i, v, j - 1);
                        boolean c01 = has(axis, a, u, i - 1, v, j);
                        boolean c11 = has(axis, a, u, i, v, j);
                        int n = (c00 ? 1 : 0) + (c10 ? 1 : 0) + (c01 ? 1 : 0) + (c11 ? 1 : 0);
                        if (n == 1 || n == 3 || (n == 2 && c00 == c11)) {
                            edges.add(new Edge(point(axis, a, u, i, v, j), point(axis, a + 1, u, i, v, j)));
                        }
                    }
                }
            }
        }
        return edges;
    }

    private boolean has(int axis, int a, int u, int i, int v, int j) {
        return cubes.contains(point(axis, a, u, i, v, j));
    }

    private static Point point(int axis, int a, int u, int i, int v, int j) {
        int[] c = new int[3];
        c[axis] = a;
        c[u] = i;
        c[v] = j;
        return Point.of(c);
    }
}
