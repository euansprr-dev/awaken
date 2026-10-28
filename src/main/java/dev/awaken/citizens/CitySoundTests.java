package dev.awaken.citizens;

import dev.awaken.core.test.AwakenTests;
import net.minecraft.gametest.framework.GameTestHelper;

final class CitySoundTests {
    static void register() {
        AwakenTests.register("citizens_bell_budget", 40, CitySoundTests::bells);
        AwakenTests.register("citizens_sound_budget", 40, CitySoundTests::voices);
    }

    private static void bells(GameTestHelper h) {
        CityBellSchedule clock = new CityBellSchedule();
        int strikes = 0;
        long previous = -CityBellSchedule.SPACING;
        // A whole day: six strokes, one tower, three occasions; no catch-up hourly cascade.
        for (int t = 0; t < 24000; t++) {
            if (t % 1000 == 0) clock.routine(t, t, 4);
            long[] stroke = clock.next(t);
            if (stroke == null) continue;
            h.assertTrue(stroke[0] == 4 && stroke[4] == 0, "only the selected staffed tower rings");
            h.assertTrue(t - previous >= 160, "recorded tails cannot stack");
            strikes++;
            previous = t;
        }
        h.assertTrue(strikes == 6, "one dawn, two noon and three dusk strokes: " + strikes);
        clock.clear();
        h.assertTrue(clock.routine(0, 12000, 1), "start a routine peal");
        h.assertTrue(clock.next(0) != null, "its first stroke sounds");
        h.assertTrue(clock.alarm(1, 2, 30), "alarm replaces pending routine strokes");
        int alarms = 0;
        previous = 0;
        for (int t = 2; t < 1201; t++) {
            h.assertTrue(!clock.alarm(t, 3, 30), "repeated fire reports do not extend/restart a peal");
            long[] stroke = clock.next(t);
            if (stroke == null) continue;
            h.assertTrue(stroke[0] == 2 && stroke[4] == 1, "only the accepted alarm tower rings");
            h.assertTrue(t - previous >= 160, "alarm waits for the current stroke's tail");
            previous = t;
            alarms++;
        }
        h.assertTrue(alarms == 3, "an alarm is capped at three strokes: " + alarms);
        clock.clear();
        h.assertTrue(clock.next(2000) == null && !clock.pealing(2, 2000), "unload/time change discards pending strokes");
        h.succeed();
    }

    private static void voices(GameTestHelper h) {
        CityAudioBudget a = new CityAudioBudget(), b = new CityAudioBudget();
        int work = CityAudioBudget.group("city.anvil"), bird = CityAudioBudget.group("city.songbird"),
                bell = CityAudioBudget.group("city.bell.hour"), hand = CityAudioBudget.group("city.handbell");
        h.assertTrue(a.accept(work, 100, 0), "a nearby smith may sound");
        h.assertTrue(!a.accept(bird, 100, 1), "another emitter cannot pile onto the same instant");
        h.assertTrue(b.accept(bird, 100, 0), "another player's budget is independent");
        h.assertTrue(!a.accept(bird, 140, CityAudioBudget.VOICES), "full voice budget refuses another source");
        h.assertTrue(a.accept(bell, 140, CityAudioBudget.VOICES), "a warning can take priority over ambience");
        h.assertTrue(!a.accept(work, 180, 0) && !a.accept(hand, 200, 0), "the bell's tail has quiet space");
        h.assertTrue(!a.accept(bell, 295, 0) && a.accept(bell, 296, 0), "duplicate tower events cannot overlap");
        h.assertTrue(a.accept(hand, 500, 0), "handbell after the tower tail");
        h.assertTrue(!a.accept(hand, 1000, 0) && a.accept(hand, 2300, 0), "crier and Watch share a 90 second bell gap");
        h.assertTrue(a.accept(work, 0, 0), "world clock rollback resets deadlines");
        a.clear();
        h.assertTrue(a.accept(hand, 0, 0), "rejoin cannot inherit an old handbell deadline");
        h.succeed();
    }
}
