package dev.awaken.above.ruin;

import dev.awaken.above.AboveWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.common.world.PieceBeardifierModifier;

/**
 * One whole Precursor ruin as a single code-built piece. Its bounding box is the ruin's footprint; each chunk it touches
 * rebuilds the deterministic blueprint ({@link RuinBuilder}) and keeps only the blocks inside that chunk. Vaults and
 * monoliths flatten the terrain around themselves (NeoForge {@link PieceBeardifierModifier}).
 */
public class PrecursorRuinPiece extends StructurePiece implements PieceBeardifierModifier {
    private final RuinKind kind;
    private final long seed;
    private final int rotation;
    private final int doorRank;
    /** Which blueprint built it ({@link RuinBuilder#STYLE}); pieces saved before the redesign have none. */
    private final int style;

    public PrecursorRuinPiece(RuinKind kind, BlockPos corner, int rotation, long seed, int doorRank) {
        super(AboveWorldgen.RUIN_PIECE.get(), 0, box(kind, corner));
        this.kind = kind;
        this.seed = seed;
        this.rotation = rotation & 3;
        this.doorRank = doorRank;
        this.style = RuinBuilder.STYLE;
        setOrientation(null);
    }

    public PrecursorRuinPiece(CompoundTag tag) {
        super(AboveWorldgen.RUIN_PIECE.get(), tag);
        RuinKind k = RuinKind.byId(tag.getStringOr("kind", "vault"));
        this.kind = k == null ? RuinKind.VAULT : k;
        this.seed = tag.getLongOr("seed", 0L);
        this.rotation = tag.getIntOr("rot", 0) & 3;
        this.doorRank = tag.getIntOr("rank", 1);
        this.style = tag.getIntOr("style", 1);
        setOrientation(null);
    }

    static BoundingBox box(RuinKind kind, BlockPos corner) {
        RuinBuilder.Size s = RuinBuilder.size(kind);
        return new BoundingBox(corner.getX(), corner.getY(), corner.getZ(), corner.getX() + s.x() - 1, corner.getY() + s.y() - 1,
                corner.getZ() + s.z() - 1);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString("kind", kind.id);
        tag.putLong("seed", seed);
        tag.putInt("rot", rotation);
        tag.putInt("rank", doorRank);
        tag.putInt("style", style);
    }

    public RuinKind kind() {
        return kind;
    }

    private Rotation rot() {
        return switch (rotation) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /** Local (x, z) to world, rotating inside the square footprint. */
    private BlockPos.MutableBlockPos world(int x, int y, int z, BlockPos.MutableBlockPos out) {
        int w = boundingBox.getXSpan() - 1;
        int lx, lz;
        switch (rotation) {
            case 1 -> { lx = w - z; lz = x; }
            case 2 -> { lx = w - x; lz = w - z; }
            case 3 -> { lx = z; lz = w - x; }
            default -> { lx = x; lz = z; }
        }
        return out.set(boundingBox.minX() + lx, boundingBox.minY() + y, boundingBox.minZ() + lz);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
        // a ruin started before the redesign keeps what it already built: its unbuilt chunks don't get the new blueprint
        // (that would weld two buildings together at a chunk edge)
        if (style < RuinBuilder.STYLE) return;
        Rotation rot = rot();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        RuinBuilder.build(kind, seed, doorRank, new RuinBuilder.Sink() {
            @Override
            public void set(int x, int y, int z, BlockState state) {
                world(x, y, z, m);
                if (!chunkBB.isInside(m)) return;
                level.setBlock(m, state.rotate(rot), Block.UPDATE_CLIENTS);
            }

            @Override
            public void fillDown(int x, int y, int z, BlockState state, int depth) {
                world(x, y, z, m);
                if (!chunkBB.isInside(m.getX(), chunkBB.minY(), m.getZ())) return;
                for (int i = 0; i < depth && m.getY() > level.getMinY(); i++) {
                    BlockState here = level.getBlockState(m);
                    if (!here.isAir() && here.getFluidState().isEmpty() && !here.canBeReplaced()) break;
                    level.setBlock(m, state, Block.UPDATE_CLIENTS);
                    m.move(Direction.DOWN);
                }
            }

            @Override
            public void chest(int x, int y, int z, Direction facing, ResourceKey<LootTable> table) {
                world(x, y, z, m);
                if (!chunkBB.isInside(m)) return;
                BlockPos at = m.immutable();
                level.setBlock(at, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, rot.rotate(facing)), Block.UPDATE_CLIENTS);
                RandomizableContainer.setBlockEntityLootTable(level, random, at, table);
            }
        });
    }

    // ---- terrain adaptation ---------------------------------------------------------------------------------------

    @Override
    public BoundingBox getBeardifierBox() {
        if (kind == RuinKind.MONOLITH) {
            return new BoundingBox(boundingBox.minX(), boundingBox.minY() + 6, boundingBox.minZ(), boundingBox.maxX(), boundingBox.maxY(),
                    boundingBox.maxZ());
        }
        return boundingBox;
    }

    @Override
    public TerrainAdjustment getTerrainAdjustment() {
        return kind.isVault() || kind == RuinKind.MONOLITH ? TerrainAdjustment.BEARD_THIN : TerrainAdjustment.NONE;
    }

    @Override
    public int getGroundLevelDelta() {
        return 1;
    }
}
