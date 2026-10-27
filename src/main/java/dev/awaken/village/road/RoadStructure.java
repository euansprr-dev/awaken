package dev.awaken.village.road;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.village.world.VillageMarker;
import dev.awaken.village.world.VillageWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.List;
import java.util.Optional;

/**
 * {@code awaken:road}: the legs of the world's roads that start in a chunk on the roads' lattice (SPEC §28.9). Each leg
 * is a painter piece and, at every point on dry land, a thin-beard marker the width of the carriageway and its verges at
 * the road's height: the land eases up or down to the road instead of meeting it in a step, so a road follows the land
 * with small cuttings and banks. Placed by the Awakened Villages pack ({@link RoadPlacement}), new worlds only.
 */
public class RoadStructure extends Structure {
    public static final MapCodec<RoadStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(settingsCodec(i)).apply(i, RoadStructure::new));

    public RoadStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext ctx) {
        List<Road.Leg> legs = RoadNet.startsIn(ctx.chunkPos().x(), ctx.chunkPos().z());
        if (legs.isEmpty()) return Optional.empty();
        Road.Leg first = legs.getFirst();
        int mid = (first.i0() + first.i1()) / 2;
        BlockPos at = new BlockPos((ctx.chunkPos().x() << 4) + 8, first.road().y()[mid], (ctx.chunkPos().z() << 4) + 8);
        return Optional.of(new Structure.GenerationStub(at, b -> {
            for (Road.Leg leg : legs) {
                b.addPiece(new RoadPiece(leg));
                Road r = leg.road();
                float hw = r.halfWidth() + 1;
                for (int i = leg.i0(); i <= leg.i1(); i++) {
                    if ((r.flags()[i] & (RoadPlanner.BRIDGE | RoadPlanner.BOARDWALK | RoadPlanner.CAUSEWAY)) != 0) continue;
                    int x = (int) Math.floor(r.x()[i]), z = (int) Math.floor(r.z()[i]), y = r.y()[i];
                    b.addPiece(new VillageMarker(new BoundingBox(x - (int) Math.ceil(hw), y, z - (int) Math.ceil(hw), x + (int) Math.ceil(hw), y + 6,
                            z + (int) Math.ceil(hw))));
                }
            }
        }));
    }

    @Override
    public StructureType<?> type() {
        return VillageWorldgen.ROAD.get();
    }
}
