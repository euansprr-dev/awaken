package dev.awaken.citizens.client;

import dev.awaken.citizens.CityAudioBudget;
import dev.awaken.core.audio.client.Soundscape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import org.jspecify.annotations.Nullable;

/** City-owned sounds share a quiet budget, independent of the number of nearby emitters. */
final class CityAudio {
    private CityAudio() {}
    private static final CityAudioBudget BUDGET = new CityAudioBudget();
    private static final SoundInstance[] PLAYING = new SoundInstance[CityAudioBudget.VOICES];
    private static final long[] STARTED = new long[PLAYING.length];
    private static ClientLevel world;
    private static long clock = Long.MIN_VALUE;
    private static boolean indoors;

    static void init() { Soundscape.register(() -> count() + CitySound.voices()); }

    static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (world != level || level != null && level.getGameTime() < clock) {
            clear();
            CitySound.clear();
            CityNature.clear();
            world = level;
        }
        if (level == null || mc.getCameraEntity() == null) return;
        clock = level.getGameTime();
        if (clock % 10 == 0) indoors = !level.canSeeSky(mc.getCameraEntity().blockPosition().above());
        for (int i = 0; i < PLAYING.length; i++) {
            if (PLAYING[i] != null && clock - STARTED[i] > 20 && !mc.getSoundManager().isActive(PLAYING[i])) PLAYING[i] = null;
        }
    }

    static void onSound(PlaySoundEvent e) {
        SoundInstance sound = e.getSound();
        if (sound == null || sound instanceof TickableSoundInstance) return;
        Identifier id = sound.getIdentifier();
        if (!id.getNamespace().equals("awaken") || !id.getPath().startsWith("city.")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getCameraEntity() == null
                || mc.level.dimension() != net.minecraft.world.level.Level.OVERWORLD) { e.setSound(null); return; }
        // Villages reuse several city recordings, but keep their existing emitter rules.
        if (!CityLife.inside(sound.getX(), sound.getZ(), 24)) return;
        int group = CityAudioBudget.group(id.getPath());
        if (!BUDGET.accept(group, mc.level.getGameTime(), count())) { e.setSound(null); return; }
        // A warning peal has priority, without piling on top of three market/animal sounds.
        if (group == 0) stopVoices();
        SoundInstance mixed = new Gain(sound, insideGain(sound.getX(), sound.getY(), sound.getZ()));
        track(mixed);
        e.setSound(mixed);
    }

    static void playNature(SoundInstance sound) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !BUDGET.accept(2, mc.level.getGameTime(), count())) return;
        track(sound);
        mc.getSoundManager().play(sound);
    }

    private static void track(SoundInstance sound) {
        for (int i = 0; i < PLAYING.length; i++) if (PLAYING[i] == null) {
            PLAYING[i] = sound;
            STARTED[i] = Minecraft.getInstance().level.getGameTime();
            return;
        }
    }

    private static int count() {
        int n = 0;
        for (SoundInstance sound : PLAYING) if (sound != null) n++;
        return n;
    }

    static float outdoorGain() { return indoors ? 0.3F : 1F; }

    static float insideGain(double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        if (!indoors || mc.level == null || mc.getCameraEntity() == null) return 1F;
        // A tavern/forge in the same room remains audible; roofs hush the world outside.
        if (mc.getCameraEntity().distanceToSqr(x, y, z) < 8 * 8
                && !mc.level.canSeeSky(BlockPos.containing(x, y, z))) return 0.8F;
        return 0.3F;
    }

    private static void stopVoices() {
        for (int i = 0; i < PLAYING.length; i++) {
            if (PLAYING[i] != null) Minecraft.getInstance().getSoundManager().stop(PLAYING[i]);
            PLAYING[i] = null;
        }
    }

    static void clear() { stopVoices(); BUDGET.clear(); world = null; clock = Long.MIN_VALUE; indoors = false; }

    /** One-shots only: delegates the original sound's variant resolution and playback permissions. */
    private record Gain(SoundInstance sound, float gain) implements SoundInstance {
        public Identifier getIdentifier() { return sound.getIdentifier(); }
        public @Nullable WeighedSoundEvents getOrResolve(SoundManager m) { return sound.getOrResolve(m); }
        public @Nullable Sound getSound() { return sound.getSound(); }
        public @Nullable WeighedSoundEvents getSoundEvent() { return sound.getSoundEvent(); }
        public SoundSource getSource() { return sound.getSource(); }
        public boolean isLooping() { return sound.isLooping(); }
        public boolean isRelative() { return sound.isRelative(); }
        public int getDelay() { return sound.getDelay(); }
        public float getVolume() { return sound.getVolume() * gain; }
        public float getPitch() { return sound.getPitch(); }
        public double getX() { return sound.getX(); }
        public double getY() { return sound.getY(); }
        public double getZ() { return sound.getZ(); }
        public Attenuation getAttenuation() { return sound.getAttenuation(); }
        public boolean canStartSilent() { return sound.canStartSilent(); }
        public boolean canPlaySound() { return sound.canPlaySound(); }
        public java.util.concurrent.CompletableFuture<net.minecraft.client.sounds.AudioStream> getStream(
                net.minecraft.client.sounds.SoundBufferLibrary buffers, Sound chosen, boolean looping) {
            return sound.getStream(buffers, chosen, looping);
        }
    }
}
