package dev.awaken.living.region;

import dev.awaken.core.PlayDays;
import dev.awaken.core.api.Notices;
import dev.awaken.living.LivingNet;
import dev.awaken.living.api.Regions;
import dev.awaken.progression.Curve;
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
 * minutes) when the land is 5+ levels above the player. Every pass also looks {@link #AHEAD} blocks along the player's heading
 * (SPEC §21.10): land 10 or more above them and rising gives one CRITICAL toast in plain words, once per land per five
 * minutes. Nothing is saved; a relog may caption again.
 */
public final class RegionTracker {
    private RegionTracker() {}

    static final int EVERY = 40;
    /** A land this many levels above you is a danger (the ember caption). */
    static final int DANGER = 5;
    /** How far ahead the look reaches, how far above you the land ahead must be to warn, and how much it must rise. */
    static final int AHEAD = 150, AHEAD_GAP = 10, AHEAD_RISE = 3;
    /** One warning per land per five minutes (it was one per player per ten, so a second danger land was silent). */
    static final long WARN_COOLDOWN = 20L * 60 * 5;

    private static final Map<UUID, Long> CURRENT = new HashMap<>();
    private static final Map<UUID, Map<Long, Long>> CAPTIONED = new HashMap<>();
    private static final Map<UUID, Map<Long, Long>> WARNED = new HashMap<>();
    /** Where each player stood at the last pass (their heading is the way they have been walking). */
    private static final Map<UUID, double[]> LAST = new HashMap<>();
    /** The land level last sent to each player's tint and sound bed, and tours' forced gaps ({@code /awaken living dread}). */
    private static final Map<UUID, Integer> LAND_SENT = new HashMap<>();
    private static final Map<UUID, Integer> FORCED_GAP = new HashMap<>();
    /** Whether each player was inside the city's walls at the last look, and the play-day of their last way out. */
    private static final Map<UUID, Boolean> IN_CITY = new HashMap<>();
    private static final Map<UUID, Long> WENT_OUT = new HashMap<>();

    public static void tick(MinecraftServer server) {
        int t = server.getTickCount();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if ((t + (p.getId() & 31)) % EVERY != 0) continue;
            if (!(p.level() instanceof ServerLevel level) || !Regions.on(level) || p.isSpectator()) {
                CURRENT.remove(p.getUUID());
                if (LAND_SENT.remove(p.getUUID()) != null) dev.awaken.core.AwakenNet.send(p, new LivingNet.LandMsg(-1)); // nothing weighs elsewhere
                continue;
            }
            if (!Progression.profile(p).awakened()) continue;
            check(p, level);
            lookAhead(p, level);
            sendLand(p, level);
            firstSteps(p, level);
        }
    }

    /** Looks at the player's region now; returns the message sent, or null when nothing changed. */
    public static LivingNet.RegionMsg check(ServerPlayer p, ServerLevel level) {
        Regions.Region r = Regions.regionAt(level, p.getBlockX(), p.getBlockZ());
        int here = Regions.levelAt(level, p.getBlockX(), p.getBlockZ());
        Long was = CURRENT.put(p.getUUID(), r.id());
        // out through the walls: the region's caption once a play-day (SPEC §27.6; the Commons spans the city's edge,
        // so leaving had no moment at all)
        boolean city = dev.awaken.terrain.api.CityBasin.reserved(p.getBlockX(), p.getBlockZ());
        // the first dusk outside the walls (the client shows it once, ever)
        long tod = Math.floorMod(level.getServer().overworld().getOverworldClockTime(), 24000L);
        if (!city && tod >= 12300 && tod < 13500 && dev.awaken.core.Throttle.allow(p, "tip_night", 24000)) dev.awaken.core.api.TipHooks.offer(p, dev.awaken.Awaken.id("first/night"));
        Boolean wasCity = IN_CITY.put(p.getUUID(), city);
        // inside the walls the city is the place: no region caption, no warning, nothing filed (Euan, 2026-10-26: the
        // Commons' "danger" and "waits for level 4" in the Journal of someone who hadn't left the city yet)
        if (city) return null;
        if (Boolean.TRUE.equals(wasCity)) {
            long day = PlayDays.current(level.getServer());
            Long last = WENT_OUT.put(p.getUUID(), day);
            if (last == null) dev.awaken.core.api.TipHooks.offer(p, dev.awaken.Awaken.id("first/atlas")); // the first way out: the Atlas (SPEC §27.7)
            CAPTIONED.computeIfAbsent(p.getUUID(), k -> new HashMap<>()).put(r.id(), day);
            // the first time out through the High Gate its shot names the land (FirstScenes): no second caption on top
            boolean named = last == null && Progression.profile(p).counter("awaken:first/gate_shot") > 0;
            if (!named && (last == null || last != day)) {
                var out = new LivingNet.RegionMsg(r.name(), r.low(), r.high(), r.commons(), true, danger(p, here));
                dev.awaken.core.AwakenNet.send(p, out);
                return out;
            }
            return null;
        }
        if (was != null && was == r.id()) return null;
        long day = PlayDays.current(level.getServer());
        Map<Long, Long> captioned = CAPTIONED.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
        Long last = captioned.get(r.id());
        boolean caption = last == null || last != day;
        if (caption) captioned.put(r.id(), day);
        boolean danger = danger(p, here);
        var msg = new LivingNet.RegionMsg(r.name(), r.low(), r.high(), r.commons(), caption, danger);
        dev.awaken.core.AwakenNet.send(p, msg);
        if (danger) {
            warn(p, level, r.id(), r.name(), here);
            if (!r.commons()) dev.awaken.core.api.Promises.seen(p, "region:" + r.id(), p.blockPosition(), dev.awaken.core.api.Promises.Need.level(here - DANGER + 1), r.name());
        }
        return msg;
    }

    /** The land under the player, to their client's tint and sound bed, when it changes (a tour's forced gap stands in for it). */
    static void sendLand(ServerPlayer p, ServerLevel level) {
        Integer forced = FORCED_GAP.get(p.getUUID());
        int land = forced != null ? Progression.profile(p).level() + forced : Regions.levelAt(level, p.getBlockX(), p.getBlockZ());
        Integer was = LAND_SENT.put(p.getUUID(), land);
        if (was == null || was != land) dev.awaken.core.AwakenNet.send(p, new LivingNet.LandMsg(land));
    }

    /**
     * The first step into a land of a rank above your own is a small find (SPEC §21.10, decision 10): a tenth of a level, once per
     * rank, kept through death, with a plain line. Going somewhere harder pays in finds, not in bigger XP.
     */
    static void firstSteps(ServerPlayer p, ServerLevel level) {
        int rank = dev.awaken.living.region.RegionMap.rankOf(Regions.levelAt(level, p.getBlockX(), p.getBlockZ()));
        if (rank == 0 || rank <= Progression.rank(p).ordinal()) return;
        if (!Progression.discover(p, "land_rank/" + rank, 0.1)) return;
        String name = "EDCBAS".substring(rank, rank + 1);
        Signal.toast(p, Component.literal("First steps in " + name + " land · creatures here are level "
                + dev.awaken.living.region.RegionMap.RANK_FROM[rank] + (rank + 1 < dev.awaken.living.region.RegionMap.RANK_FROM.length ? " to " + (dev.awaken.living.region.RegionMap.RANK_FROM[rank + 1] - 1) : " and up")),
                Notices.Tier.IMPORTANT, "land");
    }

    /** Tours: pretend the land is {@code gap} levels above the player (null: the real land). */
    public static void forceGap(ServerPlayer p, @org.jspecify.annotations.Nullable Integer gap) {
        if (gap == null) FORCED_GAP.remove(p.getUUID());
        else FORCED_GAP.put(p.getUUID(), gap);
        LAND_SENT.remove(p.getUUID());
    }

    /** The land under the player is 5 or more above them: a danger. (The Commons was exempt before; its wall is level 8.) */
    static boolean danger(ServerPlayer p, int land) {
        return land >= Progression.profile(p).level() + DANGER;
    }

    /**
     * Looks {@link #AHEAD} blocks along the heading (the way they have been walking, else the way they face): land 10 or more
     * above the player and rising warns once, early, before the first creature does. In the Commons a level-1 player at its
     * wall (level 8) is told the land rises. Never inside the city's reserve.
     */
    static void lookAhead(ServerPlayer p, ServerLevel level) {
        if (dev.awaken.terrain.api.CityBasin.reserved(p.getBlockX(), p.getBlockZ())) {
            LAST.remove(p.getUUID());
            return;
        }
        double[] last = LAST.put(p.getUUID(), new double[]{p.getX(), p.getZ()});
        double hx = p.getLookAngle().x, hz = p.getLookAngle().z;
        if (last != null) {
            double dx = p.getX() - last[0], dz = p.getZ() - last[1];
            if (dx * dx + dz * dz >= 36) { // 3 blocks a second or more: the way they are going
                hx = dx;
                hz = dz;
            }
        }
        double len = Math.hypot(hx, hz);
        if (len < 1e-3) return;
        int mine = Progression.profile(p).level();
        int here = Regions.levelAt(level, p.getBlockX(), p.getBlockZ());
        int ax = (int) Math.round(p.getX() + hx / len * AHEAD), az = (int) Math.round(p.getZ() + hz / len * AHEAD);
        int ahead = Regions.levelAt(level, ax, az);
        if (aheadWarns(mine, here, ahead)) {
            Regions.Region r = Regions.regionAt(level, ax, az);
            warn(p, level, r.id(), r.name(), ahead);
        } else if (here - mine >= DANGER) {
            Regions.Region r = Regions.regionAt(level, p.getBlockX(), p.getBlockZ());
            if (r.commons()) warn(p, level, r.id(), r.name(), here);
        }
    }

    /** Whether the land ahead is a warning: 10 or more above the player, and rising. */
    public static boolean aheadWarns(int mine, int here, int ahead) {
        return ahead - mine >= AHEAD_GAP && ahead >= here + AHEAD_RISE;
    }

    private static void warn(ServerPlayer p, ServerLevel level, long regionId, String name, int land) {
        long now = level.getGameTime();
        Map<Long, Long> seen = WARNED.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
        Long at = seen.get(regionId);
        if (at != null && now - at < WARN_COOLDOWN) return;
        if (seen.size() > 32) seen.clear();
        seen.put(regionId, now);
        int mine = Progression.profile(p).level();
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.WARNING, Component.literal("Danger · " + name))
                .body(Component.literal(body(name, land, mine)))
                .presentation(SignalWindow.Presentation.TOAST).tier(Notices.Tier.CRITICAL).topic("danger").ttl(20 * 8).build());
    }

    /** The warning's words (plain, with what the numbers mean against the player's own level). */
    public static String body(String name, int land, int mine) {
        double hp = Curve.mobHealth(land) / Curve.mobHealth(mine), hit = Curve.mobDamage(land) / Curve.mobDamage(mine);
        return name + " ahead. Creatures there are " + times(hp) + " as tough as ones at your level and hit " + times(hit)
                + " as hard. Turn back, or go in only to look.";
    }

    /** A ratio in words: "twice", "about three times", "nearly four times". */
    public static String times(double r) {
        if (r < 1.25) return "a little";
        if (r < 1.75) return "half again";
        if (r < 2.15) return "about twice";
        long n = Math.round(r);
        if (n >= 10) return "many times";
        String[] words = {"", "", "two", "three", "four", "five", "six", "seven", "eight", "nine"};
        String w = words[(int) n];
        return (r < n - 0.2 ? "nearly " : r > n + 0.2 ? "just over " : "about ") + w + " times";
    }

    public static void forget(UUID id) {
        CURRENT.remove(id);
        IN_CITY.remove(id);
        WENT_OUT.remove(id);
        CAPTIONED.remove(id);
        WARNED.remove(id);
        LAST.remove(id);
        LAND_SENT.remove(id);
        FORCED_GAP.remove(id);
    }

    public static void clear() {
        CURRENT.clear();
        CAPTIONED.clear();
        WARNED.clear();
        LAST.clear();
        LAND_SENT.clear();
        FORCED_GAP.clear();
    }
}
