package dev.awaken.village.plan;

import dev.awaken.Awaken;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * The block states a settlement's own painter places (its ground, fields, pens, the dock, lamps, the bell, the forge
 * and the mill wheel), resolved once by id on the first paint. Sides index 0 N, 1 E, 2 S, 3 W.
 */
public final class VillageBlocks {
    private VillageBlocks() {}

    static final String[] DIR = {"north", "east", "south", "west"};

    public static BlockState of(String spec) {
        String id = spec, props = null;
        int b = spec.indexOf('[');
        if (b >= 0) {
            id = spec.substring(0, b);
            props = spec.substring(b + 1, spec.length() - 1);
        }
        Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
        if (block == Blocks.AIR && !id.endsWith(":air")) {
            Awaken.LOG.warn("[village] unknown block {}", spec);
            return Blocks.STONE.defaultBlockState();
        }
        BlockState state = block.defaultBlockState();
        if (props != null && !props.isEmpty()) {
            for (String kv : props.split(",")) {
                String[] p = kv.split("=");
                Property<?> prop = block.getStateDefinition().getProperty(p[0].trim());
                if (prop == null) {
                    Awaken.LOG.warn("[village] {} has no property {}", id, p[0]);
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

    public static BlockState[] dirs(String spec) {
        BlockState[] r = new BlockState[4];
        for (int i = 0; i < 4; i++) r[i] = of(spec.formatted(DIR[i]));
        return r;
    }

    /** Fence or wall runs by a mask of neighbours: 1 north, 2 east, 4 south, 8 west. */
    public static BlockState[] connected(String id, boolean wall) {
        BlockState[] out = new BlockState[16];
        for (int m = 0; m < 16; m++) {
            String on = wall ? "low" : "true", off = wall ? "none" : "false";
            StringBuilder sb = new StringBuilder(id).append("[north=").append((m & 1) != 0 ? on : off).append(",east=").append((m & 2) != 0 ? on : off)
                    .append(",south=").append((m & 4) != 0 ? on : off).append(",west=").append((m & 8) != 0 ? on : off);
            if (wall) sb.append(",up=").append(m == 5 || m == 10 ? "false" : "true");
            out[m] = of(sb.append(']').toString());
        }
        return out;
    }

    static final BlockState AIR = Blocks.AIR.defaultBlockState();
    static final BlockState WATER = of("minecraft:water");
    static final BlockState STONE = of("minecraft:stone");
    static final BlockState DIRT = of("minecraft:dirt");
    static final BlockState COARSE_DIRT = of("minecraft:coarse_dirt");
    static final BlockState GRASS = of("minecraft:grass_block");
    static final BlockState PODZOL = of("minecraft:podzol");
    static final BlockState SAND = of("minecraft:sand");
    static final BlockState SANDSTONE = of("minecraft:sandstone");
    static final BlockState SMOOTH_SANDSTONE = of("minecraft:smooth_sandstone");
    static final BlockState CUT_SANDSTONE = of("minecraft:cut_sandstone");
    static final BlockState MUD = of("minecraft:mud");
    static final BlockState PACKED_MUD = of("minecraft:packed_mud");
    static final BlockState MUD_BRICKS = of("minecraft:mud_bricks");
    static final BlockState MOSS = of("minecraft:moss_block");
    static final BlockState PATH = of("minecraft:dirt_path");
    static final BlockState GRAVEL = of("minecraft:gravel");
    static final BlockState COBBLE = of("minecraft:cobblestone");
    static final BlockState MOSSY_COBBLE = of("minecraft:mossy_cobblestone");
    static final BlockState STONE_BRICKS = of("minecraft:stone_bricks");
    static final BlockState MOSSY_STONE_BRICKS = of("minecraft:mossy_stone_bricks");
    static final BlockState CRACKED_STONE_BRICKS = of("minecraft:cracked_stone_bricks");
    static final BlockState ANDESITE = of("minecraft:andesite");
    static final BlockState POLISHED_ANDESITE = of("minecraft:polished_andesite");
    static final BlockState TUFF = of("minecraft:tuff");
    static final BlockState TERRACOTTA = of("minecraft:terracotta");
    static final BlockState FARMLAND = of("minecraft:farmland[moisture=7]");
    static final BlockState HAY = of("minecraft:hay_block[axis=y]");
    static final BlockState COMPOSTER = of("minecraft:composter[level=4]");
    static final BlockState CAULDRON = of("minecraft:water_cauldron[level=3]");
    static final BlockState SHORT_GRASS = of("minecraft:short_grass");
    static final BlockState FERN = of("minecraft:fern");
    static final BlockState[] FLOWERS = {of("minecraft:poppy"), of("minecraft:dandelion"), of("minecraft:oxeye_daisy"),
            of("minecraft:cornflower"), of("minecraft:azure_bluet"), of("minecraft:allium")};
    static final BlockState ANVIL = of("minecraft:anvil[facing=north]");
    static final BlockState[] ANVIL_FACING = dirs("minecraft:anvil[facing=%s]");
    static final BlockState[] BLAST_FURNACE = dirs("minecraft:blast_furnace[facing=%s,lit=true]");
    static final BlockState GRINDSTONE = of("minecraft:grindstone[face=floor,facing=north]");
    static final BlockState BELL_CEILING_X = of("minecraft:bell[attachment=ceiling,facing=north]");
    static final BlockState BELL_CEILING_Z = of("minecraft:bell[attachment=ceiling,facing=east]");
    static final BlockState STREET_LAMP = of("awaken:street_lamp");
    static final BlockState STONE_WALL_POST = of("minecraft:cobblestone_wall[up=true]");
    static final BlockState STONE_SLAB = of("minecraft:stone_brick_slab[type=bottom]");
    static final BlockState COBBLE_SLAB = of("minecraft:cobblestone_slab[type=bottom]");
    static final BlockState MOSSY_COBBLE_SLAB = of("minecraft:mossy_cobblestone_slab[type=bottom]");
    static final BlockState ANDESITE_SLAB = of("minecraft:andesite_slab[type=bottom]");
    static final BlockState POLISHED_ANDESITE_SLAB = of("minecraft:polished_andesite_slab[type=bottom]");
    static final BlockState SANDSTONE_SLAB = of("minecraft:sandstone_slab[type=bottom]");
    static final BlockState SMOOTH_SANDSTONE_SLAB = of("minecraft:smooth_sandstone_slab[type=bottom]");
    static final BlockState CUT_SANDSTONE_SLAB = of("minecraft:cut_sandstone_slab[type=bottom]");
    static final BlockState MUD_BRICK_SLAB = of("minecraft:mud_brick_slab[type=bottom]");
    static final BlockState[] COBBLE_STAIR = dirs("minecraft:cobblestone_stairs[facing=%s,half=bottom,shape=straight]");
    static final BlockState[] STONE_BRICK_STAIR = dirs("minecraft:stone_brick_stairs[facing=%s,half=bottom,shape=straight]");
    static final BlockState[] LADDER = dirs("minecraft:ladder[facing=%s]");
    static final BlockState CHAIN = of("minecraft:iron_chain[axis=y]");
    static final BlockState LIGHTNING_ROD = of("minecraft:lightning_rod[facing=up]");
    static final BlockState BARREL = of("minecraft:barrel[facing=up]");

    /** One wood's pieces: planks, a log upright and lying, slabs, stairs by facing, fences by mask, a gate by side, trapdoors. */
    record Wood(BlockState planks, BlockState post, BlockState beamX, BlockState beamZ, BlockState slab, BlockState slabTop,
                BlockState[] stair, BlockState[] fence, BlockState[] gate, BlockState[] trapdoorOpen) {
        static Wood of(String w) {
            String log = w.equals("bamboo") ? "bamboo_block" : w.equals("crimson") || w.equals("warped") ? w + "_stem" : w + "_log";
            return new Wood(VillageBlocks.of("minecraft:" + w + "_planks"), VillageBlocks.of("minecraft:stripped_" + log + "[axis=y]"),
                    VillageBlocks.of("minecraft:stripped_" + log + "[axis=x]"), VillageBlocks.of("minecraft:stripped_" + log + "[axis=z]"),
                    VillageBlocks.of("minecraft:" + w + "_slab[type=bottom]"), VillageBlocks.of("minecraft:" + w + "_slab[type=top]"),
                    dirs("minecraft:" + w + "_stairs[facing=%s,half=bottom,shape=straight]"), connected("minecraft:" + w + "_fence", false),
                    dirs("minecraft:" + w + "_fence_gate[facing=%s,open=false]"), dirs("minecraft:" + w + "_trapdoor[facing=%s,open=true,half=bottom]"));
        }
    }

    static final Wood OAK = Wood.of("oak"), SPRUCE = Wood.of("spruce"), DARK_OAK = Wood.of("dark_oak"), ACACIA = Wood.of("acacia"),
            MANGROVE = Wood.of("mangrove"), JUNGLE = Wood.of("jungle"), BIRCH = Wood.of("birch"), CHERRY = Wood.of("cherry");

    static final BlockState[] CROP = {of("minecraft:wheat[age=7]"), of("minecraft:wheat[age=6]"), of("minecraft:wheat[age=5]")};
    static final BlockState[] CARROTS = {of("minecraft:carrots[age=7]"), of("minecraft:carrots[age=6]")};
    static final BlockState[] POTATOES = {of("minecraft:potatoes[age=7]"), of("minecraft:potatoes[age=6]")};
    static final BlockState[] BEETROOTS = {of("minecraft:beetroots[age=3]"), of("minecraft:beetroots[age=2]")};
    static final BlockState MELON = of("minecraft:melon"), PUMPKIN = of("minecraft:pumpkin");
    static final BlockState[] MELON_STEM = dirs("minecraft:attached_melon_stem[facing=%s]");
    static final BlockState[] PUMPKIN_STEM = dirs("minecraft:attached_pumpkin_stem[facing=%s]");
}
