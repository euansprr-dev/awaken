package dev.awaken.arms;

import dev.awaken.core.api.CreatureTag;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.api.Strikes;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Arms' rules as pure functions (SPEC §24), shared by the client's prediction and the server's judgement so both
 * decide the same move from the same press. Every number comes from the style's row ({@link Archetype}) or is named
 * here.
 */
public final class ArmsRules {
    private ArmsRules() {}

    /** Rhythm steps at most; each adds this much damage and one posture. */
    public static final int CADENCE_MAX = 3;
    public static final float CADENCE_DAMAGE = 0.08F;
    /** Resonance: a full meter releases the weapon's Art. */
    public static final int RESONANCE_MAX = 100, RESONANCE_DEFLECT = 20, RESONANCE_PERFECT_DODGE = 15, RESONANCE_BREAK = 25,
            RESONANCE_FINISHER = 6, RESONANCE_CADENCE3 = 12;
    /** Out of a fight the meter drains this much a second. */
    public static final float RESONANCE_DRAIN = 2F;
    /** Posture: what a stance holds, how fast it comes back after it is left alone. */
    public static final int POSTURE_NORMAL = 20, POSTURE_ELITE = 40, POSTURE_LORD = 100;
    public static final int POSTURE_IDLE_TICKS = 40;
    public static final float POSTURE_REGEN = 6F / 20F, POSTURE_REGEN_LORD = 8F / 20F;
    /** A broken stance: how long it lasts, and how long before it can break again. */
    public static final int BROKEN_TICKS = 50, BROKEN_TICKS_LORD = 40, BREAK_COOLDOWN = 120;
    /** Posture a deflect does to the attacker, by the blow's class. */
    public static final int DEFLECT_POSTURE_LIGHT = 18, DEFLECT_POSTURE_HEAVY = 26;
    /** The latency hold on a guardable hit is at most this many ticks. */
    public static final int GRACE_MAX = 3;
    /** Soft focus: how far a strike may turn toward its target. */
    public static final float FOCUS_TURN = 15F;
    /** Hit-stop in a sweep: at most one freeze per this many ticks. */
    public static final int HITSTOP_GAP = 4;

    // ---- the chain ---------------------------------------------------------------------------------------------------

    /**
     * What a tap of the strike key does. step: index in the chain; startAt: absolute tick the move begins (now, or a
     * buffered press waiting for the window); cadence: the rhythm count after this press.
     */
    public record Decision(Archetype.Move move, int step, long startAt, int cadence) {}

    /**
     * Decides the strike a tap starts. {@code current} is the move in progress (null when idle), started at
     * {@code start}; {@code step} its place in the chain (-1 when it isn't a chain strike); {@code now} the press.
     * Returns null when the press is too early to count (mashing far ahead of the window).
     */
    public static @Nullable Decision tap(Archetypes.Loaded a, Archetype.@Nullable Move current, long start, int step, int cadence, long now,
                                         int masterySteps) {
        Archetype.MoveDef cur = current == null ? null : a.move(current);
        int chainLen = chainLength(a, masterySteps);
        if (cur == null || now >= start + cur.total() + a.def().late()) return new Decision(chainMove(a, 0), 0, now, 0);
        long rel = now - start;
        boolean chainStrike = step >= 0;
        int next = chainStrike && step + 1 < chainLen ? step + 1 : 0;
        if (chainStrike && next != 0 && cur.hasCadence()) {
            int c0 = cur.cadence().get(0), c1 = cur.cadence().get(1);
            if (rel >= c0 && rel <= c1) return new Decision(chainMove(a, next), next, now, Math.min(CADENCE_MAX, cadence + 1));
            if (rel < c0) return c0 - rel <= a.def().buffer() ? new Decision(chainMove(a, next), next, start + c0, 0) : null;
            return new Decision(chainMove(a, next), next, now, 0);
        }
        // a finisher, a heavy, a riposte: the next tap starts the chain again once it has recovered
        long end = start + cur.total();
        if (now >= end) return new Decision(chainMove(a, 0), 0, now, 0);
        return end - now <= a.def().buffer() ? new Decision(chainMove(a, 0), 0, end, 0) : null;
    }

    static int chainLength(Archetypes.Loaded a, int masterySteps) {
        return a.chain().size() + Math.min(masterySteps, a.masteryChain().size());
    }

    static Archetype.Move chainMove(Archetypes.Loaded a, int step) {
        return step < a.chain().size() ? a.chain().get(step) : a.masteryChain().get(step - a.chain().size());
    }

    /** Damage multiplier of a move at this rhythm count (and a heavy's release). */
    public static float motion(Archetype.MoveDef m, int cadence, boolean perfectHeavy) {
        float base = perfectHeavy && m.charge().isPresent() ? m.charge().get().glintMotion() : m.motion();
        return base * (1 + CADENCE_DAMAGE * cadence);
    }

    public static int posture(Archetype.MoveDef m, int cadence, boolean perfectHeavy) {
        int base = perfectHeavy && m.charge().isPresent() ? m.charge().get().glintPosture() : m.posture();
        return base + cadence;
    }

    /** Recovery scaled by attack speed (AGI, Flow): faster arms recover sooner; wind-up and active stay as authored. */
    public static int recovery(Archetype.MoveDef m, Archetypes.Loaded a, double attackSpeed) {
        double scale = Mth.clamp(attackSpeed / a.def().baseSpeed(), 0.6, 1.6);
        return Math.max(2, (int) Math.round(m.recovery() / scale));
    }

    // ---- defence -----------------------------------------------------------------------------------------------------

    public enum Answer { DEFLECT, BLOCK, GUARD_BREAK, PASS }

    /**
     * How a guard answers a blow. sinceRaise: ticks the guard has been up when the blow is judged; held: ticks the
     * blow waited in the latency grace; grace: that grace; lockedOut: this raise follows a failed deflect.
     */
    public static Answer guard(Archetype.Guard g, Strikes.Kind kind, int sinceRaise, int held, int grace, boolean lockedOut, boolean inArc) {
        if (!inArc || !kind.blockable()) return Answer.PASS;
        int rel = sinceRaise - held; // ticks between the raise and the blow as the player saw it
        if (kind.deflectable() && !lockedOut && rel >= -grace && rel < g.deflect()) return Answer.DEFLECT;
        return kind == Strikes.Kind.HEAVY ? Answer.GUARD_BREAK : Answer.BLOCK;
    }

    /** The latency grace for a player: half the round trip, in ticks, at most {@link #GRACE_MAX}; none on a near connection. */
    public static int grace(int latencyMs) {
        if (latencyMs < 50) return 0;
        return Mth.clamp(Math.round(latencyMs / 2F / 50F), 0, GRACE_MAX);
    }

    /** A dodge evades a blow on these ticks of its run; the first {@code perfect} of them are perfect. */
    public static boolean evades(Archetype.Dodge d, int sinceDodge, Strikes.Kind kind) {
        return kind.dodgeable() && sinceDodge >= 0 && sinceDodge < d.evade();
    }

    public static boolean perfect(Archetype.Dodge d, int sinceDodge) {
        return sinceDodge >= 0 && sinceDodge < d.perfect();
    }

    /** The dodge's speed on each tick of its run (blocks a tick): a fast start easing out, the whole run = distance. */
    public static double dodgeSpeed(Archetype.Dodge d, int tick) {
        if (tick < 0 || tick >= d.ticks()) return 0;
        // weights 1 - t/ticks, normalised so the run covers the distance
        double n = d.ticks();
        double sum = n * (n + 1) / (2 * n);
        return d.distance() * (1 - tick / n) / sum;
    }

    // ---- posture -----------------------------------------------------------------------------------------------------

    /** What a creature's stance holds, by what the band says it is. */
    public static int postureMax(CreatureTag tag, MobRole.Role role) {
        if (role == MobRole.Role.BOSS || role == MobRole.Role.BREAK || role == MobRole.Role.NIGHTMARE || tag.kind() == CreatureTag.Kind.BOSS) return POSTURE_LORD;
        if (role == MobRole.Role.ELITE || role == MobRole.Role.WARDEN || tag.elite() || tag.kind() == CreatureTag.Kind.WARDEN
                || tag.kind() == CreatureTag.Kind.BEAST) return POSTURE_ELITE;
        return POSTURE_NORMAL;
    }

    public static boolean lordly(int postureMax) {
        return postureMax >= POSTURE_LORD;
    }

    /** An Execution's damage on a Lord is capped at this share of its health (the move's bossCap). */
    public static float capExecution(float damage, float maxHealth, float bossCap, boolean lord) {
        return lord && bossCap > 0 ? Math.min(damage, maxHealth * bossCap) : damage;
    }

    /** Whether a strike may turn toward its focus: the turn needed, clamped, in degrees (0 when beyond the help). */
    public static float focusTurn(float yawToTarget, float lookYaw) {
        float d = Mth.wrapDegrees(yawToTarget - lookYaw);
        return Math.abs(d) <= FOCUS_TURN * 2 ? Mth.clamp(d, -FOCUS_TURN, FOCUS_TURN) : 0;
    }

    /** Whether a point at this horizontal angle off the look is inside a move's arc. */
    public static boolean inArc(float degreesOff, float arc) {
        return Math.abs(Mth.wrapDegrees(degreesOff)) <= arc / 2;
    }
}
