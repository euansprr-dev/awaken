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
    }
}
