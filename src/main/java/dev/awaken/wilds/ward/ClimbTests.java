package dev.awaken.wilds.ward;

import dev.awaken.Awaken;
import dev.awaken.core.api.ArrivalHooks;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.core.api.WardHooks;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.progression.Progression;
import dev.awaken.wilds.WildsContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Optional;
import java.util.UUID;

/** The climbs' GameTests (SPEC §17.25): a block refused at the first one, and a cache's inner seal. */
final class ClimbTests {
    private ClimbTests() {}

    static void register() {
        AwakenTests.registerIsolated("wilds_ward_refuses_climb", 200, h -> {
            ServerLevel level = h.getLevel();
            BlockPos at = h.absolutePos(new BlockPos(1, 2, 1));
            AwakenTests.Live live = AwakenTests.survivalPlayer(h, "climber", new BlockPos(1, 2, 1));
            ServerPlayer p = live.player();
            WardHooks.Source spire = (l, pos, out) -> {
                BoundingBox box = new BoundingBox(at).inflatedBy(20);
                if (box.isInside(pos)) out.accept(new WardHooks.Ward("ruin:test", Wards.SPIRE, box));
            };
            WardHooks.register(spire);
            HearthHooks.Lookup hearths = HearthHooks.installed();
            try {
                BlockPos spot = at.east();
                BlockState stone = Blocks.STONE.defaultBlockState();
                Progression.setCounter(p, ClimbWard.SHOWN + "spire", 0);
                // the first block is refused outright, not queued to crumble (the crumble's three seconds are a nerd-pole)
                h.assertTrue(ClimbWard.refuses(p, level, spot, stone), "a spire refuses the first block");
                h.assertFalse(WardEvents.placed(p, level, spot, stone), "and never queues it to crumble");
                h.assertTrue(Crumble.pending() == 0, "nothing waits to crumble at a climb");
                // the first refusal at a kind of climb offers the card once; the visit is remembered
                h.assertTrue(Progression.profile(p).counter(ClimbWard.SHOWN + "spire") == 1, "the Show me card is offered the first time");
                h.assertTrue(pendingCard(p), "as a decision waiting for Y");
                h.assertTrue(ClimbWard.refuses(p, level, spot.above(), stone), "every block after it too");
                // a bucket's fluid (null placed) is refused the same way
                h.assertTrue(ClimbWard.refuses(p, level, spot.north(), null), "a bucket's water too");
                // a Hearth never suspends a climb's ward; its walls hold there and speak without a door
                HearthHooks.install(new HearthHooks.Lookup() {
                    @Override public Optional<BlockPos> nearest(ServerLevel l, BlockPos pos, int radius) { return Optional.of(at); }
                    @Override public Optional<BlockPos> hearthOf(ServerLevel l, UUID player) { return Optional.of(at); }
                    @Override public boolean inside(ServerLevel l, BlockPos pos) { return true; }
                });
                h.assertTrue(ClimbWard.refuses(p, level, spot, stone), "a Hearth beside a spire doesn't make it a staircase");
                BlockState plating = dev.awaken.above.AboveBlocks.PLATING.get().defaultBlockState();
                h.assertTrue(Wards.sealed(level, spot, plating), "a spire's walls hold inside a Hearth");
                h.assertTrue(WardEvents.holdWords(level, spot).equals("spire"), "and its wall line names no door");
                // creative builds; outside the ward is the open world
                p.setGameMode(GameType.CREATIVE);
                h.assertFalse(ClimbWard.refuses(p, level, spot, stone), "creative builds freely");
                p.setGameMode(GameType.SURVIVAL);
                h.assertFalse(ClimbWard.refuses(p, level, at.above(40), stone), "outside the ward nothing is refused");
            } finally {
                HearthHooks.install(hearths);
                WardHooks.unregister(spire);
                ClimbWard.forget(p.getUUID());
                live.leave();
            }
            h.succeed();
        });

        AwakenTests.registerIsolated("wilds_cache_inner_seal", 200, h -> {
            ServerLevel level = h.getLevel();
            BlockPos at = h.absolutePos(new BlockPos(1, 2, 1));
            AwakenTests.Live one = AwakenTests.survivalPlayer(h, "first", new BlockPos(1, 2, 1));
            AwakenTests.Live two = AwakenTests.survivalPlayer(h, "second", new BlockPos(1, 2, 2));
            WardHooks.Source sky = (l, pos, out) -> {
                BoundingBox box = new BoundingBox(at).inflatedBy(20);
                if (box.isInside(pos)) out.accept(new WardHooks.Ward("ruin:test", Wards.SKY, box));
            };
            WardHooks.register(sky);
            boolean[] moved = {false}, line = {false};
            ArrivalHooks.Tracker real = ArrivalHooks.installed();
            ArrivalHooks.install(new ArrivalHooks.Tracker() {
                @Override public boolean byMovement(ServerPlayer q) { return moved[0]; }
                @Override public boolean byLine(ServerPlayer q) { return line[0]; }
            });
            try {
                BlockPos cache = at.east(2);
                level.setBlock(cache, Blocks.CHEST.defaultBlockState(), 3);
                ResourceKey<net.minecraft.world.level.storage.loot.LootTable> table = ResourceKey.create(Registries.LOOT_TABLE, Awaken.id("chests/precursor_sky"));
                RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), cache, table);
                ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(cache);
                ServerPlayer p = one.player(), q = two.player();

                // built its way up: the cache stays shut and keeps everything
                h.assertFalse(Caches.opens(p, level, cache), "a built arrival finds the cache shut");
                h.assertTrue(chest.getLootTable() != null, "and its roll waits, unspent");
                // a clever climb (pearls, elytra): the ordinary roll, the best one still behind the seal
                moved[0] = true;
                h.assertTrue(Caches.opens(p, level, cache) && chest.getLootTable() == null, "a clever climb opens the ordinary roll");
                InnerSeal seal = chest.getExistingDataOrNull(WildsContent.INNER_SEAL);
                h.assertTrue(seal != null && seal.cache() && seal.opened().isEmpty(), "and the inner seal holds: " + seal);
                h.assertTrue(shards(chest) == 0, "no Power Core Shard without the line");
                // by the line: the inner seal opens to this climber, once
                line[0] = true;
                h.assertTrue(Caches.opens(p, level, cache) && shards(chest) == 2, "the line opens the inner seal: " + shards(chest));
                h.assertTrue(Caches.opens(p, level, cache) && shards(chest) == 2, "once per climber");
                // another climber by the line gets their own best roll: one open never spends it for anyone else
                h.assertTrue(Caches.opens(q, level, cache) && shards(chest) == 4, "a second climber gets theirs: " + shards(chest));
                // nobody breaks it open either
                h.assertTrue(WardEvents.refuses(p, level, cache, level.getBlockState(cache)), "the ward keeps the cache whole");
            } finally {
                ArrivalHooks.install(real);
                WardHooks.unregister(sky);
                one.leave();
                two.leave();
            }
            h.succeed();
        });
    }

    private static boolean pendingCard(ServerPlayer p) {
        return dev.awaken.signal.Signal.isPending(p, ClimbWard.SHOW_ME + ":spire");
    }

    private static int shards(ChestBlockEntity chest) {
        int n = 0;
        var shard = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Awaken.id("core_shard"));
        for (int i = 0; i < chest.getContainerSize(); i++) if (chest.getItem(i).is(shard)) n += chest.getItem(i).getCount();
        return n;
    }
}
