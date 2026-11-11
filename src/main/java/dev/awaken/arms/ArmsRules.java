package dev.awaken.arms;

import dev.awaken.core.api.CreatureTag;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.api.Strikes;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * Arms' rules as pure functions (SPEC §25), shared by the client's prediction and the server's judgement so both
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
    /**
     * The duel's payoff (SPEC §47.3): a perfect dodge through a blow fills the attacker's stance this much; a blow in an
     * opening fills it this many times as fast (a shot sent back fills its maker's as the mobs module says); a blow on a
     * duel body standing braced (no opening, not a riposte) only this share: its stance falls to deflects and punishes.
     */
    public static final int PERFECT_DODGE_POSTURE = 10;
    public static final float OPENING_POSTURE = 1.5F;
    public static final float GUARDED_POSTURE = 0.5F;
    /** A finisher (an Execution) on a broken Lord: at least this share of its health, at most that one. */
    public static final float LORD_FINISHER_MIN = 0.10F, LORD_FINISHER_MAX = 0.15F;
    /**
     * The same on a duel body (SPEC §47.3 "Breaks a fight is made of"): its stance breaks two or three times a minute in a
     * fight of three or four, so each break takes a smaller share and the fight stays its length.
     */
    public static final float DUEL_FINISHER_MIN = 0.07F, DUEL_FINISHER_MAX = 0.10F;
    /** The latency hold on a guardable hit is at most this many ticks. */
    public static final int GRACE_MAX = 3;
    /** Soft focus: how far a strike may turn toward its target; looking further than this away for so long drops it. */
    public static final float FOCUS_TURN = 15F, FOCUS_DROP_ANGLE = 60F;
    public static final int FOCUS_DROP_TICKS = 20;
    /** A mastered finisher's link: the mastery strike may start this many ticks after the finisher's blow ends. */
    public static final int MASTERY_LINK = 2;
    /** Hit-stop in a sweep: at most one freeze per this many ticks. */
    public static final int HITSTOP_GAP = 4;
    /**
     * Styles whose strike lands on the press even while it is still down, its charge drawn after the blow. None since
     * Euan 2026-11-09 ("it still sometimes attacks once before starting the charge"): the Daggers were here from the
     * 2026-11-02 audit, and name them again to have that back (a dagger's click then lands 40 to 85 ms sooner, SPEC §25
     * "Tap or hold"). Every other press waits unseen under the draw until it is a tap or the charge.
     */
    private static final java.util.Set<String> STRIKE_ON_PRESS = java.util.Set.of();

    /** Whether a strike begun by a press still down waits on that press (false: it lands as it comes). */
    public static boolean waitsOnHold(String style) {
        return !STRIKE_ON_PRESS.contains(style);
    }

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
        return tap(a, current, start, step, cadence, now, masterySteps, a.def().buffer());
    }

    /** As above; {@code buffer}: how many ticks ahead of its window a press may come and still be kept for it. */
    public static @Nullable Decision tap(Archetypes.Loaded a, Archetype.@Nullable Move current, long start, int step, int cadence, long now,
                                         int masterySteps, int buffer) {
        Archetype.MoveDef cur = current == null ? null : a.move(current);
        int chainLen = chainLength(a, masterySteps);
        if (cur == null || now >= start + cur.total() + a.def().late()) return new Decision(chainMove(a, 0), 0, now, 0);
        long rel = now - start;
        boolean chainStrike = step >= 0;
        int next = chainStrike && step + 1 < chainLen ? step + 1 : 0;
        if (chainStrike && next != 0 && cur.hasCadence()) {
            int c0 = cur.cadence().get(0), c1 = cur.cadence().get(1);
            if (rel >= c0 && rel <= c1) return new Decision(chainMove(a, next), next, now, Math.min(CADENCE_MAX, cadence + 1));
            if (rel < c0) return c0 - rel <= buffer ? new Decision(chainMove(a, next), next, start + c0, 0) : null;
            return new Decision(chainMove(a, next), next, now, 0);
        }
        if (chainStrike && next != 0) {
            // a finisher that mastery carries on: a press from the end of its blow chains into the mastery strike (its
            // recovery is the window); a chain in full rhythm stays in it
            long link = start + cur.windup() + cur.active() + MASTERY_LINK;
            int carried = cadence >= step ? Math.min(CADENCE_MAX, cadence + 1) : cadence;
            if (now >= link) return new Decision(chainMove(a, next), next, now, carried);
            return link - now <= buffer ? new Decision(chainMove(a, next), next, link, carried) : null;
        }
        // a finisher, a heavy, a riposte: the next tap starts the chain again once it has recovered
        long end = start + cur.total();
        if (now >= end) return new Decision(chainMove(a, 0), 0, now, 0);
        return end - now <= buffer ? new Decision(chainMove(a, 0), 0, end, 0) : null;
    }

    /** Ticks after the last chain strike that a client's word can still carry the chain on. */
    static final int CHAIN_SLACK = 60;

    /**
     * The buffer for a strike the hands have begun: it is kept however early the server's own clock calls it. It still
     * waits for the server's window, so nothing strikes faster than the rules allow.
     */
    static final int HEARD_BUFFER = Integer.MAX_VALUE;

    /**
     * One chain (SPEC §25 "Tap or hold"; Euan 2026-11-09: "the combo stops being the usual one", "two uppercuts in a row").
     * The two sides time the windows on their own clocks, so left to decide alone they disagree on a window's edge, and one
     * counts a strike the other never played. Only the hands decide: the server plays the strike they began (starting
     * again, or the strike after the last one, soon after it) and falls back on its own read only for a claim no chain allows.
     */
    public static @Nullable Decision follow(Archetypes.Loaded a, @Nullable Decision d, int claimed, int lastStep, long sinceLast, int masterySteps) {
        if (d == null || claimed < 0 || claimed == d.step() || claimed >= chainLength(a, masterySteps)) return d;
        if (claimed != 0 && (claimed != lastStep + 1 || sinceLast > CHAIN_SLACK)) return d;
        return new Decision(chainMove(a, claimed), claimed, d.startAt(), d.cadence());
    }

    /** A chain strike's place in its chain, mastery's strikes after it; -1 if it isn't one of this style's. */
    public static int stepOf(Archetypes.Loaded a, Archetype.Move m) {
        int i = a.chain().indexOf(m);
        if (i >= 0) return i;
        int j = a.masteryChain().indexOf(m);
        return j < 0 ? -1 : a.chain().size() + j;
    }

    static int chainLength(Archetypes.Loaded a, int masterySteps) {
        return a.chain().size() + Math.min(masterySteps, a.masteryChain().size());
    }

    static Archetype.Move chainMove(Archetypes.Loaded a, int step) {
        return step < a.chain().size() ? a.chain().get(step) : a.masteryChain().get(step - a.chain().size());
    }

    /**
     * A blow from behind (the Daggers' backstab): the striker stands more than 120° round from where the struck body
     * faces (its body yaw, and the yaw from it to the striker; degrees, Minecraft's convention).
     */
    public static boolean behind(float bodyYaw, float yawToStriker) {
        return Math.abs(Mth.wrapDegrees(yawToStriker - bodyYaw)) > 120F;
    }

    /** The Katana's draw: a chain's first strike after the blade rested {@code idle} ticks (the style's drawIdle; 0: none). */
    public static boolean drawn(Archetype.Traits t, Archetype.Move move, long idle) {
        return t.drawIdle() > 0 && move == Archetype.Move.STRIKE_1 && idle >= t.drawIdle();
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

    /**
     * What a landed blow does to a stance (SPEC §47.3): more in an opening; on a duel body standing braced only
     * {@link #GUARDED_POSTURE} of it, unless it is a riposte (the punish for a deflect).
     */
    public static int hitPosture(int posture, boolean opening, boolean riposte, boolean duelist) {
        float share = opening ? OPENING_POSTURE : riposte || !duelist ? 1F : GUARDED_POSTURE;
        return Math.round(posture * share);
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
        return guard(g, kind, sinceRaise, held, grace, lockedOut, inArc, false);
    }

    /**
     * An untold blow (a creature with no wind-up: it lands the moment it's in reach, a zombie's, a spider's) is deflected
     * by a guard raised up to {@link #UNTOLD_DEFLECT} ticks earlier than a told one's window allows (Euan 2026-10-26:
     * "you don't know when it's going to hit you... so it's very hard to time a parry"); a told blow keeps the tight
     * window, so the skill stays in reading a tell. An untold deflect is worth {@link #UNTOLD_REWARD} of a told one.
     */
    public static final int UNTOLD_DEFLECT = 4;
    public static final float UNTOLD_REWARD = 0.6F;

    public static Answer guard(Archetype.Guard g, Strikes.Kind kind, int sinceRaise, int held, int grace, boolean lockedOut, boolean inArc,
                               boolean untold) {
        if (!inArc || !kind.blockable()) return Answer.PASS;
        int rel = sinceRaise - held; // ticks between the raise and the blow as the player saw it
        int window = g.deflect() + (untold ? UNTOLD_DEFLECT : 0);
        if (kind.deflectable() && !lockedOut && rel >= -grace && rel < window) return Answer.DEFLECT;
        return kind == Strikes.Kind.HEAVY ? Answer.GUARD_BREAK : Answer.BLOCK;
    }

    /**
     * Why a guard that met a deflectable blow only blocked it (or broke), sent in the BLOCK and GUARD_BREAK replies' aux
     * so the ring can say so (SPEC §68.6): raised too early, raised again too soon after lowering it (the lockout), or
     * raised after the blow as the player saw it.
     */
    public static final int MISS_EARLY = 0, MISS_TOO_SOON = 1, MISS_LATE = 2;

    public static int missWhy(Archetype.Guard g, int sinceRaise, int held, int grace, boolean lockedOut, boolean untold) {
        int rel = sinceRaise - held;
        if (rel < -grace) return MISS_LATE;
        if (lockedOut && rel < g.deflect() + (untold ? UNTOLD_DEFLECT : 0)) return MISS_TOO_SOON;
        return MISS_EARLY;
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

    // ---- the clash (SPEC §25 "The clash"; the boss plan's part three) -----------------------------------------------

    /** A deflect's size, sent in its answer's aux: a light blow turned aside, a heavy one, the one that broke its stance. */
    public static final int CLASH_LIGHT = 0, CLASH_HEAVY = 1, CLASH_BREAK = 2;

    /**
     * What a deflect of each size does to the fighter (client): the freeze on the hands and the struck body (ms), the
     * view's kick (degrees up and of roll, settling over {@code kickTicks}), the view's shake (degrees, over
     * {@code shakeTicks}), the field of view's snap (a fraction, over {@code fovTicks}), the white-gold light's size (0:
     * none) and the frame's edges flashing. {@link #TODAY} is the deflect before it, kept for the switch.
     */
    public record Clash(int freezeMs, float kickPitch, float kickRoll, int kickTicks, float shake, int shakeTicks, float fov, int fovTicks,
                        float light, boolean edges) {}

    public static final Clash TODAY = new Clash(100, 1.8F, 1.2F, 7, 0F, 0, -0.035F, 9, 0F, false);
    private static final Clash[] CLASHES = {
            new Clash(110, 2.6F, 1.6F, 7, 0.35F, 4, -0.045F, 9, 1.9F, false),
            new Clash(160, 4.0F, 2.4F, 8, 0.55F, 6, -0.06F, 11, 2.4F, false),
            new Clash(200, 4.5F, 2.8F, 9, 0.75F, 8, -0.08F, 14, 2.4F, true)};

    /** The clash of this size (CLASH_*); anything else is {@link #TODAY}. */
    public static Clash clash(int size) {
        return size >= 0 && size < CLASHES.length ? CLASHES[size] : TODAY;
    }

    /** The size of a deflect: the blow that broke its stance outranks a heavy, a heavy outranks a light. */
    public static int clashSize(Strikes.Kind kind, boolean broke) {
        return broke ? CLASH_BREAK : kind == Strikes.Kind.HEAVY ? CLASH_HEAVY : CLASH_LIGHT;
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

    /** What a call of the attack key does with a fighting weapon in hand (SPEC §40 "One press is one press"). */
    public enum Attack {
        /** A new press: the chain's strike, wherever it points. */
        STRIKE,
        /** Left to vanilla: it mines the block. */
        MINE,
        /** The key still down from a strike's press: swallowed (the strike's hold, a heavy's charge). */
        HELD
    }

    /** Ticks after the last thing that meant a fight that the attack key never mines a block. */
    public static final int FIGHT_TICKS = 100;

    /**
     * What the attack key does: a hold goes on as its press began (a strike's hold stays the strike's, a mining hold
     * keeps mining whatever the crosshair slides onto); a new press on a block the weapon cuts faster than a hand mines
     * it once nothing has meant a fight for {@link #FIGHT_TICKS}, and strikes otherwise.
     */
    public static Attack attack(boolean strikeHeld, boolean mineHeld, long sinceFight, boolean cuts) {
        if (strikeHeld) return Attack.HELD;
        if (mineHeld || (cuts && sinceFight > FIGHT_TICKS)) return Attack.MINE;
        return Attack.STRIKE;
    }

    /**
     * Whether a press means a fight, opening the window that keeps the key from mining: a lock, a creature under the
     * crosshair, or the mark within {@code reach} ({@link #markReach}). A press at the air, leaves or a log does not
     * (Euan 2026-10-29: "I can't use a normal axe to break trees because it charges the hit like an attack").
     */
    public static boolean meansFight(boolean locked, boolean underCrosshair, double markGap, double reach) {
        return locked || underCrosshair || markGap <= reach;
    }

    /** How near the mark must be for a press at it to mean a fight: the style's longest blow (not an Art's wave) and its step in. */
    public static double markReach(Archetypes.Loaded style) {
        float most = 0;
        for (Archetype.MoveDef d : style.moves().values()) most = Math.max(most, d.reach());
        return most + 0.3 + dev.awaken.core.api.Aim.stepIn(style.id());
    }

    /** A fall this long (blocks) when the strike key is pressed arms a Plunge. */
    public static final float PLUNGE_FALL = 2F;

    /** A strike pressed in the air in a real fall (not a hop) arms a Plunge. */
    public static boolean plunges(boolean onGround, double fallDistance, boolean inLiquid) {
        return !onGround && !inLiquid && fallDistance >= PLUNGE_FALL;
    }

    /** A Plunge lands harder the further the fall: ×1.33 from 2 blocks, ×2 from 6, at most ×2.8. */
    public static float plungePower(double fell) {
        return (float) Math.min(2.8, 1 + Math.max(0, fell) / 6);
    }

    /** The Art: a heavy held through to its full charge (2 ticks' grace for the trip) with the meter full. */
    public static boolean artReady(int held, Archetype.Charge c, float resonance) {
        return artReady(held, c, resonance, RESONANCE_MAX);
    }

    /** The same, with the meter a mastered style asks for ({@link ArmsMastery#artAt}). */
    public static boolean artReady(int held, Archetype.Charge c, float resonance, int needs) {
        return held >= c.max() - 2 && resonance >= needs;
    }

    /** A stance left alone: what is left of {@code value} after {@code sinceHit} ticks untouched (both sides). */
    public static float drained(float value, long sinceHit, boolean lord) {
        long idle = sinceHit - POSTURE_IDLE_TICKS;
        return idle <= 0 ? value : Math.max(0, value - idle * (lord ? POSTURE_REGEN_LORD : POSTURE_REGEN));
    }

    /** Whether the fighter is still looking toward their focus (else it drops after {@link #FOCUS_DROP_TICKS}). */
    public static boolean lookedAt(float yawToTarget, float lookYaw) {
        return Math.abs(Mth.wrapDegrees(yawToTarget - lookYaw)) <= FOCUS_DROP_ANGLE;
    }

    /**
     * An Execution on a Lord (a move with a bossCap) is the finisher (SPEC §47.3): between {@link #LORD_FINISHER_MIN} and
     * {@link #LORD_FINISHER_MAX} of its health, whatever the weapon, so each break is worth a tenth to a seventh of the fight;
     * on a duel body, which breaks twice as often, {@link #DUEL_FINISHER_MIN} to {@link #DUEL_FINISHER_MAX}.
     */
    public static float capExecution(float damage, float maxHealth, float bossCap, boolean lord, boolean duel) {
        if (!lord || bossCap <= 0) return damage;
        return duel ? Mth.clamp(damage, maxHealth * DUEL_FINISHER_MIN, maxHealth * DUEL_FINISHER_MAX)
                : Mth.clamp(damage, maxHealth * LORD_FINISHER_MIN, maxHealth * LORD_FINISHER_MAX);
    }

    /** Whether a strike may turn toward its focus: the turn needed, clamped, in degrees (0 when beyond the help). */
    public static float focusTurn(float yawToTarget, float lookYaw) {
        float d = Mth.wrapDegrees(yawToTarget - lookYaw);
        return Math.abs(d) <= FOCUS_TURN * 2 ? Mth.clamp(d, -FOCUS_TURN, FOCUS_TURN) : 0;
    }

    // ---- the blow (SPEC §25 "Fight Feel") -------------------------------------------------------------------------

    /** A style's weight: the blow's weight on the struck body and its recoil's shove (blocks). */
    public enum Heft {
        SNAP(0.6F, 0.04F), BLADE(1F, 0.06F), HEAVY(1.5F, 0.09F);

        public final float weight, shove;

        Heft(float weight, float shove) {
            this.weight = weight;
            this.shove = shove;
        }
    }

    /** As the swings are weighed (anim_lab WEIGHT_OF): Daggers, Rapier, Gauntlets snap; the big weapons heavy. */
    public static Heft heft(@Nullable String style) {
        if (style == null) return Heft.BLADE;
        return switch (style) {
            case "daggers", "rapier", "gauntlets" -> Heft.SNAP;
            case "axe", "maul", "greatblade", "glaive", "scythe", "warstaff" -> Heft.HEAVY;
            default -> Heft.BLADE;
        };
    }

    /** A finisher, a perfect heavy or an Execution weighs this much more. */
    public static final float STRONG_WEIGHT = 1.3F;
    /** A thrust pushes straight away this much harder; a pinning blow this much softer, with no lift. */
    public static final float THRUST_PUSH = 1.15F, PIN_PUSH = 0.7F, BASH_PUSH = 2.6F;
    /** An uppercut or a rise lifts the body this much more (blocks a tick). */
    public static final float LIFT = 0.18F;
    /** A side cut's push: this share along the blade's travel, the rest away from the striker (never toward). */
    public static final float CUT_ALONG = 0.45F;
    /** A killing blow throws the body this much further along the blow. */
    public static final float KILL_PUSH = 1.6F;
    /** A kill's freeze grows by this much, to at most the cap; each extra body in one sweep by this, up to 3 of them. */
    public static final int KILL_HITSTOP = 40, HITSTOP_CAP = 160, CLEAVE_HITSTOP = 12, CLEAVE_EXTRA_MAX = 3;
    /** The last foe: no other hostile hunting the striker within this many blocks. */
    public static final double LAST_RANGE = 16;

    /** A body this big (max health) keeps vanilla's push: a Lord is neither reeled, thrown nor turned. */
    public static boolean lordBody(float maxHealth) {
        return maxHealth >= 150;
    }

    /** A blow straight in (a stab, a jab, a bash, any thrust): it pushes straight away, with no side. */
    public static boolean thrusts(Archetype.MoveDef def) {
        String f = def.swing().form();
        return def.swish() == Archetype.Swish.THRUST || f.equals("stab") || f.equals("jab") || f.equals("bash");
    }

    /** -1: the blow pins (a chop, a slam, an overhead); +1 it lifts (an uppercut, a rise); 0 level. */
    public static int lift(Archetype.MoveDef def) {
        return switch (def.swing().form()) {
            case "chop", "slam", "over" -> -1;
            case "upper", "rise" -> 1;
            default -> 0;
        };
    }

    /**
     * The cut's sideways share on the screen: -1 a level cut right to left, +1 left to right, 0 straight down or a
     * thrust. A left-handed striker's screen is mirrored.
     */
    public static float side(Archetype.MoveDef def, boolean leftHanded) {
        if (thrusts(def)) return 0;
        float dx = blowX(def), dy = blowY(def);
        float s = Mth.clamp(dx / Math.max(0.05F, Math.abs(dx) + Math.abs(dy)), -1, 1);
        return leftHanded ? -s : s;
    }

    /**
     * The way a cut travels on the screen at its hit, to the right (and {@link #blowY} up; together not a unit): the baked
     * blade's own ({@code swing.blow}, tools/arms/blows.py), else the row's old hand poses. The poses stopped matching the
     * blade on 28 cuts (SPEC §25 "Weight in the Hand": the Maul's second strike threw a body against the cut), so
     * everything that answers a blow reads this one direction.
     */
    public static float blowX(Archetype.MoveDef def) {
        Archetype.Swing sw = def.swing();
        return sw.hasBlow() ? sw.blow().get(0) : sw.to().get(0) - sw.from().get(0);
    }

    public static float blowY(Archetype.MoveDef def) {
        Archetype.Swing sw = def.swing();
        return sw.hasBlow() ? sw.blow().get(1) : sw.to().get(1) - sw.from().get(1);
    }

    /**
     * The push's flat direction, into {@code out} (x, z): away from the striker along (ax, az) (a unit vector, striker
     * to struck), turned along the cut by {@link #CUT_ALONG} of its side share. At a sweep's middle the blade travels
     * across that line, so a full side cut pushes 55% away and 45% along it; never back toward the striker.
     */
    public static void push(double ax, double az, float side, float[] out) {
        // the striker's right, seen along (ax, az): (-az, ax)
        double x = ax * (1 - CUT_ALONG * Math.abs(side)) - az * CUT_ALONG * side;
        double z = az * (1 - CUT_ALONG * Math.abs(side)) + ax * CUT_ALONG * side;
        double n = Math.sqrt(x * x + z * z);
        out[0] = n < 1e-6 ? (float) ax : (float) (x / n);
        out[1] = n < 1e-6 ? (float) az : (float) (z / n);
    }

    /** How hard a move pushes against vanilla's strength. */
    public static float pushScale(Archetype.MoveDef def) {
        if (def.swing().form().equals("bash")) return BASH_PUSH; // a shove throws its target back (Euan 2026-11-09: "it doesn't feel like a push")
        if (thrusts(def)) return THRUST_PUSH;
        return lift(def) < 0 ? PIN_PUSH : 1F;
    }

    /**
     * A blow's freeze on the server: the move's, and 12 ms for each extra body one sweep strikes in the same freeze (up
     * to 3). {@code bodies}: the freeze's bodies so far, this one included.
     */
    public static int hitstop(int base, int bodies) {
        return base + CLEAVE_HITSTOP * Mth.clamp(bodies - 1, 0, CLEAVE_EXTRA_MAX);
    }

    /** A kill's freeze, on the client that sees it (its switch is the viewer's): 40 ms more, to at most 160; never shorter. */
    public static int killHitstop(int ms) {
        return ms <= 0 ? 0 : Math.max(ms, Math.min(HITSTOP_CAP, ms + KILL_HITSTOP));
    }

    /** The struck body's recoil spring: ω 18 rad/s, ζ 0.45 (its back-swing a fifth of the shove; settled in ~0.35 s). */
    public static final float RECOIL_OMEGA = 18F, RECOIL_ZETA = 0.45F;
    private static final double RECOIL_DECAY = RECOIL_ZETA * RECOIL_OMEGA, RECOIL_WD = RECOIL_OMEGA * Math.sqrt(1 - RECOIL_ZETA * RECOIL_ZETA);
    private static final double RECOIL_PEAK;

    static {
        double tp = Math.atan2(RECOIL_WD, RECOIL_DECAY) / RECOIL_WD;
        RECOIL_PEAK = Math.exp(-RECOIL_DECAY * tp) * Math.sin(RECOIL_WD * tp);
    }

    /**
     * The recoil's travel {@code t} seconds after the freeze lets it go, as a share of its peak: 0 before, out to 1 at
     * about 70 ms, back past rest by a fifth, and settled.
     */
    public static float recoil(float t) {
        if (t <= 0) return 0;
        return (float) (Math.exp(-RECOIL_DECAY * t) * Math.sin(RECOIL_WD * t) / RECOIL_PEAK);
    }

    /** Whether a point at this horizontal angle off the look is inside a move's arc. */
    public static boolean inArc(float degreesOff, float arc) {
        return Math.abs(Mth.wrapDegrees(degreesOff)) <= arc / 2;
    }

    // ---- the sweep (SPEC §47.1 "Hits land now") ----------------------------------------------------------------------

    /**
     * The client's sweep stays this far inside the server's (blocks of reach, degrees of arc): a creature the client
     * strikes is one the server strikes too, though it moved for up to a tick and a ping since (the misses counted:
     * under 1 in 50 in arms_hit_now). A creature on the edge waits for the server's word, as every hit did before.
     */
    public static final float PREDICT_REACH = 0.25F, PREDICT_ARC = 12F;

    /**
     * Whether one active tick of a move strikes a body with this box: the one rule of the server's sweep and the
     * client's prediction. {@code eye}: the striker's eye; {@code feetY}: its feet; {@code yaw}: the arc's middle;
     * {@code reach}: the move's reach now (the meant creature's own, step in and all, when {@code meant});
     * {@code aimed}: the press meant it (the arc doesn't matter); {@code meant}: aimed and not a person (nor does the
     * band of height).
     */
    public static boolean sweeps(net.minecraft.world.phys.Vec3 eye, double feetY, net.minecraft.world.phys.AABB box, float yaw, float arc,
                                 double reach, boolean aimed, boolean meant) {
        if (dev.awaken.core.api.Aim.gap(eye, box) > reach) return false;
        double cx = (box.minX + box.maxX) * 0.5, cz = (box.minZ + box.maxZ) * 0.5;
        float toward = (float) (Mth.atan2(cz - eye.z, cx - eye.x) * Mth.RAD_TO_DEG) - 90F;
        if (!aimed && !inArc(toward - yaw, arc)) return false;
        return meant || !(box.maxY < feetY - 1.0 || box.minY > eye.y + 1.5);
    }

    /**
     * The furthest a press's creature may be and still be the one it meant (SPEC §40): the style's longest reach, the
     * blade's growth over a full chain, and the step in. The server checks a press's creature with it; the client
     * names the same creature with it before it predicts.
     */
    public static double pressReach(Archetypes.Loaded style) {
        float most = 0;
        for (Archetype.MoveDef d : style.moves().values()) most = Math.max(most, d.art().isPresent() ? Math.max(d.reach(), d.art().get().range()) : d.reach());
        return most + 0.3 + style.def().traits().reachPerStep() * 5 + dev.awaken.core.api.Aim.stepIn(style.id());
    }

    /** The same rule as the client asks it: {@link #PREDICT_REACH} and {@link #PREDICT_ARC} inside the server's. */
    public static boolean predicts(net.minecraft.world.phys.Vec3 eye, double feetY, net.minecraft.world.phys.AABB box, float yaw, float arc,
                                   double reach, boolean aimed, boolean meant) {
        return sweeps(eye, feetY, box, yaw, Math.max(0F, arc - PREDICT_ARC), reach - PREDICT_REACH, aimed, meant);
    }
}
