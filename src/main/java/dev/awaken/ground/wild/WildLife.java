package dev.awaken.ground.wild;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Grass that lives (SPEC §41.2): herds crop lawns and press trails ({@link dev.awaken.ground.api.WildPlants}), and the plants
 * grow back on random ticks, which the game gives only to chunks near players. Nothing here is kept per tick: a lawn or a
 * trail is its blocks' states. Also the cover a plant gives a crouching body, and the hook the client's effects hang on.
 */
public final class WildLife {
    private WildLife() {}

    /**
     * A grazed lawn's chance to grow a stage on a random tick: four stages, a block takes a random tick about every 1,365 game
     * ticks (the default random tick speed of 3), so it is long grass again after about 53 ticks, three in-game days.
     */
    public static final float LAWN_GROW = 0.076F;
    /** A trail's chance to stand up a stage on a random tick: three stages in about 26 ticks, a day and a half. */
    public static final float TRAIL_RISE = 0.114F;
    /** Someone running or riding through long grass, rye or meadow presses the block they are in one tick in this many. */
    public static final int RUN_TRAIL_ODDS = 4;
    /** Moving faster than this (blocks a tick, squared: 0.255, about 5 blocks a second) counts as running: a walk does not. */
    public static final double RUN_SPEED_SQR = 0.065;

    /** The client's effects (seed fluff, the swish underfoot); none on a dedicated server. */
    public interface Fx {
        void ambient(BlockState state, Level level, BlockPos pos, RandomSource random);

        void brush(BlockState state, Level level, BlockPos pos, Entity entity);
    }

    private static final Fx NONE = new Fx() {
        @Override
        public void ambient(BlockState state, Level level, BlockPos pos, RandomSource random) {}

        @Override
        public void brush(BlockState state, Level level, BlockPos pos, Entity entity) {}
    };
    private static volatile Fx fx = NONE;

    public static void installFx(Fx f) {
        fx = f;
    }

    // ---- what a block is ------------------------------------------------------------------------------------------------

    /** The block of a plant's column its other blocks stand on, or null when {@code state} is not a croppable or pressable grass. */
    static BlockPos base(BlockState state, BlockPos pos) {
        Block b = state.getBlock();
        if (b == WildBlocks.RYE_STEMS.get()) return pos;
        if (b == WildBlocks.WILD_RYE.get()) return state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER ? pos.below() : pos.below(2);
        if (b instanceof DoublePlantBlock) return state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        return pos;
    }

    /** What stood in a trampled block: one of {@link TrampledGrassBlock}'s {@code from} values, or -1. */
    static int trampleKind(BlockState s) {
        Block b = s.getBlock();
        if (b == Blocks.SHORT_GRASS) return TrampledGrassBlock.SHORT;
        if (b == WildBlocks.MEADOW_GRASS.get()) return TrampledGrassBlock.MEADOW;
        if (b == WildBlocks.LONG_GRASS.get()) return TrampledGrassBlock.LONG;
        if (b == WildBlocks.RYE_STEMS.get() || b == WildBlocks.WILD_RYE.get()) return TrampledGrassBlock.RYE;
        if (b == Blocks.TALL_GRASS) return TrampledGrassBlock.TALL;
        return -1;
    }

    private static int height(int kind) {
        return kind == TrampledGrassBlock.RYE ? 3 : kind == TrampledGrassBlock.LONG || kind == TrampledGrassBlock.TALL ? 2 : 1;
    }

    /** The plant a body standing at {@code pos} touches: the one in its block, or (given the ground) the one on it. */
    private static BlockPos plantAt(BlockGetter level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.isAir() || !s.getFluidState().isEmpty() || s.getBlock() instanceof net.minecraft.world.level.block.VegetationBlock ? pos : pos.above();
    }

    // ---- crop, trample, grow back ----------------------------------------------------------------------------------------

    public static boolean crop(ServerLevel level, BlockPos pos) {
        BlockPos at = plantAt(level, pos);
        BlockState s = level.getBlockState(at);
        Block b = s.getBlock();
        if (b == WildBlocks.LONG_GRASS.get() || b == WildBlocks.RYE_STEMS.get() || b == WildBlocks.WILD_RYE.get()) {
            int kind = trampleKind(s);
            BlockPos base = base(s, at);
            setColumn(level, base, height(kind), lawn(kind == TrampledGrassBlock.RYE ? 2 : 1, 0));
            return true;
        }
        if (b == Blocks.TALL_GRASS || b == Blocks.LARGE_FERN) {
            setColumn(level, base(s, at), 2, (b == Blocks.TALL_GRASS ? Blocks.SHORT_GRASS : Blocks.FERN).defaultBlockState());
            return true;
        }
        if (b == WildBlocks.TRAMPLED_GRASS.get()) {
            int from = s.getValue(TrampledGrassBlock.FROM);
            if (from == TrampledGrassBlock.LONG || from == TrampledGrassBlock.RYE) {
                level.setBlock(at, lawn(from == TrampledGrassBlock.RYE ? 2 : 1, 0), Block.UPDATE_ALL);
                return true;
            }
        }
        return false;
    }

    public static boolean trample(ServerLevel level, BlockPos pos, int strength) {
        BlockPos at = plantAt(level, pos);
        BlockState s = level.getBlockState(at);
        int stage = strength >= 2 ? 0 : 1;
        if (s.is(WildBlocks.TRAMPLED_GRASS.get())) {
            if (s.getValue(TrampledGrassBlock.STAGE) <= stage) return false;
            level.setBlock(at, s.setValue(TrampledGrassBlock.STAGE, stage), Block.UPDATE_ALL);
            return true;
        }
        int kind = trampleKind(s);
        if (kind < 0) return false;
        BlockPos base = base(s, at);
        BlockState flat = WildBlocks.TRAMPLED_GRASS.get().defaultBlockState().setValue(TrampledGrassBlock.FROM, kind)
                .setValue(TrampledGrassBlock.STAGE, stage).setValue(TrampledGrassBlock.AXIS, trailAxis(level, base));
        setColumn(level, base, height(kind), flat);
        return true;
    }

    /** A trail lies along the trampled blocks beside it; alone, either way. */
    private static Direction.Axis trailAxis(BlockGetter level, BlockPos pos) {
        boolean x = level.getBlockState(pos.east()).is(WildBlocks.TRAMPLED_GRASS.get()) || level.getBlockState(pos.west()).is(WildBlocks.TRAMPLED_GRASS.get());
        boolean z = level.getBlockState(pos.north()).is(WildBlocks.TRAMPLED_GRASS.get()) || level.getBlockState(pos.south()).is(WildBlocks.TRAMPLED_GRASS.get());
        if (x != z) return x ? Direction.Axis.X : Direction.Axis.Z;
        return ((pos.getX() * 31 + pos.getZ()) & 1) == 0 ? Direction.Axis.X : Direction.Axis.Z;
    }

    static BlockState lawn(int regrow, int growth) {
        return WildBlocks.MEADOW_GRASS.get().defaultBlockState().setValue(MeadowGrassBlock.REGROW, regrow).setValue(MeadowGrassBlock.GROWTH, growth);
    }

    /**
     * Puts {@code base} in {@code state} and clears the {@code height - 1} blocks of the plant above it, top first and
     * without telling the plant's own blocks (so nothing breaks and drops on the way).
     */
    private static void setColumn(ServerLevel level, BlockPos base, int height, BlockState state) {
        for (int i = height - 1; i >= 1; i--) {
            level.setBlock(base.above(i), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        level.setBlock(base, state, Block.UPDATE_ALL);
    }

    /** A grazed lawn's random tick: a stage nearer what stood there, then that again (as tall as there is room for). */
    static void regrowLawn(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(MeadowGrassBlock.REGROW) == 0 || random.nextFloat() >= LAWN_GROW) return;
        int growth = state.getValue(MeadowGrassBlock.GROWTH);
        if (growth < 3) {
            level.setBlock(pos, state.setValue(MeadowGrassBlock.GROWTH, growth + 1), Block.UPDATE_CLIENTS);
            return;
        }
        restore(level, pos, state.getValue(MeadowGrassBlock.REGROW) == 2 ? TrampledGrassBlock.RYE : TrampledGrassBlock.LONG);
    }

    /** A trail's random tick: flat and pale, flat and green, half standing, then what stood there. */
    static void springBack(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() >= TRAIL_RISE) return;
        int stage = state.getValue(TrampledGrassBlock.STAGE);
        if (stage < 2) {
            level.setBlock(pos, state.setValue(TrampledGrassBlock.STAGE, stage + 1), Block.UPDATE_CLIENTS);
            return;
        }
        restore(level, pos, state.getValue(TrampledGrassBlock.FROM));
    }

    /** What stood at {@code pos} grows back there, as tall as the air above allows (rye, then long grass, then meadow). */
    static void restore(ServerLevel level, BlockPos pos, int kind) {
        int room = level.getBlockState(pos.above()).isAir() ? level.getBlockState(pos.above(2)).isAir() ? 3 : 2 : 1;
        if (kind == TrampledGrassBlock.RYE && room >= 3) {
            BlockState rye = WildDoubleBlock.grown(WildBlocks.WILD_RYE.get(), level, pos);
            level.setBlock(pos.above(2), rye.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            level.setBlock(pos.above(), rye.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            level.setBlock(pos, WildBlocks.RYE_STEMS.get().defaultBlockState(), Block.UPDATE_ALL);
        } else if ((kind == TrampledGrassBlock.RYE || kind == TrampledGrassBlock.LONG) && room >= 2) {
            placeTall(level, pos, WildDoubleBlock.grown(WildBlocks.LONG_GRASS.get(), level, pos));
        } else if (kind == TrampledGrassBlock.TALL && room >= 2) {
            placeTall(level, pos, Blocks.TALL_GRASS.defaultBlockState());
        } else if (kind == TrampledGrassBlock.SHORT || kind == TrampledGrassBlock.TALL) {
            level.setBlock(pos, Blocks.SHORT_GRASS.defaultBlockState(), Block.UPDATE_ALL);
        } else {
            level.setBlock(pos, WildBlocks.MEADOW_GRASS.get().defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static void placeTall(ServerLevel level, BlockPos pos, BlockState lower) {
        level.setBlock(pos.above(), lower.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        level.setBlock(pos, lower.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_ALL);
    }

    /** Bone meal on meadow grass: long grass, where there is room. */
    static void growTall(ServerLevel level, BlockPos pos, Block tall) {
        if (!level.getBlockState(pos.above()).isAir()) return;
        placeTall(level, pos, tall instanceof WildDoubleBlock ? WildDoubleBlock.grown(tall, level, pos) : tall.defaultBlockState());
    }

    // ---- running through, and the client's effects ------------------------------------------------------------------------

    /** Inside a wild plant's block: a runner or a rider presses a thin trail (server); the client brushes past it. */
    static void pushThrough(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level instanceof ServerLevel server) {
            if (!trailBase(state) || server.getRandom().nextInt(RUN_TRAIL_ODDS) != 0) return;
            boolean runner = entity instanceof Player || entity.getControllingPassenger() instanceof Player;
            if (runner && entity.getKnownMovement().horizontalDistanceSqr() > RUN_SPEED_SQR) {
                trample(server, pos, 1);
            }
        } else {
            fx.brush(state, level, pos, entity);
        }
    }

    /** The block of a plant a run presses (each column once: its foot). */
    private static boolean trailBase(BlockState s) {
        Block b = s.getBlock();
        if (b == WildBlocks.RYE_STEMS.get()) return true;
        if (b == WildBlocks.LONG_GRASS.get()) return s.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER;
        return false;
    }

    static void animate(BlockState state, Level level, BlockPos pos, RandomSource random) {
        fx.ambient(state, level, pos, random);
    }

    // ---- cover --------------------------------------------------------------------------------------------------------

    /** How hidden a crouching body is in a block holding {@code s} (0..1). */
    public static double coverOf(BlockState s) {
        Block b = s.getBlock();
        if (b instanceof WildDoubleBlock d) return d.kind().cover;
        if (b instanceof WildBushBlock w) return w.kind().cover;
        if (b instanceof RyeStemsBlock) return WildKind.RYE.cover;
        if (b instanceof MeadowGrassBlock) return WildKind.MEADOW.cover;
        if (b instanceof BrambleBlock) return WildKind.BRAMBLE.cover;
        if (b instanceof TrampledGrassBlock) return s.getValue(TrampledGrassBlock.STAGE) == 2 ? 0.05 : 0.0;
        if (b == Blocks.TALL_GRASS || b == Blocks.LARGE_FERN) return 0.7;
        if (b == Blocks.BUSH || b == Blocks.SWEET_BERRY_BUSH) return 0.5;
        if (b == Blocks.SHORT_GRASS || b == Blocks.FERN) return 0.15;
        return 0.0;
    }

    private static final ThreadLocal<BlockPos.MutableBlockPos> AT = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    /** Cover in one column: the block a crouching body fills, or just above it (where its head is) a little less. */
    private static double column(BlockGetter level, BlockPos.MutableBlockPos at, int x, int y, int z) {
        double feet = coverOf(level.getBlockState(at.set(x, y, z)));
        return Math.max(feet, 0.9 * coverOf(level.getBlockState(at.set(x, y + 1, z))));
    }

    public static double coverAt(Level level, BlockPos pos) {
        BlockPos.MutableBlockPos at = AT.get();
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        BlockState s = level.getBlockState(pos);
        if (!s.isAir() && coverOf(s) == 0 && s.isCollisionShapeFullBlock(level, pos)) y++;      // given the ground
        double own = column(level, at, x, y, z);
        double ring = (column(level, at, x + 1, y, z) + column(level, at, x - 1, y, z) + column(level, at, x, y, z + 1)
                + column(level, at, x, y, z - 1)) / 4.0;
        return Math.clamp(0.75 * own + 0.25 * ring, 0.0, 1.0);
    }
}
