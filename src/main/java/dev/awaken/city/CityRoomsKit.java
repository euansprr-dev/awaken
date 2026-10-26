package dev.awaken.city;

import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.city.CityBlocks.dirs;
import static dev.awaken.city.CityBlocks.of;

/**
 * The room filler's block states ({@link CityInteriors}), resolved once by id at the first furnish (the registries are
 * ready by then). Directional arrays are indexed by side, 0 N 1 E 2 S 3 W, as everywhere in the city. Block entities
 * only where the writer makes them: beds, chests, barrels, furnaces, lecterns, brewing stands.
 */
final class CityRoomsKit {
    private CityRoomsKit() {}

    static final String[] WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry"};
    static final String[] COLOURS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    static final int WHITE = 0, ORANGE = 1, LIGHT_BLUE = 3, YELLOW = 4, LIME = 5, PINK = 6, LIGHT_GRAY = 8, CYAN = 9,
            BROWN = 12, RED = 14;

    /**
     * One wood's furniture: planks, slabs, stairs by facing (bottom: seats and steps; top: counters and mantels), fence
     * posts by a mask of the sides they join (1 N, 2 E, 4 S, 8 W), closed hatches and open rails by facing, the pressure
     * plate, stripped logs by axis (0 x, 1 y, 2 z).
     */
    record Wood(BlockState planks, BlockState slab, BlockState[] stair, BlockState[] stairTop, BlockState[] fence,
                BlockState[] hatch, BlockState[] rail, BlockState plate, BlockState[] log) {}

    static final Wood[] WOOD = new Wood[WOODS.length];
    static final BlockState[][][] BED = new BlockState[COLOURS.length][4][2];
    static final BlockState[] CARPET = new BlockState[COLOURS.length];
    static final BlockState[] WOOL = new BlockState[COLOURS.length];

    static {
        for (int i = 0; i < WOODS.length; i++) {
            String w = "minecraft:" + WOODS[i];
            BlockState[] fence = new BlockState[16];
            for (int m = 0; m < 16; m++) {
                fence[m] = of(w + "_fence[north=" + ((m & 1) != 0) + ",east=" + ((m & 2) != 0) + ",south=" + ((m & 4) != 0)
                        + ",west=" + ((m & 8) != 0) + "]");
            }
            String log = "minecraft:stripped_" + WOODS[i] + "_log";
            WOOD[i] = new Wood(of(w + "_planks"), of(w + "_slab[type=bottom]"),
                    dirs(w + "_stairs[facing=%s,half=bottom,shape=straight]"), dirs(w + "_stairs[facing=%s,half=top,shape=straight]"),
                    fence, dirs(w + "_trapdoor[facing=%s,half=top,open=false]"), dirs(w + "_trapdoor[facing=%s,half=bottom,open=true]"),
                    of(w + "_pressure_plate"), new BlockState[]{of(log + "[axis=x]"), of(log + "[axis=y]"), of(log + "[axis=z]")});
        }
        for (int c = 0; c < COLOURS.length; c++) {
            String n = "minecraft:" + COLOURS[c];
            for (int d = 0; d < 4; d++) {
                BED[c][d][0] = of(n + "_bed[facing=" + CityBlocks.DIR[d] + ",part=foot,occupied=false]");
                BED[c][d][1] = of(n + "_bed[facing=" + CityBlocks.DIR[d] + ",part=head,occupied=false]");
            }
            CARPET[c] = of(n + "_carpet");
            WOOL[c] = of(n + "_wool");
        }
    }

    static Wood wood(String name) {
        for (int i = 0; i < WOODS.length; i++) if (WOODS[i].equals(name)) return WOOD[i];
        return WOOD[1];
    }

    static int colour(String name) {
        for (int i = 0; i < COLOURS.length; i++) if (COLOURS[i].equals(name)) return i;
        return RED;
    }

    // storage and the kitchen
    static final BlockState[] CHEST = dirs("minecraft:chest[facing=%s,type=single]");
    static final BlockState BARREL = of("minecraft:barrel[facing=up,open=false]");
    /** A barrel on its side, its lid toward the room. */
    static final BlockState[] BARREL_LYING = dirs("minecraft:barrel[facing=%s,open=false]");
    static final BlockState[][] FURNACE = {dirs("minecraft:furnace[facing=%s,lit=false]"), dirs("minecraft:furnace[facing=%s,lit=true]")};
    static final BlockState[][] SMOKER = {dirs("minecraft:smoker[facing=%s,lit=false]"), dirs("minecraft:smoker[facing=%s,lit=true]")};
    static final BlockState[][] BLAST = {dirs("minecraft:blast_furnace[facing=%s,lit=false]"), dirs("minecraft:blast_furnace[facing=%s,lit=true]")};
    static final BlockState CRAFTING = of("minecraft:crafting_table");
    static final BlockState BOOKSHELF = of("minecraft:bookshelf");

    // the trades
    static final BlockState[] ANVIL = dirs("minecraft:anvil[facing=%s]");
    static final BlockState[] GRINDSTONE = dirs("minecraft:grindstone[face=floor,facing=%s]");
    static final BlockState[] STONECUTTER = dirs("minecraft:stonecutter[facing=%s]");
    static final BlockState[] LOOM = dirs("minecraft:loom[facing=%s]");
    static final BlockState[] LECTERN = dirs("minecraft:lectern[facing=%s,has_book=false,powered=false]");
    static final BlockState SMITHING = of("minecraft:smithing_table");
    static final BlockState FLETCHING = of("minecraft:fletching_table");
    static final BlockState CARTOGRAPHY = of("minecraft:cartography_table");
    static final BlockState[] COMPOSTER = {of("minecraft:composter[level=3]"), of("minecraft:composter[level=5]"), of("minecraft:composter[level=7]")};
    static final BlockState[] HAY_BALE = {of("minecraft:hay_block[axis=x]"), of("minecraft:hay_block[axis=y]"), of("minecraft:hay_block[axis=z]")};
    /** A tub of water (the smith's quench, the washer's and the healer's). */
    static final BlockState TUB = of("minecraft:water_cauldron[level=3]");
    static final BlockState BREWING = of("minecraft:brewing_stand");
    static final BlockState TARGET = of("minecraft:target");
    static final BlockState LINEN = of("minecraft:white_wool_slab[type=bottom]");

    // light and detail (lanterns and the chain are CityBlocks')
    /** Candles by count (1-3) and lit (0, 1). */
    static final BlockState[][] CANDLES = new BlockState[3][2];
    /** Flowers for a sill. */
    static final BlockState[] SILL_FLOWERS = {of("minecraft:potted_red_tulip"), of("minecraft:potted_poppy"),
            of("minecraft:potted_allium"), of("minecraft:potted_oxeye_daisy"), of("minecraft:potted_cornflower"),
            of("minecraft:potted_azure_bluet"), of("minecraft:potted_orange_tulip"), of("minecraft:potted_lily_of_the_valley"),
            of("minecraft:potted_white_tulip"), of("minecraft:potted_pink_tulip")};
    /** Green things in pots (the healer's herbs, houseplants on the floor). */
    static final BlockState[] HERBS = {of("minecraft:potted_fern"), of("minecraft:potted_azalea_bush"),
            of("minecraft:potted_flowering_azalea_bush"), of("minecraft:potted_oak_sapling"), of("minecraft:potted_red_mushroom"),
            of("minecraft:potted_brown_mushroom")};
    /** A land's own plant: desert, savanna, mire, jungle, cherry (by VillageStyles family; -1 none). */
    static final BlockState CACTUS = of("minecraft:potted_cactus"), DEAD_BUSH = of("minecraft:potted_dead_bush"),
            PROPAGULE = of("minecraft:potted_mangrove_propagule"), BAMBOO = of("minecraft:potted_bamboo"),
            CHERRY = of("minecraft:potted_cherry_sapling"), SPRUCE_SAPLING = of("minecraft:potted_spruce_sapling");

    // the hearth's trim, by the chimney's crown (0 brick, 1 mud brick, 2 stone brick)
    static final BlockState[][] MANTEL = {dirs("minecraft:brick_stairs[facing=%s,half=top,shape=straight]"),
            dirs("minecraft:mud_brick_stairs[facing=%s,half=top,shape=straight]"),
            dirs("minecraft:stone_brick_stairs[facing=%s,half=top,shape=straight]")};

    static {
        for (int n = 0; n < 3; n++) {
            CANDLES[n][0] = of("minecraft:candle[candles=" + (n + 1) + ",lit=false]");
            CANDLES[n][1] = of("minecraft:candle[candles=" + (n + 1) + ",lit=true]");
        }
    }
}
