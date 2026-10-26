package dev.awaken.delve.client;

import dev.awaken.core.api.NowHooks;
import dev.awaken.delve.home.HomeNet;
import dev.awaken.delve.home.HomeTrail;
import dev.awaken.signal.client.NowClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;

/**
 * A way home underground on the client (SPEC §28.5): the Signal's thread runs low along the floor back the way you
 * came, along the trail the server kept (never straight through the rock), up to {@link #RUN} blocks at a block a tick,
 * two of the Signal's motes a tick ({@code AscentFxBridge.threadMote}); where the path reaches the open sky a few motes
 * rise. It runs when the "Surface ↑" line first appears, and when you stand still {@link #IDLE} ticks with it (the Way
 * Forward's rule: no key), at most every {@link #GAP} ticks; {@link #thread()} runs it on request. NowClient's own
 * straight thread and compass leave this line alone ({@code NowClient.ownWay}).
 */
public final class HomeClient {
    private HomeClient() {}

    /** The longest run, the rest between two, and how long you stand still before it runs again (ticks). */
    static final int RUN = 40, GAP = 600, IDLE = 100;
    static final double SPEED = 1.0;

    private static int ticks, lastThread = -100_000, lastAsk = -100_000, still, runAt = -1;
    private static boolean hadLine, toSky, flourished;
    private static double lastX, lastZ;
    // the run's path: the player's feet when it began, then the crumbs back toward the sky
    private static final int MAX = HomeTrail.PATH + 1;
    private static final double[] PX = new double[MAX], PY = new double[MAX], PZ = new double[MAX], CUM = new double[MAX];
    private static int points;
    private static final BlockPos.MutableBlockPos M = new BlockPos.MutableBlockPos();
    private static final double[] AT = new double[5];

    public static void init(IEventBus modBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, e -> e.register(HomeNet.Way.TYPE, (m, ctx) -> way(m)));
        NowClient.ownWay(HomeTrail.NOW_ID);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ClientTickEvent.Post.class, e -> {
            tick();
            VentFx.tick();
        });
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            reset();
            VentFx.reset();
        });
    }

    /**
     * The Way Forward's glance (or anything else that wants the way home shown now): asks the server for the path and
     * runs the thread along it, past the 30 s rest. Nothing happens when there's no way home to show.
     */
    public static void thread() {
        ask(true);
    }

    private static void ask(boolean force) {
        if (ticks - lastAsk < 20 || Minecraft.getInstance().getConnection() == null) return;
        lastAsk = ticks;
        ClientPacketDistributor.sendToServer(new HomeNet.Ask(force));
    }

    static void reset() {
        runAt = -1;
        hadLine = false;
        lastThread = lastAsk = -100_000;
        still = 0;
    }

    private static void tick() {
        ticks++;
        Minecraft mc = Minecraft.getInstance();
        var p = mc.player;
        if (p == null || mc.level == null) return;
        if (runAt >= 0) run(mc.level);
        boolean moved = (p.getX() - lastX) * (p.getX() - lastX) + (p.getZ() - lastZ) * (p.getZ() - lastZ) > 0.0025;
        lastX = p.getX();
        lastZ = p.getZ();
        still = moved ? 0 : still + 1;
        if (ticks % 10 == 0) {
            boolean line = hasLine();
            if (line && !hadLine && ticks - lastThread >= GAP) ask(false);   // the line just appeared
            hadLine = line;
        }
        if (still == IDLE && hadLine && ticks - lastThread >= GAP && mc.gui.screen() == null) ask(false);
    }

    /** Whether the server shows the way home now (it's among the suggestions it sent). */
    private static boolean hasLine() {
        List<NowHooks.Suggestion> list = NowClient.suggestions();
        for (int i = 0; i < list.size(); i++) if (list.get(i).id().equals(HomeTrail.NOW_ID)) return true;
        return false;
    }

    /** The server's path home: from your feet along the crumbs, newest first. */
    static void way(HomeNet.Way m) {
        Minecraft mc = Minecraft.getInstance();
        var p = mc.player;
        if (p == null || mc.level == null || m.path().length == 0) return;
        if (!m.force() && (ticks - lastThread < GAP || mc.gui.screen() != null)) return;
        points = 0;
        add(p.getX(), p.getY(), p.getZ());
        long[] path = m.path();
        // start from the crumb nearest you (you may have walked on a little since the last one)
        int from = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < Math.min(path.length, 4); i++) {
            double d = sq(BlockPos.getX(path[i]) + 0.5 - p.getX()) + sq(BlockPos.getY(path[i]) - p.getY()) + sq(BlockPos.getZ(path[i]) + 0.5 - p.getZ());
            if (d < best) {
                best = d;
                from = i;
            }
        }
        for (int i = from; i < path.length && points < MAX; i++) {
            double x = BlockPos.getX(path[i]) + 0.5, y = BlockPos.getY(path[i]), z = BlockPos.getZ(path[i]) + 0.5;
            if (sq(x - PX[points - 1]) + sq(y - PY[points - 1]) + sq(z - PZ[points - 1]) < 1) continue;
            add(x, y, z);
        }
        if (points < 2) return;
        toSky = m.sky();
        flourished = false;
        runAt = lastThread = ticks;
    }

    private static void add(double x, double y, double z) {
        PX[points] = x;
        PY[points] = y;
        PZ[points] = z;
        CUM[points] = points == 0 ? 0 : CUM[points - 1] + Math.sqrt(sq(x - PX[points - 1]) + sq(y - PY[points - 1]) + sq(z - PZ[points - 1]));
        points++;
    }

    /** One tick of the run: two motes at the thread's head, low on the floor; a few rising where the sky opens. */
    private static void run(ClientLevel level) {
        int t = ticks - runAt;
        double total = CUM[points - 1], end = Math.min(total, RUN);
        double head = t * SPEED;
        if (head > end + 1) {
            runAt = -1;
            return;
        }
        if (t < 2) return;
        for (int k = 0; k < 2; k++) {
            double d = head + k * 0.5;
            if (d > end) break;
            at(d);
            double y = floorY(level, AT[0], AT[1], AT[2]);
            dev.awaken.body.ascent.client.AscentFxBridge.threadMote(AT[0], y, AT[2], AT[3], AT[4]);
        }
        // the sky's opening: the thread lifts there, a short column of motes
        if (toSky && !flourished && head >= total && total <= RUN) {
            flourished = true;
            double x = PX[points - 1], z = PZ[points - 1], y = floorY(level, x, PY[points - 1], z);
            var r = level.getRandom();
            for (int i = 0; i < 10; i++) {
                dev.awaken.body.ascent.client.AscentFxBridge.threadMote(x + (r.nextDouble() - 0.5) * 0.8, y + i * 0.3, z + (r.nextDouble() - 0.5) * 0.8, 0, 0);
            }
        }
    }

    /** Where the thread is {@code d} along the path: x, y, z and the heading (unit, across). */
    private static void at(double d) {
        int i = 1;
        while (i < points - 1 && CUM[i] < d) i++;
        double seg = CUM[i] - CUM[i - 1], f = seg < 1e-6 ? 1 : Mth.clamp((d - CUM[i - 1]) / seg, 0, 1);
        double dx = PX[i] - PX[i - 1], dz = PZ[i] - PZ[i - 1], h = Math.sqrt(dx * dx + dz * dz);
        AT[0] = PX[i - 1] + dx * f;
        AT[1] = PY[i - 1] + (PY[i] - PY[i - 1]) * f;
        AT[2] = PZ[i - 1] + dz * f;
        AT[3] = h < 1e-6 ? 0 : dx / h;
        AT[4] = h < 1e-6 ? 0 : dz / h;
    }

    /** Low on the floor: up out of rock a corner clipped, down onto the ground under the path (3 at most), 0.15 over it. */
    private static double floorY(ClientLevel level, double x, double y, double z) {
        int bx = Mth.floor(x), by = Mth.floor(y + 0.01), bz = Mth.floor(z);
        for (int i = 0; i < 2 && solid(level, bx, by, bz); i++) by++;
        for (int i = 0; i < 3; i++) {
            if (solid(level, bx, by - 1, bz)) return by + 0.15;
            by--;
        }
        return y + 0.15;
    }

    private static boolean solid(ClientLevel level, int x, int y, int z) {
        M.set(x, y, z);
        return !level.getBlockState(M).getCollisionShape(level, M).isEmpty();
    }

    private static double sq(double v) {
        return v * v;
    }
}
