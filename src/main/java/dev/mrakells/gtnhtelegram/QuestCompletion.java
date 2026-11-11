package dev.mrakells.gtnhtelegram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One completed quest together with the players who were online and got
 * credit for it. This is also the JSON shape of a quest feed entry.
 */
public final class QuestCompletion {
    /** Completion time, Unix seconds. */
    private long time;
    private String quest;
    private String questId;
    private String questUrl;
    private String chapter;
    /** Name of the players' team (BetterQuesting party), empty for a solo player. */
    private String team;
    private List<String> players;
    /** Parallel to {@link #players}. Used by message templates only, never written to the feed. */
    private transient List<String> playerUuids;

    /** For Gson. */
    @SuppressWarnings("unused")
    private QuestCompletion() {
    }

    public QuestCompletion(
            long time,
            String questId,
            String quest,
            String chapter,
            String questUrl,
            String team,
            List<String> players,
            List<String> playerUuids
    ) {
        this.time = time;
        this.questId = questId;
        this.quest = quest;
        this.chapter = chapter;
        this.questUrl = questUrl;
        this.team = team;
        this.players = new ArrayList<String>(players);
        this.playerUuids = new ArrayList<String>(playerUuids);
    }

    /**
     * Team members complete a shared quest at the same instant, so the quest id
     * plus the completion time identifies one completion across all of them.
     */
    public String key() {
        return questId + "@" + time;
    }

    /** False for entries read from a damaged or foreign feed file. */
    public boolean isValid() {
        return questId != null && quest != null && players != null && !players.isEmpty();
    }

    public QuestCompletion withPlayers(List<String> names, List<String> uuids) {
        return new QuestCompletion(time, questId, quest, chapter, questUrl, team, names, uuids);
    }

    public long time() {
        return time;
    }

    public String quest() {
        return quest;
    }

    public String questId() {
        return questId;
    }

    public String questUrl() {
        return questUrl == null ? "" : questUrl;
    }

    public String chapter() {
        return chapter == null ? "" : chapter;
    }

    public String team() {
        return team == null ? "" : team;
    }

    public List<String> players() {
        return players == null ? Collections.<String>emptyList() : Collections.unmodifiableList(players);
    }

    public List<String> playerUuids() {
        return playerUuids == null ? Collections.<String>emptyList() : Collections.unmodifiableList(playerUuids);
    }
}
