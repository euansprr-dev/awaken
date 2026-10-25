package dev.awaken.living.camp;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

/**
 * Builds a camp into the world (SPEC §21.8), only over natural ground and into air, recording every block it sets so
 * clearing the camp can turn it to ash: a riftborn camp (a rift scar, a palisade, tents, a fire, two soul lanterns),
 * a blight nest (blackstone and crying obsidian veins over the break's scar, sculk creeping, a heart pillar), a hollow
 * den (a ring of mossy stone, bones and webs). Bigger by tier. At most three light sources.
 */
final class CampPainter {
    private CampPainter() {}

    /** Paints the camp at its tier: ground it changed is appended to {@code ground}, what it stood up to {@code stood}. */
    static void paint(ServerLevel level, Camps.Camp c, List<Long> ground, List<Long> stood) {
        Lists painted = new Lists(ground, stood);
        RandomSource r = RandomSource.create(c.seed + c.tier);
        BlockPos o = c.pos();
        int rad = 3 + c.tier;
        switch (c.kind) {
            case "riftborn" -> riftborn(level, c, o, rad, r, painted);
            case "blight_nest" -> blight(level, o, rad, r, painted);
            default -> hollow(level, o, rad, r, painted);
        }
    }

    private static void riftborn(ServerLevel level, Camps.Camp c, BlockPos o, int rad, RandomSource r, Lists painted) {
        // trodden ground
        for (int dx = -rad; dx <= rad; dx++) for (int dz = -rad; dz <= rad; dz++) {
            if (dx * dx + dz * dz > rad * rad || r.nextFloat() < 0.35F) continue;
            ground(level, o.getX() + dx, o.getZ() + dz, r.nextBoolean() ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT_PATH.defaultBlockState(), painted);
        }
        // the rift scar it came out of: crying obsidian with a violet glint
        ground(level, o.getX(), o.getZ(), Blocks.CRYING_OBSIDIAN.defaultBlockState(), painted);
        ground(level, o.getX() + 1, o.getZ(), Blocks.OBSIDIAN.defaultBlockState(), painted);
        ground(level, o.getX(), o.getZ() + 1, Blocks.OBSIDIAN.defaultBlockState(), painted);
        // a palisade of sharpened posts, gaps in it
        for (int i = 0; i < 24 + c.tier * 6; i++) {
            double a = i * Math.PI * 2 / (24 + c.tier * 6);
            if (r.nextFloat() < 0.25F) continue;
            int x = o.getX() + (int) Math.round(Math.cos(a) * (rad + 1)), z = o.getZ() + (int) Math.round(Math.sin(a) * (rad + 1));
            BlockPos top = surface(level, x, z);
            int h = 1 + r.nextInt(2);
            for (int y = 0; y < h; y++) air(level, top.above(y), Blocks.SPRUCE_FENCE.defaultBlockState(), painted);
            air(level, top.above(h), Blocks.POINTED_DRIPSTONE.defaultBlockState(), painted);
        }
        // tents: a peak of wool over two posts each
        int tents = 1 + c.tier / 2;
        for (int t = 0; t < tents; t++) {
            double a = r.nextDouble() * Math.PI * 2;
            int x = o.getX() + (int) (Math.cos(a) * (rad - 2)), z = o.getZ() + (int) (Math.sin(a) * (rad - 2));
            BlockPos base = surface(level, x, z);
            BlockState wool = Blocks.WOOL.pick(t % 2 == 0 ? net.minecraft.world.item.DyeColor.RED : net.minecraft.world.item.DyeColor.BLACK).defaultBlockState();
            air(level, base, Blocks.SPRUCE_FENCE.defaultBlockState(), painted);
            air(level, base.offset(2, 0, 0), Blocks.SPRUCE_FENCE.defaultBlockState(), painted);
            air(level, base.above(), wool, painted);
            air(level, base.offset(1, 1, 0), wool, painted);
            air(level, base.offset(2, 1, 0), wool, painted);
            air(level, base.offset(1, 2, 0), wool, painted);
        }
        // the fire and two soul lanterns on posts
        BlockPos fire = surface(level, o.getX() + 2, o.getZ() - 2);
        air(level, fire, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true), painted);
        for (int s = -1; s <= 1; s += 2) {
            BlockPos post = surface(level, o.getX() + s * (rad - 1), o.getZ() + rad - 1);
            air(level, post, Blocks.SPRUCE_FENCE.defaultBlockState(), painted);
            air(level, post.above(), Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), painted);
        }
    }

    private static void blight(ServerLevel level, BlockPos o, int rad, RandomSource r, Lists painted) {
        for (int dx = -rad; dx <= rad; dx++) for (int dz = -rad; dz <= rad; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > rad || r.nextFloat() < d / rad * 0.6) continue;
            BlockState s = r.nextFloat() < 0.18F ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                    : r.nextFloat() < 0.5F ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.SCULK.defaultBlockState();
            ground(level, o.getX() + dx, o.getZ() + dz, s, painted);
            if (r.nextFloat() < 0.06F) air(level, surface(level, o.getX() + dx, o.getZ() + dz), Blocks.SCULK_VEIN.defaultBlockState(), painted);
        }
        // the heart: a pillar of crying obsidian, taller by tier
        BlockPos top = surface(level, o.getX(), o.getZ());
        for (int y = 0; y < 1 + rad / 2; y++) air(level, top.above(y), Blocks.CRYING_OBSIDIAN.defaultBlockState(), painted);
    }

    private static void hollow(ServerLevel level, BlockPos o, int rad, RandomSource r, Lists painted) {
        for (int i = 0; i < 14 + rad * 2; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = 1 + r.nextDouble() * rad;
            int x = o.getX() + (int) (Math.cos(a) * d), z = o.getZ() + (int) (Math.sin(a) * d);
            float k = r.nextFloat();
            if (k < 0.45F) ground(level, x, z, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), painted);
            else if (k < 0.7F) air(level, surface(level, x, z), Blocks.BONE_BLOCK.defaultBlockState(), painted);
            else air(level, surface(level, x, z), Blocks.COBWEB.defaultBlockState(), painted);
        }
    }

    // ---- writing --------------------------------------------------------------------------------------------------

    /** Natural ground (26.3's #dirt no longer holds grass): grass, dirt, podzol, mycelium, sand, gravel, stone, snow. */
    static boolean natural(BlockState s) {
        return s.is(Blocks.GRASS_BLOCK) || s.is(BlockTags.DIRT) || s.is(Blocks.PODZOL) || s.is(Blocks.MYCELIUM) || s.is(BlockTags.SAND)
                || s.is(Blocks.GRAVEL) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.SNOW_BLOCK);
    }

    /** Where the blocks it set are kept: ground it changed, blocks it stood up. */
    record Lists(List<Long> ground, List<Long> stood) {}

    static BlockPos surface(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    /** Replaces the natural ground block (grass, dirt, sand, stone) at a column's top. */
    private static void ground(ServerLevel level, int x, int z, BlockState s, Lists painted) {
        BlockPos at = surface(level, x, z).below();
        if (!level.hasChunkAt(at)) return;
        BlockState was = level.getBlockState(at);
        if (!natural(was)) return;
        level.setBlock(at, s, 2);
        painted.ground().add(at.asLong());
    }

    /** Sets a block into air (or a plant) above the ground. */
    private static void air(ServerLevel level, BlockPos at, BlockState s, Lists painted) {
        if (!level.hasChunkAt(at)) return;
        if (!level.getBlockState(at).canBeReplaced()) return;
        level.setBlock(at, s, 2);
        painted.stood().add(at.asLong());
    }

    /** Clearing: the ground it changed becomes ash, what it stood up goes. */
    static void ash(ServerLevel level, List<Long> ground, List<Long> stood) {
        for (long l : stood) {
            BlockPos at = BlockPos.of(l);
            if (level.hasChunkAt(at)) level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
        }
        for (long l : ground) {
            BlockPos at = BlockPos.of(l);
            if (level.hasChunkAt(at)) level.setBlock(at, Blocks.COARSE_DIRT.defaultBlockState(), 2);
        }
        stood.clear();
        ground.clear();
    }
}
