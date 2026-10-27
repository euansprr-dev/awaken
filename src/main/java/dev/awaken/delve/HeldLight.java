package dev.awaken.delve;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.MapColor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Light carried in a hand (SPEC §28.3, "Held light"): which held items light the world round their bearer, how far and in
 * what colour. Any block item whose block's default state gives light does (a torch, a lantern, glowstone, our own lit
 * blocks); its strength is that light level and its colour comes from its kind. The client draws it
 * ({@code delve.client.TorchGlow}); nothing is placed. Pure rules, cached per item.
 */
public final class HeldLight {
    private HeldLight() {}

    /** A held light: its level (1-15), its colour (0xRRGGBB) and whether it is a flame (a flame flickers). */
    public record Kind(int emission, int rgb, boolean flame) {
        /** How far its light reaches (blocks): three quarters of its level, a torch's 14 reaching 10.5. */
        public float reach() {
            return Math.min(MAX_REACH, emission * REACH_PER_LEVEL);
        }
    }

    public static final Kind NONE = new Kind(0, 0, false);
    public static final float REACH_PER_LEVEL = 0.75F, MAX_REACH = 11.25F;

    // the kinds' colours, as light (pale: a tint on the lit stone, not paint)
    public static final int FLAME = 0xFFC48A, SOUL = 0x86D2FF, REDSTONE = 0xFF5A3C, COPPER = 0xA8F2B6, SEA = 0xC2E9FF,
            GLOWSTONE = 0xFFDA94, SHROOM = 0xFFB070, END_ROD = 0xF2EEFF, AMETHYST = 0xCDA8FF, MAGMA = 0xFF8C44,
            LICHEN = 0xC4F0CC, OCHRE = 0xFFE4A0, VERDANT = 0xCDF5A6, PEARL = 0xF5CCEE;

    private static final Map<Item, Kind> CACHE = new ConcurrentHashMap<>();

    /** The light an item gives held, or {@link #NONE}. */
    public static Kind of(ItemStack s) {
        if (s.isEmpty()) return NONE;
        return CACHE.computeIfAbsent(s.getItem(), HeldLight::classify);
    }

    /** True when the off hand's light is the one carried (the brighter of the two; the main hand on a tie). */
    public static boolean offhandWins(ItemStack main, ItemStack off) {
        return of(off).emission() > of(main).emission();
    }

    private static Kind classify(Item item) {
        if (!(item instanceof BlockItem bi)) return NONE;
        Block b = bi.getBlock();
        int e = b.defaultBlockState().getLightEmission();
        if (e <= 0) return NONE;
        String id = BuiltInRegistries.BLOCK.getKey(b).getPath();
        if (id.contains("soul")) return new Kind(e, SOUL, flame(id));
        if (id.contains("redstone")) return new Kind(e, REDSTONE, true);
        if (id.contains("copper") && (id.contains("torch") || id.contains("lantern"))) return new Kind(e, COPPER, true);
        if (b == Blocks.SEA_LANTERN || id.contains("prismarine") || b == Blocks.CONDUIT || b == Blocks.BEACON) return new Kind(e, SEA, false);
        if (b == Blocks.GLOWSTONE) return new Kind(e, GLOWSTONE, false);
        if (b == Blocks.SHROOMLIGHT) return new Kind(e, SHROOM, false);
        if (b == Blocks.END_ROD) return new Kind(e, END_ROD, false);
        if (b == Blocks.OCHRE_FROGLIGHT) return new Kind(e, OCHRE, false);
        if (b == Blocks.VERDANT_FROGLIGHT) return new Kind(e, VERDANT, false);
        if (b == Blocks.PEARLESCENT_FROGLIGHT) return new Kind(e, PEARL, false);
        if (id.contains("amethyst") || b == Blocks.CRYING_OBSIDIAN) return new Kind(e, AMETHYST, false);
        if (b == Blocks.MAGMA_BLOCK) return new Kind(e, MAGMA, false);
        if (b == Blocks.GLOW_LICHEN) return new Kind(e, LICHEN, false);
        if (flame(id)) return new Kind(e, FLAME, true);
        // anything else (our own lit stone, glass and metal): its own map colour, lifted halfway to white so it reads as light
        MapColor m = b.defaultMapColor();
        if (m == MapColor.NONE) return new Kind(e, FLAME, true);
        int c = m.col;
        int r = (c >> 16 & 255) + 255 >> 1, g = (c >> 8 & 255) + 255 >> 1, bl = (c & 255) + 255 >> 1;
        return new Kind(e, r << 16 | g << 8 | bl, false);
    }

    private static boolean flame(String id) {
        return id.contains("torch") || id.contains("lantern") || id.contains("fire") || id.contains("candle") || id.contains("lamp")
                || id.contains("brazier");
    }
}
