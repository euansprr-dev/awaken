package dev.awaken.hall.client;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.awaken.Awaken;
import dev.awaken.armour.ArmourSet;
import dev.awaken.armour.ForgeSong;
import dev.awaken.armour.client.ArmourMesh;
import dev.awaken.armour.client.ArmourMeshes;
import dev.awaken.armour.client.FittingStandRenderer;
import dev.awaken.arms.WeaponMesh;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import org.joml.Matrix4fStack;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Draws a {@link PicState}'s weapons and suits into its own texture, only when they change, and blits that. The vanilla base class
 * keeps its texture private and draws at the state's GUI size; the Hall needs the stage's size and its own blit.
 */
final class PicRenderer extends PictureInPictureRenderer<PicState> {
    private GpuTexture color, depth;
    private GpuTextureView colorView, depthView;
    private final Projection projection = new Projection();
    private final ProjectionMatrixBuffer projectionBuffer = new ProjectionMatrixBuffer("PIP - hall");
    private final SubmitNodeStorage store = new SubmitNodeStorage();
    private int key = -1;
    private long drawn = Long.MIN_VALUE;
    private static boolean warned;

    PicRenderer() {
        super();
    }

    @Override public Class<PicState> getRenderStateClass() { return PicState.class; }
    @Override protected void renderToTexture(PicState state, PoseStack poseStack, SubmitNodeCollector collector) {}
    @Override protected String getTextureLabel() { return "hall"; }
    @Override public boolean canBeReusedFor(PicState state, int w, int h) { return key == -1 || key == state.key; }

    @Override
    public void prepare(PicState s, GuiRenderState gui, FeatureRenderDispatcher features, int guiScale) {
        int w = Math.max(1, Math.round((s.x1 - s.x0) * s.real)), h = Math.max(1, Math.round((s.y1 - s.y0) * s.real));
        GpuDevice device = RenderSystem.getDevice();
        boolean resized = color == null || color.getWidth(0) != w || color.getHeight(0) != h;
        if (resized) textures(device, w, h);
        key = s.key;
        if (resized || s.version != drawn) {
            paint(s, features, device, w, h);
            drawn = s.version;
        }
        gui.addBlitToCurrentLayer(new BlitRenderState(s.pipeline, TextureSetup.singleTexture(colorView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)),
                s.pose(), s.x0, s.y0, s.x1, s.y1, 0F, 1F, 1F, 0F, s.color, null, null));
    }

    private void paint(PicState s, FeatureRenderDispatcher features, GpuDevice device, int w, int h) {
        device.createCommandEncoder().clearColorAndDepthTextures(color, GuiRenderer.CLEAR_COLOR, depth, 0.0);
        projection.setupOrtho(-4000F, 4000F, w, h, true); // a suit in the close-up reaches over a thousand texture pixels from its middle at 4K
        RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projection), ProjectionType.ORTHOGRAPHIC);
        Matrix4fStack view = RenderSystem.getModelViewStack();
        view.pushMatrix();
        Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
        PoseStack ps = new PoseStack();
        ps.scale(s.real, s.real, -s.real);
        ps.translate(-s.x0, -s.y0, 0F);
        boolean ours = HallPipelines.weaponOk();
        if (!ours && !warned) {
            warned = true;
            Awaken.LOG.warn("[hall] the weapon shader did not build; weapons draw with vanilla's light");
        }
        // a pass each: drawn together, a whole wall's worth, weapons came out joined to one another by long streaks
        for (Placed p : s.placed) {
            if (p.set() != null) placeSuit(ps, p, ours);
            else place(ps, p, ours);
            try (FeatureRenderDispatcher.PreparedFrame frame = features.prepareFrame(store);
                 RenderPass pass = device.createCommandEncoder().createRenderPass(() -> "Hall picture", colorView, Optional.empty(), depthView, OptionalDouble.empty())) {
                RenderSystem.bindDefaultUniforms(pass);
                FeatureRenderDispatcher.renderAllFeatures(pass, frame);
            }
        }
        view.popMatrix();
    }

    private void place(PoseStack ps, Placed p, boolean ours) {
        Meshes.Up up = Meshes.up(p.id());
        if (up == null) return;
        ps.pushPose();
        ps.translate(p.x(), p.y(), 0F);
        ps.scale(p.scale(), p.scale(), p.scale());
        ps.rotate(Axis.XP.rotationDegrees(180F)); // the stage's y runs down, a model's up
        ps.rotate(Axis.ZP.rotationDegrees(p.roll()));
        ps.rotate(Axis.XP.rotationDegrees(p.pitch()));
        ps.rotate(Axis.YP.rotationDegrees(p.yaw()));
        ps.translate(-up.cx(), -up.cy(), -up.cz());
        ps.rotate(Axis.ZP.rotation(up.angle()));
        ps.translate(-up.mx(), -up.my(), -up.mz());
        Identifier tex = Awaken.id("textures/item/arms/" + p.id() + ".png");
        int overlay = ours ? p.mode() | Math.round(p.alpha() * 255F) << 16 : OverlayTexture.NO_OVERLAY;
        boolean hidden = !ours && (p.mode() == Placed.SHAPE || p.mode() == Placed.IRON); // vanilla's light can't black it out, so a colour must
        store.submitCustomGeometry(ps, ours ? HallPipelines.weapon(tex) : HallPipelines.fallback(tex),
                (pose, buffer) -> emit(up.mesh(), pose, buffer, overlay, ours, hidden));
        ps.popPose();
    }

    /** A suit stands as on its Fitting Stand: every piece's meshes on their bones, a figure's y running down and its front at -z. */
    private void placeSuit(PoseStack ps, Placed p, boolean ours) {
        ArmourSet set = p.set();
        Identifier tex = ArmourMeshes.texture(set);
        ps.pushPose();
        ps.translate(p.x(), p.y(), -p.depth());
        ps.scale(p.scale(), p.scale(), p.scale());
        ps.rotate(Axis.XP.rotationDegrees(p.pitch()));
        ps.rotate(Axis.YP.rotationDegrees(p.yaw()));
        ps.translate(0F, -.5F, 0F);
        for (int slot = 0; slot < 4; slot++) {
            int mode = p.mode() >> slot * 4 & 15;
            if ((mode & 7) == Placed.SKIP) continue;
            ArmourMesh[] bones = ArmourMeshes.piece(set, ForgeSong.PIECES[slot]);
            if (bones == null) continue;
            int overlay = ours ? mode | Math.round(p.alpha() * 255F) << 16 : OverlayTexture.NO_OVERLAY;
            boolean hidden = !ours && (mode & 7) == Placed.SHAPE;
            for (ArmourMeshes.Bone b : ArmourMeshes.BONES) {
                ArmourMesh m = bones[b.ordinal()];
                if (m == null) continue;
                ps.pushPose();
                FittingStandRenderer.rest(ps, b);
                store.submitCustomGeometry(ps, ours ? HallPipelines.weapon(tex) : HallPipelines.fallback(tex),
                        (pose, buffer) -> emit(m.accent(), m.pos(), m.uv(), m.normal(), m.along(), m.vertices(), pose, buffer, overlay, ours, hidden));
                ps.popPose();
            }
        }
        ps.popPose();
    }

    private static void emit(WeaponMesh mesh, PoseStack.Pose pose, VertexConsumer buffer, int overlay, boolean ours, boolean hidden) {
        emit(mesh.accent(), mesh.pos(), mesh.uv(), mesh.normal(), mesh.along(), mesh.vertices(), pose, buffer, overlay, ours, hidden);
    }

    private static void emit(int accent, float[] p, float[] uv, byte[] n, byte[] along, int count, PoseStack.Pose pose, VertexConsumer buffer, int overlay, boolean ours, boolean hidden) {
        int shade = hidden ? 14 : 255;
        int r = ours ? accent >> 16 & 255 : shade, g = ours ? accent >> 8 & 255 : shade, b = ours ? accent & 255 : shade;
        int light = LightCoordsUtil.FULL_BRIGHT;
        for (int i = 0; i < count; i += 3) {
            for (int c = i; c < i + 3; c++) corner(buffer, pose, p, uv, n, c, r, g, b, ours ? along[c] & 255 : 255, light, overlay);
            if (!ours) corner(buffer, pose, p, uv, n, i + 2, r, g, b, 255, light, overlay);
        }
    }

    private static void corner(VertexConsumer buffer, PoseStack.Pose pose, float[] p, float[] uv, byte[] n, int i, int r, int g, int b, int a, int light, int overlay) {
        buffer.addVertex(pose, p[i * 3], p[i * 3 + 1], p[i * 3 + 2]).setColor(r, g, b, a).setUv(uv[i * 2], uv[i * 2 + 1]).setOverlay(overlay).setLight(light)
                .setNormal(pose, n[i * 3] / 127F, n[i * 3 + 1] / 127F, n[i * 3 + 2] / 127F);
    }

    private void textures(GpuDevice device, int w, int h) {
        closeTextures();
        int usage = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;
        color = device.createTexture(() -> "UI hall texture", usage, GpuFormat.RGBA8_UNORM, w, h, 1, 1);
        colorView = device.createTextureView(color);
        GpuFormat depthFormat = Minecraft.getInstance().gameRenderer.mainRenderTarget().getDepthTexture().getFormat();
        depth = device.createTexture(() -> "UI hall depth", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_RENDER_ATTACHMENT, depthFormat, w, h, 1, 1);
        depthView = device.createTextureView(depth);
        drawn = Long.MIN_VALUE;
    }

    private void closeTextures() {
        if (colorView != null) colorView.close();
        if (color != null) color.close();
        if (depthView != null) depthView.close();
        if (depth != null) depth.close();
        color = depth = null;
        colorView = depthView = null;
    }

    @Override
    public void close() {
        closeTextures();
        super.close();
    }
}
