package dev.awaken.gates.content;

import java.util.List;

/** Block palettes per theme. See docs/modules/gates.md for the look of each. */
final class Palettes {
    private Palettes() {}

    static Palette build(Theme t) {
        return switch (t) {
            case BELL_CRYPT -> Palette.of(
                    "minecraft:deepslate", "minecraft:polished_deepslate", "minecraft:deepslate_tiles",
                    "minecraft:deepslate_bricks", "minecraft:cracked_deepslate_bricks", "minecraft:deepslate_tiles",
                    "minecraft:deepslate_tiles", "minecraft:deepslate_brick_stairs", "minecraft:pearlescent_froglight",
                    "minecraft:soul_lantern[hanging=true]", "minecraft:air", "minecraft:gray_carpet",
                    List.of("minecraft:candle[candles=3,lit=true]", "minecraft:candle[candles=1,lit=true]", "minecraft:bone_block[axis=x]",
                            "minecraft:cobweb", "minecraft:white_candle[candles=2,lit=true]"),
                    List.of("minecraft:iron_chain[axis=y]", "minecraft:cobweb", "minecraft:soul_lantern[hanging=true]"),
                    List.of("minecraft:bone_block[axis=y]", "minecraft:chiseled_deepslate", "minecraft:polished_basalt[axis=y]"));
            case FUNGAL_HOLLOW -> Palette.of(
                    "minecraft:tuff", "minecraft:mycelium", "minecraft:moss_block",
                    "minecraft:packed_mud", "minecraft:mud_bricks", "minecraft:mushroom_stem",
                    "minecraft:brown_mushroom_block", "minecraft:mud_brick_stairs", "minecraft:shroomlight",
                    "minecraft:spore_blossom", "minecraft:water", "minecraft:moss_carpet",
                    List.of("minecraft:brown_mushroom", "minecraft:red_mushroom", "minecraft:moss_carpet", "minecraft:fern"),
                    List.of("minecraft:hanging_roots", "minecraft:spore_blossom", "minecraft:hanging_roots"),
                    List.of("minecraft:red_mushroom_block", "minecraft:brown_mushroom_block", "minecraft:warped_wart_block", "minecraft:moss_block"));
            case EMBER_MINES -> Palette.of(
                    "minecraft:blackstone", "minecraft:polished_blackstone", "minecraft:gilded_blackstone",
                    "minecraft:polished_blackstone_bricks", "minecraft:cracked_polished_blackstone_bricks", "minecraft:dark_oak_log[axis=y]",
                    "minecraft:dark_oak_planks", "minecraft:polished_blackstone_brick_stairs", "minecraft:ochre_froglight",
                    "minecraft:lantern[hanging=true]", "minecraft:lava", "minecraft:black_carpet",
                    List.of("minecraft:rail[shape=north_south]", "minecraft:iron_chain[axis=x]", "minecraft:candle[candles=2,lit=true]"),
                    List.of("minecraft:iron_chain[axis=y]", "minecraft:lantern[hanging=true]"),
                    List.of("minecraft:gold_ore", "minecraft:iron_ore", "minecraft:magma_block", "minecraft:dark_oak_planks", "minecraft:coal_ore"));
            case DROWNED_ARCHIVE -> Palette.of(
                    "minecraft:prismarine", "minecraft:dark_prismarine", "minecraft:prismarine_bricks",
                    "minecraft:prismarine_bricks", "minecraft:bookshelf", "minecraft:stripped_dark_oak_log[axis=y]",
                    "minecraft:dark_prismarine", "minecraft:dark_prismarine_stairs", "minecraft:sea_lantern",
                    "minecraft:lantern[hanging=true]", "minecraft:water", "minecraft:cyan_carpet",
                    List.of("minecraft:sea_pickle[pickles=3,waterlogged=false]", "minecraft:white_carpet", "minecraft:candle[candles=1,lit=true]"),
                    List.of("minecraft:lantern[hanging=true]", "minecraft:iron_chain[axis=y]"),
                    List.of("minecraft:bookshelf", "minecraft:bookshelf", "minecraft:dark_oak_planks", "minecraft:wet_sponge"));
            case CLOCKWORK_FOUNDRY -> Palette.of(
                    "minecraft:deepslate", "minecraft:waxed_cut_copper", "minecraft:waxed_exposed_cut_copper",
                    "minecraft:waxed_weathered_cut_copper", "minecraft:waxed_copper_block", "minecraft:iron_block",
                    "minecraft:waxed_oxidized_cut_copper", "minecraft:waxed_cut_copper_stairs", "minecraft:waxed_copper_bulb[lit=true]",
                    "minecraft:waxed_copper_lantern[hanging=true]", "minecraft:lava", "minecraft:orange_carpet",
                    List.of("minecraft:anvil", "minecraft:grindstone[face=floor]", "minecraft:waxed_lightning_rod", "minecraft:iron_chain[axis=z]"),
                    List.of("minecraft:iron_chain[axis=y]", "minecraft:waxed_copper_lantern[hanging=true]"),
                    List.of("minecraft:waxed_copper_grate", "minecraft:iron_block", "minecraft:smooth_stone", "minecraft:observer"));
            case GLASS_DESERT -> Palette.of(
                    "minecraft:sandstone", "minecraft:smooth_sandstone", "minecraft:cut_sandstone",
                    "minecraft:cut_sandstone", "minecraft:chiseled_sandstone", "minecraft:smooth_sandstone",
                    "minecraft:smooth_sandstone", "minecraft:smooth_sandstone_stairs", "minecraft:glowstone",
                    "minecraft:end_rod[facing=down]", "minecraft:air", "minecraft:orange_carpet",
                    List.of("minecraft:dead_bush", "minecraft:amethyst_cluster[facing=up]", "minecraft:potted_cactus"),
                    List.of("minecraft:end_rod[facing=down]"),
                    List.of("minecraft:glass", "minecraft:tinted_glass", "minecraft:amethyst_block", "minecraft:sandstone"));
            case HIVE -> Palette.of(
                    "minecraft:honeycomb_block", "minecraft:honeycomb_block", "minecraft:yellow_terracotta",
                    "minecraft:honeycomb_block", "minecraft:orange_terracotta", "minecraft:stripped_birch_log[axis=y]",
                    "minecraft:honeycomb_block", "minecraft:birch_stairs", "minecraft:ochre_froglight",
                    "minecraft:shroomlight", "minecraft:air", "minecraft:yellow_carpet",
                    List.of("minecraft:yellow_candle[candles=3,lit=true]", "minecraft:honey_block", "minecraft:hay_block[axis=y]"),
                    List.of("minecraft:shroomlight", "minecraft:hanging_roots"),
                    List.of("minecraft:honey_block", "minecraft:honeycomb_block", "minecraft:hay_block[axis=y]"));
            case OVERGROWN_TEMPLE -> Palette.of(
                    "minecraft:mossy_cobblestone", "minecraft:mossy_stone_bricks", "minecraft:moss_block",
                    "minecraft:mossy_stone_bricks", "minecraft:chiseled_stone_bricks", "minecraft:stone_bricks",
                    "minecraft:stone_bricks", "minecraft:mossy_stone_brick_stairs", "minecraft:verdant_froglight",
                    "minecraft:lantern[hanging=true]", "minecraft:water", "minecraft:moss_carpet",
                    List.of("minecraft:fern", "minecraft:azalea", "minecraft:flowering_azalea", "minecraft:moss_carpet"),
                    List.of("minecraft:hanging_roots", "minecraft:jungle_leaves[persistent=true]", "minecraft:lantern[hanging=true]"),
                    List.of("minecraft:jungle_leaves[persistent=true]", "minecraft:moss_block", "minecraft:jungle_log[axis=y]"));
            case LANTERN_NIGHT_MARKET -> Palette.of(
                    "minecraft:dark_oak_planks", "minecraft:spruce_planks", "minecraft:red_terracotta",
                    "minecraft:dark_oak_planks", "minecraft:stripped_spruce_log[axis=y]", "minecraft:dark_oak_log[axis=y]",
                    "minecraft:dark_oak_planks", "minecraft:spruce_stairs", "minecraft:shroomlight",
                    "minecraft:lantern[hanging=true]", "minecraft:water", "minecraft:red_carpet",
                    List.of("minecraft:flower_pot", "minecraft:potted_poppy", "minecraft:candle[candles=4,lit=true]", "minecraft:cauldron"),
                    List.of("minecraft:lantern[hanging=true]", "minecraft:soul_lantern[hanging=true]", "minecraft:lantern[hanging=true]"),
                    List.of("minecraft:red_wool", "minecraft:yellow_wool", "minecraft:hay_block[axis=y]", "minecraft:crafting_table"));
            case FROST_MONASTERY -> Palette.of(
                    "minecraft:snow_block", "minecraft:polished_diorite", "minecraft:blue_ice",
                    "minecraft:packed_ice", "minecraft:quartz_bricks", "minecraft:quartz_pillar[axis=y]",
                    "minecraft:smooth_quartz", "minecraft:polished_diorite_stairs", "minecraft:sea_lantern",
                    "minecraft:lantern[hanging=true]", "minecraft:air", "minecraft:light_blue_carpet",
                    List.of("minecraft:snow[layers=2]", "minecraft:white_candle[candles=3,lit=true]", "minecraft:snow[layers=1]"),
                    List.of("minecraft:end_rod[facing=down]", "minecraft:lantern[hanging=true]"),
                    List.of("minecraft:ice", "minecraft:packed_ice", "minecraft:snow_block", "minecraft:quartz_block"));
            case MIRROR_PALACE -> Palette.of(
                    "minecraft:calcite", "minecraft:smooth_quartz", "minecraft:quartz_bricks",
                    "minecraft:white_stained_glass", "minecraft:chiseled_quartz_block", "minecraft:quartz_pillar[axis=y]",
                    "minecraft:smooth_quartz", "minecraft:smooth_quartz_stairs", "minecraft:sea_lantern",
                    "minecraft:end_rod[facing=down]", "minecraft:air", "minecraft:white_carpet",
                    List.of("minecraft:white_candle[candles=4,lit=true]", "minecraft:amethyst_cluster[facing=up]"),
                    List.of("minecraft:end_rod[facing=down]"),
                    List.of("minecraft:glass", "minecraft:white_stained_glass", "minecraft:quartz_block", "minecraft:calcite"));
            case PRECURSOR_LAB -> Palette.of(
                    "minecraft:gray_concrete", "minecraft:smooth_stone", "minecraft:cyan_terracotta",
                    "minecraft:light_gray_concrete", "minecraft:cyan_stained_glass", "minecraft:iron_block",
                    "minecraft:smooth_stone", "minecraft:stone_brick_stairs", "minecraft:sea_lantern",
                    "minecraft:waxed_copper_bulb[lit=true]", "minecraft:air", "minecraft:cyan_carpet",
                    List.of("minecraft:iron_bars", "minecraft:target", "minecraft:waxed_lightning_rod"),
                    List.of("minecraft:iron_chain[axis=y]", "minecraft:end_rod[facing=down]"),
                    List.of("minecraft:observer", "minecraft:iron_block", "minecraft:cyan_terracotta", "minecraft:light_gray_concrete"));
            case NIGHTMARE -> Palette.of(
                    "minecraft:obsidian", "minecraft:oak_planks", "minecraft:grass_block",
                    "minecraft:purple_concrete", "minecraft:crying_obsidian", "minecraft:oak_log[axis=y]",
                    "minecraft:obsidian", "minecraft:oak_stairs", "minecraft:crying_obsidian",
                    "minecraft:soul_lantern[hanging=true]", "minecraft:air", "minecraft:purple_carpet",
                    List.of("minecraft:flower_pot", "minecraft:potted_poppy", "minecraft:crafting_table", "minecraft:black_candle[candles=2,lit=true]"),
                    List.of("minecraft:iron_chain[axis=y]", "minecraft:oak_stairs[half=top]"),
                    List.of("minecraft:bookshelf", "minecraft:white_wool", "minecraft:crafting_table", "minecraft:oak_planks", "minecraft:red_wool"));
            case SKY_ARCHIPELAGO -> Palette.of(
                    "minecraft:white_concrete", "minecraft:grass_block", "minecraft:stone",
                    "minecraft:white_wool", "minecraft:light_blue_stained_glass", "minecraft:birch_log[axis=y]",
                    "minecraft:white_wool", "minecraft:birch_stairs", "minecraft:glowstone",
                    "minecraft:end_rod[facing=down]", "minecraft:air", "minecraft:white_carpet",
                    List.of("minecraft:short_grass", "minecraft:dandelion", "minecraft:poppy", "minecraft:cornflower"),
                    List.of("minecraft:end_rod[facing=down]"),
                    List.of("minecraft:birch_leaves[persistent=true]", "minecraft:stone", "minecraft:white_wool"));
            case ABYSSAL_SHRINE -> Palette.of(
                    "minecraft:obsidian", "minecraft:deepslate_tiles", "minecraft:sculk",
                    "minecraft:polished_blackstone", "minecraft:sculk", "minecraft:crying_obsidian",
                    "minecraft:obsidian", "minecraft:deepslate_tile_stairs", "minecraft:crying_obsidian",
                    "minecraft:soul_lantern[hanging=true]", "minecraft:air", "minecraft:black_carpet",
                    List.of("minecraft:black_candle[candles=3,lit=true]", "minecraft:sculk"),
                    List.of("minecraft:iron_chain[axis=y]"),
                    List.of("minecraft:crying_obsidian", "minecraft:obsidian", "minecraft:sculk"));
        };
    }
}
