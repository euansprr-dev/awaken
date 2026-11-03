package dev.awaken.lucidium.net;

import dev.awaken.Awaken;
import dev.awaken.lucidium.dream.Dreams;
import dev.awaken.lucidium.dream.JournalActions;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;
import java.util.Optional;

/** Lucidium payloads. Server to client: the HUD, the journal, Codex visions. Client to server: the H key and journal. */
public final class LucidiumNet {
    private LucidiumNet() {}

    /** The lucidity HUD while dreaming (sent when it changes, at most twice a second). */
    public record DreamHud(boolean dreaming, float lucidity, int ticksLeft, int total, int signs, int spotted, int flags,
                           int shadowless) implements CustomPacketPayload {
        /** TRIAL: a trial dream (SPEC §9.3), drawn by the trial's own HUD instead of the lucidity meter. */
        public static final int FLY = 1, SHAPE = 2, SUMMON = 4, NIGHTMARE = 8, RAIN_UP = 16, LUCID = 32, TRIAL = 64;
        public static final DreamHud AWAKE = new DreamHud(false, 0, 0, 1, 0, 0, 0, -1);
        public static final Type<DreamHud> TYPE = new Type<>(Awaken.id("lucidium_hud"));
        public static final StreamCodec<ByteBuf, DreamHud> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, DreamHud::dreaming, ByteBufCodecs.FLOAT, DreamHud::lucidity,
                ByteBufCodecs.VAR_INT, DreamHud::ticksLeft, ByteBufCodecs.VAR_INT, DreamHud::total,
                ByteBufCodecs.VAR_INT, DreamHud::signs, ByteBufCodecs.VAR_INT, DreamHud::spotted,
                ByteBufCodecs.VAR_INT, DreamHud::flags, ByteBufCodecs.INT, DreamHud::shadowless, DreamHud::new);

        public boolean has(int flag) {
            return (flags & flag) != 0;
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * A trial dream begins (SPEC §9.3): its goal line and the small line under it, the rings and the true path in
     * course order (the client draws them), where the goal stands, whether a bell waits there, and how many dreams came
     * before (after three the arrival line is just "Press H.").
     */
    public record TrialStart(String goal, String sub, List<Vec3> rings, Vec3 end, boolean bell, List<Vec3> path, int dreams)
            implements CustomPacketPayload {
        public static final Type<TrialStart> TYPE = new Type<>(Awaken.id("lucidium_trial_start"));
        public static final StreamCodec<ByteBuf, TrialStart> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(256), TrialStart::goal, ByteBufCodecs.stringUtf8(256), TrialStart::sub,
                Vec3.STREAM_CODEC.apply(ByteBufCodecs.list(64)), TrialStart::rings, Vec3.STREAM_CODEC, TrialStart::end,
                ByteBufCodecs.BOOL, TrialStart::bell, Vec3.STREAM_CODEC.apply(ByteBufCodecs.list(512)), TrialStart::path,
                ByteBufCodecs.VAR_INT, TrialStart::dreams, TrialStart::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * The trial as it stands: phase (0 waiting for H, 1 in control, 2 won), ticks left on the clock and its length,
     * the last ring passed, a line to flash ("" none: a dream sign's seconds, a catch, the prize), and whether the path
     * just glinted. Sent when something changes and once a second.
     */
    /** A Room's goal moved, appeared or hid (SPEC §9.5): where the end's light stands now ({@code shown} false: nowhere yet). */
    public record TrialGoal(boolean shown, Vec3 end) implements CustomPacketPayload {
        public static final Type<TrialGoal> TYPE = new Type<>(Awaken.id("lucidium_trial_goal"));
        public static final StreamCodec<ByteBuf, TrialGoal> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, TrialGoal::shown, Vec3.STREAM_CODEC, TrialGoal::end, TrialGoal::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** A Run's ghost (SPEC §9.6): whose, your own or a friend's, and its samples (DreamLedger.pack, every 4 ticks). */
    public record Ghost(String name, boolean own, List<Integer> samples) {
        public static final StreamCodec<ByteBuf, Ghost> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), Ghost::name, ByteBufCodecs.BOOL, Ghost::own,
                ByteBufCodecs.INT.apply(ByteBufCodecs.list(750)), Ghost::samples, Ghost::new);
    }

    /** The ghosts that run beside you, sent the moment you pass the doorway (their samples start there): from {@code origin}. */
    public record TrialGhosts(Vec3 origin, List<Ghost> ghosts) implements CustomPacketPayload {
        public static final Type<TrialGhosts> TYPE = new Type<>(Awaken.id("lucidium_trial_ghosts"));
        public static final StreamCodec<ByteBuf, TrialGhosts> STREAM_CODEC = StreamCodec.composite(
                Vec3.STREAM_CODEC, TrialGhosts::origin, Ghost.STREAM_CODEC.apply(ByteBufCodecs.list(4)), TrialGhosts::ghosts, TrialGhosts::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** A real place a dream showed you (SPEC §9.5), for the Atlas's pearl mark. */
    public record DreamPlace(BlockPos pos, String label, boolean found) {
        public static final StreamCodec<ByteBuf, DreamPlace> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, DreamPlace::pos, ByteBufCodecs.stringUtf8(256), DreamPlace::label, ByteBufCodecs.BOOL, DreamPlace::found,
                DreamPlace::new);
    }

    public record DreamPlaces(List<DreamPlace> places) implements CustomPacketPayload {
        public static final Type<DreamPlaces> TYPE = new Type<>(Awaken.id("lucidium_dream_places"));
        public static final StreamCodec<ByteBuf, DreamPlaces> STREAM_CODEC = StreamCodec.composite(
                DreamPlace.STREAM_CODEC.apply(ByteBufCodecs.list(64)), DreamPlaces::places, DreamPlaces::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TrialHud(int phase, int left, int total, int ring, String flash, boolean glint) implements CustomPacketPayload {
        public static final Type<TrialHud> TYPE = new Type<>(Awaken.id("lucidium_trial_hud"));
        public static final StreamCodec<ByteBuf, TrialHud> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, TrialHud::phase, ByteBufCodecs.VAR_INT, TrialHud::left, ByteBufCodecs.VAR_INT, TrialHud::total,
                ByteBufCodecs.VAR_INT, TrialHud::ring, ByteBufCodecs.stringUtf8(256), TrialHud::flash, ByteBufCodecs.BOOL, TrialHud::glint,
                TrialHud::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Your bed while a dream waits for you (the client sheds a few pearl motes over it at night); empty: none waits. */
    public record DreamWaiting(Optional<BlockPos> bed) implements CustomPacketPayload {
        public static final DreamWaiting NONE = new DreamWaiting(Optional.empty());
        public static final Type<DreamWaiting> TYPE = new Type<>(Awaken.id("lucidium_dream_waiting"));
        public static final StreamCodec<ByteBuf, DreamWaiting> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.optional(BlockPos.STREAM_CODEC), DreamWaiting::bed, DreamWaiting::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** The pause screen's "Wake up" button in a dream. */
    public record WakeUp() implements CustomPacketPayload {
        public static final WakeUp INSTANCE = new WakeUp();
        public static final Type<WakeUp> TYPE = new Type<>(Awaken.id("lucidium_wake_up"));
        public static final StreamCodec<ByteBuf, WakeUp> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Opens the Dream Journal screen: tonight's intention, whether a dream waits, the seconds your finished dreams give
     * every dream, each finished dream ("title|best · medal · times"), the latest dreams and remembered places.
     */
    /** {@code bell}: a bell waits in one of the dreams (the Journal offers "find a bell"). */
    public record OpenJournal(String intention, boolean waiting, boolean bell, int seconds, List<String> finished, List<String> dreams,
                              List<String> snapshots) implements CustomPacketPayload {
        public static final Type<OpenJournal> TYPE = new Type<>(Awaken.id("lucidium_journal"));
        public static final StreamCodec<ByteBuf, OpenJournal> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, OpenJournal::intention, ByteBufCodecs.BOOL, OpenJournal::waiting, ByteBufCodecs.BOOL, OpenJournal::bell,
                ByteBufCodecs.VAR_INT, OpenJournal::seconds,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(32)), OpenJournal::finished,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(32)), OpenJournal::dreams,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(80)), OpenJournal::snapshots, OpenJournal::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record CodexLine(String id, String category, String title, String body, boolean found) {
        public static final StreamCodec<ByteBuf, CodexLine> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, CodexLine::id, ByteBufCodecs.STRING_UTF8, CodexLine::category,
                ByteBufCodecs.STRING_UTF8, CodexLine::title, ByteBufCodecs.STRING_UTF8, CodexLine::body,
                ByteBufCodecs.BOOL, CodexLine::found, CodexLine::new);
    }

    /** The player's revealed visions and known dream signs for the Codex board. */
    public record CodexSync(List<CodexLine> lines) implements CustomPacketPayload {
        public static final Type<CodexSync> TYPE = new Type<>(Awaken.id("lucidium_codex"));
        public static final StreamCodec<ByteBuf, CodexSync> STREAM_CODEC = StreamCodec.composite(
                CodexLine.STREAM_CODEC.apply(ByteBufCodecs.list(64)), CodexSync::lines, CodexSync::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** H pressed. */
    public record RealityCheck() implements CustomPacketPayload {
        public static final RealityCheck INSTANCE = new RealityCheck();
        public static final Type<RealityCheck> TYPE = new Type<>(Awaken.id("lucidium_reality_check"));
        public static final StreamCodec<ByteBuf, RealityCheck> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** H held long enough while lucid: shape the dream (or, sneaking at 80+, call on a memory). */

    /** Sneak held while lying in bed: a dreamless night. */
    public record Dreamless() implements CustomPacketPayload {
        public static final Dreamless INSTANCE = new Dreamless();
        public static final Type<Dreamless> TYPE = new Type<>(Awaken.id("lucidium_dreamless"));
        public static final StreamCodec<ByteBuf, Dreamless> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** A Dream Journal button: kind "intent" (arg = intention id or ""). */
    public record JournalAction(String kind, String arg) implements CustomPacketPayload {
        public static final Type<JournalAction> TYPE = new Type<>(Awaken.id("lucidium_journal_action"));
        public static final StreamCodec<ByteBuf, JournalAction> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(32), JournalAction::kind, ByteBufCodecs.stringUtf8(64), JournalAction::arg, JournalAction::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(LucidiumNet::register);
    }

    private static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(DreamHud.TYPE, DreamHud.STREAM_CODEC);
        r.playToClient(OpenJournal.TYPE, OpenJournal.STREAM_CODEC);
        r.playToClient(CodexSync.TYPE, CodexSync.STREAM_CODEC);
        r.playToClient(TrialStart.TYPE, TrialStart.STREAM_CODEC);
        r.playToClient(TrialHud.TYPE, TrialHud.STREAM_CODEC);
        r.playToClient(TrialGoal.TYPE, TrialGoal.STREAM_CODEC);
        r.playToClient(TrialGhosts.TYPE, TrialGhosts.STREAM_CODEC);
        r.playToClient(DreamPlaces.TYPE, DreamPlaces.STREAM_CODEC);
        r.playToClient(DreamWaiting.TYPE, DreamWaiting.STREAM_CODEC);
        r.playToServer(WakeUp.TYPE, WakeUp.STREAM_CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) wakeUp(sp);
        });
        r.playToServer(RealityCheck.TYPE, RealityCheck.STREAM_CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) realityCheck(sp);
        });
        r.playToServer(Dreamless.TYPE, Dreamless.STREAM_CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) dev.awaken.lucidium.dream.Sleep.chooseDreamless(sp);
        });
        r.playToServer(JournalAction.TYPE, JournalAction.STREAM_CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) journalAction(sp, p);
        });
    }

    // client-sent actions are rate-limited per player (SPEC "Together"): a dedicated server never works faster than a
    // person presses keys

    /** A reality check from the client: at most one per 10 ticks. Returns whether it was handled. */
    public static boolean realityCheck(ServerPlayer sp) {
        if (!dev.awaken.core.Throttle.allow(sp, "reality_check", 10)) return false;
        Dreams.realityCheck(sp);
        return true;
    }

    /** The Wake up button: at most once a second; only while dreaming. Returns whether it was handled. */
    public static boolean wakeUp(ServerPlayer sp) {
        if (!dev.awaken.core.Throttle.allow(sp, "dream_wake_up", 20)) return false;
        if (!Dreams.isDreaming(sp)) return false;
        Dreams.wake(sp, Dreams.Wake.CHOSE);
        return true;
    }

    /** A journal action from the client: at most 4 a second. Returns whether it was handled. */
    public static boolean journalAction(ServerPlayer sp, JournalAction action) {
        if (!dev.awaken.core.Throttle.allowBurst(sp, "journal_action", 4, 20)) return false;
        JournalActions.handle(sp, action.kind(), action.arg());
        return true;
    }
}
