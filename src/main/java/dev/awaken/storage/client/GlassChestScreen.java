package dev.awaken.storage.client;

import dev.awaken.core.api.UiHooks;
import dev.awaken.holo.client.HoloGlass;
import dev.awaken.signal.client.Glass;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;

/** A chest, barrel or copper chest in glass: vanilla's screen underneath, so every click behaves as it always has (SPEC §56). */
final class GlassChestScreen extends ContainerScreen {
    private final Inventory inventory;
    private long opened;

    GlassChestScreen(ChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.inventory = inventory;
    }

    @Override
    protected void init() {
        super.init();
        opened = System.nanoTime();
    }

    private float alpha() {
        return Math.min(1f, (System.nanoTime() - opened) / 120_000_000f);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        // no dim and no blur, as the bag: the world stays in view and Lens dims it
        minecraft.gui.hud.extractDeferredSubtitles();
        float k = alpha();
        UiHooks.setFocus(this, k);
        ChestLook.background(g, this, inventory, getSlotAt(mx, my), k);
    }

    private Slot getSlotAt(double mx, double my) {
        for (Slot s : menu.slots) if (s.isActive() && isHovering(s.x, s.y, 16, 16, mx, my)) return s;
        return null;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
        float k = alpha();
        HoloGlass.text(g, font, title.getVisualOrderText(), titleLabelX, titleLabelY, Glass.a(Glass.EDGE, k));
        HoloGlass.text(g, font, playerInventoryTitle.getVisualOrderText(), inventoryLabelX, inventoryLabelY, Glass.a(Glass.LABEL, k));
    }

    @Override
    protected void extractSlot(GuiGraphicsExtractor g, Slot slot, int mx, int my) {
        if (!Tray.hiding(slot)) super.extractSlot(g, slot, mx, my);
    }

    @Override
    public void removed() {
        super.removed();
        UiHooks.clearFocus(this);
    }
}
