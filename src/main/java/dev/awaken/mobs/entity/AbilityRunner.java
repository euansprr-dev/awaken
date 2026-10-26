package dev.awaken.mobs.entity;

import dev.awaken.core.api.Strikes;
import dev.awaken.mobs.MobSounds;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.def.Ability;
import dev.awaken.mobs.def.MobDef;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The telegraph state machine. Idle: every 4 ticks pick a ready ability that fits the distance. Wind-up: hold still
 * (for rooted abilities), show where it will land, brighten. Execute once. Charges and leaps finish over the next
 * few ticks. Bosses swap the ability list when their phase changes.
 */
public final class AbilityRunner {
    private final AwakenMob mob;
    private List<Ability> list;
    private int[] cooldowns;
    private int global;

    private @Nullable Ability active;
    private int activeIndex, left, total;
    private Vec3 aim = Vec3.ZERO;
    private @Nullable LivingEntity aimTarget;
    private final List<Vec3> marks = new ArrayList<>();

    private int chargeTicks;
    private Vec3 chargeDir = Vec3.ZERO;
    private float chargeDamage;
    private @Nullable Ability chargeAbility;
    private final List<Integer> chargeHits = new ArrayList<>(4);
    private int leapTicks;
    private @Nullable Ability leapAbility;

    AbilityRunner(AwakenMob mob) {
        this.mob = mob;
        setPhase(0);
    }

    /** Switches to a boss phase's abilities, with a short random stagger so they don't all fire at once. */
    public void setPhase(int phase) {
        MobDef d = mob.def();
        int p = Math.max(0, Math.min(d.phases.size() - 1, phase));
        list = d.phases.get(p).abilities();
        cooldowns = new int[list.size()];
        for (int i = 0; i < cooldowns.length; i++) cooldowns[i] = 20 + mob.getRandom().nextInt(40) + i * 15;
        global = 20;
    }

    public void interrupt() {
        active = null;
        chargeTicks = 0;
        leapTicks = 0;
        mob.setTelegraph(0, false);
        mob.setFlag(AwakenMob.F_CHARGING, false);
    }

    public @Nullable Ability active() {
        return active;
    }

    boolean holdsStill() {
        return (active != null && active.roots()) || chargeTicks > 0 || leapTicks > 0;
    }

    void tick(ServerLevel level) {
        if (chargeTicks > 0) tickCharge(level);
        if (leapTicks > 0) tickLeap(level);
        if (active != null) {
            tickWindup(level);
            return;
        }
        for (int i = 0; i < cooldowns.length; i++) if (cooldowns[i] > 0) cooldowns[i]--;
        if (global > 0) {
            global--;
            return;
        }
        // a broken stance (Arms) starts nothing until it stands again: the break is the opening it earned
        if (list.isEmpty() || (mob.tickCount & 3) != 0 || mob.staggered() || mob.flag(AwakenMob.F_FALLEN) || mob.flag(AwakenMob.F_DORMANT)
                || mob.flag(AwakenMob.F_DECOY) || mob.isWatchedNow() || mob.fleeTicks > 0 || chargeTicks > 0 || leapTicks > 0) return;
        LivingEntity t = mob.getTarget();
        if (t == null || !t.isAlive()) return;
        int n = list.size();
        int start = mob.getRandom().nextInt(n);
        for (int k = 0; k < n; k++) {
            int i = (start + k) % n;
            if (cooldowns[i] > 0) continue;
            Ability a = list.get(i);
            if (usable(level, a, t)) {
                begin(level, i, a, t);
                return;
            }
            // Abilities that need an entity query back off when they can't fire, so they don't query every 4 ticks.
            if (a.kind() == Ability.Kind.SUMMON || a.kind() == Ability.Kind.DECOY || a.kind() == Ability.Kind.HEAL) cooldowns[i] = 40;
        }
    }

    private boolean usable(ServerLevel level, Ability a, LivingEntity t) {
        double d = mob.distanceTo(t) - t.getBbWidth() * 0.5 - mob.getBbWidth() * 0.5;
        if (d > a.range()) return false;
        return switch (a.kind()) {
            case BOLT, ARROWS, BEAM, CHARGE -> mob.getSensing().hasLineOfSight(t);
            case SLAM -> d <= a.radius() + 0.5;
            case CONE -> d <= a.radius();
            case FANGS_RING, GUARD -> d <= 5;
            case SUMMON -> Mobs.minionRoom(level, mob, a.count());
            case DECOY -> level.getEntitiesOfClass(AwakenMob.class, mob.getBoundingBox().inflate(24), AwakenMob::isDecoy).size() < 3;
            case HEAL -> Abilities.alliesHurt(level, mob, a.radius());
            case LEAP -> mob.onGround() && d > 1.5;
            default -> true;
        };
    }

    private void begin(ServerLevel level, int index, Ability a, LivingEntity t) {
        active = a;
        activeIndex = index;
        total = left = a.windup() + (a.heavy() ? 8 : 0);
        aimTarget = t;
        aim = t.position();
        marks.clear();
        if (a.kind() == Ability.Kind.BEAM || a.kind() == Ability.Kind.BOLT || a.kind() == Ability.Kind.CHARGE) aim = Abilities.chest(t);
        if (!mob.abilityMarks(level, a, t, marks)) {
            switch (a.kind()) {
                case RAIN -> rainMarks(level, a);
                case SUMMON -> marks.addAll(Abilities.summonSpots(level, mob, a.count()));
                case BLINK -> marks.add(Abilities.blinkSpot(mob, t));
                default -> { }
            }
        }
        if (a.roots()) mob.getNavigation().stop();
        mob.setTell(left, a.strike(), a.heavy());
        mob.setAggressive(true);
        float pitch = switch (a.kind()) {
            case BOLT, ARROWS, NOVA -> 1.5F;
            case SUMMON, DECOY, HEAL -> 0.9F;
            default -> 1.0F;
        };
        mob.playTell(a.strike(), a.heavy(), pitch);
        if (a.heavy()) {
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(mob) < 24 * 24) p.sendOverlayMessage(net.minecraft.network.chat.Component.literal(a.name()).withColor(mob.def().glow));
            }
        }
    }

    private void rainMarks(ServerLevel level, Ability a) {
        int n = Math.max(1, a.count());
        for (ServerPlayer p : level.players()) {
            if (marks.size() >= n) break;
            if (Abilities.isEnemy(mob, p) && p.distanceToSqr(mob) < a.range() * a.range()) marks.add(p.position());
        }
        Vec3 centre = mob.hasHome() ? Vec3.atBottomCenterOf(mob.getHomePosition()) : mob.position();
        float spread = mob.hasHome() ? Math.min(14, mob.getHomeRadius()) : 8;
        int guard = 0;
        while (marks.size() < n && guard++ < n * 3) {
            float ang = mob.getRandom().nextFloat() * Mth.TWO_PI;
            float r = 2 + mob.getRandom().nextFloat() * spread;
            Vec3 c = aimTarget != null && mob.getRandom().nextBoolean() ? aimTarget.position() : centre;
            marks.add(new Vec3(c.x + Mth.cos(ang) * r, c.y, c.z + Mth.sin(ang) * r));
        }
    }

    private void tickWindup(ServerLevel level) {
        Ability a = active;
        if (a == null) return;
        if (!mob.isAlive() || mob.flag(AwakenMob.F_FALLEN)) {
            interrupt();
            return;
        }
        LivingEntity t = aimTarget;
        boolean tracking = left > 6 && t != null && t.isAlive();
        if (tracking) {
            switch (a.kind()) {
                case BEAM, BOLT, CHARGE -> aim = Abilities.chest(t);
                case ARROWS, CONE, FANGS, LEAP -> aim = t.position();
                default -> { }
            }
        }
        if (t != null) mob.getLookControl().setLookAt(t, 30.0F, 30.0F);
        if (a.roots()) mob.getNavigation().stop();
        if (!mob.abilityTell(level, a, left, total, aim, marks) && left % 3 == 0) show(level, a);
        if ((left & 3) == 0) mob.setTell(left, a.strike(), a.heavy());
        if (--left > 0) return;
        active = null;
        mob.setTelegraph(0, false);
        cooldowns[activeIndex] = a.cooldown() + mob.getRandom().nextInt(20);
        global = mob.def().tier.isBoss() ? 14 : 28;
        Strikes.deliver(mob, a.strike(), () -> {
            if (!mob.abilityStrike(level, a, t, aim, marks)) execute(level, a, t);
            return true;
        });
    }

    /**
     * Begins the named ability of this phase now (a scripted moment: the seep heart's call as a phase opens), if nothing
     * is winding up and there is a living target. False otherwise.
     */
    public boolean start(ServerLevel level, String name) {
        LivingEntity t = mob.getTarget();
        if (active != null || t == null || !t.isAlive()) return false;
        for (int i = 0; i < list.size(); i++) {
            Ability a = list.get(i);
            if (!a.name().equals(name)) continue;
            begin(level, i, a, t);
            return true;
        }
        return false;
    }

    private void show(ServerLevel level, Ability a) {
        int colour = mob.def().glow;
        boolean heavy = a.heavy();
        Vec3 feet = mob.position();
        switch (a.kind()) {
            case SLAM, AURA, PULL, GUST, DARKNESS, HEAL -> Telegraph.ring(level, feet, a.radius() * (1.0F - left / (float) total * 0.5F), 20, colour, heavy);
            case BURST -> {
                Telegraph.ring(level, feet, a.radius(), 36, colour, heavy);
                Telegraph.ring(level, feet, a.inner(), 14, 0xFFFFFF, false);
            }
            case NOVA, FANGS_RING, GUARD, FRENZY, DECOY -> Telegraph.gather(level, Abilities.chest(mob), colour);
            case LEAP, ROOT, LIFT, CLOUD -> Telegraph.ring(level, aim, a.radius(), 16, colour, heavy);
            case RAIN -> {
                for (Vec3 m : marks) Telegraph.ring(level, m, a.radius(), 14, colour, heavy);
            }
            case SUMMON -> {
                for (Vec3 m : marks) Telegraph.gather(level, m.add(0, 0.5, 0), colour);
            }
            case BLINK -> {
                if (!marks.isEmpty()) {
                    Vec3 m = marks.get(0);
                    level.sendParticles(ParticleTypes.PORTAL, m.x, m.y + 0.8, m.z, 8, 0.3, 0.6, 0.3, 0.2);
                }
            }
            case CHARGE, FANGS -> Telegraph.line(level, feet.add(0, 0.2, 0), new Vec3(aim.x, feet.y + 0.2, aim.z), colour, heavy);
            case BEAM -> Telegraph.line(level, mob.getEyePosition(), aim, colour, left <= 6);
            case CONE -> Telegraph.cone(level, feet, aim.subtract(feet), a.radius(), colour);
            case BOLT, ARROWS -> Telegraph.gather(level, mob.getEyePosition(), colour);
        }
    }

    private void execute(ServerLevel level, Ability a, @Nullable LivingEntity t) {
        switch (a.kind()) {
            case SLAM -> {
                Abilities.area(level, mob, a, mob.position(), a.radius(), 0, 0.9);
                Abilities.slamFx(level, mob, mob.position(), a.radius());
            }
            case BURST -> {
                Abilities.area(level, mob, a, mob.position(), a.radius(), a.inner(), 0.6);
                for (float r = Math.max(1, a.inner()); r <= a.radius(); r += 2.5F) Telegraph.ring(level, mob.position(), r, 24, mob.def().glow, true);
                mob.playLevelSound(MobSounds.BOSS_TOLL.get(), 2.0F, 0.6F);
            }
            case LEAP -> {
                Vec3 d = aim.subtract(mob.position());
                double h = Math.sqrt(d.x * d.x + d.z * d.z);
                mob.setDeltaMovement(d.x / Math.max(1, h) * Math.min(1.4, h * 0.16), 0.55 + Math.min(0.4, h * 0.03), d.z / Math.max(1, h) * Math.min(1.4, h * 0.16));
                leapTicks = 40;
                leapAbility = a;
            }
            case CHARGE -> {
                Vec3 d = aim.subtract(mob.position().add(0, mob.getBbHeight() * 0.5, 0));
                if (!mob.def().flies()) d = new Vec3(d.x, 0, d.z);
                chargeDir = d.lengthSqr() < 1.0E-4 ? mob.getLookAngle() : d.normalize();
                chargeTicks = 14;
                chargeDamage = Abilities.damage(mob, a);
                chargeAbility = a;
                chargeHits.clear();
                mob.setFlag(AwakenMob.F_CHARGING, true);
                mob.playLevelSound(net.minecraft.sounds.SoundEvents.RAVAGER_ROAR, 0.6F, 1.4F);
            }
            case BOLT -> Abilities.bolts(level, mob, a, aim);
            case ARROWS -> { if (t != null) Abilities.arrows(level, mob, a, t); }
            case NOVA -> Abilities.nova(level, mob, a.count(), Abilities.bolt(mob, a), a.power());
            case RAIN -> { for (Vec3 m : marks) Abilities.rainStrike(level, mob, a, m); }
            case BEAM -> Abilities.beam(level, mob, a, aim);
            case BLINK -> { if (!marks.isEmpty()) Abilities.blink(level, mob, a, t, marks.get(0)); }
            case SUMMON -> Abilities.summon(level, mob, a, marks);
            case CLOUD -> Hazards.cloud(level, mob, aim, a.radius(), 100, Abilities.damage(mob, a) * 0.2F, Abilities.bolt(mob, a), Abilities.effect(a));
            case CONE -> Abilities.cone(level, mob, a, aim);
            case PULL -> Abilities.pull(level, mob, a);
            case GUST -> Abilities.gust(level, mob, a);
            case HEAL -> Abilities.heal(level, mob, a);
            case ROOT -> Abilities.root(level, mob, a, aim);
            case FANGS -> Abilities.fangs(level, mob, a, aim);
            case FANGS_RING -> Abilities.fangRing(level, mob, a);
            case DECOY -> Abilities.decoys(level, mob, a.count());
            case GUARD -> {
                mob.startGuard(60);
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, mob.getX(), mob.getY() + 1, mob.getZ(), 12, 0.5, 0.6, 0.5, 0.1);
            }
            case LIFT -> Abilities.lift(level, mob, a, aim);
            case DARKNESS -> Abilities.darkness(level, mob, a);
            case AURA -> mob.startAura(Math.max(2, a.count()), a.radius(), Abilities.damage(mob, a), Abilities.bolt(mob, a), Abilities.effect(a));
            case FRENZY -> {
                mob.startFrenzy(200);
                level.sendParticles(ParticleTypes.ANGRY_VILLAGER, mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(), 6, 0.5, 0.3, 0.5, 0);
                mob.playLevelSound(net.minecraft.sounds.SoundEvents.RAVAGER_ROAR, 1.2F, 0.8F);
            }
        }
    }

    private void tickCharge(ServerLevel level) {
        chargeTicks--;
        double speed = mob.def().flies() ? 1.0 : 0.85;
        Vec3 v = mob.getDeltaMovement();
        mob.setDeltaMovement(chargeDir.x * speed, mob.def().flies() ? chargeDir.y * speed : v.y, chargeDir.z * speed);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(0.6), e -> Abilities.isEnemy(mob, e))) {
            if (chargeHits.contains(e.getId())) continue;
            chargeHits.add(e.getId());
            Ability ca = chargeAbility;
            Strikes.deliver(mob, ca != null ? ca.strike() : Strikes.Kind.HEAVY, () -> {
                Abilities.hurt(level, mob, e, chargeDamage, ca != null ? Abilities.bolt(mob, ca) : mob.def().theme.bolt,
                        ca != null ? Abilities.effect(ca) : null);
                return true;
            });
            Abilities.push(e, chargeDir, 1.1, 0.4);
        }
        if (mob.horizontalCollision && !mob.def().flies()) chargeTicks = 0;
        if (chargeTicks <= 0) {
            mob.setFlag(AwakenMob.F_CHARGING, false);
            mob.setDeltaMovement(mob.getDeltaMovement().scale(0.2));
        }
    }

    private void tickLeap(ServerLevel level) {
        leapTicks--;
        boolean landed = (mob.onGround() && leapTicks < 36) || leapTicks <= 0;
        if (!landed) return;
        leapTicks = 0;
        Ability a = leapAbility;
        if (a == null) return;
        Strikes.deliver(mob, a.strike(), () -> {
            Abilities.area(level, mob, a, mob.position(), a.radius(), 0, 0.7);
            return true;
        });
        Abilities.slamFx(level, mob, mob.position(), a.radius());
    }
}
