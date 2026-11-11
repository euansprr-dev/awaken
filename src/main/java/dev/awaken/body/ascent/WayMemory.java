package dev.awaken.body.ascent;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What The Way Up remembers about a player (SPEC §63.6; saved, kept through death, its own attachment so the ladder's
 * record stays as it was): for each rung the place that woke for it (an overworld block, packed; {@link #NONE} for
 * none), which rungs have had their place told, which lessons are done and which are owed (a rung handed down by a later
 * fight), and the Leaper's Gauntlet's gate while it stands (its id and where). Every bit is set only once the thing has
 * happened, so a crash or a relog replays a lesson and never loses a rung.
 *
 * <p>Schema 2 (SPEC §71.3): the leap taken a move at a time at the Gauntlet's stands. {@code stands}: the stands taken (a
 * bit a {@link WayUp.Leap}); {@code parts}: the leap was given at a stand and its Gauntlet is not yet run, so only the
 * moves of the stands taken may be made. A schema 1 record reads as no stands and not by parts: every move open, as it
 * was.
 */
public record WayMemory(int schemaVersion, List<Long> places, int told, int done, int owed, int leapGate, long leapAt, int stands,
                        boolean parts) {
    public static final int SCHEMA = 2;
    /** No place. */
    public static final long NONE = Long.MIN_VALUE;
    public static final WayMemory EMPTY = new WayMemory(SCHEMA, List.of(), 0, 0, 0, -1, NONE, 0, false);

    public static final MapCodec<WayMemory> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(WayMemory::schemaVersion),
            Codec.LONG.listOf().optionalFieldOf("places", List.of()).forGetter(WayMemory::places),
            Codec.INT.optionalFieldOf("told", 0).forGetter(WayMemory::told),
            Codec.INT.optionalFieldOf("done", 0).forGetter(WayMemory::done),
            Codec.INT.optionalFieldOf("owed", 0).forGetter(WayMemory::owed),
            Codec.INT.optionalFieldOf("leapGate", -1).forGetter(WayMemory::leapGate),
            Codec.LONG.optionalFieldOf("leapAt", NONE).forGetter(WayMemory::leapAt),
            Codec.INT.optionalFieldOf("stands", 0).forGetter(WayMemory::stands),
            Codec.BOOL.optionalFieldOf("parts", false).forGetter(WayMemory::parts)
    ).apply(i, WayMemory::new));

    public boolean isEmpty() {
        return told == 0 && done == 0 && owed == 0 && leapGate < 0 && stands == 0 && !parts && places.stream().allMatch(v -> v == NONE);
    }

    /** Whether this stand has been taken. */
    public boolean stand(WayUp.Leap l) {
        return (stands & l.bit()) != 0;
    }

    /** Whether one of the leap's moves may be made: always, unless the leap is being taken by parts and its stand is not yet. */
    public boolean canLeap(WayUp.Leap l) {
        return !parts || (stands & l.bit()) != 0;
    }

    /** The moves that may be made, a bit each (the client's copy). */
    public int leapOpen() {
        int bits = 0;
        for (WayUp.Leap l : WayUp.Leap.VALUES) if (canLeap(l)) bits |= l.bit();
        return bits;
    }

    public WayMemory withStand(WayUp.Leap l, boolean byParts) {
        return new WayMemory(SCHEMA, places, told, done, owed, leapGate, leapAt, stands | l.bit(), byParts);
    }

    /** The Gauntlet is run (or the leap came whole another way): every move is open. */
    public WayMemory whole() {
        return parts ? new WayMemory(SCHEMA, places, told, done, owed, leapGate, leapAt, stands, false) : this;
    }

    /** The place that woke for a rung, or null. */
    public @Nullable BlockPos place(WayUp.Rung r) {
        long v = r.ordinal() < places.size() ? places.get(r.ordinal()) : NONE;
        return v == NONE ? null : BlockPos.of(v);
    }

    public boolean told(WayUp.Rung r) {
        return (told & r.bit()) != 0;
    }

    public boolean done(WayUp.Rung r) {
        return (done & r.bit()) != 0;
    }

    public boolean owed(WayUp.Rung r) {
        return (owed & r.bit()) != 0 && (done & r.bit()) == 0;
    }

    public @Nullable BlockPos gateAt() {
        return leapAt == NONE ? null : BlockPos.of(leapAt);
    }

    public WayMemory withPlace(WayUp.Rung r, @Nullable BlockPos pos) {
        List<Long> p = new ArrayList<>(places);
        while (p.size() < WayUp.Rung.VALUES.length) p.add(NONE);
        p.set(r.ordinal(), pos == null ? NONE : pos.asLong());
        return new WayMemory(SCHEMA, List.copyOf(p), told, done, owed, leapGate, leapAt, stands, parts);
    }

    public WayMemory withTold(WayUp.Rung r, boolean on) {
        return new WayMemory(SCHEMA, places, on ? told | r.bit() : told & ~r.bit(), done, owed, leapGate, leapAt, stands, parts);
    }

    public WayMemory withDone(WayUp.Rung r) {
        return new WayMemory(SCHEMA, places, told, done | r.bit(), owed & ~r.bit(), leapGate, leapAt, stands, parts);
    }

    public WayMemory withOwed(WayUp.Rung r) {
        return (done & r.bit()) != 0 ? this : new WayMemory(SCHEMA, places, told, done, owed | r.bit(), leapGate, leapAt, stands, parts);
    }

    public WayMemory withGate(int id, @Nullable BlockPos at) {
        return new WayMemory(SCHEMA, places, told, done, owed, id, at == null ? NONE : at.asLong(), stands, parts);
    }
}
