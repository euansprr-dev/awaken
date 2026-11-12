package dev.awaken.shaping;

import dev.awaken.Awaken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.Tags;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Looks (SPEC §52.4): ordinary building stone and wood is always on offer, free and instant; anything else is learned by
 * middle-clicking it. Swapping within a family on a whole block gives the real block; anything else is worn as a skin
 * over what the block really is.
 */
public final class Looks {
    private Looks() {}

    public static final TagKey<Block> NEVER = TagKey.create(Registries.BLOCK, Awaken.id("never_a_look"));
    public static final int PAGE = 8;

    public record Family(List<Block> members) {}

    private static volatile @Nullable List<Family> families;
    private static volatile @Nullable Map<Block, Family> byBlock;

    private static Family f(Block... members) {
        return new Family(List.of(members));
    }

    public static List<Family> families() {
        List<Family> all = families;
        if (all != null) return all;
        all = List.of(
                f(Blocks.STONE, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS,
                        Blocks.CRACKED_STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS, Blocks.SMOOTH_STONE),
                f(Blocks.ANDESITE, Blocks.POLISHED_ANDESITE, Blocks.DIORITE, Blocks.POLISHED_DIORITE, Blocks.GRANITE, Blocks.POLISHED_GRANITE,
                        Blocks.CALCITE, Blocks.DRIPSTONE_BLOCK),
                f(Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE, Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS,
                        Blocks.DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_TILES, Blocks.CHISELED_DEEPSLATE),
                f(Blocks.TUFF, Blocks.POLISHED_TUFF, Blocks.TUFF_BRICKS, Blocks.CHISELED_TUFF, Blocks.CHISELED_TUFF_BRICKS),
                f(Blocks.SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.CHISELED_SANDSTONE, Blocks.SMOOTH_SANDSTONE,
                        Blocks.RED_SANDSTONE, Blocks.CUT_RED_SANDSTONE, Blocks.CHISELED_RED_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE),
                f(Blocks.BRICKS, Blocks.MUD_BRICKS, Blocks.PACKED_MUD, Blocks.TERRACOTTA),
                f(Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS,
                        Blocks.CHISELED_POLISHED_BLACKSTONE, Blocks.BASALT, Blocks.POLISHED_BASALT, Blocks.SMOOTH_BASALT),
                f(Blocks.NETHERRACK, Blocks.NETHER_BRICKS, Blocks.CRACKED_NETHER_BRICKS, Blocks.CHISELED_NETHER_BRICKS, Blocks.RED_NETHER_BRICKS),
                f(Blocks.END_STONE, Blocks.END_STONE_BRICKS, Blocks.PURPUR_BLOCK, Blocks.PURPUR_PILLAR),
                f(Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE),
                f(Blocks.QUARTZ_BLOCK, Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BRICKS, Blocks.CHISELED_QUARTZ_BLOCK, Blocks.QUARTZ_PILLAR),
                new Family(Blocks.DYED_TERRACOTTA.asList()),
                new Family(Blocks.CONCRETE.asList()),
                f(Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.BIRCH_PLANKS, Blocks.JUNGLE_PLANKS, Blocks.ACACIA_PLANKS, Blocks.DARK_OAK_PLANKS,
                        Blocks.MANGROVE_PLANKS, Blocks.CHERRY_PLANKS, Blocks.PALE_OAK_PLANKS, Blocks.BAMBOO_PLANKS, Blocks.BAMBOO_MOSAIC,
                        Blocks.CRIMSON_PLANKS, Blocks.WARPED_PLANKS),
                f(Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.BIRCH_LOG, Blocks.JUNGLE_LOG, Blocks.ACACIA_LOG, Blocks.DARK_OAK_LOG,
                        Blocks.MANGROVE_LOG, Blocks.CHERRY_LOG, Blocks.PALE_OAK_LOG, Blocks.CRIMSON_STEM, Blocks.WARPED_STEM),
                f(Blocks.STRIPPED_OAK_LOG, Blocks.STRIPPED_SPRUCE_LOG, Blocks.STRIPPED_BIRCH_LOG, Blocks.STRIPPED_JUNGLE_LOG, Blocks.STRIPPED_ACACIA_LOG,
                        Blocks.STRIPPED_DARK_OAK_LOG, Blocks.STRIPPED_MANGROVE_LOG, Blocks.STRIPPED_CHERRY_LOG, Blocks.STRIPPED_PALE_OAK_LOG,
                        Blocks.STRIPPED_CRIMSON_STEM, Blocks.STRIPPED_WARPED_STEM));
        Map<Block, Family> map = new HashMap<>();
        for (Family fam : all) for (Block b : fam.members()) map.put(b, fam);
        byBlock = map;
        families = all;
        return all;
    }

    public static @Nullable Family familyOf(Block b) {
        families();
        return byBlock.get(b);
    }

    public static boolean ordinary(Block b) {
        return familyOf(b) != null;
    }

    public static boolean sameFamily(Block a, Block b) {
        Family f = familyOf(a);
        return f != null && f == familyOf(b);
    }

    /** Whether a block can be worn as a look at all. Never anything with something inside it, or that would pass for treasure. */
    public static boolean offerable(BlockState s) {
        if (s.getBlock() instanceof ShapedBlock || s.hasBlockEntity() || s.is(NEVER)) return false;
        if (s.is(Tags.BlockItems.ORES.block()) || s.is(Tags.BlockItems.STORAGE_BLOCKS.block())) return false;
        return s.isSolidRender() && s.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    /** A look laid over a block that has a grain keeps that grain. */
    public static BlockState fit(BlockState look, BlockState over) {
        var axis = BlockStateProperties.AXIS;
        return look.hasProperty(axis) && over.hasProperty(axis) ? look.setValue(axis, over.getValue(axis)) : look;
    }

    public static @Nullable BlockState lookAt(Level level, BlockPos pos) {
        RealShapes.Parts p = RealShapes.read(level, pos, level.getBlockState(pos));
        return p == null ? null : p.look();
    }

    /** Every block touching this one that wears the same look, nearest first, in rings by distance. */
    public static List<List<BlockPos>> run(Level level, BlockPos start, int cap) {
        BlockState first = lookAt(level, start);
        if (first == null) return List.of();
        Block kind = first.getBlock();
        List<List<BlockPos>> layers = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> ring = new ArrayDeque<>();
        ring.add(start.immutable());
        seen.add(start.immutable());
        int count = 1;
        while (!ring.isEmpty()) {
            layers.add(List.copyOf(ring));
            ArrayDeque<BlockPos> next = new ArrayDeque<>();
            for (BlockPos p : ring) {
                for (Direction d : Direction.values()) {
                    if (count >= cap) break;
                    BlockPos q = p.relative(d);
                    if (!dev.awaken.core.api.Ready.loaded(level, q) || !seen.add(q)) continue;
                    BlockState l = lookAt(level, q);
                    if (l != null && l.is(kind)) {
                        next.add(q);
                        count++;
                    }
                }
            }
            ring = next;
        }
        return layers;
    }
}
