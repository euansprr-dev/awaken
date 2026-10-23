package dev.awaken.core.audio.client;

import dev.awaken.Awaken;
import dev.awaken.core.audio.AudioSounds;
import dev.awaken.core.audio.Cue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.client.event.SelectMusicEvent;

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
        if (mm.getCurrentMusicTranslationKey() != null) {
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
     * While our cue plays somewhere whose own music is "none", keep it: vanilla would otherwise stop the song the
     * moment it saw no situational music. Once it ends, the place's own music (or silence) takes over.
     */
    static void onSelectMusic(SelectMusicEvent e) {
        if (playingMusic == null || e.getMusic() != null) return;
        SoundInstance now = e.getPlayingMusic();
        if (now != null && now.getIdentifier().equals(AudioSounds.cue(playingCue).getId())
                && Minecraft.getInstance().getSoundManager().isActive(now)) {
            e.setMusic(playingMusic);
        }
    }

    static Cue playing() {
        return playingCue;
    }

    static void clear() {
        pending = null;
        playingCue = null;
        playingMusic = null;
    }

    private static void log(Cue cue, String what) {
        Awaken.LOG.info("[audio] cue {} {}", cue.id, what);
    }
}
