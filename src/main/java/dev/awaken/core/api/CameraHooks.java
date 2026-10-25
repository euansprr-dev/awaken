package dev.awaken.core.api;

import java.util.function.BooleanSupplier;

/**
 * The view the player looks through (SPEC §17.13). The opening's View key (vanilla's Toggle Perspective, first and
 * third person only) asks {@link #toggleView()} first: with Shoulder Surfing installed the body's feel installs a toggle
 * that flips first person and the over-the-shoulder view instead, so the key never lands on vanilla's camera right
 * behind you. Client only; with nothing installed the key does what it always did.
 */
public final class CameraHooks {
    private CameraHooks() {}

    private static volatile BooleanSupplier toggle = () -> false;

    public static void installToggle(BooleanSupplier t) {
        toggle = t;
    }

    /** One press of the View key: true when an installed toggle took it (the caller leaves the camera alone). */
    public static boolean toggleView() {
        return toggle.getAsBoolean();
    }
}
