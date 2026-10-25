package dev.awaken.terrain.alpine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.condition.ConditionEvaluator;
import net.minecraft.world.level.levelgen.material.condition.MaterialCondition;

/**
 * {@code awaken:alpine} material condition (SPEC §15, Alpine): {@link AlpineSurface}'s rule for the block being
 * placed, from the column's surface gradient (the height difference across its two neighbours, as vanilla's
 * {@code steep} reads it). {@code kind}: {@code snow} (above the snow line and gentle enough to hold it), {@code scree}
 * (loose stone under cliffs) or {@code rock} (a bare face).
 */
public record AlpineCondition(Kind kind) implements MaterialCondition {
    public enum Kind implements StringRepresentable {
        SNOW, SCREE, ROCK;
        static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final MapCodec<AlpineCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Kind.CODEC.fieldOf("kind").forGetter(AlpineCondition::kind)
    ).apply(i, AlpineCondition::new));

    @Override
    public ConditionEvaluator compile(MaterialRuleContext context) {
        return new MaterialRuleContext.LazyYCondition(context) {
            @Override
            protected boolean compute() {
                MaterialRuleContext c = this.context;
                // vanilla's gradient spans two blocks (one at a chunk's edge); half of it is the rise per block
                float gx = c.surfaceGradientX() * 0.5f, gz = c.surfaceGradientZ() * 0.5f;
                int x = c.blockX(), y = c.blockY(), z = c.blockZ();
                float j = AlpineSurface.jitter(x, y, z);
                return switch (kind) {
                    case SNOW -> AlpineSurface.snow(x, y, z, gx, gz, j);
                    case SCREE -> AlpineSurface.stone(gx, gz, j) == 1;
                    case ROCK -> AlpineSurface.stone(gx, gz, j) == 2;
                };
            }
        };
    }

    @Override
    public MapCodec<AlpineCondition> codec() {
        return CODEC;
    }
}
