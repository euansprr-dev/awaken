package dev.awaken.mobs.client.life;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.mobs.client.life.LifeBones.Role;
import dev.awaken.mobs.client.life.LifeProfile.Idle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * One creature's life (SPEC §23): the state the life layer keeps between frames, made once when it is first drawn and
 * then only refilled (no allocation per frame). {@link #tick} runs once a frame from the renderer's extraction: the
 * creature's motion, breathing, blinks, where its head is in its slow follow of what it looks at, its idle fidgets,
 * a hurt's stagger, a wind-up's tremble, a roar, and a boss's weighty fall. The bone springs integrate in
 * {@link LifeRig#apply} the first time the model poses this frame, because they follow the pose its clips just set.
 *
 * <p>Two creatures of a herd never move in step: every timer, the breath and the idle clip start from the creature's
 * own seed, and each breathes at its own slightly different rate.
 */
public final class Life {
    // enum values() clones its array on every call: these are read per creature per frame
    private static final Idle[] IDLES = Idle.values();
    private static final Role[] ROLES = Role.values();
    // ---- what the renderer tells it each frame ----------------------------------------------------------------------
    /** 0..1: 1 the moment it is struck, falling to 0. */
    public float hurt;
    /** The entity this life animates (its renderer sets it), so a blow's way and weight reach its stagger. */
    public int entity = Integer.MIN_VALUE;
    /** 0..1 how far a wind-up has come, or -1 when none plays. */
    public float windup = -1;
    /** A roar or a provocation is playing. */
    public boolean roar;
    /** A clip owns the body (a strike, a held pose, a cue): no idle fidgets start. */
    public boolean busy;
    /** Seconds since it began to die, -1 alive; with {@link #deathLength} 0 the fall is the root's (a boss's topple). */
    public float dying = -1;
    public float deathLength;
    /** 0..1 how much it walks (the gait clip's weight). */
    public float walk;
    /** Blocks a stride covers (the gait clip's), so the step's dip lands with its feet; 0: from the vanilla walk. */
    public float stride;
    /** 0..1 how much the head follows the look the AI asks for (a citizen only looks at you up close). */
    public float lookWeight = 1;
    public boolean swimming, airborne;
    /** Bodies without a roar clip rear up when they turn on you (a Gate Lord's provocation). */
    public boolean aggressive;
    /** Root-level weighty fall and roar for a boss on any body. */
    public boolean boss;
    /** The body has its own roar clip: the layer only adds the breath and the shudder, not the rear. */
    public boolean roarClip;
    /** On its feet on the ground (not falling, leaping or climbing): the feet may plant (SPEC §23, "Motion"). */
    public boolean grounded = true;
    /** A pose's clip owns the legs (hanging, resting, beached): the feet let go. */
    public boolean legsFree;
    /** It creeps up on a target (a hunter's stalk): a still body holds its breath and its fidgets, and sinks low. */
    public boolean stalking;
    /** A held pose rears it on its hind feet (a wickling reaching for its vine): the front pairs let go to the clip. */
    public boolean rearing;
    /** 0..1 how much it runs (the run clip's weight), for the feet's gait. */
    public float gaitRun;
    /** The renderer's own scale on top of the entity's (a cave spider's 0.7), so the feet land where the body is drawn. */
    public float bodyScale = 1;

    // ---- what the layer works out ----------------------------------------------------------------------------------
    final LifeRig rig;
    final LifeProfile p;
    /** The feet, for a body whose motion profile has a gait; the motion profile's body features. */
    final @org.jspecify.annotations.Nullable Legs legs;
    final MotionProfile.Body motion;
    int frame, springFrame = -1;
    private float lastAge = Float.NaN;
    float dt, t;
    private long seed;
    private boolean far;
    // motion (world, blocks and seconds), and the body's acceleration in model space (px/s^2)
    private double lx, ly, lz;
    private float vx, vy, vz, wax, way, waz, lastBody, yawRate, yawAccel;
    float amx, amy, amz, speed;
    // the head (radians), following its target with the temperament's speed
    float headYaw, headPitch, headYawV, headPitchV, targetYaw, targetPitch, eyeYaw, eyePitch;
    // breath, exertion, idle clip time
    float breath, depth, exertion, idleTime;
    private float breathPhase, rateJitter, gaitPhase, tremPhase;
    // blinking
    float closed;
    private float nextBlink, blinkAt = -10, yawnShut;
    // stagger (model space: pitch + = top forward, roll + = top toward +x), tension, tremble, roar
    private float stX, stZ, stXv, stZv, lastHurt, tension, roarAt = -10;
    private boolean wasRoar, wasAggressive;
    // the outputs apply() and rootPose() add
    float bodyPitch, bodyRoll, dip, headNod, headRoll, neckPitch, neckDrop, jawOpen, scratch;
    int scratchSide;
    // the motion profile's: the gait's roll (the head holds level against it), the body's turn toward its look, a
    // stalker's crouch (px), a landing's squash (px) and how fast it springs back, its weight drifting over its feet (px)
    float swayRoll, twist, creep, land, shiftX, shiftZ;
    private float landV, airFall, shiftT, shiftW;
    private boolean wasAirborne;
    // flight and swimming (SPEC §23, "Flight"): how much it is under way, its path's climb, its cruise (0..1), the share
    // of its stroke it keeps, where its wings sit above their mean (read from the pose), the beat (cycles), what the body
    // and spine do with them; each wing's and spine link's mean pose (the stroke is measured and scaled about it)
    final MotionProfile.@org.jspecify.annotations.Nullable Fly fly;
    float flyW, climb, cruise, keep = 1, wingE, heave, beat, flyPitch, flyRoll, bend;
    int flyFrame = -1;
    final float[] wingMean, spineMean;
    // idle fidgets
    private int idle = -1, idleSide;
    private float idleAt, idleLen, nextIdle, idleEnv, wetUntil = -10;
    private boolean wasInWater;
    private float fYaw, fPitch;
    private int flick = -1;
    private float flickX, flickY, flickZ;
    private float shakeAccel;
    // the death: a root topple's angle and how fast it falls (for the springs' flop), and the thud when it lands
    private float topple, toppleV, toppleA, deathSide = 1, thud, thudV, lastDying = -1;
    private boolean landed;
    // springs
    final float[] ax, ay, az, avx, avy, avz, aacc;
    private final float[] drv, drvV, drvAccs;
    private boolean drvInit;
    private float bodyLy, bodyLv, kick, thump;
    private float kickX, kickZ;

    Life(LifeRig rig, int seed) {
        this.rig = rig;
        this.p = rig.p;
        this.seed = 0x9E3779B97F4A7C15L ^ (seed * 0xBF58476D1CE4E5B9L);
        legs = rig.legs != null ? new Legs(rig.legs.rig, seed) : null;
        motion = rig.motion != null ? rig.motion.body() : MotionProfile.Body.NONE;
        fly = rig.fly;
        wingMean = new float[rig.flyWings.length];
        spineMean = new float[rig.flySpine.length * 3];
        int n = rig.n;
        ax = new float[n];
        ay = new float[n];
        az = new float[n];
        avx = new float[n];
        avy = new float[n];
        avz = new float[n];
        aacc = new float[n * 3];
        drv = new float[rig.drivers.length * 3];
        drvV = new float[rig.drivers.length * 3];
        drvAccs = new float[rig.drivers.length * 3];
        rateJitter = 0.9F + 0.2F * rand();
        breathPhase = Mth.TWO_PI * rand();
        idleTime = 37.0F * rand();
        nextIdle = p.fidgetMax() * rand();
        nextBlink = p.blinkMin() + (p.blinkMax() - p.blinkMin()) * rand();
        deathSide = rand() < 0.5F ? -1 : 1;
        tremPhase = Mth.TWO_PI * rand();
    }

    private float rand() {
        seed ^= seed << 13;
        seed ^= seed >>> 7;
        seed ^= seed << 17;
        return (seed >>> 40) / (float) (1L << 24);
    }

    /** 0..1: the eyes are shut (a blink, a yawn, death), for bodies that blink by texture. */
    public float closed() {
        return closed;
    }

    /** Seconds of the creature's own idle clock (starts at its seed; runs faster when it's out of breath). */
    public float idleTime() {
        return idleTime;
    }

    public boolean far() {
        return far;
    }

    /** 0..1: how much the motion layer's planted feet own the legs (0 with no gait): a body's own walk swing gives way. */
    public float feet() {
        return legs != null ? legs.weight : 0.0F;
    }

    /** The beat's pace for a body that beats its own wings or tail (1 when it has no flight): see {@link Flight#pace}. */
    public float pace() {
        return fly != null ? Flight.pace(climb, cruise) : 1.0F;
    }

    /** The head's look this frame, in degrees (written over the state's look so every model's look code uses it). */
    public float headYawDeg() {
        return headYaw * Mth.RAD_TO_DEG;
    }

    private static boolean reduced;
    private static long reducedAt;

    private static boolean reduced() {
        long now = System.nanoTime();
        if (now - reducedAt > 1_000_000_000L) {
            reducedAt = now;
            reduced = dev.awaken.body.client.feel.FeelConfig.reduced();
        }
        return reduced;
    }

    // ---- once a frame -----------------------------------------------------------------------------------------------

    /**
     * Steps the creature's life to this frame and writes its head's look into the state (s.yRot, s.xRot), so the
     * model's own look code turns the head the slow way. Call from extractRenderState, after the state is filled.
     */
    public void tick(LivingEntityRenderState s, boolean steerHead) {
        float age = s.ageInTicks;
        if (Float.isNaN(lastAge)) {
            lastAge = age;
            lx = s.x;
            ly = s.y;
            lz = s.z;
            lastBody = s.bodyRot;
            headYaw = targetYaw = Mth.clamp(Mth.wrapDegrees(s.yRot) * Mth.DEG_TO_RAD, -p.neck(), p.neck());
            headPitch = targetPitch = Mth.clamp(s.xRot, -75, 75) * Mth.DEG_TO_RAD;
        }
        float step = (age - lastAge) / 20.0F;
        if (step <= 0) { // the same frame drawn again, or the game paused: nothing moves
            if (steerHead) writeHead(s);
            return;
        }
        lastAge = age;
        boolean jump = step > 0.25F;
        dt = Math.min(step, 0.1F);
        t += dt;
        frame++;
        far = s.distanceToCameraSq > 32 * 32;
        boolean reduce = reduced();
        motion(s, jump);
        // exertion: builds while it runs, falls slowly after; a blow takes its breath too
        float run = Mth.clamp((speed - 2.5F) / 4.0F, 0.0F, 1.0F);
        exertion += (run - exertion) * Math.min(1.0F, dt / (run > exertion ? 2.5F : 9.0F));
        boolean struck = hurt > lastHurt + 0.3F;
        lastHurt = hurt;
        if (struck) exertion = Math.min(1.2F, exertion + 0.35F);
        boolean dead = dying >= 0;
        // breathing: faster and deeper out of breath; it stops a moment after the fall ends
        float rate = p.breath() * rateJitter * (1.0F + 1.4F * exertion);
        breathPhase += Mth.TWO_PI * rate * dt;
        if (breathPhase > Mth.TWO_PI * 64) breathPhase -= Mth.TWO_PI * 64;
        breath = Mth.sin(breathPhase - 0.35F * Mth.sin(breathPhase)); // a quicker in-breath, a slower out
        depth = p.breathDepth() * (1.0F + 1.2F * exertion);
        boolean hold = motion.still() && stalking && dying < 0;
        if (hold) depth *= 0.25F; // it holds its breath while it creeps
        if (dead) {
            float end = deathLength > 0 ? deathLength : 1.0F;
            float after = dying - end * 0.8F;
            if (after > 0) depth *= Math.max(0.0F, 1.0F - after / 0.9F); // one last breath, then stillness
        }
        idleTime += dt * (1.0F + 0.8F * Math.min(1.0F, exertion));
        if (idleTime > 3600) idleTime -= 3600;
        blink(dead);
        stagger(s, struck, reduce);
        // a wind-up winds the body tight; a tremble builds to the strike (the tell)
        float want = windup >= 0 ? 0.35F + 0.65F * windup : 0.0F;
        tension += (want - tension) * Math.min(1.0F, dt / (want > tension ? 0.1F : 0.3F));
        boolean roarNow = roar || (aggressive && !wasAggressive && boss);
        if (roarNow && !wasRoar) roarAt = t;
        wasRoar = roarNow;
        wasAggressive = aggressive;
        float roarT = t - roarAt;
        idles(s, dead, reduce);
        // the outputs
        float moving = walk;
        if (stride > 0) gaitPhase += speed * dt / stride;
        if (gaitPhase > 1024) gaitPhase -= 1024;
        float phase = stride > 0 ? gaitPhase : s.walkAnimationPos * 0.1591F; // a vanilla walk cycle is 2*pi in walkAnimationPos
        float stepWave = 0.5F - 0.5F * Mth.cos(Mth.TWO_PI * 2.0F * phase);
        // the fall lands with a thud: one bounce on its weight, and everything loose flops (see integrate)
        float impact = deathLength > 0 ? rig.impact : 0.7F;
        if (dead && impact > 0 && lastDying < impact && dying >= impact && !landed) {
            landed = true;
            thudV = -(14.0F + 4.0F * Math.min(3.0F, p.mass()));
            kick = 1;
            kickX = kickZ = 0;
            thump = 1;
        }
        if (!dead) landed = false;
        lastDying = dying;
        float tw = Mth.TWO_PI * 3.2F;
        thudV += (-tw * tw * thud - 2 * 0.35F * tw * thudV) * dt;
        thud = Mth.clamp(thud + thudV * dt, -3.0F, 3.0F);
        // a wind-up gathers the body low; a step dips it, heavier for heavier creatures
        motionBody(dead);
        dip = (dead ? 0.0F : p.bob() * moving * stepWave) + idleDip + tension * 0.7F + thud + land;
        float sway = p.sway() * Mth.DEG_TO_RAD * moving * Mth.sin(Mth.TWO_PI * phase);
        float lean = Mth.clamp(-yawRate * Math.min(1.0F, speed / 4.0F) * 0.09F * p.lean(), -0.22F, 0.22F);
        swayRoll = sway + lean;
        float trem = tension * tension * (reduce ? 0.5F : 1.0F) * 0.03F * Mth.sin(t * 107.0F + tremPhase);
        float roarShake = roarT >= 0.3F && roarT < 1.4F ? 0.035F * Mth.sin(roarT * 44.0F) * (1.0F - (roarT - 0.3F) / 1.1F) : 0.0F;
        bodyPitch = stX + idlePitch - tension * 0.04F + (roarClip ? 0.0F : roarRear(roarT)) + trem * 0.6F;
        bodyRoll = stZ + sway + lean + idleRoll + trem;
        headRoll = idleHeadRoll + roarShake;
        if (fly != null) flight(fly, dead);
        headNod = idleNod;
        neckPitch = idleNeck;
        neckDrop = idleDrop;
        float roarJaw = rig.jaw != null && !roarClip && roarT >= 0.25F && roarT < 1.5F ? 0.45F * envelope(roarT - 0.25F, 1.25F, 0.15F, 0.3F) : 0.0F;
        jawOpen = idleJaw + roarJaw + (dead ? 0.25F * Math.min(1.0F, dying / 0.6F) : 0.0F);
        if (roarT >= 0 && roarT < 0.35F) depth += 1.2F * (roarT / 0.35F); // it fills its chest before the roar
        // the head: toward what it looks at, plus its fidget's look, leading into a turn
        // never further than its neck reaches (a look over its shoulder would wring it round); a look that passes behind
        // it holds the side the head already turned to, so the face never swings across its back
        float neck = p.neck(), rawYaw = Mth.wrapDegrees(s.yRot) * Mth.DEG_TO_RAD, rawPitch = Mth.clamp(s.xRot, -75, 75) * Mth.DEG_TO_RAD;
        if (Math.abs(rawYaw) > neck) rawYaw = (Math.abs(rawYaw) > 2.0F && Math.abs(headYaw) > 0.1F ? Math.signum(headYaw) : Math.signum(rawYaw)) * neck;
        float into = moving > 0.2F ? Mth.clamp(yawRate * 0.16F, -0.45F, 0.45F) : 0.0F;
        targetYaw = Mth.clamp(rawYaw * lookWeight + fYaw + into, -neck, neck);
        targetPitch = rawPitch * lookWeight + fPitch;
        if (dead) {
            targetYaw = headYaw;
            targetPitch = headPitch;
        }
        float w = p.headSpeed(), z = p.headDamping();
        int sub = Math.max(1, Mth.ceil(dt * w / 0.25F));
        float h = dt / sub;
        for (int i = 0; i < sub; i++) {
            headYawV += (w * w * (targetYaw - headYaw) - 2 * z * w * headYawV) * h;
            headPitchV += (w * w * (targetPitch - headPitch) - 2 * z * w * headPitchV) * h;
            headYaw += headYawV * h;
            headPitch += headPitchV * h;
        }
        if (Math.abs(headYaw) > neck) { // the spring's overshoot stops at the neck's reach too
            headYaw = Math.copySign(neck, headYaw);
            headYawV = 0;
        }
        headPitch = Mth.clamp(headPitch, -1.3F, 1.3F);
        float lead = far ? 0.0F : p.eyeLead();
        eyeYaw = Mth.clamp(targetYaw - headYaw, -lead, lead);
        eyePitch = Mth.clamp(targetPitch - headPitch, -lead * 0.6F, lead * 0.6F);
        if (steerHead) writeHead(s);
        twist = motion.twist() > 0 && !dead ? Mth.clamp(headYaw * motion.twist(), -0.35F, 0.35F) : 0.0F;
        if (legs != null) {
            MotionProfile.Gait g = legs.rig.gait;
            float st = g.strideWalk() > 0 ? Mth.lerp(gaitRun, g.strideWalk(), g.strideRun() > 0 ? g.strideRun() : g.strideWalk())
                    : stride > 0 ? stride : 1.0F;
            boolean calmFeet = !busy && !far && tension < 0.05F && !hold;
            // a wind-up, a strike, a roar or a still threat lifts the front pair: their clips rear and strike with them
            boolean front = windup >= 0 || roar || rearing || (busy && hurt <= 0.01F) || (aggressive && walk < 0.1F);
            float rear = front && !dead ? g.rear() : 0.0F, k = Math.min(1.0F, dt / 0.12F);
            legs.rear += (rear - legs.rear) * k;
            // a roar lifts one pair more
            legs.rear2 += ((roar && !dead ? g.rear() : 0.0F) - legs.rear2) * k;
            legs.face = rig.body != null ? twist * Mth.RAD_TO_DEG : 0.0F; // the body bone turns with its look, the hips with it
            legs.update(s, dt, bodyScale, grounded && !legsFree && !dead && !swimming, st, gaitRun, calmFeet);
        }
        // a boss on a body with no fall clip topples its whole weight (root level)
        deathFall(dead);
    }

    /**
     * The motion profile's body (SPEC §23, "Motion"): a landing squashes it by how far it fell and it springs back up; a
     * stalker sinks low on its legs.
     */
    private void motionBody(boolean dead) {
        if (motion.land() > 0) {
            if (!grounded) airFall = Math.min(airFall, vy);
            else if (wasAirborne) {
                landV += Math.min(60.0F, -airFall * 7.0F) * motion.land();
                airFall = 0;
            }
            wasAirborne = !grounded;
            float w = Mth.TWO_PI * 3.0F;
            landV += (-w * w * land - 2 * 0.45F * w * landV) * dt;
            land = Mth.clamp(land + landV * dt, -2.0F, 6.0F);
        }
        float want = stalking && !dead ? motion.creep() : 0.0F;
        creep += (want - creep) * Math.min(1.0F, dt / 0.4F);
        // standing, its weight drifts over its planted feet on two slow, unrelated beats (its own phase), so the legs
        // keep giving and taking; a held breath stills it, walking hands the weight to the gait
        if (motion.shift() > 0) {
            boolean calm = !dead && grounded && !legsFree && walk < 0.1F && !(motion.still() && stalking);
            shiftW += ((calm ? 1.0F : 0.0F) - shiftW) * Math.min(1.0F, dt / 0.6F);
            shiftT += dt;
            float ph = (seed & 1023) * 0.0061F;
            shiftX = motion.shift() * shiftW * Mth.sin(shiftT * 1.31F + ph) * (0.7F + 0.3F * Mth.sin(shiftT * 0.23F + ph * 2));
            shiftZ = motion.shift() * shiftW * 0.6F * Mth.sin(shiftT * 0.83F + ph * 1.7F);
        }
    }

    /**
     * Flight and swimming (SPEC §23, "Flight"): under way, the body noses into a cruise and along its path's climb or
     * dive, banks into turns and rides its wings' stroke (it rises as they sweep down); a flier beats and glides by
     * turns at a cruise and glides down a dive; the spine curves into a turn. {@link LifeRig#apply} beats, scales and
     * reads the wings and spine.
     */
    private void flight(MotionProfile.Fly f, boolean dead) {
        boolean on = !dead && (f.always() || airborne || swimming);
        flyW += ((on ? 1.0F : 0.0F) - flyW) * Math.min(1.0F, dt / 0.3F);
        climb += (Flight.climb(speed, vy) - climb) * Math.min(1.0F, dt / 0.25F);
        float c = Mth.clamp(Mth.sqrt(speed * speed + vy * vy) / f.fast(), 0.0F, 1.0F);
        cruise += (c - cruise) * Math.min(1.0F, dt / 0.3F);
        // beat and glide by turns, on a slow wave of its own (about six seconds)
        float lull = 0.5F + 0.5F * Mth.sin(t * 1.1F + (seed & 255) * 0.0245F);
        keep += (Flight.keep(climb, cruise, f.glide(), lull) - keep) * Math.min(1.0F, dt / 0.35F);
        beat += dt * f.flapHz() * Flight.pace(climb, cruise);
        if (beat > 1024) beat -= 1024;
        flyPitch = Flight.pitch(climb, cruise, f.climb(), f.tilt()) * flyW;
        flyRoll = Flight.bank(yawRate, cruise, f.bank()) * flyW;
        heave += (Flight.heave(wingE, f.heave()) * flyW - heave) * Math.min(1.0F, dt / 0.05F);
        bend += (Flight.bend(yawRate, f.bend()) * flyW - bend) * Math.min(1.0F, dt / 0.2F);
        bodyPitch += flyPitch;
        bodyRoll += flyRoll;
        dip += heave;
    }

    private void writeHead(LivingEntityRenderState s) {
        s.yRot = headYaw * Mth.RAD_TO_DEG;
        s.xRot = headPitch * Mth.RAD_TO_DEG;
    }

    private void motion(LivingEntityRenderState s, boolean jump) {
        double mx = s.x - lx, my = s.y - ly, mz = s.z - lz;
        lx = s.x;
        ly = s.y;
        lz = s.z;
        float ivx = (float) (mx / dt), ivy = (float) (my / dt), ivz = (float) (mz / dt);
        float dyaw = Mth.wrapDegrees(s.bodyRot - lastBody) * Mth.DEG_TO_RAD;
        lastBody = s.bodyRot;
        if (jump || ivx * ivx + ivy * ivy + ivz * ivz > 40 * 40) { // a teleport or a long gap, not motion
            ivx = vx;
            ivy = vy;
            ivz = vz;
            dyaw = 0;
        }
        float a = dt / (0.06F + dt), a2 = dt / (0.08F + dt);
        float nvx = vx + (ivx - vx) * a, nvy = vy + (ivy - vy) * a, nvz = vz + (ivz - vz) * a;
        wax += ((nvx - vx) / dt - wax) * a2;
        way += ((nvy - vy) / dt - way) * a2;
        waz += ((nvz - vz) / dt - waz) * a2;
        vx = nvx;
        vy = nvy;
        vz = nvz;
        float nyr = yawRate + (dyaw / dt - yawRate) * a;
        yawAccel += ((nyr - yawRate) / dt - yawAccel) * a2;
        yawRate = nyr;
        speed = Mth.sqrt(vx * vx + vz * vz);
        // into model space (+x left, +y down, -z forward), in px/s^2
        float th = (180.0F - s.bodyRot) * Mth.DEG_TO_RAD, c = Mth.cos(th), sn = Mth.sin(th);
        float fx = wax * c - waz * sn, fz = wax * sn + waz * c;
        amx = Mth.clamp(-fx * 16.0F, -4000, 4000);
        amy = Mth.clamp(-way * 16.0F, -4000, 4000);
        amz = Mth.clamp(fz * 16.0F, -4000, 4000);
    }

    private void blink(boolean dead) {
        float shut = 0;
        if (p.blinks() && !far) {
            if (t >= nextBlink) {
                blinkAt = t;
                boolean twice = rand() < 0.15F;
                // a calm creature blinks less often; one out of breath or just struck blinks more
                float calm = 1.0F - 0.4F * Math.min(1.0F, exertion);
                nextBlink = t + (twice ? 0.24F : (p.blinkMin() + (p.blinkMax() - p.blinkMin()) * rand()) * calm);
            }
            float u = (t - blinkAt) / 0.16F;
            if (u >= 0 && u < 1) shut = u < 0.3F ? u / 0.3F : u < 0.55F ? 1.0F : 1.0F - (u - 0.55F) / 0.45F;
        }
        if (dead) {
            float end = deathLength > 0 ? deathLength : 1.0F;
            shut = Math.max(shut, Mth.clamp((dying - end * 0.55F) / 0.4F, 0.0F, 1.0F)); // the eyes close as it settles
        }
        closed = Math.max(shut, yawnShut);
    }

    /**
     * A blow knocks the body away from the camera (it reels from whoever struck it) and it sways back on its weight:
     * a damped spring per axis, kicked once. Heavy beasts reel less and slower; small creatures snap.
     */
    private static final float[] BLOW = new float[2];

    private void stagger(LivingEntityRenderState s, boolean struck, boolean reduce) {
        if (struck) {
            Vec3 cam = Minecraft.getInstance().gameRenderer.mainCamera().position();
            float wx = (float) (s.x - cam.x), wz = (float) (s.z - cam.z);
            // a blade's blow (Arms): the body folds along the swing and by its weight; anything else away from the eye
            float weight = dev.awaken.core.api.CombatFeedback.blowOn(entity, BLOW);
            if (weight > 0) {
                wx = BLOW[0];
                wz = BLOW[1];
            }
            float l = Mth.sqrt(wx * wx + wz * wz);
            if (l > 0.01F) {
                wx /= l;
                wz /= l;
            } else {
                wz = 1;
            }
            float th = (180.0F - s.bodyRot) * Mth.DEG_TO_RAD, c = Mth.cos(th), sn = Mth.sin(th);
            float px = -(wx * c - wz * sn), pz = wx * sn + wz * c; // the push in model space
            float v = 2.6F * p.stagger() / Mth.sqrt(Math.max(0.3F, p.mass())) * (reduce ? 0.5F : 1.0F) * (weight > 0 ? 0.6F + 0.4F * weight : 1F);
            stXv += -pz * v;
            stZv += px * v;
            headPitchV -= 2.2F * p.stagger();
            headYawV += (rand() - 0.5F) * 4.0F * p.stagger();
            kick = 1;
            kickX = px;
            kickZ = pz;
        }
        float w = Mth.TWO_PI * 1.9F / Mth.sqrt(Math.max(0.3F, p.mass())), z = 0.32F;
        stXv += (-w * w * stX - 2 * z * w * stXv) * dt;
        stZv += (-w * w * stZ - 2 * z * w * stZv) * dt;
        stX = Mth.clamp(stX + stXv * dt, -0.5F, 0.5F);
        stZ = Mth.clamp(stZ + stZv * dt, -0.5F, 0.5F);
    }

    private static float roarRear(float rt) {
        // a provocation: it rears back, holds, and comes down (the bodies with a roar clip have their own)
        return rt >= 0 && rt < 1.7F ? -0.12F * envelope(rt, 1.7F, 0.3F, 0.5F) : 0.0F;
    }

    /** 0..1..0 over len seconds: smooth rise over in, hold, smooth fall over out. */
    static float envelope(float u, float len, float in, float out) {
        if (u <= 0 || u >= len) return 0.0F;
        if (u < in) return Mth.smoothstep(u / in);
        if (u > len - out) return Mth.smoothstep((len - u) / out);
        return 1.0F;
    }

    // ---- idle fidgets -------------------------------------------------------------------------------------------------

    private float idlePitch, idleRoll, idleDip, idleNod, idleHeadRoll, idleNeck, idleDrop, idleJaw;

    private void idles(LivingEntityRenderState s, boolean dead, boolean reduce) {
        if (s.isInWater) wasInWater = true;
        else if (wasInWater) {
            wasInWater = false;
            wetUntil = t + 6.0F; // fresh out of the water: it'll shake itself dry when it stops
        }
        boolean calm = !far && !dead && !busy && walk < 0.08F && tension < 0.05F && t - roarAt > 2.0F && !swimming && !airborne
                && !(motion.still() && stalking);
        if (idle < 0) {
            if (calm && !reduce && t >= nextIdle) start(pick());
            else if (!calm) nextIdle = Math.max(nextIdle, t + 1.5F + rand()); // settle a moment before fidgeting again
        } else if (!calm && idleEnv < 0.02F) {
            // a fidget it walked out of is over: a pause later in the walk mustn't drop it back into the middle of a graze
            idle = -1;
            nextIdle = Math.max(nextIdle, t + 1.5F + rand());
        }
        float target = 0;
        if (idle >= 0) {
            float u = t - idleAt;
            if (u >= idleLen) {
                idle = -1;
                nextIdle = t + p.fidgetMin() + (p.fidgetMax() - p.fidgetMin()) * rand();
            } else {
                target = calm ? 1.0F : 0.0F;
            }
        }
        idleEnv += (target - idleEnv) * Math.min(1.0F, dt / (target > idleEnv ? 0.05F : 0.25F));
        fYaw = fPitch = idlePitch = idleRoll = idleDip = idleNod = idleHeadRoll = idleNeck = idleDrop = idleJaw = scratch = yawnShut = shakeAccel = 0;
        if (idle < 0 && idleEnv < 0.01F) return;
        float u = t - idleAt, e = idleEnv;
        float side = idleSide;
        switch (IDLES[Math.max(0, idle)]) {
            case LOOK -> {
                // a glance one way, a look the other, back: the eyes go first (the head's spring lags them)
                float k = u < 0.35F * idleLen ? Mth.smoothstep(u / (0.25F * idleLen))
                        : u < 0.75F * idleLen ? 1.0F - 1.55F * Mth.smoothstep((u - 0.35F * idleLen) / (0.2F * idleLen))
                        : -0.55F * (1.0F - Mth.smoothstep((u - 0.75F * idleLen) / (0.25F * idleLen)));
                fYaw = side * 0.5F * k * e;
                fPitch = -0.08F * Math.abs(k) * e;
            }
            case SNIFF -> {
                float k = envelope(u, idleLen, 0.35F, 0.4F) * e;
                fPitch = 0.38F * k;
                idleNeck = 0.18F * k;
                idleNod = 0.05F * k * Mth.sin(u * 44.0F);
                idlePitch = 0.04F * k;
                fYaw = side * 0.15F * k * Mth.sin(u * 2.0F);
            }
            case FLICK -> { } // an impulse into one spring, given when it starts
            case SHAKE -> {
                float k = envelope(u, idleLen, 0.08F, 0.35F) * e;
                float wob = Mth.sin(u * 47.0F) * k * (1.0F - 0.5F * u / idleLen);
                idleRoll = 0.13F * wob;
                idleHeadRoll = -0.2F * wob;
                shakeAccel = 2600.0F * wob;
            }
            case STRETCH -> {
                float k = envelope(u, idleLen, 0.6F, 0.7F) * e;
                idlePitch = 0.13F * k;
                fPitch = -0.3F * k;
                idleDip = 0.6F * k;
            }
            case YAWN -> {
                float k = envelope(u, idleLen, 0.6F, 0.6F) * e;
                idleJaw = 0.6F * k;
                fPitch = -0.32F * k;
                yawnShut = 0.85F * k;
                depth += 0.8F * k;
            }
            case SCRATCH -> {
                float k = envelope(u, idleLen, 0.25F, 0.3F) * e;
                idleHeadRoll = side * 0.22F * k;
                fPitch = 0.15F * k;
                idleRoll = side * 0.07F * k;
                scratch = (-0.5F + 0.35F * Mth.sin(u * 50.0F)) * k;
                scratchSide = idleSide;
            }
            case PERK -> {
                float k = envelope(u, idleLen, 0.12F, 0.5F) * e;
                fPitch = -0.16F * k;
                fYaw = side * 0.2F * k;
            }
            case GRAZE -> {
                // down to the grass at its feet (the neck sinks in front of the chest, the forequarters dip), a tug at
                // each bite, up again chewing; small bodies peck quicker
                float up = idleLen - 1.6F, hz = Mth.clamp(1.4F * Mth.sqrt(14.0F / Math.max(4.0F, legLength())), 1.0F, 3.2F);
                float k = envelope(u, up, 0.8F, 0.6F) * e;
                float bite = u < up ? Mth.sin(u * Mth.TWO_PI * hz) : 0.0F, tug = bite > 0 ? bite * bite * k : 0.0F;
                boolean neck = rig.neck != null;
                fPitch = 0.55F * k;
                fYaw = side * 0.12F * k * Mth.sin(u * 0.7F);
                idlePitch = 0.07F * k;
                idleDip = 0.8F * k;
                idleNeck = (neck ? 0.65F : 0.0F) * k - 0.08F * tug;
                idleNod = (neck ? 0.15F : 0.55F) * k + 0.07F * tug;
                idleDrop = 3.0F * k;
                float chew = envelope(u - up + 0.3F, 1.9F, 0.2F, 0.4F) * e, c = Mth.sin(u * 15.0F);
                idleJaw = 0.2F * tug + 0.14F * chew * (0.5F + 0.5F * c);
                idleHeadRoll = 0.05F * chew * c;
            }
            case SETTLE -> {
                float k = envelope(u, idleLen, 0.8F, 0.9F) * e;
                idleRoll = side * 0.06F * k;
                idleDip = 0.45F * k;
                fPitch = 0.05F * k;
            }
        }
    }

    private int pick() {
        float total = 0;
        float[] w = p.idles();
        if (!rig.bones) return -1; // a vanilla body's idles are its own (Fresh Animations)
        for (int i = 0; i < w.length; i++) if (rig.can[i]) total += idleWeight(i, w[i]);
        if (total <= 0) return -1;
        float r = rand() * total;
        for (int i = 0; i < w.length; i++) {
            if (!rig.can[i]) continue;
            r -= idleWeight(i, w[i]);
            if (r <= 0) return i;
        }
        return -1;
    }

    /** The legs' mean length (px), or a cow's when it has no planted legs. */
    private float legLength() {
        if (legs == null || legs.rig.n == 0) return 14.0F;
        float sum = 0;
        for (int i = 0; i < legs.rig.n; i++) sum += legs.rig.reach(i);
        return sum / legs.rig.n;
    }

    private float idleWeight(int i, float base) {
        if (i == Idle.SHAKE.ordinal() && t < wetUntil) return base > 0 ? 40.0F : 20.0F; // wet: shake it off
        return base;
    }

    private void start(int which) {
        if (which < 0) {
            nextIdle = t + p.fidgetMax();
            return;
        }
        idle = which;
        idleAt = t;
        idleSide = rand() < 0.5F ? -1 : 1;
        idleLen = switch (IDLES[which]) {
            case LOOK -> 2.6F + rand();
            case SNIFF -> 1.6F + 0.6F * rand();
            case FLICK -> 0.6F;
            case SHAKE -> 1.0F;
            case STRETCH -> 2.4F;
            case YAWN -> 2.0F;
            case SCRATCH -> 1.4F;
            case PERK -> 1.2F;
            case SETTLE -> 2.6F;
            case GRAZE -> 5.0F + 4.0F * rand();
        };
        if (which == Idle.SHAKE.ordinal()) wetUntil = -10;
        if (which == Idle.FLICK.ordinal() && rig.n > 0) {
            // an ear, a tail, a feeler or a wing twitches
            int tries = 0, j;
            do {
                j = (int) (rand() * rig.n);
                Role r = ROLES[rig.role[j]];
                if (r == Role.EAR || r == Role.TAIL || r == Role.ANTENNA || r == Role.WING || r == Role.FIN) break;
            } while (++tries < 6);
            flick = j;
            float a = 6.0F + 3.0F * rand();
            flickX = (rand() - 0.5F) * a;
            flickY = (rand() - 0.5F) * a * 1.4F;
            flickZ = (rand() - 0.5F) * a;
        }
    }

    // ---- a boss's fall (root level) -----------------------------------------------------------------------------------

    private void deathFall(boolean dead) {
        if (!dead || deathLength > 0) {
            topple = toppleV = toppleA = 0;
            return;
        }
        // a stagger back, the knees go, then the whole weight falls (accelerating) and bounces once on the ground
        float u = dying, a;
        if (u < 0.22F) a = 0;
        else if (u < 0.7F) {
            float k = (u - 0.22F) / 0.48F;
            a = 90.0F * k * k;
        } else if (u < 0.84F) a = 90.0F - 11.0F * Mth.sin(Mth.PI * (u - 0.7F) / 0.14F);
        else if (u < 0.94F) a = 90.0F - 3.0F * Mth.sin(Mth.PI * (u - 0.84F) / 0.1F);
        else a = 90.0F;
        float v = (a - topple) / dt;
        toppleA = (v - toppleV) / dt;
        toppleV = v;
        topple = a;
    }

    /**
     * The root-level motions, on the pose stack after the yaw (call from setupRotations): the stagger, lean, the step's
     * dip and the tremble for bodies whose body bone doesn't carry the head; a boss's provocation and its weighty fall.
     * Returns true if it drew the fall (skip vanilla's flip then).
     */
    public boolean rootPose(PoseStack pose, LivingEntityRenderState s) {
        if (rig.rootMotion && !rig.rootCarries) { // a body walking on its feet has these on its root part (LifeRig.apply)
            // model space to the pose stack: model x and y are flipped here, so pitch turns the other way
            pose.translate(0.0F, -dip / 16.0F, 0.0F);
            if (bodyPitch != 0 || bodyRoll != 0) {
                pose.rotate(Axis.XP.rotation(-bodyPitch * 0.8F));
                pose.rotate(Axis.ZP.rotation(bodyRoll * 0.8F));
            }
        }
        if (dying < 0 || deathLength > 0) return false;
        float u = dying;
        // the stagger before the fall: it rears and sways
        float rear = u < 0.3F ? Mth.sin(Mth.PI * u / 0.3F) : 0.0F;
        pose.rotate(Axis.XP.rotationDegrees(-9.0F * rear));
        pose.rotate(Axis.ZP.rotationDegrees(deathSide * topple));
        return true;
    }

    public boolean toppling() {
        return dying >= 0 && deathLength <= 0;
    }

    // ---- the springs (from LifeRig.apply, once a frame) -----------------------------------------------------------------

    void integrate(LifeRig r) {
        int n = r.n;
        if (n == 0) return;
        if (far) { // past 32 blocks nobody sees an ear twitch: let the springs settle and stop
            float k = (float) Math.exp(-dt * 5.0F);
            for (int j = 0; j < n; j++) {
                ax[j] *= k;
                ay[j] *= k;
                az[j] *= k;
                avx[j] = avy[j] = avz[j] = 0;
            }
            drvInit = false;
            return;
        }
        float gainAll = reduced() ? 0.5F : 1.0F;
        // what the parts' parents did this frame: the clips' swing of a head or a body, and the body's bob
        float[] d = drv, dv = drvV;
        for (int i = 0; i < r.drivers.length; i++) {
            var q = r.drivers[i];
            int o = i * 3;
            if (!drvInit) {
                d[o] = q.xRot;
                d[o + 1] = q.yRot;
                d[o + 2] = q.zRot;
                dv[o] = dv[o + 1] = dv[o + 2] = 0;
                drvAccs[o] = drvAccs[o + 1] = drvAccs[o + 2] = 0;
                continue;
            }
            float nx = (q.xRot - d[o]) / dt, ny = (q.yRot - d[o + 1]) / dt, nz = (q.zRot - d[o + 2]) / dt;
            float accX = (nx - dv[o]) / dt, accY = (ny - dv[o + 1]) / dt, accZ = (nz - dv[o + 2]) / dt;
            dv[o] = nx;
            dv[o + 1] = ny;
            dv[o + 2] = nz;
            d[o] = q.xRot;
            d[o + 1] = q.yRot;
            d[o + 2] = q.zRot;
            drvAccs[o] = Mth.clamp(accX, -400, 400);
            drvAccs[o + 1] = Mth.clamp(accY, -400, 400);
            drvAccs[o + 2] = Mth.clamp(accZ, -400, 400);
        }
        float bodyY = r.body != null ? r.body.y + (r.rootMotion ? 0 : dip) : dip;
        float bAcc = 0;
        if (drvInit) {
            float nv = (bodyY - bodyLy) / dt;
            bAcc = (nv - bodyLv) / dt;
            bodyLv = nv;
        }
        bodyLy = bodyY;
        boolean first = !drvInit;
        drvInit = true;
        if (first) return;
        // the frame's acceleration in model space: the body's motion, the step's bob, a shake, a blow
        float Ax = amx + shakeAccel + kick * kickX * 900.0F, Ay = amy + bAcc - thump * 3200.0F, Az = amz + kick * kickZ * 900.0F;
        kick = 0;
        thump = 0;
        float yawA = -yawAccel; // model y points down
        float toppleAcc = toppleA * Mth.DEG_TO_RAD * deathSide;
        int sub = Math.max(1, Mth.ceil(dt * 120.0F));
        float h = dt / sub;
        for (int j = 0; j < n; j++) {
            float dX = r.dx[j], dY = r.dy[j], dZ = r.dz[j], il = r.invLen[j];
            // a lever pointing along d, its frame accelerated by A, feels -(d x A)/L
            float lx = -(dY * Az - dZ * Ay) * il, ly = -(dZ * Ax - dX * Az) * il, lz = -(dX * Ay - dY * Ax) * il;
            // its parent turning (a clip's head toss, the body's yaw, a fall) leaves it behind
            lx -= driverAcc(r, j, 0);
            ly -= driverAcc(r, j, 1) + yawA;
            lz -= driverAcc(r, j, 2) + toppleAcc;
            int c = r.chain[j];
            if (c >= 0) { // follow-through: a chain link swings after the one it hangs from
                lx -= 0.7F * aacc[c * 3];
                ly -= 0.7F * aacc[c * 3 + 1];
                lz -= 0.7F * aacc[c * 3 + 2];
            }
            float g = r.gain[j] * gainAll;
            lx *= g;
            ly *= g;
            lz *= g;
            if (j == flick) {
                avx[j] += flickX;
                avy[j] += flickY;
                avz[j] += flickZ;
            }
            float k = r.w2[j], cd = r.damp[j];
            float x = ax[j], y = ay[j], zz = az[j], vxx = avx[j], vyy = avy[j], vzz = avz[j];
            float accX = 0, accY = 0, accZ = 0;
            for (int i = 0; i < sub; i++) {
                accX = lx - k * x - cd * vxx;
                accY = ly - k * y - cd * vyy;
                accZ = lz - k * zz - cd * vzz;
                vxx += accX * h;
                vyy += accY * h;
                vzz += accZ * h;
                x += vxx * h;
                y += vyy * h;
                zz += vzz * h;
            }
            // only swing, never twist about the part's own length; and never further than the part could bend
            float tw = x * dX + y * dY + zz * dZ;
            x -= tw * dX;
            y -= tw * dY;
            zz -= tw * dZ;
            int hg = r.hinge[j];
            if (hg == 0) { // a hanging plate turns about the edge it hangs from, never off it
                y = zz = vyy = vzz = 0;
            } else if (hg == 2) {
                x = y = vxx = vyy = 0;
            }
            float m = r.maxAngle[j], mag = x * x + y * y + zz * zz;
            if (mag > m * m) {
                float sc = m / Mth.sqrt(mag);
                x *= sc;
                y *= sc;
                zz *= sc;
                vxx *= 0.5F;
                vyy *= 0.5F;
                vzz *= 0.5F;
            }
            ax[j] = x;
            ay[j] = y;
            az[j] = zz;
            avx[j] = vxx;
            avy[j] = vyy;
            avz[j] = vzz;
            aacc[j * 3] = accX;
            aacc[j * 3 + 1] = accY;
            aacc[j * 3 + 2] = accZ;
        }
        flick = -1;
    }

    private float driverAcc(LifeRig r, int j, int axis) {
        int d = r.driver[j];
        return d < 0 ? 0.0F : drvAccs[d * 3 + axis];
    }

    // ---- smoothing the animation graph ----------------------------------------------------------------------------------

    private final float[] eased = new float[16];
    private final int[] easedFrame = new int[16];

    /**
     * A graph weight eased toward its target over {@code tau} seconds, stepped once a frame however often the model
     * poses: a held pose, an angry stance or a swim fades in and out instead of popping.
     */
    public float ease(int slot, float target, float tau) {
        if (easedFrame[slot] != frame) {
            easedFrame[slot] = frame;
            eased[slot] += (target - eased[slot]) * Math.min(1.0F, dt / Math.max(0.001F, tau));
            if (frame <= 1) eased[slot] = target;
        }
        return eased[slot];
    }

    private final float[] clocks = new float[4];
    private final int[] clockFrame = new int[4];

    /**
     * A loop's own clock, advanced once a frame at {@code rate} (a swim or wingbeat that quickens with speed): playing a
     * loop at age times a speed factor jumps its phase whenever the speed changes; this never does.
     */
    public float clock(int slot, float rate) {
        if (clockFrame[slot] != frame) {
            clockFrame[slot] = frame;
            if (clocks[slot] == 0) clocks[slot] = idleTime * (1 + slot);
            clocks[slot] += dt * rate;
            if (clocks[slot] > 3600) clocks[slot] -= 3600;
        }
        return clocks[slot];
    }

    /** The life our renderers put on a creature's look (AwLook.life), or null. */
    public static @org.jspecify.annotations.Nullable Life from(LivingEntityRenderState s) {
        dev.awaken.mobs.entity.AwLook l = s.getRenderData(dev.awaken.mobs.client.model.AwModels.LOOK);
        return l != null && l.life instanceof Life x ? x : null;
    }

    // ---- making one ---------------------------------------------------------------------------------------------------

    /** A creature's life on a rig; reuse the one it has unless the rig changed (a resource reload rebuilds models). */
    public static Life of(@org.jspecify.annotations.Nullable Object held, LifeRig rig, int seed) {
        if (held instanceof Life l && l.rig == rig) return l;
        return new Life(rig, seed);
    }
}
