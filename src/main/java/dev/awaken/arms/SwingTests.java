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
        AwakenTests.register("arms_draw_smooth", 20, SwingTests::drawSmooth);
        AwakenTests.register("arms_dagger_stabs", 20, SwingTests::daggerStabs);
        AwakenTests.register("arms_gauntlet_sleeves", 20, SwingTests::gauntletSleeves);
        AwakenTests.register("arms_shieldblade_two_parts", 20, SwingTests::shieldbladeTwoParts);
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

        // a month after the machine booted (the hands' clock counts from then): the same blend, on the same frames
        double month = 5.4E7;
        SwingBlend late = new SwingBlend();
        for (int i = 0; i <= 4; i++) {
            d.set(0.4F, 0, 0);
            q.identity();
            late.apply(month + i * 0.25, d, q);
        }
        late.begin(month + 1, zero, id, zero, zero, 2F);
        d.zero();
        q.identity();
        late.apply(month + 1.001, d, q);
        h.assertTrue(Math.abs(d.x - 0.4F) < 0.01F, "a month after boot the first frame is still the last one shown (" + d.x + ")");
        d.zero();
        q.identity();
        late.apply(month + 2, d, q);
        h.assertTrue(d.x > 0.02F && d.x < 0.38F, "and the blend still decays frame by frame (" + d.x + ")");

        // a hand that doesn't move across the change (a shield's shove leaves the sword at rest), its turn not quite a unit
        // from rounding: no gap to carry, and no axis to carry it about. Its pose was NaN for the whole blend, so the sword
        // left the screen for all of a held heavy's draw
        SwingBlend same = new SwingBlend();
        Quaternionf stance = new Quaternionf(0.3F, 0.4F, 0.5F, 0.7F), shown = new Quaternionf();
        Vector3f restAt = new Vector3f(0.2F, -0.1F, 0.3F), shownAt = new Vector3f();
        for (int i = 0; i <= 2; i++) same.apply(i * 0.25, shownAt.set(restAt), shown.set(stance));
        same.begin(1, restAt, stance, zero, zero, 9F);
        same.apply(1.5, shownAt.set(restAt), shown.set(stance));
        h.assertTrue(Float.isFinite(shownAt.x) && Float.isFinite(shown.x) && Float.isFinite(shown.w) && shown.equals(stance, 1e-4F),
                "a hand that stays where it is stays on the screen (" + shown + ")");

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
     * and only they, close the off hand on the handle below the main, a hand's width or more from it. Round 8: every
     * chain strike says where its drive begins, at or before its hit; every style holds its guard with the hand
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
                    h.assertTrue(c.set() >= 0 && c.set() <= def.windup(), a.id() + " " + e.getKey().key + "'s drive begins by its hit (" + c.set() + ")");
                }
                // round 14: a move's own ribbon ticks are its stroke alone, through the hit and never on into the way home
                float hit = def.windup() > 0 ? def.windup() : def.active() * 0.5F;
                h.assertTrue(c.trailTo() < 0 || c.trailFrom() <= hit && hit <= c.trailTo() && c.trailTo() <= hit + 0.5F,
                        a.id() + " " + e.getKey().key + "'s ribbon is its stroke alone (" + c.trailFrom() + ".." + c.trailTo() + ", hit " + hit + ")");
                h.assertTrue(!a.id().equals("daggers") || c.trailTo() >= 0, "the Daggers' " + e.getKey().key + " has its own ribbon ticks");
            }
            float[] look = st.trail();
            h.assertTrue(look == null || look[0] >= 0.5F && look[0] < 1F && look[1] > 0 && look[1] <= 1F, a.id() + "'s ribbon look is a band toward the point");
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
        // the Twin Blades (SPEC §25 "The Twin Blades"): one hand, and every strike whole turns about the middle of the
        // shaft, with no flip in them (a solver flips on a full spin) and home where they set off
        FpSwings.Style tw = FpSwings.style("twinblades");
        h.assertTrue(tw != null && tw.offHand() == null, "the twin blades are twirled in one hand");
        for (int s = 1; s <= 5; s++) {
            Archetype.Move m = Archetype.Move.byKey("strike_" + s);
            FpSwings.sample(tw, m, 0, true, a, qa);
            Quaternionf start = new Quaternionf(qa), last = new Quaternionf(qa);
            float turned = 0F, most = 0F;
            for (float t = 0.25F; t < 14F; t += 0.25F) {
                FpSwings.sample(tw, m, t, true, b, qb);
                float turn = (float) Math.toDegrees(2 * Math.acos(Math.min(1F, Math.abs(last.dot(qb)))));
                turned += turn;
                most = Math.max(most, turn);
                last.set(qb);
            }
            float home = (float) Math.toDegrees(2 * Math.acos(Math.min(1F, Math.abs(start.dot(last)))));
            h.assertTrue(most < 62F, "twin blades strike " + s + " has no flip in its turn (" + most + " degrees in a key)");
            h.assertTrue(turned > 340F, "twin blades strike " + s + " turns the whole way round (" + turned + " degrees)");
            h.assertTrue(home < 3F, "twin blades strike " + s + " ends where it set off (" + home + " degrees off)");
        }
        h.succeed();
    }

    /** One frame at 60 fps, in ticks. */
    private static final float FRAME = 1F / 3F;

    /**
     * The draw (SPEC §25 "Tap or hold"; Euan 2026-11-09: "it just glitches into its form"): for every style, from rest and
     * from the earliest press out of each chain strike's recovery, the hand goes to the charge form on one curve with no
     * frame stepping further than that curve or the motion it was already in; the draw and the charge are one source, so
     * the mark changes nothing; and it arrives by the mark. A tap's clock meets its hit with no step. A guard against the
     * old swap, not a measure of how it looks.
     */
    static void drawSmooth(GameTestHelper h) {
        h.assertTrue(ChargeDraw.source(true, 123L) == ChargeDraw.source(true, 456L), "the charge's mark starts no new source");
        h.assertTrue(ChargeDraw.source(false, 123L) != ChargeDraw.source(true, 123L), "a move is another source than the draw");
        h.assertTrue(ChargeDraw.handsFree(false, true, 2F, 4) == false && ChargeDraw.handsFree(false, true, 4F, 4), "a blow still landing goes through first");
        h.assertTrue(ChargeDraw.GRACE * 50F >= 150F && !ChargeDraw.drawing(true, true, false, true, ChargeDraw.GRACE - 0.01F)
                && ChargeDraw.drawing(true, true, false, true, ChargeDraw.GRACE), "a press moves nothing until it has outlasted a click");
        Vector3f form = new Vector3f(), d = new Vector3f(), was = new Vector3f(), zero = new Vector3f();
        Quaternionf formQ = new Quaternionf(), q = new Quaternionf(), wasQ = new Quaternionf(), id = new Quaternionf();
        int runs = 0;
        for (Archetypes.Loaded style : Archetypes.all()) {
            FpSwings.Style st = FpSwings.style(style.id());
            Archetype.MoveDef heavy = style.move(Archetype.Move.HEAVY);
            if (st == null || heavy == null || heavy.charge().isEmpty()) continue;
            float draw = ChargeDraw.ticks(heavy.charge().get().glint());
            h.assertTrue(ChargeDraw.GRACE + draw < heavy.charge().get().glint(), style.id() + "'s draw arrives before its glint");
            h.assertTrue(FpSwings.sample(st, Archetype.Move.HEAVY, 0, true, form, formQ), style.id() + " has a charge form");
            java.util.List<Archetype.Move> from = new java.util.ArrayList<>(style.chain());
            from.add(null); // from rest
            for (Archetype.Move prev : from) {
                Archetype.MoveDef def = prev == null ? null : style.move(prev);
                float press = def == null ? 0F : def.hasCadence() ? def.cadence().get(0) : def.windup() + def.active() + ArmsRules.MASTERY_LINK;
                SwingBlend b = new SwingBlend();
                float now = 0F;
                float carried = 0F, turned = 0F;
                for (int i = -6; i <= 0; i++, now += FRAME) {
                    if (prev != null) FpSwings.sample(st, prev, press + i * FRAME, true, d, q);
                    else FpSwings.stance(st, true, d.zero(), q.identity());
                    b.apply(now, d, q);
                    if (i > -6) {
                        carried = d.distance(was);
                        turned = degrees(q, wasQ);
                    }
                    was.set(d);
                    wasQ.set(q);
                }
                float gap = was.distance(form), turn = degrees(wasQ, formQ);
                b.begin(now, form, formQ, zero, zero, draw);
                // the curve's fastest frame, or the one the hand came in on, and a little for the blend's own rounding
                float maxStep = Math.max(carried, 1.875F * gap / draw * FRAME) * 1.1F + 2e-3F;
                float maxTurn = Math.max(turned, 1.875F * turn / draw * FRAME) * 1.1F + 0.3F;
                String what = style.id() + " from " + (prev == null ? "rest" : prev.key);
                for (float t = 0; t <= draw + 3F; t += FRAME, now += FRAME) {
                    d.set(form);
                    q.set(formQ);
                    b.apply(now, d, q);
                    h.assertTrue(d.distance(was) <= maxStep, what + ": no step across the draw at " + t + " (" + d.distance(was) + " > " + maxStep + ")");
                    h.assertTrue(degrees(q, wasQ) <= maxTurn, what + ": no turn across the draw at " + t + " (" + degrees(q, wasQ) + " > " + maxTurn + ")");
                    was.set(d);
                    wasQ.set(q);
                    if (t >= draw) {
                        h.assertTrue(d.distance(form) < 2e-3F && degrees(q, formQ) < 0.5F, what + ": in the charge form by the draw's end (" + d.distance(form) + " off, " + degrees(q, formQ) + " degrees, " + t + " of " + draw + ")");
                    }
                }
                runs++;
            }
            for (float drawn = 0F; drawn <= 1F; drawn += 0.25F) {
                for (Archetype.Move m : style.chain()) {
                    float w = style.move(m).windup(), ticks = ChargeDraw.launchTicks(w, drawn), rate = ChargeDraw.launchRate(w, drawn);
                    h.assertTrue(ticks >= 0.5F && Math.abs(ChargeDraw.clock(ticks, w, rate) - w) < 1e-3F
                                    && Math.abs(ChargeDraw.clock(ticks + 1e-3F, w, rate) - w) < 2e-2F,
                            style.id() + " " + m.key + ": a tap's clock meets its hit at the launch's end, with no step");
                }
            }
        }
        h.assertTrue(runs >= 48, "every style's draw is checked (" + runs + ")");
        h.succeed();
    }

    /** The first-person framing the Daggers' places were written in (fov 70, 16:9), and vanilla's resting hand. */
    private static final float VIEW_TAN = 0.7002F, VIEW_ASPECT = 16F / 9F;

    /**
     * The Daggers' chain (SPEC §25 "The Daggers"; Euan 2026-11-09): strikes 3 and 5 are the slashes and the rest stab; at each
     * blow the fist is low and on its own side ("the hands still go way too upwards"); a slash carries it across the middle and
     * no further ("way too much to the left side"). A guard against the old poses coming back, not a measure of how it looks.
     */
    static void daggerStabs(GameTestHelper h) {
        Archetypes.Loaded style = Archetypes.get("daggers");
        FpSwings.Style st = FpSwings.style("daggers");
        h.assertTrue(style != null && st != null, "the Daggers are loaded");
        Vector3f d = new Vector3f(), fist = new Vector3f();
        Quaternionf q = new Quaternionf();
        java.util.List<Archetype.Move> moves = new java.util.ArrayList<>(style.chain());
        moves.addAll(style.masteryChain());
        for (Archetype.Move m : moves) {
            Archetype.MoveDef def = style.move(m);
            boolean slash = m.key.equals("strike_3") || m.key.equals("strike_5");
            h.assertTrue(ArmsRules.thrusts(def) != slash, m.key + (slash ? " is a slash" : " is a stab"));
            for (float t = 0F; t <= def.total(); t += 0.25F) {
                h.assertTrue(FpSwings.sample(st, m, t, true, d, q), m.key + " has its keys");
                q.transform(fist.set(st.grip())).add(d).add(0.56F, -0.52F, -0.72F);
                float x = fist.x / (-fist.z * VIEW_TAN * VIEW_ASPECT), y = fist.y / (-fist.z * VIEW_TAN);
                h.assertTrue(x >= -0.08F, m.key + ": the fist crosses over at " + t + " (" + x + ")");
                if (Math.abs(t - def.windup()) < 1e-3F) {
                    h.assertTrue(x >= 0.26F && y <= -0.55F, m.key + ": the fist is low and on its own side at the blow (" + x + ", " + y + ")");
                }
            }
        }
        h.succeed();
    }

    /** Every gauntlet has the sleeve that stands in for the arm in first person (SPEC §25; no arm is drawn, so a missing one is a stump). */
    static void gauntletSleeves(GameTestHelper h) {
        int n = 0;
        for (ArmsData.Weapon w : ArmsData.WEAPONS) {
            if (!"gauntlets".equals(w.style())) continue;
            for (String file : new String[] {"/assets/awaken/arms/weapons/" + w.id() + "_arm.mesh", "/assets/awaken/textures/item/arms/" + w.id() + "_arm.png"}) {
                h.assertTrue(SwingTests.class.getResource(file) != null, "missing " + file);
            }
            n++;
        }
        h.assertTrue(n >= 7, "the gauntlets are checked (" + n + ")");
        h.succeed();
    }

    /**
     * The Shieldblade is a sword and a shield (SPEC §25 "The Shieldblade"; Euan 2026-11-09: "the blocking with it doesn't even block with the
     * shield side"): the guard's shield shows its front to what's ahead, top up; the shove is the shield's own hand; and each weapon has both parts.
     */
    static void shieldbladeTwoParts(GameTestHelper h) {
        FpSwings.Style st = FpSwings.style("shieldblade");
        h.assertTrue(st != null && st.twin() && st.off() != null, "the off hand holds its own model");
        h.assertTrue(FpSwings.hand(st, Archetype.Move.STRIKE_2) == FpSwings.Hand.OFF && FpSwings.hand(st, Archetype.Move.HEAVY) == FpSwings.Hand.OFF, "the shove is the shield's");
        int n = 0;
        for (ArmsData.Weapon w : ArmsData.WEAPONS) {
            if (!"shieldblade".equals(w.style())) continue;
            for (String part : new String[] {"_blade", "_shield"}) {
                h.assertTrue(SwingTests.class.getResource("/assets/awaken/arms/weapons/" + w.id() + part + ".mesh") != null, w.id() + part + " has a mesh");
                h.assertTrue(SwingTests.class.getResource("/assets/awaken/textures/item/arms/" + w.id() + part + ".png") != null, w.id() + part + " has a texture");
            }
            var shieldItem = readJson("/assets/awaken/items/" + w.id() + "_shield.json");
            h.assertTrue(shieldItem != null, w.id() + " has the shield's item model");
            var fp = readJson("/assets/awaken/models/item/" + w.id() + "_shield_in_hand.json").getAsJsonObject("display").getAsJsonObject("firstperson_righthand").getAsJsonArray("rotation");
            Quaternionf hold = new Quaternionf().rotationXYZ((float) Math.toRadians(fp.get(0).getAsFloat()), (float) Math.toRadians(fp.get(1).getAsFloat()), (float) Math.toRadians(fp.get(2).getAsFloat()));
            Vector3f d = new Vector3f();
            Quaternionf turn = new Quaternionf();
            h.assertTrue(FpSwings.offGuard(st, d, turn), "the shield has its own guard");
            Vector3f front = turn.transform(hold.transform(new Vector3f(0, 0, 1))), top = turn.transform(hold.transform(new Vector3f(1, 1, 0).normalize()));
            h.assertTrue(front.z < -0.9F, w.id() + ": the guard shows the shield's front to what's ahead (" + front.z + ")");
            h.assertTrue(top.y > 0.8F, w.id() + ": the guard holds it top up (" + top.y + ")");
            n++;
        }
        h.assertTrue(n >= 7, "the shieldblades are checked (" + n + ")");
        h.succeed();
    }

    private static com.google.gson.JsonObject readJson(String path) {
        try (var in = SwingTests.class.getResourceAsStream(path)) {
            return com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static float degrees(Quaternionf a, Quaternionf b) {
        // over their lengths: the baked keys are rounded to four places, so a key against itself can read half a degree off
        return (float) Math.toDegrees(2 * Math.acos(Math.min(1F, Math.abs(a.dot(b)) / (float) Math.sqrt(a.lengthSquared() * b.lengthSquared()))));
    }
}
