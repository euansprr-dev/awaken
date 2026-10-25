package dev.awaken.living.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.progression.Reward;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A Wayfarer chain (SPEC §21, PLAN §2.2), from {@code data/<ns>/awaken/quest/<chain>.json}, schema 1. Lines are authored
 * (no language model), keyed so a step can say them when it starts and when it's done. A step whose {@code needs} the
 * player lacks (an Ascent tier, a flag) waits and says so.
 */
public record QuestDef(int schemaVersion, String title, int band, List<String> requires, String scene, String giver,
                       List<Step> steps, Optional<String> relic, Reward finish, String summary, List<String> ask) {

    /** What a step asks: talk to a Wayfarer, reach the scene, kill, collect, deliver, use an ability, discover. */
    public record Objective(String type, String target, int count, double radius) {
        public static final Codec<Objective> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("type").forGetter(Objective::type),
                Codec.STRING.optionalFieldOf("target", "").forGetter(Objective::target),
                Codec.INT.optionalFieldOf("count", 1).forGetter(Objective::count),
                Codec.DOUBLE.optionalFieldOf("radius", 8.0).forGetter(Objective::radius)
        ).apply(i, Objective::new));
    }

    /** Creatures a step brings when it starts: {@code count} of {@code entity} at a scene marker ({@code scene}, {@code site}). */
    public record Spawn(String entity, int count, String at) {
        public static final Codec<Spawn> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("entity").forGetter(Spawn::entity),
                Codec.INT.optionalFieldOf("count", 1).forGetter(Spawn::count),
                Codec.STRING.optionalFieldOf("at", "scene").forGetter(Spawn::at)
        ).apply(i, Spawn::new));
    }

    public record Step(String id, Objective objective, String text, List<String> start, List<String> done,
                       Reward reward, Optional<String> needs, String needsText, Optional<Spawn> spawn, Optional<String> move) {
        public static final Codec<Step> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Step::id),
                Objective.CODEC.fieldOf("objective").forGetter(Step::objective),
                Codec.STRING.fieldOf("text").forGetter(Step::text),
                Codec.STRING.listOf().optionalFieldOf("start", List.of()).forGetter(Step::start),
                Codec.STRING.listOf().optionalFieldOf("done", List.of()).forGetter(Step::done),
                Reward.CODEC.optionalFieldOf("reward", Reward.NONE).forGetter(Step::reward),
                Codec.STRING.optionalFieldOf("needs").forGetter(Step::needs),
                Codec.STRING.optionalFieldOf("needs_text", "").forGetter(Step::needsText),
                Spawn.CODEC.optionalFieldOf("spawn").forGetter(Step::spawn),
                Codec.STRING.optionalFieldOf("move").forGetter(Step::move)
        ).apply(i, Step::new));
    }

    public static final Codec<QuestDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", 1).forGetter(QuestDef::schemaVersion),
            Codec.STRING.fieldOf("title").forGetter(QuestDef::title),
            Codec.INT.optionalFieldOf("band", 0).forGetter(QuestDef::band),
            Codec.STRING.listOf().optionalFieldOf("requires", List.of()).forGetter(QuestDef::requires),
            Codec.STRING.optionalFieldOf("scene", "resident").forGetter(QuestDef::scene),
            Codec.STRING.optionalFieldOf("giver", "").forGetter(QuestDef::giver),
            Step.CODEC.listOf().fieldOf("steps").forGetter(QuestDef::steps),
            Codec.STRING.optionalFieldOf("relic").forGetter(QuestDef::relic),
            Reward.CODEC.optionalFieldOf("finish", Reward.NONE).forGetter(QuestDef::finish),
            Codec.STRING.optionalFieldOf("summary", "").forGetter(QuestDef::summary),
            Codec.STRING.listOf().optionalFieldOf("ask", List.of()).forGetter(QuestDef::ask)
    ).apply(i, QuestDef::new));

    /** Objective types the engine knows. */
    public static final List<String> TYPES = List.of("talk", "reach", "kill", "collect", "deliver", "use", "discover");

    public static Map<String, String> problems(String id, QuestDef d) {
        var out = new java.util.LinkedHashMap<String, String>();
        if (d.steps.isEmpty()) out.put(id, "no steps");
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Step s : d.steps) {
            if (!ids.add(s.id)) out.put(id + "/" + s.id, "duplicate step id");
            if (!TYPES.contains(s.objective.type)) out.put(id + "/" + s.id, "unknown objective " + s.objective.type);
            if (s.objective.count <= 0) out.put(id + "/" + s.id, "count must be positive");
            for (String l : s.start) if (l.contains("—")) out.put(id + "/" + s.id, "an em dash in a line");
            for (String l : s.done) if (l.contains("—")) out.put(id + "/" + s.id, "an em dash in a line");
        }
        for (String l : d.ask) if (l.contains("—")) out.put(id, "an em dash in a line");
        if (d.band < 0 || d.band > 4) out.put(id, "band must be 0..4");
        return out;
    }
}
