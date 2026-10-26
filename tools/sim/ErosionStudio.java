import dev.awaken.terrain.alpine.Erosion;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Offline look at Alpine erosion (dev.awaken.terrain.alpine.Erosion) on a synthetic mountain range, without the game:
 * base terrain on the left, eroded on the right, hill-shaded with snow, rock and forest. Far Sight 2, P1.
 * Run: javac -d build/erosion src/main/java/dev/awaken/terrain/alpine/Erosion.java &&
 *      java -cp build/erosion tools/sim/ErosionStudio.java out.png [blocksPerPixel]
 */
public class ErosionStudio {
    static final int N = 640;

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "erosion.png";
        float bpp = args.length > 1 ? Float.parseFloat(args[1]) : 4f;
        float[][] base = new float[N][N], eroded = new float[N][N];
        float[] o = new float[Erosion.OUT];
        for (int j = 0; j < N; j++) {
            for (int i = 0; i < N; i++) {
                float x = i * bpp, z = j * bpp;
                float h = height(x, z);
                float e = 24f;
                float gx = (height(x + e, z) - height(x - e, z)) / (2 * e), gz = (height(x, z + e) - height(x, z - e)) / (2 * e);
                base[j][i] = h;
                float strength = Erosion.smooth(66f, 120f, h);
                Erosion.erode(x, z, gx, gz, strength, 0f, o);
                eroded[j][i] = h + o[0];
            }
        }
        BufferedImage img = new BufferedImage(N * 2, N + VIEW_H, BufferedImage.TYPE_INT_RGB);
        paint(img, base, 0, bpp);
        paint(img, eroded, N, bpp);
        view(img, base, 0, bpp);
        view(img, eroded, N, bpp);
        ImageIO.write(img, "png", new File(out));
        System.out.println("wrote " + out);
    }

    // a range: ridged noise along a band, foothills, a valley floor near 70
    static float height(float x, float z) {
        float ridge = 0f, amp = 1f, f = 1f / 900f, sum = 0f;
        for (int k = 0; k < 4; k++) {
            float n = 1f - Math.abs(noise(x * f, z * f, k) * 2f - 1f);
            ridge += n * n * amp;
            sum += amp;
            amp *= 0.5f;
            f *= 2.1f;
        }
        ridge /= sum;
        float band = noise(x / 2600f, z / 2600f, 9);
        float m = Erosion.smooth(0.35f, 0.7f, band);
        return 70f + 40f * noise(x / 500f, z / 500f, 7) + m * 230f * ridge;
    }

    // gradient (Perlin) noise in 0..1
    static float noise(float x, float z, int seed) {
        int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
        float fx = x - xi, fz = z - zi;
        float u = fx * fx * fx * (fx * (fx * 6 - 15) + 10), v = fz * fz * fz * (fz * (fz * 6 - 15) + 10);
        float a = grad(xi, zi, seed, fx, fz), b = grad(xi + 1, zi, seed, fx - 1, fz);
        float c = grad(xi, zi + 1, seed, fx, fz - 1), d = grad(xi + 1, zi + 1, seed, fx - 1, fz - 1);
        float n = a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
        return Math.clamp(n * 0.7f + 0.5f, 0f, 1f);
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

    static final int VIEW_H = 360;

    // a low perspective view across the map from its south edge, looking north: ray-marched, hazed, sunlit
    static void view(BufferedImage img, float[][] h, int ox, float bpp) {
        float ex = N * bpp * 0.5f, ez = N * bpp - 10f * bpp;
        float ey = h[N - 11][N / 2] + 90f;
        for (int py = 0; py < VIEW_H; py++) {
            for (int px = 0; px < N; px++) {
                float dx = (px - N / 2f) / (N / 2f) * 0.9f, dy = (VIEW_H / 2f - py) / (N / 2f) * 0.9f - 0.12f, dz = -1f;
                float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                dx /= l; dy /= l; dz /= l;
                float t = 1f, hit = -1f;
                int col = 0;
                while (t < N * bpp * 1.1f) {
                    float x = ex + dx * t, y = ey + dy * t, z = ez + dz * t;
                    int i = (int) (x / bpp), j = (int) (z / bpp);
                    if (i < 1 || j < 1 || i >= N - 1 || j >= N - 1) break;
                    if (y < h[j][i]) { hit = t; col = shade(h, i, j, bpp); break; }
                    t += Math.max(1f, t * 0.004f);
                }
                int sky = 0xAFC8E6;
                int c;
                if (hit < 0) c = sky;
                else {
                    float fog = 1f - (float) Math.exp(-hit / 2600f);
                    c = mixRgb(col, 0xB4C8E0, fog);
                }
                img.setRGB(ox + px, N + py, c);
            }
        }
    }

    static int mixRgb(int a, int b, float t) {
        int r = (int) ((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t), g = (int) ((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t),
                bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    static int shade(float[][] h, int i, int j, float bpp) {
        float dx = (h[j][i + 1] - h[j][i - 1]) / (2 * bpp), dz = (h[j + 1][i] - h[j - 1][i]) / (2 * bpp);
        float nx = -dx, ny = 1f, nz = -dz;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        float lx = -0.75f, ly = 0.45f, lz = -0.35f;
        float ll = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        float lam = Math.max(0f, (nx * lx + ny * ly + nz * lz) / (len * ll));
        float slope = (float) Math.sqrt(dx * dx + dz * dz);
        float y = h[j][i];
        float r, g, b;
        boolean north = dz < 0;
        float snowLine = 215f - (north ? 18f : 0f);
        if (y > snowLine && slope < 1.1f) { r = 0.92f; g = 0.94f; b = 0.97f; }
        else if (slope > 0.9f || y > 245f) { r = 0.50f; g = 0.48f; b = 0.46f; }
        else if (y > 180f) { r = 0.42f; g = 0.47f; b = 0.32f; }
        else { r = 0.16f; g = 0.30f; b = 0.17f; }
        float l = 0.25f + 0.85f * lam;
        int R = (int) Math.min(255, r * l * 255), G = (int) Math.min(255, g * l * 255), B = (int) Math.min(255, b * l * 255);
        return R << 16 | G << 8 | B;
    }

    static void paint(BufferedImage img, float[][] h, int ox, float bpp) {
        for (int j = 1; j < N - 1; j++) {
            for (int i = 1; i < N - 1; i++) {
                float dx = (h[j][i + 1] - h[j][i - 1]) / (2 * bpp), dz = (h[j + 1][i] - h[j - 1][i]) / (2 * bpp);
                float nx = -dx, ny = 1f, nz = -dz;
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                // low sun from the west-north-west
                float lx = -0.75f, ly = 0.45f, lz = -0.35f;
                float ll = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
                float lam = Math.max(0f, (nx * lx + ny * ly + nz * lz) / (len * ll));
                float slope = (float) Math.sqrt(dx * dx + dz * dz);
                float y = h[j][i];
                float r, g, b;
                boolean north = dz < 0;
                float snowLine = 215f - (north ? 18f : 0f);
                if (y > snowLine && slope < 1.1f) { r = 0.92f; g = 0.94f; b = 0.97f; }
                else if (slope > 0.9f || y > 245f) { r = 0.50f; g = 0.48f; b = 0.46f; }
                else if (y > 180f) { r = 0.42f; g = 0.47f; b = 0.32f; }
                else { r = 0.16f; g = 0.30f; b = 0.17f; }
                float l = 0.25f + 0.85f * lam;
                int R = (int) Math.min(255, r * l * 255), G = (int) Math.min(255, g * l * 255), B = (int) Math.min(255, b * l * 255);
                img.setRGB(ox + i, j, R << 16 | G << 8 | B);
            }
        }
    }
}
