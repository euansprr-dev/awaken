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
    NEUTRAL(0xFFFFFFFF, 0xFFD6ECFF, 0xFF7FC3FF, 0xFFF2A541),
    // Awakened (SPEC 36.5): a level-5 power draws every effect in its family's awakened palette: hotter cores, a richer mid,
    // and an accent of gold (or the family's own second colour) where the plain style had dust or void
    KINETIC_AWAKENED(0xFFFFF8E8, 0xFFFFB347, 0xFFC4400F, 0xFFFF6A2A),
    VELOCITY_AWAKENED(0xFFFFFFFF, 0xFFFF3355, 0xFF8A0F2A, 0xFFFFD27A),
    FLIGHT_AWAKENED(0xFFFFFFFF, 0xFFC9E9FF, 0xFF5E8FD8, 0xFFFFE9B0),
    GRAVITY_AWAKENED(0xFFF0E6FF, 0xFFB08BFF, 0xFF3A1A8A, 0xFFFFD27A),
    ENERGY_AWAKENED(0xFFFFF8E6, 0xFF9FD6FF, 0xFF2F6FD0, 0xFFFFD27A),
    HEAT_AWAKENED(0xFFFFFBEA, 0xFFFFB04A, 0xFFD0400E, 0xFFFFE08A),
    CHARGE_AWAKENED(0xFFFFFFFF, 0xFFB8E6FF, 0xFF3C86CC, 0xFFFFE9A0),
    SHADOW_AWAKENED(0xFFE9DEFF, 0xFFA98BFF, 0xFF1A1030, 0xFFFF4FA0),
    MIND_AWAKENED(0xFFFFF0FA, 0xFFF0A0E0, 0xFF7A2A8C, 0xFFFFE08A),
    ARCHITECT_AWAKENED(0xFFFFF4D6, 0xFFFFDD8A, 0xFF8C6A30, 0xFF9FE4FF),
    TECH_AWAKENED(0xFFF0FFFC, 0xFF5CF0D0, 0xFF0F5A50, 0xFFFFD27A),
    SPIRIT_AWAKENED(0xFFFFFFF6, 0xFFA8F5C0, 0xFF2F8F6E, 0xFFFFE6A0),
    SOUL_AWAKENED(0xFFF4F6FF, 0xFFB8C6FF, 0xFF4C50B3, 0xFFFFE6A0),
    FROST_AWAKENED(0xFFFFFFFF, 0xFFC4E8FF, 0xFF4F86B8, 0xFFFFFFFF),
    PRISM_AWAKENED(0xFFFFFFFF, 0xFFF4E8FF, 0xFFA98BFF, 0xFFFFD27A);

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
    private static final FxStyle[] PLAIN = values();

    static {
        for (FxStyle s : VALUES) if (s.awakened() != s) PLAIN[s.awakened().ordinal()] = s;
    }

    public static FxStyle byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NEUTRAL;
    }

    /** This style's awakened palette (itself when it has none or already is one). */
    public FxStyle awakened() {
        return switch (this) {
            case KINETIC -> KINETIC_AWAKENED;
            case VELOCITY -> VELOCITY_AWAKENED;
            case FLIGHT -> FLIGHT_AWAKENED;
            case GRAVITY -> GRAVITY_AWAKENED;
            case ENERGY -> ENERGY_AWAKENED;
            case HEAT -> HEAT_AWAKENED;
            case CHARGE -> CHARGE_AWAKENED;
            case SHADOW -> SHADOW_AWAKENED;
            case MIND -> MIND_AWAKENED;
            case ARCHITECT -> ARCHITECT_AWAKENED;
            case TECH -> TECH_AWAKENED;
            case SPIRIT -> SPIRIT_AWAKENED;
            case SOUL -> SOUL_AWAKENED;
            case FROST -> FROST_AWAKENED;
            case PRISM -> PRISM_AWAKENED;
            default -> this;
        };
    }

    /** The plain style an awakened one grew from: recipes compare plain styles, so an awakened one keeps their shapes. */
    public FxStyle plain() {
        return PLAIN[ordinal()];
    }

    public boolean isAwakened() {
        return ordinal() >= KINETIC_AWAKENED.ordinal();
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
