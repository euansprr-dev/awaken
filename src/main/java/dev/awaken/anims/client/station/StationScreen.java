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
import net.minecraft.world.item.ItemStack;
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
        extractStation(g);
    }

    /**
     * The stack on the pointer as it is shown: the whole of it, or what is left of it while a drag lays it out (as
     * vanilla's own pointer icon counts down).
     */
    ItemStack carriedShown(ItemStack carried) {
        if (isQuickCrafting && quickCraftSlots.size() > 1) {
            int left = carried.getCount();
            for (Slot q : quickCraftSlots) {
                if (!AbstractContainerMenu.canItemQuickReplace(q, carried, true) || !menu.canDragTo(q)) continue;
                int had = q.getItem().isEmpty() ? 0 : q.getItem().getCount(), most = Math.min(carried.getMaxStackSize(), q.getMaxStackSize(carried));
                int get = Math.min(AbstractContainerMenu.getQuickCraftPlaceCount(quickCraftSlots.size(), dragType(), carried) + had, most) - had;
                left -= Math.max(0, get);
            }
            return left <= 0 ? ItemStack.EMPTY : carried.copyWithCount(left);
        }
        return carried;
    }

    /** Which drag is under way (0 a left drag that splits evenly, 1 a right drag that lays one each, 2 a creative one). */
    private int dragType() {
        return dragButton == 1 ? 1 : dragButton == 2 ? 2 : 0;
    }

    private int dragButton;

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        dragButton = event.button();
        return super.mouseClicked(event, doubleClick);
    }

    /** While your hand holds the stack over the station it is drawn there, in the world: no icon on the pointer. */
    @Override
    public void extractCarriedItem(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (StationHands.carrying()) return;
        super.extractCarriedItem(g, mouseX, mouseY);
    }

    private final double[] spot = new double[3], at = new double[2], at2 = new double[2];

    /** A world point as the view draws it, in gui pixels. */
    private boolean project(double x, double y, double z, double[] out) {
        var cam = minecraft.gameRenderer.mainCamera();
        var o = cam.position();
        float aspect = (float) minecraft.getWindow().getWidth() / Math.max(1, minecraft.getWindow().getHeight());
        return StationView.project(x, y, z, o.x, o.y, o.z, cam.rotation(), cam.getFov(), aspect, width, height, out);
    }

    /**
     * On the band's glass, over the world: the count of every stack that lies on the station and of the one in your
     * hand, and whatever lies under your sleeve, shown through it so nothing you need to read is ever hidden (SPEC
     * §63.2, §63.3).
     */
    private void extractStation(GuiGraphicsExtractor g) {
        if (StationClient.station == null || StationClient.weight(0) < 0.9F) return;
        StationView.Station st = StationClient.station;
        if (kind == StationView.Kind.FURNACE) {
            // a furnace's three slots: the count under each stack's near corner, the one in your hand beside it
            for (int i = 0; i < n; i++) {
                ItemStack stack = menu.getSlot(i).getItem();
                StationView.spot(st, i, spot, 0);
                if (stack.getCount() > 1 && project(spot[0] + st.rx() * 0.1 - st.fx() * 0.1, spot[1] - 0.1, spot[2] + st.rz() * 0.1 - st.fz() * 0.1, at)) {
                    count(g, (int) Math.round(at[0]), (int) Math.round(at[1]), stack.getCount());
                }
            }
            ItemStack held = menu.getCarried();
            if (StationHands.carrying() && held.getCount() > 1 && StationHands.hit(spot) && project(spot[0] + st.rx() * 0.1, spot[1] - 0.1, spot[2] + st.rz() * 0.1, at)) {
                count(g, (int) Math.round(at[0]), (int) Math.round(at[1]), held.getCount());
            }
            return;
        }
        for (int i = 0; i < n; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (stack.isEmpty()) continue;
            StationView.spot(st, i, spot, 0);
            double half = StationView.reach(st, i, 0);
            // the square's width on screen, from its two near corners
            if (!project(spot[0] - st.rx() * half - st.fx() * half, spot[1], spot[2] - st.rz() * half - st.fz() * half, at)
                    || !project(spot[0] + st.rx() * half - st.fx() * half, spot[1], spot[2] + st.rz() * half - st.fz() * half, at2)) continue;
            double w = Math.abs(at2[0] - at[0]);
            if (i != hovered && StationHands.over(i, width, height) && project(spot[0], spot[1] + 0.02, spot[2], at)) {
                // under the sleeve: the item itself, on a small plate of glass where it lies
                float k = (float) Math.max(0.5, w / 18.0);
                int x = (int) Math.round(at[0]), y = (int) Math.round(at[1]);
                int r = Math.round(9 * k);
                g.fill(x - r, y - r, x + r, y + r, 0xB00F1724);
                g.outline(x - r, y - r, 2 * r, 2 * r, EDGE);
                g.pose().pushMatrix();
                g.pose().translate(x - 8 * k, y - 8 * k);
                g.pose().scale(k, k);
                g.item(stack, 0, 0);
                g.pose().popMatrix();
            }
            if (stack.getCount() > 1 && project(spot[0] + st.rx() * half * 0.8 - st.fx() * half * 0.8, spot[1] + 0.02, spot[2] + st.rz() * half * 0.8 - st.fz() * half * 0.8, at)) {
                count(g, (int) Math.round(at[0]), (int) Math.round(at[1]), stack.getCount());
            }
        }
        // the stack in your hand
        ItemStack carried = carriedShown(menu.getCarried());
        if (StationHands.carrying() && carried.getCount() > 1 && StationHands.hit(spot)
                && project(spot[0] + st.rx() * 0.07 - st.fx() * 0.07, spot[1] + 0.02, spot[2] + st.rz() * 0.07 - st.fz() * 0.07, at)) {
            count(g, (int) Math.round(at[0]), (int) Math.round(at[1]), carried.getCount());
        }
    }

    /** A count on a small tag of glass, its middle at (x, y). */
    private void count(GuiGraphicsExtractor g, int x, int y, int count) {
        String text = Integer.toString(count);
        int w = font.width(text);
        g.fill(x - w / 2 - 2, y - 5, x + w / 2 + 2, y + 5, 0xD00F1724);
        g.outline(x - w / 2 - 2, y - 5, w + 4, 10, EDGE);
        g.text(font, text, x - w / 2, y - 4, 0xFFE8F3FF, false);
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

    /** Dev (tours): the inventory image's corner, where the strip's slots are drawn from. */
    int slotLeft() {
        return leftPos;
    }

    int slotTop() {
        return topPos;
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
