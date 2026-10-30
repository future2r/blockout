package name.ulbricht.blockout;

/// Pit dimensions and block set.
///
/// The original game calls the horizontal extent "length" and the vertical one "width"; the
/// menu and the settings file keep those terms. In the code `width`, `height` and `depth` are
/// the extents along x, y and z, as in [Pit] and [PolyCube].
///
/// @param width    horizontal extent on screen (the original's "Pit Length")
/// @param height   vertical extent on screen (the original's "Pit Width")
/// @param depth    number of layers
/// @param blockSet the blocks that may appear
record Setup(int width, int height, int depth, BlockSet blockSet) {

    static final int MIN_SIDE = 3, MAX_SIDE = 7;
    static final int MIN_DEPTH = 6, MAX_DEPTH = 18;

    static final Setup FLAT_FUN = new Setup(5, 5, 12, BlockSet.FLAT);
    static final Setup MANIA_3D = new Setup(3, 3, 10, BlockSet.BASIC);
    static final Setup OUT_OF_CONTROL = new Setup(5, 5, 10, BlockSet.EXTENDED);

    Setup {
        width = Math.clamp(width, MIN_SIDE, MAX_SIDE);
        height = Math.clamp(height, MIN_SIDE, MAX_SIDE);
        depth = Math.clamp(depth, MIN_DEPTH, MAX_DEPTH);
    }

    /// Unique id of this setup, used as key for the hall of fame.
    int id() {
        return blockSet.ordinal() * 325 + (depth - MIN_DEPTH) * 25
                + (width - MIN_SIDE) * 5 + (height - MIN_SIDE);
    }

    String name() {
        if (equals(FLAT_FUN)) return "Flat Fun";
        if (equals(MANIA_3D)) return "3D Mania";
        if (equals(OUT_OF_CONTROL)) return "Out of Control";
        return "Custom Setup";
    }

    /// Dimensions and block set, e.g. `5x5x12 FLAT`.
    String description() {
        return width + "x" + height + "x" + depth + " " + blockSet;
    }

    String dimensions() {
        return width + "x" + height + "x" + depth;
    }

    Setup withWidth(int w) {
        return new Setup(w, height, depth, blockSet);
    }

    Setup withHeight(int h) {
        return new Setup(width, h, depth, blockSet);
    }

    Setup withDepth(int d) {
        return new Setup(width, height, d, blockSet);
    }

    Setup withBlockSet(BlockSet b) {
        return new Setup(width, height, depth, b);
    }
}
