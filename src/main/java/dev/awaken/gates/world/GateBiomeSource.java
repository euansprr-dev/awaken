package dev.awaken.gates.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.gates.content.GatePlan;
import dev.awaken.gates.content.Theme;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * One theme biome per cell (and the annex half its own), so fog, sky, particles and sound follow the gate's theme. From
 * content version 6 each floor's plot shows its own biome: its world and mood's when it has one (SPEC §38, "Atmosphere
 * per world"), else its theme's. The game samples fog, sky and sound at the camera's 3D position, so nothing is synced.
 * Reads {@link GateCells} and cached layouts only; the holder maps are built once and never change.
 */
public final class GateBiomeSource extends BiomeSource implements BiomeResolver {
    public static final MapCodec<GateBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(s -> s.biomes),
            Biome.CODEC.fieldOf("fallback").forGetter(s -> s.fallback)
    ).apply(i, GateBiomeSource::new));

    private final HolderSet<Biome> biomes;
    private final Holder<Biome> fallback;
    private final Map<Theme, Holder<Biome>> byTheme = new EnumMap<>(Theme.class);
    private final Map<String, Holder<Biome>> byId = new HashMap<>();

    public GateBiomeSource(HolderSet<Biome> biomes, Holder<Biome> fallback) {
        this.biomes = biomes;
        this.fallback = fallback;
        for (Holder<Biome> h : biomes) {
            h.unwrapKey().ifPresent(k -> {
                for (Theme t : Theme.values()) if (k.identifier().equals(t.biome())) byTheme.put(t, h);
                byId.put(k.identifier().toString(), h);
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
        int x = QuartPos.toBlock(quartX), z = QuartPos.toBlock(quartZ);
        int cell = Cells.cellAt(x, z);
        GatePlan p = GateCells.plan(cell);
        if (p == null) return fallback;
        int lx = Cells.localX(cell, x), lz = Cells.localZ(cell, z);
        if (p.contentVersion() < Plots.FIRST_VERSION) return theme(lx >= Cells.ANNEX_SLOT_X * 16 ? p.annexTheme() : p.theme());
        FloorLayout f = GateLayout.of(p).floorAt(lx, lz, QuartPos.toBlock(quartY));
        if (f == null) return theme(p.theme());
        if (!f.placeBiomes.isEmpty()) {
            // a secret world inside this one shows its own air
            Room r = f.roomAt(lx, QuartPos.toBlock(quartY), lz);
            Holder<Biome> h = r == null ? null : byId.get(f.placeBiomes.getOrDefault(r.id, ""));
            if (h != null) return h;
        }
        if (!f.biome.isEmpty()) {
            Holder<Biome> h = byId.get(f.biome);
            if (h != null) return h;
        }
        return theme(f.theme);
    }

    private Holder<Biome> theme(Theme t) {
        return byTheme.getOrDefault(t, fallback);
    }

    /** Whether the dimension lists this biome (a world's recipe names one that exists). */
    public boolean has(String biomeId) {
        return byId.containsKey(biomeId);
    }
}
