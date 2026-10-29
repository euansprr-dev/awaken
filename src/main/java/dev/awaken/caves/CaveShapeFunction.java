package dev.awaken.caves;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.jspecify.annotations.Nullable;

/**
 * {@code awaken:cave_shape} (SPEC §42.3): wraps the overworld's cave density (Tectonic's {@code tectonic:caves}, or
 * vanilla's {@code minecraft:overworld/caves/entrances} without Tectonic) and opens a cave region's own shapes in it:
 * {@code min(input, shape)}, so every cave that was there stays and the region adds its own. The region at a point is
 * {@link CaveRegion#at}, read from the same band fields and climate the pack's biome injectors read, so the shapes are
 * exactly where the region's biome is.
 * <p>
 * The wrapped function sits inside the router's {@code interpolated}, so it is sampled on the 4 × 8 × 4 cell corners only
 * (5 × 49 × 5 a chunk), and everything 2D (the field, the climate, the depth's slope) is read once per column. Shapes are
 * held off the ground: they open only where the depth is past {@link #SEAL}, below where caves may start, so the surface
 * and the land's height never change (Far Sight's far ground reads the terrain density, not this).
 */
public record CaveShapeFunction(DensityFunction input, DensityFunction depth, DensityFunction shallows, DensityFunction middle,
                                DensityFunction deep, DensityFunction humidity, DensityFunction temperature,
                                DensityFunction continents, DensityFunction erosion,
                                Holder<NormalNoise> noiseA, Holder<NormalNoise> noiseB, Holder<NormalNoise> flat) implements DensityFunction {
    private static final Codec<DensityFunction> DF = DensityFunction.CODEC;
    public static final MapCodec<CaveShapeFunction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DF.fieldOf("input").forGetter(CaveShapeFunction::input),
            DF.fieldOf("depth").forGetter(CaveShapeFunction::depth),
            DF.fieldOf("shallows").forGetter(CaveShapeFunction::shallows),
            DF.fieldOf("middle").forGetter(CaveShapeFunction::middle),
            DF.fieldOf("deep").forGetter(CaveShapeFunction::deep),
            DF.fieldOf("humidity").forGetter(CaveShapeFunction::humidity),
            DF.fieldOf("temperature").forGetter(CaveShapeFunction::temperature),
            DF.fieldOf("continents").forGetter(CaveShapeFunction::continents),
            DF.fieldOf("erosion").forGetter(CaveShapeFunction::erosion),
            NormalNoise.CODEC.fieldOf("noise_a").forGetter(CaveShapeFunction::noiseA),
            NormalNoise.CODEC.fieldOf("noise_b").forGetter(CaveShapeFunction::noiseB),
            NormalNoise.CODEC.fieldOf("flat").forGetter(CaveShapeFunction::flat)
    ).apply(i, CaveShapeFunction::new));

    /** Shapes open only past this depth (about 28 blocks under the ground), fully by {@link #SEAL} + {@link #SEAL_FADE}. */
    public static final float SEAL = 0.22f, SEAL_FADE = 0.06f;
    /** The most a shape lowers the density (its floor): an open cave, never deeper than any cave's own. */
    static final float FLOOR = -1f;

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        return new Sampler(input.compileSampler(context), depth.compileSampler(context),
                new DensitySampler[]{shallows.compileSampler(context), middle.compileSampler(context), deep.compileSampler(context)},
                humidity.compileSampler(context), temperature.compileSampler(context), continents.compileSampler(context),
                erosion.compileSampler(context), context.createNoiseSampler(noiseA), context.createNoiseSampler(noiseB),
                context.createNoiseSampler(flat));
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        DensityFunction in = rule.rewrite(input), d = rule.rewrite(depth), s = rule.rewrite(shallows), m = rule.rewrite(middle),
                dp = rule.rewrite(deep), h = rule.rewrite(humidity), t = rule.rewrite(temperature), c = rule.rewrite(continents),
                e = rule.rewrite(erosion);
        if (in == input && d == depth && s == shallows && m == middle && dp == deep && h == humidity && t == temperature
                && c == continents && e == erosion) return this;
        return new CaveShapeFunction(in, d, s, m, dp, h, t, c, e, noiseA, noiseB, flat);
    }

    @Override
    public Interval range() {
        Interval r = input.range();
        return Interval.of(Math.min(r.min(), FLOOR), r.max());
    }

    @Override
    public @Axes int domainAxes() {
        return AXIS_X | AXIS_Y | AXIS_Z;
    }

    @Override
    public MapCodec<CaveShapeFunction> codec() {
        return CODEC;
    }

    /** One column's 2D reads: the band fields, the climate, and the depth as a line in y ({@code depth = d0 + slope · y}). */
    static final class Column {
        int x = Integer.MIN_VALUE, z = Integer.MIN_VALUE;
        final float[] field = new float[3];
        float humidity, temperature, continents, erosion, d0, slope;
        /** Each band's region here (null: plain cave), worked out once a column. */
        final CaveRegion[] region = new CaveRegion[3];
        final boolean[] known = new boolean[3];
        final CaveShapes.Column shape = new CaveShapes.Column();
    }

    static final class Sampler implements DensitySampler {
        private final DensitySampler input, depth, humidity, temperature, continents, erosion;
        private final DensitySampler[] fields;
        private final Noise a, b, flat;
        private final ThreadLocal<Column> column = ThreadLocal.withInitial(Column::new);

        Sampler(DensitySampler input, DensitySampler depth, DensitySampler[] fields, DensitySampler humidity, DensitySampler temperature,
                DensitySampler continents, DensitySampler erosion, Noise a, Noise b, Noise flat) {
            this.input = input;
            this.depth = depth;
            this.fields = fields;
            this.humidity = humidity;
            this.temperature = temperature;
            this.continents = continents;
            this.erosion = erosion;
            this.a = a;
            this.b = b;
            this.flat = flat;
        }

        @Override
        public float sampleValue(SamplerContext context, int x, int y, int z) {
            return shaped(context, input.sampleValue(context, x, y, z), x, y, z);
        }

        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer out, DensityVolume volume) {
            input.sampleVolume(context, out, volume);
            int index = 0;
            for (int zi = 0; zi < volume.sizeZ(); zi++) {
                int z = volume.blockZ(zi);
                for (int xi = 0; xi < volume.sizeX(); xi++) {
                    int x = volume.blockX(xi);
                    for (int yi = 0; yi < volume.sizeY(); yi++, index++) {
                        out.set(index, shaped(context, out.get(index), x, volume.blockY(yi), z));
                    }
                }
            }
        }

        private float shaped(SamplerContext context, float in, int x, int y, int z) {
            Column c = column(context, x, z);
            float d = c.d0 + c.slope * y;
            if (d < SEAL) return in;
            if (y > CaveRegion.MAX_Y) return in;
            float s = 1f;
            CaveRegion r = region(c, CaveRegion.Band.at(y, d));
            if (r != null && r.shape != CaveRegion.Shape.NONE) s = CaveShapes.density(r.shape, c.shape, a, b, flat, x, y, z, (d - SEAL) * 128f);
            // a shaft joins the bands where the Middle Dark and a band over or under it are regions
            if (y >= CaveShapes.SHAFT_FLOOR && shafted(c)) s = Math.min(s, CaveShapes.shaft(c.shape, x, z));
            if (s >= 1f) return in;
            // held off the ground: past the seal the shape eases in over SEAL_FADE
            s += Math.max(0f, (SEAL + SEAL_FADE - d) / SEAL_FADE);
            return Math.min(in, Math.max(FLOOR, s));
        }

        /** The band's region in this column (worked out once a column); the seal and the cap are checked per corner. */
        private @Nullable CaveRegion region(Column c, CaveRegion.Band band) {
            int i = band.ordinal();
            if (!c.known[i]) {
                c.region[i] = CaveRegion.in(band, c.field[i], c.humidity, c.temperature, c.continents, c.erosion);
                c.known[i] = true;
            }
            return c.region[i];
        }

        private boolean shafted(Column c) {
            return region(c, CaveRegion.Band.MIDDLE) != null
                    && (region(c, CaveRegion.Band.SHALLOWS) != null || region(c, CaveRegion.Band.DEEP) != null);
        }

        private Column column(SamplerContext context, int x, int z) {
            Column c = column.get();
            if (c.x == x && c.z == z) return c;
            c.x = x;
            c.z = z;
            for (int i = 0; i < 3; i++) {
                c.field[i] = fields[i].sampleValue(context, x, 0, z);
                c.known[i] = false;
            }
            c.shape.reset();
            c.humidity = humidity.sampleValue(context, x, 0, z);
            c.temperature = temperature.sampleValue(context, x, 0, z);
            c.continents = continents.sampleValue(context, x, 0, z);
            c.erosion = erosion.sampleValue(context, x, 0, z);
            // the depth is a line in y (vanilla's: the y gradient plus the offset); two reads give it
            float lo = depth.sampleValue(context, x, -64, z), hi = depth.sampleValue(context, x, 64, z);
            c.slope = (hi - lo) / 128f;
            c.d0 = lo + 64f * c.slope;
            return c;
        }
    }
}
