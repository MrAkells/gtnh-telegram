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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.Logger;

/**
 * The most recent quest completions and the current teams, mirrored to a JSON
 * file that other services (for example a website) can read.
 *
 * Not thread-safe: {@link QuestTracker} confines it to its worker thread.
 */
final class QuestFeed {
    static final int FORMAT_VERSION = 2;

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final Path file;
    private final int maxEntries;
    private final Logger logger;
    /** Newest first. */
    private final List<QuestCompletion> quests = new ArrayList<QuestCompletion>();
    private List<Team> teams = new ArrayList<Team>();

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

    List<Team> teams() {
        return Collections.unmodifiableList(teams);
    }

    /** Reads the entries already stored in the file, so the feed survives server restarts. */
    void load() {
        quests.clear();
        teams = new ArrayList<Team>();

        if (!Files.isRegularFile(file)) {
            return;
        }

        try {
            String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            Document document = GSON.fromJson(json, Document.class);

            if (document == null) {
                return;
            }

            if (document.quests != null) {
                for (QuestCompletion entry : document.quests) {
                    if (entry != null && entry.isValid() && quests.size() < maxEntries) {
                        quests.add(entry);
                    }
                }
            }

            if (document.teams != null) {
                for (Team team : document.teams) {
                    if (team != null) {
                        teams.add(team);
                    }
                }
            }
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

    /** Replaces the team list and rewrites the file if the list has changed. */
    void setTeams(List<Team> current) {
        if (!teams.equals(current)) {
            teams = new ArrayList<Team>(current);
            write();
        }
    }

    private void insert(QuestCompletion completion) {
        // The same completion reported again: only the list of players can grow.
        for (int i = 0; i < quests.size(); i++) {
            QuestCompletion existing = quests.get(i);

            if (existing.key().equals(completion.key())) {
                Set<String> players = new LinkedHashSet<String>(existing.players());
                players.addAll(completion.players());
                quests.set(i, existing.withPlayers(new ArrayList<String>(players), Collections.<String>emptyList()));
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
        document.teams = teams;
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
        List<Team> teams;
        List<QuestCompletion> quests;
    }
}
