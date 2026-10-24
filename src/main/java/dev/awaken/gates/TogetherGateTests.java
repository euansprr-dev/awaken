package dev.awaken.gates;

import dev.awaken.core.RegionRecycler;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.content.Layout;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.content.Twist;
import dev.awaken.hearth.HearthData;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.hearth.Household;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * GameTests for the Director and cells with several players (SPEC "Together"): the standing-gate cap is per owner,
 * a veteran's gates never form near (or break on) a newcomer's Hearth, and cells are reused while running.
 */
final class TogetherGateTests {
    private TogetherGateTests() {}

    static void register() {
        AwakenTests.registerIsolated("together_gates_director_per_owner", 100, h -> {
            ServerLevel level = h.getLevel();
            var server = level.getServer();
            ServerPlayer vet = GateLiveTests.player(h), late = GateLiveTests.player(h), friend = GateLiveTests.player(h);
            HearthData d = HearthData.get(server);
            BlockPos anchor = h.absolutePos(new BlockPos(2, 1, 2));
            HearthRecord home = new HearthRecord(late.getUUID(), "late", anchor, HearthService.OVERWORLD);
            GateRecord g = null;
            try {
                d.put(home);
                Household.join(server, home, friend);
                g = GateLiveTests.standUp(h, GateLiveTests.plan(4242, Theme.BELL_CRYPT, Layout.LABYRINTH, Twist.NONE, 0));
                g.owner = Optional.of(vet.getUUID());
                h.assertTrue(Director.ownedBy(g, vet) && !Director.ownedBy(g, late), "the cap counts a gate for its owner only");
                g.owner = Optional.empty();
                h.assertTrue(Director.ownedBy(g, vet) && Director.ownedBy(g, late), "an ownerless (old) gate counts for anyone, as before");

                BlockPos near = anchor.offset(120, 0, 0);
                h.assertTrue(Director.nearForeignHearth(level, near, vet), "a veteran's gate may not form 120 blocks from a newcomer's Hearth");
                h.assertFalse(Director.nearForeignHearth(level, near, late), "the newcomer's own gates may");
                h.assertFalse(Director.nearForeignHearth(level, near, friend), "and so may a member's");
                BlockPos farAway = anchor.offset(400, 0, 0);
                if (dev.awaken.core.api.HearthHooks.within(level, farAway, Director.FOREIGN_HEARTH_RADIUS).isEmpty()) {
                    h.assertFalse(Director.nearForeignHearth(level, farAway, vet), "far enough away it may");
                }

                // a veteran's gate that stands near the newcomer's Hearth (placed after it formed) folds rather than breaks
                g.owner = Optional.of(vet.getUUID());
                g.pos = anchor.offset(60, 0, 0);
                g.breaksOnPlayDay = 0;
                g.state = GateState.READY;
                h.assertTrue(Breaks.foreignHearthNear(server, g), "it stands near a Hearth its owner doesn't share");
                Breaks.newPlayDay(server, g, 5);
                h.assertTrue(g.state == GateState.RELEASED, "so it folds away instead of breaking, " + g.state);

                // the newcomer's own gate stands at home: it breaks as it always did
                g.owner = Optional.of(late.getUUID());
                h.assertFalse(Breaks.foreignHearthNear(server, g), "the newcomer's own gate is at home");
                g.owner = Optional.of(friend.getUUID());
                h.assertFalse(Breaks.foreignHearthNear(server, g), "so is a member's");
            } finally {
                d.remove(late.getUUID());
                GateLiveTests.teardown(level, null, g);
                for (ServerPlayer p : new ServerPlayer[]{vet, late, friend}) server.getPlayerList().remove(p);
            }
            h.succeed();
        });

        AwakenTests.register("together_cells_reused_while_running", 40, h -> {
            var server = h.getLevel().getServer();
            // gate cells: once erased while running, a released cell is free again and its entities' generation moves on
            GateRegistry reg = GateRegistry.get(server);
            int cell = reg.allocateCell();
            int gen = reg.cellGen(cell);
            reg.releaseCell(cell);
            reg.cellRecycled(cell);
            h.assertTrue(reg.cellGen(cell) == gen + 1, "a recycled cell has a new generation");
            h.assertFalse(reg.releasedCells().contains(cell), "and is free again");
            h.assertTrue(reg.hasFreeCell(), "it can be handed out again");
            h.assertTrue(RegionRecycler.square(0, 0, 32).length == 1024, "a cell's erase batch is its 32 x 32 chunks");

            // dream cells: a cell whose dream ended comes back for the next dream
            int first = dev.awaken.lucidium.world.Cells.allocate();
            dev.awaken.lucidium.world.Cells.release(server, first);
            int next = dev.awaken.lucidium.world.Cells.allocate();
            h.assertTrue(next == first, "the next dream reuses the cell (" + next + " vs " + first + ")");
            dev.awaken.lucidium.world.Cells.release(server, next);
            h.succeed();
        });
    }
}
