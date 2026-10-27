package dev.awaken.arms.net;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Arms' messages (SPEC §25). The client predicts every move and says what the player pressed; the server decides
 * what happened and answers with {@link Result}; everyone near sees it through {@link Seen}.
 */
public final class ArmsPayloads {
    private ArmsPayloads() {}

    /** What a {@link Press} pressed: a strike's press; the hold becoming a charge; the charge let go; the strike's press let go before it charged (a tap). */
    public static final int PRESS_STRIKE = 0, PRESS_CHARGE = 1, PRESS_RELEASE = 2, PRESS_TAP = 3;

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
            REFUSE = 8, DODGE = 9, RESONANCE = 10, HURT = 11, POSTURE = 12, CHARGE = 13;

    /**
     * Server → the player: what their press or guard did. MOVE: the move the server runs for seq (aux = move id,
     * aux2 = cadence); HIT: entity struck (aux = hit-stop ms, aux2 = the move); DEFLECT / BLOCK / GUARD_BREAK /
     * PERFECT_DODGE / EVADE: entity = the attacker; BREAK: entity's stance broke; REFUSE: seq refused; RESONANCE:
     * aux = the meter; HURT: a blow landed on you (entity = who struck, aux = the yaw it came from, aux2 = damage in
     * tenths).
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
     * DODGE (aux = yaw of the way), DEFLECT / BLOCK / GUARD_BREAK (other = the attacker), BREAK (entity = whose stance),
     * HIT (entity = struck, other = the fighter, aux = hit-stop ms, aux2 = the move), POSTURE (entity's stance changed:
     * other = broken ticks left, aux = value in tenths, aux2 = its hold; also sent to the fighter), CHARGE (a heavy is
     * being held).
     */
    /** Server → the fighter: every Weave they have woven (ids), on joining and after each new one (the Codex reads it). */
    public record Woven(java.util.List<String> weaves) implements CustomPacketPayload {
        public static final Type<Woven> TYPE = new Type<>(Awaken.id("arms_woven"));
        public static final StreamCodec<ByteBuf, Woven> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64)), Woven::weaves, Woven::new);
        @Override public Type<Woven> type() { return TYPE; }
    }

    /** Server → the fighter: Hale's issue, the styles on offer (docs/arms-progression/PLAN.md §3.3); opens the pick on their screen. */
    public record IssueOffer(java.util.List<String> styles) implements CustomPacketPayload {
        public static final Type<IssueOffer> TYPE = new Type<>(Awaken.id("arms_issue_offer"));
        public static final StreamCodec<ByteBuf, IssueOffer> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(8)), IssueOffer::styles, IssueOffer::new);
        @Override public Type<IssueOffer> type() { return TYPE; }
    }

    /** Fighter → server: the style they took from Hale's issue. The server checks that it was offered and is still theirs to take. */
    public record IssuePick(String style) implements CustomPacketPayload {
        public static final Type<IssuePick> TYPE = new Type<>(Awaken.id("arms_issue_pick"));
        public static final StreamCodec<ByteBuf, IssuePick> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, IssuePick::style, IssuePick::new);
        @Override public Type<IssuePick> type() { return TYPE; }
    }

    /** Every style's mastery step, by the style's index (the client predicts the mastery strike, Slipcut and Art). */
    public record Mastery(java.util.List<Integer> steps) implements CustomPacketPayload {
        public static final Type<Mastery> TYPE = new Type<>(Awaken.id("arms_mastery"));
        public static final StreamCodec<ByteBuf, Mastery> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(64)), Mastery::steps, Mastery::new);
        @Override public Type<Mastery> type() { return TYPE; }
    }

    public record Seen(int entity, int what, int other, int aux, int aux2) implements CustomPacketPayload {
        public static final Type<Seen> TYPE = new Type<>(Awaken.id("arms_seen"));
        public static final StreamCodec<ByteBuf, Seen> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Seen::entity, ByteBufCodecs.VAR_INT, Seen::what, ByteBufCodecs.VAR_INT, Seen::other,
                ByteBufCodecs.VAR_INT, Seen::aux, ByteBufCodecs.VAR_INT, Seen::aux2, Seen::new);
        @Override public Type<Seen> type() { return TYPE; }
    }

    // ---- the Armoury (docs/arms-progression/PLAN.md §3.2) ------------------------------------------------------------

    /** One style in the Armoury's list: 0 known, 1 on Hale's rack for its price in essence (not known yet), and whether the fighter can pay now. */
    public record StyleEntry(String id, int state, int price, boolean afford) {
        public static final StreamCodec<ByteBuf, StyleEntry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, StyleEntry::id, ByteBufCodecs.VAR_INT, StyleEntry::state,
                ByteBufCodecs.VAR_INT, StyleEntry::price, ByteBufCodecs.BOOL, StyleEntry::afford, StyleEntry::new);
    }

    /** Server → the fighter: the Armoury's list (door 0 the band, 1 Hale's rack, 2 the Forge), their essence, how many styles aren't shown yet and at which rank Hale shows more, and the tracked weapon ("daggers:E" or ""). */
    public record ArmouryView(int door, int essence, java.util.List<StyleEntry> styles, int hidden, String hiddenRank, String tracked, boolean open)
            implements CustomPacketPayload {
        public static final Type<ArmouryView> TYPE = new Type<>(Awaken.id("arms_armoury_view"));
        public static final StreamCodec<ByteBuf, ArmouryView> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ArmouryView::door, ByteBufCodecs.VAR_INT, ArmouryView::essence,
                StyleEntry.STREAM_CODEC.apply(ByteBufCodecs.list(32)), ArmouryView::styles,
                ByteBufCodecs.VAR_INT, ArmouryView::hidden, ByteBufCodecs.STRING_UTF8, ArmouryView::hiddenRank,
                ByteBufCodecs.STRING_UTF8, ArmouryView::tracked, ByteBufCodecs.BOOL, ArmouryView::open, ArmouryView::new);
        @Override public Type<ArmouryView> type() { return TYPE; }
    }

    /** A tier of a style: its letter, the level it opens at and whether the fighter has reached it. */
    public record TierRow(String tier, int level, boolean reached) {
        public static final StreamCodec<ByteBuf, TierRow> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, TierRow::tier, ByteBufCodecs.VAR_INT, TierRow::level, ByteBufCodecs.BOOL, TierRow::reached, TierRow::new);
    }

    /** One thing a weapon needs: "tag" or "item", its id, how many, and how many the fighter holds. */
    public record NeedRow(String kind, String id, int need, int have) {
        public static final StreamCodec<ByteBuf, NeedRow> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, NeedRow::kind, ByteBufCodecs.STRING_UTF8, NeedRow::id,
                ByteBufCodecs.VAR_INT, NeedRow::need, ByteBufCodecs.VAR_INT, NeedRow::have, NeedRow::new);
    }

    /** Server → the fighter: one style's tiers and what the chosen tier needs (have against need). */
    public record ArmouryDetail(String style, String tier, java.util.List<TierRow> tiers, java.util.List<NeedRow> needs) implements CustomPacketPayload {
        public static final Type<ArmouryDetail> TYPE = new Type<>(Awaken.id("arms_armoury_detail"));
        public static final StreamCodec<ByteBuf, ArmouryDetail> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ArmouryDetail::style, ByteBufCodecs.STRING_UTF8, ArmouryDetail::tier,
                TierRow.STREAM_CODEC.apply(ByteBufCodecs.list(8)), ArmouryDetail::tiers,
                NeedRow.STREAM_CODEC.apply(ByteBufCodecs.list(16)), ArmouryDetail::needs, ArmouryDetail::new);
        @Override public Type<ArmouryDetail> type() { return TYPE; }
    }

    /** Fighter → server: ask for a style's detail, buy a pattern at Hale's, draw a copy at the Forge, or track or untrack a weapon (see ArmsArmoury.Op). */
    public record ArmouryAct(int op, String style, String tier) implements CustomPacketPayload {
        public static final Type<ArmouryAct> TYPE = new Type<>(Awaken.id("arms_armoury_act"));
        public static final StreamCodec<ByteBuf, ArmouryAct> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ArmouryAct::op, ByteBufCodecs.STRING_UTF8, ArmouryAct::style, ByteBufCodecs.STRING_UTF8, ArmouryAct::tier, ArmouryAct::new);
        @Override public Type<ArmouryAct> type() { return TYPE; }
    }
}
