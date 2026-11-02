package dev.awaken.arms.anvil;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The War Anvil's messages (SPEC §46.2). */
public final class AnvilPayloads {
    private AnvilPayloads() {}

    /**
     * What the smith did: one of the acts below, its beat's number (a), when (t, ticks from the stage's start) and its aim.
     * By hand at E: KNAP a flake (a its number), WIND the cord (a 0 as the hold begins, then each turn done), RELEASE it.
     */
    public static final int RESUME = 1, LEAVE = 2, PULL = 3, STRIKE = 4, PLUNGE = 5, LIFT = 6, TAP = 7, DRAW = 8, KNAP = 9, WIND = 10, RELEASE = 11;

    /** Client → server: an act at the anvil. */
    public record Act(BlockPos pos, int act, int a, float t, float aim) implements CustomPacketPayload {
        public static final Type<Act> TYPE = new Type<>(Awaken.id("anvil_act"));
        public static final StreamCodec<ByteBuf, Act> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Act::pos, ByteBufCodecs.VAR_INT, Act::act, ByteBufCodecs.VAR_INT, Act::a,
                ByteBufCodecs.FLOAT, Act::t, ByteBufCodecs.FLOAT, Act::aim, Act::new);
        @Override public Type<Act> type() { return TYPE; }
    }

    /** Server → the smith: the scene opens at this anvil (a start, or your own work waiting on it). */
    public record Open(BlockPos pos) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(Awaken.id("anvil_open"));
        public static final StreamCodec<ByteBuf, Open> STREAM_CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, Open::pos, Open::new);
        @Override public Type<Open> type() { return TYPE; }
    }

    /**
     * Server → the smith: the weapon is made and stands in the slot; how well, its flaw, the Temper it left with, and
     * flags (WarAnvil.TWIN, FROM_MEMORY, ARMOURY).
     */
    public record Done(BlockPos pos, int quality, int flaw, int temper, int flags) implements CustomPacketPayload {
        public static final Type<Done> TYPE = new Type<>(Awaken.id("anvil_done"));
        public static final StreamCodec<ByteBuf, Done> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Done::pos, ByteBufCodecs.VAR_INT, Done::quality, ByteBufCodecs.VAR_INT, Done::flaw,
                ByteBufCodecs.VAR_INT, Done::temper, ByteBufCodecs.VAR_INT, Done::flags, Done::new);
        @Override public Type<Done> type() { return TYPE; }
    }
}
