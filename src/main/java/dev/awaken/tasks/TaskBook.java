package dev.awaken.tasks;

import dev.awaken.core.api.Goals;
import dev.awaken.core.api.Goals.Step;
import dev.awaken.core.api.NowHooks;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Tasks page for one player (SPEC §27.13): every module's goal steps and every Now suggestion as one list, one row
 * per key (two sources of the same thing make one row, the one that says more kept), nothing a goal step covers twice,
 * then the one step on top. What's done never gets here: each source drops it first (SPEC §27.12).
 */
public final class TaskBook {
    private TaskBook() {}

    /** At this priority a suggestion is urgent (the opening, a ritual's doors) and goes on top whatever else is open. */
    static final int URGENT = 90;
    /** How many Now suggestions are read (the page shows them all under their goals; the corner still shows three). */
    static final int NOW_MAX = 48;

    /** The page: the steps (path first, then priority), the top step's key and the pinned key ("" for none). */
    public record Book(List<Step> steps, String top, String pinned) {
        public @Nullable Step get(String key) {
            for (Step s : steps) if (s.key().equals(key)) return s;
            return null;
        }

        public @Nullable Step topStep() {
            return get(top);
        }
    }

    /** A page is built at most twice a second per player (the card, the sync and the sweep all read it). */
    static final int CACHE_TICKS = 10;

    private record Cached(long tick, Book book) {}

    private static final Map<UUID, Cached> CACHE = new HashMap<>();

    public static Book of(ServerPlayer p) {
        long now = p.level().getServer().getTickCount();
        Cached c = CACHE.get(p.getUUID());
        if (c != null && now >= c.tick && now - c.tick < CACHE_TICKS) return c.book;
        Book b = build(p);
        CACHE.put(p.getUUID(), new Cached(now, b));
        return b;
    }

    /** The page built afresh (after a pin; in the tests). */
    public static Book fresh(ServerPlayer p) {
        forget(p.getUUID());
        return of(p);
    }

    static void forget(UUID player) {
        CACHE.remove(player);
    }

    private static Book build(ServerPlayer p) {
        Map<String, Step> byKey = new LinkedHashMap<>();
        for (Step s : Goals.all(p)) byKey.merge(s.key(), s, TaskBook::merge);
        List<Step> rich = List.copyOf(byKey.values());
        for (NowHooks.Suggestion n : NowHooks.collect(p, NOW_MAX)) {
            if (covered(rich, n.id())) continue;
            Step s = NowMap.step(n);
            byKey.merge(s.key(), s, TaskBook::merge);
        }
        List<Step> steps = new ArrayList<>(byKey.values());
        steps.sort(ORDER);
        TaskState st = TaskState.of(p);
        String pinned = st.pinned;
        boolean held = false;
        for (Step s : steps) held |= s.key().equals(pinned);
        if (!held) pinned = ""; // a pinned step that's done, or gone, unpins itself
        return new Book(List.copyOf(steps), top(steps, pinned), pinned);
    }

    /** Path first (the main path in order), then the most pressing, then the key so the rows never shuffle. */
    static final Comparator<Step> ORDER = Comparator.comparingInt(Step::path)
            .thenComparing(Step::priority, Comparator.reverseOrder()).thenComparing(Step::key);

    /** Whether a goal step stands for this suggestion: the same id, or a cover ending in ':' or '/' that starts it. */
    static boolean covered(List<Step> steps, String id) {
        for (Step s : steps) {
            if (s.key().equals(id)) return true;
            for (String c : s.covers()) {
                if (c.equals(id) || (c.endsWith(":") || c.endsWith("/")) && id.startsWith(c)) return true;
            }
        }
        return false;
    }

    /** Two steps with one key: the one with more needs (or choices), the first on a tie; the place kept from either. */
    static Step merge(Step a, Step b) {
        Step keep = b.needs().size() + b.choices().size() > a.needs().size() + a.choices().size() ? b : a, other = keep == a ? b : a;
        if (keep.where().isPresent() || other.where().isEmpty()) return keep;
        return new Step(keep.key(), keep.goal(), keep.title(), keep.why().isEmpty() ? other.why() : keep.why(), keep.needs(), other.where(),
                Math.min(keep.path(), other.path()), keep.optional() && other.optional(), Math.max(keep.priority(), other.priority()),
                keep.choices(), keep.covers());
    }

    /**
     * The one step on top: an urgent one first; then the pinned one; then the main path's next (never an optional
     * upgrade); then the most pressing of the rest.
     */
    static String top(List<Step> steps, String pinned) {
        Step best = null;
        for (Step s : steps) if (s.priority() >= URGENT && (best == null || s.priority() > best.priority())) best = s;
        if (best != null) return best.key();
        if (!pinned.isEmpty()) return pinned;
        for (Step s : steps) {
            if (s.optional() || s.path() == Goals.NO_PATH) continue;
            if (best == null || s.path() < best.path()) best = s;
        }
        if (best != null) return best.key();
        for (Step s : steps) {
            if (s.optional()) continue;
            if (best == null || s.priority() > best.priority()) best = s;
        }
        return best == null ? "" : best.key();
    }
}
