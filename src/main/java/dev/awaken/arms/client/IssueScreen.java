package dev.awaken.arms.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.Awaken;
import dev.awaken.arms.Archetypes;
import dev.awaken.arms.net.ArmsPayloads;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Hale's issue (docs/arms-progression/PLAN.md §3.3): three cards side by side (the finished E weapon, its style's name and
 * what it is like). Click a card or press 1, 2 or 3. Esc closes it for now; Hale's line on the Now tracker brings it back.
 * Built like the first power's choice: it doesn't pause the game.
 */
final class IssueScreen extends Screen implements dev.awaken.signal.client.QuietScreen, dev.awaken.signal.client.ReadingScreen {
    private static final int EDGE = 0xFF7FC3FF, LANTERN = 0xFFFFCF7A, TITLE = 0xFFF4F6FB, LABEL = 0xFFAFC2D8, DIM = 0xFF6F8299, E_RANK = 0xFFF4F6FB;
    private static final int CARD_H = 112;

    private record Card(String style, String name, ItemStack weapon, String blurb) {}

    private final List<Card> cards = new ArrayList<>();
    private int px, py, pw, ph, cw;
    private int hovered = -1;
    private @Nullable String picked;
    private int pickedAge;

    IssueScreen(List<String> styles) {
        super(Component.translatable("screen.awaken.issue"));
        for (String id : styles) {
            Archetypes.Loaded st = Archetypes.get(id);
            var item = BuiltInRegistries.ITEM.getValue(Awaken.id("wildhide_" + id));
            if (st == null || item == net.minecraft.world.item.Items.AIR) continue;
            cards.add(new Card(id, st.def().name(), new ItemStack(item), Component.translatable("awaken.style." + id + ".blurb").getString()));
        }
    }

    @Override
    protected void init() {
        pw = Math.min(372, width - 16);
        ph = Math.min(200, height - 16);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        cw = (pw - 16 - 2 * 6) / Math.max(1, cards.size());
    }

    private int cardX(int i) {
        return px + 8 + i * (cw + 6);
    }

    private int cardY() {
        return py + 34;
    }

    private int cardAt(double mx, double my) {
        for (int i = 0; i < cards.size(); i++) {
            int x = cardX(i), y = cardY();
            if (mx >= x && mx < x + cw && my >= y && my < y + CARD_H) return i;
        }
        return -1;
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
        g.centeredText(font, title, px + pw / 2, py + 8, TITLE);
        g.centeredText(font, Component.literal("Every new hunter gets one weapon. Take whichever one you like."), px + pw / 2, py + 19, LABEL);
        hovered = picked == null ? cardAt(mouseX, mouseY) : -1;
        for (int i = 0; i < cards.size(); i++) drawCard(g, i, cards.get(i));
        int dy = cardY() + CARD_H + 6;
        if (picked != null) {
            g.centeredText(font, Component.literal("Hale hands it over..."), px + pw / 2, dy + 4, LANTERN);
        } else {
            g.centeredText(font, Component.literal("You also get its Blueprint and a spare Fang, to make more of them later."), px + pw / 2, dy + 2, LABEL);
        }
        g.centeredText(font, Component.literal("Click a card or press 1, 2 or 3     Esc: later"), px + pw / 2, py + ph - 12, DIM);
    }

    private void drawCard(GuiGraphicsExtractor g, int i, Card c) {
        int x = cardX(i), y = cardY();
        boolean on = i == hovered || c.style().equals(picked);
        g.fill(x, y, x + cw, y + CARD_H, on ? 0xF01A2436 : 0xE0101622);
        g.outline(x, y, cw, CARD_H, on ? EDGE : 0xFF3A4A60);
        g.text(font, String.valueOf(i + 1), x + 4, y + 4, on ? LANTERN : DIM, false);
        g.pose().pushMatrix();
        g.pose().translate(x + cw / 2F - 16, y + 10);
        g.pose().scale(2F, 2F);
        g.item(c.weapon(), 0, 0);
        g.pose().popMatrix();
        g.centeredText(font, Component.literal(c.name()), x + cw / 2, y + 48, E_RANK);
        g.centeredText(font, Component.literal("E-rank"), x + cw / 2, y + 59, DIM);
        List<FormattedCharSequence> lines = font.split(Component.literal(c.blurb()), cw - 10);
        for (int l = 0; l < Math.min(4, lines.size()); l++) {
            FormattedCharSequence line = lines.get(l);
            g.text(font, line, x + (cw - font.width(line)) / 2, y + 73 + l * 10, TITLE, false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && picked == null) {
            int i = cardAt(event.x(), event.y());
            if (i >= 0) {
                pick(i);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int k = event.key();
        int i = k == InputConstants.KEY_1 ? 0 : k == InputConstants.KEY_2 ? 1 : k == InputConstants.KEY_3 ? 2 : -1;
        if (i >= 0 && i < cards.size() && picked == null) {
            pick(i);
            return true;
        }
        return super.keyPressed(event);
    }

    private void pick(int i) {
        picked = cards.get(i).style();
        ClientPacketDistributor.sendToServer(new ArmsPayloads.IssuePick(picked));
    }

    @Override
    public void tick() {
        // the server hands the weapon over and says nothing back; close once it has had time to
        if (picked != null && ++pickedAge > 20) onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
