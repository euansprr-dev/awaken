package dev.awaken.city;

import dev.awaken.city.kit.CityKit;
import dev.awaken.city.kit.KitBlocks;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Lit windows at night (look bible §7; Milestone 4 step B3a): the kit's window has a lit state with an emissive room
 * behind the glass and no light source (decision 26), and this switches it by the time of day. Each house has its own
 * evening: its lights come on between half past five and seven, a little over half its rooms light up, and they go
 * out between nine and one; a few night owls burn till dawn and some early risers light a room before six. All of it
 * seeded per house and window, so the city's night has a pattern that changes through the evening and never twinkles.
 *
 * <p>Cheap by design: a house's window positions are found once (painting the plot alone into a scratch buffer, the
 * painter's own rules; the nearest unknown house to a player every other tick), and a few houses a tick are brought to
 * their wanted state, only in loaded chunks, only where the block is still a lit window. One sweep of the city takes
 * about ten seconds; a clock jump settles in one sweep.
 */
public final class CityLights {
    private CityLights() {}

    private static @Nullable CityLayout cachedFor;
    private static final CityBuffer SCRATCH = new CityBuffer(20, 20);
    private static long[][] windows = new long[0][];
    private static byte[][] storeys = new byte[0][];
    private static int sweepCursor, unbuilt;
    static long switched, buildNanos, builds;

    /**
     * Houses a tick brought to their wanted state; one house's window list found every BUILD_EVERY ticks (painting the
     * plot alone costs about half a millisecond, so this keeps it near 0.25 ms a tick until every list is known).
     */
    static final int SWEEP = 3, BUILD_EVERY = 2;

    static void tick(MinecraftServer server) {
        CityLayout l = City.layout();
        if (l == null) return;
        ServerLevel level = server.overworld();
        if (cachedFor != l) {
            cachedFor = l;
            windows = new long[l.plan.plots.size()][];
            storeys = new byte[l.plan.plots.size()][];
            sweepCursor = 0;
            unbuilt = windows.length;
        }
        var players = level.players();
        if (players.isEmpty()) return;
        int tod = (int) Math.floorMod(level.getOverworldClockTime(), 24000L);
        int n = windows.length;
        // the nearest house to a player whose windows aren't known yet (a scan of the plots' corners: microseconds)
        if (unbuilt > 0 && server.getTickCount() % BUILD_EVERY == 0) {
            int best = -1;
            double bestD = Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                if (windows[i] != null) continue;
                CityPlan.Plot p = l.plan.plots.get(i);
                if (p.enter()) {
                    windows[i] = new long[0];
                    storeys[i] = new byte[0];
                    unbuilt--;
                    continue;
                }
                double x = l.cx + p.x0() + 0.5 * (p.x1() - p.x0()), z = l.cz + p.z0() + 0.5 * (p.z1() - p.z0());
                for (var pl : players) {
                    double dx = pl.getX() - x, dz = pl.getZ() - z, d = dx * dx + dz * dz;
                    if (d < bestD && level.isLoaded(BlockPos.containing(x, l.streetY, z))) {
                        bestD = d;
                        best = i;
                    }
                }
            }
            if (best >= 0) {
                long t0 = System.nanoTime();
                build(l, level.getSeed(), best);
                buildNanos += System.nanoTime() - t0;
                builds++;
                unbuilt--;
            }
        }
        for (int k = 0, done = 0; k < 32 && done < SWEEP && n > 0; k++) {
            int i = sweepCursor;
            sweepCursor = (sweepCursor + 1) % n;
            if (windows[i] == null || windows[i].length == 0) continue;
            sweep(level, i, tod);
            done++;
        }
    }

    /** The windows of plot i as the painter makes them (world positions) and the storey each sits in. */
    static void build(CityLayout l, long seed, int i) {
        CityPlan.Plot p = l.plan.plots.get(i);
        int m = CityLayout.PLOT_MARGIN;
        CityBuffer buf = SCRATCH;   // (plots are at most 14 across: 14 + 2 * 3 fits; one buffer, never a fresh one)
        buf.reset(p.x0() - m, p.z0() - m);
        CityGrammar.plot(new CityPainter.Ctx(l, seed), buf, p);
        Block window = CityKit.LIT_WINDOW.get();
        LongArrayList found = new LongArrayList();
        it.unimi.dsi.fastutil.bytes.ByteArrayList st = new it.unimi.dsi.fastutil.bytes.ByteArrayList();
        for (int ry = CityBuffer.YMIN; ry <= CityBuffer.YMAX; ry++) {
            for (int lz = 0; lz < buf.d; lz++) {
                for (int lx = 0; lx < buf.w; lx++) {
                    BlockState s = buf.cells[buf.index(lx, ry, lz)];
                    if (s == null || !s.is(window)) continue;
                    found.add(BlockPos.asLong(l.cx + buf.px0 + lx, l.streetY + ry, l.cz + buf.pz0 + lz));
                    st.add((byte) Math.max(0, (ry - p.lift() - 1) / 4));
                }
            }
        }
        windows[i] = found.toLongArray();
        storeys[i] = st.toByteArray();
    }

    static void sweep(ServerLevel level, int i, int tod) {
        long[] ws = windows[i];
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int k = 0; k < ws.length; k++) {
            m.set(ws[k]);
            if (!level.isLoaded(m)) return;
            if (set(level, m, wanted(i, k, storeys[i][k], tod))) switched++;
        }
    }

    /** Lights or darkens the window at pos if it is still one of the kit's (and not already so); true when it changed. */
    static boolean set(ServerLevel level, BlockPos pos, boolean lit) {
        BlockState s = level.getBlockState(pos);
        if (!s.is(CityKit.LIT_WINDOW.get()) || s.getValue(KitBlocks.LitWindow.LIT) == lit) return false;
        level.setBlock(pos, s.setValue(KitBlocks.LitWindow.LIT, lit), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        return true;
    }

    static long hash(long a, long b) {
        long z = 0x5EED11E7L ^ (a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL);
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return (z ^ (z >>> 33)) & Long.MAX_VALUE;
    }

    /**
     * Whether window k (on storey s) of plot i is lit at this time of day (0 = 6:00, 12000 = 18:00, 18000 = midnight):
     * the house's evening (on from 17:36-19:12 plus the room's own minutes, off from 21:12-1:00, upstairs last), a
     * little over half its rooms; night owls till dawn; early risers' kitchens from 4:48.
     */
    public static boolean wanted(int i, int k, int s, int tod) {
        long h = hash(i, 1000 + k);
        if (h % 100 >= 58) return false;                       // a room nobody uses at night (or the shutters are shut)
        long house = hash(i, 1);
        int dusk = 11600 + (int) (house % 1600) + (int) ((h >>> 8) % 500);
        boolean owl = hash(i, 3) % 100 < 6;
        int bed = owl ? 23300 : 15200 + (int) ((house >>> 12) % 3800) + 120 * s - (int) ((h >>> 20) % 700);
        if (tod >= dusk && tod < bed) return true;
        // early risers: a downstairs room before six
        boolean early = hash(i, 4) % 100 < 25 && s == 0 && (h >>> 30) % 3 == 0;
        return early && (tod >= 22800 || tod < 300);
    }

    /** For commands and tests: how many window lists are known and how many switched so far. */
    static String info() {
        int known = 0, total = 0;
        for (long[] w : windows) {
            if (w == null) continue;
            known++;
            total += w.length;
        }
        return known + " houses' windows known (" + total + " windows), " + switched + " switched; " + builds + " lists built, "
                + String.format(java.util.Locale.ROOT, "%.3f", builds == 0 ? 0.0 : buildNanos / 1e6 / builds) + " ms each";
    }
}
