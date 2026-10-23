package dev.awaken.core.api;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Descent access for other modules (the Above's Deep Lift). Installed by the descent module. */
public final class DescentHooks {
    private DescentHooks() {}

    public interface Access {
        /** Strata (1..10) where this player has lit a Waystone. */
        List<Integer> litWaystones(ServerPlayer player);
        /** Ride the Deep Lift to a lit Waystone: no Strain. Returns false if not allowed. */
        boolean ride(ServerPlayer player, int stratum);
        /** Whether the player is somewhere in the Descent. */
        boolean inDescent(ServerPlayer player);
    }

    private static Access access = new Access() {
        @Override public List<Integer> litWaystones(ServerPlayer player) { return List.of(); }
        @Override public boolean ride(ServerPlayer player, int stratum) { return false; }
        @Override public boolean inDescent(ServerPlayer player) { return false; }
    };

    public static void install(Access a) { access = a; }
    public static List<Integer> litWaystones(ServerPlayer p) { return access.litWaystones(p); }
    public static boolean ride(ServerPlayer p, int stratum) { return access.ride(p, stratum); }
    public static boolean inDescent(ServerPlayer p) { return access.inDescent(p); }
}
