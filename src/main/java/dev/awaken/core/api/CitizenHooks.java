package dev.awaken.core.api;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * Carillon's citizens (SPEC §16.5) as other modules see them: who saw something happen (renown counts only witnessed
 * deeds), and a way to tell the city something happened near it (a gate tremor, a fire). Installed by the citizens
 * module; the defaults have no citizens, so nobody witnesses anything.
 */
public final class CitizenHooks {
    private CitizenHooks() {}

    public interface Access {
        /** Citizens (ledger ids) with line of sight to {@code at} within {@code radius} blocks. */
        default List<UUID> witnesses(ServerLevel level, Vec3 at, double radius) { return List.of(); }
        /** A city event (e.g. {@code awaken:gate_tremor}) at a place; the citizens module decides who reacts. */
        default void post(ServerLevel level, Identifier event, BlockPos pos) {}
        /**
         * A named citizen ({@code "bram"}) staged for a scene: walks to {@code at} and stands facing {@code yaw}, doing
         * {@code act} once there (an act name: "talk", "serve", "set_down", "nod", "wave", "hands_behind"; "" none) with
         * {@code prop} in hand ("holo_band", "licence", "book"; "" none). Call again to move them on; {@code at} null
         * hands them back to their day. Lapses if not renewed for 30 s. The Registry ceremony (SPEC §16.4).
         */
        default void stage(ServerLevel level, String who, @org.jspecify.annotations.Nullable Vec3 at, float yaw, String act, String prop) {}
        /** Where a named citizen's body is now (null: no body near any player). */
        default @org.jspecify.annotations.Nullable Vec3 where(ServerLevel level, String who) { return null; }
    }

    private static Access access = new Access() {};

    public static void install(Access a) {
        access = a;
    }

    public static Access installed() {
        return access;
    }

    public static List<UUID> witnesses(ServerLevel level, Vec3 at, double radius) {
        return access.witnesses(level, at, radius);
    }

    public static void post(ServerLevel level, Identifier event, BlockPos pos) {
        access.post(level, event, pos);
    }

    public static void stage(ServerLevel level, String who, @org.jspecify.annotations.Nullable Vec3 at, float yaw, String act, String prop) {
        access.stage(level, who, at, yaw, act, prop);
    }

    public static @org.jspecify.annotations.Nullable Vec3 where(ServerLevel level, String who) {
        return access.where(level, who);
    }
}
