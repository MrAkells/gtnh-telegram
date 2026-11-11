package dev.mrakells.gtnhtelegram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Counts quest progress per chapter for every player and team. Works on plain
 * data, so it knows nothing about BetterQuesting or Minecraft.
 */
final class RosterBuilder {
    private final List<String> chapterNames = new ArrayList<String>();
    private final List<Set<String>> chapterQuests = new ArrayList<Set<String>>();
    /** UUID to name, in the order the players were added. */
    private final Map<String, String> names = new LinkedHashMap<String, String>();
    /** UUID to quest id to completion time, Unix seconds. */
    private final Map<String, Map<String, Long>> completed = new HashMap<String, Map<String, Long>>();
    private final List<Party> parties = new ArrayList<Party>();

    /** Chapters are kept in the order they are added. */
    void chapter(String name, Set<String> questIds) {
        chapterNames.add(name);
        chapterQuests.add(new HashSet<String>(questIds));
    }

    void player(String uuid, String name) {
        names.put(uuid, name);
        completed.put(uuid, new HashMap<String, Long>());
    }

    /** The player must have been added first. */
    void completion(String uuid, String questId, long time) {
        completed.get(uuid).put(questId, time);
    }

    void party(int id, String name, List<String> memberUuids) {
        parties.add(new Party(id, name, memberUuids));
    }

    Roster build() {
        List<Roster.Chapter> chapters = new ArrayList<Roster.Chapter>();
        for (int i = 0; i < chapterNames.size(); i++) {
            chapters.add(new Roster.Chapter(chapterNames.get(i), chapterQuests.get(i).size()));
        }

        Map<String, Integer> teamOf = new HashMap<String, Integer>();
        List<Roster.Team> teams = new ArrayList<Roster.Team>();

        for (Party party : parties) {
            // Members BetterQuesting no longer has a name for cannot be shown, so they do not count.
            List<String> members = new ArrayList<String>();

            for (String member : party.members) {
                if (names.containsKey(member)) {
                    members.add(member);
                }
            }

            if (members.size() < 2) {
                continue;
            }

            for (String member : members) {
                teamOf.put(member, party.id);
            }

            Map<String, Long> shared = completedBy(members);
            teams.add(new Roster.Team(party.id, party.name, members, count(shared), latest(shared)));
        }

        Collections.sort(teams, new Comparator<Roster.Team>() {
            @Override
            public int compare(Roster.Team a, Roster.Team b) {
                return a.id < b.id ? -1 : a.id == b.id ? 0 : 1;
            }
        });

        List<Roster.Player> players = new ArrayList<Roster.Player>();
        for (Map.Entry<String, String> entry : names.entrySet()) {
            Map<String, Long> own = completed.get(entry.getKey());
            players.add(new Roster.Player(entry.getKey(), entry.getValue(), teamOf.get(entry.getKey()), count(own), latest(own)));
        }

        // A stable order keeps the feed file unchanged while nothing happens.
        Collections.sort(players, new Comparator<Roster.Player>() {
            @Override
            public int compare(Roster.Player a, Roster.Player b) {
                int byName = a.name.compareToIgnoreCase(b.name);
                return byName != 0 ? byName : a.uuid.compareTo(b.uuid);
            }
        });

        return new Roster(chapters, players, teams);
    }

    private Map<String, Long> completedBy(List<String> members) {
        Map<String, Long> all = new HashMap<String, Long>();

        for (String member : members) {
            for (Map.Entry<String, Long> entry : completed.get(member).entrySet()) {
                Long known = all.get(entry.getKey());
                if (known == null || known < entry.getValue()) {
                    all.put(entry.getKey(), entry.getValue());
                }
            }
        }

        return all;
    }

    private int[] count(Map<String, Long> quests) {
        int[] done = new int[chapterQuests.size()];

        for (int i = 0; i < done.length; i++) {
            for (String questId : chapterQuests.get(i)) {
                if (quests.containsKey(questId)) {
                    done[i]++;
                }
            }
        }

        return done;
    }

    private static long latest(Map<String, Long> quests) {
        long latest = 0L;

        for (Long time : quests.values()) {
            latest = Math.max(latest, time);
        }

        return latest;
    }

    private static final class Party {
        final int id;
        final String name;
        final List<String> members;

        Party(int id, String name, List<String> members) {
            this.id = id;
            this.name = name;
            this.members = new ArrayList<String>(members);
        }
    }
}
