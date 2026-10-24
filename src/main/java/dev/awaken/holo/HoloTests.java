package dev.awaken.holo;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.holo.ui.HoloTimeline;
import dev.awaken.holo.ui.InventoryWells;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * GameTests for the holo module's rules: {@code awaken:holo_state} (unknown kinds, the rate limit, who hears about it)
 * and the side-agnostic rules the client follows ({@code holo.ui}): the inventory's timeline and wells.
 */
final class HoloTests {
    private HoloTests() {}

    /** Records the players a relay went to without sending anything to the mock connections. */
    private static Predicate<ServerPlayer> recorder(List<ServerPlayer> into) {
        return p -> {
            into.add(p);
            return false;
        };
    }

    static void register() {
        AwakenTests.register("holo_state_rejects_unknown_kind", 40, h -> {
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            List<ServerPlayer> got = new ArrayList<>();
            h.assertTrue(HoloRelay.receive(p, 7, recorder(got)) == HoloRelay.Result.REJECTED, "kind 7 is refused");
            h.assertTrue(HoloRelay.receive(p, -1, recorder(got)) == HoloRelay.Result.REJECTED, "kind -1 is refused");
            h.assertTrue(HoloRelay.kind(p) == HoloKind.NONE, "a refused kind changes nothing");
            h.assertTrue(HoloRelay.receive(p, HoloKind.STATUS.id, recorder(got)) == HoloRelay.Result.RELAYED, "a known kind is taken");
            h.assertTrue(HoloRelay.kind(p) == HoloKind.STATUS, "now STATUS");
            h.assertTrue(HoloRelay.receive(p, HoloKind.STATUS.id, recorder(got)) == HoloRelay.Result.UNCHANGED, "the same kind twice is not relayed");
            HoloRelay.forget(p);
            h.succeed();
        });

        AwakenTests.register("holo_state_rate_limited", 100, h -> {
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            List<ServerPlayer> got = new ArrayList<>();
            int relayed = 0, deferred = 0;
            // six changes in one tick: four go out, the rest wait and only the last one is sent later
            for (int i = 0; i < 6; i++) {
                int kind = i % 2 == 0 ? HoloKind.STATUS.id : HoloKind.NONE.id;
                HoloRelay.Result r = HoloRelay.receive(p, kind, recorder(got));
                if (r == HoloRelay.Result.RELAYED) relayed++;
                if (r == HoloRelay.Result.DEFERRED) deferred++;
            }
            h.assertTrue(relayed == HoloRelay.MAX_PER_WINDOW, "four relayed in the first second, was " + relayed);
            h.assertTrue(deferred == 2, "two deferred, was " + deferred);
            h.assertTrue(HoloRelay.kind(p) == HoloKind.NONE, "the state others see is the fourth change until the window frees");
            HoloRelay.Result seventh = HoloRelay.receive(p, HoloKind.INVENTORY.id, recorder(got));
            h.assertTrue(seventh == HoloRelay.Result.DEFERRED, "a seventh change also waits");
            int start = h.getLevel().getServer().getTickCount();
            h.succeedWhen(() -> {
                int now = h.getLevel().getServer().getTickCount();
                h.assertTrue(now - start >= HoloRelay.WINDOW_TICKS, "waiting for the window");
                HoloRelay.flush(h.getLevel().getServer(), recorder(got));
                h.assertTrue(HoloRelay.kind(p) == HoloKind.INVENTORY, "the last change arrives once the window frees");
                HoloRelay.forget(p);
            });
        });

        AwakenTests.register("holo_inventory_timeline", 20, h -> {
            HoloTimeline k = new HoloTimeline(), inv = new HoloTimeline(HoloTimeline.INVENTORY_SPEED);
            h.assertTrue(Math.abs(k.openRealMs() - 450f) < 0.01f && Math.abs(k.closeRealMs() - 250f) < 0.01f, "the K menu keeps 450 / 250 ms");
            h.assertTrue(inv.openRealMs() <= 180.01f, "the inventory opens in at most 180 ms, was " + inv.openRealMs());
            // played frame by frame at 60 fps: fully open within 180 ms (one frame of rounding), landing only once grown
            inv.open();
            float ms = 0, lastLanded = 0;
            boolean grownBeforeLanding = true;
            while (!inv.fullyOpen() && ms < 1000) {
                inv.advance(1000f / 60f);
                ms += 1000f / 60f;
                float landed = inv.landed();
                h.assertTrue(landed >= lastLanded, "the flat face only ever comes up while opening");
                if (landed > 0 && inv.grow(false) < 0.999f) grownBeforeLanding = false;
                lastLanded = landed;
            }
            h.assertTrue(ms <= 180f + 1000f / 60f, "fully open after " + ms + " ms");
            h.assertTrue(grownBeforeLanding, "the 3D panel is in its place before the flat face takes over");
            h.assertTrue(inv.landed() == 1f, "landed when open");
            inv.seek(300f);
            h.assertTrue(inv.landed() == 0f, "nothing hands over while it still unfolds");
            inv.seek(HoloTimeline.OPEN_MS);
            inv.close();
            ms = 0;
            while (inv.active() && ms < 1000) {
                inv.advance(1000f / 60f);
                ms += 1000f / 60f;
            }
            h.assertTrue(ms <= 100f + 1000f / 60f, "the inventory folds away in 100 ms, took " + ms);
            h.succeed();
        });

        AwakenTests.register("holo_inventory_wells", 40, h -> {
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            Map<InventoryWells.Kind, Integer> count = new EnumMap<>(InventoryWells.Kind.class);
            List<int[]> wells = new ArrayList<>();
            for (Slot s : p.inventoryMenu.slots) {
                InventoryWells.Kind k = InventoryWells.kind(s);
                count.merge(k, 1, Integer::sum);
                int x = InventoryWells.x(s, k), y = InventoryWells.y(s, k), n = InventoryWells.size(k);
                h.assertTrue(x >= 0 && y >= 0 && x + n <= 176 && y + n <= 166, "slot " + s.index + "'s well is inside the panel");
                h.assertTrue(x < s.x && y < s.y && x + n > s.x + 16 && y + n > s.y + 16, "slot " + s.index + "'s well surrounds its item");
                // your figure's alcove (vanilla draws the player in 26,8 - 75,78) is never under a well
                h.assertFalse(x < 75 && x + n > 26 && y < 78 && y + n > 8, "slot " + s.index + "'s well clears the figure");
                for (int[] o : wells) {
                    h.assertFalse(x < o[0] + o[2] && o[0] < x + n && y < o[1] + o[2] && o[1] < y + n, "slot " + s.index + "'s well overlaps another");
                }
                wells.add(new int[] {x, y, n});
            }
            h.assertTrue(count.equals(Map.of(InventoryWells.Kind.RESULT, 1, InventoryWells.Kind.CRAFT, 4, InventoryWells.Kind.ARMOR, 4,
                    InventoryWells.Kind.MAIN, 27, InventoryWells.Kind.HOTBAR, 9, InventoryWells.Kind.OFFHAND, 1)), "every slot of the menu has its kind: " + count);
            p.getInventory().setSelectedSlot(3);
            int selected = 0;
            for (Slot s : p.inventoryMenu.slots) {
                if (!InventoryWells.selected(s, p.getInventory())) continue;
                selected++;
                h.assertTrue(s.getContainerSlot() == 3, "the lit well is the hand's");
            }
            h.assertTrue(selected == 1, "exactly one well is the hand's, was " + selected);
            h.succeed();
        });

        AwakenTests.register("holo_state_relays_only_to_trackers", 200, h -> {
            ServerPlayer sender = h.makeMockServerPlayerInLevel();
            ServerPlayer near = h.makeMockServerPlayerInLevel();
            ServerPlayer far = h.makeMockServerPlayerInLevel();
            var base = h.absoluteVec(new net.minecraft.world.phys.Vec3(0.5, 1, 0.5));
            sender.snapTo(base.x, base.y, base.z);
            near.snapTo(base.x + 3, base.y, base.z);
            far.snapTo(base.x + 2000, base.y, base.z);
            ServerChunkCache cache = h.getLevel().getChunkSource();
            h.succeedWhen(() -> {
                // mock players never send movement, so tell the chunk map where they are (what the packet handler does)
                for (ServerPlayer p : List.of(sender, near, far)) cache.move(p);
                List<ServerPlayer> watching = cache.chunkMap.getPlayersWatching(sender);
                h.assertTrue(watching.contains(near), "waiting for the near player to see the sender");
                h.assertFalse(watching.contains(far), "the far player is out of range");
                List<ServerPlayer> got = new ArrayList<>();
                HoloRelay.Result r = HoloRelay.receive(sender, HoloKind.STATUS.id, recorder(got));
                h.assertTrue(r == HoloRelay.Result.RELAYED, "relayed, was " + r);
                h.assertTrue(got.contains(near), "the player beside the sender hears it");
                h.assertFalse(got.contains(far), "a player out of range doesn't");
                h.assertFalse(got.contains(sender), "the sender isn't told about itself");
                HoloRelay.forget(sender);
                for (ServerPlayer p : List.of(sender, near, far)) h.getLevel().getServer().getPlayerList().remove(p);
            });
        });
    }
}
