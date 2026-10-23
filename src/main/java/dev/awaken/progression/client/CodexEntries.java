package dev.awaken.progression.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The Codex is a mystery board, not a list (SPEC section 4). Modules supply entries they have already revealed to this
 * player (rumours, visions, scanned machines, boss insight, spirits met). Undiscovered things are never supplied;
 * a "?" entry is allowed only for something the player has HEARD of but not found.
 */
public final class CodexEntries {
    private CodexEntries() {}

    /** links = ids of other entries this one connects to (drawn as threads on the board). */
    public record Entry(String id, String category, String title, String body, boolean found, List<String> links) {}

    private static final List<Supplier<List<Entry>>> SOURCES = new ArrayList<>();

    public static void addSource(Supplier<List<Entry>> source) {
        SOURCES.add(source);
    }

    public static List<Entry> all() {
        List<Entry> out = new ArrayList<>();
        for (var s : SOURCES) out.addAll(s.get());
        return out;
    }
}
