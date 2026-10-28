package dev.awaken.gates.world;

import dev.awaken.gates.worlds.GateWorld;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The passages world (SPEC §38.6, "The Ossuary Ways"): the crypt under the Belfry where the bell-ringers were laid. The
 * programme's rooms become chambers strung on winding bone-lined passages ({@link ChainLayout}): walls of stacked bones
 * with a band of skulls and candles set into them, rounded chambers under low domes. The hub is the Bone Rotunda round a
 * pillar of skulls, a quiet chamber holds the charnel well, long rooms are the Lantern Row, and the end is the
 * ringers' tomb. The Lord's arena and Rig rooms stand on the chain as built rooms.
 */
final class PassageEngine implements WorldEngine {
    static final int BASE = 40;
    /** Passage half-width (3 blocks walk), plan inset of a chamber from its slots. */
    static final double HW = 1.6, INSET = 2.5;
    /** A wall side (0 north, 1 east, 2 south, 3 west) as a direction. */
    static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    enum Kind { ARRIVAL, CRYPT, ROTUNDA, WELL, ROW, TOMB, LINK, BUILT }

    @Override
    public FloorLayout plan(Ctx c) {
        return new Plan(c).run();
    }

    /** A chamber's plan: a rounded box (a superellipse, power 4, or a circle) inset from its slots. */
    record Shape(double cx, double cz, double a, double b, boolean round) {
        static Shape of(Room r, boolean round) {
            double a = r.gw * 8 - INSET, b = r.gh * 8 - INSET;
            if (round) a = b = Math.min(a, b);
            return new Shape(r.gx * 16 + r.gw * 8, r.gz * 16 + r.gh * 8, a, b, round);
        }

        double q(double x, double z) {
            double u = (x - cx) / a, v = (z - cz) / b;
            if (round) return Math.sqrt(u * u + v * v);
            u *= u;
            v *= v;
            return Math.sqrt(Math.sqrt(u * u + v * v));
        }

        /** Roughly the distance in blocks outside the wall (negative inside). */
        double d(double x, double z) {
            return (q(x, z) - 1) * Math.min(a, b);
        }

        /** The wall's outward normal at a point, as x and z. */
        double[] normal(double x, double z) {
            double u = (x - cx) / a, v = (z - cz) / b;
            double nx = round ? u / a : u * u * u / a, nz = round ? v / b : v * v * v / b;
            double l = Math.hypot(nx, nz);
            return l == 0 ? new double[]{1, 0} : new double[]{nx / l, nz / l};
        }

        double r(double x, double z) {
            return Math.hypot(x - cx, z - cz);
        }
    }

    private static final class Plan {
        final Ctx c;
        final WorldPlan p;
        final ChainLayout chain;
        final GateWorld world;
        final int W;
        final long seed;
        final String mood;
        final Map<Integer, Kind> kinds = new HashMap<>();
        final BlockState wall, worn, crumbling, floor, wet, trim, candle, niche, vestment, rope, water, chain0, lamp, soulLamp, light,
                boneX, boneY, boneZ, handbell;
        final BlockState[] skulls = new BlockState[16];
        final List<FloorAir.Light> lights = new ArrayList<>();
        int well = -1;
        /** The crypt whose back wall keeps the sealed niche (SPEC §38.8), or -1. */
        int niched = -1;
        Sculpt s;

        Plan(Ctx c) {
            this.c = c;
            this.p = new WorldPlan(c);
            this.world = c.world();
            this.W = c.plot().minY() + BASE;
            this.seed = c.seed();
            this.mood = c.mood() == null ? "" : c.mood();
            this.chain = new ChainLayout(p, seed, c.variation());
            WorldMats m = new WorldMats(world);
            wall = m.get("wall", "awaken:crypt_limestone");
            worn = m.get("wall_worn", "awaken:crypt_limestone_worn");
            crumbling = m.get("wall_crumbling", "awaken:crypt_limestone_crumbling");
            floor = m.get("floor", "awaken:crypt_flagstone");
            wet = m.get("floor_wet", "awaken:crypt_flagstone_wet");
            trim = m.get("trim", "awaken:crypt_tracery");
            candle = m.get("candles", "awaken:crypt_candles");
            niche = m.get("niche", "awaken:crypt_niche");
            vestment = m.get("vestment", "awaken:crypt_vestment");
            rope = m.get("rope", "awaken:crypt_bell_rope");
            water = m.get("water", "minecraft:water");
            chain0 = m.get("chain", "minecraft:iron_chain");
            lamp = m.get("lamp", "minecraft:lantern[hanging=true]");
            soulLamp = m.get("soul_lamp", "minecraft:soul_lantern[hanging=true]");
            BlockState bone = m.get("bone", "minecraft:bone_block");
            boneX = WorldMats.with(bone, "axis", "x");
            boneY = WorldMats.with(bone, "axis", "y");
            boneZ = WorldMats.with(bone, "axis", "z");
            BlockState skull = m.get("skull", "minecraft:skeleton_skull");
            for (int i = 0; i < 16; i++) skulls[i] = WorldMats.with(skull, "rotation", i);
            light = WorldMats.light(12);
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
            if (!chain.lay(this::size)) return WorldEngine.fail("the chain doesn't fit: " + WorldEngine.WHY.get());
            kinds();
            sculpt();
            String biome = world == null ? "" : world.biomeFor(mood);
            FloorLayout f = p.finish(this::walk, new WorldPlan.Look(W, biome, air(), new WorldPlan.SculptModel(s), Float.NaN));
            if (f == null) return WorldEngine.fail("the programme didn't carry over: " + WorldEngine.WHY.get());
            // a built room its painter can't build (a Rig that won't fit its doors) would be a hole: try the next variation
            for (var e : kinds.entrySet()) {
                if (e.getValue() == Kind.BUILT && !WorldPlan.built(f.room(e.getKey()))) return WorldEngine.fail("a Rig room didn't rebuild");
            }
            return f;
        }

        int[] size(int id) {
            Room L = p.room(id);
            if (WorldPlan.built(L)) return new int[]{L.gw, L.gh};
            if (id == p.logic.start || id == p.logic.end) return new int[]{1, 1};
            return switch (L.type) {
                case HUB, ARENA, CAVERN -> new int[]{2, 2};
                case HALL -> new int[]{1, 2};
                default -> new int[]{1, 1};
            };
        }

        void kinds() {
            for (int w : chain.links.keySet()) kinds.put(w, Kind.LINK);
            for (int w : chain.places) {
                Room r = p.world(w);
                Kind k;
                if (WorldPlan.built(p.room(programme(w)))) k = Kind.BUILT;
                else if (w == p.host[p.logic.start]) k = Kind.ARRIVAL;
                else if (w == p.host[p.logic.end]) k = Kind.TOMB;
                else if (r.type == Room.Type.HUB && r.gw >= 2 && r.gh >= 2 && hero("bone_gallery")) k = Kind.ROTUNDA;
                else if (r.type == Room.Type.HALL && hero("the_lantern_row")) k = Kind.ROW;
                else k = Kind.CRYPT;
                kinds.put(w, k);
                r.height = switch (k) {
                    case ROTUNDA -> 16;
                    case ROW -> 9;
                    case BUILT -> r.height;
                    default -> 8;
                };
            }
            // the charnel well: a quiet one-slot chamber about halfway along the way
            if (hero("charnel_well")) {
                double mid = (chain.spine.size() - 1) / 2.0, best = Double.MAX_VALUE;
                for (int i = 0; i < chain.spine.size(); i++) {
                    int w = chain.spine.get(i);
                    Room r = p.world(w);
                    if (kinds.get(w) != Kind.CRYPT || r.gw != 1 || r.gh != 1 || p.sealable(r) || r.type == Room.Type.INNER_GATE) continue;
                    double sc = Math.abs(i - mid);
                    if (sc < best) {
                        best = sc;
                        well = w;
                    }
                }
                if (well >= 0) kinds.put(well, Kind.WELL);
            }
            // where each place's features gather: the arrival at the stair's foot, the tomb before its sarcophagus
            for (int w : chain.places) {
                Room r = p.world(w);
                Kind k = kinds.get(w);
                if (k == Kind.ARRIVAL) {
                    int back = back(r);
                    p.anchor(w, r.centerX() + GatePainter.DX[back] * 3, r.centerZ() + GatePainter.DZ[back] * 3);
                } else if (k == Kind.TOMB) {
                    int back = back(r);
                    p.anchor(w, r.centerX() + GatePainter.DX[back] * 2, r.centerZ() + GatePainter.DZ[back] * 2);
                } else if (k == Kind.WELL || k == Kind.ROTUNDA) {
                    p.anchor(w, r.centerX() + 5, r.centerZ() + 1);
                }
            }
        }

        /** The programme room a world place hosts (its first), or -1 for the world's own. */
        int programme(int w) {
            for (int i = 0; i < p.host.length; i++) if (p.host[i] == w) return i;
            return -1;
        }

        /** The side of a place away from its first doorway (where the arrival's stair and the tomb's sarcophagus go). */
        static int back(Room r) {
            return r.doors.isEmpty() ? 0 : (r.doors.get(0).side + 2) % 4;
        }

        // ---- the carve ----------------------------------------------------------------------------------------------

        void sculpt() {
            s = new Sculpt(this::skin, c.plot().minY(), c.plot().maxY());
            List<Integer> crypts = new ArrayList<>();
            for (var e : kinds.entrySet()) if (e.getValue() == Kind.CRYPT) crypts.add(e.getKey());
            crypts.sort(null);
            if (!crypts.isEmpty()) niched = crypts.get((int) Math.floorMod(Hash.of(seed, 5, 271), crypts.size()));
            for (var e : kinds.entrySet()) {
                Room r = p.world(e.getKey());
                switch (e.getValue()) {
                    case LINK -> passage(r, chain.links.get(e.getKey()));
                    case ROW -> row(r);
                    case ROTUNDA -> rotunda(r);
                    case ARRIVAL, CRYPT, WELL, TOMB -> chamber(r, e.getValue());
                    default -> {}
                }
            }
            p.thresholds(s, W, floor, trim);
            p.doorLights(s, W, light);
            p.screens(s, W, (lat, rise, side) -> rise % 3 == 1 ? boneY : worn);
            p.handbells(s);
            s.seal();
        }

        BlockState skin(int x, int y, int z, boolean fl, boolean ceil) {
            double n = Hash.noise3(seed, x, y, z, 3.5);
            if (fl) return n < (mood.equals("storm") ? 0.55 : 0.25) ? wet : floor;
            if (ceil) return n < 0.45 ? worn : wall;
            return n < 0.3 ? worn : n > 0.86 ? crumbling : wall;
        }

        boolean lit(int x, int z) {
            double chance = switch (mood) {
                case "requiem" -> 1.0;
                case "still" -> 0.55;
                default -> 0.8;
            };
            return Hash.unit(seed, x * 13 + 5, z) < chance;
        }

        /** Rotation (0..15) of a skull facing the way (fx, fz). */
        static int rotation(double fx, double fz) {
            double yaw = Math.toDegrees(Math.atan2(-fx, fz));
            return Math.floorMod((int) Math.round(yaw / 22.5), 16);
        }

        /**
         * The ossuary's walls: from the floor, a worn plinth, bone ends, a band of skulls and candles set into the
         * wall, bones laid lengthwise, bone ends again. Applied to the first block of wall behind open space.
         */
        void bones(int x0, int z0, int x1, int z1, Wall wallAt, int bands) {
            s.op(x0, W, z0, x1, W + 4 + (bands > 1 ? 4 : 0), z1, (b, x, y, z) -> {
                if (b.get(x, y, z) != Sculpt.SOLID) return;
                double[] dn = wallAt.at(x + 0.5, z + 0.5);
                if (dn == null || dn[0] < 0 || dn[0] >= 2.2) return;
                int h = y - W;
                boolean front = dn[0] < 1.1;
                BlockState ends = Math.abs(dn[1]) > Math.abs(dn[2]) ? boneX : boneZ, lengths = ends == boneX ? boneZ : boneX;
                int band = h % 4;
                if (!front) {
                    if (band == 2) b.set(x, y, z, ends);
                    return;
                }
                switch (band) {
                    case 0 -> b.set(x, y, z, h == 0 ? worn : lengths);
                    case 1, 3 -> b.set(x, y, z, band == 1 ? ends : lengths);
                    default -> {
                        double u = Hash.unit(seed, x * 31 + y, z);
                        if (u < 0.16) {
                            b.set(x, y, z, BellPieces.candles(candle, 1 + Hash.range(seed, x, z, 3), lit(x, z)));
                        } else if (u < 0.84) {
                            b.set(x, y, z, skulls[rotation(-dn[1], -dn[2])]);
                        } else b.set(x, y, z, ends);
                    }
                }
            });
        }

        /** A wall's distance (outside, in blocks) and outward normal at a plan point, or null where there's none. */
        interface Wall {
            double[] at(double x, double z);
        }

        /**
         * Hidden lights in a grid four apart, on the place's middle, wherever it's open at head height (the visible
         * light is its candles).
         */
        void glow(int x0, int z0, int x1, int z1, Wall inside) {
            int mx = (x0 + x1 + 1) / 2, mz = (z0 + z1 + 1) / 2;
            s.op(x0, W + 1, z0, x1, W + 1, z1, (b, x, y, z) -> {
                if (Math.floorMod(x - mx, 4) != 0 || Math.floorMod(z - mz, 4) != 0) return;
                double[] dn = inside.at(x + 0.5, z + 0.5);
                if (dn == null || dn[0] >= 0) return;
                // over a heap or a coffin, the light sits on top of it
                for (int yy = y; yy <= y + 3; yy++) {
                    if (b.open(x, yy, z)) {
                        b.set(x, yy, z, light);
                        return;
                    }
                }
            });
        }

        void chamber(Room r, Kind k) {
            Shape sh = Shape.of(r, false);
            int x0 = r.gx * 16, z0 = r.gz * 16, x1 = x0 + r.gw * 16 - 1, z1 = z0 + r.gh * 16 - 1;
            double dome = 3 + (r.gw + r.gh - 2) * 1.5;
            s.carve(x0, W, z0, x1, W + 4 + (int) dome, z1, (x, y, z) -> {
                double q = sh.q(x, z), h = y - W;
                double ceil = 4 + dome * Math.sqrt(Math.max(0, 1 - q * q));
                return Math.max(sh.d(x, z), Math.max(-h, h - ceil));
            });
            Wall wa = (x, z) -> {
                double[] n = sh.normal(x, z);
                return new double[]{sh.d(x, z), n[0], n[1]};
            };
            bones(x0, z0, x1, z1, wa, 1);
            double cx = sh.cx(), cz = sh.cz();
            int back = back(r);
            switch (k) {
                case ARRIVAL -> stair(r, back);
                case TOMB -> tomb(r, back);
                case WELL -> well(r);
                default -> {
                    // a crypt's own furniture: bone heaps in the corners, or a sarcophagus, by its hash
                    if (r.gw >= 2 && r.gh >= 2) {
                        for (int[] q : new int[][]{{-7, -7}, {7, -7}, {-7, 7}, {7, 7}}) pillar(cx + q[0], cz + q[1], W + 4 + (int) (dome * 0.7), 1.6);
                    } else if ((Hash.of(seed, r.id, 83) & 1) == 0) {
                        for (int[] q : new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) heap((int) (cx + q[0]), (int) (cz + q[1]));
                    } else sarcophagus((int) cx, (int) cz, (r.id & 1) == 0);
                    backBell(r, sh, back);
                }
            }
            // the hidden light last, so it finds the top of whatever stands in its way
            glow(x0, z0, x1, z1, wa);
            lights.add(new FloorAir.Light((int) cx, 2, (int) cz, 0xffb060, 7f + r.gw * 2, 0.6f));
        }

        /**
         * A handbell by a crypt's back wall, its yoke along the wall. In one crypt a niche is set in the wall behind it:
         * strike the bell and the niche opens (the sealed niche, SPEC §38.8).
         */
        void backBell(Room r, Shape sh, int back) {
            int dx = GatePainter.DX[back], dz = GatePainter.DZ[back];
            double reach = (dx != 0 ? sh.a() : sh.b());
            int x = (int) Math.floor(sh.cx() + dx * (reach - 1.4)), z = (int) Math.floor(sh.cz() + dz * (reach - 1.4));
            Direction along = dx != 0 ? Direction.EAST : Direction.NORTH;
            if (r.id != niched) {
                p.handbell(handbell, W, x, 0, z, along);
                return;
            }
            int nx = (int) Math.floor(sh.cx() + dx * (reach + 0.5)), nz = (int) Math.floor(sh.cz() + dz * (reach + 0.5));
            Direction in = SIDES[(back + 2) % 4];
            s.put(nx, W + 1, nz, WorldMats.facing(niche, in));
            p.handbell(handbell, W, x, 0, z, along, "sealed_niche", new int[]{nx, 1, nz, nx, 1, nz});
        }

        /** A short way in from each doorway, so a round room meets a door wherever it is on the slot's edge. */
        void doorways(Room r, int depth) {
            for (Room.Door d : r.doors) {
                int lo = RoomGeometry.DOOR_LO, hi = RoomGeometry.DOOR_HI;
                int bx0, bx1, bz0, bz1;
                switch (d.side) {
                    case 0 -> { bx0 = d.gx * 16 + lo; bx1 = d.gx * 16 + hi; bz0 = d.gz * 16; bz1 = bz0 + depth - 1; }
                    case 2 -> { bx0 = d.gx * 16 + lo; bx1 = d.gx * 16 + hi; bz1 = d.gz * 16 + 15; bz0 = bz1 - depth + 1; }
                    case 1 -> { bz0 = d.gz * 16 + lo; bz1 = d.gz * 16 + hi; bx1 = d.gx * 16 + 15; bx0 = bx1 - depth + 1; }
                    default -> { bz0 = d.gz * 16 + lo; bz1 = d.gz * 16 + hi; bx0 = d.gx * 16; bx1 = bx0 + depth - 1; }
                }
                int y = W + d.y;
                s.carve(bx0, y, bz0, bx1, y + 3, bz1, (x, yy, z) -> Sculpt.box(x, yy, z, bx0, y, bz0, bx1 + 1, y + RoomGeometry.DOOR_HEIGHT, bz1 + 1));
                s.putOpen((bx0 + bx1) / 2, y + 1, (bz0 + bz1) / 2, light);
            }
        }

        /** A pillar of stacked bones from the floor to the vault, a ring of skulls round its middle. */
        void pillar(double cx, double cz, int top, double pr) {
            int e = (int) Math.ceil(pr) + 1;
            s.op((int) cx - e, W, (int) cz - e, (int) cx + e, top, (int) cz + e, (b, x, y, z) -> {
                if (Math.hypot(x + 0.5 - cx, z + 0.5 - cz) < pr) b.set(x, y, z, boneY);
            });
        }

        /** A heap of bones in a corner (two blocks high at its heart, one round it), skulls on top. */
        void heap(int hx, int hz) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = hx + dx, z = hz + dz;
                    boolean core = dx == 0 && dz == 0;
                    s.put(x, W, z, (dx + dz & 1) == 0 ? boneX : boneZ);
                    if (core) s.put(x, W + 1, z, boneY);
                    else if (Hash.unit(seed, x, z) < 0.5) s.putOpen(x, W + 1, z, skulls[Hash.range(seed, z, x, 16)]);
                }
            }
            s.putOpen(hx, W + 2, hz, skulls[Hash.range(seed, hx, hz, 16)]);
        }

        /** A stone coffin of tracery, its lid draped, candles at its head and foot. */
        void sarcophagus(int x, int z, boolean alongX) {
            for (int d = -1; d <= 1; d++) {
                int sx = alongX ? x + d : x, sz = alongX ? z : z + d;
                s.put(sx, W, sz, trim);
                s.putOpen(sx, W + 1, sz, d == 0 ? worn : trim);
            }
            int hx = alongX ? x - 2 : x, hz = alongX ? z : z - 2, fx = alongX ? x + 2 : x, fz = alongX ? z : z + 2;
            s.putOpen(hx, W, hz, BellPieces.candles(candle, 3, lit(hx, hz)));
            s.putOpen(fx, W, fz, BellPieces.candles(candle, 2, lit(fx, fz)));
        }

        /** The arrival: a stair climbing away into the dark you came down, broken off where the rock came in. */
        void stair(Room r, int back) {
            int dx = GatePainter.DX[back], dz = GatePainter.DZ[back];
            double cx = r.centerX(), cz = r.centerZ();
            // through the wall and up three wide: eight steps into the dark when the rock behind is free, else two
            int steps = p.b.at(r.gx + dx, r.gz + dz) < 0 ? 8 : 2;
            double sx = dz != 0 ? 1.5 : 0.5, sz = dx != 0 ? 1.5 : 0.5;
            for (int i = 0; i <= steps; i++) {
                double px = cx + dx * (4.5 + i), pz = cz + dz * (4.5 + i);
                int step = i;
                s.carve((int) (px - 2), W + step, (int) (pz - 2), (int) (px + 2), W + step + 5, (int) (pz + 2),
                        (x, y, z) -> Sculpt.box(x, y, z, px - sx, W + step, pz - sz, px + sx, W + step + 4.5, pz + sz));
            }
            // fallen rock where it broke off
            int ex = (int) Math.floor(cx + dx * (5.5 + steps)), ez = (int) Math.floor(cz + dz * (5.5 + steps));
            s.box(ex - 2, W + steps, ez - 2, ex + 2, W + steps + 5, ez + 2, crumbling);
            for (int sgn : new int[]{-1, 1}) {
                int x = (int) Math.floor(cx + dx * 4 + (dz != 0 ? sgn * 2.5 : 0)), z = (int) Math.floor(cz + dz * 4 + (dx != 0 ? sgn * 2.5 : 0));
                s.putOpen(x, W, z, BellPieces.candles(candle, 4, true));
            }
            lights.add(new FloorAir.Light((int) (cx + dx * 8), 6, (int) (cz + dz * 8), 0x6f7fa0, 6f, 0.4f));
        }

        /** The ringers' tomb: a raised sarcophagus against the far wall, vestments hung either side, candles all round. */
        void tomb(Room r, int back) {
            int dx = GatePainter.DX[back], dz = GatePainter.DZ[back];
            int cx = r.centerX(), cz = r.centerZ();
            int tx = cx + dx * 4, tz = cz + dz * 4;
            boolean alongX = dz != 0;
            for (int a = -2; a <= 2; a++) {
                for (int b2 = -1; b2 <= 1; b2++) s.put(alongX ? tx + a : tx + b2, W, alongX ? tz + b2 : tz + a, worn);
            }
            sarcophagus(tx, tz, alongX);
            for (int a : new int[]{-3, 3}) {
                int x = alongX ? tx + a : tx + dx, z = alongX ? tz + dz : tz + a;
                s.putOpen(x, W + 1, z, BellPieces.candles(candle, 4, true));
            }
            lights.add(new FloorAir.Light(tx, 2, tz, 0xffc070, 9f, 0.8f));
        }

        /** The charnel well: a round shaft into black water, a lamp on a chain inside it, a rope down for anyone who falls. */
        void well(Room r) {
            double cx = r.centerX(), cz = r.centerZ();
            int top = W + 7;
            s.carve((int) cx - 3, W - 12, (int) cz - 3, (int) cx + 3, W, (int) cz + 3, (x, y, z) -> Sculpt.cylY(x, y, z, cx, cz, 2.5, W - 12, W + 1));
            s.fillOpen((int) cx - 3, W - 12, (int) cz - 3, (int) cx + 3, W - 11, (int) cz + 3, (x, y, z) -> Math.hypot(x - cx, z - cz) - 2.6, water);
            // the rim: a ring of bone a block high, broken where the rope comes up
            s.op((int) cx - 4, W, (int) cz - 4, (int) cx + 4, W, (int) cz + 4, (b, x, y, z) -> {
                double d = Math.hypot(x + 0.5 - cx, z + 0.5 - cz);
                if (d >= 2.5 && d < 3.5 && b.open(x, y, z) && !(z == (int) cz && x > cx)) b.set(x, y, z, boneY);
            });
            int rx = (int) cx + 1, rz = (int) cz;
            s.box(rx, W - 10, rz, rx, W - 1, rz, rope);
            s.box((int) cx, W - 4, (int) cz, (int) cx, top, (int) cz, chain0);
            s.put((int) cx, W - 5, (int) cz, soulLamp);
            lights.add(new FloorAir.Light((int) cx, -5, (int) cz, 0x7fc8ff, 8f, 0.8f));
        }

        /** The Bone Rotunda: a round domed hall, a pillar of skulls at its heart, two skull bands round its walls. */
        void rotunda(Room r) {
            Shape sh = Shape.of(r, true);
            int x0 = r.gx * 16, z0 = r.gz * 16, x1 = x0 + r.gw * 16 - 1, z1 = z0 + r.gh * 16 - 1;
            double dome = 9;
            s.carve(x0, W, z0, x1, W + 9 + (int) dome, z1, (x, y, z) -> {
                double q = sh.q(x, z), h = y - W;
                double ceil = 8 + dome * Math.sqrt(Math.max(0, 1 - q * q));
                return Math.max(sh.d(x, z), Math.max(-h, h - ceil));
            });
            Wall wa = (x, z) -> {
                double[] n = sh.normal(x, z);
                return new double[]{sh.d(x, z), n[0], n[1]};
            };
            doorways(r, 8);
            bones(x0, z0, x1, z1, wa, 2);
            glow(x0, z0, x1, z1, (x, z) -> {
                double rr = sh.r(x, z);
                return new double[]{rr < 4.5 ? 1 : sh.d(x, z), 0, 0};
            });
            double cx = sh.cx(), cz = sh.cz();
            int top = W + 17;
            pillar(cx, cz, top, 2.6);
            // skulls ringed round the pillar at three heights, candles on a bone step at its foot
            s.op((int) cx - 5, W, (int) cz - 5, (int) cx + 5, top, (int) cz + 5, (b, x, y, z) -> {
                double dx = x + 0.5 - cx, dz = z + 0.5 - cz, d = Math.hypot(dx, dz);
                int h = y - W;
                if (d < 2.6 || d >= 3.7 || !b.open(x, y, z)) return;
                if (h == 0) b.set(x, y, z, boneY);
                else if (h == 1) b.set(x, y, z, BellPieces.candles(candle, 2 + Hash.range(seed, x, z, 3), lit(x, z)));
                else if (h == 4 || h == 7 || h == 10) b.set(x, y, z, skulls[rotation(dx, dz)]);
            });
            // niches with a light in them round the wall above the skull bands
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI / 6;
                int x = (int) Math.floor(cx + Math.cos(a) * (sh.a() + 0.6)), z = (int) Math.floor(cz + Math.sin(a) * (sh.a() + 0.6));
                Direction in = Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? (Math.cos(a) > 0 ? Direction.WEST : Direction.EAST)
                        : (Math.sin(a) > 0 ? Direction.NORTH : Direction.SOUTH);
                s.put(x, W + 9, z, WorldMats.facing(niche, in));
            }
            s.box((int) cx, top + 1, (int) cz, (int) cx, W + 18, (int) cz, boneY);
            // a handbell on the floor beyond the candles at the pillar's foot
            p.handbell(handbell, W, (int) Math.floor(cx + 4.6), 0, (int) Math.floor(cz), Direction.EAST);
            lights.add(new FloorAir.Light((int) cx, 3, (int) cz, 0xffb060, 14f, 0.8f));
            lights.add(new FloorAir.Light((int) cx, 9, (int) cz, 0x9fd6ff, 12f, 0.4f));
        }

        /** The Lantern Row: a long barrel-vaulted gallery of burial shelves, soul lamps hung down its middle. */
        void row(Room r) {
            boolean alongZ = r.gh >= r.gw;
            double cx = r.centerX(), cz = r.centerZ();
            double half = (alongZ ? r.gw : r.gh) * 8 - 3, len = (alongZ ? r.gh : r.gw) * 8 - INSET;
            int x0 = r.gx * 16, z0 = r.gz * 16, x1 = x0 + r.gw * 16 - 1, z1 = z0 + r.gh * 16 - 1;
            s.carve(x0, W, z0, x1, W + 9, z1, (x, y, z) -> {
                double lat = alongZ ? x - cx : z - cz, lon = alongZ ? z - cz : x - cx, h = y - W;
                double ceil = 4 + Math.sqrt(Math.max(0, half * half - lat * lat)) * 0.8;
                return Math.max(Math.max(Math.abs(lat) - half, Math.abs(lon) - len), Math.max(-h, h - ceil));
            });
            Wall wa = (x, z) -> {
                double lat = alongZ ? x - cx : z - cz, lon = alongZ ? z - cz : x - cx;
                double dl = Math.abs(lat) - half, dlon = Math.abs(lon) - len;
                if (dl > dlon) return new double[]{dl, alongZ ? Math.signum(lat) : 0, alongZ ? 0 : Math.signum(lat)};
                return new double[]{dlon, alongZ ? 0 : Math.signum(lon), alongZ ? Math.signum(lon) : 0};
            };
            bones(x0, z0, x1, z1, wa, 1);
            glow(x0, z0, x1, z1, wa);
            // the lamps: one every four blocks down the middle, on chains from the vault
            int n = (int) (len * 2 / 4);
            for (int i = 0; i < n; i++) {
                double lon = -len + 2 + i * 4;
                int x = (int) Math.floor(alongZ ? cx : cx + lon), z = (int) Math.floor(alongZ ? cz + lon : cz);
                s.box(x, W + 6, z, x, W + 8, z, chain0);
                s.put(x, W + 5, z, i % 3 == 1 ? lamp : soulLamp);
                if (i % 2 == 0) lights.add(new FloorAir.Light(x, 5, z, i % 3 == 1 ? 0xffb060 : 0x7fc8ff, 7f, 0.6f));
            }
        }

        /** A winding passage through a link's slot from one doorway to the other, bone-lined, candles in its walls. */
        void passage(Room r, ChainLayout.Link l) {
            int ox = r.gx * 16, oz = r.gz * 16;
            ChainLayout.Way way = ChainLayout.Way.of(r, l, seed);
            Wall wa = (x, z) -> {
                double[] w = way.at(x, z);
                return w == null ? null : new double[]{Math.abs(w[0]) - HW, w[2], w[3]};
            };
            s.carve(ox, W, oz, ox + 15, W + 5, oz + 15, (x, y, z) -> {
                double[] dn = wa.at(x, z);
                if (dn == null) return Math.max(Math.max(Math.max(ox - x, x - ox - 16), Math.max(oz - z, z - oz - 16)), 0.5);
                double h = y - W, lat = dn[0] + HW;
                double ceil = 3.2 + 1.6 * Math.sqrt(Math.max(0, 1 - (lat / HW) * (lat / HW)));
                return Math.max(dn[0], Math.max(-h, h - ceil));
            });
            bones(ox, oz, ox + 15, oz + 15, wa, 1);
            // hidden light every four blocks down its middle
            s.op(ox, W + 1, oz, ox + 15, W + 1, oz + 15, (b, x, y, z) -> {
                double[] w = way.at(x + 0.5, z + 0.5);
                if (w == null || Math.abs(w[0]) > 1.1 || !b.open(x, y, z)) return;
                if (Math.floorMod((int) Math.round(w[1]), 4) == 2) b.set(x, y, z, light);
            });
            if (Hash.unit(seed, r.id, 101) < 0.5) lights.add(new FloorAir.Light(ox + 8, 2, oz + 8, 0xffa850, 5f, 0.5f));
        }

        // ---- what the floor walks on and how its air glows ----------------------------------------------------------

        boolean walk(Room r, int x, int z) {
            Kind k = kinds.get(r.id);
            if (k == null) return false;
            double px = x + 0.5, pz = z + 0.5;
            return switch (k) {
                case ARRIVAL, CRYPT, TOMB -> {
                    Shape sh = Shape.of(r, false);
                    if (sh.d(px, pz) > -1.6) yield false;
                    double dx = px - sh.cx(), dz = pz - sh.cz();
                    if (k == Kind.TOMB) {
                        int back = back(r);
                        double toward = dx * GatePainter.DX[back] + dz * GatePainter.DZ[back];
                        yield toward < 1.5;
                    }
                    if (k == Kind.CRYPT && r.gw == 1 && r.gh == 1) {
                        boolean heaps = (Hash.of(seed, r.id, 83) & 1) == 0;
                        if (heaps) yield Math.abs(Math.abs(dx) - 4) > 2 || Math.abs(Math.abs(dz) - 4) > 2;
                        yield Math.abs(dx) > 3 || Math.abs(dz) > 3;
                    }
                    if (k == Kind.CRYPT) yield Math.abs(Math.abs(dx) - 7) > 2.5 || Math.abs(Math.abs(dz) - 7) > 2.5;
                    yield true;
                }
                case WELL -> Shape.of(r, false).d(px, pz) < -1.6 && Math.hypot(px - r.centerX(), pz - r.centerZ()) > 4.5;
                case ROTUNDA -> {
                    Shape sh = Shape.of(r, true);
                    yield sh.d(px, pz) < -1.6 && sh.r(px, pz) > 4.5;
                }
                case ROW -> {
                    boolean alongZ = r.gh >= r.gw;
                    double lat = alongZ ? px - r.centerX() : pz - r.centerZ(), lon = alongZ ? pz - r.centerZ() : px - r.centerX();
                    double half = (alongZ ? r.gw : r.gh) * 8 - 3, len = (alongZ ? r.gh : r.gw) * 8 - INSET;
                    yield Math.abs(lat) < half - 1.5 && Math.abs(lon) < len - 1.5;
                }
                default -> false;
            };
        }

        FloorAir air() {
            List<FloorAir.Light> all = lights.size() > 96 ? lights.subList(0, 96) : lights;
            List<FloorAir.Mist> mists = List.of(new FloorAir.Mist(0, 2, 0x4c5048, mood.equals("requiem") ? 0.6f : 0.45f),
                    new FloorAir.Mist(6, 6, 0x2c2e30, 0.2f));
            List<FloorAir.Water> waters = new ArrayList<>();
            if (well >= 0) {
                Room r = p.world(well);
                waters.add(new FloorAir.Water(-11, r.centerX() - 2, r.centerZ() - 2, r.centerX() + 2, r.centerZ() + 2));
            }
            return new FloorAir(new ArrayList<>(all), List.of(), mists, waters);
        }
    }
}
