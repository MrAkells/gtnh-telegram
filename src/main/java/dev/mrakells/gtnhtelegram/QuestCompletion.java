package dev.mrakells.gtnhtelegram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One completed quest together with the players who were online and got
 * credit for it. This is also the JSON shape of a quest feed entry: the feed
 * refers to players by UUID and to the team by party id, and takes their
 * current names from the {@link Roster}.
 */
public final class QuestCompletion {
    /** Completion time, Unix seconds. */
    private long time;
    private String quest;
    private String questId;
    private String questUrl;
    private String chapter;
    /** Party id of the players' team, null for a solo player. */
    private Integer team;
    /** Player UUIDs. */
    private List<String> players;
    /** Names at the time of completion, for message templates only; never written to the feed. */
    private transient String teamName;
    /** Parallel to {@link #players}. */
    private transient List<String> playerNames;

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
            Integer team,
            String teamName,
            List<String> playerUuids,
            List<String> playerNames
    ) {
        this.time = time;
        this.questId = questId;
        this.quest = quest;
        this.chapter = chapter;
        this.questUrl = questUrl;
        this.team = team;
        this.teamName = teamName;
        this.players = new ArrayList<String>(playerUuids);
        this.playerNames = new ArrayList<String>(playerNames);
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

    /** The same completion credited to the players of both reports, in the order they were reported. */
    public QuestCompletion mergedWith(QuestCompletion other) {
        Map<String, String> all = new LinkedHashMap<String, String>();
        collect(all, this);
        collect(all, other);

        return new QuestCompletion(
                time,
                questId,
                quest,
                chapter,
                questUrl,
                team,
                teamName,
                new ArrayList<String>(all.keySet()),
                new ArrayList<String>(all.values())
        );
    }

    private static void collect(Map<String, String> target, QuestCompletion completion) {
        List<String> uuids = completion.playerUuids();
        List<String> names = completion.playerNames();

        for (int i = 0; i < uuids.size(); i++) {
            if (!target.containsKey(uuids.get(i))) {
                // An entry loaded from the feed file has no names; the UUID stands in.
                target.put(uuids.get(i), i < names.size() ? names.get(i) : uuids.get(i));
            }
        }
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

    /** @return the party id, null for a solo player */
    public Integer teamId() {
        return team;
    }

    /** @return the team name at the time of completion, empty for a solo player */
    public String teamName() {
        return teamName == null ? "" : teamName;
    }

    public List<String> playerUuids() {
        return players == null ? Collections.<String>emptyList() : Collections.unmodifiableList(players);
    }

    public List<String> playerNames() {
        return playerNames == null ? Collections.<String>emptyList() : Collections.unmodifiableList(playerNames);
    }
}
