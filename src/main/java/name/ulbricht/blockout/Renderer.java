package name.ulbricht.blockout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/// Draws the DOS screen layout on a 640x480 logical canvas.
final class Renderer {

    static final double WIDTH = 640, HEIGHT = 480;

    // Screen layout
    private static final double PIT_X = 48, PIT_Y = 2, PIT_SIZE = 476;
    private static final double COL_X = 6, COL_W = 36;
    private static final double PANEL_X = 530, PANEL_W = 104;
    // Height of one layer in the level column, the same for every pit depth
    private static final double SLOT_H = 20;

    // Camera: looking straight down into the pit, 60 degree field of view
    private static final double TAN_HALF_FOV = Math.tan(Math.toRadians(30));
    private static final double PIT_TOP_Z = 0.87;
    private static final double NEAR_Z = 0.05;

    private static final Font SMALL = Font.font("Monospaced", FontWeight.BOLD, 10);
    private static final Font NUMBER = Font.font("Monospaced", FontWeight.BOLD, 20);
    private static final Font MEDIUM = Font.font("Monospaced", FontWeight.BOLD, 16);
    private static final Font LARGE = Font.font("Monospaced", FontWeight.BOLD, 28);
    private static final Font LOGO = Font.font("Arial Black", FontWeight.BLACK, 30);

    private final GraphicsContext gc;

    // Projection of the current pit
    private int pw, ph, pd;
    private double cubeSide, originX, originY;

    Renderer(GraphicsContext gc) {
        this.gc = gc;
        gc.setLineCap(StrokeLineCap.SQUARE);
        gc.setLineJoin(StrokeLineJoin.MITER);
    }

    // ----------------------------------------------------------------- screens

    /// Background with pit, level column and score panel. `pit` may be `null` for an empty pit.
    void drawScreen(Setup setup, Pit pit, int level, long score, int cubes, long highScore) {
        gc.setFill(Ega.BLACK);
        gc.fillRect(0, 0, WIDTH, HEIGHT);
        setPit(setup.width(), setup.height(), setup.depth());
        drawPitGrid();
        if (pit != null) drawLandedCubes(pit);
        drawLevelColumn(pit, setup.depth(), level);
        drawPanel(setup, score, cubes, highScore);
    }

    void drawGame(Game game, long highScore) {
        drawScreen(game.setup(), game.pit(), game.level(), game.score(), game.cubesPlayed(),
                Math.max(highScore, game.score()));
        if (game.state() != Game.State.OVER) {
            drawPiece(game);
            drawSpark(game);
        }
        if (game.state() == Game.State.PAUSED) drawMessage("PAUSED", null);
        if (game.state() == Game.State.OVER) drawMessage("GAME OVER", "Press ENTER");
    }

    void drawMessage(String text, String hint) {
        double cx = PIT_X + PIT_SIZE / 2, cy = PIT_Y + PIT_SIZE / 2;
        double w = 260, h = hint == null ? 56 : 80;
        gc.setFill(Ega.BLACK);
        gc.fillRect(cx - w / 2, cy - h / 2, w, h);
        frame(cx - w / 2, cy - h / 2, w, h, Ega.LIGHT_BLUE);
        text(text, cx, cy - (hint == null ? 0 : 12), LARGE, Ega.YELLOW, TextAlignment.CENTER);
        if (hint != null) text(hint, cx, cy + 22, SMALL, Ega.LIGHT_CYAN, TextAlignment.CENTER);
    }

    /// Menu box in the middle of the pit. Items may contain a value after a tab.
    void drawMenu(String title, List<String> items, int selected) {
        double w = 380, lineH = 24, h = 64 + items.size() * lineH;
        double x = PIT_X + (PIT_SIZE - w) / 2, y = dialog(title, w, h);
        for (int i = 0; i < items.size(); i++) {
            double ly = y + 56 + i * lineH;
            boolean sel = i == selected;
            if (sel) {
                gc.setFill(Ega.BLUE);
                gc.fillRect(x + 12, ly - lineH / 2 + 1, w - 24, lineH - 2);
            }
            String[] parts = items.get(i).split("\t", 2);
            Color c = sel ? Ega.WHITE : Ega.LIGHT_CYAN;
            text(parts[0], x + 24, ly, MEDIUM, c, TextAlignment.LEFT);
            if (parts.length > 1) text(parts[1], x + w - 24, ly, MEDIUM, sel ? Ega.YELLOW : Ega.LIGHT_GREEN,
                    TextAlignment.RIGHT);
        }
    }

    /// Hall of fame table; `editRow` is the row being typed or -1.
    void drawHallOfFame(Setup setup, List<HallOfFame.Entry> entries, int editRow, String editText,
            boolean cursorOn) {
        double w = 440, h = 330;
        double x = PIT_X + (PIT_SIZE - w) / 2, y = dialog("HALL OF FAME", w, h);
        text(setup.name() + "  [" + setup.description() + "]", x + w / 2, y + 46,
                SMALL, Ega.LIGHT_CYAN, TextAlignment.CENTER);
        for (int i = 0; i < HallOfFame.SIZE; i++) {
            double ly = y + 76 + i * 23;
            boolean edit = i == editRow;
            Color c = edit ? Ega.WHITE : Ega.LIGHT_CYAN;
            text((i + 1) + ".", x + 46, ly, MEDIUM, Ega.LIGHT_GREEN, TextAlignment.RIGHT);
            if (i < entries.size()) {
                HallOfFame.Entry e = entries.get(i);
                String name = edit ? editText + (cursorOn ? "_" : " ") : e.name();
                text(name, x + 58, ly, MEDIUM, c, TextAlignment.LEFT);
                text(Long.toString(e.score()), x + 300, ly, MEDIUM, edit ? Ega.YELLOW : Ega.LIGHT_GREEN,
                        TextAlignment.RIGHT);
                text(e.date(), x + w - 18, ly, SMALL, Ega.LIGHT_GRAY, TextAlignment.RIGHT);
            } else {
                text("..........", x + 58, ly, MEDIUM, Ega.DARK_GRAY, TextAlignment.LEFT);
            }
        }
        text(editRow >= 0 ? "Enter your name" : "Press ENTER", x + w / 2, y + h - 16, SMALL,
                Ega.LIGHT_CYAN, TextAlignment.CENTER);
    }

    // --------------------------------------------------------------- the pit

    private void setPit(int w, int h, int d) {
        pw = w;
        ph = h;
        pd = d;
        if (w > h) {
            cubeSide = 1.0 / w;
            originX = -0.5;
            originY = -0.5 + (w - h) * cubeSide / 2;
        } else {
            cubeSide = 1.0 / h;
            originX = -0.5 + (h - w) * cubeSide / 2;
            originY = -0.5;
        }
    }

    /// Projects a pit point (in cell units) to the screen; x grows to the left, y upwards.
    /// Returns `null` for points too close to the camera.
    private double[] project(double x, double y, double z) {
        double wx = originX + x * cubeSide;
        double wy = originY + y * cubeSide;
        double wz = PIT_TOP_Z + z * cubeSide;
        if (wz < NEAR_Z) return null;
        double half = PIT_SIZE / 2;
        double f = half / (wz * TAN_HALF_FOV);
        return new double[] {PIT_X + half - wx * f, PIT_Y + half - wy * f};
    }

    private void line(double x1, double y1, double z1, double x2, double y2, double z2) {
        double[] a = project(x1, y1, z1), b = project(x2, y2, z2);
        if (a == null || b == null) return;
        gc.strokeLine(a[0], a[1], b[0], b[1]);
    }

    private void drawPitGrid() {
        gc.setStroke(Ega.GREEN);
        gc.setLineWidth(1);
        // Floor
        for (int i = 0; i <= pw; i++) line(i, 0, pd, i, ph, pd);
        for (int j = 0; j <= ph; j++) line(0, j, pd, pw, j, pd);
        // Walls: lines running down to the floor ...
        for (int i = 0; i <= pw; i++) {
            line(i, 0, 0, i, 0, pd);
            line(i, ph, 0, i, ph, pd);
        }
        for (int j = 0; j <= ph; j++) {
            line(0, j, 0, 0, j, pd);
            line(pw, j, 0, pw, j, pd);
        }
        // ... and one ring per layer
        for (int k = 0; k < pd; k++) {
            line(0, 0, k, pw, 0, k);
            line(pw, 0, k, pw, ph, k);
            line(pw, ph, k, 0, ph, k);
            line(0, ph, k, 0, 0, k);
        }
    }

    private void drawLandedCubes(Pit pit) {
        // Painter's algorithm: farthest cubes first
        List<int[]> cells = new ArrayList<>();
        for (int z = 0; z < pd; z++) {
            for (int y = 0; y < ph; y++) {
                for (int x = 0; x < pw; x++) {
                    if (pit.filled(x, y, z)) cells.add(new int[] {x, y, z});
                }
            }
        }
        cells.sort(Comparator.comparingDouble((int[] c) -> distance(c)).reversed());
        gc.setLineWidth(1);
        for (int[] c : cells) drawCube(pit, c[0], c[1], c[2]);
    }

    private double distance(int[] c) {
        double x = originX + (c[0] + 0.5) * cubeSide;
        double y = originY + (c[1] + 0.5) * cubeSide;
        double z = PIT_TOP_Z + (c[2] + 0.5) * cubeSide;
        return x * x + y * y + z * z;
    }

    private void drawCube(Pit pit, int x, int y, int z) {
        Color color = Ega.layer(z, pd);
        double x0 = originX + x * cubeSide, x1 = x0 + cubeSide;
        double y0 = originY + y * cubeSide, y1 = y0 + cubeSide;
        // Side faces turned towards the camera (which sits at x = y = 0)
        if (x1 < 0 && !pit.filled(x + 1, y, z)) face(color, x + 1, y, z, x + 1, y + 1, z, x + 1, y + 1, z + 1, x + 1, y, z + 1);
        if (x0 > 0 && !pit.filled(x - 1, y, z)) face(color, x, y, z, x, y + 1, z, x, y + 1, z + 1, x, y, z + 1);
        if (y1 < 0 && !pit.filled(x, y + 1, z)) face(color, x, y + 1, z, x + 1, y + 1, z, x + 1, y + 1, z + 1, x, y + 1, z + 1);
        if (y0 > 0 && !pit.filled(x, y - 1, z)) face(color, x, y, z, x + 1, y, z, x + 1, y, z + 1, x, y, z + 1);
        if (!pit.filled(x, y, z - 1)) face(color, x, y, z, x + 1, y, z, x + 1, y + 1, z, x, y + 1, z);
    }

    private void face(Color color, double... p) {
        double[] xs = new double[4], ys = new double[4];
        for (int i = 0; i < 4; i++) {
            double[] s = project(p[i * 3], p[i * 3 + 1], p[i * 3 + 2]);
            xs[i] = s[0];
            ys[i] = s[1];
        }
        gc.setFill(color);
        gc.fillPolygon(xs, ys, 4);
        gc.setStroke(Ega.BLACK);
        gc.strokePolygon(xs, ys, 4);
    }

    private void drawPiece(Game game) {
        PolyCube piece = game.piece();
        double[] r = game.pieceRotation();
        double[] pos = game.piecePosition();
        gc.setStroke(Ega.WHITE);
        gc.setLineWidth(1.4);
        for (PolyCube.Edge e : piece.edges()) {
            double[] a = piece.rotateVertex(r, e.from()), b = piece.rotateVertex(r, e.to());
            line(a[0] + pos[0], a[1] + pos[1], a[2] + pos[2], b[0] + pos[0], b[1] + pos[1], b[2] + pos[2]);
        }
    }

    private void drawSpark(Game game) {
        double t = game.sparkProgress();
        Point cell = game.sparkCell();
        if (t < 0 || cell == null) return;
        double[] s = project(cell.x() + 0.5, cell.y() + 0.5, Math.max(0, cell.z()) + 0.5);
        if (s == null) return;
        double size = 32 * ((1.0 - (double) cell.z() / pd) * 0.33 + 0.66);
        double radius = size * Math.sin(Math.min(1, t) * Math.PI);
        gc.setLineWidth(2);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + t * Math.PI;
            double len = (i % 2 == 0) ? radius : radius * 0.55;
            gc.setStroke(i % 2 == 0 ? Ega.YELLOW : Ega.WHITE);
            gc.strokeLine(s[0], s[1], s[0] + Math.cos(a) * len, s[1] + Math.sin(a) * len);
        }
    }

    // ------------------------------------------------------------ side bars

    private void drawLevelColumn(Pit pit, int depth, int level) {
        // Level number
        frame(COL_X, 6, COL_W, 52, Ega.BLUE);
        text("LEVEL", COL_X + COL_W / 2, 17, SMALL, Ega.LIGHT_CYAN, TextAlignment.CENTER);
        text(Integer.toString(level), COL_X + COL_W / 2, 40, NUMBER, Ega.LIGHT_RED, TextAlignment.CENTER);

        // One slot per layer, filled layers in their color
        double top = 64, bottom = 474;
        frame(COL_X, top, COL_W, bottom - top, Ega.BLUE);
        double y = bottom - 2 - SLOT_H * depth;
        gc.setStroke(Ega.LIGHT_GRAY);
        gc.setLineWidth(1);
        gc.strokeLine(COL_X + 3.5, y, COL_X + 3.5, bottom - 2);
        gc.strokeLine(COL_X + COL_W - 3.5, y, COL_X + COL_W - 3.5, bottom - 2);
        for (int z = 0; z < depth; z++, y += SLOT_H) {
            gc.setStroke(Ega.LIGHT_GRAY);
            gc.strokeLine(COL_X + 3, y + 0.5, COL_X + 7, y + 0.5);
            gc.strokeLine(COL_X + COL_W - 7, y + 0.5, COL_X + COL_W - 3, y + 0.5);
            if (pit != null && !pit.layerEmpty(z)) {
                gc.setFill(Ega.layer(z, depth));
                gc.fillRect(COL_X + 6, y + 2, COL_W - 12, SLOT_H - 3);
            }
        }
    }

    private void drawPanel(Setup setup, long score, int cubes, long highScore) {
        double cx = PANEL_X + PANEL_W / 2;

        // Logo
        logo("BLOCK", cx, 22, Ega.LIGHT_RED, Ega.LIGHT_BLUE);
        logo("OUT", cx, 52, Ega.LIGHT_BLUE, Ega.WHITE);

        infoBox("SCORE", Long.toString(score), 78, Ega.LIGHT_GREEN, NUMBER);
        infoBox("CUBES PLAYED", Integer.toString(cubes), 136, Ega.LIGHT_GREEN, NUMBER);
        infoBox("HIGH SCORE", Long.toString(highScore), 304, Ega.LIGHT_GREEN, NUMBER);
        infoBox("PIT", setup.dimensions(), 362, Ega.LIGHT_RED, NUMBER);
        infoBox("BLOCK SET", setup.blockSet().name(), 420, Ega.BROWN.brighter(), MEDIUM);
    }

    private void infoBox(String label, String value, double y, Color valueColor, Font font) {
        frame(PANEL_X, y, PANEL_W, 50, Ega.BLUE);
        text(label, PANEL_X + PANEL_W / 2, y + 12, SMALL, Ega.LIGHT_CYAN, TextAlignment.CENTER);
        frame(PANEL_X + 6, y + 22, PANEL_W - 12, 24, Ega.BLUE);
        text(value, PANEL_X + PANEL_W - 10, y + 34, font, valueColor, TextAlignment.RIGHT);
    }

    private void logo(String s, double cx, double cy, Color fill, Color outline) {
        gc.setFont(LOGO);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.setLineWidth(2);
        gc.setStroke(outline);
        gc.strokeText(s, cx, cy, PANEL_W - 4);
        gc.setFill(fill);
        gc.fillText(s, cx, cy, PANEL_W - 4);
    }

    // ---------------------------------------------------------------- helpers

    /// Clears a box of the given size in the middle of the pit, draws its double frame and
    /// title and returns its top edge.
    private double dialog(String title, double w, double h) {
        double x = PIT_X + (PIT_SIZE - w) / 2, y = PIT_Y + (PIT_SIZE - h) / 2;
        gc.setFill(Ega.BLACK);
        gc.fillRect(x, y, w, h);
        frame(x, y, w, h, Ega.LIGHT_BLUE);
        frame(x + 3, y + 3, w - 6, h - 6, Ega.BLUE);
        text(title, x + w / 2, y + 24, MEDIUM, Ega.YELLOW, TextAlignment.CENTER);
        return y;
    }

    private void frame(double x, double y, double w, double h, Color color) {
        gc.setStroke(color);
        gc.setLineWidth(2);
        gc.strokeRect(x + 1, y + 1, w - 2, h - 2);
    }

    private void text(String s, double x, double y, Font font, Color color, TextAlignment align) {
        gc.setFont(font);
        gc.setFill(color);
        gc.setTextAlign(align);
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText(s, x, y);
    }
}
