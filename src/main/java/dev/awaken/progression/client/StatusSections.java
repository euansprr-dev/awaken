package dev.awaken.progression.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * Extension points for the Status screen (key K). The screen has four tabs and never more: Status, Truths, Codex,
 * Gate Map. Modules add sections to a tab from their client init. Sections stack vertically inside the tab and scroll.
 */
public final class StatusSections {
    private StatusSections() {}

    public enum Tab { STATUS, TRUTHS, CODEX, GATE_MAP }

    public interface Section {
        /** Short uppercase label drawn above the section. */
        String title();

        /** Order inside the tab (lower first). Core sections use 0-99. */
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

    private static final List<Section>[] SECTIONS = newLists();

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
}
