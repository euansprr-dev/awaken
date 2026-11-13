package dev.awaken.gates.realms.check;

import dev.awaken.body.BodyRules;
import dev.awaken.body.GripRules;
import dev.awaken.body.RunnerRules;
import dev.awaken.gates.realms.Land;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Proves the moves a gate's beats ask for (SPEC §72.4) with the body's own rules. The probes the player's body uses run
 * on the blocks ({@link RunnerRules#findWall}, {@link RunnerRules#wallOn}, {@link RunnerRules#groundWithin},
 * {@link RunnerRules#findEdge}, {@link RunnerRules#lowerEdge}, {@link RunnerRules#edgeBelow}, {@link RunnerRules#climbOut},
 * {@link BodyRules#findLedge}, {@link BodyRules#crawlFits}, {@link BodyRules#stairStart}, {@link GripRules#find},
 * {@link GripRules#climbTop}, {@link GripRules#topLip}), and the client's motion is stepped here tick by tick the way
 * RunnerMoves (run, wall jump, grab, ledge jumps), ClientMoves (slide, stair slide, jump out), GripMoves (climb, slide
 * down, side swing) and vanilla (jump, air, gravity) step it.
 * <p>
 * A move is a chain of steps ("run", "lower+shimmy:6+climb"). It passes when it holds twice: played slowly (a sprint jump
 * at 80% of its push, a wall run at its slowest speed for 80% of its ticks, wall jumps late, a slide at 80% of its speed)
 * and played fast; both end standing where the move says. So nothing asks for more than 80% of a move's reach, and
 * nothing asks for a pixel-perfect timing. The body is proved at the gate's rank but never above D: a partner of lower
 * rank has the shorter wall run, and nobody is assumed to have the fifth link or the B-rank ledge jump.
 * <p>
 * Steps (":" an argument): slide, slidejump, chute (a slide into a low run of half steps: the stair slide carries the
 * body down it), jump (a sprint jump off the edge ahead, landing), jumphang (one that ends in a hang on a lip ahead),
 * run (a wall run off the edge ahead), chain:k (a wall run and k wall jumps), switch (a wall run, and across to the
 * facing wall where it ends), turn:deg (the facing turns; + is to the right, as yaw goes), mantle, lower (into a hang off the edge
 * ahead), shimmy:n (n blocks along the hang, + left, - right), climb (out of the hang), drop (to the lip below), let
 * (go, onto the floor), ledge:d (a ledge jump: 0 up, 1 left, -1 right), fall:n (off the edge ahead, no more than n),
 * walk:n, grip (up a wall line from its foot, out over its top), rope (a sprint jump off the edge ahead onto a free
 * rope), slip (down the held line to the floor), swing:d (the side swing from a free rope to the next, 1 left, -1 right).
 */
public final class MoveProof {
    private MoveProof() {}

    /** The player's box (BodyRules' own, package-private there). */
    private static final double W = 0.3, H = 1.8, CRAWL = 0.6;
    private static final CollisionContext CTX = CollisionContext.empty();
    private static final int MAX_TICKS = 160;
    /** Slow play: this share of a push, a slide, a run's ticks. */
    static final double SLOW = 0.8;

    /** Why a move fails, or null when it holds. */
    public static @Nullable String prove(BlockGetter g, Land.Move mv, Rank gateRank) {
        Rank body = gateRank.ordinal() > Rank.D.ordinal() ? Rank.D : gateRank;
        for (int variant = 0; variant < 2; variant++) {
            String why = run(g, mv, body, variant == 1);
            if (why != null) return (variant == 1 ? "fast: " : "slow: ") + why;
        }
        return null;
    }

    /** Every move of these that fails on these blocks, each with its reason. */
    public static List<String> failures(BlockGetter g, List<Land.Move> moves, Rank rank) {
        List<String> out = new ArrayList<>();
        for (Land.Move m : moves) {
            String why = prove(g, m, rank);
            if (why != null) out.add(describe(m) + ": " + why);
        }
        return out;
    }

    public static String describe(Land.Move m) {
        return m.leg() + " " + m.beat() + " [" + m.move() + "] from " + m.x0() + " " + m.y0() + " " + m.z0() + " to " + m.x1() + " " + m.y1() + " " + m.z1();
    }

    // ---- the body ---------------------------------------------------------------------------------------------------

    /** Where the body is and how it moves; {@code hang} when it hangs from an edge, {@code line} when it holds a line. */
    private static final class Body {
        double x, y, z, vx, vy, vz;
        boolean ground, bumped;
        RunnerRules.@Nullable Edge hang;
        GripRules.@Nullable Line line;
        final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        final BodyRules.StairProfile stairs = new BodyRules.StairProfile();
        final double[] tmp = new double[3];
    }

    private static boolean blocked(BlockGetter g, Body b, double x, double y, double z, double h) {
        return BodyRules.blocked(g, CTX, b.m, x - W + 1e-3, y + 1e-3, z - W + 1e-3, x + W - 1e-3, y + h - 1e-3, z + W - 1e-3);
    }

    /** The highest collision top under a footprint between two heights, or NaN. */
    static double topUnder(BlockGetter g, BlockPos.MutableBlockPos m, double x0, double z0, double x1, double z1, double yLo, double yHi) {
        double best = Double.NaN;
        for (int bx = Mth.floor(x0); bx <= Mth.floor(x1 - 1e-7); bx++) for (int bz = Mth.floor(z0); bz <= Mth.floor(z1 - 1e-7); bz++) {
            for (int by = Mth.floor(yLo) - 1; by <= Mth.floor(yHi); by++) {
                BlockState s = g.getBlockState(m.set(bx, by, bz));
                if (s.isAir()) continue;
                VoxelShape shape = s.getCollisionShape(g, m, CTX);
                if (shape.isEmpty()) continue;
                for (AABB a : shape.toAabbs()) {
                    if (!(a.maxX + bx > x0 && a.minX + bx < x1 && a.maxZ + bz > z0 && a.minZ + bz < z1)) continue;
                    double top = a.maxY + by;
                    if (top < yLo - 1e-6 || top > yHi + 1e-6) continue;
                    if (Double.isNaN(best) || top > best) best = top;
                }
            }
        }
        return best;
    }

    private static double floorUnder(BlockGetter g, Body b, double x, double z, double yLo, double yHi) {
        return topUnder(g, b.m, x - W + 1e-3, z - W + 1e-3, x + W - 1e-3, z + W - 1e-3, yLo, yHi);
    }

    /** One tick of vanilla motion with collision: up or down first, then x, then z. {@code h}: the body's height. */
    private static void step(BlockGetter g, Body b, double h) {
        b.bumped = false;
        double ny = b.y + b.vy;
        if (blocked(g, b, b.x, ny, b.z, h)) {
            if (b.vy < 0) {
                double top = floorUnder(g, b, b.x, b.z, ny - 1e-3, b.y + 1e-3);
                if (!Double.isNaN(top)) b.y = top;
                b.ground = true;
            }
            b.vy = 0;
        } else {
            b.y = ny;
            b.ground = false;
        }
        double nx = b.x + b.vx;
        if (blocked(g, b, nx, b.y, b.z, h)) {
            b.bumped = true;
            b.vx = 0;
        } else b.x = nx;
        double nz = b.z + b.vz;
        if (blocked(g, b, b.x, b.y, nz, h)) {
            b.bumped = true;
            b.vz = 0;
        } else b.z = nz;
        if (!b.ground && b.vy <= 0 && blocked(g, b, b.x, b.y - 0.01, b.z, 0.02)) b.ground = true;
    }

    /** Vanilla's air after a tick: gravity and drag, and the push of the keys held along (fx, fz). */
    private static void air(Body b, double fx, double fz, double push) {
        b.vy = (b.vy - 0.08) * 0.98;
        b.vx = b.vx * 0.91 + fx * push;
        b.vz = b.vz * 0.91 + fz * push;
    }

    private static boolean onFloor(BlockGetter g, Body b, double x, double z) {
        return blocked(g, b, x, b.y - 0.05, z, 0.06);
    }

    // ---- a move, step by step ---------------------------------------------------------------------------------------

    private static @Nullable String run(BlockGetter g, Land.Move mv, Rank rank, boolean fast) {
        Body b = new Body();
        b.x = mv.x0() + 0.5;
        b.z = mv.z0() + 0.5;
        double top = floorUnder(g, b, b.x, b.z, mv.y0() - 1.0, mv.y0() + 0.6);
        if (Double.isNaN(top)) return "nothing to stand on at the start";
        b.y = top;
        if (blocked(g, b, b.x, b.y, b.z, H)) return "no room to stand at the start";
        b.ground = true;
        float yaw = mv.yaw();
        double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD), fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        for (String part : mv.move().split("\\+")) {
            int colon = part.indexOf(':');
            String name = colon < 0 ? part : part.substring(0, colon);
            int arg;
            try {
                arg = colon < 0 ? mv.arg() : Integer.parseInt(part.substring(colon + 1));
            } catch (NumberFormatException e) {
                return "bad step " + part;
            }
            boolean hangStep = name.equals("shimmy") || name.equals("climb") || name.equals("drop") || name.equals("let") || name.equals("ledge") || name.equals("turn");
            if (b.hang != null && !hangStep) return name + ": still hanging";
            if (b.line != null && !name.equals("slip") && !name.equals("swing")) return name + ": still on a line";
            String why = switch (name) {
                case "slide" -> slide(g, b, fx, fz, false, fast);
                case "slidejump" -> slide(g, b, fx, fz, true, fast);
                case "chute" -> chute(g, b, fx, fz, fast);
                case "jump" -> jump(g, b, fx, fz, fast, false);
                case "jumphang" -> jump(g, b, fx, fz, fast, true);
                case "run" -> wallRun(g, b, yaw, fx, fz, rank, fast, 0, -1);
                case "chain" -> wallRun(g, b, yaw, fx, fz, rank, fast, arg, -1);
                case "switch" -> wallRun(g, b, yaw, fx, fz, rank, fast, 1, fast ? 0.4 : 1.0);
                case "mantle" -> mantle(g, b, yaw, fx, fz);
                case "lower" -> lower(g, b, yaw, fx, fz);
                case "shimmy" -> shimmy(g, b, arg);
                case "climb" -> climb(g, b);
                case "drop" -> drop(g, b);
                case "let" -> let(g, b);
                case "ledge" -> ledge(g, b, rank, arg);
                case "fall" -> fall(g, b, fx, fz, arg);
                case "walk" -> walk(g, b, fx, fz, arg);
                case "grip" -> grip(g, b, yaw, fx, fz);
                case "rope" -> rope(g, b, yaw, fx, fz, fast);
                case "slip" -> slip(g, b);
                case "swing" -> swing(g, b, arg, fast);
                case "turn" -> {
                    yaw = Mth.wrapDegrees(yaw + arg);
                    fx = -Mth.sin(yaw * Mth.DEG_TO_RAD);
                    fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
                    yield null;
                }
                default -> "no such step";
            };
            if (why != null) return name + ": " + why;
        }
        if (b.hang != null) return "ends hanging";
        if (b.line != null) return "ends on a line";
        // where it ends: within 1.6 of its end cell, or on along the way it faces by up to its over
        double ex = mv.x1() + 0.5, ez = mv.z1() + 0.5, ux = -Mth.sin(mv.yaw() * Mth.DEG_TO_RAD), uz = Mth.cos(mv.yaw() * Mth.DEG_TO_RAD);
        double along = Mth.clamp((b.x - ex) * ux + (b.z - ez) * uz, 0, mv.over());
        double dx = b.x - (ex + ux * along), dz = b.z - (ez + uz * along);
        if (Math.abs(dx) > 1.6 || Math.abs(dz) > 1.6 || b.y < mv.y1() - 0.6 || b.y > mv.y1() + 0.6) {
            return String.format("ends at %.1f %.1f %.1f, not at %d %d %d", b.x, b.y, b.z, mv.x1(), mv.y1(), mv.z1());
        }
        return null;
    }

    /**
     * Walks the body along (fx, fz) to the edge of what it stands on: the last place a jump can leave from (vanilla
     * falls once the body's box is wholly off the floor). Fails when something is in the way or nothing ends.
     */
    private static @Nullable String toEdge(BlockGetter g, Body b, double fx, double fz, double max) {
        for (double s = 0.05; s <= max + 1e-6; s += 0.05) {
            double x = b.x + fx * 0.05, z = b.z + fz * 0.05;
            if (blocked(g, b, x, b.y, z, H)) return "walked into something before the edge";
            if (!onFloor(g, b, x, z)) return null;
            b.x = x;
            b.z = z;
        }
        return "no edge within " + max;
    }

    /** Sprint and crouch into the low place ahead; {@code jump}: and jump out of the slide past it. */
    private static @Nullable String slide(BlockGetter g, Body b, double fx, double fz, boolean jump, boolean fast) {
        // the low face ahead within a short run-up; the slide starts a body's length before it (slow) or right at it
        double face = -1;
        for (double s = 0; s <= 3.5; s += 0.05) {
            if (blocked(g, b, b.x + fx * s, b.y, b.z + fz * s, 1.5)) {
                face = s;
                break;
            }
            if (!onFloor(g, b, b.x + fx * s, b.z + fz * s)) return "the floor ends before the low place";
        }
        if (face < 0) return "nothing low ahead";
        // a slide always starts at its own speed (BodyRules.slideEntry); what a player gets wrong is where: early, a body's
        // length before the low place, or late, right at it
        double start = Math.max(0, face - (fast ? 0.3 : 1.0));
        b.x += fx * start;
        b.z += fz * start;
        double v0 = BodyRules.slideEntry(0.28, false);
        boolean low = false;
        int air = 0;
        for (int t = 0; t < BodyRules.SLIDE_TICKS; t++) {
            if (BodyRules.stairStart(g, CTX, b.stairs, b.x, b.y, b.z, fx, fz, b.tmp)) return "a run of steps where a slide gate was meant";
            double v = BodyRules.slideSpeed(t, v0);
            b.vx = fx * v;
            b.vz = fz * v;
            b.vy = b.ground ? -0.08 : (b.vy - 0.08) * 0.98;
            step(g, b, CRAWL);
            if (b.bumped && t > 1) return "the slide runs into something";
            air = b.ground ? 0 : air + 1;
            if (air > 2) return "the slide goes off an edge";
            boolean stand = !blocked(g, b, b.x, b.y, b.z, H);
            if (!stand) low = true;
            if (!low || !stand) continue;
            if (!jump) return out(g, b, fx, fz);
            // past the low place: jump out (slow at once, fast at the last moment before the edge)
            double next = BodyRules.slideSpeed(t + 1, v0);
            boolean last = !onFloor(g, b, b.x + fx * (next + 0.05), b.z + fz * (next + 0.05));
            if (fast && !last && t + 1 < BodyRules.SLIDE_TICKS) continue;
            double speed = BodyRules.slideSpeed(t, v0) + BodyRules.SLIDE_JUMP;
            b.vx = fx * speed;
            b.vz = fz * speed;
            b.vy = 0.42;
            b.ground = false;
            return flight(g, b, fx, fz, fast ? 0.026 : 0.02, false);
        }
        if (!low) return "the slide ran out before the low place";
        if (jump) return "no room to jump out before the slide ran out";
        // the slide ran out under the low place: vanilla's crawl (the pose it is forced into) on to where it stands
        for (int k = 0; k < 120; k++) {
            b.vx = fx * 0.04;
            b.vz = fz * 0.04;
            b.vy = b.ground ? -0.08 : (b.vy - 0.08) * 0.98;
            step(g, b, CRAWL);
            if (b.bumped) return "the crawl runs into something";
            if (!blocked(g, b, b.x, b.y, b.z, H)) return out(g, b, fx, fz);
        }
        return "too long to crawl";
    }

    /** Out from under: standing room, and floor a body's length on. */
    private static @Nullable String out(BlockGetter g, Body b, double fx, double fz) {
        double x = b.x + fx * 0.4, z = b.z + fz * 0.4;
        double t = floorUnder(g, b, x, z, b.y - 1.05, b.y + 0.6);
        if (Double.isNaN(t)) return "no floor past the low place";
        if (blocked(g, b, x, t, z, H)) return "no room to stand past the low place";
        b.x = x;
        b.z = z;
        b.y = t;
        b.ground = true;
        return null;
    }

    /**
     * A slide into a low run of half steps (the chute): the stair slide must take it (ClientMoves: stairStart), carry the
     * body down all of it under the roof, and the run-out bring it out where it stands.
     */
    private static @Nullable String chute(BlockGetter g, Body b, double fx, double fz, boolean fast) {
        double v0 = BodyRules.slideEntry(0.28, false);
        boolean stairs = false, rode = false;
        double stairV = 0, start = b.y;
        int t = 0;
        for (int k = 0; k < BodyRules.STAIR_MAX_TICKS + 40; k++) {
            if (!stairs && !rode && BodyRules.stairStart(g, CTX, b.stairs, b.x, b.y, b.z, fx, fz, b.tmp)) {
                stairs = true;
                stairV = BodyRules.slideSpeed(t, v0);
            }
            if (stairs) {
                stairV = BodyRules.stairSpeed(stairV);
                boolean on = BodyRules.stairStep(g, CTX, b.stairs, b.x, b.y, b.z, fx, fz, stairV, b.tmp);
                if (on) {
                    b.x = b.tmp[0];
                    b.y = b.tmp[1];
                    b.z = b.tmp[2];
                }
                if (on && !b.stairs.last) continue;
                // the run-out: the slide's own decay from the run's speed
                stairs = false;
                rode = true;
                t = BodyRules.SLIDE_TICKS - BodyRules.STAIR_RUNOUT;
                v0 = stairV / (1 - BodyRules.SLIDE_FADE * t / (double) BodyRules.SLIDE_TICKS);
                b.ground = true;
                if (on) continue;
            }
            if (t >= BodyRules.SLIDE_TICKS) {
                if (!rode) return "the slide ran out before the chute";
                break;
            }
            double v = BodyRules.slideSpeed(t++, v0);
            b.vx = fx * v;
            b.vz = fz * v;
            b.vy = b.ground ? -0.08 : (b.vy - 0.08) * 0.98;
            step(g, b, CRAWL);
            if (b.bumped && k > 1) return rode ? "the run-out runs into something" : "the slide runs into something before the chute";
        }
        if (!rode) return "the stair slide never took the chute";
        if (start - b.y < 1.5) return String.format("the chute only drops %.1f", start - b.y);
        if (!blocked(g, b, b.x, b.y, b.z, H)) return null;
        for (int k = 0; k < 120; k++) {
            b.vx = fx * 0.04;
            b.vz = fz * 0.04;
            b.vy = b.ground ? -0.08 : (b.vy - 0.08) * 0.98;
            step(g, b, CRAWL);
            if (b.bumped) return "the crawl at the foot runs into something";
            if (!blocked(g, b, b.x, b.y, b.z, H)) return null;
        }
        return "no way out at the foot";
    }

    /** A sprint jump off the edge ahead; {@code hang}: it must end in a hang on a lip ahead (the next step climbs). */
    private static @Nullable String jump(BlockGetter g, Body b, double fx, double fz, boolean fast, boolean hang) {
        String why = toEdge(g, b, fx, fz, 3.5);
        if (why != null) return why;
        sprintJump(b, fx, fz, fast);
        return flight(g, b, fx, fz, fast ? 0.026 : 0.02, hang);
    }

    private static void sprintJump(Body b, double fx, double fz, boolean fast) {
        double push = fast ? 0.48 : 0.48 * SLOW;
        b.vx = fx * push;
        b.vz = fz * push;
        b.vy = 0.42;
        b.ground = false;
    }

    /**
     * Flight until the body stands, or hangs from a lip in front it fell short of (RunnerMoves.tryGrab: the move then says
     * "+climb"); with {@code hang} it must catch the lip.
     */
    private static @Nullable String flight(BlockGetter g, Body b, double fx, double fz, double push, boolean hang) {
        for (int t = 0; t < MAX_TICKS; t++) {
            step(g, b, H);
            if (b.ground) return hang ? String.format("landed at %.1f %.1f %.1f instead of catching the lip", b.x, b.y, b.z) : null;
            // the Runner's catch is the body's own (forward held): a jump that falls short of a shelf takes its lip; the air
            // mantle needs jump pressed in the air and is never assumed
            RunnerRules.Edge e = grab(g, b, fx, fz);
            if (e != null) {
                hold(b, e);
                return null;
            }
            air(b, fx, fz, push);
            if (b.y < -64) return "fell";
        }
        return "never came down";
    }

    /** RunnerMoves.tryGrab: facing an axis, moving toward it (forward is held through every move), a lip within reach. */
    private static RunnerRules.@Nullable Edge grab(BlockGetter g, Body b, double fx, double fz) {
        int axis = Math.abs(fx) >= Math.abs(fz) ? 0 : 2;
        if (Math.max(Math.abs(fx), Math.abs(fz)) < 0.643) return null;
        int sign = (axis == 0 ? fx : fz) > 0 ? 1 : -1;
        double toward = (axis == 0 ? b.vx : b.vz) * sign;
        if (toward <= -0.02) return null;
        return RunnerRules.findEdge(g, CTX, b.m, b.x, b.y, b.z, axis, sign, RunnerRules.HANG_REACH, RunnerRules.HANG_LO, RunnerRules.HANG_HI, 0, 0.2);
    }

    private static void hold(Body b, RunnerRules.Edge e) {
        b.hang = e;
        b.x = e.hx();
        b.y = e.hy();
        b.z = e.hz();
        b.vx = b.vy = b.vz = 0;
        b.ground = false;
    }

    /**
     * A wall run off the edge ahead (RunnerMoves.tryRun, runTick, runApply, wallJump), and {@code links} wall jumps:
     * slow ones on the fourth tick of each run, fast ones on the second; or, with {@code switchAt} (a switchback), one wall
     * jump across to the facing wall when the wall run on ends {@code switchAt} ahead. Ends standing, or hanging on a lip.
     */
    private static @Nullable String wallRun(BlockGetter g, Body b, float yaw, double fx, double fz, Rank rank, boolean fast, int links, double switchAt) {
        String why = toEdge(g, b, fx, fz, 3.5);
        if (why != null) return why;
        sprintJump(b, fx, fz, fast);
        int link = 0, runs = 0, jumpAt = fast ? 2 : 4;
        long lastWall = Long.MIN_VALUE;
        double gfx = fx, gfz = fz;
        for (int t = 0; t < MAX_TICKS; t++) {
            step(g, b, H);
            if (b.ground) return runs > links ? null : String.format("landed after %d runs of %d", runs, links + 1);
            RunnerRules.Edge e = grab(g, b, gfx, gfz);
            if (e != null) {
                hold(b, e);
                return runs > links ? null : "caught a lip before the last run";
            }
            float look = (float) (Mth.atan2(-gfx, gfz) * Mth.RAD_TO_DEG);
            RunnerRules.Wall w = RunnerRules.findWall(g, CTX, b.m, b.x, b.y, b.z, look, RunnerRules.RUN_REACH);
            boolean starts = w != null && w.id() != lastWall
                    && (link > 0 || b.vx * w.tx() + b.vz * w.tz() >= RunnerRules.RUN_MIN_ALONG)
                    && !RunnerRules.groundWithin(g, CTX, b.m, b.x, b.y, b.z, RunnerRules.RUN_MIN_HEIGHT);
            if (!starts) {
                air(b, gfx, gfz, fast ? 0.026 : 0.02);
                if (b.y < -64) return "fell";
                continue;
            }
            runs++;
            lastWall = w.id();
            int len = RunnerRules.runLength(link, rank), grip = RunnerRules.runGrip(link);
            if (!fast) len = (int) Math.floor(len * SLOW);
            double along = b.vx * w.tx() + b.vz * w.tz();
            double speed = fast ? Mth.clamp(Math.max(along, RunnerRules.RUN_SPEED_MIN), RunnerRules.RUN_SPEED_MIN, RunnerRules.RUN_SPEED_MAX) : RunnerRules.RUN_SPEED_MIN;
            double runVy = Mth.clamp(b.vy, -0.6, 0.42);
            double gap = w.gap();
            int miss = 0;
            boolean jumped = false;
            for (int age = 0; ; ) {
                double tgt = RunnerRules.runTarget(age, len, grip);
                runVy += (tgt - runVy) * RunnerRules.RUN_APPROACH;
                double close = Mth.clamp(gap - RunnerRules.RUN_GAP, -0.1, 0.1);
                b.vx = w.tx() * speed - w.nx() * close;
                b.vz = w.tz() * speed - w.nz() * close;
                b.vy = runVy;
                step(g, b, H);
                age++;
                if (b.ground) return runs > links ? null : String.format("landed on run %d of %d", runs, links + 1);
                // a chain jumps on its tick; a switch jumps across when the wall ahead ends (early: a body's length before)
                boolean jumpNow = switchAt < 0 ? age >= jumpAt
                        : RunnerRules.wallOn(g, CTX, b.m, b.x + w.tx() * switchAt, b.y, b.z + w.tz() * switchAt, w.axis(), w.side(),
                        RunnerRules.RUN_REACH + 0.1, w.tx(), w.tz()) == null;
                if (link < links && link < RunnerRules.LINKS && jumpNow) {
                    double keep = speed * RunnerRules.WALL_JUMP_KEEP;
                    b.vx = w.nx() * RunnerRules.WALL_JUMP_OUT + w.tx() * keep;
                    b.vz = w.nz() * RunnerRules.WALL_JUMP_OUT + w.tz() * keep;
                    b.vy = RunnerRules.WALL_JUMP_UP;
                    link++;
                    jumped = true;
                    break;
                }
                if (age >= len) break;
                RunnerRules.Edge ahead = grab(g, b, w.tx(), w.tz());
                if (ahead != null) {
                    hold(b, ahead);
                    return runs > links ? null : "caught a lip before the last run";
                }
                if (b.bumped) break;
                RunnerRules.Wall on = RunnerRules.wallOn(g, CTX, b.m, b.x, b.y, b.z, w.axis(), w.side(), RunnerRules.RUN_REACH + 0.1, w.tx(), w.tz());
                boolean touching = on != null && Math.abs(on.plane() - w.plane()) < 0.1;
                miss = touching ? 0 : miss + 1;
                if (miss > 1) break;
                gap = touching ? on.gap() : RunnerRules.RUN_GAP;
            }
            // off the wall the body faces on along it (a chain's next run is along the same way)
            gfx = w.tx();
            gfz = w.tz();
            if (!jumped) air(b, gfx, gfz, 0.02);
        }
        return "never came down";
    }

    private static @Nullable String mantle(BlockGetter g, Body b, float yaw, double fx, double fz) {
        for (double s = 0; s <= 1.5; s += 0.05) {
            double x = b.x + fx * s, z = b.z + fz * s;
            BodyRules.Ledge l = BodyRules.findLedge(g, CTX, x, b.y, z, yaw, BodyRules.MANTLE_REACH);
            if (l != null) {
                if (l.height() < 1.26) return String.format("a %.2f rise: a jump does it", l.height());
                b.x = l.x();
                b.y = l.y();
                b.z = l.z();
                return null;
            }
            if (blocked(g, b, x + fx * 0.05, b.y, z + fz * 0.05, H)) return "no ledge to mantle at the face";
            if (!onFloor(g, b, x, z)) return "the floor ends before the ledge";
        }
        return "nothing to mantle within reach";
    }

    /** Crouching forward off the edge into a hang on it (RunnerRules.lowerEdge). */
    private static @Nullable String lower(BlockGetter g, Body b, float yaw, double fx, double fz) {
        for (double s = 0; s <= 1.5; s += 0.05) {
            double x = b.x + fx * s, z = b.z + fz * s;
            // crouching keeps the body on the edge: no further than its box is still over the floor
            if (!onFloor(g, b, x, z)) break;
            if (blocked(g, b, x, b.y, z, 1.5)) return "walked into something before the edge";
            RunnerRules.Edge e = RunnerRules.lowerEdge(g, CTX, b.m, x, b.y, z, yaw);
            if (e != null) {
                hold(b, e);
                return null;
            }
        }
        return "no edge to lower onto";
    }

    /** Along the hang, {@code cells} blocks: + to the left as the body faces the wall, - to the right. */
    private static @Nullable String shimmy(BlockGetter g, Body b, int cells) {
        RunnerRules.Edge e = b.hang;
        if (e == null) return "not hanging";
        int dir = cells >= 0 ? 1 : -1;
        double tx = e.fz() * dir, tz = -e.fx() * dir;
        for (double s = 0.1; s <= Math.abs(cells) + 1e-6; s += 0.1) {
            RunnerRules.Edge on = RunnerRules.edgeNear(g, CTX, b.m, e.hx() + tx * s, e.hy(), e.hz() + tz * s, e.axis(), e.sign(), e.top(), 0.3, RunnerRules.HANG_REACH + 0.05);
            if (on == null) return String.format("the lip ends %.1f along", s);
            hold(b, on);
        }
        return null;
    }

    /** Out of the hang onto its top (RunnerRules.climbOut); standing already (an air mantle took the ledge), nothing to do. */
    private static @Nullable String climb(BlockGetter g, Body b) {
        if (b.hang == null) return b.ground ? null : "not hanging";
        double[] out = RunnerRules.climbOut(g, CTX, b.m, b.hang);
        if (out == null) return "no room to climb out";
        b.hang = null;
        b.x = out[0];
        b.y = out[1];
        b.z = out[2];
        b.ground = true;
        return null;
    }

    private static @Nullable String drop(BlockGetter g, Body b) {
        RunnerRules.Edge e = b.hang;
        if (e == null) return "not hanging";
        RunnerRules.Edge below = RunnerRules.edgeBelow(g, CTX, b.m, e.hx(), e.hy(), e.hz(), e, 0);
        if (below == null) return "no lip below to drop to";
        hold(b, below);
        return null;
    }

    /** Letting go of a hang onto ground no more than three and a half below the hanging feet. */
    private static @Nullable String let(BlockGetter g, Body b) {
        if (b.hang == null) return "not hanging";
        b.hang = null;
        double from = b.y;
        b.vy = 0;
        for (int t = 0; t < MAX_TICKS; t++) {
            step(g, b, H);
            if (b.ground) return from - b.y > 3.5 ? String.format("a %.1f fall after letting go", from - b.y) : null;
            air(b, 0, 0, 0);
        }
        return "never came down";
    }

    /** A ledge jump from the hang: up ({@code dir} 0), left (1) or right (-1), within 80% of its reach. */
    private static @Nullable String ledge(BlockGetter g, Body b, Rank rank, int dir) {
        RunnerRules.Edge e = b.hang;
        if (e == null) return "not hanging";
        if (dir == 0) {
            RunnerRules.Edge up = RunnerRules.findEdge(g, CTX, b.m, e.hx(), e.hy(), e.hz(), e.axis(), e.sign(), 0.6,
                    RunnerRules.HANG_DROP + 0.4, RunnerRules.HANG_DROP + RunnerRules.ledgeUp(rank) * SLOW, 0, 0.2);
            if (up == null) return "no lip within 80% of a ledge jump up";
            if (blocked(g, b, e.hx(), e.hy() + 0.05, e.hz(), up.hy() + H - e.hy() - 0.05)) return "something over the hang";
            hold(b, up);
            return null;
        }
        double tx = e.fz() * dir, tz = -e.fx() * dir;
        boolean gap = false;
        for (double d = 0.5; d <= RunnerRules.ledgeSide(rank) * SLOW + 1e-6; d += 0.25) {
            RunnerRules.Edge s = RunnerRules.findEdge(g, CTX, b.m, e.hx() + tx * d, e.hy(), e.hz() + tz * d, e.axis(), e.sign(), 0.6,
                    RunnerRules.HANG_DROP - 1.0, RunnerRules.HANG_DROP + 1.0, 0, 0.2);
            if (s == null) {
                gap = true;
                continue;
            }
            if (gap) {
                hold(b, s);
                return null;
            }
        }
        return "no lip past a gap within 80% of a ledge jump sideways";
    }

    /** Walking off the edge ahead and falling, never more than {@code most}. */
    private static @Nullable String fall(BlockGetter g, Body b, double fx, double fz, int most) {
        String why = toEdge(g, b, fx, fz, 3.5);
        if (why != null) return why;
        double from = b.y;
        b.vx = fx * 0.2;
        b.vz = fz * 0.2;
        b.vy = 0;
        b.ground = false;
        for (int t = 0; t < MAX_TICKS; t++) {
            step(g, b, H);
            if (b.ground) return from - b.y > most + 0.01 ? String.format("a %.1f fall", from - b.y) : null;
            air(b, fx, fz, 0.02);
        }
        return "never came down";
    }

    /** A walk on along the floor, {@code cells} blocks: up a block at most (a jump), down one at most. */
    private static @Nullable String walk(BlockGetter g, Body b, double fx, double fz, int cells) {
        for (double s = 0.1; s <= cells + 1e-6; s += 0.1) {
            double x = b.x + fx * 0.1, z = b.z + fz * 0.1;
            double t = floorUnder(g, b, x, z, b.y - 1.05, b.y + 1.05);
            if (Double.isNaN(t)) return String.format("no floor %.1f along", s);
            if (blocked(g, b, x, t, z, H)) return String.format("no room to stand %.1f along", s);
            b.x = x;
            b.z = z;
            b.y = t;
        }
        b.ground = true;
        return null;
    }

    /**
     * Up a wall line (a pipe, a ladder, ivy) from its foot ahead: the grip climbs it to its top (GripRules.climbTop), takes
     * the lip over it (GripRules.topLip) and climbs out (RunnerRules.climbOut), as GripMoves does.
     */
    private static @Nullable String grip(BlockGetter g, Body b, float yaw, double fx, double fz) {
        GripRules.Line l = null;
        for (double s = 0; s <= 1.5 && l == null; s += 0.05) {
            double x = b.x + fx * s, z = b.z + fz * s;
            l = GripRules.find(g, b.m, x, b.y, z, yaw);
            if (l == null && blocked(g, b, x + fx * 0.05, b.y, z + fz * 0.05, H)) break;
        }
        if (l == null) return "no line at the foot";
        if (l.free()) return "a free rope is no wall line";
        if (!GripRules.facing(l, yaw)) return "the line faces away";
        double top = GripRules.climbTop(g, CTX, b.m, l, b.y);
        int axis = Math.abs(l.fx()) >= Math.abs(l.fz()) ? 0 : 2;
        int sign = (axis == 0 ? l.fx() : l.fz()) > 0 ? 1 : -1;
        RunnerRules.Edge lip = GripRules.topLip(g, CTX, b.m, l.x(), top, l.z(), axis, sign, 0);
        if (lip == null) return String.format("no lip over the line's top at %.1f", top);
        b.x = l.x();
        b.z = l.z();
        b.y = top;
        hold(b, lip);
        return climb(g, b);
    }

    /** A sprint jump off the edge ahead onto a free rope (GripMoves.tryStart in the air, forward held). */
    private static @Nullable String rope(BlockGetter g, Body b, float yaw, double fx, double fz, boolean fast) {
        String why = toEdge(g, b, fx, fz, 3.5);
        if (why != null) return why;
        sprintJump(b, fx, fz, fast);
        for (int t = 0; t < MAX_TICKS; t++) {
            step(g, b, H);
            GripRules.Line l = GripRules.find(g, b.m, b.x, b.y, b.z, yaw);
            if (l != null) {
                b.line = l;
                b.x = l.x();
                b.z = l.z();
                b.vx = b.vy = b.vz = 0;
                return null;
            }
            if (b.ground) return "landed without taking the rope";
            air(b, fx, fz, fast ? 0.026 : 0.02);
            if (b.y < -64) return "fell";
        }
        return "never came down";
    }

    /** Down the held line to the floor (Shift: the grip's slide). */
    private static @Nullable String slip(BlockGetter g, Body b) {
        GripRules.Line l = b.line;
        if (l == null) return "not on a line";
        double v = 0;
        for (int t = 0; t < 400; t++) {
            if (!GripRules.holds(g, b.m, l.bx(), l.bz(), b.y)) {
                // the line ends over the floor: off it and down
                b.line = null;
                b.vy = -v;
                for (int k = 0; k < MAX_TICKS; k++) {
                    step(g, b, H);
                    if (b.ground) return null;
                    air(b, 0, 0, 0);
                }
                return "never came down off the line";
            }
            v = GripRules.slide(v);
            b.vx = b.vz = 0;
            b.vy = -v;
            step(g, b, H);
            if (b.ground) {
                b.line = null;
                return null;
            }
        }
        return "the line goes on and on";
    }

    /**
     * The side swing from a free rope (Jump with left or right: out {@link GripRules#SIDE_OUT}, away
     * {@link GripRules#SIDE_AWAY}, up {@link GripRules#SIDE_UP}) to the next rope, taken in the air while the swing lasts
     * (GripMoves: no regrab for 3 ticks, the swing's catch for 14).
     */
    private static @Nullable String swing(BlockGetter g, Body b, int dir, boolean fast) {
        GripRules.Line l = b.line;
        if (l == null) return "not on a rope";
        if (!l.free()) return "a side swing is from a free rope";
        double tx = l.fz() * dir, tz = -l.fx() * dir;
        b.line = null;
        b.vx = tx * GripRules.SIDE_OUT - l.fx() * GripRules.SIDE_AWAY;
        b.vz = tz * GripRules.SIDE_OUT - l.fz() * GripRules.SIDE_AWAY;
        b.vy = GripRules.SIDE_UP;
        b.ground = false;
        float look = (float) (Mth.atan2(-l.fx(), l.fz()) * Mth.RAD_TO_DEG);
        for (int t = 1; t <= 14; t++) {
            step(g, b, H);
            if (t >= 3) {
                GripRules.Line n = GripRules.find(g, b.m, b.x, b.y, b.z, look);
                if (n != null && (n.bx() != l.bx() || n.bz() != l.bz())) {
                    b.line = n;
                    b.x = n.x();
                    b.z = n.z();
                    b.vx = b.vy = b.vz = 0;
                    return null;
                }
            }
            if (b.ground) return "landed before the next rope";
            air(b, tx, tz, fast ? 0.02 : 0);
        }
        return "no rope within the swing";
    }

    /**
     * Where a side swing from a rope held at (x, y, z), facing (fx, fz), puts the body after {@code ticks}: slow (no keys)
     * or fast (the side key held). The chain-fall beat hangs its next rope in the cell both pass through.
     */
    public static double[] swingAt(double x, double y, double z, double fx, double fz, int dir, int ticks, boolean fast) {
        double tx = fz * dir, tz = -fx * dir;
        double vx = tx * GripRules.SIDE_OUT - fx * GripRules.SIDE_AWAY, vz = tz * GripRules.SIDE_OUT - fz * GripRules.SIDE_AWAY, vy = GripRules.SIDE_UP;
        for (int t = 0; t < ticks; t++) {
            x += vx;
            y += vy;
            z += vz;
            vy = (vy - 0.08) * 0.98;
            vx = vx * 0.91 + (fast ? tx * 0.02 : 0);
            vz = vz * 0.91 + (fast ? tz * 0.02 : 0);
        }
        return new double[]{x, y, z};
    }
}
