package dev.awaken.body.ascent;

import com.mojang.serialization.JsonOps;
import dev.awaken.body.BodyConfig;
import dev.awaken.core.api.Goals;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.api.TrialHooks;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;

import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;
import java.util.Optional;

/**
 * GameTests for The Way Up (SPEC §63.7): a fresh player walked rung by rung by the real grants (one row or a rank fight
 * ahead at every stop, a row with its place once one woke, no rung handed down without its lesson owed), the words of a
 * direction, the record's round trip, the wings that come first at the sky's edge, the form at Sovereign rank and its
 * field leaving a boss where it stands, and the old rules with the option off.
 */
final class WayTests {
    private WayTests() {}

    static void register() {
        AwakenTests.registerIsolated("way_up_road", 60, WayTests::road);
        AwakenTests.registerIsolated("way_up_handed_down", 100, WayTests::handedDown);
        AwakenTests.registerIsolated("way_up_form_and_field", 60, WayTests::formAndField);
        AwakenTests.register("way_up_words_and_record", 20, WayTests::wordsAndRecord);
        AwakenTests.registerIsolated("way_up_moves", 40, WayTests::moves);
    }

    /** A client says it made a move out of {@code from}: the server's own judging of it. */
    private static void says(ServerPlayer p, AscentState from, int what) {
        AscentMotion m = AscentServer.motion(p);
        long now = AscentServer.now(p);
        m.state = from;
        m.since = now;
        m.endedAt = now;
        AscentServer.onEvent(p, new AscentPayloads.Event(what, 1F));
        m.state = AscentState.NONE;
    }

    /**
     * Rope and Wings' six moves on the road (SPEC §63.8): each is counted only with its rung and its mastery (the sling
     * with the rope, which a D-rank hunter does not hold before a vault; the spring at Tether mastery 3; the rope strike
     * at 5; the flare with wings; the skim at Wings mastery 3; the takeoff with flight), and the count is what the
     * client's lesson is told, so a move made once is never taught again.
     */
    private static void moves(GameTestHelper h) {
        BodyConfig.wayOverride = true;
        ServerPlayer p = player(h);
        try {
            TrialHooks.done(p, TrialHooks.RINGER, 0, 0);
            Progression.passReassessment(p);
            h.assertTrue(!AscentServer.allowed(p, AscentState.ZIP) && WayUp.moves(p) == 0, "D-rank before a vault: no rope, so no sling; no move made yet");
            says(p, AscentState.CARRY, AscentPayloads.EV_SPRING);
            says(p, AscentState.NONE, AscentPayloads.EV_FLARE);
            says(p, AscentState.GLIDE, AscentPayloads.EV_SKIM);
            says(p, AscentState.SOAR, AscentPayloads.EV_TAKEOFF);
            h.assertTrue(WayUp.moves(p) == 0, "without its rung no move is counted, whatever a client says: " + WayUp.moves(p));
            // the rope: the sling at once, the spring at mastery 3, the strike at 5
            Ascent.grant(p, Ascent.Tier.TETHER, "tether vow");
            h.assertTrue(AscentServer.allowed(p, AscentState.ZIP), "the sling's pull comes with the rope");
            says(p, AscentState.ZIP, AscentPayloads.EV_SLING);
            says(p, AscentState.CARRY, AscentPayloads.EV_SPRING);
            h.assertTrue(WayUp.moves(p) == WayUp.Move.SLING.bit(), "the sling with the rope, and no spring below Tether mastery " + AirRules.SPRING_LEVEL + ": " + WayUp.moves(p));
            AscentServer.addMastery(p, AscentMemory.Track.TETHER, 25 * AirRules.SPRING_LEVEL * AirRules.SPRING_LEVEL);
            says(p, AscentState.CARRY, AscentPayloads.EV_SPRING);
            h.assertTrue((WayUp.moves(p) & WayUp.Move.SPRING.bit()) != 0, "the spring at Tether mastery " + AirRules.SPRING_LEVEL);
            h.assertTrue(!AirRules.strikes(AirRules.STRIKE_LEVEL - 1, 8, 20) && AirRules.strikes(AirRules.STRIKE_LEVEL, 8, 20),
                    "the rope strike at Tether mastery " + AirRules.STRIKE_LEVEL);
            // wings: the flare at once, the skim at mastery 3; the takeoff only with flight
            Ascent.grant(p, Ascent.Tier.WINGS, "sky platform core");
            says(p, AscentState.NONE, AscentPayloads.EV_FLARE);
            says(p, AscentState.GLIDE, AscentPayloads.EV_SKIM);
            says(p, AscentState.SOAR, AscentPayloads.EV_TAKEOFF);
            int now = WayUp.moves(p);
            h.assertTrue((now & WayUp.Move.FLARE.bit()) != 0 && (now & (WayUp.Move.SKIM.bit() | WayUp.Move.TAKEOFF.bit())) == 0,
                    "the flare with wings; no skim below Wings mastery " + AirRules.SKIM_LEVEL + ", no takeoff without flight: " + now);
            AscentServer.addMastery(p, AscentMemory.Track.WINGS, 25 * AirRules.SKIM_LEVEL * AirRules.SKIM_LEVEL);
            says(p, AscentState.GLIDE, AscentPayloads.EV_SKIM);
            Ascent.grant(p, Ascent.Tier.SKYBORN, "the sky warden");
            AscentServer.motion(p).aether = 1;
            says(p, AscentState.SOAR, AscentPayloads.EV_TAKEOFF);
            int all = 0;
            for (WayUp.Move mv : WayUp.Move.VALUES) if (mv != WayUp.Move.STRIKE) all |= mv.bit();
            h.assertTrue(WayUp.moves(p) == all, "the skim at Wings mastery 3 and the takeoff with flight: every move but the strike made, " + WayUp.moves(p));
        } finally {
            BodyConfig.wayOverride = null;
            AscentServer.motion(p).state = AscentState.NONE;
            WayUp.forget(p.getUUID());
            h.getLevel().getServer().getPlayerList().remove(p);
        }
        h.succeed();
    }

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        Progression.awaken(p);
        // a mock's body is new: old enough that a rung handed down is not an old world's first load
        p.tickCount = 400;
        return p;
    }

    private static Goals.Step row(GameTestHelper h, ServerPlayer p, String key) {
        List<Goals.Step> rows = WayUp.steps(p);
        h.assertTrue(rows.size() == 1, "one row for the way up, was " + rows.size());
        Goals.Step s = rows.getFirst();
        h.assertTrue(s.key().equals(key), "the row is " + s.key() + ", wanted " + key);
        h.assertTrue(s.voice() == Goals.Voice.LEAD && s.thread().equals(WayUp.THREAD) && !s.teaches().isEmpty(),
                key + " is a Lead on the way-up thread and teaches its rung");
        return s;
    }

    /** The road, stop by stop: nothing at E, the cold chain and the vault at D, the leap's two rows, wings at C. */
    private static void road(GameTestHelper h) {
        BodyConfig.wayOverride = true;
        ServerPlayer p = player(h);
        try {
            long now = AscentServer.now(p);
            h.assertTrue(WayUp.waiting(p) == null && WayUp.steps(p).isEmpty(), "E-rank: nothing waits, and no row names what is ahead");
            // the Ringer falls: its chain is cold, not the rope
            TrialHooks.done(p, TrialHooks.RINGER, 0, 0);
            h.assertFalse(Ascent.has(p, Ascent.Tier.TETHER), "the Ringer's chain is cold until a vault wakes it");
            Progression.passReassessment(p);
            h.assertTrue(Progression.rank(p) == Rank.D && WayUp.waiting(p) == WayUp.Rung.ROPE, "D-rank without the rope: the rope waits");
            row(h, p, "way/rope");
            // a place wakes: the row points at it, and the Now line carries it
            BlockPos vault = p.blockPosition().offset(310, 0, 0);
            p.setData(WayUp.MEMORY, WayUp.memory(p).withPlace(WayUp.Rung.ROPE, vault));
            p.setOnGround(true);
            WayUp.read(p, now);
            h.assertTrue(WayUp.memory(p).told(WayUp.Rung.ROPE), "the vault that woke was told");
            if (p.level().dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                h.assertTrue(row(h, p, "way/rope").where().filter(vault::equals).isPresent(), "the row points at the vault");
                h.assertTrue(WayUp.now(p).size() == 1, "and it rides the Now line while it is fresh");
            }
            // any vault's forecourt gives the rope to a D-rank hunter: a C door, still sealed, no longer turns them away
            TetherVow.Site cDoor = new TetherVow.Site(new BoundingBox(0, 0, 0, 22, 23, 22), 0, Rank.C.ordinal());
            h.assertTrue(TetherVow.ready(p, cDoor) && TetherVow.needs(cDoor) == Rank.D, "the chain is the proof at any vault");
            Ascent.grant(p, Ascent.Tier.TETHER, "tether vow");
            h.assertTrue(WayUp.memory(p).place(WayUp.Rung.ROPE) == null && !WayUp.memory(p).owed(WayUp.Rung.ROPE),
                    "the rope given where it is taught: its place is cleared and nothing is owed");
            // the leap: a row with or without a spire, then the gate's own row with its place
            h.assertTrue(WayUp.waiting(p) == WayUp.Rung.LEAP, "the rope held: the leap waits");
            row(h, p, "way/leap");
            BlockPos foot = p.blockPosition().offset(0, 0, 40);
            WayUp.gateOpened(p, 7, foot);
            if (p.level().dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                h.assertTrue(row(h, p, "way/leap_gate").where().filter(foot::equals).isPresent(), "the gate is a row with its place");
            } else {
                row(h, p, "way/leap_gate");
            }
            // C-rank: wings lead, and the leap's gate still stands for whoever runs it
            Progression.passReassessment(p);
            h.assertTrue(Progression.rank(p) == Rank.C && WayUp.waiting(p) == WayUp.Rung.WINGS, "C-rank with the rope: wings wait");
            row(h, p, "way/wings");
            Ascent.grant(p, Ascent.Tier.LEAPER, "leaper's gauntlet");
            h.assertTrue(WayUp.memory(p).leapGate() < 0, "the gate run: it leaves the road");
            // a core gives wings: the first flight's soft landing, and a row until they have been opened once
            Ascent.grant(p, Ascent.Tier.WINGS, "sky platform core");
            WayUp.firstFlight(p, p.blockPosition());
            h.assertTrue(WayUp.softFall(p), "after the core's gift a fall is a soft landing");
            row(h, p, "way/first_flight");
            p.setData(WayUp.MEMORY, WayUp.memory(p).withDone(WayUp.Rung.WINGS));
            h.assertTrue(WayUp.steps(p).isEmpty() && WayUp.waiting(p) == null, "wings opened: the road is quiet until the next rank fight");
        } finally {
            BodyConfig.wayOverride = null;
            WayUp.forget(p.getUUID());
            h.getLevel().getServer().getPlayerList().remove(p);
        }
        h.succeed();
    }

    /** A rung handed down owes its lesson; wings never earned come two seconds before the sky; off, the old rules. */
    private static void handedDown(GameTestHelper h) {
        BodyConfig.wayOverride = true;
        ServerPlayer core = player(h), sky = player(h), old = player(h);
        boolean finished = false;
        try {
            // a sky core reached with the rope alone: the leap comes along, and is owed
            Ascent.grant(core, Ascent.Tier.TETHER, "tether vow");
            Ascent.grant(core, Ascent.Tier.WINGS, "sky platform core");
            WayMemory m = WayUp.memory(core);
            h.assertTrue(m.owed(WayUp.Rung.LEAP) && !m.owed(WayUp.Rung.WINGS) && !m.owed(WayUp.Rung.ROPE),
                    "the leap a core hands down is owed its lesson; the rung asked for is not");
            core.setData(WayUp.MEMORY, m.withDone(WayUp.Rung.LEAP));
            h.assertFalse(WayUp.memory(core).owed(WayUp.Rung.LEAP), "done once, it is owed no more");
            // the sky's edge with the rope alone: wings first, flight two seconds later
            Ascent.grant(sky, Ascent.Tier.TETHER, "tether vow");
            h.assertTrue(TrialHooks.atEdge(sky, TrialHooks.SKY_WARDEN), "the edge answers");
            h.assertTrue(Ascent.has(sky, Ascent.Tier.WINGS) && !Ascent.has(sky, Ascent.Tier.SKYBORN), "wings come first, alone");
            h.assertTrue(WayUp.memory(sky).owed(WayUp.Rung.LEAP) && WayUp.memory(sky).owed(WayUp.Rung.WINGS),
                    "the leap and the wings given in a fight are owed their lessons");
            // off: the Ringer's chain is the rope, as before
            BodyConfig.wayOverride = false;
            TrialHooks.done(old, TrialHooks.RINGER, 0, 0);
            h.assertTrue(Ascent.has(old, Ascent.Tier.TETHER), "with The Way Up off the Ringer's chain is the rope");
            h.assertTrue(WayUp.steps(old).isEmpty(), "and no row is listed");
            BodyConfig.wayOverride = true;
            finished = true;
            h.runAfterDelay(AscentTrials.WINGS_FIRST + 2, () -> {
                try {
                    h.assertTrue(Ascent.has(sky, Ascent.Tier.SKYBORN), "then the sky, two seconds after the wings");
                } finally {
                    BodyConfig.wayOverride = null;
                    for (ServerPlayer p : List.of(core, sky, old)) {
                        WayUp.forget(p.getUUID());
                        h.getLevel().getServer().getPlayerList().remove(p);
                    }
                }
                h.succeed();
            });
        } finally {
            if (!finished) {
                BodyConfig.wayOverride = null;
                for (ServerPlayer p : List.of(core, sky, old)) {
                    WayUp.forget(p.getUUID());
                    h.getLevel().getServer().getPlayerList().remove(p);
                }
            }
        }
    }

    /** Sovereign rank is the form; its field routs a zombie and leaves a boss most of its pace. */
    private static void formAndField(GameTestHelper h) {
        BodyConfig.wayOverride = true;
        ServerPlayer p = player(h);
        try {
            while (Progression.rank(p) != Rank.SOVEREIGN) Progression.passReassessment(p);
            h.assertFalse(Ascent.has(p, Ascent.Tier.SOVEREIGN), "the rank alone is not yet the form");
            WayUp.read(p, AscentServer.now(p));
            h.assertTrue(Ascent.has(p, Ascent.Tier.SOVEREIGN) && Ascent.has(p, Ascent.Tier.WINGS), "the last rank fight pays the last rung");
            // in the test's own loaded ground (a mock player starts elsewhere): two zombies, the player between them
            Zombie plain = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(1, 2, 1));
            Zombie boss = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(5, 2, 1));
            plain.setNoAi(true);
            boss.setNoAi(true);
            boss.setData(dev.awaken.core.api.CoreApi.MOB_ROLE.get(), new MobRole(MobRole.Role.BOSS, 100, "", Optional.empty(), ""));
            p.teleportTo(plain.getX() + 2, plain.getY(), plain.getZ());
            h.assertTrue(dev.awaken.powers.Combat.isBoss(boss) && !dev.awaken.powers.Combat.isBoss(plain), "one of them is a boss");
            Sovereign.field(p);
            // the field's two answers, read off what it lays on each (a test's mock player can't be made a monster's
            // target, so the target it leaves a boss is read in the code, not here): the rout begins with Slowness III,
            // and a boss gets Slowness I and nothing else
            var slowPlain = plain.getEffect(MobEffects.SLOWNESS);
            var slowBoss = boss.getEffect(MobEffects.SLOWNESS);
            h.assertTrue(slowPlain != null && slowPlain.getAmplifier() == 2, "a zombie is slowed hard: the rout");
            h.assertTrue(slowBoss != null && slowBoss.getAmplifier() == 0, "a boss is slowed a little and not routed (slowness "
                    + (slowBoss == null ? "none" : String.valueOf(slowBoss.getAmplifier())) + ")");
            plain.discard();
            boss.discard();
        } finally {
            BodyConfig.wayOverride = null;
            WayUp.forget(p.getUUID());
            Sovereign.forget(p.getUUID());
            h.getLevel().getServer().getPlayerList().remove(p);
        }
        h.succeed();
    }

    /** "310 blocks east of here", and the record through its codec. */
    private static void wordsAndRecord(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        try {
            BlockPos me = p.blockPosition();
            h.assertTrue(WayUp.whereFrom(p, me.offset(310, 0, 0)).equals("310 blocks east of here"), WayUp.whereFrom(p, me.offset(310, 0, 0)));
            h.assertTrue(WayUp.whereFrom(p, me.offset(0, 0, -420)).equals("420 blocks north of here"), WayUp.whereFrom(p, me.offset(0, 0, -420)));
            h.assertTrue(WayUp.whereFrom(p, me.offset(-300, 0, 300)).endsWith("south-west of here"), WayUp.whereFrom(p, me.offset(-300, 0, 300)));
            h.assertTrue(WayUp.whereFrom(p, me.offset(8, 0, 4)).equals("right here"), "a place underfoot is right here");
            WayMemory m = WayMemory.EMPTY.withPlace(WayUp.Rung.WINGS, new BlockPos(12, 180, -40)).withTold(WayUp.Rung.WINGS, true)
                    .withOwed(WayUp.Rung.LEAP).withDone(WayUp.Rung.ROPE).withGate(9, new BlockPos(1, 64, 2));
            var json = WayMemory.MAP_CODEC.codec().encodeStart(JsonOps.INSTANCE, m).getOrThrow();
            WayMemory back = WayMemory.MAP_CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
            h.assertTrue(back.equals(m) && back.place(WayUp.Rung.WINGS).equals(new BlockPos(12, 180, -40)) && back.owed(WayUp.Rung.LEAP)
                    && back.done(WayUp.Rung.ROPE) && back.leapGate() == 9, "the record comes back as it was written");
            WayMemory empty = WayMemory.MAP_CODEC.codec().parse(JsonOps.INSTANCE, new com.google.gson.JsonObject()).getOrThrow();
            h.assertTrue(empty.isEmpty() && empty.place(WayUp.Rung.ROPE) == null && empty.leapGate() < 0, "nothing saved reads as nothing");
        } finally {
            h.getLevel().getServer().getPlayerList().remove(p);
        }
        h.succeed();
    }
}
