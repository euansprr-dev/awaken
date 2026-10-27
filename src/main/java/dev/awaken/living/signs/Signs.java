package dev.awaken.living.signs;

import dev.awaken.living.combat.Forms;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The land's signs (SPEC §21.11, decision 24): what the world leaves lying about where creatures have got worse, dressed into new chunks by
 * rank so a place looks like its number before anything in it moves. Vanilla blocks only, nothing to loot, small enough to walk round, on
 * natural ground only (never on a road, a floor or a village's paths). A kind builds itself on a ground position; the feature picks where.
 */
public final class Signs {
    private Signs() {}

    public enum Kind {
        WALLOW(1), BONEPILE(1), BOULDER(1), OUTCROP(1),
        SKULLPOST(2), COLD_CAMP(2), CART(2),
        SNAG(3), RIBS(3),
        GRAVES(4), SPIRE(4),
        WEEPING(5), CIRCLE(5);

        public final int rank;

        Kind(int rank) {
            this.rank = rank;
        }
    }

    /** Signs per chunk by rank (E none): a rank is a look, so it thickens as it climbs. */
    public static final float[] PER_CHUNK = {0F, 0.25F, 0.5F, 0.9F, 1.3F, 1.6F};

    public static int attempts(int rank, RandomSource rnd) {
        float d = PER_CHUNK[Math.max(0, Math.min(5, rank))];
        return (int) d + (rnd.nextFloat() < d - (int) d ? 1 : 0);
    }

    /** The kinds of a rank. */
    public static List<Kind> of(int rank) {
        return java.util.Arrays.stream(Kind.values()).filter(k -> k.rank == rank).toList();
    }

    /** Whether this is ground a sign may stand on: the land's own, never a road, a floor or a built path. */
    public static boolean natural(BlockState s) {
        return s.is(BlockTags.DIRT) && !s.is(Blocks.DIRT_PATH) || s.is(BlockTags.SAND) || s.is(BlockTags.STONE_ORE_REPLACEABLES) && !s.is(Blocks.DEEPSLATE)
                || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.TERRACOTTA) || s.is(Blocks.PACKED_ICE);
    }

    // ---- what each kind is made of, by family (green: moss and bark, sun: sand-baked, frost: rime) ----------------------------------------

    private static BlockState stone(int fam) {
        return (fam == Forms.SUN ? Blocks.SANDSTONE : fam == Forms.FROST ? Blocks.STONE : Blocks.MOSSY_COBBLESTONE).defaultBlockState();
    }

    private static BlockState fence(int fam) {
        return (fam == Forms.SUN ? Blocks.ACACIA_FENCE : fam == Forms.FROST ? Blocks.SPRUCE_FENCE : Blocks.OAK_FENCE).defaultBlockState();
    }

    private static BlockState wood(int fam) {
        return (fam == Forms.SUN ? Blocks.STRIPPED_ACACIA_LOG : fam == Forms.FROST ? Blocks.STRIPPED_SPRUCE_LOG : Blocks.STRIPPED_OAK_LOG).defaultBlockState();
    }

    private static BlockState log(BlockState s, Direction.Axis axis) {
        return s.setValue(RotatedPillarBlock.AXIS, axis);
    }

    private static void put(LevelAccessor level, BlockPos pos, BlockState s) {
        BlockState old = level.getBlockState(pos);
        if (old.isAir() || old.canBeReplaced() || natural(old)) level.setBlock(pos, s, Block.UPDATE_CLIENTS);
    }

    /** Builds a sign on {@code ground} (the natural block it stands on); returns how many blocks it placed. */
    public static int build(LevelAccessor level, RandomSource rnd, BlockPos ground, Kind kind, int fam) {
        int[] n = {0};
        java.util.function.BiConsumer<BlockPos, BlockState> set = (p, s) -> {
            put(level, p, s);
            n[0]++;
        };
        BlockPos up = ground.above();
        switch (kind) {
            case WALLOW -> { // a mud wallow, a few blocks across, with a bone in it
                BlockState mud = (fam == Forms.FROST ? Blocks.PACKED_ICE : Blocks.MUD).defaultBlockState();
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if ((dx != 0 && dz != 0 && rnd.nextInt(3) == 0) == false) set.accept(ground.offset(dx, 0, dz), mud);
                set.accept(up.offset(rnd.nextInt(3) - 1, 0, rnd.nextInt(3) - 1), Blocks.BONE_BLOCK.defaultBlockState());
            }
            case BONEPILE -> {
                set.accept(up, Blocks.BONE_BLOCK.defaultBlockState());
                set.accept(up.offset(1, 0, 0), log(Blocks.BONE_BLOCK.defaultBlockState(), Direction.Axis.X));
                if (rnd.nextBoolean()) set.accept(up.offset(0, 0, 1), log(Blocks.BONE_BLOCK.defaultBlockState(), Direction.Axis.Z));
                if (rnd.nextBoolean()) set.accept(up.above(), Blocks.BONE_BLOCK.defaultBlockState());
                if (rnd.nextInt(3) == 0) set.accept(up.offset(-1, 0, 0), Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, rnd.nextInt(16)));
            }
            case BOULDER -> { // a rock a boar has worn smooth: a heap two blocks high
                BlockState rock = stone(fam);
                for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) set.accept(up.offset(dx, 0, dz), rock);
                set.accept(up.offset(rnd.nextInt(2), 1, rnd.nextInt(2)), rock);
                if (fam == Forms.FROST) set.accept(up.offset(0, 2, 0), Blocks.SNOW.defaultBlockState());
            }
            case OUTCROP -> { // rust-stained rock: the surface sign of iron in the hills (a pull; the iron stays in its lodes)
                BlockState a = Blocks.TERRACOTTA.defaultBlockState(), b = (fam == Forms.SUN ? Blocks.RED_SANDSTONE : Blocks.GRANITE).defaultBlockState();
                set.accept(up, b);
                set.accept(up.offset(1, 0, 0), a);
                set.accept(up.offset(0, 0, 1), a);
                set.accept(up.offset(-1, 0, 0), Blocks.RED_SAND.defaultBlockState());
                set.accept(up.above(), a);
            }
            case SKULLPOST -> { // a skull on a post: someone's warning
                BlockState f = fence(fam);
                set.accept(up, f);
                set.accept(up.above(), f);
                set.accept(up.above(2), f);
                set.accept(up.above(3), Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, rnd.nextInt(16)));
            }
            case COLD_CAMP -> { // a fire that went out
                set.accept(up, Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
                set.accept(up.offset(2, 0, 0), log(Blocks.SPRUCE_LOG.defaultBlockState(), Direction.Axis.Z));
                set.accept(up.offset(-2, 0, 1), log(Blocks.SPRUCE_LOG.defaultBlockState(), Direction.Axis.Z));
                set.accept(up.offset(0, 0, 2), Blocks.COBBLESTONE_SLAB.defaultBlockState());
                set.accept(up.offset(-1, 0, -1), Blocks.COBBLESTONE_SLAB.defaultBlockState());
            }
            case CART -> { // a wagon that did not get through
                set.accept(up, Blocks.SPRUCE_SLAB.defaultBlockState());
                set.accept(up.offset(1, 0, 0), Blocks.SPRUCE_SLAB.defaultBlockState());
                set.accept(up.offset(0, 0, 1), Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN, true).setValue(TrapDoorBlock.FACING, Direction.NORTH));
                set.accept(up.offset(1, 0, -1), Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN, true).setValue(TrapDoorBlock.FACING, Direction.SOUTH));
                set.accept(up.offset(-1, 0, 0), Blocks.SPRUCE_FENCE.defaultBlockState());
                set.accept(up.offset(-1, 1, 0), Blocks.SPRUCE_FENCE.defaultBlockState());
            }
            case SNAG -> { // a dead tree: a bare trunk with two broken limbs
                BlockState w = wood(fam);
                int h = 5 + rnd.nextInt(3);
                for (int y = 0; y < h; y++) set.accept(up.above(y), log(w, Direction.Axis.Y));
                set.accept(up.offset(1, h - 3, 0), log(w, Direction.Axis.X));
                set.accept(up.offset(2, h - 2, 0), log(w, Direction.Axis.X));
                set.accept(up.offset(0, h - 4, -1), log(w, Direction.Axis.Z));
            }
            case RIBS -> { // the ribs of something huge, standing out of the ground
                BlockState bone = Blocks.BONE_BLOCK.defaultBlockState();
                int[] hs = {1, 2, 3, 3, 3, 2, 1};
                for (int rib = 0; rib < 3; rib++)
                    for (int i = 0; i < hs.length; i++)
                        for (int y = 0; y < hs[i]; y++) if (y == hs[i] - 1 || i == 0 || i == hs.length - 1) set.accept(up.offset(i - 3, y, rib * 2), bone);
            }
            case GRAVES -> { // a row of headstones
                int count = 3 + rnd.nextInt(2);
                for (int i = 0; i < count; i++) {
                    BlockPos p = up.offset(i * 2, 0, 0);
                    set.accept(p, Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
                    set.accept(p.above(), Blocks.STONE_BRICK_WALL.defaultBlockState());
                    set.accept(p.above(2), Blocks.STONE_BRICK_SLAB.defaultBlockState());
                    set.accept(p.offset(0, -1 + 1, 1), Blocks.COARSE_DIRT.defaultBlockState());
                }
                set.accept(up.offset(-1, 0, 0), Blocks.SPRUCE_FENCE.defaultBlockState());
                set.accept(up.offset(-1, 1, 0), Blocks.SOUL_LANTERN.defaultBlockState());
            }
            case SPIRE -> { // a black spire
                BlockState b = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) set.accept(up.offset(dx, 0, dz), b);
                int h = 6 + rnd.nextInt(4);
                for (int y = 1; y < h; y++) set.accept(up.above(y), Blocks.BLACKSTONE.defaultBlockState());
                set.accept(up.above(h), Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
            }
            case WEEPING -> { // a stone that weeps
                set.accept(up, Blocks.OBSIDIAN.defaultBlockState());
                int h = 3 + rnd.nextInt(2);
                for (int y = 1; y < h; y++) set.accept(up.above(y), Blocks.CRYING_OBSIDIAN.defaultBlockState());
            }
            case CIRCLE -> { // a ring of weeping stones
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3;
                    BlockPos p = up.offset((int) Math.round(Math.cos(a) * 3), 0, (int) Math.round(Math.sin(a) * 3));
                    set.accept(p, Blocks.CRYING_OBSIDIAN.defaultBlockState());
                    if (i % 2 == 0) set.accept(p.above(), Blocks.CRYING_OBSIDIAN.defaultBlockState());
                }
            }
        }
        return n[0];
    }
}
