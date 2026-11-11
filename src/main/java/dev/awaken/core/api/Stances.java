package dev.awaken.core.api;

import net.minecraft.world.entity.Entity;

/**
 * A creature's stance as this client knows it (SPEC §24, Arms): how full it is, whether it is broken, and whether it
 * is the local fighter's focus. The creature plates read it; Arms' client installs it from the server's snapshots.
 * Client side; every read is a quiet zero until installed.
 */
public final class Stances {
    private Stances() {}

    public interface Source {
        /** 0..1 of the stance's hold. */
        default float share(Entity e) {
            return 0;
        }

        default boolean broken(Entity e) {
            return false;
        }

        default boolean focused(Entity e) {
            return false;
        }

        /** A duel body (SPEC §47.3): its stance shows from the first time it fights, empty or not. */
        default boolean duel(Entity e) {
            return false;
        }

        /** Its stance is going back down now (left alone past its grace, not fighting). */
        default boolean draining(Entity e) {
            return false;
        }

        /** The local fighter's blows that have landed on it. */
        default int struck(Entity e) {
            return 0;
        }

        /** Client ticks since a blow last filled it (huge when none has). */
        default int sinceRise(Entity e) {
            return Integer.MAX_VALUE;
        }
    }

    private static Source source = new Source() {};

    public static void install(Source s) {
        source = s;
    }

    public static float share(Entity e) {
        return source.share(e);
    }

    public static boolean broken(Entity e) {
        return source.broken(e);
    }

    public static boolean focused(Entity e) {
        return source.focused(e);
    }

    public static boolean duel(Entity e) {
        return source.duel(e);
    }

    public static boolean draining(Entity e) {
        return source.draining(e);
    }

    public static int struck(Entity e) {
        return source.struck(e);
    }

    public static int sinceRise(Entity e) {
        return source.sinceRise(e);
    }
}
