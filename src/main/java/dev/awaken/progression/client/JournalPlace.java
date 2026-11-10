package dev.awaken.progression.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.core.client.ui.BandPlaces;
import dev.awaken.core.client.ui.Hot;
import dev.awaken.core.client.ui.Kit;
import dev.awaken.core.client.ui.Sheet;
import dev.awaken.progression.client.StatusSections.Section;
import dev.awaken.progression.client.StatusSections.Shelf;
import dev.awaken.signal.client.Glass;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Journal on the band's stage (SPEC §57.8): one wall, every shelf on it. The categories were twelve chips that each
 * swapped the page; they are an index down the left that jumps the wall. A shelf with a fixed set (creatures, beasts, cave
 * regions, ores, enchantments) shows the things themselves: a found creature stands there as itself, a found thing as its
 * own item, and one you haven't found is a dark shape with no name, no number and no hint. Shelves that are still written
 * as sections (the gate worlds' paintings, the Bell Book, lore, happenings, your story) stand on the same wall as they
 * were. The panel reads the thing you point at.
 */
final class JournalPlace implements BandPlaces.Place {
    private static final float X0 = BandPlaces.X0, Y0 = BandPlaces.Y0, X1 = BandPlaces.X1, Y1 = BandPlaces.Y1;
    private static final float PANEL = X1 - 488, PW = 440, INDEX_W = 190, WX0 = X0 + INDEX_W + 22, WX1 = PANEL - 24;
    // a tile's name has the step's width: nine letters of a label fit ("Stonehorn" broke as "Stonehor / n" at 82)
    private static final float TILE = 76, STEP = 90, ROW = 54;
    private static final String[] EMBLEMS = {"minecraft:bone", "minecraft:lead", "minecraft:grass_block", "minecraft:filled_map", "minecraft:ender_eye",
            "minecraft:pointed_dripstone", "minecraft:bell", "minecraft:written_book", "minecraft:clock", "minecraft:raw_iron", "minecraft:enchanted_book",
            "minecraft:writable_book"};

    private final ItemStack[] emblems = new ItemStack[Shelf.values().length];
    private final float[] shelfY = new float[Shelf.values().length];
    private float scroll, tall, panelScroll, panelTall;
    private @Nullable Shelf jump, lastFilter;
    /** The slot the panel reads when clicked, and the one under the pointer. */
    private @Nullable Shelf onShelf, overShelf;
    private int onSlot = -1, overSlot = -1;
    private String panelFor = "";

    @Override
    public void opened(String... link) {
        // a link to the Journal names its category (StatusSections.follow): the wall opens there
        Shelf f = StatusSections.journalFilter();
        if (f != lastFilter || link.length > 0) jump = f;
        lastFilter = f;
        for (String l : link) for (Shelf sh : Shelf.values()) if (sh.label.equalsIgnoreCase(l) || sh.name().equalsIgnoreCase(l)) jump = sh;
    }

    private ItemStack emblem(Shelf sh) {
        ItemStack e = emblems[sh.ordinal()];
        if (e == null) {
            Identifier id = Identifier.tryParse(EMBLEMS[sh.ordinal()]);
            e = emblems[sh.ordinal()] = id != null && BuiltInRegistries.ITEM.containsKey(id) ? new ItemStack(BuiltInRegistries.ITEM.getValue(id)) : new ItemStack(Items.BOOK);
        }
        return e;
    }

    /** The old sections that stand on a shelf (not the category strip, the "?" grid or the empty line: the wall draws those itself). */
    private static List<Section> sections(Shelf shelf) {
        List<Section> out = new ArrayList<>();
        for (Section sec : StatusSections.of(StatusSections.Tab.JOURNAL)) {
            if (sec instanceof JournalFilters || sec instanceof Catalogue.Grid || sec instanceof Catalogue.Empty) continue;
            Shelf on = sec.shelf();
            if ((on == shelf || on == null && shelf == Shelf.STORY) && sec.visible()) out.add(sec);
        }
        return out;
    }

    @Override
    public void draw(Sheet s, Hot hot, float mx, float my, float dt) {
        overShelf = null;
        overSlot = -1;
        // the index: every shelf, its picture and how much of it you have
        Shelf at = Shelf.values()[0];
        for (Shelf sh : Shelf.values()) if (shelfY[sh.ordinal()] <= scroll + 8) at = sh;
        for (Shelf sh : Shelf.values()) {
            float y = Y0 + sh.ordinal() * ROW;
            boolean over = Sheet.in(mx, my, X0, y, X0 + INDEX_W, y + ROW - 4), here = sh == at;
            if (over || here) s.fill(X0, y, X0 + INDEX_W, y + ROW - 4, Glass.a(Glass.EDGE, here ? .14F : .08F));
            if (here) s.fill(X0, y, X0 + 3, y + ROW - 4, Glass.a(Glass.EDGE, 1F));
            s.item(emblem(sh), X0 + 10, y + 9, 32);
            String name = sh.label.charAt(0) + sh.label.substring(1).toLowerCase(java.util.Locale.ROOT);
            float ny = s.lines(name, X0 + 50, y + 6, INDEX_W - 56, Sheet.LABEL, Glass.a(here || over ? Glass.TITLE : Glass.TEXT, 1F));
            String tally = Catalogue.tally(sh);
            if (!tally.isEmpty()) s.text(tally.toLowerCase(java.util.Locale.ROOT), X0 + 50, Math.min(ny, y + 28), Sheet.LABEL, Glass.a(tally.endsWith("NEW") ? Glass.AMBER : s.label, 1F));
            hot.add(X0, y, X0 + INDEX_W, y + ROW - 4, () -> {
                jump = sh;
                StatusSections.journalFilter(sh);
                lastFilter = sh;
                StatusSections.cue(StatusSections.Cue.PAGE);
            });
        }
        s.fill(X0 + INDEX_W + 8, Y0, X0 + INDEX_W + 9, Y1, Glass.a(Glass.EDGE, .14F));

        // the wall
        if (jump != null && tall > 0) {
            scroll = shelfY[jump.ordinal()];
            jump = null;
        }
        scroll = Math.clamp(scroll, 0F, Math.max(0F, tall - (Y1 - Y0)));
        boolean in = Sheet.in(mx, my, WX0 - 6, Y0, WX1 + 6, Y1);
        s.g.enableScissor(Math.round(WX0) - 6, Math.round(Y0) - 4, Math.round(WX1) + 8, Math.round(Y1));
        hot.clip(WX0 - 6, Y0, WX1 + 6, Y1);
        float wmx = in ? mx : -10000, wmy = in ? my : -10000, y = Y0 - scroll;
        for (Shelf sh : Shelf.values()) {
            shelfY[sh.ordinal()] = y - (Y0 - scroll);
            y = shelf(s, hot, sh, y, wmx, wmy);
        }
        hot.unclip();
        s.g.disableScissor();
        // room under the last shelf, so the index can bring any shelf to the top
        tall = y - (Y0 - scroll) + Math.max(0F, (Y1 - Y0) - (y - (Y0 - scroll) - shelfY[Shelf.values().length - 1]));
        Kit.scrollbar(s, WX1 + 8, Y0, Y1, scroll, tall);

        // the panel: the thing you point at, else the one you picked
        s.fill(PANEL, Y0, PANEL + 1, Y1, Glass.a(Glass.EDGE, .2F));
        Shelf ps = overShelf != null ? overShelf : onShelf;
        int pi = overShelf != null ? overSlot : onSlot;
        String key = ps + "|" + pi;
        if (!key.equals(panelFor)) {
            panelFor = key;
            panelScroll = 0;
        }
        panelScroll = Math.clamp(panelScroll, 0F, Math.max(0F, panelTall - (Y1 - Y0)));
        s.g.enableScissor(Math.round(PANEL) + 2, Math.round(Y0) - 8, Math.round(X1) + 8, Math.round(Y1));
        float pend = panel(s, ps, pi, PANEL + 24, Y0 - panelScroll, mx);
        s.g.disableScissor();
        panelTall = pend - (Y0 - panelScroll);
        Kit.scrollbar(s, X1 + 10, Y0, Y1, panelScroll, panelTall);
    }

    /** One shelf from y down: its name and count, its things, and whatever is still written as sections. Returns the y under it. */
    private float shelf(Sheet s, Hot hot, Shelf sh, float y, float mx, float my) {
        String tally = Catalogue.tally(sh);
        s.caps(sh.label + (tally.isEmpty() ? "" : " · " + tally), WX0, y + 4, Glass.a(s.label, 1F));
        Kit.hair(s, WX0, WX1, y + 24);
        y += 36;
        List<Catalogue.Slot> slots = Catalogue.slots(sh);
        boolean any = false;
        if (slots != null && !slots.isEmpty()) {
            any = true;
            int per = Math.max(1, (int) ((WX1 - WX0 + (STEP - TILE)) / STEP));
            for (int i = 0; i < slots.size(); i += per) {
                float rowH = TILE;
                for (int j = i; j < Math.min(slots.size(), i + per); j++) {
                    Catalogue.Slot slot = slots.get(j);
                    if (slot.found() && !bare(slot)) rowH = Math.max(rowH, TILE + 6 + s.tall(slot.name(), STEP - 6, Sheet.LABEL));
                }
                if (y + rowH >= Y0 - 8 && y <= Y1) for (int j = i; j < Math.min(slots.size(), i + per); j++) tile(s, hot, sh, j, slots.get(j), WX0 + (j - i) * STEP, y, mx, my);
                y += rowH + 12;
            }
        }
        List<Section> secs = sections(sh);
        if (!secs.isEmpty()) {
            any = true;
            y = SectionHost.flat(s, hot, secs, WX0, y, WX1, Y0, Y1, mx, my, Sheet.calm()) + 6;
        }
        if (!any) y = s.lines(Catalogue.keeps(sh), WX0, y, WX1 - WX0, Sheet.BODY, Glass.a(s.dim, 1F)) + 6;
        return y + 22;
    }

    /** A found thing with no picture of its own (a cave region): its name is the tile. */
    private static boolean bare(Catalogue.Slot slot) {
        return slot.icon().isEmpty() && slot.creature() == null;
    }

    private void tile(Sheet s, Hot hot, Shelf sh, int n, Catalogue.Slot slot, float x, float y, float mx, float my) {
        if (!slot.found()) { // not found: a dark shape, and nothing else
            Kit.well(s, x, y, x + TILE, y + TILE, Kit.Lit.NOT_FOUND, false);
            Kit.unknown(s, x + TILE / 2F, y + TILE / 2F, 13);
            return;
        }
        boolean over = Sheet.in(mx, my, x, y, x + TILE, y + TILE + 20), lit = onShelf == sh && onSlot == n;
        Kit.well(s, x, y, x + TILE, y + TILE, lit ? Kit.Lit.FOCUS : Kit.Lit.KNOWN, over);
        if (bare(slot)) {
            List<net.minecraft.util.FormattedCharSequence> lines = s.wrap(slot.name(), TILE - 8, Sheet.LABEL);
            float ty = y + Math.max(4, (TILE - lines.size() * s.line(Sheet.LABEL)) / 2F);
            s.linesCentred(slot.name(), x + TILE / 2F, ty, TILE - 8, Sheet.LABEL, Glass.a(Glass.TITLE, 1F));
        } else {
            boolean drawn = slot.creature() != null && y >= Y0 - 4 && y + TILE <= Y1 + 4
                    && Kit.creature(s, slot.creature(), x + 4, y + 4, x + TILE - 4, y + TILE - 4, over || lit ? .1F : .55F, 0F);
            if (!drawn && !slot.icon().isEmpty()) s.item(slot.icon(), x + 12, y + 12, TILE - 24);
            s.linesCentred(slot.name(), x + TILE / 2F, y + TILE + 6, STEP - 6, Sheet.LABEL, Glass.a(over || lit ? Glass.TITLE : Glass.TEXT, 1F));
        }
        if (over) {
            overShelf = sh;
            overSlot = n;
        }
        hot.add(x, y, x + TILE, y + TILE + 20, () -> {
            boolean same = onShelf == sh && onSlot == n;
            onShelf = same ? null : sh;
            onSlot = same ? -1 : n;
            StatusSections.cue(StatusSections.Cue.PAGE);
        });
    }

    private float panel(Sheet s, @Nullable Shelf sh, int index, float x, float y, float mx) {
        List<Catalogue.Slot> slots = sh == null ? null : Catalogue.slots(sh);
        if (slots == null || index < 0 || index >= slots.size() || !slots.get(index).found()) {
            // at rest: what the Journal is, and how much of the fixed shelves you have
            s.caps("Your journal", x, y, Glass.a(s.label, 1F));
            y += 28;
            y = s.lines("Everything you've found, kept as itself. What you haven't found stays dark.", x, y, PW, Sheet.BODY, Glass.a(Glass.TEXT, 1F)) + 16;
            for (Shelf each : Shelf.values()) {
                int[] c = Catalogue.counts(each);
                if (c == null || c[1] <= 0) continue;
                s.item(emblem(each), x, y, 24);
                s.text(each.label.charAt(0) + each.label.substring(1).toLowerCase(java.util.Locale.ROOT), x + 34, y + 4, Sheet.BODY, Glass.a(Glass.TEXT, 1F));
                s.right(c[0] + " of " + c[1], x + PW, y + 4, Sheet.BODY, Glass.a(c[0] >= c[1] ? Glass.GOOD : s.label, 1F));
                float w = PW * Math.clamp(c[0] / (float) c[1], 0F, 1F);
                s.fill(x, y + 30, x + PW, y + 34, Sheet.argb(0x05080F, .6F));
                s.fill(x, y + 30, x + w, y + 34, Glass.a(Glass.EDGE, 1F));
                y += 46;
            }
            return y;
        }
        Catalogue.Slot slot = slots.get(index);
        s.caps(sh.label, x, y, Glass.a(s.label, 1F));
        y += 28;
        y = s.lines(slot.name(), x, y, PW, Sheet.TITLE, Glass.a(Glass.TITLE, 1F)) + 4;
        if (slot.creature() != null || !slot.icon().isEmpty()) {
            Kit.floor(s, x + PW / 2F, y + 168, 150);
            boolean drawn = slot.creature() != null && Kit.creature(s, slot.creature(), x + 60, y + 6, x + PW - 60, y + 184, (x + PW / 2F - mx) / 400F, 0F);
            if (!drawn && !slot.icon().isEmpty()) s.item(slot.icon(), x + PW / 2F - 60, y + 40, 120);
            y += 200;
        } else {
            y += 8;
        }
        for (String line : slot.body().split("\n")) if (!line.isBlank()) y = s.lines(line, x, y, PW, Sheet.BODY, Glass.a(Glass.TEXT, 1F)) + 6;
        return y;
    }

    // ---- keys --------------------------------------------------------------------------------------------------------

    @Override
    public void hints(List<Kit.Hint> out) {
        out.add(Kit.Hint.of("Next find", "Tab"));
        out.add(Kit.Hint.of("Shelf", "↑", "↓"));
        out.add(Kit.Hint.of("More", "Scroll"));
    }

    /** Tab walks what you've found, shelf by shelf. */
    @Override
    public void next(int step) {
        List<Shelf> shelves = new ArrayList<>();
        List<Integer> index = new ArrayList<>();
        for (Shelf sh : Shelf.values()) {
            List<Catalogue.Slot> slots = Catalogue.slots(sh);
            if (slots == null) continue;
            for (int i = 0; i < slots.size(); i++) if (slots.get(i).found()) {
                shelves.add(sh);
                index.add(i);
            }
        }
        if (shelves.isEmpty()) return;
        int at = -1;
        for (int i = 0; i < shelves.size(); i++) if (shelves.get(i) == onShelf && index.get(i) == onSlot) at = i;
        int to = Math.floorMod(at < 0 ? (step > 0 ? 0 : -1) : at + step, shelves.size());
        boolean moved = shelves.get(to) != onShelf;
        onShelf = shelves.get(to);
        onSlot = index.get(to);
        if (moved) jump = onShelf;
        Catalogue.Slot slot = Catalogue.slots(onShelf).get(onSlot);
        StatusSections.cue(StatusSections.Cue.PAGE);
        StatusSections.narrate(slot.name() + ". " + slot.body().replace('\n', ' '));
    }

    @Override
    public boolean key(KeyEvent e) {
        int k = e.key();
        if (k == InputConstants.KEY_DOWN || k == InputConstants.KEY_UP) {
            Shelf at = Shelf.values()[0];
            for (Shelf sh : Shelf.values()) if (shelfY[sh.ordinal()] <= scroll + 8) at = sh;
            int to = Math.clamp(at.ordinal() + (k == InputConstants.KEY_DOWN ? 1 : shelfY[at.ordinal()] < scroll - 8 ? 0 : -1), 0, Shelf.values().length - 1);
            jump = Shelf.values()[to];
            StatusSections.journalFilter(jump);
            lastFilter = jump;
            StatusSections.cue(StatusSections.Cue.PAGE);
            return true;
        }
        // the old sections on the wall keep their own keys (the Bell Book's song, the Codex's pages)
        for (Section sec : StatusSections.of(StatusSections.Tab.JOURNAL)) {
            if (k == InputConstants.KEY_TAB || k == InputConstants.KEY_Q || k == InputConstants.KEY_E || k == InputConstants.KEY_ESCAPE) break;
            if (!(sec instanceof JournalFilters) && sec.visible() && sec.key(e)) return true;
        }
        return false;
    }

    @Override
    public boolean back() {
        if (onShelf == null) return false;
        onShelf = null;
        onSlot = -1;
        return true;
    }

    @Override
    public String backWords() {
        return onShelf == null ? "Close" : "Back";
    }

    @Override
    public boolean scroll(float mx, float my, double amount) {
        if (mx >= PANEL) panelScroll -= (float) amount * 40;
        else scroll -= (float) amount * 60;
        return true;
    }
}
