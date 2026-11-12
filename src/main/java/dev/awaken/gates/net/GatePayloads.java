package dev.awaken.gates.net;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.PlayDays;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateRegistry;
import dev.awaken.gates.GateState;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
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
        /**
         * CALM: never breaks (the story calmed the gates). NO_BLIGHT: the land keeps its colour around a break. WAITING:
         * nobody has come close yet, so its break clock hasn't started ({@code breaksIn} means nothing).
         */
        public static final int BROKEN = 1, RED = 2, SCRIPTED = 4, CALM = 8, NO_BLIGHT = 16, WAITING = 32;
        public static final StreamCodec<ByteBuf, Beacon> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Beacon::id, BlockPos.STREAM_CODEC, Beacon::pos, ByteBufCodecs.VAR_INT, Beacon::rank,
                ByteBufCodecs.VAR_INT, Beacon::flags, ByteBufCodecs.VAR_INT, Beacon::breaksIn, Beacon::new);

        public boolean broken() {
            return (flags & BROKEN) != 0;
        }
    }

    /**
     * What's being crossed into, sent just before the move (SPEC §50.5): the painting's key, the small line, the name, one
     * plain sentence. {@code frozenMs >= 0} is a tour's look at one instant of it, with no move.
     */
    public record Crossing(String key, String kicker, String title, String line, boolean entering, int frozenMs) implements CustomPacketPayload {
        public static final Type<Crossing> TYPE = new Type<>(Awaken.id("gate_crossing"));
        public static final StreamCodec<ByteBuf, Crossing> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), Crossing::key, ByteBufCodecs.stringUtf8(64), Crossing::kicker, ByteBufCodecs.stringUtf8(64), Crossing::title,
                ByteBufCodecs.stringUtf8(200), Crossing::line, ByteBufCodecs.BOOL, Crossing::entering, ByteBufCodecs.VAR_INT, Crossing::frozenMs, Crossing::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** One level of the Lord's payout landing (SPEC §50.4): the level reached, levels so far, points so far, the last one. */
    public record LordLevel(int level, int beats, int points, boolean last) implements CustomPacketPayload {
        public static final Type<LordLevel> TYPE = new Type<>(Awaken.id("gate_lord_level"));
        public static final StreamCodec<ByteBuf, LordLevel> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, LordLevel::level, ByteBufCodecs.VAR_INT, LordLevel::beats, ByteBufCodecs.VAR_INT, LordLevel::points,
                ByteBufCodecs.BOOL, LordLevel::last, LordLevel::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * One light an open land shows while its way to the Lord is shut (SPEC §50.9 "Lights and seals"): a tall pillar over a
     * Warden still standing, or a short seal at the Lord's door, one per Warden. {@code pos} is its foot, in the world.
     */
    public record RealmLight(BlockPos pos, int colour, int height, boolean seal) {
        public static final StreamCodec<ByteBuf, RealmLight> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, RealmLight::pos, ByteBufCodecs.INT, RealmLight::colour, ByteBufCodecs.VAR_INT, RealmLight::height,
                ByteBufCodecs.BOOL, RealmLight::seal, RealmLight::new);
    }

    /** Every light of one gate's land that is lit now; one gone since the last list fades out on the client. */
    public record RealmLights(int gateId, List<RealmLight> lights) implements CustomPacketPayload {
        public static final Type<RealmLights> TYPE = new Type<>(Awaken.id("gate_realm_lights"));
        public static final StreamCodec<ByteBuf, RealmLights> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, RealmLights::gateId, RealmLight.CODEC.apply(ByteBufCodecs.list()), RealmLights::lights, RealmLights::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
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
    public record RunHud(int gateId, String theme, int rank, int area, int floor, int floors, int satchelStacks, int satchelXp,
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
            ByteBufCodecs.VAR_INT.encode(buf, h.satchelStacks);
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

    /**
     * The gate's spoils as the inventory screen shows them (SPEC §13, "Gate spoils"): the Satchel in a run (look only:
     * loot is yours only once claimed) and what is held for you. Each list carries at most {@link #MAX} stacks; the
     * counts are the true ones.
     */
    public record SpoilsView(boolean inRun, String title, List<ItemStack> satchel, int satchelStacks, int xp, int deathLine,
                             List<ItemStack> held, int heldStacks) implements CustomPacketPayload {
        public static final int MAX = 300;
        /** What dying in this gate costs: none (no run), all of it, half of it, nothing (the first gate), nothing once (a Clone Vat). */
        public static final int DEATH_NONE = 0, DEATH_ALL = 1, DEATH_HALF = 2, DEATH_FIRST = 3, DEATH_CLONE = 4;
        public static final SpoilsView EMPTY = new SpoilsView(false, "", List.of(), 0, 0, DEATH_NONE, List.of(), 0);
        public static final Type<SpoilsView> TYPE = new Type<>(Awaken.id("gate_spoils"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SpoilsView> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, SpoilsView::inRun, ByteBufCodecs.STRING_UTF8, SpoilsView::title,
                ItemStack.OPTIONAL_LIST_STREAM_CODEC, SpoilsView::satchel, ByteBufCodecs.VAR_INT, SpoilsView::satchelStacks,
                ByteBufCodecs.VAR_INT, SpoilsView::xp, ByteBufCodecs.VAR_INT, SpoilsView::deathLine,
                ItemStack.OPTIONAL_LIST_STREAM_CODEC, SpoilsView::held, ByteBufCodecs.VAR_INT, SpoilsView::heldStacks,
                SpoilsView::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The inventory screen opened (true) or closed (false): while it is open the server keeps the spoils view fresh. */
    public record WatchSpoils(boolean watching) implements CustomPacketPayload {
        public static final Type<WatchSpoils> TYPE = new Type<>(Awaken.id("gate_watch_spoils"));
        public static final StreamCodec<ByteBuf, WatchSpoils> STREAM_CODEC = ByteBufCodecs.BOOL.map(WatchSpoils::new, WatchSpoils::watching);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Take a held stack into your pockets ({@code all}: every held stack). The server checks the index still holds {@code item}. */
    public record TakeHeld(int index, Item item, boolean all) implements CustomPacketPayload {
        public static final Type<TakeHeld> TYPE = new Type<>(Awaken.id("gate_take_held"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TakeHeld> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, TakeHeld::index, ByteBufCodecs.registry(Registries.ITEM), TakeHeld::item, ByteBufCodecs.BOOL, TakeHeld::all,
                TakeHeld::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** A gate monster's drops, already in its killer's Satchel, drawn popping out of it and flying into the killer (client-only copies). */
    public record SpoilsFlight(double x, double y, double z, int killer, List<ItemStack> items) implements CustomPacketPayload {
        public static final int MAX = 8;
        public static final Type<SpoilsFlight> TYPE = new Type<>(Awaken.id("gate_spoils_flight"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SpoilsFlight> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, SpoilsFlight::x, ByteBufCodecs.DOUBLE, SpoilsFlight::y, ByteBufCodecs.DOUBLE, SpoilsFlight::z,
                ByteBufCodecs.VAR_INT, SpoilsFlight::killer, ItemStack.OPTIONAL_LIST_STREAM_CODEC, SpoilsFlight::items,
                SpoilsFlight::new);

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
                // only a gate that stands: never a failed or half-built one, never one whose vortex fell (SPEC §7)
                if (!dev.awaken.gates.GateExterior.shown(g) || g.state == GateState.BUILDING) continue;
                if (g.state == GateState.CLEARED && g.inside.isEmpty()) continue;
                int flags = (g.state == GateState.BROKEN ? Beacon.BROKEN : 0) | (g.plan.is(dev.awaken.gates.content.Twist.RED_GATE) ? Beacon.RED : 0)
                        | (g.scripted ? Beacon.SCRIPTED : 0) | (!g.scripted && g.neverBreaks(server) ? Beacon.CALM : 0)
                        | (dev.awaken.core.api.StoryHooks.colourRestored(server) ? Beacon.NO_BLIGHT : 0)
                        | (g.waiting(server) ? Beacon.WAITING : 0);
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
