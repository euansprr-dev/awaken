package dev.awaken.holo.band;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** The wrist slot's one message (C2S): a click the inventory screen took for the wrist. The wrist itself syncs as an
 *  attachment ({@link BandModule#WRIST}). */
public final class BandNet {
    private BandNet() {}

    /** A click on the wrist slot (with what the cursor carries), shift on it (off into the inventory), shift on a menu
     *  slot holding a band (onto the wrist). */
    public static final int CLICK = 0, SHIFT_OFF = 1, SHIFT_ON = 2;

    public record WristClick(int action, int slot) implements CustomPacketPayload {
        public static final Type<WristClick> TYPE = new Type<>(Awaken.id("wrist_click"));
        public static final StreamCodec<ByteBuf, WristClick> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, WristClick::action, ByteBufCodecs.VAR_INT, WristClick::slot, WristClick::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent e) {
        var r = e.registrar("1");
        r.playToServer(WristClick.TYPE, WristClick.STREAM_CODEC, (m, ctx) -> {
            if (ctx.player() instanceof ServerPlayer p) BandModule.click(p, m.action(), m.slot());
        });
    }
}
