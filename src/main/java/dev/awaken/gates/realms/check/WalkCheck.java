package dev.awaken.gates.realms.check;

import dev.awaken.gates.realms.Land;
import dev.awaken.gates.realms.kit.V;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Walks a land on foot from where you arrive, with the Lord's door open: a step up, a drop of up to three, room for your head, and swimming across water. Any place the land says
 * is there that the walk can't reach is a mistake in the land (SPEC 50.8). For lands under a roof, where a column's ground can't tell you this.
 */
public final class WalkCheck {
    private WalkCheck() {}

    private static final int Y0 = 0, Y1 = 260;

    private static final class Cells {
        final Map<Long, BitSet> body = new HashMap<>(), wet = new HashMap<>(), rungs = new HashMap<>();

        boolean water(int x, int y, int z) {
            if (y < Y0 || y >= Y1) return false;
            BitSet b = wet.get(key(x >> 4, z >> 4));
            return b != null && b.get(i(x, y, z));
        }

        boolean climb(int x, int y, int z) {
            if (y < Y0 || y >= Y1) return false;
            BitSet b = rungs.get(key(x >> 4, z >> 4));
            return b != null && b.get(i(x, y, z));
        }

        void rung(int x, int y, int z) {
            rungs.computeIfAbsent(key(x >> 4, z >> 4), k -> new BitSet()).set(i(x, y, z));
        }

        void flood(int x, int y, int z) {
            wet.computeIfAbsent(key(x >> 4, z >> 4), k -> new BitSet()).set(i(x, y, z));
        }

        boolean at(int x, int y, int z) {
            if (y < Y0 || y >= Y1) return false;
            BitSet b = body.get(key(x >> 4, z >> 4));
            return b != null && b.get(i(x, y, z));
        }

        void put(int x, int y, int z, boolean solid) {
            BitSet b = body.computeIfAbsent(key(x >> 4, z >> 4), k -> new BitSet());
            b.set(i(x, y, z), solid);
        }

        static long key(int cx, int cz) {
            return (long) cx << 32 | cz & 0xFFFFFFFFL;
        }

        static int i(int x, int y, int z) {
            return ((x & 15) << 4 | z & 15) * (Y1 - Y0) + y - Y0;
        }
    }

    private static long pack(int x, int y, int z) {
        return (long) (x + (1 << 20)) << 40 | (long) y << 21 | z + (1 << 20);
    }

    /** What can't be reached, or an empty list. */
    public static List<String> unreachable(Land land) {
        Cells cells = new Cells();
        int half = land.halfSize() + 16 >> 4;
        for (int cx = -half; cx < half; cx++) for (int cz = -half; cz < half; cz++) {
            V v = new V(cx * 16, cz * 16);
            land.paint(v);
            v.emit((x, y, z, s) -> {
                if (y >= Y0 && y < Y1 && V.holds(s)) cells.put(x, y, z, true);
                else if (y >= Y0 && y < Y1 && s.is(net.minecraft.world.level.block.Blocks.WATER)) cells.flood(x, y, z);
                else if (y >= Y0 && y < Y1 && s.is(net.minecraft.tags.BlockTags.CLIMBABLE)) cells.rung(x, y, z);
            });
        }
        for (Land.Edit e : land.change("heart_open")) if (e.y() >= Y0 && e.y() < Y1) {
            BlockState s = e.state();
            cells.put(e.x(), e.y(), e.z(), V.holds(s));
        }
        Land.Site m = land.mouth();
        ArrayDeque<int[]> q = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        q.add(new int[]{m.x(), m.y(), m.z()});
        seen.add(pack(m.x(), m.y(), m.z()));
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] p = q.poll();
            if (cells.climb(p[0], p[1], p[2]) && !cells.at(p[0], p[1] + 1, p[2]) && !cells.at(p[0], p[1] + 2, p[2]) && seen.add(pack(p[0], p[1] + 1, p[2])))
                q.add(new int[]{p[0], p[1] + 1, p[2]});
            for (int[] d : dirs) {
                int x = p[0] + d[0], z = p[2] + d[1];
                for (int y = p[1] + 1; y >= p[1] - 4; y--) {
                    boolean stand = (cells.at(x, y - 1, z) || cells.water(x, y - 1, z) || cells.climb(x, y, z)) && !cells.at(x, y, z) && !cells.at(x, y + 1, z);
                    if (stand) {
                        if (seen.add(pack(x, y, z))) q.add(new int[]{x, y, z});
                        break;
                    }
                    if (cells.at(x, y + 1, z) && y <= p[1]) break;
                }
            }
        }
        List<String> bad = new ArrayList<>();
        for (Land.Site s : land.sites()) {
            if (s.kind() == Land.SiteKind.PORTAL) continue;
            boolean found = false;
            for (int dx = -4; dx <= 4 && !found; dx++) for (int dz = -4; dz <= 4 && !found; dz++) for (int dy = -4; dy <= 4 && !found; dy++) found = seen.contains(pack(s.x() + dx, s.y() + dy, s.z() + dz));
            if (!found) bad.add(s.kind() + " " + s.index() + " at " + s.x() + " " + s.y() + " " + s.z());
        }
        return bad;
    }
}
