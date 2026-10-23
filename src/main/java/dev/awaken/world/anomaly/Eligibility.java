package dev.awaken.world.anomaly;

import dev.awaken.core.api.GateHooks;
import dev.awaken.progression.Progression;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * When the director may count down and start an anomaly for a player. Pure over {@link Facts} so every rule is tested
 * without a world; {@link #of} reads the facts from a real player.
 */
public final class Eligibility {
    private Eligibility() {}

    public record Facts(boolean enabled, boolean awakened, boolean overworld, boolean spectator, boolean inGate, boolean sleeping,
                        long ticksSinceAwakened, boolean atHome, boolean hasActive) {}

    /** Why nothing may happen for this player right now, or null when the countdown runs. */
    public static @Nullable String blocked(Facts f) {
        if (!f.enabled()) return "anomalies are switched off in this world";
        if (!f.awakened()) return "not awakened yet";
        if (f.inGate()) return "in a gate run";
        if (!f.overworld()) return "not in the overworld";
        if (f.spectator()) return "spectating";
        if (f.sleeping()) return "asleep";
        if (f.ticksSinceAwakened() < Pacing.GRACE) return "just awakened (no anomaly in the first 2 minutes)";
        if (f.atHome()) return "at home inside a Hearth (nothing interrupts home)";
        if (f.hasActive()) return "one anomaly is already active for you";
        return null;
    }

    public static Facts of(ServerPlayer p, AnomalyData data, AnomalyMemory mem, long now) {
        ServerLevel level = p.level();
        boolean overworld = level.dimension() == Level.OVERWORLD;
        long since = mem.awakenedAt < 0 ? Long.MAX_VALUE : Math.max(0, now - mem.awakenedAt);
        boolean home = overworld && Spots.REAL_HEARTH.near(level, p.blockPosition(), Spots.HOME_RADIUS);
        return new Facts(data.enabled(), Progression.profile(p).awakened(), overworld, p.isSpectator(), GateHooks.inGate(p), p.isSleeping(),
                since, home, data.activeFor(p.getUUID()) != null);
    }
}
