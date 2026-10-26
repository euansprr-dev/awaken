package dev.awaken.delve.client;

import dev.awaken.core.audio.client.Soundscape;
import dev.awaken.delve.sense.SenseModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;

import java.util.Locale;

/**
 * The deep's hum (SPEC §28.5; PLAN 8.4): below y 0 a low loop round the listener rises slowly with depth (nothing at
 * y 0, about a quarter at y -30, half at y -60), the place's bed under everything (Deepglass blocks hum on their own,
 * W2's {@code delve.deepglass_hum}). Near a seep ({@link CaveState#seep()}) it grows louder and a second, stranger
 * voice rises over it, while the world muffles ({@link Soundscape#muffle}: the Conductor's air closes toward 600 Hz
 * and nature, AmbientSounds with it, falls to 30%); a quiet seep, its heart fallen, lets it all go. In the deep dark
 * it keeps to half (that place holds its breath). Under combat or Lord music it dips a little, never its own muffle.
 */
final class DeepHum {
    private DeepHum() {}

    /** The seep's muffle in the Conductor (its owner). */
    private static final Object MUFFLE = new Object();
    private static SenseLoop hum, voice;
    private static float muffleLeft = 1F, cutoff = 20000F;

    static void tick(Minecraft mc) {
        if (!SenseState.active) {
            reset();
            return;
        }
        float s = CaveState.seep();
        float deep = SenseState.y >= 0 ? 0F : smooth((float) -SenseState.y / 8F) * (0.1F + 0.42F * (float) Math.pow(SenseState.below, 1.2));
        // ducked under combat music and the like (natureLevel), but not by the seep's own muffle
        float duck = Mth.clamp(Soundscape.natureLevel() / Math.max(0.05F, muffleLeft), 0F, 1F);
        float humGoal = (deep * (float) Math.sqrt(SenseState.under) + 0.48F * s) * (0.75F + 0.25F * duck);
        if (SenseState.biome == SenseState.DEEP_DARK) humGoal *= 0.6F;
        float voiceGoal = 0.85F * (float) Math.pow(s, 1.3) * (0.75F + 0.25F * duck);
        float wobble = Mth.sin(SenseState.tick * 0.0157F) * 0.012F; // a stranger voice, never quite still
        hum = drive(mc, hum, SenseModule.DEEP_HUM.get(), Math.min(1F, humGoal), 1F - 0.06F * SenseState.below, 0.012F);
        voice = drive(mc, voice, SenseModule.DEEP_SEEP.get(), Math.min(1F, voiceGoal), 1F + wobble, 0.015F);
        if (s > 0.01F) {
            muffleLeft = 1F - 0.7F * s;
            cutoff = 20000F * (float) Math.pow(600.0 / 20000.0, s);
            Soundscape.muffle(MUFFLE, cutoff, muffleLeft, 10);
        } else {
            muffleLeft = 1F;
            cutoff = 20000F;
        }
    }

    private static SenseLoop drive(Minecraft mc, SenseLoop loop, SoundEvent e, float goal, float pitch, float ease) {
        SoundManager sm = mc.getSoundManager();
        if (loop != null && (loop.isStopped() || loop.age > 40 && !sm.isActive(loop))) loop = null;
        if (loop == null) {
            if (goal < 0.01F) return null;
            loop = new SenseLoop(e, true, 0, 0, 0, pitch, ease);
            sm.play(loop);
        }
        loop.goal = goal;
        loop.pitchGoal = pitch;
        return loop;
    }

    private static float smooth(float x) {
        float t = Mth.clamp(x, 0F, 1F);
        return t * t * (3F - 2F * t);
    }

    static int voices() {
        return (hum != null && !hum.isStopped() ? 1 : 0) + (voice != null && !voice.isStopped() ? 1 : 0);
    }

    static String status() {
        return String.format(Locale.ROOT, "hum %.2f, seep voice %.2f, muffle %s", hum == null ? 0F : hum.level, voice == null ? 0F : voice.level,
                muffleLeft < 1F ? String.format(Locale.ROOT, "%.0f Hz, nature %.2f", cutoff, muffleLeft) : "off");
    }

    static void reset() {
        if (hum != null) hum.end();
        if (voice != null) voice.end();
        hum = voice = null;
        muffleLeft = 1F;
        cutoff = 20000F;
    }
}
