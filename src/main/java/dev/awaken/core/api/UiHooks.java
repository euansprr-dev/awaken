package dev.awaken.core.api;

import java.util.function.DoubleSupplier;

/**
 * What the interface tells the rest of the client. {@link #focus()} is how much an open holo panel holds the player's
 * attention, 0..1: about 1 while the K menu or the inventory is fully open, eased through their open and close. Lens
 * reads it to dim or blur the world behind a panel. Client only; 0 on a server or when nothing is installed.
 */
public final class UiHooks {
    private UiHooks() {}

    private static DoubleSupplier focus = () -> 0;

    /** Installed once by the holo module. */
    public static void installFocus(DoubleSupplier source) {
        focus = source;
    }

    public static float focus() {
        double f = focus.getAsDouble();
        return f <= 0 ? 0f : f >= 1 ? 1f : (float) f;
    }
}
