package dev.mrakells.gtnhtelegram;

import java.io.File;
import net.minecraftforge.common.config.Configuration;

/** Settings from config/gtnhtelegram.cfg, read once at startup. */
public final class ModConfig {
    private static final String CATEGORY = Configuration.CATEGORY_GENERAL;

    public final boolean enabled;

    public final boolean sendQuestNotifications;
    public final boolean sendRepeatableQuestNotifications;
    public final String botToken;
    public final String chatId;
    public final int messageThreadId;
    public final String questMessageFormat;
    public final String questMessageFormatPlural;
    public final String questDigestFormat;
    public final int questDigestThreshold;
    public final String questBaseUrl;
    public final int connectTimeoutMs;
    public final int readTimeoutMs;

    public final boolean writeQuestFeed;
    public final String questFeedFile;
    public final int questFeedSize;

    private ModConfig(Configuration configuration) {
        enabled = configuration.getBoolean(
                "enabled", CATEGORY, true,
                "Master switch for the whole mod."
        );
        sendQuestNotifications = configuration.getBoolean(
                "sendQuestNotifications", CATEGORY, true,
                "Send a Telegram message when a BetterQuesting quest is completed."
        );
        sendRepeatableQuestNotifications = configuration.getBoolean(
                "sendRepeatableQuestNotifications", CATEGORY, false,
                "Also report repeatable quests, both to Telegram and to the quest feed."
        );
        botToken = configuration.getString(
                "botToken", CATEGORY, "",
                "Telegram bot token from @BotFather."
        ).trim();
        chatId = configuration.getString(
                "chatId", CATEGORY, "",
                "Telegram chat id that receives the notifications."
        ).trim();
        messageThreadId = configuration.getInt(
                "messageThreadId", CATEGORY, 0, 0, Integer.MAX_VALUE,
                "Telegram forum topic id. 0 sends to the main chat."
        );
        questMessageFormat = configuration.getString(
                "questMessageFormat", CATEGORY, "[Minecraft] {player} completed quest: {quest}",
                "Message for one completed quest. Placeholders: {player}, {team}, {quest}, {questName}, {questId}, {questUrl}, {chapter}, {playerUuid}. Write \\n for a line break."
        );
        questMessageFormatPlural = configuration.getString(
                "questMessageFormatPlural", CATEGORY, "",
                "Message used instead of questMessageFormat when several teammates were online and completed the quest together. {player} lists all of them. Empty: use questMessageFormat."
        );
        questDigestFormat = configuration.getString(
                "questDigestFormat", CATEGORY, "[Minecraft] {player} completed {count} quests:\\n{quests}",
                "Summary sent instead of separate messages when the same players complete many quests at once. Placeholders: {player}, {team}, {count}, {quests}."
        );
        questDigestThreshold = configuration.getInt(
                "questDigestThreshold", CATEGORY, 4, 0, 1000,
                "From how many quests completed at once a single summary is sent. 0 always sends separate messages."
        );
        questBaseUrl = configuration.getString(
                "questBaseUrl", CATEGORY, "https://gtnhquestsbook.top/?id=",
                "Base URL of an online quest book. The encoded quest id is appended. Empty: no links."
        ).trim();
        connectTimeoutMs = configuration.getInt(
                "connectTimeoutMs", CATEGORY, 5000, 1000, 30000,
                "Telegram API connection timeout in milliseconds."
        );
        readTimeoutMs = configuration.getInt(
                "readTimeoutMs", CATEGORY, 5000, 1000, 30000,
                "Telegram API read timeout in milliseconds."
        );
        writeQuestFeed = configuration.getBoolean(
                "writeQuestFeed", CATEGORY, false,
                "Keep the most recent completed quests and the current teams in a JSON file that other services can read."
        );
        questFeedFile = configuration.getString(
                "questFeedFile", CATEGORY, "gtnhtelegram/quests.json",
                "Quest feed file. A relative path is resolved against the server directory."
        ).trim();
        questFeedSize = configuration.getInt(
                "questFeedSize", CATEGORY, 50, 1, 500,
                "How many of the most recent completed quests the feed keeps."
        );
    }

    public static ModConfig load(File file) {
        Configuration configuration = new Configuration(file);

        try {
            configuration.load();
            return new ModConfig(configuration);
        } finally {
            if (configuration.hasChanged()) {
                configuration.save();
            }
        }
    }

    public boolean hasTelegramCredentials() {
        return !botToken.isEmpty() && !chatId.isEmpty();
    }
}
