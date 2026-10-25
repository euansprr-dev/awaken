package dev.awaken.opening.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The opening's sounds on the client: on the listener (the card, the bell's body) or placed in the city. */
final class OpeningSounds {
    private OpeningSounds() {}

    private static final RandomSource RANDOM = RandomSource.create();

    static void ui(SoundEvent e, float pitch, float vol) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(e, pitch, vol));
    }

    static void at(SoundEvent e, Vec3 p, float vol, float pitch) {
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(e, SoundSource.AMBIENT, vol, pitch, RANDOM, p.x, p.y, p.z));
    }

    /** A bed of sound on the listener that fades in and out (the wind over the city, the infirmary's quiet). */
    static final class Bed extends AbstractTickableSoundInstance {
        private final float peak;
        private final int fadeIn, fadeOut;
        private int age, ending = -1;

        Bed(SoundEvent e, float peak, float pitch, int fadeIn, int fadeOut) {
            super(e, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.peak = peak;
            this.pitch = pitch;
            this.fadeIn = Math.max(1, fadeIn);
            this.fadeOut = Math.max(1, fadeOut);
            this.looping = true;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 0.001F;
        }

        void end() {
            if (ending < 0) ending = 0;
        }

        /** The bed's level now (0..1 of its peak), for a shot that swells it. */
        float gain = 1F;

        @Override
        public void tick() {
            age++;
            float in = Math.min(1F, age / (float) fadeIn);
            float out = 1F;
            if (ending >= 0) {
                ending++;
                out = Math.max(0F, 1F - ending / (float) fadeOut);
                if (out <= 0F) {
                    stop();
                    return;
                }
            }
            volume = Math.max(0.001F, peak * in * out * gain);
        }
    }

    static @Nullable Bed bed(SoundEvent e, float peak, float pitch, int fadeIn, int fadeOut) {
        Bed b = new Bed(e, peak, pitch, fadeIn, fadeOut);
        Minecraft.getInstance().getSoundManager().play(b);
        return b;
    }
}
