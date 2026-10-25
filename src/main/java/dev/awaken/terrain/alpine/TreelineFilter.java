package dev.awaken.terrain.alpine;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;

/**
 * {@code awaken:treeline} placement filter (SPEC §15, Alpine): a tree may stand only below the treeline and on ground
 * gentle enough to hold soil, by {@link AlpineSurface#treeAllowed}, the rule Far Sight's far forests use too, so the
 * trees that load in where far ones stood agree with them. The slope is read from the ground two blocks either side.
 */
public final class TreelineFilter implements PlacementFilter {
    public static final TreelineFilter INSTANCE = new TreelineFilter();
    public static final MapCodec<TreelineFilter> CODEC = MapCodec.unit(INSTANCE);

    private TreelineFilter() {}

    @Override
    public boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos origin) {
        int x = origin.getX(), z = origin.getZ();
        float gx = (context.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x + 2, z) - context.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x - 2, z)) / 4f;
        float gz = (context.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z + 2) - context.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z - 2)) / 4f;
        return AlpineSurface.treeAllowed(x, origin.getY(), z, gx, gz);
    }

    @Override
    public MapCodec<TreelineFilter> codec() {
        return CODEC;
    }
}
