package dev.awaken.core;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Core network messages. */
public final class CorePayloads {
    private CorePayloads() {}

    /** Client → server: the player pressed the playtest "unfair death" key. */
    public record FlagUnfair() implements CustomPacketPayload {
        public static final Type<FlagUnfair> TYPE = new Type<>(Awaken.id("playtest_unfair"));
        public static final StreamCodec<ByteBuf, FlagUnfair> STREAM_CODEC = StreamCodec.unit(new FlagUnfair());
        @Override public Type<FlagUnfair> type() { return TYPE; }
    }

    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(FlagUnfair.TYPE, FlagUnfair.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) Playtest.flagUnfair(sp);
        });
    }
}
