package dev.awaken.village.road;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/**
 * {@code awaken:road_lattice}: a road structure starts in a lattice chunk (every eighth each way) that some leg of the
 * world's roads starts in (SPEC §28.9). Off the lattice it answers at once; on it, the roads near are laid (once each).
 */
public record RoadPlacement(int salt) implements StructurePlacement {
    public static final MapCodec<RoadPlacement> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("salt", 0).forGetter(RoadPlacement::salt)
    ).apply(i, RoadPlacement::new));

    @Override
    public boolean isStructureChunk(ChunkGeneratorStructureState state, int chunkX, int chunkZ) {
        return !RoadNet.startsIn(chunkX, chunkZ).isEmpty();
    }

    @Override
    public MapCodec<? extends StructurePlacement> codec() {
        return CODEC;
    }
}
