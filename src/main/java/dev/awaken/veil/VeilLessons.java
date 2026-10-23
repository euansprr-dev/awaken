package dev.awaken.veil;

import dev.awaken.core.Later;
import dev.awaken.core.api.DreamHooks;
import dev.awaken.progression.Counters;
import dev.awaken.progression.Lessons;
import dev.awaken.progression.Lessons.Choice;
import dev.awaken.progression.Lessons.Lesson;
import dev.awaken.progression.Lessons.Step;
import dev.awaken.progression.Progression;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Predicate;

/**
 * Meditation and the Veil taught in play (plan v2 §4.4, "taught in play"). Each lesson starts when its level-road flag
 * unlocks and reads this module's own counters; nothing here changes how meditation or projection work. Texts fit the
 * System v2 surfaces (tracker line ~200 px; a toast's title and first sentence 144 px).
 * <ul>
 *   <li>Level 11, {@code awaken:meditation}: meditate once (5 settled seconds).</li>
 *   <li>Level 15, {@code awaken:veil}: step out of your body; hear one of the dead; the Stillpoint (a choice card:
 *       go there now, or later by holding Focus for 20 s).</li>
 * </ul>
 */
final class VeilLessons {
    private VeilLessons() {}

    /** Five settled seconds of meditation (the ring has filled a quarter of the way). */
    static final long FIRST_MEDITATION_TICKS = 100;

    static void register() {
        Predicate<ServerPlayer> awake = p -> !Veil.isSpirit(p) && !DreamHooks.isDreaming(p);
        Lessons.define(new Lesson("meditation", "awaken:meditation", p -> Veil.enabled(), List.of(
                Step.of("meditate", "Meditate: sneak and hold G, keep still", 60,
                                p -> count(p, Counters.MEDITATE_TICKS) >= FIRST_MEDITATION_TICKS,
                                "Meditation", "Energy flows back faster. Wounds and Fracture heal too.")
                        .when(awake))));

        Lessons.define(new Lesson("veil", "awaken:veil", p -> Veil.enabled(), List.of(
                Step.of("step_out", "Meditate 10 s, let go of G: step out", 62,
                                p -> count(p, Counters.VEIL_TICKS) > 0,
                                "You stepped out", "Y takes you back. Only blue things can be touched.")
                        .when(awake),
                Step.of("the_dead", "Find one of the dead; press G", 55,
                        p -> count(p, Counters.SPIRITS_SPOKEN) > 0,
                        "The dead talk", "Hear a story to its end. It stays in your Codex."),
                Step.of("stillpoint", "Hold G for 20 s while meditating", 50,
                                p -> count(p, Stillpoint.VISITS) > 0,
                                "The Stillpoint", "Door Out: go anywhere. Your body waits here, safe.")
                        .when(awake)
                        .ask(new Choice(VeilLessons::calm, "The Stillpoint",
                                "A temple above the clouds where your body is safe. Any time: meditate 20 s.",
                                "Take me there now", "Later",
                                p -> Later.run(p.level().getServer(), 1, () -> {
                                    if (calm(p)) Veil.enterStillpoint(p);
                                }),
                                p -> {})))));
    }

    /** Somewhere a trip to the Stillpoint is safe to offer: awake, in the overworld, nothing hunting you. */
    static boolean calm(ServerPlayer p) {
        if (Veil.isSpirit(p) || Veil.inStillpoint(p) || DreamHooks.isDreaming(p) || !p.isAlive()) return false;
        if (p.level().dimension() != Level.OVERWORLD) return false;
        return !Meditation.inCombat(p);
    }

    private static long count(ServerPlayer p, String counter) {
        return Progression.profile(p).counter(counter);
    }
}
