package dev.awaken.aim;

import dev.awaken.core.api.Aim;
import dev.awaken.core.test.AwakenTests;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Lock-on's rules held (SPEC §40): the mark's pick, the caps that keep the skill, and how far a power's aim bends. */
final class AimTests {
    private AimTests() {}

    static void register() {
        AwakenTests.register("aim_soft_pick", 20, AimTests::softPick);
        AwakenTests.register("aim_caps", 20, AimTests::caps);
        AwakenTests.register("aim_power_bend_cap", 20, AimTests::bendCap);
    }

    /** The nearest the middle wins, a nearer creature wins a close call, the current mark holds, and the cone and range end it. */
    static void softPick(GameTestHelper h) {
        float cone = AimRules.MARK_CONE, range = AimRules.MARK_RANGE;
        h.assertTrue(AimRules.better(AimRules.score(3F, 6, false, cone, range), AimRules.score(12F, 6, false, cone, range)), "nearer the middle wins");
        h.assertTrue(AimRules.better(AimRules.score(5F, 4, false, cone, range), AimRules.score(4F, 20, false, cone, range)), "a close call goes to the nearer creature");
        h.assertTrue(AimRules.better(AimRules.score(10F, 6, true, cone, range), AimRules.score(4F, 6, false, cone, range)), "the mark holds against one a little nearer the middle");
        h.assertTrue(!AimRules.better(AimRules.score(14F, 6, true, cone, range), AimRules.score(2F, 6, false, cone, range)), "but not against one much nearer");
        h.assertTrue(Float.isNaN(AimRules.score(cone + 1F, 6, false, cone, range)), "outside the cone: no mark");
        h.assertTrue(Float.isNaN(AimRules.score(2F, range + 1, false, cone, range)), "out of range: no mark");
        // the edge counts: a big creature off to the side is nearer the middle than its centre says
        AABB big = new AABB(-1.5, 0, 8, 1.5, 4, 11).move(3, 0, 0);
        float off = Aim.offEdge(Vec3.ZERO.add(0, 1.6, 0), big, 0F, 0F);
        h.assertTrue(off < Aim.angle(0F, 0F, 3, 0.4, 9.5), "the box's edge is nearer than its centre (" + off + "°)");
        h.succeed();
    }

    /** The view turns at most 300°/s, eased, the short way round; locked, the mouse keeps a quarter; no 360° snaps. */
    static void caps(GameTestHelper h) {
        float frame = 1F / 60F;
        h.assertTrue(Math.abs(AimRules.turnStep(170F, frame)) <= AimRules.TURN_MAX * frame + 1.0E-4F, "one frame turns at most 300°/s");
        h.assertTrue(AimRules.turnStep(190F, frame) < 0F, "190° to the right is turned as 170° to the left");
        h.assertTrue(AimRules.turnStep(AimRules.TURN_DEAD * 0.5F, frame) == 0F, "inside the dead zone the view rests");
        float gap = 180F, turned = 0F;
        for (int i = 0; i < 60; i++) {
            float step = AimRules.turnStep(gap, frame);
            gap -= step;
            turned += Math.abs(step);
        }
        h.assertTrue(turned <= AimRules.TURN_MAX + 0.01F, "a second turns at most 300°, turned " + turned);
        float near = AimRules.turnStep(20F, frame);
        h.assertTrue(near > 0 && near < AimRules.turnStep(120F, frame), "it eases: a small gap closes slower than a big one");
        for (double sens : new double[] {0.0, 0.5, 1.0}) {
            double ss = sens * 0.6 + 0.2, kept = AimRules.keepSensitivity(sens, AimRules.MOUSE_KEEP) * 0.6 + 0.2;
            double ratio = (kept * kept * kept) / (ss * ss * ss);
            h.assertTrue(Math.abs(ratio - AimRules.MOUSE_KEEP) < 1.0E-6, "the mouse keeps a quarter at sensitivity " + sens + ": " + ratio);
        }
        h.assertTrue(Aim.AIM_CAP <= 35F && Aim.PERSON_CAP < Aim.AIM_CAP, "assist never reaches past 35°, people far less");
        h.assertTrue(Aim.stepIn("maul") < Aim.stepIn("katana") && Aim.stepIn("katana") < Aim.stepIn("daggers"), "light weapons step further");
        // a flick: 80 px in 50 ms is one; 80 px over half a second is a turn
        float sum = 0;
        for (int i = 0; i < 5; i++) sum = AimRules.flickSum(sum, 16F, 10F);
        h.assertTrue(Math.abs(sum) >= AimRules.FLICK_PX, "80 px in 50 ms is a flick (" + sum + ")");
        sum = 0;
        for (int i = 0; i < 30; i++) sum = AimRules.flickSum(sum, 80F / 30F, 16.7F);
        h.assertTrue(Math.abs(sum) < AimRules.FLICK_PX, "80 px over half a second isn't (" + sum + ")");
        h.succeed();
    }

    /** A power bends toward the creature only inside its cap, and then aims straight at it; a bigger gap is left alone. */
    static void bendCap(GameTestHelper h) {
        float[] out = new float[2];
        AimRules.bend(10F, 5F, 16F, 2F, AimRules.BEND_POWER, out);
        h.assertTrue(Math.abs(out[0] - 16F) < 1.0E-3 && Math.abs(out[1] - 2F) < 1.0E-3, "inside 10°: straight at it");
        AimRules.bend(10F, 5F, 25F, 5F, AimRules.BEND_POWER, out);
        h.assertTrue(out[0] == 10F && out[1] == 5F, "15° off: not bent");
        AimRules.bend(175F, 0F, -178F, 0F, AimRules.BEND_POWER, out);
        h.assertTrue(Math.abs(out[0] - 182F) < 1.0E-3, "across ±180 the short way (" + out[0] + ")");
        int pick = AimRules.pickSide(new float[] {-30F, 12F, 40F, 5F}, new float[] {0F, 2F, 0F, -30F}, 4, 1F, 0F);
        h.assertTrue(pick == 1, "a flick right picks the nearest creature to the right, picked " + pick);
        h.assertTrue(AimRules.pickSide(new float[] {-30F}, new float[] {0F}, 1, 1F, 0F) == -1, "nothing that way: no switch");
        h.succeed();
    }
}
