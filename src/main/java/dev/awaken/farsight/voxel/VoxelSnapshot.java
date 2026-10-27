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
import net.minecraft.world.level.lighting.LayerLightEventListener;
import org.jspecify.annotations.Nullable;

/**
 * A chunk's blocks and light as the server had them (SPEC §20, City Sight): copies of its sections from just under its
 * lowest ground, or its lowest sky light if that lies deeper, to the highest that holds anything, one more each side for
 * the light their faces get, taken on the server thread in a few microseconds a section and read on Far Sight's workers.
 * Sections are indexed from {@link #fromSection}; {@code ground} is the chunk's lowest ground (leaves aside).
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
        // and down to the lowest sky light here or in the four chunks beside it: under an overhang a column's top is the
        // overhang, so a cave's mouth or a cliff's foot below it lay under a cut at the ground, and with the far terrain
        // stepped aside under the chunk nothing drew there past Sodium's reach: the sky showed through (Euan, 2026-10-27:
        // a white band at a river's cave mouths in a new world). A neighbour's counts: its lit cave runs on into this
        // chunk's dark one, which then closes it
        var skyLight = level.getLightEngine().getLayerListener(LightLayer.SKY);
        var blockLight = level.getLightEngine().getLayerListener(LightLayer.BLOCK);
        int cx = chunk.getPos().x(), cz = chunk.getPos().z(), minSection = chunk.getMinSectionY();
        int top = level.getLightEngine().getMaxLightSection(), lit = lowestSkyLit(skyLight, cx, cz, minSection, top, lo);
        for (int d = 0; d < 4; d++) {
            int nx = cx + (d == 0 ? 1 : d == 1 ? -1 : 0), nz = cz + (d == 2 ? 1 : d == 3 ? -1 : 0);
            LevelChunk beside = level.getChunkSource().getChunkNow(nx, nz);
            if (beside != null && beside.isLightCorrect()) lit = Math.min(lit, lowestSkyLit(skyLight, nx, nz, minSection, top, lo));
        }
        lo = Math.max(lo, Math.min(groundSection, lit));
        lo = Math.max(0, lo - 1);
        hi = Math.min(all.length - 1, hi + 1);
        int n = hi - lo + 1;
        LevelChunkSection[] sections = new LevelChunkSection[n];
        DataLayer[] sky = new DataLayer[n], block = new DataLayer[n];
        // a section the game keeps no sky light for reads as it does: the bottom row of the nearest kept one above, and
        // open sky with none above (read as full sky, a cave inside a mountain was lit, its walls drawn pale)
        DataLayer above = null;
        for (int sy = minSection + hi + 1; sy < top && above == null; sy++) above = skyLight.getDataLayerData(SectionPos.of(cx, sy, cz));
        for (int i = n - 1; i >= 0; i--) {
            LevelChunkSection s = all[lo + i];
            sections[i] = s == null || s.hasOnlyAir() ? null : s.copy();
            SectionPos pos = SectionPos.of(cx, minSection + lo + i, cz);
            DataLayer sl = skyLight.getDataLayerData(pos), bl = blockLight.getDataLayerData(pos);
            if (sl != null) above = sl;
            sky[i] = sl != null ? sl.copy() : above != null ? bottomRow(above) : null;
            block[i] = bl == null ? null : bl.copy();
        }
        return new VoxelSnapshot(cx, cz, level.getMinY(), minSection + lo, ground, sections, sky, block);
    }

    /**
     * The lowest section of column (cx, cz), counted from {@code minSection}, with sky light anywhere in it, looked for
     * down to {@code floor}, or MAX_VALUE: by the game's rule, a section it keeps no light for has its nearest kept
     * one's bottom row, and lies under open sky with none above.
     */
    static int lowestSkyLit(LayerLightEventListener sky, int cx, int cz, int minSection, int top, int floor) {
        int lowest = Integer.MAX_VALUE;
        boolean fromAbove = true;
        for (int sy = top - 1; sy >= minSection + floor; sy--) {
            DataLayer d = sky.getDataLayerData(SectionPos.of(cx, sy, cz));
            boolean lit = fromAbove;
            if (d != null) {
                lit = anyLight(d, DataLayer.SIZE);
                fromAbove = anyLight(d, DataLayer.LAYER_SIZE);
            }
            if (lit) lowest = sy - minSection;
        }
        return lowest;
    }

    /** Whether any of a light layer's first {@code bytes} bytes (two cells each) is lit, never filling the game's layer in. */
    private static boolean anyLight(DataLayer d, int bytes) {
        if (d.isDefinitelyHomogenous()) return !d.isDefinitelyFilledWith(0);
        byte[] b = d.getData();
        for (int i = 0; i < bytes; i++) if (b[i] != 0) return true;
        return false;
    }

    /** A layer of the game's bottom row repeated up all 16 (how it fills a section it keeps no sky light for). */
    private static DataLayer bottomRow(DataLayer d) {
        if (d.isDefinitelyHomogenous()) return d.copy();
        byte[] in = d.getData(), out = new byte[DataLayer.SIZE];
        for (int y = 0; y < 16; y++) System.arraycopy(in, 0, out, y * DataLayer.LAYER_SIZE, DataLayer.LAYER_SIZE);
        return new DataLayer(out);
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
