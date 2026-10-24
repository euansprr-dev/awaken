package dev.awaken.terrain.landmark;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.terrain.fault.FaultNoise;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.synth.Noise;

import java.util.Optional;

/**
 * A landmark (lands pack {@code worldgen/structure/<landmark>.json}, placed by a wide random spread). The start chunk
 * is the placement's; the landmark may stand up to {@link #SEARCH} chunks from it wherever its biome is and its ground
 * suits it: a Gate Scar wants dry, fairly level steppe high enough above the sea that its bowl stays dry; a Shard
 * Drift wants a fault line under it (it walks onto the line along the fault noise) on dry land.
 */
public class LandmarkStructure extends Structure {
    public static final MapCodec<LandmarkStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            Landmark.CODEC.fieldOf("landmark").forGetter(LandmarkStructure::landmark)
    ).apply(i, LandmarkStructure::new));

    static final int ATTEMPTS = 14, SEARCH = 4;

    private final Landmark landmark;

    public LandmarkStructure(StructureSettings settings, Landmark landmark) {
        super(settings);
        this.landmark = landmark;
    }

    public Landmark landmark() {
        return landmark;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        return switch (landmark) {
            case GATE_SCAR -> {
                BlockPos site = scarSite(ctx);
                if (site == null) yield Optional.empty();
                long seed = ctx.random().nextLong();
                yield Optional.of(new GenerationStub(site, b -> b.addPiece(LandmarkPiece.scar(site, seed))));
            }
            case SHARD_DRIFT -> {
                double[] site = driftSite(ctx);
                if (site == null) yield Optional.empty();
                long seed = ctx.random().nextLong();
                BlockPos centre = new BlockPos((int) site[0], (int) site[2], (int) site[1]);
                float angle = (float) site[3];
                int[] ground = groundUnder(ctx, centre, seed, angle);
                yield Optional.of(new GenerationStub(centre, b -> b.addPiece(LandmarkPiece.drift(centre, seed, angle, ground))));
            }
        };
    }

    /** Where a Gate Scar's centre goes (the first air over the ground there), or null. */
    static BlockPos scarSite(GenerationContext ctx) {
        ChunkPos c = ctx.chunkPos();
        WorldgenRandom rnd = ctx.random();
        int sea = ctx.chunkGenerator().getSeaLevel();
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            int x = c.getMiddleBlockX(), z = c.getMiddleBlockZ();
            if (attempt > 0) {
                x += (rnd.nextInt(SEARCH * 2 + 1) - SEARCH) * 16 + rnd.nextInt(9) - 4;
                z += (rnd.nextInt(SEARCH * 2 + 1) - SEARCH) * 16 + rnd.nextInt(9) - 4;
            }
            if (cityNear(x, z, Landmark.GATE_SCAR.reach)) continue;
            int top = height(ctx, x, z, Heightmap.Types.WORLD_SURFACE_WG);
            if (!biome(ctx, x, top, z)) continue;
            // the bowl's floor (up to ~11 down) must stay above the sea, and the ground must be dry
            if (top <= sea + 12 || height(ctx, x, z, Heightmap.Types.OCEAN_FLOOR_WG) != top) continue;
            boolean ok = true;
            for (int k = 0; k < 12 && ok; k++) {
                double a = k * Math.PI / 6;
                double d = k % 2 == 0 ? 16 : 28;
                int rx = x + (int) Math.round(Math.cos(a) * d), rz = z + (int) Math.round(Math.sin(a) * d);
                int h = height(ctx, rx, rz, Heightmap.Types.WORLD_SURFACE_WG);
                if (h != height(ctx, rx, rz, Heightmap.Types.OCEAN_FLOOR_WG)) ok = false; // water on the rim
                if (Math.abs(h - top) > (d < 20 ? 5 : 8)) ok = false; // a hillside, not a plain
            }
            if (!ok) continue;
            if (top + 50 > ctx.heightAccessor().getMaxY()) continue;
            return new BlockPos(x, top + 1, z);
        }
        return null;
    }

    /**
     * Where a Shard Drift hangs: x, z, the first air over the ground there, and the fault's direction (radians), or
     * null. From each try it walks down the fault noise's slope onto the line (a few Newton steps on |n|).
     */
    /** Why drift tries failed (studio and tests): biome at start, walked too far, off the line, city, biome at the end, wet, high. */
    static final java.util.concurrent.atomic.AtomicIntegerArray DRIFT_MISSES = new java.util.concurrent.atomic.AtomicIntegerArray(8);

    static double[] driftSite(GenerationContext ctx) {
        Noise fault = FaultNoise.fault(ctx.randomState());
        if (fault == null) return null;
        ChunkPos c = ctx.chunkPos();
        WorldgenRandom rnd = ctx.random();
        int sea = ctx.chunkGenerator().getSeaLevel();
        int mx = c.getMiddleBlockX(), mz = c.getMiddleBlockZ(), limit = SEARCH * 16 + 8;
        for (int attempt = 0; attempt < ATTEMPTS * 2; attempt++) {
            double x = mx, z = mz;
            if (attempt > 0) {
                x += (rnd.nextInt(SEARCH * 2 + 1) - SEARCH) * 16 + rnd.nextInt(9) - 4;
                z += (rnd.nextInt(SEARCH * 2 + 1) - SEARCH) * 16 + rnd.nextInt(9) - 4;
            }
            // the biome first (the cheap test), then the walk onto the line
            int sx = (int) Math.floor(x), sz = (int) Math.floor(z);
            if (!biome(ctx, sx, height(ctx, sx, sz, Heightmap.Types.WORLD_SURFACE_WG), sz)) {
                DRIFT_MISSES.incrementAndGet(0);
                continue;
            }
            double gx = 0, gz = 0;
            for (int step = 0; step < 12; step++) {
                double n = fault.get(x, 0, z);
                gx = (fault.get(x + 2, 0, z) - fault.get(x - 2, 0, z)) / 4;
                gz = (fault.get(x, 0, z + 2) - fault.get(x, 0, z - 2)) / 4;
                double g2 = gx * gx + gz * gz;
                if (g2 < 1e-12 || FaultNoise.distance(n, gx, gz) < 0.25) break;
                // a Newton step onto n = 0, at most 32 blocks
                double k = n / g2, len = Math.abs(n) / Math.sqrt(g2);
                if (len > 32) k *= 32 / len;
                x -= gx * k;
                z -= gz * k;
            }
            int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
            // it must stay near its start (the structure reaches chunks at most 8 from it) and be on the line
            if (Math.abs(ix - mx) > limit || Math.abs(iz - mz) > limit) {
                DRIFT_MISSES.incrementAndGet(1);
                continue;
            }
            if (FaultNoise.distance(fault, ix, iz) >= FaultNoise.HALF) {
                DRIFT_MISSES.incrementAndGet(2);
                continue;
            }
            if (cityNear(ix, iz, Landmark.SHARD_DRIFT.reach)) {
                DRIFT_MISSES.incrementAndGet(3);
                continue;
            }
            int top = height(ctx, ix, iz, Heightmap.Types.WORLD_SURFACE_WG);
            if (!biome(ctx, ix, top, iz)) {
                DRIFT_MISSES.incrementAndGet(4);
                continue;
            }
            if (top <= sea + 3 || height(ctx, ix, iz, Heightmap.Types.OCEAN_FLOOR_WG) != top) {
                DRIFT_MISSES.incrementAndGet(5);
                continue;
            }
            if (top + 80 > ctx.heightAccessor().getMaxY()) {
                DRIFT_MISSES.incrementAndGet(6);
                continue;
            }
            // the line runs across the noise's slope
            double angle = Math.atan2(gz, gx) + Math.PI / 2;
            return new double[]{ix, iz, top + 1, angle};
        }
        return null;
    }

    /** The first air over the ground under each shard of the drift, relative to the centre's. */
    static int[] groundUnder(GenerationContext ctx, BlockPos centre, long seed, float angle) {
        ShardDrift.Shard[] shards = ShardDrift.layout(seed, angle);
        int[] out = new int[shards.length];
        for (int i = 0; i < shards.length; i++) {
            int x = centre.getX() + (int) Math.round(shards[i].x()), z = centre.getZ() + (int) Math.round(shards[i].z());
            out[i] = height(ctx, x, z, Heightmap.Types.WORLD_SURFACE_WG) + 1 - centre.getY();
        }
        return out;
    }

    private static boolean biome(GenerationContext ctx, int x, int y, int z) {
        Holder<Biome> b = ctx.biomeResolver().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
        return ctx.validBiome().test(b);
    }

    /** The city reserve under the centre or under any edge of the landmark. */
    private static boolean cityNear(int x, int z, int reach) {
        if (dev.awaken.terrain.api.CityBasin.reserved(x, z)) return true;
        for (int[] k : new int[][]{{reach, 0}, {-reach, 0}, {0, reach}, {0, -reach}}) {
            if (dev.awaken.terrain.api.CityBasin.reserved(x + k[0], z + k[1])) return true;
        }
        return false;
    }

    private static int height(GenerationContext ctx, int x, int z, Heightmap.Types type) {
        return ctx.chunkGenerator().getFirstOccupiedHeight(x, z, type, ctx.heightAccessor(), ctx.randomState());
    }

    @Override
    public StructureType<?> type() {
        return LandmarkWorldgen.TYPE.get();
    }
}
