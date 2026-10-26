package dev.awaken.village.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/**
 * {@code awaken:settlement_grid}: a structure start in each settlement anchor's chunk ({@link SettlementGrid}), and in the
 * first hamlet's. Pure seed arithmetic, so it answers for chunks nobody has generated. {@code /locate} doesn't search
 * custom placements: {@code /awaken village} finds them.
 */
public record SettlementPlacement(int salt) implements StructurePlacement {
    public static final MapCodec<SettlementPlacement> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("salt", 0).forGetter(SettlementPlacement::salt)
    ).apply(i, SettlementPlacement::new));

    @Override
    public boolean isStructureChunk(ChunkGeneratorStructureState state, int chunkX, int chunkZ) {
        return SettlementGrid.inChunk(state.getLevelSeed() ^ salt, chunkX, chunkZ, SettlementGrid.centre()) != null;
    }

    @Override
    public MapCodec<? extends StructurePlacement> codec() {
        return CODEC;
    }
}
