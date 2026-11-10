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
import net.minecraft.world.level.block.Blocks;

import static dev.awaken.finds.kit.B.*;

/**
 * The Long Road (SPEC §62.8, family 2): the road the old world built before it split, carried over the land on arches,
 * with a station every day's walk. One span is down. You wind out the planks the last travellers left, cross, and the
 * station that still has its door lets you in. It needs no terrace: its piers go down to whatever ground is under them.
 */
final class MRoad {
    private MRoad() {}

    private static final int PITCH = 14;                          // a pier five wide and an opening of nine

    /** The middle of pier {@code k} of a viaduct that starts at {@code a0}. */
    static int pier(int a0, int k) {
        return a0 + 2 + PITCH * k;
    }

    /**
     * A viaduct along x from {@code a0} to {@code a1}, its middle line at z {@code cz}, its road at {@code Y}. Two tiers
     * put a second row of arches and a walk half way up. Between {@code g0} and {@code g1} it has fallen (a pier there is
     * left as a stump). {@code lit}: its lamps burn. {@code ragged}: its ends break off; otherwise they end at a pier.
     */
    static void viaduct(Scene s, int a0, int a1, int cz, int Y, int tiers, int g0, int g1, boolean lit, boolean ragged) {
        int mid = tiers == 2 ? Y / 2 - 1 : -99;
        for (int x = a0; x <= a1; x++) {
            int p = Math.floorMod(x - a0, PITCH);
            boolean isPier = p <= 4, gone = x >= g0 && x <= g1;
            if (gone && !isPier) continue;
            double t = (p - 4) / 10.0;
            for (int tier = tiers == 2 ? 0 : 1; tier <= 1; tier++) {
                int T = tier == 0 ? mid : Y, under = isPier ? 0 : M.haunch(t, 5);
                if (gone) continue;
                if (!isPier) for (int z = -2; z <= 2; z++) for (int y = T - 1 - under; y <= T - 1; y++) s.put(x, y, cz + z, y == T - 1 - under && Math.abs(z) == 2 ? M.block() : M.course(y));
                if (tier == 0) {
                    for (int z = -2; z <= 2; z++) s.put(x, T, cz + z, Math.abs(z) == 2 ? M.block() : M.flag());
                } else {
                    for (int z = -3; z <= 3; z++) {
                        if (Math.abs(z) == 3) {
                            s.put(x, T, cz + z, M.block());
                            s.put(x, T - 1, cz + z, stairT(M.blockStairs(), z > 0 ? Direction.NORTH : Direction.SOUTH));
                            double keep = n(s, x * 0.9, (cz + z) * 7.0, 3.0);
                            if (!ragged || keep > 0.3) s.put(x, T + 1, cz + z, p == 2 ? M.block() : M.wall());
                        } else s.put(x, T, cz + z, z == 0 && Math.floorMod(x, 3) == 0 ? M.block() : M.flag());
                    }
                }
            }
        }
        for (int k = 0; pier(a0, k) + 2 <= a1; k++) {
            int px = pier(a0, k);
            boolean stump = px >= g0 && px <= g1;
            int top1 = stump ? 5 + Mon.pick(s, 80 + k, 6) : tiers == 2 ? mid - 1 : Y - 1;
            M.pier(s, px, cz, 2, top1, Math.min(5, top1 - 1));
            if (stump) { M.ruin(s, px - 3, cz - 3, px + 3, cz + 3, top1 - 2, 2, top1 + 1); continue; }
            if (tiers == 2) {                                      // the upper pier: narrower, with a panel on each face and a corbel under the road
                for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) for (int y = mid + 1; y <= Y - 1; y++)
                    s.put(px + x, y, cz + z, (Math.abs(x) == 2 || Math.abs(z) == 2) && y == mid + 4 ? M.band() : Math.abs(z) == 2 && x == 0 && y >= mid + 6 && y <= mid + 8 ? M.panel() : M.course(y));
                for (int z = -3; z <= 3; z += 6) for (int x = -1; x <= 1; x++) s.put(px + x, Y - 2, cz + z, stairT(M.blockStairs(), z > 0 ? Direction.NORTH : Direction.SOUTH));
            }
            for (int side = -1; side <= 1; side += 2) {            // a buttress strip up each long face
                for (int y = 6; y <= top1 - 3; y++) s.put(px, y, cz + side * 3, y % 10 == 0 ? M.band() : M.stone());
                s.put(px, top1 - 2, cz + side * 3, stair(M.blockStairs(), side > 0 ? Direction.NORTH : Direction.SOUTH));
            }
            M.lampPost(s, px, Y + 1, cz + 3, 2, lit);              // a lamp over every pier, on the parapet
            s.put(px, Y + 1, cz + 3, M.block());
        }
        if (ragged) {
            M.ruin(s, a0, cz - 3, a0 + 5, cz + 3, Y - 4, 4, Y + 5);
            M.ruin(s, a1 - 5, cz - 3, a1, cz + 3, Y - 5, 5, Y + 5);
        }
    }

    /** The pier that has a stair in it: wider, hollow, a door at its foot on the downhill side and a hatch in the road. */
    static void stairPier(Scene s, int px, int cz, int Y) {
        int y0 = Math.min(1, s.g(px, cz) + 1);
        M.pier(s, px, cz, 3, Y - 1, 5);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) for (int y = y0; y <= Y; y++) s.put(px + x, y, cz + z, AIR);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) s.put(px + x, y0 - 1, cz + z, M.flag());
        M.spiral(s, px, cz, 2, y0, Y + 1);                         // its last step is level with the road; the well stays open
        for (int y = y0; y <= y0 + 2; y++) for (int z = 3; z <= 4; z++) s.put(px, y, cz + z, y == y0 + 2 ? M.band() : AIR);     // the door, and its lintel
        s.put(px - 1, y0 + 1, cz + 4, M.bronzeBand());
        s.put(px + 1, y0 + 1, cz + 4, M.bronzeBand());
        G.stairs(s, px, cz + 5, Direction.SOUTH, 0, y0 - 1);
        Mon.carving(s, px - 3, y0 + 1, cz + 4);
    }

    /**
     * The station: a tower that stands on its own pier against the road's north side, its door on the road. {@code h}:
     * how high its room is. {@code whole}: roofed, with a beacon; otherwise broken off above the room.
     */
    static void station(Scene s, int cx, int cz, int Y, int h, boolean whole) {
        int tz = cz - 7;
        M.pier(s, cx, tz, 3, Y, 5);
        M.shell(s, cx - 3, Y + 1, tz - 3, cx + 3, Y + h, tz + 3);
        air(s, cx - 2, Y + 1, tz - 2, cx + 2, Y + h - 1, tz + 2);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) s.put(cx + x, Y, tz + z, Math.abs(x) + Math.abs(z) == 0 ? M.mosaic() : M.flag());
        M.face(s, Direction.NORTH, cx - 3, cx + 3, tz - 3, Y + 1, Y + h, 3);
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) s.put(cx + x, Y + h, tz + z, Math.abs(x) == 3 || Math.abs(z) == 3 ? M.band() : M.block());
        for (int y = Y + 1; y <= Y + 4; y++) {                      // the door on the road: bronze, in a banded frame under a seal
            for (int x = -1; x <= 1; x++) s.put(cx + x, y, tz + 3, y == Y + 4 ? M.band() : x == 0 ? M.door() : M.bronzeBand());
            if (y <= Y + 3) s.part("door", cx, y, tz + 3);
        }
        s.put(cx, Y + 5, tz + 3, M.seal());
        for (int x = -1; x <= 1; x++) s.put(cx + x, Y + 1, cz - 3, AIR);          // the parapet opens at the door
        Mon.prize(s, cx, Y + 1, tz - 1, Direction.SOUTH);
        s.put(cx, Y + h - 1, tz, M.lamp(true));
        Mon.carving(s, cx - 3, Y + 2, tz);
        Mon.carving(s, cx + 3, Y + 2, tz);
        s.put(cx - 2, Y + 1, tz + 1, stair(M.stairs(), Direction.EAST));
        s.put(cx + 2, Y + 1, tz + 1, st(Blocks.BARREL));
        if (whole) {
            for (int k = 1; k <= 3; k++) for (int x = -3 + k; x <= 3 - k; x++) for (int z = -3 + k; z <= 3 - k; z++) s.put(cx + x, Y + h + k, tz + z, k == 3 ? M.lamp(true) : M.block());
            for (int q = 0; q < 4; q++) s.put(cx + DX[q] * 3, Y + h + 1, tz + DZ[q] * 3, M.blockSlab());
        } else {
            M.shell(s, cx - 3, Y + h + 1, tz - 3, cx + 3, Y + h + 6, tz + 3);
            M.ruin(s, cx - 3, tz - 3, cx + 3, tz + 3, Y + h + 2, 3, Y + h + 7);
        }
    }

    /** The winch on the near side of a gap, the planks stacked by it, and where they will lie. The gap runs g0..g1 at z {@code cz}. */
    static void bridge(Scene s, int g0, int g1, int cz, int Y) {
        s.put(g0 - 2, Y + 1, cz + 2, Props.WINCH.get().of(0));
        s.part("winch", g0 - 2, Y + 1, cz + 2);
        int n = g1 - g0 + 1;
        for (int i = 0; i < n; i++) {
            int col = i / 3, up = i % 3;
            // the stack is taken from the top down, so the last of each column goes last
            s.put(g0 - 4 - col, Y + 3 - up, cz - 2, top(st(Blocks.SPRUCE_SLAB)));
            s.part("stack", g0 - 4 - col, Y + 3 - up, cz - 2);
            s.part("span", g0 + i, Y, cz);
        }
        for (int col = 0; col <= (n - 1) / 3; col++) for (int up = 0; up < 3; up++) if (!s.taken(g0 - 4 - col, Y + 1 + up, cz - 2)) s.put(g0 - 4 - col, Y + 1 + up, cz - 2, st(Blocks.SPRUCE_PLANKS));
        s.put(g0 - 1, Y + 1, cz - 1, Props.ROPE_COIL.get().of(0));
    }

    private static void finish(Scene s, int reach, int campX) {
        M.finish(s, 8, 44);
        Mon.camp(s, campX, 16);
        wild(s, 18, reach - 1, 9);
    }

    // ---- the five -----------------------------------------------------------------------------------------------------

    private static void three(Scene s) {
        s.clear(8);
        viaduct(s, -26, 22, 0, 14, 1, -7, -1, false, true);
        stairPier(s, pier(-26, 0), 0, 14);
        station(s, pier(-26, 3), 0, 14, 6, false);
        bridge(s, -7, -1, 0, 14);
        M.heap(s, -4, 4, 3.6);
        M.heap(s, -2, -5, 2.8);
        finish(s, 36, -14);
        s.cam(-30, 6, 30, -150, -12).cam(0, 40, 34, 180, 40).cam(-12, 16, 0, -90, 4).cam(-24, 2, 10, 180, -10);
    }

    private static void fallen(Scene s) {
        s.clear(8);
        // one length still stands, with its stair and its station; the rest is stumps, and the road lying beside them
        viaduct(s, -56, -22, 0, 20, 1, -47, -39, false, true);
        stairPier(s, pier(-56, 0), 0, 20);
        station(s, pier(-56, 2), 0, 20, 6, false);
        bridge(s, -47, -39, 0, 20);
        for (int k = 3; k <= 8; k++) {
            int px = pier(-56, k), top = 4 + Mon.pick(s, 90 + k, 8);
            M.pier(s, px, 0, 2, top, Math.min(5, top - 1));
            M.ruin(s, px - 3, -3, px + 3, 3, top - 2, 2, top + 1);
        }
        for (int x = -18; x <= 58; x++) {
            int p = Math.floorMod(x + 56, PITCH), under = p <= 4 ? 5 : M.haunch((p - 4) / 10.0, 5);
            if (n(s, x * 0.8, 77, 6.0) < 0.3) continue;
            for (int d = 0; d <= 1 + under; d++) {
                int z = 7 + d, y = s.g(x, z) + 1;
                if (s.taken(x, y, z)) continue;
                s.put(x, y, z, d == 0 ? M.flag() : M.course(d));
                if (d <= 1 || h(s, x, 3, z) < 140) s.put(x, y + 1, z, d == 0 ? M.block() : M.stone());
            }
        }
        M.heap(s, -43, 5, 3.4);
        finish(s, 60, -44);
        s.cam(-50, 8, 40, -130, -6).cam(0, 50, 56, 180, 42).cam(-50, 22, 0, -90, 4).cam(10, 4, 26, 180, 0);
    }

    private static void longRoad(Scene s) {
        s.clear(8);
        viaduct(s, -44, 40, 0, 30, 2, -11, -3, false, true);
        stairPier(s, pier(-44, 0), 0, 30);
        station(s, pier(-44, 3), 0, 30, 7, false);
        bridge(s, -11, -3, 0, 30);
        M.heap(s, -7, 5, 4.6);
        M.heap(s, -5, -6, 3.6);
        M.heap(s, -10, 9, 2.8);
        finish(s, 50, -28);
        s.cam(-40, 5, 40, -150, -24).cam(4, 60, 48, 180, 40).cam(-18, 32, 1, -90, 2).cam(-2, 32, -2, 0, 4);
    }

    private static void doubleRoad(Scene s) {
        s.clear(8);
        // the low road for carts and the high road for those in a hurry, side by side; the low one is down where they part
        viaduct(s, -52, 26, 9, 16, 1, 99, 99, false, true);
        viaduct(s, -30, 52, -9, 28, 2, 99, 99, false, true);
        stairPier(s, pier(-52, 0), 9, 16);
        station(s, pier(-30, 3), -9, 28, 7, false);
        // the way from one to the other was a stair on a wedge of masonry; its top five steps are gone
        int lx = pier(-52, 4);
        for (int z = 0; z <= 5; z++)
            for (int x = -1; x <= 1; x++) {
                int top = 17 + (5 - z);
                for (int y = Math.min(0, s.g(lx + x, z)) - 1; y < top; y++) s.put(lx + x, y, z, y % 10 == 0 ? M.band() : M.course(y));
                s.put(lx + x, top, z, M.step(s, lx + x, z, Direction.NORTH));
            }
        for (int x = -1; x <= 1; x++) s.put(lx + x, 17, 6, AIR);   // through the low road's parapet
        for (int y = 16; y <= 21; y++) s.put(lx + 2, y, 1, M.block());
        s.put(lx + 2, 22, 1, Props.WINCH.get().of(0));
        s.part("winch", lx + 2, 22, 1);
        for (int i = 0; i < 5; i++) {
            s.put(lx - 2, 20 - i % 3 + 2, 2 + i / 3, top(st(Blocks.SPRUCE_SLAB)));
            s.part("stack", lx - 2, 20 - i % 3 + 2, 2 + i / 3);
            s.part("span", lx, 23 + i, -1 - i);
        }
        for (int z = 2; z <= 3; z++) for (int y = 16; y <= 22; y++) if (!s.taken(lx - 2, y, z)) s.put(lx - 2, y, z, y >= 20 ? st(Blocks.SPRUCE_PLANKS) : M.block());
        for (int x = -1; x <= 1; x++) s.put(lx + x, 29, -6, AIR);  // and the high road's
        M.heap(s, lx, -3, 3.2);
        finish(s, 56, -40);
        s.cam(-44, 6, 46, -150, -18).cam(0, 62, 52, 180, 42).cam(lx - 6, 18, 9, -90, -10).cam(lx, 30, 3, 180, 0);
    }

    private static void whole(Scene s) {
        s.clear(8);
        viaduct(s, -60, 60, 0, 30, 2, 99, 99, true, false);
        // nothing has fallen: the gap is the drawbridge by the station, left up
        int g0 = pier(-60, 5) + 3, g1 = g0 + 5;
        for (int x = g0; x <= g1; x++) for (int z = -3; z <= 3; z++) for (int y = 22; y <= 33; y++) s.put(x, y, z, AIR);
        for (int z = -2; z <= 2; z++) for (int k = 1; k <= 5; k++) s.put(g1 + 1, 30 + k, z, k == 5 ? M.bronzeBand() : st(Blocks.SPRUCE_PLANKS));
        stairPier(s, pier(-60, 0), 0, 30);
        station(s, pier(-60, 6), 0, 30, 8, true);
        bridge(s, g0, g1, 0, 30);
        finish(s, 62, -40);
        s.cam(-50, 5, 44, -140, -24).cam(0, 70, 60, 180, 42).cam(g0 - 8, 32, 1, -90, 2).cam(pier(-60, 6), 32, -2, 0, 4);
    }

    static void init() {
        Object[][] five = {
                {1, "road_three", "The Three Arches", (dev.awaken.finds.Kind.Maker) MRoad::three, 36,
                        "Three stone arches stride across the land and stop at both ends. The middle one has lost its crown. A stub of a tower stands on the far pier, and its door is shut.",
                        "You wound out the planks the last travellers left, crossed where the crown fell, and the door of the station let go.",
                        "A waymark. It says: sea gate, nine days. A bed and a fire at every station. Keep the lamps filled.",
                        "It says this stretch was built by the people of the valley as their share. It took them eleven summers.",
                        "Scratched by a traveller: third day out. Feet bad. The stew at the last station was good."},
                {2, "road_fallen", "The Fallen Road", (dev.awaken.finds.Kind.Maker) MRoad::fallen, 60,
                        "One arch and a tower still stand. Beyond them a line of broken piers walks off across the country, with the road itself lying in the grass beside them.",
                        "You climbed the one pier that still has its stair, wound the planks across, and the station's door let go.",
                        "It says the road was kept by whoever lived nearest. When they left, nobody lived nearest.",
                        "A keeper's list of repairs, year by year. The last line reads: one arch down at the river. Will see to it in spring.",
                        "Scratched small, low on the pier: we carried the stones away for our walls. We are sorry. It was a hard winter."},
                {3, "road_long", "The Long Road", (dev.awaken.finds.Kind.Maker) MRoad::longRoad, 50,
                        "A road on two rows of arches, as tall as a hill, striding across the valley. One span is down. On the far side of the gap a tower stands on its own pier with a bronze door.",
                        "You wound the planks out across the gap, plank by plank, and walked over. The station's door let go.",
                        "It says: this road goes from the sea to the mountain and touches every town between. Walk it and you have seen everything we made.",
                        "A notice, cut large. It says: the road is free. Nobody may be turned back on it. Nobody.",
                        "The station keeper's own mark. It says: on the last day the road was full from dawn to dark, all going one of three ways. Then it was empty. I stayed to keep the lamps."},
                {4, "road_double", "The Double Road", (dev.awaken.finds.Kind.Maker) MRoad::doubleRoad, 56,
                        "Two roads on arches run side by side, one high and one low, and a broken stair once joined them. The tower with the bronze door is on the high one.",
                        "From the top of the broken stair you wound planks across to the high road. The station's door let go.",
                        "It says the high road was for those in a hurry and the low road for carts. In the end everyone was in a hurry.",
                        "It says: here the three ways parted. Up the mountain. Into the woods. Down to the dry country. People said goodbye on this stair.",
                        "Scratched on the parapet in many hands, one under another: wait for me. I waited. I waited. I am going on."},
                {5, "road_whole", "The Road That Still Runs", (dev.awaken.finds.Kind.Maker) MRoad::whole, 62,
                        "A road on two rows of arches from one edge of sight to the other, whole, a lamp burning over every pier. Its drawbridge stands up, beside a tower with a light in its roof.",
                        "You let the drawbridge down plank by plank and crossed. The station's door let go, and the fire inside was laid ready.",
                        "It says: we finished the road in the same year we learned what was under the world. We had meant it to bring people together.",
                        "It says it took four hundred years to build and was used for forty.",
                        "The newest cut. It says: I walk it end to end each year and fill what lamps I can. If you find one lit, that was me. If you find them all dark, I have stopped."}};
        for (Object[] v : five) {
            int tier = (Integer) v[0];
            Mon.kind(Family.ROAD, tier, (String) v[1], (String) v[2]).verb(Verb.PULL)
                    .lands(Group.PLAINS, Group.FOREST, Group.SNOW, Group.MOUNTAIN, Group.DESERT, Group.SWAMP).slope(18)
                    .see((String) v[5]).learned((String) v[6]).reach((Integer) v[4])
                    .air(Air.of(0xD8D2C0, 150).mote(() -> ParticleTypes.WHITE_ASH, 0.12F, 12, -0.006F).loop(() -> SoundEvents.ELYTRA_FLYING, 300, 0.05F, 0.6F))
                    .scene((dev.awaken.finds.Kind.Maker) v[3])
                    .script(() -> Lore.with(Steps.of(
                                    Swap.pull("winch", "stack", "span", top(st(Blocks.SPRUCE_SLAB))).sound(SoundEvents.CHAIN_PLACE),
                                    Then.run(c -> Mon.open(c, "door", SoundEvents.IRON_DOOR_OPEN)),
                                    Prize.at("prize")),
                            "carving", (String) v[7], (String) v[8], (String) v[9]))
                    .play(t -> { for (int i = 0; i < t.count("stack"); i++) t.use("winch", 0); t.ticks(10).use("carving", 0).use("prize", 0); })
                    .add();
        }
    }
}
