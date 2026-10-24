package dev.awaken.body.ascent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * What the Ascent remembers about a player (saved, kept through death): the tiers held, a bit per
 * {@link AscentPayloads.Tier}. Schema 1 (Phase 0: tiers come from the dev command; Phase 1 adds mastery, Flow and
 * route records and the deeds that grant each tier).
 */
public record AscentMemory(int schemaVersion, int tiers) {
    public static final int SCHEMA = 1;
    public static final AscentMemory EMPTY = new AscentMemory(SCHEMA, 0);

    public static final MapCodec<AscentMemory> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(AscentMemory::schemaVersion),
            Codec.INT.optionalFieldOf("tiers", 0).forGetter(AscentMemory::tiers)
    ).apply(i, AscentMemory::new));

    public boolean has(AscentPayloads.Tier t) {
        return t.in(tiers);
    }

    public AscentMemory withTiers(int bits) {
        return new AscentMemory(SCHEMA, bits);
    }

    public boolean isEmpty() {
        return tiers == 0;
    }
}
