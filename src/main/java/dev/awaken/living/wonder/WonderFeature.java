package dev.awaken.living.wonder;

import com.mojang.serialization.MapCodec;
import dev.awaken.living.region.RegionMap;
import dev.awaken.living.region.RegionService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * {@code awaken:wonders} (SPEC §21.11): each new overworld chunk writes its share of every wonder within reach of it (see {@link Wonders}): only blocks inside the chunk,
 * and under each column of the wonder the ground is built up to its level so it does not float on a slope. Like the signs, it waits for the region map
 * (the first chunks of a world, in the Commons, hold no wonder).
 */
public record WonderFeature() implements Feature {
    public static final MapCodec<WonderFeature> CODEC = MapCodec.unit(WonderFeature::new);

    @Override
    public MapCodec<WonderFeature> codec() {
        return CODEC;
    }

    /** The ground by the chunk generator's own noise, the same from any chunk and later from the server. */
    public static Wonders.Terrain terrain(ServerLevel level, ChunkGenerator gen) {
        var state = level.getChunkSource().randomState();
        return new Wonders.Terrain() {
            @Override
            public int ground(int x, int z) {
                return gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, state) - 1;
            }

            @Override
            public int seaLevel() {
                return gen.getSeaLevel();
            }
        };
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator gen, RandomSource random, BlockPos origin) {
        ServerLevel sl = level.getLevel();
        if (sl.dimension() != Level.OVERWORLD) return false;
        RegionMap map = RegionService.mapIfReady(sl.getServer());
        if (map == null) return false;
        int x0 = (origin.getX() >> 4) << 4, z0 = (origin.getZ() >> 4) << 4;
        var sites = Wonders.near(level.getSeed(), map, terrain(sl, gen), x0, z0, x0 + 15, z0 + 15, Wonders.REACH);
        if (sites.isEmpty()) return false;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        boolean any = false;
        for (Wonders.Site site : sites) {
            // what grows through the wonder's ground (trees, bushes, tall grass) goes, so nothing stands half in it
            int cr = site.kind().clear;
            for (int x = Math.max(x0, site.x() - cr); x <= Math.min(x0 + 15, site.x() + cr); x++)
                for (int z = Math.max(z0, site.z() - cr); z <= Math.min(z0 + 15, site.z() + cr); z++) {
                    int dx = x - site.x(), dz = z - site.z();
                    if (dx * dx + dz * dz > cr * cr) continue;
                    for (int y = site.y() + 1; y <= site.y() + 26; y++) {
                        m.set(x, y, z);
                        BlockState b = level.getBlockState(m);
                        if (b.isAir() || !b.getFluidState().isEmpty()) continue;
                        if (b.is(net.minecraft.tags.BlockTags.LEAVES) || b.is(net.minecraft.tags.BlockTags.LOGS) || b.canBeReplaced()) level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            for (var e : site.plan().blocks().long2ObjectEntrySet()) {
                long k = e.getLongKey();
                int x = site.x() + BlockPos.getX(k), z = site.z() + BlockPos.getZ(k);
                if (x < x0 || x > x0 + 15 || z < z0 || z > z0 + 15) continue;
                int y = site.y() + BlockPos.getY(k);
                m.set(x, y, z);
                BlockState put = e.getValue();
                level.setBlock(m, put, Block.UPDATE_CLIENTS);
                any = true;
                if (BlockPos.getY(k) <= 1) { // build the ground up under the foot of the wonder, and clear what grows through it
                    for (int d = 1; d <= 10; d++) {
                        m.set(x, y - d, z);
                        BlockState below = level.getBlockState(m);
                        if (below.isSolidRender() && !below.is(net.minecraft.tags.BlockTags.LEAVES)) break;
                        level.setBlock(m, (d == 1 ? Blocks.DIRT : Blocks.STONE).defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        return any;
    }
}
