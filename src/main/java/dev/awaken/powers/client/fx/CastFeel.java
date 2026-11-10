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
    record Moment(float pitch, float roll, int kickTicks, float fov, int fovTicks, int holdMs, float flash, float yaw) {
        Moment(float pitch, float roll, int kickTicks, float fov, int fovTicks, int holdMs, float flash) {
            this(pitch, roll, kickTicks, fov, fovTicks, holdMs, flash, 0f);
        }
    }

    /** yaw, pitch, roll, kick ticks, field of view, its ticks, hold (ms), flash. */
    private static Moment own(float yaw, float pitch, float roll, int kickTicks, float fov, int fovTicks, int holdMs, float flash) {
        return new Moment(pitch, roll, kickTicks, fov, fovTicks, holdMs, flash, yaw);
    }

    /**
     * Each power's own screen moment (SPEC §32, "The arm": one for every power, in place of the eight shared ones), on its
     * release tick; a held power's is its let-go. A power not listed keeps the shared moment its profile names.
     */
    static final Map<String, Moment> POWER = Map.ofEntries(
            Map.entry("shockwave", own(0f, 2.8f, 0.8f, 9, -0.03f, 7, 55, 0f)),
            Map.entry("haymaker", own(-1.6f, 1.4f, -1.2f, 8, -0.045f, 6, 70, 0.03f)),
            Map.entry("terrain_rip", own(0f, -1.8f, 0.5f, 8, -0.02f, 8, 30, 0f)),
            Map.entry("brace", own(0f, 0.8f, 0f, 5, -0.02f, 5, 20, 0f)),
            Map.entry("thunderclap", own(0f, 1.6f, 0f, 6, -0.05f, 5, 60, 0.06f)),
            Map.entry("dash", own(0f, -0.4f, 1.4f, 5, 0.03f, 5, 0, 0f)),
            Map.entry("afterimage", own(0f, -0.3f, -1.0f, 5, 0.02f, 5, 0, 0f)),
            Map.entry("flow", own(0f, 0f, 0f, 0, -0.04f, 24, 0, 0.02f)),
            Map.entry("lightning_step", own(0f, -0.6f, 0f, 4, 0.05f, 4, 0, 0.05f)),
            Map.entry("gravity_well", own(0f, 1.0f, 0f, 8, -0.02f, 10, 25, 0f)),
            Map.entry("telekinesis", own(0f, -0.9f, 0.4f, 6, 0f, 0, 20, 0f)),
            Map.entry("gravity_flip", own(0f, -2.2f, 0f, 10, 0.03f, 10, 40, 0.03f)),
            Map.entry("beam", own(0f, -0.5f, 0f, 5, -0.01f, 6, 0, 0f)),
            Map.entry("nova", own(0f, 1.8f, 0.4f, 10, -0.05f, 9, 60, 0.06f)),
            Map.entry("barrier", own(0f, 0.9f, 0f, 6, -0.015f, 6, 25, 0f)),
            Map.entry("overload", own(0f, 2.4f, 0.6f, 12, -0.06f, 10, 80, 0.08f)),
            Map.entry("prism_lance", own(0f, -0.9f, 0f, 6, -0.02f, 6, 0, 0.04f)),
            Map.entry("shadow_step", own(0f, 0.5f, -1.6f, 4, 0.03f, 4, 0, 0f)),
            Map.entry("cloak", own(0f, 0f, 0f, 0, -0.03f, 14, 0, 0f)),
            Map.entry("echo_swap", own(0f, 0f, 2.0f, 4, 0.02f, 4, 0, 0f)),
            Map.entry("legion", own(0f, 1.4f, 0f, 12, -0.04f, 14, 40, 0.04f)),
            Map.entry("dominate", own(0f, 0f, 0f, 0, -0.03f, 16, 0, 0f)),
            Map.entry("read_intent", own(0f, 0f, 0f, 0, -0.02f, 14, 0, 0f)),
            Map.entry("decoy", own(0f, 0f, 0.8f, 4, 0f, 0, 0, 0f)),
            Map.entry("foresight", own(0f, 0f, 0f, 0, -0.05f, 20, 0, 0.03f)),
            Map.entry("wall", own(0f, 0.8f, 0f, 6, -0.01f, 5, 20, 0f)),
            Map.entry("ramp", own(0f, -0.6f, 0.4f, 5, 0f, 0, 0, 0f)),
            Map.entry("floor", own(0f, 1.0f, 0f, 5, 0f, 0, 20, 0f)),
            Map.entry("cone", own(0f, -0.4f, 0f, 4, 0f, 0, 0, 0f)),
            Map.entry("rampart", own(0f, -1.4f, 0f, 10, -0.02f, 10, 40, 0f)),
            Map.entry("spire", own(0f, -1.8f, 0f, 8, 0.02f, 8, 35, 0f)),
            Map.entry("collapse", own(0f, 2.2f, 0.6f, 9, -0.03f, 8, 50, 0f)),
            Map.entry("citadel", own(0f, 2.6f, 0f, 12, -0.05f, 12, 70, 0.05f)),
            Map.entry("grapple", own(0f, -0.9f, 0f, 5, 0f, 0, 0, 0f)),
            Map.entry("jet_burst", own(0f, 1.2f, 0f, 5, 0.04f, 5, 0, 0f)),
            Map.entry("drone", own(0f, -0.5f, 0f, 5, 0f, 0, 0, 0f)),
            Map.entry("turret", own(0f, 0.9f, 0f, 6, 0f, 0, 20, 0f)),
            Map.entry("nanite_skin", own(0f, 0f, 0f, 0, -0.015f, 10, 0, 0f)),
            Map.entry("drone_swarm", own(0f, -1.2f, 0f, 8, 0.02f, 8, 30, 0.03f)),
            Map.entry("spirit_palm", own(0f, 1.1f, -0.6f, 6, -0.025f, 5, 45, 0f)),
            Map.entry("mend", own(0f, 0f, 0f, 0, 0.015f, 16, 0, 0.02f)),
            Map.entry("third_eye", own(0f, 0f, 0f, 0, -0.04f, 16, 0, 0f)),
            Map.entry("cord_lash", own(1.2f, -0.4f, 0.8f, 5, 0f, 0, 0, 0f)),
            Map.entry("bulwark", own(0f, -1.2f, 0f, 9, -0.03f, 9, 40, 0.04f)),
            Map.entry("hundred_palms", own(0f, 0.4f, 0f, 3, -0.02f, 6, 0, 0f)),
            Map.entry("void_maw", own(0f, 1.0f, 0f, 10, -0.05f, 14, 40, 0.03f)),
            Map.entry("waking_dream", own(0f, 0f, 0f, 0, -0.02f, 16, 0, 0f)),
            Map.entry("storm_call", own(0f, 2.4f, 0f, 8, -0.04f, 8, 60, 0.06f)),
            Map.entry("ember_step", own(0f, 0.4f, 1.0f, 4, 0.03f, 4, 0, 0f)),
            Map.entry("frost_nova", own(0f, 2.0f, 0f, 8, -0.03f, 7, 50, 0.03f)),
            Map.entry("ghost_step", own(0f, 0f, 0f, 0, 0.04f, 5, 0, 0f)),
            Map.entry("twin_strike", own(0f, 0f, 1.2f, 5, 0f, 0, 0, 0f)),
            // the earned five: a press that sears, a lift of the chin as the lances form, the ground dropping away,
            // a breath out that pushes you back, the land answering under your hand
            Map.entry("brand", own(0.5f, 0.9f, -0.5f, 5, -0.02f, 5, 35, 0.02f)),
            Map.entry("hoarfrost_lances", own(0f, -0.9f, 0f, 9, 0.015f, 10, 0, 0.02f)),
            Map.entry("updraft", own(0f, -1.6f, 0f, 10, 0.03f, 12, 0, 0f)),
            Map.entry("backdraft", own(0f, 1.6f, 0.3f, 9, -0.05f, 10, 40, 0.07f)),
            Map.entry("thornwake", own(0f, 1.9f, 0f, 7, -0.025f, 7, 45, 0f)));

    /**
     * A power's moment at its release ({@code end} false) or its let-go ({@code end} true): its own when it has one for that
     * beat (a held power's own is its let-go; an instant one's its release), else the shared one named.
     */
    static void moment(String power, boolean channel, boolean end, String shared, Entity caster, int tint) {
        Moment m = channel == end ? POWER.get(power) : null;
        play(m != null ? m : MOMENTS.get(shared), caster, tint);
    }

    static final Map<String, Moment> MOMENTS = Map.ofEntries(
            Map.entry("thump_heavy", new Moment(2.6f, 0.7f, 9, -0.03f, 7, 55, 0f)),
            Map.entry("thump_light", new Moment(1.2f, 0.4f, 7, -0.015f, 6, 30, 0f)),
            Map.entry("recoil_light", new Moment(-0.7f, 0f, 6, 0f, 0, 0, 0f)),
            // a small flash: the big releases (nova, prism lance) already flash in their own recipes
            Map.entry("release_big", new Moment(2.0f, 0.5f, 10, -0.04f, 9, 40, 0.05f)),
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
        play(MOMENTS.get(name), caster, tint);
    }

    private static void play(Moment m, Entity caster, int tint) {
        if (m == null || m == MOMENTS.get("none")) return;
        var p = Minecraft.getInstance().player;
        if (p == null || !AwakenConfig.Client.CAMERA_SHAKE.get()) return;
        boolean own = caster.getId() == p.getId();
        float near = own ? 1f : (float) Math.max(0, 1 - p.distanceTo(caster) / REACH);
        if (near <= 0) return;
        float k = near * (FirstPerson.reducedMotion.getAsBoolean() ? 0.5f : 1f);
        if (m.pitch() != 0 || m.roll() != 0 || m.yaw() != 0) {
            // a shared moment's roll alternates by who you are; a power's own is authored with its side
            float roll = m.yaw() != 0 || POWER.containsValue(m) ? m.roll() : (p.getId() % 2 == 0 ? 1 : -1) * m.roll();
            CombatFeedback.kick(m.yaw() * k, m.pitch() * k, roll * k, Math.max(1, m.kickTicks()));
        }
        if (m.fov() != 0) FxScreen.fovPunch(m.fov() * k, m.fovTicks());
        if (m.flash() > 0 && own) FxScreen.flash(tint, m.flash(), 6);
        if (m.flash() > 0) dev.awaken.lens.client.LensClient.pulse(m.flash() * 3f * near); // the glow lifts with the release (Lens on)
        if (own && m.holdMs() > 0) CombatFeedback.hold(m.holdMs());
    }
}
