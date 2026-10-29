package dev.awaken.arms;

import dev.awaken.core.test.AwakenTests;
import net.minecraft.gametest.framework.GameTestHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.StringReader;
import java.util.Set;

/** Swing Studio (SPEC §25 "Swing Studio"): the chain blend, the swings' reload, and the feel rules of the baked file. */
final class SwingTests {
    private SwingTests() {}

    static void register() {
        AwakenTests.register("arms_swing_blend", 20, SwingTests::blend);
        AwakenTests.register("arms_swing_reload", 20, SwingTests::reload);
        AwakenTests.register("arms_feel_rules", 20, SwingTests::feelRules);
        AwakenTests.register("arms_chain_flow", 20, SwingTests::chainFlow);
    }

    /**
     * The blend: the pose on screen is carried into a new source without a jump (the first frame shows what the last
     * did), reaches the new source by the blend's end, never swings past it, and leaves with the old motion.
     */
    static void blend(GameTestHelper h) {
        SwingBlend.Curve c = new SwingBlend.Curve();
        c.set(1F, -2F, 3F);
        h.assertTrue(Math.abs(c.at(0) - 1F) < 1e-5F, "the decay starts at the gap");
        h.assertTrue(Math.abs((c.at(0.001F) - c.at(0)) / 0.001F + 2F) < 0.05F, "and leaves with the old motion");
        float min = 1F;
        for (float t = 0; t <= c.length() + 0.5F; t += 0.01F) min = Math.min(min, c.at(t));
        h.assertTrue(min > -1e-3F, "it never swings past the new pose (" + min + ")");
        h.assertTrue(c.at(c.length()) == 0F && c.length() <= 3F, "and is done by its end");
        c.set(1F, 5F, 2F);
        h.assertTrue(Math.abs(c.at(0.001F) - c.at(0)) < 1e-3F, "a motion away from the new pose is dropped, not carried further off");

        SwingBlend b = new SwingBlend();
        Vector3f d = new Vector3f(), zero = new Vector3f();
        Quaternionf q = new Quaternionf(), id = new Quaternionf();
        // shown: a swing passing (0.3, -0.2, 0) turned 90 degrees, moving left
        for (int i = 0; i <= 4; i++) {
            d.set(0.3F - 0.05F * i, -0.2F, 0);
            q.identity().rotateZ((float) Math.toRadians(90));
            b.apply(i * 0.25F, d, q);
        }
        float now = 1F;
        b.begin(now, zero, id, zero, zero, 2.5F); // a chained strike starts from its rest
        d.zero();
        q.identity();
        b.apply(now + 0.001F, d, q);
        h.assertTrue(Math.abs(d.x - 0.1F) < 0.01F && Math.abs(d.y + 0.2F) < 0.01F, "the first frame is where the blade was (" + d + ")");
        h.assertTrue(Math.abs(Math.toDegrees(2 * Math.acos(Math.min(1, Math.abs(q.w)))) - 90) < 1, "and turned as it was");
        d.zero();
        q.identity();
        b.apply(now + 3F, d, q);
        h.assertTrue(d.length() < 1e-5F && Math.abs(Math.abs(q.w) - 1) < 1e-5F && !b.active(now + 3F), "the new source by the end");

        SwingBlend.Vec v = new SwingBlend.Vec();
        Vector3f lean = new Vector3f(2, 4, 1);
        v.apply(0, lean);
        v.begin(0.25F, new Vector3f(), new Vector3f(), 3F);
        Vector3f at = new Vector3f();
        v.apply(0.26F, at);
        h.assertTrue(at.distance(2, 4, 1) < 0.05F, "the view's lean is carried too, never snapped level (" + at + ")");
        h.succeed();
    }

    private static final String MINI = """
            {"schemaVersion": %d, "round": %d, "dt": 0.25, "styles": {"longsword": {"grip": [0,0,0], "vanilla": [0,0,0,1],
             "vanillaGrip": [0,0,0], "moves": {"strike_1": {"keys": [[0, 0.1,0,0, 0,0,0,1, 0,0,0], [0.25, 0,0,0, 0,0,0,1, 0,0,0]],
             "swish": 1.25, "bite": [0.4, 0.75]}}}}}
            """;

    /**
     * The reload: a whole file read into a set, put in use and taken back out; a newer schema refused whole; the
     * studio's fields read (when the swish starts, the bite).
     */
    static void reload(GameTestHelper h) {
        FpSwings.Data jar = FpSwings.jarData();
        h.assertTrue(jar.schema() == FpSwings.SCHEMA, "the jar's file is this build's schema (" + jar.schema() + ")");
        FpSwings.Data mini = FpSwings.parse(new StringReader(MINI.formatted(FpSwings.SCHEMA, jar.round() + 1)));
        FpSwings.Clip c = mini.styles().get("longsword").moves().get(Archetype.Move.STRIKE_1);
        h.assertTrue(c.swish() == 1.25F && c.biteRate() == 0.4F && c.biteTicks() == 0.75F, "the swish and the bite are read");
        boolean refused = false;
        try {
            FpSwings.parse(new StringReader(MINI.formatted(FpSwings.SCHEMA + 1, 1)));
        } catch (IllegalArgumentException e) {
            refused = true;
        }
        h.assertTrue(refused, "a newer schema is refused, never half-read");
        try {
            FpSwings.swap(mini);
            h.assertTrue(FpSwings.current() == mini && FpSwings.style("katana") == null, "a swapped set is the one in use");
            Vector3f d = new Vector3f();
            FpSwings.sample(FpSwings.style("longsword"), Archetype.Move.STRIKE_1, 0, true, d, new Quaternionf());
            h.assertTrue(Math.abs(d.x - 0.1F) < 1e-6F, "and its keys are the ones sampled");
        } finally {
            FpSwings.swap(null);
        }
        h.assertTrue(FpSwings.current() == jar && FpSwings.style("katana") != null, "off: the jar's swings again");
        h.succeed();
    }

    private static final Set<String> TWO_HANDED = Set.of("katana", "greatblade", "maul", "spear", "glaive", "scythe", "warstaff",
            "twinblades");

    /**
     * The baked file's feel rules: every swish starts on the press or after it and before the blow's active ticks end,
     * so the whoosh never trails the blow; every bite slows the hands without stopping them; the two-handed weapons,
     * and only they, close the off hand on the handle below the main, a hand's width or more from it. Round 8: every
     * chain strike says where a press still down waits, at or before its hit; every style holds its guard with the hand
     * on the weapon in view (Euan 2026-10-29: "blocking with most weapons hides the weapon under your view").
     */
    static void feelRules(GameTestHelper h) {
        for (Archetypes.Loaded a : Archetypes.all()) {
            FpSwings.Style st = FpSwings.style(a.id());
            if (st == null) continue;
            for (var e : st.moves().entrySet()) {
                Archetype.MoveDef def = a.move(e.getKey());
                FpSwings.Clip c = e.getValue();
                if (def == null) continue;
                // the blow lands by the end of its active ticks (a charged heavy has no wind-up and lands inside them)
                h.assertTrue(c.swish() >= 0 && c.swish() <= def.windup() + Math.max(def.active(), 1) + 0.01F,
                        a.id() + " " + e.getKey().key + "'s swish starts by its hit (" + c.swish() + ")");
                h.assertTrue(c.biteTicks() == 0 || c.biteRate() > 0 && c.biteRate() <= 1, a.id() + " " + e.getKey().key + "'s bite slows, never stops");
                if (e.getKey().strike()) {
                    h.assertTrue(c.set() >= 0 && c.set() <= def.windup(), a.id() + " " + e.getKey().key + " waits at its set, by its hit (" + c.set() + ")");
                }
            }
            Vector3f d = new Vector3f(), at = new Vector3f();
            Quaternionf q = new Quaternionf();
            h.assertTrue(FpSwings.guard(st, true, d, q) && Math.abs(q.lengthSquared() - 1) < 1e-3F, a.id() + " has a guard");
            // the hand on the weapon in guard, as the eye sees it at a 70 degree view: on the screen, ahead of the eye
            q.transform(at.set(st.hand())).add(d).add(0.56F, -0.52F, -0.72F);
            float f = 1F / (float) Math.tan(Math.toRadians(35)), x = at.x / -at.z * f / (16F / 9F), y = at.y / -at.z * f;
            h.assertTrue(at.z < -0.2F && Math.abs(x) < 1 && Math.abs(y) < 1, a.id() + "'s guard holds its hand in view (" + x + ", " + y + ")");
            boolean two = TWO_HANDED.contains(a.id());
            h.assertTrue((st.offHand() != null) == two && (st.vanillaOffHand() != null) == two,
                    a.id() + (two ? " is held in both hands" : " is held in one"));
            if (two) {
                float gap = st.offHand().distance(st.hand());
                h.assertTrue(gap > 0.08F && gap < 0.6F, a.id() + "'s off hand is a hand's width or more down the handle (" + gap + ")");
            }
        }
        h.succeed();
    }

    /**
     * Round 3's chain (Euan 2026-10-28: "the second slice is not even visible"): each strike of a chain, struck by the
     * same hand as the one before it, starts where that one holds the blade on the earliest press it chains from, so
     * the blend has nothing to carry and the next blow is wound up in view, never from below the frame; round 4's: one
     * struck by the other hand of a pair starts where that hand drew back to while the last one landed.
     */
    static void chainFlow(GameTestHelper h) {
        Vector3f a = new Vector3f(), b = new Vector3f();
        Quaternionf qa = new Quaternionf(), qb = new Quaternionf();
        int links = 0, twins = 0;
        for (Archetypes.Loaded style : Archetypes.all()) {
            FpSwings.Style st = FpSwings.style(style.id());
            if (st == null) continue;
            java.util.List<Archetype.Move> chain = new java.util.ArrayList<>(style.chain());
            chain.addAll(style.masteryChain());
            for (int i = 0; i + 1 < chain.size(); i++) {
                Archetype.Move prev = chain.get(i), next = chain.get(i + 1);
                Archetype.MoveDef def = style.move(prev);
                if (def == null) continue;
                float press = def.hasCadence() ? def.cadence().get(0) : def.windup() + def.active() + ArmsRules.MASTERY_LINK;
                // the first strike begins a tick into the chain's count, so it hands over a tick early, never in its blow
                // (round 10)
                if (i == 0) press = Math.max(press - 1, def.windup() + def.active());
                // a both-hands strike's off hand drives in from where it waited (round 9, the daggers' strike 4)
                boolean offStruck = FpSwings.hand(st, prev) == FpSwings.Hand.OFF;
                if (FpSwings.hand(st, next) == FpSwings.Hand.BOTH && FpSwings.idle(st, next, 0, true, b, qb)
                        && (offStruck ? FpSwings.sample(st, prev, press, true, a, qa) : FpSwings.idle(st, prev, press, true, a, qa))) {
                    float turn = (float) Math.toDegrees(2 * Math.acos(Math.min(1F, Math.abs(qa.dot(qb)))));
                    h.assertTrue(a.distance(b) < 0.02F && turn < 3F,
                            style.id() + " " + next.key + "'s off hand starts where it waited after " + prev.key + " (" + a.distance(b) + ", " + turn + " degrees)");
                    twins++;
                }
                // the hand that throws the next strike (both hands play the main's): the same as this one's, or the
                // idle hand drawn back while this one struck (round 4, the daggers)
                boolean same = (FpSwings.hand(st, prev) == FpSwings.Hand.OFF) == (FpSwings.hand(st, next) == FpSwings.Hand.OFF);
                boolean there = same ? FpSwings.sample(st, prev, press, true, a, qa) : FpSwings.idle(st, prev, press, true, a, qa);
                if (!there || !FpSwings.sample(st, next, 0, true, b, qb)) continue;
                float turn = (float) Math.toDegrees(2 * Math.acos(Math.min(1F, Math.abs(qa.dot(qb)))));
                h.assertTrue(a.distance(b) < 0.02F && turn < 3F,
                        style.id() + " " + next.key + " starts where its hand is after " + prev.key + " (" + a.distance(b) + ", " + turn + " degrees)");
                links++;
            }
        }
        h.assertTrue(links > 30, "the chains' links are checked (" + links + ")");
        h.assertTrue(twins > 0, "a both-hands strike's off hand is checked (the daggers' strike 4)");
        // the Twin Blades' figure-eight (round 5): the ends take turns down the light chain, so the link above from a
        // strike to one led by the other end holds only because the first carried on round end for end by the press
        FpSwings.Style tw = FpSwings.style("twinblades");
        boolean[] far = new boolean[3];
        for (int i = 0; i < 3; i++) far[i] = FpSwings.far(tw, Archetype.Move.byKey("strike_" + (i + 1)));
        h.assertTrue(tw != null && !far[0] && far[1] && !far[2], "the twin blades' ends take turns: strike 2 leads with the other end");
        h.succeed();
    }
}
