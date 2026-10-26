package dev.awaken.living.quest;

import com.mojang.serialization.JsonOps;
import dev.awaken.core.api.HorizonHooks;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.living.wayfarer.Scenes;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Reward;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The quest engine's GameTest (SPEC §21.5): one data chain through every kind of step. */
public final class QuestTests {
    private QuestTests() {}

    static final String CHAIN = "awaken:test_errand";
    /** The one player the test's need answers yes for (the Horizon's reach answers can't be removed). */
    private static volatile ServerPlayer reach;

    static {
        HorizonHooks.addReach((p, need) -> need.equals("awaken_test") && p == reach);
    }

    private static Reward levels(double l) {
        return new Reward(l, 0, Optional.empty(), 0, Optional.empty(), Optional.empty(), 0, List.of());
    }

    private static QuestDef.Step step(String id, String type, String target, int count, double reward, Optional<String> needs) {
        return new QuestDef.Step(id, new QuestDef.Objective(type, target, count, 4.0), id, List.of(), List.of(), levels(reward),
                needs, "", Optional.empty(), Optional.empty(), false, Optional.empty());
    }

    public static void register() {
        AwakenTests.register("living_quest_engine", 200, h -> {
            Map<String, QuestDef> before = QuestBook.all();
            QuestDef def = new QuestDef(1, "Test Errand", 0, List.of(), "resident", "tester", List.of(
                    step("reach", "reach", "site", 1, 0.05, Optional.empty()),
                    step("talk", "talk", "tester", 1, 0.05, Optional.empty()),
                    step("kill", "kill", "minecraft:zombie", 2, 0.05, Optional.of("tier:awaken_test")),
                    step("collect", "collect", "minecraft:stick", 2, 0.05, Optional.empty())),
                    Optional.of("awaken:wrens_hook"), levels(0.1), "", List.of());
            Map<String, QuestDef> with = new HashMap<>(before);
            with.put(CHAIN, def);
            QuestBook.replace(with);
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            try {
                Progression.awaken(p);
                h.assertTrue(QuestDef.problems(CHAIN, def).isEmpty(), "the test chain is well formed");
                h.assertTrue(QuestEngine.start(p, CHAIN), "a chain found can be taken");
                QuestState s = QuestEngine.state(p);
                h.assertTrue(s.has(CHAIN) && s.tracked.equals(CHAIN), "taken and tracked");
                // a resident's chain gets a scene for its places
                long scene = s.active.getFirst().scene().orElse(0L);
                BlockPos site = Scenes.marker(p.level().getServer(), scene, "site");
                h.assertTrue(site != null, "the chain's site is marked");
                // reach: standing at the site
                p.setPos(site.getX() + 0.5, site.getY(), site.getZ() + 0.5);
                QuestEngine.poll(p);
                h.assertTrue(stepOf(p) == 1, "reaching the site moves it on");
                // talk: paid once (claim id)
                double xp = Progression.profile(p).xp() + Progression.profile(p).level() * 1e6;
                QuestEngine.advance(p, "talk", "tester", 1);
                h.assertTrue(stepOf(p) == 2, "talking moves it on");
                h.assertTrue(Progression.profile(p).counter("awaken:claim/quest/" + CHAIN + "/talk") == 1, "the step's reward is claimed");
                h.assertTrue(Progression.profile(p).xp() + Progression.profile(p).level() * 1e6 > xp, "and paid");
                // a step that needs a tier the player lacks waits
                QuestEngine.advance(p, "kill", "minecraft:zombie", 2);
                h.assertTrue(stepOf(p) == 2 && QuestEngine.state(p).active.getFirst().progress() == 0, "a tier-gated step waits");
                reach = p;
                QuestEngine.advance(p, "kill", "minecraft:zombie", 1);
                h.assertTrue(stepOf(p) == 2 && QuestEngine.state(p).active.getFirst().progress() == 1, "with the tier it counts");
                QuestEngine.advance(p, "kill", "minecraft:skeleton", 1);
                h.assertTrue(QuestEngine.state(p).active.getFirst().progress() == 1, "only what it asks for counts");
                QuestEngine.advance(p, "kill", "minecraft:zombie", 1);
                h.assertTrue(stepOf(p) == 3, "two kills move it on");
                // relog: the state survives its codec
                QuestState again = QuestState.CODEC.parse(JsonOps.INSTANCE, QuestState.CODEC.encodeStart(JsonOps.INSTANCE, QuestEngine.state(p)).getOrThrow()).getOrThrow();
                h.assertTrue(again.has(CHAIN) && again.active.getFirst().step() == 3 && again.tracked.equals(CHAIN), "a relog keeps the chain where it was");
                // collect: holding them finishes the chain, with its relic
                p.getInventory().add(new ItemStack(Items.STICK, 2));
                QuestEngine.poll(p);
                s = QuestEngine.state(p);
                h.assertTrue(!s.has(CHAIN) && s.done.contains(CHAIN), "the chain is finished");
                h.assertTrue(hooks(p) == 1, "its relic is given");
                h.assertTrue(Scenes.scene(p.level().getServer(), scene) == null, "its scene is gone");
                h.assertTrue(!QuestEngine.start(p, CHAIN), "a finished chain can't be taken again");
                // a relic and a finish pay once, even if the chain somehow ran twice
                s.done.remove(CHAIN);
                h.assertTrue(QuestEngine.start(p, CHAIN), "(forced) taken again");
                QuestEngine.state(p).active.set(0, QuestEngine.state(p).active.getFirst().withStep(3));
                QuestEngine.poll(p);
                h.assertTrue(hooks(p) == 1, "the relic is given once");
            } finally {
                reach = null;
                QuestBook.replace(before);
                QuestState s = QuestEngine.state(p);
                for (QuestState.Active a : s.active) a.scene().ifPresent(id -> Scenes.done(p.level().getServer(), id));
            }
            h.succeed();
        });
    }

    private static int stepOf(ServerPlayer p) {
        return QuestEngine.state(p).active.getFirst().step();
    }

    private static int hooks(ServerPlayer p) {
        int n = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(Relics.WRENS_HOOK.get())) n += inv.getItem(i).getCount();
        return n;
    }
}
