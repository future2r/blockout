package name.ulbricht.blockout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// The ten best scores of every setup, stored in a text file (one `id;score;date;name` line each).
final class HallOfFame {

    static final int SIZE = 10;
    static final int NAME_LENGTH = 10;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yy");

    record Entry(String name, long score, String date) {
    }

    private final Path file;
    private final Map<Integer, List<Entry>> tables = new HashMap<>();

    HallOfFame(Path file) {
        this.file = file;
        load();
    }

    List<Entry> entries(Setup setup) {
        return List.copyOf(tables.getOrDefault(setup.id(), List.of()));
    }

    long highScore(Setup setup) {
        List<Entry> list = tables.get(setup.id());
        return list == null || list.isEmpty() ? 0 : list.getFirst().score();
    }

    /// Rank (0-based) the score would get, or -1 if it does not make it into the list.
    int rank(Setup setup, long score) {
        if (score <= 0) return -1;
        List<Entry> list = tables.getOrDefault(setup.id(), List.of());
        for (int i = 0; i < list.size(); i++) {
            if (score > list.get(i).score()) return i;
        }
        return list.size() < SIZE ? list.size() : -1;
    }

    /// Inserts a placeholder entry and returns its rank; the name is set later with [#rename].
    int insert(Setup setup, long score) {
        int rank = rank(setup, score);
        if (rank < 0) return -1;
        List<Entry> list = tables.computeIfAbsent(setup.id(), _ -> new ArrayList<>());
        list.add(rank, new Entry("", score, LocalDate.now().format(DATE)));
        while (list.size() > SIZE) list.removeLast();
        return rank;
    }

    void rename(Setup setup, int rank, String name) {
        List<Entry> list = tables.get(setup.id());
        Entry e = list.get(rank);
        list.set(rank, new Entry(name.isBlank() ? "Anonymous" : name.strip(), e.score(), e.date()));
        save();
    }

    private void load() {
        if (!Files.exists(file)) return;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String[] f = line.split(";", 4);
                if (f.length < 4) continue;
                tables.computeIfAbsent(Integer.parseInt(f[0]), _ -> new ArrayList<>())
                        .add(new Entry(f[3], Long.parseLong(f[1]), f[2]));
            }
            tables.values().forEach(l -> l.sort(Comparator.comparingLong(Entry::score).reversed()));
        } catch (IOException | NumberFormatException e) {
            System.err.println("Cannot read hall of fame " + file + ": " + e);
        }
    }

    private void save() {
        List<String> lines = new ArrayList<>();
        tables.forEach((id, list) -> list.forEach(
                e -> lines.add(id + ";" + e.score() + ";" + e.date() + ";" + e.name().replace('\n', ' '))));
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Cannot write hall of fame " + file + ": " + e);
        }
    }
}
