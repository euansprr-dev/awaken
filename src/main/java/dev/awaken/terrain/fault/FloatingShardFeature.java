package dev.awaken.terrain.fault;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.terrain.landmark.Voxels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.synth.Noise;

/**
 * A rock shard hanging in the air over a fault (docs/modules/lands.md, "Shattered Highlands"): an upturned point of
 * riven shale, faultstone in its lower half and the seam's light at its tip, some with turf still on top, a chip or
 * two of rubble beside it. It only forms over the fault band (the same noise the surface pass reads) and only where
 * every block of it is open air, so it never cuts into a slope. At most six blocks across: it stays within a chunk of
 * its own. Floats on purpose.
 */
public record FloatingShardFeature(IntProvider radius, IntProvider lift) implements Feature {
    public static final MapCodec<FloatingShardFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            net.minecraft.util.valueproviders.IntProviders.codec(1, 4).fieldOf("radius").forGetter(FloatingShardFeature::radius),
            net.minecraft.util.valueproviders.IntProviders.codec(4, 40).fieldOf("lift").forGetter(FloatingShardFeature::lift)
    ).apply(i, FloatingShardFeature::new));

    @Override
    public MapCodec<FloatingShardFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        long t0 = System.nanoTime();
        try {
            return placeShard(level, random, origin);
        } finally {
            dev.awaken.terrain.landmark.LandsClock.add("floating_shard", System.nanoTime() - t0);
        }
    }

    private boolean placeShard(WorldGenLevel level, RandomSource random, BlockPos origin) {
        var rs = level.getLevel().getChunkSource().randomState();
        Noise fault = FaultNoise.fault(rs);
        if (fault == null) return false;
        // find the fault near the origin: a few tries within six blocks
        int fx = 0, fz = 0;
        boolean found = false;
        for (int k = 0; k < 10 && !found; k++) {
            int x = origin.getX() + random.nextInt(13) - 6, z = origin.getZ() + random.nextInt(13) - 6;
            if (FaultNoise.distance(fault, x, z) < 1.2) {
                fx = x;
                fz = z;
                found = true;
            }
        }
        if (!found) return false;
        return build(level, random, fx, fz, radius.sample(random), lift.sample(random));
    }

    /** Builds one shard over (x, z): its tip {@code lift} blocks above the ground there. False when it would touch anything. */
    public static boolean build(WorldGenLevel level, RandomSource random, int x, int z, int radius, int lift) {
        int ground = level.getHeight(Voxels.surface(level), x, z);
        double rad = radius + random.nextDouble() * 0.8;
        int h = (int) Math.round(rad * (1.5 + random.nextDouble() * 0.8)) + 2;
        ShardShape s = new ShardShape(rad, h, (random.nextDouble() - 0.5) * 0.5, (random.nextDouble() - 0.5) * 0.5,
                random.nextInt(3) == 0, random.nextDouble() * 6.3);
        int tip = ground + lift, top = tip + h - 1;
        if (top >= level.getMaxY() - 2) return false;
        Voxels v = new Voxels(10, 0, h + 1);
        long seed = random.nextLong();
        s.fill(v, seed);
        v.seal();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        // all of it in open air, or nothing
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                for (int y = 0; y <= h; y++) {
                    if (v.get(dx, y, dz) != Voxels.EMPTY && !level.getBlockState(m.set(x + dx, tip + y, z + dz)).isAir()) return false;
                }
            }
        }
        BlockState[] palette = palette();
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                for (int y = 0; y <= h; y++) {
                    byte c = v.get(dx, y, dz);
                    if (c != Voxels.EMPTY) level.setBlock(m.set(x + dx, tip + y, z + dz), palette[c], Block.UPDATE_CLIENTS);
                }
            }
        }
        // a chip of rubble or two beside it
        for (int k = random.nextInt(3); k > 0; k--) {
            int bx = x + random.nextInt(9) - 4, by = tip + random.nextInt(h), bz = z + random.nextInt(9) - 4;
            if (level.getBlockState(m.set(bx, by, bz)).isAir() && level.getBlockState(m.set(bx, by - 1, bz)).isAir()
                    && level.getBlockState(m.set(bx, by + 1, bz)).isAir()) {
                level.setBlock(m.set(bx, by, bz), palette[random.nextBoolean() ? ShardShape.FAULT : ShardShape.SHALE], Block.UPDATE_CLIENTS);
            }
        }
        return true;
    }

    static BlockState[] palette() {
        return new BlockState[]{Blocks.AIR.defaultBlockState(),
                FaultBlocks.RIVEN_SHALE.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y),
                FaultBlocks.FAULTSTONE.get().defaultBlockState(), FaultBlocks.FAULT_SEAM.get().defaultBlockState(),
                Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState(), Blocks.HANGING_ROOTS.defaultBlockState(),
                Blocks.COARSE_DIRT.defaultBlockState()};
    }

    /**
     * An upturned shard's shape in voxels (y 0 = its tip): layers from the top down narrowing to a point along a
     * leaning axis, the outline lobed so no two are alike. The same shape the Shard Drift's pieces have.
     */
    record ShardShape(double radius, int height, double tiltX, double tiltZ, boolean grassy, double phase) {
        static final byte SHALE = 1, FAULT = 2, SEAM = 3, GRASS = 4, DIRT = 5, ROOTS = 6, COARSE = 7;

        void fill(Voxels v, long seed) {
            int top = height - 1;
            for (int k = 0; k < height; k++) {
                int y = top - k;
                double t = k / (double) height;
                double rad = radius * Math.pow(1 - t, 0.85);
                double cx = tiltX * k, cz = tiltZ * k;
                int span = (int) Math.ceil(rad * 1.4) + 1;
                boolean any = false;
                for (int x = (int) Math.floor(cx) - span; x <= (int) Math.ceil(cx) + span; x++) {
                    for (int z = (int) Math.floor(cz) - span; z <= (int) Math.ceil(cz) + span; z++) {
                        double dx = x + 0.5 - cx, dz = z + 0.5 - cz;
                        double a = Math.atan2(dz, dx);
                        double edge = rad * (1 + 0.2 * Math.sin(3 * a + phase) + 0.1 * Math.sin(5 * a + phase * 2.3))
                                + (Voxels.hash(seed, x, y, z) - 0.5) * 0.5;
                        if (dx * dx + dz * dz > edge * edge) continue;
                        any = true;
                        byte code;
                        if (k == height - 1) code = SEAM; // the tip: the seam's light
                        else if (grassy && k == 0) code = GRASS;
                        else if (grassy && k == 1) code = DIRT;
                        else if (k == height - 1 || t > 0.84) code = SEAM;
                        else if (t > 0.5 || Voxels.hash(seed + 1, x, y, z) < 0.1 + 0.25 * t) code = FAULT;
                        else code = SHALE;
                        v.set(x, y, z, code);
                    }
                }
                if (!any) v.set((int) Math.floor(cx), y, (int) Math.floor(cz), k == height - 1 || t > 0.8 ? SEAM : FAULT);
            }
        }
    }
}
