package dev.awaken.descent.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.descent.Strata;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.List;
import java.util.stream.Stream;

/**
 * The Endless borrows a stratum's biome per cell: cell d uses the biome of its generator form. {@code biomes} lists the
 * biomes of forms 1..9 in order.
 */
public final class EndlessBiomeSource extends BiomeSource {
    public static final MapCodec<EndlessBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(s -> s.biomes)
    ).apply(i, EndlessBiomeSource::new));

    private final HolderSet<Biome> biomes;
    private final List<Holder<Biome>> byForm;

    public EndlessBiomeSource(HolderSet<Biome> biomes) {
        this.biomes = biomes;
        this.byForm = biomes.stream().toList();
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return biomes.stream();
    }

    @Override
    public BiomeResolver createResolver(Climate.Sampler sampler) {
        return (qx, qy, qz) -> {
            int d = Strata.endlessCellAt(QuartPos.toBlock(qx), QuartPos.toBlock(qz));
            if (d <= 0 || byForm.isEmpty()) return byForm.getFirst();
            int form = Strata.endlessForm(d);
            return byForm.get(Math.min(byForm.size(), form) - 1);
        };
    }
}
