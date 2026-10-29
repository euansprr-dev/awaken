package dev.awaken.gates.world;

import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The Sky Archipelago (SPEC §38.13): islands of pale layered rock in the open sky over a sea of cloud, turf and small
 * flowers on top, roots hanging under, rope bridges between. The Isles have water running off their edges into nothing,
 * little flowering trees and wind lanterns; the Sky Ruins are the white marble and gilt of a city that fell from higher
 * still, its columns broken and its temple roofless; the Wind Stair is small islands in a storm with stones floating
 * loose round the bridges; Above the Clouds is the highest, far islands drifting round it and a ring of pennants round
 * the Wind Well where the Great Wind comes up; the Whale Road (the secret world) is the back of a sky whale.
 */
final class SkyDress extends Dress {
    final BlockState rock, turf, marble, carved, gold, planks, rope, fall, vines, crystal, banner, lantern, hide, barnacle, chime,
            fence, log, leaves, grass, daisy, dandelion, dark;
    /** Shaped ground (SPEC §38.14): marble slabs and stairs on the ruins, rock at the cliffs, turf drifted, pebbles */
    final BlockState gFloorSlab, gFloorStairs, gWallSlab, gWallStairs, gDrift, gBits;
    final boolean isles, ruins, stair, high;
    private int[] secretBox;

    SkyDress(CaveEngine.Cave c) {
        super(c);
        rock = mat("wall", "awaken:sky_rock");
        turf = mat("floor", "awaken:sky_turf");
        marble = mat("marble", "awaken:sky_marble");
        carved = mat("wall_glow", "awaken:sky_marble_carved");
        gold = mat("trim", "awaken:sky_gold");
        planks = mat("planks", "awaken:sky_planks");
        rope = mat("rope", "awaken:sky_rope");
        fall = mat("fall", "awaken:sky_fall");
        vines = mat("vines", "awaken:sky_vines");
        crystal = mat("crystal", "awaken:sky_crystal");
        banner = mat("banner", "awaken:sky_banner");
        lantern = mat("lamp", "awaken:sky_lantern");
        hide = mat("hide", "awaken:sky_hide");
        barnacle = mat("barnacle", "awaken:sky_barnacle");
        chime = mat("touch", "awaken:sky_chime");
        gFloorSlab = mat("ground_slab", "awaken:sky_marble_slab");
        gFloorStairs = mat("ground_stairs", "awaken:sky_marble_stairs");
        gWallSlab = mat("wall_slab", "awaken:sky_rock_slab");
        gWallStairs = mat("wall_stairs", "awaken:sky_rock_stairs");
        gDrift = mat("ground_drift", "awaken:sky_turf_drift");
        gBits = mat("ground_bits", "awaken:sky_pebbles");
        fence = GatePainter.state("minecraft:birch_fence");
        log = GatePainter.state("minecraft:birch_log[axis=y]");
        leaves = WorldMats.with(GatePainter.state("minecraft:flowering_azalea_leaves"), "persistent", "true");
        grass = GatePainter.state("minecraft:short_grass");
        daisy = GatePainter.state("minecraft:oxeye_daisy");
        dandelion = GatePainter.state("minecraft:dandelion");
        dark = GatePainter.state("minecraft:black_concrete");
        String n = name();
        ruins = n.equals("sky_ruins");
        stair = n.equals("wind_stair");
        high = n.equals("above_the_clouds");
        isles = !ruins && !stair && !high;
    }

    @Override
    int glow() {
        return 0xc8f0ff;
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

    // ---- the skin -----------------------------------------------------------------------------------------------------

    @Override
    BlockState skin(int x, int y, int z, boolean fl, boolean ceiling) {
        // the whale's back: blue-grey hide crusted with barnacles
        if (inSecret(x, z)) return Hash.noise(seed + 9, x, z, 3) > 0.68 ? barnacle : hide;
        int rise = y - W;
        if (fl) {
            if (ruins) return Hash.noise(seed + 3, x, z, 6) > 0.58 ? turf : Hash.noise(seed + 4, x, z, 2) > 0.8 ? carved : marble;
            return turf;
        }
        // the fallen city's foundations show in the island's side under the turf
        if (ruins && rise >= -4 && rise < 0) return rise == -2 && Math.floorMod(x + z, 6) == 0 ? carved : marble;
        return rock;
    }

    @Override
    boolean depth() {
        return true;
    }

    @Override
    BlockState ground(int part, int x, int y, int z) {
        return kit(part, ruins ? Hash.noise(seed + 3, x, z, 6) > 0.58 : true, gFloorSlab, gFloorStairs, gWallSlab, gWallStairs, gDrift, gBits);
    }

    @Override
    double bits() {
        return 0.083;
    }

    @Override
    BlockState creep(int x, int y, int z) {
        return turf;
    }

    @Override
    BlockState formation(int part, int x, int y, int z) {
        // rock outcrops under turf, fallen ruin stones, a heap of rock
        return pieces(part, x, y, z, rock, null, rock, turf, marble, rock);
    }

    @Override
    BlockState screen(int lat, int rise, int side) {
        return rise == 0 || rise % 5 == 4 ? carved : lat % 6 == 0 ? gold : marble;
    }

    @Override
    List<FloorAir.Mist> mists() {
        List<FloorAir.Mist> l = stair ? mist(0xb8c4d4, 0.3f, 0x9aa8b8, 0.16f) : mist(0xe4eef8, 0.16f, 0xd4e2f0, 0.08f);
        // the sea of cloud under the islands, thick where it lies
        if (c.clouds > 0) l.add(new FloorAir.Mist(-c.clouds - 3, 6, 0xf4f8fc, 0.6f));
        return l;
    }

    // ---- the world round the places -----------------------------------------------------------------------------------

    @Override
    void world() {
        // far islands drifting out past the edges of the places, too far to reach
        if (!(high && hero("far_isles")) && !(stair && hero("floating_steps"))) return;
        int n = high ? 10 : 6;
        for (int i = 0; i < n; i++) {
            int x = 8 + (int) (Hash.unit(seed, 401, i) * 240), z = 8 + (int) (Hash.unit(seed, 402, i) * 240);
            if (p.b.at(x >> 4, z >> 4) >= 0) continue;
            int y = W + (int) ((Hash.unit(seed, 403, i) - 0.4) * 24);
            double r = high ? 3 + Hash.unit(seed, 404, i) * 4 : 2 + Hash.unit(seed, 404, i) * 2;
            farIsle(x, y, z, r);
        }
    }

    /** A small island hanging in the air: turf on top, rock tapering to a point under it, roots off its bottom. */
    void farIsle(int cx, int cy, int cz, double r) {
        int R = (int) Math.ceil(r) + 1, depth = (int) Math.round(r * 1.6);
        c.s.op(cx - R, cy - depth - 3, cz - R, cx + R, cy, cz + R, (b, x, y, z) -> {
            if (b.get(x, y, z) != Sculpt.UNSET) return;
            double d = Math.hypot(x + 0.5 - cx, z + 0.5 - cz);
            int down = cy - y;
            double rr = r * (1 - down / (double) (depth + 1)) + Hash.noise(seed + 61, x, z, 2) * 0.8;
            if (down <= depth && d < rr) b.set(x, y, z, down == 0 ? turf : rock);
            else if (down > 0 && d < rr + 0.2 && Hash.unit(seed + 63, x, z) < 0.2) b.set(x, y, z, vines);
        });
    }

    // ---- places -------------------------------------------------------------------------------------------------------

    @Override
    void cavern(Room r, CaveEngine.Kind k) {
        int id = r.id;
        PassageEngine.Shape sh = c.shapes.get(id);
        roots(r);
        if (isles) {
            grow(r, 0.08, 31, (x, y, z, len, i) -> i < 5 ? grass : i == 5 ? daisy : i == 6 ? dandelion : null);
            if (k != CaveEngine.Kind.ARRIVAL) {
                for (int t = 0; t < (k == CaveEngine.Kind.GREAT ? 3 : 1 + (int) (unit(id, 33) * 2)); t++) tree(r, 34 + t * 5);
                if (hero("waterfalls")) waterfall(r, 50);
                if (k == CaveEngine.Kind.GREAT && hero("waterfalls")) waterfall(r, 57);
            }
            grow(r, 0.004, 37, (x, y, z, len, i) -> crystal);
        }
        if (ruins) {
            grow(r, 0.05, 41, (x, y, z, len, i) -> i < 4 ? grass : null);
            if (k != CaveEngine.Kind.ARRIVAL && hero("fallen_city")) columns(r, k == CaveEngine.Kind.GREAT ? 7 : 3);
            if (k == CaveEngine.Kind.GREAT && hero("broken_temple")) temple(r);
        }
        if (stair) {
            grow(r, 0.02, 43, (x, y, z, len, i) -> crystal);
            grow(r, 0.04, 44, (x, y, z, len, i) -> i < 3 ? grass : null);
            if (k != CaveEngine.Kind.ARRIVAL) poles(r, 2);
        }
        if (high) {
            grow(r, 0.06, 47, (x, y, z, len, i) -> i < 4 ? grass : i == 4 ? daisy : null);
            grow(r, 0.008, 48, (x, y, z, len, i) -> crystal);
            if (k == CaveEngine.Kind.GREAT && hero("the_wind_well")) windWell(r);
            else if (k != CaveEngine.Kind.ARRIVAL) tree(r, 49);
        }
        if (k != CaveEngine.Kind.ARRIVAL) {
            lanterns(r, k == CaveEngine.Kind.GREAT ? 3 : 1);
            touchstones(r, chime, k == CaveEngine.Kind.GREAT ? 2 : 1, "the_first_chime", 60);
        }
        // each world's hidden place is on its great island, or the last one when no island is great
        boolean keep = k == CaveEngine.Kind.GREAT || (c.great < 0 && k == CaveEngine.Kind.END);
        if (keep && sh != null) {
            String secret = isles ? "the_hidden_spring" : ruins ? "the_fallen_crown" : stair ? "the_eye_of_the_storm" : "the_highest_isle";
            double[] at = spot(r, 70, 0.3, 0.7, 2.5);
            if (at != null) {
                int x = (int) Math.floor(at[0]), z = (int) Math.floor(at[1]);
                nook(secret, x - 1, 0, z - 1, x + 1, 2, z + 1);
                if (has(secret)) {
                    c.s.putOpen(x, W, z, isles ? crystal : ruins ? gold : crystal);
                    block(at[0], at[1], 2);
                }
            }
        }
        if (sh != null && c.dark) light(sh.cx(), 3, sh.cz(), 0xc8e0ff, 10f + r.gw * 3, 0.5f);
    }

    /** Roots and vines hanging under an island where it overhangs, longer near its middle. */
    void roots(Room r) {
        int[] b = box(r);
        int id = r.id;
        c.s.op(b[0], W - c.under - 2, b[1], b[2], W - 2, b[3], (buf, x, y, z) -> {
            if (buf.get(x, y, z) != Sculpt.UNSET || buf.get(x, y + 1, z) != Sculpt.SOLID) return;
            long h = Hash.of(seed + 23, x, z);
            if (Hash.unit(h) >= 0.3) return;
            int len = 1 + (int) (Hash.unit(Hash.mix(h)) * (c.d(id, x + 0.5, z + 0.5) < -4 ? 5 : 2));
            for (int i = 0; i < len; i++) {
                if (buf.get(x, y - i, z) != Sculpt.UNSET) break;
                buf.set(x, y - i, z, vines);
            }
        });
    }

    /** A little flowering tree: a birch trunk four high under a round crown of azalea. */
    void tree(Room r, int salt) {
        double[] at = spot(r, salt, 0.2, 0.75, 3);
        if (at == null) return;
        int x = (int) Math.floor(at[0]), z = (int) Math.floor(at[1]);
        int h = 3 + (int) (unit(r.id, salt + 1) * 2);
        for (int y = 0; y < h; y++) c.s.putOpen(x, W + y, z, log);
        c.s.op(x - 2, W + h - 1, z - 2, x + 2, W + h + 2, z + 2, (b, xx, y, zz) -> {
            if (!b.open(xx, y, zz)) return;
            double d = Math.hypot(xx - x, (y - (W + h + 0.5)) * 1.3) + Math.abs(zz - z) * 0.6;
            if (d < 2.6 && Hash.unit(seed + 27, xx * 7 + y, zz) < 0.9) b.set(xx, y, zz, leaves);
        });
        block(at[0], at[1], 1.5);
    }

    /**
     * Water running off an island's edge: a little pool just inside it, and the fall from the lip down past the island's
     * underside into the cloud (a curtain you can see through and walk round).
     */
    void waterfall(Room r, int salt) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        int id = r.id;
        for (int t = 0; t < 12; t++) {
            double a = Hash.unit(seed, id * 13 + salt, t) * Math.PI * 2;
            double ex = sh.cx() + Math.cos(a) * sh.a(), ez = sh.cz() + Math.sin(a) * sh.b();
            // walk in from the far edge to the island's lip
            double lx = ex, lz = ez;
            for (int s = 0; s < 12 && c.d(id, lx, lz) > -0.6; s++) {
                lx -= Math.cos(a) * 0.5;
                lz -= Math.sin(a) * 0.5;
            }
            if (nearDoor(r, lx, lz, 5) || p.nearAnchor(lx, lz, 4)) continue;
            double ox = lx + Math.cos(a) * 1.2, oz = lz + Math.sin(a) * 1.2;
            int fx = (int) Math.floor(ox), fz = (int) Math.floor(oz);
            double px = lx - Math.cos(a) * 2.0, pz = lz - Math.sin(a) * 2.0;
            int wx = (int) Math.floor(px), wz = (int) Math.floor(pz);
            if (c.d(id, px, pz) > -1.6) continue;
            // the pool sits in the turf, rock all round it
            c.s.op(wx, W - 1, wz, wx, W - 1, wz, (b, x, y, z) -> {
                if (solid(b, x, y - 1, z) && solid(b, x + 1, y, z) && solid(b, x - 1, y, z) && solid(b, x, y, z + 1) && solid(b, x, y, z - 1))
                    b.set(x, y, z, GatePainter.state("minecraft:water"));
            });
            c.s.op(fx, W - c.under - 8, fz, fx, W, fz, (b, x, y, z) -> {
                if (b.get(x, y, z) == Sculpt.UNSET) b.set(x, y, z, fall);
            });
            block(px, pz, 1.5);
            return;
        }
    }

    /** Broken columns standing about: marble drums on a carved foot, some whole to a gilt capital, most snapped off. */
    void columns(Room r, int n) {
        for (int i = 0; i < n; i++) {
            double[] at = spot(r, 80 + i * 3, 0.25, 0.85, 2);
            if (at == null) continue;
            int x = (int) Math.floor(at[0]), z = (int) Math.floor(at[1]);
            int h = 2 + (int) (unit(r.id, 81 + i * 3) * 7);
            for (int y = 0; y < h; y++) c.s.putOpen(x, W + y, z, y == 0 ? carved : y == h - 1 && h >= 7 ? gold : marble);
            // the broken top lies beside it
            if (h < 6) c.s.putOpen(x + 1, W, z + (i % 2 == 0 ? 1 : -1), marble);
            block(at[0], at[1], 1.2);
        }
    }

    /**
     * The fallen city's temple on the great island: a ring of columns round a carved floor, a broken architrave on some,
     * a gilt basin at the middle and the sky where its roof was.
     */
    void temple(Room r) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        double cx = sh.cx(), cz = sh.cz(), R = Math.min(sh.a(), sh.b()) * 0.45;
        int ix = (int) Math.floor(cx), iz = (int) Math.floor(cz);
        c.s.op(ix - (int) R - 1, W - 1, iz - (int) R - 1, ix + (int) R + 1, W - 1, iz + (int) R + 1, (b, x, y, z) -> {
            if (b.open(x, W, z) && Math.hypot(x + 0.5 - cx, z + 0.5 - cz) < R + 0.5) b.set(x, y, z, (x + z) % 3 == 0 ? carved : marble);
        });
        int n = 10;
        for (int i = 0; i < n; i++) {
            double a = i / (double) n * Math.PI * 2;
            double x = cx + Math.cos(a) * R, z = cz + Math.sin(a) * R;
            if (nearDoor(r, x, z, 3) || c.d(r.id, x, z) > -1.5) continue;
            int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
            int h = i % 3 == 1 ? 3 : 7;
            for (int y = 0; y < h; y++) c.s.putOpen(bx, W + y, bz, y == 0 ? carved : y == h - 1 ? gold : marble);
            // a span of architrave to the next column where both stand whole
            if (h == 7 && (i + 1) % 3 != 1) {
                double a2 = (i + 1) / (double) n * Math.PI * 2;
                for (double t = 0.15; t < 1; t += 0.2) {
                    double sx = x + (cx + Math.cos(a2) * R - x) * t, sz = z + (cz + Math.sin(a2) * R - z) * t;
                    c.s.putOpen((int) Math.floor(sx), W + 7, (int) Math.floor(sz), carved);
                }
            }
            block(x, z, 1.2);
        }
        c.s.putOpen(ix, W, iz, gold);
        c.s.putOpen(ix, W + 1, iz, lantern);
        block(cx, cz, 1.5);
        light(cx, 4, cz, 0xfff0c0, 12f, 0.5f);
    }

    /** Tall poles of rope with pennants strung between them, snapping in the storm. */
    void poles(Room r, int n) {
        double[][] at = new double[n][];
        for (int i = 0; i < n; i++) {
            at[i] = spot(r, 90 + i * 4, 0.4, 0.8, 2);
            if (at[i] == null) return;
        }
        for (double[] a : at) {
            int x = (int) Math.floor(a[0]), z = (int) Math.floor(a[1]);
            for (int y = 0; y < 6; y++) c.s.putOpen(x, W + y, z, rope);
            block(a[0], a[1], 1);
        }
        for (int i = 0; i + 1 < n; i++) {
            for (double t = 0.1; t < 1; t += 0.08) {
                double x = at[i][0] + (at[i + 1][0] - at[i][0]) * t, z = at[i][1] + (at[i + 1][1] - at[i][1]) * t;
                int y = W + 5 - (int) Math.round(Math.sin(t * Math.PI) * 2);
                c.s.putOpen((int) Math.floor(x), y, (int) Math.floor(z), banner);
            }
        }
    }

    /** Wind lanterns on rope posts about an island. */
    void lanterns(Room r, int n) {
        for (int i = 0; i < n; i++) {
            double[] at = spot(r, 100 + i * 5, 0.55, 0.85, 2);
            if (at == null) continue;
            int x = (int) Math.floor(at[0]), z = (int) Math.floor(at[1]);
            c.s.putOpen(x, W, z, rope);
            c.s.putOpen(x, W + 1, z, rope);
            c.s.putOpen(x, W + 2, z, lantern);
            block(at[0], at[1], 1);
            light(at[0], 3, at[1], 0xffe8b0, 7f, 0.5f);
        }
    }

    /**
     * Above the Clouds' Wind Well on its great island: a round low wall of carved marble, the wind standing up out of it in
     * a column of spray, a ring of carved posts round it with wind lanterns on top and pennants strung between. The Great
     * Wind comes up through it.
     */
    void windWell(Room r) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        double cx = sh.cx(), cz = sh.cz();
        int ix = (int) Math.floor(cx), iz = (int) Math.floor(cz);
        double R = 2.5;
        c.s.op(ix - 7, W, iz - 7, ix + 7, W + 6, iz + 7, (b, x, y, z) -> {
            if (!b.open(x, y, z)) return;
            double d = Math.hypot(x + 0.5 - cx, z + 0.5 - cz);
            int rise = y - W;
            if (d < R) {
                if (rise < 6) b.set(x, y, z, fall);
            } else if (d < R + 1) {
                if (rise == 0) b.set(x, y, z, carved);
            } else if (Math.abs(d - (R + 4)) < 0.5 && Math.floorMod(x * 3 + z, 5) == 0) {
                if (rise < 4) b.set(x, y, z, rise == 3 ? lantern : carved);
            } else if (rise == 4 && Math.abs(d - (R + 3.5)) < 0.6 && Hash.unit(seed + 71, x, z) < 0.6) b.set(x, y, z, banner);
        });
        block(cx, cz, R + 2);
        light(cx, 2, cz, 0xe0f0ff, 12f, 0.6f);
    }

    @Override
    void tunnel(Room r, ChainLayout.Link l) {
        int[] b = box(r);
        ChainLayout.Way way = ChainLayout.Way.of(r, l, seed);
        // pennants along one rail of every bridge
        c.s.op(b[0], W + 1, b[1], b[2], W + 1, b[3], (buf, x, y, z) -> {
            if (buf.get(x, y, z) != Sculpt.UNSET || buf.get(x, y - 1, z) == Sculpt.UNSET) return;
            double[] w = way.at(x + 0.5, z + 0.5);
            if (w != null && w[0] > c.hw + 0.2 && w[0] < c.hw + 1.2 && Math.floorMod((int) Math.round(w[1]), 4) == 0) buf.set(x, y, z, banner);
        });
        // the Wind Stair: loose stones floating beside the bridge, well clear of it
        if (stair && hero("floating_steps")) {
            c.s.op(b[0], W - 3, b[1], b[2], W + 5, b[3], (buf, x, y, z) -> {
                if (buf.get(x, y, z) != Sculpt.UNSET) return;
                double[] w = way.at(x + 0.5, z + 0.5);
                if (w == null || Math.abs(w[0]) < c.hw + 3 || Math.abs(w[0]) > c.hw + 7) return;
                long h = Hash.of(seed + 77, x >> 1, z >> 1, (y - W) >> 1);
                if (Hash.unit(h) < 0.05) buf.set(x, y, z, Hash.unit(Hash.mix(h)) < 0.2 ? turf : rock);
            });
        }
    }

    @Override
    void secret(Room r) {
        // the Whale Road: the whale's back is the island (its skin is hide); a blowhole spouting at its head, fins out at its
        // sides, its eye low on the head's flank where you can lean over and look into it
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        double cx = sh.cx(), cz = sh.cz();
        boolean alongX = sh.a() >= sh.b();
        double reach = (alongX ? sh.a() : sh.b()) * 0.6;
        double hx = cx + (alongX ? reach : 0), hz = cz + (alongX ? 0 : reach);
        int bx = (int) Math.floor(hx), bz = (int) Math.floor(hz);
        // the blowhole: a spout of spray standing up out of the back
        c.s.putOpen(bx, W - 1, bz, dark);
        for (int y = 0; y < 5; y++) c.s.putOpen(bx, W + y, bz, fall);
        block(hx, hz, 1.5);
        // fins: hide reaching out from the flanks over the drop, a little below the back
        for (int side = -1; side <= 1; side += 2) {
            double fx = cx + (alongX ? 0 : side * (sh.a() + 1)), fz = cz + (alongX ? side * (sh.b() + 1) : 0);
            int x0 = (int) Math.floor(fx), z0 = (int) Math.floor(fz);
            c.s.op(x0 - 3, W - 2, z0 - 3, x0 + 3, W - 2, z0 + 3, (b, x, y, z) -> {
                if (b.get(x, y, z) == Sculpt.UNSET && Math.hypot(x - x0, (z - z0) * 0.6) < 3) b.set(x, y, z, hide);
            });
        }
        // the eye: low on the head's flank, black with a pale glint
        double ex = cx + (alongX ? reach : sh.a() + 0.5), ez = cz + (alongX ? sh.b() + 0.5 : reach);
        int ix = (int) Math.floor(ex), iz = (int) Math.floor(ez);
        c.s.op(ix - 1, W - 4, iz - 1, ix + 1, W - 3, iz + 1, (b, x, y, z) -> {
            if (b.get(x, y, z) == Sculpt.SOLID || b.get(x, y, z) == Sculpt.UNSET) b.set(x, y, z, y == W - 3 && x == ix && z == iz ? crystal : dark);
        });
        int nx = (int) Math.floor(cx + (alongX ? reach - 2 : 0)), nz = (int) Math.floor(cz + (alongX ? 0 : reach - 2));
        nook("the_whales_eye", nx - 1, 0, nz - 1, nx + 1, 2, nz + 1);
        light(cx, 4, cz, 0xc8e0ff, 16f, 0.6f);
    }
}
