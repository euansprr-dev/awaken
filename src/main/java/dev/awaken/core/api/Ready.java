package dev.awaken.core.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * Is the land really there? (SPEC §12, §21.6.) The game's own checks ({@code hasChunkAt}, {@code hasChunk},
 * {@code isLoaded}) only ask whether a chunk has a ticket, not whether it is made: a chunk the player's view has just
 * reached is ticketed at once and generated over the next seconds, and reading a block, a fluid or a height off it on the
 * server thread waits for the generator (up to 22 s in Euan's log of 12 Nov, flying with wings over new land, while the
 * world stood still). These answer yes only for a chunk that is whole and in memory, never load one and never wait. On
 * the server thread they ask the chunk cache for the finished chunk; anywhere else (another thread, the client, a world
 * generation region) they answer as the game does.
 */
public final class Ready {
    private Ready() {}

    /** The chunk at chunk coordinates when it is whole and in memory; null otherwise (and off the server thread). */
    public static @Nullable LevelChunk chunk(Level level, int cx, int cz) {
        return level instanceof ServerLevel sl ? sl.getChunkSource().getChunkNow(cx, cz) : null;
    }

    /** The chunk holding block column (x, z) when it is whole and in memory; null otherwise. */
    public static @Nullable LevelChunk chunkAt(Level level, int x, int z) {
        return chunk(level, SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
    }

    /** In place of {@code hasChunk(cx, cz)}: the chunk at chunk coordinates is there to read. */
    public static boolean chunkReady(LevelReader level, int cx, int cz) {
        if (level instanceof ServerLevel sl && sl.getServer().isSameThread()) return sl.getChunkSource().getChunkNow(cx, cz) != null;
        return level.hasChunk(cx, cz);
    }

    /** In place of {@code hasChunkAt(x, z)}: the chunk holding column (x, z) is there to read. */
    public static boolean at(LevelReader level, int x, int z) {
        return chunkReady(level, SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
    }

    /** In place of {@code hasChunkAt(pos)}. */
    public static boolean at(LevelReader level, BlockPos pos) {
        return at(level, pos.getX(), pos.getZ());
    }

    /** In place of {@code isLoaded(pos)}: inside the world's bounds and its chunk there to read. */
    public static boolean loaded(Level level, BlockPos pos) {
        return level.isInValidBounds(pos) && at(level, pos.getX(), pos.getZ());
    }

    /** In place of {@code hasChunksAt(x0, z0, x1, z1)}: every chunk across the box is there to read. */
    public static boolean area(LevelReader level, int x0, int z0, int x1, int z1) {
        int cx0 = SectionPos.blockToSectionCoord(Math.min(x0, x1)), cx1 = SectionPos.blockToSectionCoord(Math.max(x0, x1));
        int cz0 = SectionPos.blockToSectionCoord(Math.min(z0, z1)), cz1 = SectionPos.blockToSectionCoord(Math.max(z0, z1));
        for (int cx = cx0; cx <= cx1; cx++)
            for (int cz = cz0; cz <= cz1; cz++) if (!chunkReady(level, cx, cz)) return false;
        return true;
    }

    /** In place of {@code hasChunksAt(from, to)}. */
    public static boolean area(LevelReader level, BlockPos from, BlockPos to) {
        return area(level, from.getX(), from.getZ(), to.getX(), to.getZ());
    }

    /**
     * In place of {@code level.getHeight(type, x, z)}: the same answer where the chunk is there, and the world's floor
     * ({@code getMinY()}, the game's own answer for a chunk it doesn't hold) where it is still being made, never a wait.
     */
    public static int height(LevelReader level, Heightmap.Types type, int x, int z) {
        if (!(level instanceof ServerLevel sl) || !sl.getServer().isSameThread()) return level.getHeight(type, x, z);
        if (x < -30000000 || z < -30000000 || x >= 30000000 || z >= 30000000) return sl.getSeaLevel() + 1;
        LevelChunk c = sl.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        return c == null ? sl.getMinY() : c.getHeight(type, x & 15, z & 15) + 1;
    }

    /** In place of {@code level.getHeightmapPos(type, pos)}. */
    public static BlockPos heightPos(LevelReader level, Heightmap.Types type, BlockPos pos) {
        return new BlockPos(pos.getX(), height(level, type, pos.getX(), pos.getZ()), pos.getZ());
    }

    /**
     * Whether every chunk within {@code radius} chunks of the player is made: false while new land is still streaming in
     * round them (a fast flight, a teleport, a fresh world).
     */
    public static boolean settled(ServerPlayer p, int radius) {
        if (!(p.level() instanceof ServerLevel sl)) return true;
        int cx = SectionPos.blockToSectionCoord(p.getBlockX()), cz = SectionPos.blockToSectionCoord(p.getBlockZ());
        for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++) if (sl.getChunkSource().getChunkNow(cx + dx, cz + dz) == null) return false;
        return true;
    }

    /**
     * Nanoseconds left in this tick's share of time before the next tick is due (negative once the server is late). Work
     * that can wait (SPEC §21.6: happenings) runs only while there's plenty.
     */
    public static long spareNanos(MinecraftServer server) {
        return server.getNextTickTime() - Util.getNanos();
    }
}
