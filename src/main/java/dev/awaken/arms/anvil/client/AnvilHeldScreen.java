package dev.awaken.arms.anvil.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.arms.anvil.AnvilPayloads;
import dev.awaken.hearth.Upgrades;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The War Anvil's window for the weapon in your hand (SPEC §46.8): three cards across the top (temper it, reforge it, attune
 * it, each with what it would do in a few words) and one page below for the card you've picked: the numbers before and
 * after, what it does in plain words, what it costs, what stands in the way and its button. Every word and number comes from
 * the server, from the code that makes the change; the server judges the start. Closes if the weapon leaves your hand.
 * Doesn't pause.
 */
final class AnvilHeldScreen extends Screen implements dev.awaken.signal.client.QuietScreen, dev.awaken.signal.client.ReadingScreen {
    private static final int EDGE = 0xFF7FC3FF, LANTERN = 0xFFFFCF7A, TITLE = 0xFFF4F6FB, LABEL = 0xFFAFC2D8, DIM = 0xFF6F8299, QUIET = 0xFF8799AE, GOOD = 0xFF9FE6D6, BAD = 0xFFFF8A7A;
    private static final int PAGE = 0xF0141E30, RULE = 0xFF2A3446, TAB = 0xC07FC3FF;
    private static final int CARDS_AT = 27, CARD_H = 28, LINE = 10, BUTTON_H = 16;
    private static final String LIST = "The Armoury";

    final BlockPos pos;
    private final List<AnvilPayloads.Option> options;
    /** Each option's costs, shortest first, so they pack into the fewest lines. */
    private final List<List<AnvilPayloads.Cost>> costs = new ArrayList<>();
    private final ItemStack weapon;
    private final int[] scroll;
    private int selected;
    private int px, py, pw, ph;
    private int actionX, actionY, listX, listY, maxScroll;

    AnvilHeldScreen(AnvilPayloads.Held held) {
        super(Component.literal("War Anvil"));
        pos = held.pos();
        options = held.options();
        var player = Minecraft.getInstance().player;
        weapon = player == null ? ItemStack.EMPTY : player.getMainHandItem().copy();
        scroll = new int[options.size()];
        for (int i = options.size() - 1; i >= 0; i--) if (options.get(i).ready()) selected = i;
    }

    @Override
    protected void init() {
        pw = Math.min(404, width - 16);
        ph = Math.min(236, height - 16);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        costs.clear();
        for (AnvilPayloads.Option o : options) {
            List<AnvilPayloads.Cost> sorted = new ArrayList<>(o.costs());
            sorted.sort(Comparator.comparingInt(c -> font.width(costText(c))));
            costs.add(sorted);
        }
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
        g.item(weapon, px + 10, py + 5);
        g.text(font, weapon.getHoverName(), px + 32, py + 6, TITLE, false);
        g.text(font, "At the War Anvil", px + 32, py + 16, DIM, false);
        listX = px + pw - 10 - bw(LIST);
        listY = py + 6;
        button(g, listX, listY, LIST, true, mouseX, mouseY);
        page(g, options.get(selected), mouseX, mouseY);
        for (int i = 0; i < options.size(); i++) card(g, i, mouseX, mouseY);
    }

    // ---- the cards ---------------------------------------------------------------------------------------------------

    private int cardX(int i) {
        return px + 10 + i * (pw - 20 + 4) / options.size();
    }

    private int cardW(int i) {
        return cardX(i + 1) - 4 - cardX(i);
    }

    private void card(GuiGraphicsExtractor g, int i, int mx, int my) {
        AnvilPayloads.Option o = options.get(i);
        int x = cardX(i), y = py + CARDS_AT, w = cardW(i);
        boolean on = i == selected, over = !on && inside(x, y, w, CARD_H, mx, my);
        g.fill(x, y, x + w, y + CARD_H, on ? PAGE : over ? 0xE0182436 : 0xD00D1420);
        g.outline(x, y, w, CARD_H, on ? TAB : RULE);
        if (on) g.fill(x + 1, y + CARD_H - 1, x + w - 1, y + CARD_H + 1, PAGE); // it runs on into its page
        g.fill(x + w - 9, y + 6, x + w - 5, y + 10, o.ready() ? GOOD : RULE);
        g.text(font, o.name(), x + 7, y + 5, on ? TITLE : LABEL, false);
        g.text(font, font.plainSubstrByWidth(o.headline(), w - 12), x + 7, y + 16, o.ready() ? GOOD : LANTERN, false);
    }

    // ---- the page ----------------------------------------------------------------------------------------------------

    private void page(GuiGraphicsExtractor g, AnvilPayloads.Option o, int mx, int my) {
        int left = px + 10, right = px + pw - 10, top = py + CARDS_AT + CARD_H, bottom = py + ph - 7;
        g.fill(left, top, right, bottom, PAGE);
        g.outline(left, top, right - left, bottom - top, TAB);
        int x = left + 8, w = right - left - 16;

        // the foot: what stands in the way (or that it's ready) and the button
        int action = bw(o.button());
        List<FormattedCharSequence> status = font.split(Component.literal(o.line()), w - action - 10);
        int footH = Math.max(BUTTON_H, status.size() * LINE), footY = bottom - 5 - footH;
        g.fill(x, footY - 4, x + w, footY - 3, RULE);
        int sy = footY + (footH - status.size() * LINE) / 2 + 1;
        for (FormattedCharSequence l : status) {
            g.text(font, l, x, sy, o.ready() ? LABEL : LANTERN, false);
            sy += LINE;
        }
        actionX = x + w - action;
        actionY = footY + (footH - BUTTON_H) / 2;
        button(g, actionX, actionY, o.button(), o.ready(), mx, my);

        // above it what it would do, scrolled if a small screen can't hold it all
        int viewTop = top + 5, viewBottom = footY - 6;
        int at = scroll[selected] = Math.min(scroll[selected], maxScroll);
        g.enableScissor(x - 1, viewTop, x + w + 1, viewBottom);
        int y = changes(g, o, x, viewTop - at, w);
        y = says(g, o, x, y, w);
        y = costs(g, selected, x, y, w);
        g.disableScissor();
        int content = y + at - viewTop, view = viewBottom - viewTop;
        maxScroll = Math.max(0, content - view);
        if (maxScroll > 0) {
            int thumb = Math.max(10, view * view / content), travel = view - thumb;
            int ty = viewTop + travel * Math.min(at, maxScroll) / maxScroll;
            g.fill(right - 4, viewTop, right - 2, viewBottom, RULE);
            g.fill(right - 4, ty, right - 2, ty + thumb, EDGE);
        }
    }

    /** The numbers: what each is now, an arrow, what it would be; under it what a Flawless forging would make it. */
    private int changes(GuiGraphicsExtractor g, AnvilPayloads.Option o, int x, int y, int w) {
        if (o.changes().isEmpty()) return y;
        int cell = w / o.changes().size();
        boolean flawless = false;
        for (int i = 0; i < o.changes().size(); i++) {
            Upgrades.Change c = o.changes().get(i);
            int cx = x + i * cell;
            g.text(font, c.label(), cx, y, DIM, false);
            g.text(font, c.from(), cx, y + LINE, LABEL, false);
            int ax = cx + font.width(c.from()) + 3;
            arrow(g, ax, y + LINE + 1);
            g.text(font, c.to(), ax + 10, y + LINE, GOOD, false);
            if (!c.best().isEmpty()) {
                g.text(font, c.best() + " if Flawless", cx, y + 2 * LINE, LANTERN, false);
                flawless = true;
            }
        }
        return y + (flawless ? 3 : 2) * LINE + 4;
    }

    private static void arrow(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y + 3, x + 6, y + 4, EDGE);
        g.fill(x + 3, y + 1, x + 4, y + 6, EDGE);
        g.fill(x + 4, y + 2, x + 5, y + 5, EDGE);
        g.fill(x + 5, y + 3, x + 7, y + 4, EDGE);
    }

    private int says(GuiGraphicsExtractor g, AnvilPayloads.Option o, int x, int y, int w) {
        for (AnvilPayloads.Say s : o.says()) {
            int colour = switch (s.tone()) {
                case AnvilPayloads.Say.CHOSEN -> LANTERN;
                case AnvilPayloads.Say.NOW -> GOOD;
                case AnvilPayloads.Say.QUIET -> QUIET;
                default -> LABEL;
            };
            for (FormattedCharSequence l : font.split(Component.literal(s.text()), w)) {
                g.text(font, l, x, y, colour, false);
                y += LINE;
            }
            if (s.tone() == AnvilPayloads.Say.PLAIN) y += 2;
        }
        return y + 2;
    }

    /** What it takes: have and need, short ones in red. */
    private int costs(GuiGraphicsExtractor g, int option, int x, int y, int w) {
        List<AnvilPayloads.Cost> list = costs.get(option);
        if (list.isEmpty()) return y;
        g.text(font, "It takes", x, y, DIM, false);
        y += LINE;
        int cx = x, right = x + w;
        for (AnvilPayloads.Cost c : list) {
            String s = costText(c);
            int cw = font.width(s), colour = c.have() >= c.need() ? GOOD : BAD;
            if (cx > x && cx + cw > right) {
                cx = x;
                y += LINE;
            }
            if (cw <= w) {
                g.text(font, s, cx, y, colour, false);
                cx += cw + 10;
                continue;
            }
            // too long for the line (a part named with where it's found, on a small screen): it wraps under itself
            List<FormattedCharSequence> parts = font.split(Component.literal(s), w);
            for (int k = 0; k < parts.size(); k++) g.text(font, parts.get(k), x, y + LINE * k, colour, false);
            y += LINE * (parts.size() - 1);
            cx = right;
        }
        return y + LINE;
    }

    private static String costText(AnvilPayloads.Cost c) {
        return c.label() + " " + c.have() + "/" + c.need();
    }

    // ---- input -------------------------------------------------------------------------------------------------------

    private int bw(String label) {
        return font.width(label) + 12;
    }

    private void button(GuiGraphicsExtractor g, int x, int y, String label, boolean enabled, int mx, int my) {
        int w = bw(label);
        boolean over = enabled && inside(x, y, w, BUTTON_H, mx, my);
        g.fill(x, y, x + w, y + BUTTON_H, over ? 0xF02A3A56 : 0xE0101622);
        g.outline(x, y, w, BUTTON_H, enabled ? EDGE : RULE);
        g.text(font, label, x + 6, y + 4, enabled ? TITLE : DIM, false);
    }

    private static boolean inside(int x, int y, int w, int h, double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick);
        double mx = event.x(), my = event.y();
        for (int i = 0; i < options.size(); i++) {
            if (!inside(cardX(i), py + CARDS_AT, cardW(i), CARD_H, mx, my)) continue;
            if (i != selected) {
                selected = i;
                maxScroll = Integer.MAX_VALUE; // this page's height is measured when it's drawn
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.2F, 0.4F));
            }
            return true;
        }
        AnvilPayloads.Option o = options.get(selected);
        if (o.ready() && inside(actionX, actionY, bw(o.button()), BUTTON_H, mx, my)) {
            // the server opens the scene (or sends this window again if it can't begin)
            AnvilClient.send(pos, AnvilPayloads.HELD, o.kind(), 0F, 0F);
            onClose();
            return true;
        }
        if (inside(listX, listY, bw(LIST), BUTTON_H, mx, my)) {
            AnvilClient.send(pos, AnvilPayloads.LIST, 0, 0F, 0F);
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll <= 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        scroll[selected] = Math.clamp(scroll[selected] - (int) Math.round(scrollY * LINE * 2), 0, maxScroll);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
