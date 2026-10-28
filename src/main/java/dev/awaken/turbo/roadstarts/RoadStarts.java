package dev.awaken.turbo.roadstarts;

import dev.awaken.Awaken;
import dev.awaken.village.road.RoadNet;
import dev.awaken.village.road.RoadPlacement;
import net.minecraft.SharedConstants;
import net.minecraft.server.level.ChunkTaskDispatcher;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Road starts (SPEC §28.9, Cost; Turbo): 26.3 runs a chunk's structure starts inline on its level's one "worldgen"
 * executor, so an overworld lattice chunk whose roads weren't planned yet held all of the level's chunk generation while
 * it waited for them, and the server thread with it whenever it needed any chunk meanwhile (Euan's 42 s trial freeze
 * waited behind one). With this on, such a chunk's structure starts hand back a future instead: an urgent road planner
 * makes its legs, then the step runs on the executor exactly as it would have (the legs known, it answers at once), and
 * the executor generates every other chunk meanwhile. Chunks that need the waiting one still wait for it.
 *
 * <p>A Turbo feature: the hook is a common mixin at the head of {@code ChunkStatusTasks.generateStructureStarts}
 * (dev.awaken.turbo.roadstarts.mixin, its own config, not required) with an access transformer for ChunkMap's worldgen
 * dispatcher. It sets a chunk aside only for the game and NeoForge versions it was read against, once its probe has seen
 * it run, and with the server setting {@code roadStarts} on ({@code config/awaken-roadstarts-server.toml}); otherwise, and
 * from the first thing that goes wrong, every chunk's structure starts run as vanilla's own.
 */
public final class RoadStarts {
    private RoadStarts() {}

    /** The versions the hook was read against and tested on. Any other: off, with one log line. */
    static final String MINECRAFT = "26.3", NEOFORGE = "26.3.0.95";

    /** Set the first time the hook runs: the probe. */
    private static volatile boolean applied;
    private static volatile boolean wanted = true, versionOk, failed, told;
    /** The step being taken up again on the executor (the hook steps aside for it). */
    private static final ThreadLocal<Boolean> RERUN = ThreadLocal.withInitial(() -> false);
    /** Lattice chunks set aside and taken up again (the test, the log). */
    static final AtomicLong SET_ASIDE = new AtomicLong(), TAKEN_UP = new AtomicLong();
    /** Whether a level's generator places the roads (cached by its structure state). */
    private static volatile @Nullable ChunkGeneratorStructureState roadState, plainState;

    public static void init(IEventBus modBus) {
        RoadStartsConfig.register();
        modBus.addListener(ModConfigEvent.Loading.class, e -> refresh());
        modBus.addListener(ModConfigEvent.Reloading.class, e -> refresh());
        NeoForge.EVENT_BUS.addListener(ServerAboutToStartEvent.class, e -> refresh());
    }

    private static void refresh() {
        try {
            wanted = RoadStartsConfig.on();
            versionOk = versionsMatch();
        } catch (RuntimeException | LinkageError e) {
            fail(e);
        }
    }

    /** Whether the hook ran on this game's chunk generation (the mixin applied). */
    public static boolean applied() {
        return applied;
    }

    /** Whether lattice chunks are set aside now. */
    public static boolean active() {
        return applied && wanted && versionOk && !failed;
    }

    /** Lattice chunks set aside so far (the test). */
    public static long setAside() {
        return SET_ASIDE.get();
    }

    /** One line for the log and the tests. */
    public static String status() {
        return (active() ? "on" : "off") + (applied ? "" : ", the hook never ran") + (wanted ? "" : ", switched off")
                + (versionOk ? "" : ", other versions") + (failed ? ", failed" : "") + "; " + SET_ASIDE.get() + " lattice chunks set aside, "
                + TAKEN_UP.get() + " taken up";
    }

    /**
     * The hook (from the mixin, on the worldgen executor): a future for this chunk's structure starts when they'd wait for
     * the roads, else null (they run now, as vanilla's).
     */
    public static @Nullable CompletableFuture<ChunkAccess> later(WorldGenContext ctx, ChunkStep step, StaticCache2D<GenerationChunkHolder> chunks,
                                                               ChunkAccess chunk) {
        applied = true;
        if (!active() || RERUN.get()) return null;
        try {
            ServerLevel level = ctx.level();
            if (level.dimension() != Level.OVERWORLD || !level.getServer().getWorldGenSettings().options().generateStructures()
                    || !roads(level.getChunkSource().getGeneratorState())) return null;
            ChunkPos pos = chunk.getPos();
            GenerationChunkHolder holder = chunks.get(pos.x(), pos.z());
            ChunkTaskDispatcher worldgen = level.getChunkSource().chunkMap.worldgenTaskDispatcher;
            long key = pos.pack();
            return later(pos.x(), pos.z(), task -> worldgen.submit(task, key, holder::getQueueLevel),
                    () -> ChunkStatusTasks.generateStructureStarts(ctx, step, chunks, chunk));
        } catch (RuntimeException | LinkageError e) {
            fail(e);
            return null;
        }
    }

    /**
     * The handoff (the test drives it directly): null when the lattice chunk's legs needn't be waited for; else a future
     * that, once a planner has made them, runs {@code step} on {@code worldgen} (where it would have run) and completes
     * with what it gives.
     */
    public static <T> @Nullable CompletableFuture<T> later(int chunkX, int chunkZ, Executor worldgen, Supplier<CompletableFuture<T>> step) {
        CompletableFuture<?> legs = RoadNet.legsLater(chunkX, chunkZ);
        if (legs == null) return null;
        SET_ASIDE.incrementAndGet();
        if (!told) {
            told = true;
            Awaken.LOG.info("[roadstarts] on: lattice chunk {},{} waits for its roads off the worldgen executor", chunkX, chunkZ);
        }
        CompletableFuture<T> out = new CompletableFuture<>();
        legs.whenComplete((v, e) -> {
            // a planner that failed leaves the legs unmade: the step lays them itself, as it would have
            if (e != null) fail(e);
            try {
                worldgen.execute(() -> takeUp(step, out));
            } catch (RuntimeException x) {
                fail(x);
                out.completeExceptionally(x);
            }
        });
        return out;
    }

    private static <T> void takeUp(Supplier<CompletableFuture<T>> step, CompletableFuture<T> out) {
        RERUN.set(true);
        try {
            step.get().whenComplete((c, e) -> {
                if (e != null) out.completeExceptionally(e);
                else out.complete(c);
            });
            TAKEN_UP.incrementAndGet();
        } catch (RuntimeException | Error e) {
            fail(e);
            out.completeExceptionally(e);
        } finally {
            RERUN.set(false);
        }
    }

    private static boolean roads(ChunkGeneratorStructureState state) {
        if (state == roadState) return true;
        if (state == plainState) return false;
        boolean has = state.possibleStructureSets().stream().anyMatch(s -> s.value().placement() instanceof RoadPlacement);
        if (has) roadState = state;
        else plainState = state;
        return has;
    }

    private static void fail(Throwable e) {
        if (failed) return;
        failed = true;
        Awaken.LOG.warn("[roadstarts] off: the hook failed; structure starts run inline from now on", e);
    }

    private static boolean versionsMatch() {
        String mc = SharedConstants.getCurrentVersion().name();
        String neo = ModList.get().getModContainerById("neoforge").map(c -> c.getModInfo().getVersion().toString()).orElse("-");
        if (MINECRAFT.equals(mc) && NEOFORGE.equals(neo)) return true;
        Awaken.LOG.warn("[roadstarts] off: verified on Minecraft {}, NeoForge {}; this is {}, {}", MINECRAFT, NEOFORGE, mc, neo);
        return false;
    }
}
