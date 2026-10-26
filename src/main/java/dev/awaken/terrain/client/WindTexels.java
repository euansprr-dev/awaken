package dev.awaken.terrain.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import dev.awaken.Awaken;
import dev.awaken.terrain.SwayMath;
import dev.awaken.terrain.Wind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The wind, handed to Awaken's terrain shader through {@link SwayMath#TEXELS} texels in the block atlas's bottom-right
 * corner (Sodium binds only its own uniforms, the atlas and the lightmap; the atlas is the one thing Awaken can write
 * that the shader can read). The stitcher fills the atlas from the top left, so that corner lies outside every sprite;
 * it is checked at each stitch and nothing is written if a sprite is there. Written once a frame, before terrain draws.
 * The layout and its decoding are {@link SwayMath}'s: the sway clock ({@link ClientWind#animSeconds}, real frame time,
 * whole turns in 2048 s), the camera (x and z modulo 4096), wind direction, steady strength and a 64-sample gust
 * profile along the wind on a grid fixed in the world (all 16-bit), the plants' amplitudes, the player's feet.
 *
 * <p>The shader rounds (region offset + camera) to whole render regions to find each region's world position modulo
 * 4096, and reads the gust passing each leaf and plant from the profile, so the canopy and the meadow sway with the same
 * fronts {@link Wind} gives the falling leaves and the rustle. Seven fetches per leaf vertex and nine per plant vertex
 * within 112 blocks (four more right by the camera), none for any other vertex.
 */
final class WindTexels {
    private WindTexels() {}

    static final int TEXELS = SwayMath.TEXELS;
    /** Full sway at strength 1, in blocks (a few hundredths in a breeze). */
    private static final float AMPLITUDE = 0.11F;
    /** Plants, per unit of weight (a short grass tip has 0.75, a tall grass tip 2): blocks of sway in air and in water,
     * the brightening of grass tips at a full gust, and how far the feet push tips aside. */
    static float plantAmplitude = 0.2F, waterAmplitude = 0.16F, sheen = 0.1F, push = 0.32F;

    private static final ByteBuffer BUF = MemoryUtil.memAlloc(TEXELS * 4).order(ByteOrder.LITTLE_ENDIAN);
    private static final SwayMath.Frame FRAME = new SwayMath.Frame();
    private static GpuTexture cornerOf; // the atlas texture whose corner was found free
    private static boolean cornerFree;
    /** Measuring aid ({@code /awaken terrain waving debug}): 0 normal, 1 no signature (shader exits early), 2 amplitude 0,
     * 3 plants still (their amplitudes 0, so the shader skips their sway; leaves wave on). */
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

        SwayMath.Frame f = FRAME;
        f.signature = debugMode != 1;
        f.t = (float) ClientWind.animSeconds();
        f.camX = (float) SwayMath.wrap4096(cam.x);
        f.camY = (float) cam.y;
        f.camZ = (float) SwayMath.wrap4096(cam.z);
        f.dirX = wind.dirX;
        f.dirZ = wind.dirZ;
        f.steady = wind.steady;
        f.leafAmp = debugMode == 2 ? 0F : AMPLITUDE;
        f.profileOff = SwayMath.profileOffset(cam.x, cam.z, wind.dirX, wind.dirZ);
        for (int i = 0; i < SwayMath.PROFILE; i++) {
            float along = SwayMath.sampleAlong(i, f.profileOff);
            f.gust[i] = wind.gust(cam.x + wind.dirX * along, cam.z + wind.dirZ * along);
        }
        boolean still = debugMode == 2 || debugMode == 3;
        f.air = still ? 0F : plantAmplitude;
        f.water = still ? 0F : waterAmplitude;
        f.sheen = sheen;
        f.weather = mc.level != null && mc.level.dimensionType().hasSkyLight() ? 1F : 0F;
        Player p = mc.player;
        if (p != null && !p.isSpectator() && p.level() == mc.level) {
            Vec3 feet = p.getPosition(partialTick);
            f.feetX = (float) SwayMath.wrap4096(feet.x);
            f.feetY = (float) feet.y;
            f.feetZ = (float) SwayMath.wrap4096(feet.z);
        } else {
            f.feetX = 0F;
            f.feetY = -1.0E6F;
            f.feetZ = 0F;
        }
        f.push = still ? 0F : push;

        ByteBuffer b = BUF;
        b.clear();
        SwayMath.encode(f, b);
        b.flip();
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(tex, b, 0, 0, tex.getWidth(0) - TEXELS, tex.getHeight(0) - 1, TEXELS, 1);
        SwayProbe.frame(b, cam);
    }
}
