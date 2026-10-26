package dev.awaken.farsight;

import dev.awaken.city.City;
import dev.awaken.city.CityLayout;
import dev.awaken.compat.FarView;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Feeds the generated layer (SPEC §20): every chunk that loads, and every chunk saved after a change (that is how
 * edits come back), is summed into a {@link FarChunk} and kept in the level's {@link FarStore}. Events only note the
 * chunk; the summing happens on the server tick within {@link #BUDGET_NANOS} (the chunk is read, nothing else), so a
 * burst of loads spreads over ticks. Runs only while something draws far terrain ({@link FarStore#collecting}); the
 * chunks loaded before that (the city's pregeneration holds ~1,700 through the join, never saving them) are kept
 * track of all along and summed when it starts. City chunks it has never seen are laid out from the city plan
 * ({@link FarCity#planned}) after the real ones, until the real ones replace them.
 */
public final class FarCollector {
    private FarCollector() {}

    static final long BUDGET_NANOS = 1_500_000L;
    /** City Sight's share of the tick: snapshots of its chunks (VoxelCapture). */
    static final long VOXEL_BUDGET_NANOS = 1_500_000L;

    private static final Map<ServerLevel, LongLinkedOpenHashSet> PENDING = new HashMap<>();
    /** Every chunk loaded in each far-terrain level, whether or not anything collects yet. Server thread. */
    private static final Map<ServerLevel, LongOpenHashSet> LOADED = new HashMap<>();
    /** City chunks still to check against the store and, if never seen, lay out from the plan. */
    private static final LongArrayFIFOQueue PLANNED = new LongArrayFIFOQueue();
    private static boolean wasCollecting;

    static void init() {
        NeoForge.EVENT_BUS.addListener(ChunkEvent.Load.class, e -> {
            if (!(e.getLevel() instanceof ServerLevel level) || !wanted(level)) return;
            LOADED.computeIfAbsent(level, k -> new LongOpenHashSet()).add(e.getChunk().getPos().pack());
            if (FarStore.collecting) note(level, e.getChunk().getPos());
        });
        NeoForge.EVENT_BUS.addListener(ChunkEvent.Unload.class, e -> {
            if (e.getLevel() instanceof ServerLevel level) {
                LongOpenHashSet loaded = LOADED.get(level);
                if (loaded != null) loaded.remove(e.getChunk().getPos().pack());
            }
        });
        NeoForge.EVENT_BUS.addListener(ChunkDataEvent.Save.class, e -> {
            if (FarStore.collecting && e.getLevel() instanceof ServerLevel level && e.getChunk() instanceof LevelChunk && wanted(level)) {
                note(level, e.getChunk().getPos());
            }
        });
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> tick(e.getServer()));
        // City Sight takes a chunk again after its blocks change
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.level.BlockEvent.NeighborNotifyEvent.class, e -> {
            if (e.getLevel() instanceof ServerLevel level && level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                dev.awaken.farsight.voxel.VoxelCapture.blockChanged(level, e.getPos().getX(), e.getPos().getZ());
            }
        });
        NeoForge.EVENT_BUS.addListener(LevelEvent.Save.class, e -> {
            if (e.getLevel() instanceof ServerLevel level && level.dimension() == net.minecraft.world.level.Level.OVERWORLD) FarStore.flushAll(false);
        });
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, e -> {
            PENDING.clear();
            LOADED.clear();
            PLANNED.clear();
            wasCollecting = false;
            FarLandmarks.clear();
            FarStore.closeAll();
            dev.awaken.farsight.voxel.VoxelCapture.reset();
        });
    }

    /** Far terrain is drawn here: vanilla-skied dimensions open to the horizon (compat.FarView). */
    public static boolean wanted(ServerLevel level) {
        return FarView.allowed(level.dimension().identifier().toString()) && level.dimensionType().skybox() == DimensionType.Skybox.OVERWORLD;
    }

    private static void note(ServerLevel level, ChunkPos pos) {
        PENDING.computeIfAbsent(level, k -> new LongLinkedOpenHashSet()).add(pos.pack());
    }

    private static void tick(MinecraftServer server) {
        // City Sight's snapshots first, within their own budget: the opening shows the city
        ServerLevel overworld = server.overworld();
        if (wanted(overworld)) dev.awaken.farsight.voxel.VoxelCapture.tick(overworld, System.nanoTime() + VOXEL_BUDGET_NANOS);
        boolean on = FarStore.collecting;
        if (on && !wasCollecting) {
            // just switched on: every chunk already loaded counts too (players' views, the city's pregenerated hold),
            // nearest the players first; then the city chunks never seen, from the plan
            for (var entry : LOADED.entrySet()) {
                ServerLevel level = entry.getKey();
                long[] keys = entry.getValue().toLongArray();
                BlockPos near = level.players().isEmpty() ? BlockPos.ZERO : level.players().getFirst().blockPosition();
                int ncx = near.getX() >> 4, ncz = near.getZ() >> 4;
                LongArrays.quickSort(keys, (a, b) -> Long.compare(dist(a, ncx, ncz), dist(b, ncx, ncz)));
                for (long key : keys) PENDING.computeIfAbsent(level, k -> new LongLinkedOpenHashSet()).add(key);
            }
            PLANNED.clear();
            CityLayout city = City.layout();
            if (city != null) for (long key : city.chunks) PLANNED.enqueue(key);
        }
        wasCollecting = on;
        if (!on) {
            PENDING.clear();
            return;
        }
        FarLandmarks.tick(server);
        long deadline = System.nanoTime() + BUDGET_NANOS;
        for (var entry : PENDING.entrySet()) {
            ServerLevel level = entry.getKey();
            LongLinkedOpenHashSet queue = entry.getValue();
            FarStore store = FarStore.of(level);
            while (!queue.isEmpty()) {
                if (System.nanoTime() > deadline) return;
                long key = queue.removeFirstLong();
                LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
                if (chunk != null) store.put(FarCity.mark(FarChunk.of(chunk)));
            }
        }
        plan(server, deadline);
    }

    /** City chunks far terrain has never seen, laid out from the plan, within what is left of the tick's budget. */
    private static void plan(MinecraftServer server, long deadline) {
        CityLayout city = City.layout();
        ServerLevel level = server.overworld();
        if (city == null || PLANNED.isEmpty() || !wanted(level)) return;
        FarStore store = FarStore.of(level);
        Holder<Biome> biome = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        while (!PLANNED.isEmpty() && System.nanoTime() < deadline) {
            long key = PLANNED.dequeueLong();
            int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
            if (store.get(cx, cz) != null) continue;
            FarChunk c = FarCity.planned(city, cx, cz, level.getSeed(), biome);
            if (c != null) store.put(c);
        }
    }

    private static long dist(long key, int cx, int cz) {
        long dx = ChunkPos.getX(key) - cx, dz = ChunkPos.getZ(key) - cz;
        return dx * dx + dz * dz;
    }
}
