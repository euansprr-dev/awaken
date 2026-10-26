package dev.awaken.atlas.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.awaken.Awaken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/**
 * The wrist glance (SPEC §16.8): the land round you as a small live diorama, turned so where you look is up, your thread
 * and the clouds included. It is painted into one registered texture at most once a second, and only while something
 * asks for it ({@link #want}; or sooner when you've moved or turned), so it costs one quad a frame plus a paint a
 * second. It left the K menu with the band's pages (SPEC §30: the Map page is the Atlas itself); it is kept as the
 * painter the map scene (phase 4) will drive from its own camera.
 */
final class AtlasGlance {
    private AtlasGlance() {}

    static final Identifier ID = Awaken.id("atlas/glance");
    static final int W = 480, H = 280;
    /** Blocks across the glance. */
    static final float SPAN = 240f;
    private static final long PERIOD_NANOS = 1_000_000_000L;

    private static Target target;
    private static AtlasPainter painter;
    private static long wantedUntil, lastPaint;
    private static double lastX = Double.NaN, lastZ;
    private static float lastYaw;
    static long paintNanos;

    /** The glance's texture: our own colour target, with a depth target beside it, registered under {@link #ID}. */
    private static final class Target extends AbstractTexture {
        GpuTexture depth;
        GpuTextureView depthView;

        Target() {
            GpuDevice device = RenderSystem.getDevice();
            texture = device.createTexture(() -> "Atlas glance", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING
                    | GpuTexture.USAGE_RENDER_ATTACHMENT, GpuFormat.RGBA8_UNORM, W, H, 1, 1);
            textureView = device.createTextureView(texture);
            GpuFormat depthFormat = Minecraft.getInstance().gameRenderer.mainRenderTarget().getDepthTexture().getFormat();
            depth = device.createTexture(() -> "Atlas glance depth", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_RENDER_ATTACHMENT, depthFormat, W, H, 1, 1);
            depthView = device.createTextureView(depth);
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }

        @Override
        public void close() {
            super.close();
            if (depthView != null) depthView.close();
            if (depth != null) depth.close();
        }
    }

    /** Called by the section each frame it is drawn: keep the glance fresh for the next half second. */
    static void want() {
        wantedUntil = System.nanoTime() + 500_000_000L;
    }

    /** At the start of a frame: paint the glance if the menu shows it and it's due. Outside every render pass. */
    static void frame() {
        long now = System.nanoTime();
        Minecraft mc = Minecraft.getInstance();
        if (now > wantedUntil || mc.player == null || mc.level == null) return;
        double x = mc.player.getX(), z = mc.player.getZ();
        float yaw = mc.player.getYRot();
        boolean moved = Double.isNaN(lastX) || Math.abs(x - lastX) + Math.abs(z - lastZ) > 4 || Math.abs(yaw - lastYaw) > 12f;
        if (!moved && now - lastPaint < PERIOD_NANOS) return;
        lastPaint = now;
        lastX = x;
        lastZ = z;
        lastYaw = yaw;
        if (target == null) {
            target = new Target();
            painter = new AtlasPainter();
            mc.getTextureManager().register(ID, target);
        }
        AtlasState a = AtlasState.get();
        float zoom = W / SPAN;
        double cy = a.store.groundY(x, z, mc.player.getY());
        // where you look is up the glance (the table's yaw 0 looks north; your yaw 0 faces south)
        AtlasRenderState s = new AtlasRenderState(0, 0, W, H, yaw + 180f, 56f, zoom, x, cy, z, (now % 1_000_000_000_000L) / 1e9f,
                AtlasState.night(mc.level), true, AtlasMesh.lodFor(zoom), 10f, 0, null);
        RenderSystem.backupProjectionMatrix();
        try {
            painter.paint(RenderSystem.getDevice(), s, target.getTexture(), target.getTextureView(), target.depth, target.depthView, W, H, 1f, true, false);
        } finally {
            RenderSystem.restoreProjectionMatrix();
        }
        paintNanos = System.nanoTime() - now;
    }

    static void close() {
        if (target != null) {
            Minecraft.getInstance().getTextureManager().release(ID);
            painter.close();
            target = null;
            painter = null;
        }
    }
}
