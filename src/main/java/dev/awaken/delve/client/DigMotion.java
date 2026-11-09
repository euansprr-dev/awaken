package dev.awaken.delve.client;

import dev.awaken.core.api.FirstPerson;
import dev.awaken.delve.forged.ForgedPicks;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * The dig, in your hands (SPEC §34.3): while you hold the attack on a block, a pick swings in a rhythm keyed to the
 * strike's bites (one every four ticks): a short recoil off the rock at the bite, a wind-up back and over, and the
 * strike coming down to the next bite. A shovel scoops: drawn back low, thrust in and lifted. Added on top of the hands
 * (core.api.FirstPerson), eased in and out over a few ticks, smaller with reduced motion.
 */
final class DigMotion {
    private DigMotion() {}

    // keys over one bite (phase 0 = the bite's contact): phase, then x, y, z (blocks), pitch, roll (degrees)
    private static final float[][] PICK = {
            {0.00F, 0F, -0.03F, -0.05F, -15F, 0F},
            {0.16F, 0F, 0.00F, -0.02F, -6F, 1F},    // the recoil off the rock
            {0.68F, 0.01F, 0.06F, 0.07F, 17F, 3F},   // wound up, back and over
            {1.00F, 0F, -0.03F, -0.05F, -15F, 0F},   // down onto the next bite
    };
    private static final float[][] SHOVEL = {
            {0.00F, 0F, -0.01F, -0.09F, 10F, -4F},   // thrust in, lifting
            {0.30F, 0F, -0.02F, -0.05F, 6F, -2F},
            {0.72F, 0F, -0.06F, 0.06F, -9F, 2F},     // drawn back low
            {1.00F, 0F, -0.01F, -0.09F, 10F, -4F},
    };
    private static final float[] OUT = new float[6];

    // A forged pick, two hands, from how the real tool is used (SPEC §49.8): the leading hand stays at the foot of the haft
    // and the other rides it, high by the head at the top of the lift and sliding down to meet the first at the bite. A big
    // blow and a small one in turn. Keys as above, then where the upper hand is on the haft (the design's units from the grip).
    private static final float[] BIG_BITE = {0.00F, -0.25F, -0.03F, -0.04F, -12F, -4F, 3F}, SMALL_BITE = {0.00F, -0.20F, -0.02F, -0.03F, -9F, -3F, 7F};
    private static final float[][] TO_SMALL = {
            BIG_BITE,
            {0.18F, -0.20F, 0.00F, -0.02F, -4F, -2F, 4F},   // the recoil off the rock
            {0.66F, -0.12F, 0.02F, 0.03F, 9F, 1F, 9F},      // a short lift
            {1.00F, -0.20F, -0.02F, -0.03F, -9F, -3F, 7F},
    };
    private static final float[][] TO_BIG = {
            SMALL_BITE,
            {0.16F, -0.17F, 0.00F, -0.015F, -4F, -2F, 7F},
            {0.68F, -0.06F, 0.06F, 0.08F, 24F, 6F, 12F},    // the full lift, the upper hand up by the head
            {1.00F, -0.25F, -0.03F, -0.04F, -12F, -4F, 3F},
    };
    /** The haft in view space at rest (the right hand's hold): where it is at the grip, and how it runs per design unit. */
    private static final float HAFT_X = 0.54F, HAFT_Y = -0.62F, HAFT_Z = -0.68F, RUN_X = -0.0004F, RUN_Y = 0.02417F, RUN_Z = -0.01177F;
    private static final float[][][] LIFTS = {TO_SMALL, TO_BIG};

    /** How strongly the dig is in the hands now (0 not digging): held, or just let go; eased in from rest as it begins. */
    private static float weight(float pt) {
        float idle = StrikeFeel.clock - StrikeFeel.lastHold + pt - 1F;
        float w = Mth.clamp(1F - idle / 3F, 0F, 1F);
        float in = Mth.clamp((StrikeFeel.clock - StrikeFeel.dugFrom + pt) / 1.5F, 0F, 1F);
        return Math.min(w, in);
    }

    /** Whether your own forged pick is in the dig's hands this frame (its transform replaces vanilla's). */
    static boolean digging(ItemStack stack, net.minecraft.world.entity.HumanoidArm arm, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || stack != mc.player.getMainHandItem() || arm != mc.player.getMainArm() || !ForgedPicks.is(stack)) return false;
        return weight(pt) > 0F;
    }

    private static void forged(float pt, FirstPerson.View v, ItemStack held) {
        float w = weight(pt);
        if (w <= 0F) return;
        float since = StrikeFeel.clock - StrikeFeel.lastBite + pt;
        if (since < 0) return;
        float[][] keys = LIFTS[StrikeFeel.bites & 1];
        float phase = Math.min(since / StrikeFeel.BITE_EVERY, 1F);
        sample(keys, phase);
        boolean reduced = FirstPerson.reducedMotion.getAsBoolean();
        float k = w * w * (3F - 2F * w) * (reduced ? 0.4F : 1F);
        boolean leftMain = Minecraft.getInstance().player.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT;
        float m = leftMain ? -1F : 1F;
        FirstPerson.View.add(v.main, k, m * OUT[0], OUT[1], OUT[2], OUT[3], 0F, m * OUT[4]);
        // the upper hand on the haft, wherever the swing has carried it; a torch or a shield keeps the off hand where it is
        if (!Minecraft.getInstance().player.getOffhandItem().isEmpty() || FirstPerson.offHandShows.getAsBoolean()) return;
        float s = OUT[5];
        float x = m * (HAFT_X + RUN_X * s), y = HAFT_Y + RUN_Y * s, z = HAFT_Z + RUN_Z * s;
        // as Hands applies the main hand's moves: a move, then pitch, then roll about the eye
        float p = k * OUT[3] * Mth.DEG_TO_RAD, r = k * m * OUT[4] * Mth.DEG_TO_RAD;
        float cr = Mth.cos(r), sr = Mth.sin(r), x1 = x * cr - y * sr, y1 = x * sr + y * cr;
        float cp = Mth.cos(p), sp = Mth.sin(p), y2 = y1 * cp - z * sp, z2 = y1 * sp + z * cp;
        FirstPerson.View.fist(v.fistOff, k, x1 + k * m * OUT[0], y2 + k * OUT[1], z2 + k * OUT[2]);
    }

    static void pose(float pt, FirstPerson.View v) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack held = mc.player.getMainHandItem();
        if (ForgedPicks.is(held)) {
            forged(pt, v, held);
            return;
        }
        float[][] keys = held.is(ItemTags.PICKAXES) || held.is(dev.awaken.delve.DelveContent.MINING_TOOLS) ? PICK
                : held.is(ItemTags.SHOVELS) ? SHOVEL : null;
        if (keys == null) return;
        float idle = StrikeFeel.clock - StrikeFeel.lastHold + pt - 1F;
        float w = Mth.clamp(1F - idle / 3F, 0F, 1F);
        if (w <= 0F) return;
        float since = StrikeFeel.clock - StrikeFeel.lastBite + pt;
        if (since < 0) return;
        float phase = (since % StrikeFeel.BITE_EVERY) / StrikeFeel.BITE_EVERY;
        sample(keys, phase);
        float k = w * w * (3F - 2F * w) * (FirstPerson.reducedMotion.getAsBoolean() ? 0.4F : 1F);
        FirstPerson.View.add(v.main, k, OUT[0], OUT[1], OUT[2], OUT[3], 0F, OUT[4]);
    }

    /** The pose at a phase: eased between the keys either side (a strike snaps, so the last span eases in). */
    private static void sample(float[][] keys, float phase) {
        for (int i = 0; i < keys.length - 1; i++) {
            float[] a = keys[i], b = keys[i + 1];
            if (phase > b[0] && i < keys.length - 2) continue;
            float t = Mth.clamp((phase - a[0]) / (b[0] - a[0]), 0F, 1F);
            t = i == keys.length - 2 ? t * t * t : t * t * (3F - 2F * t);
            for (int j = 0; j < a.length - 1; j++) OUT[j] = Mth.lerp(t, a[j + 1], b[j + 1]);
            return;
        }
    }
}
