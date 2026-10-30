package dev.awaken.lens;

import dev.awaken.Awaken;
import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * The shipped rain stays neutral and restrained at the game's native weather texture footprint; the heightfield sees a
 * roof the game's heightmaps skip.
 */
final class LensTests {
    private LensTests() {}

    static void register() {
        AwakenTests.register("lens_heights_skipped_roof", 20, LensTests::skippedRoof);
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
                rainSplash(h);
                h.succeed();
            } catch (java.io.IOException ex) {
                h.fail("rain texture did not decode: " + ex.getMessage());
            }
        });
    }

    /**
     * Rain lands in the streaks' grey (SPEC §22): the rain particle names our splash sprites, each a few opaque neutral
     * pixels (the particle's layer is opaque), while the water splash keeps vanilla's.
     */
    private static void rainSplash(GameTestHelper h) throws java.io.IOException {
        String rain;
        try (var in = LensTests.class.getResourceAsStream("/assets/minecraft/particles/rain.json")) {
            h.assertTrue(in != null, "the rain particle's definition is packaged");
            rain = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        h.assertTrue(!rain.contains("minecraft:splash"), "rain no longer lands in the water splash's blue sprites");
        for (int i = 0; i < 4; i++) {
            h.assertTrue(rain.contains("\"awaken:rain_splash_" + i + "\""), "rain names splash sprite " + i);
            try (var in = LensTests.class.getResourceAsStream("/assets/awaken/textures/particle/rain_splash_" + i + ".png")) {
                h.assertTrue(in != null, "splash sprite " + i + " is packaged");
                var image = javax.imageio.ImageIO.read(in);
                h.assertTrue(image != null && image.getWidth() == 8 && image.getHeight() == 8, "splash " + i + " is 8 by 8");
                int visible = 0;
                for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                    int rgba = image.getRGB(x, y), a = rgba >>> 24;
                    if (a == 0) continue;
                    int r = rgba >> 16 & 255, g = rgba >> 8 & 255, b = rgba & 255;
                    h.assertTrue(a == 255, "splash " + i + " is fully on or off for the opaque layer");
                    h.assertTrue(r == g && g == b && r >= 150 && r <= 215, "splash " + i + " is the rain's neutral grey");
                    visible++;
                }
                h.assertTrue(visible >= 1 && visible <= 4, "splash " + i + " is a few drops: " + visible);
            }
        }
    }

    /**
     * Lens 2's heightfield sees a roof the game's heightmaps skip (SPEC §22): a mod block outside
     * #minecraft:blocks_motion (Carillon's clay tiles) covers its column, under leaves too; a stone-brick roof and a
     * torch read exactly as the heightmaps say.
     */
    private static void skippedRoof(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos o = h.absolutePos(new BlockPos(0, 2, 0));
        for (int dx = 0; dx < 5; dx++) for (int dy = 0; dy < 10; dy++) for (int dz = 0; dz < 5; dz++) {
            level.setBlock(o.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        Block tiles = BuiltInRegistries.BLOCK.getValue(Awaken.id("clay_tiles"));
        h.assertTrue(tiles != Blocks.AIR, "Carillon's clay tiles are registered");
        h.assertTrue(LensHeights.skipped(tiles.defaultBlockState()), "clay tiles stand solid outside the motion tag");
        h.assertFalse(LensHeights.skipped(Blocks.STONE_BRICKS.defaultBlockState()), "the heightmaps count stone bricks themselves");
        h.assertFalse(LensHeights.skipped(Blocks.TORCH.defaultBlockState()), "a torch is no roof");
        for (int dx = 1; dx <= 3; dx += 2) for (int dz = 1; dz <= 3; dz += 2) {
            level.setBlock(o.offset(dx, 0, dz), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        level.setBlock(o.offset(1, 3, 1), tiles.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(o.offset(3, 3, 1), Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(o.offset(1, 1, 3), Blocks.TORCH.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(o.offset(3, 3, 3), tiles.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(o.offset(3, 4, 3), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true), Block.UPDATE_CLIENTS);
        int y = o.getY();
        int[] t = tops(level, o.offset(1, 0, 1));
        h.assertValueEqual(t[1], y + 4, "the clay-tile roof covers its column");
        h.assertValueEqual(t[0], y + 4, "and blocks motion there");
        t = tops(level, o.offset(3, 0, 1));
        h.assertValueEqual(t[1], y + 4, "a stone-brick roof as the heightmap has it");
        t = tops(level, o.offset(1, 0, 3));
        h.assertValueEqual(t[1], y + 1, "a torch on the floor leaves the floor the top");
        t = tops(level, o.offset(3, 0, 3));
        h.assertValueEqual(t[0], y + 5, "leaves over the tiles top everything that blocks motion");
        h.assertValueEqual(t[1], y + 4, "and the tiles are the roof under them");
        h.succeed();
    }

    private static int[] tops(ServerLevel level, BlockPos at) {
        LevelChunk ch = level.getChunkAt(at);
        boolean[] has = new boolean[ch.getSectionsCount()];
        int[] out = new int[2];
        LensHeights.sections(ch, has);
        LensHeights.column(ch, has, at.getX() & 15, at.getZ() & 15, out);
        return out;
    }
}
