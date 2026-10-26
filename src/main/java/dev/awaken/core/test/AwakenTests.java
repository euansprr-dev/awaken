package dev.awaken.core.test;

import dev.awaken.Awaken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
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
    /**
     * A CPU-time budget measured on Euan's M4, widened 3x on CI (GitHub's 2-core runners set CI=true and run about 2x
     * slower), so a timing assertion still catches a real regression without failing on a slow machine.
     */
    public static double cpuBudget(double m4Millis) {
        return System.getenv("CI") != null ? m4Millis * 3 : m4Millis;
    }

    public static boolean selected(String name) {
        return FILTER.isEmpty() || FILTER.stream().anyMatch(name::startsWith);
    }

    /** Registers a test named awaken:&lt;name&gt; in a 1x1x1 empty structure. */
    public static void register(String name, int maxTicks, Consumer<GameTestHelper> test) {
        PENDING.add(new Pending(name, FUNCTIONS.register(name, () -> withLoadedArea(test)), maxTicks, "default"));
    }

    /**
     * Tests land at arbitrary coordinates and often straddle a chunk border; the neighbouring chunk may not be
     * entity-ticking, which hides its entities from area searches and freezes them in place. Force-load the chunks around
     * every test and start it once they all tick (the batch's end releases them: GameTestRunner unforces every forced
     * chunk).
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
            // loaded is not entity-ticking: that also needs the ring two chunks round each one loaded, which the chunk
            // workers generate in the background, and the promotion then runs on the main-thread queue
            freshBatch(level);
            h.assertTrue(settle(level, () -> areaTicking(level, cx, cz), "the test area entity-ticks"), "the test area entity-ticks within 30 s");
            // Note: don't defer the body with runAfterDelay. Vanilla iterates its scheduled callbacks while running them,
            // so scheduling from inside one can run entries twice. Tests that need settled chunks delay their own actions.
            test.accept(h);
        };
    }

    /**
     * Room round every test, in blocks (its TestData padding). The grid spawner spaces tests by their padded bounds, so
     * neighbours stand 54 blocks apart instead of 6, and vanilla clears the padded box (blocks and creatures, from 19
     * below the test's origin to 24 above it) as it places the test. The world is superflat with its grass at y -61 and
     * tests stand at y 5: whatever rock, water or roof a test finds there was left by another test.
     */
    static final int PADDING = 24;

    /** The server tick the current batch started on (its tests all take their first tick on it). */
    private static int batchTick = Integer.MIN_VALUE;
    /** The game rules and the time of day the first batch found (every batch starts from them). */
    private static GameRules startRules;
    private static long startClock;

    /**
     * Every batch starts in a world nobody else is using (the GameTest server only). The runner never removes what a test
     * leaves behind: by the end of a run some 240 players from finished tests still stood about the grid, the Living Wilds
     * started stampedes, hunts and pack spawns round them, they were the "nearest player" of other tests' spawns and caps,
     * and a gate whose reused cell one of them stood in could never be released. Rain a test set never stopped (the test
     * server doesn't advance the weather) and the clock ran on, so a test met whatever sky and hour the tests before it
     * left. A batch starts only after every earlier one has finished, and its tests all take their first tick on the same
     * server tick, so the first of them removes every player and every creature, item and other entity left in the
     * overworld, and puts back the first batch's game rules, time of day and clear sky, before any test body runs.
     */
    private static void freshBatch(ServerLevel level) {
        MinecraftServer server = level.getServer();
        if (!(server instanceof GameTestServer) || server.getTickCount() == batchTick) return;
        batchTick = server.getTickCount();
        for (ServerPlayer p : List.copyOf(server.getPlayerList().getPlayers())) server.getPlayerList().remove(p);
        ServerLevel over = server.overworld();
        List<Entity> left = new ArrayList<>();
        for (Entity e : over.getAllEntities()) if (!(e instanceof Player)) left.add(e);
        left.forEach(Entity::discard);
        var cmd = server.createCommandSourceStack().withLevel(over).withSuppressedOutput();
        if (startRules == null) {
            startRules = over.getGameRules().copy(over.enabledFeatures());
            startClock = Math.floorMod(over.getOverworldClockTime(), 24000L);
        } else {
            over.getGameRules().setAll(startRules, server);
            server.getCommands().performPrefixedCommand(cmd, "time set " + startClock);
        }
        server.getCommands().performPrefixedCommand(cmd, "weather clear");
    }

    /**
     * Air from the lower corner of a box (absolute corners, any order) up to the sky in every column of it: leftovers of
     * any height, and the water in them, so heightmaps, sky checks and falling bodies see only what the test builds. It
     * reads each column's top and changes only blocks that aren't air, without neighbour updates (nothing outside starts
     * to flow or fall in).
     */
    public static void clearToSky(ServerLevel level, BlockPos a, BlockPos b) {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int y0 = Math.min(a.getY(), b.getY()), y1 = Math.max(a.getY(), b.getY());
        for (int x = Math.min(a.getX(), b.getX()); x <= Math.max(a.getX(), b.getX()); x++) {
            for (int z = Math.min(a.getZ(), b.getZ()); z <= Math.max(a.getZ(), b.getZ()); z++) {
                int top = Math.max(y1, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1);
                for (int y = y0; y <= top; y++) {
                    if (!level.getBlockState(m.set(x, y, z)).isAir()) level.setBlock(m, air, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
    }

    /** Removes every creature, item and other non-player entity in a box (absolute corners, any order). */
    public static void clearEntities(ServerLevel level, BlockPos a, BlockPos b) {
        for (Entity e : level.getEntitiesOfClass(Entity.class, AABB.encapsulatingFullBlocks(a, b), e -> !(e instanceof Player))) e.discard();
    }

    /**
     * Runs {@code r} when the test ends, passed or failed (a failed assertion skips the rest of the test, so a switch it
     * turned on stays on for every test after it unless it's put back here).
     */
    public static void onEnd(GameTestHelper h, Runnable r) {
        h.testInfo.addListener(new GameTestListener() {
            @Override
            public void testStructureLoaded(GameTestInfo info) {}

            @Override
            public void testPassed(GameTestInfo info, GameTestRunner runner) {
                r.run();
            }

            @Override
            public void testFailed(GameTestInfo info, GameTestRunner runner) {
                r.run();
            }

            @Override
            public void testAddedForRerun(GameTestInfo original, GameTestInfo copy, GameTestRunner runner) {}
        });
    }

    /**
     * A site of the test's own wherever it lands: a floor of {@code floor} on the layer of {@code lo} (relative corners;
     * {@code lo} is the low one), air from the layer above it up to the sky, and no creatures or items left over it.
     * Returns the floor's absolute low corner.
     */
    public static BlockPos site(GameTestHelper h, BlockPos lo, BlockPos hi, BlockState floor) {
        ServerLevel level = h.getLevel();
        BlockPos a = h.absolutePos(lo), b = h.absolutePos(hi);
        clearToSky(level, a.above(), b);
        clearEntities(level, a, b.atY(Math.max(b.getY(), a.getY() + 1)));
        for (BlockPos q : BlockPos.betweenClosed(a, b.atY(a.getY()))) level.setBlock(q, floor, Block.UPDATE_CLIENTS);
        return a;
    }

    /**
     * Clears the air over a test spot, so an earlier test's floor left high above it doesn't roof it (a surface creature
     * sees no sky, a lair lays its ring on top of it). From {@code fromDy} to {@code toDy} above {@code centre},
     * {@code radius} blocks round it. Call before the test builds anything up there.
     */
    public static void clearAbove(ServerLevel level, BlockPos centre, int radius, int fromDy, int toDy) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) for (int dy = fromDy; dy <= toDy; dy++) {
            m.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
            if (!level.getBlockState(m).isAir()) level.setBlock(m, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
        }
    }

    /** Real time a test waits for the server's background work (chunk promotion, the light engine) before it fails. */
    private static final long SETTLE_DEADLINE_NS = 30_000_000_000L;

    /**
     * Ticks are not time on a GameTest server: it never waits between ticks, so on a busy machine or a CI runner whole
     * tick budgets pass before the chunk workers and the light engine, which run on other threads and are scheduled from
     * the main-thread queue, have caught up ("the platform ticks" timed out at 2400 ticks, a 30-tick light wait read a
     * room that was still lit). Instead of counting ticks, wait for the state itself: run that queue here, as
     * {@code getChunk} does while it waits, until {@code done} holds or 30 s of real time pass. True if it holds; a wait
     * of a second or more is logged with {@code what}, so a slow machine's logs show how long the state really took.
     */
    private static boolean settle(ServerLevel level, BooleanSupplier done, String what) {
        long start = System.nanoTime();
        while (!done.getAsBoolean()) {
            if (System.nanoTime() - start > SETTLE_DEADLINE_NS) return false;
            if (!level.getChunkSource().pollTask()) LockSupport.parkNanos(1_000_000L);
        }
        long ms = (System.nanoTime() - start) / 1_000_000L;
        if (ms >= 1000) Awaken.LOG.info("[tests] {}: waited {} ms", what, ms);
        return true;
    }

    private static boolean areaTicking(ServerLevel level, int cx, int cz) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            if (!level.isPositionEntityTicking(m.set((cx + dx) << 4, 64, (cz + dz) << 4))) return false;
        }
        return true;
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

    /**
     * Fails the test unless the light engine works through every block change made so far in the chunks under the box
     * (absolute corners, any order) within 30 s of real time. It lights on its own thread, well behind the block changes,
     * so read light levels after this, never after a fixed number of ticks: its tasks run in order per chunk, and a
     * chunk's "pending tasks" marker completes after the light update that took the changes queued before it.
     */
    public static void awaitLight(GameTestHelper h, BlockPos a, BlockPos b) {
        ServerLevel level = h.getLevel();
        var engine = level.getChunkSource().getLightEngine();
        List<CompletableFuture<?>> all = new ArrayList<>();
        for (int cx = Math.min(a.getX(), b.getX()) >> 4; cx <= Math.max(a.getX(), b.getX()) >> 4; cx++) {
            for (int cz = Math.min(a.getZ(), b.getZ()) >> 4; cz <= Math.max(a.getZ(), b.getZ()) >> 4; cz++) all.add(engine.waitForPendingTasks(cx, cz));
        }
        CompletableFuture<Void> caughtUp = CompletableFuture.allOf(all.toArray(CompletableFuture[]::new));
        String what = "the light engine catches up with the blocks between " + a.toShortString() + " and " + b.toShortString();
        h.assertTrue(settle(level, caughtUp::isDone, what), what + " within 30 s");
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
        Map<String, Holder<TestEnvironmentDefinition<?>>> envs = new HashMap<>();
        envs.put("default", event.registerEnvironment(Awaken.id("default")));
        int registered = 0;
        for (Pending p : PENDING) {
            if (!selected(p.name())) continue;
            Holder<TestEnvironmentDefinition<?>> e = envs.computeIfAbsent(p.env(), n -> event.registerEnvironment(Awaken.id(n)));
            event.registerTest(Awaken.id(p.name()), new FunctionGameTestInstance(p.fn().getKey(), new TestData<>(e, Level.OVERWORLD,
                    Identifier.withDefaultNamespace("empty"), p.maxTicks(), 0, true, Rotation.NONE, false, 1, 1, false, PADDING)));
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
