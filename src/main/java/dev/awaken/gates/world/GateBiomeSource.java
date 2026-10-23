package dev.awaken.gates.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.gates.content.Theme;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * One theme biome per cell (and the annex half its own), so fog, sky, particles and sound follow the gate's theme.
 * Reads {@link GateCells} only; the theme to holder map is built once and never changes.
 */
public final class GateBiomeSource extends BiomeSource implements BiomeResolver {
    public static final MapCodec<GateBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(s -> s.biomes),
            Biome.CODEC.fieldOf("fallback").forGetter(s -> s.fallback)
    ).apply(i, GateBiomeSource::new));

    private final HolderSet<Biome> biomes;
    private final Holder<Biome> fallback;
    private final Map<Theme, Holder<Biome>> byTheme = new EnumMap<>(Theme.class);

    public GateBiomeSource(HolderSet<Biome> biomes, Holder<Biome> fallback) {
        this.biomes = biomes;
        this.fallback = fallback;
        for (Holder<Biome> h : biomes) {
            h.unwrapKey().ifPresent(k -> {
                for (Theme t : Theme.values()) if (k.identifier().equals(t.biome())) byTheme.put(t, h);
            });
        }
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.concat(biomes.stream(), Stream.of(fallback));
    }

    @Override
    public BiomeResolver createResolver(Climate.Sampler sampler) {
        return this;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ) {
        Theme t = GateCells.themeAt(QuartPos.toBlock(quartX), QuartPos.toBlock(quartZ));
        if (t == null) return fallback;
        return byTheme.getOrDefault(t, fallback);
    }
}
