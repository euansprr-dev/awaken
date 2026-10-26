package dev.awaken.citizens.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.Awaken;
import dev.awaken.citizens.CitizensNet;
import dev.awaken.city.CityPlan;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Where the city stands, on the client (sent at login: {@link CitizensNet.Site}), and its plan (read from the classpath,
 * the same file the server paints from). The life the client draws for itself reads both: animals, the soundscape and
 * the lamps' light (M4 B3b). Null outside a Carillon world.
 */
public final class CityLife {
    private CityLife() {}

    /** The river's half width (terrain's CitySite.riverHalfWidth): its quays stand this far either side of the line. */
    static final double RIVER_HALF = 9;

    private static boolean known;
    static int cx, sy, cz;

    static void site(CitizensNet.Site msg, IPayloadContext ctx) {
        cx = msg.cx();
        sy = msg.sy();
        cz = msg.cz();
        known = true;
    }

    /** The plan, once the site is known (null before, or if the plan failed to load). */
    static @Nullable CityPlan plan() {
        if (!known) return null;
        try {
            return CityPlan.get();
        } catch (RuntimeException ex) {
            known = false;
            return null;
        }
    }

    static boolean known() {
        return known && Minecraft.getInstance().level != null;
    }

    static void clear() {
        known = false;
    }

    /**
     * Whether (x, z) lies within {@code margin} blocks outside Carillon's wall (or inside it); false outside a Carillon
     * world. Wild life (SPEC "Wild life") keeps out: the city's animals are B3b's own.
     */
    public static boolean inside(double x, double z, double margin) {
        if (!known) return false;
        CityPlan plan = plan();
        return plan != null && Math.hypot(x - cx, z - cz) < plan.wallRadius + margin;
    }

    /**
     * A path in the plan file that CityPlan doesn't keep (plan.json's "canal", "millRace": [[x, z], ...] in plan
     * coordinates), read once by whoever needs it; empty if it's missing.
     */
    static List<float[]> path(String key) {
        List<float[]> out = new ArrayList<>();
        try (InputStream in = CityPlan.class.getResourceAsStream(CityPlan.RESOURCE)) {
            if (in == null) return out;
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray arr = o.getAsJsonArray(key);
            if (arr == null) return out;
            for (var e : arr) out.add(new float[]{e.getAsJsonArray().get(0).getAsFloat(), e.getAsJsonArray().get(1).getAsFloat()});
        } catch (Exception ex) {
            Awaken.LOG.warn("[citizens] plan path {}: {}", key, ex.toString());
        }
        return out;
    }
}
