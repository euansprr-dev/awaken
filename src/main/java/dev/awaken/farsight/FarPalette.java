package dev.awaken.farsight;

import dev.awaken.terrain.api.BiomeTable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a biome's ground looks like from kilometres away (SPEC §20): one colour per biome, worked out once from its
 * archetype (an Awakened biome stands in for a vanilla one, {@link BiomeTable#archetype}) and its own grass and foliage
 * colours. Forests read darker than open grass because the canopy is what shows. Snow, rock on steep slopes and
 * sun shading are applied per cell and per pixel, not here. Real chunks' top blocks ({@link #block}) take their map
 * colour, with grass, leaves and water tinted by their biome the same way. Thread-safe: the workers share it.
 */
public final class FarPalette {
    private FarPalette() {}

    static final int SAND = 0xD9CB94, RED_SAND = 0xB4673C, TERRACOTTA = 0xA4593A, SNOW = 0xEEF2F5, STONE = 0x8C8C8C,
            GRAVEL = 0x8A8682, MYCELIUM = 0x77686E, SEABED = 0x9C916E, PALE = 0x8D9389;
    /** Grass where the client's colour maps aren't loaded (a dedicated server's GameTests). */
    static final int TEMPERATE = 0x79C05A;

    private static final Map<Holder<Biome>, Integer> GROUND = new ConcurrentHashMap<>();
    /** Per biome: its lawn (grass blocks) and leaves colour, packed as lawn << 32 | leaves. */
    private static final Map<Holder<Biome>, Long> TINTS = new ConcurrentHashMap<>();

    /** The biome's ground colour, 0xRRGGBB. */
    public static int ground(Holder<Biome> biome) {
        return look(biome) & 0xFFFFFF;
    }

    /** How far its canopy stands above the ground (blocks): what shows of a forest from far away is its treetops. */
    public static int canopy(Holder<Biome> biome) {
        return look(biome) >>> 24;
    }

    private static int look(Holder<Biome> biome) {
        Integer c = GROUND.get(biome);
        if (c == null) {
            c = compute(biome);
            GROUND.put(biome, c);
        }
        return c;
    }

    /** The biome's water colour, 0xRRGGBB, darkened the way deep water reads from afar. */
    public static int water(Holder<Biome> biome) {
        return scale(biome.value().getWaterColor() & 0xFFFFFF, 0.62f);
    }

    /** Forgets every colour (a resource reload can change the colour maps). */
    public static void clear() {
        GROUND.clear();
        TINTS.clear();
    }

    /**
     * A real top block's colour from afar, 0xRRGGBB, or -1 for air: its map colour, but grass, leaves and water tinted
     * by the biome as the game tints them (map colours are flat).
     */
    public static int block(BlockState state, BlockGetter level, BlockPos pos, Holder<Biome> biome) {
        MapColor mc = state.getMapColor(level, pos);
        if (mc == MapColor.NONE) return -1;
        if (mc == MapColor.WATER) return water(biome);
        if (mc == MapColor.GRASS || mc == MapColor.PLANT) {
            Long t = TINTS.get(biome);
            if (t == null) {
                int grass = grass(biome.value()), foliage = foliage(biome.value(), grass);
                t = (long) scale(grass, 0.68f) << 32 | scale(foliage, 0.56f) & 0xFFFFFFFFL;
                TINTS.put(biome, t);
            }
            return mc == MapColor.GRASS ? (int) (t >>> 32) : (int) (long) t;
        }
        return scale(mc.col, 0.85f);
    }

    private static int grass(Biome b) {
        int grass = b.getGrassColor(0, 0) & 0xFFFFFF;
        return grass == 0 || grass == 0xFF00FF ? TEMPERATE : grass;
    }

    private static int foliage(Biome b, int grass) {
        int foliage = b.getFoliageColor() & 0xFFFFFF;
        return foliage == 0 || foliage == 0xFF00FF ? grass : foliage;
    }

    private static int compute(Holder<Biome> holder) {
        Biome b = holder.value();
        ResourceKey<Biome> key = BiomeTable.archetype(holder);
        String a = key == null ? "" : key.identifier().getPath();
        int grass = grass(b), foliage = foliage(b, grass);
        // the grass and leaf textures are grey tinted by these colours: their average is about this bright
        int lawn = scale(grass, 0.68f), canopy = scale(foliage, 0.56f);
        if (a.contains("badlands")) return a.startsWith("eroded") ? TERRACOTTA : RED_SAND;
        if (a.equals("desert")) return SAND;
        if (a.equals("beach")) return 0xE0D6A4;
        if (a.equals("snowy_beach")) return 0xE3E8E8;
        if (a.equals("stony_shore") || a.equals("stony_peaks")) return STONE;
        if (a.contains("ocean") || a.contains("river")) return a.startsWith("frozen") ? SNOW : SEABED;
        if (a.equals("ice_spikes") || a.equals("snowy_plains") || a.equals("snowy_slopes") || a.equals("frozen_peaks")
                || a.equals("jagged_peaks")) return SNOW;
        if (a.equals("snowy_taiga") || a.equals("grove")) return trees(7, mix(SNOW, canopy, 0.35f));
        if (a.equals("mushroom_fields")) return MYCELIUM;
        if (a.equals("windswept_gravelly_hills")) return GRAVEL;
        if (a.equals("windswept_hills")) return mix(lawn, STONE, 0.35f);
        if (a.equals("pale_garden")) return PALE;
        if (a.equals("cherry_grove")) return trees(5, mix(0xD9A3B8, lawn, 0.45f));
        if (a.equals("dark_forest")) return trees(8, scale(canopy, 0.8f));
        if (a.contains("jungle")) return trees(10, mix(canopy, lawn, 0.3f));
        if (a.contains("forest") || a.contains("taiga")) return trees(6, mix(canopy, lawn, 0.3f));
        if (a.contains("swamp")) return trees(3, mix(canopy, lawn, 0.3f));
        if (a.startsWith("savanna")) return scale(grass, 0.8f);
        return lawn;
    }

    private static int trees(int height, int rgb) {
        return height << 24 | rgb;
    }

    static int scale(int rgb, float k) {
        int r = Math.min(255, Math.round((rgb >> 16 & 255) * k)), g = Math.min(255, Math.round((rgb >> 8 & 255) * k)),
                bl = Math.min(255, Math.round((rgb & 255) * k));
        return r << 16 | g << 8 | bl;
    }

    /** {@code t} of {@code x}, the rest {@code y}. */
    static int mix(int x, int y, float t) {
        int r = Math.round((x >> 16 & 255) * t + (y >> 16 & 255) * (1 - t)), g = Math.round((x >> 8 & 255) * t + (y >> 8 & 255) * (1 - t)),
                bl = Math.round((x & 255) * t + (y & 255) * (1 - t));
        return r << 16 | g << 8 | bl;
    }
}
