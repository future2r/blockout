package name.ulbricht.blockout;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/// Last used setup, starting level and sound switch.
///
/// The file uses the original's terms: `pit.length` is the horizontal extent
/// ([Setup#width]) and `pit.width` the vertical one ([Setup#height]).
final class Settings {

    private final Path file;
    Setup setup = Setup.FLAT_FUN;
    int startLevel;
    boolean sound = true;

    Settings(Path file) {
        this.file = file;
        if (!Files.exists(file)) return;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file)) {
            p.load(r);
            setup = new Setup(
                    Integer.parseInt(p.getProperty("pit.length", "5")),
                    Integer.parseInt(p.getProperty("pit.width", "5")),
                    Integer.parseInt(p.getProperty("pit.depth", "12")),
                    BlockSet.valueOf(p.getProperty("blockset", "FLAT")));
            startLevel = Math.clamp(Integer.parseInt(p.getProperty("level", "0")), 0, Game.MAX_LEVEL);
            sound = Boolean.parseBoolean(p.getProperty("sound", "true"));
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Cannot read settings " + file + ": " + e);
        }
    }

    void save() {
        Properties p = new Properties();
        p.setProperty("pit.length", Integer.toString(setup.width()));
        p.setProperty("pit.width", Integer.toString(setup.height()));
        p.setProperty("pit.depth", Integer.toString(setup.depth()));
        p.setProperty("blockset", setup.blockSet().name());
        p.setProperty("level", Integer.toString(startLevel));
        p.setProperty("sound", Boolean.toString(sound));
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file)) {
                p.store(w, "Blockout");
            }
        } catch (IOException e) {
            System.err.println("Cannot write settings " + file + ": " + e);
        }
    }
}
