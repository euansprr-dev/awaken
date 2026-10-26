package dev.awaken.village.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.village.VillageNet;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.List;
import java.util.Locale;

/**
 * The village notice board (SPEC §28.6): a timber board with up to three notes pinned to it, each a real need of the
 * village (what's wanted, how much is in, what it pays), and a "Hand over" on each that gives what you carry toward it.
 * The board's header says whose board it is and how the village knows you.
 */
final class NoticeBoardScreen extends Screen implements dev.awaken.signal.client.QuietScreen {
    private static final int WOOD = 0xFF4A3220, WOOD_DARK = 0xFF2E1F14, WOOD_LIGHT = 0xFF5E422B, GRAIN = 0x2A000000, PAPER = 0xFFEDE3CB,
            PAPER_EDGE = 0xFFD4C6A6, INK = 0xFF3A2A1C, INK_DIM = 0xFF7A6650, PIN = 0xFFB03A2E, GOLD = 0xFFE0B54A, EMERALD = 0xFF3FBF6A,
            BTN = 0xFF6B4A2E, BTN_ON = 0xFF8A6238, BTN_TEXT = 0xFFF4E8D0;

    private VillageNet.Board board;
    private int age, hovered = -1;

    NoticeBoardScreen(VillageNet.Board board) {
        super(Component.literal(board.village()));
        this.board = board;
    }

    void update(VillageNet.Board b) {
        this.board = b;
    }

    private int bw() {
        return Math.min(330, width - 24);
    }

    private int bh() {
        return Math.min(206, height - 24);
    }

    private int bx() {
        return (width - bw()) / 2;
    }

    private int by() {
        return (height - bh()) / 2;
    }

    private int noteW() {
        return (bw() - 32) / 3;
    }

    private int noteX(int i) {
        return bx() + 10 + i * (noteW() + 6);
    }

    private int noteY(int i) {
        return by() + 40 + (i == 1 ? 6 : i == 2 ? 2 : 0);
    }

    private int noteH() {
        return bh() - 56;
    }

    /** The "Hand over" strip at the foot of a note. */
    private int[] button(int i) {
        int x = noteX(i) + 6, w = noteW() - 12, y = noteY(i) + noteH() - 22;
        return new int[]{x, y, x + w, y + 15};
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fillGradient(0, 0, width, height, 0x60050608, 0xA0050608);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        float in = Mth.clamp((age + a) / 6f, 0, 1);
        int x0 = bx(), y0 = by() + (int) ((1 - in) * 8), x1 = x0 + bw(), y1 = y0 + bh();
        // the board: planks with grain, a darker frame, two posts' caps
        g.fill(x0 - 4, y0 - 4, x1 + 4, y1 + 4, WOOD_DARK);
        g.fill(x0, y0, x1, y1, WOOD);
        for (int k = 0; k < 6; k++) {
            int py = y0 + k * (bh() / 6);
            g.fill(x0, py, x1, py + 1, WOOD_DARK);
            for (int gx = x0 + (k * 37) % 23; gx < x1; gx += 41) g.fill(gx, py + 4, gx + 18, py + 5, GRAIN);
        }
        g.fill(x0, y0, x1, y0 + 2, WOOD_LIGHT);
        // the header plank
        String title = board.village().toUpperCase(Locale.ROOT) + "  ·  NOTICES";
        g.fill(x0 + 8, y0 + 8, x1 - 8, y0 + 30, WOOD_DARK);
        g.text(font, title, x0 + 14, y0 + 12, GOLD, false);
        g.text(font, Component.translatable("awaken.village.board.standing." + board.standing()), x0 + 14, y0 + 21, 0xFFCBB89A, false);
        String em = board.emeralds() + "";
        g.item(new ItemStack(Items.EMERALD), x1 - 30, y0 + 11);
        g.text(font, em, x1 - 34 - font.width(em), y0 + 16, EMERALD, false);
        hovered = -1;
        List<VillageNet.Note> notes = board.notes();
        if (notes.isEmpty()) {
            int nx = x0 + bw() / 2 - 70, ny = y0 + 60;
            paper(g, nx, ny, 140, 50);
            g.centeredText(font, Component.translatable("awaken.village.board.empty"), nx + 70, ny + 20, INK);
            return;
        }
        for (int i = 0; i < Math.min(3, notes.size()); i++) note(g, i, notes.get(i), mouseX, mouseY, y0 - by());
    }

    private void paper(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x50000000);
        g.fill(x, y, x + w, y + h, PAPER);
        g.outline(x, y, w, h, PAPER_EDGE);
        // the pin
        g.fill(x + w / 2 - 2, y + 2, x + w / 2 + 2, y + 6, PIN);
        g.fill(x + w / 2 - 1, y + 3, x + w / 2, y + 4, 0xFFE07A6A);
    }

    private void note(GuiGraphicsExtractor g, int i, VillageNet.Note n, int mx, int my, int dy) {
        int x = noteX(i), y = noteY(i) + dy, w = noteW(), h = noteH();
        paper(g, x, y, w, h);
        ItemStack icon = icon(n.item());
        g.item(icon, x + 6, y + 10);
        String what = n.need() + " × " + icon.getHoverName().getString();
        List<FormattedCharSequence> head = font.split(Component.literal(what), w - 30);
        int ty = y + 10;
        for (FormattedCharSequence line : head) {
            g.text(font, line, x + 26, ty, INK, false);
            ty += 9;
        }
        ty = Math.max(ty, y + 28) + 2;
        for (FormattedCharSequence line : font.split(Component.literal(n.text()), w - 12)) {
            if (ty > y + h - 52) break;
            g.text(font, line, x + 6, ty, INK_DIM, false);
            ty += 9;
        }
        // brought so far
        int barY = y + h - 44, bw = w - 12;
        g.fill(x + 6, barY, x + 6 + bw, barY + 4, 0xFFCFC0A0);
        int fill = (int) (bw * Math.min(1.0, n.have() / (double) Math.max(1, n.need())));
        g.fill(x + 6, barY, x + 6 + fill, barY + 4, 0xFF8A6A3A);
        g.text(font, n.have() + " / " + n.need(), x + 6, barY + 6, INK, false);
        String pay = n.pay() + " ";
        g.text(font, pay, x + w - 30 - font.width(pay), barY + 6, 0xFF2E8A50, false);
        g.item(new ItemStack(Items.EMERALD), x + w - 22, barY + 2);
        int[] b = button(i);
        b[1] += dy;
        b[3] += dy;
        boolean on = mx >= b[0] && mx < b[2] && my >= b[1] && my < b[3];
        if (on) hovered = i;
        g.fill(b[0], b[1], b[2], b[3], on ? BTN_ON : BTN);
        g.outline(b[0], b[1], b[2] - b[0], b[3] - b[1], WOOD_DARK);
        g.centeredText(font, Component.translatable("awaken.village.board.hand_over"), (b[0] + b[2]) / 2, b[1] + 4, BTN_TEXT);
    }

    private static ItemStack icon(String id) {
        var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
        return new ItemStack(item == Items.AIR ? Items.PAPER : item);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && hovered >= 0 && hovered < board.notes().size()) {
            ClientPacketDistributor.sendToServer(new VillageNet.Deliver(board.key(), board.notes().get(hovered).id()));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void tick() {
        age++;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
