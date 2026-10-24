package dev.awaken.body.ascent.client;

import dev.awaken.body.ascent.AscentAnchors;
import dev.awaken.body.ascent.AscentContent;
import dev.awaken.body.ascent.AscentPayloads;
import dev.awaken.body.ascent.AscentRules;
import dev.awaken.body.ascent.AscentState;
import dev.awaken.body.client.feel.FeelDirector;
import dev.awaken.core.client.AwakenKeys;
import dev.awaken.veil.Veil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
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
 * Controls (Phase 0): the movement key (Left Alt) fires and holds the rope; letting go flies on with the swing's
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
    static final AscentRules.Kin K = new AscentRules.Kin();
    private static final double[] S = new double[3], AIR = new double[3];

    // the rope
    static double ax, ay, az, len, lenTarget;
    /** The block the rope bit (null: it bit nothing we could find, so it is never "gone"). */
    private static net.minecraft.core.@org.jspecify.annotations.Nullable BlockPos anchorBlock;
    /** Ticks until a fired rope bites (it flies out first: the anticipation); 0 once it holds. */
    static int ropeTravel;
    static boolean ropeTaut;
    /** A line thrown for you (the vow's): held until it arrives, key or no key. */
    static boolean heldForYou;
    static double tension;
    // input edges
    private static boolean tetherWas, jumpWas, sneakWas, sprintWas;
    /** The Leaper's air moves used this airtime (bits: 1 double jump, 2 air dash), back on landing or a rope's bite. */
    static int airMovesUsed;
    /** A dive slam is falling: its landing shakes the ground. */
    static boolean slamming;
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

    /** The speed the state keeps (blocks a tick), 0 on the ground. */
    public static double speed() {
        return state == AscentState.NONE ? 0 : K.speed();
    }

    // ---- input ----------------------------------------------------------------------------------------------------

    static void onInput(MovementInputUpdateEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (!(e.getEntity() instanceof LocalPlayer p) || p != mc.player) return;
        ClientInput in = e.getInput();
        Input k = in.keyPresses;
        boolean tether = AwakenKeys.MOVEMENT.isDown() && mc.gui.screen() == null;
        boolean jump = k.jump(), sneak = k.shift(), sprint = k.sprint();
        boolean tetherPressed = tether && !tetherWas, jumpPressed = jump && !jumpWas, sneakPressed = sneak && !sneakWas;
        boolean sprintPressed = sprint && !sprintWas;
        tetherWas = tether;
        jumpWas = jump;
        sneakWas = sneak;
        sprintWas = sprint;
        double fwd = (k.forward() ? 1 : 0) - (k.backward() ? 1 : 0);
        double right = (k.right() ? 1 : 0) - (k.left() ? 1 : 0);
        if (off(p)) {
            if (state != AscentState.NONE) end(p, "off", false);
            sent = false;
            return;
        }
        if (state != AscentState.NONE && !reconcile(p)) return;
        stateAge++;
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
                } else if (jumpPressed && ropeTravel == 0) {
                    launch(p);
                } else {
                    tetherTick(p, yaw, fwd, right, sneak);
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
                    carry(yaw, fwd, right);
                }
            }
            case GLIDE -> {
                if (tetherPressed && has(AscentPayloads.Tier.TETHER) && fire(p)) {
                    fold(p);
                } else if (jumpPressed && has(AscentPayloads.Tier.SKYBORN)) {
                    startSoar(p);
                } else if (sneakPressed) {
                    fold(p);
                    toCarry(p, "wings folded");
                    carry(yaw, fwd, right);
                } else {
                    AscentRules.glideStep(K, yaw, pitch, glideDrag());
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
                    AscentRules.soarStep(K, yaw, pitch, fwd, right, jump, sneak, k.sprint(), S);
                    boomCheck(p);
                }
            }
        }
        if (jumpPressed) lastJumpPress = ticks;
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
                || p.isInWater() || p.isInLava() || Veil.isSpirit(p) || tiers == 0;
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
        if (bonked && p.horizontalCollision && hBefore - K.hspeed() > 0.45) {
            bonkAt = ticks;
            bonkSpeed = (float) (hBefore - K.hspeed());
            trace("bonk " + String.format("%.2f", bonkSpeed));
            FeelDirector.bonk(bonkSpeed);
            AscentFlow.bonk();
        }
        groundTicks = p.onGround() ? groundTicks + 1 : 0;
        boolean landed = switch (state) {
            case ZIP -> false;
            // a swing that scrapes the floor goes on; one that stays down has landed
            case TETHER -> groundTicks >= AscentRules.TETHER_GROUND_TICKS;
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
        AscentRules.carryStep(K, yaw, fwd, right, S, AscentRules.CARRY_DRAG_H + AscentRules.FLOW_CARRY * AscentFlow.flow);
    }

    private static double glideDrag() {
        return 1 - AscentRules.MASTERY_GLIDE_DRAG * lv(dev.awaken.body.ascent.AscentMemory.Track.WINGS);
    }

    // ---- the rope ---------------------------------------------------------------------------------------------------

    /** Fires the rope; true when it found an anchor (the state is then TETHER or ZIP). */
    private static boolean fire(LocalPlayer p) {
        Vec3 eye = p.getEyePosition();
        AscentAnchors.Anchor a = AscentAnchors.find(p.level(), p, eye, p.getYRot(), p.getXRot(),
                AscentRules.TETHER_RANGE + AscentRules.MASTERY_REACH * lv(dev.awaken.body.ascent.AscentMemory.Track.TETHER));
        fireAt = ticks;
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
        if (dy > AscentRules.TETHER_SWING_MIN_RISE) {
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
        heldForYou = true;
        if (p.onGround()) K.vy = Math.max(K.vy, 0.3);
        trace(String.format("the vow's line, zip %.1f", dist));
        return true;
    }

    private static void zipTick(LocalPlayer p, float yaw) {
        if (ropeTravel > 0) {
            ropeTravel--;
            if (ropeTravel == 0) bite(p, 0.6F);
            carry(yaw, 0, 0);
        } else if (AscentRules.zipStep(K, ax, ay, az)) {
            // arrived: a hop up onto whatever the rope bit, then fly on
            K.vx *= 0.45;
            K.vz *= 0.45;
            K.vy = Math.max(K.vy * 0.3, 0.48);
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
        if (stateAge % 2 == 0) {
            Vec3 hand = new Vec3(K.x, K.y, K.z);
            Vec3 snag = AscentAnchors.snag(p.level(), p, hand, ax, ay, az);
            // swung round a trunk or a corner: the rope wraps there and the swing goes on, shorter, about the new point
            // (a snag under the hand, or too close to swing from, lets go instead)
            boolean wrap = snag != null && snag.y > hand.y - 1 && hand.distanceTo(snag) >= AscentRules.TETHER_MIN + 1;
            if (wrap) {
                wrap(p, snag, hand.distanceTo(snag));
            } else if (snag != null || anchorGone(p)) {
                trace(snag == null ? "rope cut (anchor gone)" : String.format("rope cut (snag %.1f off, %.1f under the hand)",
                        hand.distanceTo(snag), hand.y - snag.y));
                AscentSounds.local(p, AscentContent.ROPE_RELEASE.get(), 0.7F, 1.4F);
                toCarry(p, "rope cut");
                carry(yaw, fwd, right);
                return;
            }
        }
        // reel: fast up to the length that clears the ground (the yank into the swing), and on while sneaking
        if (len > lenTarget + 1e-3) {
            len = AscentRules.reel(K, ax, ay, az, len, Math.min(AscentRules.TETHER_FIT_REEL, len - lenTarget));
        } else if (sneak && len > AscentRules.TETHER_MIN + 1e-3) {
            len = AscentRules.reel(K, ax, ay, az, len, Math.min(AscentRules.TETHER_REEL, len - AscentRules.TETHER_MIN));
            lenTarget = len;
        }
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
            trace(String.format("air dash %.2f", speed));
        } else {
            slamming = true;
            K.vx *= 0.25;
            K.vz *= 0.25;
            K.vy = -AscentRules.SLAM_SPEED;
            AscentSounds.local(p, AscentContent.WINGS_FOLD.get(), 0.8F, 0.7F);
            event(AscentPayloads.EV_DIVE, 0);
            FeelDirector.dive();
            trace("dive slam");
        }
        AscentFlow.gain(0.08F);
        if (fromNone) setState(p, AscentState.CARRY);
        return true;
    }

    private static double heightAboveGround(LocalPlayer p) {
        return p.getY() - AscentAnchors.floorUnder(p.level(), p, p.getX(), p.getY() + 0.5, p.getZ(), 40);
    }

    // ---- wings --------------------------------------------------------------------------------------------------------

    private static void startGlide(LocalPlayer p) {
        if (state == AscentState.NONE) K.set(p.getX(), p.getY(), p.getZ(), p.getX() - p.xo, p.getDeltaMovement().y, p.getZ() - p.zo);
        AscentRules.glideCatch(K, p.getYRot(), p.getXRot());
        deployAt = ticks;
        AscentFlow.gain(0.06F);
        boomArmed = K.speed() < AscentRules.BOOM_SPEED;
        setState(p, AscentState.GLIDE);
        AscentSounds.local(p, AscentContent.WINGS_OPEN.get(), 1F, 1F);
        event(AscentPayloads.EV_DEPLOY, (float) K.speed());
        FeelDirector.deploy();
        trace("wings open");
        AscentRules.glideStep(K, p.getYRot(), p.getXRot(), glideDrag());
    }

    private static void fold(LocalPlayer p) {
        foldAt = ticks;
        AscentSounds.local(p, AscentContent.WINGS_FOLD.get(), 0.7F, 1F);
    }

    private static void startSoar(LocalPlayer p) {
        if (state == AscentState.NONE) K.set(p.getX(), p.getY(), p.getZ(), p.getX() - p.xo, p.getDeltaMovement().y, p.getZ() - p.zo);
        boolean fromGlide = state == AscentState.GLIDE;
        soarAt = ticks;
        setState(p, AscentState.SOAR);
        if (!fromGlide) deployAt = ticks;
        flap(p);
        trace("takes flight");
        AscentRules.soarStep(K, p.getYRot(), p.getXRot(), 0, 0, false, false, false, S);
    }

    private static void flap(LocalPlayer p) {
        flapAt = ticks;
        AscentFlow.gain(0.02F);
        K.vy = Math.max(K.vy, 0) + AscentRules.SOAR_FLAP;
        AscentSounds.local(p, AscentContent.FLAP.get(), 0.9F, 0.9F + p.getRandom().nextFloat() * 0.15F);
        event(AscentPayloads.EV_FLAP, 0);
        FeelDirector.flap();
    }

    private static void boomCheck(LocalPlayer p) {
        double s = K.speed();
        if (boomArmed && s >= AscentRules.BOOM_SPEED) {
            boomArmed = false;
            boomAt = ticks;
            AscentFlow.gain(0.25F);
            trace("sonic boom");
            FeelDirector.boom();
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
        ClientPacketDistributor.sendToServer(new AscentPayloads.State(AscentState.NONE.ordinal(), 0, 0, 0, 0));
    }

    private static void setState(LocalPlayer p, AscentState s) {
        if (s == state) return;
        heldForYou = false;
        AscentState was = state;
        state = s;
        stateAge = 0;
        groundTicks = 0;
        // a rope pulls on the raised hand; every other state moves the feet
        if (was.rope && !s.rope) {
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
        } else {
            end(p, "refused", false);
        }
    }

    // ---- lifecycle ----------------------------------------------------------------------------------------------------

    static void tick() {
        ticks++;
    }

    static void forget() {
        slamming = false;
        heldForYou = false;
        airMovesUsed = 0;
        state = AscentState.NONE;
        stateAge = 0;
        sent = false;
        tetherWas = jumpWas = sneakWas = false;
    }

    static void reset() {
        forget();
        tiers = 0;
        mastery = 0;
        AscentFlow.reset();
        ticks = 0;
        fireAt = biteAt = releaseAt = launchAt = deployAt = foldAt = boomAt = landAt = flapAt = bonkAt = missAt = soarAt = -100;
    }
}
