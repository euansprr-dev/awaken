package dev.awaken.core.audio.client;

import dev.awaken.Awaken;
import dev.awaken.core.audio.LayerManifest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

/**
 * The sound instance a layered context plays through: not in the world (relative, at the origin, no attenuation, so
 * room-acoustics mods such as Sound Physics Remastered leave it dry), on the slot's source (Music or Ambient, so its
 * slider applies), started silently and never looping by itself. Its audio is a {@link StemMixer} built from the
 * context's manifest on an IO thread (NeoForge's per-instance stream hook), carrying the first targets so the very
 * first buffers already fade in.
 */
final class LayeredSound extends AbstractTickableSoundInstance {
    private final LayerManifest manifest;
    private final float[] firstTarget, firstSeconds;
    private volatile StemMixer mixer;
    private volatile boolean failed;

    LayeredSound(SoundEvent event, SoundSource source, LayerManifest manifest, float[] firstTarget, float[] firstSeconds) {
        super(event, source, SoundInstance.createUnseededRandom());
        this.manifest = manifest;
        this.firstTarget = firstTarget.clone();
        this.firstSeconds = firstSeconds.clone();
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.looping = false;
        this.delay = 0;
        this.volume = 1F;
        this.x = this.y = this.z = 0;
    }

    StemMixer mixer() {
        return mixer;
    }

    boolean failed() {
        return failed;
    }

    LayerManifest manifest() {
        return manifest;
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
        // gains live in the mixer; the slider and the place's music volume come from the engine
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                StemMixer m = StemMixer.open(manifest, Minecraft.getInstance().getResourceManager());
                for (int k = 0; k < m.stems() && k < firstTarget.length; k++) m.set(k, firstTarget[k], firstSeconds[k]);
                m.publish();
                mixer = m;
                return (AudioStream) m;
            } catch (IOException | RuntimeException e) {
                failed = true;
                Awaken.LOG.warn("[audio] layers {}: can't open the stems: {}", manifest.id(), e.toString());
                return Silence.INSTANCE;
            }
        }, Util.nonCriticalIoPool());
    }

    /** A stream that ends at once (the channel plays nothing and frees itself). */
    private static final class Silence implements AudioStream {
        static final Silence INSTANCE = new Silence();
        private static final AudioFormat FORMAT = new AudioFormat(44100, 16, 2, true, false);

        @Override
        public AudioFormat getFormat() {
            return FORMAT;
        }

        @Override
        public ByteBuffer read(int expectedSize) {
            return ByteBuffer.allocateDirect(0);
        }

        @Override
        public void close() {}
    }
}
