package dev.awaken.armour.net;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * The Forge Song's messages (SPEC §44.4). The client says what the smith did and when (ticks from the stage's start, as
 * its clock saw them); the server judges by its own clock within a lag allowance and keeps the work on the stand, which
 * syncs to everyone near. The list of what can be made, and each finished piece, come back to the smith.
 */
public final class ArmourPayloads {
    private ArmourPayloads() {}

    /** What an {@link Act} does. */
    public static final int START = 0, MEMORY = 1, TAKE = 2, PULL = 3, STRIKE = 4, QUENCH = 5, CUT = 6, LEAVE = 7, RESUME = 8, TWIN = 9, OPEN = 10,
            TAP = 11, SEAT = 12, LOOK = 13, ACCENT = 14;

    /**
     * Client → server, at a Fitting Stand. START / MEMORY: forge the piece {@code a} (set ordinal × 4 + slot), in full or
     * from memory; TAKE: the suit into your pack; PULL: the bar out of the fire at {@code t}; STRIKE: strike {@code a} at
     * {@code t}, {@code aim} mark radii off; QUENCH: plunged at {@code t}; CUT: the cut done, {@code aim} its score; LEAVE:
     * stepped away (the work waits); RESUME: back at it; TWIN: a helper's off-beat at {@code t}; OPEN: the stand's list again.
     */
    public record Act(BlockPos pos, int act, int a, float t, float aim) implements CustomPacketPayload {
        public static final Type<Act> TYPE = new Type<>(Awaken.id("forge_act"));
        public static final StreamCodec<ByteBuf, Act> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Act::pos, ByteBufCodecs.VAR_INT, Act::act, ByteBufCodecs.VAR_INT, Act::a,
                ByteBufCodecs.FLOAT, Act::t, ByteBufCodecs.FLOAT, Act::aim, Act::new);
        @Override public Type<Act> type() { return TYPE; }
    }

    /** One thing a piece takes: what it is called, an item that shows it, how many you have and need. */
    public record Need(String label, String icon, int have, int need) {
        public static final StreamCodec<ByteBuf, Need> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Need::label, ByteBufCodecs.STRING_UTF8, Need::icon, ByteBufCodecs.VAR_INT, Need::have,
                ByteBufCodecs.VAR_INT, Need::need, Need::new);
    }

    /** {@link Offer#flags}: you can make it now; you can forge it from memory; you have made this set whole before; you
     * haven't found the set's pattern yet (the list shows it, and where patterns come from). */
    public static final int CAN = 1, FROM_MEMORY = 2, KNOWN_WHOLE = 4, NO_PATTERN = 8;

    /** A piece the stand offers: set ordinal × 4 + slot, what it takes, and flags. */
    public record Offer(int piece, List<Need> needs, int flags) {
        public static final StreamCodec<ByteBuf, Offer> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Offer::piece, Need.STREAM_CODEC.apply(ByteBufCodecs.list(16)), Offer::needs,
                ByteBufCodecs.VAR_INT, Offer::flags, Offer::new);
    }

    /**
     * Server → the smith: the stand's list (opens the screen). {@code place} says what the stand needs near it before
     * metal can be worked (empty: all there); {@code hand} the same for work by hand.
     */
    public record Offers(BlockPos pos, int rank, List<Offer> offers, String place, String hand) implements CustomPacketPayload {
        public static final Type<Offers> TYPE = new Type<>(Awaken.id("forge_offers"));
        public static final StreamCodec<ByteBuf, Offers> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Offers::pos, ByteBufCodecs.VAR_INT, Offers::rank, Offer.STREAM_CODEC.apply(ByteBufCodecs.list(64)), Offers::offers,
                ByteBufCodecs.STRING_UTF8, Offers::place, ByteBufCodecs.STRING_UTF8, Offers::hand, Offers::new);
        @Override public Type<Offers> type() { return TYPE; }
    }

    /** Server → the smith: a piece finished and fitted (quality 0-3), whether its set now wakes on the stand, and for the first time. */
    public record Done(BlockPos pos, int piece, int quality, boolean woke, boolean first) implements CustomPacketPayload {
        public static final Type<Done> TYPE = new Type<>(Awaken.id("forge_done"));
        public static final StreamCodec<ByteBuf, Done> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Done::pos, ByteBufCodecs.VAR_INT, Done::piece, ByteBufCodecs.VAR_INT, Done::quality,
                ByteBufCodecs.BOOL, Done::woke, ByteBufCodecs.BOOL, Done::first, Done::new);
        @Override public Type<Done> type() { return TYPE; }
    }

    /**
     * Server → the wearer and everyone tracking them: a whole set's charge (0-100; Hornlord's lowered horns), the runes
     * circling them (0-3) and a flare (0 none, else how bright) when an ability fires (SPEC §44.5, "The look").
     */
    public record Charge(int entity, int charge, int runes, int flash) implements CustomPacketPayload {
        public static final Type<Charge> TYPE = new Type<>(Awaken.id("armour_charge"));
        public static final StreamCodec<ByteBuf, Charge> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Charge::entity, ByteBufCodecs.VAR_INT, Charge::charge, ByteBufCodecs.VAR_INT, Charge::runes,
                ByteBufCodecs.VAR_INT, Charge::flash, Charge::new);
        @Override public Type<Charge> type() { return TYPE; }
    }
}
