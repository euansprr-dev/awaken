package dev.awaken.lens;

import dev.awaken.core.test.AwakenTests;

/** The shipped rain stays neutral and restrained at the game's native weather texture footprint. */
final class LensTests {
    private LensTests() {}

    static void register() {
        AwakenTests.register("lens_rain_texture", 20, h -> {
            try (var stream = LensTests.class.getResourceAsStream("/assets/minecraft/textures/environment/rain.png")) {
                h.assertTrue(stream != null, "the weather texture is packaged at vanilla's rain path");
                var image = javax.imageio.ImageIO.read(stream);
                h.assertTrue(image != null && image.getWidth() == 64 && image.getHeight() == 256,
                        "rain keeps the existing 64 by 256 texture footprint and UV scale");
                long alpha = 0;
                int visible = 0, peak = 0, maxRow = 0;
                for (int y = 0; y < image.getHeight(); y++) {
                    int row = 0;
                    for (int x = 0; x < image.getWidth(); x++) {
                        int rgba = image.getRGB(x, y), a = rgba >>> 24;
                        if (a == 0) continue;
                        int r = rgba >> 16 & 255, g = rgba >> 8 & 255, b = rgba & 255;
                        h.assertTrue(r == g && g == b, "rain has no painted blue cast");
                        h.assertTrue(r >= 180 && r <= 225, "silver grey rather than emissive white");
                        visible++; row++; alpha += a; peak = Math.max(peak, a);
                    }
                    maxRow = Math.max(maxRow, row);
                }
                h.assertTrue(visible >= 200 && visible <= 600, "sparse streaks, no continuous rain veil: " + visible);
                h.assertTrue(peak >= 80 && peak <= 145, "soft opacity without opaque bands: " + peak);
                double mean = alpha / (64.0 * 256);
                h.assertTrue(mean >= 0.5 && mean <= 2.5, "restrained total opacity: " + mean);
                h.assertTrue(maxRow <= 6, "individual thin drops, no horizontal band: " + maxRow);
                h.succeed();
            } catch (java.io.IOException ex) {
                h.fail("rain texture did not decode: " + ex.getMessage());
            }
        });
    }
}
