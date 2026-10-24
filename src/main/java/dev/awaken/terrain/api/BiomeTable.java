package dev.awaken.terrain.api;

import dev.awaken.Awaken;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;

/**
 * The one biome table at run time (docs/SPEC.md §15). {@code tools/terrain/biomes.py} writes it as biome tags: base data
 * lists vanilla biomes, the Awakened Lands pack adds its own biomes to the same tags plus every tag of the vanilla biome
 * each stands in for (its archetype). Code that asks tags needs nothing more; code that reasons by a biome's name asks
 * {@link #archetype} first. Safe on any thread (a holder's bound tags are an immutable set).
 */
public final class BiomeTable {
    private BiomeTable() {}

    private static final String ARCHETYPE = "archetype/";

    /**
     * A tag the table writes, {@code awaken:<path>}: {@code fauna/<group>} (a spawn modifier's biomes),
     * {@code has_lair/<beast>}, {@code lair_style/<style>} (a lair's second look), {@code cottage/<plan>},
     * {@code archetype/<vanilla biome>}.
     */
    public static TagKey<Biome> tag(String path) {
        return TagKey.create(Registries.BIOME, Awaken.id(path));
    }

    /**
     * The vanilla biome this one stands in for: an Awakened biome's {@code #awaken:archetype/<vanilla biome>} tag, else
     * the biome's own key (every vanilla biome is its own archetype). Null only for an unregistered biome.
     */
    public static @Nullable ResourceKey<Biome> archetype(Holder<Biome> biome) {
        Identifier best = null;
        for (Iterator<TagKey<Biome>> it = biome.tags().iterator(); it.hasNext(); ) {
            Identifier t = it.next().location();
            // one per biome; were there two, the first by name wins so every thread agrees
            if (t.getNamespace().equals(Awaken.MODID) && t.getPath().startsWith(ARCHETYPE) && (best == null || t.compareTo(best) < 0)) best = t;
        }
        if (best == null) return biome.unwrapKey().orElse(null);
        return ResourceKey.create(Registries.BIOME, Identifier.withDefaultNamespace(best.getPath().substring(ARCHETYPE.length())));
    }
}
