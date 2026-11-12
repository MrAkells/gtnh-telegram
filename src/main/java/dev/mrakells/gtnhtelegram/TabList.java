package dev.mrakells.gtnhtelegram;

import com.google.gson.JsonObject;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import net.minecraft.server.MinecraftServer;

/**
 * Header and footer of the ServerUtilities tab list.
 *
 * <p>ServerUtilities draws its tab list on the client and takes the header and
 * footer from the client's own config, so changing the server's
 * {@code serverutilities.cfg} shows nothing to players. The only way to set
 * them from the server is the {@code SU|TabHF} channel the client listens on
 * (meant for proxy plugins): a UTF-8 JSON object
 * {@code {"header": "...", "footer": "..."}}. The client turns {@code &} codes
 * into formatting itself and forgets the values on disconnect, so they are sent
 * on login and then refreshed to keep the placeholders current.
 */
public final class TabList {
    static final String CHANNEL = "SU|TabHF";

    /** GTNH keeps the pack version here and updates it with the pack's configs. */
    static final String PACK_VERSION_FILE = "GTNewHorizons/dreamcraft.cfg";
    private static final String PACK_VERSION_KEY = "S:ModPackVersion=";

    private final String header;
    private final String footer;
    private final String packVersion;
    private final int refreshTicks;
    private int ticksSinceRefresh;

    public TabList(String header, String footer, int refreshSeconds, File configDirectory) {
        this.header = header;
        this.footer = footer;
        this.packVersion = readPackVersion(new File(configDirectory, PACK_VERSION_FILE));
        this.refreshTicks = refreshSeconds * 20;
    }

    /** Empty when the file is missing or has no version, so the placeholder just disappears. */
    static String readPackVersion(File file) {
        if (!file.isFile()) {
            return "";
        }

        try {
            return packVersion(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            GTNHTelegramMod.LOGGER.warn("Could not read the pack version from {}", file, e);
            return "";
        }
    }

    static String packVersion(List<String> lines) {
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith(PACK_VERSION_KEY)) {
                return trimmed.substring(PACK_VERSION_KEY.length()).trim();
            }
        }
        return "";
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            MinecraftServer server = MinecraftServer.getServer();
            send((EntityPlayerMP) event.player, serverValues(server));
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || refreshTicks <= 0 || ++ticksSinceRefresh < refreshTicks) {
            return;
        }

        ticksSinceRefresh = 0;
        MinecraftServer server = MinecraftServer.getServer();
        Map<String, String> values = serverValues(server);

        // A copy, because a player who gets kicked while sending leaves the list.
        for (Object player : new ArrayList<Object>(server.getConfigurationManager().playerEntityList)) {
            send((EntityPlayerMP) player, values);
        }
    }

    /** Never throws: this runs inside the server tick and the login handling. */
    private void send(EntityPlayerMP player, Map<String, String> serverValues) {
        try {
            Map<String, String> values = new HashMap<String, String>(serverValues);
            values.put("player", player.getCommandSenderName());
            player.playerNetServerHandler.sendPacket(new S3FPacketCustomPayload(CHANNEL, payload(header, footer, values)));
        } catch (RuntimeException e) {
            GTNHTelegramMod.LOGGER.error("Could not send the tab list header and footer", e);
        }
    }

    private Map<String, String> serverValues(MinecraftServer server) {
        double tickMs = meanTickMs(server.tickTimeArray);
        double tps = Math.min(20.0, 1000.0 / Math.max(tickMs, 1.0e-3));

        Map<String, String> values = new HashMap<String, String>();
        values.put("packVersion", packVersion);
        values.put("online", String.valueOf(server.getCurrentPlayerCount()));
        values.put("max", String.valueOf(server.getMaxPlayers()));
        values.put("tps", String.format(Locale.ROOT, "%.1f", tps));
        values.put("mspt", String.format(Locale.ROOT, "%.1f", tickMs));
        values.put("tpsColor", tpsColor(tps));
        return values;
    }

    /** Mean duration of the last 100 server ticks, the same figure /forge tps reports. */
    static double meanTickMs(long[] tickTimesNs) {
        if (tickTimesNs == null || tickTimesNs.length == 0) {
            return 0.0;
        }

        long sum = 0;
        for (long time : tickTimesNs) {
            sum += time;
        }
        return sum / (double) tickTimesNs.length / 1.0e6;
    }

    static String tpsColor(double tps) {
        if (tps >= 19.5) {
            return "&a";
        }
        return tps >= 15.0 ? "&e" : "&c";
    }

    static byte[] payload(String header, String footer, Map<String, String> values) {
        JsonObject json = new JsonObject();
        json.addProperty("header", QuestMessages.fill(header, values));
        json.addProperty("footer", QuestMessages.fill(footer, values));
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }
}
