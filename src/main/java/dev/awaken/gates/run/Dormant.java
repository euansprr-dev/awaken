package dev.awaken.gates.run;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/**
 * A prepared encounter waiting for you (SPEC §38 gate encounters): its AI stays on, so it breathes, sways and plays its
 * own idle clips, looks about and now and then shuffles or strolls a step or two from its spot, and walks back if it
 * drifts. It takes no target (Encounters.mayTarget). Woken, it stops and turns toward whoever woke it for the wake beat,
 * then fights. The goal outranks every other move and look goal while it runs; it is not saved, so a reload attaches it
 * again (Encounters.onLoad). Server thread only.
 */
final class Dormant extends Goal {
    /** How far it may drift from its spot before it walks back, and how far a stroll takes it. */
    private static final double LEASH = 4, STROLL = 2.5;

    private final Mob mob;
    private final double homeX, homeY, homeZ;
    private int next, look;
    private double lookX, lookY, lookZ;
    private boolean done;

    private Dormant(Mob mob, BlockPos home) {
        this.mob = mob;
        homeX = home.getX() + 0.5;
        homeY = home.getY();
        homeZ = home.getZ() + 0.5;
        next = 20 + mob.getRandom().nextInt(60);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    static void attach(Mob mob, BlockPos home) {
        for (WrappedGoal w : mob.goalSelector.getAvailableGoals()) if (w.getGoal() instanceof Dormant) return;
        mob.goalSelector.addGoal(0, new Dormant(mob, home));
    }

    @Override
    public boolean canUse() {
        return !done && (Encounters.waiting(mob) || Encounters.waking(mob));
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (Encounters.waiting(mob)) {
            idle();
            return;
        }
        // the wake beat: it stops and turns toward whoever woke it
        mob.getNavigation().stop();
        LivingEntity f = focus();
        if (f != null) mob.getLookControl().setLookAt(f, 40, 30);
    }

    @Override
    public void stop() {
        if (Encounters.waiting(mob) || Encounters.waking(mob)) return;
        done = true;
        // then the fight, at once, with whoever it turned to
        LivingEntity f = focus();
        if (f != null && mob.getTarget() == null && Encounters.mayTarget(mob, f)) mob.setTarget(f);
    }

    private void idle() {
        if (mob.getTarget() != null) mob.setTarget(null);
        double dx = mob.getX() - homeX, dz = mob.getZ() - homeZ;
        if (dx * dx + dz * dz > LEASH * LEASH) {
            if (!mob.getNavigation().isInProgress()) mob.getNavigation().moveTo(homeX, homeY, homeZ, 0.8);
            return;
        }
        if (--next <= 0) {
            RandomSource r = mob.getRandom();
            next = 60 + r.nextInt(100);
            if (r.nextFloat() < 0.35F) {
                double a = r.nextDouble() * Math.PI * 2, d = 0.8 + r.nextDouble() * (STROLL - 0.8);
                mob.getNavigation().moveTo(homeX + Math.cos(a) * d, homeY, homeZ + Math.sin(a) * d, 0.5);
            }
            double a = r.nextDouble() * Math.PI * 2;
            lookX = mob.getX() + Math.cos(a) * 4;
            lookY = mob.getEyeY() + (r.nextDouble() - 0.5) * 1.5;
            lookZ = mob.getZ() + Math.sin(a) * 4;
            look = 20 + r.nextInt(30);
        }
        if (look > 0) {
            look--;
            mob.getLookControl().setLookAt(lookX, lookY, lookZ);
        }
    }

    /** Whoever woke it: what last hurt it, else the nearest player. */
    private @Nullable LivingEntity focus() {
        LivingEntity hurt = mob.getLastHurtByMob();
        if (hurt != null && hurt.isAlive()) return hurt;
        return mob.level() instanceof ServerLevel level ? level.getNearestPlayer(mob.getX(), mob.getY(), mob.getZ(), 48, true) : null;
    }
}
