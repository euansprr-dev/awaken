package dev.awaken.powers.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * A power's sound with a place that changes (SPEC §50.7): a rumble rolling away along a wave, a boom going off down a Railgun's line,
 * or a well's throb that holds where it opened for as long as it lives and then fades. Stops on its own; at most {@link #SLOTS}.
 */
final class TravellingSound extends AbstractTickableSoundInstance {
    private static final int SLOTS = 12;
    private static final TravellingSound[] PLAYING = new TravellingSound[SLOTS];
    private static final int FADE_OUT = 5;

    private final double x0, y0, z0, x1, y1, z1;
    private final int ticks, fadeIn;
    private final float full;
    private int age;
    private int stopAt = -1;

    private TravellingSound(SoundEvent e, double x0, double y0, double z0, double x1, double y1, double z1, int ticks, float volume, boolean loop, int fadeIn) {
        super(e, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.x0 = x0;
        this.y0 = y0;
        this.z0 = z0;
        this.x1 = x1;
        this.y1 = y1;
        this.z1 = z1;
        this.ticks = Math.max(1, ticks);
        this.full = volume;
        this.fadeIn = fadeIn;
        this.looping = loop;
        this.delay = 0;
        this.x = x0;
        this.y = y0;
        this.z = z0;
        this.volume = fadeIn > 0 ? 0.01f : volume;
    }

    /** One shot whose source moves from one point to the other over {@code ticks}. */
    static void travel(SoundEvent e, double x0, double y0, double z0, double x1, double y1, double z1, int ticks, float volume) {
        play(new TravellingSound(e, x0, y0, z0, x1, y1, z1, ticks, volume, false, 0));
    }

    /** A loop held at a point for {@code ticks}, fading in and out. */
    static void hold(SoundEvent e, double x, double y, double z, int ticks, float volume) {
        TravellingSound s = new TravellingSound(e, x, y, z, x, y, z, ticks, volume, true, 6);
        s.stopAt = ticks;
        play(s);
    }

    private static void play(TravellingSound s) {
        for (int i = 0; i < SLOTS; i++) {
            if (PLAYING[i] == null || PLAYING[i].isStopped()) {
                PLAYING[i] = s;
                Minecraft.getInstance().getSoundManager().play(s);
                return;
            }
        }
    }

    /** Ends the held loop nearest this point (the well closed early, or on time). */
    static void release(double x, double y, double z) {
        for (TravellingSound s : PLAYING) {
            if (s != null && !s.isStopped() && s.looping && s.distanceSqr(x, y, z) < 1 && (s.stopAt < 0 || s.stopAt > s.age)) s.stopAt = s.age;
        }
    }

    private double distanceSqr(double x, double y, double z) {
        double dx = x0 - x, dy = y0 - y, dz = z0 - z;
        return dx * dx + dy * dy + dz * dz;
    }

    static void reset() {
        for (int i = 0; i < SLOTS; i++) {
            if (PLAYING[i] != null && !PLAYING[i].isStopped()) PLAYING[i].stop();
            PLAYING[i] = null;
        }
    }

    @Override
    public void tick() {
        age++;
        float t = Math.min(1f, age / (float) ticks);
        x = x0 + (x1 - x0) * t;
        y = y0 + (y1 - y0) * t;
        z = z0 + (z1 - z0) * t;
        if (looping) {
            float v = fadeIn > 0 ? Math.min(1f, age / (float) fadeIn) : 1f;
            if (stopAt >= 0 && age >= stopAt) {
                v *= Math.max(0f, 1f - (age - stopAt) / (float) FADE_OUT);
                if (age - stopAt >= FADE_OUT) {
                    stop();
                    return;
                }
            }
            volume = full * v;
        }
    }
}
