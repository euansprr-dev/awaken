package dev.awaken.farsight;

import dev.awaken.terrain.api.BiomeTable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a biome's ground looks like from kilometres away (SPEC §20): one colour per biome, worked out once from its
 * archetype (an Awakened biome stands in for a vanilla one, {@link BiomeTable#archetype}) and its own grass and foliage
 * colours, and its forest: how densely trees stand, how tall, what shape, what colour (Far Sight 2 draws them as
 * trees, not as a darker ground). Snow, rock on steep slopes and sun shading are applied per pixel, not here. Real chunks' top blocks ({@link #block}) take their map
 * colour, with grass, leaves and water tinted by their biome the same way. Thread-safe: the workers share it.
 */
public final class FarPalette {
    private FarPalette() {}

    static final int SAND = 0xD9CB94, RED_SAND = 0xB4673C, TERRACOTTA = 0xA4593A, SNOW = 0xEEF2F5, STONE = 0x8C8C8C,
            GRAVEL = 0x8A8682, MYCELIUM = 0x77686E, SEABED = 0x9C916E, PALE = 0x8D9389;
    /** Grass where the client's colour maps aren't loaded (a dedicated server's GameTests). */
    static final int TEMPERATE = 0x79C05A;

    /**
     * A biome from afar: its ground colour; its forest (how densely trees stand, 0..255, how tall their canopy is in
     * blocks, their shape, and the crown colour); its flags ({@link #BADLANDS}, {@link #STONY}); and the vanilla biome
     * it stands for (its archetype's path).
     */
    public record Look(int ground, int density, int canopy, int shape, int crown, int flags, String archetype) {}

    /** Tree shapes the far forests draw: round broadleaf crowns, conifers, tall jungle trees, flat acacia tops. */
    public static final int BROADLEAF = 0, CONIFER = 1, JUNGLE = 2, ACACIA = 3;
    /** Look flags. Bits 4-6: the rock its steep faces show (0 stone, 1 hoarfrost, 2 riven shale, 3 fused glass, 4 mirepeat). */
    public static final int BADLANDS = 4, STONY = 8;
    static final int HOARFROST = 1 << 4, SHALE = 2 << 4, GLASS = 3 << 4, MIREPEAT = 4 << 4;

    private static final Map<Holder<Biome>, Look> GROUND = new ConcurrentHashMap<>();
    /** Per biome: its lawn (grass blocks) and leaves colour, packed as lawn << 32 | leaves. */
    private static final Map<Holder<Biome>, Long> TINTS = new ConcurrentHashMap<>();

    /** The biome's ground colour, 0xRRGGBB. */
    public static int ground(Holder<Biome> biome) {
        return look(biome).ground();
    }

    public static Look look(Holder<Biome> biome) {
        Look c = GROUND.get(biome);
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

    private static Look compute(Holder<Biome> holder) {
        Biome b = holder.value();
        ResourceKey<Biome> key = BiomeTable.archetype(holder);
        String a = key == null ? "" : key.identifier().getPath();
        int grass = grass(b), foliage = foliage(b, grass);
        // the grass and leaf textures are grey tinted by these colours: their average is about this bright
        int lawn = scale(grass, 0.68f), leaves = scale(foliage, 0.56f);
        // the Awakened biomes cover their ground (cliffs too) in their own blocks: the far ground wears them (the
        // blocks' textures' average colours) over the archetype's forest
        String own = holder.unwrapKey().filter(k -> k.identifier().getNamespace().equals("awaken")).map(k -> k.identifier().getPath()).orElse("");
        Look base = compute(a, grass, lawn, leaves);
        return switch (own) {
            case "frost_spires" -> new Look(mix(0xAECCDC, lawn, 0.75f), base.density(), base.canopy(), base.shape(), base.crown(), HOARFROST, a);
            case "shattered_highlands" -> new Look(mix(0x3B3B47, lawn, 0.55f), base.density(), base.canopy(), base.shape(), base.crown(), SHALE, a);
            case "glass_steppe" -> new Look(mix(0xC8BDA7, lawn, 0.5f), base.density(), base.canopy(), base.shape(), base.crown(), GLASS, a);
            case "mirror_marsh" -> new Look(mix(0x3D382F, lawn, 0.5f), base.density(), base.canopy(), base.shape(), base.crown(), MIREPEAT, a);
            default -> base;
        };
    }

    /** Spruce leaves wear the game's fixed evergreen tint whatever the biome (FoliageColor.FOLIAGE_EVERGREEN), darker than its foliage. */
    private static final int SPRUCE = scale(FoliageColor.FOLIAGE_EVERGREEN & 0xFFFFFF, 0.56f);

    private static Look compute(String a, int grass, int lawn, int leaves) {
        if (a.contains("badlands")) {
            int ground = a.startsWith("eroded") ? TERRACOTTA : RED_SAND;
            return a.startsWith("wooded") ? new Look(ground, 40, 6, BROADLEAF, leaves, BADLANDS, a) : new Look(ground, 0, 0, 0, 0, BADLANDS, a);
        }
        if (a.equals("desert")) return open(SAND, a);
        if (a.equals("beach")) return open(0xE0D6A4, a);
        if (a.equals("snowy_beach")) return open(0xE3E8E8, a);
        if (a.equals("stony_shore") || a.equals("stony_peaks")) return new Look(STONE, 0, 0, 0, 0, STONY, a);
        if (a.contains("ocean") || a.contains("river")) return open(a.startsWith("frozen") ? SNOW : SEABED, a);
        if (a.equals("ice_spikes") || a.equals("snowy_plains") || a.equals("snowy_slopes") || a.equals("frozen_peaks")
                || a.equals("jagged_peaks")) return open(SNOW, a);
        if (a.equals("snowy_taiga")) return new Look(mix(SNOW, lawn, 0.6f), 150, 8, CONIFER, mix(SPRUCE, SNOW, 0.75f), 0, a);
        if (a.equals("grove")) return new Look(SNOW, 140, 9, CONIFER, mix(SPRUCE, SNOW, 0.75f), 0, a);
        if (a.equals("mushroom_fields")) return open(MYCELIUM, a);
        if (a.equals("windswept_gravelly_hills")) return new Look(GRAVEL, 20, 7, CONIFER, SPRUCE, STONY, a);
        if (a.equals("windswept_hills")) return new Look(mix(lawn, STONE, 0.65f), 25, 7, CONIFER, SPRUCE, STONY, a);
        if (a.equals("windswept_forest")) return new Look(mix(lawn, STONE, 0.65f), 150, 8, CONIFER, SPRUCE, 0, a);
        if (a.equals("windswept_savanna")) return new Look(scale(grass, 0.8f), 30, 6, ACACIA, leaves, 0, a);
        if (a.equals("pale_garden")) return new Look(PALE, 210, 9, BROADLEAF, 0x8F9590, 0, a);
        if (a.equals("cherry_grove")) return new Look(lawn, 150, 6, BROADLEAF, 0xD9A3B8, 0, a);
        if (a.equals("dark_forest")) return new Look(scale(lawn, 0.8f), 235, 8, BROADLEAF, scale(leaves, 0.85f), 0, a);
        if (a.equals("sparse_jungle")) return new Look(lawn, 90, 12, JUNGLE, leaves, 0, a);
        if (a.contains("jungle")) return new Look(lawn, 230, 16, JUNGLE, leaves, 0, a);
        if (a.equals("old_growth_pine_taiga") || a.equals("old_growth_spruce_taiga")) return new Look(scale(lawn, 0.9f), 220, 16, CONIFER, SPRUCE, 0, a);
        if (a.contains("taiga")) return new Look(lawn, 190, 8, CONIFER, SPRUCE, 0, a);
        if (a.equals("mangrove_swamp")) return new Look(scale(lawn, 0.85f), 200, 8, BROADLEAF, leaves, 0, a);
        if (a.contains("swamp")) return new Look(scale(lawn, 0.85f), 110, 6, BROADLEAF, leaves, 0, a);
        if (a.contains("forest")) return new Look(lawn, a.equals("flower_forest") ? 140 : 200, 6, BROADLEAF, leaves, 0, a);
        if (a.startsWith("savanna")) return new Look(scale(grass, 0.8f), 30, 6, ACACIA, leaves, 0, a);
        if (a.equals("meadow")) return new Look(lawn, 8, 8, CONIFER, SPRUCE, 0, a);
        if (a.equals("plains") || a.equals("sunflower_plains")) return new Look(lawn, 6, 6, BROADLEAF, leaves, 0, a);
        return open(lawn, a);
    }

    private static Look open(int rgb, String a) {
        return new Look(rgb, 0, 0, 0, 0, 0, a);
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
