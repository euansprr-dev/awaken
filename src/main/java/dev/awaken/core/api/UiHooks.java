package dev.awaken.core.api;

import java.util.function.DoubleSupplier;

/**
 * What the interface tells the rest of the client. {@link #focus()} is how much an open panel holds the player's
 * attention, 0..1: about 1 while the K menu, the first-power choice or another reading screen is fully open, eased
 * through their open and close. Lens reads it to dim the world behind a panel. Several sources may report at once
 * ({@link #setFocus}, one slot per owner); focus is the largest. Toasts, cards and HUD lines never report. Client
 * only; 0 on a server or when nothing reports.
 */
public final class UiHooks {
    private UiHooks() {}

    private static final int MAX_SOURCES = 8;
    private static final Object[] OWNERS = new Object[MAX_SOURCES];
    private static final float[] VALUES = new float[MAX_SOURCES];
    private static DoubleSupplier legacy = () -> 0;

    /**
     * {@code owner}'s focus, 0..1 (kept until changed or cleared). Owners are compared by identity; a slot is taken the
     * first time an owner reports a value above 0, so steady per-frame reporting allocates nothing. At most 8 owners.
     */
    public static void setFocus(Object owner, float value) {
        int free = -1;
        for (int i = 0; i < MAX_SOURCES; i++) {
            if (OWNERS[i] == owner) {
                VALUES[i] = value;
                return;
            }
            if (OWNERS[i] == null && free < 0) free = i;
        }
        if (free >= 0 && value > 0) {
            OWNERS[free] = owner;
            VALUES[free] = value;
        }
    }

    /** {@code owner} stops reporting. */
    public static void clearFocus(Object owner) {
        for (int i = 0; i < MAX_SOURCES; i++) {
            if (OWNERS[i] == owner) {
                OWNERS[i] = null;
                VALUES[i] = 0;
            }
        }
    }

    /** One pulled source (the first form of this API), counted beside the reported ones. */
    public static void installFocus(DoubleSupplier source) {
        legacy = source;
    }

    /** The largest focus any source reports, 0..1. */
    public static float focus() {
        double f = legacy.getAsDouble();
        for (int i = 0; i < MAX_SOURCES; i++) if (OWNERS[i] != null && VALUES[i] > f) f = VALUES[i];
        return f <= 0 ? 0f : f >= 1 ? 1f : (float) f;
    }
}
