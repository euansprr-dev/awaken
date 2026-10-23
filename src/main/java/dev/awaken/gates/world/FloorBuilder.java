package dev.awaken.gates.world;

import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.content.Layout;
import dev.awaken.gates.content.Modifier;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.content.Twist;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;

/**
 * The layout grammar: turns a {@link GatePlan} into rooms. Deterministic: every random choice comes from a
 * {@link SplittableRandom} seeded from the plan seed and the floor, created per build (no shared state).
 */
final class FloorBuilder {
    private FloorBuilder() {}

    static GateLayout build(GatePlan plan) {
        List<FloorLayout> main = new ArrayList<>();
        for (int f = 1; f <= plan.floors(); f++) {
            Builder b = new Builder(plan, plan.theme(), 0, f, Hash.of(plan.seed(), 0, f));
            if (f == plan.floors()) b.bossFloor(false);
            else b.layout(plan.layout());
            main.add(b.finish());
        }
        List<FloorLayout> annex = new ArrayList<>();
        if (plan.hasAnnex()) {
            Theme at = plan.annexTheme();
            Builder a1 = new Builder(plan, at, 1, 1, Hash.of(plan.seed(), 1, 1));
            a1.labyrinth(4, 4);
            annex.add(a1.finish());
            Builder a2 = new Builder(plan, at, 1, 2, Hash.of(plan.seed(), 1, 2));
            a2.bossFloor(plan.twist() == Twist.GATE_WITHIN);
            annex.add(a2.finish());
        }
        return new GateLayout(plan, main, annex);
    }

    static final class Builder {
        final GatePlan plan;
        final Theme theme;
        final int area, floor;
        final long seed;
        final SplittableRandom rnd;
        final int rank;
        int w, h;
        int[] occ;
        final List<Room> rooms = new ArrayList<>();
        int start = -1, end = -1;
        boolean bossFloor;
        boolean sealEnd;

        Builder(GatePlan plan, Theme theme, int area, int floor, long seed) {
            this.plan = plan;
            this.theme = theme;
            this.area = area;
            this.floor = floor;
            this.seed = seed;
            this.rnd = new SplittableRandom(seed);
            this.rank = plan.rank().ordinal();
        }

        // ---- grid helpers -----------------------------------------------------------------------------------------

        void grid(int w, int h) {
            this.w = w;
            this.h = h;
            this.occ = new int[w * h];
            Arrays.fill(occ, -1);
            rooms.clear();
            start = end = -1;
        }

        boolean free(int gx, int gz, int gw, int gh) {
            if (gx < 0 || gz < 0 || gx + gw > w || gz + gh > h) return false;
            for (int x = gx; x < gx + gw; x++) for (int z = gz; z < gz + gh; z++) if (occ[x + z * w] >= 0) return false;
            return true;
        }

        int add(Room.Type type, Room.Shape shape, int gx, int gz, int gw, int gh, int dy) {
            int id = rooms.size();
            Room r = new Room(id, type, shape, gx, gz, gw, gh, dy, 0, Hash.of(seed, id, 77));
            rooms.add(r);
            for (int x = gx; x < gx + gw; x++) for (int z = gz; z < gz + gh; z++) occ[x + z * w] = id;
            return id;
        }

        int at(int gx, int gz) {
            if (gx < 0 || gz < 0 || gx >= w || gz >= h) return -1;
            return occ[gx + gz * w];
        }

        boolean connected(int a, int b) {
            return rooms.get(a).doorTo(b) != null;
        }

        /** Adds a door pair between two adjacent rooms (picks the middle of their shared edge). */
        boolean connect(int a, int b) {
            if (a == b || a < 0 || b < 0 || connected(a, b)) return false;
            Room ra = rooms.get(a), rb = rooms.get(b);
            List<int[]> pairs = new ArrayList<>();
            for (int x = ra.gx; x < ra.gx + ra.gw; x++) {
                for (int z = ra.gz; z < ra.gz + ra.gh; z++) {
                    if (at(x, z - 1) == b) pairs.add(new int[]{x, z, 0});
                    if (at(x + 1, z) == b) pairs.add(new int[]{x, z, 1});
                    if (at(x, z + 1) == b) pairs.add(new int[]{x, z, 2});
                    if (at(x - 1, z) == b) pairs.add(new int[]{x, z, 3});
                }
            }
            if (pairs.isEmpty()) return false;
            int[] p = pairs.get(pairs.size() / 2);
            int side = p[2];
            int bx = p[0] + (side == 1 ? 1 : side == 3 ? -1 : 0);
            int bz = p[1] + (side == 2 ? 1 : side == 0 ? -1 : 0);
            int y = Math.max(ra.dy, rb.dy);
            if (ra.shape == Room.Shape.SHAFT) y = rb.dy;
            if (rb.shape == Room.Shape.SHAFT) y = ra.dy;
            ra.doors.add(new Room.Door(p[0], p[1], side, y, b));
            rb.doors.add(new Room.Door(bx, bz, (side + 2) % 4, y, a));
            return true;
        }

        List<Integer> neighbours(int id) {
            Room r = rooms.get(id);
            List<Integer> out = new ArrayList<>();
            for (int x = r.gx; x < r.gx + r.gw; x++) {
                for (int z = r.gz; z < r.gz + r.gh; z++) {
                    int[][] n = {{x, z - 1}, {x + 1, z}, {x, z + 1}, {x - 1, z}};
                    for (int[] q : n) {
                        int o = at(q[0], q[1]);
                        if (o >= 0 && o != id && !out.contains(o)) out.add(o);
                    }
                }
            }
            return out;
        }

        int[] distances(int from) {
            int[] d = new int[rooms.size()];
            Arrays.fill(d, -1);
            ArrayDeque<Integer> q = new ArrayDeque<>();
            q.add(from);
            d[from] = 0;
            while (!q.isEmpty()) {
                int c = q.poll();
                for (Room.Door door : rooms.get(c).doors) {
                    if (d[door.toRoom] < 0) {
                        d[door.toRoom] = d[c] + 1;
                        q.add(door.toRoom);
                    }
                }
            }
            return d;
        }

        int farthest(int from) {
            int[] d = distances(from);
            int best = from;
            for (int i = 0; i < d.length; i++) if (d[i] > d[best]) best = i;
            return best;
        }

        /** Randomised DFS spanning tree over room adjacency, then a few loops. */
        void maze(int from, double loops) {
            boolean[] seen = new boolean[rooms.size()];
            ArrayDeque<Integer> stack = new ArrayDeque<>();
            stack.push(from);
            seen[from] = true;
            while (!stack.isEmpty()) {
                int c = stack.peek();
                List<Integer> opts = new ArrayList<>();
                for (int n : neighbours(c)) if (!seen[n]) opts.add(n);
                if (opts.isEmpty()) {
                    stack.pop();
                    continue;
                }
                int n = opts.get(rnd.nextInt(opts.size()));
                connect(c, n);
                seen[n] = true;
                stack.push(n);
            }
            for (int i = 0; i < rooms.size(); i++) {
                for (int n : neighbours(i)) {
                    if (n > i && !connected(i, n) && rnd.nextDouble() < loops) connect(i, n);
                }
            }
        }

        int degree(int id) {
            return rooms.get(id).doors.size();
        }

        // ---- layouts ----------------------------------------------------------------------------------------------

        void layout(Layout l) {
            switch (l) {
                case LABYRINTH -> labyrinth(rank >= 4 ? 6 : 5, rank >= 4 ? 6 : 5);
                case SPIRAL_TOWER -> spiral(false);
                case SHAFT -> spiral(true);
                case HUB_AND_SPOKES -> hub();
                case ARENA_CHAIN -> arenaChain();
                case STEALTH_HALLS -> stealthHalls();
                case PUZZLE_VAULT -> puzzleVault();
                case OPEN_CAVERN -> cavern();
            }
        }

        void labyrinth(int gw, int gh) {
            grid(gw, gh);
            // one or two big halls first, then fill with single rooms
            int halls = gw >= 5 ? 1 + (rnd.nextDouble() < 0.4 ? 1 : 0) : 0;
            for (int i = 0; i < halls; i++) {
                for (int t = 0; t < 12; t++) {
                    int x = 1 + rnd.nextInt(gw - 2), z = 1 + rnd.nextInt(gh - 2);
                    if (x + 2 <= gw && z + 2 <= gh && free(x, z, 2, 2)) {
                        add(Room.Type.NORMAL, null, x, z, 2, 2, 0);
                        break;
                    }
                }
            }
            for (int z = 0; z < gh; z++) for (int x = 0; x < gw; x++) if (at(x, z) < 0) add(Room.Type.NORMAL, null, x, z, 1, 1, 0);
            int corner = rnd.nextInt(4);
            int sx = (corner & 1) == 0 ? 0 : gw - 1, sz = (corner & 2) == 0 ? 0 : gh - 1;
            start = at(sx, sz);
            Room s = rooms.get(start);
            if (s.area() > 1) start = at(sx == 0 ? 0 : gw - 1, sz == 0 ? gh - 1 : 0);
            rooms.get(start).type = Room.Type.START;
            maze(start, 0.12);
            end = farthest(start);
            if (rooms.get(end).area() > 1) {
                // shrines are single rooms: pick the farthest single room
                int[] d = distances(start);
                int best = -1;
                for (int i = 0; i < d.length; i++) if (rooms.get(i).area() == 1 && i != start && (best < 0 || d[i] > d[best])) best = i;
                end = best;
            }
            rooms.get(end).type = Room.Type.SHRINE;
            deadEnds();
        }

        void deadEnds() {
            for (Room r : rooms) {
                if (r.type != Room.Type.NORMAL) continue;
                if (r.doors.size() == 1) r.type = rnd.nextDouble() < 0.6 ? Room.Type.CACHE : Room.Type.ELITE;
                else if (rnd.nextDouble() < 0.12) r.type = Room.Type.ELITE;
            }
        }

        /** Spiral Tower climbs the ring to the centre; Shaft descends the ring around a great drop. */
        void spiral(boolean shaft) {
            grid(4, 4);
            int[][] ring = {{0, 3}, {0, 2}, {0, 1}, {0, 0}, {1, 0}, {2, 0}, {3, 0}, {3, 1}, {3, 2}, {3, 3}, {2, 3}, {1, 3}};
            boolean mirror = rnd.nextBoolean();
            int[] ids = new int[ring.length];
            for (int i = 0; i < ring.length; i++) {
                int x = mirror ? 3 - ring[i][0] : ring[i][0];
                int z = ring[i][1];
                int dy = shaft ? Math.max(0, 22 - 2 * i) : Math.min(20, 2 * i);
                ids[i] = add(Room.Type.NORMAL, null, x, z, 1, 1, dy);
            }
            int centre = add(shaft ? Room.Type.SHAFT : Room.Type.SHRINE, shaft ? Room.Shape.SHAFT : Room.Shape.ROUND, 1, 1, 2, 2, shaft ? 0 : 20);
            for (int i = 0; i + 1 < ids.length; i++) connect(ids[i], ids[i + 1]);
            start = ids[0];
            rooms.get(start).type = Room.Type.START;
            if (shaft) {
                end = ids[ids.length - 1];
                rooms.get(end).type = Room.Type.SHRINE;
                for (int i = 1; i < ids.length; i++) {
                    Room r = rooms.get(ids[i]);
                    if (neighbours(ids[i]).contains(centre) && (i % 2 == 0 || i == ids.length - 1)) connect(ids[i], centre);
                }
            } else {
                end = centre;
                connect(ids[ids.length - 1], centre);
            }
            for (int i = 2; i < ids.length - 1; i += 3) rooms.get(ids[i]).type = rnd.nextBoolean() ? Room.Type.ELITE : Room.Type.CACHE;
        }

        void hub() {
            grid(7, 7);
            int hubId = add(Room.Type.HUB, Room.Shape.RING, 2, 2, 3, 3, 0);
            int nIn = add(Room.Type.NORMAL, null, 3, 1, 1, 1, 0), nOut = add(Room.Type.SHRINE, null, 3, 0, 1, 1, 0);
            int sIn = add(Room.Type.NORMAL, null, 3, 5, 1, 1, 0), sOut = add(Room.Type.START, null, 3, 6, 1, 1, 0);
            int eIn = add(Room.Type.NORMAL, null, 5, 3, 1, 1, 0), eOut = add(Room.Type.KEY, null, 6, 3, 1, 1, 0);
            int wIn = add(Room.Type.NORMAL, null, 1, 3, 1, 1, 0), wOut = add(Room.Type.KEY, null, 0, 3, 1, 1, 0);
            connect(sOut, sIn); connect(sIn, hubId);
            connect(hubId, nIn); connect(nIn, nOut);
            connect(hubId, eIn); connect(eIn, eOut);
            connect(hubId, wIn); connect(wIn, wOut);
            // the third key hangs off the east or west spoke; a cache off the other
            boolean east = rnd.nextBoolean();
            int key3 = add(Room.Type.KEY, null, east ? 5 : 1, 4, 1, 1, 0);
            connect(east ? eIn : wIn, key3);
            int cache = add(Room.Type.CACHE, null, east ? 1 : 5, 2, 1, 1, 0);
            connect(east ? wIn : eIn, cache);
            if (rank >= 3) {
                int extra = add(Room.Type.ELITE, null, 2, 1, 1, 1, 0);
                connect(nIn, extra);
            }
            start = sOut;
            end = nOut;
            sealEnd = true;
        }

        void arenaChain() {
            int arenas = rank >= 4 ? 4 : rank >= 2 ? 3 : 2;
            List<int[]> pieces = new ArrayList<>();
            pieces.add(new int[]{1, 1, Room.Type.START.ordinal()});
            for (int i = 0; i < arenas; i++) {
                pieces.add(new int[]{1, 1, (i == 1 ? Room.Type.CACHE : Room.Type.NORMAL).ordinal()});
                pieces.add(new int[]{2, 2, Room.Type.ARENA.ordinal()});
            }
            pieces.add(new int[]{1, 1, Room.Type.NORMAL.ordinal()});
            pieces.add(new int[]{1, 1, Room.Type.SHRINE.ordinal()});
            if (!chain(9, 9, pieces)) labyrinth(5, 5);
        }

        void stealthHalls() {
            int n = rank >= 3 ? 7 : 5;
            grid(n, n);
            int j = (n + 1) / 2;
            int[][] junction = new int[j][j];
            for (int a = 0; a < j; a++) for (int b = 0; b < j; b++) junction[a][b] = add(Room.Type.NORMAL, Room.Shape.OCTAGON, a * 2, b * 2, 1, 1, 0);
            // spanning tree over junctions; each edge becomes a hall room in the odd slot between them
            boolean[][] seen = new boolean[j][j];
            ArrayDeque<int[]> stack = new ArrayDeque<>();
            int sa = 0, sb = j - 1;
            stack.push(new int[]{sa, sb});
            seen[sa][sb] = true;
            List<int[]> extra = new ArrayList<>();
            while (!stack.isEmpty()) {
                int[] c = stack.peek();
                List<int[]> opts = new ArrayList<>();
                int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
                for (int[] d : dirs) {
                    int na = c[0] + d[0], nb = c[1] + d[1];
                    if (na >= 0 && nb >= 0 && na < j && nb < j && !seen[na][nb]) opts.add(new int[]{na, nb});
                }
                if (opts.isEmpty()) {
                    stack.pop();
                    continue;
                }
                int[] nx = opts.get(rnd.nextInt(opts.size()));
                hall(junction, c, nx);
                seen[nx[0]][nx[1]] = true;
                stack.push(nx);
            }
            for (int a = 0; a < j; a++) for (int b = 0; b < j; b++) {
                if (a + 1 < j && at(a * 2 + 1, b * 2) < 0 && rnd.nextDouble() < 0.18) extra.add(new int[]{a, b, a + 1, b});
                if (b + 1 < j && at(a * 2, b * 2 + 1) < 0 && rnd.nextDouble() < 0.18) extra.add(new int[]{a, b, a, b + 1});
            }
            for (int[] e : extra) hall(junction, new int[]{e[0], e[1]}, new int[]{e[2], e[3]});
            start = junction[sa][sb];
            rooms.get(start).type = Room.Type.START;
            rooms.get(start).shape = Room.Shape.RECT;
            end = farthest(start);
            rooms.get(end).type = Room.Type.SHRINE;
            rooms.get(end).shape = null;
            for (Room r : rooms) {
                r.dark = r.type != Room.Type.SHRINE && r.type != Room.Type.START;
                if (r.type == Room.Type.NORMAL && r.shape == Room.Shape.OCTAGON && r.doors.size() == 1) r.type = Room.Type.CACHE;
            }
        }

        private void hall(int[][] junction, int[] a, int[] b) {
            int hx = a[0] + b[0], hz = a[1] + b[1]; // midpoint in slot coords (sum of junction indices = odd slot)
            int id = at(hx, hz);
            if (id < 0) id = add(Room.Type.HALL, Room.Shape.HALL, hx, hz, 1, 1, 0);
            connect(junction[a[0]][a[1]], id);
            connect(id, junction[b[0]][b[1]]);
        }

        void puzzleVault() {
            labyrinth(5, 5);
            int[] d = distances(start);
            List<Integer> candidates = new ArrayList<>();
            for (Room r : rooms) if (r.type == Room.Type.NORMAL && r.area() == 1 && d[r.id] >= 2) candidates.add(r.id);
            int puzzles = rank >= 3 ? 3 : 2;
            for (int i = 0; i < puzzles && !candidates.isEmpty(); i++) {
                int pick = candidates.remove(rnd.nextInt(candidates.size()));
                rooms.get(pick).type = Room.Type.PUZZLE;
                rooms.get(pick).shape = Room.Shape.RECT;
            }
            sealEnd = true;
        }

        void cavern() {
            grid(6, 6);
            int sz = 1 + rnd.nextInt(4);
            int ez = 1 + rnd.nextInt(4);
            int cav = add(Room.Type.CAVERN, Room.Shape.CAVE, 1, 0, 4, 6, 0);
            start = add(Room.Type.START, null, 0, sz, 1, 1, 0);
            end = add(Room.Type.SHRINE, null, 5, ez, 1, 1, 0);
            connect(start, cav);
            connect(cav, end);
            int c1 = add(Room.Type.CACHE, null, 0, sz >= 3 ? 0 : 5, 1, 1, 0);
            connect(c1, cav);
            int e1 = add(Room.Type.ELITE, null, 5, ez >= 3 ? 0 : 5, 1, 1, 0);
            connect(e1, cav);
        }

        /** Boss floor: a short approach and the arena. Annex boss floors get a smaller arena (the inner gate's keeper). */
        void bossFloor(boolean smallArena) {
            bossFloor = true;
            List<int[]> pieces = new ArrayList<>();
            pieces.add(new int[]{1, 1, Room.Type.START.ordinal()});
            pieces.add(new int[]{1, 1, Room.Type.HALL.ordinal()});
            pieces.add(new int[]{1, 1, Room.Type.ANTE.ordinal()});
            int s = smallArena ? 2 : 3;
            pieces.add(new int[]{s, s, Room.Type.BOSS.ordinal()});
            if (!chain(8, 8, pieces)) {
                grid(6, 6);
                int a = add(Room.Type.START, null, 0, 0, 1, 1, 0);
                int b = add(Room.Type.HALL, Room.Shape.HALL, 1, 0, 1, 1, 0);
                int c = add(Room.Type.ANTE, null, 2, 0, 1, 1, 0);
                int d = add(Room.Type.BOSS, null, 3, 0, s, s, 0);
                connect(a, b); connect(b, c); connect(c, d);
                start = a;
                end = d;
            }
        }

        /** Places pieces one after another, each sharing an edge with the previous. Returns false if it can't fit. */
        boolean chain(int gw, int gh, List<int[]> pieces) {
            for (int attempt = 0; attempt < 48; attempt++) {
                grid(gw, gh);
                int[] first = pieces.get(0);
                int x = rnd.nextInt(gw - first[0] + 1), z = rnd.nextInt(gh - first[1] + 1);
                int prev = add(Room.Type.values()[first[2]], null, x, z, first[0], first[1], 0);
                int dir = rnd.nextInt(4);
                boolean ok = true;
                for (int i = 1; i < pieces.size() && ok; i++) {
                    int[] p = pieces.get(i);
                    Room pr = rooms.get(prev);
                    int[] order = {dir, (dir + 1) % 4, (dir + 3) % 4};
                    if (rnd.nextBoolean()) { int t = order[1]; order[1] = order[2]; order[2] = t; }
                    if (rnd.nextDouble() < 0.35) { int t = order[0]; order[0] = order[1]; order[1] = t; }
                    boolean placed = false;
                    for (int d : order) {
                        int nx, nz;
                        switch (d) {
                            case 0 -> { nx = pr.gx + (pr.gw - p[0]) / 2; nz = pr.gz - p[1]; }
                            case 1 -> { nx = pr.gx + pr.gw; nz = pr.gz + (pr.gh - p[1]) / 2; }
                            case 2 -> { nx = pr.gx + (pr.gw - p[0]) / 2; nz = pr.gz + pr.gh; }
                            default -> { nx = pr.gx - p[0]; nz = pr.gz + (pr.gh - p[1]) / 2; }
                        }
                        if (free(nx, nz, p[0], p[1])) {
                            int id = add(Room.Type.values()[p[2]], null, nx, nz, p[0], p[1], 0);
                            connect(prev, id);
                            prev = id;
                            dir = d;
                            placed = true;
                            break;
                        }
                    }
                    ok = placed;
                }
                if (ok) {
                    start = 0;
                    end = rooms.size() - 1;
                    return true;
                }
            }
            return false;
        }

        // ---- finishing --------------------------------------------------------------------------------------------

        FloorLayout finish() {
            if (start < 0 || end < 0 || rooms.isEmpty()) labyrinth(4, 4);
            ensureConnected();
            boolean friendly = plan.friendly();
            boolean hollow = plan.twist() == Twist.HOLLOW_GATE;
            if (area == 0 && !bossFloor && friendly) friendlyRooms();
            if (area == 0 && plan.twist() == Twist.GATE_WITHIN && floor == plan.innerGateFloor() && !bossFloor) innerGateRoom();
            for (Room r : rooms) {
                if (r.shape == null) r.shape = pickShape(r);
                if (theme == Theme.SKY_ARCHIPELAGO && skyIsland(r)) r.shape = Room.Shape.ISLANDS;
                r.height = heightFor(r);
            }
            // stairs need head room above the higher doorway
            for (Room r : rooms) {
                if (r.shape == Room.Shape.SHAFT) continue;
                for (Room.Door d : r.doors) {
                    int rise = d.y - r.dy;
                    if (rise > 0) r.height = Math.max(r.height, rise + RoomGeometry.DOOR_HEIGHT + 2);
                }
                r.height = Math.min(r.height, 27 - r.dy);
            }
            for (Room r : rooms) {
                if (!(friendly || (hollow && r.type != Room.Type.BOSS))) spawns(r);
                features(r);
            }
            if (friendly && area == 0 && !bossFloor) lanterns();
            for (Room r : rooms) spots(r);
            if (sealEnd || (friendly && !bossFloor)) {
                Room e = rooms.get(end);
                for (Room.Door d : e.doors) {
                    d.sealed = true;
                    d.ownsBars = true;
                }
            }
            int baseY = Cells.floorY(floor);
            int ox = area == 0 ? Cells.GRID_ORIGIN : Cells.ANNEX_SLOT_X + Cells.GRID_ORIGIN;
            return new FloorLayout(area, floor, baseY, ox, Cells.GRID_ORIGIN, w, h, theme, bossFloor, rooms, start, end);
        }

        private boolean skyIsland(Room r) {
            return switch (r.type) {
                case NORMAL, ELITE, CACHE, HALL, KEY, CAVERN, ARENA, HUB -> r.shape != Room.Shape.SHAFT;
                default -> false;
            };
        }

        void ensureConnected() {
            int[] d = distances(start);
            for (int i = 0; i < rooms.size(); i++) {
                if (d[i] >= 0) continue;
                for (int n : neighbours(i)) {
                    if (d[n] >= 0) {
                        connect(n, i);
                        d = distances(start);
                        break;
                    }
                }
            }
        }

        /** Friendly floors: a spirit near the end who wants three lost lanterns, one puzzle, a market on floor 1. */
        void friendlyRooms() {
            List<Integer> path = new FloorLayoutView(this).path();
            int spiritRoom = path.size() >= 2 ? path.get(path.size() - 2) : start;
            if (spiritRoom != start) rooms.get(spiritRoom).type = Room.Type.SPIRIT;
            List<Integer> normals = new ArrayList<>();
            for (Room r : rooms) if (r.type == Room.Type.NORMAL || r.type == Room.Type.ELITE || r.type == Room.Type.CACHE || r.type == Room.Type.KEY) normals.add(r.id);
            if (floor == 1 && !normals.isEmpty()) {
                int m = normals.remove(rnd.nextInt(normals.size()));
                rooms.get(m).type = Room.Type.MARKET;
                rooms.get(m).shape = Room.Shape.RECT;
            }
            if (!normals.isEmpty()) {
                int p = normals.remove(rnd.nextInt(normals.size()));
                rooms.get(p).type = Room.Type.PUZZLE;
                rooms.get(p).shape = Room.Shape.RECT;
            }
            for (Room r : rooms) if (r.type == Room.Type.ELITE || r.type == Room.Type.KEY || r.type == Room.Type.ARENA || r.type == Room.Type.HUB) r.type = Room.Type.NORMAL;
        }

        void innerGateRoom() {
            int[] d = distances(start);
            int best = -1;
            for (Room r : rooms) {
                if ((r.type == Room.Type.NORMAL || r.type == Room.Type.CACHE) && r.area() == 1 && d[r.id] >= 2) {
                    if (best < 0 || Math.abs(d[r.id] - 3) < Math.abs(d[best] - 3)) best = r.id;
                }
            }
            if (best >= 0) {
                rooms.get(best).type = Room.Type.INNER_GATE;
                rooms.get(best).shape = Room.Shape.OCTAGON;
            }
        }

        Room.Shape pickShape(Room r) {
            Room.Shape[] opts = switch (r.type) {
                case START -> new Room.Shape[]{Room.Shape.OCTAGON, Room.Shape.RECT};
                case SHRINE -> new Room.Shape[]{Room.Shape.ROUND, Room.Shape.OCTAGON, Room.Shape.TERRACE};
                case ELITE, KEY -> new Room.Shape[]{Room.Shape.RING, Room.Shape.PILLARED, Room.Shape.OCTAGON};
                case CACHE -> new Room.Shape[]{Room.Shape.ROUND, Room.Shape.OCTAGON, Room.Shape.CROSS};
                case ARENA -> new Room.Shape[]{Room.Shape.RING, Room.Shape.ROUND, Room.Shape.OCTAGON, Room.Shape.PILLARED};
                case BOSS -> new Room.Shape[]{Room.Shape.ROUND, Room.Shape.OCTAGON};
                case HUB -> new Room.Shape[]{Room.Shape.RING};
                case HALL -> new Room.Shape[]{Room.Shape.HALL};
                case CAVERN -> new Room.Shape[]{Room.Shape.CAVE};
                case ANTE -> new Room.Shape[]{Room.Shape.PILLARED, Room.Shape.RECT, Room.Shape.TERRACE};
                case SPIRIT -> new Room.Shape[]{Room.Shape.ROUND};
                case MARKET, PUZZLE -> new Room.Shape[]{Room.Shape.RECT};
                case INNER_GATE -> new Room.Shape[]{Room.Shape.OCTAGON};
                case SHAFT -> new Room.Shape[]{Room.Shape.SHAFT};
                default -> r.area() > 1
                        ? new Room.Shape[]{Room.Shape.PILLARED, Room.Shape.RING, Room.Shape.ROUND, Room.Shape.PIT, Room.Shape.OCTAGON}
                        : new Room.Shape[]{Room.Shape.RECT, Room.Shape.OCTAGON, Room.Shape.ROUND, Room.Shape.CROSS, Room.Shape.PILLARED, Room.Shape.PIT, Room.Shape.TERRACE};
            };
            return opts[(int) Math.floorMod(r.seed, (long) opts.length)];
        }

        int heightFor(Room r) {
            int hgt = switch (r.type) {
                case BOSS -> r.gw >= 3 ? 17 : 12;
                case ARENA, HUB -> 11;
                case CAVERN -> 14;
                case SHAFT -> 26;
                case HALL -> 6;
                case SHRINE -> 8;
                case START -> 7;
                default -> r.area() > 1 ? 10 : 7 + (int) Math.floorMod(r.seed >> 8, 3L);
            };
            return Math.min(hgt, 27 - r.dy);
        }

        void spawns(Room r) {
            int base;
            switch (r.type) {
                case NORMAL -> {
                    base = 2 + rank / 2 + (r.area() > 1 ? 2 : 0);
                    if (Hash.unit(r.seed, 1, 1) < 0.22) base = Math.min(base, 1);
                    r.normals = base;
                }
                case ELITE -> { r.elites = 1; r.normals = 2; }
                case KEY -> { r.elites = 1; r.normals = 2 + rank / 3; }
                case CACHE -> r.normals = Hash.unit(r.seed, 2, 2) < 0.5 ? 1 : 0;
                case HALL -> r.normals = 1 + (rank >= 3 ? 1 : 0);
                case HUB -> r.normals = 4 + rank / 2;
                case ARENA -> { r.normals = 3 + rank / 2; r.wave2Normals = 2 + rank / 2; r.wave2Elites = 1; }
                case CAVERN -> r.normals = (3 + rank / 2) * Math.max(1, r.area() / 3);
                case ANTE -> { r.normals = 2; r.elites = 1; }
                case BOSS -> r.normals = 2;
                default -> {}
            }
            if (plan.has(Modifier.SWARM)) {
                r.normals = (int) Math.ceil(r.normals * 1.6);
                r.wave2Normals = (int) Math.ceil(r.wave2Normals * 1.6);
            }
            if (plan.has(Modifier.FORTUNE)) r.normals = (int) Math.ceil(r.normals * 1.25);
            if (plan.has(Modifier.ELITE_PACK) && (r.type == Room.Type.NORMAL || r.type == Room.Type.HUB || r.type == Room.Type.ARENA) && r.normals >= 3) {
                r.elites += 1;
                r.normals -= 1;
            }
            r.normals = Math.min(r.normals, r.type == Room.Type.CAVERN ? 30 : 12);
        }

        void features(Room r) {
            int cx = r.centerX(), cz = r.centerZ();
            switch (r.type) {
                case START -> r.features.add(new Room.Feature(Room.FeatureKind.ENTRANCE, cx, 0, cz, 0));
                case SHRINE -> {
                    int up = r.shape == Room.Shape.TERRACE ? 1 : 0;
                    // every shrine has a Seal: shrines only exist on floors that have a floor below them
                    r.features.add(new Room.Feature(Room.FeatureKind.SHRINE, cx - 2, up, cz, 0));
                    r.features.add(new Room.Feature(Room.FeatureKind.SEAL, cx + 2, up, cz, 0));
                }
                case CACHE -> r.features.add(new Room.Feature(Room.FeatureKind.CACHE, cx, 0, cz, 0));
                case PUZZLE -> {
                    int n = rank >= 3 ? 5 : 4;
                    boolean alongX = Hash.unit(r.seed, 5, 5) < 0.5;
                    for (int i = 0; i < n; i++) {
                        int off = (i - n / 2) * 2;
                        r.features.add(new Room.Feature(Room.FeatureKind.RUNE, alongX ? cx + off : cx - 3, 0, alongX ? cz - 3 : cz + off, i));
                    }
                    r.features.add(new Room.Feature(Room.FeatureKind.VAULT_CACHE, alongX ? cx : cx + 3, 0, alongX ? cz + 3 : cz, 0));
                }
                case BOSS -> {
                    r.features.add(new Room.Feature(Room.FeatureKind.BOSS_SPOT, cx, 0, cz, 0));
                    r.features.add(new Room.Feature(Room.FeatureKind.EXIT_ANCHOR, cx, 0, cz + 3, 0));
                    if (area == 0 && plan.twist() == Twist.DOUBLE_GATE) {
                        // the far wall from the entry door hides the way to the second gate
                        Room.Door in = r.doors.isEmpty() ? null : r.doors.get(0);
                        int side = in == null ? 0 : (in.side + 2) % 4;
                        int fx = side == 1 ? r.x1() : side == 3 ? r.x0() : cx;
                        int fz = side == 2 ? r.z1() : side == 0 ? r.z0() : cz;
                        r.features.add(new Room.Feature(Room.FeatureKind.RIFT_WALL, fx, 0, fz, side));
                    }
                    if (area == 1 && plan.twist() == Twist.GATE_WITHIN) r.features.add(new Room.Feature(Room.FeatureKind.ANNEX_RETURN, cx, 0, cz + 3, 0));
                }
                case ELITE, KEY -> {
                    if (Hash.unit(r.seed, 3, 3) < 0.35) r.features.add(new Room.Feature(Room.FeatureKind.CACHE, cx + 3, 0, cz + 3, 0));
                }
                case INNER_GATE -> {
                    // stand the inner gate against a wall that has no door, so it never blocks the way through
                    boolean[] doorOn = new boolean[4];
                    for (Room.Door d : r.doors) doorOn[d.side] = true;
                    if (!doorOn[0]) r.features.add(new Room.Feature(Room.FeatureKind.INNER_GATE, cx, 0, cz - 4, 0));
                    else if (!doorOn[2]) r.features.add(new Room.Feature(Room.FeatureKind.INNER_GATE, cx, 0, cz + 4, 0));
                    else if (!doorOn[1]) r.features.add(new Room.Feature(Room.FeatureKind.INNER_GATE, cx + 4, 0, cz, 1));
                    else r.features.add(new Room.Feature(Room.FeatureKind.INNER_GATE, cx - 4, 0, cz, 1));
                }
                case SPIRIT -> r.features.add(new Room.Feature(Room.FeatureKind.SPIRIT_SPOT, cx, 0, cz, 0));
                case MARKET -> {
                    r.features.add(new Room.Feature(Room.FeatureKind.MARKET_SPOT, cx - 3, 0, cz, 0));
                    r.features.add(new Room.Feature(Room.FeatureKind.MARKET_SPOT, cx + 3, 0, cz, 1));
                }
                default -> {}
            }
            if (r.shape == Room.Shape.ISLANDS) {
                r.features.add(new Room.Feature(Room.FeatureKind.UPDRAFT, r.x0() + 2, -4, r.z0() + 2, 0));
                r.features.add(new Room.Feature(Room.FeatureKind.UPDRAFT, r.x1() - 2, -4, r.z1() - 2, 1));
            }
        }

        /** Friendly floors hide three lost lanterns in rooms off the spirit's path. */
        void lanterns() {
            List<Integer> picks = new ArrayList<>();
            for (Room r : rooms) if (r.type == Room.Type.NORMAL || r.type == Room.Type.CACHE) picks.add(r.id);
            for (int i = 0; i < 3 && !picks.isEmpty(); i++) {
                Room r = rooms.get(picks.remove(rnd.nextInt(picks.size())));
                r.features.add(new Room.Feature(Room.FeatureKind.LANTERN, r.centerX() + 2, 0, r.centerZ() - 2, i));
            }
        }

        void spots(Room r) {
            int cx = r.centerX(), cz = r.centerZ();
            List<int[]> cand = new ArrayList<>();
            for (int x = r.x0(); x <= r.x1(); x += 2) {
                for (int z = r.z0(); z <= r.z1(); z += 2) {
                    if (!RoomGeometry.spawnable(r, x, z)) continue;
                    boolean nearFeature = false;
                    for (Room.Feature f : r.features) if (Math.abs(f.x() - x) <= 1 && Math.abs(f.z() - z) <= 1) nearFeature = true;
                    if (nearFeature) continue;
                    cand.add(new int[]{x, z});
                }
            }
            // keep the ones near the centre first, shuffled a little so mobs don't stand in a perfect ring
            cand.sort((a, b) -> {
                double da = Math.hypot(a[0] - cx, a[1] - cz) + Hash.unit(r.seed, a[0], a[1]) * 4;
                double db = Math.hypot(b[0] - cx, b[1] - cz) + Hash.unit(r.seed, b[0], b[1]) * 4;
                return Double.compare(da, db);
            });
            for (int i = 0; i < cand.size() && r.spawnSpots.size() < 24; i++) r.spawnSpots.add(cand.get(i));
        }
    }

    /** Minimal BFS view during building (before the FloorLayout exists). */
    private record FloorLayoutView(Builder b) {
        List<Integer> path() {
            int n = b.rooms.size();
            int[] prev = new int[n];
            Arrays.fill(prev, -2);
            ArrayDeque<Integer> q = new ArrayDeque<>();
            q.add(b.start);
            prev[b.start] = -1;
            while (!q.isEmpty()) {
                int c = q.poll();
                for (Room.Door d : b.rooms.get(c).doors) if (prev[d.toRoom] == -2) { prev[d.toRoom] = c; q.add(d.toRoom); }
            }
            List<Integer> out = new ArrayList<>();
            if (prev[b.end] == -2) return out;
            for (int c = b.end; c != -1; c = prev[c]) out.add(0, c);
            return out;
        }
    }
}
