package dev.awaken.gates.world;

import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The Ember Mines (SPEC §38.10): black basalt cracked with heat, slag and ash underfoot, timber frames down every gallery
 * with lamps hung on them, ember crystals in the corners. The Working Mine is timbered and railed, the Open Chasm's
 * caverns hold lava rivers you cross on plank bridges, the Forge Below has molten falls pouring from its roof, and the
 * Cooling Sea is crust islands on glowing magma. The Heart Seam is the inside of a geode of living ember.
 */
final class EmberDress extends Dress {
    final BlockState basalt, cracked, slag, ash, timber, timberX, timberZ, planks, lamp, crust, crystal, fall, bellows, rail, railX, chain;
    /** Shaped ground (SPEC §38.14): slag slabs and stairs on the floor, basalt at the walls, ash drifted, cinders */
    final BlockState gFloorSlab, gFloorStairs, gWallSlab, gWallStairs, gDrift, gBits;
    final boolean mine, chasm, forge, sea;

    EmberDress(CaveEngine.Cave c) {
        super(c);
        basalt = mat("wall", "awaken:ember_basalt");
        cracked = mat("wall_glow", "awaken:ember_basalt_cracked");
        slag = mat("floor", "awaken:ember_slag");
        ash = mat("floor_soft", "awaken:ember_ash");
        timber = mat("timber", "awaken:mine_timber");
        timberX = WorldMats.with(timber, "axis", "x");
        timberZ = WorldMats.with(timber, "axis", "z");
        planks = mat("planks", "awaken:mine_planks");
        lamp = mat("lamp", "awaken:mine_lamp");
        crust = mat("crust", "awaken:magma_crust");
        crystal = mat("crystal", "awaken:ember_crystal");
        fall = mat("fall", "awaken:ember_moltenfall");
        bellows = mat("touch", "awaken:ember_bellows");
        gFloorSlab = mat("ground_slab", "awaken:ember_slag_slab");
        gFloorStairs = mat("ground_stairs", "awaken:ember_slag_stairs");
        gWallSlab = mat("wall_slab", "awaken:ember_basalt_slab");
        gWallStairs = mat("wall_stairs", "awaken:ember_basalt_stairs");
        gDrift = mat("ground_drift", "awaken:ember_ash_drift");
        gBits = mat("ground_bits", "awaken:ember_cinders");
        rail = GatePainter.state("minecraft:rail[shape=north_south]");
        railX = GatePainter.state("minecraft:rail[shape=east_west]");
        chain = GatePainter.state("minecraft:iron_chain[axis=y]");
        String n = name();
        mine = n.equals("working_mine");
        chasm = n.equals("open_chasm");
        forge = n.equals("forge_below");
        sea = c.sea;
    }

    @Override
    int glow() {
        return 0xff9a40;
    }

    @Override
    BlockState skin(int x, int y, int z, boolean floor, boolean ceiling) {
        if (y < W - 1) return Hash.noise3(seed + 9, x, y, z, 3) > 0.45 ? crust : basalt;
        if (floor) return Hash.noise(seed + 3, x, z, 5) > 0.6 ? ash : slag;
        double n = Hash.noise3(seed + 5, x, y, z, 4);
        return n > (ceiling ? 0.7 : 0.64) ? cracked : basalt;
    }

    @Override
    boolean depth() {
        return true;
    }

    @Override
    BlockState ground(int part, int x, int y, int z) {
        return kit(part, Hash.noise(seed + 3, x, z, 5) > 0.6, gFloorSlab, gFloorStairs, gWallSlab, gWallStairs, gDrift, gBits);
    }

    @Override
    double bits() {
        return 0.34;
    }

    @Override
    BlockState creep(int x, int y, int z) {
        return crust;
    }

    @Override
    BlockState formation(int part, int x, int y, int z) {
        // basalt pillars banded with glowing cracks, cooled lava humps, a collapsed frame in the mine, slag heaps
        return pieces(part, x, y, z, basalt, cracked, basalt, crust, mine ? planks : basalt, slag);
    }

    @Override
    BlockState screen(int lat, int rise, int side) {
        return lat % 4 == 0 ? timber : rise % 4 == 3 ? timberX : planks;
    }

    @Override
    List<FloorAir.Mist> mists() {
        return sea ? mist(0x5a2a14, 0.55f, 0x2a1a14, 0.3f) : mist(0x3a2a22, 0.45f, 0x221a18, 0.25f);
    }

    @Override
    void cavern(Room r, CaveEngine.Kind k) {
        int id = r.id;
        PassageEngine.Shape sh = c.shapes.get(id);
        grow(r, 0.025, 13, (x, y, z, len, i) -> crystal);
        hang(r, 0.012, 2, 5, 11, (x, y, z, len, i) -> crystal);
        if (!sea && k != CaveEngine.Kind.ARRIVAL) props(r, k);
        if (sea && sh != null) {
            // a crust island: lamps on posts round its rim, ash drifts on its top
            for (int i = 0; i < 3; i++) {
                double[] at = spot(r, 30 + i * 3, 0.3, 0.7, 2);
                if (at == null) continue;
                post((int) Math.floor(at[0]), (int) Math.floor(at[1]), 3, true);
            }
        }
        if (chasm && c.inLakeAny(id)) bridge(r);
        if (forge && k == CaveEngine.Kind.GREAT && hero("molten_falls")) falls(r);
        if (k != CaveEngine.Kind.ARRIVAL) touchstones(r, bellows, k == CaveEngine.Kind.GREAT ? 2 : 1, "foremans_bellows", 60);
        if (k == CaveEngine.Kind.GREAT) {
            if (mine) wallNook(r, "old_lift", 70);
            if (forge) wallNook(r, "first_anvil", 70);
            if (sea) {
                if (sh != null) nook("the_crust_shrine", (int) sh.cx() - 1, 0, (int) sh.cz() - 1, (int) sh.cx() + 1, 2, (int) sh.cz() + 1);
            }
        }
        if (chasm && first(r)) wallNook(r, "the_dropped_lamp", 72);
        if (sh != null) light(sh.cx(), 2, sh.cz(), sea ? 0xff6a20 : 0xffa050, 10f + r.gw * 3, sea ? 0.8f : 0.55f);
        if (c.inLakeAny(id) && sh != null) light(sh.cx(), -1, sh.cz(), 0xff5010, 14f + r.gw * 4, 0.9f);
    }

    boolean first(Room r) {
        for (int w : c.chain.places) {
            CaveEngine.Kind k = c.kinds.get(w);
            if ((k == CaveEngine.Kind.CAVERN || k == CaveEngine.Kind.GREAT) && !p.sealable(p.world(w))) return w == r.id;
        }
        return false;
    }

    /** A cavern of the mine: timber props round its wall, crates of planks, a lamp on every other prop. */
    void props(Room r, CaveEngine.Kind k) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        int n = Math.max(4, (int) (sh.a() + sh.b()) / 3);
        for (int i = 0; i < n; i++) {
            double a = (i + unit(r.id, 5) * 0.5) / n * Math.PI * 2;
            double x = sh.cx() + Math.cos(a) * sh.a() * 0.8, z = sh.cz() + Math.sin(a) * sh.b() * 0.8;
            if (c.d(r.id, x, z) > -1.5 || nearDoor(r, x, z, 3.5) || c.inLake(r.id, x, z)) continue;
            int h = Math.min(4, top(r.id, (int) x, (int) z) - W);
            if (h < 3) continue;
            post((int) Math.floor(x), (int) Math.floor(z), h, i % 2 == 0);
        }
        // crates of planks stacked by the wall
        for (int i = 0; i < 2 + r.gw; i++) {
            double[] at = spot(r, 40 + i * 3, 0.55, 0.8, 1.5);
            if (at == null) continue;
            int x = (int) Math.floor(at[0]), z = (int) Math.floor(at[1]);
            int tall = unit(r.id, 44 + i) < 0.4 ? 2 : 1;
            c.s.op(x, W, z, x, W + tall - 1, z, (b, xx, yy, zz) -> {
                if (b.open(xx, yy, zz)) b.set(xx, yy, zz, planks);
            });
            block(at[0], at[1], 0.8);
        }
        if (mine && hero("rails")) rails(r);
    }

    /** A timber post {@code h} high, and the lamp hung beside its top. */
    void post(int x, int z, int h, boolean lit) {
        c.s.op(x, W, z, x, W + h - 1, z, (b, xx, y, zz) -> {
            if (b.open(xx, y, zz)) b.set(xx, y, zz, timber);
        });
        if (lit) {
            c.s.putOpen(x + 1, W + h - 1, z, lamp);
            light(x + 1, h - 1, z, 0xffb060, 7f, 0.6f);
        }
        block(x + 0.5, z + 0.5, 0.7);
    }

    /** The Working Mine's rails: a straight run across a cavern's middle, east to west or north to south. */
    void rails(Room r) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        boolean ns = unit(r.id, 7) < 0.5;
        int len = (int) ((ns ? sh.b() : sh.a()) * 0.7);
        int cx = (int) sh.cx(), cz = (int) sh.cz();
        for (int i = -len; i <= len; i++) {
            int x = ns ? cx : cx + i, z = ns ? cz + i : cz;
            if (c.d(r.id, x + 0.5, z + 0.5) > -2 || c.inLake(r.id, x + 0.5, z + 0.5)) continue;
            c.s.putOpen(x, W, z, ns ? rail : railX);
        }
    }

    /** The Open Chasm's bridge: planks over a cavern's lava river, from its first doorway's side across the middle. */
    void bridge(Room r) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        Room.Door d = r.doors.isEmpty() ? null : r.doors.get(0);
        boolean alongX = d == null || d.side == 1 || d.side == 3;
        int len = (int) ((alongX ? sh.a() : sh.b()) * 0.75);
        int cx = (int) sh.cx(), cz = (int) sh.cz();
        for (int i = -len; i <= len; i++) {
            for (int w = -1; w <= 1; w++) {
                int x = alongX ? cx + i : cx + w, z = alongX ? cz + w : cz + i;
                c.s.put(x, W - 1, z, planks);
                if (w != 0 && Math.floorMod(i, 4) == 0) {
                    c.s.putOpen(x, W, z, chain);
                    c.s.putOpen(x, W + 1, z, chain);
                }
            }
        }
    }

    /** The Forge Below's falls: three curtains of molten stone from the deep cavern's roof into its middle. */
    void falls(Room r) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        int id = r.id;
        int[] b = box(r);
        for (int i = 0; i < 3; i++) {
            double a = unit(id, 90 + i) * Math.PI * 2;
            double fx = sh.cx() + Math.cos(a) * sh.a() * 0.55, fz = sh.cz() + Math.sin(a) * sh.b() * 0.55;
            if (nearDoor(r, fx, fz, 4)) continue;
            int x = (int) Math.floor(fx), z = (int) Math.floor(fz);
            c.s.op(x - 1, W - 1, z - 1, x + 1, W + c.roofs.getOrDefault(id, c.roof) + 2, z + 1, (buf, xx, y, zz) -> {
                if (Math.abs(xx - x) + Math.abs(zz - z) > 1) return;
                if (y == W - 1) buf.set(xx, y, zz, crust);
                else if (buf.open(xx, y, zz)) buf.set(xx, y, zz, fall);
            });
            block(fx, fz, 1.6);
            light(fx, 8, fz, 0xff7020, 16f, 0.9f);
            c.shafts.add(new FloorAir.Shaft(x, c.roofs.getOrDefault(id, c.roof), z, x, 0, z, 1.5f, 0xff7020, 0.6f));
        }
    }

    @Override
    void tunnel(Room r, ChainLayout.Link l) {
        int[] b = box(r);
        int ox = b[0], oz = b[1];
        ChainLayout.Way way = ChainLayout.Way.of(r, l, seed);
        if (sea) {
            // a causeway of slag over the magma, lamps on posts every few blocks
            c.s.op(ox, W, oz, ox + 15, W, oz + 15, (buf, x, y, z) -> {
                double[] w = way.at(x + 0.5, z + 0.5);
                if (w == null || !buf.open(x, y, z) || Math.abs(Math.abs(w[0]) - (c.hw - 0.3)) > 0.5 || nearDoor(r, x + 0.5, z + 0.5, 3.5)) return;
                if (Math.floorMod((int) Math.round(w[1]), 6) == 3) buf.set(x, y, z, timber);
            });
            return;
        }
        // timber frames every five blocks: posts at the sides, a beam across, a lamp on every other frame
        c.s.op(ox, W, oz, ox + 15, W + 4, oz + 15, (buf, x, y, z) -> {
            if (!buf.open(x, y, z)) return;
            double[] w = way.at(x + 0.5, z + 0.5);
            if (w == null) return;
            int along = (int) Math.round(w[1]);
            if (Math.floorMod(along, 5) != 2) return;
            double lat = Math.abs(w[0]);
            double half = c.hw + 0.4;
            if (y <= W + 2 && lat > half - 1.1 && lat <= half) buf.set(x, y, z, timber);
            else if (y == W + 3 && lat <= half) buf.set(x, y, z, Math.abs(w[2]) > 0.5 ? timberX : timberZ);
        });
        if (mine) c.s.op(ox, W, oz, ox + 15, W, oz + 15, (buf, x, y, z) -> {
            if (!buf.open(x, y, z) || !solid(buf, x, y - 1, z)) return;
            double[] w = way.at(x + 0.5, z + 0.5);
            if (w == null || Math.abs(w[0]) > 0.5 || (l.in() + 2) % 4 != l.out()) return;
            buf.set(x, y, z, way.alongZ() ? rail : railX);
        });
        grow(r, 0.03, 17, (x, y, z, len, i) -> crystal);
    }

    @Override
    void secret(Room r) {
        // the Heart Seam: the inside of a geode, every face crystal or crust, crystals growing in from all round
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        int[] b = box(r);
        int id = r.id;
        c.s.op(b[0], W, b[1], b[2], W + c.roofs.getOrDefault(id, c.roof) + 3, b[3], (buf, x, y, z) -> {
            if (!buf.open(x, y, z)) return;
            boolean nearWall = solid(buf, x, y + 1, z) || solid(buf, x, y - 1, z);
            if (nearWall && Hash.unit(seed + 41, x * 31 + y, z) < 0.3 && !nearDoor(r, x + 0.5, z + 0.5, 2.5)) buf.set(x, y, z, crystal);
        });
        nook("the_heart", (int) sh.cx() - 1, 0, (int) sh.cz() - 1, (int) sh.cx() + 1, 2, (int) sh.cz() + 1);
        light(sh.cx(), 3, sh.cz(), 0xffa040, 16f, 1f);
    }
}
