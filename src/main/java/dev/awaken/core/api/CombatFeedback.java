package dev.awaken.core.api;

/**
 * One feel for every blow (SPEC §25), client side: hit-stop (the struck body freezes and trembles; the striker's hands
 * and view hold too), a camera kick along the blow, and the moment's weight. Arms installs it; any module's client
 * may call it (a power's hit, a slam). Holds no client classes, so common code may name it; every call is a no-op
 * until installed and on a dedicated server.
 */
public final class CombatFeedback {
    private CombatFeedback() {}

    public interface Sink {
        /** Freeze this entity's render for {@code ms} with a tremble. */
        default void hitstop(int entityId, int ms) {}

        /** Hold the local player's hands and view for {@code ms} (the striker's half of a hit-stop). */
        default void hold(int ms) {}

        /** Kick the view: degrees of yaw, pitch and roll at the start, decaying over {@code ticks}. */
        default void kick(float yaw, float pitch, float roll, int ticks) {}
    }

    private static Sink sink = new Sink() {};

    /**
     * Whether the local player is in a fight now (client; Arms installs it, honouring its comfort switch): the
     * action camera's COMBAT pose closes in while it holds.
     */
    public static volatile java.util.function.BooleanSupplier engaged = () -> false;

    public static void install(Sink s) {
        sink = s;
    }

    public static void hitstop(int entityId, int ms) {
        sink.hitstop(entityId, ms);
    }

    public static void hold(int ms) {
        sink.hold(ms);
    }

    public static void kick(float yaw, float pitch, float roll, int ticks) {
        sink.kick(yaw, pitch, roll, ticks);
    }
}
