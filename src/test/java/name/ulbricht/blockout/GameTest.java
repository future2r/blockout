package name.ulbricht.blockout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

class GameTest {

    private static final PolyCube SINGLE = PolyCubes.ALL.get(0);
    private static final PolyCube T = PolyCubes.ALL.get(7);
    private static final List<Rot> QUARTER_TURNS = List.of(Rot.OX, Rot.OY, Rot.OZ, Rot.NOX, Rot.NOY, Rot.NOZ);

    /// All rotations reachable with quarter turns.
    private static Set<Rot> orientations() {
        Set<Rot> seen = new HashSet<>();
        List<Rot> todo = new ArrayList<>(List.of(Rot.IDENTITY));
        while (!todo.isEmpty()) {
            Rot r = todo.removeLast();
            if (seen.add(r)) QUARTER_TURNS.forEach(t -> todo.add(t.times(r)));
        }
        return seen;
    }

    @Test
    void blockScoreMatchesOriginalTable() {
        // Values from the BlockOut II score table for 5x5x12 FLAT (not dropped / dropped from top)
        assertEquals(1, Game.score(Setup.FLAT_FUN, 0, SINGLE, 0, 0, false));
        assertEquals(9, Game.score(Setup.FLAT_FUN, 0, SINGLE, 11, 0, false));
        assertEquals(14, Game.score(Setup.FLAT_FUN, 10, SINGLE, 0, 0, false));
        assertEquals(151, Game.score(Setup.FLAT_FUN, 10, SINGLE, 11, 0, false));
        assertEquals(2, Game.score(Setup.FLAT_FUN, 0, T, 0, 0, false));
        assertEquals(26, Game.score(Setup.FLAT_FUN, 0, T, 11, 0, false));
        assertEquals(445, Game.score(Setup.FLAT_FUN, 10, T, 11, 0, false));
    }

    @Test
    void layerScoreMatchesOriginalTable() {
        // Line score part only: subtract the block score
        int block = Game.score(Setup.FLAT_FUN, 0, SINGLE, 0, 0, false);
        assertEquals(63, Game.score(Setup.FLAT_FUN, 0, SINGLE, 0, 1, false) - block, 1);
        assertEquals(232, Game.score(Setup.FLAT_FUN, 0, SINGLE, 0, 2, false) - block, 1);
        assertEquals(508, Game.score(Setup.FLAT_FUN, 0, SINGLE, 0, 3, false) - block, 1);
        int block10 = Game.score(Setup.FLAT_FUN, 10, SINGLE, 0, 0, false);
        assertEquals(736, Game.score(Setup.FLAT_FUN, 10, SINGLE, 0, 1, false) - block10, 1);
    }

    @Test
    void allQuarterTurnsOfEveryBlockAreCongruent() {
        Set<Rot> all = orientations();
        assertEquals(24, all.size());
        Game game = new Game(Setup.OUT_OF_CONTROL, 0, new Random(1), _ -> { }, 0);
        for (Rot r : all) {
            assertEquals(game.piece().size(), new HashSet<>(game.cubes(r, Point.ZERO)).size(), r.toString());
        }
    }

    @Test
    void turnAnimationEndsOnTheQuarterTurn() {
        for (Rot t : QUARTER_TURNS) {
            assertArrayEquals(t.toDouble(), t.partial(Math.PI / 2), 1e-12, t.toString());
            assertArrayEquals(Rot.IDENTITY.toDouble(), t.partial(0), 1e-12, t.toString());
        }
    }

    @Test
    void drawnBlockCoversTheCellsItOccupies() {
        // The wireframe must enclose exactly the cells used for collisions, in every orientation
        Set<Rot> all = orientations();
        for (PolyCube piece : PolyCubes.ALL) {
            Game game = gameWith(piece);
            for (Rot rot : all) {
                double[] lo = {1e9, 1e9, 1e9}, hi = {-1e9, -1e9, -1e9};
                for (PolyCube.Edge e : piece.edges()) {
                    for (Point p : List.of(e.from(), e.to())) {
                        double[] v = piece.rotateVertex(rot.toDouble(), p);
                        for (int i = 0; i < 3; i++) {
                            lo[i] = Math.min(lo[i], v[i]);
                            hi[i] = Math.max(hi[i], v[i]);
                        }
                    }
                }
                List<Point> cells = game.cubes(rot, Point.ZERO);
                for (int i = 0; i < 3; i++) {
                    int axis = i;
                    String what = "piece " + PolyCubes.ALL.indexOf(piece) + " " + rot + " axis " + axis;
                    assertEquals(cells.stream().mapToInt(c -> c.get(axis)).min().orElseThrow(), lo[i], 1e-9, what);
                    assertEquals(cells.stream().mapToInt(c -> c.get(axis) + 1).max().orElseThrow(), hi[i], 1e-9, what);
                }
            }
        }
    }

    /// A game whose current block is `piece`.
    private static Game gameWith(PolyCube piece) {
        for (long seed = 0; ; seed++) {
            Game game = new Game(Setup.OUT_OF_CONTROL, 0, new Random(seed), _ -> { }, 0);
            if (game.piece().equals(piece)) return game;
        }
    }

    @Test
    void fullLayerIsRemovedAndUpperLayersFall() {
        Pit pit = new Pit(3, 3, 6);
        for (int x = 0; x < 3; x++) for (int y = 0; y < 3; y++) pit.set(new Point(x, y, 5), true);
        pit.set(new Point(1, 1, 4), true);
        assertEquals(1, pit.removeFullLayers());
        assertTrue(pit.filled(1, 1, 5));
        assertFalse(pit.filled(0, 0, 5));
        assertTrue(pit.layerEmpty(4));
        assertFalse(pit.isEmpty());
    }

    @Test
    void droppedBlockLandsOnTheFloorAndScores() {
        List<Game.Sound> sounds = new ArrayList<>();
        Game game = new Game(Setup.FLAT_FUN, 0, new Random(3), sounds::add, 0);
        PolyCube first = game.piece();
        game.update(0.01);
        game.press(Game.Action.DROP);
        for (double t = 0.02; t < 1; t += 0.01) game.update(t);
        assertEquals(first.size(), game.cubesPlayed());
        assertTrue(game.score() > 0);
        assertFalse(game.pit().layerEmpty(Setup.FLAT_FUN.depth() - 1));
        assertEquals(Game.State.PLAYING, game.state());
    }

    @Test
    void gravityMovesBlockOneLayerPerStep() {
        Game game = new Game(Setup.FLAT_FUN, 5, new Random(5), _ -> { }, 0);
        double step = Game.STEP_TIME_BASE * Math.pow(Game.STEP_TIME_LEVEL_FACTOR, 5);
        game.update(0.001);
        game.update(step + 0.01);
        game.update(step + 0.5 + Game.ANIMATION_TIME);
        // flat blocks fall from layer 0 to layer 1 with the first step
        assertEquals(1.0, game.piecePosition()[2], 1e-9);
    }

    @Test
    void rotationsInTheCornerKeepTheBlockInsideThePit() {
        // New blocks start in a corner, so most turns need a kick back into the pit
        Game game = new Game(Setup.MANIA_3D, 0, new Random(0), _ -> { }, 0);
        Game.Action[] turns = {Game.Action.ROT_X, Game.Action.ROT_Y, Game.Action.ROT_Z, Game.Action.ROT_NY};
        for (int i = 0; i < 12; i++) {
            game.press(turns[i % turns.length]);
            game.update(i * 0.2 + 0.01);
            game.update(i * 0.2 + 0.15);
            for (Point c : game.pieceCells()) assertTrue(game.pit().inside(c), "turn " + i + ": " + c);
        }
        assertEquals(Game.State.PLAYING, game.state());
    }

    @Test
    void edgesOfFlatSquareFormOnlyTheOutline() {
        PolyCube square = PolyCubes.ALL.get(6);
        // 2x2x1 box: 4 vertical edges + 2 rings of 8 unit edges
        assertEquals(4 + 16, square.edges().size());
    }

    @Test
    void blockSetsHaveTheOriginalSizes() {
        assertEquals(8, PolyCubes.ALL.stream().filter(BlockSet.FLAT::contains).count());
        assertEquals(7, PolyCubes.ALL.stream().filter(BlockSet.BASIC::contains).count());
        assertEquals(41, PolyCubes.ALL.size());
    }
}
