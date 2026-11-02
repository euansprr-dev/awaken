package dev.awaken.hearth.client;

import dev.awaken.hearth.Comfort;
import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.TierRules;
import dev.awaken.hearth.player.HearthView;
import dev.awaken.hearth.room.Furniture;
import dev.awaken.hearth.room.RoomType;
import dev.awaken.progression.client.StatusSections;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.Ui;
import dev.awaken.signal.client.UiSection;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The band's Hearth page at a glance (SPEC §27.13; Euan 2026-10-30: "too much text ... make it more clear what you need to
 * do to actually improve your hearth and make it look good"): your home's name, tier and Comfort as pips; every room kind
 * as a tile (built, started with its pieces counted, open, optional, or "?" for a kind whose rare piece you haven't come
 * across), a tile's pieces and where they come from on a click; and "Make it look good", each room's six readings and the
 * one thing that lifts it. The next step for the home sits between (the Tasks page's); your beasts and kin under.
 */
final class HomePage {
    private HomePage() {}

    static void register() {
        StatusSections.add(StatusSections.Tab.HEARTH, new Banner());
        StatusSections.add(StatusSections.Tab.HEARTH, new Rooms());
        StatusSections.add(StatusSections.Tab.HEARTH, new Looks());
    }

    static HearthView view() {
        var p = Minecraft.getInstance().player;
        return p == null ? HearthView.EMPTY : p.getData(HearthContent.VIEW.get());
    }

    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V"};
    private static final int DIM = 0x4A5A72;

    // ---- your home at a glance ---------------------------------------------------------------------------------------

    static final class Banner extends UiSection {
        @Override public String title() { return ""; }
        @Override public int order() { return -300; }
        @Override public StatusSections.@Nullable Shelf shelf() { return null; }

        @Override
        public boolean visible() {
            var p = Minecraft.getInstance().player;
            return p != null && (view().has() || dev.awaken.progression.Progression.profile(p).awakened());
        }

        @Override
        protected void build(Ui ui) {
            HearthView v = view();
            if (!v.has()) {
                ui.card(Glass.LANTERN, b -> {
                    b.text("No home yet", Glass.TITLE);
                    b.text("Ilse keeps the last fire up the north road. Carry a coal from it and set it down where you'd live: your home starts at that fire.",
                            Glass.LABEL);
                });
                return;
            }
            ui.card(Glass.LANTERN, b -> {
                Ui.Flow top = b.flow();
                top.chip(v.tier() <= 0 ? "HEARTHFIRE" : "TIER " + ROMAN[Math.max(0, Math.min(5, v.tier()))] + " · " + TierRules.name(v.tier()).toUpperCase(java.util.Locale.ROOT),
                        Glass.LANTERN);
                if (v.tier() > 0 && v.forgeTier() > 0) top.chip("FORGE " + ROMAN[Math.max(0, Math.min(5, v.forgeTier()))], Glass.AMBER);
                top.end();
                b.text(v.home().name().isEmpty() ? (v.tier() <= 0 ? "Your Hearthfire" : "Your home") : v.home().name(), Glass.TITLE);
                pips(b, "Comfort", v.home().comfort(), Comfort.MAX);
                long left = dev.awaken.hearth.Rested.left(Minecraft.getInstance().player);
                String style = v.home().style();
                String rest = left > 0 ? "Rested for " + Math.max(1, (left + 1199) / 1200) + " min" : "";
                if (!style.isEmpty() || !rest.isEmpty()) b.pair(style.isEmpty() ? "" : cap(style), rest, Glass.LABEL, Glass.AMBER);
                if (!v.town().isEmpty()) b.text("Living here: " + String.join(", ", v.town()), Glass.TEXT);
                if (v.trophies() > 0 || v.records() > 0) b.text("Trophies " + v.trophies() + " · Records " + v.records(), Glass.LABEL);
                Ui.Flow f = b.flow();
                if (f.button("Rooms and upgrades ›", Glass.LANTERN, true)) GuideClient.send(dev.awaken.hearth.guide.Guide.Act.OPEN);
                f.end();
            });
        }
    }

    /** "Comfort ●●●●○○○○ 4/12": a row of pips. */
    private static void pips(Ui b, String label, int have, int max) {
        int lw = b.font().width(label) + 6, step = 7, x = b.x + lw, y = b.y();
        b.textAt(label, b.x, y + 1, lw, Glass.a(Glass.LABEL, 1f), false);
        for (int i = 0; i < max; i++) {
            int px = x + i * step;
            b.fill(px, y + 2, px + 5, y + 7, i < have ? Glass.a(Glass.LANTERN, 1f) : Glass.a(DIM, 0.8f));
        }
        b.textAt(have + "/" + max, x + max * step + 4, y + 1, 40, Glass.a(Glass.LABEL, 0.9f), false);
        b.gap(Ui.LINE);
    }

    // ---- every room kind as a tile -----------------------------------------------------------------------------------

    /** The kind whose tile was clicked (its pieces show under the tiles), or null. */
    static @Nullable RoomType open;

    static final class Rooms extends UiSection {
        private static final int TILE_H = 30, GAP = 4;

        @Override public String title() { return "ROOMS"; }
        @Override public int order() { return -200; }
        @Override public StatusSections.@Nullable Shelf shelf() { return null; }
        @Override public boolean visible() { return view().has() && !view().tiles().isEmpty(); }

        @Override
        protected void build(Ui ui) {
            List<HearthView.Tile> tiles = view().tiles();
            int cols = Math.max(2, Math.min(4, ui.width / 70)), w = (ui.width - GAP * (cols - 1)) / cols;
            HearthView.Tile picked = null;
            for (int i = 0; i < tiles.size(); i += cols) {
                int y = ui.y();
                for (int c = 0; c < cols && i + c < tiles.size(); c++) {
                    HearthView.Tile t = tiles.get(i + c);
                    int x = ui.x + c * (w + GAP);
                    RoomType kind = kind(t);
                    if (kind != null && kind == open) picked = t;
                    tile(ui, t, kind, x, y, w);
                    if (!ui.drawing() && kind != null && ui.buttonAt("", x, y, w, TILE_H, Glass.EDGE, true)) {
                        open = open == kind ? null : kind;
                        StatusSections.cue(StatusSections.Cue.PAGE);
                    }
                }
                ui.gap(TILE_H + GAP);
            }
            if (picked != null) detail(ui, picked, open);
        }

        private static void tile(Ui ui, HearthView.Tile t, @Nullable RoomType kind, int x, int y, int w) {
            if (!ui.drawing()) return;
            boolean hidden = t.state() == HearthView.Tile.HIDDEN || kind == null, optional = kind == RoomType.SANCTUARY;
            int accent = switch (t.state()) {
                case HearthView.Tile.BUILT -> Glass.GOOD;
                case HearthView.Tile.STARTED -> Glass.AMBER;
                case HearthView.Tile.OPEN -> Glass.EDGE;
                default -> DIM;
            };
            boolean lit = kind != null && kind == open, hover = ui.hover(x, y, w, TILE_H);
            ui.fill(x, y, x + w, y + TILE_H, Glass.a(t.state() == HearthView.Tile.BUILT ? 0x0E2A1E : 0x05080F, lit ? 0.85f : hover ? 0.7f : 0.5f));
            int edge = Glass.a(accent, lit ? 1f : t.state() == HearthView.Tile.OPEN ? 0.45f : 0.8f);
            if (optional && t.state() != HearthView.Tile.BUILT) dashed(ui, x, y, w, TILE_H, edge);
            else ui.canvas().outline(x, y, w, TILE_H, edge);
            if (hidden) {
                ui.textAt("?", x + w / 2 - 2, y + 11, 10, Glass.a(Glass.LABEL, 0.6f), false);
                return;
            }
            ui.itemAt(icon(kind), x + 4, y + 4, Ui.ICON);
            ui.textAt(kind.label, x + 4 + Ui.ICON + 3, y + 5, w - Ui.ICON - 10, Glass.a(t.state() == HearthView.Tile.OPEN ? Glass.TEXT : Glass.TITLE, 1f), false);
            String line = switch (t.state()) {
                case HearthView.Tile.BUILT -> "✓ " + kind.gives;
                case HearthView.Tile.STARTED -> Integer.bitCount(t.have()) + "/" + kind.furniture.size() + " · " + kind.gives;
                default -> optional ? "optional · " + kind.gives : kind.gives;
            };
            ui.textAt(line, x + 4, y + 18, w - 8, Glass.a(t.state() == HearthView.Tile.BUILT ? Glass.GOOD : Glass.LABEL, 0.9f), false);
        }

        private static void dashed(Ui ui, int x, int y, int w, int h, int argb) {
            for (int i = 0; i < w; i += 4) {
                ui.fill(x + i, y, x + Math.min(w, i + 2), y + 1, argb);
                ui.fill(x + i, y + h - 1, x + Math.min(w, i + 2), y + h, argb);
            }
            for (int i = 0; i < h; i += 4) {
                ui.fill(x, y + i, x + 1, y + Math.min(h, i + 2), argb);
                ui.fill(x + w - 1, y + i, x + w, y + Math.min(h, i + 2), argb);
            }
        }

        /** A clicked tile: what the room does, its pieces ticked where they stand together, and where each missing one comes from. */
        private static void detail(Ui ui, HearthView.Tile t, RoomType kind) {
            ui.card(t.state() == HearthView.Tile.BUILT ? Glass.GOOD : Glass.EDGE, b -> {
                b.text(kind.label + (t.state() == HearthView.Tile.BUILT ? "  ✓ built" : ""), Glass.TITLE);
                b.text(kind.does, Glass.LABEL);
                if (t.state() == HearthView.Tile.BUILT) return;
                for (Furniture f : kind.furniture) {
                    boolean has = (t.have() & f.bit()) != 0;
                    Ui.Flow row = b.flow();
                    row.item(f.icon(), (has ? "✓ " : "") + cap(f.label), has ? Glass.GOOD : Glass.TEXT);
                    row.end();
                    if (has) continue;
                    String where = dev.awaken.tasks.client.WhereFrom.line(BuiltInRegistries.ITEM.getKey(f.icon().getItem()).toString());
                    if (!where.isEmpty()) b.text(where, Glass.a(Glass.LABEL, 0.8f), Ui.ICON + 3, false);
                }
                if (kind.condition != null) b.text("· " + cap(kind.condition), Glass.TEXT);
                boolean closed = t.closed() && t.have() != 0;
                b.text((closed ? "✓ " : "· ") + "All of it in one closed room: walls, a roof and a door", closed ? Glass.GOOD : Glass.TEXT);
            });
        }
    }

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

    // ---- make it look good -------------------------------------------------------------------------------------------

    private static final String[] READINGS = {"Closed", "Light", "Warmth", "Room", "Care", "Detail"};
    private static final String[] GRADES = {"BARE", "SNUG", "FINE", "GRAND"};

    static final class Looks extends UiSection {
        @Override public String title() { return "MAKE IT LOOK GOOD"; }
        @Override public int order() { return -150; }
        @Override public StatusSections.@Nullable Shelf shelf() { return null; }
        @Override public boolean visible() { return view().has() && !view().looks().isEmpty(); }

        @Override
        protected void build(Ui ui) {
            for (HearthView.Look l : view().looks()) {
                int grade = Math.max(0, Math.min(3, l.grade()));
                int accent = grade >= 3 ? Glass.GOOD : grade == 2 ? Glass.EDGE : Glass.AMBER;
                ui.card(accent, b -> {
                    Ui.Flow head = b.flow();
                    head.chip(GRADES[grade], accent);
                    if (!l.kinds().isEmpty()) head.text(l.kinds(), Glass.LABEL);
                    head.end();
                    b.text(cap(l.name()), Glass.TITLE);
                    readings(b, l.readings());
                    if (!l.lift().isEmpty() && grade < 3) b.text(cap(l.lift()), Glass.a(Glass.AMBER, 1f));
                });
                ui.gap(3);
            }
        }

        /** The six readings side by side, each a word and three pips; the weakest in amber. */
        private static void readings(Ui b, List<Integer> at) {
            int cell = b.width / 3, y = b.y(), weakest = 3;
            for (int v : at) weakest = Math.min(weakest, v);
            for (int i = 0; i < READINGS.length && i < at.size(); i++) {
                int x = b.x + (i % 3) * cell, ry = y + (i / 3) * 11, v = Math.max(0, Math.min(3, at.get(i)));
                boolean low = v == weakest && v < 3;
                b.textAt(READINGS[i], x, ry + 1, cell - 24, Glass.a(low ? Glass.AMBER : Glass.LABEL, 1f), false);
                for (int k = 0; k < 3; k++) {
                    int px = x + cell - 22 + k * 6;
                    b.fill(px, ry + 3, px + 4, ry + 7, k < v ? Glass.a(low ? Glass.AMBER : Glass.GOOD, 1f) : Glass.a(DIM, 0.8f));
                }
            }
            b.gap(23);
        }
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
