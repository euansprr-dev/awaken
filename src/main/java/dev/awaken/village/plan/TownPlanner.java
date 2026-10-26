package dev.awaken.village.plan;

import org.jspecify.annotations.Nullable;

import java.util.function.BiPredicate;

/**
 * A region's walled town (SPEC §28.9, PLAN 5.2): its site near the region's heart, then the plan.
 */
public final class TownPlanner {
    private TownPlanner() {}

    /** How far from a region's heart a town's site may be. */
    static final int SEARCH = 192;

    /**
     * The best site for a town within {@link #SEARCH} of a heart: broad, gentle, dry ground with water near (a river's
     * bank or its fork), not too high; {x, z} or null where the land takes none. A 48-block lattice of centres, each read
     * over a 72-block core on a 16-block lattice (each column once).
     */
    public static int @Nullable [] site(Terrain t, int hx, int hz, BiPredicate<Integer, Integer> reserved) {
        it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap memo = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
        memo.defaultReturnValue(Long.MIN_VALUE);
        double best = Double.NEGATIVE_INFINITY;
        int bx = 0, bz = 0;
        int core = 72;
        for (int dz = -SEARCH; dz <= SEARCH; dz += 48) {
            for (int dx = -SEARCH; dx <= SEARCH; dx += 48) {
                int x = Math.floorDiv(hx + dx, 16) * 16, z = Math.floorDiv(hz + dz, 16) * 16;
                double s = score(t, memo, x, z, core, reserved) - Math.hypot(dx, dz) * 0.01;
                if (s > best) {
                    best = s;
                    bx = x;
                    bz = z;
                }
            }
        }
        return best == Double.NEGATIVE_INFINITY || best < -38 ? null : new int[]{bx, bz};
    }

    private static long land(Terrain t, it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap memo, int x, int z) {
        long k = ((long) x << 32) ^ (z & 0xffffffffL);
        long v = memo.get(k);
        if (v == Long.MIN_VALUE) {
            v = t.both(x, z);
            memo.put(k, v);
        }
        return v;
    }

    static double score(Terrain t, it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap memo, int x, int z, int core, BiPredicate<Integer, Integer> reserved) {
        int n = 0, wetN = 0;
        double sum = 0, sum2 = 0;
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int dz = -core; dz <= core; dz += 16) {
            for (int dx = -core; dx <= core; dx += 16) {
                if (dx * dx + dz * dz > core * core) continue;
                if (reserved.test(x + dx, z + dz)) return Double.NEGATIVE_INFINITY;
                long l = land(t, memo, x + dx, z + dz);
                int h = (int) (l >> 32);
                if ((int) l > 0) {
                    wetN++;
                    continue;
                }
                n++;
                sum += h;
                sum2 += (double) h * h;
                lo = Math.min(lo, h);
                hi = Math.max(hi, h);
            }
        }
        if (n < 20 || wetN > (n + wetN) * 0.12) return Double.NEGATIVE_INFINITY;
        double mean = sum / n, sd = Math.sqrt(Math.max(0, sum2 / n - mean * mean));
        int range = hi - lo;
        if (range > 24 || mean < t.seaLevel() + 2 || mean > 140) return Double.NEGATIVE_INFINITY;
        // water near the walls: a river or a lake within 48 of the core's edge
        int near = 0;
        for (int a = 0; a < 16; a++) {
            double ang = a * Math.PI / 8;
            for (int d = core + 16; d <= core + 48; d += 16) {
                int wx = x + 16 * (int) Math.round(Math.cos(ang) * d / 16), wz = z + 16 * (int) Math.round(Math.sin(ang) * d / 16);
                if ((int) land(t, memo, wx, wz) > 0) {
                    near++;
                    break;
                }
            }
        }
        return -sd * 2.2 - range * 0.5 - wetN * 3 + Math.min(near, 5) * 2.0 - Math.abs(mean - 74) * 0.05;
    }

    /** A town's raster reaches this far (its structure's reach less the beard). */
    static final int TOWN_R = 112;
    /** The wall's mean radius: about what thirty to forty homes, their lanes, the square and the civic buildings fill. */
    static final int WALL = 60;

    /**
     * The town's plan: inside a wall that follows the land round it, a market square where two main streets cross (each
     * out through a gate in the wall), lanes off them packing the quarters with houses, the inn, the hall, the scribe's
     * shop, the smithy and the bell tower on the square; fields, pens, barns and the mill outside the walls. Quarters
     * keep their own wealth: stone and slate round the market, timber and plaster further out, the poorest by a gate.
     */
    static class TownWork extends VillagePlanner.Work {
        float[] ring;
        boolean mainWalk;

        TownWork(VillagePlanner.Request q, Terrain t) {
            super(q, t);
        }

        @Override
        @Nullable VillagePlan run() {
            VillagePlanner.WHY.set("");
            cx = q.anchorX();
            cz = q.anchorZ();
            int room = Math.min(Math.min(cx - q.minX(), q.maxX() - cx), Math.min(cz - q.minZ(), q.maxZ() - cz)) - 2;
            r = Math.min(TOWN_R, room);
            if (r < 100) {
                VillagePlanner.WHY.set("no room in the structure's reach");
                return null;
            }
            sample();
            int fam = family();
            int wealth = rnd.nextInt(100) < 55 ? dev.awaken.city.VillageStyles.PROSPEROUS : dev.awaken.city.VillageStyles.MODEST;
            plan = new VillagePlan(q.key(), Names.town(fam, q.seed()), VillagePlan.Kind.TOWN, fam, wealth, q.seed(), cx, cz, baseY, r);
            streetDist = new float[plan.n * plan.n];
            java.util.Arrays.fill(streetDist, Float.MAX_VALUE);
            ground();
            ring();
            square();
            streets();
            if (plan.gates.size() < 2) {
                VillagePlanner.WHY.set("the main streets reach the walls by fewer than two gates");
                return null;
            }
            walls();
            keepExits();
            buildings();
            long homes = plan.buildings.stream().filter(b -> b.use().equals("house")).count();
            if (homes < 18) {
                VillagePlanner.WHY.set("only " + homes + " homes fit inside the walls");
                return null;
            }
            districts();
            mill();
            dock();
            gardens();
            fields();
            pens();
            trees();
            lines();
            lamps();
            props();
            people();
            plan.index();
            return plan;
        }

        // ---- the wall's line ------------------------------------------------------------------------------------------

        void ring() {
            int w0 = Math.min(WALL, r - 30);
            double p1 = rnd.nextDouble() * Math.PI * 2, p2 = rnd.nextDouble() * Math.PI * 2;
            ring = new float[360];
            for (int a = 0; a < 360; a++) {
                double th = Math.toRadians(a);
                ring[a] = (float) (w0 + 4 * Math.sin(2 * th + p1) + 3 * Math.sin(3 * th + p2));
            }
            plan.wallRing = ring;
        }

        /** The wall's radius in the direction of (x, z). */
        float wallAt(double x, double z) {
            double deg = (Math.toDegrees(Math.atan2(z, x)) + 360) % 360;
            int a = (int) Math.floor(deg) % 360, b = (a + 1) % 360;
            float f = (float) (deg - Math.floor(deg));
            return ring[a] * (1 - f) + ring[b] * f;
        }

        @Override
        boolean joins() {
            return !mainWalk;
        }

        @Override
        boolean walkable(int x, int z) {
            double d = Math.hypot(x, z);
            return d <= wallAt(x, z) + (mainWalk ? 6 : -8);
        }

        @Override
        boolean within(int x, int z, int margin) {
            return Math.hypot(x, z) <= wallAt(x, z) - 2 - margin;
        }

        @Override
        int[] fieldBand() {
            return new int[]{Math.min(WALL, r - 30) + 13, r - 9};
        }

        @Override
        int maxLamps() {
            return 34;
        }

        @Override
        int[] peopleBounds() {
            return new int[]{60, 120};
        }

        // ---- the market square and the streets -------------------------------------------------------------------------

        void square() {
            int g = 11;
            plan.greenR = g;
            for (int z = -g - 1; z <= g + 1; z++)
                for (int x = -g - 1; x <= g + 1; x++) {
                    double e = Math.pow(Math.abs(x) / (g + 0.5), 4) + Math.pow(Math.abs(z) / (g + 0.5), 4);
                    if (e <= 1 && dry(x, z)) plan.set(x, z, dev.awaken.city.CityPlan.SQUARE, 0);
                }
        }

        @Override
        void streets() {
            float gxh = height(14, 0) - height(-14, 0), gzh = height(0, 14) - height(0, -14);
            double heading = Math.hypot(gxh, gzh) < 1.5 ? rnd.nextDouble() * Math.PI : Math.atan2(gzh, gxh) + Math.PI / 2;
            mainWalk = true;
            for (int m = 0; m < 2; m++) {
                double h = heading + m * Math.PI / 2;
                int len = Math.min(WALL, r - 30) + 14;
                java.util.List<float[]> a = through(walk(0, 0, h, len, 1.2)), b = through(walk(0, 0, h + Math.PI, len, 1.2));
                if (a.size() < 3 && b.size() < 3) continue;
                java.util.List<float[]> main = new java.util.ArrayList<>();
                for (int k = b.size() - 1; k >= 1; k--) main.add(b.get(k));
                main.addAll(a);
                int centre = b.size() - 1;
                addStreet(0, 4.6f, main, centre);
                int s = plan.streets.size() - 1;
                VillagePlan.Street st = plan.streets.get(s);
                // a gate where an end reaches the wall
                for (int end = 0; end < 2; end++) {
                    int i = end == 0 ? 0 : st.points() - 1, j = end == 0 ? 1 : st.points() - 2;
                    float ex = st.x()[i], ez = st.z()[i];
                    if (Math.hypot(ex, ez) < wallAt(ex, ez) + 2) continue;
                    double dx = ex - st.x()[j], dz = ez - st.z()[j];
                    int side = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? 1 : 3) : (dz > 0 ? 2 : 0);
                    // the gate is where the street meets the wall's line
                    int gk = i;
                    for (int k = 0; k < st.points(); k++) {
                        int kk = end == 0 ? k : st.points() - 1 - k;
                        if (Math.hypot(st.x()[kk], st.z()[kk]) <= wallAt(st.x()[kk], st.z()[kk])) break;
                        gk = kk;
                    }
                    plan.gates.add(new int[]{Math.round(st.x()[gk]), Math.round(st.z()[gk]), st.lift()[gk], side, s});
                    plan.posts.add(new int[]{Math.round(st.x()[gk] - (float) Math.signum(dx) * 3), Math.round(st.z()[gk] - (float) Math.signum(dz) * 3),
                            st.lift()[gk]});
                    plan.exitList.add(new int[]{s, end});
                }
            }
            mainWalk = false;
            plan.gatePosts = plan.posts.size();
            if (plan.streets.isEmpty()) return;
            // lanes off the main streets every two dozen blocks, either side, packing the quarters
            int mains = plan.streets.size();
            for (int s = 0; s < mains; s++) {
                VillagePlan.Street st = plan.streets.get(s);
                for (int k = 2; k < st.points() - 2; k++) {
                    double d = Math.hypot(st.x()[k], st.z()[k]);
                    if (d < plan.greenR + 8 || Math.floorMod(k + s, 4) != 0) continue;
                    float dx = st.x()[k + 1] - st.x()[k - 1], dz = st.z()[k + 1] - st.z()[k - 1];
                    double along = Math.atan2(dz, dx);
                    for (int side = -1; side <= 1; side += 2) {
                        java.util.List<float[]> lane = walk(st.x()[k], st.z()[k], along + side * Math.PI / 2, 66, 1.5);
                        if (lane.size() >= 4) addStreet(1, 3.4f, lane, 0, st.lift()[k]);
                    }
                }
            }
        }

        /** A main street's walk out to the wall, on through the gate a few blocks past it. */
        java.util.List<float[]> through(java.util.List<float[]> pts) {
            if (pts.size() < 2) return pts;
            float[] last = pts.getLast(), prev = pts.get(pts.size() - 2);
            float d = (float) Math.hypot(last[0], last[1]), w = wallAt(last[0], last[1]);
            if (d < w - 7) return pts; // it never got to the wall
            float dx = last[0] - prev[0], dz = last[1] - prev[1], l = (float) Math.hypot(dx, dz);
            float out = w + 4 - d;
            if (out > 0) {
                float nx = last[0] + dx / l * out, nz = last[1] + dz / l * out;
                if (dry(Math.round(nx), Math.round(nz))) pts.add(new float[]{nx, nz});
            }
            return pts;
        }

        // ---- the wall, its towers and gates ----------------------------------------------------------------------------

        /** The wall's height of ground every degree round: the land under its line, smoothed, never more than a block a degree. */
        int[] ringLift() {
            int[] raw = new int[360];
            for (int a = 0; a < 360; a++) {
                double th = Math.toRadians(a);
                raw[a] = Math.round(height((int) Math.round(Math.cos(th) * ring[a]), (int) Math.round(Math.sin(th) * ring[a]))) - baseY;
            }
            int[] out = new int[360];
            for (int a = 0; a < 360; a++) {
                int sum = 0;
                for (int k = -3; k <= 3; k++) sum += raw[Math.floorMod(a + k, 360)];
                out[a] = Math.round(sum / 7f);
            }
            // at the gates, the street's own height
            for (int[] g : plan.gates) {
                int a = (int) Math.floorMod(Math.round(Math.toDegrees(Math.atan2(g[1], g[0]))), 360L);
                for (int k = -3; k <= 3; k++) out[Math.floorMod(a + k, 360)] = g[2];
            }
            for (int pass = 0; pass < 2; pass++)
                for (int a = 1; a < 720; a++) {
                    int i = a % 360, h = (a - 1) % 360;
                    out[i] = Math.clamp(out[i], out[h] - 1, out[h] + 1);
                }
            return out;
        }

        void walls() {
            int[] lift = ringLift();
            for (int z = -r; z <= r; z++) {
                for (int x = -r; x <= r; x++) {
                    double d = Math.hypot(x, z);
                    float w = wallAt(x, z);
                    if (Math.abs(d - w) > 1.3) continue;
                    byte k = plan.kind(x, z);
                    if (k == VillagePlan.WATER || k == dev.awaken.city.CityPlan.VOID) continue;
                    if (k == dev.awaken.city.CityPlan.STREET || k == dev.awaken.city.CityPlan.LANE) continue; // a gate's passage
                    int a = (int) Math.floorMod(Math.round(Math.toDegrees(Math.atan2(z, x))), 360L);
                    plan.set(x, z, dev.awaken.city.CityPlan.WALL, lift[a]);
                }
            }
            // towers about every thirty blocks round, none within a gate's reach; a gate's two towers flank its passage
            java.util.List<Integer> gateAngles = new java.util.ArrayList<>();
            for (int[] g : plan.gates) gateAngles.add((int) Math.floorMod(Math.round(Math.toDegrees(Math.atan2(g[1], g[0]))), 360L));
            for (int[] g : plan.gates) {
                double th = Math.atan2(g[1], g[0]), w = wallAt(g[0], g[1]);
                double tx = -Math.sin(th), tz = Math.cos(th);
                for (int sgn = -1; sgn <= 1; sgn += 2) {
                    int x = (int) Math.round(Math.cos(th) * w + tx * sgn * 5), z = (int) Math.round(Math.sin(th) * w + tz * sgn * 5);
                    tower(x, z, lift[(int) Math.floorMod(Math.round(Math.toDegrees(Math.atan2(z, x))), 360L)], 2);
                }
            }
            double step = Math.toDegrees(30.0 / Math.min(WALL, r - 30));
            for (double a = rnd.nextDouble() * step; a < 360; a += step) {
                boolean nearGate = false;
                for (int ga : gateAngles) nearGate |= Math.abs(((a - ga + 540) % 360) - 180) < step * 0.6;
                if (nearGate) continue;
                int ai = (int) Math.floorMod(Math.round(a), 360L);
                double th = Math.toRadians(a);
                int x = (int) Math.round(Math.cos(th) * ring[ai]), z = (int) Math.round(Math.sin(th) * ring[ai]);
                if (plan.kind(x, z) != dev.awaken.city.CityPlan.WALL) continue;
                tower(x, z, lift[ai], 2);
            }
        }

        void tower(int x, int z, int lift, int half) {
            for (int dz = -half; dz <= half; dz++)
                for (int dx = -half; dx <= half; dx++) {
                    byte k = plan.kind(x + dx, z + dz);
                    if (k == VillagePlan.WATER || k == dev.awaken.city.CityPlan.STREET || k == dev.awaken.city.CityPlan.LANE
                            || k == dev.awaken.city.CityPlan.VOID) return;
                }
            for (int dz = -half; dz <= half; dz++) for (int dx = -half; dx <= half; dx++) plan.set(x + dx, z + dz, dev.awaken.city.CityPlan.WALL, lift);
            plan.towers.add(new int[]{x, z, lift, half});
        }

        // ---- the buildings -------------------------------------------------------------------------------------------

        @Override
        void buildings() {
            java.util.List<Want> civic = new java.util.ArrayList<>(), outer = new java.util.ArrayList<>();
            civic.add(new Want("inn", 13, 11, 2, 2));
            civic.add(new Want("hall", 11, 9, 2, 2));
            civic.add(new Want("scribe", 9, 7, 2, 1));
            civic.add(new Want("smithy", 9, 7, 1, 2));
            int homes = 30 + rnd.nextInt(14);
            java.util.List<Want> houses = new java.util.ArrayList<>();
            for (int k = 0; k < homes; k++) {
                int storeys = rnd.nextInt(100) < 72 ? 2 : 1;
                int w = storeys == 2 ? 7 + rnd.nextInt(2) * 2 : 7;
                int d = 7 + (rnd.nextInt(100) < 30 ? 2 : 0);
                houses.add(new Want("house", w, d, storeys, 1));
            }
            outer.add(new Want("granary", 7, 7, 1, 2));
            outer.add(new Want("barn", 11, 9, 1, 2));
            if (rnd.nextBoolean()) outer.add(new Want("barn", 9, 7, 1, 2));
            java.util.List<int[]> slots = new java.util.ArrayList<>();
            for (int s = 0; s < plan.streets.size(); s++) {
                VillagePlan.Street st = plan.streets.get(s);
                for (int k = 0; k < st.points(); k++) for (int side = -1; side <= 1; side += 2) slots.add(new int[]{s, k, side});
            }
            slots.sort((u, v) -> Double.compare(slotDist(u), slotDist(v)));
            for (Want w : civic) place(w, slots);
            // the barns and the granary at the edge of town, by the walls
            java.util.List<int[]> far = new java.util.ArrayList<>(slots);
            java.util.Collections.reverse(far);
            for (Want w : outer) place(w, far);
            for (Want w : houses) {
                int before = plan.plots.size();
                place(w, slots);
                if (plan.plots.size() == before && w.w() > 7) place(new Want("house", 7, 7, w.storeys(), 1), slots);
            }
        }

        /**
         * The quarters: round the market the richest (stone and slate; in the heartland the highland's dressed stone), the
         * quarter by the first gate the poorest, the rest between; a house here and there a step up or down.
         */
        void districts() {
            VillagePlan.Street a = plan.streets.getFirst();
            double h = Math.atan2(a.z()[a.points() - 1] - a.z()[0], a.x()[a.points() - 1] - a.x()[0]);
            for (int i = 0; i < plan.plots.size(); i++) {
                dev.awaken.city.CityPlan.Plot p = plan.plots.get(i);
                double mx = (p.x0() + p.x1()) / 2.0, mz = (p.z0() + p.z1()) / 2.0;
                double d = Math.hypot(mx, mz);
                double ang = Math.atan2(mz, mx) - h;
                int quarter = (int) Math.floorMod(Math.round(ang / (Math.PI / 2)), 4L);
                int wealth = d < plan.greenR + 18 ? 2 : switch (quarter) {
                    case 0 -> 2;
                    case 2 -> 0;
                    default -> 1;
                };
                if (rnd.nextInt(100) < 18) wealth = Math.clamp(wealth + (rnd.nextBoolean() ? 1 : -1), 0, 2);
                if (p.use().equals("inn") || p.use().equals("hall")) wealth = 2;
                int fam = plan.family;
                if (fam == dev.awaken.city.VillageStyles.TEMPERATE && wealth == 2 && d < plan.greenR + 30 && !p.use().equals("inn"))
                    fam = dev.awaken.city.VillageStyles.HIGHLAND;
                plan.plots.set(i, new dev.awaken.city.CityPlan.Plot(p.id(), dev.awaken.city.VillageStyles.district(fam, wealth), p.x0(), p.z0(), p.x1(), p.z1(),
                        p.front(), p.storeys(), p.roof(), p.ridgeX(), p.steep() || fam == dev.awaken.city.VillageStyles.HIGHLAND, p.eave(), p.top(), p.lift(),
                        p.doorX(), p.doorZ(), p.use(), p.enter(), p.variant()));
            }
        }

        // ---- the market ------------------------------------------------------------------------------------------------

        /**
         * The market square's things: a fountain and the bell tower on the diagonals between the main streets (never across
         * them), the notice board by the fountain, benches, and stalls round the square's edges facing in.
         */
        @Override
        void civic() {
            int g = plan.greenR;
            VillagePlan.Street a = plan.streets.getFirst();
            double h = Math.atan2(a.z()[a.points() - 1] - a.z()[0], a.x()[a.points() - 1] - a.x()[0]);
            double d1 = h + Math.PI / 4, d2 = h + Math.PI * 5 / 4;
            int fx = (int) Math.round(Math.cos(d1) * 6.5), fz = (int) Math.round(Math.sin(d1) * 6.5);
            // the well the people and the troubles go to: a cell of the fountain's basin (its water)
            plan.well = new int[]{fx + 1, fz};
            plan.props.add(new dev.awaken.city.CityPlan.Prop("fountain", fx - 2, fz - 2, fx + 2, fz + 2, 0, "", 0, ""));
            mark(fx - 2, fz - 2, fx + 2, fz + 2);
            int bx = (int) Math.round(Math.cos(d2) * 7), bz = (int) Math.round(Math.sin(d2) * 7);
            int rx = bx + (int) Math.signum(-Math.cos(d2)) * 3, rz = bz;
            plan.bell = new VillagePlan.Bell(bx - 2, bz - 2, bx + 2, bz + 2, 0, true, bx, 15, bz, rx, rz);
            for (int z = bz - 2; z <= bz + 2; z++) for (int x = bx - 2; x <= bx + 2; x++) plan.set(x, z, dev.awaken.city.CityPlan.LANDMARK, 0);
            // the notice board beside the fountain, facing the street
            int side = Math.abs(Math.cos(h)) >= Math.abs(Math.sin(h)) ? (Math.sin(d1) > 0 ? 0 : 2) : (Math.cos(d1) > 0 ? 3 : 1);
            int nx = fx + (int) Math.round(Math.cos(h) * 4), nz = fz + (int) Math.round(Math.sin(h) * 4);
            if (plan.kind(nx, nz) != dev.awaken.city.CityPlan.SQUARE) {
                nx = fx - (int) Math.round(Math.cos(h) * 4);
                nz = fz - (int) Math.round(Math.sin(h) * 4);
            }
            plan.notice = new int[]{nx, nz, side};
            plan.props.add(new dev.awaken.city.CityPlan.Prop("notice", nx, nz, nx, nz, 0, "NESW".substring(side, side + 1), 0, ""));
            mark(nx, nz, nx, nz);
            java.util.List<Integer> bs = new java.util.ArrayList<>();
            int[][] around = {{fx, fz - 4, 2}, {fx, fz + 4, 0}, {fx - 4, fz, 1}, {fx + 4, fz, 3}};
            for (int[] c : around) {
                if (plan.kind(c[0], c[1]) != dev.awaken.city.CityPlan.SQUARE || taken(c[0], c[1]) || nearMain(c[0], c[1], 2.6)) continue;
                plan.props.add(new dev.awaken.city.CityPlan.Prop("bench", c[0], c[1], c[0], c[1], 0, "NESW".substring(c[2], c[2] + 1), 0, ""));
                mark(c[0], c[1], c[0], c[1]);
                bs.add(c[0]);
                bs.add(c[1]);
                bs.add(c[2]);
            }
            plan.benches = bs.stream().mapToInt(Integer::intValue).toArray();
            // stalls round the edge, facing in: up to six, never on the streets' way across
            int made = 0;
            for (int ring = g - 1; ring >= g - 6 && made < 6; ring--) {
                for (int q = 0; q < 32 && made < 6; q++) {
                    double ang = h + Math.PI / 16 + q * Math.PI / 16;
                    int sx = (int) Math.round(Math.cos(ang) * ring), sz = (int) Math.round(Math.sin(ang) * ring);
                    boolean alongX = Math.abs(Math.sin(ang)) > Math.abs(Math.cos(ang));
                    int x0 = alongX ? sx - 1 : sx, z0 = alongX ? sz : sz - 1, x1 = alongX ? sx + 2 : sx + 1, z1 = alongX ? sz + 1 : sz + 2;
                    boolean ok = true;
                    for (int z = z0 - 1; z <= z1 + 1 && ok; z++)
                        for (int x = x0 - 1; x <= x1 + 1 && ok; x++) {
                            boolean in = x >= x0 && x <= x1 && z >= z0 && z <= z1;
                            // the stall on the square clear of the main streets' way across it, a step round it free of things
                            ok = !taken(x, z) && (!in || plan.kind(x, z) == dev.awaken.city.CityPlan.SQUARE && !nearMain(x, z, 2.4));
                        }
                    if (!ok) continue;
                    // the counter toward the square's middle
                    String face = alongX ? (sz > 0 ? "N" : "S") : (sx > 0 ? "W" : "E");
                    plan.props.add(new dev.awaken.city.CityPlan.Prop("stall", x0, z0, x1, z1, 0, face, made + rnd.nextInt(3), ""));
                    mark(x0, z0, x1, z1);
                    made++;
                }
            }
        }

        private final it.unimi.dsi.fastutil.longs.LongOpenHashSet used = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();

        private void mark(int x0, int z0, int x1, int z1) {
            for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) used.add(((long) x << 32) ^ (z & 0xffffffffL));
        }

        private boolean taken(int x, int z) {
            return used.contains(((long) x << 32) ^ (z & 0xffffffffL));
        }

        /** Within {@code d} of a main street's line across the square. */
        private boolean nearMain(int x, int z, double d) {
            for (VillagePlan.Street st : plan.streets) {
                if (st.rank() != 0) continue;
                for (int k = 0; k + 1 < st.points(); k++) {
                    double ax = st.x()[k], az = st.z()[k], dx = st.x()[k + 1] - ax, dz = st.z()[k + 1] - az, l2 = dx * dx + dz * dz;
                    double tt = l2 == 0 ? 0 : Math.clamp(((x + 0.5 - ax) * dx + (z + 0.5 - az) * dz) / l2, 0, 1);
                    if (Math.hypot(ax + dx * tt - x - 0.5, az + dz * tt - z - 0.5) < d) return true;
                }
            }
            return false;
        }

        // ---- the people -------------------------------------------------------------------------------------------------

        @Override
        void moreJobs(java.util.List<String[]> jobs) {
            // the Watch: two at every gate; more hands for the market and the fields
            for (int k = 2; k < plan.gatePosts; k++) {
                jobs.add(new String[]{"watch", "post", String.valueOf(k)});
                jobs.add(new String[]{"watch", "post", String.valueOf(k)});
            }
            jobs.add(new String[]{"carter", "green", "4"});
            jobs.add(new String[]{"healer", "green", "5"});
            for (int f = 0; f < plan.fields.size(); f++) jobs.add(new String[]{"farmer", "field", String.valueOf(f)});
        }
    }
}
