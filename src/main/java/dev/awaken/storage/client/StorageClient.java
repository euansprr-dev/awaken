package dev.awaken.storage.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.Awaken;
import dev.awaken.holo.client.HoloGlass;
import dev.awaken.storage.StorageNet;
import dev.awaken.storage.Tidying.Section;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.DispenserScreen;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

/** Storage on the client (SPEC §56): buttons on the screens we've checked, the keys, and chests in the bag's glass. */
public final class StorageClient {
    private StorageClient() {}

    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Awaken.id("storage"));
    private static final KeyMapping TIDY = new KeyMapping("key.awaken.storage_tidy", KeyConflictContext.GUI, InputConstants.Type.KEYBOARD, InputConstants.KEY_T, CATEGORY);
    private static final KeyMapping UNDO = new KeyMapping("key.awaken.storage_undo", KeyConflictContext.GUI, InputConstants.Type.KEYBOARD, InputConstants.KEY_Z, CATEGORY);

    private static boolean pinMode;

    public static void init(IEventBus modBus) {
        StorageConfig.register(modBus);
        modBus.addListener(RegisterKeyMappingsEvent.class, e -> {
            e.registerCategory(CATEGORY);
            e.register(TIDY);
            e.register(UNDO);
        });
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, e -> {
            e.register(StorageNet.Done.TYPE, (m, ctx) -> Tray.onDone(m));
            e.register(StorageNet.Pins.TYPE, (m, ctx) -> Tray.onPins(m));
        });
        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(ScreenEvent.Opening.class, StorageClient::onOpening);
        bus.addListener(ScreenEvent.Init.Post.class, StorageClient::onInit);
        bus.addListener(ScreenEvent.Render.Foreground.class, StorageClient::onForeground);
        // after other mods' text boxes have had the key; if one took it, the key's not ours
        bus.addListener(EventPriority.LOW, ScreenEvent.KeyPressed.Post.class, StorageClient::onKey);
        // before Mouse Tweaks can start a drag from the same click
        bus.addListener(EventPriority.HIGH, ScreenEvent.MouseButtonPressed.Pre.class, StorageClient::onMouse);
        bus.addListener(ScreenEvent.Closing.class, e -> {
            pinMode = false;
            Tray.clear();
        });
    }

    private static void onOpening(ScreenEvent.Opening e) {
        Screen next = e.getNewScreen();
        Minecraft mc = Minecraft.getInstance();
        if (next == null || mc.player == null || !HoloGlass.on()) return;
        Inventory inv = mc.player.getInventory();
        if (next.getClass() == ContainerScreen.class) e.setNewScreen(new GlassChestScreen(((ContainerScreen) next).getMenu(), inv, next.getTitle()));
        else if (next.getClass() == ShulkerBoxScreen.class) e.setNewScreen(new GlassShulkerScreen(((ShulkerBoxScreen) next).getMenu(), inv, next.getTitle()));
    }

    private static boolean bag(AbstractContainerScreen<?> s) {
        return s instanceof InventoryScreen && s.getMenu().getClass() == InventoryMenu.class;
    }

    /** Only the screens we've looked at: other mods' keep their own panel. */
    private static boolean offered(AbstractContainerScreen<?> s) {
        if (!StorageConfig.keys()) return false;
        if (bag(s)) return true;
        Class<?> c = s.getClass(), m = s.getMenu().getClass();
        return (c == ContainerScreen.class || c == GlassChestScreen.class) && m == ChestMenu.class
                || (c == ShulkerBoxScreen.class || c == GlassShulkerScreen.class) && m == ShulkerBoxMenu.class
                // the glass hopper and dispenser (anims.client.glass, SPEC §63.9) are subclasses of vanilla's
                || (c == DispenserScreen.class || c.getSuperclass() == DispenserScreen.class && c.getName().startsWith("dev.awaken.")) && m == DispenserMenu.class
                || (c == HopperScreen.class || c.getSuperclass() == HopperScreen.class && c.getName().startsWith("dev.awaken.")) && m == HopperMenu.class;
    }

    /** The top of the row beside the "Inventory" label. */
    private static int bagRow(AbstractContainerScreen<?> s) {
        if (s.getMenu() instanceof ChestMenu c) return c.getRowCount() * 18 + 18;
        return s.getMenu() instanceof HopperMenu ? 39 : 72;
    }

    private static void onInit(ScreenEvent.Init.Post e) {
        if (!(e.getScreen() instanceof AbstractContainerScreen<?> s) || !offered(s)) return;
        String rule = "Your hotbar and what you're wearing stay with you.";
        String pinTip = "Pinned slots stay where they are. Hold " + (net.minecraft.util.Util.getPlatform() == net.minecraft.util.Util.OS.OSX ? "Option" : "Alt") + " and click a slot, or press this and click.";
        if (bag(s)) {
            // right of the recipe book's button (104 to 124, 61 to 79), the one free corner of this panel
            e.addListener(new GlassKey(s, () -> 126, () -> 58, 23, 11, "Tidy", "Line up your bag by what things are. " + rule, () -> tidy(s, Section.BAG)));
            e.addListener(new GlassKey(s, () -> 151, () -> 58, 19, 11, "Pin", pinTip, StorageClient::togglePinMode).lit(() -> pinMode));
        } else {
            e.addListener(new GlassKey(s, () -> 119, () -> 3, 23, 11, "Tidy", "Line this up by what things are.", () -> tidy(s, Section.CONTAINER)));
            int row = bagRow(s);
            e.addListener(new GlassKey(s, () -> 119, () -> row, 23, 11, "Tidy", "Line up your bag by what things are. " + rule, () -> tidy(s, Section.BAG)));
            e.addListener(new GlassKey(s, () -> 144, () -> row, 25, 11, "Pin", pinTip, StorageClient::togglePinMode).lit(() -> pinMode));
        }
        String undo = "Undo";
        int w = Minecraft.getInstance().font.width(undo) + 8;
        undoKey = new GlassKey(s, () -> s.getImageWidth() - w, () -> Tray.lineY(s, s.height) - 1 - s.getTopPos(), w, 11, undo, "Put everything back where it was.", () -> undo(s));
        undoKey.visible = false;
        e.addListener(undoKey);
    }

    /** The open screen's Undo: shown only while there's something to undo. */
    private static @Nullable GlassKey undoKey;

    private static void onForeground(ScreenEvent.Render.Foreground e) {
        if (!(e.getScreen() instanceof AbstractContainerScreen<?> s) || !offered(s)) return;
        if (undoKey != null) undoKey.visible = Tray.canUndo();
        Tray.draw(e.getGuiGraphics(), s, s.height);
        // the keys that work here, in the band's caps, under the panel where there's room (SPEC §57.9)
        int y = s.getTopPos() + s.getImageHeight() + 19;
        if (y + 10 > s.height) return;
        HINTS.clear();
        HINTS.add(dev.awaken.core.client.ui.Kit.Hint.of("Tidy", TIDY.getTranslatedKeyMessage().getString()));
        HINTS.add(dev.awaken.core.client.ui.Kit.Hint.of("Pin a slot", net.minecraft.util.Util.getPlatform() == net.minecraft.util.Util.OS.OSX ? "Option" : "Alt", "Click"));
        if (Tray.canUndo()) HINTS.add(dev.awaken.core.client.ui.Kit.Hint.of("Undo", UNDO.getTranslatedKeyMessage().getString()));
        var font = Minecraft.getInstance().font;
        int w = dev.awaken.core.client.ui.Kit.hintLineWidth(font, HINTS);
        dev.awaken.core.client.ui.Kit.hintLine(e.getGuiGraphics(), font, HINTS, s.getLeftPos() + (s.getImageWidth() - w) / 2, y, 1F);
    }

    private static final java.util.List<dev.awaken.core.client.ui.Kit.Hint> HINTS = new java.util.ArrayList<>();

    private static void togglePinMode() {
        pinMode = !pinMode;
        Tray.say(pinMode ? "Click slots to pin them." : "Pins set.");
    }

    private static void tidy(AbstractContainerScreen<?> s, Section section) {
        AbstractContainerMenu m = s.getMenu();
        ClientPacketDistributor.sendToServer(new StorageNet.TidyAsk(m.containerId, m.getStateId(), (byte) section.ordinal()));
    }

    private static void undo(AbstractContainerScreen<?> s) {
        ClientPacketDistributor.sendToServer(new StorageNet.UndoAsk(s.getMenu().containerId));
    }

    private static void onKey(ScreenEvent.KeyPressed.Post e) {
        if (!(e.getScreen() instanceof AbstractContainerScreen<?> s) || !offered(s)) return;
        if (TIDY.matches(e.getKeyEvent())) {
            Slot over = s.getHoveredSlot();
            boolean bagSide = bag(s) || over != null && over.container instanceof Inventory;
            tidy(s, bagSide ? Section.BAG : Section.CONTAINER);
        } else if (UNDO.matches(e.getKeyEvent()) && Tray.canUndo() && Tray.undoContainer() == s.getMenu().containerId) {
            undo(s);
        }
    }

    private static void onMouse(ScreenEvent.MouseButtonPressed.Pre e) {
        if (!(e.getScreen() instanceof AbstractContainerScreen<?> s) || !offered(s)) return;
        Tray.skip();
        var mouse = e.getMouseButtonEvent();
        if (mouse.button() != 0 || !(pinMode || mouse.hasAltDown())) return;
        Slot over = s.getHoveredSlot();
        if (over == null || !(over.container instanceof Inventory) || over.getContainerSlot() < Inventory.getSelectionSize()
                || over.getContainerSlot() >= Inventory.INVENTORY_SIZE) return;
        ClientPacketDistributor.sendToServer(new StorageNet.PinAsk(over.getContainerSlot()));
        e.setCanceled(true);
    }
}
