package dev.awaken.gates.realms;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/** Each point's air, light and fog: the Realm's land says which of its blueprint's biomes this is. */
public final class RealmBiomeSource extends BiomeSource implements BiomeResolver {
    public static final MapCodec<RealmBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(s -> s.biomes)
    ).apply(i, RealmBiomeSource::new));

    private final HolderSet<Biome> biomes;
    private final Map<String, Holder<Biome>> byId = new HashMap<>();
    private final Holder<Biome> fallback;

    public RealmBiomeSource(HolderSet<Biome> biomes) {
        this.biomes = biomes;
        for (Holder<Biome> h : biomes) h.unwrapKey().ifPresent(k -> byId.put(k.identifier().toString(), h));
        fallback = biomes.stream().findFirst().orElseThrow();
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
        return this;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int qx, int qy, int qz) {
        int x = QuartPos.toBlock(qx), z = QuartPos.toBlock(qz);
        int cell = RealmCells.cellAt(x, z);
        RealmPlan plan = RealmCells.plan(cell);
        if (plan == null) return fallback;
        var names = plan.bp().sky().biomes();
        int i = plan.land().biome(x - RealmCells.centerX(cell), QuartPos.toBlock(qy), z - RealmCells.centerZ(cell));
        return byId.getOrDefault(names.get(Math.min(i, names.size() - 1)), fallback);
    }
}
