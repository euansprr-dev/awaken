package dev.awaken.progression;

import dev.awaken.core.DirectorLog;
import dev.awaken.core.api.NowHooks;
import dev.awaken.progression.content.Defs.Goal;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Teaching in play (plan v2 P3) and the level road's goals.
 *
 * <p><b>Lessons</b> are short chains of in-world steps that start when a level-road flag unlocks (the first meditation,
 * the first lucid dream, the first walk in the Veil). The current step shows one Now line saying the one next action;
 * a step may ask a real decision with a choice card; when its condition holds a toast says so and the next step
 * starts. Modules define their lessons with {@link #define} (veil, lucidium); the conditions read their own counters.
 *
 * <p><b>Goals</b> are the {@code goal} of a level-road step ({@link Goal}): once the step is unlocked, its Now line shows
 * until the goal's condition holds, then a toast. Only the newest open goal shows, so the tracker never becomes a list.
 *
 * <p>Progress is saved as profile counters ({@code awaken:lesson/<id>} = steps done, {@code awaken:lesson/<id>/<step>}
 * = card sent, {@code awaken:goal/<step path>} = goal met), so nothing new is added to the saved profile. A step or goal
 * already met when it first comes up completes silently: only things done while the game was asking get a toast.
 * Checked once a second per player (the progression player tick); nothing allocates unless something changed.
 */
public final class Lessons {
    private Lessons() {}

    public static final String ACTION = "lesson";

    /** A real decision inside a step: asked once, when {@code when} first holds. */
    public record Choice(Predicate<ServerPlayer> when, String title, String body, String yesDoes, String noDoes,
                         Consumer<ServerPlayer> onYes, Consumer<ServerPlayer> onNo) {}

    /** One step: its Now line (shown while {@code showNow} holds), its finish line and the toast it ends with. */
    public record Step(String id, String now, int priority, Predicate<ServerPlayer> showNow, Predicate<ServerPlayer> done,
                       String toastTitle, String toastText, @Nullable Choice choice) {
        public static Step of(String id, String now, int priority, Predicate<ServerPlayer> done, String toastTitle, String toastText) {
            return new Step(id, now, priority, p -> true, done, toastTitle, toastText, null);
        }

        public Step when(Predicate<ServerPlayer> showNow) {
            return new Step(id, now, priority, showNow, done, toastTitle, toastText, choice);
        }

        public Step ask(Choice c) {
            return new Step(id, now, priority, showNow, done, toastTitle, toastText, c);
        }
    }

    /** A lesson: starts when {@code flag} is granted, runs while {@code active} holds (a feature switched off pauses it). */
    public record Lesson(String id, String flag, Predicate<ServerPlayer> active, List<Step> steps) {}

    /** A defined lesson with its counter keys built once. */
    private record Entry(Lesson lesson, String progressKey, String[] askedKeys, String[] nowId) {}

    private static final List<Entry> LESSONS = new ArrayList<>();
    private static final Map<String, Entry> BY_ID = new HashMap<>();
    /** Keys of steps and goals this session has seen still open, per player: only those toast when they complete. */
    private static final Map<UUID, Set<String>> SEEN_OPEN = new HashMap<>();

    public static void define(Lesson lesson) {
        String progress = Counters.LESSON_PREFIX + lesson.id();
        String[] asked = new String[lesson.steps().size()];
        String[] now = new String[lesson.steps().size()];
        for (int i = 0; i < asked.length; i++) {
            asked[i] = progress + "/" + lesson.steps().get(i).id();
            now[i] = "lesson:" + lesson.id() + "/" + lesson.steps().get(i).id();
        }
        Entry e = new Entry(lesson, progress, asked, now);
        LESSONS.removeIf(x -> x.lesson().id().equals(lesson.id()));
        LESSONS.add(e);
        BY_ID.put(lesson.id(), e);
    }

    static void init() {
        Signal.onAction(ACTION, Lessons::answer);
        NowHooks.register(Lessons::suggest);
    }

    // ---- state ----------------------------------------------------------------------------------------------------

    /** Every defined lesson, in definition order. */
    public static List<Lesson> lessons() {
        List<Lesson> out = new ArrayList<>(LESSONS.size());
        for (Entry e : LESSONS) out.add(e.lesson());
        return out;
    }

    /** Steps of this lesson the player has finished (0 = not started or on the first step). */
    public static int progress(ServerPlayer player, String lessonId) {
        Entry e = BY_ID.get(lessonId);
        return e == null ? 0 : (int) Progression.profile(player).counter(e.progressKey());
    }

    /** The id of the step the player is on, or "" when the lesson hasn't started (no flag) or is finished. */
    public static String currentStep(ServerPlayer player, String lessonId) {
        Entry e = BY_ID.get(lessonId);
        if (e == null || !Progression.hasFlag(player, e.lesson().flag())) return "";
        int i = progress(player, lessonId);
        return i < e.lesson().steps().size() ? e.lesson().steps().get(i).id() : "";
    }

    /** The goal's saved "met" marker for a level-road step id ("awaken:the_well" → "awaken:goal/the_well"). */
    public static String goalKey(String stepId) {
        int colon = stepId.indexOf(':');
        return Counters.GOAL_PREFIX + (colon < 0 ? stepId : stepId.substring(colon + 1));
    }

    // ---- the once-a-second check --------------------------------------------------------------------------------

    /** Advances lessons and goals for one player (the progression player tick calls this once a second). */
    public static void evaluate(ServerPlayer player) {
        Profile p = Progression.profile(player);
        if (!p.awakened()) return;
        Set<String> seen = SEEN_OPEN.get(player.getUUID());
        if (seen == null) SEEN_OPEN.put(player.getUUID(), seen = new HashSet<>());
        for (int li = 0; li < LESSONS.size(); li++) advance(player, p, LESSONS.get(li), seen);
        List<LevelRoad.Step> steps = LevelRoad.steps();
        for (int i = 0; i < steps.size(); i++) {
            LevelRoad.Step s = steps.get(i);
            if (s.def().goal().isEmpty() || s.def().level() > p.level()) continue;
            String key = s.goalKey();
            if (p.counter(key) > 0 || !p.unlocks.contains(s.idString())) continue;
            Goal g = s.def().goal().get();
            if (g.done().test(p, null)) {
                Progression.setCounter(player, key, 1);
                DirectorLog.log(player, "level-road", "goal met: " + s.id());
                if (seen.remove(key)) toast(player, s.def().title(), g.toast().isEmpty() ? "Done" : g.toast());
            } else {
                seen.add(key);
            }
        }
    }

    private static void advance(ServerPlayer player, Profile p, Entry e, Set<String> seen) {
        Lesson l = e.lesson();
        if (!LevelRoad.hasFlag(p, l.flag()) || !l.active().test(player)) return;
        int i = (int) p.counter(e.progressKey());
        boolean changed = false;
        while (i < l.steps().size()) {
            Step s = l.steps().get(i);
            String key = e.askedKeys()[i];
            if (s.done().test(player)) {
                i++;
                changed = true;
                Progression.setCounter(player, e.progressKey(), i);
                DirectorLog.log(player, "lesson", l.id() + ": " + s.id() + " done");
                if (seen.remove(key) && !s.toastTitle().isEmpty()) toast(player, s.toastTitle(), s.toastText());
                continue;
            }
            seen.add(key);
            Choice c = s.choice();
            if (c != null && p.counter(key) == 0 && c.when().test(player)) {
                Progression.setCounter(player, key, 1);
                Signal.send(player, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(c.title()))
                        .body(Component.literal(c.body()))
                        .choice(ACTION + ":" + l.id() + "/" + s.id(), c.yesDoes(), c.noDoes())
                        .ttl(20 * 120).build());
            }
            break;
        }
        if (changed && i >= l.steps().size()) DirectorLog.log(player, "lesson", l.id() + " finished");
    }

    /** Toasts sent for finished steps and goals (tests check that only things done while asked are announced). */
    static int toastsSent;

    private static void toast(ServerPlayer player, String title, String text) {
        toastsSent++;
        var w = SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(title))
                .presentation(SignalWindow.Presentation.TOAST).ttl(20 * 6);
        if (!text.isEmpty()) w.body(Component.literal(text));
        Signal.send(player, w.build());
    }

    private static void answer(ServerPlayer player, String action, boolean yes) {
        String rest = action.substring(ACTION.length() + 1);
        int slash = rest.indexOf('/');
        if (slash < 0) return;
        Entry e = BY_ID.get(rest.substring(0, slash));
        if (e == null) return;
        String stepId = rest.substring(slash + 1);
        int i = (int) Progression.profile(player).counter(e.progressKey());
        if (i >= e.lesson().steps().size() || !e.lesson().steps().get(i).id().equals(stepId)) return; // a stale card
        Step s = e.lesson().steps().get(i);
        if (s.choice() == null) return;
        Progression.setCounter(player, e.askedKeys()[i], yes ? 2 : 3); // 1 sent, 2 yes, 3 no
        DirectorLog.log(player, "lesson", e.lesson().id() + ": " + stepId + (yes ? " yes" : " no"));
        (yes ? s.choice().onYes() : s.choice().onNo()).accept(player);
    }

    /** Whether the player answered this step's card, and how: 0 not asked, 1 waiting, 2 yes, 3 no. */
    public static int answer(ServerPlayer player, String lessonId, String stepId) {
        return (int) Progression.profile(player).counter(Counters.LESSON_PREFIX + lessonId + "/" + stepId);
    }

    // ---- the Now tracker ----------------------------------------------------------------------------------------------

    /** A lesson step just reached outranks standing reminders (a waiting trial, points to spend) for five minutes. */
    static final int FRESH_TICKS = 6000, FRESH_BOOST = 30;
    private record Fresh(String nowId, long since) {}
    private static final java.util.Map<java.util.UUID, Fresh> FRESH = new java.util.HashMap<>();

    private static int freshPriority(ServerPlayer player, String nowId, int base) {
        long now = player.level().getGameTime();
        Fresh f = FRESH.get(player.getUUID());
        if (f == null || !f.nowId().equals(nowId)) {
            f = new Fresh(nowId, now);
            FRESH.put(player.getUUID(), f);
        }
        return now - f.since() < FRESH_TICKS ? base + FRESH_BOOST : base;
    }

    private static List<NowHooks.Suggestion> suggest(ServerPlayer player) {
        Profile p = Progression.profile(player);
        if (!p.awakened()) return List.of();
        NowHooks.Suggestion lesson = null;
        int best = -1;
        for (int li = 0; li < LESSONS.size(); li++) {
            Entry e = LESSONS.get(li);
            Lesson l = e.lesson();
            if (!LevelRoad.hasFlag(p, l.flag()) || !l.active().test(player)) continue;
            int i = (int) p.counter(e.progressKey());
            if (i >= l.steps().size()) continue;
            Step s = l.steps().get(i);
            if (s.priority() > best && s.showNow().test(player) && !s.done().test(player)) {
                best = s.priority();
                lesson = NowHooks.Suggestion.of(e.nowId()[i], freshPriority(player, e.nowId()[i], s.priority()), s.now());
            }
        }
        NowHooks.Suggestion goal = openGoal(player, p);
        if (lesson == null && goal == null) return List.of();
        if (lesson == null) return List.of(goal);
        if (goal == null) return List.of(lesson);
        return List.of(lesson, goal);
    }

    /** The newest unlocked level-road goal the player can act on now and hasn't met. */
    static NowHooks.@Nullable Suggestion openGoal(ServerPlayer player, Profile p) {
        List<LevelRoad.Step> steps = LevelRoad.steps();
        for (int i = steps.size() - 1; i >= 0; i--) {
            LevelRoad.Step s = steps.get(i);
            if (s.def().goal().isEmpty() || s.def().level() > p.level() || !p.unlocks.contains(s.idString())) continue;
            if (p.counter(s.goalKey()) > 0) continue;
            Goal g = s.def().goal().get();
            if (g.done().test(p, null) || !Requirements.met(player, g.requires())) continue;
            return NowHooks.Suggestion.of("progression:goal/" + s.id(), g.priority(), g.now());
        }
        return null;
    }

    static void forget(UUID player) {
        SEEN_OPEN.remove(player);
    }

    static void forgetAll() {
        SEEN_OPEN.clear();
    }
}
