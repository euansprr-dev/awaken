package dev.awaken.body.ascent;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Updraft vents as moving air (core.api.AirHooks; SPEC §17.6): a column of radius {@link #RADIUS} rising
 * {@link #HEIGHT} from the vent's top, full strength to {@link #FULL} and fading to nothing at the top, a little weaker
 * toward its rim. Client side the loaded vents are a short list (vents register as their block entities load), so a
 * sample walks it with no block reads and no allocation.
 */
public final class Updrafts {
    private Updrafts() {}

    public static final double RADIUS = 3.5, HEIGHT = 34, FULL = 24;
    /** Lift at the column's heart (blocks a tick²): open wings climb 2-4 blocks a second, a carry falls slower. */
    public static final double LIFT = 0.045;

    private static final List<Level> LEVELS = new ArrayList<>();
    private static final List<long[]> SPOTS = new ArrayList<>();

    static synchronized void add(Level level, BlockPos pos) {
        int i = LEVELS.indexOf(level);
        if (i < 0) {
            LEVELS.add(level);
            SPOTS.add(new long[]{pos.asLong()});
            return;
        }
        long[] s = SPOTS.get(i);
        for (long p : s) if (p == pos.asLong()) return;
        long[] n = java.util.Arrays.copyOf(s, s.length + 1);
        n[s.length] = pos.asLong();
        SPOTS.set(i, n);
    }

    static synchronized void remove(Level level, BlockPos pos) {
        int i = LEVELS.indexOf(level);
        if (i < 0) return;
        long key = pos.asLong();
        long[] s = SPOTS.get(i);
        SPOTS.set(i, java.util.Arrays.stream(s).filter(p -> p != key).toArray());
        if (SPOTS.get(i).length == 0) {
            LEVELS.remove(i);
            SPOTS.remove(i);
        }
    }

    /** The lift of one vent at (x, y, z) feet, or 0 outside its column. */
    public static double liftOf(BlockPos vent, double x, double y, double z) {
        double dx = x - (vent.getX() + 0.5), dz = z - (vent.getZ() + 0.5);
        double r2 = dx * dx + dz * dz;
        if (r2 > RADIUS * RADIUS) return 0;
        double h = y - (vent.getY() + 1);
        if (h < -1 || h > HEIGHT) return 0;
        double up = h <= FULL ? 1 : 1 - (h - FULL) / (HEIGHT - FULL);
        double rim = 1 - 0.35 * r2 / (RADIUS * RADIUS);
        return LIFT * up * rim;
    }

    /** AirHooks source: every loaded vent's lift at the feet (strongest wins, columns don't stack). */
    static void airAt(Level level, double x, double y, double z, double[] out) {
        long[] spots;
        synchronized (Updrafts.class) {
            int i = LEVELS.indexOf(level);
            if (i < 0) return;
            spots = SPOTS.get(i);
        }
        double best = 0;
        BlockPos.MutableBlockPos m = MUTABLE.get();
        for (long p : spots) {
            m.set(BlockPos.getX(p), BlockPos.getY(p), BlockPos.getZ(p));
            best = Math.max(best, liftOf(m, x, y, z));
        }
        out[1] += best;
    }

    private static final ThreadLocal<BlockPos.MutableBlockPos> MUTABLE = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    public static synchronized void clear() {
        LEVELS.clear();
        SPOTS.clear();
    }

    /** Client-only bits, kept apart so a dedicated server never loads them. */
    static final class Client {
        private Client() {}

        static void breathe(Level level, BlockPos pos) {
            dev.awaken.body.ascent.client.UpdraftFx.breathe(level, pos);
        }
    }
}
