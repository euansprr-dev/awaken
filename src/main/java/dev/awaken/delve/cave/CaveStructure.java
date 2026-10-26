package dev.awaken.delve.cave;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * A cave place ({@code awaken:cave_place}, field {@code place}: the kind; {@code first}: the sinkhole placed on purpose
 * near the Commons' edge). Data: {@code data/awaken/worldgen/structure/delve/<kind>.json} and its structure set. The
 * site is found by {@link CaveSites}; the place is built by its {@link Spec} in a single {@link CavePiece}.
 */
public class CaveStructure extends Structure {
    public static final MapCodec<CaveStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            CaveKind.CODEC.fieldOf("place").forGetter(CaveStructure::kind),
            Codec.BOOL.optionalFieldOf("first", false).forGetter(CaveStructure::first)
    ).apply(i, CaveStructure::new));

    private final CaveKind kind;
    private final boolean first;

    public CaveStructure(Structure.StructureSettings settings, CaveKind kind, boolean first) {
        super(settings);
        this.kind = kind;
        this.first = first;
    }

    public CaveKind kind() {
        return kind;
    }

    public boolean first() {
        return first;
    }

    @Override
    protected Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext ctx) {
        // the city keeps its basin, and every depth under it (SPEC §28.3: its footprint is protected all the way down)
        if (dev.awaken.terrain.api.CityBasin.reserved(ctx.chunkPos().getMinBlockX() + 8, ctx.chunkPos().getMinBlockZ() + 8)) return Optional.empty();
        Spec spec = CaveSites.find(ctx, kind, first);
        if (spec == null) return Optional.empty();
        // the stub's place is where the biome is judged: the surface for a sinkhole, the place itself underground
        BlockPos at = kind == CaveKind.SINKHOLE ? new BlockPos(spec.x(), spec.p(Sinkhole.RIM), spec.z()) : new BlockPos(spec.x(), spec.y() + 2, spec.z());
        return Optional.of(new Structure.GenerationStub(at, b -> b.addPiece(new CavePiece(spec))));
    }

    @Override
    public StructureType<?> type() {
        return CaveContent.TYPE.get();
    }
}
