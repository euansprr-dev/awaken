package dev.awaken.body.ascent;

import dev.awaken.body.Body;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Public API of the Ascent (stable; SPEC §12, §17): the ladder's tiers a player holds, granting one (the Wilds' sky
 * platform core, an Ascension trial, a story beat), the state the body is in, and whether a player came somewhere by
 * moving (the Wilds pay a site's best roll and its route record only for arriving by movement, never by tower).
 * Server side unless noted.
 */
public final class Ascent {
    private Ascent() {}

    /** The ladder above the Runner (everyone's from the first second), in order. */
    public enum Tier {
        TETHER, LEAPER, WINGS, SKYBORN, SOVEREIGN;

        public int bit() {
            return 1 << ordinal();
        }

        AscentPayloads.Tier wire() {
            return AscentPayloads.Tier.values()[ordinal()];
        }
    }

    private static final List<BiConsumer<ServerPlayer, Tier>> GRANTED = new ArrayList<>();

    /** Whether the player holds a tier (both sides: the client from its synced copy). */
    public static boolean has(Player p, Tier t) {
        if (p.level().isClientSide()) return (clientTiers & t.bit()) != 0;
        return (AscentServer.tiers(p) & t.bit()) != 0;
    }

    /** The highest tier held, or null (a Runner). */
    public static @org.jspecify.annotations.Nullable Tier highest(Player p) {
        Tier best = null;
        for (Tier t : Tier.values()) if (has(p, t)) best = t;
        return best;
    }

    /**
     * Grants a tier and every tier under it (the ladder is climbed in order), saves it and tells the client. True when
     * something new was granted; listeners hear each new tier (scenes, the Horizon line, directives).
     * {@code source} is a short reason for the log ("sky platform", "tether vow").
     */
    public static boolean grant(ServerPlayer p, Tier t, String source) {
        int before = AscentServer.tiers(p);
        int bits = before;
        for (Tier u : Tier.values()) {
            if (u.ordinal() <= t.ordinal()) bits |= u.bit();
        }
        if (bits == before) return false;
        AscentServer.setTiers(p, bits);
        dev.awaken.core.DirectorLog.log(p, "ascent", "tier " + t + " from " + source);
        for (Tier u : Tier.values()) {
            if ((bits & u.bit()) != 0 && (before & u.bit()) == 0) for (var l : GRANTED) l.accept(p, u);
        }
        return true;
    }

    /** Listens for tiers granted (each new tier once, lowest first). */
    public static void onGranted(BiConsumer<ServerPlayer, Tier> listener) {
        GRANTED.add(listener);
    }

    /** The Ascent state the body is in (the server's view; NONE on the ground or in a Runner move). */
    public static AscentState state(ServerPlayer p) {
        AscentMotion m = p.getExistingDataOrNull(AscentContent.MOTION);
        return m == null ? AscentState.NONE : m.state;
    }

    /**
     * Whether the player came here by moving: within the last {@code seconds} (≤ 20) the body spent at least a third of
     * the time in an airborne Ascent state or a Runner move (a swing, a glide, a wall-run, a hang) and placed no block.
     * A tower doesn't count; a walk doesn't either.
     */
    public static boolean arrivedByMovement(ServerPlayer p, int seconds) {
        AscentMotion m = p.getExistingDataOrNull(AscentContent.MOTION);
        if (m == null) return false;
        int ticks = Math.min(AscentMotion.TRAIL, Math.max(20, seconds * 20));
        long now = AscentServer.now(p);
        if (now - m.placedAt < ticks) return false;
        return m.movingTicks(ticks) * 3 >= ticks;
    }

    /** Whether a Runner move or an airborne Ascent state holds the body right now (server side). */
    static boolean moving(ServerPlayer p, AscentMotion m) {
        return m.state.airborne() || Body.isWallRunning(p) || Body.isHanging(p);
    }

    /** The client's copy of its own tiers (set from the server's Tiers message). */
    static int clientTiers;
}
