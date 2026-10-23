package dev.awaken.above.net;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.above.AboveData;
import dev.awaken.above.frame.FrameModules;
import dev.awaken.above.logs.PrecursorLogs;
import dev.awaken.above.machine.Machines;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.List;
import java.util.Map;

/** Above network messages. Server → client handlers are registered in {@code AboveClient}. */
public final class AboveNet {
    private AboveNet() {}

    /** Server → client: the owner's AboveData (blueprints, logs, ruins...). */
    public record Sync(AboveData data) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(Awaken.id("above_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> STREAM_CODEC = AboveData.STREAM_CODEC.map(Sync::new, Sync::data);
        @Override public Type<Sync> type() { return TYPE; }
    }

    /** Server → client: Precursor log texts (for the Codex). */
    public record Logs(Map<String, PrecursorLogs.LogDef> logs) implements CustomPacketPayload {
        public static final Type<Logs> TYPE = new Type<>(Awaken.id("above_logs"));
        private static final Codec<Map<String, PrecursorLogs.LogDef>> MAP = Codec.unboundedMap(Codec.STRING, PrecursorLogs.LogDef.CODEC);
        public static final StreamCodec<ByteBuf, Logs> STREAM_CODEC = ByteBufCodecs.fromCodec(MAP, net.minecraft.nbt.NbtAccounter::unlimitedHeap)
                .map(Logs::new, Logs::logs);
        @Override public Type<Logs> type() { return TYPE; }
    }

    /** Server → client: open or refresh a machine panel. */
    public record OpenPanel(Panel panel) implements CustomPacketPayload {
        public static final Type<OpenPanel> TYPE = new Type<>(Awaken.id("above_panel"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenPanel> STREAM_CODEC = Panel.STREAM_CODEC.map(OpenPanel::new, OpenPanel::panel);
        @Override public Type<OpenPanel> type() { return TYPE; }
    }

    /** Client → server: a panel button was pressed. */
    public record PanelAction(BlockPos pos, String action, int index) implements CustomPacketPayload {
        public static final Type<PanelAction> TYPE = new Type<>(Awaken.id("above_panel_action"));
        public static final StreamCodec<ByteBuf, PanelAction> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, PanelAction::pos,
                ByteBufCodecs.stringUtf8(64), PanelAction::action,
                ByteBufCodecs.VAR_INT, PanelAction::index,
                PanelAction::new);
        @Override public Type<PanelAction> type() { return TYPE; }
    }

    /** A gate the Gate Scanner found, for the Gate Map. */
    public record GateMark(String id, int x, int z, int rank, String name, String omen, int breaksIn) {
        public static final Codec<GateMark> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(GateMark::id),
                Codec.INT.fieldOf("x").forGetter(GateMark::x),
                Codec.INT.fieldOf("z").forGetter(GateMark::z),
                Codec.INT.fieldOf("rank").forGetter(GateMark::rank),
                Codec.STRING.fieldOf("name").forGetter(GateMark::name),
                Codec.STRING.fieldOf("omen").forGetter(GateMark::omen),
                Codec.INT.fieldOf("breaks_in").forGetter(GateMark::breaksIn)
        ).apply(i, GateMark::new));
    }

    /** Server → client: gate markers from a Gate Scanner reading (replaces the previous set). */
    public record GateMarks(List<GateMark> marks) implements CustomPacketPayload {
        public static final Type<GateMarks> TYPE = new Type<>(Awaken.id("above_gate_marks"));
        public static final StreamCodec<ByteBuf, GateMarks> STREAM_CODEC = ByteBufCodecs.fromCodec(GateMark.CODEC.listOf(),
                net.minecraft.nbt.NbtAccounter::unlimitedHeap).map(GateMarks::new, GateMarks::marks);
        @Override public Type<GateMarks> type() { return TYPE; }
    }

    /** Client → server: the jet boots fired for this many ticks (drains essence, clears fall distance). */
    public record Jet(int ticks) implements CustomPacketPayload {
        public static final Type<Jet> TYPE = new Type<>(Awaken.id("above_jet"));
        public static final StreamCodec<ByteBuf, Jet> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Jet::ticks, Jet::new);
        @Override public Type<Jet> type() { return TYPE; }
    }

    /** Server → client: a Sky Leviathan passes the Halo. */
    public record Leviathan(long seed, int durationTicks) implements CustomPacketPayload {
        public static final Type<Leviathan> TYPE = new Type<>(Awaken.id("above_leviathan"));
        public static final StreamCodec<ByteBuf, Leviathan> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, Leviathan::seed, ByteBufCodecs.VAR_INT, Leviathan::durationTicks, Leviathan::new);
        @Override public Type<Leviathan> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToClient(Sync.TYPE, Sync.STREAM_CODEC);
        r.playToClient(Logs.TYPE, Logs.STREAM_CODEC);
        r.playToClient(OpenPanel.TYPE, OpenPanel.STREAM_CODEC);
        r.playToClient(GateMarks.TYPE, GateMarks.STREAM_CODEC);
        r.playToClient(Leviathan.TYPE, Leviathan.STREAM_CODEC);
        r.playToServer(PanelAction.TYPE, PanelAction.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) Machines.action(sp, msg.pos(), msg.action(), msg.index());
        });
        r.playToServer(Jet.TYPE, Jet.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) FrameModules.onJet(sp, msg.ticks());
        });
    }
}
