package dev.awaken.lucidium.dream;

import dev.awaken.Awaken;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.PlayDays;
import dev.awaken.lucidium.LucidiumContent;
import dev.awaken.lucidium.data.Dreamer;
import dev.awaken.lucidium.data.Snapshot;
import dev.awaken.lucidium.data.Vision;
import dev.awaken.lucidium.journal.SnapshotFiles;
import dev.awaken.lucidium.plan.DreamPlan;
import dev.awaken.lucidium.plan.DreamPlanner;
import dev.awaken.lucidium.plan.Fragment;
import dev.awaken.lucidium.plan.Nightmares;
import dev.awaken.lucidium.world.BlueprintCompiler;
import dev.awaken.lucidium.world.Cells;
import dev.awaken.lucidium.world.DreamBlueprint;
import dev.awaken.lucidium.world.DreamChunkGenerator;
import dev.awaken.progression.Progression;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Prepares tonight's dream ahead of time (SPEC: "a dream plan is built at dusk"). The plan itself is cheap and built
 * on the server thread; reading the remembered places from disk and compiling the blueprint run in the background,
 * and the result is swapped in on the server thread. A plan for a night not slept is thrown away at morning.
 */
public final class Planner {
    private Planner() {}

    /** A dream ready to enter: the plan and its compiled cell. */
    public record Prepared(DreamPlan plan, DreamBlueprint blueprint, long day) {}

    private static final Map<UUID, Prepared> READY = new HashMap<>();
    private static final Map<UUID, Integer> BUILDING = new HashMap<>();
    /** Players with nothing real to dream of yet (empty journal) for this day. */
    private static final Map<UUID, Long> NOTHING = new HashMap<>();
    private static int generation;

    public static Optional<Prepared> ready(ServerPlayer player) {
        Prepared p = READY.get(player.getUUID());
        if (p == null) return Optional.empty();
        if (p.day() != PlayDays.current(player.level().getServer())) {
            READY.remove(player.getUUID());
            drop(player.level().getServer(), p);
            return Optional.empty();
        }
        return Optional.of(p);
    }

    public static boolean building(ServerPlayer player) {
        return BUILDING.containsKey(player.getUUID());
    }

    /** Takes the prepared dream (it is used once). */
    public static Optional<Prepared> take(ServerPlayer player) {
        Optional<Prepared> p = ready(player);
        p.ifPresent(x -> READY.remove(player.getUUID()));
        return p;
    }

    public static void invalidate(ServerPlayer player) {
        drop(player.level().getServer(), READY.remove(player.getUUID()));
        BUILDING.remove(player.getUUID()); // a build still running gives its cell back when it completes
        NOTHING.remove(player.getUUID());
    }

    /**
     * A plan the planner lets go of gives its cell back (SPEC "Together": cells are reused), unless a sleeper is about
     * to dream it: then their night gives it back if they never do.
     */
    private static void drop(MinecraftServer server, Prepared p) {
        if (p != null && !Sleep.adopt(p)) discard(server, p);
    }

    /** A plan nobody will dream (dropped, or its taker couldn't enter it): its cell is erased and reused. */
    public static void discard(MinecraftServer server, Prepared p) {
        int cell = p.blueprint().cell;
        if (DreamChunkGenerator.blueprint(cell) == p.blueprint()) DreamChunkGenerator.unregister(cell);
        Cells.release(server, cell);
    }

    public static void clearAll() {
        READY.clear();
        BUILDING.clear();
        NOTHING.clear();
    }

    /** Builds the inputs on the server thread. */
    public static DreamPlanner.Input input(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        long day = PlayDays.current(server);
        Dreamer d = player.getData(LucidiumContent.DREAMER);
        var profile = Progression.profile(player);
        Map<Nightmares.Family, Integer> fears = Nightmares.scores(Map.copyOf(profile.counters()), d.weeklyMarks(day));
        Optional<Vision> vision = d.pendingVision();
        return new DreamPlanner.Input(server.overworld().getSeed(), player.getUUID(), day, d.dreams, List.copyOf(d.snapshots),
                fears, d.intention(day), vision, profile.path());
    }

    /** At most this many background dream builds per player per play-day hour (1000 ticks), whatever asks for them. */
    static final int BUILDS_PER_WINDOW = 3;

    /** Starts preparing tonight's dream if it isn't ready or being built. */
    public static void request(ServerPlayer player, String why) {
        UUID id = player.getUUID();
        MinecraftServer server = player.level().getServer();
        long day = PlayDays.current(server);
        if (ready(player).isPresent() || BUILDING.containsKey(id)) return;
        Long nothing = NOTHING.get(id);
        if (nothing != null && nothing == day) return;
        DreamPlanner.Input in = input(player);
        Optional<DreamPlan> plan = DreamPlanner.plan(in);
        if (plan.isEmpty()) {
            NOTHING.put(id, day);
            return;
        }
        // each build compiles a dream in the background and takes a cell: changing one's mind can't make a server busy
        if (!dev.awaken.core.Throttle.allowBurst(player, "dream_build", BUILDS_PER_WINDOW, 1000)) return;
        int gen = ++generation;
        BUILDING.put(id, gen);
        int cell = Cells.allocate(server);
        DreamPlan p = plan.get();
        Function<String, Optional<Fragment>> loader = memoryLoader(server, id);
        CompletableFuture.supplyAsync(() -> BlueprintCompiler.compile(p, cell, loader), Util.backgroundExecutor())
                .whenCompleteAsync((bp, err) -> {
                    Integer current = BUILDING.get(id);
                    if (current == null || current != gen) {
                        Cells.release(server, cell); // invalidated meanwhile: nobody will dream it
                        return;
                    }
                    BUILDING.remove(id);
                    if (err != null || bp == null) {
                        Awaken.LOG.warn("Lucidium: dream plan for {} failed to build", id, err);
                        Cells.release(server, cell);
                        return;
                    }
                    READY.put(id, new Prepared(p, bp, day));
                    ServerPlayer online = server.getPlayerList().getPlayer(id);
                    if (online != null) DirectorLog.log(online, "dream", "planned (" + why + "): " + p.summary());
                }, server);
    }

    /** Tests: tonight's plan for this player, compiled now on this thread and ready as if built at dusk. */
    static Prepared prepareNow(ServerPlayer player) {
        DreamPlan plan = DreamPlanner.plan(input(player)).orElseThrow();
        Prepared p = buildNow(player.level().getServer(), player.getUUID(), plan);
        READY.put(player.getUUID(), p);
        return p;
    }

    /** Compiles a plan right now on this thread (admin commands, the Dream Recorder, tests). */
    public static Prepared buildNow(MinecraftServer server, UUID player, DreamPlan plan) {
        int cell = Cells.allocate(server);
        DreamBlueprint bp = BlueprintCompiler.compile(plan, cell, memoryLoader(server, player));
        return new Prepared(plan, bp, PlayDays.current(server));
    }

    static Function<String, Optional<Fragment>> memoryLoader(MinecraftServer server, UUID player) {
        Path folder = SnapshotFiles.folder(server, player);
        return id -> SnapshotFiles.load(folder.resolve(id + ".nbt"));
    }

    /** Morning: every unused plan is thrown away (builds still running give their cells back when they complete). */
    public static void morning(MinecraftServer server) {
        for (Prepared p : READY.values()) drop(server, p);
        READY.clear();
        BUILDING.clear();
        NOTHING.clear();
    }

    /** Called when the journal gains a snapshot: an empty-journal player can now dream. */
    public static void journalChanged(ServerPlayer player, Snapshot s) {
        NOTHING.remove(player.getUUID());
    }
}
