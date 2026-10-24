package dev.awaken.body.ascent.client;

import dev.awaken.body.ascent.AscentContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * The Ascent's sounds on this client. One-offs play at once (predicted: they start on your input, never a round trip
 * later); the wind is a loop that follows your speed, rising in pitch and loudness, and goes silent for the moment
 * before a sonic boom's crack.
 */
public final class AscentSounds {
    private AscentSounds() {}

    private static Wind wind;
    /** Ticks the wind stays held under (the boom's silence). */
    private static int duck;

    public static void local(Entity e, SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        mc.level.playLocalSound(e.getX(), e.getY() + 1, e.getZ(), sound, SoundSource.PLAYERS, volume, pitch, false);
    }

    public static void at(Entity e, double x, double y, double z, SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        mc.level.playLocalSound(x, y, z, sound, SoundSource.PLAYERS, volume, pitch, false);
    }

    /** The wind drops out for this many ticks (a boom's silence before its crack). */
    public static void duck(int ticks) {
        duck = Math.max(duck, ticks);
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (duck > 0) duck--;
        double s = AscentMoves.speed();
        boolean want = s > 0.25 && mc.player != null;
        if (want && (wind == null || wind.isStopped())) {
            wind = new Wind();
            mc.getSoundManager().play(wind);
        }
    }

    static float duckLevel() {
        return duck > 0 ? 0F : 1F;
    }

    /** The rush of air past your ears: louder and higher with speed, gone a moment after you stop. */
    static final class Wind extends AbstractTickableSoundInstance {
        private float level;

        Wind() {
            super(AscentContent.WIND.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.volume = 0.001F;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.x = this.y = this.z = 0;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            double s = AscentMoves.speed();
            float norm = (float) Mth.clamp((s - 0.25) / 2.4, 0, 1);
            float target = (float) Math.pow(norm, 1.25) * 0.95F * duckLevel();
            // the silence before a crack is sudden; everything else eases
            level += (target - level) * (target < level && duckLevel() == 0 ? 0.6F : 0.18F);
            volume = Math.max(0.0005F, level);
            pitch = 0.72F + 0.62F * norm;
            if (AscentMoves.speed() < 0.05 && level < 0.002F) stop();
        }
    }
}
