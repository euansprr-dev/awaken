package dev.awaken.powers.net;

import dev.awaken.Awaken;
import dev.awaken.powers.fx.FxEvent;
import dev.awaken.powers.fx.FxKind;
import dev.awaken.powers.fx.FxStyle;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Power FX on the wire: one compact event per effect (docs/modules/power-fx.md). Server → client only; the client
 * handler is registered in {@code powers.client.fx.FxClient}.
 */
public final class FxPayloads {
    private FxPayloads() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(RegisterPayloadHandlersEvent.class, e -> e.registrar("1").playToClient(Fx.TYPE, Fx.STREAM_CODEC));
    }

    /** One effect. Entity ids go +1 so "none" (-1) is a one-byte zero. */
    public record Fx(FxEvent e) implements CustomPacketPayload {
        public static final Type<Fx> TYPE = new Type<>(Awaken.id("power_fx"));
        public static final StreamCodec<ByteBuf, Fx> STREAM_CODEC = StreamCodec.of(FxPayloads::write, FxPayloads::read);

        @Override
        public Type<Fx> type() {
            return TYPE;
        }
    }

    static void write(ByteBuf buf, Fx msg) {
        FxEvent e = msg.e();
        ByteBufCodecs.VAR_INT.encode(buf, e.kind().ordinal());
        buf.writeByte(e.style().ordinal());
        ByteBufCodecs.VAR_INT.encode(buf, e.caster() + 1);
        ByteBufCodecs.VAR_INT.encode(buf, e.target() + 1);
        buf.writeDouble(e.x());
        buf.writeDouble(e.y());
        buf.writeDouble(e.z());
        buf.writeFloat(e.dx());
        buf.writeFloat(e.dy());
        buf.writeFloat(e.dz());
        buf.writeInt(e.colour());
        buf.writeFloat(e.scale());
        buf.writeFloat(e.aux());
        buf.writeInt(e.seed());
        ByteBufCodecs.VAR_INT.encode(buf, Math.max(0, e.ticks()));
        ByteBufCodecs.VAR_INT.encode(buf, e.flags());
    }

    static Fx read(ByteBuf buf) {
        FxKind kind = FxKind.byId(ByteBufCodecs.VAR_INT.decode(buf));
        FxStyle style = FxStyle.byId(buf.readByte());
        int caster = ByteBufCodecs.VAR_INT.decode(buf) - 1;
        int target = ByteBufCodecs.VAR_INT.decode(buf) - 1;
        double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble();
        float dx = buf.readFloat(), dy = buf.readFloat(), dz = buf.readFloat();
        int colour = buf.readInt();
        float scale = buf.readFloat();
        float aux = buf.readFloat();
        int seed = buf.readInt();
        int ticks = ByteBufCodecs.VAR_INT.decode(buf);
        int flags = ByteBufCodecs.VAR_INT.decode(buf);
        return new Fx(new FxEvent(kind, style, caster, target, x, y, z, dx, dy, dz, colour, scale, aux, seed, ticks, flags));
    }
}
