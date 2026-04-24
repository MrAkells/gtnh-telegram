package dev.mrakells.gtnhtelegram;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.events.QuestEvent;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.IQuestLine;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestLineDatabase;
import com.google.common.io.BaseEncoding;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;

public class BetterQuestingListener {
    @SubscribeEvent
    public void onQuestCompleted(QuestEvent event) {
        if (!ModConfig.enabled
                || !ModConfig.sendQuestNotifications
                || event == null
                || event.getType() != QuestEvent.Type.COMPLETED) {
            return;
        }

        Set<UUID> questIds = event.getQuestIDs();
        if (questIds == null || questIds.isEmpty()) {
            return;
        }

        EntityPlayerMP player = QuestingAPI.getPlayer(event.getPlayerID());
        String playerName = escapeHtml(player == null ? String.valueOf(event.getPlayerID()) : player.getCommandSenderName());
        String playerUuid = event.getPlayerID() == null ? "" : escapeHtml(event.getPlayerID().toString());

        for (UUID questId : questIds) {
            IQuest quest = QuestDatabase.INSTANCE.get(questId);
            if (quest == null) {
                continue;
            }

            if (!ModConfig.sendRepeatableQuestNotifications && quest.getProperty(NativeProps.REPEAT_TIME) >= 0) {
                continue;
            }

            String questNameRaw = sanitize(quest.getProperty(NativeProps.NAME), questId.toString());
            if (isHiddenTriggerQuest(questNameRaw)) {
                continue;
            }
            String questName = escapeHtml(questNameRaw);
            String chapterName = escapeHtml(findChapterName(questId));
            String questUrl = escapeHtml(buildQuestUrl(questId));
            String questLink = "<a href=\"" + questUrl + "\">" + questName + "</a>";
            String message = ModConfig.questMessageFormat
                    .replace("{player}", playerName)
                    .replace("{playerUuid}", playerUuid)
                    .replace("{quest}", questLink)
                    .replace("{questName}", questName)
                    .replace("{questId}", escapeHtml(questId.toString()))
                    .replace("{chapter}", chapterName);
            message = message.replace("{questUrl}", questUrl);

            TelegramSender.send(message);
        }
    }

    private static String buildQuestUrl(UUID questId) {
        String baseUrl = ModConfig.questBaseUrl == null ? "" : ModConfig.questBaseUrl.trim();
        return baseUrl + encodeQuestId(questId);
    }

    private static String encodeQuestId(UUID questId) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(questId.getMostSignificantBits());
        buffer.putLong(questId.getLeastSignificantBits());
        return BaseEncoding.base64().encode(buffer.array());
    }

    private static String findChapterName(UUID questId) {
        for (Map.Entry<UUID, IQuestLine> entry : QuestLineDatabase.INSTANCE.entrySet()) {
            IQuestLine questLine = entry.getValue();
            if (questLine != null && questLine.containsKey(questId)) {
                return sanitize(questLine.getProperty(NativeProps.NAME), "");
            }
        }

        return "";
    }

    private static String sanitize(String value, String fallback) {
        if (value == null) {
            return fallback;
        }

        String trimmed = stripFormattingCodes(value).trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static boolean isHiddenTriggerQuest(String questName) {
        return questName != null && questName.startsWith("Trigger:");
    }

    private static String stripFormattingCodes(String value) {
        return value.replaceAll("\u00A7.", "");
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
