package dev.mrakells.gtnhtelegram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A BetterQuesting party with more than one member, as written to the quest feed. */
public final class Team {
    private String name;
    private List<String> members;

    /** For Gson. */
    @SuppressWarnings("unused")
    private Team() {
    }

    public Team(String name, List<String> members) {
        this.name = name;
        this.members = new ArrayList<String>(members);
    }

    public String name() {
        return name == null ? "" : name;
    }

    public List<String> members() {
        return members == null ? Collections.<String>emptyList() : Collections.unmodifiableList(members);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Team)) {
            return false;
        }

        Team team = (Team) other;
        return name().equals(team.name()) && members().equals(team.members());
    }

    @Override
    public int hashCode() {
        return 31 * name().hashCode() + members().hashCode();
    }
}
