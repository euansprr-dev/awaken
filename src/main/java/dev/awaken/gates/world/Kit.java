package dev.awaken.gates.world;

import dev.awaken.gates.content.Bs;
import dev.awaken.gates.content.Theme;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumMap;

/**
 * A theme's interior dressing beyond its {@link dev.awaken.gates.content.Palette}: the wall rhythm (plinth, pilasters,
 * recessed panels, lintels, cornice), the ceiling (beams, vault ribs, skylights), the floor (bands, medallions, the
 * runner that leads you on) and its light fixtures (chandeliers, niche lights, sconces, the guide light over the way on).
 * States resolve once on first use (after registries freeze) and are then shared read-only by worldgen threads.
 */
public record Kit(
        BlockState trim,        // plinth and pilaster bases
        BlockState panel,       // the back of the recessed panels: the theme's signature wall
        BlockState panelAlt,    // every other big panel
        BlockState pilaster,    // pilasters and door frames
        BlockState capital,     // lintels over the panels and pilaster capitals
        BlockState cornice,     // stairs, placed upside down under the ceiling
        BlockState beam,        // ceiling beams and vault ribs (axis set when placed)
        BlockState band,        // floor bands in line with the pilasters
        BlockState medallion,   // the floor's centrepiece
        BlockState sconce,      // light set into pilasters
        BlockState nicheLight,  // hangs from a niche's lintel (or stands on its sill with STAND_NICHE)
        BlockState chandelier,  // the light at the bottom of a chandelier
        BlockState chain,       // what chandeliers hang from (air: the light sits on the ceiling)
        BlockState guide,       // the frame light over the way on
        BlockState runner,      // the leading line between the doors on the critical path (air: none)
        int flags) {

    /** Recessed panels between pilasters. */
    public static final int INSETS = 1;
    /** Ceiling beams across flat ceilings. */
    public static final int BEAMS = 2;
    /** Ribs under domed ceilings. */
    public static final int RIBS = 4;
    /** Natural walls: no pilasters, alcoves eaten out by noise. */
    public static final int ORGANIC = 8;
    /** The niche light stands on the sill instead of hanging from the lintel. */
    public static final int STAND_NICHE = 16;
    /** The runner is laid on the floor (carpet) rather than set into it. */
    public static final int CARPET = 32;
    /** Floors in a two-tone checker. */
    public static final int CHECKER = 64;
    /** Skylights: light behind glass in the ceiling instead of chandeliers. */
    public static final int SKYLIGHT = 128;
    /** Walls and ceiling are painted as open sky. */
    public static final int SKY = 256;
    /** Few lights: pools of light far apart in the dark. */
    public static final int DIM = 512;
    /** Honey, wax or water drips hang from the ceiling. */
    public static final int DRIPS = 1024;
    /** Moss, roots and vines climb the walls. */
    public static final int OVERGROWN = 2048;
    /** Nothing is quite where it should be. */
    public static final int WRONG = 4096;
    /** Pipes run along the walls. */
    public static final int PIPES = 8192;
    /** Rails along the runner (mines). */
    public static final int RAILS = 16384;

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    private static final EnumMap<Theme, Kit> KITS = new EnumMap<>(Theme.class);

    public static Kit of(Theme t) {
        Kit k;
        synchronized (KITS) {
            k = KITS.get(t);
            if (k == null) {
                k = build(t);
                KITS.put(t, k);
            }
        }
        return k;
    }

    private static Kit kit(String trim, String panel, String panelAlt, String pilaster, String capital, String cornice, String beam,
                           String band, String medallion, String sconce, String nicheLight, String chandelier, String chain,
                           String guide, String runner, int flags) {
        return new Kit(Bs.of(trim), Bs.of(panel), Bs.of(panelAlt), Bs.of(pilaster), Bs.of(capital), Bs.of(cornice), Bs.of(beam),
                Bs.of(band), Bs.of(medallion), Bs.of(sconce), Bs.of(nicheLight), Bs.of(chandelier), Bs.of(chain), Bs.of(guide),
                Bs.of(runner), flags);
    }

    private static Kit build(Theme t) {
        return switch (t) {
            // cold stone, walls of the dead behind pilasters, soul light in the niches, a ribbed vault
            case BELL_CRYPT -> kit("minecraft:polished_deepslate", "awaken:gate_ossuary", "awaken:gate_ossuary",
                    "minecraft:polished_deepslate", "minecraft:chiseled_deepslate", "minecraft:deepslate_tile_stairs",
                    "minecraft:polished_basalt[axis=y]", "minecraft:deepslate_tiles", "minecraft:chiseled_deepslate",
                    "minecraft:pearlescent_froglight", "minecraft:soul_lantern[hanging=true]", "minecraft:soul_lantern[hanging=true]",
                    "minecraft:iron_chain[axis=y]", "minecraft:pearlescent_froglight", "minecraft:polished_deepslate",
                    INSETS | RIBS | BEAMS | DIM);
            // a living cave: soft walls, glowing caps for light, moss underfoot
            case FUNGAL_HOLLOW -> kit("minecraft:moss_block", "minecraft:tuff", "minecraft:mud_bricks", "minecraft:mushroom_stem",
                    "awaken:gate_glowcap_gills", "minecraft:mud_brick_stairs", "minecraft:mushroom_stem", "minecraft:moss_block",
                    "awaken:gate_glowcap", "minecraft:shroomlight", "minecraft:spore_blossom", "awaken:gate_glowcap",
                    "minecraft:hanging_roots", "minecraft:shroomlight", "minecraft:moss_carpet",
                    ORGANIC | CARPET | DIM);
            // timber posts and beams, ore glowing in the rock between them, lanterns on the beams, a track underfoot
            case EMBER_MINES -> kit("minecraft:polished_blackstone", "awaken:gate_ember_ore", "minecraft:blackstone",
                    "minecraft:stripped_dark_oak_log[axis=y]", "minecraft:dark_oak_planks", "minecraft:polished_blackstone_brick_stairs",
                    "minecraft:stripped_dark_oak_log[axis=x]", "minecraft:polished_blackstone_bricks", "minecraft:gilded_blackstone",
                    "minecraft:ochre_froglight", "minecraft:lantern[hanging=true]", "minecraft:lantern[hanging=true]",
                    "minecraft:iron_chain[axis=y]", "minecraft:ochre_froglight", "minecraft:rail[shape=north_south]",
                    INSETS | BEAMS | RAILS | CARPET);
            // bookcases floor to cornice between dark wood, sea-green stone, reading lamps
            case DROWNED_ARCHIVE -> kit("minecraft:dark_prismarine", "minecraft:bookshelf", "awaken:gate_sodden_shelf",
                    "minecraft:stripped_dark_oak_log[axis=y]", "minecraft:dark_prismarine", "minecraft:dark_prismarine_stairs",
                    "minecraft:stripped_dark_oak_log[axis=x]", "minecraft:prismarine_bricks", "minecraft:sea_lantern",
                    "minecraft:sea_lantern", "minecraft:lantern[hanging=true]", "minecraft:lantern[hanging=true]",
                    "minecraft:iron_chain[axis=y]", "minecraft:sea_lantern", "minecraft:cyan_carpet",
                    INSETS | BEAMS | CARPET);
            // riveted copper between iron pilasters, pipes, gears set in the floor, bulbs
            case CLOCKWORK_FOUNDRY -> kit("minecraft:waxed_copper_block", "minecraft:waxed_exposed_chiseled_copper", "minecraft:waxed_copper_grate",
                    "minecraft:iron_block", "minecraft:waxed_copper_block", "minecraft:waxed_cut_copper_stairs",
                    "minecraft:iron_block", "minecraft:waxed_copper_grate", "awaken:gate_gear",
                    "minecraft:waxed_copper_bulb[lit=true]", "minecraft:waxed_copper_lantern[hanging=true]", "minecraft:waxed_copper_lantern[hanging=true]",
                    "minecraft:waxed_copper_chain[axis=y]", "minecraft:waxed_copper_bulb[lit=true]", "minecraft:waxed_copper_grate",
                    INSETS | BEAMS | PIPES);
            // carved sandstone, sun tiles, amber skylights instead of lamps
            case GLASS_DESERT -> kit("minecraft:smooth_sandstone", "minecraft:chiseled_sandstone", "awaken:gate_sun_tile",
                    "minecraft:smooth_sandstone", "minecraft:chiseled_sandstone", "minecraft:smooth_sandstone_stairs",
                    "minecraft:cut_sandstone", "minecraft:cut_sandstone", "awaken:gate_sun_tile",
                    "minecraft:glowstone", "minecraft:end_rod[facing=down]", "minecraft:lantern[hanging=true]",
                    "minecraft:iron_chain[axis=y]", "minecraft:glowstone", "minecraft:sand",
                    INSETS | SKYLIGHT);
            // resin and comb, brood cells glowing in the walls, honey hanging from the ceiling
            case HIVE -> kit("minecraft:resin_bricks", "awaken:gate_brood_cell", "minecraft:honeycomb_block",
                    "minecraft:resin_bricks", "minecraft:chiseled_resin_bricks", "minecraft:resin_brick_stairs",
                    "minecraft:stripped_birch_log[axis=x]", "minecraft:yellow_terracotta", "awaken:gate_royal_jelly",
                    "minecraft:ochre_froglight", "minecraft:yellow_candle[candles=3,lit=true]", "minecraft:shroomlight",
                    "minecraft:iron_chain[axis=y]", "minecraft:ochre_froglight", "minecraft:yellow_carpet",
                    INSETS | STAND_NICHE | DRIPS | CARPET);
            // carved reliefs and jade between stone pillars, roots climbing everything
            case OVERGROWN_TEMPLE -> kit("awaken:gate_jade", "awaken:gate_temple_relief", "minecraft:chiseled_stone_bricks",
                    "minecraft:stone_bricks", "minecraft:chiseled_stone_bricks", "minecraft:stone_brick_stairs",
                    "minecraft:stripped_jungle_log[axis=x]", "minecraft:mossy_stone_bricks", "awaken:gate_jade",
                    "minecraft:verdant_froglight", "minecraft:lantern[hanging=true]", "minecraft:lantern[hanging=true]",
                    "minecraft:iron_chain[axis=y]", "minecraft:verdant_froglight", "minecraft:moss_carpet",
                    INSETS | OVERGROWN | CARPET);
            // lacquered red posts, paper screens, strings of paper lanterns
            case LANTERN_NIGHT_MARKET -> kit("minecraft:dark_oak_planks", "minecraft:birch_planks", "minecraft:cinnabar_bricks",
                    "minecraft:stripped_mangrove_log[axis=y]", "minecraft:dark_oak_planks", "minecraft:dark_oak_stairs",
                    "minecraft:dark_oak_log[axis=x]", "minecraft:red_terracotta", "minecraft:red_terracotta",
                    "minecraft:shroomlight", "awaken:gate_paper_lantern", "awaken:gate_paper_lantern",
                    "minecraft:iron_chain[axis=y]", "minecraft:shroomlight", "minecraft:red_carpet",
                    INSETS | BEAMS | CARPET);
            // frosted windows lit from behind, rime on the quartz, a ribbed vault
            case FROST_MONASTERY -> kit("minecraft:quartz_bricks", "awaken:gate_frost_window", "awaken:gate_rime_brick",
                    "minecraft:quartz_pillar[axis=y]", "minecraft:chiseled_quartz_block", "minecraft:quartz_stairs",
                    "minecraft:quartz_pillar[axis=x]", "minecraft:packed_ice", "minecraft:blue_ice",
                    "minecraft:sea_lantern", "minecraft:lantern[hanging=true]", "minecraft:lantern[hanging=true]",
                    "minecraft:iron_chain[axis=y]", "minecraft:sea_lantern", "minecraft:light_blue_carpet",
                    INSETS | RIBS | CARPET);
            // mirrors framed in quartz, a black and white floor, crystal light
            case MIRROR_PALACE -> kit("minecraft:quartz_bricks", "awaken:gate_mirror", "awaken:gate_mirror",
                    "minecraft:quartz_pillar[axis=y]", "minecraft:chiseled_quartz_block", "minecraft:smooth_quartz_stairs",
                    "minecraft:quartz_pillar[axis=x]", "minecraft:polished_deepslate", "minecraft:chiseled_quartz_block",
                    "minecraft:sea_lantern", "minecraft:end_rod[facing=down]", "minecraft:end_rod[facing=down]",
                    "minecraft:iron_chain[axis=y]", "minecraft:sea_lantern", "minecraft:air",
                    INSETS | CHECKER | RIBS);
            // Precursor plating (the Above's language), lit lab panels, light strips, a cyan guide line
            case PRECURSOR_LAB -> kit("minecraft:smooth_stone", "awaken:gate_lab_panel", "minecraft:cyan_stained_glass",
                    "awaken:precursor_plating", "awaken:ruin_glyph", "minecraft:light_gray_concrete_stairs",
                    "awaken:precursor_plating", "minecraft:cyan_terracotta", "awaken:gate_lab_panel",
                    "minecraft:sea_lantern", "minecraft:end_rod[facing=down]", "minecraft:sea_lantern",
                    "minecraft:air", "minecraft:sea_lantern", "minecraft:cyan_concrete",
                    INSETS | SKYLIGHT);
            // a house that isn't: wallpaper that watches, skirting boards, windows onto nothing
            case NIGHTMARE -> kit("minecraft:dark_oak_planks", "awaken:gate_dream_paper", "awaken:gate_void_glass",
                    "minecraft:oak_log[axis=y]", "minecraft:oak_planks", "minecraft:oak_stairs",
                    "minecraft:oak_log[axis=x]", "minecraft:oak_planks", "minecraft:white_wool",
                    "minecraft:crying_obsidian", "minecraft:soul_lantern[hanging=true]", "minecraft:soul_lantern[hanging=true]",
                    "minecraft:iron_chain[axis=y]", "minecraft:crying_obsidian", "minecraft:purple_carpet",
                    INSETS | BEAMS | WRONG | CARPET);
            // sky on every side: cloud banks for walls, light from the sun overhead
            case SKY_ARCHIPELAGO -> kit("minecraft:white_concrete", "awaken:gate_cloud", "awaken:gate_cloud",
                    "minecraft:birch_log[axis=y]", "minecraft:birch_planks", "minecraft:birch_stairs",
                    "minecraft:birch_log[axis=x]", "minecraft:stone", "minecraft:mossy_cobblestone",
                    "minecraft:glowstone", "minecraft:end_rod[facing=down]", "minecraft:glowstone",
                    "minecraft:air", "minecraft:glowstone", "minecraft:air",
                    SKY);
            // black stone, windows into the void, obsidian ribs, candles and almost nothing else
            case ABYSSAL_SHRINE -> kit("minecraft:polished_blackstone_bricks", "awaken:gate_void_glass", "minecraft:obsidian",
                    "minecraft:obsidian", "minecraft:crying_obsidian", "minecraft:polished_blackstone_brick_stairs",
                    "minecraft:obsidian", "minecraft:polished_blackstone", "awaken:gate_void_glass",
                    "minecraft:crying_obsidian", "minecraft:black_candle[candles=3,lit=true]", "minecraft:soul_lantern[hanging=true]",
                    "minecraft:iron_chain[axis=y]", "minecraft:crying_obsidian", "minecraft:sculk",
                    INSETS | RIBS | STAND_NICHE | DIM);
        };
    }
}
