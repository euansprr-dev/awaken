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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.util.List;

/**
 * Gate spoils (SPEC §13, "Gate spoils"; docs/gate-spoils/PLAN.md): nothing a gate gives you is lost. One test per
 * mechanism; the first three were the reproductions of where loot went missing, turned round.
 */
public final class SpoilsTests {
    private SpoilsTests() {}

    public static void register() {
        // Past 27 stacks, what you walk over and what a cache holds both go in; nothing is left on the floor.
        AwakenTests.registerIsolated("gates_spoils_satchel_never_refuses", 100, h -> {
            ServerLevel level = h.getLevel();
            GateService.useTestLevel(level);
            GateRecord g = null;
            ServerPlayer p = null;
            try {
                g = GateLiveTests.standUp(h, GateLiveTests.plan(8181, Theme.BELL_CRYPT, Layout.LABYRINTH, Twist.NONE, 0), 0, 1);
                p = GateLiveTests.player(h);
                h.assertTrue(Runs.enter(p, g), "entered");
                RunState run = Runs.run(p);
                Spoils.devFill(p, 40, false);
                h.assertTrue(Satchel.count(run.satchel) == 40, "40 stacks, was " + Satchel.count(run.satchel));
                ItemEntity loot = new ItemEntity(level, p.getX(), p.getY(), p.getZ(), new ItemStack(Items.DIAMOND, 4));
                level.addFreshEntity(loot);
                loot.playerTouch(p);
                h.assertTrue(loot.isRemoved(), "the loot left the floor");
                h.assertTrue(count(run.satchel, Items.DIAMOND) == 4, "and is in the Satchel");
                int before = itemsNear(level, p), carried = Satchel.items(run.satchel);
                Interactions.openCache(p, p.blockPosition().above(2), GatesRegistry.CACHE.get().defaultBlockState());
                h.assertTrue(Satchel.items(run.satchel) > carried, "the cache went into the Satchel");
                h.assertTrue(itemsNear(level, p) == before, "and nothing of it landed on the floor");
            } finally {
                GateLiveTests.teardown(level, p, g);
            }
            h.succeed();
        });

        // Coming out with full pockets: what doesn't fit is held for you (none of it on the ground), it survives a save,
        // and a take moves it into your pockets.
        AwakenTests.registerIsolated("gates_spoils_held_for_you", 100, h -> {
            ServerLevel level = h.getLevel();
            GateService.useTestLevel(level);
            GateRecord g = null;
            ServerPlayer p = null;
            try {
                GatePlan plan = GateLiveTests.plan(8383, Theme.BELL_CRYPT, Layout.LABYRINTH, Twist.NONE, 0);
                g = GateLiveTests.standUp(h, plan, 0, 1);
                p = GateLiveTests.player(h);
                fillPockets(p);
                h.assertTrue(Runs.enter(p, g), "entered");
                RunState run = Runs.run(p);
                List<Item> loot = List.of(Items.DIAMOND, Items.EMERALD, Items.GOLD_INGOT, Items.ENDER_PEARL, Items.GOLDEN_APPLE);
                for (Item it : loot) Satchel.add(run.satchel, new ItemStack(it, 3));
                FloorLayout f = GateLayout.of(plan).floor(0, 1);
                Runs.floorCleared(p, g, run, f);
                Runs.extract(p, false);
                h.assertFalse(Runs.run(p).active(), "extracted");
                h.assertTrue(p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(6)).isEmpty(), "nothing thrown at your feet");
                Held held = Spoils.held(p);
                h.assertTrue(Satchel.count(held.items) == 5, "all five stacks are held for you, " + Satchel.count(held.items));
                var ops = RegistryOps.create(NbtOps.INSTANCE, level.registryAccess());
                Held back = Held.MAP_CODEC.codec().parse(ops, Held.MAP_CODEC.codec().encodeStart(ops, held).getOrThrow()).getOrThrow();
                h.assertTrue(Satchel.items(back.items) == 15, "held survives a save");
                p.getInventory().setItem(0, ItemStack.EMPTY);
                int at = -1;
                for (int i = 0; i < held.items.size(); i++) if (held.items.get(i).is(Items.DIAMOND)) at = i;
                h.assertTrue(Spoils.take(p, at, Items.EMERALD, false) == 0, "a take checks the item at that index");
                h.assertTrue(Spoils.take(p, at, Items.DIAMOND, false) == 3, "a take moves it");
                h.assertTrue(p.getInventory().countItem(Items.DIAMOND) == 3 && count(held.items, Items.DIAMOND) == 0, "into your pockets");
                h.assertTrue(Satchel.count(held.items) == 4, "the rest stays held");
            } finally {
                GateLiveTests.teardown(level, p, g);
            }
            h.succeed();
        });

        // In a gate, what the game drops for you with full pockets is held; what you throw yourself still drops.
        AwakenTests.registerIsolated("gates_spoils_gifts_never_fall", 100, h -> {
            ServerLevel level = h.getLevel();
            GateService.useTestLevel(level);
            GateRecord g = null;
            ServerPlayer p = null;
            try {
                g = GateLiveTests.standUp(h, GateLiveTests.plan(8282, Theme.BELL_CRYPT, Layout.LABYRINTH, Twist.NONE, 0), 0, 1);
                p = GateLiveTests.player(h);
                fillPockets(p);
                h.assertTrue(Runs.enter(p, g), "entered");
                int before = itemsNear(level, p);
                ItemStack gift = new ItemStack(Items.EMERALD, 2);
                if (!p.getInventory().add(gift)) h.assertTrue(p.drop(gift, false, Prediction.SERVER_ONLY) == null, "another module's give-else-drop is caught");
                Spoils.give(p, new ItemStack(Items.GOLD_INGOT, 1));
                h.assertTrue(itemsNear(level, p) == before, "neither gift reached the floor");
                Held held = Spoils.held(p);
                h.assertTrue(count(held.items, Items.EMERALD) == 2 && count(held.items, Items.GOLD_INGOT) == 1, "both are held for you");
                ItemEntity thrown = p.drop(new ItemStack(Items.STICK), true, Prediction.SERVER_ONLY);
                h.assertTrue(thrown != null && count(held.items, Items.STICK) == 0, "a throw from your hand still drops");
                thrown.discard();
            } finally {
                GateLiveTests.teardown(level, p, g);
            }
            h.succeed();
        });

        // A gate monster's drops are in its killer's Satchel the moment it dies; nothing of them is left in the world.
        AwakenTests.registerIsolated("gates_spoils_drops_fly_in", 100, h -> {
            ServerLevel level = h.getLevel();
            GateService.useTestLevel(level);
            GateRecord g = null;
            ServerPlayer p = null;
            try {
                g = GateLiveTests.standUp(h, GateLiveTests.plan(8484, Theme.BELL_CRYPT, Layout.LABYRINTH, Twist.NONE, 0), 0, 1);
                p = GateLiveTests.player(h);
                h.assertTrue(Runs.enter(p, g), "entered");
                Zombie z = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
                h.assertTrue(z != null, "a zombie");
                z.setPos(p.getX() + 2, p.getY(), p.getZ());
                z.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NAUTILUS_SHELL));
                z.setDropChance(EquipmentSlot.MAINHAND, 2F); // always drops
                level.addFreshEntity(z);
                z.hurtServer(level, p.damageSources().playerAttack(p), 1000F);
                h.assertTrue(z.isDeadOrDying(), "it fell");
                h.assertTrue(count(Runs.run(p).satchel, Items.NAUTILUS_SHELL) == 1, "its drop is in the killer's Satchel");
                h.assertTrue(level.getEntitiesOfClass(ItemEntity.class, z.getBoundingBox().inflate(4)).isEmpty(),
                        "no item lies where it fell (the flight is drawn on the client only)");
            } finally {
                GateLiveTests.teardown(level, p, g);
            }
            h.succeed();
        });

        // A tagged item (the Holo band, bell items) picked up in a gate goes to your pockets, never the Satchel.
        AwakenTests.registerIsolated("gates_spoils_never_in_satchel", 100, h -> {
            ServerLevel level = h.getLevel();
            GateService.useTestLevel(level);
            GateRecord g = null;
            ServerPlayer p = null;
            try {
                Item band = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("awaken", "holo_band"));
                h.assertTrue(new ItemStack(band).is(Spoils.NEVER_IN_SATCHEL), "the band is tagged");
                g = GateLiveTests.standUp(h, GateLiveTests.plan(8585, Theme.BELL_CRYPT, Layout.LABYRINTH, Twist.NONE, 0), 0, 1);
                p = GateLiveTests.player(h);
                p.setGameMode(GameType.SURVIVAL);
                h.assertTrue(Runs.enter(p, g), "entered");
                ItemEntity loot = new ItemEntity(level, p.getX(), p.getY(), p.getZ(), new ItemStack(band));
                level.addFreshEntity(loot);
                loot.playerTouch(p);
                h.assertTrue(loot.isRemoved() && p.getInventory().countItem(band) == 1, "into your pockets");
                h.assertTrue(count(Runs.run(p).satchel, band) == 0, "not the Satchel");
            } finally {
                GateLiveTests.teardown(level, p, g);
            }
            h.succeed();
        });
    }

    /** Survival (creative pockets swallow what doesn't fit) with every pocket full of cobblestone. */
    static void fillPockets(ServerPlayer p) {
        p.setGameMode(GameType.SURVIVAL);
        for (int i = 0; i < p.getInventory().getNonEquipmentItems().size(); i++) p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
    }

    static int count(List<ItemStack> list, Item item) {
        int n = 0;
        for (ItemStack s : list) if (s.is(item)) n += s.getCount();
        return n;
    }

    static int itemsNear(ServerLevel level, ServerPlayer p) {
        return level.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(6)).size();
    }
}
