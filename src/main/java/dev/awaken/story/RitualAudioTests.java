package dev.awaken.story;

import dev.awaken.core.audio.Loudness;
import dev.awaken.core.test.AwakenTests;
import java.io.IOException;
import java.util.List;

/** Headless regressions for presentation timing, mix admission and the shipped recordings. */
final class RitualAudioTests {
    private RitualAudioTests() {}
    static void register() {
        AwakenTests.register("story_ritual_audio_contacts", 20, h -> {
            var step = new RitualAudioRules.Footfall();
            h.assertTrue(step.sample(0.79F, true) < 0, "newly tracked statue establishes a baseline");
            h.assertTrue(step.sample(0.81F, true) == 1, "right foot lands at half of the authored 1.6 s clip");
            h.assertTrue(step.sample(0.81F, true) < 0 && step.sample(1.59F, true) < 0, "no repeat while planted");
            h.assertTrue(step.sample(1.61F, true) == 2, "left foot lands on the next cycle");
            h.assertTrue(step.sample(4, true) < 0, "tracking/teleport skips never produce a catch-up burst");
            step.sample(4.79F, false);
            h.assertTrue(step.sample(4.81F, true) < 0, "stopping and restarting does not replay an old contact");
            h.assertTrue(step.sample(0.1F, true) < 0, "animation clock reset is quiet");
            step.clear();
            h.assertTrue(step.sample(8.1F, true) < 0, "reconnect starts clean");
            h.assertTrue(RitualAudioRules.SWING_SOUND_DELAY == Ritual.SWING_WINDUP - 8, "whoosh belongs to final eight ticks of real windup");
            var statue = dev.awaken.mobs.Ritual.spawnStatue(h.getLevel(), h.absoluteVec(new net.minecraft.world.phys.Vec3(2, 2, 2)), 0);
            h.assertTrue(statue != null, "real statue exists");
            statue.walkAnimation.update(0.4F, 1, 1);
            h.assertTrue(Math.abs(dev.awaken.mobs.Ritual.walkSeconds(statue, 1) - 0.22F) < 0.0001F, "audio and renderer use vanilla distance phase at unchanged 0.55 scale");
            statue.setStage(dev.awaken.mobs.entity.RitualStatue.STAND);
            h.assertFalse(dev.awaken.mobs.Ritual.walking(statue), "standing statue never steps despite animation inertia");
            var planted = dev.awaken.mobs.Ritual.plantedFoot(statue, 0).subtract(statue.position());
            h.assertTrue(Math.abs(planted.x - 0.225) < 0.0001 && Math.abs(planted.z + 0.36) < 0.0001,
                    "yaw-zero contact follows vanilla model flip and 180-degree rotation");
            statue.setYBodyRot(90);
            planted = dev.awaken.mobs.Ritual.plantedFoot(statue, 0).subtract(statue.position());
            h.assertTrue(Math.abs(planted.x - 0.36) < 0.0001 && Math.abs(planted.z - 0.225) < 0.0001,
                    "contact follows rendered body yaw rather than head/entity facing");
            statue.discard();
            h.succeed();
        });
        AwakenTests.register("story_ritual_audio_impulses", 20, h -> {
            var impulse = new RitualAudioRules.Shake();
            impulse.request(12, 100);
            h.assertTrue(impulse.value() == 1.25F, "impact amplitude capped");
            for (int i = 0; i < 12; i++) { impulse.request(20, 100); impulse.tick(); }
            h.assertTrue(impulse.value() == 0, "simultaneous/repeated impacts cannot extend twelve tick impulse");
            impulse.request(1, 10);
            h.assertTrue(impulse.value() == 0, "four quiet ticks separate impulses");
            for (int i = 0; i < 4; i++) impulse.tick();
            impulse.request(1, 5);
            h.assertTrue(impulse.value() == 1, "next separated impact can play");
            impulse.clear();
            h.assertTrue(impulse.value() == 0, "handoff clears camera impulse");
            h.assertTrue(RitualAudioRules.distance(0, 12) == 1 && RitualAudioRules.distance(6, 12) == 0.5F
                    && RitualAudioRules.distance(12, 12) == 0 && RitualAudioRules.distance(40, 12) == 0, "distance falloff reaches zero at radius");
            h.assertFalse(RitualAudioRules.motionEnabled(true, true), "reduced motion overrides enabled camera shake");
            h.assertFalse(RitualAudioRules.motionEnabled(false, false), "camera shake setting disables impulses");
            h.succeed();
        });
        AwakenTests.register("story_ritual_audio_budget", 20, h -> {
            var budget = new RitualAudioRules.Budget();
            h.assertTrue(budget.accept(1, 10, 1, 0) && budget.accept(1, 10, 2, 1), "beam release and impact admitted together");
            h.assertFalse(budget.accept(1, 10, 3, 2), "third heavy transient refused");
            h.assertFalse(budget.accept(0, 20, 6, 0), "six voices includes the reserved room bed");
            h.assertTrue(budget.accept(2, 20, 2, 0), "first simultaneous statue contact admitted");
            h.assertFalse(budget.accept(2, 20, 3, 0) || budget.accept(2, 23, 3, 0), "group footfalls leave four ticks of space");
            h.assertTrue(budget.accept(2, 24, 3, 0), "separated footfall admitted");
            budget.clear();
            h.assertTrue(budget.accept(2, 0, 1, 0), "scene reset discards cooldown deadlines");
            h.succeed();
        });
        AwakenTests.register("story_ritual_audio_assets", 100, h -> {
            try {
                for (String name : List.of("bell", "doors", "flames_out", "eyes", "gaze_charge", "gaze_fire", "gaze_travel", "gaze_impact",
                        "statue_rise", "swing", "impact", "pit", "rumble", "close", "footstep1", "footstep2", "footstep3", "hall")) {
                    try (var in = RitualAudioTests.class.getResourceAsStream("/assets/awaken/sounds/ritual/" + name + ".ogg")) {
                        h.assertTrue(in != null, name + " shipped");
                        var pcm = Loudness.decode(in);
                        h.assertTrue(pcm.rate() == 44100 && pcm.channels().length == (name.equals("hall") ? 2 : 1), name + " spatial/stereo format");
                        h.assertTrue(Loudness.measure(pcm.channels()).truePeak() < -6, name + " decoded headroom");
                        if (name.equals("gaze_charge")) h.assertTrue(Math.abs(pcm.frames() / 44100.0 - Ritual.GAZE_FILL / 20.0) < 0.02, "charge ends at actual beam release");
                        if (name.startsWith("footstep")) h.assertTrue(pcm.frames() < 44100 * 1.6, "contact tail has a bounded voice lifetime");
                    }
                }
            } catch (IOException ex) { throw new IllegalStateException(ex); }
            h.succeed();
        });
    }
}
