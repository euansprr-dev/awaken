package dev.awaken.gates.world;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.awaken.core.AwakenCommands;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateRegistry;
import dev.awaken.gates.GateService;
import dev.awaken.gates.GateState;
import dev.awaken.gates.run.RunState;
import dev.awaken.gates.run.Runs;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;

/**
 * The interiors' wiring: their GameTests and a game-master tour command for autopilot scripts, which can't know a
 * gate's id or where its rooms are:
 * <ul>
 *   <li>{@code /awaken gate interior enter}: walk into the newest standing gate (yours first);</li>
 *   <li>{@code /awaken gate interior goto <start|piece|shrine|ante|lord|rig>}: inside a gate, stand where that place reads
 *   best (the first room facing the way on, the doorway of the floor's biggest set piece, the room before the Shrine
 *   facing its blue door, the antechamber facing the arena, the arena's doorway facing the Lord's spot, the Gate Rig's
 *   room from its doorway);</li>
 *   <li>{@code /awaken gate rig <start|solve>}: on your floor, start the rig room's first track as if its trigger fired,
 *   or turn a Mirror Hall's mirrors to their solution (tours shoot the moments without playing them).</li>
 * </ul>
 */
public final class Interiors {
    private Interiors() {}

    public static void init() {
        InteriorTests.register();
        AwakenCommands.add((root, ctx) -> root.then(Commands.literal("gate").then(Commands.literal("interior").requires(AwakenCommands.admin())
                .then(Commands.literal("enter").executes(Interiors::enter))
                .then(Commands.literal("goto").then(Commands.argument("spot", StringArgumentType.word())
                        .suggests((c, b) -> {
                            for (String s : List.of("start", "piece", "small", "shrine", "ante", "lord", "rig", "place", "place2")) b.suggest(s);
                            return b.buildFuture();
                        })
                        .executes(c -> go(c, StringArgumentType.getString(c, "spot"))))))));
        AwakenCommands.add((root, ctx) -> root.then(Commands.literal("gate").then(Commands.literal("rig").requires(AwakenCommands.admin())
                .then(Commands.literal("start").executes(c -> rig(c, false)))
                .then(Commands.literal("solve").executes(c -> rig(c, true)))
                .then(Commands.literal("at").then(Commands.argument("part", StringArgumentType.word())
                        .suggests((c, b) -> {
                            for (String s : List.of("start", "first", "gap", "ledge", "press", "wall", "ring", "edge", "runup", "last")) b.suggest(s);
                            return b.buildFuture();
                        })
                        .executes(c -> at(c, StringArgumentType.getString(c, "part"))))))));
    }

    /**
     * Tours: stands you on a part of the Gauntlet on this floor (any floor's, if this one has none), facing the way on:
     * the start landing, the first row (the clock starts), the row before a gap, a ledge, a press or the wall-run, a
     * ring, or the far landing.
     */
    private static int at(CommandContext<CommandSourceStack> c, String part) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        RunState run = Runs.run(p);
        GateRecord g = run.active() ? GateRegistry.get(c.getSource().getServer()).gate(run.gateId) : null;
        ServerLevel gl = GateService.gateLevel(c.getSource().getServer());
        if (g == null || gl == null) {
            c.getSource().sendFailure(Component.literal("Not inside a gate."));
            return 0;
        }
        GateLayout l = GateLayout.of(g.plan);
        FloorLayout f = null;
        Room r = null;
        FloorLayout here = l.floor(run.area, run.floor);
        List<FloorLayout> floors = new java.util.ArrayList<>();
        if (here != null) floors.add(here);
        floors.addAll(l.main);
        // the gate's fullest course (this floor's on a tie)
        int best = -1;
        for (FloorLayout o : floors) {
            for (Room q : o.rooms) {
                if (q.rig == null || q.rig.kind != RigRoom.Kind.GAUNTLET) continue;
                RigGauntlet.Course qc = q.rig.course;
                int parts = (qc.presses() > 0 ? 2 : 0) + (qc.runs() > 0 ? 1 : 0) + (qc.ledges() > 0 ? 1 : 0) + (qc.rings().length > 0 ? 2 : 0);
                if (parts > best) {
                    best = parts;
                    r = q;
                    f = o;
                }
            }
        }
        if (r == null) {
            c.getSource().sendFailure(Component.literal("No Gauntlet in this gate."));
            return 0;
        }
        RigGauntlet.Course co = r.rig.course;
        int k = switch (part) {
            case "start" -> 0;
            case "first" -> co.first();
            // the trial's edge, and four rows back from it (the run-up to the leap)
            case "edge" -> co.edge();
            case "runup" -> co.edge() < 0 ? -1 : co.edge() - 3;
            case "last" -> co.last();
            case "ring" -> co.rings().length > 0 ? co.rings()[0] : -1;
            default -> {
                byte want = switch (part) {
                    case "gap" -> RigGauntlet.GAP;
                    case "ledge" -> RigGauntlet.LEDGE;
                    case "press" -> RigGauntlet.PRESS;
                    case "wall" -> RigGauntlet.RUN;
                    default -> -1;
                };
                int found = -1;
                for (int i = co.first(); i < co.last() && found < 0; i++) if (co.rowKind()[i] == want) found = i;
                if (found < 0 || want == RigGauntlet.LEDGE) yield found;
                // a floor row before it (a press: a few back, to see it come down)
                int stand = found - 1;
                for (int i = found - 1; i >= co.first(); i--) {
                    byte kk = co.rowKind()[i];
                    if (kk != RigGauntlet.FLOOR && kk != RigGauntlet.RING) break;
                    stand = i;
                    if (want != RigGauntlet.PRESS || found - i >= 3) break;
                }
                yield stand;
            }
        };
        if (k < 0) {
            c.getSource().sendFailure(Component.literal("This Gauntlet has no " + part + "."));
            return 0;
        }
        double[] q = co.rowCentre(k), on = co.rowCentre(Math.min(co.rows() - 1, k + 3));
        BlockPos o = GateService.world(g, f, 0, r.dy, 0);
        float yaw = (float) Math.toDegrees(Math.atan2(-(on[0] - q[0]), on[1] - q[1]));
        p.teleportTo(gl, o.getX() + q[0], o.getY() + 0.05, o.getZ() + q[1], Set.of(), yaw, 12F, true);
        return 1;
    }

    private static int rig(CommandContext<CommandSourceStack> c, boolean solve) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        RunState run = Runs.run(p);
        GateRecord g = run.active() ? GateRegistry.get(c.getSource().getServer()).gate(run.gateId) : null;
        ServerLevel gl = GateService.gateLevel(c.getSource().getServer());
        FloorLayout f = g == null ? null : GateLayout.of(g.plan).floor(run.area, run.floor);
        Room r = null;
        if (f != null) for (Room o : f.rooms) if (o.rig != null) r = o;
        if (r == null || gl == null) {
            c.getSource().sendFailure(Component.literal("No Gate Rig room on this floor."));
            return 0;
        }
        if (!solve) return dev.awaken.gates.run.RigDirector.force(gl, g, f, r) ? 1 : 0;
        if (r.rig.kind != RigRoom.Kind.MIRROR_HALL) {
            c.getSource().sendFailure(Component.literal("Only a Mirror Hall can be solved."));
            return 0;
        }
        for (RigMirrors.Mirror m : r.rig.mirrors.mirrors()) {
            gl.setBlock(GateService.world(g, f, m.x(), r.dy + RigMirrors.BEAM_DY, m.z()), RigRooms.mirror(m.solution()), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
        return 1;
    }

    private static int enter(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        // the gate the game master opened last, if it's ready (a tour's: not a gate that formed by itself beside it since)
        GateRecord last = GateRegistry.get(c.getSource().getServer()).gate(GateService.lastOpened);
        if (last != null && last.state.enterable() && last.interiorReady && last.state != GateState.BUILDING) return Runs.enter(p, last) ? last.id : 0;
        GateRecord best = null;
        for (GateRecord g : GateRegistry.get(c.getSource().getServer()).all()) {
            if (!g.state.enterable() || !g.interiorReady || g.state == GateState.BUILDING) continue;
            boolean mine = g.owner.map(o -> o.equals(p.getUUID())).orElse(false);
            boolean bestMine = best != null && best.owner.map(o -> o.equals(p.getUUID())).orElse(false);
            if (best == null || (mine && !bestMine) || (mine == bestMine && g.id > best.id)) best = g;
        }
        if (best == null) {
            c.getSource().sendFailure(Component.literal("No gate ready to enter (is it still forming?)."));
            return 0;
        }
        return Runs.enter(p, best) ? best.id : 0;
    }

    private static int go(CommandContext<CommandSourceStack> c, String spot) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        RunState run = Runs.run(p);
        GateRecord g = run.active() ? GateRegistry.get(c.getSource().getServer()).gate(run.gateId) : null;
        ServerLevel gl = GateService.gateLevel(c.getSource().getServer());
        if (g == null || gl == null) {
            c.getSource().sendFailure(Component.literal("Not inside a gate."));
            return 0;
        }
        GateLayout l = GateLayout.of(g.plan);
        FloorLayout f = spot.equals("lord") || spot.equals("ante") ? l.main.get(l.main.size() - 1) : l.floor(run.area, run.floor);
        if (f == null) f = l.main.get(0);
        // the rig room may be on another floor: the first that has one
        if (spot.equals("rig") && view(f, spot) == null) for (FloorLayout o : l.main) if (view(o, spot) != null) {
            f = o;
            break;
        }
        double[] view = view(f, spot);
        if (view == null) {
            c.getSource().sendFailure(Component.literal("No " + spot + " on this floor."));
            return 0;
        }
        Vec3 at = Vec3.atLowerCornerOf(GateService.world(g, f, 0, 0, 0)).add(view[0], view[1] - f.baseY, view[2]);
        p.teleportTo(gl, at.x, at.y, at.z, Set.of(), (float) view[3], (float) view[4], true);
        return 1;
    }

    /**
     * Where to stand and look for a tour shot: floor-local x, absolute y, z, then yaw and pitch (Minecraft degrees).
     * Shared with the offline renderer's cameras (InteriorTests dumps the same views).
     */
    public static double[] view(FloorLayout f, String spot) {
        Room r;
        switch (spot) {
            case "start" -> {
                r = f.room(f.start);
                Room.Door d = r.pathOut != null ? r.pathOut : r.doors.isEmpty() ? null : r.doors.get(0);
                if (d == null) return null;
                return lookFrom(f, r, d, -5, 0, d.centerX(), f.baseY + d.y + 2.0, d.centerZ());
            }
            case "piece" -> {
                r = null;
                for (Room o : f.rooms) if (o.piece != null && !o.piece.arena && (r == null || o.area() > r.area())) r = o;
                if (r == null) return null;
                Room.Door d = r.pathIn != null ? r.pathIn : r.doors.get(0);
                return lookFrom(f, r, d, 3, -1, r.centerX(), f.baseY + r.dy + 4.0, r.centerZ());
            }
            case "small" -> {
                // a single room's piece, the theme's second small one if the floor has it
                r = null;
                for (Room o : f.rooms) {
                    if (o.piece == null || o.piece.arena || o.area() > 1) continue;
                    if (r == null || (o.piece.ordinal() > r.piece.ordinal())) r = o;
                }
                if (r == null) return null;
                Room.Door d = r.pathIn != null ? r.pathIn : r.doors.get(0);
                return lookFrom(f, r, d, 1, 0, r.centerX(), f.baseY + r.dy + 2.5, r.centerZ());
            }
            case "shrine" -> {
                Room end = f.room(f.end);
                List<Integer> path = f.criticalPath();
                if (path.size() < 2) return null;
                r = f.room(path.get(path.size() - 2));
                Room.Door d = r.doorTo(end.id);
                if (d == null) return null;
                // where you walk in, facing the blue door
                if (r.pathIn != null) return lookFrom(f, r, r.pathIn, 2, 0, d.centerX(), f.baseY + d.y + 2.0, d.centerZ());
                return lookFrom(f, r, d, -5, 0, d.centerX(), f.baseY + d.y + 2.0, d.centerZ());
            }
            case "ante" -> {
                r = null;
                for (Room o : f.rooms) if (o.type == Room.Type.ANTE) r = o;
                if (r == null || r.pathOut == null) return null;
                if (r.pathIn != null) return lookFrom(f, r, r.pathIn, 2, 0, r.pathOut.centerX(), f.baseY + r.pathOut.y + 2.5, r.pathOut.centerZ());
                return lookFrom(f, r, r.pathOut, -5, 0, r.pathOut.centerX(), f.baseY + r.pathOut.y + 2.5, r.pathOut.centerZ());
            }
            case "rig" -> {
                r = null;
                for (Room o : f.rooms) if (o.rig != null) r = o;
                if (r == null || r.pathIn == null) return null;
                // in its doorway, looking down the room toward the way on
                Room.Door out = r.pathOut != null ? r.pathOut : r.pathIn;
                return lookFrom(f, r, r.pathIn, 0, 0, out.centerX(), f.baseY + r.dy + 1.5, out.centerZ());
            }
            case "place", "place2" -> {
                // a world floor's biggest place (place2: its second), from just inside where you walk in (or a doorway), over its ground
                if (!f.isWorld()) return null;
                List<Room> big = new java.util.ArrayList<>();
                for (Room o : f.rooms) if (o.type != Room.Type.BOSS && o.rig == null && !o.doors.isEmpty() && o.area() > 1) big.add(o);
                big.sort((a, b) -> b.area() - a.area());
                int k = spot.equals("place") ? 0 : 1;
                if (big.size() <= k) return null;
                r = big.get(k);
                double[] v = lookFrom(f, r, r.pathIn != null ? r.pathIn : r.doors.get(0), 3, 0, r.centerX(), 0, r.centerZ());
                // a block over where you stand there, looking at the middle a block and a half over its ground
                double y = f.baseY + r.dy + f.ground((int) Math.floor(v[0]), (int) Math.floor(v[2])) + 1;
                double ddx = r.centerX() + 0.5 - v[0], ddz = r.centerZ() + 0.5 - v[2];
                double ddy = f.baseY + r.dy + f.ground(r.centerX(), r.centerZ()) + 1.5 - (y + 1.62);
                return new double[]{v[0], y, v[2], v[3], -Math.toDegrees(Math.atan2(ddy, Math.hypot(ddx, ddz)))};
            }
            case "lord" -> {
                r = f.room(f.end);
                if (r.doors.isEmpty()) return null;
                Room.Door d = r.doors.get(0);
                // just through the doors: the Lord on its lit medallion, the arena opening round it
                return lookFrom(f, r, d, 1, 0, r.centerX(), f.baseY + r.dy + 4.0, r.centerZ());
            }
            default -> {
                return null;
            }
        }
    }

    /**
     * A view inside room r from a point on door d's axis: {@code in} blocks into the room from the wall line (negative:
     * measured back from the room's centre toward the far side, away from the door), looking at (tx, ty, tz).
     */
    private static double[] lookFrom(FloorLayout f, Room r, Room.Door d, int in, int side, double tx, double ty, double tz) {
        double x, z;
        double dcx = d.centerX(), dcz = d.centerZ();
        double nx = 0, nz = 0; // into the room from the door
        switch (d.side) {
            case 0 -> nz = 1;
            case 2 -> nz = -1;
            case 1 -> nx = -1;
            default -> nx = 1;
        }
        if (in >= 0) {
            x = dcx + nx * (RoomGeometry.INSET + in);
            z = dcz + nz * (RoomGeometry.INSET + in);
        } else {
            // from the middle of the room, backing away from the door (n points away from it)
            x = r.centerX() + 0.5 + nx * (-in);
            z = r.centerZ() + 0.5 + nz * (-in);
            x = Math.max(r.x0() + 1.5, Math.min(r.x1() - 0.5, x));
            z = Math.max(r.z0() + 1.5, Math.min(r.z1() - 0.5, z));
        }
        int fx = (int) Math.floor(x), fz = (int) Math.floor(z);
        double y = f.baseY + r.dy + Math.max(0, RoomGeometry.inside(r, fx, fz) ? RoomGeometry.floorOffset(r, fx, fz) : 0);
        double eyeY = y + 1.62;
        double ddx = tx + 0.5 - x, ddz = tz + 0.5 - z, ddy = ty - eyeY;
        double yaw = Math.toDegrees(Math.atan2(-ddx, ddz));
        double pitch = -Math.toDegrees(Math.atan2(ddy, Math.hypot(ddx, ddz)));
        return new double[]{x, y, z, yaw, pitch};
    }
}
