package dev.awaken.arms.anvil;

import dev.awaken.Awaken;
import dev.awaken.hearth.Upgrades;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** The War Anvil's messages (SPEC §46.2, §46.8). */
public final class AnvilPayloads {
    private AnvilPayloads() {}

    /**
     * What the smith did: one of the acts below, its beat's number (a), when (t, ticks from the stage's start) and its aim.
     * By hand at E: KNAP a flake (a its number), WIND the cord (a 0 as the hold begins, then each turn done), RELEASE it.
     * From the window for a weapon in hand (§46.8): HELD begins one (a: AnvilWork.TEMPER, REFORGE or ATTUNE, plus for attuning the Force's ordinal + 1 << 8; 0 there means the off hand's stone), LIST opens
     * the Armoury instead. The ranks' acts (§46.9): TWIST the clamped bar (a 0 as the hold begins, then each turn; RELEASE
     * lets go early), GRIND a sample (a its number, aim how far the edge is off the line), BALANCE it settled (t when, aim
     * how far the weight was from its point), STAR the press as the light's ring closes.
     */
    public static final int RESUME = 1, LEAVE = 2, PULL = 3, STRIKE = 4, PLUNGE = 5, LIFT = 6, TAP = 7, DRAW = 8, KNAP = 9, WIND = 10, RELEASE = 11,
            HELD = 12, LIST = 13, TWIST = 14, GRIND = 15, BALANCE = 16, STAR = 17,
            /** Backs out of a pick's work before the first blow: everything comes back (§49.4). */
            CANCEL = 18;

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

    /** One thing it costs: what, how many you have, how many it takes, and the real item (or item tag) so the Hall can show it. */
    public record Cost(String label, int have, int need, String id, boolean tag) {
        public static final StreamCodec<ByteBuf, Cost> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Cost::label, ByteBufCodecs.VAR_INT, Cost::have, ByteBufCodecs.VAR_INT, Cost::need,
                ByteBufCodecs.STRING_UTF8, Cost::id, ByteBufCodecs.BOOL, Cost::tag, Cost::new);
    }

    /** One number a row would change. */
    private static final StreamCodec<ByteBuf, Upgrades.Change> CHANGE = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Upgrades.Change::label, ByteBufCodecs.STRING_UTF8, Upgrades.Change::from,
            ByteBufCodecs.STRING_UTF8, Upgrades.Change::to, ByteBufCodecs.STRING_UTF8, Upgrades.Change::best, Upgrades.Change::new);

    /** A plain sentence on a row's page. The tone: an ordinary line, the Force you're about to give it, what's on it now, or one that isn't yours to pick. */
    public record Say(int tone, String text) {
        public static final int PLAIN = 0, CHOSEN = 1, NOW = 2, QUIET = 3;
        public static final StreamCodec<ByteBuf, Say> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Say::tone, ByteBufCodecs.STRING_UTF8, Say::text, Say::new);
    }

    /**
     * One row of the window: its kind (AnvilWork.TEMPER, REFORGE, ATTUNE), what it's called, its button, whether it can
     * begin, one plain line for what's in the way, what it costs; and what it would do to this weapon, whether or not it can
     * begin yet: a short headline, the numbers before and after, and the plain words.
     */
    public record Option(int kind, String name, String button, boolean ready, String line, List<Cost> costs,
                         String headline, List<Upgrades.Change> changes, List<Say> says) {
        public static final StreamCodec<ByteBuf, Option> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Option::kind, ByteBufCodecs.STRING_UTF8, Option::name, ByteBufCodecs.STRING_UTF8, Option::button,
                ByteBufCodecs.BOOL, Option::ready, ByteBufCodecs.STRING_UTF8, Option::line, Cost.STREAM_CODEC.apply(ByteBufCodecs.list()), Option::costs,
                ByteBufCodecs.STRING_UTF8, Option::headline, CHANGE.apply(ByteBufCodecs.list()), Option::changes,
                Say.STREAM_CODEC.apply(ByteBufCodecs.list()), Option::says, Option::new);
    }

    /**
     * One Force you could set in the weapon (SPEC §52): its name and colour, its stone (the real item, what it's called, how
     * many you carry), what it adds to a blow, whether it can begin, one plain line for what's in the way and all it costs.
     * Whether it's the one in your off hand, and whether the weapon carries it already.
     */
    public record Stone(int force, String name, int colour, String item, String stone, int have, String touch, boolean ready, String line,
                        List<Cost> costs, boolean held, boolean on) {
        private static final StreamCodec<ByteBuf, List<Cost>> COSTS = Cost.STREAM_CODEC.apply(ByteBufCodecs.list());
        public static final StreamCodec<ByteBuf, Stone> STREAM_CODEC = new StreamCodec<>() {
            @Override public Stone decode(ByteBuf b) {
                return new Stone(ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.STRING_UTF8.decode(b), ByteBufCodecs.INT.decode(b), ByteBufCodecs.STRING_UTF8.decode(b),
                        ByteBufCodecs.STRING_UTF8.decode(b), ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.STRING_UTF8.decode(b), ByteBufCodecs.BOOL.decode(b),
                        ByteBufCodecs.STRING_UTF8.decode(b), COSTS.decode(b), ByteBufCodecs.BOOL.decode(b), ByteBufCodecs.BOOL.decode(b));
            }

            @Override public void encode(ByteBuf b, Stone s) {
                ByteBufCodecs.VAR_INT.encode(b, s.force);
                ByteBufCodecs.STRING_UTF8.encode(b, s.name);
                ByteBufCodecs.INT.encode(b, s.colour);
                ByteBufCodecs.STRING_UTF8.encode(b, s.item);
                ByteBufCodecs.STRING_UTF8.encode(b, s.stone);
                ByteBufCodecs.VAR_INT.encode(b, s.have);
                ByteBufCodecs.STRING_UTF8.encode(b, s.touch);
                ByteBufCodecs.BOOL.encode(b, s.ready);
                ByteBufCodecs.STRING_UTF8.encode(b, s.line);
                COSTS.encode(b, s.costs);
                ByteBufCodecs.BOOL.encode(b, s.held);
                ByteBufCodecs.BOOL.encode(b, s.on);
            }
        };
    }

    /**
     * Server → the smith: the anvil's window for the weapon in your hand (temper, reforge, attune; §46.8). Each Force it could
     * be attuned to, and the weapon it would reforge into ("" at the top tier), for the Hall's view of it (§52).
     */
    public record Held(BlockPos pos, List<Option> options, List<Stone> stones, String next) implements CustomPacketPayload {
        public static final Type<Held> TYPE = new Type<>(Awaken.id("anvil_held"));
        public static final StreamCodec<ByteBuf, Held> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Held::pos, Option.STREAM_CODEC.apply(ByteBufCodecs.list()), Held::options,
                Stone.STREAM_CODEC.apply(ByteBufCodecs.list()), Held::stones, ByteBufCodecs.STRING_UTF8, Held::next, Held::new);
        @Override public Type<Held> type() { return TYPE; }

        /** A weapon's three rites, which the Hall stages; a forged pick's offers are other rows (SPEC §49.4). */
        public boolean rites() {
            return options.size() == 3 && options.getFirst().kind() == AnvilWork.TEMPER;
        }
    }

    /**
     * Server → the smith: the weapon is made (or worked) and stands in the slot; how well, its flaw, the Temper it left
     * with, and flags (WarAnvil.TWIN, FROM_MEMORY, ARMOURY, TEMPERED, ATTUNED, REFORGED).
     */
    public record Done(BlockPos pos, int quality, int flaw, int temper, int flags) implements CustomPacketPayload {
        public static final Type<Done> TYPE = new Type<>(Awaken.id("anvil_done"));
        public static final StreamCodec<ByteBuf, Done> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Done::pos, ByteBufCodecs.VAR_INT, Done::quality, ByteBufCodecs.VAR_INT, Done::flaw,
                ByteBufCodecs.VAR_INT, Done::temper, ByteBufCodecs.VAR_INT, Done::flags, Done::new);
        @Override public Type<Done> type() { return TYPE; }
    }
}
