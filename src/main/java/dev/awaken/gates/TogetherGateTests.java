package dev.awaken.gates;

import dev.awaken.core.PlayDays;
import dev.awaken.core.RegionRecycler;
import dev.awaken.core.api.GateHooks;
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
import java.util.Set;

/**
 * GameTests for the Director and cells with several players (SPEC "Together"): the standing-gate cap is per owner,
 * a veteran's gates never form near (or break on) a newcomer's Hearth, cells are reused while running, and any player
 * who comes close starts a gate's clock.
 */
final class TogetherGateTests {
    private TogetherGateTests() {}

    static void register() {
        // SPEC §7 "Seen first": a gate's clock starts the first time anyone (owner or not) comes within
        // SEEN_RADIUS; until then it never breaks and readers see no clock, and left unseen it folds away quietly. An old
        // save sends standing gates back to unseen and leaves broken ones be.
        AwakenTests.registerIsolated("together_gates_seen_first", 100, h -> {
            ServerLevel level = h.getLevel();
            var server = level.getServer();
            // (a spectator never starts it either; the test's mock players are always creative, so that one isn't here)
            ServerPlayer near = GateLiveTests.player(h), far = GateLiveTests.player(h);
            GateRecord g = null, lone = null;
            try {
                long today = PlayDays.current(server);
                g = GateLiveTests.standUp(h, GateLiveTests.plan(4343, Theme.BELL_CRYPT, Layout.LABYRINTH, Twist.NONE, 0));
                g.structurePlaced = true;
                g.breaksOnPlayDay = 3; // formed on play-day 0, rolled to break three play-days on
                g.foldsOnPlayDay = today + Breaks.UNSEEN_DAYS;
                h.assertTrue(g.waiting(server) && !g.counting(server), "a new gate waits to be seen");
                Breaks.newPlayDay(server, g, today + 4);
                h.assertTrue(g.state == GateState.READY && !g.warned, "unseen, its break day passes and it stands, " + g.state);
                var view = GateHooks.get(level, g.key()).orElseThrow();
                h.assertTrue(view.breaksOnPlayDay() == Long.MAX_VALUE && GateHooks.waiting(level, g.key()), "other modules see no clock");

                BlockPos at = g.pos;
                far.teleportTo(level, at.getX() + Breaks.SEEN_RADIUS + 16, at.getY() + 1, at.getZ(), Set.of(), 0, 0, true);
                Breaks.sight(server, far);
                h.assertTrue(g.waiting(server), "too far off: it still waits, " + g.trace);
                near.teleportTo(level, at.getX() + 30, at.getY() + 1, at.getZ() + 30, Set.of(), 0, 0, true);
                h.assertTrue(Breaks.sight(server, near) >= 1 && g.seenDay == today && g.breaksOnPlayDay == today + 3,
                        "anyone close starts it, as many play-days on as it was rolled with: breaks on " + g.breaksOnPlayDay);
                int traced = g.trace.size();
                Breaks.sight(server, near);
                h.assertTrue(g.trace.size() == traced && GateHooks.get(level, g.key()).orElseThrow().breaksOnPlayDay() == today + 3,
                        "once, and other modules see the clock");
                Breaks.newPlayDay(server, g, today + 2);
                h.assertTrue(g.warned, "a seen gate's clock runs: warned the day before it breaks");

                lone = GateLiveTests.standUp(h, GateLiveTests.plan(4344, Theme.HIVE, Layout.LABYRINTH, Twist.NONE, 0));
                lone.foldsOnPlayDay = today + 1;
                Breaks.newPlayDay(server, lone, today);
                h.assertTrue(lone.state == GateState.READY, "not yet");
                Breaks.newPlayDay(server, lone, today + 1);
                h.assertTrue(lone.state == GateState.RELEASED && lone.trace.getLast().startsWith("folded unseen"),
                        "left unseen, it folds away quietly: " + lone.state);

                h.assertTrue(Breaks.unsee(g, today) && g.waiting(server) && g.foldsOnPlayDay == today + Breaks.UNSEEN_DAYS,
                        "an old save: a standing gate waits to be seen again");
                g.state = GateState.BROKEN;
                g.seenDay = -1;
                h.assertFalse(Breaks.unsee(g, today) || g.waiting(server), "a broken one stays as it was");
            } finally {
                GateLiveTests.teardown(level, null, g, lone);
                if (g != null) {
                    g.structurePlaced = false; // a stand-in: nothing to take down
                    GateService.release(server, g);
                }
                for (ServerPlayer p : new ServerPlayer[]{near, far}) server.getPlayerList().remove(p);
            }
            h.succeed();
        });

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
                g.seenDay = 0;
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
