package dev.awaken.lens.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * Lens 2's per-frame values that need full float precision (SPEC §22): the frame's exact view-projection (the
 * game's projection with its view bob, hurt tilt and nausea, times the view rotation; camera-relative), its inverse,
 * the previous frame's (for temporal accumulation), the camera within the heightfield's ring and the light's direction.
 * A 24×1 RGBA32F texture ({@code awaken:lens_frame}), written once a frame just before the post effects run; layout in
 * {@code shaders/include/lens_frame.glsl}. Nothing is allocated per frame.
 */
final class LensFrame {
    static final int WIDTH = 24;
    final Texture texture = new Texture();
    private final ByteBuffer data = MemoryUtil.memAlloc(WIDTH * 16);
    private final Matrix4f proj = new Matrix4f(), bob = new Matrix4f(), viewProj = new Matrix4f(), inverse = new Matrix4f(),
            prevViewProj = new Matrix4f();
    private final Vector3f spinAxis = new Vector3f();
    private double prevX, prevY, prevZ;
    private long prevNs;
    private int frame;
    private boolean hasPrev;

    /** Forget the previous frame (a new chain, a new place): the next frame starts its history fresh. */
    void reset() {
        hasPrev = false;
    }

    /**
     * Writes and uploads this frame's values. {@code lightX..Z} points toward the sun (or the moon at night),
     * {@code light} 0..1 how much it lights the world (0: no sun model here), {@code moon} whether it's the moon.
     */
    void write(CameraRenderState cam, PlayerRenderState player, boolean zeroToOne, float lightX, float lightY, float lightZ,
            float light, boolean moon, float rain, LensHeightfield height) {
        Minecraft mc = Minecraft.getInstance();
        // the projection GameRenderer.renderLevel draws the world with: its bob and hurt tilt, then the nausea warp
        bob.identity();
        bobHurt(cam, mc.options.damageTiltStrength().get().floatValue());
        if (mc.options.bobView().get()) bobView(cam);
        proj.set(cam.projectionMatrix).mul(bob);
        float screenEffect = mc.options.screenEffectScale().get().floatValue();
        float spin = Math.max(player.portalEffectIntensity, player.nauseaEffectIntensity) * (screenEffect * screenEffect);
        if (spin > 0f) {
            float skew = 5.0F / (spin * spin + 5.0F) - spin * 0.04F;
            skew *= skew;
            spinAxis.set(0.0F, Mth.SQRT_OF_TWO / 2.0F, Mth.SQRT_OF_TWO / 2.0F);
            float angle = player.spinningEffectAngle * (float) (Math.PI / 180.0);
            proj.rotate(angle, spinAxis).scale(1.0F / skew, 1.0F, 1.0F).rotate(-angle, spinAxis);
        }
        viewProj.set(proj).mul(cam.viewRotationMatrix);
        viewProj.invert(inverse);

        double x = cam.pos.x, y = cam.pos.y, z = cam.pos.z;
        long now = System.nanoTime();
        // history holds across ordinary frames; not across a teleport, a long gap or a fresh start
        boolean valid = hasPrev && now - prevNs < 250_000_000L && Math.abs(x - prevX) + Math.abs(y - prevY) + Math.abs(z - prevZ) < 24.0;
        if (!valid) prevViewProj.set(viewProj);

        data.clear();
        putMatrix(0, inverse);
        putMatrix(4, viewProj);
        putMatrix(8, prevViewProj);
        float ring = LensHeightfield.SIZE;
        put(12, (float) (x - Math.floor(x / ring) * ring), (float) y, (float) (z - Math.floor(z / ring) * ring), frame);
        put(13, valid ? (float) (x - prevX) : 0f, valid ? (float) (y - prevY) : 0f, valid ? (float) (z - prevZ) : 0f, valid ? 1f : 0f);
        put(14, lightX, lightY, lightZ, light);
        put(15, height != null ? ring : 0f, height != null ? height.maxTop() : -64f, LensHeightfield.TRUSTED, 0f);
        put(16, zeroToOne ? 1f : 0f, moon ? 1f : 0f, rain, 0.05f);
        for (int i = 17; i < WIDTH; i++) put(i, 0f, 0f, 0f, 0f);
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture.getTexture(), data, 0, 0, 0, 0, WIDTH, 1);

        prevViewProj.set(viewProj);
        prevX = x;
        prevY = y;
        prevZ = z;
        prevNs = now;
        hasPrev = true;
        frame = (frame + 1) & 0xFFFF;
    }

    void close() {
        texture.close();
        MemoryUtil.memFree(data);
    }

    // GameRenderer.bobHurt and bobView, on a matrix instead of a pose stack (the same order of operations)
    private void bobHurt(CameraRenderState cam, float tiltStrength) {
        var e = cam.entityRenderState;
        if (!e.isLiving) return;
        float hurt = e.hurtTime;
        if (e.isDeadOrDying) {
            float duration = Math.min(e.deathTime, 20.0F);
            bob.rotate(Axis.ZP.rotationDegrees(40.0F - 8000.0F / (duration + 200.0F)));
        }
        if (hurt < 0.0F || e.preventFlinch) return;
        hurt /= e.hurtDuration;
        hurt = Mth.sin(hurt * hurt * hurt * hurt * (float) Math.PI);
        float rr = e.hurtDir;
        bob.rotate(Axis.YP.rotationDegrees(-rr));
        bob.rotate(Axis.ZP.rotationDegrees((float) (-hurt * 14.0 * tiltStrength)));
        bob.rotate(Axis.YP.rotationDegrees(rr));
    }

    private void bobView(CameraRenderState cam) {
        var e = cam.entityRenderState;
        if (!e.isPlayer) return;
        float walk = e.backwardsInterpolatedWalkDistance, b = e.bob;
        bob.translate(Mth.sin(walk * (float) Math.PI) * b * 0.5F, -Math.abs(Mth.cos(walk * (float) Math.PI) * b), 0.0F);
        bob.rotate(Axis.ZP.rotationDegrees(Mth.sin(walk * (float) Math.PI) * b * 3.0F));
        bob.rotate(Axis.XP.rotationDegrees(Math.abs(Mth.cos(walk * (float) Math.PI - 0.2F) * b) * 5.0F));
    }

    private void putMatrix(int texel, Matrix4f m) {
        // column by column: the shader builds mat4(c0, c1, c2, c3)
        m.get(texel * 16, data);
    }

    private void put(int texel, float a, float b, float c, float d) {
        int at = texel * 16;
        data.putFloat(at, a).putFloat(at + 4, b).putFloat(at + 8, c).putFloat(at + 12, d);
    }

    /** The texture the chains read as {@code awaken:lens_frame}. */
    static final class Texture extends AbstractTexture {
        Texture() {
            var device = RenderSystem.getDevice();
            texture = device.createTexture(() -> "awaken lens frame", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    GpuFormat.RGBA32_FLOAT, WIDTH, 1, 1, 1);
            textureView = device.createTextureView(texture);
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            ByteBuffer zero = MemoryUtil.memCalloc(WIDTH * 16);
            device.createCommandEncoder().writeToTexture(texture, zero, 0, 0, 0, 0, WIDTH, 1);
            MemoryUtil.memFree(zero);
        }
    }
}
