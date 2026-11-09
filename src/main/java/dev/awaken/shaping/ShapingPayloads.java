package dev.awaken.shaping;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/** The Mallet's messages (SPEC §52.1). Everything the client sends is checked again on the server. */
public final class ShapingPayloads {
    private ShapingPayloads() {}

    /** Client → server: put this look on the block here, or on every block touching it that wears the same look. */
    public record SetLook(BlockPos pos, Identifier look, boolean run) implements CustomPacketPayload {
        public static final Type<SetLook> TYPE = new Type<>(Awaken.id("shaping_set_look"));
        public static final StreamCodec<ByteBuf, SetLook> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SetLook::pos, Identifier.STREAM_CODEC, SetLook::look, ByteBufCodecs.BOOL, SetLook::run, SetLook::new);
        @Override public Type<SetLook> type() { return TYPE; }
    }

    /** Client → server: Sneak and scroll, turn what right click does. */
    public record TurnMode(int by) implements CustomPacketPayload {
        public static final Type<TurnMode> TYPE = new Type<>(Awaken.id("shaping_turn_mode"));
        public static final StreamCodec<ByteBuf, TurnMode> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, TurnMode::by, TurnMode::new);
        @Override public Type<TurnMode> type() { return TYPE; }
    }

    /** Client → server: Sneak and left click. */
    public record Undo() implements CustomPacketPayload {
        public static final Type<Undo> TYPE = new Type<>(Awaken.id("shaping_undo"));
        public static final StreamCodec<ByteBuf, Undo> STREAM_CODEC = StreamCodec.unit(new Undo());
        @Override public Type<Undo> type() { return TYPE; }
    }

    /** Client → server: middle click, copy the look of the block here. */
    public record Pick(BlockPos pos) implements CustomPacketPayload {
        public static final Type<Pick> TYPE = new Type<>(Awaken.id("shaping_pick"));
        public static final StreamCodec<ByteBuf, Pick> STREAM_CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, Pick::pos, Pick::new);
        @Override public Type<Pick> type() { return TYPE; }
    }

    /** Server → client: the looks this player has learned. */
    public record Known(List<Identifier> learned) implements CustomPacketPayload {
        public static final Type<Known> TYPE = new Type<>(Awaken.id("shaping_known"));
        public static final StreamCodec<ByteBuf, Known> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), Known::learned, Known::new);
        @Override public Type<Known> type() { return TYPE; }
    }

    public static final int FX_KNOCK = 0, FX_LOOK = 1, FX_UNDO = 2;

    /** Server → everyone near: a knock or a look landed here. {@code look} is a block state id; {@code actor} who did it. */
    public record Fx(BlockPos pos, int look, int kind, int face, int actor) implements CustomPacketPayload {
        public static final Type<Fx> TYPE = new Type<>(Awaken.id("shaping_fx"));
        public static final StreamCodec<ByteBuf, Fx> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Fx::pos, ByteBufCodecs.VAR_INT, Fx::look, ByteBufCodecs.VAR_INT, Fx::kind, ByteBufCodecs.VAR_INT, Fx::face,
                ByteBufCodecs.VAR_INT, Fx::actor, Fx::new);
        @Override public Type<Fx> type() { return TYPE; }
    }
}
