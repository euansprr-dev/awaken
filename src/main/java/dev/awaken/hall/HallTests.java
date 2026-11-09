package dev.awaken.hall;

import dev.awaken.Awaken;
import dev.awaken.armour.ArmourForge;
import dev.awaken.armour.ArmourSet;
import dev.awaken.armour.net.ArmourPayloads;
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
        AwakenTests.register("hall_suits", 40, HallTests::suits);
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

    /** Every suit stands on the terraces whatever your rank; above it, one you know still can't be made; Track is remembered. */
    private static void suits(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        dev.awaken.progression.Progression.awaken(p);
        var o = ArmourForge.hall(p, null);
        h.assertFalse(o.stand(), "from K there is no stand");
        h.assertTrue(o.offers().size() == ArmourSet.values().length * 4, "four pieces of all 29 suits, got " + o.offers().size());
        ArmourSet first = ArmourSet.BELLGUARD, top = ArmourSet.RIFTWALKER;
        h.assertTrue(flags(o, top, 0, ArmourPayloads.NO_PATTERN), "an unfound suit stays a shape");
        h.assertTrue(flags(o, top, 0, ArmourPayloads.ABOVE), "S is above a new player");
        ArmourForge.learn(p, top);
        ArmourForge.learn(p, first);
        ArmourForge.track(p, first);
        var after = ArmourForge.hall(p, null);
        h.assertFalse(flags(after, top, 0, ArmourPayloads.NO_PATTERN), "the pattern you found shows its look");
        h.assertFalse(flags(after, top, 0, ArmourPayloads.CAN), "above your rank you can't make it, even knowing the pattern");
        h.assertFalse(flags(after, first, 0, ArmourPayloads.CAN), "no stand, nothing can be made");
        h.assertTrue(after.tracked().equals(first.id), "the tracked suit comes back, got " + after.tracked());
        h.assertTrue(dev.awaken.core.api.Goals.all(p).stream().anyMatch(s -> s.key().equals("armour/tracked")), "tracking puts it on K > Tasks");
        ArmourForge.track(p, null);
        h.assertTrue(ArmourForge.hall(p, null).tracked().isEmpty(), "and can be let go");
        h.succeed();
    }

    private static boolean flags(ArmourPayloads.Offers o, ArmourSet set, int slot, int flag) {
        for (ArmourPayloads.Offer one : o.offers()) if (one.piece() == set.ordinal() * 4 + slot) return (one.flags() & flag) != 0;
        return false;
    }
}
