package dev.awaken.holo.client;

import dev.awaken.holo.ui.InventoryWells;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.UiCanvas;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The holo inventory's furniture, one drawing for both of its faces (docs/modules/holo.md "The inventory"): the flat
 * screen draws it through {@code GuiCanvas}, the 3D panel that unfolds from the wrist records it through
 * {@link HoloGraphics}, so the two match unit for unit where the panel lands. Panel units with the panel's top-left at
 * 0,0, which is how vanilla places its slots. In place of vanilla's printed slot squares every active slot gets a
 * projected well ({@link InventoryWells}): a dark glass socket with a hairline rim, the hotbar a little brighter, the
 * slot in your hand bright. Your figure stands in a framed alcove, an arrow of light runs from the crafting grid to its
 * result, and two faint dividers part the equipment, the pack and the hotbar. The glass itself, its rim, brackets and
 * the glows are the panel's ({@link HoloRender} in 3D, {@link HoloGuiLook#panel} flat).
 */
final class InventoryLook {
    private InventoryLook() {}

    /** Vanilla's inventory image, the panel's size in units. */
    static final int W = 176, H = 166;
    /** Where vanilla draws your figure (InventoryScreen.extractBackground). */
    static final int FIGURE_X0 = 26, FIGURE_Y0 = 8, FIGURE_X1 = 75, FIGURE_Y1 = 78;
    /** Vanilla's crafting title position (InventoryScreen: titleLabelX 97, titleLabelY 6). */
    static final int TITLE_X = 97, TITLE_Y = 6;
    private static final int WELL = 0x040A16;
    private static final float WELL_FILL = 0.58f;

    /** The wells, the figure's alcove, the crafting arrow and the dividers, faded by {@code alpha}. */
    static void furniture(UiCanvas c, AbstractContainerMenu menu, Inventory inv, float alpha) {
        if (alpha <= 0.01f) return;
        well(c, FIGURE_X0, FIGURE_Y0, FIGURE_X1 - FIGURE_X0, FIGURE_Y1 - FIGURE_Y0, 0.50f * alpha, 0.24f * alpha);
        brackets(c, FIGURE_X0, FIGURE_Y0, FIGURE_X1 - FIGURE_X0, FIGURE_Y1 - FIGURE_Y0, 4, Glass.a(Glass.EDGE, 0.70f * alpha));
        c.hLine(7, W - 8, 81, Glass.a(Glass.EDGE, 0.14f * alpha));
        c.hLine(7, W - 8, 139, Glass.a(Glass.EDGE, 0.10f * alpha));
        for (Slot s : menu.slots) {
            if (!s.isActive()) continue;
            InventoryWells.Kind k = InventoryWells.kind(s);
            int x = InventoryWells.x(s, k), y = InventoryWells.y(s, k), n = InventoryWells.size(k);
            float edge = switch (k) {
                case HOTBAR -> 0.32f;
                case RESULT -> 0.50f;
                default -> 0.22f;
            };
            if (InventoryWells.selected(s, inv)) edge = 0.85f;
            well(c, x, y, n, n, WELL_FILL * alpha, edge * alpha);
            if (k == InventoryWells.Kind.RESULT) brackets(c, x, y, n, n, 3, Glass.a(Glass.EDGE, 0.75f * alpha));
        }
        arrow(c, 136, 147, 35, Glass.a(Glass.EDGE, 0.70f * alpha));
    }

    /** The 3D panel's whole face: the furniture, every slot's item (or its empty-slot icon) and the crafting title. */
    static void snapshot(HoloGraphics g, Font font, AbstractContainerMenu menu, Inventory inv, Component title) {
        furniture(g, menu, inv, 1f);
        for (Slot s : menu.slots) {
            if (!s.isActive()) continue;
            ItemStack stack = s.getItem();
            if (stack.isEmpty()) {
                Identifier icon = s.getNoItemIcon();
                if (icon != null) g.sprite(icon, s.x, s.y, 16, 16, 0xFFFFFFFF);
                continue;
            }
            g.item(stack, s.x, s.y);
            g.itemDecorations(font, stack, s.x, s.y);
        }
        g.text(font, title.getVisualOrderText(), TITLE_X, TITLE_Y, Glass.a(Glass.EDGE, 1f), false);
    }

    /** A dark glass socket with a hairline rim and a faint glint under its top edge (glass, not print). */
    private static void well(UiCanvas c, int x, int y, int w, int h, float fill, float edge) {
        c.fill(x + 1, y + 1, x + w - 1, y + h - 1, Glass.a(WELL, fill));
        c.outline(x, y, w, h, Glass.a(Glass.EDGE, edge));
        c.hLine(x + 1, x + w - 2, y + 1, Glass.a(Glass.EDGE, edge * 0.35f));
    }

    /** Brighter corners on a rectangle's rim, the System's mark (the panel's brackets in miniature; the wells beside
     *  your figure leave no room outside it). */
    private static void brackets(UiCanvas c, int x, int y, int w, int h, int len, int col) {
        int l = x, t = y, r = x + w - 1, b = y + h - 1;
        c.hLine(l, l + len - 1, t, col);
        c.vLine(l, t, t + len, col);
        c.hLine(r - len + 1, r, t, col);
        c.vLine(r, t, t + len, col);
        c.hLine(l, l + len - 1, b, col);
        c.vLine(l, b - len, b, col);
        c.hLine(r - len + 1, r, b, col);
        c.vLine(r, b - len, b, col);
    }

    /** An arrow of light from {@code x0} to {@code x1} along row {@code y}: a shaft and a stepped head. */
    private static void arrow(UiCanvas c, int x0, int x1, int y, int col) {
        c.fill(x0, y, x1 - 3, y + 1, col);
        for (int i = 0; i < 4; i++) c.fill(x1 - 4 + i, y - 3 + i, x1 - 3 + i, y + 4 - i, col);
    }
}
