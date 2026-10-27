package dev.awaken.terrain;

import dev.awaken.terrain.block.CrystalClusterBlock;
import dev.awaken.terrain.block.LumenCapBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CactusFlowerBlock;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.CaveVinesPlantBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.DryVegetationBlock;
import net.minecraft.world.level.block.FireflyBushBlock;
import net.minecraft.world.level.block.FlowerBedBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.HangingMossBlock;
import net.minecraft.world.level.block.HangingRootsBlock;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.KelpPlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LilyPadBlock;
import net.minecraft.world.level.block.MangrovePropaguleBlock;
import net.minecraft.world.level.block.NetherRootsBlock;
import net.minecraft.world.level.block.NetherSproutsBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SeagrassBlock;
import net.minecraft.world.level.block.SporeBlossomBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.AzaleaBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.TallSeagrassBlock;
import net.minecraft.world.level.block.TwistingVinesBlock;
import net.minecraft.world.level.block.TwistingVinesPlantBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WeepingVinesBlock;
import net.minecraft.world.level.block.WeepingVinesPlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jspecify.annotations.Nullable;

/**
 * Which blocks sway in the wind and how far each vertex of their models moves (SPEC 15, "Swaying plants"). Common
 * code with no client types, so the GameTests can hold every rule; the client's waving models
 * ({@code terrain.client.WavingModel}) bake the answer into vertex colour alpha and Awaken's terrain shader moves the
 * marked vertices.
 *
 * <p>The marker byte. 255 is every other block and 254 a leaf (the canopy's own sway). A plant vertex carries
 * {@code 253 - code}: {@code code = mode * 8 + level - 1} for the five modes below, the vertex moving {@code level / 4}
 * times the mode's amplitude (level 1..8), and {@link #FLOAT_CODE} for a pad floating on water. A vertex that must
 * not move (a plant's base, a hanging plant's anchor) keeps 255. A vertex's level depends only on where it is in its
 * plant (its height in the block, which half of a double plant, how far along a stalk or strand), never on the quad,
 * so every vertex two parts share (the halves of tall grass, the blocks of a kelp stalk) moves the same way: no
 * cracks, and the base never slides.
 */
public final class PlantSway {
    private PlantSway() {}

    /** The canopy marker (unchanged since the leaves spike). */
    public static final int LEAF_ALPHA = 254;
    /** Plant markers run from {@link #MIN_ALPHA} to 253. */
    public static final int MIN_ALPHA = 213, MAX_ALPHA = 253;
    public static final int LEVELS = 8;
    /** Shader modes: air rooted (bends from its base, tips dip), air hanging (swings from its anchor, tips rise),
     * water (a slow current, not the wind), and a wall vine held in the wall's plane (normal along x or z). */
    public static final int MODE_ROOTED = 0, MODE_HANGING = 1, MODE_WATER = 2, MODE_WALL_X = 3, MODE_WALL_Z = 4;
    public static final int FLOAT_CODE = 40;
    /** Stalks and strands look this many blocks along themselves (Sodium's meshing snapshot holds 2 blocks beyond a
     * section, so a longer look would disagree across section borders and open cracks). */
    public static final int CHAIN_REACH = 2;

    /** Where a plant is held, which decides the weight of each vertex. */
    public enum Anchor {
        /** rooted in the block below: weight from the height in the block */
        GROUND(false, false),
        /** the lower or upper half of a two-block plant (by its state) */
        LOWER(false, false), UPPER(false, false),
        /** a stalk rooted at the bottom of a run of the same plant (kelp, sugar cane, twisting vines) */
        STALK(true, false),
        /** bamboo: rooted at the bottom, and only the top of the stalk bends */
        CROWN(true, true),
        /** hanging from the block above */
        CEILING(false, false),
        /** a strand hanging from the top of a run of the same plant (weeping and cave vines, hanging moss, vines) */
        STRAND(false, true),
        /** floating on water: the whole pad bobs */
        PAD(false, false),
        /** a plant standing in a slab's own block (the soft steps' tuft): rooted at the block's half height, nothing below it moves */
        SLAB(false, false);

        public final boolean readsBelow, readsAbove;

        Anchor(boolean readsBelow, boolean readsAbove) {
            this.readsBelow = readsBelow;
            this.readsAbove = readsAbove;
        }

        public boolean chain() {
            return this == STALK || this == CROWN || this == STRAND;
        }
    }

    /** How the shader moves a plant's vertices. WALL resolves to {@link #MODE_WALL_X} or {@link #MODE_WALL_Z} per quad. */
    public enum Motion { AIR, WATER, WALL, FLOAT }

    /**
     * One way of swaying. {@code gain} scales the weight (sugar cane and bamboo move gently, vines on walls a little);
     * {@code family} names the run of blocks a stalk or strand counts along (0 for single plants).
     */
    public record Rule(String name, Motion motion, Anchor anchor, float gain, int family) {
        /** Model variants a rule needs: one per (blocks below, blocks above) along a stalk or strand. */
        public int variants() {
            return anchor.chain() ? (CHAIN_REACH + 1) * (CHAIN_REACH + 1) : 1;
        }

        public static int variant(int below, int above) {
            return below * (CHAIN_REACH + 1) + above;
        }

        public boolean wet() {
            return motion == Motion.WATER;
        }
    }

    // run families: the blocks a stalk or strand continues through
    public static final int F_NONE = 0, F_KELP = 1, F_TWISTING = 2, F_WEEPING = 3, F_CAVE = 4, F_MOSS = 5, F_CANE = 6,
            F_BAMBOO = 7, F_VINE = 8;

    public static final Rule ROOTED = new Rule("rooted", Motion.AIR, Anchor.GROUND, 1F, F_NONE);
    public static final Rule ROOTED_WET = new Rule("rooted_wet", Motion.WATER, Anchor.GROUND, 1F, F_NONE);
    public static final Rule LOWER = new Rule("lower", Motion.AIR, Anchor.LOWER, 1F, F_NONE);
    public static final Rule UPPER = new Rule("upper", Motion.AIR, Anchor.UPPER, 1F, F_NONE);
    public static final Rule LOWER_WET = new Rule("lower_wet", Motion.WATER, Anchor.LOWER, 1F, F_NONE);
    public static final Rule UPPER_WET = new Rule("upper_wet", Motion.WATER, Anchor.UPPER, 1F, F_NONE);
    /** pink petals and wildflowers: a few pixels tall, so they nod more than their height alone would give */
    public static final Rule FLOWERBED = new Rule("flowerbed", Motion.AIR, Anchor.GROUND, 3F, F_NONE);
    /** azalea bushes: a leafy crown on a stem, rocking gently */
    public static final Rule SHRUB = new Rule("shrub", Motion.AIR, Anchor.GROUND, 0.5F, F_NONE);
    public static final Rule CANE = new Rule("cane", Motion.AIR, Anchor.STALK, 0.45F, F_CANE);
    public static final Rule BAMBOO = new Rule("bamboo", Motion.AIR, Anchor.CROWN, 0.5F, F_BAMBOO);
    public static final Rule TWISTING = new Rule("twisting", Motion.AIR, Anchor.STALK, 0.8F, F_TWISTING);
    public static final Rule KELP = new Rule("kelp", Motion.WATER, Anchor.STALK, 1F, F_KELP);
    public static final Rule HANGING = new Rule("hanging", Motion.AIR, Anchor.CEILING, 1F, F_NONE);
    public static final Rule HANGING_WET = new Rule("hanging_wet", Motion.WATER, Anchor.CEILING, 1F, F_NONE);
    public static final Rule WEEPING = new Rule("weeping", Motion.AIR, Anchor.STRAND, 0.9F, F_WEEPING);
    public static final Rule CAVE_VINES = new Rule("cave_vines", Motion.AIR, Anchor.STRAND, 0.9F, F_CAVE);
    public static final Rule MOSS = new Rule("moss", Motion.AIR, Anchor.STRAND, 0.9F, F_MOSS);
    public static final Rule VINE = new Rule("vine", Motion.WALL, Anchor.STRAND, 0.5F, F_VINE);
    public static final Rule PAD = new Rule("pad", Motion.FLOAT, Anchor.PAD, 1F, F_NONE);
    /** the turf slab's tuft (dev.awaken.ground): grass, fern, flower or bush drawn from the slab's top, y 0.5, up */
    public static final Rule SLAB_TUFT = new Rule("slab_tuft", Motion.AIR, Anchor.SLAB, 1F, F_NONE);

    /** Every rule, in a fixed order (the client keeps one model cache per rule). */
    public static final Rule[] RULES = {ROOTED, ROOTED_WET, LOWER, UPPER, LOWER_WET, UPPER_WET, FLOWERBED, SHRUB, CANE, BAMBOO,
            TWISTING, KELP, HANGING, HANGING_WET, WEEPING, CAVE_VINES, MOSS, VINE, PAD, SLAB_TUFT};

    /** True for blocks whose models get the leaf marker (the canopy sway). */
    public static boolean isLeaf(BlockState state) {
        return state.getBlock() instanceof LeavesBlock;
    }

    /**
     * The sway rule for a block state, or null when it stands still. By class, because tags are not bound when models
     * bake: vanilla plants, any modded plant that extends them, and Awaken's own (its saplings, lumen vines, weepwood
     * tendrils and moonblooms extend vanilla classes). Mushrooms, fungi, sea pickles, leaf litter, corals, crystals
     * and lumen caps are plants in name only: they stay still.
     */
    public static @Nullable Rule rule(BlockState state) {
        Block b = state.getBlock();
        boolean wet = !state.getFluidState().isEmpty();
        if (b instanceof LeavesBlock || b instanceof CrystalClusterBlock || b instanceof LumenCapBlock) return null;
        if (b instanceof dev.awaken.ground.TurfSlabBlock) return SLAB_TUFT;      // its tuft sways; the slab's own vertices (y <= 0.5) stay
        // underwater plants
        if (b instanceof TallSeagrassBlock) return state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER ? UPPER_WET : LOWER_WET;
        if (b instanceof SeagrassBlock) return ROOTED_WET;
        if (b instanceof KelpBlock || b instanceof KelpPlantBlock) return KELP;
        // stalks and strands
        if (b instanceof SugarCaneBlock) return CANE;
        if (b instanceof BambooStalkBlock) return BAMBOO;
        if (b instanceof TwistingVinesBlock || b instanceof TwistingVinesPlantBlock) return TWISTING;
        if (b instanceof WeepingVinesBlock || b instanceof WeepingVinesPlantBlock) return WEEPING;
        if (b instanceof CaveVinesBlock || b instanceof CaveVinesPlantBlock) return CAVE_VINES;
        if (b instanceof HangingMossBlock) return MOSS;
        if (b instanceof VineBlock) return VINE;
        // hanging singles
        if (b instanceof HangingRootsBlock || b instanceof SporeBlossomBlock) return wet ? HANGING_WET : HANGING;
        if (b instanceof MangrovePropaguleBlock && state.getValue(MangrovePropaguleBlock.HANGING)) return wet ? HANGING_WET : HANGING;
        // on the water
        if (b instanceof LilyPadBlock) return PAD;
        // rooted: two-block plants by half (tall grass, large fern, tall flowers, pitcher plant and crop, small dripleaf)
        if (b instanceof DoublePlantBlock) {
            boolean upper = state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER;
            return wet ? (upper ? UPPER_WET : LOWER_WET) : (upper ? UPPER : LOWER);
        }
        if (b instanceof FlowerBedBlock) return FLOWERBED;
        if (b instanceof AzaleaBlock) return SHRUB;
        if (b instanceof TallGrassBlock || b instanceof FlowerBlock || b instanceof SaplingBlock || b instanceof BambooSaplingBlock
                || b instanceof CropBlock || b instanceof NetherWartBlock || b instanceof SweetBerryBushBlock
                || b instanceof DryVegetationBlock || b instanceof BushBlock || b instanceof FireflyBushBlock
                || b instanceof NetherRootsBlock || b instanceof NetherSproutsBlock || b instanceof CactusFlowerBlock
                || b instanceof StemBlock || b instanceof AttachedStemBlock) {
            return wet ? ROOTED_WET : ROOTED;
        }
        return null;
    }

    /** The run a block continues (for counting along a stalk or strand), {@link #F_NONE} for none. */
    public static int family(Block b) {
        if (b instanceof KelpBlock || b instanceof KelpPlantBlock) return F_KELP;
        if (b instanceof SugarCaneBlock) return F_CANE;
        if (b instanceof BambooStalkBlock) return F_BAMBOO;
        if (b instanceof TwistingVinesBlock || b instanceof TwistingVinesPlantBlock) return F_TWISTING;
        if (b instanceof WeepingVinesBlock || b instanceof WeepingVinesPlantBlock) return F_WEEPING;
        if (b instanceof CaveVinesBlock || b instanceof CaveVinesPlantBlock) return F_CAVE;
        if (b instanceof HangingMossBlock) return F_MOSS;
        if (b instanceof VineBlock) return F_VINE;
        return F_NONE;
    }

    /**
     * How far one vertex of a plant sways, before quantising: {@code y} is the vertex's height in its block's model
     * (0 at the bottom, 1 at the top; a crop's -1/16 counts as 0), {@code below} and {@code above} how many blocks of
     * the same run lie below and above (0..{@link #CHAIN_REACH}). Rooted plants bend from the base, more toward the
     * tip ({@code 2 (h/2)^1.4} for a height h over the root, so tall grass's top sways more than its middle and short
     * grass's top); hanging ones swing in proportion to the distance from their anchor. Stalks and strands saturate
     * {@link #CHAIN_REACH} blocks from their root or anchor; the shader ripples them along their length past that.
     */
    public static float weight(Rule rule, float y, int below, int above) {
        float yy = Math.clamp(y, 0F, 1F);
        float h;
        boolean hanging;
        switch (rule.anchor) {
            case GROUND -> { h = yy; hanging = false; }
            case SLAB -> { h = Math.max(0F, (yy - 0.5F) * 2F); hanging = false; }
            case LOWER -> { h = yy; hanging = false; }
            case UPPER -> { h = 1F + yy; hanging = false; }
            case STALK -> { h = Math.min(below + yy, CHAIN_REACH); hanging = false; }
            case CROWN -> {
                float fromRoot = Math.min(below + yy, CHAIN_REACH), fromTop = Math.min(above + 1F - yy, CHAIN_REACH);
                h = Math.min(fromRoot, CHAIN_REACH - fromTop);
                hanging = false;
            }
            case CEILING -> { h = 1F - yy; hanging = true; }
            case STRAND -> { h = Math.min(above + 1F - yy, CHAIN_REACH); hanging = true; }
            default -> { return 1F; } // PAD: every vertex bobs alike
        }
        float w = hanging ? h : (float) (2.0 * Math.pow(h / 2.0, 1.4));
        return w * rule.gain;
    }

    /** {@link #weight} in quarters, 0 (still) to {@link #LEVELS}. */
    public static int level(Rule rule, float y, int below, int above) {
        return Math.clamp(Math.round(weight(rule, y, below, above) * 4F), 0, LEVELS);
    }

    /** The shader mode of a rule's quad facing {@code face}, or -1 when that quad stays still (a vine's ceiling face). */
    public static int mode(Rule rule, Direction face) {
        return switch (rule.motion) {
            case WATER -> MODE_WATER;
            case FLOAT -> -2;
            case WALL -> face.getAxis() == Direction.Axis.X ? MODE_WALL_X : face.getAxis() == Direction.Axis.Z ? MODE_WALL_Z : -1;
            case AIR -> rule.anchor == Anchor.CEILING || rule.anchor == Anchor.STRAND ? MODE_HANGING : MODE_ROOTED;
        };
    }

    /** The marker alpha for one vertex: 255 when it stays still, else 213..253. */
    public static int alpha(Rule rule, Direction face, float y, int below, int above) {
        int mode = mode(rule, face);
        if (mode == -2) return MAX_ALPHA - FLOAT_CODE;
        if (mode < 0) return 255;
        int level = level(rule, y, below, above);
        return level == 0 ? 255 : MAX_ALPHA - (mode * LEVELS + level - 1);
    }
}
