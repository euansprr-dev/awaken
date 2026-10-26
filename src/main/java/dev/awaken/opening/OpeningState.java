package dev.awaken.opening;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One player's opening, beside the story's beat (StoryProgress, which says where the opening stands): what only the
 * opening needs to resume after a relog. Saved (schemaVersion 1), kept through death, never synced (the server sends
 * the scene).
 */
public final class OpeningState {
    /** 2 (2026-10-26): the Registry ceremony renumbered the Registry's steps; an older save walks the Registry again. */
    public static final int SCHEMA = 2;

    /** Came through Choir Square to the story's gate: the Ritual and the Void are still the opening's. */
    boolean arrived;
    /** The furthest act of the run reached (0 before the bell, 1 Bellwatch, 2 the Quay, 3 the market). */
    int act;
    /** Sky shots seen to the end (after the first, holding jump skips it). */
    int skyViews;
    /** The infirmary bed (party beds lie side by side). */
    int bed = -1;
    /** The Registry's step reached (OpeningRegistry: 0 the walk, the Lens, the desk, the band, wearing it, ...). */
    int step;
    /** When the bell rang and when the square was reached (game time; -1 unknown): the first run's time. */
    long runStart = -1, runEnd = -1;
    /** Pulled to the square by the light (a straggler), not run there. */
    boolean pulled;

    public static final Codec<OpeningState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(s -> SCHEMA),
            Codec.BOOL.optionalFieldOf("arrived", false).forGetter(s -> s.arrived),
            Codec.INT.optionalFieldOf("act", 0).forGetter(s -> s.act),
            Codec.INT.optionalFieldOf("skyViews", 0).forGetter(s -> s.skyViews),
            Codec.INT.optionalFieldOf("bed", -1).forGetter(s -> s.bed),
            Codec.INT.optionalFieldOf("step", 0).forGetter(s -> s.step),
            Codec.LONG.optionalFieldOf("runStart", -1L).forGetter(s -> s.runStart),
            Codec.LONG.optionalFieldOf("runEnd", -1L).forGetter(s -> s.runEnd),
            Codec.BOOL.optionalFieldOf("pulled", false).forGetter(s -> s.pulled)
    ).apply(i, (schema, arrived, act, views, bed, step, start, end, pulled) -> {
        OpeningState s = new OpeningState();
        s.arrived = arrived;
        s.act = act;
        s.skyViews = views;
        s.bed = bed;
        s.step = schema < 2 ? 0 : step;
        s.runStart = start;
        s.runEnd = end;
        s.pulled = pulled;
        return s;
    }));

    public boolean arrived() {
        return arrived;
    }

    public int act() {
        return act;
    }

    public int skyViews() {
        return skyViews;
    }

    /** The first run's length in ticks (bell to square), or -1. */
    public long runTicks() {
        return runStart >= 0 && runEnd >= runStart ? runEnd - runStart : -1;
    }

    /** A fresh start (the opening begins again: a test, or {@code /awaken opening restart}). */
    void reset() {
        arrived = false;
        act = 0;
        bed = -1;
        step = 0;
        runStart = runEnd = -1;
        pulled = false;
    }
}
