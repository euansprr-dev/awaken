package dev.awaken.gates.run;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.FloorState;
import dev.awaken.gates.GateLiveTests;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateService;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.Overworld;
import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.content.Layout;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.content.Twist;
import dev.awaken.gates.world.FloorLayout;
import dev.awaken.gates.world.GateLayout;
import dev.awaken.gates.world.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;

/** Regression tests for defects found in the gates review (one test per confirmed failure). */
public final class RunRegressionTests {
    private RunRegressionTests() {}

    public static void register() {
        // An arena whose waves can't take form (the gate is at its monster cap) must not seal the player in.
        AwakenTests.registerIsolated("gates_arena_never_seals_empty", 100, h -> {
            ServerLevel level = h.getLevel();
            GateService.useTestLevel(level);
            GateRecord g = null;
            ServerPlayer p = null;
            try {
                GatePlan plan = GateLiveTests.plan(5151, Theme.BELL_CRYPT, Layout.ARENA_CHAIN, Twist.NONE, 0);
                g = GateLiveTests.standUp(h, plan, 0, 1);
                p = GateLiveTests.player(h);
                FloorLayout f = GateLayout.of(plan).floor(0, 1);
                Room arena = arenaOf(f);
                h.assertTrue(arena != null, "an Arena Chain floor has an arena");
                // bookkeeping-only monsters fill the gate to the arena cap
                for (int i = 0; i < Rooms.ARENA_CAP; i++) {
                    var marker = EntityTypes.MARKER.create(level, EntitySpawnReason.COMMAND);
                    GateMobs.register(marker, new GateMobs.Key(g.id, 0, 1, -1));
                }
                h.assertTrue(Runs.enter(p, g), "entered");
                Rooms.enter(level, p, g, f, arena, 0, 0);
                FloorState fs = g.floor(0, 1);
                h.assertFalse(fs.locked.contains(arena.id), "no monster could come, so the arena stays open");
                h.assertTrue(fs.clearedRooms.contains(arena.id), "and counts as beaten");
            } finally {
                GateLiveTests.teardown(level, p, g);
            }
            h.succeed();
        });

        // When everyone leaves a gate its sealed rooms open; walking back in seals an unfinished fight again.
        AwakenTests.registerIsolated("gates_bars_lift_when_everyone_leaves", 100, h -> {
            ServerLevel level = h.getLevel();
            GateService.useTestLevel(level);
            GateRecord g = null;
            ServerPlayer p = null;
            try {
                GatePlan plan = GateLiveTests.plan(6161, Theme.BELL_CRYPT, Layout.ARENA_CHAIN, Twist.NONE, 0);
                g = GateLiveTests.standUp(h, plan, 0, 1);
                p = GateLiveTests.player(h);
                h.assertTrue(Runs.enter(p, g), "entered");
                FloorLayout f = GateLayout.of(plan).floor(0, 1);
                Room arena = arenaOf(f);
                h.assertTrue(arena != null && !arena.doors.isEmpty(), "an arena with doors");
                Rooms.enter(level, p, g, f, arena, 0, 0);
                FloorState fs = g.floor(0, 1);
                h.assertTrue(fs.locked.contains(arena.id), "the arena seals");
                BlockPos bar = null;
                for (BlockPos b : Rooms.barsPositions(g, f, arena.doors.get(0))) {
                    if (level.getBlockState(b).is(GatesRegistry.BARS.get())) bar = b;
                }
                h.assertTrue(bar != null, "bars stand in the doorway");
                Runs.endRun(p, g, false);
                h.assertFalse(fs.locked.contains(arena.id), "nobody is inside: the arena opens");
                h.assertFalse(level.getBlockState(bar).is(GatesRegistry.BARS.get()), "the doorway is clear");
                h.assertTrue(Runs.enter(p, g), "back in");
                Rooms.enter(level, p, g, f, arena, 0, 0);
                h.assertTrue(fs.locked.contains(arena.id), "the unfinished fight seals again");
            } finally {
                GateLiveTests.teardown(level, p, g);
            }
            h.succeed();
        });

        // A floor's clear bonus is paid once per player, however often the gate is re-entered.
        AwakenTests.registerIsolated("gates_floor_pays_once", 100, h -> {
            ServerLevel level = h.getLevel();
            GateService.useTestLevel(level);
            GateRecord g = null;
            ServerPlayer p = null;
            try {
                GatePlan plan = GateLiveTests.plan(7171, Theme.OVERGROWN_TEMPLE, Layout.LABYRINTH, Twist.NONE, 0);
                g = GateLiveTests.standUp(h, plan, 0, 1);
                p = GateLiveTests.player(h);
                FloorLayout f = GateLayout.of(plan).floor(0, 1);
                h.assertTrue(Runs.enter(p, g), "entered");
                RunState first = Runs.run(p);
                Runs.floorCleared(p, g, first, f);
                h.assertTrue(first.satchelXp > 0, "the first clear pays, " + first.satchelXp);
                Runs.endRun(p, g, false);
                h.assertTrue(Runs.enter(p, g), "back in");
                RunState again = Runs.run(p);
                Runs.floorCleared(p, g, again, f);
                h.assertTrue(again.satchelXp == 0, "the same floor pays nothing twice, " + again.satchelXp);
                h.assertTrue(again.clearedFloor(0, 1), "but its shrine still opens");
            } finally {
                GateLiveTests.teardown(level, p, g);
            }
            h.succeed();
        });

        // A gate never lands on a build.
        AwakenTests.registerIsolated("gates_surface_respects_builds", 40, h -> {
            ServerLevel level = h.getLevel();
            // The check looks 14 blocks round (the largest gate) and 64 for a Hearth, and test plots sit 6 blocks apart on
            // a grid where earlier tests leave their chests, planks, torches and Hearths: own fresh ground well off it,
            // loaded for this check (it skips columns that aren't loaded, and it all runs within this tick)
            BlockPos ground = h.absolutePos(new BlockPos(4, 0, 4)).offset(-3000, 0, -3000);
            for (int cx = (ground.getX() - 16) >> 4; cx <= (ground.getX() + 16) >> 4; cx++) {
                for (int cz = (ground.getZ() - 16) >> 4; cz <= (ground.getZ() + 16) >> 4; cz++) level.getChunk(cx, cz);
            }
            h.assertTrue(Overworld.untouched(level, ground, 0), "open ground is fine");
            BlockPos chest = ground.offset(1, 1, 1);
            level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
            h.assertFalse(Overworld.untouched(level, ground, 0), "a chest next to it: not here");
            level.removeBlock(chest, false);
            level.setBlock(chest, Blocks.OAK_PLANKS.defaultBlockState(), 3);
            h.assertFalse(Overworld.untouched(level, ground, 0), "a plank wall: not here");
            level.removeBlock(chest, false);
            h.succeed();
        });
    }

    private static Room arenaOf(FloorLayout f) {
        for (Room r : f.rooms) if (r.type == Room.Type.ARENA && !r.spawnSpots.isEmpty()) return r;
        return null;
    }
}
