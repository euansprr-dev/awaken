package dev.awaken.powers.fx;

/**
 * Body animations for casting, shared by server (which picks one per cast) and client (which poses the model in third
 * person and the arms in first person). Wind-ups ({@link #held}) keep their pose until the release event replaces
 * them; the rest play once over {@link #ticks}.
 */
public enum CastAnim {
    NONE(0, false),
    /** One-arm punch thrust with a body twist into it. */
    PUNCH(8, false),
    /** Arm cocked back over the shoulder, trembling: a punch being loaded. */
    PUNCH_WIND(0, true),
    /** One open palm driven forward, a little lean behind it. */
    PALM(9, false),
    /** Both palms pushed out at the aim. */
    PUSH2(10, false),
    /** Both hands drawn in to the chest, gathering. */
    CHARGE2(0, true),
    /** One arm held out along the aim (beams, bolts, aimed spells). */
    AIM(10, false),
    AIM_HOLD(0, true),
    /** Arm raised to the sky (calling lightning, summoning, launching). */
    RAISE(14, false),
    /** Both arms up, about to come down. */
    SLAM_WIND(0, true),
    /** Crouched landing with both fists down. */
    SLAM(10, false),
    /** A stamp: lean in and drop. */
    STOMP(8, false),
    /** Guard up, feet planted. */
    BRACE(16, false),
    /** Lean hard into the motion. */
    DASH(8, false),
    /** Hands meet in front: a clap. */
    CLAP(8, false),
    /** Fingers to the temple (mind powers). */
    TEMPLE(14, false),
    /** Both arms thrown up and out: a release of everything (novas, ultimates). */
    SKYWARD(14, false),
    /** Alternating palms too fast to see. */
    FLURRY(40, false),
    /** Weightless: a slow bob, arms loose. */
    HOVER(0, true),
    /** A throwing motion (telekinesis release, debris). */
    THROW(8, false),
    /** One hand sweeps low over the ground, raising something (summons, constructs). */
    CONJURE(12, false);

    /** Play length in ticks (held anims: the release fade). */
    public final int ticks;
    /** Held anims stay until released or replaced. */
    public final boolean held;

    CastAnim(int ticks, boolean held) {
        this.ticks = ticks;
        this.held = held;
    }

    private static final CastAnim[] VALUES = values();

    public static CastAnim byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
    }

    /** Does this pose use both arms (first person shows both hands)? */
    public boolean twoHanded() {
        return switch (this) {
            case PUSH2, CHARGE2, SLAM_WIND, SLAM, BRACE, CLAP, SKYWARD, FLURRY -> true;
            default -> false;
        };
    }
}
