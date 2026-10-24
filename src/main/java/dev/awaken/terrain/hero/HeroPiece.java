package dev.awaken.terrain.hero;

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
import net.minecraft.world.level.material.Fluids;

/**
 * A whole hero tree as one piece. Its box is reserved from the blueprint's own bounds (plus the ground cover round
 * its foot and the depth its trunk and roots may reach down), and every chunk writes only its own part of it. It only
 * ever replaces air, plants, water and leaves: never logs (another tree's trunk), never ground or anything built; its
 * hanging strands end at the water's surface.
 */
public class HeroPiece extends StructurePiece {
    /** How far trunk and root columns may reach down from the blueprint to find the ground. */
    static final int FILL = 24;

    private final HeroTree tree;
    private final BlockPos centre;
    private final long seed;

    public HeroPiece(HeroTree tree, BlockPos centre, long seed) {
        super(HeroWorldgen.PIECE.get(), 0, box(tree, centre, seed));
        this.tree = tree;
        this.centre = centre.immutable();
        this.seed = seed;
        setOrientation(null);
    }

    public HeroPiece(CompoundTag tag) {
        super(HeroWorldgen.PIECE.get(), tag);
        HeroTree t = HeroTree.ELDER_COLOSSUS;
        String id = tag.getStringOr("tree", t.getSerializedName());
        for (HeroTree v : HeroTree.values()) if (v.getSerializedName().equals(id)) t = v;
        this.tree = t;
        this.centre = new BlockPos(tag.getIntOr("cx", 0), tag.getIntOr("cy", 64), tag.getIntOr("cz", 0));
        this.seed = tag.getLongOr("seed", 0L);
        setOrientation(null);
    }

    static BoundingBox box(HeroTree tree, BlockPos c, long seed) {
        int[] b = HeroWorldgen.blueprint(tree, seed).bounds();
        int g = tree.groundRadius;
        return new BoundingBox(c.getX() + Math.min(b[0], -g), c.getY() + b[1] - FILL - 1, c.getZ() + Math.min(b[2], -g),
                c.getX() + Math.max(b[3], g), c.getY() + b[4] + 1, c.getZ() + Math.max(b[5], g));
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString("tree", tree.getSerializedName());
        tag.putInt("cx", centre.getX());
        tag.putInt("cy", centre.getY());
        tag.putInt("cz", centre.getZ());
        tag.putLong("seed", seed);
    }

    public HeroTree tree() {
        return tree;
    }

    /** The trunk's foot: x and z of its centre, y of the first air above the ground there. */
    public BlockPos centre() {
        return centre;
    }

    public long seed() {
        return seed;
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
        build(level, chunkBB);
    }

    /** What may give way to the tree: air, plants, snow, water and leaves; never logs, ground or anything built. */
    static boolean soft(BlockState s) {
        if (s.isAir()) return true;
        if (s.is(BlockTags.LOGS)) return false;
        return s.canBeReplaced() || s.is(BlockTags.LEAVES) || s.is(BlockTags.REPLACEABLE_BY_TREES);
    }

    private static boolean soil(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.PODZOL) || s.is(Blocks.MOSS_BLOCK);
    }

    /** Builds the part of the tree inside {@code clip} (worldgen: one chunk; commands and tests: everything). Returns the blocks set. */
    public int build(WorldGenLevel level, BoundingBox clip) {
        Blueprint bp = HeroWorldgen.blueprint(tree, seed);
        BoundingBox box = getBoundingBox();
        int x0 = Math.max(clip.minX(), box.minX()), x1 = Math.min(clip.maxX(), box.maxX());
        int z0 = Math.max(clip.minZ(), box.minZ()), z1 = Math.min(clip.maxZ(), box.maxZ());
        int y0 = Math.max(clip.minY(), box.minY()), y1 = Math.min(clip.maxY(), box.maxY());
        if (x0 > x1 || z0 > z1 || y0 > y1) return 0;
        int cx = centre.getX(), cy = centre.getY(), cz = centre.getZ();
        Heightmap.Types surface = level instanceof ServerLevel ? Heightmap.Types.WORLD_SURFACE : Heightmap.Types.WORLD_SURFACE_WG;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int placed = 0;

        // the ground cover round the foot first, while these columns hold none of the tree yet
        int g = tree.groundRadius;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int dx = x - cx, dz = z - cz;
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > g) continue;
                int top = Math.min(level.getHeight(surface, x, z) - 1, cy + 16);
                double n = Blueprint.hash(seed, x, 7, z);
                if (level.getFluidState(m.set(x, top, z)).isSourceOfType(Fluids.WATER) && top + 1 <= y1 && level.getBlockState(m.set(x, top + 1, z)).isAir()) {
                    // open water: what floats there
                    BlockState floats = tree.onWater(dist, n);
                    if (floats != null) {
                        level.setBlock(m, floats, Block.UPDATE_CLIENTS);
                        placed++;
                    }
                    continue;
                }
                int gy = Integer.MIN_VALUE;
                for (int y = top; y >= cy - 16 && y >= y0; y--) {
                    BlockState s = level.getBlockState(m.set(x, y, z));
                    if (s.is(BlockTags.LOGS) || !s.getFluidState().isEmpty()) break;
                    if (soil(s)) {
                        gy = y;
                        break;
                    }
                    if (!soft(s)) break;
                }
                if (gy == Integer.MIN_VALUE || gy + 1 > y1) continue;
                double patch = (Blueprint.hash(seed, x >> 2, 0, z >> 2) + Blueprint.hash(seed, (x + 2) >> 2, 1, (z + 2) >> 2)) * 0.5;
                BlockState[] cover = tree.cover(dist, n, patch);
                if (cover[0] != null) level.setBlock(m.set(x, gy, z), cover[0], Block.UPDATE_CLIENTS);
                if (cover[1] != null && level.getBlockState(m.set(x, gy + 1, z)).isAir()) {
                    level.setBlock(m, cover[1], Block.UPDATE_CLIENTS);
                    placed++;
                }
            }
        }

        // the tree, column by column; trunk and root columns reach down to the ground below the blueprint
        int e = bp.extent;
        for (int x = x0; x <= x1; x++) {
            int bx = x - cx;
            if (bx < -e || bx > e) continue;
            for (int z = z0; z <= z1; z++) {
                int bz = z - cz;
                if (bz < -e || bz > e) continue;
                int col = bp.column(bx, bz);
                int lo = Math.max(bp.colLo[col], y0 - cy), hi = Math.min(bp.colHi[col], y1 - cy);
                for (int y = lo; y <= hi; y++) {
                    byte code = bp.cells[bp.index(bx, y, bz)];
                    if (code == Blueprint.EMPTY) continue;
                    m.set(x, cy + y, z);
                    BlockState was = level.getBlockState(m);
                    if (!soft(was)) continue;
                    if (code == Blueprint.VINE || code == Blueprint.VINE_TIP) {
                        // strands end at the water: none under it, and the last one above it is a tip
                        if (!was.getFluidState().isEmpty()) continue;
                        if (code == Blueprint.VINE && !level.getFluidState(m.below()).isEmpty()) code = Blueprint.VINE_TIP;
                    }
                    level.setBlock(m, tree.state(code), Block.UPDATE_CLIENTS);
                    placed++;
                }
                int foot = bp.foot[col];
                if (foot == Blueprint.NO_FOOT) continue;
                BlockState root = tree.state(bp.cells[bp.index(bx, foot, bz)]);
                for (int y = cy + foot - 1; y >= cy + foot - FILL && y >= y0; y--) {
                    m.set(x, y, z);
                    BlockState s = level.getBlockState(m);
                    if (!soft(s)) break;
                    level.setBlock(m, root, Block.UPDATE_CLIENTS);
                    placed++;
                }
            }
        }
        return placed;
    }
}
