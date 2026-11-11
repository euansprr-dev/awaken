package dev.awaken.gates.realms.kit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.HangingRootsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.concurrent.ConcurrentHashMap;

/**
 * What a block needs to stay where it is (SPEC §50.6): a body stands on other bodies, a plant on what's under it, a
 * hanging thing on what's above it. One table, read by the float check that proves a land and by {@link Settle}, which
 * clears small strays as a land is drawn, so the two can never disagree about what a block is.
 */
public final class Kinds {
    private Kinds() {}

    public static final int AIR = 0, BODY = 1, ON_GROUND = 2, HANGS = 3, BESIDE = 4, AFLOAT = 5, WATER = 6, LEAF = 7;

    // block states are singletons, so their identity hash is a fine key and reads take no lock on a worldgen thread
    private static final ConcurrentHashMap<BlockState, Integer> KIND = new ConcurrentHashMap<>();

    /** The kind of a block; null is air. */
    public static int of(BlockState s) {
        if (s == null) return AIR;
        Integer k = KIND.get(s);
        if (k != null) return k;
        int made = classify(s);
        KIND.put(s, made);
        return made;
    }

    /**
     * A body whose top is lower than its cell's: a slab laid low. It carries people and other bodies, but a plant, a
     * crystal, a candle or a drift set over it stands in the cell above with half a block of air under it. The floors
     * that step in half blocks (SPEC §50.9 "Depth") made these common; Euan, 11 Nov, in the Cinder Workings: "There are
     * some floating things here... we need to make sure that never happens in any gates." A stair is not one: half its
     * top is whole, and candles down a flight of steps (Amber Reach's) stand on it.
     */
    public static boolean low(BlockState s) {
        return s != null && s.getBlock() instanceof SlabBlock && s.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
    }

    private static int classify(BlockState s) {
        Block b = s.getBlock();
        if (b == Blocks.WATER) return WATER;
        if (s.isAir() || b == Blocks.LIGHT || b == Blocks.BARRIER) return AIR;
        String id = BuiltInRegistries.BLOCK.getKey(b).getPath();
        if (id.endsWith("_updraft")) return AIR;
        if (id.equals("keep_climb_root")) return BESIDE; // a root net holds on to the wall beside it
        if (id.equals("fane_leaf") || id.equals("fane_leaf_lit") || id.equals("fane_leaf_deep") || id.equals("keep_leaf") || id.equals("keep_leaf_lit") || id.equals("keep_leaf_deep")) return LEAF; // the boughs are checked; a crown's outer leaves don't all touch, but vines may hang from them
        if (b == Blocks.LILY_PAD || id.equals("crypt_lily_candle") || id.equals("market_boat_lamp")) return AFLOAT;
        if (b instanceof LanternBlock) return s.getValue(BlockStateProperties.HANGING) ? HANGS : ON_GROUND;
        if (id.endsWith("_bell_rope") || id.equals("gate_bell")) return HANGS;
        // an icicle and a flag hang from an eave: read as things that stand, Settle took them out from under every roof
        if (id.endsWith("_icicles") || id.endsWith("_flags")) return HANGS;
        if (b instanceof HangingRootsBlock || id.endsWith("_drip") || id.endsWith("_vines") || id.endsWith("_vines_plant")) return HANGS;
        if (b instanceof VineBlock) return BESIDE;
        if (b instanceof BushBlock || b instanceof DoublePlantBlock || b instanceof CarpetBlock || b instanceof CropBlock
                || id.endsWith("_crumbs") || id.endsWith("_petals") || id.endsWith("_candle") || id.endsWith("_drift")) return ON_GROUND;
        return s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty() ? ON_GROUND : BODY;
    }
}
