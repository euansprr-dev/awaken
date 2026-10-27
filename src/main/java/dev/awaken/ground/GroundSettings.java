package dev.awaken.ground;

/**
 * The ground settings that code outside the client package reads (Far Sight's workers): written by
 * {@code ground.client.GroundClientConfig} from the player's file, so they stay off on a dedicated server and in GameTests,
 * where no one draws anything and Far Sight's colours must stay exactly what the palette says.
 */
public final class GroundSettings {
    private GroundSettings() {}

    private static volatile boolean seamless, drift;

    public static boolean seamless() {
        return seamless;
    }

    public static boolean drift() {
        return drift;
    }

    public static void set(boolean seamlessOn, boolean driftOn) {
        seamless = seamlessOn;
        drift = driftOn;
    }
}
