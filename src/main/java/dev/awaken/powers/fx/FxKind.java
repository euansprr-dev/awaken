package dev.awaken.powers.fx;

/**
 * Effect recipes. The server names one in each {@link FxEvent}; the client builds it (sprites, meshes, trails,
 * debris, screen feel) deterministically from the event's seed, so every viewer sees the same shape.
 * Field meanings per kind are listed here and nowhere else; {@code pos} is always the origin.
 */
public enum FxKind {
    /** Only the caster's body animation (flags) and a small flourish at the hand. */
    CAST,
    /** Energy gathering at the caster's hand for {@code ticks}; scale = size. Ends on {@link #CHARGE_END} or time. */
    CHARGE,
    /** Ends the caster's charge; FULL flag = a bright pop. */
    CHARGE_END,
    /** A hit: flare, sparks, style debris; d = hit direction; scale = strength (0.5 light .. 3 huge). */
    IMPACT,
    /** Kinetic ground shockwave: ring mesh, cracks decal, block debris, dust; scale = radius. */
    SLAM,
    /** Generic expanding ground ring; scale = radius, ticks = how long it takes to grow. */
    RING,
    /** Expanding sphere shell of light; scale = radius. */
    NOVA,
    /** A wave rolling forward in an arc; d = direction, scale = range, aux = half angle (degrees), ticks. */
    CONE,
    /** Layered beam from the caster's hand to pos + d; aux = width; ticks; FOLLOW = follows the caster's live aim. */
    BEAM,
    /** One step of a bolt in flight: glowing streak from pos to pos + d (colour). */
    BOLT,
    /** Jagged arc from pos to pos + d for ticks; scale = thickness. */
    LIGHTNING,
    /** Lightning from the sky onto pos; scale = size. */
    STRIKE,
    /** Dash burst at pos: sonic ring, speed lines, afterimages and a wind trail on the caster; d = motion. */
    DASH,
    /** Vanish at pos, appear at pos + d (style decides streak, smoke or wisps). */
    BLINK,
    /** Ribbon trail on the caster for ticks; aux = 0 feet, 1 hands, 2 body. */
    TRAIL,
    /** A ghost copy of the caster left at pos for ticks. */
    AFTERIMAGE,
    /** Smoke burst (ink for shadow styles); scale = size. */
    SMOKE,
    /** Dome or bubble at pos (FOLLOW: around the caster) for ticks; scale = radius; aux = 0 sphere, 1 ground dome. */
    DOME,
    /** Gravity vortex at pos for ticks; scale = radius; REPULSE flag pushes outward. */
    VORTEX,
    /** Cord or cable from the caster's hand to the target entity (or pos) for ticks; aux = 0 cord, 1 cable, 2 grip. */
    TETHER,
    /** Lasting aura on the caster (any entity) for ticks; aux = {@link Aura} ordinal. */
    AURA,
    /** Spirit palm print flying from the caster's hand along d; scale = size. */
    PALM,
    /** The delayed echo of a spirit hit at pos. */
    ECHO,
    /** A flurry of palms in front of the caster for ticks. */
    FLURRY,
    /** Healing motes and a lotus ring on the caster (any entity) for ticks. */
    HEAL,
    /** Frost burst: shards, frost ring, cold mist; scale = radius. */
    FROST,
    /** A burning path segment from pos to pos + d for ticks. */
    FIRE_TRAIL,
    /** Boot-jet exhaust under the caster for ticks; d = thrust. */
    EXHAUST,
    /** Construct blocks in the box pos .. pos + d assemble with glowing seams, sweeping out from the caster. */
    CONSTRUCT,
    /** A pillar erupting from pos to pos.y + scale. */
    PILLAR,
    /** A holographic panel or reticle at pos facing d; aux = 0 launch, 1 deploy, 2 reticle. */
    HOLO,
    /** Railgun slug from the caster's hand to pos + d; scale = charge. */
    RAIL,
    /** Shadow soldiers rising from a pool at pos; scale = size. */
    SUMMON,
    /** Psionic ripples from the caster's head toward pos + d (or around the head when d is zero) for ticks. */
    PSI,
    /** Magic circle on the ground at pos for ticks; scale = radius. */
    GLYPH,
    /** Comet trail on the diving caster for ticks. */
    METEOR,
    /** The target entity held in a gravity grip for ticks. */
    LIFT,
    /** The screen beat of an ultimate for players within scale blocks: impact frame, flash, FOV punch. */
    ULT,
    /** A flash on the caster's own screen; scale = strength. */
    FLASH,
    /** Only the caster's cast profile beat (flags): its clips begin; no particles. */
    POSE,
    /** A power's sound, played by every client in range at pos on the cast's release frame: colour = the sound event's registry id, scale = volume, aux = pitch. */
    SOUND,
    /** A power climbed its ladder (SPEC 34.6): a bloom on the caster; aux 0 = a fight's sting, 1 = the full moment; the level rides the flags. */
    EVOLVE;

    private static final FxKind[] VALUES = values();

    public static FxKind byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : CAST;
    }

    /** Aura looks ({@link #AURA} aux). */
    public enum Aura { GENERIC, FLOW, BRACE, NANITE, OVERLOAD, READ, FORESIGHT, THIRD_EYE, TWIN, CLOAK, MARK, HOVER, FLIGHT, BARRIER }
}
