package dev.awaken.hearth.request;

import com.mojang.serialization.Codec;
import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Network messages for Anchor requests: the pools (server → client) and the Status screen's "drop it" (client → server). */
public final class RequestNet {
    private RequestNet() {}

    /** Server → client: every request def, for the Status screen's hints. Sent on login and /reload. */
    public record Pools(Map<String, RequestDef> defs) implements CustomPacketPayload {
        public static final Type<Pools> TYPE = new Type<>(Awaken.id("hearth_request_pools"));
        public static final StreamCodec<ByteBuf, Pools> STREAM_CODEC =
                ByteBufCodecs.fromCodec(Codec.unboundedMap(Codec.STRING, RequestDef.CODEC)).map(Pools::new, Pools::defs);
        @Override public Type<Pools> type() { return TYPE; }
    }

    /** Client → server: let go of the active request at {@code index} (no penalty; a new one comes the next play-day). */
    public record Drop(int index, String id) implements CustomPacketPayload {
        public static final Type<Drop> TYPE = new Type<>(Awaken.id("hearth_request_drop"));
        public static final StreamCodec<ByteBuf, Drop> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Drop::index,
                ByteBufCodecs.STRING_UTF8, Drop::id,
                Drop::new);
        @Override public Type<Drop> type() { return TYPE; }
    }

    private static final Map<UUID, Long> RATE = new HashMap<>();

    static void register(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        // the pools are plain data: the client's copy lives in the same holder the server fills
        r.playToClient(Pools.TYPE, Pools.STREAM_CODEC, (msg, ctx) -> RequestPools.replace(msg.defs()));
        r.playToServer(Drop.TYPE, Drop.STREAM_CODEC, (msg, ctx) -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            long now = sp.level().getGameTime();
            Long last = RATE.put(sp.getUUID(), now);
            if (last != null && now - last < 5) return;
            Requests.drop(sp, msg.index(), msg.id());
        });
    }

    static void forget(UUID player) {
        RATE.remove(player);
    }
}
