package dev.awaken.enchant.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.enchant.EnchantRules;
import dev.awaken.enchant.Inscriptions;
import dev.awaken.enchant.Known;
import dev.awaken.enchant.Marks;
import dev.awaken.enchant.net.EnchantPayloads;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The screen at an Inscribing Table. Since the polish pass (SPEC §45.15) it is no window at all: it frees the pointer,
 * reads it against the pages hanging over the desk and the sockets behind the piece ({@link Desk}), and draws one small
 * label by it, the trace's pips and a line of key caps. With {@code classicTable} set it is the old book:
 *
 * <p>The book's two pages at an Inscribing Table (SPEC §45.10), laid over the lower half of the view while the table shows
 * above them. The left page is your piece: its name and rank, its room, what's on it (scrape one off with two clicks)
 * and how deep this table lets you write. The right page is what you know that fits it, each with its mark, how deep it
 * can go here and its price, or plainly why not. Choose one and the book closes down out of the way while you trace its
 * mark on the piece; when it wakes, a card says what you made.
 */
public final class InscribeScreen extends Screen implements dev.awaken.signal.client.QuietScreen {
    private static final int PARCHMENT = 0xFFEADDC2, PARCHMENT_EDGE = 0xFFD9C6A0, PAGE_LINE = 0xFFC8B48C;
    private static final int INK = 0xFF35261A, INK_SOFT = 0xFF6A5641, INK_FAINT = 0xFF9A876C, RED_INK = 0xFF9C3B28;
    private static final int LEATHER = 0xFF4A2D1D, LEATHER_DARK = 0xFF2A180F, BRASS = 0xFFC9A35A, BRASS_DARK = 0xFF8A6A32;
    private static final int ROW = 24;

    private int age;
    /** The spread's place on screen this frame. */
    private int x0, y0, w, h;
    private int scroll;
    private final List<Row> rows = new ArrayList<>();
    private final Map<Integer, Integer> targets = new HashMap<>();
    private int hover = -1, hoverSince, lookSent;
    /** A scrape waiting for its second click: the enchantment's registry number and when the first came. */
    private int scrapeId = -1, scrapeAt;
    private final List<int[]> onIt = new ArrayList<>();
    private int traceSeen;
    /** The old book of two pages and the result card ({@code classicTable}); else choosing happens on the desk itself. */
    private final boolean classic = EnchantClientConfig.classic();

    InscribeScreen() {
        super(Component.literal("Inscribing Table"));
    }

    /** Ticks this screen has been up (a held press on a socket is counted in them). */
    int age() {
        return age;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        if (!classic) {
            // no window: the room only steps back a little round the desk, darkest at the corners
            float w = InscribeScene.weight(a);
            int edge = (int) (w * 0x9C) << 24 | 0x05060A, side = Math.max(1, width / 5), band = Math.max(1, height / 4);
            g.fillGradient(0, 0, width, band, edge, 0x0005060A);
            g.fillGradient(0, height - band, width, height, 0x0005060A, edge);
            for (int i = 0; i < side; i += 2) {
                int al = (int) (w * 0x70 * (1F - i / (float) side) * (1F - i / (float) side)) << 24 | 0x05060A;
                g.fill(i, 0, i + 2, height, al);
                g.fill(width - i - 2, 0, width - i, height, al);
            }
            return;
        }
        // no blur and no dark: the table is the background
        float open = bookShown(a);
        if (open > 0) g.fillGradient(0, height / 2, width, height, 0, ((int) (open * 0x90) << 24) | 0x07050A);
    }

    @Override
    public void tick() {
        age++;
        if (InscribeScene.phase == InscribeScene.TRACE && traceSeen == 0) traceSeen = age;
        if (InscribeScene.phase != InscribeScene.TRACE) traceSeen = 0;
        if (!classic) {
            deskTick();
            return;
        }
        // the book turns to what you're looking at (others see it), once you've rested on it a moment
        if (InscribeScene.phase == InscribeScene.BOOK && hover >= 0 && hover < rows.size() && age - hoverSince >= 4) {
            int no = rows.get(hover).e.no();
            if (no != lookSent) {
                lookSent = no;
                InscribeScene.act(EnchantPayloads.LOOK, no, 0, 0F);
            }
        }
    }

    // ---- choosing on the desk itself (SPEC §45.15) ----------------------------------------------------------------------

    private void deskTick() {
        if (InscribeScene.phase != InscribeScene.BOOK) return;
        // the book turns to what you point at (others see it), once you've rested on it a moment
        if (Desk.hover >= 0 && Desk.hover < Desk.OFFERS.size() && InscribeScene.ticks - Desk.hoverSince >= 4) {
            int no = Desk.OFFERS.get(Desk.hover).e().no();
            if (no != lookSent) {
                lookSent = no;
                InscribeScene.act(EnchantPayloads.LOOK, no, 0, 0F);
            }
        }
        // a press held on a socket for most of a second scrapes what is written there
        if (Desk.held >= 0 && Desk.held < Desk.ON.size() && age - Desk.heldAt >= Desk.HOLD && !InscribeScene.waiting) {
            Desk.On on = Desk.ON.get(Desk.held);
            Desk.held = -1;
            InscribeScene.act(EnchantPayloads.SCRAPE, on.id(), 0, 0F);
            InscribeScene.play(dev.awaken.enchant.EnchantContent.BURIN_METAL.get(), 0.5F, 0.7F);
            if (on.e() != null) InscribeScene.scraped(on.e().no());
        }
    }

    private void desk(GuiGraphicsExtractor g, int mx, int my, float a) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        if (!InscribeScene.pilot && (InscribeScene.phase == InscribeScene.TRACE || InscribeScene.phase == InscribeScene.MEMORY))
            InscribeScene.pen(mx, my, width, height);
        float in = InscribeScene.weight(a);
        int alpha = (int) (255 * in);
        switch (InscribeScene.phase) {
            case InscribeScene.BOOK -> {
                Desk.build(p, p.getMainHandItem());
                Desk.point(mx, my, width, height, a, pilotHover, age);
                InscribeScene.hoverNo = Desk.hoverNo();
                if (alpha < 24) break;
                if (Desk.OFFERS.isEmpty() && Desk.ON.isEmpty())
                    g.centeredText(font, "You don't know anything that fits this piece yet.", width / 2, height / 2, alpha << 24 | 0xE8DCC8);
                if (Desk.hover >= 0 && Desk.hover < Desk.OFFERS.size()) pageLabel(g, Desk.OFFERS.get(Desk.hover), alpha);
                else if (Desk.socket >= 0) socketLabel(g, Desk.socket, alpha);
                if (Desk.OFFERS.isEmpty()) caps(g, alpha, "Hold", "a mark on the piece to scrape it off", "Esc", "step away");
                else caps(g, alpha, "Click", "a page to write it", "Hold", "a mark to scrape it", "Esc", "step away");
            }
            case InscribeScene.TRACE -> {
                traceUi(g, a);
                caps(g, alpha, "Drag", "each cut from its glowing end", "Esc", "step back, your levels come back");
            }
            case InscribeScene.MEMORY -> memoryUi(g, a);
            case InscribeScene.WAKE -> woke(g, a);
            default -> {}
        }
        if (!InscribeScene.line.isEmpty() && InscribeScene.ticks - InscribeScene.lineAt < 80) {
            int la = (int) (255 * Mth.clamp((80 - (InscribeScene.ticks - InscribeScene.lineAt)) / 15F, 0F, 1F));
            Component c = Component.literal(InscribeScene.line);
            int tw = font.width(c), y = height - 44;
            g.fill(width / 2 - tw / 2 - 6, y - 4, width / 2 + tw / 2 + 6, y + 12, (la * 3 / 4) << 24 | 0x120A06);
            g.centeredText(font, c, width / 2, y, la << 24 | 0xF2D6B0);
        }
    }

    /** One line of key caps along the foot: each a key in a box and what it does. */
    private void caps(GuiGraphicsExtractor g, int alpha, String... pairs) {
        if (alpha < 24) return;
        int total = 0;
        for (int i = 0; i < pairs.length; i += 2) total += font.width(pairs[i]) + 8 + font.width(pairs[i + 1]) + 12;
        int x = (width - total + 12) / 2, y = height - 13;
        for (int i = 0; i < pairs.length; i += 2) {
            int kw = font.width(pairs[i]) + 6;
            g.fill(x, y - 2, x + kw, y + 10, (alpha / 2) << 24 | 0x0A0E18);
            g.outline(x, y - 2, kw, 12, (alpha * 3 / 4) << 24 | 0xC9D2E0);
            g.text(font, pairs[i], x + 3, y, alpha << 24 | 0xF2F4F8, false);
            x += kw + 3;
            g.text(font, pairs[i + 1], x, y, (alpha * 3 / 4) << 24 | 0xC9D2E0, true);
            x += font.width(pairs[i + 1]) + 11;
        }
    }

    /**
     * A page's label: over the row, on the page's own side of the piece. Beside the page it lay over its neighbours (and
     * over the page itself, at the row's left end); in the middle it would lie over the piece and the mark shown on it.
     */
    private int[] pageLabelAt(float ax, float ay, float ar, int lw, int lh, int gap) {
        int mid = width / 2, centred = Math.round(ax - lw / 2F);
        int x = ax < mid ? Math.min(centred, mid - gap - lw) : Math.max(centred, mid + gap);
        x = Mth.clamp(x, 4, Math.max(4, width - lw - 4));
        int y = Math.max(4, Math.round(ay - ar - 6 - lh));
        return new int[]{x, y};
    }

    /** The one label: the page pointed at. Its name and level, what it does, how deep it goes here and what it costs, or why not. */
    private void pageLabel(GuiGraphicsExtractor g, Desk.Offer o, int alpha) {
        // clear of the piece in the middle: a weapon is narrow, armour half the slate across
        LocalPlayer me = Minecraft.getInstance().player;
        int gap = me != null && dev.awaken.enchant.EnchantRules.armour(me.getMainHandItem()) ? 44 : 20;
        int lw = Mth.clamp(width / 2 - gap - 6, 96, 176), inner = lw - 14;
        int top = Inscriptions.top(o.h());
        List<FormattedCharSequence> body = font.split(Component.literal(o.e().line()), inner);
        boolean ok = o.ok();
        String deep = "";
        if (ok && top > 1) {
            int hi = Math.min(top, o.deepest());
            deep = "Up to " + Inscriptions.numeral(hi) + " here" + (!InscribeScene.study && o.anywhere() > o.deepest() ? ", " + Inscriptions.numeral(o.anywhere()) + " in your Study" : "");
        }
        List<FormattedCharSequence> why = ok ? List.of() : font.split(Component.literal(o.why()), inner);
        int rows = body.size() + why.size() + (ok ? (deep.isEmpty() ? 1 : 2) + 1 : 0);
        int lh = 18 + rows * 10 + 6;
        int[] at = pageLabelAt(Desk.screenX(true, Desk.hover), Desk.screenY(true, Desk.hover), Desk.screenR(true, Desk.hover), lw, lh, gap);
        int x = at[0], y = at[1], colour = brighten(o.e().colour());
        dev.awaken.signal.client.Glass.panel(g, x, y, lw, lh, colour, alpha / 255F);
        mark(g, o.e().no(), x + 11, y + 10, 6, alpha << 24 | colour, 1F);
        g.text(font, o.e().name() + (top > 1 ? " " + Inscriptions.numeral(o.to()) : ""), x + 22, y + 6, alpha << 24 | colour, false);
        int ly = y + 20;
        for (FormattedCharSequence l : body) {
            g.text(font, l, x + 7, ly, alpha << 24 | 0xDDE4EE, false);
            ly += 10;
        }
        if (!ok) {
            for (FormattedCharSequence l : why) {
                g.text(font, l, x + 7, ly, alpha << 24 | 0xFFB0A6, false);
                ly += 10;
            }
            return;
        }
        if (!deep.isEmpty()) {
            dev.awaken.signal.client.Ui.fitted(g, font, deep, x + 7, ly, inner, 1, (alpha * 3 / 4) << 24 | 0xBCCDE3, false);
            ly += 10;
        }
        // what it costs, as the things themselves; red when you can't pay
        price(g, o.price(), x + 7, ly, alpha << 24 | (o.afford() ? 0xF2F4F8 : 0xFF8A7A));
        ly += 10;
        String how = o.memory() ? "Click: from memory. Shift and click: trace it." : top > 1 && Math.min(top, o.deepest()) > o.now() + 1 ? "Click to write it. Wheel: its level." : "Click to write it.";
        dev.awaken.signal.client.Ui.fitted(g, font, how, x + 7, ly, inner, 1, (alpha * 3 / 4) << 24 | 0xBCCDE3, false);
    }

    /** A socket pointed at: what is written there and how it comes off, or that there is room. */
    private void socketLabel(GuiGraphicsExtractor g, int j, int alpha) {
        Desk.On on = j < Desk.ON.size() ? Desk.ON.get(j) : null;
        // to its left, the side away from the piece (the sockets run down the slate's left side)
        float ax = Desk.screenX(false, j), ar = Desk.screenR(false, j);
        int lw = Mth.clamp(Math.round(ax - ar - 12), 96, 170), inner = lw - 14;
        List<FormattedCharSequence> body = on == null ? font.split(Component.literal("Room for one more."), inner)
                : on.e() == null ? font.split(Component.literal("It was on this piece before, and can't be written any more."), inner)
                : font.split(Component.literal(on.e().line()), inner);
        List<FormattedCharSequence> how = on == null ? List.of() : font.split(Component.literal("Hold to scrape it off. Nothing comes back."), inner);
        int lh = (on == null ? 6 : 18) + body.size() * 10 + 6 + how.size() * 10;
        int x = Mth.clamp(Math.round(ax - ar - 8 - lw), 4, Math.max(4, width - lw - 4));
        int y = Mth.clamp(Math.round(Desk.screenY(false, j) - lh / 2F), 4, Math.max(4, height - lh - 18));
        int colour = on == null ? InscribeScene.LAPIS : on.e() == null ? 0xB9C3D2 : brighten(on.e().colour());
        dev.awaken.signal.client.Glass.panel(g, x, y, lw, lh, colour, alpha / 255F);
        int ly = y + 6;
        if (on != null) {
            if (on.e() != null) mark(g, on.e().no(), x + 11, y + 10, 6, alpha << 24 | colour, 1F);
            g.text(font, on.name() + (on.level() > 1 || on.e() != null && maxLevel(on.e()) > 1 ? " " + Inscriptions.numeral(on.level()) : ""), x + 22, y + 6, alpha << 24 | colour, false);
            ly = y + 20;
        }
        for (FormattedCharSequence l : body) {
            g.text(font, l, x + 7, ly, alpha << 24 | 0xDDE4EE, false);
            ly += 10;
        }
        for (FormattedCharSequence l : how) {
            g.text(font, l, x + 7, ly, (alpha * 3 / 4) << 24 | 0xFFB0A6, false);
            ly += 10;
        }
    }

    /** The Hall's blackletter (its bitmap font; 24 pixels tall at size 1) and its soft round light. */
    private static final net.minecraft.network.chat.Style BLACKLETTER =
            net.minecraft.network.chat.Style.EMPTY.withFont(new net.minecraft.network.chat.FontDescription.Resource(dev.awaken.Awaken.id("hall_title")));
    private static final net.minecraft.resources.Identifier GLOW = dev.awaken.Awaken.id("textures/gui/hall/glow.png");

    /**
     * The wake, in the Hall's own words for a thing made (SPEC §52 "The moment it's made"), without its anvil: a hit of
     * light as the last stroke seals, then the enchantment's name in blackletter and how it came out; then what came
     * back and the two ways on. No card.
     */
    private void woke(GuiGraphicsExtractor g, float a) {
        EnchantPayloads.Done d = InscribeScene.done;
        Inscriptions.Entry e = InscribeScene.entry();
        if (d == null || e == null) return;
        float t = InscribeScene.now(a) - InscribeScene.phaseAt;
        Minecraft mc = Minecraft.getInstance();
        if (t < 5F && !mc.options.hideLightningFlash().get()) {
            float hit = 1F - t / 5F;
            g.fill(0, 0, width, height, (int) (hit * hit * 0.5F * 255F) << 24 | 0xFFE9C8);
        }
        float in = ease(Mth.clamp((t - 6F) / 5F, 0F, 1F));
        if (in > 0.02F) {
            int colour = brighten(e.colour()), na = (int) (255 * in);
            Component name = Component.literal(e.name() + (maxLevel(e) > 1 ? " " + Inscriptions.numeral(d.level()) : "")).withStyle(BLACKLETTER);
            float size = Math.min(1F, (width - 24F) / Math.max(1, font.width(name)));
            int tw = Math.round(font.width(name) * size), y = Math.round(16 + (1F - in) * (1F - in) * 8F);
            g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, GLOW, width / 2 - tw / 2 - 40, y - 12, 0F, 0F, tw + 80, Math.round(24 * size) + 30,
                    512, 512, 512, 512, (int) (in * 0.5F * 255F) << 24 | colour);
            g.pose().pushMatrix();
            g.pose().translate(width / 2F - tw / 2F, y + 11F * size);
            g.pose().scale(size, size);
            g.text(font, name, 0, 0, na << 24 | 0xF4F6FB, false);
            g.pose().popMatrix();
            String how = d.clean() ? "A clean hand" : d.card().contains("from memory") ? "From memory" : "Written";
            g.centeredText(font, how, width / 2, y + Math.round(26 * size) + 4, na << 24 | colour);
        }
        int alpha = (int) (255 * ease(Mth.clamp((t - 30) / 10F, 0F, 1F)));
        if (alpha < 24) return;
        int y = height - 30;
        if (d.refund() > 0) {
            String s = "+" + d.refund();
            int w0 = font.width(s) + 12 + font.width("back");
            int x = width / 2 - w0 / 2 - (d.remembered() ? 70 : 0);
            g.text(font, s, x, y, alpha << 24 | 0xF2E6CF, true);
            g.pose().pushMatrix();
            g.pose().translate(x + font.width(s) + 1, y - 1);
            g.pose().scale(.5625F, .5625F);
            g.item(LEVELS, 0, 0);
            g.pose().popMatrix();
            g.text(font, "back", x + font.width(s) + 12, y, alpha << 24 | 0xF2E6CF, true);
        }
        if (d.remembered()) g.centeredText(font, "Its mark is in your memory now.", width / 2 + (d.refund() > 0 ? 40 : 0), y, alpha << 24 | 0xBFD0FF);
        caps(g, alpha, "Space", "write another", "Esc", "done");
    }

    private boolean deskClick(MouseButtonEvent e) {
        if (InscribeScene.waiting) return true;
        if (Desk.socket >= 0) {
            if (Desk.socket < Desk.ON.size()) {
                Desk.held = Desk.socket;
                Desk.heldAt = age;
            }
            return true;
        }
        if (Desk.hover < 0 || Desk.hover >= Desk.OFFERS.size()) return true;
        Desk.Offer o = Desk.OFFERS.get(Desk.hover);
        if (!o.ok()) {
            InscribeScene.say(o.why());
            return true;
        }
        if (!o.afford()) {
            InscribeScene.say("You need " + EnchantRules.priceLine(o.price()) + ".");
            return true;
        }
        deskChoose(Desk.hover, o.memory() && !e.hasShiftDown());
        return true;
    }

    /** The page leaves the row for the piece, and the server is asked. */
    private void deskChoose(int i, boolean memory) {
        Desk.Offer o = Desk.OFFERS.get(i);
        Desk.picked = i;
        Desk.pickedAt = InscribeScene.ticks;
        InscribeScene.play(dev.awaken.enchant.EnchantContent.PAGE_TURN.get(), 0.7F, 0.9F);
        InscribeScene.choose(o.e(), o.to(), memory);
    }

    @Override
    public void removed() {
        pilotHover = 0;
        InscribeScene.act(EnchantPayloads.CANCEL, 0, 0, 0F);
        InscribeScene.end();
        InscribeScene.hoverNo = 0;
        super.removed();
    }

    // ---- what's on the pages ------------------------------------------------------------------------------------------

    private record Row(Inscriptions.Entry e, Holder<Enchantment> h, int now, int deepest, int anywhere, int to,
                       int[] price, String why, boolean memory, boolean afford) {}

    /** The right page's rows, worked out from what you know and the piece in hand (the server checks it all again). */
    private void build(LocalPlayer p, ItemStack piece) {
        rows.clear();
        var reg = p.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Known k = Known.of(p);
        int lapis = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(Items.LAPIS_LAZULI)) lapis += s.getCount();
        }
        List<Row> greyed = new ArrayList<>();
        for (Inscriptions.Entry e : Inscriptions.ALL) {
            if (!k.knows(e.id())) continue;
            var ref = reg.get(e.key());
            if (ref.isEmpty() || !EnchantRules.fits(piece, ref.get())) continue;
            Holder<Enchantment> h = ref.get();
            int now = EnchantRules.level(piece, h), top = Inscriptions.top(h);
            int deepest = EnchantRules.deepest(piece, h, e, InscribeScene.study), anywhere = EnchantRules.deepestAnywhere(piece, h, e);
            int lo = Math.min(top, now + 1), hi = Math.max(lo, Math.min(top, deepest));
            int to = Mth.clamp(targets.getOrDefault(e.no(), hi), lo, hi);
            String why = EnchantRules.why(piece, h, to, InscribeScene.study);
            int[] price = EnchantRules.price(e, now, to);
            boolean afford = p.hasInfiniteMaterials() || (p.experienceLevel >= price[0] && lapis >= price[1]);
            Row r = new Row(e, h, now, deepest, anywhere, to, price, why == null ? "" : why, k.inMemory(e.id()), afford);
            (why == null ? rows : greyed).add(r);
        }
        rows.addAll(greyed);
        onIt.clear();
        for (Object2IntMap.Entry<Holder<Enchantment>> x : piece.getEnchantments().entrySet())
            onIt.add(new int[]{reg.getId(x.getKey().value()), x.getIntValue()});
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------

    /** How far the book is up (0 down out of view, 1 open): it opens as the scene starts, and closes down while you trace. */
    private float bookShown(float a) {
        float t = age + a;
        float up = ease(Mth.clamp((t - 2) / 9F, 0F, 1F));
        if (InscribeScene.phase != InscribeScene.BOOK) {
            float since = InscribeScene.now(a) - InscribeScene.leftBookAt;
            return up * (1F - ease(Mth.clamp(since / 7F, 0F, 1F)));
        }
        if (InscribeScene.enteredBookAt > 0) {
            float since = InscribeScene.now(a) - InscribeScene.enteredBookAt;
            up = Math.min(up, ease(Mth.clamp(since / 8F, 0F, 1F)));
        }
        return up;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        if (!classic) {
            desk(g, mx, my, a);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        ItemStack piece = p.getMainHandItem();
        if (!InscribeScene.pilot && (InscribeScene.phase == InscribeScene.TRACE || InscribeScene.phase == InscribeScene.MEMORY))
            InscribeScene.pen(mx, my, width, height);
        float shown = bookShown(a);
        if (shown > 0.01F) {
            build(p, piece);
            w = Mth.clamp(width - 28, 300, 470);
            h = pagesHeight(height);
            x0 = (width - w) / 2;
            y0 = height - h - 8 + (int) ((1F - shown) * (h + 20));
            spread(g, mx, my, piece, shown);
        }
        switch (InscribeScene.phase) {
            case InscribeScene.TRACE -> traceUi(g, a);
            case InscribeScene.MEMORY -> memoryUi(g, a);
            case InscribeScene.WAKE -> card(g, mx, my, a);
            default -> {}
        }
        if (!InscribeScene.line.isEmpty() && InscribeScene.ticks - InscribeScene.lineAt < 80) {
            int alpha = (int) (255 * Mth.clamp((80 - (InscribeScene.ticks - InscribeScene.lineAt)) / 15F, 0F, 1F));
            int y = InscribeScene.phase == InscribeScene.BOOK ? y0 - 16 : height - 40;
            Component c = Component.literal(InscribeScene.line);
            int tw = font.width(c);
            g.fill(width / 2 - tw / 2 - 6, y - 4, width / 2 + tw / 2 + 6, y + 12, (alpha * 3 / 4) << 24 | 0x120A06);
            g.centeredText(font, c, width / 2, y, alpha << 24 | 0xF2D6B0);
        }
    }

    private void spread(GuiGraphicsExtractor g, int mx, int my, ItemStack piece, float shown) {
        int x1 = x0 + w, y1 = y0 + h, mid = x0 + w / 2;
        // the leather boards, a brass edge, then the block of pages under the open leaves
        g.fill(x0 - 6, y0 - 5, x1 + 6, y1 + 7, LEATHER_DARK);
        g.fill(x0 - 5, y0 - 4, x1 + 5, y1 + 6, LEATHER);
        g.fill(x0 - 5, y0 - 4, x1 + 5, y0 - 3, 0xFF6B4630);
        for (int c = 0; c < 4; c++) {
            int cx = (c & 1) == 0 ? x0 - 6 : x1 + 1, cy = (c & 2) == 0 ? y0 - 5 : y1 + 2;
            g.fill(cx, cy, cx + 5, cy + 5, BRASS_DARK);
            g.fill(cx + 1, cy + 1, cx + 4, cy + 4, BRASS);
        }
        for (int i = 0; i < 3; i++) g.fill(x0 + 2 + i, y1 + 1 + i, x1 - 2 - i, y1 + 2 + i, i == 1 ? PAGE_LINE : PARCHMENT_EDGE);
        // two leaves, warm paper darkening into the gutter
        g.fillGradient(x0, y0, x1, y1, PARCHMENT, 0xFFE2D2B2);
        g.fill(x0, y0, x1, y0 + 1, PARCHMENT_EDGE);
        for (int i = 0; i < 10; i++) {
            int alpha = (int) (60 * (1 - i / 10F) * (1 - i / 10F));
            g.fill(mid - 1 - i, y0, mid - i, y1, alpha << 24 | 0x5A3F22);
            g.fill(mid + i, y0, mid + i + 1, y1, alpha << 24 | 0x5A3F22);
        }
        g.fill(mid - 1, y0, mid + 1, y1, 0x50402A18);
        leftPage(g, mx, my, piece, x0 + 12, y0 + 10, w / 2 - 22, h - 18);
        rightPage(g, mx, my, mid + 12, y0 + 10, w / 2 - 22, h - 18);
    }

    private void leftPage(GuiGraphicsExtractor g, int mx, int my, ItemStack piece, int x, int y, int pw, int ph) {
        // the piece, drawn large
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(2F, 2F);
        g.item(piece, 0, 0);
        pose.popMatrix();
        String name = piece.getHoverName().getString();
        List<FormattedCharSequence> nameLines = font.split(Component.literal(name), pw - 40);
        int ty = y + 2;
        for (int i = 0; i < Math.min(2, nameLines.size()); i++, ty += 10) g.text(font, nameLines.get(i), x + 38, ty, INK, false);
        g.text(font, "Rank " + EnchantRules.rank(piece), x + 38, Math.max(ty, y + 22), INK_SOFT, false);
        int cy = y + 38;
        String room = EnchantRules.roomLine(piece);
        g.text(font, room, x, cy, room.startsWith("Room") ? 0xFF2F4F9A : RED_INK, false);
        cy += 14;
        // what's on it: a second click on scrape takes it off
        if (onIt.isEmpty()) {
            g.text(font, "Nothing is written on it yet.", x, cy, INK_FAINT, false);
            cy += 12;
        }
        var reg = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        for (int[] on : onIt) {
            if (reg == null || cy > y + ph - 30) break;
            var ref = reg.get(on[0]);
            if (ref.isEmpty()) continue;
            Inscriptions.Entry e = Inscriptions.of(ref.get());
            int colour = e == null ? INK_SOFT : 0xFF000000 | darken(e.colour());
            String label = ref.get().value().description().getString() + (Inscriptions.top(ref.get()) > 1 ? " " + Inscriptions.numeral(on[1]) : "");
            if (e != null) mark(g, e.no(), x + 5, cy + 4, 5, colour, 1F);
            dev.awaken.signal.client.Ui.fitted(g, font, label, x + 13, cy, pw - 52, 1, INK, false); // whole, never cut (§57.6)
            boolean armed = scrapeId == on[0] && age - scrapeAt < 40;
            String sc = armed ? "sure?" : "scrape";
            int sw = font.width(sc), sx = x + pw - sw;
            boolean over = InscribeScene.phase == InscribeScene.BOOK && mx >= sx - 2 && mx <= sx + sw + 2 && my >= cy - 2 && my <= cy + 9;
            if (over || armed) g.fill(sx - 2, cy + 9, sx + sw + 1, cy + 10, armed ? RED_INK : INK_FAINT);
            g.text(font, sc, sx, cy, armed ? RED_INK : over ? INK : INK_FAINT, false);
            cy += 13;
        }
        // how deep this table writes
        String study = InscribeScene.study ? "This table stands in your Study, so each enchantment can go as deep as the piece allows."
                : "Away from your Study, nothing goes deeper than II.";
        List<FormattedCharSequence> sl = font.split(Component.literal(study), pw);
        int sy = y + ph - sl.size() * 10;
        g.fill(x, sy - 5, x + pw, sy - 4, PAGE_LINE);
        for (FormattedCharSequence s : sl) {
            g.text(font, s, x, sy, InscribeScene.study ? 0xFF2F4F9A : INK_SOFT, false);
            sy += 10;
        }
    }

    private void rightPage(GuiGraphicsExtractor g, int mx, int my, int x, int y, int pw, int ph) {
        Known k = Minecraft.getInstance().player == null ? null : Known.of(Minecraft.getInstance().player);
        g.text(font, "What you know that fits", x, y, INK_SOFT, false);
        g.fill(x, y + 11, x + pw, y + 12, PAGE_LINE);
        int listY = y + 16, footH = 30, listH = ph - 16 - footH, visible = Math.max(1, listH / ROW);
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - visible));
        int was = hover;
        hover = -1;
        if (rows.isEmpty()) {
            List<FormattedCharSequence> l = font.split(Component.literal(k != null && k.count() == 0
                    ? "You don't know any enchantments yet. Gate Lords carry their pages, and the market sells a few."
                    : "Nothing you know fits this piece. You can still scrape off what's on it."), pw);
            int ly = listY + 4;
            for (FormattedCharSequence s : l) {
                g.text(font, s, x, ly, INK_FAINT, false);
                ly += 10;
            }
        }
        for (int i = scroll; i < Math.min(rows.size(), scroll + visible); i++) {
            Row r = rows.get(i);
            int ry = listY + (i - scroll) * ROW;
            boolean ok = r.why.isEmpty();
            boolean over = InscribeScene.phase == InscribeScene.BOOK && (pilotHover > 0 ? pilotHover == r.e.no()
                    : mx >= x - 4 && mx < x + pw + 2 && my >= ry - 2 && my < ry + ROW - 2);
            if (over) {
                hover = i;
                g.fill(x - 4, ry - 2, x + pw + 2, ry + ROW - 3, ok ? 0x28304A8A : 0x18402A18);
            }
            int colour = 0xFF000000 | darken(r.e.colour());
            float burn = over ? 1F : 0.85F;
            mark(g, r.e.no(), x + 8, ry + 9, 8, ok ? colour : 0xFFB4A488, burn);
            String lv = Inscriptions.top(r.h) > 1 ? " " + Inscriptions.numeral(r.to) : "";
            String nm = font.plainSubstrByWidth(r.e.name(), pw - 70);
            g.text(font, nm, x + 20, ry, ok ? INK : INK_FAINT, false);
            int lx = x + 20 + font.width(nm);
            if (!lv.isEmpty()) {
                g.text(font, lv, lx, ry, ok ? colour : INK_FAINT, false);
                // ‹ › pick a shallower or deeper level, between one above what's on it and the deepest here
                if (over && ok) {
                    int lo = r.now + 1, hi = Math.min(Inscriptions.top(r.h), r.deepest);
                    if (r.to > lo) g.text(font, "‹", lx + font.width(lv) + 3, ry, INK_SOFT, false);
                    if (r.to < hi) g.text(font, "›", lx + font.width(lv) + 10, ry, INK_SOFT, false);
                }
            }
            String sub;
            int subColour, costW = 0;
            int[] cost = null;
            // "from memory" sits at the end of the second line (the name keeps its whole line for its level arrows)
            int memW = ok && r.memory ? font.width("from memory") + 8 : 0;
            if (!ok) {
                sub = r.why;
                subColour = INK_FAINT;
            } else {
                // the price always shows: on a narrow page the Study's depth goes first (the left page says it), then "here"
                String deep = Inscriptions.top(r.h) > 1 ? Inscriptions.numeral(Math.min(Inscriptions.top(r.h), r.deepest)) : "";
                // the price as the things themselves, each with its count (SPEC §57.10; it read "6 levels and 2 lapis")
                String study = !InscribeScene.study && r.anywhere > r.deepest ? " (" + Inscriptions.numeral(r.anywhere) + " in your Study)" : "";
                cost = r.price;
                costW = priceWidth(cost);
                sub = deep.isEmpty() ? "" : fit(pw - 20 - memW - costW - 8, "Up to " + deep + " here" + study, "Up to " + deep + " here", "Up to " + deep);
                subColour = r.afford ? INK_SOFT : RED_INK;
            }
            int sx = x + 20, room = pw - 20 - memW - (cost == null ? 0 : costW + 8);
            if (!sub.isEmpty()) {
                dev.awaken.signal.client.Ui.fitted(g, font, sub, sx, ry + 10, room, 1, subColour, false); // whole, never cut (§57.6)
                sx += Math.min(font.width(sub), room) + 8;
            }
            if (cost != null) price(g, cost, sx, ry + 10, subColour);
            if (ok && r.memory) {
                String m = "from memory";
                int mw = font.width(m), mxl = x + pw - mw;
                boolean onM = over && mx >= mxl - 3 && my >= ry + 9;
                g.fill(mxl - 3, ry + 9, x + pw + 1, ry + 19, onM ? 0x40304A8A : 0x20304A8A);
                g.text(font, m, mxl, ry + 10, onM ? 0xFF2F4F9A : 0xFF4A64A8, false);
            }
        }
        if (rows.size() > visible) {
            // a ribbon down the edge shows where you are in the list
            int track = visible * ROW, bar = Math.max(10, track * visible / rows.size());
            int by = listY + (track - bar) * scroll / Math.max(1, rows.size() - visible);
            g.fill(x + pw + 5, listY, x + pw + 6, listY + track, 0x30402A18);
            g.fill(x + pw + 4, by, x + pw + 7, by + bar, 0xFF7A2E2A);
        }
        if (hover != was) hoverSince = age;
        InscribeScene.hoverNo = hover >= 0 ? rows.get(hover).e.no() : 0;
        // the foot of the page: what the one you're on does, in plain words
        int fy = y + ph - footH + 4;
        g.fill(x, fy - 4, x + pw, fy - 3, PAGE_LINE);
        if (hover >= 0) {
            Row r = rows.get(hover);
            List<FormattedCharSequence> l = font.split(Component.literal(r.e.line()), pw);
            for (int i = 0; i < Math.min(3, l.size()); i++) g.text(font, l.get(i), x, fy + i * 9, INK_SOFT, false);
        } else if (!rows.isEmpty()) {
            List<FormattedCharSequence> l = font.split(Component.literal("Choose one to trace its mark on the piece. A clean hand gives some levels back."), pw);
            for (int i = 0; i < Math.min(3, l.size()); i++) g.text(font, l.get(i), x, fy + i * 9, INK_FAINT, false);
        }
    }

    /** The first of these that fits the width, else the last. */
    private static final net.minecraft.world.item.ItemStack LEVELS = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EXPERIENCE_BOTTLE),
            LAPIS = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LAPIS_LAZULI);

    private int priceWidth(int[] p) {
        if (p[0] == 0 && p[1] == 0) return font.width("Free");
        return (p[0] > 0 ? font.width(Integer.toString(p[0])) + 11 : 0) + (p[0] > 0 && p[1] > 0 ? 5 : 0) + (p[1] > 0 ? font.width(Integer.toString(p[1])) + 11 : 0);
    }

    /** A price as the things it takes: the levels (a bottle o' enchanting stands for them) and the lapis, each a count and its item at half size. */
    private void price(GuiGraphicsExtractor g, int[] p, int x, int y, int colour) {
        if (p[0] == 0 && p[1] == 0) {
            g.text(font, "Free", x, y, colour, false);
            return;
        }
        for (int i = 0; i < 2; i++) {
            if (p[i] <= 0) continue;
            String n = Integer.toString(p[i]);
            g.text(font, n, x, y, colour, false);
            x += font.width(n) + 1;
            g.pose().pushMatrix();
            g.pose().translate(x, y - 1);
            g.pose().scale(.5625F, .5625F);
            g.item(i == 0 ? LEVELS : LAPIS, 0, 0);
            g.pose().popMatrix();
            x += 10 + 5;
        }
    }

    private String fit(int width, String... tries) {
        for (String t : tries) if (font.width(t) <= width) return t;
        return tries[tries.length - 1];
    }

    /** While you trace: its name and level, a pip for each stroke, how much of this stroke stays on the line. */
    private void traceUi(GuiGraphicsExtractor g, float a) {
        Inscriptions.Entry e = InscribeScene.entry();
        if (e == null) return;
        float in = ease(Mth.clamp((InscribeScene.now(a) - InscribeScene.phaseAt - 4) / 8F, 0F, 1F));
        int alpha = (int) (255 * in);
        if (alpha < 8) return;
        int cx = width / 2, by = height - 34;
        String title = e.name() + (InscribeScene.level > 1 || maxLevel(e) > 1 ? " " + Inscriptions.numeral(InscribeScene.level) : "");
        int tw = font.width(title);
        g.fillGradient(0, by - 18, width, height, 0, (int) (alpha * 0.6F) << 24 | 0x07050A);
        g.centeredText(font, title, cx, by - 8, alpha << 24 | brighten(e.colour()));
        int n = InscribeScene.strokes();
        int px = cx - (n * 12 - 4) / 2;
        for (int i = 0; i < n; i++) {
            int x = px + i * 12, y = by + 6;
            boolean done = i < InscribeScene.stroke, cur = i == InscribeScene.stroke;
            int c = done ? (InscribeScene.SHARE[i] >= 0.8F ? brighten(e.colour()) : 0xB0A090) : cur ? 0xE8E0D0 : 0x605850;
            g.fill(x, y, x + 8, y + 8, alpha << 24 | 0x100A08);
            g.fill(x + 1, y + 1, x + 7, y + 7, (done ? alpha : alpha / (cur ? 1 : 2)) << 24 | c);
            // a cut stroke has lapis in it, as its line on the piece does
            if (done) g.fill(x + 3, y + 3, x + 5, y + 5, alpha << 24 | InscribeScene.LAPIS);
            if (cur && !InscribeScene.drawing) {
                int pulse = (int) (alpha * (0.5F + 0.5F * Mth.sin(InscribeScene.now(a) * 0.3F)));
                g.outline(x - 2, y - 2, 12, 12, pulse << 24 | brighten(e.colour()));
            }
        }
        if (InscribeScene.drawing && InscribeScene.ran > 0) {
            float share = InscribeScene.share();
            int bw = 80, bx = cx - bw / 2, y = by + 18;
            g.fill(bx, y, bx + bw, y + 2, alpha / 2 << 24 | 0x302820);
            g.fill(bx, y, bx + (int) (bw * share), y + 2, alpha << 24 | (share >= 0.8F ? brighten(e.colour()) : 0xC08060));
        }
        int hintAge = traceSeen == 0 ? 0 : age - traceSeen;
        if (classic && InscribeScene.stroke == 0 && hintAge < 400) {
            g.centeredText(font, "Draw each stroke from its glowing end. Stay on the line for a clean hand.", cx, 14, alpha << 24 | 0xE8DCC8);
            g.centeredText(font, "Esc steps back, and your levels come back too.", cx, 26, (alpha * 2 / 3) << 24 | 0xB8AC98);
        }
    }

    private void memoryUi(GuiGraphicsExtractor g, float a) {
        Inscriptions.Entry e = InscribeScene.entry();
        if (e == null) return;
        int alpha = (int) (255 * ease(Mth.clamp((InscribeScene.now(a) - InscribeScene.phaseAt - 4) / 8F, 0F, 1F)));
        if (alpha < 8) return;
        g.fillGradient(0, height - 52, width, height, 0, (int) (alpha * 0.6F) << 24 | 0x07050A);
        g.centeredText(font, e.name() + " " + Inscriptions.numeral(InscribeScene.level), width / 2, height - 42, alpha << 24 | brighten(e.colour()));
        g.centeredText(font, "From memory: your hand knows this one.", width / 2, height - 28, (alpha * 3 / 4) << 24 | 0xD8CCB8);
    }

    /** Ticks into the wake before the card comes up: after the piece's turn (6-40) has settled. */
    static final int CARD_AT = 42;

    private static int pagesHeight(int guiH) {
        return Mth.clamp((int) (guiH * 0.52F), 140, 214);
    }

    /** Where the book's pages start down the screen (0 top, 1 bottom): the scene frames the piece above them. */
    static double pagesTop(int guiH) {
        return 1 - (pagesHeight(guiH) + 8.0) / guiH;
    }

    /** Where the card starts down the screen, near enough (its lines vary): the wake is framed above it. */
    static double cardTop(int guiH) {
        return 1 - 110.0 / guiH;
    }

    /** It woke: what you made, and two ways on (once the piece has turned and settled). */
    private void card(GuiGraphicsExtractor g, int mx, int my, float a) {
        EnchantPayloads.Done d = InscribeScene.done;
        Inscriptions.Entry e = InscribeScene.entry();
        if (d == null || e == null) return;
        float t = InscribeScene.now(a) - InscribeScene.phaseAt;
        float in = ease(Mth.clamp((t - CARD_AT) / 10F, 0F, 1F));
        if (in <= 0) return;
        int alpha = (int) (255 * in);
        int cw = Math.min(width - 40, 300);
        List<FormattedCharSequence> lines = new ArrayList<>(font.split(Component.literal(d.card()), cw - 24));
        if (d.remembered()) lines.addAll(font.split(Component.literal("Its mark is in your memory now: next time you can write it in one stroke."), cw - 24));
        int ch = 34 + lines.size() * 10 + 22;
        int cx = (width - cw) / 2, cy = height - ch - 14 + (int) ((1 - in) * 12);
        int colour = brighten(e.colour());
        g.fill(cx - 1, cy - 1, cx + cw + 1, cy + ch + 1, (alpha * 3 / 4) << 24 | (colour & 0xFFFFFF));
        g.fill(cx, cy, cx + cw, cy + ch, (int) (alpha * 0.92F) << 24 | 0x120C10);
        g.fill(cx, cy, cx + cw, cy + 2, alpha << 24 | colour);
        mark(g, e.no(), cx + 16, cy + 16, 9, alpha << 24 | colour, 1F);
        g.text(font, e.name() + (maxLevel(e) > 1 ? " " + Inscriptions.numeral(d.level()) : ""), cx + 32, cy + 8, alpha << 24 | colour, false);
        g.text(font, d.clean() ? "A clean hand" : "Written", cx + 32, cy + 18, (alpha * 2 / 3) << 24 | 0xD8CCB8, false);
        int ly = cy + 34;
        for (FormattedCharSequence l : lines) {
            g.text(font, l, cx + 12, ly, alpha << 24 | 0xEDE3D2, false);
            ly += 10;
        }
        button(g, mx, my, cx + 12, cy + ch - 18, "Write another", alpha);
        button(g, mx, my, cx + cw - 12 - font.width("Done") - 8, cy + ch - 18, "Done", alpha);
    }

    private int[] cardButtons() {
        EnchantPayloads.Done d = InscribeScene.done;
        if (d == null) return new int[0];
        int cw = Math.min(width - 40, 300);
        int n = font.split(Component.literal(d.card()), cw - 24).size()
                + (d.remembered() ? font.split(Component.literal("Its mark is in your memory now: next time you can write it in one stroke."), cw - 24).size() : 0);
        int ch = 34 + n * 10 + 22, cx = (width - cw) / 2, cy = height - ch - 14;
        int doneX = cx + cw - 12 - font.width("Done") - 8;
        return new int[]{cx + 12, cy + ch - 18, cx + 12 + font.width("Write another") + 8, doneX, doneX + font.width("Done") + 8};
    }

    private void button(GuiGraphicsExtractor g, int mx, int my, int x, int y, String label, int alpha) {
        int bw = font.width(label) + 8;
        boolean over = mx >= x && mx < x + bw && my >= y && my < y + 12;
        g.fill(x, y, x + bw, y + 12, (over ? alpha : alpha / 3) << 24 | 0x6B5A44);
        g.text(font, label, x + 4, y + 2, alpha << 24 | (over ? 0xFFFFFF : 0xE0D4C0), false);
    }

    /** An enchantment's mark in the book, drawn with the pen: each stroke's points, {@code burn} of the way along. */
    static void mark(GuiGraphicsExtractor g, int no, int cx, int cy, int half, int argb, float burn) {
        for (float[] s : Marks.of(no)) {
            int n = s.length / 2, upto = Math.max(2, (int) (n * burn));
            for (int i = 0; i + 1 < upto; i++) {
                float ax = cx + s[i * 2] * half, ay = cy - s[i * 2 + 1] * half;
                float bx = cx + s[i * 2 + 2] * half, by = cy - s[i * 2 + 3] * half;
                int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(bx - ax), Math.abs(by - ay))));
                for (int k = 0; k < steps; k++) {
                    int px = Math.round(Mth.lerp(k / (float) steps, ax, bx)), py = Math.round(Mth.lerp(k / (float) steps, ay, by));
                    g.fill(px, py, px + 1, py + 1, argb);
                }
            }
        }
    }

    // ---- the autopilot's hand (tours) -----------------------------------------------------------------------------------

    /** The row the pilot rests on (its Journal number; 0: the mouse decides). */
    static int pilotHover;

    /** The pilot chooses enchantment {@code no} as a click on its row would (at its default level); false when it isn't there. */
    boolean pilotChoose(int no, boolean memory) {
        if (!classic) {
            for (int i = 0; i < Desk.OFFERS.size(); i++) {
                Desk.Offer o = Desk.OFFERS.get(i);
                if (o.e().no() != no || !o.ok()) continue;
                deskChoose(i, memory && o.memory());
                return true;
            }
            return false;
        }
        for (Row r : rows) {
            if (r.e.no() != no || !r.why.isEmpty()) continue;
            InscribeScene.choose(r.e, r.to, memory && r.memory);
            return true;
        }
        return false;
    }

    // ---- input --------------------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (e.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(e, doubleClick);
        double mx = e.x(), my = e.y();
        switch (InscribeScene.phase) {
            case InscribeScene.TRACE -> {
                InscribeScene.pen(mx, my, width, height);
                InscribeScene.press();
                return true;
            }
            case InscribeScene.WAKE -> {
                if (!classic) return true;
                int[] b = InscribeScene.ticks - InscribeScene.phaseAt < CARD_AT ? new int[0] : cardButtons();
                if (b.length > 0 && my >= b[1] && my < b[1] + 12) {
                    if (mx >= b[0] && mx < b[2]) {
                        InscribeScene.again();
                        return true;
                    }
                    if (mx >= b[3] && mx < b[4]) {
                        onClose();
                        return true;
                    }
                }
                return true;
            }
            case InscribeScene.BOOK -> {
                return classic ? bookClick(mx, my) : deskClick(e);
            }
            default -> {
                return true;
            }
        }
    }

    private boolean bookClick(double mx, double my) {
        if (InscribeScene.waiting) return true;
        // scrape, on the left page
        int x = x0 + 12, pw = w / 2 - 22, cy = y0 + 10 + 38 + 14 + (onIt.isEmpty() ? 12 : 0);
        for (int[] on : onIt) {
            if (cy > y0 + 10 + h - 18 - 30) break;
            String sc = scrapeId == on[0] && age - scrapeAt < 40 ? "sure?" : "scrape";
            int sw = font.width(sc), sx = x + pw - sw;
            if (mx >= sx - 2 && mx <= sx + sw + 2 && my >= cy - 2 && my <= cy + 9) {
                if (scrapeId == on[0] && age - scrapeAt < 40) {
                    scrapeId = -1;
                    InscribeScene.act(EnchantPayloads.SCRAPE, on[0], 0, 0F);
                    InscribeScene.play(dev.awaken.enchant.EnchantContent.BURIN_METAL.get(), 0.5F, 0.7F);
                    var mc = Minecraft.getInstance();
                    var ref = mc.level == null ? java.util.Optional.<net.minecraft.core.Holder.Reference<net.minecraft.world.item.enchantment.Enchantment>>empty()
                            : mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(on[0]);
                    Inscriptions.Entry e = ref.map(Inscriptions::of).orElse(null);
                    if (e != null) InscribeScene.scraped(e.no());
                } else {
                    scrapeId = on[0];
                    scrapeAt = age;
                }
                return true;
            }
            cy += 13;
        }
        // a row on the right page: its level arrows, from memory, or the row itself
        if (hover < 0 || hover >= rows.size()) return true;
        Row r = rows.get(hover);
        int rx = x0 + w / 2 + 12, rpw = w / 2 - 22, ry = y0 + 10 + 16 + (hover - scroll) * ROW;
        if (!r.why.isEmpty()) {
            InscribeScene.say(r.why);
            return true;
        }
        if (Inscriptions.top(r.h) > 1 && my < ry + 9) {
            String nm = font.plainSubstrByWidth(r.e.name(), rpw - 70), lv = " " + Inscriptions.numeral(r.to);
            int lx = rx + 20 + font.width(nm) + font.width(lv);
            int lo = r.now + 1, hi = Math.min(Inscriptions.top(r.h), r.deepest);
            if (mx >= lx && mx < lx + 8 && r.to > lo) {
                targets.put(r.e.no(), r.to - 1);
                InscribeScene.play(dev.awaken.enchant.EnchantContent.PAGE_TURN.get(), 0.3F, 1.4F);
                return true;
            }
            if (mx >= lx + 8 && mx < lx + 16 && r.to < hi) {
                targets.put(r.e.no(), r.to + 1);
                InscribeScene.play(dev.awaken.enchant.EnchantContent.PAGE_TURN.get(), 0.3F, 1.5F);
                return true;
            }
        }
        boolean memory = r.memory && mx >= rx + rpw - font.width("from memory") - 3 && my >= ry + 9;
        if (!r.afford) {
            InscribeScene.say("You need " + EnchantRules.priceLine(r.price) + ".");
            return true;
        }
        InscribeScene.choose(r.e, r.to, memory);
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (InscribeScene.phase == InscribeScene.TRACE) {
            InscribeScene.pen(e.x(), e.y(), width, height);
            InscribeScene.drag();
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        Desk.held = -1;
        InscribeScene.release();
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (InscribeScene.phase == InscribeScene.BOOK && !classic) {
            // the wheel over a page: a shallower or deeper level, between one above what's on it and the deepest here
            if (Desk.hover >= 0 && Desk.hover < Desk.OFFERS.size()) {
                Desk.Offer o = Desk.OFFERS.get(Desk.hover);
                int lo = o.now() + 1, hi = Math.min(Inscriptions.top(o.h()), o.deepest());
                int to = Mth.clamp(o.to() + (int) Math.signum(sy), lo, Math.max(lo, hi));
                if (o.ok() && to != o.to()) {
                    Desk.TARGETS.put(o.e().no(), to);
                    InscribeScene.play(dev.awaken.enchant.EnchantContent.PAGE_TURN.get(), 0.3F, to > o.to() ? 1.5F : 1.4F);
                }
            }
            return true;
        }
        if (InscribeScene.phase == InscribeScene.BOOK) {
            scroll -= (int) Math.signum(sy);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (InscribeScene.phase == InscribeScene.WAKE && (e.key() == InputConstants.KEY_RETURN || e.key() == InputConstants.KEY_SPACE)) {
            InscribeScene.again();
            return true;
        }
        return super.keyPressed(e);
    }

    // ---- small helpers ------------------------------------------------------------------------------------------------

    private static int maxLevel(Inscriptions.Entry e) {
        var level = Minecraft.getInstance().level;
        if (level == null) return 1;
        return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(e.key()).map(Inscriptions::top).orElse(1);
    }

    /** A family colour deep enough to read as ink on paper. */
    private static int darken(int rgb) {
        int r = (rgb >> 16) & 255, gr = (rgb >> 8) & 255, b = rgb & 255;
        return ((int) (r * 0.55F) << 16) | ((int) (gr * 0.55F) << 8) | (int) (b * 0.55F);
    }

    private static int brighten(int rgb) {
        return InscribeScene.hot(rgb, 0.25F);
    }

    private static float ease(float x) {
        return x * x * (3 - 2 * x);
    }
}
