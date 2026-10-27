package dev.awaken.atlas;

import dev.awaken.core.AwakenNet;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.hearth.Hearth;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * (SPEC §16.8) The Hearths a player's Atlas marks: every one they own or belong to, and each housemate's own. Read through
 * the Hearth module's public API ({@link Hearth}, {@link HearthHooks}), sent when it changes, checked every few seconds.
 */
final class Homes {
    private Homes() {}

    static final int PERIOD = 100;

    /** What this player's Atlas should mark, their own Hearth first. */
    static List<AtlasNet.HomeView> of(ServerPlayer p) {
        if (p.level().dimension() != Level.OVERWORLD) return List.of();
        MinecraftServer server = p.level().getServer();
        Map<UUID, String> names = new HashMap<>();
        for (Hearth.Village v : Hearth.villages(server)) names.put(v.owner(), v.name());
        Map<UUID, List<Hearth.HearthInfo>> mates = new TreeMap<>(); // in a steady order, so an unchanged list hashes alike
        for (UUID mate : HearthHooks.household(p)) mates.put(mate, Hearth.hearthsOf(server, mate));
        return assemble(p.getUUID(), Hearth.hearthsOf(server, p.getUUID()), mates, names);
    }

    /**
     * The marks from what the Hearth module says: the player's own Hearths and those they share (in its order, their own
     * first), then each housemate's own; each Anchor once, at most 64; named by the village's name, else whose it is.
     */
    static List<AtlasNet.HomeView> assemble(UUID me, List<Hearth.HearthInfo> mine, Map<UUID, List<Hearth.HearthInfo>> mates,
                                            Map<UUID, String> names) {
        List<AtlasNet.HomeView> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Hearth.HearthInfo h : mine) add(out, seen, h, names, h.owner().equals(me) ? AtlasNet.Homes.OWN : AtlasNet.Homes.SHARED);
        for (Map.Entry<UUID, List<Hearth.HearthInfo>> e : mates.entrySet())
            for (Hearth.HearthInfo h : e.getValue())
                if (h.owner().equals(e.getKey())) add(out, seen, h, names, AtlasNet.Homes.HOUSEHOLD);
        return out;
    }

    private static void add(List<AtlasNet.HomeView> out, Set<Long> seen, Hearth.HearthInfo h, Map<UUID, String> names, int kind) {
        if (out.size() >= 64 || !seen.add(h.anchor().asLong())) return;
        out.add(new AtlasNet.HomeView(name(names.get(h.owner()), h.ownerName(), kind), h.anchor().getX(), h.anchor().getY(),
                h.anchor().getZ(), h.tier(), kind));
    }

    /** A village's own name, else whose Hearth it is, in plain words. */
    static String name(String village, String owner, int kind) {
        if (village != null && !village.isBlank()) return trim(village);
        if (kind == AtlasNet.Homes.OWN) return "Your Hearth";
        return trim(owner.isBlank() ? "A shared Hearth" : owner + "'s Hearth");
    }

    private static String trim(String s) {
        return s.length() <= 48 ? s : s.substring(0, 48);
    }

    /** Sends the player's Hearths when they differ from what their Atlas last heard ({@code force} on a join or a move). */
    static void send(ServerPlayer p, Discovery.Live l, boolean force) {
        List<AtlasNet.HomeView> homes = of(p);
        int hash = homes.hashCode();
        if (!force && hash == l.homes) return;
        l.homes = hash;
        AwakenNet.send(p, new AtlasNet.Homes(homes));
    }
}
