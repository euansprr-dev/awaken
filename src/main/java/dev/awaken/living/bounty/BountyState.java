package dev.awaken.living.bounty;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * A player's notices (attachment {@code awaken:bounties}, schema 1, kept through death, synced to its owner): the three on
 * the board for them, the ones taken (with progress), when the board last refilled, and how many they've turned in.
 */
public final class BountyState {
    public static final int SCHEMA = 1;
    public static final int NOTICES = 3;

    public record Taken(String id, int progress) {
        public static final Codec<Taken> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Taken::id),
                Codec.INT.optionalFieldOf("progress", 0).forGetter(Taken::progress)
        ).apply(i, Taken::new));
    }

    public static final Codec<BountyState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(b -> SCHEMA),
            Codec.STRING.listOf().optionalFieldOf("offered", List.of()).forGetter(b -> List.copyOf(b.offered)),
            Taken.CODEC.listOf().optionalFieldOf("taken", List.of()).forGetter(b -> List.copyOf(b.taken)),
            Codec.LONG.optionalFieldOf("refilled", -1L).forGetter(b -> b.refilled),
            Codec.INT.optionalFieldOf("done", 0).forGetter(b -> b.done)
    ).apply(i, (schema, offered, taken, refilled, done) -> new BountyState(offered, taken, refilled, done)));

    final List<String> offered;
    final List<Taken> taken;
    long refilled;
    int done;

    public BountyState() {
        this(List.of(), List.of(), -1L, 0);
    }

    BountyState(List<String> offered, List<Taken> taken, long refilled, int done) {
        this.offered = new ArrayList<>(offered);
        this.taken = new ArrayList<>(taken);
        this.refilled = refilled;
        this.done = done;
    }

    public List<String> offered() {
        return List.copyOf(offered);
    }

    public List<Taken> taken() {
        return List.copyOf(taken);
    }

    public int done() {
        return done;
    }
}
