package dev.awaken.gates.world;

import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The Glass Desert (SPEC §38.11): sand under an open sky, and the glass the sun made of it. The Buried Colossus is dune
 * basins with a colonnade along the ways, an oasis, and a stone head as big as a hill rising out of the sand at the
 * land's corner; the Sunken City is the tops of a buried town, flat roofs and blue domes rising out of the sand
 * round its squares; the Mirage has a palace on the far edge of the land you can see and never reach. The Glass Caves are
 * caverns of glowing glass with sand falling through them, and the Hourglass a room where a whole little city sits under
 * the sand pouring through its neck.
 */
final class DesertDress extends Dress {
    final BlockState sand, rippled, sandstone, carved, brick, tile, column, glass, glassGlow, crystal, grass, sandfall, mirror, palm, fronds;
    /** Shaped ground (SPEC §38.14): tile slabs and stairs, sandstone at the walls, sand drifted (all its cave floor is sand), glass shards */
    final BlockState gFloorSlab, gFloorStairs, gWallSlab, gWallStairs, gDrift, gBits;
    final boolean colossus, glassCaves, city, mirage, open;
    /** The Colossus's head (x, z, or NaN when no corner is free) and the Mirage's far edge (0 north, 1 east, 2 south, 3 west). */
    private double hx = Double.NaN, hz = Double.NaN;
    private int far = -1;
    private boolean placed;

    static final double HEAD_R = 15;

    DesertDress(CaveEngine.Cave c) {
        super(c);
        sand = mat("floor", "awaken:desert_sand");
        rippled = mat("floor_rippled", "awaken:desert_sand_rippled");
        sandstone = mat("wall", "awaken:desert_sandstone");
        carved = mat("wall_glow", "awaken:desert_sandstone_carved");
        brick = mat("brick", "awaken:desert_brick");
        tile = mat("tile", "awaken:desert_tile");
        column = mat("column", "awaken:desert_column");
        glass = mat("glass", "awaken:desert_glass");
        glassGlow = mat("glass_glow", "awaken:desert_glass_glowing");
        crystal = mat("crystal", "awaken:desert_crystal");
        grass = mat("grass", "awaken:desert_grass");
        sandfall = mat("fall", "awaken:desert_sandfall");
        mirror = mat("touch", "awaken:desert_mirror");
        gFloorSlab = mat("ground_slab", "awaken:desert_tile_slab");
        gFloorStairs = mat("ground_stairs", "awaken:desert_tile_stairs");
        gWallSlab = mat("wall_slab", "awaken:desert_sandstone_slab");
        gWallStairs = mat("wall_stairs", "awaken:desert_sandstone_stairs");
        gDrift = mat("ground_drift", "awaken:desert_sand_drift");
        gBits = mat("ground_bits", "awaken:desert_shards");
        palm = GatePainter.state("minecraft:stripped_jungle_log[axis=y]");
        fronds = GatePainter.state("minecraft:jungle_leaves[persistent=true]");
        String n = name();
        colossus = n.equals("buried_colossus");
        glassCaves = n.equals("glass_caves");
        city = n.equals("sunken_city");
        mirage = n.equals("mirage");
        open = c.open;
    }

    @Override
    int glow() {
        return 0xffe0a0;
    }

    // ---- the land's shape ---------------------------------------------------------------------------------------------

    /** Where the head stands and which edge the palace is on, once the places are laid. */
    private void place() {
        if (placed) return;
        placed = true;
        if (colossus) {
            int[][] corners = {{1, 1, 12, 12}, {13, 1, 243, 12}, {1, 13, 12, 243}, {13, 13, 243, 243}};
            for (int[] k : corners) {
                if (!p.free(k[0], k[1], 2, 2)) continue;
                hx = k[2];
                hz = k[3];
                break;
            }
        }
        if (mirage) {
            Room a = p.world(p.host[p.logic.start]);
            int ax = a.centerX(), az = a.centerZ();
            int[] dist = {az, Plots.SIDE - ax, Plots.SIDE - az, ax};
            far = 0;
            for (int i = 1; i < 4; i++) if (dist[i] > dist[far]) far = i;
        }
    }

    /** Dunes: long ridges across the wind, broken up. */
    int dune(int x, int z) {
        double n = Hash.noise(seed + 31, x, z, 22);
        return (int) ((0.5 + 0.5 * Math.sin((x * 0.6 + z * 0.8) / 7.0 + n * 5)) * 6 + n * 5);
    }

    /** The head's height over a point above the floor, or -1 off it. */
    int head(int x, int z) {
        if (Double.isNaN(hx)) return -1;
        double d = Math.hypot(x + 0.5 - hx, z + 0.5 - hz);
        if (d > HEAD_R) return -1;
        double h = 6 + 30 * Math.sqrt(1 - (d / HEAD_R) * (d / HEAD_R));
        // its face looks across the land: two eye sockets and the line of a nose on that side
        double fx = 128 - hx, fz = 128 - hz, fl = Math.hypot(fx, fz);
        double along = ((x + 0.5 - hx) * fx + (z + 0.5 - hz) * fz) / fl, side = ((x + 0.5 - hx) * -fz + (z + 0.5 - hz) * fx) / fl;
        if (along > HEAD_R * 0.55 && Math.abs(Math.abs(side) - 5) < 2) h -= 4;
        else if (along > HEAD_R * 0.75 && Math.abs(side) < 1.2) h += 2;
        return (int) h;
    }

    boolean eye(int x, int z) {
        if (Double.isNaN(hx)) return false;
        double fx = 128 - hx, fz = 128 - hz, fl = Math.hypot(fx, fz);
        double along = ((x + 0.5 - hx) * fx + (z + 0.5 - hz) * fz) / fl, side = ((x + 0.5 - hx) * -fz + (z + 0.5 - hz) * fx) / fl;
        return along > HEAD_R * 0.55 && along < HEAD_R && Math.abs(Math.abs(side) - 5) < 2;
    }

    /** A buried town's lot: its roof height above the floor (0 for a street), and whether it wears a dome. */
    int lot(int x, int z) {
        int u = Math.floorMod(x + 3, 12), v = Math.floorMod(z + 7, 12);
        if (u < 2 || v < 2) return 0;
        int lx = Math.floorDiv(x + 3, 12), lz = Math.floorDiv(z + 7, 12);
        int h = c.lid + 1 + (int) (Hash.unit(seed, lx * 37 + 5, lz) * 14);
        if (dome(lx, lz)) {
            double d = Math.hypot(u - 6.5, v - 6.5);
            if (d < 4.5) h += (int) Math.round(Math.sqrt(4.5 * 4.5 - d * d));
        }
        return h;
    }

    boolean dome(int lx, int lz) {
        return Hash.unit(seed, lx * 37 + 9, lz) < 0.3;
    }

    /** The Mirage's palace on its far edge: walls, towers and a great dome, heights above the floor, or -1 off it. */
    int palace(int x, int z) {
        if (far < 0) return -1;
        int depth = switch (far) {
            case 0 -> z;
            case 1 -> Plots.SIDE - 1 - x;
            case 2 -> Plots.SIDE - 1 - z;
            default -> x;
        };
        int along = far % 2 == 0 ? x : z;
        if (depth > 15) return -1;
        double dd = Math.hypot(along - 128, depth - 4);
        if (dd < 14) return 22 + (int) Math.round(Math.sqrt(14 * 14 - dd * dd));
        int t = Math.floorMod(along - 128, 32);
        double tower = Math.hypot(Math.min(t, 32 - t), depth - 6);
        if (tower < 2.6) return 34 + (int) Math.round(Math.sqrt(Math.max(0, 6.8 - tower * tower)));
        return depth < 10 ? 20 : -1;
    }

    @Override
    int skyline(int x, int z) {
        place();
        int base = c.lid + 1 + dune(x, z);
        if (colossus) return Math.max(base, head(x, z));
        if (city) return Math.max(c.lid - 1, lot(x, z));
        if (mirage) return Math.max(base, palace(x, z));
        return base;
    }

    // ---- the skin -----------------------------------------------------------------------------------------------------

    @Override
    BlockState skin(int x, int y, int z, boolean fl, boolean ceiling) {
        int rise = y - W;
        if (!open) {
            // the Glass Caves: sandstone fused to glass in bands, some of it still glowing
            if (fl) return Hash.noise(seed + 3, x, z, 5) > 0.55 ? rippled : sand;
            // glass only on faces you can see (glassFaces): deeper in, sandstone
            if (ceiling) return Hash.noise3(seed + 7, x, y, z, 4) > 0.55 ? glassGlow : glass;
            return Math.floorMod(rise, 5) == 2 ? carved : sandstone;
        }
        if (colossus && head(x, z) >= 0) {
            if (eye(x, z) && fl) return glassGlow;
            return Hash.noise3(seed + 5, x, y, z, 4) > 0.7 ? carved : sandstone;
        }
        if (city && rise >= 0) {
            int lx = Math.floorDiv(x + 3, 12), lz = Math.floorDiv(z + 7, 12);
            int u = Math.floorMod(x + 3, 12), v = Math.floorMod(z + 7, 12);
            boolean inLot = u >= 2 && v >= 2;
            if (inLot && fl) return dome(lx, lz) && Math.hypot(u - 6.5, v - 6.5) < 4.5 ? tile : brick;
            if (inLot) return rise % 4 == 2 && Math.floorMod(x + z, 3) == 1 ? carved : rise % 4 == 0 ? sandstone : brick;
        }
        if (mirage && palace(x, z) >= 0) {
            if (fl) return tile;
            return rise % 6 == 4 ? tile : rise % 6 == 2 && Math.floorMod(x + z, 4) == 1 ? carved : sandstone;
        }
        if (fl) return Hash.noise(seed + 3, x, z, 6) > 0.5 ? rippled : sand;
        if (ceiling || rise < 2) return sandstone;
        return Hash.noise3(seed + 9, x, y, z, 6) > 0.75 ? sandstone : sand;
    }

    @Override
    boolean depth() {
        return true;
    }

    @Override
    BlockState ground(int part, int x, int y, int z) {
        return kit(part, true, gFloorSlab, gFloorStairs, gWallSlab, gWallStairs, gDrift, gBits);
    }

    @Override
    double bits() {
        // glass shards lie thick where the sand drifts
        return 0.3;
    }

    @Override
    BlockState creep(int x, int y, int z) {
        return sand;
    }

    @Override
    BlockState formation(int part, int x, int y, int z) {
        // sandstone pillars banded with carving, glass outcrops glowing here and there, a half-buried column, a heap of sandstone
        return pieces(part, x, y, z, sandstone, carved, glass, glassGlow, column, sandstone);
    }

    @Override
    BlockState screen(int lat, int rise, int side) {
        return rise % 5 == 2 ? (open ? carved : glassGlow) : sandstone;
    }

    @Override
    List<FloorAir.Mist> mists() {
        return open ? mist(0xd8b080, 0.25f, 0xc8a070, 0.12f) : mist(0x8a6a3a, 0.4f, 0x5a4428, 0.22f);
    }

    // ---- places -------------------------------------------------------------------------------------------------------

    @Override
    void cavern(Room r, CaveEngine.Kind k) {
        int id = r.id;
        PassageEngine.Shape sh = c.shapes.get(id);
        if (open) grow(r, 0.02, 13, (x, y, z, len, i) -> grass);
        else {
            int[] b = box(r);
            glassFaces(b[0], W, b[1], b[2], W + c.roofs.getOrDefault(id, c.roof) + 2, b[3]);
            grow(r, 0.04, 13, (x, y, z, len, i) -> crystal);
            hang(r, 0.02, 2, 5, 11, (x, y, z, len, i) -> crystal);
        }
        if (open && c.inLakeAny(id) && sh != null) oasis(r);
        if (glassCaves && k == CaveEngine.Kind.GREAT && hero("sandfalls")) sandfalls(r);
        if (city && k != CaveEngine.Kind.ARRIVAL) fallen(r);
        if (k != CaveEngine.Kind.ARRIVAL) touchstones(r, mirror, k == CaveEngine.Kind.GREAT ? 2 : 1, "the_first_mirror", 60);
        if (k == CaveEngine.Kind.GREAT && sh != null) {
            if (colossus) wallNook(r, "the_colossus_eye", 70);
            if (glassCaves) wallNook(r, "the_sunstone", 70);
            if (city) wallNook(r, "the_buried_door", 70);
            if (mirage && c.inLakeAny(id)) nook("the_mirror_pool", (int) sh.cx() - 1, 0, (int) sh.cz() - 1, (int) sh.cx() + 1, 2, (int) sh.cz() + 1);
        }
        if (sh != null && (!open || c.dark)) light(sh.cx(), 3, sh.cz(), 0xffd890, 10f + r.gw * 3, 0.5f);
    }

    /**
     * The Glass Caves' walls: the faces you can see fused to glass, a band of it still glowing at shoulder height (only the
     * face: glass all through the shell would show the dark behind it).
     */
    void glassFaces(int x0, int y0, int z0, int x1, int y1, int z1) {
        c.s.op(x0, y0, z0, x1, y1, z1, (b, x, y, z) -> {
            if (b.get(x, y, z) != Sculpt.SOLID) return;
            if (!(b.open(x + 1, y, z) || b.open(x - 1, y, z) || b.open(x, y, z + 1) || b.open(x, y, z - 1))) return;
            if (Math.floorMod(y - W, 5) == 2) b.set(x, y, z, glassGlow);
            else if (Hash.noise3(seed + 5, x, y, z, 4) > 0.55) b.set(x, y, z, glass);
        });
    }

    /** An oasis round a lake: grass along its shore and three palms leaning over the water. */
    void oasis(Room r) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        int id = r.id;
        int[] b = box(r);
        c.s.op(b[0], W, b[1], b[2], W, b[3], (buf, x, y, z) -> {
            if (!buf.open(x, y, z) || c.inLake(id, x + 0.5, z + 0.5) || !solid(buf, x, y - 1, z)) return;
            double q = c.q(id, x + 0.5, z + 0.5);
            if (q < 0.62 && Hash.unit(seed + 51, x, z) < 0.45 && !nearDoor(r, x + 0.5, z + 0.5, 2.5)) buf.set(x, y, z, grass);
        });
        for (int i = 0; i < 3; i++) {
            double a = i / 3.0 * Math.PI * 2 + unit(id, 7);
            double px = sh.cx() + Math.cos(a) * sh.a() * 0.58, pz = sh.cz() + Math.sin(a) * sh.b() * 0.58;
            if (c.inLake(id, px, pz) || nearDoor(r, px, pz, 3.5) || c.d(id, px, pz) > -2.5) continue;
            int x = (int) Math.floor(px), z = (int) Math.floor(pz);
            int h = 6 + i;
            c.s.op(x - 2, W, z - 2, x + 2, W + h + 1, z + 2, (buf, xx, y, zz) -> {
                if (!buf.open(xx, y, zz)) return;
                int dx = xx - x, dz = zz - z;
                if (dx == 0 && dz == 0 && y < W + h) buf.set(xx, y, zz, palm);
                else if (y == W + h && (dx == 0 || dz == 0) && Math.abs(dx + dz) <= 2) buf.set(xx, y, zz, fronds);
                else if (y == W + h - 1 && Math.abs(dx) + Math.abs(dz) == 2 && (dx == 0 || dz == 0)) buf.set(xx, y, zz, fronds);
            });
            block(px, pz, 0.8);
        }
    }

    /** The Glass Caves' falls: three curtains of sand from the deep cavern's roof to its floor. */
    void sandfalls(Room r) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        int id = r.id;
        for (int i = 0; i < 3; i++) {
            double a = unit(id, 90 + i) * Math.PI * 2;
            double fx = sh.cx() + Math.cos(a) * sh.a() * 0.5, fz = sh.cz() + Math.sin(a) * sh.b() * 0.5;
            if (nearDoor(r, fx, fz, 4)) continue;
            int x = (int) Math.floor(fx), z = (int) Math.floor(fz);
            c.s.op(x - 1, W, z - 1, x + 1, W + c.roofs.getOrDefault(id, c.roof) + 2, z + 1, (buf, xx, y, zz) -> {
                if (Math.abs(xx - x) + Math.abs(zz - z) <= 1 && buf.open(xx, y, zz)) buf.set(xx, y, zz, sandfall);
            });
            block(fx, fz, 1.6);
            c.shafts.add(new FloorAir.Shaft(x, c.roofs.getOrDefault(id, c.roof), z, x, 0, z, 2f, 0xffd890, 0.5f));
        }
    }

    /** The Sunken City's squares: a fallen column or two lying in the sand. */
    void fallen(Room r) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        int id = r.id;
        for (int i = 0; i < 2; i++) {
            double[] at = spot(r, 70 + i * 5, 0.3, 0.6, 3);
            if (at == null) continue;
            boolean alongX = unit(id, 75 + i) < 0.5;
            BlockState lying = WorldMats.with(column, "axis", alongX ? "x" : "z");
            for (int t = -2; t <= 2; t++) {
                int x = (int) Math.floor(at[0]) + (alongX ? t : 0), z = (int) Math.floor(at[1]) + (alongX ? 0 : t);
                c.s.putOpen(x, W, z, lying);
            }
            block(at[0], at[1], 3);
        }
    }

    @Override
    void tunnel(Room r, ChainLayout.Link l) {
        if (!open) {
            int[] b = box(r);
            glassFaces(b[0], W, b[1], b[2], W + 9, b[3]);
            grow(r, 0.04, 17, (x, y, z, len, i) -> crystal);
            return;
        }
        if (!colossus || !hero("colonnade")) return;
        // the Colossus's ways: a column each side every six blocks, a lintel across the top
        int[] b = box(r);
        ChainLayout.Way way = ChainLayout.Way.of(r, l, seed);
        c.s.op(b[0], W, b[1], b[2], W + 5, b[3], (buf, x, y, z) -> {
            if (!buf.open(x, y, z)) return;
            double[] w = way.at(x + 0.5, z + 0.5);
            if (w == null || Math.floorMod((int) Math.round(w[1]), 6) != 3 || nearDoor(r, x + 0.5, z + 0.5, 3)) return;
            double lat = Math.abs(w[0]), half = c.hw;
            if (y < W + 5 && lat > half - 0.9 && lat <= half + 0.1) buf.set(x, y, z, column);
            else if (y == W + 5 && lat <= half + 0.1) buf.set(x, y, z, carved);
        });
    }

    @Override
    void secret(Room r) {
        // the Hourglass: a little city of brick and blue roofs on the floor, sand pouring down through the neck over it
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        int id = r.id;
        int[] b = box(r);
        double cx = sh.cx(), cz = sh.cz();
        c.s.op(b[0], W, b[1], b[2], W + 2, b[3], (buf, x, y, z) -> {
            if (!buf.open(x, y, z)) return;
            double q = c.q(id, x + 0.5, z + 0.5);
            if (q < 0.25 || q > 0.75 || nearDoor(r, x + 0.5, z + 0.5, 3)) return;
            int u = Math.floorMod(x, 3), v = Math.floorMod(z, 3);
            if (u == 0 || v == 0) return;
            int h = 1 + (int) (Hash.unit(seed + 61, x / 3, z / 3) * 3);
            if (y - W < h - 1) buf.set(x, y, z, brick);
            else if (y - W == h - 1) buf.set(x, y, z, Hash.unit(seed + 63, x / 3, z / 3) < 0.4 ? tile : sandstone);
        });
        int roof = c.roofs.getOrDefault(id, c.roof);
        // curtains of sand round the room, and the neck's fall over its middle
        for (int i = 0; i < 6; i++) {
            double a = i / 6.0 * Math.PI * 2;
            int x = (int) Math.floor(cx + Math.cos(a) * sh.a() * 0.85), z = (int) Math.floor(cz + Math.sin(a) * sh.b() * 0.85);
            if (nearDoor(r, x + 0.5, z + 0.5, 3)) continue;
            c.s.op(x, W, z, x, W + roof + 2, z, (buf, xx, y, zz) -> {
                if (buf.open(xx, y, zz)) buf.set(xx, y, zz, sandfall);
            });
        }
        int nx = (int) cx, nz = (int) cz;
        c.s.op(nx, W + 3, nz, nx, W + roof + 2, nz, (buf, xx, y, zz) -> {
            if (buf.open(xx, y, zz)) buf.set(xx, y, zz, sandfall);
        });
        nook("the_neck", nx - 1, 0, nz - 1, nx + 1, 2, nz + 1);
        c.shafts.add(new FloorAir.Shaft(nx, roof, nz, nx, 0, nz, 2.5f, 0xffd890, 0.8f));
        light(cx, 4, cz, 0xffe0a0, 16f, 0.9f);
    }
}
