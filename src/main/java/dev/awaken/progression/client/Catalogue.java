package dev.awaken.progression.client;

import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.Ui;
import dev.awaken.signal.client.UiSection;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The Journal as a catalogue (SPEC §27.13): a category per kind of find along the top with how many you've found. A
 * category with a fixed set (creatures, beasts, bells, cave regions) shows every slot, numbered; one you haven't found is
 * a "?" with its number and nothing else, never a name, a picture or a hint. Click a found slot for what you've learned.
 * An open-ended category (lore and places, happenings) shows a count; the gate worlds count against the Atlas's total.
 * Each module adds its own category; what was on the Journal before stands under its category below the slots.
 */
public final class Catalogue {
    private Catalogue() {}

    /** One slot: its name and what you've learned once found; {@code icon} may be empty. */
    public record Slot(String name, boolean found, String body, ItemStack icon) {
        public static Slot unknown() {
            return new Slot("", false, "", ItemStack.EMPTY);
        }
    }

    private static final Map<StatusSections.Shelf, Supplier<List<Slot>>> FIXED = new EnumMap<>(StatusSections.Shelf.class);
    private static final Map<StatusSections.Shelf, Supplier<int[]>> COUNTS = new EnumMap<>(StatusSections.Shelf.class);
    private static final java.util.Set<String> OWN_SHELF = new java.util.HashSet<>();
    private static final Map<StatusSections.Shelf, java.util.function.IntSupplier> FRESH = new EnumMap<>(StatusSections.Shelf.class);

    /** A category with a fixed set: every slot in order, found or not. Adds its slot grid to the Journal. */
    public static void fixed(StatusSections.Shelf shelf, Supplier<List<Slot>> slots) {
        FIXED.put(shelf, slots);
        StatusSections.add(StatusSections.Tab.JOURNAL, new Grid(shelf));
    }

    /** A Codex category that has its own category here (creatures, cave regions): kept off the Lore board. */
    public static void ownShelf(String codexCategory) {
        OWN_SHELF.add(codexCategory);
    }

    /** Whether a Codex category belongs on the Lore board. */
    static boolean onBoard(String codexCategory) {
        return !OWN_SHELF.contains(codexCategory);
    }

    /** An open-ended category's count: {found, total}, the total -1 when it isn't known. */
    public static void count(StatusSections.Shelf shelf, Supplier<int[]> foundTotal) {
        COUNTS.put(shelf, foundTotal);
    }

    /** How many things in a category you haven't read yet (Your Story's new messages): the strip says "3 NEW". */
    public static void fresh(StatusSections.Shelf shelf, java.util.function.IntSupplier unread) {
        FRESH.put(shelf, unread);
    }

    /** What the category strip says beside a category's name: "12/40", "7", "3 NEW" or "". */
    static String tally(StatusSections.Shelf shelf) {
        var f = FRESH.get(shelf);
        int fresh = f == null ? 0 : f.getAsInt();
        if (fresh > 0) return fresh + " NEW";
        int[] c = counts(shelf);
        if (c == null) return "";
        return c[1] < 0 ? Integer.toString(c[0]) : c[0] + "/" + c[1];
    }

    static int @Nullable [] counts(StatusSections.Shelf shelf) {
        try {
            Supplier<List<Slot>> f = FIXED.get(shelf);
            if (f != null) {
                List<Slot> slots = f.get();
                int found = 0;
                for (Slot s : slots) if (s.found()) found++;
                return new int[]{found, slots.size()};
            }
            Supplier<int[]> c = COUNTS.get(shelf);
            return c == null ? null : c.get();
        } catch (RuntimeException e) {
            return null; // a category whose data isn't here yet shows no count
        }
    }

    /** A fixed category's slots as tiles; a found one opens to what you've learned. */
    static final class Grid extends UiSection {
        private static final int TILE_H = 24, GAP = 3, MIN_W = 74;
        private static final int DIM = 0x3A4658;
        private final StatusSections.Shelf shelf;
        private int open = -1;

        Grid(StatusSections.Shelf shelf) {
            this.shelf = shelf;
        }

        @Override public String title() { return ""; }
        @Override public int order() { return -90; } // right under the strip
        @Override public StatusSections.Shelf shelf() { return shelf; }

        @Override
        public String note() {
            int[] c = counts(shelf);
            return c == null ? "" : c[0] + " OF " + c[1] + " FOUND";
        }

        @Override
        protected void build(Ui ui) {
            List<Slot> slots;
            try {
                slots = FIXED.get(shelf).get();
            } catch (RuntimeException e) {
                slots = List.of();
            }
            if (slots.isEmpty()) {
                ui.text("Nothing here yet.", Glass.LABEL);
                return;
            }
            int cols = Math.max(2, (ui.width + GAP) / (MIN_W + GAP)), w = (ui.width - GAP * (cols - 1)) / cols;
            for (int i = 0; i < slots.size(); i += cols) {
                int y = ui.y();
                for (int c = 0; c < cols && i + c < slots.size(); c++) {
                    int n = i + c, x = ui.x + c * (w + GAP);
                    Slot s = slots.get(n);
                    tile(ui, s, n, x, y, w);
                    if (!ui.drawing() && s.found() && ui.buttonAt("", x, y, w, TILE_H, Glass.EDGE, true)) {
                        open = open == n ? -1 : n;
                        StatusSections.cue(StatusSections.Cue.PAGE);
                    }
                }
                ui.gap(TILE_H + GAP);
            }
            if (open >= 0 && open < slots.size() && slots.get(open).found()) {
                Slot s = slots.get(open);
                ui.card(Glass.EDGE, b -> {
                    b.text(s.name(), Glass.TITLE);
                    for (String line : s.body().split("\n")) if (!line.isBlank()) b.text(line, Glass.LABEL);
                });
            }
        }

        private void tile(Ui ui, Slot s, int n, int x, int y, int w) {
            if (!ui.drawing()) return;
            boolean lit = n == open, hover = s.found() && ui.hover(x, y, w, TILE_H);
            ui.fill(x, y, x + w, y + TILE_H, Glass.a(0x05080F, lit ? 0.85f : hover ? 0.7f : 0.5f));
            ui.canvas().outline(x, y, w, TILE_H, Glass.a(s.found() ? Glass.EDGE : DIM, lit ? 1f : s.found() ? 0.6f : 0.5f));
            String number = Integer.toString(n + 1);
            if (!s.found()) { // a "?" carries its number and nothing else
                ui.textAt(number, x + 3, y + 3, w - 6, Glass.a(Glass.LABEL, 0.45f), false);
                ui.textAt("?", x + w / 2 - 2, y + 8, 8, Glass.a(Glass.LABEL, 0.55f), false);
                return;
            }
            int tx = x + 4;
            if (!s.icon().isEmpty()) {
                ui.itemAt(s.icon(), x + 3, y + (TILE_H - Ui.ICON) / 2, Ui.ICON);
                tx += Ui.ICON + 2;
            }
            ui.textAt(s.name(), tx, y + 4, x + w - tx - 3, Glass.a(Glass.TITLE, 1f), false);
            ui.textAt(number, tx, y + 14, x + w - tx - 3, Glass.a(Glass.LABEL, 0.6f), false);
        }
    }
}
