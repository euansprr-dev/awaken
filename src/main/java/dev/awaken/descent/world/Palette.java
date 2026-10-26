package dev.awaken.descent.world;

import dev.awaken.descent.DescentContent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

/** The blocks one form generates with. Built lazily (registries must be frozen) and immutable after. */
public final class Palette {
    public final BlockState rock, rockAlt, floor, wallGlow, light, platform, platformGlow;
    public final BlockState[] ores;
    public final double oreChance;
    public final ResourceKey<LootTable> cacheLoot;

    private Palette(BlockState rock, BlockState rockAlt, BlockState floor, BlockState wallGlow, BlockState light,
                    BlockState[] ores, double oreChance, ResourceKey<LootTable> cacheLoot) {
        this.rock = rock;
        this.rockAlt = rockAlt;
        this.floor = floor;
        this.wallGlow = wallGlow;
        this.light = light;
        this.platform = DescentContent.WELL_STONE.get().defaultBlockState();
        this.platformGlow = DescentContent.WELL_GLYPH.get().defaultBlockState();
        this.ores = ores;
        this.oreChance = oreChance;
        this.cacheLoot = cacheLoot;
    }

    private static final Palette[] CACHE = new Palette[11];

    public static Palette of(int form) {
        Palette p = CACHE[form];
        if (p == null) CACHE[form] = p = build(form);
        return p;
    }

    private static BlockState s(net.minecraft.world.level.block.Block b) {
        return b.defaultBlockState();
    }

    /**
     * Deepglass (SPEC §28.3): where the Deep presses up through the strata, from the Choir (form 3) down it is one of
     * every wall's ores. By id (the deep module owns it); missing, the walls keep their vanilla three.
     */
    private static BlockState[] withDeepglass(int form, BlockState... ores) {
        var dg = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(dev.awaken.Awaken.id("deepglass"));
        if (form < 3 || dg == Blocks.AIR) return ores;
        BlockState[] out = java.util.Arrays.copyOf(ores, ores.length + 1);
        out[ores.length] = dg.defaultBlockState();
        return out;
    }

    private static Palette build(int form) {
        return switch (form) {
            case 1 -> new Palette(s(DescentContent.RIM_STONE.get()), s(Blocks.TUFF), s(DescentContent.RIM_SOIL.get()),
                    s(DescentContent.RIM_MOSS.get()), s(DescentContent.RIM_MOSS.get()),
                    withDeepglass(form, s(Blocks.DEEPSLATE_COAL_ORE), s(Blocks.DEEPSLATE_IRON_ORE), s(Blocks.DEEPSLATE_COPPER_ORE)), 0.010,
                    BuiltInLootTables.ABANDONED_MINESHAFT);
            case 2 -> new Palette(s(DescentContent.GARDEN_STONE.get()), s(Blocks.CALCITE), s(Blocks.MOSS_BLOCK),
                    s(DescentContent.GARDEN_BLOOM.get()), s(DescentContent.GARDEN_BLOOM.get()),
                    withDeepglass(form, s(Blocks.DEEPSLATE_IRON_ORE), s(Blocks.DEEPSLATE_GOLD_ORE), s(Blocks.DEEPSLATE_LAPIS_ORE)), 0.010,
                    BuiltInLootTables.ABANDONED_MINESHAFT);
            case 3 -> new Palette(s(DescentContent.CHOIR_STONE.get()), s(Blocks.PRISMARINE), s(DescentContent.CHOIR_BRICKS.get()),
                    s(DescentContent.CHOIR_CRYSTAL.get()), s(DescentContent.CHOIR_CRYSTAL.get()),
                    withDeepglass(form, s(Blocks.DEEPSLATE_GOLD_ORE), s(Blocks.DEEPSLATE_LAPIS_ORE), s(Blocks.DEEPSLATE_REDSTONE_ORE)), 0.011,
                    BuiltInLootTables.SIMPLE_DUNGEON);
            case 4 -> new Palette(s(DescentContent.MARROW_STONE.get()), s(Blocks.SMOOTH_BASALT), s(DescentContent.ORCHARD_ASH.get()),
                    s(DescentContent.MARROW_LANTERN.get()), s(DescentContent.MARROW_LANTERN.get()),
                    withDeepglass(form, s(Blocks.DEEPSLATE_REDSTONE_ORE), s(Blocks.DEEPSLATE_IRON_ORE), s(Blocks.DEEPSLATE_DIAMOND_ORE)), 0.011,
                    BuiltInLootTables.SIMPLE_DUNGEON);
            case 5 -> new Palette(s(DescentContent.HUSH_STONE.get()), s(Blocks.DEEPSLATE), s(DescentContent.HUSH_TILES.get()),
                    s(DescentContent.HUSH_LAMP.get()), s(DescentContent.HUSH_LAMP.get()),
                    withDeepglass(form, s(Blocks.DEEPSLATE_DIAMOND_ORE), s(Blocks.DEEPSLATE_EMERALD_ORE), s(Blocks.DEEPSLATE_GOLD_ORE)), 0.009,
                    BuiltInLootTables.ANCIENT_CITY);
            case 6 -> new Palette(s(DescentContent.FOUNDRY_PLATE.get()), s(Blocks.POLISHED_DEEPSLATE), s(DescentContent.FOUNDRY_GRATE.get()),
                    s(DescentContent.FOUNDRY_LAMP.get()), s(DescentContent.FOUNDRY_LAMP.get()),
                    withDeepglass(form, s(Blocks.DEEPSLATE_DIAMOND_ORE), s(Blocks.DEEPSLATE_IRON_ORE), s(Blocks.DEEPSLATE_GOLD_ORE)), 0.012,
                    BuiltInLootTables.ANCIENT_CITY);
            case 7 -> new Palette(s(DescentContent.BRINE_STONE.get()), s(Blocks.PRISMARINE_BRICKS), s(Blocks.SAND),
                    s(DescentContent.TIDE_CORAL.get()), s(Blocks.SEA_LANTERN),
                    withDeepglass(form, s(Blocks.DEEPSLATE_DIAMOND_ORE), s(Blocks.DEEPSLATE_LAPIS_ORE), s(Blocks.DEEPSLATE_EMERALD_ORE)), 0.010,
                    BuiltInLootTables.BURIED_TREASURE);
            case 8 -> new Palette(s(DescentContent.DREAM_CLOUD.get()), s(Blocks.CALCITE), s(DescentContent.DREAM_TILE.get()),
                    s(DescentContent.DREAM_GLOW.get()), s(DescentContent.DREAM_GLOW.get()),
                    withDeepglass(form, s(Blocks.DEEPSLATE_DIAMOND_ORE), s(Blocks.AMETHYST_BLOCK), s(Blocks.DEEPSLATE_EMERALD_ORE)), 0.008,
                    BuiltInLootTables.STRONGHOLD_LIBRARY);
            case 9 -> new Palette(s(DescentContent.COURT_STONE.get()), s(Blocks.POLISHED_BLACKSTONE), s(DescentContent.COURT_TILE.get()),
                    s(DescentContent.COURT_BRAZIER.get()), s(DescentContent.COURT_BRAZIER.get()),
                    withDeepglass(form, s(Blocks.DEEPSLATE_DIAMOND_ORE), s(Blocks.GILDED_BLACKSTONE), s(Blocks.DEEPSLATE_EMERALD_ORE)), 0.010,
                    BuiltInLootTables.BASTION_TREASURE);
            default -> new Palette(s(DescentContent.ABYSS_STONE.get()), s(Blocks.BLACKSTONE), s(DescentContent.SEED_SHELL.get()),
                    s(DescentContent.SEED_VEIN.get()), s(DescentContent.SEED_VEIN.get()),
                    withDeepglass(form, s(Blocks.DEEPSLATE_DIAMOND_ORE), s(Blocks.ANCIENT_DEBRIS), s(Blocks.DEEPSLATE_EMERALD_ORE)), 0.008,
                    BuiltInLootTables.END_CITY_TREASURE);
        };
    }
}
