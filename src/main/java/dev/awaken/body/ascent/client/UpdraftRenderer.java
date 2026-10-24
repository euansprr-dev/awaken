package dev.awaken.body.ascent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.Awaken;
import dev.awaken.body.ascent.UpdraftVentBlockEntity;
import dev.awaken.body.ascent.Updrafts;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * An updraft's column as light: streaks of pale air rising up it, each turned to face you, fading out where the lift
 * fades, so a glider can see where to steer from far off. One draw a vent, nothing per block.
 */
public final class UpdraftRenderer implements BlockEntityRenderer<UpdraftVentBlockEntity, UpdraftRenderer.State> {
    static final Identifier STREAM = Awaken.id("textures/ascent/stream.png");
    private static final int STREAKS = 16;
    private static @Nullable RenderType type;

    public static final class State extends BlockEntityRenderState {
        float time;
        double camX, camZ;
    }

    public UpdraftRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(UpdraftVentBlockEntity be, State state, float partialTicks, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, crumbling);
        state.time = be.getLevel() == null ? 0 : (be.getLevel().getGameTime() % 24000L) + partialTicks;
        state.camX = camera.x - (be.getBlockPos().getX() + 0.5);
        state.camZ = camera.z - (be.getBlockPos().getZ() + 0.5);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        if (type == null) type = RenderTypes.energySwirl(STREAM, 0, 0);
        float time = state.time;
        double cx = state.camX, cz = state.camZ;
        out.submitCustomGeometry(pose, type, (p, vc) -> streaks(p, vc, time, cx, cz));
    }

    private static void streaks(PoseStack.Pose pose, VertexConsumer vc, float time, double camX, double camZ) {
        double top = Updrafts.HEIGHT;
        for (int i = 0; i < STREAKS; i++) {
            // spread round the column (golden angle), drifting slowly round as they rise
            double ang = i * 2.39996 + time * 0.004 * (i % 2 == 0 ? 1 : -1);
            double r = Updrafts.RADIUS * 0.85 * Math.sqrt(0.08 + 0.92 * frac(i * 0.618034));
            double x = 0.5 + Math.cos(ang) * r, z = 0.5 + Math.sin(ang) * r;
            double speed = 0.32 + 0.22 * frac(i * 0.4142), len = 3.5 + 3 * frac(i * 0.7321);
            double y0 = 1 + ((time * speed + frac(i * 0.5772) * (top + len)) % (top + len)) - len;
            double y1 = y0 + len;
            if (y1 < 1) continue;
            // stronger low in the column, gone where the lift is gone
            double mid = Mth.clamp((y0 + y1) / 2 - 1, 0, top);
            float a = (float) ((mid <= Updrafts.FULL ? 1 : 1 - (mid - Updrafts.FULL) / (top - Updrafts.FULL)) * 0.55);
            if (a <= 0.02F) continue;
            // a ribbon turned toward the camera round the vertical
            double tx = camX - (x - 0.5), tz = camZ - (z - 0.5), tl = Math.sqrt(tx * tx + tz * tz);
            if (tl < 1e-3) continue;
            double w = 0.16 + 0.1 * frac(i * 0.29);
            double rx = -tz / tl * w, rz = tx / tl * w;
            int c = ((int) (a * 255) << 24) | 0xD8F2FF;
            float bottom = (float) Math.max(1, y0);
            vert(vc, pose, x - rx, y1, z - rz, 0, 0, c);
            vert(vc, pose, x + rx, y1, z + rz, 1, 0, c);
            vert(vc, pose, x + rx, bottom, z + rz, 1, 1, c);
            vert(vc, pose, x - rx, bottom, z - rz, 0, 1, c);
        }
    }

    private static double frac(double v) {
        return v - Math.floor(v);
    }

    private static void vert(VertexConsumer vc, PoseStack.Pose pose, double x, double y, double z, float u, float v, int argb) {
        vc.addVertex(pose, (float) x, (float) y, (float) z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
    }

    @Override
    public int getViewDistance() {
        return 160;
    }

    @Override
    public AABB getRenderBoundingBox(UpdraftVentBlockEntity be) {
        var p = be.getBlockPos();
        return new AABB(p.getX() - Updrafts.RADIUS, p.getY(), p.getZ() - Updrafts.RADIUS,
                p.getX() + 1 + Updrafts.RADIUS, p.getY() + 1 + Updrafts.HEIGHT, p.getZ() + 1 + Updrafts.RADIUS);
    }
}
