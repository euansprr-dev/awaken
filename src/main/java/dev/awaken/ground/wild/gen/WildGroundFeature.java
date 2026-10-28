package dev.awaken.ground.wild.gen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.ground.wild.RyeStemsBlock;
import dev.awaken.ground.wild.WildBlocks;
import dev.awaken.ground.wild.WildDoubleBlock;
import dev.awaken.ground.wild.WildLooks;
import dev.awaken.living.wild.WildField;
import dev.awaken.terrain.landmark.LandsClock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FlowerBedBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SnowyBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import dev.awaken.ground.wild.BrambleBlock;

import java.util.List;

/**
 * {@code awaken:wild_ground} (SPEC §41.3): the grass by wildness over one chunk, run once a chunk after the game's own grass.
 * Each column of the biomes in {@code biomes} reads its wildness and rolls each role in turn (the tallest first) at that
 * role's rate for its band, in swathes where the role has them; a column gets one plant at most. Tamed land has the game's
 * tall grass mowed (to short grass, or fern) and clover in the lawn; deep wild has sward patches in its swathes. The rates are
 * plants a chunk added to what the game grows, read between band middles like {@link WildnessPlacement}, so nothing shows a
 * line.
 */
public record WildGroundFeature(TagKey<Biome> biomes, boolean mow, float sward, List<Role> roles) implements Feature {
    /** One kind of plant: its blocks (one picked a placing), its plants a chunk by band, its swathe cell (0: scattered), shore only. */
    public record Role(List<Block> blocks, List<Float> rates, int swathe, boolean water) {
        public static final Codec<Role> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.BLOCK.byNameCodec().listOf().fieldOf("blocks").forGetter(Role::blocks),
                Codec.FLOAT.listOf().validate(l -> l.size() == 4 ? DataResult.success(l) : DataResult.error(() -> "four rates"))
                        .fieldOf("rates").forGetter(Role::rates),
                Codec.INT.optionalFieldOf("swathe", 0).forGetter(Role::swathe),
                Codec.BOOL.optionalFieldOf("water", false).forGetter(Role::water)).apply(i, Role::new));
    }

    public static final MapCodec<WildGroundFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            TagKey.codec(Registries.BIOME).fieldOf("biomes").forGetter(WildGroundFeature::biomes),
            Codec.BOOL.optionalFieldOf("mow", false).forGetter(WildGroundFeature::mow),
            Codec.FLOAT.optionalFieldOf("sward", 0F).forGetter(WildGroundFeature::sward),
            Role.CODEC.listOf().fieldOf("roles").forGetter(WildGroundFeature::roles)).apply(i, WildGroundFeature::new));

    /** Mowing reaches this wildness, fading in from {@link #MOW_FULL} (where every tall grass is cut). */
    public static final double MOW_TO = 0.25, MOW_FULL = 0.12;
    /** A swathe's core: the swathe noise over this is full, under {@link #SWATHE_LO} nothing; the rate's spread over the swathes. */
    static final double SWATHE_LO = 0.4, SWATHE_HI = 0.75, SWATHE_GAIN = 2.2;
    /** Sward: deep wild over this, in cells of this many blocks. */
    static final double SWARD_FROM = 0.7;
    static final int SWARD_CELL = 9;

    @Override
    public MapCodec<WildGroundFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        long t0 = System.nanoTime();
        int x0 = origin.getX() & ~15, z0 = origin.getZ() & ~15;
        int n = decorate(level, random, x0, z0, x0 + 16, z0 + 16, (x, z) -> WildLattice.at(level, x, z), true);
        LandsClock.add("wild_ground", System.nanoTime() - t0);
        return n > 0;
    }

    /**
     * Decorates the columns in [x0, x1) by [z0, z1) with {@code field}'s wildness. Returns how many columns changed. With
     * {@code checkBiome} false every column counts as in the biomes (the tests).
     */
    public int decorate(WorldGenLevel level, RandomSource random, int x0, int z0, int x1, int z1, WildLattice.Field field, boolean checkBiome) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        long seed = level.getSeed();
        int changed = 0;
        for (int z = z0; z < z1; z++) {
            for (int x = x0; x < x1; x++) {
                double w = field.at(x, z);
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                at.set(x, top, z);
                BlockState slot = level.getBlockState(at);
                int inBiome = checkBiome ? -1 : 1;    // looked up only when something would change
                if (this.mow && w < MOW_TO && (slot.is(Blocks.TALL_GRASS) || slot.is(Blocks.LARGE_FERN))
                        && slot.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER
                        && random.nextDouble() < Math.clamp((MOW_TO - w) / (MOW_TO - MOW_FULL), 0.0, 1.0)) {
                    if (inBiome(level, at, checkBiome)) {
                        level.setBlock(at.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        level.setBlock(at, (slot.is(Blocks.TALL_GRASS) ? Blocks.SHORT_GRASS : Blocks.FERN).defaultBlockState(), Block.UPDATE_CLIENTS);
                        changed++;
                    }
                    continue;
                }
                if (!open(slot)) continue;
                for (int r = 0; r < this.roles.size(); r++) {
                    Role role = this.roles.get(r);
                    double rate = WildnessPlacement.weight(role.rates, w) / 256.0;
                    if (rate <= 0) continue;
                    if (role.swathe > 0) {
                        double s = WildField.noise(seed ^ (0x5A7E_0000L + r * 7919L + role.swathe), x, z, role.swathe);
                        rate *= SWATHE_GAIN * Math.clamp((s - SWATHE_LO) / (SWATHE_HI - SWATHE_LO), 0.0, 1.0);
                    }
                    if (random.nextDouble() >= rate) continue;
                    if (inBiome < 0) inBiome = inBiome(level, at, checkBiome) ? 1 : 0;
                    if (inBiome == 0) break;
                    Block b = role.blocks.get(random.nextInt(role.blocks.size()));
                    if (role.water && !nearWater(level, at)) continue;
                    if (plant(level, at, b, random)) {
                        changed++;
                        break;
                    }
                }
                // sward: deep wild's ground with dry thatch showing, in patches
                if (this.sward > 0 && w > SWARD_FROM) {
                    double s = WildField.noise(seed ^ 0x5A7D_0001L, x, z, SWARD_CELL);
                    if (s > 0.6 && random.nextDouble() < this.sward * (w - SWARD_FROM) / (1 - SWARD_FROM)) {
                        BlockPos ground = at.below();
                        BlockState g = level.getBlockState(ground);
                        if (g.is(Blocks.GRASS_BLOCK) && (inBiome > 0 || inBiome < 0 && inBiome(level, at, checkBiome))) {
                            level.setBlock(ground, WildBlocks.WILD_SWARD.get().defaultBlockState().setValue(SnowyBlock.SNOWY, g.getValue(SnowyBlock.SNOWY)),
                                    Block.UPDATE_CLIENTS);
                            changed++;
                        }
                        at.set(x, top, z);
                    }
                }
            }
        }
        return changed;
    }

    private boolean inBiome(WorldGenLevel level, BlockPos at, boolean check) {
        return !check || level.getBiome(at).is(this.biomes);
    }

    /** A spot a plant may take: air, the game's short grass or fern, or a thin snow layer. */
    static boolean open(BlockState s) {
        return s.isAir() || s.is(Blocks.SHORT_GRASS) || s.is(Blocks.FERN) || s.is(Blocks.SNOW) && s.getValue(SnowLayerBlock.LAYERS) == 1;
    }

    /** Free for a plant's upper blocks. */
    private static boolean free(WorldGenLevel level, BlockPos pos) {
        return level.getBlockState(pos).isAir() && level.isInsideBuildHeight(pos);
    }

    private static boolean nearWater(WorldGenLevel level, BlockPos at) {
        BlockPos ground = at.below();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (level.getFluidState(ground.relative(d)).is(FluidTags.WATER) || level.getFluidState(at.relative(d)).is(FluidTags.WATER)) return true;
        }
        return false;
    }

    /** Places {@code b} at {@code at} (its foot) when it can stand there and has room; true when placed. */
    static boolean plant(WorldGenLevel level, BlockPos at, Block b, RandomSource random) {
        BlockState s = b.defaultBlockState();
        if (b instanceof RyeStemsBlock) {
            if (!free(level, at.above()) || !free(level, at.above(2)) || !s.canSurvive(level, at)) return false;
            BlockState rye = WildBlocks.WILD_RYE.get().defaultBlockState().setValue(WildDoubleBlock.LOOK, WildLooks.look(level, WildBlocks.WILD_RYE.get(), at));
            level.setBlock(at, s, Block.UPDATE_CLIENTS);
            DoublePlantBlock.placeAt(level, rye, at.above(), Block.UPDATE_CLIENTS);
            return true;
        }
        if (b instanceof DoublePlantBlock) {
            if (!free(level, at.above()) || !s.canSurvive(level, at)) return false;
            if (b instanceof WildDoubleBlock) s = s.setValue(WildDoubleBlock.LOOK, WildLooks.look(level, b, at));
            DoublePlantBlock.placeAt(level, s, at, Block.UPDATE_CLIENTS);
            return true;
        }
        if (b instanceof FlowerBedBlock) {
            s = s.setValue(FlowerBedBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random)).setValue(FlowerBedBlock.AMOUNT, 1 + random.nextInt(4));
        } else if (b instanceof BrambleBlock) {
            int f = random.nextInt(6);
            s = s.setValue(BrambleBlock.FRUIT, f < 3 ? 0 : f < 5 ? 1 : 2);
        }
        if (!s.canSurvive(level, at)) return false;
        level.setBlock(at, s, Block.UPDATE_CLIENTS);
        return true;
    }
}
