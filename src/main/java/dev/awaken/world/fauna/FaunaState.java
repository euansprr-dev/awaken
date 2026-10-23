package dev.awaken.world.fauna;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Per-player fauna memory (attachment {@code awaken:fauna_state}, kept through death). Sightings themselves live in
 * the progression profile as {@code awaken:discovered/creature/<id>} counters; this only remembers the reveal.
 *
 * @param revealPending the player awakened somewhere else (the Stillpoint, a gate): reveal on the next overworld visit
 * @param revealed      the awakening reveal has played
 */
public record FaunaState(int schemaVersion, boolean revealPending, boolean revealed) {
    public static final int SCHEMA = 1;
    public static final FaunaState EMPTY = new FaunaState(SCHEMA, false, false);

    public static final MapCodec<FaunaState> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(FaunaState::schemaVersion),
            Codec.BOOL.optionalFieldOf("reveal_pending", false).forGetter(FaunaState::revealPending),
            Codec.BOOL.optionalFieldOf("revealed", false).forGetter(FaunaState::revealed)
    ).apply(i, FaunaState::new));

    public FaunaState pending() {
        return new FaunaState(SCHEMA, true, revealed);
    }

    public FaunaState done() {
        return new FaunaState(SCHEMA, false, true);
    }
}
