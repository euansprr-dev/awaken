package dev.awaken.living.bounty;

import dev.awaken.core.PlayDays;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.living.quest.QuestDef;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The Association board's GameTest (SPEC §21.5): three notices, one more a play-day, a turn-in that pays once. */
public final class BountyTests {
    private BountyTests() {}

    public static void register() {
        AwakenTests.register("living_bounty_board", 200, h -> {
            Map<String, BountyDef> before = Bounties.all();
            Map<String, BountyDef> test = new HashMap<>();
            for (int i = 0; i < 5; i++) {
                test.put("awaken:test_cull_" + i, new BountyDef(1, "Test Cull " + i, 0, new QuestDef.Objective("kill", "minecraft:zombie", 2, 8), 0.1, 3, List.of(), ""));
            }
            test.put("awaken:test_far", new BountyDef(1, "Too Hard", 3, new QuestDef.Objective("kill", "minecraft:zombie", 2, 8), 0.1, 3, List.of(), ""));
            Bounties.replace(test);
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            try {
                Progression.awaken(p);
                h.assertTrue(Bounties.board(p), "Hale's post has a board");
                BountyState s = Bounties.state(p);
                h.assertTrue(s.offered.size() == 3, "three notices: " + s.offered);
                h.assertTrue(!s.offered.contains("awaken:test_far"), "only notices for the player's band");
                String id = s.offered.getFirst();
                Bounties.answer(p, Bounties.ACTION + ":" + id, true);
                s = Bounties.state(p);
                h.assertTrue(s.taken.size() == 1 && s.offered.size() == 2, "one taken, two left");
                h.assertTrue(p.getData(dev.awaken.signal.NoticeHistory.HISTORY).entries().stream().anyMatch(e ->
                        e.event().id().startsWith("bounty/taken/" + id) && e.event().details().stream().anyMatch(line -> line.contains("only once each time"))),
                        "accepted bounty has durable authored instructions and payment conditions");
                Bounties.board(p);
                h.assertTrue(Bounties.state(p).offered.size() == 2, "no new notice the same play-day");
                Bounties.state(p).refilled = PlayDays.current(p.level().getServer()) - 1;
                Bounties.board(p);
                h.assertTrue(Bounties.state(p).offered.size() == 3, "a new one the next play-day");
                // two zombie kills ready it; the board turns it in and pays once
                BlockPos at = h.absolutePos(new BlockPos(1, 2, 1));
                kill(h, p, at);
                kill(h, p, at);
                h.assertTrue(Bounties.state(p).taken.getFirst().progress() == 2, "kills count toward it");
                int essence = essence(p);
                Bounties.board(p);
                s = Bounties.state(p);
                h.assertTrue(s.taken.isEmpty() && s.done == 1, "turned in");
                h.assertTrue(essence(p) == essence + 3, "paid its essence");
                Bounties.board(p);
                h.assertTrue(essence(p) == essence + 3, "and only once");
                h.assertTrue(p.getData(dev.awaken.signal.NoticeHistory.HISTORY).entries().stream().filter(e ->
                        e.event().id().startsWith("bounty/paid/" + id)).count() == 1, "reopening Hale does not replay reward history");
            } finally {
                Bounties.replace(before);
            }
            h.succeed();
        });
    }

    private static void kill(net.minecraft.gametest.framework.GameTestHelper h, ServerPlayer p, BlockPos at) {
        LivingEntity e = EntityTypes.ZOMBIE.create(h.getLevel(), EntitySpawnReason.COMMAND);
        if (e == null) return;
        e.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        h.getLevel().addFreshEntity(e);
        e.setData(dev.awaken.living.LivingContent.FORM.get(), dev.awaken.living.combat.Forms.Form.NONE); // the test land is far out: no revenant should stand up
        e.hurtServer(h.getLevel(), p.damageSources().playerAttack(p), 10000);
        if (e.isAlive()) e.discard();
    }

    private static int essence(ServerPlayer p) {
        int n = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(CoreApi.ESSENCE_SHARD.get())) n += inv.getItem(i).getCount();
        return n;
    }
}
