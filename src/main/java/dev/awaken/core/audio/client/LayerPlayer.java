package dev.awaken.core.audio.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.awaken.Awaken;
import dev.awaken.core.audio.AudioNet;
import dev.awaken.core.audio.AudioSounds;
import dev.awaken.core.audio.Cue;
import dev.awaken.core.audio.Intensity;
import dev.awaken.core.audio.LayerManifest;
import dev.awaken.core.audio.MusicLayers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Plays one slot of the layered music on the client (docs/modules/audio.md "Layered music"): the context the server
 * names, at the intensity it names, as one {@link LayeredSound} whose {@link StemMixer} crossfades the stems.
 *
 * <p>Music slot rules: it takes the music channel from vanilla (a song playing fades out in 1.5 s first; while the
 * layers sound, the music manager is told there is no situational music, so nothing doubles up) and gives it back
 * with a full vanilla delay. After {@value #REST_AFTER} ticks of calm the bed rests (fades in {@value #REST_FADE} s,
 * the channel goes back to vanilla), and returns at the next tension or a new floor. Cues: a stage cue takes the
 * channel from the layers; an ordinary one only while they are calm, never during tension, combat or a Lord. Exit
 * fades in {@value #EXIT_FADE} s (a teleport stops every sound anyway), death in {@value #DEATH_FADE} s, a Lord's fall
 * lets the Lord and combat stems go in {@value #VICTORY_FADE} s. Ambience slot: same player, no channel, no rest.
 */
final class LayerPlayer {
    static final LayerPlayer MUSIC = new LayerPlayer(MusicLayers.Slot.MUSIC);
    static final LayerPlayer AMBIENCE = new LayerPlayer(MusicLayers.Slot.AMBIENCE);

    static final int SETTLE = 40, REST_AFTER = 20 * 150, VANILLA_FADE = 30, END_TIMEOUT = 20 * 8, STATUS_EVERY = 100;
    static final float REST_FADE = 12F, EXIT_FADE = 4F, DEATH_FADE = 2.5F, VICTORY_FADE = 3F, YIELD_FADE = 3F, STAGE_YIELD_FADE = 1.5F;

    enum Phase { IDLE, FADING_VANILLA, PLAYING, ENDING, RESTING, YIELDED }

    private static final boolean AUTOPILOT = System.getProperty("awaken.autopilot") != null;

    final MusicLayers.Slot slot;
    // what the server wants
    private @Nullable Identifier want;
    private int wantLevel;
    private boolean arrival, victory, death;
    // what plays
    private Phase phase = Phase.IDLE;
    private @Nullable LayerManifest manifest;
    private @Nullable LayeredSound sound;
    private int level = -1, calm, phaseTicks, ticks, levelAge, retryAt;
    private boolean restFading, yielding;
    private float fadeOverride = -1F;
    private float[] tgt = new float[0], sec = new float[0];
    private float vanillaFrom = 1F, ownedGain = -1F;
    private @Nullable ClientLevel lastLevel;
    private final Map<Identifier, LayerManifest> manifests = new HashMap<>();
    private final Set<Identifier> broken = new HashSet<>();

    private LayerPlayer(MusicLayers.Slot slot) {
        this.slot = slot;
    }

    static LayerPlayer of(MusicLayers.Slot slot) {
        return slot == MusicLayers.Slot.MUSIC ? MUSIC : AMBIENCE;
    }

    /** The server's word for this slot. */
    void accept(AudioNet.LayerState msg) {
        Identifier id = msg.context().isEmpty() ? null : Identifier.tryParse(msg.context());
        if (id != null && !id.equals(want)) arrival = true;
        want = id;
        wantLevel = Math.max(Intensity.CALM, Math.min(Intensity.LORD, msg.level()));
        if ((msg.flags() & AudioNet.LayerState.ARRIVAL) != 0) arrival = true;
        if ((msg.flags() & AudioNet.LayerState.VICTORY) != 0) victory = true;
        if ((msg.flags() & AudioNet.LayerState.DEATH) != 0) death = true;
    }

    /** Whether the layers hold the music channel now (vanilla is told there is no situational music). */
    boolean ownsChannel() {
        return slot == MusicLayers.Slot.MUSIC && (phase == Phase.PLAYING || phase == Phase.ENDING);
    }

    @Nullable Identifier context() {
        return manifest == null ? null : manifest.id();
    }

    int level() {
        return level;
    }

    Phase phase() {
        return phase;
    }

    /**
     * A music cue wants the channel: true when the layers let it play now. A stage cue makes them fade out; an
     * ordinary one only while they are calm (the bed fades), never during tension, combat or a Lord.
     */
    boolean yieldTo(Cue cue) {
        if (slot != MusicLayers.Slot.MUSIC) return true;
        switch (phase) {
            case IDLE, RESTING, YIELDED:
                if (want != null && phase != Phase.YIELDED) setPhase(Phase.YIELDED, "a cue (" + cue.id + ") goes first");
                return true;
            case FADING_VANILLA:
                restoreGain();
                setPhase(Phase.YIELDED, "a cue (" + cue.id + ") goes first");
                return true;
            default:
                if (!cue.stage && wantLevel >= Intensity.TENSION) return false;
                if (!yielding) {
                    yielding = true;
                    fadeOverride = cue.stage ? STAGE_YIELD_FADE : YIELD_FADE;
                    retarget("yield to the " + cue.id + " cue");
                }
                StemMixer m = sound == null ? null : sound.mixer();
                if (m == null || m.silent()) {
                    stopSound();
                    setPhase(Phase.YIELDED, "yielded to the " + cue.id + " cue");
                    return true;
                }
                return false;
        }
    }

    void tick() {
        ticks++;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            levelAge = 0;
        } else {
            levelAge++;
        }
        if (mc.level == null || mc.player == null) {
            clear();
            return;
        }
        phaseTicks++;
        SoundManager sm = mc.getSoundManager();
        boolean dead = mc.player.isDeadOrDying();
        // the engine dropped our sound (a teleport stops every sound; an underrun ends a stream): start over later
        if (sound != null && (phase == Phase.PLAYING || phase == Phase.ENDING) && phaseTicks > 5 && !sm.isActive(sound)) {
            boolean failed = sound.failed();
            sound = null;
            if (failed && manifest != null) broken.add(manifest.id());
            retryAt = ticks + 100;
            setPhase(Phase.IDLE, failed ? "the stems failed to load" : "the sound stopped (teleport or underrun)");
        }
        switch (phase) {
            case IDLE -> {
                if (dead || want == null || broken.contains(want) || levelAge < SETTLE || ticks < retryAt
                        || mc.gui.screen() instanceof LevelLoadingScreen) break;
                if (slot == MusicLayers.Slot.MUSIC && CuePlayer.playing() != null) break; // a cue has the channel
                LayerManifest m = load(want);
                if (m == null) break;
                manifest = m;
                beginOrFade(mc);
            }
            case FADING_VANILLA -> {
                if (want == null || dead || CuePlayer.playingStage()) {
                    restoreGain();
                    setPhase(Phase.IDLE, "the vanilla fade was cut short");
                    break;
                }
                MusicManager mm = mc.getMusicManager();
                float g = vanillaFrom * Math.max(0F, 1F - phaseTicks / (float) VANILLA_FADE);
                sm.updateCategoryVolume(SoundSource.MUSIC, g);
                if (phaseTicks >= VANILLA_FADE || mm.getCurrentMusicTranslationKey() == null) {
                    mm.stopPlaying();
                    restoreGain();
                    start(mc);
                }
            }
            case PLAYING -> {
                if (dead || death) {
                    end(DEATH_FADE, "death");
                    break;
                }
                if (want == null || manifest == null || !want.equals(manifest.id())) {
                    end(EXIT_FADE, want == null ? "left the context" : "the context changed");
                    break;
                }
                keepGain(mc);
                int target = wantLevel;
                boolean was = restFading;
                if (slot == MusicLayers.Slot.MUSIC) {
                    if (target == Intensity.CALM && !mc.isPaused()) calm++;
                    else if (target > Intensity.CALM) calm = 0;
                    if (arrival) calm = 0;
                    restFading = calm >= REST_AFTER;
                }
                arrival = false;
                if (target != level || restFading != was || victory) {
                    int old = level;
                    level = target;
                    retarget(restFading && !was ? "the bed rests (" + REST_AFTER / 20 + " s of calm)"
                            : victory ? "the Lord fell" : old < 0 ? "start" : Intensity.name(old) + " -> " + Intensity.name(target), old);
                    victory = false;
                }
                if (restFading) {
                    StemMixer m = sound == null ? null : sound.mixer();
                    if (m != null && m.silent()) {
                        stopSound();
                        setPhase(Phase.RESTING, "rest: the channel goes back to the music manager");
                        releaseChannel(mc);
                    }
                }
                status();
            }
            case ENDING -> {
                keepGain(mc);
                StemMixer m = sound == null ? null : sound.mixer();
                if (m == null || m.silent() || phaseTicks > END_TIMEOUT) {
                    stopSound();
                    setPhase(Phase.IDLE, "ended");
                    releaseChannel(mc);
                }
            }
            case RESTING, YIELDED -> {
                if (want == null || dead || manifest == null || !want.equals(manifest.id())) {
                    setPhase(Phase.IDLE, "rest over: " + (want == null ? "left the context" : "another context"));
                    break;
                }
                if (phase == Phase.YIELDED && CuePlayer.playing() != null) break;
                if (phase == Phase.YIELDED) {
                    setPhase(Phase.RESTING, "the cue is over");
                    break;
                }
                if (wantLevel > Intensity.CALM || arrival) {
                    if (slot == MusicLayers.Slot.MUSIC && CuePlayer.playingStage()) break;
                    arrival = false;
                    calm = 0;
                    restFading = false;
                    beginOrFade(mc);
                }
            }
        }
        if (phase != Phase.PLAYING && phase != Phase.ENDING) {
            victory = false;
            if (phase != Phase.RESTING) arrival = false;
        }
        death = false; // one-shot: the server's word that this player died
    }

    /** Starts the layers, after fading out a vanilla song that is playing (never a stage cue: those wait). */
    private void beginOrFade(Minecraft mc) {
        if (slot == MusicLayers.Slot.MUSIC && mc.getMusicManager().getCurrentMusicTranslationKey() != null) {
            vanillaFrom = Math.max(0.01F, mc.getMusicVolume());
            setPhase(Phase.FADING_VANILLA, "a song is playing: it fades out first");
            return;
        }
        start(mc);
    }

    private void start(Minecraft mc) {
        LayerManifest m = manifest;
        if (m == null) return;
        int n = m.stems().size();
        tgt = new float[n];
        sec = new float[n];
        level = -1;
        calm = 0;
        restFading = yielding = false;
        fadeOverride = -1F;
        computeTargets(wantLevel, -1);
        level = wantLevel;
        boolean music = m.music();
        sound = new LayeredSound(music ? AudioSounds.MUSIC_LAYERS.get() : AudioSounds.AMB_LAYERS.get(),
                music ? SoundSource.MUSIC : SoundSource.AMBIENT, m, tgt, sec);
        setPhase(Phase.PLAYING, "start at " + Intensity.name(level) + " " + gains());
        ownedGain = -1F;
        keepGain(mc);
        mc.getSoundManager().play(sound);
    }

    private void end(float fade, String why) {
        fadeOverride = fade;
        restFading = false;
        if (tgt.length > 0) java.util.Arrays.fill(tgt, 0F);
        StemMixer m = sound == null ? null : sound.mixer();
        if (m != null) {
            for (int k = 0; k < tgt.length; k++) m.set(k, 0F, fade);
            m.publish();
        }
        setPhase(Phase.ENDING, why + ", fade " + fade + " s");
    }

    private void retarget(String why) {
        retarget(why, level);
    }

    private void retarget(String why, int from) {
        computeTargets(level, from);
        StemMixer m = sound == null ? null : sound.mixer();
        if (m != null) {
            for (int k = 0; k < tgt.length; k++) m.set(k, tgt[k], sec[k]);
            m.publish();
        }
        log(why + " " + gains());
    }

    /** Each stem's target at this level and how fast it travels: attack rising, duck when intensity rose, release when it fell. */
    private void computeTargets(int lvl, int from) {
        LayerManifest m = manifest;
        if (m == null) return;
        float[] g = m.gainsAt(Math.max(Intensity.CALM, lvl));
        for (int k = 0; k < tgt.length; k++) {
            LayerManifest.Stem s = m.stems().get(k);
            float t = restFading || yielding ? 0F : g[k];
            float secs = t > tgt[k] ? s.attack() : (from >= 0 && lvl > from ? s.duck() : s.release());
            if (restFading) secs = REST_FADE;
            else if (yielding) secs = fadeOverride;
            else if (victory && t < tgt[k]) secs = Math.min(secs, VICTORY_FADE);
            tgt[k] = t;
            sec[k] = secs;
        }
    }

    /** While the layers own the channel, the music category follows the place's music volume (as vanilla's songs do). */
    private void keepGain(Minecraft mc) {
        if (slot != MusicLayers.Slot.MUSIC) return;
        float pv = mc.getMusicVolume();
        if (pv != ownedGain) {
            mc.getSoundManager().updateCategoryVolume(SoundSource.MUSIC, pv);
            ownedGain = pv;
        }
    }

    private void restoreGain() {
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().updateCategoryVolume(SoundSource.MUSIC, mc.getMusicVolume());
    }

    /** Hands the channel back: nothing plays, so vanilla's next song waits its full delay (silence stays the default). */
    private void releaseChannel(Minecraft mc) {
        if (slot != MusicLayers.Slot.MUSIC) return;
        MusicManager mm = mc.getMusicManager();
        if (mm.getCurrentMusicTranslationKey() == null) mm.stopPlaying();
    }

    private void stopSound() {
        if (sound != null) {
            sound.end();
            Minecraft.getInstance().getSoundManager().stop(sound);
        }
        sound = null;
    }

    private @Nullable LayerManifest load(Identifier id) {
        LayerManifest m = manifests.get(id);
        if (m != null) return m;
        Identifier path = LayerManifest.path(id);
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(path)) {
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            m = LayerManifest.parse(id, o);
            manifests.put(id, m);
            return m;
        } catch (Exception e) {
            broken.add(id);
            Awaken.LOG.warn("[audio] layers {}: no usable manifest at {}: {}", id, path, e.toString());
            return null;
        }
    }

    private void setPhase(Phase p, String why) {
        phase = p;
        phaseTicks = 0;
        if (p != Phase.PLAYING) yielding = yielding && p == Phase.ENDING;
        log(p.name().toLowerCase(java.util.Locale.ROOT) + ": " + why);
    }

    private String gains() {
        StringBuilder sb = new StringBuilder("targets");
        LayerManifest m = manifest;
        for (int k = 0; k < tgt.length; k++) {
            sb.append(' ').append(m == null ? "?" : m.stems().get(k).name()).append('=').append(String.format(java.util.Locale.ROOT, "%.2f", tgt[k]));
        }
        return sb.toString();
    }

    private void log(String what) {
        Awaken.LOG.info("[audio] layers {} {} [{}] {}", slot.name().toLowerCase(java.util.Locale.ROOT),
                manifest == null ? "-" : manifest.id(), Intensity.name(level), what);
    }

    /** A status line every five seconds in autopilot runs (the tour's proof): the mixer's actual gains. */
    private void status() {
        if (!AUTOPILOT || ticks % STATUS_EVERY != 0) return;
        StemMixer m = sound == null ? null : sound.mixer();
        if (m == null) return;
        StringBuilder sb = new StringBuilder("status: mixing");
        LayerManifest man = manifest;
        for (int k = 0; k < m.shown.length; k++) {
            sb.append(' ').append(man == null ? "?" : man.stems().get(k).name()).append('=').append(String.format(java.util.Locale.ROOT, "%.2f", m.shown[k]));
        }
        sb.append(String.format(java.util.Locale.ROOT, ", %.1f s mixed, %d samples softened", m.frames / (float) StemMixer.SR, m.softened));
        log(sb.toString());
    }

    /** What /awaken audio layers shows on the client side (and the autopilot logs). */
    String describe() {
        StemMixer m = sound == null ? null : sound.mixer();
        return slot.name().toLowerCase(java.util.Locale.ROOT) + ": " + phase + " " + (manifest == null ? "-" : manifest.id()) + " ["
                + Intensity.name(level) + "] want " + want + " " + Intensity.name(wantLevel) + (m == null ? "" : " mixer " + m.frames + " frames");
    }

    void clear() {
        if (phase == Phase.FADING_VANILLA) restoreGain();
        stopSound();
        if (phase != Phase.IDLE) setPhase(Phase.IDLE, "cleared");
        want = null;
        manifest = null;
        level = -1;
        arrival = victory = death = false;
        restFading = yielding = false;
    }
}
