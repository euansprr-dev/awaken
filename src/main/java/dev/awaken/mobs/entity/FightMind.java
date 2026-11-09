package dev.awaken.mobs.entity;

import dev.awaken.core.api.Strikes;
import dev.awaken.core.api.Watch;
import dev.awaken.mobs.boss.FightScripts;
import dev.awaken.mobs.boss.KitMoves;
import dev.awaken.progression.Rank;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.function.IntPredicate;

/**
 * A fight's mind (SPEC §48.1 to 48.4), for a script that has one: what it has seen its target start, which answer that
 * calls for, and what keeps its moves from being dice (no move twice running, cooldowns, held blows). The read is for one
 * target and starts again when the target changes. Lasts the fight only; nothing allocated per tick.
 */
final class FightMind {
    /** A dodge counts as "just dodged" this long after the reaction time, and the next one this soon keeps a streak going. */
    static final int DODGED_FOR = 16, STREAK_GAP = 40;
    /** The dodge it catches: the third in a row. */
    static final int STREAK_CAUGHT = 3;
    /** The beat a blow is held when it's waiting for you to land. */
    static final int CAUGHT_HOLD = 10;
    /** A pause after running (a sample is 10 ticks): at least this many samples running, then one standing, counted this long. */
    static final int RAN_SAMPLES = 3, PAUSED_FOR = 30;
    /** A stop counts as stopping a run this soon after it: the half second you stopped in is half a run. */
    private static final int STOPPED_WITHIN = 20;
    private static final double RUN_STEP = 1.5, STOP_STEP = 0.4;
    /** When a roll came up empty, or an answer was given and its cooldown is short, it isn't weighed again for this long. */
    static final int ROLL_GAP = 40;
    /** With nothing it may start for this long, its blows' rests are waived. */
    static final int STALE_TICKS = 40;
    /** With nothing else for this long, a blow may follow itself: the last resort, so a spot only one move reaches isn't safe. */
    static final int REPEAT_AFTER = 200;

    private final AwakenBoss boss;
    private final KitMoves.Table moves;
    private final FightScripts.Mind mind;
    final int react;

    private int target = -1;
    /** When each fact in the {@code Fact} enum began (game time), or -1. */
    private final long[] since = new long[FightScripts.Fact.values().length];
    private long dodgedAt = -1;
    private int streak;
    private double tx, tz;
    private boolean hasPos;
    private int ran;
    private long ranAt = -1;
    private long pausedAt = -1;

    private final long[] nextRoll, ready;
    private int lastMove = -1;
    private long wanting = -1;
    private int lastResorts;
    private @Nullable String chosen;
    /** Tests: every answer is given, no dice. */
    boolean sure;

    FightMind(AwakenBoss boss, KitMoves.Table moves, FightScripts.Mind mind) {
        this.boss = boss;
        this.moves = moves;
        this.mind = mind;
        this.react = mind.react() > 0 ? mind.react() : reaction(boss.def().theme.home);
        this.nextRoll = new long[mind.answers().size()];
        this.ready = new long[moves.moves().size()];
        forget();
    }

    /** How long a foe of this rank takes to believe its eyes (ticks): 0.6 s at E, 0.35 s from S up. */
    static int reaction(Rank rank) {
        return Math.max(7, 12 - rank.ordinal());
    }

    private void forget() {
        target = -1;
        Arrays.fill(since, -1);
        dodgedAt = -1;
        pausedAt = -1;
        ranAt = -1;
        streak = 0;
        ran = 0;
        hasPos = false;
    }

    /** Every tick of the fight: what the target is doing now. */
    void watch(@Nullable LivingEntity t, long now) {
        if (t == null || !t.isAlive()) {
            if (target != -1) forget();
            return;
        }
        if (t.getId() != target) {
            forget();
            target = t.getId();
        }
        int f = Watch.facts(t);
        look(FightScripts.Fact.DRINK, f & Watch.DRINK, now);
        look(FightScripts.Fact.GUARD, f & Watch.GUARD, now);
        look(FightScripts.Fact.HEAVY, f & Watch.HEAVY, now);
        look(FightScripts.Fact.RECOVERY, f & Watch.RECOVERY, now);
        look(FightScripts.Fact.CAST, f & Watch.CAST, now);
        boolean was = since[FightScripts.Fact.DODGE.ordinal()] >= 0;
        look(FightScripts.Fact.DODGE, f & Watch.DODGE, now);
        if (!was && (f & Watch.DODGE) != 0) {
            streak = dodgedAt >= 0 && now - dodgedAt <= STREAK_GAP ? streak + 1 : 1;
            dodgedAt = now;
        }
        if ((boss.tickCount % 10) == 0) sampleRun(t, now);
    }

    private void look(FightScripts.Fact fact, int on, long now) {
        int i = fact.ordinal();
        if (on == 0) since[i] = -1;
        else if (since[i] < 0) since[i] = now;
    }

    /** Running away, seen every half second; stopping after a run is the pounce's moment. */
    private void sampleRun(LivingEntity t, long now) {
        double x = t.getX(), z = t.getZ();
        if (hasPos) {
            double mx = x - tx, mz = z - tz, dx = x - boss.getX(), dz = z - boss.getZ(), len = Math.sqrt(dx * dx + dz * dz);
            double away = len < 1.0E-3 ? 0 : (mx * dx + mz * dz) / len;
            if (away > RUN_STEP) {
                if (++ran >= RAN_SAMPLES) ranAt = now;
            } else {
                ran = 0;
                if (ranAt >= 0 && now - ranAt <= STOPPED_WITHIN && Math.sqrt(mx * mx + mz * mz) < STOP_STEP) {
                    pausedAt = now;
                    ranAt = -1;
                }
            }
        }
        tx = x;
        tz = z;
        hasPos = true;
    }

    /** When this fact began, if it has now been true long enough to believe (and at least {@code after} ticks), else -1. */
    long seen(FightScripts.Fact fact, int after, long now) {
        long at = switch (fact) {
            case DODGE -> dodgedAt >= 0 && now - dodgedAt <= react + DODGED_FOR ? dodgedAt : -1;
            case PAUSED -> pausedAt >= 0 && now - pausedAt <= react + PAUSED_FOR ? pausedAt : -1;
            case NONE -> -1;
            default -> since[fact.ordinal()];
        };
        return at >= 0 && now - at >= Math.max(react, after) ? at : -1;
    }

    /**
     * The answer due now: of those whose fact it has seen and which aren't resting, the one to what you started most
     * recently. Each is weighed once and then rests; one with no move that reaches you is skipped without resting.
     * {@link #chosen()} then names its move. {@code pressOnly}: at a phrase's last blow, only those that may carry on past it.
     */
    FightScripts.@Nullable Answer due(IntPredicate reaches, long now, boolean pressOnly) {
        int best = -1;
        long bestAt = -1;
        for (int i = 0; i < nextRoll.length; i++) {
            FightScripts.Answer a = mind.answers().get(i);
            if (now < nextRoll[i] || boss.phase() < a.from() || (pressOnly && !a.press())) continue;
            long at = seen(a.on(), a.after(), now);
            if (at > bestAt && reachable(a, reaches) != null) {
                best = i;
                bestAt = at;
            }
        }
        if (best < 0) return null;
        FightScripts.Answer a = mind.answers().get(best);
        if (!sure && boss.getRandom().nextFloat() >= a.chance()) {
            nextRoll[best] = now + ROLL_GAP;
            return null;
        }
        nextRoll[best] = now + Math.max(ROLL_GAP, a.cooldown());
        chosen = reachable(a, reaches);
        return a;
    }

    /** The move the last answer given will be made with. */
    @Nullable String chosen() {
        return chosen;
    }

    private @Nullable String reachable(FightScripts.Answer a, IntPredicate reaches) {
        for (String id : a.moves()) {
            int i = moves.index(id);
            if (i >= 0 && reaches.test(i)) return id;
        }
        return null;
    }

    /** The next blow by what you're doing now, or null to leave the string as it stands. The newest thing you started wins. */
    @Nullable String forked(FightScripts.Fork f, long now) {
        long guard = f.guard().isPresent() ? seen(FightScripts.Fact.GUARD, 0, now) : -1;
        long dodged = f.dodged().isPresent() ? seen(FightScripts.Fact.DODGE, 0, now) : -1;
        if (guard < 0 && dodged < 0) return f.stayed().orElse(null);
        return dodged > guard ? f.dodged().get() : f.guard().get();
    }

    /** Whether the dice may pick this move: not the one it just made, and not while it rests. A move that hurts nobody always rests. */
    boolean allowed(int move, long now) {
        long waited = wanting < 0 ? 0 : now - wanting;
        boolean blow = moves.get(move).kind() != Strikes.Kind.NONE;
        if (move == lastMove) return blow && waited > REPEAT_AFTER;
        return now >= ready[move] || (blow && waited > STALE_TICKS);
    }

    /** How many times this fight a blow has followed itself as the last resort: the only way it's meant to. */
    int lastResorts() {
        return lastResorts;
    }

    /** Nothing it may start reaches you right now. */
    void nothingToStart(long now) {
        if (wanting < 0) wanting = now;
    }

    /** Whether this may follow what it just made at a fork or as an answer. */
    boolean follows(int move) {
        return move != lastMove;
    }

    void began(int move, long now) {
        if (move == lastMove && wanting >= 0 && now - wanting > REPEAT_AFTER) lastResorts++;
        lastMove = move;
        wanting = -1;
        ready[move] = now + cooldown(moves.get(move));
    }

    private int cooldown(KitMoves.Move m) {
        Integer own = mind.cooldowns().get(m.id());
        if (own != null) return own;
        return switch (m.kind()) {
            case LIGHT -> 0;
            case HEAVY -> 30;
            case PERILOUS, GRAB -> 80;
            case NONE -> 240;
        };
    }

    /**
     * How long this blow is held at its peak, or 0. Any class can be, now and then. Dodging three in a row, or having
     * deflected it over and over ({@code wary}), it waits for you. A hold only ever adds warning.
     */
    int hold(KitMoves.Move m, boolean wary, long now) {
        if (streak >= STREAK_CAUGHT && now - dodgedAt >= react && now - dodgedAt <= STREAK_GAP) {
            streak = 0;
            return Math.max(CAUGHT_HOLD, m.time().hold());
        }
        if (wary || (!sure && boss.getRandom().nextFloat() < mind.holdChance())) return Math.max(mind.holdTicks(), m.time().hold());
        return 0;
    }

    /** Whether a class can be told at all: a blow that hurts nobody has nothing to hold. */
    static boolean holds(Strikes.Kind cls) {
        return cls != Strikes.Kind.NONE;
    }
}
