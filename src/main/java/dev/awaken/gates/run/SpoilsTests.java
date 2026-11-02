package dev.awaken.gates.run;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.GateLiveTests;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateService;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.content.Layout;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.content.Twist;
import dev.awaken.gates.world.FloorLayout;
import dev.awaken.gates.world.GateLayout;
import dev.awaken.Awaken;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.util.List;

/**
 * Gate spoils (docs/gate-spoils/PLAN.md): where gate loot went missing before the fix. Each test reproduces one path
 * and asserts what happens today; the build turns them into the rules that nothing is lost.
 */
public final class SpoilsTests {
    private SpoilsTests() {}

    public static void register() {
        // A full Satchel refuses what you walk over: it stays on the gate's floor and is still there after you leave.
        AwakenTests.registerIsolated("gates_spoils_repro_full_satchel", 100, h -> {
            ServerLevel level = h.getLevel();
            GateService.useTestLevel(level);
            GateRecord g = null;
            ServerPlayer p = null;
            try {
                GatePlan plan = GateLiveTests.plan(8181, Theme.BELL_CRYPT, Layout.LABYRINTH, Twist.NONE, 0);
                g = GateLiveTests.standUp(h, plan, 0, 1);
                p = GateLiveTests.player(h);
                h.assertTrue(Runs.enter(p, g), "entered");
                RunState run = Runs.run(p);
                fill(run.satchel);
                ItemEntity loot = new ItemEntity(level, p.getX(), p.getY(), p.getZ(), new ItemStack(Items.DIAMOND, 4));
                level.addFreshEntity(loot);
                loot.playerTouch(p);
                h.assertTrue(loot.isAlive() && loot.getItem().getCount() == 4, "today: a full Satchel leaves the loot on the floor");
                h.assertTrue(p.getInventory().countItem(Items.DIAMOND) == 0, "and it doesn't reach your pockets either");
                Runs.extract(p, true);
                h.assertFalse(Runs.run(p).active(), "extracted");
                h.assertTrue(loot.isAlive() && GateLiveTests.cellBox(level, g).contains(loot.position()),
                        "today: the loot is left lying in the gate's cell, which is erased once the gate is released");
            } finally {
                GateLiveTests.teardown(level, p, g);
            }
            h.succeed();
        });

        // A cache opened with a full Satchel spills onto the floor, and its toast still says it went into the Satchel.
        AwakenTests.registerIsolated("gates_spoils_repro_cache_full", 100, h -> {
            ServerLevel level = h.getLevel();
            GateService.useTestLevel(level);
            GateRecord g = null;
            ServerPlayer p = null;
            try {
                GatePlan plan = GateLiveTests.plan(8282, Theme.BELL_CRYPT, Layout.LABYRINTH, Twist.NONE, 0);
                g = GateLiveTests.standUp(h, plan, 0, 1);
                p = GateLiveTests.player(h);
                h.assertTrue(Runs.enter(p, g), "entered");
                fill(Runs.run(p).satchel);
                int before = itemsNear(level, p);
                Interactions.openCache(p, p.blockPosition().above(2), GatesRegistry.CACHE.get().defaultBlockState());
                h.assertTrue(itemsNear(level, p) > before, "today: the cache's loot lands on the floor beside it");
            } finally {
                GateLiveTests.teardown(level, p, g);
            }
            h.succeed();
        });

        // Coming out with full pockets: what doesn't fit is thrown at your feet, where it despawns in five minutes.
        AwakenTests.registerIsolated("gates_spoils_repro_full_pockets", 100, h -> {
            ServerLevel level = h.getLevel();
            GateService.useTestLevel(level);
            GateRecord g = null;
            ServerPlayer p = null;
            try {
                GatePlan plan = GateLiveTests.plan(8383, Theme.BELL_CRYPT, Layout.LABYRINTH, Twist.NONE, 0);
                g = GateLiveTests.standUp(h, plan, 0, 1);
                p = GateLiveTests.player(h);
                p.setGameMode(GameType.SURVIVAL); // a mock player is creative, and creative pockets swallow what doesn't fit
                for (int i = 0; i < p.getInventory().getNonEquipmentItems().size(); i++) p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
                h.assertTrue(Runs.enter(p, g), "entered");
                RunState run = Runs.run(p);
                for (Item it : List.of(Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT, Items.ENDER_PEARL, Items.GOLDEN_APPLE)) {
                    Satchel.add(run.satchel, new ItemStack(it, 3));
                }
                FloorLayout f = GateLayout.of(plan).floor(0, 1);
                Runs.floorCleared(p, g, run, f);
                Runs.extract(p, false);
                h.assertFalse(Runs.run(p).active(), "extracted");
                List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(6));
                h.assertTrue(dropped.size() >= 5, "today: the five stacks were thrown at your feet, " + dropped.size());
                h.assertTrue(dropped.stream().allMatch(e -> e.lifespan == 6000), "and each despawns after five minutes");
                for (ItemEntity e : dropped) e.discard();
            } finally {
                GateLiveTests.teardown(level, p, g);
            }
            h.succeed();
        });
    }

    /** 27 different full stacks. */
    static void fill(List<ItemStack> satchel) {
        int n = 0;
        for (Item it : BuiltInRegistries.ITEM) {
            if (Satchel.count(satchel) >= Satchel.SLOTS) break;
            if (it == Items.AIR || it == Items.DIAMOND || BuiltInRegistries.ITEM.getKey(it).getNamespace().equals(Awaken.MODID)) continue;
            if (n++ % 7 != 0) continue;
            Satchel.add(satchel, new ItemStack(it, it.getDefaultMaxStackSize()));
        }
    }

    static int itemsNear(ServerLevel level, ServerPlayer p) {
        return level.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(6)).size();
    }
}
