package dev.awaken.veil.world;

import dev.awaken.veil.VeilContent;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.FlowerBedBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The Stillpoint temple: a pale disc floating above the sea of clouds at dawn. A dark slate ring hangs over eight quartz
 * pillars with amber lanterns; the seat is at the centre; the Out door faces the sunrise (east) under a cherry gate; the
 * In door waits in a dark arch to the west; a stair runs north and stops in the air. Built once into an immutable map and
 * written chunk by chunk by {@link StillpointGenerator} (any worldgen thread may read it).
 */
public final class TempleBuilder {
    private TempleBuilder() {}

    public static final int FLOOR = 128;
    public static final int RADIUS = 11;
    /** The meditation seat, centre of the temple: where you arrive and where your body waits when you go Out. */
    public static final BlockPos SEAT = new BlockPos(0, FLOOR + 1, 0);
    public static final BlockPos DOOR_OUT = new BlockPos(12, FLOOR + 1, 0);
    public static final BlockPos DOOR_IN = new BlockPos(-12, FLOOR + 1, 0);
    /** Horizontal extent of everything placed (for chunk culling). */
    public static final int EXTENT = 27;

    private static volatile Long2ObjectMap<BlockState> blocks;

    /** All temple blocks by packed position. Built lazily (block registries must be ready). */
    public static Long2ObjectMap<BlockState> blocks() {
        Long2ObjectMap<BlockState> b = blocks;
        if (b == null) {
            synchronized (TempleBuilder.class) {
                b = blocks;
                if (b == null) blocks = b = Long2ObjectMaps.unmodifiable(build());
            }
        }
        return b;
    }

    private static Long2ObjectOpenHashMap<BlockState> build() {
        Long2ObjectOpenHashMap<BlockState> m = new Long2ObjectOpenHashMap<>();
        BlockState smooth = Blocks.SMOOTH_QUARTZ.defaultBlockState();
        BlockState bricks = Blocks.QUARTZ_BRICKS.defaultBlockState();
        BlockState slate = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        BlockState calcite = Blocks.CALCITE.defaultBlockState();
        BlockState tuff = Blocks.TUFF.defaultBlockState();
        BlockState deep = Blocks.DEEPSLATE.defaultBlockState();
        BlockState pillar = Blocks.QUARTZ_PILLAR.defaultBlockState();
        BlockState capital = Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState();
        BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);

        // the floating rock under the disc
        for (int x = -RADIUS - 1; x <= RADIUS + 1; x++) {
            for (int z = -RADIUS - 1; z <= RADIUS + 1; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r > RADIUS + 0.5) continue;
                double wobble = Math.sin(x * 0.9) * 0.6 + Math.cos(z * 1.1) * 0.6;
                int depth = (int) Math.round((RADIUS + 0.5 - r) * 1.15 + wobble);
                for (int d = 1; d <= Math.max(1, depth); d++) {
                    BlockState s = d <= 2 ? calcite : d <= 6 ? tuff : deep;
                    put(m, x, FLOOR - d, z, s);
                }
            }
        }
        // the disc
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r > RADIUS + 0.5) continue;
                BlockState s = r >= RADIUS - 0.5 ? slate : (r >= 5.5 && r < 6.5) ? bricks : smooth;
                put(m, x, FLOOR, z, s);
            }
        }
        // the seat: a low round dais
        BlockState seat = Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) put(m, x, FLOOR + 1, z, seat);
        // eight pillars, a dark ring above them, lanterns hanging between
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + Math.PI / 8;
            int px = (int) Math.round(Math.cos(a) * 9), pz = (int) Math.round(Math.sin(a) * 9);
            for (int y = FLOOR + 1; y <= FLOOR + 5; y++) put(m, px, y, pz, pillar);
            put(m, px, FLOOR + 6, pz, capital);
        }
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r >= 8.5 && r < 9.6) put(m, x, FLOOR + 7, z, slate);
            }
        }
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            int lx = (int) Math.round(Math.cos(a) * 9), lz = (int) Math.round(Math.sin(a) * 9);
            put(m, lx, FLOOR + 7, lz, slate);
            put(m, lx, FLOOR + 6, lz, lantern);
        }
        // east: the Out door under a cherry gate, facing the sunrise
        BlockState post = Blocks.STRIPPED_CHERRY_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        BlockState beam = Blocks.STRIPPED_CHERRY_WOOD.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
        BlockState out = VeilContent.DOOR_OUT.get().defaultBlockState();
        for (int y = FLOOR + 1; y <= FLOOR + 5; y++) {
            put(m, 12, y, -2, post);
            put(m, 12, y, 2, post);
        }
        for (int z = -4; z <= 4; z++) put(m, 12, FLOOR + 6, z, beam);
        for (int z = -2; z <= 2; z++) put(m, 12, FLOOR + 4, z, Blocks.CHERRY_PLANKS.defaultBlockState());
        for (int z = -1; z <= 1; z++) for (int y = FLOOR + 1; y <= FLOOR + 3; y++) put(m, 12, y, z, out);
        for (int z = -2; z <= 2; z++) put(m, 12, FLOOR, z, slate);
        put(m, 12, FLOOR + 5, 0, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        // west: the In door in a dark arch
        BlockState arch = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState in = VeilContent.DOOR_IN.get().defaultBlockState();
        for (int y = FLOOR + 1; y <= FLOOR + 4; y++) {
            put(m, -12, y, -2, arch);
            put(m, -12, y, 2, arch);
        }
        for (int z = -2; z <= 2; z++) put(m, -12, FLOOR + 4, z, arch);
        put(m, -12, FLOOR + 5, 0, Blocks.AMETHYST_BLOCK.defaultBlockState());
        for (int z = -1; z <= 1; z++) for (int y = FLOOR + 1; y <= FLOOR + 3; y++) put(m, -12, y, z, in);
        for (int z = -2; z <= 2; z++) put(m, -12, FLOOR, z, slate);
        // two small chedis to the north and south, white with a gold crown and a lit spire
        for (int side : new int[]{-1, 1}) {
            int cz = 7 * side;
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) put(m, x, FLOOR + 1, cz + z, calcite);
            put(m, 0, FLOOR + 2, cz, smooth);
            put(m, 1, FLOOR + 2, cz, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
            put(m, -1, FLOOR + 2, cz, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
            put(m, 0, FLOOR + 2, cz + 1, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
            put(m, 0, FLOOR + 2, cz - 1, Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
            put(m, 0, FLOOR + 3, cz, Blocks.GOLD_BLOCK.defaultBlockState());
            put(m, 0, FLOOR + 4, cz, Blocks.END_ROD.defaultBlockState());
        }
        // cherry planters on the diagonals: moss, petals, a little tree of leaves
        BlockState leaves = Blocks.CHERRY_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int[] d : new int[][]{{5, 5}, {-5, 5}, {5, -5}, {-5, -5}}) {
            put(m, d[0], FLOOR, d[1], Blocks.MOSS_BLOCK.defaultBlockState());
            put(m, d[0], FLOOR + 1, d[1], Blocks.STRIPPED_CHERRY_LOG.defaultBlockState());
            put(m, d[0], FLOOR + 2, d[1], leaves);
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                put(m, d[0] + dir.getStepX(), FLOOR + 2, d[1] + dir.getStepZ(), leaves);
                put(m, d[0] + dir.getStepX(), FLOOR, d[1] + dir.getStepZ(), Blocks.MOSS_BLOCK.defaultBlockState());
                put(m, d[0] + dir.getStepX(), FLOOR + 1, d[1] + dir.getStepZ(),
                        Blocks.PINK_PETALS.defaultBlockState().setValue(FlowerBedBlock.AMOUNT, 1 + Math.floorMod(d[0] * 3 + dir.ordinal(), 4))
                                .setValue(FlowerBedBlock.FACING, dir));
            }
            put(m, d[0], FLOOR + 3, d[1], leaves);
        }
        // a stair to the north that ends in the air above the clouds
        BlockState step = Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
        for (int k = 0; k < 14; k++) {
            for (int x = -1; x <= 1; x++) put(m, x, FLOOR - k, -RADIUS - 1 - k, step);
        }
        return m;
    }

    private static void put(Long2ObjectOpenHashMap<BlockState> m, int x, int y, int z, BlockState s) {
        m.put(BlockPos.asLong(x, y, z), s);
    }
}
