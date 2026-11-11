package dev.mrakells.gtnhtelegram;

import static org.junit.Assert.assertEquals;

import java.util.UUID;
import org.junit.Test;

public class QuestLinksTest {
    private static final String BASE = "https://gtnhquestsbook.top/?id=";

    @Test
    public void encodesQuestIdTheWayTheQuestBookExpects() {
        // Pairs taken from a real GTNH quest database.
        assertEquals(
                BASE + "XABK1llCToa6KKcF5Z69GA==",
                QuestLinks.questUrl(BASE, UUID.fromString("5c004ad6-5942-4e86-ba28-a705e59ebd18"))
        );
        assertEquals(
                BASE + "AAAAAAAAAAAAAAAAAAACLw==",
                QuestLinks.questUrl(BASE, UUID.fromString("00000000-0000-0000-0000-00000000022f"))
        );
    }

    @Test
    public void givesNoLinkWithoutBaseUrl() {
        assertEquals("", QuestLinks.questUrl("  ", UUID.randomUUID()));
        assertEquals("", QuestLinks.questUrl(null, UUID.randomUUID()));
    }
}
