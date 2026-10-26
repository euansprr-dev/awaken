package dev.awaken.village.world;

import dev.awaken.village.plan.VillagePlan;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.neoforge.common.world.PieceBeardifierModifier;

/**
 * A building's, field's or pen's footprint, for the land only (SPEC §28.6): the beardifier eases the ground up to its
 * floor and away above it (a thin beard), so the planned ground meets the land without walls of dirt. Paints nothing.
 */
public class VillageMarker extends StructurePiece implements PieceBeardifierModifier {
    public VillageMarker(BoundingBox box) {
        super(VillageWorldgen.MARKER.get(), 0, box);
        setOrientation(null);
    }

    public VillageMarker(CompoundTag tag) {
        super(VillageWorldgen.MARKER.get(), tag);
        setOrientation(null);
    }

    static VillageMarker of(VillagePlan p, int x0, int z0, int x1, int z1, int lift) {
        int y = p.baseY + lift;
        return new VillageMarker(new BoundingBox(p.ox + x0, y, p.oz + z0, p.ox + x1, y + 6, p.oz + z1));
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {}

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {}

    @Override
    public BoundingBox getBeardifierBox() {
        return boundingBox;
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
