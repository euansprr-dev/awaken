package dev.awaken.terrain.landmark;

import dev.awaken.terrain.block.CrystalClusterBlock;
import dev.awaken.terrain.frost.FrostBlocks;
import dev.awaken.terrain.glass.GlassBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * A whole landmark as one piece. Its box is reserved from the shape's own bounds (and, for a Gate Scar or an Aurora
 * Spire, the ground it reshapes round it); every chunk writes only its own part. The Gate Scar and the Aurora Spire
 * first reshape their ground column by column (each column from its own ground and its distance from the centre, so
 * chunks agree without seeing each other), then every landmark places its voxels into air, plants, snow and water only:
 * never into ground or anything built.
 */
public class LandmarkPiece extends StructurePiece {
    /** How far a standing column may reach down to find the ground. */
    static final int FILL = 12;
    /** How far the Aurora Spire's tarn may cut into the ground or build up over it to lie level. */
    static final int TARN_DEPTH = 5;

    private final Landmark landmark;
    private final BlockPos centre;
    private final long seed;
    private final float angle;
    private final int[] ground;

    private LandmarkPiece(Landmark landmark, BlockPos centre, long seed, float angle, int[] ground) {
        super(LandmarkWorldgen.PIECE.get(), 0, box(landmark, centre, seed, angle, ground));
        this.landmark = landmark;
        this.centre = centre.immutable();
        this.seed = seed;
        this.angle = angle;
        this.ground = ground;
        setOrientation(null);
    }

    public static LandmarkPiece scar(BlockPos centre, long seed) {
        return new LandmarkPiece(Landmark.GATE_SCAR, centre, seed, 0F, new int[0]);
    }

    public static LandmarkPiece drift(BlockPos centre, long seed, float angle, int[] ground) {
        return new LandmarkPiece(Landmark.SHARD_DRIFT, centre, seed, angle, ground);
    }

    public static LandmarkPiece spire(BlockPos centre, long seed) {
        return new LandmarkPiece(Landmark.AURORA_SPIRE, centre, seed, 0F, new int[0]);
    }

    public LandmarkPiece(CompoundTag tag) {
        super(LandmarkWorldgen.PIECE.get(), tag);
        Landmark l = Landmark.GATE_SCAR;
        String id = tag.getStringOr("landmark", l.getSerializedName());
        for (Landmark v : Landmark.values()) if (v.getSerializedName().equals(id)) l = v;
        this.landmark = l;
        this.centre = new BlockPos(tag.getIntOr("cx", 0), tag.getIntOr("cy", 64), tag.getIntOr("cz", 0));
        this.seed = tag.getLongOr("seed", 0L);
        this.angle = tag.getFloatOr("angle", 0F);
        this.ground = tag.getIntArray("ground").orElse(new int[0]);
        setOrientation(null);
    }

    static BoundingBox box(Landmark landmark, BlockPos c, long seed, float angle, int[] ground) {
        return switch (landmark) {
            case GATE_SCAR -> {
                GateScar.Plan p = LandmarkWorldgen.scar(seed);
                int[] b = p.voxels.bounds();
                int g = p.groundReach();
                int down = (int) Math.ceil(p.depth) + 4;
                yield new BoundingBox(c.getX() + Math.min(b[0], -g), c.getY() + Math.min(b[1], -down) - FILL, c.getZ() + Math.min(b[2], -g),
                        c.getX() + Math.max(b[3], g), c.getY() + Math.max(b[4], (int) Math.ceil(p.rimHeight) + 12) + 1, c.getZ() + Math.max(b[5], g));
            }
            case SHARD_DRIFT -> {
                int[] b = LandmarkWorldgen.drift(seed, angle, ground).bounds();
                yield new BoundingBox(c.getX() + b[0], c.getY() + b[1] - 1, c.getZ() + b[2], c.getX() + b[3], c.getY() + b[4] + 1, c.getZ() + b[5]);
            }
            case AURORA_SPIRE -> {
                AuroraSpire.Plan p = LandmarkWorldgen.spire(seed);
                int[] b = p.voxels.bounds();
                int g = p.groundReach();
                // the tarn is cut and filled up to TARN_DEPTH round the centre's ground
                yield new BoundingBox(c.getX() + Math.min(b[0], -g), c.getY() + Math.min(b[1], -TARN_DEPTH) - FILL, c.getZ() + Math.min(b[2], -g),
                        c.getX() + Math.max(b[3], g), c.getY() + Math.max(b[4], TARN_DEPTH) + 1, c.getZ() + Math.max(b[5], g));
            }
        };
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString("landmark", landmark.getSerializedName());
        tag.putInt("cx", centre.getX());
        tag.putInt("cy", centre.getY());
        tag.putInt("cz", centre.getZ());
        tag.putLong("seed", seed);
        tag.putFloat("angle", angle);
        tag.putIntArray("ground", ground);
    }

    public Landmark landmark() {
        return landmark;
    }

    /** The centre: x and z, y of the first air over the ground there. */
    public BlockPos centre() {
        return centre;
    }

    public long seed() {
        return seed;
    }

    Voxels voxels() {
        return switch (landmark) {
            case GATE_SCAR -> LandmarkWorldgen.scar(seed).voxels;
            case SHARD_DRIFT -> LandmarkWorldgen.drift(seed, angle, ground);
            case AURORA_SPIRE -> LandmarkWorldgen.spire(seed).voxels;
        };
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
        long t0 = System.nanoTime();
        build(level, chunkBB);
        LandsClock.add(landmark.getSerializedName() + " chunk", System.nanoTime() - t0);
    }

    /** What a landmark's voxels may replace: air, plants, snow, water and leaves; never logs, ground or anything built. */
    static boolean soft(BlockState s) {
        if (s.isAir()) return true;
        if (s.is(BlockTags.LOGS)) return false;
        return s.canBeReplaced() || s.is(BlockTags.LEAVES) || s.is(BlockTags.REPLACEABLE_BY_TREES);
    }

    /** Builds the part of the landmark inside {@code clip} (worldgen: one chunk; commands and tests: everything). Returns the blocks set. */
    public int build(WorldGenLevel level, BoundingBox clip) {
        BoundingBox box = getBoundingBox();
        int x0 = Math.max(clip.minX(), box.minX()), x1 = Math.min(clip.maxX(), box.maxX());
        int z0 = Math.max(clip.minZ(), box.minZ()), z1 = Math.min(clip.maxZ(), box.maxZ());
        int y0 = Math.max(clip.minY(), box.minY()), y1 = Math.min(clip.maxY(), box.maxY());
        if (x0 > x1 || z0 > z1 || y0 > y1) return 0;
        int placed = switch (landmark) {
            case GATE_SCAR -> shapeScar(level, LandmarkWorldgen.scar(seed), x0, x1, z0, z1, y0, y1);
            case AURORA_SPIRE -> freezeTarn(level, LandmarkWorldgen.spire(seed), x0, x1, z0, z1, y0, y1);
            case SHARD_DRIFT -> 0;
        };
        return placed + placeVoxels(level, voxels(), x0, x1, z0, z1, y0, y1);
    }

    private int placeVoxels(WorldGenLevel level, Voxels v, int x0, int x1, int z0, int z1, int y0, int y1) {
        int cx = centre.getX(), cy = centre.getY(), cz = centre.getZ(), e = v.extent, placed = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) {
            int bx = x - cx;
            if (bx < -e || bx > e) continue;
            for (int z = z0; z <= z1; z++) {
                int bz = z - cz;
                if (bz < -e || bz > e) continue;
                int col = v.column(bx, bz);
                int lo = Math.max(v.colLo[col], y0 - cy), hi = Math.min(v.colHi[col], y1 - cy);
                for (int y = lo; y <= hi; y++) {
                    byte code = v.cells[v.index(bx, y, bz)];
                    if (code == Voxels.EMPTY) continue;
                    m.set(x, cy + y, z);
                    if (!soft(level.getBlockState(m))) continue;
                    level.setBlock(m, landmark.state(code), Block.UPDATE_CLIENTS);
                    placed++;
                }
                int foot = v.foot[col];
                if (foot == Voxels.NO_FOOT) continue;
                BlockState base = landmark.state(v.cells[v.index(bx, foot, bz)]);
                if (base.isAir() || base.getBlock() instanceof CrystalClusterBlock) continue;
                for (int y = cy + foot - 1; y >= cy + foot - FILL && y >= y0; y--) {
                    m.set(x, y, z);
                    if (!soft(level.getBlockState(m))) break;
                    level.setBlock(m, base, Block.UPDATE_CLIENTS);
                    placed++;
                }
            }
        }
        return placed;
    }

    /**
     * The Gate Scar's ground, column by column: inside the rim the bowl, cut or filled to its profile under the
     * centre's ground and glassed (black at the heart, radial cracks of black running out through the ivory glass);
     * the rim rising over the column's own ground; beyond it rays of splashed glass across the grass.
     */
    private int shapeScar(WorldGenLevel level, GateScar.Plan p, int x0, int x1, int z0, int z1, int y0, int y1) {
        int cx = centre.getX(), cz = centre.getZ(), groundTop = centre.getY() - 1;
        int reach = p.groundReach();
        Heightmap.Types surface = level instanceof ServerLevel ? Heightmap.Types.WORLD_SURFACE : Heightmap.Types.WORLD_SURFACE_WG;
        BlockState fused = GlassBlocks.FUSED_GLASS.get().defaultBlockState(), scorched = GlassBlocks.SCORCHED_GLASS.get().defaultBlockState();
        BlockState cluster = GlassBlocks.SUNSHARD_CLUSTER.get().defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int placed = 0;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                double dx = x - cx, dz = z - cz, r = Math.sqrt(dx * dx + dz * dz);
                if (r > reach) continue;
                double a = Math.atan2(dz, dx);
                double ray = p.ray(a, r);
                boolean inRim = r < p.radius + p.rimWidth;
                if (!inRim && ray < 0.25) continue;
                // the column's own ground: the first solid block under any plants, snow or water (a pool or lake inside
                // the rim is cut away with the rest; beyond it the rays leave water alone)
                int top = level.getHeight(surface, x, z) - 1;
                int g = Integer.MIN_VALUE;
                boolean wet = false;
                for (int y = top; y >= top - 10 && y >= level.getMinY(); y--) {
                    BlockState s = level.getBlockState(m.set(x, y, z));
                    if (!s.getFluidState().isEmpty()) wet = true;
                    if (!soft(s)) {
                        g = y;
                        break;
                    }
                }
                if (g == Integer.MIN_VALUE || (wet && !inRim)) continue;
                double h = Voxels.hash(p.seed, x, 0, z);
                if (!inRim) {
                    // a ray of splashed glass: the top glassed where the ground takes it, slivers standing in it
                    if (g < y0 || g > y1) continue;
                    BlockState s = level.getBlockState(m.set(x, g, z));
                    if (!(s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.GRAVEL))) continue;
                    if (h > 0.25 + ray) continue; // its edge breaks up
                    level.setBlock(m, fused, Block.UPDATE_CLIENTS);
                    placed++;
                    if (Voxels.hash(p.seed + 9, x, 0, z) < 0.08 && level.getBlockState(m.set(x, g + 1, z)).isAir() && g + 1 <= y1) {
                        level.setBlock(m, cluster, Block.UPDATE_CLIENTS);
                        placed++;
                    }
                    continue;
                }
                int target;
                if (r < p.radius) {
                    // the bowl under the centre's ground, easing onto this column's own ground over the last blocks
                    double bowl = groundTop + p.floor(r);
                    double t = Math.max(0, Math.min(1, (r - (p.radius - 3)) / 3.0));
                    target = (int) Math.round(bowl + (g - bowl) * t * t * (3 - 2 * t));
                } else {
                    target = g + (int) Math.round(p.rim(r));
                }
                // cut down to the target, or fill up to it with glass
                for (int y = Math.max(top, g); y > target; y--) {
                    if (y < y0 || y > y1) continue;
                    if (!level.getBlockState(m.set(x, y, z)).isAir()) {
                        level.setBlock(m, air, Block.UPDATE_CLIENTS);
                        placed++;
                    }
                }
                byte mat = GateScar.material(p, a, r, h);
                BlockState skin = mat == GateScar.SCORCHED ? scorched : fused;
                boolean outerRim = r > p.radius + p.rimWidth * 0.55;
                BlockState topState = skin;
                if (outerRim && h < (r - p.radius - p.rimWidth * 0.55) / (p.rimWidth * 0.45)) {
                    BlockState was = level.getBlockState(m.set(x, g, z));
                    topState = was.is(Blocks.GRASS_BLOCK) || was.is(BlockTags.DIRT) ? Blocks.GRASS_BLOCK.defaultBlockState() : skin;
                }
                int skinDepth = r < p.radius ? 3 : 1;
                for (int y = target; y > target - skinDepth || y > g; y--) {
                    if (y < y0 || y > y1) continue;
                    level.setBlock(m.set(x, y, z), y == target ? topState : skin, Block.UPDATE_CLIENTS);
                    placed++;
                }
                // slivers of sunshard in the glass
                if (target + 1 <= y1 && target + 1 >= y0 && Voxels.hash(p.seed + 11, x, 0, z) < (r < p.radius ? 0.035 : 0.06)
                        && level.getBlockState(m.set(x, target + 1, z)).isAir() && !topState.is(Blocks.GRASS_BLOCK)) {
                    level.setBlock(m, cluster, Block.UPDATE_CLIENTS);
                    placed++;
                }
            }
        }
        return placed;
    }

    /**
     * The Aurora Spire's ground, column by column: inside the tarn, packed ice level with the centre's ground (cut down
     * to it or built up to it from the column's own ground, at most {@link #TARN_DEPTH}); round it a ring of hoarfrost
     * on the column's own ground, its snow cleared.
     */
    private int freezeTarn(WorldGenLevel level, AuroraSpire.Plan p, int x0, int x1, int z0, int z1, int y0, int y1) {
        int cx = centre.getX(), cz = centre.getZ(), level0 = centre.getY() - 1;
        int reach = p.groundReach();
        Heightmap.Types surface = Voxels.surface(level);
        BlockState ice = Blocks.PACKED_ICE.defaultBlockState(), frost = FrostBlocks.HOARFROST.get().defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int placed = 0;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                double dx = x - cx, dz = z - cz, r = Math.sqrt(dx * dx + dz * dz);
                if (r > reach) continue;
                boolean tarn = r < p.tarn;
                if (!tarn && r >= p.tarn + AuroraSpire.RING + (Voxels.hash(p.seed, x, 0, z) - 0.5) * 2) continue;
                int top = level.getHeight(surface, x, z) - 1;
                int g = Integer.MIN_VALUE;
                for (int y = top; y >= top - 10 && y >= level.getMinY(); y--) {
                    if (!soft(level.getBlockState(m.set(x, y, z)))) {
                        g = y;
                        break;
                    }
                }
                if (g == Integer.MIN_VALUE) continue;
                if (!tarn) {
                    // the ring: hoarfrost on the column's own ground, its snow and plants cleared
                    BlockState was = level.getBlockState(m.set(x, g, z));
                    boolean takes = was.is(BlockTags.DIRT) || was.is(Blocks.GRASS_BLOCK) || was.is(Blocks.SNOW_BLOCK) || was.is(BlockTags.BASE_STONE_OVERWORLD);
                    if (g < y0 || g > y1 || !takes) continue;
                    level.setBlock(m, frost, Block.UPDATE_CLIENTS);
                    placed++;
                    for (int y = g + 1; y <= top && y <= y1; y++) {
                        if (!level.getBlockState(m.set(x, y, z)).isAir()) {
                            level.setBlock(m, air, Block.UPDATE_CLIENTS);
                            placed++;
                        }
                    }
                    continue;
                }
                if (Math.abs(g - level0) > TARN_DEPTH) continue; // a cliff under the rim: leave it be
                // cut down to the ice, or build up to it with ice from the column's own ground
                for (int y = Math.max(top, g); y > level0; y--) {
                    if (y < y0 || y > y1 || level.getBlockState(m.set(x, y, z)).isAir()) continue;
                    level.setBlock(m, air, Block.UPDATE_CLIENTS);
                    placed++;
                }
                // at least two layers thick, down to the column's own ground where it lies lower
                int bottom = Math.max(Math.min(g, level0 - 1), level0 - TARN_DEPTH);
                for (int y = level0; y >= bottom; y--) {
                    if (y < y0 || y > y1) continue;
                    level.setBlock(m.set(x, y, z), ice, Block.UPDATE_CLIENTS);
                    placed++;
                }
            }
        }
        return placed;
    }
}
