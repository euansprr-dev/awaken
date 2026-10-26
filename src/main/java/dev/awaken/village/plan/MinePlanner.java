package dev.awaken.village.plan;

import dev.awaken.city.CityPlan;
import dev.awaken.city.VillageStyles;
import org.jspecify.annotations.Nullable;

/**
 * A mining town (SPEC §28.9, PLAN 5.2: "a mining town at a mountain's foot over its own mine"): a village's site with a
 * mountain rising from its edge, and at the mountain's foot the shaft head (a timber headframe over a lined shaft with its
 * winch), the assayer's store that sells the mine's ore and lode maps, and under them the workings: the shaft down to the
 * first level, a timbered gallery under the mountain to the landing and the winze, the second level driven to a lode
 * (W2's own, where one lies in reach, else a seam of the land's ore), and behind its face the cavern the Deep Mine breaks
 * into. Where no mountain rises near, the anchor plans as a village.
 */
public final class MinePlanner {
    private MinePlanner() {}

    /** The mining town's plan, or null where no mountain rises near (it plans as a village then). */
    static @Nullable VillagePlan plan(VillagePlanner.Request q, Terrain t) {
        return new MineWork(q.as(VillagePlan.Kind.MINING), t).run();
    }

    /** How deep the first level lies under the head, and how far the second lies under the first. */
    static final int LEVEL1 = 24, LEVEL2 = 16;

    static class MineWork extends VillagePlanner.Work {
        int headX, headZ, headLift, side;

        MineWork(VillagePlanner.Request q, Terrain t) {
            super(q, t);
        }

        @Override
        @Nullable VillagePlan run() {
            VillagePlanner.WHY.set("");
            if (!site()) return null;
            sample();
            double[] m = mountain();
            if (m == null) {
                VillagePlanner.WHY.set("no mountain rises near");
                return null;
            }
            int fam = family();
            int wealth = rnd.nextInt(100) < 60 ? VillageStyles.MODEST : VillageStyles.POOR;
            plan = new VillagePlan(q.key(), Names.mining(fam, q.seed()), VillagePlan.Kind.MINING, fam, wealth, q.seed(), cx, cz, baseY, r);
            streetDist = new float[plan.n * plan.n];
            java.util.Arrays.fill(streetDist, Float.MAX_VALUE);
            ground();
            green();
            streets();
            keepExits();
            if (!head(m[0], (int) m[1])) {
                VillagePlanner.WHY.set("no room for the shaft head at the mountain's foot");
                return null;
            }
            buildings();
            assay();
            if (plan.plots.size() < 5) {
                VillagePlanner.WHY.set("only " + plan.plots.size() + " buildings fit");
                return null;
            }
            mill();
            dock();
            gardens();
            fields();
            pens();
            trees();
            lines();
            lamps();
            props();
            workings();
            people();
            plan.index();
            return plan;
        }

        /** The way up the steepest rise from the site that climbs 26 or more, and how far out its foot lies: {angle, foot}. */
        double @Nullable [] mountain() {
            double best = 0;
            double[] out = null;
            for (int a = 0; a < 16; a++) {
                double th = a * Math.PI / 8;
                int foot = -1, rise = 0;
                for (int d = 24; d <= r + 40; d += 6) {
                    int h = topAt(cx + (int) Math.round(Math.cos(th) * d), cz + (int) Math.round(Math.sin(th) * d)) - baseY;
                    if (foot < 0 && h >= 4) foot = d;
                    rise = Math.max(rise, h);
                }
                if (foot < 0 || foot > r - 14 || rise < 26) continue;
                double score = rise - foot * 0.25;
                if (score > best) {
                    best = score;
                    out = new double[]{th, foot};
                }
            }
            return out;
        }

        /**
         * The shaft head at the mountain's foot: a 7x7 floor on level ground, kept clear two round, its way into the
         * mountain along the nearest axis, a path from it to the nearest street.
         */
        boolean head(double th, int foot) {
            for (int d = foot - 4; d >= plan.greenR + 14; d -= 2) {
                for (double dt : new double[]{0, 0.15, -0.15, 0.3, -0.3}) {
                    int hx = (int) Math.round(Math.cos(th + dt) * d), hz = (int) Math.round(Math.sin(th + dt) * d);
                    if (Math.abs(hx) > r - 14 || Math.abs(hz) > r - 14) continue;
                    boolean ok = true;
                    float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
                    for (int z = hz - 5; z <= hz + 5 && ok; z++)
                        for (int x = hx - 5; x <= hx + 5 && ok; x++) {
                            ok = free(x, z) && !q.reserved().test(cx + x, cz + z);
                            if (Math.abs(x - hx) <= 3 && Math.abs(z - hz) <= 3) {
                                float h = height(x, z);
                                lo = Math.min(lo, h);
                                hi = Math.max(hi, h);
                            }
                        }
                    if (!ok || hi - lo > 4) continue;
                    headX = hx;
                    headZ = hz;
                    headLift = Math.round((lo + hi) / 2) - baseY;
                    side = Math.abs(Math.cos(th)) >= Math.abs(Math.sin(th)) ? (Math.cos(th) > 0 ? 1 : 3) : (Math.sin(th) > 0 ? 2 : 0);
                    for (int z = hz - 4; z <= hz + 4; z++)
                        for (int x = hx - 4; x <= hx + 4; x++)
                            plan.set(x, z, Math.abs(x - hx) <= 3 && Math.abs(z - hz) <= 3 ? CityPlan.LANDMARK : CityPlan.YARD, headLift);
                    // the path from the head's town side to the street
                    int back = (side + 2) & 3;
                    int px = hx + STEP_X[back] * 5, pz = hz + STEP_Z[back] * 5;
                    int[] near = nearestPoint(px, pz);
                    if (near != null) {
                        VillagePlan.Street st = plan.streets.get(near[0]);
                        path(px, pz, Math.round(st.x()[near[1]]), Math.round(st.z()[near[1]]), headLift, st.lift()[near[1]]);
                    }
                    return true;
                }
            }
            return false;
        }

        /** The assayer's store near the head: the mine's ore, ingots and lode maps for sale. */
        void assay() {
            java.util.List<int[]> slots = new java.util.ArrayList<>();
            for (int s = 0; s < plan.streets.size(); s++) {
                VillagePlan.Street st = plan.streets.get(s);
                for (int k = 0; k < st.points(); k++) for (int sd = -1; sd <= 1; sd += 2) slots.add(new int[]{s, k, sd});
            }
            slots.sort((u, v) -> {
                VillagePlan.Street a = plan.streets.get(u[0]), b = plan.streets.get(v[0]);
                return Double.compare(Math.hypot(a.x()[u[1]] - headX, a.z()[u[1]] - headZ), Math.hypot(b.x()[v[1]] - headX, b.z()[v[1]] - headZ));
            });
            place(new Want("assay", 9, 7, 2, 1), slots);
        }

        @Override
        void moreJobs(java.util.List<String[]> jobs) {
            // the miners: four to six down the shaft every day
            int n = 4 + rnd.nextInt(3);
            for (int k = 0; k < n; k++) jobs.add(1 + k, new String[]{"miner", "mine", "0"});
        }

        /**
         * The workings: the first level 24 under the head, its gallery along the way into the mountain to the landing and the
         * winze; the second level 16 lower (or at the lode's own depth), its gallery toward W2's nearest lode in reach (else
         * on along the way to a seam of the land's ore); the cavern behind the face. Everything inside the plan's square.
         */
        void workings() {
            int l1 = headLift - LEVEL1;
            int along = side == 1 ? r - 6 - headX : side == 3 ? r - 6 + headX : side == 2 ? r - 6 - headZ : r - 6 + headZ;
            int len1 = Math.clamp(along, 12, 24);
            int landing = Math.max(6, len1 / 2), winze = len1;
            int wx = headX + STEP_X[side] * winze, wz = headZ + STEP_Z[side] * winze;
            int l2 = l1 - LEVEL2, side2 = side, reach2 = 18;
            String ore = null;
            int ox = 0, oy = 0, oz = 0;
            var world = dev.awaken.village.road.RoadWorld.get();
            if (world != null) {
                // W2's lodes near the winze's foot, a level's height or so under the first level
                dev.awaken.delve.Lode best = null;
                double bd = Double.MAX_VALUE;
                int wxw = cx + wx, wzw = cz + wz;
                int rx0 = Math.floorDiv(wxw - 40, dev.awaken.delve.LodePlans.REGION), rx1 = Math.floorDiv(wxw + 40, dev.awaken.delve.LodePlans.REGION);
                int rz0 = Math.floorDiv(wzw - 40, dev.awaken.delve.LodePlans.REGION), rz1 = Math.floorDiv(wzw + 40, dev.awaken.delve.LodePlans.REGION);
                for (int rx = rx0; rx <= rx1; rx++)
                    for (int rz = rz0; rz <= rz1; rz++)
                        for (dev.awaken.delve.Lode l : dev.awaken.delve.LodePlans.region(world.seed, rx, rz)) {
                            if (l.ore() == dev.awaken.delve.Ore.DEEPGLASS || l.ore() == dev.awaken.delve.Ore.DIAMOND) continue;
                            var h = l.heartPos();
                            int dy = h.getY() - (baseY + l1);
                            if (dy > -8 || dy < -34) continue;
                            int hx = h.getX() - cx, hz = h.getZ() - cz;
                            if (Math.abs(hx) > r - 8 || Math.abs(hz) > r - 8) continue;
                            double d = Math.hypot(h.getX() - wxw, h.getZ() - wzw);
                            if (d > 34 || d >= bd) continue;
                            bd = d;
                            best = l;
                        }
                if (best != null) {
                    var h = best.heartPos();
                    int hx = h.getX() - cx, hz = h.getZ() - cz;
                    int dx = hx - wx, dz = hz - wz;
                    side2 = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? 1 : 3) : (dz > 0 ? 2 : 0);
                    reach2 = Math.max(6, Math.abs(side2 == 1 || side2 == 3 ? dx : dz) - 1);
                    l2 = h.getY() - baseY - 1;
                    ore = best.ore().name().toLowerCase(java.util.Locale.ROOT);
                    ox = hx;
                    oy = h.getY() - baseY;
                    oz = hz;
                }
            }
            if (ore == null) {
                ore = switch (plan.family) {
                    case VillageStyles.DESERT, VillageStyles.SAVANNA -> "copper";
                    case VillageStyles.HIGHLAND, VillageStyles.FROST -> "iron";
                    default -> rnd.nextBoolean() ? "iron" : "coal";
                };
                int[] f = {wx + STEP_X[side2] * (reach2 + 1), wz + STEP_Z[side2] * (reach2 + 1)};
                ox = f[0];
                oy = l2 + 2;
                oz = f[1];
            }
            // keep the second level and the cavern inside the plan's square
            int ex = wx + STEP_X[side2] * (reach2 + 13), ez = wz + STEP_Z[side2] * (reach2 + 13);
            if (Math.abs(ex) > r - 2 || Math.abs(ez) > r - 2) reach2 = Math.max(6, reach2 - Math.max(Math.abs(ex), Math.abs(ez)) + r - 2);
            int cavX = wx + STEP_X[side2] * (reach2 + 7), cavZ = wz + STEP_Z[side2] * (reach2 + 7);
            plan.mine = new VillagePlan.Mine(headX, headZ, headLift, side, l1, l2, landing, winze, reach2, side2, ore, ox, oy, oz, cavX, l2 + 2, cavZ);
        }
    }
}
