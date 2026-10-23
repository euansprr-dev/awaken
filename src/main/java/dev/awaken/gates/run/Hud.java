package dev.awaken.gates.run;

import dev.awaken.core.AwakenNet;
import dev.awaken.gates.FloorState;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateRegistry;
import dev.awaken.gates.GateService;
import dev.awaken.gates.content.Modifier;
import dev.awaken.gates.content.Twist;
import dev.awaken.gates.net.GatePayloads;
import dev.awaken.gates.world.FloorLayout;
import dev.awaken.gates.world.GateLayout;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Sends the run HUD (floor, Satchel, pressure, noise, collapse) to its player. */
public final class Hud {
    private Hud() {}

    private static final Set<UUID> SOON = new HashSet<>();

    public static void sendSoon(ServerPlayer p) {
        SOON.add(p.getUUID());
    }

    static void flush(net.minecraft.server.MinecraftServer server) {
        if (SOON.isEmpty()) return;
        for (UUID u : SOON) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null) send(p);
        }
        SOON.clear();
    }

    public static void clear(ServerPlayer p) {
        AwakenNet.send(p, GatePayloads.RunHud.NONE);
    }

    public static void send(ServerPlayer p) {
        RunState run = Runs.run(p);
        if (!run.active()) {
            clear(p);
            return;
        }
        GateRecord g = GateRegistry.get(p.level().getServer()).gate(run.gateId);
        if (g == null) {
            clear(p);
            return;
        }
        long now = GateService.now(p.level().getServer());
        FloorLayout f = GateLayout.of(g.plan).floor(run.area, run.floor);
        FloorState fs = g.floor(run.area, run.floor);
        long onFloor = now - run.floorStartedAt;
        int pressure = Pressure.stage(g, onFloor);
        int collapse = g.plan.has(Modifier.COLLAPSE_TIMER) && !run.clearedFloor(run.area, run.floor)
                ? (int) Math.max(0, (Modifiers.collapseTicks(g) - (now - (fs.entered() ? fs.enteredAt : now))) / 20) : -1;
        int flags = (g.plan.has(Modifier.LIGHTLESS) ? GatePayloads.RunHud.LIGHTLESS : 0)
                | (g.plan.is(Twist.RED_GATE) && !g.bossDead ? GatePayloads.RunHud.RED : 0)
                | (g.plan.has(Modifier.LOW_GRAVITY) ? GatePayloads.RunHud.LOW_GRAVITY : 0)
                | (run.clearedFloor(run.area, run.floor) ? GatePayloads.RunHud.CLEARED_FLOOR : 0)
                | (Twists.disguised(p, g) ? GatePayloads.RunHud.DISGUISED : 0)
                | (g.plan.has(Modifier.SILENCE) ? GatePayloads.RunHud.SILENCE : 0);
        int floors = run.area == 0 ? g.plan.floors() : 2;
        String hint = Twists.hint(p, g, run);
        AwakenNet.send(p, new GatePayloads.RunHud(g.id, (f == null ? g.plan.theme() : f.theme).id, g.plan.rank().ordinal(), run.area,
                run.floor, floors, Satchel.count(run.satchel), (int) Math.round(run.satchelXp), run.kills, (int) (onFloor / 20), pressure,
                g.plan.has(Modifier.SILENCE) ? run.noise : -1, collapse, flags, hint == null ? "" : hint));
    }

    public static void sendSatchel(ServerPlayer p, boolean open) {
        RunState run = Runs.run(p);
        AwakenNet.send(p, new GatePayloads.SatchelView(run.active() ? run.satchel.stream().map(s -> s.copy()).toList() : java.util.List.of(),
                (int) Math.round(run.satchelXp), open));
    }
}
