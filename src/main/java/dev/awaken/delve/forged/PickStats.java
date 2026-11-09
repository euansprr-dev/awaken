package dev.awaken.delve.forged;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;

/**
 * The forged picks' numbers (SPEC §49.3), in one place so the tooltip, the Armoury, the tests and the items all read the
 * same ones. Tiers run 0 (E) to 5 (S). The item carries its rank's and form's base; what the smith did (temper, colour) and
 * what it has been through (keen, blunt) are applied on top of that as it digs.
 */
public final class PickStats {
    private PickStats() {}

    public static final int TIERS = 6;
    public static final float[] SPEED = {5F, 7F, 9F, 10F, 11F, 12.5F};
    public static final int[] USES = {300, 650, 1800, 2200, 2700, 3400};
    public static final float[] DAMAGE = {2F, 3F, 4F, 4.5F, 5F, 6F};
    /** What each rank can't take. B's tag only protects migrated picks: no tag in our data needs netherite over diamond. */
    public static final java.util.List<TagKey<Block>> INCORRECT = java.util.List.of(BlockTags.INCORRECT_FOR_STONE_TOOL, BlockTags.INCORRECT_FOR_IRON_TOOL,
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL, BlockTags.INCORRECT_FOR_NETHERITE_TOOL, BlockTags.INCORRECT_FOR_NETHERITE_TOOL, BlockTags.INCORRECT_FOR_NETHERITE_TOOL);

    /** Speed and uses per step of temper: Plain, Fine, Masterwork, Flawless. */
    public static final float[] TEMPER = {0F, 0.04F, 0.08F, 0.12F};
    /** A Dressing's edge: blocks it lasts and how much faster it cuts; and how fast a blunt pick digs. */
    public static final int KEEN_BLOCKS = 200;
    public static final float KEEN = 1.1F, BLUNT = 0.3F;
    /** Worn longer for being forged in an Armoury room (the weapons' rule). */
    public static final float ARMOURY = 1.25F;

    public static final float COLOUR_STRAW = 0F, COLOUR_PEACOCK = 0.5F, COLOUR_BLUE = 0.75F;

    /** Straw cuts 8% faster, peacock is even, blue is 6% slower; anything past blue is blue. */
    public static float colourSpeed(float c) {
        c = Mth.clamp(c, COLOUR_STRAW, COLOUR_BLUE);
        return c <= COLOUR_PEACOCK ? Mth.lerp(c / COLOUR_PEACOCK, 1.08F, 1F) : Mth.lerp((c - COLOUR_PEACOCK) / (COLOUR_BLUE - COLOUR_PEACOCK), 1F, 0.94F);
    }

    /** Straw chips, 20% fewer uses; blue lasts, 30% more. */
    public static float colourUses(float c) {
        c = Mth.clamp(c, COLOUR_STRAW, COLOUR_BLUE);
        return c <= COLOUR_PEACOCK ? Mth.lerp(c / COLOUR_PEACOCK, 0.8F, 1F) : Mth.lerp((c - COLOUR_PEACOCK) / (COLOUR_BLUE - COLOUR_PEACOCK), 1F, 1.3F);
    }

    /** Rock speed before anything the smith did. */
    public static float speed(PickForm f, int tier) {
        return SPEED[tier] * f.speed;
    }

    /** Earth and wood speed before anything the smith did (only the Mattock has rules for them). */
    public static float softSpeed(PickForm f, int tier) {
        return SPEED[tier] * f.soft;
    }

    public static int baseUses(PickForm f, int tier) {
        return Math.round(USES[tier] * f.uses);
    }

    public static float damage(PickForm f, int tier) {
        return Math.max(1F, DAMAGE[tier] + f.damage);
    }

    public static float temperFactor(int temper) {
        return 1F + TEMPER[Mth.clamp(temper, 0, TEMPER.length - 1)];
    }

    /** What the smith's work does to its speed. */
    public static float smithSpeed(PickForged d) {
        return temperFactor(d.temper()) * colourSpeed(d.colour());
    }

    /** Its uses, everything counted. */
    public static int uses(PickForm f, int tier, PickForged d) {
        float n = baseUses(f, tier) * temperFactor(d.temper()) * colourUses(d.colour());
        if (d.is(PickForged.ARMOURY)) n *= ARMOURY;
        return Math.max(1, Math.round(n));
    }

    /** "Straw", "Bronze", "Peacock", "Blue": the name for where the oxide stopped. */
    public static String colourName(float c) {
        return c < 0.14F ? "Straw" : c < 0.38F ? "Bronze" : c < 0.63F ? "Peacock" : "Blue";
    }
}
