package name.ulbricht.blockout;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

import javafx.application.Platform;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.transform.Transform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/// Renders a few scenes into PNG files for visual checks:
/// `mvnw test -Dtest=ScreenshotTest -Dshots=<dir>`.
@EnabledIfSystemProperty(named = "shots", matches = ".+")
class ScreenshotTest {

    @Test
    void renderScenes() throws Exception {
        Path dir = Path.of(System.getProperty("shots"));
        Files.createDirectories(dir);
        Platform.startup(() -> { });
        CompletableFuture<Void> done = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                shoot(dir.resolve("game.png"), r -> r.drawGame(playedGame(Setup.FLAT_FUN, 7, 40), 12345));
                shoot(dir.resolve("game3x3.png"), r -> r.drawGame(playedGame(Setup.MANIA_3D, 2, 9), 1806));
                shoot(dir.resolve("game7x4.png"), r -> r.drawGame(playedGame(new Setup(7, 4, 8, BlockSet.EXTENDED), 3, 14), 0));
                Game right = pushedRight();
                shoot(dir.resolve("right-falling.png"), r -> r.drawGame(right, 0));
                right.press(Game.Action.DROP);
                for (double t = 3.01; t < 4; t += 0.01) {
                    right.update(t);
                    if (right.cubesPlayed() > 0) break;
                }
                shoot(dir.resolve("right-landed.png"), r -> r.drawScreen(right.setup(), right.pit(), 0, 0, 0, 0));
                shoot(dir.resolve("menu.png"), r -> {
                    r.drawScreen(Setup.FLAT_FUN, null, 0, 0, 0, 0);
                    r.drawMenu("CHOOSE SETUP", List.of("Flat Fun\t5x5x12 FLAT", "3D Mania\t3x3x10 BASIC",
                            "Out of Control\t5x5x10 EXTENDED", "Change Setup"), 1);
                });
                shoot(dir.resolve("halloffame.png"), r -> {
                    r.drawScreen(Setup.FLAT_FUN, null, 0, 0, 0, 0);
                    r.drawHallOfFame(Setup.FLAT_FUN, List.of(new HallOfFame.Entry("Frank", 12345, "27.10.26"),
                            new HallOfFame.Entry("", 999, "27.10.26")), 1, "Clau", true);
                });
                done.complete(null);
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        });
        done.get(30, TimeUnit.SECONDS);
    }

    /// Plays a game by dropping blocks at random positions, then starts a turn of the next
    /// block, so the picture shows the turn animation (or the spark of a blocked turn).
    private static Game playedGame(Setup setup, long seed, int blocks) {
        Random rnd = new Random(seed);
        Game game = new Game(setup, 0, new Random(seed), _ -> { }, 0);
        double t = 0.01;
        Game.Action[] turns = {Game.Action.ROT_X, Game.Action.ROT_Z, Game.Action.ROT_Y};
        for (int b = 0; b < blocks && game.state() == Game.State.PLAYING; b++) {
            List<Game.Action> moves = new ArrayList<>();
            if (rnd.nextBoolean()) moves.add(turns[rnd.nextInt(turns.length)]);
            for (int i = rnd.nextInt(setup.width()); i > 0; i--) moves.add(Game.Action.RIGHT);
            for (int i = rnd.nextInt(setup.height()); i > 0; i--) moves.add(Game.Action.DOWN);
            for (Game.Action a : moves) {
                game.press(a);
                game.update(t += 0.01);
                game.update(t += 0.15);
            }
            game.press(Game.Action.DROP);
            for (int i = 0; i < 40; i++) game.update(t += 0.01);
        }
        game.update(t += 0.01);
        game.press(Game.Action.ROT_X);
        game.update(t += 0.01);
        game.update(t += 0.05);
        return game;
    }

    /// A turned flat block pushed against the right wall, still at the top of the pit.
    private static Game pushedRight() {
        Game game = null;
        for (long seed = 0; game == null || game.piece().size() < 4 || game.piece().depth() > 1; seed++) {
            game = new Game(Setup.FLAT_FUN, 0, new Random(seed), _ -> { }, 0);
        }
        double t = 0.01;
        game.update(t);
        game.press(Game.Action.ROT_Z);
        game.update(t += 0.01);
        game.update(t += 0.15);
        for (int i = 0; i < 6; i++) {
            game.press(Game.Action.RIGHT);
            game.update(t += 0.01);
            game.update(t += 0.15);
        }
        return game;
    }

    private static void shoot(Path file, Consumer<Renderer> scene) throws IOException {
        Canvas canvas = new Canvas(Renderer.WIDTH * 2, Renderer.HEIGHT * 2);
        canvas.getGraphicsContext2D().setTransform(2, 0, 0, 2, 0, 0);
        scene.accept(new Renderer(canvas.getGraphicsContext2D()));
        SnapshotParameters params = new SnapshotParameters();
        params.setTransform(Transform.scale(1, 1));
        WritableImage img = canvas.snapshot(params, null);
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        PixelReader pr = img.getPixelReader();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) out.setRGB(x, y, pr.getArgb(x, y));
        }
        ImageIO.write(out, "png", file.toFile());
    }
}
