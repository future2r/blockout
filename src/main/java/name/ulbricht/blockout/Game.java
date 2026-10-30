package name.ulbricht.blockout;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

/// Game rules and block kinematics. Timing, scoring and the rotation behaviour follow the
/// original game as measured and documented by BlockOut II. All times are in seconds.
///
/// Field names such as `redMode` or `cursorPos` keep the names used there.
final class Game {

    enum State { PLAYING, PAUSED, OVER }

    /// Player commands. A move or turn that arrives during a drop waits until the drop has
    /// ended. Commands still pending when the next block appears are discarded, and a drop
    /// request that cannot run right away is ignored.
    enum Action {
        // Moves, in the order they are handled
        UP(0, 1), DOWN(0, -1), LEFT(1, 0), RIGHT(-1, 0),
        UP_LEFT(1, 1), UP_RIGHT(-1, 1), DOWN_LEFT(1, -1), DOWN_RIGHT(-1, -1),
        DROP,
        // Turns, in the order they are handled
        ROT_Z(Rot.OZ), ROT_Y(Rot.OY), ROT_X(Rot.OX), ROT_NZ(Rot.NOZ), ROT_NY(Rot.NOY), ROT_NX(Rot.NOX),
        PAUSE;

        final int dx, dy;
        /// The quarter turn of a turn command, `null` otherwise.
        final Rot turn;

        Action() {
            this(0, 0, null);
        }

        Action(int dx, int dy) {
            this(dx, dy, null);
        }

        Action(Rot turn) {
            this(0, 0, turn);
        }

        Action(int dx, int dy, Rot turn) {
            this.dx = dx;
            this.dy = dy;
            this.turn = turn;
        }

        boolean isMove() {
            return dx != 0 || dy != 0;
        }
    }

    enum Sound { HIT, LAYER, EMPTY_PIT, LEVEL_UP, GAME_OVER }

    // Score factors measured from the original game
    private static final double[] PIECE_LEVEL_FACTOR = {
        0.066990, 0.139195, 0.219800, 0.308444, 0.403897, 0.507822,
        0.619062, 0.738630, 0.865802, 1.000000, 1.133333
    };
    private static final double[] DEPTH_FACTOR = {
        0, 0, 0, 0, 0, 0,
        1.557692, 1.367521, 1.217949, 1.100427, 1.000000, 0.918803, 0.852564,
        0.788996, 0.737714, 0.691774, 0.651709, 0.614850, 0.583868
    };
    private static final double[] LAYER_LEVEL_FACTOR = {
        0.096478, 0.163873, 0.242913, 0.328261, 0.422329, 0.518394,
        0.630405, 0.747501, 0.867087, 1.000000, 1.131653
    };
    private static final double[] LAYER_COUNT_FACTOR = {
        0, 1.000000, 3.703372, 8.104827, 14.188325, 22.144941
    };

    static final double STEP_TIME_BASE = 5.51;
    static final double STEP_TIME_LEVEL_FACTOR = 0.64;
    static final double DROP_TIME = 0.16;
    static final double SPARK_TIME = 0.5;
    static final double LOCK_TIME = 0.05;
    static final double ANIMATION_TIME = 0.1;
    static final int MAX_LEVEL = 10;

    private final Setup setup;
    private final Pit pit;
    private final List<PolyCube> possible = new ArrayList<>();
    private final RandomGenerator random;
    private final Consumer<Sound> sounds;
    private final Set<Action> pending = EnumSet.noneOf(Action.class);

    private State state = State.PLAYING;
    private int level;
    private long score;
    private int cubesPlayed;
    private double stepTime;
    private final int cubesPerLevel;
    private double now;

    // Current block: logical position and rotation ...
    private PolyCube piece;
    private Point pos;
    private Rot matRot = Rot.IDENTITY, newMatRot = Rot.IDENTITY;
    // ... and the position shown, which follows with an animation
    private double vx, vy, vz;
    private double orgX, orgY, orgZ, transX, transY, transZ;

    // Running turn animation: the quarter turn, or null; a blocked turn swings back
    private Rot turn;
    private boolean turnBlocked;
    private double turnAngle;

    private double startRotateTime, startTranslateTime, startStepTime, startRedTime, startPauseTime;
    // redMode: the block has landed and is about to lock
    private boolean stepStarted, dropMode, lastKeyMode, redMode, dropped;
    private int dropPos, cursorPos;

    // Spark shown where a turn was blocked
    private double startSpark = -1;
    private Point sparkCell;

    Game(Setup setup, int startLevel, RandomGenerator random, Consumer<Sound> sounds, double now) {
        this.setup = setup;
        this.pit = new Pit(setup.width(), setup.height(), setup.depth());
        this.random = random;
        this.sounds = sounds;
        this.now = now;
        this.level = Math.clamp(startLevel, 0, MAX_LEVEL);
        this.stepTime = STEP_TIME_BASE * Math.pow(STEP_TIME_LEVEL_FACTOR, level);
        this.cubesPerLevel = (setup.width() + setup.height()) * 15;
        int minDim = Math.min(setup.width(), setup.height());
        for (PolyCube p : PolyCubes.ALL) {
            if (setup.blockSet().contains(p) && p.maxDim() <= minDim) possible.add(p);
        }
        newPiece();
    }

    // ------------------------------------------------------------------ state

    Setup setup() {
        return setup;
    }

    Pit pit() {
        return pit;
    }

    State state() {
        return state;
    }

    int level() {
        return level;
    }

    long score() {
        return score;
    }

    int cubesPlayed() {
        return cubesPlayed;
    }

    PolyCube piece() {
        return piece;
    }

    /// Block position (in cells), including the running move animation.
    double[] piecePosition() {
        return new double[] {vx, vy, vz};
    }

    /// Block rotation including the running turn animation, row major 3x3.
    double[] pieceRotation() {
        double[] animation = turn == null ? Rot.IDENTITY.toDouble() : turn.partial(turnAngle);
        return Rot.multiply(animation, matRot.toDouble());
    }

    /// The pit cells the block occupies, once a running turn has finished.
    List<Point> pieceCells() {
        return cubes(newMatRot, pos);
    }

    /// Spark progress 0..1, or a negative value if no spark is shown.
    double sparkProgress() {
        return startSpark < 0 ? -1 : (now - startSpark) / SPARK_TIME;
    }

    Point sparkCell() {
        return sparkCell;
    }

    // ---------------------------------------------------------------- control

    void press(Action action) {
        pending.add(action);
    }

    /// Ends the game immediately (Esc).
    void abort() {
        if (state != State.OVER) {
            state = State.OVER;
            sounds.accept(Sound.GAME_OVER);
        }
    }

    void update(double time) {
        now = time;
        handleKeys();
        if (state != State.PLAYING) return;
        animateTurn();
        animateMove();
        if (dropMode) animateDrop();
        if (!redMode) gravityStep();
        if (startSpark >= 0 && now - startSpark >= SPARK_TIME) startSpark = -1;

        // End of move: the block becomes part of the pit
        if (redMode && now - startRedTime > LOCK_TIME) {
            lockPiece();
            newPiece();
        }
    }

    private void animateTurn() {
        turnAngle = 0;
        if (turn == null) return;
        double f = (now - startRotateTime) / ANIMATION_TIME;
        if (f > 1) {
            matRot = newMatRot;
            turn = null;
        } else if (!turnBlocked) {
            turnAngle = Math.PI / 2 * f;
        } else {
            // Swing a bit and come back
            turnAngle = Math.PI / 5 * Math.sin(f * Math.PI);
        }
    }

    private void animateMove() {
        double f = (now - startTranslateTime) / ANIMATION_TIME;
        if (f > 1) {
            vx = pos.x();
            vy = pos.y();
            vz = pos.z();
        } else {
            showBetween(f);
        }
    }

    private void animateDrop() {
        double f = (now - startTranslateTime) / DROP_TIME;
        startStepTime = now;
        stepStarted = true;
        if (f > 1) {
            vz = pos.z();
            dropMode = false;
            if (!lastKeyMode) {
                redMode = true;
                startRedTime = now;
            } else {
                // A move or turn was requested during the drop: leave 100 ms for it
                startStepTime = now - stepTime + 0.1;
                lastKeyMode = false;
            }
        } else {
            showBetween(f);
        }
    }

    private void gravityStep() {
        if (!stepStarted) {
            startStepTime = now;
            stepStarted = true;
        } else if (now - startStepTime > stepTime) {
            startStepTime = now;
            cursorPos++;
            dropPos--;
            if (isAboveCursor()) {
                if (!overlaps(newMatRot, pos.plus(0, 0, 1))) {
                    moveTo(pos.plus(0, 0, 1));
                } else {
                    redMode = true;
                    startRedTime = now;
                }
            }
        }
    }

    private void handleKeys() {
        if (pending.remove(Action.PAUSE)) togglePause();
        if (state == State.OVER) {
            pending.clear();
            return;
        }
        for (Action a : Action.values()) {
            if (a.isMove()) consume(a, () -> translate(a.dx, a.dy));
        }
        if (pending.remove(Action.DROP)) drop();
        for (Action a : Action.values()) {
            if (a.turn != null) consume(a, () -> rotate(a.turn));
        }
    }

    private void togglePause() {
        if (state == State.PLAYING) {
            state = State.PAUSED;
            startPauseTime = now;
        } else if (state == State.PAUSED) {
            state = State.PLAYING;
            double d = now - startPauseTime;
            startRotateTime += d;
            startTranslateTime += d;
            startRedTime += d;
            startStepTime += d;
            if (startSpark >= 0) startSpark += d;
        }
    }

    /// Runs the handler of a pending action and keeps the action pending if it returns false.
    private void consume(Action action, BooleanSupplier handler) {
        if (pending.contains(action) && handler.getAsBoolean()) pending.remove(action);
    }

    private boolean translate(int dx, int dy) {
        if (state != State.PLAYING) return true;
        if (dropMode || redMode) {
            if (dropMode) lastKeyMode = true;
            return false;
        }
        // A blocked diagonal move slides along the wall
        boolean diagonal = dx != 0 && dy != 0;
        for (Point to : List.of(pos.plus(dx, dy, 0), pos.plus(dx, 0, 0), pos.plus(0, dy, 0))) {
            if (!overlaps(newMatRot, to)) {
                moveTo(to);
                break;
            }
            if (!diagonal) break;
        }
        return true;
    }

    private void drop() {
        if (state != State.PLAYING || dropMode || redMode || lastKeyMode) return;
        dropMode = true;
        dropped = true;
        Point to = pos;
        while (!overlaps(newMatRot, to.plus(0, 0, 1))) to = to.plus(0, 0, 1);
        cursorPos = bottom(to);
        moveTo(to);
    }

    private boolean rotate(Rot quarter) {
        if (state != State.PLAYING) return true;
        if ((turn != null && !turnBlocked) || dropMode || redMode) {
            if (dropMode) lastKeyMode = true;
            return false;
        }
        turn = quarter;
        turnBlocked = false;
        startRotateTime = now;
        newMatRot = quarter.times(matRot);

        Overlap o = overlap(newMatRot, pos);
        if (o.blocked) {
            // Try to combine the turn with a move back into the pit
            Point kicked = pos.plus(o.back);
            if (overlaps(newMatRot, kicked)) {
                turnBlocked = true;
                newMatRot = matRot;
                startSpark = now;
                sparkCell = o.cell;
                sounds.accept(Sound.HIT);
            } else {
                moveTo(kicked);
            }
        }
        return true;
    }

    /// Sets the logical position and starts the animation towards it.
    private void moveTo(Point to) {
        pos = to;
        startTranslateTime = now;
        orgX = vx;
        orgY = vy;
        orgZ = vz;
        transX = pos.x() - vx;
        transY = pos.y() - vy;
        transZ = pos.z() - vz;
    }

    private void showBetween(double f) {
        vx = orgX + transX * f;
        vy = orgY + transY * f;
        vz = orgZ + transZ * f;
    }

    /// True when every cube of the block is above the gravity cursor.
    private boolean isAboveCursor() {
        return pieceCells().stream().allMatch(c -> c.z() < cursorPos);
    }

    private int bottom(Point at) {
        return cubes(newMatRot, at).stream().mapToInt(Point::z).max().orElse(0);
    }

    private void lockPiece() {
        for (Point c : pieceCells()) pit.set(c, true);
        int layers = pit.removeFullLayers();
        boolean pitEmpty = pit.isEmpty();
        if (layers > 0) sounds.accept(pitEmpty ? Sound.EMPTY_PIT : Sound.LAYER);
        score += score(setup, level, piece, dropped ? Math.max(0, dropPos) : 0, layers, pitEmpty);
        cubesPlayed += piece.size();
        if (level < MAX_LEVEL && cubesPlayed >= cubesPerLevel * (level + 1)) {
            level++;
            stepTime *= STEP_TIME_LEVEL_FACTOR;
            sounds.accept(Sound.LEVEL_UP);
        }
    }

    /// Score for one block.
    ///
    /// @param dropPos  `depth - 1` minus the gravity steps taken before the block was dropped,
    ///                 so the earlier the drop, the higher; 0 for a block that was not dropped
    /// @param layers   number of layers the block completed
    /// @param pitEmpty whether the pit is empty afterwards (counts like two more layers)
    static int score(Setup setup, int level, PolyCube piece, int dropPos, int layers, boolean pitEmpty) {
        double fPos = (double) dropPos / (setup.depth() - 1);
        double pScore = (piece.lowScore() + (piece.highScore() - piece.lowScore()) * fPos)
                * PIECE_LEVEL_FACTOR[level];
        double layerBase = setup.blockSet().lineBase * LAYER_LEVEL_FACTOR[level];
        double lineScore = layerBase * LAYER_COUNT_FACTOR[Math.min(layers, 5)];
        double pitScore = pitEmpty ? layerBase * LAYER_COUNT_FACTOR[2] : 0;
        int total = (int) Math.floor((lineScore + pScore + pitScore) * DEPTH_FACTOR[setup.depth()] + 0.5);
        return Math.max(1, total);
    }

    private void newPiece() {
        matRot = newMatRot = Rot.IDENTITY;
        pending.clear();
        startRotateTime = startTranslateTime = 0;
        turn = null;
        turnAngle = 0;
        dropMode = lastKeyMode = redMode = dropped = false;
        stepStarted = false;
        dropPos = pit.depth() - 1;
        cursorPos = 0;

        piece = possible.get(random.nextInt(possible.size()));
        // New blocks appear in the bottom left corner
        pos = new Point(pit.width() - piece.width(), 0, 0);
        vx = pos.x();
        vy = vz = 0;
        if (overlaps(newMatRot, pos)) {
            state = State.OVER;
            sounds.accept(Sound.GAME_OVER);
        }
    }

    // --------------------------------------------------------------- geometry

    /// Result of placing the block: whether it collides, the largest offset back into the pit
    /// per axis and the last colliding cell.
    private record Overlap(boolean blocked, Point back, Point cell) {
    }

    private boolean overlaps(Rot rot, Point at) {
        return overlap(rot, at).blocked;
    }

    private Overlap overlap(Rot rot, Point at) {
        boolean blocked = false;
        Point cell = null;
        int bx = 0, by = 0, bz = 0;
        for (Point c : cubes(rot, at)) {
            if (pit.blocked(c)) {
                blocked = true;
                cell = c;
            }
            Point o = pit.outOfBounds(c);
            bx = larger(bx, o.x());
            by = larger(by, o.y());
            bz = larger(bz, o.z());
        }
        return new Overlap(blocked, new Point(bx, by, bz), cell);
    }

    private static int larger(int a, int b) {
        return Math.abs(b) > Math.abs(a) ? b : a;
    }

    /// Pit cells occupied by the current block with the given rotation and offset.
    List<Point> cubes(Rot rot, Point at) {
        return piece.cubes().stream().map(c -> piece.rotateCell(rot, c).plus(at)).toList();
    }
}
