package dev.awaken.body.ascent.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.awaken.body.ascent.UpdraftVentBlockEntity;
import dev.awaken.body.ascent.Updrafts;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;

/**
 * An updraft's column as light (SPEC §17.6): gusts of pale air rising up it, winding round as they go and fading out
 * where the lift fades, so a glider can see where to steer from far off. The vent only says it is in view here: its
 * column is drawn with the windways' gusts, in their pass after the level ({@link WindwayRenderer}), where soft light
 * writes no depth (drawn through the level's own passes, its sheet of haze read as something solid to Lens: a dark slab
 * against the night sky).
 */
public final class UpdraftRenderer implements BlockEntityRenderer<UpdraftVentBlockEntity, BlockEntityRenderState> {
    public UpdraftRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public BlockEntityRenderState createRenderState() {
        return new BlockEntityRenderState();
    }

    @Override
    public void submit(BlockEntityRenderState state, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        WindwayRenderer.vent(state.blockPos);
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
