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
    /** At most this many columns are baked per server tick; a record whose column isn't ready waits a second. */
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
    private final Int2ObjectOpenHashMap<Column> columns = new Int2ObjectOpenHashMap<>();
    private int bakesThisTick;
    long bakes, bakeNanos;

    /** Next hop toward one destination for every node, and the walking distance in decimetres (-1 unreachable). */
    record Column(short[] next, int[] dm) {}

    private StreetNet(double[] x, double[] z, int[] y, byte[] kind, int[] ref, int[] ea, int[] eb, byte[] emove, byte @Nullable [] lanes) {
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
            elen[e] = (float) Math.max(0.05, Math.hypot(x[eb[e]] - x[ea[e]], z[eb[e]] - z[ea[e]]));
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

    void newTick() {
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
            if (junctionsOnly && kind[i] != JUNCTION || !main[i]) continue;
            double d = (x[i] - px) * (x[i] - px) + (z[i] - pz) * (z[i] - pz);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }
}
