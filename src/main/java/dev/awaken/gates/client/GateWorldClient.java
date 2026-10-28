package dev.awaken.gates.client;

import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.net.WorldPayloads;
import dev.awaken.gates.world.FloorAir;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * The world floor you're on, as the server told it on arrival (SPEC §38): the title card reads its words, Lens reads its
 * Gate Air data ({@link #air()} with {@link #origin()}: floor-local x/z from the origin, y above it). Null off a world
 * floor or outside the gate dimension.
 */
public final class GateWorldClient {
    private GateWorldClient() {}

    private static volatile WorldPayloads.Arrival current;
    private static volatile long arrivedAt;

    static void arrive(WorldPayloads.Arrival a) {
        current = a.world().isEmpty() ? null : a;
        arrivedAt = System.currentTimeMillis();
    }

    /** The world floor you're on, or null. */
    public static WorldPayloads.Arrival current() {
        WorldPayloads.Arrival a = current;
        if (a == null) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != GatesRegistry.GATES) {
            current = null;
            return null;
        }
        return a;
    }

    /** The floor's Gate Air data, or {@link FloorAir#NONE}. */
    public static FloorAir air() {
        WorldPayloads.Arrival a = current();
        return a == null ? FloorAir.NONE : a.air();
    }

    /** World position of the floor's local origin (Gate Air coordinates are relative to it). */
    public static BlockPos origin() {
        WorldPayloads.Arrival a = current();
        return a == null ? BlockPos.ZERO : a.origin();
    }

    /** Milliseconds since the last arrival (the title card fades by it). */
    public static long sinceArrival() {
        return System.currentTimeMillis() - arrivedAt;
    }
}
