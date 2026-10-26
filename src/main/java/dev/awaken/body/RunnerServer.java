package dev.awaken.body;

import dev.awaken.Awaken;
import dev.awaken.body.net.BodyPayloads;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.Later;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Spike S3 (throwaway): the server half of the Runner's Kit, in the body's pattern. Each start is checked from a
 * position the server itself saw (the claimed start must be one of its last {@link RunnerRules#HISTORY} positions;
 * "now" mode checks from the current position instead, like the mantle), refused with {@code Refuse} when it doesn't
 * hold, and a refused jump is teleported back. Runs need wall contact every tick; hangs are re-checked every few ticks.
 * Also: the latency knob, the test course and the bench that counts refusals and corrections.
 */
final class RunnerServer {
    private RunnerServer() {}

    /** Debug knob: ticks the server waits before handling a runner start or end (100 ms = 2 ticks). */
    static int latencyTicks;
    /** Check geometry from the claimed start (validated against history), or from where the server has the body now. */
    static boolean fromHistory = true;

    static void receive(ServerPlayer p, Runnable r) {
        if (latencyTicks <= 0) r.run();
        else Later.run(p.level().getServer(), latencyTicks, () -> {
            if (!p.isRemoved()) r.run();
        });
    }

    private static final BlockPos.MutableBlockPos M = new BlockPos.MutableBlockPos();

    // ---- starts -----------------------------------------------------------------------------------------------------

    static void onRun(ServerPlayer p, BodyPayloads.Run msg) {
        Move move = Move.byId(msg.move());
        if (move == null || !move.runner()) return;
        BodyMotion m = BodyServer.motion(p);
        if (!Body.unlocked(p, move)) { // the world turned the Runner's Kit off
            refuse(p, m, move, false);
            return;
        }
        long now = BodyServer.now(p);
        if (bench != null && bench.target == move.ordinal() && (bench.flag < 0 || bench.flag == msg.flag())) m.tStarted = true;
        if (move == Move.WALL_RUN) m.tRuns++;
        if (move == Move.LEDGE_HANG) m.tGrabs++;
        boolean ok = !BodyServer.off(p) && !p.isInLiquid() && switch (move) {
            case WALL_RUN -> startRun(p, m, msg, now);
            case WALL_JUMP -> wallJump(p, m, msg, now);
            case LEDGE_HANG -> startHang(p, m, msg, now);
            case SHIMMY -> shimmy(p, m, msg, now);
            case LEDGE_JUMP -> ledgeJump(p, m, msg, now);
            case WALL_CLIMB -> startClimb(p, m, msg, now);
            case GRIP -> grip(p, m, msg, now);
            default -> false;
        };
        if (bench != null && bench.target == move.ordinal()) {
            if (ok) m.tAccepted = true;
            else m.tRefused = true;
        }
        if (!ok) refuse(p, m, move, move == Move.WALL_JUMP || move == Move.LEDGE_JUMP && msg.flag() != BodyPayloads.JUMP_BACK);
        else Awaken.LOG.debug("[runner] {} accepted", move);
    }

    /** The feet to check from: the claim when the server saw the body there lately, else null (refused). */
    private static double[] from(ServerPlayer p, BodyMotion m, BodyPayloads.Run msg) {
        if (!fromHistory) return new double[]{p.getX(), p.getY(), p.getZ()};
        double x = msg.sx(), y = msg.sy(), z = msg.sz();
        double dx = p.getX() - x, dz = p.getZ() - z;
        boolean near = dx * dx + dz * dz <= RunnerRules.CLAIM_TOL * RunnerRules.CLAIM_TOL && Math.abs(p.getY() - y) <= RunnerRules.CLAIM_TOL;
        return near || m.saw(x, y, z, RunnerRules.CLAIM_TOL) ? new double[]{x, y, z} : null;
    }

    static boolean startRun(ServerPlayer p, BodyMotion m, BodyPayloads.Run msg, long now) {
        if (m.hanging) return false;
        double[] s = from(p, m, msg);
        if (s == null) return false;
        CollisionContext ctx = CollisionContext.of(p);
        RunnerRules.Wall w = RunnerRules.findWall(p.level(), ctx, M, s[0], s[1], s[2], msg.yaw(), RunnerRules.RUN_REACH + RunnerRules.SERVER_TOL);
        if (w == null || w.axis() != msg.axis() || w.side() != msg.sign() || Math.abs(w.plane() - msg.ty()) > 0.1) return false;
        if (RunnerRules.groundWithin(p.level(), ctx, M, s[0], s[1], s[2], RunnerRules.RUN_MIN_HEIGHT - 0.25)) return false;
        int link = msg.flag();
        if (link == 0) {
            if (m.ranWall(w.id())) return false; // never the same wall twice before landing
        } else {
            // a link follows an accepted wall jump, onto a wall other than the one just left
            if (link != m.links || m.links > RunnerRules.links(Body.runnerRank(p)) || w.id() == m.lastWall || now - m.lastWallJumpAt > 40) return false;
        }
        m.runUntil = now + RunnerRules.runLength(link, Body.runnerRank(p), Body.wallStride(p)) + RunnerRules.RUN_GRACE
                + p.getData(dev.awaken.body.ascent.AscentContent.MEMORY).level(dev.awaken.body.ascent.AscentMemory.Track.RUNNER);
        m.runAxis = w.axis();
        m.runSide = w.side();
        m.runPlane = w.plane();
        m.runMiss = 0;
        m.lastWall = w.id();
        m.addWall(w.id());
        p.resetFallDistance();
        p.causeFoodExhaustion(0.05F);
        BodyServer.didClip(p, m, Move.WALL_RUN, msg.yaw(), BodyContent.WALLSTEP, 0.6F, now,
                BodyClips.wallOnLeft(msg.yaw(), w.axis(), w.side()) ? BodyClips.WALLRUN_L : BodyClips.WALLRUN_R);
        return true;
    }

    /**
     * Wall Stride's vertical run: with the power, a wall in front where the body was (once a wall before landing).
     * The server keeps it as a run (its window, its wall) so a kick off it is a wall jump like any other.
     */
    static boolean startClimb(ServerPlayer p, BodyMotion m, BodyPayloads.Run msg, long now) {
        if (m.hanging || !Body.wallStride(p)) return false;
        double[] s = from(p, m, msg);
        if (s == null) return false;
        RunnerRules.Wall w = RunnerRules.wallOn(p.level(), CollisionContext.of(p), M, s[0], s[1], s[2], msg.axis(), msg.sign(),
                RunnerRules.CLIMB_REACH + RunnerRules.SERVER_TOL, 0, 0);
        if (w == null || Math.abs(w.plane() - msg.ty()) > 0.1 || m.ranWall(w.id())) return false;
        m.runUntil = now + RunnerRules.CLIMB_TICKS + RunnerRules.RUN_GRACE;
        m.runAxis = w.axis();
        m.runSide = w.side();
        m.runPlane = w.plane();
        m.runMiss = 0;
        m.lastWall = w.id();
        m.addWall(w.id());
        p.resetFallDistance();
        p.causeFoodExhaustion(0.05F);
        BodyServer.didClip(p, m, Move.WALL_CLIMB, msg.yaw(), BodyContent.WALLSTEP, 0.6F, now, BodyClips.WALLCLIMB);
        return true;
    }

    static boolean wallJump(ServerPlayer p, BodyMotion m, BodyPayloads.Run msg, long now) {
        if (m.runUntil == 0 && now - m.runEndedAt > RunnerRules.RUN_GRACE) return false;
        double[] s = from(p, m, msg);
        if (s == null) return false;
        // a wall behind you at the moment of the jump: the run's own wall (or one on the same side)
        RunnerRules.Wall w = RunnerRules.wallOn(p.level(), CollisionContext.of(p), M, s[0], s[1], s[2], msg.axis(), msg.sign(),
                RunnerRules.RUN_REACH + RunnerRules.SERVER_TOL, 0, 0);
        if (w == null) return false;
        if (m.links >= RunnerRules.links(Body.runnerRank(p))) return false;
        endRun(m, now);
        m.links++;
        m.lastWallJumpAt = now;
        p.causeFoodExhaustion(0.2F);
        BodyServer.didClip(p, m, Move.WALL_JUMP, msg.yaw(), BodyContent.WALLJUMP, 0.8F, now, BodyClips.WALLJUMP);
        return true;
    }

    static boolean startHang(ServerPlayer p, BodyMotion m, BodyPayloads.Run msg, long now) {
        double[] s = from(p, m, msg);
        if (s == null) return false;
        CollisionContext ctx = CollisionContext.of(p);
        RunnerRules.Edge e;
        if (msg.flag() == BodyPayloads.HANG_LOWER) {
            // lowered over the edge the feet stood on: the edge is at the feet, the hold below it
            if (Math.abs(msg.ty() - (s[1] - RunnerRules.HANG_DROP)) > 0.3) return false;
            e = RunnerRules.edgeNear(p.level(), ctx, M, msg.tx(), msg.ty(), msg.tz(), msg.axis(), msg.sign(), s[1], 0.3, 0.1 + RunnerRules.SERVER_TOL);
            // a real drop under the hold (the column in front of the face)
            if (e != null && !RunnerRules.lowerWorth(p.level(), ctx, M, e)) return false;
        } else if (msg.flag() == BodyPayloads.HANG_EAVE) {
            // from a climb line's top up to the lip over it (SPEC §17.25): only from a grip held or just let go
            if (!m.gripping && now - m.gripEndedAt > 6) return false;
            e = GripRules.topLip(p.level(), ctx, M, s[0], s[1], s[2], msg.axis(), msg.sign(), RunnerRules.SERVER_TOL);
        } else {
            e = RunnerRules.findEdge(p.level(), ctx, M, s[0], s[1], s[2], msg.axis(), msg.sign(), RunnerRules.HANG_REACH + RunnerRules.SERVER_TOL,
                    RunnerRules.HANG_LO - 0.3, RunnerRules.HANG_HI + 0.3, 0, 0.2);
        }
        if (e == null || !sameHold(e, msg)) return false;
        hang(p, m, e, now);
        boolean lower = msg.flag() == BodyPayloads.HANG_LOWER;
        BodyServer.didClip(p, m, Move.LEDGE_HANG, msg.yaw(), lower ? BodyContent.SHIMMY : BodyContent.GRAB, lower ? 0.5F : 0.8F, now,
                lower ? BodyClips.LOWER : BodyClips.GRAB);
        settleIntoHang(p, m, 8);
        return true;
    }

    private static boolean sameHold(RunnerRules.Edge e, BodyPayloads.Run msg) {
        double dx = e.hx() - msg.tx(), dz = e.hz() - msg.tz();
        return dx * dx + dz * dz <= 1.0 && Math.abs(e.hy() - msg.ty()) <= 0.25;
    }

    private static void hang(ServerPlayer p, BodyMotion m, RunnerRules.Edge e, long now) {
        endRun(m, now);
        m.hanging = true;
        m.hangAxis = e.axis();
        m.hangSign = e.sign();
        m.hangTop = e.top();
        m.hangMiss = 0;
        m.hangSince = now;
        m.hangQuietUntil = now + 6;
        m.hangX = e.hx();
        m.hangY = e.hy();
        m.hangZ = e.hz();
        m.hangExplained = false;
        m.links = 0; // a hang is a landing for the chain
        m.wallCount = 0;
        p.resetFallDistance();
    }

    static boolean shimmy(ServerPlayer p, BodyMotion m, BodyPayloads.Run msg, long now) {
        if (!m.hanging) return false;
        if (msg.flag() == BodyPayloads.SHIMMY_CORNER) {
            RunnerRules.Edge e = RunnerRules.edgeNear(p.level(), CollisionContext.of(p), M, msg.tx(), msg.ty(), msg.tz(), msg.axis(), msg.sign(),
                    m.hangTop, 0.35, 0.15 + RunnerRules.SERVER_TOL);
            if (e == null || !sameHold(e, msg)) {
                endHang(m, now);
                AwakenNet.send(p, new BodyPayloads.Refuse(Move.LEDGE_HANG.ordinal()));
                return false;
            }
            m.hangAxis = e.axis();
            m.hangSign = e.sign();
            m.hangTop = e.top();
            m.hangX = e.hx();
            m.hangY = e.hy();
            m.hangZ = e.hz();
            m.hangQuietUntil = now + 12;
            return true;
        }
        m.shimmying = true;
        BodyServer.didClip(p, m, Move.SHIMMY, msg.yaw(), BodyContent.SHIMMY, 0.4F, now,
                msg.flag() == BodyPayloads.SHIMMY_START_RIGHT ? BodyClips.SHIMMY_R : BodyClips.SHIMMY_L);
        return true;
    }

    static boolean ledgeJump(ServerPlayer p, BodyMotion m, BodyPayloads.Run msg, long now) {
        if (!m.hanging && now - m.hangEndedAt > 6) return false;
        CollisionContext ctx = CollisionContext.of(p);
        int way = msg.flag();
        if (way == BodyPayloads.JUMP_BACK) {
            endHang(m, now);
            BodyServer.didClip(p, m, Move.LEDGE_JUMP, msg.yaw(), BodyContent.WALLJUMP, 0.7F, now, BodyClips.LEDGE_BACK);
            return true;
        }
        if (way == BodyPayloads.JUMP_CLIMB) {
            RunnerRules.Edge here = RunnerRules.edgeNear(p.level(), ctx, M, msg.sx(), msg.sy(), msg.sz(), m.hangAxis, m.hangSign, m.hangTop, 0.3,
                    0.1 + RunnerRules.SERVER_TOL);
            if (here == null) return false;
            double[] stand = RunnerRules.climbOut(p.level(), ctx, M, here);
            if (stand == null) return false;
            double dx = stand[0] - msg.tx(), dz = stand[2] - msg.tz();
            if (dx * dx + dz * dz > 1.0 || Math.abs(stand[1] - msg.ty()) > 0.25) return false;
            endHang(m, now);
            BodyServer.didClip(p, m, Move.LEDGE_JUMP, msg.yaw(), BodyContent.CLIMB, 0.8F, now, BodyClips.MANTLE);
            return true;
        }
        if (way == BodyPayloads.JUMP_OVER) {
            // over a fence's or gate's rail from a hang on it (SPEC §17.27): the same crossing from the hold, landing where
            // the client's does
            RunnerRules.Edge here = RunnerRules.edgeNear(p.level(), ctx, M, msg.sx(), msg.sy(), msg.sz(), m.hangAxis, m.hangSign, m.hangTop, 0.3,
                    0.1 + RunnerRules.SERVER_TOL);
            if (here == null || RunnerRules.climbOut(p.level(), ctx, M, here) != null) return false;
            VaultRules.Vault v = VaultRules.over(p.level(), ctx, here.hx(), here.hy(), here.hz(), here.fx(), here.fz(), here.top());
            if (v == null) return false;
            double dx = v.endX() - msg.tx(), dz = v.endZ() - msg.tz();
            if (dx * dx + dz * dz > VaultRules.LAND_TOL * VaultRules.LAND_TOL || Math.abs(v.floor() - msg.ty()) > 0.3) return false;
            endHang(m, now);
            BodyServer.vaultFrom(p, m, v, msg.yaw(), now);
            return true;
        }
        // up, sideways or down: the target edge is there (found again within a block) and within the jump's (or the drop's) reach
        double topLo = way == BodyPayloads.JUMP_UP ? m.hangTop + 0.4 : way == BodyPayloads.JUMP_DOWN ? m.hangTop - RunnerRules.DROP_MAX - 0.25 : m.hangTop - 1.25;
        double topHi = way == BodyPayloads.JUMP_UP ? m.hangTop + RunnerRules.ledgeUp(Body.runnerRank(p)) + 0.25
                : way == BodyPayloads.JUMP_DOWN ? m.hangTop - RunnerRules.DROP_MIN + 0.25 : m.hangTop + 1.25;
        double ty = msg.ty() + RunnerRules.HANG_DROP;
        if (ty < topLo || ty > topHi) return false;
        RunnerRules.Edge e = RunnerRules.edgeNear(p.level(), ctx, M, msg.tx(), msg.ty(), msg.tz(), m.hangAxis, m.hangSign, ty, 0.3,
                0.15 + RunnerRules.SERVER_TOL);
        if (e == null || !sameHold(e, msg)) return false;
        double along = m.hangAxis == 0 ? msg.tz() - msg.sz() : msg.tx() - msg.sx();
        if (way == BodyPayloads.JUMP_SIDE && Math.abs(along) > RunnerRules.ledgeSide(Body.runnerRank(p)) + 0.25) return false;
        hang(p, m, e, now);
        m.hangQuietUntil = now + 20; // the path between the two edges
        String clip = BodyClips.LEDGE_UP;
        if (way == BodyPayloads.JUMP_SIDE) {
            // left or right as you face the wall: the facing is +sign along the hang's axis; your left, facing +z, is +x
            double fx = m.hangAxis == 0 ? m.hangSign : 0, fz = m.hangAxis == 2 ? m.hangSign : 0;
            double dx = msg.tx() - msg.sx(), dz = msg.tz() - msg.sz();
            clip = dx * fz - dz * fx > 0 ? BodyClips.LEDGE_SIDE_L : BodyClips.LEDGE_SIDE_R;
        } else if (way == BodyPayloads.JUMP_DOWN) {
            clip = BodyClips.GRAB; // the catch on the ledge below
        }
        BodyServer.didClip(p, m, Move.LEDGE_JUMP, msg.yaw(), BodyContent.GRAB, 0.7F, now, clip);
        settleIntoHang(p, m, 9);
        return true;
    }

    /** Others see the catch (a grab, a lower, a ledge jump) settle into the hanging loop once it has played out. */
    private static void settleIntoHang(ServerPlayer p, BodyMotion m, int ticks) {
        long since = m.hangSince;
        Later.run(p.level().getServer(), ticks, () -> {
            if (!p.isRemoved() && m.hanging && m.hangSince == since && !m.shimmying) BodyServer.showClip(p, BodyClips.HANG, p.getYRot());
        });
    }

    // ---- the grip on a climb line (SPEC §17.25) ------------------------------------------------------------------------

    /** The clip others see for a grip flag. */
    static String gripClip(int flag) {
        return switch (flag) {
            case BodyPayloads.GRIP_CLIMB -> BodyClips.PIPE_CLIMB;
            case BodyPayloads.GRIP_CLIMB_FAST -> BodyClips.PIPE_CLIMB_FAST;
            case BodyPayloads.GRIP_SLIDE -> BodyClips.PIPE_SLIDE;
            case BodyPayloads.GRIP_HOP -> BodyClips.PIPE_HOP;
            case BodyPayloads.GRIP_KICK -> BodyClips.PIPE_KICK;
            case BodyPayloads.GRIP_SIDE_L -> BodyClips.PIPE_SIDE_L;
            case BodyPayloads.GRIP_SIDE_R -> BodyClips.PIPE_SIDE_R;
            case BodyPayloads.GRIP_LAND -> BodyClips.PIPE_LAND;
            default -> BodyClips.PIPE_HOLD;
        };
    }

    /**
     * A grip's Run: the first one takes a line (a climb line in the claimed column, where the server saw the body
     * lately); later ones say what the hands do now (others see its clip); the last leaves the line.
     */
    static boolean grip(ServerPlayer p, BodyMotion m, BodyPayloads.Run msg, long now) {
        int flag = msg.flag();
        if (!m.gripping) {
            if (flag != BodyPayloads.GRIP_HOLD && flag != BodyPayloads.GRIP_CLIMB && flag != BodyPayloads.GRIP_CLIMB_FAST) return false;
            double[] s = from(p, m, msg);
            if (s == null) return false;
            GripRules.Line l = GripRules.find(p.level(), M, s[0], s[1], s[2], msg.yaw());
            if (l == null || l.bx() != (int) Math.floor(msg.tx()) || l.bz() != (int) Math.floor(msg.tz())) return false;
            endRun(m, now);
            endHang(m, now);
            m.gripping = true;
            m.gripBx = l.bx();
            m.gripBz = l.bz();
            m.gripSince = now;
            m.gripMiss = 0;
            m.links = 0; // a grip is a landing for the chain
            m.wallCount = 0;
            p.resetFallDistance();
            BodyServer.didClip(p, m, Move.GRIP, msg.yaw(), BodyContent.GRAB, 0.5F, now, gripClip(flag));
            return true;
        }
        switch (flag) {
            case BodyPayloads.GRIP_KICK, BodyPayloads.GRIP_SIDE_L, BodyPayloads.GRIP_SIDE_R, BodyPayloads.GRIP_LAND -> {
                endGrip(m, now);
                m.lastMove = now;
                BodyServer.showClip(p, gripClip(flag), msg.yaw());
            }
            default -> {
                m.lastMove = now;
                BodyServer.showClip(p, gripClip(flag), msg.yaw());
            }
        }
        return true;
    }

    private static void endGrip(BodyMotion m, long now) {
        if (!m.gripping) return;
        m.gripping = false;
        m.gripEndedAt = now;
        m.lastMove = now;
    }

    static void onEnd(ServerPlayer p, Move move) {
        BodyMotion m = p.getExistingDataOrNull(BodyContent.MOTION);
        if (m == null) return;
        long now = BodyServer.now(p);
        switch (move) {
            case WALL_RUN, WALL_CLIMB -> {
                endRun(m, now);
                BodyServer.showRunnerEnd(p);
            }
            case LEDGE_HANG -> {
                endHang(m, now);
                BodyServer.showRunnerEnd(p);
            }
            case SHIMMY -> {
                m.shimmying = false;
                if (m.hanging) BodyServer.showClip(p, BodyClips.HANG, p.getYRot());
            }
            case GRIP -> {
                endGrip(m, now);
                BodyServer.showRunnerEnd(p);
            }
            default -> {
            }
        }
    }

    private static void endRun(BodyMotion m, long now) {
        if (m.runUntil == 0) return;
        m.runUntil = 0;
        m.runEndedAt = now;
        m.lastMove = now;
    }

    private static void endHang(BodyMotion m, long now) {
        if (!m.hanging) return;
        m.hanging = false;
        m.shimmying = false;
        m.hangEndedAt = now;
        m.lastMove = now;
    }

    /** Stop the client's move; a refused jump goes back to where the server has the body. */
    static void refuse(ServerPlayer p, BodyMotion m, Move move, boolean teleport) {
        AwakenNet.send(p, new BodyPayloads.Refuse(move.ordinal()));
        Awaken.LOG.info("[runner] t{} refused {}{}", BodyServer.now(p), move, teleport ? " (teleported back)" : "");
        if (teleport && p.connection != null) {
            p.connection.teleport(p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
            m.tCorrected = true;
        }
    }

    // ---- ticking ----------------------------------------------------------------------------------------------------

    /** Every player, every tick: the position history and the landing that resets the chain. No allocation. */
    static void track(ServerPlayer p, BodyMotion m) {
        m.record(p.getX(), p.getY(), p.getZ());
        if (p.onGround() && m.runUntil == 0) {
            m.links = 0;
            m.wallCount = 0;
        }
    }

    /** One tick of a run or a hang in progress (also driven by GameTests). */
    static void tick(ServerPlayer p, BodyMotion m, long now) {
        if (BodyServer.off(p) || p.isInLiquid()) {
            if (m.runUntil != 0 || m.hanging || m.gripping) {
                endRun(m, now);
                endHang(m, now);
                endGrip(m, now);
            }
            return;
        }
        CollisionContext ctx = CollisionContext.of(p);
        if (m.runUntil != 0) {
            p.resetFallDistance();
            if (now >= m.runUntil) {
                endRun(m, now);
            } else {
                m.lastMove = now;
                // wall contact on every tick, from where the server has the body
                RunnerRules.Wall w = RunnerRules.wallOn(p.level(), ctx, M, p.getX(), p.getY(), p.getZ(), m.runAxis, m.runSide,
                        RunnerRules.RUN_REACH + RunnerRules.SERVER_TOL, 0, 0);
                boolean touching = w != null && Math.abs(w.plane() - m.runPlane) < 0.1;
                m.runMiss = touching ? 0 : m.runMiss + 1;
                if (m.runMiss > RunnerRules.RUN_MISS) {
                    endRun(m, now);
                    m.tEnded = true;
                    AwakenNet.send(p, new BodyPayloads.Refuse(Move.WALL_RUN.ordinal()));
                    Awaken.LOG.info("[runner] t{} ended WALL_RUN (no wall contact)", now);
                }
            }
        }
        gripTick(p, m, now);
        if (m.hanging) {
            // the server runs a player's physics between its packets (26.3: AUTHORITATIVE_SIDE_AND_SERVER), and a still,
            // hanging client only sends a reminder every 20 ticks: without this its copy here sinks under gravity
            // (S3's "server Y 0.58 low after an upward grab")
            p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            p.resetFallDistance();
            p.causeFoodExhaustion(RunnerRules.HANG_EXHAUSTION);
            m.lastMove = now;
            if (now >= m.hangQuietUntil && (now - m.hangSince) % RunnerRules.HANG_CHECK == 0) {
                m.hangMiss = hangHolds(p, m, ctx) ? 0 : m.hangMiss + 1;
                if (m.hangMiss >= RunnerRules.HANG_MISS) {
                    endHang(m, now);
                    m.tEnded = true;
                    AwakenNet.send(p, new BodyPayloads.Refuse(Move.LEDGE_HANG.ordinal()));
                    Awaken.LOG.info("[runner] t{} ended LEDGE_HANG (no edge at the server's position)", now);
                }
            }
        }
    }

    /** One tick of a grip: the body held where the client says it is, no fall damage, and the line still there. */
    static void gripTick(ServerPlayer p, BodyMotion m, long now) {
        if (!m.gripping) return;
        // as a hang: the server's own physics would sink a still climber's copy between its packets
        p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        p.resetFallDistance();
        p.causeFoodExhaustion(GripRules.EXHAUSTION);
        m.lastMove = now;
        if ((now - m.gripSince) % GripRules.CHECK != 0) return;
        m.gripMiss = GripRules.stillHolds(p.level(), M, m.gripBx, m.gripBz, p.getX(), p.getY(), p.getZ()) ? 0 : m.gripMiss + 1;
        if (m.gripMiss >= GripRules.MISS) {
            endGrip(m, now);
            m.tEnded = true;
            AwakenNet.send(p, new BodyPayloads.Refuse(Move.GRIP.ordinal()));
            Awaken.LOG.info("[runner] t{} ended GRIP (no climb line at the server's position)", now);
        }
    }

    /** An edge still under the hands at the server's position (its own way first, then the other three: a corner). */
    static boolean hangHolds(ServerPlayer p, BodyMotion m, CollisionContext ctx) {
        if (RunnerRules.edgeNear(p.level(), ctx, M, p.getX(), p.getY(), p.getZ(), m.hangAxis, m.hangSign, p.getY() + RunnerRules.HANG_DROP, 0.4,
                RunnerRules.HANG_REACH) != null) return true;
        for (int i = 0; i < 4; i++) {
            int axis = i < 2 ? 0 : 2, sign = (i & 1) == 0 ? 1 : -1;
            if (axis == m.hangAxis && sign == m.hangSign) continue;
            RunnerRules.Edge e = RunnerRules.edgeNear(p.level(), ctx, M, p.getX(), p.getY(), p.getZ(), axis, sign, p.getY() + RunnerRules.HANG_DROP, 0.4,
                    RunnerRules.HANG_REACH);
            if (e != null) {
                m.hangAxis = axis;
                m.hangSign = sign;
                return true;
            }
        }
        // spike diagnostics: say once per hang why the server's own position has no edge, then fall back to the hold it
        // accepted (only with the body within 2 blocks of it: still an edge the server found, the body near it)
        if (!m.hangExplained) {
            m.hangExplained = true;
            Awaken.LOG.info("[runner] hang re-check at the server's position ({}, {}, {}) fails: {}; accepted hold ({}, {}, {}) gives: {}",
                    String.format("%.3f", p.getX()), String.format("%.3f", p.getY()), String.format("%.3f", p.getZ()),
                    RunnerRules.whyNot(p.level(), ctx, M, p.getX(), p.getY(), p.getZ(), m.hangAxis, m.hangSign, RunnerRules.HANG_REACH,
                            RunnerRules.HANG_DROP - 0.4, RunnerRules.HANG_DROP + 0.4),
                    String.format("%.3f", m.hangX), String.format("%.3f", m.hangY), String.format("%.3f", m.hangZ),
                    RunnerRules.whyNot(p.level(), ctx, M, m.hangX, m.hangY, m.hangZ, m.hangAxis, m.hangSign, RunnerRules.HANG_REACH,
                            RunnerRules.HANG_DROP - 0.4, RunnerRules.HANG_DROP + 0.4));
        }
        double dx = p.getX() - m.hangX, dy = p.getY() - m.hangY, dz = p.getZ() - m.hangZ;
        if (dx * dx + dy * dy + dz * dz <= 4.0 && RunnerRules.edgeNear(p.level(), ctx, M, m.hangX, m.hangY, m.hangZ, m.hangAxis, m.hangSign,
                m.hangY + RunnerRules.HANG_DROP, 0.4, RunnerRules.HANG_REACH) != null) {
            m.hangFallbacks++;
            return true;
        }
        return false;
    }

    // ---- the test course (/awaken body course) ----------------------------------------------------------------------

    static BlockPos course = BlockPos.ZERO;
    static boolean courseBuilt;

    /**
     * Builds the S3 course with its floor top at {@code o} (feet level), facing +z. Lane A: two parallel walls 4 apart
     * with a 2-high launch platform (wall-run, wall jumps). Shaft S: two walls with trims 3 apart (back jumps). The
     * warehouse W: a face with a low trim (gap, corner wrap), an upper trim, a crane arm and a crate; its roof for the
     * sneak-lower. Accident lanes: a 2-wide alley, an alley with crates, a wall with a low fence (never run on, grabbed or
     * crossed: it's beside you, SPEC §17.27), a 2-high garden wall.
     */
    static void build(ServerLevel level, BlockPos o) {
        course = o.immutable();
        courseBuilt = true;
        BlockState floor = Blocks.SMOOTH_STONE.defaultBlockState(), wall = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState trim = Blocks.DARK_OAK_PLANKS.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        BlockState beam = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
        fill(level, o, -16, -1, -4, 42, -1, 44, floor);
        fill(level, o, -16, 0, -4, 42, 16, 44, air);
        // lane A: walls x = -13 and x = -8 (faces -12 and -8), 8 high, z 0..40; launch platform x -12..-9, z 0..7, 2 high
        fill(level, o, -13, 0, 0, -13, 7, 40, wall);
        fill(level, o, -8, 0, 0, -8, 7, 40, wall);
        fill(level, o, -12, 0, 0, -9, 1, 7, Blocks.POLISHED_ANDESITE.defaultBlockState());
        // shaft S: walls x = -6 and x = 0 (faces -5 and 0), z 0..4, 8 high; trims: west face -4 top +3, east face -1 top +4
        fill(level, o, -6, 0, 0, -6, 7, 4, wall);
        fill(level, o, 0, 0, 0, 0, 7, 4, wall);
        fill(level, o, -5, 2, 0, -5, 2, 4, trim);
        fill(level, o, -1, 3, 0, -1, 3, 4, trim);
        // warehouse W: x 2..14, z 20..24, 11 high (roof top +11); front trim (z 19, top +3) x 2..7 and 9..14 (gap at 8),
        // wrapping the east corner (x 15, z 19..24); upper trim x 2..5 (top +5); crane arm x 11, z 12..18, top +4; crate
        fill(level, o, 2, 0, 20, 14, 10, 24, wall);
        fill(level, o, 2, 2, 19, 7, 2, 19, trim);
        fill(level, o, 9, 2, 19, 14, 2, 19, trim);
        fill(level, o, 15, 2, 19, 15, 2, 24, trim);
        fill(level, o, 2, 4, 19, 5, 4, 19, trim);
        fill(level, o, 11, 3, 12, 11, 3, 18, beam);
        fill(level, o, 9, 0, 15, 9, 0, 15, Blocks.SPRUCE_PLANKS.defaultBlockState());
        // accident lanes (6 clear of the warehouse's corner): alley (walls 22, 25); alley with crates (walls 27, 30,
        // crates at z 8 and 17); wall with a low fence (wall 32, fence 33); garden wall 2 high (x 37)
        fill(level, o, 22, 0, 0, 22, 3, 30, wall);
        fill(level, o, 25, 0, 0, 25, 3, 30, wall);
        fill(level, o, 27, 0, 0, 27, 3, 30, wall);
        fill(level, o, 30, 0, 0, 30, 3, 30, wall);
        fill(level, o, 28, 0, 8, 29, 0, 8, Blocks.SPRUCE_PLANKS.defaultBlockState());
        fill(level, o, 28, 0, 17, 29, 0, 17, Blocks.SPRUCE_PLANKS.defaultBlockState());
        fill(level, o, 32, 0, 0, 32, 2, 30, wall);
        fill(level, o, 33, 0, 0, 33, 0, 30, Blocks.OAK_FENCE.defaultBlockState());
        fill(level, o, 37, 0, 0, 37, 1, 30, wall);
    }

    /** Clears the whole course again (GameTests share their level: nothing may be left for later tests to land on). */
    static void clear(ServerLevel level, BlockPos o) {
        fill(level, o, -16, -1, -4, 42, 16, 44, Blocks.AIR.defaultBlockState());
    }

    private static void fill(ServerLevel level, BlockPos o, int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) {
            level.setBlock(m.set(o.getX() + x, o.getY() + y, o.getZ() + z), s, Block.UPDATE_CLIENTS);
        }
        // fences join their neighbours only with shape updates: set them again with the neighbours in place
        if (s.is(Blocks.OAK_FENCE)) {
            for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) {
                m.set(o.getX() + x, o.getY() + y, o.getZ() + z);
                level.setBlock(m, Block.updateFromNeighbourShapes(s, level, m), Block.UPDATE_CLIENTS);
            }
        }
    }

    // ---- the bench (/awaken body bench) -----------------------------------------------------------------------------

    /** A scripted trial: where it starts (from the course origin), which move it measures, how long it runs. */
    record Spec(String script, double x, double y, double z, float yaw, int target, int flag, int ticks) {}

    static final Spec[] SPECS = {
            new Spec("wallrun", -11.68, 2, 0.5, 0, Move.WALL_RUN.ordinal(), 0, 70),
            new Spec("walljump", -11.68, 2, 0.5, 0, Move.WALL_JUMP.ordinal(), -1, 75),
            new Spec("chain", -11.68, 2, 0.5, 0, Move.WALL_JUMP.ordinal(), -1, 120),
            new Spec("hang", 4.5, 0, 18.2, 0, Move.LEDGE_HANG.ordinal(), BodyPayloads.HANG_GRAB, 40),
            new Spec("shimmy", 3.5, 0, 18.2, 0, Move.SHIMMY.ordinal(), BodyPayloads.SHIMMY_START, 56),
            new Spec("ledgeup", 3.5, 0, 18.2, 0, Move.LEDGE_JUMP.ordinal(), BodyPayloads.JUMP_UP, 50),
            new Spec("climb", 13.5, 0, 18.2, 0, Move.LEDGE_JUMP.ordinal(), BodyPayloads.JUMP_CLIMB, 42),
            new Spec("ledgeside", 7.3, 0, 18.2, 0, Move.LEDGE_JUMP.ordinal(), BodyPayloads.JUMP_SIDE, 50),
            new Spec("ledgeback", -3.0, 0, 2.0, 90, Move.LEDGE_JUMP.ordinal(), BodyPayloads.JUMP_BACK, 56),
            new Spec("lower", 5.5, 11, 19.85, 180, Move.LEDGE_HANG.ordinal(), BodyPayloads.HANG_LOWER, 36),
            new Spec("crane", 10.0, 1, 15.5, -90, Move.SHIMMY.ordinal(), -1, 96),
            // accident lanes: sprint and hold jump hugging a wall (the worst case: the middle of a 2-wide alley is out of
            // reach of both walls), the view wobbling; any wall-run or grab is unwanted
            new Spec("alley", 23.35, 0, 0.5, 0, -1, -1, 54),
            new Spec("alleycrate", 28.35, 0, 0.5, 0, -1, -1, 54),
            new Spec("fence", 33.95, 0, 0.5, 0, -1, -1, 54),
            new Spec("gardenwall", 38.35, 0, 0.5, 0, -1, -1, 54),
            new Spec("alleycratespam", 28.35, 0, 0.5, 0, -1, -1, 54),
    };

    static Spec spec(String name) {
        for (Spec s : SPECS) if (s.script.equals(name)) return s;
        return null;
    }

    static final class Bench {
        final ServerPlayer p;
        final String[] scripts;
        final int count;
        int si, trial;
        int target = -1, flag = -1;
        // per script
        int noStart, accepted, refused, ended, corrected, unwantedRuns, unwantedGrabs, unwantedVaults, fallbacks;

        Bench(ServerPlayer p, String[] scripts, int count) {
            this.p = p;
            this.scripts = scripts;
            this.count = count;
        }
    }

    static Bench bench;
    /** Benches waiting their turn (a tour queues them all at once; they run back to back on the server's clock). */
    private static final java.util.ArrayDeque<Runnable> QUEUE = new java.util.ArrayDeque<>();

    /** The wall-run trigger the next benches tell the client to use (the "press" option). */
    static boolean airPress;

    static int startBench(ServerPlayer p, String what, int count, int latencyMs, boolean history, boolean press) {
        if (bench != null) {
            if (spec(what) == null && !what.equals("all") && !what.equals("jumps") && !what.equals("accident")) return 0;
            QUEUE.add(() -> startBench(p, what, count, latencyMs, history, press));
            Awaken.LOG.info("[runner-bench] queued {} x{} at {} ms", what, count, latencyMs);
            return 1;
        }
        String[] scripts = switch (what) {
            case "all" -> new String[]{"wallrun", "walljump", "hang", "shimmy", "ledgeup", "climb", "ledgeside", "ledgeback", "lower"};
            case "jumps" -> new String[]{"ledgeup", "climb", "ledgeside", "ledgeback"};
            case "accident" -> new String[]{"alley", "alleycrate", "fence", "gardenwall", "alleycratespam"};
            default -> spec(what) == null ? new String[0] : new String[]{what};
        };
        if (scripts.length == 0 || !courseBuilt) return 0;
        latencyTicks = Math.round(latencyMs / 50F);
        fromHistory = history;
        airPress = press;
        AwakenNet.send(p, new BodyPayloads.Try(indexOf(press ? "runpress" : "runheld"), 0));
        bench = new Bench(p, scripts, count);
        int total = 0;
        for (String s : scripts) total += count * (spec(s).ticks + 14);
        Awaken.LOG.info("[runner-bench] start {} x{} latency {} ms ({} ticks), checks from {}, run trigger {}, ~{} ticks", what, count, latencyMs,
                latencyTicks, history ? "history" : "now", press ? "press" : "held", total);
        Later.run(p.level().getServer(), 2, () -> trial(bench));
        return total;
    }

    private static void trial(Bench b) {
        if (bench != b || b.p.isRemoved()) return;
        if (b.trial >= b.count) {
            report(b);
            b.si++;
            b.trial = 0;
            b.noStart = b.accepted = b.refused = b.ended = b.corrected = b.unwantedRuns = b.unwantedGrabs = b.unwantedVaults = b.fallbacks = 0;
            if (b.si >= b.scripts.length) {
                bench = null;
                Runnable next = QUEUE.poll();
                Awaken.LOG.info("[runner-bench] done{}", next == null ? " (queue empty)" : ", next");
                if (next != null) next.run();
                return;
            }
        }
        Spec s = spec(b.scripts[b.si]);
        ServerPlayer p = b.p;
        BodyMotion m = BodyServer.motion(p);
        long now = BodyServer.now(p);
        // a clean start: the client lets go of everything, the server forgets its moves
        AwakenNet.send(p, new BodyPayloads.Try(7, 0));
        endRun(m, now);
        endHang(m, now);
        m.links = 0;
        m.wallCount = 0;
        b.target = s.target;
        b.flag = s.flag;
        BlockPos o = course;
        p.connection.teleport(o.getX() + s.x, o.getY() + s.y, o.getZ() + s.z, s.yaw, 10F);
        p.setDeltaMovement(0, 0, 0);
        p.resetFallDistance();
        p.getFoodData().setFoodLevel(20);
        int what = indexOf(s.script);
        Later.run(p.level().getServer(), 8, () -> {
            if (bench != b) return;
            m.tStarted = m.tAccepted = m.tRefused = m.tEnded = m.tCorrected = false;
            m.hangFallbacks = 0;
            m.tRuns = m.tGrabs = m.tVaults = 0;
            AwakenNet.send(p, new BodyPayloads.Try(what, 0));
        });
        Later.run(p.level().getServer(), 8 + s.ticks + 6, () -> {
            if (bench != b) return;
            if (s.target < 0) {
                if (m.tRuns > 0) b.unwantedRuns++;
                if (m.tGrabs > 0) b.unwantedGrabs++;
                if (m.tVaults > 0) b.unwantedVaults++; // a fence beside you is never crossed (SPEC §17.27)
            } else if (!m.tStarted) b.noStart++;
            else if (m.tRefused) b.refused++;
            else b.accepted++;
            if (m.tEnded) b.ended++;
            if (m.hangFallbacks > 0) b.fallbacks++;
            if (m.tCorrected) b.corrected++;
            b.trial++;
            trial(b);
        });
    }

    private static int indexOf(String script) {
        if (script.equals("alleycratespam")) script = "alleyspam";
        for (int i = 0; i < BodyPayloads.TRIES.length; i++) if (BodyPayloads.TRIES[i].equals(script)) return i;
        // the accident lanes share one script
        for (int i = 0; i < BodyPayloads.TRIES.length; i++) if (BodyPayloads.TRIES[i].equals("alley")) return i;
        return 7;
    }

    private static void report(Bench b) {
        String s = b.scripts[b.si];
        String line = spec(s).target < 0
                ? String.format("%s latency %d ms, trigger %s: %d passes, unwanted wall-runs %d, unwanted grabs %d, unwanted vaults %d", s,
                latencyTicks * 50, airPress ? "press" : "held", b.count, b.unwantedRuns, b.unwantedGrabs, b.unwantedVaults)
                : String.format("%s latency %d ms (%s, trigger %s): %d trials, accepted %d, refused %d, no start %d, ended by server %d, corrected %d, hang fallbacks %d",
                s, latencyTicks * 50, fromHistory ? "history" : "now", airPress ? "press" : "held", b.count, b.accepted, b.refused, b.noStart, b.ended,
                b.corrected, b.fallbacks);
        Awaken.LOG.info("[runner-bench] {}", line);
        b.p.sendSystemMessage(Component.literal("[bench] " + line));
    }
}
