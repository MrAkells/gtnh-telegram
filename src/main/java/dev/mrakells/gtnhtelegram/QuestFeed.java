package dev.mrakells.gtnhtelegram;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.logging.log4j.Logger;

/**
 * The most recent quest completions and the current {@link Roster}, mirrored
 * to a JSON file that other services (for example a website) can read.
 *
 * Not thread-safe: {@link QuestTracker} confines it to its worker thread.
 */
final class QuestFeed {
    static final int FORMAT_VERSION = 3;

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final Path file;
    private final int maxEntries;
    private final Logger logger;
    /** Newest first. */
    private final List<QuestCompletion> quests = new ArrayList<QuestCompletion>();
    private Roster roster = Roster.empty();
    /** The roster as last written, to tell whether a new one differs. */
    private String rosterJson = GSON.toJson(roster);

    QuestFeed(Path file, int maxEntries, Logger logger) {
        this.file = file.toAbsolutePath();
        this.maxEntries = maxEntries;
        this.logger = logger;
    }

    Path file() {
        return file;
    }

    List<QuestCompletion> quests() {
        return Collections.unmodifiableList(quests);
    }

    Roster roster() {
        return roster;
    }

    /** Reads the entries already stored in the file, so the feed survives server restarts. */
    void load() {
        quests.clear();
        setRosterQuietly(Roster.empty());

        if (!Files.isRegularFile(file)) {
            return;
        }

        try {
            String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            Document document = GSON.fromJson(json, Document.class);

            // A file in another format is left alone until the first write replaces it.
            if (document == null || document.version != FORMAT_VERSION) {
                return;
            }

            if (document.quests != null) {
                for (QuestCompletion entry : document.quests) {
                    if (entry != null && entry.isValid() && quests.size() < maxEntries) {
                        quests.add(entry);
                    }
                }
            }

            // Kept until the server has started and the current roster is known.
            setRosterQuietly(new Roster(document.chapters, document.players, document.teams));
        } catch (Exception e) {
            logger.warn("Could not read quest feed " + file + ", starting with an empty one", e);
        }
    }

    /** Adds completions and rewrites the file. */
    void add(List<QuestCompletion> completions) {
        if (completions.isEmpty()) {
            return;
        }

        for (QuestCompletion completion : completions) {
            insert(completion);
        }

        while (quests.size() > maxEntries) {
            quests.remove(quests.size() - 1);
        }

        write();
    }

    /** Replaces the roster and rewrites the file if anything in it has changed. */
    void setRoster(Roster current) {
        String before = rosterJson;
        setRosterQuietly(current);

        if (!rosterJson.equals(before)) {
            write();
        }
    }

    private void setRosterQuietly(Roster current) {
        roster = current;
        rosterJson = GSON.toJson(current);
    }

    private void insert(QuestCompletion completion) {
        // The same completion reported again: only the list of players can grow.
        for (int i = 0; i < quests.size(); i++) {
            QuestCompletion existing = quests.get(i);

            if (existing.key().equals(completion.key())) {
                quests.set(i, existing.mergedWith(completion));
                return;
            }
        }

        int index = 0;
        while (index < quests.size() && quests.get(index).time() > completion.time()) {
            index++;
        }
        quests.add(index, completion);
    }

    /** Writes to a temporary file and then replaces the target, so a reader never sees a half-written feed. */
    private void write() {
        Document document = new Document();
        document.version = FORMAT_VERSION;
        document.updated = System.currentTimeMillis() / 1000L;
        document.chapters = roster.chapters();
        document.players = roster.players();
        document.teams = roster.teams();
        document.quests = quests;

        try {
            Files.createDirectories(file.getParent());

            Path temporary = file.resolveSibling(file.getFileName().toString() + ".tmp");
            Files.write(temporary, GSON.toJson(document).getBytes(StandardCharsets.UTF_8));

            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            logger.error("Could not write quest feed " + file, e);
        }
    }

    private static final class Document {
        int version;
        long updated;
        List<Roster.Chapter> chapters;
        List<Roster.Player> players;
        List<Roster.Team> teams;
        List<QuestCompletion> quests;
    }
}
