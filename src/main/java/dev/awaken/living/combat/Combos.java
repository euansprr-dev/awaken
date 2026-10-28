package dev.awaken.living.combat;

import dev.awaken.core.api.Strikes;
import dev.awaken.living.LivingNet;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * How form bodies fight beyond their moves (SPEC §21.13): from B a melee blow is two (the second half a beat later, at most the creature's
 * normal blow, a light strike you can still deflect or step out of), and the creeper's own moves around its swell (C steps aside, B leaves a
 * seed that bursts after it, A lights the fuses of creepers beside it, S drags you in while it swells). The explosion stays vanilla's.
 */
public final class Combos {
    private Combos() {}

    /** Ticks between the two blows: the target's hurt cooldown has just run out (a blow inside it would be lost). */
    public static final int SECOND_AFTER = 10;

    /** The second blow, times the creature's own (B, A, S): never more than a normal blow (decision 19's cap holds). */
    public static float secondPower(int rung) {
        return rung >= 5 ? 0.7F : rung == 4 ? 0.6F : 0.5F;
    }

    private record Pending(ServerLevel level, Mob mob, LivingEntity target, long at) {}

    private static final List<Pending> PENDING = new ArrayList<>();
    private static boolean following;

    public static void onDamage(LivingDamageEvent.Post e) {
        if (following || !e.getSource().is(DamageTypes.MOB_ATTACK)) return;
        if (!(e.getSource().getEntity() instanceof Mob mob) || e.getSource().getDirectEntity() != mob || mob instanceof Creeper) return;
        if (!(mob.level() instanceof ServerLevel level) || Forms.of(mob).rung() < 3 || Forms.Species.of(mob.getType()) == null) return;
        for (Pending p : PENDING) if (p.mob == mob) return; // one follow-up at a time
        PENDING.add(new Pending(level, mob, e.getEntity(), level.getGameTime() + SECOND_AFTER));
    }

    public static void onTick(ServerTickEvent.Post e) {
        if (PENDING.isEmpty()) return;
        for (int i = PENDING.size() - 1; i >= 0; i--) {
            Pending p = PENDING.get(i);
            if (p.level.getGameTime() < p.at) continue;
            PENDING.remove(i);
            second(p.level, p.mob, p.target);
        }
    }

    /** The follow-up lands only if the target is still in reach and in sight: stepping back is the answer. */
    static boolean second(ServerLevel level, Mob mob, LivingEntity target) {
        if (!mob.isAlive() || !target.isAlive() || mob.level() != target.level()) return false;
        if (!mob.isWithinMeleeAttackRange(target) || !mob.getSensing().hasLineOfSight(target)) return false;
        float dmg = (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE) * secondPower(Forms.of(mob).rung());
        following = true;
        try {
            return Strikes.deliver(mob, Strikes.Kind.LIGHT, () -> target.hurtServer(level, mob.damageSources().mobAttack(mob), dmg));
        } finally {
            following = false;
        }
    }

    // ---- the creeper -------------------------------------------------------------------------------------------------

    /** Per creeper: the swell direction last tick, when its move may next fire, and a planted seed's tick and place. */
    private static final class Fuse {
        int lastDir = -1;
        long readyAt;
        long seedAt = -1;
        Vec3 seed = Vec3.ZERO;
    }

    private static final Map<Creeper, Fuse> FUSES = new WeakHashMap<>();
    static final int SEED_TICKS = 30, MOVE_REST = 160;

    public static void onEntityTick(EntityTickEvent.Post e) {
        if (!(e.getEntity() instanceof Creeper c) || !(c.level() instanceof ServerLevel level)) return;
        int rung = Forms.of(c).rung();
        if (rung < 2) return;
        Fuse f = FUSES.computeIfAbsent(c, k -> new Fuse());
        int dir = c.getSwellDir();
        long now = level.getGameTime();
        LivingEntity t = c.getTarget();
        if (dir > 0 && f.lastDir <= 0 && now >= f.readyAt && t != null) {
            f.readyAt = now + MOVE_REST;
            switch (rung) {
                case 2 -> sidestep(level, c, t);
                case 3 -> {
                    f.seed = c.position();
                    f.seedAt = now + SEED_TICKS;
                    cue(level, c, "seed", 4);
                }
                case 4 -> lightFuses(level, c, t);
                default -> cue(level, c, "pull", 4);
            }
        }
        f.lastDir = dir;
        if (f.seedAt >= 0) {
            if (now % 4 == 0) level.sendParticles(new DustParticleOptions(0xE8423A, 1.3F), f.seed.x, f.seed.y + 0.2, f.seed.z, 3, 0.25, 0.05, 0.25, 0);
            if (now >= f.seedAt) {
                f.seedAt = -1;
                level.explode(c, f.seed.x, f.seed.y + 0.1, f.seed.z, 1.6F, Level.ExplosionInteraction.NONE);
            }
        }
        if (rung >= 5 && dir > 0 && t instanceof Player p && p.distanceToSqr(c) < 7 * 7 && p.distanceToSqr(c) > 1.5 * 1.5) {
            Vec3 to = c.position().subtract(p.position()).multiply(1, 0, 1).normalize();
            p.setDeltaMovement(p.getDeltaMovement().add(to.scale(0.06)));
            if (now % 2 == 0) Moves.sync(p);
            if (now % 3 == 0) Moves.line(level, c.getEyePosition(), p.getEyePosition().subtract(0, 0.4, 0), new DustParticleOptions(0xA070FF, 1.0F));
        }
    }

    /** C: a quick hop to one side just as it starts to swell, so the arrow or the blow you had lined up misses. */
    static void sidestep(ServerLevel level, Creeper c, LivingEntity t) {
        Vec3 to = t.position().subtract(c.position()).multiply(1, 0, 1);
        if (to.lengthSqr() < 1e-4) return;
        to = to.normalize();
        double side = c.getRandom().nextBoolean() ? 1 : -1;
        c.setDeltaMovement(-to.z * side * 0.45, 0.25, to.x * side * 0.45);
        c.needsSync = true;
        cue(level, c, "sidestep", 2);
    }

    /** A: lights the fuses of up to two creepers beside the one you face (each still takes its own full swell to go off). */
    static void lightFuses(ServerLevel level, Creeper c, LivingEntity t) {
        int lit = 0;
        for (Creeper o : level.getEntitiesOfClass(Creeper.class, c.getBoundingBox().inflate(6), o -> o != c && o.isAlive() && !o.isIgnited())) {
            if (o.distanceToSqr(t) > 6 * 6) continue;
            o.ignite();
            Moves.line(level, c.getEyePosition(), o.getEyePosition(), new DustParticleOptions(0xE8423A, 1.1F));
            if (++lit >= 2) break;
        }
        if (lit > 0) level.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.FLINTANDSTEEL_USE, SoundSource.HOSTILE, 1.0F, 0.6F);
        cue(level, c, "ignite", 6);
    }

    static void cue(ServerLevel level, Mob mob, String move, int windup) {
        PacketDistributor.sendToPlayersTrackingEntity(mob, new LivingNet.FormCue(mob.getId(), move, level.getGameTime(), 0, windup));
    }
}
