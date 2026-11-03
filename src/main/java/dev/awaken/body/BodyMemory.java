package dev.awaken.body;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What the body remembers about a player (saved with the player, kept through death, sent to its owner): which unlocks
 * the System has already announced, and which moves a dream remembered for it (SPEC §9.4), one bit per {@link Move}.
 * Schema 2 (1 had no {@code remembered}).
 */
public record BodyMemory(int schemaVersion, int announced, int remembered) {
    public static final int SCHEMA = 2;
    public static final BodyMemory EMPTY = new BodyMemory(SCHEMA, 0, 0);

    public static final MapCodec<BodyMemory> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(BodyMemory::schemaVersion),
            Codec.INT.optionalFieldOf("announced", 0).forGetter(BodyMemory::announced),
            Codec.INT.optionalFieldOf("remembered", 0).forGetter(BodyMemory::remembered)
    ).apply(i, (v, a, r) -> new BodyMemory(SCHEMA, a, r)));

    public static final StreamCodec<ByteBuf, BodyMemory> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BodyMemory::announced, ByteBufCodecs.VAR_INT, BodyMemory::remembered,
            (a, r) -> new BodyMemory(SCHEMA, a, r));

    public boolean announced(Move m) {
        return (announced & m.bit()) != 0;
    }

    public BodyMemory with(Move m) {
        return new BodyMemory(SCHEMA, announced | m.bit(), remembered);
    }

    public boolean remembered(Move m) {
        return (remembered & m.bit()) != 0;
    }

    public BodyMemory remember(Move m) {
        return new BodyMemory(SCHEMA, announced, remembered | m.bit());
    }

    public boolean isEmpty() {
        return announced == 0 && remembered == 0;
    }
}
