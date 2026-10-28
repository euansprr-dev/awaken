package dev.awaken.delve;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.progression.Progression;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.VanillaGameEvent;

/**
 * The Delver's Pick's verses and gifts (SPEC §34.4), one test per mechanism: each verse wakes once, in order, from its
 * deed or its rite (a deed done early waits), and a lost pick comes back at your verse; Hush keeps a break's sound from
 * creatures; Call takes only that lode's blocks near its heart, at most 24.
 */
final class PickTests {
    private PickTests() {}

    static void register() {
        AwakenTests.register("delve_pick_verses", 40, PickTests::verses);
        AwakenTests.register("delve_pick_hush", 20, PickTests::hush);
        AwakenTests.registerIsolated("delve_pick_call", 200, PickTests::call);
    }

    private static ItemStack carried(ServerPlayer p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(DelveContent.WAKING_PICK.get())) return inv.getItem(i);
        return ItemStack.EMPTY;
    }

    private static void verses(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var live = AwakenTests.survivalPlayer(h, "verses", new BlockPos(1, 1, 1));
        ServerPlayer p = live.player();
        try {
            WakingPick.handOver(p);
            ItemStack pick = carried(p);
            h.assertTrue(PickVerses.verse(pick) == 0, "it comes asleep");
            PickVerses.deed(p, PickVerses.Verse.KINDLE);
            h.assertTrue(PickVerses.verse(pick) == 1, "a heart wakes Kindle");
            PickVerses.deed(p, PickVerses.Verse.KINDLE);
            h.assertTrue(PickVerses.best(p) == 1 && PickVerses.verse(pick) == 1, "once");
            PickVerses.deed(p, PickVerses.Verse.GLASS);
            h.assertTrue(PickVerses.verse(pick) == 1, "Glass waits for the verses before it");
            PickVerses.deed(p, PickVerses.Verse.ECHO);
            h.assertTrue(PickVerses.verse(pick) == 2, "a lamp wakes Echo, and it stops at the Forge's rite: " + PickVerses.verse(pick));
            h.assertFalse(pick.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState()), "Echo mines like iron");

            // the Forge's rite: three diamonds wake Tempered, and the Glass deed done early wakes Glass straight after
            BlockPos forge = h.absolutePos(new BlockPos(2, 1, 2));
            h.assertTrue(PickGifts.forge(p, level, forge, pick), "the Forge takes the pick");
            h.assertTrue(PickVerses.verse(pick) == 2, "not without the diamonds");
            p.getInventory().add(new ItemStack(Items.DIAMOND, 3));
            PickGifts.forge(p, level, forge, pick);
            h.assertTrue(PickVerses.verse(pick) == 4, "Tempered, then Glass: " + PickVerses.verse(pick));
            h.assertTrue(p.getInventory().countItem(Items.DIAMOND) == 0, "the rite takes the diamonds");
            h.assertTrue(pick.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState()), "Tempered mines like diamond");
            h.assertTrue(TrueStrikes.radius(pick) == TrueStrikes.TEMPERED_RADIUS, "true strikes come easier");
            h.assertTrue(pick.getMaxDamage() == PickVerses.Verse.GLASS.uses, "its uses are the verse's");
            // a mend at the Forge
            pick.setDamageValue(800);
            p.getInventory().add(new ItemStack(Items.IRON_INGOT));
            PickGifts.forge(p, level, forge, pick);
            h.assertTrue(pick.getDamageValue() == 800 - pick.getMaxDamage() / 4, "an iron ingot mends a quarter: " + pick.getDamageValue());
            // lost: the Forge makes another, at the verse you'd reached
            p.getInventory().clearContent();
            ItemStack iron = new ItemStack(Items.IRON_INGOT, PickGifts.REFORGE_IRON);
            p.setItemInHand(InteractionHand.MAIN_HAND, iron);
            h.assertTrue(PickGifts.forge(p, level, forge, iron), "the Forge re-forges a lost pick");
            h.assertTrue(PickVerses.verse(carried(p)) == 4, "at the verse you'd reached: " + PickVerses.verse(carried(p)));
            h.assertTrue(Progression.profile(p).counter(PickVerses.WOKEN) == 4, "four verses woken, each once");
        } finally {
            live.leave();
        }
        h.succeed();
    }

    private static void hush(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        ItemStack pick = new ItemStack(DelveContent.WAKING_PICK.get());
        PickVerses.set(pick, PickVerses.Verse.KINDLE.ordinal(), level.registryAccess().lookupOrThrow(Registries.BLOCK));
        p.setItemInHand(InteractionHand.MAIN_HAND, pick);
        Vec3 at = Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 1, 1)));
        var loud = new VanillaGameEvent(level, GameEvent.BLOCK_DESTROY, at, GameEvent.Context.of(p, Blocks.STONE.defaultBlockState()));
        NeoForge.EVENT_BUS.post(loud);
        h.assertFalse(loud.isCanceled(), "a Kindled pick's break is heard");
        PickVerses.set(pick, PickVerses.Verse.HUSH.ordinal(), level.registryAccess().lookupOrThrow(Registries.BLOCK));
        var quiet = new VanillaGameEvent(level, GameEvent.BLOCK_DESTROY, at, GameEvent.Context.of(p, Blocks.STONE.defaultBlockState()));
        NeoForge.EVENT_BUS.post(quiet);
        h.assertTrue(quiet.isCanceled(), "a Hushed pick's break makes no sound to creatures");
        h.assertTrue(PickGifts.hushed(p), "and the deep's noise rule can ask");
        h.succeed();
    }

    /**
     * An iron lode in a stone wall, 40 blocks long, its heart in the middle, a loose iron ore beside it: a Called pick
     * breaking the heart takes the lode's 24 nearest blocks within 8, never one further or not of the lode, and their ore
     * comes to the player.
     */
    private static void call(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(new BlockPos(0, 24, 0));
        for (int dx = -24; dx <= 24; dx++) for (int dy = -2; dy <= 4; dy++) for (int dz = -3; dz <= 3; dz++) {
            boolean air = dz >= 1 && dz <= 2 && dy >= 0 && dy <= 2 && Math.abs(dx) <= 3;
            level.setBlock(o.offset(dx, dy, dz), air ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState(), 2);
        }
        BlockPos heart = o.offset(0, 1, 0);
        LongArrayList cells = new LongArrayList();
        cells.add(heart.asLong());
        for (int dx = -20; dx <= 20; dx++) for (int dy = 0; dy <= 2; dy++) if (dx != 0 || dy != 1) cells.add(o.offset(dx, dy, 0).asLong());
        Lode l = LodePlans.handmade(Ore.IRON, cells.toLongArray());
        LodePlans.EXTRA.add(l);
        BlockPos stray = o.offset(2, 1, -1);
        try {
            for (long c : l.cells()) level.setBlock(BlockPos.of(c), c == l.heart() ? Ore.IRON.heart().defaultBlockState() : Blocks.IRON_ORE.defaultBlockState(), 2);
            level.setBlock(stray, Blocks.IRON_ORE.defaultBlockState(), 2);
            DelveData.get(level).forget(l.id(), heart);
            var live = AwakenTests.survivalPlayer(h, "caller", new BlockPos(0, 25, 1));
            ServerPlayer p = live.player();
            ItemStack pick = new ItemStack(DelveContent.WAKING_PICK.get());
            PickVerses.set(pick, PickVerses.Verse.CALL.ordinal(), level.registryAccess().lookupOrThrow(Registries.BLOCK));
            p.setItemInHand(InteractionHand.MAIN_HAND, pick);
            h.assertTrue(p.gameMode.destroyBlock(heart), "the heart breaks");
            h.runAfterDelay(PickGifts.CALL_TICKS + 6, () -> {
                int taken = 0;
                for (long c : l.cells()) {
                    if (c == l.heart()) continue;
                    BlockPos b = BlockPos.of(c);
                    boolean gone = level.getBlockState(b).isAir();
                    if (gone) taken++;
                    h.assertTrue(!gone || b.distSqr(heart) <= PickGifts.CALL_REACH * PickGifts.CALL_REACH, "never a block beyond 8: " + b.toShortString());
                }
                h.assertTrue(taken == PickGifts.CALL_MAX, "the 24 nearest answer: " + taken);
                h.assertTrue(level.getBlockState(stray).is(Blocks.IRON_ORE), "a block not of the lode stays");
                h.assertTrue(p.getInventory().countItem(Items.RAW_IRON) >= PickGifts.CALL_MAX, "their ore came to the player: " + p.getInventory().countItem(Items.RAW_IRON));
                LodePlans.EXTRA.remove(l);
                live.leave();
                h.succeed();
            });
        } catch (RuntimeException ex) {
            LodePlans.EXTRA.remove(l);
            throw ex;
        }
    }
}
