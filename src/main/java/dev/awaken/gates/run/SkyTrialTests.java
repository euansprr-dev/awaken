package dev.awaken.gates.run;

import dev.awaken.body.ascent.Ascent;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.GateLiveTests;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateState;
import dev.awaken.gates.Gates;
import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.world.FloorLayout;
import dev.awaken.gates.world.GateLayout;
import dev.awaken.gates.world.Room;
import dev.awaken.gates.world.SetPiece;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Sky Leviathan's trial (SPEC §17.18): one Sky Archipelago floor whose arena is open sky (4x4 slots of islands
 * over the cloud sea, the floor's whole height) held by the theme's own Lord; the gate never breaks; stepping into the
 * arena gives Skyborn (the edge) and raises the Leviathan; its fall clears the gate and completes the trial.
 */
public final class SkyTrialTests {
    private SkyTrialTests() {}

    public static void register() {
        AwakenTests.register("gates_sky_trial", 400, h -> {
            GatePlan plan = Gates.skybornTrialPlan(0x5CA1L);
            h.assertTrue(plan.skybornTrial() && !plan.leaperTrial() && plan.floors() == 1 && plan.lordId().equals(Theme.SKY_ARCHIPELAGO.bossId()),
                    "the trial is one floor of its own, its Lord the Sky Leviathan");
            FloorLayout f = GateLayout.of(plan).floor(0, 1);
            Room arena = f.room(f.end);
            h.assertTrue(f.rooms.size() == 3 && arena.type == Room.Type.BOSS && arena.gw == 4 && arena.gh == 4 && arena.shape == Room.Shape.ISLANDS
                    && arena.height == 27 - arena.dy && arena.piece == SetPiece.ARENA_SKY,
                    "the start, the antechamber and the open sky: 4x4 islands at the floor's full height (" + arena.gw + "x" + arena.gh + ", "
                            + arena.height + " high, " + arena.piece + ")");
            ServerLevel level = h.getLevel();
            GateLiveTests.useTestLevel(level);
            GateRecord g = null;
            ServerPlayer p = null;
            try {
                g = GateLiveTests.standUp(h, plan, 0, 1);
                h.assertTrue(g.neverBreaks(level.getServer()), "a trial gate never breaks");
                g.transition(GateState.ACTIVE);
                p = GateLiveTests.player(h);
                h.assertTrue(Runs.enter(p, g), "entered");
                h.assertFalse(Ascent.has(p, Ascent.Tier.SKYBORN), "no flight yet");
                Rooms.enter(level, p, g, f, arena, arena.centerX(), arena.centerZ());
                h.assertTrue(Ascent.has(p, Ascent.Tier.SKYBORN), "at the open sky the sky remembers you: Skyborn");
                h.assertTrue(GateMobs.alive(new GateMobs.Key(g.id, f.area, f.floor, arena.id)) >= 1, "and the Leviathan takes the sky");
                Runs.lordDefeated(level, g, f, null, p);
                int flags = p.getData(dev.awaken.body.ascent.AscentContent.MEMORY).flags();
                h.assertTrue(g.bossDead && g.state == GateState.CLEARED && (flags & dev.awaken.body.ascent.AscentMemory.SKYBORN_TRIAL_DONE) != 0,
                        "its fall clears the gate and the trial is done");
            } finally {
                GateLiveTests.teardown(level, p, g);
            }
            h.succeed();
        });
    }
}
