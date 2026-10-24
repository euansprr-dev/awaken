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

        // A dedicated server reuses gate cells while running: the cell of a's finished gate is erased and becomes b's,
        // a monster saved from a's run leaves when its chunk loads, b's own monster there stays.
        AwakenTests.registerIsolated("together_cells_reused_while_running", 60, h -> {
            ServerLevel level = h.getLevel();
            var server = level.getServer();
            GateRegistry reg = GateRegistry.get(server);
            ServerPlayer a = GateLiveTests.player(h), b = GateLiveTests.player(h);
            net.minecraft.world.entity.Entity fromA = null, fromB = null;
            try {
                int cell = reg.allocateCell(); // a's gate
                int gen = reg.cellGen(cell);
                fromA = inCell(level, cell);
                h.assertFalse(GateEvents.staleInCell(level, fromA, false), "a monster of a's run takes form");
                reg.releaseCell(cell); // a's gate is done
                reg.cellRecycled(cell); // its chunks were erased while running
                h.assertTrue(reg.cellGen(cell) == gen + 1, "a recycled cell has a new generation");
                h.assertTrue(!reg.releasedCells().contains(cell) && reg.freeCells().contains(cell), "and is free again for b's gate");
                fromB = inCell(level, cell);
                h.assertFalse(GateEvents.staleInCell(level, fromB, false), "b's monster takes form there");
                h.assertTrue(GateEvents.staleInCell(level, fromA, true), "a's monster saved from the old gate leaves when loaded");
                h.assertFalse(GateEvents.staleInCell(level, fromB, true), "b's stays when its chunk loads again");
                h.assertFalse(GateEvents.staleInCell(level, a, true) || GateEvents.staleInCell(level, b, true), "players never leave");
                h.assertTrue(RegionRecycler.square(0, 0, 32).length == 1024, "a cell's erase batch is its 32 x 32 chunks");
            } finally {
                if (fromA != null) fromA.discard();
                if (fromB != null) fromB.discard();
                server.getPlayerList().remove(a);
                server.getPlayerList().remove(b);
            }
            h.succeed();
        });
    }

    /** A monster (never added to the world) standing in the given gate cell. */
    private static net.minecraft.world.entity.Entity inCell(ServerLevel level, int cell) {
        var z = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
        z.setPos(dev.awaken.gates.world.Cells.originX(cell) + 40.5, 200, dev.awaken.gates.world.Cells.originZ(cell) + 40.5);
        return z;
    }
}
