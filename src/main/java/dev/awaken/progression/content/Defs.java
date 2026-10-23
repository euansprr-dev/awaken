package dev.awaken.progression.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.progression.Condition;
import dev.awaken.progression.Perk;
import dev.awaken.progression.Rank;
import dev.awaken.progression.Reward;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Data-driven progression content. Files live in data/&lt;ns&gt;/awaken/&lt;type&gt;/*.json. */
public final class Defs {
    private Defs() {}

    /** A statement about who you're becoming. Offered at awakening and each rank; emergent ones when noticed. */
    public record TruthDef(String text, Optional<String> hint, Condition condition, Reward reward, Rank minRank,
                           Rank maxRank, Optional<Condition> emergent, int weight) {
        public static final Codec<TruthDef> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("text").forGetter(TruthDef::text),
                Codec.STRING.optionalFieldOf("hint").forGetter(TruthDef::hint),
                Condition.CODEC.fieldOf("condition").forGetter(TruthDef::condition),
                Reward.CODEC.optionalFieldOf("reward", Reward.NONE).forGetter(TruthDef::reward),
                Rank.CODEC.optionalFieldOf("min_rank", Rank.E).forGetter(TruthDef::minRank),
                Rank.CODEC.optionalFieldOf("max_rank", Rank.SOVEREIGN).forGetter(TruthDef::maxRank),
                Condition.CODEC.optionalFieldOf("emergent").forGetter(TruthDef::emergent),
                Codec.INT.optionalFieldOf("weight", 10).forGetter(TruthDef::weight)
        ).apply(i, TruthDef::new));
    }

    /** Earned by a feat; equip one for its perks. Hidden titles are never shown before they're earned. */
    public record TitleDef(String name, String description, Condition condition, List<Perk> perks, boolean hidden) {
        public static final Codec<TitleDef> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(TitleDef::name),
                Codec.STRING.fieldOf("description").forGetter(TitleDef::description),
                Condition.CODEC.fieldOf("condition").forGetter(TitleDef::condition),
                Perk.CODEC.listOf().optionalFieldOf("perks", List.of()).forGetter(TitleDef::perks),
                Codec.BOOL.optionalFieldOf("hidden", true).forGetter(TitleDef::hidden)
        ).apply(i, TitleDef::new));
    }

    /** A hidden ability that unlocks from an unusual action. Never listed until found. */
    public record LatentDef(String name, String description, Condition condition, Reward reward) {
        public static final Codec<LatentDef> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(LatentDef::name),
                Codec.STRING.fieldOf("description").forGetter(LatentDef::description),
                Condition.CODEC.fieldOf("condition").forGetter(LatentDef::condition),
                Reward.CODEC.optionalFieldOf("reward", Reward.NONE).forGetter(LatentDef::reward)
        ).apply(i, LatentDef::new));
    }

    /**
     * An Awakening Path. {@code scoring} weights counters (normalised per hour played); {@code evidence} gives a line
     * per counter with %d for the real value, e.g. "You struck %d foes bare-handed".
     */
    public record PathDef(String name, String description, int colour, Map<String, Double> scoring,
                          Map<String, String> evidence, Optional<Condition> hidden, Optional<String> signaturePower,
                          List<Perk> perks, boolean available) {
        public static final Codec<PathDef> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(PathDef::name),
                Codec.STRING.fieldOf("description").forGetter(PathDef::description),
                Codec.INT.fieldOf("colour").forGetter(PathDef::colour),
                Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).fieldOf("scoring").forGetter(PathDef::scoring),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("evidence", Map.of()).forGetter(PathDef::evidence),
                Condition.CODEC.optionalFieldOf("hidden").forGetter(PathDef::hidden),
                Codec.STRING.optionalFieldOf("signature_power").forGetter(PathDef::signaturePower),
                Perk.CODEC.listOf().optionalFieldOf("perks", List.of()).forGetter(PathDef::perks),
                Codec.BOOL.optionalFieldOf("available", true).forGetter(PathDef::available)
        ).apply(i, PathDef::new));
    }

    /**
     * A Signal suggestion. Conditions measure progress since it was accepted. {@code requires} lists what the player
     * needs before it may be offered (see {@link dev.awaken.progression.Requirements}).
     */
    public record DirectiveDef(String text, Condition condition, Reward reward, int expiresDays, Rank minRank,
                               Rank maxRank, Optional<String> path, Optional<String> context, boolean quick, int weight,
                               List<String> requires) {
        public static final Codec<DirectiveDef> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("text").forGetter(DirectiveDef::text),
                Condition.CODEC.fieldOf("condition").forGetter(DirectiveDef::condition),
                Reward.CODEC.optionalFieldOf("reward", Reward.NONE).forGetter(DirectiveDef::reward),
                Codec.INT.optionalFieldOf("expires_days", 2).forGetter(DirectiveDef::expiresDays),
                Rank.CODEC.optionalFieldOf("min_rank", Rank.E).forGetter(DirectiveDef::minRank),
                Rank.CODEC.optionalFieldOf("max_rank", Rank.SOVEREIGN).forGetter(DirectiveDef::maxRank),
                Codec.STRING.optionalFieldOf("path").forGetter(DirectiveDef::path),
                Codec.STRING.optionalFieldOf("context").forGetter(DirectiveDef::context),
                Codec.BOOL.optionalFieldOf("quick", false).forGetter(DirectiveDef::quick),
                Codec.INT.optionalFieldOf("weight", 10).forGetter(DirectiveDef::weight),
                Codec.STRING.listOf().optionalFieldOf("requires", List.of()).forGetter(DirectiveDef::requires)
        ).apply(i, DirectiveDef::new));
    }

    /** One drill of the Daily Regimen. {@code requires}: as for Directives. */
    public record DrillDef(String text, Condition condition, List<String> paths, Rank minRank, List<String> requires) {
        public static final Codec<DrillDef> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("text").forGetter(DrillDef::text),
                Condition.CODEC.fieldOf("condition").forGetter(DrillDef::condition),
                Codec.STRING.listOf().optionalFieldOf("paths", List.of()).forGetter(DrillDef::paths),
                Rank.CODEC.optionalFieldOf("min_rank", Rank.E).forGetter(DrillDef::minRank),
                Codec.STRING.listOf().optionalFieldOf("requires", List.of()).forGetter(DrillDef::requires)
        ).apply(i, DrillDef::new));
    }

    /**
     * One step of the level road (data/awaken/awaken/level_road/*.json). The file id is the unlock id that the profile
     * saves, so reordering or retuning the table never revokes anything. {@code quiet} entries are granted without a
     * toast (something else announces them, like the first-power choice).
     */
    public record LevelRoadDef(int schema, int level, String title, String text, List<Grant> grants, boolean quiet, Optional<Goal> goal) {
        public static final Codec<LevelRoadDef> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("schema", 1).forGetter(LevelRoadDef::schema),
                Codec.INT.fieldOf("level").forGetter(LevelRoadDef::level),
                Codec.STRING.fieldOf("title").forGetter(LevelRoadDef::title),
                Codec.STRING.optionalFieldOf("text", "").forGetter(LevelRoadDef::text),
                Grant.CODEC.listOf().optionalFieldOf("grants", List.of()).forGetter(LevelRoadDef::grants),
                Codec.BOOL.optionalFieldOf("quiet", false).forGetter(LevelRoadDef::quiet),
                Goal.CODEC.optionalFieldOf("goal").forGetter(LevelRoadDef::goal)
        ).apply(i, LevelRoadDef::new));

        public LevelRoadDef(int schema, int level, String title, String text, List<Grant> grants, boolean quiet) {
            this(schema, level, title, text, grants, quiet, Optional.empty());
        }
    }

    /**
     * The concrete thing a level-road step asks you to do (schema 2). Once the step is unlocked and until {@code done}
     * holds, the Now tracker shows {@code now} (only while every {@code requires} token is met, see Requirements); when
     * it holds, a toast says {@code toast}. A goal already met when its step unlocks completes silently.
     */
    public record Goal(String now, Condition done, List<String> requires, String toast, int priority) {
        public static final Codec<Goal> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("now").forGetter(Goal::now),
                Condition.CODEC.fieldOf("done").forGetter(Goal::done),
                Codec.STRING.listOf().optionalFieldOf("requires", List.of()).forGetter(Goal::requires),
                Codec.STRING.optionalFieldOf("toast", "").forGetter(Goal::toast),
                Codec.INT.optionalFieldOf("priority", 35).forGetter(Goal::priority)
        ).apply(i, Goal::new));
    }

    /**
     * What a level-road step gives. type: {@code power} (id), {@code recipe} (ids), {@code item} (id, count),
     * {@code power_core} (count, rolled at your rank), {@code flag} (id; other modules ask {@code Progression.hasFlag}).
     * A step with no grants is a hint: its text is the unlock.
     */
    public record Grant(String type, Optional<String> id, List<String> ids, int count) {
        public static final Codec<Grant> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("type").forGetter(Grant::type),
                Codec.STRING.optionalFieldOf("id").forGetter(Grant::id),
                Codec.STRING.listOf().optionalFieldOf("ids", List.of()).forGetter(Grant::ids),
                Codec.INT.optionalFieldOf("count", 1).forGetter(Grant::count)
        ).apply(i, Grant::new));
    }
}
