package dev.awaken.powers.net;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** Network messages for powers. */
public final class PowerPayloads {
    private PowerPayloads() {}

    public static final int PRESS = 0;
    public static final int RELEASE = 1;

    /** Client → server: a power key was pressed or released. Rotation is the client's aim at that moment. */
    public record Cast(int slot, int phase, boolean predicted, float yRot, float xRot) implements CustomPacketPayload {
        public static final Type<Cast> TYPE = new Type<>(Awaken.id("power_cast"));
        public static final StreamCodec<ByteBuf, Cast> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Cast::slot, ByteBufCodecs.VAR_INT, Cast::phase, ByteBufCodecs.BOOL, Cast::predicted,
                ByteBufCodecs.FLOAT, Cast::yRot, ByteBufCodecs.FLOAT, Cast::xRot, Cast::new);
        @Override public Type<Cast> type() { return TYPE; }
    }

    /** Client → server: put a power on a slot (empty id clears it). */
    public record Equip(int slot, String power) implements CustomPacketPayload {
        public static final Type<Equip> TYPE = new Type<>(Awaken.id("power_equip"));
        public static final StreamCodec<ByteBuf, Equip> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Equip::slot, ByteBufCodecs.STRING_UTF8, Equip::power, Equip::new);
        @Override public Type<Equip> type() { return TYPE; }
    }

    /** Server → client: a cast was refused (reason key) or a predicted move must be undone. */
    public record Feedback(int slot, String power, String reason, boolean undoMotion) implements CustomPacketPayload {
        public static final Type<Feedback> TYPE = new Type<>(Awaken.id("power_feedback"));
        public static final StreamCodec<ByteBuf, Feedback> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Feedback::slot, ByteBufCodecs.STRING_UTF8, Feedback::power, ByteBufCodecs.STRING_UTF8, Feedback::reason,
                ByteBufCodecs.BOOL, Feedback::undoMotion, Feedback::new);
        @Override public Type<Feedback> type() { return TYPE; }
    }

    /** Server → client: combat feel. Hit-stop the entity for ms, kick the camera by shake degrees. */
    public record Feel(int entityId, int hitstopMs, float shake) implements CustomPacketPayload {
        public static final Type<Feel> TYPE = new Type<>(Awaken.id("power_feel"));
        public static final StreamCodec<ByteBuf, Feel> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Feel::entityId, ByteBufCodecs.VAR_INT, Feel::hitstopMs, ByteBufCodecs.FLOAT, Feel::shake, Feel::new);
        @Override public Type<Feel> type() { return TYPE; }
    }

    /** Server → client: outline these entities in a colour for some ticks (Danger Sense, Third Eye, Read Intent). */
    public record Marks(int kind, List<Integer> ids, int ticks) implements CustomPacketPayload {
        public static final int DANGER = 0;
        public static final int THIRD_EYE = 1;
        public static final int INTENT_HUNTING = 2;
        public static final int INTENT_OTHER = 3;
        public static final int ALLY = 4;
        public static final Type<Marks> TYPE = new Type<>(Awaken.id("power_marks"));
        public static final StreamCodec<ByteBuf, Marks> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Marks::kind, ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Marks::ids, ByteBufCodecs.VAR_INT, Marks::ticks, Marks::new);
        @Override public Type<Marks> type() { return TYPE; }
    }

    /**
     * Server → client: draw a beam from an entity's hand to a point for some ticks. follow = the caster's own client
     * redraws it along its live aim (range) so a channelled beam never lags behind the crosshair.
     */
    public record Beam(int casterId, double x, double y, double z, int colour, int ticks, float width, float range, boolean follow)
            implements CustomPacketPayload {
        public static final Type<Beam> TYPE = new Type<>(Awaken.id("power_beam"));
        public static final StreamCodec<ByteBuf, Beam> STREAM_CODEC = StreamCodec.of((buf, b) -> {
            ByteBufCodecs.VAR_INT.encode(buf, b.casterId);
            buf.writeDouble(b.x);
            buf.writeDouble(b.y);
            buf.writeDouble(b.z);
            buf.writeInt(b.colour);
            ByteBufCodecs.VAR_INT.encode(buf, b.ticks);
            buf.writeFloat(b.width);
            buf.writeFloat(b.range);
            buf.writeBoolean(b.follow);
        }, buf -> new Beam(readVarInt(buf), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readInt(), readVarInt(buf),
                buf.readFloat(), buf.readFloat(), buf.readBoolean()));
        @Override public Type<Beam> type() { return TYPE; }
    }

    private static int readVarInt(ByteBuf buf) {
        return ByteBufCodecs.VAR_INT.decode(buf);
    }
}
