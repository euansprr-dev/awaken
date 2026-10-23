package dev.awaken.lucidium.plan;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Nightmares are chosen from authored templates by what the player has died to this week (the patent rule: never a
 * remembered enemy, always a fresh authored fear). Each family has its own arena and spawn theme
 * {@code awaken:nightmare/<family>}.
 */
public final class Nightmares {
    private Nightmares() {}

    public enum Family {
        FALLING("falling", "The ground forgets you.", "a fall that never ends"),
        BURNING("burning", "Everything here is on fire, and so are you.", "a fire you cannot put out"),
        DROWNING("drowning", "The air turns to water.", "deep water closing over you"),
        UNDEAD("undead", "The dead walk out of the walls.", "the dead that would not stay down"),
        ARTHROPOD("arthropod", "Too many legs. Too many eyes.", "things with too many legs"),
        EXPLOSION("explosion", "A hiss, right behind you.", "the hiss before the blast"),
        GENERIC("generic", "Something is hunting you in the dark.", "something hunting you in the dark");

        public final String id;
        public final String warning;
        public final String fear;

        Family(String id, String warning, String fear) {
            this.id = id;
            this.warning = warning;
            this.fear = fear;
        }

        public String theme() {
            return "awaken:nightmare/" + id;
        }

        public static Optional<Family> byId(String id) {
            for (Family f : values()) if (f.id.equals(id)) return Optional.of(f);
            return Optional.empty();
        }
    }

    public static final String KILLED_BY_WEEK = "awaken:killed_by_week/";

    /** Family of a killer entity id ("minecraft:zombie"). Unknown ids are GENERIC. */
    public static Family familyOfEntity(String entityId) {
        Identifier id = Identifier.tryParse(entityId);
        if (id == null) return Family.GENERIC;
        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
        if (type.isEmpty()) return Family.GENERIC;
        return familyOfType(type.get());
    }

    public static Family familyOfType(EntityType<?> type) {
        if (type == EntityTypes.CREEPER) return Family.EXPLOSION;
        if (type == EntityTypes.DROWNED || type == EntityTypes.GUARDIAN || type == EntityTypes.ELDER_GUARDIAN) return Family.DROWNING;
        if (type == EntityTypes.BLAZE || type == EntityTypes.MAGMA_CUBE || type == EntityTypes.GHAST) return Family.BURNING;
        if (type.builtInRegistryHolder().is(EntityTypeTags.UNDEAD)) return Family.UNDEAD;
        if (type.builtInRegistryHolder().is(EntityTypeTags.ARTHROPOD)) return Family.ARTHROPOD;
        return Family.GENERIC;
    }

    /** Family of a death, by how it happened first and who did it second. */
    public static Family familyOf(DamageSource source) {
        if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) return Family.FALLING;
        if (source.is(DamageTypeTags.IS_DROWNING)) return Family.DROWNING;
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypes.LAVA) || source.is(DamageTypes.HOT_FLOOR)) return Family.BURNING;
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return Family.EXPLOSION;
        if (source.getEntity() != null) return familyOfType(source.getEntity().getType());
        return Family.GENERIC;
    }

    /**
     * Weekly fear scores. {@code killedByWeek} are the progression counters {@code awaken:killed_by_week/<entity>};
     * {@code ownMarks} are lucidium's own death marks from the last seven play-days (which also see falls, fire and
     * water). Each family scores the larger of the two so one death is never counted twice.
     */
    public static Map<Family, Integer> scores(Map<String, Long> counters, Map<Family, Integer> ownMarks) {
        EnumMap<Family, Integer> fromCounters = new EnumMap<>(Family.class);
        for (var e : counters.entrySet()) {
            if (!e.getKey().startsWith(KILLED_BY_WEEK) || e.getValue() <= 0) continue;
            Family f = familyOfEntity(e.getKey().substring(KILLED_BY_WEEK.length()));
            fromCounters.merge(f, (int) Math.min(1000, e.getValue()), Integer::sum);
        }
        EnumMap<Family, Integer> out = new EnumMap<>(Family.class);
        for (Family f : Family.values()) {
            int s = Math.max(fromCounters.getOrDefault(f, 0), ownMarks.getOrDefault(f, 0));
            if (s > 0) out.put(f, s);
        }
        return out;
    }

    /** The family feared most (ties: enum order), if any death this week. */
    public static Optional<Family> top(Map<Family, Integer> scores) {
        Family best = null;
        int bestScore = 0;
        for (Family f : Family.values()) {
            int s = scores.getOrDefault(f, 0);
            if (s > bestScore) {
                best = f;
                bestScore = s;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Whether tonight's dream holds a nightmare, and which. More deaths make it likelier; "face my fear" makes it
     * certain (a GENERIC one if nothing has killed the player this week).
     */
    public static Optional<Family> choose(Map<Family, Integer> scores, boolean faceMyFear, RandomSource random) {
        Optional<Family> top = top(scores);
        if (faceMyFear) return Optional.of(top.orElse(Family.GENERIC));
        if (top.isEmpty()) return Optional.empty();
        int score = scores.get(top.get());
        double chance = Math.min(0.85, 0.40 + 0.15 * (score - 1));
        return random.nextDouble() < chance ? top : Optional.empty();
    }

    /** Counter ids to zero when the player beats this family's nightmare. */
    public static java.util.List<String> weeklyCountersOf(Family family, Map<String, Long> counters) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (var e : counters.entrySet()) {
            if (e.getKey().startsWith(KILLED_BY_WEEK) && e.getValue() > 0
                    && familyOfEntity(e.getKey().substring(KILLED_BY_WEEK.length())) == family) {
                out.add(e.getKey());
            }
        }
        return out;
    }
}
