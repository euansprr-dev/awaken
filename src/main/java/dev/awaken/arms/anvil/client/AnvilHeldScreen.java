package dev.awaken.arms.anvil.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.arms.anvil.AnvilPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The War Anvil's window for the weapon in your hand (SPEC §46.8): temper it, reforge it, attune it, each row with what it
 * costs (have and need), its button and one plain line for what stands in the way; the Armoury below for forging something
 * new. Draws what the server sent; the server judges the start. Closes if the weapon leaves your hand. Doesn't pause.
 */
final class AnvilHeldScreen extends Screen implements dev.awaken.signal.client.QuietScreen, dev.awaken.signal.client.ReadingScreen {
    private static final int EDGE = 0xFF7FC3FF, LANTERN = 0xFFFFCF7A, TITLE = 0xFFF4F6FB, LABEL = 0xFFAFC2D8, DIM = 0xFF6F8299, GOOD = 0xFF9FE6D6, BAD = 0xFFFF8A7A;

    final BlockPos pos;
    private final List<AnvilPayloads.Option> options;
    private final ItemStack weapon;
    private int px, py, pw, ph;
    /** Where each row's button was drawn this frame (x, y, w), and the Armoury's. */
    private final int[][] buttons;
    private int listX, listY, listW;

    AnvilHeldScreen(AnvilPayloads.Held held) {
        super(Component.literal("War Anvil"));
        pos = held.pos();
        options = held.options();
        var player = Minecraft.getInstance().player;
        weapon = player == null ? ItemStack.EMPTY : player.getMainHandItem().copy();
        buttons = new int[options.size()][3];
    }

    @Override
    protected void init() {
        pw = Math.min(320, width - 16);
        ph = Math.min(236, height - 16);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
    }

    @Override
    public void tick() {
        var player = Minecraft.getInstance().player;
        if (player == null || !ItemStack.isSameItem(player.getMainHandItem(), weapon)) onClose();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fillGradient(0, 0, width, height, 0x90050810, 0xB0050810);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        g.fill(px, py, px + pw, py + ph, 0xE80F1724);
        g.outline(px, py, pw, ph, 0xB07FC3FF);
        g.item(weapon, px + 10, py + 8);
        g.text(font, weapon.getHoverName(), px + 32, py + 9, TITLE, false);
        g.text(font, "At the War Anvil", px + 32, py + 19, DIM, false);
        int y = py + 34, inner = pw - 20;
        for (int i = 0; i < options.size(); i++) {
            AnvilPayloads.Option o = options.get(i);
            g.fill(px + 8, y - 3, px + pw - 8, y - 2, 0x402A3446);
            button(g, px + 10, y + 2, o.button(), o.ready(), mouseX, mouseY);
            buttons[i][0] = px + 10;
            buttons[i][1] = y + 2;
            buttons[i][2] = bw(o.button());
            // what it costs, right of the button: have / need, short ones in red
            int left = px + 10 + bw(o.button()) + 8, right = px + pw - 10, cx = left, cy = y + 6;
            for (AnvilPayloads.Cost c : o.costs()) {
                String s = c.label() + " " + c.have() + "/" + c.need();
                int w = font.width(s), colour = c.have() >= c.need() ? GOOD : BAD;
                if (cx > left && cx + w > right) {
                    cx = left;
                    cy += 10;
                }
                if (w <= right - left) {
                    g.text(font, s, cx, cy, colour, false);
                    cx += w + 10;
                    continue;
                }
                // too long for the row (a part named with where it's found): it wraps under itself, the next one below it
                List<FormattedCharSequence> parts = font.split(Component.literal(s), right - left);
                for (int k = 0; k < parts.size(); k++) g.text(font, parts.get(k), left, cy + 10 * k, colour, false);
                cy += 10 * (parts.size() - 1);
                cx = right;
            }
            y = Math.max(y + 22, cy + 11);
            List<FormattedCharSequence> lines = font.split(Component.literal(o.line()), inner);
            for (FormattedCharSequence l : lines) {
                g.text(font, l, px + 10, y, o.ready() ? LABEL : LANTERN, false);
                y += 10;
            }
            y += 5;
        }
        // at the foot, or up in the title's row, shorter, when the offers reach down to it (a part's long name, a short window)
        boolean foot = y - 5 <= py + ph - 26;
        String list = foot ? "Forge something new: the Armoury" : "The Armoury";
        listW = bw(list);
        listX = px + pw - 10 - listW;
        listY = foot ? py + ph - 24 : py + 6;
        button(g, listX, listY, list, true, mouseX, mouseY);
    }

    private int bw(String label) {
        return font.width(label) + 12;
    }

    private void button(GuiGraphicsExtractor g, int x, int y, String label, boolean enabled, int mx, int my) {
        int w = bw(label);
        boolean over = enabled && mx >= x && mx < x + w && my >= y && my < y + 16;
        g.fill(x, y, x + w, y + 16, over ? 0xF02A3A56 : 0xE0101622);
        g.outline(x, y, w, 16, enabled ? EDGE : 0xFF2A3446);
        g.text(font, label, x + 6, y + 4, enabled ? TITLE : DIM, false);
    }

    private static boolean hit(int x, int y, int w, double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + 16;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick);
        double mx = event.x(), my = event.y();
        for (int i = 0; i < options.size(); i++) {
            AnvilPayloads.Option o = options.get(i);
            if (!o.ready() || !hit(buttons[i][0], buttons[i][1], buttons[i][2], mx, my)) continue;
            // the server opens the scene (or sends this window again if it can't begin)
            AnvilClient.send(pos, AnvilPayloads.HELD, o.kind(), 0F, 0F);
            onClose();
            return true;
        }
        if (hit(listX, listY, listW, mx, my)) {
            AnvilClient.send(pos, AnvilPayloads.LIST, 0, 0F, 0F);
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
