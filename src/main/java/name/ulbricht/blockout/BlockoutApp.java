package name.ulbricht.blockout;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.stage.Screen;
import javafx.stage.Stage;

/// Window, main loop, menus and keyboard handling.
public final class BlockoutApp extends Application {

    private enum View { MAIN, CHOOSE_SETUP, CHANGE_SETUP, START_LEVEL, GAME, ENTER_NAME, HALL_OF_FAME }

    private enum MainItem { START_GAME, CHOOSE_SETUP, HALL_OF_FAME, SOUND, QUIT }

    // The original's terms: "length" is the horizontal extent, "width" the vertical one
    private enum SetupItem { LENGTH, WIDTH, DEPTH, BLOCK_SET, START_GAME }

    private static final List<Setup> PRESETS = List.of(Setup.FLAT_FUN, Setup.MANIA_3D, Setup.OUT_OF_CONTROL);
    private static final Path HOME = Path.of(System.getProperty("user.home"), ".blockout");

    private final Settings settings = new Settings(HOME.resolve("settings.properties"));
    private final HallOfFame hallOfFame = new HallOfFame(HOME.resolve("halloffame.txt"));
    private final Speaker speaker = new Speaker(settings.sound);
    private final RandomGenerator random = RandomGenerator.getDefault();

    private Stage stage;
    private Canvas canvas;
    private Renderer renderer;
    private double startNanos = -1, time;

    private View screen = View.MAIN;
    private int selected;
    private Game game;
    private int editRank = -1;
    private String editText = "";

    /// Created by the JavaFX launcher.
    public BlockoutApp() {
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        canvas = new Canvas(Renderer.WIDTH, Renderer.HEIGHT);
        renderer = new Renderer(canvas.getGraphicsContext2D());
        Pane root = new Pane(canvas);
        root.setStyle("-fx-background-color: black;");
        canvas.widthProperty().bind(root.widthProperty());
        canvas.heightProperty().bind(root.heightProperty());

        // Largest window scale in steps of 0.5 that fits on the screen
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        double scale = Math.max(1, Math.floor(Math.min(bounds.getWidth() / Renderer.WIDTH,
                (bounds.getHeight() - 60) / Renderer.HEIGHT) * 2) / 2);
        Scene scene = new Scene(root, Renderer.WIDTH * scale, Renderer.HEIGHT * scale);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::keyPressed);
        scene.addEventFilter(KeyEvent.KEY_TYPED, this::keyTyped);

        stage.setTitle("Blockout");
        stage.setScene(scene);
        stage.setFullScreenExitHint("");
        stage.show();

        new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (startNanos < 0) startNanos = now;
                time = (now - startNanos) / 1e9;
                frame();
            }
        }.start();
    }

    @Override
    public void stop() {
        settings.save();
    }

    // -------------------------------------------------------------- main loop

    private void frame() {
        if (game != null && screen == View.GAME) game.update(time);

        // Scale the 640x480 frame to the window, centered, keeping the aspect ratio
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth(), h = canvas.getHeight();
        gc.setTransform(1, 0, 0, 1, 0, 0);
        gc.setFill(Ega.BLACK);
        gc.fillRect(0, 0, w, h);
        double scale = Math.min(w / Renderer.WIDTH, h / Renderer.HEIGHT);
        gc.setTransform(scale, 0, 0, scale, (w - Renderer.WIDTH * scale) / 2, (h - Renderer.HEIGHT * scale) / 2);

        Setup setup = settings.setup;
        long high = hallOfFame.highScore(setup);
        if (game != null) {
            renderer.drawGame(game, high);
        } else {
            renderer.drawScreen(setup, null, settings.startLevel, 0, 0, high);
        }
        switch (screen) {
            case MAIN -> renderer.drawMenu("MAIN MENU", mainItems(), selected);
            case CHOOSE_SETUP -> renderer.drawMenu("CHOOSE SETUP", chooseItems(), selected);
            case CHANGE_SETUP -> renderer.drawMenu("CHANGE SETUP", changeItems(), selected);
            case START_LEVEL -> renderer.drawMenu("STARTING LEVEL", levelItems(), selected);
            case ENTER_NAME -> renderer.drawHallOfFame(setup, hallOfFame.entries(setup), editRank, editText,
                    ((int) (time * 3)) % 2 == 0);
            case HALL_OF_FAME -> renderer.drawHallOfFame(setup, hallOfFame.entries(setup), -1, "", false);
            case GAME -> { }
        }
    }

    // ------------------------------------------------------------------ menus

    private List<String> mainItems() {
        return labels(MainItem.values(), item -> switch (item) {
            case START_GAME -> "Start Game";
            case CHOOSE_SETUP -> "Choose Setup";
            case HALL_OF_FAME -> "Hall of Fame";
            case SOUND -> "Sound\t" + (settings.sound ? "ON" : "OFF");
            case QUIT -> "Quit";
        });
    }

    private List<String> chooseItems() {
        return Stream.concat(PRESETS.stream().map(s -> s.name() + "\t" + s.description()), Stream.of("Change Setup"))
                .toList();
    }

    private List<String> changeItems() {
        Setup s = settings.setup;
        return labels(SetupItem.values(), item -> switch (item) {
            case LENGTH -> "Pit Length\t" + s.width();
            case WIDTH -> "Pit Width\t" + s.height();
            case DEPTH -> "Pit Depth\t" + s.depth();
            case BLOCK_SET -> "Block Set\t" + s.blockSet();
            case START_GAME -> "Start Game";
        });
    }

    private List<String> levelItems() {
        return IntStream.rangeClosed(0, Game.MAX_LEVEL).mapToObj(i -> "Level " + i).toList();
    }

    private static <T> List<String> labels(T[] items, Function<T, String> label) {
        return Arrays.stream(items).map(label).toList();
    }

    private int itemCount() {
        return switch (screen) {
            case MAIN -> MainItem.values().length;
            case CHOOSE_SETUP -> PRESETS.size() + 1;
            case CHANGE_SETUP -> SetupItem.values().length;
            case START_LEVEL -> Game.MAX_LEVEL + 1;
            default -> 0;
        };
    }

    private void show(View s, int sel) {
        screen = s;
        selected = sel;
    }

    private void menuKey(KeyCode code) {
        int n = itemCount();
        switch (code) {
            case UP, KP_UP, NUMPAD8 -> {
                selected = (selected + n - 1) % n;
                speaker.play(Speaker.Effect.MENU);
            }
            case DOWN, KP_DOWN, NUMPAD2 -> {
                selected = (selected + 1) % n;
                speaker.play(Speaker.Effect.MENU);
            }
            case LEFT, KP_LEFT, NUMPAD4 -> change(-1);
            case RIGHT, KP_RIGHT, NUMPAD6 -> change(1);
            case ENTER, SPACE -> select();
            case ESCAPE -> back();
            default -> { }
        }
    }

    private void select() {
        speaker.play(Speaker.Effect.MENU);
        switch (screen) {
            case MAIN -> {
                switch (MainItem.values()[selected]) {
                    case START_GAME -> show(View.START_LEVEL, settings.startLevel);
                    case CHOOSE_SETUP -> show(View.CHOOSE_SETUP, 0);
                    case HALL_OF_FAME -> show(View.HALL_OF_FAME, 0);
                    case SOUND -> toggleSound();
                    case QUIT -> Platform.exit();
                }
            }
            case CHOOSE_SETUP -> {
                if (selected < PRESETS.size()) {
                    settings.setup = PRESETS.get(selected);
                    show(View.MAIN, 0);
                } else {
                    show(View.CHANGE_SETUP, 0);
                }
            }
            case CHANGE_SETUP -> {
                if (SetupItem.values()[selected] == SetupItem.START_GAME) show(View.START_LEVEL, settings.startLevel);
                else change(1);
            }
            case START_LEVEL -> {
                settings.startLevel = selected;
                settings.save();
                game = new Game(settings.setup, selected, random, speaker::play, time);
                screen = View.GAME;
            }
            default -> { }
        }
    }

    /// Left/right in a menu: changes the selected setup value or switches the sound.
    private void change(int d) {
        if (screen == View.CHANGE_SETUP) {
            Setup s = settings.setup;
            BlockSet[] sets = BlockSet.values();
            SetupItem item = SetupItem.values()[selected];
            settings.setup = switch (item) {
                case LENGTH -> s.withWidth(wrap(s.width() + d, Setup.MIN_SIDE, Setup.MAX_SIDE));
                case WIDTH -> s.withHeight(wrap(s.height() + d, Setup.MIN_SIDE, Setup.MAX_SIDE));
                case DEPTH -> s.withDepth(wrap(s.depth() + d, Setup.MIN_DEPTH, Setup.MAX_DEPTH));
                case BLOCK_SET -> s.withBlockSet(sets[wrap(s.blockSet().ordinal() + d, 0, sets.length - 1)]);
                case START_GAME -> s;
            };
            if (item != SetupItem.START_GAME) speaker.play(Speaker.Effect.MENU);
        } else if (screen == View.MAIN && MainItem.values()[selected] == MainItem.SOUND) {
            toggleSound();
        }
    }

    private void toggleSound() {
        settings.sound = !settings.sound;
        speaker.setEnabled(settings.sound);
    }

    /// Steps through `min..max` and wraps around at both ends.
    private static int wrap(int v, int min, int max) {
        return v < min ? max : v > max ? min : v;
    }

    private void back() {
        switch (screen) {
            case CHOOSE_SETUP, START_LEVEL, HALL_OF_FAME -> show(View.MAIN, 0);
            case CHANGE_SETUP -> show(View.CHOOSE_SETUP, PRESETS.size());
            default -> { }
        }
    }

    // --------------------------------------------------------------- keyboard

    private void keyPressed(KeyEvent e) {
        KeyCode code = e.getCode();
        e.consume();
        if (code == KeyCode.F11 || (code == KeyCode.ENTER && e.isAltDown())) {
            stage.setFullScreen(!stage.isFullScreen());
            return;
        }
        switch (screen) {
            case GAME -> gameKey(code);
            case ENTER_NAME -> {
                if (code == KeyCode.ENTER) {
                    hallOfFame.rename(settings.setup, editRank, editText);
                    show(View.HALL_OF_FAME, 0);
                } else if (code == KeyCode.BACK_SPACE && !editText.isEmpty()) {
                    editText = editText.substring(0, editText.length() - 1);
                }
            }
            case HALL_OF_FAME -> {
                if (code == KeyCode.ENTER || code == KeyCode.ESCAPE || code == KeyCode.SPACE) {
                    game = null;
                    show(View.MAIN, 0);
                }
            }
            default -> menuKey(code);
        }
    }

    private void keyTyped(KeyEvent e) {
        if (screen != View.ENTER_NAME) return;
        String c = e.getCharacter();
        // Printable characters only; ';' separates the fields of the hall of fame file
        if (c.length() == 1 && c.charAt(0) >= ' ' && c.charAt(0) != 127 && c.charAt(0) != ';'
                && editText.length() < HallOfFame.NAME_LENGTH) {
            editText += c;
        }
    }

    private void gameKey(KeyCode code) {
        if (game.state() == Game.State.OVER) {
            if (code == KeyCode.ENTER || code == KeyCode.ESCAPE) endGame();
            return;
        }
        Game.Action action = switch (code) {
            case LEFT, KP_LEFT, NUMPAD4 -> Game.Action.LEFT;
            case RIGHT, KP_RIGHT, NUMPAD6 -> Game.Action.RIGHT;
            case UP, KP_UP, NUMPAD8 -> Game.Action.UP;
            case DOWN, KP_DOWN, NUMPAD2 -> Game.Action.DOWN;
            case HOME, NUMPAD7 -> Game.Action.UP_LEFT;
            case PAGE_UP, NUMPAD9 -> Game.Action.UP_RIGHT;
            case END, NUMPAD1 -> Game.Action.DOWN_LEFT;
            case PAGE_DOWN, NUMPAD3 -> Game.Action.DOWN_RIGHT;
            case SPACE -> Game.Action.DROP;
            case Q -> Game.Action.ROT_NX;
            case W -> Game.Action.ROT_NY;
            case E -> Game.Action.ROT_NZ;
            case A -> Game.Action.ROT_X;
            case S -> Game.Action.ROT_Y;
            case D -> Game.Action.ROT_Z;
            case P -> Game.Action.PAUSE;
            default -> null;
        };
        if (action != null) {
            game.press(action);
        } else if (code == KeyCode.ESCAPE) {
            game.abort();
            endGame();
        }
    }

    /// After game over: name entry if the score made it into the hall of fame, else the list.
    private void endGame() {
        editRank = hallOfFame.insert(settings.setup, game.score());
        editText = "";
        if (editRank >= 0) {
            speaker.play(Speaker.Effect.WELL_DONE);
            show(View.ENTER_NAME, 0);
        } else {
            show(View.HALL_OF_FAME, 0);
        }
    }
}
