package dev.awaken.finds.sets;

import dev.awaken.finds.Air;
import dev.awaken.finds.Family;
import dev.awaken.finds.Kind.Verb;
import dev.awaken.finds.Scene;
import dev.awaken.finds.kit.G;
import dev.awaken.finds.kit.M;
import dev.awaken.finds.verb.*;
import dev.awaken.living.region.RegionMap.Group;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.finds.kit.B.*;

/**
 * The Chain Works (SPEC §62.8, family 11): what those who went down left at the top, to hold something. A block of
 * masonry like a fortress, and from rings in its face chains with links the size of houses running across the ground
 * and down a cleft in the rock. You climb to the capstan and push it round; far below, something takes up the slack,
 * the loose link at the cleft's lip is drawn tight, and the vault in the block lets go.
 */
final class MChains {
    private MChains() {}

    @FunctionalInterface
    interface Put {
        void at(int x, int y, int z, BlockState b);
    }

    /**
     * One link: a long loop of bronze round ({@code cx, cy, cz}), {@code a} half its length along its run (dx, dy, dz)
     * and {@code b} half its width, lying flat across the run or standing on edge.
     */
    static void link(double cx, double cy, double cz, double dx, double dy, double dz, double a, double b, boolean flat, Put out) {
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= len; dy /= len; dz /= len;
        double hx = -dz, hz = dx, hl = Math.sqrt(hx * hx + hz * hz);     // level, square to the run
        if (hl < 1e-6) { hx = 1; hz = 0; hl = 1; }
        hx /= hl; hz /= hl;
        double wx = flat ? hx : dy * hz, wy = flat ? 0 : dz * hx - dx * hz, wz = flat ? hz : -dy * hx;
        for (int i = 0; i < 120; i++) {
            double t = i * Math.PI / 60, co = Math.cos(t), si = Math.sin(t);
            double u = a * Math.signum(co) * Math.pow(Math.abs(co), 0.55), v = b * Math.signum(si) * Math.pow(Math.abs(si), 0.55);
            int x0 = (int) Math.round(cx + dx * u + wx * v - 0.5), y0 = (int) Math.round(cy + dy * u + wy * v - 0.5), z0 = (int) Math.round(cz + dz * u + wz * v - 0.5);
            BlockState st = i % 30 < 4 ? M.bronzeBand() : M.bronze();
            for (int k = 0; k < (b >= 2 ? 8 : 4); k++) out.at(x0 + (k & 1), y0 + (b >= 2 ? (k >> 1 & 1) : 0), z0 + (b >= 2 ? k >> 2 : k >> 1), st);
        }
    }

    /**
     * A chain along a run of points, each {x, y, z}: links of half-length {@code a}, lying flat and standing on edge by
     * turns. {@code only}: how many links (a snapped chain stops short). {@code skip}: the index of one link left out
     * and handed to {@code gap} instead (the loose one at the lip), or -1.
     */
    static void chain(Scene s, double[][] pts, double a, double b, int only, int skip, Put gap) {
        double step = 2 * a - 2.8;                                  // each link passes through the last by the thickness of its bar
        int n = 0;
        double carry = a;
        for (int i = 0; i + 1 < pts.length; i++) {
            double dx = pts[i + 1][0] - pts[i][0], dy = pts[i + 1][1] - pts[i][1], dz = pts[i + 1][2] - pts[i][2], len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            for (double d = carry; d <= len; d += step) {
                if (n >= only) return;
                Put out = n == skip && gap != null ? gap : (x, y, z, st) -> { if (y > s.g(x, z) || !s.taken(x, y, z)) s.put(x, y, z, st); };
                link(pts[i][0] + dx * d / len, pts[i][1] + dy * d / len, pts[i][2] + dz * d / len, dx, dy, dz, a, b, n % 2 == 0, out);
                n++;
                carry = d + step - len;
            }
        }
    }

    /** A cleft in the rock: a ragged trench running east to west round z {@code zc}, {@code depth} deep, black water in the bottom. */
    static void cleft(Scene s, int x0, int x1, int zc, int w, int depth) {
        for (int x = x0; x <= x1; x++) {
            double along = (x - x0) / (double) Math.max(1, x1 - x0), taper = Math.sin(Math.PI * along);
            double half = Math.max(0, w * (0.35 + 0.65 * taper) * (0.7 + 0.6 * n(s, x * 1.1, 55, 6.0)));
            int mid = zc + (int) Math.round((n(s, x * 1.0, 91, 9.0) - 0.5) * 6);
            for (int z = mid - (int) half; z <= mid + (int) half; z++) {
                int floor = -depth + (int) (n(s, x * 1.4, z * 1.4, 4.0) * 4) + (int) (Math.abs(z - mid) / Math.max(1.0, half) * 6);
                for (int y = floor; y <= Math.max(0, s.g(x, z)) + 6; y++) if (y > floor + 1 || true) s.put(x, y, z, y <= -depth + 2 ? WATER : AIR);
                s.put(x, floor - 1, z, h(s, x, 3, z) < 40 ? M.seal() : st(Blocks.DEEPSLATE));
            }
        }
    }

    /** The block: coursed masonry in two tiers with relief on every face, a great ring in the south face for each chain. */
    static void block(Scene s, int x0, int z0, int x1, int z1, int h, int[] ringsAt) {
        M.mass(s, x0, 1, z0, x1, h - 5, z1);
        M.mass(s, x0 + 2, h - 4, z0 + 2, x1 - 2, h, z1 - 2);
        M.face(s, Direction.SOUTH, x0, x1, z1, 1, h - 5, 6);
        M.face(s, Direction.NORTH, x0, x1, z0, 1, h - 5, 6);
        M.face(s, Direction.WEST, z0, z1, x0, 1, h - 5, 6);
        M.face(s, Direction.EAST, z0, z1, x1, 1, h - 5, 6);
        for (int x = x0 + 2; x <= x1 - 2; x++) for (int z = z0 + 2; z <= z1 - 2; z++) if (x == x0 + 2 || x == x1 - 2 || z == z0 + 2 || z == z1 - 2) { s.put(x, h + 1, z, Math.floorMod(x + z, 3) == 0 ? M.block() : M.wall()); }
        for (int rx : ringsAt) {
            for (int x = -3; x <= 3; x++) for (int y = 2; y <= 9; y++) s.put(rx + x, y, z1, Math.abs(x) == 3 || y == 9 ? M.band() : M.dark());      // a recess, dark behind the ring
            M.ring(s, rx, 6, z1 + 2, 3, 1.4, 1.4, 0, 0, 1, deg -> deg % 60 < 8 ? M.bronzeBand() : M.bronze());
            for (int z = z1; z <= z1 + 1; z++) s.put(rx, 6, z, M.bronzeBand());
        }
    }

    /** The capstan: a drum with four bars, on a plinth. The drum's top is the part. */
    static void capstan(Scene s, int x, int y, int z) {
        disc(s, x, z, 3.3, y, M.block());
        for (int k = 1; k <= 2; k++) disc(s, x, z, 1.4, y + k, k == 2 ? M.bronzeBand() : M.bronze());
        s.put(x, y + 3, z, M.bronze());
        s.part("capstan", x, y + 3, z);
        for (int q = 0; q < 4; q++) for (int k = 2; k <= 4; k++) s.put(x + DX[q] * k, y + 2, z + DZ[q] * k, k == 4 ? M.bronzeBand() : M.wall());
    }

    /** The vault in a block's south face, between two rings: a bronze door, a room, the prize. */
    static void vault(Scene s, int vx, int z1) {
        for (int x = -2; x <= 2; x++) for (int z = z1 - 6; z <= z1 - 1; z++) for (int y = 1; y <= 4; y++) s.put(vx + x, y, z, AIR);
        for (int x = -2; x <= 2; x++) for (int z = z1 - 6; z <= z1 - 1; z++) s.put(vx + x, 0, z, Math.abs(x) + Math.abs(z - z1 + 3) < 2 ? M.mosaic() : M.flag());
        for (int y = 1; y <= 4; y++) for (int x = -1; x <= 1; x++) {
            s.put(vx + x, y, z1, y == 4 ? M.band() : x == 0 ? M.door() : M.bronzeBand());
            s.put(vx + x, y, z1 + 1, y == 4 && x == 0 ? M.seal() : x == 0 || y == 4 ? AIR : M.block());
            if (x == 0 && y < 4) s.part("door", vx, y, z1);
        }
        Mon.prize(s, vx, 1, z1 - 5, Direction.SOUTH);
        s.put(vx, 4, z1 - 3, M.lamp(true));
        Mon.carving(s, vx + 2, 2, z1 - 3);
    }

    /** A stair up the west end of a block, from the ground to its roof. */
    static void climb(Scene s, int x0, int z0, int z1, int h) {
        for (int j = 0; j <= h; j++) {
            int z = z1 - j, y = 1 + j;
            if (z < z0) break;
            for (int x = x0 - 3; x <= x0 - 1; x++) {
                if (y <= h) s.put(x, y, z, M.step(s, x, z, Direction.NORTH));
                for (int yy = Math.min(0, s.g(x, z)); yy < y; yy++) s.put(x, yy, z, M.course(yy));
            }
        }
        for (int z = z0; z <= z1 - h - 1; z++) for (int x = x0 - 3; x <= x0 - 1; x++) for (int yy = Math.min(0, s.g(x, z)); yy <= h; yy++) s.put(x, yy, z, yy == h ? M.flag() : M.course(yy));
    }

    private static Put slack(Scene s, String part) {
        return (x, y, z, st) -> { if (!s.taken(x, y, z)) { s.put(x, y, z, AIR); s.part(part, x, y, z); } };
    }

    /**
     * {@code half}: half the block's width. {@code h}: its height. {@code chains}: how many. {@code snapped}: a bit for
     * each chain that has parted. {@code depth}: the cleft's. {@code lit}: whole, its capstan under a roof, lamps burning.
     */
    static void works(Scene s, int half, int h, int chains, int snapped, int depth, boolean lit, int reach) {
        int z0 = -half - 12, z1 = -8, zc = 20;
        G.platform(s, G.box(-half - 6, z0 - 3, half + 4, 9), 0, reach);
        s.clear(reach);
        int[] at = new int[chains];
        for (int i = 0; i < chains; i++) at[i] = chains == 1 ? 0 : -half + 6 + i * (2 * half - 12) / (chains - 1);
        block(s, -half, z0, half, z1, h, at);
        for (int x = -half - 5; x <= half + 3; x++) for (int z = z1 + 1; z <= 9; z++) if (!s.taken(x, 0, z)) s.put(x, 0, z, Math.floorMod(x * 3 + z, 7) == 0 ? M.block() : M.flag());
        cleft(s, -Math.min(reach - 4, half + 12), Math.min(reach - 4, half + 12), zc, 7, depth);
        // -- the chains: out of the rings, sagging to the ground, over the lip and down
        for (int i = 0; i < chains; i++) {
            double x = at[i];
            double[][] run = {{x, 6, z1 + 5}, {x, 2.5, z1 + 14}, {x, 2, zc - 7}, {x, -depth + 4, zc + 3}};
            boolean parted = (snapped >> i & 1) != 0, tightens = !parted && i == chains / 2;
            chain(s, run, 5.6, 3.2, parted ? 2 : 99, tightens ? 3 : -1, slack(s, "taut"));
            if (parted) {                                           // what it dropped: links lying where they fell, at all angles
                for (int k = 0; k < 4; k++) {
                    double a = h(s, i * 7 + k, 12, 5) / 255.0 * Math.PI, lx = x + (k % 2 == 0 ? -4 : 5), lz = z1 + 16 + k * 5;
                    link(lx, s.g((int) lx, (int) lz) + 1.6, lz, Math.cos(a), 0, Math.sin(a), 5.6, 3.2, true, (px, py, pz, st) -> { if (!s.taken(px, py, pz) && py > s.g(px, pz) - 1) s.put(px, py, pz, st); });
                }
            }
            if (tightens) {                                         // the loose link by the lip: it is what will be drawn tight
                link(x + 7, s.g((int) x + 7, zc - 12) + 1.6, zc - 12, 0.3, 0, 1, 5.6, 3.2, true, (px, py, pz, st) -> { if (!s.taken(px, py, pz)) { s.put(px, py, pz, st); s.part("slack", px, py, pz); } });
            }
        }
        Mon.carving(s, half - 2, 1, z1 + 2);                        // by the face, where anyone arriving reads it first
        // -- on the roof: the capstan, under what is left of its house; the stair up
        climb(s, -half, z0, z1, h);
        for (int x = -half - 3; x <= -half - 1; x++) s.put(x, h + 1, z0, M.wall());
        for (int z = z0 + 2; z <= z0 + 4; z++) s.put(-half + 2, h + 1, z, AIR);
        capstan(s, 0, h + 1, (z0 + z1) / 2);
        for (int q = 0; q < 4; q++) M.column(s, (q < 2 ? -6 : 6), (z0 + z1) / 2 + (q % 2 == 0 ? -5 : 5), h + 1, h + 6);
        for (int x = -7; x <= 7; x++) for (int z = -6; z <= 6; z++) { boolean edge = Math.abs(x) >= 6 || Math.abs(z) >= 5; if (edge || lit) s.put(x, h + 7, (z0 + z1) / 2 + z, edge ? M.band() : M.blockSlab()); }
        if (lit) s.put(0, h + 6, (z0 + z1) / 2, M.lamp(true));
        else M.ruin(s, -7, (z0 + z1) / 2 - 6, 7, (z0 + z1) / 2 + 6, h + 5, 2, h + 8);
        Mon.carving(s, -4, h + 1, (z0 + z1) / 2 + 4);
        vault(s, chains >= 3 ? (at[0] + at[1]) / 2 : half - 4, z1);
        if (lit) for (int x = -half; x <= half; x += half) for (int z = 2; z <= 8; z += 6) M.lampPost(s, x, 1, z, 3, true);
        // -- the miners' crane at the lip, and their way down
        int cx = -half + 2;
        for (int y = 1; y <= 7; y++) s.put(cx, s.g(cx, zc - 10) + y, zc - 10, st(Blocks.SPRUCE_FENCE));
        for (int k = 0; k <= 5; k++) s.put(cx, s.g(cx, zc - 10) + 8, zc - 10 + k, axis(st(Blocks.STRIPPED_SPRUCE_LOG), Direction.Axis.Z));
        for (int k = 1; k <= 6; k++) s.put(cx, s.g(cx, zc - 10) + 8 - k, zc - 5, st(Blocks.IRON_CHAIN));
        s.put(cx, s.g(cx, zc - 10) + 1, zc - 5, lantern(false, false));
        M.finish(s, 4, 50);
        Mon.camp(s, half + 2 > reach - 8 ? -half - 2 : half - 2, zc - 14);
        wild(s, Math.min(reach - 6, half + 16), reach - 1, 6);
        s.cam(-half - 8, 8, zc + 4, -150, -6).cam(half + 4, h + 26, zc + 8, 140, 44).cam(0, h + 3, (z0 + z1) / 2 + 9, 180, 4).cam(at[chains / 2] + 6, 3, zc - 16, -160, 14);
    }

    /**
     * The Hanging Chain: a block either side of the cleft, a chain slung between their tops, and a great weight hung from
     * its lowest link. The cleft runs north to south here, {@code o} east of the heart, so the heart lies under the west bank.
     */
    static void hanging(Scene s, int reach) {
        int o = 16;
        G.platform(s, G.either(G.box(o - 34, -9, o - 17, 9), G.box(o + 17, -9, o + 34, 9)), 0, reach);
        s.clear(reach);
        for (int side = -1; side <= 1; side += 2) {
            int x0 = o + (side < 0 ? -32 : 19), x1 = o + (side < 0 ? -19 : 32);
            M.mass(s, x0, 1, -7, x1, 12, 7);
            M.face(s, Direction.SOUTH, x0, x1, 7, 1, 12, 5);
            M.face(s, Direction.NORTH, x0, x1, -7, 1, 12, 5);
            M.face(s, side < 0 ? Direction.WEST : Direction.EAST, -7, 7, side < 0 ? x0 : x1, 1, 12, 5);
            for (int z = -7; z <= 7; z++) for (int x = x0; x <= x1; x++) if (x == x0 || x == x1 || Math.abs(z) == 7) s.put(x, 13, z, Math.floorMod(x + z, 3) == 0 ? M.block() : M.wall());
            M.ring(s, o + side * 19, 15, 0, 3, 1.4, 1.4, 0, 0, 1, deg -> deg % 60 < 8 ? M.bronzeBand() : M.bronze());
        }
        for (int z = -reach + 4; z <= reach - 4; z++) {
            double halfW = 9 * (0.75 + 0.5 * n(s, 33, z * 1.1, 6.0)) * Math.min(1, (reach - 3 - Math.abs(z)) / 10.0);
            for (int x = -(int) halfW; x <= (int) halfW; x++) {
                int floor = -24 + (int) (n(s, x * 1.4, z * 1.4, 4.0) * 4) + (int) (Math.abs(x) / Math.max(1.0, halfW) * 7);
                for (int y = floor; y <= Math.max(0, s.g(o + x, z)) + 6; y++) s.put(o + x, y, z, y <= -22 ? WATER : AIR);
                s.put(o + x, floor - 1, z, h(s, x, 3, z) < 40 ? M.seal() : st(Blocks.DEEPSLATE));
            }
        }
        double[][] run = {{o - 17, 15, 0}, {o - 9, 10.5, 0}, {o, 9, 0}, {o + 9, 10.5, 0}, {o + 17, 15, 0}};
        chain(s, run, 5.6, 3.2, 99, 2, slack(s, "taut"));
        for (int y = -8; y <= 6; y++) s.put(o, y, 0, st(Blocks.IRON_CHAIN));          // and what hangs from its middle
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) for (int y = -14; y <= -9; y++) { boolean edge = Math.abs(x) == 2 || Math.abs(z) == 2 || y == -14 || y == -9; if (edge) s.put(o + x, y, z, (Math.abs(x) == 2 && Math.abs(z) == 2) || y == -14 || y == -9 ? M.bronzeBand() : M.bronze()); }
        s.put(o, -9, 0, M.seal());
        link(o - 24, 14.6, 4, 1, 0, 0.2, 5.6, 3.2, true, (px, py, pz, st) -> { if (!s.taken(px, py, pz)) { s.put(px, py, pz, st); s.part("slack", px, py, pz); } });
        climb(s, o - 32, -7, 7, 12);
        capstan(s, o - 26, 13, -2);
        vault(s, o - 27, 7);
        Mon.carving(s, o - 29, 13, 3);
        Mon.carving(s, o + 26, 1, 9);
        for (int y = 1; y <= 12; y++) s.put(o + 33, y, 0, ladder(Direction.EAST));
        M.finish(s, 4, 50);
        Mon.camp(s, o - 26, 20);
        wild(s, 40, reach - 1, 6);
        s.cam(o - 8, 6, 34, 170, -8).cam(o + 20, 44, 36, 150, 44).cam(o - 26, 15, 4, 180, 10).cam(o - 12, 3, 12, -120, -20);
    }

    static void init() {
        Object[][] five = {
                {1, "chains_single", "The Single Chain", (dev.awaken.finds.Kind.Maker) s -> works(s, 7, 9, 1, 0, 14, false, 32), 32,
                        "A block of masonry the size of a barn, with one bronze ring in its face. From the ring a chain with links as long as a cart runs across the ground and down a crack in the rock.",
                        "You climbed to the capstan on the roof and pushed it round six times. Far down, something took up the slack. The loose link pulled tight, and the vault in the block let go.",
                        "It says: one turn of the capstan a year. No more. It is not there to be pulled up. It is there to be kept from going down.",
                        "It says this was the first anchor. They did not know yet how heavy the thing was. They learned.",
                        "Scratched by the ring: it hums if you put your ear to it. I do not put my ear to it any more."},
                {2, "chains_fallen", "The Fallen Chains", (dev.awaken.finds.Kind.Maker) s -> works(s, 22, 12, 3, 5, 18, false, 50), 50,
                        "A fortress of a block with three bronze rings in its face. Two of its chains have parted and lie about the ground in links as big as huts. The third still goes down the cleft.",
                        "You climbed to the capstan and pushed it round. The one chain that still holds drew tight, and the vault in the block let go.",
                        "It says: three chains hold. Two would hold. Check them every year. Do not let it get to one.",
                        "It says the west chain went in a hard frost and the east chain forty years after. Nobody was left who knew how to forge a link.",
                        "It says: it is at one. We are sorry. We did what we could with rope, which was nothing."},
                {3, "chains", "The Chain Works", (dev.awaken.finds.Kind.Maker) s -> works(s, 22, 14, 3, 4, 22, false, 50), 50,
                        "A block of masonry like a fortress on the bare rock, and from three great rings in its face three bronze chains, each link the size of a house, running down into a cleft. One has snapped. On the roof, a capstan under a broken colonnade.",
                        "You climbed to the capstan and pushed it round six times. Far below, something took up the slack. The loose link at the lip drew tight, and the vault in the block let go.",
                        "Three chains hold. Two would hold. Check them every year. Do not let it get to one.",
                        "It says: we do not hold it down. Nothing could. We hold the door, and the door holds it. Keep the chains and you keep the door.",
                        "It says the ones at the bottom can feel the capstan turn. It is how they know somebody is still up here. Turn it when you pass."},
                {4, "chains_hanging", "The Hanging Chain", (dev.awaken.finds.Kind.Maker) s -> hanging(s, 54), 54,
                        "Two blocks of masonry face each other across a cleft, and a chain of house-sized links hangs between their tops. From its lowest link a bronze weight as big as a room hangs straight down into the dark.",
                        "You climbed the western block and pushed the capstan round. The chain drew up a hand's width, the weight swung, and the vault in the block let go.",
                        "It says the weight is a plumb. While it hangs straight, what is under us is still. They watched it every day for two hundred years.",
                        "It says: it swung once, in my grandmother's time. Everyone went down to help. That is where everyone went.",
                        "It says: I was left to watch the plumb. It is straight today. It was straight yesterday. I will cut a line here each day it is straight."},
                {5, "chains_whole", "The Works That Still Hold", (dev.awaken.finds.Kind.Maker) s -> works(s, 30, 15, 5, 0, 24, true, 60), 60,
                        "A long block of masonry with five bronze rings in its face and five chains, whole, drawn tight, running down into the cleft. Lamps burn along it. The capstan on the roof has its house still over it.",
                        "You pushed the capstan round. All five chains sang, the loose link drew tight, and the vault in the block let go.",
                        "It says: five chains hold. This is how it was meant to be. If you find it like this, somebody has been keeping it, and you owe them a turn of the capstan.",
                        "It says nobody is paid to keep the works. Somebody always has. We do not know why, and we have stopped asking.",
                        "The newest line, cut neat. It says: turned it today. All five sound. Whatever they are holding shut down there, they are still holding. Sleep well."}};
        for (Object[] v : five) {
            int tier = (Integer) v[0];
            Mon.kind(Family.CHAINS, tier, (String) v[1], (String) v[2]).verb(Verb.TURN)
                    .lands(Group.BADLANDS, Group.DESERT, Group.MOUNTAIN).slope(13)
                    .see((String) v[5]).learned((String) v[6]).reach((Integer) v[4])
                    .air(Air.of(0x5A4A40, 130).mote(() -> ParticleTypes.ASH, 0.25F, 14, 0.02F).loop(() -> SoundEvents.CHAIN_STEP, 160, 0.25F, 0.4F))
                    .scene((dev.awaken.finds.Kind.Maker) v[3])
                    .script(() -> Lore.with(Steps.of(
                                    Order.of("capstan").times(6).each((c, i) -> {
                                        c.sound(c.pos("capstan", 0), SoundEvents.GRINDSTONE_USE, 2.0F, 0.5F + 0.04F * i);
                                        c.sound(c.pos("capstan", 0), SoundEvents.CHAIN_PLACE, 2.0F, 0.5F);
                                        c.puff(c.pos("capstan", 0).above(), ParticleTypes.CRIT, 6, 0.6, 0.05);
                                    }),
                                    Then.run(c -> {
                                        for (int k = 0; k < c.count("slack"); k++) c.set("slack", k, AIR);
                                        for (int k = 0; k < c.count("taut"); k++) c.set("taut", k, k % 9 == 0 ? M.bronzeBand() : M.bronze());
                                        if (c.count("taut") > 0) c.sound(c.pos("taut", 0), SoundEvents.ANVIL_LAND, 3.0F, 0.4F);
                                        Mon.open(c, "door", SoundEvents.IRON_DOOR_OPEN);
                                    }),
                                    Prize.at("prize")),
                            "carving", (String) v[7], (String) v[8], (String) v[9]))
                    .play(t -> { for (int i = 0; i < 6; i++) t.use("capstan", 0); t.ticks(10).use("carving", 0).use("prize", 0); })
                    .add();
        }
    }
}
