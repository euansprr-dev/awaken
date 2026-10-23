package dev.awaken.gates.world;

import java.util.ArrayList;
import java.util.List;

/**
 * Block positions of multi-block features, shared by the painter and the server (which later opens cages and walls).
 * Entries are {floor-local x, y offset above the room floor, floor-local z}.
 */
public final class FeatureBlocks {
    private FeatureBlocks() {}

    public static List<int[]> vaultBars(Room.Feature f) {
        int x = f.x(), z = f.z(), y = f.y();
        return List.of(new int[]{x + 1, y, z}, new int[]{x - 1, y, z}, new int[]{x, y, z + 1}, new int[]{x, y, z - 1},
                new int[]{x, y + 1, z});
    }

    /** index 0: the gate stands in a plane along X; index 1: along Z. */
    public static List<int[]> innerGateFrame(Room.Feature f) {
        List<int[]> out = new ArrayList<>();
        for (int dy = 0; dy <= 3; dy++) {
            out.add(along(f, -2, dy));
            out.add(along(f, 2, dy));
        }
        for (int d = -2; d <= 2; d++) out.add(along(f, d, 4));
        return out;
    }

    public static List<int[]> innerGatePortal(Room.Feature f) {
        List<int[]> out = new ArrayList<>();
        for (int d = -1; d <= 1; d++) for (int dy = 0; dy <= 3; dy++) out.add(along(f, d, dy));
        return out;
    }

    private static int[] along(Room.Feature f, int d, int dy) {
        return f.index() == 1 ? new int[]{f.x(), dy, f.z() + d} : new int[]{f.x() + d, dy, f.z()};
    }

    /** The cracked wall in the Lord's arena: two layers deep, 3 wide, 4 tall, on side f.index(). */
    public static List<int[]> riftWall(Room r, Room.Feature f) {
        List<int[]> out = new ArrayList<>();
        for (int layer = 1; layer <= 2; layer++) {
            for (int l = -1; l <= 1; l++) {
                for (int dy = 0; dy <= 3; dy++) {
                    int x, z;
                    switch (f.index()) {
                        case 0 -> { x = f.x() + l; z = r.z0() - layer; }
                        case 2 -> { x = f.x() + l; z = r.z1() + layer; }
                        case 1 -> { x = r.x1() + layer; z = f.z() + l; }
                        default -> { x = r.x0() - layer; z = f.z() + l; }
                    }
                    out.add(new int[]{x, dy, z, layer});
                }
            }
        }
        return out;
    }
}
