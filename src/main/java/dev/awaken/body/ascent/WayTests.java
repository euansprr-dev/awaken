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

    /** Sovereign rank is the form; its field routs a zombie and leaves a boss its target and most of its pace. */
    private static void formAndField(GameTestHelper h) {
        BodyConfig.wayOverride = true;
        ServerPlayer p = player(h);
        try {
            while (Progression.rank(p) != Rank.SOVEREIGN) Progression.passReassessment(p);
            h.assertFalse(Ascent.has(p, Ascent.Tier.SOVEREIGN), "the rank alone is not yet the form");
            WayUp.read(p, AscentServer.now(p));
            h.assertTrue(Ascent.has(p, Ascent.Tier.SOVEREIGN) && Ascent.has(p, Ascent.Tier.WINGS), "the last rank fight pays the last rung");
            BlockPos at = p.blockPosition();
            Zombie plain = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            Zombie boss = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            h.assertTrue(plain != null && boss != null, "two zombies made");
            plain.snapTo(at.getX() + 3.5, at.getY(), at.getZ() + 0.5, 0, 0);
            boss.snapTo(at.getX() - 3.5, at.getY(), at.getZ() + 0.5, 0, 0);
            plain.setNoAi(true);
            boss.setNoAi(true);
            boss.setData(dev.awaken.core.api.CoreApi.MOB_ROLE.get(), new MobRole(MobRole.Role.BOSS, 100, "", Optional.empty(), ""));
            h.getLevel().addFreshEntity(plain);
            h.getLevel().addFreshEntity(boss);
            plain.setTarget(p);
            boss.setTarget(p);
            Sovereign.field(p);
            var slowPlain = plain.getEffect(MobEffects.SLOWNESS);
            var slowBoss = boss.getEffect(MobEffects.SLOWNESS);
            h.assertTrue(slowPlain != null && slowPlain.getAmplifier() == 2 && plain.getTarget() == null, "a zombie is slowed hard and loses you");
            h.assertTrue(slowBoss != null && slowBoss.getAmplifier() == 0 && boss.getTarget() == p, "a boss is slowed a little and keeps its target");
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
