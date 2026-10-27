package dev.awaken.ground;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.terrain.landmark.LandsClock;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

import java.util.List;

/**
 * A boulder or outcrop (SPEC §31, P2): a rounded mass of rock half sunk in the ground, one to seven blocks across, whose
 * crown is softened with the rock's own half-block slabs so no hard corner shows. Vanilla blocks only. The palette
 * ({@code forest}: mossy cobblestone and stone; {@code highland}: stone, andesite, granite, tuff; {@code plains}: stone and
 * andesite; {@code desert}: sandstone; {@code badlands}: red sandstone and terracotta-free sandstone) is the biome's; the
 * radius is drawn from {@code min_radius..max_radius}. Never over water, logs or leaves; it is placed where the column's ground
 * is natural and open, and it stays inside the 16-block reach a feature may write.
 */
public final class BoulderFeature implements Feature {
    public static final MapCodec<BoulderFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            com.mojang.serialization.Codec.STRING.fieldOf("palette").forGetter(f -> f.palette),
            com.mojang.serialization.Codec.intRange(1, 7).fieldOf("min_radius").forGetter(f -> f.minRadius),
            com.mojang.serialization.Codec.intRange(1, 7).fieldOf("max_radius").forGetter(f -> f.maxRadius))
            .apply(i, BoulderFeature::new));

    /** A rock and the slab that rounds it off. */
    private record Rock(Block block, Block slab, int weight) {}

    private final String palette;
    private final int minRadius, maxRadius;
    private final List<Rock> rocks;
    private final int total;

    public BoulderFeature(String palette, int minRadius, int maxRadius) {
        this.palette = palette;
        this.minRadius = Math.min(minRadius, maxRadius);
        this.maxRadius = Math.max(minRadius, maxRadius);
        this.rocks = palette(palette);
        int t = 0;
        for (Rock r : this.rocks) t += r.weight;
        this.total = t;
    }

    static List<Rock> palette(String name) {
        return switch (name) {
            case "forest" -> List.of(new Rock(Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_COBBLESTONE_SLAB, 5), new Rock(Blocks.STONE, Blocks.STONE_SLAB, 3),
                    new Rock(Blocks.COBBLESTONE, Blocks.COBBLESTONE_SLAB, 2));
            case "highland" -> List.of(new Rock(Blocks.STONE, Blocks.STONE_SLAB, 4), new Rock(Blocks.ANDESITE, Blocks.ANDESITE_SLAB, 3),
                    new Rock(Blocks.GRANITE, Blocks.GRANITE_SLAB, 2), new Rock(Blocks.TUFF, Blocks.TUFF_SLAB, 1));
            case "desert" -> List.of(new Rock(Blocks.SANDSTONE, Blocks.SANDSTONE_SLAB, 1));
            case "badlands" -> List.of(new Rock(Blocks.RED_SANDSTONE, Blocks.RED_SANDSTONE_SLAB, 3), new Rock(Blocks.SANDSTONE, Blocks.SANDSTONE_SLAB, 1));
            default -> List.of(new Rock(Blocks.STONE, Blocks.STONE_SLAB, 4), new Rock(Blocks.ANDESITE, Blocks.ANDESITE_SLAB, 3), new Rock(Blocks.COBBLESTONE, Blocks.COBBLESTONE_SLAB, 1));
        };
    }

    @Override
    public MapCodec<BoulderFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        long t0 = System.nanoTime();
        int r = this.minRadius + random.nextInt(this.maxRadius - this.minRadius + 1);
        boolean placed = paint(level, origin, r, random.nextInt(), generator.getSeaLevel());
        LandsClock.add("boulder", System.nanoTime() - t0);
        return placed;
    }

    /**
     * Lays one boulder with its base plane at {@code origin} (the first open block over the ground), of radius {@code r} across
     * and {@code 0.7 r} up. Returns whether any rock was placed. Public for the tests.
     */
    public boolean paint(WorldGenLevel level, BlockPos origin, int r, int seed, int sea) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        // on open natural ground, not in water, with room: the centre column's ground must be a natural block
        BlockState under = level.getBlockState(m.set(origin.getX(), origin.getY() - 1, origin.getZ()));
        if (!under.is(BlockTags.DIRT) && !under.is(BlockTags.BASE_STONE_OVERWORLD) && !under.is(BlockTags.SAND) && !under.is(Blocks.GRAVEL)) return false;
        if (!level.getFluidState(m.set(origin.getX(), origin.getY(), origin.getZ())).isEmpty()) return false;
        // squash it a little and tilt its long axis: no two alike, none a sphere
        double rx = r * (0.85 + 0.3 * h(seed, 1, 0)), rz = r * (0.85 + 0.3 * h(seed, 2, 0)), ry = Math.max(1.2, r * 0.72);
        int reach = (int) Math.ceil(Math.max(rx, rz)) + 1;
        boolean any = false;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double d2 = (dx * dx) / (rx * rx) + (dz * dz) / (rz * rz);
                if (d2 >= 1.0) continue;
                // the crown's height over the base plane, bumped by the column's own noise
                double hf = ry * Math.sqrt(1.0 - d2) + (h(seed, dx, dz) - 0.5) * 0.7;
                int blocks = (int) Math.floor(hf);
                boolean slab = hf - blocks >= 0.5;
                int x = origin.getX() + dx, z = origin.getZ() + dz;
                // sunk one block (two for the big ones) into the ground, so it grows out of it
                int sink = r >= 3 ? 2 : 1;
                for (int k = -sink; k < Math.max(blocks, 1); k++) {
                    int y = origin.getY() + k;
                    if (!canReplace(level, m.set(x, y, z), k < 0)) continue;
                    level.setBlock(m, rock(seed, x, y, z).block.defaultBlockState(), Block.UPDATE_CLIENTS);
                    any = true;
                }
                int top = origin.getY() + Math.max(blocks, 1);
                if (slab && blocks >= 1 && canReplace(level, m.set(x, top, z), false)) {
                    level.setBlock(m, rock(seed, x, top - 1, z).slab.defaultBlockState(), Block.UPDATE_CLIENTS);
                    any = true;
                }
            }
        }
        return any;
    }

    /** Air, plants and leaf litter anywhere; below the base plane also the natural ground. Never fluids, logs, leaves or builders' blocks. */
    private static boolean canReplace(WorldGenLevel level, BlockPos pos, boolean ground) {
        BlockState s = level.getBlockState(pos);
        if (!s.getFluidState().isEmpty()) return false;
        if (s.isAir() || s.canBeReplaced() || s.is(BlockTags.REPLACEABLE_BY_TREES) && !s.is(BlockTags.LEAVES)) return true;
        if (!ground) return false;
        return s.is(BlockTags.DIRT) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL);
    }

    private Rock rock(int seed, int x, int y, int z) {
        int pick = (int) (h(seed, x * 31 + y, z) * this.total);
        for (Rock r : this.rocks) {
            if ((pick -= r.weight) < 0) return r;
        }
        return this.rocks.getLast();
    }

    /** A stable 0..1 from the boulder's seed and two integers. */
    static double h(int seed, int a, int b) {
        long k = seed * 0x9E3779B97F4A7C15L + a * 0x632BE59BD9B4E019L + b * 0x85157AF5L;
        k ^= k >>> 29;
        k *= 0xBF58476D1CE4E5B9L;
        k ^= k >>> 32;
        return (k & 0xFFFFFF) / (double) 0x1000000;
    }

    public String palette() {
        return this.palette;
    }
}
