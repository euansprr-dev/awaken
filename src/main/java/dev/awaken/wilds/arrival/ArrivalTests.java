package dev.awaken.wilds.arrival;

import dev.awaken.above.ruin.RuinBuilder;
import dev.awaken.core.api.ArrivalHooks;
import dev.awaken.core.api.ArrivalHooks.Way;
import dev.awaken.core.api.WardHooks;
import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** The arrival history's GameTest (SPEC §17): the window, a tower, a visit that remembers it, a power's move, the best roll. */
public final class ArrivalTests {
    private ArrivalTests() {}

    public static void register() {
        AwakenTests.registerIsolated("wilds_arrival_history", 200, h -> {
            ServerLevel level = h.getLevel();
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            BlockPos at = h.absolutePos(new BlockPos(1, 2, 1));
            Arrival.forget(p.getUUID());
            Arrival.State s = Arrival.state(p);
            long[] t = {Math.floorDiv(level.getGameTime(), Arrival.SAMPLE) * Arrival.SAMPLE + 1000};
            boolean[] warded = {false};
            WardHooks.Source stand = (l, pos, out) -> {
                if (warded[0]) out.accept(new WardHooks.Ward("test:site", "test", new BoundingBox(at).inflatedBy(40)));
            };
            WardHooks.register(stand);
            try {
                // walking: every sample reads as walking, and that is movement
                walk(p, s, t, at, 40);
                h.assertTrue(s.log.count(Way.WALK) >= 7 && !s.log.contains(Way.BUILT), "walking fills the history: " + s.log.toList());
                h.assertTrue(Arrival.verdict(p, t[0]), "walking is arriving by movement");

                // a tower: standing on a block you placed spoils the window
                s.placed.add(BlockPos.asLong(at.getX(), at.getY() - 1, at.getZ()), t[0]);
                stand(p, s, t, at, 10);
                h.assertTrue(s.log.contains(Way.BUILT) && !Arrival.verdict(p, t[0]), "a tower is building, not movement");

                // stepping into a site freezes that verdict: 20 s of walking around inside doesn't clean it
                warded[0] = true;
                Arrival.visit(p, s, t[0]);
                walk(p, s, t, at, ArrivalLog.SIZE * Arrival.SAMPLE + 20);
                h.assertTrue(!s.log.contains(Way.BUILT) && !Arrival.verdict(p, t[0]), "the visit remembers the tower");

                // a logout inside it changes nothing: back in, the visit is still the tower's (before, no record read as moving)
                Arrival.leave(p.getUUID(), t[0]);
                h.assertFalse(Arrival.verdict(p, t[0]), "a relog keeps the tower's visit");
                s = Arrival.state(p);

                // gone long enough, back in on foot: a fresh visit by movement
                warded[0] = false;
                walk(p, s, t, at, Arrival.VISIT_GRACE + 20);
                warded[0] = true;
                Arrival.visit(p, s, t[0]);
                h.assertTrue(Arrival.verdict(p, t[0]), "walked back in: by movement");

                // a power's move is movement too, and outranks walking in its quarter second
                s.powerUntil = t[0] + 40; // what ArrivalHooks.report(p, POWER, 40) sets, on this test's clock
                walk(p, s, t, at, 10);
                h.assertTrue(s.log.toList().getLast() == Way.POWER, "a dash reads as a power's move: " + s.log.toList());

                // the sky platform's best roll pays only by movement
                Item shard = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("awaken", "core_shard"));
                LootTable sky = level.getServer().reloadableRegistries().getLootTable(RuinBuilder.loot("precursor_sky"));
                h.assertTrue(sky != LootTable.EMPTY, "the sky platform has its own table");
                ArrivalHooks.Tracker real = ArrivalHooks.installed();
                for (boolean moved : new boolean[]{true, false}) {
                    ArrivalHooks.install(new ArrivalHooks.Tracker() {
                        @Override public boolean byMovement(ServerPlayer q) { return moved; }
                    });
                    try {
                        int found = 0;
                        for (int i = 0; i < 12; i++) {
                            List<ItemStack> items = new ArrayList<>();
                            sky.getRandomItems(new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(at))
                                    .withParameter(LootContextParams.THIS_ENTITY, p).create(LootContextParamSets.CHEST), items::add);
                            for (ItemStack it : items) if (it.is(shard)) found++;
                        }
                        h.assertTrue(moved ? found >= 12 : found == 0, (moved ? "by movement the best roll always pays: " : "a tower never gets it: ") + found);
                    } finally {
                        ArrivalHooks.install(real);
                    }
                }
            } finally {
                WardHooks.unregister(stand);
                Arrival.forget(p.getUUID());
            }
            h.succeed();
        });
    }

    /** Ticks of walking east along the ground, two blocks clear of the tower block under {@code at}. */
    private static void walk(ServerPlayer p, Arrival.State s, long[] t, BlockPos at, int ticks) {
        for (int i = 0; i < ticks; i++) {
            p.setPos(at.getX() + 2.5 + (t[0] % 40) * 0.2, at.getY(), at.getZ() + 0.5);
            p.setOnGround(true);
            Arrival.step(p, s, t[0]++);
        }
    }

    /** Ticks of standing still on the block under {@code at}. */
    private static void stand(ServerPlayer p, Arrival.State s, long[] t, BlockPos at, int ticks) {
        for (int i = 0; i < ticks; i++) {
            p.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            p.setOnGround(true);
            Arrival.step(p, s, t[0]++);
        }
    }
}
