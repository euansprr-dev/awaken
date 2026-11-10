package dev.awaken.anims.client.glass;

import dev.awaken.core.api.UiHooks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.BeaconMenu;

/** A beacon (its power buttons are vanilla's own) in the band's glass: vanilla's own screen underneath, only the drawing changed (SPEC §64.9). */
final class GlassBeaconScreen extends BeaconScreen {
    private final Inventory inventory;
    private long opened;

    GlassBeaconScreen(BeaconMenu menu, Inventory inventory, Component title) {
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
        // what a beacon takes, where vanilla shows them
        int xo = leftPos, yo = topPos;
        g.item(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_INGOT), xo + 20, yo + 109);
        g.item(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD), xo + 41, yo + 109);
        g.item(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND), xo + 41 + 22, yo + 109);
        g.item(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLD_INGOT), xo + 42 + 44, yo + 109);
        g.item(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT), xo + 42 + 66, yo + 109);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
        super.extractLabels(g, xm, ym); // "Primary Power" and "Secondary Power" are light on dark already
    }

    @Override
    public void removed() {
        super.removed();
        UiHooks.clearFocus(this);
    }
}
