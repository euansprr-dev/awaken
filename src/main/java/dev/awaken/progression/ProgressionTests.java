package dev.awaken.progression;

import dev.awaken.core.test.AwakenTests;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;

/** GameTests for progression maths, conditions, the profile codec and level-ups. */
final class ProgressionTests {
    private ProgressionTests() {}

    static void register() {
        AwakenTests.register("progression_curve_matches_sim", 20, h -> {
            // Values from tools/sim/pacing_results.json xp_curve: 1 -> 150, 10 -> 2597, 45 -> 22520, 135 -> 110525
            h.assertTrue(Math.round(Curve.xpToNext(1)) == 150, "xpToNext(1) should be 150, was " + Curve.xpToNext(1));
            h.assertTrue(Math.round(Curve.xpToNext(10)) == 2597, "xpToNext(10) should be 2597, was " + Curve.xpToNext(10));
            h.assertTrue(Math.round(Curve.xpToNext(45)) == 22520, "xpToNext(45) should be 22520, was " + Curve.xpToNext(45));
            h.assertTrue(Math.round(Curve.xpToNext(135)) == 110525, "xpToNext(135) should be 110525, was " + Curve.xpToNext(135));
            h.assertTrue(Rank.C.levelRequired == 25 && Rank.SOVEREIGN.levelRequired == 135, "rank thresholds");
            double f1 = Curve.floorReward(Rank.C, 1), f2 = Curve.floorReward(Rank.C, 2);
            h.assertTrue(Math.abs(f2 / f1 - 1.4) < 1e-9, "floor growth 1.4");
            h.succeed();
        });
        AwakenTests.register("progression_conditions", 20, h -> {
            Profile p = new Profile();
            p.counters.put("awaken:kills", 12L);
            var c = new Condition.CounterAtLeast("awaken:kills", 10);
            h.assertTrue(c.test(p, null), "absolute counter");
            h.assertFalse(c.test(p, Map.of("awaken:kills", 5L)), "relative counter (12-5 < 10)");
            var all = new Condition.All(List.of(c, new Condition.LevelAtLeast(1)));
            h.assertTrue(all.test(p, null), "all");
            var any = new Condition.Any(List.of(new Condition.LevelAtLeast(99), c));
            h.assertTrue(any.test(p, null), "any");
            h.succeed();
        });
        AwakenTests.register("progression_profile_codec_roundtrip", 20, h -> {
            Profile p = new Profile();
            p.awakened = true;
            p.level = 37;
            p.xp = 123.5;
            p.rank = Rank.C;
            p.stats[Stat.AGI.ordinal()] = 77;
            p.counters.put("awaken:flow_ticks", 4000L);
            p.truthsDeclared.add("awaken:i_do_not_retreat");
            p.directives.add(new Profile.DirectiveState("awaken:three_floors", true, 4, 6, Map.of("awaken:floors_cleared", 9L)));
            var tag = Profile.CODEC.encodeStart(NbtOps.INSTANCE, p).getOrThrow();
            Profile q = Profile.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
            h.assertTrue(q.level() == 37 && q.rank() == Rank.C && q.stat(Stat.AGI) == 77, "core fields");
            h.assertTrue(q.counter("awaken:flow_ticks") == 4000L, "counters");
            h.assertTrue(q.truthsDeclared().contains("awaken:i_do_not_retreat"), "truths");
            h.assertTrue(q.directives().get(0).baseline().get("awaken:floors_cleared") == 9L, "directive baseline");
            h.succeed();
        });
        AwakenTests.register("progression_content_loaded", 20, h -> {
            h.assertTrue(dev.awaken.progression.content.Content.TRUTHS.map.size() >= 25, "truths loaded: " + dev.awaken.progression.content.Content.TRUTHS.map.size());
            h.assertTrue(dev.awaken.progression.content.Content.DIRECTIVES.map.size() >= 30, "directives loaded");
            h.assertTrue(dev.awaken.progression.content.Content.PATHS.map.size() == 12, "12 paths");
            h.assertTrue(dev.awaken.progression.content.Content.DRILLS.map.size() >= 15, "drills loaded");
            h.succeed();
        });
        AwakenTests.register("progression_directive_lifecycle", 40, h -> {
            ServerPlayer sp = h.makeMockServerPlayerInLevel();
            Progression.awaken(sp);
            Profile p = Progression.profile(sp);
            Directives.tryOffer(sp, "test");
            h.assertTrue(p.directives().size() == 1, "one directive offered, got " + p.directives().size());
            String id = p.directives().get(0).id();
            Directives.answer(sp, Directives.ACTION + ":" + id, true);
            h.assertTrue(p.directives().get(0).accepted(), "accepted");
            var def = dev.awaken.progression.content.Content.DIRECTIVES.get(id);
            java.util.Set<String> cs = new java.util.HashSet<>();
            def.condition().counters(cs);
            for (String c : cs) Progression.count(sp, c, 10_000_000L);
            h.assertTrue(p.directives().isEmpty(), "completed and removed: " + id);
            h.assertTrue(p.counter("awaken:directive_done/" + id) == 1, "done counter");
            h.succeed();
        });
        AwakenTests.register("progression_truth_declare_and_true", 40, h -> {
            ServerPlayer sp = h.makeMockServerPlayerInLevel();
            Progression.awaken(sp);
            Profile p = Progression.profile(sp);
            h.assertTrue(p.truthsOffered().size() == 3, "three truths offered at awakening, got " + p.truthsOffered().size());
            String t = p.truthsOffered().get(0);
            h.assertTrue(Progression.declareTruth(sp, t), "declare");
            h.assertTrue(p.truthsDeclared().contains(t), "declared");
            var def = dev.awaken.progression.content.Content.TRUTHS.get(t);
            java.util.Set<String> cs = new java.util.HashSet<>();
            def.condition().counters(cs);
            for (String c : cs) Progression.setCounter(sp, c, 10_000_000L);
            h.assertTrue(p.truthsTrue().contains(t), "became true: " + t);
            h.succeed();
        });
        AwakenTests.register("progression_regimen_rolls_three", 40, h -> {
            ServerPlayer sp = h.makeMockServerPlayerInLevel();
            Progression.awaken(sp);
            Regimen.morning(sp);
            Profile p = Progression.profile(sp);
            h.assertTrue(p.regimen().drills().size() == 3, "three drills, got " + p.regimen().drills().size());
            int before = p.unspent();
            for (String id : p.regimen().drills()) {
                java.util.Set<String> cs = new java.util.HashSet<>();
                dev.awaken.progression.content.Content.DRILLS.get(id).condition().counters(cs);
                for (String c : cs) Progression.count(sp, c, 10_000_000L);
            }
            h.assertTrue(p.regimen().done().size() == 3, "all drills done");
            h.assertTrue(p.unspent() >= before + 1, "regimen point granted");
            h.succeed();
        });
        AwakenTests.register("progression_respec_refunds", 40, h -> {
            ServerPlayer sp = h.makeMockServerPlayerInLevel();
            Progression.awaken(sp);
            Progression.grantXp(sp, Curve.xpToNext(1) + 1, Progression.XpSource.COMMAND);
            Progression.spendPoint(sp, Stat.STR);
            Progression.spendPoint(sp, Stat.AGI);
            int refund = Progression.respec(sp);
            Profile p = Progression.profile(sp);
            h.assertTrue(refund == 2 && p.stat(Stat.STR) == Stat.START && p.unspent() == 3, "refund 2, back to 3 unspent");
            h.succeed();
        });
        AwakenTests.register("progression_level_up_grants_points", 40, h -> {
            ServerPlayer sp = h.makeMockServerPlayerInLevel();
            Progression.awaken(sp);
            Profile p = Progression.profile(sp);
            int gained = Progression.grantXp(sp, Curve.xpToNext(1) + Curve.xpToNext(2) + 1, Progression.XpSource.COMMAND);
            h.assertTrue(gained == 2, "should gain 2 levels, gained " + gained);
            h.assertTrue(p.level() == 3 && p.unspent() == 6, "level 3 with 6 points, was " + p.level() + "/" + p.unspent());
            h.assertTrue(Progression.spendPoint(sp, Stat.VIT), "spend point");
            h.assertTrue(sp.getMaxHealth() > 20.0f, "VIT raises max health, was " + sp.getMaxHealth());
            h.succeed();
        });
    }
}
