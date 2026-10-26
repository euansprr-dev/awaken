package dev.awaken.living.region;

import dev.awaken.core.PlayDays;
import dev.awaken.core.api.Notices;
import dev.awaken.living.LivingNet;
import dev.awaken.living.api.Regions;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Which region each player stands in (SPEC §21.2). Every 2 s per player (staggered), a change sends the region to the
 * client: a place caption the first time in a play-day, an ember caption and an amber warning (at most once per 10
 * minutes) when the region is 5+ levels above the player. Nothing is saved; a relog may caption again.
 */
public final class RegionTracker {
    private RegionTracker() {}

    static final int EVERY = 40;
    static final int DANGER = 5;
    static final long WARN_COOLDOWN = 20L * 60 * 10;

    private static final Map<UUID, Long> CURRENT = new HashMap<>();
    private static final Map<UUID, Map<Long, Long>> CAPTIONED = new HashMap<>();
    private static final Map<UUID, Long> WARNED = new HashMap<>();
    /** Whether each player was inside the city's walls at the last look, and the play-day of their last way out. */
    private static final Map<UUID, Boolean> IN_CITY = new HashMap<>();
    private static final Map<UUID, Long> WENT_OUT = new HashMap<>();

    public static void tick(MinecraftServer server) {
        int t = server.getTickCount();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if ((t + (p.getId() & 31)) % EVERY != 0) continue;
            if (!(p.level() instanceof ServerLevel level) || !Regions.on(level) || p.isSpectator()) {
                CURRENT.remove(p.getUUID());
                continue;
            }
            if (!Progression.profile(p).awakened()) continue;
            check(p, level);
        }
    }

    /** Looks at the player's region now; returns the message sent, or null when nothing changed. */
    public static LivingNet.RegionMsg check(ServerPlayer p, ServerLevel level) {
        Regions.Region r = Regions.regionAt(level, p.getBlockX(), p.getBlockZ());
        Long was = CURRENT.put(p.getUUID(), r.id());
        // out through the walls: the region's caption once a play-day (SPEC §27.6; the Commons spans the city's edge,
        // so leaving had no moment at all)
        boolean city = dev.awaken.terrain.api.CityBasin.reserved(p.getBlockX(), p.getBlockZ());
        Boolean wasCity = IN_CITY.put(p.getUUID(), city);
        if (Boolean.TRUE.equals(wasCity) && !city) {
            long day = PlayDays.current(level.getServer());
            Long last = WENT_OUT.put(p.getUUID(), day);
            if (last == null || last != day) {
                var out = new LivingNet.RegionMsg(r.name(), r.low(), r.high(), r.commons(), true, r.high() >= Progression.profile(p).level() + DANGER);
                dev.awaken.core.AwakenNet.send(p, out);
                return out;
            }
        }
        if (was != null && was == r.id()) return null;
        long day = PlayDays.current(level.getServer());
        Map<Long, Long> captioned = CAPTIONED.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
        Long last = captioned.get(r.id());
        boolean caption = last == null || last != day;
        if (caption) captioned.put(r.id(), day);
        int mine = Progression.profile(p).level();
        boolean danger = r.high() >= mine + DANGER;
        var msg = new LivingNet.RegionMsg(r.name(), r.low(), r.high(), r.commons(), caption, danger);
        dev.awaken.core.AwakenNet.send(p, msg);
        if (danger) {
            warn(p, r);
            dev.awaken.core.api.Promises.seen(p, "region:" + r.id(), p.blockPosition(), dev.awaken.core.api.Promises.Need.level(r.high() - DANGER + 1), r.name());
        }
        return msg;
    }

    private static void warn(ServerPlayer p, Regions.Region r) {
        long now = p.level().getGameTime();
        Long at = WARNED.get(p.getUUID());
        if (at != null && now - at < WARN_COOLDOWN) return;
        WARNED.put(p.getUUID(), now);
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.WARNING, Component.literal("Danger · " + r.name()))
                .body(Component.literal("Creatures here are level " + r.low() + " to " + r.high() + "."))
                .presentation(SignalWindow.Presentation.TOAST).tier(Notices.Tier.IMPORTANT).topic("danger").ttl(20 * 6).build());
    }

    public static void forget(UUID id) {
        CURRENT.remove(id);
        IN_CITY.remove(id);
        WENT_OUT.remove(id);
        CAPTIONED.remove(id);
        WARNED.remove(id);
    }

    public static void clear() {
        CURRENT.clear();
        CAPTIONED.clear();
        WARNED.clear();
    }
}
