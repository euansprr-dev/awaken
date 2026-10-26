package dev.awaken.hearth.player;

import dev.awaken.hearth.room.Reading;
import dev.awaken.hearth.room.Room;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * The room a player stands in at a Hearth they belong to, as their client draws it (the room panel): its name, grade,
 * the six readings, what it is for and the one thing that would lift it most. Synced to that player only, when it
 * changes; {@link #NONE} outside any read room.
 */
public record RoomHere(boolean in, String name, int grade, List<Integer> readings, String purpose, String lift) {
    public static final RoomHere NONE = new RoomHere(false, "", 0, List.of(), "", "");

    public static RoomHere of(Room r) {
        return of(r, "");
    }

    /** With the name the home gives it ("Ilse's room" once she lives there; "" = the reading's). */
    public static RoomHere of(Room r, String name) {
        Reading g = r.reading();
        return new RoomHere(true, name.isEmpty() ? g.name() : name, g.grade().ordinal(), List.of(g.closed(), g.light(), g.warmth(), g.space(), g.care(), g.detail()),
                g.purpose().label, g.lift());
    }

    public int reading(int i) {
        return i < readings.size() ? readings.get(i) : 0;
    }

    public static final StreamCodec<ByteBuf, RoomHere> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, RoomHere::in,
            ByteBufCodecs.stringUtf8(96), RoomHere::name,
            ByteBufCodecs.VAR_INT, RoomHere::grade,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(6)), RoomHere::readings,
            ByteBufCodecs.stringUtf8(48), RoomHere::purpose,
            ByteBufCodecs.stringUtf8(96), RoomHere::lift,
            RoomHere::new);
}
