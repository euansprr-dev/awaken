package dev.awaken.village.plan;

import org.jspecify.annotations.Nullable;

import java.util.function.BiPredicate;

/**
 * A wayhouse on a crossroads (SPEC §28.9, PLAN 9): an inn with a stable where a region's roads meet, about a day's walk
 * from the next. Its site near the region's heart, then the plan.
 */
public final class WayhousePlanner {
    private WayhousePlanner() {}

    static final int SEARCH = 96;

    /** A small, flat, dry patch near the heart: {x, z} or null. */
    public static int @Nullable [] site(Terrain t, int hx, int hz, BiPredicate<Integer, Integer> reserved) {
        it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap memo = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
        memo.defaultReturnValue(Long.MIN_VALUE);
        double best = Double.NEGATIVE_INFINITY;
        int bx = 0, bz = 0;
        for (int dz = -SEARCH; dz <= SEARCH; dz += 24) {
            for (int dx = -SEARCH; dx <= SEARCH; dx += 24) {
                int x = Math.floorDiv(hx + dx, 12) * 12, z = Math.floorDiv(hz + dz, 12) * 12;
                int n = 0, lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
                boolean bad = false;
                for (int ez = -24; ez <= 24 && !bad; ez += 12) {
                    for (int ex = -24; ex <= 24 && !bad; ex += 12) {
                        if (reserved.test(x + ex, z + ez)) bad = true;
                        long k = ((long) (x + ex) << 32) ^ ((z + ez) & 0xffffffffL);
                        long v = memo.get(k);
                        if (v == Long.MIN_VALUE) {
                            v = t.both(x + ex, z + ez);
                            memo.put(k, v);
                        }
                        if ((int) v > 0) bad = true;
                        int h = (int) (v >> 32);
                        lo = Math.min(lo, h);
                        hi = Math.max(hi, h);
                        n++;
                    }
                }
                if (bad || hi - lo > 6 || lo < t.seaLevel() + 1) continue;
                double s = -(hi - lo) * 2.0 - Math.hypot(dx, dz) * 0.04;
                if (s > best) {
                    best = s;
                    bx = x;
                    bz = z;
                }
            }
        }
        return best == Double.NEGATIVE_INFINITY ? null : new int[]{bx, bz};
    }

    /**
     * The wayhouse: a small square where the roads cross (four ways out, one to each region round it), the inn facing it
     * (two storeys, a taproom, guests' beds above), the stable across from it with its paddock of horses, a well and a
     * trough, a bench, the notice board, lanterns. Its people are the innkeeper's household and the hand who keeps the
     * stable. A bed for the night, a meal, the road's rumours.
     */
    static class WayWork extends VillagePlanner.Work {
        WayWork(VillagePlanner.Request q, Terrain t) {
            super(q, t);
        }

        @Override
        @Nullable VillagePlan run() {
            VillagePlanner.WHY.set("");
            cx = q.anchorX();
            cz = q.anchorZ();
            int room = Math.min(Math.min(cx - q.minX(), q.maxX() - cx), Math.min(cz - q.minZ(), q.maxZ() - cz)) - 2;
            r = Math.min(46, room);
            if (r < 40) {
                VillagePlanner.WHY.set("no room in the structure's reach");
                return null;
            }
            sample();
            int fam = family();
            plan = new VillagePlan(q.key(), Names.inn(q.seed()), VillagePlan.Kind.WAYHOUSE, fam, dev.awaken.city.VillageStyles.MODEST, q.seed(), cx, cz, baseY, r);
            streetDist = new float[plan.n * plan.n];
            java.util.Arrays.fill(streetDist, Float.MAX_VALUE);
            ground();
            crossroads();
            if (plan.streets.size() < 2) {
                VillagePlanner.WHY.set("fewer than two ways out of the crossroads");
                return null;
            }
            keepExits();
            buildings();
            if (plan.buildings.stream().noneMatch(b -> b.use().equals("inn"))) {
                VillagePlanner.WHY.set("no room for the inn");
                return null;
            }
            paddock();
            trees();
            lamps();
            props();
            people();
            plan.index();
            return plan;
        }

        /** The square where the ways cross, and the four ways out. */
        void crossroads() {
            int g = 4;
            plan.greenR = g;
            for (int z = -g - 1; z <= g + 1; z++)
                for (int x = -g - 1; x <= g + 1; x++) {
                    double e = Math.pow(Math.abs(x) / (g + 0.5), 4) + Math.pow(Math.abs(z) / (g + 0.5), 4);
                    if (e <= 1 && dry(x, z)) plan.set(x, z, dev.awaken.city.CityPlan.SQUARE, 0);
                }
            float gxh = height(10, 0) - height(-10, 0), gzh = height(0, 10) - height(0, -10);
            double h0 = Math.hypot(gxh, gzh) < 1.5 ? rnd.nextDouble() * Math.PI : Math.atan2(gzh, gxh) + Math.PI / 2;
            for (int k = 0; k < 4; k++) {
                java.util.List<float[]> way = walk(0, 0, h0 + k * Math.PI / 2, 30, 1.2);
                if (way.size() < 4) continue;
                addStreet(0, 4.2f, way, 0);
                int s = plan.streets.size() - 1;
                VillagePlan.Street st = plan.streets.get(s);
                plan.exitList.add(new int[]{s, 1});
                plan.posts.add(new int[]{Math.round(st.x()[st.points() - 1]), Math.round(st.z()[st.points() - 1]), st.lift()[st.points() - 1]});
            }
            plan.gatePosts = plan.posts.size();
        }

        @Override
        void buildings() {
            java.util.List<int[]> slots = new java.util.ArrayList<>();
            for (int s = 0; s < plan.streets.size(); s++) {
                VillagePlan.Street st = plan.streets.get(s);
                for (int k = 1; k < st.points(); k++) for (int side = -1; side <= 1; side += 2) slots.add(new int[]{s, k, side});
            }
            slots.sort((u, v) -> Double.compare(slotDist(u), slotDist(v)));
            place(new Want("inn", 11 + rnd.nextInt(2) * 2, 9, 2, 2), slots);
            place(new Want("barn", 9, 7, 1, 2), slots);
        }

        /** The stable's paddock: a pen of horses beside it, its gate toward the square. */
        void paddock() {
            for (VillagePlan.Building b : plan.buildings) {
                if (!b.use().equals("barn")) continue;
                dev.awaken.city.CityPlan.Plot p = plan.plots.get(b.plot());
                for (int turn : new int[]{1, 3, 2}) {
                    int sd = (p.front() + turn) & 3;
                    int x0, z0, x1, z1;
                    switch (sd) {
                        case 0 -> { x0 = p.x0(); x1 = p.x0() + 6; z1 = p.z0() - 2; z0 = z1 - 5; }
                        case 2 -> { x0 = p.x0(); x1 = p.x0() + 6; z0 = p.z1() + 2; z1 = z0 + 5; }
                        case 1 -> { z0 = p.z0(); z1 = p.z0() + 6; x0 = p.x1() + 2; x1 = x0 + 5; }
                        default -> { z0 = p.z0(); z1 = p.z0() + 6; x1 = p.x0() - 2; x0 = x1 - 5; }
                    }
                    if (!rect(x0, z0, x1, z1, 1, 3)) continue;
                    int lift = p.lift();
                    for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) plan.set(x, z, VillagePlan.PEN, lift);
                    int gx = (x0 + x1) / 2, gz = (z0 + z1) / 2, side;
                    float ddx = -(x0 + x1) / 2f, ddz = -(z0 + z1) / 2f;
                    if (Math.abs(ddx) > Math.abs(ddz)) {
                        side = ddx > 0 ? 1 : 3;
                        gx = ddx > 0 ? x1 : x0;
                    } else {
                        side = ddz > 0 ? 2 : 0;
                        gz = ddz > 0 ? z1 : z0;
                    }
                    plan.pens.add(new VillagePlan.Pen(x0, z0, x1, z1, lift, "horse", gx, gz, side));
                    return;
                }
            }
        }

        @Override
        int maxLamps() {
            return 6;
        }

        @Override
        int[] peopleBounds() {
            return new int[]{4, 9};
        }

        /** The square's things: the well and a trough in a corner, the notice board, a bench by the inn's door. */
        @Override
        void civic() {
            int g = plan.greenR;
            int[][] corners = {{g - 1, g - 1}, {-g + 1, g - 1}, {g - 1, -g + 1}, {-g + 1, -g + 1}};
            int[] w = null;
            for (int[] c : corners) {
                boolean ok = true;
                for (int z = c[1] - 1; z <= c[1] + 1 && ok; z++) for (int x = c[0] - 1; x <= c[0] + 1 && ok; x++) ok = onSquareClear(x, z);
                if (ok) {
                    w = c;
                    break;
                }
            }
            if (w == null) w = new int[]{g - 1, g - 1};
            plan.well = new int[]{w[0], w[1]};
            plan.props.add(new dev.awaken.city.CityPlan.Prop("well", w[0] - 1, w[1] - 1, w[0] + 1, w[1] + 1, 0, "", rnd.nextInt(4), ""));
            int nx = -w[0], nz = w[1];
            int side = nx > 0 ? 3 : 1;
            plan.notice = new int[]{nx, nz, side};
            plan.props.add(new dev.awaken.city.CityPlan.Prop("notice", nx, nz, nx, nz, 0, "NESW".substring(side, side + 1), 0, ""));
            plan.benches = new int[0];
        }

        /** A square cell off the ways' lines through it. */
        private boolean onSquareClear(int x, int z) {
            if (plan.kind(x, z) != dev.awaken.city.CityPlan.SQUARE) return false;
            for (VillagePlan.Street st : plan.streets) {
                for (int k = 0; k + 1 < Math.min(4, st.points()); k++) {
                    double ax = st.x()[k], az = st.z()[k], dx = st.x()[k + 1] - ax, dz = st.z()[k + 1] - az, l2 = dx * dx + dz * dz;
                    double tt = l2 == 0 ? 0 : Math.clamp(((x + 0.5 - ax) * dx + (z + 0.5 - az) * dz) / l2, 0, 1);
                    if (Math.hypot(ax + dx * tt - x - 0.5, az + dz * tt - z - 0.5) < 2.4) return false;
                }
            }
            return true;
        }
    }
}
