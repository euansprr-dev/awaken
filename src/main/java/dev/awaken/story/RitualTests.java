package dev.awaken.story;

import dev.awaken.core.api.GateHooks;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.GateLiveTests;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateRegistry;
import dev.awaken.gates.GateService;
import dev.awaken.gates.GateState;
import dev.awaken.gates.Gates;
import dev.awaken.gates.RitualHall;
import dev.awaken.gates.run.Runs;
import dev.awaken.mobs.entity.RitualStatue;
import dev.awaken.mobs.entity.SeatedOne;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * GameTests for the Ritual of the Bell (story/Ritual): the wait for everyone with its timeout and a disconnect, every
 * beat in order, the rules, nothing kills before the hand closes, a relog and a restart resume the beat, old saves read.
 * The ritual runs against the real gates module in the test level (standing in for the gate world), with the director's
 * clock moved forward instead of waited for, all inside one tick so nothing else runs in between.
 */
final class RitualTests {
    private RitualTests() {}

    /** A ritual Bell Crypt painted into the test level and the players whose story points at it. */
    private record Rig(MinecraftServer server, ServerLevel level, GateRecord gate, RitualHall hall, List<ServerPlayer> players) {}

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.connection.markClientLoaded();
        Prologue.progress(p).prologue = StoryProgress.Prologue.GLITCHED;
        // mock players appear at 0,0,0, which is inside gate cell 0 while the test level stands in for the gate world
        Vec3 home = h.absoluteVec(new Vec3(1.5, 2, 1.5));
        p.teleportTo(h.getLevel(), home.x, home.y, home.z, Set.<Relative>of(), 0, 0, true);
        return p;
    }

    /** Runs a test body with a ritual gate of {@code n} players (none entered yet), cleaning up whatever happens. */
    private static void withRitual(GameTestHelper h, int n, Consumer<Rig> body) {
        ServerLevel level = h.getLevel();
        MinecraftServer server = level.getServer();
        GateService.useTestLevel(level);
        Ritual.clockOffset = 0;
        Ritual.castAnywhere = true;
        GateRecord g = null;
        List<ServerPlayer> players = new ArrayList<>();
        try {
            for (int i = 0; i < n; i++) players.add(player(h));
            ServerPlayer opener = players.getFirst();
            // opened directly (not through openFirstGate) so other tests' mock players are never pulled in
            var id = GateHooks.openScripted(opener, "awaken:bell_crypt", 0, opener.blockPosition().offset(40, 0, 0), 1);
            h.assertTrue(id.isPresent(), "a ritual gate opens");
            g = GateRegistry.get(server).gate(Integer.parseInt(id.get()));
            h.assertTrue(g.plan.ritual() && g.scripted, "the story's crypt is the ritual's");
            g.interiorReady = true;
            g.transition(GateState.READY);
            GateLiveTests.paintFloor(level, g, 0, 1);
            for (ServerPlayer p : players) Prologue.joinCrypt(p, Prologue.progress(p), id.get());
            RitualHall hall = Gates.ritualHall(server, g.id).orElseThrow();
            body.accept(new Rig(server, level, g, hall, players));
        } finally {
            if (g != null) Ritual.dropForTests(server, g.id);
            for (ServerPlayer p : players) {
                StoryProgress s = Prologue.progress(p);
                s.prologue = StoryProgress.Prologue.DONE;
                s.prologueGate = "";
                if (server.getPlayerList().getPlayer(p.getUUID()) == null) continue; // logged out by the test
                if (Runs.run(p).active()) Runs.endRun(p, g, false);
                Vec3 home = h.absoluteVec(new Vec3(1.5, 2, 1.5));
                p.teleportTo(level, home.x, home.y, home.z, Set.<Relative>of(), 0, 0, true);
            }
            if (g != null) GateService.release(server, g);
            Ritual.clockOffset = 0;
            Ritual.castAnywhere = false;
            GateService.useTestLevel(null);
        }
    }

    /** Puts a player through the doors (in the nave, a few rows in). */
    private static void intoHall(Rig r, ServerPlayer p, double side) {
        Vec3 at = r.hall().doorway().add(side, 0, 6);
        p.teleportTo(r.level(), at.x, at.y, at.z, Set.<Relative>of(), 0, 0, true);
    }

    private static void enter(GameTestHelper h, Rig r, ServerPlayer p) {
        h.assertTrue(Runs.enter(p, r.gate()), "an unawakened player walks into the ritual gate");
        h.assertTrue(Ritual.inRitualGate(p), "in the ritual gate (antechamber)");
    }

    /** The director, {@code ticks} later. */
    private static void advance(Rig r, int ticks) {
        Ritual.clockOffset += ticks;
        Ritual.tick(r.server(), false);
    }

    /** The director, at this many ticks into the current beat. */
    private static void toAge(Rig r, long age) {
        advance(r, (int) (age - Ritual.age(r.server(), r.gate().id)));
    }

    private static void startHere(GameTestHelper h, Rig r) {
        for (ServerPlayer p : r.players()) {
            enter(h, r, p);
            intoHall(r, p, r.players().indexOf(p) * 2.0 - 1.0);
        }
        Ritual.tick(r.server(), true);
        advance(r, Ritual.WAIT_GRACE + 1);
        h.assertTrue(Ritual.beatName(r.server(), r.gate().id).equals("DOORS"), "everyone is in: the doors close, beat " + Ritual.beatName(r.server(), r.gate().id));
    }

    static void register() {
        // Multiplayer: one inside, one still outside. The hall waits, says who it waits for, and closes after 90 s.
        AwakenTests.registerIsolated("story_ritual_waits_for_everyone", 200, h -> h.startSequence().thenExecute(() -> withRitual(h, 2, r -> {
            ServerPlayer in = r.players().get(0), out = r.players().get(1);
            enter(h, r, in);
            h.assertTrue(Ritual.now(in).getFirst().text().getString().equals("Go through the doors"), "the antechamber says where to go");
            intoHall(r, in, 0);
            Ritual.tick(r.server(), true);
            Ritual.tick(r.server(), false);
            h.assertTrue(Ritual.beatName(r.server(), r.gate().id).equals("WAITING"), "one of two inside: the hall waits");
            var line = Ritual.now(in);
            h.assertTrue(!line.isEmpty() && line.getFirst().text().getString().equals("Waiting for 1 hunter…"), "the Now line: " + line);
            advance(r, Ritual.WAIT_TIMEOUT - 40);
            h.assertTrue(Ritual.beatName(r.server(), r.gate().id).equals("WAITING"), "still waiting before the timeout");
            advance(r, 41);
            h.assertTrue(Ritual.beatName(r.server(), r.gate().id).equals("DOORS"), "90 s after the first one came in, the doors close anyway");
            h.assertTrue(Prologue.progress(in).prologue == StoryProgress.Prologue.RITUAL, "the one inside is sealed in");
            StoryProgress os = Prologue.progress(out);
            h.assertTrue(os.prologue == StoryProgress.Prologue.GLITCHED && os.prologueGate.isEmpty(), "the one outside gets a crypt of their own: " + os.prologue);
            h.assertTrue(Ritual.refuses(out, Integer.toString(r.gate().id)).isPresent(), "and can't walk into the sealed hall");
        })).thenSucceed());

        // A disconnect while waiting: the one who left is no longer waited for.
        AwakenTests.registerIsolated("story_ritual_disconnect_while_waiting", 200, h -> h.startSequence().thenExecute(() -> withRitual(h, 2, r -> {
            ServerPlayer in = r.players().get(0), gone = r.players().get(1);
            enter(h, r, in);
            intoHall(r, in, 0);
            Ritual.tick(r.server(), true);
            Ritual.tick(r.server(), false);
            h.assertTrue(Ritual.beatName(r.server(), r.gate().id).equals("WAITING"), "waiting for the second");
            r.server().getPlayerList().remove(gone);
            advance(r, Ritual.WAIT_GRACE + 1);
            h.assertTrue(Ritual.beatName(r.server(), r.gate().id).equals("DOORS"), "the second logged out: the doors close without waiting the 90 s");
        })).thenSucceed());

        // Every beat in order, the rules (one kneels and stands on the plates, one doesn't), and the hand closes on both.
        AwakenTests.registerIsolated("story_ritual_every_beat", 300, h -> h.startSequence().thenExecute(() -> withRitual(h, 2, r -> {
            ServerPlayer good = r.players().get(0), bad = r.players().get(1);
            startHere(h, r);
            int gate = r.gate().id;
            SeatedOne seated = Ritual.seated(gate);
            h.assertTrue(seated != null && seated.stage() == SeatedOne.DORMANT, "the Seated One sits with its eyes closed");
            h.assertTrue(Ritual.statues(gate).size() == 12, "twelve statues kneel along the nave");
            // beat 1: the doors grind shut, every flame dies, the candles light again toward the throne
            advance(r, 30);
            for (BlockPos p : r.hall().doorBlocks()) h.assertFalse(r.level().getBlockState(p).isAir(), "the doors are shut at " + p);
            advance(r, 10);
            RitualHall.Light candle = r.hall().lights().stream().filter(l -> l.lit().hasProperty(BlockStateProperties.LIT)).findFirst().orElseThrow();
            h.assertFalse(r.level().getBlockState(candle.pos()).getValue(BlockStateProperties.LIT), "the flames die");
            advance(r, 150);
            h.assertTrue(r.level().getBlockState(candle.pos()).getValue(BlockStateProperties.LIT), "and the candles light again");
            h.assertTrue(Ritual.beatName(r.server(), gate).equals("BOW"), "beat 2a, BOW: " + Ritual.beatName(r.server(), gate));
            // BOW: kneel within six seconds, or the nearest statue strikes
            good.setShiftKeyDown(true);
            bad.setShiftKeyDown(false);
            float before = bad.getHealth();
            toAge(r, Ritual.BOW_DEADLINE - 2);
            toAge(r, Ritual.BOW_DEADLINE);
            h.assertTrue(Ritual.bowed(gate, good.getUUID()) && !Ritual.bowed(gate, bad.getUUID()), "one bowed, one didn't");
            toAge(r, Ritual.BOW_STRIKE);
            h.assertTrue(bad.getHealth() < before && bad.isAlive(), "the one standing is struck, and lives: " + bad.getHealth());
            h.assertTrue(good.getHealth() == good.getMaxHealth(), "the one kneeling is spared");
            good.setShiftKeyDown(false);
            toAge(r, Ritual.Beat.BOW.length);
            h.assertTrue(Ritual.beatName(r.server(), gate).equals("RING"), "beat 2b, RING");
            // RING: three plates in turn; the good one stands on each, the other stays put
            for (int k = 0; k < 3; k++) {
                int lit = Ritual.RING_FIRST + k * Ritual.RING_STEP;
                toAge(r, lit + 1);
                Vec3 plate = r.hall().plates().get(k);
                good.teleportTo(r.level(), plate.x, plate.y, plate.z, Set.<Relative>of(), 0, 0, true);
                toAge(r, lit + 2);
                h.assertTrue(r.hall().plateUnder(good.position()) == k, "standing on plate " + k);
                toAge(r, lit + Ritual.RING_WINDOW + 1);
            }
            h.assertTrue(Ritual.misses(gate) == 3, "the one who never moved missed all three plates: " + Ritual.misses(gate));
            toAge(r, Ritual.Beat.RING.length);
            h.assertTrue(Ritual.beatName(r.server(), gate).equals("JUDGED"), "beat 2c, BE JUDGED: " + Ritual.beatName(r.server(), gate));
            toAge(r, Ritual.Beat.JUDGED.length);
            h.assertTrue(Ritual.beatName(r.server(), gate).equals("WAKING"), "beat 3, the waking");
            toAge(r, Ritual.Beat.WAKING.length - 1);
            h.assertTrue(seated.stage() == SeatedOne.AWAKE, "its eyes are open: stage " + seated.stage());
            for (RitualStatue s : Ritual.statues(gate)) h.assertTrue(s.stage() == RitualStatue.STAND, "the statues stand");
            toAge(r, Ritual.Beat.WAKING.length);
            h.assertTrue(Ritual.beatName(r.server(), gate).equals("JUDGEMENT"), "beat 4, the Judgement");
            // the Judgement, in small steps so the statues walk; nobody dies
            for (int t = 0; t < Ritual.Beat.JUDGEMENT.length; t += 5) {
                advance(r, 5);
                for (ServerPlayer p : r.players()) h.assertTrue(p.isAlive() && p.getHealth() >= 1F, "nobody dies in the Judgement");
            }
            h.assertTrue(Ritual.beatName(r.server(), gate).equals("END"), "beat 5, the end: " + Ritual.beatName(r.server(), gate));
            toAge(r, Ritual.DEATH - 1);
            h.assertTrue(Prologue.progress(good).prologue == StoryProgress.Prologue.RITUAL, "still in the ritual until the hand closes");
            toAge(r, Ritual.DEATH);
            for (ServerPlayer p : r.players()) {
                h.assertTrue(Prologue.progress(p).prologue == StoryProgress.Prologue.ACCEPTING && p.isAlive(), "the hand closes on everyone at once");
                h.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS) && p.getEffect(net.minecraft.world.effect.MobEffects.BLINDNESS).isInfiniteDuration(),
                        "and the black holds");
            }
            h.assertTrue(Ritual.beatName(r.server(), gate).equals("DONE") && seated.stage() == SeatedOne.SPENT, "done; its eyes close again");
        })).thenSucceed());

        // From the antechamber to the hand: no hit kills an unawakened player in the ritual gate.
        AwakenTests.registerIsolated("story_ritual_nothing_kills_before_the_end", 200, h -> h.startSequence().thenExecute(() -> withRitual(h, 1, r -> {
            ServerPlayer p = r.players().getFirst();
            enter(h, r, p);
            p.hurtServer(r.level(), p.damageSources().generic(), 1000F);
            h.assertTrue(p.isAlive() && p.getHealth() >= 1F && p.getHealth() <= 1.01F, "in the antechamber a killing blow leaves half a heart: " + p.getHealth());
            h.assertTrue(Prologue.progress(p).prologue == StoryProgress.Prologue.GATE_OPEN, "and is not the death: " + Prologue.progress(p).prologue);
            p.setHealth(p.getMaxHealth());
            intoHall(r, p, 0);
            Ritual.tick(r.server(), true);
            advance(r, Ritual.WAIT_GRACE + 1);
            p.damageCooldownTime = 0;
            p.hurtServer(r.level(), p.damageSources().fall(), 500F);
            h.assertTrue(p.isAlive() && p.getHealth() >= 1F && p.getHealth() <= 1.01F && Prologue.progress(p).prologue == StoryProgress.Prologue.RITUAL,
                    "sealed in, a fall leaves half a heart and the ritual goes on: " + p.getHealth());
        })).thenSucceed());

        // Relog and restart: the beat is saved, a restart resumes it with its cast, a relog shows it, a return after the end asks.
        AwakenTests.registerIsolated("story_ritual_relog_resumes", 200, h -> h.startSequence().thenExecute(() -> withRitual(h, 1, r -> {
            ServerPlayer p = r.players().getFirst();
            startHere(h, r);
            int gate = r.gate().id;
            advance(r, Ritual.Beat.DOORS.length + Ritual.Beat.BOW.length + 50);
            h.assertTrue(Ritual.beatName(r.server(), gate).equals("RING"), "on RING");
            SeatedOne seated = Ritual.seated(gate);
            // the saved state round-trips
            RitualData data = RitualData.get(r.server());
            var tag = RitualData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
            RitualData back = RitualData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
            RitualData.State st = back.rituals.get(gate);
            h.assertTrue(st != null && st.beat == Ritual.Beat.RING && st.party.contains(p.getUUID()) && st.seated.isPresent()
                    && st.statues.size() == 12 && !st.mouth.equals(net.minecraft.core.BlockPos.ZERO), "the ritual's state is saved whole");
            // a restart: nothing live survives; the director finds its cast again and plays the beat on
            Ritual.forgetLive();
            advance(r, 1);
            h.assertTrue(Ritual.beatName(r.server(), gate).equals("RING") && Ritual.seated(gate) == seated, "resumed on RING with the same Seated One");
            // a relog in the middle: the client is told the beat it's on
            Ritual.onLogin(p);
            h.assertTrue(Ritual.beatOf(p) == Ritual.Beat.RING.ordinal(), "a relog lands on RING");
            // logged out: the scene holds while nobody of the party is there
            r.server().getPlayerList().remove(p);
            advance(r, 2000);
            h.assertTrue(Ritual.beatName(r.server(), gate).equals("RING"), "nobody there: the ritual waits on RING, " + Ritual.beatName(r.server(), gate));
            // and if the hand closed while they were away, they come back to the question
            data.rituals.get(gate).beat = Ritual.Beat.DONE;
            Prologue.progress(p).prologue = StoryProgress.Prologue.RITUAL;
            Ritual.onLogin(p);
            h.assertTrue(Prologue.progress(p).prologue == StoryProgress.Prologue.ACCEPTING, "back after the end: asked to accept");
        })).thenSucceed());

        // /awaken story skip from inside the sealed hall: awake, out of the gate, and the hall is spent.
        AwakenTests.registerIsolated("story_ritual_skip_leaves_the_hall", 200, h -> h.startSequence().thenExecute(() -> withRitual(h, 1, r -> {
            ServerPlayer p = r.players().getFirst();
            startHere(h, r);
            advance(r, Ritual.Beat.DOORS.length + 20);
            Prologue.skip(p);
            h.assertTrue(Progression.profile(p).awakened() && Prologue.progress(p).prologue == StoryProgress.Prologue.DONE, "skipped: awake");
            h.assertFalse(Runs.run(p).active(), "and out of the gate");
            advance(r, 1);
            h.assertTrue(Ritual.beatName(r.server(), r.gate().id).equals("DONE"), "nobody is left in the hall: it's spent, " + Ritual.beatName(r.server(), r.gate().id));
        })).thenSucceed());

        // The cast's bodies parse with every clip the renderers play and the bones the effects measure.
        AwakenTests.register("story_ritual_cast_rigs", 20, h -> {
            var seated = dev.awaken.mobs.client.anim.HeroRig.get("the_seated_one");
            var statue = dev.awaken.mobs.client.anim.HeroRig.get("ritual_statue");
            h.assertTrue(seated != null && statue != null, "both rigs are built (tools/art/specs/ritual_art.py)");
            for (String c : List.of("dormant", "wake", "awake", "gaze", "raise", "close", "sleep")) h.assertTrue(seated.clip(c) != null, "the Seated One plays " + c);
            for (String b : List.of("head", "hand_r", "lid_l", "lid_r", "fingers_r")) h.assertTrue(seated.bone(b) >= 0, "the Seated One has a " + b);
            for (String c : List.of("kneel", "rise", "stand", "walk", "windup", "strike", "recover")) h.assertTrue(statue.clip(c) != null, "a statue plays " + c);
            h.assertTrue(statue.bone("head") >= 0 && statue.bone("glaive") >= 0, "a statue has a head and a glaive");
            h.succeed();
        });

        // Saves from before the ritual decode cleanly; the new state round-trips; an unknown one falls back.
        // the god must be seen at every beat (SPEC §13): lit while the candles burn, its runes waking beat by beat
        AwakenTests.register("story_ritual_god_is_seen", 20, h -> {
            int lastRunes = 0;
            for (Ritual.Beat b : Ritual.Beat.values()) {
                if (b == Ritual.Beat.DONE) continue;
                for (long age = 0; age <= Math.max(1, b.length); age += 5) {
                    int key = Ritual.keyLight(b, age), runes = Ritual.runes(b, age);
                    // (at the end they drain into its palm with the hall's light, never quite out)
                    h.assertTrue(runes >= (b == Ritual.Beat.END ? 15 : 30), "the runes always show in the hall: " + b + " at " + age + " is " + runes);
                    boolean dark = b == Ritual.Beat.DOORS && age >= 34 && age < 120;
                    h.assertTrue(dark ? key >= 2 : key >= 7, "the god is lit: " + b + " at " + age + " is " + key);
                }
                if (b.ordinal() <= Ritual.Beat.JUDGEMENT.ordinal()) {
                    int r = Ritual.runes(b, b.length);
                    h.assertTrue(r >= lastRunes, "the runes never dim before the end: " + b);
                    lastRunes = r;
                }
            }
            h.assertTrue(Ritual.runes(Ritual.Beat.WAKING, Ritual.Beat.WAKING.length) == 100, "fully awake when its eyes are open");
            h.assertTrue(Ritual.keyLight(Ritual.Beat.DONE, 0) == 0, "after the ritual, only the blocks light it");
            String words = Prologue.voidWords();
            h.assertTrue(!words.isBlank() && !words.contains(Prologue.QUESTION), "the black's words leave the question to the card: " + words);
            h.succeed();
        });

        AwakenTests.register("story_ritual_old_saves", 20, h -> {
            CompoundTag v1 = new CompoundTag();
            v1.putString("prologue", "GATE_OPEN");
            v1.putString("gate", "7");
            v1.putLong("since", 99);
            ListTag ret = new ListTag();
            for (double d : new double[]{1, 64, 2}) ret.add(DoubleTag.valueOf(d));
            v1.put("return", ret);
            v1.put("seen", new ListTag());
            v1.put("sovereigns", new ListTag());
            StoryProgress a = StoryProgress.CODEC.parse(NbtOps.INSTANCE, v1).getOrThrow();
            h.assertTrue(a.prologue == StoryProgress.Prologue.GATE_OPEN && a.prologueGate.equals("7") && a.returnY == 64 && a.cryptCounterSeen == -1,
                    "a v1 save without the crypt record reads");
            CompoundTag crypt = new CompoundTag();
            crypt.putInt("schema", 1);
            crypt.putInt("floor", 2);
            v1.put("crypt", crypt);
            h.assertTrue(StoryProgress.CODEC.parse(NbtOps.INSTANCE, v1).getOrThrow().cryptFloor == 2, "a schema-1 crypt record reads");
            StoryProgress s = new StoryProgress();
            s.prologue = StoryProgress.Prologue.RITUAL;
            var tag = StoryProgress.CODEC.encodeStart(NbtOps.INSTANCE, s).getOrThrow();
            h.assertTrue(StoryProgress.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow().prologue == StoryProgress.Prologue.RITUAL, "RITUAL round-trips");
            v1.putString("prologue", "SOMETHING_NEWER");
            h.assertTrue(StoryProgress.CODEC.parse(NbtOps.INSTANCE, v1).getOrThrow().prologue == StoryProgress.Prologue.NOT_STARTED, "an unknown state falls back");
            h.assertTrue(RitualData.CODEC.parse(NbtOps.INSTANCE, new CompoundTag()).getOrThrow().rituals.isEmpty(), "an empty ritual file reads");
            h.succeed();
        });

        // The story starts together: the first trigger opens one Bell Crypt and calls everyone else in the story to it.
        AwakenTests.registerIsolated("story_ritual_one_crypt_for_everyone", 200, h -> h.startSequence().thenExecute(() -> {
            ServerLevel level = h.getLevel();
            GateService.useTestLevel(level);
            List<ServerPlayer> ps = List.of(player(h), player(h), player(h));
            String gate = "";
            try {
                // the third has already awakened: never called
                Progression.awaken(ps.get(2));
                Prologue.progress(ps.get(2)).prologue = StoryProgress.Prologue.DONE;
                Prologue.openFirstGate(ps.get(0), Prologue.progress(ps.get(0)));
                gate = Prologue.progress(ps.get(0)).prologueGate;
                h.assertTrue(!gate.isEmpty() && Gates.isRitual(level.getServer(), Integer.parseInt(gate)), "the first trigger opens the ritual's crypt");
                StoryProgress second = Prologue.progress(ps.get(1));
                h.assertTrue(second.prologue == StoryProgress.Prologue.GATE_OPEN && second.prologueGate.equals(gate), "the other unawakened player is called to it");
                h.assertTrue(Prologue.progress(ps.get(2)).prologueGate.isEmpty(), "the awakened one isn't");
                // someone reaching their own trigger later, before the doors close, joins the same crypt
                second.prologue = StoryProgress.Prologue.GLITCHED;
                second.prologueGate = "";
                Prologue.openFirstGate(ps.get(1), second);
                h.assertTrue(second.prologueGate.equals(gate), "a later trigger joins the open crypt: " + second.prologueGate);
            } finally {
                for (ServerPlayer p : ps) {
                    Prologue.progress(p).prologue = StoryProgress.Prologue.DONE;
                    Prologue.progress(p).prologueGate = "";
                }
                if (!gate.isEmpty()) GateService.release(level.getServer(), GateRegistry.get(level.getServer()).gate(Integer.parseInt(gate)));
                GateService.useTestLevel(null);
            }
        }).thenSucceed());
    }
}
