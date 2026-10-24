package dev.awaken.terrain.tree;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType;

/**
 * A canopy mass for the Awakened trees: an ellipsoid of leaves round each attachment, domed on top and flatter below
 * ({@code depth} is the lower half-height as a share of {@code height}), with a ragged edge, an optional hollow core
 * (MoreCulling then culls its inside faces) and leaves drooping from the rim. With {@code height} 1 and a small depth it
 * is a flat pad (the Lumen trees); taller, a billowing cloud (the elders).
 *
 * <p>Only leaves that reach wood within six steps through other leaves are placed (a walk out from the logs already in
 * the mass: the limb and its twig cross), which is vanilla's decay rule: no leaf decays the moment it generates and
 * none hangs loose at the ragged edge.
 */
public final class CloudFoliagePlacer extends FoliagePlacer {
    public static final MapCodec<CloudFoliagePlacer> CODEC = RecordCodecBuilder.mapCodec(i -> foliagePlacerParts(i).and(i.group(
            IntProviders.codec(1, 8).fieldOf("height").forGetter(p -> p.height),
            Codec.floatRange(0.0F, 2.0F).fieldOf("depth").forGetter(p -> p.depth),
            Codec.floatRange(0.0F, 0.9F).optionalFieldOf("hollow", 0.0F).forGetter(p -> p.hollow),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("ragged", 0.3F).forGetter(p -> p.ragged),
            Codec.floatRange(0.0F, 1.0F).optionalFieldOf("hang", 0.0F).forGetter(p -> p.hang)
    )).apply(i, CloudFoliagePlacer::new));

    /** Leaves further than this from wood decay (LeavesBlock.DECAY_DISTANCE - 1). */
    private static final int REACH = 6;

    private final IntProvider height;
    private final float depth;
    private final float hollow;
    private final float ragged;
    private final float hang;

    public CloudFoliagePlacer(IntProvider radius, IntProvider offset, IntProvider height, float depth, float hollow, float ragged, float hang) {
        super(radius, offset);
        this.height = height;
        this.depth = depth;
        this.hollow = hollow;
        this.ragged = ragged;
        this.hang = hang;
    }

    @Override
    protected FoliagePlacerType<?> type() {
        return LandsTrees.CLOUD_FOLIAGE.get();
    }

    @Override
    protected void createFoliage(WorldGenLevel level, FoliageSetter setter, RandomSource random, TreeFeature tree, int treeHeight,
                                 FoliageAttachment attachment, int foliageHeight, int leafRadius, int offset) {
        int r = Math.max(1, leafRadius + attachment.radiusOffsetXZ());
        int up = height.sample(random);
        double down = Math.max(0.6, up * depth);
        int below = (int) Math.ceil(down) + 2; // two more rows for the drooping leaves
        BlockPos c = attachment.pos().above(offset);
        double shift = attachment.doubleTrunk() ? 0.5 : 0.0;
        int extra = attachment.doubleTrunk() ? 1 : 0;
        int lo = -r - 1, sx = 2 * (r + 1) + 1 + extra, sy = below + up + 1;
        int size = sx * sy * sx;
        // 0 outside, 1 inside the mass, 2 wood (a walk starts there); distances to wood through the mass
        byte[] cell = new byte[size];
        byte[] dist = new byte[size];
        int[] queue = new int[size];
        int head = 0, tail = 0;
        double rr = r + 0.5, hollowSq = hollow * hollow;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = 0; y < sy; y++) {
            int dy = y - below;
            double fy = dy >= 0 ? dy / (up + 0.5) : dy / (down + 0.5);
            for (int x = 0; x < sx; x++) {
                for (int z = 0; z < sx; z++) {
                    int i = (y * sx + x) * sx + z;
                    m.setWithOffset(c, x + lo, dy, z + lo);
                    if (level.isStateAtPosition(m, s -> s.is(BlockTags.LOGS))) {
                        cell[i] = 2;
                        queue[tail++] = i;
                        continue;
                    }
                    double fx = (x + lo - shift) / rr, fz = (z + lo - shift) / rr;
                    double e = fx * fx + fy * fy + fz * fz;
                    if (e > 1.0 + (random.nextFloat() - 0.5F) * ragged || e < hollowSq || dy < -Math.ceil(down)) continue;
                    if (free(level, m)) cell[i] = 1;
                }
            }
        }
        java.util.Arrays.fill(dist, (byte) 127);
        for (int k = 0; k < tail; k++) dist[queue[k]] = 0;
        while (head < tail) {
            int i = queue[head++];
            int d = dist[i] + 1;
            if (d > REACH) continue;
            int z = i % sx, x = (i / sx) % sx, y = i / (sx * sx);
            for (int k = 0; k < 6; k++) {
                int nx = x + (k == 0 ? 1 : k == 1 ? -1 : 0), ny = y + (k == 2 ? 1 : k == 3 ? -1 : 0), nz = z + (k == 4 ? 1 : k == 5 ? -1 : 0);
                if (nx < 0 || ny < 0 || nz < 0 || nx >= sx || ny >= sy || nz >= sx) continue;
                int n = (ny * sx + nx) * sx + nz;
                if (cell[n] != 1 || dist[n] <= d) continue;
                dist[n] = (byte) d;
                queue[tail++] = n;
            }
        }
        for (int y = 0; y < sy; y++) {
            for (int x = 0; x < sx; x++) {
                for (int z = 0; z < sx; z++) {
                    int i = (y * sx + x) * sx + z;
                    if (cell[i] == 1 && dist[i] <= REACH) tryPlaceLeaf(level, setter, random, tree, m.setWithOffset(c, x + lo, y - below, z + lo));
                }
            }
        }
        if (hang <= 0) return;
        // leaves drooping from the underside of the outer columns, while they stay within reach of wood
        for (int x = 0; x < sx; x++) {
            for (int z = 0; z < sx; z++) {
                double fx = (x + lo - shift) / rr, fz = (z + lo - shift) / rr;
                double e = fx * fx + fz * fz;
                if (e < 0.35 || e > 1.05 || random.nextFloat() >= hang) continue;
                for (int y = 0; y < sy; y++) {
                    int i = (y * sx + x) * sx + z;
                    if (cell[i] != 1 || dist[i] > REACH) continue;
                    int d = dist[i];
                    for (int k = 1; k <= 2 && d + k <= REACH && y - k >= 0; k++) {
                        if (k == 2 && random.nextFloat() >= hang * 0.5F) break;
                        m.setWithOffset(c, x + lo, y - k - below, z + lo);
                        if (!free(level, m) || !tryPlaceLeaf(level, setter, random, tree, m)) break;
                    }
                    break;
                }
            }
        }
    }

    /** Room for a leaf: air or anything a tree may grow into, but never someone's persistent leaves. */
    private static boolean free(WorldGenLevel level, BlockPos pos) {
        return TreeFeature.validTreePos(level, pos)
                && !level.isStateAtPosition(pos, s -> s.getValueOrElse(BlockStateProperties.PERSISTENT, false));
    }

    @Override
    public int foliageHeight(RandomSource random, int treeHeight, TreeFeature tree) {
        return 0; // the trunk placer uses the whole tree height; each mass picks its own height
    }

    @Override
    protected boolean shouldSkipLocation(RandomSource random, int dx, int y, int dz, int currentRadius, boolean doubleTrunk) {
        return false;
    }
}
