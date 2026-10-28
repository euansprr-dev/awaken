package dev.awaken.progression;

import dev.awaken.core.api.NowHooks;
import dev.awaken.core.api.PowerCoreHooks;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.progression.content.Defs.Goal;
import dev.awaken.signal.Signal;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** GameTests for P3, the road to 50: every level has a step whose grants resolve, goals, and the lessons. */
final class RoadTests {
    private RoadTests() {}

    static void register() {
        // Rewards are said the way a person would say them (SPEC 26.3c): a share of a level in words, the stat's own
        // name, perks as phrases, joined with "and"; a reward with nothing to count is just the recognition.
        AwakenTests.register("progression_reward_words", 20, h -> {
            var r = new Reward(0.1, 1, java.util.Optional.of(Stat.STR), 1, java.util.Optional.empty(), java.util.Optional.empty(), 0,
                    List.of(new Perk(Perks.MOVE_SPEED, 0.05)));
            String said = RewardWords.describe(r);
            h.assertTrue(said.equals("a tenth of a level's worth of XP, a stat point, +1 Strength and you move 5% faster"), "said plainly: " + said);
            h.assertTrue(RewardWords.levelsWorth(0.25).equals("a quarter of a level's worth of XP") && RewardWords.levelsWorth(2).equals("2 levels' worth of XP")
                    && RewardWords.levelsWorth(0.3).equals("30% of a level's worth of XP"), "shares of a level in words");
            h.assertTrue(RewardWords.perk(new Perk(Perks.CRIT_CHANCE, 0.08)).startsWith("an 8-point better chance"), "an 8, not a 8");
            h.assertTrue(RewardWords.describe(Reward.NONE).equals("just the recognition"), "nothing to count");
            h.succeed();
        });
        // Every level 1-50 has a step; every grant resolves (recipes, items, powers, flags); every goal is well formed
        // and every P3 text fits its System v2 surface.
        AwakenTests.register("progression_level_road_every_level", 40, h -> {
            Set<Integer> levels = new HashSet<>();
            for (LevelRoad.Step s : LevelRoad.steps()) levels.add(s.def().level());
            for (int lv = 1; lv <= 50; lv++) h.assertTrue(levels.contains(lv), "level " + lv + " has a step on the road");
            var recipes = h.getLevel().getServer().getRecipeManager();
            for (LevelRoad.Step s : LevelRoad.steps()) {
                String where = s.id() + " (level " + s.def().level() + ")";
                for (var g : s.def().grants()) {
                    switch (g.type()) {
                        case "recipe" -> {
                            h.assertTrue(!g.ids().isEmpty() || g.id().isPresent(), where + ": a recipe grant names a recipe");
                            for (String id : g.ids().isEmpty() ? List.of(g.id().orElse("")) : g.ids()) {
                                h.assertTrue(recipes.byKey(ResourceKey.create(Registries.RECIPE, Identifier.parse(id))).isPresent(), where + ": recipe " + id);
                            }
                        }
                        case "item" -> {
                            Identifier id = Identifier.tryParse(g.id().orElse(""));
                            h.assertTrue(id != null && BuiltInRegistries.ITEM.containsKey(id), where + ": item " + g.id());
                            h.assertTrue(g.count() >= 1, where + ": item count");
                        }
                        case "power" -> h.assertTrue(!PowerCoreHooks.forPower(g.id().orElse(""), 0).isEmpty(), where + ": power " + g.id());
                        case "power_core" -> h.assertTrue(g.count() >= 1 && !PowerCoreHooks.roll(Rank.C, h.getLevel().getRandom()).isEmpty(),
                                where + ": cores roll");
                        case "flag" -> h.assertTrue(g.id().map(Identifier::tryParse).isPresent(), where + ": flag id " + g.id());
                        default -> h.assertTrue(false, where + ": unknown grant " + g.type());
                    }
                }
                boolean p3 = s.def().schema() >= 2;
                h.assertTrue(!p3 || s.def().goal().isPresent(), where + ": every P3 step names a concrete thing to do");
                if (s.def().goal().isPresent()) {
                    Goal goal = s.def().goal().get();
                    Set<String> counters = new HashSet<>();
                    goal.done().counters(counters);
                    for (String c : counters) h.assertTrue(c.startsWith("awaken:"), where + ": goal counter " + c);
                    for (String t : goal.requires()) h.assertTrue(Requirements.known(t), where + ": goal requirement " + t);
                    h.assertTrue(px(goal.now()) <= TRACKER_PX, where + ": tracker line fits (" + px(goal.now()) + " px)");
                    h.assertTrue(px(goal.toast()) <= TOAST_PX, where + ": goal toast fits (" + px(goal.toast()) + " px)");
                }
                if (p3) {
                    h.assertTrue(px(s.def().title()) <= TOAST_PX, where + ": title fits");
                    h.assertTrue(px(firstSentence(s.def().text())) <= TOAST_PX, where + ": first sentence fits ("
                            + px(firstSentence(s.def().text())) + " px)");
                }
            }
            for (Lessons.Lesson l : Lessons.lessons()) {
                for (Lessons.Step st : l.steps()) {
                    h.assertTrue(px(st.now()) <= TRACKER_PX, "lesson " + l.id() + "/" + st.id() + ": tracker line fits (" + px(st.now()) + ")");
                    h.assertTrue(px(st.toastTitle()) <= TOAST_PX && px(firstSentence(st.toastText())) <= TOAST_PX,
                            "lesson " + l.id() + "/" + st.id() + ": toast fits");
                }
            }
            h.assertTrue(Lessons.lessons().stream().map(Lessons.Lesson::id).toList().containsAll(List.of("meditation", "veil", "dreams")),
                    "the three teaching lessons are defined");
            h.succeed();
        });

        // A goal shows on the Now tracker once its step unlocks, hides while its requirements aren't met, and toasts
        // when done; a goal already met when its step unlocks completes silently.
        AwakenTests.register("progression_road_goals_track", 80, h -> {
            ServerPlayer sp = h.makeMockServerPlayerInLevel();
            Progression.awaken(sp);
            Profile p = Progression.profile(sp);
            levelTo(sp, 14);
            Lessons.evaluate(sp);
            NowHooks.Suggestion g = Lessons.openGoal(sp, p);
            h.assertTrue(g != null && g.id().equals("progression:goal/awaken:training_grounds"), "level 14's goal is up: " + (g == null ? null : g.id()));
            int toasts = Lessons.toastsSent;
            Progression.setCounter(sp, "awaken:training_laps", 1);
            Lessons.evaluate(sp);
            h.assertTrue(p.counter(Lessons.goalKey("awaken:training_grounds")) == 1, "the goal is met and saved");
            h.assertTrue(Lessons.toastsSent == toasts + 1, "with one toast");
            h.assertTrue(Lessons.openGoal(sp, p) == null, "and gone from the tracker");
            // already found a ruin before level 16: the goal completes silently
            Progression.setCounter(sp, "awaken:ruins_found", 1);
            levelTo(sp, 16);
            toasts = Lessons.toastsSent;
            Lessons.evaluate(sp);
            h.assertTrue(p.counter(Lessons.goalKey("awaken:precursor_scanner")) == 1 && Lessons.toastsSent == toasts, "met before it was asked: no toast");
            // level 24: the Hanging Gardens need D-rank, so the tracker shows the newest goal you can act on instead
            Progression.setCounter(sp, "awaken:dream_manifests", 0);
            levelTo(sp, 24);
            h.assertTrue(p.rank() == Rank.E, "still E-rank (no trial taken)");
            g = Lessons.openGoal(sp, p);
            h.assertTrue(g != null && g.id().equals("progression:goal/awaken:dream_shaping"), "an E-rank player sees the dream goal: " + (g == null ? null : g.id()));
            h.succeed();
        });

        // The first meditation, the first walk in the Veil and the first lucid dream are taught step by step: the Now
        // line says the next action, a choice card asks a real decision, a toast says when it's done.
        AwakenTests.register("progression_lessons_advance", 80, h -> {
            ServerPlayer sp = h.makeMockServerPlayerInLevel();
            Progression.awaken(sp);
            levelTo(sp, 10);
            Lessons.evaluate(sp);
            h.assertTrue(Lessons.currentStep(sp, "meditation").isEmpty(), "no lesson before its flag");
            levelTo(sp, 12);
            Lessons.evaluate(sp);
            h.assertTrue(Lessons.currentStep(sp, "meditation").equals("meditate"), "level 11 starts the meditation lesson");
            h.assertTrue(has(NowHooks.collect(sp, 6), "lesson:meditation/meditate"), "and the Now tracker says what to do");
            int toasts = Lessons.toastsSent;
            Progression.count(sp, Counters.MEDITATE_TICKS, 100);
            Lessons.evaluate(sp);
            h.assertTrue(Lessons.currentStep(sp, "meditation").isEmpty() && Lessons.progress(sp, "meditation") == 1, "meditated: lesson done");
            h.assertTrue(Lessons.toastsSent > toasts, "with a toast");
            h.assertTrue(sp.getData(dev.awaken.signal.NoticeHistory.HISTORY).entries().stream().anyMatch(e ->
                    e.event().id().startsWith("lesson/") && e.event().details().stream().anyMatch(line -> line.contains("task card"))),
                    "completed teaching step remains readable in Journal");
            // dreams (level 12): a reality check, tonight's intention (a choice), a dream, a lucid dream
            h.assertTrue(Lessons.currentStep(sp, "dreams").equals("reality_check"), "dreams start with a reality check");
            Progression.count(sp, Counters.REALITY_CHECKS);
            Lessons.evaluate(sp);
            h.assertTrue(Lessons.currentStep(sp, "dreams").equals("intention"), "then tonight's intention");
            Signal.answer(sp, "lesson:dreams/intention", false);
            Lessons.evaluate(sp);
            h.assertTrue(Lessons.answer(sp, "dreams", "intention") == 3 && Lessons.currentStep(sp, "dreams").equals("first_dream"),
                    "answered (just sleep): on to the first dream");
            Progression.count(sp, Counters.DREAMS);
            Lessons.evaluate(sp);
            h.assertTrue(Lessons.currentStep(sp, "dreams").equals("lucid"), "then a lucid dream");
            Progression.count(sp, Counters.LUCID_DREAMS);
            Lessons.evaluate(sp);
            h.assertTrue(Lessons.progress(sp, "dreams") == 4, "the dream lesson is done");
            // the Veil (level 15): step out, hear the dead, the Stillpoint (asked once, "Later" keeps the step)
            levelTo(sp, 15);
            Lessons.evaluate(sp);
            h.assertTrue(Lessons.currentStep(sp, "veil").equals("step_out"), "level 15: step out of your body");
            Progression.count(sp, Counters.VEIL_TICKS, 20);
            Lessons.evaluate(sp);
            h.assertTrue(Lessons.currentStep(sp, "veil").equals("the_dead"), "then hear the dead");
            Progression.count(sp, Counters.SPIRITS_SPOKEN);
            Lessons.evaluate(sp);
            h.assertTrue(Lessons.currentStep(sp, "veil").equals("stillpoint"), "then the Stillpoint");
            h.assertTrue(Lessons.answer(sp, "veil", "stillpoint") == 1 && Signal.isPending(sp, "lesson:veil/stillpoint"), "asked with a choice card");
            Signal.answer(sp, "lesson:veil/stillpoint", false);
            Lessons.evaluate(sp);
            h.assertTrue(Lessons.answer(sp, "veil", "stillpoint") == 3 && Lessons.currentStep(sp, "veil").equals("stillpoint"),
                    "\"Later\": the step waits, the card isn't asked again");
            h.assertTrue(!Signal.isPending(sp, "lesson:veil/stillpoint"), "no second card");
            Progression.count(sp, "awaken:stillpoint_visits");
            Lessons.evaluate(sp);
            h.assertTrue(Lessons.progress(sp, "veil") == 3 && Lessons.currentStep(sp, "veil").isEmpty(), "the Veil lesson is done");
            h.succeed();
        });
    }

    private static boolean has(List<NowHooks.Suggestion> list, String id) {
        for (NowHooks.Suggestion s : list) if (s.id().equals(id)) return true;
        return false;
    }

    /** Exactly to {@code level} through the normal level-ups (the road grants each step on the way). */
    private static void levelTo(ServerPlayer sp, int level) {
        Progression.topUpToLevel(sp, level, Progression.XpSource.COMMAND);
    }

    // ---- text sizes (System v2): a toast shows its title and the first sentence of its text in 144 px; the tracker
    // line has ~200 px. Advance widths of Minecraft's default font, the same table as tools/sim/road_content.py.
    static final int TOAST_PX = 144, TRACKER_PX = 200;
    private static final Map<Character, Integer> NARROW = narrow();

    private static Map<Character, Integer> narrow() {
        Map<Character, Integer> m = new java.util.HashMap<>();
        for (char c : "i!.,:;'|".toCharArray()) m.put(c, 2);
        m.put('l', 3);
        for (char c : "tI[] ".toCharArray()) m.put(c, 4);
        for (char c : "fk<>(){}*\"".toCharArray()) m.put(c, 5);
        m.put('@', 7);
        m.put('~', 7);
        return m;
    }

    static int px(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) w += NARROW.getOrDefault(s.charAt(i), 6);
        return w;
    }

    static String firstSentence(String s) {
        for (int i = 0; i < s.length() - 1; i++) {
            char c = s.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && s.charAt(i + 1) == ' ') return s.substring(0, i + 1);
        }
        return s;
    }
}
