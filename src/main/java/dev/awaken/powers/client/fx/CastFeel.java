package dev.awaken.powers.client.fx;

import dev.awaken.core.AwakenConfig;
import dev.awaken.core.api.CombatFeedback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * The weight of a cast on the screen (docs/power-anims/PLAN.md section 5.6), through the one feel every blow has
 * ({@link CombatFeedback}: the view's kick, the striker's hold): the stamp's thump and the beam's first recoil.
 * Honours the camera shake setting; Arms' own comfort switches (kick, hit-stop) apply on top.
 */
final class CastFeel {
    private CastFeel() {}

    /** Within this many blocks a stamp is felt at all. */
    private static final double REACH = 16;

    /**
     * A heavy plant at (x, y, z): the view dips and settles (a kick down, decaying over nine ticks, a little roll), the
     * field narrows for a beat, and if it is yours, your hands and view hold for the frame the foot lands.
     */
    static void thump(double x, double y, double z, float strength, boolean own) {
        if (!AwakenConfig.Client.CAMERA_SHAKE.get()) return;
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        double d = p.position().distanceTo(new Vec3(x, y, z));
        if (!own && d > REACH) return;
        float near = own ? 1f : (float) Math.max(0, 1 - d / REACH);
        if (near <= 0) return;
        float roll = (p.getId() % 2 == 0 ? 1 : -1) * 0.7f * near * strength;
        CombatFeedback.kick(0f, 2.6f * near * strength, roll, 9);
        FxScreen.fovPunch(-0.03f * near * strength, 7);
        if (own) CombatFeedback.hold(55);
    }

    /** The beam's arm arrives on the aim: a small recoil (the view kicks up a little and settles). */
    static void beamStart() {
        if (!AwakenConfig.Client.CAMERA_SHAKE.get()) return;
        CombatFeedback.kick(0f, -0.7f, 0f, 6);
    }
}
