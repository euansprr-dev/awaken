package dev.awaken.above.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.awaken.Awaken;
import dev.awaken.above.halo.Halo;
import dev.awaken.above.net.AboveNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

import java.util.Random;

/**
 * The Halo's sky: the planet turning far below (a textured disc that follows the camera, so it reads as infinitely far)
 * and the occasional Sky Leviathan drifting past (a billboard sent by the server). Placed inside the render-distance
 * fog so it never gets fogged out.
 */
final class HaloSky {
    private HaloSky() {}

    private static final Identifier PLANET = Awaken.id("textures/misc/halo_planet.png");
    private static final Identifier LEVIATHAN = Awaken.id("textures/misc/sky_leviathan.png");

    private static long leviathanStart = -1;
    private static int leviathanDuration;
    private static float leviathanAzimuth, leviathanElevation, leviathanSweep;

    static void sighting(AboveNet.Leviathan msg) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Random r = new Random(msg.seed());
        leviathanStart = mc.level.getGameTime();
        leviathanDuration = Math.max(200, msg.durationTicks());
        leviathanAzimuth = r.nextFloat() * 360f;
        leviathanElevation = -8f + r.nextFloat() * 22f;
        leviathanSweep = (r.nextBoolean() ? 1 : -1) * (70f + r.nextFloat() * 50f);
    }

    static void onSubmit(SubmitCustomGeometryEvent e) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != Halo.LEVEL) return;
        var state = e.getLevelRenderState();
        Vec3 cam = state.cameraRenderState.pos;
        float time = state.gameTime + state.worldPartialTicks;
        float rd = Math.max(4, mc.options.getEffectiveRenderDistance()) * 16f;
        PoseStack ps = e.getPoseStack();
        SubmitNodeCollector out = e.getSubmitNodeCollector();

        // the planet: a disc 0.35 RD below the camera, 0.8 RD wide, slowly turning
        float depth = rd * 0.35f, half = rd * 0.8f;
        ps.pushPose();
        ps.translate(0, -depth, 0);
        ps.rotate(Axis.YP, time * 0.00025f);
        horizontalQuad(out, ps, RenderTypes.entityTranslucentEmissive(PLANET), half, 0xFFFFFFFF);
        ps.popPose();

        // a Sky Leviathan, if one is passing
        if (leviathanStart >= 0) {
            float t = (time - leviathanStart) / leviathanDuration;
            if (t >= 1f) {
                leviathanStart = -1;
            } else if (t >= 0f) {
                float fade = Math.min(1f, Math.min(t, 1f - t) * 5f);
                float az = (float) Math.toRadians(leviathanAzimuth + leviathanSweep * t);
                float el = (float) Math.toRadians(leviathanElevation + 6f * (float) Math.sin(t * Math.PI));
                float dist = rd * 0.7f;
                double dx = Math.cos(el) * Math.sin(az) * dist, dy = Math.sin(el) * dist, dz = Math.cos(el) * Math.cos(az) * dist;
                ps.pushPose();
                ps.translate(dx, dy, dz);
                ps.rotate(state.cameraRenderState.orientation);
                // the body faces along its path; flip the texture when it swims the other way
                float size = dist * 0.22f;
                int alpha = (int) (fade * 235);
                billboard(out, ps, RenderTypes.entityTranslucentEmissive(LEVIATHAN), size * 2f, size, leviathanSweep < 0, (alpha << 24) | 0xFFFFFF);
                ps.popPose();
            }
        }
    }

    private static void horizontalQuad(SubmitNodeCollector out, PoseStack ps, RenderType type, float half, int argb) {
        out.submitCustomGeometry(ps, type, (pose, vc) -> {
            // both windings so it shows from above whatever the cull state
            vertex(vc, pose, -half, 0, -half, 0, 0, argb, 1);
            vertex(vc, pose, -half, 0, half, 0, 1, argb, 1);
            vertex(vc, pose, half, 0, half, 1, 1, argb, 1);
            vertex(vc, pose, half, 0, -half, 1, 0, argb, 1);
            vertex(vc, pose, half, 0, -half, 1, 0, argb, -1);
            vertex(vc, pose, half, 0, half, 1, 1, argb, -1);
            vertex(vc, pose, -half, 0, half, 0, 1, argb, -1);
            vertex(vc, pose, -half, 0, -half, 0, 0, argb, -1);
        });
    }

    private static void billboard(SubmitNodeCollector out, PoseStack ps, RenderType type, float halfW, float halfH, boolean flip, int argb) {
        float u0 = flip ? 1 : 0, u1 = flip ? 0 : 1;
        out.submitCustomGeometry(ps, type, (pose, vc) -> {
            quadVertex(vc, pose, -halfW, -halfH, u0, 1, argb);
            quadVertex(vc, pose, halfW, -halfH, u1, 1, argb);
            quadVertex(vc, pose, halfW, halfH, u1, 0, argb);
            quadVertex(vc, pose, -halfW, halfH, u0, 0, argb);
            quadVertex(vc, pose, -halfW, halfH, u0, 0, argb);
            quadVertex(vc, pose, halfW, halfH, u1, 0, argb);
            quadVertex(vc, pose, halfW, -halfH, u1, 1, argb);
            quadVertex(vc, pose, -halfW, -halfH, u0, 1, argb);
        });
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v, int argb, int ny) {
        vc.addVertex(pose, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0, ny, 0);
    }

    private static void quadVertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float u, float v, int argb) {
        vc.addVertex(pose, x, y, 0).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0, 0, 1);
    }
}
