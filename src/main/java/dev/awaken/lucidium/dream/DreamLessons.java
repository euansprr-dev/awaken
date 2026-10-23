package dev.awaken.lucidium.dream;

import dev.awaken.core.AwakenConfig;
import dev.awaken.core.PlayDays;
import dev.awaken.lucidium.data.Dreamer;
import dev.awaken.lucidium.plan.DreamPlanner;
import dev.awaken.progression.Counters;
import dev.awaken.progression.Lessons;
import dev.awaken.progression.Lessons.Choice;
import dev.awaken.progression.Lessons.Lesson;
import dev.awaken.progression.Lessons.Step;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The first lucid dream, taught in play (plan v2 §4.4). Starts when the level road grants {@code awaken:dreams}
 * (level 12) and reads this module's counters; the dream mechanics are untouched.
 * <ol>
 *   <li>Press H awake once: a reality check.</li>
 *   <li>At nightfall a choice card: set tonight's intention to fly (lucid sooner, flight at 35 lucidity) or just sleep.</li>
 *   <li>Sleep and dream.</li>
 *   <li>Become lucid in a dream.</li>
 * </ol>
 */
public final class DreamLessons {
    private DreamLessons() {}

    public static void register() {
        Lessons.define(new Lesson("dreams", "awaken:dreams", p -> enabled(), List.of(
                Step.of("reality_check", "Press H: a reality check", 58,
                                p -> count(p, Counters.REALITY_CHECKS) > 0,
                                "Reality check", "In dreams it makes you lucid. Awake, nothing changes.")
                        .when(p -> !Dreams.isDreaming(p)),
                Step.of("intention", "Sleep in a bed tonight: you will dream", 52,
                                p -> Lessons.answer(p, "dreams", "intention") >= 2 || hasIntention(p) || count(p, Counters.DREAMS) > 0,
                                "", "")
                        .when(p -> !Dreams.isDreaming(p))
                        .ask(new Choice(DreamLessons::dusk, "Tonight's dream",
                                "An intention before sleep makes you lucid sooner, and lets you fly at 35 lucidity.",
                                "Tonight I will fly", "Just sleep",
                                p -> setIntention(p, DreamPlanner.FLY), p -> {})),
                Step.of("first_dream", "Sleep in a bed tonight: you will dream", 52,
                                p -> count(p, Counters.DREAMS) > 0,
                                "You dreamed", "Press H inside next time. It was built from your day.")
                        .when(p -> !Dreams.isDreaming(p)),
                Step.of("lucid", "Next dream: press H until it clears", 55,
                        p -> count(p, Counters.LUCID_DREAMS) > 0,
                        "Lucid", "Fly, or hold H on a wall. You know you're dreaming."))));
    }

    /** Evening in the overworld, awake, not fighting: the moment to ask about tonight. */
    static boolean dusk(ServerPlayer p) {
        if (Dreams.isDreaming(p) || !p.isAlive() || p.level().dimension() != Level.OVERWORLD) return false;
        if (!p.level().isDarkOutside()) return false;
        return p.getLastHurtByMob() == null || p.tickCount - p.getLastHurtByMobTimestamp() > 200;
    }

    static boolean hasIntention(ServerPlayer p) {
        Dreamer d = Dreams.dreamer(p);
        return !d.intention(PlayDays.current(p.level().getServer())).isEmpty();
    }

    /** What the Dream Journal does when you choose an intention, without opening the journal. */
    static void setIntention(ServerPlayer p, String intention) {
        if (Dreams.isDreaming(p)) return;
        Dreamer d = Dreams.dreamer(p);
        d.setIntention(intention, PlayDays.current(p.level().getServer()));
        Planner.invalidate(p);
        Signal.toast(p, Component.literal("Tonight, I will " + JournalActions.intentionText(intention) + "."));
        if (p.level().isDarkOutside()) Planner.request(p, "intention");
    }

    private static boolean enabled() {
        try {
            return AwakenConfig.LUCIDIUM.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    private static long count(ServerPlayer p, String counter) {
        return Progression.profile(p).counter(counter);
    }
}
