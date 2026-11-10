package dev.awaken.arms.client;

import dev.awaken.arms.Archetype;
import dev.awaken.arms.Archetypes;
import dev.awaken.arms.Arms;
import dev.awaken.arms.ArmsConfig;
import dev.awaken.arms.ArmsMastery;
import dev.awaken.arms.ArmsRules;
import dev.awaken.arms.Blow;
import dev.awaken.arms.ChargeDraw;
import dev.awaken.arms.FpSwings;
import dev.awaken.arms.net.ArmsPayloads;
import dev.awaken.body.Body;
import dev.awaken.core.api.CombatFeedback;
import dev.awaken.core.api.MovementHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The client's half of Arms (SPEC §25): it reads the three verbs, predicts the move with the same rules the server
 * judges by ({@link ArmsRules}), starts it on the frame of the press, and takes the server's word when it comes back.
 * Strike: the attack key (tap = the chain, held = a heavy). Guard: the use key (vanilla's blocking, judged by the
 * server). Dodge: the sprint key with back, left, right or no direction, on the ground.
 */
public final class ArmsPredict {
    private ArmsPredict() {}

    /** The client's own tick count (moves are timed on it, the server's clock judges them). */
    static int ticks;
    static int seq;
    // the move running
    static Archetype.@Nullable Move move;
    static Archetypes.@Nullable Loaded style;
    static int moveStart, step = -1, cadence, recovery;
    /** Wall-clock start of the move, and the hold time at that start (the hands' clock skips hit-stop). */
    static long moveNanos, movePaused;
    static ArmsRules.@Nullable Decision buffered;
    static int bufferedSeq;
    /** A refused move fades out instead of snapping: the tick it started fading. */
    static int fadeFrom = -100;
    // the strike key held into a heavy
    static boolean pressHeld, charging;
    static int heldTicks, chargeStart;
    /**
     * The press is still down and may become a charge (holdOpen), and the strike it began waits unseen under the draw
     * until the press ends as a tap or becomes the charge (waitHit): a held press never shows a light attack
     * (ChargeDraw; Euan 2026-11-09), and a tap launches its strike from wherever the hands have got to.
     */
    static boolean holdOpen, waitHit;
    /** The waiting strike's swish, arc, step in and body clip, held back until the press is a tap (a hold never shows them). */
    static boolean waitShown;
    /** This press's draw has begun (its body clip with it), and when, on the hands' clock. */
    private static boolean drawSeen;
    private static long drawFrom;
    /** When this press went down, on the hands' clock. */
    private static long pressNanos;
    /** What the hands were doing when a press began that may yet be a hold: they go on with it until the press has said which. */
    private static Archetype.@Nullable Move underMove;
    private static float underAt, underEnd;
    private static long underFrom;
    static long underSource;
    // what the server told us
    static int deflectAt = -1000, dodgeAt = -1000, resonance;
    /** Each style's mastery step, by its index (ArmsMastery; the server sends them). */
    static int[] mastery = new int[0];
    static final Map<Integer, Integer> BROKEN = new HashMap<>();
    // dodge
    static boolean dodging;
    static int dodgeTick;
    static double dodgeX, dodgeZ;
    static int dodgeCharges = -1, dodgeRechargeAt;
    private static boolean sprintWas;
    /**
     * The last tick something meant a fight (SPEC §40 "One press is one press"): a press at a creature
     * ({@link ArmsRules#meansFight}), a blow either way, a guard's answer, a dodge. Until it (or a blow on us) is
     * {@link ArmsRules#FIGHT_TICKS} old the attack key mines no block. A press at the air or a block leaves it be.
     */
    static int fightAt = -1000;
    /** The last strike pressed, wherever it pointed. */
    static int swungAt = -1000;

    // the hit at once (SPEC §47.1): the blows this client played before the server's word, the running move's press,
    // the creature the press meant and the sweep's focus (as the server keeps them), and the client's own freeze rule
    static final dev.awaken.arms.HitLedger LEDGER = new dev.awaken.arms.HitLedger();

    static {
        if (ArmsBlackBox.on()) LEDGER.ghosts = ArmsGhosts::ghost; // development: each ghost gets its cause (SPEC §25 "Weight in the Hand")
    }
    static int moveSeq = -1, aimedId = -1, focusId = -1, focusSeenAt;
    private static int bodies, lastFreezeAt = -1000, freezeSeq = -1, freezeFrom, freezeBase;

    /** The last strike or fight, for the HUD's busy look, the combat camera and the dodge (a swing at a tree counts). */
    static int busyAt() {
        return Math.max(fightAt, swungAt);
    }

    static int nextSeq() {
        return ++seq;
    }

    /** Whether Arms takes this player's attack key now (a weapon with a style, the world switch on). */
    static boolean fights(LocalPlayer p) {
        return ArmsConfig.enabled() && !p.isSpectator() && Arms.styleOf(p.getMainHandItem()) != null;
    }

    // ---- strike -----------------------------------------------------------------------------------------------------

    /** In a fight now, as the shoulder camera sees it: a fighting weapon in hand and a blow either way in the last 3 s. */
    static boolean engaged() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && ticks - Math.max(busyAt(), hurtAt) < 60 && fights(p) && ArmsComfort.combatCamera();
    }

    /** Presses taken so far (the autopilot's trace). */
    static int presses;

    /** The attack key has stayed down since the last press it gave (cleared at a tick's end once it's up), and that press's tick. */
    private static boolean attackHeld;
    /** The attack key has stayed down since vanilla took it to mine (cleared with {@link #attackHeld}). */
    private static boolean mineHeld;
    private static int pressedAt = -1000;

    /**
     * The attack key ({@link ArmsRules#attack}): every new press is a strike, wherever it points; outside a fight a
     * block the weapon cuts faster than a hand (a log with an axe, a web, bamboo) is still mined. Vanilla asks again
     * every tick the key stays down over a block (its mining): that is a hold, never another press (each one restarted
     * the swing, and a held heavy shook in place, seen by Euan 2026-10-25), and it goes on as its press began.
     */
    static void onKey(InputEvent.InteractionKeyMappingTriggered e) {
        if (!e.isAttack()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !fights(p)) return;
        ArmsRules.Attack a = ArmsRules.attack(attackHeld, mineHeld, ticks - Math.max(fightAt, hurtAt), cuts(mc, p));
        if (a == ArmsRules.Attack.MINE) {
            mineHeld = true;
            ArmsChop.want(mc); // the weapon's own chop while vanilla mines (SPEC §40 "The chop")
            return;
        }
        e.setCanceled(true);
        e.setSwingHand(false);
        if (a == ArmsRules.Attack.HELD || ticks == pressedAt) return;
        pressedAt = ticks;
        attackHeld = true;
        press(p, mc);
    }

    /** The crosshair is on a block the weapon in hand breaks faster than a bare hand (survival only). */
    private static boolean cuts(Minecraft mc, LocalPlayer p) {
        if (!(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK || mc.level == null) return false;
        if (p.isCreative()) return false; // a sword breaks nothing in creative
        return p.getMainHandItem().getDestroySpeed(mc.level.getBlockState(hit.getBlockPos())) > 1.0F;
    }

    static void press(LocalPlayer p, Minecraft mc) {
        Archetypes.Loaded st = Arms.styleOf(p.getMainHandItem());
        if (st == null) return;
        int target = pressTarget(mc);
        int s = nextSeq();
        ArmsFilm.event("press seq " + s + " target " + target);
        aimedId = dev.awaken.core.api.Aim.meantId(p, target, p.getYRot(), p.getXRot(), ArmsRules.pressReach(st));
        presses++;
        pressHeld = true;
        heldTicks = 0;
        charging = false;
        drawSeen = false;
        pressNanos = ArmsFeel.nanos() - ArmsFeel.paused();
        Archetype.MoveDef heavyDef = st.move(Archetype.Move.HEAVY);
        holdOpen = heavyDef != null && heavyDef.charge().isPresent();
        swungAt = ticks;
        if (meansFight(mc, p, st, target)) fightAt = ticks;
        int steps = mastery(st);
        Archetype.Move off = offChain(p, st, steps);
        ArmsRules.Decision d = off != null ? null : ArmsRules.tap(st, style == st ? move : null, moveStart, step, cadence, ticks, ArmsMastery.strikes(steps));
        // the whole decision goes with the press: the server plays what the hands play (SPEC §25 "One chain")
        boolean again = d != null && buffered != null && buffered.step() == d.step(); // the queued strike pressed again: still one strike
        int claim = off != null ? off.ordinal() : d == null ? ArmsPayloads.CLAIM_NOTHING : again ? ArmsPayloads.CLAIM_AGAIN : d.move().ordinal();
        if (again) played(",");
        ClientPacketDistributor.sendToServer(new ArmsPayloads.Press(s, ArmsPayloads.PRESS_STRIKE, p.getYRot(), p.getXRot(), target, claim));
        if (off == Archetype.Move.PLUNGE) {
            // armed: the blade raised, a dive to the ground, the blow where it lands
            plunging = true;
            plungeSeq = s;
            style = st;
            move = null;
            Vec3 v = p.getDeltaMovement();
            p.setDeltaMovement(v.x * 0.6, Math.min(v.y, -1.1), v.z * 0.6);
            return;
        }
        if (off != null) {
            if (off == Archetype.Move.RIPOSTE) deflectAt = -1000;
            if (off == Archetype.Move.SLIPCUT) dodgeAt = -1000;
            begin(p, st, off, -1, 0, s);
            return;
        }
        if (d == null) {
            played(".");
            return;
        }
        if (d.startAt() > ticks) {
            buffered = d;
            bufferedSeq = s;
            ArmsFilm.event("buffer " + d.move().key + " at " + d.startAt());
            return;
        }
        begin(p, st, d.move(), d.step(), d.cadence(), s);
    }

    /** The move off the chain a press opens, if any: a riposte or Slipcut in its window, an Undercut out of a slide, a Plunge in a fall. */
    private static Archetype.@Nullable Move offChain(LocalPlayer p, Archetypes.Loaded st, int steps) {
        Archetype.MoveDef riposte = st.move(Archetype.Move.RIPOSTE);
        if (riposte != null && ticks - deflectAt <= riposte.window()) return Archetype.Move.RIPOSTE;
        Archetype.MoveDef slip = st.move(Archetype.Move.SLIPCUT);
        if (slip != null && ticks - dodgeAt >= 1 && ticks - dodgeAt <= st.def().dodge().ticks() + slip.window() + ArmsMastery.slipcutBonus(steps)) return Archetype.Move.SLIPCUT;
        if (st.move(Archetype.Move.UNDERCUT) != null && Body.isSliding(p)) return Archetype.Move.UNDERCUT;
        if (st.move(Archetype.Move.PLUNGE) != null && ArmsRules.plunges(p.onGround(), p.fallDistance, p.isInLiquid())) return Archetype.Move.PLUNGE;
        return null;
    }

    /**
     * What the hands played this session, for the log: strike numbers with the ticks since the last one, "." for a press
     * that counted for nothing. A "sometimes" in the combo can only be read from real play (Euan 2026-11-09).
     */
    private static final StringBuilder PLAYED = new StringBuilder();
    private static int playedAt;

    private static void played(String what) {
        if (PLAYED.length() > 12000) return;
        PLAYED.append(what).append('+').append(ticks - playedAt).append(' ');
        playedAt = ticks;
    }

    /** The creature a press means (SPEC §40.1): the lock, else what the crosshair is on, else the mark; the server checks it. */
    private static int pressTarget(Minecraft mc) {
        int aim = dev.awaken.core.api.Aim.target();
        if (dev.awaken.core.api.Aim.locked()) return pressed = aim;
        return pressed = mc.hitResult instanceof EntityHitResult ehr ? ehr.getEntity().getId() : aim;
    }

    /** Whether a press at {@code target} means a fight ({@link ArmsRules#meansFight}): locked, under the crosshair, or the mark in reach. */
    private static boolean meansFight(Minecraft mc, LocalPlayer p, Archetypes.Loaded st, int target) {
        if (target < 0) return false;
        double gap = p.level().getEntity(target) instanceof Entity t ? dev.awaken.core.api.Aim.gap(p.getEyePosition(), t.getBoundingBox())
                : Double.POSITIVE_INFINITY;
        return ArmsRules.meansFight(dev.awaken.core.api.Aim.locked(), mc.hitResult instanceof EntityHitResult, gap, ArmsRules.markReach(st));
    }

    /** The creature the last press meant, for the step in. */
    private static int pressed = -1;

    /** Blocks a ground push carries the body: vanilla's ground friction keeps 0.546 of it a tick, so it goes about 2.2 times itself. */
    private static final double STEP_CARRY = 2.2;

    /**
     * The step in (SPEC §40.1): a chain's first strike or a heavy started just out of reach of the creature you meant carries
     * you toward it, at most the style's step, on the ground only. The server allows that reach for the meant creature alone.
     */
    private static void stepIn(LocalPlayer p, Archetypes.Loaded st, Archetype.Move m, int chainStep, Archetype.MoveDef def) {
        if (!(m == Archetype.Move.HEAVY || (m.strike() && chainStep == 0)) || pressed < 0 || dodging) return;
        if (!p.onGround() || p.isPassenger() || p.isInLiquid() || !(p.level().getEntity(pressed) instanceof net.minecraft.world.entity.LivingEntity t)
                || !t.isAlive() || t instanceof net.minecraft.world.entity.player.Player) return;
        double reach = def.reach() + 0.3, gap = dev.awaken.core.api.Aim.gap(p.getEyePosition(), t.getBoundingBox());
        float step = dev.awaken.core.api.Aim.stepIn(st.id());
        if (gap <= reach - 0.15 || gap > reach + step) return;
        double need = Math.min(step, gap - reach + 0.35);
        double dx = t.getX() - p.getX(), dz = t.getZ() - p.getZ(), len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-3) return;
        double v = need / STEP_CARRY;
        Vec3 dm = p.getDeltaMovement();
        p.setDeltaMovement(dx / len * v, dm.y, dz / len * v);
    }

    static int mastery(Archetypes.Loaded st) {
        return st.index() < mastery.length ? mastery[st.index()] : 0;
    }

    static void onMastery(ArmsPayloads.Mastery msg) {
        int[] m = new int[msg.steps().size()];
        for (int i = 0; i < m.length; i++) m[i] = msg.steps().get(i);
        mastery = m;
    }

    static void begin(LocalPlayer p, Archetypes.Loaded st, Archetype.Move m, int st2, int cad, int s) {
        begin(p, st, m, st2, cad, s, false);
    }

    /** perfect: a heavy released on its glint. */
    static void begin(LocalPlayer p, Archetypes.Loaded st, Archetype.Move m, int st2, int cad, int s, boolean perfect) {
        Archetype.MoveDef def = st.move(m);
        if (def == null) return;
        perfectHeavy = perfect;
        boolean waits = m.strike() && holdOpen && s == seq && ArmsRules.waitsOnHold(st.id());
        if (waits) {
            Archetype.MoveDef was = move == null || style == null ? null : style.move(move);
            underMove = was == null ? null : move;
            underAt = was == null ? 0F : viewTime();
            underEnd = was == null ? 0F : was.windup() + was.active() + recovery + style.def().late();
            underFrom = ArmsFeel.nanos() - ArmsFeel.paused();
            underSource = moveNanos;
        }
        waitHit = waits;
        ArmsFilm.event("begin " + m.key + " step " + st2 + " seq " + s + (waitHit ? " waits" : ""));
        if (style != st) PLAYED.append('[').append(st.id()).append("] ");
        played(m.strike() ? String.valueOf(m.ordinal() + 1) : m.key);
        style = st;
        move = m;
        step = st2;
        cadence = cad;
        moveStart = ticks;
        moveSeq = s;
        bodies = 0;
        moveNanos = ArmsFeel.nanos();
        movePaused = ArmsFeel.paused();
        recovery = ArmsRules.recovery(def, st, p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED));
        buffered = null;
        fadeFrom = -100;
        waitShown = waitHit;
        swishAt = -1;
        biteAt = -1;
        launchRate = launchBlend = 0;
        if (!waitHit) {
            stepIn(p, st, m, st2, def);
            swing(p, st, m, cad);
            ArmsFx.move(p, st, m, cad, perfect);
            ArmsBodies.move(p, st, m);
        }
        if (m == Archetype.Move.ART) ArmsFeel.art();
    }

    /**
     * The swish on the blade's clock (SPEC §25 "Swing Studio"): where the swing's keys say its whoosh peaks as the blade
     * crosses the target, not on the press (a heavy's swish played a whole wind-up early); at once when they say so.
     */
    private static void swing(LocalPlayer p, Archetypes.Loaded st, Archetype.Move m, int cad) {
        float at = FpSwings.swish(FpSwings.style(st.id()), m);
        if (at <= 0) {
            ArmsAudio.swing(p, st, m, cad);
            ArmsFilm.event("swish");
        } else swishAt = at;
    }

    /** When the running move's swish is due (ticks of its clock), -1 when played or none. */
    private static float swishAt = -1;

    /**
     * Each frame: a press let go is a tap now, and a swish come due plays now (a tick's wait would put either up to 50 ms
     * late: a dagger's strike waited at its set for the next tick after the key came up).
     */
    static void frame() {
        Minecraft mc = Minecraft.getInstance();
        ArmsChop.frame(mc);
        if (pressHeld && holdOpen && !charging && mc.player != null && !mc.options.keyAttack.isDown()) {
            pressHeld = false;
            tapped(mc.player);
        }
        if (mc.player != null) {
            drawBegins(mc.player);
            landPending(mc.player);
            predictHits(mc, mc.player);
        }
        if (swishAt < 0) return;
        LocalPlayer p = mc.player;
        if (p == null || move == null || style == null) {
            swishAt = -1;
            return;
        }
        if (viewTime() >= swishAt) {
            swishAt = -1;
            ArmsAudio.swing(p, style, move, cadence);
            ArmsFilm.event("swish");
        }
    }

    /** Wall-clock nanos a tick lasts now (the server's tick rate: a slowed world slows the hands with it). */
    static float nanosPerTick() {
        var level = Minecraft.getInstance().level;
        return level == null ? 50_000_000F : level.tickRateManager().nanosecondsPerTick();
    }

    // the bite: a landed blow's hit-stop over, the hands run slow through the body for a moment, then make the time up
    // by the move's end (FpSwings.Clip biteRate, biteTicks)
    private static float biteAt = -1, biteRate = 1, biteTicks;

    /** A landed blow of the running move: its bite starts where its hit-stop holds the hands. */
    static void bite() {
        if (move == null || style == null || biteAt >= 0) return;
        FpSwings.Style st = FpSwings.style(style.id());
        FpSwings.Clip c = st == null ? null : st.moves().get(move);
        if (c == null || c.biteTicks() <= 0 || !ArmsComfort.hitStop()) return;
        biteAt = moveTime();
        biteRate = c.biteRate();
        biteTicks = c.biteTicks();
    }

    /**
     * The swing's clock as the hands and the view show it: the move's own, held through hit-stop, slowed through a
     * landed blow's bite and catching up again before the move ends (the server's clock, {@link #moveTime}, never bites).
     */
    static float viewTime() {
        float t = moveTime();
        if (biteAt < 0 || t <= biteAt || style == null || move == null) return t;
        float e = t - biteAt, lag = (1 - biteRate) * biteTicks;
        if (e < biteTicks) return t - (1 - biteRate) * e;
        Archetype.MoveDef def = style.move(move);
        float rest = def == null ? 1 : Math.max(1F, def.windup() + def.active() + def.recovery() - biteAt - biteTicks);
        return t - lag * Math.max(0F, 1F - (e - biteTicks) / rest);
    }

    /**
     * Ticks into the running move on the hands' clock (hit-stop holds pause it), with the frame's fraction: a strike
     * waiting on its press stands at 0 under the draw, and a tap starts it there, its wind-up run at the launch's rate.
     */
    static float moveTime() {
        if (waitHit) return 0F;
        float t = (ArmsFeel.nanos() - moveNanos - (ArmsFeel.paused() - movePaused)) / ArmsFeel.TICK_NANOS;
        return launchRate > 0 ? ChargeDraw.clock(t, launchTo, launchRate) : t;
    }

    // a tapped strike's launch (ChargeDraw): its clock's rate to its hit, the hit, and the ticks the hands have to get
    // there, which they take once (ArmsHands, on the frame the source changes)
    private static float launchRate, launchTo, launchBlend;

    /** The clock's rate now (a blend into a launching strike carries its true speed). */
    static float launchRate() {
        return launchRate > 0 ? launchRate : 1F;
    }

    /** The ticks a launch gives the hands to reach its strike, once; 0 when this isn't a launch. */
    static float takeLaunch() {
        float b = launchBlend;
        launchBlend = 0;
        return b;
    }

    /** The hands are free to draw: this press's strike is unseen under them, or no blow is still landing. */
    private static boolean handsFree() {
        Archetype.MoveDef def = move == null || style == null ? null : style.move(move);
        return def == null || ChargeDraw.handsFree(waitHit, true, moveTime(), def.activeEnd());
    }

    /** The hands are on their way to the charge form (and not yet held there). */
    static boolean drawing() {
        return !charging && ChargeDraw.drawing(pressHeld, holdOpen, plunging, handsFree(), heldFor());
    }

    /** Ticks this press has been down, on the hands' clock. */
    private static float heldFor() {
        return (ArmsFeel.nanos() - ArmsFeel.paused() - pressNanos) / ArmsFeel.TICK_NANOS;
    }

    /** A press not yet a tap or a hold (SPEC §25 "Tap or hold"): nothing on screen knows about it. */
    static boolean undecided() {
        return waitHit && pressHeld && holdOpen && !charging && !drawing();
    }

    /** What the hands go on with under an undecided press (null: at rest, or it has run out). */
    static Archetype.@Nullable Move underMove() {
        return underTime() > underEnd ? null : underMove;
    }

    /** Ticks into that move, its own clock carried on from the press. */
    static float underTime() {
        return underAt + (ArmsFeel.nanos() - ArmsFeel.paused() - underFrom) / ArmsFeel.TICK_NANOS;
    }

    /** This weapon's draw, in ticks, from its charge's glint. */
    static float drawTicks() {
        Archetype.MoveDef heavy = style == null ? null : style.move(Archetype.Move.HEAVY);
        return ChargeDraw.ticks(heavy == null || heavy.charge().isEmpty() ? 14 : heavy.charge().get().glint());
    }

    /** How far the hands have come along this press's draw (0..1). */
    private static float drawnNow() {
        return drawSeen ? ChargeDraw.drawn((ArmsFeel.nanos() - ArmsFeel.paused() - drawFrom) / ArmsFeel.TICK_NANOS, drawTicks()) : 0F;
    }

    /** The hands belong to the draw or the charge after it: one source, from the press to the release. */
    static boolean drawn() {
        return charging || drawing();
    }

    /** The draw's first frame: when it began, and the body starting into the same form (third person). */
    private static void drawBegins(LocalPlayer p) {
        if (drawSeen || !drawing()) return;
        drawSeen = true;
        drawFrom = ArmsFeel.nanos() - ArmsFeel.paused();
        ArmsBodies.charge(p, (int) drawTicks());
    }

    static void tick() {
        ticks++;
        ArmsFeel.tick();
        ArmsFx.tick();
        ArmsTells.tick();
        ArmsStances.tick();
        ArmsBodies.tick();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        boolean guard = p.isUsingItem() && Arms.guards(p.getUseItem());
        if (guard && !guardUp) ArmsAudio.raise(p);
        guardUp = guard;
        ArmsChop.tick(mc, p);
        if (plunging) {
            Archetypes.Loaded st = Arms.styleOf(p.getMainHandItem());
            if (st == null || p.isInLiquid() || p.getAbilities().flying) plunging = false;
            else if (p.onGround()) {
                plunging = false;
                begin(p, st, Archetype.Move.PLUNGE, -1, 0, plungeSeq);
                CombatFeedback.kick(0F, 3F, 0F, 8);
            }
        }
        if (buffered != null && ticks >= buffered.startAt()) {
            ArmsRules.Decision d = buffered;
            Archetypes.Loaded st = Arms.styleOf(p.getMainHandItem());
            buffered = null;
            if (st != null) begin(p, st, d.move(), d.step(), d.cadence(), bufferedSeq);
        }
        if (move != null && style != null && !charging) {
            Archetype.MoveDef def = style.move(move);
            if (def == null || moveTime() > def.windup() + def.active() + recovery + style.def().late()) {
                move = null; // past the late press: the next tap starts the chain again either way
                step = -1;
            }
        }
        if (waitHit && move != null && style != null) {
            Archetype.MoveDef def = style.move(move);
            // the chain's clock waits as the server's does (ArmsServer.moveTick: its start a tick later for each tick
            // the strike waits past its wind-up), so the next press is timed alike on both sides: unmoved here, the
            // client ran each dagger strike a tick or two ahead of the server's, cut the last one's carry short, and
            // took a press the server's buffer refused (its strike never landed, the chain fell out of step)
            if (def != null && ticks - moveStart > def.windup()) moveStart++;
        }
        if (pressHeld) heldOrReleased(p, mc);
        if (dodgeCharges >= 0 && style != null && ticks >= dodgeRechargeAt && dodgeCharges < style.def().dodge().charges()) {
            dodgeCharges++;
            dodgeRechargeAt = ticks + style.def().dodge().recharge();
        }
        if (!BROKEN.isEmpty()) BROKEN.values().removeIf(until -> until < ticks);
        holdFocus(p);
        LEDGER.expire(ticks);
        if (!mc.options.keyAttack.isDown()) attackHeld = mineHeld = false;
    }

    /** The waiting strike, a tap after all: its step in, its swish and arc on the blade's clock, its body clip. */
    private static void show(LocalPlayer p) {
        waitShown = false;
        Archetype.MoveDef def = style == null || move == null ? null : style.move(move);
        if (def == null) return;
        stepIn(p, style, move, step, def);
        swing(p, style, move, cadence);
        ArmsFx.move(p, style, move, cadence, perfectHeavy, ArmsHands.blowStart(def) / launchRate());
        ArmsBodies.move(p, style, move);
    }

    /**
     * The press let go before its charge: a tap. The strike that waited under the draw starts now, from wherever the
     * hands have got to, and reaches its hit by the launch's end.
     */
    private static void tapped(LocalPlayer p) {
        if (!holdOpen) return;
        holdOpen = false;
        boolean waited = waitHit;
        waitHit = false;
        float drawn = drawnNow();
        ArmsFilm.event("tap drawn " + drawn + (waited ? " waited" : ""));
        Archetype.MoveDef def = waited && style != null && move != null ? style.move(move) : null;
        if (def != null) {
            moveNanos = ArmsFeel.nanos();
            movePaused = ArmsFeel.paused();
            launchTo = def.windup();
            launchRate = ChargeDraw.launchRate(launchTo, drawn);
            launchBlend = ChargeDraw.launchTicks(launchTo, drawn);
        }
        ClientPacketDistributor.sendToServer(new ArmsPayloads.Press(seq, ArmsPayloads.PRESS_TAP, p.getYRot(), p.getXRot(), -1));
        if (waitShown) show(p);
        else if (drawSeen) ArmsBodies.settle(p); // no strike of its own to take the body over (one still queued, or none)
    }

    private static void heldOrReleased(LocalPlayer p, Minecraft mc) {
        Archetypes.Loaded st = style;
        Archetype.MoveDef heavy = st == null ? null : st.move(Archetype.Move.HEAVY);
        if (heavy == null || heavy.charge().isEmpty() || !fights(p)) {
            pressHeld = charging = false; // the weapon put away mid-draw: the body doesn't stay in it
            if (drawSeen) ArmsBodies.settle(p);
            return;
        }
        Archetype.Charge c = heavy.charge().get();
        if (mc.options.keyAttack.isDown()) {
            heldTicks++;
            if (!charging && heldTicks >= c.min() && handsFree()) {
                // held: the strike that began on the press never lands or shows; the charge takes its place, and the
                // hands, already on their way to its form, don't notice (a blow still landing goes through first)
                drawBegins(p);
                holdOpen = false;
                waitHit = false;
                waitShown = false;
                if (buffered != null && bufferedSeq == seq) buffered = null; // nor will one queued mid-combo
                charging = true;
                played("charge");
                ArmsFilm.event("charge");
                chargeStart = ticks;
                move = Archetype.Move.HEAVY;
                moveStart = ticks;
                moveNanos = ArmsFeel.nanos();
                movePaused = ArmsFeel.paused();
                launchRate = 0;
                step = -1;
                ClientPacketDistributor.sendToServer(new ArmsPayloads.Press(seq, ArmsPayloads.PRESS_CHARGE, p.getYRot(), p.getXRot(), -1));
                ArmsAudio.charge(p);
            }
            if (charging && c.min() + (ticks - chargeStart) == c.glint()) ArmsAudio.glint(p);
            return; // held at full charge for as long as the key is down: it goes only when let go (Euan 2026-10-26)
        }
        pressHeld = false;
        if (!charging) {
            tapped(p);
            return;
        }
        charging = false;
        ArmsFilm.event("release");
        float drawn = drawnNow();
        int target = pressTarget(mc);
        ClientPacketDistributor.sendToServer(new ArmsPayloads.Press(seq, ArmsPayloads.PRESS_RELEASE, p.getYRot(), p.getXRot(), target));
        if (target >= 0) aimedId = dev.awaken.core.api.Aim.meantId(p, target, p.getYRot(), p.getXRot(), ArmsRules.pressReach(st));
        int held = c.min() + (ticks - chargeStart);
        if (st.move(Archetype.Move.ART) != null && ArmsRules.artReady(held, c, resonance, ArmsMastery.artAt(mastery(st)))) {
            begin(p, st, Archetype.Move.ART, -1, 0, seq); // held through with the meter full: the Art
            launchBlend = ChargeDraw.releaseTicks(drawn);
            return;
        }
        boolean execution = target >= 0 && BROKEN.containsKey(target) && st.move(Archetype.Move.EXECUTION) != null;
        begin(p, st, execution ? Archetype.Move.EXECUTION : Archetype.Move.HEAVY, -1, 0, seq, !execution && c.perfect(held));
        launchBlend = ChargeDraw.releaseTicks(drawn);
    }

    /** The running heavy was released on its glint (its arc burns gold). */
    static boolean perfectHeavy;
    /** A Plunge armed in a fall (the blade held high until it lands), and its press. */
    static boolean plunging;
    /** The guard was up last tick (its raise plays once). */
    private static boolean guardUp;
    static int plungeSeq;

    /** How far a held heavy has charged (0..1, the glint at its mark), or -1 when none is drawn: it fills from the draw's start, so a tap never lights it and the mark is no step. */
    static float charge() {
        if (style == null || !(charging || drawing())) return -1;
        Archetype.MoveDef heavy = style.move(Archetype.Move.HEAVY);
        if (heavy == null || heavy.charge().isEmpty()) return -1;
        Archetype.Charge c = heavy.charge().get();
        int held = charging ? c.min() + (ticks - chargeStart) : heldTicks;
        return Mth.clamp((held - ChargeDraw.GRACE) / (c.glint() - ChargeDraw.GRACE), 0, 1); // full at the glint
    }

    /** Whether a held heavy is on its glint right now (a release would be perfect). */
    static boolean glinting() {
        if (!charging || style == null) return false;
        Archetype.MoveDef heavy = style.move(Archetype.Move.HEAVY);
        return heavy != null && heavy.charge().isPresent() && heavy.charge().get().perfect(heavy.charge().get().min() + (ticks - chargeStart));
    }

    // ---- dodge --------------------------------------------------------------------------------------------------------

    /** After the body's own input (LOW): the sprint key with no forward on the ground is a dodge. */
    static void onInput(MovementInputUpdateEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (!(e.getEntity() instanceof LocalPlayer p) || p != mc.player) return;
        ClientInput in = e.getInput();
        Input k = in.keyPresses;
        boolean sprintPressed = k.sprint() && !sprintWas;
        sprintWas = k.sprint();
        if (dodging) {
            dodgeTick(p);
            return;
        }
        if (!sprintPressed || k.forward() || !canDodge(p)) return;
        Archetypes.Loaded st = Arms.styleOf(p.getMainHandItem());
        if (st == null) st = Archetypes.get(Arms.LONGSWORD);
        if (st == null) return;
        Archetype.Dodge d = st.def().dodge();
        if (dodgeCharges < 0) dodgeCharges = d.charges();
        if (dodgeCharges <= 0) return;
        // a strike in its wind-up or blow can't be dodged out of; its recovery can
        if (move != null && style != null) {
            Archetype.MoveDef def = style.move(move);
            if (def != null && moveTime() < def.activeEnd()) return;
        }
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw), lx = Mth.cos(yaw), lz = Mth.sin(yaw);
        double x = 0, z = 0;
        if (k.left()) { x += lx; z += lz; }
        if (k.right()) { x -= lx; z -= lz; }
        if (k.backward() || (x == 0 && z == 0)) { x -= fx; z -= fz; }
        double len = Math.sqrt(x * x + z * z);
        dodgeX = x / len;
        dodgeZ = z / len;
        if (p.isUsingItem() && mc.gameMode != null) mc.gameMode.releaseUsingItem(p); // the dodge drops the guard
        if (dodgeCharges == d.charges()) dodgeRechargeAt = ticks + d.recharge();
        dodgeCharges--;
        dodging = true;
        dodgeTick = 0;
        dodgeAt = ticks;
        style = st;
        move = null;
        step = -1;
        cadence = 0;
        buffered = null;
        fightAt = ticks;
        ClientPacketDistributor.sendToServer(new ArmsPayloads.Dodge(nextSeq(), (float) dodgeX, (float) dodgeZ, p.getX(), p.getY(), p.getZ()));
        ArmsAudio.dodge(p);
        ArmsBodies.dodge(p, dodgeX, dodgeZ);
        CombatFeedback.kick((float) (dodgeX * fz - dodgeZ * fx) * -2.5F, 0F, (float) (dodgeX * lx + dodgeZ * lz) * 3.5F, 8);
        dodgeTick(p);
    }

    private static boolean canDodge(LocalPlayer p) {
        boolean engaged = fights(p) || ticks - busyAt() < 100;
        return engaged && ArmsConfig.enabled() && p.onGround() && !p.isInLiquid() && !p.isPassenger() && !p.isSpectator() && !p.getAbilities().flying
                && !Body.isSliding(p) && !Body.isMantling(p) && !MovementHooks.wallRunning(p) && !MovementHooks.hanging(p);
    }

    private static void dodgeTick(LocalPlayer p) {
        Archetype.Dodge d = style == null ? null : style.def().dodge();
        if (d == null || dodgeTick >= d.ticks()) {
            dodging = false;
            return;
        }
        double v = ArmsRules.dodgeSpeed(d, dodgeTick++);
        Vec3 dm = p.getDeltaMovement();
        p.setDeltaMovement(dodgeX * v, dm.y, dodgeZ * v);
    }

    // ---- the hit at once (SPEC §47.1) --------------------------------------------------------------------------------

    /**
     * Each frame of a move's active window: the server's sweep asked here first ({@link ArmsRules#predicts}, a little
     * inside its reach and arc), so a blow's impact, sound, sparks, freeze and the struck body's flinch play on the frame
     * the blade arrives, not when the server's word comes back (0-67 ms later on a healthy server, a whole stall later on
     * a slow one: Euan 2026-11-02, "hits don't hit right away"). The server still decides the damage; its HIT for a blow
     * played here only adds a kill. Only creatures the server would sweep: hostiles, never a person or one's own.
     */
    private static void predictHits(Minecraft mc, LocalPlayer p) {
        if (move == null || style == null || waitHit || charging || mc.level == null || !fights(p) || p.isPassenger()
                || p.getAbilities().flying || p.isFallFlying() || p.isSleeping()) return;
        Archetype.MoveDef def = style.move(move);
        if (def == null) return;
        float t = moveTime();
        if (t < landsAt(def) || t >= def.activeEnd()) return;
        float grow = step < 0 || !move.strike() ? 0F : style.def().traits().reachPerStep() * step;
        double reach = def.reachAt((long) t) + 0.3 + grow;
        double meantReach = reach + ((move == Archetype.Move.HEAVY || (move.strike() && step == 0)) ? dev.awaken.core.api.Aim.stepIn(style.id()) : 0F);
        Vec3 eye = p.getEyePosition();
        float yaw = p.getYRot() + focusTurn(p);
        net.minecraft.world.phys.AABB box = p.getBoundingBox().inflate(meantReach + 1, Math.max(1.5, meantReach), meantReach + 1);
        for (net.minecraft.world.entity.LivingEntity e : mc.level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box,
                e -> e != p && e.isAlive() && !(e instanceof net.minecraft.world.entity.player.Player))) {
            if (LEDGER.has(moveSeq, e.getId())) continue;
            boolean aimed = e.getId() == aimedId;
            if (!aimed && !foe(p, e)) continue;
            if (!ArmsRules.predicts(eye, p.getY(), e.getBoundingBox(), yaw, def.arc(), aimed ? meantReach : reach, aimed, aimed)) continue;
            ArmsGhosts.shown(moveSeq, e, move, eye, aimed ? meantReach : reach);
            played(p, def, e);
        }
    }

    /**
     * Where the running move's blow lands on its clock. A heavy's windup is 0, the hold having been its wind-up, but the
     * weapon has still to cross: the freeze on the first frame held it wound back while the target already reeled, and
     * only then let it swing. Its look waits for the blade to cross, on every weapon; a server HIT that comes first waits
     * with it, never more than a tick ({@link #landPending}).
     */
    private static float landsAt(Archetype.MoveDef def) {
        return move != Archetype.Move.HEAVY || style == null ? def.windup() : FpSwings.lands(FpSwings.style(style.id()), move, def);
    }

    /** What the server's sweep strikes unaimed, as far as a client can tell: a hostile, never one's own nor a villager. */
    private static boolean foe(LocalPlayer p, net.minecraft.world.entity.LivingEntity e) {
        if (e instanceof net.minecraft.world.entity.npc.Npc || e.isInvulnerable()) return false;
        if (e instanceof net.minecraft.world.entity.OwnableEntity o && o.getOwner() == p) return false;
        return e instanceof net.minecraft.world.entity.monster.Enemy;
    }

    /** The sweep's turn toward its focus (the last creature struck, else the one the press meant), as the server turns it. */
    private static float focusTurn(LocalPlayer p) {
        Entity f = p.level().getEntity(focusId >= 0 ? focusId : aimedId);
        if (!(f instanceof net.minecraft.world.entity.LivingEntity l) || !l.isAlive() || l.distanceToSqr(p) >= 64) return 0;
        float toward = (float) (Mth.atan2(f.getZ() - p.getZ(), f.getX() - p.getX()) * Mth.RAD_TO_DEG) - 90F;
        return ArmsRules.focusTurn(toward, p.getYRot());
    }

    /** The focus lets go when it's gone or looked away from for a second (ArmsServer.holdFocus). */
    private static void holdFocus(LocalPlayer p) {
        if (focusId < 0) return;
        Entity f = p.level().getEntity(focusId);
        if (!(f instanceof net.minecraft.world.entity.LivingEntity l) || !l.isAlive() || l.distanceToSqr(p) >= 64) {
            focusId = -1;
            return;
        }
        float toward = (float) (Mth.atan2(f.getZ() - p.getZ(), f.getX() - p.getX()) * Mth.RAD_TO_DEG) - 90F;
        if (ArmsRules.lookedAt(toward, p.getYRot())) focusSeenAt = ticks;
        else if (ticks - focusSeenAt >= ArmsRules.FOCUS_DROP_TICKS) focusId = -1;
    }

    /**
     * One blow played now, as the server's HIT would play it (ArmsServer.hit's freeze rule: one freeze per
     * {@link ArmsRules#HITSTOP_GAP} ticks, grown by each extra body of the same sweep), and put in the ledger.
     */
    private static void played(LocalPlayer p, Archetype.MoveDef def, net.minecraft.world.entity.LivingEntity e) {
        bodies++;
        int base = perfectHeavy && def.charge().isPresent() ? def.charge().get().glintHitstop() : def.hitstop();
        int ms;
        if (ticks - lastFreezeAt >= ArmsRules.HITSTOP_GAP) {
            lastFreezeAt = ticks;
            freezeSeq = moveSeq;
            freezeFrom = bodies;
            freezeBase = base;
            ms = base;
        } else if (ticks == lastFreezeAt && freezeSeq == moveSeq) {
            ms = ArmsRules.hitstop(freezeBase, bodies - freezeFrom + 1);
        } else {
            ms = 0;
        }
        // the push the server's blow gives: away from the striker, turned along a side cut
        float side = ArmsRules.side(def, p.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT);
        double ax = e.getX() - p.getX(), az = e.getZ() - p.getZ(), an = Math.sqrt(ax * ax + az * az);
        if (an < 1e-4) {
            ax = -Mth.sin(p.getYRot() * Mth.DEG_TO_RAD);
            az = Mth.cos(p.getYRot() * Mth.DEG_TO_RAD);
        } else {
            ax /= an;
            az /= an;
        }
        ArmsRules.push(ax, az, side, PUSH);
        boolean strong = def.finisher() || perfectHeavy || move == Archetype.Move.EXECUTION;
        Blow b = new Blow(move.ordinal(), false, false, strong, bodies, ArmsRules.lift(def), Blow.yawByte(PUSH[0], PUSH[1]), Math.round(side * 7));
        LEDGER.played(moveSeq, e.getId(), ticks, System.nanoTime(), ms);
        fightAt = ticks;
        focusId = e.getId();
        focusSeenAt = ticks;
        impact(p, e.getId(), b, ms, false);
    }

    private static final float[] PUSH = new float[2];

    // a server HIT for the running move that comes before its blade reaches the hit (a dagger's one-tick strike, the
    // word back inside the windup): its impact waits for the blade, never more than a tick (§47.1), a cleave's bodies
    // together. Only the look waits; the server already struck.
    private static final int[] PEND_TARGET = new int[8], PEND_BLOW = new int[8], PEND_MS = new int[8];
    private static int pending, pendingSeq = -1;
    private static int pendingBreak = -1;
    private static long pendingBy;

    /** Whether a HIT is held for the blade (the running move's own, its clock short of the hit). */
    private static boolean early(int s, int target, Blow b, int blow, int ms) {
        if (s != moveSeq || move == null || style == null || b.move() != move.ordinal()) return false;
        if (!shortOfBlade() || pending == PEND_TARGET.length) return false;
        waitForBlade();
        PEND_TARGET[pending] = target;
        PEND_BLOW[pending] = blow;
        PEND_MS[pending] = ms;
        pending++;
        return true;
    }

    /** Whether the running move's blade is still short of its hit. */
    private static boolean shortOfBlade() {
        Archetype.MoveDef def = move == null || style == null ? null : style.move(move);
        return def != null && moveTime() < landsAt(def);
    }

    /** The one tick's wait starts with the first thing held for the blade. */
    private static void waitForBlade() {
        if (pending > 0 || pendingBreak >= 0) return;
        pendingSeq = moveSeq;
        pendingBy = System.nanoTime() + (long) nanosPerTick();
    }

    /** Each frame: the held HITs land once the blade reaches the hit, a tick has gone, or another move began. */
    private static void landPending(LocalPlayer p) {
        if (pending == 0 && pendingBreak < 0) return;
        Archetype.MoveDef def = move == null || style == null ? null : style.move(move);
        if (def != null && moveSeq == pendingSeq && moveTime() < landsAt(def) && System.nanoTime() < pendingBy) return;
        int n = pending, broken = pendingBreak;
        pending = 0;
        pendingBreak = -1;
        for (int i = 0; i < n; i++) landed(p, PEND_TARGET[i], Blow.unpack(PEND_BLOW[i]), PEND_MS[i]);
        if (broken >= 0) postureBroke(broken);
    }

    /** A posture broken by us, as the striker feels it: the freeze on it and on the hands, the view's drop. */
    private static void postureBroke(int target) {
        CombatFeedback.hitstop(target, 160);
        CombatFeedback.hold(120);
        CombatFeedback.kick(0F, 2.5F, 0F, 10);
        ArmsFeel.broke();
    }

    /** A server HIT the client didn't play: played now, all of it. */
    private static void landed(LocalPlayer p, int target, Blow b, int ms) {
        boolean killMoment = b.kill() && ArmsComfort.killMoment();
        impact(p, target, b, ms, killMoment);
        Minecraft mc = Minecraft.getInstance();
        Entity struck = mc.level == null ? null : mc.level.getEntity(target);
        if (struck != null && b.cleave() >= 2) ArmsAudio.cleave(struck, b.cleave());
        if (struck != null && killMoment) {
            ArmsAudio.kill(struck);
            BladeTrail.flare(p.getId());
            if (b.last()) ArmsFeel.lastFoe();
        }
    }

    /**
     * The server's word on a blow already played: nothing again but what only it knew. A kill holds the freeze for the
     * kill's beat (what's left of it), drops the body, and plays the kill's sound and flare; a cleave's ring.
     */
    private static void confirmed(LocalPlayer p, int seq, int target, Blow b, int ms) {
        Minecraft mc = Minecraft.getInstance();
        Entity struck = mc.level == null ? null : mc.level.getEntity(target);
        if (struck != null && b.cleave() >= 2) ArmsAudio.cleave(struck, b.cleave());
        if (!b.kill() || !ArmsComfort.killMoment() || struck == null) return;
        long at = LEDGER.freezeAt(seq, target);
        int played = LEDGER.freezeMs(seq, target);
        int left = ArmsRules.killHitstop(Math.max(ms, played)) - (int) ((System.nanoTime() - at) / 1_000_000L);
        if (played > 0 && left > 0) {
            CombatFeedback.hitstop(target, left);
            CombatFeedback.hold(left);
        }
        ArmsRecoil.killed(struck, b);
        ArmsAudio.kill(struck);
        BladeTrail.flare(p.getId());
        if (b.last()) ArmsFeel.lastFoe();
    }

    // ---- the server's word --------------------------------------------------------------------------------------------

    static void onResult(ArmsPayloads.Result r) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        switch (r.what()) {
            case ArmsPayloads.MOVE -> {
                Archetype.Move m = Archetype.Move.byId(r.aux());
                boolean differs = r.seq() == moveSeq && m != null && m != move && style != null && !charging;
                ArmsFilm.event("server move " + (m == null ? "-" : m.key) + " seq " + r.seq() + " ours " + (move == null ? "-" : move.key) + (differs ? " SWITCHED" : ""));
                if (differs) {
                    dev.awaken.Awaken.LOG.info("[arms] the server ran {} where the hands showed {} (seq {}, step {})", m.key, move == null ? "-" : move.key, r.seq(), step);
                    // the chain is the hands' own: its reply never changes the strike they're in or the step they count from.
                    // Only what the hands couldn't know takes over (an execution on a heavy's release)
                    if (!m.strike() || move == null || !move.strike()) move = m;
                }
                if (r.seq() == seq) cadence = r.aux2();
            }
            case ArmsPayloads.REFUSE -> {
                if (r.entity() == -2) {
                    dodging = false;
                } else if (r.seq() == seq) {
                    ArmsFilm.event("refused seq " + r.seq());
                    dev.awaken.Awaken.LOG.info("[arms] the server refused seq {} (hands on {})", r.seq(), move == null ? "-" : move.key);
                    fadeFrom = ticks;
                    move = null;
                    step = -1;
                    charging = pressHeld = holdOpen = waitHit = false; // nothing to draw toward: the press is void
                    ArmsBodies.settle(p);
                }
            }
            case ArmsPayloads.HIT -> {
                fightAt = ticks;
                focusId = r.entity();
                focusSeenAt = ticks;
                Blow b = Blow.unpack(r.aux2());
                if (LEDGER.confirm(r.seq(), r.entity(), ticks)) {
                    ArmsFilm.event("confirmed seq " + r.seq() + " body " + r.entity());
                    confirmed(p, r.seq(), r.entity(), b, r.aux());
                    return;
                }
                if (early(r.seq(), r.entity(), b, r.aux2(), r.aux())) return;
                landed(p, r.entity(), b, r.aux());
            }
            case ArmsPayloads.DEFLECT -> {
                deflectAt = ticks;
                fightAt = ticks;
                CombatFeedback.hitstop(r.entity(), 100);
                CombatFeedback.hold(100);
                CombatFeedback.kick(0F, -1.8F, 1.2F, 7);
                ArmsFeel.deflected();
                ArmsAudio.deflect(p);
                int yaw = yawTo(p, Minecraft.getInstance().level.getEntity(r.entity()));
                ArmsHands.jolt(Mth.sin(Mth.wrapDegrees(yaw - p.getYRot()) * Mth.DEG_TO_RAD), 1F);
                ArmsBodies.jolt(p);
                ArmsFx.guardSpark(p, yaw, true);
            }
            case ArmsPayloads.BLOCK -> {
                fightAt = ticks;
                // the blow lands on the blade: you feel it stop, for half as long as a deflect (SPEC §47.3)
                int yaw = yawTo(p, Minecraft.getInstance().level.getEntity(r.entity()));
                float side = Mth.sin(Mth.wrapDegrees(yaw - p.getYRot()) * Mth.DEG_TO_RAD);
                CombatFeedback.hitstop(r.entity(), 50);
                CombatFeedback.hold(50);
                CombatFeedback.kick(side * 1.2F, 2.2F, side * 0.8F, 6);
                ArmsAudio.block(p);
                ArmsHands.jolt(side, 0.85F);
                ArmsBodies.jolt(p);
                ArmsFx.guardSpark(p, yaw, false);
            }
            case ArmsPayloads.SHOT -> {
                // a shot on the guard: the blade takes it (its jolt, its spark, its ring), no freeze (nothing in
                // reach was answered), no riposte
                fightAt = ticks;
                boolean deflected = r.aux() == ArmsPayloads.DEFLECT;
                float side = Mth.sin(Mth.wrapDegrees(r.aux2() - p.getYRot()) * Mth.DEG_TO_RAD);
                ArmsHands.jolt(side, deflected ? 1F : 0.7F);
                ArmsBodies.jolt(p);
                ArmsFx.guardSpark(p, r.aux2(), deflected);
                if (deflected) {
                    CombatFeedback.kick(-side * 0.6F, -1.1F, 0.7F, 6);
                    ArmsFeel.glanced();
                } else {
                    CombatFeedback.kick(-side * 0.5F, 0.9F, 0F, 5);
                }
                ArmsAudio.shot(p, deflected);
            }
            case ArmsPayloads.GUARD_BREAK -> {
                fightAt = ticks;
                CombatFeedback.kick(0F, 3.5F, -2F, 10);
                ArmsFeel.guardBroken();
                ArmsAudio.guardBreak(p);
            }
            case ArmsPayloads.PERFECT_DODGE -> {
                fightAt = ticks;
                CombatFeedback.hitstop(r.entity(), 60);
                ArmsFeel.breath();
                ArmsAudio.perfectDodge(p);
            }
            case ArmsPayloads.BREAK -> {
                BROKEN.put(r.entity(), ticks + r.aux());
                // sent as the damage lands, so before the HIT of the blow that broke it: its freeze waits for the same blade
                if (shortOfBlade()) {
                    waitForBlade();
                    pendingBreak = r.entity();
                } else {
                    postureBroke(r.entity());
                }
            }
            case ArmsPayloads.RESONANCE -> resonance = r.aux();
            case ArmsPayloads.HURT -> hurt(p, r.aux(), r.aux2() / 10F);
            default -> { }
        }
    }

    /**
     * A blow of ours landing, as the striker feels it: its sound, sparks, the struck body's flinch, the freeze on it and
     * on the hands, the bite, the view's kick along the blow. {@code rawMs}: the blow's freeze before a kill's beat.
     */
    private static void impact(LocalPlayer p, int target, Blow b, int rawMs, boolean killMoment) {
        int ms = killMoment ? ArmsRules.killHitstop(rawMs) : rawMs;
        ArmsFilm.event("impact " + Archetype.Move.byId(b.move()) + " ms " + ms + " on " + (move == null ? "-" : move.key)
                + " at " + (move == null ? -1 : viewTime()));
        ArmsAudio.impact(p, target, style, b.move());
        hitFx(p, target, style, b, ms);
        ArmsStances.struck(target);
        if (ms <= 0) return;
        CombatFeedback.hitstop(target, ms);
        Archetype.Move m = Archetype.Move.byId(b.move());
        // the hands hold for the blow on them; a blow the hands have already left (the next strike
        // begun) froze that one mid-swing, a beat skipped
        if (m == move) {
            CombatFeedback.hold(ms);
            bite();
        }
        Archetype.MoveDef def = style == null || m == null ? null : style.move(m);
        if (m == Archetype.Move.EXECUTION) ArmsFeel.executed();
        if (def != null) {
            java.util.List<Float> from = def.swing().from(), to = def.swing().to();
            // along the blow's travel on screen (y up there, pitch down here); a kill kicks harder, and
            // each extra body of a cleave a fifth more
            float dx = to.get(0) - from.get(0), dy = to.get(1) - from.get(1);
            float n = Math.max(1e-3F, (float) Math.sqrt(dx * dx + dy * dy));
            float yaw = dx / n, pitch = -dy / n;
            // a hand-keyed move kicks along the blade its keys draw, not the row (a stab's row ran sideways)
            float[] along = style == null ? null : FpSwings.kick(FpSwings.style(style.id()), m);
            if (along != null) {
                yaw = along[0];
                pitch = along[1];
            }
            float k = Math.min(2.4F, rawMs / 40F) * (killMoment ? KILL_KICK : 1F)
                    * (1 + CLEAVE_KICK * Mth.clamp(b.cleave() - 1, 0, ArmsRules.CLEAVE_EXTRA_MAX));
            CombatFeedback.kick(yaw * k, pitch * k, yaw * k * 0.4F, 6);
        }
    }

    /** A blow landed on us: its thump, the view nudged away from where it came from, the HUD's shard toward it. */
    private static void hurt(LocalPlayer p, int fromYaw, float damage) {
        ArmsAudio.hurt(p);
        if (fromYaw == Integer.MIN_VALUE) return; // no place it came from (a blast's centre is gone, a thorn)
        hurtYaw = fromYaw;
        hurtAt = ticks;
        // vanilla already rolls the view toward the blow; this adds the jolt of its weight, never more than a few degrees
        float rel = Mth.wrapDegrees(fromYaw - p.getYRot()) * Mth.DEG_TO_RAD;
        float w = Mth.clamp(damage / 6F, 0.4F, 1.5F);
        CombatFeedback.kick(-Mth.sin(rel) * 1.6F * w, -Mth.cos(rel) * 1.4F * w, 0F, 7);
    }

    /** The world yaw a blow on us last came from, and the tick it landed (the HUD's shard). */
    static int hurtYaw, hurtAt = -1000;

    /** A kill's view kick, against a hit's; each extra body of a cleave adds this share to it (SPEC §25 "Fight Feel"). */
    static final float KILL_KICK = 1.3F, CLEAVE_KICK = 0.2F;

    /** Sparks where a blade landed, and the struck body's recoil once its freeze ({@code ms}) lets go. */
    private static void hitFx(Entity by, int targetId, Archetypes.@Nullable Loaded st, Blow b, int ms) {
        Minecraft mc = Minecraft.getInstance();
        Entity t = mc.level == null ? null : mc.level.getEntity(targetId);
        Archetype.Move m = Archetype.Move.byId(b.move());
        Archetype.MoveDef def = st == null || m == null ? null : st.move(m);
        if (t == null || def == null) return;
        ArmsFx.hit(t, by, def, b);
        ArmsRecoil.struck(t, b, ArmsRules.heft(st.id()), ms);
    }

    /** Another fighter near us: their move's arc, their blow's sparks and freeze, a stance breaking. */
    static void onSeen(ArmsPayloads.Seen s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        switch (s.what()) {
            case ArmsPayloads.MOVE -> {
                Entity e = mc.level.getEntity(s.entity());
                Archetypes.Loaded st = Archetypes.byIndex(s.aux());
                Archetype.Move m = Archetype.Move.byId(s.aux2());
                if (e != null && st != null && m != null) {
                    ArmsFx.move(e, st, m, 0, false);
                    ArmsBodies.move(e, st, m);
                }
            }
            case ArmsPayloads.CHARGE -> {
                Entity e = mc.level.getEntity(s.entity());
                if (e != null) ArmsBodies.charge(e, ArmsBodies.SEEN_DRAW);
            }
            case ArmsPayloads.DODGE -> {
                Entity e = mc.level.getEntity(s.entity());
                float a = s.aux() * Mth.DEG_TO_RAD;
                if (e != null) ArmsBodies.dodge(e, Mth.cos(a), Mth.sin(a));
            }
            case ArmsPayloads.HIT -> {
                Entity by = mc.level.getEntity(s.other());
                Archetypes.Loaded st = by instanceof net.minecraft.world.entity.LivingEntity l ? Arms.styleOf(l.getMainHandItem()) : null;
                Blow b = Blow.unpack(s.aux2());
                int ms = b.kill() && ArmsComfort.killMoment() ? ArmsRules.killHitstop(s.aux()) : s.aux();
                if (by != null) hitFx(by, s.entity(), st, b, ms);
                CombatFeedback.hitstop(s.entity(), ms);
            }
            case ArmsPayloads.BREAK -> BROKEN.put(s.entity(), ticks + s.aux());
            case ArmsPayloads.DEFLECT -> {
                Entity e = mc.level.getEntity(s.entity());
                if (e != null) {
                    ArmsBodies.jolt(e);
                    ArmsFx.guardSpark(e, yawTo(e, mc.level.getEntity(s.other())), true);
                }
            }
            case ArmsPayloads.SHOT -> {
                Entity e = mc.level.getEntity(s.entity());
                if (e != null) {
                    ArmsBodies.jolt(e);
                    ArmsFx.guardSpark(e, s.aux2(), s.aux() == ArmsPayloads.DEFLECT);
                }
            }
            case ArmsPayloads.POSTURE -> ArmsStances.onSnapshot(s.entity(), s.aux() / 10F, s.aux2(), s.other());
            default -> { }
        }
    }

    /** The world yaw {@code by} stands at from {@code e} (HURT's convention); ahead of it when there's no one there. */
    private static int yawTo(Entity e, @Nullable Entity by) {
        return by == null ? (int) e.getYRot() : (int) (Mth.atan2(by.getZ() - e.getZ(), by.getX() - e.getX()) * Mth.RAD_TO_DEG) - 90;
    }

    static void reset() {
        if (LEDGER.predicted > 0) {
            com.mojang.logging.LogUtils.getLogger().info("[arms] blows played at once {}, confirmed by the server {}, shown without damage {}",
                    LEDGER.predicted, LEDGER.confirmed, LEDGER.unconfirmed);
        }
        if (PLAYED.length() > 0) {
            com.mojang.logging.LogUtils.getLogger().info("[arms] strikes played: {}", PLAYED);
            PLAYED.setLength(0);
        }
        ArmsGhosts.summary();
        ArmsBlackBox.dump();
        LEDGER.clear();
        LEDGER.predicted = LEDGER.confirmed = LEDGER.unconfirmed = 0;
        moveSeq = aimedId = focusId = pendingSeq = -1;
        pending = 0;
        pendingBreak = -1;
        move = null;
        style = null;
        swishAt = -1;
        biteAt = -1;
        step = -1;
        buffered = null;
        pressHeld = charging = dodging = plunging = holdOpen = waitHit = drawSeen = false;
        launchRate = launchBlend = 0;
        dodgeCharges = -1;
        resonance = 0;
        BROKEN.clear();
        attackHeld = mineHeld = false;
        BladeTrail.reset();
        ArmsTells.reset();
        ArmsFeel.reset();
        ArmsFx.reset();
        ArmsStances.reset();
        ArmsBodies.reset();
        ArmsChop.reset();
    }
}
