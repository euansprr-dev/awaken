package dev.awaken.lens;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.function.Predicate;

/**
 * The terrain's heights as Lens 2 takes them (SPEC §22, "The heightfield sees every solid block"): the game's own
 * heightmaps, and every block they skip that stands as solid as one they count. 26.3 builds its heightmaps from
 * {@code #minecraft:blocks_motion}, which holds the game's blocks and only the mod blocks a tag adds, so a room of
 * Carillon's city bricks under clay tiles read as open sky: Lens lit it like the street, and only the screen-space
 * contact shadows (the door frame's, a few blocks long) shaped that light, sliding as you walked (Euan 2026-10-29).
 * Pure rules over a chunk, nothing allocated.
 */
public final class LensHeights {
    private static final Predicate<BlockState> SKIPPED = LensHeights::skipped;

    private LensHeights() {}

    /**
     * A block the heightmaps skip that stands as solid as one they count: the rule the tag replaced (a collision box
     * most of a block, or a whole block high; the game still keeps it as {@code isSolid}).
     */
    @SuppressWarnings("deprecation")
    public static boolean skipped(BlockState st) {
        return st.isSolid() && !st.is(BlockTags.BLOCKS_MOTION_IN_HEIGHTMAP);
    }

    /**
     * Which of a chunk's sections may hold a skipped block, into {@code has} (by section index, at least
     * {@code getSectionsCount()} long). From their palettes: a chunk without one costs nothing to walk.
     */
    public static void sections(LevelChunk chunk, boolean[] has) {
        LevelChunkSection[] s = chunk.getSections();
        for (int i = 0; i < s.length; i++) has[i] = !s[i].hasOnlyAir() && s[i].maybeHas(SKIPPED);
    }

    /**
     * Column (x, z) of a chunk (0-15), as the heightmaps' first free y with the skipped blocks taken in: {@code out[0]}
     * everything that blocks motion, leaves included; {@code out[1]} without leaves. Walks down from the column's highest
     * block of any kind to the heightmap's own top, only through sections {@link #sections} marked.
     */
    public static void column(LevelChunk chunk, boolean[] has, int x, int z, int[] out) {
        int a = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 1;
        int s = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
        LevelChunkSection[] sections = chunk.getSections();
        for (int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z); y >= s; y--) {
            int i = chunk.getSectionIndex(y);
            if (!has[i]) {
                // on to the top of the section below
                y = (chunk.getSectionYFromSectionIndex(i) << 4);
                continue;
            }
            // (never leaves: #leaves is in the tag)
            if (skipped(sections[i].getBlockState(x, y & 15, z))) {
                a = Math.max(a, y + 1);
                s = y + 1;
                break;
            }
        }
        out[0] = a;
        out[1] = s;
    }
}
