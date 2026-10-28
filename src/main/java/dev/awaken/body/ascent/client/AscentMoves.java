package dev.awaken.body.ascent.client;

import dev.awaken.body.ascent.AscentAnchors;
import dev.awaken.body.ascent.AscentContent;
import dev.awaken.body.ascent.AscentPayloads;
import dev.awaken.body.ascent.AscentRules;
import dev.awaken.body.ascent.Windways;
import dev.awaken.body.client.feel.FeelConfig;
import dev.awaken.body.ascent.AscentState;
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
    /** A line thrown for you (the vow's): held until it arrives, key or no key. */
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
    /** The Leaper's air moves used this airtime (bits: 1 double jump, 2 air dash), back on landing or a rope's bite. */
    static int airMovesUsed;
    /** A dive slam is falling: its landing shakes the ground. */
    static boolean slamming;
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
        Input k = in.keyPresses;
        boolean tether = AwakenKeys.ROPE.isDown() && mc.gui.screen() == null;
        boolean jump = k.jump(), sneak = k.shift(), sprint = k.sprint();
        boolean tetherPressed = tether && !tetherWas, jumpPressed = jump && !jumpWas, sneakPressed = sneak && !sneakWas;
        boolean sprintPressed = sprint && !sprintWas;
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
        switch (state) {
            case NONE -> {
                // a Runner move (a wall-run, a hang) holds the body: only the rope cuts in
                boolean runner = dev.awaken.body.client.RunnerMoves.busy();
                if (p.onGround()) airMovesUsed = 0;
                if (tetherPressed && has(AscentPayloads.Tier.TETHER)) {
                    fire(p);
                } else if (runner) {
                    // the Runner's own jumps (a wall jump, a ledge jump)
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
                if (!tether) {
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
                if (!tether && !heldForYou) {
                    toCarry(p, "zip let go");
                    carry(yaw, fwd, right);
                } else {
                    zipTick(p, yaw);
                }
            }
            case CARRY -> {
                if (tetherPressed && has(AscentPayloads.Tier.TETHER) && fire(p)) {
                    // swinging again
                } else if (jumpPressed && dev.awaken.body.client.RunnerMoves.wallBeside(p)) {
                    // flying free into a wall: hand the body to the Runner, which runs along it this same tick
                    p.setDeltaMovement(K.vx, K.vy, K.vz);
                    end(p, "onto a wall", true);
                    return;
                } else if (leaperMove(p, jumpPressed, sprintPressed, sneakPressed, false)) {
                    // the move set this tick's velocity; the carry flies it
                    carry(yaw, fwd, right);
                } else if (jumpPressed && has(AscentPayloads.Tier.WINGS) && canDeploy(p)) {
                    startGlide(p);
                } else {
                    if (pullTicks > 0) pullOver();
                    carry(yaw, fwd, right);
                }
            }
            case GLIDE -> {
                if (tetherPressed && has(AscentPayloads.Tier.TETHER) && fire(p)) {
                    fold(p);
                } else if (has(AscentPayloads.Tier.SKYBORN)
                        && (jumpPressed || AscentRules.sprintTakesFlight(sprintPressed, sprint, glideFromEmpty, aether)) && startSoar(p)) {
                    // flight again: a jump, or sprint whenever the meter allows (SPEC §17.8)
                } else if (sneakPressed) {
                    fold(p);
                    toCarry(p, "wings folded");
                    carry(yaw, fwd, right);
                } else {
                    // in a windway, looking roughly along it, the air holds your line (SPEC §17.12)
                    Windways.Way way = Windways.inside(p.level(), K.x, K.y, K.z);
                    boolean held = way != null && Windways.steer(way, K.x, K.y, K.z, yaw, pitch, LINE);
                    AscentRules.glideStep(K, held ? LINE[0] : yaw, held ? LINE[1] : pitch, glideDrag());
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
                    else if (fwd <= 0 && K.hspeed() < 0.45 && stateAge > 10 && ticks - flapAt >= 18) {
                        // hovering: a slow beat to hold the air (no lift of its own: the hover holds you)
                        flapAt = ticks;
                        AscentSounds.local(p, AscentContent.FLAP.get(), 0.45F, 0.85F + p.getRandom().nextFloat() * 0.1F);
                    }
                    if (sidePressed != 0) sideTap(p, sidePressed);
                    AscentRules.soarStep(K, yaw, pitch, fwd, right, jump, sneak, k.sprint(), S);
                    boomCheck(p);
                    aetherDrain = k.sprint() ? AscentRules.AETHER_BOOST
                            : fwd <= 0 && K.hspeed() < 0.45 ? AscentRules.AETHER_HOVER : AscentRules.AETHER_CRUISE;
                }
            }
        }
        if (jumpPressed) lastJumpPress = ticks;
        aetherTick(p);
        // moving air (updrafts, windways): the free states feel it from the next tick
        double share = switch (state) {
            case CARRY -> AscentRules.AIR_CARRY;
            case GLIDE -> AscentRules.AIR_GLIDE;
            case SOAR -> AscentRules.AIR_SOAR;
            default -> 0;
        };
        if (share > 0 && dev.awaken.core.api.AirHooks.sample(p.level(), K.x, K.y, K.z, AIR)) AscentRules.air(K, AIR, share);
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
            // a teleport (the server put us somewhere): whatever we were doing is over
            end(p, "moved", false);
            return false;
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
        lastX = p.getX();
        lastY = p.getY();
        lastZ = p.getZ();
        wantDx = dx;
        wantDy = dy;
        wantDz = dz;
        sent = true;
        p.resetFallDistance();
    }

    /** A tick of the carry; Flow keeps a little more of the speed. */
    private static void carry(float yaw, double fwd, double right) {
        if (slowLeft > 0) {
            slowLeft--;
            AscentRules.carryStepSlow(K, AscentRules.SLOW);
            return;
        }
        double vy0 = K.vy;
        AscentRules.carryStep(K, yaw, fwd, right, S, AscentRules.CARRY_DRAG_H + AscentRules.FLOW_CARRY * AscentFlow.flow);
        // the top of a big leap's arc: a beat of slow time, the wind holding its breath (weight, SPEC §17.15)
        LocalPlayer p = Minecraft.getInstance().player;
        if (vy0 > 0 && K.vy <= 0 && K.hspeed() >= AscentRules.SLOW_SPEED && ticks - slowAt >= AscentRules.SLOW_EVERY && p != null
                && !FeelConfig.reduced() && heightAboveGround(p) >= AscentRules.SLOW_HEIGHT) {
            slowLeft = AscentRules.SLOW_TICKS;
            slowAt = ticks;
            AscentSounds.duck(AscentRules.SLOW_TICKS);
            trace(String.format("slow beat at the apex, %.2f b/t", K.hspeed()));
        }
    }

    /** The slow beat: ticks left, and when the last one began. */
    private static int slowLeft, slowAt = -1000;

    private static double glideDrag() {
        return 1 - AscentRules.MASTERY_GLIDE_DRAG * lv(dev.awaken.body.ascent.AscentMemory.Track.WINGS);
    }

    // ---- the rope ---------------------------------------------------------------------------------------------------

    /** Fires the rope; true when it found an anchor (the state is then TETHER or ZIP). */
    private static boolean fire(LocalPlayer p) {
        Vec3 eye = p.getEyePosition();
        // where you look: the camera in Shoulder Surfing's decoupled view, else the body's own look
        boolean view = dev.awaken.body.client.feel.ShoulderView.aim(VIEW);
        float aimYaw = view ? VIEW[0] : p.getYRot(), aimPitch = view ? VIEW[1] : p.getXRot();
        // a rope just let go of passes through the ring or branch it held: throw past it to the next one up
        AscentAnchors.Anchor a = AscentAnchors.find(p.level(), p, eye, aimYaw, aimPitch, reach(p), ticks - leftAt <= 20 ? leftAnchor : null);
        fireAt = ticks;
        AscentFp.fired();
        AscentSounds.local(p, AscentContent.ROPE_FIRE.get(), 0.8F, 0.95F + p.getRandom().nextFloat() * 0.1F);
        event(AscentPayloads.EV_FIRE, 0);
        if (a == null) {
            // nothing to bite: the rope flies out and falls back, and you keep falling as you were
            missAt = ticks;
            Vec3 look = p.getLookAngle();
            missX = eye.x + look.x * 18;
            missY = eye.y + look.y * 18;
            missZ = eye.z + look.z * 18;
            AscentFx.miss(p, missX, missY, missZ);
            trace("rope missed");
            return false;
        }
        Vec3 v = state == AscentState.NONE ? new Vec3(p.getX() - p.xo, p.getDeltaMovement().y, p.getZ() - p.zo) : null;
        double hx = p.getX(), hy = p.getY() + AscentRules.HAND_HEIGHT, hz = p.getZ();
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
        // nearly straight overhead (a ring, a branch, a keel): a zip up to it and a hang under it, not a pendulum
        boolean overhead = dy > AscentRules.ZIP_UP_MIN_RISE && Math.sqrt(dx * dx + dz * dz) < dy * AscentRules.ZIP_UP_SLOPE;
        if (dy > AscentRules.TETHER_SWING_MIN_RISE && !overhead) {
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
        if (ropeTravel > 0) {
            ropeTravel--;
            if (ropeTravel == 0) bite(p, 0.6F);
            carry(yaw, 0, 0);
        } else if (zipStalled(p)) {
            // pressed against something on the way: let go and fall (never pinned for good)
            trace("zip blocked");
            toCarry(p, "zip blocked");
            carry(yaw, 0, 0);
        } else if (AscentRules.zipStep(K, ax, ay, az)) {
            BlockPos b = anchorBlock;
            if (b != null && ay < b.getY() + 0.02) {
                // the rope bit an underside: near a ledge's edge (a floor with room on it), out, up and over its lip
                if (ledgeOver(p, b)) {
                    trace(String.format("zip arrived: over the ledge's lip (%.1f up)", pullTop - (K.y - AscentRules.HAND_HEIGHT)));
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

    /** The body's half width and a little: how far out from a block's face the body clears it. */
    private static final double VAULT_CLEAR = 0.5 + 0.3 + 0.08;

    /** A vault onto {@code b}'s top over its edge on the side ({@code ex}, {@code ez}): out, up, then in (pullOver). */
    private static void vault(BlockPos b, int ex, int ez) {
        K.vx = 0;
        K.vy = 0;
        K.vz = 0;
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
        if (state == AscentState.TETHER) {
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
        if (ropeTravel > 0) {
            toCarry(p, "let go before the bite");
            carry(p.getYRot(), 0, 0);
            return;
        }
        // let go by a passage, on a rappel or hanging still: in onto its floor rather than down past it
        if ((state == AscentState.RAPPEL || K.speed() < 0.45) && dropIn(p)) {
            releaseAt = ticks;
            releaseQuality = 0;
            AscentSounds.local(p, AscentContent.ROPE_RELEASE.get(), 0.5F, 1.1F);
            AscentFp.released();
            toCarry(p, "dropped in");
            carry(p.getYRot(), 0, 0);
            return;
        }
        float q = (float) AscentRules.releaseQuality(K, ax, ay, az);
        if (q > 0) {
            double f = 1 + (AscentRules.RELEASE_BOOST - 1) * q;
            K.vx *= f;
            K.vy *= f;
            K.vz *= f;
        }
        releaseAt = ticks;
        releaseQuality = q;
        AscentFlow.gain(0.04F + 0.12F * q);
        AscentSounds.local(p, AscentContent.ROPE_RELEASE.get(), 0.6F, 1.0F + q * 0.15F);
        FeelDirector.release(q);
        AscentFp.released();
        trace(String.format("release (window %.2f)", q));
        toCarry(p, "released");
        carry(p.getYRot(), 0, 0);
    }

    /** Jumping off the rope: up and on, faster than you swung. */
    private static void launch(LocalPlayer p) {
        float q = (float) AscentRules.releaseQuality(K, ax, ay, az);
        K.vx *= AscentRules.LAUNCH_BOOST;
        K.vz *= AscentRules.LAUNCH_BOOST;
        K.vy = Math.max(K.vy, 0) + AscentRules.LAUNCH_UP;
        launchAt = ticks;
        releaseQuality = q;
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
        if (!has(AscentPayloads.Tier.LEAPER) || p.onGround()) return false;
        boolean dbl = jumpPressed && (airMovesUsed & 1) == 0 && !dev.awaken.body.client.RunnerMoves.wallBeside(p);
        boolean dash = sprintPressed && (airMovesUsed & 2) == 0;
        boolean dive = sneakPressed && !slamming && heightAboveGround(p) >= AscentRules.SLAM_MIN_HEIGHT;
        if (!dbl && !dash && !dive) return false;
        if (fromNone) K.set(p.getX(), p.getY(), p.getZ(), p.getX() - p.xo, p.getDeltaMovement().y, p.getZ() - p.zo);
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
     * The Sovereign's Judgement Fall (SPEC §17.19): from at least {@code minHeight} up, the body drops at {@code speed},
     * a dive whose landing the server answers as Judgement. False when too low or held by a rope.
     */
    static boolean judgementDive(LocalPlayer p, double minHeight, double speed) {
        if (state.rope || p.onGround() || heightAboveGround(p) < minHeight) return false;
        if (state == AscentState.NONE) K.set(p.getX(), p.getY(), p.getZ(), p.getX() - p.xo, p.getDeltaMovement().y, p.getZ() - p.zo);
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
            K.set(p.getX(), p.getY(), p.getZ(), p.getX() - p.xo, 0.9, p.getZ() - p.zo);
        }
        startSoar(p);
    }

    private static double heightAboveGround(LocalPlayer p) {
        return p.getY() - AscentAnchors.floorUnder(p.level(), p, p.getX(), p.getY() + 0.5, p.getZ(), 40);
    }

    // ---- wings --------------------------------------------------------------------------------------------------------

    private static void startGlide(LocalPlayer p) {
        if (state == AscentState.NONE) K.set(p.getX(), p.getY(), p.getZ(), p.getX() - p.xo, p.getDeltaMovement().y, p.getZ() - p.zo);
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
        if (state == AscentState.NONE) K.set(p.getX(), p.getY(), p.getZ(), p.getX() - p.xo, p.getDeltaMovement().y, p.getZ() - p.zo);
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

    // ---- endings --------------------------------------------------------------------------------------------------

    private static void land(LocalPlayer p) {
        double h = K.hspeed(), fall = -impactVy;
        landAt = ticks;
        landSpeed = (float) Math.sqrt(h * h + Math.max(0, fall) * Math.max(0, fall));
        trace(String.format("lands h %.2f fall %.2f", h, fall));
        boolean wings = state.wings;
        end(p, "landed", true);
        if (wings) foldAt = ticks;
        AscentSounds.local(p, AscentContent.LAND.get(), Math.min(1F, 0.3F + landSpeed * 0.5F), wings ? 1.1F : 1F);
        if (slamming) {
            // the dive slam's landing: the ground answers, and everything near is thrown
            slamming = false;
            AscentSounds.local(p, AscentContent.BOOM.get(), 0.7F, 1.35F);
            AscentFx.ring(p.getX(), p.getY() + 0.15, p.getZ(), new Vec3(0, 1, 0), 0.5F);
            FeelDirector.slam(landSpeed);
            AscentFp.slamLand();
            AscentFlow.gain(0.1F);
            event(AscentPayloads.EV_SLAM_LAND, landSpeed);
        } else {
            FeelDirector.land(landSpeed);
        }
        airMovesUsed = 0;
        event(AscentPayloads.EV_LAND, landSpeed);
        // the run carries on: keep a share of the speed into the first steps (a roll when it's a hard landing)
        if (h > 0.3) {
            double keep = Math.min(h, 0.55) / h;
            p.setDeltaMovement(K.vx * keep, p.getDeltaMovement().y, K.vz * keep);
        }
        if (landSpeed > 0.75) dev.awaken.body.client.ClientMoves.ascentRoll(p);
    }

    private static void toCarry(LocalPlayer p, String why) {
        trace("carry (" + why + ")");
        setState(p, AscentState.CARRY);
    }

    static void end(LocalPlayer p, String why, boolean tell) {
        if (state == AscentState.NONE) return;
        trace("ends (" + why + ")");
        if (state.rope) AscentFx.loose(p.getId(), ax, ay, az);
        state = AscentState.NONE;
        stateAge = 0;
        sent = false;
        // a vault ends with the landing (left set, the next carry would pull toward this one's ledge)
        pullTicks = 0;
        slowLeft = 0;
        ClientPacketDistributor.sendToServer(new AscentPayloads.State(AscentState.NONE.ordinal(), 0, 0, 0, 0));
    }

    private static void setState(LocalPlayer p, AscentState s) {
        if (s == state) return;
        heldForYou = false;
        if (s != AscentState.CARRY) {
            pullTicks = 0;
            slowLeft = 0;
        }
        AscentState was = state;
        state = s;
        stateAge = 0;
        groundTicks = 0;
        // a rope pulls on the raised hand; every other state moves the feet
        if (was.rope && !s.rope) {
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

    private static void event(int what, float speed) {
        ClientPacketDistributor.sendToServer(new AscentPayloads.Event(what, speed));
    }

    /** The server refused the state we're in: let go (a rope's speed carries on; the server never yanks you back). */
    static void refused(int stateId) {
        LocalPlayer p = Minecraft.getInstance().player;
        AscentState s = AscentState.byId(stateId);
        if (p == null || s == null || s != state) return;
        trace("server refused " + s);
        if (s.rope) {
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
    }

    static void forget() {
        paid = payNow = 0;
        reeling = false;
        wallNX = wallNZ = 0;
        slamming = false;
        heldForYou = false;
        glideFromEmpty = false;
        airMovesUsed = 0;
        state = AscentState.NONE;
        stateAge = 0;
        sent = false;
        pullTicks = 0;
        slowLeft = 0;
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
