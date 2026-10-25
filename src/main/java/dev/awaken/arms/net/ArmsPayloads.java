package dev.awaken.arms.net;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Arms' messages (SPEC §24). The client predicts every move and says what the player pressed; the server decides
 * what happened and answers with {@link Result}; everyone near sees it through {@link Seen}.
 */
public final class ArmsPayloads {
    private ArmsPayloads() {}

    /** What a {@link Press} pressed. */
    public static final int PRESS_STRIKE = 0, PRESS_CHARGE = 1, PRESS_RELEASE = 2;

    /**
     * Client → server: the strike key. STRIKE on the press (the move starts now; target = the creature under the
     * crosshair, -1 for none); CHARGE once the key has been held into a heavy; RELEASE when a charged key lets go.
     */
    public record Press(int seq, int what, float yaw, float pitch, int target) implements CustomPacketPayload {
        public static final Type<Press> TYPE = new Type<>(Awaken.id("arms_press"));
        public static final StreamCodec<ByteBuf, Press> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Press::seq, ByteBufCodecs.VAR_INT, Press::what, ByteBufCodecs.FLOAT, Press::yaw,
                ByteBufCodecs.FLOAT, Press::pitch, ByteBufCodecs.VAR_INT, Press::target, Press::new);
        @Override public Type<Press> type() { return TYPE; }
    }

    /** Client → server: a dodge started (predicted) along (dx, dz), from the feet at (x, y, z). */
    public record Dodge(int seq, float dx, float dz, double x, double y, double z) implements CustomPacketPayload {
        public static final Type<Dodge> TYPE = new Type<>(Awaken.id("arms_dodge"));
        public static final StreamCodec<ByteBuf, Dodge> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Dodge::seq, ByteBufCodecs.FLOAT, Dodge::dx, ByteBufCodecs.FLOAT, Dodge::dz,
                ByteBufCodecs.DOUBLE, Dodge::x, ByteBufCodecs.DOUBLE, Dodge::y, ByteBufCodecs.DOUBLE, Dodge::z, Dodge::new);
        @Override public Type<Dodge> type() { return TYPE; }
    }

    /** What a {@link Result} or {@link Seen} reports. */
    public static final int MOVE = 0, HIT = 1, DEFLECT = 2, BLOCK = 3, GUARD_BREAK = 4, PERFECT_DODGE = 5, EVADE = 6, BREAK = 7,
            REFUSE = 8, DODGE = 9, RESONANCE = 10;

    /**
     * Server → the player: what their press or guard did. MOVE: the move the server runs for seq (aux = move id,
     * aux2 = cadence); HIT: entity struck (aux = hit-stop ms, aux2 = the move); DEFLECT / BLOCK / GUARD_BREAK /
     * PERFECT_DODGE / EVADE: entity = the attacker; BREAK: entity's stance broke; REFUSE: seq refused; RESONANCE:
     * aux = the meter.
     */
    public record Result(int seq, int what, int entity, int aux, int aux2) implements CustomPacketPayload {
        public static final Type<Result> TYPE = new Type<>(Awaken.id("arms_result"));
        public static final StreamCodec<ByteBuf, Result> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Result::seq, ByteBufCodecs.VAR_INT, Result::what, ByteBufCodecs.VAR_INT, Result::entity,
                ByteBufCodecs.VAR_INT, Result::aux, ByteBufCodecs.VAR_INT, Result::aux2, Result::new);
        @Override public Type<Result> type() { return TYPE; }
    }

    /**
     * Server → players near: a fighter did something worth seeing. MOVE (aux = archetype index, aux2 = move id),
     * DODGE (aux = yaw of the way), DEFLECT / BLOCK / GUARD_BREAK (other = the attacker), BREAK (entity = whose stance).
     */
    public record Seen(int entity, int what, int other, int aux, int aux2) implements CustomPacketPayload {
        public static final Type<Seen> TYPE = new Type<>(Awaken.id("arms_seen"));
        public static final StreamCodec<ByteBuf, Seen> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Seen::entity, ByteBufCodecs.VAR_INT, Seen::what, ByteBufCodecs.VAR_INT, Seen::other,
                ByteBufCodecs.VAR_INT, Seen::aux, ByteBufCodecs.VAR_INT, Seen::aux2, Seen::new);
        @Override public Type<Seen> type() { return TYPE; }
    }
}
