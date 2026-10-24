package dev.awaken.body.client;

import dev.awaken.body.Body;
import dev.awaken.body.BodyClips;
import dev.awaken.body.BodyContent;
import dev.awaken.body.Move;
import dev.awaken.body.RunnerRules;
import dev.awaken.body.net.BodyPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * Spike S3 (throwaway): the Runner's Kit predicted on the client, in the body's pattern. Everything happens in the
 * movement-input event: a move sets this tick's velocity (minus the air control vanilla is about to add, so the body
 * goes exactly where the move says) and vanilla's collision does the rest. One {@code Run} per start; {@code End}
 * when the client lets go; a refusal lets go here. States: a wall-run, a hang (shimmy inside it), or a path (grab,
 * lower, climb, ledge jump up or sideways, a corner) that is position-driven like the mantle. Jump help (coyote time,
 * the early-press buffer, the edge snap) only shifts vanilla's own jump and sends nothing.
 */
public final class RunnerMoves {
    private RunnerMoves() {}

    enum State { NONE, RUN, HANG, PATH }

    static State state = State.NONE;
    /** Spike comparison: a fresh run needs jump held (false) or a jump press in the air within the buffer (true). */
    static boolean airPress;

    private static final BlockPos.MutableBlockPos M = new BlockPos.MutableBlockPos();
    private static final RunnerRules.ProbeWindow WIN = new RunnerRules.ProbeWindow();
    private static final double[] ACC = new double[2], P = new double[3];

    // ground and jump timing
    private static boolean wasGround;
    private static int groundAt = -100, airJumpAt = -100;
    private static boolean jumpedOff;
    // wall-run
    static int runAge, runLen, runGrip, runStart = -100, runEnd = -100, runLink;
    private static int runMiss;
    private static boolean runStride;
    static int runAxis, runSide;
    static double runPlane, runVy, runSpeed, runTx, runTz;
    private static long lastWall;
    private static final long[] walls = new long[8];
    private static int wallCount, links, chainUntil = -100;
    // hang
    static int hangStart = -100, hangEnd = -100;
    static RunnerRules.@Nullable Edge edge;
    private static double holdX, holdY, holdZ;
    static boolean shimmying;
    static int shimmyDir;
    private static int regrabAt, settleAt, shimmyAt = -100;
    // path
    static final int GRAB = 0, LOWER = 1, CLIMB = 2, UP = 3, SIDE = 4, CORNER = 5;
    static int pathKind, pathAge, pathLen, pathStart = -100;
    private static boolean pathToHang;
    private static double ax, ay, az, bx, by, bz, cx, cz;
    private static RunnerRules.@Nullable Edge pathEdge;
    // a view turned by a move (the lower, a corner, a back jump)
    private static int turnLeft;
    private static float turnStep;
    // the sneak-lower needs a held intent
    private static int edgeHold;

    static boolean active() {
        return state != State.NONE;
    }

    /** For the Ascent (the rope and wings stand aside while a Runner move holds the body). */
    public static boolean busy() {
        return state != State.NONE;
    }

    /**
     * For the Ascent: whether a jump now would start a wall-run beside this body (so flying free off a rope, a jump at a
     * wall runs along it rather than opening the wings).
     */
    public static boolean wallBeside(LocalPlayer p) {
        if (!dev.awaken.body.BodyConfig.runnersKit()) return false;
        BlockGetter level = p.level();
        RunnerRules.Wall w = RunnerRules.findWall(level, CollisionContext.of(p), M, p.getX(), p.getY(), p.getZ(), p.getYRot(), RunnerRules.RUN_REACH);
        return w != null && !RunnerRules.groundWithin(level, CollisionContext.of(p), M, p.getX(), p.getY(), p.getZ(), RunnerRules.RUN_MIN_HEIGHT);
    }

    static boolean hanging() {
        return state == State.HANG || state == State.PATH && pathToHang;
    }

    // ---- the input event --------------------------------------------------------------------------------------------

    /** This tick's runner work; true when a runner move owns the tick (the body's other moves then wait). */
    static boolean onInput(LocalPlayer p, ClientInput in, boolean jumpPressed, boolean shiftPressed) {
        long t0 = System.nanoTime();
        long look0 = WIN.lookups, probe0 = RunnerRules.probes;
        try {
            return step(p, in, jumpPressed, shiftPressed);
        } finally {
            Cost.add(System.nanoTime() - t0, WIN.lookups - look0, WIN.misses, RunnerRules.probes - probe0);
        }
    }

    private static boolean step(LocalPlayer p, ClientInput in, boolean jumpPressed, boolean shiftPressed) {
        int now = ClientMoves.ticks;
        Input k = in.keyPresses;
        boolean ground = p.onGround();
        if (ground) {
            if (!wasGround) landed(p, in, k, now);
            groundAt = now;
        } else if (wasGround) {
            jumpedOff = p.getDeltaMovement().y > 0.2;
        }
        wasGround = ground;
        if (turnLeft > 0) {
            p.setYRot(p.getYRot() + turnStep);
            p.setYHeadRot(p.getYRot());
            turnLeft--;
        }
        if (ClientMoves.off(p) || p.isInLiquid()) {
            if (state != State.NONE) stop(p, "off");
            return false;
        }
        BlockGetter level = WIN.begin(p.level(), p.getX(), p.getY(), p.getZ());
        CollisionContext ctx = CollisionContext.of(p);
        switch (state) {
            case RUN -> {
                runTick(p, in, k, jumpPressed, shiftPressed, level, ctx, now);
                return true;
            }
            case HANG -> {
                hangTick(p, in, k, jumpPressed, shiftPressed, level, ctx, now);
                return true;
            }
            case PATH -> {
                pathTick(p, in, now);
                return true;
            }
            default -> {
            }
        }
        if (!ground) {
            if (jumpPressed) airJumpAt = now;
            if (now >= regrabAt && tryGrab(p, in, level, ctx, now)) return true;
            boolean chain = now <= chainUntil && links > 0;
            boolean intent = airPress ? now - airJumpAt <= RunnerRules.BUFFER : k.jump();
            if ((chain || intent && p.isSprinting()) && tryRun(p, in, level, ctx, chain ? links : 0, now)) return true;
            if (jumpPressed && now - groundAt <= RunnerRules.COYOTE && !jumpedOff && p.getDeltaMovement().y <= 0) {
                p.jumpFromGround(); // coyote time: the jump you meant at the edge
                jumpedOff = true;
                ClientMoves.trace("coyote jump " + (now - groundAt) + " ticks late");
                return false;
            }
            Vec3 v = p.getDeltaMovement();
            if (v.y <= 0) {
                double lift = RunnerRules.snapLift(level, ctx, M, p.getX(), p.getY(), p.getZ(), v.x, v.z);
                if (!Double.isNaN(lift)) {
                    p.setDeltaMovement(v.x, lift, v.z);
                    ClientMoves.trace(String.format("edge snap %.2f", lift));
                }
            }
        } else {
            return tryLower(p, in, k, level, ctx, now);
        }
        return false;
    }

    /** The landing tick: the chain is over, and a jump pressed just before landing goes off now (the buffer). */
    private static void landed(LocalPlayer p, ClientInput in, Input k, int now) {
        links = 0;
        wallCount = 0;
        chainUntil = -100;
        if (state == State.NONE && now - airJumpAt <= RunnerRules.BUFFER && !k.jump()) {
            in.keyPresses = ClientMoves.with(k, true, k.shift());
            ClientMoves.trace("buffered jump (" + (now - airJumpAt) + " ticks early)");
            airJumpAt = -100;
        }
    }

    private static void send(Move move, int flag, LocalPlayer p, double tx, double ty, double tz, int axis, int sign) {
        // the Runner's moves feed the Ascent's Flow (a shimmy is a pause, not a move)
        switch (move) {
            case WALL_RUN -> dev.awaken.body.ascent.client.AscentFlow.gain(0.07F);
            case WALL_JUMP -> dev.awaken.body.ascent.client.AscentFlow.gain(0.09F);
            case LEDGE_HANG -> dev.awaken.body.ascent.client.AscentFlow.gain(0.03F);
            case LEDGE_JUMP -> dev.awaken.body.ascent.client.AscentFlow.gain(0.06F);
            default -> {
            }
        }
        ClientPacketDistributor.sendToServer(new BodyPayloads.Run(move.ordinal(), flag, p.getYRot(), p.getX(), p.getY(), p.getZ(), tx, ty, tz, axis, sign));
    }

    private static void sendEnd(Move move) {
        ClientPacketDistributor.sendToServer(new BodyPayloads.End(move.ordinal()));
    }

    /** Air control vanilla adds this tick (moveRelative in the air), so a move can take it back out. */
    private static void inputAccel(LocalPlayer p, ClientInput in, double[] out) {
        Vec2 mv = in.getMoveVector();
        float x = mv.x, y = mv.y;
        out[0] = out[1] = 0;
        if (x == 0 && y == 0) return;
        x *= 0.98F;
        y *= 0.98F;
        if (p.isUsingItem() && !p.isPassenger()) {
            x *= 0.2F;
            y *= 0.2F;
        }
        if (p.isMovingSlowly()) {
            float f = (float) p.getAttributeValue(Attributes.SNEAKING_SPEED);
            x *= f;
            y *= f;
        }
        float len = Mth.sqrt(x * x + y * y), dx = x / len, dy = y / len;
        float tan = Math.abs(dy) > Math.abs(dx) ? Math.abs(dx) / Math.abs(dy) : Math.abs(dy) / Math.abs(dx);
        len = Math.min(len * Mth.sqrt(1 + tan * tan), 1);
        x = dx * len;
        y = dy * len;
        float speed = p.isSprinting() ? 0.026F : 0.02F;
        float sin = Mth.sin(p.getYRot() * Mth.DEG_TO_RAD), cos = Mth.cos(p.getYRot() * Mth.DEG_TO_RAD);
        out[0] = (x * cos - y * sin) * speed;
        out[1] = (y * cos + x * sin) * speed;
    }

    private static void turnTo(float yaw, int ticks) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        turnStep = Mth.wrapDegrees(yaw - p.getYRot()) / ticks;
        turnLeft = ticks;
    }

    private static float yawOf(double dx, double dz) {
        return (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
    }

    // ---- wall-run ---------------------------------------------------------------------------------------------------

    private static boolean ranWall(long id) {
        for (int i = 0; i < wallCount; i++) if (walls[i] == id) return true;
        return false;
    }

    private static boolean tryRun(LocalPlayer p, ClientInput in, BlockGetter level, CollisionContext ctx, int link, int now) {
        if (Minecraft.getInstance().gui.screen() != null || p.isUsingItem()) return false;
        RunnerRules.Wall w = RunnerRules.findWall(level, ctx, M, p.getX(), p.getY(), p.getZ(), p.getYRot(), RunnerRules.RUN_REACH);
        if (w == null) return false;
        Vec3 v = p.getDeltaMovement();
        double along = v.x * w.tx() + v.z * w.tz();
        if (link == 0 && along < RunnerRules.RUN_MIN_ALONG) return false;
        if (RunnerRules.groundWithin(level, ctx, M, p.getX(), p.getY(), p.getZ(), RunnerRules.RUN_MIN_HEIGHT)) return false;
        long id = w.id();
        if (link == 0 ? ranWall(id) : id == lastWall) return false;
        state = State.RUN;
        runAge = 0;
        runLink = link;
        runStride = Body.wallStride(p);
        runLen = RunnerRules.runLength(link, Body.runnerRank(p), runStride);
        runGrip = RunnerRules.runGrip(link, runStride);
        runStart = now;
        runAxis = w.axis();
        runSide = w.side();
        runPlane = w.plane();
        runTx = w.tx();
        runTz = w.tz();
        runVy = Mth.clamp(v.y, -0.6, 0.42);
        runSpeed = Mth.clamp(Math.max(along, RunnerRules.RUN_SPEED_MIN), RunnerRules.RUN_SPEED_MIN, RunnerRules.RUN_SPEED_MAX);
        runMiss = 0;
        lastWall = id;
        if (wallCount < walls.length) walls[wallCount++] = id;
        send(Move.WALL_RUN, link, p, 0, w.plane(), 0, w.axis(), w.side());
        ClientMoves.trace(String.format("wall-run%s side %d, %d ticks, vy %.2f, speed %.2f", link > 0 ? " link " + link : "", w.side(), runLen, v.y, runSpeed));
        BodyAnims.runner(p.getId(), BodyClips.wallOnLeft(p.getYRot(), w.axis(), w.side()) ? BodyClips.WALLRUN_L : BodyClips.WALLRUN_R);
        RunnerFx.step(p, runAxis == 0 ? runSide : 0, runAxis == 2 ? runSide : 0, runTx, runTz, true);
        runApply(p, in, w.gap());
        return true;
    }

    private static void runTick(LocalPlayer p, ClientInput in, Input k, boolean jumpPressed, boolean shiftPressed, BlockGetter level,
                                CollisionContext ctx, int now) {
        runAge++;
        if (Minecraft.getInstance().gui.screen() != null) {
            endRun(p, true, "screen");
            return;
        }
        if (p.onGround()) {
            endRun(p, true, "landed");
            return;
        }
        if (shiftPressed) {
            endRun(p, true, "let go");
            return;
        }
        if (jumpPressed && runAge >= 2 && links < RunnerRules.links(Body.runnerRank(p))) {
            wallJump(p, now);
            return;
        }
        if (runAge >= runLen) {
            endRun(p, true, "done");
            return;
        }
        // a ledge ahead (the run carries you into a hang)
        if (tryGrab(p, in, level, ctx, now)) {
            sendEnd(Move.WALL_RUN);
            runEnd = now;
            return;
        }
        if (p.horizontalCollision && !p.minorHorizontalCollision) {
            endRun(p, true, "blocked");
            return;
        }
        RunnerRules.Wall w = RunnerRules.wallOn(level, ctx, M, p.getX(), p.getY(), p.getZ(), runAxis, runSide, RunnerRules.RUN_REACH + 0.1, runTx, runTz);
        boolean touching = w != null && Math.abs(w.plane() - runPlane) < 0.1;
        runMiss = touching ? 0 : runMiss + 1;
        if (runMiss > 1) {
            endRun(p, true, "wall ends");
            return;
        }
        runApply(p, in, touching ? w.gap() : RunnerRules.RUN_GAP);
        // a footfall on the wall every half stride (the clip's stride pair is 8 ticks)
        if (runAge % 4 == 0) RunnerFx.step(p, runAxis == 0 ? runSide : 0, runAxis == 2 ? runSide : 0, runTx, runTz, true);
    }

    private static void runApply(LocalPlayer p, ClientInput in, double gap) {
        double tgt = RunnerRules.runTarget(runAge, runLen, runGrip, runStride);
        runVy += (tgt - runVy) * RunnerRules.RUN_APPROACH;
        double nx = runAxis == 0 ? -runSide : 0, nz = runAxis == 2 ? -runSide : 0;
        double close = Mth.clamp(gap - RunnerRules.RUN_GAP, -0.1, 0.1);
        inputAccel(p, in, ACC);
        p.setDeltaMovement(runTx * runSpeed - nx * close - ACC[0], runVy, runTz * runSpeed - nz * close - ACC[1]);
        p.resetFallDistance();
    }

    private static void wallJump(LocalPlayer p, int now) {
        double nx = runAxis == 0 ? -runSide : 0, nz = runAxis == 2 ? -runSide : 0;
        double keep = runSpeed * RunnerRules.WALL_JUMP_KEEP;
        send(Move.WALL_JUMP, links, p, 0, runPlane, 0, runAxis, runSide);
        endRun(p, false, "wall jump");
        p.setDeltaMovement(nx * RunnerRules.WALL_JUMP_OUT + runTx * keep, RunnerRules.WALL_JUMP_UP, nz * RunnerRules.WALL_JUMP_OUT + runTz * keep);
        links++;
        chainUntil = now + 30;
        ClientMoves.suppressJump();
        ClientMoves.trace("wall jump, link " + links);
        BodyAnims.runner(p.getId(), BodyClips.WALLJUMP);
        BodyFx.sound(p, BodyContent.WALLJUMP.get(), 0.8F, 1F);
        RunnerFx.step(p, -nx, -nz, runTx, runTz, false);
    }

    static void endRun(LocalPlayer p, boolean tellServer, String why) {
        if (state != State.RUN) return;
        state = State.NONE;
        runEnd = ClientMoves.ticks;
        ClientMoves.trace("wall-run ends (" + why + ") after " + runAge);
        BodyAnims.runnerEnd(p.getId());
        if (tellServer) sendEnd(Move.WALL_RUN);
    }

    // ---- hang -------------------------------------------------------------------------------------------------------

    /** Airborne toward an edge whose top is 1.2–2.4 above the feet (facing it within 50°, moving or pressing toward it). */
    private static boolean tryGrab(LocalPlayer p, ClientInput in, BlockGetter level, CollisionContext ctx, int now) {
        if (now < regrabAt || p.isUsingItem()) return false;
        float yaw = p.getYRot();
        double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD), fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        int axis = Math.abs(fx) >= Math.abs(fz) ? 0 : 2;
        if (Math.max(Math.abs(fx), Math.abs(fz)) < 0.643) return false;
        int sign = (axis == 0 ? fx : fz) > 0 ? 1 : -1;
        Vec3 v = p.getDeltaMovement();
        double toward = (axis == 0 ? v.x : v.z) * sign;
        if (!(toward >= 0.04 || in.keyPresses.forward() && toward > -0.02)) return false;
        RunnerRules.Edge e = RunnerRules.findEdge(level, ctx, M, p.getX(), p.getY(), p.getZ(), axis, sign, RunnerRules.HANG_REACH,
                RunnerRules.HANG_LO, RunnerRules.HANG_HI, 0, 0.2);
        if (e == null) return false;
        if (state == State.RUN) {
            state = State.NONE;
            ClientMoves.trace("wall-run ends (ledge ahead) after " + runAge);
        }
        startHang(p, e, BodyPayloads.HANG_GRAB, now);
        return true;
    }

    private static boolean tryLower(LocalPlayer p, ClientInput in, Input k, BlockGetter level, CollisionContext ctx, int now) {
        if (!k.shift() || !k.forward() || p.isSprinting() || p.isUsingItem()) {
            edgeHold = 0;
            return false;
        }
        RunnerRules.Edge e = RunnerRules.lowerEdge(level, ctx, M, p.getX(), p.getY(), p.getZ(), p.getYRot());
        if (e == null) {
            edgeHold = 0;
            return false;
        }
        if (++edgeHold < RunnerRules.LOWER_HOLD) return false;
        edgeHold = 0;
        startHang(p, e, BodyPayloads.HANG_LOWER, now);
        return true;
    }

    private static void startHang(LocalPlayer p, RunnerRules.Edge e, int kind, int now) {
        send(Move.LEDGE_HANG, kind, p, e.hx(), e.hy(), e.hz(), e.axis(), e.sign());
        links = 0;
        wallCount = 0;
        chainUntil = -100;
        hangStart = now;
        shimmying = false;
        ClientMoves.trace(String.format("hang (%s) top %.2f above the feet %.2f", kind == BodyPayloads.HANG_LOWER ? "lower" : "grab", e.top(), e.top() - p.getY()));
        boolean lower = kind == BodyPayloads.HANG_LOWER;
        BodyAnims.runner(p.getId(), lower ? BodyClips.LOWER : BodyClips.GRAB);
        BodyFx.sound(p, lower ? BodyContent.SHIMMY.get() : BodyContent.GRAB.get(), lower ? 0.5F : 0.85F, 1F);
        if (!lower && p.level() instanceof net.minecraft.client.multiplayer.ClientLevel cl) {
            BodyFx.lip(cl, e.hx() + e.fx() * 0.3, e.top(), e.hz() + e.fz() * 0.3, e.fx(), e.fz());
        }
        if (kind == BodyPayloads.HANG_LOWER) turnTo(yawOf(e.fx(), e.fz()), 8);
        startPath(p, kind == BodyPayloads.HANG_LOWER ? LOWER : GRAB, e.hx(), e.hy(), e.hz(), kind == BodyPayloads.HANG_LOWER ? 8 : 3, e, now);
    }

    private static void hangTick(LocalPlayer p, ClientInput in, Input k, boolean jumpPressed, boolean shiftPressed, BlockGetter level,
                                 CollisionContext ctx, int now) {
        if (settleAt > 0 && now >= settleAt && !shimmying) {
            settleAt = 0;
            BodyAnims.runner(p.getId(), BodyClips.HANG);
        }
        RunnerRules.Edge e = edge;
        if (e == null) {
            stop(p, "no edge");
            return;
        }
        double dx = holdX - p.getX(), dy = holdY - p.getY(), dz = holdZ - p.getZ();
        if (dx * dx + dy * dy + dz * dz > 0.75 * 0.75) { // a teleport, a blow: the hands let go
            endHang(p, true, "moved");
            return;
        }
        if (shiftPressed) {
            endHang(p, true, "drop");
            regrabAt = now + 8;
            return;
        }
        if (jumpPressed) {
            if (ledgeJump(p, k, level, ctx, e, now)) return;
        }
        int dir = (k.left() ? 1 : 0) - (k.right() ? 1 : 0);
        if (dir != 0 && Minecraft.getInstance().gui.screen() == null) {
            if (shimmy(p, dir, level, ctx, e, now)) return;
        } else if (shimmying) {
            shimmying = false;
            sendEnd(Move.SHIMMY);
            ClientMoves.trace("shimmy stops");
            BodyAnims.runner(p.getId(), BodyClips.HANG);
        }
        holdApply(p, in);
    }

    private static void holdApply(LocalPlayer p, ClientInput in) {
        inputAccel(p, in, ACC);
        p.setDeltaMovement(holdX - p.getX() - ACC[0], holdY - p.getY(), holdZ - p.getZ() - ACC[1]);
        p.resetFallDistance();
    }

    /** One shimmy step along the edge (left as you face the wall = +1); around an outside corner; stops at a gap. */
    private static boolean shimmy(LocalPlayer p, int dir, BlockGetter level, CollisionContext ctx, RunnerRules.Edge e, int now) {
        double tx = e.fz() * dir, tz = -e.fx() * dir;
        double step = RunnerRules.shimmy(Body.runnerRank(p));
        double nx = holdX + tx * step, nz = holdZ + tz * step;
        double lead = 0.15 * (e.axis() == 0 ? tz : tx);
        RunnerRules.Edge next = RunnerRules.findEdge(level, ctx, M, nx, holdY, nz, e.axis(), e.sign(), 0.2,
                e.top() - 0.3 - holdY, e.top() + 0.3 - holdY, lead, 0.1);
        if (next != null) {
            if (!shimmying) {
                shimmying = true;
                shimmyDir = dir;
                send(Move.SHIMMY, dir > 0 ? BodyPayloads.SHIMMY_START : BodyPayloads.SHIMMY_START_RIGHT, p, holdX, holdY, holdZ, e.axis(), e.sign());
                ClientMoves.trace("shimmy " + (dir > 0 ? "left" : "right"));
                shimmyAt = now;
            }
            if (dir != shimmyDir || now == shimmyAt) BodyAnims.runner(p.getId(), dir > 0 ? BodyClips.SHIMMY_L : BodyClips.SHIMMY_R);
            if ((now - shimmyAt) % 8 == 4) BodyFx.sound(p, BodyContent.SHIMMY.get(), 0.3F, 0.95F + p.getRandom().nextFloat() * 0.1F);
            shimmyDir = dir;
            edge = next;
            holdX = next.hx();
            holdY = next.hy();
            holdZ = next.hz();
            return false; // holdApply moves there this tick
        }
        // an outside corner: past the end, into the wall's line by a body width, facing back along the edge
        double qx = holdX + tx * 0.62 + e.fx() * 0.64, qz = holdZ + tz * 0.62 + e.fz() * 0.64;
        int axis2 = e.axis() == 0 ? 2 : 0, sign2 = (axis2 == 0 ? tx : tz) > 0 ? -1 : 1;
        RunnerRules.Edge c = RunnerRules.findEdge(level, ctx, M, qx, holdY, qz, axis2, sign2, 0.35, e.top() - 0.3 - holdY, e.top() + 0.3 - holdY, 0, 0.2);
        if (c == null) return false; // a gap or an inside corner: stay
        cx = e.axis() == 0 ? e.plane() : c.plane();
        cz = e.axis() == 0 ? c.plane() : e.plane();
        send(Move.SHIMMY, BodyPayloads.SHIMMY_CORNER, p, c.hx(), c.hy(), c.hz(), c.axis(), c.sign());
        ClientMoves.trace("shimmy round the corner");
        turnTo(yawOf(c.fx(), c.fz()), 8);
        startPath(p, CORNER, c.hx(), c.hy(), c.hz(), 8, c, now);
        return true;
    }

    private static boolean ledgeJump(LocalPlayer p, Input k, BlockGetter level, CollisionContext ctx, RunnerRules.Edge e, int now) {
        if (k.backward()) {
            double nx = -e.fx(), nz = -e.fz();
            send(Move.LEDGE_JUMP, BodyPayloads.JUMP_BACK, p, 0, 0, 0, e.axis(), e.sign());
            endHang(p, false, "back jump");
            p.setDeltaMovement(nx * RunnerRules.LEDGE_BACK_OUT, RunnerRules.LEDGE_BACK_UP, nz * RunnerRules.LEDGE_BACK_OUT);
            turnTo(yawOf(nx, nz), 5);
            regrabAt = now + 3;
            ClientMoves.suppressJump();
            ClientMoves.trace("ledge jump back");
            BodyAnims.runner(p.getId(), BodyClips.LEDGE_BACK);
            BodyFx.sound(p, BodyContent.WALLJUMP.get(), 0.75F, 1.05F);
            return true;
        }
        if (k.left() != k.right()) {
            int dir = k.left() ? 1 : -1;
            double tx = e.fz() * dir, tz = -e.fx() * dir;
            RunnerRules.Edge target = null;
            boolean gap = false;
            double sideReach = RunnerRules.ledgeSide(Body.runnerRank(p));
            for (double d = 0.5; d <= sideReach + 1e-6; d += 0.25) {
                RunnerRules.Edge s = side(level, ctx, e, tx, tz, d);
                if (s == null) {
                    gap = true;
                    continue;
                }
                if (gap) {
                    RunnerRules.Edge further = d + 0.3 <= sideReach ? side(level, ctx, e, tx, tz, d + 0.3) : null;
                    target = further != null ? further : s;
                    break;
                }
            }
            if (target == null && !gap) target = side(level, ctx, e, tx, tz, 1.5); // one edge all along: a hop on it
            if (target == null) return false;
            send(Move.LEDGE_JUMP, BodyPayloads.JUMP_SIDE, p, target.hx(), target.hy(), target.hz(), target.axis(), target.sign());
            ClientMoves.trace(String.format("ledge jump sideways %.2f", Math.abs(e.axis() == 0 ? target.hz() - holdZ : target.hx() - holdX)));
            leaveHold(p);
            startPath(p, SIDE, target.hx(), target.hy(), target.hz(), 9, target, now);
            BodyAnims.runner(p.getId(), dir > 0 ? BodyClips.LEDGE_SIDE_L : BodyClips.LEDGE_SIDE_R);
            BodyFx.sound(p, BodyContent.SHIMMY.get(), 0.5F, 0.85F);
            return true;
        }
        double[] stand = RunnerRules.climbOut(level, ctx, M, e);
        RunnerRules.Edge up = RunnerRules.findEdge(level, ctx, M, holdX, holdY, holdZ, e.axis(), e.sign(), 0.6,
                RunnerRules.HANG_DROP + 0.4, RunnerRules.HANG_DROP + RunnerRules.ledgeUp(Body.runnerRank(p)), 0, 0.2);
        boolean lookUp = p.getXRot() < -15;
        if (stand != null && !(lookUp && up != null)) {
            send(Move.LEDGE_JUMP, BodyPayloads.JUMP_CLIMB, p, stand[0], stand[1], stand[2], e.axis(), e.sign());
            ClientMoves.trace("climb out");
            leaveHold(p);
            startPath(p, CLIMB, stand[0], stand[1], stand[2], 8, null, now);
            ClientMoves.suppressJump();
            BodyAnims.runner(p.getId(), BodyClips.MANTLE);
            BodyFx.sound(p, BodyContent.CLIMB.get(), 0.85F, 1F);
            return true;
        }
        if (up != null) {
            send(Move.LEDGE_JUMP, BodyPayloads.JUMP_UP, p, up.hx(), up.hy(), up.hz(), up.axis(), up.sign());
            ClientMoves.trace(String.format("ledge jump up %.2f", up.top() - e.top()));
            leaveHold(p);
            startPath(p, UP, up.hx(), up.hy(), up.hz(), 6 + (int) Math.round(2 * (up.top() - e.top())), up, now);
            BodyAnims.runner(p.getId(), BodyClips.LEDGE_UP);
            BodyFx.sound(p, BodyContent.SHIMMY.get(), 0.5F, 0.8F);
            return true;
        }
        ClientMoves.trace("ledge jump: nothing up there");
        return false;
    }

    private static RunnerRules.@Nullable Edge side(BlockGetter level, CollisionContext ctx, RunnerRules.Edge e, double tx, double tz, double d) {
        return RunnerRules.findEdge(level, ctx, M, holdX + tx * d, holdY, holdZ + tz * d, e.axis(), e.sign(), 0.6,
                RunnerRules.HANG_DROP - 1.0, RunnerRules.HANG_DROP + 1.0, 0, 0.2);
    }

    /** The hands leave this edge for a path to another (the path's Run already told the server). */
    private static void leaveHold(LocalPlayer p) {
        shimmying = false;
        hangEnd = ClientMoves.ticks;
    }

    static void endHang(LocalPlayer p, boolean tellServer, String why) {
        if (!hanging()) return;
        boolean wasShimmy = shimmying;
        state = State.NONE;
        shimmying = false;
        hangEnd = ClientMoves.ticks;
        edge = null;
        ClientMoves.trace("hang ends (" + why + ") after " + (ClientMoves.ticks - hangStart));
        BodyAnims.runnerEnd(p.getId());
        if (why.equals("drop")) BodyFx.sound(p, BodyContent.DROP.get(), 0.6F, 1F);
        if (tellServer) {
            if (wasShimmy) sendEnd(Move.SHIMMY);
            sendEnd(Move.LEDGE_HANG);
        }
    }

    // ---- paths ------------------------------------------------------------------------------------------------------

    private static void startPath(LocalPlayer p, int kind, double x, double y, double z, int len, RunnerRules.@Nullable Edge then, int now) {
        state = State.PATH;
        pathKind = kind;
        pathAge = 0;
        pathLen = len;
        pathStart = now;
        ax = p.getX();
        ay = p.getY();
        az = p.getZ();
        bx = x;
        by = y;
        bz = z;
        pathEdge = then;
        pathToHang = then != null;
        pathTick(p, null, now);
    }

    private static void pathTick(LocalPlayer p, @Nullable ClientInput in, int now) {
        pathAge++;
        if (pathAge > 1) {
            pathAt(pathAge - 1, P);
            double ex = P[0] - p.getX(), ey = P[1] - p.getY(), ez = P[2] - p.getZ();
            if (ex * ex + ey * ey + ez * ez > 0.5 * 0.5) {
                ClientMoves.trace("path " + pathKind + " blocked at " + pathAge);
                boolean toHang = pathToHang;
                state = State.NONE;
                if (toHang) sendEnd(Move.LEDGE_HANG);
                return;
            }
        }
        pathAt(Math.min(pathAge, pathLen), P);
        if (in != null) inputAccel(p, in, ACC);
        else ACC[0] = ACC[1] = 0;
        p.setDeltaMovement(P[0] - p.getX() - ACC[0], P[1] - p.getY(), P[2] - p.getZ() - ACC[1]);
        p.resetFallDistance();
        if (pathAge < pathLen) return;
        if (pathToHang && pathEdge != null) {
            state = State.HANG;
            edge = pathEdge;
            holdX = bx;
            holdY = by;
            holdZ = bz;
            if (pathKind != GRAB && pathKind != LOWER) {
                hangStart = now;
                // caught the next edge
                BodyFx.sound(p, BodyContent.GRAB.get(), 0.75F, 1.05F);
            }
            // the catch settles into the hang once its clip has played (the grab and lower clips run 8 ticks)
            settleAt = now + (pathKind == GRAB || pathKind == LOWER ? 5 : 1);
        } else {
            state = State.NONE;
            ClientMoves.trace("climbed out");
        }
    }

    private static double smooth(double u) {
        u = Mth.clamp(u, 0, 1);
        return u * u * (3 - 2 * u);
    }

    /** The feet on path tick k (1..pathLen). */
    private static void pathAt(int k, double[] out) {
        double u = k / (double) pathLen;
        double h, v;
        switch (pathKind) {
            case LOWER -> { // out over the edge first, then down to the hold
                h = smooth(u / 0.4);
                v = smooth((u - 0.4) / 0.6);
            }
            case CLIMB -> { // up the face first, then over the lip
                h = smooth((u - 0.5) / 0.5);
                double r = Math.min(1, u / 0.55);
                v = 1 - (1 - r) * (1 - r);
                out[0] = ax + (bx - ax) * h;
                out[1] = ay + (by + 0.02 - ay) * v - (k >= pathLen ? 0.02 : 0);
                out[2] = az + (bz - az) * h;
                return;
            }
            case SIDE -> {
                h = smooth(u);
                out[0] = ax + (bx - ax) * h;
                out[1] = ay + (by - ay) * h + 0.45 * Mth.sin((float) (u * Math.PI));
                out[2] = az + (bz - az) * h;
                return;
            }
            case CORNER -> { // round the corner point at a body's clearance
                double a0 = Math.atan2(az - cz, ax - cx), a1 = Math.atan2(bz - cz, bx - cx);
                double da = Mth.wrapDegrees((a1 - a0) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
                double r0 = Math.hypot(ax - cx, az - cz), r1 = Math.hypot(bx - cx, bz - cz);
                double s = smooth(u), a = a0 + da * s, r = Math.max(0.45, r0 + (r1 - r0) * s);
                if (k >= pathLen) {
                    out[0] = bx;
                    out[2] = bz;
                } else {
                    out[0] = cx + Math.cos(a) * r;
                    out[2] = cz + Math.sin(a) * r;
                }
                out[1] = ay + (by - ay) * s;
                return;
            }
            default -> { // GRAB, UP: settle onto the hold
                h = smooth(u);
                v = smooth(u);
            }
        }
        out[0] = ax + (bx - ax) * h;
        out[1] = ay + (by - ay) * v;
        out[2] = az + (bz - az) * h;
    }

    // ---- the server's word, resets ----------------------------------------------------------------------------------

    static void refused(Move move) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        ClientMoves.trace("server ended " + move);
        switch (move) {
            case WALL_RUN -> endRun(p, false, "refused");
            case LEDGE_HANG, SHIMMY -> {
                if (hanging()) endHang(p, false, "refused");
            }
            case LEDGE_JUMP -> {
                if (state == State.PATH) state = State.NONE;
            }
            default -> {
            }
        }
    }

    static void stop(LocalPlayer p, String why) {
        endRun(p, true, why);
        endHang(p, true, why);
        if (state == State.PATH) state = State.NONE;
        state = State.NONE;
    }

    /** The dev driver starts a script, or the player was replaced: everything lets go at once. */
    static void reset() {
        state = State.NONE;
        edge = null;
        pathEdge = null;
        shimmying = false;
        turnLeft = 0;
        links = 0;
        wallCount = 0;
        chainUntil = -100;
        regrabAt = 0;
        edgeHold = 0;
    }

    static float wallYaw() {
        RunnerRules.Edge e = state == State.PATH ? pathEdge : edge;
        return e == null ? 0 : yawOf(e.fx(), e.fz());
    }

    // ---- what the probes cost ---------------------------------------------------------------------------------------

    /** Ring of the last 512 ticks' runner cost, logged in autopilot runs every 400 ticks. No allocation. */
    static final class Cost {
        private static final long[] NS = new long[512], SORT = new long[512];
        private static int at, n, sinceLog;
        private static long lookups, probes, missesAt;

        static void add(long ns, long look, long misses, long probe) {
            NS[at] = ns;
            at = (at + 1) % NS.length;
            if (n < NS.length) n++;
            lookups += look;
            probes += probe;
            if (++sinceLog >= 400) {
                System.arraycopy(NS, 0, SORT, 0, n);
                java.util.Arrays.sort(SORT, 0, n);
                ClientMoves.trace(String.format("[runner-cost] %d ticks: p50 %.1f us, p99 %.1f us, max %.1f us; %.1f probes and %.1f block lookups a tick (%.1f misses)",
                        sinceLog, SORT[n / 2] / 1e3, SORT[Math.min(n - 1, (int) (n * 0.99))] / 1e3, SORT[n - 1] / 1e3,
                        probes / (double) sinceLog, lookups / (double) sinceLog, (misses - missesAt) / (double) sinceLog));
                sinceLog = 0;
                lookups = probes = 0;
                missesAt = misses;
            }
        }
    }
}
