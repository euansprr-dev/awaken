package dev.awaken.world.fauna.lair;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.neoforge.common.world.PieceBeardifierModifier;

/**
 * A whole lair as one code-built piece ({@link LairBuilder}). Its box is the footprint with the scattered ring; the
 * beardifier flattens only the floor disc ({@link #getBeardifierBox}), so the ground round it stays natural. The
 * piece remembers its den, which the world module reads when the chunk loads (that is how a lair is known).
 */
public class LairPiece extends StructurePiece implements PieceBeardifierModifier {
    private final LairStyle style;
    private final BlockPos centre;
    private final long seed;
    private final int variant;
    /** Which blueprint built it ({@link LairBuilder#STYLE}); pieces saved before the redesign have none. */
    private final int look;

    public LairPiece(LairStyle style, BlockPos centre, long seed, int variant) {
        super(LairWorldgen.PIECE.get(), 0, box(centre));
        this.style = style;
        this.centre = centre.immutable();
        this.seed = seed;
        this.variant = variant;
        this.look = LairBuilder.STYLE;
        setOrientation(null);
    }

    public LairPiece(CompoundTag tag) {
        super(LairWorldgen.PIECE.get(), tag);
        LairStyle s = null;
        String id = tag.getStringOr("lair", "wallow");
        for (LairStyle v : LairStyle.values()) if (v.getSerializedName().equals(id)) s = v;
        this.style = s == null ? LairStyle.WALLOW : s;
        this.centre = new BlockPos(tag.getIntOr("cx", 0), tag.getIntOr("cy", 64), tag.getIntOr("cz", 0));
        this.seed = tag.getLongOr("seed", 0L);
        this.variant = tag.getIntOr("variant", 0);
        this.look = tag.getIntOr("look", 1);
        setOrientation(null);
    }

    static BoundingBox box(BlockPos c) {
        int e = LairStyle.EXTENT;
        return new BoundingBox(c.getX() - e, c.getY() - LairStyle.DEPTH, c.getZ() - e, c.getX() + e, c.getY() + LairStyle.HEIGHT, c.getZ() + e);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString("lair", style.getSerializedName());
        tag.putInt("cx", centre.getX());
        tag.putInt("cy", centre.getY());
        tag.putInt("cz", centre.getZ());
        tag.putLong("seed", seed);
        tag.putInt("variant", variant);
        tag.putInt("look", look);
    }

    public LairStyle style() {
        return style;
    }

    /** The floor layer's centre. */
    public BlockPos centre() {
        return centre;
    }

    /** Where the beast stands. */
    public BlockPos den() {
        return style.den(centre);
    }

    public int variant() {
        return variant;
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
        // a lair started before the redesign keeps what it already built (no new blueprint welded on at a chunk edge)
        if (look < LairBuilder.STYLE) return;
        build(level, chunkBB);
    }

    /** Builds the part of the lair inside {@code clip} (worldgen: one chunk; commands and tests: everything). */
    public void build(WorldGenLevel level, BoundingBox clip) {
        int cx = centre.getX(), fy = centre.getY(), cz = centre.getZ();
        // A finished world keeps the plain surface heightmap; worldgen keeps the _WG one.
        Heightmap.Types surface = level instanceof ServerLevel ? Heightmap.Types.WORLD_SURFACE : Heightmap.Types.WORLD_SURFACE_WG;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        LairBuilder.build(style, seed, variant, new LairBuilder.Sink() {
            @Override
            public void set(int x, int y, int z, BlockState state) {
                m.set(cx + x, fy + y, cz + z);
                if (clip.isInside(m)) level.setBlock(m, state, Block.UPDATE_CLIENTS);
            }

            @Override
            public int ground(int x, int z) {
                return level.getHeight(surface, cx + x, cz + z) - 1 - fy;
            }

            @Override
            public boolean here(int x, int z) {
                return clip.isInside(cx + x, clip.minY(), cz + z);
            }

            @Override
            public void fillDown(int x, int y, int z, BlockState state, int depth) {
                m.set(cx + x, fy + y, cz + z);
                if (!clip.isInside(m.getX(), clip.minY(), m.getZ())) return;
                for (int i = 0; i < depth && m.getY() > level.getMinY(); i++) {
                    BlockState here = level.getBlockState(m);
                    if (!here.isAir() && here.getFluidState().isEmpty() && !here.canBeReplaced()) break;
                    level.setBlock(m, state, Block.UPDATE_CLIENTS);
                    m.move(Direction.DOWN);
                }
            }
        });
    }

    // ---- terrain adaptation: flatten the floor disc only -------------------------------------------------------------

    @Override
    public BoundingBox getBeardifierBox() {
        int r = style.radius + 1;
        return new BoundingBox(centre.getX() - r, centre.getY(), centre.getZ() - r, centre.getX() + r, centre.getY() + LairStyle.HEIGHT, centre.getZ() + r);
    }

    @Override
    public TerrainAdjustment getTerrainAdjustment() {
        return TerrainAdjustment.BEARD_THIN;
    }

    @Override
    public int getGroundLevelDelta() {
        return 1;
    }
}
