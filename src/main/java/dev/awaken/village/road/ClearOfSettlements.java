package dev.awaken.village.road;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.awaken.city.CityPlan;
import dev.awaken.city.SettlementPaint;
import dev.awaken.village.world.VillagePiece;
import dev.awaken.village.world.VillageStructure;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkDependencies;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * {@code awaken:clear_of_settlements} (SPEC §28.9, "Tall things keep off"): a tall feature tried here stands only where
 * nothing within its {@code reach} is land a road or a settlement uses. Those clear a few blocks over their ground, so
 * a spire they meet loses a slice and its top hangs in the air, and a neighbouring chunk's feature can land before the
 * paint as well as after it, so felling afterwards can't be made safe. Not standing there is the same in either order.
 * Goes last in a placed feature, once the position is known.
 */
public record ClearOfSettlements(int reach) implements PlacementModifier {
    public static final MapCodec<ClearOfSettlements> CODEC = Codec.intRange(0, 16).fieldOf("reach").xmap(ClearOfSettlements::new, ClearOfSettlements::reach);

    /** How far past its band a road's banks can grade ({@link RoadBanks#WIDTH}, rounded up). */
    public static final int BANKS = 8;

    /**
     * What the region decorating a chunk holds round it (vanilla's pyramid): references only out to {@link #REFERENCES}
     * (the chunks beside it), starts out to {@link #STARTS}. A reference beside it can name a start 8 chunks further on,
     * which is past the region: reading it threw "Requested chunk unavailable during world generation".
     */
    private static final ChunkDependencies HELD = ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FEATURES).directDependencies();
    static final int REFERENCES = HELD.getRadiusOf(ChunkStatus.STRUCTURE_REFERENCES), STARTS = HELD.getRadiusOf(ChunkStatus.STRUCTURE_STARTS);

    @Override
    public void modify(PlacementContext context, RandomSource random, BlockPos origin, Consumer<BlockPos> output) {
        if (!used(context.getLevel(), context.generator(), origin.getX(), origin.getZ(), reach)) output.accept(origin);
    }

    @Override
    public MapCodec<ClearOfSettlements> codec() {
        return CODEC;
    }

    /**
     * Whether a road or settlement referenced from a chunk under the footprint uses land within reach of (x, z), or might:
     * one whose start the region can't read keeps the feature off, as a road would.
     */
    static boolean used(WorldGenLevel level, ChunkGenerator generator, int x, int z, int reach) {
        int r = reach + BANKS;
        int x0 = (x - r) >> 4, x1 = (x + r) >> 4, z0 = (z - r) >> 4, z1 = (z + r) >> 4;
        // on a worldgen thread only the region may be read (the server's own view would wait on the chunk being made), and
        // it has references only beside the chunk being decorated: whatever could cut the feature's own blocks is named there
        ChunkPos centre = level instanceof WorldGenRegion region ? region.getCenter() : null;
        if (centre != null) {
            x0 = Math.max(x0, centre.x() - REFERENCES);
            x1 = Math.min(x1, centre.x() + REFERENCES);
            z0 = Math.max(z0, centre.z() - REFERENCES);
            z1 = Math.min(z1, centre.z() + REFERENCES);
        }
        List<StructureStart> seen = null;
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                for (Map.Entry<Structure, LongSet> refs : level.getChunk(cx, cz, ChunkStatus.STRUCTURE_REFERENCES).getAllReferences().entrySet()) {
                    Structure structure = refs.getKey();
                    if (!(structure instanceof RoadStructure || structure instanceof VillageStructure)) continue;
                    for (LongIterator it = refs.getValue().iterator(); it.hasNext(); ) {
                        long key = it.nextLong();
                        int sx = ChunkPos.getX(key), sz = ChunkPos.getZ(key);
                        // its start lies past what the region holds: its land can't be seen from here, so keep off it
                        if (centre != null && (centre.getChessboardDistance(sx, sz) > STARTS || !level.hasChunk(sx, sz))) return true;
                        StructureStart start = level.getChunk(sx, sz, ChunkStatus.STRUCTURE_STARTS).getStartForStructure(structure);
                        if (start == null || !start.isValid()) continue;
                        // a start is named from every chunk its box crosses: test each one once
                        if (seen == null) seen = new ArrayList<>(4);
                        else if (seen.contains(start)) continue;
                        seen.add(start);
                        for (var piece : start.getPieces()) {
                            SettlementPaint.Layout layout = piece instanceof RoadPiece road ? road.paint()
                                    : piece instanceof VillagePiece village ? village.plan(level, generator) : null;
                            if (layout != null && touches(layout, x, z, r)) return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Whether the layout uses any land within {@code r} of (x, z), in the world. Every other block is enough: nothing it
     * paints is one wide. A layout is asked in its own plan coordinates (offsets from its origin), as the painter asks it.
     */
    public static boolean touches(SettlementPaint.Layout layout, int x, int z, int r) {
        int px = x - layout.originX(), pz = z - layout.originZ();
        for (int dx = -r; dx <= r; dx += 2) {
            for (int dz = -r; dz <= r; dz += 2) {
                byte kind = layout.kind(px + dx, pz + dz);
                if (kind != CityPlan.VOID && kind != CityPlan.FREE || layout.clears(px + dx, pz + dz)) return true;
            }
        }
        return false;
    }
}
