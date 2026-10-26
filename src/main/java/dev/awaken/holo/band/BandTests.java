package dev.awaken.holo.band;

import dev.awaken.core.api.HoloBand;
import dev.awaken.core.test.AwakenTests;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;

/** One GameTest per mechanism of the band (SPEC §16.4, "The Registry ceremony"). */
final class BandTests {
    private BandTests() {}

    private static int bandSlot(ServerPlayer p) {
        for (Slot s : p.inventoryMenu.slots) if (BandModule.isBand(s.getItem())) return s.index;
        return -1;
    }

    static void register() {
        // handed over, it sits in your inventory and the Holo stays dark; only the wrist turns it on
        AwakenTests.register("holo_band_dark_until_worn", 40, h -> {
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            h.assertTrue(HoloBand.worn(p), "a player who never had the slot has the Holo (old saves, classic worlds)");
            BandModule.takeAway(p);
            h.assertTrue(!HoloBand.worn(p) && HoloBand.fitted(p), "taken away: dark, the slot fitted");
            BandModule.give(p);
            h.assertTrue(BandModule.has(p), "handed a band");
            h.assertTrue(BandModule.isBand(p.getInventory().getSelectedItem()), "into the hand (the selected slot was free)");
            h.assertTrue(!HoloBand.worn(p), "in the inventory it does nothing");
            h.assertTrue(BandModule.click(p, BandNet.CLICK, -1) == BandModule.Result.REFUSED, "an empty cursor on an empty wrist does nothing");
            h.assertTrue(BandModule.click(p, BandNet.SHIFT_ON, bandSlot(p)) == BandModule.Result.WORN, "shift-click puts it on");
            h.assertTrue(HoloBand.worn(p) && bandSlot(p) < 0, "worn: on, and out of the inventory");
            h.succeed();
        });

        // off the wrist (onto the cursor, or into the inventory) the Holo goes dark; back on, it's on again
        AwakenTests.register("holo_band_wearing_toggles", 40, h -> {
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            BandModule.takeAway(p);
            BandModule.wear(p);
            boolean[] heard = new boolean[2];
            HoloBand.listen((who, worn) -> {
                if (who == p) heard[worn ? 1 : 0] = true;
            });
            h.assertTrue(HoloBand.worn(p), "worn");
            h.assertTrue(BandModule.click(p, BandNet.CLICK, -1) == BandModule.Result.TAKEN_OFF, "a click with an empty cursor takes it off");
            h.assertTrue(BandModule.isBand(p.containerMenu.getCarried()) && !HoloBand.worn(p) && heard[0], "on the cursor; dark; told");
            h.assertTrue(BandModule.click(p, BandNet.CLICK, -1) == BandModule.Result.WORN, "clicked back on");
            h.assertTrue(p.containerMenu.getCarried().isEmpty() && HoloBand.worn(p) && heard[1], "on again; told");
            p.containerMenu.setCarried(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT));
            h.assertTrue(BandModule.click(p, BandNet.CLICK, -1) == BandModule.Result.REFUSED, "anything but a band is refused");
            p.containerMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
            h.assertTrue(BandModule.click(p, BandNet.SHIFT_OFF, -1) == BandModule.Result.TAKEN_OFF && bandSlot(p) >= 0 && !HoloBand.worn(p),
                    "shift-click takes it off into the inventory");
            h.succeed();
        });

        // the wrist is saved: a worn band comes back worn, an empty fitted slot stays dark
        AwakenTests.register("holo_band_slot_saves", 40, h -> {
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            var ops = p.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            BandModule.takeAway(p);
            BandModule.wear(p);
            Tag saved = BandState.CODEC.encodeStart(ops, BandModule.state(p)).getOrThrow();
            BandState back = BandState.CODEC.parse(ops, saved).getOrThrow();
            h.assertTrue(back.fitted() && BandModule.isBand(back.wrist()) && back.worn(), "worn after a save: " + saved);
            BandModule.takeAway(p);
            BandState dark = BandState.CODEC.parse(ops, BandState.CODEC.encodeStart(ops, BandModule.state(p)).getOrThrow()).getOrThrow();
            h.assertTrue(dark.fitted() && !dark.worn(), "dark after a save");
            BandState old = BandState.CODEC.parse(ops, new net.minecraft.nbt.CompoundTag()).getOrThrow();
            h.assertTrue(old.worn(), "nothing saved (a save from before the ceremony): the Holo is on");
            h.succeed();
        });
    }
}
