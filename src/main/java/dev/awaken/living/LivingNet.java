package dev.awaken.living;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The living module's small client events (SPEC §21.1, §21.2). Everything the player sees is built on the client from
 * these; health, the tag and loot pillars ride synced entity data and attachments.
 */
public final class LivingNet {
    private LivingNet() {}

    /**
     * The player crossed into a region. {@code caption}: show the place caption (first time this play-day);
     * {@code danger}: the region is far above the player (ember caption).
     */
    public record RegionMsg(String name, int low, int high, boolean commons, boolean caption, boolean danger) implements CustomPacketPayload {
        public static final Type<RegionMsg> TYPE = new Type<>(Awaken.id("living_region"));
        public static final StreamCodec<ByteBuf, RegionMsg> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), RegionMsg::name,
                ByteBufCodecs.VAR_INT, RegionMsg::low,
                ByteBufCodecs.VAR_INT, RegionMsg::high,
                ByteBufCodecs.BOOL, RegionMsg::commons,
                ByteBufCodecs.BOOL, RegionMsg::caption,
                ByteBufCodecs.BOOL, RegionMsg::danger,
                RegionMsg::new);

        @Override
        public Type<RegionMsg> type() {
            return TYPE;
        }
    }

    /**
     * A hit the player dealt (the damage the target took, crit, Force or -1), or with {@code incoming} a hit the player
     * took from {@code entity} (no glyph: it marks that creature as in a fight).
     */
    public record HitFx(int entity, float amount, boolean crit, int force, boolean incoming) implements CustomPacketPayload {
        public static final Type<HitFx> TYPE = new Type<>(Awaken.id("living_hit_fx"));
        public static final StreamCodec<ByteBuf, HitFx> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, HitFx::entity,
                ByteBufCodecs.FLOAT, HitFx::amount,
                ByteBufCodecs.BOOL, HitFx::crit,
                ByteBufCodecs.VAR_INT, HitFx::force,
                ByteBufCodecs.BOOL, HitFx::incoming,
                HitFx::new);

        @Override
        public Type<HitFx> type() {
            return TYPE;
        }
    }

    /** Kinds of XP the stream tells apart (colour and size). */
    public static final int XP_KILL = 0, XP_HARVEST = 1, XP_DISCOVERY = 2, XP_QUEST = 3, XP_OTHER = 4;

    /** XP the player got: how much (as added), what for, and where it came from (when {@code origin}). */
    public record XpFx(int xp, int kind, boolean origin, float x, float y, float z) implements CustomPacketPayload {
        public static final Type<XpFx> TYPE = new Type<>(Awaken.id("living_xp_fx"));
        public static final StreamCodec<ByteBuf, XpFx> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, XpFx::xp,
                ByteBufCodecs.VAR_INT, XpFx::kind,
                ByteBufCodecs.BOOL, XpFx::origin,
                ByteBufCodecs.FLOAT, XpFx::x,
                ByteBufCodecs.FLOAT, XpFx::y,
                ByteBufCodecs.FLOAT, XpFx::z,
                XpFx::new);

        @Override
        public Type<XpFx> type() {
            return TYPE;
        }
    }

    /**
     * A level reached, for the moment (SPEC §21.1). Sent to the player and to players within 48 blocks, who draw only the
     * rings and the column. {@code rankLine}: "D-RANK · 10 LEVELS TO C" (empty for others).
     */
    public record LevelUp(int entity, int level, int points, String rankLine) implements CustomPacketPayload {
        public static final Type<LevelUp> TYPE = new Type<>(Awaken.id("living_level_up"));
        public static final StreamCodec<ByteBuf, LevelUp> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, LevelUp::entity,
                ByteBufCodecs.VAR_INT, LevelUp::level,
                ByteBufCodecs.VAR_INT, LevelUp::points,
                ByteBufCodecs.stringUtf8(64), LevelUp::rankLine,
                LevelUp::new);

        @Override
        public Type<LevelUp> type() {
            return TYPE;
        }
    }

    /** Whether this world shows the band's layer (the {@code awaken:pure_survival} game rule is off). Sent on login and change. */
    public record Shown(boolean shown) implements CustomPacketPayload {
        public static final Type<Shown> TYPE = new Type<>(Awaken.id("living_shown"));
        public static final StreamCodec<ByteBuf, Shown> STREAM_CODEC = ByteBufCodecs.BOOL.map(Shown::new, Shown::shown);

        @Override
        public Type<Shown> type() {
            return TYPE;
        }
    }

    /** One Journal line: a chain's title and its current step (or a bounty's), and whether it's the tracked one. */
    public record JournalEntry(String title, String line, boolean tracked, boolean bounty) {
        public static final StreamCodec<ByteBuf, JournalEntry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, JournalEntry::title,
                ByteBufCodecs.STRING_UTF8, JournalEntry::line,
                ByteBufCodecs.BOOL, JournalEntry::tracked,
                ByteBufCodecs.BOOL, JournalEntry::bounty,
                JournalEntry::new);
    }

    /** The player's Journal (chains taken, bounties taken), sent whenever it changes and on login (SPEC §21.0.7). */
    public record Journal(java.util.List<JournalEntry> entries) implements CustomPacketPayload {
        public static final Type<Journal> TYPE = new Type<>(Awaken.id("living_journal"));
        public static final StreamCodec<ByteBuf, Journal> STREAM_CODEC =
                JournalEntry.STREAM_CODEC.apply(ByteBufCodecs.list(16)).map(Journal::new, Journal::entries);

        @Override
        public Type<Journal> type() {
            return TYPE;
        }
    }

    /** Where this player's open scenes are (SPEC §21.5): each sends up a distress flare now and then. Every 5 s. */
    public record Tells(java.util.List<net.minecraft.core.BlockPos> at) implements CustomPacketPayload {
        public static final Type<Tells> TYPE = new Type<>(Awaken.id("living_tells"));
        public static final StreamCodec<ByteBuf, Tells> STREAM_CODEC =
                net.minecraft.core.BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(8)).map(Tells::new, Tells::at);

        @Override
        public Type<Tells> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent e) {
        var r = e.registrar("1");
        r.playToClient(Journal.TYPE, Journal.STREAM_CODEC);
        r.playToClient(Tells.TYPE, Tells.STREAM_CODEC);
        r.playToClient(RegionMsg.TYPE, RegionMsg.STREAM_CODEC);
        r.playToClient(HitFx.TYPE, HitFx.STREAM_CODEC);
        r.playToClient(XpFx.TYPE, XpFx.STREAM_CODEC);
        r.playToClient(LevelUp.TYPE, LevelUp.STREAM_CODEC);
        r.playToClient(Shown.TYPE, Shown.STREAM_CODEC);
    }
}
