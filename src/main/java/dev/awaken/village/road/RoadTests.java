package dev.awaken.village.road;

import dev.awaken.Awaken;
import dev.awaken.city.CityPlan;
import dev.awaken.city.SettlementPaint;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.village.TestLand;
import dev.awaken.village.TestMountain;
import dev.awaken.village.plan.Terrain;
import dev.awaken.village.plan.VillagePlan;
import dev.awaken.village.plan.VillagePlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;

/**
 * Roads and towns' GameTests (prefix {@code village_}, SPEC §28.9), one per mechanism: a road laid over the land and
 * painted ({@code village_roads}); a town, a wayhouse and a mining town planned ({@code village_town_plan}). The Deep
 * Mine's is with the troubles' ({@code village_deep_mine}).
 */
public final class RoadTests {
    private RoadTests() {}

    public static void register() {
        AwakenTests.registerIsolated("village_roads", 600, RoadTests::roads);
        AwakenTests.register("village_town_plan", 400, RoadTests::towns);
    }

    /**
     * A road across the test valley and its river: found, following the land (never more than two a point, cuttings and
     * banks small), bridged where it crosses water, the same twice; cut into legs whose structure starts reach all of them;
     * painted into the world (the carriageway's paving, a slab's half step, the waymarker leaving the first place with the
     * next place's name on its arm).
     */
    private static void roads(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        TestLand land = TestLand.at(31L, o.getX(), o.getY(), o.getZ());
        RoadPlanner.Keep none = (x, z) -> false;
        int ax = o.getX() + 4, az = o.getZ() - 60, bx = o.getX() + 30, bz = o.getZ() + 170;
        RoadPlanner.Request q = new RoadPlanner.Request(ax, az, land.top(ax, az), 0, 1, bx, bz, land.top(bx, bz), 0, -1, none);
        long t0 = System.nanoTime();
        RoadPlanner.Path a = RoadPlanner.plan(q, land), b = RoadPlanner.plan(q, land);
        long ns = System.nanoTime() - t0;
        h.assertTrue(a != null, "a road crosses the valley: " + RoadPlanner.WHY.get());
        h.assertTrue(java.util.Arrays.equals(a.x(), b.x()) && java.util.Arrays.equals(a.y(), b.y()) && java.util.Arrays.equals(a.flags(), b.flags()),
                "the same ends over the same land make the same road");
        int bridged = 0, worstStep = 0, worstCut = 0;
        for (int i = 0; i < a.points(); i++) {
            if (i > 0) worstStep = Math.max(worstStep, Math.abs(a.y()[i] - a.y()[i - 1]));
            int x = Math.round(a.x()[i]), z = Math.round(a.z()[i]);
            if ((a.flags()[i] & RoadPlanner.BRIDGE) != 0) {
                bridged++;
                continue;
            }
            if (land.water(x, z) == 0 && (a.flags()[i] & (RoadPlanner.CAUSEWAY | RoadPlanner.BOARDWALK)) == 0)
                worstCut = Math.max(worstCut, Math.abs(a.y()[i] - land.top(x, z)));
        }
        h.assertTrue(bridged >= 2, "it bridges the river: " + bridged + " points");
        h.assertTrue(worstStep <= 2, "never more than two blocks a point: " + worstStep);
        h.assertTrue(worstCut <= 7, "cuttings and banks stay small: " + worstCut);
        // the road with its ends and waymarkers, cut into legs
        RoadNet.Entry from = new RoadNet.Entry(ax, az, a.y()[0], 0, 1, "Testby", "test:a", Road.ENTRY, VillagePlan.Kind.VILLAGE.ordinal());
        RoadNet.Entry to = new RoadNet.Entry(bx, bz, a.y()[a.points() - 1], 0, -1, "Otherwick", "test:b", Road.ENTRY, VillagePlan.Kind.TOWN.ordinal());
        Road r = Highroads.lay(null, "test:road", Road.HIGHROAD, from, to, none, land);
        h.assertTrue(r != null && !r.markers().isEmpty(), "laid, with its waymarkers");
        List<Road.Leg> legs = r.legs();
        int covered = 0;
        for (Road.Leg leg : legs) {
            int cx = (leg.startX() << 4) + 8, cz = (leg.startZ() << 4) + 8;
            for (int i = leg.i0(); i <= leg.i1(); i++)
                h.assertTrue(Math.abs(r.x()[i] - cx) <= 108 && Math.abs(r.z()[i] - cz) <= 108, "leg " + leg.index() + "'s point " + i + " within its start's reach");
            h.assertTrue(Math.floorMod(leg.startX(), Road.LATTICE) == 0 && Math.floorMod(leg.startZ(), Road.LATTICE) == 0, "legs start on the lattice");
            covered += leg.i1() - leg.i0();
        }
        h.assertTrue(covered == r.points() - 1, "the legs cover the road: " + covered + " of " + (r.points() - 1));
        // paint the first leg into the world: the paving, the waymarker and its words
        RoadPiece piece = new RoadPiece(legs.getFirst());
        RoadPaint p = piece.paint();
        long p0 = System.nanoTime();
        int chunks = 0;
        for (int cx = (p.ox + p.rx0) >> 4; cx <= (p.ox + p.rx0 + p.rw) >> 4; cx++)
            for (int cz = (p.oz + p.rz0) >> 4; cz <= (p.oz + p.rz0 + p.rd) >> 4; cz++) {
                level.getChunk(cx, cz);
                SettlementPaint.repaint(p, level, cx, cz);
                for (Road.Marker m : r.markers()) if (m.point() <= legs.getFirst().i1()) RoadMarks.words(level.getChunk(cx, cz), level.registryAccess(), p, m);
                chunks++;
            }
        long paintNs = (System.nanoTime() - p0) / Math.max(1, chunks);
        BlockPos road = BlockPos.containing(r.x()[2], r.y()[2], r.z()[2]);
        BlockState pave = level.getBlockState(road);
        h.assertTrue(!pave.isAir() && level.getBlockState(road.above()).isAir() || level.getBlockState(road.above()).getBlock() instanceof net.minecraft.world.level.block.SlabBlock,
                "the carriageway is paved and clear over: " + pave);
        Road.Marker leaving = r.markers().stream().filter(m -> m.point() <= legs.getFirst().i1()).findFirst().orElse(null);
        h.assertTrue(leaving != null, "a waymarker where it leaves Testby");
        BlockPos post = new BlockPos(leaving.x(), leaving.y() + 3, leaving.z());
        h.assertTrue(level.getBlockState(post).is(net.minecraft.tags.BlockTags.LOGS) && level.getBlockState(post.above()).is(net.minecraft.world.level.block.Blocks.LANTERN),
                "its post and its lantern: " + level.getBlockState(post) + ", " + level.getBlockState(post.above()));
        boolean named = false;
        for (Road.Arm arm : leaving.arms()) {
            BlockPos sign = post.offset(RoadMarks.SX[arm.side()], 0, RoadMarks.SZ[arm.side()]);
            if (level.getBlockEntity(sign) instanceof SignBlockEntity s)
                named |= s.getText(net.minecraft.world.level.block.entity.SignTextSlot.FRONT).getMessages(false).stream().anyMatch(c -> c.getString().contains("Other") || c.getString().contains("wick"));
        }
        h.assertTrue(named, "an arm names the next place");
        // in worldgen a sign has no level yet: its words go in without one
        var loose = new net.minecraft.world.level.block.entity.HangingSignBlockEntity(BlockPos.ZERO,
                net.minecraft.world.level.block.Blocks.OAK_WALL_HANGING_SIGN.defaultBlockState());
        RoadMarks.write(loose, level.registryAccess(), RoadMarks.text(leaving.arms().getFirst(), net.minecraft.world.item.DyeColor.BLACK));
        h.assertTrue(loose.isWaxed() && loose.getText(net.minecraft.world.level.block.entity.SignTextSlot.FRONT).hasMessage(false)
                        && loose.getText(net.minecraft.world.level.block.entity.SignTextSlot.BACK).hasMessage(false),
                "a sign with no level takes its words, both faces, waxed");
        Awaken.LOG.info(String.format(Locale.ROOT, "[road] test road: %d points, %d bridged, worst step %d, worst cut %d, laid in %.1f ms (twice), %d legs, "
                + "painted %.2f ms a chunk", a.points(), bridged, worstStep, worstCut, ns / 1e6, legs.size(), paintNs / 1e6));
        h.succeed();
    }

    /**
     * A walled town on the test valley (25-45 homes, 60-120 people, its wall with towers and two gates or more, the market
     * square's stalls, the inn, the hall, the scribe, the smithy and the bell tower; every house inside the wall, fields out
     * past it; the same plan and the same blocks twice); a wayhouse (the inn, its stable and paddock of horses, four ways
     * out); and a mining town under the test mountain (its head at the foot, its two levels, its winze, its face, the
     * cavern behind it, all inside its square).
     */
    private static void towns(GameTestHelper h) {
        TestLand land = new TestLand(7);
        long t0 = System.nanoTime();
        VillagePlan town = VillagePlanner.plan(req("test:town", 4711L, VillagePlan.Kind.TOWN), land);
        long ns = System.nanoTime() - t0;
        h.assertTrue(town != null && town.kind == VillagePlan.Kind.TOWN, "a town fits the test valley: " + VillagePlanner.WHY.get());
        VillagePlan again = VillagePlanner.plan(req("test:town", 4711L, VillagePlan.Kind.TOWN), land);
        h.assertTrue(again != null && text(town).equals(text(again)), "the same request over the same land makes the same town");
        long homes = town.buildings.stream().filter(b -> b.use().equals("house")).count();
        h.assertTrue(homes >= 25 && homes <= 45, "25-45 homes: " + homes);
        h.assertTrue(town.people.size() >= 60 && town.people.size() <= 120, "60-120 people: " + town.people.size());
        h.assertTrue(town.gates.size() >= 2 && town.towers.size() >= 6, "walls with gates and towers: " + town.gates.size() + " gates, " + town.towers.size() + " towers");
        for (String use : new String[]{"inn", "hall", "scribe", "smithy"})
            h.assertTrue(town.buildings.stream().anyMatch(b -> b.use().equals(use)), "the town has its " + use);
        long stalls = town.props.stream().filter(pr -> pr.kind().equals("stall")).count();
        h.assertTrue(stalls >= 3 && town.bell != null && town.bell.x1() - town.bell.x0() == 4, "a market of stalls and the bell tower: " + stalls + " stalls");
        for (CityPlan.Plot q : town.plots) {
            double mx = (q.x0() + q.x1()) / 2.0, mz = (q.z0() + q.z1()) / 2.0;
            double deg = (Math.toDegrees(Math.atan2(mz, mx)) + 360) % 360;
            if (q.use().equals("barn") || q.use().equals("granary") || q.use().equals("mill")) continue;
            h.assertTrue(Math.hypot(mx, mz) < town.wallRing[(int) deg % 360] - 3, "every house inside the wall: plot " + q.id());
        }
        for (VillagePlan.Field f : town.fields) {
            double mx = (f.x0() + f.x1()) / 2.0, mz = (f.z0() + f.z1()) / 2.0;
            double deg = (Math.toDegrees(Math.atan2(mz, mx)) + 360) % 360;
            h.assertTrue(Math.hypot(mx, mz) > town.wallRing[(int) deg % 360], "fields lie outside the wall");
        }
        int maxLights = 0;
        long p0 = System.nanoTime();
        long[] chunks = town.chunks();
        for (long c : chunks) {
            int cx = ChunkPos.getX(c), cz = ChunkPos.getZ(c);
            h.assertTrue(SettlementPaint.fingerprint(town, cx, cz, 42L) == SettlementPaint.fingerprint(again, cx, cz, 42L), "chunk " + cx + "," + cz + " paints the same twice");
            maxLights = Math.max(maxLights, SettlementPaint.census(town, cx, cz, 42L)[1]);
        }
        long paintNs = (System.nanoTime() - p0) / Math.max(1, chunks.length * 3L);
        h.assertTrue(maxLights <= 10, "a handful of lights a chunk at most: " + maxLights);
        // the wayhouse
        VillagePlan way = VillagePlanner.plan(req("test:way", 911L, VillagePlan.Kind.WAYHOUSE), land);
        h.assertTrue(way != null && way.buildings.stream().anyMatch(b -> b.use().equals("inn")) && way.buildings.stream().anyMatch(b -> b.use().equals("barn")),
                "a wayhouse: the inn and its stable: " + VillagePlanner.WHY.get());
        h.assertTrue(way.pens.stream().anyMatch(pn -> pn.beast().equals("horse")), "its paddock of horses: " + way.pens);
        h.assertTrue(way.exitList.size() >= 3, "three or four ways out of its crossroads: " + way.exitList.size());
        // the mining town under the mountain
        TestMountain mtn = TestMountain.at(8181L, 0, 72, 0);
        VillagePlan mine = mtn.plan("test:mine", 8182L);
        h.assertTrue(mine != null && mine.kind == VillagePlan.Kind.MINING && mine.mine != null, "a mining town at the mountain's foot: " + VillagePlanner.WHY.get());
        VillagePlan.Mine m = mine.mine;
        h.assertTrue(mine.buildings.stream().anyMatch(b -> b.use().equals("assay")), "its assayer's store");
        h.assertTrue(m.level1() < m.lift() - 20 && m.level2() < m.level1() - 6, "two levels under the head: " + m);
        int[][] reach = {m.along(m.winze()), m.along2(m.reach2()), {m.cavernX(), m.cavernZ()}};
        for (int[] c : reach) h.assertTrue(Math.abs(c[0]) <= mine.r && Math.abs(c[1]) <= mine.r, "the workings stay inside the town's square: " + c[0] + "," + c[1]);
        h.assertTrue(mine.people.stream().filter(pp -> pp.job().equals("miner")).count() >= 3, "miners to work it");
        Awaken.LOG.info(String.format(Locale.ROOT, "[road] test towns: %s planned in %.1f ms, %d chunks at %.2f ms a chunk, at most %d lights | %s | %s",
                town, ns / 1e6, chunks.length, paintNs / 1e6, maxLights, way, mine));
        h.succeed();
    }

    static VillagePlanner.Request req(String key, long seed, VillagePlan.Kind kind) {
        return new VillagePlanner.Request(key, seed, 0, 0, kind, false, -124, -124, 139, 139, (x, z) -> false);
    }

    static String text(VillagePlan p) {
        StringBuilder sb = new StringBuilder(p.toString());
        for (CityPlan.Plot q : p.plots) sb.append('|').append(q);
        for (VillagePlan.Street s : p.streets) sb.append('|').append(s.rank()).append(java.util.Arrays.toString(s.x())).append(java.util.Arrays.toString(s.lift()));
        for (int[] t : p.towers) sb.append('|').append(java.util.Arrays.toString(t));
        for (VillagePlan.Person q : p.people) sb.append('|').append(q);
        return sb.toString();
    }

    /** Unused land reader for tests that need none. */
    static final Terrain FLAT = new TestLand(1);
}
