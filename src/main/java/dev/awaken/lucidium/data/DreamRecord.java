package dev.awaken.lucidium.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.lucidium.plan.DreamPlan;

/**
 * A dream the player had: kept (at most 16) for the Dream Journal and the Above's Dream Recorder, which replays the
 * stored plan. {@code written} = the player wrote it down within two minutes of waking (Recall).
 */
public record DreamRecord(long dreamId, long day, String summary, int peak, boolean lucid, boolean written,
                          boolean recorded, DreamPlan plan) {

    public static final Codec<DreamRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("id").forGetter(DreamRecord::dreamId),
            Codec.LONG.fieldOf("day").forGetter(DreamRecord::day),
            Codec.STRING.fieldOf("summary").forGetter(DreamRecord::summary),
            Codec.INT.fieldOf("peak").forGetter(DreamRecord::peak),
            Codec.BOOL.fieldOf("lucid").forGetter(DreamRecord::lucid),
            Codec.BOOL.optionalFieldOf("written", false).forGetter(DreamRecord::written),
            Codec.BOOL.optionalFieldOf("recorded", false).forGetter(DreamRecord::recorded),
            DreamPlan.CODEC.fieldOf("plan").forGetter(DreamRecord::plan)
    ).apply(i, DreamRecord::new));

    public DreamRecord withWritten() {
        return new DreamRecord(dreamId, day, summary, peak, lucid, true, recorded, plan);
    }

    public DreamRecord withRecorded() {
        return new DreamRecord(dreamId, day, summary, peak, lucid, written, true, plan);
    }
}
