package dev.awaken.gates;

import dev.awaken.core.api.GateHooks;
import dev.awaken.core.api.StoryHooks;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.content.Layout;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.content.Twist;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;
import java.util.Optional;

/** GameTests for what other modules see of gates ({@link GateHooks}) and for the story's hold on them. */
final class GateHookTests {
    private GateHookTests() {}

    static void register() {
        registerWandering();
        AwakenTests.registerIsolated("gates_hooks_view_stabilise_calm", 900, h -> {
            var level = h.getLevel();
            MinecraftServer server = level.getServer();
            BlockPos base = h.absolutePos(BlockPos.ZERO).offset(14, 0, 14);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base.getX(), base.getZ()) - 1;
            GatePlan plan = GateTests.testPlan(77, Rank.D, Theme.HIVE, Layout.LABYRINTH, Twist.NONE);
            GateRecord g = GateService.create(server, GateRegistry.get(server).nextId(), plan, new BlockPos(base.getX(), y, base.getZ()), 0,
                    Optional.empty(), 2, List.of("test"), false);
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            h.startSequence()
                    .thenWaitUntil(() -> h.assertTrue(g.state == GateState.READY, "gate became READY, was " + g.state))
                    .thenExecute(() -> {
                        var v = GateHooks.get(level, g.key());
                        h.assertTrue(v.isPresent(), "GateHooks.get finds the gate");
                        h.assertTrue(v.get().theme().equals(Theme.HIVE.themeId()) && v.get().rank() == Rank.D.ordinal()
                                && v.get().floors() == plan.floors() && v.get().state().equals("READY"), "the view matches the plan");
                        h.assertTrue(v.get().mouth().equals(g.mouth()), "the view knows where to stand");
                        h.assertTrue(GateHooks.near(level, g.pos, 32).stream().anyMatch(x -> x.id().equals(g.key())), "near() finds it");
                        long before = g.breaksOnPlayDay;
                        h.assertTrue(GateHooks.stabilise(level, g.key(), 3) && g.breaksOnPlayDay == before + 3, "the Stabiliser pushes the break back");
                        h.assertFalse(GateHooks.stabilise(level, "not-a-gate", 3), "unknown ids are refused");
                        long scouted = Progression.profile(p).counter("awaken:gates_scouted");
                        GateHooks.markScouted(p, g.key());
                        GateHooks.markScouted(p, g.key());
                        h.assertTrue(Gates.isScouted(p, g.id) && Progression.profile(p).counter("awaken:gates_scouted") == scouted + 1, "scouted, counted once");
                        h.assertFalse(GateHooks.inGate(p), "standing outside");

                        // "Break the Signal": the gates go calm. Nothing breaks and the Stabiliser has nothing to do.
                        StoryHooks.State story = StoryHooks.installed();
                        try {
                            StoryHooks.install(calm(story));
                            h.assertTrue(g.neverBreaks(server) && GateHooks.get(level, g.key()).get().breaksOnPlayDay() == Long.MAX_VALUE, "calm gates never break");
                            h.assertFalse(GateHooks.stabilise(level, g.key(), 1), "nothing to stabilise");
                            Breaks.onNewPlayDay(server, g.breaksOnPlayDay + 5);
                            h.assertTrue(g.state == GateState.READY, "the break day passes and the gate stands, state " + g.state);
                        } finally {
                            StoryHooks.install(story);
                        }
                        GateService.release(server, g);
                    })
                    .thenSucceed();
        });
    }

    static void registerWandering() {
        // A wandering gate (anomalies): forms near the player at their rank, never breaks, and fades on time.
        AwakenTests.registerIsolated("gates_wandering_gate_fades", 400, h -> {
            var level = h.getLevel();
            MinecraftServer server = level.getServer();
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            Progression.awaken(p);
            GateRecord[] made = new GateRecord[1];
            h.startSequence()
                    .thenExecute(() -> {
                        GateService.useTestLevel(level);
                        try {
                            var id = GateHooks.openWandering(p, p.blockPosition().offset(12, 0, 0), 1, 20 * 60 * 5);
                            h.assertTrue(id.isPresent(), "a wandering gate formed");
                            GateRecord g = GateRegistry.get(server).gate(Integer.parseInt(id.get()));
                            made[0] = g;
                            h.assertTrue(g.wandering() && !g.scripted && g.plan.floors() == 1, "short, wandering, not a story gate");
                            h.assertTrue(g.neverBreaks(server), "it never breaks");
                            h.assertTrue(GateHooks.get(level, id.get()).get().breaksOnPlayDay() == Long.MAX_VALUE, "the view agrees");
                            g.expiresAt = GateService.now(server); // its time is up
                        } finally {
                            GateService.useTestLevel(null);
                        }
                    })
                    .thenWaitUntil(() -> h.assertTrue(made[0].state == GateState.RELEASED, "it fades when nobody is inside, state " + made[0].state))
                    .thenSucceed();
        });
    }

    private static StoryHooks.State calm(StoryHooks.State base) {
        return new StoryHooks.State() {
            @Override public boolean gatesCalm(MinecraftServer s) { return true; }
            @Override public boolean colourRestored(MinecraftServer s) { return base.colourRestored(s); }
            @Override public int ascension(MinecraftServer s) { return base.ascension(s); }
        };
    }
}
