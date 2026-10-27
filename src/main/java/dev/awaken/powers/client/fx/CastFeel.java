package dev.awaken.powers.client.fx;

import dev.awaken.core.AwakenConfig;
import dev.awaken.core.api.CombatFeedback;
import dev.awaken.core.api.FirstPerson;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.Map;

/**
 * The weight of a cast on the screen (docs/power-anims/PLAN.md section 5.6), through the one feel every blow has
 * ({@link CombatFeedback}: the view's kick, the striker's hold) and the effects' screen layer ({@link FxScreen}). A profile names
 * a <i>moment</i>; it plays on the tick the clips release on ({@link CastAnims}), for you in full and for others by how near they
 * are. Honours the camera shake setting and the body's reduced motion (half); Arms' own comfort switches (kick, hit-stop) apply on top.
 */
final class CastFeel {
    private CastFeel() {}

    /** The view kick (pitch + down, degrees; roll's sign alternates), its ticks, the field of view's push (negative narrows) and its ticks, the hold (ms), a flash. */
    record Moment(float pitch, float roll, int kickTicks, float fov, int fovTicks, int holdMs, float flash) {}

    static final Map<String, Moment> MOMENTS = Map.ofEntries(
            Map.entry("thump_heavy", new Moment(2.6f, 0.7f, 9, -0.03f, 7, 55, 0f)),
            Map.entry("thump_light", new Moment(1.2f, 0.4f, 7, -0.015f, 6, 30, 0f)),
            Map.entry("recoil_light", new Moment(-0.7f, 0f, 6, 0f, 0, 0, 0f)),
            Map.entry("release_big", new Moment(2.0f, 0.5f, 10, -0.04f, 9, 40, 0.10f)),
            Map.entry("push", new Moment(-0.5f, 0f, 4, 0f, 0, 0, 0f)),
            Map.entry("breath", new Moment(0f, 0f, 0, -0.015f, 18, 0, 0f)),
            Map.entry("lift", new Moment(0f, 0f, 0, 0.012f, 12, 0, 0f)),
            Map.entry("none", new Moment(0f, 0f, 0, 0f, 0, 0, 0f)));

    static {
        // the names a profile may use are CastProfiles.MOMENTS (held to the table by the power_cast_profiles test)
        if (!MOMENTS.keySet().equals(dev.awaken.powers.fx.CastProfiles.MOMENTS)) throw new IllegalStateException("CastFeel.MOMENTS differs from CastProfiles.MOMENTS");
    }

    /** Within this many blocks someone else's cast is felt at all. */
    private static final double REACH = 16;

    /** A profile's moment on the tick its clips release (or a channel's let-go): {@code caster} is who cast; you feel yours in full. */
    static void moment(String name, Entity caster, int tint) {
        Moment m = MOMENTS.get(name);
        if (m == null || m == MOMENTS.get("none")) return;
        var p = Minecraft.getInstance().player;
        if (p == null || !AwakenConfig.Client.CAMERA_SHAKE.get()) return;
        boolean own = caster.getId() == p.getId();
        float near = own ? 1f : (float) Math.max(0, 1 - p.distanceTo(caster) / REACH);
        if (near <= 0) return;
        float k = near * (FirstPerson.reducedMotion.getAsBoolean() ? 0.5f : 1f);
        if (m.pitch() != 0 || m.roll() != 0) CombatFeedback.kick(0f, m.pitch() * k, (p.getId() % 2 == 0 ? 1 : -1) * m.roll() * k, Math.max(1, m.kickTicks()));
        if (m.fov() != 0) FxScreen.fovPunch(m.fov() * k, m.fovTicks());
        if (m.flash() > 0 && own) FxScreen.flash(tint, m.flash(), 6);
        if (m.flash() > 0) dev.awaken.lens.client.LensClient.pulse(m.flash() * 3f * near); // the glow lifts with the release (Lens on)
        if (own && m.holdMs() > 0) CombatFeedback.hold(m.holdMs());
    }
}
