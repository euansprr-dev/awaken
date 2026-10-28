package dev.awaken.gates.run;

import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateRegistry;
import dev.awaken.gates.GateService;
import dev.awaken.gates.world.FloorLayout;
import dev.awaken.gates.world.GateLayout;
import dev.awaken.gates.world.Room;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Visible, waiting encounters, and the boundary shared by targeting and boss-bar membership. Server thread only. */
public final class Encounters {
    private Encounters() {}

    private static final String WAITING = "awaken_encounter_waiting";
    /** Allows a fight across its doorway without recruiting the rest of the floor. */
    static final int DOOR_REACH = 8;

    static void hold(Entity entity, GateMobs.Key key) {
        if (!(entity instanceof Mob mob) || !(entity.level() instanceof ServerLevel level) || mob.isNoAi()) return;
        GateRecord g = GateRegistry.get(level.getServer()).gate(key.gate());
        if (g == null || key.room() < 0) return;
        var fs = g.floor(key.area(), key.floor());
        if (!fs.prepared.contains(key.room()) || fs.triggered.contains(key.room())) return;
        entity.getPersistentData().putBoolean(WAITING, true);
        mob.setTarget(null);
        mob.getNavigation().stop();
        mob.getMoveControl().setWait();
        mob.setNoAi(true);
    }

    static boolean waiting(Entity e) {
        return e.getPersistentData().getBooleanOr(WAITING, false);
    }

    private static void release(Entity entity) {
        if (!waiting(entity)) return;
        entity.getPersistentData().remove(WAITING);
        if (entity instanceof Mob mob) mob.setNoAi(false);
    }

    static void onLoad(Entity entity, GateMobs.Key key) {
        if (!(entity.level() instanceof ServerLevel level) || !waiting(entity)) return;
        GateRecord g = GateRegistry.get(level.getServer()).gate(key.gate());
        if (g != null && g.floor(key.area(), key.floor()).triggered.contains(key.room())) release(entity);
    }

    static void wake(ServerLevel level, GateRecord g, FloorLayout f, int key) {
        for (var id : GateMobs.aliveIds(new GateMobs.Key(g.id, f.area, f.floor, key))) {
            Entity e = level.getEntity(id);
            if (e != null) release(e);
        }
    }

    /** A ranged hit engages just this encounter, including its companions. Its doors seal on actual entry. */
    static void onDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || !GateService.isGateLevel(level) || !waiting(victim)) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker.level() != level) return;
        GateMobs.Key key = GateMobs.keyOf(victim);
        if (key == null) return;
        Where at = Where.at(level.getServer(), attacker.blockPosition());
        if (at == null || at.gate().id != key.gate() || at.floor() == null || at.area() != key.area() || at.floorNo() != key.floor()) return;
        GateRecord g = GateRegistry.get(level.getServer()).gate(key.gate());
        FloorLayout f = g == null ? null : GateLayout.of(g.plan).floor(key.area(), key.floor());
        if (f == null) return;
        g.floor(key.area(), key.floor()).triggered.add(key.room());
        GateRegistry.get(level.getServer()).setDirty();
        wake(level, g, f, key.room());
    }

    public static boolean mayTarget(LivingEntity mob, LivingEntity target) {
        return !GateService.isGateLevel(mob.level()) || !waiting(mob) && inBoundary(mob, target);
    }

    private static boolean inBoundary(Entity mob, Entity target) {
        GateMobs.Key key = GateMobs.keyOf(mob);
        if (key == null || !(mob.level() instanceof ServerLevel level) || !GateService.isGateLevel(level)) return true;
        if (target.level() != level) return false;
        Where at = Where.at(level.getServer(), target.blockPosition());
        if (at == null || at.gate().id != key.gate() || at.floor() == null || at.area() != key.area() || at.floorNo() != key.floor()) return false;
        if (key.room() < 0) return mob.distanceToSqr(target) <= 32 * 32;
        int room = key.room() >= 256 ? (key.room() >> 8) - 1 : key.room();
        Room home = at.floor().room(room);
        if (home == null) return false;
        int x0 = home.gx * 16, z0 = home.gz * 16, width = home.gw * 16, depth = home.gh * 16;
        if (key.room() >= 256) {
            int zone = key.room() & 255;
            x0 += (zone % home.gw) * 16;
            z0 += (zone / home.gw) * 16;
            width = depth = 16;
        }
        return at.x() >= x0 - DOOR_REACH && at.x() < x0 + width + DOOR_REACH
                && at.z() >= z0 - DOOR_REACH && at.z() < z0 + depth + DOOR_REACH;
    }

    public static boolean showsBossBar(Entity mob, ServerPlayer player) {
        if (!GateService.isGateLevel(mob.level())) return true;
        GateMobs.Key key = GateMobs.keyOf(mob);
        if (key == null) return !GateService.inGate(mob) || mob.getData(dev.awaken.core.api.CoreApi.MOB_ROLE.get()).gateId().isEmpty();
        return !waiting(mob) && inBoundary(mob, player) && mob.distanceToSqr(player) <= 32 * 32
                && (player.hasLineOfSight(mob) || mob instanceof Mob m && m.getTargetUnchecked() == player);
    }
}
