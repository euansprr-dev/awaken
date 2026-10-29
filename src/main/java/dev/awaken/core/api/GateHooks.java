package dev.awaken.core.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

/**
 * Read access to gates for modules built alongside the gates module (Veil scouting, Above machines, Hearth breaks).
 * The gates module installs the real implementation; the default knows no gates.
 */
public final class GateHooks {
    private GateHooks() {}

    /**
     * A gate as other modules see it. rank = Rank ordinal; state = BUILDING/READY/ACTIVE/OPEN/CLEARED/BROKEN/RELEASED;
     * mouth = where a player stands in front of the portal; breaksOnPlayDay = Long.MAX_VALUE for gates whose break clock
     * doesn't run: they never break, or nobody has come close yet ({@link #waiting}, SPEC §7 "Seen first").
     */
    public record GateView(String id, BlockPos pos, int rank, String theme, String themeName, String omen, String layout,
                           List<String> modifiers, String twist, int floors, String boss, String state, long breaksOnPlayDay,
                           BlockPos mouth) {}

    public interface Access {
        /** The gates that stand within {@code radius} (never a failed, half-built or fallen one). */
        List<GateView> near(ServerLevel level, BlockPos pos, int radius);
        Optional<GateView> get(ServerLevel level, String id);
        /** Mark a gate as scouted by this player (shows full detail on its card). */
        void markScouted(ServerPlayer player, String id);
        /** Push a gate's break back by some play-days (Gate Stabiliser). Returns false if not possible. */
        boolean stabilise(ServerLevel level, String id, int days);
        /** Whether this entity is inside a gate cell. */
        boolean inGate(net.minecraft.world.entity.Entity e);
        /** Open a scripted gate near a position (story prologue). Returns the gate id, or empty if gates are unavailable. */
        Optional<String> openScripted(ServerPlayer player, String theme, int rank, BlockPos near, int floors);
        /** Open a wandering gate at the player's rank near a position; it fades after {@code lifetimeTicks} unless entered. */
        default Optional<String> openWandering(ServerPlayer player, BlockPos near, int floors, int lifetimeTicks) { return Optional.empty(); }
        /** Release a gate now (refused while someone stands inside). */
        default void release(ServerLevel level, String id) {}
        /** Gate-owned encounter boundaries; non-gate entities remain unrestricted. */
        default boolean mayTarget(net.minecraft.world.entity.LivingEntity mob, net.minecraft.world.entity.LivingEntity target) { return true; }
        /** Whether a tracked boss belongs to this player's currently encountered gate fight. */
        default boolean showsBossBar(net.minecraft.world.entity.Entity mob, ServerPlayer player) { return true; }
        /** Whether this gate's break clock waits for someone to come close (it would break, but nobody has seen it yet). */
        default boolean waiting(ServerLevel level, String id) { return false; }
    }

    private static Access access = new Access() {
        @Override public List<GateView> near(ServerLevel level, BlockPos pos, int radius) { return List.of(); }
        @Override public Optional<GateView> get(ServerLevel level, String id) { return Optional.empty(); }
        @Override public void markScouted(ServerPlayer player, String id) {}
        @Override public boolean stabilise(ServerLevel level, String id, int days) { return false; }
        @Override public boolean inGate(net.minecraft.world.entity.Entity e) { return false; }
        @Override public Optional<String> openScripted(ServerPlayer player, String theme, int rank, BlockPos near, int floors) { return Optional.empty(); }
    };

    public static void install(Access a) { access = a; }

    public static List<GateView> near(ServerLevel level, BlockPos pos, int radius) { return access.near(level, pos, radius); }
    public static Optional<GateView> get(ServerLevel level, String id) { return access.get(level, id); }
    public static void markScouted(ServerPlayer player, String id) { access.markScouted(player, id); }
    public static boolean stabilise(ServerLevel level, String id, int days) { return access.stabilise(level, id, days); }
    public static boolean inGate(net.minecraft.world.entity.Entity e) { return access.inGate(e); }
    public static Optional<String> openScripted(ServerPlayer p, String theme, int rank, BlockPos near, int floors) { return access.openScripted(p, theme, rank, near, floors); }
    public static Optional<String> openWandering(ServerPlayer p, BlockPos near, int floors, int lifetimeTicks) { return access.openWandering(p, near, floors, lifetimeTicks); }
    public static void release(ServerLevel level, String id) { access.release(level, id); }
    public static boolean mayTarget(net.minecraft.world.entity.LivingEntity mob, net.minecraft.world.entity.LivingEntity target) { return access.mayTarget(mob, target); }
    public static boolean showsBossBar(net.minecraft.world.entity.Entity mob, ServerPlayer player) { return access.showsBossBar(mob, player); }
    public static boolean waiting(ServerLevel level, String id) { return access.waiting(level, id); }
}
