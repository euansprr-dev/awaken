package dev.awaken.village.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.city.CityPlan;
import dev.awaken.village.plan.VillagePlan;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * {@code awaken:settlement}: one hamlet or village, planned in code from its anchor (SPEC §28.6). Its start holds the
 * whole plan's painter piece and a terrain marker per building, field and pen (the beardifier eases the land up to
 * them, so a house never floats or sinks into a slope). The Awakened Lands pack places it, new worlds only, on the
 * settlement grid ({@link SettlementPlacement}); the same pack switches vanilla's villages off.
 */
public class VillageStructure extends Structure {
    public static final MapCodec<VillageStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(settingsCodec(i)).apply(i, VillageStructure::new));

    public VillageStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext ctx) {
        long seed = ctx.seed();
        SettlementGrid.Anchor a = SettlementGrid.inChunk(seed, ctx.chunkPos().x(), ctx.chunkPos().z(), SettlementGrid.centre());
        if (a == null) return Optional.empty();
        GenTerrain t = new GenTerrain(ctx.chunkGenerator(), ctx.heightAccessor(), ctx.randomState(), ctx.biomeResolver());
        VillagePlan p = Plans.of(a, t);
        if (p == null) return Optional.empty();
        return Optional.of(new Structure.GenerationStub(new BlockPos(p.ox, p.baseY, p.oz), b -> {
            b.addPiece(new VillagePiece(a, p));
            for (CityPlan.Plot q : p.plots) b.addPiece(VillageMarker.of(p, q.x0(), q.z0(), q.x1(), q.z1(), q.lift()));
            for (VillagePlan.Field f : p.fields) b.addPiece(VillageMarker.of(p, f.x0(), f.z0(), f.x1(), f.z1(), f.lift()));
            for (VillagePlan.Pen f : p.pens) b.addPiece(VillageMarker.of(p, f.x0(), f.z0(), f.x1(), f.z1(), f.lift()));
            b.addPiece(VillageMarker.of(p, -p.greenR, -p.greenR, p.greenR, p.greenR, 0));
        }));
    }

    @Override
    public StructureType<?> type() {
        return VillageWorldgen.TYPE.get();
    }
}
