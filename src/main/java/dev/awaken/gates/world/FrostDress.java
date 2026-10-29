package dev.awaken.gates.world;

import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The Frost Monastery (SPEC §38.12): a monastery on a mountain, white plaster and red timber under snow, prayer flags and
 * butter lamps. The Cloisters are its courtyards under the sky, walks roofed round them and halls standing between with
 * snow on their roofs; the Ice Caves are blue ice under it, frozen falls in the deep cavern; the Cliff Stair is pillars of
 * rock standing out of a sea of cloud with a shrine on each; the Still Snow a valley ringed by peaks round a white stupa;
 * the Frozen Choir a hall of monks of ice in rings.
 */
final class FrostDress extends Dress {
    final BlockState stone, carved, tiles, snow, plaster, timber, ice, iceGlow, icicles, flags, lamp, crystal, wheel, fence, chain;
    /** Shaped ground (SPEC §38.14): stone (tile in the cloisters) slabs and stairs, stone at the walls, snow drifted, ice chips */
    final BlockState gFloorSlab, gFloorStairs, gWallSlab, gWallStairs, gDrift, gBits;
    final boolean cloisters, caves, stair, still, open;

    FrostDress(CaveEngine.Cave c) {
        super(c);
        stone = mat("wall", "awaken:frost_stone");
        carved = mat("wall_glow", "awaken:frost_stone_carved");
        tiles = mat("floor", "awaken:frost_tiles");
        snow = mat("snow", "awaken:frost_snow");
        plaster = mat("plaster", "awaken:frost_plaster");
        timber = mat("timber", "awaken:frost_timber");
        ice = mat("ice", "awaken:frost_ice");
        iceGlow = mat("ice_glow", "awaken:frost_ice_glowing");
        icicles = mat("icicles", "awaken:frost_icicles");
        flags = mat("flags", "awaken:frost_flags");
        lamp = mat("lamp", "awaken:frost_butter_lamp");
        crystal = mat("crystal", "awaken:frost_crystal");
        wheel = mat("touch", "awaken:frost_prayer_wheel");
        gWallSlab = mat("wall_slab", "awaken:frost_stone_slab");
        gWallStairs = mat("wall_stairs", "awaken:frost_stone_stairs");
        gDrift = mat("ground_drift", "awaken:frost_snow_drift");
        gBits = mat("ground_bits", "awaken:frost_chips");
        fence = GatePainter.state("minecraft:spruce_fence");
        chain = GatePainter.state("minecraft:iron_chain[axis=y]");
        String n = name();
        cloisters = n.equals("cloisters");
        caves = n.equals("ice_caves");
        stair = n.equals("cliff_stair");
        still = n.equals("still_snow");
        open = c.open;
        gFloorSlab = mat("ground_slab", caves ? "awaken:frost_stone_slab" : "awaken:frost_tiles_slab");
        gFloorStairs = mat("ground_stairs", caves ? "awaken:frost_stone_stairs" : "awaken:frost_tiles_stairs");
    }

    @Override
    int glow() {
        return 0xffd890;
    }

    @Override
    BlockState rail() {
        return fence;
    }

    // ---- the land's shape ---------------------------------------------------------------------------------------------

    /** The Cloisters' halls between the courtyards: a building on every lot, its roof pitched along one way or the other. */
    int hall(int x, int z) {
        int u = Math.floorMod(x + 5, 16), v = Math.floorMod(z + 3, 16);
        if (u < 3 || v < 3) return c.lid - 1;
        int lx = Math.floorDiv(x + 5, 16), lz = Math.floorDiv(z + 3, 16);
        int h = c.lid + 2 + (int) (Hash.unit(seed, lx * 41 + 3, lz) * 7);
        int across = Hash.unit(seed, lx * 41 + 7, lz) < 0.5 ? v - 3 : u - 3;
        return h + Math.min(across, 12 - across) / 2;
    }

    /** The Still Snow's valley: peaks rising toward the plot's edge all round. */
    int valley(int x, int z) {
        double r = Math.hypot(x - 128, z - 128) / 128.0;
        return c.lid - 1 + (int) (Math.max(0, r - 0.3) * 64 + Hash.noise(seed + 41, x, z, 20) * 9);
    }

    @Override
    int skyline(int x, int z) {
        if (cloisters) return hall(x, z);
        if (still) return valley(x, z);
        return super.skyline(x, z);
    }

    // ---- the skin -----------------------------------------------------------------------------------------------------

    @Override
    BlockState skin(int x, int y, int z, boolean fl, boolean ceiling) {
        int rise = y - W;
        if (caves) {
            // ice faces are set over this by iceFaces: deeper in, the mountain's stone
            if (fl) return Hash.noise(seed + 3, x, z, 5) > 0.6 ? ice : snow;
            return ceiling ? ice : stone;
        }
        if (stair) {
            if (fl) return snow;
            return Hash.noise3(seed + 5, x, y, z, 5) > 0.68 ? ice : stone;
        }
        if (still) {
            if (fl) return snow;
            if (ceiling) return stone;
            return Hash.noise3(seed + 7, x, y, z, 7) > 0.62 ? ice : rise > 6 && Hash.noise(seed + 9, x, z, 3) > 0.7 ? snow : stone;
        }
        // the Cloisters: courtyards of tiles drifted with snow, halls of white plaster under red timber eaves, snow on the roofs
        if (fl) return rise >= c.lid ? snow : Hash.noise(seed + 3, x, z, 5) > 0.62 ? snow : tiles;
        if (ceiling || rise < 0) return stone;
        if (rise == 0) return carved;
        if (rise >= c.lid - 3 && rise < c.lid) return timber;
        if (rise >= c.lid) return Hash.noise3(seed + 11, x, y, z, 3) > 0.6 ? snow : timber;
        int lat = Math.floorMod(x + z, 5);
        if (lat == 0) return timber;
        if (lat == 2 && (rise == 2 || rise == 3)) return carved;
        return plaster;
    }

    @Override
    boolean depth() {
        return true;
    }

    @Override
    BlockState ground(int part, int x, int y, int z) {
        return kit(part, caves ? Hash.noise(seed + 3, x, z, 5) <= 0.6 : stair || still || Hash.noise(seed + 3, x, z, 5) > 0.62, gFloorSlab, gFloorStairs, gWallSlab, gWallStairs, gDrift, gBits);
    }

    @Override
    double bits() {
        return 0.12;
    }

    @Override
    BlockState creep(int x, int y, int z) {
        return snow;
    }

    @Override
    BlockState formation(int part, int x, int y, int z) {
        // ice pillars banded with glowing ice, ice boulders under snow, a fallen icefall, a heap of snow
        return pieces(part, x, y, z, ice, iceGlow, ice, snow, ice, snow);
    }

    @Override
    BlockState screen(int lat, int rise, int side) {
        if (caves) return rise % 4 == 2 ? iceGlow : ice;
        return lat % 5 == 0 || rise % 6 == 5 ? timber : rise == 0 ? carved : plaster;
    }

    @Override
    List<FloorAir.Mist> mists() {
        if (caves) return mist(0x203a50, 0.4f, 0x10202c, 0.2f);
        List<FloorAir.Mist> l = mist(0xe8eef8, 0.22f, 0xd8e0ec, 0.1f);
        // the sea of cloud under the Cliff Stair, thick where it lies
        if (stair) l.add(new FloorAir.Mist(-c.clouds - 3, 6, 0xf4f6fa, 0.6f));
        return l;
    }

    // ---- places -------------------------------------------------------------------------------------------------------

    @Override
    void cavern(Room r, CaveEngine.Kind k) {
        int id = r.id;
        PassageEngine.Shape sh = c.shapes.get(id);
        if (caves) {
            int[] b = box(r);
            if (hero("ice_walls")) iceFaces(b[0], W, b[1], b[2], W + c.roofs.getOrDefault(id, c.roof) + 2, b[3]);
            hang(r, 0.05, 4, 4, 11, (x, y, z, len, i) -> icicles);
            grow(r, 0.03, 13, (x, y, z, len, i) -> crystal);
            if (k == CaveEngine.Kind.GREAT && hero("frozen_falls")) falls(r);
        }
        if (cloisters) {
            if (hero("cloister_walks") && k != CaveEngine.Kind.ARRIVAL && r.gw * r.gh >= 2) walks(r);
            if (hero("prayer_flags")) strings(r, 7);
            if (k == CaveEngine.Kind.ARRIVAL || k == CaveEngine.Kind.GREAT) wheels(r);
        }
        if (stair) {
            if (hero("the_ledges")) ledges(r);
            // the highest shrine is on the great island, or the last one when no island is great
            if (k != CaveEngine.Kind.ARRIVAL) shrine(r, k == CaveEngine.Kind.GREAT || (c.great < 0 && k == CaveEngine.Kind.END));
        }
        if (still) {
            grow(r, 0.01, 13, (x, y, z, len, i) -> crystal);
            if (k == CaveEngine.Kind.GREAT && hero("the_stupa")) stupa(r);
        }
        if (k != CaveEngine.Kind.ARRIVAL) touchstones(r, wheel, k == CaveEngine.Kind.GREAT ? 2 : 1, "the_first_prayer", 60);
        if (k == CaveEngine.Kind.GREAT && sh != null) {
            if (cloisters) wallNook(r, "the_bell_rope", 70);
            if (caves) {
                int[] at = wallNook(r, "the_frozen_monk", 70);
                // the monk himself at the back of his nook, frozen where he sat
                if (at != null) monk(at[0], at[1]);
            }
            if (still) wallNook(r, "the_still_heart", 70);
        }
        if (sh != null && (!open || c.dark)) light(sh.cx(), 3, sh.cz(), caves ? 0x9ad0ff : 0xffd890, 10f + r.gw * 3, 0.5f);
    }

    /** The Ice Caves' walls: the faces you can see turned to ice, a band of it glowing at shoulder height. */
    void iceFaces(int x0, int y0, int z0, int x1, int y1, int z1) {
        c.s.op(x0, y0, z0, x1, y1, z1, (b, x, y, z) -> {
            if (b.get(x, y, z) != Sculpt.SOLID) return;
            if (!(b.open(x + 1, y, z) || b.open(x - 1, y, z) || b.open(x, y, z + 1) || b.open(x, y, z - 1))) return;
            if (Math.floorMod(y - W, 6) == 2 && Hash.noise3(seed + 15, x, y, z, 6) > 0.4) b.set(x, y, z, iceGlow);
            else if (Hash.noise3(seed + 5, x, y, z, 4) > 0.35) b.set(x, y, z, ice);
        });
    }

    /** The deep cavern's falls: three columns of ice from roof to floor, glowing at their hearts, crystals at their feet. */
    void falls(Room r) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        int id = r.id;
        int roof = c.roofs.getOrDefault(id, c.roof);
        for (int i = 0; i < 3; i++) {
            double a = unit(id, 90 + i) * Math.PI * 2;
            double fx = sh.cx() + Math.cos(a) * sh.a() * 0.5, fz = sh.cz() + Math.sin(a) * sh.b() * 0.5;
            if (nearDoor(r, fx, fz, 4) || c.d(id, fx, fz) > -3) continue;
            int x = (int) Math.floor(fx), z = (int) Math.floor(fz);
            c.s.op(x - 2, W, z - 2, x + 2, W + roof + 2, z + 2, (buf, xx, y, zz) -> {
                if (!buf.open(xx, y, zz)) return;
                int d = Math.abs(xx - x) + Math.abs(zz - z);
                // wider at the foot where the ice piled up
                int reach = y - W < 3 ? 2 : 1;
                if (d == 0) buf.set(xx, y, zz, iceGlow);
                else if (d <= reach) buf.set(xx, y, zz, ice);
                else if (y == W && d == reach + 1 && Hash.unit(seed + 17, xx, zz) < 0.5) buf.set(xx, y, zz, crystal);
            });
            block(fx, fz, 2.5);
            c.shafts.add(new FloorAir.Shaft(x, roof, z, x, 0, z, 2f, 0xa8e0ff, 0.5f));
        }
    }

    /** A frozen monk: two of ice, a glowing head. */
    void monk(int x, int z) {
        c.s.putOpen(x, W, z, ice);
        c.s.putOpen(x, W + 1, z, iceGlow);
    }

    @Override
    double headroom(Room r, CaveEngine.Kind k, int x, int z) {
        // under a cloister walk's roof (four over the floor line) the ground stays at the line
        if (!hero("cloister_walks") || k == CaveEngine.Kind.ARRIVAL || r.gw * r.gh < 2) return 999;
        double px = x + 0.5, pz = z + 0.5;
        return c.d(r.id, px, pz) >= -4.2 && !nearDoor(r, px, pz, 3) ? 3.5 : 999;
    }

    /**
     * The Cloisters' walks: a roofed walk round the courtyard's edge, red posts every four blocks along its inner line and
     * a roof of plaster with snow on it out to the wall, left open over the doorways.
     */
    void walks(Room r) {
        int id = r.id;
        int[] b = box(r);
        c.s.op(b[0], W, b[1], b[2], W + 5, b[3], (buf, x, y, z) -> {
            if (!buf.open(x, y, z)) return;
            double px = x + 0.5, pz = z + 0.5, d = c.d(id, px, pz);
            if (d < -4.2 || nearDoor(r, px, pz, 3)) return;
            boolean posts = d < -3.2;
            // a hidden light under the roof every few steps, so the walk isn't dark however deep it runs
            if (y == W + 3 && d > -3.2 && d < -0.6 && Math.floorMod(x + z, 4) == 2) {
                buf.set(x, y, z, c.light);
                return;
            }
            if (y < W + 4) {
                if (posts && Math.floorMod(x + z, 4) == 0) buf.set(x, y, z, timber);
            } else if (y == W + 4) buf.set(x, y, z, posts ? timber : plaster);
            else buf.set(x, y, z, snow);
        });
    }

    /** Strings of prayer flags across a place from wall to wall, sagging, {@code h} over the floor at the walls. */
    void strings(Room r, int h) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        int id = r.id;
        for (int i = 0; i < 2; i++) {
            double a = unit(id, 120 + i) * Math.PI;
            double dx = Math.cos(a), dz = Math.sin(a), len = Math.hypot(sh.a() * dx, sh.b() * dz);
            for (double t = -len; t <= len; t += 0.5) {
                double x = sh.cx() + dx * t, z = sh.cz() + dz * t;
                if (c.d(id, x, z) > -0.5) continue;
                double q = t / len;
                int y = W + h + 2 - (int) Math.round((1 - q * q) * 3);
                c.s.putOpen((int) Math.floor(x), y, (int) Math.floor(z), flags);
            }
        }
    }

    /**
     * A row of prayer wheels along one side of a place: each on a carved stone foot, turning slowly (props the live layer
     * turns), the row's touchstone at its end.
     */
    void wheels(Room r) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        int id = r.id;
        int side = (CaveEngine.Cave.back(r) + 1) % 4;
        double nx = GatePainter.DX[side], nz = GatePainter.DZ[side];
        double reach = (nx != 0 ? sh.a() : sh.b()) - 2.5;
        double ox = sh.cx() + nx * reach, oz = sh.cz() + nz * reach;
        for (int i = -3; i <= 3; i++) {
            double x = ox + nz * i * 2, z = oz + nx * i * 2;
            if (c.d(id, x, z) > -1.5 || nearDoor(r, x, z, 3) || p.nearAnchor(x, z, 3)) continue;
            int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
            if (!p.prop("wheel:6", bx, 1, bz, bx, 2, bz)) break;
            c.s.putOpen(bx, W, bz, carved);
            block(x, z, 0.8);
        }
    }

    /** The Cliff Stair's ledges: icicles hanging under each pillar's top where it overhangs. */
    void ledges(Room r) {
        int[] b = box(r);
        c.s.op(b[0], W - c.under, b[1], b[2], W - 2, b[3], (buf, x, y, z) -> {
            if (buf.get(x, y, z) != Sculpt.UNSET || buf.get(x, y + 1, z) != Sculpt.SOLID) return;
            long h = Hash.of(seed + 23, x, z);
            if (Hash.unit(h) < 0.35) buf.set(x, y, z, icicles);
        });
    }

    /** A little shrine on a pillar: a carved plinth with a butter lamp on it, a pole of red timber hung with flags. */
    void shrine(Room r, boolean highest) {
        double[] at = spot(r, 80, 0.15, 0.45, 2.5);
        if (at == null) return;
        int x = (int) Math.floor(at[0]), z = (int) Math.floor(at[1]);
        c.s.putOpen(x, W, z, carved);
        c.s.putOpen(x, W + 1, z, carved);
        c.s.putOpen(x, W + 2, z, lamp);
        int px = x + 2, pz = z;
        int pole = highest ? 6 : 4;
        for (int y = 0; y < pole; y++) c.s.putOpen(px, W + y, pz, timber);
        for (int t = 1; t <= 3; t++) c.s.putOpen(px, W + pole - 1 - t / 2, pz + t, flags);
        block(at[0] + 1, at[1], 2);
        light(at[0], 2, at[1], 0xffd890, 6f, 0.6f);
        // the highest shrine keeps its secret in front of it
        if (highest) nook("the_highest_shrine", x - 1, 0, z - 3, x + 1, 2, z - 2);
    }

    /**
     * The Still Snow's stupa: a square base three high, a white dome on it, a carved spire up to a gilded lamp, prayer
     * flags strung from the spire's top down to the four corners, butter lamps round the foot.
     */
    void stupa(Room r) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        double cx = sh.cx(), cz = sh.cz();
        int ix = (int) Math.floor(cx), iz = (int) Math.floor(cz);
        double R = 5;
        int top = 18;
        c.s.op(ix - 6, W, iz - 6, ix + 6, W + top, iz + 6, (b, x, y, z) -> {
            if (!b.open(x, y, z)) return;
            int rise = y - W, dx = Math.abs(x - ix), dz = Math.abs(z - iz);
            double d = Math.hypot(x + 0.5 - cx, z + 0.5 - cz);
            if (rise < 3) {
                if (dx <= 5 && dz <= 5) b.set(x, y, z, rise == 2 ? carved : plaster);
                return;
            }
            double v = rise - 3;
            if (v < R && d * d + v * v < R * R) {
                b.set(x, y, z, v < 1 ? carved : plaster);
                return;
            }
            if (dx == 0 && dz == 0 && rise < top) b.set(x, y, z, rise == top - 1 ? lamp : carved);
            else if (rise >= 8 && rise < top - 3 && dx <= 1 && dz <= 1 && (rise - 8) % 3 == 0) b.set(x, y, z, timber);
        });
        // the flags from the spire's top to the base's corners
        for (int k = 0; k < 4; k++) {
            int sx = k % 2 == 0 ? 1 : -1, sz = k < 2 ? 1 : -1;
            for (double t = 0.15; t < 1; t += 0.06) {
                double x = cx + sx * 9 * t, z = cz + sz * 9 * t;
                int y = W + top - 2 - (int) Math.round(t * (top - 5) + Math.sin(t * Math.PI) * 1.5);
                c.s.putOpen((int) Math.floor(x), y, (int) Math.floor(z), flags);
            }
        }
        for (int k = 0; k < 8; k++) {
            double a = k / 8.0 * Math.PI * 2;
            c.s.putOpen((int) Math.floor(cx + Math.cos(a) * 7.5), W, (int) Math.floor(cz + Math.sin(a) * 7.5), lamp);
        }
        block(cx, cz, 8.5);
        light(cx, top, cz, 0xffe0a0, 16f, 0.7f);
    }

    @Override
    void tunnel(Room r, ChainLayout.Link l) {
        if (caves) {
            int[] b = box(r);
            if (hero("ice_walls")) iceFaces(b[0], W, b[1], b[2], W + 9, b[3]);
            hang(r, 0.04, 3, 3, 17, (x, y, z, len, i) -> icicles);
            return;
        }
        if (stair) {
            // a rope bridge: flags strung along one rail
            int[] b = box(r);
            ChainLayout.Way way = ChainLayout.Way.of(r, l, seed);
            c.s.op(b[0], W + 1, b[1], b[2], W + 1, b[3], (buf, x, y, z) -> {
                if (buf.get(x, y, z) != Sculpt.UNSET || buf.get(x, y - 1, z) == Sculpt.UNSET) return;
                double[] w = way.at(x + 0.5, z + 0.5);
                if (w != null && w[0] > c.hw + 0.2 && w[0] < c.hw + 1.2 && Math.floorMod((int) Math.round(w[1]), 3) == 0) buf.set(x, y, z, flags);
            });
            return;
        }
        if (cloisters) {
            // lanes between the halls: a lamp in a niche of the wall every eight blocks
            int[] b = box(r);
            ChainLayout.Way way = ChainLayout.Way.of(r, l, seed);
            c.s.op(b[0], W, b[1], b[2], W, b[3], (buf, x, y, z) -> {
                if (!buf.open(x, y, z)) return;
                double[] w = way.at(x + 0.5, z + 0.5);
                if (w == null || Math.floorMod((int) Math.round(w[1]), 8) != 4 || nearDoor(r, x + 0.5, z + 0.5, 3)) return;
                double lat = Math.abs(w[0]);
                if (lat > c.hw - 0.9 && lat <= c.hw + 0.1) buf.set(x, y, z, lamp);
            });
        }
    }

    @Override
    void secret(Room r) {
        // the Frozen Choir: two rings of monks of ice facing the middle, icicles over them, the last note at the middle
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        int id = r.id;
        double cx = sh.cx(), cz = sh.cz();
        double rr = Math.min(sh.a(), sh.b());
        for (int ring = 0; ring < 2; ring++) {
            double R = rr * (ring == 0 ? 0.45 : 0.72);
            int n = ring == 0 ? 6 : 10;
            for (int i = 0; i < n; i++) {
                double a = (i + ring * 0.5) / n * Math.PI * 2;
                double x = cx + Math.cos(a) * R, z = cz + Math.sin(a) * R;
                if (nearDoor(r, x, z, 3) || c.d(id, x, z) > -1.5) continue;
                monk((int) Math.floor(x), (int) Math.floor(z));
            }
        }
        hang(r, 0.08, 5, 5, 19, (x, y, z, len, i) -> icicles);
        int[] b = box(r);
        iceFaces(b[0], W, b[1], b[2], W + c.roofs.getOrDefault(id, c.roof) + 2, b[3]);
        int nx = (int) cx, nz = (int) cz;
        nook("the_last_note", nx - 1, 0, nz - 1, nx + 1, 2, nz + 1);
        light(cx, 4, cz, 0xa8e0ff, 16f, 0.8f);
    }
}
