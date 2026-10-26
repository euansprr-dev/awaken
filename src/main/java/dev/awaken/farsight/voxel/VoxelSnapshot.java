package dev.awaken.farsight.voxel;

import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jspecify.annotations.Nullable;

/**
 * A chunk's blocks and light as the server had them (SPEC §20, City Sight): copies of its sections from just under its
 * lowest ground to the highest that holds anything, one more each side for the light their faces get, taken on the
 * server thread in a few microseconds a section and read on Far Sight's workers. Sections are indexed from
 * {@link #fromSection}; {@code ground} is the chunk's lowest ground (leaves aside), where what can be seen begins.
 */
public record VoxelSnapshot(int chunkX, int chunkZ, int levelMinY, int fromSection, int ground, @Nullable LevelChunkSection[] sections,
                            @Nullable DataLayer[] sky, @Nullable DataLayer[] block) {

    /** Takes the snapshot (server thread), or null if the chunk holds nothing but air. */
    public static @Nullable VoxelSnapshot take(ServerLevel level, LevelChunk chunk) {
        LevelChunkSection[] all = chunk.getSections();
        int lo = -1, hi = -1;
        for (int i = 0; i < all.length; i++) {
            if (all[i] != null && !all[i].hasOnlyAir()) {
                if (lo < 0) lo = i;
                hi = i;
            }
        }
        if (lo < 0) return null;
        // from just under the chunk's lowest ground: what lies deeper (stone, dark caves) never shows from afar, and a
        // natural chunk is then a few sections, not the world's whole height. Ground without leaves (OCEAN_FLOOR counts
        // them solid: under a closed canopy the cut fell at the leaves, the ground went missing and the trees floated
        // over a hole); water's surface is where it ends there, which is all that shows of it from afar
        int ground = Integer.MAX_VALUE;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                ground = Math.min(ground, chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
            }
        }
        int groundSection = chunk.getSectionIndex(Math.max(chunk.getMinY(), ground - 8));
        lo = Math.max(lo, groundSection);
        lo = Math.max(0, lo - 1);
        hi = Math.min(all.length - 1, hi + 1);
        int n = hi - lo + 1, minSection = chunk.getMinSectionY();
        LevelChunkSection[] sections = new LevelChunkSection[n];
        DataLayer[] sky = new DataLayer[n], block = new DataLayer[n];
        var skyLight = level.getLightEngine().getLayerListener(LightLayer.SKY);
        var blockLight = level.getLightEngine().getLayerListener(LightLayer.BLOCK);
        int cx = chunk.getPos().x(), cz = chunk.getPos().z();
        for (int i = 0; i < n; i++) {
            LevelChunkSection s = all[lo + i];
            sections[i] = s == null || s.hasOnlyAir() ? null : s.copy();
            SectionPos pos = SectionPos.of(cx, minSection + lo + i, cz);
            DataLayer sl = skyLight.getDataLayerData(pos), bl = blockLight.getDataLayerData(pos);
            sky[i] = sl == null ? null : sl.copy();
            block[i] = bl == null ? null : bl.copy();
        }
        return new VoxelSnapshot(cx, cz, level.getMinY(), minSection + lo, ground, sections, sky, block);
    }

    /** A hash of its blocks and light: the same chunk taken again unchanged hashes the same (workers; ~1 ms). */
    public long hash() {
        long h = 1125899906842597L ^ fromSection;
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection s = sections[i];
            if (s != null) {
                var states = s.getStates();
                for (int k = 0; k < 4096; k++) h = h * 31 + Block.BLOCK_STATE_REGISTRY.getId(states.get(k & 15, k >> 8, (k >> 4) & 15));
            } else {
                h = h * 31 - 1;
            }
            h = h * 31 + (sky[i] == null ? 7 : java.util.Arrays.hashCode(sky[i].getData()));
            h = h * 31 + (block[i] == null ? 11 : java.util.Arrays.hashCode(block[i].getData()));
        }
        return h;
    }

    /** The lowest block y the snapshot covers. */
    public int minY() {
        return fromSection * 16;
    }

    /** Blocks it covers upward from {@link #minY()}. */
    public int height() {
        return sections.length * 16;
    }

    /** The block-state id at chunk-local x, z and snapshot-local y (0 for air, and outside). */
    public int state(int x, int y, int z) {
        if (y < 0 || y >= height()) return 0;
        LevelChunkSection s = sections[y >> 4];
        return s == null ? 0 : Block.BLOCK_STATE_REGISTRY.getId(s.getStates().get(x, y & 15, z));
    }

    /** Sky light (0..15) at chunk-local x, z, snapshot-local y: full above and where the game keeps no data. */
    public int skyLight(int x, int y, int z) {
        if (y < 0) return 0;
        if (y >= height() || x < 0 || x > 15 || z < 0 || z > 15) return 15;
        DataLayer d = sky[y >> 4];
        return d == null ? 15 : d.get(x, y & 15, z);
    }

    /** Block light (0..15) at chunk-local x, z, snapshot-local y. */
    public int blockLight(int x, int y, int z) {
        if (y < 0 || y >= height() || x < 0 || x > 15 || z < 0 || z > 15) return 0;
        DataLayer d = block[y >> 4];
        return d == null ? 0 : d.get(x, y & 15, z);
    }

    /** The biome at chunk-local x, z, snapshot-local y (its section's, 4-block resolution), or null in an empty section. */
    public @Nullable Holder<Biome> biome(int x, int y, int z) {
        int i = Math.clamp(y >> 4, 0, sections.length - 1);
        LevelChunkSection s = sections[i];
        if (s == null) {
            for (int k = 0; k < sections.length && s == null; k++) s = sections[k];
            if (s == null) return null;
        }
        return s.getBiomes().get(x >> 2, (y & 15) >> 2, z >> 2);
    }
}
