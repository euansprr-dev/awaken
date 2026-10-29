package dev.awaken.caves;

import dev.awaken.Awaken;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The Living Deep's cave regions (SPEC §42.2): each is a cave biome the caves pack injects under the land, held to one
 * depth band, one slot of that band's region field and the land above (a union of climate boxes). This table is the one
 * place the rule lives: the pack's Lithostitched injectors say the same thing as data (tools/content/caves_content.py
 * writes them, {@code caves_region_shares} holds them to it), and {@link CaveShapeFunction} reads it to give a region's
 * caves their own shapes, so a region's shapes and its biome agree. A point belongs to the first region, by
 * {@link #priority}, whose rule it meets (Lithostitched's order: lowest priority first, first match wins).
 * <p>
 * The rule reads the climate, not the biome above, because the cave density can't see biomes; the boxes follow
 * vanilla's own climate bands, so they line up with the biomes on top.
 */
public enum CaveRegion {
    ROOTWAYS("rootways", Band.SHALLOWS, -10, 0f, 1f, Shape.TUBES, true, Box.ROOTED),
    OCHRE_DEEP("ochre_deep", Band.MIDDLE, -10, 0f, 1f, Shape.TERRACES, true, Box.TEMPERATE, Box.WARM_WET);

    /**
     * The regions' tiers (SPEC §42.2). The Deep is below y 0, as the Singing Deep's band; above it the tiers follow the
     * ground rather than a fixed y: the Shallows are the first caves under the land (the climate depth from
     * {@link #MIN_DEPTH} to {@link #SHALLOWS_TO}, about 20 to 43 blocks down), the Middle Dark everything between them
     * and y 0. A fixed y 40 would leave lowland (ground near y 64) almost no Shallows, and the first caves from spawn plain.
     */
    public enum Band {
        SHALLOWS, MIDDLE, DEEP;

        /** Where the Shallows end, in the climate's depth (about 43 blocks under the ground). */
        public static final float SHALLOWS_TO = 0.34f;

        public static Band at(int y, float depth) {
            return y < 0 ? DEEP : depth < SHALLOWS_TO ? SHALLOWS : MIDDLE;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * A box of the land's climate (vanilla values, as the overworld router gives them; Lithostitched's
     * {@code humidity}, {@code temperature}, {@code continentalness}, {@code erosion}). Edges at vanilla's own band
     * edges: temperature -0.45 / 0.2 / 0.55, humidity -0.1, continents -0.19 (the coast) and -0.11 (inland).
     */
    public record Box(float minHumidity, float maxHumidity, float minTemperature, float maxTemperature,
                      float minContinents, float maxContinents, float minErosion, float maxErosion) {
        static final float LO = -10f, HI = 10f;
        /**
         * The Rootways' land: forests, birch and dark forest, taiga, jungle, the Elderwood and Lumen Forest (vanilla's
         * humid columns), inland, not frozen (the Frost Spires keep their frostspar caves) and not desert.
         */
        public static final Box ROOTED = new Box(-0.1f, HI, -0.45f, 0.55f, -0.11f, HI, LO, HI);
        /**
         * The Ochre Deep's land, two boxes: any temperate land, and warm land if it is wet (forest, jungle). Frozen, hot
         * and warm-dry land (the Frost Spires, deserts, badlands, savanna and the Glass Steppe) is the Glimmer Hollows'
         * (P3) and keeps today's crystal caves until then; oceans stay plain.
         */
        public static final Box TEMPERATE = new Box(LO, HI, -0.45f, 0.2f, -0.19f, HI, LO, HI);
        public static final Box WARM_WET = new Box(-0.1f, HI, 0.2f, 0.55f, -0.19f, HI, LO, HI);

        public boolean holds(float humidity, float temperature, float continents, float erosion) {
            return humidity >= minHumidity && humidity <= maxHumidity && temperature >= minTemperature && temperature <= maxTemperature
                    && continents >= minContinents && continents <= maxContinents && erosion >= minErosion && erosion <= maxErosion;
        }
    }

    /** The shape family a region's caves take ({@link CaveShapes}). */
    public enum Shape { NONE, TUBES, TERRACES }

    /**
     * Caves start this far under the ground (the climate's depth: about 128 blocks per unit, so 20 blocks), above which a
     * column keeps its own biome: the surface and the first caves under it are never a cave region.
     */
    public static final float MIN_DEPTH = 0.16f;
    /**
     * No region above this y. The game and Awaken read the land's biome at about sea level (lairs at y 67, the wide
     * world's region names at y 63, gate spots at y 64): under hills that must still be the land's own biome. Biomes are
     * read on 4-block corners, so the highest region corner is y 56.
     */
    public static final int MAX_Y = 59;

    public final String id;
    public final Band band;
    public final int priority;
    /** The slot of the band's region field, as quantiles of that field (0 to 1): {@link Fields#value} turns them into field values. */
    public final float fromQuantile, toQuantile;
    public final Shape shape;
    /** In the pack now (P0-P2: two regions; P3 the rest). */
    public final boolean injected;
    /** The land above it may have: any of these. */
    public final List<Box> boxes;

    CaveRegion(String id, Band band, int priority, float fromQuantile, float toQuantile, Shape shape, boolean injected, Box... boxes) {
        this.id = id;
        this.band = band;
        this.priority = priority;
        this.fromQuantile = fromQuantile;
        this.toQuantile = toQuantile;
        this.shape = shape;
        this.injected = injected;
        this.boxes = List.of(boxes);
    }

    public ResourceKey<Biome> biome() {
        return ResourceKey.create(Registries.BIOME, Awaken.id(id));
    }

    public boolean holds(float humidity, float temperature, float continents, float erosion) {
        for (Box b : boxes) if (b.holds(humidity, temperature, continents, erosion)) return true;
        return false;
    }

    /** The regions in the pack, in the order Lithostitched tries them. */
    public static final List<CaveRegion> ORDER = Arrays.stream(values()).filter(r -> r.injected)
            .sorted(Comparator.comparingInt((CaveRegion r) -> r.priority).thenComparing(Enum::ordinal)).toList();

    /**
     * The region at a point, or null for a plain cave: its band, then the first region in order whose field slot holds
     * the band's field there and whose land holds the climate. {@code field} is the band's field value.
     */
    public static @Nullable CaveRegion at(int y, float depth, float field, float humidity, float temperature, float continents, float erosion) {
        if (depth < MIN_DEPTH || y > MAX_Y) return null;
        return in(Band.at(y, depth), field, humidity, temperature, continents, erosion);
    }

    /** The band's region for a column's field and climate (the depth already past {@link #MIN_DEPTH}, y at most {@link #MAX_Y}). */
    public static @Nullable CaveRegion in(Band band, float field, float humidity, float temperature, float continents, float erosion) {
        for (CaveRegion r : ORDER) {
            if (r.band != band) continue;
            if (field < Fields.value(band, r.fromQuantile) || field > Fields.value(band, r.toQuantile)) continue;
            if (r.holds(humidity, temperature, continents, erosion)) return r;
        }
        return null;
    }

    /**
     * The bands' region fields ({@code awaken:caves/<band>}: the pack's 2D noise at about 500 blocks) as quantiles,
     * measured on the five studio seeds by {@code caves_region_shares}, so a slot's width is the share of its band it
     * covers. {@link #COVER} is how much of each band is a region at all (SPEC §42.2: two thirds of the Shallows and
     * the Middle Dark, half of the Deep).
     */
    public static final class Fields {
        private Fields() {}

        /** Field values at quantiles 0, 0.1, ... 1 for each band (the ends open), measured 2026-10-29 (12,005 columns). */
        static final float[][] QUANTILES = {
                {-9f, -0.360f, -0.240f, -0.151f, -0.078f, -0.007f, 0.064f, 0.141f, 0.230f, 0.349f, 9f},
                {-9f, -0.354f, -0.238f, -0.149f, -0.070f, 0.003f, 0.078f, 0.156f, 0.250f, 0.371f, 9f},
                {-9f, -0.374f, -0.256f, -0.168f, -0.087f, -0.018f, 0.058f, 0.137f, 0.231f, 0.364f, 9f},
        };
        /** Of each band, the share any region may take. */
        public static final float[] COVER = {0.66f, 0.66f, 0.5f};

        /** The band's field value at quantile q of its cover (slots are given as shares of the cover). */
        public static float value(Band band, float q) {
            float[] table = QUANTILES[band.ordinal()];
            float t = Math.clamp(q * COVER[band.ordinal()], 0f, 1f) * 10f;
            int i = Math.min(9, (int) t);
            return table[i] + (table[i + 1] - table[i]) * (t - i);
        }
    }
}
