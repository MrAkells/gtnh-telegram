package dev.mrakells.gtnhtelegram;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import org.junit.Test;

public class RosterBuilderTest {
    private static RosterBuilder book() {
        RosterBuilder builder = new RosterBuilder();
        builder.chapter("Tier 0 - Stone Age", new HashSet<String>(Arrays.asList("a1", "a2", "a3")));
        builder.chapter("Tier 0.5 - Steam", new HashSet<String>(Arrays.asList("b1", "b2")));
        return builder;
    }

    @Test
    public void countsCompletedQuestsPerChapter() {
        RosterBuilder builder = book();
        builder.player("u-zed", "zed");
        builder.player("u-amy", "Amy");
        builder.completion("u-amy", "a1", 10L);
        builder.completion("u-amy", "b2", 30L);
        builder.completion("u-amy", "not in the book", 99L);

        Roster roster = builder.build();

        assertEquals(2, roster.chapters().size());
        assertEquals("Tier 0 - Stone Age", roster.chapters().get(0).name);
        assertEquals(3, roster.chapters().get(0).quests);

        Roster.Player amy = roster.players().get(0);
        assertEquals("players are sorted by name, ignoring case", "Amy", amy.name);
        assertArrayEquals(new int[] {1, 1}, amy.done);
        assertEquals("the latest completion, even of a quest outside the book", 99L, amy.lastQuest);
        assertNull(amy.team);

        Roster.Player zed = roster.players().get(1);
        assertArrayEquals(new int[] {0, 0}, zed.done);
        assertEquals(0L, zed.lastQuest);
    }

    @Test
    public void aTeamSharesWhatAnyMemberCompleted() {
        RosterBuilder builder = book();
        builder.player("u-amy", "Amy");
        builder.player("u-bob", "Bob");
        builder.completion("u-amy", "a1", 10L);
        builder.completion("u-amy", "a2", 20L);
        builder.completion("u-bob", "a2", 25L);
        builder.completion("u-bob", "b1", 40L);
        builder.party(3, "Diggers", Arrays.asList("u-amy", "u-bob"));

        Roster roster = builder.build();
        Roster.Team team = roster.teams().get(0);

        assertEquals(3, team.id);
        assertEquals("Diggers", team.name);
        assertEquals(Arrays.asList("u-amy", "u-bob"), team.members);
        assertArrayEquals(new int[] {2, 1}, team.done);
        assertEquals(40L, team.lastQuest);
        assertEquals(Integer.valueOf(3), roster.players().get(0).team);
        assertEquals(Integer.valueOf(3), roster.players().get(1).team);
    }

    @Test
    public void aPartyOfOneIsASoloPlayer() {
        RosterBuilder builder = book();
        builder.player("u-amy", "Amy");
        builder.party(1, "Amy_team", Collections.singletonList("u-amy"));
        // The second member is unknown to the name cache, so the party still has one usable member.
        builder.party(2, "Ghosts", Arrays.asList("u-amy", "u-gone"));

        Roster roster = builder.build();

        assertEquals(0, roster.teams().size());
        assertNull(roster.players().get(0).team);
    }
}
