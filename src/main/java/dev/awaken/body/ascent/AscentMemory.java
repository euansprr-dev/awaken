package dev.awaken.body.ascent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * What the Ascent remembers about a player (saved, kept through death): the tiers held (a bit per
 * {@link AscentPayloads.Tier}), mastery points per track ({@link Track}: the Runner and each tier), and the longest chain
 * of moves ever strung together, and flags ({@link #SETTLED}). Schema 2 (schema 1 held the tiers only; it reads as no
 * mastery yet and not settled).
 */
public record AscentMemory(int schemaVersion, int tiers, List<Integer> mastery, int chainBest, int flags) {
    public static final int SCHEMA = 2;
    /** flags: the first load in a world has been settled (an old world's tiers given back by rank, once). */
    public static final int SETTLED = 1;
    /** flags: the Tether Vow was kept (the mast reached; SPEC §17.7). */
    public static final int VOW_KEPT = 2;
    /** flags: the Leaper's Gauntlet has been opened for this player, and run to its far landing (SPEC §17.10). */
    public static final int LEAPER_TRIAL = 4, LEAPER_TRIAL_DONE = 8;
    public static final AscentMemory EMPTY = new AscentMemory(SCHEMA, 0, List.of(), 0, 0);

    public static final MapCodec<AscentMemory> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(AscentMemory::schemaVersion),
            Codec.INT.optionalFieldOf("tiers", 0).forGetter(AscentMemory::tiers),
            Codec.INT.listOf().optionalFieldOf("mastery", List.of()).forGetter(AscentMemory::mastery),
            Codec.INT.optionalFieldOf("chainBest", 0).forGetter(AscentMemory::chainBest),
            Codec.INT.optionalFieldOf("flags", 0).forGetter(AscentMemory::flags)
    ).apply(i, AscentMemory::new));

    /** What mastery is earned on: the Runner's moves, and each tier's. */
    public enum Track {
        RUNNER, TETHER, LEAPER, WINGS, SKYBORN, SOVEREIGN;

        static final Track[] VALUES = values();
    }

    /** Mastery points needed for a level: level n needs 25 n² (10 levels at 2500). */
    public static int level(int points) {
        return Math.min(10, (int) Math.floor(Math.sqrt(Math.max(0, points) / 25.0)));
    }

    public boolean has(AscentPayloads.Tier t) {
        return t.in(tiers);
    }

    public int points(Track t) {
        return t.ordinal() < mastery.size() ? mastery.get(t.ordinal()) : 0;
    }

    public int level(Track t) {
        return level(points(t));
    }

    /** The six mastery levels packed 4 bits each (the client's copy). */
    public int packedLevels() {
        int packed = 0;
        for (Track t : Track.VALUES) packed |= level(t) << (4 * t.ordinal());
        return packed;
    }

    public AscentMemory withTiers(int bits) {
        return new AscentMemory(SCHEMA, bits, mastery, chainBest, flags);
    }

    public AscentMemory withPoints(Track t, int add) {
        List<Integer> m = new ArrayList<>(mastery);
        while (m.size() < Track.VALUES.length) m.add(0);
        m.set(t.ordinal(), Math.min(1_000_000, m.get(t.ordinal()) + add));
        return new AscentMemory(SCHEMA, tiers, List.copyOf(m), chainBest, flags);
    }

    public AscentMemory withChain(int chain) {
        return chain <= chainBest ? this : new AscentMemory(SCHEMA, tiers, mastery, chain, flags);
    }

    public AscentMemory withFlag(int flag) {
        return new AscentMemory(SCHEMA, tiers, mastery, chainBest, flags | flag);
    }

    public boolean isEmpty() {
        return tiers == 0 && chainBest == 0 && flags == 0 && mastery.stream().allMatch(v -> v == 0);
    }
}
