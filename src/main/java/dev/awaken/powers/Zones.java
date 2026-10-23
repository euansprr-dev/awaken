package dev.awaken.powers;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * World areas left behind by force reactions. Steam (Heat meets Frost) blinds mobs inside it and hides anyone who
 * sneaks in it: mobs can't pick them as a target.
 */
public final class Zones {
    private Zones() {}

    public static final class Steam {
        final ServerLevel level;
        final Vec3 centre;
        final double radiusSq;
        final double radius;
        final long until;

        Steam(ServerLevel level, Vec3 centre, double radius, long until) {
            this.level = level;
            this.centre = centre;
            this.radius = radius;
            this.radiusSq = radius * radius;
            this.until = until;
        }

        boolean contains(Entity e) {
            return e.level() == level && e.position().distanceToSqr(centre) <= radiusSq;
        }
    }

    private static final List<Steam> STEAM = new ArrayList<>();

    public static void steam(ServerLevel level, Vec3 centre, double radius, int ticks) {
        STEAM.add(new Steam(level, centre, radius, level.getGameTime() + ticks));
        level.playSound(null, centre.x, centre.y, centre.z, PowerSounds.STEAM.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        level.sendParticles(ParticleTypes.CLOUD, centre.x, centre.y + 0.5, centre.z, 40, radius * 0.4, 0.8, radius * 0.4, 0.05);
    }

    public static boolean inSteam(Entity e) {
        if (STEAM.isEmpty()) return false;
        for (Steam s : STEAM) if (s.contains(e)) return true;
        return false;
    }

    public static int steamCount() {
        return STEAM.size();
    }

    static void tick() {
        if (STEAM.isEmpty()) return;
        for (int i = STEAM.size() - 1; i >= 0; i--) {
            Steam s = STEAM.get(i);
            long now = s.level.getGameTime();
            if (now > s.until) {
                STEAM.remove(i);
                continue;
            }
            if (now % 3 == 0) {
                s.level.sendParticles(ParticleTypes.CLOUD, s.centre.x, s.centre.y + 0.6, s.centre.z, 6, s.radius * 0.45, 0.7, s.radius * 0.45, 0.01);
                s.level.sendParticles(ParticleTypes.WHITE_SMOKE, s.centre.x, s.centre.y + 0.3, s.centre.z, 3, s.radius * 0.4, 0.4, s.radius * 0.4, 0.01);
            }
            if (now % 10 == 0) {
                AABB box = AABB.ofSize(s.centre, s.radius * 2, s.radius * 2, s.radius * 2);
                for (Mob m : s.level.getEntitiesOfClass(Mob.class, box, s::contains)) {
                    m.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false));
                    LivingEntity t = m.getTarget();
                    if (t != null && t.distanceToSqr(m) > 4) m.setTarget(null);
                }
                for (Player p : s.level.getEntitiesOfClass(Player.class, box, x -> s.contains(x) && x.isCrouching())) {
                    for (Mob m : s.level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(24), x -> x.getTarget() == p)) m.setTarget(null);
                }
            }
        }
    }

    static void onChangeTarget(LivingChangeTargetEvent event) {
        if (STEAM.isEmpty()) return;
        LivingEntity next = event.getNewAboutToBeSetTarget();
        if (next == null) return;
        if (next instanceof Player p && p.isCrouching() && inSteam(p)) {
            event.setCanceled(true);
            return;
        }
        if (inSteam(event.getEntity()) && next.distanceToSqr(event.getEntity()) > 4) event.setCanceled(true);
    }

    static void clear() {
        STEAM.clear();
    }
}
