package dev.awaken.body.ascent.client;

import dev.awaken.Awaken;
import dev.awaken.body.ascent.AscentScoreTable;
import dev.awaken.body.client.feel.FeelConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/**
 * The movement's sounds as scores of Minecraft's own recordings (SPEC §62). Every {@code awaken:ascent.<name>} sound
 * about to play, whoever asked for it (the prediction, the server, a scene), is taken here and played as its score
 * instead: two or three layers, each a sounds.json entry that names a vanilla sound event
 * ({@code ascent.score.<name>.<layer>}; tools/art/specs/ascent_scores.py writes them and {@link AscentScoreTable}),
 * at the same place, volume and pitch, each after its own delay. Loops choose their layer where they are made (the
 * wind, the rope's hum). {@code movementScores} off: the sounds as they were.
 */
final class AscentScores {
    private AscentScores() {}

    private static final String PREFIX = "ascent.", LAYER = "ascent.score.";
    private static final Identifier[][] LAYERS = new Identifier[AscentScoreTable.NAMES.length][];

    static {
        for (int i = 0; i < LAYERS.length; i++) {
            LAYERS[i] = new Identifier[AscentScoreTable.DELAYS[i].length];
            for (int j = 0; j < LAYERS[i].length; j++) LAYERS[i][j] = Awaken.id(LAYER + AscentScoreTable.NAMES[i] + "." + j);
        }
    }

    static boolean on() {
        return FeelConfig.movementScores();
    }

    private static int index(String name) {
        for (int i = 0; i < AscentScoreTable.NAMES.length; i++) if (AscentScoreTable.NAMES[i].equals(name)) return i;
        return -1;
    }

    /** A score's first layer (what a loop plays), or null when there is no such score. */
    static Identifier loop(String name) {
        int i = index(name);
        return i < 0 ? null : LAYERS[i][0];
    }

    /** Plays a score at a place; false when the scores are off or there is none by that name (the caller plays the old sound). */
    static boolean play(String name, double x, double y, double z, float volume, float pitch) {
        int i = on() ? index(name) : -1;
        if (i < 0) return false;
        play(i, x, y, z, volume, pitch, false, SoundSource.PLAYERS);
        return true;
    }

    private static void play(int i, double x, double y, double z, float volume, float pitch, boolean relative, SoundSource source) {
        var sounds = Minecraft.getInstance().getSoundManager();
        for (int j = 0; j < LAYERS[i].length; j++) {
            SimpleSoundInstance layer = new SimpleSoundInstance(LAYERS[i][j], source, volume, pitch, SoundInstance.createUnseededRandom(), false, 0,
                    relative ? SoundInstance.Attenuation.NONE : SoundInstance.Attenuation.LINEAR, x, y, z, relative);
            int delay = AscentScoreTable.DELAYS[i][j];
            if (delay <= 0) sounds.play(layer);
            else sounds.playDelayed(layer, delay);
        }
    }

    /** Any of the Ascent's own sounds about to play: its score plays instead. */
    static void onPlay(PlaySoundEvent e) {
        SoundInstance s = e.getOriginalSound();
        if (s == null || s.isLooping() || !on()) return;
        Identifier id = s.getIdentifier();
        String path = id.getPath();
        if (!Awaken.MODID.equals(id.getNamespace()) || !path.startsWith(PREFIX) || path.startsWith(LAYER)) return;
        int i = index(path.substring(PREFIX.length()));
        if (i < 0) return;
        // the instance's own volume and pitch are read once it knows its sound
        if (s.getOrResolve(Minecraft.getInstance().getSoundManager()) == null) return;
        play(i, s.getX(), s.getY(), s.getZ(), s.getVolume(), s.getPitch(), s.isRelative(), s.getSource());
        e.setSound(null);
    }
}
