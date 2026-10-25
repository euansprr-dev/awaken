package dev.awaken.core.api;

import net.minecraft.world.level.Level;

/**
 * Moving air (SPEC §17.6; the Wilds' updraft pylons and windways): the Ascent's free-flying states (the carry after a
 * rope, a glide, flight) feel it as an acceleration. Their motion is predicted on the client, so a place that makes
 * air must be readable on both sides from what each side has (blocks, block entities, entities, synced data), never
 * from server-only state. Modules register {@link Source}s on both sides; the Ascent sums them once a tick for each
 * airborne body. Sources must be cheap (a few block reads or a map lookup) and allocate nothing. No sources, no wind.
 */
public final class AirHooks {
    private AirHooks() {}

    /**
     * Scale, in blocks a tick², applied every tick on top of the state's own step (so it adds up fast): gravity is 0.08;
     * an updraft of 0.045 up climbs open wings 2-4 blocks a second (0.13 shot them up 58 blocks in 5 s); a windway of
     * 0.02-0.03 along carries a glide.
     */
    @FunctionalInterface
    public interface Source {
        /** Adds this source's air at the feet (x, y, z) to {@code out[0..2]} (x, y, z): accumulate, never overwrite. */
        void airAt(Level level, double x, double y, double z, double[] out);
    }

    private static volatile Source[] sources = new Source[0];

    public static synchronized void register(Source source) {
        Source[] s = java.util.Arrays.copyOf(sources, sources.length + 1);
        s[s.length - 1] = source;
        sources = s;
    }

    /** GameTests take their stand-in sources back out (try/finally). */
    public static synchronized void unregister(Source source) {
        sources = java.util.Arrays.stream(sources).filter(s -> s != source).toArray(Source[]::new);
    }

    /** The air at a point, every source summed, into {@code out} (zeroed first). False when there is none. */
    public static boolean sample(Level level, double x, double y, double z, double[] out) {
        out[0] = out[1] = out[2] = 0;
        Source[] s = sources;
        if (s.length == 0) return false;
        for (Source source : s) {
            try {
                source.airAt(level, x, y, z, out);
            } catch (RuntimeException e) {
                dev.awaken.Awaken.LOG.warn("[air] a source failed: {}", e.toString());
            }
        }
        return out[0] != 0 || out[1] != 0 || out[2] != 0;
    }
}
