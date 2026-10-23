package dev.awaken.hearth.request;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.progression.Rank;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One kind of thing the Anchor can ask for, from {@code data/<ns>/awaken/request/<id>.json}:
 * <pre>
 * { "schema": 1, "item": "awaken:mire_hide",            (or "tag": "minecraft:logs" with a "name")
 *   "count": {"E": 6, "D": 8, "C": 10},                 only offered at the ranks listed, this many
 *   "weight": 10, "value": 1.2,                          pick weight; effort per item (about a minute of play = 1)
 *   "any_of": ["seen:bog_toad", "seen:mire_mother"],     at least one must hold (if any are listed)
 *   "requires": ["rank:D"],                              all must hold
 *   "hint": "Bog Toads in the swamps drop it",           where to look, shown on the offer and in Status
 *   "group": "mire" }                                    never two requests of one group at once
 * </pre>
 * Requirement tokens are {@link RequestRules#met}'s: seen:, killed:, mined:, got:, depth: and every progression token.
 */
public record RequestDef(int schema, Optional<String> item, Optional<String> tag, Optional<String> name, Map<Rank, Integer> count,
                         int weight, double value, List<String> anyOf, List<String> requires, String hint, Optional<String> group) {
    public static final int SCHEMA = 1;

    public static final Codec<RequestDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(RequestDef::schema),
            Codec.STRING.optionalFieldOf("item").forGetter(RequestDef::item),
            Codec.STRING.optionalFieldOf("tag").forGetter(RequestDef::tag),
            Codec.STRING.optionalFieldOf("name").forGetter(RequestDef::name),
            Codec.unboundedMap(Rank.CODEC, Codec.INT).fieldOf("count").forGetter(RequestDef::count),
            Codec.INT.optionalFieldOf("weight", 10).forGetter(RequestDef::weight),
            Codec.DOUBLE.optionalFieldOf("value", 1.0).forGetter(RequestDef::value),
            Codec.STRING.listOf().optionalFieldOf("any_of", List.of()).forGetter(RequestDef::anyOf),
            Codec.STRING.listOf().optionalFieldOf("requires", List.of()).forGetter(RequestDef::requires),
            Codec.STRING.optionalFieldOf("hint", "").forGetter(RequestDef::hint),
            Codec.STRING.optionalFieldOf("group").forGetter(RequestDef::group)
    ).apply(i, RequestDef::new));

    /** How many the Anchor asks of a player of this rank; 0 = never at that rank. Sovereigns are asked as S-rank. */
    public int countAt(Rank rank) {
        Rank r = rank == Rank.SOVEREIGN ? Rank.S : rank;
        Integer n = count.get(r);
        return n == null ? 0 : Math.max(0, n);
    }

    /** "awaken:mire_hide" or "#minecraft:logs": what an active request matches. */
    public String target() {
        return item.orElseGet(() -> "#" + tag.orElse(""));
    }

    public String groupOr(String id) {
        return group.orElse(id);
    }
}
