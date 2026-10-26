package dev.awaken.farsight.voxel;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * How each block reads from past the render distance (SPEC §20, City Sight), cached by block-state id. A block there
 * spans a few pixels at most, so its model reduces to one of a few kinds: a whole cube (opaque ones hide their
 * neighbours' faces, see-through ones such as glass, stairs and leaves don't), its bounding box (slabs, beds, chests:
 * at least 0.4 of a block), a flat sheet (carpets, snow layers), water, or nothing (fences, panes, bars, plants,
 * torches, doors: under a pixel there, and a gap where they stand reads as the real one does, the room behind).
 */
public final class VoxelKinds {
    private VoxelKinds() {}

    public static final byte UNKNOWN = 0, AIR = 1, FULL = 2, SOFT = 3, BOX = 4, FLAT = 5, WATER = 6, SKIP = 7;

    private static volatile byte[] kinds = new byte[0];
    /** Per state: its box in sixteenths, x0 y0 z0 x1 y1 z1 at 5 bits each (BOX and FLAT). */
    private static volatile int[] boxes = new int[0];

    /** The kind of the state with this id (Block.BLOCK_STATE_REGISTRY). */
    public static byte kind(int id) {
        byte[] k = kinds;
        if (id < k.length && k[id] != UNKNOWN) return k[id];
        BlockState state = Block.BLOCK_STATE_REGISTRY.byId(id);
        return state == null ? AIR : learn(id, state);
    }

    /** The state's box (BOX and FLAT), packed as {@link #boxes}. */
    public static int box(int id) {
        kind(id);
        return boxes[id];
    }

    public static int x0(int box) {
        return box & 31;
    }

    public static int y0(int box) {
        return box >> 5 & 31;
    }

    public static int z0(int box) {
        return box >> 10 & 31;
    }

    public static int x1(int box) {
        return box >> 15 & 31;
    }

    public static int y1(int box) {
        return box >> 20 & 31;
    }

    public static int z1(int box) {
        return box >> 25 & 31;
    }

    private static synchronized byte learn(int id, BlockState state) {
        if (kinds.length <= id) {
            int n = Math.max(id + 1, Block.BLOCK_STATE_REGISTRY.size());
            kinds = java.util.Arrays.copyOf(kinds, n);
            boxes = java.util.Arrays.copyOf(boxes, n);
        }
        if (kinds[id] != UNKNOWN) return kinds[id];
        int[] box = new int[1];
        byte k = classify(state, box);
        boxes[id] = box[0];
        kinds[id] = k;
        return k;
    }

    private static byte classify(BlockState state, int[] box) {
        if (state.isAir()) return AIR;
        boolean water = state.getFluidState().getType().isSame(Fluids.WATER);
        if (state.getBlock() instanceof LiquidBlock) return water ? WATER : FULL;
        if (state.getRenderShape() == RenderShape.INVISIBLE) return water ? WATER : AIR;
        VoxelShape shape;
        try {
            shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        } catch (RuntimeException e) {
            // a block whose shape needs its world: take it as a whole cube that hides nothing
            return SOFT;
        }
        if (shape.isEmpty()) return water ? WATER : SKIP;
        if (shape == Shapes.block()) {
            // leaves hide their neighbours as the game's fast leaves do: from afar a canopy is a solid mass
            if (state.is(BlockTags.LEAVES)) return FULL;
            return state.canOcclude() && state.isSolidRender() ? FULL : SOFT;
        }
        double volume = 0;
        for (AABB b : shape.toAabbs()) volume += b.getXsize() * b.getYsize() * b.getZsize();
        AABB all = shape.bounds();
        box[0] = pack(all);
        if (volume >= 0.4) {
            // stairs and other near-cubes read as cubes from there
            return volume >= 0.7 && all.getXsize() > 0.99 && all.getYsize() > 0.99 && all.getZsize() > 0.99 ? SOFT : BOX;
        }
        if (all.getYsize() <= 0.2 && all.getXsize() * all.getZsize() >= 0.8) return FLAT;
        return water ? WATER : SKIP;
    }

    private static int pack(AABB b) {
        int x0 = q(b.minX), y0 = q(b.minY), z0 = q(b.minZ), x1 = q(b.maxX), y1 = q(b.maxY), z1 = q(b.maxZ);
        return x0 | y0 << 5 | z0 << 10 | x1 << 15 | y1 << 20 | z1 << 25;
    }

    private static int q(double v) {
        return Math.clamp(Math.round(v * 16), 0, 16);
    }
}
