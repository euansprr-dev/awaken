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
        float[] m = new float[6], o = new float[6];
        for (FpClipSet.Clip c : clips.values()) {
            h.assertTrue(c.length > 0, c.name + " has a length");
            for (int i = 1; i < c.eventAt.length; i++) h.assertTrue(c.eventAt[i] >= c.eventAt[i - 1], c.name + "'s events are in order");
            if (c.loop) continue;
            // a one-shot gives the hands back to vanilla: it ends at rest, with nothing left to fade
            c.sample(c.length, m, o);
            for (int i = 0; i < 6; i++) h.assertTrue(Math.abs(m[i]) < 1e-3 && Math.abs(o[i]) < 1e-3, c.name + " ends at rest");
        }
        h.succeed();
    }
}
