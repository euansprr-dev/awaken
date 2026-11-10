package dev.awaken.hearth.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.core.client.ui.BandPlaces;
import dev.awaken.core.client.ui.Hot;
import dev.awaken.core.client.ui.Kit;
import dev.awaken.core.client.ui.Sheet;
import dev.awaken.hearth.Comfort;
import dev.awaken.hearth.TierRules;
import dev.awaken.hearth.guide.Guide;
import dev.awaken.hearth.guide.GuideData;
import dev.awaken.hearth.player.HearthView;
import dev.awaken.hearth.room.Furniture;
import dev.awaken.hearth.room.RoomType;
import dev.awaken.kin.client.KinStrip;
import dev.awaken.progression.client.StatusSections;
import dev.awaken.signal.client.Glass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Hearth on the band's stage (SPEC §57.7): one Hearth, two doors. Your home's name, tier and Comfort; every room kind as
 * its own signature piece, lit by state (yours, started, open, not found); who lives with you as themselves; and one panel
 * that reads whatever you point at: a room's pieces as real items with the recipe of the one you're missing, a built
 * room's reading, a beast, a kind, a neighbour, and with nothing in focus the home itself and its next tier. K looks and
 * tracks. Used at the Anchor (or the first fire) the same place also raises the tier and takes Oren's offer: what was the
 * separate "Your Hearth" window ({@code HearthScreen}, kept for the classic band).
 */
final class HearthPlace implements BandPlaces.Place {
    static final HearthPlace INSTANCE = new HearthPlace();

    private static final float X0 = BandPlaces.X0, Y0 = BandPlaces.Y0, X1 = BandPlaces.X1, Y1 = BandPlaces.Y1;
    private static final float PANEL = X1 - 488, LEFT_W = PANEL - 24 - X0, PW = 440;
    private static final float TILE_STEP = 117, TILE_W = 104, TILE_H = 72;
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V"};
    private static final String[] READINGS = {"Closed", "Light", "Warmth", "Room", "Care", "Detail"};
    private static final String[] GRADES = {"Bare", "Snug", "Fine", "Grand"};
    private static final float HOLD = .6F;

    private enum What { HOME, ROOM, LOOK, BEAST, KIN, PERSON, WANT }

    /** What the panel reads when clicked (a room kind's name, a look's index, a beast's id, a kind, a name, a want's words). */
    private What focus = What.HOME;
    private String on = "";
    private What pointed = What.HOME;
    private String pointedOn = "";
    /** Whether the Anchor (or the fire) was used to come here: only then can the tier be raised and Oren's offer taken. */
    private boolean anchor;
    private boolean showing, holdKey, holdMouse;
    private float leftScroll, leftTall, panelScroll, panelTall, hold;
    private String panelFor = "";
    private @Nullable String tipTitle;
    private List<String> tipMore = List.of();

    private HearthPlace() {}

    boolean showing() {
        return showing;
    }

    @Override
    public void opened(String... link) {
        anchor = link.length > 0 && link[0].equals("anchor");
        if (link.length > 0 && !anchor) for (RoomType t : RoomType.values()) if (t.name().equalsIgnoreCase(link[0]) || t.label.equalsIgnoreCase(link[0])) {
            focus = What.ROOM;
            on = t.name();
        }
        showing = true;
        GuideClient.send(Guide.Act.REFRESH); // the server's reading of the home, kept fresh while this shows
    }

    @Override
    public void left() {
        if (showing) GuideClient.closed();
        showing = false;
        anchor = false;
        hold = 0;
        holdKey = holdMouse = false;
    }

    @Override
    public @Nullable String chip() {
        HearthView v = HomePage.view();
        if (!v.has()) return null;
        return anchor ? null : v.tier() <= 0 ? "At your fire you can do more" : "At your Anchor you can do more";
    }

    // ---- drawing -----------------------------------------------------------------------------------------------------

    @Override
    public void draw(Sheet s, Hot hot, float mx, float my, float dt) {
        showing = true;
        KinStrip.ask();
        tipTitle = null;
        HearthView v = HomePage.view();
        if (!v.has()) {
            s.lit("No home yet", X0, Y0 + 180, Sheet.TITLE, Glass.a(Glass.TITLE, 1F));
            s.lines("Ilse keeps the last fire up the north road. Carry a coal from it and set it down where you'd live: your home starts at that fire.",
                    X0, Y0 + 222, 760, Sheet.BODY, Glass.a(s.label, 1F));
            return;
        }
        GuideData d = GuideClient.data();
        pointed = What.HOME;
        pointedOn = "";
        leftScroll = Math.clamp(leftScroll, 0F, Math.max(0F, leftTall - (Y1 - Y0)));
        boolean in = Sheet.in(mx, my, X0 - 8, Y0, PANEL - 12, Y1);
        s.g.enableScissor(Math.round(X0) - 8, Math.round(Y0) - 8, Math.round(PANEL) - 12, Math.round(Y1));
        hot.clip(X0 - 8, Y0, PANEL - 12, Y1);
        float end = left(s, hot, v, d, Y0 - leftScroll, in ? mx : -10000, in ? my : -10000);
        hot.unclip();
        s.g.disableScissor();
        leftTall = end - (Y0 - leftScroll);
        Kit.scrollbar(s, PANEL - 12, Y0, Y1, leftScroll, leftTall);
        s.fill(PANEL, Y0, PANEL + 1, Y1, Glass.a(Glass.EDGE, .2F));
        What w = pointed != What.HOME ? pointed : focus;
        String id = pointed != What.HOME ? pointedOn : on;
        String key = w + "|" + id;
        if (!key.equals(panelFor)) {
            panelFor = key;
            panelScroll = 0;
        }
        panelScroll = Math.clamp(panelScroll, 0F, Math.max(0F, panelTall - (Y1 - Y0)));
        boolean inPanel = Sheet.in(mx, my, PANEL, Y0, X1 + 8, Y1);
        s.g.enableScissor(Math.round(PANEL) + 2, Math.round(Y0) - 8, Math.round(X1) + 8, Math.round(Y1));
        hot.clip(PANEL, Y0, X1 + 8, Y1);
        float pend = panel(s, hot, v, d, w, id, Y0 - panelScroll, inPanel ? mx : -10000, inPanel ? my : -10000, dt);
        hot.unclip();
        s.g.disableScissor();
        panelTall = pend - (Y0 - panelScroll);
        Kit.scrollbar(s, X1 + 10, Y0, Y1, panelScroll, panelTall);
        if (tipTitle != null) Kit.tip(s, mx, my, tipTitle, tipMore);
    }

    /** The home's head, its rooms, who lives here and what it asks for. Returns the y under it. */
    private float left(Sheet s, Hot hot, HearthView v, @Nullable GuideData d, float y0, float mx, float my) {
        float y = y0;
        String name = v.home().name().isEmpty() ? (v.tier() <= 0 ? "Your Hearthfire" : "Your home") : v.home().name();
        boolean overHome = Sheet.in(mx, my, X0, y, X0 + LEFT_W, y + 78);
        y = s.lines(name, X0, y, LEFT_W, Sheet.TITLE, Glass.a(focus == What.HOME || overHome ? Glass.TITLE : Glass.TEXT, 1F)) + 6;
        float cx = X0;
        cx += Kit.chip(s, ItemStack.EMPTY, v.tier() <= 0 ? "Hearthfire" : "Tier " + ROMAN[Math.clamp(v.tier(), 0, 5)] + " · " + TierRules.name(v.tier()), cx, y, Glass.LANTERN) + 10;
        if (v.tier() > 0 && v.forgeTier() > 0) cx += Kit.chip(s, ItemStack.EMPTY, "Forge " + ROMAN[Math.clamp(v.forgeTier(), 0, 5)], cx, y, Glass.AMBER) + 10;
        s.caps("Comfort", cx + 8, y + 9, Glass.a(s.label, 1F));
        cx += 8 + s.width("COMFORT", Sheet.LABEL) + 10;
        cx += Kit.pips(s, cx, y + 9, v.home().comfort(), Comfort.MAX, 12, Glass.LANTERN) + 6;
        s.text(v.home().comfort() + "/" + Comfort.MAX, cx, y + 9, Sheet.LABEL, Glass.a(s.label, 1F));
        cx += s.width(v.home().comfort() + "/" + Comfort.MAX, Sheet.LABEL) + 18;
        long rested = dev.awaken.hearth.Rested.left(Minecraft.getInstance().player);
        if (rested > 0) Kit.chip(s, ItemStack.EMPTY, "Rested " + Math.max(1, (rested + 1199) / 1200) + " min", cx, y, Glass.AMBER);
        hot.add(X0, y0, X0 + LEFT_W, y + 30, () -> focus(What.HOME, ""));
        y += 52;

        // every room kind, as its own piece
        List<HearthView.Tile> tiles = v.tiles();
        if (!tiles.isEmpty()) {
            int built = 0;
            for (HearthView.Tile t : tiles) if (t.state() == HearthView.Tile.BUILT) built++;
            s.caps("Rooms · " + built + " of " + tiles.size(), X0, y, Glass.a(s.label, 1F));
            y += 26;
            int per = (int) (LEFT_W / TILE_STEP);
            for (int i = 0; i < tiles.size(); i += per) {
                float rowH = 0;
                for (int j = i; j < Math.min(tiles.size(), i + per); j++) {
                    RoomType k = kind(tiles.get(j));
                    boolean hidden = k == null || tiles.get(j).state() == HearthView.Tile.HIDDEN;
                    rowH = Math.max(rowH, TILE_H + 6 + (hidden ? 0 : s.tall(k.label, TILE_STEP - 8, Sheet.LABEL)));
                }
                for (int j = i; j < Math.min(tiles.size(), i + per); j++) room(s, hot, tiles.get(j), X0 + (j - i) * TILE_STEP, y, mx, my);
                y += rowH + 12;
            }
            y += 6;
        }

        // the rooms as the Hearth reads them, each with its grade
        if (!v.looks().isEmpty()) {
            s.caps("As your home reads", X0, y, Glass.a(s.label, 1F));
            y += 26;
            float x = X0;
            for (int i = 0; i < v.looks().size(); i++) {
                HearthView.Look l = v.looks().get(i);
                int grade = Math.clamp(l.grade(), 0, 3), rgb = grade >= 3 ? Glass.GOOD : grade == 2 ? Glass.EDGE : Glass.AMBER;
                String words = GRADES[grade] + " · " + cap(l.name());
                float w = 24 + s.width(words.toUpperCase(Locale.ROOT), Sheet.LABEL);
                if (x > X0 && x + w > X0 + LEFT_W) {
                    x = X0;
                    y += 38;
                }
                boolean over = Sheet.in(mx, my, x, y, x + w, y + 30), lit = focus == What.LOOK && on.equals(Integer.toString(i));
                if (over || lit) s.fill(x, y, x + w, y + 30, Glass.a(rgb, .16F));
                Kit.chip(s, ItemStack.EMPTY, words, x, y, rgb);
                if (over) point(What.LOOK, Integer.toString(i));
                int index = i;
                hot.add(x, y, x + w, y + 30, () -> focus(What.LOOK, Integer.toString(index)));
                x += w + 10;
            }
            y += 48;
        }

        // who lives here: your beasts as themselves, the kinds you know, the people
        List<KinStrip.Beast> beasts = KinStrip.beasts();
        List<KinStrip.Kind> kinds = KinStrip.kinds();
        List<GuideData.Person> town = d == null ? List.of() : d.town(), coming = d == null ? List.of() : d.coming();
        if (!beasts.isEmpty() || !kinds.isEmpty() || !town.isEmpty() || !coming.isEmpty()) {
            s.caps("Living here · " + (beasts.size() + town.size()), X0, y, Glass.a(s.label, 1F));
            y += 26;
            float x = X0, rowH = 0;
            for (KinStrip.Beast b : beasts) {
                float h = creature(s, hot, KinStrip.body(b.kind()), b.shown(), Kit.Lit.YOURS, What.BEAST, b.id().isEmpty() ? b.kind() : b.id(), x, y, mx, my);
                rowH = Math.max(rowH, h);
                x += TILE_STEP;
                if (x + TILE_W > X0 + LEFT_W) {
                    x = X0;
                    y += rowH + 12;
                    rowH = 0;
                }
            }
            for (KinStrip.Kind k : kinds) {
                float h = creature(s, hot, k.met() ? KinStrip.body(k.id()) : null, k.met() ? k.title() : "", k.met() ? Kit.Lit.KNOWN : Kit.Lit.NOT_FOUND, What.KIN, k.id(), x, y, mx, my);
                rowH = Math.max(rowH, h);
                x += TILE_STEP;
                if (x + TILE_W > X0 + LEFT_W) {
                    x = X0;
                    y += rowH + 12;
                    rowH = 0;
                }
            }
            if (x > X0) y += rowH + 12;
            x = X0;
            for (int pass = 0; pass < 2; pass++) for (GuideData.Person p : pass == 0 ? town : coming) {
                String words = p.name() + " · " + p.role();
                float w = 24 + s.width(words.toUpperCase(Locale.ROOT), Sheet.LABEL);
                if (x > X0 && x + w > X0 + LEFT_W) {
                    x = X0;
                    y += 38;
                }
                int rgb = pass == 0 ? Glass.LANTERN : 0x6F829C;
                boolean over = Sheet.in(mx, my, x, y, x + w, y + 30), lit = focus == What.PERSON && on.equals(p.name());
                if (over || lit) s.fill(x, y, x + w, y + 30, Glass.a(rgb, .16F));
                Kit.chip(s, ItemStack.EMPTY, words, x, y, rgb);
                if (over) point(What.PERSON, p.name());
                hot.add(x, y, x + w, y + 30, () -> focus(What.PERSON, p.name()));
                x += w + 10;
            }
            if (x > X0) y += 48;
        }

        // what the home asks for: each a line, the ones with a lead to follow can be tracked
        List<KinStrip.Want> wants = KinStrip.wants();
        boolean head = false;
        for (KinStrip.Want want : wants) {
            if (want.state().equals("head") || want.state().equals("tier") || want.state().equals("dim")) continue;
            if (!head) {
                s.caps("Your home asks for", X0, y, Glass.a(s.label, 1F));
                y += 26;
                head = true;
            }
            boolean met = want.state().equals("met"), can = !want.kind().isEmpty() || !want.rumour().isEmpty();
            float h = s.tall(want.text(), LEFT_W - 24, Sheet.BODY) + 6;
            boolean over = can && Sheet.in(mx, my, X0 - 4, y - 3, X0 + LEFT_W, y + h - 3), lit = focus == What.WANT && on.equals(want.text());
            if (over || lit) s.fill(X0 - 4, y - 3, X0 + LEFT_W, y + h - 3, Glass.a(Glass.AMBER, .10F));
            y = Kit.conditionLines(s, want.text(), met, X0, y, LEFT_W);
            if (over) point(What.WANT, want.text());
            if (can) hot.add(X0 - 4, y - h - 3, X0 + LEFT_W, y - 3, () -> focus(What.WANT, want.text()));
        }
        return y + 8;
    }

    /** A room kind: its signature piece in a well, lit by state, its name under it; a kind you haven't found is a dark shape with no name. */
    private void room(Sheet s, Hot hot, HearthView.Tile t, float x, float y, float mx, float my) {
        RoomType k = kind(t);
        boolean hidden = k == null || t.state() == HearthView.Tile.HIDDEN;
        boolean over = !hidden && Sheet.in(mx, my, x, y, x + TILE_W, y + TILE_H), lit = !hidden && focus == What.ROOM && on.equals(k.name());
        Kit.Lit state = hidden ? Kit.Lit.NOT_FOUND : lit ? Kit.Lit.FOCUS : switch (t.state()) {
            case HearthView.Tile.BUILT -> Kit.Lit.YOURS;
            case HearthView.Tile.STARTED -> Kit.Lit.WAITS;
            default -> Kit.Lit.KNOWN;
        };
        Kit.well(s, x, y, x + TILE_W, y + TILE_H, state, over);
        if (hidden) {
            Kit.unknown(s, x + TILE_W / 2F, y + TILE_H / 2F, 15);
            return;
        }
        s.item(icon(k), x + TILE_W / 2F - 26, y + 10, 52);
        if (t.state() == HearthView.Tile.BUILT) s.text("✓", x + TILE_W - 20, y + 6, Sheet.LABEL, Glass.a(Glass.GOOD, 1F));
        else if (t.state() == HearthView.Tile.STARTED) Kit.pips(s, x + 8, y + 8, Integer.bitCount(t.have()), k.furniture.size());
        s.linesCentred(k.label, x + TILE_W / 2F, y + TILE_H + 6, TILE_STEP - 8, Sheet.LABEL,
                Glass.a(t.state() == HearthView.Tile.OPEN && !lit && !over ? s.label : Glass.TITLE, 1F));
        if (over) point(What.ROOM, k.name());
        hot.add(x, y, x + TILE_W, y + TILE_H + 22, () -> focus(What.ROOM, k.name()));
    }

    /** A beast or a kind as itself in a well, its name under it; an unmet kind is a dark shape with no name. Returns the height used. */
    private float creature(Sheet s, Hot hot, @Nullable EntityType<?> body, String name, Kit.Lit state, What what, String id, float x, float y, float mx, float my) {
        boolean over = Sheet.in(mx, my, x, y, x + TILE_W, y + TILE_H), lit = focus == what && on.equals(id);
        Kit.well(s, x, y, x + TILE_W, y + TILE_H, lit ? Kit.Lit.FOCUS : state, over);
        if (body == null || !Kit.creature(s, body, x + 8, y + 4, x + TILE_W - 8, y + TILE_H - 4, over || lit ? .1F : .55F, 0F)) Kit.unknown(s, x + TILE_W / 2F, y + TILE_H / 2F, 15);
        float h = TILE_H + 6;
        if (!name.isEmpty()) h = s.linesCentred(name, x + TILE_W / 2F, y + TILE_H + 6, TILE_STEP - 8, Sheet.LABEL, Glass.a(Glass.TITLE, 1F)) - y;
        if (over) point(what, id);
        hot.add(x, y, x + TILE_W, y + h, () -> focus(what, id));
        return h;
    }

    // ---- the panel ---------------------------------------------------------------------------------------------------

    private float panel(Sheet s, Hot hot, HearthView v, @Nullable GuideData d, What w, String id, float y0, float mx, float my, float dt) {
        float x = PANEL + 24;
        return switch (w) {
            case ROOM -> roomPanel(s, v, d, id, x, y0, mx, my);
            case LOOK -> lookPanel(s, v, id, x, y0);
            case BEAST -> beastPanel(s, hot, id, x, y0, mx, my);
            case KIN -> kinPanel(s, hot, id, x, y0, mx, my);
            case PERSON -> personPanel(s, d, id, x, y0);
            case WANT -> wantPanel(s, hot, id, x, y0, mx, my);
            default -> homePanel(s, hot, v, d, x, y0, mx, my, dt);
        };
    }

    /** With nothing in focus: the home in numbers, what would add to it, and the next tier with what it takes. */
    private float homePanel(Sheet s, Hot hot, HearthView v, @Nullable GuideData d, float x, float y, float mx, float my, float dt) {
        s.caps(v.tier() <= 0 ? "Your fire" : "Your home", x, y, Glass.a(s.label, 1F));
        y += 28;
        if (d == null) {
            y = s.lines("Reading your home…", x, y, PW, Sheet.BODY, Glass.a(s.label, 1F));
            hold = 0;
            return y;
        }
        float nx = x;
        nx += Kit.number(s, Integer.toString(d.radius()), "blocks around", nx, y, 4F, Glass.TITLE) + 30;
        nx += Kit.number(s, d.rest() + " s", "rest heals", nx, y, 4F, Glass.TITLE) + 30;
        Kit.number(s, d.home().restedMinutes() + " min", "rested", nx, y, 4F, Glass.TITLE);
        y += 8 * s.snap(4F) + 6 + s.line(Sheet.LABEL) + 12;
        String at = d.tier() <= 0 ? "the fire" : "the Anchor";
        y = s.lines(d.respawnHere() ? "If you fall, you wake here." : "If you fall, you wake at the bed you chose last. Use " + at + " to wake here again.",
                x, y, PW, Sheet.BODY, Glass.a(Glass.TEXT, 1F)) + 6;
        if (!d.home().comfortNext().isEmpty()) y = s.lines("A point of Comfort more: " + d.home().comfortNext() + ".", x, y, PW, Sheet.BODY, Glass.a(Glass.AMBER, 1F)) + 6;
        if (!d.home().style().isEmpty()) y = s.lines(cap(d.home().style()) + ".", x, y, PW, Sheet.BODY, Glass.a(s.label, 1F)) + 6;
        if (!d.home().land().isEmpty()) y = s.lines("The land: " + String.join(", ", d.home().land()) + ".", x, y, PW, Sheet.BODY, Glass.a(s.label, 1F)) + 6;
        if (v.trophies() > 0 || v.records() > 0) y = s.lines("Trophies " + v.trophies() + " · Records " + v.records(), x, y, PW, Sheet.BODY, Glass.a(s.label, 1F)) + 6;
        y += 10;

        // Oren's cottage, while it's on offer
        int cottage = d.cottage();
        if (cottage != GuideData.COTTAGE_NONE && cottage != GuideData.COTTAGE_BUILT) {
            Kit.hair(s, x, X1, y);
            y += 14;
            s.caps("Oren's cottage", x, y, Glass.a(Glass.LANTERN, 1F));
            y += 26;
            String say = switch (cottage) {
                case GuideData.COTTAGE_BUILDING -> d.cottageNote().isEmpty() ? "Oren is looking for a good spot beside your Anchor." : d.cottageNote();
                case GuideData.COTTAGE_NO_ROOM -> "Oren found no open ground for it. Clear a flat patch about 12 blocks across near the Anchor, away from your builds, then try again.";
                default -> "Oren can raise a small cottage beside your Anchor: a bed and a Meditation Mat, your first Sanctuary. Free, this once.";
            };
            y = s.lines(say, x, y, PW, Sheet.BODY, Glass.a(Glass.TEXT, 1F)) + 8;
            if (cottage != GuideData.COTTAGE_BUILDING) {
                if (anchor) {
                    String label = cottage == GuideData.COTTAGE_NO_ROOM ? "Try again" : "Raise it";
                    float bw = Kit.buttonWidth(s, "", label);
                    boolean over = Sheet.in(mx, my, x, y, x + bw, y + Kit.BUTTON_H);
                    Kit.button(s, "", label, x, y, Kit.Look.PLAIN, over, true, 0F);
                    hot.add(x, y, x + bw, y + Kit.BUTTON_H, () -> GuideClient.send(Guide.Act.COTTAGE));
                    y += Kit.BUTTON_H + 10;
                } else {
                    y = s.lines("Say yes at your Anchor.", x, y, PW, Sheet.LABEL, Glass.a(s.label, 1F)) + 8;
                }
            }
            y += 6;
        }

        // the next tier
        GuideData.Upgrade u = d.upgrade();
        Kit.hair(s, x, X1, y);
        y += 14;
        if (u.maxed()) {
            hold = 0;
            return s.lines("Your Hearth is complete.", x, y, PW, Sheet.BODY, Glass.a(Glass.GOOD, 1F));
        }
        s.caps(d.tier() <= 0 ? "Next · the Anchor" : "Next · Tier " + ROMAN[Math.clamp(u.next(), 0, 5)] + " · " + TierRules.name(u.next()), x, y, Glass.a(Glass.LANTERN, 1F));
        y += 26;
        y = s.lines(d.tier() <= 0 ? "A Lord's Heart on the fire. Radius " + u.radius() + ", and people come." : "Radius " + u.radius() + ", rest heals in " + u.rest() + " s.",
                x, y, PW, Sheet.BODY, Glass.a(Glass.TEXT, 1F)) + 8;
        if (!u.rankMet()) y = Kit.conditionLines(s, "Reach " + u.rank(), false, x, y, PW);
        for (GuideData.Need n : u.needs()) y = Kit.conditionLines(s, cap(n.text()), n.met(), x, y, PW);
        y += 4;
        float px = x, rowH = 0;
        for (GuideData.Part p : u.parts()) {
            ItemStack stack = GuideSections.stack(p.item());
            String name = stack.getHoverName().getString();
            if (px > x && px + Kit.NEED_W > x + PW) {
                px = x;
                y += rowH + 8;
                rowH = 0;
            }
            if (Kit.need(s, stack, name, p.have(), p.need(), px, y, mx, my)) {
                String where = dev.awaken.tasks.client.WhereFrom.line(p.item().toString());
                tipTitle = name;
                tipMore = where.isEmpty() ? List.of() : List.of(where);
            }
            rowH = Math.max(rowH, Kit.needHeight(s, name, p.need()));
            px += Kit.NEED_W;
        }
        if (!u.parts().isEmpty()) y += rowH + 8;
        if (u.discount() > 0) y = s.lines("Your rank takes " + u.discount() + "% off.", x, y, PW, Sheet.LABEL, Glass.a(s.label, 1F)) + 8;
        if (!anchor) {
            hold = 0;
            return s.lines(d.tier() <= 0 ? "Feed the fire the Heart at your fire." : "Raise it at your Anchor.", x, y, PW, Sheet.BODY, Glass.a(s.label, 1F));
        }
        boolean can = u.rankMet() && u.affordable();
        String label = can ? (d.tier() <= 0 ? "Hold to feed the fire the Heart" : "Hold to raise Tier " + ROMAN[Math.clamp(u.next(), 0, 5)])
                : !u.rankMet() ? "Opens at " + u.rank() : d.tier() <= 0 ? "Bring a Lord's Heart" : !u.homeReady() ? "The home grows first" : "Gather the materials first";
        float bw = Kit.buttonWidth(s, can ? "Enter" : "", label);
        boolean over = Sheet.in(mx, my, x, y, x + bw, y + Kit.BUTTON_H);
        boolean holding = can && (holdKey || holdMouse && over);
        hold = holding ? hold + dt / HOLD : 0;
        if (hold >= 1F) {
            hold = 0;
            holdKey = holdMouse = false;
            GuideClient.send(Guide.Act.UPGRADE);
            StatusSections.cue(StatusSections.Cue.PAGE);
        }
        Kit.button(s, can ? "Enter" : "", label, x, y, Kit.Look.PRIMARY, over, can, hold);
        if (can) hot.add(x, y, x + bw, y + Kit.BUTTON_H, () -> holdMouse = true);
        return y + Kit.BUTTON_H + 8;
    }

    /** A room kind: what it does, its pieces as real things, how to make the one you're missing, and why it isn't one yet. */
    private float roomPanel(Sheet s, HearthView v, @Nullable GuideData d, String id, float x, float y, float mx, float my) {
        RoomType k = null;
        HearthView.Tile t = null;
        for (HearthView.Tile tile : v.tiles()) if (tile.kind().equals(id)) {
            t = tile;
            k = kind(tile);
        }
        if (k == null || t == null) return y;
        boolean built = t.state() == HearthView.Tile.BUILT, started = t.state() == HearthView.Tile.STARTED;
        GuideData.RoomCard card = d == null ? null : d.card(k.ordinal());
        int count = card == null ? 0 : card.built().size();
        s.caps(built ? (count > 1 ? "Built ×" + count : "Built") : started ? "Started · " + Integer.bitCount(t.have()) + " of " + k.furniture.size()
                : k == RoomType.SANCTUARY ? "Optional" : "Open to build", x, y, Glass.a(built ? Glass.GOOD : started ? Glass.AMBER : s.label, 1F));
        y += 28;
        y = s.lines(k.label, x, y, PW, Sheet.TITLE, Glass.a(Glass.TITLE, 1F)) + 6;
        y = s.lines(cap(k.does), x, y, PW, Sheet.BODY, Glass.a(Glass.TEXT, 1F)) + 14;
        float px = x, rowH = 0;
        Furniture missing = null;
        for (Furniture f : k.furniture) {
            boolean has = built || (t.have() & f.bit()) != 0;
            if (!has && missing == null) missing = f;
            String name = cap(f.label);
            if (px > x && px + Kit.NEED_W > x + PW) {
                px = x;
                y += rowH + 8;
                rowH = 0;
            }
            if (Kit.need(s, f.icon(), name, has ? 1 : 0, 1, px, y, mx, my)) {
                String where = dev.awaken.tasks.client.WhereFrom.line(BuiltInRegistries.ITEM.getKey(f.icon().getItem()).toString());
                tipTitle = name;
                tipMore = where.isEmpty() ? List.of() : List.of(where);
            }
            rowH = Math.max(rowH, Kit.needHeight(s, name, 1));
            px += Kit.NEED_W;
        }
        if (!k.furniture.isEmpty()) y += rowH + 8;
        if (k.condition != null) y = Kit.conditionLines(s, cap(k.condition), built, x, y, PW);
        y = Kit.conditionLines(s, "All of it in one closed room: walls, a roof and a door", built || t.closed() && t.have() != 0, x, y, PW);
        if (card != null && !built && !card.room().isEmpty()) {
            y = s.lines("Ticked: what's in " + card.room() + ". Add the rest to that same room."
                    + (card.roomKind().isEmpty() ? "" : " It stays your " + card.roomKind() + " too."), x, y, PW, Sheet.LABEL, Glass.a(Glass.GOOD, 1F)) + 8;
        }
        if (card != null && !card.why().isEmpty()) {
            y = s.lines(card.why() + (card.mark().isPresent() ? " The spot is marked with blue sparks." : ""), x, y, PW, Sheet.BODY, Glass.a(Glass.AMBER, 1F)) + 8;
        }
        if (card != null && card.byOren()) y = s.lines("Oren built this one. Build the rest yourself.", x, y, PW, Sheet.LABEL, Glass.a(Glass.GOOD, 1F)) + 8;
        if (!built && missing != null) {
            // how the first missing piece is made: the game's own recipe, when the server has read the home
            Optional<GuideData.RecipeView> rv = d == null ? Optional.empty() : d.recipe(missing.ordinal());
            if (rv.isPresent()) {
                GuideData.RecipeView r = rv.get();
                ItemStack made = GuideSections.stack(r.result());
                y += 6;
                s.caps("To make " + (r.count() > 1 ? r.count() + " × " : "") + made.getHoverName().getString(), x, y, Glass.a(s.label, 1F));
                y += 26;
                Minecraft mc = Minecraft.getInstance();
                long second = mc.level == null ? 0 : mc.level.getGameTime() / 20;
                List<ItemStack> cells = new ArrayList<>(r.slots().size());
                for (List<Identifier> opts : r.slots()) cells.add(opts.isEmpty() ? ItemStack.EMPTY : GuideSections.stack(opts.get((int) (second % opts.size()))));
                Kit.recipe(s, r.width(), r.height(), cells, made, x, y, 44, 110);
                y += Kit.recipeHeight(r.height(), 44, 110) + 10;
            }
            String where = dev.awaken.tasks.client.WhereFrom.line(BuiltInRegistries.ITEM.getKey(missing.icon().getItem()).toString());
            if (!where.isEmpty()) y = s.lines(where, x, y, PW, Sheet.LABEL, Glass.a(s.label, 1F)) + 8;
        }
        if (built) {
            for (int i = 0; i < v.looks().size(); i++) {
                HearthView.Look l = v.looks().get(i);
                if (!l.kinds().toLowerCase(Locale.ROOT).contains(k.label.toLowerCase(Locale.ROOT))) continue;
                y += 6;
                Kit.hair(s, x, X1, y);
                y = look(s, l, x, y + 14);
            }
        }
        return y;
    }

    private float lookPanel(Sheet s, HearthView v, String id, float x, float y) {
        int i;
        try {
            i = Integer.parseInt(id);
        } catch (NumberFormatException e) {
            return y;
        }
        if (i < 0 || i >= v.looks().size()) return y;
        return look(s, v.looks().get(i), x, y);
    }

    /** A room as the Hearth reads it: its grade, its name, six readings as pips with the weakest in amber, and the one thing that lifts it. */
    private float look(Sheet s, HearthView.Look l, float x, float y) {
        int grade = Math.clamp(l.grade(), 0, 3), rgb = grade >= 3 ? Glass.GOOD : grade == 2 ? Glass.EDGE : Glass.AMBER;
        float cw = Kit.chip(s, ItemStack.EMPTY, GRADES[grade], x, y, rgb);
        if (!l.kinds().isEmpty()) s.lines(l.kinds(), x + cw + 12, y + 8, PW - cw - 12, Sheet.LABEL, Glass.a(s.label, 1F));
        y += 40;
        y = s.lines(cap(l.name()), x, y, PW, Sheet.TITLE, Glass.a(Glass.TITLE, 1F)) + 12;
        int weakest = 3;
        for (int r : l.readings()) weakest = Math.min(weakest, r);
        for (int i = 0; i < READINGS.length && i < l.readings().size(); i++) {
            int val = Math.clamp(l.readings().get(i), 0, 3);
            boolean low = val == weakest && val < 3;
            float rx = x + (i % 2) * 220, ry = y + (i / 2) * 30;
            s.text(READINGS[i], rx, ry, Sheet.BODY, Glass.a(low ? Glass.AMBER : s.label, 1F));
            Kit.pips(s, rx + 130, ry + 3, val, 3, 14, low ? Glass.AMBER : Glass.GOOD);
        }
        y += ((Math.min(READINGS.length, l.readings().size()) + 1) / 2) * 30 + 8;
        if (!l.lift().isEmpty() && grade < 3) y = s.lines(cap(l.lift()), x, y, PW, Sheet.BODY, Glass.a(Glass.AMBER, 1F)) + 6;
        return y;
    }

    private float beastPanel(Sheet s, Hot hot, String id, float x, float y, float mx, float my) {
        for (KinStrip.Beast b : KinStrip.beasts()) {
            if (!(b.id().isEmpty() ? b.kind() : b.id()).equals(id)) continue;
            s.caps("Lives with you", x, y, Glass.a(Glass.GOOD, 1F));
            y += 28;
            y = s.lines(b.name().isEmpty() ? b.title() : b.name(), x, y, PW, Sheet.TITLE, Glass.a(Glass.TITLE, 1F)) + 2;
            if (!b.name().isEmpty()) y = s.lines(b.title(), x, y, PW, Sheet.LABEL, Glass.a(s.label, 1F)) + 2;
            EntityType<?> body = KinStrip.body(b.kind());
            Kit.floor(s, x + PW / 2F, y + 176, 150);
            if (body != null) Kit.creature(s, body, x + 60, y + 6, x + PW - 60, y + 190, (x + PW / 2F - mx) / 400F, 0F);
            y += 206;
            y = s.lines("Now: " + b.doing() + ".", x, y, PW, Sheet.BODY, Glass.a(Glass.TEXT, 1F)) + 6;
            y = s.lines(cap(b.feeling()) + ".", x, y, PW, Sheet.BODY, Glass.a(s.label, 1F)) + 6;
            if (!b.made().isEmpty()) y = s.lines(cap(b.made()) + ".", x, y, PW, Sheet.BODY, Glass.a(Glass.AMBER, 1F)) + 6;
            if (!b.id().isEmpty()) {
                String label = b.name().isEmpty() ? "Give it a name" : "Change its name";
                float bw = Kit.buttonWidth(s, "", label);
                y += 6;
                Kit.button(s, "", label, x, y, Kit.Look.PLAIN, Sheet.in(mx, my, x, y, x + bw, y + Kit.BUTTON_H), true, 0F);
                hot.add(x, y, x + bw, y + Kit.BUTTON_H, () -> KinStrip.name(b));
                y += Kit.BUTTON_H + 8;
            }
            return y;
        }
        return y;
    }

    private float kinPanel(Sheet s, Hot hot, String id, float x, float y, float mx, float my) {
        for (KinStrip.Kind k : KinStrip.kinds()) {
            if (!k.id().equals(id)) continue;
            s.caps(k.met() ? "A kind you've met" : "Heard of", x, y, Glass.a(k.met() ? k.colour() : s.label, 1F));
            y += 28;
            if (k.met()) {
                y = s.lines(k.title(), x, y, PW, Sheet.TITLE, Glass.a(Glass.TITLE, 1F)) + 2;
                EntityType<?> body = KinStrip.body(k.id());
                Kit.floor(s, x + PW / 2F, y + 156, 150);
                if (body != null) Kit.creature(s, body, x + 70, y + 6, x + PW - 70, y + 170, (x + PW / 2F - mx) / 400F, 0F);
                y += 186;
            }
            y = s.lines(k.where(), x, y, PW, Sheet.BODY, Glass.a(s.label, 1F)) + 6;
            y = s.lines(k.job(), x, y, PW, Sheet.BODY, Glass.a(Glass.TEXT, 1F)) + 6;
            if (k.met()) {
                s.caps("What it wants", x, y + 4, Glass.a(s.label, 1F));
                y += 30;
                for (String w : k.wants().split("\n")) if (!w.isBlank()) y = s.lines(w.equals("?") ? "You don't know yet. Watch one." : w, x, y, PW, Sheet.BODY, Glass.a(Glass.TEXT, 1F)) + 4;
                y = s.lines("You and its kind: " + k.standing(), x, y + 4, PW, Sheet.BODY, Glass.a(Glass.AMBER, 1F)) + 6;
                if (!k.line().isEmpty()) y = s.lines(k.line(), x, y, PW, Sheet.LABEL, Glass.a(s.label, 1F)) + 6;
            }
            if (!k.rumour().isEmpty()) {
                y = s.lines(k.rumour(), x, y, PW, Sheet.BODY, Glass.a(s.label, 1F)) + 10;
                y = track(s, hot, k.id(), x, y, mx, my);
            }
            return y;
        }
        return y;
    }

    private float personPanel(Sheet s, @Nullable GuideData d, String id, float x, float y) {
        if (d == null) return y;
        for (int pass = 0; pass < 2; pass++) for (GuideData.Person p : pass == 0 ? d.town() : d.coming()) {
            if (!p.name().equals(id)) continue;
            s.caps(pass == 0 ? "Lives here" : "Could come", x, y, Glass.a(pass == 0 ? Glass.LANTERN : s.label, 1F));
            y += 28;
            y = s.lines(p.name(), x, y, PW, Sheet.TITLE, Glass.a(Glass.TITLE, 1F)) + 4;
            y = s.lines(cap(p.role()), x, y, PW, Sheet.BODY, Glass.a(Glass.TEXT, 1F)) + 6;
            if (!p.when().isBlank()) y = s.lines(cap(p.when().strip()) + ".", x, y, PW, Sheet.BODY, Glass.a(p.when().startsWith("needs") ? Glass.AMBER : s.label, 1F)) + 6;
            if (pass == 0) y = s.lines("Talk to them: commissions at the forge, tonics, the mine's haul, rumours, your Codex, mending.", x, y + 6, PW, Sheet.LABEL, Glass.a(s.label, 1F));
            return y;
        }
        return y;
    }

    private float wantPanel(Sheet s, Hot hot, String id, float x, float y, float mx, float my) {
        for (KinStrip.Want w : KinStrip.wants()) {
            if (!w.text().equals(id)) continue;
            s.caps("Your home asks for", x, y, Glass.a(Glass.AMBER, 1F));
            y += 28;
            y = s.lines(w.text(), x, y, PW, Sheet.BODY, Glass.a(Glass.TITLE, 1F)) + 8;
            if (!w.rumour().isEmpty()) y = s.lines(w.rumour(), x, y, PW, Sheet.BODY, Glass.a(s.label, 1F)) + 10;
            if (!w.kind().isEmpty()) y = track(s, hot, w.kind(), x, y, mx, my);
            return y;
        }
        return y;
    }

    private float track(Sheet s, Hot hot, String kind, float x, float y, float mx, float my) {
        float bw = Kit.buttonWidth(s, "T", "Track");
        Kit.button(s, "T", "Track", x, y, Kit.Look.PRIMARY, Sheet.in(mx, my, x, y, x + bw, y + Kit.BUTTON_H), true, 0F);
        hot.add(x, y, x + bw, y + Kit.BUTTON_H, () -> {
            KinStrip.track(kind);
            StatusSections.cue(StatusSections.Cue.PAGE);
        });
        return y + Kit.BUTTON_H + 8;
    }

    // ---- focus, keys -------------------------------------------------------------------------------------------------

    private void point(What w, String id) {
        pointed = w;
        pointedOn = id;
    }

    private void focus(What w, String id) {
        if (w == focus && id.equals(on) && w != What.HOME) {
            w = What.HOME;
            id = "";
        }
        if (w != focus || !id.equals(on)) StatusSections.cue(StatusSections.Cue.PAGE);
        focus = w;
        on = id;
    }

    /** The kind the T key would track now: a kind with a rumour, or a want with a lead; "" for none. */
    private String trackable() {
        if (focus == What.KIN) for (KinStrip.Kind k : KinStrip.kinds()) if (k.id().equals(on) && !k.rumour().isEmpty()) return k.id();
        if (focus == What.WANT) for (KinStrip.Want w : KinStrip.wants()) if (w.text().equals(on)) return w.kind();
        return "";
    }

    private boolean canRaise() {
        GuideData d = GuideClient.data();
        return anchor && focus == What.HOME && d != null && !d.upgrade().maxed() && d.upgrade().rankMet() && d.upgrade().affordable();
    }

    @Override
    public void hints(List<Kit.Hint> out) {
        if (!HomePage.view().has()) return;
        out.add(Kit.Hint.of("Next room", "Tab"));
        if (!trackable().isEmpty()) out.add(Kit.Hint.of("Track", "T"));
        if (canRaise()) out.add(Kit.Hint.hold("Raise the tier", "Enter"));
        if (leftTall > Y1 - Y0 || panelTall > Y1 - Y0) out.add(Kit.Hint.of("More", "Scroll"));
    }

    @Override
    public boolean key(KeyEvent e) {
        int k = e.key();
        if (k == InputConstants.KEY_T && !trackable().isEmpty()) {
            KinStrip.track(trackable());
            StatusSections.cue(StatusSections.Cue.PAGE);
            return true;
        }
        if ((k == InputConstants.KEY_RETURN || k == InputConstants.KEY_NUMPADENTER) && canRaise()) {
            holdKey = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyReleased(KeyEvent e) {
        if (e.key() == InputConstants.KEY_RETURN || e.key() == InputConstants.KEY_NUMPADENTER) holdKey = false;
        return false;
    }

    @Override
    public void released() {
        holdMouse = false;
    }

    /** Tab walks the room kinds you know. */
    @Override
    public void next(int step) {
        List<String> known = new ArrayList<>();
        for (HearthView.Tile t : HomePage.view().tiles()) if (kind(t) != null && t.state() != HearthView.Tile.HIDDEN) known.add(t.kind());
        if (known.isEmpty()) return;
        int at = focus == What.ROOM ? known.indexOf(on) : -1;
        String to = known.get(Math.floorMod(at < 0 ? (step > 0 ? 0 : -1) : at + step, known.size()));
        focus = What.ROOM;
        on = to;
        RoomType k = RoomType.valueOf(to);
        StatusSections.cue(StatusSections.Cue.PAGE);
        StatusSections.narrate(k.label + ". " + k.does);
    }

    @Override
    public boolean back() {
        if (focus == What.HOME) return false;
        focus = What.HOME;
        on = "";
        return true;
    }

    @Override
    public String backWords() {
        return focus == What.HOME ? "Close" : "Back";
    }

    @Override
    public boolean scroll(float mx, float my, double amount) {
        if (mx >= PANEL) panelScroll -= (float) amount * 40;
        else leftScroll -= (float) amount * 40;
        return true;
    }

    // ---- small things ------------------------------------------------------------------------------------------------

    private static @Nullable RoomType kind(HearthView.Tile t) {
        try {
            return RoomType.valueOf(t.kind());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static ItemStack icon(RoomType kind) {
        return kind.furniture.isEmpty() ? new ItemStack(Items.OAK_DOOR) : kind.furniture.getLast().icon();
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
