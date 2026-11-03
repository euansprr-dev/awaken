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
 * The first dream, taught in play (SPEC §9.2). Starts when the level road grants {@code awaken:dreams} (level 12), the
 * day the first dream comes to wait, and reads this module's counters.
 * <ol>
 *   <li>Press H awake once: a reality check.</li>
 *   <li>At nightfall a choice card: set tonight's intention to find the door (ten more seconds in the dream) or just sleep.</li>
 *   <li>Sleep and dream.</li>
 *   <li>Take control of the dream with H.</li>
 * </ol>
 */
public final class DreamLessons {
    private DreamLessons() {}

    public static void register() {
        Lessons.define(new Lesson("dreams", "awaken:dreams", p -> enabled(), List.of(
                Step.of("reality_check", "Press H: a reality check", 58,
                                p -> count(p, Counters.REALITY_CHECKS) > 0,
                                "Reality check", "In a dream, H takes control. Awake, nothing changes.")
                        .when(p -> !Dreams.isDreaming(p)),
                Step.of("intention", "A dream is waiting: sleep in a bed", 52,
                                p -> Lessons.answer(p, "dreams", "intention") >= 2 || hasIntention(p) || count(p, Counters.DREAMS) > 0,
                                "", "")
                        .when(p -> !Dreams.isDreaming(p))
                        .ask(new Choice(DreamLessons::dusk, "Tonight's dream",
                                "Deciding before you sleep that you'll find the door at the end gives you ten more seconds in the dream.",
                                "I'll find the door", "Just sleep",
                                p -> setIntention(p, DreamPlanner.FIND_THE_DOOR), p -> {})),
                Step.of("first_dream", "A dream is waiting: sleep in a bed", 52,
                                p -> count(p, Counters.DREAMS) > 0,
                                "You dreamed", "Every dream holds a prize. Get to the end before it fades.")
                        .when(p -> !Dreams.isDreaming(p)),
                Step.of("lucid", "In the dream, press H to take control", 55,
                        p -> count(p, Counters.LUCID_DREAMS) > 0,
                        "You took control", "Press H to see the way. Get to the end before the dream fades."))));
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
