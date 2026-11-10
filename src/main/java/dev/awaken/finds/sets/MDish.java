package dev.awaken.finds.sets;

import dev.awaken.finds.Air;
import dev.awaken.finds.Family;
import dev.awaken.finds.Kind.Verb;
import dev.awaken.finds.Scene;
import dev.awaken.finds.kit.G;
import dev.awaken.finds.kit.M;
import dev.awaken.finds.kit.Olden;
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
 * The Listening Dishes (SPEC §62.8, family 1): ears the old world built to hear the layers under it. One generator; a
 * dish is a row of numbers. You set its fallen stones back in the rim, it hums, and the door in its spike lets go.
 */
final class MDish {
    private MDish() {}

    /**
     * One dish: how wide, how steeply tipped and toward where ({@code high}, radians, east 0, south a quarter turn), how
     * deep its bowl, how tall its spike, which arc of its rim has fallen (degrees, east 0, south 90), where its middle
     * is, whether it is whole, and whether it is the one that carries the sockets and the prize.
     */
    record Dish(int r, double tilt, int depth, int pylon, int fallFrom, int fallTo, int ox, int oz, double high, boolean whole, boolean parts) {
        double lean(int x, int z) {
            return (x - ox) * Math.cos(high) + (z - oz) * Math.sin(high);
        }

        /** The bowl's skin as a smooth surface: a rim plane tipped to the high side, less the bowl's depth. */
        int raw(int x, int z) {
            int dx = x - ox, dz = z - oz;
            return (int) Math.round(1 + tilt * r + tilt * lean(x, z) - depth * (1 - (dx * dx + dz * dz) / (double) (r * r)));
        }

        /** Whether a column is on one of the two aisles that run from the low rim to the middle: there the bowl keeps single steps. */
        boolean aisle(int x, int z) {
            double low = high + Math.PI, ux = Math.cos(low), uz = Math.sin(low), dx = x - ox, dz = z - oz;
            double along = dx * ux + dz * uz, across = Math.abs(-dx * uz + dz * ux);
            double side = Math.abs(dx * ux + dz * uz), sideAlong = -dx * uz + dz * ux;
            return (along > 0 && across <= 1.5) || (sideAlong > 0 && side <= 1.5);
        }

        /** The bowl as it is built: tiers two high, like the seats of a theatre, except on the aisles and at the rim. */
        int bowl(int x, int z) {
            int v = Math.max(1, raw(x, z));
            int dx = x - ox, dz = z - oz;
            if (dx * dx + dz * dz > (r - 2.5) * (r - 2.5) || aisle(x, z)) return v;
            return Math.max(1, v - Math.floorMod(v - 1, 2));
        }
    }

    static void dish(Scene s, Dish d) {
        int R = d.r(), ox = d.ox(), oz = d.oz();
        int ex = (int) Math.signum(Math.round(Math.cos(d.high()) * 1.5)), ez = (int) Math.signum(Math.round(Math.sin(d.high()) * 1.5));   // toward the high side, by blocks
        // -- the dish: a skin three thick in tiers, bronze rings, a lip; solid where it rests on the ground
        for (int x = ox - R - 1; x <= ox + R + 1; x++)
            for (int z = oz - R - 1; z <= oz + R + 1; z++) {
                double r = Math.sqrt((x - ox) * (x - ox) + (z - oz) * (z - oz));
                if (r > R + 0.6) continue;
                int top = d.bowl(x, z);
                boolean rim = r > R - 1.6, aisle = d.aisle(x, z) && !rim;
                BlockState skin = rim ? M.block() : Math.abs(r - R / 3.0) < 0.7 || Math.abs(r - R * 2 / 3.0) < 0.7 ? M.bronze() : r < 5.2 ? M.mosaic() : M.flag();
                for (int y = top - 3 <= 3 ? 1 : top - 2; y < top; y++) s.put(x, y, z, y == top - 1 && !rim && !aisle ? (top / 2 % 2 == 0 ? M.band() : M.block()) : M.course(y));
                s.put(x, top, z, skin);
                if (rim) {
                    s.put(x, top + 1, z, r > R - 0.5 ? M.bronzeBand() : M.block());
                    if (r > R - 0.5 && Math.floorMod(x + z, 5) == 0) s.put(x, top + 2, z, M.blockSlab());
                }
            }
        // -- what carries the high side: a drum, and fins out to the rim with a way through each and a pilaster on its end
        double drum = R * 0.62;
        for (int x = ox - R; x <= ox + R; x++)
            for (int z = oz - R; z <= oz + R; z++) {
                double r = Math.sqrt((x - ox) * (x - ox) + (z - oz) * (z - oz));
                if (r < drum - 0.9 || r >= drum + 0.9) continue;
                for (int y = 1; y <= d.bowl(x, z) - 3; y++) s.put(x, y, z, y % 10 == 0 ? M.band() : M.course(y));
            }
        for (int k = 0; k < 12; k++) {
            double a = Math.toRadians(k * 30 + 15);
            for (int r = (int) drum + 1; r <= R - 1; r++) {
                int x = ox + (int) Math.round(Math.cos(a) * r), z = oz + (int) Math.round(Math.sin(a) * r), top = d.bowl(x, z) - 3;
                boolean way = Math.abs(r - (drum + R) / 2) <= 1.2 && top > 8;
                for (int y = 1; y <= top; y++) {
                    if (way && y <= 4) continue;
                    s.put(x, y, z, way && y == 5 ? M.band() : y % 10 == 0 ? M.band() : M.course(y));
                }
                if (r == R - 1 && top > 4) {
                    s.put(x, top + 1, z, M.block());
                    int px = ox + (int) Math.round(Math.cos(a) * R), pz = oz + (int) Math.round(Math.sin(a) * R);      // the pilaster under the lip
                    for (int y = 1; y <= top - 1; y++) s.put(px, y, pz, y == top - 1 ? M.capital() : y % 10 == 0 ? M.band() : M.block());
                }
            }
        }
        // -- the arc of the rim that fell: a ragged bite out of the dish, stumps of what carried it
        boolean fell = d.fallTo() > d.fallFrom();
        if (fell)
            for (int x = ox - R - 1; x <= ox + R + 1; x++)
                for (int z = oz - R - 1; z <= oz + R + 1; z++) {
                    double r = Math.sqrt((x - ox) * (x - ox) + (z - oz) * (z - oz)), th = Math.toDegrees(Math.atan2(z - oz, x - ox));
                    boolean in = th <= d.fallTo() && th >= d.fallFrom() || th + 360 <= d.fallTo() && th + 360 >= d.fallFrom() || th - 360 <= d.fallTo() && th - 360 >= d.fallFrom();
                    if (!in) continue;
                    if (r < R * 0.53 + n(s, x * 1.5, z * 1.5, 4.0) * R * 0.3) continue;
                    int stump = (int) (n(s, x * 2.1 + 9, z * 2.1, 2.5) * 7) - 1;
                    for (int y = Math.max(1, stump + 1); y <= d.pylon() + R; y++) {
                        var q = s.rel(x, y, z);
                        s.blocks().remove(BlockPos.asLong(q.getX(), q.getY(), q.getZ()));
                    }
                }
        // -- the spike: two steps of plinth filled down to the bowl under them, a base with the vault in it, a shaft that
        //    leans to the high side
        int c = d.bowl(ox, oz), H = c + d.pylon();
        disc(s, ox, oz, 6.3, c + 1, M.block());
        disc(s, ox, oz, 5.3, c + 2, M.block());
        G.foot(s, ox - 7, oz - 7, ox + 7, oz + 7, c + 1);
        M.mass(s, ox - 3, c + 3, oz - 3, ox + 3, c + 8, oz + 3);
        for (int q = 0; q < 4; q++) {                              // a buttress at each corner, stepped back at the top
            int bx = ox + (q < 2 ? -4 : 4), bz = oz + (q % 2 == 0 ? -4 : 4);
            for (int y = c + 3; y <= c + 6; y++) s.put(bx - Integer.signum(bx - ox), y, bz, M.block());
            for (int y = c + 3; y <= c + 6; y++) s.put(bx, y, bz - Integer.signum(bz - oz), M.block());
        }
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) if (Math.abs(x) == 3 || Math.abs(z) == 3) s.put(ox + x, c + 8, oz + z, M.band());
        if (d.parts()) {
            air(s, ox - 1, c + 3, oz - 1, ox + 1, c + 5, oz + 1);
            for (int x = -1; x <= 1; x++) for (int y = c + 3; y <= c + 6; y++) s.put(ox + x, y, oz + 4, y == c + 6 ? M.band() : x == 0 ? AIR : M.bronzeBand());
            for (int y = c + 3; y <= c + 4; y++) {
                s.put(ox, y, oz + 2, AIR);
                s.put(ox, y, oz + 3, M.door());
                s.part("door", ox, y, oz + 3);
            }
            s.put(ox, c + 3, oz - 1, M.seal());
            s.part("prize", ox, c + 3, oz - 1);
            s.put(ox, c + 6, oz, M.lamp(true));
            for (int[] k : new int[][] {{3, 0}, {-3, 0}, {0, -3}}) Mon.carving(s, ox + k[0], c + 4, oz + k[1]);
        }
        for (int y = c + 9; y <= H; y++) {
            int up = y - c - 9, span = H - c - 9, hs = up < span * 0.35 ? 2 : 1, o = up / 7;
            for (int x = -hs; x <= hs; x++)
                for (int z = -hs; z <= hs; z++) s.put(ox + ex * o + x, y, oz + ez * o + z, up % 8 == 0 ? M.bronzeBand() : (Math.abs(x) == hs && Math.abs(z) == hs && hs == 2) ? M.block() : M.course(y));
            if (up == 4 || (d.whole() && up % 12 == 4)) {          // the light in its core, seen through a cross cut in each face
                for (int q = 0; q < 4; q++) for (int k = 1; k <= 2; k++) s.put(ox + ex * o + DX[q] * k, y, oz + ez * o + DZ[q] * k, AIR);
                s.put(ox + ex * o, y, oz + ez * o, M.lamp(true));
            }
        }
        int tipX = ox + ex * ((H - c - 9) / 7), tipZ = oz + ez * ((H - c - 9) / 7);
        if (d.whole()) {                                           // whole: a bronze crown and the light it was built to carry
            disc(s, tipX, tipZ, 2.3, H + 1, M.bronze());
            for (int q = 0; q < 4; q++) s.put(tipX + DX[q] * 2, H + 2, tipZ + DZ[q] * 2, M.bronzeBand());
            s.put(tipX, H + 2, tipZ, M.lamp(true));
            s.put(tipX, H + 3, tipZ, M.blockSlab());
        } else M.ruin(s, Math.min(ox, tipX) - 3, Math.min(oz, tipZ) - 3, Math.max(ox, tipX) + 3, Math.max(oz, tipZ) + 3, H - 4, 3, H);
        // -- the sockets in the low rim, where three banded stones fell out
        if (d.parts()) {
            double low = Math.toDegrees(d.high()) + 180;
            for (int deg = -20; deg <= 20; deg += 20) {
                int x = ox + (int) Math.round(Math.cos(Math.toRadians(low + deg)) * R), z = oz + (int) Math.round(Math.sin(Math.toRadians(low + deg)) * R), y = d.bowl(x, z) + 1;
                s.put(x, y, z, M.rubble());
                s.part("socket", x, y, z);
            }
        }
        // -- what fell, lying under the arc it fell from
        if (fell)
            for (int i = 0; i < 5; i++) {
                double a = Math.toRadians(d.fallFrom() + (d.fallTo() - d.fallFrom()) * (i + 0.5) / 5), rr = R * (0.75 + (i % 3) * 0.14);
                M.heap(s, ox + (int) Math.round(Math.cos(a) * rr), oz + (int) Math.round(Math.sin(a) * rr), 2.8 + (i * 7 % 5) * 0.45);
            }
    }

    /** A dish on its terrace, with what lies about it. {@code second} is the other of a pair. */
    private static void site(Scene s, Dish d, Dish second, int reach) {
        int R = d.r();
        G.Shape foot = G.disc(d.ox(), d.oz(), R + 4);
        if (second != null) foot = G.either(G.either(foot, G.disc(second.ox(), second.oz(), second.r() + 4)), G.box(d.ox(), -4, second.ox(), 4));
        G.platform(s, foot, 0, reach);
        s.clear(reach);
        dish(s, d);
        if (second != null) {
            dish(s, second);
            for (int x = d.ox() + R + 1; x <= second.ox() - second.r() - 1; x++) for (int z = -2; z <= 2; z++) s.put(x, 0, z, Math.abs(z) == 2 ? M.block() : M.flag());   // the way between them
        }
        double low = d.high() + Math.PI;
        int lx = d.ox() + (int) Math.round(Math.cos(low) * (R + 4)), lz = d.oz() + (int) Math.round(Math.sin(low) * (R + 4));
        // the way up onto the terrace, on the low side, and the three stones that fell from the rim
        // (a pair is entered from the south, onto the way between them)
        Direction out = second != null ? Direction.SOUTH : Math.abs(Math.cos(low)) > Math.abs(Math.sin(low)) ? (Math.cos(low) > 0 ? Direction.EAST : Direction.WEST) : (Math.sin(low) > 0 ? Direction.SOUTH : Direction.NORTH);
        int sx = second != null ? 0 : d.ox() + out.getStepX() * (R + 5), sz = second != null ? 5 : d.oz() + out.getStepZ() * (R + 5);
        int steps = G.stairs(s, sx, sz, out, 2, 0);
        int[][] loose = {{lx - 3, lz + 2}, {d.ox() + out.getStepX() * (R / 2) + 2, d.oz() + out.getStepZ() * (R / 2) - 3}, {sx + out.getStepX() * (steps + 3) + 2, sz + out.getStepZ() * (steps + 3) - 1}};
        for (int i = 0; i < loose.length; i++) {
            int x = loose[i][0], z = loose[i][1];
            int y = i == 1 ? d.bowl(x, z) + 1 : Math.max(s.g(x, z), s.taken(x, 1, z) ? 1 : s.g(x, z)) + 1;
            while (s.taken(x, y, z) && !s.get(x, y, z).isAir() && y < 40) y++;
            s.put(x, y, z, M.bronzeBand());
            s.part("loose", x, y, z);
        }
        M.finish(s, 6, 40);
        Mon.camp(s, d.ox() - out.getStepZ() * (R + 9) + out.getStepX() * 6, d.oz() + out.getStepX() * (R + 9) + out.getStepZ() * 6);
        path(s, sx + out.getStepX() * (steps + 10), sz + out.getStepZ() * (steps + 10), sx + out.getStepX() * (steps + 1), sz + out.getStepZ() * (steps + 1), 1, st(Blocks.COARSE_DIRT), st(Blocks.GRAVEL));
        wild(s, Math.min(reach - 8, R + (second == null ? 6 : 26)), reach - 1, second == null ? 14 : 10);
        int cx = d.ox() + (int) Math.round(Math.cos(low - 0.5) * (R + 12)), cz = d.oz() + (int) Math.round(Math.sin(low - 0.5) * (R + 12));
        s.cam(cx, 6, cz, (int) Math.round(Math.toDegrees(Math.atan2(-(d.ox() - cx), d.oz() - cz))), -10)
                .cam(d.ox() + R, R + 24, d.oz() + R + 10, 140, 40)
                .cam(d.ox(), d.bowl(d.ox(), d.oz()) + 4, d.oz() + 12, 180, -6)
                .cam(d.ox() + (int) (Math.cos(d.high()) * R * 0.8), 3, d.oz() + (int) (Math.sin(d.high()) * R * 0.8) + 3, 150, -14);
    }

    private static final double NE = -Math.PI / 4;

    private static void small(Scene s) {
        int turn = Mon.pick(s, 21, 3) * 40;
        site(s, new Dish(18, 0.8, 5, 20, 150 + turn, 215 + turn, 0, 0, NE, false, true), null, 34);
    }

    private static void fallen(Scene s) {
        int turn = Mon.pick(s, 22, 4) * 25;
        site(s, new Dish(26, 0.12, 8, 14, 10 + turn, 150 + turn, 0, 0, NE, false, true), null, 42);
    }

    private static void listening(Scene s) {
        int turn = Mon.pick(s, 23, 3) * 20 - 20;
        site(s, new Dish(30, 0.4, 9, 34, -170 + turn, -100 + turn, 0, 0, NE, false, true), null, 46);
    }

    private static void twin(Scene s) {
        // two that face each other: each is tipped up at its far side
        site(s, new Dish(17, 0.7, 5, 18, 0, 0, -21, 0, Math.PI, false, true), new Dish(17, 0.7, 5, 18, -60, 20, 21, 0, 0, false, false), 52);
    }

    private static void whole(Scene s) {
        site(s, new Dish(32, 0.45, 10, 44, 0, 0, 0, 0, NE, true, true), null, 50);
        for (int k = 0; k < 8; k++) {                              // the lamps that ring it still burn
            double a = Math.toRadians(k * 45 + 22);
            M.lampPost(s, (int) Math.round(Math.cos(a) * 35), 1, (int) Math.round(Math.sin(a) * 35), 3, true);
        }
    }

    static void init() {
        Object[][] five = {
                {1, "dish_small", "The Small Ear", (dev.awaken.finds.Kind.Maker) MDish::small, 34,
                        "A small stone dish on tall fins, tipped hard at the sky, with a stub of a spike in its middle. A bite is gone from its rim.",
                        "Three banded stones had fallen from the rim. With them back it hummed, thin and high, and the little door in the spike let go.",
                        "A builder's mark, and a line under it. It says: the first one we made. It was too small to hear anything, and we were proud of it anyway.",
                        "Someone's height is scratched here, year after year. Seven marks, each a little higher than the last.",
                        "It says: point it at the ground, not at the sky. That was the thing we got wrong for the longest time."},
                {2, "dish_fallen", "The Fallen Ear", (dev.awaken.finds.Kind.Maker) MDish::fallen, 42,
                        "A wide dish lying almost flat in the grass, half its rim gone and the rest sunk level with the field. You could walk across it before you knew what it was.",
                        "You set three stones back in what is left of the rim. The ground hummed under your feet, and the stub in the middle opened.",
                        "It says the ground shook the night this one came down. Nobody was hurt. They never raised it again.",
                        "A tally of what the dish heard, one stroke a day. The strokes stop, and after them someone has cut: nothing for a year. Then: nothing for ten.",
                        "It says: we argued about what the sound was. Some of us said a voice. Some said only weight, settling. We did not agree."},
                {3, "listening_dish", "The Listening Dish", (dev.awaken.finds.Kind.Maker) MDish::listening, 46,
                        "A stone dish as wide as a village, tipped to the sky and half sunk in the field, its bronze rings green with age. A broken spike stands from its middle with a light still in it.",
                        "Three stones had fallen from its rim. With them back the dish hummed under your feet, and the door in the spike let go.",
                        "This says what the dish was for. They built ears to hear the layers under the world. There were once more than a hundred.",
                        "A day is marked here with a ring cut round it. It says: today something below answered.",
                        "The last line is cut by another hand. It says: leave it on. Someone should keep listening."},
                {4, "dish_twin", "The Twin Ears", (dev.awaken.finds.Kind.Maker) MDish::twin, 52,
                        "Two dishes face each other across a paved way, each tipped up at its far side like a pair of cupped hands. One has lost part of its rim.",
                        "You mended the rim of the whole one. Both of them hummed, a beat apart, and the door in the nearer spike let go.",
                        "It says they built two so that one could check the other. When both heard the same thing, they believed it.",
                        "Two columns of marks, side by side. They match all the way down. At the bottom someone has cut: so it is true.",
                        "It says: this is the day we stopped asking whether something was down there, and began asking what to do about it."},
                {5, "dish_whole", "The Ear That Still Hears", (dev.awaken.finds.Kind.Maker) MDish::whole, 50,
                        "A great dish with its rim unbroken and its spike whole, a light burning at the top and lamps lit all round it. The air near it hums.",
                        "Even this one had dropped three stones. With them back the hum steadied, and the door at the foot of the spike let go.",
                        "It says the last of them kept this one working after the others left. Three people. Then two. Then one, who cut this.",
                        "It says: it has not stopped. The sound from below has not stopped once since the day it began. I have got used to it, the way you get used to rain.",
                        "The newest cut of all. It says: if you can stand here and hear it too, then I was not the last. Good."}};
        for (Object[] v : five) {
            int tier = (Integer) v[0];
            Mon.kind(Family.DISH, tier, (String) v[1], (String) v[2]).verb(Verb.MEND)
                    .lands(Group.PLAINS, Group.FOREST, Group.DESERT, Group.BADLANDS, Group.SNOW).slope(tier == 2 ? 10 : 14)
                    .see((String) v[5]).learned((String) v[6]).reach((Integer) v[4])
                    .air(Air.of(0xD9CFA8, 150).mote(() -> ParticleTypes.END_ROD, tier == 5 ? 0.14F : 0.06F, 12, 0.005F).loop(() -> SoundEvents.AMETHYST_BLOCK_RESONATE, tier == 5 ? 120 : 200, tier == 5 ? 0.4F : 0.25F, 0.5F))
                    .scene((dev.awaken.finds.Kind.Maker) v[3])
                    .script(() -> Lore.with(Steps.of(
                                    Sockets.need("socket", st -> st.is(Olden.BRONZE_BAND.get().asItem()), i -> M.bronzeBand()).carry("loose"),
                                    Then.run(c -> {
                                        Mon.open(c, "door", SoundEvents.IRON_DOOR_OPEN);
                                        c.sound(c.pos("prize", 0), SoundEvents.BEACON_ACTIVATE, 3.0F, 0.6F);
                                        c.sound(c.pos("prize", 0), SoundEvents.AMETHYST_BLOCK_RESONATE, 3.0F, 0.5F);
                                    }),
                                    Prize.at("prize")),
                            "carving", (String) v[7], (String) v[8], (String) v[9]))
                    .play(t -> { for (int i = 0; i < 3; i++) t.use("loose", i).hold(Olden.BRONZE_BAND.get()).use("socket", i); t.ticks(10).use("carving", 1).use("prize", 0); })
                    .add();
        }
    }
}
