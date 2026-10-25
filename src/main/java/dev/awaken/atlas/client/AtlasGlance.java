package dev.awaken.atlas.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.awaken.Awaken;
import dev.awaken.atlas.Discovery;
import dev.awaken.progression.client.StatusSections;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.Ui;
import dev.awaken.signal.client.UiSection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/**
 * The wrist glance (SPEC §16.8): the land round you as a small live diorama in the K menu, turned so where you look is
 * up, your thread and the clouds included. It is painted into one registered texture at most once a second, and only
 * while the menu shows it (or sooner when you've moved or turned), so the Holo draws it as one textured quad: the
 * glance costs one quad a frame plus a paint a second.
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
            painter.paint(RenderSystem.getDevice(), s, target.getTexture(), target.getTextureView(), target.depth, target.depthView, W, H, 1, true);
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

    /** The glance on the K menu's Status page, with a button to the table. */
    static final class Section extends UiSection {
        public String title() {
            return "ATLAS";
        }

        public int order() {
            return -25; // right under NOW: where you are comes before what you carry
        }

        public boolean visible() {
            return Discovery.mapped(AtlasState.get().dimension()) && Discovery.CLIENT.count() > 0;
        }

        @Override
        protected void build(Ui ui) {
            int w = ui.width, h = Math.round(w * (float) H / W);
            int x = ui.x, y = ui.y();
            if (ui.drawing()) {
                want();
                if (target != null) ui.canvas().texture(ID, x, y, w, h, 0f, 1f, 1f, 0f, 0xFFFFFFFF);
                ui.canvas().outline(x - 1, y - 1, w + 2, h + 2, Glass.a(Glass.EDGE, 0.35f));
                // you, at the middle of the glance (the table's centre sits a little below half)
                int cx = x + w / 2, cy = y + Math.round(h * 0.55f);
                ui.canvas().fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFE9F1FF);
                ui.canvas().fill(cx, cy - 6, cx + 1, cy - 2, 0xCCE9F1FF);
            }
            ui.gap(h + 4);
            Ui.Flow row = ui.flow();
            if (row.button("Open the table  ·  " + AtlasClient.KEY.getTranslatedKeyMessage().getString(), Glass.EDGE, true)) {
                Minecraft.getInstance().schedule(() -> {
                    Minecraft.getInstance().gui.setScreen(null);
                    AtlasScreen.open(20f, 52f, 1f);
                });
            }
            row.text(String.format(java.util.Locale.ROOT, "%,d places known", Discovery.CLIENT.count()), Glass.LABEL);
            row.end();
        }
    }

    static void register() {
        StatusSections.add(StatusSections.Tab.STATUS, new Section());
    }
}
