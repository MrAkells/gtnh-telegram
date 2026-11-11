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
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Turns BetterQuesting events into {@link QuestCompletion}s and BetterQuesting
 * data into a {@link Roster}. Runs on the server thread and only reads
 * BetterQuesting data; everything slow happens in {@link QuestTracker}.
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
    /**
     * Joining a team, leaving it or renaming it raises no event this mod can
     * see, so the roster is also rebuilt every five minutes.
     */
    private static final int ROSTER_REFRESH_TICKS = 5 * 60 * 20;

    private final QuestTracker tracker;
    private final boolean includeRepeatable;
    private final String questBaseUrl;
    private int ticksSinceRefresh;
    /** Set after an unexpected failure, so a broken roster is logged once instead of every five minutes. */
    private boolean rosterBroken;

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

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && ++ticksSinceRefresh >= ROSTER_REFRESH_TICKS) {
            refreshRoster();
        }
    }

    /**
     * Publishes every player and team with their progress, so the feed can
     * show who belongs where and how far they have got. Never throws: this
     * runs inside the server tick, where a failure would stop the server.
     */
    public void refreshRoster() {
        ticksSinceRefresh = 0;

        if (rosterBroken || !tracker.wantsRoster()) {
            return;
        }

        try {
            Roster roster = readRoster();

            if (roster != null) {
                tracker.updateRoster(roster);
            }
        } catch (Throwable e) {
            rosterBroken = true;
            GTNHTelegramMod.LOGGER.error("Could not read players and teams from BetterQuesting, the quest feed will not list them", e);
        }
    }

    private Roster readRoster() {
        IQuestDatabase quests = QuestingAPI.getAPI(ApiReference.QUEST_DB);
        IQuestLineDatabase lines = QuestingAPI.getAPI(ApiReference.LINE_DB);
        INameCache names = QuestingAPI.getAPI(ApiReference.NAME_CACHE);
        IPartyDatabase parties = QuestingAPI.getAPI(ApiReference.PARTY_DB);

        if (quests == null || lines == null || names == null || parties == null) {
            return null;
        }

        // Hidden and helper quests are not part of the book a player sees, so they are not progress either.
        Map<UUID, IQuest> counted = new HashMap<UUID, IQuest>();
        for (Map.Entry<UUID, IQuest> entry : quests.entrySet()) {
            IQuest quest = entry.getValue();

            if (quest != null
                    && quest.getProperty(NativeProps.VISIBILITY) != EnumQuestVisibility.HIDDEN
                    && !clean(quest.getProperty(NativeProps.NAME)).startsWith(HELPER_QUEST_PREFIX)) {
                counted.put(entry.getKey(), quest);
            }
        }

        RosterBuilder builder = new RosterBuilder();

        for (Map.Entry<UUID, IQuestLine> entry : lines.getOrderedEntries()) {
            Set<String> questIds = new HashSet<String>();

            for (UUID questId : entry.getValue().keySet()) {
                if (counted.containsKey(questId)) {
                    questIds.add(questId.toString());
                }
            }

            builder.chapter(clean(entry.getValue().getProperty(NativeProps.NAME)), questIds);
        }

        for (String name : names.getAllNames()) {
            UUID playerId = names.getUUID(name);
            if (playerId == null) {
                continue;
            }

            builder.player(playerId.toString(), name);

            for (Map.Entry<UUID, IQuest> entry : counted.entrySet()) {
                if (entry.getValue().isComplete(playerId)) {
                    builder.completion(playerId.toString(), entry.getKey().toString(), completionTime(entry.getValue(), playerId, 0L) / 1000L);
                }
            }
        }

        for (DBEntry<IParty> entry : parties.getEntries()) {
            List<String> members = new ArrayList<String>();
            for (UUID member : entry.getValue().getMembers()) {
                members.add(member.toString());
            }

            builder.party(entry.getID(), clean(entry.getValue().getProperties().getProperty(NativeProps.NAME)), members);
        }

        return builder.build();
    }

    private void report(UUID playerId, Set<UUID> questIds) {
        IQuestDatabase quests = QuestingAPI.getAPI(ApiReference.QUEST_DB);
        long now = System.currentTimeMillis();
        String playerName = null;
        DBEntry<IParty> team = null;

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
                team = team(playerId);
            }

            tracker.report(new QuestCompletion(
                    completedAt / 1000L,
                    questId.toString(),
                    questName.isEmpty() ? questId.toString() : questName,
                    chapterName(questId),
                    QuestLinks.questUrl(questBaseUrl, questId),
                    team == null ? null : Integer.valueOf(team.getID()),
                    team == null ? "" : clean(team.getValue().getProperties().getProperty(NativeProps.NAME)),
                    Collections.singletonList(playerId.toString()),
                    Collections.singletonList(playerName)
            ));
        }

        if (playerName != null) {
            refreshRoster();
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

    /** @return the player's team, or null for a solo player; a party of one is not a team */
    private static DBEntry<IParty> team(UUID playerId) {
        IPartyDatabase parties = QuestingAPI.getAPI(ApiReference.PARTY_DB);
        DBEntry<IParty> entry = parties == null ? null : parties.getParty(playerId);

        return entry == null || entry.getValue().getMembers().size() < 2 ? null : entry;
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
