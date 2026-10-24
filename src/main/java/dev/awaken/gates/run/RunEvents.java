package dev.awaken.gates.run;

import dev.awaken.core.api.MobRole;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateRegistry;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.world.FloorLayout;
import dev.awaken.gates.world.GateLayout;
import dev.awaken.gates.world.Room;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

/** The run package's door to the event bus (keeps the internals package-private). */
public final class RunEvents {
    private RunEvents() {}

    public static void register(IEventBus bus) {
        bus.addListener((ItemEntityPickupEvent.Pre e) -> Runs.onPickup(e));
        bus.addListener((LivingIncomingDamageEvent e) -> Modifiers.onIncomingDamage(e));
        bus.addListener((LivingHealEvent e) -> Modifiers.onHeal(e));
        bus.addListener((LivingDropsEvent e) -> Modifiers.onDrops(e));
        bus.addListener((LivingChangeTargetEvent e) -> Twists.onTarget(e));
        bus.addListener((net.neoforged.neoforge.event.entity.EntityEvent.Size e) -> RigWellsLive.onSize(e));
    }

    public static void tick(MinecraftServer server) {
        Interactions.flush();
        Runs.tick(server);
        Runs.tickGates(server);
        RigDirector.tick(server);
        Twists.tickPuppets(server);
        Modifiers.tickPending(server);
        Hud.flush(server);
        if (server.getTickCount() % 100 == 30) {
            GateRegistry reg = GateRegistry.get(server);
            if (reg.pendingCount() > 0) for (ServerPlayer p : server.getPlayerList().getPlayers()) Claims.deliver(p);
        }
    }

    public static void onLogin(ServerPlayer p) {
        Runs.onLogin(p);
    }

    public static void onLogout(ServerPlayer p) {
        Twists.discardGhost(p);
        Modifiers.forget(p.getUUID());
        RunState run = Runs.run(p);
        if (run.active()) run.leftAt = dev.awaken.gates.GateService.now(p.level().getServer()); // saved with the player
    }

    public static void onRespawn(ServerPlayer p) {
        Runs.onRespawn(p);
    }

    public static void onPlayerDeath(LivingDeathEvent e) {
        Runs.onDeath(e);
    }

    public static void onMobDeath(ServerLevel level, LivingEntity dead, ServerPlayer killer, MobRole role) {
        Twists.onDeath(level, dead, killer);
    }

    public static void onGateMobDeath(ServerLevel level, GateRecord g, LivingEntity dead, ServerPlayer killer, MobRole role) {
        Modifiers.onMobDeath(level, g, dead, role);
        GateLayout l = GateLayout.of(g.plan);
        boolean mainLord = g.boss.isPresent() && g.boss.get().equals(dead.getUUID());
        boolean annexLord = g.annexBoss.isPresent() && g.annexBoss.get().equals(dead.getUUID());
        if (!mainLord && !annexLord && role.role() == MobRole.Role.BOSS) {
            // a Lord we lost track of (restart mid-fight): decide by where it fell
            Where w = Where.at(level.getServer(), dead.blockPosition());
            if (w != null && w.floor() != null && w.floor().bossFloor && w.room() != null && w.room().type == Room.Type.BOSS) {
                mainLord = w.area() == 0;
                annexLord = w.area() == 1;
            }
        }
        if (mainLord) Runs.lordDefeated(level, g, l.floor(0, g.plan.floors()), dead, killer);
        else if (annexLord) Runs.lordDefeated(level, g, l.floor(1, 2), dead, killer);
        if (killer != null) {
            Runs.onKill(killer, g, dead, role);
            // the party on that floor nearby shares the kill: their own kill XP share and their own elite loot roll
            RunState kr = Runs.run(killer);
            for (ServerPlayer mate : dev.awaken.core.api.Parties.sharing(killer, dead)) {
                RunState mr = Runs.run(mate);
                if (mr.gateId == g.id && mr.area == kr.area && mr.floor == kr.floor) Runs.onKill(mate, g, dead, role);
            }
        }
    }

    public static void mobGone(ServerLevel level, GateMobs.Key key) {
        Rooms.mobGone(level, key);
    }

    public static void talkToSpirit(ServerPlayer p, int gateId, Entity spirit) {
        GateRecord g = Runs.gateOf(p);
        if (g != null && g.id == gateId) Twists.talkToSpirit(p, g, spirit);
    }

    public static void friendlyLord(ServerPlayer p, int gateId, Entity lord) {
        GateRecord g = Runs.gateOf(p);
        if (g != null && g.id == gateId) Twists.friendlyLord(p, g, lord);
    }

    public static FloorLayout floor(GateRecord g, int area, int floor) {
        return GateLayout.of(g.plan).floor(area, floor);
    }

    // ---- test hooks (GameTests drive a run synchronously) ------------------------------------------------------------

    /** One run tick for one player right now (normally every 10 ticks). */
    public static void tickNow(ServerPlayer p) {
        RunState run = Runs.run(p);
        if (run.active()) Runs.tickPlayer(p.level().getServer(), p, run, p.level().getServer().getTickCount());
    }

    /** Runs queued block interactions now (normally at the end of the tick). */
    public static void flushNow() {
        Interactions.flush();
    }

    /** One per-gate tick (pressure, modifiers, twist actors) now. */
    public static void tickGateNow(ServerLevel level, GateRecord g) {
        Pressure.tick(level, g);
        Modifiers.tickGate(level, g);
        Twists.tickGate(level, g);
    }

    /** Runs every scheduled monster effect (Volatile bursts, Echoing rises) and queued water now, due or not. */
    public static void pendingNow(MinecraftServer server) {
        Modifiers.runAllPending(server);
    }

    public static void enterAnnexNow(ServerPlayer p, GateRecord g) {
        Twists.enterAnnex(p, g);
    }

    public static void returnFromInnerNow(ServerPlayer p, GateRecord g) {
        Twists.returnFromInner(p, g);
    }

    public static Entity ghostOf(ServerPlayer p) {
        return Twists.ghostOf(p);
    }

    public static boolean hasPuppet(ServerPlayer p) {
        return Twists.hasPuppet(p);
    }

    public static Entity twistActor(ServerLevel level, GateRecord g, int area, int floor, boolean follower) {
        return Twists.actor(level, g, area, floor, follower);
    }

    static boolean inGateDim(Entity e) {
        return dev.awaken.gates.GateService.inGate(e);
    }
}
