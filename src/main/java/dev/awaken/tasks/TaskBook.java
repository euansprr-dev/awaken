package dev.awaken.tasks;

import dev.awaken.core.AwakenConfig;
import dev.awaken.core.api.Goals;
import dev.awaken.core.api.Goals.Goal;
import dev.awaken.core.api.Goals.Step;
import dev.awaken.core.api.NowHooks;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Tasks page for one player (SPEC §27.13): every module's goal steps and every Now suggestion as one list, one row
 * per key (two sources of the same thing make one row, the one that says more kept), nothing a goal step covers twice,
 * then the one step on top. What's done never gets here: each source drops it first (SPEC §27.12).
 *
 * <p>The cap lives here (SPEC §53), so no plan can go past it: one row per line of work, at most three rows a tab; the
 * rest wait in Later. A line of work keeps its place when its row is finished, and the next one comes up in it.
 */
public final class TaskBook {
    private TaskBook() {}

    /** At this priority a suggestion is urgent (the opening, a ritual's doors) and goes on top whatever else is open. */
    static final int URGENT = 90;
    /** How many Now suggestions are read (the page shows them all under their goals; the corner still shows three). */
    static final int NOW_MAX = 48;
    /** The cap: rows on a tab, and Out there's row on Now (SPEC §53, decision 1). One constant to change after playing it. */
    public static final int TAB_CAP = 3, OUT_CAP = 1;
    /** The road's next stop: said at the foot of the Gates tab, not as a row you can't act on yet. */
    private static final String ROAD = "progression:road/";

    /** One tab: its rows in their places, and everything else of that goal, waiting. */
    public record Shelf(List<Step> rows, List<Step> later) {}

    private static final Shelf EMPTY = new Shelf(List.of(), List.of());

    /** The page: every step (path first, then priority), the top step's key, the pinned key ("" for none), the tabs and their footers. */
    public record Book(List<Step> steps, String top, String pinned, Map<Goal, Shelf> shelves, Map<Goal, String> footers, boolean capped) {
        public @Nullable Step get(String key) {
            for (Step s : steps) if (s.key().equals(key)) return s;
            return null;
        }

        public @Nullable Step topStep() {
            return get(top);
        }

        public Shelf shelf(Goal g) {
            return shelves.getOrDefault(g, EMPTY);
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
        boolean capped = AwakenConfig.TASKS_CAP.get();
        Map<String, Step> byKey = new LinkedHashMap<>();
        for (Step s : Goals.all(p)) byKey.merge(s.key(), s, TaskBook::merge);
        List<Step> rich = List.copyOf(byKey.values());
        Map<Goal, String> footers = new EnumMap<>(Goal.class);
        for (Goals.Footer f : Goals.footers(p)) footers.putIfAbsent(f.goal(), f.line());
        for (NowHooks.Suggestion n : NowHooks.collect(p, NOW_MAX)) {
            if (covered(rich, n.id())) continue;
            if (capped && n.id().startsWith(ROAD)) { // "At level 8: Essence" isn't something to do yet
                footers.putIfAbsent(Goal.RANK, n.text().getString());
                continue;
            }
            Step s = NowMap.step(n);
            byKey.merge(s.key(), s, TaskBook::merge);
        }
        List<Step> steps = new ArrayList<>(byKey.values());
        steps.removeIf(s -> s.voice() == Goals.Voice.HIDDEN);
        steps.sort(ORDER);
        TaskState st = TaskState.of(p);
        String pinned = st.pinned;
        boolean held = false;
        for (Step s : steps) held |= s.key().equals(pinned);
        if (!held) pinned = ""; // a pinned step that's done, or gone, unpins itself
        Set<String> aside = aside(steps, st, pinned);
        List<Step> open = new ArrayList<>(steps);
        open.removeIf(s -> aside.contains(s.key()));
        String top = top(open, pinned);
        Map<Goal, Shelf> shelves = capped ? shelve(steps, aside, pinned, top, st) : unshelved(steps);
        if (openTabs(steps, st) | writeHeld(shelves, st, capped)) st.save(p);
        return new Book(List.copyOf(steps), top, pinned, shelves, footers, capped);
    }

    /** What's set aside and still on the book; never something urgent, never the pin. */
    static Set<String> aside(List<Step> steps, TaskState st, String pinned) {
        Set<String> out = new HashSet<>();
        for (Step s : steps) if (st.aside.containsKey(s.key()) && s.priority() < URGENT && !s.key().equals(pinned)) out.add(s.key());
        return out;
    }

    /** A tab opens the first time it has something of its own to do; a trail doesn't open one. */
    private static boolean openTabs(List<Step> steps, TaskState st) {
        boolean changed = false;
        for (Step s : steps) if (!s.optional() && s.goal() != Goal.OUT) changed |= st.tabsSeen.add(s.goal().name());
        return changed;
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
        // the words and needs of the one that says more; the place, the path, the priority and what's covered from either
        List<String> covers = new ArrayList<>(keep.covers());
        for (String c : other.covers()) if (!covers.contains(c)) covers.add(c);
        List<String> teaches = new ArrayList<>(keep.teaches());
        for (String t : other.teaches()) if (!teaches.contains(t)) teaches.add(t);
        boolean theirs = keep.thread().isEmpty() && !other.thread().isEmpty();
        return new Step(keep.key(), keep.goal(), keep.title(), keep.why().isEmpty() ? other.why() : keep.why(), keep.needs(),
                keep.where().isPresent() ? keep.where() : other.where(), Math.min(keep.path(), other.path()), keep.optional() && other.optional(),
                Math.max(keep.priority(), other.priority()), keep.choices(), List.copyOf(covers), theirs ? other.thread() : keep.thread(),
                theirs ? other.order() : keep.order(), keep.voice() == Goals.Voice.PLAIN ? other.voice() : keep.voice(),
                keep.opens().isEmpty() ? other.opens() : keep.opens(), List.copyOf(teaches), keep.icon().isEmpty() ? other.icon() : keep.icon(),
                keep.icon().isEmpty() ? other.makes() : keep.makes());
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
            if (s.optional() || NowMap.lesson(s.key())) continue; // a lesson you put off never holds the top (SPEC §53)
            if (best == null || s.priority() > best.priority()) best = s;
        }
        return best == null ? "" : best.key();
    }

    // ---- the cap (SPEC §53) ----------------------------------------------------------------------------------------

    /** A step's line of work: its thread, or a thread of its own. */
    static String thread(Step s) {
        return s.thread().isEmpty() ? "#" + s.key() : s.thread();
    }

    /** A trail only fills a place nothing else wants (decision 11). */
    static boolean trail(Step s) {
        return s.key().startsWith("trail/");
    }

    /** Whether you have everything it asks: its own needs, or those of what was picked on it. */
    static boolean ready(TaskState st, Step s) {
        List<Goals.Need> needs = TasksModule.needsOf(st, s);
        if (needs.isEmpty()) return false;
        for (Goals.Need n : needs) if (!n.met()) return false;
        return true;
    }

    /** How much of what it needs is in hand, 0 to 1. */
    private static double done(TaskState st, Step s) {
        List<Goals.Need> needs = TasksModule.needsOf(st, s);
        if (needs.isEmpty()) return 0;
        double sum = 0;
        for (Goals.Need n : needs) sum += n.need() <= 0 ? 1 : Math.min(1.0, (double) n.have() / n.need());
        return sum / needs.size();
    }

    /** What has to show, in order: 0 urgent, 1 the pin, 2 the main path; 3 everything else. */
    private static int rank(Step s, String pinned) {
        return s.priority() >= URGENT ? 0 : s.key().equals(pinned) ? 1 : s.path() != Goals.NO_PATH && !s.optional() ? 2 : 3;
    }

    private static Map<Goal, Shelf> shelve(List<Step> steps, Set<String> aside, String pinned, String top, TaskState st) {
        Map<Goal, Shelf> out = new EnumMap<>(Goal.class);
        // which of a thread's steps comes up: what's on top or pinned, then the thread's own order
        Comparator<Step> within = Comparator.<Step, Boolean>comparing(s -> !s.key().equals(top)).thenComparing(s -> !s.key().equals(pinned))
                .thenComparingInt(Step::order).thenComparing(ORDER);
        // urgent, pin, path; then ready, then closest to done
        Comparator<Step> ranked = Comparator.<Step>comparingInt(s -> rank(s, pinned)).thenComparingInt(Step::path)
                .thenComparing(s -> !ready(st, s)).thenComparing(s -> -done(st, s)).thenComparing(Step::priority, Comparator.reverseOrder())
                .thenComparing(Step::key);
        for (Goal g : Goal.values()) {
            List<Step> of = new ArrayList<>();
            for (Step s : steps) if (s.goal() == g) of.add(s);
            of.sort(within);
            Map<String, Step> leads = new LinkedHashMap<>();
            for (Step s : of) if (!aside.contains(s.key())) leads.putIfAbsent(thread(s), s);
            List<Step> real = new ArrayList<>(), trails = new ArrayList<>();
            for (Step s : leads.values()) (trail(s) ? trails : real).add(s);
            real.sort(ranked);
            trails.sort(ORDER);
            int cap = g == Goal.OUT ? OUT_CAP : TAB_CAP;
            List<String> order = new ArrayList<>();
            Set<String> must = new HashSet<>();
            for (Step s : real) {
                order.add(thread(s));
                if (rank(s, pinned) < 3) must.add(thread(s));
            }
            List<Step> rows = new ArrayList<>();
            for (String t : place(st.held.getOrDefault(g.name(), List.of()), order, must, cap)) rows.add(leads.get(t));
            if (g != Goal.OUT) for (Step t : trails) if (rows.size() < cap) rows.add(t); // never on Now, so never Out there's row
            List<Step> later = new ArrayList<>();
            for (Step s : steps) if (s.goal() == g && !rows.contains(s)) later.add(s);
            out.put(g, new Shelf(List.copyOf(rows), List.copyOf(later)));
        }
        return out;
    }

    /** With the cap off (config tasks.cap=false): the old list, every step a row. */
    private static Map<Goal, Shelf> unshelved(List<Step> steps) {
        Map<Goal, Shelf> out = new EnumMap<>(Goal.class);
        for (Goal g : Goal.values()) {
            List<Step> of = new ArrayList<>();
            for (Step s : steps) if (s.goal() == g) of.add(s);
            out.put(g, new Shelf(List.copyOf(of), List.of()));
        }
        return out;
    }

    /** Remembers which threads hold which places; false when nothing moved. */
    private static boolean writeHeld(Map<Goal, Shelf> shelves, TaskState st, boolean capped) {
        if (!capped) return false;
        boolean changed = false;
        for (var e : shelves.entrySet()) {
            List<String> now = new ArrayList<>();
            for (Step s : e.getValue().rows()) if (!trail(s)) now.add(thread(s));
            if (now.equals(st.held.getOrDefault(e.getKey().name(), List.of()))) continue;
            if (now.isEmpty()) st.held.remove(e.getKey().name());
            else st.held.put(e.getKey().name(), now);
            changed = true;
        }
        return changed;
    }

    /**
     * Which threads get the places, in place order. Places held last time stay put while their thread has a row. What must
     * show (urgent, pinned, the main path) takes a free place, or the last place held by something that needn't show;
     * whatever's left fills what's still free, best first. A thread with no row left gives its place to the next.
     */
    static List<String> place(List<String> held, List<String> ranked, Set<String> must, int cap) {
        String[] at = new String[cap];
        for (int i = 0; i < Math.min(held.size(), cap); i++) if (ranked.contains(held.get(i))) at[i] = held.get(i);
        for (String t : ranked) {
            if (!must.contains(t) || has(at, t)) continue;
            int i = free(at);
            if (i < 0) for (int j = cap - 1; j >= 0 && i < 0; j--) if (!must.contains(at[j])) i = j;
            if (i >= 0) at[i] = t;
        }
        for (String t : ranked) {
            int i = free(at);
            if (i < 0) break;
            if (!has(at, t)) at[i] = t;
        }
        List<String> out = new ArrayList<>(cap);
        for (String t : at) if (t != null) out.add(t);
        return out;
    }

    private static boolean has(String[] at, String t) {
        for (String s : at) if (t.equals(s)) return true;
        return false;
    }

    private static int free(String[] at) {
        for (int i = 0; i < at.length; i++) if (at[i] == null) return i;
        return -1;
    }

    /**
     * The quiet line under a row: "Pick one: ..." for three or fewer picks not made; on the step on top, the main path's
     * next; else the next in its thread. A Lead says nothing of what's after it, and nothing names a Lead coming up.
     */
    static String then(Book book, Collection<String> aside, Step s, boolean picked) {
        if (s.choices().size() > 1 && s.choices().size() <= 3 && !picked) return "Pick one: " + names(s.choices());
        if (s.voice() != Goals.Voice.PLAIN) return "";
        if (s.key().equals(book.top()) && s.path() != Goals.NO_PATH) {
            Step next = null;
            for (Step o : book.steps()) {
                if (o.path() == Goals.NO_PATH || o.optional() || o.path() <= s.path() || aside.contains(o.key())) continue;
                if (next == null || o.path() < next.path()) next = o;
            }
            if (next != null && next.voice() == Goals.Voice.PLAIN) return "Then: " + next.title();
        }
        if (s.thread().isEmpty()) return "";
        Step next = null;
        for (Step o : book.steps()) {
            if (!o.thread().equals(s.thread()) || o.goal() != s.goal() || aside.contains(o.key()) || o.order() <= s.order()) continue;
            if (next == null || o.order() < next.order()) next = o;
        }
        return next != null && next.voice() == Goals.Voice.PLAIN ? "Then: " + next.title() : "";
    }

    private static String names(List<Goals.Choice> choices) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < choices.size(); i++) {
            if (i > 0) b.append(i == choices.size() - 1 ? " or " : ", ");
            b.append(choices.get(i).title().replaceFirst("^(Build|Make) (an?|your Bedroom a) ", ""));
        }
        return b.toString();
    }
}
