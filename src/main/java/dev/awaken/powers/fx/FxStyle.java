package dev.awaken.powers.fx;

import dev.awaken.core.Forces;
import dev.awaken.powers.PowerType;
import org.jspecify.annotations.Nullable;

/**
 * The colour language of power effects (docs/modules/power-fx.md section 1). Every effect is tinted from one style:
 * a white-hot core, the family's mid colour, a darker edge that keeps glow readable against a bright sky, and an
 * accent (dust, void, blueprint, soul). Forces override the family where they matter (a heat beam is orange).
 */
public enum FxStyle {
    KINETIC(0xFFFFF1D6, 0xFFF2A541, 0xFFB26A12, 0xFF9A8A72),
    VELOCITY(0xFFFFFFFF, 0xFFFF4A3D, 0xFFA8262F, 0xFFFF8A7A),
    FLIGHT(0xFFE6F6FF, 0xFFA7CDE3, 0xFF6B98B8, 0xFFDCDFE8),
    GRAVITY(0xFFE0D0FF, 0xFF9A6BFF, 0xFF4A2A9A, 0xFF0D0A17),
    ENERGY(0xFFFFFFFF, 0xFF7FC3FF, 0xFF3C86CC, 0xFF1D5591),
    HEAT(0xFFFFF4D0, 0xFFFF9A3C, 0xFFC44A12, 0xFF55180A),
    CHARGE(0xFFF0FAFF, 0xFF9AD8FF, 0xFF3C86CC, 0xFFFFFFFF),
    SHADOW(0xFFC3B0FF, 0xFF8F6BFF, 0xFF231A3B, 0xFF0D0A17),
    MIND(0xFFFFE6F6, 0xFFD088C2, 0xFF73367C, 0xFFFFD27A),
    ARCHITECT(0xFFFFE6B0, 0xFFFFD28A, 0xFF937646, 0xFF7FC3FF),
    TECH(0xFFE6FFFA, 0xFF47C6B0, 0xFF17564E, 0xFF7FC3FF),
    SPIRIT(0xFFF0FFF5, 0xFF86E0A0, 0xFF3F8F6E, 0xFF9FB0FF),
    SOUL(0xFFE8ECFF, 0xFF9FB0FF, 0xFF4C50B3, 0xFF86E0A0),
    FROST(0xFFFFFFFF, 0xFFA7CDE3, 0xFF406A8C, 0xFFE6F6FF),
    PRISM(0xFFFFFFFF, 0xFFE9D8FF, 0xFF8F6BFF, 0xFF7FC3FF),
    NEUTRAL(0xFFFFFFFF, 0xFFD6ECFF, 0xFF7FC3FF, 0xFFF2A541);

    public final int core;
    public final int mid;
    public final int edge;
    public final int accent;

    FxStyle(int core, int mid, int edge, int accent) {
        this.core = core;
        this.mid = mid;
        this.edge = edge;
        this.accent = accent;
    }

    private static final FxStyle[] VALUES = values();

    public static FxStyle byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NEUTRAL;
    }

    public static FxStyle of(PowerType.Family f) {
        return switch (f) {
            case KINETIC -> KINETIC;
            case VELOCITY -> VELOCITY;
            case FLIGHT -> FLIGHT;
            case ENERGY -> ENERGY;
            case SHADOW -> SHADOW;
            case MIND -> MIND;
            case ARCHITECT -> ARCHITECT;
            case TECH -> TECH;
            case SPIRIT -> SPIRIT;
        };
    }

    /** The style a force's damage reads as, or null to keep the family's. */
    public static @Nullable FxStyle of(Forces.@Nullable Force f) {
        if (f == null) return null;
        return switch (f) {
            case HEAT -> HEAT;
            case CHARGE -> CHARGE;
            case GRAVITY -> GRAVITY;
            case SPIRIT -> SOUL;
            case FROST -> FROST;
        };
    }

    /** True for styles whose smoke is dark ink rather than light dust (drawn alpha-blended, never additive). */
    public boolean inky() {
        return this == SHADOW || this == GRAVITY;
    }
}
