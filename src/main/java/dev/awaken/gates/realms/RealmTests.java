package dev.awaken.gates.realms;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.realms.check.FloatCheck;
import dev.awaken.gates.realms.check.LookChecks;
import dev.awaken.gates.realms.kit.V;
import dev.awaken.gates.realms.land.AmberReach;
import dev.awaken.progression.Rank;
import net.minecraft.world.level.block.Blocks;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Realms' checks (SPEC §50.6 to §50.8). */
public final class RealmTests {
    private RealmTests() {}

    private static final Blueprint AMBER = new Blueprint(1, "Amber Reach", "land", "hive", List.of("E", "D"),
            new Blueprint.LandSpec("awaken:honey_highlands", 62, Map.of()), new Blueprint.Sky(List.of("awaken:realm/amber_meadow"), "amber_reach"),
            new Blueprint.Life(List.of("g"), List.of("p"), List.of("a")), new Blueprint.Folk("wax_folk", 1), new Blueprint.Law("updrafts", Map.of()),
            new Blueprint.Sites(3, 0, 24), new Blueprint.Heart("hive_regent", "mother_comb"), List.of("late_harvest"), "");

    private static Land amber(long seed) {
        return new AmberReach(new Land.Ctx(AMBER, "awaken:amber_reach", Rank.E, seed, 1));
    }

    public static void register() {
        AwakenTests.register("realm_blueprints_parse", 40, h -> {
            String why = AMBER.problem();
            if (!why.isEmpty()) h.fail("Amber's blueprint: " + why);
            h.succeed();
        });
        AwakenTests.register("realm_cells", 20, h -> {
            for (int cell : new int[]{0, 1, 63, 64, 500}) {
                int x = RealmCells.centerX(cell), z = RealmCells.centerZ(cell);
                if (RealmCells.cellAt(x, z) != cell || RealmCells.cellAt(x + 3000, z - 3000) != cell) h.fail("cell " + cell + " doesn't find itself");
                if (dev.awaken.gates.world.Cells.cellAt(x, z) >= 0) h.fail("a Realm reads as a floor cell");
            }
            if (RealmCells.cellAt(RealmCells.centerX(0) + RealmCells.HALF + 8, RealmCells.centerZ(0)) != -1) h.fail("the gap between cells belongs to one");
            h.succeed();
        });
        AwakenTests.register("realm_bag_empty", 20, h -> {
            Blueprints.replace(Map.of("awaken:amber_reach", AMBER));
            if (Blueprints.pick(Theme.BELL_CRYPT, Rank.E, Set.of(), 1).isPresent()) h.fail("a theme with no Realm rolled one");
            if (Blueprints.pick(Theme.HIVE, Rank.S, Set.of(), 1).isPresent()) h.fail("a rank with no Realm rolled one");
            if (Blueprints.pick(Theme.HIVE, Rank.E, Set.of(), 1).isEmpty()) h.fail("the open Realm wasn't rolled");
            if (Blueprints.pick(Theme.HIVE, Rank.E, Set.of("awaken:amber_reach"), 1).isEmpty()) h.fail("a full bag didn't refill");
            h.succeed();
        });
        // the fence posts Euan caught: a real floating post has to fail, or the check proves nothing
        AwakenTests.register("realm_float_check_catches", 200, h -> {
            Land land = new Probe(8);
            FloatCheck.Report r = FloatCheck.scan(land, 0, 0, 31, 31);
            if (r.floaters().size() != 3) h.fail("expected a post, a plant and a lantern in the air, got: " + r.summary());
            FloatCheck.Report ok = FloatCheck.scan(new Probe(0), 0, 0, 31, 31);
            if (!ok.clean()) h.fail("a post on the ground counted as floating: " + ok.summary());
            h.succeed();
        });
        AwakenTests.register("realm_amber_never_floats", 1200, h -> {
            for (long seed : new long[]{1, 2}) {
                Land land = amber(seed);
                // from the Mouth through the village to the citadel's stair: where you'll stand and look
                FloatCheck.Report r = FloatCheck.scan(land, -60, -130, 240, 170);
                dev.awaken.Awaken.LOG.info("[realms] float check, seed {}: {} in {} ms", seed, r.summary(), r.millis());
                if (!r.clean()) h.fail("seed " + seed + ": " + r.summary());
            }
            h.succeed();
        });
        AwakenTests.register("realm_amber_plan_checks", 400, h -> {
            for (long seed = 1; seed <= 8; seed++) {
                Land land = amber(seed);
                List<String> bad = LookChecks.problems(land);
                if (!bad.isEmpty()) {
                    bad.forEach(b -> dev.awaken.Awaken.LOG.warn("[realms] {}", b));
                    h.fail("seed " + seed + ": " + bad.size() + ", first: " + bad.getFirst());
                }
                Land.Site m = land.mouth();
                if (m.y() <= 62) h.fail("seed " + seed + ": the Mouth is at the water");
                if (land.sites(Land.SiteKind.WARDEN).size() < 3) h.fail("seed " + seed + ": too few Wardens");
                if (land.sites(Land.SiteKind.FIND).size() < 12) h.fail("seed " + seed + ": too few finds");
            }
            h.succeed();
        });
        AwakenTests.register("realm_amber_all_reachable", 800, h -> {
            for (long seed : new long[]{1, 2}) {
                Land land = amber(seed);
                int ok = reachable(land);
                if (ok < 96) h.fail("seed " + seed + ": only " + ok + "% of the valley can be walked from the Mouth");
            }
            h.succeed();
        });
        AwakenTests.register("realm_seeds_differ", 200, h -> {
            Set<Integer> seen = new HashSet<>();
            for (long seed = 1; seed <= 5; seed++) {
                Land l = amber(seed);
                int sum = 0;
                for (int x = 0; x < 400; x += 16) for (int z = -200; z < 200; z += 16) sum = sum * 31 + l.ground(x, z);
                seen.add(sum);
            }
            if (seen.size() != 5) h.fail("seeds made the same land");
            h.succeed();
        });
    }

    /** A flood from the Mouth over walkable steps (up 1, or 2 if there's room to jump; down any): the share of the valley reached. */
    static int reachable(Land land) {
        int R = 360, step = 2, n = R * 2 / step;
        int[][] h = new int[n][n];
        for (int a = 0; a < n; a++) for (int b = 0; b < n; b++) h[a][b] = land.ground(190 - R + a * step, -R + b * step);
        boolean[][] seen = new boolean[n][n];
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
        Land.Site m = land.mouth();
        int sa = Math.max(0, Math.min(n - 1, (m.x() - 190 + R) / step)), sb = Math.max(0, Math.min(n - 1, (m.z() + R) / step));
        q.add(new int[]{sa, sb});
        seen[sa][sb] = true;
        int reach = 0, total = 0;
        while (!q.isEmpty()) {
            int[] p = q.poll();
            reach++;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int a = p[0] + d[0], b = p[1] + d[1];
                if (a < 0 || b < 0 || a >= n || b >= n || seen[a][b]) continue;
                if (h[a][b] - h[p[0]][p[1]] > 2 * step) continue; // over two blocks of ground, at the sampling distance
                seen[a][b] = true;
                q.add(new int[]{a, b});
            }
        }
        for (int a = 0; a < n; a++) for (int b = 0; b < n; b++) {
            // only land inside the valley's wall counts
            if (Math.hypot(190 - R + a * step - 190, (-R + b * step) * 1.15) < AmberBounds.WALK) total += 1;
        }
        return (int) Math.min(100, reach * 100L / Math.max(1, total));
    }

    private static final class AmberBounds {
        static final double WALK = 300;
    }

    /** A flat square with one post, one plant and one lantern that are, or aren't, up in the air. */
    private static final class Probe extends Land {
        final int lift;

        Probe(int lift) {
            super(new Ctx(AMBER, "probe", Rank.E, 0, 1));
            this.lift = lift;
        }

        @Override public int ground(int x, int z) { return 10; }

        @Override
        public void paint(V v) {
            for (int x = v.x0; x < v.x0 + 16; x++) for (int z = v.z0; z < v.z0 + 16; z++) for (int y = 0; y <= 10; y++) v.set(x, y, z, Blocks.STONE.defaultBlockState());
            if (v.in(8, 0, 8)) v.set(8, 11 + lift, 8, Blocks.DARK_OAK_FENCE.defaultBlockState());
            if (v.in(12, 0, 8)) v.set(12, 11 + lift, 8, Blocks.SHORT_GRASS.defaultBlockState());
            if (v.in(8, 0, 12)) v.set(8, 11 + lift, 12, Blocks.LANTERN.defaultBlockState());
        }

        @Override public int biome(int x, int y, int z) { return 0; }
        @Override public int halfSize() { return 16; }
        @Override public List<? extends dev.awaken.gates.realms.kit.Piece<?>> pieces() { return List.of(); }
        @Override public List<double[]> ways() { return List.of(); }
        @Override public float mouthYaw() { return 0; }
    }
}
