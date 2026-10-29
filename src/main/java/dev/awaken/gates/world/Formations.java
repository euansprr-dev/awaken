package dev.awaken.gates.world;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Things standing in a place's space (SPEC §38.14): pillars from the ground to the roof that flare at both ends, outcrops
 * half sunk and crowned with slabs, fallen pieces lying half sunk, heaps with drifts round them. They stand in clusters
 * in the wild (never on the way or a pad; an outcrop may lean on the wall), one or two in a chunk's cavern and up to three
 * clusters in a bigger place, cut from the theme's own blocks ({@link Dress#formation}). Planned once the ground is
 * solved, so each stands on it; every one is blocked, so nothing spawns in it.
 */
final class Formations {
    /** The parts a theme gives ({@link Dress#formation}); null is the shell's own skin. */
    static final int PILLAR = 0, ROCK = 1, FALLEN = 2, HEAP = 3;

    private final CaveEngine.Cave c;
    private final Relief rf;
    private final int W;
    private final long seed;

    Formations(CaveEngine.Cave c) {
        this.c = c;
        this.rf = c.relief;
        this.W = c.W;
        this.seed = c.seed ^ 0xF0E3A7L;
    }

    void place() {
        for (var e : c.kinds.entrySet()) {
            if (!Relief.shaped(e.getValue()) || c.shapes.get(e.getKey()) == null) continue;
            Room r = c.p.world(e.getKey());
            if (r.type == Room.Type.BOSS) continue;
            place(r);
        }
    }

    /** Spots wild enough to stand something on: off the way and the pads, inside the place (an outcrop may lean on its wall). */
    private boolean spot(int x, int z) {
        return rf.wild(x, z) >= 0.5 && !rf.pinned.get(x + z * Plots.SIDE) && rf.inside(x, z) >= 0.75 && !near(x + 0.5, z + 0.5, 1.5);
    }

    /**
     * Room for a footprint of radius r round (cx, cz): every column under it wild (off the way) or in the wall (an outcrop
     * may lean into it), none on a pad.
     */
    private boolean room(double cx, double cz, double r) {
        int x0 = (int) Math.floor(cx - r), x1 = (int) Math.floor(cx + r), z0 = (int) Math.floor(cz - r), z1 = (int) Math.floor(cz + r);
        for (int z = z0; z <= z1; z++)
            for (int x = x0; x <= x1; x++) {
                if (Math.hypot(x + 0.5 - cx, z + 0.5 - cz) > r) continue;
                if (x < 0 || z < 0 || x >= Plots.SIDE || z >= Plots.SIDE || rf.pinned.get(x + z * Plots.SIDE)) return false;
                if (rf.wild(x, z) < 0.35 && rf.inside(x, z) > 0) return false;
            }
        return true;
    }

    private boolean near(double x, double z, double dist) {
        for (double[] b : c.blocked) if (Math.hypot(x - b[0], z - b[1]) < b[2] + dist) return true;
        return false;
    }

    private void place(Room r) {
        int id = r.id;
        int[] b = Dress.box(r);
        List<int[]> spots = new ArrayList<>();
        for (int z = b[1]; z <= b[3]; z++) for (int x = b[0]; x <= b[2]; x++) if (spot(x, z)) spots.add(new int[]{x, z});
        // a small place (a chunk's cavern) gets one or two things in its wild, a big one up to three clusters of two to four
        if (spots.size() < 3) return;
        boolean small = spots.size() < 40;
        int clusters = small ? 1 : Math.min(3, 1 + spots.size() / 150);
        for (int k = 0; k < clusters; k++) {
            // the cluster's first piece where one fits (four tries), the rest round it
            int[] at = null;
            for (int t = 0; t < 4 && at == null; t++) {
                int[] p = spots.get((int) (Hash.unit(Hash.of(seed, id, k * 8 + t)) * spots.size()));
                if (spot(p[0], p[1]) && item(id, p[0], p[1], Hash.of(seed + 7, id, k * 16), true)) at = p;
            }
            if (at == null) continue;
            int items = small ? 1 + (int) (Hash.unit(Hash.of(seed + 3, id, k)) * 2) : 2 + (int) (Hash.unit(Hash.of(seed + 3, id, k)) * 3);
            for (int i = 1; i < items; i++) {
                long hi = Hash.of(seed + 7, id, k * 16 + i);
                double a = Hash.unit(hi) * Math.PI * 2, d = 2.5 + Hash.unit(Hash.mix(hi)) * 2.5;
                int x = (int) Math.floor(at[0] + 0.5 + Math.cos(a) * d), z = (int) Math.floor(at[1] + 0.5 + Math.sin(a) * d);
                if (spot(x, z)) item(id, x, z, hi, false);
            }
        }
    }

    /**
     * One piece: a pillar (only a cluster's first, 2.5 or more from the wall where the roof is 7 or more over the ground),
     * an outcrop, a fallen piece or a heap.
     */
    private boolean item(int id, int x, int z, long h, boolean first) {
        double roll = Hash.unit(Hash.mix(h + 3));
        double room = c.ceil(id, x + 0.5, z + 0.5) - Math.max(0, rf.stand(x, z));
        if (first && room >= 7 && rf.inside(x, z) >= 2.5 && roll < 0.45) return pillar(id, x, z, h);
        if (roll < 0.55) return rock(x, z, h);
        if (roll < 0.8) return fallen(x, z, h);
        return heap(x, z, h);
    }

    /** A pillar from the ground to the roof, slimmest a little under halfway, flaring into the ground and wider into the roof. */
    private boolean pillar(int id, int x, int z, long h) {
        double cx = x + 0.5, cz = z + 0.5;
        double r0 = 0.8 + Hash.unit(Hash.mix(h + 5)) * 0.6, flare = 1.1 + Hash.unit(Hash.mix(h + 7)) * 0.6;
        if (!room(cx, cz, r0 + flare * 0.7 + 0.6)) return false;
        int floor = W + rf.stand(x, z), roof = W + (int) Math.ceil(c.ceil(id, cx, cz));
        int y0 = floor - 2, y1 = roof + 3, rr = (int) Math.ceil(r0 + flare) + 1;
        c.blocked.add(new double[]{cx, cz, r0 + flare * 0.7 + 0.3});
        mass(x - rr, y0, z - rr, x + rr, y1, z + rr, PILLAR, (px, py, pz) -> {
            double t = Relief.clamp((py - floor) / (double) (roof - floor), 0, 1);
            double f = t < 0.4 ? 0.7 * sq((0.4 - t) / 0.4) : Math.pow((t - 0.4) / 0.6, 3);
            double rad = r0 + flare * f + (Hash.noise3(seed + 11, px, py, pz, 2.5) - 0.5) * 0.5;
            return Math.hypot(px - cx, pz - cz) - rad;
        });
        return true;
    }

    /** An outcrop half sunk in the ground, lumpy, crowned with the theme's slabs where its top steps up. */
    private boolean rock(int x, int z, long h) {
        double cx = x + 0.5 + (Hash.unit(Hash.mix(h + 9)) - 0.5), cz = z + 0.5 + (Hash.unit(Hash.mix(h + 11)) - 0.5);
        double a = 1.4 + Hash.unit(Hash.mix(h + 13)) * 1.2, bb = 1.2 + Hash.unit(Hash.mix(h + 15)) * 1.0;
        if (!room(cx, cz, a + 0.4)) return false;
        double cy = W + rf.stand(x, z) - bb * 0.35;
        int ra = (int) Math.ceil(a) + 1, rb = (int) Math.ceil(bb) + 1;
        c.blocked.add(new double[]{cx, cz, a});
        Sculpt.Field f = (px, py, pz) -> {
            double dx = (px - cx) / a, dy = (py - cy) / bb, dz = (pz - cz) / a;
            return (Math.sqrt(dx * dx + dy * dy + dz * dz) - 1) * Math.min(a, bb) + (Hash.noise3(seed + 13, px, py, pz, 1.8) - 0.5) * 0.9;
        };
        mass(x - ra, (int) cy - rb, z - ra, x + ra, (int) cy + rb, z + ra, ROCK, f);
        crown(x - ra, z - ra, x + ra, z + ra, (int) cy + rb, f);
        return true;
    }

    /** A fallen piece lying half sunk along the ground: a column, a comb, a girder, by the theme. */
    private boolean fallen(int x, int z, long h) {
        double a = Hash.unit(Hash.mix(h + 17)) * Math.PI;
        double len = 4 + Hash.unit(Hash.mix(h + 19)) * 3, rad = 0.7 + Hash.unit(Hash.mix(h + 21)) * 0.45;
        double cx = x + 0.5, cz = z + 0.5, dx = Math.cos(a) * len / 2, dz = Math.sin(a) * len / 2;
        for (double t = -0.5; t <= 0.5; t += 0.25) if (!room(cx + dx * 2 * t, cz + dz * 2 * t, rad + 0.3)) return false;
        // each end rests on the ground under it, so a piece over a rise tips with it
        int ax = (int) Math.floor(cx - dx), az = (int) Math.floor(cz - dz), bx = (int) Math.floor(cx + dx), bz = (int) Math.floor(cz + dz);
        double ya = W + rf.stand(ax, az) + rad * 0.3, yb = W + rf.stand(bx, bz) + rad * 0.3;
        int rr = (int) Math.ceil(len / 2 + rad) + 1;
        c.blocked.add(new double[]{cx, cz, len / 2});
        mass(x - rr, (int) Math.floor(Math.min(ya, yb) - rad) - 1, z - rr, x + rr, (int) Math.ceil(Math.max(ya, yb) + rad) + 1, z + rr, FALLEN,
                (px, py, pz) -> Sculpt.capsule(px, py, pz, cx - dx, ya, cz - dz, cx + dx, yb, cz + dz, rad));
        return true;
    }

    /** A heap: a low lump with a drift slumped round it. */
    private boolean heap(int x, int z, long h) {
        double cx = x + 0.5, cz = z + 0.5, cy = W + rf.stand(x, z) - 0.4;
        if (!room(cx, cz, 1.8)) return false;
        c.blocked.add(new double[]{cx, cz, 1.4});
        mass(x - 2, (int) cy - 1, z - 2, x + 2, (int) cy + 2, z + 2, HEAP, (px, py, pz) -> {
            double dx = (px - cx) / 1.5, dy = (py - cy) / 1.1, dz = (pz - cz) / 1.5;
            return Math.sqrt(dx * dx + dy * dy + dz * dz) - 1 + (Hash.noise3(seed + 17, px, py, pz, 1.4) - 0.5) * 0.6;
        });
        c.s.op(x - 3, W, z - 3, x + 3, W, z + 3, (buf, bx, by, bz) -> {
            double d = Math.hypot(bx + 0.5 - cx, bz + 0.5 - cz);
            if (d < 1.2 || d > 3 || rf.pinned.get(bx + bz * Plots.SIDE)) return;
            int top = W + rf.stand(bx, bz);
            if (!buf.open(bx, top, bz) || !Dress.solid(buf, bx, top - 1, bz)) return;
            BlockState drift = c.dress.ground(Relief.DRIFT, bx, top, bz);
            int layers = (int) Math.round((3 - d) / 1.8 * 4 + (Hash.unit(seed + 19, bx, bz) - 0.5) * 1.5);
            if (drift != null && layers >= 1) buf.set(bx, top, bz, drift.trySetValue(BlockStateProperties.LAYERS, Math.min(6, layers)));
        });
        return true;
    }

    /** Fills a field's inside with the theme's part (or the shell's own skin), in a box. */
    private void mass(int x0, int y0, int z0, int x1, int y1, int z1, int part, Sculpt.Field f) {
        c.s.op(x0, y0, z0, x1, y1, z1, (buf, x, y, z) -> {
            if (f.d(x + 0.5, y + 0.5, z + 0.5) >= 0) return;
            BlockState s = c.dress.formation(part, x, y, z);
            if (s == null) buf.mark(x, y, z, Sculpt.SOLID);
            else buf.set(x, y, z, s);
        });
    }

    /** Slabs on an outcrop's top where it steps up, so its top climbs in half blocks. */
    private void crown(int x0, int z0, int x1, int z1, int yTop, Sculpt.Field f) {
        c.s.op(x0, W, z0, x1, W, z1, (buf, x, y, z) -> {
            int top = W - 3;
            while (top <= yTop && !buf.open(x, top + 1, z)) top++;
            if (top > yTop || top < W || f.d(x + 0.5, top + 0.5, z + 0.5) >= 0 || !buf.open(x, top + 2, z)) return;
            boolean step = false;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) step |= !buf.open(x + d[0], top + 1, z + d[1]);
            if (!step) return;
            BlockState slab = c.dress.ground(Relief.SLAB, x, top + 1, z);
            if (slab != null) buf.set(x, top + 1, z, slab);
        });
    }

    private static double sq(double v) {
        return v * v;
    }
}
