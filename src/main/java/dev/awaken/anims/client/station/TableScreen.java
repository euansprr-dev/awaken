package dev.awaken.anims.client.station;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The crafting table's scene (StationClient): the vanilla crafting menu with its grid and result drawn on the table in
 * the world rather than on the screen. Your inventory sits in a strip at the foot of the screen; a click on a square of
 * the table (or on the floating result) is the same click on that slot, so stacks, splits and shift-clicks behave as
 * they always do and the server checks them. Moving, jumping, the inventory key or Esc leave.
 */
final class TableScreen extends AbstractContainerScreen<CraftingMenu> {
    private static final int GLASS = 0xC80F1724, EDGE = 0xFF7FC3FF, HINT = 0xFFB9C7D9;
    /** The slot under the cursor on the table (StationClient draws its outline), or -1. */
    int hovered = -1;

    TableScreen(CraftingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    @Override
    protected void init() {
        super.init();
        // the inventory's rows at the foot of the screen; the grid's place above them is the table now
        topPos = height - imageHeight - 4;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        // no dimming: the table is the screen; a glass strip behind the inventory only
        int y0 = topPos + 78;
        g.fill(leftPos - 4, y0, leftPos + imageWidth + 4, topPos + imageHeight + 2, GLASS);
        g.outline(leftPos - 4, y0, imageWidth + 8, imageHeight - 76, EDGE);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
        g.text(font, Component.translatable("screen.awaken.table.hint"), 0, 82 - 12 - 2, HINT, true);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        hovered = StationClient.pick(mouseX, mouseY, width, height);
        super.extractRenderState(g, mouseX, mouseY, a);
        if (hovered >= 0 && menu.getCarried().isEmpty()) {
            ItemStack s = menu.getSlot(hovered).getItem();
            if (!s.isEmpty()) g.setTooltipForNextFrame(font, s.getHoverName(), mouseX, mouseY);
        }
    }

    /** The grid and the result live on the table: never drawn here. */
    @Override
    protected void extractSlot(GuiGraphicsExtractor g, Slot slot, int mouseX, int mouseY) {
        if (slot.index >= 10) super.extractSlot(g, slot, mouseX, mouseY);
    }

    /** Nor hovered or clicked here (their screen places are under the table now). */
    @Override
    protected boolean isHovering(int left, int top, int w, int h, double xm, double ym) {
        if (w == 16 && h == 16) {
            for (int i = 0; i < 10; i++) {
                Slot s = menu.getSlot(i);
                if (s.x == left && s.y == top) return false;
            }
        }
        return super.isHovering(left, top, w, h, xm, ym);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int slot = StationClient.pick(event.x(), event.y(), width, height);
        if (slot >= 0) {
            int button = event.button() == 1 ? 1 : 0;
            slotClicked(menu.getSlot(slot), slot, button, event.hasShiftDown() ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** Dev (tours): the click a player would make on a slot (a square of the table, the result or the inventory). */
    void devClick(int slot, int button) {
        slotClicked(menu.getSlot(slot), slot, button, ContainerInput.PICKUP);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        var o = minecraft.options;
        if (o.keyUp.matches(event) || o.keyDown.matches(event) || o.keyLeft.matches(event) || o.keyRight.matches(event) || o.keyJump.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        super.removed();
        StationClient.closed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
