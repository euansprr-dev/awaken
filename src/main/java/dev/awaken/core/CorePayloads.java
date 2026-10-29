package dev.awaken.core;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
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

    /** Server → client: a teleport is waiting for its land ({@code on}), or has moved the player ({@link dev.awaken.core.api.Passage}). */
    public record PassageWait(boolean on) implements CustomPacketPayload {
        public static final Type<PassageWait> TYPE = new Type<>(Awaken.id("passage_wait"));
        public static final StreamCodec<ByteBuf, PassageWait> STREAM_CODEC = ByteBufCodecs.BOOL.map(PassageWait::new, PassageWait::on);
        @Override public Type<PassageWait> type() { return TYPE; }
    }

    static void register(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToServer(FlagUnfair.TYPE, FlagUnfair.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) flagUnfair(sp);
        });
        r.playToClient(PassageWait.TYPE, PassageWait.STREAM_CODEC);
    }

    /**
     * A playtester flags their last death (SPEC "Together", rate limits): each flag writes a log line and a toast, so
     * one every two seconds per player is plenty for a key press. Returns whether it was handled.
     */
    public static boolean flagUnfair(ServerPlayer sp) {
        if (!Throttle.allow(sp, "flag_unfair", 40)) return false;
        Playtest.flagUnfair(sp);
        return true;
    }
}
