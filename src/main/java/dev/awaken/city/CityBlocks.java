package dev.awaken.city;

import dev.awaken.Awaken;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Every block state the blockout city places, resolved once by id (vanilla blocks only until Milestone 4's kit).
 * Loaded on the first paint, when the registries are ready. Directional arrays are indexed by side: 0 N, 1 E, 2 S, 3 W.
 *
 * <p>Rules the painter keeps: no unstripped {@code *_log}, no leaves without {@code persistent=true}, no mushroom
 * blocks (the vegetation sweep removes exactly those, so whatever the city places survives it), no block entities
 * except one bell per bell tower, and few light sources.
 */
final class CityBlocks {
    private CityBlocks() {}

    static final String[] DIR = {"north", "east", "south", "west"};

    static BlockState of(String spec) {
        String id = spec;
        String props = null;
        int b = spec.indexOf('[');
        if (b >= 0) {
            id = spec.substring(0, b);
            props = spec.substring(b + 1, spec.length() - 1);
        }
        Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
        if (block == Blocks.AIR && !id.endsWith(":air")) {
            Awaken.LOG.warn("[city] unknown block {}", spec);
            return Blocks.STONE.defaultBlockState();
        }
        BlockState state = block.defaultBlockState();
        if (props != null && !props.isEmpty()) {
            for (String kv : props.split(",")) {
                String[] p = kv.split("=");
                Property<?> prop = block.getStateDefinition().getProperty(p[0].trim());
                if (prop == null) {
                    Awaken.LOG.warn("[city] {} has no property {}", id, p[0]);
                    continue;
                }
                state = with(state, prop, p[1].trim());
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> prop, String value) {
        return prop.getValue(value).map(v -> state.setValue(prop, v)).orElse(state);
    }

    /** Four states, one per side, from a spec with {@code %s} where the direction name goes. */
    static BlockState[] dirs(String spec) {
        BlockState[] r = new BlockState[4];
        for (int i = 0; i < 4; i++) r[i] = of(spec.formatted(DIR[i]));
        return r;
    }

    /** A roof material: the full block, the bottom slab and stairs by facing (bottom half), outer corners too. */
    record Roof(BlockState block, BlockState slab, BlockState[] stair, BlockState[] outerLeft, BlockState[] outerRight,
                BlockState[] stairTop) {
        static Roof of(String block, String slab, String stairs) {
            return new Roof(CityBlocks.of(block), CityBlocks.of(slab + "[type=bottom]"),
                    dirs(stairs + "[facing=%s,half=bottom,shape=straight]"),
                    dirs(stairs + "[facing=%s,half=bottom,shape=outer_left]"),
                    dirs(stairs + "[facing=%s,half=bottom,shape=outer_right]"),
                    dirs(stairs + "[facing=%s,half=top,shape=straight]"));
        }

        static Roof named(String base) {
            return of("minecraft:" + base, "minecraft:" + base + "_slab", "minecraft:" + base + "_stairs");
        }

        static Roof plural(String block, String base) {
            return of("minecraft:" + block, "minecraft:" + base + "_slab", "minecraft:" + base + "_stairs");
        }
    }

    static final BlockState AIR = Blocks.AIR.defaultBlockState();
    static final BlockState STONE = of("minecraft:stone");
    static final BlockState WATER = of("minecraft:water");
    static final BlockState DIRT = of("minecraft:dirt");
    static final BlockState GRASS = of("minecraft:grass_block");
    static final BlockState COARSE_DIRT = of("minecraft:coarse_dirt");
    static final BlockState GRAVEL = of("minecraft:gravel");
    static final BlockState PATH = of("minecraft:dirt_path");
    static final BlockState PACKED_MUD = of("minecraft:packed_mud");
    static final BlockState MOSS_CARPET_SAFE = of("minecraft:short_grass");

    // paving and masonry
    static final BlockState COBBLE = of("minecraft:cobblestone");
    static final BlockState MOSSY_COBBLE = of("minecraft:mossy_cobblestone");
    static final BlockState ANDESITE = of("minecraft:andesite");
    static final BlockState POLISHED_ANDESITE = of("minecraft:polished_andesite");
    static final BlockState POLISHED_DIORITE = of("minecraft:polished_diorite");
    static final BlockState DIORITE = of("minecraft:diorite");
    static final BlockState CALCITE = of("minecraft:calcite");
    static final BlockState SMOOTH_STONE = of("minecraft:smooth_stone");
    static final BlockState STONE_BRICKS = of("minecraft:stone_bricks");
    static final BlockState MOSSY_STONE_BRICKS = of("minecraft:mossy_stone_bricks");
    static final BlockState CRACKED_STONE_BRICKS = of("minecraft:cracked_stone_bricks");
    static final BlockState CHISELED_STONE_BRICKS = of("minecraft:chiseled_stone_bricks");
    static final BlockState TUFF_BRICKS = of("minecraft:tuff_bricks");
    static final BlockState POLISHED_TUFF = of("minecraft:polished_tuff");
    static final BlockState DEEPSLATE_BRICKS = of("minecraft:deepslate_bricks");
    static final BlockState DEEPSLATE_TILES = of("minecraft:deepslate_tiles");
    static final BlockState POLISHED_DEEPSLATE = of("minecraft:polished_deepslate");
    static final BlockState QUARTZ_BRICKS = of("minecraft:quartz_bricks");
    static final BlockState SMOOTH_QUARTZ = of("minecraft:smooth_quartz");
    static final BlockState BRICKS = of("minecraft:bricks");
    static final BlockState MUD_BRICKS = of("minecraft:mud_bricks");
    static final BlockState CINNABAR_BRICKS = of("minecraft:cinnabar_bricks");
    static final BlockState SMOOTH_SANDSTONE = of("minecraft:smooth_sandstone");
    static final BlockState CUT_SANDSTONE = of("minecraft:cut_sandstone");
    static final BlockState WHITE_TERRACOTTA = of("minecraft:white_terracotta");
    static final BlockState PALE_PLANKS = of("minecraft:pale_oak_planks");
    static final BlockState SPRUCE_PLANKS = of("minecraft:spruce_planks");
    static final BlockState DARK_PLANKS = of("minecraft:dark_oak_planks");
    static final BlockState OAK_PLANKS = of("minecraft:oak_planks");
    static final BlockState BIRCH_PLANKS = of("minecraft:birch_planks");
    static final BlockState POLISHED_BLACKSTONE_BRICKS = of("minecraft:polished_blackstone_bricks");
    static final BlockState GILDED = of("minecraft:waxed_copper_block");
    static final BlockState BRONZE = of("minecraft:waxed_exposed_cut_copper");
    static final BlockState VERDIGRIS = of("minecraft:waxed_oxidized_cut_copper");
    static final BlockState VERDIGRIS_BLOCK = of("minecraft:waxed_oxidized_copper");
    static final BlockState GLASS = of("minecraft:glass");
    static final BlockState TINTED = of("minecraft:tinted_glass");

    // timber: stripped logs only (the sweep owns unstripped ones)
    static final BlockState[] POST = {of("minecraft:stripped_dark_oak_log[axis=y]"), of("minecraft:stripped_spruce_log[axis=y]")};
    static final BlockState[] BEAM_X = {of("minecraft:stripped_dark_oak_log[axis=x]"), of("minecraft:stripped_spruce_log[axis=x]")};
    static final BlockState[] BEAM_Z = {of("minecraft:stripped_dark_oak_log[axis=z]"), of("minecraft:stripped_spruce_log[axis=z]")};
    static final BlockState DARK_WOOD = of("minecraft:dark_oak_wood[axis=y]");
    static final BlockState SPRUCE_WOOD = of("minecraft:spruce_wood[axis=y]");
    static final BlockState[] SPRUCE_BEAM = {of("minecraft:stripped_spruce_log[axis=x]"), of("minecraft:stripped_spruce_log[axis=z]")};
    static final BlockState[] CORBEL = dirs("minecraft:dark_oak_stairs[facing=%s,half=top]");
    static final BlockState[] STONE_CORBEL = dirs("minecraft:stone_brick_stairs[facing=%s,half=top]");
    static final BlockState[] STONE_STAIR = dirs("minecraft:stone_brick_stairs[facing=%s,half=bottom]");
    static final BlockState[] ANDESITE_STAIR = dirs("minecraft:polished_andesite_stairs[facing=%s,half=bottom]");
    static final BlockState[] DIORITE_STAIR = dirs("minecraft:polished_diorite_stairs[facing=%s,half=bottom]");
    static final BlockState STONE_SLAB = of("minecraft:stone_brick_slab[type=bottom]");
    static final BlockState SMOOTH_SLAB = of("minecraft:smooth_stone_slab[type=bottom]");
    static final BlockState STONE_SLAB_TOP = of("minecraft:stone_brick_slab[type=top]");

    /** What fills a closed house behind its windows (dark through the glass; opaque so the culler can work). */
    static final BlockState CORE = of("minecraft:dark_oak_planks");
    static final BlockState FLOOR = of("minecraft:spruce_planks");

    // windows, doors, shutters
    /** Glass pane joined along the wall: index 0 runs along x (north and south walls), 1 along z. */
    static final BlockState[] PANE = {of("minecraft:glass_pane[east=true,west=true]"), of("minecraft:glass_pane[north=true,south=true]")};
    static final BlockState[] BARS = {of("minecraft:iron_bars[east=true,west=true]"), of("minecraft:iron_bars[north=true,south=true]")};
    static final String[] SHUTTER_WOOD = {"spruce", "dark_oak", "mangrove", "warped", "birch", "crimson"};
    static final BlockState[][] SHUTTER = new BlockState[SHUTTER_WOOD.length][];
    static final BlockState[][] DOOR_LOWER = new BlockState[3][], DOOR_UPPER = new BlockState[3][];
    static final BlockState[] BOX_SHELF = dirs("minecraft:spruce_trapdoor[facing=%s,half=top,open=false]");
    static final BlockState[] FLOWERS = {of("minecraft:potted_red_tulip"), of("minecraft:potted_poppy"),
            of("minecraft:potted_allium"), of("minecraft:potted_oxeye_daisy"), of("minecraft:potted_cornflower"),
            of("minecraft:potted_azure_bluet"), of("minecraft:potted_orange_tulip")};

    static {
        for (int i = 0; i < SHUTTER_WOOD.length; i++)
            SHUTTER[i] = dirs("minecraft:" + SHUTTER_WOOD[i] + "_trapdoor[facing=%s,open=true,half=bottom]");
        String[] doors = {"spruce", "dark_oak", "oak"};
        for (int i = 0; i < doors.length; i++) {
            DOOR_LOWER[i] = dirs("minecraft:" + doors[i] + "_door[facing=%s,half=lower,hinge=left,open=false]");
            DOOR_UPPER[i] = dirs("minecraft:" + doors[i] + "_door[facing=%s,half=upper,hinge=left,open=false]");
        }
    }

    // roofs, by district (see CityPalette)
    static final Roof SLATE = Roof.plural("deepslate_tiles", "deepslate_tile");
    static final Roof SLATE_LIGHT = Roof.named("cobbled_deepslate");
    static final Roof SLATE_SMOOTH = Roof.named("polished_deepslate");
    static final Roof SLATE_TUFF = Roof.plural("tuff_bricks", "tuff_brick");
    static final Roof BLACKSTONE = Roof.plural("polished_blackstone_bricks", "polished_blackstone_brick");
    static final Roof SPRUCE = Roof.plural("spruce_planks", "spruce");
    static final Roof DARK_OAK = Roof.plural("dark_oak_planks", "dark_oak");
    static final Roof MUD = Roof.plural("mud_bricks", "mud_brick");
    static final Roof COPPER = Roof.named("waxed_cut_copper");
    static final Roof BRONZE_ROOF = Roof.named("waxed_exposed_cut_copper");
    static final Roof WEATHERED = Roof.named("waxed_weathered_cut_copper");
    static final Roof GREEN = Roof.named("waxed_oxidized_cut_copper");
    static final Roof RESIN = Roof.plural("resin_bricks", "resin_brick");
    static final Roof DARK_PRISMARINE = Roof.named("dark_prismarine");
    static final Roof PALE = Roof.named("polished_diorite");
    static final Roof QUARTZ = Roof.of("minecraft:smooth_quartz", "minecraft:smooth_quartz_slab", "minecraft:smooth_quartz_stairs");
    static final Roof STONE_ROOF = Roof.plural("stone_bricks", "stone_brick");
    static final Roof COBBLE_ROOF = Roof.plural("cobblestone", "cobblestone");
    static final Roof BRICK = Roof.plural("bricks", "brick");
    static final Roof CINNABAR = Roof.plural("cinnabar_bricks", "cinnabar_brick");
    static final Roof GRANITE = Roof.named("polished_granite");

    // light and street furniture
    static final BlockState LANTERN = of("minecraft:lantern[hanging=false]");
    static final BlockState LANTERN_HANGING = of("minecraft:lantern[hanging=true]");
    static final BlockState COPPER_LANTERN = of("minecraft:copper_lantern[hanging=false]");
    static final BlockState FENCE = of("minecraft:dark_oak_fence");
    static final BlockState SPRUCE_FENCE = of("minecraft:spruce_fence");
    static final BlockState[] FENCE_TO = dirs("minecraft:dark_oak_fence[%s=true]");
    static final BlockState WALL_POST = of("minecraft:stone_brick_wall[up=true]");
    static final BlockState[] WALL_RUN = {of("minecraft:stone_brick_wall[up=false,east=low,west=low]"),
            of("minecraft:stone_brick_wall[up=false,north=low,south=low]")};
    static final BlockState DIORITE_WALL_POST = of("minecraft:diorite_wall[up=true]");
    static final BlockState[] DIORITE_WALL_RUN = {of("minecraft:diorite_wall[up=false,east=low,west=low]"),
            of("minecraft:diorite_wall[up=false,north=low,south=low]")};
    static final BlockState CHAIN_Y = of("minecraft:iron_chain[axis=y]");

    /** Connected runs, indexed by a mask of neighbours: 1 north, 2 east, 4 south, 8 west (the painter sets shapes). */
    static final BlockState[] SPRUCE_FENCES = connected("minecraft:spruce_fence", false);
    static final BlockState[] STONE_WALLS = connected("minecraft:stone_brick_wall", true);
    static final BlockState[] DIORITE_WALLS = connected("minecraft:diorite_wall", true);

    private static BlockState[] connected(String id, boolean wall) {
        BlockState[] out = new BlockState[16];
        for (int m = 0; m < 16; m++) {
            StringBuilder sb = new StringBuilder(id).append('[');
            String on = wall ? "low" : "true", off = wall ? "none" : "false";
            sb.append("north=").append((m & 1) != 0 ? on : off).append(",east=").append((m & 2) != 0 ? on : off)
                    .append(",south=").append((m & 4) != 0 ? on : off).append(",west=").append((m & 8) != 0 ? on : off);
            if (wall) sb.append(",up=").append(m == 5 || m == 10 ? "false" : "true");
            out[m] = of(sb.append(']').toString());
        }
        return out;
    }

    /** Ladders by the side they face (away from the wall they hang on), and one in water. */
    static final BlockState[] LADDER = dirs("minecraft:ladder[facing=%s]");
    static final BlockState[] LADDER_WET = dirs("minecraft:ladder[facing=%s,waterlogged=true]");
    static final BlockState ROD = of("minecraft:lightning_rod[facing=up]");
    static final BlockState COPPER_ROD = of("minecraft:waxed_lightning_rod[facing=up]");
    static final BlockState BELL = of("minecraft:bell[attachment=ceiling,facing=west]");
    static final BlockState HAY = of("minecraft:hay_block[axis=y]");
    static final BlockState POT = of("minecraft:flower_pot");

    // greenery: leaves are always persistent (the vegetation sweep removes non-persistent ones)
    static final BlockState OAK_LEAVES = of("minecraft:oak_leaves[persistent=true]");
    static final BlockState BIRCH_LEAVES = of("minecraft:birch_leaves[persistent=true]");
    static final BlockState CHERRY_LEAVES = of("minecraft:cherry_leaves[persistent=true]");
    static final BlockState AZALEA_LEAVES = of("minecraft:azalea_leaves[persistent=true]");
    static final BlockState FLOWERING_AZALEA = of("minecraft:flowering_azalea_leaves[persistent=true]");
    static final BlockState OAK_TRUNK = of("minecraft:oak_wood[axis=y]");
    static final BlockState BIRCH_TRUNK = of("minecraft:birch_wood[axis=y]");
    static final BlockState CHERRY_TRUNK = of("minecraft:cherry_wood[axis=y]");
    static final BlockState[] BLOOMS = {of("minecraft:poppy"), of("minecraft:dandelion"), of("minecraft:cornflower"),
            of("minecraft:oxeye_daisy"), of("minecraft:allium"), of("minecraft:azure_bluet"), of("minecraft:red_tulip")};
    static final BlockState SHORT_GRASS = of("minecraft:short_grass");

    // fields outside the wall
    static final BlockState FARMLAND = of("minecraft:farmland[moisture=7]");
    static final BlockState[] CROPS = {of("minecraft:wheat[age=7]"), of("minecraft:carrots[age=7]"),
            of("minecraft:potatoes[age=7]"), of("minecraft:beetroots[age=3]"), of("minecraft:wheat[age=5]")};

    // ---- Milestone 4: the climbing grid's courses and the kit ----------------------------------------------------

    /**
     * An upside-down stair run, the profile of every course, cornice and hood (look bible §3.1): the top half sticks out
     * a full block (the lip, its top on the grid, its face at the cell's outer edge), the bottom half hugs the wall.
     * Indexed by the side the stair faces, which is toward the wall.
     */
    record Course(BlockState[] straight, BlockState[] outerLeft, BlockState[] outerRight) {
        static Course of(String stairs) {
            return new Course(dirs(stairs + "[facing=%s,half=top,shape=straight]"),
                    dirs(stairs + "[facing=%s,half=top,shape=outer_left]"),
                    dirs(stairs + "[facing=%s,half=top,shape=outer_right]"));
        }

        /** The piece on an outside corner, its wall-side quarter toward sides a and b (b beside a). */
        BlockState corner(int a, int b) {
            return b == ((a + 3) & 3) ? outerLeft[a] : outerRight[a];
        }
    }

    static final Course C_STONE = Course.of("minecraft:stone_brick_stairs");
    static final Course C_DARK = Course.of("minecraft:dark_oak_stairs");
    static final Course C_SPRUCE = Course.of("minecraft:spruce_stairs");
    static final Course C_BRONZE = Course.of("minecraft:waxed_exposed_cut_copper_stairs");
    static final Course C_SANDSTONE = Course.of("minecraft:smooth_sandstone_stairs");
    static final Course C_QUARTZ = Course.of("minecraft:smooth_quartz_stairs");
    static final Course C_DIORITE = Course.of("minecraft:polished_diorite_stairs");
    static final Course C_BLACKSTONE = Course.of("minecraft:polished_blackstone_brick_stairs");
    static final Course C_BRICK = Course.of("minecraft:brick_stairs");
    static final Course C_COBBLE = Course.of("minecraft:cobblestone_stairs");
    static final Course C_VERDIGRIS = Course.of("minecraft:waxed_oxidized_cut_copper_stairs");
    static final Course C_ANDESITE = Course.of("minecraft:polished_andesite_stairs");

    /** A thin board canopy over a shopfront (B3 swaps in the striped awnings), and the sill board under a loading door. */
    static final BlockState CANOPY = of("minecraft:dark_oak_slab[type=top]");
    static final BlockState[] SILL_BOARD = dirs("minecraft:spruce_trapdoor[facing=%s,half=top,open=false]");
    static final BlockState FASCIA = of("minecraft:dark_oak_planks");

    // ridge caps: a lighter stone on every ridge, the line that breaks the roofscape's mass (bible §3.4)
    static final BlockState CAP_SMOOTH = SMOOTH_SLAB;
    static final BlockState CAP_BRICK = of("minecraft:brick_slab[type=bottom]");
    static final BlockState CAP_QUARTZ = of("minecraft:smooth_quartz_slab[type=bottom]");
    static final BlockState CAP_ANDESITE = of("minecraft:polished_andesite_slab[type=bottom]");
    static final BlockState CAP_STONE = STONE_SLAB;
    static final BlockState[] MOSSY_STAIR = dirs("minecraft:mossy_cobblestone_stairs[facing=%s,half=bottom,shape=straight]");
    static final BlockState QUARTZ_PILLAR = of("minecraft:quartz_pillar[axis=y]");
    static final BlockState YELLOW_TERRACOTTA = of("minecraft:yellow_terracotta");
    static final BlockState RED_TERRACOTTA = of("minecraft:red_terracotta");
    static final BlockState TERRACOTTA = of("minecraft:terracotta");
    static final BlockState PINK_TERRACOTTA = of("minecraft:pink_terracotta");
    static final BlockState LIGHT_GRAY_TERRACOTTA = of("minecraft:light_gray_terracotta");

    // the kit's roofs
    static final Roof SLATE_KIT = Roof.of("awaken:slate_tiles", "awaken:slate_tile_slab", "awaken:slate_tile_stairs");
    static final Roof CLAY = Roof.of("awaken:clay_tiles", "awaken:clay_tile_slab", "awaken:clay_tile_stairs");
    static final Roof TUFF_ROOF = SLATE_TUFF;

    /** Window boxes by style (0 wood, 1 iron, 2 stone), bloom (0 none, 1 red, 2 yellow, 3 blue, 4 pink, 5 white, 6 herbs) and side. */
    static final String[] BOX_STYLE = {"wood", "iron", "stone"};
    static final String[] BLOOM = {"none", "red", "yellow", "blue", "pink", "white", "herbs"};
    static final BlockState[][][] BOX = new BlockState[3][7][];
    /** Shutters by colour, hinge (0 left, 1 right), half (0 lower, 1 upper) and side. */
    static final String[] SHUTTER_COLOUR = {"blue", "sage", "oxblood", "natural", "green", "cream"};
    static final int SH_BLUE = 0, SH_SAGE = 1, SH_OXBLOOD = 2, SH_NATURAL = 3, SH_GREEN = 4, SH_CREAM = 5;
    static final BlockState[][][][] SHUTTER_KIT = new BlockState[6][2][2][];
    static final BlockState[] PIPE = dirs("awaken:drainpipe[facing=%s,part=pipe]");
    static final BlockState[] PIPE_HEAD = dirs("awaken:drainpipe[facing=%s,part=head]");
    static final BlockState[] PIPE_SHOE = dirs("awaken:drainpipe[facing=%s,part=shoe]");
    static final BlockState[] TRELLIS = dirs("awaken:trellis[facing=%s,ivy=false]");
    static final BlockState[] TRELLIS_IVY = dirs("awaken:trellis[facing=%s,ivy=true]");
    /** Kit windows by style and half (0 lower, 1 upper, 2 single); placed unlit (the households light them, B3). */
    static final String[] WIN_STYLE = {"leaded", "sash", "arched", "cottage", "warehouse", "barred", "works", "shop", "dormer", "round"};
    static final int W_LEADED = 0, W_SASH = 1, W_ARCHED = 2, W_COTTAGE = 3, W_WAREHOUSE = 4, W_BARRED = 5, W_WORKS = 6,
            W_SHOP = 7, W_DORMER = 8, W_ROUND = 9;
    static final BlockState[][] WINDOW = new BlockState[WIN_STYLE.length][3];
    static final BlockState[][] LOADING = new BlockState[4][];
    static final BlockState[] HOIST = dirs("awaken:hoist_beam[facing=%s,pulley=true]");
    static final BlockState ROPE = of("awaken:rope");
    static final BlockState[] CROWN = {of("awaken:chimney_bricks[material=brick]"), of("awaken:chimney_bricks[material=mud]"),
            of("awaken:chimney_bricks[material=stone]")};

    // the walls' kit (decision 27: weighted texture variants per block, so long walls never tile)
    static final String[] PLASTER_COLOUR = {"white", "cream", "ochre", "rose", "clay", "mauve", "oxblood", "sage"};
    static final int P_WHITE = 0, P_CREAM = 1, P_OCHRE = 2, P_ROSE = 3, P_CLAY = 4, P_MAUVE = 5, P_OXBLOOD = 6, P_SAGE = 7;
    static final BlockState[] PLASTER = new BlockState[PLASTER_COLOUR.length];
    /** Kit timber by tone (0 dark, 1 pale): posts (y), beams along x and along z. */
    static final BlockState[] T_POST = {of("awaken:timber[axis=y,tone=dark]"), of("awaken:timber[axis=y,tone=pale]")};
    static final BlockState[] T_BEAM_X = {of("awaken:timber[axis=x,tone=dark]"), of("awaken:timber[axis=x,tone=pale]")};
    static final BlockState[] T_BEAM_Z = {of("awaken:timber[axis=z,tone=dark]"), of("awaken:timber[axis=z,tone=pale]")};
    static final BlockState CITY_BRICKS = of("awaken:city_bricks");
    static final BlockState ASHLAR = of("awaken:ashlar");
    static final BlockState CHISELED_SANDSTONE = of("minecraft:chiseled_sandstone");
    static final BlockState CHISELED_QUARTZ = of("minecraft:chiseled_quartz_block");

    static {
        for (int i = 0; i < PLASTER_COLOUR.length; i++) PLASTER[i] = of("awaken:plaster[colour=" + PLASTER_COLOUR[i] + "]");
        for (int s = 0; s < 3; s++)
            for (int k = 0; k < 7; k++) BOX[s][k] = dirs("awaken:flower_box[facing=%s,style=" + BOX_STYLE[s] + ",bloom=" + BLOOM[k] + "]");
        for (int c = 0; c < SHUTTER_COLOUR.length; c++)
            for (int h = 0; h < 2; h++)
                for (int v = 0; v < 2; v++)
                    SHUTTER_KIT[c][h][v] = dirs("awaken:shutter[facing=%s,colour=" + SHUTTER_COLOUR[c] + ",hinge=" + (h == 0 ? "left" : "right")
                            + ",half=" + (v == 0 ? "lower" : "upper") + "]");
        String[] halves = {"lower", "upper", "single"};
        for (int s = 0; s < WIN_STYLE.length; s++)
            for (int h = 0; h < 3; h++) WINDOW[s][h] = of("awaken:lit_window[style=" + WIN_STYLE[s] + ",half=" + halves[h] + ",lit=false]");
        String[] parts = {"lower_left", "lower_right", "upper_left", "upper_right"};
        for (int i = 0; i < 4; i++) LOADING[i] = dirs("awaken:loading_door[facing=%s,part=" + parts[i] + "]");
    }

    // the market stalls' awnings: wool stairs in stripes
    static final String[] AWNING = {"red", "yellow", "light_blue", "white", "orange", "lime"};
    static final BlockState[][] AWNING_STAIR = new BlockState[AWNING.length][];
    static final BlockState[] AWNING_SLAB = new BlockState[AWNING.length];
    static final BlockState[] AWNING_WOOL = new BlockState[AWNING.length];

    static {
        for (int i = 0; i < AWNING.length; i++) {
            AWNING_STAIR[i] = dirs("minecraft:" + AWNING[i] + "_wool_stairs[facing=%s,half=bottom]");
            AWNING_SLAB[i] = of("minecraft:" + AWNING[i] + "_wool_slab[type=bottom]");
            AWNING_WOOL[i] = of("minecraft:" + AWNING[i] + "_wool");
        }
    }
}
