package dev.awaken.story.client;

import dev.awaken.core.api.FirstPerson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * The Ritual of the Bell in first person (Hands P6; SPEC §24): your hands through the beats that ask something of you.
 * Kneeling in the bow both palms go flat to the floor before you; as the god's eyes open the main hand comes up against
 * the bloom; at the End both hands rise, palms out, shaking harder as the light drains into its palm, and drop at the
 * error. Fist stances (the anims module draws them), eased in and out; nothing here touches the scene's timing.
 */
final class RitualFp {
    private RitualFp() {}

    private static long kneelSince = -1;

    static void viewPose(float pt, FirstPerson.View v) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !RitualClient.inScene()) {
            kneelSince = -1;
            return;
        }
        float m = p.getMainArm() == HumanoidArm.LEFT ? -1 : 1;
        float age = RitualClient.beatAge(pt), t = RitualClient.ticks + pt;
        boolean still = FirstPerson.reducedMotion.getAsBoolean();
        switch (RitualClient.beat) {
            case RitualClient.BOW -> {
                // kneeling: the palms flat to the stone before you
                if (!p.isCrouching()) {
                    kneelSince = -1;
                    return;
                }
                if (kneelSince < 0) kneelSince = RitualClient.ticks;
                float k = smooth((RitualClient.ticks - kneelSince + pt) / 6F);
                FirstPerson.View.fist(v.fistMain, k, m * 0.3F, -0.64F, -0.8F);
                FirstPerson.View.fist(v.fistOff, k, -m * 0.3F, -0.64F, -0.8F);
            }
            case RitualClient.WAKING -> {
                // the eyes open (the bloom at 58): the main hand comes up against the light, then lowers
                float k = Math.min(smooth((age - 50) / 8F), 1 - smooth((age - 120) / 12F));
                if (k > 0) FirstPerson.View.fist(v.fistMain, k, m * 0.16F, 0.1F, -0.52F);
            }
            case RitualClient.END -> {
                // the hand rises and the hall's light drains into its palm: both hands up, palms out, shaking harder;
                // at the error (195) they drop
                float k = Math.min(smooth((age - 30) / 12F), 1 - smooth((age - 195) / 6F));
                if (k <= 0) return;
                float drain = Mth.clamp((age - 40) / 150F, 0, 1), shake = still ? 0 : 0.004F + 0.014F * drain;
                FirstPerson.View.fist(v.fistMain, k, m * 0.28F, -0.12F + shake * Mth.sin(t * 2.3F), -0.84F);
                FirstPerson.View.fist(v.fistOff, k, -m * 0.28F, -0.12F + shake * Mth.sin(t * 2.7F + 1F), -0.84F);
            }
            default -> kneelSince = -1;
        }
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
