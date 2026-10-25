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
 * Far Sight's predictor.
 * <ul>
 *   <li>The slope the gullies follow is the offset's own at a large scale: central differences on a {@link #LATTICE}
 *       block lattice, blended between its nodes (a local slope makes them swirl). Nodes are cached per thread, so
 *       neighbouring columns and chunks share them: about one offset sample per column, not five.</li>
 *   <li>A range's body rounds off toward {@link #TOP} instead of flattening: the pack lifts Tectonic's own offset clamp
 *       (which cut every high range into a flat plateau near y 303) and the ground eases under this instead. The body
 *       stays well under the world's ceiling so Tectonic's ridges have room above it ({@link RidgeRoomFunction}): peaks
 *       are ridgelines, not table tops.</li>
 * </ul>
 * Only the Awakened Alpine data pack uses it (new worlds, feature flag {@code awaken:awakened_alpine}).
 */
public record ErodedFunction(DensityFunction input) implements DensityFunction {
    public static final MapCodec<ErodedFunction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DensityFunction.CODEC.fieldOf("input").forGetter(ErodedFunction::input)
    ).apply(i, ErodedFunction::new));

    /** The slope lattice's spacing in blocks. */
    public static final int LATTICE = 64;
    /** Where summits start to round off, and the height they approach but never reach (y). */
    public static final float KNEE = 190f, TOP = 256f;
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
        return Interval.of(r.min() - MAX_CHANGE / 128f, Math.min(r.max() + MAX_CHANGE / 128f, TOP / 128f - 1f));
    }

    @Override
    public @Axes int domainAxes() {
        return AXIS_X | AXIS_Z;
    }

    @Override
    public MapCodec<ErodedFunction> codec() {
        return CODEC;
    }

    /** The ground (y) eased under {@link #TOP}: unchanged up to {@link #KNEE}, then ever closer to it, never flat. */
    public static float summit(float y) {
        if (y <= KNEE) return y;
        float room = TOP - KNEE;
        return TOP - room * (float) Math.exp(-(y - KNEE) / room);
    }

    /** One compiled wrap: the input's sampler and, per thread, its slope lattice. */
    static final class Sampler implements DensitySampler {
        private final DensitySampler input;
        private final ThreadLocal<Lattice> lattice = ThreadLocal.withInitial(Lattice::new);

        Sampler(DensitySampler input) {
            this.input = input;
        }

        @Override
        public float sampleValue(SamplerContext context, int x, int y, int z) {
            return eroded(context, input.sampleValue(context, x, 0, z), x, z, lattice.get());
        }

        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer out, DensityVolume volume) {
            // the centres in one go (the input's own volume path), the slope from the lattice
            input.sampleVolume(context, out, volume);
            Lattice l = lattice.get();
            int index = 0;
            for (int zi = 0; zi < volume.sizeZ(); zi++) {
                int z = volume.blockZ(zi);
                for (int xi = 0; xi < volume.sizeX(); xi++) {
                    int x = volume.blockX(xi);
                    float v = eroded(context, out.get(index), x, z, l);
                    for (int yi = 0; yi < volume.sizeY(); yi++) out.set(index++, v);
                }
            }
        }

        private float eroded(SamplerContext context, float centre, int x, int z, Lattice l) {
            int ix = Math.floorDiv(x, LATTICE), iz = Math.floorDiv(z, LATTICE);
            float fx = (x - ix * LATTICE) / (float) LATTICE, fz = (z - iz * LATTICE) / (float) LATTICE;
            // the four nodes' slopes (central differences, rise per block), blended across the cell
            float gx = 0f, gz = 0f;
            for (int k = 0; k < 4; k++) {
                int nx = ix + (k & 1), nz = iz + (k >> 1);
                float w = ((k & 1) == 0 ? 1f - fx : fx) * ((k >> 1) == 0 ? 1f - fz : fz);
                if (w == 0f) continue;
                gx += w * (node(context, nx + 1, nz, l) - node(context, nx - 1, nz, l));
                gz += w * (node(context, nx, nz + 1, l) - node(context, nx, nz - 1, l));
            }
            gx *= 128f / (2 * LATTICE);
            gz *= 128f / (2 * LATTICE);
            float h = 128f * (1f + centre);
            float[] scratch = l.scratch;
            Erosion.erode(x, z, gx, gz, AlpineSurface.erosionStrength(h), 0f, scratch);
            // as a change to the offset, so untouched ground keeps its exact value
            return centre + (summit(h + scratch[0]) - h) / 128f;
        }

        private float node(SamplerContext context, int i, int j, Lattice l) {
            long key = (long) i << 32 | (j & 0xFFFFFFFFL);
            int slot = (int) (key * 0x9E3779B97F4A7C15L >>> 52);
            if (l.keys[slot] == key && l.known[slot]) return l.values[slot];
            float v = input.sampleValue(context, i * LATTICE, 0, j * LATTICE);
            l.keys[slot] = key;
            l.values[slot] = v;
            l.known[slot] = true;
            return v;
        }
    }

    /** A direct-mapped cache of lattice nodes (4096 slots: plenty for the chunks one thread generates around it). */
    private static final class Lattice {
        final long[] keys = new long[4096];
        final float[] values = new float[4096];
        final boolean[] known = new boolean[4096];
        final float[] scratch = new float[Erosion.OUT];
    }
}
