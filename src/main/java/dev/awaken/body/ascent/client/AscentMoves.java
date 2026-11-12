package dev.awaken.body.ascent.client;

import dev.awaken.body.ascent.AirRules;
import dev.awaken.body.ascent.AscentAnchors;
import dev.awaken.body.ascent.AscentContent;
import dev.awaken.body.ascent.AscentPayloads;
import dev.awaken.body.ascent.AscentRules;
import dev.awaken.body.ascent.Windways;
import dev.awaken.body.client.feel.FeelConfig;
import dev.awaken.body.ascent.AscentState;
import dev.awaken.body.ascent.AscentSwing;
import dev.awaken.body.ascent.LevelSpace;
import dev.awaken.body.client.feel.FeelDirector;
import dev.awaken.core.client.AwakenKeys;
import dev.awaken.veil.Veil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The local player's Ascent, predicted. Every airborne state keeps its own velocity ({@link AscentRules.Kin}) and, in
 * the movement-input event (after this tick's keys are read, before vanilla moves the body), sets the body's motion to
 * it: vanilla's collision then moves the body, so walls and floors are the same ones the server sees, and vanilla's
 * air drag (which would eat a swing in a second) never touches the speed we keep. What vanilla's own air control will
 * add from the keys is taken off first, since each state steers in its own way. A collision shows up next tick as the
 * body ending short of where it was sent, and that part of the velocity is gone (a wall stops you; a floor lands you).
 * <p>
 * Controls (Phase 0): the rope key (Left Alt, its own since §17.30) fires and holds the rope; letting go flies on with the swing's
 * speed; jump on the rope jumps off it. Forward pumps the swing at the bottom of the arc, sideways turns it, sneak
 * reels in. Jump in the air opens the wings; jump while gliding takes flight; sprint boosts, jump climbs and sneak
 * sinks while flying; sneak while gliding folds the wings into a dive.
 */
public final class AscentMoves {
    private AscentMoves() {}

    static AscentState state = AscentState.NONE;
    static int tiers;
    /** The six mastery levels, 4 bits each (AscentMemory.Track order), from the server. */
    static int mastery;
    /** The server has sent the levels once this session (later changes are levels gained). */
    static boolean masterySynced;
    /** Skyborn's Aether (0..1, SPEC §17.8), predicted here; the server keeps its own at the cheapest drain. */
    static double aether = 1;
    static int aetherOutAt = -100;
    /** This glide began because the meter ran dry in flight: sprint still held takes flight again once it refills. */
    private static boolean glideFromEmpty;
    static final AscentRules.Kin K = new AscentRules.Kin();
    private static final double[] S = new double[3], AIR = new double[3];
    private static final float[] LINE = new float[2], VIEW = new float[2];
    private static boolean windHeld;

    // the rope
    static double ax, ay, az, len, lenTarget;
    /** The block the rope bit (null: it bit nothing we could find, so it is never "gone"). */
    private static net.minecraft.core.@org.jspecify.annotations.Nullable BlockPos anchorBlock;
    /** Ticks until a fired rope bites (it flies out first: the anticipation); 0 once it holds. */
    static int ropeTravel;
    static boolean ropeTaut;
    /** The block the last rope held, and when it was let go (the next throw passes through it for a second). */
    private static @org.jspecify.annotations.Nullable BlockPos leftAnchor;
    private static int leftAt = -100;
    /** A line thrown for you (the vow's), or a tapped throw (SPEC §62): held until it arrives, key or no key. */
    static boolean heldForYou;
    /**
     * After a zip to a ledge: ticks left vaulting onto it, the block it ends on (its centre, top and bottom), and the
     * edge it goes over (a unit step out from that block). Where the zip started (which side it came up from).
     */
    static int pullTicks;
    static double pullX, pullZ, pullTop, pullBottom;
    static int pullEX, pullEZ;
    private static double zipFromX, zipFromZ;
    static double tension;
    // input edges
    private static boolean tetherWas, jumpWas, sneakWas, sprintWas, leftWas, rightWas;
    /** The barrel roll's double tap: the last side tapped and when; when the last roll went. */
    private static int sideTapAt = -100, sideTapDir, rollAt = -100;
    /** The Leaper's air moves used this airtime (bits: 1 double jump, 2 air dash, 4 aimed dive), back on landing or a rope's bite. */
    static int airMovesUsed;
    /**
     * Real presses of the sprint key since the last tick (counted off the key's own events, so a held key let go and
     * pressed again inside one tick counts, and its repeats don't), and when a press the dash couldn't take yet was kept
     * (SPEC §17.32, "The dash's press").
     */
    private static int sprintTaps, dashKeptAt = -1000;
    /** A dive slam is falling: its landing shakes the ground. */
    static boolean slamming;
    /**
     * This carry is a leap's (a double jump or an air dash, SPEC §17.32): forward held into an edge it meets, it stands
     * aside for the body's own climb (a crossing, a mantle, the hang).
     */
    private static boolean leapt;
    /**
     * The aimed dive (SPEC §17.33): flying to (diveX, diveY, diveZ), diveAge whole ticks in, begun at diveAt; diveThere
     * once the point is reached (straight down from there).
     */
    private static boolean diving, diveThere;
    private static double diveX, diveY, diveZ;
    private static int diveAge, diveAt = -1000;
    /** This tick's keys, for the first-person stances (AscentFp). */
    static boolean keyFwd, keySprint, keyJump, keySneak;
    private static int lastJumpPress = -100;
    // last tick's request to vanilla, to see what a collision took
    private static double lastX, lastY, lastZ, wantDx, wantDy, wantDz;
    private static boolean sent;
    /** The vertical speed before last tick's collision (a landing's impact). */
    private static double impactVy;
    // the clock and the moments the feel reads
    static int ticks, stateAge, groundTicks;
    static int fireAt = -100, biteAt = -100, releaseAt = -100, launchAt = -100, deployAt = -100, foldAt = -100, boomAt = -100,
            landAt = -100, flapAt = -100, bonkAt = -100, missAt = -100, soarAt = -100;
    static float releaseQuality, landSpeed, bonkSpeed, biteStrength;
    static boolean boomArmed = true;
    /** A rope that missed: where it flew to (drawn for a moment, falling slack). */
    static double missX, missY, missZ;

    // ---- the rope's fittings and the rappel (SPEC §28.5) ----
    /** The fittings on this player's rope (RopeKit's line and reel), from the server. */
    static int kitLine, kitReel;
    /** Blocks paid out this rappel (the bites' phase), and this tick's pay-out (0 while braking). */
    static double paid, payNow;
    /** When the last bite caught, the last kick off the wall and the feet's last landing back on it. */
    static int catchAt = -100, kickAt = -100, plantAt = -100;
    /** The wall at the feet (its outward normal; both 0 when none is in reach). */
    static double wallNX, wallNZ;
    /** Winding the rope in this tick (a sneak's reel or the fit as it bites): the gold reel shows. */
    static boolean reeling;
    /** The rappel's length as last sent to the server, and when. */
    private static double sentLen;
    private static int sentAt = -100;
    private static final net.minecraft.core.BlockPos.MutableBlockPos PROBE = new net.minecraft.core.BlockPos.MutableBlockPos();

    // ---- the assisted swing (SPEC §17.31) ----
    private static final LevelSpace SPACE = new LevelSpace();
    /** The missed rope's look (it stops at the first block it meets); whether the last pick was the look's own target. */
    private static final dev.awaken.body.ascent.SwingSpace.Hit MISS_HIT = new dev.awaken.body.ascent.SwingSpace.Hit();
    private static boolean pickAimed;
    private static final AscentSwing.Work WORK = new AscentSwing.Work();
    private static final AscentSwing.Pick PICK = new AscentSwing.Pick();
    private static final AscentRules.Kin HAND = new AscentRules.Kin();
    /** The swing's rope (its anchor and lengths mirror ax..lenTarget each tick; it keeps the catch and the bend). */
    static final AscentSwing.Rope ROPE = new AscentSwing.Rope();
    /** Where a press now would throw (drawn in the world while {@link #markerOn}). */
    static final AscentSwing.Pick MARKER = new AscentSwing.Pick();
    static boolean markerOn;
    /** The tick the marker last looked, and whether what it showed was a ring or branch straight overhead (fire's own). */
    private static int markerAt;
    private static boolean markerOverhead;
    /** A press takes what the marker shows when it looked this recently (ticks; it looks every second or fourth). */
    private static final int MARKER_FRESH = 4;
    /** This rope is an assisted swing (no yank, no reel to a fit: the swing fits itself). */
    static boolean swinging;
    /** Ticks a let-go has waited for a clear flight; ticks before a held rope throws again. */
    private static int heldOn, fireCool;
    /** This rope was thrown by a press (a let-go within a tap of it carries the throw through), not by the chain. */
    private static boolean pressThrow;
    /** The held rope's chain, a link at a time (SPEC §62); why the rope now leaving failed, or null. */
    private static final dev.awaken.body.ascent.RopeChain CHAIN = new dev.awaken.body.ascent.RopeChain();
    private static @org.jspecify.annotations.Nullable String linkFail;
    /** The rope now leaving got where it was thrown (a zip at its lip, a sling at its point, a let-go, a launch). */
    private static boolean linkArrived;
    /** The swing's low point passed, and the last let-go in its window (the feel's beats). */
    static int bottomAt = -100, snapAt = -100, swingCatchAt = -100;
    /** This tick's input (the Runner's wall-run takes it when a swing hands the body over). */
    private static @org.jspecify.annotations.Nullable ClientInput input;

    // ---- the rope finds the way (SPEC §62) ----
    /** A sling: the ZIP state pulling toward a point over the top the rope bit (the rope still runs to the block). */
    static boolean slinging;
    static double sx, sy, sz;
    private static int slingAge, slingStall;
    /** The block the server last refused a rope at, and when: not thrown at again for a second. */
    private static @org.jspecify.annotations.Nullable BlockPos refusedBlock;
    private static int refusedAt = -100;
    /** Ticks on the ground with no state: the held rope's hop into the next rope waits for a real landing. */
    private static int groundFor;
    /** A top reached with forward held is sprung off, not stood on (Tether mastery 3). */
    private static boolean spring;
    /** How well a let-go now is timed (0..1), eased, for the rope's glow and hum; when a sling last let go. */
    static float windowNow;
    static int slingAt = -100;

    /**
     * The step the body really took last tick (blocks): where it is now less where it was at the last input step. Inside
     * the input step the game has not moved the body yet this tick, so {@code x - xo} is nothing there: every move begun
     * from the ground or a fall used to start with no speed across the ground, and the held rope's hop from a landing,
     * which asks whether you are running, never fired (SPEC §62, found by the tour of the built game).
     */
    private static double seenX, seenZ, stepX, stepZ;
    private static boolean seenValid;

    // ---- the air's contract and the moves it earned (SPEC §62, AirRules) ----
    /**
     * While a state holds the body its game motion is left at nothing between steps (as the server leaves its copy):
     * whatever is found there at the next step was put there from outside, set or added alike. True when it was left so.
     */
    private static boolean zeroed;
    /** The same, kept until the next step is asked of the game: a state that ends before then hands its speed back. */
    private static boolean rested;
    /** Pushes said plainly (the server's Push, MovementHooks.push), waiting for the next step. */
    private static double pushX, pushY, pushZ;
    /** A cast of your own: when the last one went out, and whether it set the body's motion (read at the next step). */
    static int castAt = -1000;
    private static boolean castMotion;
    /** The aim hold: the line the flight keeps, whether it is held, and when it was let go. */
    private static float aimYaw, aimPitch;
    static boolean aiming;
    private static int aimEndAt = -1000;
    private static final float[] STEER = new float[2];
    /** The flare's approach last seen; the skim (ticks in a row); a takeoff's crouch and when it left the ground. */
    private static int flareAt = -100, skimFor, sneakFor, takeoffAt = -100;
    /** Skimming a surface this tick, and how high it is (the wake's and the sound's). */
    static boolean skimming;
    static double skimY;
    /** The rope strike: the creature the rope has (-1 none), and when the last one began. */
    static int strikeTarget = -1;
    private static int strikeAt = -100;
    private static boolean attackWas;

    private static final boolean TRACE = System.getProperty("awaken.autopilot") != null;

    static void trace(String what) {
        if (!TRACE) return;
        LocalPlayer p = Minecraft.getInstance().player;
        dev.awaken.Awaken.LOG.info("[ascent] t{} {} {}{}", ticks, state, what,
                p == null ? "" : String.format(" at (%.2f, %.2f, %.2f) v %.2f", p.getX(), p.getY(), p.getZ(), K.speed()));
    }

    public static boolean has(AscentPayloads.Tier t) {
        return t.in(tiers);
    }

    /** This player's mastery level on a track (0-10). */
    public static int level(dev.awaken.body.ascent.AscentMemory.Track t) {
        return (mastery >> (4 * t.ordinal())) & 15;
    }

    private static int lv(dev.awaken.body.ascent.AscentMemory.Track t) {
        return level(t);
    }

    /** Whether an Ascent state owns the body right now (the Awakened Body's grounded moves stand aside). */
    public static boolean active() {
        return state != AscentState.NONE;
    }

    public static AscentState state() {
        return state;
    }

    public static double anchorX() {
        return ax;
    }

    public static double anchorY() {
        return ay;
    }

    /** An assisted swing holds the body (SPEC §17.31). */
    public static boolean swinging() {
        return swinging && state == AscentState.TETHER;
    }

    public static double anchorZ() {
        return az;
    }

    /** The client clock (ticks in a world). */
    public static int clock() {
        return ticks;
    }

    /** When the last rope was fired (client clock). */
    public static int fireTick() {
        return fireAt;
    }

    /** How fast the air goes past (blocks a tick): the state's speed, else the body's own motion (a fall, a sprint). */
    public static double windSpeed() {
        if (state != AscentState.NONE) return K.speed();
        LocalPlayer p = Minecraft.getInstance().player;
        return p == null || p.isPassenger() ? 0 : p.getDeltaMovement().length();
    }

    /** The speed the state keeps (blocks a tick), 0 on the ground. */
    public static double speed() {
        return state == AscentState.NONE ? 0 : K.speed();
    }

    /** The velocity the state keeps (blocks a tick), into {@code out}; zero on the ground. */
    public static void velocity(double[] out) {
        boolean on = state != AscentState.NONE;
        out[0] = on ? K.vx : 0;
        out[1] = on ? K.vy : 0;
        out[2] = on ? K.vz : 0;
    }

    // ---- input ----------------------------------------------------------------------------------------------------

    static void onInput(MovementInputUpdateEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (!(e.getEntity() instanceof LocalPlayer p) || p != mc.player) return;
        ClientInput in = e.getInput();
        input = in;
        stepX = seenValid ? p.getX() - seenX : 0;
        stepZ = seenValid ? p.getZ() - seenZ : 0;
        // a teleport is no step
        if (stepX * stepX + stepZ * stepZ > 9) stepX = stepZ = 0;
        seenX = p.getX();
        seenZ = p.getZ();
        seenValid = true;
        Input k = in.keyPresses;
        boolean tether = AwakenKeys.ROPE.isDown() && mc.gui.screen() == null;
        boolean jump = k.jump(), sneak = k.shift(), sprint = k.sprint();
        boolean tetherPressed = tether && !tetherWas, jumpPressed = jump && !jumpWas, sneakPressed = sneak && !sneakWas;
        // sprint's press: the key going down, or a real press since the last tick (a quick re-tap of a held key, a toggle
        // sprint's click), which the key's state alone misses (12 Nov 2026, Euan: "air dashes don't work after any
        // movement like double jump, wings, or rope"; the free-run holds sprint, so the dash's press is often a re-tap)
        boolean sprintPressed = AscentRules.sprintPress(sprint, sprintWas, sprintTaps) && mc.gui.screen() == null;
        sprintTaps = 0;
        if (tetherPressed) CHAIN.press();
        if (!tether && tetherWas) pressEnded();
        boolean attack = mc.options.keyAttack.isDown() && mc.gui.screen() == null, attackPressed = attack && !attackWas;
        attackWas = attack;
        int sidePressed = k.right() && !rightWas ? 1 : k.left() && !leftWas ? -1 : 0;
        leftWas = k.left();
        rightWas = k.right();
        tetherWas = tether;
        jumpWas = jump;
        sneakWas = sneak;
        sprintWas = sprint;
        double fwd = (k.forward() ? 1 : 0) - (k.backward() ? 1 : 0);
        // what the hands in first person follow (AscentFp)
        keyFwd = fwd > 0;
        keySprint = sprint;
        keyJump = jump;
        keySneak = sneak;
        double right = (k.right() ? 1 : 0) - (k.left() ? 1 : 0);
        if (off(p)) {
            if (state != AscentState.NONE) end(p, "off", false);
            sent = false;
            return;
        }
        // this tick's winding and paying out (the reel's look, the pay-out's sound) start from nothing
        reeling = false;
        payNow = 0;
        if (state != AscentState.NONE && !reconcile(p)) return;
        stateAge++;
        // Shoulder Surfing's decoupled view: in the air the body looks where the camera looks (you steer with the view)
        if (state != AscentState.NONE && dev.awaken.body.client.feel.ShoulderView.aim(VIEW)) {
            p.setYRot(VIEW[0]);
            p.setXRot(VIEW[1]);
        }
        float yaw = p.getYRot(), pitch = p.getXRot();
        boolean assist = FeelConfig.ropeAssist();
        AscentSwing.guide = assist && FeelConfig.ropeGuide();
        AscentSwing.hold = FeelConfig.ropeHold();
        if (fireCool > 0) fireCool--;
        windowNow *= 0.8F;
        // the rope held between swings throws again by itself (the chain), in the air, once the last let-go is behind;
        // never after a link that went nowhere or a press that missed (until the next press), and never while a sling's
        // flight still rises over the top it was thrown past (SPEC §62, "The held chain, a link at a time")
        boolean chain = assist && tether && !tetherPressed && fireCool == 0 && !CHAIN.stopped() && !slingRising()
                && has(AscentPayloads.Tier.TETHER);
        marker(p, assist);
        // the aim hold (SPEC §62): on wings and in flight, with the guard or a power's key held, the flight keeps the line
        // it had and the look is free; let go, and it follows the look again over a few ticks. A hit ends the hold.
        float steerYaw = yaw, steerPitch = pitch;
        if (state.wings) {
            boolean hold = aimKey(mc) && ticks - bonkAt > 1;
            if (hold && !aiming) {
                double sp = K.speed();
                aimYaw = sp > 0.2 ? (float) (Mth.atan2(-K.vx, K.vz) * Mth.RAD_TO_DEG) : yaw;
                aimPitch = sp > 0.2 ? (float) (-Math.asin(Mth.clamp(K.vy / sp, -1, 1)) * Mth.RAD_TO_DEG) : pitch;
                trace("aim hold");
            }
            if (!hold && aiming) aimEndAt = ticks;
            aiming = hold;
            AirRules.aimSteer(yaw, pitch, aimYaw, aimPitch, aiming, ticks - aimEndAt, STEER);
            steerYaw = STEER[0];
            steerPitch = STEER[1];
        } else {
            aiming = false;
            aimEndAt = -1000;
        }
        // the rope strike (SPEC §62): an attack on the rope or flying free, at a creature out of the hand's reach
        if (attackPressed && state != AscentState.NONE && state != AscentState.RAPPEL && strikeTarget < 0 && strike(p, yaw, pitch)) {
            apply(p, in);
            return;
        }
        // the aimed dive (SPEC §17.33): an attack in a leap's carry, at nothing the hand or the rope takes, flies the body
        // down to the point under the crosshair
        if (attackPressed && state == AscentState.CARRY && leapt && !slamming && !diving && aimDive(p, mc, yaw, pitch)) {
            diveTick(p, yaw);
            apply(p, in);
            return;
        }
        if (attackPressed && TRACE && !p.onGround() && !(state == AscentState.CARRY && leapt && !slamming && !diving)) {
            trace(state == AscentState.CARRY && slamming ? "no dive: a slam" : diving ? "no dive: diving already" : "no dive: not in a leap");
        }
        switch (state) {
            case NONE -> {
                // a Runner move (a wall-run, a hang) or a climb (a mantle, a crossing: SPEC §17.32, a press during one
                // never starts a leap) holds the body: only the rope cuts in
                boolean runner = dev.awaken.body.client.RunnerMoves.busy() || dev.awaken.body.Body.isMantling(p);
                if (p.onGround()) {
                    airMovesUsed = 0;
                    dashKeptAt = -1000;
                }
                groundFor = p.onGround() ? groundFor + 1 : 0;
                // the takeoff's crouch (SPEC §62): looking up and sneaking on the ground, the folded wings lift
                boolean crouch = !runner && sneak && has(AscentPayloads.Tier.SKYBORN) && p.onGround() && pitch <= -AirRules.TAKEOFF_LOOK
                        && aether >= Math.max(AirRules.TAKEOFF_AETHER, AscentRules.AETHER_START);
                sneakFor = crouch ? sneakFor + 1 : 0;
                if (sneakFor > 0) WingLayer.gather(p, sneakFor / (float) AirRules.TAKEOFF_SNEAK);
                // looking up from the ground with flight to spare: the takeoff's tip
                if (!runner && !sneak && p.onGround() && pitch <= -AirRules.TAKEOFF_LOOK - 15 && has(AscentPayloads.Tier.SKYBORN)
                        && aether >= Math.max(AirRules.TAKEOFF_AETHER, AscentRules.AETHER_START)) {
                    // The Way Up's lesson first (SPEC §63.8): the keys on the sky; the written tip when that can no longer teach
                    if (!Lesson.offerMove(Lesson.Kind.TAKEOFF, null, -1)) dev.awaken.core.api.TipHooks.client().offer(dev.awaken.body.ascent.AscentTips.TAKEOFF);
                }
                if (tetherPressed && has(AscentPayloads.Tier.TETHER)) {
                    fire(p);
                } else if (jumpPressed && AirRules.takeoffReady(pitch, sneakFor, aether, p.onGround())) {
                    takeoff(p);
                } else if (chain && !runner && !p.onGround() && fire(p, false)) {
                    // walked or ran off an edge with the rope held: it swings
                } else if (AscentSwing.guide && chain && !runner && groundFor >= 3 && fwd > 0 && ticks - fireAt >= 10
                        && (running(p) || p.horizontalCollision)) {
                    // landed with the rope held and still running, or stopped against something with forward held: a hop
                    // into the next rope (SPEC §62)
                    if (!fire(p, false) && TRACE && ticks % 20 == 0) trace("the held rope finds nothing to throw at from the ground");
                } else if (runner) {
                    // the Runner's own jumps (a wall jump, a ledge jump)
                } else if (jumpPressed && !p.onGround() && dev.awaken.body.client.ClientMoves.airCatch(p, fwd > 0)) {
                    // a jump in the air at an edge in reach climbs it, as one from the ground would (SPEC §17.32): the Body
                    // starts it this tick, and the double jump is still yours
                } else if (!p.onGround() && leaperMove(p, jumpPressed, sprintPressed, sneakPressed, true)) {
                    // a double jump, an air dash or a dive slam took the body into the carry
                } else if (jumpPressed && !p.onGround() && has(AscentPayloads.Tier.WINGS) && canDeploy(p)
                        && !dev.awaken.body.client.RunnerMoves.wallBeside(p)) {
                    startGlide(p);
                } else if (jumpPressed && has(AscentPayloads.Tier.SKYBORN) && ticks - lastJumpPress <= 7 && !p.onGround()
                        && !dev.awaken.body.client.RunnerMoves.wallBeside(p)) {
                    startSoar(p);
                }
            }
            case TETHER -> {
                keepDash(p, sprintPressed, "on the rope");
                // a tapped swing taken in hand again is a held one: the key's let-go is its let-go
                if (tether && swinging) heldForYou = false;
                if (!tether && swinging && pressThrow && !heldForYou && (ropeTravel > 0 || ticks - fireAt <= AscentSwing.T.tapTicks)) {
                    // a tap, not a hold (SPEC §62, "The held chain, a link at a time"): the rope thrown bites and swings
                    // its first arc, letting go at its top by itself; never a second rope at something else
                    heldForYou = true;
                    trace("a tap: the swing goes on to its top");
                }
                if (!tether && swinging && heldForYou) {
                    // the held rope's swing never asks to let go: the tapped one lets go at the top that marks it swung
                    // (without the held rope the swing's own top lets go, as ever)
                    if (AscentSwing.hold && ROPE.swung && ropeTravel == 0) {
                        trace("the tapped swing's top lets go");
                        release(p);
                    } else {
                        tetherTick(p, yaw, fwd, right, false);
                    }
                } else if (!tether && swinging && !AscentSwing.hold && heldOn < AscentSwing.T.holdOn
                        && !AscentSwing.letGoClear(SPACE.at(p.level(), p), K, ROPE, yaw, WORK)) {
                    // letting go now would throw you into what's just ahead: the rope holds a moment longer (the held
                    // rope never does: the key let go is the let-go, that tick)
                    heldOn++;
                    tetherTick(p, yaw, fwd, right, false);
                } else if (!tether) {
                    release(p);
                } else if (jumpPressed && sneak && ropeTravel == 0) {
                    // sneak and jump: the rope is paid out instead of reeled in (the rappel)
                    startRappel(p);
                    rappelTick(p, yaw, fwd, right, true, false);
                } else if (jumpPressed && ropeTravel == 0) {
                    launch(p);
                } else {
                    tetherTick(p, yaw, fwd, right, sneak);
                }
            }
            case RAPPEL -> {
                if (!tether) {
                    release(p);
                } else if (!sneak) {
                    // sneak let go: hanging on the line again, a swing like any other
                    setState(p, AscentState.TETHER);
                    tetherTick(p, yaw, fwd, right, false);
                } else {
                    rappelTick(p, yaw, fwd, right, jump, jumpPressed);
                }
            }
            case ZIP -> {
                keepDash(p, sprintPressed, "on the pull");
                if (strikeTarget >= 0) {
                    // a rope strike holds itself: the rope has the creature until the blow or the let-go
                    zipTick(p, yaw);
                } else if (!tether && assist && pressThrow && !heldForYou && ticks - fireAt <= AscentSwing.T.tapTicks) {
                    // a tap, not a hold (SPEC §62, "The held chain, a link at a time"): the zip or sling thrown goes on
                    // by itself to its lip or its let-go; never a second rope at something else
                    heldForYou = true;
                    trace("a tap: the zip goes on by itself");
                    zipTick(p, yaw);
                } else if (!tether && !heldForYou) {
                    toCarry(p, "zip let go");
                    carry(yaw, fwd, right);
                } else {
                    zipTick(p, yaw);
                }
            }
            case CARRY -> {
                if (tetherPressed && has(AscentPayloads.Tier.TETHER) && fire(p)) {
                    // swinging again
                } else if (diving) {
                    // the aimed dive holds its line: only the rope cuts in
                    diveTick(p, yaw);
                } else if (chain && pullTicks == 0 && fire(p, false)) {
                    // the rope held: the next swing as soon as there's one
                } else if (jumpPressed && dev.awaken.body.client.RunnerMoves.wallBeside(p)) {
                    // flying free into a wall: hand the body to the Runner, which runs along it this same tick
                    p.setDeltaMovement(K.vx, K.vy, K.vz);
                    end(p, "onto a wall", true);
                    return;
                } else if (leapt && !slamming && (fwd > 0 || jumpPressed) && dev.awaken.body.client.ClientMoves.airCatch(p, fwd > 0)) {
                    // a leap that meets an edge (SPEC §17.32): a ledge or stair top is mantled, a fence or wall crossed, a
                    // higher lip hung from, the same tick, as a jump from the ground would (the leap's own climb, no dead window)
                    p.setDeltaMovement(K.vx, K.vy, K.vz);
                    end(p, "onto an edge", true);
                    return;
                } else if (leaperMove(p, jumpPressed, sprintPressed, sneakPressed, false)) {
                    // the move set this tick's velocity; the carry flies it
                    carry(yaw, fwd, right);
                } else if (jumpPressed && has(AscentPayloads.Tier.WINGS) && canDeploy(p)) {
                    startGlide(p);
                } else {
                    if (pullTicks > 0) pullOver();
                    else if (assist && tether && has(AscentPayloads.Tier.TETHER) && steerHeld(p, yaw, pitch, chain)) break;
                    carry(yaw, fwd, right);
                }
            }
            case GLIDE -> {
                if (tetherPressed && has(AscentPayloads.Tier.TETHER) && fire(p)) {
                    fold(p);
                } else if (has(AscentPayloads.Tier.SKYBORN)
                        && (jumpPressed || AscentRules.sprintTakesFlight(sprintPressed, sprint, glideFromEmpty, aether)) && startSoar(p)) {
                    // flight again: a jump, or sprint whenever the meter allows (SPEC §17.8)
                } else if (sprintPressed && dashOpen(p, true)) {
                    // sprint that can't take a glide into flight (no Skyborn, or the meter too low): the wings fold into
                    // the Leaper's dash (SPEC §17.32, "The dash's press")
                    fold(p);
                    toCarry(p, "wings folded into a dash");
                    leaperMove(p, false, true, false, false);
                    carry(yaw, fwd, right);
                } else if (sneakPressed) {
                    fold(p);
                    toCarry(p, "wings folded");
                    carry(yaw, fwd, right);
                } else {
                    // in a windway, looking roughly along it, the air holds your line (SPEC §17.12)
                    Windways.Way way = Windways.inside(p.level(), K.x, K.y, K.z);
                    boolean held = way != null && Windways.steer(way, K.x, K.y, K.z, steerYaw, steerPitch, LINE);
                    // the skim (SPEC §62): near a surface at speed the air under the wings carries more
                    skim(p);
                    AscentRules.glideStep(K, held ? LINE[0] : steerYaw, held ? LINE[1] : steerPitch, glideDrag() * (skimming ? AirRules.SKIM_DRAG : 1));
                    flareCheck(p);
                    if (held != windHeld) trace(held ? "a windway holds the glide" : String.format("out of the windway at %.2f b/t", K.speed()));
                    windHeld = held;
                    WindwayRenderer.riding(held ? way : null);
                    boomCheck(p);
                }
            }
            case SOAR -> {
                if (tetherPressed && has(AscentPayloads.Tier.TETHER) && fire(p)) {
                    fold(p);
                } else {
                    if (jumpPressed && fwd <= 0 && stateAge > 4) flap(p);
                    else if (fwd <= 0 && K.hspeed() < 0.45 && stateAge > 10
                            && (FeelConfig.wingsFeathers() ? WingLayer.downstroke(p.getId()) : ticks - flapAt >= 18)) {
                        // hovering: a slow beat to hold the air (no lift of its own: the hover holds you); feathered
                        // wings beat on their own clock, and the sound follows the wing
                        if (!FeelConfig.wingsFeathers()) flapAt = ticks;
                        AscentSounds.local(p, AscentContent.FLAP.get(), 0.45F, 0.85F + p.getRandom().nextFloat() * 0.1F);
                    }
                    // in flight the feathered wings beat on their own clock: each downstroke is heard, softly
                    if (FeelConfig.wingsFeathers() && WingLayer.mode(p.getId()) == WingLayer.FLYING && WingLayer.downstroke(p.getId())) {
                        AscentSounds.move(p, "beat", null, 0.7F, 0.95F + p.getRandom().nextFloat() * 0.1F);
                    }
                    if (sidePressed != 0) sideTap(p, sidePressed);
                    if (ticks - takeoffAt < AirRules.TAKEOFF_TICKS) {
                        // a takeoff's rise: straight up on its own curve, the flight's own step after it
                        K.vx *= 0.9;
                        K.vz *= 0.9;
                        K.vy = AirRules.takeoffStep(ticks - takeoffAt);
                        K.x += K.vx;
                        K.y += K.vy;
                        K.z += K.vz;
                    } else {
                        AscentRules.soarStep(K, steerYaw, steerPitch, fwd, right, jump, sneak, k.sprint(), S);
                    }
                    flareCheck(p);
                    boomCheck(p);
                    aetherDrain = k.sprint() ? AscentRules.AETHER_BOOST
                            : fwd <= 0 && K.hspeed() < 0.45 ? AscentRules.AETHER_HOVER : AscentRules.AETHER_CRUISE;
                }
            }
        }
        if (jumpPressed) lastJumpPress = ticks;
        if (state != AscentState.NONE) moveTips(p, yaw, pitch);
        aetherTick(p);
        // moving air (updrafts, windways): the free states feel it from the next tick
        double share = switch (state) {
            case CARRY -> AscentRules.AIR_CARRY;
            case GLIDE -> AscentRules.AIR_GLIDE;
            case SOAR -> AscentRules.AIR_SOAR;
            default -> 0;
        };
        if (share > 0 && dev.awaken.core.api.AirHooks.sample(p.level(), K.x, K.y, K.z, AIR)) AscentRules.air(K, AIR, share);
        // a lift lifts the free states too (Levitation, and so the powers' Updraft): the game's own rule on our velocity
        if (share > 0) {
            var lift = p.getEffect(net.minecraft.world.effect.MobEffects.LEVITATION);
            if (lift != null) AirRules.levitate(K, lift.getAmplifier());
        }
        if (state != AscentState.GLIDE && skimming) skimEnd();
        if (state != AscentState.NONE) apply(p, in);
        else sent = false;
    }

    static boolean off(LocalPlayer p) {
        return p.isPassenger() || p.isSleeping() || p.isSpectator() || !p.isAlive() || p.getAbilities().flying || p.isFallFlying()
                || p.isInWater() || p.isInLava() || Veil.isSpirit(p) || tiers == 0 || dev.awaken.core.api.MovementHooks.burdened(p); // (SPEC §28.7) carrying someone
    }

    /** High enough to open wings: falling, or rising slowly, with room under you. */
    private static boolean canDeploy(LocalPlayer p) {
        double floor = AscentAnchors.floorUnder(p.level(), p, p.getX(), p.getY() + 0.5, p.getZ(), 4);
        return p.getY() - floor > 2.2 && p.getDeltaMovement().y < 0.25;
    }

    /**
     * Reads what last tick's move really did. A wall or a floor that stopped part of it takes that part of the speed;
     * a landing ends the state. False when the state ended here.
     */
    private static boolean reconcile(LocalPlayer p) {
        if (!sent) {
            K.x = p.getX();
            K.y = p.getY() + (state.rope ? AscentRules.HAND_HEIGHT : 0);
            K.z = p.getZ();
            return true;
        }
        double mx = p.getX() - lastX, my = p.getY() - lastY, mz = p.getZ() - lastZ;
        lastMoved = Math.abs(mx) + Math.abs(my) + Math.abs(mz);
        double ex = mx - wantDx, ey = my - wantDy, ez = mz - wantDz;
        if (ex * ex + ey * ey + ez * ez > 9) {
            if (!AirRules.blinkKeeps(state, ticks - castAt, ex * ex + ey * ey + ez * ez)) {
                // a teleport (the server put us somewhere): whatever we were doing is over, and its speed with it
                zeroed = rested = false;
                end(p, "moved", false);
                return false;
            }
            // a blink of your own (SPEC §62): the state goes with the body and keeps its speed; the teleport's own motion is no push
            trace("a blink: the state goes with the body");
            K.x = p.getX();
            K.y = p.getY();
            K.z = p.getZ();
            zeroed = false;
            castMotion = false;
            pushX = pushY = pushZ = 0;
            groundTicks = 0;
            return true;
        }
        double hBefore = K.hspeed();
        boolean bonked = false;
        if (Math.abs(ex) > 0.02) {
            K.vx = mx;
            bonked = true;
        }
        if (Math.abs(ez) > 0.02) {
            K.vz = mz;
            bonked = true;
        }
        impactVy = K.vy;
        if (Math.abs(ey) > 0.02) K.vy = my;
        K.x = p.getX();
        K.y = p.getY() + (state.rope ? AscentRules.HAND_HEIGHT : 0);
        K.z = p.getZ();
        if (state == AscentState.RAPPEL) {
            // a rappel's bound back into the wall is the feet landing on it, never a bonk
            if (bonked && p.horizontalCollision && hBefore - K.hspeed() > 0.06 && ticks - kickAt > 3 && ticks - plantAt > 6) {
                plant(p, (float) (hBefore - K.hspeed()));
            }
        } else if (bonked && p.horizontalCollision && hBefore - K.hspeed() > 0.45) {
            bonkAt = ticks;
            bonkSpeed = (float) (hBefore - K.hspeed());
            trace("bonk " + String.format("%.2f", bonkSpeed));
            FeelDirector.bonk(bonkSpeed);
            AscentFlow.bonk();
        }
        outside(p);
        groundTicks = p.onGround() ? groundTicks + 1 : 0;
        boolean landed = switch (state) {
            case ZIP -> false;
            // a swing that scrapes the floor goes on; one that stays down has landed (a rappel set down at the bottom too)
            case TETHER, RAPPEL -> groundTicks >= AscentRules.TETHER_GROUND_TICKS;
            default -> groundTicks > 0;
        };
        if (landed) {
            land(p);
            return false;
        }
        return true;
    }

    /** Sets the body's motion to this tick's step and remembers what was asked. */
    private static void apply(LocalPlayer p, ClientInput in) {
        double dx = K.x - p.getX(), dy = K.y - (p.getY() + (state.rope ? AscentRules.HAND_HEIGHT : 0)), dz = K.z - p.getZ();
        // vanilla adds its own air control from the keys on top of whatever we set: take it off first
        Vec2 mv = in.getMoveVector();
        double ix = 0, iz = 0;
        if (mv.lengthSquared() > 1e-6) {
            float speed = p.isSprinting() ? 0.026F : 0.02F;
            double sx = mv.x * 0.98, sz = mv.y * 0.98;
            if (p.isCrouching()) {
                double sneakSpeed = p.getAttributeValue(Attributes.SNEAKING_SPEED);
                sx *= sneakSpeed;
                sz *= sneakSpeed;
            }
            double l2 = sx * sx + sz * sz;
            if (l2 > 1) {
                double l = Math.sqrt(l2);
                sx /= l;
                sz /= l;
            }
            sx *= speed;
            sz *= speed;
            float sin = Mth.sin(p.getYRot() * Mth.DEG_TO_RAD), cos = Mth.cos(p.getYRot() * Mth.DEG_TO_RAD);
            ix = sx * cos - sz * sin;
            iz = sz * cos + sx * sin;
        }
        p.setDeltaMovement(dx - ix, dy, dz - iz);
        rested = false;
        lastX = p.getX();
        lastY = p.getY();
        lastZ = p.getZ();
        wantDx = dx;
        wantDy = dy;
        wantDz = dz;
        sent = true;
        p.resetFallDistance();
    }

    /**
     * A tick of the carry; Flow keeps a little more of the speed. Every tick the same rule (SPEC §62, 11 Nov: the slow
     * beat at a big arc's top, 8 ticks at under half speed, read as lag and is gone), so the flight after a let-go is one
     * smooth arc. Under the autopilot the first 60 ticks after a let-go log their speed (the held-rope tour reads them).
     */
    private static void carry(float yaw, double fwd, double right) {
        AscentRules.carryStep(K, yaw, fwd, right, S, AscentRules.CARRY_DRAG_H + AscentRules.FLOW_CARRY * AscentFlow.flow);
        if (TRACE && ticks - Math.max(releaseAt, launchAt) <= 60) trace(String.format("air %.3f b/t, up %.3f", K.hspeed(), K.vy));
    }

    private static double glideDrag() {
        return 1 - AscentRules.MASTERY_GLIDE_DRAG * lv(dev.awaken.body.ascent.AscentMemory.Track.WINGS);
    }

    // ---- the rope ---------------------------------------------------------------------------------------------------

    /**
     * A sling let go under 2 s ago and the flight still rising over its top: the held key throws nothing yet (12 Nov
     * 2026, Euan: "it quickly sends two different ropes ... just me trying to go onto one block"; the chain threw 2 ticks
     * after the sling's let-go).
     */
    private static boolean slingRising() {
        return ticks - slingAt < 40 && K.vy > 0;
    }

    /** Fires the rope; true when it found an anchor (the state is then TETHER or ZIP). */
    private static boolean fire(LocalPlayer p) {
        return fire(p, true);
    }

    /**
     * Fires the rope: {@code pressed} by the key, else thrown again because it is held (the chain), which says nothing
     * when there is nothing to throw at. With the assisted swing (SPEC §17.31) the rope takes the best anchor along your
     * heading, else zips up to a lip in the look; a ring or branch straight overhead is still zipped to and hung from.
     */
    private static boolean fire(LocalPlayer p, boolean pressed) {
        Vec3 eye = p.getEyePosition();
        // where you look: the camera in Shoulder Surfing's decoupled view, else the body's own look
        boolean view = dev.awaken.body.client.feel.ShoulderView.aim(VIEW);
        float aimYaw = view ? VIEW[0] : p.getYRot(), aimPitch = view ? VIEW[1] : p.getXRot();
        boolean assist = FeelConfig.ropeAssist();
        // a rope just let go of passes through the ring or branch it held: throw past it to the next one up
        AscentAnchors.Anchor a = pressed || !assist
                ? AscentAnchors.find(p.level(), p, eye, aimYaw, aimPitch, reach(p), ticks - leftAt <= 20 ? leftAnchor : null) : null;
        double hx0 = p.getX(), hy0 = p.getY() + AscentRules.HAND_HEIGHT, hz0 = p.getZ();
        boolean overhead = a != null && overhead(a.x() - hx0, a.y() - hy0, a.z() - hz0);
        if (assist && !overhead) {
            // a pull up a face wants a press, to be in the air already, or a face in the way: running, the held rope's hop
            // from the ground only swings or slings
            // a press takes what the marker shows (SPEC §62, "What the marker shows, the press takes"); else it looks again
            AscentSwing.Pick pk = pressed ? shown(p) : null;
            if (pk == null) pk = pick(p, eye, aimYaw, aimPitch, pressed || !p.onGround() || p.horizontalCollision);
            if (pk != null && (refusedLately(p, pk.x, pk.y, pk.z) || spentAnchor(p, pk.x, pk.y, pk.z))) pk = null;
            // the look met something up ahead (a tree, a roof) that the rope passed over for another: say so once
            if (pressed && pk != null && !pickAimed && !Double.isNaN(WORK.aimT) && WORK.aim.y > HAND.y + 1) lookNotTaken(p, pk);
            if (pk != null) {
                fired(p);
                pressThrow = pressed;
                throwTo(p, pk, aimYaw);
                return true;
            }
            if (!pressed) {
                fireCool = 2;
                return false;
            }
        }
        fired(p);
        pressThrow = pressed;
        if (a == null || assist && !overhead) {
            // nothing to bite: the rope flies out and falls back, and you keep falling as you were. With the assist, a
            // press the marker didn't show never pulls you at the block under the crosshair (11 Nov 2026: one key that
            // sometimes swung you on and sometimes yanked you at a wall was hard to read); only a ring or branch
            // straight overhead is zipped to and hung from, and the marker shows that too
            missAt = ticks;
            Vec3 look = p.getLookAngle();
            // the missed rope stops at the first block it meets (SPEC §62, "The rope goes where you look"): never on
            // through the tree you looked at
            double far = SPACE.at(p.level(), p).ray(eye.x, eye.y, eye.z, look.x, look.y, look.z, 18, MISS_HIT) ? Math.max(1, MISS_HIT.t) : 18;
            missX = eye.x + look.x * far;
            missY = eye.y + look.y * far;
            missZ = eye.z + look.z * far;
            if (assist) lookNotTaken(p, null);
            AscentFx.miss(p, missX, missY, missZ);
            // one miss: the key held on waits for the missed rope to fall back before it may throw again (SPEC §62, "The
            // held chain, a link at a time"), never a second rope at once at whatever came into reach a tick later
            CHAIN.missed(ticks);
            trace("rope missed");
            return false;
        }
        Vec3 v = state == AscentState.NONE ? new Vec3(stepX, p.getDeltaMovement().y, stepZ) : null;
        double hx = p.getX(), hy = p.getY() + AscentRules.HAND_HEIGHT, hz = p.getZ();
        swinging = slinging = false;
        ax = a.x();
        ay = a.y();
        az = a.z();
        anchorBlock = AscentAnchors.blockOf(p.level(), ax, ay, az);
        double dx = ax - hx, dy = ay - hy, dz = az - hz;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (v != null) K.set(hx, hy, hz, v.x, v.y, v.z);
        else K.set(hx, hy, hz, K.vx, K.vy, K.vz);
        ropeTravel = Mth.clamp((int) Math.ceil(dist / 16), 1, 3);
        ropeTaut = false;
        // nearly straight overhead (a ring, a branch, a keel): a zip up to it and a hang under it, not a pendulum; with
        // the assist, an aimed point that gives no swing is zipped to (never the old pendulum's reel into it)
        if (dy > AscentRules.TETHER_SWING_MIN_RISE && !overhead && !assist) {
            len = dist;
            // the arc's bottom keeps clear of the ground: the floor where you stand, or under the arc's lowest point
            // (just off the anchor, toward you, so the anchor's own trunk or branch isn't taken for the floor)
            double h = Math.sqrt(dx * dx + dz * dz);
            double bx = h > 1e-3 ? ax - dx / h * 1.5 : ax, bz = h > 1e-3 ? az - dz / h * 1.5 : az;
            double floor = Math.max(AscentAnchors.floorUnder(p.level(), p, hx, hy, hz, dist + 6),
                    AscentAnchors.floorUnder(p.level(), p, bx, ay, bz, dist + 6));
            double fit = ay - floor - AscentRules.TETHER_CLEARANCE - AscentRules.HAND_HEIGHT;
            lenTarget = Mth.clamp(Math.min(dist, fit), AscentRules.TETHER_MIN, dist);
            setState(p, AscentState.TETHER);
            // from the ground, a crouch and a hop: the swing starts in the air
            if (p.onGround()) K.vy = Math.max(K.vy, 0.5);
            trace(String.format("rope out %.1f (fits %.1f)%s", dist, lenTarget, a.aimed() ? "" : " (assisted)"));
        } else {
            len = lenTarget = dist;
            setState(p, AscentState.ZIP);
            zipFromX = hx;
            zipFromZ = hz;
            if (p.onGround()) K.vy = Math.max(K.vy, 0.3);
            trace(String.format("zip %.1f", dist));
        }
        // the first tick runs now
        if (state == AscentState.TETHER) tetherTick(p, p.getYRot(), 0, 0, false);
        else zipTick(p, p.getYRot());
        return true;
    }

    /**
     * A press whose rope didn't go to what the look met: one line in the log (real play shows which trees the rope
     * passed over and why, SPEC §62 "The rope goes where you look"). {@code took}: what it took instead, or null (a miss).
     */
    private static void lookNotTaken(LocalPlayer p, AscentSwing.@org.jspecify.annotations.Nullable Pick took) {
        AscentSwing.Work w = WORK;
        String met = Double.isNaN(w.aimT) ? String.format("nothing within %.0f", reach(p))
                : String.format("%s %.1f off", p.level().getBlockState(new BlockPos(w.aim.bx, w.aim.by, w.aim.bz)).getBlock(), w.aimT);
        // why, for each way the rope could have taken it (real play shows which rule said no)
        String why = Double.isNaN(w.aimT) ? "" : String.format(" (no swing; sling: %s; onto its top: %s; %s)",
                w.aimSling == 0 ? "not asked" : AscentSwing.SLING_NO[w.aimSling], w.aimOnto == 0 ? "not asked" : AscentSwing.ONTO_NO[w.aimOnto],
                w.aim.ny < 0 ? "an underside, not clear to hang from" : "no underside to hang from");
        String what = took == null ? "nothing (a miss)" : String.format("%s %.1f off", took.zip ? "a lip" : took.sling ? "a sling" : "a swing", took.len);
        // a miss: what the marker showed then (it should have shown nothing)
        String shownThen = took != null ? "" : !markerOn ? "; the marker showed nothing"
                : String.format("; the marker showed %s %d ticks before, which no longer held", MARKER.zip ? "a pull" : MARKER.sling ? "a sling" : "a swing", ticks - markerAt);
        dev.awaken.Awaken.LOG.info("[rope] the look met {}, which the rope can't take from here{}; the press took {}{}", met, why, what, shownThen);
    }

    /** Nearly straight overhead (a ring, a branch, a keel): zipped up to and hung under, never swung from. */
    private static boolean overhead(double dx, double dy, double dz) {
        return dy > AscentRules.ZIP_UP_MIN_RISE && Math.sqrt(dx * dx + dz * dz) < dy * AscentRules.ZIP_UP_SLOPE;
    }

    /** The throw's moment: the hand, its sound, the server told. */
    private static void fired(LocalPlayer p) {
        fireAt = ticks;
        CHAIN.thrown(ticks, p.getX(), p.getY(), p.getZ());
        AscentFp.fired();
        AscentSounds.local(p, AscentContent.ROPE_FIRE.get(), 0.8F, 0.95F + p.getRandom().nextFloat() * 0.1F);
        event(AscentPayloads.EV_FIRE, 0);
    }

    /** The hand point and the body's velocity now, into {@link #HAND} (from the ground, with the hop a swing starts with). */
    private static void hand(LocalPlayer p) {
        double hx = p.getX(), hy = p.getY() + AscentRules.HAND_HEIGHT, hz = p.getZ();
        if (state == AscentState.NONE) HAND.set(hx, hy, hz, stepX, p.getDeltaMovement().y, stepZ);
        else HAND.set(hx, hy, hz, K.vx, K.vy, K.vz);
        if (p.onGround()) HAND.vy = Math.max(HAND.vy, 0.5);
    }

    /**
     * What a throw now takes: what you look at, swung from or slung past ({@link AscentSwing#aimed}); else the best swing
     * along the heading; with the guide (SPEC §62), where nothing swings, a sling past a top ahead; else ({@code lips}) the
     * marker's lip or a lip in the look, with the guide only by a clear way; or null.
     */
    private static AscentSwing.@org.jspecify.annotations.Nullable Pick pick(LocalPlayer p, Vec3 eye, float yaw, float pitch, boolean lips) {
        AscentSwing.Pick pk = search(p, eye, yaw, pitch, lips);
        // only where the rope can bite as the server asks (else it refuses it and the held rope stops)
        return pk != null && AscentSwing.bites(SPACE, pk) ? pk : null;
    }

    /**
     * On a press, what the marker shows (SPEC §62, "What the marker shows, the press takes"; 12 Nov 2026, Euan: "there
     * was a blue thing showing it could connect when I clicked", and the press looked again a tick or three on, from where
     * the flight had carried him, and met nothing): its pick, when it looked within {@link #MARKER_FRESH} ticks, the
     * anchor is in the rope's reach and still bites, and the server hasn't refused it lately; its rope as long as the way
     * to it now. Null otherwise (a ring overhead is fire's own).
     */
    private static AscentSwing.@org.jspecify.annotations.Nullable Pick shown(LocalPlayer p) {
        if (!markerOn || markerOverhead || ticks - markerAt > MARKER_FRESH) return null;
        SPACE.at(p.level(), p);
        hand(p);
        PICK.copy(MARKER);
        double dx = PICK.x - HAND.x, dy = PICK.y - HAND.y, dz = PICK.z - HAND.z, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d > reach(p) + 2 || !AscentSwing.bites(SPACE, PICK) || refusedLately(p, PICK.x, PICK.y, PICK.z) || spentAnchor(p, PICK.x, PICK.y, PICK.z)) {
            return null;
        }
        PICK.len = d;
        PICK.fitLen = Math.min(PICK.fitLen, d);
        // a swing whose way has grown long for its fit since it looked reels in to it
        if (!PICK.zip && !PICK.sling && PICK.fitLen < d * AscentSwing.FIT_MIN) PICK.reel = true;
        pickAimed = true;
        return PICK;
    }

    /** The search a press runs ({@link #pick}), before the bite is checked. */
    private static AscentSwing.@org.jspecify.annotations.Nullable Pick search(LocalPlayer p, Vec3 eye, float yaw, float pitch, boolean lips) {
        SPACE.at(p.level(), p);
        hand(p);
        // where you look first (SPEC §62, "The rope goes where you look"): the block under the crosshair, swung from or
        // slung past; else the best along the heading, as ever
        pickAimed = AscentSwing.aimed(SPACE, HAND, eye.x, eye.y, eye.z, yaw, pitch, reach(p), AscentSwing.guide, WORK, PICK);
        if (pickAimed) return PICK;
        if (AscentSwing.findAfter(SPACE, HAND, yaw, reach(p), WORK, PICK)) return PICK;
        if (AscentSwing.guide && AscentSwing.findSling(SPACE, HAND, yaw, reach(p), WORK, PICK)) return PICK;
        if (!lips) return null;
        if (markerOn && MARKER.zip && !Double.isNaN(MARKER.top) && zipOk(MARKER, HAND.x, HAND.y, HAND.z)) return PICK.copy(MARKER);
        return AscentSwing.zipTo(SPACE, eye.x, eye.y, eye.z, yaw, pitch, reach(p), WORK, PICK) && zipOk(PICK, HAND.x, HAND.y, HAND.z) ? PICK : null;
    }

    /** With the guide, a pull up a face is taken only by a clear way to it (never through a crown into a wall). */
    private static boolean zipOk(AscentSwing.Pick lip, double hx, double hy, double hz) {
        return !AscentSwing.guide || lip.len <= AscentSwing.T.wallZip && AscentSwing.zipClear(SPACE, hx, hy, hz, lip);
    }

    /** Moving at a run across the ground (the held rope's hop into the next rope asks for it). */
    private static boolean running(LocalPlayer p) {
        return stepX * stepX + stepZ * stepZ > 0.15 * 0.15;
    }

    /** This press's held rope failed at this point's block after carrying you on: it isn't thrown at again this press. */
    private static boolean spentAnchor(LocalPlayer p, double x, double y, double z) {
        BlockPos b = AscentAnchors.blockOf(p.level(), x, y, z);
        return b != null && CHAIN.spent(b.asLong());
    }

    /** The server refused a rope at this point's block within the last second: it isn't thrown at again yet. */
    private static boolean refusedLately(LocalPlayer p, double x, double y, double z) {
        BlockPos r = refusedBlock;
        return r != null && ticks - refusedAt < 20 && r.equals(AscentAnchors.blockOf(p.level(), x, y, z));
    }

    /** The rope thrown to a pick: a swing (fitted, no yank: it flies a tick or two and bites) or a zip up over a lip. */
    private static void throwTo(LocalPlayer p, AscentSwing.Pick pk, float heading) {
        hand(p);
        K.set(HAND.x, HAND.y, HAND.z, HAND.vx, HAND.vy, HAND.vz);
        heldOn = 0;
        if (pk.zip) {
            zipUp(p, pk, true);
            return;
        }
        if (pk.sling) {
            slingTo(p, pk);
            return;
        }
        slinging = false;
        ax = pk.x;
        ay = pk.y;
        az = pk.z;
        anchorBlock = AscentAnchors.blockOf(p.level(), ax, ay, az);
        len = pk.len;
        lenTarget = pk.fitLen;
        ROPE.set(ax, ay, az, len, lenTarget);
        // a swing thrown longer than it can swing over the floor reels you up into its arc (the aimed swing under something)
        ROPE.reel = pk.reel;
        swinging = true;
        ropeTravel = AscentSwing.travel(len);
        ropeTaut = false;
        AscentSwing.entry(K, heading);
        setState(p, AscentState.TETHER);
        trace(String.format("swing %.1f (fits %.1f, %.0f° up, %.1f aside)", pk.len, pk.fitLen, pk.elev, pk.side));
        tetherTick(p, heading, 0, 0, false);
    }

    /**
     * A sling (SPEC §62): the rope bites the top's block and pulls the body toward a point over it; it lets go by itself
     * before the point. To the server and to watchers it is a zip (a rope pulling you along).
     */
    private static void slingTo(LocalPlayer p, AscentSwing.Pick pk) {
        swinging = false;
        slinging = true;
        ax = pk.x;
        ay = pk.y;
        az = pk.z;
        anchorBlock = AscentAnchors.blockOf(p.level(), ax, ay, az);
        len = lenTarget = pk.len;
        sx = pk.px;
        sy = pk.py;
        sz = pk.pz;
        slingAge = slingStall = 0;
        ropeTravel = 1;
        ropeTaut = false;
        setState(p, AscentState.ZIP);
        zipFromX = K.x;
        zipFromZ = K.z;
        trace(String.format("sling %.1f (over a top at %.1f, %.0f° up, %.1f aside)", pk.len, pk.top, pk.elev, pk.side));
        zipTick(p, p.getYRot());
    }

    /** A tick of a sling: the rope flies a tick, then pulls; it lets go before the top, or early with a hop if the way closes. */
    private static void slingTick(LocalPlayer p, float yaw) {
        if (ropeTravel > 0) {
            ropeTravel--;
            if (ropeTravel == 0) bite(p, 0.7F);
            carry(yaw, 0, 0);
            return;
        }
        slingStall = stateAge > 3 && lastMoved < 0.05 ? slingStall + 1 : 0;
        boolean blocked = slingStall > 5 || AscentSwing.slingBlocked(SPACE.at(p.level(), p), K, sx, sy, sz, slingAge, WORK);
        if (blocked) AscentSwing.wallHop(K);
        if (!blocked && !AscentSwing.slingStep(K, sx, sy, sz, ++slingAge)) {
            ropeTaut = true;
            tension = AscentSwing.T.slingPull * 4;
            return;
        }
        // let go: on over the top (or, blocked, a hop and whatever the free flight finds)
        releaseAt = slingAt = ticks;
        releaseQuality = blocked ? 0 : 0.6F;
        fireCool = 2;
        AscentFlow.gain(blocked ? 0.02F : 0.07F);
        AscentSounds.local(p, AscentContent.ROPE_RELEASE.get(), 0.6F, 1.12F);
        if (!blocked) AscentSounds.move(p, "sling", AscentContent.LAUNCH.get(), 0.5F, 1.2F);
        FeelDirector.release(releaseQuality);
        AscentFp.released();
        event(AscentPayloads.EV_SLING, (float) K.speed());
        trace(blocked ? "sling lets go early: the way closed" : String.format("sling lets go at %.2f b/t", K.speed()));
        if (blocked) linkFail = "the sling's way closed";
        else linkArrived = true;
        toCarry(p, "sling let go");
        carry(yaw, 0, 0);
    }

    /** A zip up to a lip and over it (the assisted rope's when nothing swings, or a tap, or a wall ahead). */
    private static void zipUp(LocalPlayer p, AscentSwing.Pick pk, boolean step) {
        swinging = slinging = false;
        ax = pk.x;
        ay = pk.y;
        az = pk.z;
        anchorBlock = AscentAnchors.blockOf(p.level(), ax, ay, az);
        len = lenTarget = pk.len;
        ropeTravel = 1;
        ropeTaut = false;
        setState(p, AscentState.ZIP);
        zipFromX = K.x;
        zipFromZ = K.z;
        if (p.onGround()) K.vy = Math.max(K.vy, 0.3);
        trace(String.format("zip up to a lip %.1f (top %.1f)", pk.len, pk.top));
        if (step) zipTick(p, p.getYRot());
    }

    /**
     * The rope held between swings, flying free: what's just ahead is flown round (a little sideways or down), else zipped
     * up and over, else hopped. True when a zip took over (its tick has run).
     */
    private static boolean steerHeld(LocalPlayer p, float yaw, float pitch, boolean mayThrow) {
        SPACE.at(p.level(), p);
        if (AscentSwing.steerFree(SPACE, K, yaw, false, WORK) || !WORK.blocked) return false;
        Vec3 eye = p.getEyePosition();
        // the zip over is a link of the chain: it waits out the chain's pause, never follows a link that went nowhere and
        // never takes an anchor this press failed at
        if (mayThrow && AscentSwing.zipTo(SPACE, eye.x, eye.y, eye.z, yaw, pitch, reach(p), WORK, PICK)
                && zipOk(PICK, K.x, K.y + AscentRules.HAND_HEIGHT, K.z) && !refusedLately(p, PICK.x, PICK.y, PICK.z)
                && !spentAnchor(p, PICK.x, PICK.y, PICK.z)) {
            fired(p);
            pressThrow = false;
            throwTo(p, PICK, yaw);
            return true;
        }
        // nothing bends round it or goes over: with the guide the body brakes, so what it meets it meets softly (SPEC §62)
        if (!AscentSwing.steerFree(SPACE, K, yaw, true, WORK) && WORK.blocked && AscentSwing.guide) AscentSwing.brake(K);
        return false;
    }

    private static final AscentSwing.Pick LESSON_PICK = new AscentSwing.Pick();

    /**
     * The Way Up's sling lesson on foot (SPEC §63.8; written when the marker below didn't look from the ground):
     * where a rope held now would bite to sling you, or null (something swings, nothing is ahead, or the guide is off).
     * The same two looks as the marker's, into a pick of its own; called twice a second at most, only while the lesson
     * can still teach.
     */
    static @org.jspecify.annotations.Nullable Vec3 slingFromGround(LocalPlayer p) {
        if (!FeelConfig.ropeAssist() || !AscentSwing.guide || state != AscentState.NONE || !p.onGround() || !has(AscentPayloads.Tier.TETHER)
                || dev.awaken.body.client.RunnerMoves.busy()) return null;
        boolean view = dev.awaken.body.client.feel.ShoulderView.aim(VIEW);
        float yaw = view ? VIEW[0] : p.getYRot();
        SPACE.at(p.level(), p);
        hand(p);
        if (AscentSwing.find(SPACE, HAND, yaw, reach(p), WORK, LESSON_PICK)) return null;
        if (!AscentSwing.findSling(SPACE, HAND, yaw, reach(p), WORK, LESSON_PICK)) return null;
        return new Vec3(LESSON_PICK.x, LESSON_PICK.y, LESSON_PICK.z);
    }

    /**
     * The marker: where a press now would throw (on the rope, where the next rope goes), every other tick, always: on the
     * ground, in the air, swinging, slinging (SPEC §62, "The marker"; 12 Nov 2026, Euan: "sometimes you can't see the place
     * the rope is going to land at all ... I'd like it to be basically constant, so you always know where you're about to
     * lock onto"; it looked only in the air off the rope, or on the ground with the key already held). Never while
     * rappelling, and not on the place the rope holds now (the rope shows that).
     */
    private static void marker(LocalPlayer p, boolean assist) {
        if (!assist || state == AscentState.RAPPEL || !has(AscentPayloads.Tier.TETHER)) {
            markerOn = false;
            return;
        }
        // every other tick, found or not (every fourth while a look costs more than its budget: a dense wood, a slow
        // machine): it looks on the ground too now, where most looks find nothing and nothing was cheap
        if (ticks % (markerCost > MARKER_BUDGET ? 4 : 2) != 0) return;
        long t0 = System.nanoTime();
        boolean view = dev.awaken.body.client.feel.ShoulderView.aim(VIEW);
        float yaw = view ? VIEW[0] : p.getYRot(), pitch = view ? VIEW[1] : p.getXRot();
        SPACE.at(p.level(), p);
        hand(p);
        Vec3 eye = p.getEyePosition();
        markerOverhead = overheadMark(p, eye, yaw, pitch);
        markerAt = ticks;
        markerOn = markerOverhead
                || AscentSwing.aimed(SPACE, HAND, eye.x, eye.y, eye.z, yaw, pitch, reach(p), AscentSwing.guide, WORK, MARKER)
                || AscentSwing.findAfter(SPACE, HAND, yaw, reach(p), WORK, MARKER)
                || AscentSwing.guide && AscentSwing.findSling(SPACE, HAND, yaw, reach(p), WORK, MARKER)
                || AscentSwing.zipTo(SPACE, eye.x, eye.y, eye.z, yaw, pitch, reach(p), WORK, MARKER) && zipOk(MARKER, HAND.x, HAND.y, HAND.z);
        // never where the rope can't bite as the server asks (the anchor moved onto the block's shape when it can)
        if (markerOn && !markerOverhead && !AscentSwing.bites(SPACE, MARKER)) markerOn = false;
        if (markerOn && state.rope && (MARKER.x - ax) * (MARKER.x - ax) + (MARKER.y - ay) * (MARKER.y - ay) + (MARKER.z - az) * (MARKER.z - az) < 2.25) {
            markerOn = false;
        }
        markerCost += (System.nanoTime() - t0 - markerCost) * 0.2;
        // where only a sling answers, its tip (SPEC §62): the rope works here too
        // (The Way Up's lesson first, SPEC §63.8: your key on the top the rope would bite; else the written tip)
        if (markerOn && MARKER.sling && !(Lesson.wants(Lesson.Kind.SLING) && Lesson.offerMove(Lesson.Kind.SLING, new Vec3(MARKER.x, MARKER.y, MARKER.z), -1))) {
            dev.awaken.core.api.TipHooks.client().offer(dev.awaken.body.ascent.AscentTips.SLING);
        }
    }

    /**
     * Looking steeply up at a ring or branch straight overhead (what a press zips to and hangs from, before any swing:
     * {@link #fire}): the marker on it as a pull. Only looked for when the look is steep enough to find one.
     */
    private static boolean overheadMark(LocalPlayer p, Vec3 eye, float yaw, float pitch) {
        if (pitch > -60) return false;
        AscentAnchors.Anchor a = AscentAnchors.find(p.level(), p, eye, yaw, pitch, reach(p), ticks - leftAt <= 20 ? leftAnchor : null);
        if (a == null || !overhead(a.x() - HAND.x, a.y() - HAND.y, a.z() - HAND.z)) return false;
        MARKER.x = a.x();
        MARKER.y = a.y();
        MARKER.z = a.z();
        MARKER.zip = true;
        MARKER.sling = false;
        MARKER.top = Double.NaN; // not a lip: pick() never takes it as one
        return true;
    }

    /**
     * The tips of the moves the air earned (SPEC §62), each offered where its move would work now and only while it can
     * still teach (a retired tip costs one lookup). Called once a tick while a state holds the body.
     */
    private static void moveTips(LocalPlayer p, float yaw, float pitch) {
        var tips = dev.awaken.core.api.TipHooks.client();
        // each move's Lesson first (The Way Up, SPEC §63.8: the key where the move is), its written tip when that can no
        // longer teach
        if (state == AscentState.ZIP && !slinging && strikeTarget < 0 && !keyFwd && lv(dev.awaken.body.ascent.AscentMemory.Track.TETHER) >= AirRules.SPRING_LEVEL) {
            if (!Lesson.offerMove(Lesson.Kind.SPRING, null, -1)) tips.offer(dev.awaken.body.ascent.AscentTips.SPRING);
        }
        if (state == AscentState.GLIDE && !skimming && lv(dev.awaken.body.ascent.AscentMemory.Track.WINGS) >= AirRules.SKIM_LEVEL
                && K.speed() >= AirRules.SKIM_SPEED && ticks % 10 == 0
                && (Lesson.wants(Lesson.Kind.SKIM) || tips.wants(dev.awaken.body.ascent.AscentTips.SKIM))
                && p.getY() - AscentAnchors.floorUnder(p.level(), p, p.getX(), p.getY() + 0.5, p.getZ(), 10) < 9.5) {
            if (!Lesson.offerMove(Lesson.Kind.SKIM, null, -1)) tips.offer(dev.awaken.body.ascent.AscentTips.SKIM);
        }
        if ((state == AscentState.TETHER || state == AscentState.CARRY) && ticks % 10 == 0 && has(AscentPayloads.Tier.TETHER)
                && lv(dev.awaken.body.ascent.AscentMemory.Track.TETHER) >= AirRules.STRIKE_LEVEL
                && (Lesson.wants(Lesson.Kind.STRIKE) || tips.wants(dev.awaken.body.ascent.AscentTips.STRIKE))) {
            net.minecraft.world.entity.LivingEntity t = strikePick(p, yaw, pitch);
            if (t != null && !Lesson.offerMove(Lesson.Kind.STRIKE, null, t.getId())) tips.offer(dev.awaken.body.ascent.AscentTips.STRIKE);
        }
    }

    /** The marker's look, its running cost (ns) and the budget over which it looks half as often (SPEC §17.31). */
    private static double markerCost;
    private static final double MARKER_BUDGET = 1_500_000;

    /**
     * The Tether Vow's line (SPEC §17.7): thrown for you, straight to a point (a zip), the same rope the key throws.
     * False when the body can't take it (no rope yet, riding, swimming...).
     */
    static boolean zipTo(LocalPlayer p, double x, double y, double z) {
        if (!has(AscentPayloads.Tier.TETHER) || off(p)) return false;
        fireAt = ticks;
        AscentSounds.local(p, AscentContent.ROPE_FIRE.get(), 0.9F, 0.9F);
        event(AscentPayloads.EV_FIRE, 0);
        double hx = p.getX(), hy = p.getY() + AscentRules.HAND_HEIGHT, hz = p.getZ();
        if (state == AscentState.NONE) K.set(hx, hy, hz, p.getX() - p.xo, p.getDeltaMovement().y, p.getZ() - p.zo);
        else K.set(hx, hy, hz, K.vx, K.vy, K.vz);
        swinging = slinging = false;
        ax = x;
        ay = y;
        az = z;
        anchorBlock = AscentAnchors.blockOf(p.level(), ax, ay, az);
        double dist = Math.sqrt((ax - hx) * (ax - hx) + (ay - hy) * (ay - hy) + (az - hz) * (az - hz));
        ropeTravel = Mth.clamp((int) Math.ceil(dist / 16), 1, 3);
        ropeTaut = false;
        len = lenTarget = dist;
        setState(p, AscentState.ZIP);
        zipFromX = hx;
        zipFromZ = hz;
        heldForYou = true;
        if (p.onGround()) K.vy = Math.max(K.vy, 0.3);
        trace(String.format("the vow's line, zip %.1f", dist));
        return true;
    }

    /**
     * Hangs the body on a rope from (x, y, z) at once, as long as the hand is from it (the rappel's tour stage, SPEC
     * §28.5; the server checks the anchor as always). False when the body can't take a rope.
     */
    static boolean hangFrom(LocalPlayer p, double x, double y, double z) {
        if (!has(AscentPayloads.Tier.TETHER) || off(p)) return false;
        double hx = p.getX(), hy = p.getY() + AscentRules.HAND_HEIGHT, hz = p.getZ();
        K.set(hx, hy, hz, 0, 0, 0);
        swinging = slinging = false;
        ax = x;
        ay = y;
        az = z;
        anchorBlock = AscentAnchors.blockOf(p.level(), ax, ay, az);
        len = lenTarget = Math.max(AscentRules.TETHER_MIN, Math.sqrt((ax - hx) * (ax - hx) + (ay - hy) * (ay - hy) + (az - hz) * (az - hz)));
        ropeTravel = 0;
        ropeTaut = true;
        fireAt = biteAt = ticks;
        setState(p, AscentState.TETHER);
        AscentSounds.at(p, ax, ay, az, AscentContent.ROPE_BITE.get(), 0.7F, 1F);
        trace(String.format("hung on a %.1f rope", len));
        return true;
    }

    private static void zipTick(LocalPlayer p, float yaw) {
        if (strikeTarget >= 0) {
            strikeTick(p, yaw);
            return;
        }
        if (slinging) {
            slingTick(p, yaw);
            return;
        }
        if (ropeTravel > 0) {
            ropeTravel--;
            if (ropeTravel == 0) bite(p, 0.6F);
            carry(yaw, 0, 0);
        } else if (zipStalled(p)) {
            // pressed against something on the way: let go and fall (never pinned for good)
            trace("zip blocked");
            linkFail = "the zip was blocked";
            toCarry(p, "zip blocked");
            carry(yaw, 0, 0);
        } else if (AscentRules.zipStep(K, ax, ay, az)) {
            BlockPos b = anchorBlock;
            if (b != null && ay < b.getY() + 0.02) {
                // the rope bit an underside: near a ledge's edge (a floor with room on it), out, up and over its lip
                if (ledgeOver(p, b)) {
                    trace(String.format("zip arrived: over the ledge's lip (%.1f up)", pullTop - (K.y - AscentRules.HAND_HEIGHT)));
                    linkArrived = true;
                    toCarry(p, "zip arrived");
                    carry(yaw, 0, 0);
                    return;
                }
                // under anything else (a ring, a branch, a keel): hang there on a short rope, still, ready to throw on
                K.vx *= 0.2;
                K.vz *= 0.2;
                K.vy = 0;
                len = lenTarget = AscentRules.CEILING_HANG;
                setState(p, AscentState.TETHER);
                ropeTravel = 0;
                ropeTaut = true;
                trace("zip arrived: hanging under it");
                AscentSounds.local(p, AscentContent.ROPE_STRAIN.get(), 0.5F, 1.1F);
                return;
            }
            if (b != null && roomOver(p, b)) {
                // arrived at a face with room over it (a ledge, a pylon, a branch): up and onto it over that face's edge
                double ox = ax - (b.getX() + 0.5), oz = az - (b.getZ() + 0.5);
                if (Math.max(Math.abs(ox), Math.abs(oz)) < 0.45) {
                    // the top's own face: over the edge on the side the body is
                    ox = K.x - (b.getX() + 0.5);
                    oz = K.z - (b.getZ() + 0.5);
                }
                boolean alongX = Math.abs(ox) >= Math.abs(oz);
                vault(b, alongX ? (ox >= 0 ? 1 : -1) : 0, alongX ? 0 : (oz >= 0 ? 1 : -1));
                trace(String.format("zip arrived: up onto the top (%.1f up)", pullTop - (K.y - AscentRules.HAND_HEIGHT)));
            } else {
                // a face with more wall over it: a hop off it, then fly on
                K.vx *= 0.45;
                K.vz *= 0.45;
                K.vy = Math.max(K.vy * 0.3, 0.48);
            }
            linkArrived = true;
            toCarry(p, "zip arrived");
            carry(yaw, 0, 0);
        }
    }

    private static void tetherTick(LocalPlayer p, float yaw, double fwd, double right, boolean sneak) {
        if (ropeTravel > 0) {
            // the rope is still flying out: the body falls on as it was
            ropeTravel--;
            double hx = K.x, hy = K.y, hz = K.z;
            carry(yaw, fwd * 0.5, right * 0.5);
            if (ropeTravel == 0) {
                double d = Math.sqrt((K.x - ax) * (K.x - ax) + (K.y - ay) * (K.y - ay) + (K.z - az) * (K.z - az));
                len = Math.max(AscentRules.TETHER_MIN, Math.min(len, d));
                bite(p, 1F);
            }
            return;
        }
        if (stateAge % 2 == 0 && cut(p, yaw, fwd, right)) return;
        if (swinging) {
            swingTick(p, yaw, fwd, sneak);
            return;
        }
        // reel: fast up to the length that clears the ground (the yank into the swing), and on while sneaking (the gold
        // reel winds both faster)
        boolean gold = kitReel == dev.awaken.body.ascent.RopeKit.REEL_GOLD;
        if (len > lenTarget + 1e-3) {
            len = AscentRules.reel(K, ax, ay, az, len, Math.min(AscentRules.fitReelRate(gold), len - lenTarget));
            reeling = true;
        } else if (sneak && len > AscentRules.TETHER_MIN + 1e-3) {
            len = AscentRules.reel(K, ax, ay, az, len, Math.min(AscentRules.reelRate(gold), len - AscentRules.TETHER_MIN));
            lenTarget = len;
            reeling = true;
        }
        if (reeling && gold && stateAge % 5 == 0) AscentSounds.local(p, AscentContent.RAPPEL_REEL.get(), 0.4F, 0.95F + p.getRandom().nextFloat() * 0.1F);
        boolean wasTaut = ropeTaut;
        // Flow and the Tether's mastery make the pump stronger (the pump is linear in the forward input)
        double pump = AscentFlow.pump() * (1 + AscentRules.MASTERY_PUMP * lv(dev.awaken.body.ascent.AscentMemory.Track.TETHER));
        double t = AscentRules.tetherStep(K, ax, ay, az, len, yaw, fwd * pump, right, S);
        double r = Math.sqrt((K.x - ax) * (K.x - ax) + (K.y - ay) * (K.y - ay) + (K.z - az) * (K.z - az));
        ropeTaut = r >= len - 0.02;
        tension = t;
        if (ropeTaut && !wasTaut && stateAge > 2 && t > 0.25) {
            // the catch: a slack rope snapping taut under you
            biteStrength = (float) Math.min(1, t / 0.9);
            FeelDirector.ropeCatch(biteStrength);
            AscentSounds.local(p, AscentContent.ROPE_STRAIN.get(), 0.35F + biteStrength * 0.5F, 0.8F + biteStrength * 0.2F);
            event(AscentPayloads.EV_CATCH, (float) t);
        }
    }

    /**
     * A tick of the assisted swing (SPEC §17.31): sneak still reels in; the swing steps, looks ahead and bends round what
     * it can. The held rope (SPEC §62, {@link AscentSwing#hold}) swings on, back and forth, until the key is let go: a
     * wall it can't bend round brakes it. Without it, a wall is run along (beside you), climbed by a zip over its top, or
     * hopped off, and the top of the arc lets go (a moment later if that would throw you into something).
     */
    private static void swingTick(LocalPlayer p, float yaw, double fwd, boolean sneak) {
        boolean gold = kitReel == dev.awaken.body.ascent.RopeKit.REEL_GOLD;
        if (sneak && len > AscentRules.TETHER_MIN + 1e-3) {
            len = AscentRules.reel(K, ax, ay, az, len, Math.min(AscentRules.reelRate(gold), len - AscentRules.TETHER_MIN));
            lenTarget = len;
            reeling = true;
            if (gold && stateAge % 5 == 0) AscentSounds.local(p, AscentContent.RAPPEL_REEL.get(), 0.4F, 0.95F + p.getRandom().nextFloat() * 0.1F);
        }
        ROPE.ax = ax;
        ROPE.ay = ay;
        ROPE.az = az;
        ROPE.len = len;
        ROPE.fitLen = Math.min(lenTarget, len);
        boolean caught = ROPE.caught;
        double vy0 = K.vy;
        double pump = AscentFlow.pump() * (1 + AscentRules.MASTERY_PUMP * lv(dev.awaken.body.ascent.AscentMemory.Track.TETHER));
        int why = AscentSwing.tick(SPACE.at(p.level(), p), K, ROPE, yaw, fwd * pump, WORK);
        len = ROPE.len;
        lenTarget = ROPE.fitLen;
        if (WORK.bent) trace(String.format("swing bends %.0f° round what's ahead", ROPE.bend));
        if (ROPE.caught) windowNow = Math.max(windowNow, (float) AscentSwing.windowQuality(K, ROPE, yaw + ROPE.bend, WORK));
        double r = Math.sqrt((K.x - ax) * (K.x - ax) + (K.y - ay) * (K.y - ay) + (K.z - az) * (K.z - az));
        ropeTaut = ROPE.caught && r >= len - 0.05;
        // the rope's pull: the turn's (v²/r) and the part of gravity along it
        tension = ropeTaut ? K.speed() * K.speed() / Math.max(1, len) + AscentRules.G * Math.max(0, (ay - K.y) / Math.max(1e-3, r)) : 0;
        if (ROPE.caught && !caught) {
            // the catch: the rope coming taut under you, the fall turned into the swing
            swingCatchAt = ticks;
            biteStrength = (float) Math.min(1, tension / 0.9);
            FeelDirector.ropeCatch(biteStrength);
            AscentSounds.local(p, AscentContent.ROPE_STRAIN.get(), 0.35F + biteStrength * 0.5F, 0.8F + biteStrength * 0.2F);
            event(AscentPayloads.EV_CATCH, (float) tension);
        }
        if (TRACE) {
            AscentSwing.pivot(K.x, K.z, ax, ay, az, yaw + ROPE.bend, PV);
            trace(String.format("swing %.3f b/t at %.0f°%s%s", K.speed(), AscentSwing.angleFromBottom(K, PV[0], PV[1], PV[2]),
                    ROPE.swung ? ", held" : "", WORK.braked ? ", braking for a wall" : ""));
        }
        if (vy0 < 0 && K.vy >= 0 && ropeTaut && ticks - bottomAt > 8) {
            // the low point: the rush of air through it, the line humming under the whole weight
            bottomAt = ticks;
            float s = (float) Mth.clamp((K.speed() - 0.5) / 1.3, 0, 1);
            AscentSounds.local(p, AscentContent.ROPE_SWOOP.get(), 0.25F + 0.6F * s, 0.9F + 0.2F * s);
            FeelDirector.swingBottom(s);
        }
        if (why == AscentSwing.WALL) {
            swingIntoWall(p, yaw);
        } else if (why == AscentSwing.TOP) {
            if (heldOn >= AscentSwing.T.holdOn || AscentSwing.letGoClear(SPACE, K, ROPE, yaw, WORK)) {
                trace("the top of the arc lets go");
                release(p, false);
            } else {
                heldOn++;
            }
        }
    }

    private static final double[] PV = new double[4];

    /** A swing that meets a wall nothing bends it round: along it (a wall-run), up it and over (a zip), or a hop off. */
    private static void swingIntoWall(LocalPlayer p, float yaw) {
        ClientInput in = input;
        if (in != null) {
            Vec3 was = p.getDeltaMovement();
            p.setDeltaMovement(K.vx, K.vy, K.vz);
            if (dev.awaken.body.client.RunnerMoves.runFromRope(p, in)) {
                trace("swing hands off to a wall-run");
                AscentSounds.local(p, AscentContent.ROPE_RELEASE.get(), 0.5F, 1.2F);
                releaseAt = ticks;
                releaseQuality = 0;
                AscentFp.released();
                fireCool = 2;
                linkArrived = true;
                end(p, "onto a wall", true);
                return;
            }
            p.setDeltaMovement(was);
        }
        double ex = K.x, ey = K.y - AscentRules.HAND_HEIGHT + p.getEyeHeight(), ez = K.z;
        if (AscentSwing.zipTo(SPACE, ex, ey, ez, yaw, p.getXRot(), reach(p), WORK, PICK) && zipOk(PICK, K.x, K.y, K.z)
                && !refusedLately(p, PICK.x, PICK.y, PICK.z)) {
            trace("swing meets a wall: up it on a new rope");
            AscentFx.loose(p.getId(), ax, ay, az);
            fired(p);
            zipUp(p, PICK, false);
            return;
        }
        trace("swing meets a wall: hop off it");
        AscentSwing.wallHop(K);
        linkFail = "the swing met a wall";
        release(p, false);
    }

    /**
     * Something across the rope (every other tick): swung round a trunk or a corner, the rope wraps there and goes on,
     * shorter, about the new point; a snag under the hand, one too close to swing from, or the anchor gone lets go into
     * the carry. True when it let go.
     */
    private static boolean cut(LocalPlayer p, float yaw, double fwd, double right) {
        Vec3 hand = new Vec3(K.x, K.y, K.z);
        Vec3 snag = AscentAnchors.snag(p.level(), p, hand, ax, ay, az);
        // on a rappel a lip just over the hand (a passage's mouth swung into) only bends the rope over its edge
        if (snag != null && state == AscentState.RAPPEL && hand.distanceTo(snag) < AscentRules.TETHER_MIN + 1 && snag.y > hand.y) return false;
        boolean wrap = snag != null && snag.y > hand.y - 1 && hand.distanceTo(snag) >= AscentRules.TETHER_MIN + 1;
        if (wrap) {
            wrap(p, snag, hand.distanceTo(snag));
        } else if (snag != null || anchorGone(p)) {
            trace(snag == null ? "rope cut (anchor gone)" : String.format("rope cut (snag %.1f off, %.1f under the hand)",
                    hand.distanceTo(snag), hand.y - snag.y));
            AscentSounds.local(p, AscentContent.ROPE_RELEASE.get(), 0.7F, 1.4F);
            linkFail = snag == null ? "the anchor went" : "the rope was cut";
            toCarry(p, "rope cut");
            carry(yaw, fwd, right);
            return true;
        }
        return false;
    }

    // ---- the rappel (SPEC §28.5) ------------------------------------------------------------------------------------

    /** The rope at its longest for this player: its reach, the Tether's mastery, the reach perk and the line. */
    static double reach(LocalPlayer p) {
        return AscentRules.reach(lv(dev.awaken.body.ascent.AscentMemory.Track.TETHER),
                dev.awaken.progression.Progression.perk(p, AscentRules.ROPE_REACH_PERK), kitLine);
    }

    static boolean smoothLine() {
        return kitLine == dev.awaken.body.ascent.RopeKit.LINE_DEEPGLASS;
    }

    /** The rope's fittings as one line for the band's Ascent page ("Fitted: Iron line · Gold reel"), or null for none. */
    public static @org.jspecify.annotations.Nullable String fittings() {
        String line = kitLine == dev.awaken.body.ascent.RopeKit.LINE_IRON ? "iron_line"
                : kitLine == dev.awaken.body.ascent.RopeKit.LINE_DEEPGLASS ? "deepglass_line" : null;
        boolean gold = kitReel == dev.awaken.body.ascent.RopeKit.REEL_GOLD;
        if (line == null && !gold) return null;
        StringBuilder sb = new StringBuilder(net.minecraft.network.chat.Component.translatable("awaken.ascent.fittings").getString());
        if (line != null) sb.append(' ').append(net.minecraft.network.chat.Component.translatable("item.awaken." + line).getString());
        if (gold) sb.append(line != null ? " · " : " ").append(net.minecraft.network.chat.Component.translatable("item.awaken.gold_reel").getString());
        return sb.toString();
    }

    private static void startRappel(LocalPlayer p) {
        // a rappel's line is the old rope's from here (it pays out; a swing's never does)
        swinging = false;
        paid = 0;
        lenTarget = len;
        setState(p, AscentState.RAPPEL);
        sentLen = len;
        sentAt = ticks;
        AscentSounds.local(p, AscentContent.ROPE_STRAIN.get(), 0.35F, 1.2F);
        trace(String.format("rappel on a %.1f rope (reach %.1f)", len, reach(p)));
    }

    /**
     * A tick of the rappel: snags as on a swing; a fresh jump with the feet on the wall kicks off it; jump held pays the
     * rope out (in bites, or smooth on a Deepglass line) down to the floor and never past the reach; the body leans back
     * to the wall; then the rope's own swing (forward still pumps, sideways still steers). The new length goes to the
     * server every few ticks while it changes.
     */
    private static void rappelTick(LocalPlayer p, float yaw, double fwd, double right, boolean jump, boolean jumpPressed) {
        if (stateAge % 2 == 0 && cut(p, yaw, fwd, right)) return;
        wallAtFeet(p);
        boolean onWall = wallNX != 0 || wallNZ != 0;
        if (jumpPressed && onWall && ticks - kickAt >= AscentRules.KICK_COOLDOWN) kick(p);
        if (jump) {
            boolean smooth = smoothLine();
            double step = AscentRules.payStep(paid, smooth);
            double floor = AscentAnchors.floorUnder(p.level(), p, K.x, K.y - AscentRules.HAND_HEIGHT + 0.5, K.z, 64);
            double limit = AscentRules.payLimit(reach(p), ax, ay, az, K.x, K.z, floor);
            double pay = Math.min(step, Math.max(0, limit - len));
            if (pay > 1e-4) {
                boolean caught = AscentRules.payCatch(paid, pay, smooth);
                len = AscentRules.payOut(K, ax, ay, az, len, pay);
                lenTarget = len;
                paid += pay;
                payNow = pay;
                if (caught) {
                    // the bite's end: the hand grips again
                    catchAt = ticks;
                    AscentSounds.local(p, AscentContent.RAPPEL_CATCH.get(), 0.32F, 0.9F + p.getRandom().nextFloat() * 0.2F);
                    FeelDirector.bite(0.3F);
                } else if (smooth && ticks % 3 == 0) {
                    // the Deepglass line's light shed where it runs through the hand
                    double rx = ax - K.x, ry = ay - K.y, rz = az - K.z, rl = Math.max(1e-3, Math.sqrt(rx * rx + ry * ry + rz * rz));
                    AscentFx.glint(p, K.x + rx / rl * 0.7, K.y + ry / rl * 0.7, K.z + rz / rl * 0.7);
                }
            }
        }
        AscentRules.lean(K, ax, az);
        double pump = AscentFlow.pump() * (1 + AscentRules.MASTERY_PUMP * lv(dev.awaken.body.ascent.AscentMemory.Track.TETHER));
        tension = AscentRules.tetherStep(K, ax, ay, az, len, yaw, fwd * pump, right, S);
        double r = Math.sqrt((K.x - ax) * (K.x - ax) + (K.y - ay) * (K.y - ay) + (K.z - az) * (K.z - az));
        ropeTaut = r >= len - 0.02;
        // the server hears the rope grow: every 5 ticks while it pays out, and once more when it stops
        if (Math.abs(len - sentLen) > 0.05 && (ticks - sentAt >= 5 || payNow == 0)) {
            sentLen = len;
            sentAt = ticks;
            ClientPacketDistributor.sendToServer(new AscentPayloads.State(state.ordinal(), ax, ay, az, (float) len));
        }
    }

    /**
     * The wall at the feet: a face with collision just past the shins (0.3 up) and the knees (0.9 up) on one side of the
     * body, the rope's own wall (toward the anchor) looked at first. Sets its outward normal, or zero when there's none.
     */
    private static void wallAtFeet(LocalPlayer p) {
        wallNX = 0;
        wallNZ = 0;
        double feet = K.y - AscentRules.HAND_HEIGHT;
        double tx = ax - K.x, tz = az - K.z;
        // the four sides, the anchor's side first
        int first = Math.abs(tx) >= Math.abs(tz) ? (tx >= 0 ? 0 : 1) : (tz >= 0 ? 2 : 3);
        for (int i = 0; i < 4; i++) {
            int side = (first + i) % 4;
            int sx = side == 0 ? 1 : side == 1 ? -1 : 0, sz = side == 2 ? 1 : side == 3 ? -1 : 0;
            double px = K.x + sx * 0.5, pz = K.z + sz * 0.5;
            if (solidAt(p, px, feet + 0.3, pz) && solidAt(p, px, feet + 0.9, pz)) {
                wallNX = -sx;
                wallNZ = -sz;
                return;
            }
        }
    }

    private static boolean solidAt(LocalPlayer p, double x, double y, double z) {
        PROBE.set(Math.floor(x), Math.floor(y), Math.floor(z));
        return !p.level().getBlockState(PROBE).getCollisionShape(p.level(), PROBE).isEmpty();
    }

    /** A kick off the wall: out from it and a little up, the rope still paying out while jump is held (a bound). */
    /** The wall the last kick pushed off (its outward normal). */
    static double kickNX, kickNZ;

    private static void kick(LocalPlayer p) {
        AscentRules.kick(K, wallNX, wallNZ);
        kickAt = ticks;
        kickNX = wallNX;
        kickNZ = wallNZ;
        double feet = K.y - AscentRules.HAND_HEIGHT;
        AscentSounds.at(p, K.x - wallNX * 0.4, feet + 0.4, K.z - wallNZ * 0.4, AscentContent.RAPPEL_KICK.get(), 0.85F,
                0.92F + p.getRandom().nextFloat() * 0.14F);
        AscentFx.grit(p, K.x - wallNX * 0.45, feet + 0.35, K.z - wallNZ * 0.45, wallNX, wallNZ, 8);
        FeelDirector.leapLaunch(0.45F);
        AscentFlow.gain(0.05F);
        AscentFp.kicked();
        AscentAnims.kick(p.getId());
        event(AscentPayloads.EV_RAPPEL_KICK, (float) AscentRules.KICK_OUT);
        trace("kick off the wall");
    }

    /** The feet back on the wall after a bound: a soft thud and a scrape of grit. */
    private static void plant(LocalPlayer p, float speed) {
        plantAt = ticks;
        double feet = K.y - AscentRules.HAND_HEIGHT;
        AscentSounds.local(p, AscentContent.RAPPEL_PLANT.get(), Math.min(0.9F, 0.35F + speed * 1.6F), 0.95F + p.getRandom().nextFloat() * 0.1F);
        if (wallNX != 0 || wallNZ != 0) AscentFx.grit(p, K.x - wallNX * 0.45, feet + 0.3, K.z - wallNZ * 0.45, wallNX, wallNZ, 4);
        FeelDirector.leapCatch(Math.min(0.6F, speed * 2.5F));
        event(AscentPayloads.EV_RAPPEL_PLANT, speed);
        trace(String.format("feet on the wall (%.2f)", speed));
    }

    /**
     * Letting go of the line by a passage (SPEC §28.5): a floor with a roof over it (a passage, not the open shaft) at
     * the feet's level, give or take a block, in the body's own cell or up to two along the way it moves (then each
     * side): the body steps in onto it (the zip's vault), rather than falling past. False when there's none.
     */
    private static boolean dropIn(LocalPlayer p) {
        double feet = K.y - AscentRules.HAND_HEIGHT;
        int bx = Mth.floor(K.x), bz = Mth.floor(K.z), fy = Mth.floor(feet);
        double h = K.hspeed();
        // the way it moves (toward the wall's face when still: the passage is in the rope's wall)
        int first = h > 0.04 ? (Math.abs(K.vx) >= Math.abs(K.vz) ? (K.vx >= 0 ? 0 : 1) : (K.vz >= 0 ? 2 : 3))
                : wallNX != 0 || wallNZ != 0 ? (Math.abs(wallNX) > 0 ? (wallNX < 0 ? 0 : 1) : (wallNZ < 0 ? 2 : 3))
                : (Math.abs(ax - K.x) >= Math.abs(az - K.z) ? (ax >= K.x ? 0 : 1) : (az >= K.z ? 2 : 3));
        for (int i = 0; i < 4; i++) {
            int side = (first + i) % 4;
            int sx = side == 0 ? 1 : side == 1 ? -1 : 0, sz = side == 2 ? 1 : side == 3 ? -1 : 0;
            for (int step = 0; step <= 2; step++) {
                for (int dy : DROP_DY) {
                    BlockPos f = new BlockPos(bx + sx * step, fy - 1 + dy, bz + sz * step);
                    double top = f.getY() + 1;
                    if (feet - top > 1.3 || top - feet > 1.0 || !floor(p, f) || !roofed(p, f)) continue;
                    // over its edge on the side the body comes from
                    vault(f, -sx, -sz);
                    trace(String.format("let go by a passage: in onto its floor (%.1f %s)", top - feet, step == 0 ? "here" : step + " along"));
                    return true;
                }
            }
        }
        return false;
    }

    private static final int[] DROP_DY = {0, 1, -1};

    /** A roof within 4 over a floor's two cells of room: a passage or a cave, not a ledge open to the sky or the shaft. */
    private static boolean roofed(LocalPlayer p, BlockPos f) {
        for (int i = 3; i <= 4; i++) {
            BlockPos q = f.above(i);
            if (!p.level().getBlockState(q).getCollisionShape(p.level(), q).isEmpty()) return true;
        }
        return false;
    }

    /** The rope catches on {@code at}: that point is the anchor now, the rope as long as the hand is from it. */
    private static void wrap(LocalPlayer p, Vec3 at, double length) {
        ax = at.x;
        ay = at.y;
        az = at.z;
        anchorBlock = AscentAnchors.blockOf(p.level(), ax, ay, az);
        len = length;
        lenTarget = Math.min(lenTarget, length);
        trace(String.format("rope wraps (%.1f)", length));
        AscentSounds.at(p, ax, ay, az, AscentContent.ROPE_STRAIN.get(), 0.5F, 1.15F);
        AscentFx.bite(p, ax, ay, az);
        FeelDirector.ropeCatch(0.3F);
        ClientPacketDistributor.sendToServer(new AscentPayloads.State(state.ordinal(), ax, ay, az, (float) len));
    }

    /** Tether mastery at which a top reached with forward held is sprung off. */
    public static final int SPRING_LEVEL = AirRules.SPRING_LEVEL;

    /** The body's half width and a little: how far out from a block's face the body clears it. */
    private static final double VAULT_CLEAR = 0.5 + 0.3 + 0.08;

    /** A vault onto {@code b}'s top over its edge on the side ({@code ex}, {@code ez}): out, up, then in (pullOver). */
    private static void vault(BlockPos b, int ex, int ez) {
        K.vx = 0;
        K.vy = 0;
        K.vz = 0;
        // forward held as the pull arrives (Tether mastery 3): off the top at a run, not onto it (SPEC §62)
        spring = AscentSwing.guide && keyFwd && lv(dev.awaken.body.ascent.AscentMemory.Track.TETHER) >= SPRING_LEVEL;
        pullTicks = 40;
        pullX = b.getX() + 0.5;
        pullZ = b.getZ() + 0.5;
        pullTop = b.getY() + 1;
        pullBottom = b.getY();
        pullEX = ex;
        pullEZ = ez;
    }

    /**
     * Under a ledge: a floor (full blocks with room on them, at least two together) whose edge is within three blocks
     * of the bite, toward the side the zip came up from first. Sets up the vault over that edge; false when there is
     * none within reach (a lone ring or branch, a ceiling: the hang).
     */
    private static boolean ledgeOver(LocalPlayer p, BlockPos b) {
        double fx = zipFromX - ax, fz = zipFromZ - az;
        Direction[] order = Direction.Plane.HORIZONTAL.stream()
                .sorted((d1, d2) -> Double.compare(-(d1.getStepX() * fx + d1.getStepZ() * fz), -(d2.getStepX() * fx + d2.getStepZ() * fz)))
                .toArray(Direction[]::new);
        for (Direction e : order) {
            BlockPos c = b;
            for (int i = 0; i < 3; i++) {
                if (!floor(p, c)) break;
                BlockPos out = c.relative(e);
                if (open(p, out) && open(p, out.above()) && open(p, out.below())) {
                    boolean wide = false;
                    for (Direction n : Direction.Plane.HORIZONTAL) if (n != e && floor(p, c.relative(n))) wide = true;
                    if (!wide) break;
                    vault(c, e.getStepX(), e.getStepZ());
                    return true;
                }
                c = out;
            }
        }
        return false;
    }

    /** A full block with two cells of room on it. */
    private static boolean floor(LocalPlayer p, BlockPos c) {
        return p.level().getBlockState(c).isCollisionShapeFullBlock(p.level(), c) && roomOver(p, c);
    }

    private static boolean open(LocalPlayer p, BlockPos c) {
        return p.level().getBlockState(c).getCollisionShape(p.level(), c).isEmpty();
    }

    /**
     * The vault, a tick of it: clear of the edge first (from under the ledge, out past its face), up beside the face
     * until the feet are over the top, then in onto it. Velocities are set against gravity (the carry takes it off).
     */
    private static void pullOver() {
        pullTicks--;
        double out = (K.x - pullX) * pullEX + (K.z - pullZ) * pullEZ;
        LocalPlayer sp = Minecraft.getInstance().player;
        if (spring && sp != null && K.y >= pullTop + 0.02) {
            // the spring: the feet are over the lip; a leap along the look with a runner's speed, and the next rope
            spring = false;
            pullTicks = 0;
            double yaw = sp.getYRot() * Mth.DEG_TO_RAD;
            K.vx = -Math.sin(yaw) * AscentRules.SPRING_SPEED;
            K.vz = Math.cos(yaw) * AscentRules.SPRING_SPEED;
            K.vy = AscentRules.SPRING_UP;
            fireCool = 2;
            AscentFlow.gain(0.06F);
            AscentSounds.move(sp, "spring", AscentContent.LAUNCH.get(), 0.55F, 1.25F);
            FeelDirector.leapLaunch(0.5F);
            AscentFp.launched();
            event(AscentPayloads.EV_SPRING, (float) AscentRules.SPRING_SPEED);
            trace("springs off the top");
            return;
        }
        if (K.y >= pullTop + 0.02) {
            double hx = pullX - K.x, hz = pullZ - K.z, hl = Math.sqrt(hx * hx + hz * hz);
            if (hl < 0.25) {
                pullTicks = 0;
                K.vx *= 0.3;
                K.vz *= 0.3;
                return;
            }
            double push = Math.min(0.3, hl * 0.5);
            K.vx = hx / hl * push;
            K.vz = hz / hl * push;
            // level until the body is over the top, then down onto it
            if (out > VAULT_CLEAR - 0.1) K.vy = (pullTop + 0.1 - K.y) * 0.4 + AscentRules.G;
        } else if (out < VAULT_CLEAR - 0.05) {
            // under the ledge: out past its face, the head kept under it
            double go = Math.min(0.25, (VAULT_CLEAR - out) * 0.7 + 0.05);
            K.vx = pullEX * go;
            K.vz = pullEZ * go;
            double feet = Math.min(K.y, pullBottom - 1.85);
            K.vy = (feet - K.y) * 0.5 + AscentRules.G;
        } else {
            // beside the face: up it, held just off it
            K.vx = pullEX * (VAULT_CLEAR - out) * 0.5;
            K.vz = pullEZ * (VAULT_CLEAR - out) * 0.5;
            K.vy = Math.min(0.7, (pullTop + 0.2 - K.y) * 0.45 + 0.1) + AscentRules.G;
        }
    }

    /** Ticks a zip has made no headway (the body moving under 0.05 a tick). */
    private static int zipStall;

    /** How far the body really went last tick (reconcile's measure). */
    private static double lastMoved = 1;

    private static boolean zipStalled(LocalPlayer p) {
        zipStall = stateAge > 3 && ropeTravel == 0 && lastMoved < 0.05 ? zipStall + 1 : 0;
        return zipStall > 10;
    }

    /** Two cells of room over a block (somewhere to stand on it). */
    private static boolean roomOver(LocalPlayer p, BlockPos b) {
        for (int i = 1; i <= 2; i++) {
            BlockPos q = b.above(i);
            if (!p.level().getBlockState(q).getCollisionShape(p.level(), q).isEmpty()) return false;
        }
        return true;
    }

    /** The block the rope bit is gone (broken, or a leaf that decayed): its cell has no collision any more. */
    private static boolean anchorGone(LocalPlayer p) {
        return anchorBlock != null && p.level().getBlockState(anchorBlock).getCollisionShape(p.level(), anchorBlock).isEmpty();
    }

    private static void bite(LocalPlayer p, float strength) {
        airMovesUsed = 0;
        if (state == AscentState.TETHER && !swinging) {
            // a tug along the swing, the way you look: chaining ropes keeps you moving
            double lx = -Mth.sin(p.getYRot() * Mth.DEG_TO_RAD), lz = Mth.cos(p.getYRot() * Mth.DEG_TO_RAD);
            double rx = K.x - ax, ry = K.y - ay, rz = K.z - az, r = Math.sqrt(rx * rx + ry * ry + rz * rz);
            if (r > 1e-3) {
                double o = (lx * rx + lz * rz) / r;
                double tx = lx - rx / r * o, ty = -ry / r * o, tz = lz - rz / r * o, tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
                if (tl > 1e-3) {
                    K.vx += tx / tl * AscentRules.TETHER_YANK;
                    K.vy += ty / tl * AscentRules.TETHER_YANK;
                    K.vz += tz / tl * AscentRules.TETHER_YANK;
                }
            }
        }
        biteAt = ticks;
        biteStrength = strength;
        AscentFlow.gain(0.08F);
        trace("rope bites");
        AscentSounds.at(p, ax, ay, az, AscentContent.ROPE_BITE.get(), 0.9F, 0.9F + p.getRandom().nextFloat() * 0.2F);
        AscentFx.bite(p, ax, ay, az);
        FeelDirector.bite(strength);
    }

    private static void release(LocalPlayer p) {
        release(p, true);
    }

    /** Lets go of the rope; {@code step}: the carry's first tick runs now (not when this tick's swing has moved already). */
    private static void release(LocalPlayer p, boolean step) {
        linkArrived = ropeTravel == 0;
        if (ropeTravel > 0) {
            toCarry(p, "let go before the bite");
            if (step) carry(p.getYRot(), 0, 0);
            return;
        }
        // let go by a passage, on a rappel or hanging still: in onto its floor rather than down past it
        if ((state == AscentState.RAPPEL || K.speed() < 0.45) && dropIn(p)) {
            releaseAt = ticks;
            releaseQuality = 0;
            AscentSounds.local(p, AscentContent.ROPE_RELEASE.get(), 0.5F, 1.1F);
            AscentFp.released();
            toCarry(p, "dropped in");
            if (step) carry(p.getYRot(), 0, 0);
            return;
        }
        float q;
        if (swinging) {
            // the assisted let-go: in the window, speed along and a lift (SPEC §17.31)
            q = (float) AscentSwing.letGo(K, ROPE, p.getYRot(), WORK);
            fireCool = 2;
            if (q > AscentSwing.T.winFrom) {
                // let go in the window: the line snaps free (the boost's sound and beat)
                snapAt = ticks;
                AscentSounds.local(p, AscentContent.ROPE_SNAP.get(), 0.5F + 0.4F * q, 0.95F + 0.1F * q);
            }
        } else {
            q = (float) AscentRules.releaseQuality(K, ax, ay, az);
            if (q > 0) {
                double f = 1 + (AscentRules.RELEASE_BOOST - 1) * q;
                K.vx *= f;
                K.vy *= f;
                K.vz *= f;
            }
        }
        releaseAt = ticks;
        releaseQuality = q;
        AscentFlow.gain(0.04F + 0.12F * q);
        AscentSounds.local(p, AscentContent.ROPE_RELEASE.get(), 0.6F, 1.0F + q * 0.15F);
        FeelDirector.release(q);
        AscentFp.released();
        trace(String.format("release (window %.2f)", q));
        toCarry(p, "released");
        if (step) carry(p.getYRot(), 0, 0);
    }

    /** Jumping off the rope: up and on, faster than you swung. */
    private static void launch(LocalPlayer p) {
        float q = (float) AscentRules.releaseQuality(K, ax, ay, az);
        K.vx *= AscentRules.LAUNCH_BOOST;
        K.vz *= AscentRules.LAUNCH_BOOST;
        K.vy = Math.max(K.vy, 0) + AscentRules.LAUNCH_UP;
        launchAt = ticks;
        releaseQuality = q;
        fireCool = 2;
        linkArrived = true;
        AscentFlow.gain(0.1F + 0.06F * q);
        AscentSounds.local(p, AscentContent.LAUNCH.get(), 0.8F, 1F);
        AscentSounds.local(p, AscentContent.ROPE_RELEASE.get(), 0.5F, 1.2F);
        event(AscentPayloads.EV_LAUNCH, (float) K.speed());
        FeelDirector.launch(q);
        AscentFp.launched();
        trace("launch");
        toCarry(p, "launched");
        carry(p.getYRot(), 0, 0);
    }

    // ---- the Leaper ---------------------------------------------------------------------------------------------------

    /**
     * The Leaper's air moves, once each an airtime: jump for a double jump, sprint for an air dash along the look's
     * heading, sneak (high enough) for a dive slam. From NONE they start the carry with the body's own motion. True when
     * one went off.
     */
    private static boolean leaperMove(LocalPlayer p, boolean jumpPressed, boolean sprintPressed, boolean sneakPressed, boolean fromNone) {
        // a sprint press the rope, a glide or a double jump's tick couldn't give the dash is the dash's for a few ticks
        boolean kept = AscentRules.dashKept(dashKeptAt, ticks);
        if (!has(AscentPayloads.Tier.LEAPER) || p.onGround()) {
            if (sprintPressed && !p.onGround()) trace("no dash: no Leaper");
            return false;
        }
        // each only once it is open (the Way Up's mask, SPEC §71.3: a leap first given at a Gauntlet's stand may come a
        // move at a time; given any other way every move is open)
        boolean dbl = jumpPressed && (airMovesUsed & 1) == 0 && !dev.awaken.body.client.RunnerMoves.wallBeside(p)
                && WayClient.canLeap(dev.awaken.body.ascent.WayUp.Leap.DOUBLE);
        boolean dash = (sprintPressed || kept) && dashOpen(p, sprintPressed);
        boolean dive = sneakPressed && !slamming && heightAboveGround(p) >= AscentRules.SLAM_MIN_HEIGHT
                && WayClient.canLeap(dev.awaken.body.ascent.WayUp.Leap.SLAM);
        if (dbl && dash) {
            // both in one tick: the double jump now, the dash the next
            dashKeptAt = ticks;
            trace("the double jump takes this tick: the dash goes the next");
        }
        if (!dbl && !dash && !dive) return false;
        if (fromNone) K.set(p.getX(), p.getY(), p.getZ(), stepX, p.getDeltaMovement().y, stepZ);
        leapt = dbl || dash; // a slam is no leap: it never stands aside for an edge
        if (dbl) {
            airMovesUsed |= 1;
            K.vy = Math.max(K.vy, 0) + AscentRules.DOUBLE_JUMP
                    + AscentRules.MASTERY_DOUBLE * lv(dev.awaken.body.ascent.AscentMemory.Track.LEAPER);
            AscentSounds.local(p, AscentContent.LAUNCH.get(), 0.6F, 1.3F);
            AscentFx.ring(p.getX(), p.getY() + 0.1, p.getZ(), new Vec3(0, 1, 0), 0.35F);
            event(AscentPayloads.EV_DOUBLE, 0);
            FeelDirector.flap();
            AscentFp.doubleJump();
            trace("double jump");
        } else if (dash) {
            airMovesUsed |= 2;
            dashKeptAt = -1000;
            if (kept && !sprintPressed) trace("the kept sprint press dashes");
            double yaw = p.getYRot() * Mth.DEG_TO_RAD, fx = -Math.sin(yaw), fz = Math.cos(yaw);
            double speed = Math.max(AscentRules.DASH_SPEED, K.hspeed() * AscentRules.DASH_GAIN)
                    * (1 + AscentRules.MASTERY_DASH * lv(dev.awaken.body.ascent.AscentMemory.Track.LEAPER));
            K.vx = fx * speed;
            K.vz = fz * speed;
            K.vy = Math.max(K.vy, AscentRules.DASH_LIFT);
            AscentSounds.local(p, AscentContent.LAUNCH.get(), 0.75F, 1.15F);
            AscentFx.ring(p.getX(), p.getY() + 0.9, p.getZ(), new Vec3(fx, 0, fz), 0.3F);
            event(AscentPayloads.EV_DASH, (float) speed);
            FeelDirector.dash();
            AscentFp.dash();
            trace(String.format("air dash %.2f", speed));
        } else {
            slamming = true;
            K.vx *= 0.25;
            K.vz *= 0.25;
            K.vy = -AscentRules.SLAM_SPEED;
            AscentSounds.local(p, AscentContent.WINGS_FOLD.get(), 0.8F, 0.7F);
            event(AscentPayloads.EV_DIVE, 0);
            FeelDirector.dive();
            AscentFp.dive();
            trace("dive slam");
        }
        AscentFlow.gain(0.08F);
        if (fromNone) setState(p, AscentState.CARRY);
        return true;
    }

    /**
     * Whether the air dash can go now: the Leaper's, open (the Way Up), not yet used this airtime (the ground, a rope's
     * bite or strike give it back). {@code why}: trace why not (a press asked for it).
     */
    private static boolean dashOpen(LocalPlayer p, boolean why) {
        String no = !has(AscentPayloads.Tier.LEAPER) ? "no Leaper" : p.onGround() ? "on the ground"
                : !WayClient.canLeap(dev.awaken.body.ascent.WayUp.Leap.DASH) ? "not open yet (the Way Up gives it at a Gauntlet's stand)"
                : (airMovesUsed & 2) != 0 ? "one already this airtime (the ground, a rope's bite or strike give it back)" : null;
        if (no != null && why) trace("no dash: " + no);
        return no == null;
    }

    /** A sprint press where the body is the rope's (a swing, a pull): kept for the dash once it lets go ({@link AscentRules#DASH_KEPT}). */
    private static void keepDash(LocalPlayer p, boolean sprintPressed, String where) {
        if (!sprintPressed || p.onGround()) return;
        dashKeptAt = ticks;
        trace("sprint " + where + ": kept for the dash at the let-go");
    }

    /** A press of the sprint key, off its own events (a press between two ticks counts; a repeat of a held key doesn't). */
    static void sprintKey(boolean pressed) {
        if (pressed && Minecraft.getInstance().gui.screen() == null) sprintTaps++;
    }

    /**
     * The aimed dive's press (SPEC §17.33). The press is the hand's when something is in its reach under the crosshair
     * (a creature, a block), when it means a creature near the look (what a weapon's press would take), or with a lock
     * on. Otherwise the look finds the point to dive to. True when the dive began.
     */
    private static boolean aimDive(LocalPlayer p, Minecraft mc, float yaw, float pitch) {
        // each refusal is traced (only with the trace on: tours read why a press made no dive)
        if (!has(AscentPayloads.Tier.LEAPER) || !WayClient.canLeap(dev.awaken.body.ascent.WayUp.Leap.DIVE)) {
            trace("no dive: not open to this player");
            return false;
        }
        if ((airMovesUsed & 4) != 0 || ticks - diveAt < AscentRules.DIVE_COOL) {
            trace("no dive: one already this airtime or within 2 s");
            return false;
        }
        if (mc.hitResult != null && mc.hitResult.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            trace("no dive: the hand's press (something in its reach under the crosshair)");
            return false;
        }
        double reach = p.entityInteractionRange();
        if (dev.awaken.core.api.Aim.locked() || dev.awaken.core.api.Aim.meantId(p, dev.awaken.core.api.Aim.target(), yaw, pitch, reach) >= 0
                || dev.awaken.core.api.Aim.calm(p, yaw, pitch, reach) >= 0) {
            trace("no dive: a lock or a creature the press means");
            return false;
        }
        Vec3 eye = p.getEyePosition(), look = Vec3.directionFromRotation(pitch, yaw);
        var hit = p.level().clip(new net.minecraft.world.level.ClipContext(eye, eye.add(look.scale(AscentRules.DIVE_REACH)),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.SOURCE_ONLY, p));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
            trace("no dive: nothing under the crosshair within reach");
            return false;
        }
        Vec3 at = hit.getLocation();
        Direction face = hit.getDirection();
        // on a floor the feet land on the point; on a side the body aims half a width out from it and falls down its foot
        double tx = at.x + face.getStepX() * 0.4, ty = at.y, tz = at.z + face.getStepZ() * 0.4;
        if (!AscentRules.diveTo(p.getX(), p.getY(), p.getZ(), tx, ty, tz)) {
            trace(String.format("no dive: the point (%.2f, %.2f, %.2f) is not under the feet", tx, ty, tz));
            return false;
        }
        double d = Math.sqrt(p.distanceToSqr(tx, ty, tz));
        airMovesUsed |= 4;
        diving = true;
        diveThere = false;
        diveAge = 0;
        diveAt = ticks;
        diveX = tx;
        diveY = ty;
        diveZ = tz;
        AscentSounds.local(p, AscentContent.DIVE.get(), 1F, 1F);
        AscentFx.mark(tx, ty, tz, (int) Math.ceil(d / AscentRules.DIVE_SPEED) + 1);
        event(AscentPayloads.EV_AIM_DIVE, (float) d);
        FeelDirector.dive();
        AscentFp.dive();
        AscentFlow.gain(0.06F);
        trace(String.format("aimed dive to (%.2f, %.2f, %.2f), %.2f away", tx, ty, tz, d));
        return true;
    }

    /** A tick of the aimed dive. A wall in its way, or its time run out, gives the body back to the carry. */
    private static void diveTick(LocalPlayer p, float yaw) {
        if ((diveAge > 0 && p.horizontalCollision) || diveAge >= AscentRules.DIVE_MAX_TICKS) {
            trace(diveAge >= AscentRules.DIVE_MAX_TICKS ? "the dive runs out" : "the dive meets a wall");
            stopDive();
            carry(yaw, 0, 0);
            return;
        }
        diveThere = AscentRules.diveStep(K, diveX, diveY, diveZ, diveAge, diveThere);
        diveAge++;
    }

    private static void stopDive() {
        diving = false;
        AscentFx.unmark();
        AscentFp.diveEnd();
    }

    /**
     * The Sovereign's Judgement Fall (SPEC §17.19): from at least {@code minHeight} up, the body drops at {@code speed},
     * a dive whose landing the server answers as Judgement. False when too low or held by a rope.
     */
    static boolean judgementDive(LocalPlayer p, double minHeight, double speed) {
        if (state.rope || p.onGround() || heightAboveGround(p) < minHeight) return false;
        if (state == AscentState.NONE) K.set(p.getX(), p.getY(), p.getZ(), stepX, p.getDeltaMovement().y, stepZ);
        slamming = true;
        K.vx *= 0.2;
        K.vz *= 0.2;
        K.vy = -speed;
        setState(p, AscentState.CARRY);
        event(AscentPayloads.EV_DIVE, 0);
        FeelDirector.dive();
        AscentFp.dive();
        trace("a Judgement Fall");
        return true;
    }

    /** The Sovereign form begun: off the ground (or out of a glide) into flight. */
    static void sovereignLift(LocalPlayer p) {
        if (state.rope || state == AscentState.SOAR || off(p)) return;
        if (state == AscentState.NONE) {
            K.set(p.getX(), p.getY(), p.getZ(), stepX, 0.9, stepZ);
        }
        startSoar(p);
    }

    private static double heightAboveGround(LocalPlayer p) {
        return p.getY() - AscentAnchors.floorUnder(p.level(), p, p.getX(), p.getY() + 0.5, p.getZ(), 40);
    }

    // ---- wings --------------------------------------------------------------------------------------------------------

    private static void startGlide(LocalPlayer p) {
        if (state == AscentState.NONE) K.set(p.getX(), p.getY(), p.getZ(), stepX, p.getDeltaMovement().y, stepZ);
        AscentRules.glideCatch(K, p.getYRot(), p.getXRot());
        deployAt = ticks;
        glideFromEmpty = false;
        AscentFlow.gain(0.06F);
        boomArmed = K.speed() < AscentRules.BOOM_SPEED;
        setState(p, AscentState.GLIDE);
        AscentSounds.local(p, AscentContent.WINGS_OPEN.get(), 1F, 1F);
        event(AscentPayloads.EV_DEPLOY, (float) K.speed());
        FeelDirector.deploy();
        AscentFp.wingsOpen();
        trace("wings open");
        AscentRules.glideStep(K, p.getYRot(), p.getXRot(), glideDrag());
    }

    private static void fold(LocalPlayer p) {
        foldAt = ticks;
        AscentSounds.local(p, AscentContent.WINGS_FOLD.get(), 0.7F, 1F);
    }

    /** This tick's drain (set by the flight step), and the meter's tick: drained in flight, refilled out of it. */
    private static double aetherDrain;

    private static void aetherTick(LocalPlayer p) {
        if (!has(AscentPayloads.Tier.SKYBORN)) return;
        boolean flying = state == AscentState.SOAR;
        BlockPos eye = BlockPos.containing(p.getEyePosition());
        boolean sun = p.level().isBrightOutside() && p.level().canSeeSky(eye);
        boolean updraft = dev.awaken.body.ascent.Updrafts.liftAt(p.level(), p.getX(), p.getY(), p.getZ()) > 0;
        aether = Math.max(0, AscentRules.aetherStep(aether, flying, aetherDrain, sun, updraft, lv(dev.awaken.body.ascent.AscentMemory.Track.SKYBORN)));
        aetherDrain = AscentRules.AETHER_CRUISE;
        if (flying && aether <= 0) skyLetsGo(p);
    }

    /** A tap of left or right in flight: the second, soon enough and the same way, is a barrel roll. */
    private static void sideTap(LocalPlayer p, int dir) {
        boolean again = dir == sideTapDir && ticks - sideTapAt <= AscentRules.ROLL_TAP;
        sideTapDir = dir;
        sideTapAt = ticks;
        if (!again || ticks - rollAt < AscentRules.ROLL_COOLDOWN || aether < AscentRules.ROLL_AETHER) return;
        rollAt = ticks;
        sideTapAt = -100;
        aether -= AscentRules.ROLL_AETHER;
        AscentRules.inputDir(p.getYRot(), 0, dir, S);
        K.vx += S[0] * AscentRules.ROLL_IMPULSE;
        K.vz += S[1] * AscentRules.ROLL_IMPULSE;
        K.vy += AscentRules.ROLL_LIFT;
        AscentSounds.local(p, AscentContent.LAUNCH.get(), 0.7F, 1.45F);
        AscentSounds.local(p, AscentContent.FLAP.get(), 0.6F, 1.25F);
        FeelDirector.barrelRoll(dir);
        AscentFp.roll();
        AscentAnims.roll(p.getId(), dir);
        AscentFlow.gain(0.06F);
        event(AscentPayloads.EV_ROLL, dir);
        trace(dir > 0 ? "barrel roll right" : "barrel roll left");
    }

    /** Out of Aether in flight: the wings stop beating and hold a glide (never a drop). */
    private static void skyLetsGo(LocalPlayer p) {
        aetherOutAt = ticks;
        trace("out of aether: the wings hold a glide");
        AscentSounds.local(p, AscentContent.WINGS_FOLD.get(), 0.7F, 0.75F);
        AscentRules.glideCatch(K, p.getYRot(), p.getXRot());
        setState(p, AscentState.GLIDE);
        glideFromEmpty = true;
    }

    /** Takes flight; false (a weak beat) when the meter can't lift. */
    private static boolean startSoar(LocalPlayer p) {
        if (aether < AscentRules.AETHER_START) {
            // not enough to lift: a weak beat that catches nothing
            AscentSounds.local(p, AscentContent.FLAP.get(), 0.4F, 0.7F);
            aetherOutAt = ticks;
            return false;
        }
        glideFromEmpty = false;
        if (state == AscentState.NONE) K.set(p.getX(), p.getY(), p.getZ(), stepX, p.getDeltaMovement().y, stepZ);
        boolean fromGlide = state == AscentState.GLIDE;
        soarAt = ticks;
        setState(p, AscentState.SOAR);
        if (!fromGlide) deployAt = ticks;
        flap(p);
        trace("takes flight");
        AscentRules.soarStep(K, p.getYRot(), p.getXRot(), 0, 0, false, false, false, S);
        return true;
    }

    private static void flap(LocalPlayer p) {
        flapAt = ticks;
        AscentFlow.gain(0.02F);
        aether -= AscentRules.AETHER_FLAP;
        K.vy = Math.max(K.vy, 0) + AscentRules.SOAR_FLAP;
        AscentSounds.local(p, AscentContent.FLAP.get(), 0.9F, 0.9F + p.getRandom().nextFloat() * 0.15F);
        event(AscentPayloads.EV_FLAP, 0);
        FeelDirector.flap();
        AscentFp.beat();
    }

    private static void boomCheck(LocalPlayer p) {
        double s = K.speed();
        if (boomArmed && s >= AscentRules.BOOM_SPEED) {
            boomArmed = false;
            boomAt = ticks;
            AscentFlow.gain(0.25F);
            trace("sonic boom");
            FeelDirector.boom();
            AscentFp.boom();
            AscentFx.boom(p, K);
            event(AscentPayloads.EV_BOOM, (float) s);
        } else if (!boomArmed && s < AscentRules.BOOM_REARM) {
            boomArmed = true;
        }
    }

    // ---- the air's contract (SPEC §62) ------------------------------------------------------------------------------

    /** A cast of the local player's went out (core.api.MovementHooks, after its motion was predicted). */
    public static void castStarting(boolean motionPredicted) {
        castAt = ticks;
        if (motionPredicted && state != AscentState.NONE) castMotion = true;
    }

    /**
     * A push said plainly (the server's, for a body it knows is in the air; a module's through MovementHooks.push): it
     * joins the state's velocity at the next step. With no state it is the body's own, as the game would have it.
     */
    public static void pushed(double x, double y, double z) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        if (state == AscentState.NONE) {
            p.setDeltaMovement(p.getDeltaMovement().add(x, y, z));
            return;
        }
        pushX += x;
        pushY += y;
        pushZ += z;
    }

    /**
     * What moved the body from outside since the last step: a cast of your own that set its motion, a blast, a knock, a
     * push said plainly. It goes into the state's own velocity (AirRules) instead of being lost when the state sets the
     * body's motion again.
     */
    private static void outside(LocalPlayer p) {
        if (zeroed) {
            Vec3 d = p.getDeltaMovement();
            if (d.lengthSqr() > AirRules.PUSH_EPS) {
                // a cast gives its motion along its own way (never slower than you were); anything else is added
                boolean took = castMotion ? AirRules.cast(K, state, d.x, d.y, d.z, ax, ay, az) : AirRules.push(K, state, d.x, d.y, d.z, ax, ay, az);
                if (took) trace(String.format(castMotion ? "a cast's motion carries (%.2f %.2f %.2f)" : "a push carries (%.2f %.2f %.2f)", d.x, d.y, d.z));
            }
        }
        if (pushX != 0 || pushY != 0 || pushZ != 0) {
            if (AirRules.push(K, state, pushX, pushY, pushZ, ax, ay, az)) trace(String.format("a push carries (%.2f %.2f %.2f)", pushX, pushY, pushZ));
            pushX = pushY = pushZ = 0;
        }
        castMotion = false;
        zeroed = false;
    }

    /** The keys that hold the aim: the guard (the use key) or any power's key, with no screen open. */
    private static boolean aimKey(Minecraft mc) {
        if (mc.gui.screen() != null) return false;
        if (mc.options.keyUse.isDown() || AwakenKeys.ULTIMATE.isDown()) return true;
        for (var k : AwakenKeys.POWER_SLOTS) if (k.isDown()) return true;
        return false;
    }

    // ---- the moves the air earned (SPEC §62) --------------------------------------------------------------------------

    /** Coming down slow and level, near the ground: the wings begin to cup (the landing itself is the flare). */
    private static void flareCheck(LocalPlayer p) {
        if (K.vy >= 0.02 || K.speed() > AirRules.FLARE_SPEED) return;
        double clear = p.getY() - AscentAnchors.floorUnder(p.level(), p, p.getX(), p.getY() + 0.5, p.getZ(), 3);
        if (AirRules.flares(K.speed(), K.vy, clear)) {
            flareAt = ticks;
            WingLayer.flare(p.getId());
        }
    }

    /** The skim's tick, before the glide's step: whether a surface is near enough at this speed, its wake, its Flow. */
    private static void skim(LocalPlayer p) {
        double speed = K.speed();
        boolean was = skimming;
        skimming = false;
        if (lv(dev.awaken.body.ascent.AscentMemory.Track.WINGS) >= AirRules.SKIM_LEVEL && speed >= AirRules.SKIM_SPEED) {
            double floor = AscentAnchors.floorUnder(p.level(), p, p.getX(), p.getY() + 0.5, p.getZ(), AirRules.SKIM_NEAR + 0.5);
            double clear = p.getY() - floor;
            boolean wall = false;
            if (clear > AirRules.SKIM_NEAR) {
                // a wall beside the wings: two blocks out to either side of the way you fly
                double h = K.hspeed();
                if (h > 0.2) {
                    double rx = -K.vz / h, rz = K.vx / h;
                    wall = solidAt(p, p.getX() + rx * 2, p.getY() + 0.9, p.getZ() + rz * 2) || solidAt(p, p.getX() - rx * 2, p.getY() + 0.9, p.getZ() - rz * 2);
                }
            }
            if (AirRules.skims(lv(dev.awaken.body.ascent.AscentMemory.Track.WINGS), speed, wall ? 0 : clear)) {
                skimming = true;
                skimY = wall ? p.getY() : floor;
            }
        }
        if (!skimming) {
            if (was) skimEnd();
            return;
        }
        skimFor++;
        if (skimFor == 6) {
            // a real skim (not a brush past a hilltop): a link, and Flow
            AscentFlow.gain(0.06F);
            AscentSounds.move(p, "skim", null, 0.8F, 1F);
            event(AscentPayloads.EV_SKIM, (float) speed);
            trace("skim");
        } else if (skimFor > 6 && skimFor % 12 == 0) {
            AscentFlow.gain(0.02F);
        }
        if (!FeelConfig.reduced()) AscentFx.wake(p, K, skimY);
    }

    private static void skimEnd() {
        skimming = false;
        skimFor = 0;
    }

    /** The takeoff (SPEC §62): straight up into flight for a tenth of the Aether. */
    private static void takeoff(LocalPlayer p) {
        K.set(p.getX(), p.getY(), p.getZ(), stepX * 0.5, 0, stepZ * 0.5);
        aether -= AirRules.TAKEOFF_AETHER;
        glideFromEmpty = false;
        sneakFor = 0;
        takeoffAt = soarAt = deployAt = ticks;
        setState(p, AscentState.SOAR);
        flapAt = ticks;
        if (AscentScores.on()) {
            AscentSounds.move(p, "takeoff", null, 1F, 1F);
        } else {
            AscentSounds.local(p, AscentContent.WINGS_OPEN.get(), 1F, 1.1F);
            AscentSounds.local(p, AscentContent.FLAP.get(), 1F, 0.8F);
        }
        AscentFx.ring(p.getX(), p.getY() + 0.1, p.getZ(), new Vec3(0, 1, 0), 0.45F);
        FeelDirector.deploy();
        FeelDirector.flap();
        AscentFp.wingsOpen();
        AscentFlow.gain(0.1F);
        event(AscentPayloads.EV_TAKEOFF, 0);
        trace("takeoff");
        K.vy = AirRules.takeoffStep(0);
        K.y += K.vy;
    }

    /**
     * The rope strike begins: the rope bites the creature under the crosshair (or the one locked on), further off than
     * the hand reaches and within the rope's. False when there is none (the attack is then just the attack).
     */
    private static boolean strike(LocalPlayer p, float yaw, float pitch) {
        if (!AscentSwing.guide || !has(AscentPayloads.Tier.TETHER) || ticks - strikeAt < AirRules.STRIKE_COOLDOWN
                || lv(dev.awaken.body.ascent.AscentMemory.Track.TETHER) < AirRules.STRIKE_LEVEL) {
            return false;
        }
        net.minecraft.world.entity.LivingEntity t = strikePick(p, yaw, pitch);
        if (t == null) return false;
        if (state.wings) fold(p);
        // the state's point becomes the hand, as for any rope
        hand(p);
        K.set(HAND.x, HAND.y, HAND.z, HAND.vx, HAND.vy, HAND.vz);
        strikeAt = ticks;
        swinging = slinging = false;
        heldOn = 0;
        ax = t.getX();
        ay = t.getY() + t.getBbHeight() * 0.5;
        az = t.getZ();
        anchorBlock = null;
        double dx = ax - p.getX(), dy = ay - (p.getY() + AscentRules.HAND_HEIGHT), dz = az - p.getZ();
        len = lenTarget = Math.sqrt(dx * dx + dy * dy + dz * dz);
        ropeTravel = 0;
        ropeTaut = true;
        fireAt = biteAt = ticks;
        // the server hears which creature before it hears the rope, so it can take a creature for an anchor
        ClientPacketDistributor.sendToServer(new AscentPayloads.Strike(t.getId()));
        if (state != AscentState.ZIP) setState(p, AscentState.ZIP);
        else ClientPacketDistributor.sendToServer(new AscentPayloads.State(AscentState.ZIP.ordinal(), ax, ay, az, (float) len));
        strikeTarget = t.getId();
        AscentSounds.local(p, AscentContent.ROPE_FIRE.get(), 0.9F, 1.15F);
        AscentFx.bite(p, ax, ay, az);
        FeelDirector.bite(0.8F);
        AscentFp.fired();
        event(AscentPayloads.EV_FIRE, 0);
        trace(String.format("rope strike at %s, %.1f off", t.getType().toShortString(), len));
        strikeTick(p, yaw);
        return true;
    }

    /** The creature a strike would take: the one locked or marked if it is in the look, else the nearest to the crosshair. */
    private static net.minecraft.world.entity.@org.jspecify.annotations.Nullable LivingEntity strikePick(LocalPlayer p, float yaw, float pitch) {
        double reach = reach(p);
        Vec3 eye = p.getEyePosition();
        int lv = lv(dev.awaken.body.ascent.AscentMemory.Track.TETHER);
        int locked = dev.awaken.core.api.Aim.target();
        if (locked >= 0 && p.level().getEntity(locked) instanceof net.minecraft.world.entity.LivingEntity l && !(l instanceof net.minecraft.world.entity.player.Player)
                && dev.awaken.core.api.Aim.meant(p, l, yaw, pitch, reach) && AirRules.strikes(lv, dev.awaken.core.api.Aim.gap(eye, l.getBoundingBox()), reach)) {
            return l;
        }
        net.minecraft.world.entity.LivingEntity best = null;
        float bestOff = 3F; // degrees off the crosshair, to the creature's edge
        for (var e : p.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, p.getBoundingBox().inflate(reach),
                e -> e != p && e.isAlive() && !(e instanceof net.minecraft.world.entity.player.Player) && !dev.awaken.core.api.Aim.body(e))) {
            var box = e.getBoundingBox();
            float off = dev.awaken.core.api.Aim.offEdge(eye, box, yaw, pitch);
            if (off >= bestOff || !AirRules.strikes(lv, dev.awaken.core.api.Aim.gap(eye, box), reach) || !dev.awaken.core.api.Aim.sees(p, e)) continue;
            bestOff = off;
            best = e;
        }
        return best;
    }

    /** A tick of the strike's pull; at the creature, the blow (the server's to judge) and the way off it. */
    private static void strikeTick(LocalPlayer p, float yaw) {
        var e = p.level().getEntity(strikeTarget);
        if (!(e instanceof net.minecraft.world.entity.LivingEntity t) || !t.isAlive() || dev.awaken.core.api.Aim.body(t)
                || ticks - strikeAt > AirRules.STRIKE_TICKS || p.distanceToSqr(t) > (reach(p) + 8) * (reach(p) + 8)) {
            // the creature died or got away mid-pull: a let-go, no blow
            strikeTarget = -1;
            toCarry(p, "strike lost");
            carry(yaw, 0, 0);
            return;
        }
        ax = t.getX();
        ay = t.getY() + t.getBbHeight() * 0.5;
        az = t.getZ();
        tension = 1;
        // the hand is drawn to where the feet will meet the creature's middle
        if (!AirRules.strikeStep(K, ax, ay + AscentRules.HAND_HEIGHT - 0.7, az)) return;
        float speed = (float) K.speed();
        double nx = K.vx, ny = K.vy, nz = K.vz;
        AirRules.strikeOff(K);
        airMovesUsed = 0;
        strikeTarget = -1;
        releaseAt = ticks;
        toCarry(p, "rope strike");
        event(AscentPayloads.EV_STRIKE, speed);
        if (AscentScores.on()) {
            AscentSounds.move(p, "strike", null, 1F, 1F);
        } else {
            AscentSounds.local(p, AscentContent.LAUNCH.get(), 1F, 0.85F);
            AscentSounds.local(p, AscentContent.LAND.get(), 0.9F, 0.8F);
        }
        if (speed > 1e-3) AscentFx.ring(ax, ay, az, new Vec3(nx / speed, ny / speed, nz / speed), 0.45F);
        FeelDirector.slam(Math.min(1.2F, speed * 0.6F));
        AscentFlow.gain(0.14F);
        trace(String.format("rope strike lands at %.2f b/t", speed));
        carry(yaw, 0, 0);
    }

    // ---- endings --------------------------------------------------------------------------------------------------

    private static void land(LocalPlayer p) {
        double h = K.hspeed(), fall = -impactVy;
        landAt = ticks;
        landSpeed = (float) Math.sqrt(h * h + Math.max(0, fall) * Math.max(0, fall));
        // an aimed dive's last step only closes the gap to its point: it landed at the speed it flew
        boolean dived = diving;
        if (dived) landSpeed = Math.max(landSpeed, (float) AscentRules.diveSpeed(diveAge - 2));
        trace(String.format("lands h %.2f fall %.2f", h, fall));
        boolean wings = state.wings;
        // the flare (SPEC §62): down slow and level on open wings, they cup and the feet touch
        boolean flared = wings && !slamming && ticks - flareAt <= 2;
        end(p, "landed", true);
        if (wings) foldAt = ticks;
        if (flared) {
            WingLayer.flare(p.getId());
            AscentFlow.gain(0.08F);
            event(AscentPayloads.EV_FLARE, landSpeed);
            AscentSounds.move(p, "flare", AscentContent.FLAP.get(), 0.55F, 0.72F);
            trace("a flare");
        }
        AscentSounds.local(p, AscentContent.LAND.get(), Math.min(1F, 0.3F + landSpeed * 0.5F) * (flared ? 0.6F : 1F), wings ? 1.1F : 1F);
        if (slamming) {
            // the dive slam's landing: the ground answers, and everything near is thrown
            slamming = false;
            AscentSounds.local(p, AscentContent.BOOM.get(), 0.7F, 1.35F);
            AscentFx.ring(p.getX(), p.getY() + 0.15, p.getZ(), new Vec3(0, 1, 0), 0.5F);
            FeelDirector.slam(landSpeed);
            AscentFp.slamLand();
            AscentFlow.gain(0.1F);
            event(AscentPayloads.EV_SLAM_LAND, landSpeed);
        } else if (dived) {
            // the aimed dive's landing (SPEC §17.33): the mace's smash, a ring on the ground, the slam's shake; the server
            // lands its shock on the slam's rule
            AscentSounds.local(p, AscentContent.DIVE_LAND.get(), Math.min(1F, 0.45F + landSpeed * 0.35F), 1F);
            AscentFx.ring(p.getX(), p.getY() + 0.15, p.getZ(), new Vec3(0, 1, 0), 0.42F);
            FeelDirector.slam(landSpeed);
            AscentFp.slamLand();
            AscentFlow.gain(0.1F);
            event(AscentPayloads.EV_DIVE_LAND, landSpeed);
            double off = Math.sqrt((p.getX() - diveX) * (p.getX() - diveX) + (p.getZ() - diveZ) * (p.getZ() - diveZ));
            trace(String.format("the dive lands %.2f from its point (%.2f, %.2f, %.2f) at %.2f", off, diveX, diveY, diveZ, landSpeed));
        } else {
            FeelDirector.land(landSpeed);
        }
        airMovesUsed = 0;
        dashKeptAt = -1000;
        event(AscentPayloads.EV_LAND, landSpeed);
        // the run carries on: keep a share of the speed into the first steps (a roll when it's a hard landing)
        if (h > 0.3) {
            double keep = Math.min(h, 0.55) / h;
            p.setDeltaMovement(K.vx * keep, Math.min(0, p.getDeltaMovement().y), K.vz * keep);
        }
        if (landSpeed > 0.75 && !flared) dev.awaken.body.client.ClientMoves.ascentRoll(p);
        // a hard landing off the wings: the flare's tip
        if (wings && !flared && landSpeed > 0.75 && !Lesson.offerMove(Lesson.Kind.FLARE, null, -1)) {
            dev.awaken.core.api.TipHooks.client().offer(dev.awaken.body.ascent.AscentTips.FLARE);
        }
    }

    private static void toCarry(LocalPlayer p, String why) {
        trace("carry (" + why + ")");
        setState(p, AscentState.CARRY);
    }

    static void end(LocalPlayer p, String why, boolean tell) {
        if (state == AscentState.NONE) return;
        trace("ends (" + why + ")");
        if (state.rope) {
            linkEnded(p);
            AscentFx.loose(p.getId(), ax, ay, az);
        }
        state = AscentState.NONE;
        leapt = false;
        if (diving) stopDive();
        stateAge = 0;
        sent = false;
        // a vault ends with the landing (left set, the next carry would pull toward this one's ledge)
        pullTicks = 0;
        strikeTarget = -1;
        aiming = false;
        // the body goes on with the speed the state had (its game motion was left at nothing between steps)
        if (rested) p.setDeltaMovement(K.vx, K.vy, K.vz);
        zeroed = rested = false;
        castMotion = false;
        pushX = pushY = pushZ = 0;
        if (skimming) skimEnd();
        ClientPacketDistributor.sendToServer(new AscentPayloads.State(AscentState.NONE.ordinal(), 0, 0, 0, 0));
    }

    private static void setState(LocalPlayer p, AscentState s) {
        if (s == state) return;
        heldForYou = false;
        if (s != AscentState.CARRY) {
            pullTicks = 0;
            leapt = false;
            if (diving) stopDive();
        }
        AscentState was = state;
        state = s;
        stateAge = 0;
        groundTicks = 0;
        if (s != AscentState.ZIP) strikeTarget = -1;
        // a rope pulls on the raised hand; every other state moves the feet
        if (was.rope && !s.rope) {
            linkEnded(p);
            leftAnchor = anchorBlock;
            leftAt = ticks;
            K.y -= AscentRules.HAND_HEIGHT;
            AscentFx.loose(p.getId(), ax, ay, az);
        }
        if (was == AscentState.NONE) {
            sent = false;
            dev.awaken.body.client.ClientMoves.ascentStarting(p);
        }
        ClientPacketDistributor.sendToServer(new AscentPayloads.State(s.ordinal(), ax, ay, az, (float) len));
    }

    /**
     * The rope left: a link of the chain ended. With the key still held, one that went nowhere stops the chain until the
     * next press, and one that failed after carrying you on spends its anchor for this press (SPEC §62, "The held chain, a
     * link at a time"). The tick's key state is {@link #tetherWas} by now; {@link #anchorBlock} is still the link's.
     */
    private static void linkEnded(LocalPlayer p) {
        String failed = linkFail;
        boolean arrived = linkArrived;
        linkFail = null;
        linkArrived = false;
        BlockPos b = anchorBlock;
        long anchor = b == null ? dev.awaken.body.ascent.RopeChain.NO_ANCHOR : b.asLong();
        if (CHAIN.ended(ticks, p.getX(), p.getY(), p.getZ(), tetherWas, arrived, failed, anchor)) trace("the held rope stops: " + CHAIN.why());
        else if (failed != null && tetherWas) trace("the held rope goes on past a link that failed (" + failed + "), never to that anchor again");
    }

    /**
     * The rope key let go: a press that threw more than one rope, or whose held rope stopped, says so in the log (one
     * line a press at most), so real play shows what a press did.
     */
    private static void pressEnded() {
        int n = CHAIN.throwsThisPress();
        if (n > 1 || CHAIN.stopped() && n > 0) {
            dev.awaken.Awaken.LOG.info("[rope] one press threw {} rope{}{}", n, n == 1 ? "" : "s",
                    CHAIN.stopped() ? ", then the held rope stopped: " + CHAIN.why() : ", one link at a time");
        }
    }

    private static void event(int what, float speed) {
        ClientPacketDistributor.sendToServer(new AscentPayloads.Event(what, speed));
        // a move made: its Lesson leaves, and the first time ever its keys show once (The Way Up, SPEC §63.8)
        Lesson.moved(what);
    }

    /** The server refused the state we're in: let go (a rope's speed carries on; the server never yanks you back). */
    static void refused(int stateId) {
        LocalPlayer p = Minecraft.getInstance().player;
        AscentState s = AscentState.byId(stateId);
        if (p == null || s == null || s != state) return;
        trace("server refused " + s);
        if (s.rope) {
            // not thrown at that block again for a second (held, the rope would throw at it every tick: SPEC §62)
            refusedBlock = anchorBlock;
            refusedAt = ticks;
            linkFail = "the server refused it";
            setState(p, AscentState.CARRY);
        } else if (s == AscentState.SOAR) {
            // the server's Aether ran out first: hold a glide, as at our own empty
            aether = 0;
            skyLetsGo(p);
        } else {
            end(p, "refused", false);
        }
    }

    // ---- lifecycle ----------------------------------------------------------------------------------------------------

    static void tick() {
        ticks++;
        // the game has moved the body by this tick's step: its motion is left at nothing until the next (SPEC §62), so
        // whatever is there then came from outside (a cast, a blast, a knock), whether it was set or added
        LocalPlayer p = Minecraft.getInstance().player;
        zeroed = rested = p != null && state != AscentState.NONE && sent;
        if (zeroed) p.setDeltaMovement(Vec3.ZERO);
    }

    static void forget() {
        paid = payNow = 0;
        reeling = false;
        wallNX = wallNZ = 0;
        slamming = false;
        heldForYou = false;
        swinging = slinging = markerOn = false;
        spring = false;
        groundFor = 0;
        refusedBlock = null;
        zeroed = rested = castMotion = aiming = skimming = attackWas = false;
        seenValid = false;
        stepX = stepZ = 0;
        pushX = pushY = pushZ = 0;
        strikeTarget = -1;
        skimFor = sneakFor = 0;
        castAt = aimEndAt = -1000;
        flareAt = takeoffAt = strikeAt = -100;
        heldOn = fireCool = 0;
        CHAIN.reset();
        linkFail = null;
        linkArrived = false;
        glideFromEmpty = false;
        airMovesUsed = 0;
        dashKeptAt = -1000;
        sprintTaps = 0;
        state = AscentState.NONE;
        stateAge = 0;
        sent = false;
        pullTicks = 0;
        tetherWas = jumpWas = sneakWas = false;
    }

    static void reset() {
        forget();
        tiers = 0;
        mastery = 0;
        masterySynced = false;
        kitLine = kitReel = 0;
        dev.awaken.body.ascent.RopeKit.client = dev.awaken.body.ascent.RopeKit.EMPTY;
        catchAt = kickAt = plantAt = -100;
        aether = 1;
        AscentFlow.reset();
        ticks = 0;
        fireAt = biteAt = releaseAt = launchAt = deployAt = foldAt = boomAt = landAt = flapAt = bonkAt = missAt = soarAt = -100;
    }
}
