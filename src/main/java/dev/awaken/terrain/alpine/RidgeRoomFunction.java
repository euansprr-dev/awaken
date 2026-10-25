package dev.awaken.terrain.alpine;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * {@code awaken:ridge_room} (SPEC §15, Alpine): wraps Tectonic's ridge strength ({@code tectonic:terrain_spline/
 * jaggedness/continents}) so the ridges on top of a range only use the room left under the world's ceiling. Tectonic
 * adds them to the depth as {@code jaggedness × ridge}, the ridge term reaching about {@link #RIDGE}, so they could
 * lift a summit 130 blocks over its offset and into the ceiling, which cut it flat. Here the strength is held to
 * {@code (CEILING − ground) / (128 · RIDGE)}, the ground read from {@code offset} (the eroded, summit-eased offset):
 * low mountains keep all their ridges, the highest keep a few blocks of them, none reaches the ceiling.
 */
public record RidgeRoomFunction(DensityFunction input, DensityFunction offset) implements DensityFunction {
    public static final MapCodec<RidgeRoomFunction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.CODEC.fieldOf("input").forGetter(RidgeRoomFunction::input),
            DensityFunction.CODEC.fieldOf("offset").forGetter(RidgeRoomFunction::offset)
    ).apply(i, RidgeRoomFunction::new));

    /** The highest a ridge may reach (y), and the ridge term's largest value. */
    public static final float CEILING = 312f, RIDGE = 1.6f;

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        DensitySampler in = input.compileSampler(context), ground = offset.compileSampler(context);
        return new DensitySampler() {
            @Override
            public float sampleValue(SamplerContext ctx, int x, int y, int z) {
                return room(in.sampleValue(ctx, x, y, z), ground.sampleValue(ctx, x, 0, z));
            }

            @Override
            public void sampleVolume(SamplerContext ctx, net.minecraft.world.level.levelgen.densityfunction.DensityBuffer out,
                    net.minecraft.world.level.levelgen.densityfunction.DensityVolume volume) {
                in.sampleVolume(ctx, out, volume);
                int index = 0;
                for (int zi = 0; zi < volume.sizeZ(); zi++) {
                    int z = volume.blockZ(zi);
                    for (int xi = 0; xi < volume.sizeX(); xi++) {
                        float o = ground.sampleValue(ctx, volume.blockX(xi), 0, z);
                        for (int yi = 0; yi < volume.sizeY(); yi++, index++) out.set(index, room(out.get(index), o));
                    }
                }
            }
        };
    }

    /** A ridge strength held to the room its column has under the ceiling. */
    public static float room(float jaggedness, float offset) {
        float ground = 128f * (1f + offset);
        float most = Math.max(0f, CEILING - ground) / (128f * RIDGE);
        return Math.min(jaggedness, most);
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        DensityFunction in = rule.rewrite(input), o = rule.rewrite(offset);
        return in == input && o == offset ? this : new RidgeRoomFunction(in, o);
    }

    @Override
    public Interval range() {
        return input.range();
    }

    @Override
    public @Axes int domainAxes() {
        return input.domainAxes() | AXIS_X | AXIS_Z;
    }

    @Override
    public MapCodec<RidgeRoomFunction> codec() {
        return CODEC;
    }
}
