package dev.awaken.living.combat;

import dev.awaken.living.combat.Moves.Def;
import dev.awaken.living.combat.Moves.Kind;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Runs a formed creature's moves (SPEC §21.11): when it has a target at the right range and its rest is over it winds a move up (standing
 * still, facing, a tell on the ground and a sound), strikes once, carries a lunge or a charge through, and rests. It outranks the creature's own
 * melee while it runs, and costs nothing while it does not (a roll every few ticks, only with a target).
 */
final class MarkGoal extends Goal {
    private enum Phase { IDLE, WIND, ACT, REST }

    private static final int REST_TICKS = 8, LUNGE_TICKS = 10, CHARGE_TICKS = 16;

    private final Mob mob;
    private final List<Def> moves;
    private final int rung;
    private Phase phase = Phase.IDLE;
    private Def cur;
    private LivingEntity target;
    private Vec3 aim = Vec3.ZERO;
    private int ticks;
    private long readyAt;
    private boolean hit;

    MarkGoal(Mob mob, List<Def> moves, int rung) {
        this.mob = mob;
        this.moves = moves;
        this.rung = rung;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!(mob.level() instanceof ServerLevel) || mob.tickCount < readyAt || mob.getRandom().nextInt(5) != 0) return false;
        LivingEntity t = mob.getTarget();
        if (t == null || !t.isAlive()) return false;
        double dist = Math.sqrt(mob.distanceToSqr(t));
        List<Def> ok = new ArrayList<>(2);
        for (Def d : moves) {
            if (dist < d.min() || dist > d.max()) continue;
            boolean needsSight = d.kind() != Kind.RALLY && d.kind() != Kind.ROAR && d.kind() != Kind.STOMP && d.kind() != Kind.SUMMON;
            if (needsSight && !mob.getSensing().hasLineOfSight(t)) continue;
            ok.add(d);
        }
        if (ok.isEmpty()) return false;
        cur = ok.get(mob.getRandom().nextInt(ok.size()));
        target = t;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return phase != Phase.IDLE && mob.isAlive() && target != null && (target.isAlive() || phase == Phase.REST);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        phase = Phase.WIND;
        ticks = 0;
        hit = false;
        aim = target.position();
        if (mob.level() instanceof ServerLevel level) Moves.tellSound(level, mob, cur);
    }

    @Override
    public void stop() {
        phase = Phase.IDLE;
        readyAt = mob.tickCount + (long) (cur == null ? 60 : cur.cooldown() * (0.8 + 0.4 * mob.getRandom().nextDouble()));
        target = null;
    }

    @Override
    public void tick() {
        if (!(mob.level() instanceof ServerLevel level) || cur == null) return;
        ticks++;
        switch (phase) {
            case WIND -> {
                mob.getNavigation().stop();
                mob.setDeltaMovement(mob.getDeltaMovement().multiply(0.2, 1, 0.2));
                // a lunge or a charge goes where you stood when it began (that is how it is dodged); a shot, a spit and a pull follow you
                if (cur.kind() != Kind.LUNGE && cur.kind() != Kind.CHARGE) aim = target.position();
                mob.getLookControl().setLookAt(aim.x, aim.y + 0.5, aim.z);
                if (ticks % 3 == 1) Moves.tellFx(level, mob, cur, rung, aim, Math.min(1F, (float) ticks / cur.windup()));
                if (ticks >= cur.windup()) {
                    Moves.strike(level, mob, target, cur, aim, rung);
                    ticks = 0;
                    phase = cur.kind() == Kind.LUNGE || cur.kind() == Kind.CHARGE ? Phase.ACT : Phase.REST;
                }
            }
            case ACT -> {
                boolean charge = cur.kind() == Kind.CHARGE;
                if (charge) {
                    Vec3 dir = aim.subtract(mob.position()).multiply(1, 0, 1);
                    if (dir.lengthSqr() > 0.01) {
                        dir = dir.normalize();
                        mob.setDeltaMovement(dir.x * 0.95, mob.getDeltaMovement().y, dir.z * 0.95);
                    }
                }
                if (!hit && target.isAlive() && mob.getBoundingBox().inflate(0.5, 0.2, 0.5).intersects(target.getBoundingBox())) {
                    Vec3 away = target.position().subtract(mob.position());
                    Moves.hit(level, mob, target, cur, away.x, away.z, charge ? 1.1 : 0.7, charge ? 0.4 : 0.25);
                    hit = true;
                }
                if (hit || ticks >= (charge ? CHARGE_TICKS : LUNGE_TICKS) || (charge && mob.horizontalCollision)) {
                    ticks = 0;
                    phase = Phase.REST;
                }
            }
            case REST -> {
                mob.getNavigation().stop();
                if (ticks >= REST_TICKS) phase = Phase.IDLE;
            }
            default -> {
            }
        }
    }
}
