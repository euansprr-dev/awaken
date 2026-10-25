package dev.awaken.anims.client.station;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A station's scene screen (StationClient): the station's own vanilla menu with its station slots (a crafting grid and
 * its result; a furnace's input, fuel and result) drawn on the station in the world instead of on the screen. Your
 * inventory sits in a glass strip at the foot of the screen; a click on a station slot in the world is the same click
 * on that slot, so stacks, splits and shift-clicks behave as they always do and the server checks them. A furnace's
 * cooking and fuel show as two thin lines over the strip. Moving, jumping, the inventory key or Esc leave.
 */
final class StationScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {
    private static final int GLASS = 0xC80F1724, EDGE = 0xFF7FC3FF, HINT = 0xFFB9C7D9, FIRE = 0xFFFFB45A;
    /** The station slots: the first {@code n} of the menu. */
    final int n;
    final StationClient.Kind kind;
    /** The station slot under the cursor in the world (StationClient outlines it), or -1. */
    int hovered = -1;

    StationScreen(M menu, Inventory inventory, Component title, StationClient.Kind kind) {
        super(menu, inventory, title, 176, 166);
        this.kind = kind;
        this.n = kind.slots;
    }

    @Override
    protected void init() {
        super.init();
        // the inventory's rows at the foot of the screen; the station's slots live on the station now
        topPos = height - imageHeight - 4;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int y0 = topPos + 78;
        g.fill(leftPos - 4, y0, leftPos + imageWidth + 4, topPos + imageHeight + 2, GLASS);
        g.outline(leftPos - 4, y0, imageWidth + 8, imageHeight - 76, EDGE);
        if (menu instanceof AbstractFurnaceMenu f) {
            // the cooking (Holo blue) and what's left of the fuel (fire), as two thin lines over the strip
            int w = imageWidth + 8, x0 = leftPos - 4;
            g.fill(x0, y0 - 5, x0 + (int) (w * f.getBurnProgress()), y0 - 3, EDGE);
            if (f.isLit()) g.fill(x0, y0 - 2, x0 + (int) (w * f.getLitProgress()), y0 - 1, FIRE);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
        // centred over the strip (it may run wider than the strip)
        Component hint = Component.translatable(kind.hint);
        g.text(font, hint, (imageWidth - font.width(hint)) / 2, 82 - 12 - 2, HINT, true);
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

    /** The station slots live on the station: never drawn here. */
    @Override
    protected void extractSlot(GuiGraphicsExtractor g, Slot slot, int mouseX, int mouseY) {
        if (slot.index >= n) super.extractSlot(g, slot, mouseX, mouseY);
    }

    /** Nor hovered or clicked here (their screen places are under the station now). */
    @Override
    protected boolean isHovering(int left, int top, int w, int h, double xm, double ym) {
        if (w == 16 && h == 16) {
            for (int i = 0; i < n; i++) {
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

    @Override
    protected void slotClicked(Slot slot, int slotId, int buttonNum, ContainerInput input) {
        StationClient.clicked();
        super.slotClicked(slot, slotId, buttonNum, input);
    }

    /** Dev (tours): the click a player would make on a slot (a station slot or the inventory). */
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
