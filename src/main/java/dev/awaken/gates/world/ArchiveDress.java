package dev.awaken.gates.world;

import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The Drowned Archive (SPEC §38.11): a library the sea came into. Walls of swollen shelves three storeys high with bands of
 * written stone between, tiled and planked floors under drifts of loose pages, kelp hanging, reading lamps and lecterns.
 * The Stacks are square halls with rows of freestanding shelves and galleries round their walls, the Atrium a lake under
 * a dome of sea glass, the Sunken Shelves a sea with shelves standing in it and lamps on its bed, and the Inkwell a sea of
 * ink crossed on paper bridges that unfold under your feet.
 */
final class ArchiveDress extends Dress {
    final BlockState stone, carved, shelf, shelfLit, tiles, planks, lamp, paper, kelp, glass, ink, lectern, bridge, water;
    /** Shaped ground (SPEC §38.14): tile slabs and stairs on the floor, the archive's stone at the walls, paper drifted, scraps */
    final BlockState gFloorSlab, gFloorStairs, gWallSlab, gWallStairs, gDrift, gBits;
    final boolean stacks, atrium, sunken, inkwell, sea;

    ArchiveDress(CaveEngine.Cave c) {
        super(c);
        stone = mat("wall", "awaken:archive_stone");
        carved = mat("wall_glow", "awaken:archive_stone_carved");
        shelf = mat("shelf", "awaken:archive_shelf");
        shelfLit = mat("shelf_lit", "awaken:archive_shelf_lit");
        tiles = mat("floor", "awaken:archive_tiles");
        planks = mat("planks", "awaken:archive_planks");
        lamp = mat("lamp", "awaken:archive_lamp");
        paper = mat("paper", "awaken:archive_paper");
        kelp = mat("kelp", "awaken:archive_kelp");
        glass = mat("glass", "awaken:archive_sea_glass");
        ink = mat("fall", "awaken:archive_inkfall");
        lectern = mat("touch", "awaken:archive_lectern");
        bridge = mat("bridge", "awaken:archive_paper_bridge");
        gFloorSlab = mat("ground_slab", "awaken:archive_tiles_slab");
        gFloorStairs = mat("ground_stairs", "awaken:archive_tiles_stairs");
        gWallSlab = mat("wall_slab", "awaken:archive_stone_slab");
        gWallStairs = mat("wall_stairs", "awaken:archive_stone_stairs");
        gDrift = mat("ground_drift", "awaken:archive_paper_drift");
        gBits = mat("ground_bits", "awaken:archive_scraps");
        water = c.fluid;
        String n = name();
        stacks = n.equals("stacks");
        atrium = n.equals("atrium");
        sunken = n.equals("sunken_shelves");
        inkwell = n.equals("inkwell");
        sea = c.sea;
    }

    @Override
    int glow() {
        return 0x7fe0d0;
    }

    @Override
    BlockState skin(int x, int y, int z, boolean fl, boolean ceiling) {
        if (y < W - 1) return Hash.noise3(seed + 9, x, y, z, 4) > 0.62 ? carved : stone;
        if (fl) return Hash.noise(seed + 3, x, z, 5) > (stacks ? 0.45 : 0.62) ? planks : tiles;
        if (ceiling) return atrium ? glass : Hash.noise(seed + 7, x, z, 5) > 0.7 ? carved : stone;
        // shelves in storeys of five with a band of written stone between, a book lit from inside here and there
        int rise = y - W;
        if (rise >= 0 && Math.floorMod(rise, 6) == 5) return Math.floorMod(x + z, 4) == 0 ? carved : stone;
        if (rise < 0 || (!stacks && rise > 10)) return stone;
        return Hash.unit(seed + 11, x * 31 + y, z) < 0.1 ? shelfLit : shelf;
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
        // loose pages lie thick: the archive grows little else
        return 0.5;
    }

    @Override
    BlockState creep(int x, int y, int z) {
        return null;
    }

    @Override
    BlockState formation(int part, int x, int y, int z) {
        // shelves standing floor to roof banded with written stone, stone crusted with writing, a toppled bookcase, book piles
        return pieces(part, x, y, z, shelf, carved, stone, carved, shelf, shelf);
    }

    @Override
    BlockState screen(int lat, int rise, int side) {
        if (rise % 6 == 5) return lat % 4 == 0 ? carved : stone;
        return lat % 5 == 2 ? shelfLit : shelf;
    }

    @Override
    List<FloorAir.Mist> mists() {
        return sea ? mist(0x1e5a5a, 0.5f, 0x143a40, 0.3f) : mist(0x2a4a50, 0.4f, 0x1a3036, 0.22f);
    }

    @Override
    void world() {
        if (sea && hero("standing_shelves")) standing();
        if (inkwell && hero("inkfalls")) inkfalls();
    }

    @Override
    void cavern(Room r, CaveEngine.Kind k) {
        int id = r.id;
        PassageEngine.Shape sh = c.shapes.get(id);
        grow(r, sea ? 0.03 : 0.06, 13, (x, y, z, len, i) -> paper);
        if (atrium || sunken || inkwell) hang(r, 0.03, 4, 4, 11, (x, y, z, len, i) -> kelp);
        if (stacks && k != CaveEngine.Kind.ARRIVAL && hero("shelf_rows")) rows(r);
        if (stacks && k == CaveEngine.Kind.GREAT && hero("galleries")) galleries(r);
        // reading lamps on the floor by the walls
        if (k != CaveEngine.Kind.ARRIVAL) for (int i = 0; i < (r.gw * r.gh >= 4 ? 3 : 1); i++) {
            double[] at = spot(r, 40 + i * 3, 0.45, 0.8, 1.5);
            if (at == null) continue;
            c.s.putOpen((int) Math.floor(at[0]), W, (int) Math.floor(at[1]), lamp);
            light(at[0], 1, at[1], 0x9ff0d8, 7f, 0.6f);
            block(at[0], at[1], 0.8);
        }
        if (k != CaveEngine.Kind.ARRIVAL) touchstones(r, lectern, k == CaveEngine.Kind.GREAT ? 2 : 1, "the_open_book", 60);
        if (k == CaveEngine.Kind.GREAT && sh != null) {
            if (stacks) wallNook(r, "the_overdue_shelf", 70);
            if (atrium && c.inLakeAny(id)) nook("the_pool_coin", (int) sh.cx() - 1, 0, (int) sh.cz() - 1, (int) sh.cx() + 1, 2, (int) sh.cz() + 1);
            if (sunken) nook("the_lit_shelf", (int) sh.cx() - 1, 0, (int) sh.cz() - 1, (int) sh.cx() + 1, 2, (int) sh.cz() + 1);
            if (inkwell) nook("the_ink_well", (int) sh.cx() - 1, 0, (int) sh.cz() - 1, (int) sh.cx() + 1, 2, (int) sh.cz() + 1);
            if (atrium && hero("sea_glass_dome"))
                c.shafts.add(new FloorAir.Shaft((int) sh.cx(), c.roofs.getOrDefault(id, c.roof), (int) sh.cz(), (int) sh.cx(), 0, (int) sh.cz(), 6f, 0x9ff0e0, 0.6f));
        }
        if (sh != null) light(sh.cx(), 3, sh.cz(), sea ? 0x5fd0c0 : 0x8fe0c8, 10f + r.gw * 3, 0.55f);
    }

    /** The Stacks' freestanding shelves: rows across a hall with an aisle down the middle and one across it. */
    void rows(Room r) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        int id = r.id;
        boolean alongX = sh.a() >= sh.b();
        double len = (alongX ? sh.a() : sh.b()) * 0.62, wide = (alongX ? sh.b() : sh.a()) * 0.62;
        for (int k = -3; k <= 3; k++) {
            if (k == 0) continue;
            double off = k * 4.5;
            if (Math.abs(off) > wide) continue;
            for (double t = -len; t <= len; t += 1) {
                if (Math.abs(t) < 2.5) continue;
                double x = alongX ? sh.cx() + t : sh.cx() + off, z = alongX ? sh.cz() + off : sh.cz() + t;
                if (c.d(id, x, z) > -3 || nearDoor(r, x, z, 3.5) || c.inLake(id, x, z) || p.nearAnchor(x, z, 3)) continue;
                int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
                int h = Math.min(5, top(id, bx, bz) - W - 1);
                if (h < 3) continue;
                c.s.op(bx, W, bz, bx, W + h - 1, bz, (b, xx, y, zz) -> {
                    if (b.open(xx, y, zz)) b.set(xx, y, zz, Hash.unit(seed + 23, xx * 7 + y, zz) < 0.12 ? shelfLit : shelf);
                });
                block(x, z, 0.8);
            }
        }
    }

    /** Galleries round the great hall's walls every six blocks up, as deep as a step is wide. */
    void galleries(Room r) {
        int[] b = box(r);
        int id = r.id;
        int roof = c.roofs.getOrDefault(id, c.roof);
        c.s.op(b[0], W + 6, b[1], b[2], W + roof, b[3], (buf, x, y, z) -> {
            if ((y - W) % 6 != 5 || !buf.open(x, y, z)) return;
            double d = c.d(id, x + 0.5, z + 0.5);
            if (d > -2.5 && d < 0) buf.set(x, y, z, planks);
        });
        PassageEngine.Shape sh = c.shapes.get(id);
        if (sh != null) for (int i = 1; i <= roof / 6; i++) light(sh.cx() - sh.a() * 0.8, i * 6, sh.cz(), 0x9ff0d8, 10f, 0.45f);
    }

    /** Shelves standing up out of the sea, three storeys of them, with a lamp lit on the bed at the foot of each. */
    void standing() {
        int n = 0;
        for (int i = 0; i < 60 && n < 26; i++) {
            // each inside one chunk, so the check for sea all round sees every block it asks about
            int x = chunkSpot(900 + i, 1, 7), z = chunkSpot(900 + i, 2, 7);
            if (p.nearAnchor(x + 0.5, z + 0.5, 6)) continue;
            n++;
            int h = 4 + (int) (Hash.unit(seed, 900 + i, 3) * 8);
            boolean alongX = Hash.unit(seed, 900 + i, 4) < 0.5;
            int lx = alongX ? x + 3 : x, lz = alongX ? z : z + 3;
            c.s.op(x - 1, W - CaveEngine.DEEP + 1, z - 1, lx + 1, W + h, lz + 1, (b, xx, y, zz) -> {
                // only where the sea is all round: never on an island or a causeway
                if (!b.water(x, W - 1, z) || !b.water(lx, W - 1, lz) || !b.water(x - 1, W - 1, z - 1)) return;
                boolean row = alongX ? zz == z && xx >= x && xx <= lx : xx == x && zz >= z && zz <= lz;
                if (row && y <= W + h - 1) b.set(xx, y, zz, Hash.unit(seed + 23, xx * 7 + y, zz) < 0.15 ? shelfLit : shelf);
                else if (row && y == W + h) b.set(xx, y, zz, carved);
                else if (!row && y == W - CaveEngine.DEEP + 1 && xx == x - 1 && zz == z - 1) b.set(xx, y, zz, lamp);
            });
            light(x, -CaveEngine.DEEP + 1, z, 0x5fd0c0, 8f, 0.5f);
        }
    }

    /** A coordinate across the sea whose span of {@code size} beyond it (and one before) stays in its chunk. */
    int chunkSpot(int i, int salt, int size) {
        int chunk = 1 + (int) (Hash.unit(seed, i, salt) * 14);
        return chunk * 16 + 1 + (int) (Hash.unit(seed, i, salt + 10) * (14 - size));
    }

    /** The Inkwell's falls: ink pouring from the dark into the sea in four places. */
    void inkfalls() {
        for (int i = 0; i < 4; i++) {
            int x = chunkSpot(950 + i, 1, 2), z = chunkSpot(950 + i, 2, 2);
            c.s.op(x - 1, W - 1, z - 1, x + 1, W + c.sky + 2, z + 1, (b, xx, y, zz) -> {
                if (Math.abs(xx - x) + Math.abs(zz - z) > 1 || !b.water(x, W - 1, z)) return;
                if (y >= W && b.open(xx, y, zz)) b.set(xx, y, zz, ink);
            });
            c.shafts.add(new FloorAir.Shaft(x, c.sky, z, x, 0, z, 1.5f, 0x2a3a52, 0.5f));
        }
    }

    @Override
    void tunnel(Room r, ChainLayout.Link l) {
        int[] b = box(r);
        if (inkwell) {
            // the causeway is paper: a folded sheet over the ink the whole way, nothing under it
            c.s.op(b[0], W - CaveEngine.DEEP + 1, b[1], b[2], W - 1, b[3], (buf, x, y, z) -> {
                if (buf.get(x, y, z) != Sculpt.SOLID) return;
                buf.set(x, y, z, y == W - 1 ? bridge : water);
            });
            return;
        }
        if (sea) return;
        grow(r, 0.05, 17, (x, y, z, len, i) -> paper);
        c.s.op(b[0], W + 2, b[1], b[2], W + 7, b[3], (buf, x, y, z) -> {
            if (!buf.open(x, y, z) || !solid(buf, x, y + 1, z) || Hash.unit(seed + 19, x, z) >= 0.03) return;
            buf.set(x, y, z, kelp);
        });
    }

    @Override
    void secret(Room r) {
        // the Last Reader's Room: a dry reading room under a skylight of sea glass, shelves all round, a desk and its lamp
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        int id = r.id;
        int[] b = box(r);
        c.s.op(b[0], W + 3, b[1], b[2], W + c.roofs.getOrDefault(id, c.roof) + 3, b[3], (buf, x, y, z) -> {
            if (!buf.open(x, y, z) || !solid(buf, x, y + 1, z) || c.q(id, x + 0.5, z + 0.5) > 0.45) return;
            buf.set(x, y + 1, z, glass);
        });
        grow(r, 0.4, 33, (x, y, z, len, i) -> paper);
        int cx = (int) sh.cx(), cz = (int) sh.cz();
        c.s.op(cx - 3, W, cz + 2, cx - 1, W, cz + 2, (buf, x, y, z) -> {
            if (buf.open(x, y, z)) buf.set(x, y, z, planks);
        });
        c.s.putOpen(cx - 2, W + 1, cz + 2, lamp);
        nook("the_last_page", cx - 1, 0, cz - 1, cx + 1, 2, cz + 1);
        c.shafts.add(new FloorAir.Shaft(cx, c.roofs.getOrDefault(id, c.roof), cz, cx, 0, cz, 4f, 0x9ff0e0, 0.7f));
        light(sh.cx(), 4, sh.cz(), 0xbff8e8, 16f, 0.9f);
    }
}
