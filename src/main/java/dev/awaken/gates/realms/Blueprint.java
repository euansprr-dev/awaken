package dev.awaken.gates.realms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Map;

/**
 * One Realm as authored (SPEC §50.7): data/awaken/realms/&lt;id&gt;.json. A gate rolled from it gets a seed that changes the
 * details; the blueprint and its painting decide what the place is.
 */
public record Blueprint(int schemaVersion, String name, String form, String theme, List<String> ranks, LandSpec land, Sky sky, Life life,
                        Folk folk, Law law, Sites sites, Heart heart, List<String> moods, String line) {

    public record LandSpec(String program, int water, Map<String, Double> params) {
        static final Codec<LandSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("program").forGetter(LandSpec::program),
                Codec.INT.optionalFieldOf("water", 62).forGetter(LandSpec::water),
                Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("params", Map.of()).forGetter(LandSpec::params)
        ).apply(i, LandSpec::new));
    }

    public record Sky(List<String> biomes, String lens) {
        static final Codec<Sky> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.listOf().fieldOf("biomes").forGetter(Sky::biomes),
                Codec.STRING.optionalFieldOf("lens", "").forGetter(Sky::lens)
        ).apply(i, Sky::new));
    }

    public record Life(List<String> herds, List<String> predators, List<String> ambient) {
        static final Codec<Life> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.listOf().optionalFieldOf("herds", List.of()).forGetter(Life::herds),
                Codec.STRING.listOf().optionalFieldOf("predators", List.of()).forGetter(Life::predators),
                Codec.STRING.listOf().optionalFieldOf("ambient", List.of()).forGetter(Life::ambient)
        ).apply(i, Life::new));
    }

    public record Folk(String kit, int villages) {
        static final Codec<Folk> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("kit").forGetter(Folk::kit),
                Codec.INT.optionalFieldOf("villages", 1).forGetter(Folk::villages)
        ).apply(i, Folk::new));
    }

    public record Law(String kind, Map<String, Double> params) {
        static final Codec<Law> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("kind").forGetter(Law::kind),
                Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("params", Map.of()).forGetter(Law::params)
        ).apply(i, Law::new));
    }

    public record Sites(int wardens, int delves, int finds) {
        static final Codec<Sites> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("wardens", 3).forGetter(Sites::wardens),
                Codec.INT.optionalFieldOf("delves", 0).forGetter(Sites::delves),
                Codec.INT.optionalFieldOf("finds", 20).forGetter(Sites::finds)
        ).apply(i, Sites::new));
    }

    public record Heart(String lord, String seat) {
        static final Codec<Heart> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("lord").forGetter(Heart::lord),
                Codec.STRING.optionalFieldOf("seat", "").forGetter(Heart::seat)
        ).apply(i, Heart::new));
    }

    public static final Codec<Blueprint> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("schemaVersion").forGetter(Blueprint::schemaVersion),
            Codec.STRING.fieldOf("name").forGetter(Blueprint::name),
            Codec.STRING.fieldOf("form").forGetter(Blueprint::form),
            Codec.STRING.fieldOf("theme").forGetter(Blueprint::theme),
            Codec.STRING.listOf().fieldOf("ranks").forGetter(Blueprint::ranks),
            LandSpec.CODEC.fieldOf("land").forGetter(Blueprint::land),
            Sky.CODEC.fieldOf("sky").forGetter(Blueprint::sky),
            Life.CODEC.optionalFieldOf("life", new Life(List.of(), List.of(), List.of())).forGetter(Blueprint::life),
            Folk.CODEC.optionalFieldOf("folk", new Folk("none", 0)).forGetter(Blueprint::folk),
            Law.CODEC.optionalFieldOf("law", new Law("none", Map.of())).forGetter(Blueprint::law),
            Sites.CODEC.optionalFieldOf("sites", new Sites(3, 0, 20)).forGetter(Blueprint::sites),
            Heart.CODEC.fieldOf("heart").forGetter(Blueprint::heart),
            Codec.STRING.listOf().optionalFieldOf("moods", List.of()).forGetter(Blueprint::moods),
            Codec.STRING.optionalFieldOf("line", "").forGetter(Blueprint::line)
    ).apply(i, Blueprint::new));

    /** What's wrong with this blueprint, or empty when it can be built. */
    public String problem() {
        if (schemaVersion != 1) return "unknown schemaVersion " + schemaVersion;
        if (sky.biomes().isEmpty()) return "no biomes";
        if (!LandPrograms.has(land.program())) return "no land program " + land.program();
        for (String r : ranks) if (java.util.Arrays.stream(dev.awaken.progression.Rank.values()).noneMatch(k -> k.label.equals(r))) return "unknown rank " + r;
        return "";
    }

    /** The six things two Realms are compared on (SPEC §50.7): landform, sky, life, folk, law, heart. */
    public List<String> axes() {
        return List.of(land.program(), sky.biomes().getFirst(), String.join(",", life.herds()), folk.kit(), law.kind(), heart.lord());
    }
}
