package name.ulbricht.blockout;

/// An integer point or offset in pit coordinates: a cell, a lattice point or a move.
///
/// @param x grows to the left on screen
/// @param y grows upwards on screen
/// @param z grows into the pit
record Point(int x, int y, int z) {

    static final Point ZERO = new Point(0, 0, 0);

    /// The point with the given coordinates per axis (0 = x, 1 = y, 2 = z).
    static Point of(int[] xyz) {
        return new Point(xyz[0], xyz[1], xyz[2]);
    }

    /// The coordinate on the given axis (0 = x, 1 = y, 2 = z).
    int get(int axis) {
        return switch (axis) {
            case 0 -> x;
            case 1 -> y;
            default -> z;
        };
    }

    Point plus(Point o) {
        return new Point(x + o.x, y + o.y, z + o.z);
    }

    Point plus(int dx, int dy, int dz) {
        return new Point(x + dx, y + dy, z + dz);
    }
}
