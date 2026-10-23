package dev.awaken.lucidium.net;

import dev.awaken.Awaken;
import dev.awaken.lucidium.dream.Dreams;
import dev.awaken.lucidium.dream.JournalActions;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;

/** Lucidium payloads. Server to client: the HUD, the journal, Codex visions. Client to server: the H key and journal. */
public final class LucidiumNet {
    private LucidiumNet() {}

    /** The lucidity HUD while dreaming (sent when it changes, at most twice a second). */
    public record DreamHud(boolean dreaming, float lucidity, int ticksLeft, int total, int signs, int spotted, int flags,
                           int shadowless) implements CustomPacketPayload {
        public static final int FLY = 1, SHAPE = 2, SUMMON = 4, NIGHTMARE = 8, RAIN_UP = 16, LUCID = 32;
        public static final DreamHud AWAKE = new DreamHud(false, 0, 0, 1, 0, 0, 0, -1);
        public static final Type<DreamHud> TYPE = new Type<>(Awaken.id("lucidium_hud"));
        public static final StreamCodec<ByteBuf, DreamHud> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, DreamHud::dreaming, ByteBufCodecs.FLOAT, DreamHud::lucidity,
                ByteBufCodecs.VAR_INT, DreamHud::ticksLeft, ByteBufCodecs.VAR_INT, DreamHud::total,
                ByteBufCodecs.VAR_INT, DreamHud::signs, ByteBufCodecs.VAR_INT, DreamHud::spotted,
                ByteBufCodecs.VAR_INT, DreamHud::flags, ByteBufCodecs.INT, DreamHud::shadowless, DreamHud::new);

        public boolean has(int flag) {
            return (flags & flag) != 0;
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Opens the Dream Journal screen. */
    public record OpenJournal(String intention, boolean canRecall, int recall, List<String> dreams, List<String> snapshots)
            implements CustomPacketPayload {
        public static final Type<OpenJournal> TYPE = new Type<>(Awaken.id("lucidium_journal"));
        public static final StreamCodec<ByteBuf, OpenJournal> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, OpenJournal::intention, ByteBufCodecs.BOOL, OpenJournal::canRecall,
                ByteBufCodecs.VAR_INT, OpenJournal::recall,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(32)), OpenJournal::dreams,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(80)), OpenJournal::snapshots, OpenJournal::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record CodexLine(String id, String category, String title, String body, boolean found) {
        public static final StreamCodec<ByteBuf, CodexLine> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, CodexLine::id, ByteBufCodecs.STRING_UTF8, CodexLine::category,
                ByteBufCodecs.STRING_UTF8, CodexLine::title, ByteBufCodecs.STRING_UTF8, CodexLine::body,
                ByteBufCodecs.BOOL, CodexLine::found, CodexLine::new);
    }

    /** The player's revealed visions and known dream signs for the Codex board. */
    public record CodexSync(List<CodexLine> lines) implements CustomPacketPayload {
        public static final Type<CodexSync> TYPE = new Type<>(Awaken.id("lucidium_codex"));
        public static final StreamCodec<ByteBuf, CodexSync> STREAM_CODEC = StreamCodec.composite(
                CodexLine.STREAM_CODEC.apply(ByteBufCodecs.list(64)), CodexSync::lines, CodexSync::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** H pressed. */
    public record RealityCheck() implements CustomPacketPayload {
        public static final RealityCheck INSTANCE = new RealityCheck();
        public static final Type<RealityCheck> TYPE = new Type<>(Awaken.id("lucidium_reality_check"));
        public static final StreamCodec<ByteBuf, RealityCheck> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** H held long enough while lucid: shape the dream (or, sneaking at 80+, call on a memory). */
    public record Manifest(boolean summon) implements CustomPacketPayload {
        public static final Type<Manifest> TYPE = new Type<>(Awaken.id("lucidium_manifest"));
        public static final StreamCodec<ByteBuf, Manifest> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Manifest::summon, Manifest::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Sneak held while lying in bed: a dreamless night. */
    public record Dreamless() implements CustomPacketPayload {
        public static final Dreamless INSTANCE = new Dreamless();
        public static final Type<Dreamless> TYPE = new Type<>(Awaken.id("lucidium_dreamless"));
        public static final StreamCodec<ByteBuf, Dreamless> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** A Dream Journal button: kind "intent" (arg = intention id or "") or "recall". */
    public record JournalAction(String kind, String arg) implements CustomPacketPayload {
        public static final Type<JournalAction> TYPE = new Type<>(Awaken.id("lucidium_journal_action"));
        public static final StreamCodec<ByteBuf, JournalAction> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(32), JournalAction::kind, ByteBufCodecs.stringUtf8(64), JournalAction::arg, JournalAction::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(LucidiumNet::register);
    }

    private static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(DreamHud.TYPE, DreamHud.STREAM_CODEC);
        r.playToClient(OpenJournal.TYPE, OpenJournal.STREAM_CODEC);
        r.playToClient(CodexSync.TYPE, CodexSync.STREAM_CODEC);
        r.playToServer(RealityCheck.TYPE, RealityCheck.STREAM_CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) Dreams.realityCheck(sp);
        });
        r.playToServer(Manifest.TYPE, Manifest.STREAM_CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) Dreams.manifest(sp, p.summon());
        });
        r.playToServer(Dreamless.TYPE, Dreamless.STREAM_CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) dev.awaken.lucidium.dream.Sleep.chooseDreamless(sp);
        });
        r.playToServer(JournalAction.TYPE, JournalAction.STREAM_CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) JournalActions.handle(sp, p.kind(), p.arg());
        });
    }
}
