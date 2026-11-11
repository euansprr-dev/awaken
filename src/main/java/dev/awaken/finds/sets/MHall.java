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
 * The Sleepers' Halls (SPEC §64.8, family 7): where those who went in lay down. A hall under the ground with stone beds
 * down both sides. You light the lamps in the order the sleepers lay down, and the round door at the far end turns.
 * One generator; a hall is how long it is, how deep, what is left of its roof and how much water stands in it.
 */
final class MHall {
    private MHall() {}

    /**
     * {@code L}: half its length. {@code F}: its floor, below the ground. {@code roof}: 0 whole, 1 fallen in down the
     * middle, 2 gone (an open sunk court). {@code flood}: blocks of water over the floor. {@code above}: 0 the ribs of the
     * fallen vault stand over it, 1 a long roofed house stands over it, 2 only its columns show.
     */
    record Hall(int L, int F, int roof, int flood, int above, int lamps) {}

    static void hall(Scene s, Hall d, int reach) {
        int L = d.L(), F = d.F(), aisle = F + 5;                   // aisles are ceiled five over the floor; the nave goes up to the vault
        BlockState root = st(Blocks.DARK_OAK_WOOD);
        // -- the ground it is dug into: a terrace over the whole of it, walled wherever the land falls away
        G.platform(s, G.box(-L - 8 + F, -14, L + 11, 14), 0, reach);
        s.clear(reach);
        // -- the hall: floor at F, aisles ceiled low, the nave open to one course of vault under the turf
        M.mass(s, -L - 1, F - 1, -11, L + 1, -1, 11);
        air(s, -L, F + 1, -3, L, -2, 3);
        air(s, -L, F + 1, -10, L, aisle, -5);
        air(s, -L, F + 1, 5, L, aisle, 10);
        air(s, -L, F + 1, -4, L, aisle - 1, -4);
        air(s, -L, F + 1, 4, L, aisle - 1, 4);
        for (int x = -L; x <= L; x++)
            for (int z = -10; z <= 10; z++) s.put(x, F, z, Math.abs(z) <= 2 ? M.mosaic() : Math.floorMod(x + z, 7) == 0 ? M.block() : M.flag());
        for (int x = -L + 3; x <= L - 3; x += 6)
            for (int z = -4; z <= 4; z += 8) M.column(s, x, z, F + 1, aisle - 1);
        for (int x = -L; x <= L; x++) for (int z = -4; z <= 4; z += 8) s.put(x, aisle, z, Math.floorMod(x + L - 3, 6) == 0 ? M.block() : M.band());
        // -- the roof: fallen in down the middle (a ragged slot open to the sky), or all of it gone
        if (d.roof() > 0)
            for (int x = -L + 6; x <= L - 6; x++)
                for (int z = -10; z <= 10; z++) {
                    double edge = d.roof() == 2 ? 9.2 + n(s, x * 0.9, 40, 4.0) * 2.0 : 1.2 + n(s, x * 1.3, 40, 3.5) * 2.6 - Math.max(0, (Math.abs(x) - (L - 12)) * 0.5);
                    if (Math.abs(z) > edge) continue;
                    for (int y = Math.abs(z) <= 3 ? -1 : aisle + 1; y <= 0; y++) s.put(x, y, z, AIR);
                    if (Math.abs(z) > 3 && Math.abs(z) != 4) s.put(x, aisle + 1, z, AIR);
                    if (Math.abs(z) <= 3) {
                        if (h(s, x, 3, z) < 30) s.put(x, F + 1, z, h(s, x, 4, z) < 128 ? M.rubble() : st(Olden.WORN.get()));
                        else if (h(s, x, 3, z) < 44 && d.flood() == 0) { s.put(x, F, z, WATER); s.put(x, F - 1, z, M.block()); }
                    }
                }
        if (d.roof() == 2) for (int x = -L + 3; x <= L - 3; x += 6) for (int z = -4; z <= 4; z += 8) {   // its columns stand on, into the daylight
            if (h(s, x, 8, z) < 70) continue;
            for (int y = aisle; y <= 6 + h(s, x, 9, z) % 6; y++) s.put(x, y, z, M.pillar(Direction.Axis.Y));
            s.put(x, 7 + h(s, x, 9, z) % 6, z, M.capital());
        }
        // -- water standing in it
        for (int k = 1; k <= d.flood(); k++)
            for (int x = -L; x <= L; x++)
                for (int z = -10; z <= 10; z++) if (s.get(x, F + k, z) != null && s.get(x, F + k, z).isAir()) s.put(x, F + k, z, WATER);
        // -- the way down, as a building: a stair between cheek walls with a coped parapet, a porch over its head
        int n = -F, x0 = -L - 1 - n;                               // the stair's top is n blocks west of the hall's end wall
        for (int i = 0; i < n; i++) {
            int x = x0 + i;
            for (int z = -3; z <= 3; z++) for (int y = -i - 1; y <= 0; y++) s.put(x, y, z, Math.abs(z) == 3 || y == -i - 1 ? M.course(y) : AIR);
            for (int z = -2; z <= 2; z++) s.put(x, -i, z, M.step(s, x, z, Direction.WEST));
            for (int z = -3; z <= 3; z += 6) {                      // the parapet: posts every third, a low wall between, never a gap
                s.put(x, 0, z, M.block());
                s.put(x, 1, z, i % 3 == 0 ? M.block() : M.wall());
                if (i % 3 == 0) s.put(x, 2, z, M.blockSlab());
            }
        }
        for (int x = x0 - 6; x <= -L + 1; x++)                     // a paved apron round the well and the porch, so the ground reads as made
            for (int z = -6; z <= 6; z++)
                if (!s.taken(x, 0, z) && !s.taken(x, 1, z)) s.put(x, 0, z, Math.floorMod(x * 3 + z, 5) == 0 ? M.block() : M.flag());
        for (int z = -3; z <= 3; z += 6)                           // the porch: four columns, a lintel, a pediment
            for (int x = x0 - 4; x <= x0 - 1; x += 3) {
                M.column(s, x, z, 1, 5);
            }
        for (int x = x0 - 5; x <= x0; x++)
            for (int z = -4; z <= 4; z++) {
                boolean edge = x == x0 - 5 || x == x0 || Math.abs(z) == 4;
                s.put(x, 6, z, edge ? M.band() : M.block());
                if (Math.abs(z) <= 3) s.put(x, 7, z, Math.abs(z) == 3 ? M.blockSlab() : M.block());
                if (Math.abs(z) <= 1) s.put(x, 8, z, z == 0 ? M.block() : M.blockSlab());
            }
        s.put(x0 - 3, 5, 0, M.lamp(true));
        for (int z = -2; z <= 2; z++) for (int y = F + 1; y <= aisle; y++) s.put(-L - 1, y, z, Math.abs(z) == 2 ? M.bronzeBand() : y == aisle ? M.band() : AIR);
        for (int z = -2; z <= 2; z++) s.put(-L - 1, aisle + 1, z, z == 0 ? M.seal() : M.panel());
        Mon.carving(s, -L, F + 2, -3);
        // -- the beds: along the outer walls, a stone sleeper on each under a canopy
        int beds = 0;
        for (int x = -L + 9; x <= L - 9; x += 6)
            for (int side = -1; side <= 1; side += 2) {
                int z = side * 9, by = F + 1 + d.flood();
                for (int dx = -1; dx <= 1; dx++) for (int y = F + 1; y <= by; y++) s.put(x + dx, y, z, M.block());
                s.put(x - 1, by + 1, z, stair(M.blockStairs(), Direction.WEST));     // the head, on its pillow
                s.put(x, by + 1, z, M.blockSlab());
                s.put(x + 1, by + 1, z, M.blockSlab());
                for (int dx = -2; dx <= 2; dx += 4) for (int y = F + 1; y <= by + 2; y++) s.put(x + dx, y, z, M.wall());
                for (int dx = -2; dx <= 2; dx++) s.put(x + dx, by + 3 > aisle ? aisle : by + 3, z, dx == 0 ? M.band() : M.blockSlab());
                s.put(x, by + 1, side * 10, M.panel());
                if (beds++ % 3 == 0 && d.flood() == 0) s.put(x + 2, F + 1, side * 8, candles(1 + beds % 3, true));
            }
        // -- the lamps: on posts at the aisles' edge, to be lit near to far, left and then right
        for (int i = 0; i < d.lamps(); i++) {
            int x = -L + 12 + (i / 2) * (2 * (L - 12) / Math.max(1, d.lamps() / 2 - 1)), z = i % 2 == 0 ? -6 : 6;
            for (int y = F + 1; y <= F + 1 + d.flood(); y++) s.put(x, y, z, M.wall());
            s.put(x, F + 2 + d.flood(), z, M.lamp(false));
            s.part("lamp", x, F + 2 + d.flood(), z);
        }
        // -- the round door at the far end, and past it the one empty bed
        air(s, L + 2, F + 1, -3, L + 8, F + 6, 3);
        for (int x = L + 2; x <= L + 8; x++) for (int z = -3; z <= 3; z++) s.put(x, F, z, M.mosaic());
        M.shell(s, L + 1, F, -4, L + 9, F + 7, 4);
        M.mass(s, L + 1, F + 7, -4, L + 9, F + 8, 4);
        for (int y = F + 1; y <= F + 6; y++)
            for (int z = -3; z <= 3; z++) {
                double r = Math.sqrt((y - F - 3.5) * (y - F - 3.5) + z * z);
                if (r > 3.1) continue;
                s.put(L + 1, y, z, r > 2.2 ? M.seal() : r < 1.2 ? M.seal() : M.bronze());
                s.part("door", L + 1, y, z);
            }
        for (int dx = -1; dx <= 1; dx++) s.put(L + 6 + dx, F + 1, 0, M.block());
        s.put(L + 6, F + 2, 0, M.seal());
        s.part("prize", L + 6, F + 2, 0);
        s.put(L + 6, F + 6, 0, M.lamp(true));
        Mon.carving(s, L + 8, F + 2, 2);
        Mon.carving(s, L / 2 - 3, F + 2, -11);
        // -- what shows above the ground (SPEC §64.3, the skyline rule: the first Hall was a stairwell in a meadow)
        if (d.above() == 0) {
            // the ribs of the vault that fell: piers either side of the slot, arching in, most of them broken at the crown
            for (int x = -L + 6; x <= L - 6; x += 12) {
                boolean wholeRib = Mon.pick(s, 60 + x, 2) == 0;     // about half of them still meet at the crown
                for (int side = -1; side <= 1; side += 2) {
                    for (int dx = -1; dx <= 1; dx++) for (int z = 6; z <= 7; z++) for (int y = 0; y <= 11; y++) s.put(x + dx, y, side * z, y % 5 == 0 ? M.band() : dx == 0 ? M.block() : M.course(y));
                    for (int dx = -1; dx <= 1; dx++) s.put(x + dx, 12, side * 7, dx == 0 ? M.block() : M.blockSlab());
                    int reachIn = wholeRib ? 5 : 2 + Mon.pick(s, 70 + x + side, 3);
                    for (int k = 0; k <= reachIn; k++) {
                        int z = side * (5 - k), y = 12 + (int) Math.round(Math.sqrt(Math.max(0, 36 - (5 - k) * (5 - k))) / 6 * 5);
                        s.put(x, y, z, M.block());
                        s.put(x, y - 1, z, M.stone());
                        if (k < reachIn) s.put(x, y - 2, z, stairT(M.blockStairs(), side > 0 ? Direction.SOUTH : Direction.NORTH));
                    }
                }
            }
        } else if (d.above() == 1) {
            // a long house over the nave: low walls with windows, a pitched roof of stone, its gables on the hall's ends
            // it is the nave's own upper storey: the vault is open to it, so daylight comes down from its windows
            air(s, -L + 3, -1, -3, L - 3, 6, 3);
            for (int x = -L + 2; x <= L - 2; x++) for (int side = -1; side <= 1; side += 2) s.put(x, 0, side * 4, M.band());
            for (int x = -L + 2; x <= L - 2; x++)
                for (int side = -1; side <= 1; side += 2) {
                    boolean pilaster = Math.floorMod(x + L, 6) == 3;
                    for (int y = 0; y <= 6; y++) s.put(x, y, side * 5, pilaster ? M.block() : y >= 2 && y <= 4 && Math.floorMod(x + L, 6) != 0 ? AIR : y == 5 ? M.band() : M.course(y));
                    for (int k = 0; k <= 5; k++) s.put(x, 7 + (k + 1) / 2, side * (6 - k), k % 2 == 0 ? M.blockSlab() : top(M.blockSlab()));
                    s.put(x, 6, side * 6, stairT(M.blockStairs(), side > 0 ? Direction.NORTH : Direction.SOUTH));
                }
            for (int x = -L + 2; x <= L - 2; x++) s.put(x, 10, 0, Math.floorMod(x, 4) == 0 ? M.block() : M.blockSlab());
            for (int x = -L + 2; x <= L - 2; x += 2 * L - 4)
                for (int z = -5; z <= 5; z++) for (int y = 0; y <= 9 - Math.abs(z) / 2 * 1; y++) s.put(x, y, z, y == 5 ? M.band() : M.course(y));
            M.giant(s, x0 - 3, 1, -9, Direction.SOUTH, 15, false, 0, false);
            M.giant(s, x0 - 3, 1, 9, Direction.NORTH, 15, false, 0, false);
        }
        M.finish(s, F + 7, 36);
        // -- growth: roots through the roof and down the columns, great trees over it, lichen in the walls, pilgrims' candles
        if (d.roof() > 0 || d.above() == 0)
            for (int i = 0; i < Math.max(2, L / 8); i++) {
                int rx = -L + 12 + i * (2 * L - 24) / Math.max(1, L / 8 - 1), side = i % 2 == 0 ? -1 : 1, rz = side * 2;
                if (d.roof() == 0) { rz = side * 13; }
                if (d.roof() > 0) {
                    limb(s, rx, 1, rz, rx, -1, rz, root);
                    limb(s, rx, -1, rz, rx + 4, aisle, side * 4, root);
                    limb(s, rx + 4, aisle, side * 4, rx + 4 + side, F + 1 + d.flood(), side * 5, root);
                }
                int tz = side * 13;                                 // outside the ribs: the trees frame the hall, they do not roof it
                if (!s.taken(rx + 3, 1, tz) && !s.taken(rx + 3, 3, tz)) giant(s, rx + 3, tz, 1, 10 + i % 3 * 2, 1, st(Blocks.DARK_OAK_LOG), leaf(Blocks.DARK_OAK_LEAVES), 3.0);
                if (d.roof() > 0) limb(s, rx, 1, rz, rx + 3, 1, tz - side, root);
            }
        for (int x = -L; x <= L; x++)
            for (int y = F + 1; y <= aisle; y++)
                for (int z = -11; z <= 11; z += 22) {
                    BlockState b = s.get(x, y, z);
                    if (b != null && n(s, x * 1.1 + y, z + y * 2.0, 3.0) > 0.66 && Olden.is(b) && b.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                            && s.partAt(s.rel(x, y, z)) == null) s.put(x, y, z, M.lichen());
                }
        if (d.flood() == 0) {
            for (int[] k : new int[][] {{L - 3, -2}, {L - 2, 2}, {L - 1, -1}, {-L + 2, 2}}) s.put(k[0], F + 1, k[1], candles(1 + (k[0] & 1) + (k[1] & 1), true));
            stilled(s, L - 4, F + 1, -1, 1, Direction.EAST);
            stilled(s, L - 3, F + 1, 1, 3, Direction.EAST);
        }
        for (int x = -L + 12; x <= L - 12; x += 24) s.put(x, F + 4, 0, light(8));
        path(s, x0 - 6, 18, x0 - 6, 6, 1, st(Blocks.COARSE_DIRT), st(Blocks.PODZOL));
        wild(s, L + 14, reach - 1, 14);
        s.cam(x0 - 16, 9, 20, -125, 8).cam(L / 2, L + 10, L + 6, 150, 44).cam(x0 - 10, 3, 0, -90, 12).cam(-L + 4, F + 2, 0, -90, -6);
    }

    static void init() {
        Object[][] five = {
                {1, "hall_small", "The Little Rest", new Hall(15, -7, 1, 0, 0, 4), 43,
                        "A porch of four stone columns in the wood, and under it a stair going down. Two ribs of a broken vault stand over a slot in the ground behind it.",
                        "You lit the four lamps in the order the sleepers lay down. The round door turned, and behind it one bed was empty.",
                        "It says this was the first place anyone tried it. Nine of them lay down here to see whether it could be done. All nine are still here.",
                        "Light them the way they lay down: the nearest first, the left side and then the right.",
                        "It says: the tenth bed was mine. I was to follow when I had seen that they were well. I am still deciding."},
                {2, "hall_fallen", "The Fallen Hall", new Hall(24, -8, 2, 0, 2, 6), 53,
                        "A long sunk court open to the sky, its roof gone and its columns still standing up out of the ground. Stone beds line both sides under drifts of leaves.",
                        "You lit the six lamps in their order, in the open air. The round door at the far end still turned.",
                        "It says the roof came down in one night, and not one of the sleepers woke. That is how the watchers knew they were truly gone under.",
                        "Light them the way they lay down: the nearest first, the left side and then the right, and so on to the far end.",
                        "It says: we could not lift the stones off them. We swept the beds every spring instead."},
                {3, "sleepers_hall", "The Sleepers' Hall", new Hall(30, -10, 1, 0, 0, 8), 61,
                        "The ribs of a broken vault stand over a long hollow in the forest floor, with roots hanging into it. Under a porch a stair goes down to a hall of columns where stone sleepers lie in rows.",
                        "You lit the lamps in the order they lay down. The round door turned, and behind it one bed was empty.",
                        "Light them the way the sleepers lay down: the nearest first, the left side and then the right, and so on to the far end.",
                        "It says they were not dying. They chose to dream until the world was safe to wake in. It does not say who would tell them.",
                        "A watcher's tally, one mark for every year someone sat up with them. It stops at four hundred and six."},
                {4, "hall_drowned", "The Drowned Hall", new Hall(27, -10, 1, 2, 0, 6), 58,
                        "A porch and a stair going down into still black water. Under the broken vault the tops of stone beds stand out of it like stepping stones, each with its sleeper.",
                        "You waded the length of it and lit the lamps on their posts in order. The round door turned in the water.",
                        "It says the spring broke through in the watchers' time. They raised every bed by two courses, by hand, in the dark, without waking one sleeper.",
                        "Light them the way they lay down: the nearest first, the left side and then the right, and so on to the far end.",
                        "It says: the water is still rising, a finger's width in a lifetime. Someone will have to raise them again. Not us."},
                {5, "hall_whole", "The Hall Where One Still Sleeps", new Hall(36, -10, 0, 0, 1, 8), 67,
                        "A long stone house with a pitched roof and a window in every bay, whole, in a clearing. Two hooded giants stand either side of the porch where the stair goes down.",
                        "Daylight falls into it from the high windows. You lit the eight lamps in their order. The round door turned, and behind it one bed was not empty.",
                        "It says this hall was kept. Somebody has swept it within living memory. They did not cut their name.",
                        "Light them the way they lay down: the nearest first, the left side and then the right, and so on to the far end.",
                        "Cut beside the last bed. It says: I am the watcher. I have decided. Whoever lights the lamps after me, sit a while. Then go and live."}};
        for (Object[] v : five) {
            int tier = (Integer) v[0];
            Hall d = (Hall) v[3];
            int reach = (Integer) v[4];
            Mon.kind(Family.HALL, tier, (String) v[1], (String) v[2]).verb(Verb.LIGHT)
                    .lands(Group.DARK, Group.FOREST, Group.SWAMP, Group.JUNGLE).slope(16)
                    .see((String) v[5]).learned((String) v[6]).reach(reach)
                    .air(Air.of(0x1E3A38, 44).below().mote(() -> ParticleTypes.GLOW, 0.4F, 7, -0.004F).loop(() -> SoundEvents.AMBIENT_CAVE.value(), 360, 0.4F, 0.9F))
                    .scene(s -> hall(s, d, reach))
                    .script(() -> Lore.with(Steps.of(
                                    Light.all("lamp").inOrder(),
                                    Then.run(c -> {
                                        Mon.open(c, "door", SoundEvents.IRON_DOOR_OPEN);
                                        c.sound(c.pos("prize", 0), SoundEvents.BEACON_ACTIVATE, 3.0F, 0.5F);
                                    }),
                                    Prize.at("prize")),
                            // the line that gives the order is the one by the way in
                            "carving", ((String) v[8]).startsWith("Light them") ? (String) v[8] : (String) v[7], ((String) v[8]).startsWith("Light them") ? (String) v[7] : (String) v[8], (String) v[9]))
                    .play(t -> { t.use("carving", 0).hold(net.minecraft.world.item.Items.FLINT_AND_STEEL); for (int i = 0; i < d.lamps(); i++) t.use("lamp", i); t.ticks(10).use("prize", 0); })
                    .add();
        }
    }
}
