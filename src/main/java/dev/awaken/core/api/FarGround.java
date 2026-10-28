package dev.awaken.core.api;

/**
 * The far land's ground height (SPEC §39.8): Far Sight installs a sampler for the world it draws, so the binoculars'
 * readout and marking reach past loaded chunks. Client side, single player only (Far Sight reads the integrated
 * server's level); NaN where nothing is known or before anything is installed. Thread-safe: callers sample from a
 * background thread, never the render thread (a column costs tens of microseconds).
 */
public final class FarGround {
    private FarGround() {}

    @FunctionalInterface
    public interface Sampler {
        /** The top of the ground at (x, z) in the dimension the player is in, or NaN. */
        double ground(int x, int z);
    }

    private static volatile Sampler sampler = (x, z) -> Double.NaN;
    private static volatile java.util.function.BooleanSupplier ready = () -> false;

    /** {@code ready}: the far land drew lately (what the player sees out there is what the sampler answers for). */
    public static void install(Sampler s, java.util.function.BooleanSupplier isReady) {
        sampler = s;
        ready = isReady;
    }

    public static boolean ready() {
        return ready.getAsBoolean();
    }

    public static double ground(int x, int z) {
        return sampler.ground(x, z);
    }
}
