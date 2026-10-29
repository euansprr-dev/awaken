package dev.awaken.mobs.entity;

import dev.awaken.core.api.BossPlates;
import dev.awaken.core.api.Stances;
import dev.awaken.core.api.Strikes;
import dev.awaken.mobs.boss.FightScripts;
import dev.awaken.mobs.boss.KitMoves;
import dev.awaken.mobs.boss.Kits;
import dev.awaken.mobs.def.Ability;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.List;

/**
 * A kit boss's fight (SPEC §35.3): it plays its fight script's moves, each a wind-up (turning toward you until its lock
 * tick, sometimes held at the peak), active ticks whose capsules hit only where the limb is, and a recovery you can
 * punish. Strings carry on without a gap; phases enter through their transition move; under the enrage line it moves
 * faster, never harder. Every blow goes through {@code Strikes.deliver}, so Arms' deflect, block and posture apply.
 * Allocation-free per tick (the string deque and the hit list are reused).
 */
final class FightRunner {
    private final AwakenBoss boss;
    private final Kits.Kit kit;
    private final KitMoves.Table moves;
    private final FightScripts.Fight fight;

    /** The move playing (an index into the table, -1 none), its tick, and its stretched timings. */
    private int move = -1, t, windup, hold, active, recover;
    private KitMoves.@Nullable Played played;
    private float yaw;
    private @Nullable Ability ability;
    private final ArrayDeque<String> string = new ArrayDeque<>(4);
    /** The studio's queue (/awaken bosses studio): every move once, in table order, a breath apart; then it fights. */
    private final ArrayDeque<String> studio = new ArrayDeque<>();
    private final int[] hit = new int[16];
    private int hits, gap = 20, navTicks;
    private boolean introduced;
    /** A phase waiting for its transition (the threshold was crossed mid-move), or -1. */
    private int pendingPhase = -1;

    FightRunner(AwakenBoss boss, Kits.Kit kit) {
        this.boss = boss;
        this.kit = kit;
        this.moves = kit.moves();
        this.fight = java.util.Objects.requireNonNull(kit.fight());
    }

    Kits.Kit kit() {
        return kit;
    }

    boolean busy() {
        return move >= 0;
    }

    /** The intro pose (until the fight starts), or none. */
    boolean waiting() {
        return !introduced;
    }

    /** Everyone left: back to the intro, nothing playing. */
    void reset() {
        stop();
        studio.clear();
        introduced = false;
        pendingPhase = -1;
        gap = 20;
        boss.syncKit(-1, 0, AwakenBoss.KIT_POSE_INTRO);
    }

    /** Plays one move now, whatever the pool would pick (tests, the studio). False if the table has no such move. */
    boolean play(ServerLevel level, String id) {
        stop();
        string.clear();
        introduced = true;
        return begin(level, id, boss.getTarget());
    }

    /** Plays a string now: its first move, then the rest as the fight script would (tests). */
    boolean play(ServerLevel level, String first, java.util.List<String> then) {
        if (!play(level, first)) return false;
        string.addAll(then);
        return true;
    }

    /** The move playing now, or null. */
    @Nullable String playing() {
        return move >= 0 ? moves.get(move).id() : null;
    }

    /** The studio: every move in turn (with no target needed), then the fight as usual. */
    void studio() {
        stop();
        string.clear();
        studio.clear();
        for (KitMoves.Move m : moves.moves()) studio.add(m.id());
        introduced = true;
        gap = 30;
    }

    /** Its stance broke or it was staggered: the move in progress ends there, and so does its string (SPEC §24). */
    void interrupt() {
        if (move < 0) return;
        stop();
        string.clear();
        gap = 6 + boss.getRandom().nextInt(9);
    }

    /** A phase threshold was crossed: its transition plays as soon as the current move ends. */
    void enterPhase(int phase) {
        pendingPhase = phase;
    }

    void tick(ServerLevel level) {
        if (move >= 0) {
            step(level);
            return;
        }
        LivingEntity target = boss.getTarget();
        if (!studio.isEmpty()) {
            if (gap > 0) {
                gap--;
                return;
            }
            begin(level, studio.poll(), target);
            return;
        }
        if (!introduced) {
            boss.syncKit(-1, 0, AwakenBoss.KIT_POSE_INTRO);
            if (target == null || !target.isAlive()) return;
            introduced = true;
            boss.syncKit(-1, 0, 0);
            if (fight.intro().move().isPresent() && begin(level, fight.intro().move().get(), target)) return;
        }
        if (pendingPhase >= 0) {
            int p = pendingPhase;
            pendingPhase = -1;
            String tr = fight.phase(p).transition().orElse(null);
            string.clear();
            if (tr != null && begin(level, tr, target)) {
                boss.shield(windup + active + recover);
                return;
            }
        }
        // a broken stance or a stagger is the opening it earned: it starts nothing (and kneels while broken)
        boolean broken = Stances.broken(boss);
        boss.syncKit(-1, 0, broken ? AwakenBoss.KIT_POSE_BROKEN : 0);
        if (broken || boss.staggered() || boss.flag(AwakenMob.F_FALLEN) || boss.flag(AwakenMob.F_DORMANT)) {
            string.clear();
            return;
        }
        if (target == null || !target.isAlive()) return;
        boss.getLookControl().setLookAt(target, 30, 30);
        if (!string.isEmpty()) {
            String next = string.poll();
            int i = moves.index(next);
            if (i >= 0 && inRange(moves.get(i), target) && begin(level, next, target)) return;
            string.clear();
        }
        if (gap > 0) {
            gap--;
            approach(target);
            return;
        }
        FightScripts.Entry e = pick(target);
        if (e == null || !begin(level, e.move(), target)) {
            approach(target);
            return;
        }
        if (!e.then().isEmpty()) {
            List<String> s = e.then().get(boss.getRandom().nextInt(e.then().size()));
            string.addAll(s);
        }
    }

    /** Walks toward the target when nothing in the pool reaches it (fliers keep their own flight goal). */
    private void approach(LivingEntity target) {
        if (boss.def().flies()) return;
        if (navTicks-- > 0) return;
        navTicks = 10;
        // it stops where its close moves reach (at most 3.5 blocks out): Unit Zero's cuts reach 1.6, not 3.5
        double reach = closeReach();
        double stop = reach > 0 ? Math.min(3.5, Math.max(0.5, reach - 0.3)) : 3.5;
        if (edgeDistance(target) > stop) boss.getNavigation().moveTo(target, 1.0);
        else boss.getNavigation().stop();
    }

    /** How far its close moves reach in this phase (those usable from 0, not kept for far or behind), or 0. */
    private double closeReach() {
        double reach = 0;
        for (FightScripts.Entry e : fight.phase(boss.phase()).pool()) {
            int i = moves.index(e.move());
            if (i < 0 || moves.get(i).minRange() > 0 || e.when() == FightScripts.When.FAR || e.when() == FightScripts.When.BEHIND) continue;
            reach = Math.max(reach, moves.get(i).maxRange());
        }
        return reach;
    }

    private FightScripts.@Nullable Entry pick(LivingEntity target) {
        List<FightScripts.Entry> pool = fight.phase(boss.phase()).pool();
        int sum = 0;
        for (FightScripts.Entry e : pool) if (usable(e, target)) sum += Math.max(1, e.weight());
        if (sum <= 0) return null;
        int r = boss.getRandom().nextInt(sum);
        for (FightScripts.Entry e : pool) {
            if (!usable(e, target)) continue;
            r -= Math.max(1, e.weight());
            if (r < 0) return e;
        }
        return null;
    }

    private boolean usable(FightScripts.Entry e, LivingEntity target) {
        int i = moves.index(e.move());
        if (i < 0) return false;
        KitMoves.Move m = moves.get(i);
        if (!inRange(m, target)) return false;
        // a summon with its colony already out is no move at all: it walks in instead
        if (m.ability().isPresent()) {
            Ability a = boss.ability(m.ability().get());
            if (a != null && boss.level() instanceof ServerLevel sl && !boss.abilityRunner().room(sl, a)) return false;
        }
        return switch (e.when()) {
            case ANY -> true;
            case NEAR -> edgeDistance(target) <= 4;
            case FAR -> edgeDistance(target) >= 8;
            case BEHIND -> behind(target);
            case AIR -> !target.onGround();
        };
    }

    private boolean inRange(KitMoves.Move m, LivingEntity target) {
        double d = edgeDistance(target);
        return d >= m.minRange() && d <= m.maxRange();
    }

    private double edgeDistance(LivingEntity target) {
        return Math.max(0, Math.sqrt(boss.distanceToSqr(target.getX(), boss.getY(), target.getZ())) - target.getBbWidth() * 0.5 - boss.getBbWidth() * 0.5);
    }

    /** The target stands in the body's back half (more than 110° from where it faces). */
    private boolean behind(LivingEntity target) {
        float to = (float) (Mth.atan2(target.getZ() - boss.getZ(), target.getX() - boss.getX()) * Mth.RAD_TO_DEG) - 90;
        return Math.abs(Mth.wrapDegrees(to - boss.yBodyRot)) > 110;
    }

    /**
     * Holds the movement goals off a kit boss while it has a target: its fight script walks it in and turns it, and a
     * random stroll must not steal its navigation between moves.
     */
    static final class Engaged extends net.minecraft.world.entity.ai.goal.Goal {
        private final AwakenMob mob;

        Engaged(AwakenMob mob) {
            this.mob = mob;
            setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }
    }

    // ---- one move ---------------------------------------------------------------------------------------------------

    private boolean begin(ServerLevel level, String id, @Nullable LivingEntity target) {
        int i = moves.index(id);
        if (i < 0) return false;
        KitMoves.Move m = moves.get(i);
        // the enrage is speed, never damage: the wind-up and recovery shrink, but never under its class's tell
        int held = m.kind() == Strikes.Kind.HEAVY && m.time().hold() > 0 && boss.getRandom().nextInt(3) == 0 ? m.time().hold() : 0;
        KitMoves.Played p = KitMoves.played(m, boss.kitSpeed(), held);
        played = p;
        windup = p.windup();
        hold = p.hold();
        active = p.active();
        recover = p.recover();
        move = i;
        t = 0;
        hits = 0;
        yaw = boss.yBodyRot;
        // an ability needs someone to aim at (the studio plays its moves with nobody there: the body only)
        ability = target != null ? m.ability().map(boss::ability).orElse(null) : null;
        if (ability != null) boss.abilityRunner().prepare(level, ability, target, windup + hold);
        boss.getNavigation().stop();
        boss.syncKit(i, hold, 0);
        if (m.kind() != Strikes.Kind.NONE) {
            boss.setTell(windup + hold, m.kind(), m.kind() == Strikes.Kind.HEAVY);
            // the body's own voice for the class when it has one (the glow still tells the class), else Arms' one
            if (!sound(m, "tell", "tell_" + m.kind().name().toLowerCase(java.util.Locale.ROOT)))
                boss.playTell(m.kind(), m.kind() == Strikes.Kind.HEAVY, 1.0F);
        }
        if (m.signature() && !m.title().isEmpty() && boss.bar() != null) BossPlates.line(boss.bar(), m.title(), BossPlates.Line.SIGNATURE, boss.def().glow);
        return true;
    }

    private void step(ServerLevel level) {
        KitMoves.Move m = moves.get(move);
        LivingEntity target = boss.getTarget();
        t++;
        int wind = windup + hold;
        if (t <= wind) {
            // the wind-up: it turns toward you until it locks, then commits
            boolean tracking = t <= m.time().lock() && target != null && target.isAlive();
            if (tracking && m.time().track() > 0) {
                float want = (float) (Mth.atan2(target.getZ() - boss.getZ(), target.getX() - boss.getX()) * Mth.RAD_TO_DEG) - 90;
                yaw = Mth.approachDegrees(yaw, want, m.time().track());
            }
            face();
            boss.getNavigation().stop();
            if (ability != null) boss.abilityRunner().windup(level, ability, wind - t + 1, tracking);
            if (m.kind() != Strikes.Kind.NONE && (t & 3) == 0) boss.setTell(wind - t, m.kind(), m.kind() == Strikes.Kind.HEAVY);
            return;
        }
        int k = t - wind - 1;
        if (k < active) {
            if (k == 0) {
                boss.setTelegraph(0, false);
                if (ability != null) {
                    boss.abilityRunner().fire(level, ability, world(m.origin()), m.kind() != Strikes.Kind.NONE ? m.kind() : ability.strike());
                    // a step that didn't close the gap (a wall where it would land) doesn't cut the air far from you:
                    // it ends there, string and all
                    if (ability.kind() == Ability.Kind.BLINK && !m.hits().isEmpty()) {
                        if (target == null || !target.isAlive() || edgeDistance(target) > closeReach() + 1) {
                            stop();
                            string.clear();
                            gap = 10;
                            return;
                        }
                        // it lands behind you facing you, so the cut meets you and not the air where it came from
                        yaw = (float) (Mth.atan2(target.getZ() - boss.getZ(), target.getX() - boss.getX()) * Mth.RAD_TO_DEG) - 90;
                    }
                }
                // a roar for an intro or transition, a cast for an ability alone (a blow's whoosh plays on its mark)
                if (m.kind() == Strikes.Kind.NONE) {
                    if (!sound(m, "swing", "roar")) boss.roar();
                } else if (m.hits().isEmpty()) sound(m, "swing", "cast");
                if (m.is("area") && firstHit(m) < 0) sound(m, "impact", "impact");
            }
            face();
            float adv = m.motion().advance() / active, up = m.motion().rise() / active;
            if (adv != 0 || up != 0) {
                double r = yaw * Mth.DEG_TO_RAD;
                boss.move(MoverType.SELF, new Vec3(-Mth.sin((float) r) * adv, up, Mth.cos((float) r) * adv));
            }
            if (k < m.hits().size()) strike(level, m, m.hits().get(k));
            marks(m);
            return;
        }
        marks(m);
        if (t < wind + active + recover) {
            // the recovery: no turning, the opening; in a string the next move links on at the end of the
            // follow-through instead of the body going back to rest first (SPEC §35.10)
            if (played != null && played.link() < recover && t == wind + active + played.link()) link(level, target);
            return;
        }
        stop();
        gap = !studio.isEmpty() ? 30 : string.isEmpty() ? 6 + boss.getRandom().nextInt(9) : 0;
    }

    /**
     * The sounds on the move's marks, each at the tick its base-timed mark plays at: the blow's whoosh, its impact (an
     * area move meeting the ground) and the steps its feet take where it doesn't travel (travel steps are the walk's).
     */
    private void marks(KitMoves.Move m) {
        if (played == null || m.kind() == Strikes.Kind.NONE) return;
        KitMoves.Time base = m.time();
        if (!m.hits().isEmpty()) {
            int whoosh = Math.max(1, Math.round(played.at(base, m.mark("whoosh", base.windup())))) + 1;
            if (t == whoosh) sound(m, "swing", m.kind() == Strikes.Kind.LIGHT ? "swing_light" : "swing_heavy");
            if (m.is("area") && firstHit(m) >= 0 && t == Math.round(played.at(base, m.mark("contact", base.windup() + firstHit(m)))) + 1)
                sound(m, "impact", "impact"); // the blow meets the ground
        }
        if (m.motion().advance() != 0) return;
        for (int i = 0; i < 8; i++) {
            int st = m.mark("step" + i, -1);
            if (st < 0) break;
            if (t == Math.round(played.at(base, st)) + 1) dev.awaken.mobs.boss.KitSounds.play(boss, moves, "step", 0.8F);
        }
    }

    /** A string's next step begins now if it is due and in range; false leaves the recovery to play out. */
    private boolean link(ServerLevel level, @Nullable LivingEntity target) {
        if (string.isEmpty() || pendingPhase >= 0 || !studio.isEmpty() || target == null || !target.isAlive()) return false;
        int i = moves.index(string.peek());
        if (i < 0 || !inRange(moves.get(i), target)) return false;
        String next = string.poll();
        stop();
        return begin(level, next, target);
    }

    /** The move's own set for this moment ("" silences it), else the default; false when the body has neither. */
    private boolean sound(KitMoves.Move m, String moment, String fallback) {
        String set = m.sounds().getOrDefault(moment, fallback);
        return set.isEmpty() || dev.awaken.mobs.boss.KitSounds.play(boss, moves, set, 1F);
    }

    /** The first active tick with a capsule, or -1. */
    private static int firstHit(KitMoves.Move m) {
        for (int i = 0; i < m.hits().size(); i++) if (!m.hits().get(i).isEmpty()) return i;
        return -1;
    }

    private void stop() {
        if (move >= 0) boss.setTelegraph(0, false);
        move = -1;
        played = null;
        ability = null;
        boss.syncKit(-1, 0, 0);
    }

    private void face() {
        boss.setYRot(yaw);
        boss.yBodyRot = yaw;
        boss.yHeadRot = yaw;
    }

    /** A point in the body's space (blocks: +x left, +y up, +z forward) in the world, at the locked facing. */
    private Vec3 world(List<Float> p) {
        if (p.size() < 3) return boss.position();
        return world(p.get(0), p.get(1), p.get(2));
    }

    private Vec3 world(float x, float y, float z) {
        float r = yaw * Mth.DEG_TO_RAD, c = Mth.cos(r), s = Mth.sin(r);
        // facing yaw: forward is (-sin, cos); the body's left is (cos, sin)
        return new Vec3(boss.getX() + x * c - z * s, boss.getY() + y, boss.getZ() + x * s + z * c);
    }

    /** This tick's capsules against every enemy the boss can see; each is hit once a move. */
    private void strike(ServerLevel level, KitMoves.Move m, List<KitMoves.Capsule> caps) {
        if (caps.isEmpty()) return;
        AABB reach = boss.getBoundingBox().inflate(m.maxRange() + 3);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, reach, e -> Abilities.isEnemy(boss, e))) {
            if (wasHit(e.getId()) || !boss.getSensing().hasLineOfSight(e)) continue;
            AABB box = e.getBoundingBox();
            boolean touched = false;
            for (KitMoves.Capsule c : caps) {
                if (touches(world(c.ax(), c.ay(), c.az()), world(c.bx(), c.by(), c.bz()), c.r(), box)) {
                    touched = true;
                    break;
                }
            }
            if (!touched) continue;
            if (hits < hit.length) hit[hits++] = e.getId();
            float dmg = (float) boss.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * m.damage();
            Strikes.Kind kind = m.kind() == Strikes.Kind.NONE ? Strikes.Kind.HEAVY : m.kind();
            boolean landed = Strikes.deliver(boss, kind, () -> e.hurtServer(level, level.damageSources().mobAttack(boss), dmg));
            if (landed && m.knock() > 0) {
                Vec3 away = e.position().subtract(boss.position());
                Abilities.push(e, away, m.knock(), Math.min(0.5, m.knock() * 0.4));
            }
        }
    }

    private boolean wasHit(int id) {
        for (int i = 0; i < hits; i++) if (hit[i] == id) return true;
        return false;
    }

    /** Whether the capsule from a to b of radius r touches the box (the segment sampled every half block). */
    static boolean touches(Vec3 a, Vec3 b, float r, AABB box) {
        double len = a.distanceTo(b);
        int n = Math.max(1, (int) Math.ceil(len / 0.5));
        for (int i = 0; i <= n; i++) {
            double f = i / (double) n;
            double x = a.x + (b.x - a.x) * f, y = a.y + (b.y - a.y) * f, z = a.z + (b.z - a.z) * f;
            double dx = Math.max(Math.max(box.minX - x, 0), x - box.maxX);
            double dy = Math.max(Math.max(box.minY - y, 0), y - box.maxY);
            double dz = Math.max(Math.max(box.minZ - z, 0), z - box.maxZ);
            if (dx * dx + dy * dy + dz * dz <= r * r) return true;
        }
        return false;
    }
}
