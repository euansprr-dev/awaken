package dev.awaken.tasks;

import com.mojang.serialization.JsonOps;
import dev.awaken.core.api.Goals;
import dev.awaken.core.api.Goals.Goal;
import dev.awaken.core.api.Goals.Step;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.progression.Progression;
import dev.awaken.tasks.net.TaskPayloads;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Set;

/**
 * The Tasks page's GameTests (SPEC §27.13), one per mechanism: the order and the one step on top (urgent, pinned, the
 * main path, then the most pressing; never an optional upgrade), one row per key with goal steps covering their Now
 * suggestions; the saved page; and on a real player, the first armour's sets as choices, a set chosen and pinned, the card
 * listing what that set still takes. The Hearth's steps in a real home: HomeTests ("tasks_next_step_follows_path").
 */
final class TasksTests {
    private TasksTests() {}

    static void register() {
        AwakenTests.register("tasks_book_rules", 100, h -> {
            Step fire = Step.of("hearth:ember", Goal.HEARTH, "Light a fire").path(Goals.PATH_FIRE).build();
            Step armoury = Step.of("hearth/armoury", Goal.HEARTH, "Build your Armoury").path(Goals.PATH_ARMOURY).priority(60)
                    .covers("hearth:room/fitting").build();
            Step gate = Step.of("hearth/anchor", Goal.RANK, "Clear your first gate").path(Goals.PATH_FIRST_GATE).covers("gate:near:").build();
            Step sanctuary = Step.of("hearth/sanctuary", Goal.HEARTH, "Make your Bedroom a Sanctuary").optional().priority(89).build();
            Step loose = Step.of("arms:blade", Goal.WEAPONS, "Make a blade").priority(70).build();
            List<Step> steps = new java.util.ArrayList<>(List.of(loose, sanctuary, gate, armoury, fire));
            steps.sort(TaskBook.ORDER);
            h.assertTrue(steps.get(0) == fire && steps.get(1) == armoury && steps.get(2) == gate, "the main path in order first: " + keys(steps));
            h.assertTrue(TaskBook.top(steps, "").equals("hearth:ember"), "the path's next is on top, not the most pressing");
            h.assertTrue(TaskBook.top(steps, "arms:blade").equals("arms:blade"), "a pin goes on top");
            Step urgent = Step.of("opening:wake", Goal.RANK, "Wake").priority(TaskBook.URGENT).build();
            h.assertTrue(TaskBook.top(List.of(urgent, fire), "arms:blade").equals("opening:wake"), "something urgent goes over the pin");
            h.assertTrue(TaskBook.top(List.of(sanctuary, loose), "").equals("arms:blade"), "never an optional upgrade on top, however pressing");
            h.assertTrue(TaskBook.covered(steps, "gate:near:12") && TaskBook.covered(steps, "hearth:room/fitting") && !TaskBook.covered(steps, "gate:far"),
                    "a goal step stands for the suggestions it covers, and no others");
            Step richer = Step.of("hearth/armoury", Goal.HEARTH, "Build your Armoury").need(Goals.Need.of("A crafting table", false)).build();
            Step merged = TaskBook.merge(armoury, richer);
            h.assertTrue(merged.needs().size() == 1 && merged.path() == Goals.PATH_ARMOURY, "two of one key make one row: the one that says more, the path kept");
            h.assertTrue(NowMap.rule("hearth:shelter").path() == Goals.PATH_SHELTER && NowMap.rule("kin:want/x").goal() == Goal.HEARTH
                    && NowMap.rule("story:bell_crypt").goal() == Goal.BELLS && NowMap.rule("something:else").goal() == Goal.OUT,
                    "suggestions land on their goals (kin on the Hearth's)");
            // the saved page: the pin, the seen and the chosen set come back
            TaskState st = TaskState.fresh();
            st.pinned = "hearth/armoury";
            st.see("armour/e");
            st.chosen.put("armour/e", "wildhide");
            var json = TaskState.CODEC.codec().encodeStart(JsonOps.INSTANCE, st).getOrThrow();
            TaskState back = TaskState.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
            h.assertTrue(back.pinned.equals("hearth/armoury") && back.seen.contains("armour/e") && back.chosen("armour/e").equals("wildhide"),
                    "the page's state survives a save: " + json);
            // a schema 1 save loads with no places, nothing set aside and no tabs yet
            TaskState old = TaskState.CODEC.codec().parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(
                    "{\"schemaVersion\":1,\"pinned\":\"x\",\"seen\":[\"a\"],\"chosen\":{}}")).getOrThrow();
            h.assertTrue(old.pinned.equals("x") && old.held.isEmpty() && old.aside.isEmpty() && old.tabsSeen.isEmpty(), "an old save loads");
            st.held.put("HEARTH", List.of("hearth.build"));
            st.setAside("kin/need/x", 7L);
            st.tabsSeen.add("HEARTH");
            json = TaskState.CODEC.codec().encodeStart(JsonOps.INSTANCE, st).getOrThrow();
            back = TaskState.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
            h.assertTrue(back.held.equals(st.held) && back.aside.get("kin/need/x") == 7L && back.tabsSeen.contains("HEARTH"), "places, aside and tabs survive a save: " + json);
            h.assertTrue(TaskBook.top(List.of(Step.of("lesson:veil/x", Goal.OUT, "Meditate").priority(89).build()), "").isEmpty()
                    && TaskBook.top(List.of(Step.of("lesson:veil/x", Goal.OUT, "Meditate").priority(89).build(), loose), "").equals("arms:blade"), "a lesson never holds the top");
            h.succeed();
        });

        AwakenTests.registerIsolated("tasks_slots_stay", 200, h -> {
            // the rule alone: a finished row's place goes to the next; what must show takes the last place that needn't; a renamed thread falls out
            h.assertTrue(TaskBook.place(List.of("a", "b", "c"), List.of("a", "c", "d"), Set.of(), 3).equals(List.of("a", "d", "c")), "the next one takes the finished one's place");
            h.assertTrue(TaskBook.place(List.of("a", "b", "c"), List.of("a", "b", "c", "m"), Set.of("m"), 3).equals(List.of("a", "b", "m")), "the main path takes the last place");
            h.assertTrue(TaskBook.place(List.of("gone"), List.of("y", "z"), Set.of(), 3).equals(List.of("y", "z")), "a thread that no longer exists gives up its place");
            h.assertTrue(TaskBook.place(List.of(), List.of("a", "b", "c", "d"), Set.of("a", "b", "c", "d"), 3).size() == 3, "never more than the cap, however much must show");
            // a real page: a player the game gives nothing else to, so the rows are exactly these
            AwakenTests.Live live = AwakenTests.survivalPlayer(h, "tasks-slots", BlockPos.ZERO);
            AwakenTests.onEnd(h, live::leave);
            ServerPlayer sp = live.player();
            if (SOURCE.compareAndSet(false, true)) Goals.register(p -> STEPS.getOrDefault(p.getUUID(), List.of()));
            List<Step> mine = new java.util.ArrayList<>();
            STEPS.put(sp.getUUID(), mine);
            AwakenTests.onEnd(h, () -> STEPS.remove(sp.getUUID()));
            Step a1 = row("t/a1", "a", 10), a2 = row("t/a2", "a", 20), b = row("t/b", "b", 10), c = row("t/c", "c", 10), d = row("t/d", "d", 10);
            Step trail = Step.of("trail/x", Goal.BELLS, "Trail").optional().build(), hidden = Step.of("t/h", Goal.BELLS, "Hidden").hidden().build();
            mine.addAll(List.of(a1, a2, b, c, d, trail, hidden));
            TaskBook.Book book = TaskBook.fresh(sp);
            h.assertTrue(book.get("t/h") == null, "a hidden step is never listed");
            List<String> rows = rows(book);
            h.assertTrue(rows.equals(List.of("t/a1", "t/b", "t/c")), "one row per thread, three rows, the trail waiting: " + rows);
            var view = TasksModule.view(sp, book);
            h.assertTrue(rowOf(view, "t/a1").extra().then().equals("Then: t/a2") && rowOf(view, "t/a2").extra().place() == TaskPayloads.LATER, "the next in its thread is named and waits in Later");
            h.assertTrue((view.tabs() >> Goal.BELLS.ordinal() & 1) != 0, "a tab opens when it has something to do");
            // finish a1: a2 comes up in its place, new; nothing else moves
            mine.remove(a1);
            TasksModule.act(sp, new TaskPayloads.Act(TaskPayloads.SEEN, "t/b"));
            book = TaskBook.fresh(sp);
            h.assertTrue(rows(book).equals(List.of("t/a2", "t/b", "t/c")), "the next step takes the finished one's place: " + rows(book));
            view = TasksModule.view(sp, book);
            h.assertTrue(rowOf(view, "t/a2").fresh() && !rowOf(view, "t/b").fresh(), "and it's marked new");
            // set one aside: the next waiting comes up in its place; Undo puts it back exactly where it was
            TasksModule.act(sp, new TaskPayloads.Act(TaskPayloads.ASIDE, "t/b"));
            book = TaskBook.fresh(sp);
            h.assertTrue(rows(book).equals(List.of("t/a2", "t/d", "t/c")), "set aside, the next takes its place: " + rows(book));
            h.assertTrue(rowOf(TasksModule.view(sp, book), "t/b").aside(), "and it waits in Later, marked");
            TasksModule.act(sp, new TaskPayloads.Act(TaskPayloads.PUT_BACK, "t/b"));
            book = TaskBook.fresh(sp);
            h.assertTrue(rows(book).equals(List.of("t/a2", "t/b", "t/c")), "Undo: back where it was: " + rows(book));
            // pin what's in Later: it's on the list, in the last place, and it's the step on top
            TasksModule.act(sp, new TaskPayloads.Act(TaskPayloads.PIN, "t/d"));
            book = TaskBook.fresh(sp);
            h.assertTrue(rows(book).equals(List.of("t/a2", "t/b", "t/d")) && book.top().equals("t/d"), "a pin always shows: " + rows(book));
            TasksModule.act(sp, new TaskPayloads.Act(TaskPayloads.UNPIN, "t/d"));
            // a trail only fills a place nothing else wants, and gives it up when something does
            mine.removeAll(List.of(a2, c, d));
            book = TaskBook.fresh(sp);
            h.assertTrue(rows(book).equals(List.of("t/b", "trail/x")), "a trail fills an empty place: " + rows(book));
            mine.add(c);
            mine.add(a2);
            mine.add(d);
            book = TaskBook.fresh(sp);
            h.assertTrue(!rows(book).contains("trail/x") && rows(book).size() == 3, "and gives it up: " + rows(book));
            h.succeed();
        });

        AwakenTests.registerIsolated("tasks_choose_and_pin", 200, h -> {
            AwakenTests.Live live = AwakenTests.survivalPlayer(h, "tasks-armour", BlockPos.ZERO);
            AwakenTests.onEnd(h, live::leave);
            ServerPlayer sp = live.player();
            Progression.awaken(sp);
            sp.getInventory().clearContent();
            TaskBook.Book book = TaskBook.fresh(sp);
            Step armour = book.get("armour/e");
            h.assertTrue(armour != null && armour.title().equals("Choose your first armour") && armour.path() == Goals.PATH_FIRST_ARMOUR,
                    "a new hunter's first armour is a step on the path: " + keys(book.steps()));
            h.assertTrue(armour.choices().size() >= 2, "every E set is offered: " + armour.choices().size());
            Goals.Choice set = armour.choices().getFirst();
            h.assertTrue(set.show().size() == 4 && !set.needs().isEmpty(), "a set shows its four pieces and what they take");
            // one row per need, never the same item twice
            java.util.Set<String> items = new java.util.HashSet<>();
            for (Goals.Need n : set.needs()) h.assertTrue(items.add(n.item()), "one row per need: " + n.item() + " twice");
            // a pin that isn't a step is refused; a set chosen and the step pinned: the card lists that set's needs
            TasksModule.act(sp, new TaskPayloads.Act(TaskPayloads.PIN, "nothing/here"));
            h.assertTrue(TaskState.of(sp).pinned().isEmpty(), "no pin on a step that isn't there");
            TasksModule.act(sp, new TaskPayloads.Act(TaskPayloads.CHOOSE, "armour/e|" + set.id()));
            TasksModule.act(sp, new TaskPayloads.Act(TaskPayloads.PIN, "armour/e"));
            h.assertTrue(TaskState.of(sp).chosen("armour/e").equals(set.id()) && TaskState.of(sp).pinned().equals("armour/e"), "chosen and pinned");
            var card = TasksModule.card(sp, true);
            h.assertTrue(card != null && card.rows().size() == set.needs().size() && card.title().endsWith(set.title()),
                    "the pinned card lists the chosen set's needs: " + card);
            h.assertTrue(TaskBook.fresh(sp).top().equals("armour/e"), "and the pin is on top");
            TasksModule.act(sp, new TaskPayloads.Act(TaskPayloads.CHOOSE, "armour/e|no_such_set"));
            h.assertTrue(TaskState.of(sp).chosen("armour/e").equals(set.id()), "a set that isn't offered can't be chosen");
            TasksModule.act(sp, new TaskPayloads.Act(TaskPayloads.SEEN, String.valueOf(Goal.ARMOUR.ordinal())));
            h.assertTrue(TaskState.of(sp).seen.contains("armour/e"), "a goal's tab read: its steps are seen");
            h.succeed();
        });
    }

    private static final java.util.concurrent.atomic.AtomicBoolean SOURCE = new java.util.concurrent.atomic.AtomicBoolean();
    private static final java.util.Map<java.util.UUID, List<Step>> STEPS = new java.util.concurrent.ConcurrentHashMap<>();

    private static Step row(String key, String thread, int order) {
        return Step.of(key, Goal.BELLS, key).thread(thread, order).build();
    }

    private static List<String> rows(TaskBook.Book book) {
        List<String> out = new java.util.ArrayList<>();
        for (Step s : book.shelf(Goal.BELLS).rows()) out.add(s.key());
        return out;
    }

    private static TaskPayloads.StepRow rowOf(TaskPayloads.View v, String key) {
        for (TaskPayloads.StepRow r : v.steps()) if (r.key().equals(key)) return r;
        throw new IllegalStateException("no row " + key);
    }

    static String keys(List<Step> steps) {
        StringBuilder b = new StringBuilder();
        for (Step s : steps) b.append(b.isEmpty() ? "" : ", ").append(s.key()).append('@').append(s.path() == Goals.NO_PATH ? "-" : s.path());
        return b.toString();
    }
}
