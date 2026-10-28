package dev.awaken.gates.worlds;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.gates.content.Theme;

import java.util.List;
import java.util.Map;

/**
 * One world's recipe (SPEC §38, "Recipes"): {@code data/awaken/gate_worlds/<theme>/<world>.json}, read by its engine.
 * Kind and parameters, blocks, hero pieces, the biome it shows, its moods, life, residents, events, rule, secrets and
 * keepsake. Versioned with {@code schema}; a test loads every recipe. What makes a world's shape (kind, params, hero)
 * never changes once released: a new shape is a new world or a new content version, like any gate change.
 */
public record GateWorld(int schema, Theme theme, String name, String line, Tier tier, Rarity rarity, String kind, boolean tall,
                        List<String> roles, int mastery, double weight, boolean enabled, String biome, List<Mood> moods,
                        Map<String, Double> params, Dressing dressing) {
    public static final int SCHEMA = 1;

    /** Where a world sits in its theme: two everyday worlds, a deep one, a wonder, a secret one; rare ones in any gate. */
    public enum Tier {
        EVERYDAY, DEEP, WONDER, SECRET, RARE;

        /** Ranks above the theme's own before the director picks it without mastery. */
        int rankStep() {
            return switch (this) {
                case EVERYDAY, SECRET -> 0;
                case DEEP -> 1;
                case WONDER -> 2;
                case RARE -> 3;
            };
        }

        /** Floors of the theme you've cleared that open it early (mastery). */
        int masteryFloors() {
            return switch (this) {
                case EVERYDAY, SECRET, RARE -> 0;
                case DEEP -> 4;
                case WONDER -> 10;
            };
        }
    }

    public enum Rarity {
        COMMON(10), UNCOMMON(4), RARE(1), MYTHIC(0.2);

        public final double weight;

        Rarity(double weight) {
            this.weight = weight;
        }

        public String label() {
            return name().charAt(0) + name().substring(1).toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** One mood: its id, how it's named on the title card, how often it's picked, and the biome it shows ("" = the world's). */
    public record Mood(String id, String name, double weight, String biome) {
        public static final Codec<Mood> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Mood::id),
                Codec.STRING.optionalFieldOf("name", "").forGetter(Mood::name),
                Codec.DOUBLE.optionalFieldOf("weight", 1.0).forGetter(Mood::weight),
                Codec.STRING.optionalFieldOf("biome", "").forGetter(Mood::biome)
        ).apply(i, Mood::new));
    }

    /** A secret: what the Atlas calls it once found, and where it leads (a secret world's id, or ""). */
    public record Secret(String id, String name, String leadsTo) {
        public static final Codec<Secret> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Secret::id),
                Codec.STRING.fieldOf("name").forGetter(Secret::name),
                Codec.STRING.optionalFieldOf("leads_to", "").forGetter(Secret::leadsTo)
        ).apply(i, Secret::new));
    }

    /**
     * Everything that dresses a world without changing its shape: block set by role, hero pieces, ambient life, how the
     * theme's monsters look here, the sound bed, events on the Gate Rig, its rule, secrets, keepsake and signature creature.
     */
    public record Dressing(Map<String, String> blocks, List<String> hero, List<String> life, String residents, String sound,
                           List<String> events, String rule, List<Secret> secrets, String keepsake, String signature) {
        public static final Dressing NONE = new Dressing(Map.of(), List.of(), List.of(), "", "", List.of(), "", List.of(), "", "");
        static final MapCodec<Dressing> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("blocks", Map.of()).forGetter(Dressing::blocks),
                Codec.STRING.listOf().optionalFieldOf("hero", List.of()).forGetter(Dressing::hero),
                Codec.STRING.listOf().optionalFieldOf("life", List.of()).forGetter(Dressing::life),
                Codec.STRING.optionalFieldOf("residents", "").forGetter(Dressing::residents),
                Codec.STRING.optionalFieldOf("sound", "").forGetter(Dressing::sound),
                Codec.STRING.listOf().optionalFieldOf("events", List.of()).forGetter(Dressing::events),
                Codec.STRING.optionalFieldOf("rule", "").forGetter(Dressing::rule),
                Secret.CODEC.listOf().optionalFieldOf("secrets", List.of()).forGetter(Dressing::secrets),
                Codec.STRING.optionalFieldOf("keepsake", "").forGetter(Dressing::keepsake),
                Codec.STRING.optionalFieldOf("signature", "").forGetter(Dressing::signature)
        ).apply(i, Dressing::new));
    }

    private static <E extends Enum<E>> Codec<E> lower(Class<E> type) {
        return Codec.STRING.comapFlatMap(s -> {
            try {
                return com.mojang.serialization.DataResult.success(Enum.valueOf(type, s.toUpperCase(java.util.Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                return com.mojang.serialization.DataResult.error(() -> "unknown " + type.getSimpleName() + " " + s);
            }
        }, e -> e.name().toLowerCase(java.util.Locale.ROOT));
    }

    public static final Codec<GateWorld> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("schema").forGetter(GateWorld::schema),
            Codec.STRING.xmap(Theme::parse, t -> t.id).fieldOf("theme").forGetter(GateWorld::theme),
            Codec.STRING.fieldOf("name").forGetter(GateWorld::name),
            Codec.STRING.optionalFieldOf("line", "").forGetter(GateWorld::line),
            lower(Tier.class).fieldOf("tier").forGetter(GateWorld::tier),
            lower(Rarity.class).optionalFieldOf("rarity", Rarity.COMMON).forGetter(GateWorld::rarity),
            Codec.STRING.fieldOf("kind").forGetter(GateWorld::kind),
            Codec.BOOL.optionalFieldOf("tall", false).forGetter(GateWorld::tall),
            Codec.STRING.listOf().optionalFieldOf("roles", List.of("floor")).forGetter(GateWorld::roles),
            Codec.INT.optionalFieldOf("mastery", -1).forGetter(GateWorld::mastery),
            Codec.DOUBLE.optionalFieldOf("weight", 1.0).forGetter(GateWorld::weight),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(GateWorld::enabled),
            Codec.STRING.optionalFieldOf("biome", "").forGetter(GateWorld::biome),
            Mood.CODEC.listOf().optionalFieldOf("moods", List.of()).forGetter(GateWorld::moods),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("params", Map.of()).forGetter(GateWorld::params),
            Dressing.MAP_CODEC.forGetter(GateWorld::dressing)
    ).apply(i, GateWorld::new));

    public GateWorld {
        roles = List.copyOf(roles);
        moods = List.copyOf(moods);
        params = Map.copyOf(params);
    }

    /** Can hold an ordinary floor / the Lord's floor. */
    public boolean ordinary() {
        return roles.contains("floor");
    }

    public boolean boss() {
        return roles.contains("boss");
    }

    /** Floors of the theme you need to have cleared before the director picks it below its rank (mastery). */
    public int masteryFloors() {
        return mastery >= 0 ? mastery : tier.masteryFloors();
    }

    /** The lowest gate rank it turns up in without mastery. */
    public int rankFrom() {
        return theme.minRank + tier.rankStep();
    }

    public double param(String key, double fallback) {
        Double v = params.get(key);
        return v == null ? fallback : v;
    }

    public Mood mood(String id) {
        for (Mood m : moods) if (m.id().equals(id)) return m;
        return moods.isEmpty() ? null : moods.get(0);
    }

    /** The biome a floor in this mood shows: the mood's, else the world's, else "" (the theme's). */
    public String biomeFor(String mood) {
        Mood m = mood(mood);
        if (m != null && !m.biome().isEmpty()) return m.biome();
        return biome;
    }

    /** What a recipe gets wrong, or "" when it's fine (the loader skips bad ones and says why). */
    public String problem() {
        if (theme == null) return "unknown theme";
        if (schema < 1 || schema > SCHEMA) return "schema " + schema + " (this build reads up to " + SCHEMA + ")";
        if (name.isBlank()) return "no name";
        if (roles.isEmpty() || !(ordinary() || boss())) return "roles must include floor or boss";
        if (tier != Tier.SECRET && moods.isEmpty()) return "no moods";
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Mood m : moods) if (!ids.add(m.id())) return "mood " + m.id() + " twice";
        return "";
    }
}
