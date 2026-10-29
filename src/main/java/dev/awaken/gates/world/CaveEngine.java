package dev.awaken.gates.world;

import dev.awaken.gates.worlds.GateWorld;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The cave world (SPEC §38.10, Stage 2): the programme's rooms become caverns strung on winding tunnels ({@link
 * ChainLayout}), shaped by noise, domed and lit by what grows or burns in them. One engine for every theme's caves: its
 * recipe's {@code params} set the shape (how high the roofs, how wide the tunnels, lakes in the big caverns, a sea with
 * islands, hexagonal comb) and its theme's {@link Dress} grows the world in it (mushrooms, timber, comb, roots) and its
 * hero pieces. The Lord's arena and Rig rooms stand on the chain as built rooms.
 * <p>
 * An open land (kind {@code open}, SPEC §38.11) is the same chain under the sky: the land stands solid up to its
 * skyline (dunes, roofs, buried towns), its places and ways are cut open to the sky, and an invisible lid over the whole
 * floor keeps everyone below the skyline, so nobody flies over a wall the bars close or walks the roofs past a door.
 * <p>
 * Stage 4 (SPEC §38.12) adds four shapes by param: {@code isles} (places are islands in the air or the dark, links
 * railed bridges, a fall caught), {@code plain} (one floor to the fog with nothing walled, its outer {@code band} of slots
 * empty so the live layer can walk you round it forever), {@code fold} (the spine one straight hall with a fold in its
 * middle) and {@code mirror} (the world again upside down under a glass floor).
 */
final class CaveEngine implements WorldEngine {
    static final int BASE = 40, DEEP = 6;
    /** An open land's skyline at most this far over its floor (the hero pieces rise higher). */
    static final int SKY_MAX = 40;

    enum Kind { ARRIVAL, CAVERN, GREAT, END, LINK, BUILT, SECRET }

    @Override
    public FloorLayout plan(Ctx c) {
        return new Cave(c).run();
    }

    /** One floor in the making: its places and their shapes, what the dress asks for, the carve. */
    static final class Cave {
        final Ctx c;
        final WorldPlan p;
        final ChainLayout chain;
        final GateWorld world;
        final int W;
        final long seed;
        final String mood;
        /**
         * The world whose recipe dresses the floor (its blocks, hero pieces and look): its own, or for a rare world that
         * borrows another theme's look (38.13) an everyday world of that theme, its shape too unless the rare world says.
         */
        final GateWorld look;
        final String lookId;
        final dev.awaken.gates.content.Theme borrowed;
        final WorldMats m;
        final Map<Integer, Kind> kinds = new HashMap<>();
        final Map<Integer, PassageEngine.Shape> shapes = new HashMap<>();
        /** A place's roof over its middle, blocks above its floor. */
        final Map<Integer, Integer> roofs = new HashMap<>();
        final List<FloorAir.Light> lights = new ArrayList<>();
        final List<FloorAir.Shaft> shafts = new ArrayList<>();
        final List<FloorAir.Water> waters = new ArrayList<>();
        /** Ground nobody stands on (a stem, a pillar, a pool): x, z, radius. */
        final List<double[]> blocked = new ArrayList<>();
        /** The shape: tunnel half-width, how ragged the walls, roof heights, the deep cavern's tower. */
        final double hw, wobble;
        final int roof, tower, sky;
        final boolean lakes, sea, hex, square;
        /** An open land under the sky (38.11): its lid over the floor, how its walls lean back, whether it's night there. */
        final boolean open, dark;
        final int lid, deep;
        final double slope;
        /** Stage 4's shapes (38.12): islands, an endless plain, a hall with a fold, a mirrored underfloor; a cloud sea under. */
        final boolean isles, plain, line, mirror;
        final int under, clouds;
        final BlockState floor, wall, trim, fluid, light, cloud;
        final Dress dress;
        int secret = -1, great = -1;
        Sculpt s;
        /** The places' shaped ground on a version 7 floor whose dress shapes it (SPEC §38.14), else null. */
        Relief relief;

        Cave(Ctx c) {
            this.c = c;
            this.p = new WorldPlan(c);
            this.world = c.world();
            this.W = c.plot().minY() + BASE;
            this.seed = c.seed();
            this.mood = c.mood() == null ? "" : c.mood();
            this.chain = new ChainLayout(p, seed, c.variation());
            String src = Dress.look(c.worldId(), world, c.theme(), seed);
            GateWorld worn = src == null ? null : dev.awaken.gates.worlds.GateWorlds.get(src);
            this.borrowed = worn == null ? null : worn.theme();
            this.lookId = worn == null ? c.worldId() : src;
            this.look = worn == null ? world : worn;
            this.m = new WorldMats(look);
            hw = param("tunnel", 2.1);
            wobble = param("wobble", 2.0);
            roof = (int) param("roof", 11);
            tower = (int) param("tower", 0);
            sky = (int) param("sky", 36);
            lakes = param("lake", 0) > 0;
            sea = param("sea", 0) > 0;
            hex = param("hex", 0) > 0;
            square = param("square", 0) > 0;
            open = world != null && world.open();
            lid = (int) param("lid", 16);
            slope = param("slope", 0);
            deep = open ? (int) param("deep", 0) : 0;
            dark = param("night", 0) > 0 || this.mood.equals("night") || this.mood.equals("power_cut");
            String kind = world == null ? "" : world.kind();
            isles = param("isles", 0) > 0 || kind.equals("isles");
            plain = param("plain", 0) > 0 || kind.equals("plain");
            line = param("fold", 0) > 0 || kind.equals("fold");
            mirror = param("mirror", 0) > 0;
            under = (int) param("under", 8);
            clouds = (int) param("clouds", 0);
            int band = (int) param("band", 0);
            if (band > 0) {
                chain.lo = band;
                chain.hi = 15 - band;
            }
            wall = m.get("wall", "minecraft:deepslate");
            floor = m.get("floor", "minecraft:mud");
            trim = m.get("trim", "minecraft:polished_deepslate");
            fluid = m.get("fluid", "minecraft:water");
            light = WorldMats.light(12);
            cloud = clouds > 0 ? m.get("cloud", "minecraft:white_concrete") : null;
            dress = Dress.of(this);
        }

        double param(String k, double dflt) {
            GateWorld w = world != null && world.params().containsKey(k) ? world : look;
            return w == null ? dflt : w.param(k, dflt);
        }

        boolean hero(String id) {
            List<String> h = look == null ? List.of() : look.dressing().hero();
            return h.isEmpty() || h.contains(id);
        }

        boolean lava() {
            return fluid.getBlock() == Blocks.LAVA;
        }

        FloorLayout run() {
            dress.programme();
            if (!(line ? chain.layLine(this::size) : chain.lay(this::size))) return WorldEngine.fail("the chain doesn't fit: " + WorldEngine.WHY.get());
            kinds();
            secretPlace();
            sculpt();
            String biome = world == null ? "" : world.biomeFor(mood);
            FloorLayout f = p.finish(this::walk, new WorldPlan.Look(W, biome, air(), new WorldPlan.SculptModel(s, dress.tint()), Float.NaN, open));
            if (f == null) return WorldEngine.fail("the programme didn't carry over: " + WorldEngine.WHY.get());
            for (var e : kinds.entrySet()) {
                if (e.getValue() == Kind.BUILT && !WorldPlan.built(f.room(e.getKey()))) return WorldEngine.fail("a Rig room didn't rebuild");
            }
            return f;
        }

        int[] size(int id) {
            Room L = p.room(id);
            if (WorldPlan.built(L)) return new int[]{L.gw, L.gh};
            if (id == p.logic.start) return sea || isles ? new int[]{2, 2} : new int[]{1, 1};
            if (id == p.logic.end) return new int[]{2, 2};
            // a hall's rooms open off it one slot each (only an arena keeps its floor to fight on)
            if (line) return L.type == Room.Type.ARENA ? new int[]{2, 2} : new int[]{1, 1};
            return switch (L.type) {
                // a friendly floor has no hubs or arenas (FloorBuilder.friendlyRooms): the spirit's square is its great place
                case HUB, ARENA, CAVERN, SPIRIT -> new int[]{2, 2};
                case HALL -> Hash.unit(seed, id, 13) < 0.5 ? new int[]{1, 2} : new int[]{2, 1};
                default -> new int[]{1, 1};
            };
        }

        void kinds() {
            for (int w : chain.links.keySet()) kinds.put(w, Kind.LINK);
            int biggest = -1, area = 0;
            for (int w : chain.places) {
                Room r = p.world(w);
                boolean built = false;
                for (int i = 0; i < p.host.length; i++) built |= p.host[i] == w && WorldPlan.built(p.room(i));
                Kind k = built ? Kind.BUILT : w == p.host[p.logic.start] ? Kind.ARRIVAL : w == p.host[p.logic.end] ? Kind.END : Kind.CAVERN;
                kinds.put(w, k);
                if (k == Kind.CAVERN && r.gw * r.gh > area && !p.sealable(r)) {
                    area = r.gw * r.gh;
                    biggest = w;
                }
            }
            // the deep cavern: the biggest calm one on the way, where the world's hero piece stands
            if (biggest >= 0 && area >= 4) {
                great = biggest;
                kinds.put(great, Kind.GREAT);
            }
            for (int w : chain.places) {
                Room r = p.world(w);
                Kind k = kinds.get(w);
                if (k == Kind.BUILT) continue;
                shapes.put(w, PassageEngine.Shape.of(r, r.gw == r.gh && !hex && !square));
                int h = roof + (r.gw + r.gh - 2) * 4;
                if (k == Kind.GREAT) h = Math.max(h + 8, tower);
                if (k == Kind.ARRIVAL) h = Math.max(7, roof - 2);
                if (sea) h = sky;
                if (open) h = lid;
                roofs.put(w, h);
                r.height = Math.min(h + 4, 120);
                if (k == Kind.ARRIVAL) {
                    int back = back(r);
                    p.anchor(w, r.centerX() + GatePainter.DX[back] * 2, r.centerZ() + GatePainter.DZ[back] * 2);
                }
            }
        }

        /** The side of a place away from its first doorway. */
        static int back(Room r) {
            return r.doors.isEmpty() ? 0 : (r.doors.get(0).side + 2) % 4;
        }

        /**
         * A secret world of the theme (a recipe secret that {@code leads_to} one), hidden off a calm cavern: an extra
         * place two slots square where there's room, else one, joined by a narrow way the dress hides.
         */
        void secretPlace() {
            if (world == null) return;
            String to = "";
            for (GateWorld.Secret sc : world.dressing().secrets()) if (!sc.leadsTo().isEmpty()) to = sc.leadsTo();
            if (to.isEmpty()) return;
            List<Integer> calm = new ArrayList<>();
            for (int w : chain.places) {
                Kind k = kinds.get(w);
                if ((k == Kind.CAVERN || k == Kind.GREAT) && !p.sealable(p.world(w))) calm.add(w);
            }
            calm.sort((a, b) -> Long.compare(Hash.of(seed, a, 307), Hash.of(seed, b, 307)));
            for (int sz = 2; sz >= 1; sz--) {
                for (int w : calm) {
                    Room r = p.world(w);
                    for (int side = 0; side < 4; side++) {
                        int[] at = beside(r, side, sz);
                        if (at == null || !p.free(at[0], at[1], sz, sz)) continue;
                        // the way in is one slot: through the side's middle slot
                        int sid = p.extra(at[0], at[1], sz, sz, 0);
                        if (!p.join(w, sid)) continue;
                        secret = sid;
                        kinds.put(sid, Kind.SECRET);
                        Room sr = p.world(sid);
                        shapes.put(sid, PassageEngine.Shape.of(sr, true));
                        // under open land the secret room is dug into the ground, its dome kept under the land's top
                        int h = open ? Math.min(roof + sz * 4, lid - 4) : roof + sz * 4;
                        roofs.put(sid, h);
                        sr.height = Math.min(h + 4, 120);
                        p.placeWorlds.put(sid, to);
                        GateWorld inner = dev.awaken.gates.worlds.GateWorlds.get(to);
                        if (inner != null && !inner.biome().isEmpty()) p.placeBiomes.put(sid, inner.biome());
                        return;
                    }
                }
            }
        }

        /** The slots beside a place's side for a square of {@code sz}, touching its edge: x, z (inside the margin), or null. */
        int[] beside(Room r, int side, int sz) {
            int x, z;
            switch (side) {
                case 0 -> { x = r.gx; z = r.gz - sz; }
                case 2 -> { x = r.gx; z = r.gz + r.gh; }
                case 1 -> { x = r.gx + r.gw; z = r.gz; }
                default -> { x = r.gx - sz; z = r.gz; }
            }
            if (x < chain.lo || z < chain.lo || x + sz - 1 > chain.hi || z + sz - 1 > chain.hi) return null;
            return new int[]{x, z};
        }

        // ---- the shape ----------------------------------------------------------------------------------------------

        /** A place's plan distance at a point (negative inside), its walls made ragged by the world's noise. */
        double d(int id, double x, double z) {
            PassageEngine.Shape sh = shapes.get(id);
            if (sh == null) return 99;
            double base = hex ? hexD(sh, x, z) : sh.d(x, z);
            return base + (Hash.noise(seed + id, x, z, 6.5) - 0.5) * 2 * wobble;
        }

        /** A rounded hexagon's distance (the comb), flat sides north and south. */
        static double hexD(PassageEngine.Shape sh, double x, double z) {
            double u = Math.abs(x - sh.cx()), v = Math.abs(z - sh.cz());
            double r = Math.min(sh.a(), sh.b());
            double q = Math.max(v, u * 0.866 + v * 0.5);
            return q - r * 0.92;
        }

        /** How far into its middle (0 at the middle, 1 at the wall). */
        double q(int id, double x, double z) {
            PassageEngine.Shape sh = shapes.get(id);
            return sh == null ? 1 : sh.q(x, z);
        }

        /** A cavern's roof over a point: low at its walls, its full height in the middle, a little uneven. */
        double ceil(int id, double x, double z) {
            if (open && kinds.get(id) != Kind.SECRET) return lid;
            int h = roofs.getOrDefault(id, roof);
            double q = Math.min(1, q(id, x, z));
            double dome = 3.5 + (h - 3.5) * Math.sqrt(Math.max(0, 1 - q * q)) + (Hash.noise(seed + id * 7L, x, z, 4.5) - 0.5) * 3;
            if (relief == null) return dome;
            // shaped places (38.14): the roof swells and dips away from the walls, a chimney rising here and there
            double swell = (Hash.noise(seed + id * 11L, x, z, 9) - 0.5) * 15 * Math.min(1, (1 - q) * 3);
            double ch = Hash.noise(seed + id * 13L, x, z, 5);
            double chimney = ch > 0.67 && q < 0.8 ? Relief.smooth(0.67, 0.8, ch) * CHIMNEY : 0;
            return Math.max(3.5, dome + swell + chimney) + Math.max(0, Relief.terrace(relief.seed, q, x, z));
        }

        /**
         * How a shaped place's wall changes as it rises (38.14), blocks it's worn back (below 0, bulging in): lumps over
         * every face, taller than wide, bigger the higher (the theme and the world say how big: {@link Relief#lumpsK}); where the wall
         * stands tall, a lean in or out as it rises and here and there a ledge where it steps back. All of it starts 2.5
         * over the highest ground or piece standing within 3 ({@link Relief#near}), past a jump, so no wall is a stair to
         * its roof. Under the roof it bulges a block down at most (headroom stays over 2), and it's never worn back nearer
         * than 2.5 to the place's box, so the rock between two places stays whole.
         */
        double wall(double x, double y, double z, double h, double dd, boolean under, int x0, int z0, int x1, int z1) {
            double hb = h - relief.near((int) Math.floor(x), (int) Math.floor(z));
            if (hb < 2.5) return 0;
            double push = (Hash.noise3(seed + 51, x, y * 0.6, z, 5) - 0.5) * 2.6 * relief.lumpsK * Relief.smooth(2.5, 5, hb);
            if (!under && dd > -4 && dd < 3) {
                push += Relief.clamp((Hash.noise(seed + 53, x, z, 16) - 0.42) * 0.5 * Math.max(0, hb - 3), -2.2, 2.2);
                double band = 5 + Hash.noise(seed + 55, x, z, 11) * 4;
                push += 1.6 * Relief.smooth(0.5, 0.6, Hash.noise(seed + 57, x, z, 9) * relief.ledgesK) * Relief.smooth(band - 0.5, band + 0.5, hb);
            }
            if (push > 0) push = Math.min(push, Math.min(2.9, Math.max(0, Math.min(Math.min(x - x0, x1 + 1 - x), Math.min(z - z0, z1 + 1 - z)) - 2.5)));
            return Math.max(under ? -1 : -2.5, push);
        }

        /** How high a shaped place's chimneys rise over its roof. */
        static final int CHIMNEY = 12;

        /**
         * How far a shaped place's wall is worn back at its foot (38.14): a groove a block high along the ground beside it
         * (its height where you stand next to it), so no wall rises sheer from the floor. {@code dd} is the plan distance
         * there (the groove runs where it's under two blocks into the wall).
         */
        double foot(double x, double z, double h, double dd) {
            if (relief == null || dd < 0 || dd > 2) return 0;
            int ix = (int) Math.floor(x), iz = (int) Math.floor(z), base = 0;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) base = Math.max(base, relief.stand(ix + dx, iz + dz));
            if (h < base || h >= base + 1) return 0;
            return 0.9 + 0.8 * Hash.noise(seed + 91, x, z, 5);
        }

        /** Inside a cavern's lake (the big ones' middles, away from the doors). */
        boolean inLake(int id, double x, double z) {
            if (!lakes || sea) return false;
            Kind k = kinds.get(id);
            Room r = p.world(id);
            if (k != Kind.GREAT && !(k == Kind.CAVERN && r.gw * r.gh >= 4 && !p.sealable(r))) return false;
            return q(id, x, z) + (Hash.noise(seed, x, z, 5) - 0.5) * 0.12 < 0.5;
        }

        // ---- the carve ----------------------------------------------------------------------------------------------

        void sculpt() {
            s = new Sculpt(this::skin, c.plot().minY(), c.plot().maxY());
            boolean v7 = c.plan().contentVersion() >= 7;
            s.thinOpen = v7;
            // the open lands, islands, seas, plains and halls keep their own ground (Stage 2 shapes those)
            if (v7 && dress.depth() && !open && !isles && !sea && !plain && !line) relief = new Relief(this);
            if (open && !isles) land();
            if (plain) plain();
            if (cloud != null) {
                // a sea of cloud under the floor: you fall through it (the catch has you long before)
                s.op(0, W - clouds, 0, Plots.SIDE - 1, W - clouds, Plots.SIDE - 1, (b, x, y, z) -> {
                    if (b.get(x, y, z) == Sculpt.UNSET) b.set(x, y, z, cloud);
                });
            }
            if (sea && open) {
                // an open lagoon: water from its bed to under the floor across the plot but its margin, the sky over it
                int x0 = 8, x1 = 247;
                s.op(x0, W - DEEP + 1, x0, x1, W + SKY_MAX + 2, x1, (b, x, y, z) -> b.mark(x, y, z, Sculpt.OPEN));
                s.fillOpen(x0, W - DEEP + 1, x0, x1, W - 1, x1, (x, y, z) -> -1, fluid);
                waters.add(new FloorAir.Water(-1, x0, x0, x1, x1));
            } else if (sea) {
                // the sea: open from its bed to the roof over the whole plot but its margin, filled to just under the floor
                int x0 = 8, x1 = 247;
                s.carve(x0, W - DEEP + 1, x0, x1, W + sky + 4, x1, (x, y, z) -> {
                    double edge = Math.max(Math.max(x0 - x, x - x1 - 1), Math.max(x0 - z, z - x1 - 1));
                    double top = W + sky * (0.75 + 0.25 * Hash.noise(seed, x, z, 24)) + (Hash.noise(seed, x, z, 7) - 0.5) * 6;
                    return Math.max(edge, Math.max(W - DEEP + 1 - y, y - top));
                });
                s.fillOpen(x0, W - DEEP + 1, x0, x1, W - 1, x1, (x, y, z) -> -1, fluid);
                waters.add(new FloorAir.Water(-1, x0, x0, x1, x1));
            }
            for (var e : kinds.entrySet()) {
                Room r = p.world(e.getKey());
                switch (e.getValue()) {
                    case LINK -> tunnel(r, chain.links.get(e.getKey()));
                    case BUILT -> {
                        if (sea) s.box(r.gx * 16 - 1, W - DEEP, r.gz * 16 - 1, (r.gx + r.gw) * 16, W + 1, (r.gz + r.gh) * 16, wall);
                        // a built room in the air stands on a slab of rock
                        if (isles) s.box(r.gx * 16, W - 4, r.gz * 16, (r.gx + r.gw) * 16 - 1, W - 1, (r.gz + r.gh) * 16 - 1, wall);
                    }
                    default -> cavern(r, e.getValue());
                }
            }
            if (open && deep > 0 && !sea && !isles) streets();
            p.thresholds(s, W, floor, trim);
            if (chain.fold != null) {
                int[] f = chain.fold;
                p.mark(FloorLayout.Mark.FOLD, "", f[0] * 16, 0, f[2] * 16, f[1] * 16 + 15, HALL_H - 1, f[2] * 16 + 15);
            }
            dress.world();
            for (var e : kinds.entrySet()) {
                Room r = p.world(e.getKey());
                switch (e.getValue()) {
                    case LINK -> dress.tunnel(r, chain.links.get(e.getKey()));
                    case BUILT -> {}
                    case SECRET -> dress.secret(r);
                    default -> dress.cavern(r, e.getValue());
                }
            }
            if (relief != null) {
                relief.apply();
                new Formations(this).place();
            }
            glow();
            p.doorLights(s, W, light);
            if (chain.fold != null) {
                // a doorway's light is a block off the hall's middle line; the fold's slots get its twin, so each slot is
                // the same turned round
                for (int gx = chain.fold[0]; gx <= chain.fold[1]; gx++) {
                    int x0 = gx * 16, z0 = chain.fold[2] * 16;
                    s.putOpen(x0, W + 1, z0 + 8, light);
                    s.putOpen(x0 + 15, W + 1, z0 + 8, light);
                }
            }
            p.screens(s, W, dress::screen, open ? W + lid - 1 : s.yHi);
            p.handbells(s);
            if (mirror) mirrorBelow();
            if (open) lid();
            s.seal();
        }

        /**
         * An open land's ground (38.11): solid from under the floor up to the dress's skyline over the whole plot, never
         * lower than one under the lid, so nobody stands on a roof under it. A little open air over the top, so the skin
         * knows the top for a floor (sand, tiles, roofs).
         */
        void land() {
            // down past a lagoon's bed, so its margin and bed hold the water
            int bottom = W - Math.max(4 + deep, sea ? DEEP + 2 : 0);
            s.op(0, bottom, 0, Plots.SIDE - 1, W + SKY_MAX + 2, Plots.SIDE - 1, (b, x, y, z) -> {
                int top = W + Math.min(SKY_MAX, Math.max(lid - 1, dress.skyline(x, z)));
                if (y <= top) b.mark(x, y, z, Sculpt.SOLID);
                else if (y <= top + 2) b.mark(x, y, z, Sculpt.OPEN);
            });
        }

        /**
         * An endless plain (38.12): one floor three deep over the whole plot and nothing walled; a wall of the floor's own
         * stuff stands round the plot's edge, the fog's colour, where nobody comes (the live layer turns you round first).
         * The air over it is open up to {@code PLAIN_H}, so the dress can stand things on it.
         */
        void plain() {
            s.op(0, W - 3, 0, Plots.SIDE - 1, W + 40, Plots.SIDE - 1, (b, x, y, z) -> {
                boolean edge = x == 0 || z == 0 || x == Plots.SIDE - 1 || z == Plots.SIDE - 1;
                if (y < W || edge) b.mark(x, y, z, Sculpt.SOLID);
                else if (y < W + PLAIN_H) b.mark(x, y, z, Sculpt.OPEN);
            });
        }

        /** How high a plain's air is open (an arena's screens stand this high on it). */
        static final int PLAIN_H = 14;

        /**
         * The world again under its glass floor (38.12, the Inverted Palace): every block above the floor reflected about
         * it, so you look down through the glass into the same rooms upside down. Run last, so it reflects the dress.
         */
        void mirrorBelow() {
            int depth = Math.min(W - c.plot().minY() - 2, 34);
            BlockState glass = dress.underGlass();
            s.op(0, W - 1 - depth, 0, Plots.SIDE - 1, W - 1, Plots.SIDE - 1, (b, x, y, z) -> {
                if (y == W - 1) {
                    if (b.open(x, W, z) && glass != null) b.set(x, y, z, glass);
                    return;
                }
                int sy = 2 * (W - 1) - y;
                short v = b.get(x, sy, z);
                if (v == Sculpt.UNSET) return;
                BlockState st = b.state(x, sy, z);
                if (st != null) b.set(x, y, z, st);
                else b.mark(x, y, z, v);
            });
        }

        /**
         * Rooftops (38.11): streets five wide every 22 blocks cut {@code deep} down through the land between the places
         * and open to the sky. Places and links keep their ground (the links become bridges), and where a street passes
         * the edge of one a rail stands along it.
         */
        void streets() {
            byte[] keep = new byte[Plots.SIDE * Plots.SIDE];
            for (var e : kinds.entrySet()) {
                Room r = p.world(e.getKey());
                Kind k = e.getValue();
                int x0 = r.gx * 16, z0 = r.gz * 16, x1 = x0 + r.gw * 16 - 1, z1 = z0 + r.gh * 16 - 1;
                ChainLayout.Way way = k == Kind.LINK ? ChainLayout.Way.of(r, chain.links.get(r.id), seed) : null;
                PassageEngine.Shape sh = shapes.get(r.id);
                for (int z = Math.max(0, z0 - 1); z <= Math.min(Plots.SIDE - 1, z1 + 1); z++) {
                    for (int x = Math.max(0, x0 - 1); x <= Math.min(Plots.SIDE - 1, x1 + 1); x++) {
                        double q;
                        // a Rig room and the secret room (dug into the ground) keep their whole box
                        if (k == Kind.BUILT || k == Kind.SECRET) q = x < x0 || x > x1 || z < z0 || z > z1 ? 0.5 : -9;
                        else if (way != null) {
                            if (x < x0 || x > x1 || z < z0 || z > z1) continue;
                            double[] w = way.at(x + 0.5, z + 0.5);
                            q = w == null ? 99 : Math.abs(w[0]) - hw;
                        } else {
                            // a place keeps its ground and the mouth from each doorway in (cavern's), so no street
                            // cuts the way between a door and the place
                            q = d(r.id, x + 0.5, z + 0.5);
                            if (sh != null) for (Room.Door dr : r.doors) {
                                double ax = dr.centerX() + 0.5, az = dr.centerZ() + 0.5;
                                double bx = ax + (sh.cx() - ax) * 0.55, bz = az + (sh.cz() - az) * 0.55;
                                q = Math.min(q, Sculpt.capsule(x + 0.5, 0, z + 0.5, ax, 0, az, bx, 0, bz, 2.4));
                            }
                        }
                        int i = z * Plots.SIDE + x;
                        byte v = q < 0 ? (byte) 2 : q < 1.5 ? (byte) 1 : 0;
                        if (v > keep[i]) keep[i] = v;
                    }
                }
            }
            int ox = (int) Math.floorMod(seed, 22L), oz = (int) Math.floorMod(seed >> 8, 22L);
            BlockState rail = dress.rail();
            s.op(4, W - deep, 4, Plots.SIDE - 5, W + SKY_MAX + 2, Plots.SIDE - 5, (b, x, y, z) -> {
                if (Math.floorMod(x + ox, 22) >= 5 && Math.floorMod(z + oz, 22) >= 5) return;
                byte kp = keep[z * Plots.SIDE + x];
                if (y < W) {
                    if (kp == 0) b.mark(x, y, z, Sculpt.OPEN);
                    return;
                }
                if (kp == 2) return;
                if (kp == 1 && y == W) b.set(x, y, z, rail);
                else b.mark(x, y, z, Sculpt.OPEN);
            });
        }

        /** Opens a plan shape from the floor up through the land, its walls leaning back by the slope as they rise. */
        void openUp(int x0, int z0, int x1, int z1, java.util.function.DoubleBinaryOperator d2) {
            int lean = (int) Math.ceil(slope * (SKY_MAX + 2)) + 1;
            s.op(x0 - lean, W, z0 - lean, x1 + lean, W + SKY_MAX + 2, z1 + lean, (b, x, y, z) -> {
                if (d2.applyAsDouble(x + 0.5, z + 0.5) < (y - W) * slope) b.mark(x, y, z, Sculpt.OPEN);
            });
        }

        /** The lid: an invisible ceiling over the whole floor (light and the sky pass through it). */
        void lid() {
            BlockState barrier = Blocks.BARRIER.defaultBlockState();
            s.op(0, W + lid, 0, Plots.SIDE - 1, W + lid, Plots.SIDE - 1, (b, x, y, z) -> {
                if (b.get(x, y, z) == Sculpt.SOLID || b.state(x, y, z) != null) return;
                b.set(x, y, z, barrier);
            });
        }

        BlockState skin(int x, int y, int z, boolean fl, boolean ceil) {
            return dress.skin(x, y, z, fl, ceil);
        }

        /**
         * A cavern (or the sea's island): its plan made ragged, its roof a dome, a mouth from each doorway to its middle. In
         * an island world every place is an island, its secret place too (38.13): beside its host, their landings meeting
         * at the doorway they share.
         */
        void cavern(Room r, Kind k) {
            int id = r.id;
            int x0 = r.gx * 16, z0 = r.gz * 16, x1 = x0 + r.gw * 16 - 1, z1 = z0 + r.gh * 16 - 1;
            if (sea || isles) {
                island(r);
                return;
            }
            // a place on the plain is only where it is: nothing to cut (its secret is a room standing on it)
            if (plain && k != Kind.SECRET) return;
            if (open && k != Kind.SECRET) {
                // a place under the sky: open from its floor up, a mouth from each doorway to its middle
                openUp(x0, z0, x1, z1, (x, z) -> d(id, x, z));
                PassageEngine.Shape sh = shapes.get(id);
                for (Room.Door dr : r.doors) {
                    double ax = dr.centerX() + 0.5, az = dr.centerZ() + 0.5;
                    double bx = ax + (sh.cx() - ax) * 0.55, bz = az + (sh.cz() - az) * 0.55;
                    openUp((int) Math.min(ax, bx) - 3, (int) Math.min(az, bz) - 3, (int) Math.max(ax, bx) + 3, (int) Math.max(az, bz) + 3,
                            (x, z) -> Sculpt.capsule(x, 0, z, ax, 0, az, bx, 0, bz, 2.4));
                }
                if (inLakeAny(id)) lake(r);
                return;
            }
            int top = W + roofs.get(id) + 3 + (relief == null ? 0 : CHIMNEY + 4);
            s.carve(x0, W, z0, x1, top, z1, (x, y, z) -> {
                double h = y - W, dd = d(id, x, z), wall = dd - foot(x, z, h, dd), roof = h - ceil(id, x, z);
                double f = Math.max(wall, Math.max(-h, roof));
                return relief == null || f < -3 || f > 3 ? f : f - wall(x, y, z, h, dd, roof > wall, x0, z0, x1, z1);
            });
            // a mouth from each doorway well in, whatever the ragged wall does there
            PassageEngine.Shape sh = shapes.get(id);
            for (Room.Door dr : r.doors) {
                double ax = dr.centerX() + 0.5, az = dr.centerZ() + 0.5;
                double bx = ax + (sh.cx() - ax) * 0.55, bz = az + (sh.cz() - az) * 0.55;
                int lx = (int) Math.floor(Math.min(ax, bx)) - 3, hx = (int) Math.ceil(Math.max(ax, bx)) + 3;
                int lz = (int) Math.floor(Math.min(az, bz)) - 3, hz = (int) Math.ceil(Math.max(az, bz)) + 3;
                s.carve(lx, W, lz, hx, W + 5, hz, (x, y, z) -> Math.max(
                        Sculpt.capsule(x, y, z, ax, W + 1.6, az, bx, W + 1.6, bz, 2.4), W - y));
            }
            if (inLakeAny(id)) lake(r);
        }

        boolean inLakeAny(int id) {
            PassageEngine.Shape sh = shapes.get(id);
            return sh != null && inLake(id, sh.cx(), sh.cz());
        }

        /** A lake in a cavern's middle: three deep, its surface level with the floor so you can wade out of it. */
        void lake(Room r) {
            int id = r.id;
            int x0 = r.gx * 16, z0 = r.gz * 16, x1 = x0 + r.gw * 16 - 1, z1 = z0 + r.gh * 16 - 1;
            // open only from three under the floor to the floor (the carve's shell reaches past its box: unbounded, the
            // lake's bed would open too and the water stand on air)
            s.carve(x0, W - 3, z0, x1, W - 1, z1, (x, y, z) -> inLake(id, x, z) ? Math.max(W - 3 - y, y - W) : 1);
            s.fillOpen(x0, W - 3, z0, x1, W - 1, z1, (x, y, z) -> inLake(id, x, z) ? -1 : 1, fluid);
            PassageEngine.Shape sh = shapes.get(id);
            int ra = (int) (sh.a() * 0.5), rb = (int) (sh.b() * 0.5);
            if (waters.size() < FloorAir.MAX_WATERS)
                waters.add(new FloorAir.Water(-1, (int) sh.cx() - ra, (int) sh.cz() - rb, (int) sh.cx() + ra, (int) sh.cz() + rb));
        }

        /** A hall's blocks across its slot (38.12): six wide, round the slot's middle line like a doorway; five high. */
        static final int HALL_LO = 5, HALL_HI = 10, HALL_H = 5;

        /**
         * A hall's way across its slot (38.12, the line): straight from doorway to doorway, or round the corner through the
         * slot's middle, six across and five high, square-cut like a house's. Each slot's the same turned round about its
         * middle, so the fold reads the same whichever way you face.
         */
        void hall(Room r, ChainLayout.Link l) {
            int ox = r.gx * 16, oz = r.gz * 16;
            for (int side : new int[]{l.in(), l.out()}) {
                int x0 = ox + HALL_LO, x1 = ox + HALL_HI, z0 = oz + HALL_LO, z1 = oz + HALL_HI;
                switch (side) {
                    case 0 -> z0 = oz;
                    case 1 -> x1 = ox + 15;
                    case 2 -> z1 = oz + 15;
                    default -> x0 = ox;
                }
                int fx0 = x0, fx1 = x1, fz0 = z0, fz1 = z1;
                s.carve(fx0, W, fz0, fx1, W + HALL_H - 1, fz1, (x, y, z) -> Sculpt.box(x, y, z, fx0, W, fz0, fx1 + 1, W + HALL_H, fz1 + 1));
            }
        }

        /** Whether a link is one of the fold's three slots. */
        boolean folded(Room r) {
            int[] f = chain.fold;
            return f != null && r.gz == f[2] && r.gx >= f[0] && r.gx <= f[1];
        }

        /** An island in the sea: a ragged slab from the bed to the surface, its top the floor. */
        void island(Room r) {
            int id = r.id;
            int x0 = r.gx * 16, z0 = r.gz * 16, x1 = x0 + r.gw * 16 - 1, z1 = z0 + r.gh * 16 - 1;
            if (isles) {
                // an island in the air: its floor the place's plan, its rock narrowing to a ragged point {@code under} below
                double narrow = Math.min(r.gw, r.gh) * 8;
                s.op(x0, W - under, z0, x1, W - 1, z1, (b, x, y, z) -> {
                    double t = (W - 1 - y) / (double) under;
                    double in = t * t * narrow + (Hash.noise(seed + id, x, z, 5) - 0.5) * 6 * t;
                    if (d(id, x + 0.5, z + 0.5) + in < 0) b.mark(x, y, z, Sculpt.SOLID);
                });
                // the air over it is open, so the dress can stand things on it
                int h = Math.min(roofs.getOrDefault(id, roof), 24);
                s.op(x0, W, z0, x1, W + h, z1, (b, x, y, z) -> {
                    if (d(id, x + 0.5, z + 0.5) < 0 && b.get(x, y, z) == Sculpt.UNSET) b.mark(x, y, z, Sculpt.OPEN);
                });
                for (Room.Door dr : r.doors) {
                    int[] box = WorldPlan.doorBox(dr, 7);
                    s.op(box[0], W - 2, box[2], box[1], W - 1, box[3], (b, x, y, z) -> {
                        if (insideSlots(r, x, z)) b.mark(x, y, z, Sculpt.SOLID);
                    });
                }
                return;
            }
            s.op(x0, W - DEEP, z0, x1, W - 1, z1, (b, x, y, z) -> {
                double under = (W - 1 - y) * 0.35;
                if (d(id, x + 0.5, z + 0.5) + under < 0) b.mark(x, y, z, Sculpt.SOLID);
            });
            // a landing from each doorway onto the island
            for (Room.Door dr : r.doors) {
                int[] box = WorldPlan.doorBox(dr, 7);
                s.op(box[0], W - 2, box[2], box[1], W - 1, box[3], (b, x, y, z) -> {
                    if (insideSlots(r, x, z)) b.mark(x, y, z, Sculpt.SOLID);
                });
            }
        }

        static boolean insideSlots(Room r, int x, int z) {
            return x >= r.gx * 16 && x < (r.gx + r.gw) * 16 && z >= r.gz * 16 && z < (r.gz + r.gh) * 16;
        }

        /** A tunnel winding across its slot from doorway to doorway, arched, a little wider where it turns. */
        void tunnel(Room r, ChainLayout.Link l) {
            int ox = r.gx * 16, oz = r.gz * 16;
            if (plain) return;
            if (line) {
                hall(r, l);
                return;
            }
            ChainLayout.Way way = ChainLayout.Way.of(r, l, seed);
            if (isles) {
                // a bridge at the floor's level on a beam, a rail along each side
                BlockState rail = dress.rail();
                s.op(ox, W - 2, oz, ox + 15, W, oz + 15, (b, x, y, z) -> {
                    double[] w = way.at(x + 0.5, z + 0.5);
                    if (w == null) return;
                    double lat = Math.abs(w[0]);
                    if (y == W - 1 && lat < hw + 1.2) b.mark(x, y, z, Sculpt.SOLID);
                    else if (y == W - 2 && lat < 1.2) b.mark(x, y, z, Sculpt.SOLID);
                    else if (y == W && lat >= hw + 0.2 && lat < hw + 1.2) b.set(x, y, z, rail);
                });
                return;
            }
            if (sea) {
                // a causeway at the surface
                s.op(ox, W - DEEP, oz, ox + 15, W - 1, oz + 15, (b, x, y, z) -> {
                    double[] w = way.at(x + 0.5, z + 0.5);
                    if (w == null) return;
                    double lat = Math.abs(w[0]) + (W - 1 - y) * 0.3;
                    if (lat < hw + 0.2) b.mark(x, y, z, Sculpt.SOLID);
                });
                return;
            }
            if (open) {
                // a street or a cut between dunes, open to the sky
                openUp(ox, oz, ox + 15, oz + 15, (x, z) -> {
                    double[] w = way.at(x, z);
                    return w == null ? 99 : Math.abs(w[0]) - hw - (Hash.noise(seed, x, z, 4) - 0.5) * 0.8;
                });
                return;
            }
            int top = W + 9;
            s.carve(ox, W, oz, ox + 15, top, oz + 15, (x, y, z) -> {
                double[] w = way.at(x, z);
                if (w == null) return 99;
                double lat = Math.abs(w[0]);
                double half = hw + (Hash.noise(seed, x, z, 4) - 0.5) * 0.8;
                double h = y - W, arch = 4.2 + Math.max(0, half * half - lat * lat) * 0.35;
                // over shaped ground the arch rises with the floor, and vaults and dips along the way
                if (relief != null) arch += Math.max(0, relief.stand((int) Math.floor(x), (int) Math.floor(z)))
                        + Math.max(-0.7, (Hash.noise(seed + 33, x, z, 7) - 0.5) * 6);
                return Math.max(lat - half - foot(x, z, h, lat - half), Math.max(-h, h - arch));
            });
        }

        /**
         * Hidden light three apart at head height over every place and along every tunnel (four leaves the ragged walls'
         * pockets and the diagonal tunnels dark, since light spreads block by block); where something stands there, on
         * top of it.
         */
        void glow() {
            for (var e : kinds.entrySet()) {
                Room r = p.world(e.getKey());
                Kind k = e.getValue();
                if (k == Kind.BUILT) continue;
                // under a day sky the sun lights the way; at night (and in a covered secret place) the hidden light does
                if (open && !dark && k != Kind.SECRET) continue;
                int x0 = r.gx * 16, z0 = r.gz * 16, x1 = x0 + r.gw * 16 - 1, z1 = z0 + r.gh * 16 - 1;
                if (k == Kind.LINK && line) {
                    // a hall's light at head height three apart down its middle, a block either side of the line so each
                    // slot is the same turned round (the fold reads the same both ways)
                    for (int[] at : new int[][]{{x0 + 1, z0 + 7}, {x0 + 4, z0 + 7}, {x0 + 7, z0 + 7}, {x0 + 8, z0 + 8}, {x0 + 11, z0 + 8}, {x0 + 14, z0 + 8}})
                        s.op(at[0], W + 1, at[1], at[0], W + 1, at[1], (b, x, y, z) -> {
                            if (b.open(x, y, z)) b.set(x, y, z, light);
                        });
                    continue;
                }
                // a plain's ways are open floor between its places
                if (k == Kind.LINK && plain) continue;
                if (k == Kind.LINK && isles) {
                    // a bridge's air is the sky's (never opened): the light stands over its deck three apart
                    ChainLayout.Way way = ChainLayout.Way.of(r, chain.links.get(r.id), seed);
                    s.op(x0, W + 1, z0, x1, W + 1, z1, (b, x, y, z) -> {
                        double[] w = way.at(x + 0.5, z + 0.5);
                        if (w == null || Math.abs(w[0]) > 1.6 || Math.floorMod((int) Math.round(w[1]), 3) != 1) return;
                        if (b.get(x, y, z) == Sculpt.UNSET && b.get(x, W, z) == Sculpt.UNSET && b.get(x, W - 1, z) != Sculpt.UNSET) b.set(x, y, z, light);
                    });
                    continue;
                }
                if (k == Kind.LINK) {
                    ChainLayout.Way way = ChainLayout.Way.of(r, chain.links.get(r.id), seed);
                    s.op(x0, W + 1, z0, x1, W + 1, z1, (b, x, y, z) -> {
                        double[] w = way.at(x + 0.5, z + 0.5);
                        if (w == null || Math.abs(w[0]) > 1.6 || Math.floorMod((int) Math.round(w[1]), 3) != 1) return;
                        lightOver(b, x, relief == null ? y : y + relief.stand(x, z), z);
                    });
                    continue;
                }
                int id = r.id;
                int mx = (x0 + x1 + 1) / 2, mz = (z0 + z1 + 1) / 2;
                s.op(x0, W + 1, z0, x1, W + 1, z1, (b, x, y, z) -> {
                    if (Math.floorMod(x - mx, 3) != 0 || Math.floorMod(z - mz, 3) != 0) return;
                    double dd = d(id, x + 0.5, z + 0.5);
                    // over shaped ground at head height over where you stand, and out into the foot of a leaning wall
                    if (dd < (relief == null ? 0 : 2)) lightOver(b, x, relief == null ? y : y + Math.max(0, relief.stand(x, z)), z);
                    // an island's ragged rim: the light hangs in the air just off it, so the way round its edge isn't dark
                    else if (isles && dd < 2 && b.get(x, y, z) == Sculpt.UNSET) b.set(x, y, z, light);
                });
                // down each doorway's mouth too, which runs on past the ragged wall
                PassageEngine.Shape sh = shapes.get(id);
                if (sh == null) continue;
                for (Room.Door dr : r.doors) {
                    double ax = dr.centerX() + 0.5, az = dr.centerZ() + 0.5;
                    double vx = (sh.cx() - ax) * 0.55, vz = (sh.cz() - az) * 0.55, len = Math.hypot(vx, vz);
                    for (double t = 2.5; t < len; t += 3) {
                        int lx = (int) Math.floor(ax + vx * t / len), lz = (int) Math.floor(az + vz * t / len);
                        s.op(lx, W + 1, lz, lx, W + 1, lz, this::lightOver);
                    }
                }
            }
        }

        void lightOver(Sculpt.Buf b, int x, int y, int z) {
            for (int yy = y; yy <= y + 2; yy++) {
                if (b.open(x, yy, z)) {
                    b.set(x, yy, z, light);
                    return;
                }
            }
        }

        // ---- what the floor walks on and how its air glows ----------------------------------------------------------

        boolean walk(Room r, int x, int z) {
            Kind k = kinds.get(r.id);
            if (k == null || k == Kind.LINK || k == Kind.BUILT) return false;
            double px = x + 0.5, pz = z + 0.5;
            if (d(r.id, px, pz) > -2.2 || inLake(r.id, px, pz) || !dress.walk(r, x, z)) return false;
            for (double[] bl : blocked) if (Math.hypot(px - bl[0], pz - bl[1]) < bl[2] + 1.2) return false;
            // on shaped ground a spot (a spawn, a feature) is level across the three by three round it, on a whole block
            if (relief != null) {
                int g0 = relief.half(x, z);
                if ((g0 & 1) != 0) return false;
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (relief.half(x + dx, z + dz) != g0) return false;
            }
            return true;
        }

        FloorAir air() {
            List<FloorAir.Light> all = lights.size() > FloorAir.MAX_LIGHTS ? lights.subList(0, FloorAir.MAX_LIGHTS) : lights;
            return new FloorAir(new ArrayList<>(all), shafts, dress.mists(), waters);
        }
    }
}
