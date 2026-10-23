package dev.awaken.hearth.net;

import dev.awaken.Awaken;
import dev.awaken.hearth.HearthService;
import dev.awaken.hearth.echo.Echoes;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.UUID;

/** Client → server messages for Echoes (the B key and the chooser). Everything is validated on the server. */
public final class HearthNet {
    private HearthNet() {}

    /** B tapped: bind a lingering Echo nearby, or call/dismiss the field army. */
    public record BindKey() implements CustomPacketPayload {
        public static final Type<BindKey> TYPE = new Type<>(Awaken.id("hearth_bind"));
        public static final StreamCodec<ByteBuf, BindKey> STREAM_CODEC = StreamCodec.unit(new BindKey());
        @Override public Type<BindKey> type() { return TYPE; }
    }

    /** Chooser: this Echo marches with me (true) or stays home (false). */
    public record EchoField(UUID echo, boolean field) implements CustomPacketPayload {
        public static final Type<EchoField> TYPE = new Type<>(Awaken.id("hearth_echo_field"));
        public static final StreamCodec<ByteBuf, EchoField> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, EchoField::echo, ByteBufCodecs.BOOL, EchoField::field, EchoField::new);
        @Override public Type<EchoField> type() { return TYPE; }
    }

    /** Chooser: call (true) or dismiss (false) the field army. */
    public record EchoArmy(boolean out) implements CustomPacketPayload {
        public static final Type<EchoArmy> TYPE = new Type<>(Awaken.id("hearth_echo_army"));
        public static final StreamCodec<ByteBuf, EchoArmy> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, EchoArmy::out, EchoArmy::new);
        @Override public Type<EchoArmy> type() { return TYPE; }
    }

    /** Chooser: let an Echo go for good. */
    public record EchoRelease(UUID echo) implements CustomPacketPayload {
        public static final Type<EchoRelease> TYPE = new Type<>(Awaken.id("hearth_echo_release"));
        public static final StreamCodec<ByteBuf, EchoRelease> STREAM_CODEC = StreamCodec.composite(UUIDUtil.STREAM_CODEC, EchoRelease::echo, EchoRelease::new);
        @Override public Type<EchoRelease> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToServer(BindKey.TYPE, BindKey.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp && sp.isAlive()) Echoes.pressBind(sp);
        });
        r.playToServer(EchoField.TYPE, EchoField.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp && HearthService.enabled()) Echoes.setField(sp, msg.echo(), msg.field());
        });
        r.playToServer(EchoArmy.TYPE, EchoArmy.STREAM_CODEC, (msg, ctx) -> {
            if (!(ctx.player() instanceof ServerPlayer sp) || !HearthService.enabled()) return;
            if (msg.out()) Echoes.summonField(sp);
            else Echoes.dismissField(sp, true);
        });
        r.playToServer(EchoRelease.TYPE, EchoRelease.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) Echoes.release(sp, msg.echo());
        });
    }
}
