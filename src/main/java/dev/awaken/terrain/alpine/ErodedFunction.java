package dev.awaken.terrain.alpine;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * {@code awaken:eroded} (SPEC §15, Alpine): wraps the terrain's 2D offset (vanilla's {@code minecraft:overworld/offset},
 * Tectonic's {@code tectonic:terrain_spline/offset/final}) and adds {@link Erosion} to it. Both routers put the ground
 * where the depth gradient {@code 1 - y/128} meets {@code -offset}, so a column's ground stands at
 * {@code 128 · (1 + offset)} and a change of {@code dh} blocks is {@code dh / 128} of offset. Because it changes the
 * offset, everything that reads it follows: the final density, the preliminary surface (aquifers, surface rules), and
 * Far Sight's predictor. The slope the gullies follow is the offset's own, measured {@link #SPAN} blocks either side
 * (the large-scale slope: a local one makes them swirl).
 *
 * <p>Only the Awakened Alpine data pack uses it (new worlds, feature flag {@code awaken:awakened_alpine}).
 */
public record ErodedFunction(DensityFunction input) implements DensityFunction {
    public static final MapCodec<ErodedFunction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.CODEC.fieldOf("input").forGetter(ErodedFunction::input)
    ).apply(i, ErodedFunction::new));

    /** Blocks either side for the slope. */
    public static final int SPAN = 24;
    /**
     * The most erosion can move the ground, in blocks: every octave at the bottom of a gully, carve included
     * ({@code (1 + GULLY + CARVE) · Σ height ≈ 1.88 × 64 = 120}).
     */
    static final float MAX_CHANGE = 128f;

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        return new Sampler(input.compileSampler(context));
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        DensityFunction in = rule.rewrite(input);
        return in == input ? this : new ErodedFunction(in);
    }

    @Override
    public Interval range() {
        Interval r = input.range();
        return Interval.of(r.min() - MAX_CHANGE / 128f, r.max() + MAX_CHANGE / 128f);
    }

    @Override
    public @Axes int domainAxes() {
        return AXIS_X | AXIS_Z;
    }

    @Override
    public MapCodec<ErodedFunction> codec() {
        return CODEC;
    }

    /** The offset at (x, z) with erosion added, from the offset there ({@code centre}) and around it. */
    static float eroded(float centre, float east, float west, float south, float north, int x, int z, float[] scratch) {
        float gx = (east - west) * 128f / (2 * SPAN), gz = (south - north) * 128f / (2 * SPAN);
        float h = 128f * (1f + centre);
        Erosion.erode(x, z, gx, gz, AlpineSurface.erosionStrength(h), 0f, scratch);
        return centre + scratch[0] / 128f;
    }

    private static final ThreadLocal<float[]> SCRATCH = ThreadLocal.withInitial(() -> new float[Erosion.OUT]);

    private record Sampler(DensitySampler input) implements DensitySampler {
        @Override
        public float sampleValue(SamplerContext context, int x, int y, int z) {
            float c = input.sampleValue(context, x, 0, z);
            return eroded(c, input.sampleValue(context, x + SPAN, 0, z), input.sampleValue(context, x - SPAN, 0, z),
                    input.sampleValue(context, x, 0, z + SPAN), input.sampleValue(context, x, 0, z - SPAN), x, z, SCRATCH.get());
        }

        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer out, DensityVolume volume) {
            // the centres in one go (the input's own volume path), the neighbours one by one
            input.sampleVolume(context, out, volume);
            float[] scratch = SCRATCH.get();
            int index = 0;
            for (int zi = 0; zi < volume.sizeZ(); zi++) {
                int z = volume.blockZ(zi);
                for (int xi = 0; xi < volume.sizeX(); xi++) {
                    int x = volume.blockX(xi);
                    float c = out.get(index);
                    float v = eroded(c, input.sampleValue(context, x + SPAN, 0, z), input.sampleValue(context, x - SPAN, 0, z),
                            input.sampleValue(context, x, 0, z + SPAN), input.sampleValue(context, x, 0, z - SPAN), x, z, scratch);
                    for (int yi = 0; yi < volume.sizeY(); yi++) out.set(index++, v);
                }
            }
        }
    }
}
