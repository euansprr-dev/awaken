package dev.awaken.delve.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * One loop of the senses on the Ambient slider: at a spot (the breath at the way out, the far water) or round the
 * listener (the deep's hum), eased toward the goal its driver sets each tick, and stopping itself once silent. Its
 * level is set by the driver (no attenuation; a placed loop's position pans it and Sound Physics gives it the cave).
 */
final class SenseLoop extends AbstractTickableSoundInstance {
    float goal, level, pitchGoal;
    int age;
    private final float ease;

    SenseLoop(SoundEvent event, boolean around, double x, double y, double z, float pitch, float ease) {
        super(event, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
        this.looping = true;
        this.delay = 0;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.relative = around;
        this.volume = 0.001F;
        this.pitch = this.pitchGoal = pitch;
        this.x = x;
        this.y = y;
        this.z = z;
        this.ease = ease;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        age++;
        level += (goal - level) * ease;
        volume = Math.max(0.001F, level);
        pitch += (pitchGoal - pitch) * 0.02F;
        if (goal < 0.004F && level < 0.004F) stop();
    }

    /** Fades it out; it stops itself once silent. */
    void release() {
        goal = 0F;
    }

    /** Stops it at once (leaving the world). */
    void end() {
        stop();
    }
}
