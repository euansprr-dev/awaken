package dev.awaken.gates.world;

import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The Nightmare (SPEC §38.12): a house you nearly know, its wallpaper peeling, its windows lit by a moon that isn't
 * there. The House has furniture far too big for you; the Endless Hall is one long hallway lined with doors whose middle
 * folds back on itself (its slots are the same turned about their middle, so the live layer can turn you round in it);
 * the Upside-Down House is the same rooms with the furniture up on the ceiling; the Sleeper's Dream is bits of a dream
 * floating in the dark (a bedroom, a garden, a cushion with a staircase going nowhere) joined by narrow bridges; the
 * Nursery has a cot, a mobile and the night light still on.
 */
final class DreamDress extends Dress {
    final BlockState wallpaper, torn, boards, wood, plush, sheet, dark, lamp, candle, motes, toy, window, musicBox, fence,
            grass, poppy, stairs;
    /** Shaped ground (SPEC §38.14): board slabs and stairs on the floor, wood at the walls, plush drifted, feathers */
    final BlockState gFloorSlab, gFloorStairs, gWallSlab, gWallStairs, gDrift, gBits;
    final boolean house, hall, upside, dream;
    private int[] secretBox;

    DreamDress(CaveEngine.Cave c) {
        super(c);
        wallpaper = mat("wall", "awaken:dream_wallpaper");
        torn = mat("torn", "awaken:dream_wallpaper_torn");
        boards = mat("floor", "awaken:dream_boards");
        wood = mat("wood", "awaken:dream_wood");
        plush = mat("plush", "awaken:dream_plush");
        sheet = mat("sheet", "awaken:dream_sheet");
        dark = mat("dark", "awaken:dream_dark");
        lamp = mat("lamp", "awaken:dream_lamp");
        candle = mat("candle", "awaken:dream_candle");
        motes = mat("motes", "awaken:dream_motes");
        toy = mat("toy", "awaken:dream_toy");
        window = mat("wall_glow", "awaken:dream_window");
        musicBox = mat("touch", "awaken:dream_music_box");
        gFloorSlab = mat("ground_slab", "awaken:dream_boards_slab");
        gFloorStairs = mat("ground_stairs", "awaken:dream_boards_stairs");
        gWallSlab = mat("wall_slab", "awaken:dream_wood_slab");
        gWallStairs = mat("wall_stairs", "awaken:dream_wood_stairs");
        gDrift = mat("ground_drift", "awaken:dream_plush_drift");
        gBits = mat("ground_bits", "awaken:dream_feathers");
        fence = GatePainter.state("minecraft:dark_oak_fence");
        grass = GatePainter.state("minecraft:grass_block");
        poppy = GatePainter.state("minecraft:poppy");
        stairs = GatePainter.state("minecraft:dark_oak_stairs");
        String n = name();
        house = n.equals("the_house");
        hall = n.equals("endless_hall");
        upside = n.equals("upside_down");
        dream = n.equals("sleepers_dream");
    }

    @Override
    int glow() {
        return 0xc8a0ff;
    }

    @Override
    BlockState rail() {
        return fence;
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

    /** A dream island's kind by its place: 0 a bedroom, 1 a garden, 2 a cushion with toys and a staircase going nowhere. */
    int island(int id) {
        return id < 0 ? 0 : (int) (Hash.unit(seed, id, 211) * 3);
    }

    // ---- the skin -----------------------------------------------------------------------------------------------------

    @Override
    BlockState skin(int x, int y, int z, boolean fl, boolean ceiling) {
        if (dream && !inSecret(x, z)) {
            if (!fl) return dark;
            int id = p.b.at(x >> 4, z >> 4);
            CaveEngine.Kind k = id < 0 ? null : c.kinds.get(id);
            if (k == null || k == CaveEngine.Kind.LINK) return boards;
            if (c.d(id, x + 0.5, z + 0.5) > -1.2) return wood;
            return switch (island(id)) {
                case 1 -> grass;
                case 2 -> plush;
                default -> boards;
            };
        }
        if (upside) {
            boolean t = fl;
            fl = ceiling;
            ceiling = t;
        }
        int hx = x, hz = z;
        if (hall) {
            // a hall's look is its slot's, the same turned about the slot's middle: the fold reads the same both ways
            int lx = x & 15, lz = z & 15;
            if (lx * 16 + lz > (15 - lx) * 16 + (15 - lz)) {
                lx = 15 - lx;
                lz = 15 - lz;
            }
            hx = lx;
            hz = lz;
        }
        int rise = y - W, lat = hx + hz;
        if (fl) return boards;
        if (ceiling) return Math.floorMod(lat, 4) == 0 ? wood : sheet;
        if (rise == 0 || rise == 6) return wood;
        if (rise >= 2 && rise <= 4 && Math.floorMod(lat, 10) == 5) return window;
        return Hash.noise3(seed + 5, hx, y, hz, 4) > 0.62 ? torn : wallpaper;
    }

    @Override
    boolean depth() {
        return !dream && !hall;
    }

    @Override
    BlockState ground(int part, int x, int y, int z) {
        return kit(part, false, gFloorSlab, gFloorStairs, gWallSlab, gWallStairs, gDrift, gBits);
    }

    @Override
    double bits() {
        // small lost things lie thick
        return 0.42;
    }

    @Override
    BlockState creep(int x, int y, int z) {
        return plush;
    }

    @Override
    BlockState formation(int part, int x, int y, int z) {
        // bedposts floor to roof, heaps of sheets crusted with plush, a fallen post, a heap of plush
        return pieces(part, x, y, z, wood, null, sheet, plush, wood, plush);
    }

    @Override
    BlockState screen(int lat, int rise, int side) {
        if (dream) return dark;
        return rise == 0 || rise == 6 || lat % 5 == 0 ? wood : wallpaper;
    }

    @Override
    List<FloorAir.Mist> mists() {
        if (!dream) return mist(0x3a2a4a, 0.3f, 0x241a30, 0.15f);
        List<FloorAir.Mist> l = mist(0x2a1450, 0.3f, 0x140a28, 0.2f);
        // the dark the dream floats in, thick below
        l.add(new FloorAir.Mist(-c.under - 6, 8, 0x3a1a6a, 0.45f));
        return l;
    }

    // ---- furniture ----------------------------------------------------------------------------------------------------

    /** Where a piece's block at {@code rise} over the floor goes: on the floor, or (upside down) hanging from the roof. */
    int at(int rise, int top) {
        return upside ? top - rise : W + rise;
    }

    /** A post of wood from a rise to the floor (or, upside down, up to the roof) wherever it's open. */
    void post(int x, int z, int rise, int top) {
        int a = at(rise, top);
        int y0 = upside ? a : W, y1 = upside ? top : a;
        c.s.op(x, y0, z, x, y1, z, (b, xx, y, zz) -> {
            if (b.open(xx, y, zz)) b.set(xx, y, zz, wood);
        });
    }

    /** A flat slab of {@code st} at a rise, over a box. */
    void slab(int x0, int z0, int x1, int z1, int rise, int top, BlockState st) {
        int y = at(rise, top);
        c.s.op(x0, y, z0, x1, y, z1, (b, xx, yy, zz) -> {
            if (b.open(xx, yy, zz)) b.set(xx, yy, zz, st);
        });
    }

    /** A table five by four, its top seven over the floor, a lamp on it: you walk under it. */
    void table(int x, int z, int top) {
        for (int[] l : new int[][]{{0, 0}, {4, 0}, {0, 3}, {4, 3}}) post(x + l[0], z + l[1], 6, top);
        slab(x, z, x + 4, z + 3, 7, top, wood);
        c.s.putOpen(x + 3, at(8, top), z + 1, upside ? candle : lamp);
    }

    /** A chair: a seat three across five up on four legs, its back up to ten on one side. */
    void chair(int x, int z, int side, int top) {
        for (int[] l : new int[][]{{0, 0}, {2, 0}, {0, 2}, {2, 2}}) post(x + l[0], z + l[1], 4, top);
        slab(x, z, x + 2, z + 2, 5, top, plush);
        int bx = side == 1 ? x + 2 : x, bz = side == 2 ? z + 2 : z;
        for (int t = 0; t < 3; t++) {
            int px = side % 2 == 0 ? x + t : bx, pz = side % 2 == 0 ? bz : z + t;
            for (int rise = 6; rise <= 10; rise++) c.s.putOpen(px, at(rise, top), pz, wood);
        }
    }

    /** A bed: a mattress four by six, three deep, a sheet over it and a headboard. */
    void bed(int x, int z, boolean alongX, int top) {
        int w = alongX ? 6 : 4, d = alongX ? 4 : 6;
        for (int rise = 0; rise <= 3; rise++) slab(x, z, x + w - 1, z + d - 1, rise, top, rise == 3 ? sheet : plush);
        for (int t = 0; t < (alongX ? d : w); t++) {
            int hx = alongX ? x - 1 : x + t, hz = alongX ? z + t : z - 1;
            for (int rise = 0; rise <= 6; rise++) c.s.putOpen(hx, at(rise, top), hz, wood);
        }
    }

    /** Toy blocks two across, stacked. */
    void toys(int x, int z, int n, int top) {
        for (int i = 0; i < n; i++) {
            int ox = x + (i % 2) * 3, oz = z + (i / 2) * 3;
            int h = 1 + (int) (Hash.unit(seed + 61, ox, oz) * 2);
            for (int rise = 0; rise < h * 2; rise++) slab(ox, oz, ox + 1, oz + 1, rise, top, toy);
        }
    }

    /** One of the house's big pieces at a spot: what by the salt. */
    void piece(Room r, int salt) {
        double[] at = spot(r, salt, 0.15, 0.6, 5);
        if (at == null) return;
        int x = (int) Math.floor(at[0]), z = (int) Math.floor(at[1]);
        int top = top(r.id, x, z);
        for (int dx = -3; dx <= 3; dx += 3) for (int dz = -3; dz <= 3; dz += 3) top = Math.min(top, top(r.id, x + dx, z + dz));
        int which = (int) (unit(r.id, salt + 1) * 4);
        switch (which) {
            case 0 -> table(x - 2, z - 2, top);
            case 1 -> chair(x - 1, z - 1, (int) (unit(r.id, salt + 2) * 4), top);
            case 2 -> bed(x - 2, z - 3, unit(r.id, salt + 3) < 0.5, top);
            default -> toys(x - 2, z - 2, 3, top);
        }
        // a bed and toys stand on the floor (block it); a table and a chair stand on legs you walk between
        block(at[0], at[1], which == 2 ? 4.5 : which == 3 ? 3 : 1.2);
    }

    // ---- places -------------------------------------------------------------------------------------------------------

    @Override
    void cavern(Room r, CaveEngine.Kind k) {
        int id = r.id;
        PassageEngine.Shape sh = c.shapes.get(id);
        if (dream) dreamIsle(r, k);
        else {
            boolean big = r.gw * r.gh >= 2;
            if ((house && hero("giant_furniture")) || (upside && hero("ceiling_furniture"))) {
                if (k != CaveEngine.Kind.ARRIVAL) piece(r, 70);
                if (big && k != CaveEngine.Kind.ARRIVAL) piece(r, 75);
            }
            if (hall) {
                // the hall's rooms: toys on the floor and candles
                if (k != CaveEngine.Kind.ARRIVAL && unit(id, 80) < 0.5) {
                    double[] at = spot(r, 81, 0.2, 0.6, 3);
                    if (at != null) {
                        toys((int) Math.floor(at[0]) - 1, (int) Math.floor(at[1]) - 1, 1, top(id, (int) at[0], (int) at[1]));
                        block(at[0], at[1], 2.5);
                    }
                }
                grow(r, 0.01, 23, (x, y, z, len, i) -> candle);
            }
            if (upside) grow(r, 0.008, 23, (x, y, z, len, i) -> candle);
            else hang(r, 0.012, 3, 5, 11, (x, y, z, len, i) -> motes);
        }
        if (k != CaveEngine.Kind.ARRIVAL) touchstones(r, musicBox, k == CaveEngine.Kind.GREAT ? 2 : 1, "the_first_tune", 60);
        boolean nookHere = k == CaveEngine.Kind.GREAT || (hall && k == CaveEngine.Kind.CAVERN && !hallNook && !p.sealable(r));
        if (nookHere && sh != null) {
            if (house) {
                int[] at = wallNook(r, "the_toy_box", 70);
                if (at != null) c.s.putOpen(at[0], W, at[1], toy);
            }
            if (hall) {
                int[] at = wallNook(r, "the_door_left_open", 70);
                hallNook |= at != null;
                if (at != null) c.s.putOpen(at[0], W, at[1], candle);
            }
            if (upside) {
                int[] at = wallNook(r, "the_fallen_bed", 70);
                if (at != null) c.s.putOpen(at[0], W, at[1], plush);
            }
        }
        if (sh != null) light(sh.cx(), 3, sh.cz(), dream ? 0xb890ff : 0xffd8a0, 10f + r.gw * 3, 0.5f);
    }

    private boolean hallNook;

    /** A bit of the dream: a bedroom's bed and lamp, a garden's flowers and fence, a cushion's toys and its stair. */
    void dreamIsle(Room r, CaveEngine.Kind k) {
        int id = r.id;
        PassageEngine.Shape sh = c.shapes.get(id);
        if (sh == null) return;
        int kind = island(id);
        if (k == CaveEngine.Kind.GREAT && hero("dream_isles")) nightWindow(r);
        if (k == CaveEngine.Kind.ARRIVAL) return;
        double[] at = spot(r, 90, 0.15, 0.5, 4);
        if (at == null) return;
        int x = (int) Math.floor(at[0]), z = (int) Math.floor(at[1]);
        switch (kind) {
            case 1 -> {
                grow(r, 0.2, 29, (xx, y, zz, len, i) -> i < 3 ? poppy : null);
                for (int t = -3; t <= 3; t++) c.s.putOpen(x + t, W, z - 3, fence);
            }
            case 2 -> {
                toys(x - 2, z - 2, 2, W);
                stair(r);
            }
            default -> {
                bed(x - 2, z - 3, unit(id, 93) < 0.5, W);
                c.s.putOpen(x + 3, W, z, lamp);
            }
        }
        block(at[0], at[1], 4.5);
    }

    /** A staircase going nowhere: dark oak steps climbing off the cushion's edge into the dark, eight up. */
    void stair(Room r) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        int side = (int) (unit(r.id, 97) * 4);
        double reach = (side % 2 == 1 ? sh.a() : sh.b()) - 3;
        int x0 = (int) Math.floor(sh.cx() + GatePainter.DX[side] * reach), z0 = (int) Math.floor(sh.cz() + GatePainter.DZ[side] * reach);
        String face = switch (side) {
            case 0 -> "north";
            case 1 -> "east";
            case 2 -> "south";
            default -> "west";
        };
        BlockState step = WorldMats.with(stairs, "facing", face);
        for (int i = 0; i < 8; i++) {
            int x = x0 + GatePainter.DX[side] * i, z = z0 + GatePainter.DZ[side] * i;
            for (int w = -1; w <= 1; w++) {
                int sx = x + (side % 2 == 0 ? w : 0), sz = z + (side % 2 == 0 ? 0 : w);
                c.s.put(sx, W + i, sz, step);
            }
        }
        block(x0 + 0.5, z0 + 0.5, 2);
    }

    /** The night window: a window standing on its own on the great island, lit from a night that isn't there. */
    void nightWindow(Room r) {
        double[] at = spot(r, 99, 0.3, 0.6, 3);
        if (at == null) return;
        int x = (int) Math.floor(at[0]), z = (int) Math.floor(at[1]);
        for (int t = -1; t <= 2; t++) {
            for (int rise = 1; rise <= 4; rise++) {
                boolean edge = t == -1 || t == 2 || rise == 1 || rise == 4;
                c.s.putOpen(x + t, W + rise, z, edge ? wood : window);
            }
            c.s.putOpen(x + t, W, z, wood);
        }
        nook("the_night_window", x, 0, z + 1, x + 1, 2, z + 2);
        block(at[0] + 0.5, at[1], 2.5);
        light(at[0], 3, at[1] + 1, 0xa0b8ff, 8f, 0.7f);
    }

    @Override
    void tunnel(Room r, ChainLayout.Link l) {
        int ox = r.gx * 16, oz = r.gz * 16;
        if (dream) {
            // candles on the bridge's rail posts every five blocks
            ChainLayout.Way way = ChainLayout.Way.of(r, l, seed);
            c.s.op(ox, W + 1, oz, ox + 15, W + 1, oz + 15, (b, x, y, z) -> {
                if (b.get(x, y - 1, z) == Sculpt.UNSET || b.state(x, y - 1, z) == null) return;
                double[] w = way.at(x + 0.5, z + 0.5);
                if (w != null && Math.floorMod((int) Math.round(w[1]), 5) == 2) b.set(x, y, z, candle);
            });
            return;
        }
        if (!c.line) return;
        // the hall's doors, each against its wall, and a candle each side: the same turned about the slot's middle
        BlockState lower = WorldMats.with(GatePainter.state("minecraft:dark_oak_door"), "half", "lower");
        BlockState upper = WorldMats.with(GatePainter.state("minecraft:dark_oak_door"), "half", "upper");
        // read from the hall's walls alone (a door just set is solid too, and would set the next one off down the row)
        c.s.op(ox + 1, W, oz + 1, ox + 14, W, oz + 14, (b, x, y, z) -> {
            if (!b.open(x, W, z) || !b.open(x, W + 1, z)) return;
            int lx = x - ox, lz = z - oz;
            String face = null;
            if (lx == 3 && wall(b, x, z - 1)) face = "south";
            else if (lx == 12 && wall(b, x, z + 1)) face = "north";
            else if (lz == 12 && wall(b, x - 1, z)) face = "east";
            else if (lz == 3 && wall(b, x + 1, z)) face = "west";
            if (face != null) {
                b.set(x, W, z, WorldMats.with(lower, "facing", face));
                b.set(x, W + 1, z, WorldMats.with(upper, "facing", face));
                return;
            }
            if ((lx == 8 && lz == 5 && wall(b, x, z - 1)) || (lx == 7 && lz == 10 && wall(b, x, z + 1))
                    || (lz == 7 && lx == 5 && wall(b, x - 1, z)) || (lz == 8 && lx == 10 && wall(b, x + 1, z)))
                b.set(x, W, z, candle);
        });
    }

    private boolean wall(Sculpt.Buf b, int x, int z) {
        return b.get(x, W, z) == Sculpt.SOLID;
    }

    @Override
    void secret(Room r) {
        // the Nursery: a cot of dark bars round a mattress, a mobile turning over it, toys, the night light on its stand
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        int id = r.id;
        double cx = sh.cx(), cz = sh.cz();
        int x = (int) Math.floor(cx) + 3, z = (int) Math.floor(cz) - 2;
        for (int dx = -1; dx <= 3; dx++) {
            for (int dz = -1; dz <= 5; dz++) {
                boolean edge = dx == -1 || dx == 3 || dz == -1 || dz == 5;
                if (edge) {
                    c.s.putOpen(x + dx, W, z + dz, fence);
                    c.s.putOpen(x + dx, W + 1, z + dz, fence);
                } else c.s.putOpen(x + dx, W, z + dz, dz == 0 ? plush : sheet);
            }
        }
        int top = top(id, x + 1, z + 2);
        c.s.op(x + 1, W + 4, z + 2, x + 1, top, z + 2, (b, xx, y, zz) -> {
            if (b.open(xx, y, zz)) b.set(xx, y, zz, y == W + 4 ? toy : motes);
        });
        c.s.putOpen(x - 3, W, z, wood);
        c.s.putOpen(x - 3, W + 1, z, lamp);
        toys((int) Math.floor(cx) - 5, (int) Math.floor(cz) + 3, 2, W);
        block(x + 1, z + 2, 4);
        int nx = (int) cx - 1, nz = (int) cz;
        nook("the_lullaby", nx - 1, 0, nz - 1, nx + 1, 2, nz + 1);
        light(cx, 4, cz, 0xffd8a0, 14f, 0.8f);
    }
}
