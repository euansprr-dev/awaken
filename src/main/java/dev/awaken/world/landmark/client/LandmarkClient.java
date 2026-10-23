package dev.awaken.world.landmark.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.awaken.Awaken;
import dev.awaken.world.landmark.LandmarkKind;
import dev.awaken.world.landmark.LandmarkNet;
import dev.awaken.world.landmark.MarkerLight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;

/**
 * The landmarks' faint markers, drawn on the client from the server's list ({@link LandmarkNet.Markers}): a soft light
 * shaft over each ruin at dusk (lingering faintly at night; a sky platform's falls toward the ground), and the Well's
 * four pillar tops glowing at night with a short shaft above each. By day nothing. A handful of camera-facing quads a
 * frame, no state beyond the list.
 */
public final class LandmarkClient {
    private LandmarkClient() {}

    private static final Identifier SHAFT = Awaken.id("textures/misc/landmark_shaft.png");
    private static final Identifier GLOW = Awaken.id("textures/misc/landmark_glow.png");
    /** Created on first draw, not when this class loads at mod construction (before the renderer is up). */
    private static final class Types {
        static final RenderType SHAFT_TYPE = RenderTypes.beaconBeam(SHAFT, true);
        static final RenderType GLOW_TYPE = RenderTypes.beaconBeam(GLOW, true);
    }

    private static final ContextKey<List<Draw>> KEY = new ContextKey<>(Awaken.id("landmark_markers"));

    /** Peak opacity of a shaft's bright core; its halo is wide and faint. Seen at dusk, faint through the night. */
    static final float RUIN_ALPHA = 0.75F, HALO_ALPHA = 0.26F, WELL_ALPHA = 0.7F;

    /** dx/dy/dz relative to the camera; kind 0 = shaft up, 1 = shaft down, 2 = glow. */
    private record Draw(double dx, double dy, double dz, int kind, float size, float height, int argb) {}

    private static volatile List<LandmarkNet.Marker> markers = List.of();

    public static void init(IEventBus modBus) {
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, e -> e.register(LandmarkNet.Markers.TYPE, (msg, ctx) -> markers = List.copyOf(msg.markers())));
        NeoForge.EVENT_BUS.addListener(ExtractLevelRenderStateEvent.class, LandmarkClient::onExtract);
        NeoForge.EVENT_BUS.addListener(SubmitCustomGeometryEvent.class, LandmarkClient::onSubmit);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> markers = List.of());
    }

    private static int argb(float alpha, int rgb) {
        return ((int) (Math.clamp(alpha, 0F, 1F) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    static void onExtract(ExtractLevelRenderStateEvent e) {
        List<LandmarkNet.Marker> list = markers;
        if (list.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != Level.OVERWORLD) return;
        long t = Math.floorMod(mc.level.getOverworldClockTime(), 24000L);
        float shaft = MarkerLight.ruinShaft(t), glow = MarkerLight.wellGlow(t);
        if (shaft <= 0F && glow <= 0F) return;
        Vec3 cam = e.getCamera().position();
        List<Draw> out = new ArrayList<>();
        for (LandmarkNet.Marker m : list) {
            double cx = m.x() + 0.5, cz = m.z() + 0.5;
            LandmarkKind kind = m.landmark();
            if (kind == LandmarkKind.WELL) {
                if (glow <= 0F) continue;
                double top = m.y() + m.height() + 0.6;
                for (int sx = -1; sx <= 1; sx += 2) {
                    for (int sz = -1; sz <= 1; sz += 2) {
                        double px = cx + sx * 8, pz = cz + sz * 8;
                        double d = Math.sqrt((px - cam.x) * (px - cam.x) + (pz - cam.z) * (pz - cam.z));
                        out.add(new Draw(px - cam.x, top - cam.y, pz - cam.z, 2, 2.2F, 0F, argb(WELL_ALPHA * glow, 0x8FD8FF)));
                        float a = WELL_ALPHA * 0.6F * glow * MarkerLight.distanceFade(d);
                        if (a > 0.01F) {
                            float w = (float) Math.max(1.4, d * 0.005);
                            out.add(new Draw(px - cam.x, top - 0.4 - cam.y, pz - cam.z, 0, w * 3F, 28F, argb(a * 0.35F, 0x8FD8FF)));
                            out.add(new Draw(px - cam.x, top - 0.4 - cam.y, pz - cam.z, 0, w, 36F, argb(a, 0x8FD8FF)));
                        }
                    }
                }
                continue;
            }
            if (shaft <= 0F) continue;
            double d = Math.sqrt((cx - cam.x) * (cx - cam.x) + (cz - cam.z) * (cz - cam.z));
            float fade = shaft * MarkerLight.distanceFade(d);
            if (fade <= 0.01F) continue;
            // wide enough to read as a column of light from far off, not a hairline: it grows a little with distance
            float core = (float) Math.max(2.0, d * 0.007);
            int rgb = kind == LandmarkKind.SKY_PLATFORM ? 0xC8DCFF : kind == LandmarkKind.OCEAN_LAB ? 0x9FE7FF : 0xFFD9A0;
            int dir = kind == LandmarkKind.SKY_PLATFORM ? 1 : 0;
            double y = kind == LandmarkKind.SKY_PLATFORM ? m.y() : m.y() + m.height();
            float h = kind == LandmarkKind.SKY_PLATFORM ? 96F : kind == LandmarkKind.OCEAN_LAB ? 120F : 110F;
            out.add(new Draw(cx - cam.x, y - cam.y, cz - cam.z, dir, core * 3.4F, h * 0.85F, argb(HALO_ALPHA * fade, rgb)));
            out.add(new Draw(cx - cam.x, y - cam.y, cz - cam.z, dir, core, h, argb(RUIN_ALPHA * fade, rgb)));
        }
        if (!out.isEmpty()) e.getRenderState().setRenderData(KEY, out);
    }

    static void onSubmit(SubmitCustomGeometryEvent e) {
        List<Draw> draws = e.getLevelRenderState().getRenderData(KEY);
        if (draws == null || draws.isEmpty()) return;
        PoseStack ps = e.getPoseStack();
        SubmitNodeCollector out = e.getSubmitNodeCollector();
        for (Draw d : draws) {
            ps.pushPose();
            ps.translate(d.dx(), d.dy(), d.dz());
            if (d.kind() == 2) {
                ps.rotate(e.getLevelRenderState().cameraRenderState.orientation);
                float s = d.size();
                out.submitCustomGeometry(ps, Types.GLOW_TYPE, (pose, vc) -> quad(pose, vc, -s, -s, s, s, 0F, 1F, d.argb()));
            } else {
                // turn about the vertical axis to face the camera: a soft column from any side
                ps.rotate(Axis.YP.rotation((float) Math.atan2(-d.dx(), -d.dz())));
                float w = d.size() / 2F, h = d.height();
                if (d.kind() == 0) out.submitCustomGeometry(ps, Types.SHAFT_TYPE, (pose, vc) -> quad(pose, vc, -w, 0F, w, h, 1F, 0F, d.argb()));
                else out.submitCustomGeometry(ps, Types.SHAFT_TYPE, (pose, vc) -> quad(pose, vc, -w, -h, w, 0F, 0F, 1F, d.argb()));
            }
            ps.popPose();
        }
    }

    /** A quad in the pose's XY plane, both windings (the beam pipeline culls back faces); vBottom/vTop pick the gradient's way. */
    private static void quad(PoseStack.Pose pose, VertexConsumer vc, float x0, float y0, float x1, float y1, float vBottom, float vTop, int argb) {
        vertex(vc, pose, x0, y0, 0F, vBottom, argb);
        vertex(vc, pose, x1, y0, 1F, vBottom, argb);
        vertex(vc, pose, x1, y1, 1F, vTop, argb);
        vertex(vc, pose, x0, y1, 0F, vTop, argb);
        vertex(vc, pose, x0, y1, 0F, vTop, argb);
        vertex(vc, pose, x1, y1, 1F, vTop, argb);
        vertex(vc, pose, x1, y0, 1F, vBottom, argb);
        vertex(vc, pose, x0, y0, 0F, vBottom, argb);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float u, float v, int argb) {
        vc.addVertex(pose, x, y, 0F).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, 0F, 1F, 0F);
    }
}
