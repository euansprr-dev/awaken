package dev.awaken.gates;

import com.mojang.serialization.Codec;

import java.util.EnumSet;
import java.util.Set;

/**
 * Gate lifecycle (SPEC section 7): BUILDING → READY → ACTIVE ⇄ OPEN → CLEARED | BROKEN → RELEASED.
 * ACTIVE = someone is inside; OPEN = entered before, nobody inside now. Only listed transitions are legal.
 */
public enum GateState {
    BUILDING, READY, ACTIVE, OPEN, CLEARED, BROKEN, RELEASED;

    public static final Codec<GateState> CODEC = Codec.STRING.xmap(GateState::valueOf, GateState::name);

    public Set<GateState> next() {
        return switch (this) {
            case BUILDING -> EnumSet.of(READY, RELEASED);
            case READY -> EnumSet.of(ACTIVE, BROKEN, RELEASED);
            case ACTIVE -> EnumSet.of(OPEN, CLEARED, BROKEN);
            case OPEN -> EnumSet.of(ACTIVE, BROKEN, RELEASED);
            case CLEARED -> EnumSet.of(RELEASED);
            case BROKEN -> EnumSet.of(RELEASED);
            case RELEASED -> EnumSet.noneOf(GateState.class);
        };
    }

    public boolean canGo(GateState to) {
        return next().contains(to);
    }

    /** Still standing in the world (pillar shown, counts toward the Director's 1–4). */
    public boolean standing() {
        return this == BUILDING || this == READY || this == ACTIVE || this == OPEN;
    }

    /** Players may walk in. */
    public boolean enterable() {
        return this == READY || this == ACTIVE || this == OPEN;
    }
}
