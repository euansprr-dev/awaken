package dev.awaken.gates.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A world laid as places strung on links (SPEC §38.6): the programme's spine winds across the plot, each place joined
 * to the next through a one-slot link (a bone passage, a causeway over black water), and its branches hang off the
 * spine the same way. Found by a bounded, seeded search on the slot grid, so the same floor always lays the same way.
 * Used by the passages world (the Ossuary Ways) and the void world (the Field of Bells).
 */
final class ChainLayout {
    /** A link place: one slot between two places, from its side toward the earlier place to its side toward the later. */
    record Link(int w, int in, int out) {}

    /** A programme room's size in slots (built rooms keep theirs). */
    interface Sizer {
        int[] size(int programmeId);
    }

    static final int LO = 1, HI = 14;
    /** The fold's three link slots in a row along x (SPEC §38.12): the first and last slot column and the row; null if none. */
    int[] fold;

    final WorldPlan p;
    /** The slots places and links may take (an endless world keeps its outer band empty: 38.12). */
    int lo = LO, hi = HI;
    final long seed;
    final int variation;
    /** Link places by world room id. */
    final Map<Integer, Link> links = new HashMap<>();
    /** The spine's places in order (world room ids, links left out). */
    final List<Integer> spine = new ArrayList<>();
    /** Every place that isn't a link, in the order it was laid: the spine, then branches breadth first. */
    final List<Integer> places = new ArrayList<>();
    /** World room id → how many branches deep it hangs (0 on the spine). */
    final Map<Integer, Integer> depth = new HashMap<>();

    private final boolean[] occ = new boolean[256];
    private final List<int[]> rects = new ArrayList<>(), linkAt = new ArrayList<>();
    private int budget;
    private boolean strict;
    /** Laid as a hall's line: its Rig rooms keep to their two doors. */
    private boolean line;
    /** A hall too long for its row: its big places stand one slot along it, two deep. */
    private boolean narrow;

    ChainLayout(WorldPlan p, long seed, int variation) {
        this.p = p;
        this.seed = seed;
        this.variation = variation;
    }

    /** Lays the whole programme; false (with a reason in {@link WorldEngine#WHY}) when it can't. */
    boolean lay(Sizer sizer) {
        List<List<Integer>> groups = new ArrayList<>();
        for (int id : p.spine) groups.add(new ArrayList<>(List.of(id)));
        boolean ok = false;
        for (int attempt = 0; attempt < 4 && !ok; attempt++) {
            // a chain that won't fit folds: plain neighbours share a place (their monsters add up), from the far end
            if (attempt > 0 && !fold(groups)) break;
            for (int pass = 0; pass < 2 && !ok; pass++) {
                strict = pass == 0;
                // the roomy pass gives up early: a cramped chain is still a good chain
                budget = strict ? 1200 : 4000;
                java.util.Arrays.fill(occ, false);
                rects.clear();
                linkAt.clear();
                ok = step(0, groups, sizer);
            }
        }
        if (!ok) return WorldEngine.fail("the chain doesn't fit the plot") != null;
        int prev = -1;
        for (int k = 0; k < groups.size(); k++) {
            int[] r = rects.get(k);
            List<Integer> g = groups.get(k);
            int w = p.host(g.get(0), r[0], r[1], r[2], r[3], 0);
            for (int i = 1; i < g.size(); i++) p.merge(g.get(i), g.get(0));
            if (k > 0) {
                int[] l = linkAt.get(k - 1);
                int lw = p.extra(l[0], l[1], 1, 1, 0);
                if (!p.join(prev, lw) || !p.join(lw, w)) return WorldEngine.fail("a link doesn't touch its places") != null;
                links.put(lw, new Link(lw, l[2], l[3]));
            }
            spine.add(w);
            places.add(w);
            depth.put(w, 0);
            prev = w;
        }
        return branches(sizer);
    }

    /**
     * Lays the spine as one straight hall along slot row {@link #ROW} from west to east (SPEC §38.12, "Folds"): each place
     * on the row, a one-slot link between neighbours, and in the middle three links in a row, the fold. A long way is
     * squeezed until it fits (see below). Branches hang north and south as in a chain. False (with a reason) when it can't.
     */
    boolean layLine(Sizer sizer) {
        List<List<Integer>> groups = new ArrayList<>();
        for (int id : p.spine) groups.add(new ArrayList<>(List.of(id)));
        if (groups.size() < 2) return WorldEngine.fail("a fold needs two places on its way") != null;
        line = true;
        // too long for the row, each step in turn until it fits: neighbours share a place three at a time and open
        // straight into each other (no link between, from the ends in); the big places stand one slot along the hall and
        // two deep; neighbours share four or five at a time; a cache shares a place too
        int[] gaps = null;
        for (int[] st : new int[][]{{3, 0, 0}, {3, 1, 0}, {4, 1, 0}, {5, 1, 0}, {5, 1, 1}}) {
            narrow = st[1] > 0;
            while (width(groups, sizer) > hi - lo + 1 && fold(groups, st[0], st[2] > 0)) {
            }
            if ((gaps = gaps(groups, sizer)) != null) break;
        }
        if (gaps == null) return WorldEngine.fail("the hall doesn't fit the plot (" + span(groups, sizer, new int[groups.size() - 1]) + "+3 slots of "
                + (hi - lo + 1) + ", " + groups.size() + " places: " + groups + ")") != null;
        int x = lo + (hi - lo + 1 - span(groups, sizer, gaps)) / 2, mid = (groups.size() - 2) / 2;
        int prev = -1;
        for (int k = 0; k < groups.size(); k++) {
            List<Integer> g = groups.get(k);
            int[] sz = line(sizer.size(g.get(0)), g.get(0));
            if (k > 0) {
                // the links before this place: one, the fold's three, or none where the row is short
                int n = gaps[k - 1];
                if (n == 3) fold = new int[]{x, x + 2, ROW};
                for (int i = 0; i < n; i++) {
                    int lw = p.extra(x++, ROW, 1, 1, 0);
                    if (!p.join(prev, lw)) return WorldEngine.fail("a link doesn't touch its places") != null;
                    links.put(lw, new Link(lw, 3, 1));
                    prev = lw;
                }
            }
            int w = p.host(g.get(0), x, ROW, sz[0], sz[1], 0);
            for (int i = 1; i < g.size(); i++) p.merge(g.get(i), g.get(0));
            if (prev >= 0 && !p.join(prev, w)) return WorldEngine.fail("a link doesn't touch its places") != null;
            x += sz[0];
            spine.add(w);
            places.add(w);
            depth.put(w, 0);
            prev = w;
        }
        return branches(sizer);
    }

    /** The hall's row: places stand on it (two-slot ones reach a row south). */
    static final int ROW = 7;

    /** A place's size on the hall: as wide as it's long, at most two rows deep. */
    private int[] line(int[] sz, int id) {
        if (narrow && !WorldPlan.built(p.room(id))) return new int[]{1, Math.min(2, Math.max(sz[0], sz[1]))};
        if (sz[1] > sz[0] && !WorldPlan.built(p.room(id))) return new int[]{sz[1], sz[0]};
        return sz;
    }

    private int width(List<List<Integer>> groups, Sizer sizer) {
        int w = 0;
        for (List<Integer> g : groups) w += line(sizer.size(g.get(0)), g.get(0))[0];
        return w + groups.size() - 1 + 2;
    }

    /** The links in each gap of the hall: one, the fold's three in the middle gap, none from the ends in until it fits; null if it can't. */
    private int[] gaps(List<List<Integer>> groups, Sizer sizer) {
        int n = groups.size() - 1, mid = (groups.size() - 2) / 2;
        int[] gaps = new int[n];
        java.util.Arrays.fill(gaps, 1);
        gaps[mid] = 3;
        for (int i = 0, lo2 = 0, hi2 = n - 1; span(groups, sizer, gaps) > hi - lo + 1; i++) {
            if (lo2 > hi2) return null;
            int at = i % 2 == 0 ? lo2++ : hi2--;
            if (at != mid) gaps[at] = 0;
        }
        return gaps;
    }

    private int span(List<List<Integer>> groups, Sizer sizer, int[] gaps) {
        int w = 0;
        for (List<Integer> g : groups) w += line(sizer.size(g.get(0)), g.get(0))[0];
        for (int n : gaps) w += n;
        return w;
    }

    private boolean fold(List<List<Integer>> groups) {
        return fold(groups, 3, false);
    }

    private boolean fold(List<List<Integer>> groups, int cap, boolean loose) {
        for (int i = groups.size() - 2; i >= 1; i--) {
            if (plain(groups.get(i), loose) && plain(groups.get(i + 1), loose) && groups.get(i).size() + groups.get(i + 1).size() <= cap) {
                List<Integer> g = groups.get(i);
                g.addAll(groups.remove(i + 1));
                // a cache never hosts: the place is the fight's, the cache's chest stands in it
                if (loose) g.sort((a, b) -> Boolean.compare(p.room(a).type == Room.Type.CACHE, p.room(b).type == Room.Type.CACHE));
                return true;
            }
        }
        return false;
    }

    private boolean plain(List<Integer> g, boolean loose) {
        for (int id : g) {
            Room L = p.room(id);
            if (WorldPlan.built(L) || id == p.logic.start || id == p.logic.end) return false;
            if (loose && L.type == Room.Type.CACHE) continue;
            if (L.type != Room.Type.NORMAL && L.type != Room.Type.ELITE && L.type != Room.Type.HALL && L.type != Room.Type.SHAFT) return false;
        }
        return true;
    }

    // ---- the spine: a depth-first search over link and place positions ----------------------------------------------

    private boolean step(int k, List<List<Integer>> groups, Sizer sizer) {
        if (k == groups.size()) return true;
        if (--budget < 0) return false;
        int[] sz = sizer.size(groups.get(k).get(0));
        boolean turnable = sz[0] != sz[1] && !WorldPlan.built(p.room(groups.get(k).get(0)));
        if (k == 0) {
            // the arrival stands near an edge of the plot, so the way has the whole plot to wind across
            List<int[]> starts = new ArrayList<>();
            for (int gx = lo; gx + sz[0] - 1 <= hi; gx++) {
                for (int gz = lo; gz + sz[1] - 1 <= hi; gz++) {
                    int edge = Math.min(Math.min(gx - lo, hi - gx - sz[0] + 1), Math.min(gz - lo, hi - gz - sz[1] + 1));
                    if (edge > 1) continue;
                    starts.add(new int[]{gx, gz, (int) (Hash.of(seed, gx * 31 + gz, 61 + variation) & 0xffff)});
                }
            }
            starts.sort((a, b) -> Integer.compare(a[2], b[2]));
            for (int i = 0; i < Math.min(8, starts.size()); i++) {
                int[] s = starts.get(i);
                if (!fits(s[0], s[1], sz[0], sz[1], -1, -1)) continue;
                mark(s[0], s[1], sz[0], sz[1], true);
                rects.add(new int[]{s[0], s[1], sz[0], sz[1]});
                if (step(1, groups, sizer)) return true;
                rects.remove(rects.size() - 1);
                mark(s[0], s[1], sz[0], sz[1], false);
            }
            return false;
        }
        int[] R = rects.get(k - 1);
        // a built room (a Rig room) is crossed straight: out on the far side, in line with the way in
        int[] through = null;
        if (k >= 2 && WorldPlan.built(p.room(groups.get(k - 1).get(0)))) {
            int[] in = linkAt.get(k - 2);
            int d = in[3], depth = d == 0 || d == 2 ? R[3] : R[2];
            through = new int[]{d, in[0] + GatePainter.DX[d] * (depth + 1), in[1] + GatePainter.DZ[d] * (depth + 1)};
        }
        for (int d : directions(R, k)) {
            if (through != null && d != through[0]) continue;
            List<int[]> along = through != null ? List.of(new int[]{through[1], through[2]}) : edge(R, d);
            for (int[] l : along) {
                int lx = l[0], lz = l[1];
                if (!inside(lx, lz) || occ[lx + lz * 16]) continue;
                for (int t = 0; t < 3; t++) {
                    int d2 = turn(d, k, t);
                    int bx = lx + GatePainter.DX[d2], bz = lz + GatePainter.DZ[d2];
                    for (int o = 0; o < 2; o++) {
                        int gw = o == 0 ? sz[0] : sz[1], gh = o == 0 ? sz[1] : sz[0];
                        if (o == 1 && !turnable) continue;
                        int span = d2 == 0 || d2 == 2 ? gw : gh;
                        for (int a = 0; a < span; a++) {
                            int off = (int) Math.floorMod(a + Hash.of(seed, k, 67 + variation), (long) span);
                            int gx, gz;
                            switch (d2) {
                                case 0 -> { gx = bx - off; gz = bz - gh + 1; }
                                case 2 -> { gx = bx - off; gz = bz; }
                                case 1 -> { gx = bx; gz = bz - off; }
                                default -> { gx = bx - gw + 1; gz = bz - off; }
                            }
                            occ[lx + lz * 16] = true;
                            if (fits(gx, gz, gw, gh, lx, lz)) {
                                mark(gx, gz, gw, gh, true);
                                rects.add(new int[]{gx, gz, gw, gh});
                                linkAt.add(new int[]{lx, lz, (d + 2) % 4, d2});
                                if (step(k + 1, groups, sizer)) return true;
                                rects.remove(rects.size() - 1);
                                linkAt.remove(linkAt.size() - 1);
                                mark(gx, gz, gw, gh, false);
                            }
                            occ[lx + lz * 16] = false;
                            if (budget < 0) return false;
                        }
                    }
                }
            }
        }
        return false;
    }

    /** The four ways on from a place, most open first (a seeded tie-break). */
    private int[] directions(int[] R, int k) {
        double[] score = new double[4];
        Integer[] ord = {0, 1, 2, 3};
        for (int d = 0; d < 4; d++) {
            int n = 0;
            double cx = R[0] + R[2] / 2.0, cz = R[1] + R[3] / 2.0;
            for (int s = 1; s <= 4; s++) {
                for (int w = -1; w <= 1; w++) {
                    int x = (int) Math.floor(cx + GatePainter.DX[d] * (R[2] / 2.0 + s) + (d % 2 == 0 ? w : 0));
                    int z = (int) Math.floor(cz + GatePainter.DZ[d] * (R[3] / 2.0 + s) + (d % 2 == 1 ? w : 0));
                    if (inside(x, z) && !occ[x + z * 16]) n++;
                }
            }
            score[d] = n + Hash.unit(seed, k * 4 + d, 71 + variation) * 4;
        }
        java.util.Arrays.sort(ord, (a, b) -> Double.compare(score[b], score[a]));
        return new int[]{ord[0], ord[1], ord[2], ord[3]};
    }

    /** The way a link turns: straight on, left or right, in a seeded order that likes turning. */
    private int turn(int d, int k, int t) {
        int first = (int) Math.floorMod(Hash.of(seed, k, 73 + variation), 3L);
        int which = (first + t) % 3;
        return which == 0 ? (d + 1) % 4 : which == 1 ? (d + 3) % 4 : d;
    }

    /** The slots just outside a place's side d, middle first. */
    private static List<int[]> edge(int[] R, int d) {
        List<int[]> out = new ArrayList<>();
        int n = d == 0 || d == 2 ? R[2] : R[3];
        for (int i = 0; i < n; i++) {
            int j = (n - 1) / 2 + ((i & 1) == 0 ? i / 2 : -(i + 1) / 2);
            if (j < 0 || j >= n) continue;
            int x, z;
            switch (d) {
                case 0 -> { x = R[0] + j; z = R[1] - 1; }
                case 2 -> { x = R[0] + j; z = R[1] + R[3]; }
                case 1 -> { x = R[0] + R[2]; z = R[1] + j; }
                default -> { x = R[0] - 1; z = R[1] + j; }
            }
            out.add(new int[]{x, z});
        }
        return out;
    }

    private boolean inside(int x, int z) {
        return x >= lo && x <= hi && z >= lo && z <= hi;
    }

    /**
     * Whether a place fits free; on the strict pass it mustn't touch anything else but its own link (lx, lz), so every
     * place keeps rock round it and the way between places is always a link.
     */
    private boolean fits(int gx, int gz, int gw, int gh, int lx, int lz) {
        if (gx < lo || gz < lo || gx + gw - 1 > hi || gz + gh - 1 > hi) return false;
        for (int x = gx; x < gx + gw; x++) for (int z = gz; z < gz + gh; z++) if (occ[x + z * 16]) return false;
        if (!strict) return true;
        for (int x = gx - 1; x <= gx + gw; x++) {
            for (int z = gz - 1; z <= gz + gh; z++) {
                boolean ring = x == gx - 1 || x == gx + gw || z == gz - 1 || z == gz + gh;
                boolean corner = (x == gx - 1 || x == gx + gw) && (z == gz - 1 || z == gz + gh);
                if (!ring || corner || (x == lx && z == lz) || x < 0 || z < 0 || x > 15 || z > 15) continue;
                if (occ[x + z * 16]) return false;
            }
        }
        return true;
    }

    private void mark(int gx, int gz, int gw, int gh, boolean v) {
        for (int x = gx; x < gx + gw; x++) for (int z = gz; z < gz + gh; z++) occ[x + z * 16] = v;
    }

    // ---- branches: each hangs off its parent through a link of its own (or opens straight off it) ---------------------

    private boolean branches(Sizer sizer) {
        java.util.Arrays.fill(occ, false);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) occ[x + y * 16] = !p.free(x, y, 1, 1);
        ArrayDeque<Integer> q = new ArrayDeque<>(p.spine);
        List<Integer> order = new ArrayList<>();
        while (!q.isEmpty()) {
            int id = q.poll();
            for (int ch : p.branches(id)) {
                order.add(ch);
                q.add(ch);
            }
        }
        for (int id : order) {
            int parentW = p.host[p.parent[id]];
            // on a hall's line a Rig room's side door would open into its trial (a well): its branches hang off another place
            if ((line && rigHost(parentW)) || !branch(id, parentW, sizer)) {
                // its parent is boxed in: it opens off the nearest place that has room (it only has to be reachable)
                Room par = p.world(parentW);
                List<Integer> near = new ArrayList<>(places);
                near.sort((a, b) -> Double.compare(dist(p.world(a), par), dist(p.world(b), par)));
                boolean done = false;
                for (int o : near) {
                    if (o == parentW || p.sealable(p.world(o)) || (line && rigHost(o))) continue;
                    if (branch(id, o, sizer)) {
                        done = true;
                        break;
                    }
                }
                if (!done) return WorldEngine.fail("no room for a side place") != null;
            }
        }
        return true;
    }

    private boolean rigHost(int worldRoom) {
        for (int i = 0; i < p.host.length; i++) if (p.host[i] == worldRoom && WorldPlan.built(p.room(i))) return true;
        return false;
    }

    private boolean branch(int id, int parentW, Sizer sizer) {
        Room par = p.world(parentW);
        int[] R = {par.gx, par.gz, par.gw, par.gh};
        int[] sz = sizer.size(id);
        boolean built = WorldPlan.built(p.room(id));
        for (int pass = 0; pass < 3; pass++) {
            strict = pass == 0;
            for (int d : directions(R, id + 100)) {
                for (int[] l : edge(R, d)) {
                    int lx = l[0], lz = l[1];
                    if (!inside(lx, lz) || occ[lx + lz * 16]) continue;
                    if (pass == 2) {
                        // no room for a link: the place opens straight off its parent
                        for (int o = 0; o < (d == 0 || d == 2 ? sz[0] : sz[1]) && !built; o++) {
                            int gx = d == 3 ? lx - sz[0] + 1 : d == 1 ? lx : lx - o, gz = d == 0 ? lz - sz[1] + 1 : d == 2 ? lz : lz - o;
                            if (!fits(gx, gz, sz[0], sz[1], -1, -1)) continue;
                            return commit(id, parentW, gx, gz, sz, null);
                        }
                        continue;
                    }
                    for (int t = 0; t < 3; t++) {
                        int d2 = turn(d, id + 100, t);
                        int bx = lx + GatePainter.DX[d2], bz = lz + GatePainter.DZ[d2];
                        int span = d2 == 0 || d2 == 2 ? sz[0] : sz[1];
                        for (int off = 0; off < span; off++) {
                            int gx, gz;
                            switch (d2) {
                                case 0 -> { gx = bx - off; gz = bz - sz[1] + 1; }
                                case 2 -> { gx = bx - off; gz = bz; }
                                case 1 -> { gx = bx; gz = bz - off; }
                                default -> { gx = bx - sz[0] + 1; gz = bz - off; }
                            }
                            occ[lx + lz * 16] = true;
                            boolean ok = fits(gx, gz, sz[0], sz[1], lx, lz);
                            occ[lx + lz * 16] = false;
                            if (ok) return commit(id, parentW, gx, gz, sz, new int[]{lx, lz, (d + 2) % 4, d2});
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean commit(int id, int parentW, int gx, int gz, int[] sz, int[] link) {
        int w = p.host(id, gx, gz, sz[0], sz[1], 0);
        mark(gx, gz, sz[0], sz[1], true);
        if (link != null) {
            int lw = p.extra(link[0], link[1], 1, 1, 0);
            occ[link[0] + link[1] * 16] = true;
            if (!p.join(parentW, lw) || !p.join(lw, w)) return false;
            links.put(lw, new Link(lw, link[2], link[3]));
        } else if (!p.join(parentW, w)) return false;
        places.add(w);
        depth.put(w, depth.getOrDefault(parentW, 0) + 1);
        return true;
    }

    /**
     * A link's way across its slot from one doorway to the other: a gentle S when the doorways face each other, else a
     * quarter turn round the corner between them, its width wavering by the link's hash.
     */
    record Way(int ox, int oz, boolean straight, boolean alongZ, double amp, int ccx, int ccz) {
        static Way of(Room r, Link l, long seed) {
            int ox = r.gx * 16, oz = r.gz * 16;
            double amp = (Hash.unit(seed, r.id, 89) < 0.5 ? -1 : 1) * (1.5 + Hash.unit(seed, r.id, 97) * 1.5);
            int ccx = l.in() == 1 || l.out() == 1 ? ox + 16 : ox, ccz = l.in() == 0 || l.out() == 0 ? oz : oz + 16;
            return new Way(ox, oz, (l.in() + 2) % 4 == l.out(), l.in() == 0 || l.in() == 2, amp, ccx, ccz);
        }

        /** At a plan point: signed offset from the centreline, distance along it, and the way out from it (x, z). */
        double[] at(double x, double z) {
            if (x < ox || x > ox + 16 || z < oz || z > oz + 16) return null;
            if (straight) {
                double t = ((alongZ ? z : x) - (alongZ ? oz : ox)) / 16.0;
                double lat = (alongZ ? x : z) - ((alongZ ? ox : oz) + 8 + amp * Math.sin(Math.PI * t));
                return new double[]{lat, t * 16, alongZ ? Math.signum(lat) : 0, alongZ ? 0 : Math.signum(lat)};
            }
            double dx = x - ccx, dz = z - ccz, rr = Math.max(Math.hypot(dx, dz), 1e-6);
            double th = Math.atan2(Math.abs(dz), Math.abs(dx));
            double lat = rr - (8 + amp * 0.6 * Math.sin(2 * th));
            return new double[]{lat, th * rr, Math.signum(lat) * dx / rr, Math.signum(lat) * dz / rr};
        }
    }

    static double dist(Room a, Room b) {
        return Math.hypot(a.gx + a.gw / 2.0 - b.gx - b.gw / 2.0, a.gz + a.gh / 2.0 - b.gz - b.gh / 2.0);
    }
}
