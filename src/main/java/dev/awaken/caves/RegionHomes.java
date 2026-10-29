package dev.awaken.caves;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.delve.Ore;
import dev.awaken.delve.api.Lodes;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * The cave regions as the lodes' homes (SPEC §42.9): a region favours the ores that live in its rock (its twins' ores, and
 * the Violet Deep's Deepglass), so a lode rolled outside its ore's regions rolls once more ({@code LodePlans}) and about
 * twice the lodes of a metal lie in its regions; the Hearth names the region a mouth leads down into. The region at a point
 * is read from the world's noise as the pack's injectors read it (the band's field and the land's climate), so it answers
 * for chunks nobody has made, on any thread. Only for a world made with the regions since this (a P2 or P3 world keeps
 * the plans its chunks were laid by): the {@link Saved} record says which.
 */
public final class RegionHomes implements Lodes.Homes {
    private final RandomState rs;
    private final NoiseRouter router;
    private final DensityFunction[] fields;

    RegionHomes(RandomState rs, NoiseRouter router, DensityFunction[] fields) {
        this.rs = rs;
        this.router = router;
        this.fields = fields;
    }

    /** The overworld's regions, or null when its generator or the pack's band fields aren't there. */
    static @Nullable RegionHomes of(ServerLevel level) {
        if (!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator gen)) return null;
        return of(level.registryAccess(), level.getChunkSource().randomState(), gen.generatorSettings().value().noiseRouter());
    }

    /** The regions of a world with this noise (the tests' flat world has none of its own), or null without the pack's fields. */
    static @Nullable RegionHomes of(RegistryAccess access, RandomState rs, NoiseRouter router) {
        var functions = access.lookupOrThrow(Registries.DENSITY_FUNCTION);
        DensityFunction[] fields = new DensityFunction[3];
        for (CaveRegion.Band band : CaveRegion.Band.values()) {
            var f = functions.get(ResourceKey.create(Registries.DENSITY_FUNCTION, Awaken.id("caves/" + band.key())));
            if (f.isEmpty()) return null;
            fields[band.ordinal()] = f.get().value();
        }
        return new RegionHomes(rs, router, fields);
    }

    /** The region at a point, or null for a plain cave or no cave depth (seven noise samples, none cached). */
    public @Nullable CaveRegion at(int x, int y, int z) {
        if (y > CaveRegion.MAX_Y) return null;
        float depth = rs.sampleBlockValueUncached(router.depth(), x, y, z);
        if (depth < CaveRegion.MIN_DEPTH) return null;
        CaveRegion.Band band = CaveRegion.Band.at(y, depth);
        return CaveRegion.at(y, depth, rs.sampleBlockValueUncached(fields[band.ordinal()], x, 0, z),
                rs.sampleBlockValueUncached(router.vegetation(), x, 0, z), rs.sampleBlockValueUncached(router.temperature(), x, 0, z),
                rs.sampleBlockValueUncached(router.continents(), x, 0, z), rs.sampleBlockValueUncached(router.erosion(), x, 0, z));
    }

    @Override
    public boolean favours(Ore ore, int x, int y, int z) {
        if (!HOMED.contains(ore)) return false;
        CaveRegion r = at(x, y, z);
        return r != null && ores(r).contains(ore);
    }

    @Override
    public @Nullable String place(int x, int y, int z) {
        CaveRegion r = at(x, y, z);
        // as a sentence says it: "the Ochre Deep" (every title starts with "The")
        return r == null ? null : "t" + r.title().substring(1);
    }

    /**
     * The ores at home in a region: the ones its rock has twins for, and Deepglass in the Violet Deep (its seeps are there).
     * An ore's own band decides how often that matters: redstone lies below y -4 and the Glimmer Hollows above y 0, so the
     * Hollows' redstone stays vanilla's; lapis and gold meet their Middle Dark regions only from y 0 to 8 and 16.
     */
    public static Set<Ore> ores(CaveRegion r) {
        return switch (r) {
            case ROOTWAYS -> EnumSet.of(Ore.COAL, Ore.COPPER);
            case PALE_HALLS, OCHRE_DEEP -> EnumSet.of(Ore.IRON);
            case WINDCLEFT -> EnumSet.of(Ore.GOLD);
            case DROWNED_GALLERIES -> EnumSet.of(Ore.LAPIS, Ore.COPPER);
            case GLIMMER_HOLLOWS -> EnumSet.of(Ore.REDSTONE);
            case BLACK_VAULTS -> EnumSet.of(Ore.DIAMOND);
            case VIOLET_DEEP -> EnumSet.of(Ore.DEEPGLASS);
        };
    }

    private static final Set<Ore> HOMED = EnumSet.noneOf(Ore.class);

    static {
        for (CaveRegion r : CaveRegion.values()) HOMED.addAll(ores(r));
    }

    // ---- which worlds -------------------------------------------------------------------------------------------------

    /** The world was made with the cave regions (its feature flags include awaken:awakened_caves). */
    static boolean flagged(MinecraftServer server) {
        FeatureFlagSet set = FeatureFlags.REGISTRY.fromNames(Set.of(Awaken.id("awakened_caves")), id -> {});
        return !set.isEmpty() && set.isSubsetOf(server.getWorldData().enabledFeatures());
    }

    /** On the overworld's load: a regions world new since lodes by region gets its homes; any other keeps its plans. */
    static void load(ServerLevel overworld) {
        // the test world loads the pack too: it keeps the plans delve's tests stand on (the region tests ask planWith)
        if (!flagged(overworld.getServer()) || overworld.getServer() instanceof GameTestServer) return;
        Saved rec = overworld.getDataStorage().computeIfAbsent(Saved.TYPE);
        if (rec.fresh) {
            // no record: a world made just now (its clock not started), or a regions world from before P4
            rec.fresh = false;
            rec.homes = overworld.getGameTime() == 0;
            rec.setDirty();
            Awaken.LOG.info("[caves] lodes by region {} for this world ({})", rec.homes ? "on" : "off", rec.homes ? "new" : "made before P4");
        }
        if (!rec.homes) return;
        RegionHomes homes = of(overworld);
        if (homes != null) Lodes.homes(overworld.getSeed(), homes);
        else Awaken.LOG.warn("[caves] lodes by region: the regions' fields aren't in this world, lodes stay as they are");
    }

    /** What the caves keep for a world ({@code awaken:caves}, schema 1): whether its lodes follow the regions. */
    static final class Saved extends SavedData {
        static final int SCHEMA = 1;
        static final Codec<Saved> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(r -> SCHEMA),
                Codec.BOOL.optionalFieldOf("lodes_by_region", false).forGetter(r -> r.homes)
        ).apply(i, (schema, homes) -> new Saved(homes)));
        static final SavedDataType<Saved> TYPE = new SavedDataType<>(Awaken.id("caves"), Saved::new, CODEC);

        boolean homes;
        /** Made just now (no record on disk). */
        transient boolean fresh;

        Saved() {
            fresh = true;
        }

        private Saved(boolean homes) {
            this.homes = homes;
        }
    }
}
