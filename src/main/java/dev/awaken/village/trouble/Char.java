package dev.awaken.village.trouble;

import dev.awaken.city.CityPlan;
import dev.awaken.village.VillageService;
import dev.awaken.village.VillageState;
import dev.awaken.village.plan.VillagePlan;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Fire's work on a house (SPEC §28.7, the Burning): its roof falls in, its timbers blacken, its glass and doors and
 * cloth go, its floor is ash; stone stands (the chimney, the footings). Every block it changes is written to the
 * village's damage record with what stood there, so the Rebuild puts the house back exactly. Seeded per block, so the
 * same fire leaves the same ruin. Chests and anything else holding things are left alone.
 */
final class Char {
    private Char() {}

    /** How much of a house: 1 a scorch (part of its roof), 2 gutted. */
    static final int SCORCH = 1, GUTTED = 2;

    /** Burns a house's cells (all of them, in one go: a few hundred). Returns blocks changed; 0 if its chunks aren't loaded. */
    static int house(ServerLevel level, VillageService.Live l, int plotId, int how, long seed, long day) {
        VillagePlan p = l.plan();
        CityPlan.Plot q = p.plots.get(plotId);
        int x0 = p.ox + q.x0() - 1, x1 = p.ox + q.x1() + 1, z0 = p.oz + q.z0() - 1, z1 = p.oz + q.z1() + 1;
        int y0 = p.baseY + q.lift(), y1 = p.baseY + q.lift() + q.top() + 3;
        int eave = p.baseY + q.lift() + Math.max(3, q.eave());
        if (!level.isLoaded(new BlockPos(x0, y0, z0)) || !level.isLoaded(new BlockPos(x1, y0, z1))) return 0;
        int n = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = y1; y >= y0; y--) {
            boolean roof = y >= eave;
            // a scorch takes only the roof's top few rows
            if (how == SCORCH && y < y1 - 3) break;
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    m.set(x, y, z);
                    BlockState s = level.getBlockState(m);
                    if (s.isAir() || s.hasBlockEntity() || !s.getFluidState().isEmpty()) continue;
                    BlockState to = burnt(s, roof, y == y0, Taken.mix(seed, m.asLong(), 3));
                    if (to == null || to == s) continue;
                    VillageService.damaged(l, m.immutable(), s, day);
                    level.setBlock(m, to, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    n++;
                }
            }
        }
        VillageState.get(level.getServer()).setDirty();
        return n;
    }

    /** Whether a block is what fire left (the Rebuild puts the house back over it). */
    static boolean isCharred(BlockState s) {
        return s.is(Blocks.COAL_BLOCK) || s.is(Blocks.BLACKSTONE) || s.is(Blocks.BASALT) || s.is(Blocks.STRIPPED_DARK_OAK_LOG) || s.is(Blocks.COARSE_DIRT)
                || s.is(Blocks.GRAVEL);
    }

    /** What fire leaves of a block (null: it stands). */
    static BlockState burnt(BlockState s, boolean roof, boolean floor, long h) {
        int r = (int) Math.floorMod(h, 100L);
        boolean burns = s.ignitedByLava() || s.is(BlockTags.LOGS) || s.is(BlockTags.PLANKS) || s.is(BlockTags.WOODEN_STAIRS)
                || s.is(BlockTags.WOODEN_SLABS) || s.is(BlockTags.WOODEN_FENCES) || s.is(BlockTags.WOODEN_DOORS) || s.is(BlockTags.WOODEN_TRAPDOORS)
                || s.is(BlockTags.WOOL) || s.is(BlockTags.WOOL_CARPETS) || s.is(BlockTags.BEDS) || s.is(BlockTags.LEAVES);
        boolean glass = s.is(Blocks.GLASS) || s.is(Blocks.GLASS_PANE) || s.getBlock() instanceof net.minecraft.world.level.block.StainedGlassPaneBlock
                || s.getBlock() instanceof net.minecraft.world.level.block.TransparentBlock;
        if (glass) return Blocks.AIR.defaultBlockState();
        if (!burns) return null;
        if (s.is(BlockTags.WOOL) || s.is(BlockTags.WOOL_CARPETS) || s.is(BlockTags.BEDS) || s.is(BlockTags.WOODEN_DOORS) || s.is(BlockTags.WOODEN_TRAPDOORS)
                || s.is(BlockTags.LEAVES)) return Blocks.AIR.defaultBlockState();
        if (floor) return r < 60 ? Blocks.COARSE_DIRT.defaultBlockState() : r < 85 ? Blocks.GRAVEL.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
        if (roof) return r < 68 ? Blocks.AIR.defaultBlockState() : r < 86 ? Blocks.COAL_BLOCK.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
        if (s.is(BlockTags.LOGS)) {
            if (r < 22) return Blocks.AIR.defaultBlockState();
            BlockState log = r < 70 ? Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState() : Blocks.BASALT.defaultBlockState();
            if (s.hasProperty(BlockStateProperties.AXIS) && log.hasProperty(BlockStateProperties.AXIS))
                log = log.setValue(BlockStateProperties.AXIS, s.getValue(BlockStateProperties.AXIS));
            return log;
        }
        return r < 48 ? Blocks.AIR.defaultBlockState() : r < 74 ? Blocks.COAL_BLOCK.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
    }
}
