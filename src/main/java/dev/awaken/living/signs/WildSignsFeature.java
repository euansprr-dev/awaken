package dev.awaken.living.signs;

import com.mojang.serialization.MapCodec;
import dev.awaken.living.combat.Forms;
import dev.awaken.living.region.RegionMap;
import dev.awaken.living.region.RegionService;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;

import java.util.List;

/**
 * {@code awaken:wild_signs} (SPEC §21.11, decision 24): each new overworld chunk in land of rank D or higher gets the signs of its rank, on
 * natural ground, in the land's own materials. It asks the region map (a pure function of the seed and the city's centre, safe on worldgen
 * threads) only once the map exists: the first chunks of a new world, which lie in the Commons anyway, wait for nothing and get nothing.
 */
public record WildSignsFeature() implements Feature {
    public static final MapCodec<WildSignsFeature> CODEC = MapCodec.unit(WildSignsFeature::new);

    @Override
    public MapCodec<WildSignsFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator gen, RandomSource random, BlockPos origin) {
        if (level.getLevel().dimension() != Level.OVERWORLD) return false;
        RegionMap map = RegionService.mapIfReady(level.getLevel().getServer());
        if (map == null) return false;
        int x0 = (origin.getX() >> 4) << 4, z0 = (origin.getZ() >> 4) << 4;
        int rank = RegionMap.rankOf(map.levelAt(x0 + 8, z0 + 8));
        if (rank <= 0) return false;
        boolean any = false;
        for (int i = Signs.attempts(rank, random); i > 0; i--) {
            int x = x0 + 4 + random.nextInt(8), z = z0 + 4 + random.nextInt(8);
            BlockPos ground = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1, z);
            if (!level.getFluidState(ground.above()).isEmpty() || !Signs.natural(level.getBlockState(ground)) || !level.getBlockState(ground.above()).isAir() && !level.getBlockState(ground.above()).canBeReplaced()) continue;
            // flat enough and all natural over its footprint
            boolean ok = true;
            for (int dx = -1; dx <= 1 && ok; dx++)
                for (int dz = -1; dz <= 1 && ok; dz++) {
                    BlockPos g = new BlockPos(x + dx, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x + dx, z + dz) - 1, z + dz);
                    ok = Math.abs(g.getY() - ground.getY()) <= 1 && Signs.natural(level.getBlockState(g)) && level.getFluidState(g.above()).isEmpty();
                }
            if (!ok) continue;
            List<Signs.Kind> kinds = Signs.of(rank);
            if (kinds.isEmpty()) continue;
            int fam = Forms.family(level.getBiome(ground).value());
            any |= Signs.build(level, random, ground, kinds.get(random.nextInt(kinds.size())), fam) > 0;
        }
        return any;
    }
}
