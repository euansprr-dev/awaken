package dev.awaken.terrain.city;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.terrain.api.CityBasin;
import dev.awaken.terrain.api.CitySite;
import net.minecraft.util.Interval;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

import java.util.List;

/**
 * {@code awaken:city_basin}: wraps the overworld's final density ({@code mode: density}) or its preliminary surface
 * level ({@code mode: surface}: what aquifers and surface rules believe the ground height is) so the ground around the
 * city site is flat at street level, a river channel runs through it, and both ease back to the natural terrain by the
 * blend radius. The surface estimate dips well under the river (see {@link Shape#channelEstimate}) so the aquifer
 * treats the channel as sea-level water and fills it to the waterline. Deep below the street (more than {@link #CRUST} blocks)
 * the natural density stays, so caves and ores go on under the city. With no site in this world (old worlds, Classic
 * start, a test generator) it compiles to its input unchanged.
 */
public record CityBasinFunction(DensityFunction input, Mode mode) implements DensityFunction {
    public enum Mode {
        DENSITY, SURFACE;
        static final Codec<Mode> CODEC = Codec.STRING.xmap(s -> s.equals("surface") ? SURFACE : DENSITY, m -> m == SURFACE ? "surface" : "density");
    }

    public static final MapCodec<CityBasinFunction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.CODEC.fieldOf("input").forGetter(CityBasinFunction::input),
            Mode.CODEC.optionalFieldOf("mode", Mode.DENSITY).forGetter(CityBasinFunction::mode)
    ).apply(i, CityBasinFunction::new));

    /** Solid ground kept under the street before the natural density (caves) takes over again. */
    static final int CRUST = 20;
    /** How far past the core the river channel still carves (it fades out over this distance). */
    static final int RIVER_FADE = 160;

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        DensitySampler in = input.compileSampler(context);
        CitySite site = CityBasin.current();
        if (site == null || !site.basinCarved()) return in;
        return new Sampler(in, new Shape(site), mode);
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        DensityFunction in = rule.rewrite(input);
        return in == input ? this : new CityBasinFunction(in, mode);
    }

    @Override
    public Interval range() {
        return mode == Mode.SURFACE ? Interval.encapsulating(input.range(), Interval.of(CityBasin.RIVER_FLOOR_Y - 8, CityBasin.STREET_Y))
                : Interval.encapsulating(input.range(), Interval.of(-1.0F, 1.0F));
    }

    @Override
    public @Axes int domainAxes() {
        return input.domainAxes() | AXIS_X | AXIS_Z | (mode == Mode.DENSITY ? AXIS_Y : 0);
    }

    @Override
    public MapCodec<CityBasinFunction> codec() {
        return CODEC;
    }

    /** The basin's shape for one site: pure maths, shared by both modes and by the tests. */
    public static final class Shape {
        final int cx, cz, core, blend, street, half, floor;
        final float[] rx, rz; // river centre line, world coordinates
        final long blendSq;

        public Shape(CitySite s) {
            cx = s.x();
            cz = s.z();
            core = s.coreRadius();
            blend = s.blendRadius();
            street = s.streetY();
            half = s.riverHalfWidth();
            floor = s.riverFloorY();
            List<int[]> r = s.river();
            rx = new float[r.size()];
            rz = new float[r.size()];
            for (int i = 0; i < r.size(); i++) {
                rx[i] = r.get(i)[0];
                rz[i] = r.get(i)[1];
            }
            blendSq = (long) blend * blend;
        }

        /** 1 in the core, easing to 0 at the blend radius. */
        public float weight(int x, int z) {
            long dx = x - cx, dz = z - cz;
            long d2 = dx * dx + dz * dz;
            if (d2 >= blendSq) return 0.0F;
            float d = (float) Math.sqrt(d2);
            if (d <= core) return 1.0F;
            float t = (d - core) / (blend - core);
            return 1.0F - t * t * (3.0F - 2.0F * t);
        }

        /** Distance (blocks) from (x, z) to the river's centre line. */
        public float riverDistance(int x, int z) {
            float best = Float.MAX_VALUE;
            for (int i = 0; i + 1 < rx.length; i++) {
                float ax = rx[i], az = rz[i], bx = rx[i + 1], bz = rz[i + 1];
                float vx = bx - ax, vz = bz - az, wx = x - ax, wz = z - az;
                float len2 = vx * vx + vz * vz;
                float t = len2 <= 0 ? 0 : Mth.clamp((wx * vx + wz * vz) / len2, 0.0F, 1.0F);
                float px = ax + vx * t - x, pz = az + vz * t - z;
                float d2 = px * px + pz * pz;
                if (d2 < best) best = d2;
            }
            return (float) Math.sqrt(best);
        }

        /** How strongly the river carves here: 1 in the core, fading to 0 over RIVER_FADE blocks past it. */
        float riverWeight(int x, int z) {
            long dx = x - cx, dz = z - cz;
            float d = (float) Math.sqrt(dx * dx + dz * dz);
            if (d <= core) return 1.0F;
            float t = Math.min(1.0F, (d - core) / RIVER_FADE);
            return 1.0F - t * t * (3.0F - 2.0F * t);
        }

        /** Ground height of the channel at distance d from the river's centre line (street level outside it). */
        public float channelSurface(float d) {
            if (d <= half - 2) return floor;
            if (d <= half) return floor + (d - (half - 2)) / 2.0F * (63 - floor); // steep inner wall up to the waterline
            if (d <= half + 3) return 63 + (d - half) / 3.0F * (street - 63); // the bank up to the street
            return street;
        }

        /**
         * The surface estimate over the channel. The aquifer fills a column with sea water only when this estimate plus 8
         * lies below sea level (Aquifer.computeFluid), so over the channel it reads 8 below the river floor; a little
         * wider than the channel, since aquifers sample it every 4 blocks.
         */
        public float channelEstimate(float d) {
            float low = floor - 8;
            if (d <= half + 4) return low;
            if (d <= half + 10) return low + (d - (half + 4)) / 6.0F * (street - low);
            return street;
        }

        /** The basin's own density at height y over a column whose ground stands at {@code surface}. */
        static float target(float surface, int y, float natural) {
            if (y < surface - CRUST) return natural;
            return Mth.clamp((surface + 0.5F - y) * 0.1F, -1.0F, 1.0F);
        }
    }

    private record Sampler(DensitySampler input, Shape shape, Mode mode) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer out, DensityVolume volume) {
            input.sampleVolume(context, out, volume);
            int index = 0;
            for (int z = 0; z < volume.sizeZ(); z++) {
                int bz = volume.blockZ(z);
                for (int x = 0; x < volume.sizeX(); x++) {
                    int bx = volume.blockX(x);
                    float w = shape.weight(bx, bz);
                    if (w <= 0.0F) {
                        index += volume.sizeY();
                        continue;
                    }
                    float rw = shape.riverWeight(bx, bz);
                    float channel = channel(bx, bz, rw);
                    for (int y = 0; y < volume.sizeY(); y++, index++) {
                        out.set(index, apply(out.get(index), w, rw, channel, volume.blockY(y)));
                    }
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int x, int y, int z) {
            float natural = input.sampleValue(context, x, y, z);
            float w = shape.weight(x, z);
            if (w <= 0.0F) return natural;
            float rw = shape.riverWeight(x, z);
            return apply(natural, w, rw, channel(x, z, rw), y);
        }

        private float channel(int x, int z, float rw) {
            if (rw <= 0) return shape.street;
            float d = shape.riverDistance(x, z);
            return mode == Mode.SURFACE ? shape.channelEstimate(d) : shape.channelSurface(d);
        }

        private float apply(float natural, float w, float rw, float channel, int y) {
            if (mode == Mode.SURFACE) {
                float flat = natural + w * (shape.street - natural);
                return channel >= shape.street ? flat : Math.min(flat, flat + rw * (channel - flat));
            }
            float flat = natural + w * (Shape.target(shape.street, y, natural) - natural);
            if (rw <= 0.0F || channel >= shape.street) return flat;
            // the channel only ever removes ground: never more solid than the flattened terrain
            float carved = flat + rw * (Shape.target(channel, y, natural) - flat);
            return Math.min(flat, carved);
        }
    }
}
