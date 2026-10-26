package dev.awaken.delve.cave;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.terrain.api.CityBasin;
import dev.awaken.terrain.api.CitySite;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/**
 * Exactly one structure start {@code min..max} blocks from Carillon's centre (world spawn's origin in a world without
 * the city), never within {@code avoid} degrees of north: the camp road runs north from the High Gate, and the first
 * sinkhole is found off it, near the Commons' edge (the Commons reaches 700). Like the spawn vault's ring, pure from the
 * seed; {@link #target} is how the Horizon finds it.
 */
public record NearCityPlacement(int minDistance, int maxDistance, int avoid, int salt) implements StructurePlacement {
    public static final MapCodec<NearCityPlacement> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("min_distance").forGetter(NearCityPlacement::minDistance),
            Codec.INT.fieldOf("max_distance").forGetter(NearCityPlacement::maxDistance),
            Codec.INT.optionalFieldOf("avoid_north", 40).forGetter(NearCityPlacement::avoid),
            Codec.INT.fieldOf("salt").forGetter(NearCityPlacement::salt)
    ).apply(i, NearCityPlacement::new));

    public ChunkPos target(long seed, int cx, int cz) {
        RandomSource r = new XoroshiroRandomSource(seed ^ ((long) salt * 0x9E3779B97F4A7C15L));
        // a bearing outside the road's sector (north is -z: Minecraft's yaw 180)
        double span = 360 - 2 * avoid;
        double yaw = 180 + avoid + r.nextDouble() * span;
        double dist = minDistance + r.nextDouble() * Math.max(0, maxDistance - minDistance);
        double a = Math.toRadians(yaw);
        int x = cx + (int) Math.round(-Math.sin(a) * dist), z = cz + (int) Math.round(Math.cos(a) * dist);
        return new ChunkPos(x >> 4, z >> 4);
    }

    public ChunkPos target(ChunkGeneratorStructureState state) {
        CitySite city = CityBasin.current();
        if (city != null) return target(state.getLevelSeed(), city.x(), city.z());
        ChunkPos o = state.getDimensionOrigin();
        return target(state.getLevelSeed(), o.getMiddleBlockX(), o.getMiddleBlockZ());
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
