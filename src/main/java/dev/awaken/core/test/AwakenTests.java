package dev.awaken.core.test;

import dev.awaken.Awaken;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * GameTest registration helper. Modules call {@link #register} during their init; tests run headless with
 * {@code tools/gradle.sh 1200 runGameTestServer} or in game with {@code /test run awaken:*}.
 */
public final class AwakenTests {
    private AwakenTests() {}

    public static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, Awaken.MODID);

    private record Pending(String name, DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> fn, int maxTicks, boolean isolated) {}

    private static final List<Pending> PENDING = new ArrayList<>();

    /** Registers a test named awaken:&lt;name&gt; in a 1x1x1 empty structure. */
    public static void register(String name, int maxTicks, Consumer<GameTestHelper> test) {
        PENDING.add(new Pending(name, FUNCTIONS.register(name, () -> withLoadedArea(test)), maxTicks, false));
    }

    /**
     * Tests land at arbitrary coordinates and often straddle a chunk border; the neighbouring chunk may not be
     * entity-ticking, which hides its entities from area searches. Force-load the chunks around every test.
     */
    private static Consumer<GameTestHelper> withLoadedArea(Consumer<GameTestHelper> test) {
        return h -> {
            var level = h.getLevel();
            var origin = h.absolutePos(net.minecraft.core.BlockPos.ZERO);
            int cx = origin.getX() >> 4, cz = origin.getZ() >> 4;
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) level.setChunkForced(cx + dx, cz + dz, true);
            // load them now rather than waiting on the chunk workers: under load (builds running beside the test server)
            // async loading took over a minute and tests waiting for "entity ticking" timed out
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) level.getChunk(cx + dx, cz + dz);
            // Note: don't defer the body with runAfterDelay. Vanilla iterates its scheduled callbacks while running them,
            // so scheduling from inside one can run entries twice. Tests that need settled chunks delay their own actions.
            test.accept(h);
        };
    }

    /**
     * Like {@link #register} but the test runs in a batch of its own (its own test environment), so area effects or
     * blocks from other tests can never reach it. Use for tests that build structures or use area powers.
     */
    public static void registerIsolated(String name, int maxTicks, Consumer<GameTestHelper> test) {
        PENDING.add(new Pending(name, FUNCTIONS.register(name, () -> withLoadedArea(test)), maxTicks, true));
    }

    /** A real survival player at {@code relative} (mock players are always creative): its packets land in its channel. */
    public record Live(net.minecraft.server.level.ServerPlayer player, io.netty.channel.embedded.EmbeddedChannel channel) {
        /** Every packet sent to this player so far, oldest first (drains the channel). */
        public java.util.List<Object> sent() {
            java.util.List<Object> out = new ArrayList<>();
            Object m;
            while ((m = channel.readOutbound()) != null) out.add(m);
            return out;
        }

        public void leave() {
            player.level().getServer().getPlayerList().remove(player);
        }
    }

    /** A survival player that the server sees as online (for rules that skip creative players). */
    public static Live survivalPlayer(GameTestHelper h, String name, net.minecraft.core.BlockPos relative) {
        var level = h.getLevel();
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), name), false);
        var player = new net.minecraft.server.level.ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        var channel = new io.netty.channel.embedded.EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.connection.markClientLoaded();
        var at = h.absolutePos(relative);
        if (player.level() != level) player.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, java.util.Set.of(), 0, 0, true);
        player.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return new Live(player, channel);
    }

    public static void init(IEventBus modBus) {
        RegistryDump.register();
        WorldgenTests.register();
        FUNCTIONS.register(modBus);
        modBus.addListener(AwakenTests::onRegisterTests);
    }

    private static void onRegisterTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(Awaken.id("default"));
        for (Pending p : PENDING) {
            Holder<TestEnvironmentDefinition<?>> e = p.isolated() ? event.registerEnvironment(Awaken.id("iso_" + p.name())) : env;
            event.registerTest(Awaken.id(p.name()), new FunctionGameTestInstance(p.fn().getKey(),
                    new TestData<>(e, Identifier.withDefaultNamespace("empty"), p.maxTicks(), 0, true)));
        }
    }
}
