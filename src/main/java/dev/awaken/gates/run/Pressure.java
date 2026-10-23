package dev.awaken.gates.run;

import dev.awaken.core.api.MobRole;
import dev.awaken.gates.FloorState;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateRegistry;
import dev.awaken.gates.GateService;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.content.Modifier;
import dev.awaken.gates.content.Twist;
import dev.awaken.gates.world.FloorLayout;
import dev.awaken.gates.world.GateLayout;
import dev.awaken.gates.world.Room;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The gate wakes up (SPEC section 7): after 4 minutes on a floor a wave every 45 s, each a little bigger; at 8 minutes
 * the theme's Warden arrives. Red Gates run faster. No hard death timer: pressure, not a cliff.
 */
public final class Pressure {
    private Pressure() {}

    public static final int START = 4 * 60 * 20, PERIOD = 45 * 20, WARDEN = 8 * 60 * 20;

    static int start(GateRecord g) {
        return g.plan.is(Twist.RED_GATE) ? 3 * 60 * 20 : START;
    }

    static int period(GateRecord g) {
        return g.plan.is(Twist.RED_GATE) ? 35 * 20 : PERIOD;
    }

    static boolean applies(GateRecord g) {
        // the story's first gate teaches the floors without the clock
        return !g.plan.friendly() && !g.plan.is(Twist.HOLLOW_GATE) && !g.scripted;
    }

    /** 0 calm, 1 stirring, 2 waves, 3 the Warden is here. */
    public static int stage(GateRecord g, long onFloor) {
        if (!applies(g)) return 0;
        if (onFloor >= WARDEN) return 3;
        if (onFloor >= start(g)) return 2;
        if (onFloor >= start(g) - 600) return 1;
        return 0;
    }

    static void tick(ServerLevel level, GateRecord g) {
        if (!applies(g)) return;
        long now = GateService.now(level.getServer());
        Map<String, List<ServerPlayer>> groups = new HashMap<>();
        for (ServerPlayer p : level.players()) {
            RunState run = Runs.run(p);
            if (run.gateId != g.id || !p.isAlive() || run.clearedFloor(run.area, run.floor)) continue;
            groups.computeIfAbsent(run.area + ":" + run.floor, k -> new ArrayList<>()).add(p);
        }
        for (var e : groups.entrySet()) {
            List<ServerPlayer> players = e.getValue();
            RunState first = Runs.run(players.get(0));
            long since = Long.MAX_VALUE;
            for (ServerPlayer p : players) since = Math.min(since, Runs.run(p).floorStartedAt);
            long onFloor = now - since;
            FloorLayout f = GateLayout.of(g.plan).floor(first.area, first.floor);
            if (f == null) continue;
            FloorState fs = g.floor(first.area, first.floor);
            if (onFloor >= start(g) - 600 && onFloor < start(g) - 580) {
                for (ServerPlayer p : players) Signal.toast(p, Component.literal("The gate stirs."));
            }
            long due = onFloor >= start(g) ? 1 + (onFloor - start(g)) / period(g) : 0;
            if (due > fs.pressureWaves) {
                int k = fs.pressureWaves;
                fs.pressureWaves++;
                ServerPlayer target = players.get(level.getRandom().nextInt(players.size()));
                int spawned = wave(level, g, f, target, k);
                if (k == 0) {
                    for (ServerPlayer p : players) {
                        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.WARNING, Component.literal("The gate noticed you"))
                                .body(Component.literal("It will keep sending more until you leave this floor.")).ttl(20 * 5).build());
                    }
                }
                if (spawned > 0) level.playSound(null, target.blockPosition(), GatesRegistry.SND_PRESSURE.get(), SoundSource.HOSTILE, 1.4F, 0.9F - Math.min(0.4F, k * 0.05F));
                GateRegistry.get(level.getServer()).setDirty();
            }
            if (onFloor >= WARDEN && !fs.warden && !f.bossFloor) {
                fs.warden = true;
                ServerPlayer target = players.get(level.getRandom().nextInt(players.size()));
                BlockPos at = spotNear(level, g, f, target, 10);
                if (at != null) Rooms.spawn(level, g, f, -1, MobRole.Role.WARDEN, at);
                level.playSound(null, target.blockPosition(), GatesRegistry.SND_WARDEN.get(), SoundSource.HOSTILE, 2F, 0.7F);
                for (ServerPlayer p : players) {
                    Signal.send(p, SignalWindow.builder(SignalWindow.Kind.WARNING, Component.literal("The Warden is here"))
                            .body(Component.literal(Rooms.capital(f.theme.displayName) + " has sent its keeper for you.")).priority(5).ttl(20 * 5).build());
                }
                GateRegistry.get(level.getServer()).setDirty();
            }
        }
    }

    static int wave(ServerLevel level, GateRecord g, FloorLayout f, ServerPlayer target, int k) {
        int size = Math.min(9, 2 + k + g.plan.rank().ordinal() / 2 + (g.plan.has(Modifier.FORTUNE) ? 1 : 0) + (g.plan.has(Modifier.SWARM) ? 2 : 0));
        int elites = k >= 2 ? 1 + (k >= 5 ? 1 : 0) : 0;
        int n = 0;
        for (int i = 0; i < size; i++) {
            BlockPos at = spotNear(level, g, f, target, 7 + (i % 3) * 2);
            if (at == null) break;
            if (Rooms.spawn(level, g, f, -1, i < elites ? MobRole.Role.ELITE : MobRole.Role.NORMAL, at) != null) n++;
        }
        return n;
    }

    /** An open spot in the player's room (or a neighbouring one) a few blocks away; never inside a safe room. */
    static BlockPos spotNear(ServerLevel level, GateRecord g, FloorLayout f, ServerPlayer target, int minDist) {
        Where w = Where.at(level.getServer(), target.blockPosition());
        if (w == null || w.room() == null || w.floor() == null || w.floor().area != f.area || w.floor().floor != f.floor) return null;
        List<Room> candidates = new ArrayList<>();
        if (!w.room().type.safe()) candidates.add(w.room());
        for (Room.Door d : w.room().doors) {
            Room o = f.room(d.toRoom);
            if (o != null && !o.type.safe()) candidates.add(o);
        }
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (Room r : candidates) {
            for (BlockPos p : Rooms.spots(level, g, f, r)) {
                double d = Math.sqrt(p.distSqr(target.blockPosition()));
                if (d < minDist) continue;
                double score = Math.abs(d - minDist - 2) + level.getRandom().nextDouble() * 3;
                if (score < bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
        }
        return best;
    }
}
