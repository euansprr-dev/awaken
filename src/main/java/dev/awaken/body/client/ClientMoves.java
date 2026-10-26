package dev.awaken.body.client;

import dev.awaken.body.Body;
import dev.awaken.body.BodyContent;
import dev.awaken.body.BodyRules;
import dev.awaken.body.Move;
import dev.awaken.body.VaultRules;
import dev.awaken.body.net.BodyPayloads;
import dev.awaken.core.api.MovementHooks;
import dev.awaken.progression.Progression;
import dev.awaken.veil.Veil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * The local player's moves, predicted. Everything happens in the movement-input event, right after this tick's keys
 * are read and before vanilla moves the body: a move sets the velocity for the tick (or the input it lets through)
 * and vanilla's own collision does the rest, so walls, gaps and ledges are the same ones the server checks. Each start
 * goes to the server once; a refusal stops the move here. Players who never awakened are never touched.
 */
public final class ClientMoves {
    private ClientMoves() {}

    /** Client clock: ticks while in a world. */
    static int ticks;

    private static boolean shiftWas, jumpWas, sprintWas, jumpSuppressed;
    private static int sprintTapAt = -100;
    // tactical sprint
    static boolean tac;
    static int tacStart = -100, tacEnd = -100, tacReadyAt;
    private static int tacNoSprint;
    // slide
    static boolean sliding;
    static int slideStart = -100, slideEnd = -100, slideReadyAt, slideAge, slideJumpAt = -100;
    private static int slideAir;
    static double slideX, slideZ, slideV0, slideSpeed;
    private static double expectH;
    static boolean slideFromTac;
    private static boolean slideWasSprinting;
    // stair slide (SPEC §17.22): down a run of stairs or slabs on the line through the step noses
    static boolean stairSliding;
    private static int stairTicks;
    private static double stairV, stairFallX, stairFallZ;
    private static final BodyRules.StairProfile STAIRS = new BodyRules.StairProfile();
    private static final double[] FALL = new double[2], WAY = new double[2], STEP = new double[3], ACC = new double[2];
    // the landing slide (SPEC §17.24): crouch pressed in the air, still held at touchdown
    private static boolean wasGround = true, airShift, airSprint;
    private static double airSpeed, airX, airZ;
    // mantle
    static boolean mantling;
    static int mantleStart = -100, mantleEnd = -100, mantleAge;
    private static double msx, msy, msz;
    private static BodyRules.@Nullable Ledge ledge;
    // a crossing (SPEC §17.27): a fence, gate or wall vaulted, climbed over or onto
    static boolean vaulting;
    static int vaultStart = -100, vaultEnd = -100, vaultAge;
    static VaultRules.@Nullable Vault vault;
    private static boolean vaultWasSprinting, vaultPlanted;
    /** Up onto a top: forward is held back until it's let go, so the body stops there (over a drop too). */
    static boolean perchHold;
    private static final double[] RAIL = new double[2];
    // roll
    static int rollAt = -100;
    // a power's flight or an elytra just now: their landings are never rolls
    private static int lastFly = -1000, lastGlide = -1000;
    private static final double[] PATH = new double[3];
    private static final Input[] INPUTS = new Input[128];
    /** Autopilot runs log every move's start and end (and why), so a tour's frames can be checked against them. */
    private static final boolean TRACE = System.getProperty("awaken.autopilot") != null;

    static void trace(String what) {
        if (!TRACE) return;
        LocalPlayer p = Minecraft.getInstance().player;
        dev.awaken.Awaken.LOG.info("[body] t{} {}{}", ticks, what,
                p == null ? "" : String.format(" at (%.2f, %.2f, %.2f) v %.2f", p.getX(), p.getY(), p.getZ(), p.getDeltaMovement().horizontalDistance()));
    }

    // ---- the input event --------------------------------------------------------------------------------------------

    static void onInput(MovementInputUpdateEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (!(e.getEntity() instanceof LocalPlayer p) || p != mc.player) return;
        ClientInput in = e.getInput();
        Input k = in.keyPresses;
        boolean shift = k.shift(), jump = k.jump(), sprintKey = k.sprint();
        boolean shiftPressed = shift && !shiftWas, jumpPressed = jump && !jumpWas, sprintPressed = sprintKey && !sprintWas;
        shiftWas = shift;
        jumpWas = jump;
        sprintWas = sprintKey;
        if (p.mayFly()) lastFly = ticks;
        if (p.isFallFlying()) lastGlide = ticks;
        // the air before a landing: how fast and which way it went, whether it ran, whether crouch was pressed
        boolean ground = p.onGround(), landed = ground && !wasGround;
        wasGround = ground;
        if (!ground) {
            Vec3 av = p.getDeltaMovement();
            airSpeed = av.horizontalDistance();
            if (airSpeed > 1e-3) {
                airX = av.x / airSpeed;
                airZ = av.z / airSpeed;
            }
            airSprint |= p.isSprinting();
            if (shiftPressed) airShift = true;
        }
        if (!shift) airShift = false;
        boolean landSlide = landed && landSlideWanted(p, shift);
        if (ground && !landed) airShift = airSprint = false; // the landing tick still needs them (the slide starts then)
        // the Runner's Kit (SPEC §16.3): every move from the first second; without it, nothing until the awakening
        boolean kit = dev.awaken.body.BodyConfig.runnersKit();
        boolean awakened = kit || Progression.profile(p).awakened();
        // the one-button free-run (SPEC §17.14): sprint held with forward reads the ground ahead
        FreeRun.begin(p, k);
        FreeRun.climb(p);
        if (!awakened && (tac || sliding || mantling || vaulting)) stopAll(p);
        if (jumpSuppressed) {
            if (jump) in.keyPresses = with(in.keyPresses, false, in.keyPresses.shift());
            else jumpSuppressed = false;
        }
        if (mantling) {
            mantleTick(p);
            return;
        }
        if (vaulting) {
            in.keyPresses = with(in.keyPresses, false, false); // no hop out of it, no edge-sneak backing off the rail
            vaultTick(p, in);
            return;
        }
        if (perchHold) perch(p, in, k, jumpPressed);
        if (sliding) {
            slideTick(p, in, jumpPressed);
            return;
        }
        if (RunnerMoves.active()) {
            if (kit) RunnerMoves.onInput(p, in, jumpPressed, shiftPressed);
            else RunnerMoves.stop(p, "kit off");
            return;
        }
        if (awakened) {
            boolean off = off(p);
            if (tac) tacTick(p, off);
            if (!off) {
                if (sprintPressed) {
                    if (!tac && ticks - sprintTapAt <= BodyRules.TAC_DOUBLE_TAP) {
                        startTac(p);
                        sprintTapAt = -100;
                    } else {
                        sprintTapAt = ticks;
                    }
                }
                if ((jumpPressed || FreeRun.held) && !k.backward() && canMantle(p)) {
                    // a fence, gate or wall (SPEC §17.27): over it, or up onto it
                    VaultRules.Vault v = vaultAhead(p, jumpPressed);
                    if (v != null) {
                        startVault(p, in, v);
                        return;
                    }
                    BodyRules.Ledge l = BodyRules.findLedge(p.level(), CollisionContext.of(p), p.getX(), p.getY(), p.getZ(), p.getYRot(), BodyRules.MANTLE_REACH);
                    if (l != null) {
                        startMantle(p, in, l);
                        return;
                    }
                }
                if (landSlide) {
                    startSlide(p, in, airSpeed, airX, airZ);
                    trace("landing slide");
                    return;
                }
                if (shiftPressed && canSlide(p)) {
                    startSlide(p, in);
                    return;
                }
                if (kit && !perchHold && FreeRun.ground(p, p.level(), CollisionContext.of(p), ticks)) return;
                if (kit) balance(p, in, k);
            }
        }
        if (kit) RunnerMoves.onInput(p, in, jumpPressed, shiftPressed);
    }

    /** A move's own jump (a wall jump, a climb): holding the key through it doesn't hop again on landing. */
    static void suppressJump() {
        jumpSuppressed = true;
    }

    static boolean off(LocalPlayer p) {
        return p.isPassenger() || p.isSleeping() || p.isSpectator() || !p.isAlive() || p.getAbilities().flying || p.isFallFlying()
                || Veil.isSpirit(p) || dev.awaken.body.ascent.client.AscentMoves.active();
    }

    /** An Ascent state takes the body into the air (a rope, wings): whatever grounded move was running stops. */
    public static void ascentStarting(LocalPlayer p) {
        stopAll(p);
        lastFly = ticks; // its landing is the Ascent's, never a second roll here
    }

    /** An Ascent state landed hard: the soft-landing roll's clip, sound and ring (the Ascent decided the landing). */
    public static void ascentRoll(LocalPlayer p) {
        rollAt = ticks;
        trace("roll out of the Ascent");
        BodyAnims.play(p.getId(), Move.ROLL, p.getYRot());
        BodyFx.sound(p, BodyContent.ROLL.get(), 0.9F, 1F);
        BodyFx.rollRing(p);
    }

    /** The same keys with jump and shift replaced (cached: nothing allocated per tick). */
    static Input with(Input k, boolean jump, boolean shift) {
        int i = (k.forward() ? 1 : 0) | (k.backward() ? 2 : 0) | (k.left() ? 4 : 0) | (k.right() ? 8 : 0) | (jump ? 16 : 0) | (shift ? 32 : 0)
                | (k.sprint() ? 64 : 0);
        Input c = INPUTS[i];
        if (c == null) INPUTS[i] = c = new Input(k.forward(), k.backward(), k.left(), k.right(), jump, shift, k.sprint());
        return c;
    }

    private static void send(Move move, float yaw, double x, double y, double z, double speed, boolean flag) {
        ClientPacketDistributor.sendToServer(new BodyPayloads.Start(move.ordinal(), yaw, x, y, z, (float) speed, flag));
    }

    private static void sendEnd(Move move) {
        ClientPacketDistributor.sendToServer(new BodyPayloads.End(move.ordinal()));
    }

    private static float yawOf(double dx, double dz) {
        return (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
    }

    // ---- tactical sprint --------------------------------------------------------------------------------------------

    private static void startTac(LocalPlayer p) {
        if (!Body.unlocked(p, Move.TACTICAL_SPRINT) || p.isInLiquid() || !p.isSprinting() || ticks < tacReadyAt || p.isUsingItem()) return;
        if (!p.isCreative() && p.getFoodData().getFoodLevel() <= 6) return;
        tac = true;
        tacStart = ticks;
        tacNoSprint = 0;
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.addOrUpdateTransientModifier(BodyRules.TAC_MODIFIER);
        send(Move.TACTICAL_SPRINT, p.getYRot(), 0, 0, 0, 0, false);
        trace("tactical sprint");
        BodyAnims.play(p.getId(), Move.TACTICAL_SPRINT, p.getYRot());
        BodyFx.sound(p, BodyContent.SPRINT.get(), 0.7F, 1F);
        BodyFx.kick(p, -Mth.sin(p.getYRot() * Mth.DEG_TO_RAD), Mth.cos(p.getYRot() * Mth.DEG_TO_RAD));
    }

    private static void tacTick(LocalPlayer p, boolean off) {
        if (ticks - tacStart >= BodyRules.TAC_TICKS || off || p.isInLiquid() || p.isUsingItem()) {
            endTac(p, true);
            return;
        }
        tacNoSprint = p.isSprinting() ? 0 : tacNoSprint + 1;
        if (tacNoSprint > 2) endTac(p, true);
    }

    static void endTac(LocalPlayer p, boolean tellServer) {
        if (!tac) return;
        trace("tactical sprint ends after " + (ticks - tacStart));
        tac = false;
        tacEnd = ticks;
        tacReadyAt = ticks + BodyRules.tacRecharge(p);
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(BodyRules.TAC_ID);
        BodyAnims.stop(p.getId(), Move.TACTICAL_SPRINT);
        if (tellServer) sendEnd(Move.TACTICAL_SPRINT);
    }

    /** The attack key: a swing ends the tactical sprint and lands as normal. */
    static void onAttackKey() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null && tac) endTac(p, true);
    }

    // ---- slide ------------------------------------------------------------------------------------------------------

    private static boolean canSlide(LocalPlayer p) {
        return Body.unlocked(p, Move.SLIDE) && (p.onGround() || onStairs(p)) && (p.isSprinting() || tac) && !p.isInLiquid() && !p.isUsingItem()
                && ticks >= slideReadyAt && !mantling;
    }

    /** Running down stairs the feet leave each step: a slide still starts when a run is under them. */
    private static boolean onStairs(LocalPlayer p) {
        Vec3 v = p.getDeltaMovement();
        double hs = v.horizontalDistance();
        if (hs < 0.02) return false;
        return BodyRules.stairStart(p.level(), CollisionContext.of(p), STAIRS, p.getX(), p.getY(), p.getZ(), v.x / hs, v.z / hs, FALL);
    }

    /**
     * A landing slide wanted now: crouch pressed in the air and held at touchdown, a running jump's speed, ground to
     * slide onto ahead (not an edge-sneak), and the slide otherwise free to start. Asked at touchdown, also by the
     * fall path, whose roll then stays quiet: the slide is the landing (the server softens the fall as for a roll).
     */
    private static boolean landSlideWanted(LocalPlayer p, boolean shift) {
        return airShift && shift && BodyRules.landSlideFast(airSpeed, airSprint) && Body.unlocked(p, Move.SLIDE) && !sliding && !mantling
                && ticks >= slideReadyAt && !p.isInLiquid() && !p.isUsingItem() && !RunnerMoves.active() && !off(p)
                && BodyRules.groundAhead(p.level(), CollisionContext.of(p), p.getX(), p.getY(), p.getZ(), airX, airZ);
    }

    private static void startSlide(LocalPlayer p, ClientInput in) {
        startSlide(p, in, -1, 0, 0);
    }

    /** {@code landSpeed} &ge; 0: a landing slide along (lx, lz) from that air speed (SPEC §17.24). */
    private static void startSlide(LocalPlayer p, ClientInput in, double landSpeed, double lx, double lz) {
        double hx = p.getX() - p.xo, hz = p.getZ() - p.zo;
        double hs = Math.sqrt(hx * hx + hz * hz);
        float yaw = p.getYRot();
        double dx = -Mth.sin(yaw * Mth.DEG_TO_RAD), dz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        if (landSpeed >= 0) {
            dx = lx;
            dz = lz;
        } else if (hs > 0.08) {
            dx = hx / hs;
            dz = hz / hs;
        }
        boolean fromTac = tac;
        if (tac) endTac(p, false); // the slide's own start tells the server
        slideV0 = landSpeed >= 0 ? BodyRules.landSlideEntry(landSpeed, fromTac) : BodyRules.slideEntry(hs, fromTac);
        sliding = true;
        slideAge = 0;
        slideAir = 0;
        slideStart = ticks;
        slideX = dx;
        slideZ = dz;
        slideFromTac = fromTac;
        stairSliding = false;
        stairTicks = 0;
        // crawl height now, so this very tick already fits under a gap
        p.setForcedPose(Pose.SWIMMING);
        p.setPose(Pose.SWIMMING);
        // no running while sliding (vanilla's sprint footprints would show under our own grit); the run comes back after
        slideWasSprinting = p.isSprinting() || landSpeed >= 0 && airSprint;
        p.setSprinting(false);
        float slideYaw = yawOf(dx, dz);
        send(Move.SLIDE, slideYaw, 0, 0, 0, slideV0, fromTac);
        trace("slide v0 " + String.format("%.2f", slideV0) + (fromTac ? " (tactical)" : ""));
        BodyAnims.play(p.getId(), Move.SLIDE, slideYaw);
        BodyFx.sound(p, BodyContent.SLIDE.get(), 0.85F, fromTac ? 0.92F : 1F);
        in.keyPresses = with(in.keyPresses, in.keyPresses.jump(), false); // no sneaking at an edge mid-slide
        applySlide(p);
    }

    private static void slideTick(LocalPlayer p, ClientInput in, boolean jumpPressed) {
        slideAge++;
        Vec3 v = p.getDeltaMovement();
        double h = Math.sqrt(v.x * v.x + v.z * v.z);
        // something else moved the body (a blow, a blast): it wins and the slide lets go
        if (h > expectH + 0.2 || v.y > 0.3) {
            endSlide(p, true, "pushed");
            return;
        }
        if (!stairSliding && slideAge > 1 && p.horizontalCollision && !p.minorHorizontalCollision) { // a wall stops it, with a thud
            endSlide(p, true, "wall");
            BodyFx.sound(p, BodyContent.IMPACT.get(), 0.35F, 1.45F);
            BodyFx.kick(p, -slideX, -slideZ);
            return;
        }
        // the top of a run (or a slide started on one): the stair slide takes over, its own speed and no time limit
        if (!stairSliding && stairTicks < BodyRules.STAIR_MAX_TICKS
                && BodyRules.stairStart(p.level(), CollisionContext.of(p), STAIRS, p.getX(), p.getY(), p.getZ(), slideX, slideZ, FALL)) {
            stairSliding = true; // the 6 s of run are the whole slide's, landings and all
            stairV = slideSpeed;
            stairFallX = FALL[0];
            stairFallZ = FALL[1];
            trace("stair slide");
        }
        slideAir = p.onGround() || stairSliding ? 0 : slideAir + 1;
        boolean done = !stairSliding && slideAge >= BodyRules.SLIDE_TICKS;
        if (slideAir > 2 || p.isInLiquid() || off(p) || done) {
            endSlide(p, true, done ? "done" : slideAir > 2 ? "airborne" : "off");
            return;
        }
        if (jumpPressed) {
            if (standFits(p)) {
                jumpOut(p, in);
                return;
            }
            in.keyPresses = with(in.keyPresses, false, in.keyPresses.shift()); // under a gap: no jump into the ceiling
        }
        in.keyPresses = with(in.keyPresses, in.keyPresses.jump(), false);
        if (stairSliding) applyStairs(p, in);
        else applySlide(p);
    }

    /**
     * One tick down the run: the way bends onto the fall line, the speed tends to the run's own, the body moves along
     * the way here and vanilla's move takes it down onto the line through the step noses (its collisions, its landing).
     */
    private static void applyStairs(LocalPlayer p, ClientInput in) {
        stairTicks++;
        BodyFp.stairTick(p);
        WAY[0] = slideX;
        WAY[1] = slideZ;
        BodyRules.stairBend(WAY, stairFallX, stairFallZ);
        slideX = WAY[0];
        slideZ = WAY[1];
        stairV = BodyRules.stairSpeed(stairV);
        if (stairTicks > BodyRules.STAIR_MAX_TICKS
                || !BodyRules.stairStep(p.level(), CollisionContext.of(p), STAIRS, p.getX(), p.getY(), p.getZ(), slideX, slideZ, stairV, STEP)) {
            runOut();
            applySlide(p);
            return;
        }
        p.setPos(STEP[0], p.getY(), STEP[2]);
        RunnerMoves.inputAccel(p, in, ACC);
        p.setDeltaMovement(-ACC[0], STEP[1] - p.getY(), -ACC[1]);
        p.resetFallDistance();
        slideSpeed = stairV;
        expectH = 0.15; // only the input's leftover moves the body horizontally after this
        BodyFx.slideSpray(p, slideX, slideZ, (float) (stairV / BodyRules.SLIDE_V0));
        if (STAIRS.last) runOut();
    }

    /** The bottom of a run: the slide's last ticks, its own decay from the speed it has now, carry the body out. */
    private static void runOut() {
        if (!stairSliding) return;
        BodyFp.stairEnd();
        stairSliding = false;
        slideAge = BodyRules.SLIDE_TICKS - BodyRules.STAIR_RUNOUT;
        slideV0 = stairV / (1 - BodyRules.SLIDE_FADE * slideAge / (double) BodyRules.SLIDE_TICKS);
        trace("stair slide run-out after " + stairTicks);
    }

    private static void applySlide(LocalPlayer p) {
        slideSpeed = BodyRules.slideSpeed(slideAge, slideV0);
        Vec3 v = p.getDeltaMovement();
        p.setDeltaMovement(slideX * slideSpeed, v.y, slideZ * slideSpeed);
        // what is left after this tick's ground friction, with room for the player's own input
        expectH = (slideSpeed + 0.15) * 0.6;
        BodyFx.slideSpray(p, slideX, slideZ, (float) (slideSpeed / BodyRules.SLIDE_V0));
    }

    private static boolean standFits(LocalPlayer p) {
        return p.level().noCollision(p, p.getDimensions(Pose.STANDING).makeBoundingBox(p.position()).deflate(1.0E-7));
    }

    /** The slide-cancel: up and out with the slide's speed, plus a little. Vanilla's own jump does the rest. */
    private static void jumpOut(LocalPlayer p, ClientInput in) {
        boolean riding = stairSliding && !p.onGround(); // on a run's noses the feet may be just off the steps
        double speed = (stairSliding ? stairV : BodyRules.slideSpeed(slideAge, slideV0)) + BodyRules.SLIDE_JUMP;
        endSlide(p, true, "jump out");
        p.setPose(Pose.STANDING);
        Vec3 v = p.getDeltaMovement();
        p.setDeltaMovement(slideX * speed, v.y, slideZ * speed);
        if (riding) p.jumpFromGround();
        else if (!in.keyPresses.jump()) in.keyPresses = with(in.keyPresses, true, false);
        slideJumpAt = ticks;
        BodyFp.slideJump();
        trace("slide-cancel jump");
        BodyFx.sound(p, BodyContent.SPRINT.get(), 0.6F, 1.25F);
        BodyFx.kick(p, slideX, slideZ);
    }

    static void endSlide(LocalPlayer p, boolean tellServer, String why) {
        if (!sliding) return;
        trace("slide ends (" + why + ") after " + slideAge);
        sliding = false;
        stairSliding = false;
        BodyFp.stairEnd();
        slideEnd = ticks;
        slideReadyAt = ticks + BodyRules.slideCooldown(Body.runnerRank(p));
        if (p.getForcedPose() == Pose.SWIMMING) p.setForcedPose(null); // vanilla keeps a crawl under a gap
        BodyAnims.stop(p.getId(), Move.SLIDE);
        if (slideWasSprinting && tellServer && p.input.hasForwardImpulse() && !p.isInLiquid()) p.setSprinting(true);
        if (tellServer) sendEnd(Move.SLIDE);
    }

    // ---- mantle -----------------------------------------------------------------------------------------------------

    private static boolean canMantle(LocalPlayer p) {
        return Body.unlocked(p, Move.MANTLE) && p.onGround() && !p.isInLiquid() && !p.isUsingItem() && !p.isBlocking() && !mantling && !sliding
                && !vaulting;
    }

    private static void startMantle(LocalPlayer p, ClientInput in, BodyRules.Ledge l) {
        mantling = true;
        mantleAge = 0;
        mantleStart = ticks;
        msx = p.getX();
        msy = p.getY();
        msz = p.getZ();
        ledge = l;
        in.keyPresses = with(in.keyPresses, false, in.keyPresses.shift());
        jumpSuppressed = true; // holding jump through the climb doesn't hop on top
        float yaw = p.getYRot();
        send(Move.MANTLE, yaw, l.x(), l.y(), l.z(), 0, false);
        trace("mantle onto " + String.format("%.2f", l.height()));
        BodyAnims.play(p.getId(), Move.MANTLE, yaw);
        BodyFx.sound(p, BodyContent.MANTLE.get(), 0.8F, 1F);
        double dx = -Mth.sin(yaw * Mth.DEG_TO_RAD), dz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        if (p.level() instanceof net.minecraft.client.multiplayer.ClientLevel cl) {
            BodyFx.lip(cl, msx + dx * (l.face() + 0.32), l.y(), msz + dz * (l.face() + 0.32), dx, dz);
        }
        mantleTick(p);
    }

    private static void mantleTick(LocalPlayer p) {
        BodyRules.Ledge l = ledge;
        if (l == null) {
            mantling = false;
            return;
        }
        mantleAge++;
        if (mantleAge > BodyRules.MANTLE_TICKS) {
            // over the lip: carry on walking
            mantling = false;
            mantleEnd = ticks;
            trace("mantle done");
            double fx = l.x() - msx, fz = l.z() - msz, f = Math.sqrt(fx * fx + fz * fz);
            if (f > 1e-6) p.setDeltaMovement(fx / f * 0.12, 0, fz / f * 0.12);
            return;
        }
        if (mantleAge > 1) {
            // something stopped the climb: let go and fall back on vanilla
            BodyRules.mantlePath(msx, msy, msz, l, mantleAge - 1, PATH);
            double ex = PATH[0] - p.getX(), ey = PATH[1] - p.getY(), ez = PATH[2] - p.getZ();
            if (ex * ex + ey * ey + ez * ez > 0.5) {
                trace("mantle blocked");
                mantling = false;
                mantleEnd = ticks;
                return;
            }
        }
        BodyRules.mantlePath(msx, msy, msz, l, mantleAge, PATH);
        p.setDeltaMovement(PATH[0] - p.getX(), PATH[1] - p.getY(), PATH[2] - p.getZ());
        p.resetFallDistance();
    }

    // ---- fences, gates and walls (SPEC §17.27) ----------------------------------------------------------------------

    /**
     * The crossing in front of the body, when the keys ask for one: jump pressed facing it (any pace: walking is a hop,
     * a wall walked at is climbed onto), or the free-run held while running at it, roughly where you look. Never while
     * the body stands held on a top it just climbed onto.
     */
    private static VaultRules.@Nullable Vault vaultAhead(LocalPlayer p, boolean jumpPressed) {
        if (perchHold || !Body.unlocked(p, Move.VAULT)) return null;
        Vec3 v = p.getDeltaMovement();
        double h = v.horizontalDistance();
        float yaw = p.getYRot();
        if (!jumpPressed) {
            if (!p.isSprinting() || h < 0.12) return null;
            double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD), fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
            if ((v.x * fx + v.z * fz) / h < 0.8) return null;
        }
        boolean run = FreeRun.held || p.isSprinting();
        // the ground's friction is already off the stored motion: the pace the body actually runs at
        double speed = h / 0.546;
        return VaultRules.find(p.level(), CollisionContext.of(p), p.getX(), p.getY(), p.getZ(), yaw,
                run ? VaultRules.REACH_RUN : VaultRules.REACH_WALK, run, speed);
    }

    private static void startVault(LocalPlayer p, ClientInput in, VaultRules.Vault v) {
        boolean run = FreeRun.held || p.isSprinting(); // as vaultAhead asked: the server asks the same
        in.keyPresses = with(in.keyPresses, false, false);
        float yaw = p.getYRot();
        send(Move.VAULT, yaw, v.endX(), v.floor(), v.endZ(), p.getDeltaMovement().horizontalDistance() / 0.546, run);
        beginVault(p, in, v, yaw);
    }

    /** From a hang on a rail, over it (the hang's climb out; RunnerMoves told the server, a ledge jump). */
    static void overFromHang(LocalPlayer p, VaultRules.Vault v, float yaw) {
        beginVault(p, null, v, yaw);
    }

    private static void beginVault(LocalPlayer p, @Nullable ClientInput in, VaultRules.Vault v, float yaw) {
        vaulting = true;
        vault = v;
        vaultAge = 0;
        vaultStart = ticks;
        vaultPlanted = false;
        vaultWasSprinting = p.isSprinting();
        // the crossing's box: crawl height over the rail, so the eye rises only a little over it
        p.setForcedPose(Pose.SWIMMING);
        p.setPose(Pose.SWIMMING);
        jumpSuppressed = true; // holding jump through it doesn't hop on landing
        trace(String.format("vault (%s) over %.2f, rail %.2f%s, %d ticks, down %.2f", kindName(v.kind()), v.top() - v.y(), v.rail(),
                v.tall() ? " tall" : "", v.ticks(), v.y() - v.floor()));
        BodyAnims.vault(p.getId(), v.kind(), yaw);
        BodyFx.sound(p, BodyContent.VAULT.get(), v.kind() == VaultRules.ONTO ? 0.6F : 0.8F, v.kind() == VaultRules.HOP ? 1.08F : 1F);
        dev.awaken.body.ascent.client.AscentFlow.gain(0.05F);
        vaultTick(p, in);
    }

    static String kindName(int kind) {
        return switch (kind) {
            case VaultRules.VAULT -> "vault";
            case VaultRules.HOP -> "hop";
            case VaultRules.HIGH -> "climb over";
            case VaultRules.ONTO -> "onto the top";
            default -> "over";
        };
    }

    /** One tick of the crossing's path: the body is moved exactly there (the path is clear: the server agrees). */
    private static void vaultTick(LocalPlayer p, @Nullable ClientInput in) {
        VaultRules.Vault v = vault;
        if (v == null) {
            endVault(p, true, "lost");
            return;
        }
        vaultAge++;
        if (vaultAge > 1) {
            // something moved the body off the path (a blow, a push): let go, vanilla takes it from here
            v.at(vaultAge - 1, PATH);
            double ex = PATH[0] - p.getX(), ey = PATH[1] - p.getY(), ez = PATH[2] - p.getZ();
            if (ex * ex + ey * ey + ez * ez > 0.5 * 0.5) {
                endVault(p, true, "blocked");
                return;
            }
        }
        if (vaultAge > v.ticks()) {
            endVault(p, true, "done");
            return;
        }
        // the hand meets the rail as the body comes level with it: a slap, the rail's own knock and a puff off it
        if (!vaultPlanted && vaultAge >= Math.max(1, v.rise() - 1) && p.level() instanceof net.minecraft.client.multiplayer.ClientLevel cl) {
            vaultPlanted = true;
            BodyFx.plant(cl, p, v);
        }
        // past the top: standing again for the way down (the path was checked for the standing box from here)
        if (vaultAge == v.rise() + v.over() + 1 && p.getForcedPose() == Pose.SWIMMING) p.setForcedPose(null);
        v.at(vaultAge, PATH);
        // the last tick sets the feet a hair into the floor: vanilla's move stops them on it, standing
        double y = vaultAge == v.ticks() ? PATH[1] - 0.02 : PATH[1];
        if (in != null) groundAccel(p, in, ACC);
        else ACC[0] = ACC[1] = 0;
        p.setDeltaMovement(PATH[0] - p.getX() - ACC[0], y - p.getY(), PATH[2] - p.getZ() - ACC[1]);
        p.resetFallDistance();
    }

    static void endVault(LocalPlayer p, boolean tellServer, String why) {
        if (!vaulting) return;
        trace("vault ends (" + why + ") after " + vaultAge);
        VaultRules.Vault v = vault;
        vaulting = false;
        vaultEnd = ticks;
        if (p.getForcedPose() == Pose.SWIMMING && !sliding) p.setForcedPose(null); // vanilla stands the body up
        BodyAnims.stop(p.getId(), Move.VAULT);
        if (tellServer) sendEnd(Move.VAULT);
        if (!why.equals("done") || v == null) return;
        if (v.kind() == VaultRules.ONTO) {
            // up on the top: it stops there until forward is let go (never on over into a drop)
            p.setDeltaMovement(0, 0, 0);
            perchHold = true;
            return;
        }
        // down beyond: the run goes on at the pace it came down with
        p.setDeltaMovement(v.dx() * v.vDown(), 0, v.dz() * v.vDown());
        if (vaultWasSprinting && p.input.hasForwardImpulse() && !p.isInLiquid()) p.setSprinting(true);
    }

    /** Standing on a top just climbed onto, forward still held from the climb: the body stays put until it's let go. */
    private static void perch(LocalPlayer p, ClientInput in, Input k, boolean jumpPressed) {
        if (!k.forward() || !p.onGround() || jumpPressed || off(p)) {
            perchHold = false;
            return;
        }
        groundAccel(p, in, ACC);
        Vec3 v = p.getDeltaMovement();
        p.setDeltaMovement(-ACC[0], v.y, -ACC[1]);
    }

    /**
     * Walking the top of a wall or a fence (narrower than the body): drawn back over its middle a little each tick
     * while going along it (within 45°), so a wall-walk holds its line; turning off it, or crouching, is yours.
     */
    private static void balance(LocalPlayer p, ClientInput in, Input k) {
        if (!p.onGround() || k.shift() || sliding || mantling || vaulting) return;
        double frac = p.getY() - Math.floor(p.getY());
        if (Math.abs(frac - 0.5) > 0.02) return; // fence and wall tops stand half a block up
        if (!VaultRules.railUnder(p.level(), CollisionContext.of(p), p.getX(), p.getY(), p.getZ(), RAIL)) return;
        groundAccel(p, in, ACC);
        boolean alongX = RAIL[0] == 0;
        double along = Math.abs(alongX ? ACC[0] : ACC[1]), across = Math.abs(alongX ? ACC[1] : ACC[0]);
        if (across > along && across > 1e-4) return; // stepping off it sideways: your call
        double off = RAIL[1] - (alongX ? p.getZ() : p.getX());
        double pull = Mth.clamp(off * 0.5, -VaultRules.BALANCE, VaultRules.BALANCE);
        Vec3 v = p.getDeltaMovement();
        // the input's sideways part is taken out, the pull put in its place
        if (alongX) p.setDeltaMovement(v.x, v.y, pull - ACC[1]);
        else p.setDeltaMovement(pull - ACC[0], v.y, v.z);
    }

    /** What vanilla's own move adds from the keys this tick (on the ground its walking push, in the air its air control). */
    static void groundAccel(LocalPlayer p, ClientInput in, double[] out) {
        RunnerMoves.inputAccel(p, in, out);
        if (!p.onGround() || out[0] == 0 && out[1] == 0) return;
        // on the ground vanilla pushes with the body's speed instead of the air's (LivingEntity.getFrictionInfluencedSpeed)
        var below = p.getBlockPosBelowThatAffectsMyMovement();
        float f = p.level().getBlockState(below).getFriction(p.level(), below, p);
        f = Mth.clamp(1F - (1F - f) * (float) p.getAttributeValue(Attributes.FRICTION_MODIFIER), 0F, 1F);
        double ground = f > 0.6F ? p.getSpeed() * (0.21600002F / (f * f * f)) : p.getSpeed();
        double air = p.isSprinting() ? 0.026F : 0.02F;
        out[0] *= ground / air;
        out[1] *= ground / air;
    }

    // ---- soft landing -----------------------------------------------------------------------------------------------

    /** The client half of the one fall path: this client's own landing. The server decides the damage. */
    static void fell(Player player, double distance) {
        Minecraft mc = Minecraft.getInstance();
        if (player != mc.player || !(player instanceof LocalPlayer p)) return;
        if (distance < BodyRules.ROLL_MIN_FALL || !Body.unlocked(p, Move.ROLL) || off(p)) return;
        if (ticks - lastGlide < 10 || ticks - lastFly < 140) return; // an elytra, or a power's own landing
        boolean held = p.isSprinting() || p.isShiftKeyDown() || mc.options.keyShift.isDown();
        if (!held) return;
        if (landSlideWanted(p, mc.options.keyShift.isDown())) return; // the slide out of it is this landing's look
        AttributeInstance safe = p.getAttribute(Attributes.SAFE_FALL_DISTANCE);
        double otherSafe = safe == null ? 0 : safe.getValue() - safe.getBaseValue();
        if (BodyRules.roll(distance, otherSafe, 1.0) == null) return;
        rollAt = ticks;
        trace("roll from " + String.format("%.1f", distance));
        BodyAnims.play(p.getId(), Move.ROLL, p.getYRot());
        BodyFx.sound(p, BodyContent.ROLL.get(), 0.9F, 1F);
        BodyFx.rollRing(p);
    }

    // ---- the server's word, and powers ------------------------------------------------------------------------------

    static void refused(int moveId) {
        LocalPlayer p = Minecraft.getInstance().player;
        Move move = Move.byId(moveId);
        if (p == null || move == null) return;
        trace("server ended " + move);
        switch (move) {
            case TACTICAL_SPRINT -> endTac(p, false);
            case SLIDE -> {
                if (sliding) {
                    endSlide(p, false, "refused");
                    Vec3 v = p.getDeltaMovement();
                    p.setDeltaMovement(0, v.y, 0);
                }
            }
            case MANTLE -> {
                mantling = false;
                mantleEnd = ticks;
            }
            case VAULT -> endVault(p, false, "refused");
            case ROLL -> {
            }
            default -> RunnerMoves.refused(move);
        }
    }

    /**
     * A power key sent a cast. A cast ends the tactical sprint (the server ends its own). A mid-slide cast keeps the
     * slide going, unless the power moved the body: then its motion wins, along the slide's way when it points
     * roughly that way, and a Velocity power's goes further.
     */
    static void castStarting(Player player, String powerId, String family, boolean motionPredicted) {
        if (!(player instanceof LocalPlayer p) || p != Minecraft.getInstance().player) return;
        if (tac) endTac(p, false);
        if (!sliding || !motionPredicted) return;
        Vec3 v = p.getDeltaMovement();
        double h = Math.sqrt(v.x * v.x + v.z * v.z);
        if (h > 1e-3 && (v.x * slideX + v.z * slideZ) / h > 0.5) {
            double stretch = "VELOCITY".equals(family) ? MovementHooks.DASH_OUT_STRETCH : 1.0;
            p.setDeltaMovement(slideX * h * stretch, v.y, slideZ * h * stretch);
        }
        endSlide(p, true, "power " + powerId);
    }

    static void stopAll(LocalPlayer p) {
        endTac(p, true);
        endSlide(p, true, "stop");
        endVault(p, true, "stop");
        mantling = false;
        perchHold = false;
    }

    /** The player entity was replaced (respawn, dimension change): moves stop, cooldowns and the clock stay. */
    static void forgetMoves() {
        RunnerMoves.reset();
        tac = sliding = mantling = stairSliding = vaulting = perchHold = false;
        jumpSuppressed = false;
        if (tacEnd < tacStart) tacEnd = ticks;
        if (slideEnd < slideStart) slideEnd = ticks;
    }

    /** Forget everything (logout, a new world). */
    static void reset() {
        RunnerMoves.reset();
        tac = sliding = mantling = stairSliding = vaulting = perchHold = false;
        jumpSuppressed = false;
        tacReadyAt = slideReadyAt = 0;
        rollAt = tacStart = tacEnd = slideStart = slideEnd = mantleStart = mantleEnd = slideJumpAt = vaultStart = vaultEnd = -100;
        ticks = 0;
    }

    static @Nullable Vec3 slideDirection() {
        return sliding ? new Vec3(slideX, 0, slideZ) : null;
    }
}
