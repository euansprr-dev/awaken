package dev.awaken.gates.world;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The ground of a cave world's places (SPEC §38.14): a height field in half blocks that rises in terraces toward the walls,
 * rolls and mounds in the open, and sinks in hollows, so a place reads as somewhere you're standing in, not a room with a
 * flat floor. The way from each doorway to the middle stays flat and the floor where you fight stays gentle; the edges
 * are bold (up to four blocks). Doorways, the middle, anchors, lakes and everything the dress set down (touchstones,
 * nooks, hero pieces) stand on flat pads.
 * <p>
 * Solved once at plan time after the dress (so it knows where the dress put things), applied by one column operation per
 * place at the end of the carve: full blocks under a raised floor (the theme's bank, its top skinned as floor), a slab or
 * a stair on a half step, a hollow opened with a shell under it. The plan's ground grid says where you stand over every
 * column, so spawns, features and the reach checks read the same ground the carve makes.
 */
final class Relief {
    /** Most a place's ground rises and sinks, in half blocks. */
    static final int RISE = 8, SINK = 4;
    /** The theme's ground pieces ({@link #ROOF}: a stair hung upside down where the roof steps). */
    static final int BANK = 0, SLAB = 1, STAIRS = 2, DRIFT = 3, BITS = 4, ROOF = 5;

    final CaveEngine.Cave c;
    final int W;
    final long seed;
    /** The ground over every column of the plot, half blocks above the floor line. */
    final byte[] g = new byte[Plots.SIDE * Plots.SIDE];
    /**
     * How wild each column is, in hundredths: 0 on the way you walk, 100 six blocks off it (a fought middle stays tame).
     * Bits, banked drifts, the theme's creep and what grows on the floor thicken with it.
     */
    final byte[] wild = new byte[Plots.SIDE * Plots.SIDE];
    /** How far in from its place's wall each column is, in quarter blocks up to 8 blocks (0 off the places). */
    final byte[] in = new byte[Plots.SIDE * Plots.SIDE];
    /** Columns that stay as the dress left them: doorways, pads, lakes (nothing banks or creeps there). */
    final java.util.BitSet pinned = new java.util.BitSet(Plots.SIDE * Plots.SIDE);
    /** The highest ground or piece standing within 3 of each column, whole blocks (what a wall rises from: {@link CaveEngine.Cave#wall}). */
    final byte[] near = new byte[Plots.SIDE * Plots.SIDE];

    Relief(CaveEngine.Cave c) {
        this.c = c;
        this.W = c.W;
        this.seed = c.seed ^ 0x5E11EFL;
    }

    /** The ground at floor-local (x, z) in half blocks (0 off the plot or where nothing's shaped). */
    int half(int x, int z) {
        if (x < 0 || z < 0 || x >= Plots.SIDE || z >= Plots.SIDE) return 0;
        return g[x + z * Plots.SIDE];
    }

    /** Where you stand at (x, z), whole blocks above the floor line: the block your feet are in (over a slab, the next). */
    int stand(int x, int z) {
        return (half(x, z) + 1) >> 1;
    }

    /** How wild (x, z) is, 0 to 1. */
    double wild(int x, int z) {
        if (x < 0 || z < 0 || x >= Plots.SIDE || z >= Plots.SIDE) return 0;
        return wild[x + z * Plots.SIDE] / 100.0;
    }

    /** How far in from the wall (x, z) is, blocks (0 off a place, at most 8). */
    double inside(int x, int z) {
        if (x < 0 || z < 0 || x >= Plots.SIDE || z >= Plots.SIDE) return 0;
        return (in[x + z * Plots.SIDE] & 0xff) / 4.0;
    }

    /** Whether a place is shaped in full (the caverns, the arrival, the end and the secret place; not a tunnel or a built room). */
    static boolean shaped(CaveEngine.Kind k) {
        return k != null && k != CaveEngine.Kind.LINK && k != CaveEngine.Kind.BUILT;
    }

    /**
     * A tunnel is the way between places: its floor swells slowly along it, up to a block, half a step at a time (a slab
     * between), flat for a few blocks at each doorway.
     */
    void solveLink(Room r) {
        ChainLayout.Way way = ChainLayout.Way.of(r, c.chain.links.get(r.id), c.seed);
        int[] b = Dress.box(r);
        int w = b[2] - b[0] + 1, h = b[3] - b[1] + 1;
        boolean[] in = new boolean[w * h];
        int[] v = new int[w * h];
        for (int lz = 0; lz < h; lz++) {
            for (int lx = 0; lx < w; lx++) {
                int x = b[0] + lx, z = b[1] + lz, i = lx + lz * w;
                double px = x + 0.5, pz = z + 0.5;
                double[] at = way.at(px, pz);
                if (at == null || Math.abs(at[0]) > c.hw + 0.5) continue;
                in[i] = true;
                int k = x + z * Plots.SIDE;
                double lat = Math.abs(at[0]);
                wild[k] = (byte) Math.round(60 * clamp((lat - c.hw + 1.2) / 1.2, 0, 1));
                this.in[k] = (byte) Math.round(4 * clamp(c.hw + 0.5 - lat, 0, 8));
                boolean door = false;
                for (Room.Door dr : r.doors) door |= Math.hypot(px - dr.centerX() - 0.5, pz - dr.centerZ() - 0.5) < 2.5;
                if (door) {
                    pinned.set(k);
                    continue;
                }
                double swell = (Hash.noise(seed + 31, px, pz, 13) - 0.26) * 7 + (Hash.noise(seed + 25, px, pz, 8) - 0.5) * 1.5;
                v[i] = (int) Math.round(clamp(swell, 0, 2));
            }
        }
        // no step over half a block: each rise is lowered toward the doorways' level until none is
        for (int pass = 0; pass < 8; pass++) {
            boolean changed = false;
            for (int lz = 0; lz < h; lz++) {
                for (int lx = 0; lx < w; lx++) {
                    int i = lx + lz * w;
                    if (v[i] == 0) continue;
                    int u = v[i];
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = lx + dx, nz = lz + dz;
                            if ((dx | dz) == 0 || nx < 0 || nz < 0 || nx >= w || nz >= h || !in[nx + nz * w]) continue;
                            u = Math.min(u, v[nx + nz * w] + 1);
                        }
                    }
                    if (u != v[i]) {
                        v[i] = u;
                        changed = true;
                    }
                }
            }
            if (!changed) break;
        }
        for (int lz = 0; lz < h; lz++) {
            for (int lx = 0; lx < w; lx++) {
                int i = lx + lz * w;
                if (!in[i]) continue;
                g[b[0] + lx + (b[1] + lz) * Plots.SIDE] = (byte) v[i];
            }
        }
    }

    // ---- solving (plan time, after the dress) ---------------------------------------------------------------------------

    /** Solves every shaped place, fills the plan's ground grid and adds the carve's column operations. */
    void apply() {
        byte[] stand = new byte[Plots.SIDE * Plots.SIDE];
        for (var e : c.kinds.entrySet()) {
            Room r = c.p.world(e.getKey());
            if (e.getValue() == CaveEngine.Kind.LINK) solveLink(r);
            else if (!shaped(e.getValue()) || c.shapes.get(r.id) == null) continue;
            else solve(r, e.getValue());
            int[] b = Dress.box(r);
            for (int z = b[1]; z <= b[3]; z++) for (int x = b[0]; x <= b[2]; x++) stand[x + z * Plots.SIDE] = (byte) stand(x, z);
            c.s.op(b[0], W - 6, b[1], b[2], W - 6, b[3], (buf, x, y, z) -> column(buf, x, z));
            if (e.getValue() != CaveEngine.Kind.LINK) c.s.op(b[0], W - 5, b[1], b[2], W - 5, b[3], (buf, x, y, z) -> soften(buf, x, z));
        }
        c.p.ground = stand;
        // the highest ground within 3, rows then columns
        byte[] rows = new byte[stand.length];
        for (int z = 0; z < Plots.SIDE; z++)
            for (int x = 0; x < Plots.SIDE; x++) {
                int m = 0;
                for (int d = -3; d <= 3; d++) if (x + d >= 0 && x + d < Plots.SIDE) m = Math.max(m, stand[x + d + z * Plots.SIDE]);
                rows[x + z * Plots.SIDE] = (byte) m;
            }
        for (int z = 0; z < Plots.SIDE; z++)
            for (int x = 0; x < Plots.SIDE; x++) {
                int m = 0;
                for (int d = -3; d <= 3; d++) if (z + d >= 0 && z + d < Plots.SIDE) m = Math.max(m, rows[x + (z + d) * Plots.SIDE]);
                near[x + z * Plots.SIDE] = (byte) m;
            }
    }

    /** Something stands to {@code top} (blocks over the floor line) within r of (cx, cz): the walls near it rise from there. */
    void lift(double cx, double cz, double r, double top) {
        int t = (int) Math.ceil(top), x0 = (int) Math.floor(cx - r - 3), x1 = (int) Math.floor(cx + r + 3);
        int z0 = (int) Math.floor(cz - r - 3), z1 = (int) Math.floor(cz + r + 3);
        for (int z = Math.max(0, z0); z <= Math.min(Plots.SIDE - 1, z1); z++)
            for (int x = Math.max(0, x0); x <= Math.min(Plots.SIDE - 1, x1); x++)
                if (near[x + z * Plots.SIDE] < t) near[x + z * Plots.SIDE] = (byte) Math.min(127, t);
    }

    /** The highest ground (or piece standing, {@link #lift}) within 3 of (x, z), whole blocks over the floor line (0 off the plot). */
    int near(int x, int z) {
        if (x < 0 || z < 0 || x >= Plots.SIDE || z >= Plots.SIDE) return 0;
        return near[x + z * Plots.SIDE];
    }

    void solve(Room r, CaveEngine.Kind kind) {
        int id = r.id;
        PassageEngine.Shape sh = c.shapes.get(id);
        int[] b = Dress.box(r);
        int w = b[2] - b[0] + 1, h = b[3] - b[1] + 1;
        boolean[] in = new boolean[w * h], pin = new boolean[w * h];
        byte[] s = new byte[w * h];
        int[] v = new int[w * h];
        boolean fight = fight(r);
        boolean lake = c.inLakeAny(id);
        double pad = c.dress.pad(r, kind);
        // the way: from each doorway to the middle, and on to the place's anchor
        List<double[]> way = new ArrayList<>();
        int[] anchor = c.p.anchorOf(r);
        for (Room.Door dr : r.doors) way.add(new double[]{dr.centerX() + 0.5, dr.centerZ() + 0.5, sh.cx(), sh.cz()});
        way.add(new double[]{sh.cx(), sh.cz(), anchor[0] + 0.5, anchor[1] + 0.5});
        List<int[]> hands = c.p.handSpots();
        for (int lz = 0; lz < h; lz++) {
            for (int lx = 0; lx < w; lx++) {
                int x = b[0] + lx, z = b[1] + lz, i = lx + lz * w;
                double px = x + 0.5, pz = z + 0.5;
                if (c.d(id, px, pz) >= 0.5) continue;
                in[i] = true;
                double q = c.q(id, px, pz);
                double wd = 99;
                for (double[] sg : way) wd = Math.min(wd, seg(px, pz, sg));
                double wild = clamp((wd - 1.8) / 4, 0, 1);
                int k = x + z * Plots.SIDE;
                this.wild[k] = (byte) Math.round(100 * (fight && q < 0.5 ? wild * 0.3 : wild));
                this.in[k] = (byte) Math.round(4 * clamp(-c.d(id, px, pz) + 0.5, 0, 8));
                // the ground rolls everywhere a little, half a step at a time
                double roll = (Hash.noise(seed + 25, px, pz, 8) - 0.5) * 3.6;
                // away from the way: terraces climbing toward the walls (their edges ragged), a mound here and there,
                // hollows between
                double terrace = terrace(seed, q, px, pz);
                double mn = Hash.noise(seed + 27, px, pz, 5);
                double mound = mn > 0.66 ? smooth(0.66, 0.86, mn) * 2.5 : 0;
                double dn = Hash.noise(seed + 29, px, pz, 6);
                double dip = dn < 0.3 ? -smooth(0.3, 0.12, dn) * 2 : 0;
                // off the way the ground stands a slab higher on the whole, so the way reads as a path worn into it
                double hh = roll + wild * (terrace + mound + dip + 0.6);
                // on the way half a step up at most; gentle where you fight, bold only at its edges
                if (wd < 1.8) hh = clamp(hh, 0, 0.5);
                else if (wd < 3.5) hh = clamp(hh, -1, 1);
                if (fight && q < 0.5) hh = clamp(hh, -0.5, 1);
                int gi = (int) Math.round(hh * 2);
                // head room under the roof (and under anything the dress hangs low)
                double room = Math.min(c.ceil(id, px, pz), c.dress.headroom(r, kind, x, z));
                gi = Math.max(-SINK, Math.min(Math.min(RISE, (int) Math.floor(2 * (room - 3.5))), gi));
                v[i] = gi;
                s[i] = (byte) (fight && q < 0.5 || wd < 3.5 ? 1 : 2);
                pin[i] = pinned(r, sh, px, pz, q, lake, hands, pad);
                if (pin[i]) pinned.set(k);
            }
        }
        // no specks: a rise or a hollow needs most of its neighbours with it (a wall counts), or it reads as a tile dropped
        // on the floor or a hole in it
        for (int pass = 0; pass < 2; pass++) {
            int[] o = v.clone();
            for (int lz = 0; lz < h; lz++) {
                for (int lx = 0; lx < w; lx++) {
                    int i = lx + lz * w;
                    if (!in[i] || pin[i] || o[i] == 0) continue;
                    int with = 0;
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = lx + dx, nz = lz + dz;
                            if ((dx | dz) == 0) continue;
                            int n = nx + nz * w;
                            boolean out = nx < 0 || nz < 0 || nx >= w || nz >= h || !in[n];
                            if (out || !pin[n] && Integer.signum(o[n]) == Integer.signum(o[i])) with++;
                        }
                    }
                    if (with < 4) v[i] = 0;
                }
            }
        }
        // no step steeper than a block (half a block where it's gentle), every pad level: the rises and the hollows are each
        // only ever lowered toward the floor line until no step is too steep, and where a rise meets a hollow each comes
        // to within half the step of the line
        int[] up = new int[w * h], dn = new int[w * h];
        for (int i = 0; i < w * h; i++) {
            if (!in[i] || pin[i]) continue;
            up[i] = Math.max(0, v[i]);
            dn[i] = Math.max(0, -v[i]);
        }
        boolean[] rise = new boolean[w * h], sink = new boolean[w * h];
        for (int i = 0; i < w * h; i++) {
            rise[i] = up[i] > 0;
            sink[i] = dn[i] > 0;
        }
        for (int pass = 0; pass < 32; pass++) {
            boolean changed = false;
            for (int lz = 0; lz < h; lz++) {
                for (int lx = 0; lx < w; lx++) {
                    int i = lx + lz * w;
                    if (up[i] == 0 && dn[i] == 0) continue;
                    int u = up[i], d = dn[i];
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = lx + dx, nz = lz + dz;
                            if ((dx | dz) == 0 || nx < 0 || nz < 0 || nx >= w || nz >= h) continue;
                            int n = nx + nz * w;
                            if (!in[n]) continue;
                            int st = Math.min(s[i], s[n]);
                            u = Math.min(u, sink[n] ? (st + 1) / 2 : up[n] + st);
                            d = Math.min(d, rise[n] ? st / 2 : dn[n] + st);
                        }
                    }
                    if (u != up[i] || d != dn[i]) {
                        up[i] = u;
                        dn[i] = d;
                        changed = true;
                    }
                }
            }
            if (!changed) break;
        }
        for (int i = 0; i < w * h; i++) v[i] = pin[i] ? 0 : up[i] - dn[i];
        // the walk guarantee: every doorway and the anchor joined by ground with no step over half a block; where they
        // aren't, the place's ground is halved, then flat
        int[] goals = goals(r, sh, anchor, b, w, h, in);
        int eased = ease(v, in, goals, w, h, i -> obstacle(b[0] + i % w + 0.5, b[1] + i / w + 0.5));
        if (eased > 0) dev.awaken.Awaken.LOG.info("[gates] {} place {}: no way through without a jump, its ground {}", c.lookId, id, eased == 1 ? "halved" : "flat");
        for (int lz = 0; lz < h; lz++) {
            for (int lx = 0; lx < w; lx++) {
                int i = lx + lz * w;
                if (in[i]) g[b[0] + lx + (b[1] + lz) * Plots.SIDE] = (byte) v[i];
            }
        }
    }

    /** The cells a place's way must join (box-local): just inside each doorway, and the anchor. */
    private int[] goals(Room r, PassageEngine.Shape sh, int[] anchor, int[] b, int w, int h, boolean[] in) {
        List<double[]> at = new ArrayList<>();
        for (Room.Door dr : r.doors) at.add(new double[]{dr.centerX() + 0.5, dr.centerZ() + 0.5});
        at.add(new double[]{anchor[0] + 0.5, anchor[1] + 0.5});
        int[] out = new int[at.size()];
        for (int k = 0; k < out.length; k++) {
            // from the point toward the middle until it's in the place
            double[] p = at.get(k);
            out[k] = -1;
            for (double t = 0; t <= 1 && out[k] < 0; t += 0.05) {
                int lx = (int) Math.floor(p[0] + (sh.cx() - p[0]) * t) - b[0], lz = (int) Math.floor(p[1] + (sh.cz() - p[1]) * t) - b[1];
                if (lx >= 0 && lz >= 0 && lx < w && lz < h && in[lx + lz * w] && !obstacle(b[0] + lx + 0.5, b[1] + lz + 0.5)) out[k] = lx + lz * w;
            }
        }
        return out;
    }

    /**
     * Eases a place's ground (half blocks, box-local) until its goals are joined: 0 as it was, 1 halved, 2 flat.
     */
    static int ease(int[] v, boolean[] in, int[] goals, int w, int h, java.util.function.IntPredicate obstacle) {
        int eased = 0;
        while (eased < 2 && !joined(v, in, goals, w, h, obstacle)) {
            for (int i = 0; i < v.length; i++) v[i] = eased == 0 ? v[i] / 2 : 0;
            eased++;
        }
        return eased;
    }

    /** Whether all the goals are joined over open ground (not an obstacle) with no step over half a block. */
    static boolean joined(int[] v, boolean[] in, int[] goals, int w, int h, java.util.function.IntPredicate obstacle) {
        if (goals.length < 2 || goals[0] < 0) return true;
        boolean[] seen = new boolean[w * h];
        java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
        seen[goals[0]] = true;
        q.add(goals[0]);
        while (!q.isEmpty()) {
            int i = q.poll(), lx = i % w, lz = i / w;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = lx + d[0], nz = lz + d[1], n = nx + nz * w;
                if (nx < 0 || nz < 0 || nx >= w || nz >= h || seen[n] || !in[n] || Math.abs(v[n] - v[i]) > 1 || obstacle.test(n)) continue;
                seen[n] = true;
                q.add(n);
            }
        }
        for (int gl : goals) if (gl >= 0 && !seen[gl]) return false;
        return true;
    }

    private boolean obstacle(double x, double z) {
        for (double[] bl : c.blocked) if (Math.hypot(x - bl[0], z - bl[1]) < bl[2]) return true;
        return false;
    }

    /** Ground that stays at the floor line: doorways, the middle, the anchor, lakes, and whatever the dress set down. */
    boolean pinned(Room r, PassageEngine.Shape sh, double px, double pz, double q, boolean lake, List<int[]> hands, double pad) {
        for (Room.Door dr : r.doors) if (Math.hypot(px - dr.centerX() - 0.5, pz - dr.centerZ() - 0.5) < 2.5) return true;
        if (Math.hypot(px - sh.cx(), pz - sh.cz()) < pad || c.p.nearAnchor(px, pz, 3)) return true;
        if (lake && q < 0.65) return true;
        for (double[] bl : c.blocked) if (Math.hypot(px - bl[0], pz - bl[1]) < bl[2] + 1) return true;
        for (int[] hd : hands) if (Math.hypot(px - hd[0] - 0.5, pz - hd[1] - 0.5) < 2) return true;
        // things the live layer finds again (a nook, a bell, a prop, a touchstone): an event's or a basin's wide box isn't one
        for (FloorLayout.Mark m : c.p.marks) {
            if ((m.x1() - m.x0() + 1) * (m.z1() - m.z0() + 1) > 49) continue;
            if (px > m.x0() - 1.5 && px < m.x1() + 2.5 && pz > m.z0() - 1.5 && pz < m.z1() + 2.5) return true;
        }
        return false;
    }

    /** Whether a place is fought in (a hosted room has monsters, or is an arena): its floor stays gentle. */
    boolean fight(Room r) {
        for (int i = 0; i < c.p.host.length; i++) {
            if (c.p.host[i] != r.id) continue;
            Room L = c.p.room(i);
            if (L.type.combat() || L.normals + L.elites > 0) return true;
        }
        return false;
    }

    // ---- carving (worldgen threads) -------------------------------------------------------------------------------------

    /** One column's ground: raised over the floor line, or a hollow sunk under it. */
    void column(Sculpt.Buf b, int x, int z) {
        int v = half(x, z);
        // only over open floor (never a doorway's threshold, a pool or a wall)
        if (!clear(b, x, W, z) || !Dress.solid(b, x, W - 1, z) || b.water(x, W - 1, z)) return;
        Dress d = c.dress;
        if (v == 0) {
            if (pinned.get(x + z * Plots.SIDE)) return;
            creep(b, x, W, z);
            if (b.open(x, W, z)) over(b, x, W, z, 0);
            return;
        }
        // what grew on the floor line (a candle, petals, a two-high plant) moves to the new ground
        BlockState grown = b.open(x, W, z) ? null : b.state(x, W, z);
        BlockState grown2 = grown != null && !b.open(x, W + 1, z) && clear(b, x, W + 1, z) ? b.state(x, W + 1, z) : null;
        if (grown != null) {
            b.mark(x, W, z, Sculpt.OPEN);
            if (grown2 != null) b.mark(x, W + 1, z, Sculpt.OPEN);
        }
        if (v > 0) {
            int whole = v >> 1;
            for (int y = W; y < W + whole; y++) {
                if (Dress.solid(b, x, y, z)) continue;
                BlockState bank = y == W + whole - 1 && (v & 1) == 0 ? null : d.ground(BANK, x, y, z);
                if (bank == null) b.mark(x, y, z, Sculpt.SOLID);
                else b.set(x, y, z, bank);
            }
            int top = W + whole;
            if ((v & 1) == 1 && clear(b, x, top, z)) {
                BlockState step = step(x, z, v, top);
                if (step != null) b.set(x, top, z, step);
                top++;
            }
            creep(b, x, top, z);
            if (!regrow(b, x, top, z, grown, grown2)) over(b, x, top, z, v);
            return;
        }
        int deep = -v, open = (deep + 1) >> 1;
        for (int y = W - open; y < W; y++) b.mark(x, y, z, Sculpt.OPEN);
        for (int y = W - open - 3; y < W - open; y++) if (b.get(x, y, z) == Sculpt.UNSET) b.mark(x, y, z, Sculpt.SOLID);
        int top = W - open;
        if ((deep & 1) == 1) {
            BlockState slab = d.ground(SLAB, x, top, z);
            if (slab != null) b.set(x, top, z, slab);
            top++;
        }
        creep(b, x, top, z);
        if (!regrow(b, x, top, z, grown, grown2)) over(b, x, top, z, v);
    }

    /**
     * The theme's growth creeping in where it's wildest (38.14, the way and the wild): over the floor under (x, y, z) and up
     * the foot of the walls beside it, thicker the wilder.
     */
    void creep(Sculpt.Buf b, int x, int y, int z) {
        double w = wild(x, z);
        if (w < 0.55) return;
        long hs = Hash.of(seed + 43, x, z);
        double t = (w - 0.55) / 0.45 * (0.5 + Hash.noise(seed + 45, x, z, 4));
        if (Hash.unit(hs) >= t) return;
        BlockState floor = c.dress.creep(x, y - 1, z);
        if (floor != null && b.get(x, y - 1, z) == Sculpt.SOLID) b.set(x, y - 1, z, floor);
        if (inside(x, z) > 1.5) return;
        int high = 1 + (int) (Hash.unit(Hash.mix(hs)) * 4 * w);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            int nx = x + dir.getStepX(), nz = z + dir.getStepZ();
            for (int yy = y; yy < y + high; yy++) {
                if (b.get(nx, yy, nz) != Sculpt.SOLID) break;
                BlockState wall = c.dress.creep(nx, yy, nz);
                if (wall != null) b.set(nx, yy, nz, wall);
            }
        }
    }

    /**
     * Soft steps where the ground meets a wall and where the roof steps (38.14): off the way, half the wall's feet that
     * rise sheer from a full block get a stair, its back to the wall; and where the roof over a column is a block higher
     * than beside it, a stair hangs upside down under it, stepping down to the lower roof. Only where there's room:
     * 4 or more over where you stand under a roof stair, 3 clear over a foot stair. A column whose neighbours aren't all
     * in this chunk is left (the buffer can't see them).
     */
    void soften(Sculpt.Buf b, int x, int z) {
        if (inside(x, z) <= 0 || pinned.get(x + z * Plots.SIDE) || (x & 15) == 0 || (x & 15) == 15 || (z & 15) == 0 || (z & 15) == 15) return;
        int f = W + stand(x, z);
        if (!b.open(x, f, z) || !b.open(x, f + 1, z) || !full(b, x, f - 1, z)) return;
        Dress d = c.dress;
        // the roof over the column, and a neighbour's a block lower
        int top = f + 2;
        while (top < f + 48 && b.open(x, top, z)) top++;
        int roof = top;
        if (roof - f >= 4 && full(b, x, roof, z)) {
            BlockState hang = d.ground(ROOF, x, roof - 1, z);
            Direction to = only(b, x, z, (nx, nz) -> full(b, nx, roof - 1, nz) && b.open(nx, roof - 2, nz),
                    (nx, nz) -> b.open(nx, roof - 1, nz));
            if (hang != null && to != null && hang.hasProperty(StairBlock.FACING))
                b.set(x, roof - 1, z, hang.setValue(StairBlock.FACING, to).setValue(StairBlock.HALF, net.minecraft.world.level.block.state.properties.Half.TOP));
        }
        // a wall's foot: off the way, every other one by the noise
        if (wild(x, z) < 0.3 || !b.open(x, f + 2, z) || Hash.noise(seed + 47, x, z, 3) < 0.5) return;
        BlockState step = d.ground(STAIRS, x, f, z);
        Direction wall = only(b, x, z, (nx, nz) -> full(b, nx, f, nz) && full(b, nx, f + 1, nz), (nx, nz) -> b.open(nx, f, nz));
        if (step != null && wall != null && step.hasProperty(StairBlock.FACING)) b.set(x, f, z, step.setValue(StairBlock.FACING, wall));
    }

    /** The one side of (x, z) that is {@code is}, with the side across from it {@code across}; null if none or more. */
    private static Direction only(Sculpt.Buf b, int x, int z, java.util.function.BiPredicate<Integer, Integer> is,
                                  java.util.function.BiPredicate<Integer, Integer> across) {
        Direction one = null;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (!is.test(x + dir.getStepX(), z + dir.getStepZ())) continue;
            if (one != null) return null;
            one = dir;
        }
        return one != null && across.test(x - one.getStepX(), z - one.getStepZ()) ? one : null;
    }

    /** A full block there: the shell's skin, or a palette block that's a full cube. */
    private static boolean full(Sculpt.Buf b, int x, int y, int z) {
        if (b.get(x, y, z) == Sculpt.SOLID) return true;
        BlockState s = b.state(x, y, z);
        return s != null && s.isSolidRender();
    }

    /** Sets what grew on the floor line on the new ground (on a half step it's lost: it would float). */
    static boolean regrow(Sculpt.Buf b, int x, int y, int z, BlockState grown, BlockState grown2) {
        if (grown == null) return false;
        if (!b.open(x, y, z) || !Dress.solid(b, x, y - 1, z)) return true;
        b.set(x, y, z, grown);
        if (grown2 != null && b.open(x, y + 1, z)) b.set(x, y + 1, z, grown2);
        return true;
    }

    /** A half step: a stair where the ground climbs one way across it, else a slab. */
    BlockState step(int x, int z, int v, int y) {
        Dress d = c.dress;
        BlockState stairs = d.ground(STAIRS, x, y, z);
        if (stairs != null && stairs.hasProperty(StairBlock.FACING)) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                int up = half(x + dir.getStepX(), z + dir.getStepZ()), down = half(x - dir.getStepX(), z - dir.getStepZ());
                int left = half(x + dir.getClockWise().getStepX(), z + dir.getClockWise().getStepZ());
                int right = half(x - dir.getClockWise().getStepX(), z - dir.getClockWise().getStepZ());
                if (up > v && down < v && left == v && right == v) return stairs.setValue(StairBlock.FACING, dir);
            }
        }
        return d.ground(SLAB, x, y, z);
    }

    /**
     * What lies on the ground: a drift banked against the wall in the wild (deepest at its foot, gone three blocks out), a
     * drift at the foot of a rise or in a dip, bits scattered by how wild it is (none on the way).
     */
    void over(Sculpt.Buf b, int x, int y, int z, int v) {
        if (!b.open(x, y, z)) return;
        Dress d = c.dress;
        long hs = Hash.of(seed + 41, x, z);
        double w = wild(x, z), into = inside(x, z);
        BlockState bank = d.ground(DRIFT, x, y, z);
        if (bank != null && w > 0.45 && into < 3) {
            int layers = (int) Math.round((w - 0.45) / 0.55 * (3 - into) / 3 * 7 + (Hash.unit(Hash.mix(hs + 3)) - 0.5) * 1.5);
            if (layers >= 1) {
                b.set(x, y, z, bank.trySetValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LAYERS, Math.min(7, layers)));
                return;
            }
        }
        if (v == 0) {
            BlockState bits = d.ground(BITS, x, y, z);
            if (bits != null && Hash.unit(Hash.mix(hs + 7)) < d.bits() * w) b.set(x, y, z, bits);
            return;
        }
        boolean foot = half(x + 1, z) >= v + 2 || half(x - 1, z) >= v + 2 || half(x, z + 1) >= v + 2 || half(x, z - 1) >= v + 2;
        BlockState drift = d.ground(DRIFT, x, y, z);
        if (drift != null && (foot || v < 0) && Hash.unit(hs) < 0.55) {
            b.set(x, y, z, drift.trySetValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LAYERS,
                    1 + (int) (Hash.unit(Hash.mix(hs)) * 2)));
            return;
        }
        BlockState bits = d.ground(BITS, x, y, z);
        if (bits != null && Hash.unit(Hash.mix(hs + 7)) < d.bits() * w) b.set(x, y, z, bits);
    }

    /** Open air or something thin standing there (a plant, a candle, crumbs). */
    static boolean clear(Sculpt.Buf b, int x, int y, int z) {
        if (b.open(x, y, z)) return true;
        BlockState s = b.state(x, y, z);
        return s != null && !s.isSolid() && s.getFluidState().isEmpty();
    }

    // ---- maths --------------------------------------------------------------------------------------------------------

    /**
     * The terraces' height toward a place's walls at q (0 at its middle, 1 at its wall), blocks: up to four, their edges
     * ragged. The roof rises over them by as much ({@link CaveEngine.Cave#ceil}), so the ground meets the wall, not a
     * trench at its foot.
     */
    static double terrace(long seed, double q, double px, double pz) {
        return smooth(0.45, 0.9, q + (Hash.noise(seed + 21, px, pz, 7) - 0.5) * 0.16) * 4
                + (Hash.noise(seed + 23, px, pz, 3) - 0.5) * 0.8;
    }

    /** Distance from (x, z) to a segment {ax, az, bx, bz}. */
    static double seg(double x, double z, double[] sg) {
        double vx = sg[2] - sg[0], vz = sg[3] - sg[1], l2 = vx * vx + vz * vz;
        double t = l2 == 0 ? 0 : clamp(((x - sg[0]) * vx + (z - sg[1]) * vz) / l2, 0, 1);
        return Math.hypot(x - sg[0] - vx * t, z - sg[1] - vz * t);
    }

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Smoothstep from e0 to e1 (either way round). */
    static double smooth(double e0, double e1, double v) {
        double t = clamp((v - e0) / (e1 - e0), 0, 1);
        return t * t * (3 - 2 * t);
    }
}
