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
        AwakenTests.register("anims_station_frame", 20, StationTests::frame);
        AwakenTests.register("anims_station_panel_click", 20, StationTests::panelClick);
        AwakenTests.register("anims_station_right_click", 20, StationTests::rightClick);
        AwakenTests.register("anims_station_right_drag", 20, StationTests::rightDrag);
        AwakenTests.register("anims_station_left_drag", 20, StationTests::leftDrag);
        AwakenTests.register("anims_station_shift_result", 20, StationTests::shiftResult);
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
                                double ma = 0.14, mu = i == 0 ? 0.14 : 0.0, mi = 0.14;
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
