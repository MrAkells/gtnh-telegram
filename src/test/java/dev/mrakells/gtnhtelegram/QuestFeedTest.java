package dev.mrakells.gtnhtelegram;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class QuestFeedTest {
    private static final Logger LOGGER = LogManager.getLogger("test");

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Path file;

    @Before
    public void setUp() throws Exception {
        file = folder.getRoot().toPath().resolve("nested/quests.json");
    }

    private QuestFeed feed(int maxEntries) {
        return new QuestFeed(file, maxEntries, LOGGER);
    }

    private JsonObject json() throws Exception {
        return new JsonParser().parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    public void keepsNewestFirstAndDropsTheOldest() throws Exception {
        QuestFeed feed = feed(2);
        feed.add(Collections.singletonList(TestData.completion(200, "q2", "Second", "", "Alice")));
        feed.add(Arrays.asList(
                TestData.completion(100, "q1", "First", "", "Alice"),
                TestData.completion(300, "q3", "Third", "", "Alice")
        ));

        assertEquals(2, feed.quests().size());
        assertEquals("Third", feed.quests().get(0).quest());
        assertEquals("Second", feed.quests().get(1).quest());
        assertEquals(2, json().getAsJsonArray("quests").size());
    }

    @Test
    public void writesTheDocumentedShape() throws Exception {
        QuestFeed feed = feed(10);
        feed.setRoster(roster());
        feed.add(Collections.singletonList(TestData.completion(100, "q1", "Tom & \"Jerry\"", "smak", "Alice", "Bob")));

        JsonObject document = json();
        JsonObject quest = document.getAsJsonArray("quests").get(0).getAsJsonObject();

        assertEquals(3, document.get("version").getAsInt());
        assertTrue(document.get("updated").getAsLong() > 0);
        assertEquals(100, quest.get("time").getAsLong());
        assertEquals("Tom & \"Jerry\"", quest.get("quest").getAsString());
        assertEquals("q1", quest.get("questId").getAsString());
        assertEquals("Tier 0 - Stone Age", quest.get("chapter").getAsString());
        assertEquals("the team is a party id", 7, quest.get("team").getAsInt());
        assertEquals("players are UUIDs", "uuid-of-Bob", quest.getAsJsonArray("players").get(1).getAsString());
        assertFalse("names live in the roster only", quest.has("playerNames") || quest.has("teamName"));

        JsonObject chapter = document.getAsJsonArray("chapters").get(0).getAsJsonObject();
        assertEquals("Tier 0 - Stone Age", chapter.get("name").getAsString());
        assertEquals(2, chapter.get("quests").getAsInt());

        JsonObject player = document.getAsJsonArray("players").get(0).getAsJsonObject();
        assertEquals("uuid-of-Alice", player.get("uuid").getAsString());
        assertEquals("Alice", player.get("name").getAsString());
        assertEquals(7, player.get("team").getAsInt());
        assertEquals(1, player.getAsJsonArray("done").get(0).getAsInt());
        assertEquals(100, player.get("lastQuest").getAsLong());
        assertFalse("a solo player has no team", document.getAsJsonArray("players").get(2).getAsJsonObject().has("team"));

        JsonObject team = document.getAsJsonArray("teams").get(0).getAsJsonObject();
        assertEquals(7, team.get("id").getAsInt());
        assertEquals("smak", team.get("name").getAsString());
        assertEquals("uuid-of-Bob", team.getAsJsonArray("members").get(1).getAsString());
        assertEquals(2, team.getAsJsonArray("done").get(0).getAsInt());

        assertFalse("no temporary file is left behind", Files.exists(file.resolveSibling("quests.json.tmp")));
    }

    @Test
    public void mergesPlayersOfTheSameCompletion() {
        QuestFeed feed = feed(10);
        feed.add(Collections.singletonList(TestData.completion(100, "q1", "Quest", "smak", "Alice")));
        feed.add(Collections.singletonList(TestData.completion(100, "q1", "Quest", "smak", "Bob", "Alice")));

        assertEquals(1, feed.quests().size());
        assertEquals(Arrays.asList("uuid-of-Alice", "uuid-of-Bob"), feed.quests().get(0).playerUuids());
    }

    @Test
    public void survivesRestart() {
        QuestFeed first = feed(10);
        first.add(Collections.singletonList(TestData.completion(100, "q1", "Quest", "smak", "Alice")));
        first.setRoster(roster());

        QuestFeed second = feed(10);
        second.load();
        second.add(Collections.singletonList(TestData.completion(200, "q2", "Later", "", "Carol")));

        assertEquals(2, second.quests().size());
        assertEquals("Later", second.quests().get(0).quest());
        assertEquals(Collections.singletonList("uuid-of-Alice"), second.quests().get(1).playerUuids());
        assertEquals(Integer.valueOf(7), second.quests().get(1).teamId());
        assertEquals("the roster is kept until the server reports the current one", 3, second.roster().players().size());
        assertEquals(1, second.roster().teams().size());
    }

    @Test
    public void writesTheRosterOnlyWhenItChanges() throws Exception {
        QuestFeed feed = feed(10);

        feed.setRoster(roster());
        assertEquals("smak", json().getAsJsonArray("teams").get(0).getAsJsonObject().get("name").getAsString());

        Files.delete(file);
        feed.setRoster(roster());
        assertFalse("an unchanged roster does not rewrite the file", Files.exists(file));

        feed.setRoster(Roster.empty());
        assertEquals(0, json().getAsJsonArray("teams").size());
        assertEquals(0, json().getAsJsonArray("players").size());
    }

    @Test
    public void startsEmptyWhenTheFileIsDamaged() throws Exception {
        Files.createDirectories(file.getParent());
        Files.write(file, "{ not json".getBytes(StandardCharsets.UTF_8));

        QuestFeed feed = feed(10);
        feed.load();

        assertTrue(feed.quests().isEmpty());
    }

    @Test
    public void ignoresAFileInAnotherFormat() throws Exception {
        Files.createDirectories(file.getParent());
        Files.write(file, "{\"version\":2,\"quests\":[{\"players\":[\"Alice\"],\"quest\":\"Old\",\"questId\":\"q1\",\"time\":2}]}"
                .getBytes(StandardCharsets.UTF_8));

        QuestFeed feed = feed(10);
        feed.load();

        assertTrue(feed.quests().isEmpty());
    }

    @Test
    public void skipsEntriesItCannotUse() throws Exception {
        Files.createDirectories(file.getParent());
        Files.write(file, ("{\"version\":3,\"quests\":[{\"quest\":\"No players\",\"questId\":\"q0\",\"time\":1},"
                + "{\"players\":[\"uuid-of-Alice\"],\"quest\":\"Good\",\"questId\":\"q1\",\"time\":2}]}").getBytes(StandardCharsets.UTF_8));

        QuestFeed feed = feed(10);
        feed.load();

        assertEquals(1, feed.quests().size());
        assertEquals("Good", feed.quests().get(0).quest());
    }

    /** Alice and Bob are the team "smak" (party 7), Carol plays alone. */
    private static Roster roster() {
        RosterBuilder builder = new RosterBuilder();
        builder.chapter("Tier 0 - Stone Age", new java.util.HashSet<String>(Arrays.asList("q1", "q2")));
        builder.player("uuid-of-Alice", "Alice");
        builder.player("uuid-of-Bob", "Bob");
        builder.player("uuid-of-Carol", "Carol");
        builder.completion("uuid-of-Alice", "q1", 100L);
        builder.completion("uuid-of-Bob", "q2", 90L);
        builder.party(7, "smak", Arrays.asList("uuid-of-Alice", "uuid-of-Bob"));

        return builder.build();
    }
}
