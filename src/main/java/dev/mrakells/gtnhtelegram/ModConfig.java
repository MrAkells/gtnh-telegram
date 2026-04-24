package dev.mrakells.gtnhtelegram;

import java.io.File;
import net.minecraftforge.common.config.Configuration;

public final class ModConfig {
    public static boolean enabled = true;
    public static boolean sendQuestNotifications = true;
    public static boolean sendRepeatableQuestNotifications = false;
    public static String botToken = "";
    public static String chatId = "";
    public static String questMessageFormat = "[Minecraft] {player} завершил квест: {quest}";
    public static String questBaseUrl = "https://gtnhquestsbook.top/?id=";
    public static int connectTimeoutMs = 5000;
    public static int readTimeoutMs = 5000;

    private ModConfig() {
    }

    public static void load(File file) {
        Configuration configuration = new Configuration(file);

        try {
            configuration.load();
            enabled = configuration.getBoolean(
                    "enabled",
                    Configuration.CATEGORY_GENERAL,
                    true,
                    "Master switch for Telegram notifications."
            );
            sendQuestNotifications = configuration.getBoolean(
                    "sendQuestNotifications",
                    Configuration.CATEGORY_GENERAL,
                    true,
                    "Send Telegram notifications when a BetterQuesting quest is completed."
            );
            sendRepeatableQuestNotifications = configuration.getBoolean(
                    "sendRepeatableQuestNotifications",
                    Configuration.CATEGORY_GENERAL,
                    false,
                    "Send Telegram notifications for repeatable BetterQuesting quests too."
            );
            botToken = configuration.getString(
                    "botToken",
                    Configuration.CATEGORY_GENERAL,
                    "",
                    "Telegram bot token from BotFather."
            ).trim();
            chatId = configuration.getString(
                    "chatId",
                    Configuration.CATEGORY_GENERAL,
                    "",
                    "Telegram chat id that will receive notifications."
            ).trim();
            questMessageFormat = configuration.getString(
                    "questMessageFormat",
                    Configuration.CATEGORY_GENERAL,
                    "[Minecraft] {player} завершил квест: {quest}",
                    "Quest message template. Supported placeholders: {player}, {quest}, {questName}, {questId}, {chapter}, {playerUuid}, {questUrl}."
            );
            questBaseUrl = configuration.getString(
                    "questBaseUrl",
                    Configuration.CATEGORY_GENERAL,
                    "https://gtnhquestsbook.top/?id=",
                    "Base URL for online quest links. The encoded BetterQuesting id will be appended automatically."
            );
            connectTimeoutMs = configuration.getInt(
                    "connectTimeoutMs",
                    Configuration.CATEGORY_GENERAL,
                    5000,
                    1000,
                    30000,
                    "Telegram API connection timeout in milliseconds."
            );
            readTimeoutMs = configuration.getInt(
                    "readTimeoutMs",
                    Configuration.CATEGORY_GENERAL,
                    5000,
                    1000,
                    30000,
                    "Telegram API read timeout in milliseconds."
            );
        } finally {
            if (configuration.hasChanged()) {
                configuration.save();
            }
        }
    }
}
