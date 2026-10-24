package dev.awaken.farsight;

import dev.awaken.compat.FarView;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
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
 * Feeds the generated layer (SPEC §16): every chunk that loads, and every chunk saved after a change (that is how
 * edits come back), is summed into a {@link FarChunk} and kept in the level's {@link FarStore}. Events only note the
 * chunk; the summing happens on the server tick within {@link #BUDGET_NANOS} (the chunk is read, nothing else), so a
 * burst of loads spreads over ticks. Runs only while something draws far terrain ({@link FarStore#collecting}).
 */
public final class FarCollector {
    private FarCollector() {}

    static final long BUDGET_NANOS = 1_500_000L;

    private static final Map<ServerLevel, LongLinkedOpenHashSet> PENDING = new HashMap<>();
    private static boolean wasCollecting;

    static void init() {
        NeoForge.EVENT_BUS.addListener(ChunkEvent.Load.class, e -> {
            if (FarStore.collecting && e.getLevel() instanceof ServerLevel level && wanted(level)) note(level, e.getChunk().getPos());
        });
        NeoForge.EVENT_BUS.addListener(ChunkDataEvent.Save.class, e -> {
            if (FarStore.collecting && e.getLevel() instanceof ServerLevel level && e.getChunk() instanceof LevelChunk && wanted(level)) {
                note(level, e.getChunk().getPos());
            }
        });
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener(LevelEvent.Save.class, e -> {
            if (e.getLevel() instanceof ServerLevel level && level.dimension() == net.minecraft.world.level.Level.OVERWORLD) FarStore.flushAll(false);
        });
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, e -> {
            PENDING.clear();
            wasCollecting = false;
            FarLandmarks.clear();
            FarStore.closeAll();
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
        boolean on = FarStore.collecting;
        if (on && !wasCollecting) {
            // just switched on: the chunks already loaded around each player count too
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!(p.level() instanceof ServerLevel level) || !wanted(level)) continue;
                int r = server.getPlayerList().getViewDistance(), cx = p.chunkPosition().x(), cz = p.chunkPosition().z();
                for (int dz = -r; dz <= r; dz++) for (int dx = -r; dx <= r; dx++) note(level, new ChunkPos(cx + dx, cz + dz));
            }
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
                if (chunk != null) store.put(FarChunk.of(chunk));
            }
        }
    }
}
