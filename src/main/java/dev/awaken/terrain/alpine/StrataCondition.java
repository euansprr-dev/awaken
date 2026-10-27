package dev.awaken.terrain.alpine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.condition.ConditionEvaluator;
import net.minecraft.world.level.levelgen.material.condition.MaterialCondition;

/**
 * {@code awaken:strata} material condition (SPEC §31, P3): whether the block being placed lies in a layer of the named rock
 * ({@link AlpineSurface#strata}: andesite, tuff, granite or a thin calcite band), so a cliff's rock reads as layers.
 */
public record StrataCondition(Kind kind) implements MaterialCondition {
    public enum Kind implements StringRepresentable {
        ANDESITE(1), TUFF(2), GRANITE(3), CALCITE(4);

        static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);
        final int layer;

        Kind(int layer) {
            this.layer = layer;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final MapCodec<StrataCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Kind.CODEC.fieldOf("kind").forGetter(StrataCondition::kind)
    ).apply(i, StrataCondition::new));

    @Override
    public ConditionEvaluator compile(MaterialRuleContext context) {
        return new MaterialRuleContext.LazyYCondition(context) {
            @Override
            protected boolean compute() {
                MaterialRuleContext c = this.context;
                return AlpineSurface.strata(c.blockX(), c.blockY(), c.blockZ()) == kind.layer;
            }
        };
    }

    @Override
    public MapCodec<StrataCondition> codec() {
        return CODEC;
    }
}
