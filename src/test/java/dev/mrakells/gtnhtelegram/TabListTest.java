package dev.mrakells.gtnhtelegram;

import static org.junit.Assert.assertEquals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class TabListTest {
    @Test
    public void sendsFilledHeaderAndFooterAsUtf8Json() {
        Map<String, String> values = new HashMap<String, String>();
        values.put("online", "3");
        values.put("max", "20");
        values.put("player", "Alice");

        byte[] payload = TabList.payload("&b&lСервер \"GTNH\"\\n&7{player}", "&7{online}/{max} {unknown}", values);
        JsonObject json = new JsonParser().parse(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();

        assertEquals("&b&lСервер \"GTNH\"\n&7Alice", json.get("header").getAsString());
        assertEquals("&73/20 {unknown}", json.get("footer").getAsString());
    }

    @Test
    public void averagesTickTimesInMilliseconds() {
        assertEquals(50.0, TabList.meanTickMs(new long[] {40000000L, 60000000L}), 1e-9);
        assertEquals(0.0, TabList.meanTickMs(new long[0]), 1e-9);
    }

    @Test
    public void readsPackVersionFromDreamcraftConfig() {
        assertEquals("2.9.0", TabList.packVersion(Arrays.asList(
                "    # Version of the Modpack [default: 2.9.x]",
                "    S:ModPackVersion=2.9.0 ",
                "    B:OreDictItems=true"
        )));
        assertEquals("", TabList.packVersion(Arrays.asList("general {", "}")));
        assertEquals("", TabList.readPackVersion(new File("does/not/exist.cfg")));
    }

    @Test
    public void colorsTpsByHealth() {
        assertEquals("&a", TabList.tpsColor(20.0));
        assertEquals("&e", TabList.tpsColor(17.0));
        assertEquals("&c", TabList.tpsColor(9.5));
    }
}
