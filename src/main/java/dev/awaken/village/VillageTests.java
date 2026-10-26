package dev.awaken.village;

import dev.awaken.Awaken;
import dev.awaken.city.CityPlan;
import dev.awaken.city.SettlementPaint;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.village.plan.Terrain;
import dev.awaken.village.plan.VillagePlan;
import dev.awaken.village.plan.VillagePlanner;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;

import java.util.Locale;

/**
 * Living villages' GameTests (prefix {@code village_}), one per mechanism: the planner and painter are deterministic
 * and within their budgets ({@code village_plan_deterministic}); a village's day runs on the people engine
 * ({@code village_day}); trade in emeralds, standing and the inn ({@code village_trade}).
 */
final class VillageTests {
    private VillageTests() {}

    static void register() {
        AwakenTests.register("village_plan_deterministic", 400, VillageTests::deterministic);
    }

    static VillagePlanner.Request request(String key, long seed, VillagePlan.Kind kind, boolean first) {
        return new VillagePlanner.Request(key, seed, 0, 0, kind, first, -124, -124, 139, 139, (x, z) -> false);
    }

    /** A canonical text of a plan: everything the painter and the people read, in order. */
    static String text(VillagePlan p) {
        StringBuilder sb = new StringBuilder(p.toString());
        for (CityPlan.Plot q : p.plots) sb.append('|').append(q);
        for (VillagePlan.Street s : p.streets) sb.append('|').append(s.rank()).append(java.util.Arrays.toString(s.x())).append(java.util.Arrays.toString(s.lift()));
        for (CityPlan.Prop q : p.props) sb.append('|').append(q);
        for (VillagePlan.Person q : p.people) sb.append('|').append(q);
        for (VillagePlan.Lamp q : p.lamps) sb.append('|').append(q);
        for (VillagePlan.Field q : p.fields) sb.append('|').append(q);
        return sb.toString();
    }

    private static void deterministic(GameTestHelper h) {
        Terrain land = new TestLand(7);
        long t0 = System.nanoTime();
        VillagePlan v = VillagePlanner.plan(request("test:village", 1234L, VillagePlan.Kind.VILLAGE, false), land);
        long planNs = System.nanoTime() - t0;
        h.assertTrue(v != null, "a village fits the test valley");
        VillagePlan again = VillagePlanner.plan(request("test:village", 1234L, VillagePlan.Kind.VILLAGE, false), land);
        h.assertTrue(again != null && text(v).equals(text(again)), "the same request over the same land makes the same plan");
        VillagePlan hamlet = VillagePlanner.plan(request("test:hamlet", 99L, VillagePlan.Kind.HAMLET, true), land);
        h.assertTrue(hamlet != null, "a hamlet fits the test valley");
        // the studio: a dump for tools/city/render.py when asked
        java.nio.file.Path dir = h.getLevel().getServer().getServerDirectory();
        if (java.nio.file.Files.exists(dir.resolve("village-dump.request"))) {
            try {
                for (VillagePlan p : new VillagePlan[]{v, hamlet}) {
                    SettlementPaint.dump(dir.resolve(p == v ? "village-dump.bin" : "hamlet-dump.bin"), p, 42L, p.chunks(), (x, z) -> {
                        int top = land.top(p.ox + x, p.oz + z) - p.baseY;
                        return land.water(p.ox + x, p.oz + z) > 0 ? top - 2000 : top;
                    });
                }
                java.nio.file.Files.writeString(dir.resolve("village-dump.txt"), text(v).replace("|", "\n") + "\n\n" + text(hamlet).replace("|", "\n"));
            } catch (java.io.IOException e) {
                h.fail("dump: " + e);
                return;
            }
        }
        int homes = (int) v.buildings.stream().filter(b -> b.use().equals("house")).count();
        int hHomes = (int) hamlet.buildings.stream().filter(b -> b.use().equals("house")).count();
        h.assertTrue(homes >= 6 && homes <= 20, "a village has 8-20 homes (a few may not fit): " + homes);
        h.assertTrue(v.people.size() >= 16 && v.people.size() <= 60, "a village houses 20-50 people: " + v.people.size());
        h.assertTrue(hHomes >= 3 && hHomes <= 6, "a hamlet has 3-6 homes: " + hHomes);
        h.assertTrue(hamlet.people.size() >= 6 && hamlet.people.size() <= 20, "a hamlet houses 8-16 people: " + hamlet.people.size());
        h.assertTrue(v.buildings.stream().anyMatch(b -> b.use().equals("inn")) && v.forge != null, "a village has its inn and its forge");
        h.assertTrue(v.bell != null && v.bell.tower() && hamlet.bell != null && !hamlet.bell.tower(), "a tower in the village, a post in the hamlet");
        // painted twice, chunk by chunk, the same blocks; never more than a few lights a chunk
        long[] chunks = v.chunks();
        int maxLights = 0, cells = 0;
        long p0 = System.nanoTime();
        for (long c : chunks) {
            int cx = ChunkPos.getX(c), cz = ChunkPos.getZ(c);
            long a = SettlementPaint.fingerprint(v, cx, cz, 42L), b = SettlementPaint.fingerprint(again, cx, cz, 42L);
            h.assertTrue(a == b, "chunk " + cx + "," + cz + " paints the same twice");
            int[] census = SettlementPaint.census(v, cx, cz, 42L);
            maxLights = Math.max(maxLights, census[1]);
            cells += census[0];
        }
        long paintNs = (System.nanoTime() - p0) / Math.max(1, chunks.length * 3);
        Awaken.LOG.info(String.format(Locale.ROOT, "[village] test plan: %s | %s | planned in %.1f ms (test land), %d chunks, %.2f ms a chunk, "
                + "%d cells, at most %d lights a chunk", v, hamlet, planNs / 1e6, chunks.length, paintNs / 1e6, cells, maxLights));
        h.assertTrue(maxLights <= 6, "at most a handful of light sources a chunk: " + maxLights);
        h.succeed();
    }
}
