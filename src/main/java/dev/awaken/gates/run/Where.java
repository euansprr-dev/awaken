package dev.awaken.gates.run;

import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateRegistry;
import dev.awaken.gates.world.Cells;
import dev.awaken.gates.world.FloorLayout;
import dev.awaken.gates.world.GateLayout;
import dev.awaken.gates.world.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

/** A position inside the gate dimension resolved to gate, floor and room (floor-local x/z). */
public record Where(GateRecord gate, GateLayout layout, FloorLayout floor, Room room, int x, int z) {

    public static Where at(MinecraftServer server, BlockPos pos) {
        int cell = Cells.cellAt(pos.getX(), pos.getZ());
        if (cell < 0) return null;
        GateRecord g = GateRegistry.get(server).byCell(cell);
        if (g == null) return null;
        GateLayout l = GateLayout.of(g.plan);
        int lx = Cells.localX(cell, pos.getX()), lz = Cells.localZ(cell, pos.getZ());
        FloorLayout f = l.floorAt(lx, pos.getY());
        if (f == null) return new Where(g, l, null, null, 0, 0);
        Room r = f.roomAtLocal(lx, lz);
        return new Where(g, l, f, r, f.toFloorX(lx), f.toFloorZ(lz));
    }

    public int area() {
        return floor == null ? 0 : floor.area;
    }

    public int floorNo() {
        return floor == null ? 0 : floor.floor;
    }
}
