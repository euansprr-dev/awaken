package dev.awaken.progression;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.progression.content.Content;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** GameTests for plan v2's first hour: banked prologue XP, the level road, requirements and diminishing returns. */
final class OnboardingTests {
    private OnboardingTests() {}

    static void register() {
        // Kills, floors and first sightings before awakening are banked, then paid on awakening.
        AwakenTests.register("progression_prologue_xp_banked", 40, h -> {
            ServerPlayer sp = h.makeMockServerPlayerInLevel();
            Profile p = Progression.profile(sp);
            h.assertTrue(Progression.grantXp(sp, 300, Progression.XpSource.GATE_KILL) == 0, "no level-ups before awakening");
            Progression.grantXp(sp, 120, Progression.XpSource.GATE_FLOOR);
            h.assertTrue(Progression.discover(sp, "test/bank_moth", 0.5), "a first sighting before awakening counts");
            h.assertTrue(p.level() == 1 && p.xp() == 0, "still level 1 with nothing applied");
            double scale = Progression.xpScale(sp);
            h.assertTrue(Math.abs(p.bankedXp() - (420 + 0.5 * Curve.xpToNext(1) / scale)) < 1e-6, "banked 495, was " + p.bankedXp());
            // the bank survives a save
            Profile q = Profile.CODEC.parse(NbtOps.INSTANCE, Profile.CODEC.encodeStart(NbtOps.INSTANCE, p).getOrThrow()).getOrThrow();
            h.assertTrue(Math.abs(q.bankedXp() - p.bankedXp()) < 1e-9, "banked XP is saved");
            Progression.awaken(sp);
            // at the default scale: 150 (1→2) + 306 (2→3) = 456 of 495, so level 3 with the rest carried
            h.assertTrue(scale < 1 || p.level() >= 3, "the bank paid on awakening: level " + p.level());
            h.assertTrue(p.level() >= 2 && p.bankedXp() == 0, "and the bank is empty");
            h.assertTrue(p.unspent() == (p.level() - 1) * Curve.POINTS_PER_LEVEL, "with every level's points, " + p.unspent());
            h.succeed();
        });

        // A v1 profile (no banked_xp, no unlocks) still loads, with defaults.
        AwakenTests.register("progression_profile_v1_loads", 20, h -> {
            Profile p = new Profile();
            p.awakened = true;
            p.level = 9;
            var tag = (net.minecraft.nbt.CompoundTag) Profile.CODEC.encodeStart(NbtOps.INSTANCE, p).getOrThrow();
            tag.getCompound("core").ifPresent(c -> c.remove("banked_xp"));
            tag.getCompound("identity").ifPresent(c -> c.remove("unlocks"));
            Profile q = Profile.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
            h.assertTrue(q.level() == 9 && q.bankedXp() == 0 && q.unlocks().isEmpty(), "old save loads with empty defaults");
            h.succeed();
        });

        AwakenTests.register("progression_level_road_table", 20, h -> {
            List<LevelRoad.Step> steps = LevelRoad.steps();
            Set<Integer> levels = new HashSet<>();
            for (LevelRoad.Step s : steps) levels.add(s.def().level());
            for (int lv = 1; lv <= 12; lv++) h.assertTrue(levels.contains(lv), "level " + lv + " has an unlock");
            for (int lv : new int[]{15, 20, 25, 30, 35, 40, 45, 50}) h.assertTrue(levels.contains(lv), "milestone " + lv);
            for (LevelRoad.Step s : steps) {
                h.assertTrue(s.def().title().length() <= 24, "toast-sized title: " + s.def().title());
                for (var g : s.def().grants()) {
                    h.assertTrue(List.of("power", "recipe", "item", "power_core", "flag").contains(g.type()), "known grant " + g.type());
                    if (g.type().equals("recipe")) {
                        for (String id : g.ids()) {
                            var key = ResourceKey.create(Registries.RECIPE, Identifier.parse(id));
                            h.assertTrue(h.getLevel().getServer().getRecipeManager().byKey(key).isPresent(), "recipe exists: " + id);
                        }
                    }
                }
            }
            h.succeed();
        });

        // Levelling grants each step once, by id; crafts at 2, Dash at 3, Truths offered at 5 (never at awakening).
        AwakenTests.register("progression_level_road_unlocks", 60, h -> {
            ServerPlayer sp = h.makeMockServerPlayerInLevel();
            Progression.awaken(sp);
            Profile p = Progression.profile(sp);
            h.assertTrue(p.truthsOffered().isEmpty(), "no Truths at awakening any more");
            h.assertTrue(p.unlocks().contains("awaken:first_power"), "level 1's step on awakening");
            while (p.level() < 2) Progression.grantXp(sp, Curve.xpToNext(p.level()) + 1, Progression.XpSource.COMMAND);
            h.assertTrue(p.unlocks().contains("awaken:hunter_crafts") && !p.unlocks().contains("awaken:dash"), "level 2: crafts, not Dash yet");
            h.assertTrue(sp.getRecipeBook().contains(ResourceKey.create(Registries.RECIPE, Identifier.parse("awaken:target_dummy"))), "recipes in the book");
            while (p.level() < 3) Progression.grantXp(sp, Curve.xpToNext(p.level()) + 1, Progression.XpSource.COMMAND);
            h.assertTrue(p.unlocks().contains("awaken:dash"), "level 3 unlocks Dash");
            h.assertTrue(Requirements.powerCheck.test(sp, "power:awaken:dash"), "and Dash is learned");
            while (p.level() < 5) Progression.grantXp(sp, Curve.xpToNext(p.level()) + 1, Progression.XpSource.COMMAND);
            h.assertTrue(p.unlocks().containsAll(List.of("awaken:second_power", "awaken:truths")), "levels 4-5: " + p.unlocks());
            h.assertTrue(p.truthsOffered().size() == 3, "three Truths offered at level 5, got " + p.truthsOffered().size());
            h.assertTrue(Progression.hasFlag(sp, "awaken:truths"), "the flag other modules can read");
            int before = p.unlocks().size();
            h.assertTrue(LevelRoad.catchUp(sp, false) == 0 && p.unlocks().size() == before, "nothing is granted twice");
            LevelRoad.Step next = LevelRoad.next(p);
            h.assertTrue(next != null && next.def().level() == 6, "the Now tracker's next unlock is level 6, got " + (next == null ? null : next.id()));
            h.succeed();
        });

        // An older save at level 8 with no unlock ids gets everything up to 8 on login, once; unknown ids are kept.
        AwakenTests.register("progression_level_road_backfill", 60, h -> {
            ServerPlayer sp = h.makeMockServerPlayerInLevel();
            Profile p = Progression.profile(sp);
            p.awakened = true; // a v1 save: awakened at some point, never saw the road
            p.level = 8;
            p.truthsOffered.add("awaken:i_am_fast"); // a v1 player keeps their Truths, gets no second set
            p.unlocks.add("awaken:retired_step");
            int granted = LevelRoad.catchUp(sp, true);
            int expected = 0;
            for (LevelRoad.Step s : LevelRoad.steps()) if (s.def().level() <= 8) expected++;
            h.assertTrue(granted == expected, "caught up " + granted + " of " + expected);
            for (LevelRoad.Step s : LevelRoad.steps()) {
                h.assertTrue(p.unlocks().contains(s.id().toString()) == (s.def().level() <= 8), "step " + s.id() + " at level " + s.def().level());
            }
            h.assertTrue(p.unlocks().contains("awaken:retired_step"), "an id the table no longer has is kept, not revoked");
            h.assertTrue(p.truthsOffered().size() == 1, "no second set of Truths for a v1 player");
            h.assertTrue(Requirements.powerCheck.test(sp, "power:awaken:dash"), "the missed Dash arrives");
            h.assertTrue(LevelRoad.catchUp(sp, true) == 0, "a second login grants nothing");
            h.succeed();
        });

        // A Directive or drill is never offered when its requirements aren't met; every token is well formed.
        AwakenTests.register("progression_requires_filtering", 80, h -> {
            for (var e : Content.DIRECTIVES.map.entrySet()) {
                for (String t : e.getValue().requires()) h.assertTrue(Requirements.known(t), "directive " + e.getKey() + ": bad token " + t);
            }
            for (var e : Content.DRILLS.map.entrySet()) {
                for (String t : e.getValue().requires()) h.assertTrue(Requirements.known(t), "drill " + e.getKey() + ": bad token " + t);
            }
            ServerPlayer sp = h.makeMockServerPlayerInLevel();
            Progression.awaken(sp);
            Profile p = Progression.profile(sp);
            Set<String> offered = new HashSet<>();
            for (int i = 0; i < 60; i++) {
                p.directives.clear();
                p.counters.keySet().removeIf(k -> k.startsWith("awaken:directive_offered/"));
                Directives.tryOffer(sp, "test");
                h.assertTrue(p.directives.size() == 1, "something is always on offer in the first hour");
                String id = p.directives.get(0).id();
                offered.add(id);
                var def = Content.DIRECTIVES.get(id);
                h.assertTrue(Requirements.met(sp, def.requires()), "offered " + id + " without " + def.requires());
            }
            h.assertFalse(offered.contains("awaken:dash_chain") || offered.contains("awaken:meditate_peak"), "nothing that needs Dash or meditation");
            for (int i = 0; i < 20; i++) {
                p.regimen = Profile.RegimenState.EMPTY;
                Regimen.morning(sp);
                h.assertTrue(p.regimen().drills().size() == 3, "three drills every morning");
                for (String id : p.regimen().drills()) {
                    h.assertTrue(Requirements.met(sp, Content.DRILLS.get(id).requires()), "drill " + id + " needs what you lack");
                }
            }
            // once the need is met, it can come up
            h.assertFalse(Requirements.met(sp, "unlock:awaken:meditation"), "meditation isn't taught yet");
            Progression.setCounter(sp, LevelRoad.flagCounter("awaken:meditation"), 1);
            h.assertTrue(Requirements.met(sp, "unlock:awaken:meditation"), "taught by the level road");
            h.assertFalse(Requirements.met(sp, "no_such_token"), "an unknown token is unmet");
            h.succeed();
        });

        // Diminishing returns: full value up to the budget, less and less after, back to full once it decays.
        AwakenTests.register("progression_diminishing_returns", 20, h -> {
            UUID id = UUID.randomUUID();
            double b = XpBudget.budget(XpBudget.KILL);
            double paid = 0, last = 1;
            boolean falling = true;
            for (int i = 0; i < 20; i++) {
                double x = XpBudget.pay(id, XpBudget.KILL, 0.04, 1000);
                if (i > 4 && x > last + 1e-12) falling = false;
                if (i > 4) last = x;
                paid += x;
            }
            double expected = b + b * Math.log(0.8 / b); // 20 kills of 0.04 = 0.8 raw in one go
            h.assertTrue(Math.abs(paid - expected) < 1e-9, "paid " + paid + ", expected " + expected);
            h.assertTrue(falling && last < 0.04 * 0.25, "each extra kill pays less, the 20th " + last);
            h.assertTrue(XpBudget.pay(id, XpBudget.ORE, 0.01, 1000) == 0.01, "another source has its own budget");
            double later = XpBudget.pay(id, XpBudget.KILL, 0.04, 1000 + 20 * 60 * 40); // 40 minutes on
            h.assertTrue(Math.abs(later - 0.04) < 1e-9, "after a long break it pays in full again: " + later);
            h.assertTrue(XpBudget.taper(1, 0) == 1 && Math.abs(XpBudget.taper(11, 0) - 0.5) < 1e-9 && XpBudget.taper(11, 17) == 1,
                    "out-levelled things taper; on-level ones don't");
            // a farm can't break pacing: 60 kills every 10 minutes for 10 hours at level 3 stays under 1 level an hour
            UUID farm = UUID.randomUUID();
            double total = 0;
            for (int m = 0; m < 600; m++) for (int k = 0; k < 6; k++) total += XpBudget.pay(farm, XpBudget.KILL, 0.04, m * 1200L + k * 200L);
            h.assertTrue(total / 10 < 1.0, "a mob farm pays " + total / 10 + " levels an hour");
            h.succeed();
        });

        // In the world: overworld kills and mining go through the budget (and bank before awakening).
        AwakenTests.register("progression_world_xp_in_play", 40, h -> {
            ServerPlayer sp = h.makeMockServerPlayerInLevel();
            Progression.awaken(sp);
            Profile p = Progression.profile(sp);
            double first = Progression.worldXp(sp, "test_source", 0.1);
            double second = Progression.worldXp(sp, "test_source", 0.3);
            h.assertTrue(first == 0.1 && second < 0.3 && second > 0, "the world budget bends: " + first + ", " + second);
            h.assertTrue(p.xp() > 0, "and it lands as XP");
            sp.getInventory().add(new ItemStack(Items.OBSIDIAN));
            h.assertTrue(RecipeUnlocks.check(sp) >= 1, "holding an ingredient unlocks its mod recipe");
            h.assertTrue(sp.getRecipeBook().contains(ResourceKey.create(Registries.RECIPE, Identifier.parse("awaken:hearth_anchor"))), "the Hearth Anchor is in the book");
            h.assertTrue(RecipeUnlocks.check(sp) == 0, "only once");
            h.succeed();
        });
    }
}
