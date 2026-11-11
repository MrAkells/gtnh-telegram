package dev.mrakells.gtnhtelegram;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class TelegramNotifierTest {
    private final TelegramNotifier notifier = new TelegramNotifier(null, "{player}: {questName}", "", "{player} x{count}\\n{quests}", 3);

    @Test
    public void sendsSeparateMessagesBelowTheThreshold() {
        List<QuestCompletion> batch = new ArrayList<QuestCompletion>();
        batch.add(TestData.completion(100, "q1", "First", "", "Alice"));
        batch.add(TestData.completion(100, "q2", "Second", "", "Alice"));

        List<String> messages = notifier.messages(batch);

        assertEquals(2, messages.size());
        assertEquals("Alice: First", messages.get(0));
        assertEquals("Alice: Second", messages.get(1));
    }

    @Test
    public void sendsOneSummaryPerGroupOfPlayers() {
        List<QuestCompletion> batch = new ArrayList<QuestCompletion>();
        for (int i = 0; i < 5; i++) {
            batch.add(TestData.completion(100, "a" + i, "Alice quest " + i, "", "Alice"));
        }
        batch.add(TestData.completion(100, "b1", "Team quest", "smak", "Bob", "Carol"));

        List<String> messages = notifier.messages(batch);

        assertEquals(2, messages.size());
        assertTrue(messages.get(0).startsWith("Alice x5\n"));
        assertEquals("Bob, Carol: Team quest", messages.get(1));
    }

    @Test
    public void zeroThresholdTurnsSummariesOff() {
        TelegramNotifier separate = new TelegramNotifier(null, "{questName}", "", "unused", 0);
        List<QuestCompletion> batch = new ArrayList<QuestCompletion>();
        for (int i = 0; i < 10; i++) {
            batch.add(TestData.completion(100, "q" + i, "Quest " + i, "", "Alice"));
        }

        assertEquals(10, separate.messages(batch).size());
    }
}
