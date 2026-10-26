package dev.awaken.delve.home;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

import java.util.Arrays;
import java.util.stream.LongStream;

/**
 * The way you came (SPEC §28.5; PLAN 8.4), one per player: where you last stood under open sky (the anchor, crumb 0)
 * and the path you walked from there, a crumb every {@link #CRUMB} blocks, at most {@link #CAP} (the oldest half thins
 * out when it fills). Walking back to where you were cuts the loop off, so a dead end you explored is never the way
 * home. Pure: the server feeds it samples ({@link HomeServer}), tests feed it numbers.
 *
 * <p>The line ("Surface ↑ 34 · NE 60") shows once you've been out of the sky {@link #SHOW_AFTER} ticks, somewhere
 * the sky's light doesn't reach, and you're well under ({@link #DEEP}) or well away ({@link #AWAY} along the path)
 * from the anchor; it stays until you see the sky again or come back within {@link #NEAR} of it. Saved small (a
 * player attachment, {@link #SCHEMA}), so a player who logs out lost logs back in with the way home.
 */
public final class HomeTrail {
    public static final int SCHEMA = 1;
    /** The Now line's id. */
    public static final String NOW_ID = "delve:home";

    public static final int CAP = 96, CRUMB = 4, SHOW_AFTER = 300, DEEP = 12, AWAY = 32, NEAR = 6;
    /** A step this long between two samples is a teleport or a respawn: the way you came is gone. */
    public static final int JUMP = 48;
    /** Back within this of an older crumb: the loop since then is cut. */
    static final double LOOP = 3.0;
    /** Crumbs a thread request carries (newest first): about 90 blocks of path, more than one thread runs. */
    public static final int PATH = 24;

    final long[] crumbs = new long[CAP];
    int n;
    long outSince = -1;
    boolean shown;
    /** When this player last asked for the thread (server, not saved). */
    long lastAsk = Long.MIN_VALUE / 2;

    public HomeTrail() {}

    public boolean shown() {
        return shown;
    }

    public int size() {
        return n;
    }

    public BlockPos anchor() {
        return BlockPos.of(crumbs[0]);
    }

    public void clear() {
        n = 0;
        outSince = -1;
        shown = false;
    }

    /**
     * One sample of where the player stands (feet block): {@code sky}, nothing over their head; {@code underground},
     * rock over them and no sky light where they are.
     */
    public void step(int x, int y, int z, boolean sky, boolean underground, long now) {
        if (sky) {
            crumbs[0] = BlockPos.asLong(x, y, z);
            n = 1;
            outSince = -1;
            shown = false;
            return;
        }
        if (n == 0) return; // never under the sky since this trail began: no way to tell
        if (outSince < 0) outSince = now;
        double last = dist(crumbs[n - 1], x, y, z);
        if (last > JUMP) {
            clear();
            return;
        }
        // back where you were: the loop you walked since isn't the way home (walking back along the trail cuts it as you go)
        for (int i = 0; i < n - 1; i++) {
            if (dist2(crumbs[i], x, y, z) < LOOP * LOOP) {
                n = i + 1;
                last = dist(crumbs[i], x, y, z);
                break;
            }
        }
        if (last >= CRUMB) append(BlockPos.asLong(x, y, z));
        double near = dist(crumbs[0], x, y, z);
        if (shown) {
            if (near < NEAR) shown = false;
        } else if (underground && now - outSince >= SHOW_AFTER && near >= NEAR
                && (BlockPos.getY(crumbs[0]) - y >= DEEP || length(x, y, z) >= AWAY)) {
            shown = true;
        }
    }

    private void append(long c) {
        if (n == CAP) {
            // thin the older half (every other crumb, the anchor kept): the near path stays fine-grained
            int w = 1, half = n / 2;
            for (int r = 1; r < n; r++) {
                if (r < half && (r & 1) == 0) continue;
                crumbs[w++] = crumbs[r];
            }
            n = w;
        }
        crumbs[n++] = c;
    }

    /** The path's length from the anchor to (x, y, z). */
    public double length(int x, int y, int z) {
        double sum = 0;
        for (int i = 1; i < n; i++) sum += dist(crumbs[i - 1], BlockPos.getX(crumbs[i]), BlockPos.getY(crumbs[i]), BlockPos.getZ(crumbs[i]));
        return n == 0 ? 0 : sum + dist(crumbs[n - 1], x, y, z);
    }

    /** "Surface ↑ 34 · NE 60": how far up the anchor is from (x, y, z), and which way and how far across. */
    public String text(int x, int y, int z) {
        long a = crumbs[0];
        int dy = BlockPos.getY(a) - y;
        double dx = BlockPos.getX(a) - x, dz = BlockPos.getZ(a) - z;
        StringBuilder b = new StringBuilder("Surface");
        if (dy >= 2) b.append(" ↑ ").append(dy);
        else if (dy <= -2) b.append(" ↓ ").append(-dy);
        double across = Math.sqrt(dx * dx + dz * dz);
        if (across >= 4) b.append(" · ").append(compass(dx, dz)).append(' ').append(Math.round(across));
        return b.toString();
    }

    /** Eight-point compass from a horizontal offset (Minecraft: -z is north). */
    public static String compass(double dx, double dz) {
        double deg = Math.toDegrees(Math.atan2(dx, -dz));
        if (deg < 0) deg += 360;
        return COMPASS[(int) Math.round(deg / 45.0) % 8];
    }

    private static final String[] COMPASS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    /** The path home for the thread, newest crumb first, at most {@link #PATH}; the last is the anchor when it fits. */
    public long[] path() {
        int k = Math.min(n, PATH);
        long[] out = new long[k];
        for (int i = 0; i < k; i++) out[i] = crumbs[n - 1 - i];
        return out;
    }

    /** Whether {@link #path} reaches the anchor (the open sky). */
    public boolean pathReachesSky() {
        return n > 0 && n <= PATH;
    }

    /** Stages a trail as if walked (tours): the anchor first, out of the sky long enough to show. */
    public void stage(long[] path, long now) {
        n = 0;
        for (long c : path) {
            if (n == CAP) break;
            crumbs[n++] = c;
        }
        outSince = now - SHOW_AFTER;
        shown = n > 1;
    }

    static double dist(long c, int x, int y, int z) {
        return Math.sqrt(dist2(c, x, y, z));
    }

    static double dist2(long c, int x, int y, int z) {
        double dx = BlockPos.getX(c) - x, dy = BlockPos.getY(c) - y, dz = BlockPos.getZ(c) - z;
        return dx * dx + dy * dy + dz * dz;
    }

    // ---- saved ------------------------------------------------------------------------------------------------------

    private static final Codec<long[]> LONGS = Codec.LONG_STREAM.xmap(LongStream::toArray, Arrays::stream);

    public static final MapCodec<HomeTrail> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(t -> SCHEMA),
            LONGS.optionalFieldOf("crumbs", new long[0]).forGetter(t -> Arrays.copyOf(t.crumbs, t.n)),
            Codec.LONG.optionalFieldOf("outSince", -1L).forGetter(t -> t.outSince),
            Codec.BOOL.optionalFieldOf("shown", false).forGetter(t -> t.shown)
    ).apply(i, HomeTrail::load));

    private static HomeTrail load(int schema, long[] crumbs, long outSince, boolean shown) {
        HomeTrail t = new HomeTrail();
        t.n = Math.min(crumbs.length, CAP);
        System.arraycopy(crumbs, 0, t.crumbs, 0, t.n);
        t.outSince = t.n == 0 ? -1 : outSince;
        t.shown = t.n > 1 && shown;
        return t;
    }

    /** Nothing worth saving: no anchor yet. */
    public boolean isEmpty() {
        return n == 0;
    }
}
