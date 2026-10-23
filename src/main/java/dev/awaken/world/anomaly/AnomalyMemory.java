package dev.awaken.world.anomaly;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The director's memory of one player (attachment {@code awaken:anomaly_memory}, kept on death): the countdown to the
 * next anomaly in ticks of outdoor play, when they awakened (for the two-minute grace), and a few tallies.
 */
public final class AnomalyMemory {
    public static final int SCHEMA = 1;

    /** Outdoor-play ticks until the next try; below zero means not scheduled yet (scheduled on the first eligible check). */
    public double timer = -1;
    /** Game time of awakening, or -1 when unknown (awakened before anomalies existed: no grace needed). */
    public long awakenedAt = -1;
    public int started;
    public int riftsClosed;
    public int caches;
    public String lastKind = "";
    public long lastStartedAt;
    /** Not saved: the last reason the director was paused for this player, so each change is logged once. */
    String lastBlock = "";

    private record A(int schema, double timer, long awakenedAt, int started, int riftsClosed, int caches, String lastKind, long lastStartedAt) {
        static final MapCodec<A> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(A::schema),
                Codec.DOUBLE.optionalFieldOf("timer", -1.0).forGetter(A::timer),
                Codec.LONG.optionalFieldOf("awakened_at", -1L).forGetter(A::awakenedAt),
                Codec.INT.optionalFieldOf("started", 0).forGetter(A::started),
                Codec.INT.optionalFieldOf("rifts_closed", 0).forGetter(A::riftsClosed),
                Codec.INT.optionalFieldOf("caches", 0).forGetter(A::caches),
                Codec.STRING.optionalFieldOf("last_kind", "").forGetter(A::lastKind),
                Codec.LONG.optionalFieldOf("last_started", 0L).forGetter(A::lastStartedAt)
        ).apply(i, A::new));
    }

    public static final MapCodec<AnomalyMemory> MAP_CODEC = A.CODEC.xmap(a -> {
        AnomalyMemory m = new AnomalyMemory();
        m.timer = a.timer();
        m.awakenedAt = a.awakenedAt();
        m.started = a.started();
        m.riftsClosed = a.riftsClosed();
        m.caches = a.caches();
        m.lastKind = a.lastKind();
        m.lastStartedAt = a.lastStartedAt();
        return m;
    }, m -> new A(SCHEMA, m.timer, m.awakenedAt, m.started, m.riftsClosed, m.caches, m.lastKind, m.lastStartedAt));

    /** Value view for tests. */
    public String describe() {
        return String.format("timer %.0f, awakened %d, started %d, rifts %d, caches %d, last %s@%d", timer, awakenedAt, started, riftsClosed,
                caches, lastKind, lastStartedAt);
    }
}
