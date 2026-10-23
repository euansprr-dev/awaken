package dev.awaken.powers.fx;

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;


/**
 * The designed effect of every power, beat by beat (docs/modules/power-fx.md section 3): its colour style, the body
 * animation, the held wind-up pose for channels, and the recipe used for each beat. The behaviours in
 * {@code catalog/*Powers} send these recipes; the client uses the table to start the cast pose and wind-up the moment
 * a key goes down, before the server answers.
 */
public final class PowerFxDefs {
    private PowerFxDefs() {}

    public record Def(String power, FxStyle style, CastAnim anim, @Nullable CastAnim windAnim, @Nullable FxKind windup,
                      FxKind release, @Nullable FxKind travel, @Nullable FxKind impact, @Nullable FxKind linger, boolean ultimate) {}

    private static final Map<String, Def> DEFS = new LinkedHashMap<>();
    public static final Map<String, Def> ALL = Collections.unmodifiableMap(DEFS);

    public static @Nullable Def get(String powerPath) {
        int colon = powerPath.indexOf(':');
        return DEFS.get(colon >= 0 ? powerPath.substring(colon + 1) : powerPath);
    }

    private static void d(String id, FxStyle style, CastAnim anim, @Nullable CastAnim wind, @Nullable FxKind windup, FxKind release,
                          @Nullable FxKind travel, @Nullable FxKind impact, @Nullable FxKind linger) {
        DEFS.put(id, new Def(id, style, anim, wind, windup, release, travel, impact, linger, false));
    }

    private static void ult(String id, FxStyle style, CastAnim anim, @Nullable CastAnim wind, @Nullable FxKind windup, FxKind release,
                            @Nullable FxKind travel, @Nullable FxKind impact, @Nullable FxKind linger) {
        DEFS.put(id, new Def(id, style, anim, wind, windup, release, travel, impact, linger, true));
    }

    static {
        // Kinetic: heavy, low, wide. Shockwave rings, cracks, debris, dust, long hit-stop.
        d("ground_slam", FxStyle.KINETIC, CastAnim.SLAM, CastAnim.SLAM_WIND, FxKind.TRAIL, FxKind.SLAM, null, FxKind.SLAM, FxKind.SMOKE);
        d("leap", FxStyle.KINETIC, CastAnim.DASH, null, null, FxKind.DASH, FxKind.TRAIL, FxKind.SLAM, null);
        d("haymaker", FxStyle.KINETIC, CastAnim.PUNCH, CastAnim.PUNCH_WIND, FxKind.CHARGE, FxKind.IMPACT, null, FxKind.IMPACT, FxKind.SMOKE);
        d("terrain_rip", FxStyle.KINETIC, CastAnim.THROW, null, null, FxKind.SLAM, FxKind.TRAIL, FxKind.IMPACT, FxKind.SMOKE);
        d("shockwave", FxStyle.KINETIC, CastAnim.STOMP, null, null, FxKind.CONE, FxKind.CONE, FxKind.IMPACT, FxKind.SMOKE);
        d("brace", FxStyle.KINETIC, CastAnim.BRACE, null, null, FxKind.RING, null, FxKind.RING, FxKind.AURA);
        d("thunderclap", FxStyle.CHARGE, CastAnim.CLAP, null, null, FxKind.STRIKE, FxKind.LIGHTNING, FxKind.IMPACT, FxKind.RING);
        // Velocity: long thin streaks, afterimages, wind.
        d("dash", FxStyle.VELOCITY, CastAnim.DASH, null, null, FxKind.DASH, FxKind.TRAIL, FxKind.IMPACT, FxKind.AFTERIMAGE);
        d("flow", FxStyle.VELOCITY, CastAnim.STOMP, null, null, FxKind.RING, null, null, FxKind.AURA);
        d("afterimage", FxStyle.VELOCITY, CastAnim.DASH, null, null, FxKind.AFTERIMAGE, FxKind.TRAIL, FxKind.SMOKE, FxKind.AURA);
        d("wall_run", FxStyle.VELOCITY, CastAnim.NONE, null, null, FxKind.TRAIL, null, null, null);
        d("water_run", FxStyle.VELOCITY, CastAnim.NONE, null, null, FxKind.TRAIL, null, null, null);
        d("lightning_step", FxStyle.CHARGE, CastAnim.DASH, null, null, FxKind.BLINK, FxKind.LIGHTNING, FxKind.IMPACT, FxKind.AFTERIMAGE);
        // Flight & Gravity: wind ribbons; contracting violet rings and a void core.
        d("hover", FxStyle.FLIGHT, CastAnim.HOVER, null, null, FxKind.RING, FxKind.TRAIL, null, FxKind.AURA);
        d("flight", FxStyle.FLIGHT, CastAnim.SKYWARD, null, null, FxKind.RING, FxKind.TRAIL, null, FxKind.AURA);
        d("gravity_well", FxStyle.GRAVITY, CastAnim.AIM, null, null, FxKind.VORTEX, null, FxKind.NOVA, FxKind.VORTEX);
        d("telekinesis", FxStyle.GRAVITY, CastAnim.THROW, CastAnim.AIM_HOLD, FxKind.LIFT, FxKind.TETHER, FxKind.TRAIL, FxKind.IMPACT, null);
        d("meteor_dive", FxStyle.GRAVITY, CastAnim.SLAM, CastAnim.SKYWARD, FxKind.TRAIL, FxKind.SLAM, FxKind.METEOR, FxKind.SLAM, FxKind.SMOKE);
        d("gravity_flip", FxStyle.GRAVITY, CastAnim.SKYWARD, null, null, FxKind.RING, FxKind.LIFT, FxKind.IMPACT, null);
        // Energy: charge orbs, layered beams with halos, crackle, glow spheres.
        d("beam", FxStyle.HEAT, CastAnim.AIM, CastAnim.AIM_HOLD, FxKind.CHARGE, FxKind.BEAM, FxKind.BEAM, FxKind.IMPACT, FxKind.SMOKE);
        d("nova", FxStyle.HEAT, CastAnim.SKYWARD, CastAnim.CHARGE2, FxKind.CHARGE, FxKind.NOVA, null, FxKind.IMPACT, FxKind.RING);
        d("barrier", FxStyle.ENERGY, CastAnim.PUSH2, null, FxKind.CHARGE, FxKind.DOME, null, FxKind.NOVA, FxKind.DOME);
        d("charged_shot", FxStyle.CHARGE, CastAnim.AIM, CastAnim.AIM_HOLD, FxKind.CHARGE, FxKind.BOLT, FxKind.BOLT, FxKind.IMPACT, null);
        ult("overload", FxStyle.ENERGY, CastAnim.SKYWARD, CastAnim.CHARGE2, FxKind.CHARGE, FxKind.NOVA, FxKind.LIGHTNING, FxKind.IMPACT, FxKind.AURA);
        ult("prism_lance", FxStyle.PRISM, CastAnim.AIM, CastAnim.AIM_HOLD, FxKind.CHARGE, FxKind.BEAM, FxKind.BEAM, FxKind.IMPACT, null);
        // Shadow: ink smoke, sharp slashes, pools, tendrils.
        d("shadow_step", FxStyle.SHADOW, CastAnim.DASH, null, null, FxKind.BLINK, null, FxKind.SMOKE, FxKind.AURA);
        d("cloak", FxStyle.SHADOW, CastAnim.CONJURE, null, null, FxKind.SMOKE, null, null, FxKind.AURA);
        d("echo_swap", FxStyle.SHADOW, CastAnim.CLAP, null, null, FxKind.BLINK, null, FxKind.SMOKE, null);
        ult("legion", FxStyle.SHADOW, CastAnim.CONJURE, null, FxKind.GLYPH, FxKind.SUMMON, null, null, FxKind.GLYPH);
        // Mind: ripples from the head, runes, gold for foresight.
        d("danger_sense", FxStyle.MIND, CastAnim.NONE, null, null, FxKind.PSI, null, null, null);
        d("dominate", FxStyle.MIND, CastAnim.TEMPLE, null, null, FxKind.PSI, FxKind.PSI, FxKind.AURA, null);
        d("read_intent", FxStyle.MIND, CastAnim.TEMPLE, null, null, FxKind.PSI, null, null, FxKind.AURA);
        d("decoy", FxStyle.MIND, CastAnim.THROW, null, null, FxKind.PSI, FxKind.TRAIL, FxKind.AFTERIMAGE, null);
        d("foresight", FxStyle.MIND, CastAnim.TEMPLE, null, null, FxKind.RING, null, null, FxKind.AURA);
        // Architect: blocks assemble with glowing seams over a blueprint grid.
        d("wall", FxStyle.ARCHITECT, CastAnim.PUSH2, null, null, FxKind.CONSTRUCT, null, null, FxKind.GLYPH);
        d("ramp", FxStyle.ARCHITECT, CastAnim.CONJURE, null, null, FxKind.CONSTRUCT, null, null, FxKind.GLYPH);
        d("floor", FxStyle.ARCHITECT, CastAnim.CONJURE, null, null, FxKind.CONSTRUCT, null, null, FxKind.GLYPH);
        d("cone", FxStyle.ARCHITECT, CastAnim.RAISE, null, null, FxKind.CONSTRUCT, null, null, FxKind.GLYPH);
        d("rampart", FxStyle.ARCHITECT, CastAnim.SKYWARD, null, null, FxKind.CONSTRUCT, null, null, FxKind.GLYPH);
        d("spire", FxStyle.ARCHITECT, CastAnim.STOMP, null, null, FxKind.PILLAR, FxKind.CONSTRUCT, FxKind.IMPACT, null);
        d("collapse", FxStyle.ARCHITECT, CastAnim.THROW, null, null, FxKind.CONSTRUCT, FxKind.TRAIL, FxKind.IMPACT, null);
        ult("citadel", FxStyle.ARCHITECT, CastAnim.SKYWARD, null, FxKind.GLYPH, FxKind.DOME, FxKind.CONSTRUCT, null, FxKind.GLYPH);
        // Tech: holograms, hex grids, square motes, thin lasers, jets.
        d("grapple", FxStyle.TECH, CastAnim.AIM, null, null, FxKind.TETHER, null, FxKind.IMPACT, null);
        d("jet_burst", FxStyle.TECH, CastAnim.DASH, null, null, FxKind.EXHAUST, FxKind.TRAIL, null, FxKind.SMOKE);
        d("drone", FxStyle.TECH, CastAnim.RAISE, null, null, FxKind.HOLO, null, null, null);
        d("turret", FxStyle.TECH, CastAnim.CONJURE, null, null, FxKind.HOLO, null, null, null);
        d("railgun", FxStyle.TECH, CastAnim.AIM, CastAnim.AIM_HOLD, FxKind.CHARGE, FxKind.RAIL, null, FxKind.IMPACT, FxKind.SMOKE);
        d("nanite_skin", FxStyle.TECH, CastAnim.BRACE, null, null, FxKind.AURA, null, null, FxKind.AURA);
        ult("drone_swarm", FxStyle.TECH, CastAnim.SKYWARD, null, FxKind.HOLO, FxKind.HOLO, null, null, null);
        // Spirit: translucent wisps, palm prints, echoes of every hit.
        d("aura", FxStyle.SPIRIT, CastAnim.NONE, null, null, FxKind.AURA, null, FxKind.IMPACT, null);
        d("spirit_palm", FxStyle.SPIRIT, CastAnim.PALM, null, null, FxKind.PALM, FxKind.PALM, FxKind.IMPACT, FxKind.ECHO);
        d("mend", FxStyle.SPIRIT, CastAnim.CLAP, null, null, FxKind.HEAL, null, null, FxKind.HEAL);
        d("third_eye", FxStyle.SOUL, CastAnim.TEMPLE, null, null, FxKind.PSI, null, null, FxKind.AURA);
        d("cord_lash", FxStyle.SOUL, CastAnim.THROW, null, null, FxKind.TETHER, null, FxKind.IMPACT, null);
        d("bulwark", FxStyle.SPIRIT, CastAnim.SKYWARD, null, null, FxKind.DOME, null, null, FxKind.DOME);
        ult("hundred_palms", FxStyle.SPIRIT, CastAnim.FLURRY, null, null, FxKind.FLURRY, null, FxKind.IMPACT, FxKind.ECHO);
        ult("void_maw", FxStyle.GRAVITY, CastAnim.AIM, null, null, FxKind.VORTEX, null, FxKind.IMPACT, FxKind.VORTEX);
        d("waking_dream", FxStyle.MIND, CastAnim.CONJURE, null, null, FxKind.CONSTRUCT, null, null, FxKind.GLYPH);
        d("storm_call", FxStyle.CHARGE, CastAnim.RAISE, null, null, FxKind.STRIKE, null, FxKind.IMPACT, null);
        d("ember_step", FxStyle.HEAT, CastAnim.DASH, null, null, FxKind.DASH, FxKind.FIRE_TRAIL, null, FxKind.FIRE_TRAIL);
        d("frost_nova", FxStyle.FROST, CastAnim.STOMP, null, null, FxKind.FROST, null, FxKind.IMPACT, null);
        d("ghost_step", FxStyle.SOUL, CastAnim.DASH, null, null, FxKind.BLINK, null, FxKind.IMPACT, null);
        d("twin_strike", FxStyle.SOUL, CastAnim.CLAP, null, null, FxKind.AURA, null, FxKind.ECHO, FxKind.AURA);
    }
}
