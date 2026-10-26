package dev.awaken.terrain;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.terrain.frost.FrostBlocks;
import dev.awaken.terrain.glass.GlassBlocks;
import dev.awaken.terrain.mire.MireBlocks;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Swaying plants (SPEC 15): which blocks get which marker, and that the marked vertices join up. The shader and the
 * models are client-only; what they bake comes from {@link PlantSway}, which these hold.
 */
final class PlantSwayTests {
    private PlantSwayTests() {}

    private static final Direction[] FACES = Direction.values();

    static void register() {
        // every plant kind is marked (its tip moves, its base or anchor does not); every leaf keeps the canopy marker
        // and no plant rule; nothing else is ever marked
        AwakenTests.register("terrain_sway_markers", 20, h -> {
            List<Block> plants = List.of(Blocks.SHORT_GRASS, Blocks.FERN, Blocks.TALL_GRASS, Blocks.LARGE_FERN, Blocks.DEAD_BUSH,
                    Blocks.BUSH, Blocks.FIREFLY_BUSH, Blocks.SHORT_DRY_GRASS, Blocks.TALL_DRY_GRASS, Blocks.SWEET_BERRY_BUSH,
                    Blocks.SUGAR_CANE, Blocks.BAMBOO, Blocks.BAMBOO_SAPLING, Blocks.NETHER_WART, Blocks.SUNFLOWER, Blocks.LILAC,
                    Blocks.ROSE_BUSH, Blocks.PEONY, Blocks.PITCHER_PLANT, Blocks.SMALL_DRIPLEAF, Blocks.PINK_PETALS, Blocks.WILDFLOWERS,
                    Blocks.CRIMSON_ROOTS, Blocks.WARPED_ROOTS, Blocks.NETHER_SPROUTS, Blocks.CACTUS_FLOWER, Blocks.VINE,
                    Blocks.CAVE_VINES, Blocks.CAVE_VINES_PLANT, Blocks.WEEPING_VINES, Blocks.WEEPING_VINES_PLANT,
                    Blocks.TWISTING_VINES, Blocks.TWISTING_VINES_PLANT, Blocks.HANGING_ROOTS, Blocks.SPORE_BLOSSOM,
                    Blocks.PALE_HANGING_MOSS, Blocks.MANGROVE_PROPAGULE, Blocks.SEAGRASS, Blocks.TALL_SEAGRASS, Blocks.KELP,
                    Blocks.KELP_PLANT, Blocks.LILY_PAD,
                    LandsBlocks.ELDER_SAPLING.get(), LandsBlocks.LUMEN_SAPLING.get(), LandsBlocks.LUMEN_VINES.get(),
                    FrostBlocks.BLUEPINE_SAPLING.get(), GlassBlocks.GLASSBARK_SAPLING.get(), MireBlocks.WEEPWOOD_SAPLING.get(),
                    MireBlocks.WEEPWOOD_TENDRILS.get(), MireBlocks.MOONBLOOM.get());
            for (Block b : plants) for (BlockState s : b.getStateDefinition().getPossibleStates()) plant(h, s);
            for (TagKey<Block> tag : List.of(BlockTags.SMALL_FLOWERS, BlockTags.SAPLINGS, BlockTags.CROPS)) {
                BuiltInRegistries.BLOCK.getTagOrEmpty(tag).forEach(holder -> {
                    for (BlockState s : holder.value().getStateDefinition().getPossibleStates()) plant(h, s);
                });
            }
            // underwater plants sway in the current, not the wind; pads bob
            for (Block b : List.of(Blocks.SEAGRASS, Blocks.TALL_SEAGRASS, Blocks.KELP, Blocks.KELP_PLANT))
                h.assertTrue(PlantSway.rule(b.defaultBlockState()).wet(), b + " sways in the current");
            h.assertTrue(PlantSway.rule(Blocks.LILY_PAD.defaultBlockState()) == PlantSway.PAD, "a lily pad bobs");
            h.assertTrue(PlantSway.rule(MireBlocks.MOONBLOOM.get().defaultBlockState()) == PlantSway.PAD, "a moonbloom bobs");

            List<Block> still = List.of(Blocks.STONE, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.OAK_LOG, Blocks.GLASS, Blocks.TORCH,
                    Blocks.FLOWER_POT, Blocks.POTTED_POPPY, Blocks.BROWN_MUSHROOM, Blocks.RED_MUSHROOM, Blocks.CRIMSON_FUNGUS,
                    Blocks.SEA_PICKLE, Blocks.LEAF_LITTER, Blocks.MOSS_CARPET, Blocks.GLOW_LICHEN, Blocks.CACTUS,
                    Blocks.BIG_DRIPLEAF, Blocks.BIG_DRIPLEAF_STEM, Blocks.CHORUS_FLOWER, Blocks.COCOA, Blocks.SNOW, Blocks.WATER,
                    Blocks.TUBE_CORAL_FAN, Blocks.FARMLAND, Blocks.HAY_BLOCK,
                    LandsBlocks.LUMEN_CAP.get(), LandsBlocks.LUMEN_MOSS.get(), FrostBlocks.FROSTSPAR_CLUSTER.get(),
                    FrostBlocks.HOARFROST.get(), GlassBlocks.SUNSHARD_CLUSTER.get(), MireBlocks.MIREPEAT.get());
            for (Block b : still) for (BlockState s : b.getStateDefinition().getPossibleStates()) {
                h.assertTrue(PlantSway.rule(s) == null && !PlantSway.isLeaf(s), s + " stands still");
            }

            int leaves = 0, rules = 0;
            for (Block b : BuiltInRegistries.BLOCK) for (BlockState s : b.getStateDefinition().getPossibleStates()) {
                PlantSway.Rule rule = PlantSway.rule(s);
                if (PlantSway.isLeaf(s)) {
                    h.assertTrue(rule == null, s + " is a leaf, not a plant");
                    leaves++;
                }
                if (rule == null) continue;
                rules++;
                // whatever a vertex of a plant gets is unmarked or a plant marker the shader can read, never the leaf's
                for (int v = 0; v < rule.variants(); v++) for (Direction f : FACES) for (float y : new float[] {-0.0625F, 0F, 0.3F, 0.5F, 0.9375F, 1F}) {
                    int a = PlantSway.alpha(rule, f, y, v / (PlantSway.CHAIN_REACH + 1), v % (PlantSway.CHAIN_REACH + 1));
                    h.assertTrue(a == 255 || (a >= PlantSway.MIN_ALPHA && a <= PlantSway.MAX_ALPHA), s + " marker " + a + " is readable");
                    if (a == 255 || a == PlantSway.MAX_ALPHA - PlantSway.FLOAT_CODE) continue;
                    int code = PlantSway.MAX_ALPHA - a;
                    h.assertTrue(code >> 3 == PlantSway.mode(rule, f), s + " marker " + a + " decodes to its mode");
                }
            }
            h.assertTrue(leaves >= 28 * 13, "every leaf block's states are leaves: " + leaves);
            h.assertTrue(rules > 300, "hundreds of plant states sway: " + rules);
            h.succeed();
        });

        // two parts that share vertices give them the same marker: the halves of a double plant, every block of a
        // stalk or strand of any length; roots and anchors stay put and tips move most
        AwakenTests.register("terrain_sway_joined", 20, h -> {
            Direction side = Direction.NORTH;
            // single plants
            h.assertTrue(PlantSway.alpha(PlantSway.ROOTED, side, 0F, 0, 0) == 255, "a grass base stays put");
            h.assertTrue(PlantSway.alpha(PlantSway.ROOTED, side, -0.0625F, 0, 0) == 255, "a crop's sunken base stays put");
            h.assertTrue(PlantSway.alpha(PlantSway.ROOTED, side, 1F, 0, 0) != 255, "a grass tip sways");
            h.assertTrue(PlantSway.alpha(PlantSway.HANGING, side, 1F, 0, 0) == 255, "a hanging root's anchor stays put");
            h.assertTrue(PlantSway.alpha(PlantSway.HANGING, side, 0F, 0, 0) != 255, "its tip swings");
            // double plants: joined at the middle, the top half's tip moves more than the middle and than short grass
            for (PlantSway.Rule[] pair : new PlantSway.Rule[][] {{PlantSway.LOWER, PlantSway.UPPER}, {PlantSway.LOWER_WET, PlantSway.UPPER_WET}}) {
                h.assertTrue(PlantSway.alpha(pair[0], side, 0F, 0, 0) == 255, pair[0].name() + " base stays put");
                h.assertTrue(PlantSway.alpha(pair[0], side, 1F, 0, 0) == PlantSway.alpha(pair[1], side, 0F, 0, 0), pair[0].name() + " meets its top half");
                h.assertTrue(PlantSway.level(pair[1], 1F, 0, 0) > PlantSway.level(pair[1], 0F, 0, 0), "the top half's tip moves more than its middle");
            }
            h.assertTrue(PlantSway.level(PlantSway.UPPER, 1F, 0, 0) > PlantSway.level(PlantSway.ROOTED, 1F, 0, 0), "tall grass sways more than short grass");
            // stalks and strands of every length up to 8: each block's top meets the next block's bottom
            for (PlantSway.Rule rule : List.of(PlantSway.CANE, PlantSway.BAMBOO, PlantSway.TWISTING, PlantSway.KELP, PlantSway.WEEPING,
                    PlantSway.CAVE_VINES, PlantSway.MOSS, PlantSway.VINE)) {
                for (int len = 1; len <= 8; len++) {
                    for (int i = 0; i + 1 < len; i++) {
                        int top = alongRun(rule, side, 1F, i, len), bottom = alongRun(rule, side, 0F, i + 1, len);
                        h.assertTrue(top == bottom, rule.name() + " run of " + len + ": block " + i + " top " + top + " meets block " + (i + 1) + " bottom " + bottom);
                    }
                    boolean hangs = rule.anchor() == PlantSway.Anchor.STRAND;
                    if (hangs) h.assertTrue(alongRun(rule, side, 1F, len - 1, len) == 255, rule.name() + " anchor stays put (run of " + len + ")");
                    else h.assertTrue(alongRun(rule, side, 0F, 0, len) == 255, rule.name() + " root stays put (run of " + len + ")");
                    int tip = hangs ? alongRun(rule, side, 0F, 0, len) : alongRun(rule, side, 1F, len - 1, len);
                    h.assertTrue(tip != 255, rule.name() + " tip sways (run of " + len + ")");
                }
            }
            // bamboo bends only near its top: deep in a tall stalk nothing moves
            h.assertTrue(alongRun(PlantSway.BAMBOO, side, 1F, 3, 10) == 255 && alongRun(PlantSway.BAMBOO, side, 0F, 3, 10) == 255, "a tall bamboo's middle stands still");
            // a vine moves in its wall's plane; its ceiling face stays put
            h.assertTrue(PlantSway.mode(PlantSway.VINE, Direction.NORTH) == PlantSway.MODE_WALL_Z, "a north vine moves along its wall");
            h.assertTrue(PlantSway.mode(PlantSway.VINE, Direction.EAST) == PlantSway.MODE_WALL_X, "an east vine moves along its wall");
            h.assertTrue(PlantSway.alpha(PlantSway.VINE, Direction.UP, 0.5F, 0, 2) == 255, "a ceiling vine stays put");
            h.succeed();
        });
    }

    /** The marker of a vertex at height {@code y} in block {@code i} (0 = bottom) of a run {@code len} blocks long. */
    private static int alongRun(PlantSway.Rule rule, Direction face, float y, int i, int len) {
        int below = Math.min(i, PlantSway.CHAIN_REACH), above = Math.min(len - 1 - i, PlantSway.CHAIN_REACH);
        return PlantSway.alpha(rule, face, y, below, above);
    }

    private static void plant(GameTestHelper h, BlockState s) {
        PlantSway.Rule rule = PlantSway.rule(s);
        h.assertTrue(rule != null, s + " sways");
        h.assertTrue(!PlantSway.isLeaf(s), s + " is not a leaf");
        boolean moves = false;
        for (int v = 0; v < rule.variants() && !moves; v++) for (Direction f : FACES) for (float y : new float[] {0F, 1F}) {
            moves |= PlantSway.alpha(rule, f, y, v / (PlantSway.CHAIN_REACH + 1), v % (PlantSway.CHAIN_REACH + 1)) != 255;
        }
        h.assertTrue(moves, s + " has vertices that move");
    }
}
