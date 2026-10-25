package dev.awaken.citizens;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.Arrays;
import java.util.List;

/**
 * Server → client: a line a citizen says (to one player, or aloud to everyone near), and the chimneys and forges the
 * client puffs smoke from (found server side as their chunks load; the client decides the hour's smoke itself).
 * Client → server: what the player's game hears with ({@link Ears}).
 */
public final class CitizensNet {
    private CitizensNet() {}

    /** kind: 0 said to you (a greeting), 1 aloud (a call, news, a shout), 2 a shout in an event (louder, longer). */
    public record Speech(int entity, String text, int ticks, int kind) implements CustomPacketPayload {
        public static final Type<Speech> TYPE = new Type<>(Awaken.id("citizen_speech"));
        public static final StreamCodec<ByteBuf, Speech> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Speech::entity, ByteBufCodecs.stringUtf8(256), Speech::text, ByteBufCodecs.VAR_INT, Speech::ticks,
                ByteBufCodecs.VAR_INT, Speech::kind, Speech::new);

        @Override
        public Type<Speech> type() {
            return TYPE;
        }
    }

    /**
     * Where the city stands (its plan's origin and street level), so the client can read the plan (on the classpath)
     * for the life it draws itself: animals, the soundscape, lamp light (M4 B3b). Sent at login; none outside Carillon.
     */
    public record Site(int cx, int sy, int cz) implements CustomPacketPayload {
        public static final Type<Site> TYPE = new Type<>(Awaken.id("city_site"));
        public static final StreamCodec<ByteBuf, Site> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Site::cx, ByteBufCodecs.VAR_INT, Site::sy, ByteBufCodecs.VAR_INT, Site::cz, Site::new);

        @Override
        public Type<Site> type() {
            return TYPE;
        }
    }

    /**
     * Smoke sources and other places the client lights or sounds: packed block positions and their kinds (0 bakery,
     * 1 house, 2 tavern, 3 forge, 4 fountain; 5 a street lamp's head, whose pool of light the client draws while lit).
     */
    public record Emitters(boolean replace, long[] pos, byte[] kind) implements CustomPacketPayload {
        public static final Type<Emitters> TYPE = new Type<>(Awaken.id("citizen_emitters"));
        public static final StreamCodec<ByteBuf, Emitters> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Emitters::replace, ByteBufCodecs.LONG_ARRAY, Emitters::pos, ByteBufCodecs.BYTE_ARRAY, Emitters::kind,
                Emitters::new);

        @Override
        public Type<Emitters> type() {
            return TYPE;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Emitters e && replace == e.replace && Arrays.equals(pos, e.pos) && Arrays.equals(kind, e.kind);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(pos);
        }
    }

    /**
     * Sent once at login: whether the player's client runs AmbientSounds (it plays the city's birds and crickets for
     * real), so the server picks the beds that leave those out (citizens.Soundscape).
     */
    public record Ears(boolean ambientSounds) implements CustomPacketPayload {
        public static final Type<Ears> TYPE = new Type<>(Awaken.id("city_ears"));
        public static final StreamCodec<ByteBuf, Ears> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, Ears::ambientSounds, Ears::new);

        @Override
        public Type<Ears> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent e) {
        var r = e.registrar("1");
        r.playToClient(Speech.TYPE, Speech.STREAM_CODEC);
        r.playToClient(Emitters.TYPE, Emitters.STREAM_CODEC);
        r.playToClient(Site.TYPE, Site.STREAM_CODEC);
        r.playToServer(Ears.TYPE, Ears.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) Soundscape.hears(sp, msg.ambientSounds());
        });
    }

    static void say(ServerPlayer p, CitizenBody b, String text, int ticks, int kind) {
        if (text.isEmpty()) return;
        PacketDistributor.sendToPlayer(p, new Speech(b.getId(), text, ticks, kind));
    }

    /** Aloud: every player within {@code radius} hears it. */
    static void aloud(ServerLevel level, CitizenBody b, String text, int ticks, int kind, double radius) {
        if (text.isEmpty()) return;
        PacketDistributor.sendToPlayersNear(level, null, b.getX(), b.getY(), b.getZ(), radius, new Speech(b.getId(), text, ticks, kind));
    }

    static void emitters(List<ServerPlayer> to, boolean replace, long[] pos, byte[] kind) {
        Emitters msg = new Emitters(replace, pos, kind);
        for (ServerPlayer p : to) PacketDistributor.sendToPlayer(p, msg);
    }
}
