package dev.awaken.above.machine;

import dev.awaken.above.Above;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Reward;
import dev.awaken.progression.RewardHooks;
import dev.awaken.progression.Stat;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

/**
 * The Recalibrator's respec maths. Every point above a stat's start value ({@link Stat#START}) is refunded as an
 * unspent point and the stat returns to its start. Uses only public Progression APIs: {@link Progression#addStat} with a
 * negative amount, and a stat-point {@link Reward} through {@link RewardHooks} (there is no public unspent setter).
 */
public final class Recalibrate {
    private Recalibrate() {}

    /** Points that a respec gives back for these stat values (in {@link Stat} order). */
    public static int refund(int[] stats) {
        int total = 0;
        for (int s : stats) total += Math.max(0, s - Stat.START);
        return total;
    }

    /** Essence the Recalibrator charges for refunding {@code points}. */
    public static int cost(int points) {
        return points <= 0 ? 0 : 100 + 25 * points;
    }

    public static int[] stats(Profile p) {
        int[] out = new int[Stat.values().length];
        for (Stat s : Stat.values()) out[s.ordinal()] = p.stat(s);
        return out;
    }

    /** Resets the player's stats and refunds the points. Returns the number of points refunded. */
    public static int apply(ServerPlayer player) {
        Profile p = Progression.profile(player);
        int points = refund(stats(p));
        if (points <= 0) return 0;
        Progression.respec(player);
        Progression.count(player, Above.RECALIBRATIONS, 1);
        Progression.markDirty(player);
        return points;
    }
}
