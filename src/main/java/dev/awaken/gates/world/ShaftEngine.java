package dev.awaken.gates.world;

import dev.awaken.gates.worlds.GateWorld;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The drop world (SPEC §38.6, "The Bell Shaft"): a hollow tower of bells hung one under another all the way down. The
 * shaft fills the plot's middle four slots from the top of the plot to its foot; the programme's spine is a ring of
 * landings round it, each a step lower than the last (a stair inside the lower one climbs to the door), every landing on
 * a straight side open to the shaft on a balcony with a railing and candles. The arrival is the top landing, with the
 * clapper bridge out to the top bell; the way ends on the shaft's floor under the lowest bell, or in the Lord's arena or
 * a sealed end cut into the rock beside it. A Rig room stands in the ring as built, side rooms go back into the rock at
 * their landing's height, and a fall is caught on the last ledge you stood on (WorldRules).
 */
final class ShaftEngine implements WorldEngine {
    static final int BASE = 24, STEP = 8, LH = 12, CORE0 = 6, CORE1 = 9;
    /** The shaft's middle and half-width (a rounded square, power 6). */
    static final double CX = 128, CZ = 128, HALF = 28;
    private static final int SIDE = Plots.SIDE;
    /** The shaft's distance field at block centres, the same on every floor: worked out once. */
    private static final float[] FIELD = new float[SIDE * SIDE];

    static {
        for (int x = 0; x < SIDE; x++) {
            for (int z = 0; z < SIDE; z++) {
                double u = Math.abs(x + 0.5 - CX) / HALF, v = Math.abs(z + 0.5 - CZ) / HALF;
                u = u * u * u;
                v = v * v * v;
                FIELD[x * SIDE + z] = (float) ((Math.pow(u * u + v * v, 1 / 6.0) - 1) * HALF);
            }
        }
    }

    enum Kind { LANDING, CHAMBER, FLOOR, BUILT }

    @Override
    public FloorLayout plan(Ctx c) {
        return new Plan(c).run();
    }

    /** Roughly the distance outside the shaft's wall at a block (negative inside it). */
    static double shaft(int x, int z) {
        return x < 0 || z < 0 || x >= SIDE || z >= SIDE ? 99 : FIELD[x * SIDE + z];
    }

    static double shaft(double x, double z) {
        return shaft((int) Math.floor(x), (int) Math.floor(z));
    }

    /** The ring of slots round the shaft's four, clockwise from the north-west corner. */
    static List<int[]> ring() {
        List<int[]> out = new ArrayList<>();
        for (int x = CORE0 - 1; x <= CORE1 + 1; x++) out.add(new int[]{x, CORE0 - 1});
        for (int z = CORE0; z <= CORE1 + 1; z++) out.add(new int[]{CORE1 + 1, z});
        for (int x = CORE1; x >= CORE0 - 1; x--) out.add(new int[]{x, CORE1 + 1});
        for (int z = CORE1; z >= CORE0; z--) out.add(new int[]{CORE0 - 1, z});
        return out;
    }

    static boolean corner(int gx, int gz) {
        return (gx == CORE0 - 1 || gx == CORE1 + 1) && (gz == CORE0 - 1 || gz == CORE1 + 1);
    }

    /** The side of the shaft a ring slot stands on (0 north, 1 east, 2 south, 3 west; corners count as north or south). */
    static int ringSide(int gx, int gz) {
        return gz == CORE0 - 1 ? 0 : gz == CORE1 + 1 ? 2 : gx == CORE1 + 1 ? 1 : 3;
    }

    /** The side of a ring slot's place that faces the shaft, or -1 (a corner, or a place that isn't one ring slot). */
    static int inward(Room r) {
        if (r.gw != 1 || r.gh != 1 || corner(r.gx, r.gz) || inCore(r.gx, r.gz)) return -1;
        boolean onRing = r.gx >= CORE0 - 1 && r.gx <= CORE1 + 1 && r.gz >= CORE0 - 1 && r.gz <= CORE1 + 1;
        return onRing ? (ringSide(r.gx, r.gz) + 2) % 4 : -1;
    }

    static boolean inCore(int gx, int gz) {
        return gx >= CORE0 && gx <= CORE1 && gz >= CORE0 && gz <= CORE1;
    }

    /** Slots for a run of {@code along} ring slots from {@code o} on a side, reaching {@code depth} out into the rock: gx, gz, gw, gh. */
    static int[] place(int side, int o, int along, int depth) {
        return switch (side) {
            case 0 -> new int[]{o, CORE0 - depth, along, depth};
            case 2 -> new int[]{o, CORE1 + 1, along, depth};
            case 1 -> new int[]{CORE1 + 1, o, depth, along};
            default -> new int[]{CORE0 - depth, o, depth, along};
        };
    }

    static boolean in(int[] box, int gx, int gz) {
        return box != null && gx >= box[0] && gx < box[0] + box[2] && gz >= box[1] && gz < box[1] + box[3];
    }

    /** One place round the ring: its programme group (-1 for a landing of the world's own), slots, and whether it's the Rig. */
    record Pos(int group, int gx, int gz, int gw, int gh, boolean rig) {}

    /** A stair: the place it stands in and the door above that it climbs to. */
    record Stair(Room r, Room.Door d) {}

    private static final class Plan {
        final Ctx c;
        final WorldPlan p;
        final GateWorld world;
        final int W;
        final long seed;
        final String mood;
        final Map<Integer, Kind> kinds = new TreeMap<>();
        /** The landings down the ring in order (world ids). */
        final List<Integer> landings = new ArrayList<>();
        final List<Stair> stairs = new ArrayList<>();
        int floorRoom = -1, top, roof, step = STEP;
        /** The lowest bell of the column's mark (an event rings it by name), or -1. */
        int lowest = -1;
        int[] reserved;
        boolean bridged;
        final BlockState wall, worn, crumbling, floor, wet, trim, bronze, candle, rope, chain0, lamp, light, grille, grilleZ, post, handbell;
        final List<FloorAir.Light> lights = new ArrayList<>();
        Sculpt s;

        Plan(Ctx c) {
            this.c = c;
            this.p = new WorldPlan(c);
            this.world = c.world();
            this.W = c.plot().minY() + BASE;
            this.seed = c.seed();
            this.mood = c.mood() == null ? "" : c.mood();
            WorldMats m = new WorldMats(world);
            wall = m.get("wall", "awaken:crypt_limestone");
            worn = m.get("wall_worn", "awaken:crypt_limestone_worn");
            crumbling = m.get("wall_crumbling", "awaken:crypt_limestone_crumbling");
            floor = m.get("floor", "awaken:crypt_flagstone");
            wet = m.get("floor_wet", "awaken:crypt_flagstone_wet");
            trim = m.get("trim", "awaken:crypt_tracery");
            bronze = m.get("bronze", "awaken:crypt_patina");
            candle = m.get("candles", "awaken:crypt_candles");
            rope = m.get("rope", "awaken:crypt_bell_rope");
            chain0 = m.get("chain", "minecraft:iron_chain");
            lamp = m.get("lamp", "minecraft:soul_lantern[hanging=true]");
            light = WorldMats.light(12);
            grille = GatePainter.state("minecraft:iron_bars[east=true,west=true]");
            grilleZ = GatePainter.state("minecraft:iron_bars[north=true,south=true]");
            post = GatePainter.state("minecraft:iron_bars");
            handbell = m.get("handbell", "awaken:crypt_handbell");
        }

        boolean hero(String id) {
            List<String> h = world == null ? List.of() : world.dressing().hero();
            return h.isEmpty() || h.contains(id);
        }

        FloorLayout run() {
            if (mood.equals("tolling")) {
                for (Room L : p.logic.rooms) if (L.type.combat() && L.normals > 0) L.normals++;
            }
            if (!spine()) return WorldEngine.fail("the spine doesn't fit round the shaft");
            if (!branches()) return WorldEngine.fail("no room in the rock for a side room");
            roof = W + top + LH + 22;
            for (var e : kinds.entrySet()) {
                Room r = p.world(e.getKey());
                if (e.getValue() == Kind.FLOOR) r.height = roof - W;
                else if (e.getValue() != Kind.BUILT) r.height = LH;
            }
            sculpt();
            String biome = world == null ? "" : world.biomeFor(mood);
            Room st = p.world(landings.get(0));
            float yaw = (float) Math.toDegrees(Math.atan2(-(CX - st.centerX()), CZ - st.centerZ()));
            FloorLayout f = p.finish(this::walk, new WorldPlan.Look(W, biome, air(), new WorldPlan.SculptModel(s), yaw));
            if (f == null) return WorldEngine.fail("the programme didn't carry over: " + WorldEngine.WHY.get());
            for (var e : kinds.entrySet()) {
                if (e.getValue() == Kind.BUILT && !WorldPlan.built(f.room(e.getKey()))) return WorldEngine.fail("a built room didn't rebuild");
            }
            return f;
        }

        // ---- the plan: landings round the shaft, each lower than the last; its foot at the bottom -----------------------

        boolean spine() {
            List<int[]> ring = ring();
            int n = ring.size(), v = c.variation();
            boolean cw = (Hash.of(seed, 5, 223 + v) & 1) == 0;
            int endId = p.spine.get(p.spine.size() - 1);
            Room E = p.room(endId);
            boolean arena = WorldPlan.built(E), sealed = !arena && p.endSealed();
            // the Lord's arena (or a sealed end) is cut into the rock beside the shaft's foot: its slots of the ring are kept
            int side = (int) Math.floorMod(Hash.of(seed, 7, 229 + v), 4L);
            if (arena) {
                int along = side % 2 == 0 ? E.gw : E.gh, depth = side % 2 == 0 ? E.gh : E.gw;
                if (along > CORE1 - CORE0 + 1 || depth > CORE0 - 1) return false;
                reserved = place(side, CORE0 + Hash.range(seed, 9, 231, CORE1 - CORE0 + 2 - along), along, depth);
            } else if (sealed) {
                reserved = place(side, CORE0 + Hash.range(seed, 9, 231, CORE1 - CORE0 + 1), 1, 1);
            }
            // the arc of the ring the landings may take: all of it, or from just past the kept slots round to them
            int start = Hash.range(seed, 3, 211 + v, n);
            if (reserved != null) {
                for (int i = 0; i < n; i++) {
                    int[] a = ring.get(i), b = ring.get(Math.floorMod(i - (cw ? 1 : -1), n));
                    if (!in(reserved, a[0], a[1]) && in(reserved, b[0], b[1])) start = i;
                }
            }
            List<int[]> arc = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int[] sl = ring.get(Math.floorMod(start + (cw ? i : -i), n));
                if (!in(reserved, sl[0], sl[1])) arc.add(sl);
            }
            List<List<Integer>> groups = new ArrayList<>();
            for (int i = 0; i + 1 < p.spine.size(); i++) groups.add(new ArrayList<>(List.of(p.spine.get(i))));
            List<Pos> pos = null;
            int room = c.plot().maxY() - (W + LH + 30);
            for (int tries = 0; tries < 16 && pos == null; tries++) {
                pos = lay(arc, groups);
                // a short plot takes shallower steps; a spine too long for the ring or the plot shares landings
                if (pos != null) {
                    step = Math.min(STEP, room / pos.size());
                    if (step < 4) pos = null;
                }
                if (pos == null && !fold(groups)) return false;
            }
            if (pos == null) return false;
            int m = pos.size();
            int[] dy = new int[m];
            dy[m - 1] = step;
            // each a step above the next, but the Rig stands level with the landing before it (its doors are its own height)
            for (int i = m - 2; i >= 0; i--) dy[i] = dy[i + 1] + (pos.get(i + 1).rig() ? 0 : step);
            top = dy[0];
            int prev = -1;
            for (int i = 0; i < m; i++) {
                Pos q = pos.get(i);
                int w;
                if (q.group() < 0) {
                    w = p.extra(q.gx(), q.gz(), 1, 1, dy[i]);
                } else {
                    List<Integer> grp = groups.get(q.group());
                    w = p.host(grp.get(0), q.gx(), q.gz(), q.gw(), q.gh(), dy[i]);
                    for (int k = 1; k < grp.size(); k++) p.merge(grp.get(k), grp.get(0));
                }
                kinds.put(w, q.rig() ? Kind.BUILT : Kind.LANDING);
                if (!q.rig()) landings.add(w);
                if (prev >= 0 && !p.join(prev, w)) return false;
                prev = w;
            }
            // the foot: the shaft's floor (the end itself, or the way to the arena or the sealed end)
            if (reserved != null) {
                floorRoom = p.extra(CORE0, CORE0, CORE1 - CORE0 + 1, CORE1 - CORE0 + 1, 0);
                int e = p.host(endId, reserved[0], reserved[1], reserved[2], reserved[3], 0);
                kinds.put(e, arena ? Kind.BUILT : Kind.CHAMBER);
                if (!p.join(prev, floorRoom) || !p.join(floorRoom, e)) return false;
            } else {
                floorRoom = p.host(endId, CORE0, CORE0, CORE1 - CORE0 + 1, CORE1 - CORE0 + 1, 0);
                if (!p.join(prev, floorRoom)) return false;
                p.anchor(floorRoom, (int) CX + 6, (int) CZ);
            }
            kinds.put(floorRoom, Kind.FLOOR);
            // a landing's monsters and features gather out on its balcony
            for (int w : landings) {
                Room r = p.world(w);
                int in = inward(r);
                if (in >= 0) p.anchor(w, r.centerX() + GatePainter.DX[in] * 4, r.centerZ() + GatePainter.DZ[in] * 4);
            }
            return true;
        }

        /** Lays the spine round the arc from the first start that fits, or null. */
        List<Pos> lay(List<int[]> arc, List<List<Integer>> groups) {
            int slack = Math.max(0, arc.size() - groups.size() - 1);
            int first = Hash.range(seed, 11, 233 + c.variation(), slack + 1);
            for (int t = 0; t <= slack; t++) {
                List<Pos> out = layFrom(arc, groups, (first + t) % (slack + 1));
                if (out != null) return out;
            }
            return null;
        }

        List<Pos> layFrom(List<int[]> arc, List<List<Integer>> groups, int o) {
            List<Pos> out = new ArrayList<>();
            int k = o, m = arc.size();
            for (int g = 0; g < groups.size(); g++) {
                Room L = p.room(groups.get(g).get(0));
                if (L.rig != null) {
                    // the Rig stands on a straight side, crossed in line: landings of the world's own until it fits
                    int[] at;
                    while ((at = rigAt(arc, k, L)) == null) {
                        if (k >= m - 1) return null;
                        out.add(new Pos(-1, arc.get(k)[0], arc.get(k)[1], 1, 1, false));
                        k++;
                    }
                    out.add(new Pos(g, at[0], at[1], at[2], at[3], true));
                    k += at[4];
                } else {
                    if (k >= m) return null;
                    out.add(new Pos(g, arc.get(k)[0], arc.get(k)[1], 1, 1, false));
                    k++;
                }
            }
            // the last ledge looks into the shaft: its way down is a stair to the foot
            while (true) {
                Pos last = out.get(out.size() - 1);
                if (!last.rig() && !corner(last.gx(), last.gz())) return out;
                if (k >= m) return null;
                out.add(new Pos(-1, arc.get(k)[0], arc.get(k)[1], 1, 1, false));
                k++;
            }
        }

        /** Where the Rig fits from arc position k: gx, gz, gw, gh and the ring slots it takes; null if not here. */
        int[] rigAt(List<int[]> arc, int k, Room L) {
            int[] a = arc.get(k);
            if (corner(a[0], a[1])) return null;
            int side = ringSide(a[0], a[1]);
            int along = side % 2 == 0 ? L.gw : L.gh, depth = side % 2 == 0 ? L.gh : L.gw;
            if (k + along >= arc.size() || depth > CORE0 - 1) return null;
            int lo = Integer.MAX_VALUE;
            for (int i = 0; i < along; i++) {
                int[] b = arc.get(k + i);
                if (corner(b[0], b[1]) || ringSide(b[0], b[1]) != side) return null;
                lo = Math.min(lo, side % 2 == 0 ? b[0] : b[1]);
            }
            int[] r = place(side, lo, along, depth);
            for (int gx = r[0]; gx < r[0] + r[2]; gx++) for (int gz = r[1]; gz < r[1] + r[3]; gz++) if (in(reserved, gx, gz)) return null;
            return new int[]{r[0], r[1], r[2], r[3], along};
        }

        boolean fold(List<List<Integer>> groups) {
            for (int i = groups.size() - 2; i >= 1; i--) {
                if (plain(groups.get(i)) && plain(groups.get(i + 1)) && groups.get(i).size() + groups.get(i + 1).size() <= 3) {
                    groups.get(i).addAll(groups.remove(i + 1));
                    return true;
                }
            }
            return false;
        }

        boolean plain(List<Integer> g) {
            for (int id : g) {
                Room L = p.room(id);
                if (L.rig != null || id == p.logic.start) return false;
                if (L.type != Room.Type.NORMAL && L.type != Room.Type.ELITE && L.type != Room.Type.HALL && L.type != Room.Type.SHAFT) return false;
            }
            return true;
        }

        /** Side rooms go back into the rock at their landing's height (or off the shaft's floor at its own). */
        boolean branches() {
            ArrayDeque<Integer> q = new ArrayDeque<>(p.spine);
            List<Integer> order = new ArrayList<>();
            while (!q.isEmpty()) {
                int id = q.poll();
                for (int ch : p.branches(id)) {
                    order.add(ch);
                    q.add(ch);
                }
            }
            for (int id : order) {
                int parentW = p.host[p.parent[id]];
                Room par0 = p.world(parentW);
                List<Integer> from = new ArrayList<>();
                for (var e : kinds.entrySet()) {
                    // never off a built room (its doors are its mechanism's) or one the bars close
                    if (e.getValue() != Kind.BUILT && !p.sealable(p.world(e.getKey()))) from.add(e.getKey());
                }
                from.sort(Comparator.comparingDouble(w -> w == parentW ? -1 : ChainLayout.dist(p.world(w), par0)));
                int at = -1;
                for (int f : from) {
                    Room par = p.world(f);
                    List<int[]> nb = around(par);
                    int sh = Hash.range(seed, id, 227, nb.size());
                    for (int k = 0; k < nb.size() && at < 0; k++) {
                        int[] sl = nb.get((k + sh) % nb.size());
                        if (sl[0] < 1 || sl[1] < 1 || sl[0] > 14 || sl[1] > 14 || inCore(sl[0], sl[1]) || !p.free(sl[0], sl[1], 1, 1)) continue;
                        at = p.host(id, sl[0], sl[1], 1, 1, par.dy);
                        if (!p.join(f, at)) return false;
                    }
                    if (at >= 0) break;
                }
                if (at < 0) return false;
                kinds.put(at, Kind.CHAMBER);
            }
            return true;
        }

        static List<int[]> around(Room r) {
            List<int[]> out = new ArrayList<>();
            for (int gx = r.gx; gx < r.gx + r.gw; gx++) {
                for (int gz = r.gz; gz < r.gz + r.gh; gz++) {
                    for (int sd = 0; sd < 4; sd++) {
                        int nx = gx + GatePainter.DX[sd], nz = gz + GatePainter.DZ[sd];
                        if (nx >= r.gx && nx < r.gx + r.gw && nz >= r.gz && nz < r.gz + r.gh) continue;
                        out.add(new int[]{nx, nz});
                    }
                }
            }
            return out;
        }

        // ---- the carve ----------------------------------------------------------------------------------------------

        void sculpt() {
            s = new Sculpt(this::skin, c.plot().minY(), c.plot().maxY());
            int lo = (int) (CX - HALF) - 1, hi = (int) (CX + HALF);
            s.carve(lo, W, lo, hi, roof, hi, (x, y, z) -> Math.max(shaft(x, z), Math.max(W - y, y - roof)));
            // string courses of tracery round the shaft, one to a landing's height
            s.op(lo - 3, W, lo - 3, hi + 3, roof, hi + 3, (b, x, y, z) -> {
                if (Math.floorMod(y - W, step) != step - 3 || b.get(x, y, z) != Sculpt.SOLID) return;
                double d = shaft(x, z);
                if (d >= 0 && d < 1.2) b.set(x, y, z, trim);
            });
            for (var e : kinds.entrySet()) {
                Room r = p.world(e.getKey());
                if (e.getValue() == Kind.BUILT) continue;
                Room.Door up = upDoor(r);
                if (up != null) stairs.add(new Stair(r, up));
            }
            bridged = hero("the_clapper_bridge") && inward(p.world(landings.get(0))) >= 0;
            for (var e : kinds.entrySet()) {
                Room r = p.world(e.getKey());
                switch (e.getValue()) {
                    case LANDING -> landing(r);
                    case CHAMBER -> chamber(r);
                    case FLOOR -> foot(r);
                    case BUILT -> {}
                }
            }
            // stairs after every space is carved, so their headroom cuts through whatever stands in the way
            for (Stair st : stairs) stair(st.r(), st.d());
            for (var e : kinds.entrySet()) {
                Room r = p.world(e.getKey());
                if (e.getValue() == Kind.FLOOR) glow(r, (x, z) -> shaft(x, z) < -1);
                else if (e.getValue() != Kind.BUILT) glow(r, (x, z) -> true);
            }
            p.thresholds(s, W, floor, trim);
            p.doorLights(s, W, light);
            if (hero("bell_column")) bellColumn();
            if (bridged) bridge();
            p.screens(s, W, (lat, rise, side) -> rise < 1 || rise % 4 == 3 || lat % 4 == 0 ? trim : side == 0 || side == 2 ? grille : grilleZ);
            p.handbells(s);
            s.seal();
        }

        BlockState skin(int x, int y, int z, boolean fl, boolean ceil) {
            double n = Hash.noise3(seed, x, y, z, 4);
            if (fl) return n < 0.3 ? wet : floor;
            if (ceil) return n < 0.5 ? worn : wall;
            return n < 0.3 ? worn : n > 0.85 ? crumbling : wall;
        }

        boolean lit(int x, int z) {
            double chance = switch (mood) {
                case "requiem" -> 1.0;
                case "still" -> 0.5;
                default -> 0.8;
            };
            return Hash.unit(seed, x * 11 + 1, z) < chance;
        }

        /** The door of a place that leads up to a higher one (its stair climbs to it), or null. */
        static Room.Door upDoor(Room r) {
            for (Room.Door d : r.doors) if (d.y > r.dy) return d;
            return null;
        }

        /** The row of a doorway's width {@code depth} blocks into the place from its edge: x0, x1, z0, z1. */
        static int[] row(Room.Door d, int depth) {
            int lo = RoomGeometry.DOOR_LO, hi = RoomGeometry.DOOR_HI;
            return switch (d.side) {
                case 0 -> new int[]{d.gx * 16 + lo, d.gx * 16 + hi, d.gz * 16 + depth, d.gz * 16 + depth};
                case 2 -> new int[]{d.gx * 16 + lo, d.gx * 16 + hi, d.gz * 16 + 15 - depth, d.gz * 16 + 15 - depth};
                case 1 -> new int[]{d.gx * 16 + 15 - depth, d.gx * 16 + 15 - depth, d.gz * 16 + lo, d.gz * 16 + hi};
                default -> new int[]{d.gx * 16 + depth, d.gx * 16 + depth, d.gz * 16 + lo, d.gz * 16 + hi};
            };
        }

        /**
         * A stair inside a place from its floor up to a door above it: the doorway's width, straight in past its
         * threshold, a block a step, a riser of stone under each tread.
         */
        void stair(Room r, Room.Door d) {
            int rise = d.y - r.dy, y0 = W + r.dy, h = RoomGeometry.DOOR_HEIGHT;
            for (int j = 0; j < rise - 1; j++) {
                int[] b = row(d, Sculpt.SHELL + j);
                int tread = y0 + rise - 2 - j;
                s.box(b[0], y0 - 1, b[2], b[1], tread - 1, b[3], j % 3 == 2 ? trim : wall);
                s.box(b[0], tread, b[2], b[1], tread, b[3], floor);
                s.carve(b[0], tread + 1, b[2], b[1], tread + h, b[3], (x, y, z) -> Sculpt.box(x, y, z, b[0], tread + 1, b[2], b[1] + 1, tread + h + 1, b[3] + 1));
                if (j % 3 == 1) s.putOpen((b[0] + b[1]) / 2, tread + 2, (b[2] + b[3]) / 2, light);
            }
        }

        /** Whether (x, z) is under a stair's run (nothing waits there, no railing crosses it). */
        static boolean onStair(Room r, Room.Door d, int x, int z) {
            if (d == null) return false;
            int depth = Sculpt.SHELL + d.y - r.dy, lo = RoomGeometry.DOOR_LO - 1, hi = RoomGeometry.DOOR_HI + 1;
            int lx = x - d.gx * 16, lz = z - d.gz * 16;
            return switch (d.side) {
                case 0 -> lx >= lo && lx <= hi && lz >= 0 && lz <= depth;
                case 2 -> lx >= lo && lx <= hi && lz <= 15 && lz >= 15 - depth;
                case 1 -> lz >= lo && lz <= hi && lx <= 15 && lx >= 15 - depth;
                default -> lz >= lo && lz <= hi && lx >= 0 && lx <= depth;
            };
        }

        boolean anyStair(int x, int z) {
            for (Stair st : stairs) if (onStair(st.r(), st.d(), x, z)) return true;
            return false;
        }

        /**
         * A landing: a ringers' room in the rock vaulted towards the shaft, and on a straight side its balcony out to
         * the shaft's edge, a railing of tracery and bars along it and candles at its foot. Bell ropes hang down its back.
         */
        void landing(Room r) {
            int x0 = r.gx * 16, z0 = r.gz * 16, x1 = x0 + 15, z1 = z0 + 15, y0 = W + r.dy;
            int in = inward(r);
            boolean alongX = in == 0 || in == 2;
            double cxm = x0 + 8, czm = z0 + 8;
            // the balcony's box, out from the room through to the shaft (continuous coordinates)
            double bx0 = x0 + 2, bz0 = z0 + 2, bx1 = x1 - 1, bz1 = z1 - 1;
            switch (in) {
                case 2 -> bz1 = z1 + 15;
                case 0 -> bz0 = z0 - 14;
                case 1 -> bx1 = x1 + 15;
                case 3 -> bx0 = x0 - 14;
                default -> {}
            }
            final double fx0 = bx0, fz0 = bz0, fx1 = bx1, fz1 = bz1;
            s.carve((int) fx0 - 1, y0, (int) fz0 - 1, (int) fx1 + 1, y0 + LH + 1, (int) fz1 + 1, (x, y, z) -> {
                double d = Sculpt.box(x, y, z, x0 + 2, y0, z0 + 2, x1 - 1, y0 + LH + 1, z1 - 1);
                if (in >= 0) d = Math.min(d, Math.max(Sculpt.box(x, y, z, fx0, y0, fz0, fx1, y0 + LH + 1, fz1), wedge(in, x, z)));
                double lat = in < 0 ? 0 : alongX ? Math.abs(x - cxm) : Math.abs(z - czm);
                double ceil = y0 + LH - 3 + 3 * Math.sqrt(Math.max(0, 1 - lat * lat / 42.25));
                return Math.max(d, y - ceil);
            });
            if (in >= 0) {
                boolean start = r.id == landings.get(0);
                s.op((int) fx0, y0 - 1, (int) fz0, (int) fx1, y0 + 1, (int) fz1, (b, x, y, z) -> {
                    boolean beyond = in == 2 ? z > z1 : in == 0 ? z < z0 : in == 1 ? x > x1 : x < x0;
                    int lat = alongX ? x - x0 : z - z0;
                    double d = shaft(x, z);
                    // (a stair down to the foot is carved after, through the railing just as wide as its steps)
                    if (!beyond || lat < 2 || lat > 13 || d < 0 || wedge(in, x + 0.5, z + 0.5) >= 0) return;
                    int past = in == 2 ? z - z1 : in == 0 ? z0 - z : in == 1 ? x - x1 : x0 - x;
                    if (y == y0 - 1) b.set(x, y, z, floor);
                    else if (d < 1) {
                        // the railing, open where the clapper bridge sets out
                        if (start && bridged && lat >= 7 && lat <= 8) return;
                        b.set(x, y, z, y == y0 ? trim : alongX ? grille : grilleZ);
                    } else if (anyStair(x, z)) {
                        return;
                    } else if (y == y0 + 1 && Math.floorMod(past, 4) == 2 && Math.floorMod(lat, 4) == 0) {
                        b.set(x, y, z, light);
                    } else if (y == y0 && d >= 2 && d < 3 && Math.floorMod(lat, 3) == 1) {
                        // candles a step back from the railing (nobody climbs them onto it)
                        b.set(x, y, z, BellPieces.candles(candle, 2 + Hash.range(seed, x, z, 3), lit(x, z)));
                    }
                });
            }
            // bell ropes down the back wall, where the ringers stood
            int back = in >= 0 ? (in + 2) % 4 : freeSide(r);
            for (int k = -1; k <= 1; k += 2) {
                int rx = back == 1 ? x1 - 2 : back == 3 ? x0 + 2 : (int) cxm + k * 4;
                int rz = back == 2 ? z1 - 2 : back == 0 ? z0 + 2 : (int) czm + k * 4;
                s.op(rx, y0 + 3, rz, rx, y0 + LH, rz, (b, x, y, z) -> {
                    if (b.open(x, y, z)) b.set(x, y, z, rope);
                });
            }
            // a handbell between the ropes, its yoke along the back wall
            int hx = back == 1 ? x1 - 2 : back == 3 ? x0 + 2 : (int) cxm, hz = back == 2 ? z1 - 2 : back == 0 ? z0 + 2 : (int) czm;
            if (!anyStair(hx, hz)) p.handbell(handbell, W, hx, r.dy, hz, back == 1 || back == 3 ? Direction.EAST : Direction.NORTH);
            lights.add(new FloorAir.Light(r.centerX(), r.dy + 3, r.centerZ(), 0xffb060, 9f, 0.6f));
        }

        /**
         * The side of the shaft a balcony facing {@code in} may take, as a distance (negative on it): round the shaft's
         * corners two balconies meet at different heights, so each keeps to its own side of the diagonal with rock
         * between. A field, so the carve's shell follows the cut.
         */
        static double wedge(int in, double x, double z) {
            double u = x - CX, v = z - CZ;
            double m = switch (in) {
                case 2 -> -v - Math.abs(u);
                case 0 -> v - Math.abs(u);
                case 3 -> u - Math.abs(v);
                default -> -u - Math.abs(v);
            };
            return (1 - m) * 0.7071;
        }

        /** A wall of a place with no door in it (for a corner landing's ropes). */
        static int freeSide(Room r) {
            for (int sd = 0; sd < 4; sd++) {
                boolean used = false;
                for (Room.Door d : r.doors) used |= d.side == sd;
                if (!used) return sd;
            }
            return 0;
        }

        interface Where {
            boolean ok(int x, int z);
        }

        /** Hidden light four apart over a place's floor, set over whatever stands in the way. */
        void glow(Room r, Where where) {
            int x0 = r.gx * 16, z0 = r.gz * 16, x1 = x0 + r.gw * 16 - 1, z1 = z0 + r.gh * 16 - 1, y0 = W + r.dy;
            int mx = (x0 + x1 + 1) / 2, mz = (z0 + z1 + 1) / 2;
            s.op(x0, y0 + 1, z0, x1, y0 + 1, z1, (b, x, y, z) -> {
                if (Math.floorMod(x - mx, 4) != 0 || Math.floorMod(z - mz, 4) != 0 || !where.ok(x, z)) return;
                for (int yy = y; yy <= y + 3; yy++) {
                    if (b.open(x, yy, z)) {
                        b.set(x, yy, z, light);
                        return;
                    }
                }
            });
        }

        /** A side room back in the rock: low and domed, candles in its corners. The sealed end is one of these too. */
        void chamber(Room r) {
            int x0 = r.gx * 16, z0 = r.gz * 16, x1 = x0 + 15, z1 = z0 + 15, y0 = W + r.dy;
            PassageEngine.Shape sh = PassageEngine.Shape.of(r, false);
            s.carve(x0, y0, z0, x1, y0 + 8, z1, (x, y, z) -> {
                double q = sh.q(x, z), h = y - y0;
                return Math.max(sh.d(x, z), Math.max(-h, h - (4 + 3 * Math.sqrt(Math.max(0, 1 - q * q)))));
            });
            for (int[] k : new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
                int x = r.centerX() + k[0], z = r.centerZ() + k[1];
                s.putOpen(x, y0, z, BellPieces.candles(candle, 1 + Hash.range(seed, x, z, 4), lit(x, z)));
            }
            lights.add(new FloorAir.Light(r.centerX(), r.dy + 2, r.centerZ(), 0xffb060, 7f, 0.5f));
        }

        /**
         * The shaft's foot: wet flagstones under the lowest bell, candles in a ring round its middle, and a passage
         * through the rock from every way in at its own height (the arena's, the sealed end's, a side room's).
         */
        void foot(Room r) {
            for (Room.Door d : r.doors) {
                if (d.y != r.dy) continue;
                int[] b = WorldPlan.doorBox(d, 8);
                int y = W + d.y, h = RoomGeometry.DOOR_HEIGHT;
                s.carve(b[0], y, b[2], b[1], y + h - 1, b[3], (x, yy, z) -> Sculpt.box(x, yy, z, b[0], y, b[2], b[1] + 1, y + h, b[3] + 1));
                int[] mid = row(d, 4);
                s.putOpen((mid[0] + mid[1]) / 2, y + 1, (mid[2] + mid[3]) / 2, light);
            }
            // hidden light along the foot of the shaft's wall, where the way comes down off the stair
            int lo = (int) (CX - HALF), hi = (int) (CX + HALF);
            s.op(lo, W + 1, lo, hi, W + 1, hi, (b, x, y, z) -> {
                double d = shaft(x, z);
                if (d >= -1.5 && d < -0.5 && ((x + z) & 3) == 0 && b.open(x, y, z)) b.set(x, y, z, light);
            });
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI / 8;
                int x = (int) Math.floor(CX + Math.cos(a) * 12), z = (int) Math.floor(CZ + Math.sin(a) * 12);
                s.putOpen(x, W, z, BellPieces.candles(candle, 2 + Hash.range(seed, x, z, 3), lit(x, z)));
            }
            lights.add(new FloorAir.Light((int) CX, 2, (int) CZ, 0xffb060, 16f, 0.7f));
            // the foot's handbell, under the lowest bell: strike it and the lowest bell answers (SPEC §38.8)
            int hx = (int) CX + 5, hz = (int) CZ;
            p.handbell(handbell, W, hx, 0, hz, Direction.NORTH, "lowest_bell", null);
        }

        /**
         * The bell column: bells hung one under another down the shaft's middle, each from the one above's mouth, the
         * top one from the roof (low over the clapper bridge's stand when there is one), the lowest well over the floor.
         */
        void bellColumn() {
            int stand = W + top, chainTo = roof - 1, y = roof - 1;
            for (int k = 0; k < 24; k++) {
                double R = k == 0 ? 6 : 3 + Hash.unit(seed, k, 233) * 3;
                int H = (int) Math.round(R * 2.3);
                int lip = k == 0 && bridged ? stand + 7 : y - H;
                if (lip < W + 8) break;
                BellPieces.bell(s, CX, lip, CZ, R, H, chainTo, bronze, lamp, chain0, true);
                lowest = p.marks.size();
                p.bell(CX, lip - W, CZ, R, H);
                if (lights.size() < 90) lights.add(new FloorAir.Light((int) CX, lip - W + H - 3, (int) CZ, 0x8fd0ff, 10f + (float) R, 0.7f));
                chainTo = lip + 2;
                y = lip - 5 - Hash.range(seed, k, 239, 6);
                if (bridged && lip > stand) {
                    y = Math.min(y, stand - 4);
                    chainTo = stand - 2;
                }
            }
            if (lowest >= 0) {
                FloorLayout.Mark m = p.marks.get(lowest);
                p.marks.set(lowest, new FloorLayout.Mark(m.kind(), "lowest", m.x0(), m.y0(), m.z0(), m.x1(), m.y1(), m.z1()));
            }
        }

        /**
         * The clapper bridge: from the arrival's balcony straight out over the shaft to its middle line, then along it
         * to a round stand in the top bell's mouth. Its rails and the stand's are bars two high, so nobody steps up onto
         * them and off into the shaft.
         */
        void bridge() {
            Room r = p.world(landings.get(0));
            int in = inward(r), y0 = W + r.dy;
            boolean alongZ = in == 0 || in == 2;
            // leg one runs inward on the landing's middle line; leg two along the shaft's middle line to the stand
            double ac = alongZ ? r.gx * 16 + 8 : r.gz * 16 + 8, cross = alongZ ? CZ : CX, end = alongZ ? CX : CZ;
            double edge = in == 2 ? (r.gz + 1) * 16 : in == 0 ? r.gz * 16 : in == 1 ? (r.gx + 1) * 16 : r.gx * 16;
            double aLo = Math.min(edge, cross), aHi = Math.max(edge, cross), bLo = Math.min(ac, end), bHi = Math.max(ac, end);
            BlockState railA = alongZ ? grilleZ : grille, railB = alongZ ? grille : grilleZ;
            double ux0 = alongZ ? Math.min(ac, end) : aLo, ux1 = alongZ ? Math.max(ac, end) : aHi;
            double uz0 = alongZ ? aLo : Math.min(ac, end), uz1 = alongZ ? aHi : Math.max(ac, end);
            int x0 = (int) Math.min(ux0, CX) - 6, x1 = (int) Math.max(ux1, CX) + 6, z0 = (int) Math.min(uz0, CZ) - 6, z1 = (int) Math.max(uz1, CZ) + 6;
            s.op(x0, y0 - 1, z0, x1, y0 + 1, z1, (b, x, y, z) -> {
                if (shaft(x, z) >= 0) return;
                double px = x + 0.5, pz = z + 0.5;
                double a = Math.abs((alongZ ? px : pz) - ac), sa = alongZ ? pz : px;
                double bb = Math.abs((alongZ ? pz : px) - cross), tb = alongZ ? px : pz;
                boolean deck = a < 1 && sa >= aLo - 1 && sa <= aHi + 1 || bb < 1 && tb >= bLo - 1 && tb <= bHi + 1;
                double rc = Math.hypot(px - CX, pz - CZ);
                if (rc < 4.3) {
                    // the stand's middle is solid: the bells below hang from its underside
                    if (y == y0 - 1) b.set(x, y, z, rc > 2.8 || rc < 1.2 ? trim : floor);
                    else if (rc > 2.8 && !deck) b.set(x, y, z, post);
                    return;
                }
                if (deck) {
                    if (y == y0 - 1) b.set(x, y, z, Math.floorMod((int) Math.floor(a < 1 ? sa : tb), 5) == 0 ? trim : floor);
                    return;
                }
                boolean onA = a < 2 && sa >= aLo - 2 && sa <= aHi + 2, onB = bb < 2 && tb >= bLo - 2 && tb <= bHi + 2;
                if (onA || onB) b.set(x, y, z, y == y0 - 1 ? trim : onA && onB ? post : onA ? railA : railB);
            });
            // hidden light along both legs
            for (double t = aLo + 2; t <= aHi; t += 5) {
                int x = (int) Math.floor(alongZ ? ac : t), z = (int) Math.floor(alongZ ? t : ac);
                if (shaft(x, z) < 0) s.putOpen(x, y0 + 1, z, light);
            }
            for (double t = bLo + 2; t <= bHi - 4; t += 5) {
                int x = (int) Math.floor(alongZ ? t : cross), z = (int) Math.floor(alongZ ? cross : t);
                s.putOpen(x, y0 + 1, z, light);
            }
        }

        // ---- what the floor walks on and how its air glows ----------------------------------------------------------

        boolean walk(Room r, int x, int z) {
            Kind k = kinds.get(r.id);
            if (k == null || k == Kind.BUILT) return false;
            if (onStair(r, upDoor(r), x, z)) return false;
            return switch (k) {
                case LANDING -> {
                    int lx = x - r.gx * 16, lz = z - r.gz * 16;
                    yield lx >= 3 && lx <= 12 && lz >= 3 && lz <= 12;
                }
                case CHAMBER -> PassageEngine.Shape.of(r, false).d(x + 0.5, z + 0.5) < -1.6
                        && Math.abs(Math.abs(x + 0.5 - r.centerX()) - 4) + Math.abs(Math.abs(z + 0.5 - r.centerZ()) - 4) > 1.5;
                case FLOOR -> shaft(x, z) < -2 && Math.abs(Math.hypot(x + 0.5 - CX, z + 0.5 - CZ) - 12) > 1.5;
                default -> false;
            };
        }

        FloorAir air() {
            List<FloorAir.Light> all = lights.size() > 96 ? lights.subList(0, 96) : lights;
            List<FloorAir.Mist> mists = List.of(new FloorAir.Mist(0, 8, 0x3a4050, mood.equals("storm") ? 0.4f : 0.6f),
                    new FloorAir.Mist(top / 2, 24, 0x22262e, 0.2f));
            List<FloorAir.Shaft> shafts = List.of(new FloorAir.Shaft((int) CX, roof - W, (int) CZ, (int) CX, 0, (int) CZ, 5f, 0x9aa8c8,
                    mood.equals("storm") ? 0.4f : 0.25f));
            return new FloorAir(new ArrayList<>(all), shafts, mists, List.of());
        }
    }
}
