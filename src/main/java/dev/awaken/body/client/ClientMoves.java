package dev.awaken.body.client;

import dev.awaken.body.Body;
import dev.awaken.body.BodyContent;
import dev.awaken.body.BodyRules;
import dev.awaken.body.Move;
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
    // mantle
    static boolean mantling;
    static int mantleStart = -100, mantleEnd = -100, mantleAge;
    private static double msx, msy, msz;
    private static BodyRules.@Nullable Ledge ledge;
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
        // the Runner's Kit (SPEC §16.3): every move from the first second; without it, nothing until the awakening
        boolean kit = dev.awaken.body.BodyConfig.runnersKit();
        boolean awakened = kit || Progression.profile(p).awakened();
        // the one-button free-run (SPEC §17.14): sprint held with forward reads the ground ahead
        FreeRun.begin(p, k);
        FreeRun.climb(p);
        if (!awakened && (tac || sliding || mantling)) stopAll(p);
        if (jumpSuppressed) {
            if (jump) in.keyPresses = with(in.keyPresses, false, in.keyPresses.shift());
            else jumpSuppressed = false;
        }
        if (mantling) {
            mantleTick(p);
            return;
        }
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
                    BodyRules.Ledge l = BodyRules.findLedge(p.level(), CollisionContext.of(p), p.getX(), p.getY(), p.getZ(), p.getYRot(), BodyRules.MANTLE_REACH);
                    if (l != null) {
                        startMantle(p, in, l);
                        return;
                    }
                }
                if (shiftPressed && canSlide(p)) {
                    startSlide(p, in);
                    return;
                }
                if (kit && FreeRun.ground(p, p.level(), CollisionContext.of(p), ticks)) return;
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

    private static void startSlide(LocalPlayer p, ClientInput in) {
        double hx = p.getX() - p.xo, hz = p.getZ() - p.zo;
        double hs = Math.sqrt(hx * hx + hz * hz);
        float yaw = p.getYRot();
        double dx = -Mth.sin(yaw * Mth.DEG_TO_RAD), dz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        if (hs > 0.08) {
            dx = hx / hs;
            dz = hz / hs;
        }
        boolean fromTac = tac;
        if (tac) endTac(p, false); // the slide's own start tells the server
        slideV0 = BodyRules.slideEntry(hs, fromTac);
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
        slideWasSprinting = p.isSprinting();
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
        return Body.unlocked(p, Move.MANTLE) && p.onGround() && !p.isInLiquid() && !p.isUsingItem() && !p.isBlocking() && !mantling && !sliding;
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

    // ---- soft landing -----------------------------------------------------------------------------------------------

    /** The client half of the one fall path: this client's own landing. The server decides the damage. */
    static void fell(Player player, double distance) {
        Minecraft mc = Minecraft.getInstance();
        if (player != mc.player || !(player instanceof LocalPlayer p)) return;
        if (distance < BodyRules.ROLL_MIN_FALL || !Body.unlocked(p, Move.ROLL) || off(p)) return;
        if (ticks - lastGlide < 10 || ticks - lastFly < 140) return; // an elytra, or a power's own landing
        boolean held = p.isSprinting() || p.isShiftKeyDown() || mc.options.keyShift.isDown();
        if (!held) return;
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
        mantling = false;
    }

    /** The player entity was replaced (respawn, dimension change): moves stop, cooldowns and the clock stay. */
    static void forgetMoves() {
        RunnerMoves.reset();
        tac = sliding = mantling = stairSliding = false;
        jumpSuppressed = false;
        if (tacEnd < tacStart) tacEnd = ticks;
        if (slideEnd < slideStart) slideEnd = ticks;
    }

    /** Forget everything (logout, a new world). */
    static void reset() {
        RunnerMoves.reset();
        tac = sliding = mantling = stairSliding = false;
        jumpSuppressed = false;
        tacReadyAt = slideReadyAt = 0;
        rollAt = tacStart = tacEnd = slideStart = slideEnd = mantleStart = mantleEnd = slideJumpAt = -100;
        ticks = 0;
    }

    static @Nullable Vec3 slideDirection() {
        return sliding ? new Vec3(slideX, 0, slideZ) : null;
    }
}
