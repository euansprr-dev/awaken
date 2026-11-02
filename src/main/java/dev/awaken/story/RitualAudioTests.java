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
            h.assertTrue(RitualAudioRules.SWING_SOUND_DELAY == Ritual.SWING_WINDUP - 6, "the whoosh's peak lands on the blade's contact (six ticks of downswing)");
            var statue = dev.awaken.mobs.Ritual.spawnStatue(h.getLevel(), h.absoluteVec(new net.minecraft.world.phys.Vec3(2, 2, 2)), 0);
            h.assertTrue(statue != null, "real statue exists");
            statue.walkAnimation.update(0.4F, 1, 1);
            h.assertTrue(Math.abs(dev.awaken.mobs.Ritual.walkSeconds(statue, 1) - 0.064F) < 0.0001F, "audio and renderer share the distance phase: 2.5 blocks a 1.6 s cycle");
            statue.setStage(dev.awaken.mobs.entity.RitualStatue.STAND);
            h.assertFalse(dev.awaken.mobs.Ritual.walking(statue), "standing statue never steps despite animation inertia");
            var planted = dev.awaken.mobs.Ritual.plantedFoot(statue, 0).subtract(statue.position());
            h.assertTrue(Math.abs(planted.x - 0.25) < 0.0001 && Math.abs(planted.z - 0.75) < 0.0001,
                    "yaw-zero contact: the heel strike lands ahead of the body (vanilla model flip and 180-degree rotation)");
            statue.setYBodyRot(90);
            planted = dev.awaken.mobs.Ritual.plantedFoot(statue, 0).subtract(statue.position());
            h.assertTrue(Math.abs(planted.x + 0.75) < 0.0001 && Math.abs(planted.z - 0.25) < 0.0001,
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
            h.assertTrue(RitualAudioRules.stepShake(3) == 1 && RitualAudioRules.stepShake(16) == 1 && RitualAudioRules.stepShake(20) == 0.5F
                    && RitualAudioRules.stepShake(24) == 0, "a footfall nudges the view whole within 16 blocks, gone by 24");
            h.assertFalse(RitualAudioRules.motionEnabled(true, true), "reduced motion overrides enabled camera shake");
            h.assertFalse(RitualAudioRules.motionEnabled(false, false), "camera shake setting disables impulses");
            h.succeed();
        });
        AwakenTests.register("story_ritual_audio_budget", 20, h -> {
            var budget = new RitualAudioRules.Budget();
            h.assertTrue(budget.accept(1, 10, 1, 0) && budget.accept(1, 10, 2, 1) && budget.accept(1, 10, 3, 2),
                    "beam release, its strike and a blade admitted together");
            h.assertFalse(budget.accept(1, 10, 4, 3), "a fourth heavy hit is not admitted as it stands");
            h.assertTrue(RitualAudioRules.Budget.stealsHeavy(1, 3) && !RitualAudioRules.Budget.stealsHeavy(1, 2) && !RitualAudioRules.Budget.stealsHeavy(3, 3),
                    "only a heavy hit over the heavy budget takes the oldest heavy's place");
            h.assertTrue(budget.accept(1, 10, 3, 2), "once the oldest fades out, the new hit plays");
            h.assertFalse(budget.accept(0, 20, 10, 0), "ten voices includes the reserved room bed");
            h.assertTrue(budget.accept(2, 20, 2, 0), "first simultaneous statue contact admitted");
            h.assertFalse(budget.accept(2, 20, 3, 0) || budget.accept(2, 21, 3, 0), "group footfalls leave two ticks of space");
            h.assertTrue(budget.accept(2, 22, 3, 0), "separated footfall admitted");
            budget.clear();
            h.assertTrue(budget.accept(2, 0, 1, 0), "scene reset discards cooldown deadlines");
            h.succeed();
        });
        AwakenTests.register("story_ritual_audio_schedules", 20, h -> {
            // the plate's timer: quicker toward its deadline, done before it, tick and tock in turn
            int[] t = RitualAudioRules.PLATE_TICKS;
            h.assertTrue(t[t.length - 1] < Ritual.RING_WINDOW, "the last tick comes before the plate's deadline");
            for (int i = 2; i < t.length; i++) h.assertTrue(t[i] - t[i - 1] <= t[i - 1] - t[i - 2], "the ticks never slow down: " + t[i]);
            h.assertTrue(RitualAudioRules.plateTick(6) == 0 && RitualAudioRules.plateTick(14) == 1 && RitualAudioRules.plateTick(7) == -1,
                    "a timer tick falls on its own ticks only");
            // the pulse: nothing until the flames die, a hard beat on the eyes' bloom, every fourth beat of the Judgement hard
            int doors = Ritual.Beat.DOORS.ordinal(), judged = Ritual.Beat.JUDGED.ordinal(), waking = Ritual.Beat.WAKING.ordinal();
            int judgement = Ritual.Beat.JUDGEMENT.ordinal(), end = Ritual.Beat.END.ordinal();
            h.assertTrue(RitualAudioRules.pulse(doors, 30) == 0 && RitualAudioRules.pulse(doors, 60) > 0, "the pulse starts out of the dark after the flames die");
            h.assertTrue(RitualAudioRules.pulse(judged, 57) > 0 && RitualAudioRules.pulse(judged, 71) > 0, "its race to the eyes begins in BE JUDGED");
            h.assertTrue(RitualAudioRules.pulse(waking, 55) > 0 && RitualAudioRules.pulse(waking, 58) < 0 && RitualAudioRules.pulse(waking, 92) < 0,
                    "the race lands hard on the bloom, and again as the twelve rise");
            h.assertTrue(RitualAudioRules.pulse(judgement, 0) < 0 && RitualAudioRules.pulse(judgement, 16) > 0 && RitualAudioRules.pulse(judgement, 64) < 0
                    && RitualAudioRules.pulse(judgement, 8) == 0, "the Judgement drives every 16 ticks, every fourth beat hard");
            for (int a = 0; a < Ritual.Beat.END.length; a++) h.assertTrue(RitualAudioRules.pulse(end, a) == 0, "no drum once the hand rises");
            // the tone: in with the flames dying, up through the End, bent up a minor third into the close
            h.assertTrue(RitualAudioRules.drone(doors, 20) == 0 && RitualAudioRules.drone(doors, 120) > 0.2F, "the tone rises out of the dark");
            h.assertTrue(RitualAudioRules.drone(end, Ritual.CLOSE) > RitualAudioRules.drone(judgement, 400), "the End is the tone's peak");
            h.assertTrue(Math.abs(RitualAudioRules.dronePitch(end, Ritual.CLOSE) - (float) Math.pow(2, 3 / 12.0)) < 1e-4
                    && RitualAudioRules.dronePitch(judgement, 100) == 1, "only the End bends the tone, a minor third by the close");
            h.succeed();
        });
        AwakenTests.register("story_ritual_audio_assets", 100, h -> {
            // the cinematic pass (tools/art/specs/ritual_v2.py): built loud, true peak under -0.5 dBTP after decoding
            List<String> stereo = List.of("hall", "eyes", "struck", "raise", "error", "close", "pulse_drone");
            try {
                for (String name : List.of("doors", "flames_out", "eyes", "gaze_charge", "gaze_fire", "gaze_travel", "gaze_impact", "statue_rise",
                        "windup", "swing", "swing2", "impact", "impact2", "struck", "pit", "rumble", "raise", "error", "close", "footstep1",
                        "footstep2", "footstep3", "footstep4", "plate_tick", "plate_tock", "plate_rise", "plate_lock", "pulse_soft", "pulse_hard",
                        "pulse_drone", "hall")) {
                    try (var in = RitualAudioTests.class.getResourceAsStream("/assets/awaken/sounds/ritual/" + name + ".ogg")) {
                        h.assertTrue(in != null, name + " shipped");
                        var pcm = Loudness.decode(in);
                        h.assertTrue(pcm.rate() == 44100 && pcm.channels().length == (stereo.contains(name) ? 2 : 1), name + " spatial/stereo format");
                        h.assertTrue(Loudness.measure(pcm.channels()).truePeak() < -0.5, name + " decoded headroom");
                        if (name.equals("gaze_charge")) h.assertTrue(Math.abs(pcm.frames() / 44100.0 - Ritual.GAZE_FILL / 20.0) < 0.02, "charge ends at actual beam release");
                        if (name.startsWith("footstep")) h.assertTrue(pcm.frames() < 44100 * 2, "contact tail has a bounded voice lifetime");
                        if (name.equals("plate_rise")) h.assertTrue(Math.abs(pcm.frames() / 44100.0 - Ritual.RING_WINDOW / 20.0) < 0.02, "the grind spans the plate's window");
                    }
                }
            } catch (IOException ex) { throw new IllegalStateException(ex); }
            h.succeed();
        });
    }
}
