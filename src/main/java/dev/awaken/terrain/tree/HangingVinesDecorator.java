package dev.awaken.terrain.tree;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;

/**
 * Strands hanging from the underside of a canopy: {@code body} blocks down to a {@code tip} (the Lumen trees' glowing
 * vines). Only from leaves with open air below, and never longer than the air beneath them.
 */
public final class HangingVinesDecorator extends TreeDecorator {
    public static final MapCodec<HangingVinesDecorator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.floatRange(0.0F, 1.0F).fieldOf("probability").forGetter(d -> d.probability),
            IntProviders.codec(1, 16).fieldOf("length").forGetter(d -> d.length),
            BlockState.CODEC.fieldOf("body").forGetter(d -> d.body),
            BlockState.CODEC.fieldOf("tip").forGetter(d -> d.tip)
    ).apply(i, HangingVinesDecorator::new));

    private final float probability;
    private final IntProvider length;
    private final BlockState body;
    private final BlockState tip;

    public HangingVinesDecorator(float probability, IntProvider length, BlockState body, BlockState tip) {
        this.probability = probability;
        this.length = length;
        this.body = body;
        this.tip = tip;
    }

    @Override
    protected TreeDecoratorType<?> type() {
        return LandsTrees.HANGING_VINES.get();
    }

    @Override
    public void place(Context context) {
        RandomSource random = context.random();
        for (BlockPos leaf : context.leaves()) {
            if (random.nextFloat() >= probability) continue;
            BlockPos below = leaf.below();
            if (!context.isAir(below)) continue;
            int want = length.sample(random), room = 0;
            while (room < want && context.isAir(below.below(room))) room++;
            for (int i = 0; i < room; i++) context.setBlock(below.below(i), i == room - 1 ? tip : body);
        }
    }
}
