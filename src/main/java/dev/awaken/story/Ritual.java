package dev.awaken.story;

import com.mojang.serialization.Codec;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.api.GateHooks;
import dev.awaken.core.api.NowHooks;
import dev.awaken.gates.Gates;
import dev.awaken.gates.RitualHall;
import dev.awaken.mobs.entity.RitualStatue;
import dev.awaken.mobs.entity.SeatedOne;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.story.net.StoryPayloads;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The Ritual of the Bell (docs/proposals/the-ritual.md, SPEC §13): the director of the prologue's scripted Bell Crypt.
 * One timeline per hall, server-side and saved ({@link RitualData}): everyone in the story gathers inside the doors,
 * the doors close, the rules, the Seated One wakes, the Judgement, and its hand closes on everyone at once.
 *
 * <p>Nobody can die in the hall before that: every hit on an unawakened player in a ritual gate is clamped to leave
 * half a heart. Beats are processed by elapsed time with every moment in between fired in order, so a lag spike, a
 * pause (the whole party away) or a test's fast-forward never skips a strike or a light.
 */
final class Ritual {
    private Ritual() {}

    /** The beats, with their lengths in ticks (WAITING and DONE have none). */
    enum Beat {
        WAITING(0), DOORS(190), BOW(190), RING(360), JUDGED(80), WAKING(170), JUDGEMENT(800), END(Ritual.DEATH + 1), DONE(0);

        final int length;

        Beat(int length) {
            this.length = length;
        }

        static final Codec<Beat> CODEC = dev.awaken.core.Codecs.lenientEnum(Beat.class, WAITING);

        Beat next() {
            return this == DONE ? DONE : values()[ordinal() + 1];
        }

        boolean sealed() {
            return ordinal() >= DOORS.ordinal();
        }
    }

    /** What a {@link StoryPayloads.RitualCue} asks the client to draw and sound. */
    enum Cue {
        TOLL, WORD, DOORS, LIGHTS_OUT, CANDLE, LIGHTS_ON, PLATE, PLATE_MISS, GLARE, RULE_STRIKE, HIT, SHAKE, EYES, RISE,
        GAZE_CHARGE, GAZE_FIRE, SWING, IMPACT, PIT, DRAIN, ERROR, DEATH, HOLD
    }

    /** The wait for everyone: after the first player is in the hall, the doors close after this long regardless. */
    static final int WAIT_TIMEOUT = 20 * 90;
    /** A moment for the last one in to take a step before the doors grind shut. */
    static final int WAIT_GRACE = 60;
    /** Rule BOW: kneel within this window after the word appears. */
    static final int BOW_WORD = 12, BOW_DEADLINE = BOW_WORD + 120, BOW_STRIKE = BOW_DEADLINE + 14;
    /** Rule RING: each plate lights at RING_FIRST + k * RING_STEP and must be stood on within RING_WINDOW. */
    static final int RING_FIRST = 40, RING_STEP = 100, RING_WINDOW = 80;
    /** Damage (before the clamp): the rule strike, the gaze, a statue's swing, the pits. */
    static final float RULE_DAMAGE = 7F, GAZE_DAMAGE = 7F, SWING_DAMAGE = 8F, PIT_DAMAGE = 3F;
    /** The gaze: the line it burns along, how long it fills before it fires, its half-width. */
    static final int GAZE_FILL = 45;
    static final double GAZE_LENGTH = 34, GAZE_HALF = 1.3;
    static final int[] GAZE_AT = {30, 200, 370, 540, 690};
    /** Statue swings: how far it reaches (a sector of this radius, +-55 degrees), its wind-up, its stride. */
    static final double SWING_REACH = 3.8, STATUE_SPEED = 0.075, SWING_START = 3.0;
    static final int SWING_WINDUP = 25, SWING_RECOVER = 22, SWING_EVERY = 45, ADVANCE_MAX = 80;
    /** The rune ring's pits: warning, open, closed again. */
    static final int PIT_FIRST = 220, PIT_EVERY = 120, PIT_WARN = 30, PIT_OPEN = 70;
    /** The End: the light drains from DRAIN_FROM over DRAIN_TICKS; the hand closes at CLOSE; everyone dies at DEATH. */
    static final int DRAIN_FROM = 40, DRAIN_TICKS = 150, ERROR_AT = 195, CLOSE = 222, DEATH = 230;
    /** The Seated One's eyes, above its feet (for the gaze and the client's light). */
    static final double EYE_HEIGHT = 19.5, EYE_FORWARD = 2.0;

    /** Tests move the director's clock forward without waiting. */
    static long clockOffset;
    /** Tests: the test level's chunks never reach entity-ticking within one tick, so the cast is brought regardless. */
    static boolean castAnywhere;

    static long now(MinecraftServer server) {
        return server.overworld().getGameTime() + clockOffset;
    }

    // ---- live state (not saved: rebuilt from the saved state and the world) ------------------------------------------

    private static final class Swing {
        int statue;
        UUID target;
        int phase; // 0 advancing, 1 winding up, 2 recovering
        long since;
        Vec3 facing = Vec3.ZERO;
    }

    private static final class Live {
        final RitualHall hall;
        long lastAge = -1;
        Beat lastBeat;
        @Nullable SeatedOne seated;
        final List<RitualStatue> statues = new ArrayList<>();
        final Set<UUID> bowed = new HashSet<>();
        final Set<UUID> onPlate = new HashSet<>();
        final List<Swing> swings = new ArrayList<>();
        final long[] pitOpen = new long[8];
        final long[] pitWarn = new long[8];
        int pitTurn;
        int gazeTurn;
        Vec3 gazeFrom = Vec3.ZERO, gazeTo = Vec3.ZERO;
        int doorPlaced;
        int misses;
        @Nullable UUID lookAt;
        boolean paused;

        Live(RitualHall hall) {
            this.hall = hall;
            java.util.Arrays.fill(pitOpen, -1);
            java.util.Arrays.fill(pitWarn, -1);
        }
    }

    private static final Map<Integer, Live> LIVE = new HashMap<>();

    static void forget() {
        LIVE.clear();
        clockOffset = 0;
    }

    // ---- who --------------------------------------------------------------------------------------------------------

    /** The ritual gate this player's story points at, or -1. */
    static int storyGate(ServerPlayer p) {
        String g = Prologue.progress(p).prologueGate;
        if (g.isEmpty()) return -1;
        try {
            int id = Integer.parseInt(g);
            return Gates.isRitual(p.level().getServer(), id) ? id : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Still to be judged: unawakened, story on, walking toward this gate (not yet sealed in). */
    static boolean eligible(ServerPlayer p, int gate) {
        StoryProgress s = Prologue.progress(p);
        return Prologue.storyOn() && !Progression.profile(p).awakened() && s.prologue == StoryProgress.Prologue.GATE_OPEN
                && storyGate(p) == gate;
    }

    /** The ritual gate whose cell this player stands in (antechamber or hall, whatever the beat), or -1. */
    static int ritualGateHere(ServerPlayer p) {
        if (!GateHooks.inGate(p) || !(p.level() instanceof ServerLevel level)) return -1;
        var info = Gates.gateAt(level, p.blockPosition());
        if (info.isEmpty()) return -1;
        return Gates.isRitual(level.getServer(), info.get().id()) ? info.get().id() : -1;
    }

    static boolean inRitualGate(ServerPlayer p) {
        return ritualGateHere(p) >= 0;
    }

    /** The hall is a held breath: until the hand closes, nothing in it can kill someone who isn't awake yet. */
    static boolean protects(ServerPlayer p) {
        if (!Prologue.storyOn() || Progression.profile(p).awakened()) return false;
        StoryProgress s = Prologue.progress(p);
        return s.prologue == StoryProgress.Prologue.RITUAL || inRitualGate(p);
    }

    /** LivingDamageEvent.Pre: every hit leaves at least half a heart (it still hurts, knocks back and flashes). */
    static void onDamagePre(LivingDamageEvent.Pre e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !protects(p)) return;
        if (e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return; // /kill still works
        float keep = p.getHealth() + p.getAbsorptionAmount() - 1.0F;
        if (e.getNewDamage() > keep) e.setNewDamage(Math.max(0F, keep));
    }

    /** Why this player may not walk into this scripted gate, or empty (StoryHooks). */
    static Optional<String> refuses(ServerPlayer p, String gateId) {
        int id;
        try {
            id = Integer.parseInt(gateId);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        MinecraftServer server = p.level().getServer();
        if (!Gates.isRitual(server, id) || p.isSpectator()) return Optional.empty();
        if (Progression.profile(p).awakened()) return Optional.of("The hall beyond is quiet. Whatever waited there is done with you.");
        RitualData.State st = RitualData.get(server).rituals.get(id);
        if (st != null && st.beat.sealed() && !st.party.contains(p.getUUID())) return Optional.of("The doors are sealed. The bell rings without you.");
        if (storyGate(p) != id) return Optional.of("This bell isn't ringing for you.");
        return Optional.empty();
    }

    // ---- the hall and its cast --------------------------------------------------------------------------------------

    static @Nullable RitualHall hall(MinecraftServer server, int gate) {
        Live l = LIVE.get(gate);
        if (l != null) return l.hall;
        return Gates.ritualHall(server, gate).orElse(null);
    }

    private static @Nullable Live live(MinecraftServer server, RitualData.State st) {
        Live l = LIVE.get(st.gate);
        if (l != null) return l;
        Optional<RitualHall> hall = Gates.ritualHall(server, st.gate);
        if (hall.isEmpty()) return null;
        l = new Live(hall.get());
        LIVE.put(st.gate, l);
        return l;
    }

    /**
     * Finds the Seated One and the statues again (after a restart) or brings them. Only once the hall's entities are
     * loaded (a chunk loads before its entities do): never a second god because the first wasn't back yet.
     */
    private static void cast(RitualData data, RitualData.State st, Live l) {
        ServerLevel level = l.hall.level();
        boolean lost = l.seated == null || l.seated.isRemoved() || l.statues.size() != l.hall.statues().size();
        for (RitualStatue s : l.statues) lost |= s.isRemoved();
        if (!lost) return;
        if (!castAnywhere && (!level.isPositionEntityTicking(BlockPos.containing(l.hall.throne())) || !level.isPositionEntityTicking(BlockPos.containing(l.hall.doorway())))) return;
        net.minecraft.world.phys.AABB box = l.hall.hall().inflate(2, 4, 2);
        if (l.seated == null || l.seated.isRemoved()) {
            l.seated = null;
            for (SeatedOne s : level.getEntitiesOfClass(SeatedOne.class, box)) {
                if (l.seated == null && (st.seated.isEmpty() || st.seated.get().equals(s.getUUID()) || level.getEntity(st.seated.get()) == null)) l.seated = s;
                else if (s != l.seated) s.discard();
            }
            if (l.seated == null) l.seated = dev.awaken.mobs.Ritual.spawnSeated(level, l.hall.throne(), l.hall.throneYaw());
            st.seated = l.seated == null ? Optional.empty() : Optional.of(l.seated.getUUID());
            data.setDirty();
        }
        l.statues.clear();
        List<RitualStatue> there = new ArrayList<>(level.getEntitiesOfClass(RitualStatue.class, box));
        for (int i = 0; i < l.hall.statues().size(); i++) {
            RitualStatue s = null;
            if (i < st.statues.size()) {
                for (RitualStatue rs : there) if (rs.getUUID().equals(st.statues.get(i))) s = rs;
            }
            if (s == null) {
                RitualHall.Statue at = l.hall.statues().get(i);
                s = dev.awaken.mobs.Ritual.spawnStatue(level, at.at(), at.yaw());
                if (s == null) continue;
                if (i < st.statues.size()) st.statues.set(i, s.getUUID());
                else st.statues.add(s.getUUID());
                data.setDirty();
            }
            there.remove(s);
            l.statues.add(s);
        }
        for (RitualStatue extra : there) extra.discard(); // strays from a lost save: twelve kneel here, never more
    }

    // ---- the tick ---------------------------------------------------------------------------------------------------

    static void onServerTick(ServerTickEvent.Post e) {
        tick(e.getServer(), e.getServer().getTickCount() % 20 == 0);
    }

    /** One director tick; {@code gatherNow} also looks for players who walked into a ritual gate (once a second). */
    static void tick(MinecraftServer server, boolean gatherNow) {
        RitualData data = RitualData.get(server);
        long now = now(server);
        if (gatherNow) gather(server, data, now);
        if (data.rituals.isEmpty()) return;
        for (RitualData.State st : List.copyOf(data.rituals.values())) {
            Live l = live(server, st);
            if (l == null) {
                // the gate is gone (released, or the gate world is off): nothing left to run
                if (st.beat == Beat.DONE || !Gates.isRitual(server, st.gate)) {
                    data.rituals.remove(st.gate);
                    data.setDirty();
                }
                continue;
            }
            if (st.beat == Beat.DONE) {
                if (server.getTickCount() % 40 == 0) maybeRelease(server, data, st);
                continue;
            }
            cast(data, st, l);
            if (st.beat == Beat.WAITING) {
                waiting(server, data, st, l, now);
                continue;
            }
            List<ServerPlayer> here = present(server, st, l);
            if (here.isEmpty() && nobodyLeft(server, st)) {
                // everyone sealed in has gone another way (skipped, awakened by a command): the hall is spent
                enter(st, l, Beat.DONE, now);
                done(server, data, st, l);
                continue;
            }
            if (here.isEmpty()) {
                // the whole party is away: hold the scene, and play the beat again from its start when someone is back
                st.beatStart = now - Math.max(0, l.lastAge);
                if (!l.paused) {
                    l.paused = true;
                    data.setDirty();
                }
                continue;
            }
            if (l.paused) {
                l.paused = false;
                restartBeat(st, l, now);
                for (ServerPlayer p : here) sendScene(p, st, l, now);
            }
            run(server, data, st, l, here, now);
        }
    }

    /** Nobody of the party is still sealed in (all online and none of them in RITUAL any more). */
    private static boolean nobodyLeft(MinecraftServer server, RitualData.State st) {
        for (UUID u : st.party) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p == null || Prologue.progress(p).prologue == StoryProgress.Prologue.RITUAL) return false;
        }
        return true;
    }

    /** Party members online and in the hall's gate. */
    private static List<ServerPlayer> present(MinecraftServer server, RitualData.State st, Live l) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID u : st.party) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null && p.isAlive() && p.level() == l.hall.level() && Prologue.progress(p).prologue == StoryProgress.Prologue.RITUAL) out.add(p);
        }
        return out;
    }

    /** Once a second: a ritual state for every ritual gate someone in the story has walked into, and stray players. */
    private static void gather(MinecraftServer server, RitualData data, long now) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            int gate = storyGate(p);
            StoryProgress s = Prologue.progress(p);
            if (gate < 0 || Progression.profile(p).awakened()) continue;
            RitualData.State st = data.rituals.get(gate);
            if (s.prologue == StoryProgress.Prologue.GATE_OPEN) {
                if (st != null && st.beat.sealed()) {
                    reassign(p, s, "The bell tolled without you. Another door will open.");
                    continue;
                }
                if (st == null && ritualGateHere(p) == gate) {
                    st = new RitualData.State(gate);
                    st.beatStart = now;
                    st.mouth = GateHooks.get(server.overworld(), Integer.toString(gate)).map(GateHooks.GateView::mouth).orElse(BlockPos.ZERO);
                    data.rituals.put(gate, st);
                    data.setDirty();
                    DirectorLog.log(p, "story", "ritual: the hall of gate " + gate + " is waiting");
                }
            } else if (s.prologue == StoryProgress.Prologue.RITUAL && (st == null || st.beat == Beat.DONE || !st.party.contains(p.getUUID()))) {
                // sealed in a ritual that ended (or never knew them): the hand closed on them too
                Prologue.beginAcceptance(p, s);
            }
        }
    }

    /** A player the ritual began without gets a Bell Crypt of their own, soon. */
    static void reassign(ServerPlayer p, StoryProgress s, String why) {
        s.prologue = StoryProgress.Prologue.GLITCHED;
        s.prologueGate = "";
        s.prologueSince = p.level().getGameTime() - Prologue.GATE_AFTER_TICKS + 20 * 15;
        Prologue.sync(p);
        Signal.toast(p, Component.literal(why));
        DirectorLog.log(p, "story", "ritual: began without them; their own Bell Crypt opens soon");
    }

    // ---- beat 0: the hall waits -------------------------------------------------------------------------------------

    /** Players waited for: in the story, walking to this gate, online. */
    static List<ServerPlayer> waitingFor(MinecraftServer server, int gate) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) if (eligible(p, gate)) out.add(p);
        return out;
    }

    private static void waiting(MinecraftServer server, RitualData data, RitualData.State st, Live l, long now) {
        List<ServerPlayer> all = waitingFor(server, st.gate);
        int inside = 0;
        for (ServerPlayer p : all) if (l.hall.inHall(p)) inside++;
        if (inside == 0) {
            if (st.firstArrival >= 0) {
                st.firstArrival = -1; // everyone stepped back out: the clock starts again when someone comes in
                data.setDirty();
            }
            return;
        }
        if (st.firstArrival < 0) {
            st.firstArrival = now;
            data.setDirty();
        }
        boolean everyone = inside == all.size();
        long since = now - st.firstArrival;
        if ((everyone && since >= WAIT_GRACE) || since >= WAIT_TIMEOUT) begin(server, data, st, l, all, now);
    }

    /** The doors close on everyone inside; the ones still in the antechamber are drawn in, the rest get their own crypt. */
    private static void begin(MinecraftServer server, RitualData data, RitualData.State st, Live l, List<ServerPlayer> all, long now) {
        for (ServerPlayer p : all) {
            StoryProgress s = Prologue.progress(p);
            boolean inGate = ritualGateHere(p) == st.gate;
            if (!inGate) {
                reassign(p, s, "The bell tolled without you. Another door will open.");
                continue;
            }
            if (!l.hall.inHall(p)) {
                // in the antechamber or the doorway: the doors don't close on anyone, they draw you in
                Vec3 in = l.hall.doorway().add(0, 0, 5);
                p.teleportTo(l.hall.level(), in.x, in.y, in.z, java.util.Set.<Relative>of(), 0F, 0F, true);
            }
            st.party.add(p.getUUID());
            s.prologue = StoryProgress.Prologue.RITUAL;
            Prologue.sync(p);
            DirectorLog.log(p, "story", "ritual: sealed in the hall of gate " + st.gate + " with " + (all.size() - 1) + " other(s)");
        }
        if (st.party.isEmpty()) return;
        enter(st, l, Beat.DOORS, now);
        data.setDirty();
        for (ServerPlayer p : present(server, st, l)) sendScene(p, st, l, now);
    }

    // ---- the timeline -----------------------------------------------------------------------------------------------

    private static void enter(RitualData.State st, Live l, Beat beat, long start) {
        if (st.beat != beat) dev.awaken.Awaken.LOG.info("[story] ritual of gate {}: {} -> {} (party {})", st.gate, st.beat, beat, st.party.size());
        st.beat = beat;
        st.beatStart = start;
        l.lastAge = -1;
        l.lastBeat = beat;
        l.bowed.clear();
        l.onPlate.clear();
    }

    /** A beat played again from its start (the party came back): the hall is set as the beat expects it. */
    private static void restartBeat(RitualData.State st, Live l, long now) {
        st.beatStart = now;
        l.lastAge = -1;
        l.swings.clear();
        closeAllPits(l);
        sealDoors(l, true);
        setAllLights(l, st.beat != Beat.DOORS);
        boolean standing = st.beat == Beat.JUDGEMENT;
        for (RitualStatue s : l.statues) s.setStage(standing ? RitualStatue.STAND : RitualStatue.KNEEL);
        if (l.seated != null) l.seated.setStage(switch (st.beat) {
            case JUDGEMENT -> SeatedOne.AWAKE;
            case END -> SeatedOne.AWAKE;
            default -> SeatedOne.DORMANT;
        });
    }

    private static void run(MinecraftServer server, RitualData data, RitualData.State st, Live l, List<ServerPlayer> here, long now) {
        // process every moment since the last tick, carrying over into the next beats if time jumped
        for (int guard = 0; guard < 12 && st.beat != Beat.DONE; guard++) {
            long age = now - st.beatStart;
            Beat beat = st.beat;
            long from = l.lastAge;
            long to = Math.min(age, beat.length);
            if (to > from) {
                beatTick(server, st, l, here, from, to);
                l.lastAge = to;
            }
            if (st.beat != beat) break; // the beat moved itself on (the hand closed)
            if (age < beat.length) break;
            long nextStart = st.beatStart + beat.length;
            enter(st, l, beat.next(), nextStart);
            data.setDirty();
            if (st.beat != Beat.DONE) for (ServerPlayer p : here) sendScene(p, st, l, now);
        }
        if (st.beat == Beat.DONE) done(server, data, st, l);
    }

    /** Fires everything in this beat between ticks (from, to]; continuous work happens once at {@code to}. */
    private static void beatTick(MinecraftServer server, RitualData.State st, Live l, List<ServerPlayer> here, long from, long to) {
        switch (st.beat) {
            case DOORS -> doors(st, l, here, from, to);
            case BOW -> bow(st, l, here, from, to);
            case RING -> ring(st, l, here, from, to);
            case JUDGED -> judged(st, l, here, from, to);
            case WAKING -> waking(st, l, here, from, to);
            case JUDGEMENT -> judgement(server, st, l, here, from, to);
            case END -> end(server, st, l, here, from, to);
            default -> {}
        }
    }

    private static boolean at(long from, long to, long t) {
        return from < t && to >= t;
    }

    // ---- beat 1: the doors ------------------------------------------------------------------------------------------

    private static void doors(RitualData.State st, Live l, List<ServerPlayer> here, long from, long to) {
        if (at(from, to, 0)) {
            cue(l, here, Cue.DOORS, -1, l.hall.doorway(), Vec3.ZERO, 0.35F, 28);
            l.doorPlaced = 0;
            setAllLights(l, true);
        }
        // the doors grind shut over 28 ticks: the outer columns first, then the middle, from the floor up
        if (from < 28) {
            int want = (int) Math.min(l.hall.doorBlocks().size(), Math.ceil(l.hall.doorBlocks().size() * Math.min(28, to) / 28.0));
            placeDoor(l, want);
        }
        if (at(from, to, 34)) {
            setAllLights(l, false);
            cue(l, here, Cue.LIGHTS_OUT, -1, l.hall.altar(), Vec3.ZERO, 0, 46);
        }
        // the candles light one by one down the nave toward the throne
        if (to > 80 && from < 180) {
            List<RitualHall.Light> lights = l.hall.lights();
            for (RitualHall.Light li : lights) {
                if (li.fill()) continue;
                long t = 80 + Math.round(li.order() * 100);
                if (at(from, to, t)) {
                    setLight(l.hall.level(), li, true);
                    cue(l, here, Cue.CANDLE, -1, Vec3.atCenterOf(li.pos()), Vec3.ZERO, (float) li.order(), 0);
                }
            }
        }
        if (at(from, to, 184)) {
            for (RitualHall.Light li : l.hall.lights()) if (li.fill()) setLight(l.hall.level(), li, true);
            cue(l, here, Cue.LIGHTS_ON, -1, l.hall.altar(), Vec3.ZERO, 0, 0);
        }
    }

    private static final BlockState DOOR = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    private static final BlockState DOOR_INNER = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();

    /** Places the first n door blocks in closing order (outer laterals first, bottom up). */
    private static void placeDoor(Live l, int n) {
        List<BlockPos> blocks = l.hall.doorBlocks();
        ServerLevel level = l.hall.level();
        for (int k = l.doorPlaced; k < n; k++) {
            int i = doorOrder(l, k);
            BlockPos p = blocks.get(i);
            int lat = l.hall.doorLat().get(i);
            if (level.getBlockState(p).isAir()) level.setBlock(p, lat == 6 || lat == 9 ? DOOR : DOOR_INNER, Block.UPDATE_ALL);
        }
        l.doorPlaced = Math.max(l.doorPlaced, n);
    }

    /** The k-th block to close: laterals 6 and 9 (rows bottom up), then 7 and 8. */
    private static int doorOrder(Live l, int k) {
        List<Integer> lat = l.hall.doorLat();
        int seen = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < lat.size(); i++) {
                boolean outer = lat.get(i) == 6 || lat.get(i) == 9;
                if (outer == (pass == 0)) {
                    if (seen == k) return i;
                    seen++;
                }
            }
        }
        return Math.min(k, lat.size() - 1);
    }

    static void sealDoors(Live l, boolean on) {
        if (on) placeDoor(l, l.hall.doorBlocks().size());
    }

    // ---- beat 2: the rules ------------------------------------------------------------------------------------------

    private static void bow(RitualData.State st, Live l, List<ServerPlayer> here, long from, long to) {
        if (at(from, to, 0)) cue(l, here, Cue.TOLL, -1, l.hall.bell(), Vec3.ZERO, 1.0F, 0);
        if (at(from, to, BOW_WORD)) cue(l, here, Cue.WORD, 0, l.hall.words(), Vec3.ZERO, 0, BOW_DEADLINE - BOW_WORD + 30);
        if (to >= BOW_WORD && from < BOW_DEADLINE) {
            for (ServerPlayer p : here) if (p.isShiftKeyDown()) l.bowed.add(p.getUUID());
        }
        if (at(from, to, BOW_DEADLINE)) {
            for (ServerPlayer p : here) {
                if (l.bowed.contains(p.getUUID())) continue;
                RitualStatue s = nearestStatue(l, p.position());
                if (s == null) continue;
                cue(l, here, Cue.GLARE, s.getId(), s.position(), p.position(), 1, BOW_STRIKE - BOW_DEADLINE + 20);
                s.setStage(RitualStatue.GLARE);
            }
        }
        if (to >= BOW_DEADLINE && from < BOW_STRIKE) {
            for (ServerPlayer p : here) {
                if (l.bowed.contains(p.getUUID())) continue;
                RitualStatue s = nearestStatue(l, p.position());
                if (s != null) s.glareAt(p.getX(), p.getZ(), 12F);
            }
        }
        if (at(from, to, BOW_STRIKE)) {
            for (ServerPlayer p : here) {
                if (l.bowed.contains(p.getUUID())) continue;
                RitualStatue s = nearestStatue(l, p.position());
                Vec3 src = s != null ? s.position().add(0, 2.6, 0) : l.hall.altar();
                cue(l, here, Cue.RULE_STRIKE, s == null ? -1 : s.getId(), src, p.position().add(0, 1, 0), 1, 8);
                hit(p, s, RULE_DAMAGE, src, 1.1);
                DirectorLog.log(p, "story", "ritual: did not bow, struck");
            }
            for (RitualStatue s : l.statues) if (s.stage() == RitualStatue.GLARE) s.setStage(RitualStatue.KNEEL);
        }
    }

    /** Tests: who has bowed so far in this gate's BOW. */
    static boolean bowed(int gate, UUID player) {
        Live l = LIVE.get(gate);
        return l != null && l.bowed.contains(player);
    }

    private static void ring(RitualData.State st, Live l, List<ServerPlayer> here, long from, long to) {
        if (at(from, to, 0)) {
            cue(l, here, Cue.TOLL, -1, l.hall.bell(), Vec3.ZERO, 1.12F, 0);
            cue(l, here, Cue.WORD, 1, l.hall.words(), Vec3.ZERO, 0, RING_FIRST + 3 * RING_STEP);
        }
        for (int k = 0; k < 3; k++) {
            long lit = RING_FIRST + (long) k * RING_STEP, deadline = lit + RING_WINDOW;
            Vec3 plate = l.hall.plates().get(k);
            if (at(from, to, lit)) {
                l.onPlate.clear();
                cue(l, here, Cue.PLATE, k, plate, Vec3.ZERO, 1.0F + k * 0.19F, RING_WINDOW);
                cue(l, here, Cue.TOLL, k, plate, Vec3.ZERO, 1.26F + k * 0.25F, 0);
                plateLight(l, k, true);
            }
            if (to >= lit && from < deadline) {
                for (ServerPlayer p : here) if (l.hall.plateUnder(p.position()) == k) l.onPlate.add(p.getUUID());
            }
            if (at(from, to, deadline)) {
                plateLight(l, k, false);
                for (ServerPlayer p : here) {
                    if (l.onPlate.contains(p.getUUID())) continue;
                    // the statues turn their heads toward the one who didn't answer
                    for (RitualStatue s : l.statues) cue(l, here, Cue.GLARE, s.getId(), s.position(), p.position(), 0.6F, 40);
                    cue(l, List.of(p), Cue.PLATE_MISS, k, plate, p.position(), 0, 40);
                    l.misses++;
                    DirectorLog.log(p, "story", "ritual: missed plate " + (k + 1));
                }
            }
        }
        // heads follow the ones who missed, for a moment
        for (int k = 0; k < 3; k++) {
            long deadline = RING_FIRST + (long) k * RING_STEP + RING_WINDOW;
            if (to > deadline && to < deadline + 40) {
                for (ServerPlayer p : here) {
                    if (l.onPlate.contains(p.getUUID())) continue;
                    for (RitualStatue s : l.statues) s.glareAt(p.getX(), p.getZ(), 6F);
                }
            }
        }
    }

    /** Tests: plates missed in this gate's RING so far (one per player per plate). */
    static int misses(int gate) {
        Live l = LIVE.get(gate);
        return l == null ? 0 : l.misses;
    }

    private static void plateLight(Live l, int k, boolean on) {
        Vec3 c = l.hall.plates().get(k);
        BlockPos p = BlockPos.containing(c.x, c.y + 1, c.z);
        ServerLevel level = l.hall.level();
        if (on) level.setBlock(p, Blocks.LIGHT.defaultBlockState().setValue(BlockStateProperties.LEVEL, 13), Block.UPDATE_ALL);
        else if (level.getBlockState(p).is(Blocks.LIGHT)) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void judged(RitualData.State st, Live l, List<ServerPlayer> here, long from, long to) {
        if (at(from, to, 0)) {
            cue(l, here, Cue.TOLL, -1, l.hall.bell(), Vec3.ZERO, 0.89F, 0);
            cue(l, here, Cue.WORD, 2, l.hall.words(), Vec3.ZERO, 0, 90);
            cue(l, here, Cue.SHAKE, -1, l.hall.altar(), Vec3.ZERO, 0.25F, 20);
        }
        if (at(from, to, 18)) cue(l, here, Cue.TOLL, -1, l.hall.bell(), Vec3.ZERO, 0.75F, 0);
    }

    // ---- beat 3: the waking -----------------------------------------------------------------------------------------

    private static void waking(RitualData.State st, Live l, List<ServerPlayer> here, long from, long to) {
        Vec3 eyes = eyes(l);
        if (at(from, to, 0)) {
            if (l.seated != null) l.seated.setStage(SeatedOne.WAKING);
            cue(l, here, Cue.EYES, l.seated == null ? -1 : l.seated.getId(), eyes, Vec3.ZERO, 0, 60);
            cue(l, here, Cue.HOLD, -1, eyes, Vec3.ZERO, 1, 84);
        }
        if (at(from, to, 12)) cue(l, here, Cue.SHAKE, -1, l.hall.altar(), Vec3.ZERO, 0.35F, 60);
        if (at(from, to, 58)) {
            cue(l, here, Cue.EYES, l.seated == null ? -1 : l.seated.getId(), eyes, Vec3.ZERO, 1, 30);
            cue(l, here, Cue.SHAKE, -1, l.hall.altar(), Vec3.ZERO, 0.9F, 25);
        }
        if (at(from, to, 64) && l.seated != null) l.seated.setStage(SeatedOne.AWAKE);
        if (to > 64) lookAtNearest(l, here, 1.4F);
        for (int i = 0; i < l.statues.size(); i++) {
            if (at(from, to, 92 + i * 2L)) l.statues.get(i).setStage(RitualStatue.RISE);
            if (at(from, to, 130 + i * 2L)) l.statues.get(i).setStage(RitualStatue.STAND);
        }
        if (at(from, to, 92)) cue(l, here, Cue.RISE, -1, l.hall.altar(), Vec3.ZERO, 0.5F, 40);
    }

    // ---- beat 4: the Judgement --------------------------------------------------------------------------------------

    private static void judgement(MinecraftServer server, RitualData.State st, Live l, List<ServerPlayer> here, long from, long to) {
        ServerLevel level = l.hall.level();
        boolean closing = to >= st.beat.length - 25;
        // the gaze: a line on the floor fills, then burns along it
        for (int at : GAZE_AT) {
            if (at(from, to, at)) {
                ServerPlayer target = here.get(Math.floorMod(l.gazeTurn++, here.size()));
                l.lookAt = target.getUUID();
                Vec3 base = new Vec3(l.hall.throne().x, l.hall.floorY() + 0.05, l.hall.throne().z - 4.5);
                Vec3 dir = new Vec3(target.getX() - base.x, 0, target.getZ() - base.z);
                if (dir.lengthSqr() < 1) dir = new Vec3(0, 0, -1);
                dir = dir.normalize();
                l.gazeFrom = base;
                l.gazeTo = clampToHall(l, base.add(dir.scale(GAZE_LENGTH)));
                if (l.seated != null) l.seated.setStage(SeatedOne.GAZE);
                cue(l, here, Cue.GAZE_CHARGE, l.seated == null ? -1 : l.seated.getId(), l.gazeFrom, l.gazeTo, (float) GAZE_HALF, GAZE_FILL);
            }
            if (at(from, to, at + GAZE_FILL)) {
                cue(l, here, Cue.GAZE_FIRE, l.seated == null ? -1 : l.seated.getId(), eyes(l), l.gazeTo, (float) GAZE_HALF, 12);
                cue(l, here, Cue.SHAKE, -1, l.gazeTo, Vec3.ZERO, 0.45F, 10);
                for (ServerPlayer p : here) {
                    double d = distToSegment(p.position(), l.gazeFrom, l.gazeTo);
                    if (d <= GAZE_HALF + 0.35) {
                        Vec3 away = awayFromLine(p.position(), l.gazeFrom, l.gazeTo);
                        hit(p, null, GAZE_DAMAGE, p.position().subtract(away), 0.9);
                    }
                }
            }
            if (at(from, to, at + GAZE_FILL + 14) && l.seated != null) l.seated.setStage(SeatedOne.AWAKE);
        }
        lookAtTarget(server, l, here, 2.0F);
        // the statues advance on someone, wind up, and swing
        if (!closing && to >= 40 && Math.floorDiv(to - 40, SWING_EVERY) != Math.floorDiv(Math.max(39, from) - 40, SWING_EVERY)) startSwing(l, here, to);
        tickSwings(l, here, to, closing);
        // the rune ring opens into pits
        if (!closing && to >= PIT_FIRST && Math.floorDiv(to - PIT_FIRST, PIT_EVERY) != Math.floorDiv(Math.max(PIT_FIRST - 1, from) - PIT_FIRST, PIT_EVERY)) {
            for (int n = 0; n < 2; n++) {
                int seg = Math.floorMod(l.pitTurn * 3 + n * 4 + 1, 8);
                if (l.pitWarn[seg] < 0 && l.pitOpen[seg] < 0) {
                    l.pitWarn[seg] = to;
                    cue(l, here, Cue.PIT, seg, l.hall.altar(), Vec3.ZERO, 0, PIT_WARN);
                }
            }
            l.pitTurn++;
        }
        tickPits(l, here, to, closing);
        if (at(from, to, st.beat.length - 20)) {
            for (RitualStatue s : l.statues) s.setStage(RitualStatue.STAND);
            l.swings.clear();
        }
    }

    private static Vec3 clampToHall(Live l, Vec3 p) {
        var b = l.hall.hall();
        return new Vec3(Math.clamp(p.x, b.minX + 1, b.maxX - 1), p.y, Math.clamp(p.z, b.minZ, b.maxZ - 1));
    }

    private static void startSwing(Live l, List<ServerPlayer> here, long to) {
        int max = Math.min(4, 2 + here.size() / 2);
        if (l.swings.size() >= max) return;
        ServerPlayer target = here.get((int) Math.floorMod(to / SWING_EVERY, (long) here.size()));
        RitualStatue best = null;
        int bestIdx = -1;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < l.statues.size(); i++) {
            int idx = i;
            if (l.swings.stream().anyMatch(s -> s.statue == idx)) continue;
            RitualStatue s = l.statues.get(i);
            double d = s.distanceToSqr(target);
            if (d < bestD) {
                bestD = d;
                best = s;
                bestIdx = i;
            }
        }
        if (best == null) return;
        Swing w = new Swing();
        w.statue = bestIdx;
        w.target = target.getUUID();
        w.phase = 0;
        w.since = to;
        l.swings.add(w);
        best.setStage(RitualStatue.WALK);
    }

    private static void tickSwings(Live l, List<ServerPlayer> here, long to, boolean closing) {
        for (int i = l.swings.size() - 1; i >= 0; i--) {
            Swing w = l.swings.get(i);
            RitualStatue s = l.statues.get(w.statue);
            ServerPlayer target = null;
            for (ServerPlayer p : here) if (p.getUUID().equals(w.target)) target = p;
            switch (w.phase) {
                case 0 -> {
                    if (target == null || closing) {
                        s.setStage(RitualStatue.STAND);
                        l.swings.remove(i);
                        continue;
                    }
                    s.face(target.getX(), target.getZ(), 8F);
                    s.stepToward(target.position(), STATUE_SPEED);
                    if (s.distanceTo(target) <= SWING_START || to - w.since >= ADVANCE_MAX) {
                        w.phase = 1;
                        w.since = to;
                        w.facing = target.position();
                        s.face(target.getX(), target.getZ(), 180F);
                        s.setStage(RitualStatue.WINDUP);
                        cue(l, here, Cue.SWING, s.getId(), s.position(), w.facing, (float) SWING_REACH, SWING_WINDUP);
                    }
                }
                case 1 -> {
                    if (to - w.since >= SWING_WINDUP) {
                        s.setStage(RitualStatue.STRIKE);
                        Vec3 fwd = Vec3.directionFromRotation(0, s.getYRot());
                        cue(l, here, Cue.IMPACT, s.getId(), s.position().add(fwd.scale(2.5)), Vec3.ZERO, 0.55F, 10);
                        for (ServerPlayer p : here) {
                            if (inSector(s.position(), fwd, p.position(), SWING_REACH, 55)) hit(p, s, SWING_DAMAGE, s.position(), 1.2);
                        }
                        w.phase = 2;
                        w.since = to;
                    }
                }
                default -> {
                    if (to - w.since >= SWING_RECOVER) {
                        s.setStage(RitualStatue.STAND);
                        l.swings.remove(i);
                    }
                }
            }
        }
    }

    private static boolean inSector(Vec3 origin, Vec3 fwd, Vec3 p, double reach, double halfDeg) {
        Vec3 d = new Vec3(p.x - origin.x, 0, p.z - origin.z);
        double len = d.length();
        if (len > reach + 0.4) return false;
        if (len < 1.0) return true;
        double cos = (d.x * fwd.x + d.z * fwd.z) / (len * Math.max(1e-6, Math.hypot(fwd.x, fwd.z)));
        return cos >= Math.cos(Math.toRadians(halfDeg));
    }

    private static void tickPits(Live l, List<ServerPlayer> here, long to, boolean closing) {
        ServerLevel level = l.hall.level();
        for (int seg = 0; seg < 8; seg++) {
            if (l.pitWarn[seg] >= 0 && to - l.pitWarn[seg] >= PIT_WARN) {
                l.pitWarn[seg] = -1;
                if (!closing) {
                    l.pitOpen[seg] = to;
                    openPit(l, seg);
                    cue(l, here, Cue.PIT, seg, l.hall.altar(), Vec3.ZERO, 1, PIT_OPEN);
                }
            }
            if (l.pitOpen[seg] >= 0) {
                if ((to - l.pitOpen[seg]) % 15 == 0) {
                    for (ServerPlayer p : here) {
                        if (inPit(l, seg, p)) {
                            hit(p, null, PIT_DAMAGE, p.position().add(0, -1, 0), 0.2);
                            Vec3 out = new Vec3(p.getX() - l.hall.altar().x, 0, p.getZ() - l.hall.altar().z).normalize();
                            p.setDeltaMovement(out.x * 0.45, 0.62, out.z * 0.45);
                            p.syncVelocity = true;
                        }
                    }
                }
                if (to - l.pitOpen[seg] >= PIT_OPEN || closing) {
                    closePit(l, seg, here);
                    cue(l, here, Cue.PIT, seg, l.hall.altar(), Vec3.ZERO, 2, 0);
                }
            }
        }
    }

    private static boolean inPit(Live l, int seg, ServerPlayer p) {
        if (p.getY() > l.hall.floorY() - 0.2) return false;
        for (BlockPos b : l.hall.ring().get(seg)) if (b.getX() == p.getBlockX() && b.getZ() == p.getBlockZ()) return true;
        return false;
    }

    private static void openPit(Live l, int seg) {
        ServerLevel level = l.hall.level();
        for (BlockPos b : l.hall.ring().get(seg)) {
            level.setBlock(b, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(b.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            // a red glow at the bottom: magma, so the pit reads (its heat is the director's, not the block's)
            level.setBlock(b.below(2), Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static void closePit(Live l, int seg, List<ServerPlayer> here) {
        ServerLevel level = l.hall.level();
        for (ServerPlayer p : here) {
            if (inPit(l, seg, p)) p.teleportTo(level, p.getX(), l.hall.floorY() + 0.05, p.getZ(), java.util.Set.<Relative>of(), p.getYRot(), p.getXRot(), false);
        }
        List<BlockPos> blocks = l.hall.ring().get(seg);
        List<BlockState> top = l.hall.ringTop().get(seg);
        for (int i = 0; i < blocks.size(); i++) {
            BlockPos b = blocks.get(i);
            level.setBlock(b.below(2), l.hall.ringBelow(), Block.UPDATE_ALL);
            level.setBlock(b.below(), l.hall.ringBelow(), Block.UPDATE_ALL);
            level.setBlock(b, top.get(i), Block.UPDATE_ALL);
        }
        l.pitOpen[seg] = -1;
        l.pitWarn[seg] = -1;
    }

    private static void closeAllPits(Live l) {
        for (int seg = 0; seg < 8; seg++) {
            if (l.pitOpen[seg] >= 0 || !l.hall.level().getBlockState(l.hall.ring().get(seg).getFirst()).equals(l.hall.ringTop().get(seg).getFirst())) {
                closePit(l, seg, List.of());
            }
            l.pitWarn[seg] = -1;
        }
    }

    // ---- beat 5: the end --------------------------------------------------------------------------------------------

    private static void end(MinecraftServer server, RitualData.State st, Live l, List<ServerPlayer> here, long from, long to) {
        Vec3 palm = palm(l);
        if (at(from, to, 0)) {
            if (l.seated != null) l.seated.setStage(SeatedOne.RAISE);
            cue(l, here, Cue.HOLD, -1, palm, Vec3.ZERO, 1, CLOSE + 10);
            closeAllPits(l);
            l.swings.clear();
            for (RitualStatue s : l.statues) s.setStage(RitualStatue.KNEEL);
        }
        if (at(from, to, DRAIN_FROM)) cue(l, here, Cue.DRAIN, l.seated == null ? -1 : l.seated.getId(), palm, Vec3.ZERO, 0, CLOSE - DRAIN_FROM);
        // every light in the hall goes out, from the doors to the throne, drawn into the palm
        if (to > DRAIN_FROM && from < DRAIN_FROM + DRAIN_TICKS) {
            for (RitualHall.Light li : l.hall.lights()) {
                long t = DRAIN_FROM + Math.round(li.order() * DRAIN_TICKS);
                if (at(from, to, t)) setLight(l.hall.level(), li, false);
            }
        }
        if (at(from, to, ERROR_AT)) cue(l, here, Cue.ERROR, -1, palm, Vec3.ZERO, 0, CLOSE - ERROR_AT);
        if (at(from, to, CLOSE)) {
            if (l.seated != null) l.seated.setStage(SeatedOne.CLOSE);
            cue(l, here, Cue.DEATH, -1, palm, Vec3.ZERO, 0, DEATH - CLOSE + 20);
        }
        if (at(from, to, DEATH)) {
            // the hand closes on everyone at once; the hall is done with them (nobody is left in it to wait for)
            for (ServerPlayer p : here) {
                StoryProgress s = Prologue.progress(p);
                DirectorLog.log(p, "story", "ritual: the hand closed");
                Prologue.beginAcceptance(p, s);
            }
            enter(st, l, Beat.DONE, st.beatStart + DEATH);
        }
    }

    private static void done(MinecraftServer server, RitualData data, RitualData.State st, Live l) {
        if (l.seated != null) l.seated.setStage(SeatedOne.SPENT);
        data.setDirty();
    }

    /** The hall is spent: once nobody is still at the question for it, the gate comes down. */
    private static void maybeRelease(MinecraftServer server, RitualData data, RitualData.State st) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            StoryProgress s = Prologue.progress(p);
            if (s.prologueGate.equals(Integer.toString(st.gate))
                    && (s.prologue == StoryProgress.Prologue.ACCEPTING || s.prologue == StoryProgress.Prologue.RITUAL)) return;
        }
        // party members still offline and not yet awake keep the hall (they wake into it when they're back)
        int unanswered = 0;
        for (UUID u : st.party) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p == null) unanswered++;
        }
        long since = now(server) - st.beatStart;
        if (unanswered > 0 && since < 20 * 60 * 10) return;
        GateHooks.release(server.overworld(), Integer.toString(st.gate));
        if (!Gates.isRitual(server, st.gate) || GateHooks.get(server.overworld(), Integer.toString(st.gate)).map(v -> v.state().equals("RELEASED")).orElse(true)) {
            // the crypt folds away, and far below, the bell sounds once more
            if (!st.mouth.equals(BlockPos.ZERO)) {
                server.overworld().playSound(null, st.mouth, RitualSounds.BELL.get(), net.minecraft.sounds.SoundSource.AMBIENT, 6F, 0.5F);
            }
            data.rituals.remove(st.gate);
            LIVE.remove(st.gate);
            data.setDirty();
        }
    }

    // ---- shared ---------------------------------------------------------------------------------------------------

    static Vec3 eyes(Live l) {
        return l.hall.throne().add(0, EYE_HEIGHT, -EYE_FORWARD);
    }

    /** About where the raised palm is (the client draws the light at the hand bone itself). */
    static Vec3 palm(Live l) {
        return l.hall.throne().add(-3.0, 17.0, -9.0);
    }

    private static @Nullable RitualStatue nearestStatue(Live l, Vec3 p) {
        RitualStatue best = null;
        double bd = Double.MAX_VALUE;
        for (RitualStatue s : l.statues) {
            double d = s.position().distanceToSqr(p);
            if (d < bd) {
                bd = d;
                best = s;
            }
        }
        return best;
    }

    private static void lookAtNearest(Live l, List<ServerPlayer> here, float step) {
        if (l.seated == null || here.isEmpty()) return;
        ServerPlayer best = here.getFirst();
        double bd = Double.MAX_VALUE;
        for (ServerPlayer p : here) {
            double d = p.distanceToSqr(l.seated);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        l.seated.lookToward(best.getX(), best.getEyeY(), best.getZ(), step);
    }

    private static void lookAtTarget(MinecraftServer server, Live l, List<ServerPlayer> here, float step) {
        if (l.seated == null) return;
        ServerPlayer t = l.lookAt == null ? null : server.getPlayerList().getPlayer(l.lookAt);
        if (t == null || !here.contains(t)) {
            lookAtNearest(l, here, step);
            return;
        }
        l.seated.lookToward(t.getX(), t.getEyeY(), t.getZ(), step);
    }

    /** A hit from the hall: it hurts and throws you (the clamp keeps you alive), and the client feels it. */
    private static void hit(ServerPlayer p, @Nullable RitualStatue by, float damage, Vec3 from, double knock) {
        ServerLevel level = (ServerLevel) p.level();
        var source = by != null ? p.damageSources().mobAttack(by) : p.damageSources().magic();
        p.hurtServer(level, source, damage);
        Vec3 d = new Vec3(p.getX() - from.x, 0, p.getZ() - from.z);
        if (d.lengthSqr() < 1e-4) d = new Vec3(0, 0, 1);
        d = d.normalize();
        p.setDeltaMovement(d.x * knock, 0.42, d.z * knock);
        p.syncVelocity = true;
        AwakenNet.send(p, new StoryPayloads.RitualCue(Cue.HIT.ordinal(), -1, from, p.position(), damage, 10));
    }

    private static double distToSegment(Vec3 p, Vec3 a, Vec3 b) {
        double dx = b.x - a.x, dz = b.z - a.z;
        double len2 = dx * dx + dz * dz;
        double t = len2 < 1e-6 ? 0 : Math.clamp(((p.x - a.x) * dx + (p.z - a.z) * dz) / len2, 0, 1);
        return Math.hypot(p.x - (a.x + dx * t), p.z - (a.z + dz * t));
    }

    private static Vec3 awayFromLine(Vec3 p, Vec3 a, Vec3 b) {
        double dx = b.x - a.x, dz = b.z - a.z;
        double len = Math.max(1e-6, Math.hypot(dx, dz));
        double nx = -dz / len, nz = dx / len;
        double side = (p.x - a.x) * nx + (p.z - a.z) * nz;
        return side >= 0 ? new Vec3(nx, 0, nz) : new Vec3(-nx, 0, -nz);
    }

    static void setAllLights(Live l, boolean on) {
        for (RitualHall.Light li : l.hall.lights()) setLight(l.hall.level(), li, on);
    }

    private static void setLight(ServerLevel level, RitualHall.Light li, boolean on) {
        BlockState cur = level.getBlockState(li.pos());
        if (on) {
            if (!cur.equals(li.lit())) level.setBlock(li.pos(), li.lit(), Block.UPDATE_ALL);
            return;
        }
        if (li.lit().hasProperty(BlockStateProperties.LIT)) {
            if (cur.hasProperty(BlockStateProperties.LIT) && cur.getValue(BlockStateProperties.LIT)) {
                level.setBlock(li.pos(), cur.setValue(BlockStateProperties.LIT, false), Block.UPDATE_ALL);
            }
        } else if (!cur.isAir()) {
            level.setBlock(li.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** Sends a cue to the party and anyone else standing in the hall. */
    private static void cue(Live l, List<ServerPlayer> to, Cue kind, int id, Vec3 a, Vec3 b, float f, int ticks) {
        StoryPayloads.RitualCue c = new StoryPayloads.RitualCue(kind.ordinal(), id, a, b, f, ticks);
        for (ServerPlayer p : to) AwakenNet.send(p, c);
    }

    // ---- what the client and the Now tracker are told --------------------------------------------------------------

    static void sendScene(ServerPlayer p, RitualData.State st, Live l, long now) {
        int beat = st.beat == Beat.DONE ? StoryPayloads.RitualScene.NONE : st.beat.ordinal();
        AwakenNet.send(p, new StoryPayloads.RitualScene(beat, (int) Math.max(0, now - st.beatStart), l.seated == null ? -1 : l.seated.getId(),
                l.hall.throne(), l.hall.altar(), l.hall.words(), l.hall.doorway(), l.hall.plates()));
    }

    /** The scene this player should see now (tests, and a login). */
    static int beatOf(ServerPlayer p) {
        int gate = storyGate(p);
        if (gate < 0) return StoryPayloads.RitualScene.NONE;
        RitualData.State st = RitualData.get(p.level().getServer()).rituals.get(gate);
        return st == null ? StoryPayloads.RitualScene.NONE : st.beat.ordinal();
    }

    /** On login: back in the scene where it is now, or, if the hand closed while you were away, at the question. */
    static void onLogin(ServerPlayer p) {
        StoryProgress s = Prologue.progress(p);
        MinecraftServer server = p.level().getServer();
        int gate = storyGate(p);
        if (gate < 0) return;
        RitualData.State st = RitualData.get(server).rituals.get(gate);
        if (s.prologue == StoryProgress.Prologue.RITUAL) {
            if (st == null || st.beat == Beat.DONE || !st.party.contains(p.getUUID())) {
                Prologue.beginAcceptance(p, s);
                return;
            }
            Live l = live(server, st);
            if (l != null) sendScene(p, st, l, now(server));
        } else if (s.prologue == StoryProgress.Prologue.GATE_OPEN && st != null && st.beat == Beat.WAITING) {
            Live l = live(server, st);
            if (l != null && ritualGateHere(p) == gate) sendScene(p, st, l, now(server));
        }
    }

    /** The overworld spot a ritual gate's party wakes at (kept even after the gate is gone). */
    static Optional<Vec3> mouthOf(MinecraftServer server, String gateId) {
        try {
            RitualData.State st = RitualData.get(server).rituals.get(Integer.parseInt(gateId));
            if (st != null && !st.mouth.equals(BlockPos.ZERO)) return Optional.of(Vec3.atBottomCenterOf(st.mouth));
        } catch (NumberFormatException ignored) {
            // not a number: not ours
        }
        return Optional.empty();
    }

    /** The Now tracker: inside the hall's antechamber or nave while it waits. */
    static List<NowHooks.Suggestion> now(ServerPlayer p) {
        int gate = storyGate(p);
        if (gate < 0 || Progression.profile(p).awakened()) return List.of();
        StoryProgress s = Prologue.progress(p);
        MinecraftServer server = p.level().getServer();
        RitualData.State st = RitualData.get(server).rituals.get(gate);
        if (s.prologue == StoryProgress.Prologue.GATE_OPEN) {
            RitualHall hall = hall(server, gate);
            if (hall != null && ritualGateHere(p) == gate) {
                if (!hall.inHall(p)) return List.of(NowHooks.Suggestion.of("story:ritual", 95, "Go through the doors"));
                int missing = 0;
                for (ServerPlayer o : waitingFor(server, gate)) if (!hall.inHall(o)) missing++;
                if (missing > 0) return List.of(NowHooks.Suggestion.of("story:ritual", 95, "Waiting for " + missing + " hunter" + (missing == 1 ? "" : "s") + "…"));
                return List.of();
            }
            if (p.level().dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                var view = GateHooks.get(server.overworld(), Integer.toString(gate));
                if (view.isPresent()) {
                    int d = (int) Math.sqrt(view.get().mouth().distSqr(p.blockPosition()));
                    return List.of(NowHooks.Suggestion.at("story:ritual", 70, "A bell is calling · " + d + " m", view.get().mouth()));
                }
            }
        }
        return List.of();
    }

    // ---- admin (autopilot tours) ---------------------------------------------------------------------------------------

    /** Closes the doors now (skips the wait). */
    static boolean forceStart(ServerPlayer p) {
        MinecraftServer server = p.level().getServer();
        int gate = storyGate(p);
        if (gate < 0) return false;
        RitualData data = RitualData.get(server);
        RitualData.State st = data.rituals.get(gate);
        if (st == null) {
            if (ritualGateHere(p) != gate) return false;
            gather(server, data, now(server));
            st = data.rituals.get(gate);
            if (st == null) return false;
        }
        if (st.beat != Beat.WAITING) return false;
        Live l = live(server, st);
        if (l == null) return false;
        cast(data, st, l);
        begin(server, data, st, l, waitingFor(server, gate), now(server));
        return st.beat == Beat.DOORS;
    }

    /** Jumps the player's ritual to the start of a beat (1 DOORS ... 7 END). */
    static boolean jump(ServerPlayer p, int beat) {
        MinecraftServer server = p.level().getServer();
        int gate = storyGate(p);
        RitualData data = RitualData.get(server);
        RitualData.State st = gate < 0 ? null : data.rituals.get(gate);
        if (st == null || st.beat == Beat.WAITING || beat < 1 || beat >= Beat.DONE.ordinal()) return false;
        Live l = live(server, st);
        if (l == null) return false;
        enter(st, l, Beat.values()[beat], now(server));
        restartBeat(st, l, now(server));
        data.setDirty();
        for (ServerPlayer o : present(server, st, l)) sendScene(o, st, l, now(server));
        return true;
    }

    /** Tours: steps through the doors (into the nave, facing the throne), or onto plate k (0..2). */
    static boolean place(ServerPlayer p, int plate) {
        int gate = storyGate(p);
        RitualHall hall = gate < 0 ? null : hall(p.level().getServer(), gate);
        if (hall == null || ritualGateHere(p) != gate) return false;
        Vec3 at = plate < 0 ? hall.doorway().add(0, 0, 6) : hall.plates().get(Math.clamp(plate, 0, 2));
        p.teleportTo(hall.level(), at.x, at.y, at.z, java.util.Set.<Relative>of(), 0F, 0F, true);
        return true;
    }

    /** Tests: the ritual of this gate is gone (its cast too), as if it had never begun. */
    static void dropForTests(MinecraftServer server, int gate) {
        Live l = LIVE.remove(gate);
        if (l != null) {
            if (l.seated != null) l.seated.discard();
            for (RitualStatue s : l.statues) s.discard();
        }
        RitualData.get(server).rituals.remove(gate);
    }

    /** Tests: ticks into the current beat. */
    static long age(MinecraftServer server, int gate) {
        RitualData.State st = RitualData.get(server).rituals.get(gate);
        return st == null ? -1 : now(server) - st.beatStart;
    }

    /** Tests: the beat and how far into it. */
    static String beatName(MinecraftServer server, int gate) {
        RitualData.State st = RitualData.get(server).rituals.get(gate);
        return st == null ? "none" : st.beat.name();
    }

    /** Tests: the cast as it stands. */
    static @Nullable SeatedOne seated(int gate) {
        Live l = LIVE.get(gate);
        return l == null ? null : l.seated;
    }

    static List<RitualStatue> statues(int gate) {
        Live l = LIVE.get(gate);
        return l == null ? List.of() : List.copyOf(l.statues);
    }

    /** Tests: a server restart (nothing live survives, only the saved state). */
    static void forgetLive() {
        LIVE.clear();
    }

    static String status(ServerPlayer p) {
        int gate = storyGate(p);
        if (gate < 0) return "no ritual gate";
        RitualData.State st = RitualData.get(p.level().getServer()).rituals.get(gate);
        if (st == null) return "gate " + gate + ": nobody has come in yet";
        return "gate " + gate + ": " + st.beat + " for " + (now(p.level().getServer()) - st.beatStart) + " ticks, party " + st.party.size();
    }
}
