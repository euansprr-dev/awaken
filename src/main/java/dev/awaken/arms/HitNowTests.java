package dev.awaken.arms;

import dev.awaken.arms.net.ArmsPayloads;
import dev.awaken.core.test.AwakenTests;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Random;

/**
 * SPEC §47.1 "Hits land now": the client plays a blow on the frame its blade arrives, by the server's own sweep rule a
 * little inside it ({@link ArmsRules#predicts}). Checked: a blow the client plays is one the server's sweep strikes,
 * even after the body moved for a tick and the look turned; the server's sweep (the same rule) strikes it in a real
 * move; and the ledger plays each blow once and counts the ones the server never confirms.
 */
final class HitNowTests {
    private HitNowTests() {}

    /** A tick's travel of a quick creature (blocks) and a quick turn of the look in that tick (degrees). */
    static final double DRIFT = 0.35;
    static final float TURN = 5F;
    static final int TRIES = 3000;

    static void register() {
        AwakenTests.register("arms_hit_now", 60, HitNowTests::hitNow);
        AwakenTests.register("arms_hit_why", 60, HitNowTests::hitWhy);
    }

    /**
     * SPEC §25 "Weight in the Hand", stage 0: a hit shown and never confirmed is given its cause from the server's own
     * record. Checked: the nearest the server came to landing is what is kept; each cause is told from the others; nothing
     * is kept while off; the ledger tells each ghost once; and the real sweep and blow write the record.
     */
    static void hitWhy(GameTestHelper h) {
        boolean was = HitWhy.on;
        HitWhy.on = true;
        HitWhy.clear();
        try {
            StringBuilder say = new StringBuilder();
            long shown = System.nanoTime();
            HitWhy.press(5, 100, HitWhy.RAN);
            HitWhy.body(5, 70, 100, HitWhy.REACH, 4.4F, 3.3F);
            HitWhy.body(5, 70, 101, HitWhy.REACH, 4.1F, 3.3F);
            HitWhy.body(5, 70, 102, HitWhy.REACH, 4.6F, 3.3F);
            HitWhy.Why w = HitWhy.explain(5, 70, shown, say);
            h.assertTrue(w.cause() == HitWhy.Cause.OUT_OF_REACH && Math.abs(w.beyond() - 0.8F) < 0.01F,
                    "out of reach, by the nearest the body came: " + w + " (" + say + ")");
            HitWhy.body(5, 70, 103, HitWhy.ARC, 12F, 100F);
            h.assertTrue(HitWhy.explain(5, 70, shown, say).cause() == HitWhy.Cause.OUT_OF_ARC, "in reach and outside the arc is the nearer miss");
            HitWhy.body(5, 70, 104, HitWhy.NO_DAMAGE, 14F, 0F);
            HitWhy.body(5, 70, 105, HitWhy.REACH, 9F, 3.3F);
            h.assertTrue(HitWhy.explain(5, 70, shown, say).cause() == HitWhy.Cause.NO_DAMAGE, "struck with no damage outlasts any miss");
            HitWhy.body(4, 71, 99, HitWhy.LANDED, 0F, 0F);
            h.assertTrue(HitWhy.explain(5, 71, shown, say).cause() == HitWhy.Cause.OTHER_PRESS, "a blow landed on the body under the press before is the same blow");
            h.assertTrue(HitWhy.explain(4, 71, shown, say).cause() == HitWhy.Cause.UNMATCHED, "landed under this very press: the word was lost");
            h.assertTrue(HitWhy.explain(5, 72, shown, say).cause() == HitWhy.Cause.NOT_NEAR, "the move ran and the body was nowhere near");
            HitWhy.press(6, 110, HitWhy.TOO_EARLY);
            h.assertTrue(HitWhy.explain(6, 72, shown, say).cause() == HitWhy.Cause.NEVER_RAN, "a press the server took as too early ran no move");
            HitWhy.press(7, 111, HitWhy.QUEUED);
            HitWhy.press(7, 112, HitWhy.RAN);
            HitWhy.press(7, 113, HitWhy.QUEUED);
            h.assertTrue(HitWhy.explain(7, 72, shown, say).cause() == HitWhy.Cause.NOT_NEAR, "a queued strike that ran has run");
            HitWhy.clear();
            HitWhy.on = false;
            HitWhy.body(8, 70, 120, HitWhy.LANDED, 0F, 0F);
            h.assertTrue(HitWhy.explain(8, 70, shown, say).cause() == HitWhy.Cause.NO_RECORD, "nothing is kept while off");

            HitLedger l = new HitLedger();
            int[] told = new int[3];
            l.ghosts = (seq, body) -> {
                told[0]++;
                told[1] = seq;
                told[2] = body;
            };
            l.played(2, 9, 5, 200L, 60);
            l.played(3, 9, 5, 200L, 60);
            l.confirm(3, 9, 6);
            l.expire(5 + HitLedger.LIFE + 1);
            h.assertTrue(told[0] == 1 && told[1] == 2 && told[2] == 9, "the ledger tells the one ghost, once: " + told[0] + " (" + told[1] + ", " + told[2] + ")");

            // the real server: one zombie in reach is struck, one a block and more past the reach is explained
            HitWhy.on = true;
            ServerPlayer p = ArmsTests.fighter(h, 1.5, 0.5, 0F);
            LivingEntity near = ArmsTests.put(h, EntityTypes.ZOMBIE, 1.5, 2.6, 200), far = ArmsTests.put(h, EntityTypes.ZOMBIE, 1.5, 5.4, 200);
            ArmsServer.onPress(p, new ArmsPayloads.Press(1, ArmsPayloads.PRESS_STRIKE, 0F, 0F, -1));
            ArmsServer.onPress(p, new ArmsPayloads.Press(1, ArmsPayloads.PRESS_TAP, 0F, 0F, -1));
            ArmsTests.runActive(p);
            h.assertTrue(near.getHealth() < 200 && far.getHealth() == 200, "the sweep strikes the near one only");
            HitWhy.Why a = HitWhy.explain(1, near.getId(), System.nanoTime(), say), b = HitWhy.explain(1, far.getId(), System.nanoTime(), say);
            h.assertTrue(a.cause() == HitWhy.Cause.UNMATCHED, "the blow that landed is on record: " + a);
            h.assertTrue(b.cause() == HitWhy.Cause.OUT_OF_REACH && b.beyond() > 0.5F && b.beyond() < 2.5F, "the far one is out of reach, by about a block: " + b + " (" + say + ")");
            ArmsTests.done(h, p);
        } finally {
            HitWhy.on = was;
            HitWhy.clear();
        }
    }

    static void hitNow(GameTestHelper h) {
        // the rule, over every move of every style: whatever the client strikes the server strikes, and moved a tick
        // later the server still does (under 1 in 50 shown without damage)
        Random r = new Random(46);
        int played = 0, missed = 0, struck = 0, playedOfStruck = 0;
        Vec3 eye = new Vec3(0, 1.62, 0);
        for (Archetypes.Loaded st : Archetypes.all()) {
            for (Map.Entry<Archetype.Move, Archetype.MoveDef> e : st.moves().entrySet()) {
                Archetype.MoveDef def = e.getValue();
                double reach = def.reach() + 0.3;
                for (int i = 0; i < TRIES / 4; i++) {
                    double d = r.nextDouble() * (reach + 1.2) + 0.3, a = (r.nextDouble() * 2 - 1) * (def.arc() / 2 + 30) * Mth.DEG_TO_RAD;
                    double cx = -Math.sin(a) * (d + 0.3), cz = Math.cos(a) * (d + 0.3), y = (r.nextDouble() - 0.4) * 1.2;
                    AABB box = new AABB(cx - 0.3, y, cz - 0.3, cx + 0.3, y + 1.95, cz + 0.3);
                    boolean client = ArmsRules.predicts(eye, 0, box, 0F, def.arc(), reach, false, false);
                    boolean server = ArmsRules.sweeps(eye, 0, box, 0F, def.arc(), reach, false, false);
                    h.assertTrue(!client || server, st.id() + " " + e.getKey() + ": the client strikes what the server's sweep doesn't");
                    if (server) struck++;
                    if (server && client) playedOfStruck++;
                    if (!client) continue;
                    played++;
                    double m = r.nextDouble() * DRIFT, ma = r.nextDouble() * Mth.TWO_PI;
                    AABB moved = box.move(Math.cos(ma) * m, 0, Math.sin(ma) * m);
                    float yaw = (r.nextFloat() * 2 - 1) * TURN;
                    if (!ArmsRules.sweeps(eye, 0, moved, yaw, def.arc(), reach, false, false)) missed++;
                }
            }
        }
        double missRate = missed / (double) Math.max(1, played), share = playedOfStruck / (double) Math.max(1, struck);
        System.out.printf("[arms_hit_now] played at once %d, shown without damage %d (%.2f%%); %.0f%% of the server's hits played at once%n",
                played, missed, missRate * 100, share * 100);
        h.assertTrue(missRate < 0.02, "a tick's drift leaves " + String.format("%.1f%%", missRate * 100) + " of played blows unstruck (want under 2%)");
        h.assertTrue(share > 0.6, "only " + String.format("%.0f%%", share * 100) + " of the server's hits are played at once");

        // the ledger: each blow once; the server's word on a blow played adds, never replays; a blow it never confirms is counted
        HitLedger l = new HitLedger();
        l.played(1, 7, 0, 100L, 60);
        h.assertTrue(l.confirm(1, 7, 1), "the server's HIT for a blow played is not played again");
        h.assertTrue(l.freezeMs(1, 7) == 60, "the played blow's freeze is kept for a kill's beat");
        h.assertTrue(!l.confirm(1, 8, 1), "a blow the client didn't play is the server's to play");
        h.assertTrue(l.has(1, 8), "and the client's sweep then leaves that body be for this move");
        l.played(2, 9, 5, 200L, 60);
        h.assertTrue(l.expire(5 + HitLedger.LIFE + 1) == 1, "a blow never confirmed is counted once it's old");
        h.assertTrue(l.predicted == 2 && l.confirmed == 1 && l.unconfirmed == 1,
                "counted: played " + l.predicted + ", confirmed " + l.confirmed + ", unconfirmed " + l.unconfirmed);

        // and in a real move: a zombie the client would strike, the server's sweep strikes
        ServerPlayer p = ArmsTests.fighter(h, 1.5, 0.5, 0F);
        LivingEntity z = ArmsTests.put(h, EntityTypes.ZOMBIE, 1.5, 2.6, 200);
        Archetype.MoveDef s1 = ArmsTests.longsword().move(Archetype.Move.STRIKE_1);
        h.assertTrue(ArmsRules.predicts(p.getEyePosition(), p.getY(), z.getBoundingBox(), p.getYRot(), s1.arc(), s1.reach() + 0.3, false, false),
                "the client plays this blow at once");
        ArmsServer.onPress(p, new ArmsPayloads.Press(1, ArmsPayloads.PRESS_STRIKE, 0F, 0F, -1));
        ArmsServer.onPress(p, new ArmsPayloads.Press(1, ArmsPayloads.PRESS_TAP, 0F, 0F, -1));
        ArmsTests.runActive(p);
        h.assertTrue(z.getHealth() < 200, "the server's sweep strikes it too");
        ArmsTests.done(h, p);
    }
}
