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
 * (for rooted abilities), follow the target until the lock, then show where it will land (SPEC §47.2: a locked line,
 * wedge or landing ring, at least {@link Carriers#LOCK} ticks before it can land). Execute once, through its carrier.
 * Charges and leaps finish over the next few ticks. Bosses swap the ability list when their phase changes.
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
    /** A kit move's aim stopped following (its lock tick passed): the beam's line shows bright from then on. */
    private boolean locked;
    private final List<Vec3> marks = new ArrayList<>();

    private int chargeTicks;
    private Vec3 chargeDir = Vec3.ZERO;
    private float chargeDamage;
    private @Nullable Ability chargeAbility;
    private final List<Integer> chargeHits = new ArrayList<>(4);
    private int leapTicks;
    private @Nullable Ability leapAbility;
    /** A leap's landing ring (shown from its lock until it lands) and where it was locked. */
    private Carriers.@Nullable Carrier leapRing;
    private Vec3 leapAt = Vec3.ZERO;
    /** The charge's effect, decided as it set off (a boss's only on its signature). */
    private net.minecraft.world.effect.@Nullable MobEffectInstance chargeEffect;
    /** A step behind you (not a kit body's) ends in a told cut this many ticks after it lands. */
    private int blinkCut;
    private @Nullable Ability blinkAbility;
    /** The class the ability lands as (its own, or the kit move's) and whether it may carry an effect now (I6). */
    private Strikes.Kind cls = Strikes.Kind.NONE;
    private boolean carries = true;

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
        blinkCut = 0;
        Carriers.end(leapRing);
        leapRing = null;
        mob.setTelegraph(0, false);
        mob.setFlag(AwakenMob.F_CHARGING, false);
    }

    public @Nullable Ability active() {
        return active;
    }

    boolean holdsStill() {
        return (active != null && active.roots()) || chargeTicks > 0 || leapTicks > 0 || blinkCut > 0;
    }

    /** Whether it has stepped and its cut is still to come (told, {@link #BLINK_CUT} ticks after the step). */
    boolean cutPending() {
        return blinkCut > 0;
    }

    /** A kit boss's runner (SPEC §35.3): it starts nothing itself; its FightRunner fires abilities as moves strike. */
    private boolean scripted;

    void scripted(boolean on) {
        scripted = on;
    }

    void tick(ServerLevel level) {
        if (chargeTicks > 0) tickCharge(level);
        if (leapTicks > 0) tickLeap(level);
        if (blinkCut > 0) tickBlinkCut(level);
        if (scripted) return;
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
                || mob.flag(AwakenMob.F_DECOY) || mob.isWatchedNow() || mob.fleeTicks > 0 || chargeTicks > 0 || leapTicks > 0 || blinkCut > 0) return;
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
            // anything aimed at you, or that takes it to you, needs to see you (SPEC §47.11)
            case BOLT, ARROWS, BEAM, CHARGE, CLOUD, RAIN, ROOT, FANGS, LIFT, PULL, BLINK -> mob.getSensing().hasLineOfSight(t);
            case SLAM -> d <= a.radius() + 0.5;
            case CONE -> d <= a.radius() && mob.getSensing().hasLineOfSight(t);
            case FANGS_RING, GUARD -> d <= 5;
            case SUMMON -> Mobs.minionRoom(level, mob, a.count());
            case DECOY -> level.getEntitiesOfClass(AwakenMob.class, mob.getBoundingBox().inflate(24), AwakenMob::isDecoy).size() < 3;
            case HEAL -> Abilities.alliesHurt(level, mob, a.radius());
            case LEAP -> mob.onGround() && d > 1.5 && mob.getSensing().hasLineOfSight(t);
            default -> true;
        };
    }

    private void begin(ServerLevel level, int index, Ability a, LivingEntity t) {
        active = a;
        activeIndex = index;
        total = left = a.windup() + (a.heavy() ? 8 : 0);
        aimTarget = t;
        aim = t.position();
        locked = false;
        cls = a.strike();
        // a lesser creature's ability carries its effect; a boss's (one not playing its kit) never does (I6)
        carries = !mob.def().tier.isBoss();
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
        boolean tracking = left > Math.max(6, Carriers.lead(a)) && t != null && t.isAlive();
        if (tracking) follow(a, t);
        else if (!locked) {
            locked = true;
            lock(level, a, left);
        }
        if (t != null && tracking) mob.getLookControl().setLookAt(t, 30.0F, 30.0F);
        if (a.roots()) mob.getNavigation().stop();
        if (!mob.abilityTell(level, a, left, total, aim, marks) && left % 3 == 0) show(level, a);
        if ((left & 3) == 0) mob.setTell(left, a.strike(), a.heavy());
        if (--left > 0) return;
        active = null;
        mob.setTelegraph(0, false);
        cooldowns[activeIndex] = a.cooldown() + mob.getRandom().nextInt(20);
        global = mob.def().tier.isBoss() ? 14 : 28;
        if (blind(level, a, t)) return;
        boolean was = Abilities.carrying;
        Abilities.carrying = carries;
        try {
            Strikes.deliver(mob, a.strike(), () -> {
                if (!mob.abilityStrike(level, a, t, aim, marks)) execute(level, a, t);
                return true;
            });
        } finally {
            Abilities.carrying = was;
        }
    }

    /** The aim follows the target (before the lock). */
    private void follow(Ability a, LivingEntity t) {
        switch (a.kind()) {
            case BEAM, BOLT, CHARGE -> aim = Abilities.chest(t);
            case ARROWS, CONE, FANGS, LEAP, ROOT, CLOUD -> aim = t.position();
            default -> { }
        }
    }

    /**
     * The aim stops following (SPEC §47.2, I3): what it locked on shows now, {@code left} ticks before it fires, in its
     * class's colour with the lock's flash and sound (the clients draw it): a beam's or charge's line, a cone's wedge, a
     * fang or root line along the ground, a leap's landing ring.
     */
    private void lock(ServerLevel level, Ability a, int left) {
        Vec3 feet = mob.position();
        switch (a.kind()) {
            case BEAM -> {
                Vec3 from = mob.getEyePosition();
                Carriers.line(level, mob, cls, from, Abilities.beamEnd(level, mob, from, aim, a.range()), left, a.heavy() ? 0.45F : 0.3F);
            }
            case CHARGE -> {
                Vec3 d = new Vec3(aim.x - feet.x, 0, aim.z - feet.z);
                if (d.lengthSqr() < 1.0E-4) d = mob.getLookAngle();
                d = new Vec3(d.x, 0, d.z).normalize();
                double reach = scripted ? Math.sqrt(feet.distanceToSqr(aim.x, feet.y, aim.z)) + CHARGE_PAST : CHARGE_TICKS * (mob.def().flies() ? 1.0 : 0.85);
                Carriers.line(level, mob, cls, feet.add(0, 0.1, 0), feet.add(d.scale(reach)).add(0, 0.1, 0), left, mob.getBbWidth() * 0.5F + 0.4F);
            }
            case FANGS -> {
                Vec3 d = Abilities.coneDir(mob, aim);
                Carriers.line(level, mob, cls, feet.add(0, 0.1, 0), feet.add(d.scale(1.25 * (Math.max(1, a.count()) + 1))).add(0, 0.1, 0), left, 0.6F);
            }
            case ROOT -> {
                Vec3 d = Abilities.coneDir(mob, aim);
                double length = Math.min(a.range() + 2, Math.sqrt(feet.distanceToSqr(aim.x, feet.y, aim.z)) + a.radius() + 1);
                Carriers.line(level, mob, cls, feet.add(0, 0.1, 0), feet.add(d.scale(length)).add(0, 0.1, 0), left, Carriers.GROUND_WIDTH);
            }
            case CONE -> Carriers.wedge(level, mob, cls, feet, Abilities.coneDir(mob, aim), a.radius(), left);
            case LEAP -> {
                leapAt = aim;
                leapRing = Carriers.ring(level, mob, cls, aim, Math.min(Carriers.VOLLEY_RING + 0.3F, a.radius()), left + LEAP_TICKS);
            }
            default -> { }
        }
    }

    /** A charge runs this many ticks; a leap is in the air at most this many. */
    static final int CHARGE_TICKS = dev.awaken.mobs.boss.KitReach.CHARGE_TICKS, LEAP_TICKS = 40;

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

    // ---- kit moves (SPEC §35.3): a move's wind-up aims and marks the ability, its strike fires it ----------------------

    /** The named ability of any phase, or null. */
    @Nullable Ability find(String name) {
        for (MobDef.Phase p : mob.def().phases) for (Ability a : p.abilities()) if (a.name().equals(name)) return a;
        return null;
    }

    /** Whether a kit move's ability has room now: a summon its budget, a decoy its cap, a heal someone hurt (the rest: always). */
    boolean room(ServerLevel level, Ability a) {
        return switch (a.kind()) {
            case SUMMON -> Mobs.minionRoom(level, mob, a.count());
            case DECOY -> level.getEntitiesOfClass(AwakenMob.class, mob.getBoundingBox().inflate(24), AwakenMob::isDecoy).size() < 3;
            case HEAL -> Abilities.alliesHurt(level, mob, a.radius());
            default -> true;
        };
    }

    /**
     * A move's wind-up begins: aim at the target and place the ability's marks, as an ability's own wind-up does. It
     * lands as {@code kind} (the move's told class) and carries an effect only when {@code signature} (I6).
     */
    void prepare(ServerLevel level, Ability a, LivingEntity t, int windup, Strikes.Kind kind, boolean signature) {
        aimTarget = t;
        locked = false;
        cls = kind;
        carries = signature;
        aim = a.kind() == Ability.Kind.BEAM || a.kind() == Ability.Kind.BOLT || a.kind() == Ability.Kind.CHARGE ? Abilities.chest(t) : t.position();
        total = left = Math.max(1, windup);
        marks.clear();
        if (!mob.abilityMarks(level, a, t, marks)) {
            switch (a.kind()) {
                case RAIN -> rainMarks(level, a);
                case SUMMON -> marks.addAll(Abilities.summonSpots(level, mob, a.count()));
                case BLINK -> marks.add(Abilities.blinkSpot(mob, t));
                default -> { }
            }
        }
    }

    /** One wind-up tick of a move's ability: follow the target while it tracks, and show where it will land. */
    void windup(ServerLevel level, Ability a, int remaining, boolean tracking) {
        LivingEntity t = aimTarget;
        left = Math.max(1, remaining);
        if (tracking && t != null && t.isAlive()) follow(a, t);
        else if (!locked) {
            locked = true;
            lock(level, a, left);
        }
        if (!mob.abilityTell(level, a, left, total, aim, marks) && left % 3 == 0) show(level, a);
    }

    /**
     * The move strikes: the ability fires as {@code kind} (the move's own class, so it lands as it was told), its area
     * centred on {@code origin} (the fist, the palm) when it has one.
     */
    void fire(ServerLevel level, Ability a, @Nullable Vec3 origin, Strikes.Kind kind) {
        LivingEntity t = aimTarget;
        this.origin = origin;
        // a step lands behind where you stand now, not where you stood when it began to tell it
        if (a.kind() == Ability.Kind.BLINK && t != null && t.isAlive()) {
            marks.clear();
            marks.add(Abilities.blinkSpot(mob, t));
        }
        if (blind(level, a, t)) return;
        boolean was = Abilities.carrying;
        Abilities.carrying = carries;
        cls = kind;
        try {
            Strikes.deliver(mob, kind, () -> {
                if (!mob.abilityStrike(level, a, t, aim, marks)) execute(level, a, t);
                return true;
            });
        } finally {
            this.origin = null;
            Abilities.carrying = was;
        }
    }

    /**
     * A shot never leaves without a clear line to the one it was meant for (SPEC §47.11): ducked behind a wall or a pillar
     * through its wind-up, it lets go with a hiss and nothing flies. True when it held its shot.
     */
    private boolean blind(ServerLevel level, Ability a, @Nullable LivingEntity t) {
        boolean shot = switch (a.kind()) {
            case BOLT, ARROWS, BEAM, CLOUD, RAIN, CONE, ROOT, FANGS, LIFT, PULL -> true;
            default -> false;
        };
        if (!shot || t == null || !t.isAlive() || mob.getSensing().hasLineOfSight(t)) return false;
        Vec3 c = Abilities.chest(mob);
        if (!Abilities.shed(level, mob, c, 6, 0.25, 0.2, 0.25, 0.01)) level.sendParticles(ParticleTypes.SMOKE, c.x, c.y, c.z, 6, 0.25, 0.2, 0.25, 0.01);
        Abilities.borrowed(mob, net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, 0.5F, 1.6F);
        return true;
    }

    /** Where a kit move's area ability centres (null: the body's feet, as abilities always have). */
    private @Nullable Vec3 origin;

    private Vec3 centre() {
        return origin != null ? origin : mob.position();
    }

    private void show(ServerLevel level, Ability a) {
        int colour = mob.def().glow;
        boolean heavy = a.heavy();
        Vec3 feet = mob.position();
        switch (a.kind()) {
            case SLAM, AURA, PULL, GUST, DARKNESS, HEAL -> Telegraph.ring(level, feet, a.radius() * (1.0F - left / (float) total * 0.5F), 20, colour, heavy);
            case BURST -> {
                Telegraph.ring(level, feet, a.radius(), 36, colour, heavy);
                Telegraph.safeRing(level, feet, a.inner());
            }
            // what is thrown gathers in the body first; where it falls shows only as it is thrown (at the throw)
            case NOVA, FANGS_RING, GUARD, FRENZY, DECOY, RAIN, LIFT, CLOUD -> Telegraph.gather(level, Abilities.chest(mob), colour);
            case LEAP -> {
                if (!locked) Telegraph.gather(level, feet.add(0, 0.3, 0), colour);
            }
            case SUMMON -> {
                for (Vec3 m : marks) Telegraph.gather(level, m.add(0, 0.5, 0), colour);
            }
            case BLINK -> {
                if (!marks.isEmpty()) {
                    Vec3 m = marks.get(0);
                    if (!Abilities.shed(level, mob, m.add(0, 0.8, 0), 5, 0.3, 0.6, 0.3, 0.02)) {
                        level.sendParticles(ParticleTypes.PORTAL, m.x, m.y + 0.8, m.z, 8, 0.3, 0.6, 0.3, 0.2);
                    }
                }
            }
            // before the lock the aim shows as dust that follows you; at the lock the carrier's line takes over
            case CHARGE, FANGS, ROOT -> {
                if (!locked) Telegraph.line(level, feet.add(0, 0.2, 0), new Vec3(aim.x, feet.y + 0.2, aim.z), colour, false);
            }
            case BEAM -> {
                if (!locked) Telegraph.line(level, mob.getEyePosition(), aim, colour, false);
            }
            case CONE -> {
                if (!locked) Telegraph.cone(level, feet, aim.subtract(feet), a.radius(), colour);
            }
            case BOLT, ARROWS -> Telegraph.gather(level, mob.def().shot != null ? Abilities.muzzle(mob) : mob.getEyePosition(), colour);
        }
    }

    private void execute(ServerLevel level, Ability a, @Nullable LivingEntity t) {
        switch (a.kind()) {
            case SLAM -> {
                // the blow where it strikes, as its class; its reach beyond rolls out as a wave (jump it, dash through it)
                Vec3 c = centre();
                float core = Math.min(SLAM_CORE, a.radius());
                Abilities.area(level, mob, a, c, core, 0, 0.9);
                if (a.radius() > core) Carriers.wave(level, mob, ground(c), core, a.radius(), Abilities.damage(mob, a) * 0.8F, Abilities.bolt(mob, a),
                        Abilities.effect(mob, a), 0.6);
                Abilities.slamFx(level, mob, c, core);
            }
            case BURST -> {
                Carriers.wave(level, mob, ground(centre()), Math.max(1.0F, a.inner()), a.radius(), Abilities.damage(mob, a), Abilities.bolt(mob, a),
                        Abilities.effect(mob, a), 0.6);
                mob.playLevelSound(MobSounds.BOSS_TOLL.get(), 2.0F, 0.6F);
            }
            case LEAP -> {
                Vec3 d = aim.subtract(mob.position());
                double h = Math.sqrt(d.x * d.x + d.z * d.z);
                mob.setDeltaMovement(d.x / Math.max(1, h) * Math.min(1.4, h * 0.16), 0.55 + Math.min(0.4, h * 0.03), d.z / Math.max(1, h) * Math.min(1.4, h * 0.16));
                leapTicks = LEAP_TICKS;
                leapAbility = a;
                leapEffect = Abilities.effect(mob, a);
            }
            case CHARGE -> {
                // a kit charge runs at you and stops there (SPEC §47.15), at your height: a flier ends where it can be hit
                Vec3 d = scripted && t != null ? chargeRun(t) : aim.subtract(mob.position().add(0, mob.getBbHeight() * 0.5, 0));
                if (!mob.def().flies()) d = new Vec3(d.x, 0, d.z);
                chargeDir = d.lengthSqr() < 1.0E-4 ? mob.getLookAngle() : d.normalize();
                double speed = chargeSpeed();
                chargeTicks = scripted && t != null ? Math.max(4, Math.min(CHARGE_TICKS + 4, Mth.ceil(d.length() / speed))) : CHARGE_TICKS;
                chargeDamage = Abilities.damage(mob, a);
                chargeAbility = a;
                chargeEffect = Abilities.effect(mob, a);
                chargeCls = cls;
                chargeHits.clear();
                mob.setFlag(AwakenMob.F_CHARGING, true);
                Abilities.borrowed(mob, net.minecraft.sounds.SoundEvents.RAVAGER_ROAR, 0.6F, 1.4F);
            }
            case BOLT -> Abilities.bolts(level, mob, a, aim);
            case ARROWS -> {
                // a body with shots of its own looses them (told and locked as every shot); a borrowed body its arrows
                if (mob.def().shot != null) Abilities.volley(level, mob, a, aim.add(0, t != null ? t.getBbHeight() * 0.6 : 1.0, 0));
                else if (t != null) Abilities.arrows(level, mob, a, t);
            }
            case NOVA -> Abilities.nova(level, mob, a.count(), Abilities.bolt(mob, a), a.power(), Abilities.effect(mob, a));
            case RAIN -> {
                // thrown now, falling where each of you stands now (never following), and the rest scattered near
                marks.clear();
                rainMarks(level, a);
                Carriers.volley(level, mob, Abilities.chest(mob), marks, a.radius(), Abilities.damage(mob, a), Abilities.bolt(mob, a), Abilities.effect(mob, a));
                mob.playLevelSound(MobSounds.VOLLEY_THROW.get(), 1.3F, 1.0F);
            }
            case BEAM -> Abilities.beam(level, mob, a, aim);
            // a kit body's step only moves it: the move's own cut is the blow (else it hit twice, the first untold);
            // a lesser body's step ends in a told cut a beat after it lands, never at once (SPEC §47.2)
            case BLINK -> {
                if (!marks.isEmpty()) Abilities.blink(level, mob, a, t, marks.get(0), false);
                if (!mob.scriptedFight() && t != null) {
                    blinkCut = BLINK_CUT;
                    blinkAbility = a;
                    blinkEffect = Abilities.effect(mob, a);
                    mob.setTell(BLINK_CUT, Strikes.Kind.HEAVY, true);
                    mob.playTell(Strikes.Kind.HEAVY, true, 1.1F);
                }
            }
            case SUMMON -> Abilities.summon(level, mob, a, marks);
            // a lob: a globe thrown to where you stood at its lock, a ring where it will fall; a small field where it bursts
            case CLOUD -> {
                Carriers.lob(level, mob, Abilities.chest(mob), aim, Abilities.damage(mob, a) * 0.6F, Abilities.bolt(mob, a), Abilities.effect(mob, a),
                        a.radius(), 100, Abilities.damage(mob, a) * 0.2F);
                mob.playLevelSound(MobSounds.VOLLEY_THROW.get(), 1.0F, 1.2F);
            }
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
                if (!Abilities.shed(level, mob, mob.position().add(0, mob.getBbHeight() * 0.6, 0), 12, 0.5, 0.6, 0.5, 0.1)) {
                    level.sendParticles(ParticleTypes.ENCHANTED_HIT, mob.getX(), mob.getY() + 1, mob.getZ(), 12, 0.5, 0.6, 0.5, 0.1);
                }
            }
            case LIFT -> Abilities.lift(level, mob, a, t != null && t.isAlive() ? t.position() : aim);
            case DARKNESS -> Abilities.darkness(level, mob, a);
            case AURA -> mob.startAura(Math.max(2, a.count()), a.radius(), Abilities.damage(mob, a), Abilities.bolt(mob, a), Abilities.effect(mob, a));
            case FRENZY -> {
                mob.startFrenzy(200);
                level.sendParticles(dev.awaken.mobs.MobParticles.shot(dev.awaken.mobs.MobParticles.Shot.SPARK, 0xFF4A3A), mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(), 6, 0.5, 0.3, 0.5, 0);
                Abilities.borrowed(mob, net.minecraft.sounds.SoundEvents.RAVAGER_ROAR, 1.2F, 0.8F);
            }
        }
    }

    /** A slam strikes this far as its blow; past it, its reach is a wave. */
    static final float SLAM_CORE = dev.awaken.mobs.boss.KitReach.SLAM_CORE;
    /** A step's cut lands this long after it arrives: amber, a beat to turn and parry. */
    static final int BLINK_CUT = 8;
    private net.minecraft.world.effect.@Nullable MobEffectInstance leapEffect, blinkEffect;
    private Strikes.Kind chargeCls = Strikes.Kind.HEAVY;

    /** The ground under a point at the body's feet (a fist's origin is in the air; a wave rolls along the floor). */
    private Vec3 ground(Vec3 c) {
        return new Vec3(c.x, mob.getY(), c.z);
    }

    private void tickBlinkCut(ServerLevel level) {
        LivingEntity t = aimTarget;
        if (t != null && t.isAlive()) mob.getLookControl().setLookAt(t, 30.0F, 30.0F);
        if (--blinkCut > 0) return;
        mob.setTelegraph(0, false);
        Ability a = blinkAbility;
        if (a == null || t == null || !t.isAlive()) return;
        double reach = mob.getBbWidth() + t.getBbWidth() + 1.5;
        mob.swingForAttack(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (mob.distanceToSqr(t) > reach * reach) return;
        var fx = blinkEffect;
        Strikes.deliver(mob, Strikes.Kind.HEAVY, () -> Abilities.hurt(level, mob, t, Abilities.damage(mob, a), Abilities.bolt(mob, a), fx));
    }

    /** A kit charge's whole run: from where the body is to just past the target, ending level with its feet. */
    private Vec3 chargeRun(LivingEntity t) {
        Vec3 flat = new Vec3(t.getX() - mob.getX(), 0, t.getZ() - mob.getZ());
        double len = flat.length();
        Vec3 ahead = len < 1.0E-3 ? Vec3.ZERO : flat.scale((len + CHARGE_PAST) / len);
        return new Vec3(ahead.x, mob.def().flies() ? t.getY() - mob.getY() : 0, ahead.z);
    }

    /** Blocks past you a charge carries before it's spent. */
    static final double CHARGE_PAST = dev.awaken.mobs.boss.KitReach.CHARGE_PAST;

    /** What's left of a charge's speed on the tick it's spent. */
    static final double CHARGE_BRAKE = 0.2;

    private double chargeSpeed() {
        return dev.awaken.mobs.boss.KitReach.chargeSpeed(mob.def().flies()); // one rule with the ring a client draws (SPEC §67.2)
    }

    /** How far past where it is a charge sends the body this tick, or null (SPEC §48.8). Its last tick is only {@link #CHARGE_BRAKE} of a stride. */
    @Nullable Vec3 lead() {
        if (chargeTicks <= 0) return null;
        double by = chargeSpeed() * (Math.min(FightRunner.LEAD, chargeTicks - 1) + (chargeTicks <= FightRunner.LEAD ? CHARGE_BRAKE : 0));
        return new Vec3(chargeDir.x * by, mob.def().flies() ? chargeDir.y * by : 0, chargeDir.z * by);
    }

    private void tickCharge(ServerLevel level) {
        chargeTicks--;
        double speed = chargeSpeed();
        Vec3 v = mob.getDeltaMovement();
        mob.setDeltaMovement(chargeDir.x * speed, mob.def().flies() ? chargeDir.y * speed : v.y, chargeDir.z * speed);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(dev.awaken.mobs.boss.KitReach.CHARGE_GRAZE), e -> Abilities.isEnemy(mob, e))) {
            if (chargeHits.contains(e.getId())) continue;
            chargeHits.add(e.getId());
            Ability ca = chargeAbility;
            var fx = chargeEffect;
            boolean landed = Strikes.deliver(mob, chargeCls, () -> Abilities.hurt(level, mob, e, chargeDamage,
                    ca != null ? Abilities.bolt(mob, ca) : mob.def().theme.bolt, fx));
            Vec3 way = chargeDir;
            Runnable push = () -> Abilities.push(e, way, 1.1, 0.4);
            if (landed || dev.awaken.arms.Arms.guarded(e)) push.run();
            else dev.awaken.arms.Arms.afterHeld(e, push);
        }
        if (mob.horizontalCollision && !mob.def().flies()) chargeTicks = 0;
        if (chargeTicks <= 0) {
            mob.setFlag(AwakenMob.F_CHARGING, false);
            mob.setDeltaMovement(mob.getDeltaMovement().scale(CHARGE_BRAKE));
        }
    }

    private void tickLeap(ServerLevel level) {
        leapTicks--;
        boolean landed = (mob.onGround() && leapTicks < 36) || leapTicks <= 0;
        if (!landed) return;
        leapTicks = 0;
        Carriers.end(leapRing);
        leapRing = null;
        Ability a = leapAbility;
        if (a == null) return;
        // it lands on the ring it showed (near enough; a wall in the way lands it where it stands)
        Vec3 at = mob.position().distanceToSqr(leapAt.x, mob.getY(), leapAt.z) < 2.5 * 2.5 ? new Vec3(leapAt.x, mob.getY(), leapAt.z) : mob.position();
        float r = Math.min(Carriers.VOLLEY_RING + 0.3F, a.radius());
        boolean was = Abilities.carrying;
        Abilities.carrying = leapEffect != null;
        try {
            Strikes.deliver(mob, cls != Strikes.Kind.NONE ? cls : a.strike(), () -> {
                Abilities.area(level, mob, a, at, r, 0, 0.7);
                return true;
            });
        } finally {
            Abilities.carrying = was;
        }
        Abilities.slamFx(level, mob, at, r);
    }
}
