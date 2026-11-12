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
 * Walks a land on foot from where you arrive, with the Lord's door open: a step up, a drop of up to three (any drop in a land where no fall hurts), room for your head, and swimming across water. Any place the land says
 * is there that the walk can't reach is a mistake in the land (SPEC 50.8). For lands under a roof, where a column's ground can't tell you this.
 * <p>
 * A land's beats (SPEC §72.4) are crossings too: each {@link Land.Move} is taken from where its feet stand to where they
 * land, as a leap is, and MoveProof proves the move itself. {@link #needlessMoves} walks from each needed move's start with
 * no moves at all, inside the stretch it belongs to, and says where that walk still gets to the move's end.
 */
public final class WalkCheck {
    private WalkCheck() {}

    private static final int Y0 = 0, Y1 = 260;

    private static final class Cells {
        final Map<Long, BitSet> body = new HashMap<>(), wet = new HashMap<>(), rungs = new HashMap<>(), halves = new HashMap<>();

        /** A solid cell whose top is half way up it (a bottom slab): feet on it stand half a block below the cell over it. */
        boolean half(int x, int y, int z) {
            if (y < Y0 || y >= Y1) return false;
            BitSet b = halves.get(key(x >> 4, z >> 4));
            return b != null && b.get(i(x, y, z));
        }

        void halve(int x, int y, int z) {
            halves.computeIfAbsent(key(x >> 4, z >> 4), k -> new BitSet()).set(i(x, y, z));
        }

        /** The height the feet are at, standing in this cell. */
        double floor(int x, int y, int z) {
            return half(x, y - 1, z) ? y - 0.5 : y;
        }

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

    /** Everywhere the walk got to, and the sites it didn't. */
    public record Walk(Set<Long> seen, List<String> unreachable) {
        /** Whether the walk stood anywhere within {@code slack} of a point. */
        public boolean reaches(int x, int y, int z, int slack) {
            for (int dx = -slack; dx <= slack; dx++) for (int dz = -slack; dz <= slack; dz++) for (int dy = -slack; dy <= slack; dy++) if (seen.contains(pack(x + dx, y + dy, z + dz))) return true;
            return false;
        }

        /** How many columns within a radius of a point the walk stood in, at about its height: the room there is to fight in. */
        public int room(int x, int y, int z, int radius) {
            int n = 0;
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                for (int dy = -4; dy <= 4; dy++) if (seen.contains(pack(x + dx, y + dy, z + dz))) {
                    n++;
                    break;
                }
            }
            return n;
        }
    }

    /** What can't be reached, or an empty list. */
    public static List<String> unreachable(Land land) {
        return walk(land).unreachable();
    }

    /**
     * The walk itself. Besides stepping, it makes the one jump anyone can: a sprint over one to three missing cells, level
     * or down, with nothing in the way. And everything the land says is open at some time ({@code walk_open}) is open.
     */
    public static Walk walk(Land land) {
        return walk(land, true);
    }

    /**
     * A crossing a land says needs a move of the leap ({@link Land#leaps}) that can be made without it: the walk with no
     * leaps at all still stood where one lands. Empty when every one of them is needed.
     */
    public static List<String> needless(Land land) {
        if (land.leaps().isEmpty()) return List.of();
        Walk plain = walk(land, false);
        List<String> out = new ArrayList<>();
        for (Land.Leap l : land.leaps()) if (plain.reaches(l.x1(), l.y1(), l.z1(), 0)) out.add(l.move() + " to " + l.x1() + " " + l.y1() + " " + l.z1());
        return out;
    }

    private static Walk walk(Land land, boolean leaps) {
        Cells cells = paint(land);
        Land.Site m = land.mouth();
        Set<Long> seen = bfs(land, cells, new int[]{m.x(), m.y(), m.z()}, null, leaps, true, false);
        List<String> bad = new ArrayList<>();
        for (Land.Site s : land.sites()) {
            if (s.kind() == Land.SiteKind.PORTAL) continue;
            boolean found = false;
            for (int dx = -4; dx <= 4 && !found; dx++) for (int dz = -4; dz <= 4 && !found; dz++) for (int dy = -4; dy <= 4 && !found; dy++) found = seen.contains(pack(s.x() + dx, s.y() + dy, s.z() + dz));
            if (!found) bad.add(s.kind() + " " + s.index() + " at " + s.x() + " " + s.y() + " " + s.z());
        }
        return new Walk(seen, bad);
    }

    /** Every cell of the land, painted once: what is solid, what is water, what is a rung, what is a half block. */
    private static Cells paint(Land land) {
        Cells cells = new Cells();
        int half = land.halfSize() + 16 >> 4;
        for (int cx = -half; cx < half; cx++) for (int cz = -half; cz < half; cz++) {
            V v = new V(cx * 16, cz * 16);
            land.paint(v);
            v.emit((x, y, z, s) -> {
                if (y >= Y0 && y < Y1 && V.holds(s)) {
                    cells.put(x, y, z, true);
                    if (s.hasProperty(net.minecraft.world.level.block.SlabBlock.TYPE) && s.getValue(net.minecraft.world.level.block.SlabBlock.TYPE) == net.minecraft.world.level.block.state.properties.SlabType.BOTTOM) cells.halve(x, y, z);
                } else if (y >= Y0 && y < Y1 && s.is(net.minecraft.world.level.block.Blocks.WATER)) cells.flood(x, y, z);
                else if (y >= Y0 && y < Y1 && s.is(net.minecraft.tags.BlockTags.CLIMBABLE)) cells.rung(x, y, z);
            });
        }
        for (String moment : new String[]{"heart_open", "walk_open"}) for (Land.Edit e : land.change(moment)) if (e.y() >= Y0 && e.y() < Y1) {
            BlockState s = e.state();
            cells.put(e.x(), e.y(), e.z(), V.holds(s));
            if (s.is(net.minecraft.tags.BlockTags.CLIMBABLE)) cells.rung(e.x(), e.y(), e.z());
        }
        return cells;
    }

    /** Where feet stand in a column at about a height (a move's end): the cell with floor under it and room over it. */
    private static int[] stand(Cells cells, int x, int y, int z) {
        for (int yy : new int[]{y, y + 1, y - 1, y + 2}) {
            if (!cells.at(x, yy, z) && !cells.at(x, yy + 1, z) && (cells.at(x, yy - 1, z) || cells.water(x, yy - 1, z))) return new int[]{x, yy, z};
        }
        return null;
    }

    /**
     * The walk from a start. {@code box} (x0, y0, z0, x1, y1, z1) keeps it inside a stretch, or null; {@code moves} and
     * {@code leaps}: whether the land's moves and leaps are crossings it takes; {@code strict}: a step up is never more
     * than a block from where the feet really are (a block and a half from a half step is a mantle, not a step).
     */
    private static Set<Long> bfs(Land land, Cells cells, int[] start, int[] box, boolean leaps, boolean moves, boolean strict) {
        Map<Long, List<Land.Move>> from = new HashMap<>();
        if (moves) for (Land.Move mv : land.moves()) from.computeIfAbsent(Cells.key(mv.x0(), mv.z0()), k -> new ArrayList<>()).add(mv);
        ArrayDeque<int[]> q = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        q.add(start);
        seen.add(pack(start[0], start[1], start[2]));
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] p = q.poll();
            if (box != null && (p[0] < box[0] || p[0] > box[3] || p[1] < box[1] || p[1] > box[4] || p[2] < box[2] || p[2] > box[5])) continue;
            // a crossing that needs a move of the leap, taken by a walker who has it, from where its near lip is stood on
            if (leaps) for (Land.Leap l : land.leaps()) {
                if (p[1] == l.y0() && Math.abs(p[0] - l.x0()) <= 1 && Math.abs(p[2] - l.z0()) <= 1 && seen.add(pack(l.x1(), l.y1(), l.z1()))) q.add(new int[]{l.x1(), l.y1(), l.z1()});
            }
            // a beat's move, from within a cell of where its feet start (a half step's feet are in the cell under)
            if (moves) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                List<Land.Move> here = from.get(Cells.key(p[0] + dx, p[2] + dz));
                if (here != null) for (Land.Move mv : here) {
                    if (mv.x0() != p[0] + dx || mv.z0() != p[2] + dz || Math.abs(p[1] - mv.y0()) > 1) continue;
                    int[] e = stand(cells, mv.x1(), mv.y1(), mv.z1());
                    if (e != null && seen.add(pack(e[0], e[1], e[2]))) q.add(e);
                }
            }
            if (cells.climb(p[0], p[1], p[2]) && !cells.at(p[0], p[1] + 1, p[2]) && !cells.at(p[0], p[1] + 2, p[2]) && seen.add(pack(p[0], p[1] + 1, p[2])))
                q.add(new int[]{p[0], p[1] + 1, p[2]});
            for (int[] d : dirs) {
                int x = p[0] + d[0], z = p[2] + d[1];
                boolean stepped = false;
                // where no fall hurts (a trial's halls), any drop is a way down
                for (int y = p[1] + 1, low = land.soft() ? Math.max(Y0 + 1, p[1] - 64) : p[1] - 4; y >= low; y--) {
                    boolean stand = (cells.at(x, y - 1, z) || cells.water(x, y - 1, z) || cells.climb(x, y, z)) && !cells.at(x, y, z) && !cells.at(x, y + 1, z);
                    if (stand) {
                        if (strict && !cells.climb(x, y, z) && cells.floor(x, y, z) - cells.floor(p[0], p[1], p[2]) > 1.01) break;
                        if (seen.add(pack(x, y, z))) q.add(new int[]{x, y, z});
                        // a long drop is a way down, not the way on: the jump over it is still tried
                        stepped = y >= p[1] - 4;
                        break;
                    }
                    if (cells.at(x, y + 1, z) && y <= p[1]) break;
                }
                // nowhere to step that way, from solid ground: a sprint jump over the gap, landing level or up to three down
                if (stepped || !cells.at(p[0], p[1] - 1, p[2])) continue;
                for (int n = 2; n <= 4; n++) {
                    int jx = p[0] + d[0] * n, jz = p[2] + d[1] * n;
                    boolean clear = true;
                    for (int k = 1; k <= n && clear; k++) for (int h = 0; h <= 2 && clear; h++) clear = !cells.at(p[0] + d[0] * k, p[1] + h, p[2] + d[1] * k);
                    if (!clear) break;
                    boolean landed = false;
                    for (int y = p[1]; y >= p[1] - 3 && !landed; y--) {
                        if (cells.at(jx, y, jz)) break;
                        if (cells.at(jx, y - 1, jz) && !cells.at(jx, y + 1, jz)) {
                            if (seen.add(pack(jx, y, jz))) q.add(new int[]{jx, y, jz});
                            landed = true;
                        }
                    }
                    if (landed) break;
                }
            }
        }
        return seen;
    }

    /**
     * Where the walk from the Mouth stops on the lines of beats (SPEC §72.4), said so a test's log shows why: for the first
     * line it never gets onto, and every line it gets onto but not off, the move whose end nothing stands on, or the cell
     * it stood in nearest the line's end (or start) with the columns round it (top down from two over the feet to four
     * under: # solid, ~ water, H a rung, . open). Empty when every line is crossed.
     */
    public static List<String> stuck(Land land, Walk walk) {
        List<String> out = new ArrayList<>();
        Cells cells = null;
        boolean before = false;
        for (Land.Line l : land.lines()) {
            if (l.fallback()) continue;
            boolean on = walk.reaches(l.x0(), l.y0(), l.z0(), 2), off = walk.reaches(l.x1(), l.y1(), l.z1(), 2);
            if (off || !on && before) continue;
            if (cells == null) cells = paint(land);
            StringBuilder sb = new StringBuilder(l.leg()).append(' ').append(l.beats()).append(" from ").append(l.x0()).append(' ').append(l.y0()).append(' ').append(l.z0())
                    .append(" to ").append(l.x1()).append(' ').append(l.y1()).append(' ').append(l.z1()).append(": ");
            if (!on) {
                before = true;
                sb.append("never got onto it; nearest ").append(nearest(walk, cells, land, l.leg(), l.x0(), l.y0(), l.z0()));
                out.add(sb.toString());
                continue;
            }
            for (Land.Move mv : land.moves()) {
                if (!mv.leg().equals(l.leg())) continue;
                boolean started = false;
                for (int dx = -1; dx <= 1 && !started; dx++) for (int dz = -1; dz <= 1 && !started; dz++) for (int dy = -1; dy <= 1 && !started; dy++)
                    started = walk.seen().contains(pack(mv.x0() + dx, mv.y0() + dy, mv.z0() + dz));
                if (!started) {
                    sb.append("never at the start of ").append(MoveProof.describe(mv)).append(" (").append(columns(cells, mv.x0(), mv.y0(), mv.z0())).append("); ");
                    break;
                }
                if (stand(cells, mv.x1(), mv.y1(), mv.z1()) == null) {
                    sb.append("nothing stands at the end of ").append(MoveProof.describe(mv)).append(" (").append(columns(cells, mv.x1(), mv.y1(), mv.z1())).append("); ");
                    break;
                }
            }
            sb.append("nearest its end ").append(nearest(walk, cells, land, l.leg(), l.x1(), l.y1(), l.z1()));
            out.add(sb.toString());
        }
        return out;
    }

    /** The stood cell in a leg's Course (or within twelve) nearest a point, and the columns round it. */
    private static String nearest(Walk walk, Cells cells, Land land, String leg, int x, int y, int z) {
        int[] box = {x - 12, y - 12, z - 12, x + 12, y + 12, z + 12};
        for (Land.Course c : land.courses()) if (c.leg().equals(leg)) box = new int[]{Math.min(c.x0(), x - 4), Math.min(c.y0(), y - 4), Math.min(c.z0(), z - 4), Math.max(c.x1(), x + 4), Math.max(c.y1(), y + 4), Math.max(c.z1(), z + 4)};
        int[] best = null;
        long bestD = Long.MAX_VALUE;
        for (int xx = box[0]; xx <= box[3]; xx++) for (int zz = box[2]; zz <= box[5]; zz++) for (int yy = box[1]; yy <= box[4]; yy++) {
            if (!walk.seen().contains(pack(xx, yy, zz))) continue;
            long d = (long) (xx - x) * (xx - x) + (long) (yy - y) * (yy - y) * 4 + (long) (zz - z) * (zz - z);
            if (d < bestD) {
                bestD = d;
                best = new int[]{xx, yy, zz};
            }
        }
        if (best == null) return "nothing stood in its box";
        return best[0] + " " + best[1] + " " + best[2] + " [" + columns(cells, best[0], best[1], best[2]) + "]";
    }

    /** A cell's own column and the four round it, top down from two over the feet to four under. */
    private static String columns(Cells cells, int x, int y, int z) {
        StringBuilder sb = new StringBuilder();
        int[][] at = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        String[] name = {"here", "+x", "-x", "+z", "-z"};
        for (int i = 0; i < at.length; i++) {
            sb.append(i == 0 ? "" : " ").append(name[i]).append(' ');
            for (int yy = y + 2; yy >= y - 4; yy--) {
                int xx = x + at[i][0], zz = z + at[i][1];
                sb.append(cells.at(xx, yy, zz) ? (cells.half(xx, yy, zz) ? '_' : '#') : cells.water(xx, yy, zz) ? '~' : cells.climb(xx, yy, zz) ? 'H' : '.');
                if (yy == y) sb.append('|');
            }
        }
        return sb.toString();
    }

    /**
     * The needed moves of a land's beats that aren't (SPEC §72.4): from each one's start, a walk with no moves and no leaps,
     * kept inside the stretch it belongs to (its Course), and stepping no more than a block up from where the feet really
     * are, still gets to within a cell of its end. Empty when every move a beat asks for is asked for.
     */
    public static List<String> needlessMoves(Land land) {
        List<Land.Move> asked = new ArrayList<>();
        for (Land.Move mv : land.moves()) if (mv.needed()) asked.add(mv);
        if (asked.isEmpty()) return List.of();
        Cells cells = paint(land);
        List<String> out = new ArrayList<>();
        for (Land.Move mv : asked) {
            int[] st = stand(cells, mv.x0(), mv.y0(), mv.z0());
            if (st == null) {
                out.add(MoveProof.describe(mv) + ": nowhere to stand at its start");
                continue;
            }
            int[] box = null;
            for (Land.Course c : land.courses()) if (c.leg().equals(mv.leg())) box = new int[]{c.x0(), c.y0(), c.z0(), c.x1(), c.y1(), c.z1()};
            Set<Long> seen = bfs(land, cells, st, box, false, false, true);
            boolean got = false;
            for (int dx = -1; dx <= 1 && !got; dx++) for (int dz = -1; dz <= 1 && !got; dz++) for (int dy = 0; dy <= 1 && !got; dy++) got = seen.contains(pack(mv.x1() + dx, mv.y1() + dy, mv.z1() + dz));
            if (got) out.add(MoveProof.describe(mv) + ": a walk gets there without it");
        }
        return out;
    }
}
