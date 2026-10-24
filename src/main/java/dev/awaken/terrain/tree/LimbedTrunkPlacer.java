package dev.awaken.terrain.tree;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The Awakened trees' trunk (docs/modules/lands.md, "Trees"): a 1x1 or 2x2 trunk that may lean, a flared root base,
 * and limbs that leave the trunk at different heights and angles to carry the canopy masses (each limb end, and the
 * top, is a foliage attachment). Limbs, root flares and the twig crosses inside each mass are {@code branch_provider}
 * blocks (bark on all sides); the twigs keep every leaf of a wide mass within reach of wood, so nothing decays.
 * Everything stays within {@code limb_length + twig} of the trunk; with a cloud of radius r the tree reaches
 * {@code limb_length + r} at most (kept at 12 or less: a feature may write one chunk beyond its own).
 */
public final class LimbedTrunkPlacer extends TrunkPlacer {
    public static final MapCodec<LimbedTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(0, 32).fieldOf("base_height").forGetter(p -> p.baseHeight),
            Codec.intRange(0, 24).fieldOf("height_rand_a").forGetter(p -> p.heightRandA),
            Codec.intRange(0, 24).fieldOf("height_rand_b").forGetter(p -> p.heightRandB),
            Codec.intRange(1, 2).fieldOf("width").forGetter(p -> p.width),
            IntProviders.codec(0, 6).optionalFieldOf("lean", ConstantInt.ZERO).forGetter(p -> p.lean),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("lean_start", 0.4F).forGetter(p -> p.leanStart),
            IntProviders.codec(0, 6).fieldOf("limbs").forGetter(p -> p.limbs),
            Codec.floatRange(0.0F, 1.0F).fieldOf("limb_start").forGetter(p -> p.limbStart),
            IntProviders.codec(1, 10).fieldOf("limb_length").forGetter(p -> p.limbLength),
            Codec.floatRange(0.0F, 3.0F).fieldOf("limb_rise").forGetter(p -> p.limbRise),
            IntProviders.codec(0, 5).optionalFieldOf("twig", ConstantInt.ZERO).forGetter(p -> p.twig),
            IntProviders.codec(0, 4).optionalFieldOf("root_flare", ConstantInt.ZERO).forGetter(p -> p.rootFlare),
            BlockStateProvider.CODEC.fieldOf("branch_provider").forGetter(p -> p.branchProvider)
    ).apply(i, LimbedTrunkPlacer::new));

    private final int width;
    private final IntProvider lean;
    private final float leanStart;
    private final IntProvider limbs;
    private final float limbStart;
    private final IntProvider limbLength;
    private final float limbRise;
    private final IntProvider twig;
    private final IntProvider rootFlare;
    private final Holder<BlockStateProvider> branchProvider;

    public LimbedTrunkPlacer(int baseHeight, int heightRandA, int heightRandB, int width, IntProvider lean, float leanStart, IntProvider limbs,
                             float limbStart, IntProvider limbLength, float limbRise, IntProvider twig, IntProvider rootFlare,
                             Holder<BlockStateProvider> branchProvider) {
        super(baseHeight, heightRandA, heightRandB);
        this.width = width;
        this.lean = lean;
        this.leanStart = leanStart;
        this.limbs = limbs;
        this.limbStart = limbStart;
        this.limbLength = limbLength;
        this.limbRise = limbRise;
        this.twig = twig;
        this.rootFlare = rootFlare;
        this.branchProvider = branchProvider;
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return LandsTrees.LIMBED_TRUNK.get();
    }

    @Override
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(WorldGenLevel level, BiConsumer<BlockPos, BlockState> trunkSetter, RandomSource random,
                                                            int treeHeight, BlockPos origin, TreeFeature tree) {
        List<FoliagePlacer.FoliageAttachment> out = new ArrayList<>();
        int w = width;
        for (int dx = 0; dx < w; dx++) for (int dz = 0; dz < w; dz++) placeBelowTrunkBlock(level, trunkSetter, random, origin.offset(dx, -1, dz), tree);

        // the trunk, leaning along a curve once past lean_start (a step block keeps it joined face to face)
        double leanAngle = random.nextDouble() * Math.PI * 2;
        int leanBy = lean.sample(random);
        int leanFrom = Math.round(treeHeight * leanStart);
        int[] ox = new int[treeHeight + 1], oz = new int[treeHeight + 1];
        for (int h = 0; h < treeHeight; h++) {
            double t = h <= leanFrom ? 0 : (h - leanFrom) / (double) Math.max(1, treeHeight - 1 - leanFrom);
            double off = leanBy * t * t;
            ox[h] = (int) Math.round(Math.cos(leanAngle) * off);
            oz[h] = (int) Math.round(Math.sin(leanAngle) * off);
            for (int dx = 0; dx < w; dx++) for (int dz = 0; dz < w; dz++) placeLog(level, trunkSetter, random, origin.offset(ox[h] + dx, h, oz[h] + dz), tree);
            if (h > 0 && (ox[h] != ox[h - 1] || oz[h] != oz[h - 1])) {
                for (int dx = 0; dx < w; dx++) {
                    for (int dz = 0; dz < w; dz++) {
                        placeLog(level, trunkSetter, random, origin.offset(ox[h - 1] + dx, h, oz[h - 1] + dz), tree);
                        if (ox[h] != ox[h - 1] && oz[h] != oz[h - 1]) placeLog(level, trunkSetter, random, origin.offset(ox[h] + dx, h, oz[h - 1] + dz), tree);
                    }
                }
            }
        }
        ox[treeHeight] = ox[treeHeight - 1];
        oz[treeHeight] = oz[treeHeight - 1];

        flare(level, trunkSetter, random, origin, tree);

        // limbs: spread round the trunk (golden-angle steps) and up its top part, the lowest first
        int n = limbs.sample(random);
        int lo = Mth.clamp(Math.round(treeHeight * limbStart), 1, treeHeight - 1), hi = Math.max(lo, treeHeight - 2);
        double a = random.nextDouble() * Math.PI * 2;
        for (int k = 0; k < n; k++) {
            a += 2.39996 + (random.nextDouble() - 0.5) * 0.6;
            int hy = n == 1 ? (lo + hi) / 2 : lo + Math.round((hi - lo) * k / (float) (n - 1)) - random.nextInt(2);
            hy = Mth.clamp(hy, 1, treeHeight - 1);
            double cx = origin.getX() + ox[hy] + (w - 1) / 2.0, cz = origin.getZ() + oz[hy] + (w - 1) / 2.0;
            int len = limbLength.sample(random);
            double rise = limbRise * (0.75 + 0.5 * random.nextDouble());
            BlockPos prev = null, end = null;
            for (double t = w / 2.0 + 0.5; t <= len + 0.01; t += 1.0) {
                BlockPos p = BlockPos.containing(cx + 0.5 + Math.cos(a) * t, origin.getY() + hy + t * rise, cz + 0.5 + Math.sin(a) * t);
                if (prev == null) prev = BlockPos.containing(cx + 0.5, origin.getY() + hy, cz + 0.5);
                joinBranch(level, trunkSetter, random, tree, prev, p);
                prev = p;
                end = p;
            }
            if (end == null) continue;
            twigs(level, trunkSetter, random, tree, end);
            out.add(new FoliagePlacer.FoliageAttachment(end, -1, false));
        }

        // the crown on top of the trunk
        BlockPos top = origin.offset(ox[treeHeight], treeHeight, oz[treeHeight]);
        for (int dx = 0; dx < w; dx++) for (int dz = 0; dz < w; dz++) placeBranch(level, trunkSetter, random, tree, top.offset(dx, 0, dz), Direction.Axis.Y);
        twigs(level, trunkSetter, random, tree, w == 2 ? top.offset(random.nextInt(2), 0, random.nextInt(2)) : top);
        out.add(new FoliagePlacer.FoliageAttachment(top, 0, w == 2));
        return out;
    }

    /** Root buttresses round the base: short walls of bark against each side, a few reaching one block further out. */
    private void flare(WorldGenLevel level, BiConsumer<BlockPos, BlockState> setter, RandomSource random, BlockPos origin, TreeFeature tree) {
        int flare = rootFlare.sample(random);
        if (flare <= 0) return;
        int w = width;
        for (int side = 0; side < 4; side++) {
            Direction d = Direction.from2DDataValue(side);
            for (int along = 0; along < w; along++) {
                int bx = d.getStepX() > 0 ? w : d.getStepX() < 0 ? -1 : along;
                int bz = d.getStepZ() > 0 ? w : d.getStepZ() < 0 ? -1 : along;
                int h = flare - random.nextInt(flare + 1);
                if (h <= 0) continue;
                rootColumn(level, setter, random, tree, origin.offset(bx, 0, bz), h);
                if (h >= 2 && random.nextInt(3) == 0) rootColumn(level, setter, random, tree, origin.offset(bx + d.getStepX(), 0, bz + d.getStepZ()), h - 1);
            }
        }
    }

    /** One root column of {@code h} blocks from the ground up; it reaches down (at most two blocks) to find the ground first. */
    private void rootColumn(WorldGenLevel level, BiConsumer<BlockPos, BlockState> setter, RandomSource random, TreeFeature tree, BlockPos base, int h) {
        BlockPos.MutableBlockPos m = base.mutable();
        int down = 0;
        while (down < 3 && !ground(level, m.below())) {
            m.move(Direction.DOWN);
            down++;
        }
        if (!ground(level, m.below())) return;
        for (int y = m.getY(); y < base.getY() + h; y++) placeBranch(level, setter, random, tree, m.setY(y).immutable(), Direction.Axis.Y);
    }

    private static boolean ground(WorldGenLevel level, BlockPos pos) {
        return level.isStateAtPosition(pos, s -> !s.isAir() && !s.canBeReplaced() && !s.is(BlockTags.LEAVES));
    }

    /** Branch blocks from {@code a} to {@code b}, stepping one axis at a time so the limb stays joined face to face. */
    private void joinBranch(WorldGenLevel level, BiConsumer<BlockPos, BlockState> setter, RandomSource random, TreeFeature tree, BlockPos a, BlockPos b) {
        BlockPos.MutableBlockPos m = a.mutable();
        int guard = 0;
        while (!m.equals(b) && guard++ < 32) {
            int dx = Integer.signum(b.getX() - m.getX()), dy = Integer.signum(b.getY() - m.getY()), dz = Integer.signum(b.getZ() - m.getZ());
            Direction.Axis axis;
            if (dy != 0 && (Math.abs(b.getY() - m.getY()) >= Math.max(Math.abs(b.getX() - m.getX()), Math.abs(b.getZ() - m.getZ())))) {
                m.move(0, dy, 0);
                axis = Direction.Axis.Y;
            } else if (dx != 0 && Math.abs(b.getX() - m.getX()) >= Math.abs(b.getZ() - m.getZ())) {
                m.move(dx, 0, 0);
                axis = Direction.Axis.X;
            } else if (dz != 0) {
                m.move(0, 0, dz);
                axis = Direction.Axis.Z;
            } else {
                m.move(0, dy, 0);
                axis = Direction.Axis.Y;
            }
            placeBranch(level, setter, random, tree, m.immutable(), axis);
        }
    }

    /** The twig cross inside a canopy mass: short arms along x and z from its centre. */
    private void twigs(WorldGenLevel level, BiConsumer<BlockPos, BlockState> setter, RandomSource random, TreeFeature tree, BlockPos centre) {
        int t = twig.sample(random);
        if (t <= 0) return;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            int len = t - random.nextInt(2);
            for (int i = 1; i <= len; i++) placeBranch(level, setter, random, tree, centre.relative(d, i), d.getAxis());
        }
    }

    private void placeBranch(WorldGenLevel level, BiConsumer<BlockPos, BlockState> setter, RandomSource random, TreeFeature tree, BlockPos pos, Direction.Axis axis) {
        BlockState state = branchProvider.value().getState(level, random, pos);
        BlockState placed = state.hasProperty(BlockStateProperties.AXIS) ? state.setValue(BlockStateProperties.AXIS, axis) : state;
        placeLog(level, setter, random, pos, tree, ignored -> placed);
    }
}
