package dev.awaken.gates.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.awaken.Awaken;
import dev.awaken.gates.GateVortex;
import dev.awaken.gates.GateVortexBlockEntity;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws a surface gate's whole vortex from its anchor core: one continuous surface across the opening (halo, dark
 * heart, spiral arms, filaments, rim, motes, ground cracks) from one filtered atlas so every layer sorts together, and
 * rock shards orbiting the rim. The geometry is {@link GateVortex}'s (shared with the offline preview); this class only
 * places it in the world and streams it into vertex buffers. Rendered off-screen-aware with bounds that cover the
 * whole gate, so it never pops at the screen edge, and faded out near the end of its view distance.
 */
public final class GateVortexRenderer implements BlockEntityRenderer<GateVortexBlockEntity, GateVortexRenderer.State> {
    static final Identifier ATLAS = Awaken.id("textures/entity/gate_vortex.png");
    static final Identifier SHARD = Awaken.id("textures/entity/gate_shard.png");
    private static final RenderType GLOW = RenderTypes.entityTranslucentEmissive(ATLAS);
    /** The dark heart writes depth (the emissive glow can't), so clouds behind the gate don't show through it. */
    private static final RenderType HEART = RenderTypes.entityTranslucent(ATLAS);
    private static final RenderType ROCK = RenderTypes.entityCutout(SHARD);
    /** Ticks in the animation clock's loop (an hour), so float time stays precise. */
    private static final long LOOP = 72000L;

    public static final class State extends BlockEntityRenderState {
        int rank, mode, axis, side = 1;
        boolean red;
        long seed;
        float open = 1F, warn, fade = 1F, time;
        float centreY;
    }

    public GateVortexRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(GateVortexBlockEntity be, State s, float partialTicks, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTicks, camera, crumbling);
        s.rank = be.rank();
        s.mode = be.mode();
        s.axis = be.axis();
        s.red = be.red();
        s.seed = be.seed();
        s.warn = be.warn();
        long now = be.getLevel() != null ? be.getLevel().getGameTime() : 0L;
        s.time = (Math.floorMod(now, LOOP) + partialTicks) / 20F;
        s.open = s.mode == GateVortex.FORMING ? Math.max(0F, Math.min(1F, (now - be.formedAt() + partialTicks) / 60F)) : 1F;
        s.centreY = GateVortex.size(s.rank).centreY() - 1F; // the anchor is one block above the ground block
        double dx = camera.x - be.centreX(), dy = camera.y - be.centreY(), dz = camera.z - be.centreZ();
        double through = s.axis == 0 ? dz : dx;
        s.side = through >= 0 ? 1 : -1;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz), far = viewDistance(s.rank);
        s.fade = (float) Math.max(0.0, Math.min(1.0, (far - d) / (far * 0.2)));
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        if (s.fade <= 0.01F) return;
        GateVortex.Look look = new GateVortex.Look(s.rank, s.mode, s.red, s.seed, s.open, s.warn, s.fade);
        float time = s.time;
        // local z runs through the opening: world z for axis 0, world x for axis 1 (after the quarter turn below)
        int side = s.side;
        int light = LightCoordsUtil.pack(Math.max(LightCoordsUtil.block(s.lightCoords), 11), LightCoordsUtil.sky(s.lightCoords));
        pose.pushPose();
        pose.translate(0.5F, s.centreY, 0.5F);
        // axis 1: the opening's face runs along z; turn the local frame (x along the face, z through it) a quarter
        if (s.axis == 1) pose.rotateDegrees(Axis.YP, 90F);
        // the heart goes in an earlier order, so the glow layers in front of it are drawn after its depth is written
        out.order(-1).submitCustomGeometry(pose, HEART, (p, vc) -> GateVortex.emitHeart(look, time, side, new Glow(p, vc)));
        out.submitCustomGeometry(pose, GLOW, (p, vc) -> GateVortex.emitSurface(look, time, side, new Glow(p, vc), null));
        out.submitCustomGeometry(pose, ROCK, (p, vc) -> GateVortex.emitShards(look, time, new Rock(p, vc, light)));
        pose.popPose();
    }

    /** Emissive translucent vertices (full bright, normal facing the viewer's side). */
    private record Glow(PoseStack.Pose pose, VertexConsumer vc) implements GateVortex.Sink {
        @Override
        public void vertex(float x, float y, float z, float u, float v, int argb, float nx, float ny, float nz) {
            vc.addVertex(pose, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT)
                    .setNormal(pose, nx, ny, nz);
        }
    }

    /** Lit rock (the shards take the world's light, shaded by their faces). */
    private record Rock(PoseStack.Pose pose, VertexConsumer vc, int light) implements GateVortex.Sink {
        @Override
        public void vertex(float x, float y, float z, float u, float v, int argb, float nx, float ny, float nz) {
            vc.addVertex(pose, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        }
    }

    // ---- visibility -----------------------------------------------------------------------------------------------------

    /** Bigger gates are seen from further away. */
    static double viewDistance(int rank) {
        return 96 + 20 * GateVortex.clampRank(rank);
    }

    /** Drawn even when its own chunk section is out of view: the vortex reaches far beyond the anchor block. */
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return (int) viewDistance(6);
    }

    @Override
    public boolean shouldRender(GateVortexBlockEntity be, Vec3 camera) {
        double dx = camera.x - be.centreX(), dy = camera.y - be.centreY(), dz = camera.z - be.centreZ();
        double far = viewDistance(be.rank());
        return dx * dx + dy * dy + dz * dz < far * far;
    }

    /** The whole gate: the vortex, its shards and glow, the cracks over the pad. */
    @Override
    public AABB getRenderBoundingBox(GateVortexBlockEntity be) {
        float[] b = GateVortex.bounds(be.rank());
        double e = Math.max(b[0], b[2]);
        return new AABB(be.centreX() - e, be.centreY() - b[1], be.centreZ() - e, be.centreX() + e, be.centreY() + b[1], be.centreZ() + e);
    }
}
