package dev.awaken.core.audio.client.world;

import dev.awaken.core.audio.AudioSounds;
import dev.awaken.core.audio.client.AirMixer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

import java.util.concurrent.CompletableFuture;

/**
 * The Conductor's one stream: a sound instance around the listener (relative, no attenuation, so Sound Physics leaves
 * it dry and we filter it ourselves) on the Ambient slider, whose audio is an {@link AirMixer}. Each tick the Conductor
 * says which air layers it wants and how loud ({@link #want}, then {@link #commit}); this class keeps them in the
 * mixer's slots, loading a layer's bytes the first time and only reusing a slot once it has gone silent. The sound
 * starts when something wants to be heard and ends after ten silent seconds.
 */
final class AirBed {
    private static final int QUIET_TICKS = 200;
    private static final float FADE_SECONDS = 6F;

    private final Identifier[] wantFile = new Identifier[AirMixer.SLOTS * 2];
    private final float[] wantGain = new float[AirMixer.SLOTS * 2];
    private int wants, quiet;
    private float cutoff = 20000F;
    private Instance sound;

    /** This tick: the air layer {@code file} at {@code gain} (several asks for one file add up). */
    void want(Identifier file, float gain) {
        if (gain <= 0.001F) return;
        for (int i = 0; i < wants; i++) {
            if (wantFile[i].equals(file)) {
                wantGain[i] += gain;
                return;
            }
        }
        if (wants == wantFile.length) return;
        wantFile[wants] = file;
        wantGain[wants++] = gain;
    }

    void cutoff(float hz) {
        cutoff = hz;
    }

    /** Hands this tick's wants to the mixer: known files get their gains, new ones a free slot, the rest fade out. */
    void commit(Minecraft mc) {
        if (sound != null && sound.isStopped()) sound = null;
        if (sound == null) {
            if (wants == 0) return;
            sound = new Instance();
            mc.getSoundManager().play(sound);
        }
        AirMixer m = sound.mixer;
        for (int s = 0; s < AirMixer.SLOTS; s++) {
            Identifier f = m.file(s);
            float g = 0F;
            for (int i = 0; f != null && i < wants; i++) {
                if (f.equals(wantFile[i])) {
                    g = wantGain[i];
                    wantFile[i] = null; // served
                }
            }
            m.set(s, g, FADE_SECONDS);
        }
        for (int i = 0; i < wants; i++) {
            if (wantFile[i] == null) continue;
            byte[] data = Soundscapes.bytes(wantFile[i]);
            if (data == null) continue; // loading; asked again next tick
            for (int s = 0; s < AirMixer.SLOTS; s++) {
                if (m.free(s)) {
                    m.assign(s, wantFile[i], data);
                    m.set(s, wantGain[i], FADE_SECONDS);
                    break;
                }
            }
        }
        m.cutoff(cutoff);
        m.publish();
        quiet = wants == 0 && m.silent() ? quiet + 1 : 0;
        if (quiet > QUIET_TICKS) stop();
        wants = 0;
    }

    void stop() {
        if (sound != null) sound.end();
        sound = null;
        quiet = 0;
    }

    String status() {
        if (sound == null) return "air: off";
        StringBuilder sb = new StringBuilder("air:");
        AirMixer m = sound.mixer;
        for (int s = 0; s < AirMixer.SLOTS; s++) {
            if (m.file(s) != null && m.shown(s) > 0.001F) sb.append(String.format(java.util.Locale.ROOT, " %s %.2f", m.file(s).getPath(), m.shown(s)));
        }
        return sb.append(String.format(java.util.Locale.ROOT, ", filter %.0f Hz", m.shownCutoff())).toString();
    }

    /** The stream's instance: started silent, never looping by itself (the mixer loops its layers). */
    private static final class Instance extends AbstractTickableSoundInstance {
        final AirMixer mixer = new AirMixer();

        Instance() {
            super(AudioSounds.AMB_WORLD.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.looping = false;
            this.delay = 0;
            this.volume = 1F;
            this.x = this.y = this.z = 0;
        }

        void end() {
            stop();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            // gains live in the mixer; the Ambient slider comes from the engine
        }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
            return CompletableFuture.completedFuture(mixer);
        }
    }
}
