package dev.awaken.terrain;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;

import java.lang.ref.WeakReference;

/**
 * The wind over the land (SPEC 15): a pure function of the world's seed and game time, so every client computes the
 * same wind with no packets, and the server could too.
 * <ul>
 * <li><b>Direction</b>: a heading for two in-game hours (100 s), then a 20 s turn to the next. Headings follow a slow
 * noise over the segments, so over in-game hours the wind wanders round, never jumps.</li>
 * <li><b>Steady strength</b> 0..1 breathes over a few minutes; rain and thunder raise it.</li>
 * <li><b>Gusts</b> 0..1 are fronts that roll downwind at {@link #FRONT_SPEED}: the gust at a place is the gust signal
 * delayed by how far downwind that place lies, so trees upwind of you move before the ones around you.</li>
 * </ul>
 * The client never learns the world seed, only its SHA-256 hash (the biome zoom seed every client gets at login).
 * {@link #salt} reads that hash through the level's own {@link BiomeManager} (which corner each probe position picks
 * depends on it), so the server, which hashes its seed the same way, gets the same salt. Read a {@link Field} once a
 * tick or frame, then ask it anywhere: no allocation.
 */
public final class Wind {
    private Wind() {}

    /** Ticks a heading holds (two in-game hours), and the turn to the next one at the end of each segment. */
    public static final long SEGMENT_TICKS = 2000, TURN_TICKS = 400;
    /** Blocks per second that gust fronts travel downwind. */
    public static final double FRONT_SPEED = 7.0;

    private static final int PROBES = 24;
    private static WeakReference<Level> saltLevel = new WeakReference<>(null);
    private static long saltValue;

    /** The world's wind salt, the same on the server and every client of that world. Cached for the last level. */
    public static synchronized long salt(Level level) {
        if (level != saltLevel.get()) {
            saltValue = saltOf(level.getBiomeManager(), level.registryAccess().lookupOrThrow(Registries.BIOME).listElements().findFirst().orElseThrow());
            saltLevel = new WeakReference<>(level);
        }
        return saltValue;
    }

    /** Fingerprint of a biome manager's zoom seed: which of the eight neighbouring cells each probe position picks. */
    static long saltOf(BiomeManager manager, Holder<Biome> anyBiome) {
        long[] acc = {0x9E3779B97F4A7C15L};
        BiomeResolver probe = (qx, qy, qz) -> {
            acc[0] = mix(acc[0] * 31 + (((long) qx * 73856093L) ^ ((long) qy * 19349663L) ^ ((long) qz * 83492791L)));
            return anyBiome;
        };
        BiomeManager m = manager.withDifferentSource(probe);
        for (int i = 0; i < PROBES; i++) m.getBiome(i * 64 + 4, 4, i * 48 + 4); // cell centres: the seed decides
        return acc[0];
    }

    /** The wind at one moment in one world. Mutable: set it, then ask {@link #gust} and {@link #strength} anywhere. */
    public static final class Field {
        public long salt;
        /** Game time in seconds (ticks / 20, with the partial tick). */
        public double seconds;
        /** Where the wind blows to: a unit vector on the ground plane. */
        public float dirX = 1F, dirZ;
        /** Strength without gusts, 0..1. */
        public float steady;
        /** Raises every gust toward 1 (0 = the real wind); a debug aid for showing gusts on demand. */
        public float gustFloor;
        /** A showcase front (debug aid, {@code /awaken terrain wind front}): launched at {@code frontAt} seconds from
         * (frontX, frontZ), rolling along (frontDirX, frontDirZ) at {@link #FRONT_SPEED}; NaN for none. */
        public double frontAt = Double.NaN, frontX, frontZ;
        public float frontDirX, frontDirZ;
        private double ax, az, bx, bz; // this segment's heading and the next one's (fronts travel along these)
        private float blend;           // 0 = this segment's heading, 1 = the next one's

        public Field set(Level level, float partialTick) {
            return set(salt(level), level.getGameTime(), partialTick, level.getRainLevel(partialTick), level.getThunderLevel(partialTick));
        }

        public Field set(long salt, long gameTime, float partialTick, float rain, float thunder) {
            return setTicks(salt, gameTime + (double) partialTick, rain, thunder);
        }

        /** The wind at a time in ticks with a fraction (the client passes its smoothed clock, {@link SwayClock}). */
        public Field setTicks(long salt, double ticks, float rain, float thunder) {
            this.salt = salt;
            this.seconds = ticks / 20.0;
            long seg = (long) Math.floor(ticks / SEGMENT_TICKS);
            double into = ticks - seg * (double) SEGMENT_TICKS;
            double u = Math.clamp((into - (SEGMENT_TICKS - TURN_TICKS)) / TURN_TICKS, 0.0, 1.0);
            blend = (float) (u * u * (3 - 2 * u));
            double h0 = heading(salt, seg), h1 = heading(salt, seg + 1);
            ax = Math.cos(h0);
            az = Math.sin(h0);
            bx = Math.cos(h1);
            bz = Math.sin(h1);
            double d = Math.IEEEremainder(h1 - h0, 2 * Math.PI); // the shorter way round
            double h = h0 + d * blend;
            dirX = (float) Math.cos(h);
            dirZ = (float) Math.sin(h);
            double breathe = valueNoise(seconds / 170.0, salt + 2);
            steady = (float) Math.clamp(0.2 + 0.45 * breathe + 0.3 * rain + 0.3 * thunder, 0.0, 1.0);
            return this;
        }

        /** Gust at a place, 0..1: the front that is passing there now. */
        public float gust(double x, double z) {
            double g = envelope(salt, seconds - (x * ax + z * az) / FRONT_SPEED);
            if (blend > 0F) g += (envelope(salt, seconds - (x * bx + z * bz) / FRONT_SPEED) - g) * blend;
            if (!Double.isNaN(frontAt)) g = Math.max(g, showcase(seconds - frontAt - ((x - frontX) * frontDirX + (z - frontZ) * frontDirZ) / FRONT_SPEED));
            return gustFloor > 0F ? (float) (1 - (1 - g) * (1 - gustFloor)) : (float) g;
        }

        /** How hard the wind blows at a place, 0..1: the steady wind lifted by the passing gust. */
        public float strength(double x, double z) {
            return Wind.strength(steady, gust(x, z));
        }
    }

    /** The showcase front over time since it passed: a 0.8 s rise, 1.2 s at full, a 2.5 s fall. */
    static double showcase(double tau) {
        if (tau <= 0 || tau >= 4.5) return 0;
        double u = tau < 0.8 ? tau / 0.8 : tau < 2.0 ? 1 : 1 - (tau - 2.0) / 2.5;
        return u * u * (3 - 2 * u);
    }

    /** The one formula for strength from steady wind and gust, shared with the terrain shader's sway. */
    public static float strength(float steady, float gust) {
        return Math.min(1F, steady * (0.6F + 0.8F * gust) + 0.25F * gust);
    }

    /** The heading (radians) of segment {@code seg}: a seeded base, wandering slowly from segment to segment. */
    static double heading(long salt, long seg) {
        double base = hash01(0, salt + 7) * 2 * Math.PI;
        return base + (valueNoise(seg / 5.0, salt + 3) - 0.5) * 2.4 * Math.PI + (hash01(seg, salt + 5) - 0.5) * 0.5;
    }

    /** The gust signal over time: mostly calm, with fronts a few seconds long. */
    static double envelope(long salt, double tau) {
        double n = 0.62 * valueNoise(tau / 8.5, salt + 11) + 0.38 * valueNoise(tau / 2.9, salt + 13);
        double g = Math.clamp((n - 0.44) / 0.36, 0.0, 1.0);
        return g * g * (3 - 2 * g);
    }

    /** Smooth value noise in 0..1. */
    static double valueNoise(double x, long seed) {
        double i = Math.floor(x);
        double f = x - i;
        double u = f * f * (3 - 2 * f);
        long n = (long) i;
        double a = hash01(n, seed), b = hash01(n + 1, seed);
        return a + (b - a) * u;
    }

    static double hash01(long n, long seed) {
        return (mix(n * 0x9E3779B97F4A7C15L + seed * 0xC2B2AE3D27D4EB4FL) >>> 11) * 0x1.0p-53;
    }

    private static long mix(long h) {
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        h *= 0x94D049BB133111EBL;
        return h ^ (h >>> 32);
    }
}
