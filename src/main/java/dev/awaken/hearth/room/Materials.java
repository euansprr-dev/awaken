package dev.awaken.hearth.room;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * What a room is made of, read from its walls, floor and roof: each block's family (the word a room's name uses,
 * "timber", "stone") and whether it is worked (planks, bricks, stripped logs, cut or polished stone, tiles, glass) or
 * rough (dirt, cobble, raw stone, logs, leaves). Never a list of named blocks: families come from tags and the block's
 * own name, so other mods' bricks and planks read the same way. Cached per block (the scan asks thousands of times).
 */
public final class Materials {
    private Materials() {}

    /** The families a room's name and the home's Style speak of. */
    public enum Family {
        TIMBER("timber"), LOG("log"), STONE("stone"), COBBLE("fieldstone"), DEEPSLATE("deepslate"), BRICK("brick"),
        MUD("mud-brick"), SANDSTONE("sandstone"), BLACKSTONE("blackstone"), TUFF("tuff"), QUARTZ("quartz"), COPPER("copper"),
        PRISMARINE("sea-stone"), CLAY("clay"), CONCRETE("concrete"), GLASS("glass"), EARTH("earthen"), END("end-stone"),
        NETHER("nether-brick"), WOOL("wool"), LEAF("leaf"), SNOW("snow"), OTHER("");

        public final String word;

        Family(String word) {
            this.word = word;
        }
    }

    public static final int FAMILIES = Family.values().length;
    private static final int WORKED = 1 << 8;
    private static final Map<Block, Integer> CACHE = new IdentityHashMap<>();

    /** Family ordinal in the low byte, {@link #WORKED} above it. */
    public static int of(BlockState s) {
        Block b = s.getBlock();
        Integer c = CACHE.get(b);
        if (c == null) {
            c = classify(s);
            synchronized (CACHE) {
                CACHE.put(b, c);
            }
        }
        return c;
    }

    public static Family family(int packed) {
        return Family.values()[packed & 0xFF];
    }

    public static boolean worked(int packed) {
        return (packed & WORKED) != 0;
    }

    private static int classify(BlockState s) {
        String path = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        if (s.is(BlockTags.PLANKS) || s.is(Tags.BlockItems.STRIPPED_LOGS.block()) || s.is(Tags.BlockItems.STRIPPED_WOODS.block())) return worked(Family.TIMBER);
        if (s.is(BlockTags.LOGS)) return rough(Family.LOG);
        if (s.is(BlockTags.LEAVES)) return rough(Family.LEAF);
        if (s.is(Tags.BlockItems.GLASS_BLOCKS.block()) || s.is(Tags.BlockItems.GLASS_PANES.block())) return worked(Family.GLASS);
        if (s.is(BlockTags.WOOL)) return worked(Family.WOOL);
        // stairs, slabs, walls and fences take their stone's or wood's family, and are worked (someone cut them)
        boolean shaped = s.is(BlockTags.STAIRS) || s.is(BlockTags.SLABS) || s.is(BlockTags.WALLS) || s.is(BlockTags.FENCES)
                || s.is(BlockTags.FENCE_GATES) || s.is(BlockTags.DOORS) || s.is(BlockTags.TRAPDOORS);
        boolean cut = shaped || path.contains("brick") || path.contains("polished") || path.startsWith("cut_") || path.contains("_cut_")
                || path.startsWith("smooth_") || path.startsWith("chiseled_") || path.contains("tile") || path.contains("planks")
                || path.contains("terracotta") || (path.contains("concrete") && !path.contains("powder")) || path.contains("quartz")
                || path.startsWith("stripped_") || path.contains("pillar");
        Family f = familyByName(path);
        if (f == Family.OTHER && (s.is(BlockTags.WOODEN_STAIRS) || s.is(BlockTags.WOODEN_SLABS) || s.is(BlockTags.WOODEN_FENCES)
                || s.is(BlockTags.WOODEN_DOORS) || s.is(BlockTags.WOODEN_TRAPDOORS) || s.is(BlockTags.FENCE_GATES))) f = Family.TIMBER;
        return cut ? worked(f) : rough(f);
    }

    private static Family familyByName(String p) {
        if (p.contains("deepslate")) return Family.DEEPSLATE;
        if (p.contains("blackstone")) return Family.BLACKSTONE;
        if (p.contains("nether_brick") || p.contains("netherrack")) return Family.NETHER;
        if (p.contains("end_stone") || p.contains("purpur")) return Family.END;
        if (p.contains("prismarine")) return Family.PRISMARINE;
        if (p.contains("mud")) return Family.MUD;
        if (p.contains("sandstone")) return Family.SANDSTONE;
        if (p.contains("tuff")) return Family.TUFF;
        if (p.contains("quartz")) return Family.QUARTZ;
        if (p.contains("copper")) return Family.COPPER;
        if (p.contains("terracotta")) return Family.CLAY;
        if (p.contains("concrete")) return Family.CONCRETE;
        if (p.contains("cobble")) return Family.COBBLE;
        if (p.equals("bricks") || p.startsWith("brick_")) return Family.BRICK;
        if (p.contains("stone") || p.contains("andesite") || p.contains("diorite") || p.contains("granite") || p.contains("calcite")
                || p.contains("basalt")) return Family.STONE;
        if (p.contains("dirt") || p.contains("grass_block") || p.equals("podzol") || p.equals("mycelium") || p.contains("rooted")
                || p.equals("clay") || p.equals("gravel") || p.contains("sand")) return Family.EARTH;
        if (p.contains("snow") || p.contains("ice")) return Family.SNOW;
        for (String wood : WOODS) if (p.startsWith(wood)) return Family.TIMBER;
        return Family.OTHER;
    }

    private static final String[] WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak",
            "bamboo", "crimson", "warped"};

    private static int worked(Family f) {
        return f.ordinal() | WORKED;
    }

    private static int rough(Family f) {
        return f.ordinal();
    }
}
