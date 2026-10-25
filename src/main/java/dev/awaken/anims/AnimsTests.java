package dev.awaken.anims;

import dev.awaken.core.test.AwakenTests;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.Map;

/** The first-person clips: the file reads, every clip a module plays is in it, and every one-shot ends at rest. */
final class AnimsTests {
    private AnimsTests() {}

    static void register() {
        AwakenTests.register("anims_fp_clips", 20, AnimsTests::clips);
    }

    static void clips(GameTestHelper h) {
        StringBuilder errors = new StringBuilder();
        Map<String, FpClipSet.Clip> clips = FpClipSet.load(errors);
        h.assertTrue(errors.isEmpty(), "the clip files read: " + errors);
        for (String name : dev.awaken.body.BodyClips.FP) h.assertTrue(clips.containsKey(name), name + " is in the file");
        for (String name : AnimsClips.TOUCHES) {
            h.assertTrue(clips.containsKey(name), name + " is in the file");
            if (!name.equals(AnimsClips.LID)) h.assertTrue(clips.containsKey(name + "_off"), name + "_off is in the file");
        }
        float[] m = new float[6], o = new float[6];
        for (FpClipSet.Clip c : clips.values()) {
            h.assertTrue(c.length > 0, c.name + " has a length");
            for (int i = 1; i < c.eventAt.length; i++) h.assertTrue(c.eventAt[i] >= c.eventAt[i - 1], c.name + "'s events are in order");
            if (c.loop) continue;
            // a one-shot gives the hands back to vanilla: it ends at rest (offsets at 0, fists where vanilla's rest)
            c.sample(c.length, m, o);
            if (c.fists) {
                if (c.toStance) continue; // played over a stance: it ends where the stance holds the hands
                float[] r = dev.awaken.core.api.FirstPerson.View.REST_FIST;
                boolean mainRest = !c.hasMain() || Math.abs(m[0] - r[0]) < 1e-3 && Math.abs(m[1] - r[1]) < 1e-3 && Math.abs(m[2] - r[2]) < 1e-3;
                boolean offRest = !c.hasOff() || o[1] <= -0.9F
                        || Math.abs(o[0] + r[0]) < 1e-3 && Math.abs(o[1] - r[1]) < 1e-3 && Math.abs(o[2] - r[2]) < 1e-3;
                h.assertTrue(mainRest && offRest, c.name + " gives its fists back (at rest, or the off hand out under the frame)");
            } else {
                for (int i = 0; i < 6; i++) h.assertTrue(Math.abs(m[i]) < 1e-3 && Math.abs(o[i]) < 1e-3, c.name + " ends at rest");
            }
        }
        h.succeed();
    }
}
