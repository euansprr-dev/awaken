package dev.awaken.gates.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.awaken.gates.GatePortalBlockEntity;
import dev.awaken.gates.GateVortex;
import dev.awaken.gates.block.GateBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws a doorway inside a gate (the exit after the Lord, the way back, an inner gate, a rift) as one vortex across the
 * whole opening, in the gate's rank colours: the surface gate's own geometry ({@link GateVortex}) at the opening's
 * size, heart first so it hides what is behind, like {@link GateVortexRenderer}. Rifts burn red.
 */
public final class GatePortalRenderer implements BlockEntityRenderer<GatePortalBlockEntity, GatePortalRenderer.State> {
    private static final long LOOP = 72000L;
    /** Openings are seen across a Lord's arena (radius 22) and down a corridor. */
    private static final double FAR = 72;
    /** Half the opening's width: three blocks, the surface just inside the edges. */
    private static final float HALF_WIDTH = 1.42F;

    public static final class State extends BlockEntityRenderState {
        int rank, height = 3, side = 1;
        boolean alongX = true, red;
        long seed;
        float time, fade = 1F;
    }

    public GatePortalRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(GatePortalBlockEntity be, State s, float partialTicks, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, s, partialTicks, camera, crumbling);
        BlockState st = be.getBlockState();
        if (!(st.getBlock() instanceof GateBlocks.Portal portal)) return;
        s.alongX = st.getValue(GateBlocks.Portal.AXIS) == Direction.Axis.X;
        s.rank = st.getValue(GateBlocks.Portal.RANK);
        s.red = portal.kind() == GateBlocks.Portal.Kind.RIFT;
        BlockPos pos = be.getBlockPos();
        s.seed = pos.asLong() * 0x9E3779B97F4A7C15L;
        Level level = be.getLevel();
        long now = level != null ? level.getGameTime() : 0L;
        s.time = (Math.floorMod(now, LOOP) + partialTicks) / 20F;
        // the opening's height: blocks of the same doorway stacked on the anchor
        int h = 1;
        if (level != null) {
            BlockPos.MutableBlockPos m = pos.mutable();
            while (h < 6 && level.getBlockState(m.move(Direction.UP)).is(st.getBlock())) h++;
        }
        s.height = h;
        double cx = pos.getX() + 0.5, cy = pos.getY() + h * 0.5, cz = pos.getZ() + 0.5;
        double dx = camera.x - cx, dy = camera.y - cy, dz = camera.z - cz;
        s.side = (s.alongX ? dz : dx) >= 0 ? 1 : -1;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        s.fade = (float) Math.max(0.0, Math.min(1.0, (FAR - d) / (FAR * 0.25)));
    }

    @Override
    public void submit(State s, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        if (s.fade <= 0.01F) return;
        GateVortex.Look look = GateVortex.Look.forDoorway(s.rank, s.red, s.seed, HALF_WIDTH, s.height * 0.5F - 0.04F, s.fade);
        float time = s.time;
        int side = s.side;
        pose.pushPose();
        pose.translate(0.5F, s.height * 0.5F, 0.5F);
        // local x along the opening, z through it (as for a surface gate)
        if (!s.alongX) pose.rotateDegrees(Axis.YP, 90F);
        out.order(-1).submitCustomGeometry(pose, GateVortexRenderer.HEART, (p, vc) -> GateVortex.emitHeart(look, time, side, new GateVortexRenderer.Glow(p, vc)));
        out.submitCustomGeometry(pose, GateVortexRenderer.GLOW, (p, vc) -> GateVortex.emitSurface(look, time, side, new GateVortexRenderer.Glow(p, vc), null));
        pose.popPose();
    }

    /** The surface (and its halo) reaches past the anchor block. */
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return (int) FAR;
    }

    @Override
    public AABB getRenderBoundingBox(GatePortalBlockEntity be) {
        BlockPos p = be.getBlockPos();
        return new AABB(p.getX() - 3, p.getY() - 1, p.getZ() - 3, p.getX() + 4, p.getY() + 7, p.getZ() + 4);
    }
}
