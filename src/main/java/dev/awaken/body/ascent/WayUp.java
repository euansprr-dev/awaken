package dev.awaken.body.ascent;

import dev.awaken.above.ruin.RuinKind;
import dev.awaken.above.ruin.RuinLocator;
import dev.awaken.body.BodyConfig;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.api.Goals;
import dev.awaken.core.api.NowHooks;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.signal.Signal;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The Way Up (SPEC §63; plan docs/movement-unlocks/PLAN.md, Euan 2026-11-10: "it shouldn't be impossible to unlock the
 * future movement ... based on rank assessments, and then that unlocks somewhere ... that you can go and unlock that item,
 * and then it actually shows you how to use it").
 *
 * <p>One rule for the ladder: a rank fight opens a rung, a place gives it, and the way out of the place is the move.
 * Nothing here counts in secret. What a player is owed follows from what is true of them (their rank, the rungs they
 * hold), read every five seconds; the only things remembered are the place that woke for a rung, whether it has been
 * told, and which lessons are done or owed ({@link WayMemory}).
 *
 * <ul>
 * <li>The rope: D-rank without it. A vault wakes (one the player has found, else the nearest, else the spawn vault); the
 * Tether Vow gives the rope in its forecourt ({@link TetherVow#ready}).</li>
 * <li>The leap: the rope without it. The first crown reached by the line opens the Leaper's Gauntlet at its foot
 * ({@link #crownReached}); Tether mastery opens it too ({@link AscentTrials#leaperDue}). The gate is a Tasks row with its
 * place and is opened again if it is ever gone.</li>
 * <li>Wings: C-rank with the rope, without them. A sky platform wakes. Its core gives wings as before; then the first
 * flight begins off its pier ({@link #firstFlight}), and for a minute a fall is a soft landing.</li>
 * <li>The open sky stays inside the A rank fight ({@link AscentTrials#skyEdge}).</li>
 * <li>The Sovereign's form: Sovereign rank (the last rank fight's win).</li>
 * </ul>
 * A rung handed down by a later one (a core gives the leap, the Sky Warden's roof can give everything under flight) is
 * flagged "lesson owed", and the client teaches it the next time the player is free.
 */
public final class WayUp {
    private WayUp() {}

    /** The rungs above the Runner, in the ladder's order (the same as {@link Ascent.Tier}). */
    public enum Rung {
        ROPE(Ascent.Tier.TETHER), LEAP(Ascent.Tier.LEAPER), WINGS(Ascent.Tier.WINGS), SKY(Ascent.Tier.SKYBORN), FORM(Ascent.Tier.SOVEREIGN);

        public static final Rung[] VALUES = values();
        public final Ascent.Tier tier;

        Rung(Ascent.Tier tier) {
            this.tier = tier;
        }

        public int bit() {
            return 1 << ordinal();
        }

        static Rung of(Ascent.Tier t) {
            return VALUES[t.ordinal()];
        }
    }

    /**
     * The six moves Rope and Wings added (SPEC §62), each with its own lesson (§63.8): the sling comes with the rope, the
     * spring at Tether mastery 3, the rope strike at 5, the flare with wings, the skim at Wings mastery 3, the takeoff
     * with flight. Whether one has been made is the count Rope and Wings already keeps; nothing new is saved.
     */
    public enum Move {
        SLING(Rung.ROPE, AscentGoals.SLUNG), SPRING(Rung.ROPE, AscentGoals.SPRUNG), STRIKE(Rung.ROPE, AscentGoals.STRUCK),
        FLARE(Rung.WINGS, AscentGoals.FLARED), SKIM(Rung.WINGS, AscentGoals.SKIMMED), TAKEOFF(Rung.SKY, AscentGoals.TOOK_OFF);

        public static final Move[] VALUES = values();
        public final Rung rung;
        final String counter;

        Move(Rung rung, String counter) {
            this.rung = rung;
            this.counter = counter;
        }

        public int bit() {
            return 1 << ordinal();
        }
    }

    /** The moves this player has made at least once, a bit each ({@link Move#bit}). */
    static int moves(ServerPlayer p) {
        var profile = Progression.profile(p);
        int bits = 0;
        for (Move m : Move.VALUES) if (profile.counter(m.counter) > 0) bits |= m.bit();
        return bits;
    }

    /** One of the six moves was made and counted (AscentServer.made): the client hears at once, so its lesson ends for good. */
    static void moveMade(ServerPlayer p) {
        if (BodyConfig.wayUp()) sync(p, memory(p), -1);
    }

    // ---- the leap, a move at a time (SPEC §71.3) -----------------------------------------------------------------------

    /** The leap's moves, in the order the Leaper's Gauntlet's stands give them. */
    public enum Leap implements net.minecraft.util.StringRepresentable {
        DOUBLE("double", "Double jumps"), DASH("dash", "Dashes"), SLAM("slam", "Slams"), DIVE("dive", "Dives");

        public static final Leap[] VALUES = values();
        private final String id;
        /** What a run's count of it is called on the tally. */
        public final String counted;

        Leap(String id, String counted) {
            this.id = id;
            this.counted = counted;
        }

        public int bit() {
            return 1 << ordinal();
        }

        @Override
        public String getSerializedName() {
            return id;
        }

        public static @Nullable Leap byName(String name) {
            for (Leap l : VALUES) if (l.id.equals(name)) return l;
            return null;
        }
    }

    /**
     * Whether a leap first given at a stand comes a move at a time. The leap's own rules ask {@link #canLeap} before
     * a second jump, a dash, a slam or an aimed dive since 11 Nov 2026 ({@code AscentMoves}, {@code AscentServer}); this
     * stays false, so such a leap still comes whole and the later stands only play their titles, until the Gauntlet's
     * three stands stand in a world (the gates session's course): turning it on is the manager's call.
     */
    public static final boolean LEAP_BY_PARTS = false;
    /** Whether the aimed dive exists yet (its stand gives nothing until it does): built 11 Nov 2026 (SPEC §17.33). */
    public static final boolean DIVE_BUILT = true;
    /** The first finished run's XP, as a share of the level (a fifth until the plan of 11 Nov 2026, decision 8). */
    static final double GAUNTLET_XP = 0.5;

    /** Whether this player may make one of the leap's moves (the server's word; the client's copy is synced). */
    public static boolean canLeap(ServerPlayer p, Leap l) {
        return !BodyConfig.wayUp() || memory(p).canLeap(l);
    }

    /** Whether the Leaper's Gauntlet has been run to its end. */
    static boolean gauntletRun(ServerPlayer p) {
        return (p.getData(AscentContent.MEMORY).flags() & AscentMemory.LEAPER_TRIAL_DONE) != 0;
    }

    /** The counts of a timed run in progress, a slot a {@link Leap} (not saved: a run does not outlive a session). */
    private static final Map<UUID, int[]> RUN = new HashMap<>();

    /**
     * A stand taken (the client held Use on it for a second): the server's own checks, then the move is the player's,
     * saved at once, and its stroke and title are told. The first stand of a player without the leap gives the leap.
     */
    static void take(ServerPlayer p, BlockPos pos) {
        if (!on(p) || !(p.level().getBlockState(pos).getBlock() instanceof LeapStandBlock)) return;
        Leap l = p.level().getBlockState(pos).getValue(LeapStandBlock.MOVE);
        // standing on it, on the ground, still
        if (!p.onGround() || p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.25, pos.getZ() + 0.5) > 1.6 * 1.6) return;
        if (l == Leap.DIVE && !DIVE_BUILT) return;
        if (memory(p).stand(l)) return;
        boolean had = Ascent.has(p, Ascent.Tier.LEAPER);
        if (!had) Ascent.grant(p, Ascent.Tier.LEAPER, "the Leaper's Gauntlet: the " + l.getSerializedName() + " stand");
        // a leap that came whole another way stays whole; one first given here comes a move at a time
        WayMemory m = memory(p);
        m = m.withStand(l, had ? m.parts() : LEAP_BY_PARTS);
        set(p, m);
        sync(p, m, -1);
        AwakenNet.send(p, new WayPayloads.Stroke(l.ordinal(), pos.asLong()));
        p.level().playSound(null, pos, net.minecraft.sounds.SoundEvents.ANVIL_LAND, net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 0.62F);
        dev.awaken.core.api.TrialHooks.standTaken(p, dev.awaken.core.api.TrialHooks.LEAPER, l.getSerializedName(), pos);
        Progression.count(p, "awaken:ascent_stand_" + l.getSerializedName());
        dev.awaken.Awaken.LOG.info("[ascent] the way up: {} unlocked at a stand at {} for {}{}", l, pos.toShortString(), p.getPlainTextName(),
                had ? " (the leap was already held)" : "");
    }

    /** A mark of the Gauntlet's course reached (TrialHooks.at): the client shows what belongs there; a run starts counting. */
    static void mark(ServerPlayer p, String mark, BlockPos from, BlockPos to) {
        if (!BodyConfig.wayUp()) return;
        if (mark.equals(dev.awaken.core.api.TrialHooks.MARK_RUN)) {
            RUN.put(p.getUUID(), new int[Leap.VALUES.length]);
            return;
        }
        int kind;
        Leap l = null;
        int n = 0;
        if (mark.equals(dev.awaken.core.api.TrialHooks.MARK_REACH)) {
            kind = WayPayloads.SPOT_REACH;
        } else if (mark.startsWith(dev.awaken.core.api.TrialHooks.MARK_MISS)) {
            kind = WayPayloads.SPOT_MISS;
            String[] parts = mark.split("/");
            if (parts.length > 1) l = Leap.byName(parts[1]);
        } else if (mark.startsWith(dev.awaken.core.api.TrialHooks.MARK_USE)) {
            kind = WayPayloads.SPOT_USE;
            String[] parts = mark.split("/");
            l = parts.length > 1 ? Leap.byName(parts[1]) : null;
            if (l == null) return;
            try {
                n = parts.length > 2 ? Integer.parseInt(parts[2]) : 1;
            } catch (NumberFormatException e) {
                n = 1;
            }
        } else {
            return;
        }
        AwakenNet.send(p, new WayPayloads.Spot(kind, l == null ? -1 : l.ordinal(), n, from.asLong(), to.asLong()));
    }

    /** One of the leap's moves was made and counted (AscentServer): a timed run in progress counts it. */
    static void used(ServerPlayer p, Leap l) {
        int[] c = RUN.get(p.getUUID());
        if (c != null) c[l.ordinal()]++;
    }

    /**
     * The Gauntlet's timed run finished (TrialHooks.pay): the first time, the leap is whole and paid for (half a level,
     * ten mastery, the old far landing's counters); every time, what was paid and the run's counts go back to the course
     * to show, and the log gets one line a play can be read from.
     */
    static dev.awaken.core.api.TrialHooks.Pay pay(ServerPlayer p, int ticks, int medal) {
        AscentMemory mem = p.getData(AscentContent.MEMORY);
        boolean first = (mem.flags() & AscentMemory.LEAPER_TRIAL_DONE) == 0;
        int before = mem.level(AscentMemory.Track.LEAPER), xp = 0;
        if (first) {
            Ascent.grant(p, Ascent.Tier.LEAPER, "leaper's gauntlet");
            p.setData(AscentContent.MEMORY, p.getData(AscentContent.MEMORY).withFlag(AscentMemory.LEAPER_TRIAL_DONE));
            AscentServer.link(p, AscentMemory.Track.LEAPER, 10);
            double share = dev.awaken.progression.Curve.xpToNext(Progression.profile(p).level()) * GAUNTLET_XP;
            Progression.grantXp(p, share, Progression.XpSource.DISCOVERY);
            xp = (int) Math.round(share);
            Progression.count(p, "awaken:ascent_leaper_trial");
            AscentQuests.advance(p, AscentQuests.GAUNTLET);
        }
        WayMemory m = memory(p).whole();
        set(p, m);
        sync(p, m, -1);
        int[] c = RUN.remove(p.getUUID());
        if (c == null) c = new int[Leap.VALUES.length];
        java.util.ArrayList<dev.awaken.core.api.TrialHooks.Pay.Count> counts = new java.util.ArrayList<>(4);
        for (Leap l : Leap.VALUES) {
            if (l == Leap.DIVE && c[l.ordinal()] == 0) continue;
            counts.add(new dev.awaken.core.api.TrialHooks.Pay.Count(l.counted, c[l.ordinal()]));
        }
        AscentMemory after = p.getData(AscentContent.MEMORY);
        int level = after.level(AscentMemory.Track.LEAPER), points = after.points(AscentMemory.Track.LEAPER);
        int lo = 25 * level * level, hi = 25 * (level + 1) * (level + 1);
        float fill = level >= 10 ? 1F : Math.max(0F, Math.min(1F, (points - lo) / (float) (hi - lo)));
        dev.awaken.Awaken.LOG.info("[gauntlet] run by {}: {} double jumps, {} dashes, {} slams, {} dives, {}, medal {}{}", p.getPlainTextName(),
                c[0], c[1], c[2], c[3], String.format("%d:%02d", ticks / 1200, ticks / 20 % 60), medal, first ? ", the first: paid " + xp + " XP" : "");
        return new dev.awaken.core.api.TrialHooks.Pay(first, xp, before, level, fill, List.copyOf(counts));
    }

    /** Saved with the player and kept through death (registered with the Ascent's own attachments). */
    public static final Supplier<AttachmentType<WayMemory>> MEMORY = AscentContent.ATTACHMENTS.register("way_up",
            () -> AttachmentType.builder(() -> WayMemory.EMPTY).serialize(WayMemory.MAP_CODEC, m -> !m.isEmpty()).copyOnDeath().build());

    /** How often a player's road is read (ticks), and how long a woken place rides the Now line. */
    static final int EVERY = 100, RAISED_FOR = 20 * 60 * 10;
    /** How far a place is looked for: a vault, a spire, a sky platform (then the platform's search widens once). */
    static final int VAULT_REACH = 2000, SPIRE_REACH = 1500, SKY_REACH = 3000, SKY_FAR = 8000;
    /**
     * A place is told this long after its rung began to wait, so the rank fight's own messages (the rank, the rival's
     * lines) have had their say first. Then it is told at once: the director files a paced line that has waited 45 s, and
     * this one must be seen.
     */
    static final int TELL_AFTER = 20 * 20;
    /** After a core's gift a fall is a soft landing for this long (the first flight may take a few tries). */
    static final int SOFT_FALL = 20 * 60;
    /** The Tasks thread and the Now ids the row stands for. */
    static final String THREAD = "rank.way_up", NOW = "ascent:way/";

    /** What the server last told each client, when each rung's place woke (not saved), and soft falls. */
    private record Sent(int told, int done, int owed, List<Long> places, long gateAt, int moves, int leap, int stands) {}

    private static final Map<UUID, Sent> SENT = new HashMap<>();
    private static final Map<UUID, long[]> WOKE_AT = new HashMap<>();
    private static final Map<UUID, long[]> WAITING_SINCE = new HashMap<>();
    private static final Map<UUID, Long> SOFT_UNTIL = new HashMap<>();
    /** Mastery points seen last (rope, leap, form): a rise is the move done once. */
    private static final Map<UUID, int[]> POINTS = new HashMap<>();

    static void init(IEventBus modBus) {
        modBus.addListener(WayUp::registerPayloads);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(WayUp::onPlayerTick);
        // before the Ascent's own (HIGH) and the body's roll: the first flight's falls never hurt
        bus.addListener(EventPriority.HIGHEST, WayUp::onFall);
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> forget(e.getEntity().getUUID()));
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> SENT.remove(e.getEntity().getUUID()));
        bus.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> SENT.remove(e.getEntity().getUUID()));
        Ascent.onGranted(WayUp::onGranted);
        Goals.register(WayUp::steps);
        NowHooks.register(WayUp::now);
        WayTests.register();
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToClient(WayPayloads.Way.TYPE, WayPayloads.Way.STREAM_CODEC);
        r.playToClient(WayPayloads.Lesson.TYPE, WayPayloads.Lesson.STREAM_CODEC);
        r.playToClient(WayPayloads.Spot.TYPE, WayPayloads.Spot.STREAM_CODEC);
        r.playToClient(WayPayloads.Stroke.TYPE, WayPayloads.Stroke.STREAM_CODEC);
        r.playToServer(WayPayloads.Take.TYPE, WayPayloads.Take.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer p) take(p, BlockPos.of(msg.pos()));
        });
    }

    static void forget(UUID id) {
        SENT.remove(id);
        WOKE_AT.remove(id);
        WAITING_SINCE.remove(id);
        SOFT_UNTIL.remove(id);
        POINTS.remove(id);
        RUN.remove(id);
    }

    public static WayMemory memory(ServerPlayer p) {
        return p.getData(MEMORY);
    }

    private static void set(ServerPlayer p, WayMemory m) {
        p.setData(MEMORY, m);
    }

    /** Whether the road applies to this player now: the option on, awake, not watching. */
    static boolean on(ServerPlayer p) {
        return BodyConfig.wayUp() && !p.isSpectator() && Progression.profile(p).awakened();
    }

    // ---- reading the road -------------------------------------------------------------------------------------------

    /** The rung whose place this player should be going to now, or null: the rope first, then the leap, then wings. */
    static @Nullable Rung waiting(ServerPlayer p) {
        if (!on(p)) return null;
        int rank = Progression.rank(p).ordinal();
        if (!Ascent.has(p, Ascent.Tier.TETHER)) return rank >= Rank.D.ordinal() ? Rung.ROPE : null;
        // wings before the leap once C-rank is held: the core hands the leap down, and the bigger thing leads
        if (!Ascent.has(p, Ascent.Tier.WINGS) && rank >= Rank.C.ordinal()) return Rung.WINGS;
        // the leap waits until its Gauntlet is run, not only until it is held (SPEC §71): a leap handed down whole by a sky
        // core still has its stands' halls and its pay to come
        if (!Ascent.has(p, Ascent.Tier.LEAPER) || !gauntletRun(p)) return Rung.LEAP;
        return null;
    }

    private static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        long now = AscentServer.now(p);
        if ((now + (p.getUUID().hashCode() & 63)) % 5 == 0) watch(p);
        if ((now + (p.getUUID().hashCode() & 127)) % EVERY != 0) return;
        read(p, now, false);
    }

    /** The road read now, a woken place told at once (the dev command, tests). */
    static void read(ServerPlayer p, long now) {
        read(p, now, true);
    }

    /**
     * Every five seconds: the form at Sovereign rank, the place for the rung that waits, the gate still standing, the
     * sync. {@code atOnce}: a place found is told now, not after {@link #TELL_AFTER}.
     */
    static void read(ServerPlayer p, long now, boolean atOnce) {
        if (!on(p)) return;
        // the last rank fight pays the last rung (SPEC §63.2): the rank is the fight's win
        if (Progression.rank(p) == Rank.SOVEREIGN && !Ascent.has(p, Ascent.Tier.SOVEREIGN)) Sovereign.give(p);
        WayMemory m = memory(p);
        // a rung held needs no place any more
        boolean run = gauntletRun(p);
        for (Rung r : Rung.VALUES) {
            if (Ascent.has(p, r.tier) && (r != Rung.LEAP || run) && m.place(r) != null) m = m.withPlace(r, null);
        }
        // the Gauntlet's gate stays on the road until it is run (the leap itself is taken inside it now, SPEC §71)
        if (run && m.leapGate() >= 0) m = m.withGate(-1, null);
        if (run && m.parts()) m = m.whole();
        Rung r = waiting(p);
        int woke = -1;
        if (r != null && p.level().dimension() == Level.OVERWORLD && p.level() instanceof ServerLevel level) {
            if (r == Rung.LEAP) m = gate(p, level, m);
            if (m.place(r) == null && !(r == Rung.LEAP && m.leapGate() >= 0)) {
                BlockPos at = find(p, level, r);
                if (at != null) m = m.withPlace(r, at);
            }
            long[] since = WAITING_SINCE.computeIfAbsent(p.getUUID(), k -> new long[Rung.VALUES.length]);
            if (since[r.ordinal()] == 0) since[r.ordinal()] = now;
            if (m.place(r) != null && !m.told(r) && (atOnce || now - since[r.ordinal()] >= TELL_AFTER) && p.onGround()) {
                m = m.withTold(r, true);
                woke = r.ordinal();
                WOKE_AT.computeIfAbsent(p.getUUID(), k -> new long[Rung.VALUES.length])[r.ordinal()] = now;
                tell(p, r, m.place(r));
            }
        }
        if (!m.equals(memory(p))) set(p, m);
        sync(p, m, woke);
    }

    /** The lessons done by doing: a rope thrown twice, a move of the leap, wings opened, flight taken, the form taken. */
    private static void watch(ServerPlayer p) {
        if (!BodyConfig.wayUp()) return;
        AscentMemory a = p.getData(AscentContent.MEMORY);
        if (a.tiers() == 0) return;
        int[] seen = POINTS.computeIfAbsent(p.getUUID(), k -> new int[]{a.points(AscentMemory.Track.TETHER),
                a.points(AscentMemory.Track.LEAPER), a.points(AscentMemory.Track.SOVEREIGN)});
        int rope = a.points(AscentMemory.Track.TETHER), leap = a.points(AscentMemory.Track.LEAPER), form = a.points(AscentMemory.Track.SOVEREIGN);
        WayMemory m = memory(p), was = m;
        // the Vow's own pull is the first rope; the second is the player's
        if (rope >= seen[0] + 4 || (a.flags() & AscentMemory.VOW_KEPT) != 0 && a.has(AscentPayloads.Tier.TETHER)) m = done(m, Rung.ROPE);
        if (rope >= seen[0] + 4) seen[0] = rope;
        if (leap > seen[1]) {
            m = done(m, Rung.LEAP);
            seen[1] = leap;
        }
        if (form > seen[2]) {
            m = done(m, Rung.FORM);
            seen[2] = form;
        }
        AscentState s = Ascent.state(p);
        if (s == AscentState.GLIDE) m = done(m, Rung.WINGS);
        if (s == AscentState.SOAR) m = done(m, Rung.SKY);
        if (m != was) {
            set(p, m);
            sync(p, m, -1);
        }
    }

    /** A rung given where it could not be taught (in a fight): its lesson is owed. */
    static void owe(ServerPlayer p, Rung r) {
        WayMemory m = memory(p).withOwed(r);
        set(p, m);
        sync(p, m, -1);
    }

    private static WayMemory done(WayMemory m, Rung r) {
        return m.done(r) ? m : m.withDone(r);
    }

    private static void sync(ServerPlayer p, WayMemory m, int woke) {
        int moves = moves(p);
        Sent now = new Sent(m.told(), m.done(), m.owed(), m.places(), m.leapAt(), moves, m.leapOpen(), m.stands());
        if (woke < 0 && now.equals(SENT.get(p.getUUID()))) return;
        SENT.put(p.getUUID(), now);
        long[] places = new long[Rung.VALUES.length];
        for (Rung r : Rung.VALUES) {
            BlockPos at = m.place(r);
            places[r.ordinal()] = at == null ? WayMemory.NONE : at.asLong();
        }
        AwakenNet.send(p, new WayPayloads.Way(m.told(), m.done(), m.owed(), places, m.leapAt(), woke, moves, m.leapOpen(), m.stands()));
    }

    // ---- places -----------------------------------------------------------------------------------------------------

    /**
     * The place for a rung: one of its kind the player has found if there is one in reach, else the nearest. Found by
     * the structure sets' own placement (RuinLocator: no chunk is loaded or made), a few terrain samples a call, so a far
     * search finishes over later reads. Null: none yet (the leap then opens by rope alone; the row has no needle).
     */
    static @Nullable BlockPos find(ServerPlayer p, ServerLevel level, Rung r) {
        BlockPos me = p.blockPosition();
        return switch (r) {
            case ROPE -> {
                BlockPos at = nearest(p, level, me, VAULT_REACH, List.of(RuinKind.VAULT, RuinKind.SPAWN_VAULT));
                if (at != null) yield at;
                // every world has the spawn vault
                var chunk = RuinLocator.spawnVault(level);
                yield chunk == null ? null : RuinLocator.center(chunk, me.getY());
            }
            case LEAP -> nearest(p, level, me, SPIRE_REACH, List.of(RuinKind.SPIRE));
            case WINGS -> {
                BlockPos at = nearest(p, level, me, SKY_REACH, List.of(RuinKind.SKY));
                yield at != null ? at : nearest(p, level, me, SKY_FAR, List.of(RuinKind.SKY));
            }
            default -> null;
        };
    }

    private static @Nullable BlockPos nearest(ServerPlayer p, ServerLevel level, BlockPos me, int reach, List<RuinKind> kinds) {
        List<RuinLocator.Found> found = RuinLocator.near(level, me, reach, kinds, dev.awaken.above.Above.data(p), false, 10, 14);
        RuinLocator.Found best = null;
        for (RuinLocator.Found f : found) {
            // the city keeps its basin: the placement maths names chunks there, but no ruin ever forms in them
            // (PrecursorRuinStructure.findGenerationPoint); the built tour was sent to a vault that does not exist
            if (dev.awaken.terrain.api.CityBasin.reserved(f.pos().getX(), f.pos().getZ())) continue;
            // one already met comes first when it is no more than twice as far as the nearest
            if (best == null) best = f;
            else if (f.discovered() && !best.discovered() && f.distance() <= best.distance() * 2 + 64) best = f;
        }
        return best == null ? null : best.pos();
    }

    /** The leap's gate: still standing where it was opened, or opened again there (never lost). */
    private static WayMemory gate(ServerPlayer p, ServerLevel level, WayMemory m) {
        BlockPos at = m.gateAt();
        if (m.leapGate() < 0 || at == null) return m;
        for (var g : dev.awaken.gates.Gates.near(level, at, 64)) if (g.id() == m.leapGate() && g.state().standing()) return m;
        // only judge it gone from near enough that its record would show
        if (p.blockPosition().distSqr(at) > 96 * 96) return m;
        int id = dev.awaken.gates.Gates.openScripted(p, dev.awaken.gates.Gates.leaperTrialPlan(p.getUUID().getLeastSignificantBits() ^ level.getGameTime()), at);
        DirectorLog.log(p, "ascent", "the Leaper's Gauntlet was gone: opened again at " + at.toShortString() + " (gate " + id + ")");
        return id < 0 ? m.withGate(-1, null) : m.withGate(id, at);
    }

    /** A gate of the Gauntlet opened for this player (either way in): it goes on their road. */
    static void gateOpened(ServerPlayer p, int id, BlockPos at) {
        if (id < 0) return;
        WayMemory m = memory(p).withGate(id, at);
        set(p, m);
        sync(p, m, -1);
    }

    /**
     * A crown reached by the line (a Tether Spire's, a hero tree's; SPEC §63.2): the first time without the leap, the
     * Leaper's Gauntlet opens at its foot. {@code foot}: the ground the climb began from, under the crown.
     */
    static void crownReached(ServerPlayer p, BlockPos crown, int footY) {
        if (!on(p) || gauntletRun(p) || !Ascent.has(p, Ascent.Tier.TETHER)) return;
        AscentMemory a = p.getData(AscentContent.MEMORY);
        if ((a.flags() & AscentMemory.LEAPER_TRIAL) != 0 || memory(p).leapGate() >= 0) return;
        // the crown answers only the line (SPEC §17.29): asked here, last, so the rim's watch (every other tick while
        // someone stands on it) costs a hunter with the leap, or with the gate already open, nothing
        if (!dev.awaken.core.api.ArrivalHooks.byLine(p)) return;
        // out on the plaza (a spire's is 11 wide), toward where the player stands on the crown
        double dx = p.getX() - (crown.getX() + 0.5), dz = p.getZ() - (crown.getZ() + 0.5), len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.3) {
            dx = 1;
            dz = 0;
            len = 1;
        }
        BlockPos near = new BlockPos(crown.getX() + (int) Math.round(dx / len * 9), footY, crown.getZ() + (int) Math.round(dz / len * 9));
        int id = AscentTrials.openLeaper(p, near);
        if (id >= 0) DirectorLog.log(p, "ascent", "a crown by rope: the Leaper's Gauntlet opens at its foot (gate " + id + ")");
        dev.awaken.Awaken.LOG.info("[ascent] the way up: a crown by rope for {}: the Gauntlet's gate {} near {}", p.getPlainTextName(), id, near.toShortString());
    }

    // ---- telling ----------------------------------------------------------------------------------------------------

    private static final String[] WAYS = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};

    /** "310 blocks east of here", from a player to a place. */
    static String whereFrom(ServerPlayer p, BlockPos at) {
        double dx = at.getX() + 0.5 - p.getX(), dz = at.getZ() + 0.5 - p.getZ();
        int blocks = (int) Math.round(Math.sqrt(dx * dx + dz * dz) / 10.0) * 10;
        if (blocks < 30) return "right here";
        // yaw 0 is south (+z), 90 west (-x)
        double deg = Math.toDegrees(Math.atan2(-dx, dz));
        int way = Math.floorMod((int) Math.round(deg / 45.0), 8);
        return blocks + " blocks " + WAYS[way] + " of here";
    }

    private static void tell(ServerPlayer p, Rung r, BlockPos at) {
        String where = whereFrom(p, at);
        // a panel, never a toast: a toast holds two short lines and cuts the rest, and nothing is ever cut off (§57.6);
        // the leap's first row is told by the Tasks page alone: there is nothing to see light up yet
        switch (r) {
            case ROPE -> say(p, "A vault has lit up for you", "It wants the chain you're carrying. It's " + where + ".");
            case WINGS -> say(p, "A sky platform has lit up for you", "It's " + where + ". You climb its rings by rope.");
            default -> { }
        }
        Progression.markNewThing(p, "way " + r.name().toLowerCase(java.util.Locale.ROOT));
        DirectorLog.log(p, "ascent", "the way up: " + r + " wakes at " + at.toShortString() + " (" + where + ")");
        dev.awaken.Awaken.LOG.info("[ascent] the way up: {} wakes for {} at {} ({})", r, p.getPlainTextName(), at.toShortString(), where);
    }

    /** One of the road's own lines: said at once, whole, for twelve seconds (the director files a paced line that waits). */
    static void say(ServerPlayer p, String title, String body) {
        Signal.send(p, dev.awaken.signal.SignalWindow.builder(dev.awaken.signal.SignalWindow.Kind.NOTICE, Component.literal(title))
                .body(Component.literal(body)).presentation(dev.awaken.signal.SignalWindow.Presentation.PANEL)
                .tier(dev.awaken.core.api.Notices.Tier.CRITICAL).topic("ascent_way").ttl(20 * 12).build());
    }

    // ---- the Tasks row and the Now line ------------------------------------------------------------------------------

    /** One row, Lead voice: the kind of place and where, never what the move is, never a fight (SPEC §63.4). */
    static List<Goals.Step> steps(ServerPlayer p) {
        Rung r = waiting(p);
        WayMemory m = memory(p);
        if (r == null) {
            // wings given, never opened: the one thing left of this rung
            if (on(p) && Ascent.has(p, Ascent.Tier.WINGS) && !m.done(Rung.WINGS)) {
                return List.of(Goals.Step.of("way/first_flight", Goals.Goal.RANK, "Jump from somewhere high and open your wings")
                        .why("They open in the air, not on the ground.").thread(THREAD, 40).lead().teaches("tier:wings").build());
            }
            return List.of();
        }
        BlockPos at = m.place(r);
        Goals.Builder b = switch (r) {
            case ROPE -> Goals.Step.of("way/rope", Goals.Goal.RANK, "Take the Ringer's chain to the vault that lit up")
                    .why("Stand between its two pylons. It will make something of the chain.").thread(THREAD, 10).teaches("tier:tether");
            case LEAP -> {
                BlockPos gate = m.gateAt();
                if (m.leapGate() >= 0 && gate != null) {
                    at = gate;
                    yield Goals.Step.of("way/leap_gate", Goals.Goal.RANK, "Run the Leaper's Gauntlet")
                            .why(Ascent.has(p, Ascent.Tier.LEAPER) ? "It is a course for the leap, and it pays the first time you finish it. No fighting."
                                    : "It holds a new way to move. No fighting.").thread(THREAD, 22).teaches("tier:leaper");
                }
                yield at != null
                        ? Goals.Step.of("way/leap", Goals.Goal.RANK, "Climb a Tether Spire with your rope")
                                .why("There is something at the top for a hunter who gets there by rope.").thread(THREAD, 20).teaches("tier:leaper")
                        : Goals.Step.of("way/leap", Goals.Goal.RANK, "Get to know your rope")
                                .why("Swing and climb with it. Something opens for a hunter who uses it a lot.").thread(THREAD, 20).teaches("tier:leaper");
            }
            case WINGS -> Goals.Step.of("way/wings", Goals.Goal.RANK, "Climb the sky platform that lit up")
                    .why("Its rings climb to it by rope. Whatever is in its cabin answers only to a climb.").thread(THREAD, 30).teaches("tier:wings");
            default -> null;
        };
        if (b == null) return List.of();
        if (at != null && p.level().dimension() == Level.OVERWORLD) b.at(at);
        return List.of(b.lead().covers(NOW).build());
    }

    /** For ten minutes after a place wakes it rides the Now line, with its needle. */
    static List<NowHooks.Suggestion> now(ServerPlayer p) {
        Rung r = waiting(p);
        long[] woke = WOKE_AT.get(p.getUUID());
        if (r == null || woke == null || p.level().dimension() != Level.OVERWORLD) return List.of();
        BlockPos at = memory(p).place(r);
        long age = AscentServer.now(p) - woke[r.ordinal()];
        if (at == null || woke[r.ordinal()] == 0 || age < 0 || age > RAISED_FOR) return List.of();
        String text = switch (r) {
            case ROPE -> "A vault has lit up for the chain you're carrying";
            case WINGS -> "A sky platform has lit up for you";
            default -> null;
        };
        return text == null ? List.of() : List.of(NowHooks.Suggestion.at(NOW + r.name().toLowerCase(java.util.Locale.ROOT), 60, text, at));
    }

    // ---- rungs given ------------------------------------------------------------------------------------------------

    /**
     * A rung given. The one asked for is taught where it is given; any rung under it that came along (a core's leap, the
     * Sky Warden's wings) is owed its lesson (SPEC §63.1). An old world's first load gives its rungs back in one scene and
     * owes nothing.
     */
    private static void onGranted(ServerPlayer p, Ascent.Tier t) {
        if (!BodyConfig.wayUp()) return;
        Rung r = Rung.of(t);
        Ascent.Tier top = Ascent.highest(p);
        WayMemory m = memory(p).withPlace(r, null);
        boolean handedDown = top != null && t.ordinal() < top.ordinal() && p.tickCount > 100;
        dev.awaken.Awaken.LOG.info("[ascent] the way up: {} given to {}{}", r, p.getPlainTextName(), handedDown ? " (handed down: its lesson is owed)" : "");
        if (handedDown) {
            m = m.withOwed(r);
            // its own title, after the one that was asked for has had the screen (SPEC §71.2: every unlock is named)
            net.minecraft.resources.Identifier title = AscentUnlocks.of(r);
            if (title != null) {
                var server = p.level().getServer();
                dev.awaken.core.Later.run(server, 180, () -> {
                    // still this player, still here (a relog makes a new body; a test's player is gone by then)
                    if (server.getPlayerList().getPlayer(p.getUUID()) == p && !p.hasDisconnected()) dev.awaken.core.api.Unlocks.show(p, title);
                });
            }
        }
        if (r == Rung.LEAP && gauntletRun(p)) m = m.withGate(-1, null);
        set(p, m);
        if (r == Rung.WINGS && !handedDown) SOFT_UNTIL.put(p.getUUID(), AscentServer.now(p) + SOFT_FALL);
        sync(p, m, -1);
    }

    /**
     * A sky core has just given wings (SPEC §63.2): the first flight begins. The client is told the pier's end and the way
     * off it (the rig's pier runs out along +x and +z from the deck's centre, where the core stands).
     */
    static void firstFlight(ServerPlayer p, BlockPos core) {
        if (!BodyConfig.wayUp()) return;
        int pier = dev.awaken.above.ruin.SkyRig.PIER;
        double inv = 1 / Math.sqrt(2);
        AwakenNet.send(p, new WayPayloads.Lesson(WayPayloads.FIRST_FLIGHT, core.getX() + pier + 0.5, core.getY() + 1.0, core.getZ() + pier + 0.5, inv, inv));
        SOFT_UNTIL.put(p.getUUID(), AscentServer.now(p) + SOFT_FALL);
    }

    private static void onFall(LivingFallEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Long until = SOFT_UNTIL.get(p.getUUID());
        if (until == null) return;
        if (AscentServer.now(p) > until) {
            SOFT_UNTIL.remove(p.getUUID());
            return;
        }
        e.setDistance(0);
        e.setDamageMultiplier(0);
    }

    /** Whether a fall is soft for this player right now (tests). */
    static boolean softFall(ServerPlayer p) {
        Long until = SOFT_UNTIL.get(p.getUUID());
        return until != null && AscentServer.now(p) <= until;
    }
}
