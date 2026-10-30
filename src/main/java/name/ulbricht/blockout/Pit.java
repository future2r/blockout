package name.ulbricht.blockout;

import java.util.Arrays;

/// The pit contents. Coordinates follow the original game: x grows to the left on screen,
/// y grows upwards and z grows from the top layer (0) down to the floor (`depth - 1`).
///
/// The cells are stored layer by layer, so every layer is one contiguous slice.
final class Pit {

    private final int width, height, depth, layerSize;
    private final boolean[] cells;

    Pit(int width, int height, int depth) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.layerSize = width * height;
        this.cells = new boolean[layerSize * depth];
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    int depth() {
        return depth;
    }

    boolean inside(Point p) {
        return p.x() >= 0 && p.x() < width && p.y() >= 0 && p.y() < height && p.z() >= 0 && p.z() < depth;
    }

    /// True for occupied cells and for everything outside the pit.
    boolean blocked(Point p) {
        return !inside(p) || cells[index(p)];
    }

    /// True only for occupied cells inside the pit.
    boolean filled(Point p) {
        return inside(p) && cells[index(p)];
    }

    boolean filled(int x, int y, int z) {
        return filled(new Point(x, y, z));
    }

    void set(Point p, boolean value) {
        if (inside(p)) cells[index(p)] = value;
    }

    boolean layerEmpty(int z) {
        return count(z) == 0;
    }

    boolean layerFull(int z) {
        return count(z) == layerSize;
    }

    boolean isEmpty() {
        return count(0, cells.length) == 0;
    }

    /// Removes all full layers, lets the layers above fall down and returns their count.
    int removeFullLayers() {
        int removed = 0;
        int z = depth - 1;
        while (z >= 0) {
            if (layerFull(z)) {
                // Shift layers 0 .. z-1 down by one and clear the top layer
                System.arraycopy(cells, 0, cells, layerSize, z * layerSize);
                Arrays.fill(cells, 0, layerSize, false);
                removed++;
            } else {
                z--;
            }
        }
        return removed;
    }

    /// Offset that would bring a cell back inside the pit, per axis.
    Point outOfBounds(Point p) {
        return new Point(back(p.x(), width), back(p.y(), height), back(p.z(), depth));
    }

    private static int back(int v, int size) {
        return v < 0 ? -v : v >= size ? size - v - 1 : 0;
    }

    private int count(int z) {
        return count(z * layerSize, (z + 1) * layerSize);
    }

    private int count(int from, int to) {
        int n = 0;
        for (int i = from; i < to; i++) {
            if (cells[i]) n++;
        }
        return n;
    }

    private int index(Point p) {
        return p.x() + p.y() * width + p.z() * layerSize;
    }
}
