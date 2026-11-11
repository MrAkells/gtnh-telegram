package dev.mrakells.gtnhtelegram;

import betterquesting.api.api.ApiReference;
import betterquesting.api.api.QuestingAPI;
import betterquesting.api.enums.EnumQuestVisibility;
import betterquesting.api.events.QuestEvent;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.IQuestDatabase;
import betterquesting.api.questing.IQuestLine;
import betterquesting.api.questing.IQuestLineDatabase;
import betterquesting.api.questing.party.IParty;
import betterquesting.api.questing.party.IPartyDatabase;
import betterquesting.api.storage.INameCache;
import betterquesting.api2.storage.DBEntry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Turns BetterQuesting events into {@link QuestCompletion}s. Runs on the
 * server thread and only reads BetterQuesting data; everything slow happens
 * in {@link QuestTracker}.
 */
public class BetterQuestingListener {
    /**
     * BetterQuesting reports a completion within seconds. An older timestamp
     * means the quest was copied to this player from a teammate by a team
     * sync, which is not news.
     */
    private static final long STALE_AFTER_MS = 60000L;
    /** GTNH names its invisible helper quests this way. */
    private static final String HELPER_QUEST_PREFIX = "Trigger:";

    private final QuestTracker tracker;
    private final boolean includeRepeatable;
    private final String questBaseUrl;

    public BetterQuestingListener(QuestTracker tracker, boolean includeRepeatable, String questBaseUrl) {
        this.tracker = tracker;
        this.includeRepeatable = includeRepeatable;
        this.questBaseUrl = questBaseUrl;
    }

    /**
     * BetterQuesting posts this event every few seconds for every online
     * player, usually with no quests. A quest shared by a team arrives once
     * per online member; {@link CompletionCollector} merges those.
     */
    @SubscribeEvent
    public void onQuestEvent(QuestEvent event) {
        if (event.getType() != QuestEvent.Type.COMPLETED || event.getPlayerID() == null) {
            return;
        }

        Set<UUID> questIds = event.getQuestIDs();
        if (questIds == null || questIds.isEmpty()) {
            return;
        }

        try {
            report(event.getPlayerID(), questIds);
        } catch (RuntimeException e) {
            // Never let a reporting problem reach BetterQuesting's own event handling.
            GTNHTelegramMod.LOGGER.error("Could not process completed quests", e);
        }
    }

    /** Publishes the current teams, so the feed can show who belongs where. */
    public void refreshTeams() {
        IPartyDatabase parties = QuestingAPI.getAPI(ApiReference.PARTY_DB);
        if (parties == null) {
            return;
        }

        List<Team> teams = new ArrayList<Team>();

        for (DBEntry<IParty> entry : parties.getEntries()) {
            List<UUID> members = entry.getValue().getMembers();

            if (members.size() < 2) {
                continue;
            }

            List<String> names = new ArrayList<String>();
            for (UUID member : members) {
                names.add(playerName(member));
            }

            teams.add(new Team(clean(entry.getValue().getProperties().getProperty(NativeProps.NAME)), names));
        }

        tracker.updateTeams(teams);
    }

    private void report(UUID playerId, Set<UUID> questIds) {
        IQuestDatabase quests = QuestingAPI.getAPI(ApiReference.QUEST_DB);
        long now = System.currentTimeMillis();
        String playerName = null;
        String teamName = null;

        for (UUID questId : questIds) {
            IQuest quest = quests.get(questId);
            if (quest == null || !isAnnounced(quest)) {
                continue;
            }

            String questName = clean(quest.getProperty(NativeProps.NAME));
            if (questName.startsWith(HELPER_QUEST_PREFIX)) {
                continue;
            }

            long completedAt = completionTime(quest, playerId, now);
            if (now - completedAt > STALE_AFTER_MS) {
                continue;
            }

            if (playerName == null) {
                playerName = playerName(playerId);
                teamName = teamName(playerId);
            }

            tracker.report(new QuestCompletion(
                    completedAt / 1000L,
                    questId.toString(),
                    questName.isEmpty() ? questId.toString() : questName,
                    chapterName(questId),
                    QuestLinks.questUrl(questBaseUrl, questId),
                    teamName,
                    Collections.singletonList(playerName),
                    Collections.singletonList(playerId.toString())
            ));
        }

        if (playerName != null) {
            refreshTeams();
        }
    }

    private boolean isAnnounced(IQuest quest) {
        if (!includeRepeatable && quest.getProperty(NativeProps.REPEAT_TIME) >= 0) {
            return false;
        }

        return quest.getProperty(NativeProps.VISIBILITY) != EnumQuestVisibility.HIDDEN;
    }

    private static long completionTime(IQuest quest, UUID playerId, long fallback) {
        NBTTagCompound info = quest.getCompletionInfo(playerId);

        return info != null && info.hasKey("timestamp") ? info.getLong("timestamp") : fallback;
    }

    private static String playerName(UUID playerId) {
        EntityPlayerMP player = QuestingAPI.getPlayer(playerId);
        if (player != null) {
            return player.getCommandSenderName();
        }

        // Offline teammates are known to BetterQuesting by their cached name.
        INameCache names = QuestingAPI.getAPI(ApiReference.NAME_CACHE);
        String cached = names == null ? null : names.getName(playerId);

        return cached == null || cached.trim().isEmpty() ? playerId.toString() : cached;
    }

    /** @return the name of the player's team, or an empty string for a solo player */
    private static String teamName(UUID playerId) {
        IPartyDatabase parties = QuestingAPI.getAPI(ApiReference.PARTY_DB);
        DBEntry<IParty> entry = parties == null ? null : parties.getParty(playerId);

        if (entry == null || entry.getValue().getMembers().size() < 2) {
            return "";
        }

        return clean(entry.getValue().getProperties().getProperty(NativeProps.NAME));
    }

    private static String chapterName(UUID questId) {
        IQuestLineDatabase lines = QuestingAPI.getAPI(ApiReference.LINE_DB);

        for (IQuestLine line : lines.values()) {
            if (line != null && line.containsKey(questId)) {
                return clean(line.getProperty(NativeProps.NAME));
            }
        }

        return "";
    }

    /** Removes Minecraft formatting codes such as "§4§l". */
    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("§.", "").trim();
    }
}
