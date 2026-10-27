package dev.awaken.village.road;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.village.VillageService;
import dev.awaken.village.plan.VillagePlan;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * What the Atlas shows of the roads and settlements (SPEC §28.9): each player's record of the stretches of road they've
 * walked (a few points each, so the map can draw them anywhere without the road being laid again) and the settlements
 * they've found (name, kind, where). Saved with the player and kept through death; its owner gets the whole of it on
 * joining and each new stretch or place as it's added (never the whole again); the client draws them through the Atlas's
 * marks.
 */
public final class RoadAtlas {
    private RoadAtlas() {}

    static final int SCHEMA = 1;
    /** Points a stretch walked holds (the road's own, every fourth), and how many stretches a player keeps. */
    static final int STRETCH = 8, MAX_STRETCHES = 3000, MAX_FOUND = 400;

    /** One settlement found: its key, name, kind (VillagePlan.Kind's ordinal) and where. */
    public record Found(String key, String name, int kind, int x, int z) {
        static final Codec<Found> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("key").forGetter(Found::key), Codec.STRING.fieldOf("name").forGetter(Found::name),
                Codec.INT.fieldOf("kind").forGetter(Found::kind), Codec.INT.fieldOf("x").forGetter(Found::x), Codec.INT.fieldOf("z").forGetter(Found::z)
        ).apply(i, Found::new));
    }

    /** A stretch walked: its key (road and stretch), the road's class and its points (x, z pairs). */
    public record Walked(String key, int cls, List<Integer> xz) {
        static final Codec<Walked> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("key").forGetter(Walked::key), Codec.INT.fieldOf("cls").forGetter(Walked::cls),
                Codec.INT.listOf().fieldOf("xz").forGetter(Walked::xz)
        ).apply(i, Walked::new));
    }

    public record State(int schemaVersion, List<Walked> walked, List<Found> found) {
        public static final State EMPTY = new State(SCHEMA, List.of(), List.of());
        static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(State::schemaVersion),
                Walked.CODEC.listOf().optionalFieldOf("walked", List.of()).forGetter(State::walked),
                Found.CODEC.listOf().optionalFieldOf("found", List.of()).forGetter(State::found)
        ).apply(i, State::new));
    }

    static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);
    public static final Supplier<AttachmentType<State>> STATE = ATTACHMENTS.register("road_atlas",
            () -> AttachmentType.builder(() -> State.EMPTY).serialize(State.CODEC.fieldOf("road_atlas")).copyOnDeath().build());

    /** To the owner: the whole record ({@code whole}, on joining) or what was just added. */
    public record Sync(boolean whole, State state) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(Awaken.id("village_road_atlas"));
        public static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, Sync> STREAM_CODEC = net.minecraft.network.codec.StreamCodec.composite(
                ByteBufCodecs.BOOL, Sync::whole, ByteBufCodecs.fromCodec(State.CODEC), Sync::state, Sync::new);

        @Override
        public Type<Sync> type() {
            return TYPE;
        }
    }

    public static void init(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        modBus.addListener((net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e) -> e.registrar("1").playToClient(Sync.TYPE, Sync.STREAM_CODEC));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) dev.awaken.core.AwakenNet.send(p, new Sync(true, p.getData(STATE.get())));
        });
    }

    /** Every second: the stretch of road each player is on (within six of its line) and the settlement they stand in. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 3) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level().dimension() != Level.OVERWORLD || p.isSpectator()) continue;
            State s = p.getData(STATE.get());
            State next = s;
            RoadNet.Nearest n = RoadNet.indexed(p.getX(), p.getZ(), 6);
            if (n != null) {
                int seg = n.point() / STRETCH;
                String key = n.road().key() + "#" + seg;
                boolean had = false;
                for (Walked w : s.walked) had |= w.key.equals(key);
                if (!had) {
                    Road r = n.road();
                    List<Integer> xz = new ArrayList<>();
                    for (int i = seg * STRETCH; i <= Math.min(r.points() - 1, seg * STRETCH + STRETCH); i++) {
                        xz.add(Math.round(r.x()[i]));
                        xz.add(Math.round(r.z()[i]));
                    }
                    List<Walked> ws = new ArrayList<>(s.walked);
                    ws.add(new Walked(key, r.cls(), List.copyOf(xz)));
                    while (ws.size() > MAX_STRETCHES) ws.removeFirst();
                    next = new State(SCHEMA, List.copyOf(ws), next.found);
                }
            }
            VillageService.Live l = VillageService.at(p.level(), p.blockPosition());
            if (l != null) {
                boolean had = false;
                for (Found f : s.found) had |= f.key.equals(l.key());
                if (!had) {
                    VillagePlan plan = l.plan();
                    List<Found> fs = new ArrayList<>(next.found);
                    fs.add(new Found(plan.key, plan.name, plan.kind.ordinal(), plan.ox, plan.oz));
                    while (fs.size() > MAX_FOUND) fs.removeFirst();
                    next = new State(SCHEMA, next.walked, List.copyOf(fs));
                }
            }
            if (next == s) continue;
            p.setData(STATE.get(), next);
            List<Walked> newW = next.walked.size() > s.walked.size() ? List.of(next.walked.getLast()) : List.of();
            List<Found> newF = next.found.size() > s.found.size() ? List.of(next.found.getLast()) : List.of();
            dev.awaken.core.AwakenNet.send(p, new Sync(false, new State(SCHEMA, newW, newF)));
        }
    }
}
