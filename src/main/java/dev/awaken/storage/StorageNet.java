package dev.awaken.storage;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenNet;
import dev.awaken.storage.Tidying.Outcome;
import dev.awaken.storage.Tidying.Result;
import dev.awaken.storage.Tidying.Section;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.Arrays;
import java.util.List;

/** Storage's wire (SPEC §56). The client asks; the server decides and says what happened. */
public final class StorageNet {
    private StorageNet() {}

    public record TidyAsk(int containerId, int stateId, byte section) implements CustomPacketPayload {
        public static final Type<TidyAsk> TYPE = new Type<>(Awaken.id("storage_tidy"));
        public static final StreamCodec<ByteBuf, TidyAsk> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, TidyAsk::containerId,
                ByteBufCodecs.VAR_INT, TidyAsk::stateId, ByteBufCodecs.BYTE, TidyAsk::section, TidyAsk::new);

        @Override
        public Type<TidyAsk> type() {
            return TYPE;
        }
    }

    public record UndoAsk(int containerId) implements CustomPacketPayload {
        public static final Type<UndoAsk> TYPE = new Type<>(Awaken.id("storage_undo"));
        public static final StreamCodec<ByteBuf, UndoAsk> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, UndoAsk::containerId, UndoAsk::new);

        @Override
        public Type<UndoAsk> type() {
            return TYPE;
        }
    }

    public record PinAsk(int slot) implements CustomPacketPayload {
        public static final Type<PinAsk> TYPE = new Type<>(Awaken.id("storage_pin"));
        public static final StreamCodec<ByteBuf, PinAsk> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, PinAsk::slot, PinAsk::new);

        @Override
        public Type<PinAsk> type() {
            return TYPE;
        }
    }

    /** What came of a Tidy or an Undo. {@code firstBag}: the first time you've tidied your bag, so the line can mention pins. */
    public record Done(int containerId, byte section, byte result, boolean undo, boolean firstBag, List<Integer> from, List<Integer> to)
            implements CustomPacketPayload {
        public static final Type<Done> TYPE = new Type<>(Awaken.id("storage_done"));
        public static final StreamCodec<ByteBuf, Done> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Done::containerId,
                ByteBufCodecs.BYTE, Done::section, ByteBufCodecs.BYTE, Done::result, ByteBufCodecs.BOOL, Done::undo, ByteBufCodecs.BOOL, Done::firstBag,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(256)), Done::from, ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(256)), Done::to, Done::new);

        @Override
        public Type<Done> type() {
            return TYPE;
        }
    }

    public record Pins(long mask) implements CustomPacketPayload {
        public static final Type<Pins> TYPE = new Type<>(Awaken.id("storage_pins"));
        public static final StreamCodec<ByteBuf, Pins> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_LONG, Pins::mask, Pins::new);

        @Override
        public Type<Pins> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent e) {
        var r = e.registrar("1");
        r.playToServer(TidyAsk.TYPE, TidyAsk.STREAM_CODEC, (m, ctx) -> {
            if (ctx.player() instanceof ServerPlayer p) reply(p, m.containerId(), m.section(), Tidying.tidy(p, m.containerId(), m.stateId(), Section.of(m.section())), false);
        });
        r.playToServer(UndoAsk.TYPE, UndoAsk.STREAM_CODEC, (m, ctx) -> {
            if (ctx.player() instanceof ServerPlayer p) reply(p, m.containerId(), (byte) 0, Tidying.undo(p, m.containerId()), true);
        });
        r.playToServer(PinAsk.TYPE, PinAsk.STREAM_CODEC, (m, ctx) -> {
            if (ctx.player() instanceof ServerPlayer p && Tidying.pinToggle(p, m.slot()) != null) syncPins(p);
        });
        r.playToClient(Done.TYPE, Done.STREAM_CODEC);
        r.playToClient(Pins.TYPE, Pins.STREAM_CODEC);
    }

    private static void reply(ServerPlayer p, int containerId, byte section, Outcome o, boolean undo) {
        if (o.result() == Result.LIMITED) return; // the extra clicks of a held key: nothing to say
        boolean first = false;
        if (!undo && o.result() == Result.DONE && section == Section.BAG.ordinal()) {
            BagPins pins = p.getData(StorageModule.PINS);
            first = !pins.hinted();
            if (first) p.setData(StorageModule.PINS, pins.seenHint());
        }
        AwakenNet.send(p, new Done(containerId, section, (byte) o.result().ordinal(), undo, first, box(o.from()), box(o.to())));
    }

    private static List<Integer> box(int[] a) {
        return Arrays.stream(a).boxed().toList();
    }

    static void syncPins(ServerPlayer p) {
        AwakenNet.send(p, new Pins(p.getData(StorageModule.PINS).mask()));
    }
}
