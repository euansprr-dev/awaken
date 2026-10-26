package dev.awaken.progression.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Extension points for the band (key K): six pages and never more (SPEC §30): Status, Powers, Journal, Truths, Codex,
 * Map. Modules add sections to a page from their client init; sections stack inside the page and scroll. A page can
 * instead open something of its own (the Map opens the Atlas table): {@link #setPageAction}.
 */
public final class StatusSections {
    private StatusSections() {}

    /** The band's pages, in the order of the rail. */
    public enum Tab {
        STATUS("Status"), POWERS("Powers"), JOURNAL("Journal"), TRUTHS("Truths"), CODEX("Codex"), MAP("Map");

        private final String label;

        Tab(String label) {
            this.label = label;
        }

        /** The page's name on the rail and the tabs. */
        public String label() {
            return label;
        }
    }

    public interface Section {
        /** Short uppercase label drawn above the section. */
        String title();

        /** Order inside the page (lower first). Core sections use 0-99. */
        default int order() { return 500; }

        /** Whether to show this section right now (e.g. hide the loadout before you own a power). */
        default boolean visible() { return true; }

        /** Height in GUI pixels for the given width. */
        int height(int width);

        /** Draw at (x, y) with the given width. Coordinates are absolute screen coordinates. */
        void render(GuiGraphicsExtractor g, int x, int y, int width, int mouseX, int mouseY, float partialTick);

        /** Handle a left click inside the section. Return true if consumed. */
        default boolean click(double mouseX, double mouseY, int x, int y, int width) { return false; }
    }

    /** What a page opens instead of its sections, and the key that opens it from anywhere (the Map: the Atlas, M). */
    public record PageAction(Runnable open, @Nullable KeyMapping key) {}

    private static final List<Section>[] SECTIONS = newLists();
    private static final PageAction[] ACTIONS = new PageAction[Tab.values().length];

    @SuppressWarnings("unchecked")
    private static List<Section>[] newLists() {
        List<Section>[] a = new List[Tab.values().length];
        for (int i = 0; i < a.length; i++) a[i] = new ArrayList<>();
        return a;
    }

    public static void add(Tab tab, Section section) {
        SECTIONS[tab.ordinal()].add(section);
        SECTIONS[tab.ordinal()].sort((a, b) -> Integer.compare(a.order(), b.order()));
    }

    public static List<Section> of(Tab tab) {
        return SECTIONS[tab.ordinal()];
    }

    /** Makes a page open {@code open} (after the band closes) instead of showing sections. */
    public static void setPageAction(Tab tab, Runnable open, @Nullable KeyMapping key) {
        ACTIONS[tab.ordinal()] = new PageAction(open, key);
    }

    public static @Nullable PageAction action(Tab tab) {
        return ACTIONS[tab.ordinal()];
    }

    /** The page whose action this key opens, if any (M in the band opens the Map). */
    public static @Nullable Tab pageForKey(net.minecraft.client.input.KeyEvent event) {
        for (Tab t : Tab.values()) {
            PageAction a = ACTIONS[t.ordinal()];
            if (a != null && a.key() != null && a.key().matches(event)) return t;
        }
        return null;
    }
}
