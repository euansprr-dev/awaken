package dev.awaken.gates.worlds;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenConfig;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.content.FloorWorld;
import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.world.Hash;
import dev.awaken.gates.world.Plots;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Every world recipe in the loaded data packs ({@code data/<ns>/gate_worlds/<theme>/<world>.json}, SPEC §38) and the
 * world director: when a gate is made it picks a world and mood for each floor, leaning hard toward what the gate's
 * owner hasn't seen ({@link GateAtlas}). Read from worldgen threads (a volatile, immutable map).
 */
public final class GateWorlds {
    private GateWorlds() {}

    private static volatile Map<String, GateWorld> defs = Map.of();

    public static Map<String, GateWorld> all() {
        return defs;
    }

    public static GateWorld get(String id) {
        return defs.get(id);
    }

    /** A theme's worlds in id order. */
    public static List<String> ofTheme(Theme t) {
        List<String> out = new ArrayList<>();
        for (var e : defs.entrySet()) if (e.getValue().theme() == t) out.add(e.getKey());
        return out;
    }

    /** Replaces the catalogue (the reload, and tests). */
    public static void replace(Map<String, GateWorld> next) {
        defs = Collections.unmodifiableMap(new TreeMap<>(next));
    }

    public static void addReload(AddServerReloadListenersEvent e) {
        e.addListener(Awaken.id("gate_worlds"), new Loader());
    }

    static final class Loader extends SimpleJsonResourceReloadListener<GateWorld> {
        Loader() {
            super(GateWorld.CODEC, FileToIdConverter.json("gate_worlds"));
        }

        @Override
        protected void apply(Map<Identifier, GateWorld> loaded, ResourceManager manager, ProfilerFiller profiler) {
            Map<String, GateWorld> ok = new TreeMap<>();
            loaded.forEach((k, v) -> {
                String why = v.problem();
                if (!why.isEmpty()) Awaken.LOG.warn("[gates] world {} skipped: {}", k, why);
                else ok.put(k.toString(), v);
            });
            replace(ok);
            Awaken.LOG.info("AWAKEN loaded {} gate worlds", ok.size());
        }
    }

    // ---- the world director -------------------------------------------------------------------------------------------

    /** As {@link #dress(GatePlan, GateAtlas, Set)} with the server's switches and the owner's Atlas (online or not, empty). */
    public static GatePlan dress(MinecraftServer server, GatePlan plan, Optional<UUID> owner) {
        if (!AwakenConfig.GATE_WORLDS.get()) return plan;
        GateAtlas atlas = new GateAtlas();
        if (owner.isPresent()) {
            ServerPlayer p = server.getPlayerList().getPlayer(owner.get());
            if (p != null) atlas = p.getData(GatesRegistry.ATLAS.get());
        }
        return dress(plan, atlas, Set.copyOf(AwakenConfig.GATE_WORLDS_OFF.get()));
    }

    /**
     * Picks a world and mood for every floor of a new plan (pure: same plan, Atlas and catalogue, same picks). Never the
     * same world twice in a gate while another fits, never back to back; worlds and moods you've met less weigh more
     * (1 / (1 + times)^2, the model in SPEC §38 "Freshness"). A floor nothing fits stays today's rooms. Plans before
     * version 6, the story's ritual and the Ascension trials are never dressed.
     */
    public static GatePlan dress(GatePlan plan, GateAtlas atlas, Set<String> off) {
        if (plan.contentVersion() < Plots.FIRST_VERSION || plan.ritual() || plan.leaperTrial() || plan.skybornTrial()) return plan;
        if (!plan.worlds().isEmpty() || defs.isEmpty()) return plan;
        SplittableRandom rng = new SplittableRandom(Hash.of(plan.seed(), 0x6A7E, 38));
        List<FloorWorld> out = new ArrayList<>();
        Set<String> used = new HashSet<>();
        String prev = atlas.lastWorld();
        List<int[]> floors = new ArrayList<>();
        for (int f = 1; f <= plan.floors(); f++) floors.add(new int[]{0, f, f == plan.floors() ? 1 : 0});
        if (plan.hasAnnex()) {
            floors.add(new int[]{1, 1, 0});
            // the annex's second floor is always a Lord's (the inner gate's keeper, or the Double Gate's echo)
            floors.add(new int[]{1, 2, 1});
        }
        for (int[] af : floors) {
            Theme theme = af[0] == 0 ? plan.theme() : plan.annexTheme();
            boolean boss = af[2] == 1;
            String id = pickWorld(plan, theme, boss, atlas, off, used, prev, rng);
            if (id == null) continue;
            GateWorld w = defs.get(id);
            out.add(new FloorWorld(af[0], af[1], id, pickMood(id, w, atlas, rng), w.tall()));
            used.add(id);
            prev = id;
        }
        return out.isEmpty() ? plan : plan.withWorlds(out);
    }

    /** Can the director pick it for this floor at all (enabled, its theme, its role, open to you)? */
    static boolean open(GateWorld w, String id, GatePlan plan, Theme theme, boolean boss, GateAtlas atlas, Set<String> off) {
        if (!w.enabled() || off.contains(id) || w.tier() == GateWorld.Tier.SECRET) return false;
        if (boss ? !w.boss() : !w.ordinary()) return false;
        if (w.tier() == GateWorld.Tier.RARE) return plan.rank().ordinal() >= w.rankFrom();
        if (w.theme() != theme) return false;
        int mastery = w.masteryFloors();
        return plan.rank().ordinal() >= w.rankFrom() || (mastery > 0 && atlas.themeFloors(theme.id) >= mastery);
    }

    static String pickWorld(GatePlan plan, Theme theme, boolean boss, GateAtlas atlas, Set<String> off, Set<String> used, String prev,
                            SplittableRandom rng) {
        List<String> open = new ArrayList<>(), rare = new ArrayList<>();
        for (var e : defs.entrySet()) {
            if (!open(e.getValue(), e.getKey(), plan, theme, boss, atlas, off)) continue;
            (e.getValue().tier() == GateWorld.Tier.RARE ? rare : open).add(e.getKey());
        }
        double roll = rng.nextDouble();
        // a rare world turns up on about one floor in 25 once the gate's rank allows one
        List<String> pool = !rare.isEmpty() && roll < 0.04 ? rare : open;
        if (pool.isEmpty()) return null;
        List<String> fresh = new ArrayList<>();
        for (String id : pool) if (!used.contains(id) && !id.equals(prev)) fresh.add(id);
        if (fresh.isEmpty()) for (String id : pool) if (!id.equals(prev)) fresh.add(id);
        if (fresh.isEmpty()) fresh.addAll(pool);
        double[] wt = new double[fresh.size()];
        double tot = 0;
        for (int i = 0; i < wt.length; i++) {
            GateWorld w = defs.get(fresh.get(i));
            double seen = atlas.visits(fresh.get(i));
            wt[i] = w.weight() * w.rarity().weight / ((1 + seen) * (1 + seen));
            tot += wt[i];
        }
        return fresh.get(pick(wt, tot, rng.nextDouble()));
    }

    static String pickMood(String id, GateWorld w, GateAtlas atlas, SplittableRandom rng) {
        double r = rng.nextDouble();
        if (w.moods().isEmpty()) return "";
        double[] wt = new double[w.moods().size()];
        double tot = 0;
        for (int i = 0; i < wt.length; i++) {
            GateWorld.Mood m = w.moods().get(i);
            double seen = atlas.moodVisits(id, m.id());
            wt[i] = m.weight() / ((1 + seen) * (1 + seen));
            tot += wt[i];
        }
        return w.moods().get(pick(wt, tot, r)).id();
    }

    private static int pick(double[] wt, double tot, double r) {
        double x = r * tot;
        for (int i = 0; i < wt.length; i++) {
            x -= wt[i];
            if (x <= 0) return i;
        }
        return wt.length - 1;
    }
}
