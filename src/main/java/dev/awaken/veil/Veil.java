package dev.awaken.veil;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Public API of the Veil for other modules (stable). Server side unless noted.
 * <ul>
 *   <li>{@link #isSpirit} / {@link #isMeditating}: e.g. gates should not count a spirit as being in a run.</li>
 *   <li>{@link #energyRegenMultiplier}: powers multiplies its energy regen by this (meditation and the Stillpoint).</li>
 *   <li>{@link #STILLPOINT}: the Stillpoint dimension; trial arenas may be built at |x| or |z| >= 16384.</li>
 * </ul>
 */
public final class Veil {
    private Veil() {}

    public static final ResourceKey<Level> STILLPOINT = ResourceKey.create(Registries.DIMENSION, Awaken.id("stillpoint"));

    /** Whether this player is out of their body right now (server: live projection; client: last state received). */
    public static boolean isSpirit(Player player) {
        if (player.level().isClientSide()) return ClientSide.spirit(player);
        return Projection.LIVE.containsKey(player.getUUID());
    }

    public static boolean isMeditating(Player player) {
        return !player.level().isClientSide() && Meditation.isMeditating(player.getUUID());
    }

    public static boolean inStillpoint(Entity entity) {
        return entity.level().dimension() == STILLPOINT;
    }

    /** Energy regen multiplier: x2 while meditating, x1.5 anywhere in the Stillpoint (the two stack to x3). */
    public static double energyRegenMultiplier(Player player) {
        double m = 1.0;
        if (isMeditating(player)) m += 1.0;
        if (inStillpoint(player)) m += 0.5;
        return m;
    }

    /** Step out of the body now (no meditation). Returns false if the Veil refused (already out, shocked, disabled...). */
    public static boolean project(ServerPlayer player) {
        return Projection.start(player, false, null);
    }

    /** Snap a spirit back into its body. No-op if not projecting. */
    public static void returnToBody(ServerPlayer player) {
        if (Projection.LIVE.containsKey(player.getUUID())) Projection.end(player, Projection.EndReason.RETURNED);
    }

    /** Send a player to the Stillpoint temple, remembering where they were. */
    public static void enterStillpoint(ServerPlayer player) {
        Stillpoint.enter(player);
    }

    /** Leave the Stillpoint for where the player came from (or their spawn). */
    public static void leaveStillpoint(ServerPlayer player) {
        Stillpoint.leave(player);
    }

    static boolean enabled() {
        try {
            return AwakenConfig.VEIL.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    /** Indirection so common code never loads client classes. */
    static final class ClientSide {
        static volatile java.util.function.Predicate<Player> spirit = p -> false;
        static boolean spirit(Player p) { return spirit.test(p); }
    }

    /** Called by the client module to answer {@link #isSpirit} on the client. */
    public static void installClientQuery(java.util.function.Predicate<Player> query) {
        ClientSide.spirit = query;
    }
}
