package dev.awaken.hearth.request;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One request the player was offered or took on: what it matches ({@code target}: an item id or "#tag"), what to call
 * it, how many, how many are already in the Anchor, and the effort per item its rewards scale with. Immutable; the
 * def id is kept for "never the same thing twice in a row" and the hint.
 */
public record ActiveRequest(String id, String target, String name, int count, int delivered, double value, long since) {
    public static final Codec<ActiveRequest> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(ActiveRequest::id),
            Codec.STRING.fieldOf("target").forGetter(ActiveRequest::target),
            Codec.STRING.fieldOf("name").forGetter(ActiveRequest::name),
            Codec.INT.fieldOf("count").forGetter(ActiveRequest::count),
            Codec.INT.optionalFieldOf("delivered", 0).forGetter(ActiveRequest::delivered),
            Codec.DOUBLE.optionalFieldOf("value", 1.0).forGetter(ActiveRequest::value),
            Codec.LONG.optionalFieldOf("since", 0L).forGetter(ActiveRequest::since)
    ).apply(i, ActiveRequest::new));

    public int remaining() {
        return Math.max(0, count - delivered);
    }

    public boolean done() {
        return delivered >= count;
    }

    /** Effort points the rewards scale with: about a minute of play each. */
    public double effort() {
        return count * value;
    }

    public ActiveRequest withDelivered(int n) {
        return new ActiveRequest(id, target, name, count, Math.min(count, n), value, since);
    }

    public ActiveRequest startedOn(long day) {
        return new ActiveRequest(id, target, name, count, delivered, value, day);
    }
}
