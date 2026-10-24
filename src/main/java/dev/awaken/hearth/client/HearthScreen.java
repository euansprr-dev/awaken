package dev.awaken.hearth.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.core.client.AwakenKeys;
import dev.awaken.hearth.guide.Guide;
import dev.awaken.hearth.guide.GuideData;
import dev.awaken.hearth.room.Furniture;
import dev.awaken.hearth.room.RoomScanner;
import dev.awaken.hearth.room.RoomType;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.QuietScreen;
import dev.awaken.signal.client.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * "Your Hearth": opens when you use your own Anchor. The header says where you stand (tier, radius, rest, respawn);
 * ROOMS has a card per room type: what it gives, a checklist of what it needs with icons (ticked when it's inside your
 * radius), the recipe of any Hearth furniture still missing, the rule, and why it isn't recognised yet; TOWN says who
 * lives here and who comes next; NEXT UPGRADE shows the price with what you carry and builds it ([Y] or the button).
 * Oren's cottage offer sits on top until it's built. Glass style of the Status screen, one scrolling column, laid out
 * with {@link Ui} so it wraps at any GUI scale (the owner's smallest is 378 × 246). Quiet: toasts wait while it's open.
 */
final class HearthScreen extends Screen implements QuietScreen {
    private static final int MAX_W = 520, MAX_H = 340, HEAD = 30, SCROLLBAR = 6, ICON = 12, SLOT = 13, ROW = 13;
    private static final int DIM = 0x4A5A72;

    private GuideData data;
    private final Map<Identifier, ItemStack> stacks = new HashMap<>();
    private int px, py, pw, ph, cx, cy, cw, ch;
    private int scroll, contentHeight, ticks;

    private enum Pass { MEASURE, DRAW, CLICK }

    private Pass pass = Pass.MEASURE;
    private GuiGraphicsExtractor g;
    private double mx = -10000, my = -10000;
    private int clicked = -1;

    HearthScreen(GuideData data) {
        super(Component.translatable("screen.awaken.hearth"));
        this.data = data;
    }

    void update(GuideData d) {
        this.data = d;
    }

    @Override
    protected void init() {
        pw = Math.min(MAX_W, width - 16);
        ph = Math.min(MAX_H, height - 16);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        cx = px + 10;
        cy = py + HEAD + 4;
        cw = pw - 20 - SCROLLBAR;
        ch = ph - HEAD - 4 - 8;
    }

    @Override
    public void tick() {
        ticks++;
    }

    @Override
    public void removed() {
        GuideClient.closed();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- drawing ----------------------------------------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fillGradient(0, 0, width, height, 0xA0050810, 0xC8050810);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        Glass.panel(g, px, py, pw, ph, Glass.LANTERN, 1f);
        // header: name, tier, where; the close hint on the right
        String title = "YOUR HEARTH";
        g.text(font, title, px + 12, py + 9, Glass.a(Glass.LANTERN, 1f), false);
        int chipX = px + 12 + font.width(title) + 6;
        int chipW = Glass.chip(g, font, "TIER " + roman(data.tier()), chipX, py + 7, Glass.AMBER);
        String close = "Esc to close";
        int closeX = px + pw - 12 - font.width(close);
        g.text(font, close, closeX, py + 9, Glass.a(Glass.LABEL, 0.7f), false);
        BlockPos anchor = data.anchor();
        String where = anchor.getX() + " " + anchor.getY() + " " + anchor.getZ();
        int whereX = chipX + chipW + 6;
        if (whereX + font.width(where) + 8 < closeX) g.text(font, where, whereX, py + 9, Glass.a(Glass.LABEL, 0.8f), false);
        g.horizontalLine(px + 8, px + pw - 8, py + HEAD - 6, Glass.a(Glass.LANTERN, 0.25f));

        boolean over = mouseX >= cx && mouseY >= cy && mouseX < cx + cw && mouseY < cy + ch;
        g.enableScissor(cx - 2, cy, cx + cw + SCROLLBAR, cy + ch);
        this.g = g;
        this.mx = over ? mouseX : -10000;
        this.my = over ? mouseY : -10000;
        pass = Pass.DRAW;
        Ui ui = Ui.draw(g, font, cx, cy - scroll, cw, (int) mx, (int) my);
        content(ui);
        contentHeight = ui.height();
        g.disableScissor();
        this.g = null;
        scroll = Math.max(0, Math.min(scroll, contentHeight - ch));
        if (contentHeight > ch) {
            int barH = Math.max(20, ch * ch / contentHeight);
            int barY = cy + (ch - barH) * scroll / Math.max(1, contentHeight - ch);
            g.fill(cx + cw + 3, barY, cx + cw + 5, barY + barH, Glass.a(Glass.LANTERN, 0.5f));
        }
    }

    // ---- input ------------------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double ex = event.x(), ey = event.y();
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && ex >= cx && ey >= cy && ex < cx + cw && ey < cy + ch) {
            pass = Pass.CLICK;
            mx = ex;
            my = ey;
            clicked = -1;
            content(Ui.click(font, cx, cy - scroll, cw, ex, ey));
            pass = Pass.MEASURE;
            if (clicked >= 0) {
                GuideClient.send(clicked);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        scroll = (int) Math.max(0, Math.min(Math.max(0, contentHeight - ch), scroll - scrollY * 18));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (AwakenKeys.ACCEPT.matches(event) && data.upgrade().affordable()) {
            GuideClient.send(Guide.Act.UPGRADE);
            return true;
        }
        return super.keyPressed(event);
    }

    // ---- content: one builder for measure, draw and click -------------------------------------------------------------

    private void content(Ui ui) {
        facts(ui);
        cottage(ui);
        section(ui, "ROOMS");
        ui.text("A room is closed and roofed: walls, a roof and a door, at most " + RoomScanner.MAX_EXTENT
                + " blocks wide. Doors, glass panes, fences, slabs and stairs close gaps. Build it; the Hearth notices.", Glass.a(Glass.LABEL, 1f));
        ui.gap(4);
        for (RoomType t : RoomType.values()) {
            room(ui, t);
            ui.gap(5);
        }
        section(ui, "TOWN");
        town(ui);
        ui.gap(4);
        section(ui, "NEXT UPGRADE");
        upgrade(ui);
        ui.gap(4);
    }

    private void section(Ui ui, String title) {
        ui.gap(4);
        ui.text(title, Glass.a(Glass.LANTERN, 0.9f));
        ui.gap(1);
    }

    private void facts(Ui ui) {
        ui.pair("Radius", data.radius() + " blocks around the Anchor", Glass.a(Glass.LABEL, 1f), Glass.a(Glass.TEXT, 1f));
        ui.pair("Rest heals a Fracture", "in " + data.rest() + " s standing still (" + data.sanctuaryRest() + " s in a Sanctuary)",
                Glass.a(Glass.LABEL, 1f), Glass.a(Glass.TEXT, 1f));
        ui.pair("You respawn", data.respawnHere() ? "here, by the Anchor" : "at a bed elsewhere (using the Anchor brings it back)",
                Glass.a(Glass.LABEL, 1f), Glass.a(Glass.TEXT, 1f));
    }

    /** Oren's offer, until the cottage stands. */
    private void cottage(Ui ui) {
        int st = data.cottage();
        if (st == GuideData.COTTAGE_NONE || st == GuideData.COTTAGE_BUILT) return;
        ui.gap(4);
        card(ui, Glass.LANTERN, in -> {
            in.text("Oren's cottage", Glass.a(Glass.TITLE, 1f));
            switch (st) {
                case GuideData.COTTAGE_BUILDING -> in.text("Oren is raising it beside your Anchor. Close this and watch.", Glass.a(Glass.TEXT, 1f));
                case GuideData.COTTAGE_NO_ROOM -> {
                    in.text("Oren found no open ground for it. Clear a flat patch about 12 blocks across near the Anchor, away from "
                            + "your builds (a few trees are fine), then try again.", Glass.a(Glass.TEXT, 1f));
                    button(in, "Try again", Glass.LANTERN, true, Guide.Act.COTTAGE);
                }
                default -> {
                    in.text("Oren can raise a small cottage beside your Anchor: a bed and a Meditation Mat, your first Sanctuary. "
                            + "Free, this once.", Glass.a(Glass.TEXT, 1f));
                    button(in, "Raise it", Glass.LANTERN, true, Guide.Act.COTTAGE);
                }
            }
        });
    }

    private void room(Ui ui, RoomType t) {
        GuideData.RoomCard c = data.card(t.ordinal());
        boolean built = !c.built().isEmpty();
        int accent = built ? Glass.GOOD : !c.why().isEmpty() ? Glass.AMBER : DIM;
        card(ui, accent, in -> {
            String status = !built ? "Not yet" : c.built().size() > 1 ? "Built ×" + c.built().size() : "Built · " + pos(c.built().get(0));
            in.pair(t.label, status, Glass.a(Glass.TITLE, 1f), Glass.a(built ? Glass.GOOD : Glass.LABEL, 1f));
            in.text(t.does, Glass.a(Glass.TEXT, 0.9f));
            in.gap(2);
            checklist(in, t, c, built);
            if (!built) {
                for (Furniture f : t.furniture) if ((c.have() & f.bit()) == 0) recipe(in, f);
            }
            if (!c.why().isEmpty()) {
                in.gap(1);
                in.text(c.why() + (c.mark().isPresent() ? " The spot is marked with blue sparks." : ""), Glass.a(Glass.AMBER, 1f));
            }
            if (c.byOren()) in.text("Oren built this one. Build the rest yourself.", Glass.a(Glass.GOOD, 1f));
        });
    }

    /** What it needs: an icon and a tick or a cross for each piece, then the closed room. */
    private void checklist(Ui ui, RoomType t, GuideData.RoomCard c, boolean built) {
        List<ItemStack> icons = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<Boolean> ok = new ArrayList<>();
        for (Furniture f : t.furniture) {
            icons.add(f.icon());
            labels.add(f.label);
            ok.add(built || (c.have() & f.bit()) != 0);
        }
        icons.add(new ItemStack(Items.OAK_DOOR));
        labels.add("all of it in one closed, roofed room");
        ok.add(built);
        int x = ui.x, y = ui.y(), right = ui.x + ui.width;
        for (int i = 0; i < icons.size(); i++) {
            String text = (ok.get(i) ? "✓ " : "✗ ") + labels.get(i);
            int tw = Math.min(font.width(text), ui.width - ICON - 3);
            int ew = ICON + 3 + tw + 8;
            if (x > ui.x && x + ew > right) {
                x = ui.x;
                y += ROW;
            }
            icon(ui, icons.get(i), x, y);
            ui.textAt(text, x + ICON + 3, y + 2, tw, Glass.a(ok.get(i) ? Glass.GOOD : Glass.AMBER, 1f), false);
            x += ew;
        }
        ui.gap(y - ui.y() + ROW + 1);
    }

    /** The Hearth furniture's own recipe, inline: the grid (tag slots cycle), an arrow, what it makes. */
    private void recipe(Ui ui, Furniture f) {
        Optional<GuideData.RecipeView> rv = data.recipe(f.ordinal());
        if (rv.isEmpty()) return;
        GuideData.RecipeView r = rv.get();
        ItemStack result = stack(r.result());
        String extra = switch (f) {
            case PAD -> " (any role pad works)";
            case TROPHY -> ", then hang a Lord's trophy on it";
            default -> "";
        };
        ui.text("Make " + (r.count() > 1 ? r.count() + " × " : "a ") + result.getHoverName().getString() + extra + ":", Glass.a(Glass.LABEL, 1f));
        int x = ui.x + 4, y = ui.y() + 1;
        for (int row = 0; row < r.height(); row++) {
            for (int col = 0; col < r.width(); col++) {
                int i = row * r.width() + col;
                int sx = x + col * SLOT, sy = y + row * SLOT;
                if (ui.drawing()) {
                    ui.g().fill(sx - 1, sy - 1, sx + ICON + 1, sy + ICON + 1, Glass.a(0x0A1020, 0.9f));
                    ui.g().outline(sx - 1, sy - 1, ICON + 2, ICON + 2, Glass.a(Glass.EDGE, 0.18f));
                }
                if (i < r.slots().size() && !r.slots().get(i).isEmpty()) {
                    List<Identifier> opts = r.slots().get(i);
                    icon(ui, stack(opts.get((ticks / 20) % opts.size())), sx, sy);
                }
            }
        }
        int gridW = r.width() * SLOT, gridH = r.height() * SLOT;
        int ay = y + gridH / 2 - 5;
        ui.textAt("→", x + gridW + 4, ay + 1, 12, Glass.a(Glass.LABEL, 1f), false);
        icon(ui, result, x + gridW + 16, ay);
        if (r.count() > 1) ui.textAt("×" + r.count(), x + gridW + 16 + ICON + 2, ay + 2, 30, Glass.a(Glass.LABEL, 1f), false);
        ui.gap(gridH + 3);
    }

    private void town(Ui ui) {
        if (data.town().isEmpty()) {
            ui.text("Nobody has moved in yet.", Glass.a(Glass.TEXT, 1f));
        } else {
            List<String> names = new ArrayList<>();
            for (GuideData.Person p : data.town()) names.add(p.name() + " the " + p.role());
            ui.text("Living here: " + String.join(", ", names) + ".", Glass.a(Glass.TEXT, 1f));
        }
        if (!data.coming().isEmpty()) {
            List<String> names = new ArrayList<>();
            for (GuideData.Person p : data.coming()) names.add(p.name() + " the " + p.role());
            ui.text("Next: " + String.join(" and ", names) + ", when you reach " + data.coming().get(0).when() + ".", Glass.a(Glass.LABEL, 1f));
        }
        ui.text("Talk to them: they temper gear, tell rumours, read your Codex and heal.", Glass.a(Glass.LABEL, 0.8f));
    }

    private void upgrade(Ui ui) {
        GuideData.Upgrade u = data.upgrade();
        if (u.maxed()) {
            ui.text("Your Hearth is complete.", Glass.a(Glass.GOOD, 1f));
            return;
        }
        ui.text("Tier " + roman(u.next()) + ": radius " + u.radius() + ", rest heals in " + u.rest() + " s.", Glass.a(Glass.TEXT, 1f));
        if (!u.rankMet()) ui.text("Opens at " + u.rank() + " (pass the Reassessment). You will need:", Glass.a(Glass.LABEL, 1f));
        int x = ui.x, y = ui.y() + 1, right = ui.x + ui.width;
        for (GuideData.Part p : u.parts()) {
            ItemStack s = stack(p.item());
            boolean enough = p.have() >= p.need();
            String text = Math.min(p.have(), 999) + "/" + p.need() + " " + s.getHoverName().getString();
            int tw = Math.min(font.width(text), ui.width - ICON - 3);
            int ew = ICON + 3 + tw + 10;
            if (x > ui.x && x + ew > right) {
                x = ui.x;
                y += ROW;
            }
            icon(ui, s, x, y);
            ui.textAt(text, x + ICON + 3, y + 2, tw, Glass.a(enough ? Glass.GOOD : Glass.AMBER, 1f), false);
            x += ew;
        }
        ui.gap(y - ui.y() + ROW + 2);
        if (u.discount() > 0) ui.text("Your rank takes " + u.discount() + "% off.", Glass.a(Glass.LABEL, 1f));
        if (u.rankMet()) {
            String key = AwakenKeys.ACCEPT.getTranslatedKeyMessage().getString();
            String label = u.affordable() ? "[" + key + "] Build Tier " + roman(u.next()) : "Gather the materials first";
            button(ui, label, Glass.LANTERN, u.affordable(), Guide.Act.UPGRADE);
        }
    }

    // ---- small pieces -----------------------------------------------------------------------------------------------

    /** A glass card around content laid out by {@code body}: measured first so the background fits. */
    private void card(Ui ui, int accent, java.util.function.Consumer<Ui> body) {
        int x = ui.x, y = ui.y(), w = ui.width;
        Ui m = Ui.measure(font, w - 12);
        Pass was = pass;
        pass = Pass.MEASURE;
        body.accept(m);
        pass = was;
        int h = m.height() + 8;
        if (ui.drawing()) Glass.card(ui.g(), x, y, w, h, accent, 1f);
        Ui in = switch (pass) {
            case DRAW -> Ui.draw(g, font, x + 8, y + 4, w - 12, (int) mx, (int) my);
            case CLICK -> Ui.click(font, x + 8, y + 4, w - 12, mx, my);
            default -> null;
        };
        if (in != null) body.accept(in);
        ui.gap(h);
    }

    private void button(Ui ui, String label, int accent, boolean enabled, int action) {
        ui.gap(2);
        int bw = Math.min(ui.width, font.width(label) + 16);
        if (ui.buttonAt(label, ui.x, ui.y(), bw, Ui.BUTTON_H, accent, enabled)) clicked = action;
        ui.gap(Ui.BUTTON_H + 2);
    }

    private void icon(Ui ui, ItemStack stack, int x, int y) {
        if (!ui.drawing() || stack.isEmpty()) return;
        GuiGraphicsExtractor gg = ui.g();
        gg.pose().pushMatrix();
        gg.pose().translate(x, y);
        gg.pose().scale(ICON / 16f, ICON / 16f);
        gg.item(stack, 0, 0);
        gg.pose().popMatrix();
        if (ui.hover(x, y, ICON, ICON)) gg.setTooltipForNextFrame(font, stack, ui.mouseX(), ui.mouseY());
    }

    private ItemStack stack(Identifier id) {
        return stacks.computeIfAbsent(id, k -> new ItemStack(BuiltInRegistries.ITEM.getValue(k)));
    }

    private static String pos(BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    static String roman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V";
            default -> String.valueOf(n);
        };
    }
}
