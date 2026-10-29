package dev.awaken.gates.world;

import net.minecraft.world.level.block.state.BlockState;

/**
 * The rare worlds that are a theme's world with something wrong in it (SPEC §38.13). The theme dresses the floor as its
 * own world; this adds the one thing that makes it rare. The Hollow Sky's great island is the open hand of a stone giant;
 * the Endless Stair is the same landing again and again, a flight up and a flight down from each and none arriving, and
 * one landing is different; the Gates' Heart beats in the middle of the hanging stars, a gilt ring standing round it and
 * veins running out through every island; the Glass Sea is frozen clear, fish moving under it; the Tide Room's pools are
 * full of shells; the Library of Doors has a doorway standing in every room, each one opening out of the next; the
 * Dollhouse has a window in every room and, outside the biggest, an eye the size of a house looking in.
 */
final class TwistDress extends BorrowedDress {
    private final String n;
    private final BlockState giant, heart, vein, gold, pale, step, rail, brass, shimmer, frame, post, back, sclera, iris, pupil,
            glass, ice, light;
    /** The Gates' Heart: where it beats (floor-local, the height of its middle above the floor); NaN before it's placed. */
    private double hx = Double.NaN, hz, hy;

    TwistDress(CaveEngine.Cave c, Dress in) {
        super(c, in, false);
        n = rareName(c.c.worldId());
        giant = GatePainter.state("awaken:rare_giant_stone");
        heart = GatePainter.state("awaken:rare_heart");
        vein = GatePainter.state("awaken:rare_heart_vein");
        gold = GatePainter.state("awaken:sky_gold[axis=y]");
        pale = GatePainter.state("awaken:abyss_pale");
        step = GatePainter.state("minecraft:smooth_quartz_stairs[facing=east]");
        rail = GatePainter.state("minecraft:polished_blackstone_wall");
        brass = GatePainter.state("minecraft:waxed_lightning_rod");
        shimmer = GatePainter.state("awaken:rare_doorway");
        frame = GatePainter.state("minecraft:dark_oak_planks");
        post = GatePainter.state("minecraft:dark_oak_log[axis=y]");
        back = GatePainter.state("minecraft:bookshelf");
        sclera = GatePainter.state("awaken:abyss_sclera");
        iris = GatePainter.state("awaken:abyss_iris");
        pupil = GatePainter.state("awaken:abyss_pupil");
        glass = GatePainter.state("minecraft:glass");
        ice = GatePainter.state("awaken:frost_ice");
        light = WorldMats.light(12);
    }

    @Override
    BlockState skin(int x, int y, int z, boolean floor, boolean ceiling) {
        BlockState s = in.skin(x, y, z, floor, ceiling);
        // the Heart's veins run through every island's ground, thin and branching
        if (n.equals("the_gates_heart") && (floor || y < W - 1)) {
            double v = Math.abs(Hash.noise(seed + 501, x, z, 9) - 0.5) + Math.abs(Hash.noise(seed + 502, x, z, 4) - 0.5) * 0.35;
            if (v < 0.035) return vein;
        }
        return s;
    }

    @Override
    void moreWorld() {
        if (n.equals("the_glass_sea")) {
            // the sea's top frozen clear from shore to shore: you walk on it and see the fish under you
            int top = W - 1;
            c.s.op(0, top, 0, Plots.SIDE - 1, top, Plots.SIDE - 1, (b, x, y, z) -> {
                BlockState st = b.state(x, y, z);
                if (st != null && st.getBlock() == c.fluid.getBlock()) b.set(x, y, z, ice);
            });
        }
    }

    @Override
    void more(Room r, CaveEngine.Kind k) {
        boolean keep = k == CaveEngine.Kind.GREAT || (c.great < 0 && k == CaveEngine.Kind.END);
        switch (n) {
            case "the_hollow_sky" -> {
                if (keep) hand(r);
            }
            case "the_endless_stair" -> {
                if (k != CaveEngine.Kind.ARRIVAL) landing(r, keep);
            }
            case "the_gates_heart" -> {
                if (keep) heart(r);
                else if (k == CaveEngine.Kind.END || (k == CaveEngine.Kind.CAVERN && unit(r.id, 611) < 0.3)) firstGate(r);
            }
            case "the_glass_sea" -> {
                if (k != CaveEngine.Kind.ARRIVAL && keep) bottle(r);
            }
            case "the_tide_room" -> tide(r, keep);
            case "the_library_of_doors" -> {
                if (k != CaveEngine.Kind.ARRIVAL) doorway(r, 620, true);
                if (keep) doorway(r, 640, false);
            }
            case "the_dollhouse" -> window(r, keep);
            default -> {}
        }
    }

    // ---- the Hollow Sky ---------------------------------------------------------------------------------------------

    /**
     * The giant's hand on the great island, palm up as if it were holding the island out: a broad palm a little above the
     * ground with the wrist sloping into the turf (the way up), four fingers curling up round its far edge and a thumb.
     * Standing in the palm finds its secret.
     */
    void hand(Room r) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        double a = unit(r.id, 601) * Math.PI * 2, ux = Math.cos(a), uz = Math.sin(a), vx = -uz, vz = ux;
        double cx = sh.cx() + ux * 1.5, cz = sh.cz() + uz * 1.5;
        double py = W + 1.2;
        int R = 14;
        int ix = (int) Math.floor(cx), iz = (int) Math.floor(cz);
        c.s.fillOpen(ix - R, W, iz - R, ix + R, W + 10, iz + R, (x, y, z) -> {
            double px = x + 0.5, pz = z + 0.5, py0 = y + 0.5;
            double along = (px - cx) * ux + (pz - cz) * uz, side = (px - cx) * vx + (pz - cz) * vz;
            // the palm: a flattened round pad, its top at W + 2
            double palm = Math.max(Math.hypot(along / 4.8, side / 4.2) - 1, (py0 - (py + 0.8)) / 1.2);
            palm = Math.max(palm, (W - 0.5 - py0));
            // the wrist, sinking back into the ground behind the palm
            double wrist = Sculpt.capsule(px, py0, pz, cx - ux * 3, W + 0.2, cz - uz * 3, cx - ux * 9, W - 3, cz - uz * 9, 2.0);
            double d = Math.min(palm * 2.4, wrist);
            // four fingers from the far edge, up and curling back over the palm
            for (int f = 0; f < 4; f++) {
                double s = (f - 1.5) * 2.1, len = f == 1 || f == 2 ? 1.0 : 0.85;
                double bx = cx + ux * 4.6 + vx * s, bz = cz + uz * 4.6 + vz * s;
                double mx = bx + ux * 1.8 * len, mz = bz + uz * 1.8 * len, my = py + 3.6 * len;
                double tx = bx + ux * 0.4, tz = bz + uz * 0.4, ty = py + 6.4 * len;
                d = Math.min(d, Sculpt.capsule(px, py0, pz, bx, py + 0.6, bz, mx, my, mz, 0.95));
                d = Math.min(d, Sculpt.capsule(px, py0, pz, mx, my, mz, tx, ty, tz, 0.85));
            }
            // the thumb, out from the side and up
            double sx = cx + vx * 4.2 + ux * 0.5, sz = cz + vz * 4.2 + uz * 0.5;
            d = Math.min(d, Sculpt.capsule(px, py0, pz, sx, py + 0.6, sz, sx + vx * 1.4 + ux * 1.6, py + 3.8, sz + vz * 1.4 + uz * 1.6, 1.0));
            return d;
        }, giant);
        block(cx, cz, 6);
        int x = (int) Math.floor(cx), z = (int) Math.floor(cz);
        nook("the_palm", x - 1, 2, z - 1, x + 1, 4, z + 1);
        light(cx, 4, cz, 0xfff4e0, 12f, 0.5f);
    }

    // ---- the Endless Stair --------------------------------------------------------------------------------------------

    /**
     * Every island the same landing: a flight of pale steps going up from one side into the dark and a flight going down
     * from the other, each twelve steps and stopping in the air, a dark rail along both. One landing's rail is brass.
     */
    void landing(Room r, boolean differs) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        // the same way round on every landing (along x, or along z where a bridge leaves that way), so they're all alike
        boolean alongX = !nearDoor(r, sh.cx() + sh.a() + 1, sh.cz(), 4) && !nearDoor(r, sh.cx() - sh.a() - 1, sh.cz(), 4);
        if (!alongX && (nearDoor(r, sh.cx(), sh.cz() + sh.b() + 1, 4) || nearDoor(r, sh.cx(), sh.cz() - sh.b() - 1, 4))) return;
        BlockState banister = differs ? brass : rail;
        BlockState stair = alongX ? step : GatePainter.state("minecraft:smooth_quartz_stairs[facing=south]");
        for (int dir = -1; dir <= 1; dir += 2) {
            double reach = (alongX ? sh.a() : sh.b()) - 0.5;
            flight:
            for (int i = 0; i < 12; i++) {
                double along = dir * (reach + i + 1);
                int x = (int) Math.floor(sh.cx() + (alongX ? along : 0)), z = (int) Math.floor(sh.cz() + (alongX ? 0 : along));
                // a flight stops before it reaches another place's slots or a bridge's (it would stand in the bridge's air)
                for (int w = -2; w <= 2; w++) {
                    int o = p.b.at((x + (alongX ? 0 : w)) >> 4, (z + (alongX ? w : 0)) >> 4);
                    if (o >= 0 && o != r.id) break flight;
                }
                // up from one side, down from the other, each step walkable
                int y = dir > 0 ? W + i : W - 2 - i;
                for (int w = -2; w <= 2; w++) {
                    int sx = x + (alongX ? 0 : w), sz = z + (alongX ? w : 0);
                    BlockState st = Math.abs(w) == 2 ? banister : stair;
                    int sy = Math.abs(w) == 2 ? y + 1 : y;
                    c.s.op(sx, sy, sz, sx, sy, sz, (b, xx, yy, zz) -> {
                        if (b.get(xx, yy, zz) == Sculpt.UNSET || b.open(xx, yy, zz)) b.set(xx, yy, zz, st);
                    });
                    if (w == 0 && i % 2 == 1) {
                        // hidden light over every second step, so the way down into the dark (and its banisters) isn't:
                        // the first free block over the step, since near the landing the island's ragged rock reaches out
                        c.s.op(sx, y + 2, sz, sx, y + 4, sz, (b, xx, yy, zz) -> {
                            for (int ly = y + 2; ly < yy; ly++)
                                if (b.get(xx, ly, zz) == Sculpt.UNSET || b.open(xx, ly, zz) || b.state(xx, ly, zz) == c.light) return;
                            if (b.get(xx, yy, zz) == Sculpt.UNSET || b.open(xx, yy, zz)) b.set(xx, yy, zz, c.light);
                        });
                    }
                    if (Math.abs(w) == 2) {
                        // the banister's post stands on the step's edge
                        c.s.op(sx, y, sz, sx, y, sz, (b, xx, yy, zz) -> {
                            if (b.get(xx, yy, zz) == Sculpt.UNSET || b.open(xx, yy, zz)) b.set(xx, yy, zz, pale);
                        });
                    }
                }
            }
        }
        if (differs) {
            int x = (int) Math.floor(sh.cx()), z = (int) Math.floor(sh.cz());
            nook("the_landing_that_differs", x - 1, 0, z - 1, x + 1, 2, z + 1);
        }
    }

    // ---- the Gates' Heart ---------------------------------------------------------------------------------------------

    /**
     * The Heart on the great island: a great round mass of living red stone held up off the ground by its veins, a ring of
     * gilt standing round it, upright and still. Its light fills the island.
     */
    void heart(Room r) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        hx = sh.cx();
        hz = sh.cz();
        hy = 9;
        double rad = 4.5, ring = 8.5;
        double ra = unit(r.id, 621) * Math.PI;
        double nx = Math.cos(ra), nz = Math.sin(ra);
        int R = (int) ring + 2, ix = (int) Math.floor(hx), iz = (int) Math.floor(hz);
        c.s.op(ix - R, W, iz - R, ix + R, W + (int) (hy + ring) + 2, iz + R, (b, x, y, z) -> {
            if (!b.open(x, y, z) && b.get(x, y, z) != Sculpt.UNSET) return;
            double px = x + 0.5 - hx, py = y + 0.5 - (W + hy), pz = z + 0.5 - hz;
            double d = Math.sqrt(px * px + py * py + pz * pz) + (Hash.noise(seed + 503, x * 3 + y, z, 3) - 0.5) * 1.2;
            if (d < rad) {
                b.set(x, y, z, heart);
                return;
            }
            // the veins holding it up: four roots from its underside down to the ground
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2 + 0.4;
                double v = Sculpt.capsule(x + 0.5, y + 0.5, z + 0.5, hx, W + hy - 2, hz, hx + Math.cos(a) * 6, W - 1, hz + Math.sin(a) * 6, 0.9);
                if (v < 0) {
                    b.set(x, y, z, vein);
                    return;
                }
            }
            // the ring, standing upright round it in the plane across (nx, nz)
            double along = px * nx + pz * nz, across = -px * nz + pz * nx;
            double t = Math.hypot(Math.hypot(across, py) - ring, along);
            if (t < 0.75) b.set(x, y, z, gold);
        });
        block(hx, hz, 7);
        light(hx, (int) hy, hz, 0xff4a3a, 22f, 0.9f);
        int x = (int) Math.floor(hx + ring + 3), z = (int) Math.floor(hz);
        c.s.putOpen(x, W + 3, z, light);
    }

    /** The first gate: a small, plain stone arch on a calm island, older than anything else here. */
    void firstGate(Room r) {
        if (!has("the_first_gate")) return;
        double[] at = spot(r, 631, 0.2, 0.55, 3);
        if (at == null) return;
        int x = (int) Math.floor(at[0]), z = (int) Math.floor(at[1]);
        for (int y = 0; y < 4; y++) {
            c.s.putOpen(x - 2, W + y, z, pale);
            c.s.putOpen(x + 2, W + y, z, pale);
        }
        for (int dx = -2; dx <= 2; dx++) c.s.putOpen(x + dx, W + 4, z, pale);
        block(at[0], at[1], 2.5);
        nook("the_first_gate", x - 1, 0, z, x + 1, 2, z);
        light(at[0], 2, at[1], 0xe8dcc8, 6f, 0.4f);
    }

    // ---- the Glass Sea ------------------------------------------------------------------------------------------------

    /** A bottle frozen in the ice, a little way off the shore of the great place: standing over it finds it. */
    void bottle(Room r) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null || !has("the_frozen_bottle")) return;
        double a = unit(r.id, 651) * Math.PI * 2;
        double bx = sh.cx() + Math.cos(a) * (sh.a() + 5), bz = sh.cz() + Math.sin(a) * (sh.b() + 5);
        int x = (int) Math.floor(bx), z = (int) Math.floor(bz);
        if (!CaveEngine.Cave.insideSlots(r, x, z) && p.b.at(x >> 4, z >> 4) >= 0) return;
        c.s.op(x, W - 2, z, x, W - 2, z, (b, xx, yy, zz) -> {
            BlockState st = b.state(xx, yy, zz);
            if (st != null && st.getBlock() == c.fluid.getBlock()) b.set(xx, yy, zz, GatePainter.state("minecraft:decorated_pot[waterlogged=true]"));
        });
        p.mark(FloorLayout.Mark.NOOK, "the_frozen_bottle", x - 1, 0, z - 1, x + 1, 2, z + 1);
        light(bx, 0, bz, 0xd0f0ff, 5f, 0.4f);
    }

    // ---- the Tide Room ------------------------------------------------------------------------------------------------

    /** Shells and dead coral left on the floor by the tide; in the great place, a pool in the wall with a pearl in it. */
    void tide(Room r, boolean keep) {
        BlockState[] shells = {GatePainter.state("minecraft:sea_pickle[pickles=2,waterlogged=false]"),
                GatePainter.state("minecraft:dead_brain_coral[waterlogged=false]"), GatePainter.state("minecraft:dead_fire_coral_fan[waterlogged=false]"),
                GatePainter.state("minecraft:dead_tube_coral[waterlogged=false]"), GatePainter.state("minecraft:sea_pickle[pickles=1,waterlogged=false]")};
        // never on a touchstone's spot (the room's are asked for by now): a shell isn't something one can be set over
        java.util.List<int[]> touches = p.handSpots();
        grow(r, 0.05, 661, (x, y, z, len, i) -> {
            for (int[] t : touches) if (Math.abs(x - t[0]) <= 3 && Math.abs(z - t[1]) <= 3) return null;
            return i < shells.length ? shells[i] : null;
        });
        if (!keep) return;
        int[] at = wallNook(r, "the_pearl_pool", 662);
        if (at == null) return;
        // a pool sunk in the nook's floor, lit from under the water
        c.s.put(at[0], W - 2, at[1], GatePainter.state("minecraft:pearlescent_froglight"));
        c.s.put(at[0], W - 1, at[1], GatePainter.state("minecraft:water"));
        light(at[0] + 0.5, 1, at[1] + 0.5, 0xf0e0ff, 6f, 0.6f);
    }

    // ---- the Library of Doors -----------------------------------------------------------------------------------------

    /**
     * A doorway standing on its own in a room, facing the middle: two posts and a lintel round a shimmer three wide and
     * three high, shelves at its back. Walk into the shimmer and you come out of the next one (the live layer). The door
     * to nowhere is the same with nothing in it.
     */
    void doorway(Room r, int salt, boolean live) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null || (!live && !has("the_door_to_nowhere"))) return;
        double[] at = spot(r, salt, 0.35, 0.65, 3.5);
        if (at == null) return;
        // facing the middle: the way out of the shimmer, along whichever axis is nearer
        double fx = sh.cx() - at[0], fz = sh.cz() - at[1];
        int dx = Math.abs(fx) >= Math.abs(fz) ? (int) Math.signum(fx) : 0, dz = dx == 0 ? (int) Math.signum(fz) : 0;
        if (dx == 0 && dz == 0) dx = 1;
        int x = (int) Math.floor(at[0]), z = (int) Math.floor(at[1]);
        int sx = -dz, sz = dx;
        for (int w = -2; w <= 2; w++) {
            for (int y = 0; y <= 3; y++) {
                int bx = x + sx * w, bz = z + sz * w;
                boolean edge = Math.abs(w) == 2 || y == 3;
                // the back: shelves behind the shimmer, the frame round it
                c.s.putOpen(bx - dx, W + y, bz - dz, edge ? frame : back);
                if (edge) c.s.putOpen(bx, W + y, bz, Math.abs(w) == 2 ? post : frame);
                else if (live) c.s.putOpen(bx, W + y, bz, shimmer);
            }
        }
        block(at[0], at[1], 3);
        int x0 = Math.min(x - sx, x + sx), x1 = Math.max(x - sx, x + sx), z0 = Math.min(z - sz, z + sz), z1 = Math.max(z - sz, z + sz);
        if (live) p.mark(FloorLayout.Mark.DOOR, dx + "," + dz, x0, 0, z0, x1, 2, z1);
        else nook("the_door_to_nowhere", x0, 0, z0, x1, 2, z1);
        light(at[0] + dx * 2, 2, at[1] + dz * 2, live ? 0xb090ff : 0x908070, 6f, 0.5f);
    }

    // ---- the Dollhouse ------------------------------------------------------------------------------------------------

    /**
     * A window in the outside wall of every room, out into the dark round the house; outside the great room's, an eye as
     * big as the room looking in. A tiny door in a wall somewhere leads to a room hardly big enough to stand in.
     */
    void window(Room r, boolean keep) {
        PassageEngine.Shape sh = c.shapes.get(r.id);
        if (sh == null) return;
        // the side with nothing beyond it
        int side = -1;
        for (int t = 0; t < 4 && side < 0; t++) {
            int sd = (int) (unit(r.id, 671) * 4 + t) % 4;
            if (outside(r, sd)) side = sd;
        }
        if (side < 0) return;
        int dx = GatePainter.DX[side], dz = GatePainter.DZ[side];
        double wx = sh.cx() + dx * sh.a(), wz = sh.cz() + dz * sh.b();
        int half = (int) Math.max(2, (dx != 0 ? sh.b() : sh.a()) * 0.3);
        int ix = (int) Math.floor(wx), iz = (int) Math.floor(wz);
        int x0 = dx != 0 ? ix - 3 : ix - half, x1 = dx != 0 ? ix + 3 : ix + half, z0 = dz != 0 ? iz - 3 : iz - half, z1 = dz != 0 ? iz + 3 : iz + half;
        // the shell there turns to glass (only the shell: what stands in the room stays)
        c.s.op(x0, W + 2, z0, x1, W + 6, z1, (b, x, y, z) -> {
            if (b.get(x, y, z) == Sculpt.SOLID) b.set(x, y, z, glass);
        });
        if (keep) eye(wx + dx * 9, wz + dz * 9, dx, dz);
        if (keep && has("the_tiny_door")) {
            int[] at = wallNook(r, "the_tiny_door", 672);
            if (at != null) light(at[0] + 0.5, 1, at[1] + 0.5, 0xffe0b0, 5f, 0.5f);
        }
    }

    boolean outside(Room r, int side) {
        int dx = GatePainter.DX[side], dz = GatePainter.DZ[side];
        int n = dx != 0 ? r.gh : r.gw;
        for (int i = 0; i < n; i++) {
            int sx = dx > 0 ? r.gx + r.gw : dx < 0 ? r.gx - 1 : r.gx + i, sz = dz > 0 ? r.gz + r.gh : dz < 0 ? r.gz - 1 : r.gz + i;
            if (sx < 0 || sz < 0 || sx >= Plots.SIDE / 16 || sz >= Plots.SIDE / 16 || p.b.at(sx, sz) >= 0) return false;
        }
        return true;
    }

    /** The giant's eye outside a window: a disc standing upright facing in, the white, the iris and the pupil. */
    void eye(double cx, double cz, int dx, int dz) {
        double cy = W + 4.5, R = 7;
        int ix = (int) Math.floor(cx), iz = (int) Math.floor(cz);
        c.s.op(ix - 8, (int) cy - 8, iz - 8, ix + 8, (int) cy + 8, iz + 8, (b, x, y, z) -> {
            if (b.get(x, y, z) != Sculpt.UNSET) return;
            double depth = (x + 0.5 - cx) * dx + (z + 0.5 - cz) * dz;
            double across = (x + 0.5 - cx) * -dz + (z + 0.5 - cz) * dx;
            double up = (y + 0.5 - cy) * 1.25;
            double rr = Math.hypot(across, up);
            if (rr > R || Math.abs(depth) > 0.6 + (1 - rr / R) * 1.5) return;
            b.set(x, y, z, rr < 1.4 ? pupil : rr < 3.6 ? iris : sclera);
        });
        light(cx - dx * 4, 4, cz - dz * 4, 0xffe8c0, 10f, 0.4f);
    }
}
