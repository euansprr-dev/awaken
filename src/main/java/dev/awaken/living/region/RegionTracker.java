package dev.awaken.living.region;

import dev.awaken.core.PlayDays;
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
        if (danger) warn(p, r);
        return msg;
    }

    private static void warn(ServerPlayer p, Regions.Region r) {
        long now = p.level().getGameTime();
        Long at = WARNED.get(p.getUUID());
        if (at != null && now - at < WARN_COOLDOWN) return;
        WARNED.put(p.getUUID(), now);
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.WARNING, Component.literal("Danger · " + r.name()))
                .body(Component.literal("Creatures here are level " + r.low() + " to " + r.high() + "."))
                .presentation(SignalWindow.Presentation.TOAST).ttl(20 * 6).build());
    }

    public static void forget(UUID id) {
        CURRENT.remove(id);
        CAPTIONED.remove(id);
        WARNED.remove(id);
    }

    public static void clear() {
        CURRENT.clear();
        CAPTIONED.clear();
        WARNED.clear();
    }
}
