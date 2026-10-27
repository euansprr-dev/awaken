import dev.awaken.terrain.alpine.Erosion;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;

/**
 * A proof for docs/terrain-immersion/PLAN.md (no production code): the "soft steps" pass on land shaped the way AWAKEN's
 * is (Alpine's real {@link Erosion} on synthetic hills), measuring what it does to the one-block risers and what it costs.
 *
 * <p>The rule, per column of the natural height field h (the y of the top ground block, as the generator's WG heightmap
 * holds it): the column takes a half-block slab on top of its ground when some neighbour (the four cardinal ones) is
 * exactly one higher, none is lower, and none of the eight is two or more higher (a cliff stays a cliff). Only ever adds
 * a slab; never removes a block; the decision reads heights only, so it is pure across chunk borders.
 *
 * <p>Run: javac -d /tmp/softbench src/main/java/dev/awaken/terrain/alpine/Erosion.java docs/terrain-immersion/proof/SoftBench.java
 *      java -cp /tmp/softbench SoftBench docs/terrain-immersion/proof/out
 */
public class SoftBench {
    static final int N = 512;
    /** The finest erosion cell the land keeps (blocks): 16 is what the game does; the study below tries 32 and 64. */
    static float finest = 16f;

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "out");
        out.mkdirs();
        String[] names = {"meadow", "hillside", "mountain", "massif"};
        System.out.println("scene      | slope p50/p90 | columns raised | 1-block risers before -> after | half risers after | blocks of riser face before -> after");
        for (String name : names) {
            int[] h = land(name);
            byte[] raise = new byte[N * N];
            soft(h, raise);
            report(name, h, raise);
            try (DataOutputStream o = new DataOutputStream(new FileOutputStream(new File(out, name + ".bin")))) {
                o.writeInt(N);
                for (int v : h) o.writeInt(v);
                o.write(raise);
            }
        }
        // does the erosion's finest octave make the one-block jitter? (the octaves are 256, 128, 64, 32, 16 blocks wide)
        System.out.println();
        System.out.println("finest erosion cell | scene    | mean |step| between neighbours | one-block risers | after the soft pass");
        for (float f : new float[]{16f, 32f, 64f}) {
            finest = f;
            for (String name : new String[]{"hillside", "massif"}) {
                int[] hh = land(name);
                byte[] rr = new byte[N * N];
                soft(hh, rr);
                long sum = 0, ones = 0, onesAfter = 0, n = 0;
                for (int z = 1; z < N - 2; z++) {
                    for (int x = 1; x < N - 2; x++) {
                        int i = z * N + x;
                        for (int j : new int[]{i + 1, i + N}) {
                            int d = Math.abs(hh[i] - hh[j]);
                            sum += d;
                            n++;
                            if (d == 1) ones++;
                            if (Math.abs((2 * hh[i] + rr[i]) - (2 * hh[j] + rr[j])) == 2) onesAfter++;
                        }
                    }
                }
                System.out.printf("%-19.0f | %-8s | %.3f                         | %d (%.1f%% of pairs) | %d%n", f, name, sum / (double) n, ones, 100.0 * ones / n, onesAfter);
            }
        }
        finest = 16f;
        // cost: the pass over every column of a 512 x 512 land, as 16 x 16 chunks, after warm-up
        int[] h = land("hillside");
        byte[] raise = new byte[N * N];
        for (int i = 0; i < 300; i++) soft(h, raise);
        int reps = 2000;
        long t0 = System.nanoTime();
        int sink = 0;
        for (int i = 0; i < reps; i++) sink += soft(h, raise);
        long dt = System.nanoTime() - t0;
        double perColumn = dt / (double) reps / (N * N);
        System.out.printf("cost: %.1f ns a column, %.1f us a chunk (256 columns), heights only; %d raised%n", perColumn, perColumn * 256 / 1000, sink);
    }

    /** The pass over the whole field; returns how many columns it raised. A one-column margin is left alone. */
    static int soft(int[] h, byte[] raise) {
        int n = 0;
        for (int z = 1; z < N - 1; z++) {
            for (int x = 1; x < N - 1; x++) {
                int i = z * N + x, c = h[i];
                int e = h[i + 1] - c, w = h[i - 1] - c, s = h[i + N] - c, nn = h[i - N] - c;
                byte r = 0;
                if (e >= 0 && w >= 0 && s >= 0 && nn >= 0 && (e == 1 | w == 1 | s == 1 | nn == 1)
                        && e <= 1 && w <= 1 && s <= 1 && nn <= 1
                        && h[i + N + 1] - c <= 1 && h[i + N - 1] - c <= 1 && h[i - N + 1] - c <= 1 && h[i - N - 1] - c <= 1) {
                    r = 1;
                    n++;
                }
                raise[i] = r;
            }
        }
        return n;
    }

    static void report(String name, int[] h, byte[] raise) {
        int risers1 = 0, risers1After = 0, halfAfter = 0, tall = 0, tallAfter = 0;
        double faceBefore = 0, faceAfter = 0;
        int[] slopes = new int[(N - 2) * (N - 2)];
        int k = 0;
        for (int z = 1; z < N - 1; z++) {
            for (int x = 1; x < N - 1; x++) {
                int i = z * N + x;
                double gx = (h[i + 1] - h[i - 1]) / 2.0, gz = (h[i + N] - h[i - N]) / 2.0;
                slopes[k++] = (int) (Math.sqrt(gx * gx + gz * gz) * 100);
                // each column against its east and south neighbour: the exposed face between them
                for (int q = 0; q < 2; q++) {
                    int j = q == 0 ? i + 1 : i + N;
                    if (q == 0 ? x + 1 >= N - 1 : z + 1 >= N - 1) continue;
                    int d2 = Math.abs((2 * h[i] + raise[i]) - (2 * h[j] + raise[j]));
                    int d0 = Math.abs(h[i] - h[j]);
                    if (d0 == 1) risers1++;
                    if (d0 >= 2) tall++;
                    faceBefore += d0;
                    faceAfter += d2 / 2.0;
                    if (d2 == 2) risers1After++;
                    if (d2 == 1) halfAfter++;
                    if (d2 >= 4) tallAfter++;
                }
            }
        }
        java.util.Arrays.sort(slopes);
        int raised = 0;
        for (byte b : raise) raised += b;
        System.out.printf("%-10s | %.2f / %.2f   | %5.1f%%         | %7d -> %7d (%+.0f%%)        | %7d          | %.0f -> %.0f%n", name,
                slopes[slopes.length / 2] / 100.0, slopes[slopes.length * 9 / 10] / 100.0, 100.0 * raised / (N * N),
                risers1, risers1After, 100.0 * (risers1After - risers1) / risers1, halfAfter, faceBefore, faceAfter);
        System.out.printf("             (faces two blocks or taller: %d before, %d after: cliffs are left alone)%n", tall, tallAfter);
    }

    // ---- land: AWAKEN's own erosion on synthetic ground ---------------------------------------------------------------

    /** Integer ground height of a synthetic land, on a 1-block lattice. */
    static int[] land(String name) {
        int[] h = new int[N * N];
        float[] o = new float[Erosion.OUT];
        for (int z = 0; z < N; z++) {
            for (int x = 0; x < N; x++) {
                float fx = x, fz = z;
                float y = base(name, fx, fz);
                float e = 6f;
                float gx = (base(name, fx + e, fz) - base(name, fx - e, fz)) / (2 * e), gz = (base(name, fx, fz + e) - base(name, fx, fz - e)) / (2 * e);
                float strength = name.equals("mountain") || name.equals("massif") ? 1f : name.equals("hillside") ? 0.18f : 0.06f;
                Erosion.erode(fx, fz, gx, gz, strength, finest, o);
                h[z * N + x] = (int) Math.floor(y + o[0]);
            }
        }
        return h;
    }

    static float base(String name, float x, float z) {
        switch (name) {
            case "meadow": // rolling grassland: 3-15 degrees
                return 80f + 9f * noise(x / 90f, z / 90f, 1) + 3.5f * noise(x / 30f, z / 30f, 2) + 1.2f * noise(x / 11f, z / 11f, 3);
            case "hillside": // a long valley side: about 25-35 degrees, rolling across it
                return 70f + z * 0.5f + 14f * noise(x / 120f, z / 120f, 4) + 4f * noise(x / 26f, z / 26f, 5) + 1.5f * noise(x / 10f, z / 10f, 6);
            case "massif": // a mountain rising from foothills: 20 degrees at its foot to 60 above, cut by the real erosion
                return 60f + 0.0016f * z * z + 14f * noise(x / 120f, z / 120f, 12) + 6f * noise(x / 38f, z / 38f, 13);
            default: // a mountain flank: 40-60 degrees
                return 120f + z * 1.1f + 30f * noise(x / 160f, z / 160f, 7) + 8f * noise(x / 40f, z / 40f, 8);
        }
    }

    static float noise(float x, float z, int seed) {
        int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
        float fx = x - xi, fz = z - zi;
        float u = fx * fx * fx * (fx * (fx * 6 - 15) + 10), v = fz * fz * fz * (fz * (fz * 6 - 15) + 10);
        float a = grad(xi, zi, seed, fx, fz), b = grad(xi + 1, zi, seed, fx - 1, fz);
        float c = grad(xi, zi + 1, seed, fx, fz - 1), d = grad(xi + 1, zi + 1, seed, fx - 1, fz - 1);
        float n = a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
        return n * 1.4f; // about -1..1
    }

    static float grad(int x, int z, int s, float dx, float dz) {
        float ang = r(x, z, s) * 6.2831855f;
        return (float) (Math.cos(ang) * dx + Math.sin(ang) * dz);
    }

    static float r(int x, int z, int s) {
        int h = x * 374761393 + z * 668265263 + s * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFFFF) / 16777216f;
    }
}
