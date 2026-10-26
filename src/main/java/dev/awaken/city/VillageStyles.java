package dev.awaken.city;

import dev.awaken.city.CityBlocks.Course;
import dev.awaken.city.CityBlocks.Roof;
import dev.awaken.city.CityGrammar.Style;
import dev.awaken.city.CityPlan.Plot;
import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.city.CityBlocks.*;
import static dev.awaken.city.CityGrammar.*;

/**
 * The grammar's styles for settlements outside Carillon (SPEC §28.6, PLAN 5.3): keyed by the land's family (the biome)
 * and the place's wealth instead of Carillon's seven districts. Lower and plainer than the city (one or two storeys,
 * no jetties, no shopfronts), every house still different (walls, roof, shutters and boxes drawn per house), and the
 * same climbing grid: hoods at row 3, the cornice at 5, a box and a course per storey, a trellis or a pipe on a street
 * face. A settlement's plot carries its style in {@code Plot.district}: {@link #BASE} + family × 4 + wealth, so
 * Carillon's districts (1-7) never reach this class and the city paints as before.
 *
 * <p>The palette for what's inside (floors, furniture woods, beds and rugs) is here too, so a house's rooms match its
 * walls ({@link #palette}).
 */
public final class VillageStyles {
    private VillageStyles() {}

    /** Plot districts from here up are settlement styles (Carillon's are 1-7). */
    public static final int BASE = 16;
    /** The land's families (by biome). */
    public static final int TEMPERATE = 0, FROST = 1, DESERT = 2, SAVANNA = 3, MIRE = 4, HIGHLAND = 5, ELDER = 6, COAST = 7,
            JUNGLE = 8, CHERRY = 9;
    public static final String[] FAMILIES = {"temperate", "frost", "desert", "savanna", "mire", "highland", "elder", "coast",
            "jungle", "cherry"};
    /** Wealth: a poor place's houses are timber and rubble, a prosperous one's stone, glass and flowers. */
    public static final int POOR = 0, MODEST = 1, PROSPEROUS = 2;

    public static int district(int family, int wealth) {
        return BASE + family * 4 + Math.clamp(wealth, 0, 2);
    }

    public static boolean isVillage(Plot p) {
        return p.district() >= BASE;
    }

    public static int family(Plot p) {
        return Math.clamp((p.district() - BASE) / 4, 0, FAMILIES.length - 1);
    }

    public static int wealth(Plot p) {
        return Math.clamp((p.district() - BASE) % 4, 0, 2);
    }

    // ---- woods and stones the cities don't use --------------------------------------------------------------------

    static final Roof OAK = Roof.plural("oak_planks", "oak");
    static final Roof ACACIA = Roof.plural("acacia_planks", "acacia");
    static final Roof MANGROVE = Roof.plural("mangrove_planks", "mangrove");
    static final Roof BIRCH = Roof.plural("birch_planks", "birch");
    static final Roof CHERRY_ROOF = Roof.plural("cherry_planks", "cherry");
    static final Roof BAMBOO = Roof.plural("bamboo_mosaic", "bamboo_mosaic");
    static final Roof JUNGLE_ROOF = Roof.plural("jungle_planks", "jungle");
    static final Roof SANDSTONE_ROOF = Roof.of("minecraft:smooth_sandstone", "minecraft:smooth_sandstone_slab", "minecraft:smooth_sandstone_stairs");
    static final Roof MOSSY_ROOF = Roof.plural("mossy_cobblestone", "mossy_cobblestone");
    static final Roof MUD_ROOF = MUD;

    static final Course C_OAK = Course.of("minecraft:oak_stairs");
    static final Course C_ACACIA = Course.of("minecraft:acacia_stairs");
    static final Course C_MANGROVE = Course.of("minecraft:mangrove_stairs");
    static final Course C_JUNGLE = Course.of("minecraft:jungle_stairs");
    static final Course C_BIRCH = Course.of("minecraft:birch_stairs");
    static final Course C_CHERRY = Course.of("minecraft:cherry_stairs");
    static final Course C_MUD = Course.of("minecraft:mud_brick_stairs");
    static final Course C_MOSSY = Course.of("minecraft:mossy_cobblestone_stairs");

    static final BlockState STRIPPED_SPRUCE = of("minecraft:stripped_spruce_log[axis=y]");
    static final BlockState STRIPPED_OAK = of("minecraft:stripped_oak_log[axis=y]");
    static final BlockState STRIPPED_DARK = of("minecraft:stripped_dark_oak_log[axis=y]");
    static final BlockState STRIPPED_ACACIA = of("minecraft:stripped_acacia_log[axis=y]");
    static final BlockState STRIPPED_MANGROVE = of("minecraft:stripped_mangrove_log[axis=y]");
    static final BlockState STRIPPED_JUNGLE = of("minecraft:stripped_jungle_log[axis=y]");
    static final BlockState STRIPPED_CHERRY = of("minecraft:stripped_cherry_log[axis=y]");
    static final BlockState STRIPPED_BIRCH = of("minecraft:stripped_birch_log[axis=y]");
    static final BlockState ACACIA_PLANKS = of("minecraft:acacia_planks");
    static final BlockState MANGROVE_PLANKS = of("minecraft:mangrove_planks");
    static final BlockState JUNGLE_PLANKS = of("minecraft:jungle_planks");
    static final BlockState CHERRY_PLANKS = of("minecraft:cherry_planks");
    static final BlockState PACKED_MUD_BLOCK = of("minecraft:packed_mud");
    static final BlockState SANDSTONE = of("minecraft:sandstone");
    static final BlockState ORANGE_TERRACOTTA = of("minecraft:orange_terracotta");
    static final BlockState BROWN_TERRACOTTA = of("minecraft:brown_terracotta");
    static final BlockState CAP_SANDSTONE = of("minecraft:smooth_sandstone_slab[type=bottom]");
    static final BlockState CAP_COBBLE = of("minecraft:cobblestone_slab[type=bottom]");
    static final BlockState CAP_MUD = of("minecraft:mud_brick_slab[type=bottom]");
    static final BlockState CAP_DEEPSLATE = of("minecraft:cobbled_deepslate_slab[type=bottom]");

    static final String[] DOOR_WOOD = {"oak", "spruce", "dark_oak", "acacia", "mangrove", "jungle", "birch", "cherry"};
    static final int D_OAK = 0, D_SPRUCE = 1, D_DARK = 2, D_ACACIA = 3, D_MANGROVE = 4, D_JUNGLE = 5, D_BIRCH = 6, D_CHERRY = 7;
    static final BlockState[][] DOOR_LO = new BlockState[DOOR_WOOD.length][], DOOR_HI = new BlockState[DOOR_WOOD.length][];

    static {
        for (int i = 0; i < DOOR_WOOD.length; i++) {
            DOOR_LO[i] = dirs("minecraft:" + DOOR_WOOD[i] + "_door[facing=%s,half=lower,hinge=left,open=false]");
            DOOR_HI[i] = dirs("minecraft:" + DOOR_WOOD[i] + "_door[facing=%s,half=upper,hinge=left,open=false]");
        }
    }

    /** A plot's door half, facing into the house's front (the grammar's door rows). */
    static BlockState door(Plot p, boolean upper, int facing) {
        int w = palette(p).door();
        return (upper ? DOOR_HI : DOOR_LO)[w][facing];
    }

    // ---- what a house is made of ----------------------------------------------------------------------------------

    /** 0..99 from the plot alone (the same in every world, different house to house). */
    private static int roll(Plot p, int salt) {
        return CityPainter.Ctx.shape(p.id() * 131 + p.variant(), salt, 211);
    }

    private static <T> T pick(Plot p, int salt, T[] options) {
        return options[roll(p, salt) % options.length];
    }

    /** A house's style: its land's materials, wealth's finish, and this house's own draw of both. */
    static Style style(Plot p) {
        int fam = family(p), wealth = wealth(p);
        int d = p.district();
        boolean two = p.storeys() >= 2;
        int bloom = wealth == POOR ? 12 : wealth == MODEST ? 38 : 66;
        int shutterPct = wealth == POOR ? 45 : 70;
        String use = p.use();
        boolean work = use.equals("smithy") || use.equals("barn") || use.equals("granary") || use.equals("mill");
        if (work) bloom = Math.min(bloom, 10);
        return switch (fam) {
            case FROST -> {
                // longhouses: log walls on a rubble plinth, steep dark roofs that hold the snow, small shuttered windows
                BlockState ground = wealth == PROSPEROUS ? pick(p, 1, new BlockState[]{COBBLE, STONE_BRICKS}) : pick(p, 1, new BlockState[]{SPRUCE_PLANKS, SPRUCE_PLANKS, COBBLE});
                Roof roof = pick(p, 2, new Roof[]{SPRUCE, DARK_OAK, SPRUCE, SLATE_LIGHT});
                yield new Style(d, ground, SPRUCE_PLANKS, true, 0, roof, CAP_DEEPSLATE, C_SPRUCE, C_SPRUCE, C_SPRUCE,
                        SPRUCE_WOOD, STRIPPED_SPRUCE, null, W_COTTAGE, W_COTTAGE, true, R_TIMBER, 0, bloom / 3, new int[]{6, 6, 5},
                        new int[]{SH_NATURAL, SH_OXBLOOD}, 80, false, false, SMOOTH_SLAB, 2, false, false, SPRUCE_PLANKS, 20, false, false);
            }
            case DESERT -> {
                // caravanserai: sandstone and fired clay, flat roofs behind a parapet, stone boxes of herbs
                BlockState ground = pick(p, 1, new BlockState[]{SMOOTH_SANDSTONE, CUT_SANDSTONE, SANDSTONE, wealth == POOR ? PACKED_MUD_BLOCK : SMOOTH_SANDSTONE});
                BlockState wall = pick(p, 3, new BlockState[]{SMOOTH_SANDSTONE, WHITE_TERRACOTTA, CUT_SANDSTONE, ORANGE_TERRACOTTA});
                yield new Style(d, ground, wall, false, 0, SANDSTONE_ROOF, CAP_SANDSTONE, C_SANDSTONE, C_SANDSTONE, C_SANDSTONE,
                        CUT_SANDSTONE, CHISELED_SANDSTONE, null, W_ARCHED, W_ARCHED, false, R_SYM, 2, bloom / 2, new int[]{6, 6, 1, 4},
                        new int[]{SH_NATURAL, SH_CREAM}, 25, false, false, CAP_SANDSTONE, 1, false, false, wall, 0, false, false);
            }
            case SAVANNA -> {
                BlockState ground = pick(p, 1, new BlockState[]{MUD_BRICKS, PACKED_MUD_BLOCK, MUD_BRICKS, ACACIA_PLANKS});
                yield new Style(d, ground, wealth == PROSPEROUS ? PLASTER[P_OCHRE] : PACKED_MUD_BLOCK, true, 1, pick(p, 2, new Roof[]{ACACIA, ACACIA, MUD_ROOF}),
                        CAP_MUD, C_ACACIA, C_ACACIA, C_MUD, STRIPPED_ACACIA, STRIPPED_ACACIA, null, W_COTTAGE, W_COTTAGE, false, R_SINGLE, 0,
                        bloom / 2, new int[]{2, 4, 6}, new int[]{SH_NATURAL, SH_OXBLOOD}, shutterPct, false, false, SMOOTH_SLAB, 1, false, false,
                        PACKED_MUD_BLOCK, 30, false, false);
            }
            case MIRE -> {
                BlockState ground = pick(p, 1, new BlockState[]{MUD_BRICKS, MANGROVE_PLANKS, MUD_BRICKS, MOSSY_COBBLE});
                yield new Style(d, ground, MANGROVE_PLANKS, true, 0, pick(p, 2, new Roof[]{DARK_OAK, MANGROVE, MOSSY_ROOF}), CAP_MUD,
                        C_MANGROVE, C_MANGROVE, C_MUD, STRIPPED_MANGROVE, STRIPPED_MANGROVE, null, W_COTTAGE, W_COTTAGE, true, R_TIMBER, 0,
                        bloom / 3, new int[]{6, 5}, new int[]{SH_SAGE, SH_NATURAL}, shutterPct, false, false, SMOOTH_SLAB, 1, false, false,
                        MANGROVE_PLANKS, 85, false, false);
            }
            case HIGHLAND -> {
                BlockState ground = pick(p, 1, new BlockState[]{COBBLE, STONE_BRICKS, TUFF_BRICKS, COBBLE, POLISHED_ANDESITE});
                Roof roof = wealth == POOR ? pick(p, 2, new Roof[]{COBBLE_ROOF, SPRUCE, SLATE_LIGHT}) : pick(p, 2, new Roof[]{SLATE, SLATE_KIT, SLATE_LIGHT, SLATE_TUFF});
                BlockState wall = two ? pick(p, 3, new BlockState[]{PLASTER[P_WHITE], PLASTER[P_CREAM], STONE_BRICKS, COBBLE}) : ground;
                boolean timber = two && wall != STONE_BRICKS && wall != COBBLE;
                yield new Style(d, ground, wall, timber, 0, roof, CAP_STONE, C_STONE, timber ? C_DARK : C_STONE, C_COBBLE,
                        POLISHED_ANDESITE, CHISELED_STONE_BRICKS, null, W_LEADED, W_DORMER, true, timber ? R_TIMBER : R_SINGLE, 0, bloom / 2,
                        new int[]{1, 3, 5}, new int[]{SH_OXBLOOD, SH_BLUE, SH_NATURAL}, shutterPct, false, false, SMOOTH_SLAB, 2, false, two,
                        ground, 25, false, false);
            }
            case ELDER -> {
                BlockState ground = pick(p, 1, new BlockState[]{DARK_PLANKS, STRIPPED_DARK, DARK_PLANKS, COBBLE});
                yield new Style(d, ground, DARK_PLANKS, true, 0, pick(p, 2, new Roof[]{DARK_OAK, SPRUCE, MOSSY_ROOF}), CAP_COBBLE,
                        C_DARK, C_DARK, C_DARK, DARK_WOOD, STRIPPED_DARK, null, W_COTTAGE, W_COTTAGE, true, R_TIMBER, 0, bloom / 3,
                        new int[]{6, 2}, new int[]{SH_NATURAL, SH_SAGE}, shutterPct, false, false, SMOOTH_SLAB, 2, false, false, DARK_PLANKS,
                        70, false, false);
            }
            case COAST -> {
                // a fishing village: whitewash and blue shutters, slate against the sea wind
                BlockState ground = pick(p, 1, new BlockState[]{PLASTER[P_WHITE], PLASTER[P_WHITE], COBBLE, PLASTER[P_CREAM]});
                BlockState wall = pick(p, 3, new BlockState[]{PLASTER[P_WHITE], PLASTER[P_WHITE], SPRUCE_PLANKS, PLASTER[P_CREAM]});
                yield new Style(d, ground, wall, wall == SPRUCE_PLANKS, 1, pick(p, 2, new Roof[]{SLATE_KIT, SLATE, SPRUCE, SLATE_KIT}), CAP_STONE,
                        C_SPRUCE, C_SPRUCE, C_STONE, COBBLE, STONE_BRICKS, null, W_COTTAGE, W_COTTAGE, false, R_SYM, 0, bloom,
                        new int[]{3, 5, 1}, new int[]{SH_BLUE, SH_BLUE, SH_SAGE}, 85, false, false, SMOOTH_SLAB, 0, false, false, wall, 30,
                        false, false);
            }
            case JUNGLE -> {
                BlockState ground = pick(p, 1, new BlockState[]{JUNGLE_PLANKS, MOSSY_COBBLE, JUNGLE_PLANKS, MUD_BRICKS});
                yield new Style(d, ground, JUNGLE_PLANKS, true, 1, pick(p, 2, new Roof[]{BAMBOO, JUNGLE_ROOF, BAMBOO}), CAP_MUD,
                        C_JUNGLE, C_JUNGLE, C_JUNGLE, STRIPPED_JUNGLE, STRIPPED_JUNGLE, null, W_COTTAGE, W_COTTAGE, false, R_SINGLE, 0,
                        bloom, new int[]{1, 4, 6}, new int[]{SH_GREEN, SH_NATURAL}, shutterPct, false, false, SMOOTH_SLAB, 1, false, false,
                        JUNGLE_PLANKS, 90, false, false);
            }
            case CHERRY -> {
                BlockState ground = pick(p, 1, new BlockState[]{PLASTER[P_WHITE], STONE_BRICKS, PLASTER[P_CREAM]});
                yield new Style(d, ground, PLASTER[P_WHITE], true, 1, pick(p, 2, new Roof[]{CHERRY_ROOF, SLATE_KIT, CHERRY_ROOF}), CAP_SMOOTH,
                        C_CHERRY, C_CHERRY, C_STONE, CALCITE, CHISELED_STONE_BRICKS, null, W_COTTAGE, W_COTTAGE, false, R_SYM, 0, bloom + 10,
                        new int[]{4, 4, 5, 1}, new int[]{SH_CREAM, SH_SAGE}, shutterPct, false, false, SMOOTH_SLAB, 2, false, false,
                        PLASTER[P_WHITE], 40, false, false);
            }
            default -> {
                // the temperate heartland: rubble or ashlar below, plaster and timber above, slate or shingle over all
                BlockState ground = switch (wealth) {
                    case POOR -> pick(p, 1, new BlockState[]{COBBLE, OAK_PLANKS, COBBLE, MOSSY_COBBLE});
                    case MODEST -> pick(p, 1, new BlockState[]{COBBLE, STONE_BRICKS, COBBLE, PLASTER[P_WHITE]});
                    default -> pick(p, 1, new BlockState[]{STONE_BRICKS, ASHLAR, STONE_BRICKS});
                };
                BlockState wall = pick(p, 3, new BlockState[]{PLASTER[P_WHITE], PLASTER[P_CREAM], PLASTER[P_WHITE], PLASTER[P_OCHRE],
                        OAK_PLANKS});
                Roof roof = switch (wealth) {
                    case POOR -> pick(p, 2, new Roof[]{SPRUCE, OAK, DARK_OAK});
                    case MODEST -> pick(p, 2, new Roof[]{DARK_OAK, SPRUCE, SLATE_KIT, DARK_OAK});
                    default -> pick(p, 2, new Roof[]{SLATE_KIT, SLATE, DARK_OAK, SLATE_KIT});
                };
                int tone = roll(p, 4) < 75 ? 0 : 1;
                Course beams = tone == 0 ? C_DARK : C_SPRUCE;
                yield new Style(d, ground, wall, true, tone, roof, CAP_SMOOTH, wealth == POOR ? beams : C_STONE, beams, ground == COBBLE ? C_COBBLE : C_STONE,
                        wealth == POOR ? DARK_WOOD : POLISHED_ANDESITE, CHISELED_STONE_BRICKS, null, W_LEADED, W_COTTAGE, false, R_TIMBER,
                        0, bloom, new int[]{1, 1, 2, 3, 5, 6}, new int[]{SH_GREEN, SH_SAGE, SH_NATURAL, SH_BLUE}, shutterPct, false, false,
                        SMOOTH_SLAB, roll(p, 5) < 70 ? 0 : 2, false, wealth == PROSPEROUS && two, wall, 55, false, false);
            }
        };
    }

    // ---- inside: the palette the rooms are furnished from -----------------------------------------------------------

    /**
     * What a house's rooms are made of: floor boards, the wood its furniture is cut from (planks, stairs and slabs for
     * benches and tables, fence posts, trapdoors), its door, and the colours of its beds and rugs.
     */
    public record Palette(BlockState floor, String wood, int door, String bed, String carpet) {}

    private static final String[] BEDS = {"red", "blue", "green", "brown", "cyan", "white", "yellow", "orange", "light_gray"};

    public static Palette palette(Plot p) {
        int fam = family(p);
        String bed = BEDS[roll(p, 9) % BEDS.length];
        String carpet = pick(p, 10, new String[]{"red", "brown", "green", "blue", "orange", "light_gray", "cyan"});
        return switch (fam) {
            case FROST -> new Palette(SPRUCE_PLANKS, "spruce", D_SPRUCE, bed, pick(p, 10, new String[]{"brown", "red", "gray"}));
            case DESERT -> new Palette(SMOOTH_SANDSTONE, "acacia", D_ACACIA, bed, pick(p, 10, new String[]{"orange", "red", "yellow"}));
            case SAVANNA -> new Palette(ACACIA_PLANKS, "acacia", D_ACACIA, bed, pick(p, 10, new String[]{"orange", "brown", "red"}));
            case MIRE -> new Palette(MANGROVE_PLANKS, "mangrove", D_MANGROVE, bed, pick(p, 10, new String[]{"green", "brown"}));
            case HIGHLAND -> new Palette(SPRUCE_PLANKS, "spruce", D_SPRUCE, bed, carpet);
            case ELDER -> new Palette(DARK_PLANKS, "dark_oak", D_DARK, bed, pick(p, 10, new String[]{"green", "brown", "red"}));
            case COAST -> new Palette(BIRCH_PLANKS, "birch", D_SPRUCE, bed, pick(p, 10, new String[]{"blue", "light_blue", "white"}));
            case JUNGLE -> new Palette(JUNGLE_PLANKS, "jungle", D_JUNGLE, bed, pick(p, 10, new String[]{"lime", "orange", "green"}));
            case CHERRY -> new Palette(CHERRY_PLANKS, "cherry", D_CHERRY, bed, pick(p, 10, new String[]{"pink", "white", "magenta"}));
            default -> new Palette(roll(p, 11) < 60 ? OAK_PLANKS : SPRUCE_PLANKS, roll(p, 12) < 55 ? "oak" : "spruce",
                    roll(p, 13) < 50 ? D_OAK : D_SPRUCE, bed, carpet);
        };
    }
}
