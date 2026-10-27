package dev.awaken.powers.client.fx;

import dev.awaken.core.api.FirstPerson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Your own boot in first person for the stamp (docs/power-anims/PLAN.md section 5.4, decision D5), a world source of the
 * Hands system: the stamping leg comes up from under the frame, the boot hangs at the bottom of the view, above the hotbar, for a beat, then
 * drives down out of it on the plant (tick 5 of {@code CastProfiles} "shockwave"). A real stomp is out of frame (the foot is
 * 75 degrees below the eye), so the boot's places are authored on screen, in the hands' view space, and put into the world
 * the way the hands are ({@link Muzzles#viewToWorld}); Hands draws a leg that near the eye in its own pass. Off with the first
 * person moves setting or the cast animations setting, and under reduced motion.
 */
final class CastLegs {
    private CastLegs() {}

    /** The boot's sole on screen, right-handed (view space: x right, y up, z toward you), by tick: x, y, z, how much shows. */
    private static final float[][] KEYS = {
            {0, 0.14f, -1.35f, -0.55f, 0f}, {1, 0.15f, -1.05f, -0.75f, 0.6f}, {2, 0.17f, -0.62f, -0.95f, 1f},
            {3, 0.18f, -0.36f, -1.00f, 1f}, {4, 0.18f, -0.36f, -1.00f, 1f}, {5, 0.14f, -1.00f, -1.00f, 1f},
            {7, 0.14f, -1.25f, -0.90f, 0.6f}, {9, 0.14f, -1.40f, -0.80f, 0f}};
    private static final double[] FOOT = new double[3];

    /** The world source (Hands, {@code FirstPerson.world}): fills the stamping leg while the stamp's clip runs. */
    static boolean pose(float pt, FirstPerson.Rig rig) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !PowerFxConfig.animations() || FirstPerson.reducedMotion.getAsBoolean()) return false;
        float age = Math.max(CastAnims.profileAge(p.getId(), CastProfilesIds.SHOCKWAVE, pt), CastAnims.profileAge(p.getId(), CastProfilesIds.SPIRE, pt));
        if (age < 0 || age > 9 || !mc.options.getCameraType().isFirstPerson()) return false;
        int k = 1;
        while (k < KEYS.length - 1 && age > KEYS[k][0]) k++;
        float[] a = KEYS[k - 1], b = KEYS[k];
        float u = Mth.clamp((age - a[0]) / (b[0] - a[0]), 0f, 1f);
        u = u * u * (3 - 2 * u);
        boolean left = p.getMainArm() == HumanoidArm.LEFT;
        float x = Mth.lerp(u, a[1], b[1]) * (left ? -1 : 1), y = Mth.lerp(u, a[2], b[2]), z = Mth.lerp(u, a[3], b[3]);
        float w = Mth.lerp(u, a[4], b[4]);
        Muzzles.viewToWorld(x, y, z, FOOT);
        float yaw = p.getViewYRot(pt) * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw), rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);
        double side = left ? -0.12 : 0.12;
        double px = Mth.lerp(pt, p.xo, p.getX()), py = Mth.lerp(pt, p.yo, p.getY()), pz = Mth.lerp(pt, p.zo, p.getZ());
        rig.fx = fx;
        rig.fz = fz;
        rig.reach(left ? FirstPerson.Limb.LEFT_LEG : FirstPerson.Limb.RIGHT_LEG, px + rx * side, py + 0.75, pz + rz * side,
                FOOT[0], FOOT[1], FOOT[2], FirstPerson.Rig.LEG_REACH, fx, 0, fz, w);
        return true;
    }

    /** A view stance: the hands glow with the beam's charge while it is held (the lit palm). */
    static void glow(float pt, FirstPerson.View view) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !CastAnims.holding(p.getId(), CastProfilesIds.BEAM)) return;
        float age = CastAnims.profileAge(p.getId(), CastProfilesIds.BEAM, pt);
        view.glow = Math.max(view.glow, Mth.clamp((age - 1) / 5f, 0f, 1f) * 0.8f);
    }
}
