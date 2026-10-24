package dev.awaken.city;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 * The city as this world built it ({@code awaken:city}, world-global SavedData, schemaVersion 1): the plan's hash, the
 * site, and the street and roof graphs baked in world coordinates when the city was created (SPEC §16.2: the graphs are
 * saved with the world, so a later generator change never disagrees with a city already standing).
 *
 * <p>Graph nodes: x and z in tenths of a block, y as a block height, a kind index (see {@link #NODE_KINDS}) and a ref
 * (a plot id for doors, else -1). Edges: two node indices, a move index (the plan's move list), the cost in
 * hundredths of a second, and a tag index ({@link #tags}, "" for none; "bell_bridge" marks the edges the opening breaks).
 * A roof edge whose b is negative leads to street node -1 - b.
 */
public final class CityData extends SavedData {
    public static final int SCHEMA = 1;
    public static final List<String> NODE_KINDS = List.of("junction", "door", "landmark", "roof");

    public record Graph(int[] x, int[] y, int[] z, int[] kind, int[] ref, int[] a, int[] b, int[] move, int[] cost, int[] tag) {
        static final Codec<int[]> INTS = Codec.INT_STREAM.xmap(IntStream::toArray, Arrays::stream);
        public static final Codec<Graph> CODEC = RecordCodecBuilder.create(i -> i.group(
                INTS.fieldOf("x").forGetter(Graph::x), INTS.fieldOf("y").forGetter(Graph::y), INTS.fieldOf("z").forGetter(Graph::z),
                INTS.fieldOf("kind").forGetter(Graph::kind), INTS.fieldOf("ref").forGetter(Graph::ref),
                INTS.fieldOf("a").forGetter(Graph::a), INTS.fieldOf("b").forGetter(Graph::b), INTS.fieldOf("move").forGetter(Graph::move),
                INTS.fieldOf("cost").forGetter(Graph::cost), INTS.fieldOf("tag").forGetter(Graph::tag)
        ).apply(i, Graph::new));

        static final Graph EMPTY = new Graph(new int[0], new int[0], new int[0], new int[0], new int[0], new int[0], new int[0],
                new int[0], new int[0], new int[0]);

        public int nodes() {
            return x.length;
        }

        public int edges() {
            return a.length;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Graph g && Arrays.equals(x, g.x) && Arrays.equals(y, g.y) && Arrays.equals(z, g.z)
                    && Arrays.equals(kind, g.kind) && Arrays.equals(ref, g.ref) && Arrays.equals(a, g.a) && Arrays.equals(b, g.b)
                    && Arrays.equals(move, g.move) && Arrays.equals(cost, g.cost) && Arrays.equals(tag, g.tag);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(x) * 31 + Arrays.hashCode(a);
        }
    }

    public static final Codec<CityData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("schemaVersion").forGetter(d -> d.schemaVersion),
            Codec.STRING.fieldOf("planHash").forGetter(d -> d.planHash),
            Codec.INT.fieldOf("siteX").forGetter(d -> d.siteX),
            Codec.INT.fieldOf("siteZ").forGetter(d -> d.siteZ),
            Codec.INT.fieldOf("streetY").forGetter(d -> d.streetY),
            Codec.STRING.listOf().fieldOf("tags").forGetter(d -> d.tags),
            Graph.CODEC.fieldOf("street").forGetter(d -> d.street),
            Graph.CODEC.fieldOf("roof").forGetter(d -> d.roof),
            Codec.DOUBLE.optionalFieldOf("pregenSeconds", 0.0).forGetter(d -> d.pregenSeconds)
    ).apply(i, CityData::new));

    public static final SavedDataType<CityData> TYPE = new SavedDataType<>(Awaken.id("city"), CityData::new, CODEC);

    public int schemaVersion = SCHEMA;
    public String planHash = "";
    public int siteX, siteZ, streetY;
    public List<String> tags = List.of("");
    public Graph street = Graph.EMPTY, roof = Graph.EMPTY;
    public double pregenSeconds;

    public CityData() {}

    private CityData(int schema, String planHash, int siteX, int siteZ, int streetY, List<String> tags, Graph street, Graph roof,
                     double pregenSeconds) {
        this.schemaVersion = schema;
        this.planHash = planHash;
        this.siteX = siteX;
        this.siteZ = siteZ;
        this.streetY = streetY;
        this.tags = List.copyOf(tags);
        this.street = street;
        this.roof = roof;
        this.pregenSeconds = pregenSeconds;
    }

    public boolean built() {
        return !planHash.isEmpty();
    }

    public static CityData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public static @Nullable CityData peek(MinecraftServer server) {
        return server.getDataStorage().get(TYPE);
    }

    /** Bakes the plan's graphs at this site into a fresh record (the world's copy is written by {@link #bake}). */
    static CityData of(CityLayout l, double pregenSeconds) {
        CityPlan p = l.plan;
        java.util.LinkedHashSet<String> tagSet = new java.util.LinkedHashSet<>();
        tagSet.add("");
        for (String t : p.streetGraph.etag()) tagSet.add(t);
        for (String t : p.roofGraph.etag()) tagSet.add(t);
        List<String> tags = List.copyOf(tagSet);
        CityData d = new CityData(SCHEMA, p.sha256, l.cx, l.cz, l.streetY, tags, graph(l, p.streetGraph, tags), graph(l, p.roofGraph, tags), pregenSeconds);
        return d;
    }

    static Graph graph(CityLayout l, CityPlan.Graph g, List<String> tags) {
        int n = g.nodes(), m = g.edges();
        int[] x = new int[n], y = new int[n], z = new int[n], kind = new int[n], ref = new int[n];
        for (int i = 0; i < n; i++) {
            x[i] = Math.round((g.nx()[i] + l.cx) * 10);
            z[i] = Math.round((g.nz()[i] + l.cz) * 10);
            y[i] = l.streetY + g.nry()[i];
            kind[i] = Math.max(0, NODE_KINDS.indexOf(g.nkind()[i]));
            int r = -1;
            try {
                if (!g.nref()[i].isEmpty()) r = Integer.parseInt(g.nref()[i]);
            } catch (NumberFormatException ignored) {
                r = -2; // a landmark entrance (its id is in the plan)
            }
            ref[i] = r;
        }
        int[] a = new int[m], b = new int[m], move = new int[m], cost = new int[m], tag = new int[m];
        for (int i = 0; i < m; i++) {
            a[i] = g.ea()[i];
            b[i] = g.eb()[i];
            move[i] = g.emove()[i];
            cost[i] = Math.round(g.ecost()[i] * 100);
            tag[i] = Math.max(0, tags.indexOf(g.etag()[i]));
        }
        return new Graph(x, y, z, kind, ref, a, b, move, cost, tag);
    }

    /** At creation: the world's own copy of the graphs, once. */
    static CityData bake(MinecraftServer server, CityLayout l, double pregenSeconds) {
        CityData d = get(server);
        if (d.built()) return d;
        CityData fresh = of(l, pregenSeconds);
        d.schemaVersion = fresh.schemaVersion;
        d.planHash = fresh.planHash;
        d.siteX = fresh.siteX;
        d.siteZ = fresh.siteZ;
        d.streetY = fresh.streetY;
        d.tags = fresh.tags;
        d.street = fresh.street;
        d.roof = fresh.roof;
        d.pregenSeconds = pregenSeconds;
        d.setDirty();
        Awaken.LOG.info("[city] graphs baked: street {} nodes {} edges, roof {} nodes {} edges", d.street.nodes(), d.street.edges(),
                d.roof.nodes(), d.roof.edges());
        return d;
    }
}
