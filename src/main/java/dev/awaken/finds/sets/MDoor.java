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
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.finds.kit.B.*;

/**
 * The Doors in the Sand (SPEC §64.8, family 12): the fronts those who went down built over the ways they left open, for
 * anyone who changed their mind. The sand has shut them. Seated giants either side of a bronze door, buried to the
 * chest. You dig the door out; it lets go; behind it is a hall and what was left there.
 */
final class MDoor {
    private MDoor() {}

    private static BlockState sand(Scene s) {
        return st(s.land() == Group.BADLANDS ? Blocks.RED_SAND : Blocks.SAND);
    }

    /**
     * A dune lying against the south of something: sand from the ground up to {@code deep} at the wall (z {@code z0}),
     * thinning over {@code run} blocks, across x {@code x0..x1} and tailing off past each end. Never over what is built.
     */
    static void dune(Scene s, int x0, int x1, int z0, int deep, int run) {
        BlockState sand = sand(s);
        for (int x = x0 - 8; x <= x1 + 8; x++)
            for (int z = z0; z <= z0 + run; z++) {
                double side = x < x0 ? (x0 - x) / 8.0 : x > x1 ? (x - x1) / 8.0 : 0, away = (z - z0) / (double) run;
                int top = (int) Math.round(deep * (1 - away * away) * (1 - side) + (n(s, x * 1.1, z * 1.1, 6.0) - 0.5) * 3.2);
                for (int y = s.g(x, z) + 1; y <= top; y++) if (!s.taken(x, y, z)) s.put(x, y, z, sand);
            }
    }

    /** The plug of sand before a door, three wide: every block of it is to be dug, the top course first. */
    static void plug(Scene s, int z0, int deep, int thick) {
        BlockState sand = sand(s);
        for (int y = deep; y >= 1; y--)
            for (int z = z0; z < z0 + thick; z++)
                for (int x = -1; x <= 1; x++) {
                    s.put(x, y, z, sand);
                    s.part("sand", x, y, z);
                }
    }

    /** The hall behind a door: columns, a mosaic way, lamps, and the plinth at its end. It runs north from z {@code z0}. */
    static void hall(Scene s, int z0, int len, int y0, boolean lit) {
        M.mass(s, -6, y0 - 1, z0 - len - 1, 6, y0 + 8, z0);
        air(s, -4, y0, z0 - len, 4, y0 + 6, z0 - 1);
        for (int x = -4; x <= 4; x++) for (int z = z0 - len; z <= z0 - 1; z++) s.put(x, y0 - 1, z, Math.abs(x) <= 1 ? M.mosaic() : Math.floorMod(x + z, 5) == 0 ? M.block() : M.flag());
        for (int z = z0 - 3; z >= z0 - len + 2; z -= 4) for (int x = -3; x <= 3; x += 6) { M.column(s, x, z, y0, y0 + 5); s.put(x, y0 + 6, z, M.band()); }
        for (int z = z0 - 5; z >= z0 - len + 2; z -= 8) { s.put(-4, y0 + 3, z, M.lamp(lit)); s.put(4, y0 + 3, z, M.lamp(true)); }
        Mon.prize(s, 0, y0, z0 - len + 1, Direction.SOUTH);
        s.put(0, y0 + 6, z0 - len + 1, M.lamp(true));
        Mon.carving(s, -5, y0 + 1, z0 - len / 2);
        for (int x = -2; x <= 2; x += 4) M.colossus(s, x * 2, y0, z0 - len, Direction.SOUTH, 6, 0);
    }

    /** A bronze door five wide in a wall at z {@code z0}: its leaves are the part, a ring of pale inlay across them. */
    static void door(Scene s, int z0, int y0, int high, boolean open) {
        for (int x = -3; x <= 3; x++)
            for (int y = y0; y <= y0 + high + 1; y++) {
                boolean frame = Math.abs(x) == 3 || y == y0 + high + 1;
                for (int z = z0 - 3; z <= z0; z++) s.put(x, y, z, frame ? (z == z0 ? M.bronzeBand() : M.block()) : AIR);
                if (frame || open) continue;
                double r = Math.hypot(x, y - (y0 + high * 0.6));
                s.put(x, y, z0 - 1, Math.abs(r - 1.9) < 0.6 ? M.seal() : M.door());
                s.part("door", x, y, z0 - 1);
            }
        s.put(0, y0 + high + 2, z0, M.seal());
    }

    /**
     * The front: a wall {@code 2 * W + 1} wide and {@code H} high with a deep frieze, the door in its middle, {@code n}
     * seated giants before it, a body behind it, and the sand. {@code bury}: how deep the sand lies at the wall.
     * {@code whole}: nothing broken, the door standing open, on a podium.
     */
    static void front(Scene s, int W, int H, int n, int bury, int hallLen, boolean whole, int reach) {
        int top = whole ? 3 : 0;
        G.platform(s, G.box(-W - 4, -hallLen - 8, W + 4, whole ? 12 : 2), top, reach);
        s.clear(reach);
        int y0 = top + 1;
        M.mass(s, -W, y0, -3, W, y0 + H - 1, 0);
        M.face(s, Direction.SOUTH, -W, W, 0, y0, y0 + H - 5, 6);
        for (int x = -W; x <= W; x++) {                             // the frieze: panels and carved stones between bronze bands, a cornice over it
            for (int y = y0 + H - 5; y <= y0 + H - 1; y++) s.put(x, y, 1, y == y0 + H - 5 || y == y0 + H - 2 ? M.bronzeBand() : y == y0 + H - 1 ? M.block() : Math.floorMod(x, 4) == 0 ? M.glyph() : M.panel());
            s.put(x, y0 + H, 1, M.blockSlab());
            s.put(x, y0 + H, 0, M.block());
            if (Math.floorMod(x, 3) == 0) s.put(x, y0 + H + 1, 0, M.blockSlab());
        }
        Mon.carving(s, 5, y0 + Math.max(0, bury - 1), 1);          // by the door, at the height the sand lets you stand
        // the body behind it: lower, stepping back, with the hall in it
        M.mass(s, -W + 3, y0, -hallLen - 6, W - 3, y0 + H - 8, -4);
        M.mass(s, -W + 7, y0 + H - 7, -hallLen - 2, W - 7, y0 + H - 4, -4);
        M.face(s, Direction.WEST, -hallLen - 6, -4, -W + 3, y0, y0 + H - 8, 6);
        M.face(s, Direction.EAST, -hallLen - 6, -4, W - 3, y0, y0 + H - 8, 6);
        M.face(s, Direction.NORTH, -W + 3, W - 3, -hallLen - 6, y0, y0 + H - 8, 6);
        for (int x = -W + 3; x <= W - 3; x++) for (int z = -hallLen - 6; z <= -4; z++) if (x == -W + 3 || x == W - 3 || z == -hallLen - 6) s.put(x, y0 + H - 7, z, Math.floorMod(x + z, 3) == 0 ? M.block() : M.wall());
        hall(s, -4, hallLen, y0, whole);
        door(s, 0, y0, 7, whole);
        // the giants: seated, their backs to the wall
        int gh = Math.min(16, H - 6);
        for (int i = 0; i < n; i++) {
            int gx = (i % 2 == 0 ? -1 : 1) * (8 + (i / 2) * 9), lost = whole ? 0 : i == 1 ? 1 : i == 2 ? 2 : 0;
            if (Math.abs(gx) + 4 > W) continue;
            M.giant(s, gx, y0, 3, Direction.SOUTH, gh, true, lost, false);
            if (lost == 1) {                                        // its head, where it rolled
                int hx = gx + 4, hz = 14;
                for (int x = -1; x <= 1; x++) for (int y = 0; y <= 2; y++) for (int z = -1; z <= 1; z++) s.put(hx + x, bury / 2 + y, hz + z, z == 1 && x == 0 && y == 1 ? M.panel() : y == 2 && Math.abs(x) + Math.abs(z) == 2 ? M.blockSlab() : M.block());
                G.foot(s, hx - 1, hz - 1, hx + 1, hz + 1, bury / 2);
            }
        }
        if (!whole) {
            M.ruin(s, W - 9, -3, W, 1, y0 + H - 7, 4, y0 + H + 2);
            M.heap(s, W - 4, 6, 3.4);
        } else {
            for (int x = -W + 2; x <= W - 2; x += (2 * W - 4) / 4) if (Math.abs(x) > 4) M.lampPost(s, x, y0, 9, 3, true);
            G.stairs(s, 0, 13, Direction.SOUTH, 3, top);
        }
        M.finish(s, 2, 52);
        // -- the sand, the trench the last diggers left, and the plug still in front of the door
        if (bury > 0) {
            dune(s, -W, W, 1, bury, 18);
            for (int z = 1; z <= 14; z++) for (int x = -3; x <= 3; x++) {
                int floor = Math.min(bury, Math.max(1, 5 - (z - 3) / 2));
                for (int y = y0 + (z <= 2 ? 0 : Math.max(0, floor - 1)); y <= y0 + bury + 4; y++) { if (z <= 2 && Math.abs(x) <= 1 && y <= y0 + Math.min(4, bury) - 1) continue; BlockState b = s.get(x, y, z); if (b != null && (b.is(Blocks.SAND) || b.is(Blocks.RED_SAND))) s.put(x, y, z, AIR); }
                if (Math.abs(x) == 3 && z >= 3 && z % 2 == 1) for (int y = y0; y <= y0 + bury; y++) { BlockState b = s.get(x, y, z); if (b == null || b.isAir() || b.is(Blocks.SAND) || b.is(Blocks.RED_SAND)) s.put(x, y, z, st(Blocks.SPRUCE_FENCE)); }
            }
            plug(s, 1, Math.min(4, bury) + top, 2);
            // the diggers' awning stands on the dune: the sand under it is brought level first, so no post hangs
            int ay = Mon.restAt(s, 8, 11, y0 + bury + 6);
            for (int x = 6; x <= 10; x++) for (int z = 9; z <= 13; z++) for (int y = Mon.restAt(s, x, z, ay + 4); y < ay; y++) s.put(x, y, z, sand(s));
            awning(s, 6, 9, 10, 13, ay, 3, st(Blocks.SPRUCE_FENCE), DyeColor.ORANGE, DyeColor.WHITE);
            s.put(7, ay, 10, Props.BASKET.get().of(1));
            s.put(8, ay, 12, Props.BUNDLE.get().of(0));
        } else plug(s, 1, 1 + top, 2);
        Mon.camp(s, -W + 4, Math.min(reach - 6, 24));
        wild(s, Math.min(reach - 6, W + 8), reach - 1, 8);
        if (s.land() != Group.BADLANDS) for (int i = 0; i < 3; i++) { int px = (i - 1) * (W - 2) + 3, pz = 20 + i * 3; if (open(s, px, pz, 2) && Math.abs(pz) < reach - 3) palm(s, px, pz, s.g(px, pz) + 1, 6 + i, i - 1); }
        s.cam(-W + 2, y0 + bury + 4, 28, -160, -10).cam(W, H + 18, 30, 145, 40).cam(0, y0 + bury + 1, 12, 180, -4).cam(0, y0 + 1, -6, 180, 0);
    }

    /** The Fallen Front: the wall went over on its face and lies in three pieces; the giants lie beside it; the hall's mouth is a hole full of sand. */
    static void fallen(Scene s, int reach) {
        int W = 16;
        G.platform(s, G.box(-10, -22, 10, 0), 0, reach);
        s.clear(reach);
        M.mass(s, -8, 1, -20, 8, 7, -2);
        Mon.carving(s, 9, 1, -1);
        Mon.carving(s, -9, 1, 2);
        hall(s, -1, 14, 1, false);
        door(s, 0, 1, 6, true);
        M.ruin(s, -8, -20, 8, -2, 5, 2, 9);
        for (int x = -W; x <= W; x++)                               // the wall, on its face: its back is up, its courses run away from you
            for (int z = 4; z <= 24; z++) {
                if (Math.floorMod(x + 5, 12) == 0 || n(s, x * 0.7, z * 0.7, 7.0) < 0.2) continue;
                int g = s.g(x, z), lift = Math.floorMod(x + 5, 12) < 6 ? 0 : 1;
                for (int y = 1; y <= 2 + lift; y++) s.put(x, g + y, z, y == 2 + lift ? (z % 6 == 0 ? M.band() : z > 19 ? M.panel() : M.course(z)) : M.stone());
            }
        M.giant(s, -W - 2, s.g(-W - 2, 6) + 1, 6, Direction.SOUTH, 16, false, 1, true);
        M.giant(s, W + 4, s.g(W + 4, 8) + 1, 8, Direction.SOUTH, 16, false, 0, true);
        M.giant(s, 13, 1, 2, Direction.SOUTH, 16, true, 2, false);   // one still sits by the hall's mouth: what you see of it from a hill
        G.foot(s, 8, -1, 18, 9, 1);
        M.finish(s, 2, 56);
        dune(s, -W, W, 1, 3, 26);
        for (int z = 1; z <= 3; z++) for (int x = -1; x <= 1; x++) for (int y = 1; y <= 5; y++) { BlockState b = s.get(x, y, z); if (b != null && !b.isAir() && !Olden(b)) s.put(x, y, z, AIR); }
        BlockState sand = sand(s);
        for (int y = 4; y >= 1; y--) for (int z = -2; z <= -1; z++) for (int x = -1; x <= 1; x++) { s.put(x, y, z, sand); s.part("sand", x, y, z); }
        Mon.camp(s, -W - 6, 28);
        wild(s, 30, reach - 1, 8);
        s.cam(-W - 4, 8, 34, -155, 6).cam(W, 30, 34, 145, 44).cam(0, 4, 8, 180, 4).cam(0, 2, -7, 180, 0);
    }

    private static boolean Olden(BlockState b) {
        return dev.awaken.finds.kit.Olden.is(b);
    }

    /** The Door Without a Wall: two pylons and a lintel standing alone, the bronze door between them, and under its threshold a stair down. */
    static void gate(Scene s, int reach) {
        G.platform(s, G.box(-14, -14, 14, 3), 0, reach);
        s.clear(reach);
        for (int side = -1; side <= 1; side += 2) {                 // the pylons: battered, banded, a panel high on each face
            for (int y = 1; y <= 26; y++) {
                int in = y > 18 ? 1 : 0;
                for (int x = 4 + in; x <= 10 - in; x++) for (int z = -3 + in; z <= 3 - in; z++) s.put(side * x, y, z, y % 6 == 0 ? M.band() : (x == 7 && Math.abs(z) == 3 - in && y >= 12 && y <= 15) ? M.panel() : M.course(y));
            }
            for (int x = 3; x <= 11; x++) for (int z = -4; z <= 4; z++) if (x == 3 || x == 11 || Math.abs(z) == 4) { s.put(side * x, 1, z, M.block()); s.put(side * x, 2, z, stair(M.blockStairs(), x == 3 ? (side > 0 ? Direction.EAST : Direction.WEST) : x == 11 ? (side > 0 ? Direction.WEST : Direction.EAST) : z > 0 ? Direction.NORTH : Direction.SOUTH)); }
            M.giant(s, side * 7, 1, 6, Direction.SOUTH, 15, true, side < 0 ? 0 : 2, false);
        }
        Mon.carving(s, 12, 1, 1);
        Mon.carving(s, -12, 1, 1);
        M.mass(s, -5, 18, -2, 5, 22, 2);
        for (int x = -5; x <= 5; x++) { s.put(x, 23, 0, Math.floorMod(x, 2) == 0 ? M.block() : M.blockSlab()); s.put(x, 20, 3, x == 0 ? M.seal() : M.bronzeBand()); }
        door(s, 0, 1, 9, false);
        for (int y = 12; y <= 17; y++) for (int x = -3; x <= 3; x++) s.put(x, y, 0, y == 12 ? M.band() : Math.floorMod(x + y, 2) == 0 ? M.glyph() : M.panel());
        // under the threshold: a stair north and down to a room
        for (int i = 0; i <= 6; i++) for (int x = -1; x <= 1; x++) { s.put(x, -i, -4 - i, M.step(s, x, -4 - i, Direction.SOUTH)); for (int y = -i + 1; y <= -i + 3; y++) s.put(x, y, -4 - i, AIR); s.put(x, -i - 1, -4 - i, M.stone()); s.put(-2, -i + 1, -4 - i, M.block()); s.put(2, -i + 1, -4 - i, M.block()); }
        M.shell(s, -4, -8, -18, 4, -2, -11);
        M.mass(s, -4, -2, -18, 4, -1, -11);
        air(s, -3, -7, -17, 3, -3, -11);
        for (int x = -3; x <= 3; x++) for (int z = -17; z <= -11; z++) s.put(x, -8, z, Math.abs(x) <= 1 ? M.mosaic() : M.flag());
        for (int x = -1; x <= 1; x++) for (int y = -7; y <= -5; y++) s.put(x, y, -11, AIR);
        Mon.prize(s, 0, -7, -16, Direction.SOUTH);
        s.put(0, -3, -14, M.lamp(true));
        Mon.carving(s, -3, -6, -14);
        M.finish(s, 2, 52);
        dune(s, -12, 12, 1, 7, 16);
        for (int z = 1; z <= 10; z++) for (int x = -2; x <= 2; x++) for (int y = Math.max(1, 5 - z / 2); y <= 12; y++) { if (z <= 2 && Math.abs(x) <= 1 && y <= 4) continue; BlockState b = s.get(x, y, z); if (b != null && (b.is(Blocks.SAND) || b.is(Blocks.RED_SAND))) s.put(x, y, z, AIR); }
        plug(s, 1, 4, 2);
        dune(s, -12, 12, -16, 4, 10);
        Mon.camp(s, -16, 14);
        wild(s, 20, reach - 1, 6);
        s.cam(-12, 6, 26, -160, -22).cam(14, 36, 26, 150, 40).cam(0, 6, 12, 180, -10).cam(0, -6, -12, 180, 0);
    }

    static void init() {
        Object[][] five = {
                {1, "door_small", "The Small Door", (dev.awaken.finds.Kind.Maker) s -> front(s, 10, 14, 2, 6, 8, false, 30), 30,
                        "The top of a stone front shows above a dune: a carved frieze, the heads of two seated figures, and the upper edge of a bronze door. A trench runs down to it.",
                        "You dug out what was left of the sand before the door. It let go with a sigh of air that had been shut in a very long time.",
                        "It says: this is a side door. The great ones are further out. But it is a door, and it was left for the same reason.",
                        "It says the ones who went down asked that a door be left wherever one of them had family above.",
                        "Scratched in the frame: I dug it out every spring for thirty years. Nobody ever came up. I kept digging. What else was there to do."},
                {2, "door_fallen", "The Fallen Front", (dev.awaken.finds.Kind.Maker) s -> fallen(s, 42), 42,
                        "A great wall of carved stone lying on its face in the sand, broken in three, with two stone giants on their backs beside it. Where it stood, a hole full of sand goes into the ground.",
                        "You dug the sand out of the mouth of the hall. There was no door left to open. You walked in.",
                        "It says the front was pushed over from the inside. It took them a week. They wanted it to look as if nobody was home.",
                        "It says: we who stayed stood it up again twice. The third time we let it lie.",
                        "It says: if you are from below, we are sorry about the wall. We never stopped leaving the lamp."},
                {3, "door", "The Door in the Sand", (dev.awaken.finds.Kind.Maker) s -> front(s, 20, 24, 4, 9, 14, false, 42), 42,
                        "A temple front of grey stone buried to its shoulders in the dunes. Four seated giants flank the door, sunk to their chests; one has lost its head. Only the top of the bronze door shows, at the bottom of a trench somebody dug and left.",
                        "You dug out the last of the sand. The door let go, and behind it the hall was as they left it, with the lamps on one side still burning.",
                        "We left this door open for anyone who changed their mind. The sand has shut it for us.",
                        "It says nobody is kept below. Any of them may come up by any of these doors on any day. In all this time, not one has.",
                        "It says: we think they are ashamed. Or they are busy. Or it is simply a long way up. Leave the lamps on the right-hand side lit."},
                {4, "door_gate", "The Door Without a Wall", (dev.awaken.finds.Kind.Maker) s -> gate(s, 30), 30,
                        "Two stone pylons and a lintel stand alone in the sand with a bronze door between them, and a seated giant before each. There is no wall. You can walk round it. Behind it is more sand.",
                        "You dug the door out anyway and it opened, on nothing. Then you saw the stair under its threshold.",
                        "It says a door needs no wall. It only needs somewhere to go. Look down.",
                        "It says this one was built by a mason whose wife went below. He would not build the wall. He said a wall was a way of saying no.",
                        "Cut at the bottom of the stair: she came up once, to this room, and we talked through the night. She went back down. I have left this for whoever comes next, from either side."},
                {5, "door_whole", "The Door That Was Left Open", (dev.awaken.finds.Kind.Maker) s -> front(s, 22, 28, 4, 0, 16, true, 46), 46,
                        "A temple front standing whole and clear of the sand on its podium, four seated giants before it, lamps burning along its steps. The bronze door stands open. A little sand has blown across the sill.",
                        "You cleared the sill and went in. The hall was swept and every lamp was lit.",
                        "It says: this door is kept. If you are coming up, welcome. If you are going down, think. Either way, wipe your feet.",
                        "It says there has been a keeper at this door for as long as there has been a door. The keeper is not here just now. The keeper will be back.",
                        "The last line. It says: one came up, in the end. A child, born down there, who had never seen the sky. She stood on these steps all day. Then she asked to see the sea."}};
        for (Object[] v : five) {
            int tier = (Integer) v[0];
            Mon.kind(Family.DOOR, tier, (String) v[1], (String) v[2]).verb(Verb.DIG)
                    .lands(Group.DESERT, Group.BADLANDS).slope(16).weight(18)
                    .see((String) v[5]).learned((String) v[6]).reach((Integer) v[4])
                    .air(Air.of(0xD9C49A, 140).mote(() -> ParticleTypes.WHITE_ASH, 0.35F, 14, 0.03F).loop(() -> SoundEvents.ELYTRA_FLYING, 220, 0.08F, 0.5F))
                    .scene((dev.awaken.finds.Kind.Maker) v[3])
                    .script(() -> Lore.with(Steps.of(
                                    Dig.at("sand"),
                                    Then.run(c -> {
                                        Mon.open(c, "door", SoundEvents.IRON_DOOR_OPEN);
                                        c.sound(c.pos("prize", 0), SoundEvents.AMBIENT_CAVE.value(), 2.0F, 0.7F);
                                    }),
                                    Prize.at("prize")),
                            "carving", (String) v[7], (String) v[8], (String) v[9]))
                    .play(t -> { for (int i = 0; i < t.count("sand"); i++) t.dig("sand", i); t.ticks(10).use("carving", 0).use("prize", 0); })
                    .add();
        }
    }
}
