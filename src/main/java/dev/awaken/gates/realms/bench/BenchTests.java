package dev.awaken.gates.realms.bench;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.GateLiveTests;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateState;
import dev.awaken.gates.realms.Blueprints;
import dev.awaken.gates.realms.Realms;
import dev.awaken.gates.run.Runs;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** The bench's own GameTests: the bench itself (asked for through the environment) and the straight-in walk's command. */
public final class BenchTests {
    private BenchTests() {}

    public static void register() {
        // does nothing unless tools/bench/bench.sh asked for it
        AwakenTests.register("realm_bench", 30000, h -> {
            Bench.runIfAsked();
            h.succeed();
        });
        // gate realm_enter (SPEC §50.8): inside at the Mouth with nothing built in the overworld, and a failure a tour can read
        AwakenTests.registerIsolated("realm_enter_straight_in", 200, h -> {
            ServerLevel level = h.getLevel();
            MinecraftServer server = level.getServer();
            if (Blueprints.get("awaken:amber_reach").isEmpty()) h.fail("Amber Reach's blueprint isn't loaded");
            ServerPlayer p = GateLiveTests.player(h);
            List<String> said = new ArrayList<>();
            CommandSource ear = new CommandSource() {
                @Override
                public void sendSystemMessage(Component message) {
                    said.add(message.getString());
                }

                @Override
                public boolean acceptsSuccess() {
                    return true;
                }

                @Override
                public boolean acceptsFailure() {
                    return true;
                }

                @Override
                public boolean shouldInformAdmins() {
                    return false;
                }
            };
            var src = server.createCommandSourceStack().withSource(ear).withEntity(p).withLevel(level);
            GateRecord g = null;
            try {
                GateLiveTests.useLevel(level); // the test server has no gate dimensions of its own
                server.getCommands().performPrefixedCommand(src, "awaken gate realm_enter E awaken:no_such_place");
                h.assertFalse(Runs.run(p).active(), "a Realm that isn't there was entered");
                h.assertTrue(said.stream().anyMatch(s -> s.contains("not in a Realm") && s.contains("no Realm is called awaken:no_such_place")), "the failure says why, in the words a tour reads: " + said);
                server.getCommands().performPrefixedCommand(src, "awaken gate realm_enter E awaken:amber_reach 7");
                g = Runs.gateOf(p);
                h.assertTrue(g != null && g.realm.isPresent(), "inside a Realm at once: " + said);
                h.assertTrue(g.realm.get().blueprint().equals("awaken:amber_reach") && g.realm.get().seed() == 7, "the Realm and the seed asked for: " + g.realm.get());
                h.assertTrue(!g.structurePlaced && g.state == GateState.ACTIVE, "nothing stands in the overworld (" + g.state + ")");
                BlockPos mouth = Realms.entrance(g);
                h.assertTrue(p.blockPosition().distManhattan(mouth) <= 1, "at the Mouth " + mouth.toShortString() + ", not " + p.blockPosition().toShortString());
                h.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(p.getYRot() - Realms.entranceYaw(g))) < 1, "facing the way you arrive");
                said.clear();
                server.getCommands().performPrefixedCommand(src, "awaken gate realm_enter E awaken:amber_reach");
                h.assertTrue(said.stream().anyMatch(s -> s.contains("still inside a gate")), "a second time from inside says why not: " + said);
            } finally {
                GateLiveTests.teardown(level, p, g);
                GateLiveTests.useLevel(null);
                server.getPlayerList().remove(p);
            }
            h.succeed();
        });
    }
}
