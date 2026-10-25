package dev.awaken.opening;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.api.CitizenHooks;
import dev.awaken.core.api.GateHooks;
import dev.awaken.opening.net.OpeningPayloads;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import dev.awaken.story.Story;
import dev.awaken.story.StoryProgress.Prologue;
import dev.awaken.story.net.StoryPayloads;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The opening's server timeline (SPEC §16.4). The beat lives in the story's state (StoryProgress: SKY, RUN, ARRIVAL,
 * then the story's GATE_OPEN, RITUAL and ACCEPTING, then WAKE and REGISTRY) with the moment it began; this class moves
 * players through it, sends each client its scene with the age it has reached (a relog resumes mid-flight), and keeps
 * the group's clock: the first arrival, the stragglers, the cinematic that plays for everyone at once.
 */
final class OpeningDirector {
    private OpeningDirector() {}

    /** The card (the city's name over black, one bell) and the flight into your eyes, in ticks (the client's own). */
    static final int CARD = 110, FLIGHT = 380, LAND = 50;
    /** Without a client to say it landed (a test player), the sky ends on the clock; with one, at most this long. */
    static final int SKY_END = CARD + FLIGHT + LAND, SKY_MAX = 20 * 90;
    /** The bell, then the Signal's line a moment after its first toll. */
    static final int LINE_AFTER = 34;
    /** The arrival: a breath before a solo cinematic, three seconds after the last of a group; the cinematic's length. */
    static final int SOLO_DELAY = 6, GROUP_DELAY = 60, CINEMATIC = 240;
    /** The pull at the cinematic's end: the black holds this long for a gate still forming before giving up. */
    static final int GATE_PATIENCE = 20 * 45;
    /** Stragglers (SPEC §16.0.4): the light reaches for them 5 min after the first arrival, or after 2 min idle. */
    static final int STRAGGLERS = 20 * 60 * 5, IDLE = 20 * 60 * 2;
    /** The Bell Bridge goes when a runner comes this close (or reaches the Quay). */
    static final double BRIDGE_NEAR = 44;
    /** The market empties toward the runners once they're this close to the square (or in the market). */
    static final double RUSH_NEAR = 75;
    /** The infirmary, when no client says the eyes have opened. */
    static final int WAKE_END = 20 * 38, WAKE_MAX = 20 * 120;

    /** Where each runner last moved, and when (the idle pull): transient. */
    static final Map<UUID, long[]> MOVED = new HashMap<>();
    /** The cinematic whose burst the citizens were told of; whether they know the Bell Bridge is down (this session). */
    private static long burstFor = -1;
    private static boolean bridgeTold;

    /** Arrivals waiting for the story's gate to finish forming: player → when the pull began. */
    private static final Map<UUID, Long> PULLING = new HashMap<>();

    static void forget() {
        MOVED.clear();
        PULLING.clear();
        burstFor = -1;
        bridgeTold = false;
    }

    // ---- who --------------------------------------------------------------------------------------------------------

    static OpeningState state(ServerPlayer p) {
        return p.getData(OpeningModule.STATE.get());
    }

    static Prologue beat(ServerPlayer p) {
        return Story.prologue(p);
    }

    /** A player the opening may take: a Carillon world's overworld, the story on, not awakened yet. */
    static boolean eligible(ServerPlayer p) {
        return Story.on() && p.level().dimension() == Level.OVERWORLD && OpeningPlaces.get() != null
                && !Progression.profile(p).awakened() && !p.isSpectator();
    }

    /** OpeningHooks.inOpening: one of the opening's beats, or the story's own between the arrival and the Accept. */
    static boolean inOpening(ServerPlayer p) {
        Prologue b = beat(p);
        if (b.opening()) return true;
        boolean storyPart = b == Prologue.GATE_OPEN || b == Prologue.RITUAL || b == Prologue.ACCEPTING;
        return storyPart && state(p).arrived && !Progression.profile(p).awakened();
    }

    static @Nullable String stage(ServerPlayer p) {
        Prologue b = beat(p);
        if (b.opening()) return b.name().toLowerCase(java.util.Locale.ROOT);
        return inOpening(p) ? b == Prologue.ACCEPTING ? "void" : "ritual" : null;
    }

    /** Tests move the opening's clock forward instead of waiting. */
    static long clockOffset;

    static long now(ServerPlayer p) {
        return p.level().getGameTime() + clockOffset;
    }

    static long now(MinecraftServer server) {
        return server.overworld().getGameTime() + clockOffset;
    }

    /** The story's gate as the opening uses it: tests stand a fake one in. */
    interface Gate {
        java.util.Optional<String> open(ServerPlayer p, BlockPos near);

        boolean usable(MinecraftServer server, String id);

        java.util.Optional<BlockPos> pos(MinecraftServer server, String id);

        boolean enter(ServerPlayer p, String id);
    }

    static final Gate REAL_GATE = new Gate() {
        @Override public java.util.Optional<String> open(ServerPlayer p, BlockPos near) {
            return GateHooks.openScripted(p, "awaken:bell_crypt", 0, near, 1);
        }

        @Override public boolean usable(MinecraftServer server, String gate) {
            if (gate.isEmpty()) return false;
            String s = GateHooks.get(server.overworld(), gate).map(GateHooks.GateView::state).orElse("RELEASED");
            if (!(s.equals("BUILDING") || s.equals("READY") || s.equals("ACTIVE") || s.equals("OPEN"))) return false;
            return !OpeningModule.ritualSealed(server, gate);
        }

        @Override public java.util.Optional<BlockPos> pos(MinecraftServer server, String gate) {
            return gate.isEmpty() ? java.util.Optional.empty() : GateHooks.get(server.overworld(), gate).map(GateHooks.GateView::pos);
        }

        @Override public boolean enter(ServerPlayer p, String gate) {
            return OpeningModule.enterGate(p, gate);
        }
    };

    static Gate gates = REAL_GATE;

    private static long age(ServerPlayer p) {
        return now(p) - Story.prologueSince(p);
    }

    // ---- the beginning ----------------------------------------------------------------------------------------------

    /** On login and on every player tick while NOT_STARTED: the opening takes a new player in a Carillon world. */
    static void maybeBegin(ServerPlayer p) {
        Prologue b = beat(p);
        if ((b != Prologue.NOT_STARTED && b != Prologue.GLITCHED) || !eligible(p)) return;
        if (OpeningModule.skipRule(p.level().getServer())) {
            DirectorLog.log(p, "opening", "the world skips the opening");
            skip(p);
            return;
        }
        begin(p);
    }

    /** The card, then the sky: the player waits on the Roost, held, while the camera flies. */
    static void begin(ServerPlayer p) {
        OpeningPlaces at = OpeningPlaces.get();
        if (at == null) return;
        OpeningState st = state(p);
        st.reset();
        MinecraftServer server = p.level().getServer();
        OpeningWorld w = OpeningWorld.get(server);
        // a new Carillon world opens at first light: the lamps going out, the spire catching the sun
        if (!w.dawnSet && OpeningPlaces.testLayout == null && Math.floorMod(server.overworld().getOverworldClockTime(), 24000L) < 4000) {
            // 26.3's world clocks: the vanilla command knows how to move the overworld's
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "time set 23200");
            w.dawnSet = true;
            w.setDirty();
        }
        OpeningStage.ensure(server.overworld(), at);
        placeOnRoost(p, at);
        Story.openingBeat(p, Prologue.SKY, now(p));
        DirectorLog.log(p, "opening", "begins: the card, then the sky");
        sendScene(p);
    }

    /** Friends start together: side by side along the Roost's terrace, all facing the spire. */
    private static void placeOnRoost(ServerPlayer p, OpeningPlaces at) {
        BlockPos start = at.roost();
        int k = 0;
        for (ServerPlayer o : p.level().getServer().getPlayerList().getPlayers()) {
            if (o == p) break;
            if (beat(o) == Prologue.SKY) k++;
        }
        double off = k == 0 ? 0 : ((k + 1) / 2) * 2.0 * (k % 2 == 0 ? -1 : 1);
        float yaw = at.roostYaw();
        // across the view: right of the spire is +off
        double rx = -Math.cos(Math.toRadians(yaw)), rz = -Math.sin(Math.toRadians(yaw));
        p.teleportTo(p.level(), start.getX() + 0.5 + rx * off, start.getY(), start.getZ() + 0.5 + rz * off, Set.<Relative>of(), yaw, 0F, true);
        p.resetFallDistance();
    }

    // ---- the tick ---------------------------------------------------------------------------------------------------

    static void tick(ServerPlayer p) {
        Prologue b = beat(p);
        if (!b.opening()) {
            if (b == Prologue.NOT_STARTED || b == Prologue.GLITCHED) {
                if (p.tickCount % 10 == 0) maybeBegin(p);
            }
            return;
        }
        if (!Story.on()) return;
        switch (b) {
            case SKY -> tickSky(p);
            case RUN -> tickRun(p);
            case ARRIVAL -> tickArrival(p);
            case WAKE -> tickWake(p);
            case REGISTRY -> OpeningRegistry.tick(p);
            default -> {}
        }
    }

    private static void tickSky(ServerPlayer p) {
        OpeningPlaces at = OpeningPlaces.get();
        if (at == null) return;
        long age = age(p);
        // held on the Roost: nothing moves the body while the camera flies (the client holds the keys as well)
        if (p.tickCount % 10 == 0 && p.position().distanceToSqr(Vec3.atBottomCenterOf(at.roost())) > 36) placeOnRoost(p, at);
        p.resetFallDistance();
        boolean client = AwakenNet.canReceive(p, OpeningPayloads.Scene.TYPE);
        if ((!client && age >= SKY_END) || age >= SKY_MAX) ringBell(p);
    }

    /** The client landed (or skipped): the bell rings wrong. */
    static void onReady(ServerPlayer p, OpeningPayloads.Ready r) {
        Prologue b = beat(p);
        switch (r.what()) {
            case OpeningPayloads.READY_SKY, OpeningPayloads.READY_SKY_SKIPPED -> {
                if (b != Prologue.SKY) return;
                state(p).skyViews++;
                ringBell(p);
            }
            case OpeningPayloads.READY_WAKE -> {
                if (b == Prologue.WAKE && age(p) >= 40) toRegistry(p);
            }
            case OpeningPayloads.READY_STEP -> OpeningRegistry.onReady(p, r.n());
            default -> {}
        }
    }

    // ---- the bell rings wrong, the run -------------------------------------------------------------------------------

    static void ringBell(ServerPlayer p) {
        OpeningPlaces at = OpeningPlaces.get();
        if (at == null) return;
        MinecraftServer server = p.level().getServer();
        OpeningState st = state(p);
        st.runStart = now(p);
        st.act = Math.max(st.act, 1);
        Story.openingBeat(p, Prologue.RUN, now(p));
        ensureGate(p, at);
        sendScene(p);
        AwakenNet.send(p, new OpeningPayloads.Cue(OpeningPayloads.CUE_BELL_WRONG, column(server, at), 1F, 0, ""));
        // the city hears it too: people in the streets stop and look toward the square
        CitizenHooks.post(server.overworld(), Awaken.id("bell_wrong"), at.square());
        dev.awaken.core.Later.run(server, LINE_AFTER, () -> {
            if (p.isRemoved() || beat(p) != Prologue.RUN) return;
            Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("[ERROR] … candidate found."))
                    .ttl(20 * 5).priority(9).build());
        });
        DirectorLog.log(p, "opening", "the bell rings wrong; the run begins");
    }

    /** The story's gate in the square, opened once for the group (it forms while they run). */
    static void ensureGate(ServerPlayer p, OpeningPlaces at) {
        MinecraftServer server = p.level().getServer();
        OpeningWorld w = OpeningWorld.get(server);
        if (gateUsable(server, w.gate)) return;
        var id = gates.open(p, at.square());
        w.gate = id.orElse("");
        w.setDirty();
        DirectorLog.log(p, "opening", id.isPresent() ? "the story's gate opens in Choir Square: " + id.get() : "no gates: the arrival will ask in place");
    }

    /** Still standing, and its ritual hasn't sealed anyone in yet. */
    static boolean gateUsable(MinecraftServer server, String gate) {
        return gates.usable(server, gate);
    }

    /** Where the light falls: the gate's own place once it stands, else the square's middle. */
    static Vec3 column(MinecraftServer server, OpeningPlaces at) {
        return gates.pos(server, OpeningWorld.get(server).gate).map(Vec3::atBottomCenterOf).orElse(Vec3.atBottomCenterOf(at.square()));
    }

    private static void tickRun(ServerPlayer p) {
        if (p.tickCount % 5 != 0) return;
        OpeningPlaces at = OpeningPlaces.get();
        if (at == null) return;
        MinecraftServer server = p.level().getServer();
        OpeningState st = state(p);
        OpeningWorld w = OpeningWorld.get(server);
        Vec3 pos = p.position();
        // the act: Bellwatch and the wall, the Quay and its island, the market
        int act = switch (at.district(pos)) {
            case "quay" -> 2;
            case "market", "terrace" -> 3;
            default -> 1;
        };
        if (pos.distanceTo(Vec3.atCenterOf(at.square())) < 60) act = 3;
        if (act > st.act) {
            st.act = act;
            DirectorLog.log(p, "opening", "act " + act);
        }
        // Act 2: the Bell Bridge goes into the river as you come to it, not on a timer (near enough to see it go from
        // each act-2 route; a runner who never comes that near is past it by the market)
        if (!w.bridgeDown && (st.act >= 3 || pos.distanceTo(at.bellBridge()) < BRIDGE_NEAR)) {
            // the gate pulses first: its shockwave crosses the Quay and takes the bridge as it arrives
            dev.awaken.core.Later.run(server, 22, () -> OpeningStage.dropBridge(server.overworld(), at));
            w.bridgeDown = true;
            w.setDirty();
            Vec3 mid = at.bellBridge();
            for (ServerPlayer o : runners(server)) AwakenNet.send(o, OpeningPayloads.Cue.of(OpeningPayloads.CUE_BRIDGE, mid));
            CitizenHooks.post(server.overworld(), Awaken.id("bell_bridge_down"), BlockPos.containing(mid));
            DirectorLog.log(p, "opening", "the Bell Bridge falls");
        }
        // Act 3: the market empties toward you while the Watch closes the square
        if (w.rushAt < 0 && (st.act >= 3 || pos.distanceTo(Vec3.atCenterOf(at.square())) < RUSH_NEAR)) {
            w.rushAt = now(p);
            w.setDirty();
            CitizenHooks.post(server.overworld(), Awaken.id("gate_rush"), at.square());
            for (ServerPlayer o : runners(server)) AwakenNet.send(o, OpeningPayloads.Cue.of(OpeningPayloads.CUE_RUSH, Vec3.atCenterOf(at.square())));
            DirectorLog.log(p, "opening", "the market runs");
        }
        // the gate pulls at the city: a tremor every so often, harder the closer the run comes
        long age = age(p);
        if (age > 200 && age % 340 < 5) {
            float strength = 0.35F + 0.2F * st.act;
            AwakenNet.send(p, new OpeningPayloads.Cue(OpeningPayloads.CUE_TREMOR, column(server, at), strength, 0, ""));
        }
        // idle: where it last moved
        long[] m = MOVED.computeIfAbsent(p.getUUID(), k -> new long[]{Double.doubleToLongBits(pos.x), Double.doubleToLongBits(pos.z), now(p)});
        double mx = Double.longBitsToDouble(m[0]), mz = Double.longBitsToDouble(m[1]);
        if ((pos.x - mx) * (pos.x - mx) + (pos.z - mz) * (pos.z - mz) > 4) {
            m[0] = Double.doubleToLongBits(pos.x);
            m[1] = Double.doubleToLongBits(pos.z);
            m[2] = now(p);
        }
        if (at.inSquare(pos, 2)) arrive(p, false);
    }

    /** Online players still running (the group's stragglers). */
    static List<ServerPlayer> runners(MinecraftServer server) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer o : server.getPlayerList().getPlayers()) if (beat(o) == Prologue.RUN) out.add(o);
        return out;
    }

    static List<ServerPlayer> arrived(MinecraftServer server) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer o : server.getPlayerList().getPlayers()) if (beat(o) == Prologue.ARRIVAL) out.add(o);
        return out;
    }

    // ---- arrival ----------------------------------------------------------------------------------------------------

    /** In Choir Square: hold here; the cinematic waits for the last runner (or plays at once, alone). */
    static void arrive(ServerPlayer p, boolean pulled) {
        OpeningPlaces at = OpeningPlaces.get();
        if (at == null) return;
        MinecraftServer server = p.level().getServer();
        OpeningState st = state(p);
        st.runEnd = now(p);
        st.pulled = pulled;
        st.act = 3;
        MOVED.remove(p.getUUID());
        if (pulled) {
            Vec3 c = Vec3.atBottomCenterOf(at.square()).add((p.getRandom().nextDouble() - 0.5) * 8, 0, 6 + p.getRandom().nextDouble() * 4);
            p.teleportTo(server.overworld(), c.x, c.y, c.z, Set.<Relative>of(), 180F, 0F, true);
            p.resetFallDistance();
            AwakenNet.send(p, OpeningPayloads.Cue.of(OpeningPayloads.CUE_PULLED, c));
        }
        OpeningWorld w = OpeningWorld.get(server);
        if (w.firstArrival < 0) {
            w.firstArrival = now(p);
            w.setDirty();
        }
        Story.openingBeat(p, Prologue.ARRIVAL, now(p));
        DirectorLog.log(p, "opening", (pulled ? "pulled to" : "reached") + " Choir Square in "
                + (st.runTicks() >= 0 ? st.runTicks() / 20 + " s" : "?") + "; " + runners(server).size() + " still running");
        dev.awaken.core.Playtest.log(p, "opening_run", "seconds", st.runTicks() / 20);
        ensureGate(p, at);
        settle(server);
        for (ServerPlayer o : arrived(server)) sendScene(o);
    }

    /** Once nobody is running, the group's cinematic is set (a breath alone, three seconds after the last arrival). */
    private static void settle(MinecraftServer server) {
        OpeningWorld w = OpeningWorld.get(server);
        if (w.cinematicAt >= 0 || !runners(server).isEmpty()) return;
        List<ServerPlayer> here = arrived(server);
        if (here.isEmpty()) return;
        w.cinematicAt = now(server) + (here.size() > 1 ? GROUP_DELAY : SOLO_DELAY);
        w.setDirty();
    }

    private static void tickArrival(ServerPlayer p) {
        OpeningPlaces at = OpeningPlaces.get();
        if (at == null) return;
        MinecraftServer server = p.level().getServer();
        OpeningWorld w = OpeningWorld.get(server);
        // inside the square, never out of it (you can wait for your friends, or race them: not leave)
        if (p.tickCount % 5 == 0 && !at.inSquare(p.position(), 0) && w.cinematicAt < 0) {
            Vec3 c = Vec3.atBottomCenterOf(at.square());
            Vec3 d = p.position().subtract(c).multiply(1, 0, 1);
            double r = at.squareRadius() - 3;
            Vec3 back = d.lengthSqr() < 1e-4 ? c : c.add(d.normalize().scale(Math.min(d.length(), r)));
            p.teleportTo(server.overworld(), back.x, Math.max(back.y, c.y), back.z, Set.<Relative>of(), p.getYRot(), p.getXRot(), false);
        }
        long now = now(p);
        // the light throws the square's people back (the second toll), once for the group
        if (w.cinematicAt >= 0 && now == w.cinematicAt + 60 && burstFor != w.cinematicAt) {
            burstFor = w.cinematicAt;
            CitizenHooks.post(server.overworld(), Awaken.id("gate_burst"), at.square());
        }
        if (w.cinematicAt >= 0 && now >= w.cinematicAt + CINEMATIC) {
            if ((now - w.cinematicAt - CINEMATIC) % 10 == 0) pull(p, at);
        } else if (w.cinematicAt >= 0 && now == w.cinematicAt) {
            sendScene(p);
        } else if (p.tickCount % 20 == 0 && w.cinematicAt < 0) {
            sendScene(p); // the count of hunters still running
        }
    }

    /** The group's clock, once a tick: stragglers pulled, the cinematic set when the last one is in. */
    static void serverTick(MinecraftServer server) {
        if ((server.getTickCount() + clockOffset) % 20 != 0 || OpeningPlaces.get() == null) return;
        OpeningWorld w = OpeningWorld.get(server);
        List<ServerPlayer> running = runners(server);
        List<ServerPlayer> here = arrived(server);
        if (here.isEmpty() && running.isEmpty()) {
            // the group went through (or left): the next one starts afresh, with its own crowd and its own clock
            if (w.firstArrival >= 0 || w.cinematicAt >= 0 || w.rushAt >= 0) w.newGroup();
            return;
        }
        if (w.firstArrival < 0) return;
        long now = now(server);
        for (ServerPlayer r : running) {
            long[] m = MOVED.get(r.getUUID());
            boolean idle = m != null && now - m[2] >= IDLE;
            if (now - w.firstArrival >= STRAGGLERS || idle && !here.isEmpty()) {
                DirectorLog.log(r, "opening", idle ? "idle for two minutes: the light reaches for you" : "five minutes since the first arrival: the light reaches for you");
                arrive(r, true);
            }
        }
        settle(server);
    }

    /** The cinematic's end: the gate takes you. The black holds until the story's gate has formed. */
    private static void pull(ServerPlayer p, OpeningPlaces at) {
        MinecraftServer server = p.level().getServer();
        OpeningWorld w = OpeningWorld.get(server);
        long since = PULLING.computeIfAbsent(p.getUUID(), k -> now(p));
        if (!gateUsable(server, w.gate)) ensureGate(p, at);
        OpeningState st = state(p);
        if (!w.gate.isEmpty()) {
            long arrivedAt = Story.prologueSince(p);
            Story.callToGate(p, w.gate); // the ritual lets in only those its bell rings for
            st.arrived = true;
            if (gates.enter(p, w.gate)) {
                PULLING.remove(p.getUUID());
                DirectorLog.log(p, "opening", "the gate takes you: " + w.gate);
                return;
            }
            // not formed yet: back to waiting at the threshold (the client holds the black)
            st.arrived = false;
            Story.openingBeat(p, Prologue.ARRIVAL, arrivedAt);
        }
        if (now(p) - since > GATE_PATIENCE || w.gate.isEmpty()) {
            // no gate will come (gates off): the Void finds you here, as the old prologue did
            PULLING.remove(p.getUUID());
            st.arrived = true;
            OpeningModule.acceptInPlace(p);
        }
    }

    // ---- after the Void: the infirmary ------------------------------------------------------------------------------

    private static void tickWake(ServerPlayer p) {
        OpeningPlaces at = OpeningPlaces.get();
        if (at == null) {
            finish(p);
            return;
        }
        OpeningState st = state(p);
        if (st.bed < 0 || p.level().dimension() != Level.OVERWORLD) {
            putToBed(p, at);
            return;
        }
        long age = age(p);
        boolean client = AwakenNet.canReceive(p, OpeningPayloads.Scene.TYPE);
        if ((!client && age >= WAKE_END) || age >= WAKE_MAX) toRegistry(p);
    }

    /** From the black to the infirmary: a bed beside your friends'. */
    static void putToBed(ServerPlayer p, OpeningPlaces at) {
        MinecraftServer server = p.level().getServer();
        OpeningState st = state(p);
        if (st.bed < 0) {
            boolean[] used = new boolean[8];
            for (ServerPlayer o : server.getPlayerList().getPlayers()) {
                if (o == p) continue;
                Prologue b = beat(o);
                int ob = state(o).bed;
                if ((b == Prologue.WAKE || b == Prologue.REGISTRY) && ob >= 0 && ob < 8) used[ob] = true;
            }
            int k = 0;
            while (k < 7 && used[k]) k++;
            st.bed = k;
        }
        OpeningStage.ensure(server.overworld(), at);
        Vec3 side = at.bedside(st.bed);
        float yaw = -90F; // facing east (Minecraft's yaw: -90 looks along +x), toward the doorway and the Registry beyond
        p.teleportTo(server.overworld(), side.x, side.y, side.z, Set.<Relative>of(), yaw, 0F, true);
        p.resetFallDistance();
        p.removeEffect(MobEffects.BLINDNESS);
        p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 20, 4, false, false));
        Story.openingBeat(p, Prologue.WAKE, now(p));
        // the Void's scene is over for the ritual's client (first: its end clears the ritual's look), then our own
        // black (closed eyes) takes the frame
        AwakenNet.send(p, StoryPayloads.RitualScene.of(StoryPayloads.RitualScene.NONE));
        sendScene(p);
        OpeningModule.infirmaryCast(server, at, st.bed, true);
        DirectorLog.log(p, "opening", "wakes in the infirmary, bed " + st.bed);
    }

    static void toRegistry(ServerPlayer p) {
        OpeningState st = state(p);
        st.step = 0;
        Story.openingBeat(p, Prologue.REGISTRY, now(p));
        sendScene(p);
        OpeningPlaces at = OpeningPlaces.get();
        if (at != null) OpeningModule.infirmaryCast(p.level().getServer(), at, st.bed, false);
        DirectorLog.log(p, "opening", "up: off to the Registry");
    }

    // ---- the end ----------------------------------------------------------------------------------------------------

    /** "The city is yours." The opening is over: free play. */
    static void finish(ServerPlayer p) {
        Story.openingBeat(p, Prologue.DONE, now(p));
        AwakenNet.send(p, OpeningPayloads.Scene.none());
        OpeningModule.afterOpening(p);
        DirectorLog.log(p, "opening", "done: the city is yours");
    }

    /**
     * Skips the whole opening (the world option, {@code /awaken opening skip}, OpeningHooks.skip): awakened and
     * registered as the opening would leave you, on the Roost with your licence, the Holo and your first power to choose.
     */
    static void skip(ServerPlayer p) {
        Prologue b = beat(p);
        if (b == Prologue.DONE && Progression.profile(p).awakened()) return;
        OpeningState st = state(p);
        boolean placed = b.opening() || b == Prologue.NOT_STARTED || b == Prologue.GLITCHED;
        Story.skipPrologue(p); // awakened, DONE, out of a ritual hall if inside one
        st.arrived = false;
        PULLING.remove(p.getUUID());
        MOVED.remove(p.getUUID());
        OpeningPlaces at = OpeningPlaces.get();
        if (at != null && placed && p.level().dimension() == Level.OVERWORLD && (b == Prologue.SKY || b == Prologue.NOT_STARTED || b == Prologue.GLITCHED)) {
            BlockPos r = at.roost();
            p.teleportTo(p.level().getServer().overworld(), r.getX() + 0.5, r.getY(), r.getZ() + 0.5, Set.<Relative>of(), at.roostYaw(), 0F, true);
        }
        OpeningRegistry.giveLicence(p);
        AwakenNet.send(p, OpeningPayloads.Scene.none());
        OpeningModule.afterOpening(p);
        DirectorLog.log(p, "opening", "skipped from " + b);
    }

    // ---- the scene, as this client should see it now ------------------------------------------------------------------

    static void sendScene(ServerPlayer p) {
        OpeningPlaces at = OpeningPlaces.get();
        if (at == null) return;
        MinecraftServer server = p.level().getServer();
        OpeningState st = state(p);
        OpeningWorld w = OpeningWorld.get(server);
        Prologue b = beat(p);
        int age = (int) Math.min(Integer.MAX_VALUE, Math.max(0, age(p)));
        Vec3 origin = at.origin();
        int flags = (w.bridgeDown ? OpeningPayloads.F_BRIDGE_DOWN : 0) | (st.skyViews > 0 ? OpeningPayloads.F_SEEN : 0)
                | (OpeningModule.tourShots ? OpeningPayloads.F_SHOTS : 0) | (OpeningModule.noQuestions ? OpeningPayloads.F_NO_QUESTIONS : 0);
        OpeningPayloads.Scene s = switch (b) {
            case SKY -> new OpeningPayloads.Scene(OpeningPayloads.SKY, age, origin, p.getEyePosition(), p.getYRot(), column(server, at), 0, flags, "");
            case RUN -> new OpeningPayloads.Scene(OpeningPayloads.RUN, age, origin, p.getEyePosition(), p.getYRot(), column(server, at), st.act, flags, "");
            case ARRIVAL -> {
                long now = now(p);
                boolean playing = w.cinematicAt >= 0 && now >= w.cinematicAt;
                int n = playing ? (int) (now - w.cinematicAt) : runners(server).size();
                yield new OpeningPayloads.Scene(OpeningPayloads.ARRIVAL, age, origin, p.getEyePosition(), p.getYRot(), column(server, at), n,
                        flags | (playing ? OpeningPayloads.F_CINEMATIC : 0), "");
            }
            case WAKE -> {
                BlockPos head = at.bedHead(Math.max(0, st.bed));
                Vec3 pillow = new Vec3(head.getX() + 0.5, head.getY() + 0.62, head.getZ() + 0.5);
                yield new OpeningPayloads.Scene(OpeningPayloads.WAKE, age, origin, pillow, p.getYRot(), at.bedside(Math.max(0, st.bed)), st.bed, flags, "");
            }
            case REGISTRY -> new OpeningPayloads.Scene(OpeningPayloads.REGISTRY, age, origin, at.lensMark(), 180F, at.lens(), st.step, flags, "");
            default -> OpeningPayloads.Scene.none();
        };
        AwakenNet.send(p, s);
    }

    /** On login: the scene where it stands (the sky mid-flight, the wait, the bed, the Registry), or the beginning. */
    static void onLogin(ServerPlayer p) {
        MinecraftServer server = p.level().getServer();
        // the Bell Bridge stays in the river: the citizens' routes keep off it after a restart too
        if (!bridgeTold && OpeningPlaces.get() != null && OpeningWorld.get(server).bridgeDown) {
            bridgeTold = true;
            CitizenHooks.post(server.overworld(), Awaken.id("bell_bridge_down"), BlockPos.ZERO);
        }
        Prologue b = beat(p);
        if (b == Prologue.NOT_STARTED || b == Prologue.GLITCHED) {
            maybeBegin(p);
            return;
        }
        if (!b.opening()) return;
        if (!Story.on() || OpeningPlaces.get() == null) {
            // the city is gone (a classic world loaded with an opening save): nothing to resume, free the player
            finish(p);
            return;
        }
        if (b == Prologue.ARRIVAL) settle(p.level().getServer());
        sendScene(p);
    }

    static void onLogout(ServerPlayer p) {
        MOVED.remove(p.getUUID());
        PULLING.remove(p.getUUID());
        // a runner who leaves isn't waited for; the group may be complete now
        MinecraftServer server = p.level().getServer();
        dev.awaken.core.Later.run(server, 1, () -> settle(server));
    }
}
