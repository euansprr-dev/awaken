package dev.awaken.together;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.Throttle;
import dev.awaken.core.api.PartyHooks;
import dev.awaken.gates.Gates;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Together on the wire (SPEC §70.2): each player's party as they need it drawn (sent only when it changes, at most
 * twice a second), the band's buttons, and pings. Under two messages a second at rest.
 */
public final class TogetherNet {
    private TogetherNet() {}

    public static final int TRAVEL = 0, LEAVE = 1, FULL = 2, GENTLE = 3;
    /** State bits of a member. */
    public static final int F_GENTLE = 1, F_KNEEL = 2, F_DOWN = 4, F_IN_GATE = 8, F_ELSEWHERE = 16, F_SELF = 32;
    /** Ping kinds. */
    public static final int HERE = 0, CAREFUL = 1, TARGET = 2;

    /**
     * One party member as the strip draws them: health in twentieths, state bits, the floor they're on in a gate (0
     * outside), second winds left (theirs), ticks left while down, and where they stand (their own level's blocks).
     */
    public record Member(UUID id, String name, int health, int flags, int floor, int winds, int down, int x, int y, int z) {
        static final StreamCodec<ByteBuf, Member> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public Member decode(ByteBuf b) {
                return new Member(UUIDUtil.STREAM_CODEC.decode(b), ByteBufCodecs.STRING_UTF8.decode(b), ByteBufCodecs.VAR_INT.decode(b),
                        ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.VAR_INT.decode(b),
                        ByteBufCodecs.INT.decode(b), ByteBufCodecs.INT.decode(b), ByteBufCodecs.INT.decode(b));
            }

            @Override
            public void encode(ByteBuf b, Member m) {
                UUIDUtil.STREAM_CODEC.encode(b, m.id);
                ByteBufCodecs.STRING_UTF8.encode(b, m.name);
                ByteBufCodecs.VAR_INT.encode(b, m.health);
                ByteBufCodecs.VAR_INT.encode(b, m.flags);
                ByteBufCodecs.VAR_INT.encode(b, m.floor);
                ByteBufCodecs.VAR_INT.encode(b, m.winds);
                ByteBufCodecs.VAR_INT.encode(b, m.down);
                ByteBufCodecs.INT.encode(b, m.x);
                ByteBufCodecs.INT.encode(b, m.y);
                ByteBufCodecs.INT.encode(b, m.z);
            }
        };

        public boolean is(int flag) {
            return (flags & flag) != 0;
        }
    }

    /** Server → one player: their party as it is now, themselves first (alone: just themselves). */
    public record Party(List<Member> members) implements CustomPacketPayload {
        public static final Type<Party> TYPE = new Type<>(Awaken.id("together_party"));
        public static final StreamCodec<ByteBuf, Party> STREAM_CODEC = StreamCodec.composite(Member.STREAM_CODEC.apply(ByteBufCodecs.list(8)), Party::members, Party::new);

        @Override
        public Type<Party> type() {
            return TYPE;
        }
    }

    /** Player → server: a button on the band's Together section. {@code other} is unused for the setting and Leave. */
    public record Ask(int kind, UUID other) implements CustomPacketPayload {
        public static final Type<Ask> TYPE = new Type<>(Awaken.id("together_ask"));
        public static final StreamCodec<ByteBuf, Ask> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Ask::kind, UUIDUtil.STREAM_CODEC, Ask::other, Ask::new);

        @Override
        public Type<Ask> type() {
            return TYPE;
        }
    }

    /**
     * A ping, both ways: the place pointed at, what it is ({@link #HERE}, {@link #CAREFUL}, {@link #TARGET} with the
     * creature's entity id, else -1) and the line under the pinger's name. From the server it carries who pointed.
     */
    public record Ping(UUID by, String name, int kind, int entity, double x, double y, double z, String line) implements CustomPacketPayload {
        public static final Type<Ping> TYPE = new Type<>(Awaken.id("together_ping"));
        public static final StreamCodec<ByteBuf, Ping> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public Ping decode(ByteBuf b) {
                return new Ping(UUIDUtil.STREAM_CODEC.decode(b), ByteBufCodecs.STRING_UTF8.decode(b), ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.INT.decode(b),
                        ByteBufCodecs.DOUBLE.decode(b), ByteBufCodecs.DOUBLE.decode(b), ByteBufCodecs.DOUBLE.decode(b), ByteBufCodecs.STRING_UTF8.decode(b));
            }

            @Override
            public void encode(ByteBuf b, Ping p) {
                UUIDUtil.STREAM_CODEC.encode(b, p.by);
                ByteBufCodecs.STRING_UTF8.encode(b, p.name);
                ByteBufCodecs.VAR_INT.encode(b, p.kind);
                ByteBufCodecs.INT.encode(b, p.entity);
                ByteBufCodecs.DOUBLE.encode(b, p.x);
                ByteBufCodecs.DOUBLE.encode(b, p.y);
                ByteBufCodecs.DOUBLE.encode(b, p.z);
                ByteBufCodecs.STRING_UTF8.encode(b, p.line);
            }
        };

        @Override
        public Type<Ping> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent e) {
        var r = e.registrar("1");
        r.playToClient(Party.TYPE, Party.STREAM_CODEC);
        r.playToServer(Ask.TYPE, Ask.STREAM_CODEC, (m, ctx) -> {
            if (ctx.player() instanceof ServerPlayer p && Throttle.allow(p, "together_ask", 5)) onAsk(p, m);
        });
        r.playBidirectional(Ping.TYPE, Ping.STREAM_CODEC, (m, ctx) -> {
            // four in two seconds; the fifth is dropped without a word
            if (ctx.player() instanceof ServerPlayer p && Throttle.allowBurst(p, "together_ping", 4, 40)) onPing(p, m);
        });
    }

    private static void onAsk(ServerPlayer p, Ask m) {
        switch (m.kind()) {
            case LEAVE -> PartyService.leave(p);
            case FULL -> Fights.set(p, false);
            case GENTLE -> Fights.set(p, true);
            case TRAVEL -> {
                ServerPlayer other = p.level().getServer().getPlayerList().getPlayer(m.other());
                if (other != null) PartyService.ask(p, other);
            }
            default -> {}
        }
    }

    /** A ping goes to the rest of the pinger's party in their level; the line is cut to what a marker shows. */
    private static void onPing(ServerPlayer p, Ping m) {
        if (!TogetherConfig.seeing() || p.isSpectator()) return;
        if (!Double.isFinite(m.x()) || !Double.isFinite(m.y()) || !Double.isFinite(m.z())) return;
        if (p.distanceToSqr(m.x(), m.y(), m.z()) > 256.0 * 256.0) return;
        String line = m.line().length() > 48 ? m.line().substring(0, 48) : m.line();
        int kind = Mth.clamp(m.kind(), HERE, TARGET);
        Ping out = new Ping(p.getUUID(), p.getGameProfile().name(), kind, m.entity(), m.x(), m.y(), m.z(), line);
        for (UUID id : PartyHooks.members(p)) {
            ServerPlayer mate = id.equals(p.getUUID()) ? null : p.level().getServer().getPlayerList().getPlayer(id);
            if (mate != null && mate.level() == p.level()) AwakenNet.send(mate, out);
        }
    }

    /** A ping made on the server for {@code by} (the tour partner, who has no client): to the rest of their party in their level. */
    static void pingFrom(ServerPlayer by, net.minecraft.world.phys.Vec3 at, String line) {
        onPing(by, new Ping(by.getUUID(), by.getGameProfile().name(), HERE, -1, at.x, at.y, at.z, line));
    }

    // ---- the party's state ----------------------------------------------------------------------------------------------

    private static boolean dirty = true;
    private static final Map<UUID, Party> SENT = new HashMap<>();

    /** Something a strip draws has changed: everyone hears on the next pass. */
    static void dirty() {
        dirty = true;
    }

    static void forget() {
        SENT.clear();
        dirty = true;
    }

    static void forget(UUID id) {
        SENT.remove(id);
        dirty = true;
    }

    private static Member member(ServerPlayer viewer, ServerPlayer m) {
        int flags = (m == viewer ? F_SELF : 0) | (Fights.gentle(m) ? F_GENTLE : 0) | (Fights.down(m.getUUID()) ? F_DOWN : 0)
                | (Fights.kneeling(m.getUUID()) && !Fights.down(m.getUUID()) ? F_KNEEL : 0) | (m.level() != viewer.level() ? F_ELSEWHERE : 0);
        int floor = 0;
        var run = Gates.activeRun(m);
        if (run.isPresent()) {
            flags |= F_IN_GATE;
            floor = run.get().floor();
        }
        int health = m.isAlive() ? Mth.clamp(Math.round(20F * m.getHealth() / Math.max(1F, m.getMaxHealth())), m.getHealth() > 0 ? 1 : 0, 20) : 0;
        return new Member(m.getUUID(), m.getGameProfile().name(), health, flags, floor, Fights.windsLeft(m), (Fights.downLeft(m.getUUID()) + 19) / 20,
                m.getBlockX(), m.getBlockY(), m.getBlockZ());
    }

    /** Every tenth tick: each player's party, sent to them only when it differs from what they were last sent. */
    static void tick(MinecraftServer s) {
        if (s.getTickCount() % 10 == 0) pass(s);
    }

    /** One pass: every player's party as it is now, sent only where it differs from what they were last sent. */
    static void pass(MinecraftServer s) {
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            List<Member> members = new ArrayList<>(4);
            if (TogetherConfig.seeing() || TogetherConfig.gentle()) {
                for (ServerPlayer m : PartyService.online(p)) members.add(member(p, m));
            }
            Party now = new Party(members);
            if (!now.equals(SENT.put(p.getUUID(), now))) AwakenNet.send(p, now);
        }
        dirty = false;
    }
}
