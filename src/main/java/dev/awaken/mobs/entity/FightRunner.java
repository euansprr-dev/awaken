package dev.awaken.mobs.entity;

import dev.awaken.core.api.BossPlates;
import dev.awaken.core.api.Stances;
import dev.awaken.core.api.Strikes;
import dev.awaken.mobs.boss.FightScripts;
import dev.awaken.mobs.boss.KitMoves;
import dev.awaken.mobs.boss.Kits;
import dev.awaken.mobs.def.Ability;
import dev.awaken.mobs.def.BoltKind;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
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
    /** The last attack begun (a move index), kept after it ends: what a fallen player was hit by. */
    private int last = -1;
    private KitMoves.@Nullable Played played;
    private float yaw;
    private @Nullable Ability ability;
    /** The move's told class (SPEC §47.2, I1): never NONE for a move that hurts. */
    private Strikes.Kind cls = Strikes.Kind.NONE;
    private final ArrayDeque<String> string = new ArrayDeque<>(4);
    /** The studio's queue (/awaken bosses studio): every move once, in table order, a breath apart; then it fights. */
    private final ArrayDeque<String> studio = new ArrayDeque<>();
    private final int[] hit = new int[16];
    private int hits, gap = 20, navTicks;
    private boolean introduced;
    /** A phase waiting for its transition (the threshold was crossed mid-move), or -1. */
    private int pendingPhase = -1;

    // the duel (SPEC §47.3), for a fight script that asks for it
    /** Opening after a phrase: ticks for a phrase of one blow, each blow more, at most. */
    static final int OPENING_MIN = 16, OPENING_PER_BLOW = 3, OPENING_MAX = 30;
    /** A deflected blow's limb recoils this long before the phrase goes on; its last blow turned aside, it reels this long. */
    static final int RECOIL_TICKS = 10, REEL_TICKS = 26;
    /** Deflects in a row before it answers with a red blow (forgotten after ten seconds); weights for its answers. */
    static final int HABIT_DEFLECTS = 2;
    static final long HABIT_FORGET = 200;
    static final int HABIT_RED = 4, HABIT_CLOSE = 3;
    private int phraseBlows, deflects, recoil;
    private long deflectAt;
    /** The target's distance, sampled every 10 ticks (the last two), to see it backing off. */
    private double distWas = -1, distWasWas = -1;

    // a fight that reads you (SPEC §47.6, the Examiner): through its first phase it counts how you meet it (deflects,
    // blows you got out of, ten-tick spells kept 8 blocks off, power hits), and the habit you lean on most, at least
    // HABIT_LEAN times over the others, brings up what answers it HABIT_ANSWER times as often from its second phase
    static final int HABIT_LEAN = 4, HABIT_ANSWER = 5;
    /** Spells at range to one habit point (two seconds kept off is one point). */
    static final int HABIT_FAR = 4;
    private int guards, slips, far, casts;
    private Strikes.Habit read = Strikes.Habit.NONE;
    private boolean measured;
    /** This blow: whether you stood in its reach as it struck, and whether it landed on you. */
    private boolean inReach, struck;

    // aiming a blow (SPEC §47.15): the correction it took at its lock, and the moves it can't reach you with for a moment
    private final BlowAim.Result aim = new BlowAim.Result(), trial = new BlowAim.Result();
    private boolean aimDone;
    private final long[] outOfReach;
    /** A string played by hand (tests, the studio) plays whether or not its blows would land. */
    private boolean byHand;

    // the mind (SPEC §48), for a fight script that has one
    private final @Nullable FightMind mind;
    /** Ticks it shows it noticed before its answer's wind-up. */
    static final int NOTICE_TICKS = 5;
    private int notice;
    private FightScripts.@Nullable Answer answering;
    private final java.util.function.IntPredicate canAnswer;
    private @Nullable String answerMove;
    /** The forks of the string being played, and which blow's fork was last taken (a fork is taken once). */
    private List<FightScripts.Fork> forks = List.of();
    private int forkDone;
    /** This blow is held waiting for you: it keeps turning to you through its hold, and locks as late as it always would. */
    private boolean followsHold;
    /** The answer waiting on its notice cuts into a phrase, which counts toward its opening. */
    private boolean carriedOn;
    /** A move that can't reach you now isn't tried again for this long. */
    static final int RETRY_TICKS = 8;

    FightRunner(AwakenBoss boss, Kits.Kit kit) {
        this.boss = boss;
        this.kit = kit;
        this.moves = kit.moves();
        this.fight = java.util.Objects.requireNonNull(kit.fight());
        this.outOfReach = new long[moves.moves().size()];
        this.mind = fight.mind().map(m -> new FightMind(boss, moves, m)).orElse(null);
        this.canAnswer = i -> ready(i, boss.getTarget());
    }

    Kits.Kit kit() {
        return kit;
    }

    /** The fight's mind, or null for a fight that hasn't one. */
    @Nullable FightMind mind() {
        return mind;
    }

    boolean busy() {
        return move >= 0;
    }

    boolean duel() {
        return fight.duel();
    }

    /** A duel's stance size, or 0 for a Lord's own. */
    int stance() {
        return fight.duel() ? fight.stance() : 0;
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
        forks = List.of();
        endNotice();
        introduced = true;
        phraseBlows = 1;
        recoil = 0;
        byHand = true;
        return begin(level, id, boss.getTarget());
    }

    /** Plays a string as the fight itself would, forks, holds and all (tests). */
    boolean fight(ServerLevel level, String first, java.util.List<String> then, List<FightScripts.Fork> forkAt) {
        stop();
        string.clear();
        endNotice();
        introduced = true;
        phraseBlows = 1;
        recoil = 0;
        byHand = false;
        forks = forkAt;
        forkDone = 0;
        if (!begin(level, first, boss.getTarget())) return false;
        string.addAll(then);
        return true;
    }

    /** Between blows and standing open for {@code ticks}, its intro done (tests). */
    void idleFor(ServerLevel level, int ticks) {
        stop();
        string.clear();
        forks = List.of();
        endNotice();
        introduced = true;
        phraseBlows = 1;
        recoil = 0;
        byHand = false;
        openUp(level, ticks);
    }

    private void endNotice() {
        notice = 0;
        answering = null;
        answerMove = null;
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

    /** The last attack begun, or null. */
    KitMoves.@Nullable Move lastAttack() {
        return last >= 0 ? moves.get(last) : null;
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
        recoil = 0;
        endNotice();
        if (move < 0) return;
        stop();
        string.clear();
        gap = 6 + boss.getRandom().nextInt(9);
    }

    /** A phase threshold was crossed: its transition plays as soon as the current move ends. */
    void enterPhase(int phase) {
        pendingPhase = phase;
        if (phase >= 1) habit(); // a fight that reads you has read you by its second phase
    }

    /**
     * The habit this fight has read of you: the one you leaned on most through its first phase, read once (at its second
     * phase, or when first asked); NONE for a fight that doesn't read you, or when no habit stood out.
     */
    Strikes.Habit habit() {
        if (!fight.mirrors() || read != Strikes.Habit.NONE || boss.phase() < 1) return read;
        int[] n = {guards, slips, far / HABIT_FAR, casts};
        int best = 0;
        for (int i = 1; i < n.length; i++) if (n[i] > n[best]) best = i;
        int next = 0;
        for (int i = 0; i < n.length; i++) if (i != best) next = Math.max(next, n[i]);
        if (n[best] >= HABIT_LEAN && n[best] > next) read = Strikes.Habit.values()[best + 1];
        return read;
    }

    /** It was hurt by {@code source} (landed): a power's blow is a habit too. */
    void hurtBy(DamageSource source) {
        if (!fight.mirrors() || read != Strikes.Habit.NONE || !(source.getEntity() instanceof net.minecraft.world.entity.player.Player)) return;
        for (var k : POWER_DAMAGE) {
            if (source.is(k)) {
                casts++;
                return;
            }
        }
    }

    private static final List<net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType>> POWER_DAMAGE = List.of(
            dev.awaken.core.Forces.HEAT, dev.awaken.core.Forces.CHARGE, dev.awaken.core.Forces.GRAVITY, dev.awaken.core.Forces.SPIRIT,
            dev.awaken.core.Forces.FROST, dev.awaken.core.Forces.KINETIC);

    /**
     * The look of the power family you've used most, for its shots (their look only, never an effect): your own power,
     * thrown back at you. Null when you've used none.
     */
    static @Nullable BoltKind yours(net.minecraft.server.level.ServerPlayer sp) {
        String best = null;
        long most = 0;
        for (var en : dev.awaken.progression.Progression.profile(sp).counters().entrySet()) {
            if (!en.getKey().startsWith(POWER_USES) || en.getValue() <= most) continue;
            most = en.getValue();
            best = en.getKey().substring(POWER_USES.length());
        }
        if (best == null) return null;
        return switch (best) {
            case "kinetic" -> BoltKind.GRAVITY;
            case "velocity", "energy" -> BoltKind.CHARGE;
            case "flight", "architect" -> BoltKind.GLASS;
            case "shadow" -> BoltKind.VOID;
            case "mind" -> BoltKind.DREAM;
            case "tech" -> BoltKind.SIGNAL;
            case "spirit" -> BoltKind.SPIRIT;
            default -> null;
        };
    }

    private static final String POWER_USES = "awaken:power_uses/";

    void tick(ServerLevel level) {
        if (mind != null) mind.watch(boss.getTarget(), level.getGameTime());
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
            boss.moment(level);
            if (fight.mirrors() && !measured && target instanceof net.minecraft.server.level.ServerPlayer sp) {
                measured = true;
                boss.mirrorBolt(yours(sp));
            }
            boss.syncKit(-1, 0, 0);
            if (fight.intro().move().isPresent() && begin(level, fight.intro().move().get(), target)) return;
        }
        if (pendingPhase >= 0) {
            int p = pendingPhase;
            pendingPhase = -1;
            String tr = fight.phase(p).transition().orElse(null);
            string.clear();
            phraseBlows = 1;
            if (tr != null && begin(level, tr, target)) {
                boss.shield(windup + active + recover);
                return;
            }
        }
        // a broken stance or a stagger is the opening it earned: it starts nothing (and kneels while broken, reels from
        // a phrase's last blow turned aside, its limb recoils from a deflect)
        boolean broken = boss.broken() || Stances.broken(boss);
        int pose = broken ? AwakenBoss.KIT_POSE_BROKEN : boss.reeling() ? AwakenBoss.KIT_POSE_REEL : recoil > 0 ? AwakenBoss.KIT_POSE_RECOIL
                : notice > 0 ? AwakenBoss.KIT_POSE_NOTICE : 0;
        boss.syncKit(-1, 0, pose);
        if (broken || boss.staggered() || boss.flag(AwakenMob.F_FALLEN) || boss.flag(AwakenMob.F_DORMANT)) {
            string.clear();
            recoil = 0;
            endNotice();
            return;
        }
        if (target == null || !target.isAlive()) {
            endNotice();
            return;
        }
        boss.getLookControl().setLookAt(target, 30, 30);
        if ((boss.tickCount % 10) == 0) {
            distWasWas = distWas;
            distWas = edgeDistance(target);
            if (fight.mirrors() && read == Strikes.Habit.NONE && distWas >= 8) far++;
        }
        if (recoil > 0) { // the limb comes back; the phrase goes on after
            recoil--;
            boss.getNavigation().stop();
            return;
        }
        if (notice > 0) {
            noticing(level, target);
            return;
        }
        if (!string.isEmpty()) {
            if (answer(target, level, false)) return;
            fork(target);
            if (mind != null && !byHand) dropRepeats();
            String next = string.poll();
            int i = next == null ? -1 : moves.index(next);
            if (i >= 0 && inRange(moves.get(i), target) && reaches(i, target) && begin(level, next, target)) {
                phraseBlows++;
                return;
            }
            string.clear();
            if (fight.duel()) { // a phrase cut short (you got out of reach) still ends in its opening
                openUp(level, opening(phraseBlows));
                return;
            }
        }
        byHand = false;
        if (gap > 0) {
            gap--;
            // an opening: it starts nothing and stands its ground, turning to keep you in front (SPEC §47.3)
            if (boss.inOpening()) boss.getNavigation().stop();
            else approach(target);
            return;
        }
        if (answer(target, level, false)) return;
        FightScripts.Entry e = pick(target);
        if (e == null || !reaches(moves.index(e.move()), target) || !begin(level, e.move(), target)) {
            if (mind != null) mind.nothingToStart(level.getGameTime());
            approach(target);
            return;
        }
        phraseBlows = 1;
        forks = e.forks();
        forkDone = 0;
        if (!e.then().isEmpty()) {
            List<String> s = e.then().get(boss.getRandom().nextInt(e.then().size()));
            string.addAll(s);
        }
    }

    /**
     * An answer is due (SPEC §48.2): what it was going to do next is off, and it shows you it noticed before it starts.
     * Only ever between blows: at a string's link or with nothing playing, so a swing that has begun plays out.
     */
    private boolean answer(LivingEntity target, ServerLevel level, boolean pressOnly) {
        if (mind == null || byHand || !studio.isEmpty()) return false;
        FightScripts.Answer a = mind.due(canAnswer, level.getGameTime(), pressOnly);
        if (a == null) return false;
        carriedOn = pressOnly || !string.isEmpty();
        answering = a;
        answerMove = mind.chosen();
        notice = NOTICE_TICKS;
        string.clear();
        forks = List.of();
        boss.getNavigation().stop();
        if (!dev.awaken.mobs.boss.KitSounds.play(boss, moves, "notice", 1F) && !dev.awaken.mobs.boss.KitSounds.play(boss, moves, "step", 1F))
            dev.awaken.mobs.boss.KitSounds.play(boss, moves, "impact", 0.5F);
        return true;
    }

    /** The beat it takes to show it noticed: its head snaps round and it drops; then the answer's wind-up, tell and all. */
    private void noticing(ServerLevel level, LivingEntity target) {
        boss.getNavigation().stop();
        boss.getLookControl().setLookAt(target, 180, 180);
        if (--notice > 0) return;
        FightScripts.Answer a = answering;
        String id = answerMove;
        answering = null;
        answerMove = null;
        int i = id == null ? -1 : moves.index(id);
        // you got out of its reach while it was looking: the normal choice takes over next tick
        if (a == null || i < 0 || !inRange(moves.get(i), target) || !reaches(i, target) || !begin(level, id, target)) {
            if (carriedOn && fight.duel()) openUp(level, opening(phraseBlows)); // a phrase it cut short still ends in its opening
            return;
        }
        phraseBlows = carriedOn ? phraseBlows + 1 : 1;
        forkDone = 0;
        if (!a.then().isEmpty()) string.addAll(a.then().get(boss.getRandom().nextInt(a.then().size())));
    }

    /** A string doesn't make the same move twice running either (SPEC §48.3): a fork can land on the blow that was coming anyway. */
    private void dropRepeats() {
        while (!string.isEmpty() && !mind.follows(moves.index(string.peek()))) string.poll();
    }

    /** Whether this phase's pool or strings use the move: an answer never reaches for a move its phase hasn't got yet (SPEC §48.6). */
    private boolean inPhase(String id) {
        for (FightScripts.Entry e : fight.phase(boss.phase()).pool()) {
            if (e.move().equals(id)) return true;
            for (List<String> string : e.then()) if (string.contains(id)) return true;
        }
        return false;
    }

    /** Whether a move could begin now, as an answer or at a fork. */
    private boolean ready(int i, LivingEntity target) {
        KitMoves.Move m = moves.get(i);
        if (mind != null && (!mind.follows(i) || !inPhase(m.id()))) return false;
        if (boss.level().getGameTime() < outOfReach[i] || !inRange(m, target) || !reaches(i, target)) return false;
        if (m.ability().isEmpty()) return true;
        Ability a = boss.ability(m.ability().get());
        return a == null || !(boss.level() instanceof ServerLevel sl) || boss.abilityRunner().room(sl, a);
    }

    /** The string's next blow by what you're doing right now (SPEC §48.4); until the fork, the branches look the same. */
    private void fork(LivingEntity target) {
        if (mind == null || byHand || forkDone >= phraseBlows || phraseBlows > forks.size()) return;
        forkDone = phraseBlows;
        String next = mind.forked(forks.get(phraseBlows - 1), boss.level().getGameTime());
        if (next == null || (!string.isEmpty() && next.equals(string.peek()))) return;
        int i = moves.index(next);
        if (i < 0 || !ready(i, target)) return;
        if (!string.isEmpty()) string.poll();
        string.addFirst(next);
    }

    /** Walks toward the target when nothing in the pool reaches it (fliers keep their own flight goal). */
    private void approach(LivingEntity target) {
        if (boss.def().flies()) return;
        if (edgeDistance(target) < TOO_CLOSE) { // inside its own swing: it makes room
            boss.getNavigation().stop();
            Vec3 away = boss.position().subtract(target.position());
            double len = Math.sqrt(away.x * away.x + away.z * away.z);
            if (len > 1.0E-3) boss.move(MoverType.SELF, new Vec3(away.x / len * STEP_BACK, 0, away.z / len * STEP_BACK));
            return;
        }
        if (navTicks-- > 0) return;
        navTicks = 10;
        // it stops where its close moves reach (at most 3.5 blocks out): Unit Zero's cuts reach 1.6, not 3.5
        double reach = closeReach();
        // its longest close blow is what keeps it this far out: when that's the one it just made, it steps in until another reaches (SPEC §48.3)
        if (mind != null && mind.waiting(boss.level().getGameTime()) > STEP_IN_AFTER) reach = shortestCloseReach(reach);
        double stop = reach > 0 ? Math.min(3.5, Math.max(0.5, reach - 0.3)) : 3.5;
        if (edgeDistance(target) > stop) boss.getNavigation().moveTo(target, 1.0);
        else boss.getNavigation().stop();
    }

    /** With only one close blow in this phase it can't change what it does; it waits this long, not the full last resort. */
    private static final int ONE_CLOSE_BLOW_AFTER = 60;

    /** How many blows in this pool can be started from close (not kept for far or behind). */
    private int closeBlows(List<FightScripts.Entry> pool) {
        int n = 0;
        for (FightScripts.Entry e : pool) {
            int i = moves.index(e.move());
            if (i >= 0 && moves.get(i).kind() != Strikes.Kind.NONE && moves.get(i).minRange() <= 0 && e.when() != FightScripts.When.FAR && e.when() != FightScripts.When.BEHIND) n++;
        }
        return n;
    }

    /** How long it stands with nothing to start before it steps closer. */
    private static final int STEP_IN_AFTER = 15;

    /** The shortest reach among its close moves in this phase, for when the longest has just been used. */
    private double shortestCloseReach(double longest) {
        double reach = longest;
        for (FightScripts.Entry e : fight.phase(boss.phase()).pool()) {
            int i = moves.index(e.move());
            if (i < 0 || moves.get(i).minRange() > 0 || moves.get(i).maxRange() <= 0 || e.when() == FightScripts.When.FAR || e.when() == FightScripts.When.BEHIND) continue;
            reach = Math.min(reach, moves.get(i).maxRange());
        }
        return reach;
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
        if (mind != null) mind.repeatAfter(closeBlows(pool) <= 1 ? ONE_CLOSE_BLOW_AFTER : FightMind.REPEAT_AFTER);
        int sum = 0;
        for (FightScripts.Entry e : pool) if (usable(e, target)) sum += weight(e, target);
        if (sum <= 0) return null;
        int r = boss.getRandom().nextInt(sum);
        for (FightScripts.Entry e : pool) {
            if (!usable(e, target)) continue;
            r -= weight(e, target);
            if (r < 0) {
                if (red(e)) deflects = 0; // its answer to your deflects is given
                return e;
            }
        }
        return null;
    }

    /**
     * An entry's weight; in a duel it answers your habits (SPEC §47.3): after {@link #HABIT_DEFLECTS} deflects in a row
     * its red phrases come up {@link #HABIT_RED} times as often, and when you back off its gap closers
     * {@link #HABIT_CLOSE} times.
     */
    private int weight(FightScripts.Entry e, LivingEntity target) {
        int w = Math.max(1, e.weight());
        if (!fight.duel()) return w;
        if (deflects >= HABIT_DEFLECTS && boss.level().getGameTime() - deflectAt <= HABIT_FORGET && red(e)) w *= HABIT_RED;
        int i = moves.index(e.move());
        if (i >= 0 && moves.get(i).is("gap") && backingOff()) w *= HABIT_CLOSE;
        if (i >= 0 && read != Strikes.Habit.NONE && answers(e, moves.get(i))) w *= HABIT_ANSWER;
        return w;
    }

    /**
     * Whether a phrase answers the habit it read (SPEC §47.6): your guard, the blows that can't be turned aside (amber,
     * red, a grab); your dodging, the held blows and the long strings (a dodge spent early, or on the first blow of
     * three); your distance, its gap closers; your powers, its shots (in your power's look).
     */
    private boolean answers(FightScripts.Entry e, KitMoves.Move m) {
        return switch (read) {
            case GUARD -> {
                Strikes.Kind k = told(m, m.ability().map(boss::ability).orElse(null));
                yield k == Strikes.Kind.HEAVY || k == Strikes.Kind.PERILOUS || k == Strikes.Kind.GRAB;
            }
            case SLIP -> m.time().hold() > 0 || e.then().stream().anyMatch(t -> t.size() >= 2);
            case DISTANCE -> m.is("gap");
            case POWERS -> m.is("ranged");
            case NONE -> false;
        };
    }

    private boolean red(FightScripts.Entry e) {
        int i = moves.index(e.move());
        if (i < 0) return false;
        KitMoves.Move m = moves.get(i);
        Strikes.Kind k = told(m, m.ability().map(boss::ability).orElse(null));
        return k == Strikes.Kind.PERILOUS || k == Strikes.Kind.GRAB;
    }

    /** The target is putting distance between you: further than 6 blocks and 1.5 further than a second ago. */
    private boolean backingOff() {
        return distWas >= 6 && distWasWas >= 0 && distWas - distWasWas > 1.5;
    }

    /** The opening after a phrase of {@code blows}: 0.8 s after one, longer after longer ones, at most 1.5 s. */
    static int opening(int blows) {
        return Math.min(OPENING_MAX, OPENING_MIN + OPENING_PER_BLOW * Math.max(0, blows - 1));
    }

    /** It stands open for {@code ticks}: it starts nothing, and blows on it break its stance faster. */
    private void openUp(ServerLevel level, int ticks) {
        gap = ticks;
        boss.openFor(ticks);
    }

    /**
     * A blow of its was deflected (Arms, SPEC §47.3): the blow is spent and its limb recoils; the phrase goes on after
     * {@link #RECOIL_TICKS}, unless that was its last blow: then it reels, the opening a parry earns.
     */
    void deflected(ServerLevel level, Strikes.Kind kind) {
        if (!fight.duel() || move < 0 || cls == Strikes.Kind.NONE) return;
        long now = level.getGameTime();
        if (now - deflectAt > HABIT_FORGET) deflects = 0;
        deflects++;
        if (fight.mirrors() && read == Strikes.Habit.NONE) guards++;
        deflectAt = now;
        boolean last = string.isEmpty();
        stop();
        dev.awaken.mobs.boss.KitSounds.play(boss, moves, "impact", 0.8F); // the limb stopped dead, heard at the body
        if (last) {
            // the reel is a stagger (nothing counts down through it), then half an opening more while it gathers itself
            boss.reel(REEL_TICKS);
            gap = OPENING_MIN / 2;
            boss.openFor(REEL_TICKS + OPENING_MIN / 2);
        } else {
            recoil = RECOIL_TICKS;
            boss.syncKit(-1, 0, AwakenBoss.KIT_POSE_RECOIL);
        }
    }

    private boolean usable(FightScripts.Entry e, LivingEntity target) {
        int i = moves.index(e.move());
        if (i < 0) return false;
        KitMoves.Move m = moves.get(i);
        if (boss.level().getGameTime() < outOfReach[i] || !inRange(m, target)) return false;
        if (mind != null && !mind.allowed(i, boss.level().getGameTime())) return false;
        if (!e.band().isEmpty() && (edgeDistance(target) < e.band().get(0) || (e.band().size() > 1 && edgeDistance(target) > e.band().get(1)))) return false;
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
        return d >= m.minRange() && d <= m.maxRange() && inBand(m, target);
    }

    /** A swing's height against yours: a body whose blow passes far over or under you doesn't begin it (SPEC §47.15). */
    private boolean inBand(KitMoves.Move m, LivingEntity target) {
        if (byHand || !aimed(m)) return true;
        float low = Float.MAX_VALUE, high = -Float.MAX_VALUE;
        for (List<KitMoves.Capsule> frame : m.hits()) {
            for (KitMoves.Capsule c : frame) {
                low = Math.min(low, Math.min(c.ay(), c.by()) - c.r());
                high = Math.max(high, Math.max(c.ay(), c.by()) + c.r());
            }
        }
        double reach = boss.def().flies() ? BAND_SLACK + 1.5 : BAND_SLACK;
        return target.getBoundingBox().minY - boss.getY() <= high + reach && target.getBoundingBox().maxY - boss.getY() >= low - reach;
    }

    /** Closer than this its swings pass over you; it backs off this far a tick. */
    private static final double TOO_CLOSE = 0.35, STEP_BACK = 0.16;

    /** How far past its sweep a lean carries a blow, in height. */
    private static final double BAND_SLACK = 1.2;

    /** A swing of the body's own, aimed at its lock; a move with an ability aims in its own way. */
    private static boolean aimed(KitMoves.Move m) {
        return !m.hits().isEmpty() && m.ability().isEmpty();
    }

    /** Whether the move can land on you where you stand; if not, it isn't tried again for {@link #RETRY_TICKS}. */
    private boolean reaches(int i, LivingEntity target) {
        if (i < 0) return false;
        KitMoves.Move m = moves.get(i);
        if (byHand || !aimed(m)) return true;
        boolean ok = solve(m, target, boss.yBodyRot, trial);
        if (!ok) outOfReach[i] = boss.level().getGameTime() + RETRY_TICKS;
        return ok;
    }

    /** The correction that lands {@code m} on the target, with the body facing it (or {@code facing}, for a blow that doesn't turn). */
    private boolean solve(KitMoves.Move m, LivingEntity target, float facing, BlowAim.Result out) {
        boolean turns = m.time().track() > 0;
        double dx = target.getX() - boss.getX(), dz = target.getZ() - boss.getZ();
        float base = turns ? (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90 : facing;
        float r = base * Mth.DEG_TO_RAD, c = Mth.cos(r), s = Mth.sin(r);
        // the body's frame: forward (-sin, cos), left (cos, sin)
        double lx = dx * c + dz * s, lz = -dx * s + dz * c;
        double y0 = target.getBoundingBox().minY - boss.getY(), y1 = target.getBoundingBox().maxY - boss.getY();
        boolean ok = BlowAim.solve(m, hip(), lx, lz, y0, y1, target.getBbWidth() * 0.5, boss.def().flies(), lowestDip(), true, Math.max(0, edgeDistance(target) - 0.1), out);
        if (ok) out.turn = Mth.wrapDegrees(base + out.turn);
        return ok;
    }

    /** How far a flier can drop before the floor stops it (0 to -1.5, in the dips' half blocks). */
    private float lowestDip() {
        if (!boss.def().flies()) return 0;
        float d = 0;
        while (d > -1.5F && boss.level().noBlockCollision(boss, boss.getBoundingBox().move(0, d - 0.5F, 0))) d -= 0.5F;
        return d;
    }

    private float hip() {
        return boss.getBbHeight() * AwakenBoss.KIT_LEAN_HIP;
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
        cls = told(m, m.ability().map(boss::ability).orElse(null));
        // the enrage is speed, never damage: the wind-up and recovery shrink, but never under its class's tell
        int held = cls == Strikes.Kind.HEAVY && m.time().hold() > 0 && boss.getRandom().nextInt(3) == 0 ? m.time().hold() : 0;
        followsHold = false;
        if (mind != null) {
            mind.began(i, level.getGameTime());
            if (!byHand && studio.isEmpty() && FightMind.holds(cls)) {
                boolean wary = deflects >= HABIT_DEFLECTS && level.getGameTime() - deflectAt <= HABIT_FORGET;
                held = mind.hold(m, wary, level.getGameTime());
                followsHold = held > 0;
            }
        }
        KitMoves.Played p = KitMoves.played(m, boss.kitSpeed(), held);
        played = p;
        windup = p.windup();
        hold = p.hold();
        active = p.active();
        recover = p.recover();
        move = i;
        if (cls != Strikes.Kind.NONE) last = i;
        t = 0;
        hits = 0;
        yaw = boss.yBodyRot;
        aim.clear();
        aimDone = false;
        // an ability needs someone to aim at (the studio plays its moves with nobody there: the body only)
        ability = target != null ? m.ability().map(boss::ability).orElse(null) : null;
        if (ability != null) boss.abilityRunner().prepare(level, ability, target, windup + hold, cls, m.signature());
        boss.getNavigation().stop();
        boss.syncKit(i, hold, 0);
        if (cls != Strikes.Kind.NONE) {
            boss.setTell(windup + hold, cls, cls == Strikes.Kind.HEAVY);
            // the body's own voice for the class when it has one (the glow still tells the class), else Arms' one
            if (!sound(m, "tell", "tell_" + cls.name().toLowerCase(java.util.Locale.ROOT)))
                boss.playTell(cls, cls == Strikes.Kind.HEAVY, 1.0F);
        }
        if (m.signature() && !m.title().isEmpty() && boss.bar() != null) BossPlates.line(boss.bar(), m.title(), BossPlates.Line.SIGNATURE, boss.def().glow);
        return true;
    }

    /**
     * From its lock on the blow commits to where you are: it takes its correction (SPEC §47.15) over what's left of the
     * wind-up. If you've moved so that no correction reaches you, it swings where it was going: you got out of it.
     */
    private void aimBlow(KitMoves.Move m, @Nullable LivingEntity target, int lockAt, int wind) {
        if (!aimed(m) || t < lockAt) return;
        if (!aimDone) {
            aimDone = true;
            if (target != null && target.isAlive() && solve(m, target, yaw, aim)) boss.syncKitLean(Math.round(aim.lean));
            else {
                aim.clear();
                aim.turn = yaw;
            }
        }
        yaw = Mth.approachDegrees(yaw, aim.turn, Math.max(TURN_RATE, m.time().track()));
        int left = Math.max(1, wind - lockAt);
        if (aim.step != 0 || aim.dip != 0) {
            float r = yaw * Mth.DEG_TO_RAD, per = aim.step / left;
            boss.move(MoverType.SELF, new Vec3(-Mth.sin(r) * per, aim.dip / left, Mth.cos(r) * per));
        }
    }

    /** How fast the body turns into its aim after the lock (degrees a tick). */
    private static final float TURN_RATE = 14F;

    private void step(ServerLevel level) {
        KitMoves.Move m = moves.get(move);
        LivingEntity target = boss.getTarget();
        t++;
        int wind = windup + hold;
        if (t <= wind) {
            // the wind-up: it turns toward you until it locks, then commits; an aimed ability locks at least its lead
            // before it can land (SPEC §47.2, I3), whatever the move's own lock says
            int lock = m.time().lock() + (followsHold ? hold : 0);
            int lockAt = ability != null ? Math.min(lock, wind - Carriers.lead(ability)) : lock;
            boolean tracking = t <= lockAt && target != null && target.isAlive();
            if (tracking && m.time().track() > 0) {
                float want = (float) (Mth.atan2(target.getZ() - boss.getZ(), target.getX() - boss.getX()) * Mth.RAD_TO_DEG) - 90;
                yaw = Mth.approachDegrees(yaw, want, m.time().track());
            }
            aimBlow(m, target, lockAt, wind);
            face();
            boss.getNavigation().stop();
            if (ability != null) boss.abilityRunner().windup(level, ability, wind - t + 1, tracking);
            if (cls != Strikes.Kind.NONE && (t & 3) == 0) boss.setTell(wind - t, cls, cls == Strikes.Kind.HEAVY);
            return;
        }
        int k = t - wind - 1;
        if (k < active) {
            if (k == 0) {
                inReach = target != null && target.isAlive() && !m.hits().isEmpty() && edgeDistance(target) <= m.maxRange() + 0.5;
                struck = false;
                boss.setTelegraph(0, false);
                if (ability != null) {
                    boss.abilityRunner().fire(level, ability, world(m.origin()), cls != Strikes.Kind.NONE ? cls : ability.strike());
                    if (move < 0) return; // turned aside as it fired: deflected() has ended the move
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
                if (cls == Strikes.Kind.NONE) {
                    if (!sound(m, "swing", "roar")) boss.roar();
                } else if (m.hits().isEmpty()) sound(m, "swing", "cast");
                if (m.is("area") && firstHit(m) < 0) sound(m, "impact", "impact");
            }
            face();
            // a charge carries the body itself (it stops at you); the clip's own run would add to it
            boolean charging = ability != null && ability.kind() == Ability.Kind.CHARGE;
            float adv = charging ? 0 : m.motion().advance() * aim.run / active, up = m.motion().rise() / active;
            if (adv != 0 || up != 0) {
                double r = yaw * Mth.DEG_TO_RAD;
                boss.move(MoverType.SELF, new Vec3(-Mth.sin((float) r) * adv, up, Mth.cos((float) r) * adv));
            }
            if (k < m.hits().size()) strike(level, m, m.hits().get(k));
            marks(m);
            return;
        }
        marks(m);
        // a blow you stood in the reach of that never landed (and wasn't turned aside: that ends it before here) you got
        // out of: a dash, a step, a roll
        // a blow that met nobody though you stood in its reach: one line each, for the next playtest (SPEC §47.3)
        if (k == active && mind != null && inReach && hits == 0 && !m.hits().isEmpty() && target != null) {
            dev.awaken.Awaken.LOG.info("[fights] missed {} {} {}: {} blocks off, {}", fight.id(), m.id(), cls,
                    String.format("%.1f", edgeDistance(target)), boss.getSensing().hasLineOfSight(target) ? "in sight" : "out of sight");
        }
        if (k == active && inReach && !struck && fight.mirrors() && read == Strikes.Habit.NONE) slips++;
        if (t < wind + active + recover) {
            // the recovery: no turning, the opening; in a string the next move links on at the end of the
            // follow-through instead of the body going back to rest first (SPEC §35.10)
            if (played != null && played.link() < recover && t == wind + active + played.link()) link(level, target);
            return;
        }
        stop();
        if (!studio.isEmpty()) gap = 30;
        else if (!string.isEmpty()) gap = 0;
        else if (fight.duel()) openUp(level, opening(phraseBlows)); // every phrase ends in an opening (SPEC §47.3, I8)
        else gap = 6 + boss.getRandom().nextInt(9);
    }

    /**
     * The sounds on the move's marks, each at the tick its base-timed mark plays at: the blow's whoosh, its impact (an
     * area move meeting the ground) and the steps its feet take where it doesn't travel (travel steps are the walk's).
     */
    private void marks(KitMoves.Move m) {
        if (played == null || cls == Strikes.Kind.NONE) return;
        KitMoves.Time base = m.time();
        if (!m.hits().isEmpty()) {
            int whoosh = Math.max(1, Math.round(played.at(base, m.mark("whoosh", base.windup())))) + 1;
            if (t == whoosh) sound(m, "swing", cls == Strikes.Kind.LIGHT ? "swing_light" : "swing_heavy");
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
        if (pendingPhase >= 0 || !studio.isEmpty() || target == null || !target.isAlive()) return false;
        // the follow-through ends here, as it would for the string's next blow; a phrase's last blow only for an answer that presses on
        if (answer(target, level, string.isEmpty())) {
            stop();
            return false;
        }
        if (string.isEmpty()) return false;
        if (mind != null && !byHand) {
            fork(target);
            dropRepeats();
            if (string.isEmpty()) return false;
        }
        int i = moves.index(string.peek());
        if (i < 0 || !inRange(moves.get(i), target) || !reaches(i, target)) return false;
        String next = string.poll();
        stop();
        phraseBlows++;
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
        boss.syncKitLean(0);
        aim.clear();
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

    /**
     * A move's told class (SPEC §47.2, I1): its own, else what it does says it (an ability's class, a blow's amber), so
     * no move that hurts goes untold. NONE only for a move that hurts nobody (a roar, a summon, a guard).
     */
    static Strikes.Kind told(KitMoves.Move m, @Nullable Ability a) {
        if (m.kind() != Strikes.Kind.NONE) return m.kind();
        if (a != null && a.strike() != Strikes.Kind.NONE) return a.strike();
        return m.hits().isEmpty() ? Strikes.Kind.NONE : Strikes.Kind.HEAVY;
    }

    /** A point in the body's space (blocks: +x left, +y up, +z forward) in the world, at the locked facing. */
    private Vec3 world(List<Float> p) {
        if (p.size() < 3) return boss.position();
        return world(p.get(0), p.get(1), p.get(2));
    }

    private Vec3 world(float x, float y, float z) {
        if (aim.lean != 0) { // the body leans into the blow about its hips
            float hip = hip(), cl = Mth.cos(aim.lean * Mth.DEG_TO_RAD), sl = Mth.sin(aim.lean * Mth.DEG_TO_RAD), up = y - hip;
            float zw = up * sl + z * cl;
            y = hip + up * cl - z * sl;
            z = zw;
        }
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
            Strikes.Kind kind = cls == Strikes.Kind.NONE ? Strikes.Kind.HEAVY : cls;
            boolean landed = Strikes.deliver(boss, kind, () -> e.hurtServer(level, level.damageSources().mobAttack(boss), dmg));
            if (landed && e == boss.getTarget()) struck = true;
            if (m.knock() > 0) {
                // a blow a guard took pushes even when it did no damage (all of it stopped, or the last blow's immunity)
                Vec3 away = e.position().subtract(boss.position());
                Runnable push = () -> Abilities.push(e, away, m.knock(), Math.min(0.5, m.knock() * 0.4));
                if (landed || dev.awaken.arms.Arms.guarded(e)) push.run();
                else dev.awaken.arms.Arms.afterHeld(e, push);
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
