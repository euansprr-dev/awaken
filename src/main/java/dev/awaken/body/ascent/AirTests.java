package dev.awaken.body.ascent;

import dev.awaken.body.ascent.AscentRules.Kin;
import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/**
 * Rope and Wings (SPEC §62), one test a mechanism: the air's contract (a push in each state, a cast's motion, a blink,
 * the aim hold), the moves' rules (the flare, the skim, the takeoff's rise and cost), the rope strike as the server
 * judges it, and the wings' form (the beat closes on itself, the boxes by distance).
 */
final class AirTests {
    private AirTests() {}

    static void register() {
        AwakenTests.register("ascent_air_push", 20, AirTests::push);
        AwakenTests.register("ascent_air_blink_and_aim", 20, AirTests::blinkAndAim);
        AwakenTests.register("ascent_air_moves", 20, AirTests::moves);
        AwakenTests.register("ascent_rope_strike", 40, AirTests::strike);
        AwakenTests.register("ascent_wing_form", 20, AirTests::wingForm);
    }

    /** A push carries: added in the free states, only along the swing on a rope, none on a zip; a cast never slows you. */
    private static void push(GameTestHelper h) {
        for (AscentState st : new AscentState[] {AscentState.CARRY, AscentState.GLIDE, AscentState.SOAR}) {
            Kin k = new Kin().set(0, 0, 0, 1.0, 0, 0);
            h.assertTrue(AirRules.push(k, st, 0.3, 0.2, -0.4, 0, 0, 0) && near(k.vx, 1.3) && near(k.vy, 0.2) && near(k.vz, -0.4),
                    st + ": a push is added to the state's velocity (" + k.vx + " " + k.vy + " " + k.vz + ")");
            // two in one tick sum
            AirRules.push(k, st, 0.1, 0, 0, 0, 0, 0);
            h.assertTrue(near(k.vx, 1.4), st + ": pushes sum");
        }
        // a rope straight overhead: a push along the rope is the rope's, one across it is the swing's
        Kin rope = new Kin().set(0, 0, 0, 0.5, 0, 0);
        h.assertFalse(AirRules.push(rope, AscentState.TETHER, 0, 0.6, 0, 0, 10, 0), "a push along the rope is taken by the rope");
        h.assertTrue(AirRules.push(rope, AscentState.TETHER, 0.4, 0.6, 0, 0, 10, 0) && near(rope.vx, 0.9) && near(rope.vy, 0),
                "on a rope only the part along the swing counts (" + rope.vx + " " + rope.vy + ")");
        for (AscentState st : new AscentState[] {AscentState.ZIP, AscentState.RAPPEL, AscentState.NONE}) {
            Kin k = new Kin().set(0, 0, 0, 1, 0, 0);
            h.assertFalse(AirRules.push(k, st, 0.5, 0.5, 0.5, 0, 10, 0) || k.vx != 1, st + " takes no push");
        }
        // no push takes a body past the cap unless it was already there
        Kin fast = new Kin().set(0, 0, 0, 2.8, 0, 0);
        AirRules.push(fast, AscentState.GLIDE, 1.5, 0, 0, 0, 0, 0);
        h.assertTrue(near(fast.speed(), AirRules.PUSH_MAX), "a push is capped at " + AirRules.PUSH_MAX + ": " + fast.speed());
        // a cast that sets a motion: a dash ahead never slows a dive; a launch straight up leaves the glide its speed
        Kin dive = new Kin().set(0, 0, 0, 2.4, 0, 0);
        h.assertFalse(AirRules.cast(dive, AscentState.GLIDE, 1.8, 0, 0, 0, 0, 0) || dive.vx != 2.4, "a dash slower than the dive changes nothing");
        Kin glide = new Kin().set(0, 0, 0, 1.2, 0, 0);
        h.assertTrue(AirRules.cast(glide, AscentState.GLIDE, 1.8, 0, 0, 0, 0, 0) && near(glide.vx, 1.8), "a dash ahead takes its speed: " + glide.vx);
        h.assertTrue(AirRules.cast(glide, AscentState.GLIDE, 0, 1.0, 0, 0, 0, 0) && near(glide.vx, 1.8) && near(glide.vy, 1.0),
                "a launch up is added across the flight (" + glide.vx + " " + glide.vy + ")");
        // Levitation lifts a free state by the game's own rule
        Kin lift = new Kin().set(0, 0, 0, 1, -0.5, 0);
        for (int i = 0; i < 120; i++) AirRules.levitate(lift, 1);
        h.assertTrue(near(lift.vy, 0.1), "Levitation II settles a free state at 0.1 up: " + lift.vy);
        h.succeed();
    }

    /** A blink of your own keeps a free state, never a rope, never late or far; the aim hold keeps the line and eases back. */
    private static void blinkAndAim(GameTestHelper h) {
        h.assertTrue(AirRules.blinkKeeps(AscentState.GLIDE, 3, 12 * 12) && AirRules.blinkKeeps(AscentState.SOAR, 20, 16 * 16)
                && AirRules.blinkKeeps(AscentState.CARRY, 0, 25), "a blink just cast, near, moves a free state with the body");
        h.assertFalse(AirRules.blinkKeeps(AscentState.TETHER, 3, 25) || AirRules.blinkKeeps(AscentState.ZIP, 3, 25), "a rope never survives a teleport");
        h.assertFalse(AirRules.blinkKeeps(AscentState.GLIDE, 21, 25), "a teleport long after the cast is not the cast's");
        h.assertFalse(AirRules.blinkKeeps(AscentState.GLIDE, 3, 30 * 30), "nor one too far");
        h.assertFalse(AirRules.blinkKeeps(AscentState.GLIDE, -5, 25), "nor one with no cast before it");
        float[] out = new float[2];
        AirRules.aimSteer(90, -30, 10, 5, true, 0, out);
        h.assertTrue(out[0] == 10 && out[1] == 5, "while the aim is held the flight keeps its line");
        AirRules.aimSteer(90, -30, 10, 5, false, 0, out);
        float first = out[0];
        AirRules.aimSteer(90, -30, 10, 5, false, AirRules.AIM_RETURN - 1, out);
        h.assertTrue(first > 10 && first < 30 && Math.abs(out[0] - 90) < 1e-3 && Math.abs(out[1] + 30) < 1e-3,
                "let go, the flight eases back to the look over " + AirRules.AIM_RETURN + " ticks (" + first + " .. " + out[0] + ")");
        // the short way round the compass
        AirRules.aimSteer(-170, 0, 170, 0, false, 2, out);
        h.assertTrue(out[0] > 170 && out[0] < 190, "the return turns the short way: " + out[0]);
        AirRules.aimSteer(90, -30, 10, 5, false, 100, out);
        h.assertTrue(out[0] == 90 && out[1] == -30, "after that the flight follows the look");
        h.succeed();
    }

    /** The flare, the skim and the takeoff, by their numbers. */
    private static void moves(GameTestHelper h) {
        h.assertTrue(AirRules.flares(0.7, -0.3, 1.2), "slow, level and near the ground is a flare");
        h.assertFalse(AirRules.flares(1.6, -0.3, 1.2) || AirRules.flares(0.7, -0.9, 1.2) || AirRules.flares(0.7, -0.3, 6) || AirRules.flares(0.7, 0.3, 1.2),
                "fast, steep, high or rising is not");
        h.assertTrue(AirRules.skims(3, 1.2, 2.0), "Wings mastery 3, fast and low, skims");
        h.assertFalse(AirRules.skims(2, 1.2, 2.0) || AirRules.skims(3, 0.8, 2.0) || AirRules.skims(3, 1.2, 4.0), "below the mastery, slow or high does not");
        // the skim halves the glide's drag and never removes it: level flight slows, less
        Kin plain = new Kin().set(0, 100, 0, 1.6, 0, 0), skim = new Kin().set(0, 100, 0, 1.6, 0, 0);
        for (int i = 0; i < 60; i++) {
            AscentRules.glideStep(plain, -90, 0, 1);
            AscentRules.glideStep(skim, -90, 0, AirRules.SKIM_DRAG);
        }
        h.assertTrue(skim.speed() > plain.speed() + 0.05 && skim.speed() < 1.6, "a skim keeps more speed and still loses some: " + plain.speed() + " against "
                + skim.speed());
        double rise = 0;
        for (int i = 0; i < AirRules.TAKEOFF_TICKS + 4; i++) rise += AirRules.takeoffStep(i);
        h.assertTrue(Math.abs(rise - AirRules.TAKEOFF_RISE) < 1e-6 && AirRules.takeoffStep(0) > AirRules.takeoffStep(8)
                && AirRules.takeoffStep(AirRules.TAKEOFF_TICKS) == 0, "a takeoff rises " + AirRules.TAKEOFF_RISE + " blocks and eases out: " + rise);
        h.assertTrue(AirRules.takeoffReady(-25, AirRules.TAKEOFF_SNEAK, 0.5, true), "looking up, sneaked long enough, with Aether, on the ground");
        h.assertFalse(AirRules.takeoffReady(-10, 8, 0.5, true) || AirRules.takeoffReady(-25, 3, 0.5, true) || AirRules.takeoffReady(-25, 8, 0.05, true)
                || AirRules.takeoffReady(-25, 8, 0.5, false), "not looking level, too soon, with no Aether or in the air");
        // the server takes the takeoff's Aether, only for Skyborn just risen into flight
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        dev.awaken.progression.Progression.awaken(p);
        Ascent.grant(p, Ascent.Tier.SKYBORN, "test");
        AscentMotion m = AscentServer.motion(p);
        m.aether = 1;
        m.state = AscentState.SOAR;
        m.since = AscentServer.now(p);
        AscentServer.onEvent(p, new AscentPayloads.Event(AscentPayloads.EV_TAKEOFF, 0));
        h.assertTrue(Math.abs(m.aether - (1 - AirRules.TAKEOFF_AETHER)) < 1e-9, "a takeoff costs a tenth of the Aether: " + m.aether);
        m.since = AscentServer.now(p) - 200;
        AscentServer.onEvent(p, new AscentPayloads.Event(AscentPayloads.EV_TAKEOFF, 0));
        h.assertTrue(Math.abs(m.aether - (1 - AirRules.TAKEOFF_AETHER)) < 1e-9, "and is not taken twice, long into a flight");
        m.state = AscentState.NONE;
        h.succeed();
    }

    /**
     * The rope strike is the server's: no blow without the mastery, without a creature the rope was let bite, from too
     * far, or twice; with all of them the creature is hurt by the hand and the speed, and the pull's rule ends at it.
     */
    private static void strike(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        dev.awaken.progression.Progression.awaken(p);
        Ascent.grant(p, Ascent.Tier.TETHER, "test");
        var target = h.spawn(net.minecraft.world.entity.EntityTypes.IRON_GOLEM, new BlockPos(1, 2, 12));
        target.setNoAi(true);
        var at = h.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2, 0.5));
        p.teleportTo(at.x, at.y, at.z);
        p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
        AscentMotion m = AscentServer.motion(p);
        float full = target.getHealth();
        AscentServer.onStrike(p, new AscentPayloads.Strike(target.getId()));
        h.assertTrue(m.strikeTarget < 0, "no strike below Tether mastery " + AirRules.STRIKE_LEVEL);
        AscentServer.addMastery(p, AscentMemory.Track.TETHER, 25 * AirRules.STRIKE_LEVEL * AirRules.STRIKE_LEVEL);
        AscentServer.strikeBlow(p, m, 1.5F);
        h.assertTrue(target.getHealth() == full, "no blow without a creature the rope bit");
        AscentServer.onStrike(p, new AscentPayloads.Strike(target.getId()));
        h.assertTrue(m.strikeTarget == target.getId(), "with the mastery and the creature in the look, the rope may bite it");
        var c = target.getBoundingBox().getCenter();
        h.assertTrue(AscentServer.strikeAnchor(p, m, c.x, c.y, c.z) && !AscentServer.strikeAnchor(p, m, c.x + 9, c.y, c.z),
                "the strike's anchor is its creature, and only there");
        // the blow asked for from where the pull began (a dozen blocks off): refused, and the strike is spent
        AscentServer.strikeBlow(p, m, 1.5F);
        h.assertTrue(target.getHealth() == full && m.strikeTarget < 0, "a blow from out of reach lands nothing");
        // again, and this time the body is at the creature
        m.strikeAt -= AirRules.STRIKE_COOLDOWN;
        AscentServer.onStrike(p, new AscentPayloads.Strike(target.getId()));
        p.teleportTo(c.x, target.getY(), c.z - 2);
        int before = p.getData(AscentContent.MEMORY).points(AscentMemory.Track.TETHER);
        AscentServer.strikeBlow(p, m, 99F);
        h.assertTrue(target.getHealth() < full, "at the creature the blow lands: " + (full - target.getHealth()));
        h.assertTrue(AirRules.strikeDamage(1, 99) == 1 + (float) AirRules.STRIKE_BONUS_MAX && AirRules.strikeDamage(1, 0) == 1F
                && AirRules.strikeDamage(1, 1) > 1F, "the hand's damage and more for speed, up to the cap");
        h.assertTrue(p.getData(AscentContent.MEMORY).points(AscentMemory.Track.TETHER) > before
                && dev.awaken.progression.Progression.profile(p).counter(AscentGoals.STRUCK) == 1, "it is a link, and counted once made");
        float after = target.getHealth();
        AscentServer.strikeBlow(p, m, 1.5F);
        h.assertTrue(target.getHealth() == after, "one strike, one blow");
        // the pull: straight in, arriving, then off it upward with the air moves back (the client's own, by the same rule)
        Kin k = new Kin().set(0, 0, 0, 0.2, 0, 0.9);
        int ticks = 0;
        while (!AirRules.strikeStep(k, 0, 0, 20) && ticks < AirRules.STRIKE_TICKS) ticks++;
        h.assertTrue(ticks < 30 && Math.abs(k.x) < 1.5 && k.speed() <= AirRules.STRIKE_SPEED + 0.01, "the pull arrives in " + ticks + " ticks, drawn onto the line ("
                + k.x + ")");
        AirRules.strikeOff(k);
        h.assertTrue(k.vy == AirRules.STRIKE_UP && k.hspeed() < 0.5, "off the blow upward");
        h.succeed();
    }

    /** The wings' form: the beat ends where it began and never jumps; the far forms are the budget the plan gave. */
    private static void wingForm(GameTestHelper h) {
        float[] a = new float[WingForm.N], b = new float[WingForm.N];
        for (float[] base : new float[][] {WingForm.GLIDE, WingForm.HOVER}) {
            float period = base == WingForm.GLIDE ? WingForm.FLY_PERIOD : WingForm.HOVER_PERIOD;
            WingForm.beat(base, 0, period, 1, 1, a);
            WingForm.beat(base, period - 1e-4F, period, 1, 1, b);
            for (int i = 0; i < WingForm.N; i++) {
                h.assertTrue(Math.abs(a[i] - b[i]) < 0.05, "the beat closes on itself at term " + i + ": " + a[i] + " against " + b[i]);
            }
            // a frame at 60 a second never moves a joint more than a few degrees (the first beat jumped 30 where it looped)
            float worst = 0;
            WingForm.beat(base, 0, period, 1, 1, a);
            for (int f = 1; f <= 120; f++) {
                WingForm.beat(base, f / 60F, period, 1, 1, b);
                for (int i = 0; i < WingForm.N; i++) worst = Math.max(worst, Math.abs(b[i] - a[i]));
                System.arraycopy(b, 0, a, 0, WingForm.N);
            }
            h.assertTrue(worst < 6, "no term of the beat moves more than 6 in a frame: " + worst);
        }
        // the opening ends open, the flare ends cupped, and a feather's flap grows out along the span
        WingForm.unfold(WingForm.UNFOLD + 2, a);
        h.assertTrue(Math.abs(a[WingForm.A0] - WingForm.OPEN[WingForm.A0]) < 0.5 && a[WingForm.CASC] == 1, "the opening settles open");
        WingForm.land(3, a);
        h.assertTrue(Math.abs(a[WingForm.P0] - WingForm.FLARE[WingForm.P0]) < 0.5, "the flare settles cupped forward");
        h.assertTrue(WingForm.flap(10, 20, 30, 0) == 10 && WingForm.flap(10, 20, 30, 3) == 60 && WingForm.flap(10, 20, 30, 1.05F) > 10
                && WingForm.flap(10, 20, 30, 1.05F) < 30, "a feather takes the flap of its own place on the span");
        int near = WingForm.boxes(0), mid = WingForm.boxes(1), far = WingForm.boxes(2);
        h.assertTrue(near >= 220 && near <= 240, "about 230 boxes a wing up close: " + near);
        h.assertTrue(mid < near / 3 && far == 3 + 6 * 4, "every other feather past 48 blocks (" + mid + "), three bones and six slabs past 96 (" + far + ")");
        h.assertTrue(WingForm.lod(40 * 40) == 0 && WingForm.lod(60 * 60) == 1 && WingForm.lod(120 * 120) == 2, "the far forms begin at 48 and 96 blocks");
        h.assertTrue(WingForm.PAIRS_WINGS.length == 1 && WingForm.PAIRS_SKYBORN.length == 2 && WingForm.PAIRS_SOVEREIGN.length == 3,
                "one pair, two for Skyborn, three for the Sovereign's form");
        h.succeed();
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-6;
    }
}
