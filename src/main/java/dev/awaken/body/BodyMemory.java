package dev.awaken.body;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * What the body remembers about a player (saved with the player, kept through death): which unlocks the System has
 * already announced, one bit per {@link Move}. Schema 1.
 */
public record BodyMemory(int schemaVersion, int announced) {
    public static final int SCHEMA = 1;
    public static final BodyMemory EMPTY = new BodyMemory(SCHEMA, 0);

    public static final MapCodec<BodyMemory> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(BodyMemory::schemaVersion),
            Codec.INT.optionalFieldOf("announced", 0).forGetter(BodyMemory::announced)
    ).apply(i, BodyMemory::new));

    public boolean announced(Move m) {
        return (announced & m.bit()) != 0;
    }

    public BodyMemory with(Move m) {
        return new BodyMemory(SCHEMA, announced | m.bit());
    }

    public boolean isEmpty() {
        return announced == 0;
    }
}
