package dev.awaken.ground;

import dev.awaken.terrain.block.BlockSet;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.List;

/**
 * The slabs of natural ground (SPEC §31, P1): the half-block steps the soft pass lays. The ones vanilla has no slab for
 * (grass, dirt, coarse dirt, podzol, mud, gravel, sand, red sand); stone and sandstone steps use vanilla's own slabs.
 * Blocks exist in every world; only worlds made with the {@code awaken:awakened_ground} flag grow them. Blockstates, models,
 * loot and lang are written by tools/content/ground_content.py.
 */
public final class SoftSlabs {
    private SoftSlabs() {}

    private static final BlockSet SET = new BlockSet();

    public static final DeferredBlock<SoftSlabBlock> DIRT_SLAB = slab("dirt_slab", MapColor.DIRT, SoundType.GRAVEL, 0.5F);
    public static final DeferredBlock<SoftSlabBlock> COARSE_DIRT_SLAB = slab("coarse_dirt_slab", MapColor.DIRT, SoundType.GRAVEL, 0.5F);
    public static final DeferredBlock<SoftSlabBlock> PODZOL_SLAB = slab("podzol_slab", MapColor.PODZOL, SoundType.GRAVEL, 0.5F);
    public static final DeferredBlock<SoftSlabBlock> MUD_SLAB = slab("mud_slab", MapColor.TERRACOTTA_CYAN, SoundType.MUD, 0.5F);
    public static final DeferredBlock<SoftSlabBlock> GRAVEL_SLAB = slab("gravel_slab", MapColor.STONE, SoundType.GRAVEL, 0.6F);
    public static final DeferredBlock<SoftSlabBlock> SAND_SLAB = slab("sand_slab", MapColor.SAND, SoundType.SAND, 0.5F);
    public static final DeferredBlock<SoftSlabBlock> RED_SAND_SLAB = slab("red_sand_slab", MapColor.COLOR_ORANGE, SoundType.SAND, 0.5F);
    public static final DeferredBlock<TurfSlabBlock> TURF_SLAB = SET.withItem(SET.blocks.registerBlock("turf_slab",
            p -> new TurfSlabBlock(p, DIRT_SLAB),
            p -> p.mapColor(MapColor.GRASS).strength(0.6F).sound(SoundType.GRASS).randomTicks()));

    private static DeferredBlock<SoftSlabBlock> slab(String name, MapColor color, SoundType sound, float strength) {
        return SET.withItem(SET.blocks.registerBlock(name, SoftSlabBlock::new, p -> p.mapColor(color).strength(strength).sound(sound)));
    }

    /** Every soft slab, in creative-tab order. */
    public static List<DeferredBlock<? extends Block>> all() {
        return List.of(TURF_SLAB, DIRT_SLAB, COARSE_DIRT_SLAB, PODZOL_SLAB, MUD_SLAB, GRAVEL_SLAB, SAND_SLAB, RED_SAND_SLAB);
    }

    static void init(IEventBus modBus) {
        SET.init(modBus);
    }
}
