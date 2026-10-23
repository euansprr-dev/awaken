package dev.awaken.above.ruin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.util.RandomSource;

/**
 * Exactly one structure start, {@code min..max} blocks from the dimension origin (where the world spawn search starts;
 * vanilla puts spawn within about 5 chunks of it). Used for the guaranteed sealed vault near spawn.
 * {@code /locate} doesn't search custom placements, so {@link #target} is also how the Above finds it.
 */
public record SpawnRingPlacement(int minDistance, int maxDistance, int salt) implements StructurePlacement {
    public static final MapCodec<SpawnRingPlacement> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("min_distance").forGetter(SpawnRingPlacement::minDistance),
            Codec.INT.fieldOf("max_distance").forGetter(SpawnRingPlacement::maxDistance),
            Codec.INT.fieldOf("salt").forGetter(SpawnRingPlacement::salt)
    ).apply(i, SpawnRingPlacement::new));

    public ChunkPos target(long seed, ChunkPos origin) {
        RandomSource r = new XoroshiroRandomSource(seed ^ ((long) salt * 0x9E3779B97F4A7C15L));
        double angle = r.nextDouble() * Math.PI * 2.0;
        double dist = minDistance + r.nextDouble() * Math.max(0, maxDistance - minDistance);
        return new ChunkPos(origin.x() + (int) Math.round(Math.cos(angle) * dist / 16.0),
                origin.z() + (int) Math.round(Math.sin(angle) * dist / 16.0));
    }

    public ChunkPos target(ChunkGeneratorStructureState state) {
        return target(state.getLevelSeed(), state.getDimensionOrigin());
    }

    @Override
    public boolean isStructureChunk(ChunkGeneratorStructureState state, int chunkX, int chunkZ) {
        ChunkPos t = target(state);
        return t.x() == chunkX && t.z() == chunkZ;
    }

    @Override
    public MapCodec<? extends StructurePlacement> codec() {
        return CODEC;
    }
}
