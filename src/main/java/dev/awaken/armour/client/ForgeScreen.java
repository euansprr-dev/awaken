package dev.awaken.armour.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.Awaken;
import dev.awaken.armour.ArmourForge;
import dev.awaken.armour.ArmourSet;
import dev.awaken.armour.ForgeSong;
import dev.awaken.armour.net.ArmourPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.ArmorType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Fitting Stand's list (SPEC §44.4): the sets your rank can forge, one at a time, each set's four pieces with what
 * they take and whether you have it, and Forge it (or From memory). What the stand still needs near it is said plainly.
 * The camera stands back so the stand, the anvil and the Forge are in view beside the list. Doesn't pause the game.
 */
final class ForgeScreen extends Screen implements dev.awaken.signal.client.QuietScreen, dev.awaken.signal.client.ReadingScreen {
    static final int EDGE = 0xFF7FC3FF, LANTERN = 0xFFFFCF7A, TITLE = 0xFFF4F6FB, LABEL = 0xFFAFC2D8, DIM = 0xFF6F8299, GOOD = 0xFF9FE6D6, BAD = 0xFFFF8A7A;
    private static final int ROW = 18;

    private final ArmourPayloads.Offers offers;
    private final @Nullable String busy;
    private final List<ArmourSet> sets = new ArrayList<>();
    private int set, piece;
    private int px, py, pw, ph, rowsY, buttonY;
    private boolean shot;

    ForgeScreen(ArmourPayloads.Offers offers, @Nullable String busy) {
        super(Component.translatable("block.awaken.fitting_stand"));
        this.offers = offers;
        this.busy = busy;
        ArmourSet[] all = ArmourSet.values();
        for (ArmourPayloads.Offer o : offers.offers()) {
            ArmourSet s = all[o.piece() / 4];
            if (!sets.contains(s)) sets.add(s);
        }
        // open on the best set you can make something of now, else the highest you know
        set = Math.max(0, sets.size() - 1);
        for (int i = sets.size() - 1; i >= 0; i--) {
            if (anyCan(sets.get(i))) {
                set = i;
                break;
            }
        }
        piece = 0;
        for (int i = 0; i < 4; i++) {
            ArmourPayloads.Offer o = offer(i);
            if (o != null && (o.flags() & ArmourPayloads.CAN) != 0) {
                piece = i;
                break;
            }
        }
    }

    private boolean anyCan(ArmourSet s) {
        for (ArmourPayloads.Offer o : offers.offers())
            if (o.piece() / 4 == s.ordinal() && (o.flags() & ArmourPayloads.CAN) != 0) return true;
        return false;
    }

    private @Nullable ArmourSet current() {
        return sets.isEmpty() ? null : sets.get(set);
    }

    private ArmourPayloads.@Nullable Offer offer(int slot) {
        ArmourSet s = current();
        if (s == null) return null;
        for (ArmourPayloads.Offer o : offers.offers()) if (o.piece() == s.ordinal() * 4 + slot) return o;
        return null;
    }

    static ItemStack item(ArmourSet s, ArmorType piece) {
        String name = switch (piece) {
            case HELMET -> "helmet";
            case CHESTPLATE -> "chestplate";
            case LEGGINGS -> "leggings";
            default -> "boots";
        };
        var item = BuiltInRegistries.ITEM.getValue(Awaken.id(s.id + "_" + name));
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    @Override
    protected void init() {
        pw = Math.min(232, width / 2 - 10);
        ph = Math.min(height - 36, 268);
        px = 14;
        py = (height - ph) / 2;
        if (!shot && minecraft != null && minecraft.level != null) {
            shot = true;
            var level = minecraft.level;
            ForgeCamera.cut(ForgeShots.wide(offers.pos(), ArmourForge.anvil(level, offers.pos()), ArmourForge.forge(level, offers.pos())),
                    ForgeClient.now(0), ForgeCamera.active() ? 24 : 16);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        letterbox(g, width, height);
    }

    /** The bars above and below while the camera holds the view, and a soft shade on the list's side. */
    static void letterbox(GuiGraphicsExtractor g, int width, int height) {
        int bar = Math.max(10, height / 14);
        g.fill(0, 0, width, bar, 0xFF000000);
        g.fill(0, height - bar, width, height, 0xFF000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        g.fillGradient(px - 14, 0, px + pw + 40, height, 0x60050810, 0x60050810);
        g.fill(px, py, px + pw, py + ph, 0xE80F1724);
        g.outline(px, py, pw, ph, 0xB07FC3FF);
        g.text(font, title, px + 8, py + 7, TITLE, false);
        dev.awaken.progression.Rank[] ranks = dev.awaken.progression.Rank.values();
        dev.awaken.progression.Rank rank = ranks[Math.min(offers.rank(), ranks.length - 1)];
        String r = rank.label + "-rank";
        g.text(font, r, px + pw - 8 - font.width(r), py + 7, 0xFF000000 | rank.colour, false);
        g.fill(px + 6, py + 19, px + pw - 6, py + 20, 0x553A4A60);
        int y = py + 26;

        ArmourSet s = current();
        if (s == null) {
            wrap(g, "There's nothing your rank can forge here yet. Hunter armour comes with rank; other sets' patterns are found out in the world.",
                    px + 8, y, pw - 16, LABEL, 4);
            footer(g);
            return;
        }

        // the set
        boolean many = sets.size() > 1;
        if (many) {
            arrow(g, px + 8, y, "<", mouseX, mouseY);
            arrow(g, px + pw - 20, y, ">", mouseX, mouseY);
        }
        g.centeredText(font, Component.literal(s.setName), px + pw / 2, y + 2, 0xFF000000 | s.rank.colour);
        y += 12;
        g.centeredText(font, Component.literal(s.archetype.label + " armour, " + s.rank.label + "-rank"), px + pw / 2, y, LABEL);
        y += 10;
        ArmourPayloads.Offer first = offer(0);
        if (first != null && (first.flags() & ArmourPayloads.KNOWN_WHOLE) != 0) {
            g.centeredText(font, Component.literal("You've forged this set whole before."), px + pw / 2, y, GOOD);
            y += 10;
        }
        y += 3;

        // its four pieces
        rowsY = y;
        for (int i = 0; i < 4; i++) {
            ArmourPayloads.Offer o = offer(i);
            int ry = rowsY + i * ROW;
            boolean sel = i == piece;
            boolean over = mouseX >= px + 6 && mouseX < px + pw - 6 && mouseY >= ry && mouseY < ry + ROW - 1;
            g.fill(px + 6, ry, px + pw - 6, ry + ROW - 1, sel ? 0xF01A2436 : over ? 0xE0141C2A : 0x00000000);
            if (sel) g.fill(px + 6, ry, px + 8, ry + ROW - 1, 0xFF000000 | s.rank.colour);
            ItemStack it = item(s, ForgeSong.PIECES[i]);
            if (!it.isEmpty()) {
                g.item(it, px + 11, ry + 1);
                g.text(font, it.getHoverName(), px + 31, ry + 5, TITLE, false);
            }
            String state;
            int colour;
            if (o == null) {
                state = "";
                colour = DIM;
            } else if ((o.flags() & ArmourPayloads.CAN) != 0) {
                state = (o.flags() & ArmourPayloads.FROM_MEMORY) != 0 ? "Ready, known by heart" : "Ready";
                colour = GOOD;
            } else if (!place(s).isEmpty()) {
                state = "Not here";
                colour = DIM;
            } else {
                state = "Needs more";
                colour = BAD;
            }
            g.text(font, state, px + pw - 10 - font.width(state), ry + 5, colour, false);
        }
        y = rowsY + 4 * ROW + 3;

        // what the chosen piece takes, two to a line (creative takes nothing: its counts stay quiet)
        ArmourPayloads.Offer o = offer(piece);
        if (o != null) {
            ItemStack it = item(s, ForgeSong.PIECES[piece]);
            g.text(font, "What the " + it.getHoverName().getString() + " takes", px + 8, y, LABEL, false);
            y += 11;
            boolean can = (o.flags() & ArmourPayloads.CAN) != 0;
            int col = (pw - 16) / 2;
            List<ArmourPayloads.Need> needs = o.needs();
            for (int k = 0; k < needs.size(); k++) {
                ArmourPayloads.Need n = needs.get(k);
                int nx = px + 8 + (k % 2) * col, ny = y + (k / 2) * 17;
                boolean ok = n.have() >= n.need();
                var icon = BuiltInRegistries.ITEM.getValue(Identifier.parse(n.icon()));
                g.item(new ItemStack(icon), nx, ny);
                String count = Math.min(n.have(), n.need()) + "/" + n.need();
                g.text(font, count, nx + 18, ny + 4, ok ? GOOD : can ? DIM : BAD, false);
                int lx = nx + 20 + font.width(count) + 3;
                g.text(font, font.plainSubstrByWidth(n.label(), nx + col - 2 - lx), lx, ny + 4, ok ? LABEL : TITLE, false);
            }
            y += (needs.size() + 1) / 2 * 17;
        }

        // where things stand
        String note = busy != null ? busy + " is forging here. Come back when the piece is done, or help at the anvil while it's shaped."
                : place(s);
        if (!note.isEmpty()) y = wrap(g, note, px + 8, y + 2, pw - 16, LANTERN, 2) + 2;

        // the buttons
        boolean memory = o != null && (o.flags() & ArmourPayloads.FROM_MEMORY) != 0;
        buttonY = Math.min(py + ph - 34, y + (memory ? 14 : 4));
        boolean can = o != null && (o.flags() & ArmourPayloads.CAN) != 0 && busy == null;
        button(g, px + 8, "Forge it", can, mouseX, mouseY);
        if (memory) {
            button(g, px + 8 + bw("Forge it") + 6, "From memory", can, mouseX, mouseY);
            g.text(font, "One heat and one strike, always Fine.", px + 8, buttonY - 11, DIM, false);
        }
        if (suitOnStand() > 0) {
            String take = "Take the suit";
            button(g, px + pw - 8 - bw(take), take, true, mouseX, mouseY);
        }
        footer(g);
    }

    private String place(ArmourSet s) {
        return ForgeSong.byHand(s) ? offers.hand() : offers.place();
    }

    private int suitOnStand() {
        var be = ForgeClient.stand(offers.pos());
        int n = 0;
        if (be != null) for (int i = 0; i < 4; i++) if (!be.suit(i).isEmpty()) n++;
        return n;
    }

    private void footer(GuiGraphicsExtractor g) {
        String esc = "Space: forge   Tab: piece   Esc: close";
        g.text(font, esc, px + pw - 8 - font.width(esc), py + ph - 12, DIM, false);
        int n = suitOnStand();
        if (n > 0) g.text(font, n == 4 ? "A whole suit stands on it." : n + " of 4 pieces stand on it.", px + 8, py + ph - 12, DIM, false);
    }

    private int wrap(GuiGraphicsExtractor g, String text, int x, int y, int w, int colour, int max) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text), w);
        for (int i = 0; i < Math.min(max, lines.size()); i++) g.text(font, lines.get(i), x, y + i * 10, colour, false);
        return y + Math.min(max, lines.size()) * 10;
    }

    private void arrow(GuiGraphicsExtractor g, int x, int y, String s, int mx, int my) {
        boolean over = mx >= x && mx < x + 12 && my >= y - 1 && my < y + 11;
        g.fill(x, y - 1, x + 12, y + 11, over ? 0xF02A3A56 : 0xE0101622);
        g.outline(x, y - 1, 12, 12, EDGE);
        g.centeredText(font, Component.literal(s), x + 6, y + 1, TITLE);
    }

    private int bw(String label) {
        return font.width(label) + 14;
    }

    private void button(GuiGraphicsExtractor g, int x, String label, boolean enabled, int mx, int my) {
        int w = bw(label);
        boolean over = enabled && mx >= x && mx < x + w && my >= buttonY && my < buttonY + 18;
        g.fill(x, buttonY, x + w, buttonY + 18, over ? 0xF02A3A56 : 0xE0101622);
        g.outline(x, buttonY, w, 18, enabled ? LANTERN : 0xFF2A3446);
        g.text(font, label, x + 7, buttonY + 5, enabled ? TITLE : DIM, false);
    }

    private boolean hit(int x, String label, double mx, double my) {
        return mx >= x && mx < x + bw(label) && my >= buttonY && my < buttonY + 18;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick);
        double mx = event.x(), my = event.y();
        ArmourSet s = current();
        if (s == null) return super.mouseClicked(event, doubleClick);
        int y = py + 26;
        if (sets.size() > 1 && my >= y - 1 && my < y + 11) {
            if (mx >= px + 8 && mx < px + 20) return pick(-1);
            if (mx >= px + pw - 20 && mx < px + pw - 8) return pick(1);
        }
        for (int i = 0; i < 4; i++) {
            int ry = rowsY + i * ROW;
            if (mx >= px + 6 && mx < px + pw - 6 && my >= ry && my < ry + ROW - 1) {
                piece = i;
                return true;
            }
        }
        ArmourPayloads.Offer o = offer(piece);
        boolean can = o != null && (o.flags() & ArmourPayloads.CAN) != 0 && busy == null;
        if (can && hit(px + 8, "Forge it", mx, my)) return forge(o, ArmourPayloads.START);
        if (can && (o.flags() & ArmourPayloads.FROM_MEMORY) != 0 && hit(px + 8 + bw("Forge it") + 6, "From memory", mx, my))
            return forge(o, ArmourPayloads.MEMORY);
        if (suitOnStand() > 0 && hit(px + pw - 8 - bw("Take the suit"), "Take the suit", mx, my)) {
            ForgeClient.send(offers.pos(), ArmourPayloads.TAKE, 0, 0F, 0F);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** Space or Enter forges the chosen piece; Tab picks the next piece (Shift back); Q and E the set. */
    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        ArmourPayloads.Offer o = offer(piece);
        boolean can = o != null && (o.flags() & ArmourPayloads.CAN) != 0 && busy == null;
        if ((key == InputConstants.KEY_SPACE || key == InputConstants.KEY_RETURN) && can) return forge(o, ArmourPayloads.START);
        if (key == InputConstants.KEY_TAB) {
            piece = Math.floorMod(piece + (event.hasShiftDown() ? -1 : 1), 4);
            return true;
        }
        if ((key == InputConstants.KEY_Q || key == InputConstants.KEY_E) && sets.size() > 1) return pick(key == InputConstants.KEY_Q ? -1 : 1);
        return super.keyPressed(event);
    }

    private boolean pick(int d) {
        set = Math.floorMod(set + d, sets.size());
        piece = 0;
        return true;
    }

    private boolean forge(ArmourPayloads.Offer o, int act) {
        ForgeClient.send(offers.pos(), act, o.piece(), 0F, 0F);
        Minecraft.getInstance().gui.setScreen(new ForgeSongScreen(offers.pos(), false));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
