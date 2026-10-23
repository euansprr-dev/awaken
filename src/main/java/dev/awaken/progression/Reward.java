package dev.awaken.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Optional;

/**
 * What completing something grants. Levels are fractions of the player's current level (0.25 = a quarter level).
 * {@code power} and {@code item} are applied through {@link RewardHooks} by the modules that own them.
 */
public record Reward(double levels, int statPoints, Optional<Stat> stat, int statAmount, Optional<String> power,
                     Optional<String> item, int itemCount, List<Perk> perks) {
    public static final Reward NONE = new Reward(0, 0, Optional.empty(), 0, Optional.empty(), Optional.empty(), 0, List.of());

    public static final Codec<Reward> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("levels", 0.0).forGetter(Reward::levels),
            Codec.INT.optionalFieldOf("stat_points", 0).forGetter(Reward::statPoints),
            Stat.CODEC.optionalFieldOf("stat").forGetter(Reward::stat),
            Codec.INT.optionalFieldOf("stat_amount", 0).forGetter(Reward::statAmount),
            Codec.STRING.optionalFieldOf("power").forGetter(Reward::power),
            Codec.STRING.optionalFieldOf("item").forGetter(Reward::item),
            Codec.INT.optionalFieldOf("item_count", 1).forGetter(Reward::itemCount),
            Perk.CODEC.listOf().optionalFieldOf("perks", List.of()).forGetter(Reward::perks)
    ).apply(i, Reward::new));
}
