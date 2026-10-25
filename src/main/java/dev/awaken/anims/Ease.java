package dev.awaken.anims;

/**
 * The timing curves every Awaken animation shares (the names the Python specs and the Player Animation Library clips
 * use), so first person moves on the same curves as third person. Pure math: both sides, any module.
 */
public final class Ease {
    private Ease() {}

    public static final int LINEAR = 0, SMOOTH = 1, IN_QUAD = 2, OUT_QUAD = 3, IN_CUBIC = 4, OUT_CUBIC = 5, IN_OUT_SINE = 6,
            OUT_SINE = 7, IN_SINE = 8, OUT_BACK = 9, STEP = 10;

    /** A curve by its spec name (unknown names are linear). */
    public static int byName(String name) {
        return switch (name) {
            case "smooth", "smoothstep" -> SMOOTH;
            case "easeinquad" -> IN_QUAD;
            case "easeoutquad" -> OUT_QUAD;
            case "easeincubic" -> IN_CUBIC;
            case "easeoutcubic" -> OUT_CUBIC;
            case "easeinoutsine" -> IN_OUT_SINE;
            case "easeoutsine" -> OUT_SINE;
            case "easeinsine" -> IN_SINE;
            case "easeoutback" -> OUT_BACK;
            case "step" -> STEP;
            default -> LINEAR;
        };
    }

    /** The curve at t (0..1, clamped). */
    public static float at(int curve, float t) {
        t = t < 0 ? 0 : t > 1 ? 1 : t;
        return switch (curve) {
            case SMOOTH -> t * t * (3 - 2 * t);
            case IN_QUAD -> t * t;
            case OUT_QUAD -> 1 - (1 - t) * (1 - t);
            case IN_CUBIC -> t * t * t;
            case OUT_CUBIC -> 1 - (1 - t) * (1 - t) * (1 - t);
            case IN_OUT_SINE -> (float) (0.5 - 0.5 * Math.cos(Math.PI * t));
            case OUT_SINE -> (float) Math.sin(t * Math.PI / 2);
            case IN_SINE -> (float) (1 - Math.cos(t * Math.PI / 2));
            case OUT_BACK -> {
                float c = 1.70158F, u = t - 1;
                yield 1 + (c + 1) * u * u * u + c * u * u;
            }
            case STEP -> t < 1 ? 0 : 1;
            default -> t;
        };
    }

    /** 0 → 1 over {@code in} ticks after start, held while running, 1 → 0 over {@code out} after end (the body's hold). */
    public static float hold(float t, float start, float end, boolean running, float in, float out) {
        if (start < -50) return 0;
        float up = clamp((t - start) / in);
        if (running) return up;
        return Math.min(up, 1 - clamp((t - end) / out));
    }

    /** A one-shot's envelope: in over the first {@code in} share, out over the last {@code out} share (the casts' 15/35). */
    public static float envelope(float p, float in, float out) {
        return Math.max(0, Math.min(clamp(p / in), clamp((1 - p) / out)));
    }

    public static float clamp(float t) {
        return t < 0 ? 0 : t > 1 ? 1 : t;
    }
}
