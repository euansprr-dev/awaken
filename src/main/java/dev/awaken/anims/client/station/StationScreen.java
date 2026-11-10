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
    private static final ItemStack BOTTLE = new ItemStack(net.minecraft.world.item.Items.EXPERIENCE_BOTTLE);
    /** The station slots: the first {@code n} of the menu. */
    final int n;
    final StationView.Kind kind;
    /** The station slot under the cursor in the world (StationClient outlines it), or -1. */
    int hovered = -1;
    StationView.@Nullable Layout layout;
    private List<FormattedCharSequence> hint = List.of();
    /** When this screen opened: its hint is read once and then leaves the view to the work (SPEC §64.1). */
    private final long openedNs = System.nanoTime();
    private static final float HINT_HOLD = 3.5F;
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
        if (menu instanceof net.minecraft.world.inventory.AnvilMenu) {
            // the name, on a line of glass at the foot of the room: click it to write (vanilla's own box and packet)
            String before = name == null ? "" : name.getValue();
            int w = Math.min(150, layout.rx1() - layout.rx0() - 20);
            name = new net.minecraft.client.gui.components.EditBox(font, width / 2 - w / 2, layout.ry1() - 16, w, 12, Component.translatable("container.repair"));
            name.setTextColor(0xFFE8F3FF);
            name.setTextColorUneditable(0xFF8094AA);
            name.setBordered(false);
            name.setMaxLength(50);
            name.setValue(before);
            name.setResponder(this::named);
            name.setEditable(menu.getSlot(0).hasItem());
            addRenderableWidget(name);
            named0 = menu.getSlot(0).getItem().copy();
        }
    }

    private net.minecraft.client.gui.components.@Nullable EditBox name;
    private ItemStack named0 = ItemStack.EMPTY;

    /** The anvil's name line changed: vanilla's own rule and packet. */
    private void named(String text) {
        if (!(menu instanceof net.minecraft.world.inventory.AnvilMenu anvil)) return;
        Slot slot = menu.getSlot(0);
        if (!slot.hasItem()) return;
        String newName = text;
        if (!slot.getItem().has(net.minecraft.core.component.DataComponents.CUSTOM_NAME) && newName.equals(slot.getItem().getHoverName().getString())) newName = "";
        if (anvil.setItemName(newName)) minecraft.player.connection.send(new net.minecraft.network.protocol.game.ServerboundRenameItemPacket(newName));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (name != null) {
            // the piece on the face changed: its name is on the line
            ItemStack now = menu.getSlot(0).getItem();
            if (!ItemStack.matches(now, named0)) {
                named0 = now.copy();
                name.setValue(now.isEmpty() ? "" : now.getHoverName().getString());
                name.setEditable(!now.isEmpty());
            }
            minecraft.player.experienceDisplayStartTick = minecraft.player.tickCount;
        }
    }

    // ---- a stonecutter's choices: a rail of glass at the foot of the room ---------------------------------------------

    private static final int CHIP = 18;

    /** The rail's place: its left, its top, how many to a row, how many rows. */
    private int[] rail() {
        int count = menu instanceof net.minecraft.world.inventory.StonecutterMenu sc && sc.hasInputItem() ? sc.getVisibleRecipes().size() : 0;
        if (count == 0 || layout == null) return new int[]{0, 0, 0, 0, 0};
        int perRow = Math.max(1, Math.min(count, (layout.rx1() - layout.rx0() - 16) / CHIP)), rows = Math.min(3, (count + perRow - 1) / perRow);
        return new int[]{width / 2 - perRow * CHIP / 2, layout.ry1() - rows * CHIP - 2, perRow, rows, Math.min(count, perRow * rows)};
    }

    private void extractRail(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (!(menu instanceof net.minecraft.world.inventory.StonecutterMenu sc)) return;
        int[] r = rail();
        if (r[4] == 0) return;
        var context = net.minecraft.world.item.crafting.display.SlotDisplayContext.fromLevel(minecraft.level);
        var recipes = sc.getVisibleRecipes();
        for (int i = 0; i < r[4]; i++) {
            int x = r[0] + i % r[2] * CHIP, y = r[1] + i / r[2] * CHIP;
            boolean on = i == sc.getSelectedRecipeIndex(), over = mouseX >= x && mouseX < x + CHIP && mouseY >= y && mouseY < y + CHIP;
            g.fill(x, y, x + CHIP - 1, y + CHIP - 1, on ? 0xD01B3A5C : 0xB00F1724);
            g.outline(x, y, CHIP - 1, CHIP - 1, on || over ? EDGE : 0x667FC3FF);
            ItemStack icon = recipes.entries().get(i).recipe().optionDisplay().resolveForFirstStack(context);
            g.item(icon, x + 1, y + 1);
            if (over) g.setTooltipForNextFrame(font, icon, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        dragButton = event.button();
        if (menu instanceof net.minecraft.world.inventory.StonecutterMenu sc) {
            int[] r = rail();
            for (int i = 0; i < r[4]; i++) {
                int x = r[0] + i % r[2] * CHIP, y = r[1] + i / r[2] * CHIP;
                if (event.x() >= x && event.x() < x + CHIP && event.y() >= y && event.y() < y + CHIP && sc.clickMenuButton(minecraft.player, i)) {
                    minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F));
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
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
        if (name != null) {
            g.fill(name.getX() - 4, name.getY() - 3, name.getX() + name.getWidth() + 4, name.getY() + 11, GLASS);
            g.outline(name.getX() - 4, name.getY() - 3, name.getWidth() + 8, 14, name.isFocused() ? EDGE : 0x667FC3FF);
            if (menu instanceof net.minecraft.world.inventory.AnvilMenu anvil && anvil.getCost() > 0 && menu.getSlot(2).hasItem()) {
                // what it costs: levels as a bottle o' enchanting and their number (SPEC §57.10), red when you can't pay
                boolean tooMuch = anvil.getCost() >= 40 && !minecraft.player.hasInfiniteMaterials(), short_ = !menu.getSlot(2).mayPickup(minecraft.player);
                int x = name.getX() + name.getWidth() + 10, y = name.getY() - 3;
                g.item(BOTTLE, x, y - 1);
                g.text(font, tooMuch ? Component.translatable("container.repair.expensive") : Component.literal(Integer.toString(anvil.getCost())), x + 18, y + 3,
                        tooMuch || short_ ? 0xFFFF6060 : 0xFF80FF20, true);
            }
        }
        if (menu instanceof net.minecraft.world.inventory.BrewingStandMenu b) {
            // the brewing (Holo blue) and the blaze powder left (fire), as two thin lines over the strip
            int w = l.px1() - l.px0();
            if (b.getBrewingTicks() > 0) g.fill(l.px0(), l.py0() - 5, l.px0() + (int) (w * (1 - b.getBrewingTicks() / 400F)), l.py0() - 3, EDGE);
            if (b.getFuel() > 0) g.fill(l.px0(), l.py0() - 2, l.px0() + (int) (w * Math.min(1F, b.getFuel() / 20F)), l.py0() - 1, FIRE);
        }
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
        // read once, then out of the way of the work: whole for 3.5 s, gone over the next 0.6
        float seen = (System.nanoTime() - openedNs) / 1e9F;
        float a = seen < HINT_HOLD ? 1F : 1F - (seen - HINT_HOLD) / 0.6F;
        if (a <= 0.06F) return;
        int col = (Math.round(255 * Math.min(1F, a)) << 24) | (HINT & 0xFFFFFF);
        int mid = (l.rx0() + l.rx1()) / 2;
        for (int i = 0; i < hint.size(); i++) {
            FormattedCharSequence line = hint.get(i);
            g.text(font, line, mid - font.width(line) / 2 - leftPos, l.hintY() + i * StationView.LINE - topPos, col, a > 0.6F);
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
        extractRail(g, mouseX, mouseY);
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
     * §64.2, §64.3).
     */
    private void extractStation(GuiGraphicsExtractor g) {
        if (StationClient.station == null || StationClient.weight(0) < 0.9F) return;
        StationView.Station st = StationClient.station;
        if (kind == StationView.Kind.FURNACE) {
            // a furnace's three slots: the count under each stack's near corner, the one in your hand beside it
            for (int i = 0; i < n; i++) {
                ItemStack stack = menu.getSlot(i).getItem();
                if (stack.getCount() <= 1) counted[i] = 0;
                StationView.spot(st, i, spot, 0);
                if (stack.getCount() > 1 && project(spot[0] + st.rx() * 0.1 - st.fx() * 0.1, spot[1] - 0.1, spot[2] + st.rz() * 0.1 - st.fz() * 0.1, at)) {
                    count(g, (int) Math.round(at[0]), (int) Math.round(at[1]), stack.getCount(), i);
                }
            }
            ItemStack held = menu.getCarried();
            if (held.getCount() <= 1) counted[10] = 0;
            if (StationHands.carrying() && held.getCount() > 1 && StationHands.hit(spot) && project(spot[0] + st.rx() * 0.1, spot[1] - 0.1, spot[2] + st.rz() * 0.1, at)) {
                count(g, (int) Math.round(at[0]), (int) Math.round(at[1]), held.getCount(), 10);
            }
            return;
        }
        for (int i = 0; i < n; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (stack.getCount() <= 1) counted[i] = 0;
            if (stack.isEmpty()) continue;
            StationView.spot(st, i, spot, 0);
            double half = StationView.reach(st, i, 0);
            // the square's width on screen, from its two near corners
            if (!project(spot[0] - st.rx() * half - st.fx() * half, spot[1], spot[2] - st.rz() * half - st.fz() * half, at)
                    || !project(spot[0] + st.rx() * half - st.fx() * half, spot[1], spot[2] + st.rz() * half - st.fz() * half, at2)) continue;
            double w = Math.abs(at2[0] - at[0]);
            if (i != hovered && StationHands.over(i, width, height) && project(spot[0], spot[1] + 0.02, spot[2], at)) {
                // under the sleeve: the item itself, on a small plate of glass where it lies
                // (two thirds of the square's own width: the game's icon stands taller than a thing lying flat)
                float k = (float) Math.max(0.45, w / 18.0 * 0.66);
                int x = (int) Math.round(at[0]), y = (int) Math.round(at[1]);
                int r = Math.round(9 * k);
                g.fill(x - r, y - r, x + r, y + r, 0x900F1724);
                g.outline(x - r, y - r, 2 * r, 2 * r, EDGE);
                g.pose().pushMatrix();
                g.pose().translate(x - 8 * k, y - 8 * k);
                g.pose().scale(k, k);
                g.item(stack, 0, 0);
                g.pose().popMatrix();
            }
            if (stack.getCount() > 1 && project(spot[0] + st.rx() * half * 0.8 - st.fx() * half * 0.8, spot[1] + 0.02, spot[2] + st.rz() * half * 0.8 - st.fz() * half * 0.8, at)) {
                count(g, (int) Math.round(at[0]), (int) Math.round(at[1]), stack.getCount(), i);
            }
        }
        // the stack in your hand
        ItemStack carried = carriedShown(menu.getCarried());
        if (carried.getCount() <= 1) counted[10] = 0;
        if (StationHands.carrying() && carried.getCount() > 1 && StationHands.hit(spot)
                && project(spot[0] + st.rx() * 0.07 - st.fx() * 0.07, spot[1] + 0.02, spot[2] + st.rz() * 0.07 - st.fz() * 0.07, at)) {
            count(g, (int) Math.round(at[0]), (int) Math.round(at[1]), carried.getCount(), 10);
        }
    }

    /** What each tag last read, what it read before that and when it changed: a count rolls to its new number. */
    private final int[] counted = new int[11], countedWas = new int[11];
    private final long[] countedAt = new long[11];
    private static final long ROLL_NS = 140_000_000L;

    /**
     * A count on a small tag of glass, its middle at (x, y). A tag that knows its slot (the last is the stack in your
     * hand) rolls to a new number: the old one leaves upward as a larger one comes in from below, and the other way
     * for a smaller (SPEC §64.3).
     */
    private void count(GuiGraphicsExtractor g, int x, int y, int count, int slot) {
        String text = Integer.toString(count);
        int w = font.width(text);
        long now = System.nanoTime();
        if (slot >= 0 && counted[slot] != count) {
            countedWas[slot] = counted[slot];
            counted[slot] = count;
            countedAt[slot] = countedWas[slot] > 1 ? now : 0; // a tag that has just appeared has nothing to roll from
        }
        float u = slot >= 0 && countedAt[slot] != 0 ? (now - countedAt[slot]) / (float) ROLL_NS : 1F;
        if (u >= 1F || dev.awaken.core.api.FirstPerson.reducedMotion.getAsBoolean()) {
            g.fill(x - w / 2 - 2, y - 5, x + w / 2 + 2, y + 5, 0xD00F1724);
            g.outline(x - w / 2 - 2, y - 5, w + 4, 10, EDGE);
            g.text(font, text, x - w / 2, y - 4, 0xFFE8F3FF, false);
            return;
        }
        String old = Integer.toString(countedWas[slot]);
        int wide = Math.max(w, font.width(old));
        int dir = count > countedWas[slot] ? 1 : -1, d = Math.round(9 * u);
        g.fill(x - wide / 2 - 2, y - 5, x + wide / 2 + 2, y + 5, 0xD00F1724);
        g.outline(x - wide / 2 - 2, y - 5, wide + 4, 10, EDGE);
        g.enableScissor(x - wide / 2 - 1, y - 4, x + wide / 2 + 1, y + 4);
        g.text(font, old, x - font.width(old) / 2, y - 4 - dir * d, 0xFFE8F3FF, false);
        g.text(font, text, x - w / 2, y - 4 + dir * (9 - d), 0xFFE8F3FF, false);
        g.disableScissor();
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
        if (name != null && name.isFocused() && !event.isEscape()) return name.keyPressed(event) || name.canConsumeInput() || super.keyPressed(event);
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
