package dev.awaken.signal.client;

import dev.awaken.core.api.Notices;
import dev.awaken.signal.NoticeHistory;
import dev.awaken.progression.client.StatusSections;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * The Journal (SPEC §26): the quiet home of what the director filed instead of showing. Ambient finds (a creature
 * seen again, a biome's first steps, a rumour), and anything that waited too long to still be news, land here with a
 * soft cue by the crosshair; the K screen lists them newest first and the count of unread ones. The finds themselves
 * are kept for good in the Codex (the profile's discovery counters); ambient entries remain a session diary; meaningful events come from the server and survive relog and death.
 */
public final class Journal {
    private Journal() {}

    public static final int MAX = 60;

    public record Entry(String title, String line, int colour, Notices.Tier tier, long at, long sequence, java.util.List<String> details, String kind) {}

    private static List<Entry> merged = List.of();
    private static boolean dirty;
    private static final Deque<Entry> ENTRIES = new ArrayDeque<>();
    /** Entries filed at or before this were read; {@code seen}: the newest one the open K screen showed. */
    private static long readUpTo, seen;
    private static long last, readSequence, seenSequence;
    private static final java.util.Map<String, Entry> SAVED = new java.util.HashMap<>();
    private static final java.util.Set<Long> EXPANDED = new java.util.HashSet<>();

    static void add(String title, String line, int colour, Notices.Tier tier) {
        long at = Math.max(System.currentTimeMillis(), last + 1); // strictly increasing, so "read up to" is exact
        last = at;
        ENTRIES.addFirst(new Entry(title, line == null ? "" : line, colour, tier, at, 0, java.util.List.of(), ""));
        while (ENTRIES.size() > MAX) ENTRIES.removeLast();
        dirty = true;
    }

    /** Client tick: what the K screen showed counts as read once it closes. */
    static void tick(boolean screenOpen) {
        if (!screenOpen && seen > readUpTo) readUpTo = seen;
        if (!screenOpen && seenSequence > readSequence) {
            readSequence = seenSequence;
            net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new NoticeHistory.Read(readSequence));
        }
    }

    public static List<Entry> entries() {
        if (!dirty) return merged;
        java.util.List<Entry> all = new java.util.ArrayList<>(SAVED.values());
        all.addAll(ENTRIES);
        all.sort(java.util.Comparator.comparingLong(Entry::at).thenComparingLong(Entry::sequence).reversed());
        merged = List.copyOf(all);
        dirty = false;
        return merged;
    }

    /** Entries filed since the player last looked at the Journal. */
    public static int unread() {
        int n = 0;
        for (Entry e : SAVED.values()) if (e.sequence() > readSequence) n++;
        for (Entry e : ENTRIES) if (e.at() > readUpTo) n++;
        return n;
    }

    static void accept(NoticeHistory.Update update) {
        if (update.reset()) { SAVED.clear(); EXPANDED.clear(); readSequence = update.read(); seenSequence = 0; }
        else readSequence = Math.max(readSequence, update.read());
        long now = System.currentTimeMillis();
        for (var entry : update.entries()) {
            var event = entry.event();
            long age = Math.max(0, update.now() - entry.at());
            Entry previous = SAVED.put(event.id(), new Entry(event.title(), event.summary(), event.colour(), Notices.Tier.IMPORTANT,
                    now - age * 50, entry.sequence(), event.details(), event.kind().label));
            if (previous != null) EXPANDED.remove(previous.sequence());
            if (!update.reset() && update.announce()) SystemHud.event(event);
        }
        dirty = true;
        while (SAVED.size() > NoticeHistory.MAX) {
            var oldest = SAVED.entrySet().stream().min(java.util.Comparator.comparingLong(e -> e.getValue().sequence())).orElseThrow();
            EXPANDED.remove(oldest.getValue().sequence()); SAVED.remove(oldest.getKey());
        }
    }

    static void clear() {
        ENTRIES.clear(); SAVED.clear(); EXPANDED.clear();
        merged = List.of(); dirty = false; Section.all = false;
        readSequence = seenSequence = 0;
        readUpTo = seen = last = 0;
    }

    static void register() {
        // right under your stats on the Status page (Euan, 2026-10-26), and the whole log under the Journal's Your Story
        StatusSections.add(StatusSections.Tab.STATUS, new Section(20, 4));
        StatusSections.add(StatusSections.Tab.JOURNAL, new Section(10, SHORT));
        dev.awaken.progression.client.Catalogue.fresh(StatusSections.Shelf.STORY, Journal::unread);
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
            return limit < SHORT && !entries().isEmpty() ? "ALL " + entries().size() + " ›" : "";
        }

        @Override
        public StatusSections.@org.jspecify.annotations.Nullable Tab notePage() {
            return limit < SHORT ? StatusSections.Tab.JOURNAL : null;
        }

        public String title() {
            // the Status page's few are the JOURNAL; the Journal page's whole list is its LOG (SPEC §30.6)
            String name = limit < SHORT ? "JOURNAL" : "RECENT EVENTS";
            int n = unread();
            return n > 0 ? name + " · " + n + " NEW" : name;
        }

        public int order() { return order; }

        @Override
        public dev.awaken.progression.client.StatusSections.Shelf shelf() {
            return StatusSections.Shelf.STORY; // the log is Your Story's (SPEC §27.13); "ALL n ›" opens the Journal there
        }

        public boolean visible() { return !ENTRIES.isEmpty() || !SAVED.isEmpty(); }

        @Override
        protected void build(Ui ui) {
            List<Entry> list = entries();
            boolean short_ = limit < SHORT;
            int n = all && !short_ ? list.size() : Math.min(limit, list.size());
            long now = System.currentTimeMillis();
            for (int i = 0; i < n; i++) {
                Entry e = list.get(i);
                boolean unread = e.sequence() > 0 ? e.sequence() > readSequence : e.at() > readUpTo;
                ui.pair((unread ? "◆ " : "◇ ") + e.title(), ago(now - e.at()), e.colour(), Glass.LABEL);
                if (!e.line().isBlank()) ui.text(e.line(), Glass.TEXT, 6, false);
                if (ui.drawing() && !short_) {
                    if (e.sequence() > 0) seenSequence = Math.max(seenSequence, e.sequence());
                    else seen = Math.max(seen, e.at());
                }
                if (!short_ && e.sequence() > 0) {
                    Ui.Flow row = ui.flow();
                    boolean expanded = EXPANDED.contains(e.sequence());
                    if (row.button(expanded ? "Show less" : "Read more", Glass.EDGE, true)) {
                        if (expanded) EXPANDED.remove(e.sequence()); else EXPANDED.add(e.sequence());
                    }
                    row.end();
                    if (EXPANDED.contains(e.sequence())) {
                        ui.text(e.kind(), Glass.LABEL, 6, false);
                        for (String detail : e.details()) ui.text(detail, Glass.TEXT, 6, false);
                    }
                }
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
