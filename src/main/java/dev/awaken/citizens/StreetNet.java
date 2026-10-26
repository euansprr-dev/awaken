package dev.awaken.citizens;

import dev.awaken.city.CityData;
import dev.awaken.city.CityPlan;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/**
 * The street graph citizens walk, in world coordinates (the world's own copy from {@code awaken:city}, or the plan
 * placed at a site for tests), with each edge's keep-right lane offset and next-hop columns baked per destination on
 * first use (SPEC §16.5): one Dijkstra from the destination gives every node's next hop toward it and its walking
 * distance, cached so walking never routes and never allocates.
 */
final class StreetNet {
    static final byte JUNCTION = 0, DOOR = 1, LANDMARK = 2;
    /** A tower's stair between its door and the wall walk (M4 Walls): walked by the ledger, never by a body. */
    static final byte MOVE_HIDDEN = 9;
    /** What climbing a tower's stair costs on top of its length (blocks; tools/city/citizens.py TOWER_CLIMB). */
    static final float TOWER_CLIMB = 45f;
    /**
     * At most this many columns are baked per server tick, across every settlement's net (SPEC §28.4: one budget for
     * all of them); a record whose column isn't ready waits a second.
     */
    static final int BAKES_PER_TICK = 3;

    final int n, m;
    /** Node positions: x, z block coordinates (centres), y = the paving's top block (feet stand at y + 1). */
    final double[] x, z;
    final int[] y;
    final byte[] kind;
    /** A door node's plot id, else -1. */
    final int[] ref;
    final int[] adjStart, adj, adjEdge;
    final int[] ea, eb;
    final byte[] emove;
    final float[] elen, elane;
    /** Edges closed for now (a jam from a runaway cart; later the Bell Bridge). */
    final boolean[] eoff;
    /** Nodes in the graph's main piece (the plan has two tiny islands nobody should be sent to). */
    final boolean[] main;
    /**
     * Nodes of the wall walk and the towers' doors (M4 Walls): never a nearest node, and a route changes between them and
     * the streets at most once (nobody crosses the city over the wall; the Watch climbs to its round and back).
     */
    final boolean[] wall;
    private final Int2ObjectOpenHashMap<Column> columns = new Int2ObjectOpenHashMap<>();
    /** Shared by every net: the settlements' registry resets it once a server tick (tests through any net's newTick). */
    private static int bakesThisTick;
    long bakes, bakeNanos;

    /** Next hop toward one destination for every node, and the walking distance in decimetres (-1 unreachable). */
    record Column(short[] next, int[] dm) {}

    private StreetNet(double[] x, double[] z, int[] y, byte[] kind, int[] ref, int[] ea, int[] eb, byte[] emove, byte @Nullable [] lanes) {
        this(x, z, y, kind, ref, ea, eb, emove, lanes, new boolean[x.length]);
    }

    private StreetNet(double[] x, double[] z, int[] y, byte[] kind, int[] ref, int[] ea, int[] eb, byte[] emove, byte @Nullable [] lanes,
                      boolean[] wall) {
        this.wall = wall;
        this.n = x.length;
        this.m = ea.length;
        this.x = x;
        this.z = z;
        this.y = y;
        this.kind = kind;
        this.ref = ref;
        this.ea = ea;
        this.eb = eb;
        this.emove = emove;
        elen = new float[m];
        elane = new float[m];
        eoff = new boolean[m];
        int[] deg = new int[n];
        for (int e = 0; e < m; e++) {
            elen[e] = (float) Math.max(0.05, Math.hypot(x[eb[e]] - x[ea[e]], z[eb[e]] - z[ea[e]])) + (emove[e] == MOVE_HIDDEN ? TOWER_CLIMB : 0f);
            elane[e] = lanes != null && e < lanes.length ? lanes[e] / 10f : 0f;
            deg[ea[e]]++;
            deg[eb[e]]++;
        }
        adjStart = new int[n + 1];
        for (int i = 0; i < n; i++) adjStart[i + 1] = adjStart[i] + deg[i];
        adj = new int[adjStart[n]];
        adjEdge = new int[adjStart[n]];
        int[] fill = Arrays.copyOf(adjStart, n);
        for (int e = 0; e < m; e++) {
            adj[fill[ea[e]]] = eb[e];
            adjEdge[fill[ea[e]]++] = e;
            adj[fill[eb[e]]] = ea[e];
            adjEdge[fill[eb[e]]++] = e;
        }
        // the main piece: everything reachable from the best-connected node
        main = new boolean[n];
        int hub = 0;
        for (int i = 1; i < n; i++) if (deg[i] > deg[hub]) hub = i;
        int[] stack = new int[n];
        int top = 0;
        if (n > 0) {
            stack[top++] = hub;
            main[hub] = true;
        }
        while (top > 0) {
            int u = stack[--top];
            for (int k = adjStart[u]; k < adjStart[u + 1]; k++) {
                int v = adj[k];
                if (!main[v]) {
                    main[v] = true;
                    stack[top++] = v;
                }
            }
        }
    }

    /** From the world's baked city graph. */
    static StreetNet of(CityData.Graph g, byte @Nullable [] lanes) {
        int n = g.nodes(), m = g.edges();
        double[] x = new double[n], z = new double[n];
        int[] y = new int[n], ref = new int[n];
        byte[] kind = new byte[n];
        for (int i = 0; i < n; i++) {
            x[i] = g.x()[i] / 10.0;
            z[i] = g.z()[i] / 10.0;
            y[i] = g.y()[i];
            kind[i] = (byte) g.kind()[i];
            ref[i] = g.ref()[i];
        }
        byte[] mv = new byte[m];
        for (int e = 0; e < m; e++) mv[e] = (byte) g.move()[e];
        return new StreetNet(x, z, y, kind, ref, g.a().clone(), g.b().clone(), mv, lanes);
    }

    /** The plan's graph placed with its centre at (cx, cz) and the street at streetY (tests, and worlds without data). */
    static StreetNet of(CityPlan plan, int cx, int cz, int streetY, byte @Nullable [] lanes) {
        CityPlan.Graph g = plan.streetGraph;
        int n = g.nodes(), m = g.edges();
        double[] x = new double[n], z = new double[n];
        int[] y = new int[n], ref = new int[n];
        byte[] kind = new byte[n];
        for (int i = 0; i < n; i++) {
            // the same rounding to tenths as CityData.graph, so both sources agree exactly
            x[i] = Math.round((g.nx()[i] + cx) * 10) / 10.0;
            z[i] = Math.round((g.nz()[i] + cz) * 10) / 10.0;
            y[i] = streetY + g.nry()[i];
            kind[i] = (byte) Math.max(0, CityData.NODE_KINDS.indexOf(g.nkind()[i]));
            int r = -1;
            try {
                if (!g.nref()[i].isEmpty()) r = Integer.parseInt(g.nref()[i]);
            } catch (NumberFormatException ignored) {
                r = -2;
            }
            ref[i] = r;
        }
        byte[] mv = new byte[m];
        for (int e = 0; e < m; e++) mv[e] = (byte) g.emove()[e];
        return new StreetNet(x, z, y, kind, ref, g.ea().clone(), g.eb().clone(), mv, lanes);
    }

    /**
     * A settlement's own graph built in code (RosterSource.Graph: offsets from its origin), placed at the origin; edges
     * carry their keep-right lanes.
     */
    static StreetNet of(dev.awaken.citizens.api.RosterSource.Graph g, int ox, int oy, int oz) {
        int n = g.nodes(), m = g.edges();
        double[] x = new double[n], z = new double[n];
        int[] y = new int[n];
        for (int i = 0; i < n; i++) {
            x[i] = Math.round((g.x()[i] + ox) * 10) / 10.0;
            z[i] = Math.round((g.z()[i] + oz) * 10) / 10.0;
            y[i] = oy + g.ry()[i];
        }
        return new StreetNet(x, z, y, g.kind().clone(), g.ref().clone(), g.a().clone(), g.b().clone(), new byte[m], g.lane());
    }

    /**
     * This graph with the roster's wall walk appended (its nodes and edges follow the plan's, as the generator numbered
     * them): the plan placed at (cx, cz) with the street at streetY.
     */
    StreetNet withWall(Roster r, int cx, int cz, int streetY) {
        int k = r.wallX.length, ke = r.wallA.length;
        if (k == 0 && ke == 0) return this;
        int nn = n + k, mm = m + ke;
        double[] nx = Arrays.copyOf(x, nn), nz = Arrays.copyOf(z, nn);
        int[] ny = Arrays.copyOf(y, nn), nref = Arrays.copyOf(ref, nn);
        byte[] nkind = Arrays.copyOf(kind, nn);
        boolean[] nwall = new boolean[nn];
        for (int i = 0; i < k; i++) {
            nx[n + i] = Math.round((r.wallX[i] + cx) * 10) / 10.0;
            nz[n + i] = Math.round((r.wallZ[i] + cz) * 10) / 10.0;
            ny[n + i] = streetY + r.wallRy[i];
            nkind[n + i] = JUNCTION;
            nref[n + i] = -1;
            nwall[n + i] = true;
        }
        int[] na = Arrays.copyOf(ea, mm), nb = Arrays.copyOf(eb, mm);
        byte[] nmove = Arrays.copyOf(emove, mm);
        for (int e = 0; e < ke; e++) {
            na[m + e] = r.wallA[e];
            nb[m + e] = r.wallB[e];
            nmove[m + e] = (byte) r.wallMove[e];
        }
        return new StreetNet(nx, nz, ny, nkind, nref, na, nb, nmove, r.edgeLane, nwall);
    }

    /** Whether the edge from a to b is a tower's stair (a body goes in at the door; the ledger walks it). */
    boolean hidden(int a, int b) {
        int e = edge(a, b);
        return e >= 0 && emove[e] == MOVE_HIDDEN;
    }

    /** A new server tick: the bake budget (shared by every settlement's net) starts again. */
    void newTick() {
        newServerTick();
    }

    static void newServerTick() {
        bakesThisTick = 0;
    }

    double len(int a, int b) {
        return Math.hypot(x[b] - x[a], z[b] - z[a]);
    }

    /** The edge joining a and b, or -1. */
    int edge(int a, int b) {
        for (int k = adjStart[a]; k < adjStart[a + 1]; k++) if (adj[k] == b) return adjEdge[k];
        return -1;
    }

    /** Keep-right offset (blocks) for walking from a to b. */
    float lane(int a, int b) {
        int e = edge(a, b);
        return e < 0 ? 0f : elane[e];
    }

    /** The column for a destination, baked now if this tick still has room; null means "wait". */
    @Nullable Column column(int dest) {
        Column c = columns.get(dest);
        if (c != null) return c;
        if (bakesThisTick >= BAKES_PER_TICK) return null;
        bakesThisTick++;
        c = bake(dest);
        columns.put(dest, c);
        return c;
    }

    /** As {@link #column} but always bakes (tests and simulations). */
    Column columnNow(int dest) {
        Column c = columns.get(dest);
        if (c == null) {
            c = bake(dest);
            columns.put(dest, c);
        }
        return c;
    }

    /** Next node from {@code at} toward {@code dest}: {@code at} itself when there, -1 when unreachable, -2 when the column waits. */
    int nextHop(int at, int dest) {
        if (at == dest) return at;
        Column c = column(dest);
        if (c == null) return -2;
        return c.next[at];
    }

    /** Walking distance in blocks from a node to a destination (-1 unreachable, -2 waiting). */
    double distance(int at, int dest) {
        if (at == dest) return 0;
        Column c = column(dest);
        if (c == null) return -2;
        int d = c.dm[at];
        return d < 0 ? -1 : d / 10.0;
    }

    int columns() {
        return columns.size();
    }

    /** Closing or opening an edge changes routes: every cached column is dropped. */
    void setOff(int e, boolean off) {
        if (eoff[e] == off) return;
        eoff[e] = off;
        columns.clear();
    }

    private Column bake(int dest) {
        long t0 = System.nanoTime();
        short[] next = new short[n];
        int[] dm = new int[n];
        Arrays.fill(next, (short) -1);
        Arrays.fill(dm, -1);
        double[] dist = new double[n];
        Arrays.fill(dist, Double.MAX_VALUE);
        int[] heap = new int[m * 2 + n + 4];
        double[] key = new double[heap.length];
        int size = 0;
        dist[dest] = 0;
        heap[size] = dest;
        key[size++] = 0;
        next[dest] = (short) dest;
        while (size > 0) {
            int u = heap[0];
            double du = key[0];
            size--;
            heap[0] = heap[size];
            key[0] = key[size];
            siftDown(heap, key, size);
            if (du > dist[u]) continue;
            // doors and landmarks are ends, never shortcuts
            if (u != dest && kind[u] != JUNCTION) continue;
            for (int k = adjStart[u]; k < adjStart[u + 1]; k++) {
                int e = adjEdge[k];
                if (eoff[e]) continue;
                int v = adj[k];
                // one change between the streets and the wall walk at most (tools/city/citizens.py City.row: the same)
                if (wall[u] != wall[dest] && wall[v] == wall[dest]) continue;
                double nd = du + elen[e];
                if (nd < dist[v]) {
                    dist[v] = nd;
                    next[v] = (short) u; // from v, the next step toward dest is u
                    if (size < heap.length) {
                        heap[size] = v;
                        key[size] = nd;
                        siftUp(heap, key, size++);
                    }
                }
            }
        }
        for (int i = 0; i < n; i++) if (dist[i] < Double.MAX_VALUE) dm[i] = (int) Math.round(dist[i] * 10);
        bakes++;
        bakeNanos += System.nanoTime() - t0;
        return new Column(next, dm);
    }

    private static void siftUp(int[] h, double[] k, int i) {
        while (i > 0) {
            int p = (i - 1) >> 1;
            if (k[p] <= k[i]) return;
            swap(h, k, i, p);
            i = p;
        }
    }

    private static void siftDown(int[] h, double[] k, int size) {
        int i = 0;
        while (true) {
            int l = 2 * i + 1, r = l + 1, s = i;
            if (l < size && k[l] < k[s]) s = l;
            if (r < size && k[r] < k[s]) s = r;
            if (s == i) return;
            swap(h, k, i, s);
            i = s;
        }
    }

    private static void swap(int[] h, double[] k, int a, int b) {
        int t = h[a];
        h[a] = h[b];
        h[b] = t;
        double tk = k[a];
        k[a] = k[b];
        k[b] = tk;
    }

    /** The node nearest a point (linear; for commands, events and tests, never per tick). */
    int nearest(double px, double pz, boolean junctionsOnly) {
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            if (junctionsOnly && kind[i] != JUNCTION || !main[i] || wall[i]) continue;
            double d = (x[i] - px) * (x[i] - px) + (z[i] - pz) * (z[i] - pz);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }
}
