package dev.awaken.world.fauna;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-player fauna memory (attachment {@code awaken:fauna_state}, kept through death). Sightings themselves live in
 * the progression profile as {@code awaken:discovered/creature/<id>} counters; this remembers the reveal and the
 * named beasts' lairs the player has heard of or found (schema 2).
 *
 * @param revealPending the player awakened somewhere else (the Stillpoint, a gate): reveal on the next overworld visit
 * @param revealed      the awakening reveal has played
 * @param lairs         lairs heard of ({@code found} once the player stood in one)
 */
public record FaunaState(int schemaVersion, boolean revealPending, boolean revealed, List<KnownLair> lairs) {
    public static final int SCHEMA = 2;
    public static final FaunaState EMPTY = new FaunaState(SCHEMA, false, false, List.of());
    /** A player remembers this many lairs at most (the oldest unfound ones are forgotten first). */
    public static final int MAX_LAIRS = 24;

    /**
     * A lair the player knows of. {@code key} is its structure start chunk ({@code ChunkPos#toLong}), the same key the
     * world's lair registry uses; {@code pos} is where the System thinks it is (exact once its chunk has loaded).
     */
    public record KnownLair(String beast, long key, BlockPos pos, boolean found) {
        public static final Codec<KnownLair> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("beast").forGetter(KnownLair::beast),
                Codec.LONG.fieldOf("key").forGetter(KnownLair::key),
                BlockPos.CODEC.fieldOf("pos").forGetter(KnownLair::pos),
                Codec.BOOL.optionalFieldOf("found", false).forGetter(KnownLair::found)
        ).apply(i, KnownLair::new));
    }

    public static final MapCodec<FaunaState> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(FaunaState::schemaVersion),
            Codec.BOOL.optionalFieldOf("reveal_pending", false).forGetter(FaunaState::revealPending),
            Codec.BOOL.optionalFieldOf("revealed", false).forGetter(FaunaState::revealed),
            KnownLair.CODEC.listOf().optionalFieldOf("lairs", List.of()).forGetter(FaunaState::lairs)
    ).apply(i, FaunaState::new));

    public FaunaState {
        lairs = List.copyOf(lairs);
    }

    public FaunaState pending() {
        return new FaunaState(SCHEMA, true, revealed, lairs);
    }

    public FaunaState done() {
        return new FaunaState(SCHEMA, false, true, lairs);
    }

    public KnownLair lair(long key) {
        for (KnownLair l : lairs) if (l.key() == key) return l;
        return null;
    }

    /** Adds or updates a known lair (a found one stays found; an exact position replaces a guess). */
    public FaunaState withLair(KnownLair lair) {
        List<KnownLair> out = new ArrayList<>(lairs.size() + 1);
        boolean merged = false;
        for (KnownLair l : lairs) {
            if (l.key() == lair.key()) {
                out.add(new KnownLair(lair.beast(), lair.key(), lair.pos(), l.found() || lair.found()));
                merged = true;
            } else {
                out.add(l);
            }
        }
        if (!merged) out.add(lair);
        while (out.size() > MAX_LAIRS) {
            int drop = 0;
            for (int i = 0; i < out.size(); i++) {
                if (!out.get(i).found()) {
                    drop = i;
                    break;
                }
            }
            out.remove(drop);
        }
        return new FaunaState(SCHEMA, revealPending, revealed, out);
    }
}
