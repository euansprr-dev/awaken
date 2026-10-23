package dev.awaken.veil.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.function.BooleanSupplier;

/** A looping, non-positional sound that fades in while its condition holds and fades out after. */
final class LoopSound extends AbstractTickableSoundInstance {
    private final BooleanSupplier active;
    private final float max;
    private float level;

    LoopSound(SoundEvent event, BooleanSupplier active, float maxVolume, float pitch) {
        super(event, SoundSource.AMBIENT, RandomSource.create());
        this.active = active;
        this.max = maxVolume;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0001F;
        this.pitch = pitch;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
    }

    @Override
    public void tick() {
        level = active.getAsBoolean() ? Math.min(1F, level + 0.04F) : level - 0.06F;
        if (level <= 0F) {
            stop();
            return;
        }
        volume = Math.max(0.0001F, level * max);
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
