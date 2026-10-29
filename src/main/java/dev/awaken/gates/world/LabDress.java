package dev.awaken.gates.world;

import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The Precursor Lab (SPEC §38.12): white panels with strips of light, black frames, glass and green tanks, cables and work
 * lights. The Facility is labs of benches and tanks; the Server Abyss rows of humming racks round a core of light that
 * climbs out of sight; Containment rows of tanks, some empty and some broken; the Simulation a world drawn in lines that
 * fills itself in where you look (the live layer draws it); the Garden Dome a garden under a lit roof that nobody tends.
 */
final class LabDress extends Dress {
    final BlockState panel, lit, floorTile, frame, glass, rack, tank, cables, lamp, holo, moss, sketch, console, bars,
            moss2, grass, leaves, log;
    /** Shaped ground (SPEC §38.14): floor slabs and stairs, panel at the walls, moss drifted in the garden, screws */
    final BlockState gFloorSlab, gFloorStairs, gWallSlab, gWallStairs, gDrift, gBits;
    final BlockState[] flowers;
    final boolean facility, servers, containment, sim;
    private int[] secretBox;

    LabDress(CaveEngine.Cave c) {
        super(c);
        panel = mat("wall", "awaken:lab_panel");
        lit = mat("wall_glow", "awaken:lab_panel_lit");
        floorTile = mat("floor", "awaken:lab_floor");
        frame = mat("frame", "awaken:lab_frame");
        glass = mat("glass", "awaken:lab_glass");
        rack = mat("rack", "awaken:lab_rack");
        tank = mat("tank", "awaken:lab_tank");
        cables = mat("cables", "awaken:lab_cables");
        lamp = mat("lamp", "awaken:lab_light");
        holo = mat("holo", "awaken:lab_hologram");
        moss = mat("moss", "awaken:lab_moss");
        sketch = mat("sketch", "awaken:lab_sketch");
        console = mat("touch", "awaken:lab_console");
        gFloorSlab = mat("ground_slab", "awaken:lab_floor_slab");
        gFloorStairs = mat("ground_stairs", "awaken:lab_floor_stairs");
        gWallSlab = mat("wall_slab", "awaken:lab_panel_slab");
        gWallStairs = mat("wall_stairs", "awaken:lab_panel_stairs");
        gDrift = mat("ground_drift", "awaken:lab_moss_drift");
        gBits = mat("ground_bits", "awaken:lab_screws");
        bars = GatePainter.state("minecraft:iron_bars");
        moss2 = GatePainter.state("minecraft:moss_block");
        grass = GatePainter.state("minecraft:short_grass");
        leaves = GatePainter.state("minecraft:flowering_azalea_leaves[persistent=true]");
        log = GatePainter.state("minecraft:birch_log[axis=y]");
        flowers = new BlockState[]{GatePainter.state("minecraft:cornflower"), GatePainter.state("minecraft:oxeye_daisy"),
                GatePainter.state("minecraft:allium"), GatePainter.state("minecraft:azure_bluet"), grass, grass};
        String n = name();
        facility = n.equals("facility");
        servers = n.equals("server_abyss");
        containment = n.equals("containment");
        sim = n.equals("simulation");
    }

    @Override
    int glow() {
        return 0x80e8ff;
    }

    @Override
    BlockState rail() {
        return bars;
    }

    boolean inSecret(int x, int z) {
        if (secretBox == null) {
            if (c.secret < 0) secretBox = new int[]{1, 1, 0, 0};
            else {
                int[] b = box(p.world(c.secret));
                secretBox = new int[]{b[0] - 3, b[1] - 3, b[2] + 3, b[3] + 3};
            }
        }
        return x >= secretBox[0] && x <= secretBox[2] && z >= secretBox[1] && z <= secretBox[3];
    }

    // ---- the skin -----------------------------------------------------------------------------------------------------

    @Override
    BlockState skin(int x, int y, int z, boolean fl, boolean ceiling) {
        boolean garden = inSecret(x, z);
        // the Simulation is lines all through until you look at it; its garden is as real as the lab's
        if (sim && !garden) return sketch;
        int rise = y - W;
        if (garden) {
            if (fl) return moss2;
            if (ceiling) return Math.floorMod(x, 5) == 0 || Math.floorMod(z, 5) == 0 ? frame : lit;
        }
        if (fl) return Math.floorMod(x, 6) == 0 || Math.floorMod(z, 6) == 0 ? frame : floorTile;
        if (ceiling) return Math.floorMod(x, 4) == 0 && Math.floorMod(z, 4) == 0 ? lit : panel;
        if (rise == 0) return frame;
        if (rise == 3 && Math.floorMod(x + z, 4) != 0) return lit;
        if (Math.floorMod(x + z, 8) == 0) return frame;
        return panel;
    }

    @Override
    boolean depth() {
        return !sim;
    }

    @Override
    BlockState ground(int part, int x, int y, int z) {
        return kit(part, inSecret(x, z), gFloorSlab, gFloorStairs, gWallSlab, gWallStairs, gDrift, gBits);
    }

    @Override
    double bits() {
        // screws and wires lie thick
        return 0.3;
    }

    @Override
    BlockState creep(int x, int y, int z) {
        // moss creeping in from the garden
        return moss2;
    }

    @Override
    BlockState formation(int part, int x, int y, int z) {
        // frames floor to roof banded with light, racks, a fallen frame, a heap of panels
        return pieces(part, x, y, z, frame, lit, rack, lit, frame, panel);
    }

    @Override
    BlockState screen(int lat, int rise, int side) {
        if (sim) return sketch;
        if (lat % 4 == 0 || rise == 0) return frame;
        return rise % 4 == 3 ? lit : glass;
    }

    @Override
    List<FloorAir.Mist> mists() {
        if (sim) return mist(0x0a2040, 0.35f, 0x061430, 0.2f);
        if (containment) return mist(0x1a4028, 0.4f, 0x0e2418, 0.22f);
        return mist(0x3a4a58, 0.3f, 0x1a2430, 0.15f);
    }

    @Override
    boolean walk(Room r, int x, int z) {
        // a rack's row or a tank stands there
        if (servers && rackRoom(r)) return !rackRow(r, x, z);
        if ((containment && tankRoom(r)) || (facility && c.kinds.get(r.id) == CaveEngine.Kind.GREAT && hero("tanks"))) return !tankAt(r, x, z);
        return true;
    }

    // ---- places -------------------------------------------------------------------------------------------------------

    boolean rackRoom(Room r) {
        CaveEngine.Kind k = c.kinds.get(r.id);
        return k == CaveEngine.Kind.CAVERN && !p.sealable(r) && r.type != Room.Type.INNER_GATE && hero("server_racks");
    }

    /** A server row: lines along x every four blocks, broken every ten for an aisle, a few in from the walls. */
    boolean rackRow(Room r, int x, int z) {
        int[] b = box(r);
        if (Math.floorMod(z - b[1], 4) != 2 || Math.floorMod(x - b[0], 10) < 2) return false;
        double px = x + 0.5, pz = z + 0.5;
        return c.d(r.id, px, pz) < -3 && !nearDoor(r, px, pz, 3.5) && !p.nearAnchor(px, pz, 3);
    }

    boolean tankRoom(Room r) {
        CaveEngine.Kind k = c.kinds.get(r.id);
        return (k == CaveEngine.Kind.CAVERN || k == CaveEngine.Kind.GREAT) && !p.sealable(r) && r.type != Room.Type.INNER_GATE
                && hero("containment_tanks");
    }

    /** A tank's place: every fifth block each way, a few in from the walls. */
    boolean tankAt(Room r, int x, int z) {
        int[] b = box(r);
        if (Math.floorMod(x - b[0], 5) != 2 || Math.floorMod(z - b[1], 5) != 2) return false;
        double px = x + 0.5, pz = z + 0.5;
        return c.d(r.id, px, pz) < -3 && !nearDoor(r, px, pz, 3.5) && !p.nearAnchor(px, pz, 3);
    }

    @Override
    void cavern(Room r, CaveEngine.Kind k) {
        int id = r.id;
        PassageEngine.Shape sh = c.shapes.get(id);
        int[] b = box(r);
        int roof = c.roofs.getOrDefault(id, c.roof);
        if (sim) {
            if (hero("sketch_world")) drawn(r);
        } else {
            hang(r, 0.025, 4, 4, 11, (x, y, z, len, i) -> i == len - 1 && len > 2 ? lamp : cables);
        }
        if (facility && hero("lab_benches") && k != CaveEngine.Kind.ARRIVAL) benches(r);
        if (facility && hero("tanks") && k == CaveEngine.Kind.GREAT) tanks(r, 0.0);
        if (servers && rackRoom(r)) racks(r);
        if (servers && k == CaveEngine.Kind.GREAT && hero("the_core")) core(r, roof);
        if (containment && tankRoom(r)) tanks(r, 0.25);
        if (k != CaveEngine.Kind.ARRIVAL) touchstones(r, console, k == CaveEngine.Kind.GREAT ? 2 : 1, "the_first_login", 60);
        if (k == CaveEngine.Kind.GREAT && sh != null) {
            if (facility) wallNook(r, "the_logbook", 70);
            if (servers) wallNook(r, "the_cold_drive", 70);
            if (containment) wallNook(r, "the_empty_tank", 70);
            if (sim) {
                int[] at = wallNook(r, "the_unfinished", 70);
                // the corner nobody drew: its floor not even sketched
                if (at != null) c.s.op(at[0] - 1, W - 1, at[1] - 1, at[0] + 1, W - 1, at[1] + 1, (buf, x, y, z) -> buf.set(x, y, z, frame));
            }
        }
        if (sh != null) light(sh.cx(), 3, sh.cz(), sim ? 0x60c0ff : 0xd0f0ff, 10f + r.gw * 3, 0.55f);
        if (sh != null && !sim && b[2] - b[0] > 20) light(sh.cx(), roof - 1, sh.cz(), 0xe0f4ff, 14f, 0.4f);
    }

    /** Lab benches in rows: a frame top two long with a hologram flickering on it, a stool of frame beside. */
    void benches(Room r) {
        int id = r.id;
        int[] b = box(r);
        c.s.op(b[0], W, b[1], b[2], W + 1, b[3], (buf, x, y, z) -> {
            if (!buf.open(x, y, z)) return;
            int u = Math.floorMod(x - b[0], 7), v = Math.floorMod(z - b[1], 6);
            if (v != 3 || u < 2 || u > 3) return;
            double px = x + 0.5, pz = z + 0.5;
            if (c.d(id, px, pz) > -3 || nearDoor(r, px, pz, 3.5) || p.nearAnchor(px, pz, 3)) return;
            if (y == W) buf.set(x, y, z, frame);
            else if (u == 2 && Hash.unit(seed + 41, x, z) < 0.7) buf.set(x, y, z, holo);
        });
    }

    /**
     * Tanks every fifth block: a frame foot, three of green glass glowing, a frame cap; {@code broken} of them empty, their
     * glass gone and moss spilled round the foot.
     */
    void tanks(Room r, double broken) {
        int id = r.id;
        int[] b = box(r);
        c.s.op(b[0] - 1, W, b[1] - 1, b[2] + 1, W + 4, b[3] + 1, (buf, x, y, z) -> {
            if (!buf.open(x, y, z)) return;
            int u = Math.floorMod(x - b[0], 5), v = Math.floorMod(z - b[1], 5);
            int tx = x - u + 2, tz = z - v + 2;
            if (Math.abs(u - 2) > 1 || Math.abs(v - 2) > 1 || !tankAt(r, tx, tz)) return;
            boolean empty = Hash.unit(seed + 43, tx, tz) < broken;
            if (u == 2 && v == 2) {
                if (y == W || y == W + 4) buf.set(x, y, z, frame);
                else if (!empty) buf.set(x, y, z, tank);
            } else if (empty && y == W && Hash.unit(seed + 47, x, z) < 0.5) buf.set(x, y, z, moss);
        });
    }

    /** Racks of servers in rows four high, their lights blinking, cables hanging over the aisles. */
    void racks(Room r) {
        int[] b = box(r);
        c.s.op(b[0], W, b[1], b[2], W + 3, b[3], (buf, x, y, z) -> {
            if (buf.open(x, y, z) && rackRow(r, x, z)) buf.set(x, y, z, y == W + 3 ? frame : rack);
        });
    }

    /**
     * The Server Abyss's core: a column of green light two across from the floor out of sight, ringed with frame every six
     * blocks, cables hanging round it.
     */
    void core(Room r, int roof) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        double cx = sh.cx(), cz = sh.cz();
        int ix = (int) Math.floor(cx), iz = (int) Math.floor(cz);
        c.s.op(ix - 3, W, iz - 3, ix + 3, W + roof + 2, iz + 3, (b, x, y, z) -> {
            if (!b.open(x, y, z)) return;
            double d = Math.hypot(x + 0.5 - cx, z + 0.5 - cz);
            int rise = y - W;
            if (d < 1.6) b.set(x, y, z, tank);
            else if (d < 2.8 && rise % 6 == 0) b.set(x, y, z, frame);
        });
        block(cx, cz, 3.5);
        light(cx, 6, cz, 0x60ff90, 18f, 0.9f);
        c.shafts.add(new FloorAir.Shaft(ix, roof, iz, ix, 0, iz, 2.5f, 0x80ffa0, 0.6f));
    }

    /** The Simulation's things, drawn in the same lines: trees of a trunk and a ball, and boulders, standing on its floor. */
    void drawn(Room r) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        int id = r.id;
        int n = 2 + r.gw * r.gh;
        for (int i = 0; i < n; i++) {
            double[] at = spot(r, 100 + i * 3, 0.2, 0.75, 3);
            if (at == null) continue;
            double cx = at[0], cz = at[1];
            boolean tree = unit(id, 140 + i) < 0.6;
            int ix = (int) Math.floor(cx), iz = (int) Math.floor(cz);
            if (tree) {
                int h = 4 + (int) (unit(id, 150 + i) * 3);
                double cr = 2.4;
                c.s.op(ix - 3, W, iz - 3, ix + 3, W + h + 4, iz + 3, (b, x, y, z) -> {
                    if (!b.open(x, y, z)) return;
                    double dd = Math.sqrt(Math.pow(x + 0.5 - cx, 2) + Math.pow(y + 0.5 - (W + h + 1.5), 2) + Math.pow(z + 0.5 - cz, 2));
                    if ((x == ix && z == iz && y < W + h) || dd < cr) b.set(x, y, z, sketch);
                });
            } else {
                double cr = 1.8 + unit(id, 160 + i);
                c.s.op(ix - 3, W, iz - 3, ix + 3, W + 3, iz + 3, (b, x, y, z) -> {
                    if (!b.open(x, y, z)) return;
                    double dd = Math.sqrt(Math.pow(x + 0.5 - cx, 2) + Math.pow((y - W) * 1.4, 2) + Math.pow(z + 0.5 - cz, 2));
                    if (dd < cr) b.set(x, y, z, sketch);
                });
            }
            block(cx, cz, 2.5);
        }
    }

    @Override
    void tunnel(Room r, ChainLayout.Link l) {
        int[] b = box(r);
        ChainLayout.Way way = ChainLayout.Way.of(r, l, seed);
        if (sim) return;
        // ribs of frame across the corridor every six blocks, a light over every other
        c.s.op(b[0], W, b[1], b[2], W + 3, b[3], (buf, x, y, z) -> {
            if (!buf.open(x, y, z)) return;
            double[] w = way.at(x + 0.5, z + 0.5);
            if (w == null || Math.floorMod((int) Math.round(w[1]), 6) != 3 || nearDoor(r, x + 0.5, z + 0.5, 3)) return;
            double lat = Math.abs(w[0]), half = c.hw + 0.4;
            if (lat > half - 1.1 && lat <= half) buf.set(x, y, z, frame);
        });
        hang(r, 0.02, 3, 3, 17, (x, y, z, len, i) -> cables);
    }

    @Override
    void secret(Room r) {
        // the Garden Dome: moss underfoot and flowers in it, little trees of birch and flowering azalea, a hologram of a
        // sprout on a plinth in the middle where the last seed is kept
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        int id = r.id;
        double cx = sh.cx(), cz = sh.cz();
        for (int i = 0; i < 4; i++) {
            double a = i / 4.0 * Math.PI * 2 + unit(id, 7);
            double x = cx + Math.cos(a) * sh.a() * 0.55, z = cz + Math.sin(a) * sh.b() * 0.55;
            if (nearDoor(r, x, z, 3.5) || c.d(id, x, z) > -3) continue;
            int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
            int h = 4 + i % 2;
            c.s.op(ix - 2, W, iz - 2, ix + 2, W + h + 2, iz + 2, (b, xx, y, zz) -> {
                if (!b.open(xx, y, zz)) return;
                int dx = Math.abs(xx - ix), dz = Math.abs(zz - iz), rise = y - W;
                if (dx == 0 && dz == 0 && rise < h) b.set(xx, y, zz, log);
                else if (rise >= h - 2 && dx + dz <= (rise == h + 1 ? 1 : 3) && dx <= 2 && dz <= 2) b.set(xx, y, zz, leaves);
            });
            block(x, z, 1.5);
        }
        grow(r, 0.45, 29, (x, y, z, len, i) -> flowers[i % flowers.length]);
        int nx = (int) cx, nz = (int) cz;
        nook("the_last_seed", nx - 1, 0, nz - 1, nx + 1, 2, nz + 1);
        c.s.put(nx, W, nz, frame);
        c.s.put(nx, W + 1, nz, holo);
        light(cx, 5, cz, 0xfff0d0, 18f, 0.9f);
    }
}
