package dev.mrakells.gtnhtelegram;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
        modid = GTNHTelegramMod.MOD_ID,
        name = GTNHTelegramMod.MOD_NAME,
        version = GTNHTelegramMod.VERSION,
        acceptableRemoteVersions = "*"
)
public class GTNHTelegramMod {
    public static final String MOD_ID = "gtnhtelegram";
    public static final String MOD_NAME = "GTNH Telegram Achievements";
    public static final String VERSION = "1.0.0";

    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ModConfig.load(event.getSuggestedConfigurationFile());
        TelegramSender.configure(
                ModConfig.botToken,
                ModConfig.chatId,
                ModConfig.messageThreadId,
                ModConfig.connectTimeoutMs,
                ModConfig.readTimeoutMs
        );
        if (Loader.isModLoaded("betterquesting")) {
            MinecraftForge.EVENT_BUS.register(new BetterQuestingListener());
            LOGGER.info("BetterQuesting integration enabled");
        }
        LOGGER.info("Loaded {} with Telegram notifications {}", MOD_NAME, ModConfig.enabled ? "enabled" : "disabled");
    }
}
