package dev.awaken.living.region;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.terrain.api.BiomeTable;
import dev.awaken.terrain.api.CityBasin;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * The world's region map (SPEC §21.2, §21.10), one per server. Its centre is fixed the first time it is asked for and saved
 * ({@code awaken:living_regions}, schema 2), so moving the world spawn later never reshuffles the map: Carillon's site in
 * a Carillon world, otherwise the world spawn. The heart probe reads the land from the biome source, so it works for
 * chunks nobody has generated yet. {@code rules} says which rules named the world's regions: absent (a save from before
 * §21.10) is 1 and keeps the old names; a new world is {@link RegionMap#RULES}.
 */
public final class RegionService extends SavedData {
    static final int SCHEMA = 2;

    public static final Codec<RegionService> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(r -> SCHEMA),
            Codec.BOOL.optionalFieldOf("fixed", false).forGetter(r -> r.fixed),
            Codec.INT.optionalFieldOf("centre_x", 0).forGetter(r -> r.centreX),
            Codec.INT.optionalFieldOf("centre_z", 0).forGetter(r -> r.centreZ),
            Codec.INT.optionalFieldOf("rules", 1).forGetter(r -> r.rules)
    ).apply(i, (schema, fixed, x, z, rules) -> new RegionService(fixed, x, z, rules)));

    public static final SavedDataType<RegionService> TYPE = new SavedDataType<>(Awaken.id("living_regions"), RegionService::new, CODEC);

    private boolean fixed;
    private int centreX, centreZ;
    private int rules = RegionMap.RULES;
    private transient @Nullable RegionMap map;

    /** Which rules named this world's regions (SPEC §21.10): 1 a save from before, {@link RegionMap#RULES} a new world. */
    public int rules() {
        return rules;
    }

    /** A world with no record yet is a new one: made under the current rules. */
    public RegionService() {}

    private RegionService(boolean fixed, int x, int z, int rules) {
        this.fixed = fixed;
        this.rules = rules;
        this.centreX = x;
        this.centreZ = z;
    }

    private static volatile @Nullable RegionService current;
    private static volatile @Nullable MinecraftServer currentServer;

    /** The map of this server's overworld (made on first use). */
    public static RegionMap map(MinecraftServer server) {
        RegionService s = current;
        if (s == null || currentServer != server || s.map == null) {
            synchronized (RegionService.class) {
                s = server.overworld().getDataStorage().computeIfAbsent(TYPE);
                if (!s.fixed) {
                    var site = CityBasin.site(server);
                    var spawn = server.getRespawnData().globalPos().pos();
                    s.centreX = site.map(c -> c.x()).orElse(spawn.getX());
                    s.centreZ = site.map(c -> c.z()).orElse(spawn.getZ());
                    s.fixed = true;
                    s.setDirty();
                    Awaken.LOG.info("[living] region map centred at {} {} ({})", s.centreX, s.centreZ, site.isPresent() ? "Carillon" : "world spawn");
                }
                if (s.map == null) {
                    ServerLevel level = server.overworld();
                    s.map = new RegionMap(level.getSeed(), s.centreX, s.centreZ, (x, z) -> land(level, x, z), s.rules);
                }
                current = s;
                currentServer = server;
            }
        }
        return s.map;
    }

    /**
     * Which rules this world's regions are named under, read from the save before the overworld loads (the roads' world
     * opens that early): no record means a new world (the current rules), a record without the field is 1.
     */
    public static int rulesOnDisk(MinecraftServer server) {
        try {
            java.nio.file.Path f = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.DATA).getParent()
                    .resolve("dimensions/minecraft/overworld/data/awaken/living_regions.dat");
            if (!java.nio.file.Files.exists(f)) return RegionMap.RULES;
            var tag = net.minecraft.nbt.NbtIo.readCompressed(f, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            return tag.getCompoundOrEmpty("data").getIntOr("rules", 1);
        } catch (Exception e) {
            Awaken.LOG.warn("[living] could not read the region record's rules, keeping the old names: {}", e.toString());
            return 1;
        }
    }

    /** Forgets the cached map (server stop). */
    public static void reset() {
        current = null;
        currentServer = null;
    }

    /** The land at a heart, from the biome source (quart coordinates at sea level). */
    static RegionMap.Land land(ServerLevel level, int x, int z) {
        Holder<Biome> b = level.getUncachedNoiseBiome(x >> 2, 63 >> 2, z >> 2);
        ResourceKey<Biome> archetype = BiomeTable.archetype(b);
        String path = archetype != null ? archetype.identifier().getPath() : b.unwrapKey().map(k -> k.identifier().getPath()).orElse("plains");
        return land(path);
    }

    /** A vanilla biome path's name group and tilt (harsh one band up, gentle one band down). */
    public static RegionMap.Land land(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        RegionMap.Group g;
        if (p.contains("mushroom")) g = RegionMap.Group.MUSHROOM;
        else if (p.contains("ocean") || p.contains("river") || p.contains("beach") || p.contains("shore")) g = RegionMap.Group.OCEAN;
        else if (p.contains("desert")) g = RegionMap.Group.DESERT;
        else if (p.contains("badlands")) g = RegionMap.Group.BADLANDS;
        else if (p.contains("swamp") || p.contains("mangrove")) g = RegionMap.Group.SWAMP;
        else if (p.contains("jungle") || p.contains("bamboo")) g = RegionMap.Group.JUNGLE;
        else if (p.contains("pale_garden") || p.contains("dark_forest")) g = RegionMap.Group.DARK;
        else if (p.contains("peaks") || p.contains("slopes") || p.contains("stony") || p.contains("windswept")) g = RegionMap.Group.MOUNTAIN;
        else if (p.contains("snowy") || p.contains("frozen") || p.contains("ice") || p.contains("grove")) g = RegionMap.Group.SNOW;
        else if (p.contains("plains") || p.contains("meadow") || p.contains("savanna")) g = RegionMap.Group.PLAINS;
        else g = RegionMap.Group.FOREST;
        int tilt = 0;
        if (p.equals("pale_garden") || p.contains("badlands") || p.equals("jagged_peaks") || p.equals("frozen_peaks") || p.equals("dark_forest")) tilt = 1;
        else if (p.equals("plains") || p.equals("sunflower_plains") || p.equals("meadow") || p.equals("flower_forest")
                || p.equals("cherry_grove") || p.equals("mushroom_fields")) tilt = -1;
        return new RegionMap.Land(g, tilt);
    }
}
