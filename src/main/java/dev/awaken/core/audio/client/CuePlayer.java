package dev.awaken.core.audio.client;

import dev.awaken.Awaken;
import dev.awaken.core.audio.AudioSounds;
import dev.awaken.core.audio.Cue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import org.jspecify.annotations.Nullable;

/**
 * Plays music cues through vanilla's {@link MusicManager}, so a cue is a song as far as the game is concerned: it
 * never starts on top of another song, the Now Playing toast names it, the Music slider and the place's music volume
 * apply, and when it ends vanilla's own long silence follows (the next song waits its full delay).
 *
 * <p>One request waits at a time (a stage cue replaces an ordinary one). It starts once its delay has passed, the
 * level has been loaded for two seconds (a teleport stops every sound), and the music slot is free; a stage cue may
 * stop the song that is playing, an ordinary one waits for it to end and lets the moment pass when its window closes.
 * Nothing plays where the music volume is zero (the Abyssal Shrine) or when the Music slider is off.
 */
final class CuePlayer {
    private CuePlayer() {}

    private record Pending(Cue cue, int at, int until) {}

    private static Pending pending;
    private static Cue playingCue;
    /** The music manager's song as of its last {@link SelectMusicEvent} (it asks no one while it fades its gain). */
    private static @Nullable SoundInstance lastSong;
    private static Music playingMusic;
    private static ClientLevel lastLevel;
    private static int ticks, levelAge;

    static void request(Cue cue) {
        if (cue == null) return;
        if (pending != null && pending.cue.stage && !cue.stage) {
            log(cue, "skipped: a stage cue is waiting");
            return;
        }
        pending = new Pending(cue, ticks + cue.delayTicks, ticks + cue.delayTicks + cue.waitTicks);
    }

    static void tick() {
        ticks++;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            levelAge = 0;
        } else {
            levelAge++;
        }
        MusicManager mm = mc.getMusicManager();
        if (playingMusic != null && !mm.isPlayingMusic(playingMusic)) {
            playingMusic = null;
            playingCue = null;
        }
        Pending p = pending;
        if (p == null || ticks < p.at) return;
        if (mc.level == null || mc.player == null || ticks > p.until) {
            pending = null;
            log(p.cue, "let pass: the music slot never came free");
            return;
        }
        if (levelAge < 40 || mc.gui.screen() instanceof LevelLoadingScreen) return;
        if (mc.getMusicVolume() <= 0.001F) {
            pending = null;
            log(p.cue, "skipped: this place keeps its silence");
            return;
        }
        if (mc.options.getFinalSoundSourceVolume(SoundSource.MUSIC) <= 0F) {
            pending = null;
            log(p.cue, "skipped: music is off");
            return;
        }
        // the layered music holds the channel: a stage cue makes it fade, an ordinary one waits unless it is calm
        if (!LayerPlayer.MUSIC.yieldTo(p.cue)) return;
        if (songActive()) {
            if (!p.cue.stage) return; // wait for the song to end
            mm.stopPlaying();
        }
        pending = null;
        // long delays: if the game ever asks this cue's Music for the next song's delay, the answer is a long silence
        Music music = new Music(AudioSounds.cue(p.cue), 24000, 24000, false);
        mm.startPlaying(music);
        playingCue = p.cue;
        playingMusic = music;
        log(p.cue, "started");
    }

    /**
     * Who has the music channel, every tick, before vanilla's music manager decides:
     * <ol>
     *   <li>our cue, while it plays (somewhere whose own music is "none" vanilla would otherwise stop it the moment
     *   it saw no situational music; once it ends the place's own music, or silence, takes over);</li>
     *   <li>the layered music, while it sounds: vanilla is told there is no situational music, so no song starts on
     *   top of it (and one that was playing has already been faded out);</li>
     *   <li>otherwise vanilla, as always.</li>
     * </ol>
     */
    static void onSelectMusic(SelectMusicEvent e) {
        SoundInstance now = e.getPlayingMusic();
        lastSong = now;
        if (playingMusic != null && now != null && now.getIdentifier().equals(AudioSounds.cue(playingCue).getId())
                && Minecraft.getInstance().getSoundManager().isActive(now)) {
            if (e.getMusic() == null) e.setMusic(playingMusic);
            return;
        }
        if (LayerPlayer.MUSIC.ownsChannel()) e.setMusic(null);
    }

    static Cue playing() {
        return playingCue;
    }

    /**
     * Whether the music manager has a song that really sounds. A teleport stops every sound without telling it, and
     * while it fades its gain it doesn't look, so it can hold a dead song for seconds: ask the engine about the song
     * it last named. One it started after asking (another key) is new, so it sounds.
     */
    static boolean songActive() {
        Minecraft mc = Minecraft.getInstance();
        String key = mc.getMusicManager().getCurrentMusicTranslationKey();
        if (key == null) return false;
        SoundInstance s = lastSong;
        Sound sound = s == null ? null : s.getSound();
        if (sound == null || !key.equals(sound.getLocation().toShortLanguageKey())) return true;
        return mc.getSoundManager().isActive(s);
    }

    static boolean playingStage() {
        return playingCue != null && playingCue.stage;
    }

    static void clear() {
        pending = null;
        playingCue = null;
        playingMusic = null;
        lastSong = null;
    }

    private static void log(Cue cue, String what) {
        Awaken.LOG.info("[audio] cue {} {}", cue.id, what);
    }
}
