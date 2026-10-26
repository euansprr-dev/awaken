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
        // footings up, so what's left of a course knows whether the one under it still stands
        for (int y = y0; y <= y1; y++) {
            boolean roof = y >= eave;
            // a scorch takes only the roof's top few rows
            if (how == SCORCH && y < y1 - 3) continue;
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    m.set(x, y, z);
                    BlockState s = level.getBlockState(m);
                    if (s.isAir() || s.hasBlockEntity() || !s.getFluidState().isEmpty()) continue;
                    // soot climbs the stone of its walls and roof (not the paving): little at the footings, most under the eaves
                    double soot = 0;
                    if (how == GUTTED && y > y0) {
                        Block under = level.getBlockState(m.below()).getBlock();
                        if (roof || !level.getBlockState(m.above()).isAir() || SOOT.containsKey(under) || SOOT.containsValue(under))
                            soot = 0.16 + 0.46 * Math.clamp((y - y0) / (double) Math.max(1, eave - y0), 0, 1);
                    }
                    BlockState to = burnt(s, roof, y == y0, Taken.mix(seed, m.asLong(), 3), soot);
                    if (to == null || to == s) continue;
                    // a charred remnant hangs on what's under it (or beside it in a wall); nothing floats
                    if (!to.isAir() && isCharred(to) && !SOOT.containsValue(to.getBlock()) && y > y0 && !supported(level, m, roof))
                        to = Blocks.AIR.defaultBlockState();
                    VillageService.damaged(l, m.immutable(), s, day);
                    level.setBlock(m, to, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    n++;
                }
            }
        }
        VillageState.get(level.getServer()).setDirty();
        return n;
    }

    /** Something under this cell (or under a neighbour, for a stepped roof; or beside it, in a wall) still stands. */
    private static boolean supported(ServerLevel level, BlockPos.MutableBlockPos m, boolean roof) {
        BlockPos at = m.immutable();
        if (!level.getBlockState(at.below()).isAir()) return true;
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            if (!level.getBlockState(at.below().relative(d)).isAir()) return true;
            if (!roof && !level.getBlockState(at.relative(d)).isAir()) return true;
        }
        return false;
    }

    /** Whether a block is what fire left (the Rebuild puts the house back over it). */
    static boolean isCharred(BlockState s) {
        return s.is(Blocks.COAL_BLOCK) || s.is(Blocks.BLACKSTONE) || s.is(Blocks.BASALT) || s.is(Blocks.STRIPPED_DARK_OAK_LOG) || s.is(Blocks.COARSE_DIRT)
                || s.is(Blocks.GRAVEL) || SOOT.containsValue(s.getBlock());
    }

    /** Stone blackened by the fire, shape kept (stairs, slabs and walls into their dark kin). */
    private static final java.util.Map<Block, Block> SOOT = java.util.Map.ofEntries(
            java.util.Map.entry(Blocks.STONE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS), java.util.Map.entry(Blocks.MOSSY_STONE_BRICKS, Blocks.DEEPSLATE_BRICKS),
            java.util.Map.entry(Blocks.CRACKED_STONE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS), java.util.Map.entry(Blocks.COBBLESTONE, Blocks.COBBLED_DEEPSLATE),
            java.util.Map.entry(Blocks.MOSSY_COBBLESTONE, Blocks.COBBLED_DEEPSLATE), java.util.Map.entry(Blocks.STONE, Blocks.DEEPSLATE),
            java.util.Map.entry(Blocks.ANDESITE, Blocks.TUFF), java.util.Map.entry(Blocks.POLISHED_ANDESITE, Blocks.POLISHED_TUFF),
            java.util.Map.entry(Blocks.SMOOTH_STONE, Blocks.POLISHED_DEEPSLATE),
            java.util.Map.entry(Blocks.STONE_BRICK_STAIRS, Blocks.DEEPSLATE_BRICK_STAIRS), java.util.Map.entry(Blocks.STONE_BRICK_SLAB, Blocks.DEEPSLATE_BRICK_SLAB),
            java.util.Map.entry(Blocks.STONE_BRICK_WALL, Blocks.DEEPSLATE_BRICK_WALL), java.util.Map.entry(Blocks.COBBLESTONE_STAIRS, Blocks.COBBLED_DEEPSLATE_STAIRS),
            java.util.Map.entry(Blocks.COBBLESTONE_SLAB, Blocks.COBBLED_DEEPSLATE_SLAB), java.util.Map.entry(Blocks.COBBLESTONE_WALL, Blocks.COBBLED_DEEPSLATE_WALL),
            java.util.Map.entry(Blocks.STONE_STAIRS, Blocks.POLISHED_DEEPSLATE_STAIRS), java.util.Map.entry(Blocks.STONE_SLAB, Blocks.POLISHED_DEEPSLATE_SLAB));

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState sooted(BlockState s, Block to) {
        BlockState out = to.defaultBlockState();
        for (net.minecraft.world.level.block.state.properties.Property p : s.getProperties())
            if (out.hasProperty(p)) out = out.setValue(p, s.getValue(p));
        return out;
    }

    /** What fire leaves of a block (null: it stands); stone it doesn't take is blackened at this chance. */
    static BlockState burnt(BlockState s, boolean roof, boolean floor, long h, double soot) {
        int r = (int) Math.floorMod(h, 100L);
        Block dark = SOOT.get(s.getBlock());
        if (dark != null) return r < soot * 100 ? sooted(s, dark) : null;
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
