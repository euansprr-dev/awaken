package dev.awaken.terrain.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import dev.awaken.Awaken;
import dev.awaken.terrain.Wind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The wind, handed to Awaken's terrain shader through 27 texels in the block atlas's bottom-right corner (Sodium binds
 * only its own uniforms, the atlas and the lightmap; the atlas is the one thing Awaken can write that the shader can
 * read). The stitcher fills the atlas from the top left, so that corner lies outside every sprite; it is checked at
 * each stitch and nothing is written if a sprite is there. Written once a frame, before terrain draws.
 *
 * <pre>
 * texel 0      "AWND" signature
 * texel 1      time in seconds modulo 2048 (float bits)
 * texels 2..4  camera x, y, z (x and z modulo 4096; float bits)
 * texel 5      wind direction x, z (-1..1 as 0..1), steady strength, amplitude (x 0.25 blocks)
 * texels 6..21 the gust along the wind through the camera: 64 samples 4 blocks apart, 4 to a texel, sample i at
 *              (i - 31.5) x 4 blocks downwind of the camera
 * texel 22     plants: amplitude in air and under water (x 0.5 blocks), sheen (x 0.5), push by the feet (x 1 block)
 * texels 23..25 the player's feet x, y, z (x and z modulo 4096; y far below the world when there is no one to push)
 * texel 26     weather wind here (1, or 0 in a world without sky light: plants feel only a draught), 3 spare bytes
 * </pre>
 * The shader rounds (region offset + camera) to whole render regions to find each region's world position modulo 4096,
 * and reads the gust passing each leaf and plant from the profile, so the canopy and the meadow sway with the same
 * fronts {@link Wind} gives the falling leaves and the rustle. Seven fetches per leaf vertex and about a dozen per plant
 * vertex, none for any other vertex.
 */
final class WindTexels {
    private WindTexels() {}

    static final int TEXELS = 27;
    static final int PROFILE = 64;
    static final float PROFILE_STEP = 4F;
    private static final int SIGNATURE = 0x444E5741; // "AWND" little-endian
    /** Full sway at strength 1, in blocks (a few hundredths in a breeze). */
    private static final float AMPLITUDE = 0.11F;
    /** Plants, per unit of weight (a short grass tip has 0.75, a tall grass tip 2): blocks of sway in air and in water,
     * the brightening of grass tips at a full gust, and how far the feet push tips aside. */
    static float plantAmplitude = 0.2F, waterAmplitude = 0.16F, sheen = 0.1F, push = 0.32F;

    private static final ByteBuffer BUF = MemoryUtil.memAlloc(TEXELS * 4).order(ByteOrder.LITTLE_ENDIAN);
    private static GpuTexture cornerOf; // the atlas texture whose corner was found free
    private static boolean cornerFree;
    /** Measuring aid ({@code /awaken terrain waving debug}): 0 normal, 1 no signature (shader exits early), 2 amplitude 0. */
    static int debugMode;

    /** At each block-atlas stitch: is the corner strip outside every sprite? */
    static void onBlockAtlasStitched(TextureAtlas atlas) {
        GpuTexture tex = atlas.getTexture();
        int w = tex.getWidth(0), h = tex.getHeight(0);
        int x0 = w - TEXELS, y0 = h - 1;
        boolean free = true;
        for (TextureAtlasSprite s : atlas.getTextures().values()) {
            int pad = Math.round(s.getU0() * w) - s.getX(); // sprites carry a border for filtering
            int x1 = s.getX() + s.contents().width() + 2 * pad, y1 = s.getY() + s.contents().height() + 2 * pad;
            if (x1 > x0 && y1 > y0) {
                free = false;
                break;
            }
        }
        cornerOf = tex;
        cornerFree = free;
        if (free) Awaken.LOG.info("Waving leaves: wind texels at {},{} of the {}x{} block atlas", x0, y0, w, h);
        else Awaken.LOG.warn("Waving leaves: the block atlas's corner holds a sprite; leaves will not wave this session");
    }

    static boolean cornerFree() {
        return cornerFree;
    }

    /** Writes this frame's wind (from {@link ClientWind}). Render thread, before the level draws. */
    static void write(Minecraft mc, float partialTick) {
        Wind.Field wind = ClientWind.field();
        if (wind == null || !cornerFree) return;
        GpuTexture tex = mc.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTexture();
        if (tex != cornerOf) return; // replaced by a reload whose stitch we have not seen yet
        Vec3 cam = mc.gameRenderer.mainCamera().position();

        ByteBuffer b = BUF;
        b.clear();
        b.putInt(debugMode == 1 ? 0 : SIGNATURE);
        b.putFloat((float) (wind.seconds - Math.floor(wind.seconds / 2048.0) * 2048.0));
        b.putFloat((float) wrap(cam.x)).putFloat((float) cam.y).putFloat((float) wrap(cam.z));
        b.put(snorm(wind.dirX)).put(snorm(wind.dirZ)).put(unorm(wind.steady)).put(debugMode == 2 ? 0 : unorm(AMPLITUDE / 0.25F));
        for (int i = 0; i < PROFILE; i++) {
            float along = (i - (PROFILE - 1) * 0.5F) * PROFILE_STEP;
            b.put(unorm(wind.gust(cam.x + wind.dirX * along, cam.z + wind.dirZ * along)));
        }
        boolean still = debugMode == 2;
        b.put(still ? 0 : unorm(plantAmplitude / 0.5F)).put(still ? 0 : unorm(waterAmplitude / 0.5F)).put(unorm(sheen / 0.5F)).put(still ? 0 : unorm(push));
        Player p = mc.player;
        if (p != null && !p.isSpectator() && p.level() == mc.level) {
            Vec3 feet = p.getPosition(partialTick);
            b.putFloat((float) wrap(feet.x)).putFloat((float) feet.y).putFloat((float) wrap(feet.z));
        } else {
            b.putFloat(0F).putFloat(-1.0E6F).putFloat(0F);
        }
        b.put(mc.level != null && mc.level.dimensionType().hasSkyLight() ? (byte) 255 : 0).put((byte) 0).put((byte) 0).put((byte) 0);
        b.flip();
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(tex, b, 0, 0, tex.getWidth(0) - TEXELS, tex.getHeight(0) - 1, TEXELS, 1);
    }

    private static double wrap(double v) {
        return v - Math.floor(v / 4096.0) * 4096.0;
    }

    private static byte unorm(float v) {
        return (byte) Math.round(Mth.clamp(v, 0.0F, 1.0F) * 255.0F);
    }

    private static byte snorm(float v) {
        return unorm(v * 0.5F + 0.5F);
    }
}
