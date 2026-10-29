package dev.awaken.ground.wild.gen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.ground.wild.BracketFungusBlock;
import dev.awaken.ground.wild.WildBlocks;
import dev.awaken.terrain.landmark.LandsClock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * The wild's woody things (SPEC §41.3), each a small feature placed where {@code awaken:wildness} says: big bushes (leaf
 * mounds two to five wide), hedgerows (hawthorn along old field edges, cow parsley at the foot), bramble thickets, old giants
 * (two-by-two trunks, root flares, branches and a wide crown), snags (standing dead trunks with bracket fungus) and deadfall
 * (fallen trunks with moss and fungus). Each is given its origin on the ground (the first open block over it), writes only
 * open blocks (air, plants, leaves; never water) and stays within twelve blocks of its origin.
 */
public final class WildGrowth {
    private WildGrowth() {}

    private static final Codec<Block> BLOCK = BuiltInRegistries.BLOCK.byNameCodec();
    private static final int SET = Block.UPDATE_CLIENTS;

    /** Air or something a tree may grow through (grass, flowers, leaves), and dry. */
    static boolean open(BlockState s) {
        return (s.isAir() || s.is(BlockTags.REPLACEABLE_BY_TREES)) && s.getFluidState().isEmpty();
    }

    /** Soil a plant or trunk stands on. */
    static boolean soil(BlockState s) {
        return s.is(BlockTags.SUBSTRATE_OVERWORLD);
    }

    static BlockState leafState(Block b) {
        BlockState s = b.defaultBlockState();
        return s.hasProperty(LeavesBlock.PERSISTENT) ? s.setValue(LeavesBlock.PERSISTENT, true).setValue(LeavesBlock.DISTANCE, 1) : s;
    }

    static BlockState logState(Block b, Direction.Axis axis) {
        BlockState s = b.defaultBlockState();
        return s.hasProperty(RotatedPillarBlock.AXIS) ? s.setValue(RotatedPillarBlock.AXIS, axis) : s;
    }

    static void put(WorldGenLevel level, BlockPos pos, BlockState s) {
        if (open(level.getBlockState(pos))) level.setBlock(pos, s, SET);
    }

    /** An ellipsoid of leaves round {@code c}, {@code r} across and {@code ry} up, ragged at its rim. */
    static void blob(WorldGenLevel level, BlockPos c, double r, double ry, BlockState leaves, RandomSource random) {
        int R = Mth.ceil(r), RY = Mth.ceil(ry);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = -RY; dy <= RY; dy++) {
            for (int dz = -R; dz <= R; dz++) {
                for (int dx = -R; dx <= R; dx++) {
                    double d = (dx * dx + dz * dz) / (r * r) + dy * dy / (ry * ry);
                    if (d > 1.0 || d > 0.7 && random.nextFloat() < 0.35F) continue;
                    put(level, p.setWithOffset(c, dx, dy, dz), leaves);
                }
            }
        }
    }

    private static boolean onSoil(WorldGenLevel level, BlockPos origin) {
        return soil(level.getBlockState(origin.below())) && open(level.getBlockState(origin));
    }

    // ---- a big bush ---------------------------------------------------------------------------------------------------

    /** {@code awaken:wild_bush}: a leaf mound round a stub of wood, 2..5 wide (radius {@code max_radius} at most). */
    public record Bush(Block leaves, Block log, float maxRadius) implements Feature {
        public static final MapCodec<Bush> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BLOCK.fieldOf("leaves").forGetter(Bush::leaves), BLOCK.fieldOf("log").forGetter(Bush::log),
                Codec.floatRange(1F, 3F).optionalFieldOf("max_radius", 2.5F).forGetter(Bush::maxRadius)).apply(i, Bush::new));

        @Override
        public MapCodec<Bush> codec() {
            return CODEC;
        }

        @Override
        public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
            if (!onSoil(level, origin)) return false;
            paint(level, origin, this.leaves, this.log, 1.0 + random.nextDouble() * (this.maxRadius - 1.0), random);
            return true;
        }

        static void paint(WorldGenLevel level, BlockPos origin, Block leaves, Block log, double r, RandomSource random) {
            level.setBlock(origin, logState(log, Direction.Axis.Y), SET);
            blob(level, origin.above(r > 1.8 ? 1 : 0), r, Math.max(1.0, r * 0.7), leafState(leaves), random);
        }
    }

    // ---- a hedgerow ------------------------------------------------------------------------------------------------------

    /** {@code awaken:hedgerow}: 6..14 blocks of hawthorn one or two high along an old field edge, with gaps and cow parsley at the foot. */
    public record Hedgerow(Block leaves, Block foot) implements Feature {
        public static final MapCodec<Hedgerow> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BLOCK.fieldOf("leaves").forGetter(Hedgerow::leaves), BLOCK.fieldOf("foot").forGetter(Hedgerow::foot)).apply(i, Hedgerow::new));

        @Override
        public MapCodec<Hedgerow> codec() {
            return CODEC;
        }

        @Override
        public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
            if (!onSoil(level, origin)) return false;
            Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            int len = 6 + random.nextInt(9), y = origin.getY(), laid = 0;
            BlockState hedge = leafState(this.leaves);
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int i = 0; i < len; i++) {
                int x = origin.getX() + dir.getStepX() * i, z = origin.getZ() + dir.getStepZ() * i;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (Math.abs(top - y) > 1) break;                                  // the field edge follows the ground, never a cliff
                y = top;
                p.set(x, y, z);
                if (!soil(level.getBlockState(p.below())) || !open(level.getBlockState(p))) break;
                if (random.nextInt(9) == 0) continue;                               // a gap
                put(level, p, hedge);
                if (random.nextInt(10) < 7) put(level, p.move(Direction.UP), hedge);
                laid++;
                for (Direction side : new Direction[]{dir.getClockWise(), dir.getCounterClockWise()}) {
                    if (random.nextInt(5) != 0) continue;
                    p.set(x + side.getStepX(), y, z + side.getStepZ());
                    if (level.getBlockState(p).isAir()) WildGroundFeature.plant(level, p.immutable(), this.foot, random);
                }
            }
            return laid > 0;
        }
    }

    // ---- a bramble thicket -------------------------------------------------------------------------------------------------

    /** {@code awaken:thicket}: bramble over a patch 5..9 across with a leaf mound or two in it. */
    public record Thicket(Block bushLeaves, Block bushLog) implements Feature {
        public static final MapCodec<Thicket> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BLOCK.fieldOf("bush_leaves").forGetter(Thicket::bushLeaves), BLOCK.fieldOf("bush_log").forGetter(Thicket::bushLog)).apply(i, Thicket::new));

        @Override
        public MapCodec<Thicket> codec() {
            return CODEC;
        }

        @Override
        public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
            if (!onSoil(level, origin)) return false;
            double rx = 2.5 + random.nextDouble() * 2, rz = 2.5 + random.nextDouble() * 2;
            int n = 0;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int dz = -5; dz <= 5; dz++) {
                for (int dx = -5; dx <= 5; dx++) {
                    if (dx * dx / (rx * rx) + dz * dz / (rz * rz) > 1.0 || random.nextFloat() > 0.65F) continue;
                    int x = origin.getX() + dx, z = origin.getZ() + dz;
                    int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    if (Math.abs(top - origin.getY()) > 2) continue;
                    p.set(x, top, z);
                    if (WildGroundFeature.open(level.getBlockState(p)) && WildGroundFeature.plant(level, p.immutable(), WildBlocks.BRAMBLE.get(), random)) n++;
                }
            }
            for (int k = random.nextInt(3); k > 0; k--) {
                int x = origin.getX() + random.nextInt(5) - 2, z = origin.getZ() + random.nextInt(5) - 2;
                p.set(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                if (soil(level.getBlockState(p.below())) && open(level.getBlockState(p)))
                    Bush.paint(level, p.immutable(), this.bushLeaves, this.bushLog, 1.2 + random.nextDouble() * 0.8, random);
            }
            return n > 0;
        }
    }

    // ---- an old giant ------------------------------------------------------------------------------------------------------

    /**
     * {@code awaken:old_giant}: a two-by-two trunk {@code min_height..max_height} tall on root flares, four to six branches
     * from its upper half each ending in a leaf crown, and a wide crown on top. Gives up where its trunk would not stand free.
     */
    public record OldGiant(Block log, Block leaves, int minHeight, int maxHeight) implements Feature {
        public static final MapCodec<OldGiant> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BLOCK.fieldOf("log").forGetter(OldGiant::log), BLOCK.fieldOf("leaves").forGetter(OldGiant::leaves),
                Codec.intRange(10, 32).fieldOf("min_height").forGetter(OldGiant::minHeight),
                Codec.intRange(10, 32).fieldOf("max_height").forGetter(OldGiant::maxHeight)).apply(i, OldGiant::new));

        @Override
        public MapCodec<OldGiant> codec() {
            return CODEC;
        }

        @Override
        public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
            long t0 = System.nanoTime();
            boolean ok = grow(level, origin, random);
            LandsClock.add("old_giant", System.nanoTime() - t0);
            return ok;
        }

        public boolean grow(WorldGenLevel level, BlockPos origin, RandomSource random) {
            int h = this.minHeight + random.nextInt(Math.max(1, this.maxHeight - this.minHeight + 1));
            if (origin.getY() + h + 6 >= level.getMaxY()) return false;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int dx = 0; dx < 2; dx++) for (int dz = 0; dz < 2; dz++) {
                if (!soil(level.getBlockState(p.setWithOffset(origin, dx, -1, dz)))) return false;
                for (int y = 0; y < 8; y++) {
                    BlockState s = level.getBlockState(p.setWithOffset(origin, dx, y, dz));
                    if (!open(s)) return false;
                }
            }
            BlockState up = logState(this.log, Direction.Axis.Y), leaves = leafState(this.leaves);
            for (int dx = 0; dx < 2; dx++) for (int dz = 0; dz < 2; dz++) {
                level.setBlock(p.setWithOffset(origin, dx, -1, dz), Blocks.ROOTED_DIRT.defaultBlockState(), SET);
                for (int y = 0; y < h; y++) put(level, p.setWithOffset(origin, dx, y, dz), up);
            }
            // root flares: the eight blocks round the trunk's foot, one to three high, reaching down to the ground
            int[][] ring = {{-1, 0}, {-1, 1}, {2, 0}, {2, 1}, {0, -1}, {1, -1}, {0, 2}, {1, 2}};
            for (int[] o : ring) {
                int rise = 1 + random.nextInt(3);
                for (int y = -2; y < rise; y++) {
                    p.setWithOffset(origin, o[0], y, o[1]);
                    if (y < 0 && !open(level.getBlockState(p))) continue;
                    put(level, p, up);
                }
            }
            // branches from the upper half, each ending in a crown of leaves
            double cx = origin.getX() + 1.0, cz = origin.getZ() + 1.0;
            int branches = 4 + random.nextInt(3);
            double turn = random.nextDouble() * Math.PI * 2;
            for (int b = 0; b < branches; b++) {
                double a = turn + b * Math.PI * 2 / branches + (random.nextDouble() - 0.5) * 0.6;
                int from = (int) (h * (0.5 + 0.35 * random.nextDouble()));
                int len = 4 + random.nextInt(4);
                double ax = Math.cos(a), az = Math.sin(a);
                Direction.Axis axis = Math.abs(ax) > Math.abs(az) ? Direction.Axis.X : Direction.Axis.Z;
                int ex = 0, ey = 0, ez = 0;
                for (int t = 1; t <= len; t++) {
                    ex = Mth.floor(cx + ax * (t + 0.5));
                    ez = Mth.floor(cz + az * (t + 0.5));
                    ey = origin.getY() + from + t / 2;
                    put(level, p.set(ex, ey, ez), logState(this.log, t % 3 == 0 ? Direction.Axis.Y : axis));
                }
                blob(level, new BlockPos(ex, ey + 1, ez), 2.5 + random.nextDouble(), 1.8, leaves, random);
            }
            blob(level, origin.offset(0, h, 0), 4.0 + random.nextDouble() * 1.2, 2.6, leaves, random);
            return true;
        }
    }

    // ---- a snag ----------------------------------------------------------------------------------------------------------

    /** {@code awaken:snag}: a standing dead trunk 4..9 tall, bark gone above, its top broken, bracket fungus on its side. */
    public record Snag(Block log, Block bare) implements Feature {
        public static final MapCodec<Snag> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BLOCK.fieldOf("log").forGetter(Snag::log), BLOCK.fieldOf("bare").forGetter(Snag::bare)).apply(i, Snag::new));

        @Override
        public MapCodec<Snag> codec() {
            return CODEC;
        }

        @Override
        public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
            if (!onSoil(level, origin)) return false;
            int h = 4 + random.nextInt(6);
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int y = 0; y < h; y++) {
                if (!open(level.getBlockState(p.setWithOffset(origin, 0, y, 0)))) {
                    h = y;
                    break;
                }
                level.setBlock(p, logState(y < h * 0.45 ? this.log : this.bare, Direction.Axis.Y), SET);
            }
            if (h < 3) return h > 0;
            if (random.nextBoolean()) {                                             // a broken limb near the top
                Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(random);
                put(level, p.setWithOffset(origin, d.getStepX(), h - 2, d.getStepZ()), logState(this.bare, d.getAxis()));
            }
            fungus(level, origin.above(1 + random.nextInt(Math.max(1, h - 2))), random, 2);
            return true;
        }
    }

    /** Up to {@code n} bracket fungi on a log's open sides at {@code at}. */
    static void fungus(WorldGenLevel level, BlockPos at, RandomSource random, int n) {
        Direction start = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        for (int i = 0; i < 4 && n > 0; i++) {
            Direction d = Direction.from2DDataValue(start.get2DDataValue() + i);
            BlockPos f = at.relative(d);
            if (!level.getBlockState(f).isAir() || random.nextInt(3) == 0) continue;
            BlockState s = WildBlocks.BRACKET_FUNGUS.get().defaultBlockState().setValue(BracketFungusBlock.FACING, d);
            if (s.canSurvive(level, f)) {
                level.setBlock(f, s, SET);
                n--;
            }
        }
    }

    // ---- deadfall ---------------------------------------------------------------------------------------------------------

    /** {@code awaken:deadfall}: a fallen trunk 4..8 long on the ground with its stump, moss on its back and fungus on its sides. */
    public record Deadfall(Block log) implements Feature {
        public static final MapCodec<Deadfall> CODEC = BLOCK.fieldOf("log").xmap(Deadfall::new, Deadfall::log);

        @Override
        public MapCodec<Deadfall> codec() {
            return CODEC;
        }

        @Override
        public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
            if (!onSoil(level, origin)) return false;
            Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            level.setBlock(origin, logState(this.log, Direction.Axis.Y), SET);           // the stump
            if (random.nextBoolean()) put(level, origin.above(), logState(this.log, Direction.Axis.Y));
            int len = 4 + random.nextInt(5), laid = 0;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int i = 2; i < len + 2; i++) {
                p.setWithOffset(origin, d.getStepX() * i, 0, d.getStepZ() * i);
                if (!open(level.getBlockState(p))) break;
                BlockState under = level.getBlockState(p.below());
                if (open(under) && !open(level.getBlockState(p.below(2)))) p.move(Direction.DOWN);   // lie on the ground a step down
                else if (open(under)) break;
                level.setBlock(p, logState(this.log, d.getAxis()), SET);
                laid++;
                BlockPos back = p.above();
                if (random.nextInt(5) < 2 && level.getBlockState(back).isAir()) level.setBlock(back, Blocks.MOSS_CARPET.defaultBlockState(), SET);
                if (random.nextInt(4) == 0) fungus(level, p.immutable(), random, 1);
            }
            return laid > 0;
        }
    }
}
