package dev.awaken.kin.works;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.List;

/** The works' wire (SPEC §43.12): the Ledger's page, and asking it for something. */
public final class WorksNet {
    private WorksNet() {}

    /** One line of the Ledger: the item's id, how many, and its bay's name. */
    public record LedgerRow(String item, int count, String bay) {
        static final StreamCodec<ByteBuf, LedgerRow> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, LedgerRow::item, ByteBufCodecs.VAR_INT,
                LedgerRow::count, ByteBufCodecs.STRING_UTF8, LedgerRow::bay, LedgerRow::new);
    }

    /** Server → player: open the Ledger at this desk. */
    public record Ledger(BlockPos desk, List<LedgerRow> rows) implements CustomPacketPayload {
        public static final Type<Ledger> TYPE = new Type<>(Awaken.id("works_ledger"));
        public static final StreamCodec<ByteBuf, Ledger> STREAM_CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, Ledger::desk,
                LedgerRow.CODEC.apply(ByteBufCodecs.list(2048)), Ledger::rows, Ledger::new);

        @Override
        public Type<Ledger> type() {
            return TYPE;
        }
    }

    /** Player → server: fetch this to the desk, or (show) light its shelf. */
    public record Ask(BlockPos desk, String item, boolean show) implements CustomPacketPayload {
        public static final Type<Ask> TYPE = new Type<>(Awaken.id("works_ledger_ask"));
        public static final StreamCodec<ByteBuf, Ask> STREAM_CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, Ask::desk, ByteBufCodecs.STRING_UTF8,
                Ask::item, ByteBufCodecs.BOOL, Ask::show, Ask::new);

        @Override
        public Type<Ask> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent e) {
        var r = e.registrar("1");
        r.playToClient(Ledger.TYPE, Ledger.STREAM_CODEC);
        r.playToServer(Ask.TYPE, Ask.STREAM_CODEC, (m, ctx) -> {
            if (ctx.player() instanceof ServerPlayer p) Storehouse.fetch(p, m.desk(), m.item(), m.show());
        });
    }

    static void send(ServerPlayer p, Ledger page) {
        PacketDistributor.sendToPlayer(p, page);
    }
}
