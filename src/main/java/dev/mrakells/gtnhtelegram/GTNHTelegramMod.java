package dev.mrakells.gtnhtelegram;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import java.io.File;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
        modid = GTNHTelegramMod.MOD_ID,
        name = GTNHTelegramMod.MOD_NAME,
        version = Tags.VERSION,
        acceptedMinecraftVersions = "[1.7.10]",
        // Server-side only: clients do not need the mod to join.
        acceptableRemoteVersions = "*",
        dependencies = "after:betterquesting"
)
public class GTNHTelegramMod {
    public static final String MOD_ID = "gtnhtelegram";
    public static final String MOD_NAME = "GTNH Telegram Achievements";

    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    /**
     * How long reports of one completion are collected before it is published.
     * BetterQuesting checks every online player once per 3 seconds, so all
     * online teammates have reported by then.
     */
    private static final long COLLECT_WINDOW_MS = 4000L;

    private QuestTracker tracker;
    /** Kept as a Runnable so this class loads even when BetterQuesting is absent. */
    private Runnable rosterRefresher;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ModConfig config = ModConfig.load(event.getSuggestedConfigurationFile());

        if (!config.enabled) {
            LOGGER.info("{} is disabled in the config", MOD_NAME);
            return;
        }

        // The tab list has nothing to do with quests, so it works without BetterQuesting too.
        if (config.tabListEnabled) {
            FMLCommonHandler.instance().bus().register(new TabList(
                    config.tabListHeader,
                    config.tabListFooter,
                    config.tabListRefreshSeconds,
                    event.getModConfigurationDirectory()
            ));
            LOGGER.info("{} sends the tab list header and footer", MOD_NAME);
        }

        if (!Loader.isModLoaded("betterquesting")) {
            LOGGER.warn("BetterQuesting is not installed, there are no quests to track");
            return;
        }

        QuestFeed feed = createFeed(config, event.getModConfigurationDirectory().getParentFile());
        TelegramClient telegram = createTelegram(config);

        if (feed == null && telegram == null) {
            LOGGER.warn("Neither Telegram notifications nor the quest feed are set up, nothing to do");
            return;
        }

        TelegramNotifier notifier = telegram == null ? null : new TelegramNotifier(
                telegram,
                config.questMessageFormat,
                config.questMessageFormatPlural,
                config.questDigestFormat,
                config.questDigestThreshold
        );

        tracker = new QuestTracker(new CompletionCollector(COLLECT_WINDOW_MS), feed, notifier, telegram, LOGGER);

        final BetterQuestingListener listener = new BetterQuestingListener(
                tracker,
                config.sendRepeatableQuestNotifications,
                config.questBaseUrl
        );
        // Quest events come through the Forge bus, server ticks through the FML one.
        MinecraftForge.EVENT_BUS.register(listener);
        FMLCommonHandler.instance().bus().register(listener);
        rosterRefresher = new Runnable() {
            @Override
            public void run() {
                listener.refreshRoster();
            }
        };

        LOGGER.info(
                "{} {} is tracking quest completions (Telegram: {}, quest feed: {})",
                MOD_NAME,
                Tags.VERSION,
                telegram == null ? "off" : "on",
                feed == null ? "off" : feed.file() + ", " + feed.quests().size() + " entries loaded"
        );
    }

    /** BetterQuesting has loaded the world's quest data by now. */
    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        if (rosterRefresher != null) {
            rosterRefresher.run();
        }
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        if (tracker != null) {
            tracker.flush();
        }
    }

    private static QuestFeed createFeed(ModConfig config, File serverDirectory) {
        if (!config.writeQuestFeed || config.questFeedFile.isEmpty()) {
            return null;
        }

        File file = new File(config.questFeedFile);
        if (!file.isAbsolute()) {
            file = new File(serverDirectory, config.questFeedFile);
        }

        QuestFeed feed = new QuestFeed(file.toPath(), config.questFeedSize, LOGGER);
        feed.load();
        return feed;
    }

    private static TelegramClient createTelegram(ModConfig config) {
        if (!config.sendQuestNotifications) {
            return null;
        }

        if (!config.hasTelegramCredentials()) {
            LOGGER.warn("Telegram notifications are on, but botToken or chatId is missing in config/gtnhtelegram.cfg");
            return null;
        }

        return new TelegramClient(
                config.botToken,
                config.chatId,
                config.messageThreadId,
                config.connectTimeoutMs,
                config.readTimeoutMs,
                LOGGER
        );
    }
}
