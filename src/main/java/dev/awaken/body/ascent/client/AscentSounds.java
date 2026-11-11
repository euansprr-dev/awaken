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

    /**
     * One of the new moves' own sounds (SPEC §62: sling, spring, flare, skim, takeoff, strike, beat): its score, or with
     * the scores off the old sound nearest to it ({@code classic}, null for none).
     */
    public static void move(Entity e, String score, @org.jspecify.annotations.Nullable SoundEvent classic, float volume, float pitch) {
        if (AscentScores.play(score, e.getX(), e.getY() + 1, e.getZ(), volume, pitch)) return;
        if (classic != null) local(e, classic, volume, pitch);
    }

    /** The wind drops out for this many ticks (a boom's silence before its crack). */
    public static void duck(int ticks) {
        duck = Math.max(duck, ticks);
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (duck > 0) duck--;
        double s = AscentMoves.windSpeed();
        boolean want = s > 0.25 && mc.player != null;
        if (want && (wind == null || wind.isStopped())) {
            wind = new Wind();
            mc.getSoundManager().play(wind);
        }
        // the rope's hum through the let-go window (SPEC §62): the moment can be heard as well as seen
        if (AscentScores.on() && AscentMoves.windowNow > 0.05F && mc.player != null && (hum == null || hum.isStopped())) {
            hum = new Hum();
            mc.getSoundManager().play(hum);
        }
        // the rope paying out through the hand: its own loop for each line (a rasp of bites; the Deepglass line's glassy run)
        boolean glass = AscentMoves.smoothLine();
        if (AscentMoves.payNow > 0 && mc.player != null && (pay == null || pay.isStopped() || pay.glass != glass)) {
            if (pay != null) pay.end();
            pay = new Pay(glass);
            mc.getSoundManager().play(pay);
        }
    }

    private static Pay pay;
    private static Hum hum;

    /** The taut rope singing in the air as a swing comes into its let-go window: higher and nearer as the moment nears. */
    static final class Hum extends AbstractTickableSoundInstance {
        private float level;

        Hum() {
            super(net.minecraft.sounds.SoundEvent.createVariableRangeEvent(AscentScores.loop("hum")), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
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
            float w = AscentMoves.state() == dev.awaken.body.ascent.AscentState.TETHER ? AscentMoves.windowNow : 0;
            level += (w - level) * (w > level ? 0.45F : 0.2F);
            volume = Math.max(0.0005F, 0.5F * level);
            pitch = 1.45F + 0.5F * level;
            if (level < 0.01F && w <= 0.01F) stop();
        }
    }

    /** The rope running out through the hand while it pays out, at the hand, fading a moment after it stops. */
    static final class Pay extends AbstractTickableSoundInstance {
        final boolean glass;
        private float level;
        private boolean ending;

        Pay(boolean glass) {
            super((glass ? AscentContent.RAPPEL_PAY_GLASS : AscentContent.RAPPEL_PAY).get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.glass = glass;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.001F;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.x = this.y = this.z = 0;
        }

        void end() {
            ending = true;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            float rate = (float) Mth.clamp(AscentMoves.payNow / dev.awaken.body.ascent.AscentRules.RAPPEL_SMOOTH, 0, 1.2);
            float target = ending ? 0F : 0.55F * rate;
            level += (target - level) * (target > level ? 0.5F : 0.25F);
            volume = Math.max(0.0005F, level);
            pitch = (glass ? 1.0F : 0.92F) + 0.12F * rate;
            if (level < 0.004F && (ending || AscentMoves.payNow <= 0)) stop();
        }
    }

    static float duckLevel() {
        return duck > 0 ? 0F : 1F;
    }

    /** The rush of air past your ears: louder and higher with speed, gone a moment after you stop. */
    static final class Wind extends AbstractTickableSoundInstance {
        private float level;

        Wind() {
            // with the scores on, the air's own loop (the game's recording of it) instead of the built one
            super(AscentScores.on() ? net.minecraft.sounds.SoundEvent.createVariableRangeEvent(AscentScores.loop("wind")) : AscentContent.WIND.get(),
                    SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
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
            double s = AscentMoves.windSpeed();
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
