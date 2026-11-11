package dev.mrakells.gtnhtelegram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Everyone BetterQuesting knows about and how far they have got: the quest
 * book chapters, every player and every team. This is also the JSON shape of
 * these three lists in the quest feed.
 *
 * Players and teams are identified by UUID and party id, so a renamed player
 * or team stays the same entry; names are only labels.
 */
public final class Roster {
    private final List<Chapter> chapters;
    private final List<Player> players;
    private final List<Team> teams;

    public Roster(List<Chapter> chapters, List<Player> players, List<Team> teams) {
        this.chapters = chapters == null ? Collections.<Chapter>emptyList() : new ArrayList<Chapter>(chapters);
        this.players = players == null ? Collections.<Player>emptyList() : new ArrayList<Player>(players);
        this.teams = teams == null ? Collections.<Team>emptyList() : new ArrayList<Team>(teams);
    }

    public static Roster empty() {
        return new Roster(null, null, null);
    }

    public List<Chapter> chapters() {
        return Collections.unmodifiableList(chapters);
    }

    public List<Player> players() {
        return Collections.unmodifiableList(players);
    }

    public List<Team> teams() {
        return Collections.unmodifiableList(teams);
    }

    /** A quest book chapter (BetterQuesting quest line), in the order of the book. */
    public static final class Chapter {
        final String name;
        /** How many quests of the chapter count towards progress. */
        final int quests;

        Chapter(String name, int quests) {
            this.name = name;
            this.quests = quests;
        }
    }

    public static final class Player {
        final String uuid;
        final String name;
        /** Party id of the player's team, null for a player without one. */
        final Integer team;
        /** Completed quests per chapter, parallel to {@link Roster#chapters()}. */
        final int[] done;
        /** Time of the latest completed quest, Unix seconds; 0 when there is none. */
        final long lastQuest;

        Player(String uuid, String name, Integer team, int[] done, long lastQuest) {
            this.uuid = uuid;
            this.name = name;
            this.team = team;
            this.done = done;
            this.lastQuest = lastQuest;
        }
    }

    /** A BetterQuesting party with at least two members. A party of one is just a solo player. */
    public static final class Team {
        final int id;
        final String name;
        /** Player UUIDs. */
        final List<String> members;
        /** Quests completed by at least one member, per chapter, parallel to {@link Roster#chapters()}. */
        final int[] done;
        final long lastQuest;

        Team(int id, String name, List<String> members, int[] done, long lastQuest) {
            this.id = id;
            this.name = name;
            this.members = new ArrayList<String>(members);
            this.done = done;
            this.lastQuest = lastQuest;
        }
    }
}
