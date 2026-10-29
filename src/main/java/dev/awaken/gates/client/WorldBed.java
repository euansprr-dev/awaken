package dev.awaken.gates.client;

import dev.awaken.gates.net.WorldPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * A world floor's sound bed (SPEC §38.7): one quiet loop of the world's own sound, all round you, fading in over three
 * seconds when you arrive (from the start of the journey down) and out over two when you leave or the world changes.
 * Worlds without a sound, or whose sound isn't in the pack, play nothing.
 */
final class WorldBed {
    private WorldBed() {}

    static final int IN = 60, OUT = 40;
    static final float LEVEL = 0.55F;

    private static Bed playing;
    private static String cued = "", missing = "";
    private static int hushed;

    /** The bed drains away for a while (the Silence) and comes back. */
    static void hush(int ticks) {
        hushed = ticks;
    }

    /** The journey down starts the next world's bed early, so it's there as the dark lifts. */
    static void cue(String sound) {
        cued = sound;
        swap(sound);
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            stop();
            return;
        }
        WorldPayloads.Arrival a = GateWorldClient.current();
        if (hushed > 0) hushed--;
        String want = hushed > 0 ? "" : WorldJourney.dark() ? cued : a != null ? a.sound() : "";
        if (playing != null && playing.age > 5 && (playing.isStopped() || !mc.getSoundManager().isActive(playing))) playing = null;
        if (!want.equals(playing == null ? "" : playing.id)) swap(want);
    }

    private static void swap(String sound) {
        if (playing != null && playing.id.equals(sound)) return;
        if (playing != null) playing.end();
        playing = null;
        if (sound.isEmpty() || sound.equals(missing)) return;
        Identifier id = Identifier.tryParse(sound);
        Minecraft mc = Minecraft.getInstance();
        if (id == null || mc.getSoundManager().getSoundEvent(id) == null) {
            missing = sound; // not in the pack: don't look again every tick
            return;
        }
        playing = new Bed(sound, SoundEvent.createVariableRangeEvent(id));
        mc.getSoundManager().play(playing);
    }

    static void stop() {
        if (playing != null) playing.end();
        playing = null;
        hushed = 0;
    }

    /** The loop: eased in, eased out, never positional. */
    static final class Bed extends AbstractTickableSoundInstance {
        final String id;
        int age;
        private boolean ending;
        private float level;

        Bed(String id, SoundEvent event) {
            super(event, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.id = id;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.001F;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        void end() {
            ending = true;
        }

        @Override
        public void tick() {
            age++;
            level = ending ? level - 1F / OUT : Math.min(1, level + 1F / IN);
            if (ending && level <= 0) {
                stop();
                return;
            }
            float k = Math.max(0, level);
            volume = Math.max(0.001F, LEVEL * k * k);
        }
    }
}
