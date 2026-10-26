package dev.awaken.hearth.net;

import dev.awaken.Awaken;
import dev.awaken.hearth.Embers;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The carried flame's messages (SPEC §28.2): the founding moment and the lifting, drawn on every client nearby; the
 * YOUR GROUND card and the name the owner gives it; a room's name as a quiet caption.
 */
public final class HomeNet {
    private HomeNet() {}

    /** Server → clients nearby: an Ember took at {@code pos} (warmth runs out to {@code radius}), or was lifted again. */
    public record Kindled(BlockPos pos, int radius, boolean lifted) implements CustomPacketPayload {
        public static final Type<Kindled> TYPE = new Type<>(Awaken.id("hearth_kindled"));
        public static final StreamCodec<ByteBuf, Kindled> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Kindled::pos, ByteBufCodecs.VAR_INT, Kindled::radius, ByteBufCodecs.BOOL, Kindled::lifted, Kindled::new);
        @Override public Type<Kindled> type() { return TYPE; }
    }

    /** Server → owner: the YOUR GROUND card, with a name offered from the land's words. */
    public record NameCard(String offered) implements CustomPacketPayload {
        public static final Type<NameCard> TYPE = new Type<>(Awaken.id("hearth_name_card"));
        public static final StreamCodec<ByteBuf, NameCard> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), NameCard::offered, NameCard::new);
        @Override public Type<NameCard> type() { return TYPE; }
    }

    /** Owner → server: the name they chose (validated there). */
    public record NameHearth(String name) implements CustomPacketPayload {
        public static final Type<NameHearth> TYPE = new Type<>(Awaken.id("hearth_name"));
        public static final StreamCodec<ByteBuf, NameHearth> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), NameHearth::name, NameHearth::new);
        @Override public Type<NameHearth> type() { return TYPE; }
    }

    /** Server → player: they walked into a room the Hearth just recognised or regraded (AMBIENT, topic hearth_room). */
    public record RoomCaption(String name, int grade, String purpose) implements CustomPacketPayload {
        public static final Type<RoomCaption> TYPE = new Type<>(Awaken.id("hearth_room_caption"));
        public static final StreamCodec<ByteBuf, RoomCaption> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(96), RoomCaption::name, ByteBufCodecs.VAR_INT, RoomCaption::grade,
                ByteBufCodecs.stringUtf8(48), RoomCaption::purpose, RoomCaption::new);
        @Override public Type<RoomCaption> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToClient(Kindled.TYPE, Kindled.STREAM_CODEC);
        r.playToClient(NameCard.TYPE, NameCard.STREAM_CODEC);
        r.playToClient(RoomCaption.TYPE, RoomCaption.STREAM_CODEC);
        r.playToServer(NameHearth.TYPE, NameHearth.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) Embers.name(sp, msg.name());
        });
    }
}
