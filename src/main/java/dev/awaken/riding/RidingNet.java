package dev.awaken.riding;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.Throttle;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The thread's wire (SPEC §55): who is tied to whom, for everyone who can see it, and the K menu's buttons. */
public final class RidingNet {
    private RidingNet() {}

    public static final int RIDE = 0, BRING = 1, LET_GO = 2;

    /** One thread; {@code fold} is the ticks left in the rider's fold, 0 when they're on their feet. */
    public record Entry(UUID rider, UUID anchor, int fold) {
        static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(UUIDUtil.STREAM_CODEC, Entry::rider,
                UUIDUtil.STREAM_CODEC, Entry::anchor, ByteBufCodecs.VAR_INT, Entry::fold, Entry::new);
    }

    /** Server → everyone: the threads there are now, sent when they change. */
    public record Threads(List<Entry> entries) implements CustomPacketPayload {
        public static final Type<Threads> TYPE = new Type<>(Awaken.id("riding_threads"));
        public static final StreamCodec<ByteBuf, Threads> STREAM_CODEC = StreamCodec.composite(Entry.STREAM_CODEC.apply(ByteBufCodecs.list(256)), Threads::entries, Threads::new);

        @Override
        public Type<Threads> type() {
            return TYPE;
        }
    }

    /** Player → server: a button on the K menu. {@code other} is unused for {@link #LET_GO}. */
    public record Ask(int kind, UUID other) implements CustomPacketPayload {
        public static final Type<Ask> TYPE = new Type<>(Awaken.id("riding_ask"));
        public static final StreamCodec<ByteBuf, Ask> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Ask::kind, UUIDUtil.STREAM_CODEC, Ask::other, Ask::new);

        @Override
        public Type<Ask> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent e) {
        var r = e.registrar("1");
        r.playToClient(Threads.TYPE, Threads.STREAM_CODEC);
        r.playToServer(Ask.TYPE, Ask.STREAM_CODEC, (m, ctx) -> {
            if (ctx.player() instanceof ServerPlayer p && Throttle.allow(p, "riding_ask", 5)) onAsk(p, m);
        });
    }

    private static void onAsk(ServerPlayer p, Ask m) {
        if (m.kind() == LET_GO) {
            Bonds.letGo(p);
            return;
        }
        if (m.kind() != RIDE && m.kind() != BRING) return;
        ServerPlayer other = p.level().getServer().getPlayerList().getPlayer(m.other());
        if (other != null) Bonds.ask(p, other, m.kind() == RIDE);
    }

    private static Threads now() {
        List<Entry> out = new ArrayList<>();
        for (Bonds.Bond b : Bonds.all()) out.add(new Entry(b.rider(), b.anchor(), Fold.left(b.rider())));
        return new Threads(out);
    }

    static void sync(MinecraftServer s) {
        Threads threads = now();
        for (ServerPlayer p : s.getPlayerList().getPlayers()) AwakenNet.send(p, threads);
    }

    static void sendTo(ServerPlayer p) {
        AwakenNet.send(p, now());
    }
}
