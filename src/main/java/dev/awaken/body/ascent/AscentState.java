package dev.awaken.body.ascent;

import org.jspecify.annotations.Nullable;

/**
 * What the body is doing in the air (SPEC §17). The ordinal is the network id, so new states only go on the end.
 * Grounded moves (sprint, slide, mantle, roll) stay the Awakened Body's own; these are the states above them.
 */
public enum AscentState {
    /** On the ground, or falling as vanilla does. */
    NONE(false, false),
    /** Hanging from a rope and swinging. */
    TETHER(true, false),
    /** Pulled along a rope toward its anchor. */
    ZIP(true, false),
    /** Flying free with the momentum a move left you (after letting go of a rope, jumping out of a swing). */
    CARRY(false, false),
    /** On wings of light. */
    GLIDE(false, true),
    /** Momentum flight. */
    SOAR(false, true);

    private static final AscentState[] VALUES = values();

    /** A rope is out. */
    public final boolean rope;
    /** The wings are open. */
    public final boolean wings;

    AscentState(boolean rope, boolean wings) {
        this.rope = rope;
        this.wings = wings;
    }

    /** Every state but NONE holds the body off the ground on purpose: no fall damage, no floating kick. */
    public boolean airborne() {
        return this != NONE;
    }

    public static @Nullable AscentState byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : null;
    }
}
