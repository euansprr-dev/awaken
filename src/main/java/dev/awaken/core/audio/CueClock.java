package dev.awaken.core.audio;

import java.util.Arrays;

/** One player's cue cooldowns (pure; times are server ticks). Kept in memory: a restart forgets them, which is fine. */
public final class CueClock {
    private static final long NEVER = Long.MIN_VALUE / 4;
    private final long[] last = new long[Cue.values().length];
    private long lastAny = NEVER;

    public CueClock() {
        Arrays.fill(last, NEVER);
    }

    /** Ticks until this cue may play again (0 = now). */
    public long wait(Cue cue, long now) {
        long own = last[cue.ordinal()] + cue.cooldownTicks - now;
        long gap = cue.stage ? 0 : lastAny + Cue.GAP - now;
        if (cue == Cue.AWAKENING && last[cue.ordinal()] != NEVER) return Long.MAX_VALUE; // once
        return Math.max(0, Math.max(own, gap));
    }

    public boolean ready(Cue cue, long now) {
        return wait(cue, now) == 0;
    }

    /** Fires the cue if its cooldown and the gap allow; returns whether it fired. */
    public boolean tryFire(Cue cue, long now) {
        if (!ready(cue, now)) return false;
        last[cue.ordinal()] = now;
        lastAny = now;
        return true;
    }

    /** Why a cue is held back, for /awaken audio why and the Director log. */
    public String why(Cue cue, long now) {
        long w = wait(cue, now);
        if (w == 0) return "ready";
        if (w == Long.MAX_VALUE) return "plays once";
        long own = last[cue.ordinal()] + cue.cooldownTicks - now;
        return (own >= w ? "cooldown " : "another cue just played, ") + (w + 19) / 20 + " s";
    }
}
