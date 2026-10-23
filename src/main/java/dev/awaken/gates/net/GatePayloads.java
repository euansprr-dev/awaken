package dev.awaken.gates.net;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.PlayDays;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateRegistry;
import dev.awaken.gates.GateState;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** Gate payloads. Server → client always goes through {@link AwakenNet#send}. */
public final class GatePayloads {
    private GatePayloads() {}

    public static final int BEACON_RANGE = 1600;

    /** One gate as the client sees it from afar: a pillar, a rank, a timer. Nothing about the inside. */
    public record Beacon(int id, BlockPos pos, int rank, int flags, int breaksIn) {
        public static final int BROKEN = 1, RED = 2, SCRIPTED = 4;
        public static final StreamCodec<ByteBuf, Beacon> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Beacon::id, BlockPos.STREAM_CODEC, Beacon::pos, ByteBufCodecs.VAR_INT, Beacon::rank,
                ByteBufCodecs.VAR_INT, Beacon::flags, ByteBufCodecs.VAR_INT, Beacon::breaksIn, Beacon::new);

        public boolean broken() {
            return (flags & BROKEN) != 0;
        }
    }

    public record Beacons(List<Beacon> beacons) implements CustomPacketPayload {
        public static final Type<Beacons> TYPE = new Type<>(Awaken.id("gate_beacons"));
        public static final StreamCodec<ByteBuf, Beacons> STREAM_CODEC = Beacon.CODEC.apply(ByteBufCodecs.list()).map(Beacons::new, Beacons::beacons);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The run HUD: what floor, what you carry, how hard the gate is pushing. {@code gateId < 0} clears it. */
    public record RunHud(int gateId, String theme, int rank, int area, int floor, int floors, int satchelSlots, int satchelXp,
                         int kills, int floorSeconds, int pressure, int noise, int collapseSeconds, int flags, String hint)
            implements CustomPacketPayload {
        public static final int LIGHTLESS = 1, RED = 2, LOW_GRAVITY = 4, CLEARED_FLOOR = 8, DISGUISED = 16, SILENCE = 32;
        public static final Type<RunHud> TYPE = new Type<>(Awaken.id("gate_run_hud"));
        public static final StreamCodec<ByteBuf, RunHud> STREAM_CODEC = StreamCodec.of((buf, h) -> {
            ByteBufCodecs.VAR_INT.encode(buf, h.gateId);
            ByteBufCodecs.STRING_UTF8.encode(buf, h.theme);
            ByteBufCodecs.VAR_INT.encode(buf, h.rank);
            ByteBufCodecs.VAR_INT.encode(buf, h.area);
            ByteBufCodecs.VAR_INT.encode(buf, h.floor);
            ByteBufCodecs.VAR_INT.encode(buf, h.floors);
            ByteBufCodecs.VAR_INT.encode(buf, h.satchelSlots);
            ByteBufCodecs.VAR_INT.encode(buf, h.satchelXp);
            ByteBufCodecs.VAR_INT.encode(buf, h.kills);
            ByteBufCodecs.VAR_INT.encode(buf, h.floorSeconds);
            ByteBufCodecs.VAR_INT.encode(buf, h.pressure);
            ByteBufCodecs.VAR_INT.encode(buf, h.noise + 1);
            ByteBufCodecs.VAR_INT.encode(buf, h.collapseSeconds + 1);
            ByteBufCodecs.VAR_INT.encode(buf, h.flags);
            ByteBufCodecs.STRING_UTF8.encode(buf, h.hint);
        }, buf -> new RunHud(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf) - 1,
                ByteBufCodecs.VAR_INT.decode(buf) - 1, ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf)));

        public static final RunHud NONE = new RunHud(-1, "", 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, -1, 0, "");

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The Satchel's contents (read-only on the client: loot is yours only once claimed). */
    public record SatchelView(List<ItemStack> items, int xp, boolean open) implements CustomPacketPayload {
        public static final Type<SatchelView> TYPE = new Type<>(Awaken.id("gate_satchel"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SatchelView> STREAM_CODEC = StreamCodec.composite(
                ItemStack.OPTIONAL_LIST_STREAM_CODEC, SatchelView::items, ByteBufCodecs.VAR_INT, SatchelView::xp, ByteBufCodecs.BOOL, SatchelView::open,
                SatchelView::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client asks to see the Satchel (Gate Map button). */
    public record OpenSatchel() implements CustomPacketPayload {
        public static final Type<OpenSatchel> TYPE = new Type<>(Awaken.id("gate_open_satchel"));
        public static final StreamCodec<ByteBuf, OpenSatchel> STREAM_CODEC = StreamCodec.unit(new OpenSatchel());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ---- sync helpers -----------------------------------------------------------------------------------------------

    public static void syncBeacons(ServerPlayer p) {
        if (!AwakenNet.canReceive(p, Beacons.TYPE)) return;
        MinecraftServer server = p.level().getServer();
        List<Beacon> list = new ArrayList<>();
        if (p.level().dimension() == Level.OVERWORLD) {
            long today = PlayDays.current(server);
            for (GateRecord g : GateRegistry.get(server).near(p.blockPosition(), BEACON_RANGE, false)) {
                if (!g.structurePlaced && g.state != GateState.BROKEN) continue;
                if (g.state == GateState.RELEASED || g.state == GateState.BUILDING) continue;
                if (g.state == GateState.CLEARED && g.inside.isEmpty()) continue;
                int flags = (g.state == GateState.BROKEN ? Beacon.BROKEN : 0) | (g.plan.is(dev.awaken.gates.content.Twist.RED_GATE) ? Beacon.RED : 0)
                        | (g.scripted ? Beacon.SCRIPTED : 0);
                list.add(new Beacon(g.id, g.pos, g.plan.rank().ordinal(), flags, (int) g.breaksIn(today)));
            }
        }
        AwakenNet.send(p, new Beacons(list));
    }

    public static void syncBeaconsAround(MinecraftServer server, BlockPos pos) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level().dimension() == Level.OVERWORLD && p.blockPosition().distSqr(pos) < (BEACON_RANGE + 200.0) * (BEACON_RANGE + 200.0)) {
                syncBeacons(p);
            }
        }
    }
}
