package dev.awaken.core.api;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Everything AWAKEN puts in front of a player as something to do (SPEC §27.12; Euan 2026-10-30: "the task system is broken
 * and shows stuff you've already done"): the task card, the corner's place, the Now suggestions, Saved for later, trails,
 * the Hearth page's needs and rumours, Bell Book leads and decisions waiting. Each module lists its own lines here, so
 * one check can sweep every source at once: a goal already met is never among them. Server side; not for a hot path.
 */
public final class Shown {
    private Shown() {}

    /** One line in front of the player: where it shows ("card", "hearth") and its words. */
    public record Line(String source, String text) {}

    @FunctionalInterface
    public interface Source {
        List<String> lines(ServerPlayer p);
    }

    private record Named(String name, Source source) {}

    private static final List<Named> SOURCES = new CopyOnWriteArrayList<>();

    public static void register(String name, Source source) {
        SOURCES.add(new Named(name, source));
    }

    /** Every line every source shows this player now. A failing source is skipped with a warning. */
    public static List<Line> all(ServerPlayer p) {
        List<Line> out = new ArrayList<>();
        for (Named n : SOURCES) {
            try {
                for (String s : n.source().lines(p)) if (s != null && !s.isBlank()) out.add(new Line(n.name(), s));
            } catch (RuntimeException e) {
                dev.awaken.Awaken.LOG.warn("[shown] the {} lines failed: {}", n.name(), e.toString());
            }
        }
        return out;
    }

    /** The lines that contain {@code words}, ignoring case (for the checks). */
    public static List<Line> mentioning(ServerPlayer p, String words) {
        String w = words.toLowerCase(Locale.ROOT);
        List<Line> out = new ArrayList<>();
        for (Line l : all(p)) if (l.text().toLowerCase(Locale.ROOT).contains(w)) out.add(l);
        return out;
    }

    /** The names of the sources registered (a check that every place a goal can show is swept). */
    public static List<String> sources() {
        List<String> out = new ArrayList<>();
        for (Named n : SOURCES) out.add(n.name());
        return out;
    }
}
