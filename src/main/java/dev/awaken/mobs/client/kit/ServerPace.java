package dev.awaken.mobs.client.kit;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

/**
 * The server's clock as a boss fight sees it (SPEC §47.1 "Hits land now"): the last tick the server finished (its
 * {@code mobs_pace}, sent each tick while a boss fights near you) and how long ago it came, run on at most one tick
 * past it. In step, that is the game clock; in a server stall the boss holds where the server is, so a blow you see
 * land is one that lands and a parry keeps its timing (the game clock ran on and showed blows before they came).
 */
public final class ServerPace {
    private ServerPace() {}

    /** A pace older than this (wall time) is a fight over: the game clock takes over again. */
    static final long STALE = 3_000_000_000L;

    private static long tick = Long.MIN_VALUE, at;
    private static Level level;

    public static void onPace(long gameTime) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        boolean fresh = tick == Long.MIN_VALUE || level != mc.level;
        level = mc.level;
        // never backwards: a pace out of order is where the clock already is
        tick = fresh ? gameTime : Math.max(gameTime, tick);
        at = System.nanoTime();
    }

    /** Ticks on the server's clock now, with the frame's fraction; the game clock when no boss fights near. */
    public static float now(Level l, float partialTicks) {
        long since = System.nanoTime() - at;
        if (tick == Long.MIN_VALUE || l != level || since > STALE) return l.getGameTime() + partialTicks;
        float per = l.tickRateManager().nanosecondsPerTick();
        return tick + Math.min(1F, since / per);
    }

    /**
     * Ticks on the server's clock since game tick {@code from}, with the frame's fraction, as a double: a difference
     * taken before anything is rounded, so it stays smooth however old the world is ({@link #now} is a float of the game
     * time itself, good to a hundredth of a tick only in a young world). For what must glide (SPEC §67.2, the ring).
     */
    public static double since(Level l, long from, float partialTicks) {
        long ago = System.nanoTime() - at;
        if (tick == Long.MIN_VALUE || l != level || ago > STALE) return (l.getGameTime() - from) + (double) partialTicks;
        double per = l.tickRateManager().nanosecondsPerTick();
        return (tick - from) + Math.min(1.0, ago / per);
    }

    public static void reset() {
        tick = Long.MIN_VALUE;
        level = null;
    }
}
