package dev.awaken.living.pulse;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.api.GateHooks;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.living.LivingNet;
import dev.awaken.living.api.Regions;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Finds;
import dev.awaken.terrain.api.CityBasin;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The World Pulse (SPEC §21.6): one director for what happens in the open world, so the cadence is designed rather than
 * random. Around each player outdoors it keeps one happening with a tell in sight most of the time (at most three near),
 * prefers kinds they haven't witnessed, follows the moon (full: the Wild Hunt; new: the Deep Tide; half: caravans) and
 * the weather and time of day, stays quiet inside a Hearth's radius, the city and gates. Happenings are world state
 * (saved data {@code awaken:living_pulse}, schema 1), shared, and end on their own. Witnessing a kind the first time is a
 * discovery (the Almanac).
 */
public final class Pulse extends SavedData {
    static final int SCHEMA = 1;
    /** How far a happening counts as "near" a player, how far its tell is sent. */
    static final int NEAR = 256, TELL_RANGE = 512;
    /** A player's first happening after this long outdoors; then at least this long between two they're given. */
    static final long FIRST_AFTER = 20L * 120, GAP_EMPTY = 20L * 90, GAP_BUSY = 20L * 60 * 10;
    static final int MAX_NEAR = 3;

    public static final Codec<Pulse> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(p -> SCHEMA),
            Happening.CODEC.listOf().optionalFieldOf("happenings", List.of()).forGetter(p -> List.copyOf(p.happenings)),
            Codec.LONG.optionalFieldOf("next_id", 1L).forGetter(p -> p.nextId)
    ).apply(i, (schema, list, next) -> new Pulse(list, next)));

    public static final SavedDataType<Pulse> TYPE = new SavedDataType<>(Awaken.id("living_pulse"), Pulse::new, CODEC);

    final List<Happening> happenings;
    long nextId;
    /** Per player (not saved): when they were last given one, and the kind. */
    private final transient Map<UUID, Long> lastGiven = new HashMap<>();
    private final transient Map<UUID, String> lastKind = new HashMap<>();
    private final transient Map<UUID, Boolean> sentTells = new HashMap<>();

    public Pulse() {
        this(List.of(), 1L);
    }

    Pulse(List<Happening> list, long nextId) {
        this.happenings = new ArrayList<>(list);
        this.nextId = nextId;
    }

    public static Pulse get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public List<Happening> active() {
        return List.copyOf(happenings);
    }

    public static @Nullable Happening find(MinecraftServer server, long id) {
        for (Happening h : get(server).happenings) if (h.id == id) return h;
        return null;
    }

    // ---- the calendar -------------------------------------------------------------------------------------------------

    /** The moon's phase, 0 full .. 4 new .. 7 (vanilla's 8-day cycle). */
    public static int moon(ServerLevel level) {
        return (int) Math.floorMod(level.getOverworldClockTime() / 24000L, 8L);
    }

    /** dawn (23000-1500), day, dusk (11500-13500), night. */
    public static String timeOfDay(ServerLevel level) {
        long t = Math.floorMod(level.getOverworldClockTime(), 24000L);
        if (t >= 23000 || t < 1500) return "dawn";
        if (t < 11500) return "day";
        if (t < 13500) return "dusk";
        return "night";
    }

    static String weather(ServerLevel level) {
        return level.isThundering() ? "thunder" : level.isRaining() ? "rain" : "clear";
    }

    // ---- the tick ---------------------------------------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        int t = server.getTickCount();
        Pulse p = get(server);
        if (t % 20 == 0) p.run(level);
        if (t % 40 == 0) p.sendTells(level);
        // GameTests start happenings themselves: none comes to a test's player on its own, across other tests' plots
        if (server instanceof net.minecraft.gametest.framework.GameTestServer) return;
        for (ServerPlayer pl : server.getPlayerList().getPlayers()) {
            if ((t + (pl.getUUID().hashCode() & 0xFF)) % 200 == 0) p.consider(level, pl);
        }
    }

    /** Every second: each happening moves on, ends, or is witnessed. */
    void run(ServerLevel level) {
        long now = level.getGameTime();
        boolean dirty = false;
        for (int i = 0; i < happenings.size(); i++) {
            Happening h = happenings.get(i);
            HappeningDef def = HappeningBook.get(h.kind);
            Behaviour b = def == null ? null : Behaviours.get(def.behaviour());
            if (b == null || now >= h.end) {
                if (b != null) safely(() -> b.end(level, h, def));
                else dismiss(level, h);
                happenings.remove(i--);
                dirty = true;
                continue;
            }
            Vec3 before = h.centre;
            safely(() -> b.tick(level, h, def, now));
            if (!h.centre.equals(before)) h.changed = true;
            for (ServerPlayer pl : level.players()) {
                // seen from inside Carillon's walls it isn't witnessed yet (SPEC §26.3): stepping out still can
                if (h.witnessed.contains(pl.getUUID()) || pl.isSpectator() || !Finds.counts(pl)) continue;
                if (pl.position().distanceToSqr(h.centre) <= (double) def.witnessRadius() * def.witnessRadius()) {
                    h.witnessed.add(pl.getUUID());
                    witness(pl, h, def);
                    dirty = true;
                }
            }
        }
        if (dirty) setDirty();
    }

    private static void safely(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            Awaken.LOG.error("[living] a happening failed", e);
        }
    }

    /** The Almanac: the first time a player witnesses a kind, a discovery (0.1 of a level) and a line; always a count. */
    static void witness(ServerPlayer p, Happening h, HappeningDef def) {
        String path = net.minecraft.resources.Identifier.parse(h.kind).getPath();
        Progression.count(p, "awaken:happenings_witnessed");
        if (Progression.discover(p, "happening/" + path, 0.1)) {
            Progression.markNewThing(p, "happening/" + path);
            // a happening is big and in plain view: witnessing it is enough (SPEC §26.3)
            Finds.tell(p, Finds.Find.of("happening", "Witnessed · " + def.title(), def.title(), "", 0xFFCF7A, false));
        }
        Behaviour b = Behaviours.get(def.behaviour());
        if (b != null) b.witnessed(p, h, def);
        // two of the hundred bells are heard only in the wild's big moments (SPEC §43.13)
        dev.awaken.bells.Bells.witnessed(p, path);
    }

    /** For one player: a happening now, if the world around them is quiet and it's been long enough. */
    private void consider(ServerLevel level, ServerPlayer p) {
        if (p.level() != level || p.isSpectator() || !Progression.profile(p).awakened() || !Regions.on(level)) return;
        if (GateHooks.inGate(p) || HearthHooks.inside(level, p.blockPosition()) || CityBasin.reserved(p.getBlockX(), p.getBlockZ())) return;
        long now = level.getGameTime();
        Long last = lastGiven.get(p.getUUID());
        if (last == null) {
            lastGiven.put(p.getUUID(), now - GAP_EMPTY + FIRST_AFTER); // the first comes a couple of minutes in
            return;
        }
        int near = nearCount(level, p);
        if (near >= MAX_NEAR) return;
        if (now - last < (near == 0 ? GAP_EMPTY : GAP_BUSY)) return;
        Happening h = startFor(level, p, null);
        lastGiven.put(p.getUUID(), now - (h == null ? GAP_EMPTY - 20L * 30 : 0)); // nothing fit: look again in 30 s
    }

    /** Happenings (and the anomalies, which share the land) near a player. */
    int nearCount(ServerLevel level, ServerPlayer p) {
        int n = 0;
        for (Happening h : happenings) if (h.centre.distanceToSqr(p.position()) < (double) NEAR * NEAR) n++;
        try {
            for (var a : dev.awaken.world.anomaly.AnomalyData.get(level.getServer()).active()) {
                if (Vec3.atCenterOf(a.pos).distanceToSqr(p.position()) < (double) NEAR * NEAR) n++;
            }
        } catch (RuntimeException ignored) {
            // anomalies off or not loaded: they don't count
        }
        return n;
    }

    /**
     * Starts a happening for this player: {@code only} a kind id, or null to choose (weights × the moon × twice for a
     * kind they haven't witnessed, never the same as their last). Null when nothing fits here and now.
     */
    @Nullable Happening startFor(ServerLevel level, ServerPlayer p, @Nullable String only) {
        RandomSource r = level.getRandom();
        List<Map.Entry<String, HappeningDef>> pool = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        String time = timeOfDay(level), weather = weather(level);
        int moon = moon(level);
        int band = Regions.bandOf(Regions.levelAt(level, p.getBlockX(), p.getBlockZ()));
        for (var e : HappeningBook.all().entrySet()) {
            HappeningDef d = e.getValue();
            if (only != null && !e.getKey().equals(only) && !e.getKey().endsWith(":" + only)) continue;
            if (only == null && !fits(d, time, weather, moon, band)) continue;
            if (only == null && e.getKey().equals(lastKind.get(p.getUUID()))) continue;
            double w = d.weight();
            if (!d.when().moon().isEmpty() && d.when().moon().contains(moon)) w *= d.when().moonWeight();
            String path = net.minecraft.resources.Identifier.parse(e.getKey()).getPath();
            if (Progression.profile(p).counter("awaken:discovered/happening/" + path) == 0) w *= 2;
            pool.add(e);
            weights.add(w);
        }
        for (int tries = 0; tries < 3 && !pool.isEmpty(); tries++) {
            double total = 0;
            for (double w : weights) total += w;
            double roll = r.nextDouble() * total;
            int pick = 0;
            while (pick < weights.size() - 1 && (roll -= weights.get(pick)) > 0) pick++;
            var e = pool.get(pick);
            Behaviour b = Behaviours.get(e.getValue().behaviour());
            Behaviour.Plan plan = b == null ? null : b.plan(level, p, e.getValue(), r);
            if (plan != null) return open(level, p, e.getKey(), e.getValue(), b, plan);
            pool.remove(pick);
            weights.remove(pick);
        }
        return null;
    }

    static boolean fits(HappeningDef d, String time, String weather, int moon, int band) {
        HappeningDef.When w = d.when();
        if (!w.time().isEmpty() && !w.time().contains(time)) return false;
        if (w.moonOnly() && !w.moon().contains(moon)) return false;
        if (!w.weather().equals("any") && !(w.weather().equals(weather) || w.weather().equals("rain") && weather.equals("thunder"))) return false;
        return d.bands().contains(band);
    }

    private Happening open(ServerLevel level, ServerPlayer p, String kind, HappeningDef def, Behaviour b, Behaviour.Plan plan) {
        long now = level.getGameTime();
        Happening h = new Happening(nextId++, kind, Vec3.atBottomCenterOf(plan.from()), plan.from(), plan.to(), now,
                now + def.durationS() * 20L, level.getRandom().nextLong(), 0, List.of(), List.of(), Map.of());
        happenings.add(h);
        setDirty();
        lastKind.put(p.getUUID(), kind);
        safely(() -> b.start(level, h, def));
        DirectorLog.log(p, "pulse", def.title() + " at " + plan.from().toShortString());
        Awaken.LOG.info("[living] happening {} {} for {} at {}", h.id, kind, p.getName().getString(), plan.from().toShortString());
        return h;
    }

    /** Tests: a happening of this kind on exactly this line, now. */
    @Nullable Happening openAt(ServerLevel level, String kind, BlockPos from, BlockPos to) {
        HappeningDef def = HappeningBook.get(kind);
        Behaviour b = def == null ? null : Behaviours.get(def.behaviour());
        if (b == null) return null;
        long now = level.getGameTime();
        Happening h = new Happening(nextId++, HappeningBook.all().containsKey(kind) ? kind : Awaken.MODID + ":" + kind, Vec3.atBottomCenterOf(from),
                from, to, now, now + def.durationS() * 20L, level.getRandom().nextLong(), 0, List.of(), List.of(), Map.of());
        happenings.add(h);
        setDirty();
        safely(() -> b.start(level, h, def));
        return h;
    }

    /** Dev and tests: starts this kind now for the player (ignoring when it may run). */
    public static @Nullable Happening force(ServerPlayer p, String kind) {
        ServerLevel level = p.level().getServer().overworld();
        if (p.level() != level) return null;
        return get(level.getServer()).startFor(level, p, kind);
    }

    /** Dev and tests: every happening ends now. */
    public static int endAll(MinecraftServer server) {
        Pulse p = get(server);
        ServerLevel level = server.overworld();
        int n = p.happenings.size();
        for (Happening h : List.copyOf(p.happenings)) {
            HappeningDef def = HappeningBook.get(h.kind);
            Behaviour b = def == null ? null : Behaviours.get(def.behaviour());
            if (b != null) safely(() -> b.end(level, h, def));
            else dismiss(level, h);
        }
        p.happenings.clear();
        p.setDirty();
        return n;
    }

    /** Its creatures go with it: those still listed are removed if loaded (unloaded ones go when their chunk loads). */
    public static void dismiss(ServerLevel level, Happening h) {
        for (UUID id : h.participants) {
            Entity e = level.getEntity(id);
            if (e != null && e.isAlive()) e.discard();
        }
        h.participants.clear();
    }

    /** The perk source for what witnessing gives (a murmuration's lucid night). */
    public static double lucidity(net.minecraft.world.entity.player.Player p, String perk) {
        return Skies.lucidity(p, perk);
    }

    /** Loot from a happening's creatures (the Wild Hunt's essence). */
    public static void onDrops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent e) {
        Hunts.onDrops(e);
    }

    /** The Deep Tide is up: the night's hunters and camps run harder (Regions adds it). */
    public static int tideExtra(ServerLevel level) {
        return Tides.rising(level) ? Tides.EXTRA : 0;
    }

    /** Whether an entity belongs to a happening (its tag), e.g. for loot or cleanup. */
    public static boolean participant(Entity e) {
        return e.entityTags().contains(TAG);
    }

    public static final String TAG = "awaken_happening";

    // ---- tells ----------------------------------------------------------------------------------------------------

    /** Every 2 s: each player gets the tells within 512 blocks (an empty list once, when the last one went). */
    private void sendTells(ServerLevel level) {
        long now = level.getGameTime();
        for (ServerPlayer pl : level.players()) {
            List<LivingNet.HappeningTell> out = new ArrayList<>(4);
            for (Happening h : happenings) {
                if (h.centre.distanceToSqr(pl.position()) > (double) TELL_RANGE * TELL_RANGE || out.size() >= 12) continue;
                HappeningDef def = HappeningBook.get(h.kind);
                if (def == null) continue;
                out.add(new LivingNet.HappeningTell((int) h.id, def.tell(), (float) h.centre.x, (float) h.centre.y, (float) h.centre.z,
                        def.tellRadius(), h.progress(now), h.to.getX() - h.from.getX(), h.to.getZ() - h.from.getZ()));
            }
            boolean had = sentTells.getOrDefault(pl.getUUID(), false);
            if (out.isEmpty() && !had) continue;
            AwakenNet.send(pl, new LivingNet.Happenings(out));
            sentTells.put(pl.getUUID(), !out.isEmpty());
        }
    }

    /** Rumours: the happenings on now that a player hasn't witnessed, far enough off to be news. */
    public static List<Happening> news(ServerPlayer p, int min, int max) {
        List<Happening> out = new ArrayList<>();
        for (Happening h : get(p.level().getServer()).happenings) {
            if (h.witnessed.contains(p.getUUID())) continue;
            double d2 = h.centre.distanceToSqr(p.position());
            if (d2 >= (double) min * min && d2 <= (double) max * max) out.add(h);
        }
        return out;
    }

    static BlockPos at(Vec3 v) {
        return BlockPos.containing(v);
    }
}
