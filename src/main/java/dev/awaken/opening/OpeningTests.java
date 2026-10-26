package dev.awaken.opening;

import dev.awaken.city.CityLayout;
import dev.awaken.city.CityPlan;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.api.OpeningHooks;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.opening.net.OpeningPayloads;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.story.Story;
import dev.awaken.story.StoryProgress.Prologue;
import dev.awaken.terrain.api.CityBasin;
import dev.awaken.terrain.api.CitySite;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * GameTests for the opening (prefix {@code opening_}, one per mechanism): the state machine from the card to the city
 * being yours (with a relog at every beat), the party's wait and the stragglers, no death in the opening, and every
 * way to skip it. A city is put round the test, the stage writes nothing, the story's gate is a stand-in and the
 * opening's clock is moved forward instead of waited for. Each body runs inside one tick (like the Ritual's), so
 * nothing else runs in between; everything it touched is put back.
 */
final class OpeningTests {
    private OpeningTests() {}

    /** The story's gate as the opening uses it, in the square: opens "1", is usable, and records who went through. */
    private static final class FakeGate implements OpeningDirector.Gate {
        final List<UUID> entered = new ArrayList<>();
        private final BlockPos at;

        FakeGate(BlockPos at) {
            this.at = at;
        }

        @Override public Optional<String> open(ServerPlayer p, BlockPos near) {
            return Optional.of("1");
        }

        @Override public boolean usable(MinecraftServer server, String id) {
            return id.equals("1");
        }

        @Override public Optional<BlockPos> pos(MinecraftServer server, String id) {
            return Optional.of(at);
        }

        @Override public boolean enter(ServerPlayer p, String id) {
            entered.add(p.getUUID());
            return true;
        }
    }

    private record Rig(GameTestHelper h, MinecraftServer server, ServerLevel level, OpeningPlaces at, OpeningWorld world,
                       FakeGate gate, List<ServerPlayer> players) {}

    /** A survival player that has loaded (it takes damage), made before the test city exists so login doesn't begin the opening. */
    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.connection.markClientLoaded();
        return p;
    }

    /** Carillon centred on the test, its streets at the test floor: the Roost, the square and the Association are near. */
    private static CityLayout layoutAround(GameTestHelper h) {
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        List<int[]> river = new ArrayList<>();
        for (int[] p : CityBasin.RIVER) river.add(new int[]{o.getX() + p[0], o.getZ() + p[1]});
        return new CityLayout(CityPlan.get(), new CitySite(o.getX(), o.getZ(), CityBasin.CORE_RADIUS, CityBasin.BLEND_RADIUS, o.getY(), river,
                CityBasin.RIVER_HALF_WIDTH, CityBasin.RIVER_FLOOR_Y, true));
    }

    /** Runs a body with {@code n} fresh players in a test city, then puts back everything the opening touched. */
    private static void with(GameTestHelper h, int n, Consumer<Rig> body) {
        ServerLevel level = h.getLevel();
        MinecraftServer server = level.getServer();
        OpeningWorld w = OpeningWorld.get(server);
        String gate = w.gate;
        long first = w.firstArrival, cinematic = w.cinematicAt, rush = w.rushAt;
        boolean bridge = w.bridgeDown, dawn = w.dawnSet;
        int stage = w.stage;
        boolean skipRule = server.overworld().getGameRules().get(OpeningModule.SKIP.get());
        List<ServerPlayer> players = new ArrayList<>();
        try {
            for (int i = 0; i < n; i++) players.add(player(h));
            w.gate = "";
            w.firstArrival = w.cinematicAt = w.rushAt = -1;
            w.bridgeDown = false;
            OpeningStage.enabled = false; // the level is shared: the stage would write over other tests' ground
            OpeningDirector.clockOffset = 0;
            OpeningDirector.forget();
            OpeningRegistry.forget();
            OpeningPlaces.testLayout = layoutAround(h);
            OpeningPlaces at = OpeningPlaces.get();
            FakeGate fake = new FakeGate(at.square());
            OpeningDirector.gates = fake;
            body.accept(new Rig(h, server, level, at, w, fake, players));
        } finally {
            for (ServerPlayer p : players) server.getPlayerList().remove(p);
            OpeningPlaces.testLayout = null;
            OpeningStage.enabled = true;
            OpeningDirector.gates = OpeningDirector.REAL_GATE;
            OpeningDirector.clockOffset = 0;
            OpeningDirector.forget();
            OpeningRegistry.forget();
            w.gate = gate;
            w.firstArrival = first;
            w.cinematicAt = cinematic;
            w.rushAt = rush;
            w.bridgeDown = bridge;
            w.stage = stage;
            w.dawnSet = dawn;
            w.setDirty();
            server.overworld().getGameRules().set(OpeningModule.SKIP.get(), skipRule, server);
        }
    }

    // ---- small helpers ------------------------------------------------------------------------------------------------

    /** One player tick of the opening's (some checks run on every 5th tick: this is one). */
    private static void tick(ServerPlayer p) {
        p.tickCount = 5;
        OpeningDirector.tick(p);
    }

    private static void teleport(Rig r, ServerPlayer p, Vec3 to) {
        p.teleportTo(r.level(), to.x, to.y, to.z, Set.<Relative>of(), 0F, 0F, true);
    }

    /** The card and the sky, then the bell: a runner. */
    private static void run(Rig r, ServerPlayer p) {
        OpeningDirector.begin(p);
        OpeningDirector.ringBell(p);
        r.h().assertTrue(Story.prologue(p) == Prologue.RUN, "running, got " + Story.prologue(p));
    }

    /** Into Choir Square and one tick of the run: the arrival. */
    private static void toSquare(Rig r, ServerPlayer p) {
        teleport(r, p, Vec3.atBottomCenterOf(r.at().square()));
        r.h().assertTrue(r.at().inSquare(p.position(), 2), "the player stands in the square: " + p.position());
        tick(p);
    }

    /** The group's clock on a tick where it fires (every 20th of the opening's clock). */
    private static void serverTick(Rig r) {
        long t = r.server().getTickCount() + OpeningDirector.clockOffset;
        OpeningDirector.clockOffset += Math.floorMod(-t, 20L);
        OpeningDirector.serverTick(r.server());
    }

    /** A relog leaves the beat, when it began and the opening's own progress exactly as they were. */
    private static void relog(GameTestHelper h, ServerPlayer p, Prologue beat) {
        OpeningState st = OpeningDirector.state(p);
        long since = Story.prologueSince(p), runStart = st.runStart;
        int step = st.step, bed = st.bed;
        boolean arrived = st.arrived;
        OpeningDirector.onLogout(p);
        OpeningDirector.onLogin(p);
        h.assertTrue(Story.prologue(p) == beat, "a relog leaves the beat at " + beat + ", got " + Story.prologue(p));
        h.assertTrue(Story.prologueSince(p) == since, "and its start time: " + since + " became " + Story.prologueSince(p));
        h.assertTrue(st.runStart == runStart && st.step == step && st.bed == bed && st.arrived == arrived,
                "and the opening's progress at " + beat);
    }

    private static int licences(ServerPlayer p) {
        int n = 0;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.is(OpeningModule.LICENCE.get()) && !s.is(Items.PAPER)) continue; // paper: licences from before the desk
            CustomData d = s.get(DataComponents.CUSTOM_DATA);
            if (d != null && d.copyTag().getStringOr("awaken", "").equals("licence")) n += s.getCount();
        }
        return n;
    }

    /** Over: awakened, the city's theirs, one licence, nothing of the opening left on them. */
    private static void assertSkipped(GameTestHelper h, ServerPlayer p, String how) {
        h.assertTrue(Progression.profile(p).awakened(), how + ": awakened");
        h.assertTrue(Story.prologue(p) == Prologue.DONE, how + ": DONE, got " + Story.prologue(p));
        h.assertTrue(licences(p) == 1, how + ": the licence is in the inventory once, got " + licences(p));
        h.assertFalse(OpeningHooks.inOpening(p), how + ": not in the opening");
        // the Registry ceremony (SPEC §16.4): the band on the wrist, none left over in the inventory
        h.assertTrue(dev.awaken.core.api.HoloBand.worn(p) && dev.awaken.core.api.HoloBand.fitted(p), how + ": the Holo band worn");
        h.assertFalse(p.getInventory().hasAnyMatching(dev.awaken.holo.band.BandModule::isBand), how + ": no spare band in the inventory");
    }

    static void register() {
        // Every beat, in order, with a relog at each: the card and the sky, the bell, the run, the arrival, the pull into
        // the story's gate, the Void and the Accept, the infirmary, the Registry's steps, and the city is yours.
        AwakenTests.register("opening_state_machine", 100, h -> {
            with(h, 1, r -> {
                ServerPlayer p = r.players().getFirst();
                OpeningPlaces at = r.at();
                OpeningWorld w = r.world();
                OpeningState st = OpeningDirector.state(p);
                h.assertFalse(AwakenNet.canReceive(p, OpeningPayloads.Scene.TYPE), "a mock player has no client: the opening runs its own clock");
                h.assertFalse(Progression.profile(p).awakened(), "a fresh player isn't awakened");

                // the card, then the sky: on the Roost
                OpeningDirector.maybeBegin(p);
                h.assertTrue(Story.prologue(p) == Prologue.SKY, "the opening takes a new player: " + Story.prologue(p));
                h.assertTrue(p.position().distanceTo(Vec3.atBottomCenterOf(at.roost())) < 1.5, "on the Roost: " + p.position() + " vs " + at.roost());
                h.assertTrue(Story.prologueSince(p) == OpeningDirector.now(p), "the sky's clock starts now");
                h.assertTrue(OpeningHooks.inOpening(p) && "sky".equals(OpeningHooks.stage(p)), "in the opening, at the sky");
                relog(h, p, Prologue.SKY);

                // the sky ends on the clock (no client to say it landed): one tick short, then on the tick
                OpeningDirector.clockOffset += OpeningDirector.SKY_END - 1;
                tick(p);
                h.assertTrue(Story.prologue(p) == Prologue.SKY, "the sky is still flying one tick before its end");
                OpeningDirector.clockOffset += 1;
                tick(p);
                h.assertTrue(Story.prologue(p) == Prologue.RUN, "the bell rings wrong: " + Story.prologue(p));
                h.assertTrue(w.gate.equals("1"), "the story's gate opened in the square for the group: '" + w.gate + "'");
                h.assertTrue(st.runStart == OpeningDirector.now(p) && st.act == 1, "the run's clock started, act " + st.act);
                relog(h, p, Prologue.RUN);

                // still running at the Roost; into the square, the run is over
                tick(p);
                h.assertTrue(Story.prologue(p) == Prologue.RUN, "not in the square yet: still running");
                toSquare(r, p);
                h.assertTrue(Story.prologue(p) == Prologue.ARRIVAL, "in Choir Square: the arrival, got " + Story.prologue(p));
                h.assertTrue(st.runEnd >= st.runStart && st.act == 3 && !st.pulled, "the run's time is kept and nobody pulled you");
                h.assertTrue(w.bridgeDown && w.rushAt >= 0, "the Bell Bridge fell and the market ran on the way");
                long now = OpeningDirector.now(p);
                h.assertTrue(w.firstArrival == now, "the group's first arrival is now");
                h.assertTrue(w.cinematicAt == now + OpeningDirector.SOLO_DELAY, "alone: the cinematic follows a breath later, " + w.cinematicAt + " vs " + now);
                relog(h, p, Prologue.ARRIVAL);

                // the cinematic plays; one tick before its end nothing pulls, then the gate takes you
                OpeningDirector.clockOffset += w.cinematicAt + OpeningDirector.CINEMATIC - 1 - OpeningDirector.now(p);
                tick(p);
                h.assertTrue(Story.prologue(p) == Prologue.ARRIVAL, "the cinematic is still playing one tick before its end");
                OpeningDirector.clockOffset += 1;
                tick(p);
                h.assertTrue(Story.prologue(p) == Prologue.GATE_OPEN && Story.prologueGate(p).equals("1"), "the story's gate takes you: " + Story.prologue(p));
                h.assertTrue(st.arrived, "arrived: the Ritual and the Void are still the opening's");
                h.assertTrue(r.gate().entered.contains(p.getUUID()), "through the gate");
                h.assertTrue(OpeningHooks.inOpening(p) && "ritual".equals(OpeningHooks.stage(p)), "in the opening, at the ritual");
                relog(h, p, Prologue.GATE_OPEN);

                // the Void and the Accept
                Story.acceptInPlace(p);
                h.assertTrue(Story.prologue(p) == Prologue.ACCEPTING, "the question: " + Story.prologue(p));
                h.assertTrue(OpeningHooks.inOpening(p) && "void".equals(OpeningHooks.stage(p)), "the Void is the opening's");
                h.assertFalse(Progression.profile(p).awakened(), "not awakened until the answer");
                Signal.answer(p, "story:accept_only", true);
                h.assertTrue(Progression.profile(p).awakened(), "awakened by the answer");
                h.assertTrue(Story.prologue(p) == Prologue.WAKE, "you wake in the opening's infirmary: " + Story.prologue(p));
                h.assertTrue(OpeningHooks.inOpening(p) && "wake".equals(OpeningHooks.stage(p)), "still in the opening, awakened");

                // the next tick puts you to bed
                h.assertTrue(st.bed < 0, "no bed yet");
                tick(p);
                h.assertTrue(st.bed == 0, "bed 0, got " + st.bed);
                h.assertTrue(Story.prologue(p) == Prologue.WAKE && Story.prologueSince(p) == OpeningDirector.now(p), "the infirmary's clock starts now");
                h.assertTrue(p.position().distanceTo(at.bedside(0)) < 1.5, "at the bedside: " + p.position() + " vs " + at.bedside(0));
                relog(h, p, Prologue.WAKE);

                // eyes open on the clock: one tick short, then to the Registry
                OpeningDirector.clockOffset += OpeningDirector.WAKE_END - 1;
                tick(p);
                h.assertTrue(Story.prologue(p) == Prologue.WAKE, "still in bed one tick before the wake ends");
                OpeningDirector.clockOffset += 1;
                tick(p);
                h.assertTrue(Story.prologue(p) == Prologue.REGISTRY && st.step == OpeningRegistry.WALK, "up and off to the Registry: " + Story.prologue(p));
                relog(h, p, Prologue.REGISTRY);

                // the steps run out on the clock, one tick at a time (the licence is handed over on one exact tick of its step)
                List<Integer> steps = new ArrayList<>();
                for (int i = 0; i < 2000 && Story.prologue(p) == Prologue.REGISTRY; i++) {
                    if (steps.isEmpty() || steps.getLast() != st.step) steps.add(st.step);
                    OpeningDirector.clockOffset += 1;
                    tick(p);
                }
                h.assertTrue(steps.equals(List.of(OpeningRegistry.WALK, OpeningRegistry.READ, OpeningRegistry.SIGN, OpeningRegistry.BAND,
                        OpeningRegistry.WEAR, OpeningRegistry.BOOT, OpeningRegistry.BRIEF, OpeningRegistry.OPEN, OpeningRegistry.ATLAS,
                        OpeningRegistry.POWER, OpeningRegistry.DIRECTIVE, OpeningRegistry.YOURS)), "every step in order: " + steps);
                h.assertTrue(dev.awaken.core.api.HoloBand.worn(p), "the band on the wrist when it's over");
                h.assertTrue(Story.prologue(p) == Prologue.DONE, "the city is yours: " + Story.prologue(p));
                assertSkipped(h, p, "after the Registry");
                h.assertTrue(OpeningHooks.stage(p) == null, "no stage once it's over");
                // a relog now changes nothing: the opening is over
                OpeningDirector.onLogout(p);
                OpeningDirector.onLogin(p);
                h.assertTrue(Story.prologue(p) == Prologue.DONE && !OpeningHooks.inOpening(p), "a relog after the opening doesn't begin it again");
            });
            h.succeed();
        });

        // A party: the first to arrive waits for the others; the last in sets the cinematic three seconds on. Then the
        // stragglers: five minutes after the first arrival the light reaches for whoever still runs, and for an idle one.
        AwakenTests.register("opening_party_wait", 100, h -> {
            with(h, 4, r -> {
                OpeningWorld w = r.world();
                MinecraftServer server = r.server();
                ServerPlayer a = r.players().get(0), b = r.players().get(1), c = r.players().get(2), d = r.players().get(3);
                // the clock is game time: early in a run (or a short one) "five minutes before now" fell below zero, which
                // reads as no group at all, and the straggler was never pulled
                OpeningDirector.clockOffset += OpeningDirector.STRAGGLERS;

                // both run; the first arrives and waits
                run(r, a);
                run(r, b);
                h.assertTrue(OpeningDirector.runners(server).size() == 2, "two runners");
                toSquare(r, a);
                h.assertTrue(Story.prologue(a) == Prologue.ARRIVAL && Story.prologue(b) == Prologue.RUN, "the first is in, the second still runs");
                h.assertTrue(w.cinematicAt < 0, "the cinematic waits for the last runner");
                h.assertTrue(w.firstArrival == OpeningDirector.now(a), "the group's clock started with the first arrival");
                h.assertTrue(OpeningDirector.runners(server).size() == 1, "one runner left");
                long first = w.firstArrival;
                OpeningDirector.clockOffset += 20 * 30;
                serverTick(r);
                h.assertTrue(Story.prologue(b) == Prologue.RUN && w.cinematicAt < 0, "half a minute on, still waiting for the second");
                // the second arrives: three seconds later the cinematic plays for both
                toSquare(r, b);
                h.assertTrue(Story.prologue(b) == Prologue.ARRIVAL && OpeningDirector.arrived(server).size() == 2, "both are in");
                h.assertTrue(w.cinematicAt == OpeningDirector.now(server) + OpeningDirector.GROUP_DELAY,
                        "the cinematic is set GROUP_DELAY after the last arrival: " + w.cinematicAt + " vs " + OpeningDirector.now(server));
                h.assertTrue(w.firstArrival == first, "the first arrival is still the first");

                // the group goes through (or leaves): the next one starts afresh
                Story.openingBeat(a, Prologue.DONE, OpeningDirector.now(a));
                Story.openingBeat(b, Prologue.DONE, OpeningDirector.now(b));
                serverTick(r);
                h.assertTrue(w.firstArrival < 0 && w.cinematicAt < 0 && w.rushAt < 0, "a finished group's clock is cleared: " + w.firstArrival + " " + w.cinematicAt + " " + w.rushAt);

                // stragglers: one arrives, the other keeps running
                run(r, c);
                run(r, d);
                toSquare(r, c);
                tick(d);
                h.assertTrue(Story.prologue(c) == Prologue.ARRIVAL && Story.prologue(d) == Prologue.RUN, "one in, one running");
                h.assertTrue(w.firstArrival >= 0 && w.cinematicAt < 0, "waiting for the straggler");
                serverTick(r);
                long clock = OpeningDirector.now(server);
                w.firstArrival = clock - OpeningDirector.STRAGGLERS + 1;
                serverTick(r);
                h.assertTrue(Story.prologue(d) == Prologue.RUN, "one tick short of five minutes: not yet");
                w.firstArrival = clock - OpeningDirector.STRAGGLERS;
                serverTick(r);
                h.assertTrue(Story.prologue(d) == Prologue.ARRIVAL, "five minutes after the first arrival the light reaches for the runner: " + Story.prologue(d));
                h.assertTrue(OpeningDirector.state(d).pulled && !OpeningDirector.state(c).pulled, "pulled, not run there: the other ran");
                h.assertTrue(r.at().inSquare(d.position(), 0), "and stands in the square: " + d.position());
                h.assertTrue(w.cinematicAt == OpeningDirector.now(server) + OpeningDirector.GROUP_DELAY, "now nobody runs: the cinematic is set");

                // idle: two minutes without moving, with a friend already waiting, is enough
                w.newGroup();
                run(r, c);
                run(r, d);
                toSquare(r, c);
                tick(d);
                long[] moved = OpeningDirector.MOVED.get(d.getUUID());
                h.assertTrue(moved != null, "a runner's last move is tracked");
                serverTick(r);
                moved[2] = OpeningDirector.now(server) - OpeningDirector.IDLE + 1;
                serverTick(r);
                h.assertTrue(Story.prologue(d) == Prologue.RUN, "one tick short of two idle minutes: not yet");
                moved[2] = OpeningDirector.now(server) - OpeningDirector.IDLE;
                serverTick(r);
                h.assertTrue(Story.prologue(d) == Prologue.ARRIVAL && OpeningDirector.state(d).pulled, "two idle minutes: pulled to the square");
            });
            h.succeed();
        });

        // No death in the opening: a fall that would kill leaves a heart; the same fall after it hurts as it should.
        AwakenTests.register("opening_no_death", 100, h -> {
            with(h, 1, r -> {
                ServerPlayer p = r.players().getFirst();
                ServerLevel level = r.level();
                run(r, p);
                p.setHealth(p.getMaxHealth());
                p.hurtServer(level, level.damageSources().fall(), 100F);
                h.assertTrue(p.isAlive() && p.getHealth() == 2F, "a 100-damage fall in the run leaves one heart: " + p.getHealth());
                p.damageCooldownTime = 0;
                p.hurtServer(level, level.damageSources().fall(), 100F);
                h.assertTrue(p.isAlive() && p.getHealth() == 2F, "and the next one still does: " + p.getHealth());

                Story.openingBeat(p, Prologue.DONE, OpeningDirector.now(p));
                h.assertFalse(OpeningHooks.inOpening(p), "the opening is over");
                p.setHealth(p.getMaxHealth());
                p.damageCooldownTime = 0;
                p.hurtServer(level, level.damageSources().fall(), 5F);
                h.assertTrue(p.isAlive() && p.getHealth() == p.getMaxHealth() - 5F, "after the opening 5 damage is 5 damage: " + p.getHealth());
            });
            h.succeed();
        });

        // Every way out: OpeningHooks.skip mid-run, the world option, and /awaken opening skip (a player's own command).
        AwakenTests.register("opening_skip", 100, h -> {
            with(h, 3, r -> {
                MinecraftServer server = r.server();
                ServerPlayer runner = r.players().get(0), fresh = r.players().get(1), commander = r.players().get(2);

                // (a) mid-run, by the hook; a Runner has no band yet (the Holo is dark), and one handed over early is the
                // one that goes on
                run(r, runner);
                h.assertFalse(dev.awaken.core.api.HoloBand.worn(runner), "a Runner's Holo is dark until the Registry");
                dev.awaken.core.api.HoloBand.give(runner);
                OpeningHooks.skip(runner);
                assertSkipped(h, runner, "skipped mid-run");
                OpeningHooks.skip(runner);
                h.assertTrue(licences(runner) == 1, "skipping again gives no second licence");

                // (b) the world option: a new player is awakened on the Roost at once
                var rules = server.overworld().getGameRules();
                rules.set(OpeningModule.SKIP.get(), true, server);
                try {
                    OpeningDirector.maybeBegin(fresh);
                } finally {
                    rules.set(OpeningModule.SKIP.get(), false, server);
                }
                assertSkipped(h, fresh, "the world option");
                h.assertTrue(fresh.position().distanceTo(Vec3.atBottomCenterOf(r.at().roost())) < 1.5, "on the Roost: " + fresh.position());

                // (c) the command, as the player's own (no permission needed to skip your own opening)
                h.assertTrue(Story.prologue(commander) != Prologue.DONE, "the third player hasn't been through it");
                CommandSourceStack src = commander.createCommandSourceStack().withSuppressedOutput();
                server.getCommands().performPrefixedCommand(src, "awaken opening skip");
                assertSkipped(h, commander, "/awaken opening skip");
            });
            h.succeed();
        });
    }
}
