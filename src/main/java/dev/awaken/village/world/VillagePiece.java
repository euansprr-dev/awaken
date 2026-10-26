package dev.awaken.village.world;

import dev.awaken.Awaken;
import dev.awaken.city.SettlementPaint;
import dev.awaken.village.plan.VillagePlan;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.neoforge.common.world.PieceBeardifierModifier;
import org.jspecify.annotations.Nullable;

/**
 * A settlement's whole plan as one piece (SPEC §28.6): its box is the plan's raster, and each chunk it touches paints its
 * own share through the city's painter, straight into the chunk's sections. The piece keeps its anchor (key, seed,
 * place, kind) and the planner's version, never the plan: after a restart the first chunk to paint makes the same plan
 * again from the land. It shapes no terrain itself (its markers do); its pens get their animals as they generate.
 */
public class VillagePiece extends StructurePiece implements PieceBeardifierModifier {
    private final SettlementGrid.Anchor anchor;
    private final int version;
    static long paintNanos, chunks, blocks;

    public VillagePiece(SettlementGrid.Anchor a, VillagePlan p) {
        super(VillageWorldgen.PIECE.get(), 0, new BoundingBox(p.ox - p.r, p.baseY - 24, p.oz - p.r, p.ox + p.r, p.baseY + 48, p.oz + p.r));
        this.anchor = a;
        this.version = VillagePlan.VERSION;
        setOrientation(null);
    }

    public VillagePiece(CompoundTag tag) {
        super(VillageWorldgen.PIECE.get(), tag);
        this.anchor = new SettlementGrid.Anchor(tag.getStringOr("key", "?"), tag.getIntOr("ax", 0), tag.getIntOr("az", 0),
                tag.getBooleanOr("village", false) ? VillagePlan.Kind.VILLAGE : VillagePlan.Kind.HAMLET, tag.getBooleanOr("first", false),
                tag.getLongOr("seed", 0L));
        this.version = tag.getIntOr("version", 1);
        setOrientation(null);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString("key", anchor.key());
        tag.putInt("ax", anchor.x());
        tag.putInt("az", anchor.z());
        tag.putBoolean("village", anchor.kind() == VillagePlan.Kind.VILLAGE);
        tag.putBoolean("first", anchor.first());
        tag.putLong("seed", anchor.seed());
        tag.putInt("version", version);
    }

    public SettlementGrid.Anchor anchor() {
        return anchor;
    }

    /** The plan (made again from the land if this server hasn't yet), or null if it no longer fits there. */
    public @Nullable VillagePlan plan(WorldGenLevel level, ChunkGenerator generator) {
        VillagePlan p = Plans.cached(anchor.key());
        if (p != null || Plans.known(anchor.key())) return p;
        ServerLevel sl = level.getLevel();
        return Plans.of(anchor, GenTerrain.of(generator, level, sl.getChunkSource().randomState()));
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
        // a settlement planned by another version of the planner keeps what it already painted
        if (version != VillagePlan.VERSION) return;
        VillagePlan p = plan(level, generator);
        if (p == null) return;
        long t0 = System.nanoTime();
        int n = SettlementPaint.paintChunk(p, chunkPos.x(), chunkPos.z(), level.getSeed(), level.getChunk(chunkPos.x(), chunkPos.z()));
        n += SettlementPaint.sweep(level, p, chunkPos.x(), chunkPos.z());
        paintNanos += System.nanoTime() - t0;
        chunks++;
        blocks += n;
        animals(level, p, chunkBB);
    }

    /** The beasts in each pen whose middle is in this chunk: two to five, kept (they belong to the place). */
    private static void animals(WorldGenLevel level, VillagePlan p, BoundingBox chunkBB) {
        for (int k = 0; k < p.pens.size(); k++) {
            VillagePlan.Pen pen = p.pens.get(k);
            int mx = p.ox + (pen.x0() + pen.x1()) / 2, mz = p.oz + (pen.z0() + pen.z1()) / 2, y = p.baseY + pen.lift() + 1;
            if (!chunkBB.isInside(mx, chunkBB.minY(), mz)) continue;
            EntityType<? extends Mob> type = switch (pen.beast()) {
                case "pig" -> EntityTypes.PIG;
                case "cow" -> EntityTypes.COW;
                case "chicken" -> EntityTypes.CHICKEN;
                case "goat" -> EntityTypes.GOAT;
                default -> EntityTypes.SHEEP;
            };
            int count = 2 + (int) Math.floorMod(p.seed + k * 31L, 4L);
            for (int i = 0; i < count; i++) {
                Mob m = type.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
                if (m == null) continue;
                double x = p.ox + pen.x0() + 1.5 + Math.floorMod(p.seed * 7 + i * 13L + k, (long) Math.max(1, pen.x1() - pen.x0() - 2));
                double z = p.oz + pen.z0() + 1.5 + Math.floorMod(p.seed * 11 + i * 5L + k, (long) Math.max(1, pen.z1() - pen.z0() - 2));
                m.setPersistenceRequired();
                m.snapTo(x, y, z, (i * 97) % 360, 0);
                BlockPos at = BlockPos.containing(x, y, z);
                m.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.STRUCTURE, null);
                level.addFreshEntityWithPassengers(m);
            }
        }
    }

    @Override
    public BoundingBox getBeardifierBox() {
        return boundingBox;
    }

    @Override
    public TerrainAdjustment getTerrainAdjustment() {
        return TerrainAdjustment.NONE;
    }

    @Override
    public int getGroundLevelDelta() {
        return 0;
    }

    static String stats() {
        return String.format(java.util.Locale.ROOT, "%d chunks painted, %.2f ms and %d blocks a chunk on average", chunks,
                chunks == 0 ? 0 : paintNanos / 1e6 / chunks, chunks == 0 ? 0 : blocks / chunks);
    }

    static {
        Awaken.LOG.debug("[village] piece class ready");
    }
}
