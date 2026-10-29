package dev.awaken.gates.world;

import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The rare worlds with a look of their own (SPEC §38.13), open land under the sky each one. The Quiet is a meadow at night
 * with one old tree and moonflowers open in the grass; Upward Rain is wet green hills where the rain runs up out of the
 * puddles into the sky; the Aurora Steppe is dry grass and old snow under the aurora, standing stones all over it and a
 * circle of them on the widest ground; the Lantern Orchard is rows of dark trees with lit lanterns growing on them like
 * fruit; the Last Campfire is a clearing where pale figures sit round a fire with a seat left empty; the Cartographer's
 * Table is a map you walk on, its hills drawn in ink round you and made in paper out past the places, a compass rose at
 * its middle.
 */
final class RareDress extends Dress {
    private final String n;
    private final boolean quiet, rain, steppe, orchard, camp, map;
    private final BlockState grass, moss, mud, dirt, coarse, podzol, snow, stone, tuff, andesite, mossy,
            shortGrass, fern, flower, petals, dryGrass, tallDry, litter, bush, fireflyBush,
            oakLog, oakLeaves, darkLog, darkLeaves, fruit, unlit, menhir, rainUp, water, figure, campfire, seat, seatZ, lantern,
            paper, ink, plank, gold, quill, inkwell, inkTop, fence, slab;
    /** Shaped ground (SPEC §38.14): the Soft Land's slabs of each soil, stone at the hillsides, snow drifted, pebbles and twigs. */
    private final BlockState turfSlab, mudSlab, coarseSlab, podzolSlab, stoneSlab, stoneStairs, snowDrift, pebbles, twigs;
    /** The map's waves: whole turns across 32 blocks, so the endless table's band repeats (38.12) under its hills. */
    private static final int[][] WAVES = {{1, 0}, {0, 1}, {1, 1}, {1, -1}, {2, 1}, {-1, 2}, {3, -2}};
    private final double[] phase = new double[WAVES.length * 2];

    RareDress(CaveEngine.Cave c) {
        super(c);
        n = name();
        rain = n.equals("upward_rain");
        steppe = n.equals("the_aurora_steppe");
        orchard = n.equals("the_lantern_orchard");
        camp = n.equals("the_last_campfire");
        map = n.equals("the_cartographers_table");
        quiet = !rain && !steppe && !orchard && !camp && !map;
        for (int i = 0; i < phase.length; i++) phase[i] = Hash.unit(seed, 901, i) * Math.PI * 2;
        grass = mat("floor", "minecraft:grass_block");
        moss = GatePainter.state("minecraft:moss_block");
        mud = GatePainter.state("minecraft:mud");
        dirt = GatePainter.state("minecraft:dirt");
        coarse = GatePainter.state("minecraft:coarse_dirt");
        podzol = GatePainter.state("minecraft:podzol");
        snow = GatePainter.state("minecraft:snow_block");
        stone = mat("wall", "minecraft:stone");
        tuff = GatePainter.state("minecraft:tuff");
        andesite = GatePainter.state("minecraft:andesite");
        mossy = GatePainter.state("minecraft:mossy_cobblestone");
        shortGrass = GatePainter.state("minecraft:short_grass");
        fern = GatePainter.state("minecraft:fern");
        flower = GatePainter.state("awaken:rare_moonflower");
        petals = GatePainter.state("minecraft:wildflowers");
        dryGrass = GatePainter.state("minecraft:short_dry_grass");
        tallDry = GatePainter.state("minecraft:tall_dry_grass");
        litter = GatePainter.state("minecraft:leaf_litter");
        bush = GatePainter.state("minecraft:bush");
        fireflyBush = GatePainter.state("minecraft:firefly_bush");
        oakLog = GatePainter.state("minecraft:oak_log[axis=y]");
        oakLeaves = WorldMats.with(GatePainter.state("minecraft:oak_leaves"), "persistent", "true");
        darkLog = GatePainter.state("minecraft:dark_oak_log[axis=y]");
        darkLeaves = WorldMats.with(GatePainter.state("minecraft:dark_oak_leaves"), "persistent", "true");
        fruit = GatePainter.state("awaken:rare_lantern_fruit");
        unlit = GatePainter.state("awaken:rare_lantern_dark");
        menhir = GatePainter.state("awaken:rare_aurora_stone");
        rainUp = GatePainter.state("awaken:rare_rain_up");
        water = GatePainter.state("minecraft:water");
        figure = GatePainter.state("awaken:rare_figure");
        campfire = GatePainter.state("minecraft:campfire[lit=true]");
        seat = GatePainter.state("minecraft:stripped_spruce_log[axis=x]");
        seatZ = GatePainter.state("minecraft:stripped_spruce_log[axis=z]");
        lantern = GatePainter.state("minecraft:lantern");
        paper = mat("floor", "awaken:rare_map");
        ink = GatePainter.state("awaken:rare_map_ink");
        plank = GatePainter.state("minecraft:dark_oak_planks");
        gold = GatePainter.state("minecraft:gold_block");
        quill = GatePainter.state("minecraft:white_wool");
        inkwell = GatePainter.state("minecraft:black_concrete");
        inkTop = GatePainter.state("minecraft:blue_concrete");
        fence = GatePainter.state("minecraft:spruce_fence");
        slab = GatePainter.state("minecraft:spruce_slab[type=bottom]");
        turfSlab = GatePainter.state("awaken:turf_slab[type=bottom]");
        mudSlab = GatePainter.state("awaken:mud_slab[type=bottom]");
        coarseSlab = GatePainter.state("awaken:coarse_dirt_slab[type=bottom]");
        podzolSlab = GatePainter.state("awaken:podzol_slab[type=bottom]");
        stoneSlab = GatePainter.state("minecraft:cobblestone_slab[type=bottom]");
        stoneStairs = GatePainter.state("minecraft:mossy_cobblestone_stairs");
        snowDrift = GatePainter.state("minecraft:snow");
        pebbles = GatePainter.state("awaken:pebbles");
        twigs = GatePainter.state("awaken:twigs");
    }

    @Override
    boolean depth() {
        // the map is a plain (it keeps its line)
        return !map;
    }

    /** Whether shaped ground (38.14) steps up from (x, z) to a side. */
    private boolean stepUp(int x, int z) {
        if (c.relief == null) return false;
        int h = c.relief.half(x, z);
        return c.relief.half(x + 1, z) > h || c.relief.half(x - 1, z) > h || c.relief.half(x, z + 1) > h || c.relief.half(x, z - 1) > h;
    }

    @Override
    BlockState ground(int part, int x, int y, int z) {
        double nz = Hash.noise(seed + 3, x, z, 6);
        // the half step is a slab of the soil it stands in
        BlockState soil = rain ? (nz > 0.68 ? mudSlab : turfSlab) : steppe ? (nz > 0.56 ? coarseSlab : turfSlab)
                : camp && nz > 0.62 ? podzolSlab : turfSlab;
        return kit(part, steppe && nz > 0.7, soil, null, stoneSlab, stoneStairs, steppe ? snowDrift : null,
                orchard || camp || Hash.unit(seed + 17, x, z) < 0.4 ? twigs : pebbles);
    }

    @Override
    double bits() {
        return 0.15;
    }

    @Override
    BlockState creep(int x, int y, int z) {
        return rain || orchard ? moss : steppe ? snow : null;
    }

    @Override
    BlockState formation(int part, int x, int y, int z) {
        // mossy boulders, a fallen tree, a heap of earth
        return pieces(part, x, y, z, stone, null, stone, mossy, oakLog, coarse);
    }

    @Override
    int glow() {
        return quiet ? 0xd8e4ff : rain ? 0xa0c8e8 : steppe ? 0x70f0b0 : orchard ? 0xffb060 : camp ? 0xffa050 : 0x4a64a4;
    }

    @Override
    BlockState rail() {
        return map ? plank : fence;
    }

    // ---- the skin -----------------------------------------------------------------------------------------------------

    @Override
    BlockState skin(int x, int y, int z, boolean fl, boolean ceiling) {
        int rise = y - W;
        if (map) {
            // the map: paper on top, the hills' contour lines inked across it; the table's wood under
            if (!fl) return plank;
            double v = height(x, z);
            return v - Math.floor(v) < 0.09 ? ink : paper;
        }
        double nz = Hash.noise(seed + 3, x, z, 6);
        if (fl) {
            // mud stands 7/8 of a block: beside a half step up it'd be a jump (moss there instead)
            if (rain) return nz > 0.68 && !stepUp(x, z) ? mud : nz > 0.55 ? moss : grass;
            if (steppe) return nz > 0.7 ? snow : nz > 0.56 ? coarse : grass;
            if (camp) return nz > 0.62 ? podzol : grass;
            if (orchard) return nz > 0.66 ? moss : grass;
            return nz > 0.7 ? moss : grass;
        }
        if (ceiling) return stone;
        // the hillsides: earth near the top of the land, stone under, a little moss or tuff through it
        if (rise >= c.lid - 2) return steppe && Hash.noise(seed + 5, x, z, 4) > 0.6 ? snow : dirt;
        double s3 = Hash.noise3(seed + 7, x, y, z, 5);
        if (rain || orchard) return s3 > 0.64 ? mossy : s3 < 0.3 ? moss : stone;
        if (steppe) return s3 > 0.62 ? andesite : stone;
        return s3 > 0.66 ? tuff : stone;
    }

    @Override
    BlockState screen(int lat, int rise, int side) {
        return map ? plank : rise == 0 ? mossy : stone;
    }

    @Override
    List<FloorAir.Mist> mists() {
        if (map) return mist(0xe8dcbc, 0.06f, 0xd8ccac, 0.04f);
        if (rain) return mist(0x9ab0c0, 0.22f, 0x8aa0b4, 0.14f);
        if (steppe) return mist(0x203040, 0.12f, 0x182838, 0.08f);
        if (orchard) return mist(0x302418, 0.12f, 0x281c14, 0.08f);
        if (camp) return mist(0x1c2028, 0.14f, 0x14181e, 0.1f);
        return mist(0x1a2238, 0.12f, 0x141a2c, 0.08f);
    }

    // ---- round the places ---------------------------------------------------------------------------------------------

    @Override
    void world() {
        if (!map) return;
        // out past the places the map is made: paper hills rising where its contours are tightest, rivers cut in blue,
        // all of it the same every 32 blocks (the table is an endless plain: its band turns you round unseen)
        c.s.op(0, W, 0, Plots.SIDE - 1, W + 7, Plots.SIDE - 1, (b, x, y, z) -> {
            if (p.b.at(x >> 4, z >> 4) >= 0 || x < 6 || z < 6 || x > Plots.SIDE - 7 || z > Plots.SIDE - 7) return;
            double v = height(x, z);
            int h = (int) Math.max(0, (v - 4.2) * 1.4);
            if (y - W < h && b.get(x, y, z) != Sculpt.SOLID) b.set(x, y, z, v - Math.floor(v) < 0.09 ? ink : paper);
        });
        c.s.op(0, W - 1, 0, Plots.SIDE - 1, W - 1, Plots.SIDE - 1, (b, x, y, z) -> {
            if (p.b.at(x >> 4, z >> 4) >= 0 || x < 6 || z < 6 || x > Plots.SIDE - 7 || z > Plots.SIDE - 7) return;
            if (Math.abs(wave(x, z, WAVES.length)) < 0.035 && height(x, z) < 4.2 && b.get(x, y, z) == Sculpt.SOLID) b.set(x, y, z, water);
        });
        // lamplight over the whole table, hidden at head height on a staggered grid every four (so it repeats too)
        c.s.op(0, W, 0, Plots.SIDE - 1, W, Plots.SIDE - 1, (b, x, y, z) -> {
            boolean on = (Math.floorMod(x, 4) == 1 && Math.floorMod(z, 4) == 1) || (Math.floorMod(x, 4) == 3 && Math.floorMod(z, 4) == 3);
            if (!on) return;
            for (int yy = W; yy < W + 10; yy++) {
                if (!b.open(x, yy, z)) continue;
                if (b.open(x, yy + 1, z)) b.set(x, yy + 1, z, c.light);
                return;
            }
        });
        // the cartographer's things left on the table, giant beside you: an inkwell and a quill lying across the map (among
        // the places, never out in the band that repeats)
        int done = 0;
        for (int i = 0; i < 24 && done < 2; i++) {
            int sx = (int) (Hash.unit(seed, 711, i) * 8) + 4, sz = (int) (Hash.unit(seed, 712, i) * 8) + 4;
            if (p.b.at(sx, sz) >= 0) continue;
            int cx = sx * 16 + 8, cz = sz * 16 + 8;
            if (done == 0) {
                c.s.op(cx - 6, W, cz - 6, cx + 6, W + 9, cz + 6, (b, x, y, z) -> {
                    double d = Math.hypot(x + 0.5 - cx, z + 0.5 - cz);
                    int r = y - W;
                    if (d < 5.5 && r < 8 && !(d < 2 && r > 5)) b.set(x, y, z, r == 7 || (r == 6 && d >= 2) ? inkTop : inkwell);
                });
            } else {
                for (int t = -9; t <= 9; t++) {
                    int qx = cx + t, qz = cz + t / 3;
                    c.s.put(qx, W, qz, quill);
                    if (Math.abs(t) < 7) c.s.put(qx, W, qz + 1, quill);
                    if (t > 6) c.s.put(qx, W, qz, inkwell);
                }
            }
            done++;
        }
    }

    /** How high the map's land is drawn at a block (about 1 to 9): its waves, so it's the same every 32 blocks. */
    private double height(int x, int z) {
        return 5 + 7 * wave(x, z, 0);
    }

    /** A sum of the map's waves (from {@code from} in the phases), about -1 to 1, the same every 32 blocks both ways. */
    private double wave(int x, int z, int from) {
        double v = 0, all = 0;
        for (int i = 0; i < WAVES.length; i++) {
            double w = 1 / (1 + i * 0.35);
            v += Math.sin(Math.PI * 2 * (WAVES[i][0] * x + WAVES[i][1] * z) / 32.0 + phase[from + i]) * w;
            all += w;
        }
        return v / all;
    }

    // ---- places -------------------------------------------------------------------------------------------------------

    @Override
    void cavern(Room r, CaveEngine.Kind k) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        boolean keep = k == CaveEngine.Kind.GREAT || (c.great < 0 && k == CaveEngine.Kind.END);
        boolean arrival = k == CaveEngine.Kind.ARRIVAL;
        if (quiet) {
            grow(r, 0.16, 31, (x, y, z, len, i) -> i < 4 ? shortGrass : i == 4 ? petals : i == 5 ? flower : null);
            if (keep) loneTree(r);
            else if (!arrival) boulders(r, 2);
        }
        if (rain) {
            grow(r, 0.14, 41, (x, y, z, len, i) -> i < 4 ? shortGrass : i < 6 ? fern : null);
            if (!arrival) puddles(r, k == CaveEngine.Kind.GREAT ? 6 : 3);
            if (keep) shelter(r);
        }
        if (steppe) {
            grow(r, 0.12, 51, (x, y, z, len, i) -> i < 4 ? dryGrass : i == 4 ? tallDry : null);
            if (keep) circle(r);
            else if (!arrival) stones(r, 1 + (int) (unit(r.id, 52) * 3));
        }
        if (orchard) {
            grow(r, 0.08, 61, (x, y, z, len, i) -> i < 3 ? litter : i == 3 ? shortGrass : null);
            if (!arrival) orchard(r, keep);
        }
        if (camp) {
            grow(r, 0.1, 71, (x, y, z, len, i) -> i < 3 ? shortGrass : i == 3 ? bush : i == 4 ? fireflyBush : null);
            if (keep) fire(r);
            else if (!arrival) loners(r);
        }
        if (map && keep) compass(r);
        if (sh != null) light(sh.cx(), 4, sh.cz(), glow(), 10f + r.gw * 3, c.dark ? 0.45f : 0.25f);
    }

    /** The Quiet's lone tree: one old oak, broad and low, in the widest meadow; its roots keep the secret. */
    void loneTree(Room r) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        double cx = sh.cx(), cz = sh.cz();
        int x = (int) Math.floor(cx), z = (int) Math.floor(cz), h = 8;
        for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) for (int y = 0; y < h; y++) c.s.putOpen(x + dx, W + y, z + dz, oakLog);
        // three boughs out from the top of the trunk
        for (int i = 0; i < 3; i++) {
            double a = i * Math.PI * 2 / 3 + unit(r.id, 33);
            for (int t = 1; t < 4; t++) c.s.putOpen((int) Math.floor(cx + 0.5 + Math.cos(a) * t), W + h - 2 + t / 2, (int) Math.floor(cz + 0.5 + Math.sin(a) * t),
                    GatePainter.state("minecraft:oak_log[axis=" + (Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? "x" : "z") + "]"));
        }
        c.s.op(x - 7, W + h - 3, z - 7, x + 8, W + h + 4, z + 8, (b, xx, y, zz) -> {
            if (!b.open(xx, y, zz)) return;
            double d = Math.hypot(xx - cx - 0.5, zz - cz - 0.5) / 7.0, v = (y - (W + h)) / 3.5;
            if (d * d + v * v < 1 && Hash.unit(seed + 35, xx * 7 + y, zz) < 0.86) b.set(xx, y, zz, oakLeaves);
        });
        block(cx + 0.5, cz + 0.5, 2.5);
        nook("the_lone_tree", x - 2, 0, z + 2, x, 2, z + 4);
        light(cx, 2, cz, 0xd8e4ff, 9f, 0.4f);
    }

    /** A few boulders of mossy stone half sunk in the grass. */
    void boulders(Room r, int n) {
        for (int i = 0; i < n; i++) {
            double[] at = spot(r, 37 + i * 3, 0.3, 0.85, 2);
            if (at == null) continue;
            double rr = 1.2 + unit(r.id, 38 + i) * 1.0;
            int x = (int) Math.floor(at[0]), z = (int) Math.floor(at[1]);
            c.s.op(x - 2, W, z - 2, x + 2, W + 2, z + 2, (b, xx, y, zz) -> {
                if (b.open(xx, y, zz) && Math.hypot(xx + 0.5 - at[0], (y - W + 0.6) * 1.4) + Math.abs(zz + 0.5 - at[1]) * 0.7 < rr) b.set(xx, y, zz, mossy);
            });
            block(at[0], at[1], rr + 0.5);
        }
    }

    /** Puddles in the grass, the rain running up out of each in a thin bright column to the sky. */
    void puddles(Room r, int n) {
        for (int i = 0; i < n; i++) {
            double[] at = spot(r, 43 + i * 5, 0.2, 0.8, 2.5);
            if (at == null) continue;
            int x = (int) Math.floor(at[0]), z = (int) Math.floor(at[1]);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (dx * dz == 0 || unit(r.id, 44 + i) < 0.5) {
                int px = x + dx, pz = z + dz;
                c.s.op(px, W - 1, pz, px, W - 1, pz, (b, xx, y, zz) -> {
                    if (solid(b, xx, y - 1, zz) && solid(b, xx + 1, y, zz) && solid(b, xx - 1, y, zz) && solid(b, xx, y, zz + 1) && solid(b, xx, y, zz - 1))
                        b.set(xx, y, zz, water);
                });
            }
            for (int y = 0; y < c.lid - 1; y++) c.s.putOpen(x, W + y, z, rainUp);
            block(at[0], at[1], 2);
        }
    }

    /** Upward Rain's dry patch: a little roof of planks on four posts, the ground under it dry; standing there finds it. */
    void shelter(Room r) {
        double[] at = spot(r, 47, 0.3, 0.7, 3.5);
        if (at == null) return;
        int x = (int) Math.floor(at[0]), z = (int) Math.floor(at[1]);
        for (int dx = -2; dx <= 2; dx += 4) for (int dz = -2; dz <= 2; dz += 4) for (int y = 0; y < 3; y++) c.s.putOpen(x + dx, W + y, z + dz, fence);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            c.s.putOpen(x + dx, W + 3, z + dz, slab);
            c.s.op(x + dx, W - 1, z + dz, x + dx, W - 1, z + dz, (b, xx, y, zz) -> {
                if (b.get(xx, y, zz) == Sculpt.SOLID) b.set(xx, y, zz, coarse);
            });
        }
        block(at[0], at[1], 3);
        nook("the_dry_patch", x - 1, 0, z - 1, x + 1, 2, z + 1);
    }

    /** Standing stones, one here and there, lit along their cracks like the sky. */
    void stones(Room r, int n) {
        for (int i = 0; i < n; i++) {
            double[] at = spot(r, 53 + i * 3, 0.25, 0.8, 2);
            if (at == null) continue;
            menhir((int) Math.floor(at[0]), (int) Math.floor(at[1]), 3 + (int) (unit(r.id, 54 + i) * 3));
            block(at[0], at[1], 1.5);
        }
    }

    void menhir(int x, int z, int h) {
        for (int y = 0; y < h; y++) c.s.putOpen(x, W + y, z, menhir);
    }

    /** The stone circle on the widest ground: ten stones in a ring, a flat stone at its middle where the secret is. */
    void circle(Room r) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        double R = Math.min(7, Math.min(sh.a(), sh.b()) - 3);
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5;
            double x = sh.cx() + Math.cos(a) * R, z = sh.cz() + Math.sin(a) * R;
            if (nearDoor(r, x, z, 2.5)) continue;
            menhir((int) Math.floor(x), (int) Math.floor(z), i % 3 == 0 ? 3 : 4);
        }
        int x = (int) Math.floor(sh.cx()), z = (int) Math.floor(sh.cz());
        c.s.op(x - 1, W - 1, z - 1, x + 1, W - 1, z + 1, (b, xx, y, zz) -> {
            if (b.get(xx, y, zz) == Sculpt.SOLID) b.set(xx, y, zz, menhir);
        });
        block(sh.cx(), sh.cz(), 2);
        nook("the_stone_circle", x - 1, 0, z - 1, x + 1, 2, z + 1);
        light(sh.cx(), 2, sh.cz(), 0x70f0b0, 10f, 0.6f);
    }

    /**
     * The orchard: dark trees in rows across the place, lanterns growing under their crowns. In the great place one of
     * them holds a lantern that's gone out; standing under it finds it.
     */
    void orchard(Room r, boolean keep) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        int ox = (int) (unit(r.id, 62) * 6), oz = (int) (unit(r.id, 63) * 6);
        int[] b = box(r);
        boolean dark = false;
        for (int x = b[0] + ox + 3; x < b[2] - 2; x += 7) {
            for (int z = b[1] + oz + 3; z < b[3] - 2; z += 7) {
                if (c.d(r.id, x + 0.5, z + 0.5) > -3 || nearDoor(r, x + 0.5, z + 0.5, 3.5) || nearBlocked(x + 0.5, z + 0.5, 2) || p.nearAnchor(x + 0.5, z + 0.5, 3))
                    continue;
                boolean out = keep && !dark && has("the_unlit_lantern");
                fruitTree(x, z, out);
                if (out) {
                    dark = true;
                    nook("the_unlit_lantern", x, 0, z + 1, x + 1, 2, z + 2);
                }
            }
        }
    }

    void fruitTree(int x, int z, boolean out) {
        int h = 4;
        for (int y = 0; y < h; y++) c.s.putOpen(x, W + y, z, darkLog);
        c.s.op(x - 3, W + h - 1, z - 3, x + 3, W + h + 2, z + 3, (b, xx, y, zz) -> {
            if (!b.open(xx, y, zz)) return;
            double d = Math.hypot(xx - x, zz - z) / 3.0, v = (y - (W + h)) / 2.0;
            if (d * d + v * v < 1 && Hash.unit(seed + 65, xx * 7 + y, zz) < 0.9) b.set(xx, y, zz, darkLeaves);
        });
        // lanterns hang under the crown's rim
        for (int i = 0; i < 4; i++) {
            int lx = x + (i == 0 ? 2 : i == 1 ? -2 : 0), lz = z + (i == 2 ? 2 : i == 3 ? -2 : 0);
            if (Hash.unit(seed, x * 31 + z, 66 + i) < 0.3 && !(out && i == 2)) continue;
            c.s.putOpen(lx, W + h - 2, lz, out && i == 2 ? unlit : fruit);
        }
        block(x + 0.5, z + 0.5, 1.5);
        if (!out) light(x + 0.5, h - 1, z + 0.5, 0xffb060, 6f, 0.5f);
    }

    /**
     * The Last Campfire: a fire at the middle of the widest clearing, six log seats round it and a pale figure sitting on
     * all but one. The empty seat keeps the secret.
     */
    void fire(Room r) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        double cx = sh.cx(), cz = sh.cz();
        int x = (int) Math.floor(cx), z = (int) Math.floor(cz);
        c.s.putOpen(x, W, z, campfire);
        int empty = (int) (unit(r.id, 72) * 6);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + unit(r.id, 73);
            int sx = (int) Math.floor(cx + Math.cos(a) * 3.6), sz = (int) Math.floor(cz + Math.sin(a) * 3.6);
            boolean alongX = Math.abs(Math.sin(a)) > Math.abs(Math.cos(a));
            // a log lying round the fire, the figure sitting on it
            c.s.putOpen(sx, W, sz, alongX ? seat : seatZ);
            c.s.putOpen(sx + (alongX ? 1 : 0), W, sz + (alongX ? 0 : 1), alongX ? seat : seatZ);
            if (i == empty) nook("the_empty_seat", sx - 1, 0, sz - 1, sx + 1, 2, sz + 1);
            else c.s.putOpen(sx, W + 1, sz, figure);
        }
        block(cx, cz, 5);
        light(cx, 1, cz, 0xffa050, 14f, 0.8f);
    }

    /** Elsewhere in the camp's land, a figure or two sitting alone by a lantern. */
    void loners(Room r) {
        for (int i = 0; i < 2; i++) {
            if (unit(r.id, 75 + i) < 0.4) continue;
            double[] at = spot(r, 76 + i * 3, 0.3, 0.75, 2.5);
            if (at == null) continue;
            int x = (int) Math.floor(at[0]), z = (int) Math.floor(at[1]);
            c.s.putOpen(x, W, z, figure);
            c.s.putOpen(x + 1, W, z, lantern);
            block(at[0], at[1], 2);
            light(at[0] + 1, 1, at[1], 0xffc070, 6f, 0.5f);
        }
    }

    /** The compass rose at the map's middle: eight points inked out across the paper round a gilt centre. */
    void compass(Room r) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        double cx = sh.cx(), cz = sh.cz();
        int x = (int) Math.floor(cx), z = (int) Math.floor(cz);
        c.s.op(x - 9, W - 1, z - 9, x + 9, W - 1, z + 9, (b, xx, y, zz) -> {
            if (b.get(xx, y, zz) != Sculpt.SOLID && b.state(xx, y, zz) == null) return;
            double dx = xx + 0.5 - cx, dz = zz + 0.5 - cz, d = Math.hypot(dx, dz);
            if (d < 1.5) {
                b.set(xx, y, zz, gold);
                return;
            }
            for (int k = 0; k < 8; k++) {
                double ang = k * Math.PI / 4, len = k % 2 == 0 ? 8.5 : 5.5, wide = k % 2 == 0 ? 1.6 : 1.1;
                double along = dx * Math.cos(ang) + dz * Math.sin(ang), across = Math.abs(-dx * Math.sin(ang) + dz * Math.cos(ang));
                if (along > 0 && along < len && across < wide * (1 - along / len) + 0.2) {
                    b.set(xx, y, zz, ink);
                    return;
                }
            }
            if (Math.abs(d - 6.5) < 0.45) b.set(xx, y, zz, ink);
        });
        block(cx, cz, 1.5);
        nook("the_compass_rose", x - 1, 0, z - 1, x + 1, 2, z + 1);
    }
}
