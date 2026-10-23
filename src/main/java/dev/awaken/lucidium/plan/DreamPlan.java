package dev.awaken.lucidium.plan;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Optional;

/**
 * Tonight's dream as pure data, built at dusk from the Day Journal (SPEC section 9). Generation is deterministic
 * from this record, so the same plan always builds the same dream (the Dream Recorder relies on it).
 *
 * <p>Node positions are relative to the cell centre (x/z) and absolute for y (the walking level).
 */
public record DreamPlan(long seed, long day, String mood, int hueShift, List<Node> nodes, List<Edge> edges,
                        List<String> signs, Optional<String> nightmare, Optional<String> vision, String intention,
                        int durationTicks, List<String> spares) {

    public static final int VERSION = 1;

    /** Node kinds. */
    public static final String MEMORY = "memory", IMAGINED = "imagined", NIGHTMARE = "nightmare", MENTOR = "mentor",
            VISION = "vision", DOOR = "door";
    /** Edge kinds. */
    public static final String BRIDGE = "bridge", STAIR = "stair", PORTAL = "door";

    public record Node(String kind, String ref, String label, int x, int y, int z, int rot, boolean flip) {
        public static final Codec<Node> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("kind").forGetter(Node::kind),
                Codec.STRING.fieldOf("ref").forGetter(Node::ref),
                Codec.STRING.fieldOf("label").forGetter(Node::label),
                Codec.INT.fieldOf("x").forGetter(Node::x),
                Codec.INT.fieldOf("y").forGetter(Node::y),
                Codec.INT.fieldOf("z").forGetter(Node::z),
                Codec.INT.fieldOf("rot").forGetter(Node::rot),
                Codec.BOOL.fieldOf("flip").forGetter(Node::flip)
        ).apply(i, Node::new));

        public boolean swappable() {
            return MEMORY.equals(kind) || IMAGINED.equals(kind);
        }
    }

    public record Edge(int a, int b, String kind, int style) {
        public static final Codec<Edge> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("a").forGetter(Edge::a),
                Codec.INT.fieldOf("b").forGetter(Edge::b),
                Codec.STRING.fieldOf("kind").forGetter(Edge::kind),
                Codec.INT.fieldOf("style").forGetter(Edge::style)
        ).apply(i, Edge::new));
    }

    public static final Codec<DreamPlan> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("seed").forGetter(DreamPlan::seed),
            Codec.LONG.fieldOf("day").forGetter(DreamPlan::day),
            Codec.STRING.fieldOf("mood").forGetter(DreamPlan::mood),
            Codec.INT.fieldOf("hue").forGetter(DreamPlan::hueShift),
            Node.CODEC.listOf().fieldOf("nodes").forGetter(DreamPlan::nodes),
            Edge.CODEC.listOf().fieldOf("edges").forGetter(DreamPlan::edges),
            Codec.STRING.listOf().fieldOf("signs").forGetter(DreamPlan::signs),
            Codec.STRING.optionalFieldOf("nightmare").forGetter(DreamPlan::nightmare),
            Codec.STRING.optionalFieldOf("vision").forGetter(DreamPlan::vision),
            Codec.STRING.optionalFieldOf("intention", "").forGetter(DreamPlan::intention),
            Codec.INT.fieldOf("duration").forGetter(DreamPlan::durationTicks),
            Codec.STRING.listOf().optionalFieldOf("spares", List.of()).forGetter(DreamPlan::spares)
    ).apply(i, DreamPlan::new));

    /** Snapshot ids this plan reads (memories, the vision and spares). */
    public List<String> snapshotRefs() {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (Node n : nodes) if (MEMORY.equals(n.kind) || VISION.equals(n.kind)) out.add(n.ref);
        for (String s : spares) if (!s.startsWith("imagined:")) out.add(s);
        return out;
    }

    /** One line per node for /awaken dream plan and the journal. */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        int memories = 0;
        for (Node n : nodes) if (MEMORY.equals(n.kind)) memories++;
        sb.append(memories).append(" memories, ").append(nodes.size()).append(" places, mood ").append(mood);
        nightmare.ifPresent(f -> sb.append(", a nightmare (").append(f).append(")"));
        vision.ifPresent(v -> sb.append(", a vision"));
        return sb.toString();
    }
}
