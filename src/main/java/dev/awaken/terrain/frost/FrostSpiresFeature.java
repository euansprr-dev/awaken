package dev.awaken.terrain.frost;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.terrain.landmark.LandsClock;
import dev.awaken.terrain.landmark.Voxels;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * A sheaf of frost spires (docs/modules/lands.md, "Frost Spires"): needles of frostspar bursting from one root, the
 * tallest near upright in the middle, the rest splaying out every way like frost grown on a pane, the big ones with
 * side crystals branching off at about 55° (hoarfrost's dendrites, scaled up). Square needles turned 45° to a point,
 * never the steppe's hexagonal prisms, never all one way. The ground round the root is frozen to hoarfrost with slivers
 * standing in it; every needle's foot reaches down to the ground and the sheaf stays within thirteen blocks of its root
 * (a feature may write one chunk beyond its own).
 */
public record FrostSpiresFeature(IntProvider count, IntProvider height, float splay) implements Feature {
    public static final MapCodec<FrostSpiresFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            IntProviders.codec(1, 12).fieldOf("count").forGetter(FrostSpiresFeature::count),
            IntProviders.codec(3, 18).fieldOf("height").forGetter(FrostSpiresFeature::height),
            Codec.floatRange(0F, 1F).fieldOf("splay").forGetter(FrostSpiresFeature::splay)
    ).apply(i, FrostSpiresFeature::new));

    static final byte SPAR = 1;
    static final int EXTENT = 13;
    /** The angle between successive needles round the root (the golden angle: no two line up). */
    private static final double GOLDEN = 2.39996;

    @Override
    public MapCodec<FrostSpiresFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        long t0 = System.nanoTime();
        try {
            return placeSheaf(level, random, origin);
        } finally {
            LandsClock.add(height.maxInclusive() > 8 ? "frost_spires" : "small_frost_spires", System.nanoTime() - t0);
        }
    }

    private boolean placeSheaf(WorldGenLevel level, RandomSource random, BlockPos origin) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        // stand on frozen ground (under the snow layer the surface heightmap sees)
        if (level.getBlockState(origin.below()).is(Blocks.SNOW)) origin = origin.below();
        if (!ground(level.getBlockState(m.set(origin.getX(), origin.getY() - 1, origin.getZ())))) return false;
        Voxels v = new Voxels(EXTENT, -3, 22);
        sheaf(v, 0, -1, 0, random, count.sample(random), height, splay, SPAR);
        v.seal();
        BlockState spar = FrostBlocks.FROSTSPAR.get().defaultBlockState();
        BlockState frost = FrostBlocks.HOARFROST.get().defaultBlockState();
        BlockState cluster = FrostBlocks.FROSTSPAR_CLUSTER.get().defaultBlockState();
        int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
        // the root freezes the ground round it to hoarfrost (its snow gone), slivers standing in it
        int ring = 2 + (int) (height.maxInclusive() * 0.25);
        for (int x = -ring - 1; x <= ring + 1; x++) {
            for (int z = -ring - 1; z <= ring + 1; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r > ring + random.nextDouble() * 1.5) continue;
                int gx = ox + x, gz = oz + z;
                int gy = level.getHeight(Voxels.surface(level), gx, gz) - 1;
                if (level.getBlockState(m.set(gx, gy, gz)).is(Blocks.SNOW)) {
                    level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    gy--;
                }
                if (Math.abs(gy - (oy - 1)) > 3 || !ground(level.getBlockState(m.set(gx, gy, gz)))) continue;
                if (random.nextDouble() < 0.85 - r * 0.07) level.setBlock(m, frost, Block.UPDATE_CLIENTS);
                if (random.nextDouble() < 0.1 && level.getBlockState(m.set(gx, gy + 1, gz)).isAir()) level.setBlock(m, cluster, Block.UPDATE_CLIENTS);
            }
        }
        v.place(level, origin, SPAR, spar, 7, s -> s.isAir() || s.canBeReplaced() || s.is(Blocks.SNOW) || s.is(FrostBlocks.FROSTSPAR_CLUSTER.get()));
        return true;
    }

    /**
     * A sheaf into {@code v}, its root at (x, y, z) (y: the top of the ground): {@code n} needles, the first near upright
     * and as tall as {@code height} allows, the others splayed up to {@code splay} radians off upright, each 9 or more
     * tall branching once or twice. The Aurora Spire grows its lesser crystals the same way.
     */
    public static void sheaf(Voxels v, double x, int y, double z, RandomSource random, int n, IntProvider height, double splay, byte code) {
        double a0 = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < n; i++) {
            double a = a0 + i * GOLDEN + (random.nextDouble() - 0.5) * 0.4;
            double tilt = i == 0 ? random.nextDouble() * 0.08 : splay * (0.45 + 0.55 * random.nextDouble());
            int h = height.sample(random);
            h = i == 0 ? Math.max(h, height.maxInclusive() - random.nextInt(3)) : Math.max(3, (int) (h * (0.5 + 0.4 * random.nextDouble())));
            double d = i == 0 ? 0 : 0.6 + random.nextDouble() * 0.9; // all from one root
            double bx = x + Math.cos(a) * d, bz = z + Math.sin(a) * d;
            double dx = Math.sin(tilt) * Math.cos(a), dy = Math.cos(tilt), dz = Math.sin(tilt) * Math.sin(a);
            double r0 = 0.65 + h * 0.08 + random.nextDouble() * 0.25;
            v.needle(bx, y, bz, dx, dy, dz, h + 1, r0, true, code);
            if (h < 9) continue;
            int branches = 1 + random.nextInt(2);
            for (int b = 0; b < branches; b++) {
                double f = 0.35 + random.nextDouble() * 0.3;
                double ba = a + (random.nextDouble() - 0.5) * 1.6, bt = Math.min(1.25, tilt + 0.85 + random.nextDouble() * 0.2);
                double len = h * (0.22 + random.nextDouble() * 0.13);
                v.needle(bx + dx * h * f, y + dy * h * f, bz + dz * h * f, Math.sin(bt) * Math.cos(ba), Math.cos(bt), Math.sin(bt) * Math.sin(ba),
                        len, Math.max(0.45, r0 * (1 - f) * 0.6), false, code);
            }
        }
    }

    static boolean ground(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.SNOW_BLOCK) || s.is(FrostBlocks.HOARFROST.get())
                || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.PACKED_ICE) || s.is(Blocks.GRAVEL);
    }
}
