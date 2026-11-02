package dev.awaken.gates.client;

import dev.awaken.gates.net.GatePayloads;
import dev.awaken.gates.net.GatePayloads.SpoilsView;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.GuiCanvas;
import dev.awaken.signal.client.HudLook;
import dev.awaken.signal.client.UiCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The gate's spoils beside your pockets (SPEC §13, "Gate spoils"), on the survival inventory, plain or holo: a glass
 * panel left of it (the right side is vanilla's effects). In a run, the Satchel, look only, and what dying here costs;
 * whenever anything is held for you, a tab of it you take from with a click. With the recipe book open there's no room,
 * so it folds to a slim tab above the inventory. It isn't one of the menu's slots (no mixin): it draws on the screen's
 * foreground and eats every click that lands on it, so nothing on the cursor is ever thrown through it.
 */
public final class SpoilsPanel {
    private SpoilsPanel() {}

    private static final int CELL = 18, PAD = 6, GAP = 4, MARGIN = 4, HEAD = 30, FOOT = 44, MIN_COLS = 4, MAX_COLS = 7;
    /** The recipe book covers the inventory below this width (AbstractRecipeBookScreen). */
    private static final int BOOK_NARROW = 379;
    private static final int SATCHEL = 0, HELD = 1;
    private static final int WELL = 0x040A16;

    /** The screen the panel belongs to now (a new one starts the fade on page 1), and whether the server knows it's open. */
    private static @Nullable Screen on;
    private static long openedNanos;
    private static boolean watching;
    private static int tab, page;

    static void init() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ScreenEvent.Init.Post.class, SpoilsPanel::opened);
        bus.addListener(ScreenEvent.Closing.class, SpoilsPanel::closed);
        bus.addListener(ScreenEvent.Render.Foreground.class, SpoilsPanel::draw);
        bus.addListener(EventPriority.HIGH, ScreenEvent.MouseButtonPressed.Pre.class, SpoilsPanel::press);
        bus.addListener(EventPriority.HIGH, ScreenEvent.MouseButtonReleased.Pre.class, SpoilsPanel::release);
        bus.addListener(ScreenEvent.MouseScrolled.Pre.class, SpoilsPanel::scroll);
    }

    // ---- open and close: the server sends the view, and fresh ones while you look ----------------------------------

    private static void opened(ScreenEvent.Init.Post e) {
        if (!(e.getScreen() instanceof InventoryScreen s) || Minecraft.getInstance().gui.screen() != s) return;
        if (on != s) {
            on = s;
            openedNanos = System.nanoTime();
            page = 0;
            tab = SATCHEL;
        }
        if (!watching) {
            watching = true;
            ClientPacketDistributor.sendToServer(new GatePayloads.WatchSpoils(true));
        }
    }

    private static void closed(ScreenEvent.Closing e) {
        if (!(e.getScreen() instanceof InventoryScreen) || !watching) return;
        watching = false;
        on = null;
        ClientGates.spoils = SpoilsView.EMPTY; // the next open waits for a fresh view rather than flash an old one
        ClientPacketDistributor.sendToServer(new GatePayloads.WatchSpoils(false));
    }

    static void forget() {
        watching = false;
        on = null;
    }

    // ---- where it sits ----------------------------------------------------------------------------------------------

    /** The panel ({@code cols > 0}) or the slim tab ({@code cols == 0}). */
    private record Box(int x, int y, int w, int h, int cols, int rows) {
        boolean full() {
            return cols > 0;
        }

        boolean over(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        int gridX() {
            return x + PAD;
        }

        int gridY() {
            return y + HEAD;
        }

        int perPage() {
            return cols * rows;
        }

        int pagerY() {
            return gridY() + rows * CELL + 3;
        }
    }

    private static boolean shown(SpoilsView v) {
        return v.inRun() || v.heldStacks() > 0;
    }

    private static @Nullable Box box(InventoryScreen s, Font font, SpoilsView v) {
        Minecraft mc = Minecraft.getInstance();
        if (!shown(v) || mc.player == null) return null;
        boolean book = mc.player.getRecipeBook().isOpen(RecipeBookType.CRAFTING);
        if (book && s.width < BOOK_NARROW) return null; // the book is drawn over the inventory
        int left = s.getLeftPos(), top = s.getTopPos();
        int cols = book ? 0 : Math.min(MAX_COLS, (left - GAP - MARGIN - 2 * PAD) / CELL);
        if (cols >= MIN_COLS) {
            int w = cols * CELL + 2 * PAD, h = s.getImageHeight();
            return new Box(left - GAP - w, top, w, h, cols, Math.max(1, (h - HEAD - FOOT) / CELL));
        }
        int w = font.width(slimLabel(v)) + 2 * PAD, h = 13;
        if (top < h + 2) return null;
        return new Box(left, top - h - 2, w, h, 0, 0);
    }

    private static int tabOf(SpoilsView v) {
        if (tab == HELD && v.heldStacks() > 0) return HELD;
        if (tab == SATCHEL && v.inRun()) return SATCHEL;
        return v.inRun() ? SATCHEL : HELD;
    }

    private static boolean twoTabs(SpoilsView v) {
        return v.inRun() && v.heldStacks() > 0;
    }

    private static String heldTab(SpoilsView v) {
        return "HELD " + v.heldStacks();
    }

    private static String slimLabel(SpoilsView v) {
        return v.inRun() ? "SATCHEL " + v.satchelStacks() + " · " + v.xp() + " XP" : "HELD FOR YOU " + v.heldStacks();
    }

    private static int pages(Box b, List<ItemStack> items) {
        return Math.max(1, (items.size() + b.perPage() - 1) / b.perPage());
    }

    private static List<ItemStack> items(SpoilsView v, int t) {
        return t == SATCHEL ? v.satchel() : v.held();
    }

    /** The well at grid index {@code i} of the page, or -1. */
    private static int cellAt(Box b, double mx, double my) {
        if (mx < b.gridX() || my < b.gridY()) return -1;
        int c = (int) ((mx - b.gridX()) / CELL), r = (int) ((my - b.gridY()) / CELL);
        return c < b.cols && r < b.rows ? r * b.cols + c : -1;
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------

    /** Up after the screen opens (the holo inventory lands first) and once the server's view is here. */
    private static float fade() {
        long from = Math.max(openedNanos + 120_000_000L, ClientGates.spoilsNanos);
        float t = Math.clamp((System.nanoTime() - from) / 250_000_000f, 0f, 1f);
        return t * t * (3 - 2 * t);
    }

    private static void draw(ScreenEvent.Render.Foreground e) {
        if (!(e.getScreen() instanceof InventoryScreen s)) return;
        Minecraft mc = Minecraft.getInstance();
        SpoilsView v = ClientGates.spoils;
        Box b = box(s, mc.font, v);
        if (b == null) return;
        if (on != s) {
            on = s;
            openedNanos = System.nanoTime();
            page = 0;
        }
        float k = fade();
        if (k <= 0.01f) return;
        if (b.full()) drawPanel(e.getGuiGraphics(), mc.font, s, b, v, e.getMouseX(), e.getMouseY(), k);
        else drawSlim(e.getGuiGraphics(), mc.font, b, v, e.getMouseX(), e.getMouseY(), k);
    }

    private static void drawPanel(GuiGraphicsExtractor g, Font font, InventoryScreen s, Box b, SpoilsView v, int mx, int my, float k) {
        int t = tabOf(v);
        HudLook.card(g, b.x, b.y, b.w, b.h, Glass.EDGE, k, k, 0f);
        UiCanvas c = GuiCanvas.of(g);

        // the head: the tab (or both), the gate, Satchel XP
        int hy = b.y + 6, head;
        if (twoTabs(v)) {
            String held = heldTab(v);
            int sw = font.width("SATCHEL"), hx = b.x + PAD + sw + 8;
            HudLook.text(g, font, "SATCHEL", b.x + PAD, hy, Glass.a(t == SATCHEL ? Glass.EDGE : Glass.LABEL, (t == SATCHEL ? 1f : 0.55f) * k), false);
            HudLook.text(g, font, held, hx, hy, Glass.a(t == HELD ? Glass.EDGE : Glass.LABEL, (t == HELD ? 1f : 0.55f) * k), false);
            int ux = t == SATCHEL ? b.x + PAD : hx, uw = t == SATCHEL ? sw : font.width(held);
            c.hLine(ux, ux + uw - 1, hy + 9, Glass.a(Glass.EDGE, 0.7f * k));
            head = hx + font.width(held);
        } else {
            String title = t == SATCHEL ? "SATCHEL" : "HELD FOR YOU";
            HudLook.text(g, font, title, b.x + PAD, hy, Glass.a(Glass.EDGE, k), false);
            head = b.x + PAD + font.width(title);
        }
        String[] sub = t == SATCHEL ? new String[]{v.title(), v.title().replace("-rank", "")} : new String[]{"Click a stack to take it", "Click one to take it"};
        int subRight = b.x + b.w - PAD;
        if (t == SATCHEL) {
            String xp = v.xp() + " XP";
            int xw = font.width(xp);
            boolean up = head + 6 + xw <= b.x + b.w - PAD; // beside the title when it fits, else beside the gate's name
            HudLook.text(g, font, xp, b.x + b.w - PAD - xw, up ? hy : b.y + 17, Glass.a(Glass.AMBER, k), false);
            if (!up) subRight -= xw + 4;
        }
        HudLook.text(g, font, fit(font, subRight - b.x - PAD, sub), b.x + PAD, b.y + 17, Glass.a(Glass.LABEL, 0.85f * k), false);

        // the wells, one page of them
        List<ItemStack> items = items(v, t);
        int pages = pages(b, items);
        page = Math.clamp(page, 0, pages - 1);
        boolean carrying = !s.getMenu().getCarried().isEmpty();
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = 0; i < b.perPage(); i++) {
            int x = b.gridX() + (i % b.cols) * CELL + 1, y = b.gridY() + (i / b.cols) * CELL + 1;
            c.fill(x, y, x + 16, y + 16, Glass.a(WELL, 0.58f * k));
            c.outline(x - 1, y - 1, 18, 18, Glass.a(Glass.EDGE, 0.22f * k));
            c.hLine(x, x + 15, y, Glass.a(Glass.EDGE, 0.12f * k));
            int idx = page * b.perPage() + i;
            if (idx >= items.size()) continue;
            ItemStack st = items.get(idx);
            if (cellAt(b, mx, my) == i) {
                hovered = st;
                c.fill(x, y, x + 16, y + 16, Glass.a(Glass.EDGE, (t == HELD ? 0.28f : 0.14f) * k));
                if (t == HELD) HudLook.glow(g, x - 1, y - 1, 18, 18, Glass.EDGE, 0.6f * k);
            }
            if (k > 0.5f) { // items can't fade: they appear halfway, as the holo inventory's do
                g.item(st, x, y);
                g.itemDecorations(font, st, x, y);
            }
        }
        if (items.isEmpty() && t == SATCHEL) {
            int ty = b.gridY() + b.rows * CELL / 2 - 9;
            for (FormattedCharSequence line : font.split(Component.literal("Nothing yet. What you find in here goes in."), b.w - 2 * PAD - 8)) {
                HudLook.text(g, font, line, b.x + b.w / 2 - font.width(line) / 2, ty, Glass.a(Glass.LABEL, 0.7f * k), false);
                ty += 10;
            }
        }

        // the count and the pages
        int py = b.pagerY();
        int stacks = t == SATCHEL ? v.satchelStacks() : v.heldStacks();
        int countRoom = pages > 1 ? pager(font, b, pages).prevX - 6 - b.x - PAD : b.w - 2 * PAD;
        HudLook.text(g, font, fit(font, countRoom, stacks + (stacks == 1 ? " stack" : " stacks"), Integer.toString(stacks)), b.x + PAD, py,
                Glass.a(Glass.LABEL, 0.85f * k), false);
        if (pages > 1) {
            Pager p = pager(font, b, pages);
            HudLook.text(g, font, "<", p.prevX, py, Glass.a(page > 0 && p.overPrev(mx, my, py) ? 0xFFFFFF : Glass.EDGE, (page > 0 ? 1f : 0.3f) * k), false);
            HudLook.text(g, font, p.label, p.labelX, py, Glass.a(Glass.TEXT, 0.9f * k), false);
            HudLook.text(g, font, ">", p.nextX, py, Glass.a(page < pages - 1 && p.overNext(mx, my, py) ? 0xFFFFFF : Glass.EDGE, (page < pages - 1 ? 1f : 0.3f) * k), false);
        }

        // the foot: what dying costs, or taking it all
        if (t == SATCHEL) {
            List<FormattedCharSequence> lines = font.split(Component.literal(deathLine(v.deathLine())), b.w - 2 * PAD);
            int n = Math.min(3, lines.size()), ly = b.y + b.h - PAD - n * 9;
            int colour = switch (v.deathLine()) {
                case SpoilsView.DEATH_ALL -> Glass.WARN;
                case SpoilsView.DEATH_HALF -> Glass.AMBER;
                default -> Glass.GOOD;
            };
            for (int i = 0; i < n; i++) HudLook.text(g, font, lines.get(i), b.x + PAD, ly + i * 9, Glass.a(colour, k), false);
        } else {
            int by = b.y + b.h - PAD - 14, bw = b.w - 2 * PAD;
            HudLook.text(g, font, fit(font, bw, "Safe here. Nothing expires.", "Kept safe for you."), b.x + PAD, by - 11, Glass.a(Glass.LABEL, 0.7f * k), false);
            boolean over = mx >= b.x + PAD && mx < b.x + PAD + bw && my >= by && my < by + 14;
            if (k > 0.5f) Glass.button(c, font, "Take all", b.x + PAD, by, bw, 14, over, true, Glass.EDGE);
        }

        if (!hovered.isEmpty() && !carrying) g.setTooltipForNextFrame(font, hovered, mx, my);
    }

    private static void drawSlim(GuiGraphicsExtractor g, Font font, Box b, SpoilsView v, int mx, int my, float k) {
        HudLook.card(g, b.x, b.y, b.w, b.h, Glass.EDGE, k, k, 0f);
        HudLook.text(g, font, slimLabel(v), b.x + PAD, b.y + 3, Glass.a(v.inRun() ? Glass.AMBER : Glass.EDGE, k), false);
        if (b.over(mx, my)) {
            g.setTooltipForNextFrame(font, Component.literal(v.inRun() ? "Close the recipe book to see your Satchel." : "Close the recipe book to take what's held for you."), mx, my);
        }
    }

    static String deathLine(int rule) {
        return switch (rule) {
            case SpoilsView.DEATH_ALL -> "If you die in here, you lose all of this.";
            case SpoilsView.DEATH_HALF -> "If you die in here, half of it comes back with you.";
            case SpoilsView.DEATH_FIRST -> "This first gate is forgiving. You keep all of it even if you die.";
            case SpoilsView.DEATH_CLONE -> "Your Clone Vat will bring it back once today.";
            default -> "";
        };
    }

    /** The first wording that fits (a narrow window gives the panel fewer columns), else the last one clipped. */
    private static String fit(Font font, int w, String... options) {
        for (String o : options) if (font.width(o) <= w) return o;
        return clip(font, options[options.length - 1], w);
    }

    private static String clip(Font font, String s, int w) {
        if (font.width(s) <= w) return s;
        return font.plainSubstrByWidth(s, w - font.width("…")) + "…";
    }

    /** The pager's places on its row: "< 2/5 >" at the right edge. */
    private record Pager(String label, int prevX, int labelX, int nextX, int right) {
        boolean overPrev(double mx, double my, int py) {
            return my >= py - 2 && my < py + 10 && mx >= prevX - 3 && mx < prevX + 8;
        }

        boolean overNext(double mx, double my, int py) {
            return my >= py - 2 && my < py + 10 && mx >= nextX - 3 && mx < nextX + 8;
        }
    }

    private static Pager pager(Font font, Box b, int pages) {
        String label = (page + 1) + "/" + pages;
        int right = b.x + b.w - PAD;
        int nextX = right - font.width(">");
        int labelX = nextX - 5 - font.width(label);
        int prevX = labelX - 5 - font.width("<");
        return new Pager(label, prevX, labelX, nextX, right);
    }

    // ---- input --------------------------------------------------------------------------------------------------------

    private static @Nullable Box boxUnder(Screen screen, double mx, double my) {
        if (!(screen instanceof InventoryScreen s)) return null;
        Box b = box(s, Minecraft.getInstance().font, ClientGates.spoils);
        return b != null && b.over(mx, my) ? b : null;
    }

    private static void press(ScreenEvent.MouseButtonPressed.Pre e) {
        double mx = e.getMouseX(), my = e.getMouseY();
        Box b = boxUnder(e.getScreen(), mx, my);
        if (b == null) return;
        e.setCanceled(true); // never through to the inventory: a click here doesn't throw what's on the cursor
        if (!b.full() || e.getButton() != 0) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        SpoilsView v = ClientGates.spoils;
        if (twoTabs(v) && my >= b.y + 4 && my < b.y + 16) {
            int sw = font.width("SATCHEL"), hx = b.x + PAD + sw + 8;
            int want = mx < hx - 4 ? SATCHEL : mx < hx + font.width(heldTab(v)) + 4 ? HELD : tabOf(v);
            if (want != tabOf(v)) {
                tab = want;
                page = 0;
                click(mc);
            }
            return;
        }
        int t = tabOf(v);
        List<ItemStack> items = items(v, t);
        int pages = pages(b, items);
        int py = b.pagerY();
        if (pages > 1) {
            Pager p = pager(font, b, pages);
            if (p.overPrev(mx, my, py) && page > 0) {
                page--;
                click(mc);
                return;
            }
            if (p.overNext(mx, my, py) && page < pages - 1) {
                page++;
                click(mc);
                return;
            }
        }
        if (t != HELD) return; // the Satchel is look only: taking from it would dodge the death stake
        int by = b.y + b.h - PAD - 14;
        if (mx >= b.x + PAD && mx < b.x + b.w - PAD && my >= by && my < by + 14) {
            ClientPacketDistributor.sendToServer(new GatePayloads.TakeHeld(-1, net.minecraft.world.item.Items.AIR, true));
            click(mc);
            return;
        }
        int cell = cellAt(b, mx, my);
        int idx = cell < 0 ? -1 : page * b.perPage() + cell;
        if (idx < 0 || idx >= items.size() || !s(e).getMenu().getCarried().isEmpty()) return;
        ClientPacketDistributor.sendToServer(new GatePayloads.TakeHeld(idx, items.get(idx).getItem(), false));
    }

    private static InventoryScreen s(ScreenEvent e) {
        return (InventoryScreen) e.getScreen();
    }

    private static void release(ScreenEvent.MouseButtonReleased.Pre e) {
        if (boxUnder(e.getScreen(), e.getMouseX(), e.getMouseY()) != null) e.setCanceled(true);
    }

    private static void scroll(ScreenEvent.MouseScrolled.Pre e) {
        Box b = boxUnder(e.getScreen(), e.getMouseX(), e.getMouseY());
        if (b == null || !b.full()) return;
        e.setCanceled(true);
        int pages = pages(b, items(ClientGates.spoils, tabOf(ClientGates.spoils)));
        int to = Math.clamp(page + (e.getScrollDeltaY() < 0 ? 1 : -1), 0, pages - 1);
        if (to != page) {
            page = to;
            click(Minecraft.getInstance());
        }
    }

    private static void click(Minecraft mc) {
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.2F, 0.4F));
    }

    // ---- autopilot ------------------------------------------------------------------------------------------------------

    /** Shows a tab ("satchel" or "held") and a page, as a click would. */
    static String devShow(String which, int toPage) {
        tab = "held".equals(which) ? HELD : SATCHEL;
        page = Math.max(0, toPage);
        SpoilsView v = ClientGates.spoils;
        return "spoils view: run " + v.inRun() + ", " + v.satchelStacks() + " in the Satchel, " + v.heldStacks() + " held";
    }
}
