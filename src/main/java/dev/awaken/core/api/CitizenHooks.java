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
}
