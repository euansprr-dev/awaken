package dev.awaken.holo.client;

import dev.awaken.Awaken;
import dev.awaken.holo.HoloSounds;
import dev.awaken.signal.client.HudLook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/**
 * The holo panels' sounds (docs/modules/holo.md "Sounds"), one glass voice with the System's UI family (D major
 * pentatonic, so nothing here ever clashes with a toast or a card): the K menu's unfold and the inventory's shorter one,
 * the fold back into the band, a tick as the cursor comes onto something, and a System card's arrival while the
 * projected look is on. Non-positional on the Interface slider; volumes are on top of the files' own mastering
 * (audio_mix.py), the inventory's a little under the K menu's because it opens far more often.
 */
final class HoloAudio {
    private HoloAudio() {}

    private static final Identifier SIGNAL_OPEN = Awaken.id("signal.open");

    static void open(boolean inventory) {
        play(inventory ? HoloSounds.OPEN_QUICK.get() : HoloSounds.OPEN.get(), inventory ? 0.30f : 0.50f);
    }

    static void close(boolean inventory) {
        play(HoloSounds.CLOSE.get(), inventory ? 0.22f : 0.40f);
    }

    static void hover() {
        play(HoloSounds.HOVER.get(), 0.18f);
    }

    /** D5 E5 F#5 A5 B5 D6: each point placed in a run plays one step higher, so a run of points climbs the scale. */
    private static final float[] STEPS = {1f, 1.1225f, 1.2599f, 1.4983f, 1.6818f, 2f};
    private static int run;

    /** The band's own moments (SPEC §30.4), told by its sections through {@code StatusSections.cue}. */
    static void cue(dev.awaken.progression.client.StatusSections.Cue c) {
        var mc = Minecraft.getInstance();
        switch (c) {
            case PAGE -> play(HoloSounds.PAGE.get(), 0.16f);
            case SPEND -> {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(HoloSounds.SPEND.get(), STEPS[Math.min(run, STEPS.length - 1)], 0.20f));
                run++;
            }
            case UNDO -> {
                run = Math.max(0, run - 1);
                play(HoloSounds.UNDO.get(), 0.16f);
            }
            case CONFIRM -> {
                run = 0;
                play(HoloSounds.CONFIRM.get(), 0.30f);
            }
        }
    }

    /**
     * Every sound about to play: a System card's arrival ({@code signal.open}, played by the signal module) takes the
     * projected card's voice while the projected look draws, so a card sounds the way it looks and never twice.
     */
    static void onPlaySound(PlaySoundEvent e) {
        SoundInstance s = e.getSound();
        if (s == null || !SIGNAL_OPEN.equals(s.getIdentifier()) || !HudLook.holo()) return;
        e.setSound(SimpleSoundInstance.forUI(HoloSounds.CARD.get(), 1.0f, 0.55f));
    }

    private static void play(SoundEvent sound, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0f, volume));
    }
}
