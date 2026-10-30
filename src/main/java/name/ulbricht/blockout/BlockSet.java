package name.ulbricht.blockout;

/// The three block sets of the original game.
enum BlockSet {
    FLAT(762.5),
    BASIC(875.5),
    EXTENDED(2886.25);

    /// Base score of one completed layer (measured from the original game).
    final double lineBase;

    BlockSet(double lineBase) {
        this.lineBase = lineBase;
    }

    boolean contains(PolyCube p) {
        return switch (this) {
            case FLAT -> p.flat();
            case BASIC -> p.basic();
            case EXTENDED -> true;
        };
    }
}
