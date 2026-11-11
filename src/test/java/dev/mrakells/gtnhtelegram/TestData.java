package dev.mrakells.gtnhtelegram;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class TestData {
    private TestData() {
    }

    /** A team name stands for party 7; an empty one for a solo player. */
    static QuestCompletion completion(long time, String questId, String quest, String team, String... players) {
        List<String> uuids = new ArrayList<String>();
        for (String player : players) {
            uuids.add("uuid-of-" + player);
        }

        return new QuestCompletion(
                time,
                questId,
                quest,
                "Tier 0 - Stone Age",
                "https://gtnhquestsbook.top/?id=" + questId,
                team.isEmpty() ? null : Integer.valueOf(7),
                team,
                uuids,
                Arrays.asList(players)
        );
    }
}
