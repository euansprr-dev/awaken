package dev.awaken.gates.world;

import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The Overgrown Temple (SPEC §38.10): carved green stone gone to moss, tiled floors, roots splitting every hall, vines and
 * glowing flower bulbs hanging, braziers to light. The Jungle Courts are square courts under a roof of leaves (the green
 * sky), the Root Halls are crossed by roots as thick as towers, the Waterfall Temple stands beside a fall sixty blocks
 * high, and the Great Tree is a temple in one colossal trunk. The Sleeping Idol is a stone face the size of a hill.
 */
final class TempleDress extends Dress {
    final BlockState stone, mossy, carving, tiles, root, rootX, rootZ, vines, moss, brazier, idol, fern, bulb, canopy, fall;
    /** Shaped ground (SPEC §38.14): tile slabs and stairs on the floor, the temple's stone at the walls, moss drifted, fallen leaves */
    final BlockState gFloorSlab, gFloorStairs, gWallSlab, gWallStairs, gDrift, gBits;
    final boolean courts, halls, falls, tree;

    TempleDress(CaveEngine.Cave c) {
        super(c);
        stone = mat("wall", "awaken:temple_stone");
        mossy = mat("wall_moss", "awaken:temple_stone_mossy");
        carving = mat("wall_glow", "awaken:temple_carving");
        tiles = mat("floor", "awaken:temple_tiles");
        root = mat("root", "awaken:temple_root");
        rootX = WorldMats.with(root, "axis", "x");
        rootZ = WorldMats.with(root, "axis", "z");
        vines = mat("vines", "awaken:temple_vines");
        moss = mat("moss", "awaken:temple_moss");
        brazier = mat("touch", "awaken:temple_brazier");
        idol = mat("idol", "awaken:temple_idol");
        fern = mat("fern", "awaken:temple_fern");
        bulb = mat("bulb", "awaken:temple_lantern_bulb");
        canopy = mat("canopy", "awaken:temple_canopy");
        fall = mat("fall", "awaken:temple_waterfall");
        gFloorSlab = mat("ground_slab", "awaken:temple_tiles_slab");
        gFloorStairs = mat("ground_stairs", "awaken:temple_tiles_stairs");
        gWallSlab = mat("wall_slab", "awaken:temple_stone_slab");
        gWallStairs = mat("wall_stairs", "awaken:temple_stone_stairs");
        gDrift = mat("ground_drift", "awaken:temple_moss_drift");
        gBits = mat("ground_bits", "awaken:temple_leaves");
        String n = name();
        courts = n.equals("jungle_courts");
        halls = n.equals("root_halls");
        falls = n.equals("waterfall_temple");
        tree = n.equals("great_tree");
    }

    @Override
    int glow() {
        return 0xd8f080;
    }

    @Override
    BlockState skin(int x, int y, int z, boolean fl, boolean ceiling) {
        if (fl) return Hash.noise(seed + 3, x, z, 5) > 0.64 ? mossy : tiles;
        if (ceiling) return courts || tree || Hash.noise(seed + 7, x, z, 6) > 0.55 ? canopy : mossy;
        int rise = y - W;
        // courses of stone, a band of carving at eye height, moss rising from the floor and creeping down from the top
        if (rise == 2 && Math.floorMod(x + z, 3) == 0) return carving;
        double n = Hash.noise3(seed + 5, x, y, z, 4);
        if (n > 0.6 - Math.max(0, 3 - rise) * 0.08) return mossy;
        return stone;
    }

    @Override
    boolean depth() {
        return true;
    }

    @Override
    BlockState ground(int part, int x, int y, int z) {
        return kit(part, Hash.noise(seed + 3, x, z, 5) > 0.64, gFloorSlab, gFloorStairs, gWallSlab, gWallStairs, gDrift, gBits);
    }

    @Override
    double bits() {
        return 0.1;
    }

    @Override
    BlockState creep(int x, int y, int z) {
        return mossy;
    }

    @Override
    BlockState formation(int part, int x, int y, int z) {
        // columns banded with carving (roots in the tree and the root halls), mossy outcrops, a fallen column, rubble
        return pieces(part, x, y, z, tree || halls ? root : stone, tree || halls ? null : carving, stone, mossy, stone, mossy);
    }

    @Override
    BlockState screen(int lat, int rise, int side) {
        return rise == 2 && lat % 3 == 0 ? carving : rise % 5 == 4 ? mossy : stone;
    }

    @Override
    List<FloorAir.Mist> mists() {
        return mist(0x2a4a30, 0.45f, 0x1a3020, 0.25f);
    }

    @Override
    void cavern(Room r, CaveEngine.Kind k) {
        int id = r.id;
        PassageEngine.Shape sh = c.shapes.get(id);
        hang(r, courts ? 0.06 : 0.04, 5, 3, 11, (x, y, z, len, i) -> i == len - 1 && len > 2 ? bulb : vines);
        grow(r, 0.08, 13, (x, y, z, len, i) -> i < 4 ? fern : moss);
        if (courts && k != CaveEngine.Kind.ARRIVAL) colonnade(r);
        if (halls) roots(r, k == CaveEngine.Kind.GREAT ? 4 : 2);
        if (tree && k == CaveEngine.Kind.GREAT && hero("the_trunk")) trunk(r);
        if (falls && k == CaveEngine.Kind.GREAT && hero("the_fall")) fall(r);
        // an idol or two by the wall, watching
        for (int i = 0; i < (r.gw * r.gh >= 4 ? 2 : 1); i++) {
            double[] at = spot(r, 50 + i * 3, 0.6, 0.82, 1.5);
            if (at == null || k == CaveEngine.Kind.ARRIVAL) continue;
            c.s.putOpen((int) Math.floor(at[0]), W, (int) Math.floor(at[1]), idol);
            block(at[0], at[1], 0.8);
        }
        if (k != CaveEngine.Kind.ARRIVAL) touchstones(r, brazier, k == CaveEngine.Kind.GREAT ? 3 : 1, "first_offering", 60);
        if (k == CaveEngine.Kind.GREAT) {
            if (courts) wallNook(r, "overgrown_shrine", 70);
            if (halls) wallNook(r, "root_cellar", 70);
            if (falls) wallNook(r, "behind_the_water", 70);
            if (tree) wallNook(r, "heartwood", 70);
        }
        if (sh != null) light(sh.cx(), 3, sh.cz(), 0xc8e070, 10f + r.gw * 3, 0.55f);
    }

    /** A court's colonnade: square pillars of stone round its middle, a carved band near the top, moss at their feet. */
    void colonnade(Room r) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        int id = r.id;
        double ra = sh.a() * 0.55, rb = sh.b() * 0.55;
        for (int i = 0; i < 8; i++) {
            double a = i / 8.0 * Math.PI * 2 + Math.PI / 8;
            double px = sh.cx() + Math.cos(a) * ra, pz = sh.cz() + Math.sin(a) * rb;
            if (nearDoor(r, px, pz, 3) || c.d(id, px, pz) > -2.5) continue;
            int x = (int) Math.floor(px), z = (int) Math.floor(pz);
            int h = top(id, x, z) - W + 1;
            if (h < 4) continue;
            c.s.op(x, W, z, x, W + h, z, (b, xx, y, zz) -> {
                if (!b.open(xx, y, zz) && b.get(xx, y, zz) != Sculpt.UNSET) return;
                b.set(xx, y, zz, y - W == h - 2 ? carving : y - W < 2 ? mossy : stone);
            });
            block(px, pz, 0.8);
        }
    }

    /** Roots as thick as towers across a hall, high over the floor, each from one wall to the other. */
    void roots(Room r, int n) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        int id = r.id;
        int[] b = box(r);
        for (int i = 0; i < n; i++) {
            double a = unit(id, 60 + i) * Math.PI;
            double ax = sh.cx() + Math.cos(a) * sh.a() * 1.1, az = sh.cz() + Math.sin(a) * sh.b() * 1.1;
            double bx = sh.cx() - Math.cos(a) * sh.a() * 1.1, bz = sh.cz() - Math.sin(a) * sh.b() * 1.1;
            double rr = 1.4 + unit(id, 70 + i) * 1.2;
            double y0 = W + rr + 4 + unit(id, 80 + i) * 4, y1 = W + rr + 6 + unit(id, 90 + i) * 6;
            BlockState axis = Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? rootX : rootZ;
            c.s.op(b[0], (int) (Math.min(y0, y1) - rr - 1), b[1], b[2], (int) (Math.max(y0, y1) + rr + 1), b[3], (buf, x, y, z) -> {
                if (!buf.open(x, y, z)) return;
                if (Sculpt.capsule(x + 0.5, y + 0.5, z + 0.5, ax, y0, az, bx, y1, bz, rr) < 0) buf.set(x, y, z, axis);
            });
        }
    }

    /** The Great Tree's trunk: a colossal column of root from the floor through the roof, its foot spread in buttresses. */
    void trunk(Room r) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        int id = r.id;
        double cx = sh.cx() + sh.a() * 0.15, cz = sh.cz();
        double rr = Math.min(sh.a(), sh.b()) * 0.24;
        int roof = c.roofs.getOrDefault(id, c.roof);
        int r0 = (int) (rr * 2.2) + 2;
        c.s.op((int) cx - r0, W, (int) cz - r0, (int) cx + r0, W + roof + 3, (int) cz + r0, (b, x, y, z) -> {
            if (!b.open(x, y, z)) return;
            double px = x + 0.5 - cx, pz = z + 0.5 - cz, d = Math.hypot(px, pz);
            double t = (y - W) / (double) roof;
            // buttress roots at the foot, four of them, falling away over eight blocks
            double ang = Math.atan2(pz, px);
            double butt = Math.max(0, 1 - t * 4) * rr * 1.1 * Math.pow(Math.abs(Math.cos(ang * 2)), 6);
            if (d < rr * (1 + Math.max(0, 0.4 - t) * 0.5) + butt) b.set(x, y, z, root);
        });
        block(cx, cz, rr + 1.5);
        light(cx + rr + 2, 6, cz, 0xd8f080, 16f, 0.7f);
        light(cx, roof - 4, cz, 0xb8e070, 20f, 0.6f);
        // a ring of glowing bulbs round the trunk, hung from the leaves
        for (int i = 0; i < 10; i++) {
            double a = i / 10.0 * Math.PI * 2;
            int x = (int) Math.floor(cx + Math.cos(a) * (rr + 4)), z = (int) Math.floor(cz + Math.sin(a) * (rr + 4));
            int top = top(id, x, z);
            c.s.op(x, top - 3, z, x, top, z, (b, xx, y, zz) -> {
                if (b.open(xx, y, zz)) b.set(xx, y, zz, y == top - 3 ? bulb : vines);
            });
        }
    }

    /** The Waterfall Temple's fall: a curtain from the deep cavern's roof into a pool on its far side. */
    void fall(Room r) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        int id = r.id;
        double a0 = unit(id, 81) * Math.PI * 2;
        double fx = sh.cx() + Math.cos(a0) * sh.a() * 0.7, fz = sh.cz() + Math.sin(a0) * sh.b() * 0.7;
        int[] b = box(r);
        // the pool it falls into, sunk a block, walled with mossy stone
        c.s.op((int) fx - 5, W - 2, (int) fz - 5, (int) fx + 5, W - 1, (int) fz + 5, (buf, x, y, z) -> {
            double d = Math.hypot(x + 0.5 - fx, z + 0.5 - fz);
            if (d < 3.6) buf.set(x, y, z, fall);
            else if (d < 4.6 && y == W - 1) buf.set(x, y, z, mossy);
        });
        c.s.op(b[0], W, b[1], b[2], W + c.roofs.getOrDefault(id, c.roof) + 2, b[3], (buf, x, y, z) -> {
            double px = x + 0.5, pz = z + 0.5;
            double along = (px - fx) * -Math.sin(a0) + (pz - fz) * Math.cos(a0), across = (px - fx) * Math.cos(a0) + (pz - fz) * Math.sin(a0);
            if (Math.abs(along) > 2.6 || Math.abs(across) > 0.6 || !buf.open(x, y, z)) return;
            buf.set(x, y, z, fall);
        });
        block(fx, fz, 4.6);
        light(fx, 4, fz, 0xa8e0ff, 16f, 0.7f);
        c.waters.add(new FloorAir.Water(-1, (int) fx - 3, (int) fz - 3, (int) fx + 3, (int) fz + 3));
        c.shafts.add(new FloorAir.Shaft((int) fx, c.roofs.getOrDefault(id, c.roof), (int) fz, (int) fx, 0, (int) fz, 3f, 0xd8f0ff, 0.5f));
    }

    @Override
    void tunnel(Room r, ChainLayout.Link l) {
        int[] b = box(r);
        c.s.op(b[0], W + 2, b[1], b[0] + 15, W + 9, b[1] + 15, (buf, x, y, z) -> {
            if (!buf.open(x, y, z) || !solid(buf, x, y + 1, z)) return;
            long h = Hash.of(seed + 17, x, z);
            if (Hash.unit(h) >= 0.12) return;
            int len = 1 + (int) (Hash.unit(Hash.mix(h)) * 3);
            for (int i = 0; i < len && buf.open(x, y - i, z) && y - i > W + 1; i++) buf.set(x, y - i, z, i == len - 1 && len == 3 ? bulb : vines);
        });
        grow(r, 0.05, 19, (x, y, z, len, i) -> i < 3 ? fern : moss);
    }

    @Override
    void secret(Room r) {
        // the Sleeping Idol: a stone head the size of a hill rising out of the far floor, its eyes shut and glowing,
        // its mouth a door you can stand in
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        int id = r.id;
        int back = CaveEngine.Cave.back(r);
        double hx = sh.cx() + GatePainter.DX[back] * sh.a() * 0.45, hz = sh.cz() + GatePainter.DZ[back] * sh.b() * 0.45;
        double R = Math.min(sh.a(), sh.b()) * 0.45;
        int[] b = box(r);
        double fx = -GatePainter.DX[back], fz = -GatePainter.DZ[back];
        c.s.op(b[0], W, b[1], b[2], W + (int) (R * 1.6) + 2, b[3], (buf, x, y, z) -> {
            if (!buf.open(x, y, z)) return;
            double px = x + 0.5 - hx, py = y + 0.5 - (W + R * 0.6), pz = z + 0.5 - hz;
            double e = Math.sqrt(px * px / (R * R) + py * py / (R * R * 1.3) + pz * pz / (R * R));
            if (e > 1) return;
            double face = px * fx + pz * fz;
            double side = px * -fz + pz * fx;
            // the mouth: a doorway in its chin you can walk into
            if (face > R * 0.4 && Math.abs(side) < 1.6 && py < -R * 0.15 && y >= W && y < W + 3) return;
            boolean eye = face > R * 0.55 && Math.abs(Math.abs(side) - R * 0.32) < 1.2 && Math.abs(py - R * 0.25) < 0.8;
            buf.set(x, y, z, eye ? carving : e > 0.9 && Hash.unit(seed + 3, x * 7 + y, z) < 0.4 ? mossy : stone);
        });
        int mx = (int) Math.floor(hx + fx * R * 0.7), mz = (int) Math.floor(hz + fz * R * 0.7);
        nook("the_idols_mouth", mx - 1, 0, mz - 1, mx + 1, 2, mz + 1);
        block(hx, hz, R);
        light(hx + fx * R, 4, hz + fz * R, 0xd8f080, 16f, 0.9f);
        hang(r, 0.08, 4, 3, 31, (x, y, z, len, i) -> i == len - 1 ? bulb : vines);
    }
}
