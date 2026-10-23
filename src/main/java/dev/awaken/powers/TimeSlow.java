package dev.awaken.powers;

import dev.awaken.powers.content.PowerSummon;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Real slow motion for Flow and Foresight: entities near the caster only tick every Nth server tick, so mobs, their
 * attacks and their arrows all run at 1/N speed while the caster moves normally. Damage cooldowns keep counting in
 * real time so the caster's hits still land.
 */
public final class TimeSlow {
    private TimeSlow() {}

    public static final class Zone {
        final ServerLevel level;
        final ServerPlayer owner;
        final double radiusSq;
        int factor;
        boolean removed;

        Zone(ServerPlayer owner, double radius, int factor) {
            this.level = owner.level();
            this.owner = owner;
            this.radiusSq = radius * radius;
            this.factor = Math.max(2, factor);
        }
    }

    private static final List<Zone> ZONES = new ArrayList<>();

    public static Zone add(ServerPlayer owner, double radius, int factor) {
        Zone z = new Zone(owner, radius, factor);
        ZONES.add(z);
        return z;
    }

    public static void remove(Zone z) {
        z.removed = true;
        ZONES.remove(z);
    }

    public static boolean active() {
        return !ZONES.isEmpty();
    }

    static void clear() {
        ZONES.clear();
    }

    static void onEntityTick(EntityTickEvent.Pre event) {
        if (ZONES.isEmpty()) return;
        Entity e = event.getEntity();
        if (e instanceof Player || e instanceof Display || !(e.level() instanceof ServerLevel level)) return;
        for (int i = 0; i < ZONES.size(); i++) {
            Zone z = ZONES.get(i);
            if (z.level != level || z.owner.isRemoved() || e.distanceToSqr(z.owner) > z.radiusSq) continue;
            if (exempt(z.owner, e)) return;
            if ((level.getGameTime() + e.getId()) % z.factor != 0) {
                event.setCanceled(true);
                if (e instanceof LivingEntity le && le.damageCooldownTime > 0) le.damageCooldownTime--;
            }
            return;
        }
    }

    private static boolean exempt(ServerPlayer owner, Entity e) {
        if (e instanceof PowerSummon s) return s.getOwner() == owner;
        if (e instanceof Projectile p) return p.getOwner() == owner;
        return Allies.isAlly(e);
    }
}
