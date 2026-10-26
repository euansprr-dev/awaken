package dev.awaken.signal.client;

import dev.awaken.core.api.Notices;
import dev.awaken.progression.client.StatusSections;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * The Journal (SPEC §26): the quiet home of what the director filed instead of showing. Ambient finds (a creature
 * seen again, a biome's first steps, a rumour), and anything that waited too long to still be news, land here with a
 * soft cue by the crosshair; the K screen lists them newest first and the count of unread ones. The finds themselves
 * are kept for good in the Codex (the profile's discovery counters); this is the session's diary of them.
 */
public final class Journal {
    private Journal() {}

    public static final int MAX = 60;

    public record Entry(String title, String line, int colour, Notices.Tier tier, long at) {}

    private static final Deque<Entry> ENTRIES = new ArrayDeque<>();
    /** Entries filed at or before this were read; {@code seen}: the newest one the open K screen showed. */
    private static long readUpTo, seen;
    private static long last;

    static void add(String title, String line, int colour, Notices.Tier tier) {
        long at = Math.max(System.currentTimeMillis(), last + 1); // strictly increasing, so "read up to" is exact
        last = at;
        ENTRIES.addFirst(new Entry(title, line == null ? "" : line, colour, tier, at));
        while (ENTRIES.size() > MAX) ENTRIES.removeLast();
    }

    /** Client tick: what the K screen showed counts as read once it closes. */
    static void tick(boolean screenOpen) {
        if (!screenOpen && seen > readUpTo) readUpTo = seen;
    }

    public static List<Entry> entries() {
        return List.copyOf(ENTRIES);
    }

    /** Entries filed since the player last looked at the Journal. */
    public static int unread() {
        int n = 0;
        for (Entry e : ENTRIES) {
            if (e.at() <= readUpTo) break;
            n++;
        }
        return n;
    }

    static void clear() {
        ENTRIES.clear();
        readUpTo = seen = last = 0;
    }

    static void register() {
        // right under your stats on the Status page (Euan, 2026-10-26), and again on the Journal page under your tasks
        StatusSections.add(StatusSections.Tab.STATUS, new Section(20, 4));
        StatusSections.add(StatusSections.Tab.JOURNAL, new Section(700, SHORT));
    }

    private static final int SHORT = 8;

    /**
     * The band's Journal: newest first; opening it reads them. On the Status page only the newest few, with "ALL n ›"
     * to the Journal page; there, the first eight and a button for the rest.
     */
    static final class Section extends UiSection {
        private static boolean all;
        private final int order, limit;

        Section(int order, int limit) {
            this.order = order;
            this.limit = limit;
        }

        @Override
        public String note() {
            return limit < SHORT && ENTRIES.size() > limit ? "ALL " + ENTRIES.size() + " ›" : "";
        }

        @Override
        public StatusSections.@org.jspecify.annotations.Nullable Tab notePage() {
            return limit < SHORT ? StatusSections.Tab.JOURNAL : null;
        }

        public String title() {
            int n = unread();
            return n > 0 ? "JOURNAL · " + n + " NEW" : "JOURNAL";
        }

        public int order() { return order; }

        public boolean visible() { return !ENTRIES.isEmpty(); }

        @Override
        protected void build(Ui ui) {
            List<Entry> list = entries();
            if (!list.isEmpty()) seen = Math.max(seen, list.get(0).at());
            boolean short_ = limit < SHORT;
            int n = all && !short_ ? list.size() : Math.min(limit, list.size());
            long now = System.currentTimeMillis();
            for (int i = 0; i < n; i++) {
                Entry e = list.get(i);
                ui.pair((e.at() > readUpTo ? "◆ " : "◇ ") + e.title(), ago(now - e.at()), e.colour(), Glass.LABEL);
                if (!e.line().isBlank()) ui.text(e.line(), Glass.TEXT, 6, false);
                ui.gap(2);
            }
            if (!short_ && list.size() > SHORT) {
                Ui.Flow row = ui.flow();
                if (row.button(all ? "Show fewer" : "Show all " + list.size(), Glass.EDGE, true)) all = !all;
                row.end();
            }
        }

        private static String ago(long ms) {
            long s = ms / 1000;
            if (s < 60) return "just now";
            if (s < 3600) return s / 60 + " min ago";
            return s / 3600 + " h ago";
        }
    }
}
