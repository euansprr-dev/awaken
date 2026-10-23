package dev.awaken.core.audio;

import java.util.Locale;

/**
 * The music cues: short pieces (10-40 s) for moments that matter, played through vanilla's music manager so they
 * never sit on top of a song and the silence after them is vanilla's own (a cue takes a song's place).
 *
 * <p>{@code stage} cues may stop a song that is playing (they are rare and they are the moment); the others wait up
 * to {@code waitTicks} for the music to fall silent and otherwise let the moment pass. {@code delayTicks} lets the
 * moment's own sound speak first (the rank-up chime) or a teleport settle. {@code cooldownTicks} is per player and
 * per cue; between any two cues there is also {@link #GAP} (stage cues ignore it).
 */
public enum Cue {
    AWAKENING(true, 0, 40, 600),
    RANK_UP(true, 20 * 60 * 5, 40, 300),
    GATE_BROKE(true, 20 * 60 * 10, 10, 200),
    BEAST(false, 20 * 60 * 15, 10, 200),
    GATE_FORMING(false, 20 * 60 * 20, 0, 300),
    SHRINE(false, 20 * 60 * 8, 20, 200),
    LAIR(false, 20 * 60 * 15, 10, 200);

    /** Least time between any two cues for one player (ticks). */
    public static final int GAP = 20 * 60 * 3;

    public final boolean stage;
    public final int cooldownTicks, delayTicks, waitTicks;
    public final String id;

    Cue(boolean stage, int cooldownTicks, int delayTicks, int waitTicks) {
        this.stage = stage;
        this.cooldownTicks = cooldownTicks;
        this.delayTicks = delayTicks;
        this.waitTicks = waitTicks;
        this.id = name().toLowerCase(Locale.ROOT);
    }

    /** The sound event path, {@code music.cue.<id>}. */
    public String eventPath() {
        return "music.cue." + id;
    }

    public static Cue byId(String id) {
        for (Cue c : values()) if (c.id.equals(id)) return c;
        return null;
    }

    public static Cue byOrdinal(int i) {
        Cue[] v = values();
        return i >= 0 && i < v.length ? v[i] : null;
    }
}
