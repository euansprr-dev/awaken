package dev.awaken.anims.station;

import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.joml.Quaternionf;

/**
 * The crafting table scene (SPEC §24, "Station scenes"). Its screen is vanilla's own container screen with the table's
 * squares as the slots under the cursor, so a gesture sends the clicks vanilla's crafting grid sends: these tests make
 * those clicks on a real crafting menu and check what the grid gets, and check that the view frames every square in the
 * room above the strip and that nothing under the strip picks a square.
 */
public final class StationTests {
    private StationTests() {}

    public static void register() {
        AwakenTests.register("anims_station_cells", 20, StationTests::cells);
        AwakenTests.register("anims_station_frame", 20, StationTests::frame);
        AwakenTests.register("anims_station_panel_click", 20, StationTests::panelClick);
        AwakenTests.register("anims_station_right_click", 20, StationTests::rightClick);
        AwakenTests.register("anims_station_right_drag", 20, StationTests::rightDrag);
        AwakenTests.register("anims_station_left_drag", 20, StationTests::leftDrag);
        AwakenTests.register("anims_station_shift_result", 20, StationTests::shiftResult);
        AwakenTests.register("stations_bench_view", 20, StationTests::benchView);
        AwakenTests.register("stations_hand_never_hides", 20, StationTests::handNeverHides);
        AwakenTests.register("stations_presence", 40, StationTests::presence);
        AwakenTests.register("stations_living_slots", 20, StationTests::livingSlots);
        AwakenTests.register("stations_workstations", 20, StationTests::workstations);
        AwakenTests.register("touch_every_block", 20, StationTests::touchEveryBlock);
    }

    private static final BlockPos TABLE = new BlockPos(0, 64, 0);
    private static final double EYE = 1.62;
    // gui sizes: Euan's MacBook at his scale (about 424×238), the smallest the game allows, and smaller scales
    private static final int[][] SCREENS = {{424, 238}, {320, 240}, {480, 270}, {640, 360}, {720, 450}, {960, 540}, {1280, 720}};

    /** The station as the scene sets it up for a player standing at (px, pz): the way in snapped to an axis. */
    private static StationView.Station station(double px, double pz) {
        double dx = TABLE.getX() + 0.5 - px, dz = TABLE.getZ() + 0.5 - pz;
        return Math.abs(dx) >= Math.abs(dz) ? new StationView.Station(TABLE, StationView.Kind.TABLE, Math.signum(dx), 0)
                : new StationView.Station(TABLE, StationView.Kind.TABLE, 0, Math.signum(dz));
    }

    /**
     * Each ingredient sits centred on its drawn square of the table top, whichever way you face the table: the squares'
     * middles are pixels 5, 8 and 11 of the top's 16, row 0 the far one; the result lies on the right border, everything
     * inside the table's own block (nothing can reach into a wall), and a ray down onto each picks it.
     */
    static void cells(GameTestHelper h) {
        double[] p = new double[3];
        Quaternionf down = StationView.rotation(0, 90, new Quaternionf());
        int[][] ways = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] way : ways) {
            StationView.Station s = new StationView.Station(TABLE, StationView.Kind.TABLE, way[0], way[1]);
            String facing = "facing " + way[0] + "," + way[1];
            boolean[] seen = new boolean[9];
            for (int i = 0; i < 10; i++) {
                StationView.spot(s, i, p, 0);
                double px = (p[0] - TABLE.getX()) * 16, pz = (p[2] - TABLE.getZ()) * 16;
                double reach = Math.max(StationView.reach(s, i, 0), StationView.reach(s, i, 2)) * 16;
                h.assertTrue(px - reach >= 0 && px + reach <= 16 && pz - reach >= 0 && pz + reach <= 16 && p[1] > TABLE.getY() + 1,
                        "slot " + i + " stays on the table's own top " + facing);
                if (i == 0) {
                    // to your right as you face the table (vanilla's own side of the grid), level with the middle row
                    double across = (p[0] - TABLE.getX() - 0.5) * s.rx() + (p[2] - TABLE.getZ() - 0.5) * s.rz();
                    double far = (p[0] - TABLE.getX() - 0.5) * s.fx() + (p[2] - TABLE.getZ() - 0.5) * s.fz();
                    h.assertTrue(across > StationView.GRID_HALF + StationView.RESULT_HALF && Math.abs(far) < 1e-6,
                            "the result lies on the right border, past the grid " + facing);
                } else {
                    int cx = (int) Math.round(px), cz = (int) Math.round(pz);
                    h.assertTrue(Math.abs(px - cx) < 1e-6 && Math.abs(pz - cz) < 1e-6 && (cx == 5 || cx == 8 || cx == 11) && (cz == 5 || cz == 8 || cz == 11),
                            "slot " + i + " sits on a drawn square's middle " + facing + " (at " + px + "," + pz + ")");
                    seen[(cx - 5) / 3 * 3 + (cz - 5) / 3] = true;
                    // row 0 is the far one
                    double far = (p[0] - TABLE.getX() - 0.5) * s.fx() + (p[2] - TABLE.getZ() - 0.5) * s.fz();
                    int row = (i - 1) / 3;
                    h.assertTrue(Math.abs(far - (1 - row) * StationView.CELL) < 1e-6, "slot " + i + " is in row " + row + " " + facing);
                }
                // straight down from over it, a click picks it
                StationView.Layout l = StationView.layout(424, 238, 2, false);
                int got = StationView.pick(s, l, p[0], p[1] + 0.8, p[2], down, 70, 424 / 238F, l.rx0() + (l.rx1() - l.rx0()) / 2.0, 119, 424, 238);
                h.assertTrue(got == i, "a click straight down on slot " + i + " picks it " + facing + " (got " + got + ")");
            }
            for (boolean b : seen) h.assertTrue(b, "the nine squares each get one slot " + facing);
        }
        h.succeed();
    }

    /** Every square and the result lie inside the room above the hint and strip, and each picks itself. */
    static void frame(GameTestHelper h) {
        float[] view = new float[3];
        double[] p = new double[3], q = new double[2];
        Quaternionf rot = new Quaternionf();
        int[][] ways = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] scr : SCREENS) {
            int w = scr[0], gh = scr[1];
            float aspect = (float) w / gh;
            for (boolean sides : new boolean[]{true, false}) {
                StationView.Layout l = StationView.layout(w, gh, 2, sides);
                h.assertTrue(l.ry1() < l.hintY() && l.hintY() < l.py0(), "the room is above the hint, the hint above the strip (" + w + "x" + gh + ")");
                for (int[] way : ways) {
                    for (double d : new double[]{1.0, 1.5, 2.0, 3.0, 4.0}) {
                        for (double side : new double[]{-0.4, 0, 0.4}) {
                            // standing d from the table's middle on one side, a little to one side of it
                            double ex = TABLE.getX() + 0.5 - way[0] * d - way[1] * side, ez = TABLE.getZ() + 0.5 - way[1] * d + way[0] * side;
                            double ey = TABLE.getY() + EYE;
                            StationView.Station s = station(ex, ez);
                            StationView.frame(s, ex, ey, ez, l, w, gh, aspect, StationView.MAX_FOV, view);
                            String at = w + "x" + gh + (sides ? " with JEI" : "") + " from " + d + " (" + side + ")";
                            h.assertTrue(view[2] < StationView.MAX_FOV - 0.01F, "the view frames the table without opening past the most at " + at);
                            StationView.rotation(view[0], view[1], rot);
                            for (int i = 0; i < 10; i++) {
                                StationView.spot(s, i, p, 0);
                                double ma = StationView.reach(s, i, 0) - 0.005, mu = 0, mi = StationView.reach(s, i, 2) - 0.005;
                                for (int c = 0; c < 8; c++) {
                                    double a = (c & 1) == 0 ? -ma : ma, up = (c & 2) == 0 ? -mu : mu, in = (c & 4) == 0 ? -mi : mi;
                                    boolean seen = StationView.project(p[0] + s.rx() * a + s.fx() * in, p[1] + up, p[2] + s.rz() * a + s.fz() * in,
                                            ex, ey, ez, rot, view[2], aspect, w, gh, q);
                                    h.assertTrue(seen && l.inRoom(q[0], q[1]), "slot " + i + " lies inside the room at " + at
                                            + " (at " + Math.round(q[0]) + "," + Math.round(q[1]) + ")");
                                }
                                StationView.project(p[0], p[1], p[2], ex, ey, ez, rot, view[2], aspect, w, gh, q);
                                int got = StationView.pick(s, l, ex, ey, ez, rot, view[2], aspect, q[0], q[1], w, gh);
                                h.assertTrue(got == i, "a click on slot " + i + " picks it at " + at + " (got " + got + ")");
                            }
                        }
                    }
                }
            }
        }
        h.succeed();
    }

    /**
     * The bench view (SPEC §63.1): a table's own view is the same from wherever you stand on one side of it, it looks
     * down on the grid from over the near edge, every slot lies inside the room at seven window sizes with and without
     * side panels, and a click on a slot's middle picks it.
     */
    static void benchView(GameTestHelper h) {
        float[] view = new float[3];
        double[] p = new double[3], q = new double[2], eye = new double[3], eye2 = new double[3];
        Quaternionf rot = new Quaternionf();
        int[][] ways = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] way : ways) {
            // twenty standing places on this side, one to four and a half blocks back and to either hand: one station, one view
            StationView.Station first = null;
            for (double d : new double[]{1.0, 1.6, 2.4, 3.3, 4.5}) {
                for (double side : new double[]{-0.9, -0.3, 0.3, 0.9}) {
                    if (Math.abs(side) >= d) continue;
                    StationView.Station s = station(TABLE.getX() + 0.5 - way[0] * d - way[1] * side, TABLE.getZ() + 0.5 - way[1] * d + way[0] * side);
                    if (first == null) first = s;
                    h.assertTrue(s.equals(first), "every place on one side works the table the same way (" + d + ", " + side + ")");
                }
            }
            StationView.benchEye(first, 0, eye);
            double back = -((eye[0] - TABLE.getX() - 0.5) * first.fx() + (eye[2] - TABLE.getZ() - 0.5) * first.fz()), up = eye[1] - TABLE.getY() - 1;
            double angle = Math.toDegrees(Math.atan2(up, back));
            h.assertTrue(back > 0.5 && angle > 50 && angle < 58, "the first view is over the near edge, looking down about 54 degrees (" + angle + ")");
            for (int b = 1; b < StationView.BENCHES; b++) {
                StationView.benchEye(first, b, eye2);
                h.assertTrue(eye2[1] < eye[1] + 1e-9 && eye2[1] > TABLE.getY() + 1.5, "view " + b + " is lower, and still over the top");
            }
            for (int[] scr : SCREENS) {
                int w = scr[0], gh = scr[1];
                float aspect = (float) w / gh;
                for (boolean sides : new boolean[]{true, false}) {
                    StationView.Layout l = StationView.layout(w, gh, 2, sides);
                    for (int b = 0; b < StationView.BENCHES; b++) {
                        StationView.benchEye(first, b, eye);
                        StationView.frame(first, eye[0], eye[1], eye[2], l, w, gh, aspect, StationView.MAX_FOV, view);
                        String at = "view " + b + " at " + w + "x" + gh + (sides ? " with JEI" : "");
                        h.assertTrue(view[2] < StationView.MAX_FOV - 0.01F, "the bench view frames the table without opening past the most, " + at);
                        StationView.rotation(view[0], view[1], rot);
                        for (int i = 0; i < 10; i++) {
                            StationView.spot(first, i, p, 0);
                            double ma = StationView.reach(first, i, 0) - 0.005, mi = StationView.reach(first, i, 2) - 0.005;
                            for (int c = 0; c < 4; c++) {
                                double a = (c & 1) == 0 ? -ma : ma, in = (c & 2) == 0 ? -mi : mi;
                                boolean seen = StationView.project(p[0] + first.rx() * a + first.fx() * in, p[1], p[2] + first.rz() * a + first.fz() * in,
                                        eye[0], eye[1], eye[2], rot, view[2], aspect, w, gh, q);
                                h.assertTrue(seen && l.inRoom(q[0], q[1]), "slot " + i + " lies inside the room, " + at);
                            }
                            StationView.project(p[0], p[1], p[2], eye[0], eye[1], eye[2], rot, view[2], aspect, w, gh, q);
                            int got = StationView.pick(first, l, eye[0], eye[1], eye[2], rot, view[2], aspect, q[0], q[1], w, gh);
                            h.assertTrue(got == i, "a click on slot " + i + " picks it, " + at + " (got " + got + ")");
                        }
                    }
                }
            }
        }
        h.assertTrue(StationView.glideTicks(0.5) == 5 && StationView.glideTicks(4.5) == 9, "the view takes 5 ticks from beside the table to 9 from 4.5 blocks");
        h.succeed();
    }

    /**
     * The carrying hand (SPEC §63.2): with a stack held over any slot, at seven window sizes and for either main hand,
     * the arm's far end is under the inventory strip or off the frame (no sleeve end in view), the arm never lies over
     * the result, and the only squares under the sleeve are nearer to you than the one the stack is over (they are then
     * shown through it).
     */
    static void handNeverHides(GameTestHelper h) {
        float[] view = new float[3];
        double[] p = new double[3], q = new double[2], eye = new double[3], arm = new double[6], tmp = new double[3];
        Quaternionf rot = new Quaternionf();
        double scale = StationView.FIST / (StationView.FIST_SQUARES * StationView.CELL), half = StationView.FIST / 2 / scale;
        StationView.Station s = new StationView.Station(TABLE, StationView.Kind.TABLE, 0, 1);
        StationView.benchEye(s, 0, eye);
        int most = 0;
        for (int[] scr : SCREENS) {
            int w = scr[0], gh = scr[1];
            float aspect = (float) w / gh;
            for (boolean sides : new boolean[]{true, false}) {
                StationView.Layout l = StationView.layout(w, gh, 1, sides);
                StationView.frame(s, eye[0], eye[1], eye[2], l, w, gh, aspect, StationView.MAX_FOV, view);
                StationView.rotation(view[0], view[1], rot);
                double mostAcross = StationView.acrossMost(s, l, eye[0], eye[1], eye[2], rot, view[2], aspect, w, gh, tmp);
                for (int side = -1; side <= 1; side += 2) {
                    for (int i = 0; i < 10; i++) {
                        String at = "slot " + i + " at " + w + "x" + gh + (sides ? " with JEI" : "") + (side > 0 ? ", right hand" : ", left hand");
                        StationView.spot(s, i, p, 0);
                        StationView.carry(s, p[0], p[1] + StationView.HOVER, p[2], side, mostAcross, arm);
                        float stretch = StationView.stretch(arm, scale, l, eye[0], eye[1], eye[2], rot, view[2], aspect, w, gh, q);
                        double len = StationView.ARM_BACK * stretch / scale;
                        h.assertTrue(stretch >= 1 && stretch <= StationView.STRETCH_MOST + 1e-4, "the arm is drawn at most " + StationView.STRETCH_MOST + " times its length, " + at);
                        boolean seen = StationView.project(arm[0] + arm[3] * len, arm[1] + arm[4] * len, arm[2] + arm[5] * len, eye[0], eye[1], eye[2], rot, view[2], aspect, w, gh, q);
                        h.assertTrue(!seen || StationView.hidden(l, q[0], q[1], w, gh), "the arm's far end is under the strip or off the frame, " + at
                                + " (at " + Math.round(q[0]) + "," + Math.round(q[1]) + ", strip from " + l.py0() + ")");
                        int row = i == 0 ? 1 : (i - 1) / 3, under = 0;
                        for (int o = 0; o < 10; o++) {
                            if (o == i || !StationView.sleeveOver(s, o, arm, len, half, eye[0], eye[1], eye[2], rot, view[2], aspect, w, gh, tmp, q)) continue;
                            h.assertTrue(o != 0, "the arm never lies over the result, " + at);
                            h.assertTrue((o - 1) / 3 > row, "only squares nearer to you than the stack are under the sleeve (" + o + "), " + at);
                            under++;
                        }
                        most = Math.max(most, under);
                    }
                }
            }
        }
        h.assertTrue(most <= 2, "at most two squares are ever under the sleeve (" + most + ")");
        h.succeed();
    }

    /**
     * Presence (SPEC §63.5): a player who opens a crafting table's menu just after using the table is marked as at it,
     * with what lies on it; a slot they change is passed on as a beat, at most one every three ticks; a menu that is not
     * the station's, or one opened without using a station, marks nobody; shutting the menu takes the mark away. A box
     * shows its six largest stacks, one of each kind of thing, biggest first.
     */
    static void presence(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        CraftingMenu m = menu(h, p);
        StationPresence.Sent sent = new StationPresence.Sent();
        h.assertTrue(StationPresence.kindOf(Blocks.CRAFTING_TABLE.defaultBlockState()) == StationPresence.BENCH
                && StationPresence.kindOf(Blocks.FURNACE.defaultBlockState()) == StationPresence.KNEEL
                && StationPresence.kindOf(Blocks.CHEST.defaultBlockState()) == StationPresence.BOX
                && StationPresence.kindOf(Blocks.BARREL.defaultBlockState()) == StationPresence.BOX
                && StationPresence.kindOf(Blocks.STONE.defaultBlockState()) == StationPresence.NONE, "each station is worked its own way");
        h.assertTrue(StationPresence.menuFits(StationPresence.BENCH, m) && !StationPresence.menuFits(StationPresence.KNEEL, m)
                && !StationPresence.menuFits(StationPresence.BOX, m), "a crafting menu is a table's and nothing else's");
        // at the table, with a plank on a square
        m.getSlot(5).set(new ItemStack(Items.OAK_PLANKS, 3));
        java.util.List<ItemStack> first = StationPresence.shown(StationPresence.BENCH, m);
        h.assertTrue(first.size() == 10 && first.get(5).is(Items.OAK_PLANKS) && first.get(5).getCount() == 3, "others see the ten slots of a table");
        p.setData(StationPresence.AT, new StationPresence.At(StationPresence.BENCH, h.absolutePos(BlockPos.ZERO), (byte) 0, (byte) 1, 0, first));
        sent.shown = first;
        h.assertTrue(StationPresence.tick(p, sent) && p.getData(StationPresence.AT).beats() == 0, "nothing changed: no beat");
        // a slot is changed: a beat with that slot; a second change straight after is seen but passes no beat
        m.getSlot(2).set(new ItemStack(Items.OAK_PLANKS, 1));
        h.assertTrue(StationPresence.tick(p, sent), "still at the table");
        StationPresence.At at = p.getData(StationPresence.AT);
        h.assertTrue(at.beats() == 1 && at.beatSlot() == 2 && at.shown().get(2).is(Items.OAK_PLANKS), "a changed slot is a beat with its number (" + at.beat() + ")");
        m.getSlot(8).set(new ItemStack(Items.STICK, 1));
        StationPresence.tick(p, sent);
        at = p.getData(StationPresence.AT);
        h.assertTrue(at.beats() == 1 && at.shown().get(8).is(Items.STICK), "a second change inside three ticks is seen, with no second beat");
        // the menu shuts: they are no longer at it
        p.containerMenu = p.inventoryMenu;
        h.assertTrue(!StationPresence.tick(p, sent), "with the menu shut they have left the station");
        // a box: the six largest stacks, like things together, biggest first
        net.minecraft.world.SimpleContainer box = new net.minecraft.world.SimpleContainer(27);
        box.setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        box.setItem(1, new ItemStack(Items.OAK_PLANKS, 30));
        box.setItem(2, new ItemStack(Items.COBBLESTONE, 40));
        for (int i = 0; i < 7; i++) box.setItem(5 + i, new ItemStack(i == 0 ? Items.STICK : i == 1 ? Items.COAL : i == 2 ? Items.BEEF : i == 3 ? Items.APPLE : i == 4 ? Items.BONE
                : i == 5 ? Items.FLINT : Items.FEATHER, i + 1));
        net.minecraft.world.inventory.ChestMenu chest = net.minecraft.world.inventory.ChestMenu.threeRows(2, p.getInventory(), box);
        java.util.List<ItemStack> seen = StationPresence.shown(StationPresence.BOX, chest);
        h.assertTrue(seen.size() == StationPresence.BOX_SHOWN && seen.get(0).is(Items.COBBLESTONE) && seen.get(0).getCount() == 50 && seen.get(1).is(Items.OAK_PLANKS),
                "a box shows its six largest stacks, like things together, biggest first (" + seen + ")");
        for (int i = 1; i < seen.size(); i++) h.assertTrue(seen.get(i).getCount() <= seen.get(i - 1).getCount(), "in order of size");
        h.assertTrue(StationPresence.changedSlot(first, first) == -1 && StationPresence.changedSlot(first, StationPresence.shown(StationPresence.BENCH, m)) == 2,
                "the first slot that differs is the one that changed");
        h.succeed();
    }

    /**
     * Living slots (SPEC §63.7): a stack sent from one slot to another without carrying it is one move with its count; a
     * stack split across two slots is two; a change the pointer made is none; a tick that changed more than six slots is
     * none (a Tidy shows its own).
     */
    static void livingSlots(GameTestHelper h) {
        ItemStack[] was = new ItemStack[12], now = new ItemStack[12];
        java.util.Arrays.fill(was, ItemStack.EMPTY);
        java.util.Arrays.fill(now, ItemStack.EMPTY);
        int[] out = new int[24];
        was[2] = new ItemStack(Items.OAK_PLANKS, 16);
        now[7] = new ItemStack(Items.OAK_PLANKS, 16);
        h.assertTrue(SlotMoves.read(was, now, false, out) == 1 && out[0] == 2 && out[1] == 7 && out[2] == 16, "a shift-click is one move, from its slot to where it landed");
        h.assertTrue(SlotMoves.read(was, now, true, out) == 0, "a stack the pointer carried is no move");
        h.assertTrue(SlotMoves.read(was, was, false, out) == 0, "nothing changed, nothing moves");
        // it topped up one stack and the rest went to an empty slot
        was[7] = new ItemStack(Items.OAK_PLANKS, 60);
        now[7] = new ItemStack(Items.OAK_PLANKS, 64);
        now[8] = new ItemStack(Items.OAK_PLANKS, 12);
        int n = SlotMoves.read(was, now, false, out);
        h.assertTrue(n == 2 && out[2] + out[5] == 16, "a stack that filled one slot and spilled into the next is two moves (" + n + ")");
        // a number key swaps two different stacks
        java.util.Arrays.fill(was, ItemStack.EMPTY);
        java.util.Arrays.fill(now, ItemStack.EMPTY);
        was[0] = new ItemStack(Items.STICK, 4);
        was[9] = new ItemStack(Items.COAL, 2);
        now[0] = new ItemStack(Items.COAL, 2);
        now[9] = new ItemStack(Items.STICK, 4);
        h.assertTrue(SlotMoves.read(was, now, false, out) == 2, "a swap is two moves, one each way");
        // a Tidy: more than six slots at once
        for (int i = 0; i < 8; i++) {
            was[i] = new ItemStack(Items.COBBLESTONE, i + 1);
            now[i] = new ItemStack(Items.COBBLESTONE, 8 - i);
        }
        h.assertTrue(SlotMoves.read(was, now, false, out) == 0, "more than six slots changed in a tick: left to whoever made it");
        h.succeed();
    }

    /**
     * The six workstations (SPEC §63.8): each block is its kind (a grindstone only on the floor), each kind's slots lie
     * on its own block, apart from one another, and from each of its three views, from all four sides, at four window
     * sizes, every slot is inside the room and a click on its middle picks it.
     */
    static void workstations(GameTestHelper h) {
        h.assertTrue(StationView.kindOf(Blocks.ANVIL.defaultBlockState()) == StationView.Kind.ANVIL
                && StationView.kindOf(Blocks.DAMAGED_ANVIL.defaultBlockState()) == StationView.Kind.ANVIL
                && StationView.kindOf(Blocks.SMITHING_TABLE.defaultBlockState()) == StationView.Kind.SMITHING
                && StationView.kindOf(Blocks.STONECUTTER.defaultBlockState()) == StationView.Kind.STONECUTTER
                && StationView.kindOf(Blocks.CARTOGRAPHY_TABLE.defaultBlockState()) == StationView.Kind.CARTOGRAPHY
                && StationView.kindOf(Blocks.BREWING_STAND.defaultBlockState()) == StationView.Kind.BREWING
                && StationView.kindOf(Blocks.LOOM.defaultBlockState()) == null, "each workstation is its own kind, and a loom has no scene");
        var floor = Blocks.GRINDSTONE.defaultBlockState().setValue(net.minecraft.world.level.block.GrindstoneBlock.FACE,
                net.minecraft.world.level.block.state.properties.AttachFace.FLOOR);
        var wall = floor.setValue(net.minecraft.world.level.block.GrindstoneBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL);
        h.assertTrue(StationView.kindOf(floor) == StationView.Kind.GRINDSTONE && StationView.kindOf(wall) == null, "a grindstone has a scene only on the floor");
        int[][] screens = {{320, 240}, {424, 238}, {640, 360}, {1280, 720}};
        int[][] ways = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        float[] view = new float[3];
        double[] p = new double[3], q = new double[2], eye = new double[3], o = new double[3];
        Quaternionf rot = new Quaternionf();
        for (StationView.Kind kind : new StationView.Kind[]{StationView.Kind.ANVIL, StationView.Kind.SMITHING, StationView.Kind.GRINDSTONE,
                StationView.Kind.STONECUTTER, StationView.Kind.CARTOGRAPHY, StationView.Kind.BREWING}) {
            for (int[] way : ways) {
                StationView.Station s = new StationView.Station(TABLE, kind, way[0], way[1]);
                for (int i = 0; i < kind.slots; i++) {
                    StationView.spot(s, i, p, 0);
                    h.assertTrue(p[0] > TABLE.getX() + 0.02 && p[0] < TABLE.getX() + 0.98 && p[2] > TABLE.getZ() + 0.02 && p[2] < TABLE.getZ() + 0.98
                            && p[1] > TABLE.getY() && p[1] < TABLE.getY() + 1.05, kind + " slot " + i + " lies on its own block");
                    for (int j = 0; j < i; j++) {
                        StationView.spot(s, j, o, 0);
                        double d = Math.sqrt((p[0] - o[0]) * (p[0] - o[0]) + (p[1] - o[1]) * (p[1] - o[1]) + (p[2] - o[2]) * (p[2] - o[2]));
                        h.assertTrue(d > kind.half * 1.6, kind + " slots " + j + " and " + i + " are apart (" + d + ")");
                    }
                }
                for (int b = 0; b < StationView.BENCHES; b++) {
                    StationView.benchEye(s, b, eye);
                    for (int[] scr : screens) {
                        int w = scr[0], gh = scr[1];
                        float aspect = (float) w / gh;
                        StationView.Layout l = StationView.layout(w, gh, 2, true);
                        StationView.frame(s, eye[0], eye[1], eye[2], l, w, gh, aspect, StationView.MAX_FOV, view);
                        StationView.rotation(view[0], view[1], rot);
                        for (int i = 0; i < kind.slots; i++) {
                            StationView.spot(s, i, p, 0);
                            String at = kind + " slot " + i + " from view " + b + " at " + w + "x" + gh;
                            h.assertTrue(StationView.project(p[0], p[1], p[2], eye[0], eye[1], eye[2], rot, view[2], aspect, w, gh, q) && l.inRoom(q[0], q[1]),
                                    at + " is inside the room");
                            int got = StationView.pick(s, l, eye[0], eye[1], eye[2], rot, view[2], aspect, q[0], q[1], w, gh);
                            h.assertTrue(got == i, at + " picks itself (got " + got + ")");
                        }
                    }
                }
            }
        }
        h.succeed();
    }

    /**
     * Every block a player works has a touch (SPEC §63.9): vanilla's by class, ours by id (each id is a real block: a
     * renamed block would lose its hand silently); what the hand holds decides a put from a take, and the Forge's blows
     * from its palm; how high the thing is against the body is low, middle or high.
     */
    static void touchEveryBlock(GameTestHelper h) {
        ItemStack none = ItemStack.EMPTY, stick = new ItemStack(Items.STICK);
        Object[][] vanilla = {
                {Blocks.OAK_DOOR, TouchKinds.Kind.PUSH}, {Blocks.OAK_TRAPDOOR, TouchKinds.Kind.PUSH}, {Blocks.OAK_FENCE_GATE, TouchKinds.Kind.PUSH},
                {Blocks.LEVER, TouchKinds.Kind.LEVER}, {Blocks.STONE_BUTTON, TouchKinds.Kind.BUTTON}, {Blocks.CHEST, TouchKinds.Kind.LID},
                {Blocks.ENDER_CHEST, TouchKinds.Kind.LID}, {Blocks.SHULKER_BOX, TouchKinds.Kind.LID}, {Blocks.BARREL, TouchKinds.Kind.HATCH},
                {Blocks.SWEET_BERRY_BUSH, TouchKinds.Kind.PLUCK}, {Blocks.LODESTONE, TouchKinds.Kind.PALM}, {Blocks.RESPAWN_ANCHOR, TouchKinds.Kind.PALM},
                {Blocks.BEACON, TouchKinds.Kind.PALM}, {Blocks.GRINDSTONE, TouchKinds.Kind.CRANK}, {Blocks.LECTERN, TouchKinds.Kind.PAGE},
                {Blocks.OAK_SIGN, TouchKinds.Kind.PAGE}, {Blocks.ANVIL, TouchKinds.Kind.HAMMER}, {Blocks.SMITHING_TABLE, TouchKinds.Kind.HAMMER},
                {Blocks.STONECUTTER, TouchKinds.Kind.SET}, {Blocks.LOOM, TouchKinds.Kind.SET}, {Blocks.CARTOGRAPHY_TABLE, TouchKinds.Kind.SET},
                {Blocks.BREWING_STAND, TouchKinds.Kind.SET}, {Blocks.NOTE_BLOCK, TouchKinds.Kind.TAP},
                {Blocks.REPEATER, TouchKinds.Kind.TAP}, {Blocks.COMPARATOR, TouchKinds.Kind.TAP}, {Blocks.DAYLIGHT_DETECTOR, TouchKinds.Kind.TAP},
                {Blocks.BELL, TouchKinds.Kind.RING}, {Blocks.CAKE, TouchKinds.Kind.TAKE}};
        for (Object[] row : vanilla) {
            TouchKinds.Kind got = TouchKinds.of(((net.minecraft.world.level.block.Block) row[0]).defaultBlockState(), none);
            h.assertTrue(got == row[1], row[0] + " is " + row[1] + " (got " + got + ")");
        }
        for (net.minecraft.world.level.block.Block b : new net.minecraft.world.level.block.Block[]{Blocks.JUKEBOX, Blocks.COMPOSTER, Blocks.DECORATED_POT,
                Blocks.END_PORTAL_FRAME, Blocks.FLOWER_POT, Blocks.CHISELED_BOOKSHELF}) {
            h.assertTrue(TouchKinds.of(b.defaultBlockState(), stick) == TouchKinds.Kind.PUT && TouchKinds.of(b.defaultBlockState(), none) == TouchKinds.Kind.TAKE,
                    b + ": a thing in the hand is put, an empty hand takes");
        }
        h.assertTrue(TouchKinds.of(Blocks.STONE.defaultBlockState(), none) == null && TouchKinds.of(Blocks.CRAFTING_TABLE.defaultBlockState(), none) == null,
                "a plain block has no touch, and a crafting table's hands are its scene's");
        String[][] ours = {{"waystone", "PALM"}, {"conduit", "PALM"}, {"ascent_stone", "PALM"}, {"hearth_anchor", "PALM"}, {"stillpoint_shrine", "PALM"},
                {"word_stone", "PALM"}, {"precursor_log_terminal", "PALM"}, {"precursor_door_core", "PALM"}, {"halo_return_pad", "PALM"},
                {"anomaly_fallen_star", "PALM"}, {"well_lift", "PALM"}, {"reassessment_monolith", "PALM"}, {"waking_pick_stone", "PALM"}, {"hearthfire", "PALM"},
                {"fitting_stand", "SET"}, {"foundry_crucible", "SET"}, {"storehouse_drop", "SET"}, {"work_plan", "SET"}, {"fabricator", "SET"},
                {"broken_machine", "SET"}, {"trophy_mount", "SET"}, {"pattern_stand", "SET"}, {"bell_frame", "RING"}, {"found_bell", "RING"},
                {"sleeping_bell", "RING"}, {"dream_bell", "RING"}, {"spire_board", "TEAR"}, {"ledger_desk", "PAGE"}, {"ghost_lever", "LEVER"},
                {"carved_token", "TAKE"}, {"piton", "TAKE"}, {"veil_cache", "LID"}, {"anomaly_spirit_cache", "LID"}, {"left_chest", "LID"},
                {"precursor_door", "PUSH"}, {"ghost_door", "PUSH"}, {"forge", "PALM"}};
        for (String[] row : ours) {
            var id = net.minecraft.resources.Identifier.fromNamespaceAndPath("awaken", row[0]);
            var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(id);
            h.assertTrue(block.isPresent(), "awaken:" + row[0] + " is a real block");
            TouchKinds.Kind got = TouchKinds.of(block.get().defaultBlockState(), none);
            h.assertTrue(got != null && got.name().equals(row[1]), "awaken:" + row[0] + " is " + row[1] + " (got " + got + ")");
        }
        var forge = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(net.minecraft.resources.Identifier.fromNamespaceAndPath("awaken", "forge"));
        h.assertTrue(forge.isPresent() && TouchKinds.of(forge.get().defaultBlockState(), stick) == TouchKinds.Kind.HAMMER, "the Forge with a piece in the hand is blows");
        h.assertTrue(TouchKinds.height(64, 64.3) == 0 && TouchKinds.height(64, 65) == 1 && TouchKinds.height(64, 65.6) == 2, "low, middle and high against the body");
        for (TouchKinds.Kind k : TouchKinds.Kind.values()) h.assertTrue(k.reach != null, k + " has a body");
        h.succeed();
    }

    /** A click on the strip never picks a square, even when the view (still leaning in) has a square under it. */
    static void panelClick(GameTestHelper h) {
        int w = 424, gh = 238;
        float aspect = (float) w / gh, fov = 70;
        StationView.Layout l = StationView.layout(w, gh, 2, true);
        double ex = TABLE.getX() + 0.5, ey = TABLE.getY() + EYE, ez = TABLE.getZ() + 0.5 - 1.5;
        StationView.Station s = station(ex, ez);
        // the player's own look before the lean: the table's middle where the strip's middle is
        double[] p = new double[3], q = new double[2];
        StationView.spot(s, 5, p, 0);
        double ny = 1 - (l.py0() + l.py1()) / (double) gh;
        float pitch = (float) Math.toDegrees(Math.atan2(ey - p[1], p[2] - ez) + Math.atan(ny * Math.tan(Math.toRadians(fov) / 2)));
        Quaternionf rot = StationView.rotation(0, pitch, new Quaternionf());
        int under = 0;
        for (int i = 1; i < 10; i++) {
            StationView.spot(s, i, p, 0);
            if (!StationView.project(p[0], p[1], p[2], ex, ey, ez, rot, fov, aspect, w, gh, q) || !l.inPanel(q[0], q[1])) continue;
            under++;
            h.assertTrue(StationView.pick(s, l, ex, ey, ez, rot, fov, aspect, q[0], q[1], w, gh) == -1, "a click on the strip over square " + i + " picks nothing");
        }
        h.assertTrue(under > 0, "a square lies under the strip in this view");
        h.succeed();
    }

    // ---- the clicks on a real crafting menu (what vanilla's screen sends for each gesture) ----------------------------

    private static CraftingMenu menu(GameTestHelper h, ServerPlayer p) {
        BlockPos at = h.absolutePos(BlockPos.ZERO);
        h.getLevel().setBlockAndUpdate(at, Blocks.CRAFTING_TABLE.defaultBlockState());
        CraftingMenu m = new CraftingMenu(1, p.getInventory(), ContainerLevelAccess.create(h.getLevel(), at));
        p.containerMenu = m;
        return m;
    }

    /** A drag across squares: vanilla's quickCraftToSlots (type 0 splits evenly, 1 lays one each). */
    private static void drag(AbstractContainerMenu m, ServerPlayer p, int type, int... slots) {
        m.clicked(-999, AbstractContainerMenu.getQuickcraftMask(0, type), ContainerInput.QUICK_CRAFT, p);
        for (int s : slots) m.clicked(s, AbstractContainerMenu.getQuickcraftMask(1, type), ContainerInput.QUICK_CRAFT, p);
        m.clicked(-999, AbstractContainerMenu.getQuickcraftMask(2, type), ContainerInput.QUICK_CRAFT, p);
    }

    private static int count(AbstractContainerMenu m, int slot) {
        return m.getSlot(slot).getItem().getCount();
    }

    /** Right click lays one item from the held stack; on a square with nothing held it takes half. */
    static void rightClick(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        CraftingMenu m = menu(h, p);
        m.setCarried(new ItemStack(Items.OAK_PLANKS, 8));
        m.clicked(1, 1, ContainerInput.PICKUP, p);
        m.clicked(1, 1, ContainerInput.PICKUP, p);
        h.assertTrue(count(m, 1) == 2 && m.getCarried().getCount() == 6, "two right clicks lay one each (" + count(m, 1) + ", held " + m.getCarried().getCount() + ")");
        m.setCarried(ItemStack.EMPTY);
        m.getSlot(2).set(new ItemStack(Items.OAK_PLANKS, 5));
        m.clicked(2, 1, ContainerInput.PICKUP, p);
        h.assertTrue(m.getCarried().getCount() == 3 && count(m, 2) == 2, "a right click on 5 takes half, rounded up (held " + m.getCarried().getCount() + ")");
        h.succeed();
    }

    /** Right-drag lays one item on every square passed over. */
    static void rightDrag(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        CraftingMenu m = menu(h, p);
        m.setCarried(new ItemStack(Items.OAK_PLANKS, 8));
        drag(m, p, 1, 1, 2, 3, 5);
        for (int s : new int[]{1, 2, 3, 5}) h.assertTrue(count(m, s) == 1, "square " + s + " got one (" + count(m, s) + ")");
        h.assertTrue(m.getCarried().getCount() == 4, "four stay in hand (" + m.getCarried().getCount() + ")");
        h.succeed();
    }

    /** Left-drag splits the held stack evenly over the squares passed over, the rest staying in hand. */
    static void leftDrag(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        CraftingMenu m = menu(h, p);
        m.setCarried(new ItemStack(Items.OAK_PLANKS, 10));
        drag(m, p, 0, 1, 2, 3);
        for (int s : new int[]{1, 2, 3}) h.assertTrue(count(m, s) == 3, "square " + s + " got a third (" + count(m, s) + ")");
        h.assertTrue(m.getCarried().getCount() == 1, "the one left over stays in hand (" + m.getCarried().getCount() + ")");
        h.succeed();
    }

    /** A plain click on the result takes one craft; shift-click crafts all the rest into the inventory. */
    static void shiftResult(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        CraftingMenu m = menu(h, p);
        m.getSlot(5).set(new ItemStack(Items.OAK_LOG, 3));
        h.assertTrue(m.getSlot(0).getItem().is(Items.OAK_PLANKS) && count(m, 0) == 4, "three logs show four planks");
        m.clicked(0, 0, ContainerInput.PICKUP, p);
        h.assertTrue(m.getCarried().getCount() == 4 && count(m, 5) == 2, "a click on the result takes one craft (held " + m.getCarried().getCount() + ")");
        m.setCarried(ItemStack.EMPTY);
        m.clicked(0, 0, ContainerInput.QUICK_MOVE, p);
        int planks = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack st = p.getInventory().getItem(i);
            if (st.is(Items.OAK_PLANKS)) planks += st.getCount();
        }
        h.assertTrue(planks == 8 && m.getSlot(5).getItem().isEmpty(), "shift-click crafts the last two logs into the inventory (" + planks + " planks)");
        h.succeed();
    }
}
