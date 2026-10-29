package dev.awaken.gates.world;

import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The Abyssal Shrine (SPEC §38.13): a shrine at the bottom of everything, near-black stone with runes glowing in it, pale
 * steps you can see in the dark, clusters of vigil candles and offering bowls. The Dark Steps are landings of black tile
 * hanging in the dark, joined by pale bridges, stairs off every landing going down into nothing; the Black Mirror is a
 * still black lake in a cave, shrines on its islands and stars hanging from its roof; Starfall is landings in a void full
 * of hanging stars; the Last Room is the same small room again and again (a chair, a table with a candle on it, a door
 * that doesn't open, a black window); the Eye (the secret world) is a glass floor over an eye as big as a room.
 */
final class AbyssDress extends Dress {
    final BlockState stone, tiles, pale, rune, glass, candles, star, veil, iris, pupil, sclera, offering, planks, stairs, chair,
            fence, plate, door, doorTop;
    /** Shaped ground (SPEC §38.14): tile slabs and stairs on the floor, the shrine's stone at the walls, wax stubs */
    final BlockState gFloorSlab, gFloorStairs, gWallSlab, gWallStairs, gDrift, gBits;
    final boolean steps, lake, starfall, room;
    private int[] secretBox;

    AbyssDress(CaveEngine.Cave c) {
        super(c);
        stone = mat("wall", "awaken:shrine_stone");
        tiles = mat("floor", "awaken:abyss_tiles");
        pale = mat("trim", "awaken:abyss_pale");
        rune = mat("wall_glow", "awaken:abyss_rune");
        glass = mat("glass", "awaken:abyss_glass");
        candles = mat("candles", "awaken:abyss_candles");
        star = mat("star", "awaken:abyss_star");
        veil = mat("veil", "awaken:abyss_veil");
        iris = mat("iris", "awaken:abyss_iris");
        pupil = mat("pupil", "awaken:abyss_pupil");
        sclera = mat("sclera", "awaken:abyss_sclera");
        offering = mat("touch", "awaken:abyss_offering");
        gFloorSlab = mat("ground_slab", "awaken:abyss_tiles_slab");
        gFloorStairs = mat("ground_stairs", "awaken:abyss_tiles_stairs");
        gWallSlab = mat("wall_slab", "awaken:shrine_stone_slab");
        gWallStairs = mat("wall_stairs", "awaken:shrine_stone_stairs");
        gDrift = null;
        gBits = mat("ground_bits", "awaken:abyss_wax_stubs");
        planks = GatePainter.state("minecraft:dark_oak_planks");
        stairs = GatePainter.state("minecraft:smooth_quartz_stairs");
        chair = GatePainter.state("minecraft:dark_oak_stairs");
        fence = GatePainter.state("minecraft:dark_oak_fence");
        plate = GatePainter.state("minecraft:dark_oak_pressure_plate");
        door = WorldMats.with(GatePainter.state("minecraft:dark_oak_door"), "half", "lower");
        doorTop = WorldMats.with(GatePainter.state("minecraft:dark_oak_door"), "half", "upper");
        String n = name();
        lake = n.equals("black_mirror");
        starfall = n.equals("starfall");
        room = n.equals("the_last_room");
        steps = !lake && !starfall && !room;
    }

    @Override
    int glow() {
        return 0xb888f4;
    }

    @Override
    BlockState rail() {
        return GatePainter.state("minecraft:polished_blackstone_wall");
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
        int rise = y - W;
        // the Eye: a floor of black glass you look down through
        if (inSecret(x, z)) {
            if (fl) return glass;
            return rise > 0 && Math.floorMod(rise, 4) == 2 && Math.floorMod(x + z, 5) == 0 ? rune : stone;
        }
        if (room) {
            if (fl) return planks;
            if (ceiling) return stone;
            return rise == 0 ? stone : rise == 4 ? rune : pale;
        }
        if (fl) {
            int id = p.b.at(x >> 4, z >> 4);
            CaveEngine.Kind k = id < 0 ? null : c.kinds.get(id);
            if (k == null || k == CaveEngine.Kind.LINK) return pale;
            // a pale rim round each landing, the black tile inside it
            return c.d(id, x + 0.5, z + 0.5) > -1.3 ? pale : tiles;
        }
        if (ceiling) return stone;
        return Math.floorMod(rise, 6) == 3 && Hash.noise3(seed + 5, x, y, z, 4) > 0.55 ? rune : stone;
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
        // old wax lies thick: the shrine grows nothing
        return 0.42;
    }

    @Override
    BlockState creep(int x, int y, int z) {
        return null;
    }

    @Override
    BlockState formation(int part, int x, int y, int z) {
        // stone pillars banded with runes, stone crusted pale, a fallen pale column, a heap of stone
        return pieces(part, x, y, z, stone, rune, stone, pale, pale, stone);
    }

    @Override
    BlockState screen(int lat, int rise, int side) {
        return rise == 0 ? pale : rise % 5 == 3 && lat % 4 == 0 ? rune : stone;
    }

    @Override
    List<FloorAir.Mist> mists() {
        List<FloorAir.Mist> l = mist(0x1a1028, 0.35f, 0x0c0814, 0.2f);
        // the dark under the landings, thick below
        if (c.isles) l.add(new FloorAir.Mist(-c.under - 6, 8, 0x140a24, 0.5f));
        return l;
    }

    // ---- the void round the places ------------------------------------------------------------------------------------

    @Override
    void world() {
        // Starfall: stars hanging in the dark all round the landings, above and below, never in the way
        if (!(starfall && hero("hanging_stars"))) return;
        c.s.op(0, W - c.under - 12, 0, 255, W + 22, 255, (b, x, y, z) -> {
            if (b.get(x, y, z) != Sculpt.UNSET) return;
            long h = Hash.of(seed + 91, x, y, z);
            if (Hash.unit(h) >= 0.0018) return;
            // not over a landing or a bridge, so nothing hangs where anyone walks
            if (y >= W - 1 && y <= W + 4 && p.b.at(x >> 4, z >> 4) >= 0) return;
            b.set(x, y, z, star);
        });
    }

    // ---- places -------------------------------------------------------------------------------------------------------

    @Override
    void cavern(Room r, CaveEngine.Kind k) {
        int id = r.id;
        PassageEngine.Shape sh = c.shapes.get(id);
        if (room) lastRoom(r, k);
        else {
            grow(r, steps ? 0.03 : 0.02, 31, (x, y, z, len, i) -> i < 2 ? candles : null);
            if (k != CaveEngine.Kind.ARRIVAL && (steps || starfall) && hero(steps ? "the_way_down" : "hanging_stars")) stairDown(r);
            if (lake) {
                hang(r, 0.015, 1, 6, 33, (x, y, z, len, i) -> star);
                hang(r, 0.01, 3, 5, 34, (x, y, z, len, i) -> veil);
                if (k == CaveEngine.Kind.GREAT && hero("the_still_lake")) obelisks(r);
            }
        }
        if (k != CaveEngine.Kind.ARRIVAL) touchstones(r, offering, k == CaveEngine.Kind.GREAT ? 2 : 1, "the_first_offering", 60);
        boolean keep = k == CaveEngine.Kind.GREAT || (c.great < 0 && k == CaveEngine.Kind.END);
        if (keep && sh != null) {
            String secret = steps ? "the_bottom_step" : lake ? "the_still_reflection" : starfall ? "the_fallen_star" : "the_key_under_the_mat";
            double[] at = spot(r, 70, 0.3, 0.7, 2.5);
            if (at != null) {
                int x = (int) Math.floor(at[0]), z = (int) Math.floor(at[1]);
                nook(secret, x - 1, 0, z - 1, x + 1, 2, z + 1);
                if (has(secret)) {
                    c.s.putOpen(x, W, z, starfall ? star : candles);
                    block(at[0], at[1], 2);
                }
            }
        }
        if (sh != null) light(sh.cx(), 3, sh.cz(), room ? 0xffd8a0 : 0xb888f4, 9f + r.gw * 3, 0.45f);
    }

    /** A pale stair off a landing's edge going down into the dark, ten steps, to nowhere. */
    void stairDown(Room r) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        for (int t = 0; t < 8; t++) {
            int side = (int) (unit(r.id, 97 + t) * 4);
            double reach = (side % 2 == 1 ? sh.a() : sh.b());
            double ex = sh.cx() + GatePainter.DX[side] * reach, ez = sh.cz() + GatePainter.DZ[side] * reach;
            if (nearDoor(r, ex, ez, 5) || p.nearAnchor(ex, ez, 4)) continue;
            // from the landing's lip outward and down
            double lx = ex, lz = ez;
            for (int s = 0; s < 10 && c.d(r.id, lx, lz) > -0.5; s++) {
                lx -= GatePainter.DX[side] * 0.5;
                lz -= GatePainter.DZ[side] * 0.5;
            }
            String face = switch (side) {
                case 0 -> "south";
                case 1 -> "west";
                case 2 -> "north";
                default -> "east";
            };
            BlockState step = WorldMats.with(stairs, "facing", face);
            int x0 = (int) Math.floor(lx) + GatePainter.DX[side], z0 = (int) Math.floor(lz) + GatePainter.DZ[side];
            for (int i = 0; i < 10; i++) {
                int x = x0 + GatePainter.DX[side] * i, z = z0 + GatePainter.DZ[side] * i;
                for (int w = -1; w <= 1; w++) {
                    int sx = x + (side % 2 == 0 ? w : 0), sz = z + (side % 2 == 0 ? 0 : w);
                    int y = W - 1 - i;
                    c.s.op(sx, y, sz, sx, y, sz, (b, xx, yy, zz) -> {
                        if (b.get(xx, yy, zz) == Sculpt.UNSET) b.set(xx, yy, zz, step);
                    });
                    if (i % 3 == 1 && w != 0) c.s.op(sx, y + 1, sz, sx, y + 1, sz, (b, xx, yy, zz) -> {
                        if (b.get(xx, yy, zz) == Sculpt.UNSET) b.set(xx, yy, zz, candles);
                    });
                }
            }
            return;
        }
    }

    /** The Black Mirror's great island: four obelisks of black glass round a carved floor, a rune at each one's foot. */
    void obelisks(Room r) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        double R = Math.min(sh.a(), sh.b()) * 0.45;
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + 0.4;
            double x = sh.cx() + Math.cos(a) * R, z = sh.cz() + Math.sin(a) * R;
            if (nearDoor(r, x, z, 3) || c.d(r.id, x, z) > -2) continue;
            int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
            c.s.putOpen(bx, W, bz, rune);
            for (int y = 1; y < 6; y++) c.s.putOpen(bx, W + y, bz, glass);
            block(x, z, 1.5);
        }
    }

    /**
     * The Last Room: the same small room every time, furnished the same way from its middle. A chair at a table with a
     * candle on it, a door against the far wall that doesn't open, a black window, a candle in each corner.
     */
    void lastRoom(Room r, CaveEngine.Kind k) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        int cx = (int) Math.floor(sh.cx()), cz = (int) Math.floor(sh.cz());
        if (k != CaveEngine.Kind.ARRIVAL && !nearDoor(r, cx + 0.5, cz + 0.5, 3) && !p.nearAnchor(cx + 0.5, cz + 0.5, 3)) {
            c.s.putOpen(cx, W, cz, fence);
            c.s.putOpen(cx, W + 1, cz, plate);
            c.s.putOpen(cx, W + 2, cz, candles);
            c.s.putOpen(cx - 1, W, cz, WorldMats.with(chair, "facing", "west"));
            block(cx, cz, 2);
        }
        // a candle in each corner the room reaches
        double qa = sh.a() - 1.5, qb = sh.b() - 1.5;
        for (int i = 0; i < 4; i++) {
            double x = sh.cx() + (i % 2 == 0 ? -qa : qa), z = sh.cz() + (i < 2 ? -qb : qb);
            if (c.d(r.id, x, z) < -0.5 && !nearDoor(r, x, z, 2.5)) c.s.putOpen((int) Math.floor(x), W, (int) Math.floor(z), candles);
        }
        // the door that doesn't open and the black window, against whichever walls they find
        int[] b = box(r);
        c.s.op(b[0] + 1, W, b[1] + 1, b[2] - 1, W, b[3] - 1, (buf, x, y, z) -> {
            if (!buf.open(x, W, z) || !buf.open(x, W + 1, z) || nearDoor(r, x + 0.5, z + 0.5, 3)) return;
            int lx = x - cx, lz = z - cz;
            if (lx == 0 && buf.get(x, W, z - 1) == Sculpt.SOLID && buf.get(x, W + 2, z - 1) == Sculpt.SOLID) {
                buf.set(x, W, z, WorldMats.with(door, "facing", "south"));
                buf.set(x, W + 1, z, WorldMats.with(doorTop, "facing", "south"));
            } else if (lz == 0 && buf.get(x + 1, W + 2, z) == Sculpt.SOLID && buf.get(x + 1, W + 1, z) == Sculpt.SOLID
                    && buf.get(x + 1, W + 3, z) == Sculpt.SOLID) {
                buf.set(x + 1, W + 2, z, glass);
            }
        });
        hang(r, 0.02, 2, 4, 35, (x, y, z, len, i) -> veil);
    }

    @Override
    void tunnel(Room r, ChainLayout.Link l) {
        if (room) return;
        int[] b = box(r);
        ChainLayout.Way way = ChainLayout.Way.of(r, l, seed);
        // a vigil candle on the bridge's rail every six blocks
        c.s.op(b[0], W, b[1], b[2], W + 1, b[3], (buf, x, y, z) -> {
            if (y != W + (c.isles ? 1 : 0)) return;
            if (c.isles ? buf.get(x, y, z) != Sculpt.UNSET || buf.get(x, y - 1, z) == Sculpt.UNSET : !buf.open(x, y, z)) return;
            double[] w = way.at(x + 0.5, z + 0.5);
            if (w == null || Math.floorMod((int) Math.round(w[1]), 6) != 3) return;
            double lat = Math.abs(w[0]);
            if (c.isles ? lat > c.hw + 0.2 && lat < c.hw + 1.2 : lat > c.hw - 0.9 && lat <= c.hw + 0.1) buf.set(x, y, z, candles);
        });
    }

    @Override
    void secret(Room r) {
        // the Eye: under the glass floor, an eye as big as the room looking up at you
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        double cx = sh.cx(), cz = sh.cz(), ra = sh.a() - 1, rb = sh.b() - 1;
        int[] b = box(r);
        c.s.op(b[0] - 1, W - 6, b[1] - 1, b[2] + 1, W - 1, b[3] + 1, (buf, x, y, z) -> {
            double u = (x + 0.5 - cx) / ra, v = (z + 0.5 - cz) / rb, e = u * u + v * v;
            if (e > 1.15) return;
            // the glass over it reaches its rim, wherever the room's ragged floor stops short (no water in over the eye)
            if (y == W - 1) {
                if (buf.get(x, y, z) != Sculpt.SOLID) buf.mark(x, y, z, Sculpt.SOLID);
                return;
            }
            if (y == W - 6 || e > 1.0) {
                if (buf.get(x, y, z) != Sculpt.OPEN) buf.set(x, y, z, stone);
                return;
            }
            if (y == W - 5) {
                // the iris is round in the almond of the eye, the pupil a tall slit in it
                double ir = Math.hypot(u * ra, v * rb) / Math.min(ra, rb);
                buf.set(x, y, z, Math.abs(u * ra) < 1.2 && ir < 0.42 ? pupil : ir < 0.62 ? iris : sclera);
            } else buf.mark(x, y, z, Sculpt.UNSET);
        });
        int nx = (int) cx, nz = (int) cz;
        nook("the_eye_blinks", nx - 1, 0, nz - 1, nx + 1, 2, nz + 1);
        light(cx, -3, cz, 0xffc060, 14f, 0.7f);
    }
}
