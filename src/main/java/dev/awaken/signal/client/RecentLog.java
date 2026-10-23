package dev.awaken.signal.client;

import dev.awaken.signal.SignalWindow;
import dev.awaken.signal.ui.SignalText;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * Everything the System said this session, newest first, in full: rows a toast had to drop, panels cut short, story
 * lines, decisions and what you chose. Shown in the Status screen, so nothing is lost to a busy moment or a fight.
 */
public final class RecentLog {
    private RecentLog() {}

    public static final int MAX = 40;

    public static final class Entry {
        public final SignalWindow.Kind kind;
        public final String title;
        public final List<String> lines;
        public final long at;
        public final Optional<String> action;
        private String outcome = "";

        Entry(SignalWindow.Kind kind, String title, List<String> lines, Optional<String> action) {
            this.kind = kind;
            this.title = title;
            this.lines = lines;
            this.action = action;
            this.at = System.currentTimeMillis();
        }

        /** What the player chose, once answered ("You chose: Keep going"). */
        public String outcome() { return outcome; }
    }

    private static final Deque<Entry> ENTRIES = new ArrayDeque<>();

    static void add(SignalWindow w) {
        List<String> lines = new ArrayList<>();
        w.body().map(Component::getString).filter(s -> !s.isBlank()).ifPresent(lines::add);
        for (SignalWindow.Row r : w.rows()) lines.add(r.label().getString() + ": " + r.value().getString());
        w.footer().map(Component::getString).map(SignalText::stripKeyHints).filter(s -> !s.isBlank()).ifPresent(lines::add);
        push(new Entry(w.kind(), w.title().getString(), List.copyOf(lines), w.action()));
    }

    static void add(Component toast) {
        push(new Entry(SignalWindow.Kind.NOTICE, toast.getString(), List.of(), Optional.empty()));
    }

    private static void push(Entry e) {
        ENTRIES.addFirst(e);
        while (ENTRIES.size() > MAX) ENTRIES.removeLast();
    }

    /** Marks the newest entry for this action with the choice made. */
    static void answered(String action, String outcome) {
        for (Entry e : ENTRIES) {
            if (e.action.map(action::equals).orElse(false)) {
                e.outcome = outcome;
                return;
            }
        }
    }

    public static List<Entry> entries() {
        return List.copyOf(ENTRIES);
    }

    static void clear() {
        ENTRIES.clear();
    }
}
