package dev.awaken.finds.sets;

import dev.awaken.finds.Air;
import dev.awaken.finds.Kind;
import dev.awaken.finds.Kind.Anchor;
import dev.awaken.finds.Kind.FSet;
import dev.awaken.finds.Kind.Made;
import dev.awaken.finds.Kind.Verb;
import dev.awaken.finds.Relics;
import dev.awaken.finds.Scene;
import dev.awaken.finds.kit.M;
import dev.awaken.finds.kit.Olden;
import dev.awaken.finds.kit.Props;
import dev.awaken.finds.verb.*;
import dev.awaken.living.region.RegionMap.Group;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.finds.kit.B.*;

/**
 * The monuments of the old world (SPEC §61, docs/places/MONUMENTS.md): what the Precursors left, a little broken down,
 * each with a relic, something to do and carvings the System reads. Built from olden stone with the monument kit.
 */
final class Monuments {
    private Monuments() {}

    // ---- A. The Stair That Stops ------------------------------------------------------------------------------------

    /** The piers' middles, west to east; a landing four long sits on each. */
    private static final int[] PIER = {-22, -10, 2, 14, 26, 38, 50};

    /** The height of the stair's tread at x: the gatehouse roof (16) to its east wall, then eight steps and a landing of four, seven times. */
    private static int tread(int x) {
        if (x < -31) return 16;
        return 16 + 8 * Math.floorDiv(x + 31, 12) + Math.min(8, Math.floorMod(x + 31, 12) + 1);
    }

    /** How deep the masonry under the deck is at x: thin at an arch's crown, deep at its haunches; 0 over a pier or the gatehouse. */
    private static int haunch(int x) {
        int a = -32, b = 99;
        for (int p : PIER) {
            if (x >= p - 2 && x <= p + 2) return 0;
            if (p + 2 < x) a = p + 2;
            if (p - 2 > x && b == 99) b = p - 2;
        }
        if (b == 99) return 2;                                 // past the last pier: a stub
        double t = (x - a) / (double) (b - a);
        return (int) Math.round(6 * (1 - Math.sqrt(Math.max(0, 1 - (2 * t - 1) * (2 * t - 1)))));
    }

    private static void stairThatStops(Scene s) {
        s.level(-40, 2, 18, 4, 0).clear(36);
        // -- the gatehouse: a podium two courses high, walls to 15, a roof two courses thick whose top is where the stair begins
        M.mass(s, -53, 1, -10, -31, 2, 10);
        M.mass(s, -51, 1, 11, -46, 2, 12);                     // under the west giant
        M.mass(s, -38, 1, 11, -33, 2, 12);                     // under the east giant
        for (int x = -45; x <= -39; x++) {                     // the steps between them
            s.put(x, 2, 11, M.step(s, x, 11, Direction.NORTH));
            s.put(x, 1, 11, M.block());
            s.put(x, 1, 12, M.step(s, x, 12, Direction.NORTH));
        }
        M.shell(s, -52, 3, -9, -32, 14, 9);
        M.mass(s, -52, 15, -9, -32, 16, 9);
        air(s, -51, 3, -8, -33, 14, 8);
        for (int x = -51; x <= -33; x++) for (int z = -8; z <= 8; z++) s.put(x, 2, z, Math.abs(z) <= 1 || (x + 42) % 6 == 0 ? M.mosaic() : M.flag());
        M.face(s, Direction.SOUTH, -52, -32, 9, 3, 15, 5);
        M.face(s, Direction.NORTH, -52, -32, -9, 3, 15, 5);
        M.face(s, Direction.WEST, -9, 9, -52, 3, 15, 6);
        M.face(s, Direction.EAST, -9, 4, -32, 3, 15, 6);       // the east face stops short: the climbers' ladder goes up the plain wall
        // the doors: three wide, six high, bronze, in a banded frame under a lintel and a seal
        for (int x = -44; x <= -40; x++)
            for (int y = 3; y <= 12; y++) {
                boolean opening = Math.abs(x + 42) <= 1 && y <= 8;
                s.put(x, y, 10, opening ? AIR : y == 9 ? M.band() : y <= 8 ? M.bronzeBand() : (x == -42 && y == 11) ? M.seal() : M.panel());
                if (opening) {
                    s.put(x, y, 9, M.door());
                    s.part("door", x, y, 9);
                } else if (y <= 8) s.put(x, y, 9, M.block());
            }
        s.put(-45, 13, 10, stairT(M.blockStairs(), Direction.EAST));
        s.put(-39, 13, 10, stairT(M.blockStairs(), Direction.WEST));
        M.colossus(s, -48, 3, 12, Direction.SOUTH, 11, 1);
        M.colossus(s, -36, 3, 12, Direction.SOUTH, 11, 0);
        // the roof: a parapet with merlons, most of it gone
        for (int x = -52; x <= -32; x++)
            for (int z = -9; z <= 9; z++) {
                if (x != -52 && x != -32 && z != -9 && z != 9) continue;
                if (x == -32 && (Math.abs(z) <= 3 || z == 6)) continue;     // the stair leaves here; the ladder arrives at z 6
                s.put(x, 17, z, M.wall());
                if ((x + z) % 3 == 0) { s.put(x, 17, z, M.block()); s.put(x, 18, z, M.blockSlab()); }
            }
        M.ruin(s, -52, -9, -32, 9, 17, 1, 18);
        // inside: the hall where they waited to go up
        for (int x = -48; x <= -36; x += 4) for (int z = -5; z <= 5; z += 10) M.column(s, x, z, 3, 14);
        s.put(-42, 3, -6, M.block());
        s.put(-42, 4, -6, M.seal());
        s.part("prize", -42, 4, -6);
        s.put(-43, 3, -6, stair(M.blockStairs(), Direction.EAST));
        s.put(-41, 3, -6, stair(M.blockStairs(), Direction.WEST));
        s.put(-42, 3, -5, stair(M.blockStairs(), Direction.NORTH));
        s.put(-42, 14, -6, M.lamp(true));
        for (int[] k : new int[][] {{-52, 5, -3}, {-32, 5, 3}, {-42, 5, -9}}) {
            s.put(k[0], k[1], k[2], M.glyph());
            s.put(k[0], k[1] + 1, k[2], M.glyph());
            s.part("carving", k[0], k[1], k[2]);
        }
        for (int z = -1; z <= 2; z += 3)                           // benches, facing the plinth
            for (int x = -49; x <= -35; x++)
                if (x != -42 && x != -43 && x != -41 && (x + 49) % 8 != 7) s.put(x, 3, z, stair(M.stairs(), Direction.SOUTH));
        int[][] left = {{-50, 7}, {-49, 7}, {-50, 6}, {-34, 7}, {-35, 7}, {-34, 5}, {-46, 7}, {-38, 6}};          // what they could not carry
        for (int i = 0; i < left.length; i++)
            s.put(left[i][0], 3, left[i][1], i % 4 == 0 ? st(Blocks.BARREL) : i % 4 == 1 ? Props.BUNDLE.get().of(i & 1) : i % 4 == 2 ? Props.BASKET.get().of(1) : st(Blocks.CHEST));
        s.put(-50, 4, 7, Props.BUNDLE.get().of(0));
        s.put(-47, 9, 0, light(9));
        s.put(-37, 9, 0, light(9));
        // -- the stair: a deck seven wide on rampant arches, parapets, a corbel course under the edge
        for (int x = -31; x <= 55; x++) {
            int h = tread(x), under = haunch(x), wide = x <= 52 ? 3 : x == 53 ? 2 : x == 54 ? 1 : 0;
            boolean rises = h > tread(x - 1), gap = x >= 19 && x <= 22;
            if (gap) continue;                                 // the span that fell; the climbers' planks go in after
            for (int z = -wide; z <= wide; z++) {
                boolean edge = Math.abs(z) == 3;
                if (edge) {
                    s.put(x, h, z, M.block());
                    s.put(x, h - 1, z, M.course(h - 1));
                    s.put(x, h - 2, z, stairT(M.blockStairs(), z > 0 ? Direction.NORTH : Direction.SOUTH));
                    if (n(s, x * 0.9, z * 7.0, 3.0) > 0.34) s.put(x, h + 1, z, x % 4 == 0 ? M.block() : M.wall());
                    if (x % 4 == 0 && n(s, x * 0.9, z * 7.0, 3.0) > 0.5) s.put(x, h + 2, z, M.blockSlab());
                } else {
                    s.put(x, h, z, rises ? M.step(s, x, z, Direction.EAST) : M.flag());
                    for (int y = h - 2 - under; y <= h - 1; y++) s.put(x, y, z, M.course(y));
                }
            }
        }
        for (int i = 0; i < PIER.length; i++) {
            int px = PIER[i], top = tread(px) - 3;
            M.pier(s, px, 0, 2, top, 5);
            for (int side = -1; side <= 1; side += 2) {           // a pilaster strip up the north and south faces, stepped back at the top
                for (int y = 7; y <= top - 4; y++) s.put(px, y, side * 3, y % 10 == 0 ? M.band() : M.stone());
                s.put(px, top - 3, side * 3, stair(M.blockStairs(), side > 0 ? Direction.NORTH : Direction.SOUTH));
                if (top > 22) for (int y = 12; y <= 14; y++) s.put(px, y, side * 2, M.panel());
            }
        }
        // the planks the climbers laid over the fallen span, two wide, a board under each
        for (int x = 19; x <= 22; x++)
            for (int z = -1; z <= 0; z++) {
                s.put(x, tread(x), z, stair(st(Blocks.SPRUCE_STAIRS), Direction.EAST));
                s.put(x, tread(x) - 1, z, st(Blocks.SPRUCE_PLANKS));
            }
        // the end: chains from the last span, a weight still on one
        int[][] chain = {{54, -1, 15}, {53, 2, 22}, {53, 0, 9}, {54, 1, 6}};
        for (int[] c : chain) {
            int y0 = tread(c[0]) - 3 - haunch(c[0]);
            for (int k = 0; k < c[2]; k++) s.put(c[0], y0 - k, c[1], st(Blocks.IRON_CHAIN));
            if (c[2] > 10) s.put(c[0], y0 - c[2], c[1], M.bronze());
        }
        // the release, on the top landing
        s.put(51, tread(51) + 1, 1, Props.WINCH.get().of(0));
        s.part("release", 51, tread(51) + 1, 1);
        s.put(51, tread(51) + 1, 2, M.bronze());
        M.age(s, 9, 46);
        // -- what later people left: a ladder up the gatehouse, a camp by the first pier
        for (int y = 3; y <= 16; y++) s.put(-31, y, 6, ladder(Direction.EAST));
        BlockState cloth = st(Blocks.WOOL.pick(DyeColor.LIGHT_GRAY));
        for (int z = 12; z <= 15; z++) {                           // the tent
            s.put(-29, 1, z, cloth);
            s.put(-25, 1, z, cloth);
            s.put(-28, 2, z, cloth);
            s.put(-26, 2, z, cloth);
            s.put(-27, 3, z, cloth);
            s.put(-29, 2, z, st(Blocks.WOOL_SLAB.pick(DyeColor.LIGHT_GRAY)));
            s.put(-25, 2, z, st(Blocks.WOOL_SLAB.pick(DyeColor.LIGHT_GRAY)));
        }
        s.put(-27, 1, 13, st(Blocks.CARPET.pick(DyeColor.RED)));
        s.put(-27, 1, 14, st(Blocks.CARPET.pick(DyeColor.BROWN)));
        s.put(-32, 0, 15, st(Blocks.COBBLESTONE));
        s.put(-32, 1, 15, st(Blocks.CAMPFIRE).setValue(CampfireBlock.LIT, false));
        s.put(-34, 1, 16, stair(st(Blocks.SPRUCE_STAIRS), Direction.EAST));
        s.put(-30, 1, 17, Props.BUNDLE.get().of(1));
        s.put(-31, 1, 12, Props.ROPE_COIL.get().of(0));
        stack(s, -24, 1, 11, 4, false);
        lamp(s, -30, 1, 11, 2, st(Blocks.SPRUCE_FENCE), false);
        // -- what fell: heaps at the foot of the gap and under the end
        M.heap(s, 21, 9, 4.6);
        M.heap(s, 18, -8, 3.4);
        M.heap(s, 56, 6, 4.2);
        M.heap(s, 46, -10, 3.0);
        M.heap(s, -24, -13, 2.6);
        path(s, -42, 30, -42, 14, 1, st(Blocks.COARSE_DIRT), st(Blocks.GRAVEL));
        wild(s, 26, 60, 22);
        s.cam(-58, 5, 34, -135, -16).cam(8, 84, 62, 180, 38).cam(-42, 3, 24, 180, -10).cam(-42, 3, 5, 180, -4);
    }

    // ---- B. The Listening Dish --------------------------------------------------------------------------------------

    private static final int DISH = 30;

    /** How far a column lies toward the dish's high side (the north-east). */
    private static double lean(int x, int z) {
        return (x - z) / Math.sqrt(2);
    }

    /** The height of the bowl's skin at a column: a rim plane tipped to the north-east, less the bowl's depth. */
    private static int bowl(int x, int z) {
        return (int) Math.round(13 + 0.4 * lean(x, z) - 9 * (1 - (x * x + z * z) / (double) (DISH * DISH)));
    }

    private static void listeningDish(Scene s) {
        s.level(36, 6).clear(40);
        // -- the dish: a skin three thick, bronze rings, a lip; solid where it rests on the ground
        for (int x = -31; x <= 31; x++)
            for (int z = -31; z <= 31; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r > 30.6) continue;
                int top = Math.max(1, bowl(x, z));
                boolean rim = r > 28.4;
                BlockState skin = rim ? M.block() : Math.abs(r - 10) < 0.7 || Math.abs(r - 20) < 0.7 ? M.bronze() : r < 5.2 ? M.mosaic() : M.flag();
                for (int y = top - 3 <= 3 ? 1 : top - 2; y < top; y++) s.put(x, y, z, M.course(y));
                s.put(x, top, z, skin);
                if (rim) {
                    s.put(x, top + 1, z, r > 29.5 ? M.bronzeBand() : M.block());
                    if (r > 29.5 && Math.floorMod(x + z, 5) == 0) s.put(x, top + 2, z, M.blockSlab());
                }
            }
        // -- what carries the high side: a drum, and twelve fins out to the rim with a way through each
        for (int x = -20; x <= 20; x++)
            for (int z = -20; z <= 20; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r < 17.6 || r >= 19.4) continue;
                for (int y = 1; y <= bowl(x, z) - 3; y++) s.put(x, y, z, y % 10 == 0 ? M.band() : M.course(y));
            }
        for (int k = 0; k < 12; k++) {
            double a = Math.toRadians(k * 30 + 15);
            for (int r = 20; r <= 29; r++) {
                int x = (int) Math.round(Math.cos(a) * r), z = (int) Math.round(Math.sin(a) * r), top = bowl(x, z) - 3;
                boolean way = r >= 23 && r <= 25 && top > 8;
                for (int y = 1; y <= top; y++) {
                    if (way && y <= 4) continue;
                    s.put(x, y, z, way && y == 5 ? M.band() : y % 10 == 0 ? M.band() : M.course(y));
                }
                if (r == 29 && top > 4) s.put(x, top + 1, z, M.block());
            }
        }
        // -- the third of the rim that fell, between west and north: a ragged bite out of the dish, stumps of what carried it
        for (int x = -31; x <= 0; x++)
            for (int z = -31; z <= 0; z++) {
                double r = Math.sqrt(x * x + z * z), th = Math.toDegrees(Math.atan2(z, x));
                if (th > -100 || th < -170) continue;
                double edge = 16 + n(s, x * 1.5, z * 1.5, 4.0) * 9;
                if (r < edge) continue;
                int stump = (int) (n(s, x * 2.1 + 9, z * 2.1, 2.5) * 7) - 1;
                for (int y = Math.max(1, stump + 1); y <= 34; y++) {
                    var q = s.rel(x, y, z);
                    s.blocks().remove(net.minecraft.core.BlockPos.asLong(q.getX(), q.getY(), q.getZ()));
                }
            }
        // -- the pylon: a plinth, a base with the vault in it, a shaft that leans to the high side and is broken off
        disc(s, 0, 0, 4.3, 5, M.block());
        M.mass(s, -2, 6, -2, 2, 10, 2);
        air(s, -1, 6, -1, 1, 8, 1);
        for (int x = -1; x <= 1; x++) for (int y = 6; y <= 9; y++) s.put(x, y, 3, y == 9 ? M.band() : x == 0 ? AIR : M.bronzeBand());
        for (int y = 6; y <= 7; y++) {
            s.put(0, y, 2, M.door());
            s.part("door", 0, y, 2);
        }
        s.put(0, 8, 2, M.band());
        s.put(0, 6, -1, M.seal());
        s.part("prize", 0, 6, -1);
        s.put(0, 9, 0, M.lamp(true));
        for (int[] k : new int[][] {{2, 0}, {-2, 0}, {0, -2}}) {
            s.put(k[0], 7, k[1], M.glyph());
            s.part("carving", k[0], 7, k[1]);
        }
        for (int y = 11; y <= 27; y++) {
            int hs = y < 14 ? 2 : 1, o = (y - 11) / 6;
            for (int x = -hs; x <= hs; x++)
                for (int z = -hs; z <= hs; z++) s.put(o + x, y, -o + z, y == 14 || y == 21 ? M.bronzeBand() : M.course(y));
            if (y == 18) {
                s.put(o, y, -o, M.lamp(true));
                for (int d = 0; d < 4; d++) s.put(o + DX[d], y, -o + DZ[d], AIR);
            }
        }
        M.ruin(s, -2, -7, 7, 2, 25, 2, 28);
        // -- the sockets in the low rim, where three banded stones fell out
        for (int[] k : new int[][] {{-21, 21}, {-13, 27}, {-27, 13}}) {
            int y = Math.max(1, bowl(k[0], k[1])) + 1;
            s.put(k[0], y, k[1], M.rubble());
            s.part("socket", k[0], y, k[1]);
        }
        M.age(s, 6, 40);
        // -- what fell
        M.heap(s, -22, -20, 4.6);
        M.heap(s, -14, -27, 3.8);
        M.heap(s, -28, -11, 3.6);
        M.heap(s, -15, -13, 3.0);
        M.heap(s, -31, -25, 2.8);
        // -- the herders: lean-tos under the high rim, a hearth with one of the fallen stones for its back
        for (int[] k : new int[][] {{19, -13}, {10, -22}}) {
            for (int x = 0; x <= 3; x += 3) for (int y = 1; y <= 2; y++) s.put(k[0] + x, y, k[1] + 2, st(Blocks.SPRUCE_FENCE));
            for (int x = -1; x <= 4; x++) for (int z = 0; z <= 2; z++) s.put(k[0] + x, z == 2 ? 3 : 2 + (z == 1 ? 1 : 0), k[1] + z, z == 1 ? top(st(Blocks.SPRUCE_SLAB)) : st(Blocks.SPRUCE_SLAB));
            for (int x = -1; x <= 4; x += 5) s.put(k[0] + x, 1, k[1], st(Blocks.SPRUCE_PLANKS));
            s.put(k[0] + 1, 1, k[1] + 1, st(Blocks.HAY_BLOCK));
            s.put(k[0] + 2, 1, k[1] + 1, st(Blocks.CARPET.pick(DyeColor.BROWN)));
            s.put(k[0] + 3, 1, k[1] + 3, st(Blocks.BARREL));
        }
        s.put(15, 0, -16, st(Blocks.COBBLESTONE));
        s.put(15, 1, -16, st(Blocks.CAMPFIRE).setValue(CampfireBlock.LIT, false));
        int[][] loose = {{16, -17}, {-33, -14}, {6, 34}};
        for (int[] k : loose) {
            s.put(k[0], 1, k[1], M.bronzeBand());
            s.part("loose", k[0], 1, k[1]);
        }
        path(s, 0, 42, -12, 31, 1, st(Blocks.COARSE_DIRT), st(Blocks.GRAVEL));
        wild(s, 33, 42, 12);
        s.cam(-42, 7, 42, -135, -6).cam(34, 52, 44, 140, 40).cam(0, 4, 14, 180, -8).cam(13, 2, -9, -150, -18);
    }

    // ---- C. The Sleepers' Hall --------------------------------------------------------------------------------------

    private static void sleepersHall(Scene s) {
        s.level(40, 4).clear(42);
        BlockState root = st(Blocks.DARK_OAK_WOOD);
        // -- the hall under the ground: floor at -10, aisles ceiled at -4, the nave open to -2 under one course of vault
        M.mass(s, -31, -11, -11, 31, -1, 11);
        air(s, -30, -9, -3, 30, -2, 3);                          // the nave
        air(s, -30, -9, -10, 30, -5, -5);                        // the north aisle
        air(s, -30, -9, 5, 30, -5, 10);                          // the south aisle
        air(s, -30, -9, -4, 30, -6, -4);                         // under the arcades
        air(s, -30, -9, 4, 30, -6, 4);
        for (int x = -30; x <= 30; x++)
            for (int z = -10; z <= 10; z++) s.put(x, -10, z, Math.abs(z) <= 2 ? M.mosaic() : (x + z) % 7 == 0 ? M.block() : M.flag());
        for (int x = -27; x <= 27; x += 6)
            for (int z = -4; z <= 4; z += 8) M.column(s, x, z, -9, -6);
        for (int x = -30; x <= 30; x++) for (int z = -4; z <= 4; z += 8) s.put(x, -5, z, Math.floorMod(x + 27, 6) == 0 ? M.block() : M.band());
        // -- the vault fell in down the middle: a ragged slot open to the sky, what fell lying under it
        for (int x = -24; x <= 24; x++)
            for (int z = -3; z <= 3; z++) {
                double edge = 1.2 + n(s, x * 1.3, 40, 3.5) * 2.6 - Math.max(0, (Math.abs(x) - 18) * 0.5);
                if (Math.abs(z) > edge) continue;
                s.put(x, -1, z, AIR);
                s.put(x, 0, z, AIR);
                if (h(s, x, 3, z) < 30) s.put(x, -9, z, h(s, x, 4, z) < 128 ? M.rubble() : st(Olden.WORN.get()));
                else if (h(s, x, 3, z) < 44) { s.put(x, -10, z, WATER); s.put(x, -11, z, M.block()); }
            }
        // -- the way down: a stair in a cut from the west, a portal, the first carving beside it
        for (int i = 0; i <= 9; i++) {
            int x = -41 + i;
            for (int z = -3; z <= 3; z++) for (int y = -i - 1; y <= 0; y++) s.put(x, y, z, Math.abs(z) == 3 || y == -i - 1 ? M.course(y) : AIR);
            for (int z = -2; z <= 2; z++) s.put(x, -i, z, M.step(s, x, z, Direction.WEST));
            for (int z = -3; z <= 3; z += 6) if (i % 3 == 0) s.put(x, 1, z, M.block());
        }
        for (int z = -2; z <= 2; z++) for (int y = -9; y <= -5; y++) s.put(-31, y, z, Math.abs(z) == 2 ? M.bronzeBand() : y == -5 ? M.band() : AIR);
        for (int z = -2; z <= 2; z++) s.put(-31, -4, z, z == 0 ? M.seal() : M.panel());
        s.put(-30, -8, -3, M.glyph());
        s.part("carving", -30, -8, -3);
        // -- the beds: sixteen along the outer walls, a stone sleeper on each under a canopy
        for (int i = 0; i < 16; i++) {
            int x = -21 + (i / 2) * 6, side = i % 2 == 0 ? -1 : 1, z = side * 9;
            for (int dx = -1; dx <= 1; dx++) s.put(x + dx, -9, z, M.block());
            s.put(x - 1, -8, z, stair(M.blockStairs(), Direction.WEST));     // the head, on its pillow
            s.put(x, -8, z, M.blockSlab());
            s.put(x + 1, -8, z, M.blockSlab());
            for (int dx = -2; dx <= 2; dx += 4) { s.put(x + dx, -9, z, M.wall()); s.put(x + dx, -8, z, M.wall()); s.put(x + dx, -7, z, M.wall()); }
            for (int dx = -2; dx <= 2; dx++) s.put(x + dx, -6, z, dx == 0 ? M.band() : M.blockSlab());
            s.put(x, -8, side * 10, M.panel());
            if (i % 3 == 0) s.put(x + 2, -9, side * 8, candles(1 + i % 3, true));
        }
        // -- the lamps: eight on posts at the aisles' edge, to be lit near to far, left and then right
        for (int i = 0; i < 8; i++) {
            int x = -18 + (i / 2) * 12, z = i % 2 == 0 ? -6 : 6;
            s.put(x, -9, z, M.wall());
            s.put(x, -8, z, M.lamp(false));
            s.part("lamp", x, -8, z);
        }
        // -- the round door at the far end, and past it the one empty bed
        air(s, 32, -9, -3, 38, -4, 3);
        for (int x = 32; x <= 38; x++) for (int z = -3; z <= 3; z++) s.put(x, -10, z, M.mosaic());
        M.shell(s, 31, -10, -4, 39, -3, 4);
        M.mass(s, 31, -3, -4, 39, -2, 4);
        for (int y = -9; y <= -4; y++)
            for (int z = -3; z <= 3; z++) {
                double d = Math.sqrt((y + 6.5) * (y + 6.5) + z * z);
                if (d > 3.1) continue;
                s.put(31, y, z, d > 2.2 ? M.seal() : d < 1.2 ? M.seal() : M.bronze());
                s.part("door", 31, y, z);
            }
        for (int dx = -1; dx <= 1; dx++) s.put(36 + dx, -9, 0, M.block());
        s.put(36, -8, 0, M.seal());
        s.part("prize", 36, -8, 0);
        s.put(36, -4, 0, M.lamp(true));
        s.put(38, -8, 2, M.glyph());
        s.part("carving", 38, -8, 2);
        s.put(12, -8, -11, M.glyph());
        s.part("carving", 12, -8, -11);
        M.age(s, -3, 36);
        // -- growth: roots through the roof and down the columns, lichen in the walls, pilgrims' candles
        int[][] roots = {{-20, -2, -16, -4}, {-9, 2, -3, 4}, {4, -1, 9, -4}, {15, 2, 21, 4}, {-2, 1, -3, -4}, {22, -2, 27, -4}};
        for (int[] k : roots) {
            limb(s, k[0], 1, k[1], k[0], -1, k[1], root);
            limb(s, k[0], -1, k[1], k[2], -5, k[3], root);
            limb(s, k[2], -5, k[3], k[2] + (k[3] > 0 ? 1 : -1), -9, k[3] + (k[3] > 0 ? 1 : -1), root);
            tree(s, k[0], k[1] + (k[1] > 0 ? 3 : -3), 1, 7, st(Blocks.DARK_OAK_LOG), leaf(Blocks.DARK_OAK_LEAVES), 3.0);
            limb(s, k[0], 1, k[1], k[0], 1, k[1] + (k[1] > 0 ? 3 : -3), root);
        }
        for (int x = -30; x <= 30; x++)
            for (int y = -9; y <= -5; y++)
                for (int z = -11; z <= 11; z += 22)
                    if (n(s, x * 1.1 + y, z + y * 2.0, 3.0) > 0.66 && Olden.is(s.get(x, y, z) == null ? AIR : s.get(x, y, z)) && s.get(x, y, z).isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO)
                            && s.partAt(s.rel(x, y, z)) == null) s.put(x, y, z, M.lichen());
        for (int[] k : new int[][] {{27, -9, -2}, {28, -9, 2}, {29, -9, -1}, {-28, -9, 2}, {0, -9, -6}, {24, -9, 6}}) s.put(k[0], k[1], k[2], candles(1 + (k[0] & 1) + (k[2] & 1), true));
        stilled(s, 26, -9, -1, 1, Direction.EAST);
        stilled(s, 27, -9, 1, 3, Direction.EAST);
        s.put(-12, -6, 0, light(8));
        s.put(12, -6, 0, light(8));
        path(s, -41, 16, -41, 4, 1, st(Blocks.COARSE_DIRT), st(Blocks.PODZOL));
        wild(s, 30, 43, 14);
        s.cam(-10, 14, 40, 170, 24).cam(26, 36, 30, 140, 46).cam(-44, 2, 0, -90, 22).cam(-26, -9, 0, -90, -6);
    }

    static void init() {
        Kind.of(FSet.MONUMENTS, 1, "stair_that_stops", "The Stair That Stops").verb(Verb.REACH).place().rank(1)
                .lands(Group.MOUNTAIN, Group.SNOW, Group.FOREST, Group.PLAINS).anchor(Anchor.FIELD).slope(7).made(Made.FALLEN)
                .see("A stair on stone arches that climbs out of the trees into the sky and stops in mid-air, chains hanging from the last step. At its foot, a hall with bronze doors shut tight between two worn giants.")
                .learned("You climbed to the last span and let the counterweight go. The doors came open, and what the leavers could not carry was still on its plinth.")
                .reach(62).loot("relic_stair").icon(Relics.COUNTERWEIGHT)
                .air(Air.of(0xC9D6E2, 140).mote(() -> ParticleTypes.WHITE_ASH, 0.25F, 14, -0.01F).loop(() -> SoundEvents.ELYTRA_FLYING, 240, 0.07F, 0.7F))
                .scene(Monuments::stairThatStops)
                .script(() -> Lore.with(Steps.of(
                                Prize.at("release").then((c, i) -> {
                                    PlacesWood.fillPart(c, "door");
                                    c.sound(c.pos("door", 0), SoundEvents.IRON_DOOR_OPEN, 3.0F, 0.5F);
                                    c.sound(c.pos("release", 0), SoundEvents.CHAIN_BREAK, 2.0F, 0.6F);
                                }),
                                Prize.at("prize")),
                        "carving",
                        "They cut this where the ones staying behind would see it. It says: we built the stair to come back down. We did not finish it.",
                        "A list of what they could not carry up. Beds. Looms. The names of rivers. It goes on round the corner and stops in the middle of a word.",
                        "This one is cut deeper than the rest. It says: if you can read this, the way up is closed. Go and find the others. Some went down. Some went to sleep."))
                .play(t -> t.use("carving", 0).use("release", 0).use("prize", 0))
                .add();

        Kind.of(FSet.MONUMENTS, 2, "listening_dish", "The Listening Dish").verb(Verb.MEND).place().rank(1)
                .lands(Group.PLAINS, Group.FOREST, Group.DESERT, Group.BADLANDS).anchor(Anchor.FIELD).slope(6).made(Made.FALLEN)
                .see("A stone dish as wide as a village, tipped to the sky and half sunk in the field, its bronze rings green with age. A broken spike stands from its middle with a light still in it.")
                .learned("Three stones had fallen from its rim. With them back the dish hummed under your feet, and the door in the spike let go.")
                .reach(46).loot("relic_dish").icon(Relics.LISTENING_STONE)
                .air(Air.of(0xD9CFA8, 150).mote(() -> ParticleTypes.END_ROD, 0.06F, 12, 0.005F).loop(() -> SoundEvents.AMETHYST_BLOCK_RESONATE, 200, 0.25F, 0.5F))
                .scene(Monuments::listeningDish)
                .script(() -> Lore.with(Steps.of(
                                Sockets.need("socket", st -> st.is(Olden.BRONZE_BAND.get().asItem()), i -> M.bronzeBand()).carry("loose"),
                                Then.run(c -> {
                                    PlacesWood.fillPart(c, "door");
                                    c.sound(c.pos("prize", 0), SoundEvents.BEACON_ACTIVATE, 3.0F, 0.6F);
                                    c.sound(c.pos("prize", 0), SoundEvents.AMETHYST_BLOCK_RESONATE, 3.0F, 0.5F);
                                }),
                                Prize.at("prize")),
                        "carving",
                        "This says what the dish was for. They built ears to hear the layers under the world. There were once more than a hundred.",
                        "A day is marked here with a ring cut round it. It says: today something below answered.",
                        "The last line is cut by another hand. It says: leave it on. Someone should keep listening."))
                .play(t -> { for (int i = 0; i < 3; i++) t.use("loose", i).hold(Olden.BRONZE_BAND.get()).use("socket", i); t.ticks(10).use("carving", 1).use("prize", 0); })
                .add();

        Kind.of(FSet.MONUMENTS, 3, "sleepers_hall", "The Sleepers' Hall").verb(Verb.LIGHT).place().rank(2)
                .lands(Group.DARK, Group.FOREST, Group.SWAMP, Group.JUNGLE).anchor(Anchor.FIELD).slope(6).made(Made.FALLEN)
                .see("A long hollow in the forest floor with roots hanging into it. A stair goes down to a hall of columns where stone sleepers lie in rows, and far down it something glows.")
                .learned("You lit the lamps in the order they lay down. The round door turned, and behind it one bed was empty.")
                .reach(44).loot("relic_hall").icon(Relics.SLEEPERS_LAMP)
                .air(Air.of(0x1E3A38, 44).below().mote(() -> ParticleTypes.GLOW, 0.4F, 7, -0.004F).loop(() -> SoundEvents.AMBIENT_CAVE.value(), 360, 0.4F, 0.9F))
                .scene(Monuments::sleepersHall)
                .script(() -> Lore.with(Steps.of(
                                Light.all("lamp").inOrder(),
                                Then.run(c -> {
                                    PlacesWood.fillPart(c, "door");
                                    c.sound(c.pos("prize", 0), SoundEvents.BEACON_ACTIVATE, 3.0F, 0.5F);
                                }),
                                Prize.at("prize")),
                        "carving",
                        "Light them the way the sleepers lay down: the nearest first, the left side and then the right, and so on to the far end.",
                        "It says they were not dying. They chose to dream until the world was safe to wake in. It does not say who would tell them.",
                        "A watcher's tally, one mark for every year someone sat up with them. It stops at four hundred and six."))
                .play(t -> { t.use("carving", 0).hold(net.minecraft.world.item.Items.FLINT_AND_STEEL); for (int i = 0; i < 8; i++) t.use("lamp", i); t.ticks(10).use("prize", 0); })
                .add();
    }
}
