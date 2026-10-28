package dev.awaken.village.road;

import dev.awaken.Awaken;
import dev.awaken.core.test.AwakenTests;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.Locale;

/**
 * The server's tick holds while the roads are planned (SPEC §28.9, Cost; Euan's 42 s freeze in a D-rank trial,
 * 2026-10-28). {@code village_tick_holds}: a roads' world over the real noise, a big wave queued (every lattice chunk
 * within 1,100 of a point 2,400 out) and world generation waiting on a cold lattice chunk beside it; for 15 s the
 * server thread does what it does in play each tick (a pass ahead of a player flying 40 blocks a second over new land,
 * the distance to a road a creature's level asks), and none of it waits for planning (no future joined, no lock a
 * planner holds), no call of it does over 5 ms of work (its own CPU time: its wall time also counts the moments the OS
 * gave its core to another thread, which the planner cap governs) and no tick takes over 250 ms (a collection or the
 * runner's shared cores, never a stall: Euan's was 42 s), the planners busy all along and two cores left for the game.
 * {@code village_far_chunk_named}: a
 * chunk the server thread had to wait for far from every player is logged with who asked.
 */
final class RoadTickTests {
    private RoadTickTests() {}

    static final long WINDOW_NS = 15_000_000_000L, CALL_NS = 5_000_000L, TICK_NS = 250_000_000L;

    static void register() {
        AwakenTests.registerIsolated("village_tick_holds", 60000, RoadTickTests::tickHolds);
        AwakenTests.register("village_far_chunk_named", 40, RoadTickTests::farChunkNamed);
        AwakenTests.registerIsolated("village_road_starts_wait", 2_000_000, RoadTickTests::startsWait);
        AwakenTests.register("village_road_starts_applied", 20, RoadTickTests::startsApplied);
    }

    /** A roads' world over vanilla's real noise (seed 26102610) in use, and what puts the server's own back. */
    private record Opened(RoadWorld w, Runnable restore) {}

    private static Opened open(GameTestHelper h) {
        long seed = 26102610L;
        var reg = h.getLevel().registryAccess();
        var ns = reg.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE_SETTINGS).getOrThrow(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD);
        var preset = reg.lookupOrThrow(net.minecraft.core.registries.Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                .getOrThrow(net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        var gen = new net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator(net.minecraft.world.level.biome.MultiNoiseBiomeSource.createFromPreset(preset), ns);
        var rs = net.minecraft.world.level.levelgen.RandomState.create(reg.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE), seed, ns.value());
        var heights = net.minecraft.world.level.LevelHeightAccessor.create(-64, 384);
        RoadWorld had = RoadWorld.get();
        var hadFirst = dev.awaken.village.world.FirstHamlet.found();
        Runnable restore = () -> {
            RoadPlanners.settle();
            RoadNet.clear();
            dev.awaken.village.world.Plans.clear();
            dev.awaken.village.world.FirstHamlet.use(hadFirst);
            RoadWorld.use(had);
        };
        try {
            RoadNet.clear();
            dev.awaken.village.world.Plans.clear();
            var t = dev.awaken.village.world.GenTerrain.of(gen, heights, rs);
            var site = dev.awaken.terrain.city.SitePicker.pick(gen, rs, heights, false);
            dev.awaken.village.world.FirstHamlet.use(dev.awaken.village.world.FirstHamlet.search(seed, new int[]{site.x(), site.z()}, t));
            RoadWorld w = RoadWorld.of(seed, site.x(), site.z(), gen, rs, heights, true);
            RoadWorld.use(w);
            return new Opened(w, restore);
        } catch (RuntimeException e) {
            restore.run();
            throw e;
        }
    }

    private static void tickHolds(GameTestHelper h) {
        Opened o = open(h);
        RoadWorld w = o.w();
        Runnable restore = o.restore();
        int cores = Runtime.getRuntime().availableProcessors();
        h.assertTrue(cores < 3 || RoadPlanners.threads() <= cores - 2, RoadPlanners.threads() + " planners leave two of " + cores + " cores");
        // the wave, queued off the server thread the way world generation fans its work out, and world generation waiting beside it
        int wx = w.cx + 2400, wz = w.cz;
        Thread worldgen = new Thread(() -> {
            RoadNet.ahead(w, wx >> 4, wz >> 4, Integer.MAX_VALUE);
            RoadNet.startsIn(Math.floorDiv((w.cx - 3600) >> 4, Road.LATTICE) * Road.LATTICE, Math.floorDiv(w.cz >> 4, Road.LATTICE) * Road.LATTICE);
        }, "Awaken test worldgen");
        worldgen.setDaemon(true);
        worldgen.start();
        var cpu = java.lang.management.ManagementFactory.getThreadMXBean();
        boolean cpuTimed = cpu.isCurrentThreadCpuTimeSupported();
        long waits0 = RoadTimer.serverWaits(), t0 = System.nanoTime();
        long[] last = {t0}, worstTick = {0}, worstCall = {0}, worstWall = {0}, ticks = {0}, busy = {0};
        String[] worstWhat = {""};
        boolean[] done = {false};
        h.onEachTick(() -> {
            if (done[0]) return;
            long now = System.nanoTime();
            if (ticks[0]++ > 2) worstTick[0] = Math.max(worstTick[0], now - last[0]); // (the first ticks hold the setup above)
            if (RoadPlanners.queued() > 0) busy[0]++;
            // a player flying east over new land at 40 blocks a second: the pass ahead of it, and a creature's road distance
            int px = w.cx - 1200 + (int) ((now - t0) / 25_000_000L), pz = w.cz + 600;
            long w0 = System.nanoTime(), c0 = cpuTimed ? cpu.getCurrentThreadCpuTime() : w0;
            RoadNet.ahead(w, px >> 4, pz >> 4, RoadNet.PER_PASS);
            long c1 = cpuTimed ? cpu.getCurrentThreadCpuTime() : System.nanoTime();
            RoadNet.distanceWithin(px, pz, dev.awaken.village.SettledCalm.ROAD_FAR);
            RoadNet.known(Math.floorDiv(px >> 4, Road.LATTICE) * Road.LATTICE, Math.floorDiv(pz >> 4, Road.LATTICE) * Road.LATTICE);
            long c2 = cpuTimed ? cpu.getCurrentThreadCpuTime() : System.nanoTime();
            worstWall[0] = Math.max(worstWall[0], System.nanoTime() - w0);
            if (c1 - c0 > worstCall[0]) {
                worstCall[0] = c1 - c0;
                worstWhat[0] = "the pass ahead at " + px + "," + pz;
            }
            if (c2 - c1 > worstCall[0]) {
                worstCall[0] = c2 - c1;
                worstWhat[0] = "the road distance at " + px + "," + pz;
            }
            last[0] = System.nanoTime();
            if (now - t0 < WINDOW_NS) return;
            done[0] = true;
            int queued = RoadPlanners.queued();
            long waits = RoadTimer.serverWaits() - waits0;
            String line = String.format(Locale.ROOT, "%d ticks in %.1f s, the longest %.1f ms; the most work in a road call %.2f ms%s (%s), "
                            + "the longest tick's road calls by the clock %.2f ms; %d server-thread waits; the planners (%d) had work in %d ticks, "
                            + "%d jobs queued at the end",
                    ticks[0], (now - t0) / 1e9, worstTick[0] / 1e6, worstCall[0] / 1e6, cpuTimed ? " of CPU" : " by the clock", worstWhat[0],
                    worstWall[0] / 1e6, waits, RoadPlanners.threads(), busy[0], queued);
            Awaken.LOG.info("[road] tick holds: {}", line);
            try {
                worldgen.join(120_000L);
                h.assertTrue(queued > 0 && busy[0] * 10 >= ticks[0] * 9, "the wave kept the planners busy throughout: " + line);
                h.assertTrue(waits == 0, "the server thread never waited for planning: " + line);
                h.assertTrue(worstCall[0] < CALL_NS, "no road call on the server thread does over 5 ms of work: " + line);
                h.assertTrue(worstTick[0] < TICK_NS, "no tick over 250 ms while the wave runs: " + line);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                restore.run();
            }
            h.succeed();
        });
    }

    /**
     * Road starts (Turbo, {@code RoadStarts}): a cold lattice chunk's structure starts, handed off on a stand-in for the
     * level's worldgen executor, come back at once (under 50 ms) as a future; the executor runs other work meanwhile and the server ticks
     * on (no tick over 250 ms); once a planner has made the legs, the step runs on that executor, the legs known (under
     * 5 ms), and the chunk is never set aside again.
     */
    private static void startsWait(GameTestHelper h) {
        Opened o = open(h);
        RoadWorld w = o.w();
        int cx = Math.floorDiv((w.cx + 3600) >> 4, Road.LATTICE) * Road.LATTICE, cz = Math.floorDiv(w.cz >> 4, Road.LATTICE) * Road.LATTICE;
        var worldgen = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Awaken test worldgen");
            t.setDaemon(true);
            return t;
        });
        long[] stepNs = {-1}, handNs = {-1}, probeNs = {-1}, worstTick = {0}, ticks = {0}, last = {System.nanoTime()}, aside0 = {0};
        String[] stepOn = {""};
        boolean[] probeWaiting = {false}, done = {false};
        java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<java.util.List<Road.Leg>>> later = new java.util.concurrent.atomic.AtomicReference<>();
        h.assertTrue(!RoadNet.known(cx, cz), "lattice chunk " + cx + "," + cz + " is cold");
        aside0[0] = dev.awaken.turbo.roadstarts.RoadStarts.setAside();
        worldgen.execute(() -> {
            long s0 = System.nanoTime();
            later.set(dev.awaken.turbo.roadstarts.RoadStarts.later(cx, cz, worldgen, () -> {
                stepOn[0] = Thread.currentThread().getName();
                long t0 = System.nanoTime();
                java.util.List<Road.Leg> legs = RoadNet.startsIn(cx, cz);
                stepNs[0] = System.nanoTime() - t0;
                return java.util.concurrent.CompletableFuture.completedFuture(legs);
            }));
            handNs[0] = System.nanoTime() - s0;
            // world generation's next task, queued behind the hand-off: it runs while the roads are still being planned
            long p0 = System.nanoTime();
            worldgen.execute(() -> {
                probeNs[0] = System.nanoTime() - p0;
                var f = later.get();
                probeWaiting[0] = f != null && !f.isDone();
            });
        });
        long t0 = System.nanoTime();
        h.onEachTick(() -> {
            if (done[0]) return;
            long now = System.nanoTime();
            if (ticks[0]++ > 2) worstTick[0] = Math.max(worstTick[0], now - last[0]);
            last[0] = now;
            var f = later.get();
            boolean timedOut = now - t0 > 240_000_000_000L;
            if (!timedOut && (f == null ? handNs[0] < 0 : !f.isDone() || stepNs[0] < 0)) return;
            done[0] = true;
            try {
                String line = String.format(Locale.ROOT, "handed off in %.2f ms, the next task ran %.1f ms later%s; %d ticks while the roads were planned "
                                + "(%.1f s), the longest %.1f ms; the step ran on %s in %.2f ms", handNs[0] / 1e6, probeNs[0] / 1e6,
                        probeWaiting[0] ? " (still waiting)" : "", ticks[0], (now - t0) / 1e9, worstTick[0] / 1e6, stepOn[0], stepNs[0] / 1e6);
                Awaken.LOG.info("[road] road starts: {}; {}", line, dev.awaken.turbo.roadstarts.RoadStarts.status());
                h.assertTrue(!timedOut, "the legs were made within 240 s: " + line);
                h.assertTrue(f != null, "the cold lattice chunk was set aside");
                // at once (the first hand-off on a server also loads its classes), where waiting would take the seconds planning does
                h.assertTrue(handNs[0] < 50_000_000L, "the hand-off returned at once: " + line);
                h.assertTrue(probeWaiting[0] && probeNs[0] >= 0 && probeNs[0] < 100_000_000L, "world generation ran on while the chunk waited: " + line);
                h.assertTrue(ticks[0] > 20 && worstTick[0] < TICK_NS, "the server ticked on, no tick over 250 ms: " + line);
                h.assertTrue("Awaken test worldgen".equals(stepOn[0]) && stepNs[0] < CALL_NS, "the step ran on world generation's executor, the legs known: " + line);
                h.assertTrue(f.join().equals(RoadNet.startsIn(cx, cz)), "the step's legs are the chunk's legs");
                h.assertTrue(dev.awaken.turbo.roadstarts.RoadStarts.later(cx, cz, worldgen, () -> null) == null, "a chunk whose legs are known isn't set aside");
                h.assertTrue(dev.awaken.turbo.roadstarts.RoadStarts.setAside() == aside0[0] + 1, "one chunk set aside: " + dev.awaken.turbo.roadstarts.RoadStarts.status());
            } finally {
                worldgen.shutdownNow();
                o.restore().run();
            }
            h.succeed();
        });
    }

    /** Road starts' probe: the hook ran on this server's chunk generation (its mixin applied), and it's on. */
    private static void startsApplied(GameTestHelper h) {
        h.assertTrue(dev.awaken.turbo.roadstarts.RoadStarts.applied(), "the structure starts hook ran (the mixin applied): " + dev.awaken.turbo.roadstarts.RoadStarts.status());
        h.assertTrue(dev.awaken.turbo.roadstarts.RoadStarts.active(), "road starts are on: " + dev.awaken.turbo.roadstarts.RoadStarts.status());
        h.succeed();
    }

    /** A chunk the server thread waits for far from every player: counted, and logged with who asked. */
    private static void farChunkNamed(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ChunkPos o = ChunkPos.containing(h.absolutePos(net.minecraft.core.BlockPos.ZERO));
        int x = o.x() + 400 + (int) (level.getGameTime() % 64), z = o.z() - 400;
        h.assertTrue(level.getChunkSource().getChunkNow(x, z) == null, "chunk " + x + "," + z + " isn't loaded yet");
        int before = RoadTimer.farLoads;
        level.getChunk(x, z);
        h.assertTrue(RoadTimer.farLoads > before && RoadTimer.lastFar == ChunkPos.pack(x, z), "the far chunk " + x + "," + z + " is named");
        h.succeed();
    }
}
