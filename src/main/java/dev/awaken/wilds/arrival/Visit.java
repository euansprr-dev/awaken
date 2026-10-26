package dev.awaken.wilds.arrival;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.Codec;

/**
 * A ward visit saved at logout (SPEC §17.27): which ward, how the player came into it, and when. A relog inside the
 * ward within {@link Arrival#VISIT_KEPT} picks it back up, so towering up and relogging no longer reads as a fresh
 * arrival by movement. Saved with the player, schema 1; taken off again at the next login.
 */
public record Visit(int schemaVersion, String ward, boolean byMovement, boolean byLine, long at) {
    static final int SCHEMA = 1;

    public static final MapCodec<Visit> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(Visit::schemaVersion),
            Codec.STRING.fieldOf("ward").forGetter(Visit::ward),
            Codec.BOOL.fieldOf("byMovement").forGetter(Visit::byMovement),
            Codec.BOOL.optionalFieldOf("byLine", false).forGetter(Visit::byLine),
            Codec.LONG.fieldOf("at").forGetter(Visit::at)
    ).apply(i, Visit::new));
}
