package dev.awaken.wilds;

import dev.awaken.core.DirectorLog;
import dev.awaken.core.Playtest;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Finds;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Biome discoveries, promised by plan v2 ("first sightings of a creature, biome or landmark"; SPEC §17): the first time
 * an awakened player stands in a biome of the overworld, the Nether or the End it is a discovery ({@link #XP} of a
 * level, once per biome) with a toast, counted in {@link #COUNTER}. Checked every two seconds per player (staggered), and
 * only when the biome underfoot changed. Instanced worlds (gates, strata, dreams) keep their own firsts.
 */
public final class BiomeDiscovery {
    private BiomeDiscovery() {}

    static final int PERIOD = 40;
    public static final double XP = 0.1;
    public static final String COUNTER = "awaken:biomes_discovered";

    private static final Map<UUID, ResourceKey<Biome>> LAST = new HashMap<>();

    static void tick(MinecraftServer server) {
        if (server instanceof GameTestServer || !WildsModule.on()) return;
        int t = server.getTickCount();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (Math.floorMod(t + p.getUUID().hashCode(), PERIOD) == 0) check(p);
        }
    }

    static void forget(UUID id) {
        LAST.remove(id);
    }

    static void clear() {
        LAST.clear();
    }

    /** One look at the biome underfoot. True when it was a first. */
    public static boolean check(ServerPlayer p) {
        if (p.isSpectator() || !Progression.profile(p).awakened()) return false;
        if (!p.level().dimension().identifier().getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) return false;
        var key = p.level().getBiome(p.blockPosition()).unwrapKey();
        if (key.isEmpty() || key.get().equals(LAST.put(p.getUUID(), key.get()))) return false;
        Identifier id = key.get().identifier();
        if (!Progression.discover(p, "biome/" + id.getNamespace() + "/" + id.getPath(), XP)) return false;
        Progression.count(p, COUNTER);
        Progression.markNewThing(p, "biome");
        Component name = Component.translatableWithFallback(id.toLanguageKey("biome"), pretty(id.getPath()));
        // a biome is big and in plain view: standing in it is enough, but it is a quiet find (SPEC §26.3)
        Finds.tell(p, Finds.Find.of("biome", "New biome · " + name.getString(), name.getString(), "First steps here", 0x9FE6D6, false));
        DirectorLog.log(p, "wilds", "first steps in " + id);
        Playtest.log(p, "biome", "id", id.toString());
        return true;
    }

    /** "snowy_taiga" → "Snowy Taiga" (a biome without a name in the language file). */
    static String pretty(String path) {
        StringBuilder b = new StringBuilder(path.length());
        boolean up = true;
        for (char c : path.toCharArray()) {
            if (c == '_' || c == '/') {
                b.append(' ');
                up = true;
            } else {
                b.append(up ? Character.toUpperCase(c) : c);
                up = false;
            }
        }
        return b.toString();
    }
}
