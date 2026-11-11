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
import java.util.List;
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
        feed(10).add(Collections.singletonList(TestData.completion(100, "q1", "Tom & \"Jerry\"", "smak", "Alice", "Bob")));

        JsonObject document = json();
        JsonObject quest = document.getAsJsonArray("quests").get(0).getAsJsonObject();

        assertEquals(QuestFeed.FORMAT_VERSION, document.get("version").getAsInt());
        assertTrue(document.get("updated").getAsLong() > 0);
        assertEquals(100, quest.get("time").getAsLong());
        assertEquals("Tom & \"Jerry\"", quest.get("quest").getAsString());
        assertEquals("q1", quest.get("questId").getAsString());
        assertEquals("smak", quest.get("team").getAsString());
        assertEquals("Tier 0 - Stone Age", quest.get("chapter").getAsString());
        assertEquals(2, quest.getAsJsonArray("players").size());
        assertEquals("Bob", quest.getAsJsonArray("players").get(1).getAsString());
        assertFalse("player UUIDs stay private", quest.has("playerUuids"));
        assertFalse("no temporary file is left behind", Files.exists(file.resolveSibling("quests.json.tmp")));
    }

    @Test
    public void mergesPlayersOfTheSameCompletion() {
        QuestFeed feed = feed(10);
        feed.add(Collections.singletonList(TestData.completion(100, "q1", "Quest", "smak", "Alice")));
        feed.add(Collections.singletonList(TestData.completion(100, "q1", "Quest", "smak", "Bob", "Alice")));

        assertEquals(1, feed.quests().size());
        assertEquals(Arrays.asList("Alice", "Bob"), feed.quests().get(0).players());
    }

    @Test
    public void survivesRestart() {
        QuestFeed first = feed(10);
        first.add(Collections.singletonList(TestData.completion(100, "q1", "Quest", "smak", "Alice")));
        first.setTeams(Collections.singletonList(new Team("smak", Arrays.asList("Alice", "Bob"))));

        QuestFeed second = feed(10);
        second.load();
        second.add(Collections.singletonList(TestData.completion(200, "q2", "Later", "", "Carol")));

        assertEquals(2, second.quests().size());
        assertEquals("Later", second.quests().get(0).quest());
        assertEquals(Collections.singletonList("Alice"), second.quests().get(1).players());
        assertEquals(1, second.teams().size());
        assertEquals(Arrays.asList("Alice", "Bob"), second.teams().get(0).members());
    }

    @Test
    public void writesTeamsOnlyWhenTheyChange() throws Exception {
        QuestFeed feed = feed(10);
        List<Team> teams = Collections.singletonList(new Team("smak", Arrays.asList("Alice", "Bob")));

        feed.setTeams(teams);
        assertEquals("smak", json().getAsJsonArray("teams").get(0).getAsJsonObject().get("name").getAsString());

        Files.delete(file);
        feed.setTeams(Collections.singletonList(new Team("smak", Arrays.asList("Alice", "Bob"))));
        assertFalse("unchanged teams do not rewrite the file", Files.exists(file));

        feed.setTeams(Collections.<Team>emptyList());
        assertEquals(0, json().getAsJsonArray("teams").size());
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
    public void skipsEntriesItCannotUse() throws Exception {
        Files.createDirectories(file.getParent());
        Files.write(file, ("{\"version\":1,\"quests\":[{\"player\":\"Old format\",\"quest\":\"x\",\"questId\":\"q0\",\"time\":1},"
                + "{\"players\":[\"Alice\"],\"quest\":\"Good\",\"questId\":\"q1\",\"time\":2}]}").getBytes(StandardCharsets.UTF_8));

        QuestFeed feed = feed(10);
        feed.load();

        assertEquals(1, feed.quests().size());
        assertEquals("Good", feed.quests().get(0).quest());
    }
}
