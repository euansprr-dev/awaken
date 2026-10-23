package dev.awaken.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A named passive bonus. Modules read perks with {@code Progression.perk(player, id)}, which sums every active source
 * (true Truths, the equipped Title, the Path passive, Latents). Perk ids are listed in {@link Perks}.
 */
public record Perk(String id, double amount) {
    public static final Codec<Perk> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(Perk::id),
            Codec.DOUBLE.optionalFieldOf("amount", 1.0).forGetter(Perk::amount)
    ).apply(i, Perk::new));
}
