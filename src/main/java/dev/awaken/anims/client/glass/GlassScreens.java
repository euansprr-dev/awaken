package dev.awaken.anims.client.glass;

import dev.awaken.holo.client.HoloGlass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.client.gui.screens.inventory.CrafterScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Vanilla's hopper, dispenser and dropper, crafter and beacon screens on the band's glass (SPEC §65.9), as storage put
 * chests and shulker boxes (§56): each replaces only the exact vanilla class, and only while the bag is glass, with a
 * subclass that changes the drawing and nothing else. The loom and the lectern keep vanilla's own screens: the loom's
 * is its pattern list and banner as much as its slots, and a lectern shows a book.
 */
public final class GlassScreens {
    private GlassScreens() {}

    public static void init(IEventBus bus) {
        bus.addListener(EventPriority.LOW, ScreenEvent.Opening.class, GlassScreens::onOpening);
    }

    private static void onOpening(ScreenEvent.Opening e) {
        Screen next = e.getNewScreen();
        Minecraft mc = Minecraft.getInstance();
        if (next == null || mc.player == null || !HoloGlass.on()) return;
        Inventory inv = mc.player.getInventory();
        Class<?> c = next.getClass();
        if (c == HopperScreen.class) e.setNewScreen(new GlassHopperScreen(((HopperScreen) next).getMenu(), inv, next.getTitle()));
        else if (c == DispenserScreen.class) e.setNewScreen(new GlassDispenserScreen(((DispenserScreen) next).getMenu(), inv, next.getTitle()));
        else if (c == CrafterScreen.class) e.setNewScreen(new GlassCrafterScreen(((CrafterScreen) next).getMenu(), inv, next.getTitle()));
        else if (c == BeaconScreen.class) e.setNewScreen(new GlassBeaconScreen(((BeaconScreen) next).getMenu(), inv, next.getTitle()));
    }
}
