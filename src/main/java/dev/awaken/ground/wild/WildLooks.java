package dev.awaken.ground.wild;

import dev.awaken.Awaken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;

/**
 * Which look a wild plant takes where it grows (SPEC §41.2), pure of the place: the land's own patches pick long grass's
 * look (green, going to seed, tawny), the Awakened biomes their own (the Glass Steppe's glassy rye, Mirror Marsh's pale
 * reeds). Worldgen, regrowth and placing by hand all ask here, so a lawn grows back the way it was.
 */
public final class WildLooks {
    private WildLooks() {}

    public static final int GREEN = 0, SEEDING = 1, TAWNY = 2;
    public static final ResourceKey<Biome> GLASS_STEPPE = ResourceKey.create(Registries.BIOME, Awaken.id("glass_steppe"));
    public static final ResourceKey<Biome> MIRROR_MARSH = ResourceKey.create(Registries.BIOME, Awaken.id("mirror_marsh"));

    /** Long grass's look from the land's patches (about 20 blocks across); {@code wild} 0..1 makes tawny and seeding commoner. */
    public static int longGrass(int x, int z, double wild) {
        double n = patch(x, z, 22) * 0.75 + patch(x + 911, z - 377, 7) * 0.25;
        double tawny = 0.86 - 0.14 * wild, seeding = 0.52 - 0.10 * wild;
        return n > tawny ? TAWNY : n > seeding ? SEEDING : GREEN;
    }

    /** The look for a plant placed or grown back at {@code pos}. */
    public static int look(LevelReader level, Block block, BlockPos pos) {
        if (block == WildBlocks.LONG_GRASS.get()) return longGrass(pos.getX(), pos.getZ(), 0.5);
        if (block == WildBlocks.WILD_RYE.get()) return in(level, pos, GLASS_STEPPE) ? 1 : 0;
        if (block == WildBlocks.REEDS.get()) return in(level, pos, MIRROR_MARSH) ? 1 : 0;
        if (block == WildBlocks.BRACKEN.get()) return patch(pos.getX(), pos.getZ(), 9) > 0.72 ? 1 : 0;
        return 0;
    }

    /** Which of a two-block plant's three drawings stands in this column (SPEC §41.2): both halves take it, so they meet. */
    public static int shape(int x, int z) {
        return (int) (hash(x ^ 0x5EA4, z) * 3);
    }

    /** As {@link #look(LevelReader, Block, BlockPos)} where only the column is known (no biome: the default look). */
    public static int look(Block block, int x, int z) {
        if (block == WildBlocks.LONG_GRASS.get()) return longGrass(x, z, 0.5);
        if (block == WildBlocks.BRACKEN.get()) return patch(x, z, 9) > 0.72 ? 1 : 0;
        return 0;
    }

    private static boolean in(LevelReader level, BlockPos pos, ResourceKey<Biome> biome) {
        Holder<Biome> b = level.getBiome(pos);
        return b.is(biome);
    }

    /** Smooth value noise in 0..1 on a lattice of {@code cell} blocks, the same in every world. */
    public static double patch(int x, int z, int cell) {
        int gx = Math.floorDiv(x, cell), gz = Math.floorDiv(z, cell);
        double fx = smooth((x - (double) gx * cell) / cell), fz = smooth((z - (double) gz * cell) / cell);
        double a = hash(gx, gz), b = hash(gx + 1, gz), c = hash(gx, gz + 1), d = hash(gx + 1, gz + 1);
        double top = a + (b - a) * fx, bottom = c + (d - c) * fx;
        return top + (bottom - top) * fz;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    /** A stable hash of a lattice point (or a block) in 0..1. */
    public static double hash(int x, int z) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ 0x5711D_0A7L;
        h = (h ^ (h >>> 30)) * 0xbf58476d1ce4e5b9L;
        h = (h ^ (h >>> 27)) * 0x94d049bb133111ebL;
        h ^= h >>> 31;
        return (h >>> 11) * 0x1.0p-53;
    }
}
