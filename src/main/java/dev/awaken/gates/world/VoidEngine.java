package dev.awaken.gates.world;

import dev.awaken.gates.worlds.GateWorld;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The void world (SPEC §38.6, "The Field of Bells"): black water with no shore and no roof, and bells hanging over it
 * from nothing, a lamp alight in each. The programme's rooms are islands at the water's surface strung on causeways
 * ({@link ChainLayout}) edged with floating candles; a stair comes down out of the dark to the first island, and the Still
 * Bell, the biggest of them, hangs over the last. The water is deep but its islands and causeways sit level with it, so
 * anyone who falls in swims to the nearest edge and climbs out.
 */
final class VoidEngine implements WorldEngine {
    static final int BASE = 40, DEEP = 7, SKY = 46;
    /** Causeway half-width (3 blocks to walk), island plan inset from its slots. */
    static final double HW = 1.6;

    enum Kind { ARRIVAL, ISLAND, END, LINK, BUILT }

    @Override
    public FloorLayout plan(Ctx c) {
        return new Plan(c).run();
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
        final Map<Integer, PassageEngine.Shape> shapes = new HashMap<>();
        final BlockState wall, worn, floor, wet, trim, bronze, candle, lily, water, chain0, lamp, light, dark, handbell;
        final List<FloorAir.Light> lights = new ArrayList<>();
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
            floor = m.get("floor", "awaken:crypt_flagstone");
            wet = m.get("floor_wet", "awaken:crypt_flagstone_wet");
            trim = m.get("trim", "awaken:crypt_tracery");
            bronze = m.get("bronze", "awaken:crypt_patina");
            candle = m.get("candles", "awaken:crypt_candles");
            lily = m.get("lily", "awaken:crypt_lily_candle");
            water = m.get("water", "minecraft:water");
            chain0 = m.get("chain", "minecraft:iron_chain");
            lamp = m.get("lamp", "minecraft:soul_lantern[hanging=true]");
            dark = m.get("void", "minecraft:black_concrete");
            handbell = m.get("handbell", "awaken:crypt_handbell");
            light = WorldMats.light(12);
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
            for (int w : chain.links.keySet()) kinds.put(w, Kind.LINK);
            for (int w : chain.places) {
                Room r = p.world(w);
                boolean built = false;
                for (int i = 0; i < p.host.length; i++) built |= p.host[i] == w && WorldPlan.built(p.room(i));
                Kind k = built ? Kind.BUILT : w == p.host[p.logic.start] ? Kind.ARRIVAL : w == p.host[p.logic.end] ? Kind.END : Kind.ISLAND;
                kinds.put(w, k);
                shapes.put(w, PassageEngine.Shape.of(r, r.gw == r.gh));
                if (!built) r.height = SKY;
            }
            for (int w : chain.places) {
                Room r = p.world(w);
                if (kinds.get(w) == Kind.ARRIVAL) {
                    int back = r.doors.isEmpty() ? 0 : (r.doors.get(0).side + 2) % 4;
                    p.anchor(w, r.centerX() + GatePainter.DX[back] * 2, r.centerZ() + GatePainter.DZ[back] * 2);
                }
            }
            sculpt();
            String biome = world == null ? "" : world.biomeFor(mood);
            FloorLayout f = p.finish(this::walk, new WorldPlan.Look(W, biome, air(), new WorldPlan.SculptModel(s), Float.NaN));
            if (f == null) return WorldEngine.fail("the programme didn't carry over: " + WorldEngine.WHY.get());
            for (var e : kinds.entrySet()) {
                if (e.getValue() == Kind.BUILT && !WorldPlan.built(f.room(e.getKey()))) return WorldEngine.fail("a Rig room didn't rebuild");
            }
            return f;
        }

        int[] size(int id) {
            Room L = p.room(id);
            if (WorldPlan.built(L)) return new int[]{L.gw, L.gh};
            if (id == p.logic.start) return new int[]{1, 1};
            if (id == p.logic.end) return new int[]{2, 2};
            return switch (L.type) {
                case HUB, ARENA, CAVERN -> new int[]{2, 2};
                case HALL -> new int[]{1, 2};
                default -> new int[]{1, 1};
            };
        }

        // ---- the carve ----------------------------------------------------------------------------------------------

        void sculpt() {
            s = new Sculpt(this::skin, c.plot().minY(), c.plot().maxY());
            // the field itself: open from the deep water's bed to far overhead, the whole plot but its margin
            int x0 = 8, x1 = 247;
            s.carve(x0, W - DEEP + 1, x0, x1, W + SKY, x1,
                    (x, y, z) -> Sculpt.box(x, y, z, x0, W - DEEP + 1, x0, x1 + 1, W + SKY + 1, x1 + 1));
            s.fillOpen(x0, W - DEEP + 1, x0, x1, W - 1, x1, (x, y, z) -> -1, water);
            for (var e : kinds.entrySet()) {
                Room r = p.world(e.getKey());
                switch (e.getValue()) {
                    case LINK -> causeway(r, chain.links.get(e.getKey()));
                    // a Rig room stands in the water as a drowned chamber: a dark plinth under it and a dam round its
                    // slots hold the water back from the band its painter fills; the dam stands two above the water, so
                    // nobody walks its rim (its doorway is cut through it)
                    case BUILT -> s.box(r.gx * 16 - 1, W - DEEP, r.gz * 16 - 1, (r.gx + r.gw) * 16, W + 1, (r.gz + r.gh) * 16, dark);
                    default -> island(r, e.getValue());
                }
            }
            p.thresholds(s, W, floor, trim);
            p.doorLights(s, W, light);
            bells();
            if (hero("the_still_bell")) stillBell();
            p.screens(s, W, (lat, rise, side) -> {
                // a sealed island stands in a fence of bars rising out of the water, open to the sky above
                if (rise > 6) return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
                if (rise < 1 || rise == 6 || lat % 5 == 0) return trim;
                return GatePainter.state(side == 0 || side == 2 ? "minecraft:iron_bars[east=true,west=true]" : "minecraft:iron_bars[north=true,south=true]");
            });
            p.handbells(s);
            s.seal();
        }

        /** The field's edges and roof are black, so it has none; only the deep bed under the water is stone. */
        BlockState skin(int x, int y, int z, boolean fl, boolean ceil) {
            return fl && y < W ? wet : dark;
        }

        /** An island: a ragged slab of stone from the deep bed to the surface, its top a floor of flagstones. */
        void island(Room r, Kind k) {
            PassageEngine.Shape sh = shapes.get(r.id);
            int x0 = r.gx * 16, z0 = r.gz * 16, x1 = x0 + r.gw * 16 - 1, z1 = z0 + r.gh * 16 - 1;
            s.op(x0, W - DEEP, z0, x1, W - 1, z1, (b, x, y, z) -> {
                double d = sh.d(x + 0.5, z + 0.5) + (Hash.noise3(seed, x, 0, z, 2.5) - 0.5) * 1.6;
                // the sides lean in as they go down, so the deep water shows under the edges
                double under = (W - 1 - y) * 0.35;
                if (d + under < 0) b.set(x, y, z, y == W - 1 ? (Hash.noise3(seed, x, 1, z, 3) < 0.35 ? wet : floor) : wall);
            });
            // a landing from each doorway well onto the island, whatever its ragged edge does
            for (Room.Door d : r.doors) {
                int lo = RoomGeometry.DOOR_LO, hi = RoomGeometry.DOOR_HI;
                int bx0, bx1, bz0, bz1;
                switch (d.side) {
                    case 0 -> { bx0 = d.gx * 16 + lo; bx1 = d.gx * 16 + hi; bz0 = d.gz * 16; bz1 = bz0 + 7; }
                    case 2 -> { bx0 = d.gx * 16 + lo; bx1 = d.gx * 16 + hi; bz1 = d.gz * 16 + 15; bz0 = bz1 - 7; }
                    case 1 -> { bz0 = d.gz * 16 + lo; bz1 = d.gz * 16 + hi; bx1 = d.gx * 16 + 15; bx0 = bx1 - 7; }
                    default -> { bz0 = d.gz * 16 + lo; bz1 = d.gz * 16 + hi; bx0 = d.gx * 16; bx1 = bx0 + 7; }
                }
                s.box(bx0, W - 2, bz0, bx1, W - 2, bz1, wall);
                s.box(bx0, W - 1, bz0, bx1, W - 1, bz1, floor);
                s.putOpen((bx0 + bx1) / 2, W + 1, (bz0 + bz1) / 2, light);
            }
            // hidden light four apart over the island, candles at its rim
            int mx = (x0 + x1 + 1) / 2, mz = (z0 + z1 + 1) / 2;
            s.op(x0, W + 1, z0, x1, W + 1, z1, (b, x, y, z) -> {
                if (Math.floorMod(x - mx, 4) != 0 || Math.floorMod(z - mz, 4) != 0) return;
                if (sh.d(x + 0.5, z + 0.5) < 0) b.set(x, y, z, light);
            });
            // the island's handbell first, so the rim's candles step round it
            if (k != Kind.ARRIVAL) rimBell(r, sh, k == Kind.END);
            int n = 6 + r.gw * 4;
            for (int i = 0; i < n; i++) {
                double a = (i + Hash.unit(seed, r.id, i)) * 2 * Math.PI / n;
                double rr = Math.min(sh.a(), sh.b()) - 1.6;
                int x = (int) Math.floor(sh.cx() + Math.cos(a) * rr * sh.a() / Math.min(sh.a(), sh.b()));
                int z = (int) Math.floor(sh.cz() + Math.sin(a) * rr * sh.b() / Math.min(sh.a(), sh.b()));
                if (nearDoor(r, x, z)) continue;
                s.putOpen(x, W, z, BellPieces.candles(candle, 1 + Hash.range(seed, x, z, 4), lit(x, z)));
            }
            lights.add(new FloorAir.Light((int) sh.cx(), 1, (int) sh.cz(), 0xffb060, 6f + r.gw * 3, 0.5f));
            if (k == Kind.ARRIVAL) stair(r);
            else if (k != Kind.END) {
                // a bell hung low over the island, its lamp lit: the dead gather under it
                double bx = sh.cx() + (Hash.unit(seed, r.id, 3) - 0.5) * 4, bz = sh.cz() + (Hash.unit(seed, r.id, 5) - 0.5) * 4;
                double R = 1.8 + r.gw * 0.9;
                BellPieces.bell(s, Math.floor(bx) + 0.5, W + 6 + r.gw * 2, Math.floor(bz) + 0.5, R, (int) (R * 2.2), 0, bronze, lamp, chain0, false);
                p.bell(Math.floor(bx) + 0.5, 6 + r.gw * 2, Math.floor(bz) + 0.5, R, (int) (R * 2.2));
                lights.add(new FloorAir.Light((int) bx, 6 + r.gw * 2, (int) bz, 0x8fd0ff, 8f, 0.7f));
            }
        }

        /**
         * A handbell standing near an island's rim, clear of its doorways. The last island's is the silent one: struck,
         * it makes no sound, and the Still Bell answers it (SPEC §38.8).
         */
        void rimBell(Room r, PassageEngine.Shape sh, boolean last) {
            double rr = Math.min(sh.a(), sh.b()) - 2.4, a0 = Hash.unit(seed, r.id, 17) * 2 * Math.PI;
            for (int i = 0; i < 12; i++) {
                double a = a0 + i * Math.PI / 6;
                int x = (int) Math.floor(sh.cx() + Math.cos(a) * rr * sh.a() / Math.min(sh.a(), sh.b()));
                int z = (int) Math.floor(sh.cz() + Math.sin(a) * rr * sh.b() / Math.min(sh.a(), sh.b()));
                if (nearDoor(r, x, z)) continue;
                p.handbell(handbell, W, x, 0, z, Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? Direction.EAST : Direction.NORTH,
                        last ? "silent_bell" : "", null);
                return;
            }
        }

        boolean nearDoor(Room r, int x, int z) {
            for (Room.Door d : r.doors) if (Math.abs(x - d.centerX()) + Math.abs(z - d.centerZ()) < 7) return true;
            return false;
        }

        boolean lit(int x, int z) {
            double chance = switch (mood) {
                case "requiem" -> 1.0;
                case "still" -> 0.5;
                default -> 0.75;
            };
            return Hash.unit(seed, x * 7 + 3, z) < chance;
        }

        /** The arrival: a stair of floating slabs coming down out of the dark to the first island. */
        void stair(Room r) {
            int back = r.doors.isEmpty() ? 0 : (r.doors.get(0).side + 2) % 4;
            int dx = GatePainter.DX[back], dz = GatePainter.DZ[back];
            double cx = r.centerX(), cz = r.centerZ();
            for (int i = 0; i < 14; i++) {
                double px = cx + dx * (5 + i), pz = cz + dz * (5 + i);
                int y = W - 1 + i;
                for (int w = -1; w <= 1; w++) {
                    int x = (int) Math.floor(px + (dz != 0 ? w : 0)), z = (int) Math.floor(pz + (dx != 0 ? w : 0));
                    s.put(x, y, z, i % 4 == 3 ? trim : worn);
                }
            }
            lights.add(new FloorAir.Light((int) (cx + dx * 10), 6, (int) (cz + dz * 10), 0x8090b0, 8f, 0.4f));
        }

        /** A causeway at the water's surface, winding from doorway to doorway, floating candles along its edges. */
        void causeway(Room r, ChainLayout.Link l) {
            int ox = r.gx * 16, oz = r.gz * 16;
            ChainLayout.Way way = ChainLayout.Way.of(r, l, seed);
            s.op(ox, W - 2, oz, ox + 15, W - 1, oz + 15, (b, x, y, z) -> {
                double[] w = way.at(x + 0.5, z + 0.5);
                if (w == null) return;
                double lat = Math.abs(w[0]);
                if (lat < HW) b.set(x, y, z, y == W - 1 ? (Hash.unit(seed, x, z) < 0.3 ? wet : floor) : wall);
                else if (lat < HW + 1 && y == W - 1 && Hash.unit(seed, x * 3, z) < 0.35) b.set(x, y, z, worn);
            });
            s.op(ox, W, oz, ox + 15, W + 1, oz + 15, (b, x, y, z) -> {
                double[] w = way.at(x + 0.5, z + 0.5);
                if (w == null || !b.open(x, y, z)) return;
                double lat = Math.abs(w[0]);
                int along = (int) Math.round(w[1]);
                if (y == W + 1 && lat < 1.1 && Math.floorMod(along, 4) == 2) b.set(x, y, z, light);
                // lilies on the water either side, every third block
                if (y == W && lat >= HW + 0.6 && lat < HW + 1.6 && Math.floorMod(along, 3) == 0 && b.water(x, y - 1, z)) b.set(x, y, z, lily);
            });
        }

        /** The field's bells: hung from nothing over the open water, a lamp lit in each, some sunk to their shoulders. */
        void bells() {
            if (!hero("hung_bells")) return;
            for (int gx = 1; gx <= 14; gx++) {
                for (int gz = 1; gz <= 14; gz++) {
                    if (p.b.at(gx, gz) >= 0 || Hash.unit(seed, gx * 17 + gz, 211) < 0.3) continue;
                    double R = 1.6 + Hash.unit(seed, gx, gz * 3) * 3.2;
                    int H = (int) Math.round(R * 2.2);
                    // a few sunk to their shoulders, in the middle of their slot so they never reach a way
                    boolean sunk = R >= 2.8 && Hash.unit(seed, gx * 5, gz) < 0.25;
                    double bx = gx * 16 + (sunk ? 8 : 4 + Math.floor(Hash.unit(seed, gx, gz) * 8) + 0.5);
                    double bz = gz * 16 + (sunk ? 8 : 4 + Math.floor(Hash.unit(seed, gz, gx) * 8) + 0.5);
                    int lip = W - 1 + (sunk ? -H / 2 : 3 + Hash.range(seed, gx, gz * 7, 26));
                    BellPieces.bell(s, bx, lip, bz, R, H, 0, bronze, lamp, chain0, false);
                    p.bell(bx, lip - W, bz, R, H);
                    if (lights.size() < 90 && lip > W) lights.add(new FloorAir.Light((int) bx, lip - W + H - 2, (int) bz, 0x8fd0ff, 6f + (float) R, 0.6f));
                }
            }
        }

        /** The Still Bell over the last island: the biggest of them, hanging low, its clapper still. */
        void stillBell() {
            int w = p.host[p.logic.end];
            PassageEngine.Shape sh = shapes.get(w);
            if (sh == null) return;
            BellPieces.bell(s, sh.cx(), W + 7, sh.cz(), 8.5, 18, 0, bronze, lamp, chain0, true);
            p.bell("still", sh.cx(), 7, sh.cz(), 8.5, 18);
            lights.add(new FloorAir.Light((int) sh.cx(), 20, (int) sh.cz(), 0xffd890, 18f, 0.9f));
        }

        // ---- what the floor walks on and how its air glows ----------------------------------------------------------

        boolean walk(Room r, int x, int z) {
            Kind k = kinds.get(r.id);
            if (k == null || k == Kind.LINK || k == Kind.BUILT) return false;
            PassageEngine.Shape sh = shapes.get(r.id);
            double px = x + 0.5, pz = z + 0.5;
            return sh.d(px, pz) < -2.6;
        }

        FloorAir air() {
            List<FloorAir.Light> all = lights.size() > 96 ? lights.subList(0, 96) : lights;
            List<FloorAir.Mist> mists = List.of(new FloorAir.Mist(0, 2, 0x26304a, mood.equals("storm") ? 0.35f : 0.55f),
                    new FloorAir.Mist(18, 14, 0x141a2a, 0.25f));
            List<FloorAir.Water> waters = List.of(new FloorAir.Water(-1, 8, 8, 247, 247));
            return new FloorAir(new ArrayList<>(all), List.of(), mists, waters);
        }
    }
}
