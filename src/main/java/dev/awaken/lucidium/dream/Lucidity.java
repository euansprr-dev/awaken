package dev.awaken.lucidium.dream;

import net.minecraft.util.Mth;

/**
 * Lucidity maths (SPEC section 9). Lucidity runs 0..100. These are real lucid-dreaming techniques turned into
 * numbers: MILD (intention), WBTB (wake back to bed), Recall (journal entries), reality checks and dream signs.
 * SPI multiplies every gain by +1% per point.
 */
public final class Lucidity {
    private Lucidity() {}

    public static final float MAX = 100f;
    public static final float LUCID = 50f;
    public static final float MASTERY = 80f;
    /** Flying comes earlier when you went to sleep intending to fly. */

    public static final float INTENTION = 10f;
    public static final float WBTB = 25f;
    public static final float PER_RECALL = 0.5f;
    public static final int RECALL_CAP = 20;
    public static final float REALITY_CHECK = 12f;
    public static final float DREAM_SIGN = 15f;
    public static final float DOOR_FOUND = 20f;
    public static final float NIGHTMARE_BEATEN = 10f;
    public static final float FIRST_FALL = 5f;

    /** Starting lucidity from the evening's preparation. */
    public static float start(boolean intention, boolean wbtb, int recallEntries, double perkBonus) {
        float v = 0;
        if (intention) v += INTENTION;
        if (wbtb) v += WBTB;
        v += PER_RECALL * Math.min(RECALL_CAP, Math.max(0, recallEntries));
        v += (float) Math.max(0, perkBonus);
        return clamp(v);
    }

    /** Every gain is scaled by SPI: +1% per point. */
    public static float gainMultiplier(int spi) {
        return 1f + 0.01f * Math.max(0, spi);
    }

    public static float gain(float current, float amount, int spi) {
        return clamp(current + amount * gainMultiplier(spi));
    }

    /** Reality checks lose their bite when spammed: the sixth and later checks in one dream count half. */
    public static float realityCheckAmount(int checksSoFar) {
        return checksSoFar >= 5 ? REALITY_CHECK * 0.5f : REALITY_CHECK;
    }

    public static float clamp(float v) {
        return Mth.clamp(v, 0f, MAX);
    }
}
