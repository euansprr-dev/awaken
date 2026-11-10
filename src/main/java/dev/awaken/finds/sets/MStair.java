package dev.awaken.finds.sets;

import dev.awaken.finds.Air;
import dev.awaken.finds.Family;
import dev.awaken.finds.Kind.Verb;
import dev.awaken.finds.Scene;
import dev.awaken.finds.kit.G;
import dev.awaken.finds.kit.M;
import dev.awaken.finds.kit.Props;
import dev.awaken.finds.verb.*;
import dev.awaken.living.region.RegionMap.Group;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.finds.kit.B.*;

/**
 * The Stairs (SPEC §64.8, family 4): what those who went up climbed. A hall at the foot where they waited, and a stair
 * on arches that goes up out of it. You climb to the last step that stands and let the counterweight go; the doors of
 * the hall come open. Two pieces: {@link #hall} and {@link #flight}; the five are what stands of them.
 */
final class MStair {
    private MStair() {}

    /** How far a flight has risen {@code t} blocks along: eight steps and a landing of four, again and again. */
    static int rise(int t) {
        return 8 * Math.floorDiv(t, 12) + Math.min(8, Math.floorMod(t, 12) + 1);
    }

    /** Where a flight's piers stand along it: the middle of each landing. */
    static int pierAt(int k) {
        return 9 + 12 * k;
    }

    /** How deep the masonry under the deck is at {@code t}: thin at an arch's crown, deep at its haunches, nothing over a pier. */
    static int haunch(int t, int piers) {
        int a = -1, b = Integer.MAX_VALUE;
        for (int k = 0; k < piers; k++) {
            int p = pierAt(k);
            if (t >= p - 2 && t <= p + 2) return 0;
            if (p + 2 < t) a = p + 2;
            if (p - 2 > t && b == Integer.MAX_VALUE) b = p - 2;
        }
        if (b == Integer.MAX_VALUE) return 2;                     // past the last pier: a stub
        return M.haunch((t - a) / (double) (b - a), 10);
    }

    /**
     * A stair on arches from {@code (x0, base)} toward {@code dir} (+1 east, -1 west), {@code len} long over
     * {@code piers} piers. {@code gapFrom..gapTo}: a span that fell (planks are laid over it when {@code planks}).
     * {@code ragged}: its end breaks off in the air. Returns the height of its last tread.
     */
    static int flight(Scene s, int x0, int base, int dir, int len, int piers, int gapFrom, int gapTo, boolean planks, boolean ragged) {
        Direction up = dir > 0 ? Direction.EAST : Direction.WEST;
        for (int t = 0; t <= len; t++) {
            int x = x0 + dir * t, h = base + rise(t), under = haunch(t, piers), wide = !ragged || t <= len - 3 ? 3 : len - t;
            boolean rises = rise(t) > rise(t - 1);
            if (t >= gapFrom && t <= gapTo) continue;
            for (int z = -wide; z <= wide; z++) {
                if (Math.abs(z) == 3) {
                    s.put(x, h, z, M.block());
                    s.put(x, h - 1, z, M.course(h - 1));
                    s.put(x, h - 2, z, stairT(M.blockStairs(), z > 0 ? Direction.NORTH : Direction.SOUTH));
                    double keep = n(s, x * 0.9, z * 7.0, 3.0);
                    if (!ragged || keep > 0.34) s.put(x, h + 1, z, x % 4 == 0 ? M.block() : M.wall());
                    if (x % 4 == 0 && (!ragged || keep > 0.5)) s.put(x, h + 2, z, M.blockSlab());
                } else {
                    s.put(x, h, z, rises ? M.step(s, x, z, up) : M.flag());
                    for (int y = h - 2 - under; y <= h - 1; y++) s.put(x, y, z, M.course(y));
                }
            }
        }
        for (int k = 0; k < piers; k++) {
            int px = x0 + dir * pierAt(k), top = base + rise(pierAt(k)) - 3;
            M.pier(s, px, 0, 2, top, 5);
            for (int side = -1; side <= 1; side += 2) {            // a pilaster strip up the north and south faces, stepped back at the top
                for (int y = 7; y <= top - 4; y++) s.put(px, y, side * 3, y % 10 == 0 ? M.band() : M.stone());
                s.put(px, top - 3, side * 3, stair(M.blockStairs(), side > 0 ? Direction.NORTH : Direction.SOUTH));
                if (top > 22) for (int y = 12; y <= 14; y++) s.put(px, y, side * 2, M.panel());
            }
        }
        if (planks)                                                // what the climbers laid over the span that fell
            for (int t = gapFrom; t <= gapTo; t++)
                for (int z = -1; z <= 0; z++) {
                    s.put(x0 + dir * t, base + rise(t), z, stair(st(Blocks.SPRUCE_STAIRS), up));
                    s.put(x0 + dir * t, base + rise(t) - 1, z, st(Blocks.SPRUCE_PLANKS));
                }
        return base + rise(len);
    }

    /** The release on a landing near a flight's top, and the chains that hang from its end. */
    static void release(Scene s, int x0, int base, int dir, int len, int piers) {
        int t = len - 4, x = x0 + dir * t, y = base + rise(t) + 1;
        s.put(x, y, 1, Props.WINCH.get().of(0));
        s.part("release", x, y, 1);
        s.put(x, y, 2, M.bronze());
        int[][] chain = {{len - 1, -1, 15}, {len - 2, 2, 22}, {len - 2, 0, 9}, {len - 1, 1, 6}};
        for (int[] c : chain) {
            int y0 = base + rise(c[0]) - 3 - haunch(c[0], piers);
            for (int k = 0; k < c[2]; k++) s.put(x0 + dir * c[0], y0 - k, c[1], st(Blocks.IRON_CHAIN));
            if (c[2] > 10) s.put(x0 + dir * c[0], y0 - c[2], c[1], M.bronze());
        }
    }

    /**
     * The hall at the foot, its middle at x {@code gx - 42} as first built (so {@code gx} 0 is the Stair That Stops'
     * own). A podium, walls with relief, bronze doors between two giants, a roof with a parapet, and inside the plinth
     * with what they could not carry. {@code state}: 0 as the painting has it, 1 roofless and broken down, 2 whole.
     */
    static void hall(Scene s, int gx, int state) {
        M.mass(s, gx - 53, 1, -10, gx - 31, 2, 10);
        M.mass(s, gx - 51, 1, 11, gx - 46, 2, 12);                 // under the west giant
        M.mass(s, gx - 38, 1, 11, gx - 33, 2, 12);                 // under the east giant
        for (int x = gx - 45; x <= gx - 39; x++) {                 // the steps between them
            s.put(x, 2, 11, M.step(s, x, 11, Direction.NORTH));
            s.put(x, 1, 11, M.block());
            s.put(x, 1, 12, M.step(s, x, 12, Direction.NORTH));
        }
        M.shell(s, gx - 52, 3, -9, gx - 32, 14, 9);
        M.mass(s, gx - 52, 15, -9, gx - 32, 16, 9);
        air(s, gx - 51, 3, -8, gx - 33, 14, 8);
        for (int x = gx - 51; x <= gx - 33; x++) for (int z = -8; z <= 8; z++) s.put(x, 2, z, Math.abs(z) <= 1 || Math.floorMod(x - gx + 42, 6) == 0 ? M.mosaic() : M.flag());
        M.face(s, Direction.SOUTH, gx - 52, gx - 32, 9, 3, 15, 5);
        M.face(s, Direction.NORTH, gx - 52, gx - 32, -9, 3, 15, 5);
        M.face(s, Direction.WEST, -9, 9, gx - 52, 3, 15, 6);
        M.face(s, Direction.EAST, -9, 4, gx - 32, 3, 15, 6);       // the east face stops short: a ladder goes up the plain wall
        // the doors: three wide, six high, bronze, in a banded frame under a lintel and a seal
        for (int x = gx - 44; x <= gx - 40; x++)
            for (int y = 3; y <= 12; y++) {
                boolean opening = Math.abs(x - gx + 42) <= 1 && y <= 8;
                s.put(x, y, 10, opening ? AIR : y == 9 ? M.band() : y <= 8 ? M.bronzeBand() : (x == gx - 42 && y == 11) ? M.seal() : M.panel());
                if (opening) {
                    s.put(x, y, 9, M.door());
                    s.part("door", x, y, 9);
                } else if (y <= 8) s.put(x, y, 9, M.block());
            }
        s.put(gx - 45, 13, 10, stairT(M.blockStairs(), Direction.EAST));
        s.put(gx - 39, 13, 10, stairT(M.blockStairs(), Direction.WEST));
        M.colossus(s, gx - 48, 3, 12, Direction.SOUTH, 11, state == 2 ? 0 : 1);
        M.colossus(s, gx - 36, 3, 12, Direction.SOUTH, 11, state == 1 ? 2 : 0);
        // the roof: a parapet with merlons
        for (int x = gx - 52; x <= gx - 32; x++)
            for (int z = -9; z <= 9; z++) {
                if (x != gx - 52 && x != gx - 32 && z != -9 && z != 9) continue;
                if (x == gx - 32 && (Math.abs(z) <= 3 || z == 6)) continue;     // the stair leaves here; the ladder arrives at z 6
                s.put(x, 17, z, M.wall());
                if (Math.floorMod(x + z, 3) == 0) { s.put(x, 17, z, M.block()); s.put(x, 18, z, M.blockSlab()); }
            }
        if (state == 0) M.ruin(s, gx - 52, -9, gx - 32, 9, 17, 1, 18);
        if (state == 1) M.ruin(s, gx - 52, -9, gx - 32, 9, 11, 5, 18);     // the roof is in, the walls stand to a ragged line
        // inside: where they waited to go up
        for (int x = gx - 48; x <= gx - 36; x += 4) for (int z = -5; z <= 5; z += 10) M.column(s, x, z, 3, 14);
        Mon.prize(s, gx - 42, 3, -6, Direction.SOUTH);
        s.put(gx - 42, 14, -6, M.lamp(true));
        for (int[] k : new int[][] {{gx - 52, 5, -3}, {gx - 32, 5, 3}, {gx - 42, 5, -9}}) Mon.carving(s, k[0], k[1], k[2]);
        for (int z = -1; z <= 2; z += 3)                           // benches, facing the plinth
            for (int x = gx - 49; x <= gx - 35; x++)
                if (Math.abs(x - gx + 42) > 1 && Math.floorMod(x - gx + 49, 8) != 7) s.put(x, 3, z, stair(M.stairs(), Direction.SOUTH));
        int[][] left = {{-50, 7}, {-49, 7}, {-50, 6}, {-34, 7}, {-35, 7}, {-34, 5}, {-46, 7}, {-38, 6}};          // what they could not carry
        for (int i = 0; i < left.length; i++)
            s.put(gx + left[i][0], 3, left[i][1], i % 4 == 0 ? st(Blocks.BARREL) : i % 4 == 1 ? Props.BUNDLE.get().of(i & 1) : i % 4 == 2 ? Props.BASKET.get().of(1) : st(Blocks.CHEST));
        s.put(gx - 47, 9, 0, light(9));
        s.put(gx - 37, 9, 0, light(9));
        if (state == 2) for (int x = gx - 50; x <= gx - 34; x += 16) for (int z = -7; z <= 7; z += 14) M.lampPost(s, x, 3, z, 2, true);
        if (state != 1) for (int y = 3; y <= 16; y++) s.put(gx - 31, y, 6, ladder(Direction.EAST));
    }

    /** The terrace under a hall and the steps up to its doors; the way to them is kept open. */
    private static void ground(Scene s, int gx, int reach) {
        G.platform(s, G.box(gx - 56, -13, gx - 28, 15), 0, reach);
        int steps = G.stairs(s, gx - 42, 16, Direction.SOUTH, 3, 0);
        path(s, gx - 42, 16 + steps + 12, gx - 42, 16 + steps, 1, st(Blocks.COARSE_DIRT), st(Blocks.GRAVEL));
        for (int x = gx - 54; x <= gx - 30; x++) for (int z = 13; z <= 15; z++) if (!s.taken(x, 1, z)) s.put(x, 0, z, Math.floorMod(x + z, 4) == 0 ? M.block() : M.flag());
    }

    private static void finish(Scene s, int reach, int gx, int camX, int camZ) {
        M.finish(s, 9, 46);
        Mon.camp(s, camX, camZ);
        // the forecourt and the way the stair is first seen from stay open: nothing is planted across them
        for (int x = -reach; x <= reach; x++) for (int z = 13; z <= reach; z++) if (Math.abs(x - gx + 42) < 20 && !s.taken(x, s.g(x, z) + 1, z)) s.put(x, s.g(x, z) + 1, z, AIR);
        wild(s, 30, reach - 1, 26);
    }

    // ---- the five -----------------------------------------------------------------------------------------------------

    private static void first(Scene s) {
        s.clear(50);
        ground(s, 20, 50);
        hall(s, 20, 1);
        flight(s, -11, 16, 1, 30, 2, 99, 99, false, true);
        release(s, -11, 16, 1, 30, 2);
        for (int k = 2; k <= 3; k++) {                             // the piers that were never joined: they stand, broken, with nothing on them
            int px = -11 + pierAt(k), top = 16 + rise(pierAt(k)) - 3 - 6 * (k - 1) - Mon.pick(s, 30 + k, 5);
            M.pier(s, px, 0, 2, top, 5);
            M.ruin(s, px - 3, -3, px + 3, 3, top - 3, 3, top + 1);
        }
        M.heap(s, 30, 8, 3.6);
        M.heap(s, 40, -7, 3.0);
        finish(s, 50, 20, 4, 24);
        s.cam(-30, 5, 36, -150, -14).cam(10, 52, 44, 180, 36).cam(-22, 3, 24, 180, -10).cam(-22, 3, 5, 180, -4);
    }

    private static void fallen(Scene s) {
        s.clear(70);
        ground(s, 0, 70);
        hall(s, 0, 1);
        // every pier is a stump, and the stair lies beside them where it came down: its arches read lying on the ground
        for (int k = 0; k < 7; k++) {
            int px = -31 + pierAt(k), top = 5 + Mon.pick(s, 40 + k, 9);
            M.pier(s, px, 0, 2, top, Math.min(5, top - 1));
            M.ruin(s, px - 3, -3, px + 3, 3, top - 2, 2, top + 1);
        }
        for (int t = 0; t <= 86; t++) {
            int x = -31 + t, under = haunch(t, 7);
            if (n(s, t * 0.8, 91, 6.0) < 0.3) continue;            // it broke into lengths as it fell
            for (int d = 0; d <= 2 + under; d++) {
                int z = 8 + d, y = s.g(x, z) + 1;
                if (s.taken(x, y, z)) continue;
                s.put(x, y, z, d == 0 ? (rise(t) > rise(t - 1) ? M.flag() : M.block()) : M.course(d));
                if (d <= 1 || h(s, x, 3, z) < 150) s.put(x, y + 1, z, d == 0 ? M.block() : M.stone());
            }
            if (t % 12 == 9) for (int d = 3; d <= 9; d++) { int z = 8 + d, y = s.g(x, z) + 1; if (!s.taken(x, y, z)) s.put(x, y, z, M.block()); }
        }
        int rx = 49, rz = 7;                                       // the release came down with the last landing
        s.put(rx, s.g(rx, rz) + 1, rz, Props.WINCH.get().of(0));
        s.part("release", rx, s.g(rx, rz) + 1, rz);
        for (int k = 0; k < 9; k++) s.put(57 + (k > 5 ? 1 : 0), s.g(57 + (k > 5 ? 1 : 0), 9 + k) + 1, 9 + k, st(Blocks.IRON_CHAIN).setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.Z));
        M.heap(s, 21, 4, 4.2);
        M.heap(s, 56, 4, 3.6);
        M.heap(s, 2, -8, 3.0);
        finish(s, 70, 0, -16, 32);
        s.cam(-52, 8, 40, -125, -4).cam(8, 46, 58, 180, 40).cam(-42, 3, 24, 180, -10).cam(20, 4, 24, 180, 8);
    }

    private static void stops(Scene s) {
        s.clear(70);
        ground(s, 0, 70);
        hall(s, 0, 0);
        flight(s, -31, 16, 1, 86, 7, 50, 53, true, true);
        release(s, -31, 16, 1, 86, 7);
        M.heap(s, 21, 9, 4.6);
        M.heap(s, 18, -8, 3.4);
        M.heap(s, 56, 6, 4.2);
        M.heap(s, 46, -10, 3.0);
        M.heap(s, -24, -13, 2.6);
        finish(s, 70, 0, -16, 22);
        s.cam(-46, 6, 32, -128, -22).cam(8, 84, 62, 180, 38).cam(-42, 3, 24, 180, -10).cam(-42, 3, 5, 180, -4);
    }

    private static void twin(Scene s) {
        // two stairs that were to meet in the air and never did; the hall stands between their feet
        s.clear(64);
        ground(s, 42, 64);
        hall(s, 42, 0);
        for (int dir = -1; dir <= 1; dir += 2) {
            int x0 = -dir * 58;
            // a gate at the foot of each: a block with an arch through it, and the first tread on its roof
            M.mass(s, x0 - 3, 1, -4, x0 + 3, 8, 4);
            air(s, x0 - 3, 1, -1, x0 + 3, 5, 1);
            G.foot(s, x0 - 3, -4, x0 + 3, 4, 1);
            for (int z = -4; z <= 4; z++) s.put(x0 - dir * 3, 9, z, Math.floorMod(z, 2) == 0 ? M.block() : M.wall());
            flight(s, x0, 8, dir, 50, 4, 99, 99, false, true);
            for (int y = 1; y <= 8; y++) s.put(x0 - dir * 4, y, 3, ladder(dir > 0 ? Direction.WEST : Direction.EAST));
        }
        release(s, -58, 8, 1, 50, 4);
        M.heap(s, 0, 9, 5.0);
        M.heap(s, -6, -10, 3.6);
        M.heap(s, 7, -8, 3.2);
        finish(s, 64, 42, 28, 22);
        s.cam(-30, 6, 50, -160, -24).cam(0, 70, 62, 180, 40).cam(0, 3, 24, 180, -10).cam(0, 3, 5, 180, -4);
    }

    private static void whole(Scene s) {
        s.clear(70);
        ground(s, 0, 70);
        hall(s, 0, 2);
        int top = flight(s, -31, 16, 1, 82, 7, 99, 99, false, false);
        // where they stepped off: a round landing on the last pier, four columns and a lamp
        M.pier(s, 55, 0, 3, top - 1, 5);
        disc(s, 55, 0, 5.3, top, M.flag());
        ring(s, 55, 0, 5.0, top + 1, M.wall());
        for (int q = 0; q < 4; q++) M.column(s, 55 + DX[q] * 3 + DZ[q] * 3, DZ[q] * 3 - DX[q] * 3, top + 1, top + 5);
        disc(s, 55, 0, 4.6, top + 6, M.block());
        disc(s, 55, 0, 2.6, top + 7, M.bronze());
        s.put(55, top + 8, 0, M.lamp(true));
        for (int z = -2; z <= 2; z++) s.put(50, top + 1, z, AIR);
        release(s, -31, 16, 1, 80, 7);
        for (int k = 0; k < 7; k++) M.lampPost(s, -31 + pierAt(k), 16 + rise(pierAt(k)) + 1, -2, 2, true);
        finish(s, 70, 0, -16, 22);
        s.cam(-46, 6, 32, -128, -24).cam(8, 96, 62, 180, 40).cam(-42, 3, 24, 180, -10).cam(52, top + 2, 0, 90, 20);
    }

    static void init() {
        Object[][] five = {
                {1, "stair_first", "The First Flight", (dev.awaken.finds.Kind.Maker) MStair::first, 50,
                        "A roofless hall with bronze doors, and a stair on two arches climbing out of it to nothing. Beyond it stand piers that were never joined.",
                        "You climbed to the last landing and let the counterweight go. The doors of the hall came open.",
                        "It says: this is where we tried it first, to see whether stone could be made to climb. It could. We went and built the others.",
                        "A mason's sum, cut and cut again. So many stones a day, so many days. At the bottom: not in my lifetime. Build it anyway.",
                        "It says the ones who began this never saw one finished. They knew that when they started."},
                {2, "stair_fallen", "The Fallen Stair", (dev.awaken.finds.Kind.Maker) MStair::fallen, 70,
                        "A row of broken piers marching away from a ruined hall, and beside them, in the grass, the whole stair lying where it fell: arch after arch, flat on the ground.",
                        "The winch lay at the far end with the last landing. You let it go where it lay, and back at the hall the doors came open.",
                        "It says nobody pulled this one down. It was left, and a hundred winters did the rest.",
                        "Names, in two columns. The left is headed: went up. The right is headed: stayed. The right column is much the longer.",
                        "It says: we who stayed came here on the day each year, and looked up, for as long as there were any of us who remembered which day."},
                {3, "stair_that_stops", "The Stair That Stops", (dev.awaken.finds.Kind.Maker) MStair::stops, 70,
                        "A stair on stone arches that climbs out of the trees into the sky and stops in mid-air, chains hanging from the last step. At its foot, a hall with bronze doors shut tight between two worn giants.",
                        "You climbed to the last span and let the counterweight go. The doors came open, and what the leavers could not carry was still on its plinth.",
                        "They cut this where the ones staying behind would see it. It says: we built the stair to come back down. We did not finish it.",
                        "A list of what they could not carry up. Beds. Looms. The names of rivers. It goes on round the corner and stops in the middle of a word.",
                        "This one is cut deeper than the rest. It says: if you can read this, the way up is closed. Go and find the others. Some went down. Some went to sleep."},
                {4, "stair_twin", "The Stairs That Never Met", (dev.awaken.finds.Kind.Maker) MStair::twin, 64,
                        "Two stairs rise toward each other from either end of a field and stop a short jump apart, high over a hall with bronze doors. From below they look like one arch with its crown knocked out.",
                        "You climbed the western stair to where it ends and let its counterweight go. Down below, the hall's doors came open.",
                        "It says two towns built this, one from each side, to go up together. They quarrelled over who would set the last stone.",
                        "It says: the gap is nine stones. We measured it every year. Nobody would set them.",
                        "Cut small, low down, where a child could reach. It says: I jumped it once. Do not tell them."},
                {5, "stair_whole", "The Stair That Goes On", (dev.awaken.finds.Kind.Maker) MStair::whole, 70,
                        "A stair on seven arches, whole, with a lamp burning on every landing and a round porch of columns at the very top. Nothing is broken. Nobody is on it.",
                        "You climbed all of it. At the top there was only the porch, the wind and the winch. You let the counterweight go, and far below the doors opened.",
                        "It says: this is the one we finished. Everyone who was going went up it in a single day. It took from dawn until the lamps were lit.",
                        "It says: at the top you step off. That is all there is to it. Nobody who did it came back to say what is under your foot.",
                        "The last cut, by the porch. It says: the lamps are for the ones who change their minds. Keep them filled. Someone may come down."}};
        for (Object[] v : five) {
            int tier = (Integer) v[0];
            Mon.kind(Family.STAIR, tier, (String) v[1], (String) v[2]).verb(Verb.REACH)
                    .lands(Group.MOUNTAIN, Group.SNOW, Group.FOREST, Group.PLAINS).slope(20)
                    .see((String) v[5]).learned((String) v[6]).reach((Integer) v[4])
                    .air(Air.of(0xC9D6E2, 140).mote(() -> ParticleTypes.WHITE_ASH, 0.25F, 14, -0.01F).loop(() -> SoundEvents.ELYTRA_FLYING, 240, 0.07F, 0.7F))
                    .scene((dev.awaken.finds.Kind.Maker) v[3])
                    .script(() -> Lore.with(Steps.of(
                                    Prize.at("release").then((c, i) -> {
                                        Mon.open(c, "door", SoundEvents.IRON_DOOR_OPEN);
                                        c.sound(c.pos("release", 0), SoundEvents.CHAIN_BREAK, 2.0F, 0.6F);
                                    }),
                                    Prize.at("prize")),
                            "carving", (String) v[7], (String) v[8], (String) v[9]))
                    .play(t -> t.use("carving", 0).use("release", 0).use("prize", 0))
                    .add();
        }
    }
}
