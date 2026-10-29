package dev.awaken.gates.world;

import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The Clockwork Foundry (SPEC §38.11): a works that never stopped. Sooty brick below and riveted iron above with a band of
 * brass between, copper pipes up the walls, furnace mouths glowing, work lamps, steam, sparks and cables. The Works Floor
 * is grating over machine pits where gears turn, the Pipe Maze narrow ways lined with pipes and curtains of steam, the
 * Great Engine a cavern round a gear twenty blocks high, the Stopped Hour a hall where everything that was falling stopped
 * in the air, and the Orrery a dark room where brass planets go round a lamp of a sun.
 */
final class FoundryDress extends Dress {
    final BlockState iron, brick, brass, gear, grate, pipe, pipeX, pipeZ, furnace, lamp, steam, sparks, cables, valve, chain;
    /** Shaped ground (SPEC §38.14): iron slabs and stairs on the floor, brick at the walls, shavings */
    final BlockState gFloorSlab, gFloorStairs, gWallSlab, gWallStairs, gDrift, gBits;
    final boolean works, maze, engine, stopped;

    FoundryDress(CaveEngine.Cave c) {
        super(c);
        iron = mat("wall", "awaken:foundry_iron");
        brick = mat("brick", "awaken:foundry_brick");
        brass = mat("trim", "awaken:foundry_brass");
        gear = mat("gear", "awaken:foundry_gear");
        grate = mat("grate", "awaken:foundry_grille");
        pipe = mat("pipe", "awaken:foundry_pipe");
        pipeX = WorldMats.with(pipe, "axis", "x");
        pipeZ = WorldMats.with(pipe, "axis", "z");
        furnace = mat("wall_glow", "awaken:foundry_furnace");
        lamp = mat("lamp", "awaken:foundry_bulb");
        steam = mat("fall", "awaken:foundry_steam");
        sparks = mat("sparks", "awaken:foundry_sparks");
        cables = mat("cables", "awaken:foundry_cables");
        valve = mat("touch", "awaken:foundry_valve");
        gFloorSlab = mat("ground_slab", "awaken:foundry_iron_slab");
        gFloorStairs = mat("ground_stairs", "awaken:foundry_iron_stairs");
        gWallSlab = mat("wall_slab", "awaken:foundry_brick_slab");
        gWallStairs = mat("wall_stairs", "awaken:foundry_brick_stairs");
        gDrift = null;
        gBits = mat("ground_bits", "awaken:foundry_shavings");
        chain = GatePainter.state("minecraft:iron_chain[axis=y]");
        String n = name();
        works = n.equals("works_floor");
        maze = n.equals("pipe_maze");
        engine = n.equals("great_engine");
        stopped = n.equals("stopped_hour");
    }

    @Override
    int glow() {
        return 0xffc070;
    }

    @Override
    BlockState skin(int x, int y, int z, boolean fl, boolean ceiling) {
        int rise = y - W;
        if (fl) return Math.floorMod(x, 8) == 0 || Math.floorMod(z, 8) == 0 ? brick : iron;
        if (ceiling) return Math.floorMod(x + z, 6) == 0 ? brass : iron;
        // pipes up the walls every seven blocks, a brass band at shoulder height, a furnace mouth glowing low down
        if (Math.floorMod(x * 3 + z * 5, 7) == 0) return pipe;
        if (rise == 3) return brass;
        if (rise == 0 && Hash.unit(seed + 13, x, z) < 0.06) return furnace;
        return rise < 3 || Hash.noise3(seed + 5, x, y, z, 5) > 0.7 ? brick : iron;
    }

    @Override
    boolean depth() {
        return true;
    }

    @Override
    BlockState ground(int part, int x, int y, int z) {
        return kit(part, false, gFloorSlab, gFloorStairs, gWallSlab, gWallStairs, gDrift, gBits);
    }

    @Override
    double bits() {
        // bolts and shavings lie thick: the foundry grows little else
        return 0.42;
    }

    @Override
    BlockState creep(int x, int y, int z) {
        return null;
    }

    @Override
    BlockState formation(int part, int x, int y, int z) {
        // pipe bundles floor to roof banded with brass, scrap iron with gears in it, a fallen gear half sunk, a scrap heap
        return pieces(part, x, y, z, pipe, brass, iron, gear, gear, iron);
    }

    @Override
    BlockState screen(int lat, int rise, int side) {
        if (lat % 5 == 0) return pipe;
        return rise == 3 ? brass : rise < 3 ? brick : iron;
    }

    @Override
    List<FloorAir.Mist> mists() {
        return mist(0x4a3a2a, 0.45f, 0x2a221c, 0.25f);
    }

    @Override
    void cavern(Room r, CaveEngine.Kind k) {
        int id = r.id;
        PassageEngine.Shape sh = c.shapes.get(id);
        hang(r, 0.03, 5, 4, 11, (x, y, z, len, i) -> i == len - 1 && len > 3 ? lamp : cables);
        grow(r, 0.015, 13, (x, y, z, len, i) -> sparks);
        if (works && k != CaveEngine.Kind.ARRIVAL && hero("machine_pits")) pit(r);
        if (engine && k == CaveEngine.Kind.GREAT && hero("the_great_gear")) bigGear(r);
        if (stopped && k == CaveEngine.Kind.GREAT && hero("frozen_debris")) frozen(r);
        if (k != CaveEngine.Kind.ARRIVAL) touchstones(r, valve, k == CaveEngine.Kind.GREAT ? 2 : 1, "the_old_valve", 60);
        if (k == CaveEngine.Kind.GREAT) {
            if (works) wallNook(r, "the_spare_cog", 70);
            if (maze) wallNook(r, "the_duct_map", 70);
            if (engine) wallNook(r, "the_governor", 70);
            if (stopped) wallNook(r, "the_stopped_clock", 70);
        }
        if (sh != null) light(sh.cx(), 3, sh.cz(), 0xffb060, 10f + r.gw * 3, 0.55f);
    }

    /** A machine pit under grating: four deep, gears turning in it and furnaces glowing at the bottom. */
    void pit(Room r) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        int id = r.id;
        double ra = sh.a() * 0.5, rb = sh.b() * 0.5;
        int x0 = (int) (sh.cx() - ra) - 1, x1 = (int) (sh.cx() + ra) + 1, z0 = (int) (sh.cz() - rb) - 1, z1 = (int) (sh.cz() + rb) + 1;
        java.util.function.BiPredicate<Double, Double> in = (x, z) -> c.q(id, x, z) < 0.5 && !nearDoor(r, x, z, 3.5) && !p.nearAnchor(x, z, 4);
        c.s.carve(x0, W - 5, z0, x1, W - 2, z1, (x, y, z) -> in.test(x, z) ? Math.max(W - 5 - y, y - (W - 1)) : 1);
        c.s.op(x0, W - 6, z0, x1, W - 1, z1, (b, x, y, z) -> {
            double px = x + 0.5, pz = z + 0.5;
            if (!in.test(px, pz)) return;
            if (y == W - 1) b.set(x, y, z, grate);
            else if (y == W - 6 && Hash.unit(seed + 29, x, z) < 0.12) b.set(x, y, z, furnace);
        });
        // gears turning in the pit, flat, a little under the grate
        for (int i = 0; i < 3; i++) {
            double a = i / 3.0 * Math.PI * 2 + unit(id, 3);
            double gx = sh.cx() + Math.cos(a) * ra * 0.45, gz = sh.cz() + Math.sin(a) * rb * 0.45;
            if (!in.test(gx, gz)) continue;
            int R = 2 + i % 2;
            p.prop("gear:" + R * 10, (int) Math.floor(gx - R), -4, (int) Math.floor(gz - R), (int) Math.ceil(gx + R) - 1, -4, (int) Math.ceil(gz + R) - 1);
        }
        light(sh.cx(), -4, sh.cz(), 0xff9030, 10f + r.gw * 2, 0.8f);
    }

    /**
     * The Great Engine's gear: a wheel twenty blocks across standing on its edge over the deep cavern, its lowest teeth four
     * over the floor so you walk under it, its axle on two iron pillars.
     */
    void bigGear(Room r) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        int id = r.id;
        boolean faceX = sh.a() >= sh.b();
        double cx = sh.cx(), cz = sh.cz();
        double R = Math.min(10, (c.roofs.getOrDefault(id, c.roof) - 4) / 2.0);
        double cy = W + R + 4;
        int r0 = (int) R + 2;
        BlockState axle = faceX ? pipeZ : pipeX;
        c.s.op((int) cx - r0, W, (int) cz - r0, (int) cx + r0, (int) (cy + R) + 2, (int) cz + r0, (b, x, y, z) -> {
            if (!b.open(x, y, z)) return;
            double u = (faceX ? x : z) + 0.5 - (faceX ? cx : cz), depth = (faceX ? z : x) + 0.5 - (faceX ? cz : cx);
            double v = y + 0.5 - cy, d = Math.hypot(u, v), ang = Math.atan2(v, u);
            if (d < 0.8 && Math.abs(depth) < 3.2) {
                b.set(x, y, z, axle);
                return;
            }
            if (Math.abs(depth) > 1.1) return;
            boolean tooth = Math.cos(ang * 14) > 0.3;
            boolean rim = d > R - 1.6 && (d < R || (tooth && d < R + 1.2));
            boolean spoke = d < R - 1.6 && Math.min(Math.abs(Math.sin(ang * 3)), Math.abs(Math.cos(ang * 3))) * d < 0.8;
            if (rim || spoke || d < 2.5) b.set(x, y, z, (int) (d * 2) % 3 == 0 ? brass : gear);
        });
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            double px = faceX ? cx : cx + sgn * 2.5, pz = faceX ? cz + sgn * 2.5 : cz;
            if (c.d(id, px, pz) < -1.5 && !nearDoor(r, px, pz, 2)) pillar(px, pz, 0.9, (int) (cy - W), iron);
        }
        light(cx, (int) (cy - W), cz, 0xffc070, 20f, 0.8f);
        c.shafts.add(new FloorAir.Shaft((int) cx, c.roofs.getOrDefault(id, c.roof), (int) cz, (int) cx, 0, (int) cz, 3f, 0xffd8a0, 0.4f));
    }

    /** The Stopped Hour: the clock face on the far wall, and everything that was falling hung still in the air. */
    void frozen(Room r) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        int id = r.id;
        int roof = c.roofs.getOrDefault(id, c.roof);
        BlockState[] bits = {gear, brass, iron, brick, pipe, lamp};
        for (int i = 0; i < 70; i++) {
            double a = unit(id, 200 + i) * Math.PI * 2, q = Math.sqrt(unit(id, 300 + i)) * 0.8;
            double x = sh.cx() + Math.cos(a) * sh.a() * q, z = sh.cz() + Math.sin(a) * sh.b() * q;
            int y = W + 4 + (int) (unit(id, 400 + i) * Math.max(1, roof - 8));
            if (nearDoor(r, x, z, 3) || c.d(id, x, z) > -2) continue;
            c.s.putOpen((int) Math.floor(x), y, (int) Math.floor(z), bits[i % bits.length]);
        }
        // the clock: a brass ring on the back wall with its hands at a quarter to
        int back = CaveEngine.Cave.back(r);
        double fx = sh.cx() + GatePainter.DX[back] * sh.a() * 0.85, fz = sh.cz() + GatePainter.DZ[back] * sh.b() * 0.85;
        double R = Math.min(7, (roof - 6) / 2.0), cy = W + R + 4;
        boolean faceX = GatePainter.DZ[back] != 0;
        c.s.op((int) fx - 9, W, (int) fz - 9, (int) fx + 9, (int) (cy + R) + 2, (int) fz + 9, (b, x, y, z) -> {
            if (!b.open(x, y, z)) return;
            double u = (faceX ? x : z) + 0.5 - (faceX ? fx : fz), depth = (faceX ? z : x) + 0.5 - (faceX ? fz : fx);
            double v = y + 0.5 - cy, d = Math.hypot(u, v);
            if (Math.abs(depth) > 0.6) return;
            boolean ring = d > R - 1 && d < R;
            boolean hand = (Math.abs(u) < 0.6 && v > 0 && v < R - 1.5) || (Math.abs(v) < 0.6 && u < 0 && u > -(R - 2.5));
            if (ring || hand) b.set(x, y, z, ring ? brass : gear);
        });
        light(fx, (int) (cy - W), fz, 0xffd090, 14f, 0.7f);
    }

    @Override
    void tunnel(Room r, ChainLayout.Link l) {
        int[] b = box(r);
        ChainLayout.Way way = ChainLayout.Way.of(r, l, seed);
        // pipes along both walls at knee and head height; in the maze a curtain of steam across the way here and there.
        // Where the way bends hard by a door the wall swings across the doorway, so a pipe stops short of its middle.
        c.s.op(b[0], W, b[1], b[2], W + 5, b[3], (buf, x, y, z) -> {
            if (!buf.open(x, y, z)) return;
            double[] w = way.at(x + 0.5, z + 0.5);
            if (w == null) return;
            double lat = Math.abs(w[0]), half = c.hw + 0.4;
            int along = (int) Math.round(w[1]);
            if ((y == W + 1 || (maze && y == W + 3)) && lat > half - 1.1 && lat <= half) {
                if (!inDoorway(r, x, z, 4)) buf.set(x, y, z, way.alongZ() ? pipeZ : pipeX);
            } else if (maze && Math.floorMod(along, 9) == 4 && lat < half - 1.1 && !nearDoor(r, x + 0.5, z + 0.5, 3)) buf.set(x, y, z, steam);
        });
        grow(r, 0.02, 17, (x, y, z, len, i) -> sparks);
    }

    @Override
    void secret(Room r) {
        // the Orrery: brass planets going round a sun of furnace and lamp in the middle of a dark round room
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        int id = r.id;
        double cx = sh.cx(), cz = sh.cz();
        int cy = W + 5;
        c.s.op((int) cx - 3, cy - 3, (int) cz - 3, (int) cx + 3, cy + 3, (int) cz + 3, (b, x, y, z) -> {
            double d = Math.sqrt(Math.pow(x + 0.5 - cx, 2) + Math.pow(y + 0.5 - cy, 2) + Math.pow(z + 0.5 - cz, 2));
            if (d < 2.2 && b.open(x, y, z)) b.set(x, y, z, d < 1.2 ? lamp : furnace);
        });
        c.s.op((int) cx, W, (int) cz, (int) cx, cy - 3, (int) cz, (b, x, y, z) -> {
            if (b.open(x, y, z)) b.set(x, y, z, pipe);
        });
        double maxR = Math.min(sh.a(), sh.b()) - 3;
        for (int i = 0; i < 5; i++) {
            double R = 4 + i * (maxR - 4) / 4.0;
            int y = 3 + i % 3 * 2;
            p.prop("orb:" + Math.round(R * 10), (int) Math.floor(cx - R), y, (int) Math.floor(cz - R), (int) Math.ceil(cx + R) - 1, y, (int) Math.ceil(cz + R) - 1);
        }
        block(cx, cz, 2.5);
        int nx = (int) (cx + sh.a() * 0.55);
        nook("the_little_planet", nx - 1, 0, (int) cz - 1, nx + 1, 2, (int) cz + 1);
        light(cx, 5, cz, 0xffd080, 18f, 1f);
    }
}
