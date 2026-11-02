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

    static String keys(List<Step> steps) {
        StringBuilder b = new StringBuilder();
        for (Step s : steps) b.append(b.isEmpty() ? "" : ", ").append(s.key()).append('@').append(s.path() == Goals.NO_PATH ? "-" : s.path());
        return b.toString();
    }
}
