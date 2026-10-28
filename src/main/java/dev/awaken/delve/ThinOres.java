package dev.awaken.delve;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

import java.util.List;
import java.util.Optional;

/**
 * {@code awaken:thin_ores} (SPEC §34.2): in new land, vanilla's scattered iron, copper, coal and gold are a third of
 * what they were: wherever a biome places one of {@link #THINNED} at the underground ores step, our copy
 * ({@code awaken:thinned/<name>}, the same ore at 35% of the count) takes its place, so every biome keeps the ores it
 * had, fewer of them, and a staircase still finds some. Chunks already generated keep theirs. Diamonds, redstone,
 * lapis and emerald are untouched.
 */
public record ThinOres(HolderSet<Biome> biomes) implements BiomeModifier {
    /** The vanilla placed features thinned (each has its copy in data/awaken/worldgen/placed_feature/thinned/). */
    public static final List<String> THINNED = List.of("ore_iron_upper", "ore_iron_middle", "ore_iron_small", "ore_copper", "ore_copper_large",
            "ore_coal_upper", "ore_coal_lower", "ore_gold", "ore_gold_lower", "ore_gold_extra");
    /** The share of the vanilla count the copies keep. */
    public static final double SHARE = 0.35;

    public static final MapCodec<ThinOres> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(ThinOres::biomes)).apply(i, ThinOres::new));

    public static ResourceKey<PlacedFeature> vanilla(String name) {
        return ResourceKey.create(Registries.PLACED_FEATURE, Identifier.withDefaultNamespace(name));
    }

    public static ResourceKey<PlacedFeature> thinned(String name) {
        return ResourceKey.create(Registries.PLACED_FEATURE, Awaken.id("thinned/" + name));
    }

    @Override
    public void modify(RegistryAccess registries, Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.AFTER_EVERYTHING || !biomes.contains(biome)) return;
        var placed = registries.lookupOrThrow(Registries.PLACED_FEATURE);
        List<Holder<PlacedFeature>> ores = builder.getGenerationSettings().getFeatures(GenerationStep.Decoration.UNDERGROUND_ORES);
        for (int i = 0; i < ores.size(); i++) {
            Optional<ResourceKey<PlacedFeature>> key = ores.get(i).unwrapKey();
            if (key.isEmpty() || !key.get().identifier().getNamespace().equals("minecraft")) continue;
            String name = key.get().identifier().getPath();
            if (!THINNED.contains(name)) continue;
            Optional<Holder.Reference<PlacedFeature>> copy = placed.get(thinned(name));
            if (copy.isPresent()) ores.set(i, copy.get());
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return DelveContent.THIN_ORES.get();
    }
}
