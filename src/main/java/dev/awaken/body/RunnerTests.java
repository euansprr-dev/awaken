package dev.awaken.body;

import dev.awaken.Awaken;
import dev.awaken.body.net.BodyPayloads;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

/**
 * Spike S3 GameTests: the Runner's Kit probes and server checks on the very course the S3 tour runs
 * ({@link RunnerServer#build}), the hang against the floating kick, the run's numbers, and what the probes cost.
 */
final class RunnerTests {
    private RunnerTests() {}

    static void register() {
        AwakenTests.register("runner_numbers", 20, RunnerTests::numbers);
        AwakenTests.registerIsolated("runner_course_probes", 120, RunnerTests::courseProbes);
        AwakenTests.registerIsolated("runner_server_checks", 120, RunnerTests::serverChecks);
        AwakenTests.registerIsolated("runner_wall_climb", 120, RunnerTests::wallClimb);
        AwakenTests.registerIsolated("runner_hang_no_float_kick", 400, RunnerTests::hangNoFloatKick);
        AwakenTests.registerIsolated("runner_probe_cost", 200, RunnerTests::probeCost);
        AwakenTests.register("runner_roof_climb", 20, RunnerTests::roofClimb);
        AwakenTests.registerIsolated("runner_pipe_grip", 120, RunnerTests::pipeGrip);
        AwakenTests.registerIsolated("runner_pipe_cornice", 120, RunnerTests::pipeCornice);
        AwakenTests.registerIsolated("runner_ledge_drop", 120, RunnerTests::ledgeDrop);
        AwakenTests.registerIsolated("runner_pipe_tops", 120, RunnerTests::pipeTops);
        AwakenTests.registerIsolated("runner_rail_hang", 120, RunnerTests::railHang);
        // runner_hang_real_tick (hangRealTick below) is kept for W2 but not registered: its harness doesn't yet get its own
        // teleport accepted, so every move after it is ignored (see docs/carillon/spikes/S3-runner.md, Open issue)
    }

    private static final BlockPos.MutableBlockPos M = new BlockPos.MutableBlockPos();

    /** The course's origin (feet level) for a test: up in the air, clear of the terrain other tests stand on. */
    private static BlockPos origin(GameTestHelper h) {
        return h.absolutePos(new BlockPos(20, 1, 6)).above(80);
    }

    private static void finish(GameTestHelper h, BlockPos o, ServerPlayer... ps) {
        RunnerServer.clear(h.getLevel(), o);
        BodyTests.done(h, ps);
    }

    private static Vec3 at(BlockPos o, double x, double y, double z) {
        return new Vec3(o.getX() + x, o.getY() + y, o.getZ() + z);
    }

    // ---- numbers ----------------------------------------------------------------------------------------------------

    /** A fresh run from level flight ends ~2 blocks lower over 35 ticks (~11 blocks along); links shrink 35, 28, 22, 18, 14. */
    static void numbers(GameTestHelper h) {
        double v = 0, y = 0;
        for (int t = 0; t < RunnerRules.RUN_TICKS; t++) {
            v += (RunnerRules.runTarget(t, RunnerRules.RUN_TICKS, RunnerRules.RUN_GRIP) - v) * RunnerRules.RUN_APPROACH;
            y += v;
        }
        h.assertTrue(y < -1.9 && y > -2.8, "a run from level flight ends about 2 lower, was " + String.format("%.2f", y));
        h.assertTrue(Math.abs(RunnerRules.RUN_SPEED_MIN * RunnerRules.RUN_TICKS - 10.5) < 1.0, "about 11 blocks along at the run's floor speed");
        int[] want = {35, 28, 22, 18, 14};
        for (int i = 0; i < want.length; i++) h.assertTrue(RunnerRules.runLength(i) == want[i], "link " + i + " runs " + want[i] + ", was " + RunnerRules.runLength(i));
        h.assertTrue(RunnerRules.runTarget(0, 35, 10) > 0 && RunnerRules.runTarget(10, 35, 10) == 0 && Math.abs(RunnerRules.runTarget(35, 35, 10) - RunnerRules.RUN_SINK) < 1e-9,
                "the grip eases from +0.06 to 0, the sink reaches -0.26");
        h.succeed();
    }

    // ---- probes on the course -----------------------------------------------------------------------------------------

    static void courseProbes(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = origin(h);
        RunnerServer.build(level, o);
        h.startSequence().thenIdle(2).thenExecute(() -> {
            CollisionContext ctx = CollisionContext.empty();
            // lane A: the west wall beside the body, facing along it; 30° off still runs, 40° doesn't
            Vec3 a = at(o, -11.68, 2.5, 14);
            RunnerRules.Wall w = RunnerRules.findWall(level, ctx, M, a.x, a.y, a.z, 0F, RunnerRules.RUN_REACH);
            h.assertTrue(w != null && w.axis() == 0 && w.side() == -1 && Math.abs(w.plane() - (o.getX() - 12)) < 1e-6 && w.gap() < 0.05,
                    "the west wall is found beside the body: " + w);
            h.assertTrue(RunnerRules.findWall(level, ctx, M, a.x, a.y, a.z, 30F, RunnerRules.RUN_REACH) != null, "30° off the wall still runs");
            h.assertTrue(RunnerRules.findWall(level, ctx, M, a.x, a.y, a.z, 40F, RunnerRules.RUN_REACH) == null, "40° off the wall doesn't");
            Vec3 mid = at(o, -10.0, 2.5, 14);
            h.assertTrue(RunnerRules.findWall(level, ctx, M, mid.x, mid.y, mid.z, 0F, RunnerRules.RUN_REACH) == null, "the middle of the lane is out of reach of both walls");
            h.assertFalse(RunnerRules.groundWithin(level, ctx, M, a.x, a.y, a.z, RunnerRules.RUN_MIN_HEIGHT), "2.5 up is high enough");
            Vec3 low = at(o, -11.68, 1.2, 14);
            h.assertTrue(RunnerRules.groundWithin(level, ctx, M, low.x, low.y, low.z, RunnerRules.RUN_MIN_HEIGHT), "1.2 up is too low to run");
            // the 2-high garden wall can't cover a body 1.6 up (needs two blocks of wall beside you)
            Vec3 g = at(o, 38.32, 1.6, 10);
            h.assertTrue(RunnerRules.findWall(level, ctx, M, g.x, g.y, g.z, 0F, RunnerRules.RUN_REACH) == null, "a 2-high wall is too short to run on 1.6 up");
            // the fence lane: the fence is no wall, and the wall behind it is out of reach
            Vec3 f = at(o, 33.95, 1.6, 10);
            h.assertTrue(RunnerRules.findWall(level, ctx, M, f.x, f.y, f.z, 0F, RunnerRules.RUN_REACH) == null, "a low fence is no wall to run on");

            // warehouse trim 1a (top +3) from a jump's height under the upper trim
            Vec3 j = at(o, 4.5, 0.9, 18.6);
            RunnerRules.Edge e = RunnerRules.findEdge(level, ctx, M, j.x, j.y, j.z, 2, 1, RunnerRules.HANG_REACH, RunnerRules.HANG_LO, RunnerRules.HANG_HI, 0, 0.2);
            h.assertTrue(e != null && Math.abs(e.top() - (o.getY() + 3)) < 1e-6 && Math.abs(e.hz() - (o.getZ() + 19 - 0.32)) < 1e-6,
                    "the trim is a hang, the hold flush with its face: " + e);
            h.assertTrue(RunnerRules.climbOut(level, ctx, M, e) == null, "no climbing out under the upper trim (one block of room)");
            RunnerRules.Edge up = RunnerRules.findEdge(level, ctx, M, e.hx(), e.hy(), e.hz(), 2, 1, 0.6, RunnerRules.HANG_DROP + 0.4,
                    RunnerRules.HANG_DROP + RunnerRules.LEDGE_UP, 0, 0.2);
            h.assertTrue(up != null && Math.abs(up.top() - (o.getY() + 5)) < 1e-6, "the upper trim is a ledge jump up (+2): " + up);
            // the open part of trim 1b: climb out
            Vec3 c = at(o, 13.5, 1.15, 18.68);
            RunnerRules.Edge open = RunnerRules.edgeNear(level, ctx, M, c.x, c.y, c.z, 2, 1, o.getY() + 3, 0.3, 0.2);
            double[] stand = open == null ? null : RunnerRules.climbOut(level, ctx, M, open);
            h.assertTrue(stand != null && Math.abs(stand[1] - (o.getY() + 3)) < 1e-6, "trim 1b is open above: climb out onto it");
            // a fence on the floor you stand on is no hang (it's crossed, SPEC §17.27), and a plain face has no edge
            Vec3 fw = at(o, 33.95, 0.5, 10);
            h.assertTrue(RunnerRules.findEdge(level, ctx, M, fw.x, fw.y, fw.z, 0, -1, RunnerRules.HANG_REACH, RunnerRules.HANG_LO, RunnerRules.HANG_HI, 0, 0.2) == null,
                    "a low fence beside you is no hang");
            Vec3 face = at(o, -11.68, 2, 14);
            h.assertTrue(RunnerRules.findEdge(level, ctx, M, face.x, face.y, face.z, 0, -1, RunnerRules.HANG_REACH, RunnerRules.HANG_LO, RunnerRules.HANG_HI, 0, 0.2) == null,
                    "a plain wall face has no edge");
            // the corner of trim 1b: past its east end, the side trim's face is found facing -x
            Vec3 q = at(o, 16.63, 1.15, 19.32);
            RunnerRules.Edge corner = RunnerRules.findEdge(level, ctx, M, q.x, q.y, q.z, 0, -1, 0.35, 1.55, 2.15, 0, 0.2);
            h.assertTrue(corner != null && Math.abs(corner.hx() - (o.getX() + 16.32)) < 1e-6, "round the outside corner onto the side trim: " + corner);
            // sneak-lower off the roof's front edge (drop 11); the launch platform (drop 2) is too low
            Vec3 roof = at(o, 5.5, 11, 19.75);
            RunnerRules.Edge lower = RunnerRules.lowerEdge(level, ctx, M, roof.x, roof.y, roof.z, 180F);
            h.assertTrue(lower != null && lower.sign() == 1 && Math.abs(lower.hy() - (roof.y - RunnerRules.HANG_DROP)) < 1e-6, "the roof edge lowers into a hang: " + lower);
            Vec3 plat = at(o, -10.5, 2, 8.0); // at the platform's very edge: only the drop (2) says no
            h.assertTrue(RunnerRules.lowerEdge(level, ctx, M, plat.x, plat.y, plat.z, 0F) == null, "a 2-block drop is no reason to hang");
            // edge snap: falling at a 0.25 step lifts onto it; a 0.5 one doesn't (that is a jump)
            BlockPos snow = o.offset(0, 0, -3), slab = o.offset(2, 0, -3);
            level.setBlockAndUpdate(snow, net.minecraft.world.level.block.Blocks.SNOW.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, 3));
            level.setBlockAndUpdate(slab, net.minecraft.world.level.block.Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
            Vec3 f1 = at(o, 0.5, 0.05, -3.31), f2 = at(o, 2.5, 0.05, -3.31);
            double lift = RunnerRules.snapLift(level, ctx, M, f1.x, f1.y, f1.z, 0, 0.2);
            h.assertTrue(Math.abs(lift - 0.202) < 0.01, "falling at a 0.25 step snaps up 0.2, was " + lift);
            h.assertTrue(Double.isNaN(RunnerRules.snapLift(level, ctx, M, f2.x, f2.y, f2.z, 0, 0.2)), "a half-block step is out of the snap's reach");
            finish(h, o);
        });
    }

    // ---- the server's checks ------------------------------------------------------------------------------------------

    static BodyPayloads.Run run(Move move, int flag, float yaw, Vec3 s, double tx, double ty, double tz, int axis, int sign) {
        return new BodyPayloads.Run(move.ordinal(), flag, yaw, s.x, s.y, s.z, tx, ty, tz, axis, sign);
    }

    /**
     * Wall Stride's vertical run (SPEC §17.2): the rules (a wall in front only when facing it, about three blocks of
     * rise), and the server: refused without the power, accepted with it on a real wall in front, never the same wall
     * twice before landing, and a kick off it is a wall jump.
     */
    static void wallClimb(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = origin(h);
        RunnerServer.build(level, o);
        RunnerServer.fromHistory = true;
        RunnerServer.latencyTicks = 0;
        h.startSequence().thenIdle(2).thenExecute(() -> {
            double rise = 0;
            for (int t = 0; t < RunnerRules.CLIMB_TICKS; t++) rise += RunnerRules.climbTarget(t);
            h.assertTrue(rise > 3.4 && rise < 4.0 && RunnerRules.climbTarget(RunnerRules.CLIMB_TICKS) == 0, "a climb rises " + rise);
            long now = h.getLevel().getServer().overworld().getGameTime();
            Vec3 s = at(o, -11.68, 2.5, 14);
            double plane = o.getX() - 12;
            var ctx = net.minecraft.world.phys.shapes.CollisionContext.empty();
            var m0 = new BlockPos.MutableBlockPos();
            RunnerRules.Wall ahead = RunnerRules.wallAhead(level, ctx, m0, s.x, s.y, s.z, 90F, RunnerRules.CLIMB_REACH);
            h.assertTrue(ahead != null && ahead.axis() == 0 && ahead.side() == -1, "the west wall is ahead facing west: " + ahead);
            h.assertTrue(RunnerRules.wallAhead(level, ctx, m0, s.x, s.y, s.z, 0F, RunnerRules.CLIMB_REACH) == null, "facing along it, no wall ahead");
            ServerPlayer a = BodyTests.player(h, Rank.D);
            a.snapTo(s.x, s.y, s.z, 90F, 0F);
            a.setOnGround(false);
            BodyMotion m = BodyServer.motion(a);
            h.assertFalse(RunnerServer.startClimb(a, m, run(Move.WALL_CLIMB, 0, 90F, s, 0, plane, 0, 0, -1), now), "no climb without Wall Stride");
            dev.awaken.powers.Powers.learn(a, net.minecraft.resources.Identifier.parse(RunnerRules.STRIDE_POWER), 0);
            h.assertTrue(Body.wallStride(a), "Wall Stride learned");
            h.assertTrue(RunnerServer.startClimb(a, m, run(Move.WALL_CLIMB, 0, 90F, s, 0, plane, 0, 0, -1), now), "a climb on a real wall in front");
            h.assertTrue(RunnerServer.wallJump(a, m, run(Move.WALL_JUMP, 0, 90F, s, 0, plane, 0, 0, -1), now + 4), "a kick off the climb");
            h.assertFalse(RunnerServer.startClimb(a, m, run(Move.WALL_CLIMB, 0, 90F, s, 0, plane, 0, 0, -1), now + 8), "never the same wall twice");
            Vec3 mid = at(o, -10.0, 2.5, 14);
            ServerPlayer b = BodyTests.player(h, Rank.D);
            dev.awaken.powers.Powers.learn(b, net.minecraft.resources.Identifier.parse(RunnerRules.STRIDE_POWER), 0);
            b.snapTo(mid.x, mid.y, mid.z, 90F, 0F);
            h.assertFalse(RunnerServer.startClimb(b, BodyServer.motion(b), run(Move.WALL_CLIMB, 0, 90F, mid, 0, plane, 0, 0, -1), now),
                    "a climb claimed away from the wall");
            h.getLevel().getServer().getPlayerList().remove(a);
            h.getLevel().getServer().getPlayerList().remove(b);
        }).thenSucceed();
    }

    static void serverChecks(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = origin(h);
        RunnerServer.build(level, o);
        RunnerServer.fromHistory = true;
        RunnerServer.latencyTicks = 0;
        h.startSequence().thenIdle(2).thenExecute(() -> {
            long now = h.getLevel().getServer().overworld().getGameTime();
            // a run on the west wall: accepted once, never the same wall twice before landing
            ServerPlayer a = BodyTests.player(h, null);
            Vec3 s = at(o, -11.68, 2.5, 14);
            a.snapTo(s.x, s.y, s.z, 0F, 0F);
            a.setOnGround(false);
            BodyMotion m = BodyServer.motion(a);
            double plane = o.getX() - 12;
            h.assertTrue(RunnerServer.startRun(a, m, run(Move.WALL_RUN, 0, 0F, s, 0, plane, 0, 0, -1), now), "a run on a real wall is accepted");
            RunnerServer.onEnd(a, Move.WALL_RUN);
            h.assertFalse(RunnerServer.startRun(a, m, run(Move.WALL_RUN, 0, 0F, s, 0, plane, 0, 0, -1), now + 5), "never the same wall twice before landing");
            // a link without a wall jump, and a claimed wall that isn't there
            ServerPlayer b = BodyTests.player(h, null);
            Vec3 mid = at(o, -10.0, 2.5, 14);
            b.snapTo(mid.x, mid.y, mid.z, 0F, 0F);
            BodyMotion mb = BodyServer.motion(b);
            h.assertFalse(RunnerServer.startRun(b, mb, run(Move.WALL_RUN, 0, 0F, mid, 0, plane, 0, 0, -1), now), "a run claimed in the lane's middle is refused");
            // a claimed start far from anywhere the server saw the body is refused even where a wall is
            h.assertFalse(RunnerServer.startRun(b, mb, run(Move.WALL_RUN, 0, 0F, s, 0, plane, 0, 0, -1), now), "a start the server never saw is refused");
            // wall jump during the run, a link onto the east wall, then the fifth link is refused
            ServerPlayer c = BodyTests.player(h, null);
            c.snapTo(s.x, s.y, s.z, 0F, 0F);
            BodyMotion mc = BodyServer.motion(c);
            h.assertTrue(RunnerServer.startRun(c, mc, run(Move.WALL_RUN, 0, 0F, s, 0, plane, 0, 0, -1), now), "run");
            h.assertTrue(RunnerServer.wallJump(c, mc, run(Move.WALL_JUMP, 0, 0F, s, 0, plane, 0, 0, -1), now + 8), "a wall jump with the wall behind");
            Vec3 east = at(o, -8.32, 3.2, 17);
            c.snapTo(east.x, east.y, east.z, 0F, 0F);
            h.assertTrue(RunnerServer.startRun(c, mc, run(Move.WALL_RUN, 1, 0F, east, 0, o.getX() - 8, 0, 0, 1), now + 14), "link 1 on the east wall");
            Vec3 midAir = at(o, -10.0, 3.0, 20);
            c.snapTo(midAir.x, midAir.y, midAir.z, 0F, 0F);
            h.assertFalse(RunnerServer.wallJump(c, mc, run(Move.WALL_JUMP, 1, 0F, midAir, 0, o.getX() - 8, 0, 0, 1), now + 20), "a wall jump with no wall behind is refused");
            // four links used: the next kick off a wall is refused (land first)
            mc.links = RunnerRules.LINKS;
            Vec3 west = new Vec3(s.x, s.y, s.z + 10);
            c.snapTo(west.x, west.y, west.z, 0F, 0F);
            h.assertFalse(RunnerServer.wallJump(c, mc, run(Move.WALL_JUMP, RunnerRules.LINKS, 0F, west, 0, plane, 0, 0, -1), now + 22), "no fifth wall jump");
            // hang on the trim: accepted; a claimed hang where there's no edge is refused; up to the upper trim
            ServerPlayer d = BodyTests.player(h, null);
            Vec3 jump = at(o, 3.5, 0.9, 18.6);
            d.snapTo(jump.x, jump.y, jump.z, 0F, 0F);
            BodyMotion md = BodyServer.motion(d);
            Vec3 hold = at(o, 3.5, 3 - RunnerRules.HANG_DROP, 19 - 0.32);
            h.assertTrue(RunnerServer.startHang(d, md, run(Move.LEDGE_HANG, BodyPayloads.HANG_GRAB, 0F, jump, hold.x, hold.y, hold.z, 2, 1), now), "a grab at the trim");
            h.assertTrue(md.hanging, "the server holds the hang");
            d.snapTo(hold.x, hold.y, hold.z, 0F, 0F);
            Vec3 upHold = at(o, 3.5, 5 - RunnerRules.HANG_DROP, 19 - 0.32);
            h.assertFalse(RunnerServer.ledgeJump(d, md, run(Move.LEDGE_JUMP, BodyPayloads.JUMP_UP, 0F, hold, upHold.x, upHold.y + 2, upHold.z, 2, 1), now + 10),
                    "a ledge jump to a ledge that isn't there is refused");
            h.assertTrue(RunnerServer.ledgeJump(d, md, run(Move.LEDGE_JUMP, BodyPayloads.JUMP_UP, 0F, hold, upHold.x, upHold.y, upHold.z, 2, 1), now + 12),
                    "up to the upper trim");
            ServerPlayer e = BodyTests.player(h, null);
            Vec3 bare = at(o, -11.68, 2, 14);
            e.snapTo(bare.x, bare.y, bare.z, 0F, 0F);
            h.assertFalse(RunnerServer.startHang(e, BodyServer.motion(e), run(Move.LEDGE_HANG, BodyPayloads.HANG_GRAB, 90F, bare, bare.x, bare.y, bare.z, 0, -1), now),
                    "a hang claimed on a bare wall is refused");
            finish(h, o, a, b, c, d, e);
        });
    }

    // ---- the floating kick ------------------------------------------------------------------------------------------

    /**
     * Vanilla kicks a player whose client reports floating (not descending, not standing) for 80 ticks, but only when
     * {@code noBlocksAround}: the box grown 1/16 (and 0.55 down) is all air. A hang is flush with its face (2/100 off),
     * so that is never true. Here a body hangs on the trim and on the crane arm (air under it) for 220 ticks with the
     * server's own hang check running; the predicate is vanilla's own, called on the server's box every tick. The
     * GameTest server allows flight (vanilla's kick can't fire here), so the predicate is the evidence; a body 0.6 off
     * the same face is the control.
     */
    static void hangNoFloatKick(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = origin(h);
        RunnerServer.build(level, o);
        int[] floating = new int[2], ticks = {0};
        ServerPlayer[] ps = new ServerPlayer[2];
        Vec3[] holds = new Vec3[2];
        float[] food = new float[2];
        h.startSequence().thenIdle(2).thenExecute(() -> {
            // the trim (face below it) and the crane arm (air under the beam: the face is only the beam's own block)
            holds[0] = at(o, 3.5, 3 - RunnerRules.HANG_DROP, 19 - 0.32);
            holds[1] = at(o, 11 - 0.32, 4 - RunnerRules.HANG_DROP, 15.5);
            float[] yaws = {0F, -90F};
            int[][] dirs = {{2, 1}, {0, 1}};
            for (int i = 0; i < 2; i++) {
                ServerPlayer p = BodyTests.player(h, null);
                Vec3 v = holds[i];
                p.snapTo(v.x, v.y, v.z, yaws[i], 0F);
                p.setOnGround(false);
                BodyMotion m = BodyServer.motion(p);
                ps[i] = p;
                food[i] = p.getFoodData().getSaturationLevel() + p.getFoodData().getFoodLevel();
                h.assertTrue(RunnerServer.startHang(p, m, run(Move.LEDGE_HANG, BodyPayloads.HANG_GRAB, yaws[i], v, v.x, v.y, v.z, dirs[i][0], dirs[i][1]),
                        BodyServer.now(p)), "hang " + i + " accepted: " + RunnerRules.whyNot(level, net.minecraft.world.phys.shapes.CollisionContext.of(p),
                        new BlockPos.MutableBlockPos(), v.x, v.y, v.z, dirs[i][0], dirs[i][1], RunnerRules.HANG_REACH + RunnerRules.SERVER_TOL,
                        RunnerRules.HANG_LO - 0.3, RunnerRules.HANG_HI + 0.3));
            }
        }).thenExecuteFor(220, () -> {
            ticks[0]++;
            for (int i = 0; i < 2; i++) {
                ServerPlayer p = ps[i];
                if (p == null) continue;
                Vec3 v = holds[i];
                p.snapTo(v.x, v.y, v.z, p.getYRot(), 0F); // the client holds the body there
                p.setDeltaMovement(0, 0, 0);
                BodyMotion m = BodyServer.motion(p);
                RunnerServer.tick(p, m, BodyServer.now(p) + ticks[0]);
                p.getFoodData().tick(p); // the server doesn't tick test players: turn the exhaustion into hunger here
                if (RunnerRules.noBlocksAround(level, p.getBoundingBox())) floating[i]++;
            }
        }).thenExecute(() -> {
            h.assertTrue(ticks[0] >= 200, "hung for 200+ ticks (" + ticks[0] + ")");
            for (int i = 0; i < 2; i++) {
                h.assertTrue(floating[i] == 0, "hang " + i + " looked like floating on " + floating[i] + " ticks");
                h.assertTrue(BodyServer.motion(ps[i]).hanging, "the server kept hang " + i + " the whole time");
            }
            // the control: the same body 0.6 out from the trim's face is floating by vanilla's rule
            Vec3 off = holds[0].add(0, 0, -0.6);
            AABB box = ps[0].getBoundingBox().move(off.subtract(ps[0].position()));
            h.assertTrue(RunnerRules.noBlocksAround(level, box), "0.6 off the face counts as floating (the control)");
            // hunger at the sprint's rate
            float after = ps[0].getFoodData().getSaturationLevel() + ps[0].getFoodData().getFoodLevel();
            h.assertTrue(after < food[0], "hanging costs hunger (" + food[0] + " -> " + after + ")");
            // vanilla's own reset is public, in case W2 wants belt and braces
            if (ps[0].connection != null) ps[0].connection.resetFlyingTicks();
            Awaken.LOG.info("[runner] float kick: {} ticks hanging on a trim and on a crane arm, noBlocksAround true on {} / {} ticks; control 0.6 off: floating",
                    ticks[0], floating[0], floating[1]);
            finish(h, o, ps);
        });
    }

    // ---- the hang through the server's real tick ----------------------------------------------------------------------

    /** Puts a test player at a spot the way the server does (a teleport the client accepts). */
    private static void place(ServerPlayer p, Vec3 v) {
        p.connection.teleport(v.x, v.y, v.z, 0F, 0F);
        // the teleport's id is the listener's own; try ids until one lands (the accepted one moves the body by 1e-3)
        for (int id = 0; id < 64 && Math.abs(p.getX() - v.x) < 1e-4; id++) {
            p.connection.handleAcceptTeleportPacket(new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket(id, v.x + 1e-3, v.y, v.z, 0F, 0F));
        }
    }

    /** One client tick as the server sees it: the position packet, the tick's end, then the connection's own tick. */
    private static void clientTick(ServerPlayer p, Vec3 v) {
        if (v != null) p.connection.handleMovePlayer(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos(v.x, v.y, v.z, false, false));
        p.connection.handleClientTickEnd(net.minecraft.network.protocol.game.ServerboundClientTickEndPacket.INSTANCE);
        p.connection.tick();
    }

    /**
     * The grab exactly as the smoke tour's client did it (jump, grab mid-jump, a 3-tick ease onto the hold, then still),
     * fed through vanilla's movement handler and the connection's own tick (so PlayerTickEvent and the body's server tick
     * run as in a game). The hang must hold 40 ticks.
     */
    static void hangRealTick(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = origin(h);
        RunnerServer.build(level, o);
        ServerPlayer[] ps = new ServerPlayer[1];
        Vec3[] path = {at(o, 4.5, 0.42, 18.27), at(o, 4.5, 0.75, 18.37), at(o, 4.5, 0.854, 18.45), at(o, 4.5, 1.046, 18.60), at(o, 4.5, 1.15, 18.68)};
        int[] k = {0};
        h.startSequence().thenIdle(2).thenExecute(() -> {
            ServerPlayer p = BodyTests.player(h, null);
            ps[0] = p;
            place(p, at(o, 4.5, 0, 18.2));
            Awaken.LOG.info("[runner] real tick: placed at {}", p.position());
        }).thenExecuteFor(48, () -> {
            ServerPlayer p = ps[0];
            int i = k[0]++;
            if (i == 1) {
                Vec3 s = path[1], hold = path[4];
                RunnerServer.onRun(p, run(Move.LEDGE_HANG, BodyPayloads.HANG_GRAB, 0F, s, hold.x, hold.y, hold.z, 2, 1));
            }
            clientTick(p, i < path.length ? path[i] : null);
            BodyMotion m = BodyServer.motion(p);
            if (i % 4 == 0) Awaken.LOG.info("[runner] real tick {}: server at ({}, {}, {}), hanging {}", i, String.format("%.3f", p.getX()),
                    String.format("%.3f", p.getY()), String.format("%.3f", p.getZ()), m.hanging);
        }).thenExecute(() -> {
            BodyMotion m = BodyServer.motion(ps[0]);
            h.assertTrue(m.hanging && m.hangFallbacks == 0, "the grab holds through the real server tick (hanging " + m.hanging + ", fallbacks " + m.hangFallbacks + ")");
            finish(h, o, ps);
        });
    }

    // ---- what the probes cost -----------------------------------------------------------------------------------------

    /** The probes a tick can run beside a wall and under a ledge, timed on the server thread: plain level and the window. */
    static void probeCost(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = origin(h);
        RunnerServer.build(level, o);
        h.startSequence().thenIdle(2).thenExecute(() -> {
            CollisionContext ctx = CollisionContext.empty();
            RunnerRules.ProbeWindow win = new RunnerRules.ProbeWindow();
            Vec3 a = at(o, -11.68, 2.5, 14), j = at(o, 4.5, 0.9, 18.6);
            int n = 20000;
            long sink = 0;
            for (int pass = 0; pass < 2; pass++) { // warm up, then measure
                long t0 = System.nanoTime();
                for (int i = 0; i < n; i++) sink += tickProbes(level, ctx, a, j);
                long plain = System.nanoTime() - t0;
                long l0 = win.lookups;
                t0 = System.nanoTime();
                for (int i = 0; i < n; i++) {
                    win.begin(level, a.x, a.y, a.z);
                    sink += tickProbes(win, ctx, a, j);
                }
                long windowed = System.nanoTime() - t0;
                if (pass == 1) Awaken.LOG.info("[runner] probe cost: a tick's probes (wall both sides + ground + edge ahead + climb + up) {} us plain, {} us through the window, {} lookups a tick ({})",
                        String.format("%.2f", plain / 1e3 / n), String.format("%.2f", windowed / 1e3 / n), (win.lookups - l0) / n, sink);
            }
            finish(h, o);
        });
    }

    // ---- roofs ------------------------------------------------------------------------------------------------------

    /**
     * Euan's roofs (SPEC §17.21): from a hang on a roof's eave the climb out lands on the stair's high back half (or the
     * slab) and the slope goes on from there, on Carillon's overhanging eaves too (CityGrammar.roof: the eave stair one
     * out at the eave row, the slope starting over the wall), and the server's re-check agrees; under a beam, no climb.
     */
    static void roofClimb(GameTestHelper h) {
        BlockState stone = Blocks.STONE.defaultBlockState(), tiles = Blocks.DEEPSLATE_TILES.defaultBlockState();
        BlockState slab = Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState slabTop = slab.setValue(SlabBlock.TYPE, SlabType.TOP);
        for (int[] dir : new int[][]{{2, 1}, {0, -1}}) {
            String way = dir[0] == 2 ? " (facing +z)" : " (facing -x)";
            // a stair eave on a 3-high wall, the stair slope going on up from it
            Scene a = new Scene(dir[0], dir[1]).fill(2, 6, 64, 66, stone).put(2, 67, Scene.STAIR);
            a.fill(3, 6, 67, 67, tiles).put(3, 68, Scene.STAIR).fill(4, 6, 68, 68, tiles).put(4, 69, Scene.STAIR);
            roofCheck(h, "stair eave" + way, a, 2, 67.5, 68.0);
            // Carillon's gentle gable: the wall up to the eave row, its stair one out, the slope's first stair over the wall
            Scene g = new Scene(dir[0], dir[1]).fill(2, 6, 64, 67, stone).put(1, 67, Scene.STAIR);
            g.put(2, 68, Scene.STAIR).fill(3, 6, 68, 68, tiles).put(3, 69, Scene.STAIR);
            roofCheck(h, "Carillon's overhanging eave, gentle" + way, g, 1, 67.5, 68.0);
        }
        // a steep gable's overhang: a full step after the eave (the climb lands on the eave stair, the next is a jump)
        Scene st = new Scene(2, 1).fill(2, 6, 64, 67, stone).put(1, 67, Scene.STAIR);
        st.fill(2, 2, 68, 68, tiles).put(2, 69, Scene.STAIR).fill(3, 6, 68, 70, tiles).put(3, 71, Scene.STAIR);
        roofCheck(h, "Carillon's overhanging eave, steep", st, 1, 67.5, 68.0);
        // a slab eave under a slab slope
        Scene s = new Scene(2, 1).fill(2, 6, 64, 66, stone).fill(2, 2, 67, 67, slab).fill(3, 3, 67, 67, slabTop);
        s.fill(4, 6, 67, 67, tiles).fill(4, 4, 68, 68, slab);
        roofCheck(h, "slab eave", s, 2, 67.5, 67.5);
        // a beam a block over the eave: a hang, but no room to stand on the step
        Scene b = new Scene(2, 1).fill(2, 6, 64, 66, stone).put(2, 67, Scene.STAIR).fill(2, 2, 69, 69, stone);
        roofCheck(h, "stair eave under a beam", b, 2, 67.5, Double.NaN);
        h.succeed();
    }

    /** A jump at the face catches the eave at {@code lip}; climbing out stands at {@code stand} (NaN: no climb), as the server finds. */
    private static void roofCheck(GameTestHelper h, String what, Scene s, int u, double lip, double stand) {
        CollisionContext ctx = CollisionContext.empty();
        double face = s.sign * u, along = 1.5, c = face - s.sign * 0.4, py = lip - 1.8;
        double px = s.axis == 0 ? c : along, pz = s.axis == 0 ? along : c;
        RunnerRules.Edge e = RunnerRules.findEdge(s, ctx, M, px, py, pz, s.axis, s.sign, RunnerRules.HANG_REACH, RunnerRules.HANG_LO, RunnerRules.HANG_HI, 0, 0.2);
        h.assertTrue(e != null && Math.abs(e.top() - lip) < 1e-6, what + ": the eave is a hang at " + lip + ": "
                + (e != null ? e : RunnerRules.whyNot(s, ctx, M, px, py, pz, s.axis, s.sign, RunnerRules.HANG_REACH, RunnerRules.HANG_LO, RunnerRules.HANG_HI)));
        double[] out = RunnerRules.climbOut(s, ctx, M, e);
        if (Double.isNaN(stand)) {
            h.assertTrue(out == null, what + ": no climb out, was " + (out == null ? "" : out[1]));
            return;
        }
        h.assertTrue(out != null && Math.abs(out[1] - stand) < 1e-6, what + ": climbs out to stand at " + stand + ", was " + (out == null ? "none" : out[1]));
        double past = ((s.axis == 0 ? out[0] : out[2]) - face) * s.sign;
        h.assertTrue(past > 0.3 && past < 0.5, what + ": the feet land just past the face, " + past);
        // the server's re-check (RunnerServer.ledgeJump, JUMP_CLIMB): the edge found again from the hold, the same stand
        RunnerRules.Edge here = RunnerRules.edgeNear(s, ctx, M, e.hx(), e.hy(), e.hz(), s.axis, s.sign, e.top(), 0.3, 0.1 + RunnerRules.SERVER_TOL);
        double[] again = here == null ? null : RunnerRules.climbOut(s, ctx, M, here);
        h.assertTrue(again != null && Math.abs(again[0] - out[0]) < 1e-6 && Math.abs(again[1] - out[1]) < 1e-6 && Math.abs(again[2] - out[2]) < 1e-6,
                what + ": the server's re-check agrees");
    }

    /**
     * A few blocks held in memory and read like a level (pure geometry: no chunks or light to wait for). Laid out from
     * the climber's side: depth u into the house (the block's near face at sign * u on the axis faced), height y, and a
     * along the face (0..2); its stairs face inward, their high back half away from the climber.
     */
    private static final class Scene implements BlockGetter {
        static final BlockState STAIR = Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState();
        final int axis, sign;
        private final Direction inward;
        private final java.util.Map<Long, BlockState> blocks = new java.util.HashMap<>();

        Scene(int axis, int sign) {
            this.axis = axis;
            this.sign = sign;
            this.inward = axis == 0 ? (sign > 0 ? Direction.EAST : Direction.WEST) : (sign > 0 ? Direction.SOUTH : Direction.NORTH);
        }

        /** One block across the whole face. */
        Scene put(int u, int y, BlockState s) {
            if (s.getBlock() instanceof StairBlock) s = s.setValue(StairBlock.FACING, inward);
            int c = sign > 0 ? u : -u - 1;
            for (int t = 0; t < 3; t++) blocks.put(BlockPos.asLong(axis == 0 ? c : t, y, axis == 0 ? t : c), s);
            return this;
        }

        Scene fill(int u0, int u1, int y0, int y1, BlockState s) {
            for (int u = u0; u <= u1; u++) for (int y = y0; y <= y1; y++) put(u, y, s);
            return this;
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos p) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos p) {
            return blocks.getOrDefault(p.asLong(), Blocks.AIR.defaultBlockState());
        }

        @Override
        public FluidState getFluidState(BlockPos p) {
            return getBlockState(p).getFluidState();
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinY() {
            return -64;
        }
    }

    /** What one airborne tick beside a wall and under a ledge probes at most: the run's wall, its ground, the grab ahead. */
    private static int tickProbes(net.minecraft.world.level.BlockGetter level, CollisionContext ctx, Vec3 a, Vec3 j) {
        int k = 0;
        if (RunnerRules.findWall(level, ctx, M, a.x, a.y, a.z, 0F, RunnerRules.RUN_REACH) != null) k++;
        if (RunnerRules.groundWithin(level, ctx, M, a.x, a.y, a.z, RunnerRules.RUN_MIN_HEIGHT)) k++;
        RunnerRules.Edge e = RunnerRules.findEdge(level, ctx, M, j.x, j.y, j.z, 2, 1, RunnerRules.HANG_REACH, RunnerRules.HANG_LO, RunnerRules.HANG_HI, 0, 0.2);
        if (e != null) {
            k++;
            if (RunnerRules.climbOut(level, ctx, M, e) != null) k++;
            if (RunnerRules.findEdge(level, ctx, M, e.hx(), e.hy(), e.hz(), 2, 1, 0.6, RunnerRules.HANG_DROP + 0.4, RunnerRules.HANG_DROP + RunnerRules.LEDGE_UP, 0, 0.2) != null) k++;
        }
        return k;
    }

    // ---- the grip on a climb line (SPEC §17.28) -----------------------------------------------------------------------

    /**
     * A drainpipe on a wall and a free hoist rope: where the body holds each and which way it faces; the server takes a
     * grip only at a real line where it saw the body, holds it still with no fall damage, lets it go when the line is
     * gone or on a kick; the slide's and the hop's numbers.
     */
    static void pipeGrip(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = origin(h);
        var reg = net.minecraft.core.registries.BuiltInRegistries.BLOCK;
        BlockState pipe = reg.getValue(dev.awaken.Awaken.id("drainpipe")).defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        BlockState rope = reg.getValue(dev.awaken.Awaken.id("rope")).defaultBlockState();
        for (int x = -1; x <= 5; x++) for (int z = -2; z <= 2; z++) level.setBlockAndUpdate(o.offset(x, -1, z), Blocks.STONE.defaultBlockState());
        for (int y = 0; y <= 6; y++) {
            level.setBlockAndUpdate(o.offset(0, y, 1), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(o.offset(0, y, 0), pipe);
            level.setBlockAndUpdate(o.offset(4, y, 0), rope);
        }
        RunnerServer.fromHistory = true;
        RunnerServer.latencyTicks = 0;
        h.startSequence().thenIdle(2).thenExecute(() -> {
            var m0 = new BlockPos.MutableBlockPos();
            // the pipe faces north, so its wall is to the south: the body stands in front of it facing south (+z)
            GripRules.Line l = GripRules.find(level, m0, o.getX() + 0.5, o.getY() + 2, o.getZ() + 0.3, 0F);
            h.assertTrue(l != null && !l.free() && l.fz() == 1 && l.fx() == 0, "a drainpipe is a line against its wall: " + l);
            double front = o.getZ() + 10 / 16.0 - BodyRules.HALF_WIDTH - GripRules.GAP;
            h.assertTrue(Math.abs(l.z() - front) < 1e-6 && Math.abs(l.x() - (o.getX() + 0.5)) < 1e-6, "held just in front of the pipe: " + l);
            h.assertTrue(GripRules.facing(l, 0F) && !GripRules.facing(l, 180F), "taken facing the wall, not with your back to it");
            GripRules.Line r = GripRules.find(level, m0, o.getX() + 4.2, o.getY() + 2, o.getZ() + 0.5, 90F);
            h.assertTrue(r != null && r.free() && r.fx() > 0.99, "a rope is held from the side the body is on: " + r);
            h.assertTrue(GripRules.find(level, m0, o.getX() + 2.5, o.getY() + 2, o.getZ() + 0.5, 0F) == null, "no line between them");

            long now = h.getLevel().getServer().overworld().getGameTime();
            ServerPlayer a = BodyTests.player(h, null);
            Vec3 s = new Vec3(l.x(), o.getY() + 2, l.z());
            a.snapTo(s.x, s.y, s.z, 0F, 0F);
            a.setOnGround(false);
            BodyMotion m = BodyServer.motion(a);
            int axis = 2, sign = 1;
            h.assertFalse(RunnerServer.grip(a, m, run(Move.GRIP, BodyPayloads.GRIP_SLIDE, 0F, s, l.x(), s.y, l.z(), axis, sign), now),
                    "a grip starts holding or climbing, never mid-slide");
            Vec3 bare = new Vec3(o.getX() + 2.5, o.getY() + 2, o.getZ() + 0.5);
            ServerPlayer b = BodyTests.player(h, null);
            b.snapTo(bare.x, bare.y, bare.z, 0F, 0F);
            h.assertFalse(RunnerServer.grip(b, BodyServer.motion(b), run(Move.GRIP, BodyPayloads.GRIP_HOLD, 0F, bare, bare.x, bare.y, bare.z, axis, sign), now),
                    "no grip where there's no line");
            h.assertTrue(RunnerServer.grip(a, m, run(Move.GRIP, BodyPayloads.GRIP_CLIMB, 0F, s, l.x(), s.y, l.z(), axis, sign), now), "a grip on the pipe");
            h.assertTrue(m.gripping && m.gripBx == o.getX() && m.gripBz == o.getZ(), "the server holds the pipe's column");
            h.assertTrue(RunnerServer.grip(a, m, run(Move.GRIP, BodyPayloads.GRIP_SLIDE, 0F, s, l.x(), s.y, l.z(), axis, sign), now + 1), "a slide on it");
            // held still: the server's own physics doesn't sink it, and a long slide takes no fall damage
            a.setDeltaMovement(0, -1, 0);
            a.fallDistance = 12;
            RunnerServer.tick(a, m, now + 2);
            h.assertTrue(a.getDeltaMovement().lengthSqr() == 0 && a.fallDistance == 0, "held: no motion, no fall built up");
            // the pipe torn down: the re-checks let go
            for (int y = 0; y <= 6; y++) level.setBlockAndUpdate(o.offset(0, y, 0), Blocks.AIR.defaultBlockState());
            for (long t = now + 3; t < now + 3 + GripRules.CHECK * (GripRules.MISS + 1) && m.gripping; t++) RunnerServer.tick(a, m, t);
            h.assertFalse(m.gripping, "no line, no grip");
            for (int y = 0; y <= 6; y++) level.setBlockAndUpdate(o.offset(0, y, 0), pipe);
            long later = now + 40;
            h.assertTrue(RunnerServer.grip(a, m, run(Move.GRIP, BodyPayloads.GRIP_HOLD, 0F, s, l.x(), s.y, l.z(), axis, sign), later), "gripped again");
            h.assertTrue(RunnerServer.grip(a, m, run(Move.GRIP, BodyPayloads.GRIP_KICK, 0F, s, l.x(), s.y, l.z(), axis, sign), later + 3) && !m.gripping,
                    "a kick off the wall leaves the line");

            // the slide speeds up to its top in under a second; the hop rises exactly its height
            double v = 0;
            int ticks = 0;
            while (v < GripRules.SLIDE_MAX && ticks < 40) {
                v = GripRules.slide(v);
                ticks++;
            }
            h.assertTrue(v == GripRules.SLIDE_MAX && ticks <= 12, "the slide's top speed in " + ticks + " ticks");
            double rise = 0;
            for (int t = 1; t <= GripRules.HOP_TICKS; t++) rise += GripRules.hopStep(t);
            h.assertTrue(Math.abs(rise - GripRules.HOP) < 1e-9, "a hop rises " + rise);
            for (int x = -1; x <= 5; x++) for (int y = -1; y <= 6; y++) for (int z = -2; z <= 2; z++)
                level.setBlockAndUpdate(o.offset(x, y, z), Blocks.AIR.defaultBlockState());
            BodyTests.done(h, a, b);
        });
    }

    /**
     * The top of a pipe always leads somewhere (Euan, 2026-10-25: "the last block doesn't let me through"; SPEC §17.28):
     * a drainpipe up a wall under a one-block cornice, a roof behind it. At the pipe's top, the head under the cornice,
     * the lip over it is a hang from its outer face (out, then up), the server takes it only from a grip, and the hang
     * climbs out onto the roof. A ceiling with no lip in reach, or open air, is no hang.
     */
    static void pipeCornice(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = origin(h);
        BlockState pipe = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(dev.awaken.Awaken.id("drainpipe")).defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        BlockState stone = Blocks.STONE.defaultBlockState(), bricks = Blocks.STONE_BRICKS.defaultBlockState();
        for (int x = -1; x <= 2; x++) for (int z = -4; z <= 4; z++) level.setBlockAndUpdate(o.offset(x, -1, z), stone);
        for (int x = -1; x <= 2; x++) {
            for (int y = 0; y <= 3; y++) level.setBlockAndUpdate(o.offset(x, y, 1), stone);
            // the cornice one out over the wall's face, the roof behind it
            level.setBlockAndUpdate(o.offset(x, 4, 0), bricks);
            for (int z = 1; z <= 3; z++) level.setBlockAndUpdate(o.offset(x, 4, z), stone);
        }
        for (int y = 0; y <= 3; y++) level.setBlockAndUpdate(o.offset(0, y, 0), pipe);
        RunnerServer.fromHistory = true;
        RunnerServer.latencyTicks = 0;
        h.startSequence().thenIdle(2).thenExecute(() -> {
            var m0 = new BlockPos.MutableBlockPos();
            CollisionContext ctx = CollisionContext.empty();
            GripRules.Line l = GripRules.find(level, m0, o.getX() + 0.5, o.getY() + 2, o.getZ() + 0.3, 0F);
            h.assertTrue(l != null && l.fz() == 1, "the drainpipe is a line: " + l);
            // at the top: the head under the cornice
            Vec3 s = new Vec3(l.x(), o.getY() + 4 - BodyRules.HEIGHT, l.z());
            int axis = 2, sign = 1;
            RunnerRules.Edge e = GripRules.topLip(level, ctx, m0, s.x, s.y, s.z, axis, sign, 0);
            h.assertTrue(e != null && Math.abs(e.top() - (o.getY() + 5)) < 1e-6, "the cornice's lip is the way on: " + e);
            h.assertTrue(Math.abs(e.hz() - (o.getZ() - BodyRules.HALF_WIDTH - RunnerRules.HANG_GAP)) < 0.01 && e.hz() < s.z,
                    "hung from the cornice's outer face, out from the pipe: " + e);
            double[] stand = RunnerRules.climbOut(level, ctx, m0, e);
            h.assertTrue(stand != null && Math.abs(stand[1] - (o.getY() + 5)) < 1e-6, "and the hang climbs out onto the roof: "
                    + (stand == null ? "none" : stand[1]));

            long now = level.getServer().overworld().getGameTime();
            ServerPlayer b = BodyTests.player(h, null);
            b.snapTo(s.x, s.y, s.z, 0F, 0F);
            h.assertFalse(RunnerServer.startHang(b, BodyServer.motion(b), run(Move.LEDGE_HANG, BodyPayloads.HANG_EAVE, 0F, s, e.hx(), e.hy(), e.hz(), axis, sign), now),
                    "the lip over a pipe's top is taken only from the pipe");
            ServerPlayer a = BodyTests.player(h, null);
            a.snapTo(s.x, s.y, s.z, 0F, 0F);
            a.setOnGround(false);
            BodyMotion m = BodyServer.motion(a);
            h.assertTrue(RunnerServer.grip(a, m, run(Move.GRIP, BodyPayloads.GRIP_CLIMB, 0F, s, l.x(), s.y, l.z(), axis, sign), now), "a grip on the pipe");
            RunnerServer.onEnd(a, Move.GRIP);
            h.assertTrue(RunnerServer.startHang(a, m, run(Move.LEDGE_HANG, BodyPayloads.HANG_EAVE, 0F, s, e.hx(), e.hy(), e.hz(), axis, sign), now + 1)
                    && m.hanging && Math.abs(m.hangTop - e.top()) < 1e-6, "the server takes the hang on the cornice from the pipe's top");
            Vec3 hold = new Vec3(e.hx(), e.hy(), e.hz());
            a.snapTo(hold.x, hold.y, hold.z, 0F, 0F);
            h.assertTrue(RunnerServer.ledgeJump(a, m, run(Move.LEDGE_JUMP, BodyPayloads.JUMP_CLIMB, 0F, hold, stand[0], stand[1], stand[2], axis, sign), now + 12)
                    && !m.hanging, "and lets it climb out onto the roof");

            // a ceiling running far out with no lip in reach, and open air: no hang (the grip holds, or vanilla climbs)
            for (int x = -1; x <= 2; x++) for (int z = -4; z <= 0; z++) level.setBlockAndUpdate(o.offset(x, 4, z), stone);
            h.assertTrue(GripRules.topLip(level, ctx, m0, s.x, s.y, s.z, axis, sign, 0) == null, "no lip under a deep ceiling");
            for (int x = -1; x <= 2; x++) for (int z = -4; z <= 3; z++) level.setBlockAndUpdate(o.offset(x, 4, z), Blocks.AIR.defaultBlockState());
            h.assertTrue(GripRules.topLip(level, ctx, m0, s.x, s.y + 0.6, s.z, axis, sign, 0) == null, "no lip over open air");
            for (int x = -1; x <= 2; x++) for (int y = -1; y <= 4; y++) for (int z = -4; z <= 4; z++)
                level.setBlockAndUpdate(o.offset(x, y, z), Blocks.AIR.defaultBlockState());
            BodyTests.done(h, a, b);
        });
    }

    /**
     * Rails at a roof's edge (SPEC §17.27): a fence or wall on a wall's top row stands back from the face under it, so a
     * hang on its top holds from that face (the hands reaching in). From a fence's rail the climb out goes over it, down
     * onto the roof beyond, and the server takes it with the crawl box; a wall's coping is stood on, the body over its
     * middle.
     */
    static void railHang(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = origin(h);
        BlockState stone = Blocks.STONE.defaultBlockState();
        // its own air first, wherever the test lands (inside a mountain too)
        for (int x = -2; x <= 8; x++) for (int y = -1; y <= 9; y++) for (int z = -4; z <= 6; z++)
            level.setBlockAndUpdate(o.offset(x, y, z), Blocks.AIR.defaultBlockState());
        // a 3-high wall at z 1 under a roof (top y 3) running back to z 4; a fence on its top row at x -1..2, a wall at 4..7
        for (int x = -1; x <= 7; x++) {
            for (int z = -3; z <= 4; z++) level.setBlockAndUpdate(o.offset(x, -1, z), stone);
            for (int y = 0; y <= 2; y++) level.setBlockAndUpdate(o.offset(x, y, 1), stone);
            for (int z = 2; z <= 4; z++) level.setBlockAndUpdate(o.offset(x, 2, z), stone);
        }
        for (int x = -1; x <= 2; x++) level.setBlockAndUpdate(o.offset(x, 3, 1), Blocks.OAK_FENCE.defaultBlockState());
        for (int x = 4; x <= 7; x++) level.setBlockAndUpdate(o.offset(x, 3, 1), Blocks.COBBLESTONE_WALL.defaultBlockState());
        for (int x = -1; x <= 7; x++) {
            BlockPos a = o.offset(x, 3, 1);
            level.setBlock(a, net.minecraft.world.level.block.Block.updateFromNeighbourShapes(level.getBlockState(a), level, a), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
        RunnerServer.fromHistory = true;
        RunnerServer.latencyTicks = 0;
        h.startSequence().thenIdle(2).thenExecute(() -> {
            var m0 = new BlockPos.MutableBlockPos();
            CollisionContext ctx = CollisionContext.empty();
            double top = o.getY() + 4.5, hold = o.getZ() + 1 - BodyRules.HALF_WIDTH - RunnerRules.HANG_GAP;
            RunnerRules.Edge fence = RunnerRules.findEdge(level, ctx, m0, o.getX() + 0.5, top - RunnerRules.HANG_DROP, hold, 2, 1,
                    RunnerRules.HANG_REACH, RunnerRules.HANG_LO, RunnerRules.HANG_HI, 0, 0.2);
            h.assertTrue(fence != null && Math.abs(fence.top() - top) < 1e-6 && Math.abs(fence.hz() - hold) < 0.03,
                    "a hang on the fence, held from the wall's face under it: " + fence);
            h.assertTrue(RunnerRules.climbOut(level, ctx, m0, fence) == null, "a fence's rail is no place to stand");
            VaultRules.Vault over = VaultRules.over(level, ctx, fence.hx(), fence.hy(), fence.hz(), fence.fx(), fence.fz(), fence.top());
            h.assertTrue(over != null && over.kind() == VaultRules.OVER && Math.abs(over.floor() - (o.getY() + 3)) < 1e-6
                    && over.endZ() > o.getZ() + 1.625 + 0.3, "over the rail, down onto the roof beyond: " + over);
            String fault = BodyTests.pathFault(h, over);
            h.assertTrue(fault == null, "climbing over: " + fault);
            RunnerRules.Edge wall = RunnerRules.findEdge(level, ctx, m0, o.getX() + 5.5, top - RunnerRules.HANG_DROP, hold, 2, 1,
                    RunnerRules.HANG_REACH, RunnerRules.HANG_LO, RunnerRules.HANG_HI, 0, 0.2);
            double[] stand = wall == null ? null : RunnerRules.climbOut(level, ctx, m0, wall);
            h.assertTrue(stand != null && Math.abs(stand[1] - top) < 1e-6 && Math.abs(stand[2] - (o.getZ() + 1.5)) < 0.02,
                    "a wall's coping: climbed out onto, over its middle: " + (stand == null ? null : java.util.Arrays.toString(stand)));

            long now = level.getServer().overworld().getGameTime();
            ServerPlayer a = BodyTests.player(h, null);
            Vec3 s = new Vec3(fence.hx(), fence.hy(), fence.hz());
            a.snapTo(s.x, s.y, s.z, 0F, 0F);
            a.setOnGround(false);
            BodyMotion m = BodyServer.motion(a);
            h.assertTrue(RunnerServer.startHang(a, m, run(Move.LEDGE_HANG, BodyPayloads.HANG_GRAB, 0F, s, fence.hx(), fence.hy(), fence.hz(), 2, 1), now)
                    && m.hanging, "the server holds the hang on the fence");
            h.assertFalse(RunnerServer.ledgeJump(a, m, run(Move.LEDGE_JUMP, BodyPayloads.JUMP_OVER, 0F, s, over.endX(), over.floor() + 2, over.endZ(), 2, 1), now + 5),
                    "a climb over to somewhere else is refused");
            RunnerServer.startHang(a, m, run(Move.LEDGE_HANG, BodyPayloads.HANG_GRAB, 0F, s, fence.hx(), fence.hy(), fence.hz(), 2, 1), now + 6);
            h.assertTrue(RunnerServer.ledgeJump(a, m, run(Move.LEDGE_JUMP, BodyPayloads.JUMP_OVER, 0F, s, over.endX(), over.floor(), over.endZ(), 2, 1), now + 10)
                    && !m.hanging && m.vaultUntil != 0 && a.getForcedPose() == net.minecraft.world.entity.Pose.SWIMMING,
                    "and takes the climb over the rail, with the crawl box");
            for (int x = -2; x <= 8; x++) for (int y = -1; y <= 9; y++) for (int z = -4; z <= 6; z++)
                level.setBlockAndUpdate(o.offset(x, y, z), Blocks.AIR.defaultBlockState());
            BodyTests.done(h, a);
        });
    }

    /**
     * Shift in a hang drops to the ledge below (Euan, 2026-10-25; SPEC §17.25): hanging from a wall's top with a sill
     * sticking out a block lower down, the drop catches the sill (out from the wall first, then down), and the server
     * takes it; with no sill, there's nothing to catch (Shift lets go).
     */
    static void ledgeDrop(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = origin(h);
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int x = -1; x <= 2; x++) for (int z = -3; z <= 3; z++) level.setBlockAndUpdate(o.offset(x, -1, z), stone);
        for (int x = -1; x <= 2; x++) {
            for (int y = 0; y <= 5; y++) level.setBlockAndUpdate(o.offset(x, y, 1), stone);
            level.setBlockAndUpdate(o.offset(x, 3, 0), Blocks.STONE_BRICKS.defaultBlockState()); // the sill, one out
        }
        RunnerServer.fromHistory = true;
        RunnerServer.latencyTicks = 0;
        h.startSequence().thenIdle(2).thenExecute(() -> {
            var m0 = new BlockPos.MutableBlockPos();
            CollisionContext ctx = CollisionContext.empty();
            int axis = 2, sign = 1;
            double x = o.getX() + 0.5, hold = o.getZ() + 1 - BodyRules.HALF_WIDTH - RunnerRules.HANG_GAP;
            RunnerRules.Edge top = RunnerRules.findEdge(level, ctx, m0, x, o.getY() + 6 - RunnerRules.HANG_DROP, hold, axis, sign,
                    RunnerRules.HANG_REACH, RunnerRules.HANG_LO, RunnerRules.HANG_HI, 0, 0.2);
            h.assertTrue(top != null && Math.abs(top.top() - (o.getY() + 6)) < 1e-6, "a hang on the wall's top: " + top);
            RunnerRules.Edge below = RunnerRules.edgeBelow(level, ctx, m0, top.hx(), top.hy(), top.hz(), top, 0);
            h.assertTrue(below != null && Math.abs(below.top() - (o.getY() + 4)) < 1e-6, "Shift drops to the sill below: " + below);
            h.assertTrue(below.hz() < top.hz() - 0.9, "hung from the sill's own face, out from the wall: " + below);
            // standing on the wall's top facing out, Shift + forward lowers into that hang: the sill below is worth it
            RunnerRules.Edge lowered = RunnerRules.lowerEdge(level, ctx, m0, x, o.getY() + 6, o.getZ() + 1.0, 180F); // at the very edge, as sneaking leaves you
            h.assertTrue(lowered != null && Math.abs(lowered.top() - top.top()) < 1e-6, "a lower onto the top over the sill: " + lowered);

            long now = level.getServer().overworld().getGameTime();
            ServerPlayer a = BodyTests.player(h, null);
            Vec3 s = new Vec3(top.hx(), top.hy(), top.hz());
            a.snapTo(s.x, s.y, s.z, 0F, 0F);
            a.setOnGround(false);
            BodyMotion m = BodyServer.motion(a);
            h.assertTrue(RunnerServer.startHang(a, m, run(Move.LEDGE_HANG, BodyPayloads.HANG_GRAB, 0F, s, top.hx(), top.hy(), top.hz(), axis, sign), now)
                    && m.hanging, "the server holds the hang on the top");
            h.assertTrue(RunnerServer.ledgeJump(a, m, run(Move.LEDGE_JUMP, BodyPayloads.JUMP_DOWN, 0F, s, below.hx(), below.hy(), below.hz(), axis, sign), now + 10)
                    && m.hanging && Math.abs(m.hangTop - below.top()) < 1e-6, "and takes the drop to the sill");

            // no sill: nothing below to catch, and the server won't hang you on thin air
            for (int xx = -1; xx <= 2; xx++) level.setBlockAndUpdate(o.offset(xx, 3, 0), Blocks.AIR.defaultBlockState());
            h.assertTrue(RunnerRules.edgeBelow(level, ctx, m0, top.hx(), top.hy(), top.hz(), top, 0) == null, "no sill, no drop to catch");
            ServerPlayer b = BodyTests.player(h, null);
            b.snapTo(s.x, s.y, s.z, 0F, 0F);
            BodyMotion mb = BodyServer.motion(b);
            RunnerServer.startHang(b, mb, run(Move.LEDGE_HANG, BodyPayloads.HANG_GRAB, 0F, s, top.hx(), top.hy(), top.hz(), axis, sign), now + 20);
            h.assertFalse(RunnerServer.ledgeJump(b, mb, run(Move.LEDGE_JUMP, BodyPayloads.JUMP_DOWN, 0F, s, below.hx(), below.hy(), below.hz(), axis, sign), now + 30),
                    "the server refuses a drop to no ledge");
            for (int xx = -1; xx <= 2; xx++) for (int y = -1; y <= 5; y++) for (int z = -3; z <= 3; z++)
                level.setBlockAndUpdate(o.offset(xx, y, z), Blocks.AIR.defaultBlockState());
            BodyTests.done(h, a, b);
        });
    }

    /**
     * Every kind of pipe top Carillon paints (Euan, 2026-10-26: "I still get stuck on the final upper part of a drain
     * pipe"), each climbed from the street on a real drainpipe with its deeper head (CityGrammar's facades and roofs,
     * CityPainter's retaining walls, CityWall's inner face): the climb reaches the top, the top has a lip, and the hang
     * on it climbs out onto the roof, the terrace or the wall walk.
     */
    static void pipeTops(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = origin(h);
        String[] names = {"flat roof, balustrade open at the pipe", "gable eave (the head in the eave stairs' row)", "full-block cornice",
                "upside-down stair cornice", "slab cornice", "retaining wall, parapet gap", "city wall inner face: corbels, band, parapet gap"};
        double[] stands = new double[names.length];
        for (int i = 0; i < names.length; i++) stands[i] = pipeTop(level, o.offset(10 * i, 0, 0), i);
        h.startSequence().thenIdle(2).thenExecute(() -> {
            var m0 = new BlockPos.MutableBlockPos();
            CollisionContext ctx = CollisionContext.empty();
            for (int i = 0; i < names.length; i++) {
                int ox = o.getX() + 10 * i;
                GripRules.Line l = GripRules.find(level, m0, ox + 0.5, o.getY() + 0.5, o.getZ() + 0.2, 0F);
                h.assertTrue(l != null && l.fz() == 1, names[i] + ": a drainpipe line: " + l);
                double y = GripRules.climbTop(level, ctx, m0, l, o.getY());
                h.assertTrue(y > o.getY() + 2, names[i] + ": the climb gets up the pipe, to " + (y - o.getY()));
                RunnerRules.Edge lip = GripRules.topLip(level, ctx, m0, l.x(), y, l.z(), 2, 1, 0);
                h.assertTrue(lip != null, names[i] + ": a lip at the top (feet at " + (y - o.getY()) + "): "
                        + RunnerRules.whyNot(level, ctx, m0, l.x(), y, l.z(), 2, 1, RunnerRules.HANG_REACH, GripRules.LIP_LO, GripRules.LIP_HI));
                double[] stand = RunnerRules.climbOut(level, ctx, m0, lip);
                h.assertTrue(stand != null && stand[1] >= stands[i] - 1e-6, names[i] + ": climbs out on top, at "
                        + (stand == null ? "none" : stand[1] - o.getY()) + " (lip " + (lip.top() - o.getY()) + ", wanted " + (stands[i] - o.getY()) + ")");
            }
            for (int i = 0; i < names.length; i++) for (int x = -4; x <= 4; x++) for (int y = -1; y <= 12; y++) for (int z = -3; z <= 6; z++)
                level.setBlockAndUpdate(o.offset(10 * i + x, y, z), Blocks.AIR.defaultBlockState());
            h.succeed();
        });
    }

    /** Builds pipe-top scene {@code kind} with its pipe at (o.x, o.z) against a wall to the south; returns the lowest stand. */
    private static double pipeTop(ServerLevel level, BlockPos o, int kind) {
        BlockState stone = Blocks.STONE.defaultBlockState(), bricks = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState wallTop = Blocks.STONE_BRICK_WALL.defaultBlockState();
        BlockState stair = Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
        BlockState course = Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH)
                .setValue(StairBlock.HALF, net.minecraft.world.level.block.state.properties.Half.TOP);
        BlockState slabTop = Blocks.STONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
        int e = 5;
        for (int x = -4; x <= 4; x++) for (int z = -3; z <= 6; z++) put(level, o, x, -1, z, stone);
        int head; // the pipe's head row; the pipe runs from the street to it
        double stand;
        switch (kind) {
            case 0 -> { // a flat roof: the house to the eave row, the balustrade on its wall cells, open at the climb column
                box(level, o, -3, 3, 0, e, 1, 4, stone);
                for (int x = -3; x <= 3; x++) if (Math.abs(x) > 1) put(level, o, x, e + 1, 1, wallTop);
                head = e;
                stand = e + 1;
            }
            case 1 -> { // a gable: the wall to the eave row, its stair one out (the head takes it at the pipe), the slope over the wall
                box(level, o, -3, 3, 0, e, 1, 4, stone);
                for (int x = -3; x <= 3; x++) {
                    if (x != 0) put(level, o, x, e, 0, stair);
                    put(level, o, x, e + 1, 1, stair);
                    for (int z = 2; z <= 4; z++) put(level, o, x, e + 1, z, Blocks.DEEPSLATE_TILES.defaultBlockState());
                    put(level, o, x, e + 2, 2, stair);
                }
                head = e;
                stand = e + 1;
            }
            case 2, 3, 4 -> { // a cornice one out over the wall's top, over the pipe too, the roof behind it
                box(level, o, -3, 3, 0, e, 1, 4, stone);
                BlockState c = kind == 2 ? bricks : kind == 3 ? course : slabTop;
                for (int x = -3; x <= 3; x++) put(level, o, x, e, 0, c);
                head = e - 1;
                stand = e + 1;
            }
            case 5 -> { // a retaining wall: the terrace behind, the parapet on its edge, open where the pipe arrives
                box(level, o, -3, 3, 0, e - 1, 1, 5, stone);
                for (int x = -3; x <= 3; x++) if (Math.abs(x) > 1) put(level, o, x, e, 1, wallTop);
                head = e - 1;
                stand = e;
            }
            default -> { // the city wall's inner face: the corbel table, the parapet's band, the inner parapet open at the pipe
                int top = 8;
                box(level, o, -3, 3, 0, top, 1, 5, stone);
                for (int x = -3; x <= 3; x++) {
                    if (x == 0) continue;
                    if ((x & 1) == 0) put(level, o, x, top - 3, 0, course);
                    for (int y = top - 2; y <= top; y++) put(level, o, x, y, 0, Blocks.POLISHED_ANDESITE.defaultBlockState());
                    if (Math.abs(x) > 1) put(level, o, x, top + 1, 0, bricks);
                }
                head = top;
                stand = top + 1;
            }
        }
        var pipeBlock = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(dev.awaken.Awaken.id("drainpipe"));
        BlockState pipe = pipeBlock.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        for (int y = 0; y <= head; y++) put(level, o, 0, y, 0, part(pipe, y == 0 ? "shoe" : y == head ? "head" : "pipe"));
        return o.getY() + stand;
    }

    private static void put(ServerLevel level, BlockPos o, int x, int y, int z, BlockState s) {
        level.setBlockAndUpdate(o.offset(x, y, z), s);
    }

    private static void box(ServerLevel level, BlockPos o, int x0, int x1, int y0, int y1, int z0, int z1, BlockState s) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) put(level, o, x, y, z, s);
    }

    /** The drainpipe's part ("pipe", "head", "shoe") by its property's own name (the block lives in the city module). */
    private static BlockState part(BlockState s, String name) {
        for (var prop : s.getProperties()) if (prop.getName().equals("part")) return withValue(s, prop, name);
        return s;
    }

    private static <T extends Comparable<T>> BlockState withValue(BlockState s, net.minecraft.world.level.block.state.properties.Property<T> prop, String name) {
        return prop.getValue(name).map(v -> s.setValue(prop, v)).orElse(s);
    }
}
