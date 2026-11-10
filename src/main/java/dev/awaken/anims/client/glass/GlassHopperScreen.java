package dev.awaken.anims.client.glass;

import dev.awaken.core.api.UiHooks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.HopperScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.HopperMenu;

/** A hopper in the band's glass: vanilla's own screen underneath, only the drawing changed (SPEC §64.9). */
final class GlassHopperScreen extends HopperScreen {
    private final Inventory inventory;
    private long opened;

    GlassHopperScreen(HopperMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.inventory = inventory;
    }

    @Override
    protected void init() {
        super.init();
        opened = System.nanoTime();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        // no dim and no blur, as the bag: the world stays in view
        minecraft.gui.hud.extractDeferredSubtitles();
        GlassLook.background(g, this, inventory, mx, my, GlassLook.alpha(opened));
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
        GlassLook.labels(g, font, title, titleLabelX, titleLabelY, playerInventoryTitle, inventoryLabelX, inventoryLabelY, GlassLook.alpha(opened));
    }

    @Override
    public void removed() {
        super.removed();
        UiHooks.clearFocus(this);
    }
}
