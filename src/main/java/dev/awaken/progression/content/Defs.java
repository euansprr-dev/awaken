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

    /** A Signal suggestion. Conditions measure progress since it was accepted. */
    public record DirectiveDef(String text, Condition condition, Reward reward, int expiresDays, Rank minRank,
                               Rank maxRank, Optional<String> path, Optional<String> context, boolean quick, int weight) {
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
                Codec.INT.optionalFieldOf("weight", 10).forGetter(DirectiveDef::weight)
        ).apply(i, DirectiveDef::new));
    }

    /** One drill of the Daily Regimen. */
    public record DrillDef(String text, Condition condition, List<String> paths, Rank minRank) {
        public static final Codec<DrillDef> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("text").forGetter(DrillDef::text),
                Condition.CODEC.fieldOf("condition").forGetter(DrillDef::condition),
                Codec.STRING.listOf().optionalFieldOf("paths", List.of()).forGetter(DrillDef::paths),
                Rank.CODEC.optionalFieldOf("min_rank", Rank.E).forGetter(DrillDef::minRank)
        ).apply(i, DrillDef::new));
    }
}
