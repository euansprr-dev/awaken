package dev.awaken.hall;

import dev.awaken.Awaken;
import dev.awaken.arms.ArmsApiAccess;
import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** The wall tells the same truth as the Armoury's list, and a niche changes as the player does (SPEC §52). */
final class HallTests {
    private HallTests() {}

    static void register() {
        AwakenTests.register("hall_wall", 40, HallTests::wall);
    }

    private static void wall(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        HallNet.Wall w = HallServer.wall(p);
        h.assertTrue(w.niches().length == HallNet.COLS * HallNet.ROWS, "a niche for every kind at every rank");
        for (var e : ArmsApiAccess.styles(p)) {
            int col = java.util.Arrays.asList(HallNet.COLUMNS).indexOf(e.id());
            h.assertTrue(col >= 0, e.id() + " has a column on the wall");
            int state = w.at(col, 0);
            if (e.state() == 0) h.assertTrue(state >= HallNet.OVER, e.id() + " is known on the list, so not a shape on the wall, got " + state);
            else h.assertTrue(state == HallNet.OFFER, e.id() + " is on the rack, so iron on the wall, got " + state);
        }
        for (int t = 0; t < HallNet.ROWS; t++) h.assertTrue(w.at(java.util.Arrays.asList(HallNet.COLUMNS).indexOf("precursor"), t) <= HallNet.OFFER, "an unknown kind stays unknown at every rank");
        ArmsApiAccess.learn(p, "katana");
        int katana = java.util.Arrays.asList(HallNet.COLUMNS).indexOf("katana");
        h.assertTrue(HallServer.wall(p).at(katana, 5) == HallNet.OVER, "a known pattern above your level shows its true look and waits");
        p.getInventory().add(new ItemStack(BuiltInRegistries.ITEM.getValue(Awaken.id("wildhide_katana"))));
        h.assertTrue(HallServer.wall(p).at(katana, 0) == HallNet.MADE, "holding one makes it yours on the wall");
        h.succeed();
    }
}
