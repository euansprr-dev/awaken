package dev.awaken.trails;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Set;

/**
 * A trail (docs/trails/PLAN.md §3.2), from {@code data/<ns>/awaken/trail/*.json}, schemaVersion 1: a short optional list of
 * things to try in one area. {@code opens} are the tokens that open it, {@code after} the trails to finish first;
 * {@code order} is "free" (any step) or "linear" (one at a time). A step is done when all its {@code done} tokens hold
 * (or it is marked by hand when {@code manual}); {@code show} tokens hide a step until they hold.
 */
public record TrailDef(int schemaVersion, String title, String area, String blurb, int weight, List<String> opens, List<String> after,
                       String order, List<Step> steps, Finish finish) {
    public static final Set<String> AREAS = Set.of("home", "weapons", "gates", "powers", "body", "world", "deep", "story");

    public record Step(String id, String text, List<String> done, List<String> show, String where, String hint, String tip, boolean manual) {
        public static final Codec<Step> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Step::id),
                Codec.STRING.fieldOf("text").forGetter(Step::text),
                Codec.STRING.listOf().optionalFieldOf("done", List.of()).forGetter(Step::done),
                Codec.STRING.listOf().optionalFieldOf("show", List.of()).forGetter(Step::show),
                Codec.STRING.optionalFieldOf("where", "").forGetter(Step::where),
                Codec.STRING.optionalFieldOf("hint", "").forGetter(Step::hint),
                Codec.STRING.optionalFieldOf("tip", "").forGetter(Step::tip),
                Codec.BOOL.optionalFieldOf("manual", false).forGetter(Step::manual)
        ).apply(i, Step::new));
    }

    /** What finishing says and pays: a line for the Journal and a share of a level, once. */
    public record Finish(String line, double levels) {
        public static final Codec<Finish> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("line", "").forGetter(Finish::line),
                Codec.DOUBLE.optionalFieldOf("levels", 0.05).forGetter(Finish::levels)
        ).apply(i, Finish::new));
    }

    public static final Codec<TrailDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", 1).forGetter(TrailDef::schemaVersion),
            Codec.STRING.fieldOf("title").forGetter(TrailDef::title),
            Codec.STRING.fieldOf("area").forGetter(TrailDef::area),
            Codec.STRING.optionalFieldOf("blurb", "").forGetter(TrailDef::blurb),
            Codec.INT.optionalFieldOf("weight", 100).forGetter(TrailDef::weight),
            Codec.STRING.listOf().optionalFieldOf("opens", List.of()).forGetter(TrailDef::opens),
            Codec.STRING.listOf().optionalFieldOf("after", List.of()).forGetter(TrailDef::after),
            Codec.STRING.optionalFieldOf("order", "free").forGetter(TrailDef::order),
            Step.CODEC.listOf().fieldOf("steps").forGetter(TrailDef::steps),
            Finish.CODEC.optionalFieldOf("finish", new Finish("", 0.05)).forGetter(TrailDef::finish)
    ).apply(i, TrailDef::new));

    public boolean linear() {
        return order.equals("linear");
    }
}
