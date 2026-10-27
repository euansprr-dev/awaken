package dev.awaken.trails;

import dev.awaken.core.api.NowHooks;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.progression.Progression;
import dev.awaken.trails.net.TrailPayloads;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** GameTests for trails: one per mechanism, each on trails of its own so the real catalogue is untouched. */
final class TrailsTests {
    private TrailsTests() {}

    static void register() {
        AwakenTests.register("trail_defs_valid", 40, TrailsTests::defsValid);
        AwakenTests.register("trail_progress_derived", 60, TrailsTests::progressDerived);
        AwakenTests.register("trail_open_once", 60, TrailsTests::openOnce);
        AwakenTests.register("trail_follow_card", 60, TrailsTests::followCard);
        AwakenTests.register("trail_silhouette", 60, TrailsTests::silhouette);
    }

    private static TrailDef.Step step(String id, String text, String... done) {
        return new TrailDef.Step(id, text, List.of(done), List.of(), "", "", "", false);
    }

    private static TrailDef trail(String title, int weight, List<String> opens, String order, TrailDef.Step... steps) {
        return new TrailDef(1, title, "home", "A test trail.", weight, opens, List.of(), order, List.of(steps), new TrailDef.Finish("Done.", 0));
    }

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        Progression.awaken(p);
        return p;
    }

    /** Runs {@code body} with these trails instead of the catalogue, restoring it after. */
    private static void with(Map<String, TrailDef> defs, Runnable body) {
        Map<String, TrailDef> before = Trails.all();
        Trails.replace(defs);
        try {
            body.run();
        } finally {
            Trails.replace(before);
        }
    }

    private static void counter(ServerPlayer p, String key, long v) {
        Progression.setCounter(p, key, v);
    }

    /** Every shipped trail passes the checks, and the checks catch what they should. */
    static void defsValid(GameTestHelper h) {
        List<String> real = TrailChecks.problems(Trails.all());
        h.assertTrue(real.isEmpty(), "the shipped trails have problems: " + real);
        Map<String, TrailDef> bad = new LinkedHashMap<>();
        bad.put("awaken:bad", new TrailDef(1, "", "nowhere", "", 1, List.of("nonsense:token"), List.of("awaken:missing"), "sideways",
                List.of(step("a", "Do it.", "counter:awaken:x"), step("a", "Again.", "wat:ever"), new TrailDef.Step("b", "x".repeat(200), List.of(), List.of(), "nowhere", "", "", false)),
                new TrailDef.Finish("", 0)));
        List<String> found = TrailChecks.problems(bad);
        for (String needle : new String[] {"title", "area", "order", "opens token", "after names", "unique", "done token", "text must", "manual", "no place"}) {
            h.assertTrue(found.stream().anyMatch(s -> s.contains(needle)), "the checks miss '" + needle + "': " + found);
        }
        h.succeed();
    }

    /** A step done before the trail opened is ticked at once, stays ticked, and the last step finishes the trail. */
    static void progressDerived(GameTestHelper h) {
        ServerPlayer p = player(h);
        Map<String, TrailDef> defs = new LinkedHashMap<>();
        defs.put("awaken:t_derived", trail("Derived", 1, List.of("counter:awaken:t_open"), "free",
                step("one", "Do the first thing.", "counter:awaken:t_one"), step("two", "Do the second thing.", "counter:awaken:t_two"),
                new TrailDef.Step("three", "Tick this one yourself.", List.of(), List.of(), "", "", "", true)));
        with(defs, () -> {
            counter(p, "awaken:t_one", 1);
            TrailEngine.update(p);
            TrailState st = TrailState.of(p);
            h.assertTrue(!st.opened.contains("awaken:t_derived"), "closed until its moment");
            counter(p, "awaken:t_open", 1);
            TrailEngine.update(p);
            st = TrailState.of(p);
            h.assertTrue(st.opened.contains("awaken:t_derived") && st.latched.contains("awaken:t_derived|one"), "open, and the step done earlier is ticked at once");
            h.assertTrue(!st.latched.contains("awaken:t_derived|two"), "the other is not");
            counter(p, "awaken:t_one", 0);
            TrailEngine.update(p);
            h.assertTrue(TrailState.of(p).latched.contains("awaken:t_derived|one"), "a ticked step stays ticked");
            counter(p, "awaken:t_two", 1);
            TrailEngine.update(p);
            h.assertTrue(!TrailState.of(p).finished.contains("awaken:t_derived"), "not finished while a hand tick waits");
            TrailEngine.act(p, new TrailPayloads.Act(TrailPayloads.MARK, "awaken:t_derived", "two"));
            h.assertTrue(TrailState.of(p).latched.contains("awaken:t_derived|two"), "the world already read step two");
            TrailEngine.act(p, new TrailPayloads.Act(TrailPayloads.MARK, "awaken:t_derived", "one"));
            h.assertTrue(!TrailState.of(p).finished.contains("awaken:t_derived"), "a hand tick on a step that is not manual does nothing new");
            TrailEngine.act(p, new TrailPayloads.Act(TrailPayloads.MARK, "awaken:t_derived", "three"));
            h.assertTrue(TrailState.of(p).finished.contains("awaken:t_derived"), "the last step finishes it");
        });
        h.succeed();
    }

    /** Two new trails are announced a play-day; the rest open quietly; nothing opens twice. */
    static void openOnce(GameTestHelper h) {
        ServerPlayer p = player(h);
        Map<String, TrailDef> defs = new LinkedHashMap<>();
        for (int i = 0; i < 3; i++) defs.put("awaken:t_once" + i, trail("Once " + i, i, List.of(), "free", step("a", "Do it.", "counter:awaken:t_never")));
        with(defs, () -> {
            TrailEngine.update(p);
            TrailState st = TrailState.of(p);
            h.assertTrue(st.opened.size() == 3, "all three are open: " + st.opened);
            h.assertTrue(st.newToday == TrailEngine.ANNOUNCE_PER_DAY, "two were announced, the third quietly: " + st.newToday);
            TrailEngine.update(p);
            h.assertTrue(TrailState.of(p).newToday == TrailEngine.ANNOUNCE_PER_DAY && TrailState.of(p).opened.size() == 3, "and looking again changes nothing");
        });
        h.succeed();
    }

    /** Following puts the steps on the task card and the Now line; unfollowing takes them off; hiding drops it. */
    static void followCard(GameTestHelper h) {
        ServerPlayer p = player(h);
        Map<String, TrailDef> defs = new LinkedHashMap<>();
        defs.put("awaken:t_card", trail("Card", 1, List.of(), "free", step("a", "First.", "counter:awaken:t_a"), step("b", "Second.", "counter:awaken:t_b"),
                step("c", "Third.", "counter:awaken:t_c")));
        defs.put("awaken:t_other", trail("Other", 2, List.of(), "linear", step("a", "Only this.", "counter:awaken:t_o1"), step("b", "Then this.", "counter:awaken:t_o2"),
                step("c", "Never shown yet.", "counter:awaken:t_o3")));
        with(defs, () -> {
            TrailEngine.update(p);
            h.assertTrue(TrailEngine.task(p) == null, "nothing on the card until you follow");
            h.assertTrue(NowHooks.collect(p, 10).stream().noneMatch(s -> s.id().startsWith("trail:")), "or on the Now line");
            TrailEngine.act(p, new TrailPayloads.Act(TrailPayloads.FOLLOW, "awaken:t_card", ""));
            var shown = dev.awaken.progression.Checklist.now(p);
            h.assertTrue(shown != null && shown.id().equals("trail:awaken:t_card"), "a followed trail is the card, ahead of the first-hour moves: " + (shown == null ? null : shown.id()));
            var task = TrailEngine.task(p);
            h.assertTrue(task != null && task.title().equals("Card") && task.rows().size() == 3 && task.rows().stream().noneMatch(r -> r.done()), "the card shows its three steps");
            counter(p, "awaken:t_a", 1);
            TrailEngine.update(p);
            task = TrailEngine.task(p);
            h.assertTrue(task.rows().size() == 3 && task.rows().getFirst().done() && task.rows().getFirst().text().equals("First."), "a done step stays on the card for context: " + task.rows());
            h.assertTrue(NowHooks.collect(p, 10).stream().anyMatch(s -> s.id().equals("trail:awaken:t_card:b") && s.priority() == TrailEngine.NOW_PRIORITY), "the Now line names the next step");
            TrailEngine.act(p, new TrailPayloads.Act(TrailPayloads.FOLLOW, "awaken:t_other", ""));
            task = TrailEngine.task(p);
            h.assertTrue(task.title().equals("Other") && task.rows().size() == 2, "following another replaces it, and a linear trail shows two");
            h.assertTrue(TrailEngine.suggest(p).startsWith("Other:"), "'Anything for me?' leads with what you follow: " + TrailEngine.suggest(p));
            TrailEngine.act(p, new TrailPayloads.Act(TrailPayloads.HIDE, "awaken:t_other", ""));
            h.assertTrue(TrailEngine.task(p) == null && TrailState.of(p).followed.isEmpty(), "hiding the followed trail clears the card");
            var view = TrailEngine.view(p, TrailState.of(p));
            h.assertTrue(view.trails().stream().anyMatch(r -> r.id().equals("awaken:t_other") && r.state() == TrailPayloads.HIDDEN), "it waits under Hidden");
            h.assertTrue(view.now().stream().noneMatch(r -> r.trail().equals("awaken:t_other")), "and out of what you can do now");
        });
        h.succeed();
    }

    /** A trail whose only unmet need is a rank or a level shows as a silhouette; any other lock keeps it out of sight. */
    static void silhouette(GameTestHelper h) {
        ServerPlayer p = player(h);
        Map<String, TrailDef> defs = new LinkedHashMap<>();
        defs.put("awaken:t_rank", trail("Rank", 1, List.of("rank:S"), "free", step("a", "Later.", "counter:awaken:t_x")));
        defs.put("awaken:t_hidden", trail("Hidden", 2, List.of("counter:awaken:t_unmet"), "free", step("a", "Later.", "counter:awaken:t_x")));
        defs.put("awaken:t_mixed", trail("Mixed", 3, List.of("level:99", "counter:awaken:t_unmet"), "free", step("a", "Later.", "counter:awaken:t_x")));
        with(defs, () -> {
            TrailEngine.update(p);
            var view = TrailEngine.view(p, TrailState.of(p));
            var rank = view.trails().stream().filter(r -> r.id().equals("awaken:t_rank")).findFirst().orElse(null);
            h.assertTrue(rank != null && rank.state() == TrailPayloads.SILHOUETTE && rank.need().equals("Opens at S-rank"), "a rank lock shows what it waits for: " + view.trails());
            h.assertTrue(view.trails().stream().noneMatch(r -> r.id().equals("awaken:t_hidden") || r.id().equals("awaken:t_mixed")), "a lock you have not met is not listed");
            h.assertTrue(view.now().isEmpty(), "and a silhouette is never something to do now");
        });
        h.succeed();
    }
}
