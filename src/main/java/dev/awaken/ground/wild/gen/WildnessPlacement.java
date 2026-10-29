package dev.awaken.ground.wild.gen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

import java.util.List;
import java.util.function.Consumer;

/**
 * {@code awaken:wildness} (SPEC §41.3): how many times a feature is tried here, by how wild the land is. {@code weights} are
 * the count in the middle of each band (Tamed, Pasture, Wild, Deep wild), read between band middles in a straight line, so
 * the count changes smoothly and no band edge shows a line; a fraction is a chance of one more. Put it first, before
 * {@code in_square}: it reads the chunk's corner.
 */
public record WildnessPlacement(List<Float> weights) implements PlacementModifier {
    public static final MapCodec<WildnessPlacement> CODEC = Codec.FLOAT.listOf()
            .validate(l -> l.size() == 4 ? DataResult.success(l) : DataResult.error(() -> "wildness weights are four: tamed, pasture, wild, deep"))
            .fieldOf("weights").xmap(WildnessPlacement::new, WildnessPlacement::weights);

    /** The bands' middles: where each weight holds exactly. */
    static final double[] MIDDLES = {0.1, 0.325, 0.575, 0.85};

    /** The weight at wildness {@code w} from four band weights. */
    public static double weight(List<Float> weights, double w) {
        if (w <= MIDDLES[0]) return weights.get(0);
        for (int i = 1; i < 4; i++) {
            if (w <= MIDDLES[i]) {
                double t = (w - MIDDLES[i - 1]) / (MIDDLES[i] - MIDDLES[i - 1]);
                return weights.get(i - 1) + (weights.get(i) - weights.get(i - 1)) * t;
            }
        }
        return weights.get(3);
    }

    @Override
    public void modify(PlacementContext context, RandomSource random, BlockPos origin, Consumer<BlockPos> output) {
        double k = weight(this.weights, WildLattice.at(context.getLevel(), origin.getX(), origin.getZ()));
        int n = (int) k;
        if (random.nextDouble() < k - n) n++;
        for (int i = 0; i < n; i++) output.accept(origin);
    }

    @Override
    public MapCodec<WildnessPlacement> codec() {
        return CODEC;
    }
}
