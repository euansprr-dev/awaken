package dev.awaken.delve.home;

import dev.awaken.Awaken;
import dev.awaken.body.ascent.AscentContent;
import dev.awaken.body.ascent.Updrafts;
import dev.awaken.core.api.NowHooks;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.descent.Strata;
import dev.awaken.descent.world.Layout;
import dev.awaken.descent.world.Terrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The way home's GameTest (SPEC §28.5), one for the mechanism: the line's rule and text (under the sky, none; out of
 * the sky 15 s and 40 walked under, "Surface ↑ 20 · NE 40"; a loop walked back is cut; the sky again, none), on the Now
 * line for a player in the overworld and never in a gate or the Descent; a worldgen vent found and dressed, lifting
 * above it; and the Descent's tunnels dressed (grottos with their pools, seams in the walls).
 */
final class HomeTests {
    private HomeTests() {}

    static void register() {
        AwakenTests.registerIsolated("delve_way_home", 400, HomeTests::wayHome);
    }

    private static void wayHome(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos base = h.absolutePos(BlockPos.ZERO);
        int ax = base.getX(), ay = base.getY() + 20, az = base.getZ();
        // under the sky: the anchor, no line
        HomeTrail t = new HomeTrail();
        long now = 1000;
        for (int i = 0; i < 4; i++) t.step(ax, ay, az, true, false, now += 5);
        h.assertTrue(t.size() == 1 && !t.shown(), "under the sky: an anchor and no line");
        // walk 40 under, south-west and down 20 (a block every sample), so the way home is up and to the NE
        int x = ax, y = ay, z = az;
        for (int i = 1; i <= 40; i++) {
            x = ax - (int) Math.round(i * 0.7);
            z = az + (int) Math.round(i * 0.7);
            y = ay - i / 2;
            t.step(x, y, z, false, true, now += 5);
        }
        h.assertFalse(t.shown(), "not yet: out of the sky only 10 s");
        for (int i = 0; i < 30; i++) t.step(x, y, z, false, true, now += 5);
        h.assertTrue(t.shown(), "out of the sky 15 s, 20 down and 40 walked: the line shows");
        String text = t.text(x, y, z);
        h.assertTrue(text.equals("Surface ↑ 20 · NE 40"), "the line reads " + text);
        // a dead end walked in and back: the loop is cut, the way home is still the way in
        int crumbs = t.size();
        for (int i = 1; i <= 12; i++) t.step(x - i, y, z, false, true, now += 5);
        h.assertTrue(t.size() > crumbs, "walking on lays crumbs");
        for (int i = 11; i >= 0; i--) t.step(x - i, y, z, false, true, now += 5);
        long[] path = t.path();
        boolean deadEnd = false;
        for (long c : path) deadEnd |= BlockPos.getX(c) < x - 5;
        h.assertTrue(!deadEnd && t.size() <= crumbs + 2 && t.shown(), "the dead end isn't on the way home: " + t.size() + " crumbs, " + crumbs + " before");
        h.assertTrue(path.length >= 2 && BlockPos.getY(path[path.length - 1]) == ay && t.pathReachesSky(), "the thread's path runs back to the sky");

        // the Now line for a player standing there, in the overworld
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        try {
            p.setPos(x + 0.5, y, z + 0.5);
            HomeTrail pt = p.getData(HomeModule.TRAIL.get());
            pt.stage(trailOf(t), now);
            h.assertTrue(level.dimension() == Level.OVERWORLD && HomeServer.eligible(p), "the overworld has a way home");
            List<NowHooks.Suggestion> got = NowHooks.collect(p, 20);
            NowHooks.Suggestion line = got.stream().filter(s -> s.id().equals(HomeTrail.NOW_ID)).findFirst().orElse(null);
            h.assertTrue(line != null && line.text().getString().equals("Surface ↑ 20 · NE 40") && line.priority() == HomeServer.PRIORITY
                    && line.where().isPresent() && line.where().get().getY() == ay, "on the Now line: " + got);
            // never in a gate or the Descent: their dimensions have no way home, and a sample there ends the trail
            ResourceKey<Level> gates = ResourceKey.create(Registries.DIMENSION, Awaken.id("gates"));
            h.assertFalse(HomeServer.eligibleDim(gates) || HomeServer.eligibleDim(Strata.dimensionOf(3)) || HomeServer.eligibleDim(Strata.ENDLESS),
                    "no way home in a gate or the Descent");
            // under the sky again: none
            pt.step(p.getBlockX(), p.getBlockY(), p.getBlockZ(), true, false, now + 5);
            h.assertTrue(HomeServer.suggest(p).isEmpty(), "under the sky again: no line");
        } finally {
            level.getServer().getPlayerList().remove(p);
        }

        // the Descent's tunnels: every stratum with tunnels has grottos, each with its pool, and seams in its walls
        for (int form : new int[]{1, 2, 3, 4, 8, 10}) {
            Terrain tr = new Terrain(form, 777L + form);
            h.assertTrue(tr.grottos().size() >= 3, "form " + form + ": " + tr.grottos().size() + " grottos");
            Terrain.Grotto g = tr.grottos().getFirst();
            BlockState[] col = tr.singleColumn(g.x(), g.z());
            // open over the pool (the Gardens' cave vines may hang down to it)
            boolean open = col[g.floorY()].getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
            h.assertTrue(col[g.floorY() - 1].is(Blocks.WATER) && open, "form " + form + ": a grotto's pool, open over it: "
                    + col[g.floorY() - 1] + " under " + col[g.floorY()]);
        }
        Terrain choir = new Terrain(3, 4242L);
        int seams = 0;
        for (int sx = 60; sx < 140 && seams == 0; sx += 3) {
            for (int sz = -40; sz < 40 && seams == 0; sz += 3) {
                BlockState[] col = choir.singleColumn(sx, sz);
                for (int sy = Layout.floorY(3) + 2; sy < 220; sy++) if (col[sy].getBlock() == dev.awaken.delve.DelveContent.DEEPGLASS.get()) seams++;
            }
        }
        h.assertTrue(seams > 0, "the Choir's tunnels hold seams of Deepglass");

        // a vent: a floor with 24 open over it and lava beside it is a site; dressed, its column lifts over it
        int vy = level.getMaxY() - 60;
        BlockPos floor = new BlockPos(base.getX(), vy, base.getZ());
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            level.setBlock(floor.offset(dx, 0, dz), Blocks.STONE.defaultBlockState(), 3);
            for (int dy = 1; dy <= 26; dy++) level.setBlock(floor.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(floor.offset(4, 0, 0), Blocks.LAVA.defaultBlockState(), 3);
        VentFeature.Site site = VentFeature.site(level, floor.getX(), floor.getZ(), vy - 4, vy + 2, Integer.MAX_VALUE);
        h.assertTrue(site != null && site.floor().equals(floor) && site.kind() == VentFeature.Kind.LAVA, "a vent's site: " + site);
        // the feature's cost: a probe is one pass up a column (bottom to y 48 and its air); a chunk probes 3 one time in 8
        double best = Double.MAX_VALUE;
        for (int round = 0; round < 3; round++) {
            long t0 = System.nanoTime();
            for (int i = 0; i < 32; i++) VentFeature.site(level, base.getX() + (i & 7) - 4, base.getZ() + (i >> 3) - 2, level.getMinY() + 5, 48, VentFeature.LAVA_MAX_Y);
            best = Math.min(best, (System.nanoTime() - t0) / 1e6 / 32);
        }
        double perChunk = best * VentFeature.PROBES / VentFeature.CHANCE;
        Awaken.LOG.info("AWAKEN delve: a vent probe {} ms, {} ms a chunk on average", String.format("%.4f", best), String.format("%.4f", perChunk));
        h.assertTrue(perChunk < AwakenTests.cpuBudget(0.5), "the vents cost " + perChunk + " ms a chunk (budget 0.5)");
        VentFeature.dress(level, floor, VentFeature.Kind.LAVA, RandomSource.create(5));
        h.assertTrue(level.getBlockState(floor).is(AscentContent.UPDRAFT_VENT.get()) && level.getBlockState(floor.below()).is(Blocks.MAGMA_BLOCK)
                && level.getBlockState(floor.east()).is(Blocks.MAGMA_BLOCK), "the vent set in a crust of magma");
        double cx = floor.getX() + 0.5, cz = floor.getZ() + 0.5;
        // the vent's block entity lists it as it loads (the next tick): then the air over it rises
        h.succeedWhen(() -> {
            h.assertTrue(Updrafts.liftAt(level, cx, vy + 6, cz) > 0, "the vent lifts above it");
            h.assertTrue(Updrafts.liftAt(level, cx + 8, vy + 6, cz) == 0, "and not beside its column");
            level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);   // a test plot is reused: take the column away
        });
    }

    /** A trail's crumbs, anchor first (to stage the same trail on a player). */
    private static long[] trailOf(HomeTrail t) {
        long[] p = t.path(), out = new long[p.length];
        for (int i = 0; i < p.length; i++) out[i] = p[p.length - 1 - i];
        return out;
    }
}
