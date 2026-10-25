package dev.awaken.citizens.client;

import dev.awaken.citizens.CitizensNet;
import dev.awaken.city.CityPlan;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;

/**
 * Where the city stands, on the client (sent at login: {@link CitizensNet.Site}), and its plan (read from the classpath,
 * the same file the server paints from). The life the client draws for itself reads both: animals, the soundscape and
 * the lamps' light (M4 B3b). Null outside a Carillon world.
 */
public final class CityLife {
    private CityLife() {}

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
}
