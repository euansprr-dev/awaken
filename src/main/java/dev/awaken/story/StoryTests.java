package dev.awaken.story;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.progression.Profile;
import net.minecraft.nbt.NbtOps;

/** GameTests for story logic. */
final class StoryTests {
    private StoryTests() {}

    static void register() {
        AwakenTests.register("story_triggers", 20, h -> {
            Profile p = new Profile();
            h.assertFalse(Beats.triggered(p, "counter:awaken:gates_cleared>=1"), "no gates yet");
            h.assertTrue(Beats.triggered(p, "rank:E"), "rank E always");
            h.assertFalse(Beats.triggered(p, "rank:D"), "not D yet");
            h.assertFalse(Beats.triggered(p, "trigger:prologue_death"), "code-only triggers never fire on their own");
            h.assertFalse(Beats.triggered(p, "counter:garbage"), "malformed triggers are false, not crashes");
            h.succeed();
        });
        AwakenTests.register("story_progress_codec", 20, h -> {
            StoryProgress s = new StoryProgress();
            s.prologue = StoryProgress.Prologue.ACCEPTING;
            s.prologueGate = "g42";
            s.returnX = 10.5;
            s.seen.add("revelation:rank_d");
            s.sovereignsDefeated.add("awaken:sovereign_ash");
            var tag = StoryProgress.CODEC.encodeStart(NbtOps.INSTANCE, s).getOrThrow();
            StoryProgress t = StoryProgress.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
            h.assertTrue(t.prologue == StoryProgress.Prologue.ACCEPTING && t.prologueGate.equals("g42") && t.returnX == 10.5, "fields");
            h.assertTrue(t.seen.contains("revelation:rank_d") && t.sovereignsDefeated.contains("awaken:sovereign_ash"), "sets");
            h.succeed();
        });
    }
}
