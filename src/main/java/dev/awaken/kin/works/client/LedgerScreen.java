package dev.awaken.kin.works.client;

import dev.awaken.kin.works.WorksNet;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.QuietScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Storehouse's Ledger (SPEC §43.12): everything on its shelves and on the heap, by bay, with a search. Pick a line
 * and a Totter fetches it to the desk, or "Show me" lights its shelf.
 */
final class LedgerScreen extends Screen implements QuietScreen {
    private static final int W = 300, H = 230, ROW = 18, TOP = 50, ROWS = 8;

    private record Line(ItemStack stack, String name, int count, String bay) {}

    private final WorksNet.Ledger page;
    private final List<Line> all = new ArrayList<>();
    private List<Line> shown = List.of();
    private EditBox search;
    private int px, py, scroll, picked = -1;

    LedgerScreen(WorksNet.Ledger page) {
        super(Component.literal("The Ledger"));
        this.page = page;
        for (WorksNet.LedgerRow r : page.rows()) {
            var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(r.item()));
            ItemStack s = new ItemStack(item);
            all.add(new Line(s, s.getHoverName().getString(), r.count(), r.bay()));
        }
    }

    @Override
    protected void init() {
        px = (width - W) / 2;
        py = Math.max(8, (height - H) / 2);
        search = new EditBox(font, px + 14, py + 26, W - 28, 16, Component.literal("Look something up"));
        search.setHint(Component.literal("Look something up..."));
        search.setMaxLength(32);
        search.setResponder(s -> filter());
        addRenderableWidget(search);
        setInitialFocus(search);
        filter();
    }

    private void filter() {
        String q = search == null ? "" : search.getValue().strip().toLowerCase(Locale.ROOT);
        List<Line> out = new ArrayList<>();
        for (Line l : all) if (q.isEmpty() || l.name().toLowerCase(Locale.ROOT).contains(q) || l.bay().toLowerCase(Locale.ROOT).contains(q)) out.add(l);
        shown = out;
        scroll = 0;
        picked = -1;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        Glass.panel(g, px, py, W, H, Glass.EDGE, 1F);
        String title = "THE LEDGER";
        g.text(font, title, px + (W - font.width(title)) / 2, py + 9, Glass.a(Glass.TITLE, 1F), false);
        super.extractRenderState(g, mouseX, mouseY, a);
        if (all.isEmpty()) {
            g.text(font, "The shelves are bare. Tip things into the Drop", px + 16, py + TOP + 4, Glass.a(Glass.LABEL, 1F), false);
            g.text(font, "and the Totters will shelve them.", px + 16, py + TOP + 16, Glass.a(Glass.LABEL, 1F), false);
            return;
        }
        if (shown.isEmpty()) g.text(font, "Nothing like that is here.", px + 16, py + TOP + 4, Glass.a(Glass.LABEL, 1F), false);
        String lastBay = null;
        for (int i = 0; i < ROWS && scroll + i < shown.size(); i++) {
            Line l = shown.get(scroll + i);
            int y = py + TOP + i * ROW;
            boolean over = overRow(mouseX, mouseY, i), pick = scroll + i == picked;
            if (pick) g.fill(px + 10, y - 1, px + W - 10, y + ROW - 1, Glass.a(Glass.AMBER, 0.22F));
            else if (over) g.fill(px + 10, y - 1, px + W - 10, y + ROW - 1, Glass.a(0xFFFFFF, 0.06F));
            g.item(l.stack(), px + 14, y);
            g.text(font, font.plainSubstrByWidth(l.name(), 150), px + 36, y + 4, Glass.a(pick ? Glass.TITLE : Glass.TEXT, 1F), false);
            String n = "×" + l.count();
            g.text(font, n, px + 194 - font.width(n), y + 4, Glass.a(Glass.AMBER, 1F), false);
            if (!l.bay().equals(lastBay)) g.text(font, font.plainSubstrByWidth(l.bay(), 86), px + 202, y + 4, Glass.a(Glass.LABEL, 1F), false);
            lastBay = l.bay();
        }
        if (shown.size() > ROWS) {
            String more = (scroll + 1) + "–" + Math.min(shown.size(), scroll + ROWS) + " of " + shown.size();
            g.text(font, more, px + W - 14 - font.width(more), py + TOP + ROWS * ROW + 2, Glass.a(Glass.LABEL, 1F), false);
        }
        boolean can = picked >= 0 && picked < shown.size();
        int by = py + H - 30;
        Glass.button(g, font, "Fetch it", px + 14, by, 130, 18, can && overFetch(mouseX, mouseY), can, Glass.AMBER);
        Glass.button(g, font, "Show me", px + W - 144, by, 130, 18, can && overShow(mouseX, mouseY), can, Glass.GOOD);
    }

    private boolean overRow(double mx, double my, int i) {
        int y = py + TOP + i * ROW;
        return mx >= px + 10 && mx < px + W - 10 && my >= y - 1 && my < y + ROW - 1;
    }

    private boolean overFetch(double mx, double my) {
        int by = py + H - 30;
        return mx >= px + 14 && mx < px + 144 && my >= by && my < by + 18;
    }

    private boolean overShow(double mx, double my) {
        int by = py + H - 30;
        return mx >= px + W - 144 && mx < px + W - 14 && my >= by && my < by + 18;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        for (int i = 0; i < ROWS && scroll + i < shown.size(); i++)
            if (overRow(mx, my, i)) {
                if (doubleClick && picked == scroll + i) ask(false);
                else picked = scroll + i;
                return true;
            }
        if (picked >= 0 && picked < shown.size()) {
            if (overFetch(mx, my)) {
                ask(false);
                return true;
            }
            if (overShow(mx, my)) {
                ask(true);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        int max = Math.max(0, shown.size() - ROWS);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(sy)));
        return true;
    }

    private void ask(boolean show) {
        Line l = shown.get(picked);
        ClientPacketDistributor.sendToServer(new WorksNet.Ask(page.desk(), BuiltInRegistries.ITEM.getKey(l.stack().getItem()).toString(), show));
        onClose();
    }
}
