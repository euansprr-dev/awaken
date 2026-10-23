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
 *   <li>{@code /awaken gate interior goto <start|piece|shrine|ante|lord>}: inside a gate, stand where that place reads
 *   best (the first room facing the way on, the doorway of the floor's biggest set piece, the room before the Shrine
 *   facing its blue door, the antechamber facing the arena, the arena's doorway facing the Lord's spot).</li>
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
                            for (String s : List.of("start", "piece", "shrine", "ante", "lord")) b.suggest(s);
                            return b.buildFuture();
                        })
                        .executes(c -> go(c, StringArgumentType.getString(c, "spot"))))))));
    }

    private static int enter(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
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
            case "shrine" -> {
                Room end = f.room(f.end);
                List<Integer> path = f.criticalPath();
                if (path.size() < 2) return null;
                r = f.room(path.get(path.size() - 2));
                Room.Door d = r.doorTo(end.id);
                if (d == null) return null;
                return lookFrom(f, r, d, -5, 0, d.centerX(), f.baseY + d.y + 2.0, d.centerZ());
            }
            case "ante" -> {
                r = null;
                for (Room o : f.rooms) if (o.type == Room.Type.ANTE) r = o;
                if (r == null || r.pathOut == null) return null;
                return lookFrom(f, r, r.pathOut, -5, 0, r.pathOut.centerX(), f.baseY + r.pathOut.y + 2.5, r.pathOut.centerZ());
            }
            case "lord" -> {
                r = f.room(f.end);
                if (r.doors.isEmpty()) return null;
                Room.Door d = r.doors.get(0);
                return lookFrom(f, r, d, 4, 0, r.centerX(), f.baseY + r.dy + 5.0, r.centerZ());
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
