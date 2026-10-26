package dev.awaken.wildlife;

import dev.awaken.core.audio.world.Families;

/**
 * How much of each kind of wild life a stretch of land holds (SPEC "Wild life"): the expected number living in one
 * chunk at a time of day, in some weather, given what the chunk offers (perches, trunks, flowers, still water). Real and
 * never a zoo: one to three birds in view on a temperate forest walk at noon, a few butterflies in a sunny meadow,
 * nothing in a deep forest at night but fireflies and an owl. The client turns this into animals deterministically per
 * chunk ({@link #count}), so a place looks alike when you come back. Pure: the GameTests read it on the server.
 */
public final class Density {
    private Density() {}

    // the sound families' order (core.audio.world.Families.NAMES)
    static final int GREENWOOD = 0, BLOSSOM = 1, DUSKWOOD = 2, CONIFER = 3, OPEN_GRASS = 4, DRY_WARMTH = 5, DESERT = 6,
            CANOPY = 7, MIRE = 8, HEIGHTS = 9, SNOWFIELD = 10, COAST = 11, OPEN_SEA = 12, RIVER = 13, SPOREWILD = 14,
            HOLLOWS = 15;

    /**
     * Affinity of each kind for each family (0..1), in the families' order: greenwood, blossom, duskwood, conifer,
     * open_grass, dry_warmth, desert, canopy, mire, heights, snowfield, coast, open_sea, river, sporewild, hollows.
     */
    static final float[][] AFFINITY = {
            /* songbird   */ {1.0F, 1.0F, 0.5F, 0.6F, 0.5F, 0.4F, 0.05F, 0.9F, 0.6F, 0.25F, 0.2F, 0.3F, 0F, 0.7F, 0.3F, 0.1F},
            /* woodpecker */ {1.0F, 0.6F, 0.6F, 0.9F, 0.1F, 0.1F, 0F, 0.6F, 0.4F, 0.2F, 0.2F, 0F, 0F, 0.3F, 0.2F, 0F},
            /* crow       */ {0.3F, 0.2F, 0.4F, 0.3F, 1.0F, 0.6F, 0.2F, 0F, 0.3F, 0.6F, 0.4F, 0.4F, 0F, 0.5F, 0.3F, 0.2F},
            /* owl        */ {1.0F, 0.6F, 1.0F, 0.8F, 0.3F, 0.2F, 0.05F, 0.5F, 0.5F, 0.2F, 0.4F, 0.1F, 0F, 0.5F, 0.6F, 0.3F},
            /* raptor     */ {0.3F, 0.2F, 0.1F, 0.3F, 1.0F, 1.0F, 0.5F, 0.1F, 0.4F, 1.0F, 0.5F, 0.5F, 0.1F, 0.5F, 0.1F, 0.1F},
            /* gull       */ {0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F, 1.0F, 0.6F, 0.25F, 0F, 0F},
            /* duck       */ {0.3F, 0.3F, 0.2F, 0.3F, 0.4F, 0.1F, 0F, 0.1F, 1.0F, 0.1F, 0.2F, 0.3F, 0F, 1.0F, 0.3F, 0F},
            /* heron      */ {0.3F, 0.2F, 0.1F, 0.2F, 0.3F, 0.1F, 0F, 0.1F, 1.0F, 0.1F, 0.1F, 0.5F, 0F, 1.0F, 0.2F, 0F},
            /* squirrel   */ {1.0F, 0.7F, 0.6F, 1.0F, 0.1F, 0.1F, 0F, 0.4F, 0.2F, 0.2F, 0.3F, 0F, 0F, 0.3F, 0.3F, 0F},
            /* rabbit     */ {0.6F, 0.6F, 0.2F, 0.4F, 1.0F, 0.6F, 0.4F, 0F, 0.1F, 0.4F, 0.6F, 0.3F, 0F, 0.4F, 0.2F, 0F},
            /* fox        */ {0.6F, 0.4F, 0.5F, 0.7F, 0.8F, 0.5F, 0.2F, 0F, 0.2F, 0.4F, 0.8F, 0.2F, 0F, 0.3F, 0.3F, 0F},
            /* frog       */ {0.4F, 0.3F, 0.3F, 0.2F, 0.3F, 0F, 0F, 0.6F, 1.0F, 0F, 0F, 0.1F, 0F, 0.8F, 0.4F, 0F},
            /* butterfly  */ {0.6F, 1.0F, 0.1F, 0.2F, 1.0F, 0.7F, 0.1F, 0.8F, 0.3F, 0.4F, 0F, 0.3F, 0F, 0.5F, 0.3F, 0F},
            /* bee        */ {0.5F, 1.0F, 0.05F, 0.2F, 0.9F, 0.6F, 0F, 0.4F, 0.2F, 0.3F, 0F, 0.2F, 0F, 0.4F, 0.1F, 0F},
            /* dragonfly  */ {0.6F, 0.5F, 0.3F, 0.5F, 0.6F, 0.3F, 0F, 0.8F, 1.0F, 0.1F, 0F, 0.2F, 0F, 1.0F, 0.4F, 0F},
            /* firefly    */ {1.0F, 0.8F, 1.0F, 0.5F, 0.7F, 0.2F, 0F, 1.0F, 1.0F, 0.1F, 0F, 0.1F, 0F, 0.8F, 0.9F, 0.2F},
            /* moth       */ {0.6F, 0.6F, 0.6F, 0.6F, 0.6F, 0.6F, 0.3F, 0.6F, 0.6F, 0.5F, 0.2F, 0.5F, 0F, 0.6F, 0.6F, 0.4F},
            /* gnats      */ {0.3F, 0.2F, 0.5F, 0.2F, 0.3F, 0.1F, 0F, 0.6F, 1.0F, 0F, 0F, 0.2F, 0F, 0.8F, 0.6F, 0.1F},
            /* strider    */ {0.5F, 0.4F, 0.3F, 0.4F, 0.4F, 0.2F, 0F, 0.5F, 1.0F, 0F, 0F, 0.1F, 0F, 0.8F, 0.3F, 0F},
    };

    /** The most of each kind one ideal chunk holds (at full affinity, the best hour and the best habitat). */
    static final float[] PEAK = {0.55F, 0.06F, 0.12F, 0.05F, 0.006F, 0.5F, 0.6F, 0.12F, 0.22F, 0.25F, 0.025F, 0.8F,
            2.5F, 1.5F, 1.5F, 7F, 2.5F, 1F, 2F};

    /**
     * The hour each kind is about, as (time of day, factor) points over the 24000-tick day, wrapping round midnight
     * (0 = 06:00, 6000 noon, 12000 18:00, 18000 midnight). Dawn and dusk are busiest; night has owls, foxes, frogs,
     * moths and fireflies.
     */
    static final float[][] HOURS = {
            /* songbird   */ {0, 1.5F, 1500, 1.4F, 3000, 1.0F, 6000, 0.75F, 9000, 0.9F, 11000, 1.1F, 12500, 0.8F, 13300, 0, 22300, 0, 23000, 1.2F},
            /* woodpecker */ {0, 0.7F, 2000, 1, 10000, 1, 12000, 0.4F, 12800, 0, 23000, 0, 23600, 0.5F},
            /* crow       */ {0, 0.8F, 2000, 1, 12000, 1, 12800, 0, 23000, 0, 23600, 0.6F},
            /* owl        */ {0, 0, 12000, 0, 12800, 0.6F, 13500, 1, 22000, 1, 23000, 0.4F, 23800, 0},
            /* raptor     */ {0, 0, 1500, 0.6F, 3000, 1, 9500, 1, 11000, 0.4F, 12000, 0},
            /* gull       */ {0, 0.8F, 2000, 1, 11000, 1, 13000, 0.4F, 14000, 0.2F, 22000, 0.2F, 23500, 0.6F},
            /* duck       */ {0, 1, 12500, 1, 14000, 0.7F, 22000, 0.7F, 23500, 1},
            /* heron      */ {0, 1.4F, 2000, 0.9F, 10000, 0.9F, 12000, 1.4F, 13500, 0.6F, 14500, 0.3F, 22000, 0.3F, 23000, 1.2F},
            /* squirrel   */ {0, 0.8F, 2000, 1, 10000, 1, 12000, 0.6F, 12800, 0, 23000, 0, 23600, 0.5F},
            /* rabbit     */ {0, 1.6F, 2000, 0.7F, 4000, 0.5F, 9000, 0.5F, 11000, 1.2F, 12500, 1.6F, 14000, 0.6F, 22000, 0.4F, 23500, 1.4F},
            /* fox        */ {0, 1.0F, 1500, 0.4F, 3000, 0.15F, 10000, 0.15F, 11500, 0.9F, 12500, 1.6F, 14000, 1.2F, 18000, 0.8F, 22500, 1.2F},
            /* frog       */ {0, 0.6F, 6000, 0.5F, 11500, 0.8F, 13000, 1, 22000, 1, 23500, 0.7F},
            /* butterfly  */ {0, 0, 1500, 0.3F, 3000, 1, 9000, 1, 10500, 0.4F, 11500, 0},
            /* bee        */ {0, 0, 1000, 0.4F, 2500, 1, 9500, 1, 11000, 0.2F, 11800, 0},
            /* dragonfly  */ {0, 0.2F, 2000, 1, 10000, 1, 11500, 0.3F, 12300, 0, 23200, 0},
            /* firefly    */ {0, 0, 12200, 0, 13200, 0.8F, 14000, 1, 21500, 1, 22600, 0.3F, 23200, 0},
            /* moth       */ {0, 0, 12500, 0, 13500, 1, 22000, 1, 23000, 0},
            /* gnats      */ {0, 0.8F, 1500, 0.3F, 9500, 0.3F, 11000, 1.4F, 13000, 1.4F, 13800, 0.3F, 22000, 0.2F, 23200, 0.8F},
            /* strider    */ {0, 0.5F, 2000, 1, 10000, 1, 12000, 0.5F, 13000, 0.2F, 23000, 0.3F},
    };

    /** What a chunk offers (the client's scan fills one per chunk and reuses it). Counts are what was found, not kept. */
    public static final class Habitat {
        /** Share of columns under leaves (0..1), the biome's temperature, the sound family (-1 none). */
        public float canopy, temperature = 0.7F;
        public int family = -1;
        /** An Awakened biome's own flavour (VARIANTS), 0 for any other land. */
        public int variant;
        public int perches, trunks, ground, flowers, water, shore, lights;

        public Habitat set(int family, float canopy, float temperature, int perches, int trunks, int ground, int flowers,
                           int water, int shore, int lights) {
            this.family = family;
            this.variant = 0;
            this.canopy = canopy;
            this.temperature = temperature;
            this.perches = perches;
            this.trunks = trunks;
            this.ground = ground;
            this.flowers = flowers;
            this.water = water;
            this.shore = shore;
            this.lights = lights;
            return this;
        }
    }

    /** The Awakened biomes (Awakened Lands): their path, and what each brings more of. */
    public static final String[] VARIANTS = {"", "elderwood", "lumen_forest", "glass_steppe", "shattered_highlands",
            "frost_spires", "mirror_marsh"};

    /** More of a kind in an Awakened biome: the old wood's woodpeckers and owls, Lumen's fireflies, the Marsh's herons. */
    public static float variant(int kind, int variant) {
        return switch (variant) {
            case 1 -> kind == WildKinds.WOODPECKER ? 2F : kind == WildKinds.OWL ? 1.5F : kind == WildKinds.SQUIRREL ? 1.3F : 1F;
            case 2 -> kind == WildKinds.FIREFLY ? 1.6F : kind == WildKinds.MOTH ? 1.5F : kind == WildKinds.OWL ? 1.3F : 1F;
            case 3, 4 -> kind == WildKinds.RAPTOR ? 1.6F : 1F;
            case 6 -> kind == WildKinds.HERON || kind == WildKinds.DRAGONFLY ? 1.6F : kind == WildKinds.FROG ? 1.3F : 1F;
            default -> 1F;
        };
    }

    /** The factor of the hour for a kind (0..~1.6). */
    public static float hour(int kind, int tod) {
        float[] k = HOURS[kind];
        float t = Math.floorMod(tod, 24000);
        int n = k.length / 2;
        for (int i = 0; i < n; i++) {
            float t0 = k[i * 2], t1 = i + 1 < n ? k[i * 2 + 2] : k[0] + 24000;
            if (t >= t0 && t < t1) {
                float v0 = k[i * 2 + 1], v1 = i + 1 < n ? k[i * 2 + 3] : k[1];
                return v0 + (v1 - v0) * (t - t0) / (t1 - t0);
            }
        }
        return k[1];
    }

    /** Rain (0..1) and thunder (0..1): birds shelter, insects stop, frogs come out. */
    public static float weather(int kind, float rain, float thunder) {
        if (kind >= WildKinds.BODIES) return thunder > 0.3F ? 0F : Math.max(0F, 1F - 1.5F * rain);
        float f = switch (kind) {
            case WildKinds.SONGBIRD, WildKinds.WOODPECKER -> 1F - 0.6F * rain;
            case WildKinds.CROW -> 1F - 0.4F * rain;
            case WildKinds.OWL -> 1F - 0.5F * rain;
            case WildKinds.RAPTOR -> 1F - rain;
            case WildKinds.HERON -> 1F - 0.2F * rain;
            case WildKinds.SQUIRREL -> 1F - 0.7F * rain;
            case WildKinds.RABBIT -> 1F - 0.5F * rain;
            case WildKinds.FOX -> 1F - 0.3F * rain;
            case WildKinds.FROG -> 1F + 0.5F * rain;
            default -> 1F;
        };
        if (WildKinds.flies(kind) && kind != WildKinds.DUCK && kind != WildKinds.GULL) f *= 1F - 0.7F * thunder;
        return Math.max(0F, f);
    }

    /** Cold land (snowy biomes): no insects or frogs, few small birds. */
    public static float cold(int kind, float temperature) {
        if (temperature >= 0.15F) return 1F;
        if (kind >= WildKinds.BODIES || kind == WildKinds.FROG) return 0F;
        return switch (kind) {
            case WildKinds.SONGBIRD, WildKinds.DUCK -> 0.5F;
            case WildKinds.WOODPECKER, WildKinds.SQUIRREL -> 0.7F;
            case WildKinds.HERON -> 0.4F;
            default -> 1F;
        };
    }

    /** What the chunk offers this kind (0..1): perches for songbirds, trunks for woodpeckers, flowers for bees... */
    public static float habitat(int kind, Habitat h) {
        float open = 1F - h.canopy;
        return switch (kind) {
            // forest edges and open woodland suit small birds best; a deep closed canopy less; open land only its fences
            case WildKinds.SONGBIRD -> Math.min(1F, h.perches / 4F) * (h.canopy < 0.05F ? 0.3F : h.canopy > 0.85F ? 0.6F : 1F);
            case WildKinds.WOODPECKER -> h.canopy < 0.25F ? 0F : Math.min(1F, h.trunks / 2F);
            case WildKinds.CROW -> h.ground == 0 ? 0F : open * Math.min(1F, h.ground / 4F);
            case WildKinds.OWL -> h.canopy < 0.15F && h.trunks == 0 ? 0F : Math.min(1F, (h.perches + h.trunks) / 3F);
            case WildKinds.RAPTOR -> open < 0.4F ? 0F : open;
            case WildKinds.GULL -> h.water == 0 && h.shore == 0 ? 0F : Math.min(1F, (h.water + h.shore * 2) / 16F);
            case WildKinds.DUCK -> h.water < 6 ? 0F : Math.min(1F, h.water / 24F);
            case WildKinds.HERON -> h.shore < 3 ? 0F : Math.min(1F, h.shore / 8F);
            case WildKinds.SQUIRREL -> h.trunks == 0 ? 0F : Math.min(1F, h.trunks / 3F);
            case WildKinds.RABBIT -> h.ground == 0 ? 0F : (h.canopy > 0.1F && h.canopy < 0.55F ? 1F : h.canopy <= 0.1F ? 0.7F : 0.25F);
            case WildKinds.FOX -> h.ground == 0 ? 0F : 1F;
            case WildKinds.FROG -> h.shore < 2 ? 0F : Math.min(1F, h.shore / 6F);
            case WildKinds.BUTTERFLY -> open * (0.06F + Math.min(1F, h.flowers / 8F));
            case WildKinds.BEE -> h.flowers < 2 ? 0F : open * Math.min(1F, h.flowers / 10F);
            case WildKinds.DRAGONFLY -> h.water < 4 ? 0F : Math.min(1F, h.water / 16F);
            case WildKinds.FIREFLY -> h.ground == 0 && h.canopy < 0.1F ? 0.2F : (0.5F + 0.5F * Math.min(1F, h.canopy * 1.6F)) * (h.shore > 0 ? 1.3F : 1F);
            case WildKinds.MOTH -> Math.min(3, h.lights) + (h.lights == 0 ? 0.04F : 0F);
            case WildKinds.GNATS -> h.water < 4 ? 0F : 1F;
            case WildKinds.STRIDER -> h.water < 4 ? 0F : Math.min(1F, h.water / 12F);
            default -> 0F;
        };
    }

    /** The expected number of a kind living in one chunk now (0 where the family has none of it). */
    public static float lambda(int kind, Habitat h, int tod, float rain, float thunder) {
        if (h.family < 0 || h.family >= Families.COUNT) return 0F;
        float a = AFFINITY[kind][h.family];
        if (a <= 0F) return 0F;
        return PEAK[kind] * a * hour(kind, tod) * weather(kind, rain, thunder) * cold(kind, h.temperature) * habitat(kind, h)
                * variant(kind, h.variant);
    }

    /**
     * How many of a kind a chunk holds: the expected number made whole by the chunk's own fixed draw, so the same
     * chunk keeps the same animals as the hours turn (one more appears as the number crosses its draw, one goes as it
     * falls back), and the land as a whole averages the expected number.
     */
    public static int count(float lambda, long chunk, int kind) {
        if (lambda <= 0F) return 0;
        return (int) Math.floor(lambda + draw(chunk, kind, 0));
    }

    /** A fixed number in [0, 1) for a chunk, a kind and a slot: where a chunk's animals live and what they are. */
    public static float draw(long chunk, int kind, int slot) {
        long h = chunk * 0x9E3779B97F4A7C15L + kind * 0xC2B2AE3D27D4EB4FL + slot * 0x165667B19E3779F9L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return (h >>> 40) / (float) (1L << 24);
    }
}
