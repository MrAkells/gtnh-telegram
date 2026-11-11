package dev.mrakells.gtnhtelegram;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class QuestMessagesTest {
    @Test
    public void fillsPlaceholdersAndEscapesHtml() {
        QuestCompletion completion = TestData.completion(100, "q1", "Tom & <Jerry>", "smak", "Alice");

        assertEquals(
                "Alice [smak] done: <a href=\"https://gtnhquestsbook.top/?id=q1\">Tom &amp; &lt;Jerry&gt;</a> (Tier 0 - Stone Age)",
                QuestMessages.single("{player} [{team}] done: {quest} ({chapter})", "", completion)
        );
        assertEquals("Tom &amp; &lt;Jerry&gt; q1 uuid-of-Alice", QuestMessages.single("{questName} {questId} {playerUuid}", "", completion));
    }

    @Test
    public void usesPluralTemplateOnlyForSeveralPlayers() {
        QuestCompletion solo = TestData.completion(100, "q1", "Quest", "", "Alice");
        QuestCompletion pair = TestData.completion(100, "q1", "Quest", "smak", "Alice", "Bob");

        assertEquals("Alice has", QuestMessages.single("{player} has", "{player} have", solo));
        assertEquals("Alice, Bob have", QuestMessages.single("{player} has", "{player} have", pair));
        assertEquals("Alice, Bob has", QuestMessages.single("{player} has", " ", pair));
    }

    @Test
    public void leavesUnknownPlaceholdersAndDoesNotExpandValues() {
        QuestCompletion completion = TestData.completion(100, "q1", "{player} $1", "", "Alice");

        assertEquals("{unknown} {player} $1", QuestMessages.single("{unknown} {questName}", "", completion));
    }

    @Test
    public void turnsEscapedNewlinesIntoLineBreaks() {
        QuestCompletion completion = TestData.completion(100, "q1", "Quest", "", "Alice");

        assertEquals("Alice\nQuest", QuestMessages.single("{player}\\n{questName}", "", completion));
    }

    @Test
    public void omitsLinkWhenThereIsNoUrl() {
        QuestCompletion completion = new QuestCompletion(
                100, "q1", "Quest", "", "", "",
                java.util.Collections.singletonList("Alice"), java.util.Collections.singletonList("u")
        );

        assertEquals("Quest", QuestMessages.single("{quest}", "", completion));
    }

    @Test
    public void digestListsEveryQuestUnderOneHeader() {
        List<QuestCompletion> completions = new ArrayList<QuestCompletion>();
        completions.add(TestData.completion(100, "q1", "First", "", "Alice"));
        completions.add(TestData.completion(100, "q2", "Second", "", "Alice"));

        List<String> messages = QuestMessages.digest("{player}: {count} quests\\n{quests}\\nGG", completions, 3800);

        assertEquals(1, messages.size());
        assertEquals(
                "Alice: 2 quests\n"
                        + "• <a href=\"https://gtnhquestsbook.top/?id=q1\">First</a> [Tier 0 - Stone Age]\n"
                        + "• <a href=\"https://gtnhquestsbook.top/?id=q2\">Second</a> [Tier 0 - Stone Age]\n"
                        + "GG",
                messages.get(0)
        );
    }

    @Test
    public void digestSplitsIntoMessagesThatFit() {
        List<QuestCompletion> completions = new ArrayList<QuestCompletion>();
        for (int i = 0; i < 40; i++) {
            completions.add(TestData.completion(100, "q" + i, "Quest number " + i, "", "Alice"));
        }

        List<String> messages = QuestMessages.digest("{player} completed {count} quests:\\n{quests}", completions, 500);

        assertTrue(messages.size() > 1);
        int lines = 0;
        for (String message : messages) {
            assertTrue("message of " + message.length() + " chars", message.length() <= 500);
            lines += message.split("•", -1).length - 1;
        }
        assertEquals(40, lines);
        assertTrue(messages.get(0).startsWith("Alice completed 40 quests:"));
    }
}
