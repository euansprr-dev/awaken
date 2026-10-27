package dev.awaken.anims.client.station;

import dev.awaken.anims.station.StationView;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.neoforged.fml.ModList;
import org.jspecify.annotations.Nullable;

/**
 * A station's scene screen (StationClient): the station's own vanilla menu with its station slots (a crafting grid and
 * its result; a furnace's input, fuel and result) drawn on the station in the world instead of on the screen. Your
 * inventory sits in a glass strip at the foot of the screen, the hint over it, and the view frames the station in the
 * room above (StationView.layout). Vanilla's own container screen does every click, drag and key: the station square
 * under the cursor is simply the slot it finds there ({@link #isHovering}), so laying a stack, one item or half, the
 * drags that split a stack or lay one each, double-click, shift-click, number keys and Q all work as on the usual grid,
 * and the server checks them. Nothing outside the room picks a square: a click on the strip, the hint or a side panel
 * never reaches the station. A furnace's cooking and fuel show as two thin lines over the strip. Moving, jumping, the
 * inventory key or Esc leave.
 */
final class StationScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {
    private static final int GLASS = 0xC80F1724, EDGE = 0xFF7FC3FF, HINT = 0xFFB9C7D9, FIRE = 0xFFFFB45A;
    /** The station slots: the first {@code n} of the menu. */
    final int n;
    final StationView.Kind kind;
    /** The station slot under the cursor in the world (StationClient outlines it), or -1. */
    int hovered = -1;
    StationView.@Nullable Layout layout;
    private List<FormattedCharSequence> hint = List.of();
    // vanilla draws its slot highlight at a hovered slot's screen place, which is no longer the station's: while it
    // draws, the station's slots are never hovered
    private boolean drawing;
    // the slot under a point, asked once per point a frame
    private double pickX = Double.NaN, pickY = Double.NaN;
    private int picked = -1;

    StationScreen(M menu, Inventory inventory, Component title, StationView.Kind kind) {
        super(menu, inventory, title, StationView.IMAGE_W, StationView.IMAGE_H);
        this.kind = kind;
        this.n = kind.slots;
    }

    @Override
    protected void init() {
        super.init();
        // JEI's list and bookmarks sit at the sides of the screen's area: leave them the sides
        boolean sides = ModList.get().isLoaded("jei");
        StationView.Layout l = StationView.layout(width, height, 1, sides);
        hint = font.split(Component.translatable(kind.hint), l.rx1() - l.rx0());
        layout = StationView.layout(width, height, hint.size(), sides);
        leftPos = layout.left();
        topPos = layout.top();
        pickX = Double.NaN;
    }

    // the screen's area for other mods (JEI keeps clear of it): the room the station is framed in and the strip
    @Override
    public int getLeftPos() {
        return layout == null ? leftPos : Math.min(layout.rx0(), layout.px0());
    }

    @Override
    public int getTopPos() {
        return layout == null ? topPos : layout.ry0();
    }

    @Override
    public int getImageWidth() {
        return layout == null ? imageWidth : Math.max(layout.rx1(), layout.px1()) - getLeftPos();
    }

    @Override
    public int getImageHeight() {
        return layout == null ? imageHeight : layout.py1() - layout.ry0();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        StationView.Layout l = layout;
        if (l == null) return;
        g.fill(l.px0(), l.py0(), l.px1(), l.py1(), GLASS);
        g.outline(l.px0(), l.py0(), l.px1() - l.px0(), l.py1() - l.py0(), EDGE);
        if (menu instanceof AbstractFurnaceMenu f) {
            // the cooking (Holo blue) and what's left of the fuel (fire), as two thin lines over the strip
            int w = l.px1() - l.px0();
            g.fill(l.px0(), l.py0() - 5, l.px0() + (int) (w * f.getBurnProgress()), l.py0() - 3, EDGE);
            if (f.isLit()) g.fill(l.px0(), l.py0() - 2, l.px0() + (int) (w * f.getLitProgress()), l.py0() - 1, FIRE);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
        StationView.Layout l = layout;
        if (l == null) return;
        // centred in the room, over the strip (drawn from the image's corner)
        int mid = (l.rx0() + l.rx1()) / 2;
        for (int i = 0; i < hint.size(); i++) {
            FormattedCharSequence line = hint.get(i);
            g.text(font, line, mid - font.width(line) / 2 - leftPos, l.hintY() + i * StationView.LINE - topPos, HINT, true);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        pickX = Double.NaN; // the view breathes: ask again this frame
        hovered = slotAt(mouseX, mouseY);
        super.extractRenderState(g, mouseX, mouseY, a);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        drawing = true;
        try {
            super.extractContents(g, mouseX, mouseY, a);
        } finally {
            drawing = false;
        }
        // the square under the cursor is the hovered slot (its tooltip, the number keys, Q, the pick key)
        if (hoveredSlot == null && hovered >= 0) hoveredSlot = menu.getSlot(hovered);
    }

    /** The station slots live on the station: never drawn here. */
    @Override
    protected void extractSlot(GuiGraphicsExtractor g, Slot slot, int mouseX, int mouseY) {
        if (slot.index >= n) super.extractSlot(g, slot, mouseX, mouseY);
    }

    /** A station slot is under the cursor where the world shows its square, not at its old place on the screen. */
    @Override
    protected boolean isHovering(int left, int top, int w, int h, double xm, double ym) {
        if (w == 16 && h == 16) {
            for (int i = 0; i < n; i++) {
                Slot s = menu.getSlot(i);
                if (s.x == left && s.y == top) return !drawing && slotAt(xm, ym) == i;
            }
        }
        return super.isHovering(left, top, w, h, xm, ym);
    }

    /** The room and the strip are the screen's own: a click outside both drops what you carry, as off any screen. */
    @Override
    protected boolean hasClickedOutside(double mx, double my, int xo, int yo) {
        int x0 = getLeftPos(), y0 = getTopPos();
        return mx < x0 || my < y0 || mx >= x0 + getImageWidth() || my >= y0 + getImageHeight();
    }

    private int slotAt(double x, double y) {
        if (x != pickX || y != pickY) {
            pickX = x;
            pickY = y;
            picked = layout == null ? -1 : StationClient.pick(layout, x, y, width, height);
        }
        return picked;
    }

    /** A drag (laying a stack across squares) has passed over this station slot. */
    boolean dragging(int slot) {
        return isQuickCrafting && !quickCraftSlots.isEmpty() && quickCraftSlots.contains(menu.getSlot(slot));
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
