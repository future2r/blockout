package name.ulbricht.blockout;

import javafx.scene.paint.Color;

/// The 16 color EGA/VGA palette used by the DOS version.
final class Ega {

    static final Color BLACK = Color.web("#000000");
    static final Color BLUE = Color.web("#0000AA");
    static final Color GREEN = Color.web("#00AA00");
    static final Color CYAN = Color.web("#00AAAA");
    static final Color RED = Color.web("#AA0000");
    static final Color MAGENTA = Color.web("#AA00AA");
    static final Color BROWN = Color.web("#AA5500");
    static final Color LIGHT_GRAY = Color.web("#AAAAAA");
    static final Color DARK_GRAY = Color.web("#555555");
    static final Color LIGHT_BLUE = Color.web("#5555FF");
    static final Color LIGHT_GREEN = Color.web("#55FF55");
    static final Color LIGHT_CYAN = Color.web("#55FFFF");
    static final Color LIGHT_RED = Color.web("#FF5555");
    static final Color LIGHT_MAGENTA = Color.web("#FF55FF");
    static final Color YELLOW = Color.web("#FFFF55");
    static final Color WHITE = Color.web("#FFFFFF");

    /// Layer colors from the floor upwards; they repeat after seven layers.
    private static final Color[] LAYERS = {BLUE, GREEN, CYAN, RED, MAGENTA, BROWN, LIGHT_GRAY};

    /// Color of layer `z` (0 = top) in a pit of the given depth.
    static Color layer(int z, int depth) {
        return LAYERS[(depth - z - 1) % LAYERS.length];
    }

    private Ega() {
    }
}
