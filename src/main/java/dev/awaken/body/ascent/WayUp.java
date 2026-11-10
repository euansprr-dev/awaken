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
    private record Sent(int told, int done, int owed, List<Long> places, long gateAt) {}

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
    }

    static void forget(UUID id) {
        SENT.remove(id);
        WOKE_AT.remove(id);
        WAITING_SINCE.remove(id);
        SOFT_UNTIL.remove(id);
        POINTS.remove(id);
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
        if (!Ascent.has(p, Ascent.Tier.LEAPER)) return Rung.LEAP;
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
        for (Rung r : Rung.VALUES) {
            if (Ascent.has(p, r.tier) && m.place(r) != null) m = m.withPlace(r, null);
        }
        if (Ascent.has(p, Ascent.Tier.LEAPER) && m.leapGate() >= 0) m = m.withGate(-1, null);
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
        Sent now = new Sent(m.told(), m.done(), m.owed(), m.places(), m.leapAt());
        if (woke < 0 && now.equals(SENT.get(p.getUUID()))) return;
        SENT.put(p.getUUID(), now);
        long[] places = new long[Rung.VALUES.length];
        for (Rung r : Rung.VALUES) {
            BlockPos at = m.place(r);
            places[r.ordinal()] = at == null ? WayMemory.NONE : at.asLong();
        }
        AwakenNet.send(p, new WayPayloads.Way(m.told(), m.done(), m.owed(), places, m.leapAt(), woke));
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
        if (!on(p) || Ascent.has(p, Ascent.Tier.LEAPER) || !Ascent.has(p, Ascent.Tier.TETHER)) return;
        AscentMemory a = p.getData(AscentContent.MEMORY);
        if ((a.flags() & AscentMemory.LEAPER_TRIAL) != 0 || memory(p).leapGate() >= 0) return;
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
        String line = switch (r) {
            case ROPE -> "A vault has lit up for the chain you're carrying. It's " + where + ".";
            case WINGS -> "A sky platform has lit up for you. It's " + where + ". You climb its rings by rope.";
            default -> null;
        };
        // the leap's first row is told by the Tasks page alone: there is nothing to see light up yet
        if (line != null) Signal.toast(p, Component.literal(line), dev.awaken.core.api.Notices.Tier.CRITICAL, "ascent_way");
        Progression.markNewThing(p, "way " + r.name().toLowerCase(java.util.Locale.ROOT));
        DirectorLog.log(p, "ascent", "the way up: " + r + " wakes at " + at.toShortString() + " (" + where + ")");
        dev.awaken.Awaken.LOG.info("[ascent] the way up: {} wakes for {} at {} ({})", r, p.getPlainTextName(), at.toShortString(), where);
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
                    yield Goals.Step.of("way/leap_gate", Goals.Goal.RANK, "Run the gate that opened where you climbed")
                            .why("It is a course, not a fight, and it stays open until you have run it.").thread(THREAD, 22).teaches("tier:leaper");
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
        if (handedDown) m = m.withOwed(r);
        if (r == Rung.LEAP) m = m.withGate(-1, null);
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
