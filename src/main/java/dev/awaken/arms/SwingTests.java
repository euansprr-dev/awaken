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

    private static final Set<String> TWO_HANDED = Set.of("katana", "greatblade", "maul", "spear", "glaive", "scythe", "warstaff");

    /**
     * The baked file's feel rules: every swish starts on the press or after it and before the blow's active ticks end,
     * so the whoosh never trails the blow; every bite slows the hands without stopping them; the two-handed weapons,
     * and only they, close the off hand on the handle below the main, a hand's width or more from it.
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
            }
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
}
