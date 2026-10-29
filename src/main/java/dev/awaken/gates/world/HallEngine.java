package dev.awaken.gates.world;

import dev.awaken.gates.worlds.GateWorld;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The hall world (SPEC §38.6, "The Sunken Belfry"): a church the sea took. The floor's programme is hosted down one
 * nave: the porch you arrive in, flooded bays with a causeway of candles, the crossing under the Great Bell, the chancel
 * and its rose window at the far end; side rooms are chapels off the aisles, and deeper ones are crypts. Aisles carry
 * galleries, lancets let the moon in, a bell with a lamp inside hangs in every bay, and an odd rope in the Rope Shaft
 * climbs to a secret world. The Lord's arena and Rig rooms stand in the nave's line as built rooms. Where the world has
 * the Flood (SPEC §38.8) the tide is out when you arrive: the bays are pits three deep with shallow water at their foot and
 * a stair out mid-bay, and the Flood fills them back to the causeway's brim.
 */
final class HallEngine implements WorldEngine {
    /** The nave's axis: the middle of slot column 7 (floor-local x 120); the nave and aisles fill columns 6..8. */
    static final double AXIS = 120.0;
    // the cross-section, as lateral distances from the axis (block centres sit on halves)
    static final double CAUSE = 2.5, NAVE = 10.5, ARCADE = 12.5, PIER = 14.5, AISLE = 20.5;
    static final int SPRING = 30, RISE = 14, GALLERY = 17, BASE = 30, FIRST_ROW = 1, LAST_ROW = 14;
    static final int ROPES_H = 56, REST_UP = 50, REST_H = 8;
    /** Rose window centre height and radius. */
    static final double ROSE_Y = 29.5, ROSE_R = 11.5;
    /** The water's height at low tide (the Flood raises it to -1, the causeway's brim). */
    static final int LOW = -3;

    enum Kind { NARTHEX, BAY, CHANCEL, CHAPEL, CRYPT, ROPES, REST, BUILT }

    private static final byte O = 0, S = 1, WTR = 2, TRIM = 3, GLASS = 4;

    @Override
    public FloorLayout plan(Ctx c) {
        return new Plan(c).run();
    }

    static double vault(double aa) {
        return Sculpt.pointedArch(aa, ARCADE, SPRING, RISE);
    }

    private static final class Plan {
        final Ctx c;
        final WorldPlan p;
        final GateWorld world;
        final int W;
        final long seed;
        final String mood;
        final Map<Integer, Kind> kinds = new HashMap<>();
        /** The spine's places in order (world room ids). */
        final List<Integer> nave = new ArrayList<>();
        /** Per slot row: the hall place standing there (-1 none) and its run (a stretch of hall places between built rooms). */
        final int[] rowPlace = new int[16], rowRun = new int[16];
        final List<int[]> runs = new ArrayList<>();
        int ropes = -1, rest = -1, greatBell = -1, choir = -1;
        final BlockState wall, worn, crumbling, floor, wet, trim, bronze, candle, glass, rose, niche, vestment, rope, lily, water, chain, lamp,
                light, grille, grilleZ, leaves, handbell;
        /** The world has the Flood: the bays start as low-tide pits (lilies and water two below the brim). */
        final boolean flood;
        final List<FloorAir.Light> lights = new ArrayList<>(), minor = new ArrayList<>();
        final List<FloorAir.Shaft> shafts = new ArrayList<>();
        Sculpt s;
        /** The Bell Crypt's wear (content 7), null before. */
        CryptWear wear;

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
            glass = m.get("glass", "awaken:crypt_moon_glass");
            rose = m.get("rose", "awaken:crypt_rose_glass");
            niche = m.get("niche", "awaken:crypt_niche");
            vestment = m.get("vestment", "awaken:crypt_vestment");
            rope = m.get("rope", "awaken:crypt_bell_rope");
            lily = m.get("lily", "awaken:crypt_lily_candle");
            water = m.get("water", "minecraft:water");
            chain = m.get("chain", "minecraft:iron_chain");
            lamp = m.get("lamp", "minecraft:soul_lantern[hanging=true]");
            leaves = m.get("door", "minecraft:dark_oak_planks");
            handbell = m.get("handbell", "awaken:crypt_handbell");
            flood = world != null && world.dressing().events().contains("flood");
            light = WorldMats.light(10);
            grille = GatePainter.state("minecraft:iron_bars[east=true,west=true]");
            grilleZ = GatePainter.state("minecraft:iron_bars[north=true,south=true]");
            java.util.Arrays.fill(rowPlace, -1);
            java.util.Arrays.fill(rowRun, -1);
        }

        boolean hero(String id) {
            List<String> h = world == null ? List.of() : world.dressing().hero();
            return h.isEmpty() || h.contains(id);
        }

        boolean requiem() {
            return mood.equals("requiem");
        }

        FloorLayout run() {
            if (mood.equals("tolling")) {
                // every bell ringing: the fights are louder, one more of the dead in every room that has any
                for (Room L : p.logic.rooms) if (L.type.combat() && L.normals > 0) L.normals++;
            }
            if (!spine()) return WorldEngine.fail("the spine doesn't fit the nave");
            if (!branches()) return WorldEngine.fail("no room beside a place for its side rooms");
            ropes();
            sculpt();
            if (flood) basins();
            FloorLayout f = p.finish(this::walk, new WorldPlan.Look(W, biome(), air(), new WorldPlan.SculptModel(s), 0f));
            if (f == null) return WorldEngine.fail("the programme didn't carry over: " + WorldEngine.WHY.get());
            innerGates(f);
            return f;
        }

        String biome() {
            return world == null ? "" : world.biomeFor(mood);
        }

        // ---- the plan: the spine down the nave, side rooms off the aisles -----------------------------------------

        /** How far a long spine is squeezed: 0 none, 1 arenas and the chancel to one bay, 2 the crossing too. */
        int squeeze;

        int[] size(int id) {
            Room L = p.room(id);
            if (WorldPlan.built(L)) return new int[]{L.gw, L.gh};
            if (L.type == Room.Type.START) return new int[]{3, 1};
            if (L.type == Room.Type.HUB) return new int[]{3, Math.max(1, 3 - squeeze)};
            if (L.type == Room.Type.ARENA || L.type == Room.Type.CAVERN || id == p.logic.end) return new int[]{3, squeeze > 0 ? 1 : 2};
            return new int[]{3, 1};
        }

        boolean plain(List<Integer> g) {
            for (int id : g) {
                Room L = p.room(id);
                if (WorldPlan.built(L) || id == p.logic.start || id == p.logic.end) return false;
                if (L.type != Room.Type.NORMAL && L.type != Room.Type.ELITE && L.type != Room.Type.HALL && L.type != Room.Type.SHAFT) return false;
            }
            return true;
        }

        int rows(List<List<Integer>> groups) {
            int n = 0;
            for (List<Integer> g : groups) n += size(g.get(0))[1];
            return n;
        }

        boolean spine() {
            List<List<Integer>> groups = new ArrayList<>();
            for (int id : p.spine) groups.add(new ArrayList<>(List.of(id)));
            int room = LAST_ROW - FIRST_ROW + 1;
            // a spine too long for the nave folds: big rooms take one bay, then plain rooms share a bay (their dead
            // add up), from the far end
            while (squeeze < 2 && rows(groups) > room) squeeze++;
            while (rows(groups) > room) {
                boolean merged = false;
                for (int i = groups.size() - 2; i >= 1 && !merged; i--) {
                    if (plain(groups.get(i)) && plain(groups.get(i + 1)) && groups.get(i).size() + groups.get(i + 1).size() <= 3) {
                        groups.get(i).addAll(groups.remove(i + 1));
                        merged = true;
                    }
                }
                if (!merged) return false;
            }
            // a short spine gets calm bays after the porch: the nave is long whatever the floor
            int pad = Math.max(0, Math.min(3 + c.variation() % 2, room - rows(groups)));
            pad = Math.min(pad, Math.max(0, 7 - rows(groups)));
            int gz = FIRST_ROW, prev = -1;
            for (int i = 0; i < groups.size(); i++) {
                List<Integer> g = groups.get(i);
                int id = g.get(0);
                int[] sz = size(id);
                // centred so the door in the middle of a shared edge is always on the causeway (slot column 7)
                int gx = Math.max(1, Math.min(15 - sz[0], 7 - sz[0] / 2));
                if (gz + sz[1] - 1 > LAST_ROW) return false;
                int w = p.host(id, gx, gz, sz[0], sz[1], 0);
                for (int k = 1; k < g.size(); k++) p.merge(g.get(k), id);
                Kind k = WorldPlan.built(p.room(id)) ? Kind.BUILT : i == 0 ? Kind.NARTHEX : id == p.logic.end ? Kind.CHANCEL : Kind.BAY;
                place(w, k);
                if (prev >= 0 && !p.join(prev, w)) return false;
                nave.add(w);
                prev = w;
                gz += sz[1];
                if (i == 0 && k == Kind.NARTHEX) {
                    for (int e = 0; e < pad; e++) {
                        int x = p.extra(6, gz, 3, 1, 0);
                        place(x, Kind.BAY);
                        p.join(prev, x);
                        nave.add(x);
                        prev = x;
                        gz++;
                    }
                }
            }
            // runs: stretches of hall places, the built rooms between them
            int run = -1;
            for (int w : nave) {
                Room r = p.world(w);
                if (kinds.get(w) == Kind.BUILT) {
                    run = -1;
                    continue;
                }
                if (run < 0) {
                    runs.add(new int[]{r.gz * 16, 0});
                    run = runs.size() - 1;
                }
                runs.get(run)[1] = (r.gz + r.gh) * 16 - 1;
                for (int z = r.gz; z < r.gz + r.gh; z++) {
                    rowPlace[z] = w;
                    rowRun[z] = run;
                }
            }
            return !runs.isEmpty();
        }

        void place(int w, Kind k) {
            kinds.put(w, k);
            Room r = p.world(w);
            r.height = switch (k) {
                case NARTHEX, BAY, CHANCEL -> SPRING;
                case ROPES -> ROPES_H;
                case REST -> REST_H;
                case CHAPEL, CRYPT -> 10;
                default -> r.height;
            };
            // where a place's features gather: the porch's causeway, an aisle of a bay, the chancel before its altar
            switch (k) {
                case NARTHEX -> p.anchor(w, (int) AXIS, r.gz * 16 + 3);
                case BAY -> p.anchor(w, (int) (AXIS + ((Hash.of(seed, w, 23) & 1) == 0 ? -17.5 : 17.5)), r.gz * 16 + r.gh * 8);
                case CHANCEL -> p.anchor(w, (int) AXIS, (r.gz + r.gh) * 16 - 9);
                default -> {}
            }
        }

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
                Room L = p.room(id);
                int parentW = p.host[p.parent[id]];
                boolean built = WorldPlan.built(L);
                int gw = built ? L.gw : 1, gh = built ? L.gh : 1;
                int[] at = beside(p.world(parentW), gw, gh, id);
                // its parent's flanks are full: it opens off the nearest chapel instead (it only has to be reachable)
                if (at == null) {
                    Room par = p.world(parentW);
                    List<Integer> near = new ArrayList<>(kinds.keySet());
                    near.sort((a, b) -> Double.compare(dist(p.world(a), par), dist(p.world(b), par)));
                    for (int o : near) {
                        Kind ok = kinds.get(o);
                        if (o == parentW || (ok != Kind.CHAPEL && ok != Kind.CRYPT && !isHall(ok)) || p.sealable(p.world(o))) continue;
                        at = beside(p.world(o), gw, gh, id);
                        if (at != null) {
                            parentW = o;
                            break;
                        }
                    }
                }
                if (at == null) return false;
                int w = p.host(id, at[0], at[1], gw, gh, 0);
                if (!p.join(parentW, w)) return false;
                Kind pk = kinds.get(parentW);
                place(w, built ? Kind.BUILT : pk == Kind.CHAPEL || pk == Kind.CRYPT ? Kind.CRYPT : Kind.CHAPEL);
            }
            return true;
        }

        static double dist(Room a, Room b) {
            return Math.hypot(a.gx + a.gw / 2.0 - b.gx - b.gw / 2.0, a.gz + a.gh / 2.0 - b.gz - b.gh / 2.0);
        }

        /** A free spot for a gw x gh place touching the parent: off a hall place's flanks, any side of a chapel. */
        int[] beside(Room par, int gw, int gh, int id) {
            boolean hall = isHall(kinds.get(par.id));
            int west = (int) ((Hash.of(seed, id, 31) + c.variation()) & 1);
            int[] best = null;
            double bestScore = Double.MAX_VALUE;
            for (int gx = par.gx - gw; gx <= par.gx + par.gw; gx++) {
                for (int gz = par.gz - gh; gz <= par.gz + par.gh; gz++) {
                    boolean flank = gx + gw == par.gx || gx == par.gx + par.gw;
                    boolean end = gz + gh == par.gz || gz == par.gz + par.gh;
                    boolean overZ = gz < par.gz + par.gh && gz + gh > par.gz, overX = gx < par.gx + par.gw && gx + gw > par.gx;
                    if (!(flank && overZ) && !(end && overX)) continue;
                    if (hall && !flank) continue;
                    if (gx < 1 || gz < FIRST_ROW || gx + gw > 15 || gz + gh > LAST_ROW + 1) continue;
                    if (!p.free(gx, gz, gw, gh)) continue;
                    double side = (gx < par.gx) == (west == 1) ? 0 : 0.5;
                    double score = Math.abs(gz + gh / 2.0 - (par.gz + par.gh / 2.0)) + Math.abs(gx + gw / 2.0 - (par.gx + par.gw / 2.0)) * 0.1
                            + side + Hash.unit(seed, gx * 31 + gz, id) * 0.3;
                    if (score < bestScore) {
                        bestScore = score;
                        best = new int[]{gx, gz};
                    }
                }
            }
            return best;
        }

        static boolean isHall(Kind k) {
            return k == Kind.NARTHEX || k == Kind.BAY || k == Kind.CHANCEL;
        }

        /** The Rope Shaft off a calm bay's aisle, and the Bellmaker's Rest above its far wall (a secret world). */
        void ropes() {
            if (!hero("rope_shaft")) return;
            List<Integer> bays = new ArrayList<>();
            for (int w : nave) if (kinds.get(w) == Kind.BAY && !p.sealable(p.world(w))) bays.add(w);
            double mid = (bays.size() - 1) / 2.0;
            List<Integer> sorted = new ArrayList<>(bays);
            sorted.sort((a, b) -> Double.compare(Math.abs(bays.indexOf(a) - mid), Math.abs(bays.indexOf(b) - mid)));
            int first = (int) (Hash.of(seed, 7, 41) & 1);
            for (int w : sorted) {
                Room r = p.world(w);
                for (int t = 0; t < 2; t++) {
                    boolean west = ((first + t) & 1) == 0;
                    int sx = west ? r.gx - 1 : r.gx + r.gw, rx = west ? sx - 1 : sx + 1;
                    if (rx < 1 || rx > 14) continue;
                    for (int gz = r.gz; gz < r.gz + r.gh; gz++) {
                        if (!p.free(sx, gz, 1, 1) || !p.free(rx, gz, 1, 1)) continue;
                        ropes = p.extra(sx, gz, 1, 1, 0);
                        place(ropes, Kind.ROPES);
                        p.join(w, ropes);
                        rest = p.extra(rx, gz, 1, 1, REST_UP);
                        place(rest, Kind.REST);
                        p.join(ropes, rest);
                        if (world != null) {
                            for (GateWorld.Secret sc : world.dressing().secrets()) {
                                if (!sc.leadsTo().isEmpty()) {
                                    p.placeWorlds.put(rest, sc.leadsTo());
                                    GateWorld inner = dev.awaken.gates.worlds.GateWorlds.get(sc.leadsTo());
                                    if (inner != null && !inner.biome().isEmpty()) p.placeBiomes.put(rest, inner.biome());
                                    break;
                                }
                            }
                        }
                        return;
                    }
                }
            }
        }

        // ---- the carve ----------------------------------------------------------------------------------------------

        Kind kindAtRow(int z) {
            int row = z >> 4;
            return row < 0 || row > 15 || rowPlace[row] < 0 ? null : kinds.get(rowPlace[row]);
        }

        void sculpt() {
            s = new Sculpt(this::skin, c.plot().minY(), c.plot().maxY());
            pickHeroes();
            for (int i = 0; i < runs.size(); i++) hall(i);
            for (int w : kinds.keySet()) {
                Room r = p.world(w);
                switch (kinds.get(w)) {
                    case CHAPEL, CRYPT -> chapel(r, kinds.get(w) == Kind.CRYPT);
                    case ROPES -> ropeShaft(r);
                    case REST -> rest(r);
                    default -> {}
                }
            }
            p.thresholds(s, W, floor, trim);
            causeway();
            bells();
            handbells();
            if (choir >= 0) choir();
            for (int i = 0; i < runs.size(); i++) ends(i);
            moonlight();
            p.screens(s, W, (lat, rise, side) -> {
                if (rise < 2 || lat % 4 == 0 || rise % 9 == 8) return trim;
                return side == 0 || side == 2 ? grille : grilleZ;
            });
            // the years on its floors (38.14, stage 3), new gates only
            if (c.plan().contentVersion() >= 7) {
                wear = new CryptWear(p, W, seed, floor, wet, trim, (r, x, z) -> {
                    // the bays' pits and their causeway hold the water; the rope shaft and the rest are their own
                    Kind k = kinds.get(r.id);
                    return k == null || k == Kind.ROPES || k == Kind.REST || k == Kind.BAY && Math.abs(x + 0.5 - AXIS) < ARCADE + 1;
                });
                wear.apply(s);
            }
            p.handbells(s);
            s.seal();
        }

        void pickHeroes() {
            int hub = -1, arena = -1;
            List<Integer> bays = new ArrayList<>();
            for (int w : nave) {
                Room r = p.world(w);
                if (kinds.get(w) != Kind.BAY) continue;
                if (r.type == Room.Type.HUB && hub < 0) hub = w;
                else if (r.type == Room.Type.ARENA && arena < 0) arena = w;
                bays.add(w);
            }
            if (hero("great_bell") && !bays.isEmpty()) greatBell = hub >= 0 ? hub : arena >= 0 ? arena : bays.get(bays.size() / 2);
            if (hero("drowned_choir")) {
                for (int w : bays) {
                    if (w == greatBell || p.sealable(p.world(w))) continue;
                    choir = w;
                    if (bays.indexOf(w) >= 1) break;
                }
            }
        }

        BlockState skin(int x, int y, int z, boolean fl, boolean ceil) {
            double n = Hash.noise3(seed, x, y, z, 4.5);
            int h = y - W;
            if (fl) return h <= -2 || n < 0.3 ? wet : floor;
            if (ceil) return n < 0.5 ? worn : wall;
            if (h < 3 && n < 0.45) return n < 0.2 ? crumbling : worn;
            return n < 0.28 ? worn : n > 0.88 ? crumbling : wall;
        }

        boolean broken(int z, int side) {
            if (!mood.equals("storm")) return false;
            int row = z >> 4;
            int col = side < 0 ? 5 : 9;
            if (p.b.at(col, row) >= 0) return false;
            return Hash.unit(seed, row * 2 + (side < 0 ? 0 : 1), 913) < 0.45;
        }

        /** One run of the nave: its whole cross-section, worked out per block. */
        void hall(int run) {
            int z0 = runs.get(run)[0], z1 = runs.get(run)[1];
            s.op(93, W - 6, z0 - 3, 146, W + SPRING + RISE + 3, z1 + 3, (b, x, y, z) -> {
                byte v = z < z0 || z > z1 ? S : cell(x, y - W, z, z1);
                switch (v) {
                    case O -> b.mark(x, y, z, Sculpt.OPEN);
                    case WTR -> b.set(x, y, z, water);
                    case TRIM -> b.set(x, y, z, trim);
                    case GLASS -> b.set(x, y, z, glass);
                    default -> b.mark(x, y, z, Sculpt.SOLID);
                }
            });
            // the porch's stair turrets up to the galleries
            if (kindAtRow(z0) == Kind.NARTHEX) {
                turret(AXIS - 16.5 + 0.0, z0 + 6.5);
                turret(AXIS + 16.5, z0 + 6.5);
            }
        }

        byte cell(int x, int h, int z, int runEnd) {
            double a = x + 0.5 - AXIS, aa = Math.abs(a);
            Kind k = kindAtRow(z);
            if (k == null) return S;
            int zl = z & 15;
            boolean pier = zl == 15 || zl <= 1;
            int u = zl - 8;
            if (h < 0) {
                if (k == Kind.BAY && aa > CAUSE && aa <= NAVE) {
                    // a flooded bay's water laps at the causeway's brim: one step up out of it anywhere
                    if (!flood) return h == -1 ? WTR : S;
                    // at low tide it's a pit three deep, a hand of water at its foot, and mid-bay a stair down from the
                    // causeway and another up to the arcade, so it's crossed on foot as the water used to be swum
                    if (h <= LOW - 1) return S;
                    int step = stair(aa, zl);
                    if (step > 0) return h <= -step ? S : O;
                    return h == LOW ? WTR : O;
                }
                return S;
            }
            if (aa <= NAVE) {
                if (k == Kind.CHANCEL && h == 0) {
                    // the chancel: stalls along its sides, a step up to the altar's end
                    if (aa > 8 && z < runEnd - 6) return TRIM;
                    if (z > runEnd - 4 && aa < 8.5) return S;
                }
                return vaulted(aa, h, zl);
            }
            if (aa <= ARCADE) {
                if (pier) return h < SPRING ? S : vaulted(aa, h, zl);
                double a1 = Sculpt.pointedArch(u, 7, 8, 6);
                if (h < a1) return O;
                if (h < a1 + 1) return TRIM;
                if (h < GALLERY) return S;
                if (h == GALLERY) return aa <= NAVE + 1 ? TRIM : O;
                double a2 = Sculpt.pointedArch(u, 7, 22, 6);
                if (h < a2) return O;
                if (h < a2 + 1) return TRIM;
                if (h < SPRING) return S;
                return vaulted(aa, h, zl);
            }
            if (aa <= AISLE) {
                if (pier && aa <= PIER) return S;
                double av = Sculpt.pointedArch(aa - 16.5, 4, 9, 4);
                if (h < av) return pier && h >= av - 1 ? TRIM : O;
                if (h < GALLERY) return S;
                return h < 29 - (aa - ARCADE) * 0.5 ? O : S;
            }
            // the outer wall: lancets of moonlight along the galleries (broken in a storm, open to the night)
            if (Math.abs(u) <= 2 && h > GALLERY) {
                double la = Sculpt.pointedArch(u, 2.5, 24, 4);
                if (h < la) {
                    if (broken(z, (int) Math.signum(a))) return O;
                    if (aa <= AISLE + 1) return u == 0 ? TRIM : GLASS;
                }
            }
            return S;
        }

        /** On a pit's stairs (mid-bay, three steps each side): how many steps down this block is, else 0. */
        static int stair(double aa, int zl) {
            if (zl < 7 || zl > 9) return 0;
            int step = (int) Math.round(Math.min(aa - CAUSE, NAVE + 1 - aa));
            return step >= 1 && step <= -LOW ? step : 0;
        }

        /** The nave's vault: open under the curve, ribs on it (transverse at the piers, diagonal across each bay, the ridge). */
        static byte vaulted(double aa, int h, int zl) {
            double vt = vault(Math.min(aa, ARCADE));
            if (h < vt) return O;
            if (h < vt + 1) {
                int u = zl - 8;
                boolean rib = zl == 0 || aa <= 0.5 || Math.abs(aa - Math.abs(u + 0.5) * ARCADE / 8.0) < 0.75;
                if (rib) return TRIM;
            }
            return S;
        }

        /** A stair turret: a helix of steps round a newel, from the aisle floor up through the gallery. */
        void turret(double cx, double cz) {
            int x0 = (int) Math.floor(cx - 4), x1 = (int) Math.ceil(cx + 4), z0 = (int) Math.floor(cz - 4), z1 = (int) Math.ceil(cz + 4);
            s.op(x0, W, z0, x1, W + GALLERY + 2, z1, (b, x, y, z) -> {
                double dx = x + 0.5 - cx, dz = z + 0.5 - cz, d = Math.hypot(dx, dz);
                if (d >= 3.6) return;
                int h = y - W;
                if (d < 0.9) {
                    if (h < GALLERY) b.mark(x, y, z, Sculpt.SOLID);
                    return;
                }
                int step = (int) Math.floor((Math.atan2(dz, dx) + Math.PI) / (2 * Math.PI) * 12) % 12;
                if (h < GALLERY && Math.floorMod(h - step, 12) == 0) b.set(x, y, z, trim);
                else b.mark(x, y, z, Sculpt.OPEN);
            });
        }

        /** The run's ends: the great west door in the porch, the rose window over the far end. */
        void ends(int run) {
            int z0 = runs.get(run)[0], z1 = runs.get(run)[1];
            if (kindAtRow(z0) == Kind.NARTHEX) {
                s.op(115, W, z0 - 2, 124, W + 10, z0 - 1, (b, x, y, z) -> {
                    double a = x + 0.5 - AXIS, top = Sculpt.pointedArch(a, 3.5, 5, 4);
                    if (!(y - W < top)) return;
                    if (z == z0 - 1) b.mark(x, y, z, Sculpt.OPEN);
                    else b.set(x, y, z, Math.abs(a) < 0.6 ? trim : leaves);
                });
                s.putOpen(116, W, z0, BellPieces.candles(candle, 4, true));
                s.putOpen(123, W, z0, BellPieces.candles(candle, 4, true));
            }
            if (run == runs.size() - 1 && hero("rose_window")) {
                // in the far end's last row, against its wall (the Lord's arena is beyond it on a Lord's floor)
                s.op(108, W + 17, z1, 131, W + 42, z1, (b, x, y, z) -> {
                    if (!b.open(x, y, z)) return;
                    double a = x + 0.5 - AXIS, hy = y + 0.5 - W - ROSE_Y, r = Math.hypot(a, hy);
                    if (r > ROSE_R) return;
                    double t = (Math.atan2(hy, a) + Math.PI) / (2 * Math.PI) * 12;
                    int petal = (int) Math.floor(t) % 12;
                    double arc = Math.abs(t - Math.rint(t)) * 2 * Math.PI * r / 12;
                    BlockState st;
                    if (r > ROSE_R - 1 || (r > 5.5 && r <= 6.5) || (r > 2.5 && arc < 0.5)) st = trim;
                    else if (r <= 2.5) st = hue(2);
                    else if (r <= 5.5) st = hue(petal % 2 == 0 ? 1 : 0);
                    else st = hue(petal % 4 == 1 ? 3 : petal % 2 == 0 ? 0 : 4);
                    b.set(x, y, z, st);
                });
                int ry = (int) Math.round(ROSE_Y);
                lights.add(new FloorAir.Light(120, ry, z1, 0xd9a441, 18f, 0.9f));
                lights.add(new FloorAir.Light(113, ry + 5, z1, 0xb02a3c, 14f, 0.7f));
                lights.add(new FloorAir.Light(127, ry + 5, z1, 0x2a4bb0, 14f, 0.7f));
                lights.add(new FloorAir.Light(113, ry - 5, z1, 0x6a3cb0, 12f, 0.6f));
                lights.add(new FloorAir.Light(127, ry - 5, z1, 0x3c8ab0, 12f, 0.6f));
                shafts.add(new FloorAir.Shaft(120, ry, z1, 120, 0, z1 - 24, 6f, 0xb06a8a, 0.35f));
            }
        }

        BlockState hue(int k) {
            return WorldMats.with(rose, "hue", k);
        }

        /** Candles down both edges of the causeway, a light at every bay's edge, candles at the aisle walls. */
        void causeway() {
            for (int[] run : runs) {
                for (int z = run[0]; z <= run[1]; z++) {
                    Kind k = kindAtRow(z);
                    int zl = z & 15;
                    if (zl == 0) s.putOpen(119, W + 1, z, light);
                    if (zl == 0 || zl == 15) continue;
                    if (Math.floorMod(z, 4) == 0) causewayCandle(117, z);
                    if (Math.floorMod(z, 4) == 2) causewayCandle(122, z);
                    if (zl == 8) {
                        for (int x : new int[]{99, 140}) {
                            boolean lit = requiem() || Hash.unit(seed, x, z) >= 0.4;
                            s.putOpen(x, W, z, BellPieces.candles(candle, 2 + Hash.range(seed, x, z, 2), lit));
                            if (lit) minor.add(new FloorAir.Light(x, 0, z, 0xffa04a, 5f, 0.5f));
                        }
                    }
                    if (k == Kind.BAY && Hash.range(seed, z, 77, 100) < (requiem() ? 45 : 22)) lilies(z);
                }
            }
        }

        void causewayCandle(int x, int z) {
            s.putOpen(x, W, z, BellPieces.candles(candle, 4 + Hash.range(seed, x, z, 2), true));
            if (Math.floorMod(z, 8) < 2) minor.add(new FloorAir.Light(x, 0, z, 0xffb35c, 6f, 0.55f));
        }

        void lilies(int z) {
            for (int x = 109; x <= 130; x++) {
                double aa = Math.abs(x + 0.5 - AXIS);
                if (aa <= CAUSE + 1 || (flood && stair(aa, z & 15) > 0)) continue;
                if (Hash.unit(seed, x * 7 + 3, z) < 0.06) s.putOpen(x, lilyY(), z, lily);
            }
        }

        /** Lilies float on the water: at the brim, or two below it at low tide. */
        int lilyY() {
            return flood ? W + LOW + 1 : W;
        }

        /** A bell with a lamp inside over the water of every bay (three when they toll), the Great Bell in its place. */
        void bells() {
            boolean tolling = mood.equals("tolling");
            for (int w : nave) {
                Room r = p.world(w);
                if (kinds.get(w) != Kind.BAY) continue;
                if (w == greatBell) {
                    int zc = r.gh == 1 ? r.gz * 16 + 8 : (r.gz + r.gh / 2) * 16 + (r.gh % 2 == 1 ? 8 : 0);
                    BellPieces.bell(s, AXIS, W + 3, zc, 7.5, 15, W + (int) vault(0.5) - 1, bronze, lamp, chain, true);
                    p.bell("great", AXIS, 3, zc, 7.5, 15);
                    s.putOpen(119, W + 1, zc - 1, light);
                    lights.add(0, new FloorAir.Light(120, 14, zc, 0x7fc8ff, 22f, 1f));
                    continue;
                }
                for (int gz = r.gz; gz < r.gz + r.gh; gz++) {
                    if (w == choir && gz == r.gz) continue;
                    int flip = (int) ((gz + Hash.of(seed, 3, 9)) & 1);
                    for (int i = 0; i < (tolling ? 3 : 2); i++) {
                        double bx = i == 2 ? AXIS : AXIS + ((i ^ flip) == 0 ? -6.5 : 6.5);
                        int zc = gz * 16 + (i == 2 ? 8 : i == 0 ? 4 : 12);
                        double R = i == 2 ? 3.1 : 2.4 + Hash.unit(seed, gz, 50 + i) * 0.6;
                        int lip = W + 19 + Hash.range(seed, gz, 60 + i, 6);
                        double vt = vault(Math.abs(bx - AXIS));
                        p.hungBell(s, "", bx, lip, W, zc + (i == 2 ? 0 : 0.5), R, R > 2.7 ? 6 : 5, W + (int) Math.ceil(vt) - 1, bronze, lamp, chain);
                        lights.add(new FloorAir.Light((int) Math.floor(bx), lip - W + 2, zc, 0x7fc8ff, 9f, 0.7f));
                    }
                }
            }
        }

        /**
         * A handbell at the causeway's edge in every bay, west and east by turns, between its candles: the bell anyone can
         * reach (the great ones hang far overhead). The choir's bay has its own.
         */
        void handbells() {
            for (int w : nave) {
                Room r = p.world(w);
                if (kinds.get(w) != Kind.BAY) continue;
                for (int gz = r.gz; gz < r.gz + r.gh; gz++) {
                    if (w == choir && gz == r.gz) continue;
                    boolean west = (gz & 1) == 0;
                    p.handbell(handbell, W, west ? 117 : 122, 0, gz * 16 + (west ? 6 : 9), west ? Direction.EAST : Direction.WEST);
                }
            }
        }

        /** The pits the Flood fills: each stretch of bays either side of the causeway, from the low water up to the brim. */
        void basins() {
            for (int[] run : runs) {
                int from = -1;
                for (int z = run[0]; z <= run[1] + 1; z++) {
                    boolean wetRow = z <= run[1] && kindAtRow(z) == Kind.BAY;
                    if (wetRow && from < 0) from = z;
                    if (!wetRow && from >= 0) {
                        p.mark(FloorLayout.Mark.BASIN, "", 109, LOW + 1, from, 116, -1, z - 1);
                        p.mark(FloorLayout.Mark.BASIN, "", 123, LOW + 1, from, 130, -1, z - 1);
                        from = -1;
                    }
                }
            }
        }

        /** The Drowned Choir: two rows of stone singers each side of the causeway, waist-deep, robed, facing it. */
        void choir() {
            Room r = p.world(choir);
            int gz = r.gz;
            for (int zl : new int[]{3, 7, 11}) {
                int z = gz * 16 + zl;
                for (int x : new int[]{111, 114, 125, 128}) {
                    // at low tide they stand on plinths in the pit, so the Flood leaves them waist-deep again
                    if (flood) s.box(x, W + LOW, z, x, W - 2, z, worn);
                    BellPieces.singer(s, x, W - 1, z, x < AXIS ? Direction.EAST : Direction.WEST, worn, wall, vestment);
                }
            }
            for (int z = gz * 16 + 2; z <= gz * 16 + 13; z += 3) {
                s.putOpen(116, lilyY(), z, lily);
                s.putOpen(123, lilyY(), z + 1, lily);
            }
            // the handbell the choir waits on, at the causeway's edge among them: strike it and they sing their last note
            int hz = gz * 16 + 7;
            p.handbell(handbell, W, 122, 0, hz, Direction.WEST, "choir_hymn", null);
            minor.add(0, new FloorAir.Light(116, 0, gz * 16 + 8, 0xffc070, 7f, 0.6f));
        }

        /** A chapel off an aisle (an altar under a lancet, vestments on the walls), or a crypt of niches below it. */
        void chapel(Room r, boolean crypt) {
            int x0 = r.x0(), x1 = r.x1(), z0 = r.z0(), z1 = r.z1();
            double cx = (x0 + x1 + 1) / 2.0, cz = (z0 + z1 + 1) / 2.0;
            int top = crypt ? 7 : 9;
            s.carve(x0, W, z0, x1, W + top + 2, z1, (x, y, z) -> {
                double h = y - W;
                if (x < x0 || x > x1 + 1 || z < z0 || z > z1 + 1 || h < 0) return 1;
                double v = Math.min(Sculpt.pointedArch(x - cx, 6.5, top - 3, 3), Sculpt.pointedArch(z - cz, 6.5, top - 3, 3));
                return Double.isNaN(v) || h >= v ? 1 : -1;
            });
            int ds = r.doors.isEmpty() ? 3 : r.doors.get(0).side;
            int far = (ds + 2) % 4;
            // the altar against the far wall, candles on it, a window or a lit niche above
            int ax = far == 1 ? x1 - 1 : far == 3 ? x0 + 1 : (int) Math.floor(cx);
            int az = far == 2 ? z1 - 1 : far == 0 ? z0 + 1 : (int) Math.floor(cz);
            boolean alongX = far == 0 || far == 2;
            for (int d = -1; d <= 1; d++) {
                int x = alongX ? ax + d : ax, z = alongX ? az : az + d;
                s.put(x, W, z, trim);
                s.put(x, W + 1, z, BellPieces.candles(candle, 2 + Hash.range(seed, x, z, 3), true));
            }
            int wx = far == 1 ? x1 + 1 : far == 3 ? x0 - 1 : ax, wz = far == 2 ? z1 + 1 : far == 0 ? z0 - 1 : az;
            for (int d = -1; d <= 1; d++) {
                for (int h = 3; h <= 6; h++) {
                    int x = alongX ? wx + d : wx, z = alongX ? wz : wz + d;
                    if (h == 6 && d != 0) continue;
                    s.put(x, W + h, z, crypt ? WorldMats.facing(niche, Direction.from2DDataValue(fromSide(ds))) : glass);
                }
            }
            lights.add(new FloorAir.Light(ax, 2, az, crypt ? 0x9fd6ff : 0xffb35c, 8f, 0.7f));
            // the side walls: niches of the dead in a crypt, hung vestments in a chapel
            for (int side = 0; side < 4; side++) {
                if (side == ds || side == far) continue;
                Direction in = Direction.from2DDataValue(fromSide((side + 2) % 4));
                for (int i = 1; i < 11; i += crypt ? 2 : 4) {
                    int x = side == 1 ? x1 + 1 : side == 3 ? x0 - 1 : x0 + i, z = side == 2 ? z1 + 1 : side == 0 ? z0 - 1 : z0 + i;
                    if (crypt) {
                        s.put(x, W + 1, z, WorldMats.facing(niche, in));
                        s.put(x, W + 3, z, WorldMats.facing(niche, in));
                    } else {
                        int vx = x - (side == 1 ? 1 : side == 3 ? -1 : 0), vz = z - (side == 2 ? 1 : side == 0 ? -1 : 0);
                        s.putOpen(vx, W + 2, vz, WorldMats.facing(vestment, in));
                        s.putOpen(vx, W + 3, vz, WorldMats.facing(vestment, in));
                    }
                }
            }
            for (int[] k : new int[][]{{x0 + 1, z0 + 1}, {x1 - 1, z0 + 1}, {x0 + 1, z1 - 1}, {x1 - 1, z1 - 1}}) {
                boolean lit = requiem() || Hash.unit(seed, k[0], k[1]) < 0.5;
                s.putOpen(k[0], W, k[1], BellPieces.candles(candle, 1 + Hash.range(seed, k[0], k[1], 3), lit));
            }
        }

        /** A wall side (0 north, 1 east, 2 south, 3 west) as Direction's 2D value (0 south, 1 west, 2 north, 3 east). */
        static int fromSide(int side) {
            return switch (side) {
                case 0 -> 2;
                case 1 -> 3;
                case 2 -> 0;
                default -> 1;
            };
        }

        /** The Rope Shaft: a tower of hanging bell ropes, one odd rope reaching the floor and the Bellmaker's door. */
        void ropeShaft(Room r) {
            int x0 = r.x0(), x1 = r.x1(), z0 = r.z0(), z1 = r.z1();
            s.carve(x0, W, z0, x1, W + ROPES_H, z1, (x, y, z) -> x >= x0 && x <= x1 + 1 && z >= z0 && z <= z1 + 1 && y >= W && y < W + ROPES_H ? -1 : 1);
            Room.Door up = null;
            for (Room.Door d : r.doors) if (d.toRoom == rest) up = d;
            int ox = up == null ? x0 + 1 : up.side == 3 ? x0 + 1 : x1 - 1;
            int oz = r.gz * 16 + 7;
            s.box(ox, W, oz, ox, W + ROPES_H - 1, oz, rope);
            p.secret("odd_rope", ox, 0, oz, ox, ROPES_H - 1, oz);
            s.putOpen(ox + (up != null && up.side == 3 ? 1 : -1), W, oz + 1, BellPieces.candles(candle, 1, true));
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    int x = x0 + 1 + 3 * i, z = z0 + 1 + 3 * j;
                    if (Math.abs(x - ox) <= 1 && Math.abs(z - oz) <= 1) continue;
                    int bottom = 5 + Hash.range(seed, x, z, 6);
                    s.box(x, W + bottom, z, x, W + ROPES_H - 1, z, rope);
                }
            }
            // three small bells high in the tower, lamps in them
            for (int k = 0; k < 3; k++) {
                double bx = x0 + 3.5 + 2.5 * k, bz = z0 + 3.5 + (k == 1 ? 5 : 0);
                p.hungBell(s, "", bx, W + 40 + 3 * k, W, bz, 1.8, 4, W + ROPES_H - 1, bronze, lamp, chain);
                lights.add(new FloorAir.Light((int) bx, 42 + 3 * k, (int) bz, 0x7fc8ff, 7f, 0.6f));
            }
            s.putOpen(x0 + 6, W + 1, z0 + 6, WorldMats.light(7));
        }

        /** The Bellmaker's Rest: a warm workshop above the shaft, a bell half-cast on its floor, candles lit. */
        void rest(Room r) {
            int x0 = r.x0(), x1 = r.x1(), z0 = r.z0(), z1 = r.z1();
            int y0 = W + REST_UP;
            s.carve(x0, y0, z0, x1, y0 + REST_H, z1, (x, y, z) -> x >= x0 && x <= x1 + 1 && z >= z0 && z <= z1 + 1 && y >= y0 && y < y0 + REST_H ? -1 : 1);
            double cx = (x0 + x1 + 1) / 2.0, cz = (z0 + z1 + 1) / 2.0;
            BellPieces.bell(s, cx + 2, y0, cz + 2, 2.2, 4, y0 + 4, bronze, lamp, chain, false);
            p.bell(cx + 2, REST_UP, cz + 2, 2.2, 4);
            s.put(x0 + 1, y0, z1 - 1, GatePainter.state("minecraft:smithing_table"));
            s.put(x0 + 2, y0, z1 - 1, GatePainter.state("minecraft:anvil"));
            for (int x = x0 + 1; x <= x0 + 4; x++) s.put(x, y0, z0, GatePainter.state("minecraft:bookshelf"));
            s.put(x0 + 3, y0 + 1, z1 - 1, BellPieces.candles(candle, 3, true));
            s.put((int) cx, y0 + REST_H - 1, (int) cz, GatePainter.state("minecraft:lantern[hanging=true]"));
            for (int k : new int[]{0, 1}) s.put(k == 0 ? x0 : x1, y0 + 2, (int) cz + k, trim);
            lights.add(new FloorAir.Light((int) cx, REST_UP + 3, (int) cz, 0xffb060, 10f, 0.9f));
        }

        // ---- what the floor walks on and how its air glows ----------------------------------------------------------

        boolean walk(Room r, int x, int z) {
            if (wear != null && !wear.level(x, z)) return false;
            Kind k = kinds.get(r.id);
            if (k == null) return false;
            double aa = Math.abs(x + 0.5 - AXIS);
            int zl = z & 15;
            boolean aisle = aa > PIER && aa < AISLE - 0.5;
            return switch (k) {
                case NARTHEX -> (aa < NAVE - 0.5 && Math.abs(aa - CAUSE) > 0.1 && zl >= 2) || (aisle && zl >= 11);
                case BAY -> aa <= CAUSE - 1 || aisle;
                case CHANCEL -> {
                    int end = runs.get(runs.size() - 1)[1];
                    yield (aa < 8 && Math.abs(aa - CAUSE) > 0.1 && z <= end - 4) || aisle;
                }
                case CHAPEL, CRYPT -> x > r.x0() && x < r.x1() && z > r.z0() && z < r.z1() && !nearAltar(r, x, z);
                default -> false;
            };
        }

        boolean nearAltar(Room r, int x, int z) {
            int ds = r.doors.isEmpty() ? 3 : r.doors.get(0).side;
            return switch ((ds + 2) % 4) {
                case 0 -> z <= r.z0() + 2;
                case 1 -> x >= r.x1() - 2;
                case 2 -> z >= r.z1() - 2;
                default -> x <= r.x0() + 2;
            };
        }

        /** Moonlight from each lancet down across the nave: a shaft for Gate Air, a soft light where it lands. */
        void moonlight() {
            for (int[] run : runs) {
                for (int z = run[0] + 8; z <= run[1]; z += 16) {
                    for (int side : new int[]{-1, 1}) {
                        if (broken(z, side)) continue;
                        int lx = (int) (AXIS + side * 21.5 - 0.5), landX = (int) Math.floor(AXIS - side * 1.5);
                        shafts.add(new FloorAir.Shaft(lx, 25, z, landX, 0, z + 3 * side, 2.2f, 0xa9b8ff, 0.5f));
                        minor.add(0, new FloorAir.Light(lx, 24, z, 0xb8c8ff, 8f, 0.5f));
                        s.putOpen(landX, W, z + 3 * side, WorldMats.light(7));
                    }
                }
            }
        }

        FloorAir air() {
            List<FloorAir.Light> all = new ArrayList<>(lights);
            all.addAll(minor);
            List<FloorAir.Water> waters = new ArrayList<>();
            List<FloorAir.Mist> mists = new ArrayList<>();
            for (int[] run : runs) {
                int from = -1;
                for (int z = run[0]; z <= run[1] + 1; z++) {
                    boolean wetRow = z <= run[1] && kindAtRow(z) == Kind.BAY;
                    if (wetRow && from < 0) from = z;
                    if (!wetRow && from >= 0) {
                        waters.add(new FloorAir.Water(flood ? LOW : -1, 109, from, 130, z - 1));
                        from = -1;
                    }
                }
            }
            mists.add(new FloorAir.Mist(-1, 3, 0x56657a, mood.equals("storm") ? 0.35f : 0.55f));
            mists.add(new FloorAir.Mist(SPRING, 12, 0x3a4252, 0.18f));
            return new FloorAir(all, shafts, mists, waters);
        }

        /** An inner gate on a hall floor stands across an aisle, clear of the causeway. */
        void innerGates(FloorLayout f) {
            for (Room r : f.rooms) {
                if (r.type != Room.Type.INNER_GATE || !isHall(kinds.get(r.id))) continue;
                Room.Feature g = r.feature(Room.FeatureKind.INNER_GATE);
                if (g == null) continue;
                r.features.remove(g);
                r.features.add(new Room.Feature(Room.FeatureKind.INNER_GATE, (int) (AXIS + 16.5), 0, r.gz * 16 + 8, 1));
            }
        }
    }
}
