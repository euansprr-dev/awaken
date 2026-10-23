package dev.awaken.core.audio.client;

import dev.awaken.core.CoreModule;
import dev.awaken.core.audio.AudioSounds;
import dev.awaken.core.audio.UiSound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

import java.util.Set;

/**
 * The System's UI sounds on the client, one family (tools/art/specs/audio_ui.py), and the rules that keep it subtle:
 * the generic toast chime stays quiet when a more specific System sound (level up, rank up, a quest done, a request
 * delivered, a warning, an answer) has just played, so a moment never sounds twice; the Now tracker's tick plays only
 * for a clear next step, never in the first seconds of a session or a new place, and at most every 30 seconds.
 * Volumes here are on top of the files' own mastering (audio_mix.py); the Interface slider applies.
 */
public final class AudioUi {
    private AudioUi() {}

    /** A toast chime this soon after a specific System sound is dropped (ticks). */
    static final int QUIET_AFTER_SPECIFIC = 50;
    static final int NOW_GAP = 600, NOW_SETTLE = 200, NOW_MIN_PRIORITY = 50;

    private static final Set<String> SPECIFIC = Set.of("level_up", "rank_up", "signal.warning", "ui.quest_done",
            "ui.request_delivered", "ui.answer_yes", "ui.answer_no", "hearth.request_complete");

    private static int ticks;
    private static int lastSpecific = -10_000, lastNow = -10_000, settledAt;
    private static Object lastLevel;

    /** A System toast appeared (SystemHud). */
    public static void toast() {
        if (ticks - lastSpecific < QUIET_AFTER_SPECIFIC) return;
        play(CoreModule.SIGNAL_CHIME.get(), 0.3F);
    }

    /** The player answered a choice card (SystemHud). */
    public static void answer(boolean yes) {
        play((yes ? AudioSounds.UI_ANSWER_YES : AudioSounds.UI_ANSWER_NO).get(), 0.45F);
    }

    /** The Signal's voice begins a line (SystemHud). */
    public static void story() {
        play(AudioSounds.UI_STORY.get(), 0.4F);
    }

    /** The Now tracker's top suggestion changed (NowClient); {@code priority} of the new one, 0 when none. */
    public static void nowChanged(int priority) {
        if (priority < NOW_MIN_PRIORITY || ticks - settledAt < NOW_SETTLE || ticks - lastNow < NOW_GAP) return;
        if (ticks - lastSpecific < QUIET_AFTER_SPECIFIC) return;
        lastNow = ticks;
        play(AudioSounds.UI_NOW.get(), 0.22F);
    }

    /** A UI sound the server asked for. */
    static void server(UiSound s) {
        if (s != null) play(s.event().get(), s.volume);
    }

    static void play(SoundEvent sound, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F, volume));
    }

    static void tick() {
        ticks++;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            settledAt = ticks;
        }
    }

    /** Notes when a specific System sound starts, whoever played it (server-side level-ups play in the world). */
    static void onPlaySound(PlaySoundEvent e) {
        Identifier id = e.getOriginalSound().getIdentifier();
        if (id.getNamespace().equals("awaken") && SPECIFIC.contains(id.getPath())) lastSpecific = ticks;
    }
}
