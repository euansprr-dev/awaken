package dev.awaken.core.test;

import dev.awaken.Awaken;
import net.minecraft.core.BlockPos;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * GameTest registration helper. Modules call {@link #register} during their init; tests run headless with
 * {@code tools/gradle.sh 1200 runGameTestServer} or in game with {@code /test run awaken:*}.
 * <p>
 * {@code -Ptests=terrain_,anomaly_} (system property {@code awaken.tests}) registers only the tests whose names start
 * with one of those prefixes, for fast local iteration; empty or absent means every test.
 */
public final class AwakenTests {
    private AwakenTests() {}

    public static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, Awaken.MODID);

    /** env: the test environment's path ("default" is shared; any other name gives the test a batch of its own). */
    private record Pending(String name, DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> fn, int maxTicks, String env) {}

    private static final List<Pending> PENDING = new ArrayList<>();

    /** Name prefixes from -Ptests (system property awaken.tests); empty means every test. */
    private static final List<String> FILTER = Arrays.stream(System.getProperty("awaken.tests", "").split(","))
            .map(String::strip).filter(s -> !s.isEmpty()).toList();

    /** True when the test filter lets this test name through (always, without a filter). */
    public static boolean selected(String name) {
        return FILTER.isEmpty() || FILTER.stream().anyMatch(name::startsWith);
    }

    /** Registers a test named awaken:&lt;name&gt; in a 1x1x1 empty structure. */
    public static void register(String name, int maxTicks, Consumer<GameTestHelper> test) {
        PENDING.add(new Pending(name, FUNCTIONS.register(name, () -> withLoadedArea(test)), maxTicks, "default"));
    }

    /**
     * Tests land at arbitrary coordinates and often straddle a chunk border; the neighbouring chunk may not be
     * entity-ticking, which hides its entities from area searches. Force-load the chunks around every test.
     */
    private static Consumer<GameTestHelper> withLoadedArea(Consumer<GameTestHelper> test) {
        return h -> {
            var level = h.getLevel();
            var origin = h.absolutePos(BlockPos.ZERO);
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
        PENDING.add(new Pending(name, FUNCTIONS.register(name, () -> withLoadedArea(test)), maxTicks, "iso_" + name));
    }

    /**
     * A test in an environment of its own, named by the caller, without the force-loaded area of {@link #register}
     * (the powers smoke test). Goes through here so the -Ptests filter sees it.
     */
    public static void registerBare(String name, String env, int maxTicks, Consumer<GameTestHelper> test) {
        PENDING.add(new Pending(name, FUNCTIONS.register(name, () -> test), maxTicks, env));
    }

    public static void init(IEventBus modBus) {
        RegistryDump.register();
        WorldgenTests.register();
        FUNCTIONS.register(modBus);
        modBus.addListener(AwakenTests::onRegisterTests);
    }

    private static void onRegisterTests(RegisterGameTestsEvent event) {
        Map<String, Holder<TestEnvironmentDefinition<?>>> envs = new HashMap<>();
        envs.put("default", event.registerEnvironment(Awaken.id("default")));
        int registered = 0;
        for (Pending p : PENDING) {
            if (!selected(p.name())) continue;
            Holder<TestEnvironmentDefinition<?>> e = envs.computeIfAbsent(p.env(), n -> event.registerEnvironment(Awaken.id(n)));
            event.registerTest(Awaken.id(p.name()), new FunctionGameTestInstance(p.fn().getKey(),
                    new TestData<>(e, Identifier.withDefaultNamespace("empty"), p.maxTicks(), 0, true)));
            registered++;
        }
        if (FILTER.isEmpty()) return;
        // the GameTest server exits with a bare "found no tests" warning when nothing is registered: say why instead
        if (registered == 0) {
            String msg = "-Ptests=" + String.join(",", FILTER) + " matches none of the " + PENDING.size()
                    + " AWAKEN GameTests (the filter takes comma-separated name prefixes, e.g. -Ptests=terrain_,anomaly_)";
            Awaken.LOG.error("========= TEST FILTER MATCHES NOTHING: {} =========", msg);
            throw new IllegalStateException(msg);
        }
        Awaken.LOG.info("Test filter -Ptests={}: {} of {} GameTests registered", String.join(",", FILTER), registered, PENDING.size());
    }
}
