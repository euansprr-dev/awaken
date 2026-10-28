package dev.awaken.delve.client;

import dev.awaken.core.api.FirstPerson;
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
    private static final float[] OUT = new float[5];

    static void pose(float pt, FirstPerson.View v) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack held = mc.player.getMainHandItem();
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
            for (int j = 0; j < 5; j++) OUT[j] = Mth.lerp(t, a[j + 1], b[j + 1]);
            return;
        }
    }
}
