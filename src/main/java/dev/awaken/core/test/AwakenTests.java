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

    private record Pending(String name, DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> fn, int maxTicks) {}

    private static final List<Pending> PENDING = new ArrayList<>();

    /** Registers a test named awaken:&lt;name&gt; in a 1x1x1 empty structure. */
    public static void register(String name, int maxTicks, Consumer<GameTestHelper> test) {
        PENDING.add(new Pending(name, FUNCTIONS.register(name, () -> test), maxTicks));
    }

    public static void init(IEventBus modBus) {
        FUNCTIONS.register(modBus);
        modBus.addListener(AwakenTests::onRegisterTests);
    }

    private static void onRegisterTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(Awaken.id("default"));
        for (Pending p : PENDING) {
            event.registerTest(Awaken.id(p.name()), new FunctionGameTestInstance(p.fn().getKey(),
                    new TestData<>(env, Identifier.withDefaultNamespace("empty"), p.maxTicks(), 0, true)));
        }
    }
}
