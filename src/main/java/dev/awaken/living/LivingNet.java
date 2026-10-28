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
     * The level of the land under the player (SPEC §21.10), sent when it changes: the client's tint and sound bed read it
     * against the player's own level. -1: no land (another dimension), nothing weighs.
     */
    public record LandMsg(int level) implements CustomPacketPayload {
        public static final Type<LandMsg> TYPE = new Type<>(Awaken.id("living_land"));
        public static final StreamCodec<ByteBuf, LandMsg> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, LandMsg::level, LandMsg::new);

        @Override
        public Type<LandMsg> type() {
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

    /**
     * A Rolling Storm strike (SPEC §21.6), to players within {@code StormCell.HEARD}: the game sends its bolt only to 256
     * blocks, so farther off the client flashes the cloud and thunders from this alone.
     */
    public record StormStrike(float x, float y, float z) implements CustomPacketPayload {
        public static final Type<StormStrike> TYPE = new Type<>(Awaken.id("living_storm_strike"));
        public static final StreamCodec<ByteBuf, StormStrike> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.FLOAT, StormStrike::x,
                ByteBufCodecs.FLOAT, StormStrike::y, ByteBufCodecs.FLOAT, StormStrike::z, StormStrike::new);

        @Override
        public Type<StormStrike> type() {
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

    /**
     * One happening's tell (SPEC §21.6): which kind of tell, where it is now, how big, how far along (0..1), and its line's
     * direction (dx, dz; zero for one that stays put).
     */
    public record HappeningTell(int id, String tell, float x, float y, float z, int radius, float progress, int dx, int dz) {
        public static final StreamCodec<ByteBuf, HappeningTell> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public HappeningTell decode(ByteBuf b) {
                return new HappeningTell(ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.STRING_UTF8.decode(b), b.readFloat(), b.readFloat(),
                        b.readFloat(), ByteBufCodecs.VAR_INT.decode(b), b.readFloat(), b.readInt(), b.readInt());
            }

            @Override
            public void encode(ByteBuf b, HappeningTell t) {
                ByteBufCodecs.VAR_INT.encode(b, t.id());
                ByteBufCodecs.STRING_UTF8.encode(b, t.tell());
                b.writeFloat(t.x());
                b.writeFloat(t.y());
                b.writeFloat(t.z());
                ByteBufCodecs.VAR_INT.encode(b, t.radius());
                b.writeFloat(t.progress());
                b.writeInt(t.dx());
                b.writeInt(t.dz());
            }
        };
    }

    /** The happenings within 512 blocks of the player, every 2 s (an empty list once when the last goes). */
    public record Happenings(java.util.List<HappeningTell> tells) implements CustomPacketPayload {
        public static final Type<Happenings> TYPE = new Type<>(Awaken.id("living_happenings"));
        public static final StreamCodec<ByteBuf, Happenings> STREAM_CODEC =
                HappeningTell.STREAM_CODEC.apply(ByteBufCodecs.list(16)).map(Happenings::new, Happenings::tells);

        @Override
        public Type<Happenings> type() {
            return TYPE;
        }
    }

    /** Server → client: the Wayfarer camp's fire, so its smoke can be seen from afar (SPEC §27.6; every 5 s while near). */
    public record CampSmoke(net.minecraft.core.BlockPos at) implements CustomPacketPayload {
        public static final Type<CampSmoke> TYPE = new Type<>(Awaken.id("living_camp_smoke"));
        public static final StreamCodec<ByteBuf, CampSmoke> STREAM_CODEC = net.minecraft.core.BlockPos.STREAM_CODEC.map(CampSmoke::new, CampSmoke::at);

        @Override
        public Type<CampSmoke> type() {
            return TYPE;
        }
    }

    /**
     * Server → client: the first thread's staged moments (SPEC §27.6). {@code GATE}: the High Gate's shot of the camp's
     * smoke ({@code at} the fire, {@code n} metres to it, {@code text} "who|line" said over it, then on a second line
     * "land|low|high", the region it names). {@code BEGIN}: Ilse's
     * scene by her fire ({@code entity} her, {@code at} the fire). {@code LINE}: one of her lines ("who|line", {@code n}
     * ticks). {@code DELIVERED}: the letters changed hands. {@code OFFER}: she holds the coal out ({@code entity}).
     * {@code END}: the scene is over.
     */
    public record FirstScene(int kind, int entity, net.minecraft.core.BlockPos at, int n, String text) implements CustomPacketPayload {
        public static final int GATE = 0, BEGIN = 1, LINE = 2, DELIVERED = 3, OFFER = 4, END = 5;
        public static final Type<FirstScene> TYPE = new Type<>(Awaken.id("living_first_scene"));
        public static final StreamCodec<ByteBuf, FirstScene> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, FirstScene::kind,
                ByteBufCodecs.VAR_INT, FirstScene::entity,
                net.minecraft.core.BlockPos.STREAM_CODEC, FirstScene::at,
                ByteBufCodecs.VAR_INT, FirstScene::n,
                ByteBufCodecs.stringUtf8(512), FirstScene::text,
                FirstScene::new);

        @Override
        public Type<FirstScene> type() {
            return TYPE;
        }
    }

    /** Client → server: the coal taken from Ilse's hand ({@code TOOK}), or a held skip ({@code SKIP}). */
    public record FirstSceneReady(int what) implements CustomPacketPayload {
        public static final int TOOK = 0, SKIP = 1;
        public static final Type<FirstSceneReady> TYPE = new Type<>(Awaken.id("living_first_scene_ready"));
        public static final StreamCodec<ByteBuf, FirstSceneReady> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(FirstSceneReady::new, FirstSceneReady::what);

        @Override
        public Type<FirstSceneReady> type() {
            return TYPE;
        }
    }

    /**
     * Server → client: a boss bar belongs to {@code entity} (SPEC §34.1, {@code core.api.BossPlates}): the client draws the
     * Boss Plate for it instead of vanilla's bar. {@code kind}: BossPlates.Kind ordinal; {@code marks}: the health fractions
     * where phases begin; {@code glow}: the boss's own colour (the plate's rim).
     */
    public record BossBind(java.util.UUID bar, int entity, int kind, java.util.List<Float> marks, int glow) implements CustomPacketPayload {
        public static final Type<BossBind> TYPE = new Type<>(Awaken.id("living_boss_bind"));
        public static final StreamCodec<ByteBuf, BossBind> STREAM_CODEC = StreamCodec.composite(
                net.minecraft.core.UUIDUtil.STREAM_CODEC, BossBind::bar,
                ByteBufCodecs.VAR_INT, BossBind::entity,
                ByteBufCodecs.VAR_INT, BossBind::kind,
                ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list(8)), BossBind::marks,
                ByteBufCodecs.INT, BossBind::glow,
                BossBind::new);

        @Override
        public Type<BossBind> type() {
            return TYPE;
        }
    }

    /** Server → client: a line under a boss's plate ({@code kind}: BossPlates.Line ordinal; {@code colour} RGB). */
    public record BossLine(java.util.UUID bar, String text, int kind, int colour) implements CustomPacketPayload {
        public static final Type<BossLine> TYPE = new Type<>(Awaken.id("living_boss_line"));
        public static final StreamCodec<ByteBuf, BossLine> STREAM_CODEC = StreamCodec.composite(
                net.minecraft.core.UUIDUtil.STREAM_CODEC, BossLine::bar,
                ByteBufCodecs.stringUtf8(256), BossLine::text,
                ByteBufCodecs.VAR_INT, BossLine::kind,
                ByteBufCodecs.INT, BossLine::colour,
                BossLine::new);

        @Override
        public Type<BossLine> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent e) {
        var r = e.registrar("1");
        r.playToClient(FirstScene.TYPE, FirstScene.STREAM_CODEC);
        r.playToServer(FirstSceneReady.TYPE, FirstSceneReady.STREAM_CODEC, (m, ctx) -> {
            if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp) FirstScenes.ready(sp, m.what());
        });
        r.playToClient(Happenings.TYPE, Happenings.STREAM_CODEC);
        r.playToClient(Journal.TYPE, Journal.STREAM_CODEC);
        r.playToClient(Tells.TYPE, Tells.STREAM_CODEC);
        r.playToClient(CampSmoke.TYPE, CampSmoke.STREAM_CODEC);
        r.playToClient(RegionMsg.TYPE, RegionMsg.STREAM_CODEC);
        r.playToClient(LandMsg.TYPE, LandMsg.STREAM_CODEC);
        r.playToClient(HitFx.TYPE, HitFx.STREAM_CODEC);
        r.playToClient(XpFx.TYPE, XpFx.STREAM_CODEC);
        r.playToClient(LevelUp.TYPE, LevelUp.STREAM_CODEC);
        r.playToClient(Shown.TYPE, Shown.STREAM_CODEC);
        r.playToClient(StormStrike.TYPE, StormStrike.STREAM_CODEC);
        r.playToClient(BossBind.TYPE, BossBind.STREAM_CODEC);
        r.playToClient(BossLine.TYPE, BossLine.STREAM_CODEC);
    }
}
